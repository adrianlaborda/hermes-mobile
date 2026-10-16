@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nousresearch.hermesmobile.ui.screens

import com.nousresearch.hermesmobile.R
import androidx.compose.ui.res.stringResource

import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nousresearch.hermesmobile.data.BotProfileConfig
import com.nousresearch.hermesmobile.data.*
import com.nousresearch.hermesmobile.ui.components.*
import com.nousresearch.hermesmobile.ui.theme.hermes

@Composable
fun BotsScreen(
    onMenu: () -> Unit,
    profiles: List<HermesProfile>,
    routedBots: List<RoutedBot>,
    botRosterLoading: Boolean,
    editor: BotProfileConfig?,
    editorLoadingProfile: String?,
    editorSaving: Boolean,
    groupCapabilities: GroupCapabilities?,
    groupRooms: List<GroupRoom>,
    activeGroup: ActiveGroupRoom?,
    groupsLoading: Boolean,
    groupActionBusy: Boolean,
    groupError: String?,
    avatarAssets: Map<String, String>,
    sessionDots: Map<String, com.nousresearch.hermesmobile.data.SessionDot>,
    onEnsureAvatar: (HermesProfile) -> Unit,
    onSwitchConnection: (String) -> Unit,
    onOpen: (HermesProfile) -> Unit,
    onCreate: (String, String, String, String?) -> Unit,
    onNewChat: (HermesProfile) -> Unit,
    onEdit: (HermesProfile) -> Unit,
    onSaveEditor: (BotProfileConfig) -> Unit,
    onCloseEditor: () -> Unit,
    onDelete: (HermesProfile) -> Unit,
    onRefreshGroups: () -> Unit,
    onCreateGroup: (String, Set<String>) -> Unit,
    onOpenGroup: (GroupRoom) -> Unit,
    onCloseGroup: () -> Unit,
    onSendGroup: (String) -> Unit,
    onReplyGroupThread: (String?) -> Unit,
    onRenameGroup: (String) -> Unit,
    onStopGroup: () -> Unit,
    onRetryGroup: (GroupPendingAction) -> Unit,
    onApproveGroup: (GroupPendingAction, String) -> Unit,
    onDisbandGroup: () -> Unit
) {
    var page by remember { mutableIntStateOf(0) }
    var create by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var showHidden by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<HermesProfile?>(null) }
    val allBots = remember(routedBots) { routedBots.filter { it.profile.isBot } }
    val hiddenCount = remember(allBots) { allBots.count { it.profile.botHidden } }
    val bots = remember(allBots, showHidden, search) {
        allBots.filter { (showHidden || !it.profile.botHidden) && (it.label.contains(search, true) || it.profile.name.contains(search, true)) }
            .sortedWith(compareByDescending<RoutedBot> { it.profile.botPinned }.thenBy { it.connectionLabel.lowercase() }.thenBy { it.label.lowercase() })
    }

    if (activeGroup != null) {
        GroupsPanel(
            profiles, routedBots, groupCapabilities, groupRooms, activeGroup, groupsLoading, groupActionBusy, groupError,
            avatarAssets, onEnsureAvatar, onRefreshGroups, onCreateGroup, onOpenGroup, onCloseGroup,
            onSendGroup, onReplyGroupThread, onRenameGroup, onStopGroup, onRetryGroup, onApproveGroup, onDisbandGroup
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        HermesTopBar(
            stringResource(R.string.a14_agents),
            stringResource(if (page == 0) R.string.a14_agents_hint else R.string.a14_groups_hint),
            leading = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, stringResource(R.string.a21_menu)) } },
            actions = {
                if (page == 0 && hiddenCount > 0) {
                    IconButton(onClick = { showHidden = !showHidden }) {
                        Icon(
                            if (showHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            if (showHidden) "Hide hidden bots" else "Show hidden bots"
                        )
                    }
                }
                if (page == 0) IconButton(onClick = { create = true }) { Icon(Icons.Default.Add, stringResource(R.string.ui_b23572a604)) }
            }
        )
        DashedDivider(color = MaterialTheme.hermes.line)
        SecondaryTabRow(selectedTabIndex = page) {
            Tab(selected = page == 0, onClick = { page = 0 }, text = { Text("Bots (${allBots.count { !it.profile.botHidden }})") })
            Tab(selected = page == 1, onClick = { page = 1; onRefreshGroups() }, text = { Text("Groups (${groupRooms.size})") })
        }
        if (page == 0) com.nousresearch.hermesmobile.ui.components.HermesSearchField(search, { search = it }, stringResource(R.string.a14_search), Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        if (botRosterLoading && page == 0) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (page == 1) {
            GroupsPanel(
                profiles, routedBots, groupCapabilities, groupRooms, null, groupsLoading, groupActionBusy, groupError,
                avatarAssets, onEnsureAvatar, onRefreshGroups, onCreateGroup, onOpenGroup, onCloseGroup,
                onSendGroup, onReplyGroupThread, onRenameGroup, onStopGroup, onRetryGroup, onApproveGroup, onDisbandGroup
            )
        } else if (bots.isEmpty()) {
            EmptyState(Icons.Default.SmartToy, "No visible bots", "Create a profile-backed Hermes bot.") {
                Button(onClick = { create = true }) { Text(stringResource(R.string.ui_b23572a604)) }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(bots, key = { it.routeKey }) { routed ->
                    val profile = routed.profile
                    if (routed.activeConnection) {
                        LaunchedEffect(profile.name, profile.hasAvatar) { onEnsureAvatar(profile) }
                    }
                    BotRow(
                        routed = routed,
                        avatarDataUrl = avatarAssets[profile.name].takeIf { routed.activeConnection },
                        dot = if (routed.activeConnection) com.nousresearch.hermesmobile.data.LiveSessions.dotFor(sessionDots, profile.canonicalSession)
                            else com.nousresearch.hermesmobile.data.SessionDot.NONE,
                        onSwitchConnection = { onSwitchConnection(routed.connectionId) },
                        onOpen = { onOpen(profile) },
                        onNewChat = { onNewChat(profile) },
                        onEdit = { onEdit(profile) },
                        onDelete = { deleteCandidate = profile }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = .22f)
                    )
                }
            }
        }
    }

    if (create) {
        CreateBotDialog(
            profiles = profiles,
            onDismiss = { create = false },
            onCreate = { name, title, description, clone ->
                create = false
                onCreate(name, title, description, clone)
            }
        )
    }

    editor?.let {
        BotEditorDialog(
            config = it,
            saving = editorSaving,
            onDismiss = onCloseEditor,
            onSave = onSaveEditor
        )
    }

    if (editorLoadingProfile != null) {
        Dialog(onDismissRequest = onCloseEditor) {
            Surface(shape = MaterialTheme.shapes.large) {
                Row(
                    Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(14.dp))
                    Text("Loading $editorLoadingProfile configuration…")
                }
            }
        }
    }

    deleteCandidate?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete ${profile.label}?") },
            text = {
                Text(
                    "This removes the Hermes profile and its stored Bot Chat from the gateway. " +
                        "This action is not offered for the default profile."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !profile.isDefault,
                    onClick = {
                        deleteCandidate = null
                        onDelete(profile)
                    }
                ) { Text(stringResource(R.string.ui_e2d0a54968)) }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text(stringResource(R.string.ui_19766ed6cc)) } }
        )
    }
}

