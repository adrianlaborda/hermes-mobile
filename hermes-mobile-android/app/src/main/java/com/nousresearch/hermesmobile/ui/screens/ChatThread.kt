package com.nousresearch.hermesmobile.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nousresearch.hermesmobile.CommandOutput
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.data.*
import com.nousresearch.hermesmobile.features.VoiceActions
import com.nousresearch.hermesmobile.ui.components.*
import com.nousresearch.hermesmobile.ui.theme.HermesMono
import com.nousresearch.hermesmobile.ui.theme.hermes
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Max width of the transcript and composer column on tablets and foldables. */
private val ReadableWidth = 820.dp

/** Callbacks of the open conversation; grouped so ChatScreen stays readable. */
internal class ChatThreadActions(
    val onLoadOlderMessages: () -> Unit,
    val onDraftChange: (String) -> Unit,
    val onAttach: (List<Uri>) -> Unit,
    val onRemoveAttachment: (String) -> Unit,
    val onRemoveQueued: (String) -> Unit,
    val onUpdateQueued: (String, String) -> Unit,
    val onMoveQueued: (String, Int) -> Unit,
    val onClearQueue: () -> Unit,
    val onSwitchModel: (SessionModelChoice, String) -> Unit,
    val onSetReasoning: (String) -> Unit,
    val onRefreshSessionMeta: () -> Unit,
    val onSend: (String) -> Unit,
    val onStop: () -> Unit,
    val onSteer: (String) -> Unit,
    val onRetry: () -> Unit,
    val onBranch: () -> Unit,
    val onApplySlash: (SlashCompletion) -> Unit,
    val onDismissCommandOutput: () -> Unit,
    val onBack: () -> Unit,
    val onOpen: (HermesSession) -> Unit,
    val onNew: (String, String) -> Unit,
    val onOpenArtifact: (String) -> Unit,
    val onContext: () -> Unit
)

