package com.nousresearch.hermesmobile.data

import org.json.JSONArray

data class ClarifyOption(val label: String, val recommended: Boolean)

/** The server appends " (Recommended)"; the agent sometimes writes its own localized marker too. */
private val RECOMMENDED_SUFFIX = Regex("""\s*\((recommended|recomendado|recomendada)\)\s*$""", RegexOption.IGNORE_CASE)

/** Mirrors Desktop's bareChoice and stagedAnswer wire encoding. */
object ClarifyAnswer {
    /** Strips every trailing recommended marker, so "X (Recomendado) (Recommended)" becomes "X". */
    fun parseOption(raw: String): ClarifyOption {
        var label = raw.trim()
        var recommended = false
        while (true) {
            val match = RECOMMENDED_SUFFIX.find(label) ?: break
            label = label.substring(0, match.range.first).trimEnd()
            recommended = true
        }
        return ClarifyOption(label, recommended)
    }

    fun encode(multi: Boolean, choices: List<String>, custom: String): String {
        val draft = custom.trim()
        val bare = choices.map { parseOption(it).label }
        val values = bare + listOfNotNull(draft.takeIf { it.isNotBlank() })
        return if (multi) { if (values.isEmpty()) "" else JSONArray(values).toString() }
            else draft.ifBlank { bare.firstOrNull().orEmpty() }
    }
}
