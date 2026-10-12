package com.nousresearch.hermesmobile.ui.screens

import com.nousresearch.hermesmobile.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nousresearch.hermesmobile.data.*
import com.nousresearch.hermesmobile.ui.components.*
import com.nousresearch.hermesmobile.ui.theme.hermes

enum class CapabilitiesTab(val title: String) {
    MODELS("Models"), SKILLS("Skills"), TOOLS("Tools"), MCP("MCP"), KEYS("Keys")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapabilitiesScreen(
    profiles: List<HermesProfile>,
    profile: String,
    snapshot: CapabilitySnapshot?,
    loading: Boolean,
    actionBusy: String?,
    skillSearchResults: List<CapabilitySkill>,
    skillDetail: CapabilitySkillDocument?,
    toolsetConfig: CapabilityToolsetConfig?,
    oauthFlow: CapabilityOAuthFlow?,
    onBack: () -> Unit,
    onProfile: (String) -> Unit,
    onRefresh: () -> Unit,
    onSetModel: (String, String) -> Unit,
    onSkillEnabled: (CapabilitySkill, Boolean) -> Unit,
    onSkillOpen: (CapabilitySkill) -> Unit,
    onSkillClose: () -> Unit,
    onSkillSearch: (String) -> Unit,
    onInstallSkill: (CapabilitySkill) -> Unit,
    onUninstallSkill: (CapabilitySkill) -> Unit,
    onUpdateSkills: () -> Unit,
    onToolsetEnabled: (CapabilityToolset, Boolean) -> Unit,
    onToolsetConfig: (CapabilityToolset) -> Unit,
    onCloseToolsetConfig: () -> Unit,
    onSelectToolProvider: (String, String) -> Unit,
    onSaveToolEnv: (String, Map<String, String>) -> Unit,
    onAddMcp: (String, String, String, List<String>) -> Unit,
    onRemoveMcp: (CapabilityMcpServer) -> Unit,
    onMcpEnabled: (CapabilityMcpServer, Boolean) -> Unit,
    onTestMcp: (CapabilityMcpServer) -> Unit,
    onInstallMcpCatalog: (CapabilityMcpCatalogEntry, Map<String, String>) -> Unit,
    onSetEnv: (CapabilityEnvVar, String) -> Unit,
    onDeleteEnv: (CapabilityEnvVar) -> Unit,
    onSaveCustomEndpoint: (String?, String, String, String, String, String, Boolean) -> Unit,
    onActivateCustomEndpoint: (CapabilityCustomEndpoint) -> Unit,
    onDeleteCustomEndpoint: (CapabilityCustomEndpoint) -> Unit,
    onStartOAuth: (CapabilityOAuthProvider) -> Unit,
    onDisconnectOAuth: (CapabilityOAuthProvider) -> Unit,
    onSubmitOAuthCode: (String) -> Unit,
    onDismissOAuth: () -> Unit
) {
    var tab by remember { mutableStateOf(CapabilitiesTab.MODELS) }
    var profileMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        HermesTopBar(
            title = stringResource(R.string.ui_9460f16ac9),
            subtitle = snapshot?.let { "${it.profile} · remote gateway" } ?: profile,
            leading = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Default.Refresh, "Refresh") }
            }
        )
        DashedDivider(color = MaterialTheme.hermes.line)
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
            OutlinedButton(onClick = { profileMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AccountCircle, null)
                Spacer(Modifier.width(8.dp))
                Text(profiles.firstOrNull { it.name == profile }?.label ?: profile, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                profiles.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p.label) },
                        onClick = { profileMenu = false; onProfile(p.name) },
                        leadingIcon = { if (p.name == profile) Icon(Icons.Default.Check, null) }
                    )
                }
            }
        }
        SecondaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 8.dp) {
            CapabilitiesTab.entries.forEach { item ->
                Tab(selected = tab == item, onClick = { tab = item }, text = { Text(item.title) })
            }
        }
        if (loading && snapshot == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        when (tab) {
            CapabilitiesTab.MODELS -> ModelsPane(snapshot, actionBusy, onSetModel, onSaveCustomEndpoint, onActivateCustomEndpoint, onDeleteCustomEndpoint, onStartOAuth, onDisconnectOAuth)
            CapabilitiesTab.SKILLS -> SkillsPane(snapshot, actionBusy, skillSearchResults, onSkillEnabled, onSkillOpen, onSkillSearch, onInstallSkill, onUninstallSkill, onUpdateSkills)
            CapabilitiesTab.TOOLS -> ToolsPane(snapshot, actionBusy, onToolsetEnabled, onToolsetConfig)
            CapabilitiesTab.MCP -> McpPane(snapshot, actionBusy, onAddMcp, onRemoveMcp, onMcpEnabled, onTestMcp, onInstallMcpCatalog)
            CapabilitiesTab.KEYS -> KeysPane(snapshot, actionBusy, onSetEnv, onDeleteEnv)
        }
    }

    skillDetail?.let { SkillDetailDialog(it, onSkillClose) }
    toolsetConfig?.let {
        ToolsetConfigDialog(it, actionBusy, onCloseToolsetConfig, onSelectToolProvider, onSaveToolEnv)
    }
    oauthFlow?.let {
        OAuthCodeDialog(it, onSubmitOAuthCode, onDismissOAuth)
    }
}

