package com.nousresearch.hermesmobile.data

import org.json.JSONObject

/**
 * One `/` palette row, as Desktop's composer popover shows it. [section] groups rows under a header:
 * a gateway category name, or [SlashCommands.SECTION_COMMANDS] / [SlashCommands.SECTION_SKILLS].
 */
data class SlashCompletion(val text: String, val display: String, val meta: String, val skill: Boolean, val section: String = "")

/** What the composer's `/` popover shows: null [query] hides it. */
data class SlashPalette(val query: String? = null, val items: List<SlashCompletion> = emptyList(), val loading: Boolean = false)

/** One entry of the `commands.catalog` palette. */
data class SlashCatalogEntry(val name: String, val description: String, val category: String, val skill: Boolean, val usage: Int = 0)

/** `commands.catalog`: every built-in/quick/plugin command by category, then the profile's skills. */
data class SlashCatalog(val entries: List<SlashCatalogEntry>, val aliases: Map<String, String> = emptyMap())

/** Result envelope of `slash.exec` / `command.dispatch`. */
data class SlashResult(
    /** "send" (submit [message] as a turn), "prefill" (put [message] in the composer) or "exec" (show [output]). */
    val type: String,
    val message: String = "",
    val output: String = "",
    val notice: String = ""
)

/** Slash commands the app handles itself instead of sending to the gateway. */
enum class LocalSlash { NEW, MODEL, RETRY, COMPRESS, BRANCH }

object SlashCommands {
    const val SECTION_COMMANDS = "@commands"
    const val SECTION_SKILLS = "@skills"
    private const val FILTER_LIMIT = 60
    /** Built-ins that rewind the gateway transcript; the local copy must be reloaded afterwards. */
    private val rewinding = setOf("undo", "retry")

    /** The slash text to complete, or null when the draft is not a single-line command. */
    fun query(draft: String): String? =
        draft.takeIf { it.startsWith("/") && '\n' !in it && it.length <= 200 }

    fun name(command: String): String = command.trim().removePrefix("/").substringBefore(' ').lowercase()

    fun local(command: String): LocalSlash? {
        val arg = command.trim().substringAfter(' ', "").trim()
        return when (name(command)) {
            "new", "reset", "clear" -> LocalSlash.NEW
            "model" -> LocalSlash.MODEL.takeIf { arg.isEmpty() }
            "retry" -> LocalSlash.RETRY
            "compress", "compact" -> LocalSlash.COMPRESS.takeIf { arg.isEmpty() }
            "branch", "fork" -> LocalSlash.BRANCH.takeIf { arg.isEmpty() }
            else -> null
        }
    }

    fun rewinds(command: String): Boolean = name(command) in rewinding

    fun parseCompletions(result: JSONObject): Pair<List<SlashCompletion>, Int> {
        val items = result.optJSONArray("items") ?: return emptyList<SlashCompletion>() to 1
        val parsed = (0 until items.length()).mapNotNull { items.optJSONObject(it) }.mapNotNull { item ->
            val text = item.optString("text").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val skill = item.optString("kind") == "skill"
            SlashCompletion(
                text = text,
                display = item.optString("display").ifBlank { text },
                meta = item.optString("meta"),
                skill = skill,
                section = if (skill) SECTION_SKILLS else SECTION_COMMANDS
            )
        }.distinctBy { it.text }.take(FILTER_LIMIT)
        return parsed to result.optInt("replace_from", 1)
    }

    /**
     * Applies a completion as Desktop does: command-stage items carry the whole `/token`;
     * argument-stage items (replace_from > 1) carry only the argument stub.
     */
    fun apply(draft: String, completion: SlashCompletion, replaceFrom: Int): String {
        val value = if (replaceFrom > 1) draft.take(replaceFrom) + completion.text
        else completion.text.let { if (it.startsWith("/")) it else "/$it" }
        return if (value.endsWith(" ")) value else "$value "
    }

    fun parseResult(result: JSONObject): SlashResult {
        val type = result.optString("type").ifBlank { "exec" }
        return SlashResult(
            type = type,
            message = result.optString("message"),
            output = result.optString("output"),
            notice = result.optString("notice").ifBlank { result.optString("warning") }
        )
    }

    /** Whether [query] is still choosing the command itself (`/mo`), not its arguments (`/model gp`). */
    fun commandStage(query: String): Boolean = query.startsWith("/") && ' ' !in query

