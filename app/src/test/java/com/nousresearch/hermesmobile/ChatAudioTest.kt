package com.nousresearch.hermesmobile
import com.nousresearch.hermesmobile.data.ChatAudio
import org.junit.Assert.*
import org.junit.Test
class ChatAudioTest {
    @Test fun realTtsContractSupportsChunksAndDeduplicatesTags() {
        val result=ChatAudio.fromTool("""{"success":true,"file_path":"/tmp/a.mp3","file_paths":["/tmp/a.mp3","/tmp/b.ogg"],"media_tag":"MEDIA:/tmp/a.mp3\nMEDIA:/tmp/b.ogg\n[[audio_as_voice]]"}""")
        assertEquals(listOf("/tmp/a.mp3","/tmp/b.ogg"), result)
    }
    @Test fun nestedExecutionOutputAndFailures() {
        assertEquals(listOf("/tmp/prueba_voz_es.mp3"), ChatAudio.fromTool("""{"results":[{"stdout":"/tmp/prueba_voz_es.mp3"}]}"""))
        assertTrue(ChatAudio.fromTool("""{"success":false,"file_path":"/tmp/missing.mp3"}""").isEmpty())
    }
    @Test fun mediaMarkerAndSandboxPaths() {
        assertEquals(listOf("/tmp/voice note.wav"), ChatAudio.fromTool("MEDIA:/tmp/voice note.wav\n[[audio_as_voice]]"))
        assertEquals("/tmp/a.m4a",ChatAudio.path("sandbox:/tmp/a.m4a"))
    }
    @Test fun neverUsesExternalUrlsOrApiPathsAsGatewayFiles() {
        listOf("https://host/a.mp3","//host/a.mp3","/api/private.mp3","file:///a.mp3","/tmp/a.pdf", "relative.mp3").forEach { assertNull(ChatAudio.path(it)) }
    }
    @Test fun ignoresIncidentalPathsInProseAndCode() {
        assertTrue(ChatAudio.fromTool("Example: /tmp/a.mp3").isEmpty())
        assertTrue(ChatAudio.fromTool("{\"example\":\"/tmp/a.mp3\"}").isEmpty())
    }
}
