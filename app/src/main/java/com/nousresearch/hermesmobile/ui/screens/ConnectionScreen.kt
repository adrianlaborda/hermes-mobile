package com.nousresearch.hermesmobile.ui.screens

import com.nousresearch.hermesmobile.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nousresearch.hermesmobile.data.*
import com.nousresearch.hermesmobile.ui.components.*
import com.nousresearch.hermesmobile.ui.theme.hermes

@Composable
fun ConnectionScreen(
    connections: List<GatewayConfig>,
    activeId: String?,
    discovery: GatewayDiscovery?,
    busy: Boolean,
    error: String?,
    onDiscover: (String, String, Map<String, String>) -> Unit,
    onConnectSaved: (String) -> Unit,
    onForget: (String) -> Unit,
    onPassword: (String, String, String?) -> Unit,
    onNative: (String?) -> Unit,
    onSessionToken: (String) -> Unit,
    onNoAuth: () -> Unit,
    onCancelSetup: () -> Unit
) {
    var label by remember(discovery?.draft?.id) { mutableStateOf(discovery?.draft?.label.orEmpty()) }
    var url by remember(discovery?.draft?.id) { mutableStateOf(discovery?.draft?.baseUrl.orEmpty()) }
    var headers by remember(discovery?.draft?.id) { mutableStateOf(discovery?.draft?.extraHeaders?.entries?.joinToString("\n") { "${it.key}: ${it.value}" }.orEmpty()) }
    var username by remember(discovery?.draft?.id) { mutableStateOf("") }
    var password by remember(discovery?.draft?.id) { mutableStateOf("") }
    var sessionToken by remember(discovery?.draft?.id) { mutableStateOf("") }
    var advanced by remember(discovery?.draft?.id) { mutableStateOf(false) }
    var connectionAdvanced by remember(discovery?.draft?.id) { mutableStateOf(false) }
    var selectedProvider by remember(discovery?.draft?.id) {
        mutableStateOf(discovery?.providers?.firstOrNull()?.name.orEmpty())
    }

    var appearanceOpen by remember { mutableStateOf(false) }
    if(appearanceOpen) AlertDialog(onDismissRequest = { appearanceOpen = false },
        title = { Text(stringResource(R.string.appearance)) },
        text = { com.nousresearch.hermesmobile.ui.AppearanceSettings() },
        confirmButton = { TextButton(onClick = { appearanceOpen = false }) { Text(stringResource(R.string.close_panel)) } })
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        HermesTopBar("Hermes", stringResource(R.string.connection_mobile), leading = { HermesBrandMark() },
            actions = { IconButton(onClick = { appearanceOpen = true }) { Icon(Icons.Default.Contrast, stringResource(R.string.appearance)) } })
        DashedDivider(color = MaterialTheme.hermes.line)

        if (discovery == null) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        HermesBrandMark(size = 76)
                        Spacer(Modifier.height(18.dp))
                        Text(stringResource(R.string.connection_heading), style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.connection_intro), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                if(error != null) item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.WifiOff, null, tint = MaterialTheme.colorScheme.error)
                            Column {
                                Text(stringResource(R.string.connection_unavailable), style = MaterialTheme.typography.titleSmall)
                                Text(error, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (connections.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.ui_0c667864ef)) }
                    items(connections, key = { it.id }) { connection ->
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, tonalElevation = 0.dp) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Dns, null)
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(connection.displayLabel, style = MaterialTheme.typography.titleMedium)
                                        if (connection.id == activeId) AssistChip(onClick = {}, label = { Text(stringResource(R.string.ui_eb970eb095)) })
                                    }
                                    Text(connection.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        authLabel(connection.authMode, connection.authProvider),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { onConnectSaved(connection.id) }, enabled = !busy) { Text(stringResource(R.string.ui_1a2303ede0)) }
                                IconButton(onClick = { onForget(connection.id) }, enabled = !busy) {
                                    Icon(Icons.Default.DeleteOutline, stringResource(R.string.ui_c3812fc4ac))
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .25f))
                    }
                    item { Spacer(Modifier.height(8.dp)); SectionLabel(stringResource(R.string.ui_685f88ae3d)) }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HermesTextField(
                            url,
                            { url = it },
                            "Gateway",
                            placeholder = "https://hermes.example.com",
                            imeAction = ImeAction.Done
                        )
                        Text(
                            stringResource(R.string.connection_url_help),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { connectionAdvanced = !connectionAdvanced }) {
                            Icon(if (connectionAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ui_891724b6b2))
                        }
                        if (connectionAdvanced) {
                            HermesTextField(
                                label,
                                { label = it },
                                "Connection name (optional)",
                                placeholder = "Automatically uses the gateway hostname",
                                imeAction = ImeAction.Next
                            )
                            HermesTextField(
                                headers,
                                { headers = it },
                                "Access-proxy headers (optional)",
                                placeholder = "CF-Access-Client-Id: …\nCF-Access-Client-Secret: …",
                                singleLine = false,
                                imeAction = ImeAction.Default
                            )
                            Text(
                                "Only needed when a reverse proxy such as Cloudflare Access requires extra request headers.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            enabled = url.isNotBlank() && !busy,
                            onClick = { onDiscover(label, url, parseHeaders(headers)) }
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(if (busy) R.string.connection_checking else R.string.connection_continue))
                        }
                    }
                }
            }
            return@Column
        }

        AuthStep(
            discovery = discovery,
            busy = busy,
            error = error,
            username = username,
            onUsername = { username = it },
            password = password,
            onPasswordValue = { password = it },
            sessionToken = sessionToken,
            onSessionTokenValue = { sessionToken = it },
            advanced = advanced,
            onAdvanced = { advanced = !advanced },
            selectedProvider = selectedProvider,
            onProvider = { selectedProvider = it },
            onPassword = {
                val provider = discovery.passwordProviders.firstOrNull { it.name == selectedProvider }?.name
                    ?: discovery.passwordProviders.firstOrNull()?.name
                onPassword(username, password, provider)
            },
            onNative = {
                val provider = discovery.nativeProviders.firstOrNull { it.name == selectedProvider }?.name
                    ?: discovery.nativeProviders.firstOrNull()?.name
                onNative(provider)
            },
            onSessionToken = { onSessionToken(sessionToken) },
            onNoAuth = onNoAuth,
            onBack = onCancelSetup
        )
    }
}

