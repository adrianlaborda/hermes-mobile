package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.data.ActiveChat
import com.nousresearch.hermesmobile.ui.screens.ChatConversationIdentity
import com.nousresearch.hermesmobile.ui.screens.ChatListEntry
import com.nousresearch.hermesmobile.ui.screens.isAtLatest
import com.nousresearch.hermesmobile.ui.screens.reverseChatEntries
import com.nousresearch.hermesmobile.ui.screens.shouldLoadOlder
import com.nousresearch.hermesmobile.ui.screens.shouldShowJumpToLatest
import com.nousresearch.hermesmobile.ui.screens.unseenMessageCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScrollPolicyTest {
    @Test
    fun reverseEntriesPutBottomLaneFirstNewestNextAndOlderPagerLast() {
        val entries = reverseChatEntries(listOf("a", "b", "c"), hasOlder = true) { it }
        assertEquals(ChatListEntry.Bottom, entries.first())
        assertEquals(listOf("c", "b", "a"), entries.filterIsInstance<ChatListEntry.Row<String>>().map { it.row })
        assertEquals(ChatListEntry.Older, entries.last())
    }

    @Test
    fun reverseEntriesOmitOlderPagerWhenHistoryIsComplete() {
        val entries = reverseChatEntries(listOf("a"), hasOlder = false) { it }
        assertEquals(listOf<Any>("chat:bottom", "a"), entries.map { it.key })
    }

    @Test
    fun appendingRowsKeepsBottomLaneAsIndexZeroAndHistoryKeysStable() {
        // The lazy list anchors on the first visible key; appends must never re-key history.
        val before = reverseChatEntries(listOf("m1", "m2"), hasOlder = false) { it }.map { it.key }
        val after = reverseChatEntries(listOf("m1", "m2", "m3"), hasOlder = false) { it }.map { it.key }
        assertEquals("chat:bottom", after[0])
        assertEquals("m3", after[1])
        assertTrue(after.containsAll(before))
    }

    @Test
    fun prependingOlderHistoryDoesNotMoveNewestRows() {
        val before = reverseChatEntries(listOf("m5", "m6"), hasOlder = true) { it }.map { it.key }
        val after = reverseChatEntries(listOf("m3", "m4", "m5", "m6"), hasOlder = true) { it }.map { it.key }
        assertEquals(before.take(3), after.take(3))
    }

    @Test
    fun atLatestOnlyWhenBottomLaneRestsAtOffsetZero() {
        assertTrue(isAtLatest(0, 0))
        assertFalse(isAtLatest(0, 12))
        assertFalse(isAtLatest(3, 0))
    }

    @Test
    fun jumpButtonIgnoresTinyOverscrollButShowsOnceHistoryIsVisible() {
        assertFalse(shouldShowJumpToLatest(0, 0, thresholdPx = 120))
        assertFalse(shouldShowJumpToLatest(0, 60, thresholdPx = 120))
        assertTrue(shouldShowJumpToLatest(0, 200, thresholdPx = 120))
        assertTrue(shouldShowJumpToLatest(1, 10, thresholdPx = 120))
        assertTrue(shouldShowJumpToLatest(7, 0, thresholdPx = 120))
    }

    @Test
    fun olderHistoryLoadsNearTopOnlyWhenAvailableAndIdle() {
        assertTrue(shouldLoadOlder(lastVisibleIndex = 17, totalItems = 20, hasOlder = true, loading = false))
        assertFalse(shouldLoadOlder(lastVisibleIndex = 10, totalItems = 20, hasOlder = true, loading = false))
        assertFalse(shouldLoadOlder(lastVisibleIndex = 19, totalItems = 20, hasOlder = false, loading = false))
        assertFalse(shouldLoadOlder(lastVisibleIndex = 19, totalItems = 20, hasOlder = true, loading = true))
        assertFalse(shouldLoadOlder(lastVisibleIndex = null, totalItems = 0, hasOlder = true, loading = false))
    }

    @Test
    fun unseenCountTracksOnlyMessagesAppendedAfterReaderLeftBottom() {
        val keys = listOf<Any>("m1", "m2", "m3", "m4")
        assertEquals(0, unseenMessageCount(atLatest = true, baselineKey = "m2", messageKeys = keys))
        assertEquals(0, unseenMessageCount(atLatest = false, baselineKey = null, messageKeys = keys))
        assertEquals(2, unseenMessageCount(atLatest = false, baselineKey = "m2", messageKeys = keys))
        // Paging older history in above the baseline is not "new".
        assertEquals(0, unseenMessageCount(atLatest = false, baselineKey = "m4", messageKeys = listOf<Any>("m0") + keys))
        assertEquals(0, unseenMessageCount(atLatest = false, baselineKey = "gone", messageKeys = keys))
    }

    @Test
    fun conversationIdentityIgnoresRuntimeTransportHandle() {
        val first = ActiveChat(storedSessionId = "s1", runtimeSessionId = "rt-1", profile = "default", title = "")
        val resumed = first.copy(runtimeSessionId = "rt-2")
        assertEquals(ChatConversationIdentity.from(first), ChatConversationIdentity.from(resumed))
    }
}
