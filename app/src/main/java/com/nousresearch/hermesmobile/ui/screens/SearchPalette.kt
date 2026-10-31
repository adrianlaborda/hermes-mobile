package com.nousresearch.hermesmobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nousresearch.hermesmobile.HermesUiState
import com.nousresearch.hermesmobile.MainSection
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.data.HermesSession
import com.nousresearch.hermesmobile.data.LiveSessions
import com.nousresearch.hermesmobile.data.PaletteSearch
import com.nousresearch.hermesmobile.data.SessionDot
import com.nousresearch.hermesmobile.sectionTitle
import com.nousresearch.hermesmobile.ui.components.SectionLabel
import com.nousresearch.hermesmobile.ui.components.SessionStatusDot
import com.nousresearch.hermesmobile.ui.components.runningArc
import com.nousresearch.hermesmobile.ui.theme.hermes
import kotlinx.coroutines.delay

/** The pages the palette can go to, in the side menu's order, with its icons. */
private val PALETTE_PAGES: List<Pair<MainSection, ImageVector>> = listOf(
    MainSection.HOME to Icons.Default.SpaceDashboard,
    MainSection.CHAT to Icons.AutoMirrored.Filled.Chat,
    MainSection.BOTS to Icons.Default.SmartToy,
    MainSection.KANBAN to Icons.Default.ViewKanban,
    MainSection.CAPABILITIES to Icons.Default.Psychology,
    MainSection.MESSAGING to Icons.Default.Hub,
    MainSection.FILES to Icons.Default.FolderOpen,
    MainSection.CRON to Icons.Default.Schedule,
    MainSection.MEMORY to Icons.Default.AutoAwesome,
    MainSection.PROFILES to Icons.Default.ManageAccounts,
    MainSection.MORE to Icons.Default.Settings,
    MainSection.MODELS to Icons.Default.Memory,
    MainSection.PROVIDERS to Icons.Default.Key,
    MainSection.VOICE to Icons.Default.GraphicEq,
    MainSection.ASSISTANT to Icons.Default.Mic,
    MainSection.APPEARANCE to Icons.Default.Palette,
    MainSection.NOTIFICATIONS to Icons.Default.Notifications,
    MainSection.SERVER_SETTINGS to Icons.Default.Tune,
    MainSection.GATEWAYS to Icons.Default.Dns,
    MainSection.SYSTEM to Icons.Default.MonitorHeart,
    MainSection.ABOUT to Icons.Default.Info
)

private class PaletteEntry(val key: String, val label: String, val keywords: String, val icon: ImageVector, val run: () -> Unit)

/**
 * Desktop's command palette (⌘K) as a full-screen sheet: one field that finds chats (titles of
 * the loaded ones at once, then the server's full-text hits), pages and quick actions. With no
 * query it lists the recent chats, so it doubles as a quick switcher.
 */