@Composable
private fun BotRow(
    routed: RoutedBot,
    avatarDataUrl: String?,
    dot: com.nousresearch.hermesmobile.data.SessionDot,
    onSwitchConnection: () -> Unit,
    onOpen: () -> Unit,
    onNewChat: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val profile = routed.profile
    // Like a chat row: the whole row opens the bot (or switches to its gateway); a long press shows its actions.
    Row(
        Modifier.fillMaxWidth()
            .runningArc(dot == com.nousresearch.hermesmobile.data.SessionDot.WORKING)
            .combinedClickable(onClick = if (routed.activeConnection) onOpen else onSwitchConnection, onLongClick = { menu = true })
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileAvatar(profile, size = 40, avatarDataUrl = avatarDataUrl)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dot != com.nousresearch.hermesmobile.data.SessionDot.NONE) {
                    com.nousresearch.hermesmobile.ui.components.SessionStatusDot(dot)
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    profile.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (profile.botPinned) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.PushPin, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (profile.botHidden) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.VisibilityOff, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                profile.description.ifBlank { profile.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (routed.activeConnection) "${routed.connectionLabel} · this gateway" else routed.connectionLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!routed.activeConnection) Icon(Icons.Default.Cloud, "Remote gateway", Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        StateDot(routed.reachable && profile.gatewayRunning)
        Spacer(Modifier.width(3.dp))
        // A bot on another gateway says what a tap does: it switches the app to that gateway.
        if (!routed.activeConnection) {
            TextButton(onClick = onSwitchConnection, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Switch") }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Bot actions") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (!routed.activeConnection) {
                    DropdownMenuItem(
                        text = { Text("Switch to ${routed.connectionLabel}") },
                        leadingIcon = { Icon(Icons.Default.Dns, null) },
                        onClick = { menu = false; onSwitchConnection() }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ui_885b06da76)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, null) },
                        onClick = { menu = false; onOpen() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ui_b13f96dc48)) },
                        leadingIcon = { Icon(Icons.Default.AddComment, null) },
                        onClick = { menu = false; onNewChat() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ui_c0cd1fd055)) },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menu = false; onEdit() }
                    )
                    if (!profile.isDefault) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_f53f5a6eb1)) },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                            onClick = { menu = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateBotDialog(
    profiles: List<HermesProfile>,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var clone by remember { mutableStateOf<String?>("default") }
    val slug = name.trim().lowercase().replace(Regex("[^a-z0-9_-]+"), "-").trim('-')
    val valid = Regex("^[a-z0-9][a-z0-9_-]{0,63}$").matches(slug) && profiles.none { it.name == slug }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_b23572a604)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HermesTextField(name, { name = it }, "Profile name", placeholder = "researcher")
                if (name.isNotBlank() && !valid) {
                    Text(
                        if (profiles.any { it.name == slug }) "That profile already exists." else "Use 1–64 lowercase letters, numbers, _ or -.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                HermesTextField(title, { title = it }, "Display name", placeholder = "Researcher")
                HermesTextField(description, { description = it }, "Description", singleLine = false)
                Text(stringResource(R.string.ui_6e31e77621), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(clone == null, { clone = null })
                    Text(stringResource(R.string.ui_e0367b3cc8))
                }
                profiles.take(8).forEach { profile ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(clone == profile.name, { clone = profile.name })
                        Text(profile.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onCreate(slug, title.trim(), description.trim(), clone) }) {
                Text(stringResource(R.string.ui_4759498ac2))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_19766ed6cc)) } }
    )
}
