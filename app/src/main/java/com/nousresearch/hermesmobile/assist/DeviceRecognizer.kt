package com.nousresearch.hermesmobile.assist

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android's speech recognizer (on a Pixel, Google's, on the device when the language pack is installed). It shows
 * the words while the user speaks, ends on its own when they stop, and needs no upload: faster than recording and
 * transcribing on the server. Must be used from the main thread.
 */
class DeviceRecognizer(private val context: Context) {
    class RecognizerException(val code: Int) : Exception("Speech recognition failed ($code)")

    private var active: SpeechRecognizer? = null

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    /** Ends listening now; the recognizer still returns what it heard. */
    fun stop() { active?.stopListening() }

    /** The final transcript, or null when nothing was said. */
    suspend fun listen(onPartial: (String) -> Unit, onLevel: (Float) -> Unit): String? =
        try {
            listenWith(onDevice = true, onPartial, onLevel)
        } catch (e: RecognizerException) {
            // No offline pack for this language: Google's network recognizer instead.
            if (e.code in NEEDS_FALLBACK) listenWith(onDevice = false, onPartial, onLevel) else throw e
        }

    private suspend fun listenWith(onDevice: Boolean, onPartial: (String) -> Unit, onLevel: (Float) -> Unit): String? =
        suspendCancellableCoroutine { continuation ->
            val useOnDevice = onDevice && Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            if (onDevice && !useOnDevice) { continuation.resumeWithException(RecognizerException(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)); return@suspendCancellableCoroutine }
            val recognizer = if (useOnDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                else SpeechRecognizer.createSpeechRecognizer(context)
            active = recognizer
            fun done(block: () -> Unit) {
                if (!continuation.isActive) return
                active = null
                recognizer.destroy()
                block()
            }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) = done {
                    continuation.resume(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.ifBlank { null })
                }
                override fun onPartialResults(partial: Bundle?) {
                    partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onPartial)
                }
                override fun onError(error: Int) = done {
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) continuation.resume(null)
                    else continuation.resumeWithException(RecognizerException(error))
                }
                // RMS arrives in dB, about -2 (silence) to 10 (loud speech).
                override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { onLevel(0f) }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val language = Locale.getDefault().toLanguageTag()
            recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_000L))
            continuation.invokeOnCancellation { active = null; recognizer.cancel(); recognizer.destroy() }
        }

    private companion object {
        val NEEDS_FALLBACK = setOf(
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED, SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT
        )
    }
}
