package com.nousresearch.hermesmobile.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

/**
 * Short-lived, non-UI gateway connection used for fleet operations (Bot roster / RoomLink).
 * It owns no application state and never changes the foreground Hermes connection.
 */
class RemoteGatewaySession(
    private val config: GatewayConfig,
    onConfigUpdated: (GatewayConfig) -> Unit = {}
) : AutoCloseable {
    private val ready = CompletableDeferred<Unit>()
    private val failed = CompletableDeferred<Throwable>()
    val rest = HermesRestClient(config, onConfigUpdated = onConfigUpdated)
    val gateway = HermesGatewayClient(
        config = config,
        listener = object : HermesGatewayListener {
            override fun onConnectionState(state: GatewayConnectionState, error: String?) {
                if (state == GatewayConnectionState.FAILED && !failed.isCompleted) {
                    failed.complete(IllegalStateException(error ?: "Remote Hermes gateway connection failed"))
                }
            }

            override fun onGatewayReady(payload: org.json.JSONObject) {
                if (!ready.isCompleted) ready.complete(Unit)
            }
        },
        wsTicketProvider = {
            when (config.authMode) {
                GatewayAuthMode.BASIC, GatewayAuthMode.NATIVE_PKCE -> rest.wsTicket()
                else -> null
            }
        }
    )

    suspend fun connect(timeoutMs: Long = 20_000L): RemoteGatewaySession {
        gateway.connect()
        withTimeout(timeoutMs) {
            while (!ready.isCompleted) {
                if (failed.isCompleted) throw failed.await()
                kotlinx.coroutines.delay(25)
            }
            ready.await()
        }
        return this
    }

    override fun close() {
        gateway.close()
    }
}
