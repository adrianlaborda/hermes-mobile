package com.nousresearch.hermesmobile.ui.screens

import com.nousresearch.hermesmobile.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nousresearch.hermesmobile.data.KANBAN_LOCKED_COLUMNS
import com.nousresearch.hermesmobile.data.KanbanBoardSnapshot
import com.nousresearch.hermesmobile.data.KanbanBoards
import com.nousresearch.hermesmobile.data.KanbanColumn
import com.nousresearch.hermesmobile.data.KanbanTask
import com.nousresearch.hermesmobile.data.KanbanTaskDetail
import com.nousresearch.hermesmobile.ui.components.*
import com.nousresearch.hermesmobile.ui.theme.HermesMono
import com.nousresearch.hermesmobile.ui.theme.HermesTokens
import com.nousresearch.hermesmobile.ui.theme.hermes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Kanban boards of the gateway, one profile at a time: the cards per status column, and a card's detail sheet. */
@Composable
fun KanbanScreen(
    onMenu: () -> Unit,
    available: Boolean,
    boards: KanbanBoards?,
    board: String,
    snapshot: KanbanBoardSnapshot?,
    loading: Boolean,
    error: String?,
    taskId: String?,
    task: KanbanTaskDetail?,
    busy: Boolean,
    onRefresh: () -> Unit,
    onBoard: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onCloseTask: () -> Unit,
    onUnblock: (String) -> Unit,
    onRelease: (String) -> Unit,
    onMove: (taskId: String, status: String) -> Unit
) {
    val boardName = boards?.boards?.firstOrNull { it.slug == board }?.name ?: board
    Column(Modifier.fillMaxSize()) {
        HermesTopBar(
            stringResource(R.string.kanban_title),
            subtitle = boardName.takeIf { it.isNotBlank() },
            leading = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, stringResource(R.string.a21_menu)) } },
            actions = {
                if (available) IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Default.Refresh, stringResource(R.string.kanban_refresh)) }
            }
        )
        DashedDivider(color = MaterialTheme.hermes.line)
        when {
            !available -> EmptyState(Icons.Default.ViewKanban, stringResource(R.string.kanban_title), stringResource(R.string.kanban_unavailable))
            snapshot == null && loading -> HermesLoader(stringResource(R.string.kanban_loading))
            snapshot == null -> EmptyState(
                Icons.Default.ErrorOutline,
                stringResource(R.string.kanban_title),
                error ?: stringResource(R.string.kanban_no_boards)
            ) { TextButton(onClick = onRefresh) { Text(stringResource(R.string.kanban_refresh)) } }
            else -> KanbanBoardBody(boards, board, snapshot, loading, error, onBoard, onRefresh, onOpenTask, onMove)
        }
    }

    if (taskId != null) {
        KanbanTaskSheet(taskId, task, busy, snapshot?.columns?.map { it.name }.orEmpty(), onCloseTask, onUnblock, onRelease, onMove)
    }
}

@Composable
private fun ColumnScope.KanbanBoardBody(
    boards: KanbanBoards?,
    board: String,
    snapshot: KanbanBoardSnapshot,
    loading: Boolean,
    error: String?,
    onBoard: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenTask: (String) -> Unit,
    onMove: (taskId: String, status: String) -> Unit
) {
    if (boards != null && boards.boards.size > 1) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            boards.boards.forEach { meta ->
                FilterChip(selected = meta.slug == board, onClick = { onBoard(meta.slug) }, label = { Text(meta.name) })
            }
        }
    }
    if (snapshot.columns.isEmpty()) {
        EmptyState(Icons.Default.ViewKanban, stringResource(R.string.kanban_title), stringResource(R.string.kanban_no_tasks))
        return
    }
    if (error != null) Text(error, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)

    // The pull-to-refresh indicator shows every refresh, the top bar's included. Keyed by board: switching boards starts the pager over at that board's first busy column.
    key(board) {
        KanbanColumnPager(snapshot.columns, loading, onRefresh, onOpenTask, onMove)
    }
}

