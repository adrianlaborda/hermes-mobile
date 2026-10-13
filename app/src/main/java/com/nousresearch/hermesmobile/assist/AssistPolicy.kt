package com.nousresearch.hermesmobile.assist

/**
 * Decides when the user has finished speaking from MediaRecorder's peak amplitude, sampled every
 * [AssistPolicy.SAMPLE_MS]. The first samples measure the room's noise floor; speech is anything clearly above it.
 */
class SpeechEndpointer(
    private val silenceToEndMs: Long = 1_400,
    private val noSpeechTimeoutMs: Long = 7_000,
    private val maxMs: Long = 60_000,
    private val calibrationMs: Long = 300,
    private val minThreshold: Int = 1_200
) {
    enum class Decision { LISTEN, END_OF_SPEECH, NO_SPEECH, MAX_LENGTH }

    private var floorSum = 0L
    private var floorSamples = 0
    private var threshold = minThreshold
    private var heardSpeech = false
    private var lastLoudAt = 0L

    /** The level to draw, 0..1, relative to the speech threshold. */
    var level = 0f
        private set

    fun feed(amplitude: Int, elapsedMs: Long): Decision {
        if (elapsedMs <= calibrationMs) {
            floorSum += amplitude; floorSamples++
            threshold = maxOf(minThreshold, (floorSum / floorSamples * 2.5).toInt())
        }
        level = (amplitude.toFloat() / (threshold * 3)).coerceIn(0f, 1f)
        if (amplitude >= threshold && elapsedMs > calibrationMs) { heardSpeech = true; lastLoudAt = elapsedMs }
        return when {
            elapsedMs >= maxMs -> Decision.MAX_LENGTH
            heardSpeech && elapsedMs - lastLoudAt >= silenceToEndMs -> Decision.END_OF_SPEECH
            !heardSpeech && elapsedMs >= noSpeechTimeoutMs -> Decision.NO_SPEECH
            else -> Decision.LISTEN
        }
    }
}

object AssistPolicy {
    const val SAMPLE_MS = 100L
    /** Talking again within this window continues the same voice conversation, as Gemini Live does. */
    const val CONTINUE_WINDOW_MS = 5 * 60 * 1000L

    enum class Entry { LISTEN, UNLOCK_FIRST }

    /**
     * Mirrors Gemini's "Use Gemini without unlocking": off by default, the assistant asks to unlock before it
     * listens. When on, it listens and answers over the lock screen; anything personal still needs unlocking.
     */
    fun entry(keyguardLocked: Boolean, allowWhileLocked: Boolean): Entry =
        if (keyguardLocked && !allowWhileLocked) Entry.UNLOCK_FIRST else Entry.LISTEN

    /** The profile to talk to: the lock-screen profile while locked (if set), else the assistant's profile. */
    fun profile(keyguardLocked: Boolean, assistantProfile: String, lockedProfile: String, fallback: String): String {
        val normal = assistantProfile.ifBlank { fallback }
        return if (keyguardLocked) lockedProfile.ifBlank { normal } else normal
    }

    fun continueSession(lastUsedAt: Long, now: Long): Boolean = lastUsedAt > 0 && now - lastUsedAt in 0..CONTINUE_WINDOW_MS
}
