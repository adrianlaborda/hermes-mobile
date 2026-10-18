package com.nousresearch.hermesmobile.data

/**
 * What a stored turn shows, as Hermes Desktop projects it (`lib/chat-messages/hydration.ts`).
 * The durable rows carry model-facing scaffolding the user never wrote: attached file bodies,
 * expanded skills, routing notes, background-process envelopes. None of it belongs in a bubble.
 */
object TurnDisplay {
    /** A user turn: [text] for the bubble, [refs] the `@file:`/`@folder:`/`@image:` lines lifted into chips. */
    data class UserTurn(val text: String, val refs: List<String>)

    private val ATTACHED_CONTEXT = Regex("""(?:^|\n)--- Attached Context ---\s*\n""")
    private val CONTEXT_WARNINGS = Regex("""(?:^|\n)--- Context Warnings ---[\s\S]*$""")
    private val CONTEXT_REF = Regex("""@(file|folder|url|image|tool|terminal):(?:"[^"\n]+"|'[^'\n]+'|`[^`\n]+`|\S+)""")
    private val DISCORD_TRIGGERING_NOTE = Regex(
        """(^|\n)\[Triggering message id: `[^`\n]*` — use as `message_id` for reply/react/pin via the discord tools\.]\n*"""
    )
    private val LEGACY_HEARTBEAT = Regex("""^\[Background process \S+ heartbeat #\d+ """)
    private val REFERENCE_LINE = Regex("""^@(file|folder|url|image|tool|line|terminal|session):(`[^`\n]+`|"[^"\n]+"|'[^'\n]+'|\S+)$""")
    private val IMAGE_REF_LINE = Regex("""^@image:[^\n]*\n?""", RegexOption.MULTILINE)
    private val SCREENSHOT_PLACEHOLDER_LINE = Regex("""^\[screenshot]\n?""", RegexOption.MULTILINE)

    /** The user's words, or null for a background heartbeat a backend stored as a user row. */
    fun user(raw: String): UserTurn? {
        if (LEGACY_HEARTBEAT.containsMatchIn(raw.trim())) return null
        val text = userText(raw)
        val images = IMAGE_REF_LINE.findAll(text).map { it.value.trim() }.toList()
        val cleaned = if (images.isEmpty()) text
            else text.replace(IMAGE_REF_LINE, "").replace(SCREENSHOT_PLACEHOLDER_LINE, "")
        // Only the leading block moves into chips: a ref typed mid-prose stays where the user put it.
        val lines = cleaned.split('\n').toMutableList()
        val files = mutableListOf<String>()
        while (lines.isNotEmpty() && Regex("^@(?:file|folder):").containsMatchIn(lines[0]) &&
            REFERENCE_LINE.matches(lines[0].trimEnd())) {
            files += lines.removeAt(0).trimEnd()
        }
        val body = if (files.isEmpty()) cleaned else lines.joinToString("\n").trim()
        return UserTurn(body.trim(), files + images)
    }

    private val PREVIEW_REF = Regex("""@(?:file|folder|image|url|tool|terminal|line|session):(?:"[^"\n]+"|'[^'\n]+'|`[^`\n]+`|\S+)""")

    /** A chat's list preview without the attachment refs the server's excerpt keeps; refs alone read as their names. */
    fun preview(text: String): String {
        val words = text.replace(PREVIEW_REF, " ").replace(Regex("""\s+"""), " ").trim()
        if (words.isNotEmpty() || !PREVIEW_REF.containsMatchIn(text)) return words.ifEmpty { text.trim() }
        return PREVIEW_REF.findAll(text).map { refName(it.value) }.filter { it.isNotEmpty() }.joinToString(", ")
    }

    /** The gateway path an attachment ref points at. A relative one is relative to the session's workspace (see [resolve]). */
    fun refPath(ref: String): String {
        val raw = ref.substringAfter(':').trim()
        val quoted = raw.length >= 2 && raw.first() == raw.last() && raw.first() in "`\"'"
        val value = if (quoted) raw.substring(1, raw.length - 1) else raw.trimEnd(',', '.', ';', '!', '?')
        return value
    }

    /** [path] as the gateway can read it: the server stages attachments relative to the session's working directory. */
    fun resolve(path: String, cwd: String): String =
        if (path.startsWith('/') || path.startsWith("~") || cwd.isBlank()) path else cwd.trimEnd('/') + "/" + path.removePrefix("./")

    private val PASTED_CONTENT = Regex("""(?:^|/)pasted_content_[\w-]+\.txt$""")

    /** A large paste saved as a file (Desktop's `isPastedContentPath`): its chip reads "Pasted text", not the name. */
    fun isPastedContent(name: String): Boolean = PASTED_CONTENT.containsMatchIn(name)

    /** The chip label for a ref: its file or folder name. */
    fun refName(ref: String): String = refPath(ref).trimEnd('/').substringAfterLast('/')

