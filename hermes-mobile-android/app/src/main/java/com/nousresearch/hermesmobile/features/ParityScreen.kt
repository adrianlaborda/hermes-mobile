package com.nousresearch.hermesmobile.features

import com.nousresearch.hermesmobile.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nousresearch.hermesmobile.data.HermesRepository
import com.nousresearch.hermesmobile.ui.theme.hermes
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun ParityScreen(repo: HermesRepository, profile: String, initialTab: String = "Memory", onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val c = remember(repo, profile) { ParityController(repo, profile, scope) }
    var tab by remember { mutableStateOf(initialTab) }
    DisposableEffect(c) { onDispose { c.dispose() } }
    LaunchedEffect(c, tab) { c.load(tab) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) { Text(stringResource(R.string.ui_87bb59ba2f), style = MaterialTheme.typography.titleLarge); Text(profile, style = MaterialTheme.typography.labelMedium) }
            IconButton(onClick = { c.load(tab) }, enabled = !c.busy) { Icon(Icons.Default.Refresh, "Refresh") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Memory", "Plugins", "Models", "Files", "Voice", "Settings").forEach { name ->
                FilterChip(tab == name, { tab = name }, label = { Text(stringResource(when(name) { "Memory" -> R.string.a14_memory; "Plugins" -> R.string.a14_plugins; "Models" -> R.string.a14_models_advanced; "Files" -> R.string.a14_files; "Voice" -> R.string.a14_voice; else -> R.string.a14_settings })) })
            }
        }
        if (c.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        c.message?.let { message -> Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { c.message = null }) { Text(stringResource(R.string.ui_48845bff33)) }
        } }
        when(tab) {
            "Memory" -> MemoryPage(c)
            "Plugins" -> PluginsPage(c)
            "Models" -> ModelsPage(c)
            "Files" -> FilesPage(c)
            "Voice" -> VoicePage(c)
            "Settings" -> SettingsPage(c)
        }
    }
    FeatureConsent(c, tab)
}

@Composable
fun FeatureConsent(c: ParityController, refresh: String? = null) {
    c.consent?.let { pending -> AlertDialog(
        onDismissRequest = { c.consent = null }, title = { Text(stringResource(R.string.ui_704ac1d7ac)) },
        text = { Text(pending.first, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(enabled = !c.busy, onClick = { c.accept(refresh) }) { Text(stringResource(R.string.ui_eebdd24a77)) } },
        dismissButton = { TextButton(onClick = { c.consent = null }) { Text(stringResource(R.string.ui_19766ed6cc)) } }
    ) }
}

@Composable
fun FeatureCard(title: String, subtitle: String = "", content: @Composable ColumnScope.() -> Unit = {}) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)),
        border = BorderStroke(1.dp, MaterialTheme.hermes.line)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun Availability(c: ParityController, key: String) { c.unavailable[key]?.let { Text(it, color = MaterialTheme.colorScheme.error) } }

