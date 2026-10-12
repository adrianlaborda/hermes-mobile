package com.nousresearch.hermesmobile.data

/** Presentation only: original transcript, ordering and reasoning remain intact. */
sealed interface ChatTimelineItem {
    val key: String
    data class Message(override val key: String, val message: ChatMessage) : ChatTimelineItem
    data class Activity(override val key: String, val steps: List<ChatMessage>) : ChatTimelineItem
}

fun chatTimeline(messages: List<ChatMessage>): List<ChatTimelineItem> = buildList {
    val pending = mutableListOf<ChatMessage>()
    var activityKey = ""
    val occurrences = mutableMapOf<String, Int>()
    fun flush() {
        if (pending.isNotEmpty()) {
            add(ChatTimelineItem.Activity(activityKey, pending.toList()))
            pending.clear()
        }
    }
    messages.filter { it.renderable }.forEach { message ->
        val base = message.stableKey
        val occurrence = occurrences.getOrDefault(base, 0)
        occurrences[base] = occurrence + 1
        val key = "$base:$occurrence"
        val ordinary = message.displayKind.isBlank() || message.displayKind == "null"
        val reasoning = ordinary && message.role == "assistant" && message.reasoning.isNotBlank()
        val tool = ordinary && message.role == "tool"
        if (reasoning || tool) {
            if (pending.isEmpty()) activityKey = "activity:$key"
            pending += if (reasoning) message.copy(content = "", attachments = emptyList()) else message
        }
        // Reasoning-only assistant rows and tool output become activity. Never
        // swallow a visible answer, attachment, user message or timeline notice.
        val activityOnly = ordinary && (tool || (reasoning && message.content.isBlank() && message.attachments.isEmpty()))
        if (!activityOnly) {
            flush()
            add(ChatTimelineItem.Message("message:$key", if (reasoning) message.copy(reasoning = "") else message))
        }
    }
    flush()
}

/** Pin context only while activity occupies the top of the viewport. */
fun List<ChatTimelineItem>.contextForActivity(index: Int): ChatTimelineItem.Message? =
    if(getOrNull(index) is ChatTimelineItem.Activity)
        take(index).filterIsInstance<ChatTimelineItem.Message>().lastOrNull()
    else null
