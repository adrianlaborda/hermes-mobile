package com.nousresearch.hermesmobile.assist

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Base64
import com.nousresearch.hermesmobile.data.HermesHttpException
import com.nousresearch.hermesmobile.features.ParityController
import com.nousresearch.hermesmobile.features.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The assistant's voice: records until the user stops talking, then uses the gateway's own speech-to-text and
 * text-to-speech (the same `/api/audio/…` endpoints as the chat's dictation and read-aloud).
 */
class AssistVoice(private val context: Context) {
    private val lease = "mobile-assist:${UUID.randomUUID()}"

    class Recording(val file: File?, val decision: SpeechEndpointer.Decision)

    /** Records until [SpeechEndpointer] decides the utterance ended, or [stopRequested] (the user tapped stop). */
    suspend fun record(onLevel: (Float) -> Unit, stopRequested: () -> Boolean): Recording {
        val file = withContext(Dispatchers.IO) { File.createTempFile("hermes-voice-", ".m4a", context.cacheDir) }
        @Suppress("DEPRECATION")
        val recorder = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        var keep = false
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(16000); recorder.setAudioEncodingBitRate(64000)
            recorder.setMaxFileSize(8L * 1024 * 1024)
            recorder.setOutputFile(file.path)
            recorder.prepare(); recorder.start()
            val endpointer = SpeechEndpointer()
            var elapsed = 0L
            var decision: SpeechEndpointer.Decision
            do {
                delay(AssistPolicy.SAMPLE_MS)
                elapsed += AssistPolicy.SAMPLE_MS
                decision = endpointer.feed(recorder.maxAmplitude, elapsed)
                if (decision == SpeechEndpointer.Decision.LISTEN && stopRequested()) decision = SpeechEndpointer.Decision.END_OF_SPEECH
                onLevel(endpointer.level)
            } while (decision == SpeechEndpointer.Decision.LISTEN)
            onLevel(0f)
            runCatching { recorder.stop() }.getOrElse { return Recording(null, SpeechEndpointer.Decision.NO_SPEECH) }
            keep = decision != SpeechEndpointer.Decision.NO_SPEECH
            return Recording(file.takeIf { keep }, decision)
        } finally {
            recorder.release()
            if (!keep) file.delete()
        }
    }

    suspend fun transcribe(c: ParityController, audio: File): String {
        try {
            lease(c, "stt", true)
            val data = withContext(Dispatchers.IO) {
                require(audio.length() <= 8 * 1024 * 1024) { "Recording is too large" }
                "data:audio/mp4;base64," + Base64.encodeToString(audio.readBytes(), Base64.NO_WRAP)
            }
            return c.api("/api/audio/transcribe", "POST", json("data_url" to data, "mime_type" to "audio/mp4"), 180)
                .optString("transcript").trim()
        } finally {
            audio.delete()
            withContext(NonCancellable) { withTimeoutOrNull(10_000) { runCatching { lease(c, "stt", false) } } }
        }
    }

    /** Speaks [text] and returns when playback ends. Cancelling stops it. */
    suspend fun speak(c: ParityController, text: String) {
        if (text.isBlank()) return
        var file: File? = null
        val player = MediaPlayer()
        try {
            lease(c, "tts", true)
            val data = c.api("/api/audio/speak", "POST", json("text" to text.take(40_000)), 600).optString("data_url")
            require(data.startsWith("data:audio/") && ";base64," in data && data.length <= 32 * 1024 * 1024) { "Unsupported or oversized speech response" }
            file = withContext(Dispatchers.IO) {
                File.createTempFile("hermes-speech-", ".audio", context.cacheDir).apply { writeBytes(Base64.decode(data.substringAfter(','), Base64.DEFAULT)) }
            }
            player.setDataSource(file.path)
            suspendCancellableCoroutine { continuation ->
                player.setOnPreparedListener { it.start() }
                player.setOnCompletionListener { if (continuation.isActive) continuation.resume(Unit) }
                player.setOnErrorListener { _, _, _ ->
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Audio playback failed")); true
                }
                player.prepareAsync()
                continuation.invokeOnCancellation { runCatching { player.stop() } }
            }
        } finally {
            player.release()
            file?.delete()
            withContext(NonCancellable) { withTimeoutOrNull(10_000) { runCatching { lease(c, "tts", false) } } }
        }
    }

    private suspend fun lease(c: ParityController, kind: String, active: Boolean) {
        try {
            c.api("/api/audio/$kind-lease", "POST", json("lease" to lease, "active" to active), 180)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HermesHttpException) {
            if (e.status !in listOf(404, 405, 501)) throw e
        }
    }
}
