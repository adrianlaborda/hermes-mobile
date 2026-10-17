package com.nousresearch.hermesmobile.features

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nousresearch.hermesmobile.*
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.data.*
import com.nousresearch.hermesmobile.ui.components.*
import com.nousresearch.hermesmobile.ui.theme.hermes
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/**
 * Home is a dashboard, not a second menu: gateway state at a glance, the conversations to
 * pick up again and what is scheduled next. Navigation lives in the drawer.
 */
@Composable
fun ExperienceHome(s: HermesUiState, onMenu: () -> Unit, onNavigate: (MainSection) -> Unit, onNew: () -> Unit, onOpen: (HermesSession) -> Unit) {
    val connected = s.connectionState == GatewayConnectionState.CONNECTED
    val visible = remember(s.sessions) { s.sessions.filterNot { it.hidden || it.archived || it.isRoutineRun } }
    val recent = remember(visible) { visible.sortedByDescending { it.lastActive }.take(5) }
    val upcoming = remember(s.cronJobs) { s.cronJobs.filter { it.enabled && (it.nextRunAt ?: 0L) > 0L }.sortedBy { it.nextRunAt }.take(3) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            HermesTopBar(
                stringResource(R.string.a14_home),
                leading = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, stringResource(R.string.a21_menu)) } },
                actions = { FilledTonalIconButton(onClick = onNew, enabled = connected) { Icon(Icons.Outlined.AddComment, stringResource(R.string.a14_new_chat)) } }
            )
            DashedDivider(color = MaterialTheme.hermes.line)
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.hermes.line)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HermesBrandMark(size = 40)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.savedConfig?.displayLabel ?: "Hermes", style = MaterialTheme.typography.titleMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StateDot(good = connected, warning = !connected)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    listOfNotNull(
                                        stringResource(if (connected) R.string.a14_connected else R.string.a14_reconnecting),
                                        s.status?.version?.takeIf { it.isNotBlank() }?.let { "Hermes $it" }
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeStat(visible.size.toString(), stringResource(R.string.a22_stat_chats), Modifier.weight(1f)) { onNavigate(MainSection.CHAT) }
                        HomeStat(s.profiles.size.toString(), stringResource(R.string.a22_stat_profiles), Modifier.weight(1f)) { onNavigate(MainSection.PROFILES) }
                        HomeStat(s.cronJobs.count { it.enabled }.toString(), stringResource(R.string.a22_stat_tasks), Modifier.weight(1f)) { onNavigate(MainSection.CRON) }
                    }
                    Button(onClick = onNew, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = connected) {
                        Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.a14_new_chat))
                    }
                }
            }
        }
        item { SectionLabel(stringResource(R.string.a22_continue), modifier = Modifier.padding(top = 8.dp)) }
        if (recent.isEmpty()) item {
            Text(stringResource(R.string.a22_no_conversations), Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(recent, key = { "recent:" + it.profile + it.id }) { session ->
            FlatRow(
                onClick = { onOpen(session) },
                leading = { com.nousresearch.hermesmobile.ui.screens.SessionSourceAvatar(session.source, size = 36) },
                trailing = {
                    Text(com.nousresearch.hermesmobile.ui.screens.relativeDate(session.lastActive), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            ) {
                Text(session.displayTitle.ifBlank { stringResource(R.string.a21_untitled) }, style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(com.nousresearch.hermesmobile.ui.screens.sessionMeta(session), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item { SectionLabel(stringResource(R.string.a22_up_next), modifier = Modifier.padding(top = 8.dp)) }
        if (upcoming.isEmpty()) item {
            FlatRow(
                stringResource(R.string.a22_no_tasks),
                stringResource(R.string.a14_scheduled_hint),
                leading = { Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailing = { Icon(Icons.Default.ChevronRight, null) },
                onClick = { onNavigate(MainSection.CRON) }
            )
        }
        items(upcoming, key = { "cron:" + it.id }) { job ->
            FlatRow(
                job.name.ifBlank { job.prompt.take(60) },
                listOf(job.scheduleDisplay.ifBlank { job.schedule }, nextRunLabel(job.nextRunAt)).filter { it.isNotBlank() }.joinToString(" · "),
                leading = { Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary) },
                trailing = { Icon(Icons.Default.ChevronRight, null) },
                onClick = { onNavigate(MainSection.CRON) }
            )
        }
    }
}

@Composable
private fun HomeStat(value: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.hermes.accentTint.copy(alpha = .55f)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun nextRunLabel(at: Long?): String {
    if (at == null || at <= 0L) return ""
    val millis = if (at < 10_000_000_000L) at * 1000L else at
    return android.text.format.DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(),
        android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()
}

@Composable
fun SystemPage(
    repo: HermesRepository, profile: String, profiles: List<String> = emptyList(), onProfile: (String) -> Unit = {},
    onMenu: () -> Unit = {}, onSettings: () -> Unit
) {
    var logs by remember(repo, profile) { mutableStateOf(false) }
    if (logs) { ServerLogs(repo, profile) { logs = false }; return }
    var stats by remember(repo) { mutableStateOf<JSONObject?>(null) }
    var error by remember(repo) { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    // One read on entry or explicit refresh: no background polling.
    LaunchedEffect(repo, revision) {
        loading = true; error = null; stats = null
        try { stats = repo.rest.scopedApi(profile, "/api/system/stats") }
        catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize()) {
        HermesTopBar(stringResource(R.string.a14_system),
            subtitleContent = { ProfileSwitcher(profile, profiles, onProfile) },
            leading = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, stringResource(R.string.a21_menu)) } },
            actions = { IconButton(onClick = { revision++ }, enabled = !loading) { Icon(Icons.Default.Refresh, stringResource(R.string.a14_refresh)) } })
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else DashedDivider(color = MaterialTheme.hermes.line)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            error?.let { item { Text(it, Modifier.padding(horizontal = 18.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
            stats?.let { d ->
                item {
                    SectionLabel(stringResource(R.string.i18n_sys_server))
                    FlatRow(
                        d.optString("hostname").ifBlank { stringResource(R.string.a14_system) },
                        listOf(d.optString("system"), d.optString("arch"), d.optString("hermes_version").takeIf { it.isNotBlank() }?.let { "Hermes $it" }.orEmpty())
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        leading = { Icon(Icons.Default.Dns, null) }
                    )
                    d.optLong("uptime_seconds", -1).takeIf { it >= 0 }?.let {
                        FlatRow(stringResource(R.string.a14_uptime, it / 3600), leading = { Icon(Icons.Default.Schedule, null) })
                    }
                }
                item { SectionLabel(stringResource(R.string.i18n_sys_resources)) }
                listOf(
                    Triple(R.string.a14_cpu, Icons.Default.Memory, d.optDouble("cpu_percent", Double.NaN)),
                    Triple(R.string.a14_ram, Icons.Default.Storage, d.optJSONObject("memory")?.optDouble("percent", Double.NaN) ?: Double.NaN),
                    Triple(R.string.a14_disk, Icons.Default.SdStorage, d.optJSONObject("disk")?.optDouble("percent", Double.NaN) ?: Double.NaN)
                ).forEach { (title, icon, value) ->
                    item { ResourceRow(stringResource(title), icon, value) }
                }
            }
            item {
                SectionLabel(stringResource(R.string.i18n_sys_more))
                FlatRow(stringResource(R.string.a14_logs), leading = { Icon(Icons.AutoMirrored.Filled.Article, null) }, trailing = { Icon(Icons.Default.ChevronRight, null) }, onClick = { logs = true })
                FlatRow(stringResource(R.string.a14_configuration_usage), leading = { Icon(Icons.Default.Tune, null) }, trailing = { Icon(Icons.Default.ChevronRight, null) }, onClick = onSettings)
            }
        }
    }
}

/** One server resource: its name and use, with a thin bar like the context ring's. */
@Composable
private fun ResourceRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, value: Double) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(if (value.isFinite()) "${value.toInt()}%" else stringResource(R.string.a14_unavailable),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (value.isFinite()) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (value / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = if (value >= 90) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    drawStopIndicator = {}
                )
            }
        }
    }
}

@Composable
fun ServerLogs(repo: HermesRepository, profile: String, onBack: () -> Unit) {
    var lines by remember(repo, profile) { mutableStateOf<List<String>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(onBack = onBack)
    LaunchedEffect(repo, profile, revision) {
        loading = true; error = null
        try { lines = repo.rest.scopedApi(profile, "/api/logs?file=agent&lines=200").strings("lines") }
        catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize()) {
        HermesTopBar(stringResource(R.string.a14_logs), profile, leading = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, stringResource(R.string.a14_back)) } }, actions = { IconButton(enabled = !loading, onClick = { revision++ }) { Icon(Icons.Default.Refresh, stringResource(R.string.a14_refresh)) } })
        HermesSearchField(query, { query = it }, stringResource(R.string.a14_search), Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(lines.filter { it.contains(query, true) }) { line ->
                androidx.compose.foundation.text.selection.SelectionContainer { Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = com.nousresearch.hermesmobile.ui.theme.HermesMono) }
            }
        }
    }
}
