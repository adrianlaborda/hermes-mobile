package com.nousresearch.hermesmobile.ui

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nousresearch.hermesmobile.R
import java.util.Locale

object AppLanguage {
    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return context
        val tag = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("language", "").orEmpty()
        if (tag.isEmpty()) return context
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag(tag))) })
    }
    fun selected(context: Context): String = if (Build.VERSION.SDK_INT >= 33)
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        else context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("language", "").orEmpty()
    fun set(context: Context, tag: String) {
        require(tag in setOf("", "en", "es"))
        if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        else { context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("language", tag).apply(); (context as? Activity)?.recreate() }
    }
}

@Composable
fun LanguageSettings() {
    val context = LocalContext.current
    val selected = AppLanguage.selected(context)
    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("" to stringResource(R.string.system_language), "es" to "Español", "en" to "English").forEach { (tag, label) ->
                FilterChip(selected == tag, onClick = { AppLanguage.set(context, tag) }, label = { Text(label) })
            }
        }
    }
}
