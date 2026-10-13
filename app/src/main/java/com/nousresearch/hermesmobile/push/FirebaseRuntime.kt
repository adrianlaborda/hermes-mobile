package com.nousresearch.hermesmobile.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.data.GatewayConfigStore
import org.json.JSONObject

/** The public Android identifiers of one Firebase project (the values `google-services.json` carries for this app). */
data class FirebaseConfig(val appId: String, val apiKey: String, val projectId: String, val senderId: String) {
    fun json(): String = JSONObject().put("appId", appId).put("apiKey", apiKey).put("projectId", projectId)
        .put("senderId", senderId).toString()

    companion object {
        fun from(json: String): FirebaseConfig? = runCatching {
            val o = JSONObject(json)
            FirebaseConfig(o.getString("appId"), o.getString("apiKey"), o.getString("projectId"), o.getString("senderId"))
        }.getOrNull()
    }
}

class FirebaseConfigException(message: String) : IllegalArgumentException(message)

/**
 * Bring-your-own Firebase. A prebuilt APK carries no Firebase project, so each user imports the
 * `google-services.json` of their own project at runtime. A build with `-Pfirebase.*` identifiers
 * (self-built APKs) keeps working and takes precedence.
 */
object FirebaseRuntime {
    private const val STORE_KEY = "firebase_config"

    /** Picks the client entry registered for [packageName], so a multi-app project file works too. */
    fun parseGoogleServices(json: String, packageName: String): FirebaseConfig {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: throw FirebaseConfigException("not_json")
        if (root.optString("type") == "service_account") throw FirebaseConfigException("service_account")
        val project = root.optJSONObject("project_info") ?: throw FirebaseConfigException("not_google_services")
        val clients = root.optJSONArray("client") ?: throw FirebaseConfigException("not_google_services")
        val registered = (0 until clients.length()).mapNotNull { clients.optJSONObject(it) }
        val client = registered.firstOrNull {
            it.optJSONObject("client_info")?.optJSONObject("android_client_info")?.optString("package_name") == packageName
        } ?: throw FirebaseConfigException("package_missing")
        val appId = client.optJSONObject("client_info")?.optString("mobilesdk_app_id").orEmpty()
        val apiKey = client.optJSONArray("api_key")?.optJSONObject(0)?.optString("current_key").orEmpty()
        val projectId = project.optString("project_id")
        val senderId = project.optString("project_number")
        if (listOf(appId, apiKey, projectId, senderId).any { it.isBlank() }) throw FirebaseConfigException("incomplete")
        return FirebaseConfig(appId, apiKey, projectId, senderId)
    }

    fun builtIn(context: Context): FirebaseConfig? {
        val values = listOf(R.string.google_app_id, R.string.google_api_key, R.string.project_id, R.string.gcm_defaultSenderId)
            .map { context.getString(it) }
        return if (values.all { it.isNotBlank() }) FirebaseConfig(values[0], values[1], values[2], values[3]) else null
    }

    fun imported(context: Context): FirebaseConfig? =
        GatewayConfigStore(context).readPrivate(STORE_KEY)?.let(FirebaseConfig::from)

    fun config(context: Context): FirebaseConfig? = builtIn(context) ?: imported(context)

    fun configured(context: Context): Boolean = config(context) != null

    /** Stores a user's project and (re)initializes Firebase with it. */
    fun import(context: Context, json: String): FirebaseConfig {
        val parsed = parseGoogleServices(json, context.packageName)
        GatewayConfigStore(context).writePrivate(STORE_KEY, parsed.json())
        synchronized(this) {
            FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }?.let { current ->
                if (current.options.applicationId != parsed.appId && builtIn(context) == null) current.delete()
            }
        }
        ensureInitialized(context)
        return parsed
    }

    /**
     * The FCM token, recovering from a remembered failure: after a rejected registration (for example a
     * misconfigured API key that the user has since fixed) the SDK keeps the errored installation and
     * "won't retry". Deleting the installation once forces a clean registration. Blocking; call off the main thread.
     */
    fun fcmToken(timeoutSeconds: Long = 30): String {
        val messaging = com.google.firebase.messaging.FirebaseMessaging.getInstance()
        return try {
            com.google.android.gms.tasks.Tasks.await(messaging.token, timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
        } catch (first: Exception) {
            runCatching {
                com.google.android.gms.tasks.Tasks.await(
                    com.google.firebase.installations.FirebaseInstallations.getInstance().delete(),
                    timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
            }
            com.google.android.gms.tasks.Tasks.await(messaging.token, timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    /** Idempotent; safe from Application.onCreate, workers and the messaging service. */
    fun ensureInitialized(context: Context): Boolean = synchronized(this) {
        if (FirebaseApp.getApps(context).any { it.name == FirebaseApp.DEFAULT_APP_NAME }) return true
        val config = config(context) ?: return false
        FirebaseApp.initializeApp(
            context.applicationContext,
            FirebaseOptions.Builder().setApplicationId(config.appId).setApiKey(config.apiKey)
                .setProjectId(config.projectId).setGcmSenderId(config.senderId).build()
        )
        true
    }
}