/** The column tabs above a pager with one page per column; the tabs and the pager share one pager state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.KanbanColumnPager(
    columns: List<KanbanColumn>,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpenTask: (String) -> Unit,
    onMove: (taskId: String, status: String) -> Unit
) {
    val columnNames = columns.map { it.name }
    val initialPage = remember { columns.indexOfFirst { it.tasks.isNotEmpty() }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialPage) { columns.size }
    val scope = rememberCoroutineScope()
    val tabsState = rememberLazyListState()
    val selected = pagerState.currentPage

    // Keeps the selected tab on screen, with its left neighbour visible when it is not the first tab.
    LaunchedEffect(selected) { tabsState.animateScrollToItem((selected - 1).coerceAtLeast(0)) }

    LazyRow(
        state = tabsState,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(columns, key = { _, column -> column.name }) { index, column ->
            KanbanColumnTab(column, selected = index == selected) {
                scope.launch { pagerState.animateScrollToPage(index) }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.hermes.line)

    PullToRefreshBox(isRefreshing = loading, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            KanbanColumnPage(columns[page], columnNames, onOpenTask, onMove)
        }
    }
}

@Composable
private fun KanbanColumnTab(column: KanbanColumn, selected: Boolean, onClick: () -> Unit) {
    val tone = kanbanTone(column.name)
    val labelColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        // Intrinsic width: a LazyRow item is unbounded, so the underline's fillMaxWidth needs the label row's width.
        Modifier
            .width(IntrinsicSize.Max)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(tone))
            Spacer(Modifier.width(6.dp))
            Text(
                kanbanStatusLabel(column.name).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                color = labelColor,
                maxLines = 1
            )
            Spacer(Modifier.width(6.dp))
            Text(
                column.tasks.size.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) tone else Color.Transparent)
        )
    }
}

/** One column's cards. Always a LazyColumn so the page stays pull-to-refresh-able even when it is empty. */
@Composable
private fun KanbanColumnPage(
    column: KanbanColumn,
    columnNames: List<String>,
    onOpenTask: (String) -> Unit,
    onMove: (taskId: String, status: String) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (column.tasks.isEmpty()) {
            item {
                Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.kanban_empty_column),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        items(column.tasks, key = { it.id }) { task ->
            KanbanTaskCard(task, columnNames, onClick = { onOpenTask(task.id) }, onMove = onMove)
        }
    }
}