@Composable
internal fun ChatThread(
    chat: ActiveChat,
    sessions: List<HermesSession>,
    profiles: List<HermesProfile>,
    streamingText: String,
    loadingOlderMessages: Boolean,
    tools: List<ToolActivity>,
    draft: String,
    attachments: List<ComposerAttachment>,
    queuedPrompts: List<QueuedPrompt>,
    reasoningText: String,
    liveStatuses: List<LiveTaskStatus>,
    artifactSuggestions: List<ArtifactSuggestion>,
    voiceControls: @Composable (@Composable (VoiceActions?) -> Unit) -> Unit,
    sessionModelChoices: List<SessionModelChoice>,
    sessionUsage: SessionUsage?,
    modelOptionsLoading: Boolean,
    slashPalette: SlashPalette,
    commandOutput: CommandOutput?,
    actions: ChatThreadActions,
    twoPane: Boolean = false
) {
    val conversationIdentity = ChatConversationIdentity.from(chat)
    var showModelDialog by remember(conversationIdentity) { mutableStateOf(false) }
    var editQueueItem by remember(conversationIdentity) { mutableStateOf<QueuedPrompt?>(null) }
    var showSwitcher by remember { mutableStateOf(false) }
    var showArtifacts by remember { mutableStateOf(false) }
    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) actions.onAttach(uris)
    }
    // Reverse layout: index 0 is the bottom lane, so a fresh state already rests on the
    // latest message. See ChatScrollPolicy.kt for why there is no follow scroll.
    val state = key(conversationIdentity) { rememberLazyListState() }
    val timeline = remember(chat.messages, streamingText, reasoningText) {
        chatTimeline(chat.messages + if (streamingText.isNotBlank() || reasoningText.isNotBlank())
            listOf(ChatMessage(role = "assistant", content = streamingText, reasoning = reasoningText, live = true)) else emptyList())
    }
    val displayRows = remember(timeline) { chatDisplayRows(timeline) }
    val entries = remember(displayRows, chat.hasOlderMessages) {
        reverseChatEntries(displayRows, chat.hasOlderMessages) { it.key }
    }
    val messageKeys = remember(timeline) { timeline.filterIsInstance<ChatTimelineItem.Message>().map { it.key } }
    val turnEnds = remember(displayRows, chat.running) {
        assistantTurnEnds(
            displayRows,
            key = { it.key },
            role = { row ->
                when ((row.entry as? ChatTimelineItem.Message)?.message?.role) {
                    "user" -> TurnRole.USER
                    "assistant" -> TurnRole.ASSISTANT
                    else -> TurnRole.OTHER
                }
            },
            actionable = { row ->
                val message = (row.entry as? ChatTimelineItem.Message)?.message
                message != null && !message.live && message.content.isNotBlank() && message.displayKind.isBlank()
            },
            newestTurnOpen = chat.running
        )
    }
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val mentionQuery = remember(draft) { Regex("(?:^|\\s)@([A-Za-z0-9._:-]*)$").find(draft)?.groupValues?.getOrNull(1) }
    val mentionCandidates = remember(profiles, chat.profile, mentionQuery) {
        val q = mentionQuery?.lowercase() ?: return@remember emptyList()
        profiles.filter { it.isBot && it.name != chat.profile }
            .map { it to if (it.name == "default") "hermes" else it.name }
            .filter { (profile, handle) -> handle.lowercase().startsWith(q) || profile.label.lowercase().startsWith(q) }
            .take(8)
    }

    val atLatest by remember(state) { derivedStateOf { isAtLatest(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) } }
    val jumpThresholdPx = with(density) { 48.dp.roundToPx() }
    val showJump by remember(state, jumpThresholdPx) {
        derivedStateOf { shouldShowJumpToLatest(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, jumpThresholdPx) }
    }
    // Newest message when the reader left the bottom; drives the "new messages" badge.
    var unseenBaseline by remember(conversationIdentity) { mutableStateOf<Any?>(null) }
    LaunchedEffect(atLatest, conversationIdentity) {
        unseenBaseline = if (atLatest) null else unseenBaseline ?: messageKeys.lastOrNull()
    }
    val unseen = unseenMessageCount(atLatest, unseenBaseline, messageKeys)

    // Infinite history: page older messages in as the reader nears the top.
    val hasOlder by rememberUpdatedState(chat.hasOlderMessages)
    val loadingOlder by rememberUpdatedState(loadingOlderMessages)
    val loadOlder by rememberUpdatedState(actions.onLoadOlderMessages)
    LaunchedEffect(state) {
        snapshotFlow {
            val info = state.layoutInfo
            shouldLoadOlder(info.visibleItemsInfo.lastOrNull()?.index, info.totalItemsCount, hasOlder, loadingOlder)
        }.distinctUntilChanged().collect { if (it) loadOlder() }
    }

    // A gentle tick when a long turn finishes, so the phone can be put down while Hermes works.
    var turnStartedAt by remember(conversationIdentity) { mutableLongStateOf(0L) }
    LaunchedEffect(chat.running, conversationIdentity) {
        val now = System.currentTimeMillis()
        if (chat.running) turnStartedAt = now
        else {
            if (turnStartedAt > 0L && now - turnStartedAt >= 12_000L) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            turnStartedAt = 0L
        }
    }

    fun jumpToLatest() {
        scope.launch {
            // Long jumps snap first so the animation stays short.
            if (state.firstVisibleItemIndex > 12) state.scrollToItem(6)
            state.animateScrollToItem(0)
        }
    }
    val send: (String) -> Unit = send@{ text ->
        if (SlashCommands.local(text) == LocalSlash.MODEL) {
            actions.onDraftChange("")
            actions.onRefreshSessionMeta()
            showModelDialog = true
            return@send
        }
        actions.onSend(text)
        // Sending is the one moment we always re-pin the reader to the latest message.
        state.requestScrollToItem(0)
    }
    val copy: (String) -> Unit = { text ->
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        scope.launch { clipboard.setClipEntry(android.content.ClipData.newPlainText("Hermes", text).toClipEntry()) }
    }
    val share: (String) -> Unit = { text ->
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            .putExtra(Intent.EXTRA_SUBJECT, chat.title)
        runCatching { context.startActivity(Intent.createChooser(send, null)) }
    }

    voiceControls { voice ->
        Column(Modifier.fillMaxSize().imePadding()) {
            ChatThreadTopBar(
                chat = chat,
                artifactCount = artifactSuggestions.size,
                showBack = !twoPane,
                onBack = actions.onBack,
                onSwitcher = { showSwitcher = true },
                onArtifacts = { showArtifacts = true },
                onNewChat = { actions.onNew(chat.profile, "") }
            )

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // Desktop keeps a readable column on wide screens; the list still scrolls edge to edge.
                val sideGutter = ((maxWidth - ReadableWidth) / 2).coerceAtLeast(0.dp)
                if (displayRows.isEmpty() && !chat.hasOlderMessages) {
                    ChatIntro(chat, onSuggestion = { actions.onDraftChange(it) })
                }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = state,
                    reverseLayout = true,
                    verticalArrangement = Arrangement.Top,
                    contentPadding = PaddingValues(start = sideGutter, end = sideGutter, top = 8.dp)
                ) {
                    items(entries, key = { it.key }, contentType = { entry ->
                        when (entry) {
                            ChatListEntry.Bottom -> 0
                            ChatListEntry.Older -> 1
                            is ChatListEntry.Row -> if (entry.row.dayMillis != null) 2 else 3
                        }
                    }) { entry ->
                        when (entry) {
                            ChatListEntry.Bottom -> Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                                LiveStatusLane(liveStatuses)
                                ToolActivityLane(tools.filter { it.status in setOf("running", "generating", "error") })
                                ArtifactLane(artifactSuggestions, actions.onOpenArtifact)
                                QueueLane(
                                    queuedPrompts = queuedPrompts,
                                    onEdit = { editQueueItem = it },
                                    onRemove = actions.onRemoveQueued,
                                    onMove = actions.onMoveQueued,
                                    onClear = actions.onClearQueue
                                )
                            }
                            ChatListEntry.Older -> Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                if (loadingOlderMessages) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else TextButton(onClick = actions.onLoadOlderMessages) { Text(stringResource(R.string.ui_bf2424a0ba)) }
                            }
                            is ChatListEntry.Row -> ChatTranscriptRow(
                                row = entry.row,
                                turnEnd = entry.row.key in turnEnds.keys,
                                canRetry = entry.row.key == turnEnds.retryKey && !chat.running,
                                voice = voice,
                                onEdit = { actions.onDraftChange(it) },
                                onCopy = copy,
                                onShare = share,
                                onRetry = actions.onRetry,
                                onBranch = actions.onBranch
                            )
                        }
                    }
                }
                JumpToLatestButton(
                    visible = showJump,
                    unseen = unseen,
                    onClick = ::jumpToLatest,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                )
            }

            Column(Modifier.widthIn(max = ReadableWidth).align(Alignment.CenterHorizontally)) {
            if (SlashCommands.query(draft) != null) SlashSuggestions(slashPalette, actions.onApplySlash)
            MentionSuggestions(mentionCandidates) { handle ->
                val at = draft.lastIndexOf('@')
                actions.onDraftChange(if (at >= 0) draft.substring(0, at) + "@$handle " else "$draft @$handle ")
            }
            ChatComposer(
                chat = chat,
                draft = draft,
                attachments = attachments,
                voice = voice,
                sessionUsage = sessionUsage,
                onDraftChange = actions.onDraftChange,
                onRemoveAttachment = actions.onRemoveAttachment,
                onAttach = { attachmentLauncher.launch(arrayOf("*/*")) },
                onModel = { actions.onRefreshSessionMeta(); showModelDialog = true },
                onContext = actions.onContext,
                onSend = send,
                onStop = actions.onStop,
                onSteer = actions.onSteer
            )
            }
        }
    }

    if (showSwitcher) {
        SwitchSessionSheet(
            chat = chat,
            sessions = sessions,
            onDismiss = { showSwitcher = false },
            onOpen = { showSwitcher = false; actions.onOpen(it) },
            onNewChat = { showSwitcher = false; actions.onNew(chat.profile, "") },
            onAllSessions = { showSwitcher = false; actions.onBack() }
        )
    }
    if (showArtifacts) {
        ArtifactsSheet(
            artifacts = artifactSuggestions,
            onDismiss = { showArtifacts = false },
            onOpen = { showArtifacts = false; actions.onOpenArtifact(it) }
        )
    }
    if (showModelDialog) {
        ModelReasoningDialog(
            chat = chat,
            choices = sessionModelChoices,
            loading = modelOptionsLoading,
            onDismiss = { showModelDialog = false },
            onSwitch = { choice, reasoning ->
                actions.onSwitchModel(choice, reasoning)
                showModelDialog = false
            },
            onReasoning = actions.onSetReasoning
        )
    }
    editQueueItem?.let { item ->
        QueueEditDialog(item = item, onDismiss = { editQueueItem = null }) { value ->
            actions.onUpdateQueued(item.id, value)
            editQueueItem = null
        }
    }
    commandOutput?.let { output -> CommandOutputSheet(output, onCopy = copy, onDismiss = actions.onDismissCommandOutput) }
}

