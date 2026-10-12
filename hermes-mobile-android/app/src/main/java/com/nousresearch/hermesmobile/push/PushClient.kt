package com.nousresearch.hermesmobile.push

import android.content.Context
import com.nousresearch.hermesmobile.data.GatewayConfigStore
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class PushAccount(val connection: String, val profile: String, val endpoint: String, val secret: String,
    val device: String = "", val deviceSecret: String = "") {
    val key get() = "$connection:$profile"
    fun json() = JSONObject().put("connection", connection).put("profile", profile).put("endpoint", endpoint)
        .put("secret", secret).put("device", device).put("deviceSecret", deviceSecret)
    companion object {
        fun from(o: JSONObject) = PushAccount(o.getString("connection"), o.getString("profile"), o.getString("endpoint"),
            o.getString("secret"), o.optString("device"), o.optString("deviceSecret"))
    }
}

class PushStore(context: Context) {
    private val secure = GatewayConfigStore(context)
    fun accounts(): List<PushAccount> = synchronized(lock) {
        val a = JSONArray(secure.readPrivate("push_accounts") ?: "[]")
        (0 until a.length()).map { PushAccount.from(a.getJSONObject(it)) }
    }
    fun save(account: PushAccount) = synchronized(lock) {
        secure.writePrivate("push_accounts", JSONArray((accounts().filterNot { it.key == account.key } + account).map { it.json() }).toString())
        changes.value += 1
    }
    fun remove(key: String) = synchronized(lock) {
        secure.writePrivate("push_accounts", JSONArray(accounts().filterNot { it.key == key }.map { it.json() }).toString())
        changes.value += 1
    }
    fun forgetConnection(connection: String): List<PushAccount> = synchronized(lock) {
        val removed = accounts().filter { it.connection == connection }
        removed.forEach { remove(it.key) }
        secure.writePrivate("push_events", JSONArray(events().filterNot { it.optString("connection") == connection }).toString())
        removed
    }
    fun events(): List<JSONObject> = synchronized(lock) {
        val a = JSONArray(secure.readPrivate("push_events") ?: "[]")
        (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optLong("received_at") > System.currentTimeMillis() - 7 * 86400000L }
    }
    fun received(account: PushAccount, event: JSONObject, overwrite: Boolean = true) = synchronized(lock) {
        if (!overwrite && events().any { it.optString("event_id") == event.optString("event_id") }) return@synchronized
        val item = JSONObject(event.toString()).put("connection", account.connection).put("profile", account.profile)
            .put("received_at", System.currentTimeMillis())
        val entries = events().filter { it.optLong("received_at") > System.currentTimeMillis() - 7 * 86400000L }
            .filterNot { it.optString("event_id") == event.getString("event_id") } + item
        secure.writePrivate("push_events", JSONArray(entries.takeLast(100)).toString())
        changes.value += 1
    }
    companion object { private val lock = Any(); val changes = kotlinx.coroutines.flow.MutableStateFlow(0L) }
}

class PushRpcException(val code: String) : Exception(code)

object PushClient {
    private val http by lazy { OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build() }
    /** Same rule as the chat gateway: HTTPS, or plain HTTP only to a private/local host (LAN, VPN). */
    private fun transportAllowed(url: okhttp3.HttpUrl): Boolean =
        url.isHttps || com.nousresearch.hermesmobile.data.GatewayUrlPolicy.isPrivateOrLocalHost(url.host)

    /** The official API server listens on 8642 by default, on the same host as the chat gateway. */
    fun suggestedBase(gatewayBaseUrl: String): String =
        gatewayBaseUrl.trim().toHttpUrlOrNull()?.newBuilder()?.port(DEFAULT_API_SERVER_PORT)?.encodedPath("/")
            ?.build()?.toString()?.trimEnd('/').orEmpty()

    const val DEFAULT_API_SERVER_PORT = 8642

    fun endpoint(base: String, profile: String): String {
        val url = base.trim().toHttpUrl()
        require(transportAllowed(url) && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
            "Use an HTTPS API-server URL (HTTP only on a private network) without credentials, query or fragment."
        }
        require(profile.isNotBlank() && profile !in listOf(".", "..") && '/' !in profile && '\\' !in profile)
        return url.newBuilder().addPathSegments("p").addPathSegment(profile)
            .addPathSegments("api/platforms/hermes_android/events").build().toString()
    }
    fun call(account: PushAccount, type: String, data: JSONObject = JSONObject()): JSONObject {
        require(account.secret.length >= 32)
        // Stored URLs are rechecked before forwarding a secret; redirects are never followed.
        require(transportAllowed(account.endpoint.toHttpUrl()))
        val payload = JSONObject(data.toString()).put("type", type).put("protocol_version", 1)
        if (account.device.isNotEmpty()) payload.put("device_id", account.device).put("device_secret", account.deviceSecret)
        val req = Request.Builder().url(account.endpoint).header("Authorization", "Bearer ${account.secret}")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { response ->
            if (!response.isSuccessful) throw PushRpcException("HTTP ${response.code}")
            val body = response.body ?: throw PushRpcException("empty_response")
            val source = body.source()
            if (source.request(1024 * 1024 + 1L)) throw PushRpcException("response_too_large")
            val envelope = JSONObject(source.readUtf8())
            if (!envelope.optBoolean("ok")) throw PushRpcException(envelope.optJSONObject("error")?.optString("code") ?: "invalid_response")
            return envelope.getJSONObject("result")
        }
    }
}
