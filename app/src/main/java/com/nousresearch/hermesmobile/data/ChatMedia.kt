package com.nousresearch.hermesmobile.data

/**
 * `MEDIA:<path>` in an agent's reply is Hermes' "here is a file" delivery. Hermes Desktop rewrites each tag into a
 * link before rendering (`renderMediaTags` in `chat-messages/parts.ts`), so the reader sees the image, a player or a
 * file to open — never the raw tag. Same grammar here: a quoted path, an absolute path (spaces allowed) ending in a
 * known deliverable extension, or a bare word; sentence punctuation after a bare path stays prose.
 */
object ChatMedia {
    // Desktop's MEDIA_DELIVERY_EXTS (mirrors the gateway's own list).
    private val EXTS = listOf(
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "tiff", "svg", "mp4", "mov", "avi", "mkv", "webm", "3gp",
        "mp3", "m2a", "wav", "ogg", "opus", "m4a", "flac", "pdf", "docx", "doc", "odt", "rtf", "txt", "md", "epub",
        "xlsx", "xls", "ods", "csv", "tsv", "json", "xml", "yaml", "yml", "kmz", "kml", "geojson", "gpx", "pptx",
        "ppt", "odp", "key", "zip", "tar", "gz", "tgz", "bz2", "xz", "7z", "rar", "apk", "ipa", "html", "htm"
    ).sortedByDescending { it.length }
    private val IMAGES = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
    private val ANCHORED = "(?:~/|/|[A-Za-z]:[/\\\\])\\S+?(?:[^\\S\\n]+\\S+?)*?\\.(?:${EXTS.joinToString("|")})" +
        "(?=[\\s`\"'*_,;:)\\]}]|MEDIA:|$)"
    private val TAG = Regex("[`\"']?MEDIA:\\s*(`[^`\\n]+`|\"[^\"\\n]+\"|'[^'\\n]+'|$ANCHORED|[^\\s`\"]+)[`\"']?")

    fun render(text: String): String {
        if ("MEDIA:" !in text) return text
        return TAG.replace(text) { match -> link(match.groupValues[1]) ?: match.value }
    }

    private fun link(value: String): String? {
        val raw = value.trim()
        val quoted = raw.length >= 2 && raw.first() in "\"'`" && raw.first() == raw.last()
        var path = if (quoted) raw.substring(1, raw.length - 1) else raw.removeSuffix("`").removeSuffix("\"")
        var punctuation = ""
        if (!quoted) {
            while (path.isNotEmpty() && path.last() in ".,;:!?" && plausible(path.dropLast(1))) {
                punctuation = path.last() + punctuation
                path = path.dropLast(1)
            }
        }
        if (!plausible(path) || path.any { it == '<' || it == '>' || it == '\n' }) return null
        val name = path.split('/', '\\').lastOrNull { it.isNotEmpty() } ?: path
        val label = name.replace(Regex("[\\[\\]\\\\]")) { "\\" + it.value }
        val image = name.substringAfterLast('.', "").lowercase() in IMAGES
        return "${if (image) "!" else ""}[$label](<$path>)$punctuation"
    }

    /** A path separator, or a dot with something after it: `...` or a plain word stays prose. */
    private fun plausible(value: String): Boolean = '/' in value || '\\' in value || Regex("\\.[^.]").containsMatchIn(value)
}
