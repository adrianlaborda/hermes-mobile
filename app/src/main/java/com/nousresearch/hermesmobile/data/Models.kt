package com.nousresearch.hermesmobile.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

fun JSONObject.optLongOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) optLong(key) else null

/** Hermes uses both epoch values and ISO-8601 timestamps across older/newer gateway builds. */
fun JSONObject.optTimestampMillisOrNull(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    fun normalizeEpoch(value: Long): Long = if (value in 1..9_999_999_999L) value * 1_000L else value
    return when (val raw = opt(key)) {
        is Number -> normalizeEpoch(raw.toLong())
        is String -> {
            val text = raw.trim()
            if (text.isEmpty()) null
            else text.toLongOrNull()?.let(::normalizeEpoch)
                ?: runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
        }
        else -> null
    }
}

fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key) else null

fun JSONObject.optBooleanOrNull(key: String): Boolean? =
    if (has(key) && !isNull(key)) optBoolean(key) else null

fun JSONArray.objects(): List<JSONObject> = buildList {
    for (i in 0 until length()) optJSONObject(i)?.let(::add)
}


enum class GatewayAuthMode { NONE, SESSION_TOKEN, BASIC, NATIVE_PKCE }

data class NativeTokenSet(
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAt: Long = 0L,
    val provider: String = "",
    val userId: String = ""
) {
    val usable: Boolean get() = accessToken.isNotBlank()
    fun needsRefresh(nowSeconds: Long = System.currentTimeMillis() / 1_000L, skewSeconds: Long = 60L): Boolean =
        expiresAt <= 0L || expiresAt <= nowSeconds + skewSeconds

    companion object {
        fun fromJson(o: JSONObject): NativeTokenSet {
            val rawExpiry = o.optLong("expires_at", 0L)
            val expirySeconds = if (rawExpiry > 9_999_999_999L) rawExpiry / 1_000L else rawExpiry
            return NativeTokenSet(
                accessToken = o.optString("access_token"),
                refreshToken = o.optString("refresh_token"),
                expiresAt = expirySeconds,
                provider = o.optString("provider"),
                userId = o.optString("user_id")
            )
        }
    }
}

data class GatewayConfig(
    val id: String = "",
    val label: String = "Hermes",
    val baseUrl: String = "",
    val authMode: GatewayAuthMode = GatewayAuthMode.SESSION_TOKEN,
    /** Legacy loopback/self-hosted session-token credential. */
    val token: String = "",
    /** Cookie session created by a password provider. Stored encrypted; password is never persisted. */
    val cookieHeader: String = "",
    /** Native RFC 8252 token set. Stored encrypted with Android Keystore. */
    val nativeTokens: NativeTokenSet = NativeTokenSet(),
    val authProvider: String = "",
    val selectedProfile: String = "default",
    val extraHeaders: Map<String, String> = emptyMap()
) {
    val displayLabel: String get() = label.ifBlank { baseUrl.substringAfter("://").substringBefore('/') }
    val hasCredential: Boolean get() = when (authMode) {
        GatewayAuthMode.NONE -> true
        GatewayAuthMode.SESSION_TOKEN -> token.isNotBlank()
        GatewayAuthMode.BASIC -> cookieHeader.isNotBlank() || nativeTokens.usable
        GatewayAuthMode.NATIVE_PKCE -> nativeTokens.usable
    }
}

data class AuthProvider(
    val name: String,
    val displayName: String = "",
    val supportsPassword: Boolean = false,
    val supportsSession: Boolean = true
) {
    val label: String get() = displayName.ifBlank { name }
    companion object {
        fun fromJson(o: JSONObject) = AuthProvider(
            name = o.optString("name"),
            displayName = o.optString("display_name").ifBlank { o.optString("label") },
            supportsPassword = o.optBoolean("supports_password", o.optString("name") == "basic"),
            supportsSession = o.optBoolean("supports_session", true)
        )
    }
}

data class GatewayStatus(
    val version: String = "",
    val installId: String = "",
    val authRequired: Boolean = false,
    val authProviders: List<String> = emptyList(),
    val authFlows: List<String> = emptyList(),
    val raw: JSONObject = JSONObject()
) {
    val supportsNativePkce: Boolean get() = "native_pkce" in authFlows
    val supportsCookieAuth: Boolean get() = "cookie" in authFlows
    val advertisesBasic: Boolean get() = "basic" in authProviders

    companion object {
        fun fromJson(o: JSONObject): GatewayStatus {
            fun stringList(key: String): List<String> {
                val a = o.optJSONArray(key) ?: return emptyList()
                return (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
            }
            return GatewayStatus(
                version = o.optString("version"),
                installId = o.optString("install_id"),
                authRequired = o.optBoolean("auth_required", false),
                authProviders = stringList("auth_providers"),
                authFlows = stringList("auth_flows"),
                raw = o
            )
        }
    }
}

data class GatewayDiscovery(
    val draft: GatewayConfig,
    val status: GatewayStatus,
    val providers: List<AuthProvider> = emptyList()
) {
    val passwordProviders: List<AuthProvider> get() = providers.filter { it.supportsPassword }
    val nativeProviders: List<AuthProvider> get() = providers.filter { it.supportsSession && !it.supportsPassword }
}

/**
 * Desktop's `botActivitySession`: the fresher of the bot's Bot Chat (`canonical_session`, hidden from session lists)
 * and its newest listed conversation (`last_session`). Its preview is the newest message, not the first one.
 */
internal fun botActivity(canonical: JSONObject?, last: JSONObject?): JSONObject? = when {
    canonical == null || last == null -> canonical ?: last
    canonical.optLong("last_active", 0L) >= last.optLong("last_active", 0L) -> canonical
    else -> last
}

/** Gateway timestamps come in seconds or milliseconds; seconds here. */
internal fun sessionEpochSeconds(ts: Long): Long = if (ts > 10_000_000_000L) ts / 1000L else ts

data class HermesProfile(
    val name: String,
    val displayName: String = "",
    val botTitle: String = "",
    val description: String = "",
    val isDefault: Boolean = false,
    val gatewayRunning: Boolean = false,
    val model: String = "",
    val provider: String = "",
    val canonicalSession: String? = null,
    val canonicalResolvedSession: String? = null,
    val lastSession: String? = null,
    /** The newest message of the bot's freshest conversation and when it was active (Desktop's roster row). */
    val activityPreview: String = "",
    val activityAt: Long = 0L,
    val hasAvatar: Boolean = false,
    val skillCount: Int = 0,
    val uiMeta: JSONObject? = null,
    val uiMetaRevisions: Map<String, Int> = emptyMap()
) {
    val label: String get() = botTitle.ifBlank { displayName.ifBlank { name } }
    val botMeta: JSONObject? get() = uiMeta?.optJSONObject("hermes-bots")
    val isBot: Boolean get() = botMeta != null
    val botHidden: Boolean get() = botMeta?.optBoolean("hidden", false) ?: false
    val botPinned: Boolean get() = botMeta?.optBoolean("pinned", false) ?: false

    companion object {
        fun fromJson(o: JSONObject): HermesProfile {
            val meta = o.optJSONObject("ui_meta")
            val botMeta = meta?.optJSONObject("hermes-bots")
            // Current Bot Mode identity is the server-resolved session titled exactly
            // "Bot Chat". `canonical_session` is an object, not a client-owned pointer.
            val canonicalObject = o.optJSONObject("canonical_session")
            val canonical = canonicalObject?.optStringOrNull("id")
            val canonicalResolved = canonicalObject?.optStringOrNull("resolved_id")
            // `last_session` is the newest listed conversation (an object; a bare id on older gateways).
            val lastObject = o.optJSONObject("last_session")
            val activity = botActivity(canonicalObject, lastObject)
            return HermesProfile(
                name = o.optString("name", o.optString("id", "default")),
                displayName = o.optString("display_name"),
                botTitle = o.optString("bot_title").ifBlank { botMeta?.optString("title").orEmpty() },
                description = o.optString("description").ifBlank { botMeta?.optString("description").orEmpty() },
                isDefault = o.optBoolean("is_default", o.optString("name") == "default"),
                gatewayRunning = o.optBoolean("gateway_running", true),
                model = o.optString("model"),
                provider = o.optString("provider"),
                canonicalSession = canonical,
                canonicalResolvedSession = canonicalResolved,
                lastSession = lastObject?.optStringOrNull("id") ?: o.optStringOrNull("last_session"),
                activityPreview = activity?.optString("preview").orEmpty(),
                activityAt = activity?.let { sessionEpochSeconds(it.optLong("last_active", 0L)) } ?: 0L,
                hasAvatar = o.optBoolean("has_avatar", false),
                skillCount = o.optInt("skill_count", 0),
                uiMeta = meta,
                uiMetaRevisions = buildMap {
                    val revisions = o.optJSONObject("ui_meta_revisions")
                    if (revisions != null) {
                        val keys = revisions.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            put(key, revisions.optInt(key, 0).coerceAtLeast(0))
                        }
                    }
                }
            )
        }
    }
}


