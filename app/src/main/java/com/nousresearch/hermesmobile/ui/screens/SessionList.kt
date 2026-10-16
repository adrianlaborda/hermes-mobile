package com.nousresearch.hermesmobile.ui.screens

import com.nousresearch.hermesmobile.data.LiveSessions
import com.nousresearch.hermesmobile.data.SessionDot
import com.nousresearch.hermesmobile.ui.components.SessionStatusDot
import com.nousresearch.hermesmobile.ui.components.runningArc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.data.*
import com.nousresearch.hermesmobile.ui.components.*
import com.nousresearch.hermesmobile.ui.theme.hermes
import kotlinx.coroutines.launch

/** Everything the conversation list needs; shared by the phone and two-pane layouts. */
internal class SessionListActions(
    val onSelectProfile: (String) -> Unit,
    val onIncludeArchived: (Boolean) -> Unit,
    val onSearch: (String) -> Unit,
    val onLoadMore: () -> Unit,
    val onNewChat: () -> Unit,
    val onOpen: (HermesSession) -> Unit,
    val onRename: (HermesSession, String) -> Unit,
    val onTogglePin: (HermesSession) -> Unit,
    val onToggleArchive: (HermesSession) -> Unit,
    val onToggleUnread: (HermesSession) -> Unit,
    val onDelete: (HermesSession) -> Unit,
    val onBranch: (HermesSession) -> Unit,
    val onMenu: () -> Unit
)

@Composable
internal fun SessionListPane(
    profiles: List<HermesProfile>,
    sessions: List<HermesSession>,
    sessionDots: Map<String, SessionDot>,
    searchResults: List<HermesSession>,
    searchQuery: String,
    searchLoading: Boolean,
    hasMore: Boolean,
    loadingMore: Boolean,
    includeArchived: Boolean,
    selectedProfile: String,
    activeKey: String?,
    actions: SessionListActions,
    listHeader: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var sheetTarget by remember { mutableStateOf<HermesSession?>(null) }
    var renameTarget by remember { mutableStateOf<HermesSession?>(null) }
    var deleteTarget by remember { mutableStateOf<HermesSession?>(null) }

    val source = if (searchQuery.isBlank()) sessions else searchResults
    val visible = remember(source, includeArchived, selectedProfile) {
        source.filter { !it.hidden && (includeArchived || !it.archived) && (selectedProfile == "all" || it.profile == selectedProfile) }
    }

    Column(modifier.fillMaxSize()) {
        HermesTopBar(
            stringResource(R.string.ui_ef5b4049bb),
            pluralStringResource(R.plurals.a22_conversations, visible.size, visible.size),
            leading = { IconButton(onClick = actions.onMenu) { Icon(Icons.Default.Menu, stringResource(R.string.a21_menu)) } },
            actions = {
                val filtersActive = includeArchived || selectedProfile != "all"
                IconButton(onClick = { showFilters = !showFilters }) {
                    BadgedBox(badge = { if (filtersActive) Badge() }) {
                        Icon(Icons.Default.Tune, stringResource(R.string.chat_filters),
                            tint = if (showFilters) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                    }
                }
                FilledTonalIconButton(onClick = actions.onNewChat) { Icon(Icons.Default.Edit, stringResource(R.string.ui_db18382a24)) }
            }
        )
        listHeader()
        SessionSearchBar(searchQuery, searchLoading, actions.onSearch)
        AnimatedVisibility(visible = showFilters) {
            Column {
                ProfileStrip(profiles, selectedProfile, actions.onSelectProfile)
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                    FilterChip(
                        selected = includeArchived,
                        onClick = { actions.onIncludeArchived(!includeArchived) },
                        label = { Text(stringResource(R.string.ui_bdb86505f8)) },
                        leadingIcon = { Icon(Icons.Default.Inventory2, null, Modifier.size(16.dp)) }
                    )
                }
            }
        }

        if (visible.isEmpty() && !searchLoading) {
            EmptyState(
                if (searchQuery.isBlank()) Icons.Default.ChatBubbleOutline else Icons.Default.SearchOff,
                stringResource(if (searchQuery.isBlank()) R.string.a22_no_conversations else R.string.a22_no_results),
                stringResource(if (searchQuery.isBlank()) R.string.a22_no_conversations_hint else R.string.a22_no_results_hint)
            ) {
                if (searchQuery.isBlank()) Button(onClick = actions.onNewChat) { Text(stringResource(R.string.ui_db18382a24)) }
            }
        } else {
            val sections = remember(visible) { groupSessions(visible) }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                sections.forEach { section ->
                    item(key = "section:${section.key}", contentType = "header") {
                        ChatSectionHeader(sessionSectionLabel(section), section.rows.size)
                    }
                    items(section.rows, key = { "${it.profile}:${it.id}" }, contentType = { "session" }) { session ->
                        SessionRow(
                            session = session,
                            dot = LiveSessions.dotFor(sessionDots, session.id, session.unread),
                            selected = activeKey == "${session.profile}:${session.id}",
                            showProfile = profiles.size > 1,
                            onOpen = { actions.onOpen(session) },
                            onMore = { sheetTarget = session },
                            onTogglePin = { actions.onTogglePin(session) },
                            onToggleArchive = { actions.onToggleArchive(session) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
                if (searchQuery.isBlank() && hasMore) {
                    item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            if (loadingMore) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            else TextButton(onClick = actions.onLoadMore) { Text(stringResource(R.string.ui_ac8991ef01)) }
                        }
                    }
                }
            }
        }
    }

    sheetTarget?.let { target ->
        SessionActionsSheet(
            session = target,
            onDismiss = { sheetTarget = null },
            onTogglePin = { actions.onTogglePin(target) },
            onToggleUnread = { actions.onToggleUnread(target) },
            onRename = { renameTarget = target },
            onBranch = { actions.onBranch(target) },
            onToggleArchive = { actions.onToggleArchive(target) },
            onDelete = { deleteTarget = target }
        )
    }
    renameTarget?.let { target ->
        SessionRenameDialog(target, onDismiss = { renameTarget = null }) { title ->
            renameTarget = null
            actions.onRename(target, title)
        }
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.ui_44801fe9f6)) },
            text = { Text(target.title.ifBlank { stringResource(R.string.a22_this_conversation) }) },
            confirmButton = {
                TextButton(
                    onClick = { deleteTarget = null; actions.onDelete(target) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.ui_e2d0a54968)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.ui_19766ed6cc)) } }
        )
    }
}

