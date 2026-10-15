package com.nousresearch.hermesmobile.update

import org.json.JSONArray

/** A SemVer 2.0 version (`0.3.0`, `0.3.0-beta.2`), ordered as SemVer orders them; build metadata is ignored. */
data class AppVersion(val major: Int, val minor: Int, val patch: Int, val pre: List<String>) : Comparable<AppVersion> {
    val prerelease: Boolean get() = pre.isNotEmpty()

    override fun compareTo(other: AppVersion): Int {
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }).let { if (it != 0) return it }
        // A pre-release sorts before its release.
        if (pre.isEmpty() || other.pre.isEmpty()) return other.pre.size.coerceAtMost(1) - pre.size.coerceAtMost(1)
        for (i in 0 until minOf(pre.size, other.pre.size)) {
            val a = pre[i]; val b = other.pre[i]
            val an = a.toIntOrNull(); val bn = b.toIntOrNull()
            val c = when {
                an != null && bn != null -> an.compareTo(bn)
                an != null -> -1 // numeric identifiers sort before alphanumeric ones
                bn != null -> 1
                else -> a.compareTo(b)
            }
            if (c != 0) return c
        }
        return pre.size.compareTo(other.pre.size)
    }

    override fun toString() = "$major.$minor.$patch" + if (pre.isEmpty()) "" else "-" + pre.joinToString(".")

    companion object {
        private val pattern = Regex("""v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?""")

        fun parse(text: String): AppVersion? {
            val m = pattern.matchEntire(text.trim()) ?: return null
            val pre = m.groupValues[4].takeIf { it.isNotEmpty() }?.split('.').orEmpty()
            if (pre.any { it.isEmpty() }) return null
            return AppVersion(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), pre)
        }
    }
}

/** A published release that the app can install: its APK and the checksums file next to it. */
data class AppRelease(
    val version: AppVersion,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
    val apkSize: Long,
    val checksumsUrl: String?
)

/** Reads GitHub's release list (`GET /repos/{repo}/releases`) as `release.yml` publishes it. */
object ReleaseFeed {
    /** Notes end where `release.yml` appends the install and verification footer. */
    private const val FOOTER = "\n---\n"

    fun parse(json: String): List<AppRelease> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            if (o.optBoolean("draft")) return@mapNotNull null
            val version = AppVersion.parse(o.optString("tag_name")) ?: return@mapNotNull null
            val assets = o.optJSONArray("assets") ?: JSONArray()
            val byName = (0 until assets.length()).mapNotNull(assets::optJSONObject).associateBy { it.optString("name") }
            val apk = byName["hermes-mobile-$version.apk"] ?: return@mapNotNull null
            val body = o.optString("body").replace("\r\n", "\n")
            AppRelease(
                version = version,
                notes = body.substringBefore(FOOTER).trim(),
                pageUrl = o.optString("html_url"),
                apkUrl = apk.optString("browser_download_url"),
                apkSize = apk.optLong("size"),
                checksumsUrl = byName["SHA256SUMS"]?.optString("browser_download_url")
            )
        }
    }

    /** The newest release above [current]; pre-releases only when [beta]. */
    fun newest(releases: List<AppRelease>, current: AppVersion, beta: Boolean): AppRelease? =
        releases.filter { beta || !it.version.prerelease }.maxByOrNull { it.version }?.takeIf { it.version > current }

    /** The newest stable version among plain tags (the plugin's release list). */
    fun newestStable(json: String): AppVersion? {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull(array::optJSONObject)
            .filter { !it.optBoolean("draft") }
            .mapNotNull { AppVersion.parse(it.optString("tag_name")) }
            .filter { !it.prerelease }
            .maxOrNull()
    }

    /** The SHA-256 that `SHA256SUMS` lists for [fileName] (`sha256sum` output), lowercase. */
    fun checksum(sums: String, fileName: String): String? = sums.lineSequence()
        .map { it.trim().split(Regex("\\s+"), limit = 2) }
        .firstOrNull { it.size == 2 && it[1].removePrefix("*") == fileName }
        ?.get(0)?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
}
