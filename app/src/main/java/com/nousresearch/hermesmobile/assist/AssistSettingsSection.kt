package com.nousresearch.hermesmobile.assist

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.NoEncryption
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.ui.components.FlatRow
import com.nousresearch.hermesmobile.ui.components.SectionLabel

/** Settings → Voice assistant: become the digital assistant, which profile answers, and the lock screen. */
@Composable
fun AssistSettingsSection(profiles: List<String>) {
    val context = LocalContext.current
    val settings = remember(context) { AssistSettings(context) }
    var isDefault by remember { mutableStateOf(isDefaultAssistant(context)) }
    LifecycleResumeEffect(Unit) { isDefault = isDefaultAssistant(context); onPauseOrDispose {} }
    var profile by remember { mutableStateOf(settings.profile) }
    var lockedProfile by remember { mutableStateOf(settings.lockedProfile) }
    var allowLocked by remember { mutableStateOf(settings.allowWhileLocked) }
    var deviceVoice by remember { mutableStateOf(settings.deviceVoice) }
    var voiceMenu by remember { mutableStateOf(false) }

    SectionLabel(stringResource(R.string.assist_settings_title))
    FlatRow(
        title = stringResource(R.string.assist_default_title),
        description = stringResource(if (isDefault) R.string.assist_default_on else R.string.assist_default_off),
        leading = { Icon(Icons.Default.KeyboardVoice, null) },
        trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)) },
        onClick = { openAssistantSettings(context) }
    )
    ProfileRow(
        title = stringResource(R.string.assist_profile),
        value = profile, blankLabel = stringResource(R.string.assist_profile_app), profiles = profiles,
        icon = { Icon(Icons.Default.ManageAccounts, null) }
    ) { profile = it; settings.profile = it }
    Box {
        FlatRow(
            title = stringResource(R.string.assist_voice),
            description = stringResource(if (deviceVoice) R.string.assist_voice_device_hint else R.string.assist_voice_server_hint),
            leading = { Icon(Icons.AutoMirrored.Filled.VolumeUp, null) },
            trailing = { Text(stringResource(if (deviceVoice) R.string.assist_voice_device else R.string.assist_voice_server), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) },
            onClick = { voiceMenu = true }
        )
        DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.assist_voice_device)) }, onClick = { voiceMenu = false; deviceVoice = true; settings.deviceVoice = true })
            DropdownMenuItem(text = { Text(stringResource(R.string.assist_voice_server)) }, onClick = { voiceMenu = false; deviceVoice = false; settings.deviceVoice = false })
        }
    }
    if (deviceVoice) {
        FlatRow(
            title = stringResource(R.string.assist_voice_android_settings),
            leading = { Icon(Icons.Default.RecordVoiceOver, null) },
            trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)) },
            onClick = { openTtsSettings(context) }
        )
    }
    FlatRow(
        title = stringResource(R.string.assist_lock_title),
        description = stringResource(R.string.assist_lock_desc),
        leading = { Icon(if (allowLocked) Icons.Default.NoEncryption else Icons.Default.Lock, null) },
        trailing = { Switch(checked = allowLocked, onCheckedChange = null) },
        onClick = { allowLocked = !allowLocked; settings.allowWhileLocked = allowLocked }
    )
    if (allowLocked) {
        ProfileRow(
            title = stringResource(R.string.assist_locked_profile),
            value = lockedProfile, blankLabel = stringResource(R.string.assist_locked_profile_same), profiles = profiles,
            icon = { Icon(Icons.Default.Lock, null) }
        ) { lockedProfile = it; settings.lockedProfile = it }
        Text(
            stringResource(R.string.assist_lock_tip),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ProfileRow(
    title: String, value: String, blankLabel: String, profiles: List<String>,
    icon: @Composable () -> Unit, onPick: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FlatRow(title = title, description = value.ifBlank { blankLabel }, leading = icon,
            trailing = { Icon(Icons.Default.ArrowDropDown, null) }, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(blankLabel) }, onClick = { open = false; onPick("") })
            profiles.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onPick(name) }) }
        }
    }
}

private fun isDefaultAssistant(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 29) context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
    else Settings.Secure.getString(context.contentResolver, "assistant").orEmpty().startsWith(context.packageName + "/")

/** Android's text-to-speech settings: engine, installed voices, speech rate. */
private fun openTtsSettings(context: Context) {
    for (intent in listOf(Intent("com.android.settings.TTS_SETTINGS"), Intent(Settings.ACTION_SETTINGS))) {
        if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}

/** The "Digital assistant app" page; the assistant role cannot be requested with a dialog. */
private fun openAssistantSettings(context: Context) {
    val intents = listOf(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS), Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    for (intent in intents) {
        if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}
