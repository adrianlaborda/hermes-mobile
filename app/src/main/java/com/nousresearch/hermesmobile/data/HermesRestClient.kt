package com.nousresearch.hermesmobile.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ensureActive
import okhttp3.RequestBody.Companion.asRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

class HermesHttpException(val status: Int, message: String) : IOException(message)

class HermesRestClient(
    initialConfig: GatewayConfig,
    private val onConfigUpdated: (GatewayConfig) -> Unit = {},
    private val onAuthExpired: (String) -> Unit = {},
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    private val refreshMutex = Mutex()
    @Volatile private var config: GatewayConfig = initialConfig
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = GatewayUrlPolicy.canonical(initialConfig.baseUrl)

    fun currentConfig(): GatewayConfig = config

    suspend fun wsTicket(): String {
        val root = requestObject("/api/auth/ws-ticket", "POST", JSONObject())
        return root.optString("ticket").takeIf { it.isNotBlank() }
            ?: error("Hermes did not return a WebSocket ticket")
    }

    suspend fun status(): GatewayStatus = GatewayStatus.fromJson(getObject("/api/status"))

    suspend fun profiles(): List<HermesProfile> {
        val root = getObject("/api/profiles")
        val rows = root.optJSONArray("profiles") ?: root.optJSONArray("items") ?: JSONArray()
        return rows.objects().map(HermesProfile::fromJson)
    }

    suspend fun createProfile(
        name: String,
        description: String = "",
        cloneFrom: String? = null,
        cloneAll: Boolean = false,
        noSkills: Boolean = false
    ): JSONObject {
        val body = JSONObject()
            .put("name", name)
            .put("clone_all", cloneAll)
            .put("no_skills", noSkills)
        if (!description.isBlank()) body.put("description", description)
        if (!cloneFrom.isNullOrBlank()) body.put("clone_from", cloneFrom)
        return requestObject("/api/profiles", "POST", body)
    }

    suspend fun deleteProfile(name: String): JSONObject =
        requestObject("/api/profiles/${enc(name)}", "DELETE")

    suspend fun renameProfile(name: String, newName: String): JSONObject =
        requestObject("/api/profiles/${enc(name)}", "PATCH", JSONObject().put("new_name", newName))

    suspend fun exportProfile(name: String): JSONObject =
        requestObject("/api/profiles/${enc(name)}/export", "POST", JSONObject().put("extra_files", JSONObject()).put("output", ""), readTimeoutSeconds = 60)

    suspend fun importProfile(archive: String, name: String? = null): JSONObject =
        requestObject("/api/profiles/import", "POST", JSONObject().put("archive", archive).apply {
            if (name.isNullOrBlank()) put("name", JSONObject.NULL) else put("name", name)
        }, readTimeoutSeconds = 60)

    suspend fun profileSoul(name: String): String =
        getObject("/api/profiles/${enc(name)}/soul").optString("content")

    suspend fun updateProfileSoul(name: String, content: String): JSONObject =
        requestObject("/api/profiles/${enc(name)}/soul", "PUT", JSONObject().put("content", content))

    /**
     * The recents: scheduled runs stay (the Routines section lists them), Kanban workers, tool and one-shot runs
     * do not (Desktop's `INTERNAL_LISTING_SOURCES`). Older servers ignore the parameter, so the rows are filtered too.
     */
    suspend fun sessionsPage(
        profile: String = "all",
        limit: Int = 80,
        offset: Int = 0,
        includeArchived: Boolean = false
    ): SessionListPage {
        val archived = if (includeArchived) "include" else "exclude"
        val path = "/api/profiles/sessions?limit=$limit&offset=$offset&min_messages=1" +
            "&archived=$archived&order=recent&profile=${enc(profile)}" +
            "&exclude_sources=${enc(HermesSession.INTERNAL_LISTING_SOURCES_CSV)}"
        val root = getObject(path)
        val rows = (root.optJSONArray("sessions") ?: JSONArray()).objects().map(HermesSession::fromJson)
            .filterNot { it.isInternalRun }
        return SessionListPage(
            sessions = rows,
            offset = root.optInt("offset", offset),
            limit = root.optInt("limit", limit),
            total = root.optInt("total", offset + rows.size)
        )
    }

    suspend fun sessions(
        profile: String = "all",
        limit: Int = 100,
        includeArchived: Boolean = false
    ): List<HermesSession> = sessionsPage(profile, limit, 0, includeArchived).sessions

    suspend fun searchSessions(query: String, profile: String = "all"): List<HermesSession> {
        if (query.isBlank()) return emptyList()
        val profileParam = if (profile == "all") "" else "&profile=${enc(profile)}"
        val root = getObject("/api/sessions/search?q=${enc(query)}$profileParam")
        val results = root.optJSONArray("results") ?: root.optJSONArray("sessions") ?: JSONArray()
        return results.objects().mapNotNull { row ->
            val session = row.optJSONObject("session") ?: row
            val normalized = JSONObject(session.toString())
            if (!normalized.has("id") && row.has("session_id")) normalized.put("id", row.optString("session_id"))
            if (!normalized.has("title") && row.has("title")) normalized.put("title", row.optString("title"))
            row.optStringOrNull("snippet")?.let { normalized.put("preview", it) }
            if (!normalized.has("profile")) normalized.put("profile", row.optString("profile", "default"))
            HermesSession.fromJson(normalized).takeIf { it.id.isNotBlank() }
        }
    }

    suspend fun sessionMessagesPage(
        sessionId: String,
        profile: String,
        limit: Int = 120,
        offset: Int = 0,
        order: String = "latest"
    ): SessionMessagePage {
        val path = "/api/sessions/${enc(sessionId)}/messages?profile=${enc(profile)}" +
            "&limit=$limit&offset=$offset&order=${enc(order)}&inline_images=false&include_compacted=true"
        val root = getObject(path)
        val rawMessages = (root.optJSONArray("messages") ?: root.optJSONArray("data") ?: JSONArray()).objects()
        val messages = rawMessages.map(ChatMessage::fromJson)
            .filter { it.renderable }
        val pagination = root.optJSONObject("pagination") ?: JSONObject()
        return SessionMessagePage(
            messages = messages,
            offset = pagination.optInt("offset", offset),
            returned = pagination.optInt("returned", rawMessages.size),
            limit = pagination.optInt("limit", limit),
            order = pagination.optString("order", order)
        )
    }

    suspend fun sessionMessages(
        sessionId: String,
        profile: String,
        limit: Int = 120
    ): List<ChatMessage> = sessionMessagesPage(sessionId, profile, limit).messages

    suspend fun patchSession(sessionId: String, profile: String, changes: JSONObject): JSONObject {
        changes.put("profile", profile)
        return requestObject("/api/sessions/${enc(sessionId)}", "PATCH", changes)
    }

    suspend fun deleteSession(sessionId: String, profile: String): JSONObject =
        requestObject("/api/sessions/${enc(sessionId)}?profile=${enc(profile)}", "DELETE")

    /** Names of the dashboard plugins active for [profile]; hidden and disabled ones are left out server-side. */
    suspend fun activeDashboardPlugins(profile: String): Set<String> =
        (requestObject("/api/dashboard/plugins?profile=${enc(profile)}", "GET").optJSONArray("items") ?: JSONArray())
            .objects().map { it.optString("name") }.filter { it.isNotBlank() }.toSet()

    /** Kanban plugin routes take the board as `?board=`; [scopedApi] adds the profile. */
    suspend fun kanbanBoards(profile: String): KanbanBoards =
        KanbanBoards.fromJson(scopedApi(profile, "/api/plugins/kanban/boards"))

    suspend fun kanbanBoard(profile: String, board: String): KanbanBoardSnapshot =
        KanbanBoardSnapshot.fromJson(scopedApi(profile, "/api/plugins/kanban/board?board=${enc(board)}"))

    suspend fun kanbanTask(profile: String, board: String, taskId: String): KanbanTaskDetail =
        KanbanTaskDetail.fromJson(scopedApi(profile, "/api/plugins/kanban/tasks/${enc(taskId)}?board=${enc(board)}"))
            ?: throw IllegalStateException("The gateway returned no task")

    /** Desktop's unblock: a blocked card goes back to Ready. */
    suspend fun kanbanUnblock(profile: String, board: String, taskId: String): JSONObject =
        kanbanMoveTask(profile, board, taskId, "ready")

    /** Desktop's move: the card's status is set to [status], the column it lands in. */
    suspend fun kanbanMoveTask(profile: String, board: String, taskId: String, status: String): JSONObject =
        scopedApi(profile, "/api/plugins/kanban/tasks/${enc(taskId)}?board=${enc(board)}", "PATCH", JSONObject().put("status", status))

    /** Releases a running worker's claim; the card returns to the queue. */
    suspend fun kanbanReclaim(profile: String, board: String, taskId: String): JSONObject =
        scopedApi(profile, "/api/plugins/kanban/tasks/${enc(taskId)}/reclaim?board=${enc(board)}", "POST", JSONObject())

    suspend fun cronJobs(profile: String = "all"): List<CronJob> {
        val raw = request("/api/cron/jobs?profile=${enc(profile)}", "GET")
        val arr = when (raw) {
            is JSONArray -> raw
            is JSONObject -> raw.optJSONArray("jobs") ?: raw.optJSONArray("items") ?: JSONArray()
            else -> JSONArray()
        }
        return arr.objects().map(CronJob::fromJson)
    }

    suspend fun cronJob(profile: String, id: String): CronJob = CronJob.fromJson(
        requestObject("/api/cron/jobs/${enc(id)}?profile=${enc(profile)}", "GET")
    ).copy(profile = profile)

    suspend fun cronRuns(profile: String, id: String, limit: Int = 20): List<CronRun> {
        val raw = request("/api/cron/jobs/${enc(id)}/runs?profile=${enc(profile)}&limit=$limit", "GET")
        val arr = when (raw) {
            is JSONArray -> raw
            is JSONObject -> raw.optJSONArray("runs") ?: raw.optJSONArray("sessions") ?: raw.optJSONArray("items") ?: JSONArray()
            else -> JSONArray()
        }
        return arr.objects().map(CronRun::fromJson)
    }

    suspend fun cronDeliveryTargets(profile: String): List<CronDeliveryTarget> {
        val raw = request("/api/cron/delivery-targets?profile=${enc(profile)}", "GET")
        val arr = when (raw) {
            is JSONArray -> raw
            is JSONObject -> raw.optJSONArray("targets") ?: raw.optJSONArray("delivery_targets") ?: raw.optJSONArray("items") ?: JSONArray()
            else -> JSONArray()
        }
        val parsed = arr.objects().map(CronDeliveryTarget::fromJson).filter { it.id.isNotBlank() }
        return if (parsed.any { it.id == "local" }) parsed else listOf(CronDeliveryTarget("local", "Local")) + parsed
    }

    suspend fun cronBlueprints(profile: String): List<AutomationBlueprint> {
        val raw = request("/api/cron/blueprints?profile=${enc(profile)}", "GET")
        val arr = when (raw) {
            is JSONArray -> raw
            is JSONObject -> raw.optJSONArray("blueprints") ?: raw.optJSONArray("items") ?: JSONArray()
            else -> JSONArray()
        }
        return arr.objects().map(AutomationBlueprint::fromJson).filter { it.id.isNotBlank() }
    }

    suspend fun instantiateCronBlueprint(
        profile: String,
        blueprint: String,
        values: Map<String, String>
    ): CronJob {
        val body = JSONObject().put("blueprint", blueprint).put("values", JSONObject(values))
        val root = requestObject("/api/cron/blueprints/instantiate?profile=${enc(profile)}", "POST", body)
        return CronJob.fromJson(root.optJSONObject("job") ?: root).copy(profile = profile)
    }

    suspend fun createCron(profile: String, draft: CronDraft): CronJob {
        val root = requestObject("/api/cron/jobs?profile=${enc(profile)}", "POST", draft.toJson())
        return CronJob.fromJson(root.optJSONObject("job") ?: root).copy(profile = profile)
    }

    suspend fun updateCron(profile: String, id: String, draft: CronDraft): CronJob {
        val root = requestObject(
            "/api/cron/jobs/${enc(id)}?profile=${enc(profile)}",
            "PUT",
            JSONObject().put("updates", draft.toJson(includeClears = true))
        )
        return CronJob.fromJson(root.optJSONObject("job") ?: root).copy(profile = profile)
    }

    suspend fun pauseCron(profile: String, id: String): CronJob = CronJob.fromJson(
        requestObject("/api/cron/jobs/${enc(id)}/pause?profile=${enc(profile)}", "POST", JSONObject())
    ).copy(profile = profile)

    suspend fun resumeCron(profile: String, id: String): CronJob = CronJob.fromJson(
        requestObject("/api/cron/jobs/${enc(id)}/resume?profile=${enc(profile)}", "POST", JSONObject())
    ).copy(profile = profile)

    suspend fun triggerCron(profile: String, id: String): CronJob {
        val root = requestObject("/api/cron/jobs/${enc(id)}/trigger?profile=${enc(profile)}", "POST", JSONObject())
        return CronJob.fromJson(root.optJSONObject("job") ?: root).copy(profile = profile)
    }

    suspend fun deleteCron(profile: String, id: String): JSONObject =
        requestObject("/api/cron/jobs/${enc(id)}?profile=${enc(profile)}", "DELETE")


    // -----------------------------------------------------------------------
    // Capabilities / global settings (profile-scoped remote management)
    // -----------------------------------------------------------------------

    suspend fun capabilityModelInfo(profile: String): CapabilityModelInfo = CapabilityModelInfo.fromJson(
        requestObject("/api/model/info?profile=${enc(profile)}", "GET")
    )

    suspend fun capabilityModelOptions(profile: String, refresh: Boolean = false): List<ModelProviderOption> {
        val path = "/api/model/options?profile=${enc(profile)}&include_unconfigured=1&explicit_only=0" +
            if (refresh) "&refresh=1" else ""
        val root = requestObject(path, "GET", readTimeoutSeconds = 60)
        return (root.optJSONArray("providers") ?: root.optJSONArray("items") ?: JSONArray()).objects()
            .map(ModelProviderOption::fromJson).filter { it.slug.isNotBlank() }
    }

    suspend fun setCapabilityModel(profile: String, provider: String, model: String, confirmExpensive: Boolean = false): JSONObject =
        requestObject(
            "/api/model/set?profile=${enc(profile)}",
            "POST",
            JSONObject().put("scope", "main").put("provider", provider).put("model", model).apply {
                if (confirmExpensive) put("confirm_expensive_model", true)
            }
        )

    suspend fun capabilitySkills(profile: String): List<CapabilitySkill> {
        val raw = request("/api/skills?profile=${enc(profile)}", "GET")
        val rows = when (raw) {
            is JSONArray -> raw
            is JSONObject -> raw.optJSONArray("skills") ?: raw.optJSONArray("items") ?: JSONArray()
            else -> JSONArray()
        }
        return rows.objects().map { CapabilitySkill.fromJson(it, installed = true) }.filter { it.name.isNotBlank() }
    }

    suspend fun setCapabilitySkillEnabled(profile: String, name: String, enabled: Boolean): JSONObject =
        requestObject(
            "/api/skills/toggle?profile=${enc(profile)}",
            "PUT",
            JSONObject().put("name", name).put("enabled", enabled).put("profile", profile)
        )

    suspend fun capabilitySkillContent(profile: String, name: String): JSONObject =
        requestObject("/api/skills/content?name=${enc(name)}&profile=${enc(profile)}", "GET")

    suspend fun capabilityOfficialSkills(profile: String): List<CapabilitySkill> {
        val root = requestObject("/api/skills/hub/official?profile=${enc(profile)}", "GET", readTimeoutSeconds = 45)
        return (root.optJSONArray("skills") ?: root.optJSONArray("items") ?: JSONArray()).objects()
            .map { CapabilitySkill.fromJson(it, installed = it.optBoolean("installed", false)) }
            .filter { it.name.isNotBlank() }
    }

    suspend fun searchCapabilitySkills(profile: String, query: String, limit: Int = 30): List<CapabilitySkill> {
        val root = requestObject(
            "/api/skills/hub/search?q=${enc(query)}&source=all&limit=$limit&profile=${enc(profile)}",
            "GET",
            readTimeoutSeconds = 45
        )
        val rows = root.optJSONArray("skills") ?: root.optJSONArray("results") ?: root.optJSONArray("items") ?: JSONArray()
        return rows.objects().map { CapabilitySkill.fromJson(it, installed = it.optBoolean("installed", false)) }
            .filter { it.name.isNotBlank() }
    }

    suspend fun installCapabilitySkill(profile: String, identifier: String): JSONObject =
        requestObject(
            "/api/skills/hub/install?profile=${enc(profile)}",
            "POST",
            JSONObject().put("identifier", identifier).put("profile", profile),
            readTimeoutSeconds = 60
        )

    suspend fun uninstallCapabilitySkill(profile: String, name: String): JSONObject =
        requestObject(
            "/api/skills/hub/uninstall?profile=${enc(profile)}",
            "POST",
            JSONObject().put("name", name).put("profile", profile),
            readTimeoutSeconds = 60
        )

    suspend fun updateCapabilitySkills(profile: String): JSONObject =
        requestObject(
            "/api/skills/hub/update?profile=${enc(profile)}",
            "POST",
            JSONObject().put("profile", profile),
            readTimeoutSeconds = 60
        )

    suspend fun capabilityToolsets(profile: String): List<CapabilityToolset> {
        val raw = request("/api/tools/toolsets?profile=${enc(profile)}", "GET")
        val rows = when (raw) {
            is JSONArray -> raw
            is JSONObject -> raw.optJSONArray("toolsets") ?: raw.optJSONArray("items") ?: JSONArray()
            else -> JSONArray()
        }
        return rows.objects().map(CapabilityToolset::fromJson).filter { it.name.isNotBlank() }
    }

    suspend fun setCapabilityToolsetEnabled(profile: String, name: String, enabled: Boolean): JSONObject =
        requestObject(
            "/api/tools/toolsets/${enc(name)}?profile=${enc(profile)}",
            "PUT",
            JSONObject().put("enabled", enabled).put("profile", profile)
        )

    suspend fun capabilityToolsetConfig(profile: String, name: String): CapabilityToolsetConfig =
        CapabilityToolsetConfig.fromJson(
            requestObject("/api/tools/toolsets/${enc(name)}/config?profile=${enc(profile)}", "GET")
        )

    suspend fun selectCapabilityToolsetProvider(profile: String, name: String, provider: String): JSONObject =
        requestObject(
            "/api/tools/toolsets/${enc(name)}/provider?profile=${enc(profile)}",
            "PUT",
            JSONObject().put("provider", provider).put("profile", profile)
        )

    suspend fun saveCapabilityToolsetEnv(profile: String, name: String, env: Map<String, String>): JSONObject =
        requestObject(
            "/api/tools/toolsets/${enc(name)}/env?profile=${enc(profile)}",
            "PUT",
            JSONObject().put("env", JSONObject(env)).put("profile", profile)
        )

    suspend fun capabilityMcpServers(profile: String): List<CapabilityMcpServer> {
        val root = requestObject("/api/mcp/servers?profile=${enc(profile)}", "GET")
        return (root.optJSONArray("servers") ?: root.optJSONArray("items") ?: JSONArray()).objects()
            .map(CapabilityMcpServer::fromJson).filter { it.name.isNotBlank() }
    }

    suspend fun addCapabilityMcpServer(
        profile: String,
        name: String,
        url: String = "",
        command: String = "",
        args: List<String> = emptyList(),
        env: Map<String, String> = emptyMap(),
        auth: String = ""
    ): JSONObject {
        val body = JSONObject().put("name", name).put("profile", profile)
        if (url.isNotBlank()) body.put("url", url)
        if (command.isNotBlank()) body.put("command", command)
        if (args.isNotEmpty()) body.put("args", JSONArray(args))
        if (env.isNotEmpty()) body.put("env", JSONObject(env))
        if (auth.isNotBlank()) body.put("auth", auth)
        return requestObject("/api/mcp/servers?profile=${enc(profile)}", "POST", body, readTimeoutSeconds = 60)
    }

    suspend fun removeCapabilityMcpServer(profile: String, name: String): JSONObject =
        requestObject("/api/mcp/servers/${enc(name)}?profile=${enc(profile)}", "DELETE")

    suspend fun setCapabilityMcpServerEnabled(profile: String, name: String, enabled: Boolean): JSONObject =
        requestObject(
            "/api/mcp/servers/${enc(name)}/enabled?profile=${enc(profile)}",
            "PUT",
            JSONObject().put("enabled", enabled).put("profile", profile)
        )

    suspend fun testCapabilityMcpServer(profile: String, name: String): JSONObject =
        requestObject(
            "/api/mcp/servers/${enc(name)}/test?profile=${enc(profile)}",
            "POST",
            JSONObject().put("profile", profile),
            readTimeoutSeconds = 60
        )

    suspend fun capabilityMcpCatalog(profile: String): List<CapabilityMcpCatalogEntry> {
        val root = requestObject("/api/mcp/catalog?profile=${enc(profile)}", "GET", readTimeoutSeconds = 30)
        val rows = root.optJSONArray("entries") ?: root.optJSONArray("servers") ?: JSONArray()
        return rows.objects().map(CapabilityMcpCatalogEntry::fromJson).filter { it.name.isNotBlank() }
    }

    suspend fun installCapabilityMcpCatalog(
        profile: String,
        name: String,
        env: Map<String, String> = emptyMap()
    ): JSONObject = requestObject(
        "/api/mcp/catalog/install?profile=${enc(profile)}",
        "POST",
        JSONObject().put("name", name).put("env", JSONObject(env)).put("enable", true).put("profile", profile),
        readTimeoutSeconds = 60
    )

    suspend fun capabilityEnvVars(profile: String): List<CapabilityEnvVar> {
        val root = requestObject("/api/env?profile=${enc(profile)}", "GET")
        return buildList {
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                root.optJSONObject(key)?.let { add(CapabilityEnvVar.fromJson(key, it)) }
            }
        }.sortedWith(compareBy<CapabilityEnvVar> { it.providerLabel.ifBlank { it.category } }.thenBy { it.key })
    }

    suspend fun setCapabilityEnvVar(profile: String, key: String, value: String, providerSetup: Boolean = false): JSONObject =
        requestObject(
            "/api/env?profile=${enc(profile)}",
            "PUT",
            JSONObject().put("key", key).put("value", value).put("profile", profile).apply {
                if (providerSetup) put("provider_setup", true)
            }
        )

    suspend fun deleteCapabilityEnvVar(profile: String, key: String): JSONObject =
        requestObject(
            "/api/env?profile=${enc(profile)}",
            "DELETE",
            JSONObject().put("key", key).put("profile", profile)
        )

    suspend fun validateCapabilityProviderCredential(
        profile: String,
        key: String,
        value: String,
        apiKey: String = ""
    ): JSONObject = requestObject(
        "/api/providers/validate?profile=${enc(profile)}",
        "POST",
        JSONObject().put("key", key).put("value", value).put("api_key", apiKey).put("profile", profile),
        readTimeoutSeconds = 45
    )

    suspend fun capabilityCustomEndpoints(profile: String): List<CapabilityCustomEndpoint> {
        val root = requestObject("/api/providers/custom-endpoints?profile=${enc(profile)}", "GET")
        return (root.optJSONArray("endpoints") ?: JSONArray()).objects()
            .map(CapabilityCustomEndpoint::fromJson).filter { it.id.isNotBlank() }
    }

    suspend fun validateCapabilityCustomEndpoint(
        profile: String,
        name: String,
        baseUrl: String,
        model: String,
        apiKey: String = "",
        apiMode: String = "",
        discoverModels: Boolean = true
    ): JSONObject = requestObject(
        "/api/providers/custom-endpoints/validate?profile=${enc(profile)}",
        "POST",
        JSONObject()
            .put("name", name).put("base_url", baseUrl).put("model", model)
            .put("api_key", apiKey).put("api_mode", apiMode).put("discover_models", discoverModels),
        readTimeoutSeconds = 45
    )

    suspend fun saveCapabilityCustomEndpoint(
        profile: String,
        id: String? = null,
        name: String,
        baseUrl: String,
        model: String,
        apiKey: String = "",
        apiMode: String = "",
        discoverModels: Boolean = true,
        makeDefault: Boolean = false
    ): JSONObject = requestObject(
        "/api/providers/custom-endpoints?profile=${enc(profile)}",
        "POST",
        JSONObject().apply {
            id?.takeIf(String::isNotBlank)?.let { put("id", it) }
            put("name", name).put("base_url", baseUrl).put("model", model)
            if (apiKey.isNotBlank()) put("api_key", apiKey)
            put("api_mode", apiMode).put("discover_models", discoverModels).put("make_default", makeDefault)
        },
        readTimeoutSeconds = 45
    )

    suspend fun activateCapabilityCustomEndpoint(profile: String, id: String): JSONObject =
        requestObject("/api/providers/custom-endpoints/${enc(id)}/activate?profile=${enc(profile)}", "POST", JSONObject())

    suspend fun deleteCapabilityCustomEndpoint(profile: String, id: String): JSONObject =
        requestObject("/api/providers/custom-endpoints/${enc(id)}?profile=${enc(profile)}", "DELETE")

    suspend fun capabilityOAuthProviders(profile: String): List<CapabilityOAuthProvider> {
        val root = requestObject("/api/providers/oauth?profile=${enc(profile)}", "GET")
        return (root.optJSONArray("providers") ?: JSONArray()).objects()
            .map(CapabilityOAuthProvider::fromJson).filter { it.id.isNotBlank() }
    }

    suspend fun startCapabilityOAuth(profile: String, providerId: String): CapabilityOAuthFlow {
        val root = requestObject(
            "/api/providers/oauth/${enc(providerId)}/start?profile=${enc(profile)}",
            "POST",
            JSONObject().put("profile", profile),
            readTimeoutSeconds = 45
        )
        return CapabilityOAuthFlow(
            providerId = providerId,
            sessionId = root.optString("session_id"),
            flow = root.optString("flow"),
            authUrl = root.optString("auth_url"),
            verificationUrl = root.optString("verification_url"),
            userCode = root.optString("user_code"),
            expiresIn = root.optInt("expires_in", 900),
            pollInterval = root.optInt("poll_interval", 5).coerceAtLeast(2)
        )
    }

    suspend fun pollCapabilityOAuth(profile: String, flow: CapabilityOAuthFlow): JSONObject =
        requestObject(
            "/api/providers/oauth/${enc(flow.providerId)}/poll/${enc(flow.sessionId)}?profile=${enc(profile)}",
            "GET",
            readTimeoutSeconds = 30
        )

    suspend fun submitCapabilityOAuthCode(profile: String, flow: CapabilityOAuthFlow, code: String): JSONObject =
        requestObject(
            "/api/providers/oauth/${enc(flow.providerId)}/submit?profile=${enc(profile)}",
            "POST",
            JSONObject().put("session_id", flow.sessionId).put("code", code).put("profile", profile),
            readTimeoutSeconds = 45
        )

    suspend fun disconnectCapabilityOAuth(profile: String, providerId: String): JSONObject =
        requestObject("/api/providers/oauth/${enc(providerId)}?profile=${enc(profile)}", "DELETE")

    suspend fun messagingPlatforms(profile: String = "default"): List<MessagingPlatform> {
        val raw = request("/api/messaging/platforms?profile=${enc(profile)}", "GET")
        val arr = when (raw) {
            is JSONArray -> raw
            is JSONObject -> raw.optJSONArray("platforms") ?: raw.optJSONArray("items")
            else -> null
        }
        if (arr != null) return arr.objects().map(MessagingPlatform::fromJson)

        // Older/plugin-heavy gateways may expose a map keyed by platform id.
        val platformObject = (raw as? JSONObject)?.optJSONObject("platforms") ?: (raw as? JSONObject)
        if (platformObject == null) return emptyList()
        return buildList {
            val keys = platformObject.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val item = platformObject.optJSONObject(id) ?: continue
                add(MessagingPlatform.fromJson(JSONObject(item.toString()).put("id", id)))
            }
        }
    }

    suspend fun updateMessagingPlatform(
        profile: String,
        platform: String,
        enabled: Boolean? = null,
        env: Map<String, String> = emptyMap(),
        clearEnv: List<String> = emptyList()
    ): JSONObject {
        val body = JSONObject()
        enabled?.let { body.put("enabled", it) }
        if (env.isNotEmpty()) body.put("env", JSONObject(env))
        if (clearEnv.isNotEmpty()) body.put("clear_env", JSONArray(clearEnv))
        return requestObject(
            "/api/messaging/platforms/${enc(platform)}?profile=${enc(profile)}",
            "PUT",
            body
        )
    }

    suspend fun testMessagingPlatform(profile: String, platform: String): JSONObject =
        requestObject(
            "/api/messaging/platforms/${enc(platform)}/test?profile=${enc(profile)}",
            "POST",
            JSONObject()
        )

    suspend fun restartGateway(profile: String): JSONObject =
        requestObject("/api/gateway/restart?profile=${enc(profile)}", "POST", JSONObject())

    suspend fun pairing(profile: String): Pair<List<PairingUser>, List<PairingUser>> {
        val root = getObject("/api/pairing?profile=${enc(profile)}")
        fun parse(array: JSONArray?, pending: Boolean) = (array ?: JSONArray()).objects().map {
            PairingUser(
                platform = it.optString("platform"),
                userId = it.optString("user_id"),
                userName = it.optString("user_name").ifBlank { it.optString("username") },
                requestId = it.optStringOrNull("request_id"),
                code = it.optStringOrNull("code"),
                createdAt = it.optString("created_at").ifBlank { it.optString("age") },
                pending = pending
            )
        }
        return parse(root.optJSONArray("pending"), true) to parse(root.optJSONArray("approved"), false)
    }

    suspend fun approvePairing(profile: String, user: PairingUser): JSONObject {
        val body = JSONObject().put("platform", user.platform)
        when {
            !user.code.isNullOrBlank() -> body.put("code", user.code)
            !user.requestId.isNullOrBlank() -> body.put("request_id", user.requestId)
            else -> error("Pairing request does not contain a code or request id")
        }
        return requestObject("/api/pairing/approve?profile=${enc(profile)}", "POST", body)
    }

    suspend fun revokePairing(profile: String, platform: String, userId: String): JSONObject =
        requestObject(
            "/api/pairing/revoke?profile=${enc(profile)}",
            "POST",
            JSONObject().put("platform", platform).put("user_id", userId)
        )

    suspend fun clearPendingPairing(profile: String): JSONObject =
        requestObject("/api/pairing/clear-pending?profile=${enc(profile)}", "POST", JSONObject())

    suspend fun webhooks(profile: String): WebhookState {
        val root = getObject("/api/webhooks?profile=${enc(profile)}")
        val arr = root.optJSONArray("routes") ?: root.optJSONArray("subscriptions") ?: root.optJSONArray("webhooks")
        val routes = if (arr != null) {
            arr.objects().map(WebhookRoute::fromJson)
        } else {
            val routeObject = root.optJSONObject("routes") ?: root.optJSONObject("subscriptions") ?: root.optJSONObject("webhooks")
            if (routeObject == null) emptyList() else buildList {
                val keys = routeObject.keys()
                while (keys.hasNext()) {
                    val name = keys.next()
                    val item = routeObject.optJSONObject(name) ?: continue
                    add(WebhookRoute.fromJson(JSONObject(item.toString()).put("name", name)))
                }
            }
        }
        return WebhookState(
            platformEnabled = root.optBoolean("platform_enabled", root.optBoolean("enabled", false)),
            routes = routes
        )
    }

    suspend fun createWebhook(profile: String, draft: WebhookDraft): JSONObject =
        requestObject("/api/webhooks?profile=${enc(profile)}", "POST", draft.toJson())

    suspend fun setWebhookEnabled(profile: String, name: String, enabled: Boolean): JSONObject =
        requestObject(
            "/api/webhooks/${enc(name)}/enabled?profile=${enc(profile)}",
            "PUT",
            JSONObject().put("enabled", enabled)
        )

    suspend fun deleteWebhook(profile: String, name: String): JSONObject =
        requestObject("/api/webhooks/${enc(name)}?profile=${enc(profile)}", "DELETE")

    suspend fun startTelegramOnboarding(profile: String, botName: String = ""): TelegramOnboarding {
        val body = JSONObject().apply { if (botName.isNotBlank()) put("bot_name", botName) }
        val o = requestObject("/api/messaging/telegram/onboarding/start?profile=${enc(profile)}", "POST", body)
        return TelegramOnboarding(
            pairingId = o.optString("pairing_id"),
            deepLink = o.optString("deep_link"),
            qrPayload = o.optString("qr_payload"),
            suggestedUsername = o.optString("suggested_username"),
            expiresAt = o.optString("expires_at")
        )
    }

    suspend fun telegramOnboardingStatus(profile: String, pairingId: String): TelegramOnboarding {
        val o = getObject("/api/messaging/telegram/onboarding/${enc(pairingId)}?profile=${enc(profile)}")
        return TelegramOnboarding(
            pairingId = pairingId,
            deepLink = "",
            qrPayload = "",
            suggestedUsername = "",
            expiresAt = o.optString("expires_at"),
            status = o.optString("status", "waiting"),
            botUsername = o.optStringOrNull("bot_username"),
            ownerUserId = o.optStringOrNull("owner_user_id")
        )
    }

    suspend fun applyTelegramOnboarding(
        profile: String,
        pairingId: String,
        allowedUserIds: List<String>
    ): JSONObject = requestObject(
        "/api/messaging/telegram/onboarding/${enc(pairingId)}/apply?profile=${enc(profile)}",
        "POST",
        JSONObject().put("allowed_user_ids", JSONArray(allowedUserIds))
    )

    suspend fun cancelTelegramOnboarding(profile: String, pairingId: String): JSONObject =
        requestObject("/api/messaging/telegram/onboarding/${enc(pairingId)}?profile=${enc(profile)}", "DELETE")

    /** Only relative gateway routes; ownership is captured by the caller before any await. */
    suspend fun scopedApi(profile: String, path: String, method: String = "GET", body: JSONObject? = null, timeout: Long = 60): JSONObject {
        require(profile.isNotBlank() && profile != "all") { "Choose a profile" }
        require(path.startsWith("/api/") && !path.contains("#"))
        return requestObject(path + (if ('?' in path) "&" else "?") + "profile=" + enc(profile), method, body, timeout)
    }

    private fun authorizedBuilder(path: String): Request.Builder {
        require(path.startsWith("/api/"))
        val current = config
        return Request.Builder().url(baseUrl + path).apply {
            when {
                current.nativeTokens.accessToken.isNotBlank() -> header("Authorization", "Bearer ${current.nativeTokens.accessToken}")
                current.cookieHeader.isNotBlank() -> header("Cookie", current.cookieHeader)
                current.token.isNotBlank() -> header("X-Hermes-Session-Token", current.token)
            }
            current.extraHeaders.forEach { (key, value) -> if (!isManagedHeader(key) && value.isNotBlank()) header(key, value) }
        }
    }

    /** Bounded, streaming download; no tokens in URLs/intents, no redirects to other origins. */
    suspend fun downloadFile(profile: String, path: String, sessionId: String?, output: java.io.File, managed: Boolean = false, maxBytes: Long = 64L * 1024 * 1024): String {
        val route = (if (managed) "/api/files/download" else "/api/fs/download") + "?path=${enc(path)}&profile=${enc(profile)}" +
            (sessionId?.let { "&session_id=${enc(it)}" } ?: "")
        ensureFreshNativeToken()
        for (attempt in 0..1) {
            val authSnapshot = config
            try {
                return withContext(Dispatchers.IO) {
                    client.newCall(authorizedBuilder(route).build()).execute().use { response ->
                        if (response.code == 401 && attempt == 0 && authSnapshot.nativeTokens.refreshToken.isNotBlank()) throw NativeRefreshRequiredException(authSnapshot)
                        if (!response.isSuccessful) throw HermesHttpException(response.code, "Download failed (HTTP ${response.code})")
                        require(response.body.contentLength() <= maxBytes) { "Mobile preview/download size limit exceeded" }
                        response.body.byteStream().use { source -> output.outputStream().use { sink ->
                            val buffer = ByteArray(32 * 1024)
                            var total = 0L
                            while (true) {
                                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                val n = source.read(buffer)
                                if (n < 0) break
                                total += n
                                require(total <= maxBytes) { "Mobile preview/download size limit exceeded" }
                                sink.write(buffer, 0, n)
                            }
                        } }
                        response.header("Content-Type")?.substringBefore(';') ?: "application/octet-stream"
                    }
                }
            } catch (_: NativeRefreshRequiredException) { refreshNativeOrThrow(authSnapshot) }
            catch (t: Throwable) { output.delete(); if (isTerminalAuthFailure(t)) onAuthExpired("Hermes sign-in has expired"); throw t }
        }
        error("Download authentication failed")
    }

    suspend fun uploadFile(profile: String, path: String, file: java.io.File, mime: String): JSONObject {
        require(file.length() <= 64L * 1024 * 1024) { "Mobile upload limit: 64 MB" }
        ensureFreshNativeToken()
        for (attempt in 0..1) {
            val authSnapshot = config
            try {
                return withContext(Dispatchers.IO) {
                    val body = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
                        .addFormDataPart("path", path).addFormDataPart("overwrite", "false")
                        .addFormDataPart("file", file.name, file.asRequestBody(mime.toMediaType())).build()
                    client.newCall(authorizedBuilder("/api/files/upload-stream?profile=${enc(profile)}").post(body).build()).execute().use { response ->
                        if (response.code == 401 && attempt == 0 && authSnapshot.nativeTokens.refreshToken.isNotBlank()) throw NativeRefreshRequiredException(authSnapshot)
                        if (!response.isSuccessful) throw HermesHttpException(response.code, "Upload failed (HTTP ${response.code}); existing files are preserved")
                        JSONObject(response.body.string())
                    }
                }
            } catch (_: NativeRefreshRequiredException) { refreshNativeOrThrow(authSnapshot) }
            catch (e: HermesHttpException) {
                if (e.status in listOf(404,405,501)) {
                    require(file.length() <= 8L * 1024 * 1024) { "This gateway only supports JSON uploads; mobile compatibility limit: 8 MB" }
                    val data = withContext(Dispatchers.IO) { "data:$mime;base64," + java.util.Base64.getEncoder().encodeToString(file.readBytes()) }
                    return scopedApi(profile,"/api/files/upload","POST",JSONObject().put("path",path).put("data_url",data).put("overwrite",false),120)
                }
                if (isTerminalAuthFailure(e)) onAuthExpired("Hermes sign-in has expired")
                throw e
            }
        }
        error("Upload authentication failed")
    }

    private suspend fun getObject(path: String): JSONObject = requestObject(path, "GET")

    private suspend fun requestObject(
        path: String,
        method: String,
        body: JSONObject? = null,
        readTimeoutSeconds: Long? = null
    ): JSONObject {
        val raw = request(path, method, body, readTimeoutSeconds)
        return when (raw) {
            is JSONObject -> raw
            is JSONArray -> JSONObject().put("items", raw)
            else -> JSONObject().put("value", raw)
        }
    }

    private suspend fun request(
        path: String,
        method: String,
        body: JSONObject? = null,
        readTimeoutSeconds: Long? = null
    ): Any {
        return try {
            ensureFreshNativeToken()
            try {
                executeRequest(path, method, body, readTimeoutSeconds, allowRefreshRetry = true)
            } catch (refresh: NativeRefreshRequiredException) {
                refreshNativeOrThrow(refresh.original)
                executeRequest(path, method, body, readTimeoutSeconds, allowRefreshRetry = false)
            }
        } catch (t: Throwable) {
            if (isTerminalAuthFailure(t)) onAuthExpired(t.message ?: "Hermes sign-in has expired")
            throw t
        }
    }

    private suspend fun executeRequest(
        path: String,
        method: String,
        body: JSONObject?,
        readTimeoutSeconds: Long?,
        allowRefreshRetry: Boolean
    ): Any = withContext(Dispatchers.IO) {
        val requestClient = if (readTimeoutSeconds != null) {
            client.newBuilder().readTimeout(readTimeoutSeconds, TimeUnit.SECONDS).build()
        } else client
        val current = config
        val builder = Request.Builder().url(baseUrl + path).header("Accept", "application/json")
        when {
            current.nativeTokens.accessToken.isNotBlank() -> builder.header("Authorization", "Bearer ${current.nativeTokens.accessToken}")
            current.cookieHeader.isNotBlank() -> builder.header("Cookie", current.cookieHeader)
            current.token.isNotBlank() -> builder.header("X-Hermes-Session-Token", current.token)
        }
        current.extraHeaders.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank() && !isManagedHeader(k)) builder.header(k, v)
        }
        val requestBody = (body ?: JSONObject()).toString().toRequestBody(jsonType)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(requestBody)
            "PUT" -> builder.put(requestBody)
            "PATCH" -> builder.patch(requestBody)
            "DELETE" -> if (body == null) builder.delete() else builder.delete(requestBody)
            else -> error("Unsupported HTTP method: $method")
        }
        requestClient.newCall(builder.build()).execute().use { response ->
            mergeResponseCookies(response.headers.values("Set-Cookie"))
            val source = response.body.source()
            val responseLimit = (if (path.startsWith("/api/files/read?")) 4L else 48L) * 1024 * 1024
            require(!source.request(responseLimit + 1)) { "Gateway JSON response exceeds the mobile preview limit; use file download" }
            val text = source.readString(Charsets.UTF_8)
            if (!response.isSuccessful) {
                if (response.code == 401 && allowRefreshRetry && current.nativeTokens.refreshToken.isNotBlank()) {
                    // The refresh itself is performed outside this IO response block to avoid recursively
                    // using this same authenticated request path.
                    throw NativeRefreshRequiredException(current)
                }
                val detail = runCatching { JSONObject(text).optString("detail") }.getOrNull()
                throw HermesHttpException(response.code, detail?.ifBlank { text } ?: text.ifBlank { "HTTP ${response.code}" })
            }
            val trimmed = text.trim()
            when {
                trimmed.startsWith("{") -> JSONObject(trimmed)
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.isBlank() -> JSONObject().put("ok", true)
                else -> trimmed
            }
        }
    }.let { it }

    private suspend fun ensureFreshNativeToken() {
        val current = config
        val tokens = current.nativeTokens
        if (!tokens.usable || !tokens.needsRefresh()) return
        refreshNativeOrThrow(current)
    }

    private suspend fun refreshNativeOrThrow(current: GatewayConfig) = refreshMutex.withLock {
        // Rotating refresh tokens are single-use. A concurrent request may already have rotated it.
        if (config.nativeTokens != current.nativeTokens) return@withLock
        val refresh = current.nativeTokens.refreshToken
        if (refresh.isBlank()) throw HermesHttpException(401, "Hermes sign-in has expired; sign in again")
        val tokens = HermesAuthClient(current.baseUrl, current.extraHeaders).refreshNative(refresh)
        updateConfig(config.copy(nativeTokens = tokens, authProvider = tokens.provider.ifBlank { config.authProvider }))
    }

    private fun mergeResponseCookies(setCookies: List<String>) {
        if (setCookies.isEmpty()) return
        val incoming = setCookies.mapNotNull { raw ->
            raw.substringBefore(';').trim().takeIf { it.contains('=') }
        }
        if (incoming.isEmpty()) return
        val merged = linkedMapOf<String, String>()
        fun addCookie(pair: String) {
            val name = pair.substringBefore('=').trim()
            if (name.isNotBlank()) merged[name] = pair.substringAfter('=', "")
        }
        config.cookieHeader.split(';').map(String::trim).filter(String::isNotBlank).forEach(::addCookie)
        incoming.forEach(::addCookie)
        val value = merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
        if (value != config.cookieHeader) updateConfig(config.copy(cookieHeader = value))
    }

    @Synchronized
    private fun updateConfig(updated: GatewayConfig) {
        config = updated
        onConfigUpdated(updated)
    }

    private class NativeRefreshRequiredException(val original: GatewayConfig) : IOException()

    private fun isTerminalAuthFailure(t: Throwable): Boolean = when (t) {
        is HermesHttpException -> t.status == 401
        is GatewayAuthException -> t.status == 401
        else -> false
    }

    private fun isManagedHeader(name: String): Boolean = name.lowercase(Locale.ROOT) in setOf(
        "authorization", "cookie", "host", "origin", "referer", "x-hermes-session-token", "connection", "upgrade"
    )

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
