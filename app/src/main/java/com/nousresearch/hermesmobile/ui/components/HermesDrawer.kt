package com.nousresearch.hermesmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nousresearch.hermesmobile.MainSection
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.ui.theme.HermesTokens
import com.nousresearch.hermesmobile.ui.theme.hermes

/**
 * Reference-style navigation drawer: brand header with connection state,
 * a prominent "New chat" action, primary destinations as rounded rows and
 * grouped secondary destinations (intelligence / connections / system).
 */
@Composable
fun HermesDrawer(
    current: MainSection,
    connected: Boolean,
    gatewayLabel: String,
    onSelect: (MainSection) -> Unit,
    onNewChat: () -> Unit,
    onNotifications: () -> Unit,
    onClose: () -> Unit,
    /** Kanban's entry shows only while the gateway has the plugin active for the profile. */
    kanbanAvailable: Boolean = false,
    onKanban: () -> Unit = {},
    recentSessions: List<com.nousresearch.hermesmobile.data.HermesSession> = emptyList(),
    activeSessionId: String? = null,
    sessionDots: Map<String, com.nousresearch.hermesmobile.data.SessionDot> = emptyMap(),
    onOpenSession: (com.nousresearch.hermesmobile.data.HermesSession) -> Unit = {},
    profile: String = "",
    profiles: List<String> = emptyList(),
    onProfile: (String) -> Unit = {},
    drawerState: DrawerState? = null
) {
    val sheet: @Composable (@Composable ColumnScope.() -> Unit) -> Unit = { content ->
        // Passing the state lets the sheet own system back (and its predictive-back animation).
        if (drawerState != null) ModalDrawerSheet(drawerState, drawerContainerColor = MaterialTheme.colorScheme.background, content = content)
        else ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.background, content = content)
    }
    sheet {
        Column(
            Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 16.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HermesBrandMark(size = 36)
                Column(Modifier.weight(1f)) {
                    Text("Hermes", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StateDot(good = connected, warning = !connected)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            gatewayLabel.ifBlank { stringResource(R.string.connection_mobile) } + " · " +
                                stringResource(if (connected) R.string.a14_connected else R.string.a14_reconnecting),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = { onClose(); onNewChat() },
                enabled = connected,
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.hermes.lineStrong)
            ) {
                Icon(Icons.Outlined.AddComment, null, Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.a14_new_chat))
            }
            Spacer(Modifier.height(10.dp))

            DrawerItem(Icons.Default.SpaceDashboard, stringResource(R.string.a14_home), current == MainSection.HOME) { onClose(); onSelect(MainSection.HOME) }
            DrawerItem(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.ui_ef5b4049bb), current == MainSection.CHAT) { onClose(); onSelect(MainSection.CHAT) }
            DrawerItem(Icons.Default.SmartToy, stringResource(R.string.ui_f17c0acbd8), current == MainSection.BOTS) { onClose(); onSelect(MainSection.BOTS) }
            if (kanbanAvailable) DrawerItem(Icons.Default.ViewKanban, stringResource(R.string.kanban_title), current == MainSection.KANBAN) { onClose(); onKanban() }

            // Desktop's sidebar is primarily the session list; mirror its most recent rows here.
            if (recentSessions.isNotEmpty()) {
                DrawerSection(stringResource(R.string.a21_recent))
                recentSessions.take(6).forEach { session ->
                    DrawerSessionRow(session, selected = session.id == activeSessionId,
                        dot = com.nousresearch.hermesmobile.data.LiveSessions.dotFor(sessionDots, session.id, session.unread)) { onClose(); onOpenSession(session) }
                }
            }

            // Desktop's sidebar navigation: where the agent's work lives. Settings holds everything else.
            DrawerSection(stringResource(R.string.a21_agent))
            DrawerItem(Icons.Default.Psychology, stringResource(R.string.i18n_capabilities), current == MainSection.CAPABILITIES) { onClose(); onSelect(MainSection.CAPABILITIES) }
            DrawerItem(Icons.Default.Hub, stringResource(R.string.i18n_messaging), current == MainSection.MESSAGING) { onClose(); onSelect(MainSection.MESSAGING) }
            DrawerItem(Icons.Default.FolderOpen, stringResource(R.string.a14_files), current == MainSection.FILES) { onClose(); onSelect(MainSection.FILES) }
            DrawerItem(Icons.Default.Schedule, stringResource(R.string.ui_61b7bb44e2), current == MainSection.CRON) { onClose(); onSelect(MainSection.CRON) }
            DrawerItem(Icons.Default.AutoAwesome, stringResource(R.string.a14_memory), current == MainSection.MEMORY) { onClose(); onSelect(MainSection.MEMORY) }

            Spacer(Modifier.height(14.dp))
            DashedDivider(color = MaterialTheme.hermes.line)
            Spacer(Modifier.height(8.dp))
            if (profile.isNotBlank()) DrawerProfile(profile, profiles, onProfile) { onClose(); onSelect(MainSection.PROFILES) }
            DrawerItem(Icons.Default.Settings, stringResource(R.string.a21_settings), current in SETTINGS_PAGES) { onClose(); onSelect(MainSection.MORE) }
            AppearanceRow()
        }
    }
}

