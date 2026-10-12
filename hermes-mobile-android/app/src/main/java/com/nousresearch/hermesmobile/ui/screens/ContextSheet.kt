package com.nousresearch.hermesmobile.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.data.*
import com.nousresearch.hermesmobile.ui.components.ContextRing
import com.nousresearch.hermesmobile.ui.components.StatusPill
import com.nousresearch.hermesmobile.ui.theme.HermesMono
import com.nousresearch.hermesmobile.ui.theme.contextPressure
import com.nousresearch.hermesmobile.ui.theme.contextSeries
import com.nousresearch.hermesmobile.ui.theme.hermes
import kotlinx.coroutines.CancellationException
import java.text.NumberFormat

/**
 * Hermes Go-style context window sheet: a large ring with used / max tokens, the stacked
 * category breakdown with each share, live session usage, the loaded context files and a
 * compress action that says whether compressing is worth it yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextWindowSheet(
    repo: HermesRepository,
    chat: ActiveChat,
    usage: SessionUsage?,
    onDismiss: () -> Unit,
    onCompress: () -> Unit
) {
    var breakdown by remember(repo, chat.runtimeSessionId) { mutableStateOf<ContextBreakdown?>(null) }
    var error by remember(repo, chat.runtimeSessionId) { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    // Re-measure on open, on refresh and when a turn settles (the transcript just grew).
    LaunchedEffect(repo, chat.runtimeSessionId, revision, chat.running) {
        if (chat.running && breakdown != null) return@LaunchedEffect
        loading = true; error = null
        try { breakdown = repo.contextBreakdown(chat.runtimeSessionId, chat.profile) }
        catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: e.javaClass.simpleName }
        finally { loading = false }
    }
    val numbers = remember { NumberFormat.getIntegerInstance() }
    val d = breakdown
    // The breakdown anchors on provider usage, so it wins; the live snapshot covers the first load.
    val used = d?.used?.takeIf { d.max > 0 } ?: usage?.contextUsed ?: 0L
    val max = d?.max?.takeIf { it > 0 } ?: usage?.contextLimit ?: 0L
    val pct = d?.percent ?: usage?.contextPercent
    val estimated = d?.estimated ?: usage?.contextEstimated ?: false

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 720.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.a23_context_window), style = MaterialTheme.typography.headlineSmall)
                        val model = listOf(chat.model.substringAfterLast('/'), chat.provider).filter { it.isNotBlank() }.joinToString(" · ")
                        if (model.isNotBlank()) Text(model, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    SheetIconButton(onClick = { revision++ }, enabled = !loading) {
                        if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, stringResource(R.string.a14_refresh), Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    SheetIconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.a23_close), Modifier.size(18.dp)) }
                }
            }
            item { ContextHero(pct, used, max, estimated, chat.running, numbers) }
            error?.let { message ->
                item {
                    Text(stringResource(R.string.a14_context_error) + "\n" + message, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            if (d != null) {
                item { SheetLabel(stringResource(R.string.a23_breakdown)) }
                if (d.categories.isEmpty()) item {
                    Text(stringResource(R.string.a14_no_breakdown), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    item { BreakdownBar(d) }
                    items(d.categories, key = { "cat:" + it.id }) { category -> CategoryRow(category, d.share(category), numbers) }
                }
            }
            if (usage != null && usage.hasTraffic) {
                item { SheetLabel(stringResource(R.string.a23_session_usage)) }
                item { UsageGrid(usage, numbers) }
            }
            if (d != null && d.files.isNotEmpty()) {
                item { SheetLabel(stringResource(R.string.a14_context_files)) }
                items(d.files, key = { "file:" + it.path + it.label }) { file -> ContextFileRow(file) }
            }
            item { CompressBlock(pct, chat.running) { onDismiss(); onCompress() } }
            item {
                com.nousresearch.hermesmobile.features.SessionInfoCard(stringResource(R.string.a23_session).uppercase(), listOf(
                    stringResource(R.string.a23_title) to chat.title.ifBlank { "—" },
                    stringResource(R.string.a23_session_id) to chat.storedSessionId.ifBlank { "—" },
                    stringResource(R.string.a23_profile) to chat.profile,
                    stringResource(R.string.a23_source) to sessionSourceInfo(chat.source).label
                ))
            }
        }
    }
}

@Composable
private fun SheetIconButton(onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.hermes.field,
        border = BorderStroke(1.dp, MaterialTheme.hermes.line),
        modifier = Modifier.size(40.dp)
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ContextHero(pct: Int?, used: Long, max: Long, estimated: Boolean, running: Boolean, numbers: NumberFormat) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(124.dp), contentAlignment = Alignment.Center) {
            ContextRing(pct, Modifier.fillMaxSize(), color = MaterialTheme.hermes.contextPressure(pct), strokeWidth = 10.dp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(pct?.toString() ?: "–", style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold)
                if (pct != null) Text("%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp, start = 1.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.a23_tokens, numbers.format(used)),
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold)
            if (max > 0) {
                Text(stringResource(R.string.a23_of_max, numbers.format(max)), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.a23_free, numbers.format((max - used).coerceAtLeast(0L))),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (estimated || running) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (running) StatusPill(stringResource(R.string.a23_live), good = true)
                    if (estimated) StatusPill(stringResource(R.string.a23_estimated), warning = true)
                }
            }
        }
    }
}

/** One stacked bar, each category's width = its share of the measured context. */
@Composable
private fun BreakdownBar(d: ContextBreakdown) {
    val palette = MaterialTheme.hermes
    Row(
        Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(palette.line),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        d.categories.filter { it.tokens > 0 }.forEach { category ->
            val share by animateFloatAsState(d.share(category).coerceAtLeast(0.004f), label = "share-" + category.id)
            Box(Modifier.weight(share).fillMaxHeight().background(palette.contextSeries(category.id)))
        }
    }
}

