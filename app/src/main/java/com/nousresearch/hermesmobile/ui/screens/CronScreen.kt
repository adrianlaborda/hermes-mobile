package com.nousresearch.hermesmobile.ui.screens

import com.nousresearch.hermesmobile.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nousresearch.hermesmobile.data.*
import com.nousresearch.hermesmobile.ui.components.*
import com.nousresearch.hermesmobile.ui.theme.hermes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CronScreen(
    profiles: List<HermesProfile>,
    selectedProfile: String,
    jobs: List<CronJob>,
    deliveryTargets: List<CronDeliveryTarget>,
    blueprints: List<AutomationBlueprint>,
    runs: Map<String, List<CronRun>>,
    onProfile: (String) -> Unit,
    onCreate: (String, CronDraft) -> Unit,
    onUpdate: (CronJob, CronDraft) -> Unit,
    onPause: (CronJob) -> Unit,
    onResume: (CronJob) -> Unit,
    onTrigger: (CronJob) -> Unit,
    onDelete: (CronJob) -> Unit,
    onLoadRuns: (CronJob) -> Unit,
    onInstantiateBlueprint: (String, AutomationBlueprint, Map<String, String>) -> Unit,
    onMenu: () -> Unit = {}
) {
    var editorJob by remember { mutableStateOf<CronJob?>(null) }
    var create by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<CronJob?>(null) }
    var blueprint by remember { mutableStateOf<AutomationBlueprint?>(null) }
    var search by remember { mutableStateOf("") }

    val filtered = remember(jobs, search) {
        val q = search.trim().lowercase()
        if (q.isBlank()) jobs else jobs.filter {
            listOf(it.name, it.prompt, it.profile, it.scheduleDisplay, it.schedule, it.deliver)
                .any { value -> value.lowercase().contains(q) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        HermesTopBar(
            stringResource(R.string.ui_61b7bb44e2),
            subtitleContent = { com.nousresearch.hermesmobile.ui.components.ProfileSwitcher(selectedProfile, profiles.map { it.name }, onProfile) },
            leading = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, stringResource(R.string.a21_menu)) } },
            actions = {
                if (blueprints.isNotEmpty()) {
                    IconButton(onClick = { blueprint = blueprints.first() }) { Icon(Icons.Default.AutoAwesome, stringResource(R.string.ui_a398de9c57)) }
                }
                IconButton(onClick = { create = true }) { Icon(Icons.Default.Add, stringResource(R.string.ui_d5e520f554)) }
            }
        )
        DashedDivider(color = MaterialTheme.hermes.line)

        Spacer(Modifier.height(8.dp))
        HermesTextField(
            value = search,
            onValueChange = { search = it },
            label = stringResource(R.string.i18n_cron_search),
            modifier = Modifier.padding(horizontal = 14.dp)
        )
        Spacer(Modifier.height(8.dp))

        if (jobs.isEmpty()) {
            EmptyState(Icons.Default.Schedule, stringResource(R.string.i18n_cron_empty), stringResource(R.string.i18n_cron_empty_hint)) {
                Button(onClick = { create = true }) { Text(stringResource(R.string.ui_d5e520f554)) }
            }
        } else if (filtered.isEmpty()) {
            EmptyState(Icons.Default.SearchOff, stringResource(R.string.a22_no_results), stringResource(R.string.i18n_cron_no_matches_hint))
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filtered, key = { "${it.profile}:${it.id}" }) { job ->
                    FlatRow(
                        leading = {
                            StateDot(
                                good = job.enabled && job.lastError.isBlank(),
                                warning = job.enabled && job.lastError.isNotBlank()
                            )
                        },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onTrigger(job) }) { Icon(Icons.Default.PlayArrow, stringResource(R.string.ui_0991397702)) }
                                IconButton(onClick = { if (job.enabled) onPause(job) else onResume(job) }) {
                                    Icon(if (job.enabled) Icons.Default.Pause else Icons.Default.PlayCircle, stringResource(if (job.enabled) R.string.routines_pause else R.string.routines_resume))
                                }
                                IconButton(onClick = {
                                    editorJob = job
                                    detail = null
                                }) { Icon(Icons.Default.Edit, stringResource(R.string.ui_464c4ffd01)) }
                            }
                        },
                        onClick = {
                            detail = job
                            onLoadRuns(job)
                        }
                    ) {
                        Text(job.name.ifBlank { stringResource(R.string.i18n_cron_routine) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(job.profile, job.scheduleDisplay.ifBlank { job.schedule }, job.deliver)
                                .filter(String::isNotBlank).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (job.prompt.isNotBlank()) {
                            Text(
                                job.prompt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
                }

                if (blueprints.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.ui_a398de9c57), blueprints.size.toString()) }
                    items(blueprints, key = { it.id }) { item ->
                        FlatRow(
                            title = item.name,
                            description = item.description.ifBlank { item.category },
                            leading = { Icon(Icons.Default.AutoAwesome, null) },
                            trailing = { Icon(Icons.Default.ChevronRight, null) },
                            onClick = { blueprint = item }
                        )
                    }
                }
            }
        }
    }

    if (create) {
        CronEditorDialog(
            profiles = profiles,
            defaultProfile = selectedProfile,
            deliveryTargets = deliveryTargets,
            job = null,
            onDismiss = { create = false },
            onSave = { profile, draft -> create = false; onCreate(profile, draft) }
        )
    }

    editorJob?.let { job ->
        CronEditorDialog(
            profiles = profiles,
            defaultProfile = job.profile,
            deliveryTargets = deliveryTargets,
            job = job,
            onDismiss = { editorJob = null },
            onSave = { _, draft -> editorJob = null; onUpdate(job, draft) }
        )
    }

    detail?.let { job ->
        CronDetailDialog(
            job = job,
            runs = runs["${job.profile}:${job.id}"].orEmpty(),
            onDismiss = { detail = null },
            onEdit = { detail = null; editorJob = job },
            onRun = { onTrigger(job); onLoadRuns(job) },
            onPauseResume = { if (job.enabled) onPause(job) else onResume(job) },
            onRefreshRuns = { onLoadRuns(job) },
            onDelete = { detail = null; onDelete(job) }
        )
    }

    blueprint?.let { item ->
        BlueprintDialog(
            blueprint = item,
            profile = selectedProfile,
            allBlueprints = blueprints,
            onSelect = { blueprint = it },
            onDismiss = { blueprint = null },
            onCreate = { values -> blueprint = null; onInstantiateBlueprint(selectedProfile, item, values) }
        )
    }
}

