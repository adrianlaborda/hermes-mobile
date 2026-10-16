package com.nousresearch.hermesmobile.data

import org.json.JSONObject

/**
 * What a chat row's status dot says, as Hermes Desktop's sidebar resolves it (`session-dot-state.ts`), loudest first:
 * a turn waiting on an approval or a question, a turn running, a turn that finished while nobody was looking.
 */
enum class SessionDot { NEEDS_INPUT, WORKING, UNREAD, NONE }

/**
 * Which conversations the gateway process is running, from `session.active_list` (Desktop's sidebar liveness
 * snapshot). Keys are STORED session ids — the ids chat rows and bots carry. A turn that was live and is no longer
 * (idle, or gone from the snapshot) counts as finished unread unless that chat is open: Desktop's "your turn" dot.
 */
class LiveSessions {
    private var statuses: Map<String, String> = emptyMap()
    private val finished = linkedSetOf<String>()

    /** Runtime id → stored id, for matching stream events to rows. */
    var storedByRuntime: Map<String, String> = emptyMap()
        private set

    /** Applies one snapshot; [viewing] is the stored id of the chat on screen. Returns true when a dot changed. */
    fun apply(snapshot: JSONObject, viewing: String?): Boolean {
        val before = dots()
        val rows = snapshot.optJSONArray("sessions")
        val next = mutableMapOf<String, String>()
        val runtimes = mutableMapOf<String, String>()
        if (rows != null) for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val stored = row.optString("session_key").ifBlank { row.optString("id") }
            if (stored.isBlank()) continue
            next[stored] = row.optString("status", "idle")
            row.optString("id").takeIf { it.isNotBlank() }?.let { runtimes[it] = stored }
        }
        for ((id, status) in statuses) {
            if (live(status) && !live(next[id]) && id != viewing) finished += id
        }
        next.keys.filter { live(next[it]) }.forEach { finished -= it }
        statuses = next
        storedByRuntime = runtimes
        return dots() != before
    }

    /** The user opened this chat: its finished turn is read. */
    fun seen(storedId: String): Boolean = finished.remove(storedId)

    fun clear() { statuses = emptyMap(); finished.clear(); storedByRuntime = emptyMap() }

    /** Only the rows that show something; the server's own read-state flag joins in [dotFor]. */
    fun dots(): Map<String, SessionDot> = buildMap {
        finished.forEach { put(it, SessionDot.UNREAD) }
        statuses.forEach { (id, status) ->
            when {
                status == "waiting" -> put(id, SessionDot.NEEDS_INPUT)
                live(status) -> put(id, SessionDot.WORKING)
            }
        }
    }

    companion object {
        /** A turn is under way: Desktop's working (and the states that lead into it). */
        fun live(status: String?): Boolean = status in setOf("working", "starting", "streaming", "waiting")

        fun dotFor(dots: Map<String, SessionDot>, storedId: String?, backendUnread: Boolean = false): SessionDot =
            storedId?.let { dots[it] } ?: if (backendUnread) SessionDot.UNREAD else SessionDot.NONE
    }
}
