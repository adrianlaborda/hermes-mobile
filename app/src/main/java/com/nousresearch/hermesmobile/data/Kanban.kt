package com.nousresearch.hermesmobile.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The Kanban plugin's REST contract (`/api/plugins/kanban/...`, `plugins/kanban/dashboard/plugin_api.py`), read the way
 * Desktop reads it. Only the fields the phone shows are kept; every parser tolerates missing keys.
 */

/** One board on disk, from `GET /boards`. */
data class KanbanBoardMeta(
    val slug: String,
    val name: String,
    val total: Int = 0,
    val isCurrent: Boolean = false
) {
    companion object {
        fun fromJson(o: JSONObject): KanbanBoardMeta? {
            val slug = o.optString("slug").takeIf { it.isNotBlank() } ?: return null
            return KanbanBoardMeta(
                slug = slug,
                name = o.optStringOrNull("name") ?: slug,
                total = o.optInt("total", 0),
                isCurrent = o.optBoolean("is_current", false)
            )
        }
    }
}

/** `GET /boards`: every board plus the server's current one. */
data class KanbanBoards(val boards: List<KanbanBoardMeta>, val current: String) {
    companion object {
        fun fromJson(o: JSONObject): KanbanBoards = KanbanBoards(
            boards = (o.optJSONArray("boards") ?: JSONArray()).objects().mapNotNull(KanbanBoardMeta::fromJson),
            current = o.optString("current")
        )
    }
}

/**
 * One card. [status] is the column name (`triage`, `todo`, `scheduled`, `ready`, `running`, `blocked`, `review`,
 * `done`, `archived`). [blockKind] is set only while the card is blocked (`dependency`, `needs_input`, `capability`,
 * `transient`).
 */
data class KanbanTask(
    val id: String,
    val title: String,
    val body: String = "",
    val status: String,
    val assignee: String? = null,
    val priority: Int = 0,
    val createdAt: Long = 0L,
    val startedAt: Long = 0L,
    val completedAt: Long = 0L,
    val latestSummary: String = "",
    val commentCount: Int = 0,
    val blockKind: String? = null,
    val lastFailureError: String? = null,
    val consecutiveFailures: Int = 0,
    val workerPid: Long? = null
) {
    companion object {
        fun fromJson(o: JSONObject): KanbanTask? {
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
            return KanbanTask(
                id = id,
                title = o.optString("title").ifBlank { id },
                body = o.optStringOrNull("body").orEmpty(),
                status = o.optString("status"),
                assignee = o.optStringOrNull("assignee")?.takeIf { it.isNotBlank() },
                priority = o.optInt("priority", 0),
                createdAt = o.optLong("created_at", 0L),
                startedAt = o.optLong("started_at", 0L),
                completedAt = o.optLong("completed_at", 0L),
                latestSummary = o.optStringOrNull("latest_summary").orEmpty(),
                commentCount = o.optInt("comment_count", 0),
                blockKind = o.optStringOrNull("block_kind")?.takeIf { it.isNotBlank() },
                lastFailureError = o.optStringOrNull("last_failure_error")?.takeIf { it.isNotBlank() },
                consecutiveFailures = o.optInt("consecutive_failures", 0),
                workerPid = o.optLongOrNull("worker_pid")
            )
        }
    }
}

data class KanbanColumn(val name: String, val tasks: List<KanbanTask>)

/** Columns the server owns (Desktop's LOCKED_COLUMNS): a card can leave them but never be moved into them. */
val KANBAN_LOCKED_COLUMNS = setOf("review", "running", "scheduled")

/** `GET /board?board=`: the cards grouped by status column. */
data class KanbanBoardSnapshot(
    val columns: List<KanbanColumn>,
    val assignees: List<String> = emptyList()
) {
    val taskCount: Int get() = columns.sumOf { it.tasks.size }

    /** Desktop's moveCard: the card leaves its column and lands first in [status]; an unknown id changes nothing. */
    fun moveTask(taskId: String, status: String): KanbanBoardSnapshot {
        val task = columns.firstNotNullOfOrNull { column -> column.tasks.firstOrNull { it.id == taskId } } ?: return this
        val moved = task.copy(status = status)
        return copy(columns = columns.map { column ->
            val kept = column.tasks.filter { it.id != taskId }
            when (column.name) {
                status -> column.copy(tasks = listOf(moved) + kept)
                else -> column.copy(tasks = kept)
            }
        })
    }

    companion object {
        fun fromJson(o: JSONObject): KanbanBoardSnapshot = KanbanBoardSnapshot(
            columns = (o.optJSONArray("columns") ?: JSONArray()).objects().map { column ->
                KanbanColumn(
                    name = column.optString("name"),
                    tasks = (column.optJSONArray("tasks") ?: JSONArray()).objects().mapNotNull(KanbanTask::fromJson)
                )
            },
            assignees = (o.optJSONArray("assignees") ?: JSONArray()).strings()
        )
    }
}

data class KanbanComment(val author: String, val body: String, val createdAt: Long) {
    companion object {
        fun fromJson(o: JSONObject): KanbanComment = KanbanComment(
            author = o.optString("author"),
            body = o.optString("body"),
            createdAt = o.optLong("created_at", 0L)
        )
    }
}

/** A task's activity line: its `kind` (`created`, `status`, `blocked`, `reclaimed`…) and when it happened. */
data class KanbanEvent(val kind: String, val createdAt: Long) {
    companion object {
        fun fromJson(o: JSONObject): KanbanEvent = KanbanEvent(
            kind = o.optString("kind"),
            createdAt = o.optLong("created_at", 0L)
        )
    }
}

/** `GET /tasks/{id}`: the card plus its comments and recent events, which are siblings of `task`. */
data class KanbanTaskDetail(
    val task: KanbanTask,
    val result: String = "",
    val comments: List<KanbanComment> = emptyList(),
    val events: List<KanbanEvent> = emptyList()
) {
    companion object {
        fun fromJson(o: JSONObject): KanbanTaskDetail? {
            val taskJson = o.optJSONObject("task") ?: return null
            val task = KanbanTask.fromJson(taskJson) ?: return null
            return KanbanTaskDetail(
                task = task,
                result = taskJson.optStringOrNull("result").orEmpty(),
                comments = (o.optJSONArray("comments") ?: JSONArray()).objects().map(KanbanComment::fromJson),
                events = (o.optJSONArray("events") ?: JSONArray()).objects().map(KanbanEvent::fromJson)
            )
        }
    }
}

private fun JSONArray.strings(): List<String> = buildList {
    for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let(::add)
}