@Composable
private fun MemoryPage(c: ParityController) {
    var provider by remember(c) { mutableStateOf<JSONObject?>(null) }
    var node by remember(c) { mutableStateOf<JSONObject?>(null) }
    var query by remember { mutableStateOf("") }
    val memory = c.data["memory"]
    val graph = c.data["graph"]
    val nodesById = remember(graph) { graph?.rows("nodes").orEmpty().associateBy { it.optString("id") } }
    val edgesById = remember(graph) {
        buildMap<String, MutableList<JSONObject>> {
            graph?.rows("edges").orEmpty().forEach { edge ->
                listOf(edge.optString("source"), edge.optString("target")).distinct().forEach { id ->
                    getOrPut(id) { mutableListOf() }.add(edge)
                }
            }
        }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { FeatureCard(stringResource(R.string.ui_c3963aedaa), "Active provider: ${memory?.optString("active").orEmpty()}") { Availability(c,"memory") } }
        items(memory?.rows("providers").orEmpty()) { p ->
            FeatureCard(p.optString("name"), p.optString("description")) {
                Text(if (p.optBoolean("configured")) "Configured" else "Setup required", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { c.fetch("provider") { c.api("/api/memory/providers/${encoded(p.optString("name"))}/config?surface=declared").also { config ->
                        try { c.data["memory-oauth:${p.optString("name")}"] = c.api("/api/memory/providers/${encoded(p.optString("name"))}/oauth/status") }
                        catch(e:com.nousresearch.hermesmobile.data.HermesHttpException) { if(e.status !in listOf(404,405,501))throw e }
                        provider = config
                    } } }, enabled = !c.busy) { Text(stringResource(R.string.ui_6defafa2ca)) }
                    TextButton(onClick = { c.action("Memory") { c.api("/api/memory/provider", "PUT", json("provider" to p.optString("name"))) } }, enabled = !c.busy && p.optString("name") != memory?.optString("active")) { Text(stringResource(R.string.ui_d086d26f17)) }
                }
            }
        }
        item { c.data["curator"]?.let { curator -> FeatureCard(stringResource(R.string.ui_61c91d9953), "Last run: ${curator.optString("last_run_at", "Never")} · Interval: ${curator.optString("interval_hours")} h") {
            Text(if (curator.optBoolean("enabled")) "Enabled" else "Disabled in backend settings")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ui_e159b06187), Modifier.weight(1f)); Switch(curator.optBoolean("paused"), { paused -> c.action("Memory") { c.api("/api/curator/paused", "PUT", json("paused" to paused)) } }, enabled = !c.busy)
            }
            TextButton(onClick = { c.action("Memory") { c.api("/api/curator/run", "POST", JSONObject()) } }, enabled = !c.busy) { Text(stringResource(R.string.ui_0991397702)) }
        } }; Availability(c, "curator") }
        item { Text(stringResource(R.string.ui_eac3c5fa75), style = MaterialTheme.typography.headlineSmall); Availability(c,"graph")
            OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.ui_cf9d3e368d)) }, modifier = Modifier.fillMaxWidth()) }
        items(graph?.rows("nodes").orEmpty().filter { it.toString().contains(query, true) }, key = { it.optString("id") }) { n ->
            val related = edgesById[n.optString("id")].orEmpty().size
            FeatureCard(n.optString("label"), "${n.optString("kind")} · ${n.optString("category")} · ${n.optInt("useCount")} uses · $related links") {
                TextButton(onClick = { c.fetch("node") { c.api("/api/learning/node?id=${encoded(n.optString("id"))}").put("id",n.optString("id")).also { node = it } } }, enabled = !c.busy) { Text(stringResource(R.string.ui_9214dc738b)) }
                edgesById[n.optString("id")].orEmpty().forEach { edge ->
                    val otherId = if (edge.optString("source") == n.optString("id")) edge.optString("target") else edge.optString("source")
                    val other = nodesById[otherId]
                    TextButton(enabled = !c.busy, onClick = { c.fetch("node") { c.api("/api/learning/node?id=${encoded(otherId)}").put("id", otherId).also { node = it } } }) {
                        Text("${edge.optString("kind", "Related")}: ${other?.optString("label") ?: otherId}")
                    }
                }
            }
        }
        item { FeatureCard(stringResource(R.string.ui_5fb1482d49), "Permanent deletion for profile ${c.profile}. Built-in memory: ${memory?.optJSONObject("builtin_files")?.optInt("memory") ?: 0} bytes") {
            Row(Modifier.horizontalScroll(rememberScrollState())) { listOf("memory", "user", "all").forEach { target ->
                TextButton(onClick = { c.confirm("Delete $target memory for ${c.profile}?") { c.api("/api/memory/reset", "POST", json("target" to target)) } }) { Text("Reset $target") }
            } }
        } }
    }
    provider?.let { p -> SchemaDialog(p.optString("label"), p.rows("fields"), onDismiss = { provider = null }, onSave = { values ->
        c.action("Memory") { c.api("/api/memory/providers/${encoded(p.optString("name"))}/config?surface=declared", "PUT", json("values" to values)) }; provider = null
    }, extra = {
        if(c.data.containsKey("memory-oauth:${p.optString("name")}")) {
        Text(stringResource(R.string.ui_8346eece81), style = MaterialTheme.typography.bodySmall)
        Row {
            TextButton(onClick = { c.action(null) { c.api("/api/memory/providers/${encoded(p.optString("name"))}/oauth/start", "POST").also { c.message = it.optString("detail") } } }) { Text(stringResource(R.string.ui_6429ea5125)) }
            TextButton(onClick = { c.fetch("memory-oauth:${p.optString("name")}") { c.api("/api/memory/providers/${encoded(p.optString("name"))}/oauth/status") } }) { Text(stringResource(R.string.ui_69cd359030)) }
        }
        c.data["memory-oauth:${p.optString("name")}"]?.let { Text("${it.optString("state")} · ${it.optString("detail")}") }
        }
    }) }
    node?.let { n -> TextEditorDialog(n.optString("label"), n.optString("content"), { node = null }, { content ->
        c.action("Memory") { val latest = c.api("/api/learning/node?id=${encoded(n.optString("id"))}"); check(latest.optString("content") == n.optString("content")) { "This node changed. Reopen it before saving." }; c.api("/api/learning/node", "PUT", json("id" to n.optString("id"), "content" to content, "profile" to c.profile)) }; node = null
    }, onDelete = { c.confirm("Permanently delete ${n.optString("label")}?") { c.api("/api/learning/node", "DELETE", json("id" to n.optString("id"), "profile" to c.profile)) }; node = null }) }
}

