package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.ui.screens.TurnRole
import com.nousresearch.hermesmobile.ui.screens.assistantTurnEnds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatMessageActionsTest {
    private data class Row(val key: String, val role: TurnRole, val actionable: Boolean = true)

    private fun ends(rows: List<Row>, open: Boolean = false) =
        assistantTurnEnds(rows, { it.key }, { it.role }, { it.actionable }, newestTurnOpen = open)

    @Test
    fun actionsSitOnceUnderEachTurnsFinalAnswer() {
        val rows = listOf(
            Row("u1", TurnRole.USER), Row("a1", TurnRole.ASSISTANT), Row("tool", TurnRole.OTHER), Row("a2", TurnRole.ASSISTANT),
            Row("u2", TurnRole.USER), Row("a3", TurnRole.ASSISTANT)
        )
        val result = ends(rows)
        assertEquals(setOf("a2", "a3"), result.keys)
        assertEquals("a3", result.retryKey)
    }

    @Test
    fun runningTurnHasNoActionsYet() {
        val rows = listOf(Row("u1", TurnRole.USER), Row("a1", TurnRole.ASSISTANT), Row("u2", TurnRole.USER), Row("a2", TurnRole.ASSISTANT))
        val result = ends(rows, open = true)
        assertEquals(setOf("a1"), result.keys)
        assertNull(result.retryKey)
    }

    @Test
    fun nonActionableRowsFallBackToTheTurnsEarlierAnswer() {
        val rows = listOf(Row("u1", TurnRole.USER), Row("a1", TurnRole.ASSISTANT), Row("a2", TurnRole.ASSISTANT, actionable = false))
        assertEquals(setOf("a1"), ends(rows).keys)
    }

    @Test
    fun unansweredLastPromptOffersNoRetry() {
        val rows = listOf(Row("u1", TurnRole.USER), Row("a1", TurnRole.ASSISTANT), Row("u2", TurnRole.USER))
        val result = ends(rows)
        assertEquals(setOf("a1"), result.keys)
        assertNull(result.retryKey)
    }
}
