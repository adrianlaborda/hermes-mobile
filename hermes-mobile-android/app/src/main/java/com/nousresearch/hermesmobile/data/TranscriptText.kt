package com.nousresearch.hermesmobile.data

import org.json.JSONArray
import org.json.JSONObject

/** Project protocol content into human text. Never serialize unknown protocol objects.
 * String content is deliberately preserved, including legitimate JSON answers/code. */
object TranscriptText {
    fun visible(value: Any?): String = project(value, false, 0)
    fun reasoning(value: Any?): String = project(decodeStructured(value), true, 0)
    fun reasoningDelta(value: Any?): String = project(value, true, 0)
    fun sidecar(value: Any?): String = project(decodeStructured(value), false, 0)
    private fun decodeStructured(value: Any?): Any? {
        if (value !is String) return value
        val trimmed = value.trim()
        return if (trimmed.startsWith("[")) runCatching { JSONArray(trimmed) }.getOrNull()
        else if (trimmed.startsWith("{")) runCatching { JSONObject(trimmed) }.getOrNull()
        else value
    }

    private fun project(value: Any?, reasoning: Boolean, depth: Int): String {
        if (depth > 32) return ""
        return when (value) {
            is String -> value
            is JSONArray -> (0 until value.length()).map {
                project(value.opt(it), reasoning, depth + 1)
            }.filter { it.isNotBlank() }.joinToString("\n")
            is JSONObject -> {
                val type = value.optString("type")
                val phase = value.optString("phase")
                val thinking = type in setOf("thinking", "reasoning", "reasoning_text", "summary_text", "reasoning.summary") || phase == "analysis"
                when {
                    (thinking || phase == "commentary") && !reasoning -> ""
                    type in setOf("redacted_thinking", "tool_use", "tool_result", "function_call", "function_call_output", "image", "image_url", "input_image") -> ""
                    type.isNotBlank() && type !in setOf("text", "input_text", "output_text", "message", "thinking", "reasoning", "reasoning_text", "summary_text", "reasoning.summary") -> ""
                    else -> {
                        val keys = if (reasoning) listOf("text", "thinking", "summary", "content", "output_text", "items", "value")
                                   else listOf("text", "content", "output_text", "items", "value")
                        keys.firstNotNullOfOrNull { key ->
                            project(value.opt(key), reasoning, depth + 1).takeIf { it.isNotBlank() }
                        }.orEmpty()
                    }
                }
            }
            else -> ""
        }
    }
}
