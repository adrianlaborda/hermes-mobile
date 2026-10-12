package com.nousresearch.hermesmobile.data

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Small, connection-local compatibility layer for gateway RPC drift.
 *
 * Hermes intentionally validates JSON-RPC params strictly. A newer client can therefore
 * encounter an older gateway that implements the method but rejects a newly-added optional
 * field with Hermes contract code 4000 (or legacy JSON-RPC -32602). For optional fields only, we may safely retry after removing the exact
 * field named by the server. Required/routing fields are never stripped.
 *
 * The cache belongs to one HermesRepository (and therefore one concrete connection config),
 * so a downgrade learned from an older gateway never leaks into another saved gateway.
 */
class GatewayCompatibility(private val gateway: HermesGatewayClient) {
    private val unsupportedParams = ConcurrentHashMap<String, MutableSet<String>>()
    private val missingMethods = ConcurrentHashMap.newKeySet<String>()

    suspend fun requestObject(
        method: String,
        required: JSONObject,
        optional: LinkedHashMap<String, Any?> = linkedMapOf(),
        timeoutMs: Long = 120_000L
    ): JSONObject {
        if (missingMethods.contains(method)) {
            throw GatewayRpcException("Gateway does not implement $method", code = -32601)
        }

        val disabled = unsupportedParams[method]?.toSet().orEmpty()
        val remaining = optional.keys.filterNot { it in disabled }.toMutableSet()

        while (true) {
            val params = JSONObject(required.toString())
            optional.forEach { (key, value) ->
                if (key in remaining) params.put(key, value ?: JSONObject.NULL)
            }

            try {
                return gateway.requestObject(method, params, timeoutMs)
            } catch (error: GatewayRpcException) {
                if (error.code == -32601) {
                    missingMethods += method
                    throw error
                }

                val rejected = if (error.code == 4000 || error.code == -32602) {
                    rejectedOptionalField(error, remaining)
                } else {
                    null
                }

                if (rejected == null) throw error

                unsupportedParams.computeIfAbsent(method) { ConcurrentHashMap.newKeySet() } += rejected
                remaining -= rejected
            }
        }
    }

    suspend fun requestOptionalObject(
        method: String,
        required: JSONObject = JSONObject(),
        optional: LinkedHashMap<String, Any?> = linkedMapOf(),
        timeoutMs: Long = 120_000L
    ): JSONObject? {
        if (missingMethods.contains(method)) return null
        return try {
            requestObject(method, required, optional, timeoutMs)
        } catch (error: GatewayRpcException) {
            if (error.code == -32601) null else throw error
        }
    }

    private fun rejectedOptionalField(error: GatewayRpcException, candidates: Set<String>): String? {
        if (candidates.isEmpty()) return null

        val detail = buildString {
            append(error.message.orEmpty())
            when (val data = error.data) {
                is JSONObject -> append(' ').append(data.toString())
                null, JSONObject.NULL -> Unit
                else -> append(' ').append(data.toString())
            }
        }

        // Retry only for strict-schema "this key does not exist" failures. Do not downgrade
        // validation failures about the VALUE of an otherwise-supported field.
        val lower = detail.lowercase()
        val unknownFieldFailure =
            "extra inputs are not permitted" in lower ||
                "extra fields not permitted" in lower ||
                "unexpected keyword" in lower ||
                "unexpected field" in lower ||
                "unknown field" in lower ||
                "unrecognized field" in lower ||
                "additional properties are not allowed" in lower
        if (!unknownFieldFailure) return null

        return candidates.firstOrNull { key ->
            Regex("(^|[^A-Za-z0-9_])${Regex.escape(key)}([^A-Za-z0-9_]|$)")
                .containsMatchIn(detail)
        }
    }
}