@Composable
private fun PluginsPage(c: ParityController) {
    var settings by remember(c) { mutableStateOf<JSONObject?>(null) }
    var query by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf("") }
    val providers = c.data["hub"]?.optJSONObject("providers")
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.ui_2a4c771fb1), style = MaterialTheme.typography.headlineSmall); Text(stringResource(R.string.ui_8934c38b97), style = MaterialTheme.typography.bodySmall); Availability(c,"plugins") }
        items(c.data["plugins"]?.rows("plugins").orEmpty(), key = { it.optString("key",it.optString("name")) }) { p ->
            FeatureCard(p.optString("name") + " " + p.optString("version"), p.optString("description")) {
                Text("${p.optString("status")} · ${p.optString("source")}", style = MaterialTheme.typography.labelMedium)
                p.rows("servers").forEach { Text("${it.optString("name")}: ${it.optString("sentence", it.optString("state"))}", style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    val key = p.optString("key")
                    TextButton(enabled = !c.busy && key.isNotBlank(), onClick = { c.action("Plugins") { c.plugins("toggle", json("key" to key, "enable" to (p.optString("status") != "enabled"))) } }) { Text(if(p.optString("status") == "enabled") "Disable" else "Enable") }
                    if (p.rows("settings_schema").isNotEmpty()) TextButton(onClick = { settings = p }) { Text(stringResource(R.string.ui_74a883a037)) }
                    if (p.optBoolean("update_available")) TextButton(onClick = { c.action("Plugins") { c.updatePlugin(p.optString("name")) } }, enabled = !c.busy) { Text(stringResource(R.string.ui_c1c1009d3f)) }
                    if (p.optString("source") in listOf("user","git")) TextButton(onClick = { c.confirm("Remove ${p.optString("name")} from ${c.profile}?") { c.plugins("remove", json("name" to p.optString("name"))) } }) { Text(stringResource(R.string.ui_c3812fc4ac)) }
                }
            }
        }
        item { FeatureCard(stringResource(R.string.ui_8fe8b73b1f), "Profile ${c.profile}") {
            Availability(c,"hub")
            ChoiceField(stringResource(R.string.ui_8e75ebbdb2), providers?.optString("context_engine").orEmpty(), providers?.rows("context_options").orEmpty().map { it.optString("name") }) { value -> c.action("Plugins") { c.api("/api/dashboard/plugin-providers", "PUT", json("context_engine" to value)) } }
        } }
        item { Text(stringResource(R.string.ui_3877d14889), style = MaterialTheme.typography.headlineSmall); Availability(c,"catalog"); OutlinedTextField(query, { query = it }, label={Text(stringResource(R.string.ui_b7fe876dc9))}, modifier=Modifier.fillMaxWidth()) }
        items(c.data["catalog"]?.rows("entries").orEmpty().filter { it.toString().contains(query,true) }) { p ->
            FeatureCard(p.optString("title").ifBlank { p.optString("name") }, p.optString("description")) {
                Text(p.opt("capability_summary")?.toString().orEmpty(), style=MaterialTheme.typography.bodySmall)
                TextButton(enabled = !c.busy, onClick = { c.confirm("Install ${p.optString("name")} on profile ${c.profile}?\n${p.optString("repo")}\nReviewed commit: ${p.optString("sha")}\n${p.opt("capability_summary")}") {
                    c.plugins("install", json("identifier" to p.optString("repo"), "catalog_name" to p.optString("name"), "enable" to true))
                } }) { Text(stringResource(R.string.ui_3eec9eadf3)) }
            }
        }
        item { FeatureCard(stringResource(R.string.ui_7b1192bdd3), "Install a repository or package supported by the gateway") {
            OutlinedTextField(custom, { custom=it }, label={Text(stringResource(R.string.ui_9ecc632f10))}, modifier=Modifier.fillMaxWidth())
            TextButton(enabled=custom.isNotBlank() && !c.busy, onClick={c.confirm("Install and enable $custom on ${c.profile}?") { c.plugins("install", json("identifier" to custom.trim(), "enable" to true)) }}) {Text(stringResource(R.string.ui_569ca49f4a))}
        } }
    }
    settings?.let { plugin -> SchemaDialog(plugin.optString("name"), plugin.rows("settings_schema"), { settings=null }, { values ->
        c.action("Plugins") {
            val ordinary = JSONObject(values.toString())
            for (field in plugin.rows("settings_schema")) if(field.optString("type") == "secret") {
                val key=field.optString("key"); val value=ordinary.optString(key); ordinary.remove(key)
                if(value.isNotBlank()) c.api("/api/env", "PUT", json("key" to field.optString("env"), "value" to value))
            }
            c.plugins("settings", json("key" to plugin.optString("key"), "values" to ordinary))
        }; settings=null
    }) }
}

