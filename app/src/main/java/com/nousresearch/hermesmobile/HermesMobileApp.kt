package com.nousresearch.hermesmobile

import com.nousresearch.hermesmobile.HermesViewModel
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.ui.components.HermesDrawer
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.nousresearch.hermesmobile.features.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nousresearch.hermesmobile.data.GatewayConnectionState
import com.nousresearch.hermesmobile.data.SlashPalette
import com.nousresearch.hermesmobile.ui.components.InteractionDialog
import com.nousresearch.hermesmobile.ui.screens.*
import com.nousresearch.hermesmobile.ui.theme.HermesTheme
import com.nousresearch.hermesmobile.ui.theme.hermes

@Composable
fun HermesMobileApp(vm: HermesViewModel = viewModel()) {
    HermesTheme {
        val s = vm.uiState
        val drawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
        LaunchedEffect(vm.notificationInboxRequested) { if (vm.notificationInboxRequested) { vm.switchSection(MainSection.NOTIFICATIONS); vm.notificationInboxRequested = false } }
        // Lets the push service skip a turn notice for the chat the user is reading right now.
        val openChat = if (s.section == MainSection.CHAT) s.activeChat?.storedSessionId else null
        DisposableEffect(openChat) {
            com.nousresearch.hermesmobile.push.AppPresence.openSessionId = openChat
            onDispose { com.nousresearch.hermesmobile.push.AppPresence.openSessionId = null }
        }
        val uriHandler = LocalUriHandler.current
        androidx.compose.runtime.LaunchedEffect(s.authLaunchUrl) {
            val url = s.authLaunchUrl ?: return@LaunchedEffect
            val opened = runCatching { uriHandler.openUri(url) }
            if (opened.isFailure) {
                vm.cancelNativeSignIn("No se pudo abrir el navegador del sistema para iniciar sesión en Hermes")
            } else {
                vm.consumeAuthLaunchUrl()
            }
        }
        if (s.connectionState != GatewayConnectionState.CONNECTED && s.profiles.isEmpty()) {
            ConnectionScreen(
                connections = s.connections,
                activeId = s.activeConnectionId,
                discovery = s.connectionDiscovery,
                busy = s.loading,
                error = s.error,
                onDiscover = vm::discoverConnection,
                onConnectSaved = vm::switchConnection,
                onForget = vm::forgetConnection,
                onPassword = vm::connectWithPassword,
                onNative = vm::startNativeSignIn,
                onSessionToken = vm::connectWithSessionToken,
                onNoAuth = vm::connectDiscoveredWithoutAuth,
                onCancelSetup = vm::cancelConnectionSetup
            )
            return@HermesTheme
        }

        // System back returns to the page visited before, then to Home, the start destination (screens with
        // their own back stack — an open chat, sub-pages — register deeper handlers that win).
        androidx.activity.compose.BackHandler(enabled = s.section != MainSection.HOME || vm.canNavigateBack) { vm.navigateBack() }
        val wide = LocalWindowInfo.current.containerSize.width / LocalDensity.current.density >= 720f
        androidx.compose.material3.ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                HermesDrawer(
                    current = s.section,
                    connected = s.connectionState == GatewayConnectionState.CONNECTED,
                    gatewayLabel = s.savedConfig?.displayLabel.orEmpty(),
                    onSelect = vm::switchSection,
                    onNewChat = { vm.newChat() },
                    onNotifications = { vm.switchSection(MainSection.NOTIFICATIONS) },
                    onClose = { scope.launch { drawerState.close() } },
                    recentSessions = remember(s.sessions) {
                        s.sessions.filter { !it.hidden && !it.archived && !it.isRoutineRun }
                            .sortedWith(compareByDescending<com.nousresearch.hermesmobile.data.HermesSession> { it.pinned }.thenByDescending { it.lastActive })
                    },
                    activeSessionId = s.activeChat?.storedSessionId,
                    sessionDots = s.sessionDots,
                    onOpenSession = vm::openSession,
                    profile = s.selectedProfile,
                    profiles = s.profiles.map { it.name },
                    onProfile = vm::selectProfile,
                    drawerState = drawerState
                )
            }
        ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                val pending = vm.incomingShare
                if (vm.importingShare) LinearProgressIndicator(Modifier.fillMaxWidth())
                else if (pending != null) Surface(color = MaterialTheme.hermes.accentTint) {
                    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)) {
                        Text(stringResource(R.string.share_count, pending.files.size), style = MaterialTheme.typography.titleSmall)
                        Text(pending.text.take(160), maxLines = 2)
                        Text(s.activeChat?.let { stringResource(R.string.share_destination, it.profile, it.title) }
                            ?: stringResource(R.string.share_choose), style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(enabled = s.activeChat != null, onClick = vm::acceptIncomingShare) { Text(stringResource(R.string.ui_9901ff83c4)) }
                            TextButton(onClick = vm::discardIncomingShare) { Text(stringResource(R.string.ui_1a878a55c5)) }
                        }
                    }
                } else if (s.connectionState != GatewayConnectionState.CONNECTED) Surface(color = MaterialTheme.hermes.accentTint) {
                    Text(if (s.connectionState == GatewayConnectionState.FAILED) stringResource(R.string.connection_pending)
                        else stringResource(R.string.connection_recovering),
                        Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            },
            snackbarHost = {
                SnackbarHost(rememberHermesSnackbarHostState(s.snackbar ?: s.error, vm::clearMessage)) { data ->
                    // Hairline Hermes surface instead of the inverse Material slab; long
                    // gateway errors stay readable without covering the composer.
                    Surface(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.hermes.lineStrong),
                        shadowElevation = 4.dp
                    ) {
                        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                data.visuals.message,
                                Modifier.weight(1f).padding(vertical = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 4,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            IconButton(onClick = { data.dismiss() }) {
                                Icon(Icons.Default.Close, stringResource(R.string.chat_dismiss_voice), Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        ) { inset ->
            Row(Modifier.fillMaxSize().padding(inset).consumeWindowInsets(inset)) {
                if (wide) NavigationRail(containerColor = MaterialTheme.colorScheme.background) {
                    // Primary destinations with the drawer's icons, in the drawer's order (Home first);
                    // the full list (workspace, profiles, notifications) stays in the drawer.
                    FloatingActionButton(onClick = { vm.newChat() }, containerColor = MaterialTheme.hermes.accentTint,
                        contentColor = MaterialTheme.colorScheme.primary, elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) { Icon(Icons.Outlined.AddComment, stringResource(R.string.ui_db18382a24)) }
                    listOf(MainSection.HOME to Icons.Default.SpaceDashboard, MainSection.CHAT to Icons.AutoMirrored.Filled.Chat, MainSection.BOTS to Icons.Default.SmartToy,
                        MainSection.CAPABILITIES to Icons.Default.Psychology, MainSection.FILES to Icons.Default.FolderOpen,
                        MainSection.CRON to Icons.Default.Schedule, MainSection.MORE to Icons.Default.Settings).forEach { (section, icon) ->
                        NavigationRailItem(selected = s.section == section, onClick = { if(section == MainSection.CHAT) vm.closeActiveChat() else vm.switchSection(section) },
                            icon = { Icon(icon, null) }, label = { Text(stringResource(sectionTitle(section))) })
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {

                when (s.section) {
                    MainSection.HOME -> ExperienceHome(s, openDrawer, vm::switchSection, { vm.newChat() }, vm::openSession)
                    MainSection.SYSTEM -> vm.featureRepository()?.let { repo -> key(repo, s.selectedProfile) {
                        SystemPage(repo, s.selectedProfile, s.profiles.map { it.name }, vm::selectProfile, onMenu = openDrawer) { vm.switchSection(MainSection.SERVER_SETTINGS) }
                    } }
                    MainSection.CHAT -> ChatRoute(vm, s, openDrawer)
                    MainSection.BOTS -> BotsScreen(
                        onMenu = openDrawer,
                        profiles = s.profiles,
                        routedBots = s.botRoster,
                        botRosterLoading = s.botRosterLoading,
                        editor = s.botEditor,
                        editorLoadingProfile = s.botEditorLoadingProfile,
                        editorSaving = s.botEditorSaving,
                        groupCapabilities = s.groupCapabilities,
                        groupRooms = s.groupRooms,
                        activeGroup = s.activeGroup,
                        groupsLoading = s.groupsLoading,
                        groupActionBusy = s.groupActionBusy,
                        groupError = s.groupError,
                        avatarAssets = vm.avatarAssets,
                        sessionDots = s.sessionDots,
                        onEnsureAvatar = vm::ensureAvatar,
                        onSwitchConnection = vm::switchConnection,
                        onOpen = vm::openBot,
                        onCreate = vm::createBot,
                        onNewChat = vm::newBotSideChat,
                        onEdit = vm::editBot,
                        onSaveEditor = vm::saveBotEditor,
                        onCloseEditor = vm::closeBotEditor,
                        onDelete = vm::deleteBot,
                        onRefreshGroups = vm::refreshGroups,
                        onCreateGroup = vm::createGroup,
                        onOpenGroup = vm::openGroup,
                        onCloseGroup = vm::closeGroup,
                        onSendGroup = vm::sendGroupMessage,
                        onReplyGroupThread = vm::setGroupReplyThread,
                        onRenameGroup = vm::renameGroup,
                        onStopGroup = vm::stopGroup,
                        onRetryGroup = vm::retryGroupAction,
                        onApproveGroup = vm::approveGroupAction,
                        onDisbandGroup = vm::disbandGroup
                    )
                    MainSection.CRON -> CronScreen(
                        onMenu = openDrawer,
                        profiles = s.profiles,
                        selectedProfile = s.selectedProfile,
                        jobs = s.cronJobs,
                        deliveryTargets = s.cronDeliveryTargets,
                        blueprints = s.cronBlueprints,
                        runs = s.cronRuns,
                        onProfile = vm::selectProfile,
                        onCreate = vm::createCron,
                        onUpdate = vm::updateCron,
                        onPause = vm::pauseCron,
                        onResume = vm::resumeCron,
                        onTrigger = vm::triggerCron,
                        onDelete = vm::deleteCron,
                        onLoadRuns = vm::loadCronRuns,
                        onInstantiateBlueprint = vm::instantiateCronBlueprint
                    )
                    MainSection.MESSAGING -> MessagingScreen(
                        onMenu = openDrawer,
                        profiles = s.profiles,
                        selected = s.selectedProfile,
                        messaging = s.messaging,
                        pending = s.pendingPairing,
                        approved = s.approvedPairing,
                        onboarding = s.telegramOnboarding,
                        webhooks = s.webhooks,
                        webhookReceipt = s.webhookReceipt,
                        onProfile = vm::selectProfile,
                        onEnable = vm::setMessagingEnabled,
                        onConfigure = vm::saveMessagingPlatform,
                        onTest = vm::testMessagingPlatform,
                        onStartTelegram = vm::startTelegramOnboarding,
                        onApplyTelegram = vm::applyTelegramOnboarding,
                        onCancelTelegram = vm::cancelTelegramOnboarding,
                        onApprove = vm::approvePairing,
                        onRevoke = vm::revokePairing,
                        onClearPending = vm::clearPendingPairing,
                        onCreateWebhook = vm::createWebhook,
                        onWebhookEnabled = vm::setWebhookEnabled,
                        onDeleteWebhook = vm::deleteWebhook,
                        onDismissWebhookReceipt = vm::dismissWebhookReceipt,
                        onRestartGateway = vm::restartMessagingGateway
                    )
                    MainSection.CAPABILITIES, MainSection.PROVIDERS -> CapabilitiesScreen(
                        providers = s.section == MainSection.PROVIDERS,
                        pluginsContent = {
                            vm.featureRepository()?.let { repo -> key(repo, s.capabilityProfile) {
                                com.nousresearch.hermesmobile.features.WorkspaceContent(repo, s.capabilityProfile, "Plugins")
                            } }
                        },
                        profiles = s.profiles,
                        profile = s.capabilityProfile,
                        snapshot = s.capabilities,
                        loading = s.capabilitiesLoading,
                        actionBusy = s.capabilityActionBusy,
                        skillSearchResults = s.capabilitySkillSearchResults,
                        skillDetail = s.capabilitySkillDetail,
                        toolsetConfig = s.capabilityToolsetConfig,
                        oauthFlow = s.capabilityOAuthFlow,
                        onMenu = openDrawer,
                        onProfile = vm::selectCapabilityProfile,
                        onRefresh = { vm.refreshCapabilities() },
                        onSetModel = vm::setCapabilityModel,
                        onSkillEnabled = vm::setCapabilitySkillEnabled,
                        onSkillOpen = vm::openCapabilitySkill,
                        onSkillClose = vm::closeCapabilitySkill,
                        onSkillSearch = vm::searchCapabilitySkills,
                        onInstallSkill = vm::installCapabilitySkill,
                        onUninstallSkill = vm::uninstallCapabilitySkill,
                        onUpdateSkills = vm::updateCapabilitySkills,
                        onToolsetEnabled = vm::setCapabilityToolsetEnabled,
                        onToolsetConfig = vm::loadCapabilityToolsetConfig,
                        onCloseToolsetConfig = vm::closeCapabilityToolsetConfig,
                        onSelectToolProvider = vm::selectCapabilityToolsetProvider,
                        onSaveToolEnv = vm::saveCapabilityToolsetEnv,
                        onAddMcp = vm::addCapabilityMcpServer,
                        onRemoveMcp = vm::removeCapabilityMcpServer,
                        onMcpEnabled = vm::setCapabilityMcpEnabled,
                        onTestMcp = vm::testCapabilityMcp,
                        onInstallMcpCatalog = vm::installCapabilityMcpCatalog,
                        onSetEnv = vm::setCapabilityEnvVar,
                        onDeleteEnv = vm::deleteCapabilityEnvVar,
                        onSaveCustomEndpoint = vm::saveCapabilityCustomEndpoint,
                        onActivateCustomEndpoint = vm::activateCapabilityCustomEndpoint,
                        onDeleteCustomEndpoint = vm::deleteCapabilityCustomEndpoint,
                        onStartOAuth = vm::startCapabilityOAuth,
                        onDisconnectOAuth = vm::disconnectCapabilityOAuth,
                        onSubmitOAuthCode = vm::submitCapabilityOAuthCode,
                        onDismissOAuth = vm::dismissCapabilityOAuth
                    )
                    MainSection.PROFILES -> ProfilesScreen(
                        profiles = s.profiles,
                        busy = s.profileActionBusy,
                        lastExport = s.lastProfileExport,
                        onMenu = openDrawer,
                        onCreate = vm::createManagedProfile,
                        onRename = vm::renameManagedProfile,
                        onDelete = vm::deleteManagedProfile,
                        onExport = vm::exportManagedProfile,
                        onImport = vm::importManagedProfile
                    )
                    MainSection.FILES, MainSection.MEMORY, MainSection.MODELS, MainSection.VOICE, MainSection.SERVER_SETTINGS -> {
                        val page = when (s.section) {
                            MainSection.FILES -> "Files"
                            MainSection.MEMORY -> "Memory"
                            MainSection.MODELS -> "Models"
                            MainSection.VOICE -> "Voice"
                            else -> "Settings"
                        }
                        vm.featureRepository()?.let { repo -> key(repo, s.selectedProfile, page) {
                            ParityScreen(
                                repo, s.selectedProfile, page,
                                profiles = s.profiles.map { it.name }, onProfile = vm::selectProfile,
                                onMenu = openDrawer
                            )
                        } }
                    }
                    MainSection.GATEWAYS -> GatewaysScreen(
                        onMenu = openDrawer,
                        config = s.savedConfig,
                        connections = s.connections,
                        status = s.status,
                        profileCount = s.profiles.size,
                        sessionCount = s.sessions.size,
                        onRefresh = { vm.refreshAll() },
                        onDisconnect = { vm.disconnect(clearSaved = false) },
                        onSignOut = vm::signOutActive,
                        onSwitchConnection = vm::switchConnection,
                        onForgetConnection = vm::forgetConnection,
                        onAddConnection = vm::addAnotherConnection
                    )
                    MainSection.NOTIFICATIONS -> com.nousresearch.hermesmobile.push.PushSettings(
                        s.savedConfig, s.selectedProfile, s.profiles.map { it.name },
                        onMenu = openDrawer,
                        onOpen = vm::openNotification,
                        onAskHermes = { profile, prompt -> vm.askHermes(prompt, profile) },
                        runCommand = { profile, name, arg -> vm.pluginCommand(profile, name, arg) }
                    )
                    MainSection.APPEARANCE -> AppearanceScreen(openDrawer)
                    MainSection.ASSISTANT -> AssistantScreen(openDrawer, s.profiles.map { it.name })
                    MainSection.ABOUT -> AboutScreen(openDrawer)
                    MainSection.MORE -> MoreScreen(
                        onMenu = openDrawer,
                        config = s.savedConfig,
                        status = s.status,
                        profileNames = s.profiles.map { it.name },
                        onOpen = vm::switchSection
                    )
                }
                if (s.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        }

        com.nousresearch.hermesmobile.update.UpdateDialog()

        s.pendingCapabilityModelChange?.let { pending ->
            AlertDialog(
                onDismissRequest = vm::cancelCapabilityModelChange,
                title = { Text(stringResource(R.string.ui_0aad21ec3d)) },
                text = { Text(pending.message) },
                confirmButton = { TextButton(onClick = vm::confirmCapabilityModelChange) { Text(stringResource(R.string.ui_eebdd24a77)) } },
                dismissButton = { TextButton(onClick = vm::cancelCapabilityModelChange) { Text(stringResource(R.string.ui_19766ed6cc)) } }
            )
        }

        s.pendingSessionModelChange?.let { pending ->
            AlertDialog(
                onDismissRequest = vm::cancelSessionModelChange,
                title = { Text(stringResource(R.string.ui_0aad21ec3d)) },
                text = { Text(pending.message) },
                confirmButton = { TextButton(onClick = vm::confirmSessionModelChange) { Text(stringResource(R.string.ui_eebdd24a77)) } },
                dismissButton = { TextButton(onClick = vm::cancelSessionModelChange) { Text(stringResource(R.string.ui_19766ed6cc)) } }
            )
        }

        s.pendingBotModelSave?.let { pending ->
            AlertDialog(
                onDismissRequest = vm::cancelBotModelSwitch,
                title = { Text(stringResource(R.string.ui_0aad21ec3d)) },
                text = { Text(pending.message) },
                confirmButton = { TextButton(onClick = vm::confirmBotModelSwitch) { Text(stringResource(R.string.ui_eebdd24a77)) } },
                dismissButton = { TextButton(onClick = vm::cancelBotModelSwitch) { Text(stringResource(R.string.ui_19766ed6cc)) } }
            )
        }

        s.interaction?.let { interaction ->
            InteractionDialog(
                interaction,
                vm::answerApproval,
                vm::answerInteraction,
                vm::answerClarifyBatch,
                vm::cancelInteraction
            )
        }
    }
}


@Composable
private fun ChatRoute(vm: HermesViewModel, s: HermesUiState, onMenu: () -> Unit) {
    val repo = vm.featureRepository()
    val chat = s.activeChat
    var chatView by remember(repo) { mutableIntStateOf(0) }
    // View switcher sits under the title (not above it) so the screen keeps one header.
    val viewTabs: @Composable () -> Unit = {
        if (repo != null) Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(chatView == 0, { chatView = 0 }, label = { Text(stringResource(R.string.a14_all_chats)) })
            FilterChip(chatView == 1, { chatView = 1 }, label = { Text(stringResource(R.string.a14_folders)) })
            FilterChip(chatView == 2, { chatView = 2 }, label = { Text(stringResource(R.string.a14_projects)) })
        }
    }
    if (chat == null && repo != null && chatView != 0) {
        Column {
            com.nousresearch.hermesmobile.ui.components.HermesTopBar(
                stringResource(R.string.ui_ef5b4049bb),
                leading = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, stringResource(R.string.a21_menu)) } },
                subtitleContent = {
                    com.nousresearch.hermesmobile.ui.components.ProfileSwitcher(s.selectedProfile, s.profiles.map { it.name }, vm::selectProfile)
                }
            )
            viewTabs()
            key(repo, s.selectedProfile) {
                if (chatView == 2) ProjectsPage(repo, s.selectedProfile, vm::openSession, vm::newWorkspaceChat)
                else LocalFolders(repo.config.id, s.selectedProfile, s.sessions, vm::openSession)
            }
        }
        return
    }
    ChatContent(vm, s, onMenu, viewTabs)
}

@Composable
private fun ChatContent(vm: HermesViewModel, s: HermesUiState, onMenu: () -> Unit = {}, listHeader: @Composable () -> Unit = {}) {
    val repo = vm.featureRepository()
    val chat = s.activeChat
    var contextOpen by remember(repo, chat?.runtimeSessionId) { mutableStateOf(false) }
    if (contextOpen && repo != null && chat != null) {
        com.nousresearch.hermesmobile.ui.screens.ContextWindowSheet(repo, chat, s.sessionUsage, { contextOpen = false }, vm::compressActiveChat)
    }
    val scope = rememberCoroutineScope()
    val features = remember(repo, chat?.profile, chat?.storedSessionId) {
        if (repo != null && chat != null) ParityController(repo, chat.profile, scope) else null
    }
    var artifact by remember(repo, chat?.storedSessionId) { mutableStateOf<String?>(null) }
    DisposableEffect(features) { onDispose { features?.dispose() } }
    if (artifact != null && features != null && chat != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { artifact = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                Column {
                    TextButton(onClick = { artifact = null }) { Text(stringResource(R.string.ui_5978844cb6)) }
                    if (features.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    features.message?.let { message -> Text(com.nousresearch.hermesmobile.features.shown(message), Modifier.padding(16.dp)) }
                    FilesPage(features, artifact, chat.storedSessionId)
                    FeatureConsent(features)
                }
            }
        }
    }

    // Keep the high-frequency streaming state read inside the chat subtree so
    // token batches do not invalidate the whole app shell / other tabs.
    val artifactOwner = remember(repo, chat?.profile, chat?.storedSessionId) {
        if (repo != null && chat != null) com.nousresearch.hermesmobile.ui.components.ArtifactOwner(repo, chat.profile, chat.storedSessionId) { artifact = it } else null
    }
    androidx.compose.runtime.CompositionLocalProvider(com.nousresearch.hermesmobile.ui.components.LocalArtifactOwner provides artifactOwner) {
    ChatScreen(
        profiles = s.profiles,
        sessions = s.sessions,
        sessionDots = s.sessionDots,
        searchResults = s.sessionSearchResults,
        searchQuery = s.sessionSearchQuery,
        searchLoading = s.sessionSearchLoading,
        sessionsHasMore = s.sessionsHasMore,
        sessionsLoadingMore = s.sessionsLoadingMore,
        includeArchived = s.includeArchivedSessions,
        active = s.activeChat,
        streamingText = vm.streamingText,
        loadingOlderMessages = s.loadingOlderMessages,
        tools = s.tools,
        draft = s.chatDraft,
        attachments = s.composerAttachments,
        queuedPrompts = s.queuedPrompts,
        reasoningText = s.reasoningText,
        liveStatuses = s.liveStatuses,
        artifactSuggestions = s.artifactSuggestions,
        onOpenArtifact = { artifact = it },
        onContext = { contextOpen = true },
        voiceControls = { content ->
            if (features != null && chat != null) key(chat.storedSessionId, chat.profile) { VoiceControls(features, { transcript ->
                if (vm.uiState.activeChat?.storedSessionId == chat.storedSessionId && vm.featureRepository() === repo)
                    vm.setChatDraft((vm.uiState.chatDraft + " " + transcript).trim())
            }, chat.messages.lastOrNull { it.role == "assistant" }?.content.orEmpty(), content = content) }
            else content(null)
        },
        sessionModelChoices = s.sessionModelChoices,
        sessionUsage = s.sessionUsage,
        modelOptionsLoading = s.modelOptionsLoading,
        slashPalette = SlashPalette(s.slashQuery, s.slashCompletions, s.slashLoading),
        commandOutput = s.commandOutput,
        selectedProfile = s.sessionProfileFilter,
        onSelectProfile = vm::selectSessionFilter,
        onIncludeArchived = vm::setIncludeArchivedSessions,
        onSearch = vm::searchSessions,
        onLoadMoreSessions = vm::loadMoreSessions,
        onNewChat = vm::newChat,
        onOpen = vm::openSession,
        onRename = vm::renameSession,
        onTogglePin = vm::togglePin,
        onToggleArchive = vm::toggleArchive,
        onToggleUnread = vm::toggleUnread,
        onDelete = vm::deleteSession,
        onBranch = vm::branchSession,
        onBranchActive = vm::branchActiveChat,
        onRetry = vm::retryLastTurn,
        onApplySlash = vm::applySlashCompletion,
        onDismissCommandOutput = vm::dismissCommandOutput,
        onLoadOlderMessages = vm::loadOlderMessages,
        onDraftChange = vm::setChatDraft,
        onAttach = vm::addComposerAttachments,
        onRemoveAttachment = vm::removeComposerAttachment,
        onRemoveQueued = vm::removeQueuedPrompt,
        onUpdateQueued = vm::updateQueuedPrompt,
        onMoveQueued = vm::moveQueuedPrompt,
        onClearQueue = vm::clearQueuedPrompts,
        onSwitchModel = vm::switchSessionModel,
        onSetReasoning = vm::setSessionReasoning,
        onRefreshSessionMeta = vm::refreshSessionMeta,
        onSend = vm::sendMessage,
        onStop = vm::stopTurn,
        onSteer = vm::steer,
        onBack = vm::closeActiveChat,
        onMenu = onMenu,
        listHeader = listHeader,
        routines = com.nousresearch.hermesmobile.ui.screens.RoutineActions(
            jobs = s.cronJobs,
            runs = s.cronRuns,
            onLoadRuns = vm::loadCronRuns,
            onTrigger = vm::triggerCron,
            onPause = vm::pauseCron,
            onResume = vm::resumeCron,
            onManage = { vm.switchSection(MainSection.CRON) },
            onDelete = vm::deleteCron,
            // The run lives in the profile that owns the job, which the server names on each run.
            onOpenRun = { job, run ->
                vm.openSession(com.nousresearch.hermesmobile.data.HermesSession(
                    id = run.id, title = run.title, profile = run.profile.ifBlank { job.profile }, source = "cron"
                ))
            }
        )
    )
    }
}

@Composable
private fun rememberHermesSnackbarHostState(message: String?, clear: () -> Unit): SnackbarHostState {
    val host = androidx.compose.runtime.remember { SnackbarHostState() }
    androidx.compose.runtime.LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            host.showSnackbar(message)
            clear()
        }
    }
    return host
}

/** A section's name, as the side menu and the navigation rail show it. */
internal fun sectionTitle(section: MainSection): Int = when (section) {
    MainSection.HOME -> R.string.a14_home
    MainSection.CHAT -> R.string.ui_ef5b4049bb
    MainSection.BOTS -> R.string.ui_f17c0acbd8
    MainSection.CAPABILITIES -> R.string.i18n_capabilities
    MainSection.MESSAGING -> R.string.i18n_messaging
    MainSection.FILES -> R.string.a14_files
    MainSection.CRON -> R.string.ui_61b7bb44e2
    MainSection.MEMORY -> R.string.a14_memory
    MainSection.MODELS -> R.string.i18n_set_models
    MainSection.VOICE -> R.string.a14_voice
    MainSection.SERVER_SETTINGS -> R.string.i18n_server_settings
    MainSection.PROVIDERS -> R.string.i18n_providers
    MainSection.GATEWAYS -> R.string.ui_dc27311748
    MainSection.SYSTEM -> R.string.a14_system
    MainSection.PROFILES -> R.string.ui_535e52e4a2
    MainSection.NOTIFICATIONS -> R.string.push_title
    MainSection.MORE -> R.string.a21_settings
    MainSection.APPEARANCE -> R.string.appearance
    MainSection.ASSISTANT -> R.string.assist_settings_title
    MainSection.ABOUT -> R.string.about_title
}
