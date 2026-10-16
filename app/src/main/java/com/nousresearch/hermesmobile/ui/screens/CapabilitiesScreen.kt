package com.nousresearch.hermesmobile.ui.screens

import com.nousresearch.hermesmobile.R
import androidx.compose.ui.res.pluralStringResource
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

enum class CapabilitiesTab(val title: Int) {
    MODELS(R.string.i18n_caps_models), SKILLS(R.string.i18n_skills), TOOLS(R.string.i18n_caps_tools), MCP(R.string.i18n_caps_mcp), KEYS(R.string.i18n_caps_keys)
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
    onMenu: () -> Unit,
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

    Column(Modifier.fillMaxSize()) {
        HermesTopBar(
            title = stringResource(R.string.ui_9460f16ac9),
            subtitleContent = { com.nousresearch.hermesmobile.ui.components.ProfileSwitcher(profile, profiles.map { it.name }, onProfile) },
            leading = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, stringResource(R.string.a21_menu)) } },
            actions = {
                IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Default.Refresh, stringResource(R.string.i18n_refresh)) }
            }
        )
        DashedDivider(color = MaterialTheme.hermes.line)
        SecondaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 8.dp) {
            CapabilitiesTab.entries.forEach { item ->
                Tab(selected = tab == item, onClick = { tab = item }, text = { Text(stringResource(item.title)) })
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
                FlatRow(stringResource(R.string.i18n_caps_model_na), stringResource(R.string.i18n_caps_model_na_hint))
            } else {
                FlatRow(
                    title = s.modelInfo.model.ifBlank { stringResource(R.string.i18n_caps_not_selected) },
                    description = listOfNotNull(
                        s.modelInfo.provider.takeIf(String::isNotBlank),
                        s.modelInfo.effectiveContextLength?.let { stringResource(R.string.i18n_caps_context, it) }
                    ).joinToString(" · "),
                    leading = { Icon(Icons.Default.Psychology, null) },
                    trailing = { TextButton(onClick = { modelDialog = true }) { Text(stringResource(R.string.ui_c0bf75bd78)) } }
                )
                val caps = buildList {
                    if (s.modelInfo.supportsTools == true) add(stringResource(R.string.i18n_caps_cap_tools))
                    if (s.modelInfo.supportsVision == true) add(stringResource(R.string.i18n_caps_cap_vision))
                    if (s.modelInfo.supportsReasoning == true) add(stringResource(R.string.i18n_caps_cap_reasoning))
                }
                if (caps.isNotEmpty()) FlatRow(stringResource(R.string.i18n_caps_capabilities), caps.joinToString(" · "))
            }
            SectionLabel(stringResource(R.string.ui_a701acdcc0))
        }
        if (s != null && "oauth" !in s.unsupported) {
            items(s.oauthProviders, key = { it.id }) { provider ->
                FlatRow(
                    title = provider.name,
                    description = when {
                        provider.loggedIn -> provider.account.ifBlank { stringResource(R.string.i18n_connected) }
                        provider.flow == "device_code" -> stringResource(R.string.i18n_caps_device_code)
                        provider.flow == "external" -> stringResource(R.string.i18n_caps_external)
                        else -> stringResource(R.string.i18n_caps_account, provider.flow.ifBlank { "OAuth" })
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
                stringResource(R.string.i18n_caps_add_endpoint),
                stringResource(R.string.i18n_caps_add_endpoint_hint),
                leading = { Icon(Icons.Default.AddLink, null) },
                onClick = { endpointDialog = null; newEndpoint = true }
            )
        }
        if (s != null) {
            items(s.customEndpoints, key = { it.id }) { endpoint ->
                FlatRow(
                    title = endpoint.name,
                    description = "${endpoint.baseUrl} · ${endpoint.model}" + if (endpoint.current) " · " + stringResource(R.string.i18n_active) else "",
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
        title = { Text(stringResource(if (existing == null) R.string.i18n_caps_endpoint_add else R.string.i18n_caps_endpoint_edit)) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HermesTextField(name, { name = it }, stringResource(R.string.i18n_name))
                HermesTextField(base, { base = it }, stringResource(R.string.i18n_caps_base_url), placeholder = "http://host:port/v1")
                HermesTextField(model, { model = it }, stringResource(R.string.i18n_model))
                HermesTextField(apiKey, { apiKey = it }, stringResource(if (existing?.hasApiKey == true) R.string.i18n_caps_api_key_keep else R.string.i18n_caps_api_key), password = true)
                HermesTextField(mode, { mode = it }, stringResource(R.string.i18n_caps_api_mode), placeholder = "auto / chat_completions / anthropic_messages")
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
                IconButton(onClick = onUpdate, enabled = busy == null) { Icon(Icons.Default.SystemUpdateAlt, stringResource(R.string.i18n_caps_update_skills)) }
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
                            if (skill.provenance == "hub") IconButton(onClick = { onUninstall(skill) }, enabled = busy == null) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.i18n_caps_uninstall)) }
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
            }
        } else {
            item {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    HermesTextField(query, { query = it; onSearch(it) }, stringResource(R.string.i18n_caps_search_hub), placeholder = "github, browser, docs…")
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
                item { Text(document.content.ifBlank { stringResource(R.string.i18n_caps_no_skill_md) }, style = MaterialTheme.typography.bodySmall) }
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
        title = { Text(stringResource(R.string.i18n_msg_configure, config.name)) },
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
                                label = if (field.isSet) stringResource(R.string.i18n_caps_field_set, field.prompt.ifBlank { field.key }) else field.prompt.ifBlank { field.key },
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
            FlatRow(stringResource(R.string.i18n_caps_add_mcp), stringResource(R.string.i18n_caps_add_mcp_hint), leading = { Icon(Icons.Default.Add, null) }, onClick = { addDialog = true })
        }
        items(snapshot?.mcpServers.orEmpty(), key = { it.name }) { server ->
            FlatRow(
                server.name,
                listOf(server.transport, server.url.ifBlank { server.command }, pluralStringResource(R.plurals.i18n_tools_count, server.tools.size, server.tools.size)).filter(String::isNotBlank).joinToString(" · "),
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
            HermesTextField(name, { name = it }, stringResource(R.string.i18n_name))
            HermesTextField(url, { url = it }, stringResource(R.string.i18n_caps_http_url), placeholder = "https://…")
            Text(stringResource(R.string.ui_7175517a37), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HermesTextField(command, { command = it }, stringResource(R.string.i18n_caps_stdio), placeholder = "npx / uvx / binary")
            HermesTextField(args, { args = it }, stringResource(R.string.i18n_caps_args), placeholder = stringResource(R.string.i18n_caps_args_hint))
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
        title = { Text(stringResource(R.string.i18n_caps_install, entry.name)) },
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
                stringResource(R.string.i18n_caps_secrets_note),
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
                        TextButton(onClick = { edit = variable }, enabled = busy == null) { Text(stringResource(if (variable.isSet) R.string.i18n_replace else R.string.i18n_set)) }
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
            HermesTextField(code, { code = it }, stringResource(R.string.i18n_caps_auth_code))
        } },
        confirmButton = { TextButton(onClick = { onSubmit(code) }, enabled = code.isNotBlank()) { Text(stringResource(R.string.ui_1a2303ede0)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_7d9eb7acb1)) } }
    )
}
