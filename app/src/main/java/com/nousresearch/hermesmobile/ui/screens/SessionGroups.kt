package com.nousresearch.hermesmobile.ui.screens

import com.nousresearch.hermesmobile.data.HermesSession
import com.nousresearch.hermesmobile.data.SessionSourceKind
import com.nousresearch.hermesmobile.data.sessionSourceInfo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Desktop-style recency buckets for the conversation list. */
internal enum class SessionBucket { PINNED, TODAY, YESTERDAY, WEEK, OLDER }

internal data class SessionSection(
    val key: String,
    val bucket: SessionBucket? = null,
    /** Messaging platform label when the section groups channel sessions. */
    val channel: String? = null,
    /** Runs of scheduled jobs, kept apart from the conversations. */
    val routines: Boolean = false,
    val rows: List<HermesSession>
)

internal fun sessionEpochMillis(ts: Long): Long = if (ts < 10_000_000_000L) ts * 1000L else ts

internal fun sessionBucket(lastActive: Long, today: LocalDate, zone: ZoneId): SessionBucket {
    if (lastActive <= 0L) return SessionBucket.OLDER
    val day = Instant.ofEpochMilli(sessionEpochMillis(lastActive)).atZone(zone).toLocalDate()
    return when {
        !day.isBefore(today) -> SessionBucket.TODAY
        day == today.minusDays(1) -> SessionBucket.YESTERDAY
        day.isAfter(today.minusDays(7)) -> SessionBucket.WEEK
        else -> SessionBucket.OLDER
    }
}

/**
 * Pinned first, then Hermes-app and client sessions by recency, then one section per
 * messaging platform (Telegram, Discord…) so channel traffic does not bury direct chats,
 * then the runs of scheduled jobs, which would otherwise top the list every time one fires.
 */
internal fun groupSessions(
    sessions: List<HermesSession>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault()
): List<SessionSection> {
    // Kanban workers, tool and one-shot runs stay out of the recents (Desktop's INTERNAL_LISTING_SOURCES).
    val byRecency = HermesSession.collapseLineages(sessions.filterNot { it.isInternalRun })
        .sortedByDescending { sessionEpochMillis(it.lastActive) }
    val pinned = byRecency.filter { it.pinned }
    val routines = byRecency.filter { !it.pinned && it.isRoutineRun }
    val rest = byRecency.filterNot { it.pinned || it.isRoutineRun }
    val direct = rest.filter { sessionSourceInfo(it.source).kind != SessionSourceKind.CHANNEL }
    val channels = rest.filter { sessionSourceInfo(it.source).kind == SessionSourceKind.CHANNEL }
        .groupBy { sessionSourceInfo(it.source).label }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    return buildList {
        if (pinned.isNotEmpty()) add(SessionSection("pinned", SessionBucket.PINNED, rows = pinned))
        direct.groupBy { sessionBucket(it.lastActive, today, zone) }
            .toSortedMap()
            .forEach { (bucket, rows) -> add(SessionSection("bucket:${bucket.name}", bucket, rows = rows)) }
        channels.forEach { (label, rows) -> add(SessionSection("channel:$label", channel = label, rows = rows)) }
        if (routines.isNotEmpty()) add(SessionSection("routines", routines = true, rows = routines))
    }
}