data class BotCapability(
    val name: String,
    val enabled: Boolean = true,
    val label: String = "",
    val description: String = "",
    val transport: String = "",
    val fromCatalog: Boolean = false,
    val installed: Boolean = true,
    val auth: String = "",
    val requires: List<String> = emptyList()
) {
    companion object {
        fun fromJson(o: JSONObject, fromCatalog: Boolean = false): BotCapability {
            val requires = buildList {
                val arr = o.optJSONArray("requires")
                if (arr != null) for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
            return BotCapability(
                name = o.optString("name"),
                enabled = o.optBoolean("enabled", !fromCatalog),
                label = o.optString("label"),
                description = o.optString("description"),
                transport = o.optString("transport"),
                fromCatalog = fromCatalog,
                installed = o.optBoolean("installed", !fromCatalog),
                auth = o.optString("auth"),
                requires = requires
            )
        }
    }
}

data class BotProfileConfig(
    val profile: HermesProfile,
    val title: String = "",
    val description: String = "",
    val soul: String = "",
    val provider: String = "",
    val model: String = "",
    val shape: String = "blobatar",
    val color: String = "",
    val hidden: Boolean = false,
    val pinned: Boolean = false,
    val skills: List<BotCapability> = emptyList(),
    val toolsets: List<BotCapability> = emptyList(),
    val mcpServers: List<BotCapability> = emptyList(),
    val modelProviders: List<ModelProviderOption> = emptyList(),
    val uiMetaRevision: Int = 0,
    /** Profile avatar asset as a gateway-returned data URL. Never persisted in ui_meta. */
    val avatarDataUrl: String? = null
)

data class ModelProviderOption(
    val slug: String,
    val name: String = "",
    val models: List<String> = emptyList()
) {
    companion object {
        fun fromJson(o: JSONObject): ModelProviderOption {
            val models = buildList {
                val arr = o.optJSONArray("models") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    when (val value = arr.opt(i)) {
                        is String -> if (value.isNotBlank()) add(value)
                        is JSONObject -> value.optString("id").ifBlank { value.optString("name") }
                            .takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }
            return ModelProviderOption(
                slug = o.optString("slug"),
                name = o.optString("name"),
                models = models
            )
        }
    }
}

data class BotSaveResult(
    val applied: Map<String, Boolean> = emptyMap(),
    val newUiMetaRevision: Int? = null,
    val confirmRequired: Boolean = false,
    val confirmMessage: String = "",
    val uiMetaConflict: Boolean = false
) {
    val ok: Boolean get() = !uiMetaConflict && applied.values.all { it }
}

data class HermesSession(
    val id: String,
    val title: String = "",
    val preview: String = "",
    val profile: String = "default",
    val source: String = "",
    val messageCount: Int = 0,
    val lastActive: Long = 0L,
    val startedAt: Long = 0L,
    val model: String = "",
    val unread: Boolean = false,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val hidden: Boolean = false,
    val lineageRootId: String? = null
) {
    /** A scheduled job's run (Desktop keeps these out of the recents, in their own Cron section). */
    val isRoutineRun: Boolean get() = source.trim().lowercase() in setOf("cron", "cron_output")

    companion object {
        fun fromJson(o: JSONObject): HermesSession = HermesSession(
            id = o.optString("id", o.optString("session_id")),
            title = o.optString("title"),
            preview = o.optString("preview"),
            profile = o.optString("profile", "default"),
            source = o.optString("source"),
            messageCount = o.optInt("message_count", 0),
            lastActive = o.optLong("last_active", 0L),
            startedAt = o.optLong("started_at", 0L),
            model = o.optString("model"),
            unread = o.optBoolean("unread", false),
            pinned = o.optBoolean("pinned", false),
            archived = o.optBoolean("archived", false),
            hidden = o.optBoolean("hidden", false),
            lineageRootId = o.optStringOrNull("_lineage_root_id") ?: o.optStringOrNull("lineage_root")
        )
    }
}

data class ChatMessage(
    val rowId: Long? = null,
    val role: String,
    val content: String,
    val reasoning: String = "",
    val toolName: String = "",
    val attachments: List<String> = emptyList(),
    val timestamp: Long = 0L,
    val displayKind: String = "",
    val live: Boolean = false
) {
    val stableKey: String
        get() = rowId?.let { "row:$it" } ?: if (live) "live:$role:$timestamp" else "message:$role:$timestamp:${content.hashCode()}"

    val renderable: Boolean
        get() = displayKind != "hidden" && (role != "system" || displayKind.isNotBlank()) &&
            (content.isNotBlank() || reasoning.isNotBlank() || attachments.isNotEmpty() || displayKind.isNotBlank() || role == "tool")

    companion object {
        private const val BOT_MENTION_NOTE_MARKER = "\n\n[@mentions resolved from the Bot Mode roster"

        fun fromJson(o: JSONObject): ChatMessage {
            val display = o.opt("display_content")
            val raw = if (display != null && display != JSONObject.NULL) display
                else o.opt("content") ?: o.opt("text")
            var content = TranscriptText.visible(raw)
            if (content.isBlank() && (display == null || display == JSONObject.NULL)) {
                content = TranscriptText.sidecar(o.opt("codex_message_items"))
            }
            val role = o.optString("role", "assistant")
            // Android mirrors Desktop's Bot Mode mention middleware by adding a
            // model-only roster-resolution note to submitted USER text. The
            // durable row may carry that scaffolding, so keep it out of the UI
            // without touching assistant content that happens to contain the marker.
            if (role == "user") content = content.substringBefore(BOT_MENTION_NOTE_MARKER).trimEnd()
            return ChatMessage(
                rowId = o.optLongOrNull("row_id") ?: o.optLongOrNull("id"),
                role = role,
                content = content,
                reasoning = TranscriptText.reasoning(o.opt("display_reasoning")
                    ?: o.opt("reasoning") ?: o.opt("reasoning_content")),
                toolName = o.optString("tool_name", o.optString("name")),
                attachments = buildList {
                    val arr = o.optJSONArray("attachments") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        when (val item = arr.opt(i)) {
                            is String -> item.substringAfterLast('/').takeIf { it.isNotBlank() }?.let(::add)
                            is JSONObject -> item.optString("name").ifBlank { item.optString("filename") }.takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                    // `@file:`/`@image:` are the composer's attachment references: only a user's message attaches.
                    if (role == "user") Regex("@(?:file|image):([^\\s]+)").findAll(content).forEach { match ->
                        match.groupValues[1].substringAfterLast('/').takeIf { it.isNotBlank() }?.let { if (it !in this) add(it) }
                    }
                },
                timestamp = o.optLong("timestamp", 0L),
                displayKind = (o.opt("display_kind") as? String)?.takeUnless { it == "null" }.orEmpty()
            )
        }
    }
}

data class SessionListPage(
    val sessions: List<HermesSession>,
    val offset: Int,
    val limit: Int,
    val total: Int
) {
    val hasMore: Boolean get() = offset + sessions.size < total
}

data class SessionMessagePage(
    val messages: List<ChatMessage>,
    val offset: Int,
    val returned: Int,
    val limit: Int,
    val order: String
) {
    val hasOlder: Boolean get() = returned >= limit
}

data class PromptSubmitResult(
    val status: String = "",
    val userRowId: Long? = null
)

enum class SessionSourceKind { HERMES_APP, CHANNEL, CLIENT, UNKNOWN }

data class SessionSourceInfo(
    val id: String,
    val label: String,
    val kind: SessionSourceKind
)

fun sessionSourceInfo(raw: String): SessionSourceInfo {
    val id = raw.trim().lowercase()
    val labels = mapOf(
        "desktop" to "Desktop", "mobile" to "Mobile",
        "telegram" to "Telegram", "discord" to "Discord", "slack" to "Slack",
        "mattermost" to "Mattermost", "matrix" to "Matrix", "signal" to "Signal",
        "whatsapp" to "WhatsApp", "bluebubbles" to "iMessage", "photon" to "Photon",
        "homeassistant" to "Home Assistant", "email" to "Email", "sms" to "SMS",
        "webhook" to "Webhook", "api_server" to "API", "weixin" to "WeChat",
        "wecom" to "WeCom", "qqbot" to "QQ", "yuanbao" to "Yuanbao",
        "dingtalk" to "DingTalk", "feishu" to "Feishu",
        "acp" to "ACP", "cli" to "CLI", "codex" to "Codex", "gateway" to "Gateway",
        "kanban" to "Kanban", "local" to "Local", "oneshot" to "One-shot", "tui" to "TUI"
    )
    val channelIds = setOf(
        "telegram", "discord", "slack", "mattermost", "matrix", "signal", "whatsapp",
        "bluebubbles", "photon", "homeassistant", "email", "sms", "webhook", "api_server",
        "weixin", "wecom", "qqbot", "yuanbao", "dingtalk", "feishu"
    )
    val kind = when {
        id == "desktop" || id == "mobile" -> SessionSourceKind.HERMES_APP
        id in channelIds -> SessionSourceKind.CHANNEL
        id.isNotBlank() -> SessionSourceKind.CLIENT
        else -> SessionSourceKind.UNKNOWN
    }
    return SessionSourceInfo(id, labels[id] ?: id.replace('_', ' ').replaceFirstChar { it.uppercase() }.ifBlank { "Hermes" }, kind)
}

/** Bots as Desktop orders its roster: pinned first, then the most recently active, then by name. */
val botRosterOrder: Comparator<RoutedBot> = compareByDescending<RoutedBot> { it.profile.botPinned }
    .thenByDescending { it.profile.activityAt }
    .thenBy { it.label.lowercase() }

/** A message excerpt as one plain line: markdown markers, links' targets and line breaks dropped. */
fun previewLine(text: String): String = text
    .replace(Regex("""!?\[([^\]]*)]\([^)]*\)"""), "$1")
    .replace(Regex("""[*_`~#>]+"""), "")
    .replace(Regex("""\s+"""), " ")
    .trim()

data class RoutedBot(
    val connectionId: String,
    val connectionLabel: String,
    val installId: String = "",
    val profile: HermesProfile,
    val reachable: Boolean = true,
    val activeConnection: Boolean = false,
    val roomLinkEnabled: Boolean = false
) {
    val routeKey: String get() = "$connectionId::${profile.name}"
    val label: String get() = profile.label
    val handle: String get() = if (profile.name == "default") "hermes" else profile.name
}

data class SessionModelSwitchResult(
    val confirmRequired: Boolean = false,
    val confirmMessage: String = "",
    val deferred: Boolean = false,
    val provider: String = "",
    val model: String = ""
)

data class ActiveChat(
    val storedSessionId: String,
    val runtimeSessionId: String,
    val profile: String,
    val title: String,
    val source: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val streamingText: String = "",
    val running: Boolean = false,
    val model: String = "",
    val provider: String = "",
    val reasoningEffort: String = "",
    val hasOlderMessages: Boolean = false,
    val olderOffset: Int = 0
)



data class RoomLinkEndpoint(
    val available: Boolean = false,
    val url: String = "",
    val transportSecurity: String = "",
    val reason: String = ""
)

data class RoomLinkCatalog(
    val installationId: String = "",
    val protocolVersions: List<Int> = emptyList(),
    val linkModes: List<String> = emptyList(),
    val persistentProcess: Boolean = false,
    val text: Boolean = false,
    val attachments: Boolean = false,
    val catalogDigest: String = "",
    val raw: JSONObject = JSONObject()
)

data class RoomLinkStatus(
    val enabled: Boolean = false,
    val profile: String = "",
    val catalog: RoomLinkCatalog? = null,
    val endpoint: RoomLinkEndpoint? = null,
    val reason: String = ""
)

data class GroupCapabilities(
    val protocolVersion: Int = 0,
    val driver: Boolean = false,
    val persistentProcess: Boolean = false,
    val authorityGatewayId: String = "",
    val roomLink: RoomLinkStatus = RoomLinkStatus(),
    val features: List<String> = emptyList(),
    val methods: Set<String> = emptySet(),
    val maxLogLimit: Int = 500
) {
    companion object {
        fun fromJson(o: JSONObject): GroupCapabilities = GroupCapabilities(
            protocolVersion = o.optInt("protocol_version", 0),
            driver = o.optBoolean("driver", false),
            persistentProcess = o.optBoolean("persistent_process", false),
            authorityGatewayId = o.optString("authority_gateway_id"),
            roomLink = run {
                val link = o.optJSONObject("room_link") ?: JSONObject()
                fun endpoint(raw: JSONObject?): RoomLinkEndpoint? = raw?.let {
                    RoomLinkEndpoint(
                        available = it.optBoolean("available", false),
                        url = it.optString("url"),
                        transportSecurity = it.optString("transport_security"),
                        reason = it.optString("reason")
                    )
                }
                val catalogRaw = link.optJSONObject("catalog")
                val catalog = catalogRaw?.let { c ->
                    RoomLinkCatalog(
                        installationId = c.optString("installation_id"),
                        protocolVersions = buildList {
                            val a = c.optJSONArray("protocol_versions") ?: JSONArray()
                            for (i in 0 until a.length()) add(a.optInt(i))
                        },
                        linkModes = buildList {
                            val a = c.optJSONArray("link_modes") ?: JSONArray()
                            for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                        },
                        persistentProcess = c.optBoolean("persistent_process", false),
                        text = c.optBoolean("text", false),
                        attachments = c.optBoolean("attachments", false),
                        catalogDigest = c.optString("catalog_digest"),
                        raw = JSONObject(c.toString())
                    )
                }
                RoomLinkStatus(
                    enabled = link.optBoolean("enabled", false),
                    profile = link.optString("profile"),
                    catalog = catalog,
                    endpoint = endpoint(link.optJSONObject("endpoint") ?: catalogRaw?.optJSONObject("endpoint")),
                    reason = link.optString("reason")
                )
            },
            features = buildList {
                val a = o.optJSONArray("features") ?: JSONArray()
                for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            },
            methods = buildSet {
                val a = o.optJSONArray("methods") ?: JSONArray()
                for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            },
            maxLogLimit = o.optInt("max_log_limit", 500).coerceIn(1, 500)
        )
    }
}

data class GroupMember(
    val memberId: String,
    val profile: String,
    val handle: String,
    val displayName: String = "",
    val targetKind: String = "local",
    val targetProfile: String = "",
    val peerId: String = "",
    val installationId: String = "",
    val capabilityDigest: String = ""
) {
    val label: String get() = displayName.ifBlank { profile }

    companion object {
        fun fromJson(o: JSONObject): GroupMember {
            val target = o.optJSONObject("target")
            return GroupMember(
                memberId = o.optString("member_id").ifBlank { o.optString("profile") },
                profile = o.optString("profile"),
                handle = o.optString("handle").ifBlank { o.optString("profile") },
                displayName = o.optString("display_name"),
                targetKind = target?.optString("kind").orEmpty().ifBlank { "local" },
                targetProfile = target?.optString("profile").orEmpty(),
                peerId = target?.optString("peer_id").orEmpty(),
                installationId = target?.optString("installation_id").orEmpty(),
                capabilityDigest = target?.optString("capability_digest").orEmpty()
            )
        }
    }
}

data class GroupRoom(
    val roomId: String,
    val name: String,
    val members: List<GroupMember>,
    val authorityGatewayId: String = "",
    val authorityEpoch: Int = 0,
    val revision: Int = 0,
    val createdAt: Double = 0.0,
    val updatedAt: Double = 0.0,
    val disbandedAt: Double? = null,
    val latestSeq: Long = 0L
) {
    val active: Boolean get() = disbandedAt == null

    companion object {
        fun fromJson(o: JSONObject): GroupRoom = GroupRoom(
            roomId = o.optString("room_id"),
            name = o.optString("name"),
            members = (o.optJSONArray("members") ?: JSONArray()).objects().map(GroupMember::fromJson),
            authorityGatewayId = o.optString("authority_gateway_id"),
            authorityEpoch = o.optInt("authority_epoch", 0),
            revision = o.optInt("revision", 0),
            createdAt = o.optDouble("created_at", 0.0),
            updatedAt = o.optDouble("updated_at", 0.0),
            disbandedAt = o.optDoubleOrNull("disbanded_at"),
            latestSeq = o.optLong("latest_seq", 0L)
        )
    }
}

data class GroupActor(
    val kind: String,
    val id: String,
    val profile: String = "",
    val displayName: String = "",
    val connectionId: String = ""
) {
    companion object {
        fun fromJson(o: JSONObject): GroupActor = GroupActor(
            kind = o.optString("kind"),
            id = o.optString("id"),
            profile = o.optString("profile"),
            displayName = o.optString("display_name"),
            connectionId = o.optString("connection_id")
        )
    }
}

data class GroupEvent(
    val roomId: String,
    val seq: Long,
    val eventId: String,
    val kind: String,
    val actor: GroupActor,
    val payload: JSONObject,
    val createdAt: Double = 0.0,
    val authorityEpoch: Int? = null
) {
    val text: String get() = payload.optString("text")
    val threadId: String get() = payload.optString("thread_id")
    val memberId: String get() = payload.optString("member_id").ifBlank { actor.id }
    val visibleMessage: Boolean get() = kind == "message.user" || kind == "message.member"

    companion object {
        fun fromJson(o: JSONObject): GroupEvent = GroupEvent(
            roomId = o.optString("room_id"),
            seq = o.optLong("seq", 0L),
            eventId = o.optString("event_id"),
            kind = o.optString("kind"),
            actor = GroupActor.fromJson(o.optJSONObject("actor") ?: JSONObject()),
            payload = o.optJSONObject("payload") ?: JSONObject(),
            createdAt = o.optDouble("created_at", 0.0),
            authorityEpoch = if (o.has("authority_epoch") && !o.isNull("authority_epoch")) o.optInt("authority_epoch") else null
        )
    }
}

data class GroupPendingAction(
    val kind: String,
    val memberId: String = "",
    val taskId: String = "",
    val executionGeneration: Int = 0,
    val requestId: String = "",
    val command: String = "",
    val description: String = "",
    val raw: JSONObject = JSONObject()
) {
    companion object {
        fun fromJson(o: JSONObject): GroupPendingAction = GroupPendingAction(
            kind = o.optString("kind"),
            memberId = o.optString("member_id"),
            taskId = o.optString("task_id"),
            executionGeneration = o.optInt("execution_generation", 0),
            requestId = o.optString("request_id"),
            command = o.optString("command"),
            description = o.optString("description"),
            raw = o
        )
    }
}

data class GroupPeerRoute(
    val roomId: String = "",
    val memberId: String = "",
    val status: String = ""
) {
    companion object {
        fun fromJson(o: JSONObject) = GroupPeerRoute(o.optString("room_id"), o.optString("member_id"), o.optString("status"))
    }
}

data class GroupDriverStatus(
    val running: Boolean = false,
    val working: Boolean = false,
    val blocked: Boolean = false,
    val pendingActions: List<GroupPendingAction> = emptyList(),
    val peerRoutes: List<GroupPeerRoute> = emptyList(),
    val counts: Map<String, Int> = emptyMap()
) {
    companion object {
        fun fromJson(o: JSONObject?): GroupDriverStatus {
            if (o == null) return GroupDriverStatus()
            val counts = buildMap<String, Int> {
                val c = o.optJSONObject("counts") ?: JSONObject()
                val keys = c.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, c.optInt(key, 0))
                }
            }
            return GroupDriverStatus(
                running = o.optBoolean("running", false),
                working = o.optBoolean("working", false),
                blocked = o.optBoolean("blocked", false),
                pendingActions = (o.optJSONArray("pending_actions") ?: JSONArray()).objects().map(GroupPendingAction::fromJson),
                peerRoutes = (o.optJSONArray("peer_routes") ?: JSONArray()).objects().map(GroupPeerRoute::fromJson),
                counts = counts
            )
        }
    }
}

data class GroupRoomState(
    val room: GroupRoom,
    val driver: GroupDriverStatus = GroupDriverStatus()
)

data class GroupLogPage(
    val events: List<GroupEvent>,
    val cursor: Long,
    val latestSeq: Long,
    val hasMore: Boolean
)

data class ActiveGroupRoom(
    val room: GroupRoom,
    val events: List<GroupEvent> = emptyList(),
    val cursor: Long = 0L,
    val latestSeq: Long = 0L,
    val driver: GroupDriverStatus = GroupDriverStatus(),
    val replyThreadId: String? = null
)



data class ComposerAttachment(
    val id: String,
    val uri: String,
    val name: String,
    val mimeType: String = "application/octet-stream",
    val size: Long = 0L
) {
    val kind: String get() = when {
        mimeType.startsWith("image/") -> "image"
        mimeType == "application/pdf" || name.endsWith(".pdf", ignoreCase = true) -> "pdf"
        else -> "file"
    }
}

data class QueuedPrompt(
    val id: String,
    val text: String,
    val attachments: List<ComposerAttachment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class SessionModelChoice(
    val provider: String,
    val model: String,
    val label: String = model,
    val supportsReasoning: Boolean? = null,
    val canDisableReasoning: Boolean? = null
)

data class LiveTaskStatus(
    val id: String,
    val label: String,
    val detail: String = "",
    val status: String = "running"
)

data class ArtifactSuggestion(
    val path: String,
    val name: String,
    val kind: String = "file",
    val sourceTool: String = ""
)

data class ToolActivity(
    val id: String,
    val name: String,
    val status: String,
    val detail: String = ""
)

data class CronJob(
    val id: String,
    val name: String,
    val prompt: String,
    val schedule: String,
    val scheduleDisplay: String,
    val deliver: String,
    val enabled: Boolean,
    val profile: String,
    val model: String = "",
    val provider: String = "",
    val state: String = "",
    val nextRunAt: Long? = null,
    val lastRunAt: Long? = null,
    val lastStatus: String = "",
    val lastError: String = "",
    val skills: List<String> = emptyList(),
    val script: String = "",
    val noAgent: Boolean = false,
    val reasoningEffort: String = "",
    val workdir: String = "",
    val repeat: Int? = null,
    val completedRuns: Int = 0
) {
    val deliveryTargets: List<String>
        get() = deliver.split(',').map(String::trim).filter(String::isNotBlank).ifEmpty { listOf("local") }

    companion object {
        fun fromJson(o: JSONObject): CronJob {
            val scheduleObj = o.optJSONObject("schedule")
            val deliverValue = when (val raw = o.opt("deliver")) {
                is JSONArray -> (0 until raw.length()).mapNotNull { raw.optString(it).takeIf(String::isNotBlank) }.joinToString(",")
                is JSONObject -> raw.optString("target").ifBlank { raw.optString("id") }
                else -> o.optString("deliver", "local")
            }.ifBlank { "local" }
            val skillsArray = o.optJSONArray("skills") ?: JSONArray()
            val runState = o.optJSONObject("run_state") ?: o.optJSONObject("last_run")
            val repeatObj = o.optJSONObject("repeat")
            return CronJob(
                id = o.optString("id"),
                name = o.optString("name"),
                prompt = o.optString("prompt"),
                schedule = scheduleObj?.optString("expr").orEmpty().ifBlank { o.optString("schedule") },
                scheduleDisplay = o.optString("schedule_display").ifBlank {
                    scheduleObj?.optString("display").orEmpty()
                },
                deliver = deliverValue,
                enabled = o.optBoolean("enabled", o.optString("state") != "paused"),
                profile = o.optString("profile", "default"),
                model = o.optString("model"),
                provider = o.optString("provider"),
                state = o.optString("state"),
                nextRunAt = o.optTimestampMillisOrNull("next_run_at") ?: scheduleObj?.optTimestampMillisOrNull("next_run_at"),
                lastRunAt = o.optTimestampMillisOrNull("last_run_at") ?: runState?.optTimestampMillisOrNull("at") ?: runState?.optTimestampMillisOrNull("started_at"),
                lastStatus = o.optString("last_status").ifBlank { runState?.optString("status").orEmpty() },
                lastError = o.optString("last_error").ifBlank { runState?.optString("error").orEmpty() },
                skills = (0 until skillsArray.length()).mapNotNull { skillsArray.optString(it).takeIf(String::isNotBlank) },
                script = o.optString("script"),
                noAgent = o.optBoolean("no_agent", false),
                reasoningEffort = o.optString("reasoning_effort"),
                workdir = o.optString("workdir"),
                repeat = when {
                    repeatObj != null -> repeatObj.optInt("times", 0).takeIf { it > 0 }
                    o.has("repeat") && !o.isNull("repeat") -> o.optInt("repeat", 0).takeIf { it > 0 }
                    else -> null
                },
                completedRuns = repeatObj?.optInt("completed", o.optInt("completed_runs", 0)) ?: o.optInt("completed_runs", 0)
            )
        }
    }
}

data class CronDraft(
    val name: String,
    val prompt: String,
    val schedule: String,
    val deliver: String = "local",
    val model: String = "",
    val provider: String = "",
    val reasoningEffort: String = "",
    val skills: List<String> = emptyList(),
    val script: String = "",
    val noAgent: Boolean = false,
    val workdir: String = "",
    val repeat: Int? = null
) {
    fun toJson(includeClears: Boolean = false): JSONObject = JSONObject().apply {
        put("prompt", prompt)
        put("schedule", schedule)
        put("deliver", deliver.ifBlank { "local" })
        put("name", name)
        if (includeClears || model.isNotBlank()) put("model", model)
        if (includeClears || provider.isNotBlank()) put("provider", provider)
        if (includeClears || reasoningEffort.isNotBlank()) put("reasoning_effort", reasoningEffort)
        put("skills", JSONArray(skills.filter(String::isNotBlank)))
        put("script", script)
        put("no_agent", noAgent)
        put("workdir", workdir)
        if (repeat != null) put("repeat", repeat) else if (includeClears) put("repeat", JSONObject.NULL)
    }
}

data class CronDeliveryTarget(
    val id: String,
    val name: String,
    val configured: Boolean = true,
    val homeTargetSet: Boolean = false,
    val homeEnvVar: String = "",
    val description: String = ""
) {
    companion object {
        fun fromJson(o: JSONObject): CronDeliveryTarget = CronDeliveryTarget(
            id = o.optString("id").ifBlank { o.optString("value") },
            name = o.optString("name").ifBlank { o.optString("label").ifBlank { o.optString("id") } },
            configured = o.optBoolean("configured", o.optBoolean("available", true)),
            homeTargetSet = o.optBoolean("home_target_set", false),
            homeEnvVar = o.optString("home_env_var"),
            description = o.optString("description")
        )
    }
}

data class CronRun(
    val id: String,
    val title: String,
    val preview: String,
    val status: String,
    val startedAt: Long? = null,
    val lastActiveAt: Long? = null,
    val profile: String = "",
    val source: String = ""
) {
    /** A script-only job's fire: listed from its output file, with no conversation behind it to open. */
    val outputOnly: Boolean get() = source == "cron_output" || id.startsWith("cron_output:")

    companion object {
        fun fromJson(o: JSONObject): CronRun = CronRun(
            id = o.optString("session_id").ifBlank { o.optString("id") },
            title = o.optString("title").ifBlank { o.optString("name") },
            preview = o.optString("preview").ifBlank { o.optString("summary") },
            status = o.optString("status").ifBlank { o.optString("state") },
            startedAt = o.optTimestampMillisOrNull("started_at") ?: o.optTimestampMillisOrNull("created_at"),
            lastActiveAt = o.optTimestampMillisOrNull("last_active") ?: o.optTimestampMillisOrNull("updated_at"),
            profile = o.optString("profile"),
            source = o.optString("source")
        )
    }
}

data class AutomationBlueprintField(
    val key: String,
    val label: String,
    val type: String = "text",
    val required: Boolean = false,
    val defaultValue: String = "",
    val placeholder: String = "",
    val description: String = "",
    val options: List<String> = emptyList()
) {
    companion object {
        fun fromJson(o: JSONObject): AutomationBlueprintField {
            val options = o.optJSONArray("options") ?: o.optJSONArray("choices") ?: JSONArray()
            return AutomationBlueprintField(
                key = o.optString("key").ifBlank { o.optString("name") },
                label = o.optString("label").ifBlank { o.optString("name").ifBlank { o.optString("key") } },
                type = o.optString("type", "text"),
                required = o.optBoolean("required", false),
                defaultValue = o.opt("default")?.takeUnless { it === JSONObject.NULL }?.toString().orEmpty(),
                placeholder = o.optString("placeholder"),
                description = o.optString("description").ifBlank { o.optString("help") },
                options = (0 until options.length()).mapNotNull {
                    val value = options.opt(it)
                    when (value) {
                        is JSONObject -> value.optString("value").ifBlank { value.optString("id") }.takeIf(String::isNotBlank)
                        else -> value?.toString()?.takeIf(String::isNotBlank)
                    }
                }
            )
        }
    }
}

data class AutomationBlueprint(
    val id: String,
    val name: String,
    val description: String = "",
    val category: String = "",
    val fields: List<AutomationBlueprintField> = emptyList()
) {
    companion object {
        fun fromJson(o: JSONObject): AutomationBlueprint {
            val fields = o.optJSONArray("fields") ?: o.optJSONArray("inputs") ?: JSONArray()
            return AutomationBlueprint(
                id = o.optString("id").ifBlank { o.optString("key").ifBlank { o.optString("name") } },
                name = o.optString("name").ifBlank { o.optString("title").ifBlank { o.optString("id") } },
                description = o.optString("description"),
                category = o.optString("category"),
                fields = fields.objects().map(AutomationBlueprintField::fromJson)
            )
        }
    }
}

data class MessagingSetupField(
    val key: String,
    val label: String,
    val type: String = "text",
    val required: Boolean = false,
    val secret: Boolean = false,
    val configured: Boolean = false,
    val valueHint: String = "",
    val placeholder: String = "",
    val description: String = ""
) {
    companion object {
        fun fromJson(o: JSONObject): MessagingSetupField {
            val key = o.optString("key").ifBlank {
                o.optString("env").ifBlank { o.optString("env_var").ifBlank { o.optString("name") } }
            }
            val type = o.optString("type", "text")
            return MessagingSetupField(
                key = key,
                label = o.optString("label").ifBlank { o.optString("prompt").ifBlank { o.optString("name").ifBlank { key } } },
                type = type,
                required = o.optBoolean("required", false),
                secret = o.optBoolean("secret", o.optBoolean("password", type.equals("password", true))),
                configured = o.optBoolean("configured", o.optBoolean("set", false)),
                valueHint = o.optString("value").ifBlank { o.optString("display_value") },
                placeholder = o.optString("placeholder"),
                description = o.optString("description").ifBlank { o.optString("help") }
            )
        }
    }
}

data class MessagingPlatform(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val configured: Boolean,
    val gatewayRunning: Boolean,
    val state: String,
    val errorMessage: String,
    val homeChannel: String = "",
    val description: String = "",
    val docsUrl: String = "",
    val setupFields: List<MessagingSetupField> = emptyList(),
    val supportsPairing: Boolean = true
) {
    companion object {
        fun fromJson(o: JSONObject): MessagingPlatform {
            val id = o.optString("id").ifBlank { o.optString("platform") }
            val home = o.optJSONObject("home_channel")
            val fieldArray = o.optJSONArray("setup_fields")
                ?: o.optJSONArray("fields")
                ?: o.optJSONArray("env_vars")
            val parsedFields = if (fieldArray != null) {
                fieldArray.objects().map(MessagingSetupField::fromJson)
            } else {
                // A few gateway builds expose env metadata as an object keyed by env name.
                val envObject = o.optJSONObject("env")
                if (envObject == null) emptyList() else buildList {
                    val keys = envObject.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        when (val raw = envObject.opt(key)) {
                            is JSONObject -> add(MessagingSetupField.fromJson(JSONObject(raw.toString()).put("key", key)))
                            else -> add(MessagingSetupField(key = key, label = key, configured = raw != null && raw !== JSONObject.NULL))
                        }
                    }
                }
            }
            return MessagingPlatform(
                id = id,
                name = o.optString("name").ifBlank { o.optString("label").ifBlank { id } },
                enabled = o.optBoolean("enabled", false),
                configured = o.optBoolean("configured", o.optBoolean("credentials_configured", o.optBoolean("enabled", false))),
                gatewayRunning = o.optBoolean("gateway_running", o.optBoolean("connected", o.optBoolean("running", false))),
                state = o.optString("state").ifBlank { o.optString("status") },
                errorMessage = o.optString("error_message").ifBlank { o.optString("error") },
                homeChannel = home?.optString("label").orEmpty().ifBlank { home?.optString("id").orEmpty() }.ifBlank { o.optString("home_channel") },
                description = o.optString("description"),
                docsUrl = o.optString("docs_url").ifBlank { o.optString("setup_url") },
                setupFields = parsedFields.filter { it.key.isNotBlank() },
                supportsPairing = o.optBoolean("supports_pairing", true)
            )
        }
    }
}

data class PairingUser(
    val platform: String,
    val userId: String,
    val userName: String,
    val requestId: String? = null,
    val code: String? = null,
    val createdAt: String = "",
    val pending: Boolean
)

data class TelegramOnboarding(
    val pairingId: String,
    val deepLink: String,
    val qrPayload: String,
    val suggestedUsername: String,
    val expiresAt: String,
    val status: String = "waiting",
    val botUsername: String? = null,
    val ownerUserId: String? = null
)

data class WebhookRoute(
    val name: String,
    val description: String = "",
    val url: String = "",
    val events: List<String> = emptyList(),
    val prompt: String = "",
    val deliver: String = "log",
    val deliverOnly: Boolean = false,
    val enabled: Boolean = true,
    val skills: List<String> = emptyList(),
    val cronJob: String = ""
) {
    companion object {
        fun fromJson(o: JSONObject): WebhookRoute {
            fun stringList(key: String): List<String> {
                val value = o.opt(key)
                return when (value) {
                    is JSONArray -> (0 until value.length()).mapNotNull { value.optString(it).takeIf(String::isNotBlank) }
                    is String -> value.split(',').map(String::trim).filter(String::isNotBlank)
                    else -> emptyList()
                }
            }
            return WebhookRoute(
                name = o.optString("name"),
                description = o.optString("description"),
                url = o.optString("url").ifBlank { o.optString("route_url") },
                events = stringList("events"),
                prompt = o.optString("prompt"),
                deliver = o.optString("deliver", "log"),
                deliverOnly = o.optBoolean("deliver_only", false),
                enabled = o.optBoolean("enabled", true),
                skills = stringList("skills"),
                cronJob = o.optString("cron_job")
            )
        }
    }
}

data class WebhookState(
    val platformEnabled: Boolean = false,
    val routes: List<WebhookRoute> = emptyList()
)

data class WebhookReceipt(
    val name: String,
    val url: String,
    val secret: String
)

data class WebhookDraft(
    val name: String,
    val description: String,
    val events: List<String>,
    val prompt: String,
    val deliver: String,
    val deliverOnly: Boolean = false,
    val skills: List<String> = emptyList(),
    val cronJob: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("description", description)
        put("events", JSONArray(events.filter(String::isNotBlank)))
        put("prompt", prompt)
        put("deliver", deliver.ifBlank { "log" })
        put("deliver_only", deliverOnly)
        put("skills", JSONArray(skills.filter(String::isNotBlank)))
        if (cronJob.isNotBlank()) put("cron_job", cronJob)
    }
}

