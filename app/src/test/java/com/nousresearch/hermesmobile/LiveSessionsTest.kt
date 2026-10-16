package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.data.LiveSessions
import com.nousresearch.hermesmobile.data.SessionDot
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSessionsTest {
    private fun snapshot(vararg rows: Pair<String, String>) = JSONObject().put("sessions", JSONArray().apply {
        rows.forEach { (stored, status) -> put(JSONObject().put("id", "rt-$stored").put("session_key", stored).put("status", status)) }
    })

    @Test fun workingThenFinishedElsewhereIsUnread() {
        val live = LiveSessions()
        assertTrue(live.apply(snapshot("a" to "working", "b" to "waiting", "c" to "idle"), viewing = null))
        assertEquals(mapOf("a" to SessionDot.WORKING, "b" to SessionDot.NEEDS_INPUT), live.dots())
        assertEquals("a", live.storedByRuntime["rt-a"])
        // a finished, b's runtime was reaped: both turns ended while nobody looked.
        assertTrue(live.apply(snapshot("a" to "idle", "c" to "idle"), viewing = null))
        assertEquals(mapOf("a" to SessionDot.UNREAD, "b" to SessionDot.UNREAD), live.dots())
        assertTrue(live.seen("a"))
        assertFalse(live.seen("a"))
        assertEquals(mapOf("b" to SessionDot.UNREAD), live.dots())
        // A new turn replaces the unread dot.
        live.apply(snapshot("b" to "starting"), viewing = null)
        assertEquals(mapOf("b" to SessionDot.WORKING), live.dots())
    }

    @Test fun theChatOnScreenNeverTurnsUnread() {
        val live = LiveSessions()
        live.apply(snapshot("a" to "working"), viewing = "a")
        live.apply(snapshot("a" to "idle"), viewing = "a")
        assertEquals(emptyMap<String, SessionDot>(), live.dots())
        assertFalse(live.apply(snapshot("a" to "idle"), viewing = null))
    }

    @Test fun backendUnreadShowsWhenNothingLiveDoes() {
        val dots = mapOf("a" to SessionDot.WORKING)
        assertEquals(SessionDot.WORKING, LiveSessions.dotFor(dots, "a", backendUnread = true))
        assertEquals(SessionDot.UNREAD, LiveSessions.dotFor(dots, "b", backendUnread = true))
        assertEquals(SessionDot.NONE, LiveSessions.dotFor(dots, "b"))
        assertEquals(SessionDot.NONE, LiveSessions.dotFor(dots, null))
    }
}
