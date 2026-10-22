package com.nousresearch.hermesmobile.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nousresearch.hermesmobile.MainActivity
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.data.GatewayConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object PushDelivery {
    const val CHANNEL = "hermes_updates"
    fun configured(context: Context) = FirebaseRuntime.configured(context)
    fun permitted(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    private const val PREFS = "hermes_push_prefs"
    private const val ONLY_PHONE_TURNS = "only_phone_turns"
    private const val PHONE_TURNS = "phone_turns"
    // Long enough for a long agent turn; a claim left by a turn that never ended expires on its own.
    private const val PHONE_TURN_TTL_MS = 24 * 60 * 60 * 1000L

    /** The user's choice: notify only answers to prompts sent from this phone (default), or every finished turn. */
    fun onlyPhoneTurns(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ONLY_PHONE_TURNS, true)

    fun setOnlyPhoneTurns(context: Context, only: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ONLY_PHONE_TURNS, only).apply()

    /** A prompt sent from this phone started a turn in [sessionId]: its answer may notify. */
    fun claimTurn(context: Context, sessionId: String) = editTurns(context) { claims, _ ->
        if (sessionId.isNotBlank()) claims[sessionId] = claims[sessionId].orEmpty() + System.currentTimeMillis()
    }

    /** A claimed turn of [sessionId] will send no notice (stopped, never sent, or a steer): its claim is spent. */
    fun releaseTurn(context: Context, sessionId: String) = editTurns(context) { claims, _ ->
        claims[sessionId]?.let { claims[sessionId] = it.drop(1) }
    }

    /**
     * Drops every claim of [sessionId]: its chat was opened or refreshed while no turn runs there, so a claim left by a
     * turn that ended without a notice (interrupted server-side) must not make a later Desktop turn ring. Cheap when
     * there is nothing to remove: the stored JSON is only rewritten when it names the chat.
     */
    fun clearTurns(context: Context, sessionId: String) {
        if (sessionId.isBlank()) return
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PHONE_TURNS, null) ?: return
        if (!stored.contains(sessionId)) return
        editTurns(context) { claims, _ -> claims.remove(sessionId) }
    }

    /**
     * Whether the turn notice [eventId] rings: always, unless the user chose phone-only and no prompt sent from
     * here is waiting for an answer in that chat. Decided once per event, since the inbox can hand it over again.
     */
    private fun turnNotifies(context: Context, eventId: String, sessionId: String): Boolean {
        var notifies = true
        editTurns(context) { claims, decided ->
            notifies = decided[eventId] ?: run {
                val claim = claims[sessionId].orEmpty()
                if (claim.isNotEmpty()) claims[sessionId] = claim.drop(1)
                // No chat named: nothing to tell where it started, so it rings.
                (claim.isNotEmpty() || sessionId.isBlank() || !onlyPhoneTurns(context)).also { decided[eventId] = it }
            }
        }
        return notifies
    }

    private fun editTurns(context: Context, change: (MutableMap<String, List<Long>>, MutableMap<String, Boolean>) -> Unit) =
        synchronized(this) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val stored = runCatching { JSONObject(prefs.getString(PHONE_TURNS, null) ?: "{}") }.getOrDefault(JSONObject())
            val fresh = System.currentTimeMillis() - PHONE_TURN_TTL_MS
            val claims = mutableMapOf<String, List<Long>>()
            stored.optJSONObject("claims")?.let { json ->
                json.keys().forEach { key ->
                    val times = json.optJSONArray(key) ?: return@forEach
                    claims[key] = (0 until times.length()).map { times.optLong(it) }.filter { it > fresh }
                }
            }
            val decided = linkedMapOf<String, Boolean>()
            stored.optJSONObject("decided")?.let { json -> json.keys().forEach { decided[it] = json.optBoolean(it) } }
            change(claims, decided)
            val out = JSONObject()
                .put("claims", JSONObject().also { json -> claims.filterValues { it.isNotEmpty() }.forEach { (k, v) -> json.put(k, org.json.JSONArray(v)) } })
                .put("decided", JSONObject().also { json -> decided.entries.toList().takeLast(200).forEach { (k, v) -> json.put(k, v) } })
            prefs.edit().putString(PHONE_TURNS, out.toString()).apply()
        }

    /** Title and text for an event fetched from the inbox, localizing the plugin's generic notices. */
    fun presentation(context: Context, event: JSONObject): Pair<String, String> {
        val type = event.optString("event_type")
        val title = event.optString("title").ifBlank { "Hermes" }
        val body = event.optString("body")
        return when (type) {
            // Plugin 0.5.3+: title = the chat, body = "…to continue: <what the action does>".
            "approval_required" -> {
                val detail = body.substringAfter("continue: ", "").trim()
                val heading = context.getString(R.string.a25_approval_title)
                (if (title == "Hermes") heading else "$heading · $title") to
                    (context.getString(R.string.a25_approval_body) + if (detail.isNotBlank()) "\n$detail" else "")
            }
            "test" -> "Hermes" to context.getString(R.string.a25_test_body)
            "reminder" -> (if (title == "Hermes reminder") context.getString(R.string.a25_reminder_title) else title) to body
            "turn_failed" -> title to body.replace("The task failed or needs your attention.", context.getString(R.string.a25_turn_failed)).trim()
            "turn_completed" -> title to (if (body == "Your response is ready.") context.getString(R.string.a25_turn_ready) else body)
            else -> title to body
        }
    }

    /**
     * Posts (or updates, same tag) the notice for [eventId]. Without [event] — the push only carries the ID and the
     * content could not be fetched yet — it is generic. With it, the real title and text appear in the shade, and
     * on the lock screen too unless the user turned that off. Returns false when nothing was posted.
     */
    fun show(context: Context, eventId: String, event: JSONObject? = null): Boolean {
        if (!permitted(context)) return false
        // The user is looking at that very chat: the answer is already on screen.
        val session = event?.optString("session_id").orEmpty()
        if (session.isNotBlank() && AppPresence.isShowing(session)) return false
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.push_channel), NotificationManager.IMPORTANCE_DEFAULT))
        if (nm.getNotificationChannel(CHANNEL).importance == NotificationManager.IMPORTANCE_NONE) return false
        val intent = Intent(context, MainActivity::class.java).setAction("hermes.notification")
            .putExtra("event_id", eventId).setData(android.net.Uri.parse("hermes-notification:$eventId"))
        val tap = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val generic = context.getString(R.string.push_generic)
        // Nous blue, Hermes Desktop's accent (HermesTokens.NousBlue).
        val accent = 0xFF0053FD.toInt()
        val publicVersion = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_hermes).setColor(accent)
            .setContentTitle("Hermes").setContentText(generic).build()
        val builder = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_hermes).setColor(accent)
            .setContentIntent(tap).setAutoCancel(true).setOnlyAlertOnce(true)
        if (event == null) {
            builder.setContentTitle("Hermes").setContentText(generic).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        } else {
            val (title, text) = presentation(context, event)
            builder.setContentTitle(title).setContentText(text.ifBlank { generic })
                .setStyle(NotificationCompat.BigTextStyle().bigText(text.ifBlank { generic }))
                .setWhen((event.optDouble("created_at", 0.0) * 1000).toLong().takeIf { it > 0 } ?: System.currentTimeMillis())
                .setShowWhen(true)
                // Android's own lock-screen setting decides: full content, or this generic public version.
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
        }
        nm.notify(eventId, 0, builder.build())
        return true
    }
    /** Removes a notice from the shade and the inbox: the server withdrew it. */
    fun withdraw(context: Context, eventId: String) {
        context.getSystemService(NotificationManager::class.java).cancel(eventId, 0)
        runCatching { PushStore(context).forget(eventId) }
    }

    /**
     * What to do with an event fetched from the inbox. Returns true when the server can mark it delivered.
     * - `approval_resolved`: the approval was answered (Desktop, Telegram, the app) or timed out → withdraw its notice.
     * - already acknowledged or expired: the server withdrew it → remove any generic notice still showing.
     * - anything else: store it and show it.
     */
    fun handle(context: Context, account: PushAccount, event: JSONObject): Boolean {
        val id = event.getString("event_id")
        if (event.optString("event_type") == "approval_resolved") {
            event.optString("source_id").takeIf { it.isNotBlank() }?.let { withdraw(context, it) }
            return true
        }
        if (event.optString("state") in setOf("expired", "acked") || event.optDouble("expires_at") * 1000 <= System.currentTimeMillis()) {
            withdraw(context, id)
            return false
        }
        PushStore(context).received(account, event)
        // A turn started elsewhere (Desktop, the CLI…) stays in the inbox without ringing: whoever started it is there.
        if (event.optString("event_type") in setOf("turn_completed", "turn_failed") &&
            !turnNotifies(context, id, event.optString("session_id"))) {
            context.getSystemService(NotificationManager::class.java).cancel(id, 0)
            return true
        }
        // Replaces the generic notice (same tag) with the real content.
        val shown = show(context, id, event)
        return shown || event.optString("session_id").let { it.isNotBlank() && AppPresence.isShowing(it) }
    }

    fun schedule(context: Context, eventId: String? = null) {
        if (!configured(context) || runCatching { PushStore(context).accounts().isEmpty() }.getOrDefault(true)) return
        val request = OneTimeWorkRequestBuilder<PushSyncWorker>()
            .setInputData(workDataOf("event_id" to eventId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("hermes-push-${eventId ?: "sync"}", ExistingWorkPolicy.KEEP, request)
    }
}

class HermesMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { PushDelivery.schedule(this) }
    override fun onMessageReceived(message: RemoteMessage) {
        val id = message.data["event_id"] ?: return
        if (message.data["protocol_version"] != "1" || !id.matches(Regex("[A-Za-z0-9-]{1,64}"))) return
        val device = message.data["device_id"] ?: return
        val validConnections = GatewayConfigStore(this).snapshot().connections.map { it.id }.toSet()
        val account = runCatching { PushStore(this).accounts().firstOrNull { it.device == device && it.connection in validConnections } }.getOrNull() ?: return
        // FCM only carried the ID: fetch the content from the user's server now (briefly; it may need a VPN).
        val event = runCatching { PushClient.call(account, "event.get", JSONObject().put("event_id", id), fast = true) }.getOrNull()
        if (event != null) {
            if (PushDelivery.handle(this, account, event)) runCatching { PushClient.call(account, "event.ack", JSONObject().put("event_id", id), fast = true) }
            return
        }
        // Persist only the known owner before notifying. An offline tap must open
        // that profile's inbox, even when private event details are still deferred.
        PushStore(this).received(account, JSONObject().put("event_id", id).put("title", "Hermes")
            .put("body", getString(R.string.push_generic)).put("state", "pending"), overwrite = false)
        PushDelivery.show(this, id)
        PushDelivery.schedule(this, id)
    }
}

class PushSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val store = PushStore(context)
        var retry = false
        val configs = GatewayConfigStore(context).snapshot().connections.map { it.id }.toSet()
        val token = runCatching {
            check(FirebaseRuntime.ensureInitialized(context))
            FirebaseRuntime.fcmToken(15)
        }.getOrNull()
        for (account in store.accounts().filter { it.connection in configs }) {
            try {
                if (token != null) PushClient.call(account, "device.token.update", JSONObject().put("push_token", token))
                val id = inputData.getString("event_id")
                val events = if (id != null) listOf(PushClient.call(account, "event.get", JSONObject().put("event_id", id)))
                    else PushClient.call(account, "events.pending", JSONObject().put("limit", 10)).getJSONArray("events").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
                if (id == null && events.size >= 10 && PushDelivery.permitted(context)) retry = true
                for (event in events) {
                    if (PushDelivery.handle(context, account, event)) {
                        PushClient.call(account, "event.ack", JSONObject().put("event_id", event.getString("event_id")))
                    }
                }
            } catch (e: PushRpcException) {
                if (e.code != "event_not_found" && !e.code.startsWith("HTTP 4") && e.code !in setOf("device_revoked", "device_auth_failed", "device_not_found")) retry = true
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; retry = true }
        }
        if (retry && runAttemptCount < 5) Result.retry() else Result.success()
    }
}

/** Which chat is on screen, so a turn notice for it is not posted while the user reads that very answer. */
object AppPresence {
    @Volatile var foreground: Boolean = false
    @Volatile var openSessionId: String? = null
    fun isShowing(sessionId: String): Boolean = foreground && openSessionId == sessionId
}