    private fun userText(raw: String): String {
        val text = raw.replace(DISCORD_TRIGGERING_NOTE, "$1")
        skillInvocation(text)?.let { return it }
        val marker = ATTACHED_CONTEXT.find(text) ?: return text.replace(CONTEXT_WARNINGS, "").trim()
        val visible = text.substring(0, marker.range.first).replace(CONTEXT_WARNINGS, "").trim()
        val attached = text.substring(marker.range.last + 1)
        // The prose keeps the tokens the user typed; only hoist a ref an older backend stripped from it.
        val missing = CONTEXT_REF.findAll(attached).map { it.value }.distinct().filter { it !in visible }.toList()
        return listOf(missing.joinToString("\n"), visible).filter { it.isNotEmpty() }.joinToString("\n\n").ifEmpty { visible }
    }

    private const val INITIATE_SETUP_HEADER = "[/initiate-setup]"
    private const val INVOCATION_PREFIX = "[IMPORTANT: The user has invoked the "
    private const val SINGLE_MARKER = "The full skill content is loaded below.]"
    private const val SINGLE_INSTRUCTION = "The user has provided the following instruction alongside the skill invocation: "
    private const val RUNTIME_NOTE = "\n\n[Runtime note:"
    private const val BUNDLE_MARKER = " skill bundle,"
    private const val BUNDLE_INSTRUCTION = "\nUser instruction: "
    private const val BUNDLE_SKILL_BLOCK = "\n\n[Loaded as part of the "
    private val SKILL_NAME = Regex("^\\[IMPORTANT: The user has invoked the \"([^\"]*)\"")

    /** The `/skill instruction` an expanded skill turn came from (Desktop's `skillInvocationText`), or null. */
    fun skillInvocation(text: String): String? {
        if (text.startsWith(INITIATE_SETUP_HEADER)) return "/initiate-setup"
        if (!text.startsWith(INVOCATION_PREFIX)) return null
        val name = SKILL_NAME.find(text)?.groupValues?.get(1)?.trim().orEmpty()
        if (name.isEmpty()) return null
        val label = if (name.startsWith("/")) name else "/$name"
        val instruction = when {
            BUNDLE_MARKER in text -> between(text, BUNDLE_INSTRUCTION, BUNDLE_SKILL_BLOCK, fromEnd = false)
            SINGLE_MARKER in text -> between(text, SINGLE_INSTRUCTION, RUNTIME_NOTE, fromEnd = true)
            else -> ""
        }
        return if (instruction.isNotEmpty()) "$label ${instruction.replace(Regex("""\s+"""), " ")}" else label
    }

    private fun between(text: String, marker: String, end: String, fromEnd: Boolean): String {
        val index = if (fromEnd) text.lastIndexOf(marker) else text.indexOf(marker)
        if (index < 0) return ""
        val tail = text.substring(index + marker.length)
        val stop = tail.indexOf(end)
        return (if (stop >= 0) tail.substring(0, stop) else tail).trim()
    }

    private val TASK_HEADER = Regex("""^--- [✓✗⚠] TASK \d+/\d+(?:: [\s\S]*?)? {2}\(status=[^\n]*\) ---\r?\n""", RegexOption.MULTILINE)
    private val RESULT_HEADER = Regex("""^--- (?:RESULT|ERROR) ---\r?\n""", RegexOption.MULTILINE)
    private val JOB_OUTPUT = Regex("""^--- JOB OUTPUT ---\r?\n""", RegexOption.MULTILINE)
    private val TRANSCRIPT_FOOTER = Regex("""\nFull live transcript \(complete tool/assistant trace\): [^\n]*\n*$""")

    /** The report inside a background-process or delegation envelope (Desktop's `asyncResultBody`). */
    fun asyncResultBody(content: String): String {
        val bodies = when {
            content.startsWith("[IMPORTANT: ") -> content.split(Regex("""\n\n(?=\[IMPORTANT: )"""))
                .map { it.replace(Regex("""^\[IMPORTANT:\s*"""), "").replace(Regex("""]$"""), "") }
                .filterNot { Regex("""^\d+ background processes completed\.""").containsMatchIn(it) }
            content.startsWith("[ASYNC DELEGATION BATCH COMPLETE") -> content.split(TASK_HEADER).drop(1)
            content.startsWith("[ASYNC DELEGATION") ->
                RESULT_HEADER.find(content)?.let { listOf(content.substring(it.range.last + 1)) }.orEmpty()
            else -> listOf(content)
        }
        return bodies.map { body ->
            val output = if (body.startsWith("Cron job ")) JOB_OUTPUT.find(body) else null
            val result = output?.let { body.substring(it.range.last + 1) } ?: body
            result.replace(TRANSCRIPT_FOOTER, "").trim()
        }.filter { it.isNotEmpty() }.joinToString("\n\n")
    }
}
