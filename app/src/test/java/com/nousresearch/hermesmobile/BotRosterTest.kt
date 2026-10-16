package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.data.HermesProfile
import com.nousresearch.hermesmobile.data.RoutedBot
import com.nousresearch.hermesmobile.data.botRosterOrder
import com.nousresearch.hermesmobile.data.previewLine
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class BotRosterTest {
    private fun bot(json: String) = HermesProfile.fromJson(JSONObject(json))

    @Test
    fun previewComesFromTheFresherConversation() {
        val chatNewer = bot("""{"name":"a","canonical_session":{"id":"c","preview":"from bot chat","last_active":200},
            "last_session":{"id":"l","preview":"from side chat","last_active":100}}""")
        assertEquals("from bot chat", chatNewer.activityPreview)
        assertEquals(200L, chatNewer.activityAt)
        assertEquals("l", chatNewer.lastSession)

        val sideNewer = bot("""{"name":"b","canonical_session":{"id":"c","preview":"old","last_active":100},
            "last_session":{"id":"l","preview":"new","last_active":300000}}""")
        assertEquals("new", sideNewer.activityPreview)

        val legacy = bot("""{"name":"c","last_session":"s1"}""")
        assertEquals("s1", legacy.lastSession)
        assertEquals("", legacy.activityPreview)
    }

    @Test
    fun pinnedFirstThenMostRecentlyActive() {
        fun routed(name: String, at: Long, pinned: Boolean = false) = RoutedBot(
            "conn", "Gateway", profile = HermesProfile(name, activityAt = at,
                uiMeta = JSONObject().put("hermes-bots", JSONObject().put("pinned", pinned)))
        )
        val order = listOf(routed("quiet", 10), routed("busy", 500), routed("fav", 1, pinned = true), routed("never", 0))
            .sortedWith(botRosterOrder).map { it.profile.name }
        assertEquals(listOf("fav", "busy", "quiet", "never"), order)
    }

    @Test
    fun previewLineIsPlainText() {
        assertEquals("Done: see the report and next steps", previewLine("**Done**: see [the report](https://x.y)\n\n## and `next` steps"))
    }
}
