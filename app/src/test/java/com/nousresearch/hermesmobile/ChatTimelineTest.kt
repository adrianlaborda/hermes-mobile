package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.data.*
import org.junit.Assert.*
import org.junit.Test

class ChatTimelineTest {
    @Test fun pinnedContextEndsAtTheNextMessage() {
        val timeline = chatTimeline(listOf(ChatMessage(role="user",content="Request"),
            ChatMessage(role="tool",content="result"), ChatMessage(role="assistant",content="Answer")))
        assertNull(timeline.contextForActivity(0))
        assertEquals("Request", timeline.contextForActivity(1)?.message?.content)
        assertNull(timeline.contextForActivity(2))
        assertNull(timeline.contextForActivity(-1))
    }
    @Test fun nullableGatewayDisplayKindsStillGroupOnAndroid() {
        val rows = listOf(org.json.JSONObject.NULL, "null", "").flatMap { kind -> listOf(
            ChatMessage.fromJson(org.json.JSONObject().put("role", "assistant").put("content", "")
                .put("reasoning", "Thinking").put("display_kind", kind)),
            ChatMessage.fromJson(org.json.JSONObject().put("role", "tool").put("content", "result")
                .put("display_kind", kind))) }
        val timeline = chatTimeline(rows)
        assertEquals(1, timeline.size)
        assertEquals(6, (timeline.single() as ChatTimelineItem.Activity).steps.size)
    }
    @Test fun legacyNullStringDoesNotSplitActivity() {
        val rows = listOf(ChatMessage(role="assistant",content="",reasoning="Thinking",displayKind="null"),
            ChatMessage(role="tool",content="result",displayKind="null"))
        assertEquals(1, chatTimeline(rows).size)
    }
    @Test fun repeatedToolsAndReasoningBecomeOneActivityWithoutLosingAnswer() {
        val user = ChatMessage(rowId = 1, role = "user", content = "Change language")
        val steps = (1..8).flatMap { n -> listOf(
            ChatMessage(rowId = n * 2L, role = "assistant", content = "", reasoning = "Think $n"),
            ChatMessage(rowId = n * 2L + 1, role = "tool", content = "result $n", toolName = "execute_code")) }
        val answer = ChatMessage(rowId = 20, role = "assistant", content = "Done", reasoning = "Verified")
        val result = chatTimeline(listOf(user) + steps + answer)
        assertEquals(3, result.size)
        assertEquals(17, (result[1] as ChatTimelineItem.Activity).steps.size)
        assertEquals("Done", (result[2] as ChatTimelineItem.Message).message.content)
        assertEquals("", (result[2] as ChatTimelineItem.Message).message.reasoning)
        assertEquals("Verified", answer.reasoning)
    }
    @Test fun neverGroupsAcrossUserOrVisibleAssistantMessages() {
        val result = chatTimeline(listOf(
            ChatMessage(role="tool", content="first"),
            ChatMessage(role="assistant", content="Here is an update"),
            ChatMessage(role="tool", content="second"),
            ChatMessage(role="user", content="Stop")))
        assertEquals(4, result.size)
        assertTrue(result[1] is ChatTimelineItem.Message)
        assertTrue(result[3] is ChatTimelineItem.Message)
    }
    @Test fun noticesAndAttachmentsAreNeverSwallowed() {
        val result = chatTimeline(listOf(
            ChatMessage(role="assistant", content="", reasoning="note", attachments=listOf("image.png")),
            ChatMessage(role="assistant", content="Failure", displayKind="failed_turn")))
        assertEquals(listOf("image.png"), (result[1] as ChatTimelineItem.Message).message.attachments)
        assertEquals("failed_turn", (result[2] as ChatTimelineItem.Message).message.displayKind)
    }
    @Test fun duplicateLegacyKeysDoNotCrashLazyList() {
        val row = ChatMessage(role="tool", content="same")
        val user = ChatMessage(role="user", content="again")
        val result = chatTimeline(listOf(row,user,row,user,row))
        assertEquals(result.size, result.map { it.key }.toSet().size)
    }
    @Test fun liveReasoningHasStableGroupKeyAcrossTokens() {
        val a=ChatMessage(role="assistant",content="",reasoning="a",live=true)
        val first=chatTimeline(listOf(a))
        val second=chatTimeline(listOf(a.copy(reasoning="ab",content="Answer")))
        assertEquals(first.first().key,second.first().key)
        assertTrue((second.first() as ChatTimelineItem.Activity).steps.single().live)
        assertEquals("Answer", (second.last() as ChatTimelineItem.Message).message.content)
    }
    @Test fun hiddenAndSystemRowsStayHidden() {
        assertTrue(chatTimeline(listOf(ChatMessage(role="system",content="secret"),
            ChatMessage(role="tool",content="hidden",displayKind="hidden"))).isEmpty())
    }
}
