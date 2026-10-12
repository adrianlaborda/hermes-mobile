package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.data.ContextBreakdown
import com.nousresearch.hermesmobile.data.ContextPressure
import com.nousresearch.hermesmobile.data.SessionUsage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionContextTest {
    @Test
    fun parsesGatewayUsageSnapshot() {
        val usage = SessionUsage.from(JSONObject("""{"model":"m","input":1200,"output":300,"reasoning":50,"prompt":99999,
            "total":1550,"calls":4,"context_used":155431,"context_max":1048576,"context_estimated":true,
            "compressions":1,"cache_hit_pct":63,"avg_tps":41.27,"avg_latency_s":2.3}"""))
        assertEquals(155431L, usage.contextUsed)
        assertEquals(1048576L, usage.contextLimit)
        assertEquals(14, usage.contextPercent)
        assertEquals(63, usage.cacheHitPercent)
        assertEquals(41.27, usage.avgTokensPerSecond!!, 0.001)
        assertTrue(usage.contextEstimated)
        assertTrue(usage.hasTraffic)
    }

    @Test
    fun lifetimePromptTokensAreNeverTreatedAsContext() {
        val usage = SessionUsage.from(JSONObject("""{"prompt":500000,"calls":2}"""))
        assertEquals(0L, usage.contextUsed)
        assertNull(usage.contextPercent)
        assertNull(usage.cacheHitPercent)
        assertNull(usage.avgTokensPerSecond)
    }

    @Test
    fun eventsWithoutUsageDoNotWipeTheSnapshot() {
        assertNull(SessionUsage.fromEvent(JSONObject("""{"session_id":"s"}""")))
        assertNull(SessionUsage.fromEvent(JSONObject("""{"usage":{}}""")))
        assertEquals(3, SessionUsage.fromEvent(JSONObject("""{"usage":{"calls":3}}"""))!!.calls)
    }

    @Test
    fun parsesBreakdownWithShares() {
        val d = ContextBreakdown.from(JSONObject("""{"context_used":2000,"context_max":10000,"context_estimated":false,
            "categories":[{"id":"system_prompt","label":"System prompt","tokens":500},{"id":"conversation","label":"","tokens":1500}],
            "context_files":[{"label":"AGENTS.md","path":"/w/AGENTS.md","status":"loaded"}]}"""))
        assertEquals(20, d.percent)
        assertEquals(8000L, d.free)
        assertEquals(0.25f, d.share(d.categories[0]), 0.0001f)
        assertEquals("conversation", d.categories[1].label)
        assertEquals("AGENTS.md", d.files.single().label)
    }

    @Test
    fun breakdownFillsTheRingOfAResumedChat() {
        // A resumed chat: the gateway has measured nothing in this process yet, so session.usage has no window.
        val usage = SessionUsage.from(JSONObject("""{"calls":0,"input":0,"output":0,"total":0}"""))
        assertNull(usage.contextPercent)
        val estimate = ContextBreakdown.from(JSONObject("""{"context_used":45000,"context_max":200000,"context_estimated":true}"""))
        val filled = usage.withContextFrom(estimate)
        assertEquals(22, filled.contextPercent)
        assertTrue(filled.contextEstimated)
        // A lazy session (no agent built yet) answers a zero window: keep what we had.
        val lazy = ContextBreakdown.from(JSONObject("""{"context_used":0,"context_max":0}"""))
        assertEquals(usage, usage.withContextFrom(lazy))
        assertEquals(usage, usage.withContextFrom(null))
    }

    @Test
    fun pressureBands() {
        assertEquals(ContextPressure.UNKNOWN, ContextPressure.of(null))
        assertEquals(ContextPressure.LOW, ContextPressure.of(59))
        assertEquals(ContextPressure.MEDIUM, ContextPressure.of(60))
        assertEquals(ContextPressure.HIGH, ContextPressure.of(85))
    }
}
