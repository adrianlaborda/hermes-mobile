package com.nousresearch.hermesmobile.ui.screens

import com.nousresearch.hermesmobile.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nousresearch.hermesmobile.data.*
import com.nousresearch.hermesmobile.ui.components.EmptyState
import com.nousresearch.hermesmobile.ui.components.HermesTextField
import com.nousresearch.hermesmobile.ui.components.HermesTopBar
import com.nousresearch.hermesmobile.ui.components.DashedDivider
import com.nousresearch.hermesmobile.ui.components.ProfileAvatar
import com.nousresearch.hermesmobile.ui.theme.hermes

@Composable
fun GroupsPanel(
    profiles: List<HermesProfile>,
    routedBots: List<RoutedBot>,
    capabilities: GroupCapabilities?,
    rooms: List<GroupRoom>,
    active: ActiveGroupRoom?,
    loading: Boolean,
    actionBusy: Boolean,
    error: String?,
    avatarAssets: Map<String, String>,
    onEnsureAvatar: (HermesProfile) -> Unit,
    onRefresh: () -> Unit,
    onCreate: (String, Set<String>) -> Unit,
    onOpen: (GroupRoom) -> Unit,
    onClose: () -> Unit,
    onSend: (String) -> Unit,
    onReplyThread: (String?) -> Unit,
    onRename: (String) -> Unit,
    onStop: () -> Unit,
    onRetry: (GroupPendingAction) -> Unit,
    onApprove: (GroupPendingAction, String) -> Unit,
    onDisband: () -> Unit
) {
    if (active != null) {
        GroupRoomView(
            active = active,
            profiles = profiles,
            actionBusy = actionBusy,
            avatarAssets = avatarAssets,
            onEnsureAvatar = onEnsureAvatar,
            onBack = onClose,
            onSend = onSend,
            onReplyThread = onReplyThread,
            onRename = onRename,
            onStop = onStop,
            onRetry = onRetry,
            onApprove = onApprove,
            onDisband = onDisband
        )
        return
    }

    var create by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                val subtitle = when {
                    capabilities == null -> "Hosted Group Chats on this gateway"
                    capabilities.driver && capabilities.roomLink.enabled -> "Gateway-hosted · RoomLink ready for multi-gateway Bots"
                    capabilities.driver && capabilities.persistentProcess -> "Gateway-hosted · persistent driver"
                    capabilities.driver -> "Gateway-hosted · room driver active"
                    else -> "Gateway supports rooms, but its driver is unavailable"
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!error.isNullOrBlank()) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Default.Refresh, "Refresh groups") }
            IconButton(
                onClick = { create = true },
                enabled = capabilities?.driver == true && capabilities.methods.contains("groups.create") && routedBots.size >= 2
            ) { Icon(Icons.Default.GroupAdd, stringResource(R.string.ui_7888b00075)) }
        }
        DashedDivider(color = MaterialTheme.hermes.line)
        when {
            loading && rooms.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            capabilities == null && !error.isNullOrBlank() -> EmptyState(
                Icons.Default.Groups,
                "Group Chats unavailable",
                "Update/start a Hermes gateway that exposes the hosted groups.* protocol."
            ) { TextButton(onClick = onRefresh) { Text(stringResource(R.string.ui_942087cc2d)) } }
            rooms.isEmpty() -> EmptyState(
                Icons.Default.Groups,
                "No Group Chats",
                "Create a room with 2–6 Bots. The gateway owns its durable log and turn scheduling."
            ) { Button(onClick = { create = true }, enabled = capabilities?.driver == true && routedBots.size >= 2) { Text(stringResource(R.string.ui_7888b00075)) } }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(rooms, key = { it.roomId }) { room ->
                    GroupRoomRow(room, onOpen = { onOpen(room) })
                    HorizontalDivider(
                        Modifier.padding(start = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = .2f)
                    )
                }
            }
        }
    }

    if (create) {
        CreateGroupDialog(
            bots = routedBots.filter { !it.profile.botHidden },
            busy = actionBusy,
            allowRemote = capabilities?.roomLink?.enabled == true,
            onDismiss = { create = false },
            onCreate = { name, members ->
                create = false
                onCreate(name, members)
            }
        )
    }
}

