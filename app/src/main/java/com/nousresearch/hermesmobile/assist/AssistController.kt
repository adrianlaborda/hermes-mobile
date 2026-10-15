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
import kotlinx.coroutines.async
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
    private val recognizer: DeviceRecognizer,
    private val settings: AssistSettings,
    private val voiceChatTitle: String
) {
    private companion object { const val TURN_TIMEOUT_MS = 5 * 60 * 1000L }

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
    /** What the device recognizer has heard so far, shown while the user speaks. */
    var partial by mutableStateOf("")
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
    fun stopListening() { stopRequested = true; recognizer.stop() }

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
        val turn = CoroutineScope(currentCoroutineContext())
        // While the user talks: wake the device voice and get the voice chat ready with its model, so neither
        // waits for the transcript.
        if (settings.deviceVoice) turn.launch { speech.warmUp() }
        val prepared = turn.async { prepareChat() }
        partial = ""
        // The device recognizer transcribes as the user speaks; the server path records, then transcribes.
        val heard: String?
        var audio: java.io.File? = null
        val onDevice = settings.deviceRecognition && recognizer.available
        val recognized = if (!onDevice) Result.failure(IllegalStateException("server recognition"))
            else runCatching { recognizer.listen({ partial = it }, { level = it }) }
                .onFailure { if (it is CancellationException) throw it; android.util.Log.w("HermesAssist", "device recognition failed, using the server: ${it.message}") }
        if (recognized.isSuccess) {
            heard = recognized.getOrNull()
            if (heard == null) { prepared.cancel(); phase = Phase.NOT_HEARD; return }
        } else {
            // No recognizer, or it failed: record and let the server transcribe (Whisper).
            heard = null
            partial = ""
            audio = voice.record({ level = it }, { stopRequested }).file ?: run { prepared.cancel(); phase = Phase.NOT_HEARD; return }
        }
        if (vm.uiState.connectionState != GatewayConnectionState.CONNECTED) phase = Phase.CONNECTING
        val ready = prepared.await()
        val repo = vm.featureRepository()
        if (ready == null || repo == null) { audio?.delete(); phase = Phase.UNREACHABLE; return }
        val chat = ready
        val c = ParityController(repo, profile, scope)
        try {
            val prompt = heard ?: run {
                phase = Phase.TRANSCRIBING
                voice.transcribe(c, audio!!)
            }
            partial = ""
            if (prompt.isBlank()) { phase = Phase.NOT_HEARD; return }
            exchanges += Exchange(prompt)
            phase = Phase.THINKING
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
                    streamer.push(text).forEach { sentence -> speak { speaking = true; speech.enqueue(sentence) } }
                }
            }
            // The turn ends when an answer newer than our question is in the chat and nothing runs. `running` alone
            // is not enough: a reconnect reloads the chat from the server mid-turn, briefly not running and without
            // the answer yet.
            val answersBefore = chat.messages.count { it.role == "assistant" }
            withTimeoutOrNull(TURN_TIMEOUT_MS) {
                snapshotFlow { vm.uiState.activeChat }.first { c ->
                    c == null || c.storedSessionId != chat.storedSessionId ||
                        (!c.running && c.messages.count { it.role == "assistant" } > answersBefore)
                }
            }
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

    /** Connected, the voice chat open and the voice model applied; null when the server cannot be reached. */
    private suspend fun prepareChat(): ActiveChat? {
        val connected = withTimeoutOrNull(20_000) {
            snapshotFlow { vm.uiState.connectionState }.first { it == GatewayConnectionState.CONNECTED }
        } != null
        if (!connected || vm.featureRepository() == null) return null
        profile = resolveProfile()
        val chat = openVoiceChat(profile) ?: return null
        // The voice model and no reasoning: a thinking model spends seconds before its first word.
        val picked = settings.voiceModel
        val applied = vm.applyVoiceModel(picked?.first.orEmpty(), picked?.second.orEmpty(), if (settings.voiceThinking) "" else "none")
        android.util.Log.d("HermesAssist", "voice model ${picked ?: "profile"} applied=$applied")
        return vm.uiState.activeChat?.takeIf { it.storedSessionId == chat.storedSessionId } ?: chat
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
        // Unset means the server's main profile, not whatever the app last selected (a notifications picker, a
        // chat filter): the assistant must answer from the same place every time, lock screen included.
        val known = vm.uiState.profiles.map { it.name }
        val main = if (known.isEmpty() || "default" in known) "default" else known.first()
        val wanted = AssistPolicy.profile(locked, settings.profile, settings.lockedProfile, main)
        return if (known.isEmpty() || wanted in known) wanted else main
    }
}
