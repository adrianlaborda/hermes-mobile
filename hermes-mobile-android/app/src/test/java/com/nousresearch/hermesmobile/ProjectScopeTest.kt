package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.features.projectSessions
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProjectScopeTest {
    @Test fun hydratedLanesKeepProfileAndIgnoreForeignRows() {
        val payload = JSONObject("""{"repos":[{"groups":[{"sessions":[
            {"id":"a","profile":"coder","last_active":10},
            {"id":"b","profile":"other","last_active":30},
            {"id":"c","profile":null,"last_active":20}
        ]}]}]}""")
        val rows = projectSessions(payload, "coder")
        assertEquals(listOf("c", "a"), rows.map { it.id })
        assertTrue(rows.all { it.profile == "coder" })
    }
    @Test fun repeatedLaneRowsDoNotDuplicateConversations() {
        val payload = JSONObject("""{"repos":[{"groups":[{"sessions":[{"id":"a"}]},{"sessions":[{"id":"a"},{"id":""}]}]}]}""")
        assertEquals(listOf("a"), projectSessions(payload, "default").map { it.id })
    }
    @Test fun overviewPreviewsAreNotMistakenForHydratedHistory() {
        assertTrue(projectSessions(JSONObject("""{"previewSessions":[{"id":"a"}],"repos":[]}"""), "default").isEmpty())
    }
}
