package com.nousresearch.hermesmobile.data

import org.json.JSONObject

object ChatAudio {
    fun path(value: String): String? {
        val p = value.removePrefix("sandbox:")
        return p.takeIf { it.startsWith('/') && !it.startsWith("//") && !it.startsWith("/api/") &&
            '\u0000' !in it && it.length <= 4096 &&
            it.substringAfterLast('.', "").lowercase() in setOf("mp3", "m4a", "wav", "ogg", "opus", "aac", "flac") }
    }
    fun fromTool(text: String): List<String> {
        val found = linkedSetOf<String>()
        var visited = 0
        fun collect(value: Any?, depth: Int) {
            if(depth > 8 || ++visited > 256 || found.size >= 12) return
            when(value) {
                is JSONObject -> {
                    if(value.opt("success") == false) return
                    path(value.optString("file_path"))?.let(found::add)
                    value.optJSONArray("file_paths")?.let { collect(it, depth + 1) }
                    listOf("media_tag", "stdout", "output", "result", "results", "content", "text")
                        .forEach { key -> value.opt(key)?.let { collect(it, depth + 1) } }
                }
                is org.json.JSONArray -> for(i in 0 until minOf(value.length(), 32)) collect(value.opt(i), depth + 1)
                is String -> {
                    val trimmed = value.trim()
                    path(trimmed)?.let(found::add)
                    if(trimmed.startsWith("{")) runCatching { JSONObject(trimmed) }.getOrNull()?.let { collect(it, depth + 1) }
                    else if(trimmed.startsWith("[")) runCatching { org.json.JSONArray(trimmed) }.getOrNull()?.let { collect(it, depth + 1) }
                    Regex("(?im)^MEDIA:(.+)$").findAll(value).forEach { path(it.groupValues[1].trim())?.let(found::add) }
                }
            }
        }
        collect(text, 0)
        return found.take(12)
    }
}