@Composable
private fun CronEditorDialog(
    profiles: List<HermesProfile>,
    defaultProfile: String,
    deliveryTargets: List<CronDeliveryTarget>,
    job: CronJob?,
    onDismiss: () -> Unit,
    onSave: (String, CronDraft) -> Unit
) {
    var profile by remember(job, defaultProfile) { mutableStateOf(job?.profile ?: defaultProfile) }
    var name by remember(job) { mutableStateOf(job?.name.orEmpty()) }
    var prompt by remember(job) { mutableStateOf(job?.prompt.orEmpty()) }
    var schedule by remember(job) { mutableStateOf(job?.schedule.orEmpty().ifBlank { "0 8 * * *" }) }
    var deliver by remember(job) { mutableStateOf(job?.deliver.orEmpty().ifBlank { "local" }) }
    var model by remember(job) { mutableStateOf(job?.model.orEmpty()) }
    var provider by remember(job) { mutableStateOf(job?.provider.orEmpty()) }
    var reasoning by remember(job) { mutableStateOf(job?.reasoningEffort.orEmpty()) }
    var skills by remember(job) { mutableStateOf(job?.skills?.joinToString(", ").orEmpty()) }
    var script by remember(job) { mutableStateOf(job?.script.orEmpty()) }
    var workdir by remember(job) { mutableStateOf(job?.workdir.orEmpty()) }
    var repeat by remember(job) { mutableStateOf(job?.repeat?.toString().orEmpty()) }
    var noAgent by remember(job) { mutableStateOf(job?.noAgent == true) }
    val parsedSkills = skills.split(',').map(String::trim).filter(String::isNotBlank)
    val hasRunnablePayload = prompt.isNotBlank() || script.isNotBlank() || parsedSkills.isNotEmpty()
    val noAgentValid = !noAgent || script.isNotBlank()
    var advanced by remember(job) {
        mutableStateOf(job != null && listOf(model, provider, reasoning, skills, script, workdir, repeat).any(String::isNotBlank) || noAgent)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (job == null) R.string.i18n_cron_new else R.string.i18n_cron_edit)) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 590.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                if (job == null && profiles.size > 1) {
                    Text(stringResource(R.string.ui_d696a35bdd), style = MaterialTheme.typography.labelMedium)
                    profiles.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(profile == p.name, { profile = p.name })
                            Text(p.label)
                        }
                    }
                } else {
                    Text(stringResource(R.string.i18n_profile_named, profile), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HermesTextField(name, { name = it }, stringResource(R.string.i18n_name))
                HermesTextField(prompt, { prompt = it }, stringResource(R.string.i18n_cron_prompt), singleLine = false)
                HermesTextField(schedule, { schedule = it }, stringResource(R.string.i18n_cron_schedule), placeholder = stringResource(R.string.i18n_cron_schedule_hint))

                Text(stringResource(R.string.ui_52bfe584a5), style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(deliveryTargets, key = { it.id }) { target ->
                        val current = deliver.split(',').map(String::trim).filter(String::isNotBlank).toMutableSet()
                        val selected = target.id in current
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (selected) current.remove(target.id) else current.add(target.id)
                                deliver = current.ifEmpty { mutableSetOf("local") }.joinToString(",")
                            },
                            label = { Text(target.name) },
                            enabled = target.configured || selected
                        )
                    }
                }
                HermesTextField(deliver, { deliver = it }, stringResource(R.string.i18n_cron_targets), placeholder = "local,telegram")

                TextButton(onClick = { advanced = !advanced }) {
                    Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (advanced) R.string.i18n_hide_advanced else R.string.i18n_advanced))
                }
                if (advanced) {
                    HermesTextField(model, { model = it }, stringResource(R.string.i18n_cron_model), placeholder = stringResource(R.string.i18n_optional))
                    HermesTextField(provider, { provider = it }, stringResource(R.string.i18n_cron_provider), placeholder = stringResource(R.string.i18n_optional))
                    HermesTextField(reasoning, { reasoning = it }, stringResource(R.string.i18n_cron_reasoning), placeholder = stringResource(R.string.i18n_optional))
                    HermesTextField(skills, { skills = it }, stringResource(R.string.i18n_skills), placeholder = "skill-a, skill-b")
                    HermesTextField(script, { script = it }, stringResource(R.string.i18n_cron_script), placeholder = stringResource(R.string.i18n_cron_script_hint))
                    HermesTextField(workdir, { workdir = it }, stringResource(R.string.i18n_cron_workdir), placeholder = stringResource(R.string.i18n_optional))
                    HermesTextField(repeat, { repeat = it.filter(Char::isDigit) }, stringResource(R.string.i18n_cron_repeat), placeholder = stringResource(R.string.i18n_cron_repeat_hint))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(noAgent, { noAgent = it })
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(stringResource(R.string.ui_eef146185e))
                            Text(stringResource(R.string.ui_f1a545c743), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (noAgent && script.isBlank()) {
                        Text(
                            stringResource(R.string.i18n_cron_no_agent_needs_script),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = hasRunnablePayload && schedule.isNotBlank() && noAgentValid,
                onClick = {
                    onSave(
                        profile,
                        CronDraft(
                            name = name.trim(),
                            prompt = prompt.trim(),
                            schedule = schedule.trim(),
                            deliver = deliver.trim().ifBlank { "local" },
                            model = model.trim(),
                            provider = provider.trim(),
                            reasoningEffort = reasoning.trim(),
                            skills = parsedSkills,
                            script = script.trim(),
                            noAgent = noAgent,
                            workdir = workdir.trim(),
                            repeat = repeat.toIntOrNull()
                        )
                    )
                }
            ) { Text(stringResource(if (job == null) R.string.i18n_create else R.string.i18n_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_19766ed6cc)) } }
    )
}