@Composable
private fun ModelsPane(
    snapshot: CapabilitySnapshot?,
    busy: String?,
    onSetModel: (String, String) -> Unit,
    onSaveCustomEndpoint: (String?, String, String, String, String, String, Boolean) -> Unit,
    onActivateCustomEndpoint: (CapabilityCustomEndpoint) -> Unit,
    onDeleteCustomEndpoint: (CapabilityCustomEndpoint) -> Unit,
    onStartOAuth: (CapabilityOAuthProvider) -> Unit,
    onDisconnectOAuth: (CapabilityOAuthProvider) -> Unit
) {
    var modelDialog by remember { mutableStateOf(false) }
    var endpointDialog by remember { mutableStateOf<CapabilityCustomEndpoint?>(null) }
    var newEndpoint by remember { mutableStateOf(false) }
    val s = snapshot
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            SectionLabel(stringResource(R.string.ui_3840d9d294))
            if (s == null || "models" in s.unsupported) {
                FlatRow("Model settings unavailable", "This gateway does not expose the current model settings API.")
            } else {
                FlatRow(
                    title = s.modelInfo.model.ifBlank { "Not selected" },
                    description = listOfNotNull(
                        s.modelInfo.provider.takeIf(String::isNotBlank),
                        s.modelInfo.effectiveContextLength?.let { "$it context" }
                    ).joinToString(" · "),
                    leading = { Icon(Icons.Default.Psychology, null) },
                    trailing = { TextButton(onClick = { modelDialog = true }) { Text(stringResource(R.string.ui_c0bf75bd78)) } }
                )
                val caps = buildList {
                    if (s.modelInfo.supportsTools == true) add("tools")
                    if (s.modelInfo.supportsVision == true) add("vision")
                    if (s.modelInfo.supportsReasoning == true) add("reasoning")
                }
                if (caps.isNotEmpty()) FlatRow("Capabilities", caps.joinToString(" · "))
            }
            SectionLabel(stringResource(R.string.ui_a701acdcc0))
        }
        if (s != null && "oauth" !in s.unsupported) {
            items(s.oauthProviders, key = { it.id }) { provider ->
                FlatRow(
                    title = provider.name,
                    description = when {
                        provider.loggedIn -> provider.account.ifBlank { "Connected" }
                        provider.flow == "device_code" -> "Device-code sign-in · use API key or Desktop/CLI on current gateways"
                        provider.flow == "external" -> "Authentication delegated to gateway CLI"
                        else -> "${provider.flow.ifBlank { "OAuth" }} account"
                    },
                    leading = { StateDot(provider.loggedIn, !provider.loggedIn) },
                    trailing = {
                        if (provider.loggedIn) {
                            TextButton(onClick = { onDisconnectOAuth(provider) }, enabled = busy == null) { Text(stringResource(R.string.ui_acfc5be785)) }
                        } else {
                            TextButton(onClick = { onStartOAuth(provider) }, enabled = busy == null) { Text(stringResource(R.string.ui_1a2303ede0)) }
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
            }
        }
        item {
            SectionLabel(stringResource(R.string.ui_693550d642), s?.customEndpoints?.size?.toString())
            FlatRow(
                "Add custom endpoint",
                "OpenAI-compatible, Anthropic Messages or Codex Responses endpoint",
                leading = { Icon(Icons.Default.AddLink, null) },
                onClick = { endpointDialog = null; newEndpoint = true }
            )
        }
        if (s != null) {
            items(s.customEndpoints, key = { it.id }) { endpoint ->
                FlatRow(
                    title = endpoint.name,
                    description = "${endpoint.baseUrl} · ${endpoint.model}" + if (endpoint.current) " · active" else "",
                    leading = { StateDot(endpoint.current, endpoint.hasApiKey) },
                    trailing = {
                        Row {
                            if (!endpoint.current) TextButton(onClick = { onActivateCustomEndpoint(endpoint) }, enabled = busy == null) { Text(stringResource(R.string.ui_c36d819e7b)) }
                            IconButton(onClick = { endpointDialog = endpoint; newEndpoint = true }, enabled = busy == null) { Icon(Icons.Default.Edit, stringResource(R.string.ui_464c4ffd01)) }
                            IconButton(onClick = { onDeleteCustomEndpoint(endpoint) }, enabled = busy == null) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.ui_e2d0a54968)) }
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
            }
        }
    }

    if (modelDialog && s != null) {
        ModelPickerDialog(s, onDismiss = { modelDialog = false }) { provider, model ->
            modelDialog = false
            onSetModel(provider, model)
        }
    }
    if (newEndpoint) {
        CustomEndpointDialog(endpointDialog, onDismiss = { newEndpoint = false }) { id, name, base, model, key, mode, makeDefault ->
            newEndpoint = false
            onSaveCustomEndpoint(id, name, base, model, key, mode, makeDefault)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerDialog(snapshot: CapabilitySnapshot, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.a14_model), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.a14_profile_scope, snapshot.profile), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.a14_models_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                snapshot.modelProviders.forEach { provider ->
                    val rows = provider.models.filter { it.contains(query, true) || provider.name.contains(query, true) }
                    if (rows.isNotEmpty()) item { SectionLabel(provider.name.ifBlank { provider.slug }, rows.size.toString()) }
                    items(rows) { model ->
                        FlatRow(model, leading = { RadioButton(snapshot.modelInfo.provider == provider.slug && snapshot.modelInfo.model == model, onClick = null) }, onClick = { onSave(provider.slug, model) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomEndpointDialog(
    existing: CapabilityCustomEndpoint?,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, String, String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var base by remember { mutableStateOf(existing?.baseUrl.orEmpty()) }
    var model by remember { mutableStateOf(existing?.model.orEmpty()) }
    var apiKey by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(existing?.apiMode.orEmpty()) }
    var makeDefault by remember { mutableStateOf(existing?.current ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add endpoint" else "Edit endpoint") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HermesTextField(name, { name = it }, "Name")
                HermesTextField(base, { base = it }, "Base URL", placeholder = "http://host:port/v1")
                HermesTextField(model, { model = it }, "Model")
                HermesTextField(apiKey, { apiKey = it }, if (existing?.hasApiKey == true) "API key (blank keeps existing)" else "API key", password = true)
                HermesTextField(mode, { mode = it }, "API mode", placeholder = "auto / chat_completions / anthropic_messages")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(makeDefault, { makeDefault = it })
                    Text(stringResource(R.string.ui_cc7d24bc8d))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(existing?.id, name, base, model, apiKey, mode, makeDefault) }) { Text(stringResource(R.string.ui_e433ad097d)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_19766ed6cc)) } }
    )
}

@Composable
private fun SkillsPane(
    snapshot: CapabilitySnapshot?,
    busy: String?,
    searchResults: List<CapabilitySkill>,
    onEnabled: (CapabilitySkill, Boolean) -> Unit,
    onOpen: (CapabilitySkill) -> Unit,
    onSearch: (String) -> Unit,
    onInstall: (CapabilitySkill) -> Unit,
    onUninstall: (CapabilitySkill) -> Unit,
    onUpdate: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var browse by remember { mutableStateOf(false) }
    val installedNames = snapshot?.skills?.mapTo(mutableSetOf()) { it.name }.orEmpty()
    val browseRows = if (query.isBlank()) snapshot?.officialSkills.orEmpty().filterNot { it.name in installedNames } else searchResults.filterNot { it.name in installedNames }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !browse, onClick = { browse = false }, label = { Text(stringResource(R.string.ui_f8b32f4e92)) })
                FilterChip(selected = browse, onClick = { browse = true }, label = { Text(stringResource(R.string.ui_3227aa9666)) })
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onUpdate, enabled = busy == null) { Icon(Icons.Default.SystemUpdateAlt, "Update skills") }
            }
        }
        if (!browse) {
            item { SectionLabel(stringResource(R.string.ui_f8b32f4e92), snapshot?.skills?.size?.toString()) }
            items(snapshot?.skills.orEmpty(), key = { it.name }) { skill ->
                FlatRow(
                    skill.name,
                    skill.description.ifBlank { skill.category },
                    leading = { Icon(Icons.Default.Extension, null) },
                    onClick = { onOpen(skill) },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(skill.enabled, { onEnabled(skill, it) }, enabled = busy == null)
                            if (skill.provenance == "hub") IconButton(onClick = { onUninstall(skill) }, enabled = busy == null) { Icon(Icons.Default.DeleteOutline, "Uninstall") }
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
            }
        } else {
            item {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    HermesTextField(query, { query = it; onSearch(it) }, "Search Skills Hub", placeholder = "github, browser, docs…")
                }
                SectionLabel(stringResource(R.string.ui_e674447337), browseRows.size.toString())
            }
            items(browseRows, key = { it.identifier.ifBlank { it.name } }) { skill ->
                FlatRow(
                    skill.name,
                    skill.description.ifBlank { skill.tags.joinToString(" · ") },
                    leading = { Icon(Icons.Default.Download, null) },
                    trailing = { TextButton(onClick = { onInstall(skill) }, enabled = busy == null) { Text(stringResource(R.string.ui_569ca49f4a)) } }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
            }
        }
    }
}