@Composable
private fun sessionSectionLabel(section: SessionSection): String = section.channel ?: stringResource(when (section.bucket) {
    SessionBucket.PINNED -> R.string.a21_pinned
    SessionBucket.TODAY -> R.string.a21_today
    SessionBucket.YESTERDAY -> R.string.a21_yesterday
    SessionBucket.WEEK -> R.string.a21_week
    else -> R.string.a21_older
})

@Composable
private fun ChatSectionHeader(label: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        DashedDivider(Modifier.weight(1f), horizontalPadding = 0.dp)
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SessionSourceAvatar(rawSource: String, size: Int = 38) {
    val source = sessionSourceInfo(rawSource)
    val icon = when (source.id) {
        "mobile" -> Icons.Default.PhoneAndroid
        "desktop" -> Icons.Default.DesktopWindows
        "telegram" -> Icons.AutoMirrored.Filled.Send
        "discord", "slack", "mattermost", "matrix" -> Icons.Default.Forum
        "whatsapp", "signal", "sms", "bluebubbles", "photon" -> Icons.AutoMirrored.Filled.Message
        "email" -> Icons.Default.Email
        "webhook", "api_server" -> Icons.Default.Api
        "cli", "tui" -> Icons.Default.Terminal
        "codex" -> Icons.Default.Code
        "homeassistant" -> Icons.Default.Home
        else -> when (source.kind) {
            SessionSourceKind.HERMES_APP -> Icons.Default.AutoAwesome
            SessionSourceKind.CHANNEL -> Icons.Default.Hub
            SessionSourceKind.CLIENT -> Icons.Default.Devices
            SessionSourceKind.UNKNOWN -> Icons.Default.ChatBubbleOutline
        }
    }
    val (bg, fg) = when (source.kind) {
        SessionSourceKind.HERMES_APP -> MaterialTheme.hermes.accentTint to MaterialTheme.colorScheme.primary
        SessionSourceKind.CHANNEL -> MaterialTheme.hermes.greenTint to MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        Modifier.size(size.dp).background(bg, RoundedCornerShape((size * .3f).dp)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, source.label, Modifier.size((size * .5f).dp), tint = fg) }
}

@Composable
private fun SessionSearchBar(query: String, loading: Boolean, onSearch: (String) -> Unit) =
    HermesSearchField(query, onSearch, stringResource(R.string.ui_8abdf3b226), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), loading)

