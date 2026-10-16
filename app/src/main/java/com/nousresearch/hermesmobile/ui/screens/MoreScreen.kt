package com.nousresearch.hermesmobile.ui.screens

import com.nousresearch.hermesmobile.BuildConfig
import com.nousresearch.hermesmobile.R
import android.content.ClipData
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.launch
import androidx.compose.ui.res.pluralStringResource
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
import com.nousresearch.hermesmobile.MainSection
import com.nousresearch.hermesmobile.data.GatewayAuthMode
import com.nousresearch.hermesmobile.data.GatewayConfig
import com.nousresearch.hermesmobile.data.GatewayStatus
import com.nousresearch.hermesmobile.ui.components.*
import com.nousresearch.hermesmobile.ui.theme.hermes

/**
 * Settings, laid out like Hermes Desktop's: the agent (models, voice, configuration), where its models come from
 * (providers), the gateways it runs on (connections, system, profiles), notifications and the app itself.
 * Each row opens a page of its own; nothing here repeats an entry of the side menu.
 */
@Composable
fun MoreScreen(
    onMenu: () -> Unit,
    config: GatewayConfig?,
    status: GatewayStatus?,
    profileNames: List<String> = emptyList(),
    onOpen: (MainSection) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 22.dp)) {
        item {
            HermesTopBar(stringResource(R.string.a21_settings), listOfNotNull(config?.displayLabel, status?.version?.let { "Hermes $it" }).joinToString(" · ").ifBlank { null },
                leading = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, stringResource(R.string.a21_menu)) } })
            DashedDivider(color = MaterialTheme.hermes.line)
            com.nousresearch.hermesmobile.update.UpdateNotices()

            SectionLabel(stringResource(R.string.i18n_set_agent))
            SettingsLink(Icons.Default.Tune, stringResource(R.string.i18n_set_models), stringResource(R.string.i18n_set_models_hint)) { onOpen(MainSection.MODELS) }
            SettingsLink(Icons.Default.RecordVoiceOver, stringResource(R.string.a14_voice), stringResource(R.string.i18n_set_voice_hint)) { onOpen(MainSection.VOICE) }
            SettingsLink(Icons.Default.SettingsSuggest, stringResource(R.string.i18n_server_settings), stringResource(R.string.i18n_set_config_hint)) { onOpen(MainSection.SERVER_SETTINGS) }

            SectionLabel(stringResource(R.string.i18n_providers))
            SettingsLink(Icons.Default.Key, stringResource(R.string.i18n_providers), stringResource(R.string.i18n_set_providers_hint)) { onOpen(MainSection.PROVIDERS) }

            SectionLabel(stringResource(R.string.i18n_set_gateways))
            SettingsLink(Icons.Default.Dns, stringResource(R.string.ui_dc27311748), config?.displayLabel ?: stringResource(R.string.i18n_set_connections_hint)) { onOpen(MainSection.GATEWAYS) }
            SettingsLink(Icons.Default.MonitorHeart, stringResource(R.string.a14_system), stringResource(R.string.i18n_set_system_hint)) { onOpen(MainSection.SYSTEM) }
            SettingsLink(Icons.Default.ManageAccounts, stringResource(R.string.ui_535e52e4a2), stringResource(R.string.ui_4dd643b524)) { onOpen(MainSection.PROFILES) }

            SectionLabel(stringResource(R.string.push_title))
            SettingsLink(Icons.Default.NotificationsNone, stringResource(R.string.push_title), stringResource(R.string.i18n_set_push_hint)) { onOpen(MainSection.NOTIFICATIONS) }

            SectionLabel(stringResource(R.string.a14_app_settings))
            com.nousresearch.hermesmobile.ui.AppearanceSettings()
            com.nousresearch.hermesmobile.ui.LanguageSettings()
            com.nousresearch.hermesmobile.assist.AssistSettingsSection(profileNames)
        }
        item { AboutSection() }
    }
}

@Composable
private fun SettingsLink(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String?, onClick: () -> Unit) =
    FlatRow(title, description, leading = { Icon(icon, null) }, trailing = { Icon(Icons.Default.ChevronRight, null) }, onClick = onClick)

