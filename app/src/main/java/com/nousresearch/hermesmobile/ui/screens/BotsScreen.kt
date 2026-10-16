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
            .sortedWith(botRosterOrder)
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
                            stringResource(if (showHidden) R.string.i18n_bots_hide_hidden else R.string.i18n_bots_show_hidden)
                        )
                    }
                }
                if (page == 0) IconButton(onClick = { create = true }) { Icon(Icons.Default.Add, stringResource(R.string.ui_b23572a604)) }
            }
        )
        DashedDivider(color = MaterialTheme.hermes.line)
        SecondaryTabRow(selectedTabIndex = page) {
            Tab(selected = page == 0, onClick = { page = 0 }, text = { Text(stringResource(R.string.i18n_bots_tab, allBots.count { !it.profile.botHidden })) })
            Tab(selected = page == 1, onClick = { page = 1; onRefreshGroups() }, text = { Text(stringResource(R.string.i18n_groups_tab, groupRooms.size)) })
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
            EmptyState(Icons.Default.SmartToy, stringResource(R.string.i18n_bots_empty), stringResource(R.string.i18n_bots_empty_hint)) {
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
                    Text(stringResource(R.string.i18n_bots_loading_config, editorLoadingProfile))
                }
            }
        }
    }

    deleteCandidate?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.i18n_delete_named, profile.label)) },
            text = {
                Text(
                    stringResource(R.string.i18n_bots_delete_body)
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
            // Like Desktop's roster: the newest message of the bot's conversation; its role until there is one.
            Text(
                previewLine(profile.activityPreview).ifBlank { profile.description.ifBlank { profile.name } },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val offline = if (routed.reachable && profile.gatewayRunning) "" else " · " + stringResource(R.string.bot_offline)
            Text(
                (if (routed.activeConnection) stringResource(R.string.i18n_bots_this_gateway, routed.connectionLabel) else routed.connectionLabel) + offline,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Only a problem is shown: green here would read as the status dot's "finished, unread".
        if (!(routed.reachable && profile.gatewayRunning)) {
            Icon(Icons.Default.CloudOff, stringResource(R.string.bot_offline), Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (!routed.activeConnection) {
            Icon(Icons.Default.Cloud, stringResource(R.string.i18n_remote_gateway), Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(3.dp))
        // A bot on another gateway says what a tap does: it switches the app to that gateway.
        if (!routed.activeConnection) {
            TextButton(onClick = onSwitchConnection, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(stringResource(R.string.i18n_bots_switch)) }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.i18n_bots_actions)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (!routed.activeConnection) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.i18n_bots_switch_to, routed.connectionLabel)) },
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
                HermesTextField(name, { name = it }, stringResource(R.string.i18n_prof_name), placeholder = "researcher")
                if (name.isNotBlank() && !valid) {
                    Text(
                        stringResource(if (profiles.any { it.name == slug }) R.string.i18n_bots_exists else R.string.i18n_bots_name_rule),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                HermesTextField(title, { title = it }, stringResource(R.string.i18n_display_name), placeholder = "Researcher")
                HermesTextField(description, { description = it }, stringResource(R.string.i18n_description), singleLine = false)
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