data class ServerInteraction(
    val requestId: String,
    val method: String,
    val params: JSONObject,
    val replayed: Boolean = false
)


// ---------------------------------------------------------------------------
// Global Capabilities / Settings surface (alpha8)
// ---------------------------------------------------------------------------

data class CapabilityModelInfo(
    val provider: String = "",
    val model: String = "",
    val effectiveContextLength: Long? = null,
    val supportsTools: Boolean? = null,
    val supportsVision: Boolean? = null,
    val supportsReasoning: Boolean? = null
) {
    companion object {
        fun fromJson(o: JSONObject): CapabilityModelInfo {
            val capabilities = o.optJSONObject("capabilities") ?: JSONObject()
            return CapabilityModelInfo(
                provider = o.optString("provider"),
                model = o.optString("model"),
                effectiveContextLength = o.optLongOrNull("effective_context_length"),
                supportsTools = capabilities.optBooleanOrNull("supports_tools"),
                supportsVision = capabilities.optBooleanOrNull("supports_vision"),
                supportsReasoning = capabilities.optBooleanOrNull("supports_reasoning")
            )
        }
    }
}

data class CapabilitySkillDocument(
    val name: String,
    val content: String,
    val path: String = ""
)

data class CapabilitySkill(
    val name: String,
    val description: String = "",
    val category: String = "",
    val enabled: Boolean = true,
    val provenance: String = "",
    val usage: Int? = null,
    val installed: Boolean = true,
    val identifier: String = "",
    val tags: List<String> = emptyList()
) {
    companion object {
        fun fromJson(o: JSONObject, installed: Boolean = true): CapabilitySkill {
            val tags = o.optJSONArray("tags") ?: JSONArray()
            return CapabilitySkill(
                name = o.optString("name"),
                description = o.optString("description"),
                category = o.optString("category"),
                enabled = o.optBoolean("enabled", true),
                provenance = o.optString("provenance"),
                usage = if (o.has("usage") && !o.isNull("usage")) o.optInt("usage") else null,
                installed = if (o.has("installed")) o.optBoolean("installed") else installed,
                identifier = o.optString("identifier").ifBlank { o.optString("name") },
                tags = (0 until tags.length()).mapNotNull { tags.optString(it).takeIf(String::isNotBlank) }
            )
        }
    }
}

