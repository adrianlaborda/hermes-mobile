package com.nousresearch.hermesmobile.features

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.data.*
import com.nousresearch.hermesmobile.ui.components.*
import com.nousresearch.hermesmobile.ui.theme.hermes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Project session rows come from the hydrated server lanes, never client path guessing. */
fun projectSessions(project: JSONObject, profile: String): List<HermesSession> = project.rows("repos")
    .flatMap { it.rows("groups") }.flatMap { it.rows("sessions") }
    .filter { it.isNull("profile") || it.optString("profile").isBlank() || it.optString("profile") == profile }
        .map { row ->
        val normalized = JSONObject(row.toString()).put("profile", profile)
        listOf("title", "preview", "source", "model").forEach { if (normalized.isNull(it)) normalized.put(it, "") }
        HermesSession.fromJson(normalized)
    }
    .distinctBy { it.id }.filter { it.id.isNotBlank() }.sortedByDescending { it.lastActive }

@Composable
fun ProjectsPage(repo: HermesRepository, profile: String, onOpen: (HermesSession) -> Unit,
                 onNew: (String, String, String?) -> Unit) {
    var projects by remember(repo, profile) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selected by remember(repo, profile) { mutableStateOf<JSONObject?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var loading by remember(repo, profile) { mutableStateOf(false) }
    var error by remember(repo, profile) { mutableStateOf<String?>(null) }
    var rename by remember(repo, profile) { mutableStateOf(false) }
    var archive by remember(repo, profile) { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }
    var create by remember(repo, profile) { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }; var path by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var filesOpen by remember(repo, profile) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val projectId = selected?.optString("id")
    LaunchedEffect(repo, profile, projectId, revision) {
        loading = true; error = null
        try {
            if (projectId == null) projects = repo.gateway.requestObject("projects.tree", json("profile" to profile)).rows("projects")
            else {
                val detail = repo.gateway.requestObject("projects.project_sessions", json("profile" to profile, "project_id" to projectId)).optJSONObject("project")
                // Discovered projects without chats may not appear in the hydrated tree.
                if (detail != null) selected = detail
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message }
        finally { loading = false }
    }
    if (filesOpen && selected != null) {
        val controller = remember(repo, profile, projectId) { ParityController(repo, profile, scope) }
        DisposableEffect(controller) { onDispose { controller.dispose() } }
        BackHandler { filesOpen = false }
        Column {
            SubBar(selected?.optString("label").orEmpty(), onClose = { filesOpen = false })
            controller.message?.let { Text(shown(it), Modifier.padding(16.dp)) }
            FilesPage(controller, initialDirectory = selected?.optString("path"))
        }
        FeatureConsent(controller)
        return
    }
    BackHandler(selected != null) { selected = null }
    Column(Modifier.fillMaxSize()) {
        // Under the Chats header: the open project's name and close, or what this view is.
        SubBar(selected?.optString("label") ?: stringResource(R.string.a14_projects), onClose = if (selected != null) {{ selected = null }} else null,
            actions = {
                IconButton(onClick = { revision++ }, enabled = !loading) { Icon(Icons.Default.Refresh, stringResource(R.string.a14_refresh)) }
                if (selected == null) IconButton(onClick = { create = true }) { Icon(Icons.Default.Add, stringResource(R.string.a14_create_project)) }
                else if (selected?.optBoolean("isAuto") == false && selected?.optBoolean("isNoProject") == false) {
                    IconButton(onClick = { projectName = selected?.optString("label").orEmpty(); rename = true }, enabled = !busy) { Icon(Icons.Default.Edit, stringResource(R.string.a14_edit)) }
                }
            })
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(stringResource(R.string.a14_projects_error) + "\n" + it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val current = selected
            if (current == null) {
                if (projects.isEmpty() && !loading && error == null) item { Text(stringResource(R.string.a14_no_projects)) }
                items(projects, key = { it.optString("id") }) { p ->
                    Card(onClick = { selected = p }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f))) {
                        FlatRow(p.optString("label"), p.optString("path").takeUnless { it == "null" }, leading = { Icon(Icons.Default.FolderOpen, null) }, trailing = { Text(p.optInt("sessionCount").toString()); Icon(Icons.Default.ChevronRight, null) })
                    }
                }
            } else {
                item {
                    val cwd = current.optString("path").takeUnless { it.isBlank() || it == "null" }
                    FeatureCard(stringResource(R.string.a14_workspace), cwd.orEmpty()) {
                        Text(stringResource(R.string.a14_project_memory), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { onNew(profile, "", cwd) }, enabled = !loading && error == null) { Text(stringResource(R.string.a14_new_chat)) }
                        if (cwd != null) TextButton(onClick = { filesOpen = true }) { Text(stringResource(R.string.a14_files)) }
                    }
                }
                val sessions = projectSessions(current, profile)
                if (sessions.isEmpty() && !loading) item { Text(stringResource(R.string.a14_no_chats)) }
                items(sessions, key = { it.id }) { session ->
                    FlatRow(session.title.ifBlank { session.id }, session.preview.ifBlank { session.source }, leading = { Icon(Icons.Default.ChatBubbleOutline, null) }, onClick = { onOpen(session) })
                }
            }
        }
    }
    if (rename) AlertDialog(onDismissRequest = { if (!busy) rename = false }, title = { Text(stringResource(R.string.a14_edit)) }, text = {
        Column {
            OutlinedTextField(projectName, { projectName = it }, label = { Text(stringResource(R.string.a14_name)) })
            TextButton(onClick = { archive = true; rename = false }, enabled = !busy) { Text(stringResource(R.string.a14_archive)) }
        }
    }, confirmButton = { TextButton(enabled = !busy && projectName.isNotBlank(), onClick = {
        val id = projectId ?: return@TextButton
        busy = true
        scope.launch {
            try { repo.gateway.requestObject("projects.update", json("id" to id, "profile" to profile, "name" to projectName.trim())); rename = false; selected = null; revision++ }
            catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message }
            finally { busy = false }
        }
    }) { Text(stringResource(R.string.a14_apply)) } }, dismissButton = { TextButton(enabled = !busy, onClick = { rename = false }) { Text(stringResource(R.string.a14_cancel)) } })
    if (archive) AlertDialog(onDismissRequest = { if (!busy) archive = false }, title = { Text(stringResource(R.string.a14_archive)) }, text = { Text(stringResource(R.string.a14_archive_hint)) }, confirmButton = {
        TextButton(enabled = !busy, onClick = {
            val id = projectId ?: return@TextButton
            busy = true
            scope.launch {
                try { repo.gateway.requestObject("projects.archive", json("id" to id, "profile" to profile)); archive = false; selected = null; revision++ }
                catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message }
                finally { busy = false }
            }
        }) { Text(stringResource(R.string.a14_archive)) }
    }, dismissButton = { TextButton(enabled = !busy, onClick = { archive = false }) { Text(stringResource(R.string.a14_cancel)) } })
    if (create) AlertDialog(onDismissRequest = { if (!busy) create = false }, title = { Text(stringResource(R.string.a14_create_project)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.a14_name)) }, singleLine = true)
            OutlinedTextField(path, { path = it }, label = { Text(stringResource(R.string.a14_remote_path)) }, singleLine = true)
            Text(stringResource(R.string.a14_existing_path), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = !busy && name.isNotBlank() && path.trim().startsWith("/"), onClick = {
        busy = true
        scope.launch {
            try {
                repo.gateway.requestObject("projects.create", json("profile" to profile, "name" to name.trim(), "folders" to org.json.JSONArray(listOf(path.trim())), "primary_path" to path.trim()))
                create = false; name = ""; path = ""; revision++
            } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message }
            finally { busy = false }
        }
    }) { Text(stringResource(R.string.a14_create)) } }, dismissButton = { TextButton(enabled = !busy, onClick = { create = false }) { Text(stringResource(R.string.a14_cancel)) } })
}

/** Reference-style labelled card: MODEL / SESSION metadata with hairline border. */
@Composable
fun SessionInfoCard(label: String, rows: List<Pair<String, String>>) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.hermes.line)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            rows.forEach { (key, value) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        key,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(76.dp)
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
