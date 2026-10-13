package com.nousresearch.hermesmobile.data

import org.json.JSONArray

/** Mirrors Desktop's bareChoice and stagedAnswer wire encoding. */
object ClarifyAnswer {
    fun encode(multi: Boolean, choices: List<String>, custom: String): String {
        val draft = custom.trim()
        val bare = choices.map { if (it.endsWith("(Recommended)")) it.removeSuffix("(Recommended)").trim() else it }
        val values = bare + listOfNotNull(draft.takeIf { it.isNotBlank() })
        return if (multi) { if (values.isEmpty()) "" else JSONArray(values).toString() }
            else draft.ifBlank { bare.firstOrNull().orEmpty() }
    }
}
