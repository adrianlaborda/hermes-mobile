package com.nousresearch.hermesmobile

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nousresearch.hermesmobile.data.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.URI
import java.net.ConnectException
import java.util.UUID

/**
 * The app's pages, laid out as Hermes Desktop does: the side menu holds the places you work in (Chats, Bots,
 * Capabilities, Messaging, Files, Cron, Memory); Settings ([MORE]) holds the rest, one page each.
 */
enum class MainSection {
    HOME, CHAT, BOTS, CAPABILITIES, MESSAGING, FILES, CRON, MEMORY,
    MORE, MODELS, VOICE, SERVER_SETTINGS, PROVIDERS, GATEWAYS, SYSTEM, PROFILES, NOTIFICATIONS, APPEARANCE, ASSISTANT, ABOUT
}

data class PendingBotModelSave(
    val original: BotProfileConfig,
    val updated: BotProfileConfig,
    val message: String
)

data class PendingCapabilityModelChange(
    val profile: String,
    val provider: String,
    val model: String,
    val message: String
)

data class PendingSessionModelChange(
    val runtimeSessionId: String,
    val profile: String,
    val provider: String,
    val model: String,
    val reasoning: String,
    val message: String
)

private data class SessionModelPin(
    val profile: String,
    val provider: String,
    val model: String,
    val reasoning: String,
    val ensureOnNextPrompt: Boolean = true
)

data class HermesUiState(
    val savedConfig: GatewayConfig? = null,
    val connections: List<GatewayConfig> = emptyList(),
    val activeConnectionId: String? = null,
    val connectionDiscovery: GatewayDiscovery? = null,
    val authLaunchUrl: String? = null,
    val status: GatewayStatus? = null,
    val connectionState: GatewayConnectionState = GatewayConnectionState.DISCONNECTED,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    // Like Desktop, the app opens on the conversation list; Home stays reachable from the drawer.
    val section: MainSection = MainSection.HOME,
    val profiles: List<HermesProfile> = emptyList(),
    val botRoster: List<RoutedBot> = emptyList(),
    val botRosterLoading: Boolean = false,
    val profileActionBusy: Boolean = false,
    val lastProfileExport: String? = null,
    val groupCapabilities: GroupCapabilities? = null,
    val groupRooms: List<GroupRoom> = emptyList(),
    val groupsLoading: Boolean = false,
    val groupError: String? = null,
    val activeGroup: ActiveGroupRoom? = null,
    val groupActionBusy: Boolean = false,
    val sessions: List<HermesSession> = emptyList(),
    val sessionsHasMore: Boolean = false,
    val sessionsLoadingMore: Boolean = false,
    val includeArchivedSessions: Boolean = false,
    val sessionSearchQuery: String = "",
    val sessionSearchResults: List<HermesSession> = emptyList(),
    val sessionSearchLoading: Boolean = false,
    val cronJobs: List<CronJob> = emptyList(),
    val cronDeliveryTargets: List<CronDeliveryTarget> = listOf(CronDeliveryTarget("local", "Local")),
    val cronBlueprints: List<AutomationBlueprint> = emptyList(),
    val cronRuns: Map<String, List<CronRun>> = emptyMap(),
    val messaging: List<MessagingPlatform> = emptyList(),
    val pendingPairing: List<PairingUser> = emptyList(),
    val approvedPairing: List<PairingUser> = emptyList(),
    val webhooks: WebhookState = WebhookState(),
    val webhookReceipt: WebhookReceipt? = null,
    val selectedProfile: String = "default",
    val sessionProfileFilter: String = "all",
    val activeChat: ActiveChat? = null,
    /** Chats with something to show in their status dot (Desktop's sidebar dots), by stored session id. */
    val sessionDots: Map<String, com.nousresearch.hermesmobile.data.SessionDot> = emptyMap(),
    val loadingOlderMessages: Boolean = false,
    val tools: List<ToolActivity> = emptyList(),
    val chatDraft: String = "",
    val composerAttachments: List<ComposerAttachment> = emptyList(),
    val queuedPrompts: List<QueuedPrompt> = emptyList(),
    val reasoningText: String = "",
    val liveStatuses: List<LiveTaskStatus> = emptyList(),
    val artifactSuggestions: List<ArtifactSuggestion> = emptyList(),
    val sessionModelChoices: List<SessionModelChoice> = emptyList(),
    val sessionUsage: SessionUsage? = null,
    val modelOptionsLoading: Boolean = false,
    val interaction: ServerInteraction? = null,
    val interactionQueue: List<ServerInteraction> = emptyList(),
    val telegramOnboarding: TelegramOnboarding? = null,
    val botEditor: BotProfileConfig? = null,
    val botEditorLoadingProfile: String? = null,
    val botEditorSaving: Boolean = false,
    val pendingBotModelSave: PendingBotModelSave? = null,
    val capabilityProfile: String = "default",
    val capabilities: CapabilitySnapshot? = null,
    val capabilitiesLoading: Boolean = false,
    val capabilitySkillSearchResults: List<CapabilitySkill> = emptyList(),
    val capabilitySkillDetail: CapabilitySkillDocument? = null,
    val capabilityToolsetConfig: CapabilityToolsetConfig? = null,
    val capabilityOAuthFlow: CapabilityOAuthFlow? = null,
    val pendingCapabilityModelChange: PendingCapabilityModelChange? = null,
    val pendingSessionModelChange: PendingSessionModelChange? = null,
    val capabilityActionBusy: String? = null,
    val slashCompletions: List<SlashCompletion> = emptyList(),
    val slashReplaceFrom: Int = 1,
    /** The `/…` text the palette is answering; null hides it. */
    val slashQuery: String? = null,
    val slashLoading: Boolean = false,
    val commandOutput: CommandOutput? = null,
    val snackbar: String? = null
)

/** Display-only result of a slash command, shown in a sheet over the chat. */
data class CommandOutput(val command: String, val output: String)

class HermesViewModel(application: Application) : AndroidViewModel(application), HermesGatewayListener {
    private val store = GatewayConfigStore(application)
    private val composerStore = ChatComposerStore(application)
    private var repository: HermesRepository? = null
    private var readyWaiter: CompletableDeferred<Unit>? = null

    private var navigationGeneration = 0L
    private var botEditorGeneration = 0L
    private var refreshGeneration = 0L
    private var searchGeneration = 0L
    private var sessionSearchJob: Job? = null
    private var composerSaveJob: Job? = null
    private var slashCompletionJob: Job? = null
    private var slashCatalogJob: Job? = null
    /** `commands.catalog` of one runtime session; an empty catalog marks a gateway without it. */
    private var slashCatalog: Pair<String, SlashCatalog>? = null
    private var groupPollingJob: Job? = null
    private val liveSessions = com.nousresearch.hermesmobile.data.LiveSessions()
    private var liveSessionsJob: Job? = null
    private val liveSessionsPoke = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    private var groupGeneration = 0L
    private var telegramOnboardingPollingJob: Job? = null
    private var nativeAuthJob: Job? = null
    private var appForeground = true
    private var networkOnline = true
    private var transportGeneration = 0L
    private var connectionJob: Job? = null
    private var connectionGeneration = 0L
    private var capabilitiesGeneration = 0L
    private var capabilityOAuthJob: Job? = null

    val avatarAssets = mutableStateMapOf<String, String>()
    private val avatarMissing = mutableSetOf<String>()
    private val avatarLoading = mutableSetOf<String>()
    private val avatarCacheOrder = ArrayDeque<String>()

    private val streamBuffer = StringBuilder()
    private var streamFlushJob: Job? = null
    // A session-scoped model pick must survive the lazy first-turn agent build.
    // The backend config.set call is authoritative; this pin additionally fences
    // stale session.info frames and reasserts the choice immediately before turn 1.
    private val sessionModelPins = mutableMapOf<String, SessionModelPin>()

    var streamingText by mutableStateOf("")
        private set

    private val initialStoreLoad = store.loadSnapshotResult()
    private val initialStoreSnapshot = (initialStoreLoad as? GatewayStoreLoadResult.Loaded)?.snapshot ?: GatewayStoreSnapshot()

    var uiState by mutableStateOf(
        HermesUiState(
            savedConfig = initialStoreSnapshot.active,
            connections = initialStoreSnapshot.connections,
            activeConnectionId = initialStoreSnapshot.active?.id,
            selectedProfile = initialStoreSnapshot.active?.selectedProfile ?: "default",
            capabilityProfile = initialStoreSnapshot.active?.selectedProfile ?: "default",
            error = (initialStoreLoad as? GatewayStoreLoadResult.Invalid)?.message
        )
    )
        private set

    init {
        initialStoreSnapshot.active?.let { cfg ->
            if (cfg.baseUrl.isNotBlank() && cfg.hasCredential) connect(cfg, silent = true)
        }
    }

