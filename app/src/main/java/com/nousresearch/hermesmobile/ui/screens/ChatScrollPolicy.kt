package com.nousresearch.hermesmobile.ui.screens

import com.nousresearch.hermesmobile.data.ActiveChat

/** Durable UI identity: runtimeSessionId is a replaceable gateway transport handle. */
internal data class ChatConversationIdentity(
    val profile: String,
    val storedSessionId: String
) {
    companion object {
        fun from(chat: ActiveChat) = ChatConversationIdentity(chat.profile, chat.storedSessionId)
    }
}

/*
 * The transcript is a reverse-layout LazyColumn. Index 0 is the bottom lane (live
 * status, queue), the newest row sits directly above it and older history grows
 * toward higher indices. Resting on the latest message is the list's natural
 * position, and the lazy list anchors on the first visible key:
 *
 *  - at the bottom, the anchor is the bottom lane, so streamed text and appended
 *    rows push history up and stay in view without any scroll call;
 *  - while reading history, the anchor is a history row, so appended rows never
 *    move the viewport.
 *
 * There is deliberately no programmatic "follow" scroll. Earlier builds raced such
 * calls against flings and size changes, which yanked readers back to the latest
 * message in a loop. The only programmatic scrolls left are user-initiated: sending
 * a message and tapping "jump to latest".
 */
internal sealed interface ChatListEntry<out T> {
    val key: Any

    /** Always measured with a non-zero height so it stays the bottom anchor. */
    data object Bottom : ChatListEntry<Nothing> {
        override val key: Any = "chat:bottom"
    }

    data object Older : ChatListEntry<Nothing> {
        override val key: Any = "chat:older"
    }

    data class Row<T>(override val key: Any, val row: T) : ChatListEntry<T>
}

/** Converts chronological rows into reverse-layout order: bottom lane, newest … oldest, older pager. */
internal fun <T> reverseChatEntries(
    rows: List<T>,
    hasOlder: Boolean,
    key: (T) -> Any
): List<ChatListEntry<T>> = buildList(rows.size + 2) {
    add(ChatListEntry.Bottom)
    for (i in rows.indices.reversed()) add(ChatListEntry.Row(key(rows[i]), rows[i]))
    if (hasOlder) add(ChatListEntry.Older)
}

/** True when nothing newer is below the viewport (reverse layout: index 0 at offset 0). */
internal fun isAtLatest(firstVisibleIndex: Int, firstVisibleOffset: Int): Boolean =
    firstVisibleIndex == 0 && firstVisibleOffset == 0

/** Hide the jump control for tiny overscrolls near the bottom lane. */
internal fun shouldShowJumpToLatest(firstVisibleIndex: Int, firstVisibleOffset: Int, thresholdPx: Int): Boolean =
    firstVisibleIndex > 1 || (firstVisibleIndex == 1 && firstVisibleOffset > 0) ||
        (firstVisibleIndex == 0 && firstVisibleOffset > thresholdPx)

/** Start paging older history before the reader reaches the very top. */
internal fun shouldLoadOlder(
    lastVisibleIndex: Int?,
    totalItems: Int,
    hasOlder: Boolean,
    loading: Boolean,
    prefetchRows: Int = 3
): Boolean = hasOlder && !loading && lastVisibleIndex != null && totalItems > 0 &&
    lastVisibleIndex >= totalItems - 1 - prefetchRows

/**
 * Counts messages appended after the reader left the bottom. [baselineKey] is the key of
 * the newest message at that moment; history paged in above it is never "new".
 */
internal fun unseenMessageCount(atLatest: Boolean, baselineKey: Any?, messageKeys: List<Any>): Int {
    if (atLatest || baselineKey == null) return 0
    val index = messageKeys.lastIndexOf(baselineKey)
    return if (index < 0) 0 else messageKeys.size - 1 - index
}
