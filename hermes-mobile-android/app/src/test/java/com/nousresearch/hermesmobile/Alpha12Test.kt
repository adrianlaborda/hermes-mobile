package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.push.PushClient
import com.nousresearch.hermesmobile.push.PushAccount
import com.nousresearch.hermesmobile.ui.components.ChatMarkdown
import com.nousresearch.hermesmobile.ui.components.artifactPath
import org.junit.Assert.*
import org.junit.Test
import org.commonmark.node.FencedCodeBlock

class Alpha12Test {
    @Test fun pluginUrlKeepsProfileAndProxyPrefix() {
        assertEquals("https://example.com/hermes/p/work/api/platforms/hermes_android/events", PushClient.endpoint("https://example.com/hermes/", "work"))
    }
    @Test fun pluginUrlRejectsUnsafeSecretsTransport() {
        listOf("http://example.com", "https://user:secret@example.com", "https://example.com?token=x", "https://example.com#fragment").forEach {
            assertTrue(runCatching { PushClient.endpoint(it, "default") }.isFailure)
        }
        assertTrue(runCatching { PushClient.endpoint("https://example.com", "../other") }.isFailure)
    }
    @Test fun separateConnectionsAndProfilesDoNotShareRegistrationKeys() {
        assertNotEquals(PushAccount("one", "default", "", "").key, PushAccount("two", "default", "", "").key)
        assertNotEquals(PushAccount("one", "default", "", "").key, PushAccount("one", "work", "", "").key)
    }
    @Test fun artifactPathsNeverTreatUrlsAsAuthenticatedFilePaths() {
        assertEquals("/mnt/data/plot.png", artifactPath("sandbox:/mnt/data/plot.png"))
        listOf("https://evil.test/a.png", "//evil.test/a", "data:image/png;base64,xx", "/api/files?path=a", "javascript:alert(1)").forEach { assertNull(artifactPath(it)) }
    }
    @Test fun displayMathPreservesCodeAndProducesMathBlock() {
        val literal = "```python\n$$\n```"
        assertEquals(literal, ChatMarkdown.normalizeMath(literal))
        val parsed = ChatMarkdown.parse("$$\nx^2\n$$")
        val block = parsed.firstChild as FencedCodeBlock
        assertEquals("math", block.info)
        assertEquals("x^2\n", block.literal)
    }
}
