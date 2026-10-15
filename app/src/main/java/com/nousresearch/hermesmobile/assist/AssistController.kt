package com.nousresearch.hermesmobile.assist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.nousresearch.hermesmobile.HermesApplication
import com.nousresearch.hermesmobile.data.ActiveChat
import com.nousresearch.hermesmobile.data.GatewayConnectionState
import com.nousresearch.hermesmobile.data.HermesSession
import com.nousresearch.hermesmobile.features.ParityController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One voice turn after another: listen, transcribe on the gateway, send to the profile's voice conversation,
 * show the streamed answer and read it aloud. It drives the app's shared [com.nousresearch.hermesmobile.HermesViewModel],
 * so the conversation is the same one the app shows when opened.
 */
class AssistController(
    private val app: HermesApplication,
    private val scope: CoroutineScope,
    private val voice: AssistVoice,
    private val speech: AndroidSpeech,
    private val settings: AssistSettings,
    private val voiceChatTitle: String
) {
    enum class Phase { UNLOCK, NO_SERVER, PERMISSION, LISTENING, CONNECTING, TRANSCRIBING, THINKING, ANSWERED, NOT_HEARD, UNREACHABLE, ERROR }

    /** One question and its answer. Only this overlay's turns are shown: earlier ones need unlocking the app. */
    data class Exchange(val prompt: String, val answer: String = "")

    var phase by mutableStateOf(Phase.LISTENING)
    var level by mutableFloatStateOf(0f)
    var locked by mutableStateOf(false)
    var speaking by mutableStateOf(false)
    /** Reading the answer aloud failed (the gateway's text-to-speech); the answer is still on screen. */
    var speechFailed by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var profile by mutableStateOf("")
    val exchanges = mutableStateListOf<Exchange>()

    val vm get() = app.viewModel
    val busy: Boolean get() = phase in setOf(Phase.LISTENING, Phase.CONNECTING, Phase.TRANSCRIBING, Phase.THINKING)

    private var job: Job? = null
    private var stopRequested = false
    /** The user silenced this turn: later sentences are not read either. */
    private var muted = false

    fun listen() {
        job?.cancel()
        speech.stop()
        job = scope.launch {
            try { runTurn() }
            catch (e: CancellationException) { throw e }
            catch (e: Throwable) { error = e.message; phase = Phase.ERROR }
            finally { level = 0f; speaking = false }
        }
    }

    /** Ends the recording now and sends what was said. */
    fun stopListening() { stopRequested = true }

    fun stopSpeaking() {
        if (!speaking) return
        muted = true
        speech.stop()
        if (!settings.deviceVoice) job?.cancel()
        speaking = false
    }

    fun cancel() { job?.cancel(); job = null; speech.stop() }

    private suspend fun runTurn() {
        error = null
        speechFailed = false
        muted = false
        if (vm.uiState.savedConfig == null) { phase = Phase.NO_SERVER; return }
        phase = Phase.LISTENING
        stopRequested = false
        val recording = voice.record({ level = it }, { stopRequested })
        val audio = recording.file ?: run { phase = Phase.NOT_HEARD; return }
        if (vm.uiState.connectionState != GatewayConnectionState.CONNECTED) phase = Phase.CONNECTING
        val connected = withTimeoutOrNull(20_000) {
            snapshotFlow { vm.uiState.connectionState }.first { it == GatewayConnectionState.CONNECTED }
        } != null
        val repo = vm.featureRepository()
        if (!connected || repo == null) { audio.delete(); phase = Phase.UNREACHABLE; return }

        profile = resolveProfile()
        val c = ParityController(repo, profile, scope)
        try {
            phase = Phase.TRANSCRIBING
            val prompt = voice.transcribe(c, audio)
            if (prompt.isBlank()) { phase = Phase.NOT_HEARD; return }
            exchanges += Exchange(prompt)
            phase = Phase.THINKING
            val chat = openVoiceChat(profile) ?: run { phase = Phase.UNREACHABLE; return }
            // The voice model and no reasoning: a thinking model spends seconds before its first word.
            settings.voiceModel.let { picked ->
                vm.applyVoiceModel(picked?.first.orEmpty(), picked?.second.orEmpty(), if (settings.voiceThinking) "" else "none")
            }
            val connection = vm.uiState.activeConnectionId.orEmpty()
            // The answer is on screen here: the push service must not also post a turn notice for it.
            com.nousresearch.hermesmobile.push.AppPresence.openSessionId = chat.storedSessionId
            vm.sendMessage(prompt)
            settings.rememberSession(connection, profile, chat.storedSessionId, System.currentTimeMillis())
            // The device voice reads each sentence as it streams in, as Gemini does; the server voice needs the whole answer.
            val deviceVoice = settings.deviceVoice
            val streamer = SpeechText.Streamer()
            // A child of this turn, so cancelling the turn stops it too.
            val reader = if (!deviceVoice) null else CoroutineScope(currentCoroutineContext()).launch {
                snapshotFlow { vm.streamingText }.collect { text ->
                    android.util.Log.d("HermesAssist", "stream len=${text.length}")
                    streamer.push(text).forEach { sentence -> speak { speaking = true; speech.enqueue(sentence) } }
                }
            }
            // sendMessage marks the chat running at once; the turn ends when it stops or the app moves elsewhere.
            snapshotFlow { vm.uiState.activeChat }.first { it == null || it.storedSessionId != chat.storedSessionId || !it.running }
            reader?.cancel()
            android.util.Log.d("HermesAssist", "turn done")
            val answer = vm.uiState.activeChat?.takeIf { it.storedSessionId == chat.storedSessionId }
                ?.messages?.lastOrNull { it.role == "assistant" }?.content.orEmpty()
            exchanges[exchanges.lastIndex] = exchanges.last().copy(answer = answer)
            settings.rememberSession(connection, profile, chat.storedSessionId, System.currentTimeMillis())
            phase = Phase.ANSWERED
            if (deviceVoice) {
                streamer.finish(answer).forEach { sentence -> speak { speaking = true; speech.enqueue(sentence) } }
                if (speaking) speak { speech.awaitIdle() }
                speaking = false
            } else if (answer.isNotBlank()) {
                speaking = true
                speak { voice.speak(c, answer) }
                speaking = false
            }
        } finally {
            c.dispose()
        }
    }

    /** Runs a speech step; a failure is reported once and never ends the turn (the answer stays on screen). */
    private suspend fun speak(block: suspend () -> Unit) {
        if (speechFailed || muted) return
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: Throwable) { speechFailed = true; speech.stop() }
    }

    /** The voice conversation to talk in: the recent one of this profile, or a new chat. */
    private suspend fun openVoiceChat(profile: String): ActiveChat? {
        val connection = vm.uiState.activeConnectionId ?: return null
        val current = vm.uiState.activeChat
        val recent = settings.lastSession(connection, profile)
            ?.takeIf { AssistPolicy.continueSession(it.second, System.currentTimeMillis()) }?.first
        if (recent != null) {
            if (current?.storedSessionId == recent) return current
            vm.openSession(HermesSession(id = recent, profile = profile, title = voiceChatTitle))
            withTimeoutOrNull(20_000) {
                snapshotFlow { vm.uiState.activeChat }.first { it?.storedSessionId == recent }
            }?.let { return it }
        }
        val previous = vm.uiState.activeChat?.storedSessionId
        vm.newChat(profile, voiceChatTitle)
        return withTimeoutOrNull(20_000) {
            snapshotFlow { vm.uiState.activeChat }.first { it != null && it.profile == profile && it.storedSessionId != previous }
        }
    }

    private fun resolveProfile(): String {
        val selected = vm.uiState.selectedProfile
        val wanted = AssistPolicy.profile(locked, settings.profile, settings.lockedProfile, selected)
        val known = vm.uiState.profiles.map { it.name }
        return if (known.isEmpty() || wanted in known) wanted else selected
    }
}