/** The pages Settings opens: the menu marks Settings while one of them is on screen. */
private val SETTINGS_PAGES = setOf(
    MainSection.MORE, MainSection.MODELS, MainSection.VOICE, MainSection.SERVER_SETTINGS, MainSection.PROVIDERS,
    MainSection.GATEWAYS, MainSection.SYSTEM, MainSection.PROFILES, MainSection.NOTIFICATIONS,
    MainSection.APPEARANCE, MainSection.ASSISTANT, MainSection.ABOUT
)

/** Desktop's profile pill: the active profile; tap to switch, or to manage profiles. */
@Composable
private fun DrawerProfile(profile: String, profiles: List<String>, onProfile: (String) -> Unit, onManage: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.AccountCircle, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ui_d696a35bdd), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(profile, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.UnfoldMore, stringResource(R.string.chat_change_profile), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            profiles.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name, maxLines = 1) },
                    leadingIcon = { if (name == profile) Icon(Icons.Default.Check, null) },
                    onClick = { open = false; if (name != profile) onProfile(name) }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.i18n_manage_profiles)) },
                leadingIcon = { Icon(Icons.Default.ManageAccounts, null) },
                onClick = { open = false; onManage() }
            )
        }
    }
}

@Composable
private fun DrawerSessionRow(
    session: com.nousresearch.hermesmobile.data.HermesSession,
    selected: Boolean,
    dot: com.nousresearch.hermesmobile.data.SessionDot,
    onClick: () -> Unit
) {
    // Desktop's sidebar row: status dot in the lead slot, the travelling arc while its turn runs.
    Row(
        Modifier
            .fillMaxWidth()
            .runningArc(dot == com.nousresearch.hermesmobile.data.SessionDot.WORKING, cornerRadius = 8f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.hermes.accentTint else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SessionStatusDot(dot)
        if (session.pinned) Icon(Icons.Default.PushPin, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            session.displayTitle.ifBlank { stringResource(R.string.a21_untitled) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 1f else .8f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DrawerSection(label: String) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) MaterialTheme.hermes.accentTint else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon, null, Modifier.size(21.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        )
    }
}

/** Compact appearance cycle (system → light → dark) pinned to the drawer footer. */
@Composable
private fun AppearanceRow() {
    val context = LocalContext.current
    val mode = com.nousresearch.hermesmobile.ui.rememberAppearance()
    val next = when (mode) { "light" -> "dark"; "dark" -> "system"; else -> "light" }
    val icon = when (mode) { "light" -> Icons.Default.LightMode; "dark" -> Icons.Default.DarkMode; else -> Icons.Default.BrightnessAuto }
    val label = when (mode) {
        "light" -> stringResource(R.string.theme_light)
        "dark" -> stringResource(R.string.theme_dark)
        else -> stringResource(R.string.theme_system)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                context.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE)
                    .edit().putString("theme", next).apply()
            }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.appearance), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
