package com.nousresearch.hermesmobile.data

import java.text.Normalizer

/**
 * Ranking for the search palette, after Desktop's command palette: a label that starts with the
 * query beats one where a word does, which beats a plain substring; extra terms (a chat's preview,
 * a page's synonyms) match at the lowest rank. Case and accents are ignored, and every word of
 * the query has to appear somewhere. 0 means no match.
 */
object PaletteSearch {
    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

    fun score(label: String, query: String, keywords: String = ""): Int {
        val q = fold(query)
        if (q.isEmpty()) return 1
        val l = fold(label)
        val rest = fold(keywords)
        val words = q.split(Regex("\\s+"))
        if (!words.all { it in l || it in rest }) return 0
        return when {
            l.startsWith(q) -> 4
            Regex("(^|[^\\p{L}\\p{N}])" + Regex.escape(q)).containsMatchIn(l) -> 3
            q in l -> 2
            else -> 1
        }
    }

    /** Matching chats, best first and then most recent; a chat's id or profile never matches by itself. */
    fun sessions(sessions: List<HermesSession>, query: String): List<HermesSession> =
        sessions.map { it to score(it.displayTitle, query, it.preview) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<HermesSession, Int>> { it.second }.thenByDescending { it.first.lastActive })
            .map { it.first }

    /**
     * Loaded matches first, then the server's full-text hits that weren't already listed. An assistant
     * message can be serialized tool calls (a JSON list of call ids); a hit only there is no one's words, so it goes.
     */
    fun mergeSessions(local: List<HermesSession>, remote: List<HermesSession>): List<HermesSession> {
        val seen = local.mapTo(HashSet()) { "${it.profile}:${it.id}" }
        return local + remote.filter { !isJson(it.preview) && seen.add("${it.profile}:${it.id}") }
    }

    private fun isJson(snippet: String): Boolean {
        val text = snippet.trim().removePrefix("...").removePrefix("…").trimStart()
        return text.startsWith("{\"") || text.startsWith("[{")
    }
}