@Composable
fun ChoiceField(label: String, value: String, choices: List<String>, onValue: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style=MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick={expanded=true}, modifier=Modifier.fillMaxWidth()) {Text(value.ifBlank {"Inherit"})}
            DropdownMenu(expanded, {expanded=false}) { choices.distinct().forEach { choice -> DropdownMenuItem(text={Text(choice.ifBlank {"Inherit"})}, onClick={onValue(choice);expanded=false}) } }
        }
    }
}

@Composable
fun SchemaDialog(title: String, fields: List<JSONObject>, onDismiss: () -> Unit, onSave: (JSONObject) -> Unit, extra: @Composable () -> Unit = {}) {
    val values = remember(title, fields.map { it.toString() }) { mutableStateMapOf<String,String>().apply { fields.forEach { f ->
        val secret = f.optString("kind",f.optString("type")) == "secret"
        put(f.optString("key"), if(secret) "" else if(f.isNull("value")) "" else f.optString("value"))
    } } }
    var error by remember { mutableStateOf<String?>(null) }
    FullScreenEditor(onDismissRequest=onDismiss, title={Text(title)}, text={
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(12.dp)) {
            fields.forEach { f ->
                val key=f.optString("key"); val kind=f.optString("kind",f.optString("type")); val value=values[key].orEmpty(); val label=f.optString("label",key)
                when(kind) {
                    "bool","boolean" -> Row(verticalAlignment=Alignment.CenterVertically) { Text(label,Modifier.weight(1f)); Switch(value=="true", {values[key]=it.toString()}) }
                    "select","enum" -> ChoiceField(label,value, f.rows("options").map {it.optString("value")}.ifEmpty {f.strings("choices")}) {values[key]=it}
                    else -> OutlinedTextField(value,{values[key]=it},label={Text(label)},modifier=Modifier.fillMaxWidth(),visualTransformation=if(kind=="secret") PasswordVisualTransformation() else VisualTransformation.None, supportingText={Text(if(kind=="secret") "Blank keeps the stored secret" else f.optString("description"))})
                }
            }
            extra(); error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }
    }, confirmButton={TextButton(onClick={
        runCatching {
            JSONObject().apply { fields.forEach { f ->
                val key=f.optString("key");val value=values[key].orEmpty();val kind=f.optString("kind",f.optString("type"))
                if(kind!="secret" || value.isNotBlank()) put(key,when(kind){
                    "boolean" -> value=="true"
                    "number" -> if(f.has("kind")) value else value.toDouble().also {require(it.isFinite())}
                    "json" -> if(f.has("kind")) value else org.json.JSONTokener(value).nextValue()
                    else -> value
                })
            } }
        }.onSuccess(onSave).onFailure {error=it.message}
    }) {Text(stringResource(R.string.ui_1509f561f2))}}, dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.ui_19766ed6cc))}})
}

@Composable
fun TextEditorDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit, onDelete: (() -> Unit)? = null, readOnly: Boolean=false) {
    var value by remember(initial) { mutableStateOf(initial) }
    FullScreenEditor(onDismissRequest=onDismiss,title={Text(title)},text={OutlinedTextField(value,{value=it},readOnly=readOnly,modifier=Modifier.fillMaxWidth().heightIn(min=180.dp,max=420.dp))},confirmButton={if(!readOnly) TextButton(onClick={onSave(value)}){Text(stringResource(R.string.ui_1509f561f2))}},dismissButton={Row {if(onDelete!=null) TextButton(onClick=onDelete){Text(stringResource(R.string.ui_e2d0a54968))};TextButton(onClick=onDismiss){Text(stringResource(R.string.ui_7d9eb7acb1))}}})
}


/** Long editors use the available display and a single inset owner. */
@Composable
fun FullScreenEditor(onDismissRequest: () -> Unit, title: @Composable () -> Unit,
                     text: @Composable () -> Unit, confirmButton: @Composable () -> Unit,
                     dismissButton: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() } }
                    dismissButton()
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { text() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { confirmButton() }
            }
        }
    }
}
