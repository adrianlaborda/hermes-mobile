package com.nousresearch.hermesmobile.assist

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * The device's text-to-speech (on a Pixel, Google's neural voices). It starts at once, works offline with
 * installed voices, and takes sentences one by one, so a streaming answer is read as it arrives.
 */
class AndroidSpeech(context: Context, private val usage: Int = AudioAttributes.USAGE_ASSISTANT) {
    private val ready = CompletableDeferred<Boolean>()
    private val pending = AtomicInteger(0)
    private val _speaking = MutableStateFlow(false)
    private var nextId = 0

    /** True while sentences are queued or playing. */
    val speaking: StateFlow<Boolean> = _speaking

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { android.util.Log.d("HermesAssist", "tts start $utteranceId") }
            override fun onDone(utteranceId: String?) = finished()
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) = finished()
            override fun onError(utteranceId: String?, errorCode: Int) = finished()
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finished()
        })
    }

    private fun finished() { if (pending.decrementAndGet() <= 0) { pending.set(0); _speaking.value = false } }

    private suspend fun awaitReady() {
        check(ready.await()) { "No text-to-speech engine on this device" }
        if (!configured) configure()
    }

    private var configured = false
    private fun configure() {
        configured = true
        tts.setAudioAttributes(AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        // The voice the user set up in Android's text-to-speech settings, unless it speaks another language.
        val locale = Locale.getDefault()
        val current = runCatching { tts.voice ?: tts.defaultVoice }.getOrNull()
        if (current == null || current.locale.language != locale.language) tts.language = locale
        android.util.Log.d("HermesAssist", "tts voice=${runCatching { tts.voice?.name }.getOrNull()} quality=${runCatching { tts.voice?.quality }.getOrNull()}")
    }

    /** Queues one sentence after whatever is playing. */
    suspend fun enqueue(sentence: String) {
        if (sentence.isBlank()) return
        awaitReady()
        pending.incrementAndGet()
        _speaking.value = true
        android.util.Log.d("HermesAssist", "tts enqueue ${sentence.take(40)}")
        if (tts.speak(sentence, TextToSpeech.QUEUE_ADD, null, "hermes-${nextId++}") != TextToSpeech.SUCCESS) finished()
    }

    /** Suspends until everything queued has been spoken (or stopped). */
    suspend fun awaitIdle() { _speaking.first { !it } }

    /** Reads a whole Markdown answer and returns when done. */
    suspend fun speak(markdown: String) {
        stop()
        SpeechText.sentences(markdown).forEach { enqueue(it) }
        awaitIdle()
    }

    fun stop() {
        if (ready.isCompleted) tts.stop()
        pending.set(0)
        _speaking.value = false
    }

    fun shutdown() { stop(); tts.shutdown() }
}
