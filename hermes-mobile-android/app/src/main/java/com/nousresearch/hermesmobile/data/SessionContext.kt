package com.nousresearch.hermesmobile.data

import org.json.JSONObject

/**
 * Live usage snapshot of one session, as the gateway reports it in `session.usage`
 * (RPC result and the 1 s live event), `message.complete` and `session.info`.
 * Optional readouts stay null when the gateway omits them rather than showing a fake 0.
 */
data class SessionUsage(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val reasoningTokens: Long = 0,
    val totalTokens: Long = 0,
    val contextUsed: Long = 0,
    val contextLimit: Long = 0,
    val calls: Int = 0,
    val model: String = "",
    val contextEstimated: Boolean = false,
    val compressions: Int = 0,
    val cacheHitPercent: Int? = null,
    val avgTokensPerSecond: Double? = null,
    val avgLatencySeconds: Double? = null,
    val activeSubagents: Int = 0
) {
    val contextPercent: Int?
        get() = if (contextLimit > 0L) ((contextUsed * 100L) / contextLimit).toInt().coerceIn(0, 100) else null

    /** Whether the snapshot measured anything worth showing. */
    val hasTraffic: Boolean get() = calls > 0 || totalTokens > 0 || inputTokens > 0 || outputTokens > 0

    companion object {
        fun from(o: JSONObject): SessionUsage {
            fun long(vararg names: String): Long {
                for (name in names) if (o.has(name) && !o.isNull(name)) return o.optLong(name, 0L)
                return 0L
            }
            fun double(name: String): Double? =
                if (o.has(name) && !o.isNull(name)) o.optDouble(name).takeIf { !it.isNaN() && it > 0.0 } else null
            return SessionUsage(
                inputTokens = long("input", "input_tokens", "prompt_tokens"),
                outputTokens = long("output", "output_tokens", "completion_tokens"),
                reasoningTokens = long("reasoning", "reasoning_tokens"),
                totalTokens = long("total", "total_tokens"),
                contextUsed = long("context_used", "context_tokens", "context_current"),
                contextLimit = long("context_max", "context_limit", "context_window"),
                calls = o.optInt("calls", 0),
                model = o.optString("model"),
                contextEstimated = o.optBoolean("context_estimated", false),
                compressions = o.optInt("compressions", 0),
                cacheHitPercent = if (o.has("cache_hit_pct") && !o.isNull("cache_hit_pct")) o.optInt("cache_hit_pct").coerceIn(0, 100) else null,
                avgTokensPerSecond = double("avg_tps"),
                avgLatencySeconds = double("avg_latency_s"),
                activeSubagents = o.optInt("active_subagents", 0)
            )
        }

        /**
         * The usage object an event carries: `session.usage` / `message.complete` / `session.info`
         * nest it under `usage`; an empty object (no agent yet) yields null so it never wipes a known snapshot.
         */
        fun fromEvent(payload: JSONObject): SessionUsage? {
            val usage = payload.optJSONObject("usage") ?: return null
            if (usage.length() == 0) return null
            return from(usage)
        }
    }
}

/** One row of `session.context_breakdown` (system prompt, tools, skills, MCP, memory, conversation…). */
data class ContextCategory(val id: String, val label: String, val tokens: Long)

/** A context file the session loaded (AGENTS.md, CLAUDE.md…) and whether it was used. */
data class ContextFile(val label: String, val path: String, val status: String)

/** `session.context_breakdown`: what currently fills the model's context window. */
data class ContextBreakdown(
    val used: Long,
    val max: Long,
    val percent: Int?,
    val estimated: Boolean,
    val model: String,
    val categories: List<ContextCategory>,
    val files: List<ContextFile>
) {
    val free: Long get() = (max - used).coerceAtLeast(0L)
    val categoryTotal: Long get() = categories.sumOf { it.tokens }

    /** Share of the measured categories, 0..1 (what the stacked bar and the % column show). */
    fun share(category: ContextCategory): Float =
        if (categoryTotal > 0) category.tokens.toFloat() / categoryTotal else 0f

    companion object {
        fun from(o: JSONObject): ContextBreakdown {
            val used = o.optLong("context_used", 0L)
            val max = o.optLong("context_max", 0L)
            val categories = o.optJSONArray("categories")?.let { rows ->
                (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }.map { row ->
                    val id = row.optString("id")
                    ContextCategory(id, row.optString("label").ifBlank { id.replace('_', ' ') }, row.optLong("tokens", 0L).coerceAtLeast(0L))
                }
            }.orEmpty()
            val files = o.optJSONArray("context_files")?.let { rows ->
                (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }.map { row ->
                    ContextFile(row.optString("label").ifBlank { row.optString("path").substringAfterLast('/') }, row.optString("path"), row.optString("status"))
                }
            }.orEmpty()
            return ContextBreakdown(
                used = used,
                max = max,
                percent = if (max > 0) (used * 100L / max).toInt().coerceIn(0, 100) else null,
                estimated = o.optBoolean("context_estimated", false),
                model = o.optString("model"),
                categories = categories,
                files = files
            )
        }
    }
}

/** How full the window is, which drives the ring color and the compress hint. */
enum class ContextPressure { UNKNOWN, LOW, MEDIUM, HIGH;
    companion object {
        fun of(percent: Int?): ContextPressure = when {
            percent == null -> UNKNOWN
            percent >= 85 -> HIGH
            percent >= 60 -> MEDIUM
            else -> LOW
        }
    }
}