@Composable
private fun ChatTranscriptRow(
    row: ChatDisplayRow,
    turnEnd: Boolean,
    canRetry: Boolean,
    voice: VoiceActions?,
    onEdit: (String) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onRetry: () -> Unit,
    onBranch: () -> Unit
) {
    row.dayMillis?.let { DaySeparatorRow(dayLabel(it)); return }
    when (val entry = row.entry) {
        is ChatTimelineItem.Message -> {
            val message = entry.message
            val editable = message.role == "user" && message.content.isNotBlank()
            Column {
                ChatMessageBubble(
                    message,
                    onEdit = if (editable) ({ onEdit(message.content) }) else null,
                    onCopy = if (editable) ({ onCopy(message.content) }) else null
                )
                if (turnEnd) AssistantActions(message.content, canRetry, voice, onCopy, onShare, onRetry, onBranch)
            }
        }
        is ChatTimelineItem.Activity -> ChatActivityBlock(entry.steps)
        null -> {}
    }
}

/** Desktop's message toolbar, shown once under each finished agent answer. */
@Composable
private fun AssistantActions(
    text: String,
    canRetry: Boolean,
    voice: VoiceActions?,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onRetry: () -> Unit,
    onBranch: () -> Unit
) {
    Row(Modifier.padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        MessageAction(Icons.Default.ContentCopy, stringResource(R.string.a22_copy)) { onCopy(text) }
        MessageAction(Icons.Default.Share, stringResource(R.string.a22_share)) { onShare(text) }
        if (voice != null) {
            if (voice.speaking) MessageAction(Icons.Default.StopCircle, stringResource(R.string.a22_stop_reading), tint = MaterialTheme.colorScheme.primary) { voice.cancel() }
            else MessageAction(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.chat_read_aloud), enabled = !voice.busy && !voice.recording) {
                voice.speakText(text)
            }
        }
        if (canRetry) MessageAction(Icons.Default.Refresh, stringResource(R.string.a22_retry), onClick = onRetry)
        MessageAction(Icons.AutoMirrored.Filled.CallSplit, stringResource(R.string.a22_branch), onClick = onBranch)
    }
}

