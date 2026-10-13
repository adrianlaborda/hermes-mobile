package com.nousresearch.hermesmobile.data

import android.os.SystemClock
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private const val DEFAULT_REQUEST_TIMEOUT_MS = 120_000L
private const val HEARTBEAT_INTERVAL_MS = 15_000L
private const val HEARTBEAT_DEADLINE_MS = 45_000L
private const val REPLAY_REQUEST_TIMEOUT_MS = 10_000L

enum class GatewayConnectionState { DISCONNECTED, CONNECTING, CONNECTED, FAILED }

class GatewayRpcException(
    message: String,
    val code: Int? = null,
    val data: Any? = null
) : IllegalStateException(message)

interface HermesGatewayListener {
    fun onConnectionState(state: GatewayConnectionState, error: String? = null) {}
    fun onGatewayReady(payload: JSONObject) {}
    fun onAuthenticationExpired() { onConnectionState(GatewayConnectionState.FAILED, "Hermes sign-in has expired") }
    fun onEvent(type: String, payload: JSONObject) {}
    fun onServerRequest(request: ServerInteraction) {}
}

/**
 * Android counterpart of apps/shared JsonRpcGatewayClient + JsonRpcRequestChannel.
 *
 * Important invariants copied from upstream:
 * - socket callbacks are accepted only from the current connection generation;
 * - gateway.ready advertises server_requests before the UI resumes a session;
 * - open_requests returned by resume/replay are re-delivered before the RPC completes;
 * - seq watermarks drive best-effort session.events.since replay after reconnect;
 * - any inbound frame counts as heartbeat liveness, while gateway.ping detects silent drops.
 */