@Composable
private fun SkillDetailDialog(document: CapabilitySkillDocument, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(document.name) },
        text = {
            LazyColumn(Modifier.heightIn(max = 560.dp)) {
                if (document.path.isNotBlank()) {
                    item { Text(document.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item { Spacer(Modifier.height(10.dp)) }
                }
                item { Text(document.content.ifBlank { "No SKILL.md content returned by this gateway." }, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_11a6767d56)) } }
    )
}

@Composable
private fun ToolsPane(snapshot: CapabilitySnapshot?, busy: String?, onEnabled: (CapabilityToolset, Boolean) -> Unit, onConfig: (CapabilityToolset) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { SectionLabel(stringResource(R.string.ui_d2d6b12006), snapshot?.toolsets?.size?.toString()) }
        items(snapshot?.toolsets.orEmpty(), key = { it.name }) { toolset ->
            FlatRow(
                title = toolset.label.ifBlank { toolset.name },
                description = toolset.description.ifBlank { toolset.tools.joinToString(" · ") },
                leading = { StateDot(toolset.enabled && toolset.configured, toolset.enabled && !toolset.configured) },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onConfig(toolset) }, enabled = busy == null) { Icon(Icons.Default.Settings, stringResource(R.string.ui_6defafa2ca)) }
                        Switch(toolset.enabled, { onEnabled(toolset, it) }, enabled = busy == null)
                    }
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
        }
    }
}