@Composable
private fun CronDetailDialog(
    job: CronJob,
    runs: List<CronRun>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onPauseResume: () -> Unit,
    onRefreshRuns: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(job.name.ifBlank { stringResource(R.string.i18n_cron_routine) }) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 590.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailLine(stringResource(R.string.ui_d696a35bdd), job.profile)
                DetailLine(stringResource(R.string.i18n_state), job.state.ifBlank { stringResource(if (job.enabled) R.string.i18n_enabled else R.string.routines_state_paused) })
                DetailLine(stringResource(R.string.i18n_cron_schedule), job.scheduleDisplay.ifBlank { job.schedule })
                DetailLine(stringResource(R.string.i18n_cron_delivery), job.deliver)
                if (job.model.isNotBlank() || job.provider.isNotBlank()) DetailLine(stringResource(R.string.i18n_model), listOf(job.provider, job.model).filter(String::isNotBlank).joinToString(" / "))
                if (job.skills.isNotEmpty()) DetailLine(stringResource(R.string.i18n_skills), job.skills.joinToString(", "))
                job.repeat?.let { DetailLine(stringResource(R.string.i18n_cron_repeats), "${job.completedRuns}/$it") }
                job.nextRunAt?.let { DetailLine(stringResource(R.string.i18n_cron_next_run), formatEpoch(it)) }
                job.lastRunAt?.let { DetailLine(stringResource(R.string.i18n_cron_last_run), formatEpoch(it)) }
                if (job.lastStatus.isNotBlank()) DetailLine(stringResource(R.string.i18n_cron_last_status), job.lastStatus)
                if (job.lastError.isNotBlank()) {
                    Text(stringResource(R.string.ui_5488d837f5), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    Text(job.lastError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Text(stringResource(R.string.ui_5c39123805), style = MaterialTheme.typography.labelMedium)
                Text(job.prompt, style = MaterialTheme.typography.bodyMedium)

                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ui_addf321bfa), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onRefreshRuns) { Icon(Icons.Default.Refresh, stringResource(R.string.i18n_refresh)) }
                }
                if (runs.isEmpty()) {
                    Text(stringResource(R.string.ui_9a80852174), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    runs.take(20).forEach { run ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StateDot(good = run.status.lowercase() in setOf("complete", "completed", "success", "done"), warning = run.status.lowercase() in setOf("running", "queued"))
                                Spacer(Modifier.width(8.dp))
                                Text(run.title.ifBlank { run.id.ifBlank { stringResource(R.string.i18n_cron_run) } }, style = MaterialTheme.typography.bodyMedium)
                            }
                            val meta = listOf(run.status, run.startedAt?.let(::formatEpoch).orEmpty()).filter(String::isNotBlank).joinToString(" · ")
                            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (run.preview.isNotBlank()) Text(run.preview, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onRun) { Text(stringResource(R.string.ui_00d60e31a4)) }
                TextButton(onClick = onPauseResume) { Text(stringResource(if (job.enabled) R.string.routines_pause else R.string.routines_resume)) }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.ui_464c4ffd01)) }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.ui_e2d0a54968)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_7d9eb7acb1)) }
            }
        }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    if (value.isBlank()) return
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BlueprintDialog(
    blueprint: AutomationBlueprint,
    profile: String,
    allBlueprints: List<AutomationBlueprint>,
    onSelect: (AutomationBlueprint) -> Unit,
    onDismiss: () -> Unit,
    onCreate: (Map<String, String>) -> Unit
) {
    val values = remember(blueprint.id) { mutableStateMapOf<String, String>().apply { blueprint.fields.forEach { put(it.key, it.defaultValue) } } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(blueprint.name) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(stringResource(R.string.i18n_profile_named, profile), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (allBlueprints.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(allBlueprints, key = { it.id }) { candidate ->
                            FilterChip(candidate.id == blueprint.id, { onSelect(candidate) }, { Text(candidate.name) })
                        }
                    }
                }
                if (blueprint.description.isNotBlank()) Text(blueprint.description, style = MaterialTheme.typography.bodyMedium)
                if (blueprint.fields.isEmpty()) {
                    Text(stringResource(R.string.ui_483034eff1), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                blueprint.fields.forEach { field ->
                    HermesTextField(
                        value = values[field.key].orEmpty(),
                        onValueChange = { values[field.key] = it },
                        label = field.label + if (field.required) " *" else "",
                        placeholder = field.placeholder,
                        singleLine = field.type.lowercase() !in setOf("textarea", "multiline", "prompt")
                    )
                    if (field.description.isNotBlank()) Text(field.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (field.options.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(field.options) { option ->
                                FilterChip(values[field.key] == option, { values[field.key] = option }, { Text(option) })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val ready = blueprint.fields.filter { it.required }.all { values[it.key].orEmpty().isNotBlank() }
            TextButton(enabled = ready, onClick = { onCreate(values.toMap()) }) { Text(stringResource(R.string.ui_4759498ac2)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_19766ed6cc)) } }
    )
}

private fun formatEpoch(value: Long): String = runCatching {
    val millis = if (value < 10_000_000_000L) value * 1000L else value
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))
}.getOrDefault(value.toString())
