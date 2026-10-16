package com.nousresearch.hermesmobile.features

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.data.HermesSession
import com.nousresearch.hermesmobile.ui.components.*
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

/** Only folder labels and session ids are local; no transcripts or credentials. */
@Composable
fun LocalFolders(connection: String, profile: String, sessions: List<HermesSession>, onOpen: (HermesSession) -> Unit) {
    val prefs = LocalContext.current.getSharedPreferences("chat_folders", android.content.Context.MODE_PRIVATE)
    val owner = JSONArray(listOf(connection, profile)).toString()
    var folders by remember(owner) { mutableStateOf(runCatching { JSONObject(prefs.getString(owner, "{}")!!) }.getOrDefault(JSONObject())) }
    var selected by remember(owner) { mutableStateOf<String?>(null) }
    var create by remember(owner) { mutableStateOf(false) }
    var editing by remember(owner) { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    fun save(updated: JSONObject) { folders = updated; prefs.edit().putString(owner, updated.toString()).apply() }
    val folder = selected?.let { folders.optJSONObject(it) }
    val ids = folder?.strings("sessions").orEmpty().toSet()
    val available = sessions.filter { it.profile == profile && !it.hidden }
    BackHandler(selected != null) { selected = null }
    Column {
        // Under the Chats header: the open folder's name and close, or what this view is.
        SubBar(folder?.optString("name") ?: stringResource(R.string.a14_local_folders), onClose = if (selected != null) {{ selected = null }} else null) {
            if (selected == null) IconButton(onClick = { create = true }) { Icon(Icons.Default.Add, stringResource(R.string.a14_create)) }
            else IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, stringResource(R.string.a14_edit)) }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            if (folder == null) {
                items(folders.keys().asSequence().toList(), key = { it }) { id ->
                    val f = folders.getJSONObject(id)
                    FlatRow(f.optString("name"), f.strings("sessions").size.toString(), leading = { Icon(Icons.Default.Folder, null) }, onClick = { selected = id })
                }
                if (folders.length() == 0) item { Text(stringResource(R.string.a14_no_folders)) }
            } else {
                val visible = available.filter { it.id in ids }
                items(visible, key = { it.id }) { session -> FlatRow(session.title.ifBlank { session.id }, session.source, onClick = { onOpen(session) }) }
                if (visible.isEmpty()) item { Text(stringResource(R.string.a14_no_chats)) }
                item { Text(stringResource(R.string.a14_loaded_sessions), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    if (create) AlertDialog(onDismissRequest = { create = false }, title = { Text(stringResource(R.string.a14_folders)) }, text = {
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.a14_name)) }, singleLine = true)
    }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
        save(JSONObject(folders.toString()).put(UUID.randomUUID().toString(), json("name" to name.trim(), "sessions" to JSONArray())))
        create = false; name = ""
    }) { Text(stringResource(R.string.a14_create)) } }, dismissButton = { TextButton(onClick = { create = false }) { Text(stringResource(R.string.a14_cancel)) } })
    if (editing && folder != null) {
        var chosen by remember(selected) { mutableStateOf(ids) }
        var title by remember(selected) { mutableStateOf(folder.optString("name")) }
        FullScreenEditor(onDismissRequest = { editing = false }, title = { Text(stringResource(R.string.a14_folders)) }, text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.a14_name)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.a14_search)) }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.a14_loaded_sessions), style = MaterialTheme.typography.bodySmall)
                LazyColumn { items(available.filter { it.title.contains(query, true) }, key = { it.id }) { session ->
                    FlatRow(session.title.ifBlank { session.id }, session.source, leading = { Checkbox(session.id in chosen, onCheckedChange = null) }, onClick = { chosen = if (session.id in chosen) chosen - session.id else chosen + session.id })
                } }
            }
        }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = {
            save(JSONObject(folders.toString()).put(selected!!, json("name" to title.trim(), "sessions" to JSONArray(chosen.toList())))); editing = false
        }) { Text(stringResource(R.string.a14_apply)) } }, dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.a14_cancel)) } })
    }
}