@Composable
private fun ToolsetConfigDialog(
    config: CapabilityToolsetConfig,
    busy: String?,
    onDismiss: () -> Unit,
    onProvider: (String, String) -> Unit,
    onSaveEnv: (String, Map<String, String>) -> Unit
) {
    val secrets = remember(config.name, config.activeProvider) { mutableStateMapOf<String, String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure ${config.name}") },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(config.providers, key = { it.name }) { provider ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = provider.name == config.activeProvider || provider.active, onClick = { onProvider(config.name, provider.name) }, enabled = busy == null)
                            Column(Modifier.weight(1f)) {
                                Text(provider.badge.ifBlank { provider.name }, fontWeight = FontWeight.Medium)
                                Text(provider.status.ifBlank { provider.tag }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        provider.env.forEach { field ->
                            HermesTextField(
                                value = secrets[field.key].orEmpty(),
                                onValueChange = { secrets[field.key] = it },
                                label = if (field.isSet) "${field.prompt.ifBlank { field.key }} (set)" else field.prompt.ifBlank { field.key },
                                password = true
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        if (provider.env.any { secrets[it.key].orEmpty().isNotBlank() }) {
                            TextButton(onClick = { onSaveEnv(config.name, secrets.toMap()) }, enabled = busy == null) { Text(stringResource(R.string.ui_f412ebc3df)) }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .2f))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_11a6767d56)) } }
    )
}

