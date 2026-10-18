package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.data.ToolArtifacts
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolArtifactsTest {
    @Test
    fun readingOrFetchingIsNotDelivering() {
        val read = ToolArtifacts.from("read_file", JSONObject("""{"path":"/home/me/agent-guide.md"}"""), "# Guide")
        val fetched = ToolArtifacts.from("web_extract", JSONObject("""{"urls":["https://opencode.ai/llms.txt"]}"""),
            JSONObject("""{"content":"see /docs/agent-guide.md and https://opencode.ai/llms.txt"}"""))
        assertEquals(emptyList<String>(), read + fetched)
    }

    @Test
    fun pathsMentionedInOutputAreNotFiles() {
        val terminal = ToolArtifacts.from("terminal", JSONObject("""{"command":"cat README.md"}"""),
            JSONObject("""{"output":"wrote /tmp/notes.md\nsee ./llms.txt"}"""))
        assertEquals(emptyList<String>(), terminal)
    }

    @Test
    fun writtenFilesAndMediaTagsAre() {
        assertEquals(listOf("/srv/out/report.pdf"),
            ToolArtifacts.from("write_file", JSONObject("""{"path":"/srv/out/report.pdf","content":"…"}"""), "ok"))
        assertEquals(listOf("/tmp/chart.png"),
            ToolArtifacts.from("terminal", JSONObject("""{"command":"python plot.py"}"""), "done MEDIA:/tmp/chart.png"))
        assertEquals(listOf("/srv/site/index.html"),
            ToolArtifacts.from("tool_call", JSONObject("""{"calls":[{"name":"write_file","arguments":{"path":"/srv/site/index.html"}}]}"""), "ok"))
    }

    @Test
    fun aUrlIsNotAFile() {
        assertEquals(emptyList<String>(),
            ToolArtifacts.from("desktop_preview", JSONObject("""{"action":"open","target":"https://example.com/a.html"}"""), null))
    }

    @Test
    fun onlyAUsersMessageAttachesFileReferences() {
        fun row(role: String) = JSONObject().put("role", role).put("content", "@file:/srv/docs/agent-guide.md\n\nRead it first")
        assertEquals(listOf("agent-guide.md"), com.nousresearch.hermesmobile.data.ChatMessage.fromJson(row("user")).attachments)
        assertEquals(emptyList<String>(), com.nousresearch.hermesmobile.data.ChatMessage.fromJson(row("assistant")).attachments)
    }

    @Test
    fun readingToolsDeliverNothingToPlay() {
        assertEquals(false, ToolArtifacts.delivers("read_file"))
        assertEquals(true, ToolArtifacts.delivers("text_to_speech"))
    }

    @Test
    fun aFailedWriteDeliversNothing() {
        assertEquals(emptyList<String>(), ToolArtifacts.from("write_file", JSONObject("""{"path":"/srv/out/a.md"}"""),
            JSONObject("""{"success":false,"error":"Permission denied"}""")))
        assertEquals(emptyList<String>(), ToolArtifacts.from("write_file", JSONObject("""{"path":"/srv/out/a.md"}"""),
            """{"error":"disk full"}"""))
        assertEquals(listOf("/srv/out/a.md"), ToolArtifacts.from("write_file", JSONObject("""{"path":"/srv/out/a.md"}"""),
            JSONObject("""{"success":true,"bytes":12}""")))
    }

    @Test
    fun filesLaneOffersOnlyPreviewablePages() {
        assertEquals(true, ToolArtifacts.previewable("/tmp/site/index.html"))
        assertEquals(false, ToolArtifacts.previewable("/home/h/.hermes/skills/x/SKILL.md"))
        assertEquals(false, ToolArtifacts.previewable("/tmp/report.pdf"))
    }
}
