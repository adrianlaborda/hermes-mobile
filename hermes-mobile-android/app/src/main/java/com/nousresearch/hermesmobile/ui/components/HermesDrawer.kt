package com.nousresearch.hermesmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
    recentSessions: List<com.nousresearch.hermesmobile.data.HermesSession> = emptyList(),
    activeSessionId: String? = null,
    onOpenSession: (com.nousresearch.hermesmobile.data.HermesSession) -> Unit = {},
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
                Icon(Icons.Default.Edit, null, Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.a14_new_chat))
            }
            Spacer(Modifier.height(10.dp))

            DrawerItem(Icons.Default.SpaceDashboard, stringResource(R.string.a14_home), current == MainSection.HOME) { onClose(); onSelect(MainSection.HOME) }
            DrawerItem(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.ui_ef5b4049bb), current == MainSection.CHAT) { onClose(); onSelect(MainSection.CHAT) }
            DrawerItem(Icons.Default.SmartToy, stringResource(R.string.ui_f17c0acbd8), current == MainSection.BOTS) { onClose(); onSelect(MainSection.BOTS) }

            // Desktop's sidebar is primarily the session list; mirror its most recent rows here.
            if (recentSessions.isNotEmpty()) {
                DrawerSection(stringResource(R.string.a21_recent))
                recentSessions.take(6).forEach { session ->
                    DrawerSessionRow(session, selected = session.id == activeSessionId) { onClose(); onOpenSession(session) }
                }
            }

            DrawerSection(stringResource(R.string.a21_agent))
            DrawerItem(Icons.Default.Psychology, stringResource(R.string.a14_intelligence), current == MainSection.CAPABILITIES) { onClose(); onSelect(MainSection.CAPABILITIES) }
            DrawerItem(Icons.Default.Schedule, stringResource(R.string.a14_work), current == MainSection.WORK || current == MainSection.CRON) { onClose(); onSelect(MainSection.WORK) }
            DrawerItem(Icons.Default.FolderOpen, stringResource(R.string.ui_87bb59ba2f), current == MainSection.WORKSPACE) { onClose(); onSelect(MainSection.WORKSPACE) }

            DrawerSection(stringResource(R.string.a21_gateway))
            DrawerItem(Icons.Default.MonitorHeart, stringResource(R.string.a14_system), current == MainSection.SYSTEM) { onClose(); onSelect(MainSection.SYSTEM) }
            DrawerItem(Icons.Default.Hub, stringResource(R.string.a14_connections), current == MainSection.MESSAGING) { onClose(); onSelect(MainSection.MESSAGING) }
            DrawerItem(Icons.Default.ManageAccounts, stringResource(R.string.ui_535e52e4a2), current == MainSection.PROFILES) { onClose(); onSelect(MainSection.PROFILES) }
            DrawerItem(Icons.Default.NotificationsNone, stringResource(R.string.push_title), false, onClick = { onClose(); onNotifications() })

            Spacer(Modifier.height(14.dp))
            DashedDivider(color = MaterialTheme.hermes.line)
            Spacer(Modifier.height(8.dp))
            DrawerItem(Icons.Default.Settings, stringResource(R.string.a21_settings), current == MainSection.MORE) { onClose(); onSelect(MainSection.MORE) }
            AppearanceRow()
        }
    }
}

@Composable
private fun DrawerSessionRow(session: com.nousresearch.hermesmobile.data.HermesSession, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.hermes.accentTint else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (session.pinned) Icon(Icons.Default.PushPin, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            session.title.ifBlank { stringResource(R.string.a21_untitled) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 1f else .8f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (session.unread) StateDot(true)
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