@Composable
fun SearchPalette(
    s: HermesUiState,
    kanbanAvailable: Boolean,
    onDismiss: () -> Unit,
    onOpenSession: (HermesSession) -> Unit,
    onSection: (MainSection) -> Unit,
    onNewChat: () -> Unit,
    onProfile: (String) -> Unit,
    serverSearch: suspend (String) -> List<HermesSession>
) {
    var query by rememberSaveable { mutableStateOf("") }
    var remote by remember { mutableStateOf<List<HermesSession>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        remote = emptyList()
        if (query.isBlank()) { searching = false; return@LaunchedEffect }
        delay(250)
        searching = true
        remote = serverSearch(query)
        searching = false
    }

    val context = LocalContext.current
    val connected = s.connectionState == com.nousresearch.hermesmobile.data.GatewayConnectionState.CONNECTED
    val visible = remember(s.sessions) {
        HermesSession.collapseLineages(s.sessions).filterNot { it.hidden || it.archived || it.isRoutineRun || it.isInternalRun }
    }
    val chats = remember(visible, remote, query) {
        if (query.isBlank()) visible.sortedByDescending { it.lastActive }.take(6)
        else PaletteSearch.mergeSessions(PaletteSearch.sessions(visible, query), remote).take(30)
    }

    fun close(then: () -> Unit) = { onDismiss(); then() }
    val pages = PALETTE_PAGES.filter { it.first != MainSection.KANBAN || kanbanAvailable }.map { (section, icon) ->
        PaletteEntry("page:${section.name}", stringResource(sectionTitle(section)), section.name.replace('_', ' '), icon, close { onSection(section) })
    }
    val appearance = listOf(
        "light" to (R.string.theme_light to Icons.Default.LightMode),
        "dark" to (R.string.theme_dark to Icons.Default.DarkMode),
        "system" to (R.string.theme_system to Icons.Default.BrightnessAuto)
    ).map { (mode, res) ->
        PaletteEntry("theme:$mode", stringResource(R.string.palette_appearance, stringResource(res.first)), "theme $mode", res.second, close {
            context.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE).edit().putString("theme", mode).apply()
        })
    }
    val actions = buildList {
        if (connected) add(PaletteEntry("new", stringResource(R.string.a14_new_chat), "new chat", Icons.Outlined.AddComment, close(onNewChat)))
        s.profiles.map { it.name }.filter { it != s.selectedProfile }.forEach { name ->
            add(PaletteEntry("profile:$name", stringResource(R.string.palette_switch_profile, name), "profile", Icons.Default.AccountCircle, close { onProfile(name) }))
        }
        addAll(appearance)
    }
    fun ranked(entries: List<PaletteEntry>) =
        if (query.isBlank()) entries
        else entries.map { it to PaletteSearch.score(it.label, query, it.keywords) }.filter { it.second > 0 }
            .sortedByDescending { it.second }.map { it.first }
    // Without a query, actions stay short: a new chat; profiles and appearance modes appear as you type.
    val shownActions = if (query.isBlank()) actions.filter { it.key == "new" } else ranked(actions)
    val shownPages = ranked(pages)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                PaletteHeader(query, { query = it }, searching, onDismiss) {
                    // Enter runs the best match, as in Desktop.
                    chats.firstOrNull()?.let { close { onOpenSession(it) }() }
                        ?: (shownActions.firstOrNull() ?: shownPages.firstOrNull())?.run?.invoke()
                }
                HorizontalDivider(color = MaterialTheme.hermes.line)
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
                    if (chats.isNotEmpty()) {
                        item(key = "h:chats") { SectionLabel(stringResource(if (query.isBlank()) R.string.a21_recent else R.string.palette_chats)) }
                        items(chats, key = { "chat:${it.profile}:${it.id}" }) { session ->
                            PaletteSessionRow(session, LiveSessions.dotFor(s.sessionDots, session.id, session.unread), query, close { onOpenSession(session) })
                        }
                    }
                    if (searching && query.isNotBlank()) item(key = "searching") {
                        Text(stringResource(R.string.palette_searching), Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (shownActions.isNotEmpty()) {
                        item(key = "h:actions") { SectionLabel(stringResource(R.string.palette_actions)) }
                        items(shownActions, key = { it.key }) { PaletteEntryRow(it, query) }
                    }
                    if (shownPages.isNotEmpty()) {
                        item(key = "h:pages") { SectionLabel(stringResource(R.string.palette_go_to)) }
                        items(shownPages, key = { it.key }) { PaletteEntryRow(it, query) }
                    }
                    if (query.isNotBlank() && !searching && chats.isEmpty() && shownPages.isEmpty() && shownActions.isEmpty()) item(key = "empty") {
                        Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SearchOff, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(10.dp))
                            Text(stringResource(R.string.palette_no_results, query.trim()), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** The search line as the sheet's top bar: no box, the field is the header (Desktop's palette input). */
@Composable
private fun PaletteHeader(query: String, onQuery: (String) -> Unit, searching: Boolean, onBack: () -> Unit, onGo: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.a21_back)) }
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f).focusRequester(focus),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(stringResource(R.string.palette_hint), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    field()
                }
            }
        )
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            when {
                searching -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                query.isNotEmpty() -> IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Default.Close, stringResource(R.string.a22_clear_search), Modifier.size(20.dp))
                }
            }
        }
    }
}

/** A dense palette row: small leading glyph, one line of title, optional muted second line and trailing meta. */
@Composable
private fun PaletteRow(
    title: AnnotatedString,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: AnnotatedString? = null,
    trailing: String? = null,
    bold: Boolean = false,
    leading: @Composable () -> Unit
) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 46.dp).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.let {
            Spacer(Modifier.width(12.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PaletteEntryRow(entry: PaletteEntry, query: String) {
    PaletteRow(highlighted(entry.label, query), entry.run) {
        Icon(entry.icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PaletteSessionRow(session: HermesSession, dot: SessionDot, query: String, onClick: () -> Unit) {
    // A chat's status as everywhere else: the dot in the lead slot, the arc while its turn runs.
    // When the match is in the text (a server hit's snippet, a loaded chat's preview), show it under the title.
    val snippet = session.preview.trim().takeIf { query.isNotBlank() && PaletteSearch.fold(query) in PaletteSearch.fold(it) }
    PaletteRow(
        highlighted(session.displayTitle.ifBlank { stringResource(R.string.a21_untitled) }, query),
        onClick,
        modifier = Modifier.runningArc(dot == SessionDot.WORKING),
        subtitle = snippet?.let { highlighted(it.lineSequence().joinToString(" "), query) },
        trailing = relativeDate(session.lastActive),
        bold = dot != SessionDot.NONE
    ) {
        if (dot != SessionDot.NONE) SessionStatusDot(dot)
        else Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** [text] with the first case-insensitive occurrence of [query] in the accent color. */
@Composable
private fun highlighted(text: String, query: String): AnnotatedString {
    val q = query.trim()
    val at = if (q.isEmpty()) -1 else text.indexOf(q, ignoreCase = true)
    if (at < 0) return AnnotatedString(text)
    val accent = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        append(text)
        addStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold), at, at + q.length)
    }
}
