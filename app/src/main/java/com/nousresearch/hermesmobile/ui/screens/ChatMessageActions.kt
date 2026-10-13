package com.nousresearch.hermesmobile.ui.screens

/**
 * Where assistant actions (copy, share, read aloud, retry, branch) render. An agent turn
 * can span several assistant rows interleaved with tool activity; like Desktop, the
 * actions sit once under the turn's final answer. Retry only applies to the newest turn,
 * because the gateway's `/retry` rewinds the last user message.
 */
internal data class AssistantTurnEnds(val keys: Set<Any>, val retryKey: Any?)

internal enum class TurnRole { USER, ASSISTANT, OTHER }

internal fun <T> assistantTurnEnds(
    rows: List<T>,
    key: (T) -> Any,
    role: (T) -> TurnRole,
    actionable: (T) -> Boolean,
    /** While the agent is still working, the newest turn has no final answer yet. */
    newestTurnOpen: Boolean = false
): AssistantTurnEnds {
    val keys = LinkedHashSet<Any>()
    var retryKey: Any? = null
    var turnHasEnd = false
    var newestTurn = true
    for (i in rows.indices.reversed()) {
        val row = rows[i]
        when (role(row)) {
            TurnRole.USER -> { turnHasEnd = false; newestTurn = false }
            TurnRole.ASSISTANT -> if (!turnHasEnd && !(newestTurn && newestTurnOpen) && actionable(row)) {
                keys += key(row)
                if (newestTurn) retryKey = key(row)
                turnHasEnd = true
            }
            TurnRole.OTHER -> {}
        }
    }
    return AssistantTurnEnds(keys, retryKey)
}
