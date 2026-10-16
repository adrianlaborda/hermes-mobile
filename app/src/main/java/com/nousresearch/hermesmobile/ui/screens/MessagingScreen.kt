@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nousresearch.hermesmobile.ui.screens

import com.nousresearch.hermesmobile.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

import androidx.compose.material.icons.automirrored.filled.OpenInNew
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import android.content.ClipData
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nousresearch.hermesmobile.data.*
import com.nousresearch.hermesmobile.ui.components.*
import com.nousresearch.hermesmobile.ui.theme.hermes

private enum class MessagingTab { CHANNELS, ACCESS, WEBHOOKS }

@Composable
fun MessagingScreen(
    profiles: List<HermesProfile>,
    selected: String,
    messaging: List<MessagingPlatform>,
    pending: List<PairingUser>,
    approved: List<PairingUser>,
    onboarding: TelegramOnboarding?,
    webhooks: WebhookState,
    webhookReceipt: WebhookReceipt?,
    onProfile: (String) -> Unit,
    onEnable: (MessagingPlatform, Boolean) -> Unit,
    onConfigure: (MessagingPlatform, Map<String, String>, List<String>) -> Unit,
    onTest: (MessagingPlatform) -> Unit,
    onStartTelegram: (String) -> Unit,
    onApplyTelegram: (List<String>) -> Unit,
    onCancelTelegram: () -> Unit,
    onApprove: (PairingUser) -> Unit,
    onRevoke: (PairingUser) -> Unit,
    onClearPending: () -> Unit,
    onCreateWebhook: (WebhookDraft) -> Unit,
    onWebhookEnabled: (WebhookRoute, Boolean) -> Unit,
    onDeleteWebhook: (WebhookRoute) -> Unit,
    onDismissWebhookReceipt: () -> Unit,
    onRestartGateway: () -> Unit,
    onMenu: () -> Unit = {}
) {
    var tab by remember { mutableStateOf(MessagingTab.CHANNELS) }
    var configure by remember { mutableStateOf<MessagingPlatform?>(null) }
    var createWebhook by remember { mutableStateOf(false) }
    var botName by remember { mutableStateOf("") }
    var confirmRestart by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        HermesTopBar(
            stringResource(R.string.a14_connections),
            subtitleContent = { com.nousresearch.hermesmobile.ui.components.ProfileSwitcher(selected, profiles.map { it.name }, onProfile) },
            leading = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, stringResource(R.string.a21_menu)) } },
            actions = {
                IconButton(onClick = { confirmRestart = true }) {
                    Icon(Icons.Default.RestartAlt, stringResource(R.string.i18n_msg_restart))
                }
            }
        )
        DashedDivider(color = MaterialTheme.hermes.line)

        SecondaryTabRow(selectedTabIndex = tab.ordinal) {
            Tab(tab == MessagingTab.CHANNELS, { tab = MessagingTab.CHANNELS }, text = { Text(stringResource(R.string.ui_4c8906cf76)) }, icon = { Icon(Icons.Default.Hub, null) })
            Tab(tab == MessagingTab.ACCESS, { tab = MessagingTab.ACCESS }, text = { Text(stringResource(R.string.ui_ec5ba0abb7)) }, icon = { Icon(Icons.Default.Group, null) })
            Tab(tab == MessagingTab.WEBHOOKS, { tab = MessagingTab.WEBHOOKS }, text = { Text(stringResource(R.string.ui_45808d75bf)) }, icon = { Icon(Icons.Default.Webhook, null) })
        }

        when (tab) {
            MessagingTab.CHANNELS -> ChannelsList(
                messaging = messaging,
                onboarding = onboarding,
                botName = botName,
                onBotName = { botName = it },
                onEnable = onEnable,
                onConfigure = { configure = it },
                onTest = onTest,
                onStartTelegram = onStartTelegram,
                onApplyTelegram = onApplyTelegram,
                onCancelTelegram = onCancelTelegram,
                onOpenUri = { raw -> openSafeUri(context, raw) }
            )
            MessagingTab.ACCESS -> PairingList(pending, approved, onApprove, onRevoke, onClearPending)
            MessagingTab.WEBHOOKS -> WebhooksList(webhooks, { createWebhook = true }, onWebhookEnabled, onDeleteWebhook)
        }
    }

    configure?.let { platform ->
        PlatformConfigDialog(
            platform = platform,
            onDismiss = { configure = null },
            onOpenDocs = { openSafeUri(context, platform.docsUrl) },
            onSave = { env, clear -> configure = null; onConfigure(platform, env, clear) }
        )
    }

    if (createWebhook) {
        WebhookCreateDialog(
            onDismiss = { createWebhook = false },
            onCreate = { createWebhook = false; onCreateWebhook(it) }
        )
    }

    webhookReceipt?.let { receipt ->
        WebhookReceiptDialog(receipt, onDismissWebhookReceipt)
    }

    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text(stringResource(R.string.ui_58d94844d2)) },
            text = { Text(stringResource(R.string.ui_c21df17642)) },
            confirmButton = {
                TextButton(onClick = { confirmRestart = false; onRestartGateway() }) { Text(stringResource(R.string.ui_6b983a81e5)) }
            },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text(stringResource(R.string.ui_19766ed6cc)) } }
        )
    }
}

