package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.update.AppVersion
import com.nousresearch.hermesmobile.update.ReleaseFeed
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseFeedTest {
    private fun v(text: String) = AppVersion.parse(text)!!

    @Test fun semverOrder() {
        // The SemVer 2.0 spec's own example, in ascending order.
        val ordered = listOf("1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta", "1.0.0-beta.2",
            "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0", "1.0.1", "1.1.0", "2.0.0").map(::v)
        assertEquals(ordered, ordered.shuffled().sorted())
        assertTrue(v("0.3.0-beta.10") > v("0.3.0-beta.2"))
        assertTrue(v("0.3.0") > v("0.3.0-beta.9"))
        assertEquals(v("v0.5.3+build.7"), v("0.5.3"))
        assertNull(AppVersion.parse("0.3.0-beta..1"))
        assertNull(AppVersion.parse("latest"))
    }

    private fun release(tag: String, prerelease: Boolean = tag.contains('-'), draft: Boolean = false, apk: Boolean = true) = JSONObject()
        .put("tag_name", tag).put("prerelease", prerelease).put("draft", draft).put("html_url", "https://github.com/x/releases/tag/$tag")
        .put("body", "### Added\r\n\r\n- Things.\r\n\r\n---\r\n\r\n**Install:** download")
        .put("assets", JSONArray().apply {
            val version = tag.removePrefix("v")
            if (apk) put(JSONObject().put("name", "hermes-mobile-$version.apk").put("size", 1234).put("browser_download_url", "https://dl/$version.apk"))
            put(JSONObject().put("name", "SHA256SUMS").put("browser_download_url", "https://dl/$version.sums"))
        })

    @Test fun picksTheNewestForTheChannel() {
        val feed = JSONArray().put(release("v0.3.0-beta.3")).put(release("v0.2.9")).put(release("v0.3.0-beta.4", draft = true))
            .put(release("v0.3.1", apk = false)).put(release("nightly")).toString()
        val releases = ReleaseFeed.parse(feed)
        assertEquals(listOf("0.3.0-beta.3", "0.2.9"), releases.map { it.version.toString() })
        val beta = ReleaseFeed.newest(releases, v("0.3.0-beta.2"), beta = true)!!
        assertEquals("0.3.0-beta.3", beta.version.toString())
        assertEquals("https://dl/0.3.0-beta.3.apk", beta.apkUrl)
        assertEquals("### Added\n\n- Things.", beta.notes)
        assertNull(ReleaseFeed.newest(releases, v("0.3.0-beta.2"), beta = false))
        assertEquals("0.2.9", ReleaseFeed.newest(releases, v("0.2.8"), beta = false)!!.version.toString())
        assertNull(ReleaseFeed.newest(releases, v("0.3.0-beta.3"), beta = true))
    }

    @Test fun pluginLatestIgnoresPrereleases() {
        val feed = JSONArray().put(release("v0.5.3")).put(release("v0.6.0-rc.1")).put(release("v0.5.10")).toString()
        assertEquals(v("0.5.10"), ReleaseFeed.newestStable(feed))
    }

    @Test fun readsSha256sums() {
        val sums = "${"a".repeat(64)}  hermes-mobile-0.3.0.apk\n${"B".repeat(64)} *other.apk\n"
        assertEquals("a".repeat(64), ReleaseFeed.checksum(sums, "hermes-mobile-0.3.0.apk"))
        assertEquals("b".repeat(64), ReleaseFeed.checksum(sums, "other.apk"))
        assertNull(ReleaseFeed.checksum(sums, "missing.apk"))
        assertNull(ReleaseFeed.checksum("xyz  hermes-mobile-0.3.0.apk", "hermes-mobile-0.3.0.apk"))
    }
}
