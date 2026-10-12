package com.nousresearch.hermesmobile.push

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/** The `hermes-android` plugin's answer to `/android-pair` (see the plugin's setup.pairing_payload). */
data class ServerPairing(
    val profile: String,
    val ready: Boolean,
    val port: Int,
    val reachableFromPhone: Boolean,
    val secret: String,
    val problem: String,
    val keyState: String,
    val keyProject: String,
    val pluginVersion: String
)

/** Where the server stands, as the Notifications wizard shows it. */
enum class ServerState { UNKNOWN, PLUGIN_MISSING, API_SERVER_OFF, API_SERVER_LOOPBACK, SECRET_MISSING, WRONG_PROFILE, READY }

object PushSetup {
    const val PAIR_COMMAND = "android-pair"
    const val CREDENTIALS_COMMAND = "android-credentials"
    const val TEST_COMMAND = "android-test"
    const val MAX_KEY_BYTES = 64 * 1024

    fun parsePairing(output: String): ServerPairing {
        val o = runCatching { JSONObject(output.trim()) }.getOrNull()
        require(o != null && o.optInt("hermes_android_pairing") == 1) { "not_a_pairing_reply" }
        val api = o.optJSONObject("api_server") ?: JSONObject()
        val key = o.optJSONObject("server_key") ?: JSONObject()
        return ServerPairing(
            profile = o.optString("profile"),
            ready = o.optBoolean("ready"),
            port = api.optInt("port", PushClient.DEFAULT_API_SERVER_PORT),
            reachableFromPhone = api.optBoolean("reachable_from_phone", true),
            secret = o.optString("secret"),
            problem = o.optString("problem"),
            keyState = key.optString("state", "missing"),
            keyProject = key.optString("project_id"),
            pluginVersion = o.optString("plugin_version")
        )
    }

    fun state(pairing: ServerPairing?, expectedProfile: String): ServerState = when {
        pairing == null -> ServerState.UNKNOWN
        pairing.profile.isNotBlank() && pairing.profile != expectedProfile -> ServerState.WRONG_PROFILE
        pairing.problem == "api_server_disabled" -> ServerState.API_SERVER_OFF
        !pairing.reachableFromPhone -> ServerState.API_SERVER_LOOPBACK
        !pairing.ready || pairing.secret.length < 32 -> ServerState.SECRET_MISSING
        else -> ServerState.READY
    }

    /**
     * The API server's base URL as the phone reaches it: the host the chat already uses (it is known
     * to be reachable; the server may report a bind address such as 0.0.0.0) with the API server's port.
     */
    fun apiBase(gatewayBaseUrl: String, pairing: ServerPairing): String =
        gatewayBaseUrl.trim().toHttpUrlOrNull()?.newBuilder()?.port(pairing.port)?.encodedPath("/")
            ?.build()?.toString()?.trimEnd('/').orEmpty()

    /** A `/android-credentials` / `/android-test` reply: {"ok": true, ...} or {"ok": false, "error": "..."}. */
    fun commandReply(output: String): JSONObject {
        val o = runCatching { JSONObject(output.trim()) }.getOrNull() ?: throw IllegalStateException(output.take(200))
        if (!o.optBoolean("ok")) throw IllegalStateException(o.optString("error").ifBlank { "error" })
        return o
    }
}