@Composable
private fun McpPane(
    snapshot: CapabilitySnapshot?,
    busy: String?,
    onAdd: (String, String, String, List<String>) -> Unit,
    onRemove: (CapabilityMcpServer) -> Unit,
    onEnabled: (CapabilityMcpServer, Boolean) -> Unit,
    onTest: (CapabilityMcpServer) -> Unit,
    onInstallCatalog: (CapabilityMcpCatalogEntry, Map<String, String>) -> Unit
) {
    var addDialog by remember { mutableStateOf(false) }
    var installEntry by remember { mutableStateOf<CapabilityMcpCatalogEntry?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            SectionLabel(stringResource(R.string.ui_84aebc69a1), snapshot?.mcpServers?.size?.toString())
            FlatRow("Add MCP server", "HTTP/SSE URL or stdio command", leading = { Icon(Icons.Default.Add, null) }, onClick = { addDialog = true })
        }
        items(snapshot?.mcpServers.orEmpty(), key = { it.name }) { server ->
            FlatRow(
                server.name,
                listOf(server.transport, server.url.ifBlank { server.command }, "${server.tools.size} tools").filter(String::isNotBlank).joinToString(" · "),
                leading = { StateDot(server.enabled) },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onTest(server) }, enabled = busy == null) { Text(stringResource(R.string.ui_532eaabd95)) }
                        Switch(server.enabled, { onEnabled(server, it) }, enabled = busy == null)
                        IconButton(onClick = { onRemove(server) }, enabled = busy == null) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.ui_c3812fc4ac)) }
                    }
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
        }
        item { SectionLabel(stringResource(R.string.ui_3877d14889), snapshot?.mcpCatalog?.count { !it.installed }?.toString()) }
        items(snapshot?.mcpCatalog.orEmpty().filterNot { it.installed }, key = { it.name }) { entry ->
            FlatRow(
                entry.name,
                entry.description.ifBlank { listOf(entry.transport, entry.authType).filter(String::isNotBlank).joinToString(" · ") },
                leading = { Icon(Icons.Default.Hub, null) },
                trailing = { TextButton(onClick = { installEntry = entry }, enabled = busy == null) { Text(stringResource(R.string.ui_569ca49f4a)) } }
            )
        }
    }
    if (addDialog) AddMcpDialog(onDismiss = { addDialog = false }) { name, url, command, args -> addDialog = false; onAdd(name, url, command, args) }
    installEntry?.let { entry -> McpCatalogInstallDialog(entry, onDismiss = { installEntry = null }) { env -> installEntry = null; onInstallCatalog(entry, env) } }
}

