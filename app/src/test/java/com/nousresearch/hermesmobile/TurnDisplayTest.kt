package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.data.ChatMessage
import com.nousresearch.hermesmobile.data.TurnDisplay
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TurnDisplayTest {
    private fun row(role: String, content: String, kind: String? = null, metadata: String? = null) =
        ChatMessage.fromJson(JSONObject().put("role", role).put("content", content).apply {
            kind?.let { put("display_kind", it) }
            metadata?.let { put("display_metadata", it) }
        })

    @Test
    fun attachedFileBodiesStayOutOfTheBubble() {
        val message = row("user", "@file:.hermes/attachments/automation.json\n\nCópialo tú en el directorio\n\n" +
            "--- Attached Context ---\n\n📄 @file:.hermes/attachments/automation.json (2 KB)\n```json\n{\"a\": 1}\n```")
        assertEquals("Cópialo tú en el directorio", message.content)
        assertEquals(listOf("automation.json"), message.attachments)
        assertEquals("@file:.hermes/attachments/automation.json\nCópialo tú en el directorio", message.editText)
    }

    @Test
    fun aRefTheBackendStrippedFromTheProseComesBackAsAChip() {
        val message = row("user", "Revisa esto\n\n--- Context Warnings ---\n- big file\n\n--- Attached Context ---\n\n@file:`docs/My Notes.md`\nbody")
        assertEquals("Revisa esto", message.content)
        assertEquals(listOf("My Notes.md"), message.attachments)
    }

    @Test
    fun aRefTypedMidProseIsNotAnAttachment() {
        val message = row("user", "Compara @file:a.md con b")
        assertEquals("Compara @file:a.md con b", message.content)
        assertEquals(emptyList<String>(), message.attachments)
    }

    @Test
    fun anExpandedSkillShowsItsInvocation() {
        val text = "[IMPORTANT: The user has invoked the \"b2b-outreach\" skill, indicating they want you to follow its " +
            "instructions. The full skill content is loaded below.]\n\n# Skill body\n\nThe user has provided the following " +
            "instruction alongside the skill invocation: escribe a  diez clubes\n\n[Runtime note: x]"
        assertEquals("/b2b-outreach escribe a diez clubes", TurnDisplay.skillInvocation(text))
        assertEquals("/b2b-outreach escribe a diez clubes", row("user", text).content)
        assertNull(TurnDisplay.skillInvocation("Hola"))
    }

    @Test
    fun scaffoldingIsNotTheUsersWords() {
        assertFalse(row("user", "[Background process proc_1 heartbeat #3 at 12:00]").renderable)
        assertEquals("hola", row("user", "[Triggering message id: `42` — use as `message_id` for reply/react/pin via the discord tools.]\nhola").content)
    }

    @Test
    fun noticesSayWhatHappenedAndKeepTheReport() {
        assertEquals("", row("user", "[System: The active model for this chat has changed to qwen3.6.]", "model_switch").content)
        val process = row("user", "[IMPORTANT: 2 background processes completed. Treat these results as one batch.]\n\n" +
            "[IMPORTANT: Background process proc_1 completed normally (exit code 0).\nOutput: ok]\n\n" +
            "[IMPORTANT: Background process proc_2 completed normally (exit code 0).\nOutput: done]",
            "process_complete", "{\"display_text\": \"2 Background Processes Finished\"}")
        assertEquals("2 Background Processes Finished", process.content)
        assertEquals("Background process proc_1 completed normally (exit code 0).\nOutput: ok\n\n" +
            "Background process proc_2 completed normally (exit code 0).\nOutput: done", process.report)
        val delegation = row("user", "[ASYNC DELEGATION COMPLETE — deleg_1]\nGoal: x\n--- RESULT ---\nThe answer\n" +
            "Full live transcript (complete tool/assistant trace): /tmp/t.json\n", "async_delegation_complete")
        assertEquals("", delegation.content)
        assertEquals("The answer", delegation.report)
    }

    @Test
    fun aRefPointsAtItsFileOnTheGateway() {
        assertEquals("attachments/a.json", TurnDisplay.refPath("@file:attachments/a.json"))
        assertEquals("/home/h/.hermes/attachments/a.json", TurnDisplay.resolve("attachments/a.json", "/home/h/.hermes/"))
        assertEquals("/tmp/a.json", TurnDisplay.resolve("/tmp/a.json", "/home/h"))
        assertEquals("/srv/My Notes.md", TurnDisplay.refPath("@file:`/srv/My Notes.md`"))
        assertEquals("/tmp/shot.png", TurnDisplay.refPath("@image:/tmp/shot.png"))
        assertEquals("My Notes.md", TurnDisplay.refName("@file:`/srv/My Notes.md`"))
    }

    @Test
    fun aLargePasteIsNamedAsSuch() {
        assertEquals(true, TurnDisplay.isPastedContent("pasted_content_20261018_130000_ab12cd.txt"))
        assertEquals(true, TurnDisplay.isPastedContent("~/work/pasted_content_20261018_130000_ab12cd-2.txt"))
        assertEquals(false, TurnDisplay.isPastedContent("notes.txt"))
    }

    @Test
    fun listPreviewDropsAttachmentRefs() {
        assertEquals("Extrae el número de cuenta", TurnDisplay.preview("Extrae el número de cuenta @image:/home/h/.hermes/images/a.jpg"))
        assertEquals("informe.pdf", TurnDisplay.preview("@file:attachments/informe.pdf"))
        assertEquals("hola", TurnDisplay.preview("hola"))
    }
}
