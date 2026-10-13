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
import com.nousresearch.hermesmobile.R

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
}
