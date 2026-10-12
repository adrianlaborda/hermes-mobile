package com.nousresearch.hermesmobile.features

import androidx.compose.runtime.*
import com.nousresearch.hermesmobile.data.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

fun encoded(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
fun JSONObject.rows(key: String): List<JSONObject> = optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull(a::optJSONObject) }.orEmpty()
fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.let { a -> (0 until a.length()).map(a::optString) }.orEmpty()
fun json(vararg entries: Pair<String, Any?>) = JSONObject().apply { entries.forEach { (k,v) -> put(k, v ?: JSONObject.NULL) } }

/** One immutable owner per mounted surface. Disposal cancels reads and fences late results. */
class ParityController(val repo: HermesRepository, val profile: String, parentScope: CoroutineScope) {
    private val ownerJob = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + ownerJob)
    val data = mutableStateMapOf<String, JSONObject>()
    val unavailable = mutableStateMapOf<String, String>()
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var consent by mutableStateOf<Pair<String, suspend () -> JSONObject>?>(null)
    private var alive = true
    private var readGeneration = 0
    fun dispose() { alive = false; ownerJob.cancel(); readGeneration++ ; consent = null; data.clear() }
    suspend fun api(path: String, method: String = "GET", body: JSONObject? = null, timeout: Long = 60) = repo.rest.scopedApi(profile, path, method, body, timeout)
    suspend fun plugins(action: String, fields: JSONObject = JSONObject()): JSONObject = repo.gateway.requestObject(
        "plugins.manage", JSONObject(fields.toString()).put("action", action).put("profile", profile), 120_000
    )
    fun load(section: String) {
        if (section == "Files") return
        val generation = ++readGeneration
        busy = true
        scope.launch {
            suspend fun read(key: String, block: suspend () -> JSONObject) {
                try {
                    val result = block()
                    if (alive && generation == readGeneration) { data[key] = result; unavailable.remove(key) }
                } catch (e: CancellationException) { throw e }
                catch (e: Throwable) {
                    if (alive && generation == readGeneration) {
                        val optional = (e as? HermesHttpException)?.status in listOf(404,405,501) || (e as? GatewayRpcException)?.code == -32601
                        unavailable[key] = if (optional) "Not supported by this gateway version" else e.message ?: "Could not load"
                    }
                }
            }
            try {
                when (section) {
                    "Memory" -> { read("memory") { api("/api/memory") }; read("curator") { api("/api/curator") }; read("graph") { api("/api/learning/graph") } }
                    "Plugins" -> { read("plugins") { plugins("list") }; read("hub") { api("/api/dashboard/plugins/hub") }; read("catalog") { api("/api/dashboard/plugins/catalog") } }
                    "Models" -> { read("auxiliary") { api("/api/model/auxiliary") }; read("moa") { api("/api/model/moa") } }
                    "Settings" -> { read("config") { api("/api/config") }; read("schema") { api("/api/config/schema") }; read("mcp") { api("/api/mcp/servers") }; read("usage") { api("/api/analytics/usage?days=30") } }
                    "Voice" -> { read("voice") { api("/api/audio/voice-live/status") } }
                }
            } finally { if (alive && generation == readGeneration) busy = false }
        }
    }
    fun fetch(key: String, block: suspend () -> JSONObject) = action(null) { block().also { if (alive) data[key] = it } }
    fun action(refresh: String?, block: suspend () -> JSONObject) {
        if (busy || !alive) return
        busy = true; message = null
        scope.launch {
            var reloading = false
            try {
                val result = block()
                if (!alive) return@launch
                check(!result.has("ok") || result.optBoolean("ok") || result.optBoolean("consent_required") || result.optBoolean("confirm_required")) {
                    result.optString("error", "The gateway did not apply this change")
                }
                if (!result.optBoolean("consent_required") && !result.optBoolean("confirm_required")) {
                    message = result.optString("message").ifBlank { result.optString("detail").ifBlank { "Done" } }
                    if (refresh != null && refresh != "Files") { reloading = true; load(refresh) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Throwable) { if (alive) message = e.message ?: "Request failed" }
            finally { if (alive && !reloading) busy = false }
        }
    }
    fun confirm(title: String, block: suspend () -> JSONObject) { consent = title to block }
    fun accept(refresh: String?) { val pending = consent ?: return; consent = null; action(refresh, pending.second) }
    suspend fun updatePlugin(name: String, accepted: Boolean = false): JSONObject {
        val result = plugins("update", json("name" to name).apply { if (accepted) put("accept_capabilities", true) })
        if (result.optBoolean("consent_required") && alive) {
            consent = ("This update adds capabilities (commit ${result.optString("sha").take(12)}):\n" + result.strings("delta_lines").joinToString("\n")) to { updatePlugin(name, true) }
        }
        return result
    }
    suspend fun assignModel(body: JSONObject): JSONObject {
        val result = api("/api/model/set", "POST", body)
        if (result.optBoolean("confirm_required") && alive) {
            consent = result.optString("confirm_message", "Confirm this model selection") to {
                assignModel(JSONObject(body.toString()).put("confirm_expensive_model", true))
            }
        }
        return result
    }
}