@Composable
private fun GroupRoomRow(room: GroupRoom, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(room.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                room.members.joinToString(" · ") { if (it.targetKind == "peer") "☁ @${it.handle}" else "@${it.handle}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text("${room.members.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = onOpen) { Icon(Icons.Default.ChevronRight, stringResource(R.string.ui_ed077f3d81)) }
    }
}

@Composable
private fun CreateGroupDialog(
    bots: List<RoutedBot>,
    busy: Boolean,
    allowRemote: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, Set<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val atCap = selected.size >= 6
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.ui_7888b00075)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HermesTextField(name, { name = it }, "Room name", placeholder = "Project review")
                Text(stringResource(R.string.ui_2745ea8b0f), style = MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(bots, key = { it.routeKey }) { bot ->
                        val profile = bot.profile
                        val checked = bot.routeKey in selected
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val routeEnabled = bot.activeConnection || allowRemote
                            Checkbox(
                                checked = checked,
                                enabled = routeEnabled && (checked || !atCap),
                                onCheckedChange = { value ->
                                    selected = if (value) selected + bot.routeKey else selected - bot.routeKey
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(profile.label)
                                Text(
                                    "@${bot.handle} · ${bot.connectionLabel}${if (bot.activeConnection) " · this gateway" else if (!allowRemote) " · RoomLink unavailable" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!bot.activeConnection) Icon(Icons.Default.Cloud, "Remote gateway", Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    "Bots may come from different saved gateways. Hermes RoomLink negotiates a scoped grant for each remote member; the room remains hosted by the active gateway.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && name.trim().isNotBlank() && selected.size in 2..6,
                onClick = { onCreate(name.trim(), selected) }
            ) { Text(if (busy) "Creating…" else "Create") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.ui_19766ed6cc)) } }
    )
}

@Composable
private fun GroupRoomView(
    active: ActiveGroupRoom,
    profiles: List<HermesProfile>,
    actionBusy: Boolean,
    avatarAssets: Map<String, String>,
    onEnsureAvatar: (HermesProfile) -> Unit,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onReplyThread: (String?) -> Unit,
    onRename: (String) -> Unit,
    onStop: () -> Unit,
    onRetry: (GroupPendingAction) -> Unit,
    onApprove: (GroupPendingAction, String) -> Unit,
    onDisband: () -> Unit
) {
    var input by remember(active.room.roomId) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var disband by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val visibleEvents = remember(active.events) { active.events.filter { shouldRenderRoomEvent(it) } }
    val membersById = remember(active.room.members) { active.room.members.associateBy { it.memberId } }
    val profilesByName = remember(profiles) { profiles.associateBy { it.name } }

    LaunchedEffect(visibleEvents.lastOrNull()?.seq) {
        if (visibleEvents.isNotEmpty()) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: Int.MAX_VALUE
            if (lastVisible >= visibleEvents.lastIndex - 2 || listState.layoutInfo.totalItemsCount == 0) {
                listState.scrollToItem(visibleEvents.lastIndex)
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        HermesTopBar(
            active.room.name,
            buildString {
                append(active.room.members.size).append(" Bots")
                when {
                    active.driver.working -> append(" · working")
                    active.driver.blocked -> append(" · needs attention")
                    active.driver.running -> append(" · driver ready")
                }
            },
            leading = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                if (active.driver.working) IconButton(onClick = onStop, enabled = !actionBusy) { Icon(Icons.Default.StopCircle, "Stop group work") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Group actions") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_3064d79a29)) },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menu = false; rename = true }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_5a7525fd26)) },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                            onClick = { menu = false; disband = true }
                        )
                    }
                }
            }
        )
        DashedDivider(color = MaterialTheme.hermes.line)

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            active.room.members.forEach { member ->
                val profile = profilesByName[member.profile]
                if (profile != null) LaunchedEffect(profile.name, profile.hasAvatar) { onEnsureAvatar(profile) }
                AssistChip(
                    onClick = { input = appendMention(input, member.handle) },
                    label = { Text("@${member.handle}", maxLines = 1) },
                    leadingIcon = if (profile != null) {
                        { ProfileAvatar(profile, size = 22, avatarDataUrl = avatarAssets[profile.name]) }
                    } else null
                )
            }
        }

        if (active.driver.pendingActions.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                active.driver.pendingActions.forEach { action ->
                    PendingRoomActionCard(action, membersById[action.memberId], actionBusy, onRetry, onApprove)
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        if (visibleEvents.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.ui_ea79140946), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleEvents, key = { it.seq }) { event ->
                    RoomEventRow(
                        event = event,
                        member = membersById[event.memberId],
                        onReplyThread = { event.threadId.takeIf(String::isNotBlank)?.let(onReplyThread) },
                        onReplyTo = { handle -> input = appendMention(input, handle) }
                    )
                }
            }
        }

        DashedDivider(color = MaterialTheme.hermes.line)
        active.replyThreadId?.let { thread ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Forum, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text("Replying in thread $thread", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { onReplyThread(null) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, "Cancel thread", Modifier.size(16.dp)) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 4,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                placeholder = { Text(stringResource(R.string.ui_5413c3c10b)) }
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                enabled = input.trim().isNotBlank() && !actionBusy,
                onClick = { val value = input.trim(); input = ""; onSend(value) }
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
        }
    }

    if (rename) {
        var value by remember { mutableStateOf(active.room.name) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text(stringResource(R.string.ui_14fbfb5329)) },
            text = { HermesTextField(value, { value = it }, "Name") },
            confirmButton = { TextButton(enabled = value.trim().isNotBlank() && !actionBusy, onClick = { rename = false; onRename(value) }) { Text(stringResource(R.string.ui_3064d79a29)) } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text(stringResource(R.string.ui_19766ed6cc)) } }
        )
    }
    if (disband) {
        AlertDialog(
            onDismissRequest = { disband = false },
            title = { Text("Disband ${active.room.name}?") },
            text = { Text(stringResource(R.string.ui_71f2b5e341)) },
            confirmButton = { TextButton(enabled = !actionBusy, onClick = { disband = false; onDisband() }) { Text(stringResource(R.string.ui_f2a9142052)) } },
            dismissButton = { TextButton(onClick = { disband = false }) { Text(stringResource(R.string.ui_19766ed6cc)) } }
        )
    }
}

