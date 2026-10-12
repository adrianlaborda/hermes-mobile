package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.data.*
import com.nousresearch.hermesmobile.ui.components.ChatMarkdown
import org.commonmark.node.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test

class TranscriptTest {
    @Test fun structuredContentDoesNotLeakToolCallsOrReasoning() {
        val parts = JSONArray("""[{"type":"thinking","thinking":"internal"},{"type":"text","text":"**Answer**"},{"type":"tool_use","name":"terminal","input":{"cmd":"pwd"}},{"encrypted_content":"opaque"}]""")
        assertEquals("**Answer**", TranscriptText.visible(parts))
    }
    @Test fun nestedContentIsReadWithoutSerializingJson() {
        assertEquals("Hello", TranscriptText.visible(JSONObject("""{"type":"message","content":[{"type":"output_text","text":"Hello"}]}""")))
        assertEquals("", TranscriptText.visible(JSONObject("""{"usage":{"tokens":42}}""")))
    }
    @Test fun legitimateJsonAndCodeRemainUntouched() {
        val text = "```json\n{\"type\":\"reasoning\",\"answer\":42}\n```"
        assertEquals(text, TranscriptText.visible(text))
        assertEquals("{\"answer\":42}", TranscriptText.visible("{\"answer\":42}"))
    }
    @Test fun sqliteSidecarIsDecodedAndPrivateAnalysisIsExcluded() {
        val row = JSONObject().put("role", "assistant").put("content", "").put("codex_message_items",
            """[{"type":"message","phase":"analysis","content":[{"type":"output_text","text":"Private"}]},{"type":"message","phase":"final_answer","content":[{"type":"output_text","text":"Final"}]}]""")
        assertEquals("Final", ChatMessage.fromJson(row).content)
        assertEquals("", TranscriptText.sidecar("{invalid"))
    }
    @Test fun displayProjectionAndCanonicalAnswerTakePrecedence() {
        val row = JSONObject().put("role", "assistant").put("content", "Canonical").put("codex_message_items", "[{}]")
        assertEquals("Canonical", ChatMessage.fromJson(row).content)
        row.put("display_content", "")
        assertEquals("", ChatMessage.fromJson(row).content)
    }
    @Test fun reasoningSummaryDoesNotExposeEncryptedMetadata() {
        assertEquals("Summary", TranscriptText.reasoning("""[{"type":"reasoning","summary":[{"type":"summary_text","text":"Summary"}],"encrypted_content":"secret"}]"""))
        assertEquals("", TranscriptText.reasoning(JSONObject().put("encrypted_content", "secret")))
    }
    @Test fun markdownRecognizesNestedFormattingAndLiteralCode() {
        val doc = ChatMarkdown.parse("**bold *italic*** and `**literal**`\n\n```json\n{\"a\":1}\n```")
        var bold = 0; var italic = 0; var code = ""; var fenced = ""
        doc.accept(object : AbstractVisitor() {
            override fun visit(n: StrongEmphasis) { bold++; visitChildren(n) }
            override fun visit(n: Emphasis) { italic++; visitChildren(n) }
            override fun visit(n: Code) { code = n.literal }
            override fun visit(n: FencedCodeBlock) { fenced = n.literal }
        })
        assertEquals(1, bold); assertEquals(1, italic); assertEquals("**literal**", code); assertEquals("{\"a\":1}\n", fenced)
    }
    @Test fun unsafeMarkdownLinksCannotLaunchIntentsOrLocalFiles() {
        listOf("javascript:alert(1)", "intent://app", "file:///secret", "content://private", "https://user:secret@example.com").forEach { assertFalse(it, ChatMarkdown.safeLink(it)) }
        listOf("https://example.com/a", "mailto:a@example.com").forEach { assertTrue(it, ChatMarkdown.safeLink(it)) }
    }
    @Test fun streamingAndIncompleteMarkdownParseWithoutFailure() {
        val sample = "# Hello\n\n**bold** [link](https://example.com)\n\n|A|B|\n|-|-|\n|x|y|\n\n```kotlin\nval x = 1\n```"
        for (end in 0..sample.length) assertNotNull(ChatMarkdown.parse(sample.take(end)))
    }
    @Test fun renderedSpansActuallyApplyBoldItalicAndSafeLinks() {
        val node = ChatMarkdown.parse("**bold** *italic* [safe](https://example.com) [bad](javascript:evil)").firstChild
        val rendered = com.nousresearch.hermesmobile.ui.components.renderMarkdownInline(node,
            androidx.compose.ui.graphics.Color.Blue, androidx.compose.ui.graphics.Color.Gray) {}
        assertEquals("bold italic safe bad", rendered.text)
        assertTrue(rendered.spanStyles.any { it.start == 0 && it.end == 4 && it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold })
        assertTrue(rendered.spanStyles.any { it.item.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic })
        assertEquals(1, rendered.getLinkAnnotations(0, rendered.length).size)
    }
    @Test fun blankTechnicalRowsDoNotRenderAndHistoryKeysRemainDistinct() {
        val empty = ChatMessage.fromJson(JSONObject().put("role", "assistant").put("content", JSONObject().put("usage", 10)))
        assertFalse(empty.renderable)
        assertNotEquals(ChatMessage(role="assistant",content="one").stableKey, ChatMessage(role="assistant",content="two").stableKey)
        assertEquals(ChatMessage(role="assistant",content="one",live=true).stableKey, ChatMessage(role="assistant",content="two",live=true).stableKey)
    }
    @Test fun settingsMaskCredentialsWithoutHidingTokenBudgets() {
        assertTrue(com.nousresearch.hermesmobile.features.sensitiveSetting("provider.access_token", JSONObject()))
        assertTrue(com.nousresearch.hermesmobile.features.sensitiveSetting("custom", JSONObject().put("sensitive", true)))
        assertFalse(com.nousresearch.hermesmobile.features.sensitiveSetting("model.max_tokens", JSONObject()))
    }
    @Test fun multipleChoiceEncodesCustomOnlyAndRemovesRecommendedLabel() {
        assertEquals("[\"custom\"]", ClarifyAnswer.encode(true, emptyList(), "custom"))
        assertEquals("[\"one\",\"extra\"]", ClarifyAnswer.encode(true, listOf("one (Recommended)"), "extra"))
        assertEquals("one", ClarifyAnswer.encode(false, listOf("one (Recommended)"), ""))
        assertEquals("", ClarifyAnswer.encode(true, emptyList(), ""))
    }
}
