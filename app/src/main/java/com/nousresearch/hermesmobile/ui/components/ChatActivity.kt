package com.nousresearch.hermesmobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.data.ChatMessage
import com.nousresearch.hermesmobile.data.ToolActivity
import com.nousresearch.hermesmobile.ui.theme.HermesTokens
import com.nousresearch.hermesmobile.ui.theme.hermes

private data class ActivityStep(val name: String, val detail: String, val status: String = "", val reasoning: Boolean = false)

@Composable
fun ChatActivityBlock(messages: List<ChatMessage>) {
    Column {
        ActivityPanel(messages.map {
            ActivityStep(it.toolName, if(it.role == "tool") it.content else it.reasoning,
                if(it.live) "running" else "", it.role != "tool")
        })
        remember(messages) { messages.filter { it.role == "tool" }
            .flatMap { com.nousresearch.hermesmobile.data.ChatAudio.fromTool(it.content) }.distinct().take(12) }
            .forEach { ChatAudioPlayer(it) }
    }
}

@Composable
fun CompactToolActivity(tools: List<ToolActivity>) {
    if(tools.isEmpty()) return
    ActivityPanel(tools.map { ActivityStep(it.name, it.detail, it.status) })
}

@Composable
private fun activityName(step: ActivityStep): String = when {
    step.reasoning -> stringResource(R.string.activity_reasoning)
    step.name == "terminal" -> stringResource(R.string.activity_terminal)
    step.name == "execute_code" -> stringResource(R.string.activity_code)
    step.name == "text_to_speech" -> stringResource(R.string.activity_speech)
    step.name.isBlank() -> stringResource(R.string.activity_tool)
    else -> step.name.replace('_', ' ')
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityPanel(steps: List<ActivityStep>) {
    if(steps.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val running = steps.any { it.status in setOf("running", "generating") }
    val failed = steps.any { it.status == "error" }
    val toolCount = steps.count { !it.reasoning }
    val label = stringResource(when {
        failed -> R.string.activity_needs_attention
        running -> R.string.activity_working
        toolCount == 0 -> R.string.activity_reasoning
        else -> R.string.activity_title
    })
    val summary = if(toolCount > 0) stringResource(R.string.activity_tool_count, toolCount) else ""
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        DashedDivider(Modifier.weight(1f))
        Surface(
            color = when {
                failed -> MaterialTheme.hermes.redTint
                running -> MaterialTheme.hermes.accentTint
                else -> MaterialTheme.hermes.greenTint
            },
            contentColor = when {
                failed -> MaterialTheme.colorScheme.error
                running -> MaterialTheme.colorScheme.primary
                else -> HermesTokens.Success.takeIf { MaterialTheme.hermes.isDark } ?: HermesTokens.SuccessLight
            },
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, MaterialTheme.hermes.line),
            modifier = Modifier
                .weight(1f, fill = false)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.activity_open)) { open = true }
        ) {
            Row(
                Modifier.heightIn(min = 30.dp).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (running) ThinkingIndicator(dotSize = 5.dp, spacing = 3.dp)
                else Icon(if (failed) Icons.Default.ErrorOutline else Icons.Default.CheckCircleOutline, null, Modifier.size(14.dp))
                Text(
                    if (running || failed) listOf(label, summary).filter { it.isNotBlank() }.joinToString(" · ")
                    else summary.ifBlank { label },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Default.ChevronRight, null, Modifier.size(14.dp))
            }
        }
        DashedDivider(Modifier.weight(1f))
    }
    if(open) ModalBottomSheet(onDismissRequest = { open = false }) {
        Column(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.activity_title), Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium)
            // Reference-style stat chips: actions · insights · status.
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (toolCount > 0) ActivityStatChip(stringResource(R.string.activity_tool_count, toolCount), MaterialTheme.hermes.accentTint, MaterialTheme.colorScheme.primary)
                val insightCount = steps.count { it.reasoning }
                if (insightCount > 0) ActivityStatChip(androidx.compose.ui.res.pluralStringResource(R.plurals.a22_insights, insightCount, insightCount), MaterialTheme.hermes.greenTint,
                    HermesTokens.Success.takeIf { MaterialTheme.hermes.isDark } ?: HermesTokens.SuccessLight)
                when {
                    failed -> ActivityStatChip(label, MaterialTheme.hermes.redTint, MaterialTheme.colorScheme.error)
                    running -> ActivityStatChip(label, MaterialTheme.hermes.orangeTint, HermesTokens.Warning.takeIf { MaterialTheme.hermes.isDark } ?: HermesTokens.WarningLight)
                    toolCount == 0 && insightCount == 0 -> ActivityStatChip(label, MaterialTheme.hermes.accentTint, MaterialTheme.colorScheme.primary)
                }
            }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(steps) { index, step ->
                var expanded by remember(index) { mutableStateOf(step.reasoning) }
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().clickable(enabled = step.detail.isNotBlank(), role = Role.Button) { expanded = !expanded }
                        .heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if(step.status in setOf("running", "generating")) ThinkingIndicator(dotSize = 5.dp, spacing = 3.dp)
                        else Icon(if(step.status == "error") Icons.Default.ErrorOutline else if(step.reasoning) Icons.Default.Psychology else Icons.Default.Build,
                            null, Modifier.size(16.dp), tint = if(step.status == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(activityName(step), Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.bodyMedium)
                        if(step.detail.isNotBlank()) Icon(if(expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            stringResource(if(expanded) R.string.activity_hide_detail else R.string.activity_show_detail), Modifier.size(18.dp))
                    }
                    if(expanded && step.detail.isNotBlank()) {
                        if (step.reasoning) {
                            // Reasoning renders as an italic quote with a brand bar.
                            Surface(
                                color = MaterialTheme.hermes.codeBlock,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.hermes.codeBlockBorder),
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                            ) {
                                Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                                    Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                                    SelectionContainer {
                                        Text(step.detail.take(16_000), Modifier.padding(12.dp).fillMaxWidth(),
                                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        } else {
                            SelectionContainer {
                                Surface(
                                    color = MaterialTheme.hermes.codeBlock,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.hermes.codeBlockBorder),
                                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                                ) {
                                    Text(step.detail.take(16_000), Modifier.padding(12.dp).fillMaxWidth(),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = com.nousresearch.hermesmobile.ui.theme.HermesMono))
                                }
                            }
                        }
                        if(step.detail.length > 16_000) Text(stringResource(R.string.activity_truncated),
                            Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/** Tiny stat pill for the Activity sheet header. */
@Composable
private fun ActivityStatChip(text: String, tint: Color, fg: Color) {
    Surface(color = tint, contentColor = fg, shape = androidx.compose.foundation.shape.RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
