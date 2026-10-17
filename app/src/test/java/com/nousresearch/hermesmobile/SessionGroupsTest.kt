package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.data.HermesSession
import com.nousresearch.hermesmobile.ui.screens.SessionBucket
import com.nousresearch.hermesmobile.ui.screens.groupSessions
import com.nousresearch.hermesmobile.ui.screens.sessionBucket
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class SessionGroupsTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 10, 12)
    private fun at(day: LocalDate, hour: Int = 12) = day.atTime(hour, 0).toEpochSecond(ZoneOffset.UTC)

    @Test
    fun bucketsAcceptSecondsAndMillis() {
        assertEquals(SessionBucket.TODAY, sessionBucket(at(today), today, zone))
        assertEquals(SessionBucket.TODAY, sessionBucket(at(today) * 1000, today, zone))
        assertEquals(SessionBucket.YESTERDAY, sessionBucket(at(today.minusDays(1)), today, zone))
        assertEquals(SessionBucket.WEEK, sessionBucket(at(today.minusDays(6)), today, zone))
        assertEquals(SessionBucket.OLDER, sessionBucket(at(today.minusDays(7)), today, zone))
        assertEquals(SessionBucket.OLDER, sessionBucket(0, today, zone))
    }

    @Test
    fun pinnedFirstThenRecencyThenChannelsByPlatform() {
        val sessions = listOf(
            HermesSession(id = "old", profile = "default", title = "", source = "desktop", lastActive = at(today.minusDays(30))),
            HermesSession(id = "tg", profile = "default", title = "", source = "telegram", lastActive = at(today)),
            HermesSession(id = "pin", profile = "default", title = "", source = "mobile", lastActive = at(today.minusDays(40)), pinned = true),
            HermesSession(id = "now2", profile = "default", title = "", source = "mobile", lastActive = at(today, 9)),
            HermesSession(id = "now1", profile = "default", title = "", source = "desktop", lastActive = at(today, 15)),
            HermesSession(id = "yday", profile = "default", title = "", source = "cli", lastActive = at(today.minusDays(1)))
        )
        val sections = groupSessions(sessions, today, zone)
        assertEquals(
            listOf("pinned", "bucket:TODAY", "bucket:YESTERDAY", "bucket:OLDER", "channel:Telegram"),
            sections.map { it.key }
        )
        assertEquals(listOf("now1", "now2"), sections[1].rows.map { it.id })
    }

    @Test
    fun routineRunsGoLastUnlessPinned() {
        val sessions = listOf(
            HermesSession(id = "run", source = "cron", lastActive = at(today, 18)),
            HermesSession(id = "chat", source = "mobile", lastActive = at(today, 9)),
            HermesSession(id = "tg", source = "telegram", lastActive = at(today)),
            HermesSession(id = "kept", source = "cron", lastActive = at(today.minusDays(3)), pinned = true)
        )
        val sections = groupSessions(sessions, today, zone)
        assertEquals(listOf("pinned", "bucket:TODAY", "channel:Telegram", "routines"), sections.map { it.key })
        assertEquals(listOf("kept"), sections.first().rows.map { it.id })
        assertEquals(listOf("run"), sections.last().rows.map { it.id })
    }

    @Test
    fun aCompressedChatShowsOnce() {
        val old = HermesSession("a", title = "Chat", lastActive = 200, lineageRootId = "a")
        val tip = HermesSession("b", title = "Chat", lastActive = 100, lineageRootId = "a", parentSessionId = "a")
        val other = HermesSession("c", lastActive = 50)
        assertEquals(listOf("b", "c"), HermesSession.collapseLineages(listOf(old, tip, other)).map { it.id })
    }
}
