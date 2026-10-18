package com.nousresearch.hermesmobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScheduleSend
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.data.*
import com.nousresearch.hermesmobile.features.VoiceActions
import com.nousresearch.hermesmobile.ui.components.*
import com.nousresearch.hermesmobile.ui.theme.HermesMono
import com.nousresearch.hermesmobile.ui.theme.contextPressure
import com.nousresearch.hermesmobile.ui.theme.hermes

/**
 * Desktop-style composer card: input on top, a control row underneath with
 * attach/actions, model + reasoning pill, context ring and the primary action.
 */
@Composable
internal fun ChatComposer(
    chat: ActiveChat,
    draft: String,
    attachments: List<ComposerAttachment>,
    voice: VoiceActions?,
    sessionUsage: SessionUsage?,
    onDraftChange: (String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onAttach: () -> Unit,
    onModel: () -> Unit,
    onContext: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onSteer: (String) -> Unit
) {
    var composerMenu by remember(chat.storedSessionId, chat.profile) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val hasMessage = draft.isNotBlank() || attachments.isNotEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.hermes.field,
        border = BorderStroke(1.dp, MaterialTheme.hermes.lineStrong)
    ) {
        Column(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
            if (voice != null && (voice.recording || voice.busy || voice.speaking || voice.status != null)) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    when {
                        voice.busy -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        voice.recording -> Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                        voice.speaking -> ThinkingIndicator(dotSize = 4.dp, spacing = 2.dp)
                        else -> {}
                    }
                    Text(
                        when {
                            voice.recording -> stringResource(R.string.chat_recording)
                            voice.busy -> stringResource(R.string.chat_transcribing)
                            voice.speaking -> stringResource(R.string.chat_speaking)
                            else -> voice.status.orEmpty()
                        },
                        Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 2,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall
                    )
                    IconButton(onClick = voice.cancel) {
                        Icon(Icons.Default.Close, stringResource(R.string.chat_dismiss_voice), Modifier.size(18.dp))
                    }
                }
            }
            AttachmentStrip(attachments, onRemoveAttachment)
            TextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(if (chat.running) R.string.chat_queue_hint else R.string.a22_message_hint)) },
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodyLarge.let {
                    // Commands read as code, like Desktop's composer.
                    if (draft.startsWith("/")) it.copy(fontFamily = HermesMono) else it
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default)
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { composerMenu = true }) { Icon(Icons.Default.Add, stringResource(R.string.chat_more_actions)) }
                    DropdownMenu(expanded = composerMenu, onDismissRequest = { composerMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.chat_attach)) },
                            leadingIcon = { Icon(Icons.Default.AttachFile, null) },
                            enabled = attachments.size < 6,
                            onClick = { composerMenu = false; onAttach() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.a22_commands)) },
                            leadingIcon = { Icon(Icons.Default.Code, null) },
                            onClick = { composerMenu = false; if (!draft.startsWith("/")) onDraftChange("/") })
                        if (voice != null) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.chat_dictate)) },
                                leadingIcon = { Icon(Icons.Default.Mic, null) },
                                enabled = !voice.busy && !voice.speaking && !voice.recording,
                                onClick = { composerMenu = false; voice.dictate() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.chat_read_aloud)) },
                                leadingIcon = { Icon(Icons.Default.RecordVoiceOver, null) },
                                enabled = voice.canSpeak && !voice.speaking,
                                onClick = { composerMenu = false; voice.speak() })
                        }
                        if (chat.running) DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_steer)) },
                            leadingIcon = { Icon(Icons.Default.TurnRight, null) },
                            enabled = draft.isNotBlank() && attachments.isEmpty(),
                            onClick = { composerMenu = false; onSteer(draft); onDraftChange("") })
                    }
                }
                Box(Modifier.weight(1f)) { ModelPill(chat, enabled = !chat.running, onClick = onModel) }
                ComposerContextRing(sessionUsage, onContext)
                if (chat.running) {
                    IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.Reject); onStop() }) {
                        Icon(Icons.Default.StopCircle, stringResource(R.string.chat_stop), tint = MaterialTheme.colorScheme.error)
                    }
                }
                when {
                    voice?.recording == true -> FilledIconButton(onClick = voice.dictate) {
                        Icon(Icons.Default.Stop, stringResource(R.string.chat_finish_dictation))
                    }
                    !hasMessage && voice != null && !chat.running -> IconButton(
                        enabled = !voice.busy && !voice.speaking,
                        onClick = voice.dictate
                    ) { Icon(Icons.Default.Mic, stringResource(R.string.chat_dictate)) }
                    else -> FilledIconButton(enabled = hasMessage, onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onSend(draft)
                    }) {
                        Icon(if (chat.running) Icons.AutoMirrored.Filled.ScheduleSend else Icons.Default.ArrowUpward,
                            stringResource(if (chat.running) R.string.chat_queue else R.string.chat_send))
                    }
                }
            }
        }
    }
}