@Composable
private fun ChannelsList(
    messaging: List<MessagingPlatform>,
    onboarding: TelegramOnboarding?,
    botName: String,
    onBotName: (String) -> Unit,
    onEnable: (MessagingPlatform, Boolean) -> Unit,
    onConfigure: (MessagingPlatform) -> Unit,
    onTest: (MessagingPlatform) -> Unit,
    onStartTelegram: (String) -> Unit,
    onApplyTelegram: (List<String>) -> Unit,
    onCancelTelegram: () -> Unit,
    onOpenUri: (String) -> Unit
) {
    val telegram = messaging.firstOrNull { it.id == "telegram" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (messaging.isEmpty()) {
            item { EmptyState(Icons.Default.Hub, stringResource(R.string.i18n_msg_no_channels), stringResource(R.string.i18n_msg_no_channels_hint)) }
        } else {
            item { SectionLabel(stringResource(R.string.ui_0155d01cec), messaging.size.toString()) }
            items(messaging, key = { it.id }) { platform ->
                FlatRow(
                    title = platform.name,
                    description = channelDescription(platform),
                    leading = { StateDot(platform.gatewayRunning, platform.enabled && !platform.gatewayRunning) },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onTest(platform) }) { Icon(Icons.Default.NetworkPing, stringResource(R.string.ui_532eaabd95)) }
                            IconButton(onClick = { onConfigure(platform) }) { Icon(Icons.Default.Settings, stringResource(R.string.ui_6defafa2ca)) }
                            Switch(platform.enabled, { onEnable(platform, it) })
                        }
                    },
                    onClick = { onConfigure(platform) }
                )
                if (platform.errorMessage.isNotBlank()) {
                    Text(
                        platform.errorMessage,
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
            }
        }

        if (telegram != null) {
            item { SectionLabel(stringResource(R.string.ui_6a39b22944)) }
            item {
                Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        stringResource(R.string.i18n_msg_telegram_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HermesTextField(botName, onBotName, stringResource(R.string.i18n_msg_bot_name))
                    OutlinedButton(onClick = { onStartTelegram(botName) }) {
                        Icon(Icons.Default.QrCode, null)
                        Spacer(Modifier.width(7.dp))
                        Text(stringResource(R.string.ui_d351da846b))
                    }
                }
            }
        }

        if (onboarding != null) {
            item {
                Surface(
                    Modifier.fillMaxWidth().padding(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val status = onboarding.status.lowercase()
                        Text(
                            when (status) {
                                "ready" -> stringResource(R.string.i18n_msg_tg_ready)
                                "expired" -> stringResource(R.string.i18n_msg_tg_expired)
                                "failed", "error" -> stringResource(R.string.i18n_msg_tg_failed)
                                else -> stringResource(R.string.i18n_msg_tg_waiting)
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                        onboarding.botUsername?.let { Text("@$it") }
                        if (onboarding.expiresAt.isNotBlank()) Text(stringResource(R.string.i18n_msg_expires, onboarding.expiresAt), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (onboarding.deepLink.isNotBlank()) Button(onClick = { onOpenUri(onboarding.deepLink) }) { Text(stringResource(R.string.ui_b86620b43d)) }
                            if (status == "ready") Button(onClick = { onApplyTelegram(listOfNotNull(onboarding.ownerUserId).filter(String::isNotBlank)) }) { Text(stringResource(R.string.ui_31e392d1c0)) }
                            TextButton(onClick = onCancelTelegram) { Text(stringResource(R.string.ui_19766ed6cc)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun channelDescription(platform: MessagingPlatform): String = buildList {
    add(
        platform.state.ifBlank {
            when {
                platform.gatewayRunning -> stringResource(R.string.i18n_msg_connected)
                platform.enabled && platform.configured -> stringResource(R.string.i18n_msg_enabled)
                platform.configured -> stringResource(R.string.i18n_msg_configured)
                else -> stringResource(R.string.i18n_msg_not_configured)
            }
        }
    )
    if (platform.homeChannel.isNotBlank()) add(platform.homeChannel)
    if (platform.description.isNotBlank()) add(platform.description)
}.filter(String::isNotBlank).joinToString(" · ")

@Composable
private fun PlatformConfigDialog(
    platform: MessagingPlatform,
    onDismiss: () -> Unit,
    onOpenDocs: () -> Unit,
    onSave: (Map<String, String>, List<String>) -> Unit
) {
    val tokenLabel = stringResource(R.string.i18n_msg_bot_token)
    val fields = remember(platform) {
        if (platform.setupFields.isNotEmpty()) platform.setupFields
        else if (platform.id == "telegram") listOf(
            MessagingSetupField("TELEGRAM_BOT_TOKEN", tokenLabel, required = true, secret = true, configured = platform.configured)
        ) else emptyList()
    }
    val values = remember(platform.id) { mutableStateMapOf<String, String>().apply { fields.forEach { put(it.key, "") } } }
    val clear = remember(platform.id) { mutableStateMapOf<String, Boolean>().apply { fields.forEach { put(it.key, false) } } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.i18n_msg_configure, platform.name)) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 570.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (platform.description.isNotBlank()) Text(platform.description, style = MaterialTheme.typography.bodyMedium)
                if (fields.isEmpty()) {
                    Text(
                        stringResource(R.string.i18n_msg_no_fields),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                fields.forEach { field ->
                    HermesTextField(
                        value = values[field.key].orEmpty(),
                        onValueChange = { values[field.key] = it; if (it.isNotBlank()) clear[field.key] = false },
                        label = field.label + if (field.required) " *" else "",
                        placeholder = when {
                            field.configured && field.secret -> stringResource(R.string.i18n_msg_secret_kept)
                            field.valueHint.isNotBlank() -> field.valueHint
                            else -> field.placeholder
                        },
                        password = field.secret,
                        singleLine = field.type.lowercase() !in setOf("textarea", "multiline")
                    )
                    if (field.description.isNotBlank()) Text(field.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (field.configured) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(clear[field.key] == true, { checked -> clear[field.key] = checked; if (checked) values[field.key] = "" })
                            Text(stringResource(R.string.ui_06bf4d4cfd), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (platform.docsUrl.isNotBlank()) {
                    TextButton(onClick = onOpenDocs) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ui_f91058b1df))
                    }
                }
                Text(
                    stringResource(R.string.i18n_msg_blank_keeps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = fields.isNotEmpty(),
                onClick = {
                    onSave(
                        values.toMap().filterValues(String::isNotBlank),
                        clear.filterValues { it }.keys.toList()
                    )
                }
            ) { Text(stringResource(R.string.ui_1509f561f2)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_19766ed6cc)) } }
    )
}

@Composable
private fun PairingList(
    pending: List<PairingUser>,
    approved: List<PairingUser>,
    onApprove: (PairingUser) -> Unit,
    onRevoke: (PairingUser) -> Unit,
    onClearPending: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            SectionLabel(stringResource(R.string.ui_f83c8b8c32), pending.size.toString())
            if (pending.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClearPending) { Text(stringResource(R.string.ui_e587c75da3)) }
                }
            }
        }
        if (pending.isEmpty()) {
            item { Text(stringResource(R.string.ui_07a3b360eb), Modifier.padding(horizontal = 18.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(pending, key = { "${it.platform}:${it.code ?: it.requestId ?: it.userId}" }) { user ->
                FlatRow(
                    title = user.userName.ifBlank { user.userId.ifBlank { user.code ?: stringResource(R.string.i18n_msg_pairing_request) } },
                    description = listOf(user.platform, user.code?.let { stringResource(R.string.i18n_msg_code, it) }.orEmpty(), user.createdAt).filter(String::isNotBlank).joinToString(" · "),
                    trailing = { Button(onClick = { onApprove(user) }) { Text(stringResource(R.string.ui_6007acbe30)) } }
                )
            }
        }

        item { SectionLabel(stringResource(R.string.ui_a418b2c858), approved.size.toString()) }
        if (approved.isEmpty()) {
            item { Text(stringResource(R.string.ui_c923a1a16a), Modifier.padding(horizontal = 18.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(approved, key = { "${it.platform}:${it.userId}" }) { user ->
                FlatRow(
                    title = user.userName.ifBlank { user.userId },
                    description = listOf(user.platform, user.userId).filter(String::isNotBlank).joinToString(" · "),
                    trailing = { IconButton(onClick = { onRevoke(user) }) { Icon(Icons.Default.PersonRemove, stringResource(R.string.i18n_msg_revoke)) } }
                )
            }
        }
    }
}

@Composable
private fun WebhooksList(
    webhooks: WebhookState,
    onCreate: () -> Unit,
    onEnabled: (WebhookRoute, Boolean) -> Unit,
    onDelete: (WebhookRoute) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            // A tab of this page: its own small bar, not a second page header.
            com.nousresearch.hermesmobile.ui.components.SubBar(
                stringResource(if (webhooks.platformEnabled) R.string.i18n_wh_enabled else R.string.i18n_wh_enable_first)
            ) { IconButton(onClick = onCreate) { Icon(Icons.Default.Add, stringResource(R.string.ui_d58b5d6489)) } }
        }
        if (!webhooks.platformEnabled) {
            item {
                Text(
                    stringResource(R.string.i18n_wh_rejected),
                    Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (webhooks.routes.isEmpty()) {
            item { EmptyState(Icons.Default.Webhook, "No webhook routes", "Create an event-driven subscription on the remote gateway.") { Button(onClick = onCreate) { Text(stringResource(R.string.ui_d58b5d6489)) } } }
        } else {
            items(webhooks.routes, key = { it.name }) { route ->
                FlatRow(
                    title = route.name,
                    description = buildList {
                        if (route.events.isNotEmpty()) add(route.events.joinToString(", "))
                        if (route.deliver.isNotBlank()) add(route.deliver)
                        if (route.description.isNotBlank()) add(route.description)
                    }.joinToString(" · "),
                    leading = { StateDot(route.enabled) },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(route.enabled, { onEnabled(route, it) })
                            IconButton(onClick = { onDelete(route) }) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.ui_e2d0a54968)) }
                        }
                    }
                )
                if (route.url.isNotBlank()) Text(route.url, Modifier.padding(horizontal = 48.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun WebhookCreateDialog(onDismiss: () -> Unit, onCreate: (WebhookDraft) -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var events by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var deliver by remember { mutableStateOf("log") }
    var skills by remember { mutableStateOf("") }
    var cronJob by remember { mutableStateOf("") }
    var deliverOnly by remember { mutableStateOf(false) }
    val directDeliveryNeedsTarget = deliverOnly && deliver.trim().equals("log", ignoreCase = true)
    val conflictingModes = deliverOnly && cronJob.isNotBlank()
    val hasExecutionMode = prompt.isNotBlank() || deliverOnly || cronJob.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_d58b5d6489)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                HermesTextField(name, { name = it }, stringResource(R.string.i18n_wh_route))
                HermesTextField(description, { description = it }, stringResource(R.string.i18n_description))
                HermesTextField(events, { events = it }, stringResource(R.string.i18n_wh_events), placeholder = stringResource(R.string.i18n_wh_events_hint))
                HermesTextField(prompt, { prompt = it }, stringResource(R.string.i18n_wh_prompt), singleLine = false, placeholder = stringResource(R.string.i18n_wh_prompt_hint))
                HermesTextField(deliver, { deliver = it }, stringResource(R.string.i18n_wh_target), placeholder = "log / telegram / discord / …")
                HermesTextField(skills, { skills = it }, stringResource(R.string.i18n_skills), placeholder = stringResource(R.string.i18n_wh_skills_hint))
                HermesTextField(cronJob, { cronJob = it }, stringResource(R.string.i18n_wh_cron), placeholder = stringResource(R.string.i18n_wh_cron_hint))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(deliverOnly, { deliverOnly = it })
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(stringResource(R.string.ui_cfc53dbaa8))
                        Text(stringResource(R.string.ui_bac1ed1023), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (directDeliveryNeedsTarget) {
                    Text(
                        stringResource(R.string.i18n_wh_direct_needs_target),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (conflictingModes) {
                    Text(
                        stringResource(R.string.i18n_wh_exclusive),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(stringResource(R.string.ui_a43a4b5cf9), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && hasExecutionMode && !directDeliveryNeedsTarget && !conflictingModes,
                onClick = {
                    onCreate(
                        WebhookDraft(
                            name = name.trim(),
                            description = description.trim(),
                            events = events.split(',').map(String::trim).filter(String::isNotBlank),
                            prompt = prompt.trim(),
                            deliver = deliver.trim().ifBlank { "log" },
                            deliverOnly = deliverOnly,
                            skills = skills.split(',').map(String::trim).filter(String::isNotBlank),
                            cronJob = cronJob.trim()
                        )
                    )
                }
            ) { Text(stringResource(R.string.ui_4759498ac2)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_19766ed6cc)) } }
    )
}

@Composable
private fun WebhookReceiptDialog(receipt: WebhookReceipt, onDismiss: () -> Unit) {
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_20bf63f7b4)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.ui_7b37fa812c))
                if (receipt.url.isNotBlank()) {
                    Text(stringResource(R.string.ui_e7a241deba), style = MaterialTheme.typography.labelMedium)
                    Text(receipt.url, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { clipboardScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Hermes",receipt.url))) } }) { Text(stringResource(R.string.ui_b26d1037f1)) }
                }
                if (receipt.secret.isNotBlank()) {
                    Text(stringResource(R.string.ui_c54f0d2c9f), style = MaterialTheme.typography.labelMedium)
                    Text(receipt.secret, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { clipboardScope.launch { val clip=ClipData.newPlainText("Hermes",receipt.secret); clip.description.extras=android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE",true) }; clipboard.setClipEntry(ClipEntry(clip)) } }) { Text(stringResource(R.string.ui_9b80d4b38e)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_11a6767d56)) } }
    )
}

private fun openSafeUri(context: android.content.Context, raw: String) {
    if (raw.isBlank()) return
    runCatching {
        val uri = Uri.parse(raw)
        if (uri.scheme?.lowercase() in setOf("https", "tg")) context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