/**
 * Conversation row. Swipe right pins, swipe left archives, long-press opens the full
 * action sheet (TalkBack gets the same actions as custom accessibility actions).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionRow(
    session: HermesSession,
    dot: SessionDot,
    selected: Boolean,
    showProfile: Boolean,
    onOpen: () -> Unit,
    onMore: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // A fresh state per pinned/archived value: once the action lands, the row comes back
    // settled in its new section instead of staying dismissed.
    val state = key(session.pinned, session.archived) { rememberSwipeToDismissBoxState() }
    val pinLabel = stringResource(if (session.pinned) R.string.a22_unpin else R.string.a22_pin)
    val archiveLabel = stringResource(if (session.archived) R.string.a22_unarchive else R.string.a22_archive)
    val moreLabel = stringResource(R.string.a22_conversation_actions)
    SwipeToDismissBox(
        state = state,
        modifier = modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(pinLabel) { onTogglePin(); true },
                CustomAccessibilityAction(archiveLabel) { onToggleArchive(); true },
                CustomAccessibilityAction(moreLabel) { onMore(); true }
            )
        },
        onDismiss = { value ->
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onTogglePin()
                SwipeToDismissBoxValue.EndToStart -> onToggleArchive()
                SwipeToDismissBoxValue.Settled -> {}
            }
            // If the action fails (flag unchanged), still bring the row back.
            scope.launch { state.reset() }
        },
        backgroundContent = {
            val direction = state.dismissDirection
            val start = direction == SwipeToDismissBoxValue.StartToEnd
            val active = direction != SwipeToDismissBoxValue.Settled
            val bg by animateColorAsState(
                when {
                    !active -> Color.Transparent
                    start -> MaterialTheme.hermes.accentTint
                    else -> MaterialTheme.hermes.orangeTint
                }, label = "swipe"
            )
            Row(
                Modifier.fillMaxSize().background(bg).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (start) Arrangement.Start else Arrangement.End
            ) {
                if (active) {
                    Icon(if (start) Icons.Default.PushPin else Icons.Default.Inventory2, null,
                        tint = if (start) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(8.dp))
                    Text(if (start) pinLabel else archiveLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    ) {
        Surface(color = if (selected) MaterialTheme.hermes.accentTint else MaterialTheme.colorScheme.background,
            modifier = Modifier.runningArc(dot == SessionDot.WORKING)) {
            FlatRow(
                onClick = onOpen,
                onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onMore() },
                leading = { SessionSourceAvatar(session.source) },
                trailing = {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            relativeDate(session.lastActive),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (session.pinned) Icon(Icons.Default.PushPin, stringResource(R.string.a22_pinned_cd), Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            ) {
                // Desktop's lead slot: the status dot sits before the title.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (dot != SessionDot.NONE) {
                        SessionStatusDot(dot)
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(
                        session.title.ifBlank { stringResource(R.string.a21_untitled) },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (dot != SessionDot.NONE) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showProfile) {
                        SessionTag(session.profile)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        sessionMeta(session),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun sessionMeta(session: HermesSession): String = listOfNotNull(
    session.preview.takeIf { it.isNotBlank() }?.replace('\n', ' '),
    session.model.substringAfterLast('/').takeIf { it.isNotBlank() && session.preview.isBlank() },
    pluralStringResource(R.plurals.a22_messages, session.messageCount, session.messageCount).takeIf { session.preview.isBlank() }
).joinToString(" · ")

/** Tiny tinted tag used on session rows (profile labels). */
@Composable
private fun SessionTag(text: String) {
    Surface(color = MaterialTheme.hermes.accentTint, contentColor = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(5.dp)) {
        Text(text, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionActionsSheet(
    session: HermesSession,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleUnread: () -> Unit,
    onRename: () -> Unit,
    onBranch: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SessionSourceAvatar(session.source, size = 34)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(session.title.ifBlank { stringResource(R.string.a21_untitled) }, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOf(sessionSourceInfo(session.source).label, session.profile, relativeDate(session.lastActive))
                    .filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.hermes.line)
        fun act(block: () -> Unit): () -> Unit = { onDismiss(); block() }
        SheetAction(Icons.Default.PushPin, stringResource(if (session.pinned) R.string.a22_unpin else R.string.a22_pin), act(onTogglePin))
        SheetAction(if (session.unread) Icons.Default.MarkEmailRead else Icons.Default.MarkEmailUnread,
            stringResource(if (session.unread) R.string.a22_mark_read else R.string.a22_mark_unread), act(onToggleUnread))
        SheetAction(Icons.Default.Edit, stringResource(R.string.ui_3064d79a29), act(onRename))
        SheetAction(Icons.AutoMirrored.Filled.CallSplit, stringResource(R.string.ui_52656e8104), act(onBranch))
        SheetAction(Icons.Default.Inventory2, stringResource(if (session.archived) R.string.a22_unarchive else R.string.a22_archive), act(onToggleArchive))
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.hermes.line)
        SheetAction(Icons.Default.DeleteOutline, stringResource(R.string.ui_e2d0a54968), act(onDelete), destructive = true)
        Spacer(Modifier.height(16.dp))
    }
}

/** 52dp action row used by the bottom-sheet menus. */
@Composable
internal fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit, destructive: Boolean = false, enabled: Boolean = true) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (destructive) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f))
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
private fun ProfileStrip(profiles: List<HermesProfile>, selected: String, onSelect: (String) -> Unit) {
    if (profiles.size <= 1) return
    androidx.compose.foundation.lazy.LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item { FilterChip(selected == "all", { onSelect("all") }, { Text(stringResource(R.string.ui_a52ace420f)) }) }
        items(profiles, key = { it.name }) { profile ->
            FilterChip(selected == profile.name, { onSelect(profile.name) }, { Text(profile.label) })
        }
    }
}

