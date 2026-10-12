package com.nousresearch.hermesmobile.ui.screens

import com.nousresearch.hermesmobile.R
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nousresearch.hermesmobile.data.HermesProfile
import com.nousresearch.hermesmobile.ui.components.*
import com.nousresearch.hermesmobile.ui.theme.hermes

@Composable
fun ProfilesScreen(
    profiles: List<HermesProfile>,
    busy: Boolean,
    lastExport: String?,
    onBack: () -> Unit,
    onCreate: (String, String, String?) -> Unit,
    onRename: (HermesProfile, String) -> Unit,
    onDelete: (HermesProfile) -> Unit,
    onExport: (HermesProfile) -> Unit,
    onImport: (String, String?) -> Unit
) {
    var create by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<HermesProfile?>(null) }
    var delete by remember { mutableStateOf<HermesProfile?>(null) }
    var showImport by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        HermesTopBar(
            "Profiles",
            "Manage profile identity and portable archives",
            leading = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { showImport = true }) { Icon(Icons.Default.FileDownload, "Import profile") }
                IconButton(onClick = { create = true }) { Icon(Icons.Default.Add, stringResource(R.string.ui_61d30d997d)) }
            }
        )
        DashedDivider(color = MaterialTheme.hermes.line)
        if (!lastExport.isNullOrBlank()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Archive, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ui_f0ed9acf03), style = MaterialTheme.typography.labelMedium)
                        Text(lastExport, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(profiles.sortedWith(compareByDescending<HermesProfile> { it.isDefault }.thenBy { it.name }), key = { it.name }) { profile ->
                var menu by remember(profile.name) { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileAvatar(profile, size = 38)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(profile.label, style = MaterialTheme.typography.titleSmall)
                            if (profile.isDefault) {
                                Spacer(Modifier.width(8.dp))
                                AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.ui_21b111cbfe)) })
                            }
                        }
                        Text(
                            listOfNotNull(
                                profile.name,
                                profile.provider.takeIf { it.isNotBlank() }?.let { p -> profile.model.takeIf { it.isNotBlank() }?.let { "$p · $it" } ?: p }
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (profile.description.isNotBlank()) Text(
                            profile.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !busy) { Icon(Icons.Default.MoreVert, "Profile actions") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (!profile.isDefault) DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_3064d79a29)) },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { menu = false; rename = profile }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_a7232fd49d)) },
                                leadingIcon = { Icon(Icons.Default.Archive, null) },
                                onClick = { menu = false; onExport(profile) }
                            )
                            if (!profile.isDefault) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ui_e2d0a54968), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menu = false; delete = profile }
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .2f))
            }
        }
    }

    if (create) ProfileCreateDialog(profiles, { create = false }) { name, description, clone ->
        create = false
        onCreate(name, description, clone)
    }
    rename?.let { profile ->
        var value by remember(profile.name) { mutableStateOf(profile.name) }
        AlertDialog(
            onDismissRequest = { rename = null },
            title = { Text(stringResource(R.string.ui_c9aaa25628)) },
            text = { HermesTextField(value, { value = it }, "New profile name") },
            confirmButton = { TextButton(onClick = { rename = null; onRename(profile, value) }) { Text(stringResource(R.string.ui_3064d79a29)) } },
            dismissButton = { TextButton(onClick = { rename = null }) { Text(stringResource(R.string.ui_19766ed6cc)) } }
        )
    }
    delete?.let { profile ->
        AlertDialog(
            onDismissRequest = { delete = null },
            title = { Text("Delete ${profile.label}?") },
            text = { Text(stringResource(R.string.ui_e1b6e5a2b7)) },
            confirmButton = { TextButton(onClick = { delete = null; onDelete(profile) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.ui_e2d0a54968)) } },
            dismissButton = { TextButton(onClick = { delete = null }) { Text(stringResource(R.string.ui_19766ed6cc)) } }
        )
    }
    if (showImport) {
        var archive by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text(stringResource(R.string.ui_c9ababd3d1)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.ui_61b75fb595), style = MaterialTheme.typography.bodySmall)
                    HermesTextField(archive, { archive = it }, "Gateway archive path", placeholder = "/home/hermes/profile.tar.gz")
                    HermesTextField(name, { name = it }, "New name (optional)")
                }
            },
            confirmButton = { TextButton(enabled = archive.isNotBlank(), onClick = { showImport = false; onImport(archive, name.ifBlank { null }) }) { Text(stringResource(R.string.ui_2cff9baabf)) } },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text(stringResource(R.string.ui_19766ed6cc)) } }
        )
    }
}

@Composable
private fun ProfileCreateDialog(
    profiles: List<HermesProfile>,
    onDismiss: () -> Unit,
    onCreate: (String, String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var clone by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_61d30d997d)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HermesTextField(name, { name = it }, "Profile name", placeholder = "research")
                HermesTextField(description, { description = it }, "Description", singleLine = false)
                Text(stringResource(R.string.ui_5c56b5acdd), style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = clone == null, onClick = { clone = null })
                    Text(stringResource(R.string.ui_ba46560275))
                }
                profiles.take(12).forEach { profile ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = clone == profile.name, onClick = { clone = profile.name })
                        Text("Clone ${profile.label}")
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name, description, clone) }) { Text(stringResource(R.string.ui_4759498ac2)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_19766ed6cc)) } }
    )
}