@Composable
private fun AddMcpDialog(onDismiss: () -> Unit, onAdd: (String, String, String, List<String>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var args by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_0e3e58f90d)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HermesTextField(name, { name = it }, "Name")
            HermesTextField(url, { url = it }, "HTTP URL", placeholder = "https://…")
            Text(stringResource(R.string.ui_7175517a37), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HermesTextField(command, { command = it }, "stdio command", placeholder = "npx / uvx / binary")
            HermesTextField(args, { args = it }, "Arguments", placeholder = "space-separated")
        } },
        confirmButton = { TextButton(onClick = { onAdd(name, url, command, splitArgs(args)) }) { Text(stringResource(R.string.ui_9fd728c66c)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_19766ed6cc)) } }
    )
}

private fun splitArgs(raw: String): List<String> = raw.trim().split(Regex("\\s+")).filter(String::isNotBlank)

@Composable
private fun McpCatalogInstallDialog(entry: CapabilityMcpCatalogEntry, onDismiss: () -> Unit, onInstall: (Map<String, String>) -> Unit) {
    val env = remember(entry.name) { mutableStateMapOf<String, String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Install ${entry.name}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(entry.description)
            entry.requiredEnv.forEach { key -> HermesTextField(env[key].orEmpty(), { env[key] = it }, key, password = true) }
            if (entry.requiredEnv.isEmpty()) Text(stringResource(R.string.ui_a2167afadd), style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = { onInstall(env.toMap()) }) { Text(stringResource(R.string.ui_569ca49f4a)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_19766ed6cc)) } }
    )
}

@Composable
private fun KeysPane(snapshot: CapabilitySnapshot?, busy: String?, onSet: (CapabilityEnvVar, String) -> Unit, onDelete: (CapabilityEnvVar) -> Unit) {
    var edit by remember { mutableStateOf<CapabilityEnvVar?>(null) }
    var advanced by remember { mutableStateOf(false) }
    val rows = snapshot?.envVars.orEmpty().filter { !it.channelManaged && (advanced || !it.advanced) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ui_3b22700928), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.ui_9f088dbebd))
                Switch(advanced, { advanced = it })
            }
            Text(
                "Secrets are stored only by the remote Hermes profile. Mobile never persists the value entered here.",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(rows, key = { it.key }) { variable ->
            FlatRow(
                title = variable.providerLabel.ifBlank { variable.key },
                description = listOf(variable.key, variable.redactedValue.takeIf { variable.isSet && it.isNotBlank() }, variable.description).filterNotNull().filter(String::isNotBlank).joinToString(" · "),
                leading = { StateDot(variable.isSet) },
                trailing = {
                    Row {
                        TextButton(onClick = { edit = variable }, enabled = busy == null) { Text(if (variable.isSet) "Replace" else "Set") }
                        if (variable.isSet) IconButton(onClick = { onDelete(variable) }, enabled = busy == null) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.ui_c3812fc4ac)) }
                    }
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
        }
    }
    edit?.let { variable ->
        var value by remember(variable.key) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(variable.providerLabel.ifBlank { variable.key }) },
            text = { Column {
                Text(variable.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                HermesTextField(value, { value = it }, variable.key, password = true)
            } },
            confirmButton = { TextButton(onClick = { edit = null; onSet(variable, value) }, enabled = value.isNotBlank()) { Text(stringResource(R.string.ui_1509f561f2)) } },
            dismissButton = { TextButton(onClick = { edit = null }) { Text(stringResource(R.string.ui_19766ed6cc)) } }
        )
    }
}

@Composable
private fun OAuthCodeDialog(flow: CapabilityOAuthFlow, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember(flow.sessionId) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_fb7084b712)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ui_0d6ddd6125))
            HermesTextField(code, { code = it }, "Authorization code")
        } },
        confirmButton = { TextButton(onClick = { onSubmit(code) }, enabled = code.isNotBlank()) { Text(stringResource(R.string.ui_1a2303ede0)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_7d9eb7acb1)) } }
    )
}
