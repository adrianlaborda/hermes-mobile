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
    fun show(context: Context, eventId: String, content: String? = null): Boolean {
        if (!permitted(context)) return false
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.push_channel), NotificationManager.IMPORTANCE_DEFAULT))
        if (nm.getNotificationChannel(CHANNEL).importance == NotificationManager.IMPORTANCE_NONE) return false
        val intent = Intent(context, MainActivity::class.java).setAction("hermes.notification")
            .putExtra("event_id", eventId).setData(android.net.Uri.parse("hermes-notification:$eventId"))
        val tap = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_hermes)
            .setContentTitle("Hermes").setContentText(content ?: context.getString(R.string.push_generic))
            .setContentIntent(tap).setAutoCancel(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        nm.notify(eventId, 0, notification)
        return true
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
        // Persist only the known owner before notifying. An offline tap must open
        // that profile's inbox, even when private event details are still deferred.
        PushStore(this).received(account, JSONObject().put("event_id", id).put("title", "Hermes")
            .put("body", getString(R.string.push_generic)).put("state", "pending"), overwrite = false)
        // Display immediately; fetching private details may require the user's VPN.
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
            com.google.android.gms.tasks.Tasks.await(FirebaseMessaging.getInstance().token, 15, TimeUnit.SECONDS)
        }.getOrNull()
        for (account in store.accounts().filter { it.connection in configs }) {
            try {
                if (token != null) PushClient.call(account, "device.token.update", JSONObject().put("push_token", token))
                val id = inputData.getString("event_id")
                val events = if (id != null) listOf(PushClient.call(account, "event.get", JSONObject().put("event_id", id)))
                    else PushClient.call(account, "events.pending", JSONObject().put("limit", 10)).getJSONArray("events").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
                if (id == null && events.size >= 10 && PushDelivery.permitted(context)) retry = true
                for (event in events) {
                    if (event.optString("state") in setOf("expired", "acked") || event.optDouble("expires_at") * 1000 <= System.currentTimeMillis()) continue
                    store.received(account, event)
                    // Privacy default: no transcript content on the lock screen.
                    if (PushDelivery.show(context, event.getString("event_id"))) {
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
