package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.data.HermesSession
import com.nousresearch.hermesmobile.data.KanbanBoardSnapshot
import com.nousresearch.hermesmobile.data.KanbanBoards
import com.nousresearch.hermesmobile.data.KanbanColumn
import com.nousresearch.hermesmobile.data.KanbanTask
import com.nousresearch.hermesmobile.data.KanbanTaskDetail
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KanbanTest {
    @Test
    fun boardsReadCurrentAndCounts() {
        val boards = KanbanBoards.fromJson(JSONObject(
            """{"boards":[
                {"slug":"default","name":"Default","total":4,"is_current":true,"counts":{"ready":2}},
                {"slug":"ops","name":"","total":0,"is_current":false}
            ],"current":"default"}"""
        ))
        assertEquals("default", boards.current)
        assertEquals(2, boards.boards.size)
        assertTrue(boards.boards[0].isCurrent)
        assertEquals(4, boards.boards[0].total)
        // A board without a display name falls back to its slug.
        assertEquals("ops", boards.boards[1].name)
    }

    @Test
    fun boardSnapshotGroupsCardsByColumn() {
        val snapshot = KanbanBoardSnapshot.fromJson(JSONObject(
            """{
                "columns":[
                    {"name":"blocked","tasks":[
                        {"id":"t_1","title":"Wait for review","status":"blocked","assignee":"reviewer",
                         "priority":2,"block_kind":"needs_input","block_recurrences":1,"created_at":1700000000,
                         "comment_count":3,"latest_summary":null,"tenant":null,"started_at":null}
                    ]},
                    {"name":"running","tasks":[
                        {"id":"t_2","title":"Build feature","status":"running","assignee":null,"priority":0,
                         "started_at":1700000100,"worker_pid":4242,"last_heartbeat_at":1700000160,
                         "consecutive_failures":2,"last_failure_error":"exit 1"}
                    ]},
                    {"name":"done","tasks":[]}
                ],
                "tenants":[],"assignees":["reviewer","builder"],"latest_event_id":17,"now":1700000200
            }"""
        ))
        assertEquals(listOf("blocked", "running", "done"), snapshot.columns.map { it.name })
        assertEquals(2, snapshot.taskCount)
        assertEquals(listOf("reviewer", "builder"), snapshot.assignees)

        val blocked = snapshot.columns[0].tasks.single()
        assertEquals("t_1", blocked.id)
        assertEquals("reviewer", blocked.assignee)
        assertEquals(2, blocked.priority)
        assertEquals("needs_input", blocked.blockKind)
        assertEquals(3, blocked.commentCount)
        assertEquals("", blocked.latestSummary)

        val running = snapshot.columns[1].tasks.single()
        assertNull(running.assignee)
        assertEquals(4242L, running.workerPid)
        assertEquals(1700000100L, running.startedAt)
        assertEquals("exit 1", running.lastFailureError)
        assertEquals(2, running.consecutiveFailures)
        assertNull(running.blockKind)
    }

    @Test
    fun taskDetailKeepsSiblingCollections() {
        val detail = KanbanTaskDetail.fromJson(JSONObject(
            """{
                "task":{"id":"t_9","title":"Write notes","body":"Summarise the thread","status":"review",
                        "assignee":"writer","priority":0,"created_at":1700000000,"result":"Draft saved"},
                "comments":[{"id":3,"author":"desktop","body":"Looks good","created_at":1700000300}],
                "events":[{"id":1,"kind":"created","payload":{},"created_at":1700000000},
                          {"id":2,"kind":"status","payload":{"status":"review"},"created_at":1700000400}],
                "attachments":[],
                "links":{"parents":[],"children":[]},
                "runs":[]
            }"""
        ))!!
        assertEquals("t_9", detail.task.id)
        assertEquals("Summarise the thread", detail.task.body)
        assertEquals("Draft saved", detail.result)
        assertEquals("Looks good", detail.comments.single().body)
        assertEquals(listOf("created", "status"), detail.events.map { it.kind })
    }

    @Test
    fun taskDetailWithoutTaskIsRejected() {
        assertNull(KanbanTaskDetail.fromJson(JSONObject("""{"comments":[],"events":[]}""")))
    }

    @Test
    fun kanbanWorkerSessionsAreInternalRuns() {
        val worker = HermesSession(id = "s1", source = "kanban")
        val tool = HermesSession(id = "s2", source = "tool")
        val oneshot = HermesSession(id = "s3", source = " OneShot ")
        val cron = HermesSession(id = "s4", source = "cron")
        val chat = HermesSession(id = "s5", source = "mobile")

        assertTrue(worker.isKanbanWorker)
        assertTrue(worker.isInternalRun)
        assertTrue(tool.isInternalRun)
        assertFalse(tool.isKanbanWorker)
        assertTrue(oneshot.isInternalRun)
        // Routines keep their own group, so they are not internal runs.
        assertTrue(cron.isRoutineRun)
        assertFalse(cron.isInternalRun)
        assertFalse(chat.isInternalRun)
        assertFalse(chat.isKanbanWorker)
    }

    @Test
    fun movingACardLandsItFirstInTheTargetColumn() {
        val snapshot = KanbanBoardSnapshot(
            columns = listOf(
                KanbanColumn("todo", listOf(task("t_a", "todo"), task("t_b", "todo"))),
                KanbanColumn("done", listOf(task("t_c", "done")))
            )
        )

        val moved = snapshot.moveTask("t_b", "done")

        assertEquals(listOf("t_a"), moved.columns[0].tasks.map { it.id })
        assertEquals(listOf("t_b", "t_c"), moved.columns[1].tasks.map { it.id })
        assertEquals("done", moved.columns[1].tasks.first().status)
        assertEquals(snapshot.taskCount, moved.taskCount)
    }

    @Test
    fun movingAnUnknownCardChangesNothing() {
        val snapshot = KanbanBoardSnapshot(
            columns = listOf(KanbanColumn("todo", listOf(task("t_a", "todo"))), KanbanColumn("done", emptyList()))
        )

        assertEquals(snapshot, snapshot.moveTask("t_missing", "done"))
    }

    private fun task(id: String, status: String) = KanbanTask(id = id, title = id, status = status)
}