/**
 * A card in Desktop's board: title, summary, and a footer of assignee, working time, block reason and counters.
 * A long press opens Desktop's card menu: Open, then Move to each column the card may go to.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KanbanTaskCard(
    task: KanbanTask,
    columnNames: List<String>,
    onClick: () -> Unit,
    onMove: (taskId: String, status: String) -> Unit
) {
    val tone = kanbanTone(task.status)
    val summary = task.latestSummary.ifBlank { task.body }.trim()
    val haptic = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth().clip(shape).combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuOpen = true
                }
            ),
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.hermes.line)
        ) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(tone))
                Column(Modifier.weight(1f).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (summary.isNotBlank()) {
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    KanbanCardFooter(task, tone)
                }
            }
        }
        val targets = kanbanMoveTargets(columnNames, task.status)
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.kanban_open)) },
                onClick = { menuOpen = false; onClick() },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)) }
            )
            if (targets.isNotEmpty()) {
                HorizontalDivider()
                targets.forEach { status ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.kanban_move_to, kanbanStatusLabel(status))) },
                        onClick = { menuOpen = false; onMove(task.id, status) },
                        leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(kanbanTone(status))) }
                    )
                }
            }
        }
    }
}

@Composable
private fun KanbanCardFooter(task: KanbanTask, tone: Color) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    Row(verticalAlignment = Alignment.CenterVertically) {
        // The left side takes the free width, so the counters on the right stay at the card's edge.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            task.assignee?.let { name ->
                Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KanbanAvatar(name)
                    Text(name, Modifier.weight(1f, fill = false), style = labelStyle, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (task.status == "running") {
                KanbanWorkingLabel(task.id, task.startedAt, tone, Modifier.weight(1f, fill = false))
            }
            val blockKind = task.blockKind
            if (task.status == "blocked" && blockKind != null) {
                Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Block, null, Modifier.size(12.dp), tint = tone)
                    Text(
                        blockKind.replace('_', ' '),
                        style = labelStyle,
                        color = tone,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KanbanCounters(task)
            Text(
                task.id.removePrefix("t_").take(6),
                style = labelStyle.copy(fontFamily = HermesMono),
                color = muted.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}

/** Priority, comment count and consecutive failures, each with its glyph; shared by the card and the sheet. */
@Composable
private fun KanbanCounters(task: KanbanTask) {
    val labelStyle = MaterialTheme.typography.labelSmall
    if (task.priority > 0) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(Icons.Default.ArrowUpward, null, Modifier.size(12.dp), tint = HermesTokens.KanbanReview)
            Text(task.priority.toString(), style = labelStyle, color = HermesTokens.KanbanReview)
        }
    }
    if (task.commentCount > 0) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(Icons.AutoMirrored.Filled.Comment, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(task.commentCount.toString(), style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (task.consecutiveFailures > 0) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(Icons.Default.Warning, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
            Text(task.consecutiveFailures.toString(), style = labelStyle, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** "Working · 3m" for a running card. The clock ticks every 5 s, and only while this label is on screen. */
@Composable
private fun KanbanWorkingLabel(taskId: String, startedAt: Long, tone: Color, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelSmall
    if (startedAt <= 0L) {
        Text(stringResource(R.string.kanban_status_running), modifier, style = style, color = tone, maxLines = 1)
        return
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(taskId) {
        while (true) {
            delay(5_000)
            now = System.currentTimeMillis()
        }
    }
    Text(
        stringResource(R.string.kanban_working, kanbanElapsed(startedAt, now)),
        modifier,
        style = style,
        color = tone,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** Compact elapsed time since [startedAtSeconds] (epoch seconds): Ns, Nm, Nh or Nd. */
private fun kanbanElapsed(startedAtSeconds: Long, nowMillis: Long): String {
    val seconds = (nowMillis / 1000L - startedAtSeconds).coerceAtLeast(0L)
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3_600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3_600}h"
        else -> "${seconds / 86_400}d"
    }
}

@Composable
private fun KanbanAvatar(name: String, size: Dp = 18.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.hermes.accentTint),
        contentAlignment = Alignment.Center
    ) {
        Text(
            kanbanInitials(name),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}

private fun kanbanInitials(name: String): String {
    val initials = name.split(Regex("[\\s_\\-./]+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
    return initials.ifEmpty { "?" }
}

/** The tone of a column (Desktop COLUMN_META): the accent of its tab, its card bar, and its pill. */
@Composable
private fun kanbanTone(status: String): Color = when (status) {
    "scheduled" -> HermesTokens.KanbanScheduled
    "ready" -> HermesTokens.KanbanReady
    "running" -> HermesTokens.KanbanRunning
    "blocked" -> HermesTokens.KanbanBlocked
    "review" -> HermesTokens.KanbanReview
    "todo" -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    "triage", "done" -> MaterialTheme.colorScheme.onSurfaceVariant
    "archived" -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
}

/** The columns a card can be moved to from its menu: board order, without its own column and the locked ones. */
private fun kanbanMoveTargets(columns: List<String>, current: String): List<String> =
    columns.filter { it != current && it !in KANBAN_LOCKED_COLUMNS }

@Composable
private fun kanbanStatusLabel(status: String): String = when (status) {
    "triage" -> stringResource(R.string.kanban_status_triage)
    "todo" -> stringResource(R.string.kanban_status_todo)
    "scheduled" -> stringResource(R.string.kanban_status_scheduled)
    "ready" -> stringResource(R.string.kanban_status_ready)
    "running" -> stringResource(R.string.kanban_status_running)
    "blocked" -> stringResource(R.string.kanban_status_blocked)
    "review" -> stringResource(R.string.kanban_status_review)
    "done" -> stringResource(R.string.kanban_status_done)
    "archived" -> stringResource(R.string.kanban_status_archived)
    else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/**
 * Desktop's StatusMenu button: a tone-tinted chip with a dot and the uppercased status. With [onClick] it also shows
 * the caret that opens the status menu.
 */
@Composable
private fun KanbanStatusPill(status: String, onClick: (() -> Unit)? = null) {
    val tone = kanbanTone(status)
    Surface(
        modifier = if (onClick != null) Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick) else Modifier,
        shape = RoundedCornerShape(4.dp),
        color = tone.copy(alpha = 0.15f)
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(tone))
            Spacer(Modifier.width(6.dp))
            Text(
                kanbanStatusLabel(status).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = tone
            )
            if (onClick != null) Icon(Icons.Default.ArrowDropDown, null, Modifier.size(14.dp), tint = tone)
        }
    }
}

/** The card's details, with the two actions Desktop offers on a card: unblock (blocked) and release (running). */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun KanbanTaskSheet(
    taskId: String,
    detail: KanbanTaskDetail?,
    busy: Boolean,
    columns: List<String>,
    onDismiss: () -> Unit,
    onUnblock: (String) -> Unit,
    onRelease: (String) -> Unit,
    onMove: (taskId: String, status: String) -> Unit
) {
    var confirmRelease by remember(taskId) { mutableStateOf(false) }
    var statusMenu by remember(taskId) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        if (detail == null) {
            HermesLoader(stringResource(R.string.kanban_loading))
            Spacer(Modifier.height(24.dp))
            return@ModalBottomSheet
        }
        val task = detail.task
        val unassigned = stringResource(R.string.kanban_unassigned)
        val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(task.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box {
                    KanbanStatusPill(task.status, onClick = if (busy) null else ({ statusMenu = true }))
                    DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                        // The current status stays in the list, checked; locked columns are offered only as the current one.
                        columns.filter { it == task.status || it !in KANBAN_LOCKED_COLUMNS }.forEach { status ->
                            val current = status == task.status
                            DropdownMenuItem(
                                text = { Text(kanbanStatusLabel(status)) },
                                onClick = { statusMenu = false; if (!current) onMove(task.id, status) },
                                leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(kanbanTone(status))) },
                                trailingIcon = if (current) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KanbanAvatar(task.assignee ?: unassigned)
                    Text(task.assignee ?: unassigned, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                KanbanCounters(task)
                Text(
                    task.id.removePrefix("t_").take(6),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = HermesMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            if (task.status == "running") {
                Spacer(Modifier.height(6.dp))
                KanbanWorkingLabel(task.id, task.startedAt, kanbanTone(task.status))
            }
            if (task.status == "blocked" && task.blockKind != null) {
                Spacer(Modifier.height(12.dp))
                KanbanNote(stringResource(R.string.kanban_block_reason), kanbanBlockTip(task.blockKind))
            }
            task.lastFailureError?.let {
                Spacer(Modifier.height(12.dp))
                KanbanNote(stringResource(R.string.kanban_last_failure), it)
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel(stringResource(R.string.kanban_description))
            Text(
                task.body.ifBlank { stringResource(R.string.kanban_no_description) },
                style = MaterialTheme.typography.bodyMedium,
                color = if (task.body.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            if (detail.result.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                SectionLabel(stringResource(R.string.kanban_result))
                Text(detail.result, style = MaterialTheme.typography.bodyMedium, maxLines = 12, overflow = TextOverflow.Ellipsis)
            }
            if (task.createdAt > 0L) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.kanban_created) + " · " + dateFormat.format(Date(task.createdAt * 1000L)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionLabel(stringResource(R.string.kanban_comments, detail.comments.size))
            if (detail.comments.isEmpty()) {
                Text(stringResource(R.string.kanban_no_comments), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else detail.comments.takeLast(5).forEach { comment ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(comment.author, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(comment.body, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
            }

            SectionLabel(stringResource(R.string.kanban_activity))
            if (detail.events.isEmpty()) {
                Text(stringResource(R.string.kanban_no_activity), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else detail.events.takeLast(8).asReversed().forEach { event ->
                Text(
                    event.kind.replace('_', ' ') + if (event.createdAt > 0L) " · " + dateFormat.format(Date(event.createdAt * 1000L)) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            Spacer(Modifier.height(18.dp))
            when (task.status) {
                "blocked" -> Button(onClick = { onUnblock(task.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.kanban_unblock))
                }
                "running" -> OutlinedButton(onClick = { confirmRelease = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.kanban_release))
                }
            }
        }
    }

    if (confirmRelease) {
        AlertDialog(
            onDismissRequest = { confirmRelease = false },
            title = { Text(stringResource(R.string.kanban_release_title)) },
            text = { Text(stringResource(R.string.kanban_release_body)) },
            confirmButton = {
                TextButton(onClick = { confirmRelease = false; onRelease(taskId) }) {
                    Text(stringResource(R.string.kanban_release), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRelease = false }) { Text(stringResource(R.string.a14_cancel)) } }
        )
    }
}

@Composable
private fun KanbanNote(title: String, body: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.hermes.accentTint,
        border = BorderStroke(1.dp, MaterialTheme.hermes.line),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun kanbanBlockTip(kind: String): String = when (kind) {
    "needs_input" -> stringResource(R.string.kanban_block_needs_input)
    "capability" -> stringResource(R.string.kanban_block_capability)
    "transient" -> stringResource(R.string.kanban_block_transient)
    else -> stringResource(R.string.kanban_block_dependency)
}

/**
 * Shown in place of the composer when a Kanban worker's transcript is open: the board owns that session, so the chat
 * only reads. [onOpenBoard] is null while Kanban is not available on the gateway, which hides the button.
 */
@Composable
internal fun KanbanReadOnlyBanner(onOpenBoard: (() -> Unit)?) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.hermes.accentTint,
        border = BorderStroke(1.dp, MaterialTheme.hermes.line)
    ) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ViewKanban, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.kanban_read_only_banner),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall
            )
            if (onOpenBoard != null) TextButton(onClick = onOpenBoard) { Text(stringResource(R.string.kanban_open_board)) }
        }
    }
}
