package com.nousresearch.hermesmobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit

class GatewayAuthException(message: String, val status: Int? = null) : IllegalStateException(message)

data class NativePkceRequest(
    val authorizeUrl: String,
    val redirectUri: String,
    val verifier: String,
    val state: String,
    internal val callback: kotlinx.coroutines.Deferred<String>
)

/** Public/bootstrap auth calls shared by the connection wizard and the authenticated REST client. */
class HermesAuthClient(
    private val baseUrl: String,
    private val extraHeaders: Map<String, String> = emptyMap(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val root = GatewayUrlPolicy.canonical(baseUrl)

    suspend fun status(): GatewayStatus = GatewayStatus.fromJson(publicJson("/api/status"))

    suspend fun providers(): List<AuthProvider> = runCatching {
        val o = publicJson("/api/auth/providers")
        val a = o.optJSONArray("providers") ?: JSONArray()
        a.objects().map(AuthProvider::fromJson).filter { it.name.isNotBlank() }
    }.getOrDefault(emptyList())

    suspend fun discover(draft: GatewayConfig): GatewayDiscovery {
        val status = status()
        val discovered = providers().ifEmpty {
            status.authProviders.map { name -> AuthProvider(name, supportsPassword = name == "basic") }
        }
        return GatewayDiscovery(draft = draft, status = status, providers = discovered)
    }

    /**
     * Hermes' password-provider route creates an authenticated cookie session. We keep only the
     * resulting cookie values; the supplied password never leaves this call and is never persisted.
     */
    suspend fun passwordLogin(provider: String, username: String, password: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("provider", provider)
            .put("username", username)
            .put("password", password)
            .toString().toRequestBody(jsonType)
        val request = baseRequest("/auth/password-login")
            .post(body)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (response.code !in 200..399) {
                throw GatewayAuthException(errorMessage(text, "Invalid username or password"), response.code)
            }
            val cookies = cookiePairs(response.headers.values("Set-Cookie"))
            if (cookies.isBlank()) {
                // Current basic provider sets an authenticated cookie. Failing closed here avoids
                // presenting a successful sign-in that cannot mint a WebSocket ticket.
                throw GatewayAuthException("Hermes accepted the login but did not return an authenticated session cookie")
            }
            validatePasswordSession(cookies, provider)
            cookies
        }
    }


    /**
     * Prove that the cookie minted by /auth/password-login is immediately usable on the protected
     * plane. This catches cookie/path/proxy regressions before the wizard reports a successful login.
     * Password credentials are never reused here; only the freshly issued session cookie is sent.
     */
    private fun validatePasswordSession(cookieHeader: String, expectedProvider: String) {
        val request = baseRequest("/api/auth/me")
            .get()
            .header("Accept", "application/json")
            .header("Cookie", cookieHeader)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                throw GatewayAuthException(
                    errorMessage(text, "Hermes created a session but rejected it during /api/auth/me"),
                    response.code
                )
            }
            val actualProvider = runCatching { JSONObject(text).optString("provider") }.getOrDefault("")
            if (actualProvider.isNotBlank() && expectedProvider.isNotBlank() && actualProvider != expectedProvider) {
                throw GatewayAuthException(
                    "Hermes authenticated with provider '$actualProvider' instead of '$expectedProvider'"
                )
            }
        }
    }

    suspend fun exchangeNativeCode(code: String, verifier: String, redirectUri: String): NativeTokenSet =
        postToken(
            "/auth/native/token",
            JSONObject()
                .put("code", code)
                .put("code_verifier", verifier)
                .put("redirect_uri", redirectUri)
        )

    suspend fun refreshNative(refreshToken: String): NativeTokenSet =
        postToken("/auth/native/refresh", JSONObject().put("refresh_token", refreshToken))

    private suspend fun postToken(path: String, body: JSONObject): NativeTokenSet = withContext(Dispatchers.IO) {
        val request = baseRequest(path)
            .post(body.toString().toRequestBody(jsonType))
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) throw GatewayAuthException(errorMessage(text, "Hermes sign-in failed"), response.code)
            val tokens = NativeTokenSet.fromJson(JSONObject(text))
            if (!tokens.usable) throw GatewayAuthException("Gateway token response is missing access_token")
            tokens
        }
    }

    private suspend fun publicJson(path: String): JSONObject = withContext(Dispatchers.IO) {
        val request = baseRequest(path).get().header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) throw GatewayAuthException(errorMessage(text, "HTTP ${response.code}"), response.code)
            JSONObject(text)
        }
    }

    private fun baseRequest(path: String): Request.Builder = Request.Builder().url(root + path).also { builder ->
        extraHeaders.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank() && !isManagedHeader(k)) builder.header(k, v)
        }
    }

    private fun cookiePairs(setCookies: List<String>): String = setCookies.mapNotNull { raw ->
        raw.substringBefore(';').trim().takeIf { it.contains('=') && !it.startsWith("=", false) }
    }.distinctBy { it.substringBefore('=') }.joinToString("; ")

    private fun errorMessage(text: String, fallback: String): String = runCatching {
        val o = JSONObject(text)
        o.optString("detail").ifBlank { o.optString("message") }.ifBlank { fallback }
    }.getOrDefault(text.trim().takeIf { it.isNotBlank() } ?: fallback)

    private fun isManagedHeader(name: String): Boolean = name.lowercase(Locale.ROOT) in setOf(
        "authorization", "cookie", "host", "origin", "referer", "x-hermes-session-token", "connection", "upgrade"
    )
}

