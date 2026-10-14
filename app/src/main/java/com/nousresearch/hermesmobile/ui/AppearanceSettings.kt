package com.nousresearch.hermesmobile.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.nousresearch.hermesmobile.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.nousresearch.hermesmobile.ui.components.FlatRow
import com.nousresearch.hermesmobile.ui.theme.DesktopTheme
import com.nousresearch.hermesmobile.ui.theme.DesktopThemes
import com.nousresearch.hermesmobile.ui.theme.hermes

@Composable
fun rememberAppearance(): String {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { context.getSharedPreferences("appearance", Context.MODE_PRIVATE) }
    var mode by remember(prefs) { mutableStateOf(prefs.getString("theme", "system") ?: "system") }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if(key == "theme") mode = p.getString("theme", "system") ?: "system"
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return mode
}

/**
 * The theme preferences: `skin_choice` (blank = follow the server's Hermes skin, else a Desktop built-in)
 * and `server_skin`, the skin the connected server last announced.
 */
object ThemeStore {
    const val CHOICE = "skin_choice"
    const val SERVER_SKIN = "server_skin"
    fun prefs(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    /** Keeps only what the app paints from (`name`, `colors`): skins also carry banners and CSS. */
    fun saveServerSkin(context: Context, skin: org.json.JSONObject?) {
        val slim = skin?.let { org.json.JSONObject().put("name", it.optString("name")).put("colors", it.optJSONObject("colors") ?: org.json.JSONObject()) }
        val value = slim?.toString()
        if (prefs(context).getString(SERVER_SKIN, null) != value) prefs(context).edit { putString(SERVER_SKIN, value) }
    }

    fun serverSkin(prefs: SharedPreferences): org.json.JSONObject? =
        prefs.getString(SERVER_SKIN, null)?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
}

/** The Desktop theme to paint, following the theme choice and the server's skin live. */
@Composable
fun rememberDesktopTheme(): com.nousresearch.hermesmobile.ui.theme.DesktopTheme {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { ThemeStore.prefs(context) }
    fun read() = com.nousresearch.hermesmobile.ui.theme.DesktopThemes.resolve(prefs.getString(ThemeStore.CHOICE, "").orEmpty(), ThemeStore.serverSkin(prefs))
    var theme by remember(prefs) { mutableStateOf(read()) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == ThemeStore.CHOICE || key == ThemeStore.SERVER_SKIN) theme = read()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return theme
}

@Composable
fun AppearanceSettings() {
    val mode = rememberAppearance()
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark).forEach { (key, label) ->
                FilterChip(selected = mode == key, onClick = {
                    context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("theme", key).apply()
                }, label = { Text(stringResource(label)) })
            }
        }
    }
    ThemePicker()
}

/** Settings → Theme: follow the server's Hermes skin (as Desktop does) or pin one of Desktop's built-ins. */
@Composable
private fun ThemePicker() {
    val context = LocalContext.current
    val prefs = remember(context) { ThemeStore.prefs(context) }
    var choice by remember(prefs) { mutableStateOf(prefs.getString(ThemeStore.CHOICE, "").orEmpty()) }
    val dark = MaterialTheme.hermes.isDark
    val server = remember(choice) { DesktopThemes.fromServerSkin(ThemeStore.serverSkin(prefs)) }
    var open by remember { mutableStateOf(false) }
    fun pick(name: String) { choice = name; prefs.edit { putString(ThemeStore.CHOICE, name) }; open = false }
    val current = if (choice.isBlank()) server else DesktopThemes.builtIn[choice] ?: DesktopThemes.nous
    FlatRow(
        title = stringResource(R.string.theme_title),
        description = if (choice.isBlank()) stringResource(R.string.theme_follow_hermes, server.label) else current.label,
        leading = { ThemeSwatch(current, dark) },
        trailing = { Icon(Icons.Default.ArrowDropDown, null) },
        onClick = { open = !open }
    )
    if (open) {
        ThemeOption(stringResource(R.string.theme_follow_hermes, server.label), stringResource(R.string.theme_follow_hint),
            server, dark, choice.isBlank()) { pick("") }
        DesktopThemes.builtIn.values.forEach { theme ->
            ThemeOption(theme.label, null, theme, dark, choice == theme.name) { pick(theme.name) }
        }
    }
}

@Composable
private fun ThemeOption(title: String, hint: String?, theme: DesktopTheme, dark: Boolean, selected: Boolean, onClick: () -> Unit) {
    FlatRow(
        title = title, description = hint,
        modifier = Modifier.padding(start = 16.dp),
        leading = { ThemeSwatch(theme, dark) },
        trailing = { if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = onClick
    )
}

/** The theme's page with its accent, as Desktop's theme cards show it. */
@Composable
private fun ThemeSwatch(theme: DesktopTheme, dark: Boolean) {
    val palette = theme.paletteFor(dark)
    Box(
        Modifier.size(26.dp).clip(CircleShape).background(Color(palette.background))
            .border(1.dp, Color(palette.border), CircleShape),
        contentAlignment = Alignment.Center
    ) { Box(Modifier.size(11.dp).clip(CircleShape).background(Color(palette.primary))) }
}
