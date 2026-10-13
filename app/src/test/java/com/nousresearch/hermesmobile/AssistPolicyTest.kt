package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.assist.AssistPolicy
import com.nousresearch.hermesmobile.assist.SpeechEndpointer
import com.nousresearch.hermesmobile.assist.SpeechEndpointer.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistPolicyTest {
    private fun run(endpointer: SpeechEndpointer, levels: List<Int>): Pair<Decision, Long> {
        var t = 0L
        for (amplitude in levels) {
            t += AssistPolicy.SAMPLE_MS
            val d = endpointer.feed(amplitude, t)
            if (d != Decision.LISTEN) return d to t
        }
        return Decision.LISTEN to t
    }

    @Test
    fun endsAfterSilenceFollowingSpeech() {
        val quiet = List(3) { 300 }
        val speech = List(20) { 9000 }
        val silence = List(30) { 400 }
        val (decision, at) = run(SpeechEndpointer(), quiet + speech + silence)
        assertEquals(Decision.END_OF_SPEECH, decision)
        assertEquals(2300 + 1400L, at)
    }

    @Test
    fun shortPausesDoNotEndTheUtterance() {
        val levels = List(3) { 300 } + List(10) { 9000 } + List(8) { 400 } + List(10) { 9000 }
        assertEquals(Decision.LISTEN, run(SpeechEndpointer(), levels).first)
    }

    @Test
    fun noisyRoomRaisesTheThreshold() {
        // A floor of 2000 puts the threshold at 5000: steady 3000 hum is not speech.
        val (decision, _) = run(SpeechEndpointer(), List(3) { 2000 } + List(80) { 3000 })
        assertEquals(Decision.NO_SPEECH, decision)
    }

    @Test
    fun givesUpWithoutSpeechAndCapsLength() {
        assertEquals(Decision.NO_SPEECH, run(SpeechEndpointer(), List(100) { 200 }).first)
        assertEquals(Decision.MAX_LENGTH, run(SpeechEndpointer(maxMs = 2_000), List(3) { 200 } + List(50) { 9000 }).first)
    }

    @Test
    fun lockScreenFollowsGeminiDefaults() {
        assertEquals(AssistPolicy.Entry.UNLOCK_FIRST, AssistPolicy.entry(keyguardLocked = true, allowWhileLocked = false))
        assertEquals(AssistPolicy.Entry.LISTEN, AssistPolicy.entry(keyguardLocked = true, allowWhileLocked = true))
        assertEquals(AssistPolicy.Entry.LISTEN, AssistPolicy.entry(keyguardLocked = false, allowWhileLocked = false))
    }

    @Test
    fun lockedProfileOnlyAppliesWhileLocked() {
        assertEquals("guest", AssistPolicy.profile(true, "work", "guest", "default"))
        assertEquals("work", AssistPolicy.profile(false, "work", "guest", "default"))
        assertEquals("work", AssistPolicy.profile(true, "work", "", "default"))
        assertEquals("default", AssistPolicy.profile(false, "", "guest", "default"))
    }

    @Test
    fun continuesARecentVoiceConversationOnly() {
        val now = 10_000_000L
        assertTrue(AssistPolicy.continueSession(now - 60_000, now))
        assertFalse(AssistPolicy.continueSession(now - AssistPolicy.CONTINUE_WINDOW_MS - 1, now))
        assertFalse(AssistPolicy.continueSession(0, now))
    }
}
