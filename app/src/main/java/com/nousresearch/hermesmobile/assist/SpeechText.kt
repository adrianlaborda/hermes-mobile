package com.nousresearch.hermesmobile.assist

/**
 * Turns an answer's Markdown into speakable text, and a streaming answer into whole sentences as they arrive,
 * so the device voice can start reading while Hermes is still writing.
 */
object SpeechText {
    private val fencedCode = Regex("```[\\s\\S]*?```")
    private val image = Regex("!\\[[^\\]]*]\\([^)]*\\)")
    private val link = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val url = Regex("https?://\\S*[^\\s.,;:!?)]")
    private val html = Regex("<[^>]+>")
    private val tableRule = Regex("(?m)^\\s*\\|?\\s*:?-{2,}.*$")
    private val heading = Regex("(?m)^\\s{0,3}#{1,6}\\s*")
    private val quote = Regex("(?m)^\\s*>\\s?")
    private val bullet = Regex("(?m)^\\s*(?:[-*+•]|\\d+[.)])\\s+")
    private val emphasis = Regex("(\\*\\*|__|\\*|_|~~|`)")
    private val sentenceEnd = Regex("(?<=[.!?…])\\s+|\\n+")

    /** Speakable text: code blocks, images, URLs and markup removed; link text kept. */
    fun clean(markdown: String): String = markdown
        .replace(fencedCode, " ")
        .replace(image, " ")
        .replace(link, "$1")
        .replace(url, " ")
        .replace(html, " ")
        .replace(tableRule, " ")
        .replace(heading, "")
        .replace(quote, "")
        .replace(bullet, "")
        .replace(emphasis, "")
        .replace("|", ", ")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex(" +([.,;:!?])"), "$1")
        .replace(Regex(" *\\n *"), "\n")
        .trim()

    /** Split cleaned-or-raw text into sentences worth speaking. */
    fun sentences(text: String): List<String> =
        clean(text).split(sentenceEnd).map { it.trim() }.filter { s -> s.any { it.isLetterOrDigit() } }

    /**
     * Feeds a growing answer and hands back the sentences completed since the last call. A boundary inside an
     * open code fence waits until the fence closes, so code is never read half-cleaned.
     */
    class Streamer {
        private var consumed = 0
        private var prefix = ""

        fun push(full: String): List<String> {
            if (!full.startsWith(prefix)) return emptyList() // the stream was replaced; finish() decides
            val end = lastBoundary(full, consumed) ?: return emptyList()
            return take(full, end)
        }

        /** The rest once the answer is final. Text that diverged from what was read is not repeated. */
        fun finish(full: String): List<String> =
            if (full.startsWith(prefix)) take(full, full.length) else if (consumed == 0) sentences(full) else emptyList()

        private fun take(full: String, end: Int): List<String> {
            val chunk = full.substring(consumed, end)
            consumed = end
            prefix = full.substring(0, end)
            return sentences(chunk)
        }

        private fun lastBoundary(text: String, from: Int): Int? {
            var best: Int? = null
            var i = from
            while (i < text.length) {
                val c = text[i]
                val boundary = c == '\n' || (c in ".!?…" && i + 1 < text.length && text[i + 1].isWhitespace())
                if (boundary && fences(text, i) % 2 == 0) best = i + 1
                i++
            }
            return best
        }

        private fun fences(text: String, until: Int): Int {
            var count = 0
            var idx = text.indexOf("```")
            while (idx in 0 until until) { count++; idx = text.indexOf("```", idx + 3) }
            return count
        }
    }
}