/**
 * Android implementation of Hermes Desktop's RFC 8252 native sign-in: an ephemeral loopback
 * listener receives the one-time code, while the system browser handles the actual provider UI.
 */
object NativePkceCoordinator {
    private val random = SecureRandom()

    suspend fun begin(
        scope: kotlinx.coroutines.CoroutineScope,
        baseUrl: String,
        provider: String? = null
    ): NativePkceRequest {
        val verifier = b64url(ByteArray(32).also(random::nextBytes))
        val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val state = b64url(ByteArray(24).also(random::nextBytes))
        val server = withContext(Dispatchers.IO) { ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")) }
        val redirectUri = "http://127.0.0.1:${server.localPort}/callback"
        val callback = scope.async(Dispatchers.IO) {
            withTimeout(180_000) {
                server.use { socket ->
                    socket.soTimeout = 1000
                    var acceptedCode: String? = null
                    while (acceptedCode == null) {
                        ensureActive()
                        val client = try { socket.accept() } catch (_: java.net.SocketTimeoutException) { continue }
                        client.use { connection ->
                            connection.soTimeout = 3000
                            val line = try { readLoopbackRequestLine(connection.getInputStream()) }
                                catch (_: java.io.IOException) { return@use }
                                catch (_: IllegalArgumentException) { return@use }
                            val parts = line.split(' ')
                            val target = parts.getOrNull(1).orEmpty()
                            val params = runCatching { parseQuery(target.substringAfter('?', "")) }.getOrDefault(emptyMap())
                            val valid = parts.firstOrNull() == "GET" && target.substringBefore('?') == "/callback" && params["state"] == state
                            val code = params["code"].orEmpty()
                            val error = params["error"].orEmpty()
                            val ok = valid && error.isBlank() && code.isNotBlank()
                            val message = if (ok) "Sign-in completed. Return to Hermes." else "Invalid or unsuccessful sign-in callback."
                            connection.getOutputStream().write(("HTTP/1.1 ${if(ok) "200 OK" else "400 Bad Request"}\r\nContent-Type: text/plain\r\nContent-Length: ${message.length}\r\nConnection: close\r\n\r\n$message").toByteArray(Charsets.US_ASCII))
                            if (valid && error.isNotBlank()) throw GatewayAuthException("Sign-in was declined by the authorization provider")
                            if (ok) acceptedCode = code
                        }
                    }
                    requireNotNull(acceptedCode)
                }
            }
        }

        callback.invokeOnCompletion { runCatching { server.close() } }
        val authorizeUrl = buildAuthorizeUrl(baseUrl, challenge, redirectUri, state, provider)
        return NativePkceRequest(authorizeUrl, redirectUri, verifier, state, callback)
    }

    private fun buildAuthorizeUrl(baseUrl: String, challenge: String, redirectUri: String, state: String, provider: String?): String {
        val root = baseUrl.trim().trimEnd('/')
        val q = linkedMapOf(
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            "redirect_uri" to redirectUri,
            "state" to state
        )
        provider?.takeIf { it.isNotBlank() }?.let { q["provider"] = it }
        return "$root/auth/native/authorize?" + q.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, Charsets.UTF_8.name()) + "=" + URLEncoder.encode(v, Charsets.UTF_8.name()).replace("+", "%20")
        }
    }

    private fun parseQuery(query: String): Map<String, String> = query.split('&').mapNotNull { pair ->
        if (pair.isBlank()) return@mapNotNull null
        val key = pair.substringBefore('=')
        val value = pair.substringAfter('=', "")
        URLDecoder.decode(key, Charsets.UTF_8.name()) to URLDecoder.decode(value, Charsets.UTF_8.name())
    }.toMap()

    private fun b64url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    private fun html(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