    fun discoverConnection(label: String, baseUrl: String, extraHeaders: Map<String, String> = emptyMap()) {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null, connectionDiscovery = null)
            runCatching {
                val canonicalUrl = GatewayUrlPolicy.canonical(baseUrl)
                val uri = URI(canonicalUrl)
                val draft = GatewayConfig(
                    id = UUID.randomUUID().toString(),
                    label = label.trim().ifBlank { GatewayUrlPolicy.defaultLabel(uri) },
                    baseUrl = canonicalUrl,
                    authMode = GatewayAuthMode.NONE,
                    extraHeaders = extraHeaders
                )
                HermesAuthClient(draft.baseUrl, draft.extraHeaders).discover(draft)
            }.onSuccess { discovery ->
                val normalized = if (!discovery.status.authRequired) {
                    discovery.copy(draft = discovery.draft.copy(authMode = GatewayAuthMode.NONE))
                } else discovery
                uiState = uiState.copy(loading = false, connectionDiscovery = normalized, error = null)
            }.onFailure { t ->
                val canonical = runCatching { GatewayUrlPolicy.normalize(baseUrl) }.getOrDefault(baseUrl.trim())
                uiState = uiState.copy(loading = false, error = connectionErrorMessage(t, canonical))
            }
        }
    }

    fun cancelConnectionSetup() {
        nativeAuthJob?.cancel()
        nativeAuthJob = null
        uiState = uiState.copy(connectionDiscovery = null, authLaunchUrl = null, error = null)
    }

    fun consumeAuthLaunchUrl() {
        uiState = uiState.copy(authLaunchUrl = null)
    }

    fun cancelNativeSignIn(message: String) {
        nativeAuthJob?.cancel()
        nativeAuthJob = null
        uiState = uiState.copy(authLaunchUrl = null, loading = false, error = message)
    }

    fun connectDiscoveredWithoutAuth() {
        val discovery = uiState.connectionDiscovery ?: return
        connect(discovery.draft.copy(authMode = GatewayAuthMode.NONE))
    }

    fun connectWithSessionToken(token: String) {
        val discovery = uiState.connectionDiscovery ?: return
        val value = token.trim()
        if (value.isBlank()) {
            uiState = uiState.copy(error = string(R.string.i18n_vm_need_token))
            return
        }
        connect(discovery.draft.copy(authMode = GatewayAuthMode.SESSION_TOKEN, token = value))
    }

    fun connectWithPassword(username: String, password: String, provider: String? = null) {
        val discovery = uiState.connectionDiscovery ?: return
        val passwordProvider = provider?.takeIf { it.isNotBlank() }
            ?: discovery.passwordProviders.firstOrNull()?.name
            ?: discovery.status.authProviders.firstOrNull { it == "basic" }
            ?: "basic"
        if (username.isBlank() || password.isBlank()) {
            uiState = uiState.copy(error = string(R.string.i18n_vm_need_login))
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            runCatching {
                val cookie = HermesAuthClient(discovery.draft.baseUrl, discovery.draft.extraHeaders)
                    .passwordLogin(passwordProvider, username, password)
                discovery.draft.copy(
                    authMode = GatewayAuthMode.BASIC,
                    cookieHeader = cookie,
                    authProvider = passwordProvider
                )
            }.onSuccess { connect(it) }
                .onFailure { uiState = uiState.copy(loading = false, error = it.message) }
        }
    }

    fun startNativeSignIn(provider: String? = null) {
        val discovery = uiState.connectionDiscovery ?: return
        if (!discovery.status.supportsNativePkce) {
            uiState = uiState.copy(error = string(R.string.i18n_vm_no_pkce))
            return
        }
        nativeAuthJob?.cancel()
        nativeAuthJob = viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            runCatching {
                val request = NativePkceCoordinator.begin(
                    scope = this,
                    baseUrl = discovery.draft.baseUrl,
                    provider = provider?.takeIf { it.isNotBlank() }
                )
                uiState = uiState.copy(authLaunchUrl = request.authorizeUrl)
                val code = withTimeout(185_000) { request.callback.await() }
                val tokens = HermesAuthClient(discovery.draft.baseUrl, discovery.draft.extraHeaders)
                    .exchangeNativeCode(code, request.verifier, request.redirectUri)
                discovery.draft.copy(
                    authMode = GatewayAuthMode.NATIVE_PKCE,
                    nativeTokens = tokens,
                    authProvider = tokens.provider.ifBlank { provider.orEmpty() }
                )
            }.onSuccess { connect(it) }
                .onFailure { uiState = uiState.copy(loading = false, authLaunchUrl = null, error = it.message) }
        }
    }

    fun onEnvironmentChanged(foreground: Boolean, online: Boolean, networkChanged: Boolean = false) {
        val becameAvailable = foreground && online && (!appForeground || !networkOnline || networkChanged)
        appForeground = foreground
        networkOnline = online
        repository?.gateway?.setEnvironment(foreground, online, networkChanged)
        if (!foreground) {
            persistComposerSoon(immediate = true)
            liveSessionsJob?.cancel()
            groupPollingJob?.cancel()
            telegramOnboardingPollingJob?.cancel()
        } else if (becameAvailable) {
            startLiveSessions()
            if (uiState.section == MainSection.BOTS) uiState.activeGroup?.let { startGroupPolling(it.room.roomId, groupGeneration) }
            if (uiState.section == MainSection.MESSAGING) uiState.telegramOnboarding?.takeUnless { it.status.lowercase() in setOf("ready", "expired", "cancelled", "failed", "error") }?.let { pollTelegramOnboarding(uiState.selectedProfile, it) }
        }
    }

    fun featureRepository(): HermesRepository? = repository

    fun connect(config: GatewayConfig, silent: Boolean = false) {
        val generation = ++connectionGeneration
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            val wasStoredBeforeConnect = store.snapshot().connections.any { it.id == config.id }
            uiState = uiState.copy(
                loading = !silent,
                error = null,
                savedConfig = config,
                activeConnectionId = config.id,
                selectedProfile = config.selectedProfile.ifBlank { "default" },
                capabilityProfile = config.selectedProfile.ifBlank { "default" }
            )
            try {
                val canonicalUrl = GatewayUrlPolicy.canonical(config.baseUrl)
                var working = config.copy(baseUrl = canonicalUrl)
                val authClient = HermesAuthClient(working.baseUrl, working.extraHeaders)
                val status = authClient.status()
                if (status.authRequired && (working.authMode == GatewayAuthMode.NONE || !working.hasCredential)) {
                    val discovery = authClient.discover(working)
                    uiState = uiState.copy(loading = false, connectionState = GatewayConnectionState.DISCONNECTED, connectionDiscovery = discovery)
                    return@launch
                }
                if (working.nativeTokens.usable && working.nativeTokens.needsRefresh()) {
                    val refresh = working.nativeTokens.refreshToken
                    if (refresh.isBlank()) throw IllegalStateException("La sesión de Hermes ha caducado; inicia sesión de nuevo")
                    val tokens = authClient.refreshNative(refresh)
                    working = working.copy(nativeTokens = tokens, authProvider = tokens.provider.ifBlank { working.authProvider })
                }

                repository?.gateway?.close()
                repository = null
                // Existing trusted connections may persist cookie/token rotation after a protected
                // REST proof; brand-new connections still wait for gateway.ready before first save.
                var mayPersistAuthUpdates = wasStoredBeforeConnect
                val rest = HermesRestClient(
                    initialConfig = working,
                    onConfigUpdated = { updated ->
                        // A successful REST probe is not enough to persist credentials. Cookie/token
                        // rotations are saved only after this connection has completed gateway.ready.
                        viewModelScope.launch {
                            if (generation == connectionGeneration && mayPersistAuthUpdates) {
                                persistActiveConfig(updated, makeActive = true)
                            }
                        }
                    },
                    onAuthExpired = { message ->
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            if (generation == connectionGeneration && mayPersistAuthUpdates) {
                                requireReauthentication(message)
                            }
                        }
                    }
                )
                if (status.authRequired) {
                    // Public /api/status is not an auth proof. Verify the protected plane before
                    // opening the socket so stale cookies/tokens fail with a useful error.
                    rest.profiles()
                }
                val guardedListener = listenerFor(generation) { mayPersistAuthUpdates = true }
                val gateway = HermesGatewayClient(
                    working,
                    guardedListener,
                    wsTicketProvider = { if (status.authRequired) rest.wsTicket() else null }
                )
                repository = HermesRepository(working, rest, gateway)
                if (generation != connectionGeneration) return@launch
                uiState = uiState.copy(status = status, error = null, connectionDiscovery = null, authLaunchUrl = null)
                readyWaiter = CompletableDeferred()
                gateway.setEnvironment(appForeground, networkOnline)
                gateway.connect()
                try { withTimeout(25_000) { readyWaiter?.await() } }
                catch (_: TimeoutCancellationException) {
                    // The transport owns bounded retries; a slow network must not destroy it.
                    if (generation == connectionGeneration) uiState = uiState.copy(loading = false)
                    return@launch
                }
                if (generation != connectionGeneration) return@launch

                mayPersistAuthUpdates = true
                val persisted = rest.currentConfig().copy(selectedProfile = uiState.selectedProfile)
                persistActiveConfig(persisted, makeActive = true)
                refreshAll()
            } catch (t: Throwable) {
                if (generation != connectionGeneration) return@launch
                repository?.gateway?.close()
                repository = null
                val authRejected = (t as? HermesHttpException)?.status == 401 || (t as? GatewayAuthException)?.status == 401
                val discovery = if (authRejected) {
                    val cleared = config.copy(
                        authMode = GatewayAuthMode.NONE,
                        token = "",
                        cookieHeader = "",
                        nativeTokens = NativeTokenSet(),
                        authProvider = ""
                    )
                    if (wasStoredBeforeConnect) store.save(cleared, makeActive = true)
                    runCatching { HermesAuthClient(cleared.baseUrl, cleared.extraHeaders).discover(cleared) }.getOrNull()
                } else null
                val snapshot = store.snapshot()
                uiState = uiState.copy(
                    savedConfig = snapshot.active ?: config,
                    connections = snapshot.connections,
                    activeConnectionId = snapshot.active?.id ?: config.id,
                    loading = false,
                    connectionState = if (discovery != null) GatewayConnectionState.DISCONNECTED else GatewayConnectionState.FAILED,
                    connectionDiscovery = discovery,
                    error = if (authRejected) "La sesión de Hermes ha caducado. Inicia sesión de nuevo." else connectionErrorMessage(t, config.baseUrl)
                )
            }
        }
    }

    private fun connectionErrorMessage(t: Throwable, baseUrl: String): String {
        val root = generateSequence(t) { it.cause }.last()
        val original = t.message.orEmpty().ifBlank { root.message.orEmpty() }
        val isConnectFailure = root is ConnectException || original.contains("failed to connect", ignoreCase = true) ||
            original.contains("connect timed out", ignoreCase = true)
        if (isConnectFailure) {
            val host = runCatching { URI(baseUrl).host }.getOrNull().orEmpty()
            return "No se puede alcanzar Hermes en $baseUrl. " +
                if (GatewayUrlPolicy.isPrivateOrLocalHost(host)) {
                    "Comprueba que el móvil está en la misma red y que Hermes escucha en 0.0.0.0 (o en $host), no solo en 127.0.0.1. " +
                        "Para una instalación CLI remota: hermes serve --host 0.0.0.0 --port 9119. Error: ${original.ifBlank { "conexión rechazada" }}"
                } else {
                    "Comprueba la URL, DNS/firewall y que el gateway esté escuchando. Error: ${original.ifBlank { "conexión rechazada" }}"
                }
        }
        return original.ifBlank { "No se pudo conectar con Hermes" }
    }

    private fun listenerFor(generation: Long, onReady: () -> Unit = {}): HermesGatewayListener = object : HermesGatewayListener {
        private fun dispatch(action: () -> Unit) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                if (generation == connectionGeneration) action()
            }
        }
        override fun onConnectionState(state: GatewayConnectionState, error: String?) = dispatch {
            this@HermesViewModel.onConnectionState(state, error)
        }
        override fun onGatewayReady(payload: JSONObject) = dispatch {
            onReady()
            this@HermesViewModel.onGatewayReady(payload)
        }
        override fun onAuthenticationExpired() = dispatch {
            requireReauthentication("La sesión de Hermes ha caducado. Inicia sesión de nuevo.")
        }
        override fun onEvent(type: String, payload: JSONObject) = dispatch {
            this@HermesViewModel.onEvent(type, payload)
        }
        override fun onServerRequest(request: ServerInteraction) = dispatch {
            this@HermesViewModel.onServerRequest(request)
        }
    }

    private fun persistActiveConfig(config: GatewayConfig, makeActive: Boolean) {
        store.save(config, makeActive = makeActive)
        val snapshot = store.snapshot()
        uiState = uiState.copy(
            savedConfig = if (makeActive) config else uiState.savedConfig,
            connections = snapshot.connections,
            activeConnectionId = snapshot.active?.id
        )
    }

    fun disconnect(clearSaved: Boolean = false) {
        persistCurrentComposerNow()
        connectionGeneration += 1
        navigationGeneration += 1
        // Every network action is a ViewModel child. Cancelling existing children here gives
        // gateway switches a hard isolation boundary in addition to generation/repository guards.
        viewModelScope.coroutineContext.cancelChildren()
        searchGeneration += 1
        streamFlushJob?.cancel()
        sessionSearchJob?.cancel()
        groupPollingJob?.cancel()
        telegramOnboardingPollingJob?.cancel()
        nativeAuthJob?.cancel()
        liveSessionsJob?.cancel()
        liveSessions.clear()
        groupGeneration += 1
        clearAvatarCache()
        avatarLoading.clear()
        resetStream()
        readyWaiter?.cancel()
        readyWaiter = null
        repository?.gateway?.close()
        repository = null
        if (clearSaved) store.clear()
        val snapshot = store.snapshot()
        uiState = HermesUiState(
            savedConfig = snapshot.active,
            connections = snapshot.connections,
            activeConnectionId = snapshot.active?.id,
            selectedProfile = snapshot.active?.selectedProfile ?: "default",
            capabilityProfile = snapshot.active?.selectedProfile ?: "default"
        )
    }

    fun switchConnection(id: String) {
        val target = store.snapshot().connections.firstOrNull { it.id == id } ?: return
        if (uiState.connectionState == GatewayConnectionState.CONNECTED && uiState.activeConnectionId == id) return
        disconnect(clearSaved = false)
        store.setActive(id)
        connect(target, silent = false)
    }

    private fun removePushConnection(id: String) {
        val accounts = runCatching { com.nousresearch.hermesmobile.push.PushStore(getApplication()).forgetConnection(id) }.getOrDefault(emptyList())
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            accounts.forEach { runCatching { com.nousresearch.hermesmobile.push.PushClient.call(it, "device.revoke") } }
        }
    }

    fun forgetConnection(id: String) {
        val folders = getApplication<android.app.Application>().getSharedPreferences("chat_folders", android.content.Context.MODE_PRIVATE)
        val editor = folders.edit()
        folders.all.keys.filter { runCatching { JSONArray(it).optString(0) == id }.getOrDefault(false) }.forEach { editor.remove(it) }
        editor.apply()
        removePushConnection(id)
        val wasActive = uiState.activeConnectionId == id
        if (wasActive) disconnect(clearSaved = false)
        store.remove(id)
        val snapshot = store.snapshot()
        uiState = uiState.copy(
            savedConfig = snapshot.active,
            connections = snapshot.connections,
            activeConnectionId = snapshot.active?.id,
            selectedProfile = snapshot.active?.selectedProfile ?: "default",
            capabilityProfile = snapshot.active?.selectedProfile ?: "default",
            snackbar = string(R.string.i18n_vm_conn_removed)
        )
    }

    fun addAnotherConnection() {
        disconnect(clearSaved = false)
        uiState = uiState.copy(savedConfig = null, activeConnectionId = null, connectionDiscovery = null)
    }

    fun signOutActive() {
        uiState.activeConnectionId?.let { removePushConnection(it) }
        requireReauthentication(null)
    }

    private fun requireReauthentication(reason: String?) {
        val current = uiState.savedConfig ?: return
        val cleared = current.copy(
            authMode = GatewayAuthMode.NONE,
            token = "",
            cookieHeader = "",
            nativeTokens = NativeTokenSet(),
            authProvider = ""
        )
        disconnect(clearSaved = false)
        store.save(cleared, makeActive = true)
        val snapshot = store.snapshot()
        uiState = HermesUiState(
            savedConfig = cleared,
            connections = snapshot.connections,
            activeConnectionId = cleared.id,
            selectedProfile = cleared.selectedProfile,
            capabilityProfile = cleared.selectedProfile,
            loading = true,
            error = reason
        )
        viewModelScope.launch {
            runCatching { HermesAuthClient(cleared.baseUrl, cleared.extraHeaders).discover(cleared) }
                .onSuccess { discovery ->
                    uiState = uiState.copy(loading = false, connectionDiscovery = discovery, error = reason)
                }
                .onFailure { t -> uiState = uiState.copy(loading = false, error = reason ?: t.message) }
        }
    }

    /**
     * Desktop's sidebar liveness: `session.active_list` every few seconds while the app is in the foreground, and
     * at once when a turn starts or ends elsewhere. Stops in the background; the next foreground restarts it.
     */
    private fun startLiveSessions() {
        if (!appForeground || liveSessionsJob?.isActive == true) return
        val repo = repository ?: return
        liveSessionsJob = viewModelScope.launch {
            while (repository === repo && appForeground) {
                val snapshot = runCatching { repo.gateway.requestObject("session.active_list", JSONObject(), 15_000) }.getOrNull()
                if (snapshot != null) {
                    val viewing = uiState.activeChat?.storedSessionId?.takeIf { uiState.section == MainSection.CHAT }
                    val changed = liveSessions.apply(snapshot, viewing)
                    if (changed || (viewing != null && liveSessions.seen(viewing))) uiState = uiState.copy(sessionDots = liveSessions.dots())
                }
                kotlinx.coroutines.withTimeoutOrNull(LIVE_SESSIONS_POLL_MS) { liveSessionsPoke.receive() }
            }
        }
    }

    /** Opening a chat reads its finished turn, as in Desktop. */
    private fun markChatSeen(storedId: String) {
        if (liveSessions.seen(storedId)) uiState = uiState.copy(sessionDots = liveSessions.dots())
    }

    /** The page a chat was opened from (Bots, Home…): back from the chat returns there, not to the Chats list. */
    private var chatReturnSection: MainSection? = null

    private fun rememberChatOrigin() {
        chatReturnSection = when {
            uiState.section != MainSection.CHAT -> uiState.section
            uiState.activeChat != null -> chatReturnSection // one chat to another keeps the first one's origin
            else -> null
        }
    }

    fun closeActiveChat() {
        val back = chatReturnSection
        persistCurrentComposerNow()
        uiState.activeChat?.runtimeSessionId?.let(sessionModelPins::remove)
        navigationGeneration += 1
        resetStream()
        composerSaveJob?.cancel()
        uiState = uiState.copy(
            activeChat = null,
            tools = emptyList(),
            chatDraft = "",
            composerAttachments = emptyList(),
            queuedPrompts = emptyList(),
            reasoningText = "",
            liveStatuses = emptyList(),
            artifactSuggestions = emptyList(),
            sessionModelChoices = emptyList(),
            sessionUsage = null,
            loadingOlderMessages = false,
            slashCompletions = emptyList(),
            slashQuery = null,
            slashLoading = false,
            commandOutput = null,
            section = MainSection.CHAT
        )
        refreshSessions()
        if (back != null) switchSection(back, record = false)
    }

    /** The pages visited before this one, so system back returns to the previous page rather than to Home. */
    private val sectionHistory = ArrayDeque<MainSection>()
    val canNavigateBack: Boolean get() = sectionHistory.isNotEmpty() || uiState.section != MainSection.HOME

    /** System back: the previous page, or Home (the start destination) once there is none. */
    fun navigateBack() {
        val previous = sectionHistory.removeLastOrNull() ?: MainSection.HOME.takeIf { uiState.section != MainSection.HOME } ?: return
        switchSection(previous, record = false)
    }

    fun switchSection(section: MainSection, record: Boolean = true) {
        if (record && section != uiState.section) {
            sectionHistory.remove(section)
            sectionHistory.addLast(uiState.section)
            while (sectionHistory.size > 20) sectionHistory.removeFirst()
        }
        chatReturnSection = null
        if (section != MainSection.BOTS) {
            // Hosted room work is gateway-owned and continues. Only retire the
            // mobile observer while its surface is not visible.
            groupPollingJob?.cancel()
            groupPollingJob = null
        }
        uiState = uiState.copy(section = section)
        when (section) {
            MainSection.BOTS -> {
                // No asset revision is exposed by profiles.get_asset. Re-entering
                // Bot Mode is therefore our explicit cross-device cache fence.
                clearAvatarCache()
                // The roster is built from the profiles: rebuild it once they (and their latest messages) are in.
                refreshProfiles(then = ::refreshBotRoster)
                if (uiState.activeGroup != null) {
                    startGroupPolling(uiState.activeGroup!!.room.roomId, groupGeneration)
                } else {
                    refreshGroups()
                }
            }
            MainSection.CRON -> refreshCron()
            MainSection.MESSAGING -> viewModelScope.launch { refreshMessagingAndPairing() }
            MainSection.CAPABILITIES, MainSection.PROVIDERS -> {
                val profile = uiState.capabilityProfile.takeIf { p -> uiState.profiles.any { it.name == p } }
                    ?: uiState.selectedProfile
                uiState = uiState.copy(capabilityProfile = profile)
                refreshCapabilities(profile)
            }
            MainSection.PROFILES -> refreshProfiles()
            // Home shows what is scheduled next.
            MainSection.HOME -> refreshCron()
            // Chats lists the scheduled jobs in its Routines section.
            MainSection.CHAT -> refreshCron()
            MainSection.SYSTEM, MainSection.MORE, MainSection.NOTIFICATIONS, MainSection.FILES, MainSection.MEMORY,
            MainSection.MODELS, MainSection.VOICE, MainSection.SERVER_SETTINGS, MainSection.GATEWAYS,
            MainSection.APPEARANCE, MainSection.ASSISTANT, MainSection.ABOUT -> Unit
        }
    }

    fun selectProfile(name: String) {
        if (name == uiState.selectedProfile) return
        telegramOnboardingPollingJob?.cancel()
        val updatedConfig = uiState.savedConfig?.copy(selectedProfile = name)
        if (updatedConfig != null) persistActiveConfig(updatedConfig, makeActive = true)
        uiState = uiState.copy(selectedProfile = name, telegramOnboarding = null, webhookReceipt = null)
        viewModelScope.launch {
            refreshMessagingAndPairing()
            refreshCronSupport(name)
        }
    }


    fun selectCapabilityProfile(name: String) {
        if (name == uiState.capabilityProfile) return
        capabilitiesGeneration += 1
        capabilityOAuthJob?.cancel()
        uiState = uiState.copy(
            capabilityProfile = name,
            capabilities = null,
            capabilitySkillSearchResults = emptyList(),
            capabilitySkillDetail = null,
            capabilityToolsetConfig = null,
            capabilityOAuthFlow = null,
            pendingCapabilityModelChange = null
        )
        refreshCapabilities(name)
    }

    fun refreshCapabilities(profile: String = uiState.capabilityProfile) {
        val repo = repository ?: return
        val connection = connectionGeneration
        val generation = ++capabilitiesGeneration
        viewModelScope.launch {
            uiState = uiState.copy(capabilitiesLoading = true, error = null)
            val snapshot = runCatching { loadCapabilitiesSnapshot(repo, profile) }
            if (connection != connectionGeneration || generation != capabilitiesGeneration || repository !== repo) return@launch
            snapshot.onSuccess {
                uiState = uiState.copy(capabilities = it, capabilitiesLoading = false)
            }.onFailure {
                uiState = uiState.copy(capabilitiesLoading = false, error = it.message ?: "Could not load Hermes capabilities")
            }
        }
    }

    private suspend fun loadCapabilitiesSnapshot(repo: HermesRepository, profile: String): CapabilitySnapshot = coroutineScope {
        val modelInfo = async { runCatching { repo.rest.capabilityModelInfo(profile) } }
        val models = async { runCatching { repo.rest.capabilityModelOptions(profile) } }
        val skills = async { runCatching { repo.rest.capabilitySkills(profile) } }
        val official = async { runCatching { repo.rest.capabilityOfficialSkills(profile) } }
        val toolsets = async { runCatching { repo.rest.capabilityToolsets(profile) } }
        val mcp = async { runCatching { repo.rest.capabilityMcpServers(profile) } }
        val mcpCatalog = async { runCatching { repo.rest.capabilityMcpCatalog(profile) } }
        val env = async { runCatching { repo.rest.capabilityEnvVars(profile) } }
        val endpoints = async { runCatching { repo.rest.capabilityCustomEndpoints(profile) } }
        val oauth = async { runCatching { repo.rest.capabilityOAuthProviders(profile) } }

        val modelInfoR = modelInfo.await()
        val modelsR = models.await()
        val skillsR = skills.await()
        val officialR = official.await()
        val toolsetsR = toolsets.await()
        val mcpR = mcp.await()
        val mcpCatalogR = mcpCatalog.await()
        val envR = env.await()
        val endpointsR = endpoints.await()
        val oauthR = oauth.await()
        val unsupported = buildSet {
            if (modelInfoR.isFailure || modelsR.isFailure) add("models")
            if (skillsR.isFailure) add("skills")
            if (officialR.isFailure) add("skills-hub")
            if (toolsetsR.isFailure) add("toolsets")
            if (mcpR.isFailure) add("mcp")
            if (mcpCatalogR.isFailure) add("mcp-catalog")
            if (envR.isFailure) add("credentials")
            if (endpointsR.isFailure) add("custom-endpoints")
            if (oauthR.isFailure) add("oauth")
        }
        CapabilitySnapshot(
            profile = profile,
            modelInfo = modelInfoR.getOrDefault(CapabilityModelInfo()),
            modelProviders = modelsR.getOrDefault(emptyList()),
            skills = skillsR.getOrDefault(emptyList()),
            officialSkills = officialR.getOrDefault(emptyList()),
            toolsets = toolsetsR.getOrDefault(emptyList()),
            mcpServers = mcpR.getOrDefault(emptyList()),
            mcpCatalog = mcpCatalogR.getOrDefault(emptyList()),
            envVars = envR.getOrDefault(emptyList()),
            customEndpoints = endpointsR.getOrDefault(emptyList()),
            oauthProviders = oauthR.getOrDefault(emptyList()),
            unsupported = unsupported
        )
    }

    private fun capabilityMutation(
        key: String,
        success: String,
        refresh: Boolean = true,
        action: suspend (HermesRepository, String) -> Unit
    ) {
        val repo = repository ?: return
        val profile = uiState.capabilityProfile
        val connection = connectionGeneration
        val generation = capabilitiesGeneration
        viewModelScope.launch {
            uiState = uiState.copy(capabilityActionBusy = key, error = null)
            runCatching { action(repo, profile) }
                .onSuccess {
                    if (connection != connectionGeneration || generation != capabilitiesGeneration || repository !== repo || uiState.capabilityProfile != profile) return@onSuccess
                    uiState = uiState.copy(capabilityActionBusy = null, snackbar = success)
                    if (refresh) refreshCapabilities(profile)
                }
                .onFailure {
                    if (connection == connectionGeneration && generation == capabilitiesGeneration && repository === repo && uiState.capabilityProfile == profile) {
                        uiState = uiState.copy(capabilityActionBusy = null, error = it.message)
                    }
                }
        }
    }

    fun setCapabilityModel(provider: String, model: String) {
        if (provider.isBlank() || model.isBlank()) return
        val repo = repository ?: return
        val profile = uiState.capabilityProfile
        val connection = connectionGeneration
        val generation = capabilitiesGeneration
        viewModelScope.launch {
            uiState = uiState.copy(capabilityActionBusy = "model", error = null)
            runCatching { repo.rest.setCapabilityModel(profile, provider, model) }
                .onSuccess { result ->
                    if (connection != connectionGeneration || generation != capabilitiesGeneration || repository !== repo || uiState.capabilityProfile != profile) return@onSuccess
                    if (result.optBoolean("confirm_required", false)) {
                        uiState = uiState.copy(
                            capabilityActionBusy = null,
                            pendingCapabilityModelChange = PendingCapabilityModelChange(
                                profile = profile,
                                provider = provider,
                                model = model,
                                message = result.optString("confirm_message").ifBlank { "Hermes requires confirmation before switching to this model." }
                            )
                        )
                    } else {
                        uiState = uiState.copy(capabilityActionBusy = null, snackbar = string(R.string.i18n_vm_model_updated))
                        refreshCapabilities(profile)
                    }
                }
                .onFailure { error ->
                    if (connection == connectionGeneration && generation == capabilitiesGeneration && repository === repo && uiState.capabilityProfile == profile) {
                        uiState = uiState.copy(capabilityActionBusy = null, error = error.message)
                    }
                }
        }
    }

    fun confirmCapabilityModelChange() {
        val pending = uiState.pendingCapabilityModelChange ?: return
        if (pending.profile != uiState.capabilityProfile) {
            uiState = uiState.copy(pendingCapabilityModelChange = null)
            return
        }
        capabilityMutation("model-confirm", string(R.string.i18n_vm_model_updated)) { repo, profile ->
            val result = repo.rest.setCapabilityModel(profile, pending.provider, pending.model, confirmExpensive = true)
            if (!result.optBoolean("ok", true)) error(result.optString("confirm_message").ifBlank { "Hermes rejected the model change" })
            uiState = uiState.copy(pendingCapabilityModelChange = null)
        }
    }

    fun cancelCapabilityModelChange() { uiState = uiState.copy(pendingCapabilityModelChange = null) }

    fun setCapabilitySkillEnabled(skill: CapabilitySkill, enabled: Boolean) =
        capabilityMutation("skill:${skill.name}", if (enabled) "Skill enabled" else "Skill disabled") { repo, profile ->
            repo.rest.setCapabilitySkillEnabled(profile, skill.name, enabled)
        }

    fun openCapabilitySkill(skill: CapabilitySkill) {
        val repo = repository ?: return
        val profile = uiState.capabilityProfile
        val connection = connectionGeneration
        val generation = capabilitiesGeneration
        viewModelScope.launch {
            uiState = uiState.copy(capabilityActionBusy = "skill-detail:${skill.name}", error = null)
            runCatching { repo.rest.capabilitySkillContent(profile, skill.name) }
                .onSuccess { result ->
                    if (connection != connectionGeneration || generation != capabilitiesGeneration || repository !== repo || uiState.capabilityProfile != profile) return@onSuccess
                    uiState = uiState.copy(
                        capabilitySkillDetail = CapabilitySkillDocument(
                            name = result.optString("name").ifBlank { skill.name },
                            content = result.optString("content"),
                            path = result.optString("path")
                        ),
                        capabilityActionBusy = null
                    )
                }
                .onFailure { error ->
                    if (connection == connectionGeneration && generation == capabilitiesGeneration && repository === repo && uiState.capabilityProfile == profile) {
                        uiState = uiState.copy(capabilityActionBusy = null, error = error.message)
                    }
                }
        }
    }

    fun closeCapabilitySkill() { uiState = uiState.copy(capabilitySkillDetail = null) }

    fun searchCapabilitySkills(query: String) {
        val repo = repository ?: return
        val profile = uiState.capabilityProfile
        val generation = capabilitiesGeneration
        if (query.isBlank()) {
            uiState = uiState.copy(capabilitySkillSearchResults = emptyList())
            return
        }
        viewModelScope.launch {
            runCatching { repo.rest.searchCapabilitySkills(profile, query) }
                .onSuccess {
                    if (repository === repo && generation == capabilitiesGeneration) {
                        uiState = uiState.copy(capabilitySkillSearchResults = it)
                    }
                }
                .onFailure { if (repository === repo && generation == capabilitiesGeneration) uiState = uiState.copy(error = it.message) }
        }
    }

    fun installCapabilitySkill(skill: CapabilitySkill) =
        capabilityMutation("skill-install:${skill.identifier}", "Skill installation started") { repo, profile ->
            repo.rest.installCapabilitySkill(profile, skill.identifier.ifBlank { skill.name })
        }

    fun uninstallCapabilitySkill(skill: CapabilitySkill) =
        capabilityMutation("skill-uninstall:${skill.name}", "Skill uninstall started") { repo, profile ->
            repo.rest.uninstallCapabilitySkill(profile, skill.name)
        }

    fun updateCapabilitySkills() =
        capabilityMutation("skills-update", "Skill update started") { repo, profile ->
            repo.rest.updateCapabilitySkills(profile)
        }

    fun setCapabilityToolsetEnabled(toolset: CapabilityToolset, enabled: Boolean) =
        capabilityMutation("toolset:${toolset.name}", if (enabled) "Toolset enabled" else "Toolset disabled") { repo, profile ->
            repo.rest.setCapabilityToolsetEnabled(profile, toolset.name, enabled)
        }

    fun loadCapabilityToolsetConfig(toolset: CapabilityToolset) {
        val repo = repository ?: return
        val profile = uiState.capabilityProfile
        val generation = capabilitiesGeneration
        viewModelScope.launch {
            uiState = uiState.copy(capabilityActionBusy = "toolset-config:${toolset.name}")
            runCatching { repo.rest.capabilityToolsetConfig(profile, toolset.name) }
                .onSuccess {
                    if (repository === repo && generation == capabilitiesGeneration) {
                        uiState = uiState.copy(capabilityToolsetConfig = it, capabilityActionBusy = null)
                    }
                }.onFailure {
                    if (repository === repo && generation == capabilitiesGeneration) {
                        uiState = uiState.copy(capabilityActionBusy = null, error = it.message)
                    }
                }
        }
    }

    fun closeCapabilityToolsetConfig() { uiState = uiState.copy(capabilityToolsetConfig = null) }

    fun selectCapabilityToolsetProvider(toolset: String, provider: String) =
        capabilityMutation("toolset-provider:$toolset", "Tool provider updated") { repo, profile ->
            repo.rest.selectCapabilityToolsetProvider(profile, toolset, provider)
        }

    fun saveCapabilityToolsetEnv(toolset: String, env: Map<String, String>) =
        capabilityMutation("toolset-env:$toolset", "Tool credentials saved") { repo, profile ->
            repo.rest.saveCapabilityToolsetEnv(profile, toolset, env.filterValues(String::isNotBlank))
        }

    fun addCapabilityMcpServer(name: String, url: String, command: String, args: List<String>) {
        if (name.isBlank() || (url.isBlank() && command.isBlank())) {
            uiState = uiState.copy(error = string(R.string.i18n_vm_mcp_needs))
            return
        }
        capabilityMutation("mcp-add", "MCP server added") { repo, profile ->
            repo.rest.addCapabilityMcpServer(profile, name.trim(), url.trim(), command.trim(), args.filter(String::isNotBlank))
        }
    }

    fun removeCapabilityMcpServer(server: CapabilityMcpServer) =
        capabilityMutation("mcp-remove:${server.name}", "MCP server removed") { repo, profile ->
            repo.rest.removeCapabilityMcpServer(profile, server.name)
        }

    fun setCapabilityMcpEnabled(server: CapabilityMcpServer, enabled: Boolean) =
        capabilityMutation("mcp:${server.name}", if (enabled) "MCP enabled" else "MCP disabled") { repo, profile ->
            repo.rest.setCapabilityMcpServerEnabled(profile, server.name, enabled)
        }

    fun testCapabilityMcp(server: CapabilityMcpServer) {
        val repo = repository ?: return
        val profile = uiState.capabilityProfile
        capabilityMutation("mcp-test:${server.name}", "MCP test completed", refresh = false) { _, _ ->
            val result = repo.rest.testCapabilityMcpServer(profile, server.name)
            if (!result.optBoolean("ok", false)) error(result.optString("error").ifBlank { "MCP test failed" })
            val count = (result.optJSONArray("tools") ?: org.json.JSONArray()).length()
            uiState = uiState.copy(snackbar = string(R.string.i18n_vm_mcp_tools, server.name, count))
        }
    }

    fun installCapabilityMcpCatalog(entry: CapabilityMcpCatalogEntry, env: Map<String, String>) =
        capabilityMutation("mcp-install:${entry.name}", "MCP installation started") { repo, profile ->
            repo.rest.installCapabilityMcpCatalog(profile, entry.name, env.filterValues(String::isNotBlank))
        }

    fun setCapabilityEnvVar(variable: CapabilityEnvVar, value: String) {
        if (value.isBlank()) return
        capabilityMutation("env:${variable.key}", "Credential saved") { repo, profile ->
            repo.rest.setCapabilityEnvVar(profile, variable.key, value, providerSetup = variable.provider.isNotBlank())
        }
    }

    fun deleteCapabilityEnvVar(variable: CapabilityEnvVar) =
        capabilityMutation("env-delete:${variable.key}", "Credential removed") { repo, profile ->
            repo.rest.deleteCapabilityEnvVar(profile, variable.key)
        }

    fun saveCapabilityCustomEndpoint(
        existingId: String?, name: String, baseUrl: String, model: String, apiKey: String, apiMode: String, makeDefault: Boolean
    ) {
        if (name.isBlank() || baseUrl.isBlank() || model.isBlank()) {
            uiState = uiState.copy(error = string(R.string.i18n_vm_endpoint_needs))
            return
        }
        capabilityMutation("custom-endpoint", "Custom endpoint saved") { repo, profile ->
            val resolved = if (existingId != null && apiKey.isBlank()) {
                // The read contract exposes only a key preview. Do not force the user to re-enter
                // a stored secret just to edit name/model/transport; POST omits blank api_key and
                // the gateway preserves the existing credential.
                baseUrl
            } else {
                val check = repo.rest.validateCapabilityCustomEndpoint(profile, name, baseUrl, model, apiKey, apiMode)
                if (!check.optBoolean("ok", false)) error(check.optString("message").ifBlank { "Custom endpoint validation failed" })
                check.optString("resolved_base_url").ifBlank { baseUrl }
            }
            repo.rest.saveCapabilityCustomEndpoint(profile, existingId, name, resolved, model, apiKey, apiMode, makeDefault = makeDefault)
        }
    }

    fun activateCapabilityCustomEndpoint(endpoint: CapabilityCustomEndpoint) =
        capabilityMutation("custom-activate:${endpoint.id}", "Custom endpoint activated") { repo, profile ->
            repo.rest.activateCapabilityCustomEndpoint(profile, endpoint.id)
        }

    fun deleteCapabilityCustomEndpoint(endpoint: CapabilityCustomEndpoint) =
        capabilityMutation("custom-delete:${endpoint.id}", "Custom endpoint removed") { repo, profile ->
            repo.rest.deleteCapabilityCustomEndpoint(profile, endpoint.id)
        }

    fun startCapabilityOAuth(provider: CapabilityOAuthProvider) {
        if (provider.flow == "device_code") {
            uiState = uiState.copy(
                error = string(R.string.i18n_vm_device_code)
            )
            return
        }
        if (provider.flow == "external") {
            uiState = uiState.copy(error = string(R.string.i18n_vm_external_auth, provider.name))
            return
        }
        val repo = repository ?: return
        val profile = uiState.capabilityProfile
        val generation = capabilitiesGeneration
        capabilityOAuthJob?.cancel()
        capabilityOAuthJob = viewModelScope.launch {
            uiState = uiState.copy(capabilityActionBusy = "oauth:${provider.id}", error = null)
            runCatching { repo.rest.startCapabilityOAuth(profile, provider.id) }
                .onSuccess { flow ->
                    if (repository !== repo || generation != capabilitiesGeneration) return@onSuccess
                    uiState = uiState.copy(capabilityOAuthFlow = flow, capabilityActionBusy = null)
                    val url = flow.authUrl.ifBlank { flow.verificationUrl }
                    if (url.isNotBlank()) {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            getApplication<Application>().startActivity(intent)
                        }.onFailure { uiState = uiState.copy(error = string(R.string.i18n_vm_oauth_open_failed, it.message.orEmpty())) }
                    }
                }
                .onFailure {
                    if (repository === repo && generation == capabilitiesGeneration) {
                        uiState = uiState.copy(capabilityActionBusy = null, error = it.message)
                    }
                }
        }
    }

    fun submitCapabilityOAuthCode(code: String) {
        val flow = uiState.capabilityOAuthFlow ?: return
        if (code.isBlank()) return
        val repo = repository ?: return
        val profile = uiState.capabilityProfile
        capabilityMutation("oauth-submit:${flow.providerId}", "Provider connected", refresh = false) { _, _ ->
            val result = repo.rest.submitCapabilityOAuthCode(profile, flow, code.trim())
            if (!result.optBoolean("ok", false) && result.optString("status") != "approved") {
                error(result.optString("error").ifBlank { "Provider sign-in was not approved" })
            }
            uiState = uiState.copy(capabilityOAuthFlow = null)
            refreshCapabilities(profile)
        }
    }

    fun dismissCapabilityOAuth() { uiState = uiState.copy(capabilityOAuthFlow = null) }

    fun disconnectCapabilityOAuth(provider: CapabilityOAuthProvider) =
        capabilityMutation("oauth-disconnect:${provider.id}", "Provider disconnected") { repo, profile ->
            repo.rest.disconnectCapabilityOAuth(profile, provider.id)
        }

    fun selectSessionFilter(name: String) {
        uiState = uiState.copy(sessionProfileFilter = name)
        if (uiState.sessionSearchQuery.isNotBlank()) searchSessions(uiState.sessionSearchQuery)
    }

    var incomingShare by mutableStateOf<IncomingShare?>(null)
        private set
    var importingShare by mutableStateOf(false)
        private set
    fun receiveShare(intent: Intent?) {
        if (intent?.action !in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) return
        if (importingShare || incomingShare != null) {
            uiState = uiState.copy(error = string(R.string.i18n_vm_share_pending))
            return
        }
        importingShare = true
        viewModelScope.launch {
            try {
                incomingShare = ShareImport.read(getApplication(), requireNotNull(intent))
                uiState = uiState.copy(section = MainSection.CHAT)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { uiState = uiState.copy(error = e.message) }
            finally { importingShare = false }
        }
    }
    fun discardIncomingShare() {
        val pending = incomingShare ?: return
        incomingShare = null
        viewModelScope.launch(Dispatchers.IO) { ShareImport.discard(getApplication(), pending) }
    }
    fun acceptIncomingShare() {
        val pending = incomingShare ?: return
        if (uiState.activeChat == null) return
        if (uiState.composerAttachments.size + pending.files.size > MAX_COMPOSER_ATTACHMENTS) {
            uiState = uiState.copy(error = string(R.string.i18n_vm_six_files))
            return
        }
        uiState = uiState.copy(section = MainSection.CHAT,
            chatDraft = listOf(uiState.chatDraft, pending.text).filter { it.isNotBlank() }.joinToString("\n\n"),
            composerAttachments = uiState.composerAttachments + pending.files)
        incomingShare = null
        persistComposerSoon(immediate = true)
    }

    fun setChatDraft(value: String) = setChatDraft(value, typed = true)

    /**
     * Desktop's edit: the message returns to the composer, and sending it rewinds the chat to that turn and
     * runs it again instead of adding a new message at the end.
     */
    fun startEdit(message: ChatMessage) {
        val chat = uiState.activeChat ?: return
        val index = chat.messages.indexOfFirst { it.stableKey == message.stableKey }
        if (index < 0 || message.role != "user") return
        fun visibleUser(m: ChatMessage) = m.role == "user" && m.renderable
        chat.messages.subList(0, index).count(::visibleUser).let { ordinal ->
            uiState = uiState.copy(activeChat = chat.copy(editing = EditingTurn(
                key = message.stableKey,
                rowId = message.rowId,
                ordinal = ordinal,
                laterTurns = chat.messages.drop(index + 1).count(::visibleUser)
            )))
        }
        // Whole, however long: an edit is not a paste.
        setChatDraft(message.editText, typed = false)
    }

    fun cancelEdit() {
        val chat = uiState.activeChat ?: return
        uiState = uiState.copy(activeChat = chat.copy(editing = null), chatDraft = "")
        persistComposerSoon(immediate = true)
    }

    /** Answers the "remove what follows?" question an edit of an older turn asks. */
    fun confirmEdit(confirmed: Boolean) {
        val chat = uiState.activeChat ?: return
        val editing = chat.editing ?: return
        uiState = uiState.copy(activeChat = chat.copy(editing = editing.copy(confirming = false)))
        if (confirmed) sendMessage(uiState.chatDraft, deepConfirmed = true)
    }

    private fun setChatDraft(value: String, typed: Boolean) {
        if (uiState.activeChat == null) return
        if (typed && pasteToAttachment(uiState.chatDraft, value)) return
        uiState = uiState.copy(chatDraft = value)
        persistComposerSoon()
        requestSlashCompletions(value)
    }

    private fun string(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    /**
     * Desktop-style `/` palette. The command itself is matched locally against `commands.catalog`
     * (loaded once per session), so the list appears on `/` and narrows instantly per keystroke;
     * arguments (`/personality al`) and gateways without the catalog use debounced `complete.slash`.
     */
    private fun requestSlashCompletions(draft: String) {
        slashCompletionJob?.cancel()
        val query = SlashCommands.query(draft)
        val repo = repository
        val chat = uiState.activeChat
        if (query == null || repo == null || chat == null) {
            if (uiState.slashQuery != null || uiState.slashCompletions.isNotEmpty()) {
                uiState = uiState.copy(slashCompletions = emptyList(), slashQuery = null, slashLoading = false)
            }
            return
        }
        val catalog = slashCatalog?.takeIf { it.first == chat.runtimeSessionId }?.second
        if (SlashCommands.commandStage(query) && catalog?.entries?.isNotEmpty() == true) {
            uiState = uiState.copy(slashCompletions = SlashCommands.filter(catalog, query), slashReplaceFrom = 1, slashQuery = query, slashLoading = false)
            return
        }
        if (SlashCommands.commandStage(query) && catalog == null) {
            uiState = uiState.copy(slashQuery = query, slashLoading = true)
            if (slashCatalogJob?.isActive == true) return
            slashCatalogJob = viewModelScope.launch {
                val loaded = runCatching { repo.commandsCatalog(chat.runtimeSessionId, chat.profile) }
                    .getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it else SlashCatalog(emptyList()) }
                if (repository !== repo || uiState.activeChat?.runtimeSessionId != chat.runtimeSessionId) return@launch
                slashCatalog = chat.runtimeSessionId to loaded
                uiState = uiState.copy(slashLoading = false)
                requestSlashCompletions(uiState.chatDraft)
            }
            return
        }
        // Command-stage rows would apply to the wrong span once the draft is in its arguments.
        val stale = uiState.slashQuery?.let(SlashCommands::commandStage) != false
        uiState = uiState.copy(slashQuery = query, slashCompletions = if (stale) emptyList() else uiState.slashCompletions)
        slashCompletionJob = viewModelScope.launch {
            delay(140)
            runCatching { repo.completeSlash(chat.runtimeSessionId, chat.profile, query) }
                .onSuccess { (items, replaceFrom) ->
                    if (uiState.chatDraft == draft && uiState.activeChat?.runtimeSessionId == chat.runtimeSessionId) {
                        uiState = uiState.copy(slashCompletions = items, slashReplaceFrom = replaceFrom, slashLoading = false)
                    }
                }
                .onFailure { if (it !is kotlinx.coroutines.CancellationException) uiState = uiState.copy(slashCompletions = emptyList(), slashLoading = false) }
        }
    }

    fun applySlashCompletion(item: SlashCompletion) {
        setChatDraft(SlashCommands.apply(uiState.chatDraft, item, uiState.slashReplaceFrom))
    }

    fun dismissCommandOutput() {
        uiState = uiState.copy(commandOutput = null)
    }

    fun addComposerAttachments(uris: List<Uri>) {
        val chat = uiState.activeChat ?: return
        val resolver = getApplication<Application>().contentResolver
        val existing = uiState.composerAttachments
        val room = (MAX_COMPOSER_ATTACHMENTS - existing.size).coerceAtLeast(0)
        if (room == 0) {
            uiState = uiState.copy(snackbar = string(R.string.i18n_vm_max_attachments, MAX_COMPOSER_ATTACHMENTS))
            return
        }
        val added = uris.take(room).mapNotNull { uri ->
            runCatching {
                runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                var name = uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"
                var size = 0L
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = cursor.getString(it) ?: name }
                        cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = cursor.getLong(it) }
                    }
                }
                if (size > MAX_ATTACHMENT_BYTES) {
                    throw IllegalArgumentException("$name is larger than 25 MB")
                }
                ComposerAttachment(
                    id = UUID.randomUUID().toString(),
                    uri = uri.toString(),
                    name = name,
                    mimeType = resolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" },
                    size = size
                )
            }.onFailure { uiState = uiState.copy(error = it.message) }.getOrNull()
        }.filterNot { candidate -> existing.any { it.uri == candidate.uri } }
        if (uiState.activeChat?.runtimeSessionId != chat.runtimeSessionId) return
        uiState = uiState.copy(composerAttachments = (existing + added).take(MAX_COMPOSER_ATTACHMENTS))
        persistComposerSoon(immediate = true)
    }

    /**
     * Desktop's large-paste policy: one insertion over [LARGE_PASTE_CHARS] becomes a `.txt` attachment instead of
     * flooding the composer. Returns true when it took the paste.
     */
    private fun pasteToAttachment(before: String, after: String): Boolean {
        val prefix = before.commonPrefixWith(after).length
        val suffix = before.substring(prefix).commonSuffixWith(after.substring(prefix)).length
        val inserted = after.substring(prefix, after.length - suffix)
        if (inserted.length <= LARGE_PASTE_CHARS || before.length - prefix - suffix > 0) return false
        if (uiState.composerAttachments.size >= MAX_COMPOSER_ATTACHMENTS) return false
        val dir = java.io.File(getApplication<Application>().filesDir, "composer-pastes").apply { mkdirs() }
        // Pastes live until sent; drop the ones left behind by drafts abandoned a week ago.
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 7L * 24 * 3600 * 1000 }?.forEach { it.delete() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.ROOT).format(java.util.Date())
        val file = java.io.File(dir, "pasted_content_${stamp}_${UUID.randomUUID().toString().take(6)}.txt")
        runCatching { file.writeText(inserted) }.onFailure { return false }
        uiState = uiState.copy(
            chatDraft = after.substring(0, prefix) + after.substring(after.length - suffix),
            composerAttachments = uiState.composerAttachments + ComposerAttachment(
                id = UUID.randomUUID().toString(),
                uri = Uri.fromFile(file).toString(),
                name = file.name,
                mimeType = "text/plain",
                size = file.length()
            )
        )
        persistComposerSoon(immediate = true)
        return true
    }

    fun removeComposerAttachment(id: String) {
        uiState = uiState.copy(composerAttachments = uiState.composerAttachments.filterNot { it.id == id })
        persistComposerSoon(immediate = true)
    }

    fun removeQueuedPrompt(id: String) {
        uiState = uiState.copy(queuedPrompts = uiState.queuedPrompts.filterNot { it.id == id })
        persistComposerSoon(immediate = true)
    }

    fun updateQueuedPrompt(id: String, text: String) {
        uiState = uiState.copy(
            queuedPrompts = uiState.queuedPrompts.map { if (it.id == id) it.copy(text = text) else it }
        )
        persistComposerSoon(immediate = true)
    }

    fun moveQueuedPrompt(id: String, delta: Int) {
        val rows = uiState.queuedPrompts.toMutableList()
        val from = rows.indexOfFirst { it.id == id }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, rows.lastIndex)
        if (to == from) return
        val item = rows.removeAt(from)
        rows.add(to, item)
        uiState = uiState.copy(queuedPrompts = rows)
        persistComposerSoon(immediate = true)
    }

    fun clearQueuedPrompts() {
        uiState = uiState.copy(queuedPrompts = emptyList())
        persistComposerSoon(immediate = true)
    }

    fun refreshSessionMeta() {
        val chat = uiState.activeChat ?: return
        val repo = repository ?: return
        val runtimeId = chat.runtimeSessionId
        viewModelScope.launch {
            uiState = uiState.copy(modelOptionsLoading = true)
            val choices = async { runCatching { repo.sessionModelOptions(chat.profile) }.getOrDefault(emptyList()) }
            val usage = async { runCatching { repo.sessionUsage(runtimeId, chat.profile) }.getOrNull() }
            // Mid-turn the live session.usage ticks carry the ring; an estimate would only lag behind them.
            val breakdown = async {
                if (chat.running) null else runCatching { repo.contextBreakdown(runtimeId, chat.profile) }.getOrNull()
            }
            val resolvedChoices = choices.await()
            val resolvedBreakdown = breakdown.await()
            val resolvedUsage = (usage.await() ?: resolvedBreakdown?.let { SessionUsage() })?.withContextFrom(resolvedBreakdown)
            if (uiState.activeChat?.runtimeSessionId == runtimeId && repository === repo) {
                val previous = uiState.sessionUsage
                uiState = uiState.copy(
                    sessionModelChoices = resolvedChoices,
                    sessionUsage = if (resolvedUsage != null && resolvedUsage.contextLimit == 0L && previous != null && previous.contextLimit > 0L) {
                        resolvedUsage.copy(contextUsed = previous.contextUsed, contextLimit = previous.contextLimit, contextEstimated = previous.contextEstimated)
                    } else resolvedUsage ?: previous,
                    modelOptionsLoading = false
                )
            }
        }
    }

    fun switchSessionModel(choice: SessionModelChoice, reasoning: String = uiState.activeChat?.reasoningEffort.orEmpty()) {
        val chat = uiState.activeChat ?: return
        val repo = repository ?: return
        if (chat.running) {
            uiState = uiState.copy(snackbar = string(R.string.i18n_vm_stop_before_model))
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            runCatching { repo.switchSessionModel(chat.runtimeSessionId, chat.profile, choice.provider, choice.model, reasoning) }
                .onSuccess { result ->
                    val current = uiState.activeChat
                    if (current?.runtimeSessionId != chat.runtimeSessionId) return@onSuccess
                    if (result.confirmRequired) {
                        uiState = uiState.copy(
                            loading = false,
                            pendingSessionModelChange = PendingSessionModelChange(
                                chat.runtimeSessionId, chat.profile, choice.provider, choice.model, reasoning,
                                result.confirmMessage.ifBlank { "Hermes requires confirmation before using this model." }
                            )
                        )
                    } else {
                        sessionModelPins[chat.runtimeSessionId] = SessionModelPin(
                            chat.profile, choice.provider, choice.model, reasoning, ensureOnNextPrompt = true
                        )
                        uiState = uiState.copy(
                            loading = false,
                            activeChat = current.copy(provider = choice.provider, model = choice.model, reasoningEffort = reasoning),
                            snackbar = string(R.string.i18n_vm_model_switched)
                        )
                        refreshSessionMeta()
                    }
                }
                .onFailure { uiState = uiState.copy(loading = false, error = it.message) }
        }
    }

    fun confirmSessionModelChange() {
        val pending = uiState.pendingSessionModelChange ?: return
        val repo = repository ?: return
        val chat = uiState.activeChat ?: return
        if (chat.runtimeSessionId != pending.runtimeSessionId) {
            uiState = uiState.copy(pendingSessionModelChange = null)
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, pendingSessionModelChange = null, error = null)
            runCatching {
                repo.switchSessionModel(
                    pending.runtimeSessionId, pending.profile, pending.provider, pending.model, pending.reasoning,
                    confirmExpensive = true
                )
            }.onSuccess {
                val current = uiState.activeChat
                if (current?.runtimeSessionId == pending.runtimeSessionId) {
                    sessionModelPins[pending.runtimeSessionId] = SessionModelPin(
                        pending.profile, pending.provider, pending.model, pending.reasoning, ensureOnNextPrompt = true
                    )
                    uiState = uiState.copy(
                        loading = false,
                        activeChat = current.copy(
                            provider = pending.provider, model = pending.model, reasoningEffort = pending.reasoning
                        ),
                        snackbar = string(R.string.i18n_vm_model_switched)
                    )
                    refreshSessionMeta()
                }
            }.onFailure { uiState = uiState.copy(loading = false, error = it.message) }
        }
    }

    fun cancelSessionModelChange() { uiState = uiState.copy(pendingSessionModelChange = null) }

    /**
     * The voice assistant's model for the open voice chat, session-scoped and silent (no snackbar). A blank [model]
     * keeps the profile's model and only sets [reasoning]; a blank [reasoning] leaves it as it is.
     * Returns false when nothing could be applied (no chat, a turn running, or the server refused).
     */
    suspend fun applyVoiceModel(provider: String, model: String, reasoning: String): Boolean {
        val chat = uiState.activeChat ?: return false
        val repo = repository ?: return false
        if (chat.running) return false
        val runtime = chat.runtimeSessionId
        if (model.isNotBlank() && (chat.model != model || chat.provider != provider || chat.reasoningEffort != reasoning)) {
            val result = runCatching { repo.switchSessionModel(runtime, chat.profile, provider, model, reasoning) }
                .onFailure { android.util.Log.w("HermesAssist", "voice model switch failed: ${it.message}") }.getOrNull() ?: return false
            if (result.confirmRequired) return false
            sessionModelPins[runtime] = SessionModelPin(chat.profile, provider, model, reasoning, ensureOnNextPrompt = true)
            uiState.activeChat?.takeIf { it.runtimeSessionId == runtime }?.let {
                uiState = uiState.copy(activeChat = it.copy(provider = provider, model = model, reasoningEffort = reasoning))
            }
        } else if (model.isBlank() && reasoning.isNotBlank() && chat.reasoningEffort != reasoning) {
            runCatching { repo.setSessionReasoning(runtime, chat.profile, reasoning) }.getOrNull() ?: return false
            sessionModelPins[runtime]?.let { sessionModelPins[runtime] = it.copy(reasoning = reasoning) }
            uiState.activeChat?.takeIf { it.runtimeSessionId == runtime }?.let {
                uiState = uiState.copy(activeChat = it.copy(reasoningEffort = reasoning))
            }
        }
        return true
    }

    fun setSessionReasoning(level: String) {
        val chat = uiState.activeChat ?: return
        val repo = repository ?: return
        if (chat.running) return
        viewModelScope.launch {
            runCatching { repo.setSessionReasoning(chat.runtimeSessionId, chat.profile, level) }
                .onSuccess {
                    val current = uiState.activeChat
                    if (current?.runtimeSessionId == chat.runtimeSessionId) {
                        sessionModelPins[chat.runtimeSessionId]?.let { pin ->
                            sessionModelPins[chat.runtimeSessionId] = pin.copy(reasoning = level)
                        }
                        uiState = uiState.copy(activeChat = current.copy(reasoningEffort = level), snackbar = string(R.string.i18n_vm_reasoning, level))
                    }
                }
                .onFailure { uiState = uiState.copy(error = it.message) }
        }
    }

    private fun composerKey(chat: ActiveChat): String = buildString {
        append(uiState.activeConnectionId ?: uiState.savedConfig?.id ?: "gateway")
        append(':').append(chat.profile)
        append(':').append(chat.storedSessionId)
    }

    private fun loadChatEnhancements(chat: ActiveChat, draft: String = "") {
        val key = composerKey(chat)
        val runtime = chat.runtimeSessionId
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { composerStore.load(key) }
            if (uiState.activeChat?.runtimeSessionId != runtime) return@launch
            uiState = uiState.copy(
                chatDraft = draft.ifBlank { saved.draft },
                composerAttachments = saved.attachments,
                queuedPrompts = saved.queue,
                reasoningText = "",
                liveStatuses = emptyList(),
                artifactSuggestions = emptyList()
            )
            if (draft.isNotBlank()) persistComposerSoon()
            refreshSessionMeta()
        }
    }

    private fun persistComposerSoon(immediate: Boolean = false) {
        val chat = uiState.activeChat ?: return
        val key = composerKey(chat)
        val snapshot = ChatComposerStore.State(uiState.chatDraft, uiState.composerAttachments, uiState.queuedPrompts)
        composerSaveJob?.cancel()
        composerSaveJob = viewModelScope.launch {
            if (!immediate) delay(350)
            withContext(Dispatchers.IO) { composerStore.save(key, snapshot) }
        }
    }

    /** Navigation/gateway switches cancel ViewModel child jobs, so flush the latest composer state first. */
    private fun persistCurrentComposerNow() {
        val chat = uiState.activeChat ?: return
        val key = composerKey(chat)
        val snapshot = ChatComposerStore.State(uiState.chatDraft, uiState.composerAttachments, uiState.queuedPrompts)
        composerSaveJob?.cancel()
        composerSaveJob = null
        runCatching { composerStore.save(key, snapshot) }
    }

    fun setIncludeArchivedSessions(enabled: Boolean) {
        if (uiState.includeArchivedSessions == enabled) return
        uiState = uiState.copy(includeArchivedSessions = enabled)
        refreshSessions()
    }

    fun refreshAll(background: Boolean = false) {
        val repo = repository ?: return
        val generation = ++refreshGeneration
        viewModelScope.launch {
            uiState = uiState.copy(refreshing = true, loading = if (background) uiState.loading else true, error = null)
            runCatching {
                val profiles = runCatching { repo.rpcProfiles() }.getOrElse { repo.rest.profiles() }
                val selected = uiState.selectedProfile.takeIf { current -> profiles.any { it.name == current } }
                    ?: profiles.firstOrNull()?.name
                    ?: "default"

                coroutineScope {
                    val sessions = async { repo.rest.sessionsPage("all", limit = SESSION_PAGE_SIZE, offset = 0, includeArchived = uiState.includeArchivedSessions) }
                    val cron = async { repo.rest.cronJobs("all") }
                    val cronTargets = async { runCatching { repo.rest.cronDeliveryTargets(selected) }.getOrDefault(listOf(CronDeliveryTarget("local", "Local"))) }
                    val cronBlueprints = async { runCatching { repo.rest.cronBlueprints(selected) }.getOrDefault(emptyList()) }
                    val messaging = async { repo.rest.messagingPlatforms(selected) }
                    val pairing = async { repo.rest.pairing(selected) }
                    val webhooks = async { runCatching { repo.rest.webhooks(selected) }.getOrDefault(WebhookState()) }

                    InitialSnapshot(
                        profiles = profiles,
                        selectedProfile = selected,
                        sessions = sessions.await(),
                        cron = cron.await(),
                        cronTargets = cronTargets.await(),
                        cronBlueprints = cronBlueprints.await(),
                        messaging = messaging.await(),
                        pairing = pairing.await(),
                        webhooks = webhooks.await()
                    )
                }
            }.onSuccess { snapshot ->
                if (generation != refreshGeneration || repository !== repo) return@onSuccess
                if (snapshot.selectedProfile != uiState.savedConfig?.selectedProfile) {
                    uiState.savedConfig?.copy(selectedProfile = snapshot.selectedProfile)?.let {
                        persistActiveConfig(it, makeActive = true)
                    }
                }
                uiState = uiState.copy(
                    profiles = snapshot.profiles,
                    sessions = snapshot.sessions.sessions,
                    sessionsHasMore = snapshot.sessions.hasMore,
                    cronJobs = snapshot.cron,
                    cronDeliveryTargets = snapshot.cronTargets,
                    cronBlueprints = snapshot.cronBlueprints,
                    selectedProfile = snapshot.selectedProfile,
                    capabilityProfile = uiState.capabilityProfile.takeIf { p -> snapshot.profiles.any { it.name == p } } ?: snapshot.selectedProfile,
                    messaging = snapshot.messaging,
                    pendingPairing = snapshot.pairing.first,
                    approvedPairing = snapshot.pairing.second,
                    webhooks = snapshot.webhooks,
                    loading = false,
                    refreshing = false,
                    error = null
                )
            }.onFailure {
                if (generation == refreshGeneration) {
                    uiState = uiState.copy(loading = false, refreshing = false, error = it.message)
                }
            }
        }
    }

    private data class InitialSnapshot(
        val profiles: List<HermesProfile>,
        val selectedProfile: String,
        val sessions: SessionListPage,
        val cron: List<CronJob>,
        val cronTargets: List<CronDeliveryTarget>,
        val cronBlueprints: List<AutomationBlueprint>,
        val messaging: List<MessagingPlatform>,
        val pairing: Pair<List<PairingUser>, List<PairingUser>>,
        val webhooks: WebhookState
    )

    private fun refreshProfiles(then: () -> Unit = {}) {
        val repo = repository ?: return
        viewModelScope.launch {
            runCatching { repo.rpcProfiles() }.recoverCatching { repo.rest.profiles() }
                .onSuccess { profiles ->
                    if (repository !== repo) return@onSuccess
                    val selected = uiState.selectedProfile.takeIf { current -> profiles.any { it.name == current } }
                        ?: profiles.firstOrNull()?.name ?: "default"
                    profiles.filterNot { it.hasAvatar }.forEach {
                        dropAvatarCache(it.name, markMissing = true)
                    }
                    uiState = uiState.copy(profiles = profiles, selectedProfile = selected)
                    then()
                }
        }
    }

    private var botActivityJob: Job? = null

    /** A turn ended while Bots is on screen: its rows' last message and order follow, batched over a burst. */
    private fun refreshBotActivitySoon() {
        botActivityJob?.cancel()
        botActivityJob = viewModelScope.launch {
            delay(1_500)
            if (uiState.section == MainSection.BOTS && uiState.activeChat == null) refreshProfiles(then = ::refreshBotRoster)
        }
    }



    private fun clearAvatarCache() {
        avatarAssets.clear()
        avatarMissing.clear()
        avatarCacheOrder.clear()
    }

    private fun dropAvatarCache(name: String, markMissing: Boolean = false) {
        avatarAssets.remove(name)
        avatarCacheOrder.remove(name)
        if (markMissing) avatarMissing.add(name) else avatarMissing.remove(name)
    }

    private fun cacheAvatar(name: String, data: String) {
        avatarMissing.remove(name)
        avatarAssets[name] = data
        avatarCacheOrder.remove(name)
        avatarCacheOrder.addLast(name)
        while (avatarCacheOrder.size > MAX_AVATAR_CACHE_ITEMS) {
            val evicted = avatarCacheOrder.removeFirst()
            avatarAssets.remove(evicted)
        }
    }

    fun ensureAvatar(profile: HermesProfile, force: Boolean = false) {
        val repo = repository ?: return
        if (!profile.hasAvatar) {
            dropAvatarCache(profile.name, markMissing = true)
            return
        }
        if (!force && (avatarAssets.containsKey(profile.name) || profile.name in avatarMissing)) return
        if (!avatarLoading.add(profile.name)) return
        viewModelScope.launch {
            runCatching { repo.getBotAvatar(profile.name) }
                .onSuccess { data ->
                    if (data.isNullOrBlank()) {
                        dropAvatarCache(profile.name, markMissing = true)
                    } else {
                        cacheAvatar(profile.name, data)
                    }
                }
            avatarLoading.remove(profile.name)
        }
    }

    fun refreshBotRoster() {
        val activeConfig = uiState.savedConfig ?: return
        val activeId = activeConfig.id
        val connections = uiState.connections
        val localProfiles = uiState.profiles
        val currentInstall = uiState.status?.installId.orEmpty()
        val localLink = uiState.groupCapabilities?.roomLink?.enabled == true
        val immediate = localProfiles.filter { it.isBot }.map {
            RoutedBot(activeId, activeConfig.displayLabel, currentInstall, it, reachable = true, activeConnection = true, roomLinkEnabled = localLink)
        }
        uiState = uiState.copy(botRoster = immediate, botRosterLoading = true)
        val generation = connectionGeneration
        viewModelScope.launch {
            val remoteRows = coroutineScope {
                connections.filter { it.id != activeId && it.baseUrl.isNotBlank() && it.hasCredential }.map { cfg ->
                    async {
                        runCatching {
                            val rest = HermesRestClient(cfg, onConfigUpdated = { updated -> store.save(updated, makeActive = false) })
                            val status = rest.status()
                            rest.profiles().filter { it.isBot }.map { profile ->
                                RoutedBot(cfg.id, cfg.displayLabel, status.installId, profile, reachable = true, activeConnection = false)
                            }
                        }.getOrElse { emptyList() }
                    }
                }.map { it.await() }.flatten()
            }
            if (generation == connectionGeneration && uiState.activeConnectionId == activeId) {
                uiState = uiState.copy(
                    botRoster = (immediate + remoteRows).distinctBy { it.routeKey }
                        .sortedWith(botRosterOrder),
                    botRosterLoading = false
                )
            }
        }
    }

    fun createManagedProfile(name: String, description: String, cloneFrom: String?) {
        val repo = repository ?: return
        val clean = name.trim().lowercase()
        if (!Regex("^[a-z0-9][a-z0-9_-]{0,63}$").matches(clean)) {
            uiState = uiState.copy(error = string(R.string.i18n_vm_profile_name_rule))
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(profileActionBusy = true, error = null)
            runCatching { repo.rest.createProfile(clean, description.trim(), cloneFrom = cloneFrom) }
                .onSuccess { uiState = uiState.copy(profileActionBusy = false, snackbar = string(R.string.i18n_vm_profile_created)); refreshProfiles() }
                .onFailure { uiState = uiState.copy(profileActionBusy = false, error = it.message) }
        }
    }

    fun renameManagedProfile(profile: HermesProfile, newName: String) {
        val repo = repository ?: return
        if (profile.isDefault) { uiState = uiState.copy(error = string(R.string.i18n_vm_default_rename)); return }
        val clean = newName.trim().lowercase()
        if (!Regex("^[a-z0-9][a-z0-9_-]{0,63}$").matches(clean)) return
        viewModelScope.launch {
            uiState = uiState.copy(profileActionBusy = true, error = null)
            runCatching { repo.rest.renameProfile(profile.name, clean) }
                .onSuccess { uiState = uiState.copy(profileActionBusy = false, snackbar = string(R.string.i18n_vm_profile_renamed)); refreshProfiles(); refreshAll(background = true) }
                .onFailure { uiState = uiState.copy(profileActionBusy = false, error = it.message) }
        }
    }

    fun deleteManagedProfile(profile: HermesProfile) {
        val repo = repository ?: return
        if (profile.isDefault) return
        viewModelScope.launch {
            uiState = uiState.copy(profileActionBusy = true, error = null)
            runCatching { repo.rest.deleteProfile(profile.name) }
                .onSuccess { uiState = uiState.copy(profileActionBusy = false, snackbar = string(R.string.i18n_vm_profile_deleted)); refreshProfiles(); refreshAll(background = true) }
                .onFailure { uiState = uiState.copy(profileActionBusy = false, error = it.message) }
        }
    }

    fun exportManagedProfile(profile: HermesProfile) {
        val repo = repository ?: return
        viewModelScope.launch {
            uiState = uiState.copy(profileActionBusy = true, error = null)
            runCatching { repo.rest.exportProfile(profile.name) }
                .onSuccess { result ->
                    val archive = result.optString("archive")
                    uiState = uiState.copy(profileActionBusy = false, lastProfileExport = archive, snackbar = if (archive.isBlank()) string(R.string.i18n_vm_export_done) else string(R.string.i18n_vm_exported_to, archive))
                }
                .onFailure { uiState = uiState.copy(profileActionBusy = false, error = it.message) }
        }
    }

    fun importManagedProfile(archive: String, name: String?) {
        val repo = repository ?: return
        if (archive.isBlank()) return
        viewModelScope.launch {
            uiState = uiState.copy(profileActionBusy = true, error = null)
            runCatching { repo.rest.importProfile(archive.trim(), name?.trim()?.takeIf { it.isNotBlank() }) }
                .onSuccess { result -> uiState = uiState.copy(profileActionBusy = false, snackbar = string(R.string.i18n_vm_imported, result.optString("name").ifBlank { "profile" })); refreshProfiles(); refreshAll(background = true) }
                .onFailure { uiState = uiState.copy(profileActionBusy = false, error = it.message) }
        }
    }

    fun refreshGroups() {
        val repo = repository ?: return
        if (uiState.groupsLoading) return
        uiState = uiState.copy(groupsLoading = true, groupError = null)
        viewModelScope.launch {
            runCatching {
                val caps = repo.groupCapabilities()
                if ("groups.list" !in caps.methods) error("This Hermes gateway does not expose hosted Group Chats")
                caps to repo.groupRooms()
            }.onSuccess { (caps, rooms) ->
                uiState = uiState.copy(
                    groupCapabilities = caps,
                    groupRooms = rooms.filter { it.active }.sortedByDescending { it.updatedAt },
                    groupsLoading = false,
                    groupError = null
                )
                refreshBotRoster()
            }.onFailure {
                uiState = uiState.copy(groupsLoading = false, groupError = it.message)
            }
        }
    }

    fun createGroup(name: String, memberRouteKeys: Set<String>) {
        val repo = repository ?: return
        val members = uiState.botRoster.filter { it.routeKey in memberRouteKeys }
        if (members.size !in 2..6) {
            uiState = uiState.copy(error = string(R.string.i18n_vm_group_size))
            return
        }
        val roomName = name.trim()
        if (roomName.isBlank()) return
        viewModelScope.launch {
            uiState = uiState.copy(groupActionBusy = true, error = null)
            runCatching {
                if (members.all { it.activeConnection }) {
                    // Preserve the alpha4 same-gateway wire shape for older Hermes gateways:
                    // pre-RoomLink strict schemas do not know the member.target field.
                    repo.createGroupRoom(roomName, members.map { it.profile })
                } else {
                    repo.createRoutedGroupRoom(roomName, members, uiState.connections) { updated ->
                        store.save(updated, makeActive = false)
                    }
                }
            }
                .onSuccess { room ->
                    uiState = uiState.copy(
                        groupActionBusy = false,
                        groupRooms = (listOf(room) + uiState.groupRooms).distinctBy { it.roomId },
                        snackbar = string(R.string.i18n_vm_group_created)
                    )
                    openGroup(room)
                }
                .onFailure { uiState = uiState.copy(groupActionBusy = false, error = it.message) }
        }
    }

    fun openGroup(room: GroupRoom) {
        val repo = repository ?: return
        val generation = ++groupGeneration
        groupPollingJob?.cancel()
        viewModelScope.launch {
            uiState = uiState.copy(groupsLoading = true, groupError = null)
            runCatching { repo.openGroupRoom(room) }
                .onSuccess { active ->
                    if (generation != groupGeneration) return@onSuccess
                    uiState = uiState.copy(activeGroup = active, groupsLoading = false, groupActionBusy = false)
                    startGroupPolling(active.room.roomId, generation)
                }
                .onFailure {
                    if (generation == groupGeneration) uiState = uiState.copy(groupsLoading = false, groupError = it.message)
                }
        }
    }

    fun closeGroup() {
        groupGeneration += 1
        groupPollingJob?.cancel()
        groupPollingJob = null
        uiState = uiState.copy(activeGroup = null, groupActionBusy = false)
        refreshGroups()
    }

    private fun startGroupPolling(roomId: String, generation: Long) {
        val repo = repository ?: return
        groupPollingJob?.cancel()
        groupPollingJob = viewModelScope.launch {
            while (appForeground && networkOnline && generation == groupGeneration && uiState.activeGroup?.room?.roomId == roomId) {
                val current = uiState.activeGroup ?: break
                val delayMs = if (current.driver.working || current.driver.pendingActions.isNotEmpty()) 900L else 2_500L
                delay(delayMs)
                if (generation != groupGeneration || uiState.activeGroup?.room?.roomId != roomId) break
                runCatching { repo.pollGroupRoom(current) }
                    .onSuccess { updated ->
                        val latest = uiState.activeGroup
                        if (generation == groupGeneration && latest?.room?.roomId == roomId) {
                            // Never let a slow poll move the monotonic cursor backwards.
                            uiState = uiState.copy(activeGroup = updated.copy(
                                cursor = maxOf(latest.cursor, updated.cursor),
                                latestSeq = maxOf(latest.latestSeq, updated.latestSeq),
                                events = (latest.events + updated.events)
                                    .associateBy { it.seq }.values.sortedBy { it.seq }.takeLast(1200)
                            ))
                        }
                    }
            }
        }
    }

    fun sendGroupMessage(text: String) {
        val repo = repository ?: return
        val active = uiState.activeGroup ?: return
        val value = text.trim()
        if (value.isBlank() || uiState.groupActionBusy) return
        val threadId = active.replyThreadId
        viewModelScope.launch {
            uiState = uiState.copy(groupActionBusy = true, error = null)
            runCatching { repo.sendGroupMessage(active.room.roomId, value, threadId) }
                .onSuccess { event ->
                    val current = uiState.activeGroup
                    if (current?.room?.roomId == active.room.roomId) {
                        uiState = uiState.copy(
                            activeGroup = current.copy(
                                events = (current.events + event).associateBy { it.seq }.values.sortedBy { it.seq },
                                cursor = maxOf(current.cursor, event.seq),
                                latestSeq = maxOf(current.latestSeq, event.seq),
                                replyThreadId = null
                            ),
                            groupActionBusy = false
                        )
                    }
                }
                .onFailure { uiState = uiState.copy(groupActionBusy = false, error = it.message) }
        }
    }

    fun setGroupReplyThread(threadId: String?) {
        val active = uiState.activeGroup ?: return
        uiState = uiState.copy(activeGroup = active.copy(replyThreadId = threadId?.takeIf { it.isNotBlank() }))
    }

    fun renameGroup(name: String) {
        val repo = repository ?: return
        val active = uiState.activeGroup ?: return
        val value = name.trim()
        if (value.isBlank()) return
        viewModelScope.launch {
            uiState = uiState.copy(groupActionBusy = true)
            runCatching { repo.renameGroup(active.room.roomId, value) }
                .onSuccess { room ->
                    val current = uiState.activeGroup
                    uiState = uiState.copy(
                        activeGroup = current?.takeIf { it.room.roomId == room.roomId }?.copy(room = room),
                        groupRooms = uiState.groupRooms.map { if (it.roomId == room.roomId) room else it },
                        groupActionBusy = false,
                        snackbar = string(R.string.i18n_vm_group_renamed)
                    )
                }
                .onFailure { uiState = uiState.copy(groupActionBusy = false, error = it.message) }
        }
    }

    fun stopGroup() {
        val repo = repository ?: return
        val active = uiState.activeGroup ?: return
        viewModelScope.launch {
            uiState = uiState.copy(groupActionBusy = true)
            runCatching { repo.stopGroup(active.room.roomId) }
                .onSuccess { uiState = uiState.copy(groupActionBusy = false, snackbar = string(R.string.i18n_vm_group_stopped)) }
                .onFailure { uiState = uiState.copy(groupActionBusy = false, error = it.message) }
        }
    }

    fun retryGroupAction(action: GroupPendingAction) {
        val repo = repository ?: return
        val active = uiState.activeGroup ?: return
        if (action.taskId.isBlank()) return
        viewModelScope.launch {
            uiState = uiState.copy(groupActionBusy = true)
            runCatching { repo.retryGroupTask(active.room.roomId, action.taskId) }
                .onSuccess { uiState = uiState.copy(groupActionBusy = false, snackbar = string(R.string.i18n_vm_retry_requested)) }
                .onFailure { uiState = uiState.copy(groupActionBusy = false, error = it.message) }
        }
    }

    fun approveGroupAction(action: GroupPendingAction, choice: String) {
        val repo = repository ?: return
        val active = uiState.activeGroup ?: return
        viewModelScope.launch {
            uiState = uiState.copy(groupActionBusy = true)
            runCatching { repo.approveGroupTask(active.room.roomId, action, choice) }
                .onSuccess { uiState = uiState.copy(groupActionBusy = false, snackbar = string(if (choice == "once") R.string.i18n_vm_approved_once else R.string.i18n_vm_denied)) }
                .onFailure { uiState = uiState.copy(groupActionBusy = false, error = it.message) }
        }
    }

    fun disbandGroup() {
        val repo = repository ?: return
        val active = uiState.activeGroup ?: return
        viewModelScope.launch {
            uiState = uiState.copy(groupActionBusy = true)
            runCatching { repo.disbandGroup(active.room.roomId) }
                .onSuccess {
                    groupGeneration += 1
                    groupPollingJob?.cancel()
                    uiState = uiState.copy(
                        activeGroup = null,
                        groupRooms = uiState.groupRooms.filterNot { it.roomId == active.room.roomId },
                        groupActionBusy = false,
                        snackbar = string(R.string.i18n_vm_group_deleted)
                    )
                }
                .onFailure { uiState = uiState.copy(groupActionBusy = false, error = it.message) }
        }
    }


    fun refreshSessions() {
        val repo = repository ?: return
        viewModelScope.launch {
            runCatching { repo.rest.sessionsPage("all", limit = SESSION_PAGE_SIZE, offset = 0, includeArchived = uiState.includeArchivedSessions) }
                .onSuccess { page ->
                    if (repository !== repo) return@onSuccess
                    uiState = uiState.copy(
                        sessions = page.sessions,
                        sessionsHasMore = page.hasMore,
                        sessionsLoadingMore = false
                    )
                }
                .onFailure { if (repository === repo) uiState = uiState.copy(sessionsLoadingMore = false, error = it.message) }
        }
    }

    fun loadMoreSessions() {
        val repo = repository ?: return
        if (!uiState.sessionsHasMore || uiState.sessionsLoadingMore || uiState.sessionSearchQuery.isNotBlank()) return
        val offset = uiState.sessions.size
        uiState = uiState.copy(sessionsLoadingMore = true)
        viewModelScope.launch {
            runCatching { repo.rest.sessionsPage("all", limit = SESSION_PAGE_SIZE, offset = offset, includeArchived = uiState.includeArchivedSessions) }
                .onSuccess { page ->
                    if (repository !== repo) return@onSuccess
                    val merged = (uiState.sessions + page.sessions).distinctBy { "${it.profile}:${it.id}" }
                    uiState = uiState.copy(
                        sessions = merged,
                        sessionsHasMore = page.hasMore,
                        sessionsLoadingMore = false
                    )
                }
                .onFailure { if (repository === repo) uiState = uiState.copy(sessionsLoadingMore = false, error = it.message) }
        }
    }

    fun searchSessions(query: String) {
        val trimmed = query.trim()
        uiState = uiState.copy(sessionSearchQuery = query)
        sessionSearchJob?.cancel()
        val repo = repository ?: return
        val generation = ++searchGeneration
        if (trimmed.isBlank()) {
            uiState = uiState.copy(sessionSearchResults = emptyList(), sessionSearchLoading = false)
            return
        }

        uiState = uiState.copy(sessionSearchLoading = true)
        sessionSearchJob = viewModelScope.launch {
            delay(250)
            val profile = uiState.sessionProfileFilter
            runCatching {
                if (profile == "all") {
                    coroutineScope {
                        uiState.profiles.map { candidate ->
                            async { runCatching { repo.rest.searchSessions(trimmed, candidate.name) }.getOrDefault(emptyList()) }
                        }.map { it.await() }.flatten()
                            .distinctBy { "${it.profile}:${it.id}" }
                            .sortedByDescending { it.lastActive }
                    }
                } else {
                    repo.rest.searchSessions(trimmed, profile)
                }
            }.onSuccess { results ->
                    if (generation == searchGeneration && uiState.sessionSearchQuery.trim() == trimmed) {
                        val enriched = results.map { hit ->
                            uiState.sessions.firstOrNull { sameSession(it, hit) }?.let { loaded ->
                                loaded.copy(preview = hit.preview.ifBlank { loaded.preview })
                            } ?: hit
                        }
                        uiState = uiState.copy(sessionSearchResults = enriched, sessionSearchLoading = false)
                    }
                }
                .onFailure {
                    if (generation == searchGeneration) {
                        uiState = uiState.copy(sessionSearchLoading = false, error = it.message)
                    }
                }
        }
    }

    /**
     * Where a chat started from the side menu, Home or the button goes: the gateway's default profile. Not
     * [UiState.selectedProfile], which follows the last chat or bot opened, so a new chat would land in that bot.
     * An empty chat can still move to another profile from its intro.
     */
    private fun mainProfile(): String = uiState.profiles.firstOrNull { it.isDefault }?.name ?: "default"

    fun newChat(profile: String = mainProfile(), title: String = "") = newWorkspaceChat(profile, title, null)

    /** Opens a fresh chat with [prompt] waiting in the composer (the user reviews and sends it). */
    fun askHermes(prompt: String, profile: String = mainProfile()) = newWorkspaceChat(profile, "", null, draft = prompt)

    /**
     * Runs a plugin slash command over the authenticated chat connection (`command.dispatch`), whose
     * output never enters the model's history. A session of [profile] binds it to that profile;
     * without one the gateway answers for its launch profile, which callers must verify.
     */
    suspend fun pluginCommand(profile: String, name: String, arg: String = ""): String {
        val repo = repository ?: throw IllegalStateException(string(R.string.a23_not_connected))
        if (pluginSessionsRepo !== repo) { pluginSessions.clear(); pluginSessionsRepo = repo }
        val active = uiState.activeChat?.takeIf { it.profile == profile }?.runtimeSessionId
        val runtime = active ?: pluginSessions[profile] ?: profileRuntimeSession(repo, profile)
        return try {
            repo.dispatchCommand(runtime.orEmpty(), profile, name, arg).output
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            // A cached runtime session may have been reaped by the gateway: bind a fresh one once.
            if (active != null || runtime == null) throw e
            pluginSessions.remove(profile)
            repo.dispatchCommand(profileRuntimeSession(repo, profile).orEmpty(), profile, name, arg).output
        }
    }

    private val PLUGIN_COMMAND_SURFACES = setOf("", "mobile", "desktop", "tui", "cli", "api_server", "local")
    private val pluginSessions = mutableMapOf<String, String>()
    private var pluginSessionsRepo: HermesRepository? = null

    /**
     * A runtime session bound to [profile], so profile-scoped plugin commands answer for it without the user
     * opening a chat there: resumes that profile's most recent conversation (no new chat is created).
     */
    private suspend fun profileRuntimeSession(repo: HermesRepository, profile: String): String? {
        // Only an app/Desktop/terminal conversation: plugins refuse secret-bearing commands in messaging
        // channels (Telegram…, cron deliveries), where the reply would be posted to a chat.
        fun usable(source: String) = source.trim().lowercase() in PLUGIN_COMMAND_SURFACES
        val stored = uiState.sessions.firstOrNull { it.profile == profile && usable(it.source) }
            ?: runCatching { repo.rest.sessionsPage(profile, limit = 20, offset = 0, includeArchived = true).sessions.firstOrNull { usable(it.source) } }.getOrNull()
            ?: return null
        val runtime = runCatching { repo.resumeSession(stored.id, profile, stored.title, stored.source).runtimeSessionId }.getOrNull()
        if (runtime != null) pluginSessions[profile] = runtime
        return runtime
    }

    fun newWorkspaceChat(profile: String, title: String, cwd: String?, draft: String = "") {
        val repo = repository ?: return
        rememberChatOrigin()
        persistCurrentComposerNow()
        val generation = ++navigationGeneration
        resetStream()
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            runCatching { repo.createSession(profile, title.takeIf { it.isNotBlank() }, cwd = cwd) }
                .onSuccess { chat ->
                    if (generation == navigationGeneration) {
                        resetStream(chat.streamingText)
                        uiState = uiState.copy(
                            // Switch only now: switching first showed the Chats list while the chat loaded.
                            section = MainSection.CHAT,
                            activeChat = chat.copy(streamingText = ""),
                            sessionUsage = null,
                            loading = false,
                            tools = emptyList(),
                            selectedProfile = profile,
                            loadingOlderMessages = false
                        )
                        // The prefilled request wins over the (empty) saved composer of the brand-new chat.
                        loadChatEnhancements(chat, draft)
                    }
                }
                .onFailure {
                    if (generation == navigationGeneration) uiState = uiState.copy(loading = false, error = it.message)
                }
        }
    }

    var notificationInboxRequested by mutableStateOf(false)

    fun openNotification(event: JSONObject) {
        val connection = event.optString("connection")
        val profile = event.optString("profile")
        val sid = event.optString("session_id").takeUnless { it.isBlank() || it == "null" }
        if (uiState.connections.none { it.id == connection }) return
        if (uiState.activeConnectionId != connection) switchConnection(connection)
        viewModelScope.launch {
            repeat(60) {
                if (uiState.activeConnectionId != connection) return@launch
                if (uiState.connectionState == GatewayConnectionState.CONNECTED && uiState.profiles.any { it.name == profile }) {
                    if (sid != null) openSession(HermesSession(id = sid, profile = profile, title = "Hermes"))
                    else { selectProfile(profile); notificationInboxRequested = true }
                    return@launch
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    /**
     * Where the user is, for the activity's saved state. A background process kill restores it
     * ([restoreNavigation]); a cold start (no saved state) opens on Home instead.
     */
    private var activityAttached = false

    /** True the first time an activity binds this ViewModel, i.e. it was just created, not retained. */
    fun attachActivity(): Boolean = !activityAttached.also { activityAttached = true }

    fun navigationState(): android.os.Bundle = android.os.Bundle().apply {
        putString(NAV_SECTION, uiState.section.name)
        uiState.activeChat?.let { chat ->
            putString(NAV_SESSION, chat.storedSessionId)
            putString(NAV_PROFILE, chat.profile)
            putString(NAV_TITLE, chat.title)
            putString(NAV_SOURCE, chat.source)
        }
    }

    fun restoreNavigation(state: android.os.Bundle?) {
        state ?: return
        val section = state.getString(NAV_SECTION)?.let { name -> MainSection.entries.firstOrNull { it.name == name } } ?: return
        val sessionId = state.getString(NAV_SESSION)?.takeIf { it.isNotBlank() }
        val profile = state.getString(NAV_PROFILE).orEmpty().ifBlank { "default" }
        uiState = uiState.copy(section = section)
        viewModelScope.launch {
            // The saved connection reconnects on its own; reopen the page once it is live, unless the user moved on.
            repeat(60) {
                if (uiState.section != section || uiState.activeChat != null) return@launch
                if (uiState.connectionState == GatewayConnectionState.CONNECTED &&
                    (sessionId == null || uiState.profiles.any { it.name == profile })) {
                    if (sessionId != null && section == MainSection.CHAT) {
                        openSession(HermesSession(id = sessionId, profile = profile,
                            title = state.getString(NAV_TITLE).orEmpty(), source = state.getString(NAV_SOURCE).orEmpty()))
                    } else switchSection(section)
                    return@launch
                }
                delay(500)
            }
        }
    }

    fun openSession(session: HermesSession) {
        val repo = repository ?: return
        rememberChatOrigin()
        val generation = ++navigationGeneration
        resetStream()
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            runCatching { repo.resumeSession(session.id, session.profile, session.title, session.source) }
                .onSuccess { chat ->
                    if (generation != navigationGeneration) return@onSuccess
                    if (!chat.running) com.nousresearch.hermesmobile.push.PushDelivery.clearTurns(getApplication(), chat.storedSessionId)
                    resetStream(chat.streamingText)
                    uiState = uiState.copy(
                        // Switch only now: switching first showed the Chats list while the chat loaded.
                        section = MainSection.CHAT,
                        activeChat = chat.copy(streamingText = ""),
                        sessionUsage = null,
                        loading = false,
                        tools = emptyList(),
                        selectedProfile = session.profile,
                        loadingOlderMessages = false
                    )
                    loadChatEnhancements(chat)
                    runCatching {
                        repo.rest.patchSession(session.id, session.profile, JSONObject().put("unread", false))
                    }
                    updateSessionLocal(session.copy(unread = false))
                    markChatSeen(chat.storedSessionId)
                }
                .onFailure {
                    if (generation == navigationGeneration) uiState = uiState.copy(loading = false, error = it.message)
                }
        }
    }

    fun openBot(profile: HermesProfile) {
        val repo = repository ?: return
        rememberChatOrigin()
        val generation = ++navigationGeneration
        resetStream()
        viewModelScope.launch {
            uiState = uiState.copy(
                loading = true,
                error = null,
                selectedProfile = profile.name
            )
            val (claimKickoff, releaseKickoff) = kickoffClaims()
            runCatching { repo.openBotChat(profile, claimKickoff, releaseKickoff) }
                .onSuccess { chat ->
                    if (generation == navigationGeneration) {
                        resetStream(chat.streamingText)
                        uiState = uiState.copy(
                            // Switch only now: switching first showed the Chats list while the chat loaded.
                            section = MainSection.CHAT,
                            activeChat = chat.copy(title = profile.label, streamingText = ""),
                            sessionUsage = null,
                            loading = false,
                            tools = emptyList(),
                            loadingOlderMessages = false
                        )
                        loadChatEnhancements(chat.copy(title = profile.label))
                        markChatSeen(chat.storedSessionId)
                    }
                }
                .onFailure {
                    if (generation == navigationGeneration) uiState = uiState.copy(loading = false, error = it.message)
                }
        }
    }

    fun loadOlderMessages() {
        val repo = repository ?: return
        val chat = uiState.activeChat ?: return
        if (!chat.hasOlderMessages || uiState.loadingOlderMessages) return

        uiState = uiState.copy(loadingOlderMessages = true)
        viewModelScope.launch {
            runCatching { repo.loadOlderMessages(chat) }
                .onSuccess { page ->
                    val current = uiState.activeChat
                    if (current?.storedSessionId != chat.storedSessionId) return@onSuccess
                    val merged = mergeMessagePages(page.messages, current.messages)
                    uiState = uiState.copy(
                        activeChat = current.copy(
                            messages = merged,
                            hasOlderMessages = page.hasOlder,
                            olderOffset = current.olderOffset + page.returned
                        ),
                        loadingOlderMessages = false
                    )
                }
                .onFailure { uiState = uiState.copy(loadingOlderMessages = false, error = it.message) }
        }
    }

    fun branchSession(session: HermesSession) {
        val repo = repository ?: return
        rememberChatOrigin()
        val generation = ++navigationGeneration
        resetStream()
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null, section = MainSection.CHAT)
            runCatching { repo.branchSession(session) }
                .onSuccess { chat ->
                    if (generation != navigationGeneration) return@onSuccess
                    resetStream(chat.streamingText)
                    uiState = uiState.copy(
                        activeChat = chat.copy(streamingText = ""),
                        sessionUsage = null,
                        loading = false,
                        tools = emptyList(),
                        selectedProfile = session.profile
                    )
                    loadChatEnhancements(chat)
                    refreshSessions()
                }
                .onFailure {
                    if (generation == navigationGeneration) uiState = uiState.copy(loading = false, error = it.message)
                }
        }
    }

    fun compressActiveChat() {
        val repo = repository ?: return
        val chat = uiState.activeChat ?: return
        if (chat.running) {
            uiState = uiState.copy(snackbar = string(R.string.i18n_vm_stop_before_compress))
            return
        }
        val generation = navigationGeneration
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            runCatching { repo.compressSession(chat) }
                .onSuccess { compressed ->
                    if (generation != navigationGeneration || uiState.activeChat?.storedSessionId != chat.storedSessionId) {
                        return@onSuccess
                    }
                    resetStream(compressed.streamingText)
                    uiState = uiState.copy(
                        activeChat = compressed.copy(streamingText = ""),
                        loading = false,
                        tools = emptyList(),
                        snackbar = string(R.string.i18n_vm_compressed)
                    )
                    // Compression may rotate the durable session id. Keep the current draft/queue and
                    // re-key it to the compressed session instead of loading an empty composer state.
                    persistCurrentComposerNow()
                    refreshSessionMeta()
                    refreshSessions()
                }
                .onFailure {
                    if (generation == navigationGeneration) uiState = uiState.copy(loading = false, error = it.message)
                }
        }
    }

    /**
     * The bot chat's first message is sent from this phone, so its answer may notify as a prompt typed here would.
     * Claimed when the kickoff is about to be sent, released when nothing was sent (the repository reports which).
     */
    private fun kickoffClaims(): Pair<(String) -> Unit, (Boolean) -> Unit> {
        var session = ""
        val app = getApplication<Application>()
        return { storedId: String -> session = storedId; com.nousresearch.hermesmobile.push.PushDelivery.claimTurn(app, storedId) } to
            { sent: Boolean -> if (!sent) com.nousresearch.hermesmobile.push.PushDelivery.releaseTurn(app, session) }
    }

    fun createBot(name: String, title: String, description: String, cloneFrom: String? = null) {
        val repo = repository ?: return
        rememberChatOrigin()
        val generation = ++navigationGeneration
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            val (claimKickoff, releaseKickoff) = kickoffClaims()
            runCatching { repo.createBot(name, title, description, cloneFrom, claimKickoff, releaseKickoff) }
                .onSuccess { (profile, chat) ->
                    if (generation == navigationGeneration) {
                        resetStream(chat.streamingText)
                        uiState = uiState.copy(
                            loading = false,
                            snackbar = string(R.string.i18n_vm_bot_created, profile.label),
                            section = MainSection.CHAT,
                            selectedProfile = profile.name,
                            activeChat = chat.copy(title = profile.label, streamingText = ""),
                            tools = emptyList(),
                            loadingOlderMessages = false
                        )
                        loadChatEnhancements(chat.copy(title = profile.label))
                    } else {
                        uiState = uiState.copy(loading = false, snackbar = string(R.string.i18n_vm_bot_created, profile.label))
                    }
                    refreshProfiles()
                    refreshSessions()
                }
                .onFailure {
                    if (generation == navigationGeneration) uiState = uiState.copy(loading = false, error = it.message)
                    else uiState = uiState.copy(loading = false)
                }
        }
    }

    fun editBot(profile: HermesProfile) {
        val repo = repository ?: return
        val generation = ++botEditorGeneration
        viewModelScope.launch {
            uiState = uiState.copy(
                botEditorLoadingProfile = profile.name,
                botEditor = null,
                pendingBotModelSave = null,
                error = null
            )
            runCatching {
                val fresh = runCatching { repo.rpcProfiles().firstOrNull { it.name == profile.name } }.getOrNull() ?: profile
                repo.loadBotProfileConfig(fresh)
            }.onSuccess { config ->
                if (generation == botEditorGeneration) {
                    config.avatarDataUrl?.let { cacheAvatar(config.profile.name, it) }
                    if (config.avatarDataUrl == null && !config.profile.hasAvatar) dropAvatarCache(config.profile.name, markMissing = true)
                    uiState = uiState.copy(botEditorLoadingProfile = null, botEditor = config)
                }
            }.onFailure {
                if (generation == botEditorGeneration) {
                    uiState = uiState.copy(botEditorLoadingProfile = null, error = it.message)
                }
            }
        }
    }

    fun closeBotEditor() {
        botEditorGeneration += 1
        uiState = uiState.copy(
            botEditor = null,
            botEditorLoadingProfile = null,
            botEditorSaving = false,
            pendingBotModelSave = null
        )
    }

    fun saveBotEditor(updated: BotProfileConfig) {
        val repo = repository ?: return
        val original = uiState.botEditor ?: return
        if (original.profile.name != updated.profile.name || uiState.botEditorSaving) return
        viewModelScope.launch {
            uiState = uiState.copy(botEditorSaving = true, error = null)
            runCatching { repo.saveBotProfileConfig(original, updated) }
                .onSuccess { result ->
                    when {
                        result.uiMetaConflict -> {
                            val freshProfile = runCatching { repo.rpcProfiles().firstOrNull { it.name == original.profile.name } }.getOrNull()
                            val fresh = freshProfile?.let { runCatching { repo.loadBotProfileConfig(it) }.getOrNull() }
                            uiState = uiState.copy(
                                botEditorSaving = false,
                                botEditor = fresh ?: original,
                                error = string(R.string.i18n_vm_bot_conflict)
                            )
                        }
                        result.confirmRequired -> {
                            // profiles.configure may already have committed SOUL/capabilities/ui_meta
                            // before the model guard asks for confirmation. Refresh the editor base so
                            // cancelling or confirming never retries those writes with a stale CAS revision.
                            val freshProfile = runCatching {
                                repo.rpcProfiles().firstOrNull { it.name == original.profile.name }
                            }.getOrNull()
                            val fresh = freshProfile?.let { runCatching { repo.loadBotProfileConfig(it) }.getOrNull() }
                                ?: original
                            if (fresh.avatarDataUrl.isNullOrBlank()) {
                                dropAvatarCache(fresh.profile.name, markMissing = true)
                            } else {
                                cacheAvatar(fresh.profile.name, fresh.avatarDataUrl)
                            }
                            uiState = uiState.copy(
                                botEditorSaving = false,
                                botEditor = fresh,
                                pendingBotModelSave = PendingBotModelSave(
                                    original = fresh,
                                    updated = fresh.copy(provider = updated.provider, model = updated.model),
                                    message = result.confirmMessage.ifBlank { "Hermes requires confirmation before switching to this model." }
                                )
                            )
                        }
                        result.applied.any { !it.value } -> {
                            val failed = result.applied.filterValues { !it }.keys.joinToString()
                            uiState = uiState.copy(botEditorSaving = false, error = string(R.string.i18n_vm_bot_sections_failed, failed))
                        }
                        else -> {
                            if (updated.avatarDataUrl.isNullOrBlank()) {
                                dropAvatarCache(updated.profile.name, markMissing = true)
                            } else {
                                cacheAvatar(updated.profile.name, updated.avatarDataUrl)
                            }
                            uiState = uiState.copy(
                                botEditor = null,
                                botEditorSaving = false,
                                pendingBotModelSave = null,
                                snackbar = string(R.string.i18n_vm_bot_updated, updated.title.ifBlank { updated.profile.name })
                            )
                            refreshProfiles()
                        }
                    }
                }
                .onFailure { uiState = uiState.copy(botEditorSaving = false, error = it.message) }
        }
    }

    fun confirmBotModelSwitch() {
        val repo = repository ?: return
        val pending = uiState.pendingBotModelSave ?: return
        viewModelScope.launch {
            uiState = uiState.copy(botEditorSaving = true, error = null)
            runCatching {
                repo.confirmBotModelSwitch(
                    profile = pending.original.profile.name,
                    provider = pending.updated.provider,
                    model = pending.updated.model
                )
            }
                .onSuccess { result ->
                    if (result.uiMetaConflict) {
                        uiState = uiState.copy(
                            botEditorSaving = false,
                            pendingBotModelSave = null,
                            error = string(R.string.i18n_vm_bot_conflict_confirm)
                        )
                    } else if (result.applied.any { !it.value }) {
                        uiState = uiState.copy(
                            botEditorSaving = false,
                            pendingBotModelSave = null,
                            error = string(R.string.i18n_vm_bot_rejected)
                        )
                    } else {
                        uiState = uiState.copy(
                            botEditor = null,
                            botEditorSaving = false,
                            pendingBotModelSave = null,
                            snackbar = string(R.string.i18n_vm_bot_model_updated)
                        )
                        refreshProfiles()
                    }
                }
                .onFailure {
                    uiState = uiState.copy(botEditorSaving = false, pendingBotModelSave = null, error = it.message)
                }
        }
    }

    fun cancelBotModelSwitch() {
        uiState = uiState.copy(pendingBotModelSave = null, botEditorSaving = false)
    }

    fun deleteBot(profile: HermesProfile) {
        val repo = repository ?: return
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            runCatching { repo.deleteBot(profile) }
                .onSuccess {
                    if (uiState.activeChat?.profile == profile.name) closeActiveChat()
                    uiState = uiState.copy(
                        loading = false,
                        botEditor = null,
                        snackbar = string(R.string.i18n_vm_bot_deleted, profile.label)
                    )
                    refreshProfiles()
                    refreshSessions()
                }
                .onFailure { uiState = uiState.copy(loading = false, error = it.message) }
        }
    }

    fun newBotSideChat(profile: HermesProfile) {
        newChat(profile.name)
    }

    fun sendMessage(text: String) = sendMessage(text, deepConfirmed = false)

    private fun sendMessage(text: String, deepConfirmed: Boolean) {
        val chat = uiState.activeChat ?: return
        val prompt = text.trim()
        val attachments = uiState.composerAttachments
        if (prompt.isBlank() && attachments.isEmpty()) return
        chat.editing?.let { editing ->
            if (chat.running) {
                uiState = uiState.copy(snackbar = string(R.string.i18n_edit_busy))
                return
            }
            // Removing later turns is asked first, as Desktop's restore confirm does.
            if (editing.laterTurns > 0 && !deepConfirmed) {
                uiState = uiState.copy(activeChat = chat.copy(editing = editing.copy(confirming = true)))
                return
            }
            uiState = uiState.copy(chatDraft = "", composerAttachments = emptyList(), activeChat = chat.copy(editing = null))
            persistComposerSoon(immediate = true)
            submitPromptNow(chat.copy(editing = null), prompt, attachments, rewind = editing, confirmDeep = deepConfirmed)
            return
        }
        if (attachments.isEmpty() && prompt.startsWith("/") && prompt.length > 1) {
            runSlashCommand(chat, prompt)
            return
        }

        if (chat.running) {
            val queued = QueuedPrompt(UUID.randomUUID().toString(), prompt, attachments)
            uiState = uiState.copy(
                queuedPrompts = uiState.queuedPrompts + queued,
                chatDraft = "",
                composerAttachments = emptyList(),
                snackbar = string(R.string.i18n_vm_queued)
            )
            persistComposerSoon(immediate = true)
            return
        }

        uiState = uiState.copy(chatDraft = "", composerAttachments = emptyList())
        persistComposerSoon(immediate = true)
        submitPromptNow(chat, prompt, attachments)
    }

    private fun submitPromptNow(
        chat: ActiveChat,
        prompt: String,
        attachments: List<ComposerAttachment>,
        rewind: EditingTurn? = null,
        confirmDeep: Boolean = false
    ) {
        val repo = repository ?: return
        val optimisticTimestamp = System.currentTimeMillis() / 1000
        // Only your words: a bubble with nothing typed shows its attachments alone, as in Desktop.
        val visibleText = prompt
        val optimistic = ChatMessage(
            role = "user",
            content = visibleText,
            attachments = attachments.map { it.name },
            live = true,
            timestamp = optimisticTimestamp
        )
        // An edit drops the edited turn and everything after it before running again.
        val kept = rewind?.let { edit -> chat.messages.indexOfFirst { it.stableKey == edit.key }.takeIf { it >= 0 } }
            ?.let { chat.messages.take(it) } ?: chat.messages
        uiState = uiState.copy(
            activeChat = chat.copy(messages = kept + optimistic, running = true),
            error = null,
            liveStatuses = if (attachments.isEmpty()) uiState.liveStatuses else listOf(
                LiveTaskStatus("attachments", "Preparing attachments", "0/${attachments.size}")
            ) + uiState.liveStatuses.filterNot { it.id == "attachments" }
        )

        // Claimed before sending: a short turn can end, and its notice arrive, before prompt.submit answers.
        // Its answer may then notify even with "only my prompts" on.
        com.nousresearch.hermesmobile.push.PushDelivery.claimTurn(getApplication(), chat.storedSessionId)
        viewModelScope.launch {
            runCatching {
                sessionModelPins[chat.runtimeSessionId]?.takeIf { it.ensureOnNextPrompt }?.let { pin ->
                    val reapplied = repo.switchSessionModel(
                        chat.runtimeSessionId, pin.profile, pin.provider, pin.model, pin.reasoning,
                        confirmExpensive = true
                    )
                    if (reapplied.confirmRequired) error(
                        reapplied.confirmMessage.ifBlank { "Hermes still requires confirmation for the selected model" }
                    )
                    sessionModelPins[chat.runtimeSessionId] = pin.copy(ensureOnNextPrompt = false)
                }
                val refs = mutableListOf<String>()
                // Where each attachment landed, so the live bubble's chips open it before the history reloads.
                val shown = mutableListOf<String>()
                attachments.forEachIndexed { index, item ->
                    val bytes = withContext(Dispatchers.IO) { readAttachmentBytes(item) }
                    when (item.kind) {
                        "image" -> repo.attachImageBytes(chat.runtimeSessionId, chat.profile, item.name, bytes)
                            .takeIf { it.isNotBlank() }?.let { shown += "@image:$it" }
                        // A PDF stays a file in the session, as in Desktop: tools can read it and its chip opens it.
                        else -> repo.attachFileBytes(chat.runtimeSessionId, chat.profile, item.name, item.mimeType, bytes)
                            .takeIf { it.isNotBlank() }?.let { refs += it; shown += it }
                    }
                    val current = uiState.activeChat
                    if (current?.runtimeSessionId == chat.runtimeSessionId) {
                        uiState = uiState.copy(
                            liveStatuses = uiState.liveStatuses.map {
                                if (it.id == "attachments") it.copy(detail = "${index + 1}/${attachments.size}") else it
                            }
                        )
                    }
                }
                if (shown.isNotEmpty()) uiState.activeChat?.takeIf { it.runtimeSessionId == chat.runtimeSessionId }?.let { current ->
                    uiState = uiState.copy(activeChat = current.copy(messages = current.messages.map {
                        if (it.live && it.rowId == null && it.role == "user" && it.content == visibleText &&
                            it.timestamp == optimisticTimestamp) it.copy(refs = shown) else it
                    }))
                }
                // Desktop's buildContextText: the refs, then your words; an image alone gets its standard question.
                val wireText = listOf(refs.joinToString("\n"), prompt).filter { it.isNotBlank() }.joinToString("\n\n")
                    .ifBlank { if (attachments.any { it.kind == "image" }) "What do you see in this image?" else "Please inspect the attached files and respond to them." }
                repo.submitPrompt(chat.runtimeSessionId, chat.profile, withBotMentionContext(wireText, chat.profile), rewind, confirmDeep)
            }.onSuccess { result ->
                // A steer joins a turn already running instead of starting one: it waits for no answer of its own.
                if (result.status == "steered") com.nousresearch.hermesmobile.push.PushDelivery.releaseTurn(getApplication(), chat.storedSessionId)
                val current = uiState.activeChat
                if (current?.runtimeSessionId != chat.runtimeSessionId) return@onSuccess
                // The rewind re-inserted the kept turns under new row ids: take the history whole, not grafted.
                if (rewind != null) refreshActiveTailSoon(chat.storedSessionId, chat.profile, replace = true)
                val rebound = if (result.userRowId != null) {
                    current.messages.toMutableList().also { rows ->
                        val index = rows.indexOfLast {
                            it.live && it.rowId == null && it.role == "user" &&
                                it.content == visibleText && it.timestamp == optimisticTimestamp
                        }
                        if (index >= 0) rows[index] = rows[index].copy(rowId = result.userRowId)
                    }
                } else current.messages
                val notice = when (result.status) {
                    "queued" -> "Message queued by Hermes"
                    "steered" -> "Message applied to current turn"
                    "redirected" -> "Turn redirected"
                    else -> null
                }
                uiState = uiState.copy(
                    activeChat = current.copy(messages = rebound),
                    liveStatuses = uiState.liveStatuses.filterNot { it.id == "attachments" },
                    snackbar = notice
                )
            }.onFailure { t ->
                com.nousresearch.hermesmobile.push.PushDelivery.releaseTurn(getApplication(), chat.storedSessionId)
                val current = uiState.activeChat
                if (current?.runtimeSessionId == chat.runtimeSessionId) {
                    val canRestore = uiState.chatDraft.isBlank() && uiState.composerAttachments.isEmpty()
                    uiState = uiState.copy(
                        activeChat = current.copy(
                            // A failed edit puts the chat back as it was.
                            messages = if (rewind != null) chat.messages else current.messages.filterNot {
                                it.live && it.rowId == null && it.role == "user" &&
                                    it.content == visibleText && it.timestamp == optimisticTimestamp
                            },
                            running = false
                        ),
                        chatDraft = if (canRestore) prompt else uiState.chatDraft,
                        composerAttachments = if (canRestore) attachments else uiState.composerAttachments,
                        liveStatuses = uiState.liveStatuses.filterNot { it.id == "attachments" },
                        error = t.message
                    )
                    persistComposerSoon(immediate = true)
                }
            }
        }
    }

    private fun readAttachmentBytes(item: ComposerAttachment): ByteArray {
        require(item.size <= MAX_ATTACHMENT_BYTES || item.size <= 0L) { "${item.name} is larger than 25 MB" }
        val uri = Uri.parse(item.uri)
        val stream = getApplication<Application>().contentResolver.openInputStream(uri)
            ?: error("Cannot read ${item.name}")
        stream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_ATTACHMENT_BYTES) { "${item.name} is larger than 25 MB" }
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }

    private fun drainQueuedPrompt(expectedRuntimeId: String) {
        val current = uiState.activeChat ?: return
        if (current.runtimeSessionId != expectedRuntimeId || current.running) return
        val next = uiState.queuedPrompts.firstOrNull() ?: return
        uiState = uiState.copy(queuedPrompts = uiState.queuedPrompts.drop(1))
        persistComposerSoon(immediate = true)
        submitPromptNow(current, next.text, next.attachments)
    }

    private fun withBotMentionContext(text: String, activeProfile: String): String {
        if (!text.contains('@')) return text
        val bots = uiState.profiles.filter { it.isBot && it.name != activeProfile }
        if (bots.isEmpty()) return text
        val aliases = buildMap<String, HermesProfile> {
            bots.forEach { bot ->
                put(botMentionHandle(bot).lowercase(), bot)
                put(bot.name.lowercase(), bot)
                val friendly = mentionSlug(bot.label)
                if (friendly.isNotBlank()) putIfAbsent(friendly, bot)
            }
        }
        val found = linkedMapOf<String, HermesProfile>()
        val mention = Regex("(?:^|\\s)@([A-Za-z0-9][A-Za-z0-9._:-]*)", RegexOption.IGNORE_CASE)
        mention.findAll(text).forEach { match ->
            val token = match.groupValues[1].lowercase()
            aliases[token]?.let { found.putIfAbsent(it.name, it) }
        }
        if (found.isEmpty()) return text
        val lines = found.values.map { bot ->
            val handle = botMentionHandle(bot)
            val target = if (bot.name == "default") "hermes" else bot.name
            val title = bot.label.takeIf { it != bot.name }.orEmpty()
            val titlePart = if (title.isNotBlank()) " (\"$title\")" else ""
            val targetPart = if (handle != target) " (message_agent target: \"$target\")" else ""
            "@$handle = agent profile \"${bot.name}\"$titlePart$targetPart"
        }
        return text + "\n\n[@mentions resolved from the Bot Mode roster — the user is referring to: " +
            lines.joinToString("; ") +
            ". If they want one of these agents contacted, compose your own message and send it with your message_agent tool; never forward the user's text verbatim. If this session has no message_agent tool, agent messaging is unavailable here — say so.]"
    }

    private fun botMentionHandle(profile: HermesProfile): String =
        if (profile.name == "default") "hermes" else profile.name

    private fun mentionSlug(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9._:-]+"), "-")
        .trim('-')

    private fun runSlashCommand(chat: ActiveChat, command: String) {
        val repo = repository ?: return
        slashCompletionJob?.cancel()
        uiState = uiState.copy(chatDraft = "", slashCompletions = emptyList(), slashQuery = null, slashLoading = false)
        persistComposerSoon(immediate = true)
        when (SlashCommands.local(command)) {
            LocalSlash.NEW -> { newChat(chat.profile); return }
            LocalSlash.RETRY -> { retryLastTurn(); return }
            LocalSlash.COMPRESS -> { compressActiveChat(); return }
            LocalSlash.BRANCH -> { branchActiveChat(); return }
            // The model picker is UI state; ChatThread opens it before reaching here.
            LocalSlash.MODEL, null -> Unit
        }
        viewModelScope.launch {
            runCatching { repo.execSlash(chat.runtimeSessionId, chat.profile, command) }
                .onSuccess { result -> applySlashResult(chat, command, result) }
                .onFailure { t ->
                    // Old gateways without slash.exec: fall back to a plain prompt as before.
                    if (t is GatewayRpcException && t.code == -32601) {
                        uiState.activeChat?.takeIf { it.runtimeSessionId == chat.runtimeSessionId }?.let { submitPromptNow(it, command, emptyList()) }
                    } else if (uiState.activeChat?.runtimeSessionId == chat.runtimeSessionId) {
                        uiState = uiState.copy(chatDraft = uiState.chatDraft.ifBlank { command }, error = t.message)
                    }
                }
        }
    }

    private suspend fun applySlashResult(chat: ActiveChat, command: String, result: SlashResult) {
        val repo = repository ?: return
        var current = uiState.activeChat?.takeIf { it.runtimeSessionId == chat.runtimeSessionId } ?: return
        if (SlashCommands.rewinds(command)) {
            current = runCatching { repo.reloadTranscript(current) }.getOrDefault(current)
            uiState = uiState.copy(activeChat = current)
        }
        val notice = result.notice.takeIf { it.isNotBlank() }
        when (result.type) {
            "send" -> {
                if (notice != null) uiState = uiState.copy(snackbar = notice)
                if (result.message.isNotBlank()) {
                    if (current.running) uiState = uiState.copy(
                        queuedPrompts = uiState.queuedPrompts + QueuedPrompt(UUID.randomUUID().toString(), result.message, emptyList())
                    ) else submitPromptNow(current, result.message, emptyList())
                }
            }
            "prefill" -> {
                uiState = uiState.copy(chatDraft = result.message, snackbar = notice)
                persistComposerSoon(immediate = true)
            }
            else -> uiState = uiState.copy(
                commandOutput = CommandOutput(command.trim(), result.output.ifBlank { notice ?: string(R.string.a22_no_output) })
            )
        }
    }

    /** Desktop's "Retry": the gateway rewinds the last turn and hands back its prompt to resend. */
    fun retryLastTurn() {
        val repo = repository ?: return
        val chat = uiState.activeChat ?: return
        if (chat.running) {
            uiState = uiState.copy(snackbar = string(R.string.a22_retry_busy))
            return
        }
        viewModelScope.launch {
            runCatching { repo.dispatchCommand(chat.runtimeSessionId, chat.profile, "retry") }
                .onSuccess { result -> applySlashResult(chat, "/retry", result) }
                .onFailure { t -> if (uiState.activeChat?.runtimeSessionId == chat.runtimeSessionId) uiState = uiState.copy(error = t.message) }
        }
    }

    /** Branches the open conversation into a new chat (whole history, like Desktop's /branch). */
    fun branchActiveChat() {
        val chat = uiState.activeChat ?: return
        val session = uiState.sessions.firstOrNull { it.id == chat.storedSessionId && it.profile == chat.profile }
            ?: HermesSession(id = chat.storedSessionId, profile = chat.profile, title = chat.title, source = chat.source)
        branchSession(session)
    }

    fun stopTurn() {
        val repo = repository ?: return
        val chat = uiState.activeChat ?: return
        viewModelScope.launch {
            runCatching { repo.interrupt(chat.runtimeSessionId, chat.profile) }
                // A stopped turn sends no notice: its claim would let the next turn started elsewhere ring.
                .onSuccess { com.nousresearch.hermesmobile.push.PushDelivery.releaseTurn(getApplication(), chat.storedSessionId) }
                .onFailure { uiState = uiState.copy(error = it.message) }
        }
    }

    fun steer(text: String) {
        val repo = repository ?: return
        val chat = uiState.activeChat ?: return
        val value = text.trim()
        if (value.isBlank()) return
        viewModelScope.launch {
            runCatching { repo.steer(chat.runtimeSessionId, chat.profile, value) }
                .onSuccess { uiState = uiState.copy(snackbar = string(R.string.i18n_vm_steer_sent)) }
                .onFailure { uiState = uiState.copy(error = it.message) }
        }
    }

    fun renameSession(session: HermesSession, title: String) {
        val repo = repository ?: return
        val clean = title.trim()
        if (clean.isBlank()) return
        val old = session
        updateSessionLocal(session.copy(title = clean))
        viewModelScope.launch {
            runCatching { repo.rest.patchSession(session.id, session.profile, JSONObject().put("title", clean)) }
                .onFailure {
                    updateSessionLocal(old)
                    uiState = uiState.copy(error = it.message)
                }
        }
    }

    fun togglePin(session: HermesSession) =
        patchSessionFlag(session, "pinned", !session.pinned) { it.copy(pinned = !session.pinned) }

    fun toggleArchive(session: HermesSession) =
        patchSessionFlag(session, "archived", !session.archived) { it.copy(archived = !session.archived) }

    fun toggleUnread(session: HermesSession) =
        patchSessionFlag(session, "unread", !session.unread) { it.copy(unread = !session.unread) }

    private fun patchSessionFlag(
        session: HermesSession,
        field: String,
        value: Boolean,
        optimistic: (HermesSession) -> HermesSession
    ) {
        val repo = repository ?: return
        val old = session
        updateSessionLocal(optimistic(session))
        viewModelScope.launch {
            val targetId = if (field == "pinned") session.lineageRootId ?: session.id else session.id
            runCatching { repo.rest.patchSession(targetId, session.profile, JSONObject().put(field, value)) }
                .onFailure {
                    updateSessionLocal(old)
                    uiState = uiState.copy(error = it.message)
                }
        }
    }

    fun deleteSession(session: HermesSession) {
        val repo = repository ?: return
        val oldSessions = uiState.sessions
        val oldSearch = uiState.sessionSearchResults
        uiState = uiState.copy(
            sessions = oldSessions.filterNot { sameSession(it, session) },
            sessionSearchResults = oldSearch.filterNot { sameSession(it, session) }
        )
        viewModelScope.launch {
            runCatching { repo.rest.deleteSession(session.id, session.profile) }
                .onFailure {
                    uiState = uiState.copy(sessions = oldSessions, sessionSearchResults = oldSearch, error = it.message)
                }
        }
    }

    private fun updateSessionLocal(updated: HermesSession) {
        uiState = uiState.copy(
            sessions = uiState.sessions.map { if (sameSession(it, updated)) updated else it },
            sessionSearchResults = uiState.sessionSearchResults.map { if (sameSession(it, updated)) updated else it }
        )
    }

    private fun sameSession(a: HermesSession, b: HermesSession): Boolean =
        a.id == b.id && a.profile == b.profile

    fun createCron(profile: String, draft: CronDraft) {
        val repo = repository ?: return
        viewModelScope.launch {
            runCatching { repo.rest.createCron(profile, draft) }
                .onSuccess {
                    uiState = uiState.copy(snackbar = string(R.string.i18n_vm_routine_created))
                    refreshCron()
                }
                .onFailure { uiState = uiState.copy(error = it.message) }
        }
    }

    fun updateCron(job: CronJob, draft: CronDraft) {
        val repo = repository ?: return
        viewModelScope.launch {
            runCatching { repo.rest.updateCron(job.profile, job.id, draft) }
                .onSuccess { updated ->
                    if (repository !== repo) return@onSuccess
                    uiState = uiState.copy(
                        cronJobs = uiState.cronJobs.map { if (it.id == updated.id && it.profile == updated.profile) updated else it },
                        snackbar = string(R.string.i18n_vm_routine_updated)
                    )
                    loadCronRuns(updated)
                }
                .onFailure { uiState = uiState.copy(error = it.message) }
        }
    }

    fun pauseCron(job: CronJob) = mutateCron { repo -> repo.rest.pauseCron(job.profile, job.id) }
    fun resumeCron(job: CronJob) = mutateCron { repo -> repo.rest.resumeCron(job.profile, job.id) }
    fun triggerCron(job: CronJob) = mutateCron(message = string(R.string.i18n_vm_routine_queued)) { repo -> repo.rest.triggerCron(job.profile, job.id) }

    fun deleteCron(job: CronJob) {
        val repo = repository ?: return
        viewModelScope.launch {
            runCatching { repo.rest.deleteCron(job.profile, job.id) }
                .onSuccess {
                    uiState = uiState.copy(cronRuns = uiState.cronRuns - cronRunKey(job))
                    refreshCron()
                }
                .onFailure { uiState = uiState.copy(error = it.message) }
        }
    }

    fun loadCronRuns(job: CronJob) {
        val repo = repository ?: return
        val key = cronRunKey(job)
        viewModelScope.launch {
            runCatching { repo.rest.cronRuns(job.profile, job.id, limit = 20) }
                .onSuccess { runs -> uiState = uiState.copy(cronRuns = uiState.cronRuns + (key to runs)) }
                .onFailure { t ->
                    // Older gateways may not expose run history yet. Keep the rest of Cron usable.
                    if (t !is HermesHttpException || t.status != 404) uiState = uiState.copy(error = t.message)
                }
        }
    }

    fun instantiateCronBlueprint(profile: String, blueprint: AutomationBlueprint, values: Map<String, String>) {
        val repo = repository ?: return
        viewModelScope.launch {
            runCatching { repo.rest.instantiateCronBlueprint(profile, blueprint.id, values) }
                .onSuccess {
                    uiState = uiState.copy(snackbar = string(R.string.i18n_vm_blueprint, blueprint.name))
                    refreshCron()
                }
                .onFailure { uiState = uiState.copy(error = it.message) }
        }
    }

    private fun cronRunKey(job: CronJob): String = "${job.profile}:${job.id}"

    private fun mutateCron(message: String? = null, action: suspend (HermesRepository) -> CronJob) {
        val repo = repository ?: return
        viewModelScope.launch {
            runCatching { action(repo) }
                .onSuccess { updated ->
                    if (repository !== repo) return@onSuccess
                    uiState = uiState.copy(
                        cronJobs = uiState.cronJobs.map {
                            if (it.id == updated.id && it.profile == updated.profile) updated else it
                        },
                        snackbar = message
                    )
                }
                .onFailure { if (repository === repo) uiState = uiState.copy(error = it.message) }
        }
    }

    private fun refreshCron() {
        val repo = repository ?: return
        val profile = uiState.selectedProfile
        viewModelScope.launch {
            coroutineScope {
                val jobs = async { runCatching { repo.rest.cronJobs("all") } }
                val targets = async { runCatching { repo.rest.cronDeliveryTargets(profile) } }
                val blueprints = async { runCatching { repo.rest.cronBlueprints(profile) } }
                jobs.await()
                    .onSuccess { list -> if (repository === repo) uiState = uiState.copy(cronJobs = list) }
                    .onFailure { if (repository === repo) uiState = uiState.copy(error = it.message) }
                targets.await().onSuccess { list ->
                    if (repository === repo && uiState.selectedProfile == profile) uiState = uiState.copy(cronDeliveryTargets = list)
                }
                blueprints.await().onSuccess { list ->
                    if (repository === repo && uiState.selectedProfile == profile) uiState = uiState.copy(cronBlueprints = list)
                }
            }
        }
    }

    private suspend fun refreshCronSupport(profile: String) {
        val repo = repository ?: return
        coroutineScope {
            val targets = async { runCatching { repo.rest.cronDeliveryTargets(profile) }.getOrDefault(listOf(CronDeliveryTarget("local", "Local"))) }
            val blueprints = async { runCatching { repo.rest.cronBlueprints(profile) }.getOrDefault(emptyList()) }
            val targetList = targets.await()
            val blueprintList = blueprints.await()
            if (uiState.selectedProfile == profile && repository === repo) {
                uiState = uiState.copy(cronDeliveryTargets = targetList, cronBlueprints = blueprintList)
            }
        }
    }

    fun setMessagingEnabled(platform: MessagingPlatform, enabled: Boolean) {
        val repo = repository ?: return
        val profile = uiState.selectedProfile
        viewModelScope.launch {
            runCatching { repo.rest.updateMessagingPlatform(profile, platform.id, enabled = enabled) }
                .onSuccess { result ->
                    val action = if (enabled) "enabled" else "disabled"
                    val hotServed = result.optBooleanOrNull("hot_served")
                    uiState = uiState.copy(
                        snackbar = when (hotServed) {
                            true -> "${platform.name} $action · applied live"
                            false -> "${platform.name} $action · restart gateway to apply"
                            null -> "${platform.name} $action"
                        }
                    )
                    refreshMessagingAndPairing()
                }
                .onFailure { uiState = uiState.copy(error = it.message) }
        }
    }

    fun saveMessagingPlatform(platform: MessagingPlatform, env: Map<String, String>, clearEnv: List<String>) {
        val repo = repository ?: return
        val profile = uiState.selectedProfile
        val changed = env.filterValues { it.isNotBlank() }
        viewModelScope.launch {
            runCatching { repo.rest.updateMessagingPlatform(profile, platform.id, env = changed, clearEnv = clearEnv) }
                .onSuccess { result ->
                    val hotServed = result.optBooleanOrNull("hot_served")
                    uiState = uiState.copy(
                        snackbar = when (hotServed) {
                            true -> "${platform.name} configuration applied live"
                            false -> "${platform.name} configuration saved · restart gateway to apply"
                            null -> "${platform.name} configuration saved"
                        }
                    )
                    refreshMessagingAndPairing()
                }
                .onFailure { uiState = uiState.copy(error = it.message) }
        }
    }

    fun testMessagingPlatform(platform: MessagingPlatform) {
        val repo = repository ?: return
        val profile = uiState.selectedProfile
        viewModelScope.launch {
            runCatching { repo.rest.testMessagingPlatform(profile, platform.id) }
                .onSuccess { result ->
                    val message = result.optString("message").ifBlank {
                        val connected = result.optBoolean("connected", result.optBoolean("gateway_running", false))
                        if (connected) "${platform.name} is connected" else "${platform.name} test completed"
                    }
                    uiState = uiState.copy(snackbar = message)
                }
                .onFailure { uiState = uiState.copy(error = it.message) }
        }
    }

    fun restartMessagingGateway() {
        val repo = repository ?: return
        val profile = uiState.selectedProfile
        viewModelScope.launch {
            runCatching { repo.rest.restartGateway(profile) }
                .onSuccess { uiState = uiState.copy(snackbar = string(R.string.i18n_vm_restart)) }
                .onFailure { uiState = uiState.copy(error = it.message) }
        }
    }

    fun startTelegramOnboarding(botName: String = "") {
        val repo = repository ?: return
        val profile = uiState.selectedProfile
        telegramOnboardingPollingJob?.cancel()
        viewModelScope.launch {
            runCatching { repo.rest.startTelegramOnboarding(profile, botName) }
                .onSuccess { onboarding ->
                    if (uiState.selectedProfile != profile || repository !== repo) return@onSuccess
                    uiState = uiState.copy(telegramOnboarding = onboarding)
                    pollTelegramOnboarding(profile, onboarding)
                }
                .onFailure { uiState = uiState.copy(error = it.message) }
        }
    }

    private fun pollTelegramOnboarding(profile: String, initial: TelegramOnboarding) {
        val repo = repository ?: return
        telegramOnboardingPollingJob?.cancel()
        telegramOnboardingPollingJob = viewModelScope.launch {
            repeat(150) {
                delay(2_000)
                if (!appForeground || !networkOnline || repository !== repo || uiState.selectedProfile != profile) return@launch
                val latest = runCatching { repo.rest.telegramOnboardingStatus(profile, initial.pairingId) }.getOrNull()
                    ?: return@repeat
                uiState = uiState.copy(
                    telegramOnboarding = initial.copy(
                        status = latest.status,
                        botUsername = latest.botUsername,
                        ownerUserId = latest.ownerUserId,
                        expiresAt = latest.expiresAt
                    )
                )
                if (latest.status.lowercase() in setOf("ready", "expired", "cancelled", "failed", "error")) return@launch
            }
        }
    }

    fun applyTelegramOnboarding(allowedUserIds: List<String>) {
        val repo = repository ?: return
        val onboarding = uiState.telegramOnboarding ?: return
        val profile = uiState.selectedProfile
        telegramOnboardingPollingJob?.cancel()
        viewModelScope.launch {
            runCatching { repo.rest.applyTelegramOnboarding(profile, onboarding.pairingId, allowedUserIds.filter(String::isNotBlank)) }
                .onSuccess {
                    uiState = uiState.copy(telegramOnboarding = null, snackbar = string(R.string.i18n_vm_telegram))
                    refreshMessagingAndPairing()
                }
                .onFailure { uiState = uiState.copy(error = it.message) }
        }
    }

    fun cancelTelegramOnboarding() {
        val repo = repository ?: return
        val onboarding = uiState.telegramOnboarding ?: return
        val profile = uiState.selectedProfile
        telegramOnboardingPollingJob?.cancel()
        viewModelScope.launch {
            runCatching { repo.rest.cancelTelegramOnboarding(profile, onboarding.pairingId) }
            if (uiState.selectedProfile == profile) uiState = uiState.copy(telegramOnboarding = null)
        }
    }

    fun approvePairing(user: PairingUser) {
        val repo = repository ?: return
        val profile = uiState.selectedProfile
        viewModelScope.launch {
            runCatching { repo.rest.approvePairing(profile, user) }
                .onSuccess { refreshMessagingAndPairing() }
                .onFailure { uiState = uiState.copy(error = it.message) }
        }
    }

    fun revokePairing(user: PairingUser) {
        val repo = repository ?: return
        val profile = uiState.selectedProfile
        viewModelScope.launch {
            runCatching { repo.rest.revokePairing(profile, user.platform, user.userId) }
                .onSuccess { refreshMessagingAndPairing() }
                .onFailure { uiState = uiState.copy(error = it.message) }
        }
    }

    fun clearPendingPairing() {
        val repo = repository ?: return
        val profile = uiState.selectedProfile
        viewModelScope.launch {
            runCatching { repo.rest.clearPendingPairing(profile) }
                .onSuccess {
                    uiState = uiState.copy(snackbar = string(R.string.i18n_vm_pairing_cleared))
                    refreshMessagingAndPairing()
                }
                .onFailure { uiState = uiState.copy(error = it.message) }
        }
    }

    fun createWebhook(draft: WebhookDraft) {
        val repo = repository ?: return
        val profile = uiState.selectedProfile
        viewModelScope.launch {
            runCatching { repo.rest.createWebhook(profile, draft) }
                .onSuccess { result ->
                    val route = result.optJSONObject("route") ?: result.optJSONObject("subscription")
                    val secret = result.optString("secret").ifBlank { result.optString("hmac_secret") }
                    val url = result.optString("url").ifBlank { result.optString("route_url").ifBlank { route?.optString("url").orEmpty() } }
                    uiState = uiState.copy(
                        webhookReceipt = if (secret.isNotBlank() || url.isNotBlank()) WebhookReceipt(draft.name, url, secret) else null,
                        snackbar = if (secret.isBlank() && url.isBlank()) string(R.string.i18n_vm_webhook) else null
                    )
                    refreshMessagingAndPairing()
                }
                .onFailure { uiState = uiState.copy(error = it.message) }
        }
    }

    fun setWebhookEnabled(route: WebhookRoute, enabled: Boolean) {
        val repo = repository ?: return
        val profile = uiState.selectedProfile
        viewModelScope.launch {
            runCatching { repo.rest.setWebhookEnabled(profile, route.name, enabled) }
                .onSuccess { refreshMessagingAndPairing() }
                .onFailure { uiState = uiState.copy(error = it.message) }
        }
    }

    fun deleteWebhook(route: WebhookRoute) {
        val repo = repository ?: return
        val profile = uiState.selectedProfile
        viewModelScope.launch {
            runCatching { repo.rest.deleteWebhook(profile, route.name) }
                .onSuccess { refreshMessagingAndPairing() }
                .onFailure { uiState = uiState.copy(error = it.message) }
        }
    }

    fun dismissWebhookReceipt() {
        uiState = uiState.copy(webhookReceipt = null)
    }

    private suspend fun refreshMessagingAndPairing() {
        val repo = repository ?: return
        val profile = uiState.selectedProfile
        coroutineScope {
            val messaging = async { runCatching { repo.rest.messagingPlatforms(profile) } }
            val pairing = async { runCatching { repo.rest.pairing(profile) }.getOrDefault(emptyList<PairingUser>() to emptyList()) }
            val webhooks = async { runCatching { repo.rest.webhooks(profile) }.getOrDefault(WebhookState()) }
            val messagingResult = messaging.await()
            val pairingResult = pairing.await()
            val webhookResult = webhooks.await()
            if (uiState.selectedProfile == profile && repository === repo) {
                messagingResult.onSuccess { list ->
                    uiState = uiState.copy(
                        messaging = list,
                        pendingPairing = pairingResult.first,
                        approvedPairing = pairingResult.second,
                        webhooks = webhookResult
                    )
                }.onFailure { uiState = uiState.copy(error = it.message) }
            }
        }
    }

    fun answerApproval(choice: String) {
        val interaction = uiState.interaction ?: return
        repository?.gateway?.respond(interaction.requestId, JSONObject().put("choice", choice))
        completeInteraction(interaction.requestId)
    }

    fun answerInteraction(value: String) {
        val interaction = uiState.interaction ?: return
        val result = when (interaction.method) {
            "clarify" -> JSONObject().put("answer", value)
            "sudo", "secret", "vault.code", "vault.unlock_prompt", "vault.save_login" -> JSONObject().put("value", value)
            else -> JSONObject().put("value", value)
        }
        repository?.gateway?.respond(interaction.requestId, result)
        completeInteraction(interaction.requestId)
    }

    fun answerClarifyBatch(answers: Map<String, String>) {
        val interaction = uiState.interaction ?: return
        if (interaction.method != "clarify") return
        val payload = JSONObject(answers)
        interaction.params.optJSONObject("answers")?.let { locked ->
            locked.keys().forEach { key -> payload.put(key, locked.opt(key)) }
        }
        repository?.gateway?.respond(interaction.requestId, JSONObject().put("answers", payload))
        completeInteraction(interaction.requestId)
    }

    fun cancelInteraction() {
        val interaction = uiState.interaction ?: return
        when (interaction.method) {
            "clarify" -> repository?.gateway?.respond(interaction.requestId, JSONObject())
            "approval" -> repository?.gateway?.respond(interaction.requestId, JSONObject().put("choice", "deny"))
            "sudo", "secret", "vault.code", "vault.unlock_prompt", "vault.save_login" ->
                repository?.gateway?.respond(interaction.requestId, JSONObject().put("value", ""))
            else -> repository?.gateway?.reject(interaction.requestId, "Cancelled by user", -32800)
        }
        completeInteraction(interaction.requestId)
    }

    private fun enqueueInteraction(request: ServerInteraction) {
        val current = uiState.interaction
        if (current?.requestId == request.requestId ||
            uiState.interactionQueue.any { it.requestId == request.requestId }
        ) return

        if (current == null) {
            uiState = uiState.copy(interaction = request)
        } else {
            uiState = uiState.copy(interactionQueue = uiState.interactionQueue + request)
        }
    }

    private fun completeInteraction(requestId: String) {
        val remaining = uiState.interactionQueue.filterNot { it.requestId == requestId }
        if (uiState.interaction?.requestId == requestId) {
            uiState = uiState.copy(
                interaction = remaining.firstOrNull(),
                interactionQueue = remaining.drop(1)
            )
        } else {
            uiState = uiState.copy(interactionQueue = remaining)
        }
    }

    fun clearMessage() {
        uiState = uiState.copy(snackbar = null, error = null)
    }

    override fun onConnectionState(state: GatewayConnectionState, error: String?) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            uiState = uiState.copy(
                connectionState = state,
                error = when {
                    error != null -> error
                    state == GatewayConnectionState.CONNECTED -> null
                    else -> uiState.error
                }
            )
        }
    }

    override fun onGatewayReady(payload: JSONObject) {
        // The server's Hermes skin: the app follows it, as Desktop does (Settings → Theme).
        com.nousresearch.hermesmobile.ui.ThemeStore.saveServerSkin(getApplication(), payload.optJSONObject("skin"))
        startLiveSessions()
        // Whether the server's hermes-android plugin is older than this app needs (Settings says so).
        repository?.let { repo -> com.nousresearch.hermesmobile.update.AppUpdates.checkPlugin(getApplication(), repo.rest.currentConfig().id, repo) }
        viewModelScope.launch(Dispatchers.Main.immediate) {
        readyWaiter?.complete(Unit)
        uiState = uiState.copy(loading = false, error = null)
        repository?.rest?.currentConfig()?.let { persistActiveConfig(it.copy(selectedProfile = uiState.selectedProfile), true) }
        val current = uiState.activeChat
        val repo = repository ?: return@launch
        if (current == null) {
            refreshAll()
            return@launch
        }

        val storedId = current.storedSessionId
        val generation = navigationGeneration
        val transport = ++transportGeneration
        viewModelScope.launch {
            runCatching { repo.resumeSession(storedId, current.profile, current.title) }
                .onSuccess { resumed ->
                    val foreground = uiState.activeChat
                    if (transport == transportGeneration && repository === repo && generation == navigationGeneration && foreground?.storedSessionId == storedId) {
                        resetStream(resumed.streamingText)
                        uiState = uiState.copy(
                            activeChat = resumed.copy(title = current.title, streamingText = "", model = sessionModelPins[current.runtimeSessionId]?.model ?: resumed.model, provider = sessionModelPins[current.runtimeSessionId]?.provider ?: resumed.provider, reasoningEffort = sessionModelPins[current.runtimeSessionId]?.reasoning ?: resumed.reasoningEffort),
                            tools = emptyList()
                        )
                        sessionModelPins.remove(current.runtimeSessionId)?.let { pin ->
                            sessionModelPins[resumed.runtimeSessionId] = pin.copy(ensureOnNextPrompt = true)
                        }
                        refreshSessionMeta()
                        if (!resumed.running) {
                            com.nousresearch.hermesmobile.push.PushDelivery.clearTurns(getApplication(), resumed.storedSessionId)
                            drainQueuedPrompt(resumed.runtimeSessionId)
                        }
                    }
                }
                .onFailure { if (transport == transportGeneration && generation == navigationGeneration && repository === repo && uiState.connectionState == GatewayConnectionState.CONNECTED) uiState = uiState.copy(error = it.message) }
            if (transport == transportGeneration) refreshSessions()
        }
    }

    }

    override fun onServerRequest(request: ServerInteraction) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            when (request.method) {
                "approval", "clarify", "sudo", "secret", "vault.code", "vault.unlock_prompt", "vault.save_login" ->
                    enqueueInteraction(request)
                else ->
                    repository?.gateway?.reject(
                        request.requestId,
                        "Mobile client does not implement ${request.method}",
                        -32601
                    )
            }
        }
    }

    override fun onEvent(type: String, payload: JSONObject) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            // A turn started or ended in a chat that is not on screen: refresh the row dots now.
            if (type == "message.start" || type == "message.complete" || type == "approval.request" || type == "clarify.request") {
                val chat = uiState.activeChat
                if (chat == null || !payloadSessionMatches(payload, chat)) liveSessionsPoke.trySend(Unit)
            }
            if (type == "message.complete" && uiState.section == MainSection.BOTS) refreshBotActivitySoon()
            when (type) {
                "message.start" -> {
                    val chat = uiState.activeChat ?: return@launch
                    if (payloadSessionMatches(payload, chat)) {
                        uiState = uiState.copy(
                            activeChat = chat.copy(running = true),
                            reasoningText = "",
                            liveStatuses = uiState.liveStatuses.filter { it.status == "background" }
                        )
                    }
                }
                "skin.changed" -> com.nousresearch.hermesmobile.ui.ThemeStore.saveServerSkin(getApplication(), payload.optJSONObject("skin") ?: payload)
                "message.delta" -> handleMessageDelta(payload)
                "thinking.delta", "reasoning.delta", "reasoning.available" -> handleReasoningEvent(type, payload)
                "message.complete" -> { applyUsageEvent(payload); handleMessageComplete(payload) }
                "session.usage" -> applyUsageEvent(payload)
                "tool.start", "tool.generating", "tool.progress", "tool.complete" -> handleTool(type, payload)
                "status.update", "notification.show", "notification.clear", "background.complete",
                "review.summary", "browser.progress", "subagent.start", "subagent.progress", "subagent.complete" ->
                    handleLiveStatus(type, payload)
                "request.cancel" -> {
                    val id = payload.optString("id")
                    if (id.isNotBlank()) completeInteraction(id)
                }
                "session.info" -> {
                    applyUsageEvent(payload)
                    val chat = uiState.activeChat ?: return@launch
                    if (payloadSessionMatches(payload, chat)) {
                        val pin = sessionModelPins[chat.runtimeSessionId]
                        uiState = uiState.copy(
                            activeChat = chat.copy(
                                model = pin?.model ?: payload.optString("model", chat.model),
                                provider = pin?.provider ?: payload.optString("provider", chat.provider),
                                reasoningEffort = pin?.reasoning?.takeIf { it.isNotBlank() }
                                    ?: payload.optString("reasoning_effort").ifBlank {
                                        payload.optString("reasoning").ifBlank { chat.reasoningEffort }
                                    },
                                running = payload.optBoolean("running", chat.running)
                            )
                        )
                    }
                }
                "session.created", "session.updated", "sessions.changed" -> refreshSessions()
                "profiles.changed" -> refreshProfiles()
                "cron.changed" -> refreshCron()
                "messaging.changed" -> viewModelScope.launch { refreshMessagingAndPairing() }
                "gateway.error", "error" ->
                    uiState = uiState.copy(error = payload.optString("message", payload.optString("error")))
            }
        }
    }

    private fun handleMessageDelta(payload: JSONObject) {
        val chat = uiState.activeChat ?: return
        if (!payloadSessionMatches(payload, chat)) return
        val delta = TranscriptText.visible(payload.opt("text")).ifEmpty { TranscriptText.visible(payload.opt("delta")) }
        // A delta can be only whitespace (some models stream the space before a word on its own): keep it.
        if (delta.isEmpty()) return

        streamBuffer.append(delta)
        if (!chat.running) uiState = uiState.copy(activeChat = chat.copy(running = true))
        if (streamFlushJob?.isActive != true) {
            streamFlushJob = viewModelScope.launch {
                delay(STREAM_FLUSH_MS)
                streamingText = streamBuffer.toString()
            }
        }
    }

    private fun handleReasoningEvent(type: String, payload: JSONObject) {
        val chat = uiState.activeChat ?: return
        if (!payloadSessionMatches(payload, chat)) return
        val text = TranscriptText.reasoningDelta(payload.opt("text"))
            .ifBlank { TranscriptText.reasoningDelta(payload.opt("delta")) }
            .ifBlank { TranscriptText.reasoningDelta(payload.opt("reasoning")) }
        if (text.isBlank()) return
        uiState = uiState.copy(
            reasoningText = if (type.endsWith(".delta")) uiState.reasoningText + text else text
        )
    }

    private fun handleMessageComplete(payload: JSONObject) {
        val chat = uiState.activeChat ?: return
        if (!payloadSessionMatches(payload, chat)) return

        streamFlushJob?.cancel()
        val completed = TranscriptText.visible(payload.opt("text"))
            .ifBlank { TranscriptText.visible(payload.opt("content")) }
            .ifBlank { streamBuffer.toString() }
            .ifBlank { streamingText }

        // The model pin only fences the model-switch/first-turn race. Once that
        // turn completes, subsequent session.info events are authoritative again
        // so changes made from Desktop/another client are visible on Mobile.
        sessionModelPins.remove(chat.runtimeSessionId)

        val liveRow = if (completed.isBlank()) emptyList() else listOf(
            ChatMessage(
                role = "assistant",
                content = completed,
                reasoning = uiState.reasoningText,
                live = true,
                timestamp = System.currentTimeMillis() / 1000
            )
        )
        resetStream()
        uiState = uiState.copy(
            activeChat = chat.copy(messages = chat.messages + liveRow, running = false, streamingText = ""),
            reasoningText = "",
            liveStatuses = uiState.liveStatuses.filter { it.status == "background" }
        )
        refreshActiveTailSoon(chat.storedSessionId, chat.profile)
        refreshSessions()
        refreshSessionMeta()
        viewModelScope.launch {
            delay(120)
            drainQueuedPrompt(chat.runtimeSessionId)
        }
    }

    private fun handleTool(type: String, payload: JSONObject) {
        val chat = uiState.activeChat ?: return
        if (!payloadSessionMatches(payload, chat)) return

        val id = payload.optString("tool_id").ifBlank {
            payload.optString("tool_call_id").ifBlank {
                payload.optString("id").ifBlank {
                    payload.optString("name") + ":" + payload.optString("tool_name")
                }
            }
        }
        val name = payload.optString("tool_name").ifBlank { payload.optString("name", "Tool") }
        val status = when (type) {
            "tool.start", "tool.progress" -> "running"
            "tool.generating" -> "generating"
            else -> if (payload.optString("error").isNotBlank()) "error" else "done"
        }
        val detail = payload.optString("preview")
            .ifBlank { payload.optString("message") }
            .ifBlank { payload.optString("context") }

        val activity = ToolActivity(id = id, name = name, status = status, detail = detail)
        val rest = uiState.tools.filterNot { it.id == id }
        val artifacts = if (type == "tool.complete" && status == "done") {
            (uiState.artifactSuggestions + artifactSuggestionsFrom(payload, name))
                .distinctBy { it.path }
                .takeLast(20)
        } else uiState.artifactSuggestions
        uiState = uiState.copy(tools = (rest + activity).takeLast(12), artifactSuggestions = artifacts)
    }

    private fun handleLiveStatus(type: String, payload: JSONObject) {
        val chat = uiState.activeChat ?: return
        if (!payloadSessionMatches(payload, chat)) return
        val id = payload.optString("id").ifBlank {
            payload.optString("task_id").ifBlank { payload.optString("subagent_id").ifBlank { type } }
        }
        if (type == "notification.clear") {
            uiState = uiState.copy(liveStatuses = uiState.liveStatuses.filterNot { it.id == id || it.id == type })
            return
        }
        if (type == "status.update") {
            // Desktop only surfaces compaction from these; the rest are status-bar echoes
            // that, with no title, used to show up as a bare "Update" row.
            val kind = payload.optString("kind")
            val text = payload.optString("text").trim()
            val rows = uiState.liveStatuses.filterNot { it.id == type }
            uiState = uiState.copy(liveStatuses = when (kind) {
                "compacting", "compressing" -> (rows + LiveTaskStatus(type, text.ifBlank { kind.replaceFirstChar { it.uppercase() } })).takeLast(8)
                else -> rows
            })
            return
        }
        val label = payload.optString("title")
            .ifBlank { payload.optString("status") }
            .ifBlank { payload.optString("name") }
            .ifBlank { type.substringAfterLast('.').replaceFirstChar { it.uppercase() } }
        val detail = payload.optString("message")
            .ifBlank { payload.optString("detail") }
            .ifBlank { payload.optString("preview") }
        val done = type.endsWith(".complete") || type == "background.complete" || type == "review.summary"
        val activity = LiveTaskStatus(id, label, detail, if (done) "done" else if (type.startsWith("background")) "background" else "running")
        val rows = uiState.liveStatuses.filterNot { it.id == id }
        uiState = uiState.copy(liveStatuses = (rows + activity).takeLast(8))
    }

    private fun artifactSuggestionsFrom(payload: JSONObject, toolName: String): List<ArtifactSuggestion> {
        val matches = com.nousresearch.hermesmobile.data.ToolArtifacts.from(toolName, payload.optJSONObject("args"), payload.opt("result"))
        return matches.filter(com.nousresearch.hermesmobile.data.ToolArtifacts::previewable).take(8).map { path ->
            val name = path.substringAfterLast('/').ifBlank { "artifact" }
            val ext = name.substringAfterLast('.', "").lowercase()
            ArtifactSuggestion(
                path = path,
                name = name,
                kind = when (ext) {
                    "png", "jpg", "jpeg", "webp", "gif" -> "image"
                    "pdf" -> "pdf"
                    else -> "file"
                },
                sourceTool = toolName
            )
        }
    }

    /**
     * Live context ring: the gateway pushes `session.usage` every second while a turn runs and the
     * final figures on `message.complete`. A snapshot taken before the first API call has no window
     * yet, so it keeps the last known one instead of blanking the ring.
     */
    private fun applyUsageEvent(payload: JSONObject) {
        val chat = uiState.activeChat ?: return
        if (!payloadSessionMatches(payload, chat)) return
        val usage = SessionUsage.fromEvent(payload) ?: return
        val previous = uiState.sessionUsage
        val merged = if (usage.contextLimit == 0L && previous != null && previous.contextLimit > 0L) {
            usage.copy(contextUsed = previous.contextUsed, contextLimit = previous.contextLimit, contextEstimated = previous.contextEstimated)
        } else usage
        if (merged != previous) uiState = uiState.copy(sessionUsage = merged)
    }

    private fun payloadSessionMatches(payload: JSONObject, chat: ActiveChat): Boolean {
        val sid = payload.optString("session_id")
        return sid.isBlank() || sid == chat.runtimeSessionId || sid == chat.storedSessionId
    }

    private fun refreshActiveTailSoon(storedId: String, profile: String, replace: Boolean = false) {
        val repo = repository ?: return
        viewModelScope.launch {
            delay(250)
            runCatching {
                repo.rest.sessionMessagesPage(storedId, profile, limit = 120, offset = 0, order = "latest")
            }.onSuccess { page ->
                val current = uiState.activeChat
                if (current?.storedSessionId != storedId || current.profile != profile) return@onSuccess
                val merged = if (replace) graftDurableTail(emptyList(), page.messages).ifEmpty { current.messages }
                    .let { durable -> durable + current.messages.filter { live -> live.live && live.rowId == null &&
                        durable.none { it.role == live.role && it.content == live.content } } }
                    else graftDurableTail(current.messages, page.messages)
                uiState = uiState.copy(
                    activeChat = current.copy(
                        messages = merged,
                        hasOlderMessages = if (replace) page.hasOlder else current.hasOlderMessages || page.hasOlder,
                        olderOffset = if (replace) page.returned else maxOf(current.olderOffset, page.returned)
                    )
                )
            }
        }
    }

    private fun mergeMessagePages(older: List<ChatMessage>, newer: List<ChatMessage>): List<ChatMessage> {
        val seen = HashSet<String>()
        return (older + newer).filter { message ->
            val key = message.rowId?.let { "row:$it" } ?: message.stableKey
            seen.add(key)
        }
    }

    private fun graftDurableTail(current: List<ChatMessage>, tail: List<ChatMessage>): List<ChatMessage> {
        if (tail.isEmpty()) return current
        val tailIds = tail.mapNotNull { it.rowId }.toSet()
        val minTailId = tailIds.minOrNull()
        val preservedOlder = current.filter { message ->
            val row = message.rowId
            row != null && row !in tailIds && (minTailId == null || row < minTailId)
        }
        val optimistic = current.filter { message ->
            message.rowId == null && message.live && (
                message.role == "user" && tail.none { it.role == "user" && it.content == message.content } ||
                    message.role == "assistant" && tail.none { it.role == "assistant" && it.content == message.content }
                )
        }
        return mergeMessagePages(preservedOlder, tail + optimistic)
    }

    private fun resetStream(seed: String = "") {
        streamFlushJob?.cancel()
        streamFlushJob = null
        streamBuffer.setLength(0)
        if (seed.isNotBlank()) streamBuffer.append(seed)
        streamingText = seed
    }

    override fun onCleared() {
        persistCurrentComposerNow()
        readyWaiter?.cancel()
        streamFlushJob?.cancel()
        sessionSearchJob?.cancel()
        composerSaveJob?.cancel()
        repository?.gateway?.close()
        repository = null
        super.onCleared()
    }

    companion object {
        private const val LIVE_SESSIONS_POLL_MS = 4_000L
        private const val NAV_SECTION = "nav.section"
        private const val NAV_SESSION = "nav.session"
        private const val NAV_PROFILE = "nav.profile"
        private const val NAV_TITLE = "nav.title"
        private const val NAV_SOURCE = "nav.source"
        private const val SESSION_PAGE_SIZE = 80
        private const val STREAM_FLUSH_MS = 32L
        private const val MAX_AVATAR_CACHE_ITEMS = 32
        /** Desktop's LARGE_PASTE_ATTACHMENT_THRESHOLD. */
        private const val LARGE_PASTE_CHARS = 3_000
        private const val MAX_COMPOSER_ATTACHMENTS = 6
        private const val MAX_ATTACHMENT_BYTES = 25L * 1024L * 1024L
    }
}
