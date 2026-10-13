package com.nousresearch.hermesmobile.ui.screens

import com.nousresearch.hermesmobile.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nousresearch.hermesmobile.data.GatewayAuthMode
import com.nousresearch.hermesmobile.data.GatewayConfig
import com.nousresearch.hermesmobile.data.GatewayStatus
import com.nousresearch.hermesmobile.ui.components.*
import com.nousresearch.hermesmobile.ui.theme.hermes

@Composable
fun MoreScreen(
    onMenu: () -> Unit,
    config: GatewayConfig?,
    connections: List<GatewayConfig>,
    status: GatewayStatus?,
    profileCount: Int,
    sessionCount: Int,
    onNotifications: () -> Unit,
    onSystem: () -> Unit,
    onConnections: () -> Unit,
    onFeature: (String) -> Unit,
    onRefresh: () -> Unit,
    onCapabilities: () -> Unit,
    onProfiles: () -> Unit,
    onWorkspace: () -> Unit,
    onDisconnect: () -> Unit,
    onSignOut: () -> Unit,
    onSwitchConnection: (String) -> Unit,
    onForgetConnection: (String) -> Unit,
    onAddConnection: () -> Unit
) {
    var details by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf<GatewayConfig?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 22.dp)) {
        item {
            HermesTopBar(stringResource(R.string.a21_settings), listOfNotNull(config?.displayLabel, status?.version?.let { "Hermes $it" }).joinToString(" · ").ifBlank { null },
                leading = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, stringResource(R.string.a21_menu)) } })
            DashedDivider(color = MaterialTheme.hermes.line)
            SectionLabel(stringResource(R.string.a14_intelligence))
            FlatRow(stringResource(R.string.a14_intelligence_hint), leading = { Icon(Icons.Default.Psychology, null) }, onClick = onCapabilities)
            FlatRow(stringResource(R.string.a14_models_advanced), leading = { Icon(Icons.Default.Tune, null) }, onClick = { onFeature("Models") })
            FlatRow(stringResource(R.string.a14_memory), leading = { Icon(Icons.Default.AutoAwesome, null) }, onClick = { onFeature("Memory") })
            FlatRow(stringResource(R.string.a14_plugins), leading = { Icon(Icons.Default.Extension, null) }, onClick = { onFeature("Plugins") })
            SectionLabel(stringResource(R.string.a14_system))
            FlatRow(stringResource(R.string.a14_system_hint), leading = { Icon(Icons.Default.MonitorHeart, null) }, onClick = onSystem)
            FlatRow(stringResource(R.string.a14_connections), leading = { Icon(Icons.Default.Hub, null) }, onClick = onConnections)
            SectionLabel(stringResource(R.string.a14_app_settings))
            com.nousresearch.hermesmobile.ui.AppearanceSettings()
            com.nousresearch.hermesmobile.ui.LanguageSettings()
            FlatRow(stringResource(R.string.a14_connection_details), trailing = { Icon(if (details) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) }, onClick = { details = !details })
            if (details) {
            SectionLabel(stringResource(R.string.ui_34f54fe10b))
            FlatRow(leading = { Icon(Icons.Default.Dns, null) }) {
                Text(config?.displayLabel ?: "Hermes")
                Text(config?.baseUrl.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlatRow(leading = { Icon(Icons.Default.Security, null) }) {
                Text(stringResource(R.string.ui_66880d2d82))
                Text(authLabel(config), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlatRow(leading = { Icon(Icons.Default.Fingerprint, null) }) {
                Text(stringResource(R.string.ui_19ff1c8c79))
                Text(status?.installId?.ifBlank { "Unknown" } ?: "Unknown", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionLabel(stringResource(R.string.ui_307d5e40d4))
            FlatRow(leading = { Icon(Icons.Default.SmartToy, null) }) {
                Text(stringResource(R.string.ui_0045267106))
                Text(profileCount.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlatRow(leading = { Icon(Icons.Default.ChatBubbleOutline, null) }) {
                Text(stringResource(R.string.ui_1d432f5869))
                Text(sessionCount.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            }
            SectionLabel(stringResource(R.string.ui_5a23444828))
            FlatRow(title = androidx.compose.ui.res.stringResource(com.nousresearch.hermesmobile.R.string.push_title),
                leading = { Icon(Icons.Default.NotificationsNone, null) }, onClick = onNotifications)
            FlatRow(
                title = stringResource(R.string.ui_535e52e4a2),
                description = stringResource(R.string.ui_4dd643b524),
                leading = { Icon(Icons.Default.ManageAccounts, null) },
                trailing = { Icon(Icons.Default.ChevronRight, null) },
                onClick = onProfiles
            )
            SectionLabel(stringResource(R.string.ui_dc27311748))
        }

        items(connections, key = { it.id }) { connection ->
            val active = connection.id == config?.id
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(if (active) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked, null)
                Column(Modifier.weight(1f)) {
                    Text(connection.displayLabel, style = MaterialTheme.typography.titleSmall)
                    Text(connection.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(authLabel(connection), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!active) {
                    TextButton(onClick = { onSwitchConnection(connection.id) }) { Text(stringResource(R.string.ui_39921a740b)) }
                }
                IconButton(onClick = { confirmForget = connection }) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.ui_a6bd489d95)) }
            }
            DashedDivider(color = MaterialTheme.hermes.line)
        }

        item {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onAddConnection, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_685f88ae3d))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Refresh, null)
                        Text(stringResource(R.string.ui_7ce64141b1))
                    }
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.LinkOff, null)
                        Text(stringResource(R.string.ui_2c47dab8a6))
                    }
                }
                Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_5dec8319b4))
                }
                Text(
                    "Disconnect keeps the encrypted connection for later. Sign out removes only this gateway's Hermes credentials and returns to its advertised login methods.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    confirmForget?.let { connection ->
        AlertDialog(
            onDismissRequest = { confirmForget = null },
            title = { Text(stringResource(R.string.ui_0eff7e374f)) },
            text = { Text("${connection.displayLabel} and its encrypted authentication material will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = { confirmForget = null; onForgetConnection(connection.id) }) { Text(stringResource(R.string.ui_a6bd489d95)) }
            },
            dismissButton = { TextButton(onClick = { confirmForget = null }) { Text(stringResource(R.string.ui_19766ed6cc)) } }
        )
    }
}

private fun authLabel(config: GatewayConfig?): String = when (config?.authMode) {
    GatewayAuthMode.NONE -> "None / gateway-local"
    GatewayAuthMode.SESSION_TOKEN -> "Session token"
    GatewayAuthMode.BASIC -> "Username/password${config.authProvider.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}"
    GatewayAuthMode.NATIVE_PKCE -> "Native PKCE${config.authProvider.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}"
    null -> "Unknown"
}
