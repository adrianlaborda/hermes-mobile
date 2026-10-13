@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nousresearch.hermesmobile.ui.screens

import com.nousresearch.hermesmobile.R
import androidx.compose.ui.res.stringResource

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import com.nousresearch.hermesmobile.data.BotCapability
import com.nousresearch.hermesmobile.data.BotProfileConfig
import com.nousresearch.hermesmobile.ui.components.HermesTextField
import com.nousresearch.hermesmobile.ui.components.ProfileAvatar

private enum class BotEditorTab(val label: String) {
    GENERAL("General"), SOUL("SOUL"), CAPABILITIES("Capabilities")
}

@Composable
fun BotEditorDialog(
    config: BotProfileConfig,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (BotProfileConfig) -> Unit
) {
    var tab by remember(config.profile.name, config.uiMetaRevision) { mutableStateOf(BotEditorTab.GENERAL) }
    var title by remember(config.profile.name, config.uiMetaRevision) { mutableStateOf(config.title) }
    var description by remember(config.profile.name, config.uiMetaRevision) { mutableStateOf(config.description) }
    var soul by remember(config.profile.name, config.uiMetaRevision) { mutableStateOf(config.soul) }
    var provider by remember(config.profile.name, config.uiMetaRevision) { mutableStateOf(config.provider) }
    var model by remember(config.profile.name, config.uiMetaRevision) { mutableStateOf(config.model) }
    var shape by remember(config.profile.name, config.uiMetaRevision) { mutableStateOf(config.shape) }
    var color by remember(config.profile.name, config.uiMetaRevision) { mutableStateOf(config.color) }
    var hidden by remember(config.profile.name, config.uiMetaRevision) { mutableStateOf(config.hidden) }
    var pinned by remember(config.profile.name, config.uiMetaRevision) { mutableStateOf(config.pinned) }
    var skills by remember(config.profile.name, config.uiMetaRevision) { mutableStateOf(config.skills) }
    var toolsets by remember(config.profile.name, config.uiMetaRevision) { mutableStateOf(config.toolsets) }
    var mcp by remember(config.profile.name, config.uiMetaRevision) { mutableStateOf(config.mcpServers) }
    var avatarDataUrl by remember(config.profile.name, config.uiMetaRevision) { mutableStateOf(config.avatarDataUrl) }

    fun updated() = config.copy(
        title = title,
        description = description,
        soul = soul,
        provider = provider,
        model = model,
        shape = shape,
        color = color,
        hidden = hidden,
        pinned = pinned,
        skills = skills,
        toolsets = toolsets,
        mcpServers = mcp,
        avatarDataUrl = avatarDataUrl
    )

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(.92f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ui_c0cd1fd055), style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${config.profile.label} · ${config.profile.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(enabled = !saving, onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.ui_7d9eb7acb1)) }
                }

                SecondaryTabRow(selectedTabIndex = tab.ordinal) {
                    BotEditorTab.entries.forEach { item ->
                        Tab(
                            selected = tab == item,
                            onClick = { tab = item },
                            text = { Text(item.label) }
                        )
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        BotEditorTab.GENERAL -> GeneralBotEditor(
                            config = config,
                            title = title,
                            onTitle = { title = it },
                            description = description,
                            onDescription = { description = it },
                            provider = provider,
                            onProvider = { value ->
                                provider = value
                                val selected = config.modelProviders.firstOrNull { it.slug == value }
                                if (selected != null && model !in selected.models) model = selected.models.firstOrNull().orEmpty()
                            },
                            model = model,
                            onModel = { model = it },
                            shape = shape,
                            onShape = { shape = it },
                            color = color,
                            onColor = { color = it },
                            hidden = hidden,
                            onHidden = { hidden = it },
                            pinned = pinned,
                            onPinned = { pinned = it },
                            avatarDataUrl = avatarDataUrl,
                            onAvatarDataUrl = { avatarDataUrl = it }
                        )
                        BotEditorTab.SOUL -> SoulEditor(soul, { soul = it })
                        BotEditorTab.CAPABILITIES -> CapabilitiesEditor(
                            skills = skills,
                            onSkills = { skills = it },
                            toolsets = toolsets,
                            onToolsets = { toolsets = it },
                            mcp = mcp,
                            onMcp = { mcp = it }
                        )
                    }
                }

                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.ui_19766ed6cc)) }
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = !saving && title.isNotBlank(), onClick = { onSave(updated()) }) {
                        if (saving) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (saving) "Saving" else "Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneralBotEditor(
    config: BotProfileConfig,
    title: String,
    onTitle: (String) -> Unit,
    description: String,
    onDescription: (String) -> Unit,
    provider: String,
    onProvider: (String) -> Unit,
    model: String,
    onModel: (String) -> Unit,
    shape: String,
    onShape: (String) -> Unit,
    color: String,
    onColor: (String) -> Unit,
    hidden: Boolean,
    onHidden: (Boolean) -> Unit,
    pinned: Boolean,
    onPinned: (Boolean) -> Unit,
    avatarDataUrl: String?,
    onAvatarDataUrl: (String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var avatarBusy by remember { mutableStateOf(false) }
    var avatarError by remember { mutableStateOf<String?>(null) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                avatarBusy = true
                avatarError = null
                runCatching { avatarDataUrlFromUri(context, uri) }
                    .onSuccess(onAvatarDataUrl)
                    .onFailure { avatarError = it.message ?: "Could not read this image" }
                avatarBusy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(config.profile, size = 64, avatarDataUrl = avatarDataUrl)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(config.profile.name, style = MaterialTheme.typography.labelLarge)
                Text(
                    "Canonical Bot Chat follows this profile's current configuration.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(enabled = !avatarBusy, onClick = { avatarPicker.launch("image/*") }) {
                        Text(if (avatarBusy) "Processing…" else if (avatarDataUrl == null) "Add photo" else "Change photo")
                    }
                    if (avatarDataUrl != null) {
                        TextButton(enabled = !avatarBusy, onClick = { onAvatarDataUrl(null) }) { Text(stringResource(R.string.ui_c3812fc4ac)) }
                    }
                }
                avatarError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
        HermesTextField(title, onTitle, "Display name", placeholder = config.profile.name)
        HermesTextField(description, onDescription, "Description", singleLine = false)

        Text(stringResource(R.string.ui_5e2c614c23), style = MaterialTheme.typography.titleSmall)
        HermesTextField(provider, onProvider, "Provider", placeholder = "nous / openrouter / custom")
        if (config.modelProviders.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                config.modelProviders.forEach { option ->
                    AssistChip(onClick = { onProvider(option.slug) }, label = { Text(option.name.ifBlank { option.slug }) })
                }
            }
        }
        HermesTextField(model, onModel, "Model", placeholder = "provider/model")
        val models = config.modelProviders.firstOrNull { it.slug == provider }?.models.orEmpty()
        if (models.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                models.forEach { candidate ->
                    AssistChip(onClick = { onModel(candidate) }, label = { Text(candidate, maxLines = 1) })
                }
            }
        }

        Text(stringResource(R.string.ui_3907fa7f80), style = MaterialTheme.typography.titleSmall)
        HermesTextField(shape, onShape, "Avatar shape", placeholder = "blobatar")
        HermesTextField(color, onColor, "Avatar color", placeholder = "Optional")

        SettingSwitch("Pinned in Bots", "Keep this bot prominent in the roster.", pinned, onPinned)
        SettingSwitch("Hidden bot", "Hide from normal Bot Mode sections while preserving the profile and its chat.", hidden, onHidden)
    }
}

@Composable
private fun SoulEditor(value: String, onValue: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.ui_bde0ac766b), style = MaterialTheme.typography.titleMedium)
        Text(
            "The bot's persistent personality and instructions. Hermes injects the Bot Mode teammate protocol separately on current gateways.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth().weight(1f),
            textStyle = LocalTextStyle.current.copy(fontFamily = com.nousresearch.hermesmobile.ui.theme.HermesMono),
            label = { Text(stringResource(R.string.ui_b1a226d594)) },
            singleLine = false
        )
    }
}

@Composable
private fun CapabilitiesEditor(
    skills: List<BotCapability>,
    onSkills: (List<BotCapability>) -> Unit,
    toolsets: List<BotCapability>,
    onToolsets: (List<BotCapability>) -> Unit,
    mcp: List<BotCapability>,
    onMcp: (List<BotCapability>) -> Unit
) {
    var section by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = section) {
            listOf("Skills (${skills.count { it.enabled }})", "Tools (${toolsets.count { it.enabled }})", "MCP (${mcp.count { it.enabled }})")
                .forEachIndexed { index, label ->
                    Tab(selected = section == index, onClick = { section = index }, text = { Text(label) })
                }
        }
        when (section) {
            0 -> CapabilityChecklist(skills, onSkills, "Filter skills")
            1 -> CapabilityChecklist(toolsets, onToolsets, "Filter toolsets")
            else -> CapabilityChecklist(mcp, onMcp, "Filter MCP servers", mcpMode = true)
        }
    }
}

@Composable
private fun CapabilityChecklist(
    entries: List<BotCapability>,
    onEntries: (List<BotCapability>) -> Unit,
    placeholder: String,
    mcpMode: Boolean = false
) {
    var query by remember { mutableStateOf("") }
    val visible = remember(entries, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) entries else entries.filter { it.name.lowercase().contains(q) || it.label.lowercase().contains(q) }
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text(placeholder) }
        )
        Spacer(Modifier.height(8.dp))
        if (visible.isEmpty()) {
            Text(stringResource(R.string.ui_02ee725b14), modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(visible, key = { it.name }) { item ->
                    val needsSetup = mcpMode && item.fromCatalog && !item.installed &&
                        (item.requires.isNotEmpty() || item.auth.equals("oauth", true))
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 7.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Checkbox(
                            checked = item.enabled,
                            enabled = !needsSetup,
                            onCheckedChange = { checked ->
                                onEntries(entries.map { if (it.name == item.name) it.copy(enabled = checked) else it })
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.label.ifBlank { item.name }, style = MaterialTheme.typography.bodyMedium)
                            val detail = when {
                                needsSetup -> "Requires credential/OAuth setup before it can be enabled."
                                item.description.isNotBlank() -> item.description
                                item.transport.isNotBlank() -> item.transport
                                else -> ""
                            }
                            if (detail.isNotBlank()) {
                                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .25f))
                }
            }
        }
    }
}

private suspend fun avatarDataUrlFromUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val raw = context.contentResolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > 25_000_000) error("Image is too large to process on mobile")
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    } ?: error("Could not open the selected image")

    // Read dimensions first and downsample during decode. Camera JPEGs can be
    // tens of megapixels even when the compressed file is only a few MB;
    // decoding the full bitmap before resizing would create avoidable OOM risk.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("Unsupported image")
    var sample = 1
    while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > 2048) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(
        raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample }
    ) ?: error("Unsupported image")
    val maxSide = 1024
    val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height).coerceAtLeast(1))
    val bitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true
        ).also { if (it !== decoded) decoded.recycle() }
    } else decoded

    var quality = 90
    var bytes: ByteArray
    do {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bytes = out.toByteArray()
        quality -= 10
    } while (bytes.size > 1_900_000 && quality >= 50)
    bitmap.recycle()
    if (bytes.size > 2_000_000) error("Avatar is still larger than Hermes' 2 MB limit")
    "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