class HermesGatewayClient(
    private val config: GatewayConfig,
    private val listener: HermesGatewayListener,
    private val wsTicketProvider: suspend () -> String? = { null },
    private val socketFactory: WebSocket.Factory? = null,
    private val reconnectDelayMillis: (Int) -> Long = { ReconnectPolicy.delayMillis(it, Random.nextDouble()) },
    private val handshakeTimeoutMillis: Long = 25_000,
    private val clockMillis: () -> Long = { SystemClock.elapsedRealtime() },
    private val client: OkHttpClient? = null
) {
    private val transport by lazy { client ?: OkHttpClient.Builder()
        // Protocol liveness is handled with gateway.ping below. Keep a conservative
        // WebSocket ping as a transport-level NAT/proxy keepalive too.
        .pingInterval(25, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build() }
    private val validatedBaseUrl = GatewayUrlPolicy.canonical(config.baseUrl)

    private data class PendingCall(val deferred: CompletableDeferred<Any?>)

    private data class GatewayEventFrame(
        val type: String,
        val payload: JSONObject,
        val sessionId: String?,
        val seq: Long?,
        val replayed: Boolean = false
    )

    private data class ReplayBucket(
        val events: MutableList<GatewayEventFrame> = mutableListOf(),
        val done: CompletableDeferred<Boolean> = CompletableDeferred()
    )

    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(supervisor + Dispatchers.IO)
    private val nextId = AtomicLong(1)
    private val nextHeartbeatId = AtomicLong(1)
    private val nextGeneration = AtomicLong(0)
    private val pending = ConcurrentHashMap<String, PendingCall>()
    private val replayLock = Any()
    private val lastSeenSeq = linkedMapOf<String, Long>()
    private var replayHold: MutableMap<String, ReplayBucket>? = null

    @Volatile private var socket: WebSocket? = null
    @Volatile private var activeGeneration: Long = 0L
    @Volatile private var state = GatewayConnectionState.DISCONNECTED
    @Volatile private var manualClose = false
    @Volatile private var lastInboundAt = 0L
    @Volatile private var replayEpoch: String? = null
    @Volatile private var backendCountsDeclines = false

    @Volatile private var foreground = true
    @Volatile private var networkAvailable = true
    private var handshakeJob: Job? = null
    private var reconnectAttempt = 0
    private var rejectedSocketTickets = 0
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private val replayJobs = ConcurrentHashMap<String, Job>()

    @Synchronized
    fun connect() {
        manualClose = false
        reconnectJob?.cancel()
        reconnectJob = null
        connectSocket()
    }

    @Synchronized
    private fun connectSocket() {
        if (!foreground || !networkAvailable || manualClose || state == GatewayConnectionState.CONNECTING || state == GatewayConnectionState.CONNECTED) return

        val generation = nextGeneration.incrementAndGet()
        activeGeneration = generation
        state = GatewayConnectionState.CONNECTING
        listener.onConnectionState(state)

        handshakeJob?.cancel()
        handshakeJob = scope.launch {
            delay(handshakeTimeoutMillis)
            if (generation == activeGeneration && state == GatewayConnectionState.CONNECTING) {
                invalidateGeneration(generation, "Gateway handshake timed out; reconnecting…")
            }
        }
        scope.launch {
            val ticket = try {
                // Current auth-gated gateways require a fresh, single-use WebSocket ticket for
                // every connect/reconnect. Legacy token-only gateways leave the provider null and
                // continue to use ?token= below.
                val issued = wsTicketProvider()?.takeIf { it.isNotBlank() }
                if (issued == null && (config.authMode == GatewayAuthMode.BASIC || config.authMode == GatewayAuthMode.NATIVE_PKCE)) {
                    error("Hermes did not return a WebSocket ticket")
                }
                issued
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                synchronized(this@HermesGatewayClient) {
                if (generation == activeGeneration && !manualClose) {
                    val authExpired = (t as? HermesHttpException)?.status == 401 || (t as? GatewayAuthException)?.status == 401
                    if (authExpired) { manualClose = true; listener.onAuthenticationExpired() }
                    dropGeneration(generation, true, if (authExpired) "Hermes sign-in has expired" else "Reconnecting to Hermes…", t)
                }
                }
                return@launch
            }
            if (generation != activeGeneration || manualClose) return@launch
            val request = Request.Builder().url(webSocketUrl(ticket))
            config.extraHeaders.forEach { (k, v) ->
                if (k.isNotBlank() && v.isNotBlank() && !isManagedHeader(k)) request.header(k, v)
            }
            synchronized(this@HermesGatewayClient) {
                if (generation != activeGeneration || manualClose) return@synchronized
                socket = (socketFactory ?: transport).newWebSocket(request.build(), WsListener(generation))
            }
        }
    }

    @Synchronized
    fun disconnect() {
        handshakeJob?.cancel()
        manualClose = true
        reconnectJob?.cancel()
        reconnectJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        cancelReplay()

        // Invalidate identity before closing. If OkHttp invokes onClosed synchronously,
        // that callback now belongs to a stale generation and cannot schedule a reconnect.
        activeGeneration = nextGeneration.incrementAndGet()
        val oldSocket = socket
        socket = null
        runCatching { oldSocket?.close(1000, "mobile client disconnect") }

        failAllPending(IllegalStateException("Gateway disconnected"))
        state = GatewayConnectionState.DISCONNECTED
        listener.onConnectionState(state)
    }

    /** Android suspension is not an authentication failure. Keep session/replay state. */
    @Synchronized
    fun setEnvironment(isForeground: Boolean, online: Boolean, networkChanged: Boolean = false) {
        val resume = isForeground && online && (!foreground || !networkAvailable || networkChanged)
        foreground = isForeground
        networkAvailable = online
        if (manualClose) return
        if (!isForeground || !online || resume) {
            reconnectJob?.cancel()
            reconnectJob = null
            handshakeJob?.cancel()
            activeGeneration = nextGeneration.incrementAndGet()
            val old = socket
            socket = null
            stopHeartbeat()
            cancelReplay()
            old?.cancel()
            failAllPending(IllegalStateException("Connection suspended; resume required"))
            state = GatewayConnectionState.DISCONNECTED
            listener.onConnectionState(state)
        }
        if (resume || (isForeground && online && state != GatewayConnectionState.CONNECTED && state != GatewayConnectionState.CONNECTING)) {
            reconnectAttempt = 0
            connectSocket()
        }
    }

    fun close() {
        disconnect()
        scope.cancel()
    }

    suspend fun awaitReady() = withTimeout(30_000) {
        while (state != GatewayConnectionState.CONNECTED) {
            check(!manualClose) { "Gateway is signed out" }
            delay(100)
        }
    }

    suspend fun request(
        method: String,
        params: JSONObject = JSONObject(),
        timeoutMs: Long = DEFAULT_REQUEST_TIMEOUT_MS
    ): Any? {
        val id = nextId.getAndIncrement().toString()
        val deferred = CompletableDeferred<Any?>()
        val frame = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("method", method)
            .put("params", params)

        val serialized = frame.toString()
        synchronized(this) {
            val ws = socket ?: error("Gateway WebSocket is not connected")
            pending[id] = PendingCall(deferred)
            if (!ws.send(serialized)) {
                pending.remove(id)
                error("Could not send gateway request")
            }
        }

        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } finally {
            pending.remove(id)
        }
    }

    suspend fun requestObject(
        method: String,
        params: JSONObject = JSONObject(),
        timeoutMs: Long = DEFAULT_REQUEST_TIMEOUT_MS
    ): JSONObject {
        return when (val result = request(method, params, timeoutMs)) {
            is JSONObject -> result
            is JSONArray -> JSONObject().put("items", result)
            null, JSONObject.NULL -> JSONObject()
            else -> JSONObject().put("value", result)
        }
    }

    /**
     * Prevent a REST transcript hydration from publishing ahead of reconnect replay.
     * Mirrors apps/shared sessionReplayBarrier(): false means the replay-owning
     * socket disappeared and the caller should abandon this read.
     */
    suspend fun awaitSessionReplay(sessionId: String, timeoutMs: Long = REPLAY_REQUEST_TIMEOUT_MS + 1_000L): Boolean {
        val waiter = synchronized(replayLock) { replayHold?.get(sessionId)?.done } ?: return true
        return runCatching { withTimeout(timeoutMs) { waiter.await() } }.getOrDefault(true)
    }

    fun respond(requestId: String, result: JSONObject) {
        sendCurrent(
            JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", requestId)
                .put("result", result)
        )
    }

    fun reject(requestId: String, message: String = "Method not implemented", code: Int = -32601) {
        sendCurrent(
            JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", requestId)
                .put("error", JSONObject().put("code", code).put("message", message))
        )
    }

    @Synchronized
    private fun sendCurrent(frame: JSONObject): Boolean {
        val ws = socket ?: return false
        return runCatching { ws.send(frame.toString()) }.getOrDefault(false)
    }

    private inner class WsListener(private val generation: Long) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) = synchronized(this@HermesGatewayClient) {
            if (!owns(webSocket, generation)) return@synchronized
            lastInboundAt = clockMillis()
            state = GatewayConnectionState.CONNECTING
            listener.onConnectionState(state)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            // Parse outside the transition lock so foreground changes never wait on JSON decoding.
            val parsed = runCatching { JSONObject(text) }
            synchronized(this@HermesGatewayClient) {
                if (!owns(webSocket, generation)) return@synchronized
                lastInboundAt = clockMillis()
                parsed.onSuccess { handleFrame(it, generation) }.onFailure {
                    listener.onEvent("gateway.protocol_error", JSONObject().put("error", it.message ?: "Invalid frame"))
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = synchronized(this@HermesGatewayClient) {
            if (!owns(webSocket, generation)) return@synchronized
            dropGeneration(generation, failed = false, error = reason.ifBlank { "WebSocket closed" })
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = synchronized(this@HermesGatewayClient) {
            if (!owns(webSocket, generation)) return@synchronized
            if (response?.code == 401 && ++rejectedSocketTickets > 1) {
                manualClose = true
                listener.onAuthenticationExpired()
            }
            dropGeneration(generation, failed = true,
                error = if (manualClose && response?.code == 401) "Hermes sign-in has expired" else "Reconnecting to Hermes…", cause = t)
        }
    }

    private fun handleFrame(frame: JSONObject, generation: Long) {
        if (generation != activeGeneration) return

        val id = frame.opt("id")?.takeUnless { it == JSONObject.NULL }?.toString()
        val method = frame.optString("method")

        if (!id.isNullOrBlank() && frame.has("result")) {
            val result = frame.opt("result")
            // Upstream guarantee: pending server→client questions survive reconnect
            // inside RPC results and must be redelivered before the caller resumes.
            deliverOpenRequests(result)
            pending[id]?.deferred?.complete(result)
            return
        }

        if (!id.isNullOrBlank() && frame.has("error")) {
            val error = frame.optJSONObject("error")
            pending[id]?.deferred?.completeExceptionally(
                GatewayRpcException(
                    message = error?.optString("message")?.takeIf { it.isNotBlank() } ?: "Gateway RPC error",
                    code = if (error?.has("code") == true) error.optInt("code") else null,
                    data = error?.opt("data")
                )
            )
            return
        }

        if (method == "event") {
            val event = frame.optJSONObject("params") ?: JSONObject()
            handleEventFrame(event, generation)
            return
        }

        if (!id.isNullOrBlank() && method.isNotBlank()) {
            listener.onServerRequest(
                ServerInteraction(
                    requestId = id,
                    method = method,
                    params = frame.optJSONObject("params") ?: JSONObject(),
                    replayed = false
                )
            )
        }
    }

    private fun handleEventFrame(event: JSONObject, generation: Long) {
        val type = event.optString("type")
        val payload = event.optJSONObject("payload") ?: JSONObject()
        val sid = event.optString("session_id").takeIf { it.isNotBlank() }
        val seq = if (event.has("seq") && !event.isNull("seq")) event.optLong("seq") else null

        if (type == "gateway.ready") {
            handshakeJob?.cancel()
            rejectedSocketTickets = 0
            reconnectAttempt = 0
            val epoch = payload.optString("replay_epoch").takeIf { it.isNotBlank() }
            if (epoch != null) adoptReplayEpoch(epoch)

            if (payload.optBoolean("heartbeat", false)) startHeartbeat(generation)
            else stopHeartbeat()

            // Start synchronously until request() sends the frame. This preserves the
            // upstream ordering: client.capabilities is put on the wire before a UI
            // onGatewayReady callback can issue session.resume.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                runCatching {
                    requestObject(
                        "client.capabilities",
                        JSONObject().put("server_requests", true),
                        timeoutMs = 10_000
                    )
                }.onSuccess { result ->
                    backendCountsDeclines = result.optBoolean("declines_not_shown", false)
                }
            }

            startReplay(generation)
            state = GatewayConnectionState.CONNECTED
            listener.onConnectionState(state)
            listener.onGatewayReady(payload)
        }

        dispatchOrHold(
            GatewayEventFrame(
                type = type,
                payload = payload,
                sessionId = sid,
                seq = seq,
                replayed = false
            )
        )
    }

    private fun deliverOpenRequests(result: Any?) {
        val root = result as? JSONObject ?: return
        val open = root.optJSONArray("open_requests") ?: return
        for (i in 0 until open.length()) {
            val item = open.optJSONObject(i) ?: continue
            val id = item.optString("id")
            val method = item.optString("method")
            if (id.isBlank() || method.isBlank()) continue
            listener.onServerRequest(
                ServerInteraction(
                    requestId = id,
                    method = method,
                    params = item.optJSONObject("params") ?: JSONObject(),
                    replayed = true
                )
            )
        }
    }

    private fun dispatchOrHold(event: GatewayEventFrame) {
        val sid = event.sessionId
        val seq = event.seq
        if (sid != null && seq != null) {
            val held = synchronized(replayLock) {
                replayHold?.get(sid)?.also { it.events += event } != null
            }
            if (held) return
        }
        dispatchIfNewer(event)
    }

    private fun dispatchIfNewer(event: GatewayEventFrame) {
        val sid = event.sessionId
        val seq = event.seq
        if (sid != null && seq != null) {
            val shouldDispatch = synchronized(replayLock) {
                val previous = lastSeenSeq[sid] ?: 0L
                if (seq <= previous) false else {
                    lastSeenSeq[sid] = seq
                    true
                }
            }
            if (!shouldDispatch) return
        }

        // Preserve envelope coordinates for the existing Android consumers.
        event.sessionId?.let { if (!event.payload.has("session_id")) event.payload.put("session_id", it) }
        event.seq?.let { if (!event.payload.has("seq")) event.payload.put("seq", it) }
        if (event.replayed) event.payload.put("replayed", true)
        listener.onEvent(event.type, event.payload)
    }

    private fun startReplay(generation: Long) {
        if (generation != activeGeneration) return
        val entries = synchronized(replayLock) {
            if (lastSeenSeq.isEmpty() || replayHold != null) return
            val snapshot = lastSeenSeq.toMap()
            replayHold = snapshot.keys.associateWith { ReplayBucket() }.toMutableMap()
            snapshot
        }

        entries.forEach { (sid, lastSeen) ->
            replayJobs[sid]?.cancel()
            replayJobs[sid] = scope.launch {
                try {
                    val result = requestObject(
                        "session.events.since",
                        JSONObject().put("session_id", sid).put("last_seen", lastSeen),
                        timeoutMs = REPLAY_REQUEST_TIMEOUT_MS
                    )
                    if (generation != activeGeneration) return@launch

                    val epoch = result.optString("epoch").takeIf { it.isNotBlank() }
                    if (epoch != null && replayEpoch != null && epoch != replayEpoch) {
                        adoptReplayEpoch(epoch)
                        return@launch
                    }
                    if (epoch != null && replayEpoch == null) replayEpoch = epoch

                    val events = result.optJSONArray("events") ?: JSONArray()
                    for (i in 0 until events.length()) {
                        if (generation != activeGeneration) return@launch
                        val raw = events.optJSONObject(i) ?: continue
                        val type = raw.optString("type")
                        if (type.isBlank()) continue
                        dispatchIfNewer(
                            GatewayEventFrame(
                                type = type,
                                payload = raw.optJSONObject("payload") ?: JSONObject(),
                                sessionId = raw.optString("session_id").takeIf { it.isNotBlank() },
                                seq = if (raw.has("seq") && !raw.isNull("seq")) raw.optLong("seq") else null,
                                replayed = true
                            )
                        )
                    }
                } catch (_: Throwable) {
                    // Replay is a recovery optimization. session.resume/REST history remain authoritative.
                } finally {
                    replayJobs.remove(sid)
                    flushReplayHold(sid, generation)
                }
            }
        }
    }

    private fun flushReplayHold(sessionId: String, generation: Long) {
        while (generation == activeGeneration) {
            val next = synchronized(replayLock) {
                val bucket = replayHold?.get(sessionId) ?: return
                if (bucket.events.isNotEmpty()) {
                    bucket.events.removeAt(0)
                } else {
                    replayHold?.remove(sessionId)
                    if (!bucket.done.isCompleted) bucket.done.complete(true)
                    if (replayHold?.isEmpty() == true) replayHold = null
                    null
                }
            } ?: return
            dispatchIfNewer(next.copy(replayed = true))
        }
    }

    private fun adoptReplayEpoch(epoch: String) {
        val changed = replayEpoch != null && replayEpoch != epoch
        replayEpoch = epoch
        if (!changed) return

        val parked = synchronized(replayLock) {
            lastSeenSeq.clear()
            val buckets = replayHold?.values.orEmpty()
            val frames = buckets.flatMap { it.events }
            buckets.forEach { if (!it.done.isCompleted) it.done.complete(true) }
            replayHold = null
            frames
        }
        replayJobs.values.forEach { it.cancel() }
        replayJobs.clear()
        // A backend restart invalidates old seq numbering. Parked frames arrived on
        // the new process/socket, so let them through without the obsolete watermark.
        parked.forEach { dispatchIfNewer(it.copy(replayed = true)) }
    }

    private fun cancelReplay() {
        replayJobs.values.forEach { it.cancel() }
        replayJobs.clear()
        synchronized(replayLock) {
            replayHold?.values?.forEach { if (!it.done.isCompleted) it.done.complete(false) }
            replayHold = null
        }
    }

    private fun startHeartbeat(generation: Long) {
        heartbeatJob?.cancel()
        lastInboundAt = clockMillis()
        heartbeatJob = scope.launch {
            while (isActive && generation == activeGeneration && !manualClose) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (generation != activeGeneration || manualClose) break

                if (clockMillis() - lastInboundAt >= HEARTBEAT_DEADLINE_MS) {
                    invalidateGeneration(generation, "Gateway heartbeat acknowledgement timed out")
                    break
                }

                val ws = socket ?: break
                val id = "heartbeat-${nextHeartbeatId.getAndIncrement()}"
                val sent = ws.send(
                    JSONObject()
                        .put("jsonrpc", "2.0")
                        .put("id", id)
                        .put("method", "gateway.ping")
                        .put("params", JSONObject())
                        .toString()
                )
                if (!sent) {
                    invalidateGeneration(generation, "Could not send gateway heartbeat")
                    break
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    @Synchronized
    private fun invalidateGeneration(generation: Long, message: String) {
        if (generation != activeGeneration) return
        val ws = socket
        dropGeneration(generation, failed = true, error = message)
        runCatching { ws?.cancel() }
    }

    @Synchronized
    private fun dropGeneration(
        generation: Long,
        failed: Boolean,
        error: String,
        cause: Throwable? = null
    ) {
        if (generation != activeGeneration) return

        activeGeneration = nextGeneration.incrementAndGet()
        handshakeJob?.cancel()
        socket = null
        stopHeartbeat()
        cancelReplay()
        failAllPending(cause ?: IllegalStateException(error))
        state = if (failed) GatewayConnectionState.FAILED else GatewayConnectionState.DISCONNECTED
        listener.onConnectionState(state, error)
        scheduleReconnect()
    }

    private fun failAllPending(error: Throwable) {
        pending.values.forEach { it.deferred.completeExceptionally(error) }
        pending.clear()
    }

    @Synchronized
    private fun scheduleReconnect() {
        if (!foreground || !networkAvailable || manualClose || reconnectJob?.isActive == true) return
        val attempt = reconnectAttempt++
        val delayMs = reconnectDelayMillis(attempt)
        reconnectJob = scope.launch {
            delay(delayMs)
            synchronized(this@HermesGatewayClient) {
                if (!isActive) return@synchronized
                reconnectJob = null
                if (foreground && networkAvailable && !manualClose && state != GatewayConnectionState.CONNECTED && state != GatewayConnectionState.CONNECTING) {
                    state = GatewayConnectionState.DISCONNECTED
                    connectSocket()
                }
            }
        }
    }

    @Synchronized
    private fun owns(webSocket: WebSocket, generation: Long): Boolean =
        generation == activeGeneration && socket === webSocket

    private fun webSocketUrl(ticket: String? = null): String {
        val raw = validatedBaseUrl
        val wsBase = when {
            raw.startsWith("https://", true) -> "wss://" + raw.substringAfter("://")
            raw.startsWith("http://", true) -> "ws://" + raw.substringAfter("://")
            raw.startsWith("wss://", true) || raw.startsWith("ws://", true) -> raw
            else -> "wss://$raw"
        }
        val authPart = when {
            !ticket.isNullOrBlank() -> "?ticket=" + URLEncoder.encode(ticket, Charsets.UTF_8.name())
            config.token.isNotBlank() -> "?token=" + URLEncoder.encode(config.token, Charsets.UTF_8.name())
            else -> ""
        }
        return "$wsBase/api/ws$authPart"
    }

    private fun isManagedHeader(name: String): Boolean = name.lowercase(Locale.ROOT) in setOf(
        "authorization", "cookie", "host", "origin", "referer", "x-hermes-session-token", "connection", "upgrade"
    )
}
