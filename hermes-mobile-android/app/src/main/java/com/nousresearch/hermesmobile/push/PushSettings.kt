package com.nousresearch.hermesmobile.push

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.messaging.FirebaseMessaging
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.data.GatewayConfig
import com.nousresearch.hermesmobile.ui.components.HermesTopBar
import com.nousresearch.hermesmobile.ui.components.SectionLabel
import com.nousresearch.hermesmobile.ui.components.StatusPill
import com.nousresearch.hermesmobile.ui.theme.HermesMono
import com.nousresearch.hermesmobile.ui.theme.hermes
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Notifications for one connection + profile: a guided setup (server plugin, the user's own
 * Firebase project, server key, permission, one-tap pairing), then status, test and inbox.
 * [runCommand] calls a plugin slash command over the authenticated chat connection; its output
 * never enters the model's history, which is why secrets and the server key travel that way.
 */
@Composable
fun PushSettings(
    config: GatewayConfig?,
    profile: String,
    onBack: () -> Unit,
    onOpen: (JSONObject) -> Unit,
    onAskHermes: (String) -> Unit,
    runCommand: suspend (name: String, arg: String) -> String
) {
    val context = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    val scope = rememberCoroutineScope()
    var selectedEvent by remember { mutableStateOf<JSONObject?>(null) }
    // Drawn outside the main Scaffold: system back must close this screen, not the app.
    androidx.activity.compose.BackHandler(onBack = onBack)
    selectedEvent?.let { event ->
        AlertDialog(onDismissRequest = { selectedEvent = null }, title = { Text(event.optString("title", "Hermes")) },
            text = { Box(Modifier.heightIn(max = 500.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                com.nousresearch.hermesmobile.ui.components.MarkdownLite(event.optString("body"))
            } }, confirmButton = { TextButton(onClick = { selectedEvent = null }) { Text(stringResource(R.string.back)) } })
    }
    val store = remember { PushStore(context) }
    var accounts by remember { mutableStateOf(store.accounts()) }
    var events by remember { mutableStateOf(store.events().asReversed()) }
    val revision by PushStore.changes.collectAsState()
    LaunchedEffect(revision) { accounts = store.accounts(); events = store.events().asReversed() }
    val account = accounts.firstOrNull { it.connection == config?.id && it.profile == profile }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var allowed by remember { mutableStateOf(PushDelivery.permitted(context)) }
    var firebase by remember { mutableStateOf(FirebaseRuntime.config(context)) }
    var pairing by remember(config, profile) { mutableStateOf<ServerPairing?>(null) }
    var server by remember(config, profile) { mutableStateOf(ServerState.UNKNOWN) }
    var checking by remember(config, profile) { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }

    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; info = null
        scope.launch {
            try { block(); accounts = store.accounts(); events = store.events().asReversed() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: e.javaClass.simpleName }
            finally { busy = false }
        }
    }

    suspend fun probe(): ServerPairing? {
        checking = true
        try {
            val reply = PushSetup.parsePairing(runCommand(PushSetup.PAIR_COMMAND, ""))
            pairing = reply
            server = PushSetup.state(reply, profile)
            return reply
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            pairing = null
            // The gateway rejects an unknown command: the plugin is not installed (or not loaded yet).
            server = if ((e.message ?: "").contains("not a quick/plugin", ignoreCase = true) ||
                e.message == "not_a_pairing_reply") ServerState.PLUGIN_MISSING else ServerState.UNKNOWN
            if (server == ServerState.UNKNOWN) error = e.message
            return null
        } finally { checking = false }
    }
    LaunchedEffect(config?.id, profile, account == null) { if (config != null && account == null) probe() }

    suspend fun register(candidate: PushAccount) = withContext(Dispatchers.IO) {
        check(FirebaseRuntime.ensureInitialized(context)) { res.getString(R.string.a24_firebase_missing) }
        val fcm = FirebaseMessaging.getInstance()
        fcm.isAutoInitEnabled = true
        val token = try { com.google.android.gms.tasks.Tasks.await(fcm.token, 30, TimeUnit.SECONDS) }
            catch (e: Exception) {
                // FCM only says "FCM Registration failed"; the usual cause is the project's API key restriction.
                throw IllegalStateException(res.getString(R.string.a24_fcm_failed, context.packageName), e)
            }
        val reply = PushClient.call(candidate, "device.register", JSONObject().put("label", Build.MODEL.take(80))
            .put("push", JSONObject().put("type", "fcm").put("token", token)))
        store.save(candidate.copy(device = reply.getString("device_id"), deviceSecret = reply.getString("device_secret")))
        PushDelivery.schedule(context)
    }

    val importConfig = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) action {
            val text = readSmallText(context, uri)
            firebase = try { withContext(Dispatchers.IO) { FirebaseRuntime.import(context, text) } }
            catch (e: FirebaseConfigException) { throw IllegalStateException(firebaseError(res, e.message, context.packageName)) }
            info = res.getString(R.string.a24_firebase_imported, firebase?.projectId.orEmpty())
        }
    }
    val uploadKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) action {
            val text = readSmallText(context, uri)
            val parsed = runCatching { JSONObject(text) }.getOrNull()
            check(parsed?.optString("type") == "service_account") {
                res.getString(if (parsed?.has("project_info") == true) R.string.a24_key_is_app_config else R.string.a24_key_invalid)
            }
            val encoded = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
            val reply = PushSetup.commandReply(runCommand(PushSetup.CREDENTIALS_COMMAND, encoded))
            info = res.getString(R.string.a24_key_installed, reply.optString("project_id"))
            probe()
        }
    }

    // Edge-to-edge window and no Scaffold here: pad for the status/navigation bars and the keyboard ourselves.
    // A Surface (not just a background) so untinted text gets the theme's content color.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            HermesTopBar(stringResource(R.string.push_title), "${config?.displayLabel.orEmpty()} · $profile", leading = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            })
        }
        if (account != null) {
            item {
                PushCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(stringResource(R.string.a24_active), good = true)
                        Text(stringResource(R.string.push_enabled), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(account.endpoint, style = MaterialTheme.typography.labelSmall.copy(fontFamily = HermesMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!allowed) PermissionButton(permission, context)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(enabled = !busy, onClick = { action {
                            val reply = PushSetup.commandReply(runCommand(PushSetup.TEST_COMMAND, ""))
                            val outcomes = PushSetup.testOutcomes(reply)
                            if (outcomes.failed.isEmpty()) info = res.getQuantityString(R.plurals.a24_test_ok, outcomes.ok, outcomes.ok)
                            else error = testFailure(res, outcomes.failed.first())
                        } }) { Text(stringResource(R.string.a24_send_test)) }
                        OutlinedButton(enabled = !busy, onClick = { PushDelivery.schedule(context); events = store.events().asReversed() }) {
                            Text(stringResource(R.string.refresh))
                        }
                    }
                    TextButton(enabled = !busy, onClick = { action { withContext(Dispatchers.IO) { PushClient.call(account, "device.revoke") }; store.remove(account.key) } }) {
                        Text(stringResource(R.string.push_disable), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        } else {
            item {
                Text(stringResource(R.string.a24_intro), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                SetupStep(1, stringResource(R.string.a24_step_server), done = server == ServerState.READY) {
                    Text(serverText(res, server, pairing, profile, checking), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        askPrompt(res, server)?.let { prompt ->
                            FilledTonalButton(onClick = { onAskHermes(prompt) }) { Text(stringResource(R.string.a24_ask_hermes)) }
                        }
                        OutlinedButton(enabled = !checking && !busy && config != null, onClick = { scope.launch { error = null; probe() } }) {
                            Text(stringResource(R.string.a24_check_again))
                        }
                    }
                }
            }
            item {
                val clipboard = LocalClipboardManager.current
                SetupStep(2, stringResource(R.string.a24_step_firebase), done = firebase != null) {
                    if (firebase == null) Text(stringResource(R.string.a24_firebase_help), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (firebase == null) Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.hermes.field, border = BorderStroke(1.dp, MaterialTheme.hermes.line)) {
                        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(context.packageName, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(fontFamily = HermesMono))
                            IconButton(onClick = { clipboard.setText(AnnotatedString(context.packageName)); info = res.getString(R.string.a24_copied) }) {
                                Icon(Icons.Default.ContentCopy, stringResource(R.string.a24_copy_package), Modifier.size(18.dp))
                            }
                        }
                    }
                    firebase?.let {
                        Text(stringResource(if (FirebaseRuntime.builtIn(context) != null) R.string.a24_firebase_builtin else R.string.a24_firebase_project, it.projectId),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (FirebaseRuntime.builtIn(context) == null) {
                        OutlinedButton(enabled = !busy, onClick = { importConfig.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }) {
                            Text(stringResource(if (firebase == null) R.string.a24_import_config else R.string.a24_replace_config))
                        }
                    }
                }
            }
            item {
                val keyOk = pairing?.keyState == "ok"
                SetupStep(3, stringResource(R.string.a24_step_key), done = keyOk) {
                    if (!keyOk) Text(stringResource(R.string.a24_key_help), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (keyOk) {
                        val project = pairing?.keyProject.orEmpty()
                        Text(stringResource(R.string.a24_key_installed, project), style = MaterialTheme.typography.bodySmall)
                        if (firebase != null && project.isNotBlank() && project != firebase?.projectId) {
                            Text(stringResource(R.string.a24_key_mismatch, project, firebase?.projectId.orEmpty()),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    OutlinedButton(enabled = !busy && server != ServerState.PLUGIN_MISSING && server != ServerState.UNKNOWN,
                        onClick = { uploadKey.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }) {
                        Text(stringResource(if (keyOk) R.string.a24_replace_key else R.string.a24_upload_key))
                    }
                }
            }
            item {
                SetupStep(4, stringResource(R.string.a24_step_permission), done = allowed) {
                    if (!allowed) PermissionButton(permission, context)
                    else Text(stringResource(R.string.a24_permission_ok), style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        enabled = !busy && config != null && server == ServerState.READY && firebase != null && allowed,
                        onClick = {
                            val selected = config ?: return@Button
                            action {
                                val fresh = probe() ?: error(res.getString(R.string.a24_server_unknown))
                                check(PushSetup.state(fresh, profile) == ServerState.READY) { serverText(res, server, fresh, profile, false) }
                                val base = PushSetup.apiBase(selected.baseUrl, fresh)
                                register(PushAccount(selected.id, profile, PushClient.endpoint(base, profile), fresh.secret))
                                info = res.getString(R.string.a24_paired)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.a24_pair)) }
                    Text(stringResource(R.string.a24_pair_note), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { ManualPairing(config, profile, busy, allowed) { candidate -> action { register(candidate) } } }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                info?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            }
        }
        item { SectionLabel(stringResource(R.string.push_inbox)) }
        val inbox = events.filter { it.optString("connection") == config?.id && it.optString("profile") == profile }
        if (inbox.isEmpty()) item {
            Text(stringResource(R.string.push_empty), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(inbox, key = { it.optString("event_id") }) { event ->
            val linked = !event.isNull("session_id") && event.optString("session_id").isNotBlank()
            OutlinedCard(onClick = { if (linked) onOpen(event) else selectedEvent = event }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(event.optString("title", "Hermes"), style = MaterialTheme.typography.titleSmall)
                    Text(event.optString("body"), maxLines = 8)
                    if (linked) Text(stringResource(R.string.push_open_chat), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    }
}

@Composable
private fun PushCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.hermes.line)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun SetupStep(number: Int, title: String, done: Boolean, content: @Composable ColumnScope.() -> Unit) {
    PushCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(Modifier.size(26.dp), shape = CircleShape,
                color = if (done) MaterialTheme.hermes.greenTint else MaterialTheme.hermes.field,
                border = BorderStroke(1.dp, MaterialTheme.hermes.line)) {
                Box(contentAlignment = Alignment.Center) {
                    if (done) Icon(Icons.Default.Check, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                    else Text("$number", style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
private fun PermissionButton(permission: androidx.activity.result.ActivityResultLauncher<String>, context: Context) {
    FilledTonalButton(onClick = {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
    }) { Text(stringResource(R.string.push_permission)) }
}

/** Fallback for servers the app cannot ask (older plugin, chat connected to another host). */
@Composable
private fun ManualPairing(config: GatewayConfig?, profile: String, busy: Boolean, allowed: Boolean, onRegister: (PushAccount) -> Unit) {
    var open by remember { mutableStateOf(false) }
    // Only the manual form shows a secret: keep it out of screenshots and the recents preview.
    val context = LocalContext.current
    if (open) DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        val secureFlag = android.view.WindowManager.LayoutParams.FLAG_SECURE
        val alreadySecure = ((window?.attributes?.flags ?: 0) and secureFlag) != 0
        window?.addFlags(secureFlag)
        onDispose { if (!alreadySecure) window?.clearFlags(secureFlag) }
    }
    var base by remember(config, profile) { mutableStateOf(config?.baseUrl?.let(PushClient::suggestedBase).orEmpty()) }
    var secret by remember(config, profile) { mutableStateOf("") }
    Column(Modifier.padding(horizontal = 16.dp)) {
        TextButton(onClick = { open = !open }) { Text(stringResource(R.string.a24_manual)) }
        if (open) {
            Text(stringResource(R.string.push_explanation), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(base, { base = it }, label = { Text(stringResource(R.string.push_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(secret, { secret = it }, label = { Text(stringResource(R.string.push_secret)) },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(enabled = !busy && allowed && config != null && secret.length >= 32, onClick = {
                val selected = config ?: return@OutlinedButton
                val candidate = PushAccount(selected.id, profile, PushClient.endpoint(base, profile), secret)
                secret = ""
                onRegister(candidate)
            }) { Text(stringResource(R.string.push_enable)) }
        }
    }
}

private suspend fun readSmallText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val bytes = input.readNBytesCompat(PushSetup.MAX_KEY_BYTES + 1)
        check(bytes.size <= PushSetup.MAX_KEY_BYTES) { context.resources.getString(R.string.a24_file_too_large) }
        String(bytes, Charsets.UTF_8)
    } ?: error(context.resources.getString(R.string.a24_file_unreadable))
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

private fun testFailure(res: android.content.res.Resources, outcome: String): String = when (outcome) {
    "auth" -> res.getString(R.string.a24_test_auth)
    "permanent" -> res.getString(R.string.a24_test_permanent)
    else -> res.getString(R.string.a24_test_transient)
}

private fun firebaseError(res: android.content.res.Resources, code: String?, packageName: String): String = when (code) {
    "service_account" -> res.getString(R.string.a24_config_is_key)
    "package_missing" -> res.getString(R.string.a24_config_package, packageName)
    "incomplete" -> res.getString(R.string.a24_config_incomplete)
    else -> res.getString(R.string.a24_config_invalid)
}

private fun serverText(res: android.content.res.Resources, state: ServerState, pairing: ServerPairing?, profile: String, checking: Boolean): String = when {
    checking -> res.getString(R.string.a24_server_checking)
    else -> when (state) {
        ServerState.READY -> res.getString(R.string.a24_server_ready, pairing?.pluginVersion.orEmpty())
        ServerState.PLUGIN_MISSING -> res.getString(R.string.a24_server_plugin_missing)
        ServerState.API_SERVER_OFF -> res.getString(R.string.a24_server_api_off)
        ServerState.API_SERVER_LOOPBACK -> res.getString(R.string.a24_server_api_loopback)
        ServerState.SECRET_MISSING -> res.getString(R.string.a24_server_secret_missing)
        ServerState.WRONG_PROFILE -> res.getString(R.string.a24_server_wrong_profile, pairing?.profile.orEmpty(), profile)
        ServerState.UNKNOWN -> res.getString(R.string.a24_server_unknown)
    }
}

/** The request the user can send to Hermes for each server-side gap (prefilled, never auto-sent). */
private fun askPrompt(res: android.content.res.Resources, state: ServerState): String? = when (state) {
    ServerState.PLUGIN_MISSING -> res.getString(R.string.a24_prompt_install, PLUGIN_REPO_URL)
    ServerState.API_SERVER_OFF, ServerState.API_SERVER_LOOPBACK -> res.getString(R.string.a24_prompt_api_server)
    ServerState.SECRET_MISSING -> res.getString(R.string.a24_prompt_configure)
    else -> null
}

/** Where users (and their agent) get the server plugin. */
const val PLUGIN_REPO_URL = "https://github.com/adrianlaborda/hermes-android-plugin"