/**
 * Hermes Go-style context meter: always present (a dash until the first reading), the number
 * inside the ring, colored by how full the window is; tapping opens the context sheet.
 */
@Composable
private fun ComposerContextRing(usage: SessionUsage?, onClick: () -> Unit) {
    val pct = usage?.contextPercent
    val contextLabel = if (pct != null) stringResource(R.string.a21_context_used, pct) else stringResource(R.string.a23_context_window)
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = contextLabel, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        ContextRing(pct, Modifier.size(28.dp), color = MaterialTheme.hermes.contextPressure(pct), strokeWidth = 2.5.dp)
        Text(
            pct?.toString() ?: "–",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = if (pct != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Compact model · reasoning pill inside the composer, mirroring Desktop's model-pill. */
@Composable
private fun ModelPill(chat: ActiveChat, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val model = chat.model.substringAfterLast('/').ifBlank { stringResource(R.string.a14_model) }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            Modifier.heightIn(min = 40.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(model, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false))
            if (chat.reasoningEffort.isNotBlank()) {
                Text(
                    chat.reasoningEffort,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.hermes.accentTint)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
            Icon(Icons.Default.UnfoldMore, null, Modifier.size(14.dp))
        }
    }
}

@Composable
private fun AttachmentStrip(items: List<ComposerAttachment>, onRemove: (String) -> Unit) {
    if (items.isEmpty()) return
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(items, key = { it.id }) { item ->
            InputChip(
                selected = false,
                onClick = {},
                label = { Text(if (com.nousresearch.hermesmobile.data.TurnDisplay.isPastedContent(item.name)) stringResource(R.string.i18n_pasted_text) else item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp)) },
                leadingIcon = {
                    Icon(when (item.kind) {
                        "image" -> Icons.Default.Image
                        "pdf" -> Icons.Default.PictureAsPdf
                        else -> Icons.Default.AttachFile
                    }, null, Modifier.size(16.dp))
                },
                trailingIcon = {
                    IconButton(onClick = { onRemove(item.id) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, stringResource(R.string.a22_remove_named, item.name), Modifier.size(15.dp))
                    }
                }
            )
        }
    }
}

/**
 * Desktop's `/` palette. A bare `/` lists every command by category (skills last); typing narrows
 * it instantly, with the matched part of each name highlighted.
 */
@Composable
internal fun SlashSuggestions(palette: SlashPalette, onPick: (SlashCompletion) -> Unit) {
    val query = palette.query ?: return
    val commandStage = SlashCommands.commandStage(query)
    val items = palette.items
    // Arguments are free text for most commands: only the command stage explains an empty list.
    if (items.isEmpty() && !(commandStage || palette.loading)) return
    val token = if (commandStage) query.removePrefix("/").lowercase() else query.substringAfterLast(' ').lowercase()
    val listState = rememberLazyListState()
    LaunchedEffect(query) { if (items.isNotEmpty()) listState.scrollToItem(0) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.hermes.lineStrong),
        shadowElevation = 6.dp
    ) {
        when {
            items.isEmpty() && palette.loading -> Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.a23_loading_commands), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items.isEmpty() -> Text(
                stringResource(R.string.a23_no_commands, query),
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 320.dp),
                state = listState,
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                // groupBy keeps first-appearance order, so each header (and its key) occurs once.
                items.groupBy { it.section }.forEach { (section, rows) ->
                    item(key = "header:$section") { SlashSectionHeader(section, rows.size) }
                    items(rows, key = { "slash:" + it.text }) { SlashRow(it, token, onPick) }
                }
            }
        }
    }
}

