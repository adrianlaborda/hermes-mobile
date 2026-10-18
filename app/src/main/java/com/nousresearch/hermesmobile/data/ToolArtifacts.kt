package com.nousresearch.hermesmobile.data

import org.json.JSONObject

/**
 * The files a finished tool delivered, for the chat's Files lane. Hermes Desktop's rule (`toolPreviewTarget`):
 * reading, listing or searching is not producing a deliverable, and the target comes from the tool's own fields
 * (its result's `preview`/`target`/`path`, or the `path`/`file` it was given), never from any text that happens
 * to name a file — a fetched page or a file the agent only read must not show up as if it were attached.
 * `MEDIA:` tags, Hermes' explicit "here is a file" convention, also count.
 */
object ToolArtifacts {
    private val NON_DELIVERABLE = setOf(
        "annotate_preview", "drive_preview", "gui_tour", "list_files", "read_file", "search_files",
        "web_search", "web_extract", "browser_navigate", "browser_snapshot", "session_search", "memory",
        // Loading a skill reads its SKILL.md into the turn (Desktop: "neither proves the task has been completed").
        "skill_view", "skills_list"
    )
    private val MEDIA = Regex("MEDIA:([^\\s\"']+)", RegexOption.IGNORE_CASE)
    private val FILE = Regex(
        "\\.(?:png|jpe?g|webp|gif|pdf|csv|xlsx?|docx?|pptx?|txt|md|json|zip|mp3|m4a|wav|ogg|opus|aac|flac|html?|svg)$",
        RegexOption.IGNORE_CASE
    )

    /**
     * Whether a target belongs in the Files lane above the composer. Desktop offers only what it can preview
     * there (`isPreviewableTarget`: an HTML page); files sent with `MEDIA:` render in the reply instead, and
     * the user's own uploads are chips on their message.
     */
    fun previewable(path: String): Boolean = Regex("\\.html?$", RegexOption.IGNORE_CASE).containsMatchIn(path)

    /** False for tools that only look at files or pages: what they return was found, not made. */
    fun delivers(toolName: String): Boolean = toolName !in NON_DELIVERABLE

    fun from(name: String, args: JSONObject?, result: Any?): List<String> {
        if (name == "tool_call") {
            // A single wrapped call is judged as the tool it wraps.
            val calls = args?.optJSONArray("calls")
            val call = if (calls != null && calls.length() == 1) calls.optJSONObject(0) else null
            if (call != null && call.optString("name").isNotBlank()) {
                return from(call.optString("name"), call.optJSONObject("arguments"), result)
            }
        }
        // A tool that failed delivered nothing, whatever path it was given (Desktop: status must be success).
        if (failed(result)) return emptyList()
        val found = linkedSetOf<String>()
        MEDIA.findAll(result?.toString().orEmpty()).forEach { found += it.groupValues[1].trimEnd(',', '.', ')', ']') }
        if (delivers(name)) {
            val record = when (result) {
                is JSONObject -> result
                is String -> runCatching { JSONObject(result) }.getOrNull()
                else -> null
            }
            val direct = field(record, "preview", "target") ?: field(args, "path", "file", "filepath", "file_path")
                ?: field(record, "path", "file", "filepath")
            if (direct != null && isFile(direct)) found += direct
        }
        return found.take(8)
    }

    /** Desktop's `toolErrorText` signals, minus the ones that only colour a row. */
    internal fun failed(result: Any?): Boolean {
        val record = when (result) {
            is JSONObject -> result
            is String -> result.trim().takeIf { it.startsWith("{") }?.let { runCatching { JSONObject(it) }.getOrNull() }
            else -> null
        } ?: return false
        if (record.opt("success") == true || record.opt("ok") == true) return false
        if (record.opt("success") == false || record.opt("ok") == false) return true
        if (record.optString("error").isNotBlank() && record.opt("error") != JSONObject.NULL) return true
        if (record.optString("status").trim().lowercase() in setOf("error", "failed", "failure", "fatal", "exception")) return true
        val exit = record.opt("exit_code") as? Number
        return exit != null && exit.toInt() != 0 &&
            listOf("output", "stdout", "stderr", "output_preview").none { record.optString(it).isNotBlank() }
    }

    private fun field(o: JSONObject?, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { k -> o?.opt(k)?.takeIf { it is String }?.toString()?.trim()?.takeIf { it.isNotEmpty() } }

    private fun isFile(value: String): Boolean =
        "://" !in value && '\n' !in value && FILE.containsMatchIn(value)
}