@Composable
private fun RoomEventRow(
    event: GroupEvent,
    member: GroupMember?,
    onReplyThread: () -> Unit,
    onReplyTo: (String) -> Unit
) {
    if (event.visibleMessage) {
        val user = event.kind == "message.user"
        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
            Surface(
                color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(if (user) .86f else .94f)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    if (!user) {
                        Text(
                            member?.let { "@${it.handle} · ${it.label}" } ?: event.actor.displayName.ifBlank { event.actor.id },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(3.dp))
                    }
                    com.nousresearch.hermesmobile.ui.components.MarkdownLite(event.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (event.threadId.isNotBlank()) {
                            TextButton(onClick = onReplyThread, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text(stringResource(R.string.ui_a236732715), style = MaterialTheme.typography.labelSmall) }
                        }
                        if (!user && member != null) {
                            TextButton(onClick = { onReplyTo(member.handle) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text("Reply to @${member.handle}", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
    } else {
        val label = roomEventStatusText(event) ?: return
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (event.kind == "turn.failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = if (event.kind.startsWith("turn.")) com.nousresearch.hermesmobile.ui.theme.HermesMono else null
            )
        }
    }
}

@Composable
private fun PendingRoomActionCard(
    action: GroupPendingAction,
    member: GroupMember?,
    busy: Boolean,
    onRetry: (GroupPendingAction) -> Unit,
    onApprove: (GroupPendingAction, String) -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            when (action.kind) {
                "approval" -> {
                    Text("Approval · @${member?.handle ?: action.memberId}", style = MaterialTheme.typography.titleSmall)
                    if (action.command.isNotBlank()) Text(action.command, style = MaterialTheme.typography.bodySmall, fontFamily = com.nousresearch.hermesmobile.ui.theme.HermesMono)
                    if (action.description.isNotBlank()) Text(action.description, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy, onClick = { onApprove(action, "once") }) { Text(stringResource(R.string.ui_168511d24d)) }
                        OutlinedButton(enabled = !busy, onClick = { onApprove(action, "deny") }) { Text(stringResource(R.string.ui_05a2d7332e)) }
                    }
                }
                "retry" -> {
                    Text(stringResource(R.string.ui_94efa66d7a), style = MaterialTheme.typography.titleSmall)
                    Text(action.taskId, style = MaterialTheme.typography.bodySmall, fontFamily = com.nousresearch.hermesmobile.ui.theme.HermesMono)
                    Button(enabled = !busy, onClick = { onRetry(action) }) { Text(stringResource(R.string.ui_942087cc2d)) }
                }
                else -> Text("Room action: ${action.kind}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun shouldRenderRoomEvent(event: GroupEvent): Boolean = when (event.kind) {
    "message.user", "message.member", "turn.failed", "turn.cancelled", "turn.deferred", "room.activity", "room.stop_requested" -> true
    else -> false
}

private fun roomEventStatusText(event: GroupEvent): String? = when (event.kind) {
    "turn.failed" -> "Turn failed: ${event.payload.optString("error").ifBlank { "unknown error" }}"
    "turn.cancelled" -> "Turn cancelled: ${event.payload.optString("reason")}".trimEnd(':', ' ')
    "turn.deferred" -> "Turn deferred: ${event.payload.optString("reason")}".trimEnd(':', ' ')
    "room.activity" -> event.payload.optString("status").takeIf { it.isNotBlank() }?.let { "Room $it" }
    "room.stop_requested" -> "Stop requested"
    else -> null
}

private fun appendMention(text: String, handle: String): String {
    val prefix = text.trimEnd()
    return if (prefix.isBlank()) "@$handle " else "$prefix @$handle "
}