@Composable
private fun CategoryRow(category: ContextCategory, share: Float, numbers: NumberFormat) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.hermes.contextSeries(category.id)))
        Text(category.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(numbers.format(category.tokens), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
        Text(
            if (share > 0f && share < 0.01f) "<1%" else "${(share * 100).toInt()}%",
            Modifier.widthIn(min = 40.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun UsageGrid(usage: SessionUsage, numbers: NumberFormat) {
    val decimals = remember { NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 } }
    val tiles = buildList {
        add(stringResource(R.string.a23_input) to numbers.format(usage.inputTokens))
        add(stringResource(R.string.a23_output) to numbers.format(usage.outputTokens))
        if (usage.reasoningTokens > 0) add(stringResource(R.string.a23_reasoning) to numbers.format(usage.reasoningTokens))
        if (usage.calls > 0) add(stringResource(R.string.a23_calls) to numbers.format(usage.calls))
        usage.cacheHitPercent?.let { add(stringResource(R.string.a23_cache) to "$it%") }
        usage.avgTokensPerSecond?.let { add(stringResource(R.string.a23_speed) to stringResource(R.string.a23_tps, decimals.format(it))) }
        usage.avgLatencySeconds?.let { add(stringResource(R.string.a23_latency) to stringResource(R.string.a23_seconds, decimals.format(it))) }
        if (usage.compressions > 0) add(stringResource(R.string.a23_compressions) to numbers.format(usage.compressions))
        if (usage.activeSubagents > 0) add(stringResource(R.string.a23_subagents) to numbers.format(usage.activeSubagents))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (label, value) -> UsageTile(label, value, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun UsageTile(label: String, value: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.hermes.field,
        border = BorderStroke(1.dp, MaterialTheme.hermes.line)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ContextFileRow(file: ContextFile) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.Description, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(file.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (file.path.isNotBlank()) Text(file.path, style = MaterialTheme.typography.labelSmall.copy(fontFamily = HermesMono),
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (file.status.isNotBlank()) StatusPill(file.status, good = file.status.equals("loaded", true) || file.status.equals("active", true))
    }
}

@Composable
private fun CompressBlock(pct: Int?, running: Boolean, onCompress: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = MaterialTheme.hermes.line)
        Text(stringResource(R.string.a23_compress_explainer), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val hint = when {
            running -> stringResource(R.string.a23_compress_running)
            pct == null -> null
            ContextPressure.of(pct) == ContextPressure.HIGH -> stringResource(R.string.a23_hint_high, pct)
            ContextPressure.of(pct) == ContextPressure.MEDIUM -> stringResource(R.string.a23_hint_medium, pct)
            else -> stringResource(R.string.a23_hint_low, pct)
        }
        hint?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                color = if (running) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.hermes.contextPressure(pct))
        }
        FilledTonalButton(
            onClick = onCompress,
            enabled = !running,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Compress, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.a23_compress_now))
        }
    }
}