@Composable
private fun SlashSectionHeader(section: String, count: Int) {
    val title = when (section) {
        SlashCommands.SECTION_COMMANDS, "" -> stringResource(R.string.a22_commands)
        SlashCommands.SECTION_SKILLS -> stringResource(R.string.a22_skills)
        else -> section
    }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SlashRow(item: SlashCompletion, token: String, onPick: (SlashCompletion) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val name = remember(item.display, token, accent) {
        buildAnnotatedString {
            append(item.display)
            val at = if (token.isEmpty()) -1 else item.display.lowercase().indexOf(token)
            if (at >= 0) addStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold), at, at + token.length)
        }
    }
    Row(
        Modifier.fillMaxWidth().clickable { onPick(item) }.heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                .background(if (item.skill) MaterialTheme.hermes.accentTint else MaterialTheme.hermes.field),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (item.skill) Icons.Default.AutoAwesome else Icons.Default.Code, null, Modifier.size(15.dp),
                tint = if (item.skill) accent else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = HermesMono),
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.meta.isNotBlank()) Text(item.meta, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun MentionSuggestions(candidates: List<Pair<HermesProfile, String>>, onPick: (String) -> Unit) {
    if (candidates.isEmpty()) return
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(candidates, key = { it.first.name }) { (profile, handle) ->
            AssistChip(
                onClick = { onPick(handle) },
                leadingIcon = { Icon(Icons.Default.AlternateEmail, null, Modifier.size(16.dp)) },
                label = { Text("$handle · ${profile.label}", maxLines = 1) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ModelReasoningDialog(
    chat: ActiveChat,
    choices: List<SessionModelChoice>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSwitch: (SessionModelChoice, String) -> Unit,
    onReasoning: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val conversationIdentity = ChatConversationIdentity.from(chat)
    var selected by remember(conversationIdentity, choices) {
        mutableStateOf(choices.firstOrNull { it.model == chat.model && it.provider == chat.provider } ?: SessionModelChoice(chat.provider, chat.model))
    }
    var reasoning by remember(conversationIdentity) { mutableStateOf(chat.reasoningEffort) }
    val filtered = remember(choices, query) {
        choices.filter { it.label.contains(query, true) || it.provider.contains(query, true) }.groupBy { it.provider }
    }
    // Hermes accepts this scale; the server resolves/clamps the wire value as in Desktop.
    val levels = listOf("", "none", "minimal", "low", "medium", "high", "xhigh", "max", "ultra")
        .filter { it != "none" || selected.canDisableReasoning != false }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.a14_model), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.a14_session_scope), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                query, { query = it },
                placeholder = { Text(stringResource(R.string.a14_models_search)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 320.dp)) {
                filtered.forEach { (provider, rows) ->
                    item(key = "provider:$provider") { SectionLabel(provider, rows.size.toString()) }
                    items(rows, key = { it.provider + ":" + it.model }) { choice ->
                        val isSelected = selected == choice
                        Surface(
                            color = if (isSelected) MaterialTheme.hermes.accentTint else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            FlatRow(choice.model, leading = { RadioButton(isSelected, onClick = null) }, onClick = {
                                selected = choice
                                if (choice.canDisableReasoning == false && reasoning == "none") reasoning = ""
                            })
                        }
                    }
                }
            }
            if (selected.supportsReasoning != false) {
                Text(stringResource(R.string.a14_reasoning), style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    levels.forEach { level ->
                        FilterChip(reasoning == level, { reasoning = level },
                            label = { Text(if (level.isBlank()) stringResource(R.string.a14_keep_reasoning) else level) })
                    }
                }
            }
            Button(enabled = !loading && !chat.running, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).heightIn(min = 48.dp), onClick = {
                selected.let {
                    if (it.provider == chat.provider && it.model == chat.model) {
                        if (reasoning.isNotBlank() && it.supportsReasoning != false) onReasoning(reasoning)
                    } else onSwitch(it, if (it.supportsReasoning == false) "" else reasoning)
                }
                onDismiss()
            }) { Text(stringResource(R.string.a14_apply)) }
        }
    }
}

@Composable
internal fun QueueEditDialog(item: QueuedPrompt, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(item.id) { mutableStateOf(item.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_250ac6f7d3)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value, { value = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8)
                if (item.attachments.isNotEmpty()) {
                    Text(
                        stringResource(R.string.a22_attachments_list, item.attachments.joinToString { it.name }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = value.isNotBlank() || item.attachments.isNotEmpty()) {
                Text(stringResource(R.string.ui_1509f561f2))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_19766ed6cc)) } }
    )
}