data class CapabilityToolset(
    val name: String,
    val label: String = "",
    val description: String = "",
    val enabled: Boolean = false,
    val configured: Boolean = false,
    val tools: List<String> = emptyList()
) {
    companion object {
        fun fromJson(o: JSONObject): CapabilityToolset {
            val tools = o.optJSONArray("tools") ?: JSONArray()
            return CapabilityToolset(
                name = o.optString("name"),
                label = o.optString("label"),
                description = o.optString("description"),
                enabled = o.optBoolean("enabled", false),
                configured = o.optBoolean("configured", false),
                tools = (0 until tools.length()).mapNotNull { tools.optString(it).takeIf(String::isNotBlank) }
            )
        }
    }
}

data class CapabilityToolProvider(
    val name: String,
    val badge: String = "",
    val tag: String = "",
    val active: Boolean = false,
    val status: String = "",
    val requiresNousAuth: Boolean = false,
    val env: List<CapabilityToolEnv> = emptyList()
) {
    companion object {
        fun fromJson(o: JSONObject): CapabilityToolProvider = CapabilityToolProvider(
            name = o.optString("name"),
            badge = o.optString("badge"),
            tag = o.optString("tag"),
            active = o.optBoolean("is_active", false),
            status = o.optString("status"),
            requiresNousAuth = o.optBoolean("requires_nous_auth", false),
            env = (o.optJSONArray("env_vars") ?: JSONArray()).objects().map(CapabilityToolEnv::fromJson)
        )
    }
}