@Composable
internal fun NewChatDialog(
    profiles: List<HermesProfile>,
    current: String,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf(current.takeIf { it != "all" } ?: profiles.firstOrNull()?.name ?: "default") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_db18382a24)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HermesTextField(title, { title = it }, stringResource(R.string.a22_title_optional))
                Text(stringResource(R.string.ui_d696a35bdd), style = MaterialTheme.typography.labelMedium)
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    profiles.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { profile = item.name }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(profile == item.name, { profile = item.name })
                            Text(item.label)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(profile, title) }) { Text(stringResource(R.string.ui_4759498ac2)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_19766ed6cc)) } }
    )
}

@Composable
private fun SessionRenameDialog(session: HermesSession, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var title by remember(session.id, session.profile) { mutableStateOf(session.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_4f10b6dc1d)) },
        text = { HermesTextField(value = title, onValueChange = { title = it }, label = stringResource(R.string.a22_title)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }, enabled = title.isNotBlank() && title.trim() != session.title) {
                Text(stringResource(R.string.ui_1509f561f2))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_19766ed6cc)) } }
    )
}

/** Locale-aware short date: time today, "Yesterday", else a short month-day. */
@Composable
internal fun relativeDate(ts: Long): String {
    val context = LocalContext.current
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    if (ts <= 0L) return ""
    val yesterday = stringResource(R.string.a21_yesterday)
    val millis = if (ts < 10_000_000_000L) ts * 1000L else ts
    val date = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val today = java.time.LocalDate.now()
    return when (date) {
        today -> android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(millis))
        today.minusDays(1) -> yesterday
        else -> {
            val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale,
                if (date.year == today.year) "MMMd" else "yMMMd")
            java.text.SimpleDateFormat(pattern, locale).format(java.util.Date(millis))
        }
    }
}
