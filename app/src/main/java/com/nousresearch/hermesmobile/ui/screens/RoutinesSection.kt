package com.nousresearch.hermesmobile.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.data.CronJob
import com.nousresearch.hermesmobile.data.CronRun
import com.nousresearch.hermesmobile.ui.theme.hermes

/** The scheduled jobs the Chats list shows in its Routines section, and what a row can do with them. */
class RoutineActions internal constructor(
    val jobs: List<CronJob>,
    val runs: Map<String, List<CronRun>>,
    val onLoadRuns: (CronJob) -> Unit,
    val onTrigger: (CronJob) -> Unit,
    val onPause: (CronJob) -> Unit,
    val onResume: (CronJob) -> Unit,
    val onManage: () -> Unit,
    val onDelete: (CronJob) -> Unit,
    val onOpenRun: (CronJob, CronRun) -> Unit
)

/** Desktop's `jobState`: the explicit state wins, otherwise the enabled flag says. */
internal fun cronJobState(job: CronJob): String = job.state.trim().lowercase().ifBlank { if (job.enabled) "scheduled" else "disabled" }

/** Desktop's `jobTitle`: name, else the start of the prompt or script, else the id. */
internal fun cronJobTitle(job: CronJob): String = job.name.trim().ifBlank {
    listOf(job.prompt, job.script).map { it.trim() }.firstOrNull { it.isNotBlank() }?.let { if (it.length > 60) it.take(60) + "…" else it }
        ?: job.id
}

private val INACTIVE_STATES = setOf("completed", "disabled", "error", "paused")

/** When the job fires next (epoch millis), or null when it is not expected to. */
internal fun cronNextRunMillis(job: CronJob): Long? =
    job.nextRunAt?.takeIf { it > 0L && cronJobState(job) !in INACTIVE_STATES }?.let { if (it < 10_000_000_000L) it * 1000L else it }

/** Desktop's sidebar order: soonest next run first, jobs that will not fire last, then by name. */
internal val cronJobOrder: Comparator<CronJob> = compareBy<CronJob, Long?>(nullsLast()) { cronNextRunMillis(it) }
    .thenBy { cronJobTitle(it).lowercase() }

internal const val ROUTINES_INITIAL = 3
private const val ROUTINES_STEP = 10
private const val PEEK_RUNS = 5

private fun runsKey(job: CronJob) = "${job.profile}:${job.id}"

/**
 * Desktop's Cron sidebar section: the jobs (not each run), a few up front and more on demand. Tapping a job peeks at
 * its latest runs, which open like any chat; a long press holds its actions.
 */
internal fun LazyListScope.routineItems(
    routines: RoutineActions,
    jobs: List<CronJob>,
    visible: Int,
    onShowMore: (Int) -> Unit,
    expandedKey: String?,
    onExpand: (String?) -> Unit
) {
    items(jobs.take(visible), key = { "routine:${runsKey(it)}" }, contentType = { "routine" }) { job ->
        val key = runsKey(job)
        Column(Modifier.animateItem()) {
            RoutineRow(job, routines, expanded = expandedKey == key, onToggle = {
                if (expandedKey == key) onExpand(null) else { onExpand(key); routines.onLoadRuns(job) }
            })
            if (expandedKey == key) RoutineRuns(job, routines.runs[key]) { routines.onOpenRun(job, it) }
        }
    }
    if (jobs.size > visible) item(key = "routines:more", contentType = "more") {
        val step = minOf(ROUTINES_STEP, jobs.size - visible)
        TextButton(onClick = { onShowMore(visible + ROUTINES_STEP) }, modifier = Modifier.padding(start = 10.dp)) {
            Text(stringResource(R.string.routines_show_more, step), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoutineRow(job: CronJob, routines: RoutineActions, expanded: Boolean, onToggle: () -> Unit) {
    var sheet by remember { mutableStateOf(false) }
    val state = cronJobState(job)
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(
                onClickLabel = stringResource(if (expanded) R.string.routines_hide_runs else R.string.routines_show_runs),
                role = Role.Button, onClick = onToggle, onLongClick = { sheet = true }
            )
            .padding(start = 22.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(if (state == "running") 7.dp else 5.dp).background(routineDotColor(state), CircleShape))
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(cronJobTitle(job), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.padding(start = 2.dp).size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Text(routineMeta(job), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
    if (sheet) RoutineActionsSheet(job, routines) { sheet = false }
}

/** Desktop's STATE_DOT: accent while it will fire, amber paused, red failed, faint once done. */
@Composable
private fun routineDotColor(state: String): Color = when (state) {
    "paused" -> Color(0xFFF59E0B)
    "error" -> MaterialTheme.colorScheme.error
    "completed", "disabled" -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f)
    else -> MaterialTheme.colorScheme.primary
}

/** When it fires next, or why it will not. */
@Composable
private fun routineMeta(job: CronJob): String = when (cronJobState(job)) {
    "paused" -> stringResource(R.string.routines_state_paused)
    "completed" -> stringResource(R.string.routines_state_completed)
    "disabled" -> stringResource(R.string.routines_state_disabled)
    "error" -> stringResource(R.string.routines_state_error)
    else -> cronNextRunMillis(job)?.let { DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() } ?: "—"
}

/** The same bottom sheet a chat's long press opens, with the job's actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineActionsSheet(job: CronJob, routines: RoutineActions, onDismiss: () -> Unit) {
    val state = cronJobState(job)
    val paused = state == "paused" || !job.enabled
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(MaterialTheme.hermes.accentTint, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Schedule, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(cronJobTitle(job), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOf(job.profile, job.scheduleDisplay.ifBlank { job.schedule }, routineMeta(job)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.hermes.line)
        fun act(block: () -> Unit): () -> Unit = { onDismiss(); block() }
        SheetAction(Icons.Default.Bolt, stringResource(R.string.routines_run_now), act { routines.onTrigger(job) })
        SheetAction(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
            stringResource(if (paused) R.string.routines_resume else R.string.routines_pause),
            act { if (paused) routines.onResume(job) else routines.onPause(job) })
        SheetAction(Icons.Default.Tune, stringResource(R.string.routines_manage), act(routines.onManage))
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.hermes.line)
        SheetAction(Icons.Default.DeleteOutline, stringResource(R.string.ui_e2d0a54968), { confirmDelete = true }, destructive = true)
        Spacer(Modifier.height(16.dp))
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.routines_delete_title)) },
        text = { Text(cronJobTitle(job)) },
        confirmButton = {
            TextButton(
                onClick = { confirmDelete = false; onDismiss(); routines.onDelete(job) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.ui_e2d0a54968)) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.ui_19766ed6cc)) } }
    )
}

/** The job's latest runs under its row; null while they load. */
@Composable
private fun RoutineRuns(job: CronJob, runs: List<CronRun>?, onOpen: (CronRun) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 39.dp, end = 18.dp, bottom = 6.dp)) {
        when {
            runs == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 10.dp))
            runs.isEmpty() -> Text(stringResource(R.string.routines_no_runs), Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> runs.take(PEEK_RUNS).forEach { run ->
                val at = run.startedAt ?: run.lastActiveAt
                Row(
                    // A script-only fire has nothing to open (Desktop shows it as plain text too).
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                        .clickable(enabled = run.id.isNotBlank() && !run.outputOnly) { onOpen(run) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(run.preview.ifBlank { run.title.ifBlank { cronJobTitle(job) } }, Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (at != null) Text(
                        DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                        Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
