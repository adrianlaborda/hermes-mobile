package com.nousresearch.hermesmobile.update

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.ui.components.FlatRow
import com.nousresearch.hermesmobile.ui.components.MarkdownLite
import com.nousresearch.hermesmobile.ui.components.StatusPill
import com.nousresearch.hermesmobile.ui.theme.HermesMono
import com.nousresearch.hermesmobile.ui.theme.hermes
import kotlinx.coroutines.launch

/** The new version's notes and the install button. Hosted by the app shell, so it opens over any screen. */
@Composable
fun UpdateDialog() {
    val state by AppUpdates.state.collectAsState()
    val release = state.available
    if (!state.showDialog || release == null) return
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val install = state.install
    val busy = install is InstallState.Downloading || install is InstallState.Installing
    AlertDialog(
        onDismissRequest = { if (!busy) AppUpdates.dismissDialog() },
        icon = { Icon(Icons.Default.SystemUpdate, null) },
        title = { Text(stringResource(R.string.update_available_title, release.version.toString())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.update_installed_version, AppUpdates.current.toString()),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (release.notes.isNotBlank()) {
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) { MarkdownLite(release.notes) }
                }
                when (install) {
                    is InstallState.Downloading -> {
                        Text(stringResource(R.string.update_downloading), style = MaterialTheme.typography.labelMedium)
                        LinearProgressIndicator(progress = { install.progress }, Modifier.fillMaxWidth())
                    }
                    InstallState.Installing -> {
                        Text(stringResource(R.string.update_installing), style = MaterialTheme.typography.labelMedium)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    InstallState.NeedsPermission -> Text(stringResource(R.string.update_permission), style = MaterialTheme.typography.bodySmall)
                    is InstallState.Failed -> Text(failure(install.reason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    InstallState.Idle -> Unit
                }
            }
        },
        confirmButton = {
            when (install) {
                InstallState.NeedsPermission -> TextButton(onClick = {
                    // Back from Android's settings, Install tries again.
                    AppUpdates.resetInstall()
                    context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                }) { Text(stringResource(R.string.update_allow)) }
                is InstallState.Failed -> TextButton(onClick = { uri.openUri(release.pageUrl) }) { Text(stringResource(R.string.update_open_github)) }
                else -> TextButton(onClick = { AppUpdates.install(context) }, enabled = !busy) { Text(stringResource(R.string.update_install)) }
            }
        },
        dismissButton = { if (!busy) TextButton(onClick = AppUpdates::dismissDialog) { Text(stringResource(R.string.update_later)) } }
    )
}

@Composable
private fun failure(reason: String): String = when (reason) {
    "checksum_mismatch", "checksum_missing" -> stringResource(R.string.update_failed_checksum)
    "signature_mismatch" -> stringResource(R.string.update_failed_signature)
    else -> stringResource(R.string.update_failed, reason)
}

/** At the top of Settings: a waiting update, and a server plugin that should be updated. */
@Composable
fun UpdateNotices() {
    val state by AppUpdates.state.collectAsState()
    val context = LocalContext.current
    state.available?.let { release ->
        FlatRow(
            title = stringResource(R.string.update_available_title, release.version.toString()),
            description = stringResource(R.string.update_available_tap),
            leading = { Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary) },
            trailing = { Icon(Icons.Default.ChevronRight, null) },
            onClick = { AppUpdates.openDialog(context) }
        )
    }
    val plugin = state.plugin?.takeIf { it.required || it.updateAvailable } ?: return
    var open by remember { mutableStateOf(false) }
    FlatRow(
        title = stringResource(if (plugin.required) R.string.update_plugin_required else R.string.update_plugin_available,
            (if (plugin.required) AppUpdates.MIN_PLUGIN else plugin.latest).toString()),
        description = stringResource(R.string.update_plugin_installed, plugin.installed.toString()),
        leading = { Icon(Icons.Default.Extension, null, tint = if (plugin.required) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) },
        trailing = { Icon(Icons.Default.ChevronRight, null) },
        onClick = { open = true }
    )
    if (open) PluginUpdateDialog(plugin) { open = false }
}

@Composable
private fun PluginUpdateDialog(plugin: PluginStatus, onDismiss: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Extension, null) },
        title = { Text(stringResource(R.string.update_plugin_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (plugin.required) Text(stringResource(R.string.update_plugin_required_body, plugin.installed.toString(), AppUpdates.MIN_PLUGIN.toString()))
                else Text(stringResource(R.string.update_plugin_available_body, plugin.installed.toString(), plugin.latest.toString()))
                Text(stringResource(R.string.update_plugin_how), style = MaterialTheme.typography.bodySmall)
                Text(
                    AppUpdates.PLUGIN_UPDATE_COMMAND,
                    Modifier.fillMaxWidth().background(MaterialTheme.hermes.codeBlock, RoundedCornerShape(8.dp)).padding(10.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = HermesMono)
                )
                Text(stringResource(R.string.update_plugin_restart), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("hermes", AppUpdates.PLUGIN_UPDATE_COMMAND))) }
            }) { Text(stringResource(R.string.update_copy_command)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_close)) } }
    )
}

/** In Settings → About: check now, and whether to receive betas. */
@Composable
fun UpdateSettings() {
    if (!AppUpdates.enabled) return
    val state by AppUpdates.state.collectAsState()
    val context = LocalContext.current
    var beta by remember { mutableStateOf(AppUpdates.beta(context)) }
    FlatRow(
        title = stringResource(R.string.update_check),
        description = when {
            state.checking -> stringResource(R.string.update_checking)
            state.upToDate -> stringResource(R.string.update_up_to_date)
            state.checkFailed -> stringResource(R.string.update_check_failed)
            else -> null
        },
        leading = { Icon(Icons.Default.Update, null) },
        trailing = { if (state.available != null) StatusPill(state.available!!.version.toString()) },
        onClick = { if (state.available != null) AppUpdates.openDialog(context) else AppUpdates.checkNow(context, manual = true) }
    )
    FlatRow(
        title = stringResource(R.string.update_beta),
        description = stringResource(R.string.update_beta_hint),
        leading = { Icon(Icons.Default.Science, null) },
        trailing = { Switch(checked = beta, onCheckedChange = null) },
        onClick = { beta = !beta; AppUpdates.setBeta(context, beta) }
    )
}
