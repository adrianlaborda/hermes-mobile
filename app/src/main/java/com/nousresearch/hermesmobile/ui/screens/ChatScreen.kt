package com.nousresearch.hermesmobile.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nousresearch.hermesmobile.CommandOutput
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.data.*
import com.nousresearch.hermesmobile.features.VoiceActions
import com.nousresearch.hermesmobile.ui.components.EmptyState
import com.nousresearch.hermesmobile.ui.theme.hermes

/** Width from which the list and the open conversation sit side by side, like Desktop. */
private val TwoPaneMinWidth = 760.dp

@Composable
fun ChatScreen(
    profiles: List<HermesProfile>,
    sessions: List<HermesSession>,
    sessionDots: Map<String, com.nousresearch.hermesmobile.data.SessionDot>,
    searchResults: List<HermesSession>,
    searchQuery: String,
    searchLoading: Boolean,
    sessionsHasMore: Boolean,
    sessionsLoadingMore: Boolean,
    includeArchived: Boolean,
    active: ActiveChat?,
    streamingText: String,
    loadingOlderMessages: Boolean,
    tools: List<ToolActivity>,
    draft: String,
    attachments: List<ComposerAttachment>,
    queuedPrompts: List<QueuedPrompt>,
    reasoningText: String,
    liveStatuses: List<LiveTaskStatus>,
    artifactSuggestions: List<ArtifactSuggestion>,
    onOpenArtifact: (String) -> Unit,
    onContext: () -> Unit,
    voiceControls: @Composable (@Composable (VoiceActions?) -> Unit) -> Unit,
    sessionModelChoices: List<SessionModelChoice>,
    sessionUsage: SessionUsage?,
    modelOptionsLoading: Boolean,
    slashPalette: SlashPalette,
    commandOutput: CommandOutput?,
    selectedProfile: String,
    onSelectProfile: (String) -> Unit,
    onIncludeArchived: (Boolean) -> Unit,
    onSearch: (String) -> Unit,
    onLoadMoreSessions: () -> Unit,
    onNewChat: (String, String) -> Unit,
    onOpen: (HermesSession) -> Unit,
    onRename: (HermesSession, String) -> Unit,
    onTogglePin: (HermesSession) -> Unit,
    onToggleArchive: (HermesSession) -> Unit,
    onToggleUnread: (HermesSession) -> Unit,
    onDelete: (HermesSession) -> Unit,
    onBranch: (HermesSession) -> Unit,
    onBranchActive: () -> Unit,
    onRetry: () -> Unit,
    onApplySlash: (SlashCompletion) -> Unit,
    onDismissCommandOutput: () -> Unit,
    onLoadOlderMessages: () -> Unit,
    onDraftChange: (String) -> Unit,
    onEditMessage: (ChatMessage) -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onConfirmEdit: (Boolean) -> Unit = {},
    onOpenKanban: (() -> Unit)? = null,
    onAttach: (List<Uri>) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onRemoveQueued: (String) -> Unit,
    onUpdateQueued: (String, String) -> Unit,
    onMoveQueued: (String, Int) -> Unit,
    onClearQueue: () -> Unit,
    onSwitchModel: (SessionModelChoice, String) -> Unit,
    onSetReasoning: (String) -> Unit,
    onRefreshSessionMeta: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onSteer: (String) -> Unit,
    onBack: () -> Unit,
    onMenu: () -> Unit = {},
    routines: RoutineActions? = null,
    listHeader: @Composable () -> Unit = {}
) {
    var showNew by remember { mutableStateOf(false) }

    BackHandler(enabled = active != null, onBack = onBack)
    // Like Desktop, a new chat starts immediately; only ask when the profile is ambiguous.
    val newChat: () -> Unit = {
        val profile = selectedProfile.takeIf { it != "all" } ?: profiles.singleOrNull()?.name
        if (profile != null || profiles.isEmpty()) onNewChat(profile ?: "default", "") else showNew = true
    }
    val listActions = SessionListActions(onSelectProfile, onIncludeArchived, onSearch, onLoadMoreSessions, newChat, onOpen,
        onRename, onTogglePin, onToggleArchive, onToggleUnread, onDelete, onBranch, onMenu)
    val threadActions = ChatThreadActions(
        onLoadOlderMessages, onDraftChange, onAttach, onRemoveAttachment, onRemoveQueued, onUpdateQueued, onMoveQueued,
        onClearQueue, onSwitchModel, onSetReasoning, onRefreshSessionMeta, onSend, onStop, onSteer, onRetry, onBranchActive,
        onApplySlash, onDismissCommandOutput, onBack, onOpen, onNewChat, onOpenArtifact, onContext, onEditMessage, onCancelEdit, onConfirmEdit,
        onOpenKanban
    )

    @Composable
    fun list(modifier: Modifier) = SessionListPane(
        profiles = profiles,
        sessions = sessions,
        sessionDots = sessionDots,
        searchResults = searchResults,
        searchQuery = searchQuery,
        searchLoading = searchLoading,
        hasMore = sessionsHasMore,
        loadingMore = sessionsLoadingMore,
        includeArchived = includeArchived,
        selectedProfile = selectedProfile,
        activeKey = active?.let { "${it.profile}:${it.storedSessionId}" },
        actions = listActions,
        listHeader = listHeader,
        modifier = modifier,
        routines = routines
    )

    @Composable
    fun thread(chat: ActiveChat, twoPane: Boolean) = ChatThread(
        chat = chat,
        sessions = sessions,
        profiles = profiles,
        streamingText = streamingText,
        loadingOlderMessages = loadingOlderMessages,
        tools = tools,
        draft = draft,
        attachments = attachments,
        queuedPrompts = queuedPrompts,
        reasoningText = reasoningText,
        liveStatuses = liveStatuses,
        artifactSuggestions = artifactSuggestions,
        voiceControls = voiceControls,
        sessionModelChoices = sessionModelChoices,
        sessionUsage = sessionUsage,
        modelOptionsLoading = modelOptionsLoading,
        slashPalette = slashPalette,
        commandOutput = commandOutput,
        actions = threadActions,
        twoPane = twoPane
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = this.maxWidth
        if (width >= TwoPaneMinWidth) {
            Row(Modifier.fillMaxSize()) {
                list(Modifier.width((width * .36f).coerceIn(300.dp, 400.dp)))
                VerticalDivider(color = MaterialTheme.hermes.line)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (active != null) thread(active, twoPane = true)
                    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            Icons.AutoMirrored.Filled.Chat,
                            stringResource(R.string.a22_select_conversation),
                            stringResource(R.string.a22_select_conversation_hint)
                        ) { Button(onClick = newChat) { Text(stringResource(R.string.ui_db18382a24)) } }
                    }
                }
            }
        } else if (active == null) list(Modifier)
        else thread(active, twoPane = false)
    }

    if (showNew) {
        NewChatDialog(profiles, selectedProfile, { showNew = false }) { profile, title ->
            showNew = false
            onNewChat(profile, title)
        }
    }
}
