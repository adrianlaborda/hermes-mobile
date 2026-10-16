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
/** Placeholders the controller (which has no resources) stores, put into words where they are shown: [shown]. */
internal const val UNSUPPORTED = "\u0000unsupported"
internal const val LOAD_FAILED = "\u0000load-failed"
internal const val REQUEST_FAILED = "\u0000request-failed"

/** A controller message or unavailability note in the user's language. */
@androidx.compose.runtime.Composable
fun shown(text: String): String = when (text) {
    UNSUPPORTED -> androidx.compose.ui.res.stringResource(com.nousresearch.hermesmobile.R.string.i18n_unsupported)
    LOAD_FAILED -> androidx.compose.ui.res.stringResource(com.nousresearch.hermesmobile.R.string.i18n_load_failed)
    REQUEST_FAILED -> androidx.compose.ui.res.stringResource(com.nousresearch.hermesmobile.R.string.i18n_request_failed)
    else -> text
}

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
                        unavailable[key] = if (optional) UNSUPPORTED else e.message ?: LOAD_FAILED
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
                    // Only what the server has to say: a read (opening a folder) or a plain write needs no "Done".
                    // A success note clears itself; errors stay until dismissed.
                    message = result.optString("message").ifBlank { result.optString("detail") }.ifBlank { null }
                    message?.let { note -> scope.launch { delay(4_000); if (message == note) message = null } }
                    if (refresh != null && refresh != "Files") { reloading = true; load(refresh) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Throwable) { if (alive) message = e.message ?: REQUEST_FAILED }
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