data class CapabilityToolEnv(
    val key: String,
    val prompt: String = "",
    val url: String = "",
    val defaultValue: String = "",
    val isSet: Boolean = false
) {
    companion object {
        fun fromJson(o: JSONObject) = CapabilityToolEnv(
            key = o.optString("key"),
            prompt = o.optString("prompt"),
            url = o.optString("url"),
            defaultValue = o.optString("default"),
            isSet = o.optBoolean("is_set", false)
        )
    }
}

data class CapabilityToolsetConfig(
    val name: String,
    val activeProvider: String = "",
    val providers: List<CapabilityToolProvider> = emptyList()
) {
    companion object {
        fun fromJson(o: JSONObject) = CapabilityToolsetConfig(
            name = o.optString("name"),
            activeProvider = o.optString("active_provider"),
            providers = (o.optJSONArray("providers") ?: JSONArray()).objects().map(CapabilityToolProvider::fromJson)
        )
    }
}

data class CapabilityMcpServer(
    val name: String,
    val transport: String = "",
    val command: String = "",
    val args: List<String> = emptyList(),
    val url: String = "",
    val enabled: Boolean = true,
    val tools: List<String> = emptyList()
) {
    companion object {
        fun fromJson(o: JSONObject): CapabilityMcpServer {
            fun strings(key: String): List<String> {
                val a = o.optJSONArray(key) ?: return emptyList()
                return (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
            }
            return CapabilityMcpServer(
                name = o.optString("name"),
                transport = o.optString("transport"),
                command = o.optString("command"),
                args = strings("args"),
                url = o.optString("url"),
                enabled = o.optBoolean("enabled", true),
                tools = strings("tools")
            )
        }
    }
}

data class CapabilityMcpCatalogEntry(
    val name: String,
    val description: String = "",
    val transport: String = "",
    val authType: String = "",
    val installed: Boolean = false,
    val enabled: Boolean = false,
    val needsInstall: Boolean = false,
    val url: String = "",
    val command: String = "",
    val requiredEnv: List<String> = emptyList()
) {
    companion object {
        fun fromJson(o: JSONObject): CapabilityMcpCatalogEntry = CapabilityMcpCatalogEntry(
            name = o.optString("name"),
            description = o.optString("description"),
            transport = o.optString("transport"),
            authType = o.optString("auth_type"),
            installed = o.optBoolean("installed", false),
            enabled = o.optBoolean("enabled", false),
            needsInstall = o.optBoolean("needs_install", false),
            url = o.optString("url"),
            command = o.optString("command"),
            requiredEnv = (o.optJSONArray("required_env") ?: JSONArray()).objects().mapNotNull {
                it.optString("name").takeIf(String::isNotBlank)
            }
        )
    }
}

data class CapabilityEnvVar(
    val key: String,
    val isSet: Boolean = false,
    val redactedValue: String = "",
    val description: String = "",
    val url: String = "",
    val category: String = "",
    val password: Boolean = false,
    val advanced: Boolean = false,
    val channelManaged: Boolean = false,
    val provider: String = "",
    val providerLabel: String = "",
    val tools: List<String> = emptyList()
) {
    companion object {
        fun fromJson(key: String, o: JSONObject): CapabilityEnvVar {
            val tools = o.optJSONArray("tools") ?: JSONArray()
            return CapabilityEnvVar(
                key = key,
                isSet = o.optBoolean("is_set", false),
                redactedValue = o.optString("redacted_value"),
                description = o.optString("description"),
                url = o.optString("url"),
                category = o.optString("category"),
                password = o.optBoolean("is_password", false),
                advanced = o.optBoolean("advanced", false),
                channelManaged = o.optBoolean("channel_managed", false),
                provider = o.optString("provider"),
                providerLabel = o.optString("provider_label"),
                tools = (0 until tools.length()).mapNotNull { tools.optString(it).takeIf(String::isNotBlank) }
            )
        }
    }
}

data class CapabilityCustomEndpoint(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val apiMode: String = "",
    val discoverModels: Boolean = true,
    val hasApiKey: Boolean = false,
    val apiKeyPreview: String = "",
    val models: List<String> = emptyList(),
    val current: Boolean = false
) {
    companion object {
        fun fromJson(o: JSONObject): CapabilityCustomEndpoint {
            val models = o.optJSONArray("models") ?: JSONArray()
            return CapabilityCustomEndpoint(
                id = o.optString("id"),
                name = o.optString("name"),
                baseUrl = o.optString("base_url"),
                model = o.optString("model"),
                apiMode = o.optString("api_mode"),
                discoverModels = o.optBoolean("discover_models", true),
                hasApiKey = o.optBoolean("has_api_key", false),
                apiKeyPreview = o.optString("api_key_preview"),
                models = (0 until models.length()).mapNotNull { models.optString(it).takeIf(String::isNotBlank) },
                current = o.optBoolean("is_current", false)
            )
        }
    }
}

data class CapabilityOAuthProvider(
    val id: String,
    val name: String,
    val flow: String,
    val docsUrl: String = "",
    val loggedIn: Boolean = false,
    val account: String = "",
    val statusError: String = ""
) {
    companion object {
        fun fromJson(o: JSONObject): CapabilityOAuthProvider {
            val status = o.optJSONObject("status") ?: JSONObject()
            return CapabilityOAuthProvider(
                id = o.optString("id"),
                name = o.optString("name").ifBlank { o.optString("id") },
                flow = o.optString("flow"),
                docsUrl = o.optString("docs_url"),
                loggedIn = status.optBoolean("logged_in", false),
                account = status.optString("account_email").ifBlank { status.optString("source_label") },
                statusError = status.optString("error")
            )
        }
    }
}

data class CapabilityOAuthFlow(
    val providerId: String,
    val sessionId: String,
    val flow: String,
    val authUrl: String = "",
    val verificationUrl: String = "",
    val userCode: String = "",
    val expiresIn: Int = 0,
    val pollInterval: Int = 5,
    val status: String = "starting",
    val error: String = ""
)

data class CapabilitySnapshot(
    val profile: String,
    val modelInfo: CapabilityModelInfo = CapabilityModelInfo(),
    val modelProviders: List<ModelProviderOption> = emptyList(),
    val skills: List<CapabilitySkill> = emptyList(),
    val officialSkills: List<CapabilitySkill> = emptyList(),
    val toolsets: List<CapabilityToolset> = emptyList(),
    val mcpServers: List<CapabilityMcpServer> = emptyList(),
    val mcpCatalog: List<CapabilityMcpCatalogEntry> = emptyList(),
    val envVars: List<CapabilityEnvVar> = emptyList(),
    val customEndpoints: List<CapabilityCustomEndpoint> = emptyList(),
    val oauthProviders: List<CapabilityOAuthProvider> = emptyList(),
    val unsupported: Set<String> = emptySet()
)