@Composable
private fun MessageAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f),
    onClick: () -> Unit
) {
    // 40dp visual, Material's minimum interactive size still applies to the touch target.
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(icon, label, Modifier.size(16.dp), tint = if (enabled) tint else tint.copy(alpha = .3f))
    }
}

/** Chat header: back, live state, tappable title (session switcher), files and new chat. */
@Composable
private fun ChatThreadTopBar(
    chat: ActiveChat,
    artifactCount: Int,
    showBack: Boolean,
    onBack: () -> Unit,
    onSwitcher: () -> Unit,
    onArtifacts: () -> Unit,
    onNewChat: () -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.a21_back)) }
            else Spacer(Modifier.width(10.dp))
            Row(
                Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable(onClick = onSwitcher)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f, fill = false)) {
                    Text(
                        chat.title.ifBlank { stringResource(R.string.a21_untitled) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (chat.running) {
                            ThinkingIndicator(dotSize = 4.dp, spacing = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            if (chat.running) stringResource(R.string.a21_working)
                            else listOf(sessionSourceInfo(chat.source).label, chat.profile).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (chat.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(Icons.Default.ExpandMore, stringResource(R.string.a21_switch_session), Modifier.padding(start = 2.dp).size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onArtifacts) {
                BadgedBox(badge = { if (artifactCount > 0) Badge { Text(artifactCount.coerceAtMost(99).toString()) } }) {
                    Icon(Icons.Default.FolderOpen, stringResource(R.string.a21_files))
                }
            }
            IconButton(onClick = onNewChat) { Icon(Icons.Default.Edit, stringResource(R.string.ui_db18382a24)) }
        }
        HorizontalDivider(color = MaterialTheme.hermes.line)
    }
}

/** Floating pill above the composer, like Desktop's thread jump button. */
@Composable
private fun JumpToLatestButton(visible: Boolean, unseen: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = .85f),
        exit = fadeOut() + scaleOut(targetScale = .85f)
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.hermes.lineStrong),
            shadowElevation = 3.dp
        ) {
            Row(
                Modifier.heightIn(min = 40.dp).padding(horizontal = if (unseen > 0) 14.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.ArrowDownward, stringResource(R.string.a21_jump_latest), Modifier.size(18.dp))
                if (unseen > 0) Text(
                    pluralStringResource(R.plurals.a21_new_messages, unseen, unseen),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** Empty-thread intro with Desktop-style suggestion pills that prefill the composer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatIntro(chat: ActiveChat, onSuggestion: (String) -> Unit) {
    val suggestions = listOf(
        Icons.Default.Checklist to stringResource(R.string.a21_suggest_plan),
        Icons.Default.Lightbulb to stringResource(R.string.a21_suggest_explain),
        Icons.Default.Summarize to stringResource(R.string.a21_suggest_summarize),
        Icons.Default.TravelExplore to stringResource(R.string.a21_suggest_research)
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HermesBrandMark(size = 48)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.a21_intro_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            listOf(chat.profile, chat.model.substringAfterLast('/')).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { (icon, suggestion) ->
                Surface(
                    onClick = { onSuggestion(suggestion) },
                    shape = RoundedCornerShape(50),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.hermes.lineStrong)
                ) {
                    Row(Modifier.heightIn(min = 40.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(suggestion, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.a22_intro_commands_hint), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LiveStatusLane(statuses: List<LiveTaskStatus>) {
    if (statuses.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp)) {
        statuses.takeLast(3).forEach { status ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                if (status.status == "running" || status.status == "background") ThinkingIndicator(dotSize = 4.dp, spacing = 2.dp)
                else Icon(Icons.Default.CheckCircleOutline, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(7.dp))
                Text(status.label, style = MaterialTheme.typography.labelSmall)
                if (status.detail.isNotBlank()) {
                    Text(" · ${status.detail}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun artifactIcon(kind: String): ImageVector = when (kind) {
    "image" -> Icons.Default.Image
    "pdf" -> Icons.Default.PictureAsPdf
    else -> Icons.Default.Description
}

@Composable
private fun ArtifactLane(artifacts: List<ArtifactSuggestion>, onOpen: (String) -> Unit) {
    if (artifacts.isEmpty()) return
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            Text(stringResource(R.string.a22_artifacts), Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(artifacts.takeLast(8), key = { it.path }) { artifact ->
            AssistChip(
                onClick = { onOpen(artifact.path) },
                label = { Text(artifact.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp)) },
                leadingIcon = { Icon(artifactIcon(artifact.kind), null, Modifier.size(16.dp)) }
            )
        }
    }
}

@Composable
private fun QueueLane(
    queuedPrompts: List<QueuedPrompt>,
    onEdit: (QueuedPrompt) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onClear: () -> Unit
) {
    if (queuedPrompts.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, null, Modifier.padding(start = 4.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.a22_queued, queuedPrompts.size), style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f).padding(start = 6.dp))
            TextButton(onClick = onClear) { Text(stringResource(R.string.ui_83b12c2216)) }
        }
        queuedPrompts.take(4).forEachIndexed { index, item ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.hermes.line),
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.text.ifBlank { item.attachments.joinToString { it.name } },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                    IconButton(onClick = { onMove(item.id, -1) }, enabled = index > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.a22_move_up), Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onMove(item.id, 1) }, enabled = index < queuedPrompts.lastIndex) {
                        Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.a22_move_down), Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onEdit(item) }) { Icon(Icons.Default.Edit, stringResource(R.string.ui_464c4ffd01), Modifier.size(16.dp)) }
                    IconButton(onClick = { onRemove(item.id) }) { Icon(Icons.Default.Close, stringResource(R.string.ui_c3812fc4ac), Modifier.size(16.dp)) }
                }
            }
        }
        if (queuedPrompts.size > 4) {
            Text(stringResource(R.string.a22_more_queued, queuedPrompts.size - 4), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** Date separator — centered timestamp between dashed hairlines, like the reference UI. */
@Composable
private fun DaySeparatorRow(label: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        DashedDivider(Modifier.weight(1f))
        Text(label, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        DashedDivider(Modifier.weight(1f))
    }
}

internal data class ChatDisplayRow(val key: String, val dayMillis: Long? = null, val entry: ChatTimelineItem? = null)

private fun chatDisplayRows(timeline: List<ChatTimelineItem>): List<ChatDisplayRow> = buildList {
    var lastDay = Long.MIN_VALUE
    timeline.forEach { entry ->
        val ts = (entry as? ChatTimelineItem.Message)?.message?.timestamp ?: 0L
        if (ts > 0L) {
            val millis = if (ts < 10_000_000_000L) ts * 1000L else ts
            val day = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
            if (day != lastDay) {
                add(ChatDisplayRow(key = "day:$day:$millis", dayMillis = millis))
                lastDay = day
            }
        }
        add(ChatDisplayRow(key = entry.key, entry = entry))
    }
}

@Composable
private fun dayLabel(millis: Long): String {
    val context = LocalContext.current
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val zoned = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
    val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(millis))
    val today = java.time.LocalDate.now()
    return when (zoned.toLocalDate()) {
        today -> stringResource(R.string.a22_today_at, time)
        today.minusDays(1) -> stringResource(R.string.a22_yesterday_at, time)
        else -> {
            val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale,
                if (zoned.year == today.year) "EEEMMMd" else "yMMMd")
            java.text.SimpleDateFormat(pattern, locale).format(java.util.Date(millis)) + " · " + time
        }
    }
}

/** Reference-style "Switch session" sheet: search, recents, current-session check. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwitchSessionSheet(
    chat: ActiveChat,
    sessions: List<HermesSession>,
    onDismiss: () -> Unit,
    onOpen: (HermesSession) -> Unit,
    onNewChat: () -> Unit,
    onAllSessions: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.a21_switch_session), style = MaterialTheme.typography.titleLarge)
            SheetSearchField(query, { query = it }, stringResource(R.string.ui_8abdf3b226))
            val visible = remember(sessions, query) {
                sessions.filter { !it.hidden && !it.archived }
                    .filter { query.isBlank() || it.title.contains(query, true) || it.profile.contains(query, true) }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 380.dp)) {
                items(visible, key = { it.profile + ":" + it.id }) { session ->
                    val current = session.id == chat.storedSessionId && session.profile == chat.profile
                    Surface(color = if (current) MaterialTheme.hermes.accentTint else Color.Transparent, shape = RoundedCornerShape(12.dp)) {
                        FlatRow(
                            onClick = { onOpen(session) },
                            leading = { SessionSourceAvatar(session.source, size = 32) },
                            trailing = {
                                if (current) Icon(Icons.Default.Check, stringResource(R.string.a22_current_session), Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                                else Text(relativeDate(session.lastActive), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        ) {
                            Text(session.title.ifBlank { stringResource(R.string.a21_untitled) }, style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(sessionMeta(session), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onNewChat, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.a14_new_chat))
                }
                OutlinedButton(onClick = onAllSessions, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.a14_all_chats))
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SheetSearchField(value: String, onChange: (String) -> Unit, placeholder: String) =
    HermesSearchField(value, onChange, placeholder, Modifier.padding(vertical = 8.dp))

/** Reference-style Artifacts sheet: kind tabs, search and quick-open rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtifactsSheet(artifacts: List<ArtifactSuggestion>, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    var tab by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.a22_artifacts), style = MaterialTheme.typography.titleLarge)
            SheetSearchField(query, { query = it }, stringResource(R.string.a22_search_artifacts))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val images = artifacts.count { it.kind == "image" }
                FilterChip(tab == "all", { tab = "all" }, label = { Text(stringResource(R.string.a22_all_count, artifacts.size)) })
                FilterChip(tab == "image", { tab = "image" }, label = { Text(stringResource(R.string.a22_images_count, images)) })
                FilterChip(tab == "file", { tab = "file" }, label = { Text(stringResource(R.string.a22_files_count, artifacts.size - images)) })
            }
            val filtered = artifacts
                .filter { tab == "all" || (tab == "image") == (it.kind == "image") }
                .filter { query.isBlank() || it.name.contains(query, true) || it.path.contains(query, true) }
            if (filtered.isEmpty()) {
                Text(
                    stringResource(R.string.a22_no_artifacts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 400.dp)) {
                    items(filtered, key = { it.path }) { artifact ->
                        FlatRow(
                            onClick = { onOpen(artifact.path) },
                            leading = {
                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.hermes.accentTint),
                                    contentAlignment = Alignment.Center) {
                                    Icon(artifactIcon(artifact.kind), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            trailing = { Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        ) {
                            Text(artifact.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(artifact.path.substringBeforeLast('/').substringAfterLast('/'), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Text(
                pluralStringResource(R.plurals.a22_items, filtered.size, filtered.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Output of a display-only slash command (`/status`, `/usage`, `/help`…). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommandOutputSheet(output: CommandOutput, onCopy: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(output.command, style = MaterialTheme.typography.titleMedium.copy(fontFamily = HermesMono),
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { onCopy(output.output) }) { Icon(Icons.Default.ContentCopy, stringResource(R.string.a22_copy)) }
            }
            Surface(
                color = MaterialTheme.hermes.codeBlock,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.hermes.codeBlockBorder),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                SelectionContainer {
                    Text(
                        output.output.take(48_000),
                        Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(14.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = HermesMono)
                    )
                }
            }
        }
    }
}
