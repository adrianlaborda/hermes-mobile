package com.nousresearch.hermesmobile.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

class HermesRepository(
    val config: GatewayConfig,
    val rest: HermesRestClient,
    val gateway: HermesGatewayClient
) {
    private val canonicalBotLocks = ConcurrentHashMap<String, Mutex>()
    private val compatibility = GatewayCompatibility(gateway)

    suspend fun rpcProfiles(): List<HermesProfile> {
        val root = gateway.requestObject("profiles.list", JSONObject())
        val rows = root.optJSONArray("profiles") ?: root.optJSONArray("items") ?: JSONArray()
        return rows.objects().map(HermesProfile::fromJson)
    }

    suspend fun createSession(profile: String, title: String? = null, hidden: Boolean = false, cwd: String? = null): ActiveChat {
        val params = JSONObject()
            .put("profile", profile)
            .put("source", "mobile")
            .put("close_on_disconnect", false)
            .put("hidden", hidden)
        if (!title.isNullOrBlank()) params.put("title", title)
        if (!cwd.isNullOrBlank()) params.put("cwd", cwd)
        val result = gateway.requestObject("session.create", params)
        if (!cwd.isNullOrBlank()) {
            val actual = result.optJSONObject("info")?.optString("cwd").orEmpty()
            check(actual.trimEnd('/') == cwd.trimEnd('/')) { "The gateway did not confirm the requested workspace. No message was sent." }
        }
        return activeChatFromSnapshot(result, profile, title.orEmpty(), "mobile")
    }

    suspend fun resumeSession(storedId: String, profile: String, title: String = "", source: String = ""): ActiveChat {
        val result = compatibility.requestObject(
            method = "session.resume",
            required = JSONObject()
                .put("session_id", storedId)
                .put("profile", profile),
            optional = linkedMapOf(
                "source" to "mobile",
                "close_on_disconnect" to false,
                "defer_history" to true,
                "omit_messages" to true,
                "inline_images" to false
            ),
            timeoutMs = 60_000
        )
        val snapshot = activeChatFromSnapshot(result, profile, title, source)
        if (!gateway.awaitSessionReplay(snapshot.runtimeSessionId)) {
            error("Gateway connection changed while restoring the conversation")
        }
        val page = rest.sessionMessagesPage(snapshot.storedSessionId, profile, limit = 120, offset = 0, order = "latest")
        return snapshot.copy(
            messages = page.messages,
            hasOlderMessages = page.hasOlder,
            olderOffset = page.returned
        )
    }

    suspend fun submitPrompt(runtimeId: String, profile: String, text: String): PromptSubmitResult {
        val result = gateway.requestObject(
            "prompt.submit",
            JSONObject()
                .put("session_id", runtimeId)
                .put("profile", profile)
                .put("text", text)
                .put("surface", "mobile"),
            timeoutMs = 30_000
        )
        return PromptSubmitResult(
            status = result.optString("status"),
            userRowId = result.optLongOrNull("user_row_id")
        )
    }

    suspend fun attachImageBytes(runtimeId: String, profile: String, filename: String, bytes: ByteArray) {
        gateway.requestObject(
            "image.attach_bytes",
            JSONObject()
                .put("session_id", runtimeId)
                .put("profile", profile)
                .put("filename", filename)
                .put("content_base64", Base64.getEncoder().encodeToString(bytes)),
            timeoutMs = 60_000
        )
    }

    suspend fun attachPdfBytes(runtimeId: String, profile: String, filename: String, bytes: ByteArray) {
        gateway.requestObject(
            "pdf.attach",
            JSONObject()
                .put("session_id", runtimeId)
                .put("profile", profile)
                .put("filename", filename)
                .put("content_base64", Base64.getEncoder().encodeToString(bytes)),
            timeoutMs = 120_000
        )
    }

    suspend fun attachFileBytes(
        runtimeId: String,
        profile: String,
        filename: String,
        mimeType: String,
        bytes: ByteArray
    ): String {
        val encoded = Base64.getEncoder().encodeToString(bytes)
        val result = gateway.requestObject(
            "file.attach",
            JSONObject()
                .put("session_id", runtimeId)
                .put("profile", profile)
                .put("path", filename)
                .put("name", filename)
                .put("data_url", "data:${mimeType.ifBlank { "application/octet-stream" }};base64,$encoded"),
            timeoutMs = 90_000
        )
        return result.optString("ref_text").ifBlank {
            result.optString("ref_path").takeIf { it.isNotBlank() }?.let { "@file:$it" }.orEmpty()
        }
    }

    suspend fun sessionModelOptions(profile: String): List<SessionModelChoice> {
        val result = gateway.requestObject(
            "model.options",
            JSONObject()
                .put("profile", profile)
                .put("include_unconfigured", false)
                .put("explicit_only", false),
            timeoutMs = 20_000
        )
        val providers = result.optJSONArray("providers") ?: result.optJSONArray("items") ?: JSONArray()
        return buildList {
            for (i in 0 until providers.length()) {
                val p = providers.optJSONObject(i) ?: continue
                val provider = p.optString("slug").ifBlank { p.optString("provider").ifBlank { p.optString("name") } }
                val providerLabel = p.optString("name").ifBlank { provider }
                val models = p.optJSONArray("models") ?: JSONArray()
                for (j in 0 until models.length()) {
                    val raw = models.opt(j)
                    val id = when (raw) {
                        is String -> raw
                        is JSONObject -> raw.optString("id").ifBlank { raw.optString("name") }
                        else -> ""
                    }
                    if (provider.isNotBlank() && id.isNotBlank()) add(
                        SessionModelChoice(provider, id, "$providerLabel · $id",
                            p.optJSONObject("capabilities")?.optJSONObject(id)?.let { if (it.has("reasoning")) it.optBoolean("reasoning") else null },
                            p.optJSONObject("capabilities")?.optJSONObject(id)?.let { if (it.has("can_disable_reasoning")) it.optBoolean("can_disable_reasoning") else null })
                    )
                }
            }
        }.distinctBy { "${it.provider}:${it.model}" }
    }

    suspend fun switchSessionModel(
        runtimeId: String,
        profile: String,
        provider: String,
        model: String,
        reasoning: String = "",
        confirmExpensive: Boolean = false
    ): SessionModelSwitchResult {
        // Desktop uses config.set rather than slash.exec for session-scoped model changes.
        // This matters especially for a lazy, zero-message session: config.set writes the
        // live session override that the first prompt build consumes, while a slash worker
        // can update only transient CLI state and the first turn then falls back to the
        // profile default. Keep slash.exec only as a compatibility fallback for old gateways.
        val value = buildString {
            append(model.trim())
            if (provider.isNotBlank()) append(" --provider ").append(provider.trim())
            append(" --session")
        }
        val result = try {
            gateway.requestObject(
                "config.set",
                JSONObject()
                    .put("session_id", runtimeId)
                    .put("profile", profile)
                    .put("key", "model")
                    .put("value", value)
                    .apply { if (confirmExpensive) put("confirm_expensive_model", true) },
                timeoutMs = 30_000
            )
        } catch (error: GatewayRpcException) {
            if (error.code != -32601) throw error
            val command = buildString {
                append("/model ").append(slashQuote(model))
                if (provider.isNotBlank()) append(" --provider ").append(slashQuote(provider))
                append(" --session")
            }
            gateway.requestObject(
                "slash.exec",
                JSONObject().put("session_id", runtimeId).put("profile", profile).put("command", command),
                timeoutMs = 30_000
            )
        }
        val confirmRequired = result.optBoolean("confirm_required", false)
        if (!confirmRequired && reasoning.isNotBlank()) {
            setSessionReasoning(runtimeId, profile, reasoning)
        }
        return SessionModelSwitchResult(
            confirmRequired = confirmRequired,
            confirmMessage = result.optString("confirm_message"),
            deferred = result.optBoolean("deferred", false),
            provider = provider,
            model = result.optString("value").substringBefore(" --provider").ifBlank { model }
        )
    }

    suspend fun setSessionReasoning(runtimeId: String, profile: String, reasoning: String) {
        try {
            gateway.requestObject(
                "config.set",
                JSONObject()
                    .put("session_id", runtimeId)
                    .put("profile", profile)
                    .put("key", "reasoning")
                    .put("value", reasoning),
                timeoutMs = 30_000
            )
        } catch (error: GatewayRpcException) {
            if (error.code != -32601) throw error
            gateway.requestObject(
                "slash.exec",
                JSONObject()
                    .put("session_id", runtimeId)
                    .put("profile", profile)
                    .put("command", "/reasoning ${slashQuote(reasoning)}"),
                timeoutMs = 30_000
            )
        }
    }

    suspend fun sessionUsage(runtimeId: String, profile: String): SessionUsage =
        SessionUsage.from(
            gateway.requestObject(
                "session.usage",
                JSONObject().put("session_id", runtimeId).put("profile", profile),
                timeoutMs = 20_000
            )
        )

    suspend fun contextBreakdown(runtimeId: String, profile: String): ContextBreakdown =
        ContextBreakdown.from(
            gateway.requestObject(
                "session.context_breakdown",
                JSONObject().put("session_id", runtimeId).put("profile", profile),
                timeoutMs = 30_000
            )
        )

    private fun slashQuote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    suspend fun loadOlderMessages(chat: ActiveChat, limit: Int = 120): SessionMessagePage =
        rest.sessionMessagesPage(
            sessionId = chat.storedSessionId,
            profile = chat.profile,
            limit = limit,
            offset = chat.olderOffset,
            order = "latest"
        )

    suspend fun branchSession(parent: HermesSession): ActiveChat {
        val result = gateway.requestObject(
            "session.branch_stored",
            JSONObject()
                .put("parent_session_id", parent.id)
                .put("profile", parent.profile)
                .put("source", "mobile"),
            timeoutMs = 60_000
        )
        val snapshot = activeChatFromSnapshot(
            result,
            parent.profile,
            parent.title.takeIf { it.isNotBlank() }?.let { "$it (branch)" }.orEmpty(),
            "mobile"
        )
        val page = rest.sessionMessagesPage(
            snapshot.storedSessionId,
            parent.profile,
            limit = 120,
            offset = 0,
            order = "latest"
        )
        return snapshot.copy(
            messages = page.messages,
            hasOlderMessages = page.hasOlder,
            olderOffset = page.returned
        )
    }

    suspend fun compressSession(chat: ActiveChat, focusTopic: String? = null): ActiveChat {
        val params = JSONObject()
            .put("session_id", chat.runtimeSessionId)
            .put("profile", chat.profile)
        if (!focusTopic.isNullOrBlank()) params.put("focus_topic", focusTopic)
        val result = gateway.requestObject("session.compress", params, timeoutMs = 180_000)
        val status = result.optString("status")
        if (status == "aborted" || result.optBoolean("lock_held", false)) {
            error(result.optString("message").ifBlank { "Hermes could not compress this conversation" })
        }
        // Compression rotates the durable session key. Reopen through the old
        // lineage address so the backend resolves the current continuation tip.
        return resumeSession(chat.storedSessionId, chat.profile, chat.title, chat.source)
    }

    suspend fun interrupt(runtimeId: String, profile: String) {
        gateway.requestObject(
            "session.interrupt",
            JSONObject().put("session_id", runtimeId).put("profile", profile)
        )
    }

    /** Full `/` palette (commands by category + skills), filtered locally as the user types. */
    suspend fun commandsCatalog(runtimeId: String, profile: String): SlashCatalog =
        SlashCommands.parseCatalog(
            gateway.requestObject(
                "commands.catalog",
                JSONObject().put("session_id", runtimeId).put("profile", profile),
                timeoutMs = 20_000
            )
        )

    suspend fun completeSlash(runtimeId: String, profile: String, text: String): Pair<List<SlashCompletion>, Int> =
        SlashCommands.parseCompletions(
            gateway.requestObject(
                "complete.slash",
                JSONObject().put("session_id", runtimeId).put("profile", profile).put("text", text),
                timeoutMs = 15_000
            )
        )

    /** Runs a typed slash command like Desktop: slash.exec routes pending-input built-ins itself. */
    suspend fun execSlash(runtimeId: String, profile: String, command: String): SlashResult =
        SlashCommands.parseResult(
            gateway.requestObject(
                "slash.exec",
                JSONObject().put("session_id", runtimeId).put("profile", profile).put("command", command),
                timeoutMs = 120_000
            )
        )

    suspend fun dispatchCommand(runtimeId: String, profile: String, name: String, arg: String = ""): SlashResult =
        SlashCommands.parseResult(
            gateway.requestObject(
                "command.dispatch",
                JSONObject().put("session_id", runtimeId).put("profile", profile).put("name", name).put("arg", arg),
                timeoutMs = 60_000
            )
        )

    /** Re-reads the newest transcript page after the gateway rewound history (/retry, /undo). */
    suspend fun reloadTranscript(chat: ActiveChat): ActiveChat {
        val page = rest.sessionMessagesPage(chat.storedSessionId, chat.profile, limit = 120, offset = 0, order = "latest")
        return chat.copy(messages = page.messages, hasOlderMessages = page.hasOlder, olderOffset = page.returned)
    }

    suspend fun steer(runtimeId: String, profile: String, text: String) {
        gateway.requestObject(
            "session.steer",
            JSONObject().put("session_id", runtimeId).put("profile", profile).put("text", text)
        )
    }

    /**
     * Resolve the canonical Bot Chat by NAME, matching Desktop's registry contract.
     * Any last-known canonical_session hint turns an empty lookup into a fail-closed
     * error: a transient empty answer must never mint a replacement forever-chat.
     */
    suspend fun findCanonicalBotChat(profile: HermesProfile): String? {
        val exact = gateway.requestObject(
            "session.list",
            JSONObject()
                .put("profile", profile.name)
                .put("title", BOT_CHAT_TITLE)
                .put("limit", 200)
                .put("include_hidden", true)
        )
        val rows = exact.optJSONArray("sessions") ?: JSONArray()
        val row = rows.objects().firstOrNull {
            it.optString("root_title").ifBlank { it.optString("title") } == BOT_CHAT_TITLE
        }
        if (row != null) {
            return row.optStringOrNull("resolved_id") ?: row.optStringOrNull("id")
        }
        if (!profile.canonicalSession.isNullOrBlank()) {
            error("Could not confirm ${profile.name}'s Bot Chat registry — not starting a new chat")
        }
        return null
    }

    suspend fun openBotChat(profile: HermesProfile): ActiveChat =
        ensureCanonicalBotChat(profile, kickoff = false)

    /**
     * Single-flight, adopt-before-mint canonical creation. The lock prevents this
     * Android process from racing itself; the post-create title conflict lookup
     * adopts a winner created by Desktop/Telegram/another Android client.
     */
    private suspend fun ensureCanonicalBotChat(profile: HermesProfile, kickoff: Boolean): ActiveChat {
        val lock = canonicalBotLocks.getOrPut(profile.name) { Mutex() }
        return lock.withLock {
            val latestProfile = runCatching { rpcProfiles().firstOrNull { it.name == profile.name } }
                .getOrNull() ?: profile
            val existing = findCanonicalBotChat(latestProfile)
            if (existing != null) return@withLock resumeSession(existing, profile.name, profile.label)

            val created = gateway.requestObject(
                "session.create",
                JSONObject()
                    .put("profile", profile.name)
                    .put("source", "mobile")
                    .put("close_on_disconnect", false)
                    .put("follow_profile_config", true)
                    .put("hidden", true)
                    .put("title", BOT_CHAT_TITLE)
            )
            val runtime = created.optString("session_id")
            val stored = created.optString("stored_session_id").ifBlank { runtime }
            if (runtime.isBlank()) error("Hermes did not return a runtime session for ${profile.name}")

            var titled = false
            try {
                gateway.requestObject(
                    "session.title",
                    JSONObject()
                        .put("session_id", runtime)
                        .put("profile", profile.name)
                        .put("title", BOT_CHAT_TITLE)
                )
                titled = true
            } catch (t: Throwable) {
                // A uniqueness conflict means a concurrent surface already won
                // the registry title. Abandon our lazy zero-message row and adopt it.
                if (t.message.orEmpty().contains("already in use", ignoreCase = true)) {
                    val winnerProfile = runCatching { rpcProfiles().firstOrNull { it.name == profile.name } }
                        .getOrNull() ?: latestProfile
                    val winner = findCanonicalBotChat(winnerProfile)
                    if (winner != null) return@withLock resumeSession(winner, profile.name, profile.label)
                }
                // Compatibility: older gateways may not support eager session.title.
                // The intro below persists the pending title instead.
            }

            // Genuine New Agent gets one intro. On older gateways the same submit
            // is also the narrow persistence fallback for an otherwise-pruned lazy row.
            if (kickoff || !titled) {
                delay(400)
                runCatching {
                    gateway.requestObject(
                        "prompt.submit",
                        JSONObject()
                            .put("session_id", runtime)
                            .put("profile", profile.name)
                            .put("text", BOT_KICKOFF_TEXT)
                            .put("surface", "mobile"),
                        timeoutMs = 30_000
                    )
                }
            }

            // Re-open by the durable registry identity. If a compatibility
            // gateway renamed the row during kickoff, the exact resolver is still
            // the authority and will fail closed rather than silently fork.
            val refreshed = runCatching { rpcProfiles().firstOrNull { it.name == profile.name } }.getOrNull()
            val canonical = if (refreshed != null) {
                findCanonicalBotChat(refreshed) ?: stored
            } else {
                stored
            }
            resumeSession(canonical, profile.name, profile.label)
        }
    }

    suspend fun loadBotProfileConfig(profile: HermesProfile): BotProfileConfig {
        val described = gateway.requestObject("profiles.describe", JSONObject().put("name", profile.name), timeoutMs = 30_000)
        val catalog = runCatching {
            gateway.requestObject("mcp.catalog", JSONObject().put("profile", profile.name), timeoutMs = 20_000)
        }.getOrNull()
        val modelOptions = runCatching {
            gateway.requestObject(
                "model.options",
                JSONObject()
                    .put("profile", profile.name)
                    .put("include_unconfigured", true)
                    .put("explicit_only", false),
                timeoutMs = 20_000
            )
        }.getOrNull()

        val configuredMcp = (described.optJSONArray("mcp_servers") ?: JSONArray()).objects()
            .map { BotCapability.fromJson(it) }
        val configuredNames = configuredMcp.mapTo(mutableSetOf()) { it.name }
        val catalogMcp = (catalog?.optJSONArray("servers") ?: JSONArray()).objects()
            .filterNot { it.optString("name") in configuredNames }
            .map { BotCapability.fromJson(it, fromCatalog = true).copy(enabled = false) }
        val meta = profile.uiMeta?.optJSONObject("hermes-bots") ?: JSONObject()
        val model = described.optJSONObject("model") ?: JSONObject()
        val avatar = if (profile.hasAvatar) runCatching { getBotAvatar(profile.name) }.getOrNull() else null

        return BotProfileConfig(
            profile = profile,
            title = meta.optString("title").ifBlank { profile.label },
            description = described.optString("description").ifBlank { profile.description },
            soul = described.optString("soul"),
            provider = model.optString("provider").ifBlank { profile.provider },
            model = model.optString("default").ifBlank { profile.model },
            shape = meta.optString("shape", "blobatar"),
            color = meta.optString("color"),
            hidden = meta.optBoolean("hidden", false),
            pinned = meta.optBoolean("pinned", false),
            skills = (described.optJSONArray("skills") ?: JSONArray()).objects().map(BotCapability::fromJson),
            toolsets = (described.optJSONArray("toolsets") ?: JSONArray()).objects().map(BotCapability::fromJson),
            mcpServers = configuredMcp + catalogMcp,
            modelProviders = (modelOptions?.optJSONArray("providers") ?: JSONArray()).objects()
                .map(ModelProviderOption::fromJson).filter { it.slug.isNotBlank() },
            uiMetaRevision = profile.uiMetaRevisions[BOT_META_KEY] ?: 0,
            avatarDataUrl = avatar
        )
    }

    suspend fun saveBotProfileConfig(
        original: BotProfileConfig,
        updated: BotProfileConfig,
        confirmExpensiveModel: Boolean = false
    ): BotSaveResult {
        val payload = JSONObject().put("name", original.profile.name).put("profile", original.profile.name)
        val preApplied = linkedMapOf<String, Boolean>()

        val avatarChanged = original.avatarDataUrl != updated.avatarDataUrl
        val lookChanged = original.title != updated.title || original.shape != updated.shape ||
            original.color != updated.color || original.hidden != updated.hidden || original.pinned != updated.pinned || avatarChanged
        if (lookChanged) {
            val previousMeta = original.profile.uiMeta?.optJSONObject(BOT_META_KEY)
            val nextMeta = if (previousMeta != null) JSONObject(previousMeta.toString()) else JSONObject()
            nextMeta.put("title", updated.title.trim())
            nextMeta.put("shape", updated.shape.ifBlank { "blobatar" })
            if (updated.color.isBlank()) nextMeta.remove("color") else nextMeta.put("color", updated.color)
            nextMeta.put("hidden", updated.hidden)
            nextMeta.put("pinned", updated.pinned)
            nextMeta.put("custom", true)
            if (avatarChanged) nextMeta.put("imageKind", if (updated.avatarDataUrl.isNullOrBlank()) "shape" else "photo")
            else if (!nextMeta.has("imageKind")) nextMeta.put("imageKind", "shape")
            if (!nextMeta.has("created")) nextMeta.put("created", System.currentTimeMillis())
            payload.put("ui_meta", JSONObject().put(BOT_META_KEY, nextMeta))
            payload.put(
                "ui_meta_expected_revisions",
                JSONObject().put(BOT_META_KEY, original.uiMetaRevision)
            )
        }

        if (original.description.trim() != updated.description.trim()) payload.put("description", updated.description.trim())
        if (original.soul != updated.soul) payload.put("soul", updated.soul)
        if (original.provider != updated.provider || original.model != updated.model) {
            if (updated.provider.isBlank() xor updated.model.isBlank()) {
                error("Provider and model must both be set, or both cleared")
            }
            if (updated.provider.isNotBlank() && updated.model.isNotBlank()) {
                payload.put("provider", updated.provider.trim())
                payload.put("model", updated.model.trim())
                if (confirmExpensiveModel) payload.put("confirm_expensive_model", true)
            } else if (original.provider.isNotBlank() || original.model.isNotBlank()) {
                // Match Desktop's profile editor: clearing both fields unsets the
                // profile model pin instead of silently keeping the old one.
                val unset = gateway.requestObject(
                    "cli.exec",
                    JSONObject().put(
                        "argv",
                        JSONArray(listOf("--profile", original.profile.name, "config", "unset", "model"))
                    ),
                    timeoutMs = 30_000
                )
                preApplied["model"] = unset.optBoolean("blocked", false).not() && unset.optInt("code", 0) == 0
            }
        }

        if (capabilitiesChanged(original.skills, updated.skills)) {
            payload.put("disabled_skills", JSONArray(updated.skills.filterNot { it.enabled }.map { it.name }))
        }
        if (capabilitiesChanged(original.toolsets, updated.toolsets)) {
            val enabled = updated.toolsets.filter { it.enabled }
            val pin = if (enabled.isEmpty() || enabled.size == updated.toolsets.size) emptyList() else enabled.map { it.name }
            payload.put("enabled_toolsets", JSONArray(pin))
        }
        if (capabilitiesChanged(original.mcpServers, updated.mcpServers)) {
            payload.put("enabled_mcp_servers", JSONArray(updated.mcpServers.filter { it.enabled }.map { it.name }))
        }

        val result = if (payload.length() <= 2) {
            BotSaveResult(applied = preApplied)
        } else {
            val configured = parseBotSaveResult(gateway.requestObject("profiles.configure", payload, timeoutMs = 60_000))
            if (preApplied.isEmpty()) configured else configured.copy(applied = preApplied + configured.applied)
        }
        if (avatarChanged && !result.uiMetaConflict) {
            setBotAvatar(original.profile.name, updated.avatarDataUrl)
        }
        return result
    }

    suspend fun confirmBotModelSwitch(profile: String, provider: String, model: String): BotSaveResult {
        require(provider.isNotBlank() && model.isNotBlank()) { "Provider and model are required" }
        val result = gateway.requestObject(
            "profiles.configure",
            JSONObject()
                .put("name", profile)
                .put("profile", profile)
                .put("provider", provider.trim())
                .put("model", model.trim())
                .put("confirm_expensive_model", true),
            timeoutMs = 60_000
        )
        return parseBotSaveResult(result)
    }

    private fun parseBotSaveResult(result: JSONObject): BotSaveResult {
        val appliedObject = result.optJSONObject("applied") ?: JSONObject()
        val applied = buildMap<String, Boolean> {
            val keys = appliedObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (appliedObject.opt(key) is Boolean) put(key, appliedObject.optBoolean(key))
            }
        }
        val conflicts = appliedObject.optJSONObject("ui_meta_conflicts")
        val revisions = appliedObject.optJSONObject("ui_meta_revisions")
        return BotSaveResult(
            applied = applied,
            newUiMetaRevision = revisions?.optInt(BOT_META_KEY),
            confirmRequired = result.optBoolean("confirm_required", false),
            confirmMessage = result.optString("confirm_message"),
            uiMetaConflict = conflicts?.has(BOT_META_KEY) == true
        )
    }

    suspend fun createBot(name: String, title: String, description: String, cloneFrom: String? = null): Pair<HermesProfile, ActiveChat> {
        val createParams = JSONObject()
            .put("name", name)
            .put("description", description.trim())
            .put("share_auth", true)
            .put("mirror_credentials", true)
        if (!cloneFrom.isNullOrBlank()) createParams.put("clone_from", cloneFrom)
        gateway.requestObject("profiles.create", createParams, timeoutMs = 60_000)

        val createdProfile = rpcProfiles().firstOrNull { it.name == name } ?: HermesProfile(name = name)
        val meta = JSONObject()
            .put("title", title.ifBlank { name })
            .put("description", description.trim())
            .put("imageKind", "shape")
            .put("shape", "blobatar")
            .put("created", System.currentTimeMillis())

        // Only a failure to register Bot Mode metadata makes this an invisible
        // draft worth rolling back. Once the metadata is persisted, later
        // runtime/canonical-chat failures must never delete a valid profile.
        try {
            val configured = gateway.requestObject(
                "profiles.configure",
                JSONObject()
                    .put("name", name)
                    .put("profile", name)
                    .put("ui_meta", JSONObject().put(BOT_META_KEY, meta))
                    .put("ui_meta_expected_revisions", JSONObject().put(BOT_META_KEY, createdProfile.uiMetaRevisions[BOT_META_KEY] ?: 0)),
                timeoutMs = 30_000
            )
            if (configured.optJSONObject("applied")?.optBoolean("ui_meta", false) != true) {
                error("Hermes did not persist Bot Mode metadata for $name")
            }
        } catch (t: Throwable) {
            runCatching { rest.deleteProfile(name) }
            throw t
        }

        val profile = rpcProfiles().firstOrNull { it.name == name }
            ?: createdProfile.copy(botTitle = title.ifBlank { name }, description = description)
        val readiness = runCatching {
            gateway.requestObject("setup.runtime_check", JSONObject().put("profile", name), timeoutMs = 20_000)
        }.getOrNull()
        val chat = ensureCanonicalBotChat(profile, kickoff = readiness?.optBoolean("ok", true) != false)
        val refreshedProfile = rpcProfiles().firstOrNull { it.name == name } ?: profile
        return refreshedProfile to chat
    }



    suspend fun getBotAvatar(profile: String): String? {
        val result = gateway.requestObject(
            "profiles.get_asset",
            JSONObject().put("name", profile).put("asset", "avatar"),
            timeoutMs = 30_000
        )
        return result.optStringOrNull("data").takeIf { result.optBoolean("found", false) }
    }

    suspend fun setBotAvatar(profile: String, dataUrl: String?) {
        val params = JSONObject().put("name", profile).put("asset", "avatar")
        if (dataUrl.isNullOrBlank()) params.put("clear", true) else params.put("data", dataUrl)
        val result = gateway.requestObject("profiles.set_asset", params, timeoutMs = 60_000)
        if (!result.optBoolean("ok", true)) error("Hermes did not store the avatar")
    }

    suspend fun groupCapabilities(profile: String? = null): GroupCapabilities = GroupCapabilities.fromJson(
        gateway.requestObject(
            "groups.capabilities",
            JSONObject().apply { if (!profile.isNullOrBlank()) put("profile", profile) },
            timeoutMs = 20_000
        )
    )

    suspend fun groupRooms(includeDisbanded: Boolean = false, limit: Int = 100, offset: Int = 0): List<GroupRoom> {
        val result = gateway.requestObject(
            "groups.list",
            JSONObject()
                .put("include_disbanded", includeDisbanded)
                .put("limit", limit.coerceIn(1, 200))
                .put("offset", offset.coerceAtLeast(0)),
            timeoutMs = 30_000
        )
        return (result.optJSONArray("rooms") ?: JSONArray()).objects().map(GroupRoom::fromJson)
    }

    private data class PeerInvitation(
        val connection: GatewayConfig,
        val memberId: String,
        val targetProfile: String,
        val grant: String,
        val catalog: JSONObject,
        val endpointUrl: String
    )

    suspend fun createRoutedGroupRoom(
        name: String,
        members: List<RoutedBot>,
        connections: List<GatewayConfig>,
        onConnectionUpdated: (GatewayConfig) -> Unit = {}
    ): GroupRoom {
        require(members.size in 2..6) { "A Group Chat needs between 2 and 6 bots" }
        require(members.distinctBy { it.routeKey }.size == members.size) { "Group members must be unique" }
        val roomName = name.trim().take(200)
        require(roomName.isNotBlank()) { "Group Chat name required" }

        val homeConnectionId = config.id
        val homeCaps = groupCapabilities(config.selectedProfile)
        require(homeCaps.driver) { "The active gateway Group Chat driver is unavailable" }
        if (members.any { it.connectionId != config.id }) {
            require(homeCaps.authorityGatewayId.isNotBlank()) { "The active gateway does not expose a RoomLink authority identity" }
            require(homeCaps.roomLink.enabled) { "The active gateway does not have RoomLink enabled" }
        }
        val roomId = "m-${UUID.randomUUID()}"
        val usedHandles = mutableSetOf<String>()
        val remoteSessions = linkedMapOf<String, RemoteGatewaySession>()
        val invitations = mutableListOf<PeerInvitation>()
        val memberRows = JSONArray()

        fun uniqueHandle(bot: RoutedBot): String {
            val base = (if (bot.profile.name == "default") "hermes" else bot.profile.name)
                .replace(Regex("[^A-Za-z0-9._:-]"), "-")
                .take(96)
                .ifBlank { "bot" }
            var candidate = base
            if (members.count { it.handle.equals(bot.handle, ignoreCase = true) } > 1) {
                val device = bot.connectionLabel.lowercase()
                    .replace(Regex("[^a-z0-9._:-]+"), "-").trim('-').take(24).ifBlank { "gateway" }
                candidate = "$base-$device"
            }
            var n = 2
            while (!usedHandles.add(candidate.lowercase())) candidate = "$base-${n++}"
            return when (candidate.lowercase()) {
                "all", "everyone" -> "$candidate-bot"
                else -> candidate
            }
        }

        try {
            for (bot in members) {
                val handle = uniqueHandle(bot)
                val memberId = "member-${UUID.randomUUID().toString().replace("-", "").take(24)}"
                val row = JSONObject()
                    .put("member_id", memberId)
                    .put("profile", bot.profile.name)
                    .put("handle", handle)
                    .put("display_name", bot.profile.label.take(200))

                if (bot.connectionId == homeConnectionId) {
                    row.put("target", JSONObject().put("kind", "local").put("profile", bot.profile.name))
                } else {
                    val targetConfig = connections.firstOrNull { it.id == bot.connectionId }
                        ?: error("Saved connection '${bot.connectionLabel}' is no longer available")
                    val remote = remoteSessions.getOrPut(targetConfig.id) {
                        // Construction is synchronous; the actual dial happens immediately below.
                        RemoteGatewaySession(targetConfig, onConnectionUpdated)
                    }
                    if (!bot.reachable) error("${bot.connectionLabel} is offline")
                    remote.connect()
                    val caps = GroupCapabilities.fromJson(
                        remote.gateway.requestObject(
                            "groups.capabilities",
                            JSONObject().put("profile", bot.profile.name),
                            timeoutMs = 20_000
                        )
                    )
                    val roomLink = caps.roomLink
                    if (!roomLink.enabled || roomLink.catalog == null || roomLink.endpoint?.available != true) {
                        error("${bot.connectionLabel}/${bot.profile.name} does not expose RoomLink: ${roomLink.reason.ifBlank { roomLink.endpoint?.reason.orEmpty() }}")
                    }
                    val invite = remote.gateway.requestObject(
                        "groups.peer.invite",
                        JSONObject()
                            .put("profile", bot.profile.name)
                            .put("room_id", roomId)
                            .put("home_install_id", homeCaps.authorityGatewayId)
                            .put("authority_gateway_id", homeCaps.authorityGatewayId)
                            .put("authority_epoch", 1)
                            .put("member_id", memberId)
                            .put("grant_id", "mobile-${UUID.randomUUID()}"),
                        timeoutMs = 30_000
                    )
                    val catalog = invite.optJSONObject("catalog") ?: error("RoomLink target returned no catalog")
                    val endpoint = invite.optJSONObject("endpoint") ?: catalog.optJSONObject("endpoint")
                        ?: error("RoomLink target returned no endpoint")
                    val endpointUrl = endpoint.optString("url")
                    if (!endpoint.optBoolean("available", false) || endpointUrl.isBlank()) {
                        error("RoomLink endpoint is unavailable for ${bot.connectionLabel}/${bot.profile.name}")
                    }
                    val targetProfile = invite.optString("target_profile").ifBlank { bot.profile.name }
                    val grant = invite.optString("grant").ifBlank { error("RoomLink target returned no grant") }
                    val targetInstallationId = catalog.optString("installation_id")
                    val capabilityDigest = catalog.optString("catalog_digest")
                    require(targetInstallationId.isNotBlank()) { "RoomLink target returned no installation identity" }
                    require(Regex("^[0-9a-f]{64}$").matches(capabilityDigest)) { "RoomLink target returned an invalid capability digest" }
                    row.put(
                        "target",
                        JSONObject()
                            .put("kind", "peer")
                            .put("peer_id", bot.connectionId)
                            .put("installation_id", targetInstallationId)
                            .put("profile", targetProfile)
                            .put("capability_digest", capabilityDigest)
                    )
                    invitations += PeerInvitation(targetConfig, memberId, targetProfile, grant, catalog, endpointUrl)
                }
                memberRows.put(row)
            }

            val result = gateway.requestObject(
                "groups.create",
                JSONObject()
                    .put("room_id", roomId)
                    .put("name", roomName)
                    .put("members", memberRows),
                timeoutMs = 60_000
            )
            val room = GroupRoom.fromJson(result.optJSONObject("room") ?: error("Hermes returned no Group Chat room"))

            try {
                for (invite in invitations) {
                    gateway.requestObject(
                        "groups.peer.register",
                        JSONObject()
                            .put("room_id", roomId)
                            .put("member_id", invite.memberId)
                            .put("target_url", invite.endpointUrl)
                            .put("target_profile", invite.targetProfile)
                            .put("grant", invite.grant)
                            .put("catalog", invite.catalog)
                            .put("cancellation_scope_id", "cancel-$roomId-${invite.memberId}")
                            .put("trace_id", "mobile-${UUID.randomUUID()}"),
                        timeoutMs = 60_000
                    )
                }
            } catch (registrationError: Throwable) {
                runCatching { disbandGroup(roomId) }
                throw registrationError
            }
            return room
        } catch (error: Throwable) {
            // Grants are target-scoped and short-lived, but revoke every one we successfully minted
            // when room setup aborts so a failed mobile wizard never leaves live authority behind.
            invitations.forEach { invite ->
                val remote = remoteSessions[invite.connection.id]
                if (remote != null) runCatching {
                    remote.gateway.requestObject(
                        "groups.peer.revoke",
                        JSONObject().put("profile", invite.targetProfile).put("grant", invite.grant),
                        timeoutMs = 15_000
                    )
                }
            }
            throw error
        } finally {
            remoteSessions.values.forEach { runCatching { it.close() } }
        }
    }

    suspend fun createGroupRoom(name: String, members: List<HermesProfile>): GroupRoom {
        require(members.size in 2..6) { "A Group Chat needs between 2 and 6 bots" }
        val profiles = members.distinctBy { it.name }
        require(profiles.size == members.size) { "Group members must be unique" }
        val memberRows = JSONArray()
        val usedHandles = mutableSetOf<String>()
        profiles.forEach { profile ->
            var handle = groupHandle(profile)
            var suffix = 2
            while (!usedHandles.add(handle.lowercase())) {
                handle = "${groupHandle(profile)}-$suffix"
                suffix += 1
            }
            memberRows.put(
                JSONObject()
                    .put("member_id", profile.name)
                    .put("profile", profile.name)
                    .put("handle", handle)
                    .put("display_name", profile.label.take(200))
            )
        }
        val roomId = "m-${UUID.randomUUID()}"
        val result = gateway.requestObject(
            "groups.create",
            JSONObject()
                .put("room_id", roomId)
                .put("name", name.trim().take(200))
                .put("members", memberRows),
            timeoutMs = 60_000
        )
        return GroupRoom.fromJson(result.optJSONObject("room") ?: error("Hermes returned no Group Chat room"))
    }

    suspend fun groupState(roomId: String): GroupRoomState {
        val result = gateway.requestObject(
            "groups.state",
            JSONObject().put("room_id", roomId),
            timeoutMs = 30_000
        )
        return GroupRoomState(
            room = GroupRoom.fromJson(result.optJSONObject("room") ?: error("Group Chat not found")),
            driver = GroupDriverStatus.fromJson(result.optJSONObject("driver_status"))
        )
    }

    suspend fun groupLog(roomId: String, sinceSeq: Long, limit: Int = 500): GroupLogPage {
        val result = gateway.requestObject(
            "groups.log",
            JSONObject()
                .put("room_id", roomId)
                .put("since_seq", sinceSeq.coerceAtLeast(0L))
                .put("limit", limit.coerceIn(1, 500)),
            timeoutMs = 30_000
        )
        return GroupLogPage(
            events = (result.optJSONArray("events") ?: JSONArray()).objects().map(GroupEvent::fromJson),
            cursor = result.optLong("cursor", sinceSeq),
            latestSeq = result.optLong("latest_seq", sinceSeq),
            hasMore = result.optBoolean("has_more", false)
        )
    }

    suspend fun openGroupRoom(room: GroupRoom): ActiveGroupRoom {
        val state = groupState(room.roomId)
        // Hosted-room replay is forward-only. Start close enough to the durable
        // tip to render the useful mobile tail without walking a 50k-event room.
        var cursor = (state.room.latestSeq - GROUP_INITIAL_EVENT_WINDOW).coerceAtLeast(0L)
        val events = mutableListOf<GroupEvent>()
        var latest = state.room.latestSeq
        var pages = 0
        do {
            val page = groupLog(room.roomId, cursor, limit = 500)
            events += page.events
            cursor = page.cursor.coerceAtLeast(cursor)
            latest = maxOf(latest, page.latestSeq)
            pages += 1
            if (!page.hasMore || pages >= 4) break
        } while (true)
        return ActiveGroupRoom(
            room = state.room,
            events = events.distinctBy { it.seq }.sortedBy { it.seq },
            cursor = cursor,
            latestSeq = latest,
            driver = state.driver
        )
    }

    suspend fun pollGroupRoom(active: ActiveGroupRoom): ActiveGroupRoom {
        val state = groupState(active.room.roomId)
        var cursor = active.cursor
        var latest = maxOf(active.latestSeq, state.room.latestSeq)
        val additions = mutableListOf<GroupEvent>()
        var pages = 0
        do {
            val page = groupLog(active.room.roomId, cursor, limit = 500)
            additions += page.events
            val next = page.cursor.coerceAtLeast(cursor)
            if (next == cursor && page.hasMore) break
            cursor = next
            latest = maxOf(latest, page.latestSeq)
            pages += 1
            if (!page.hasMore || pages >= 4) break
        } while (true)
        val merged = (active.events + additions)
            .associateBy { it.seq }
            .values
            .sortedBy { it.seq }
            .takeLast(GROUP_LOCAL_EVENT_LIMIT)
        return active.copy(
            room = state.room,
            events = merged,
            cursor = maxOf(active.cursor, cursor),
            latestSeq = maxOf(active.latestSeq, latest),
            driver = state.driver
        )
    }

    suspend fun sendGroupMessage(roomId: String, text: String, threadId: String? = null): GroupEvent {
        val actualThread = threadId?.takeIf { it.isNotBlank() } ?: "t-${UUID.randomUUID()}"
        val clientEventId = "mobile-${UUID.randomUUID()}"
        val result = gateway.requestObject(
            "groups.send",
            JSONObject()
                .put("room_id", roomId)
                .put("event_id", clientEventId)
                .put("payload", JSONObject().put("text", text).put("thread_id", actualThread)),
            timeoutMs = 30_000
        )
        return GroupEvent.fromJson(result.optJSONObject("event") ?: error("Hermes returned no room event"))
    }

    suspend fun renameGroup(roomId: String, name: String): GroupRoom {
        val result = gateway.requestObject(
            "groups.rename",
            JSONObject()
                .put("room_id", roomId)
                .put("event_id", "rename-${UUID.randomUUID()}")
                .put("name", name.trim().take(200)),
            timeoutMs = 30_000
        )
        return GroupRoom.fromJson(result.optJSONObject("room") ?: error("Hermes returned no renamed room"))
    }

    suspend fun stopGroup(roomId: String) {
        gateway.requestObject(
            "groups.stop",
            JSONObject().put("room_id", roomId).put("cancel_id", "mobile-${UUID.randomUUID()}"),
            timeoutMs = 30_000
        )
    }

    suspend fun retryGroupTask(roomId: String, taskId: String) {
        gateway.requestObject(
            "groups.retry",
            JSONObject().put("room_id", roomId).put("task_id", taskId),
            timeoutMs = 30_000
        )
    }

    suspend fun approveGroupTask(roomId: String, action: GroupPendingAction, choice: String) {
        require(choice == "once" || choice == "deny") { "Hosted room approvals only support once or deny" }
        gateway.requestObject(
            "groups.approve",
            JSONObject()
                .put("room_id", roomId)
                .put("member_id", action.memberId)
                .put("task_id", action.taskId)
                .put("execution_generation", action.executionGeneration)
                .put("choice", choice)
                .put("request_id", action.requestId),
            timeoutMs = 30_000
        )
    }

    suspend fun disbandGroup(roomId: String) {
        gateway.requestObject(
            "groups.disband",
            JSONObject().put("room_id", roomId).put("cancel_id", "mobile-${UUID.randomUUID()}"),
            timeoutMs = 60_000
        )
    }

    private fun groupHandle(profile: HermesProfile): String {
        val base = if (profile.name == "default") "hermes" else profile.name
        return when (base.lowercase()) {
            "all", "everyone" -> "$base-bot"
            else -> base
        }.take(128)
    }

    suspend fun deleteBot(profile: HermesProfile) {
        if (profile.isDefault) error("The default Hermes profile cannot be deleted from Bot Mode")
        rest.deleteProfile(profile.name)
    }

    private fun capabilitiesChanged(a: List<BotCapability>, b: List<BotCapability>): Boolean =
        a.associate { it.name to it.enabled } != b.associate { it.name to it.enabled }

    companion object {
        private const val BOT_CHAT_TITLE = "Bot Chat"
        private const val BOT_META_KEY = "hermes-bots"
        private const val BOT_KICKOFF_TEXT = "Hey, tell me about yourself!"
        private const val GROUP_INITIAL_EVENT_WINDOW = 400L
        private const val GROUP_LOCAL_EVENT_LIMIT = 1200
    }

    private fun activeChatFromSnapshot(root: JSONObject, profile: String, fallbackTitle: String, source: String = ""): ActiveChat {
        val info = root.optJSONObject("info") ?: JSONObject()
        val messages = (root.optJSONArray("messages") ?: JSONArray()).objects().map(ChatMessage::fromJson)
        val runtimeId = root.optString("session_id")
        val storedId = root.optString("stored_session_id").ifBlank {
            root.optString("resumed").ifBlank { runtimeId }
        }
        val inflight = root.optJSONObject("inflight")
        val inflightText = inflight?.optString("assistant").orEmpty()
        return ActiveChat(
            storedSessionId = storedId,
            runtimeSessionId = runtimeId,
            profile = profile,
            title = info.optString("title").ifBlank { fallbackTitle.ifBlank { "New chat" } },
            source = source.ifBlank { info.optString("source") },
            messages = messages,
            streamingText = inflightText,
            running = root.optBoolean("running", info.optBoolean("running", false)),
            model = info.optString("model"),
            provider = info.optString("provider"),
            reasoningEffort = info.optString("reasoning_effort").ifBlank { info.optString("reasoning") }
        )
    }
}