    fun parseCatalog(result: JSONObject): SlashCatalog {
        val entries = mutableListOf<SlashCatalogEntry>()
        val seen = HashSet<String>()
        val descriptions = HashMap<String, String>()
        result.optJSONArray("pairs")?.let { pairs ->
            for (i in 0 until pairs.length()) {
                val pair = pairs.optJSONArray(i) ?: continue
                descriptions.putIfAbsent(pair.optString(0), pair.optString(1))
            }
        }
        val categories = result.optJSONArray("categories")
        for (i in 0 until (categories?.length() ?: 0)) {
            val category = categories!!.optJSONObject(i) ?: continue
            val name = category.optString("name")
            val pairs = category.optJSONArray("pairs") ?: continue
            for (j in 0 until pairs.length()) {
                val pair = pairs.optJSONArray(j) ?: continue
                val key = normalize(pair.optString(0)) ?: continue
                if (seen.add(key)) entries += SlashCatalogEntry(key, pair.optString(1), name, skill = false)
            }
        }
        val skills = result.optJSONObject("skills")
        skills?.keys()?.asSequence()?.sorted()?.forEach { raw ->
            val key = normalize(raw) ?: return@forEach
            if (!seen.add(key)) return@forEach
            val usage = skills.optJSONObject(raw)?.optInt("usage", 0) ?: 0
            entries += SlashCatalogEntry(key, descriptions[raw].orEmpty().ifBlank { descriptions[key].orEmpty() }, "", skill = true, usage = usage)
        }
        val aliases = HashMap<String, String>()
        result.optJSONObject("canon")?.let { canon ->
            canon.keys().forEach { alias ->
                val target = normalize(canon.optString(alias)) ?: return@forEach
                val from = normalize(alias) ?: return@forEach
                if (from != target) aliases[from] = target
            }
        }
        return SlashCatalog(entries, aliases)
    }

    private fun normalize(key: String): String? {
        val trimmed = key.trim()
        if (trimmed.isEmpty() || trimmed == "/") return null
        return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }

    /**
     * Local, instant palette for a command-stage [query]. A bare `/` browses everything grouped by
     * category (skills last, most used first); anything longer ranks name prefix > alias > substring >
     * description > in-order letters (`/mdl` finds `/model`), commands before skills.
     */
    fun filter(catalog: SlashCatalog, query: String): List<SlashCompletion> {
        val token = query.removePrefix("/").trim().lowercase()
        if (token.isEmpty()) {
            val commands = catalog.entries.filterNot { it.skill }.map { it.toCompletion(it.category.ifBlank { SECTION_COMMANDS }) }
            val skills = catalog.entries.filter { it.skill }.sortedWith(compareByDescending<SlashCatalogEntry> { it.usage }.thenBy { it.name })
                .map { it.toCompletion(SECTION_SKILLS) }
            return commands + skills
        }
        val aliasHits = catalog.aliases.filterKeys { it.removePrefix("/").lowercase().startsWith(token) }.values.toSet()
        val ranked = catalog.entries.mapNotNull { entry ->
            val name = entry.name.removePrefix("/").lowercase()
            val score = when {
                name == token -> 0
                name.startsWith(token) -> 1
                entry.name in aliasHits -> 2
                token in name -> 3
                token.length >= 2 && entry.description.lowercase().split(' ', '-', '_', '/', ',', '.', '(', ')').any { it.startsWith(token) } -> 4
                token.length >= 2 && isSubsequence(token, name) -> 5
                else -> return@mapNotNull null
            }
            entry to score
        }.sortedWith(
            compareBy<Pair<SlashCatalogEntry, Int>> { it.first.skill }
                .thenBy { it.second }
                .thenByDescending { it.first.usage }
                .thenBy { it.first.name.length }
                .thenBy { it.first.name }
        )
        return ranked.take(FILTER_LIMIT).map { (entry, _) -> entry.toCompletion(if (entry.skill) SECTION_SKILLS else SECTION_COMMANDS) }
    }

    private fun SlashCatalogEntry.toCompletion(section: String) =
        SlashCompletion(text = name, display = name, meta = description, skill = skill, section = section)

    private fun isSubsequence(needle: String, haystack: String): Boolean {
        var i = 0
        for (c in haystack) if (i < needle.length && c == needle[i]) i++
        return i == needle.length
    }
}