/** The gateways this phone knows: switch, add, forget, and the active one's details and session controls. */
@Composable
fun GatewaysScreen(
    onMenu: () -> Unit,
    config: GatewayConfig?,
    connections: List<GatewayConfig>,
    status: GatewayStatus?,
    profileCount: Int,
    sessionCount: Int,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onSignOut: () -> Unit,
    onSwitchConnection: (String) -> Unit,
    onForgetConnection: (String) -> Unit,
    onAddConnection: () -> Unit
) {
    var confirmForget by remember { mutableStateOf<GatewayConfig?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 22.dp)) {
        item {
            HermesTopBar(stringResource(R.string.ui_dc27311748), pluralStringResource(R.plurals.i18n_gateways_count, connections.size, connections.size),
                leading = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, stringResource(R.string.a21_menu)) } },
                actions = { IconButton(onClick = onAddConnection) { Icon(Icons.Default.Add, stringResource(R.string.ui_685f88ae3d)) } })
            DashedDivider(color = MaterialTheme.hermes.line)
            SectionLabel(stringResource(R.string.i18n_set_saved_gateways))
        }
        items(connections, key = { it.id }) { connection ->
            val active = connection.id == config?.id
            FlatRow(
                leading = { Icon(if (active) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked, null,
                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!active) TextButton(onClick = { onSwitchConnection(connection.id) }) { Text(stringResource(R.string.ui_39921a740b)) }
                        IconButton(onClick = { confirmForget = connection }) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.ui_a6bd489d95)) }
                    }
                }
            ) {
                Text(connection.displayLabel, style = MaterialTheme.typography.bodyLarge)
                Text(connection.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(authLabel(connection), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionLabel(stringResource(R.string.ui_34f54fe10b))
            FlatRow(leading = { Icon(Icons.Default.Fingerprint, null) }) {
                Text(stringResource(R.string.ui_19ff1c8c79))
                Text(status?.installId?.ifBlank { stringResource(R.string.i18n_unknown) } ?: stringResource(R.string.i18n_unknown), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlatRow(leading = { Icon(Icons.Default.SmartToy, null) }) {
                Text(stringResource(R.string.ui_0045267106))
                Text(profileCount.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlatRow(leading = { Icon(Icons.Default.ChatBubbleOutline, null) }) {
                Text(stringResource(R.string.ui_1d432f5869))
                Text(sessionCount.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlatRow(stringResource(R.string.ui_7ce64141b1), leading = { Icon(Icons.Default.Refresh, null) }, onClick = onRefresh)
            FlatRow(stringResource(R.string.ui_2c47dab8a6), leading = { Icon(Icons.Default.LinkOff, null) }, onClick = onDisconnect)
            FlatRow(leading = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.error) }, onClick = onSignOut) {
                Text(stringResource(R.string.ui_5dec8319b4), color = MaterialTheme.colorScheme.error)
            }
            Text(
                stringResource(R.string.i18n_more_disconnect_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }

    confirmForget?.let { connection ->
        AlertDialog(
            onDismissRequest = { confirmForget = null },
            title = { Text(stringResource(R.string.ui_0eff7e374f)) },
            text = { Text(stringResource(R.string.i18n_more_remove_note, connection.displayLabel)) },
            confirmButton = {
                TextButton(onClick = { confirmForget = null; onForgetConnection(connection.id) }) { Text(stringResource(R.string.ui_a6bd489d95)) }
            },
            dismissButton = { TextButton(onClick = { confirmForget = null }) { Text(stringResource(R.string.ui_19766ed6cc)) } }
        )
    }
}

const val APP_REPO_URL = "https://github.com/adrianlaborda/hermes-mobile"

/** Which build this is, plus where to read changes, find the source and report problems. */
@Composable
private fun AboutSection() {
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    SectionLabel(stringResource(R.string.about_title))
    FlatRow(
        title = "Hermes Mobile",
        description = stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
        leading = { HermesBrandMark(size = 24) },
        trailing = { Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)) },
        // Android 13+ confirms the copy itself.
        onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Hermes Mobile", version))) } }
    )
    com.nousresearch.hermesmobile.update.UpdateSettings()
    FlatRow(stringResource(R.string.about_changes), leading = { Icon(Icons.Default.NewReleases, null) },
        trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)) },
        onClick = { uri.openUri("$APP_REPO_URL/releases") })
    FlatRow(stringResource(R.string.about_report), leading = { Icon(Icons.Default.BugReport, null) },
        trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)) },
        onClick = { uri.openUri("$APP_REPO_URL/issues/new/choose") })
    FlatRow(stringResource(R.string.about_source), leading = { Icon(Icons.Default.Code, null) },
        trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)) },
        onClick = { uri.openUri(APP_REPO_URL) })
    FlatRow(stringResource(R.string.about_licenses), leading = { Icon(Icons.Default.Gavel, null) },
        trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)) },
        onClick = { uri.openUri("$APP_REPO_URL/blob/main/THIRD_PARTY_NOTICES.md") })
    Text(
        stringResource(R.string.about_unofficial),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
    )
}

@Composable
private fun authLabel(config: GatewayConfig?): String {
    val provider = config?.authProvider?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
    return when (config?.authMode) {
        GatewayAuthMode.NONE -> stringResource(R.string.i18n_auth_none)
        GatewayAuthMode.SESSION_TOKEN -> stringResource(R.string.i18n_auth_token)
        GatewayAuthMode.BASIC -> stringResource(R.string.i18n_auth_basic) + provider
        GatewayAuthMode.NATIVE_PKCE -> stringResource(R.string.i18n_auth_pkce) + provider
        null -> stringResource(R.string.i18n_unknown)
    }
}