@Composable
private fun AuthStep(
    discovery: GatewayDiscovery,
    busy: Boolean,
    error: String?,
    username: String,
    onUsername: (String) -> Unit,
    password: String,
    onPasswordValue: (String) -> Unit,
    sessionToken: String,
    onSessionTokenValue: (String) -> Unit,
    advanced: Boolean,
    onAdvanced: () -> Unit,
    selectedProvider: String,
    onProvider: (String) -> Unit,
    onPassword: () -> Unit,
    onNative: () -> Unit,
    onSessionToken: () -> Unit,
    onNoAuth: () -> Unit,
    onBack: () -> Unit
) {
    val status = discovery.status
    val passwordProviders = discovery.passwordProviders
    val nativeProviders = discovery.nativeProviders
    val selectedPasswordProvider = passwordProviders.firstOrNull { it.name == selectedProvider }?.name
        ?: passwordProviders.firstOrNull()?.name.orEmpty()
    val selectedNativeProvider = nativeProviders.firstOrNull { it.name == selectedProvider }?.name
        ?: nativeProviders.firstOrNull()?.name.orEmpty()
    var passwordVisible by remember(discovery.draft.id) { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Column {
                    Text(discovery.draft.displayLabel, style = MaterialTheme.typography.headlineSmall)
                    Text(discovery.draft.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("Hermes ${status.version.ifBlank { "gateway" }}") })
                AssistChip(onClick = {}, label = { Text(if (status.authRequired) "Authentication required" else "Legacy/local auth") })
            }
        }

        if (status.authRequired && passwordProviders.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.ui_2ca25c14e1)) }
            if (passwordProviders.size > 1) {
                item { ProviderPicker(passwordProviders, selectedPasswordProvider, onProvider) }
            } else if (selectedProvider.isBlank()) {
                item { LaunchedEffect(passwordProviders.first().name) { onProvider(passwordProviders.first().name) } }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsername,
                        label = { Text(stringResource(R.string.ui_e3b89e9d33)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Next
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordValue,
                        label = { Text(stringResource(R.string.ui_e7cf3ef4f1)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            if (username.isNotBlank() && password.isNotBlank() && !busy) onPassword()
                        }),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    )
                    Text(
                        "Username is case-sensitive and both fields are sent exactly as typed; autocorrect and capitalization are disabled.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onPassword, enabled = username.isNotBlank() && password.isNotBlank() && !busy) {
                        Icon(Icons.AutoMirrored.Filled.Login, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (busy) "Signing in…" else "Sign in")
                    }
                    Text(
                        "The password is used only for POST /auth/password-login and is never saved. Hermes' resulting session is encrypted at rest on this device.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (status.authRequired && status.supportsNativePkce && nativeProviders.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.ui_d8a43dbfd9)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProviderPicker(nativeProviders, selectedNativeProvider, onProvider)
                    OutlinedButton(onClick = onNative, enabled = !busy && selectedNativeProvider.isNotBlank()) {
                        Icon(Icons.Default.OpenInBrowser, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ui_50e55a7524))
                    }
                    Text(
                        "Uses Hermes' native PKCE flow with an ephemeral 127.0.0.1 callback. No embedded WebView or browser cookie extraction.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (status.authRequired && passwordProviders.isEmpty() && (!status.supportsNativePkce || nativeProviders.isEmpty())) {
            item {
                Text(
                    "This gateway requires interactive authentication but does not advertise a password provider or native_pkce. Update Hermes, or use the advanced session-token path only if this older gateway supports it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!status.authRequired) {
            item { SectionLabel(stringResource(R.string.ui_26cec7c60c)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This gateway does not advertise the dashboard auth gate. Connect directly, or use the legacy Hermes session token if its /api/ws endpoint requires one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onNoAuth, enabled = !busy) { Text(stringResource(R.string.ui_1a2303ede0)) }
                }
            }
        }

        item {
            TextButton(onClick = onAdvanced) {
                Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.ui_c7b706cede))
            }
        }
        if (advanced) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HermesTextField(sessionToken, onSessionTokenValue, "Session token", password = true, imeAction = ImeAction.Done)
                    OutlinedButton(onClick = onSessionToken, enabled = sessionToken.isNotBlank() && !busy) {
                        Icon(Icons.Default.Key, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ui_53e688c0a3))
                    }
                    Text(
                        "For older/self-hosted gateways only. Auth-gated Hermes gateways normally use Basic or native PKCE and mint one-time WebSocket tickets.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item { ErrorText(error) }
    }
}

@Composable
private fun ProviderPicker(providers: List<AuthProvider>, selected: String, onSelected: (String) -> Unit) {
    if (providers.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        providers.forEach { provider ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == provider.name, onClick = { onSelected(provider.name) })
                Column {
                    Text(provider.label)
                    Text(provider.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ErrorText(error: String?) {
    if (!error.isNullOrBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

private fun authLabel(mode: GatewayAuthMode, provider: String): String = when (mode) {
    GatewayAuthMode.NONE -> "No stored Hermes credential"
    GatewayAuthMode.SESSION_TOKEN -> "Session token"
    GatewayAuthMode.BASIC -> "Username/password session${provider.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}"
    GatewayAuthMode.NATIVE_PKCE -> "Native PKCE${provider.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}"
}

private fun parseHeaders(text: String): Map<String, String> = text.lines().mapNotNull { line ->
    val i = line.indexOf(':')
    if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
}.filter { it.first.isNotBlank() && it.second.isNotBlank() }.toMap()
