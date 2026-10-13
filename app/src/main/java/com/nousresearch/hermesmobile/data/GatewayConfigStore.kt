package com.nousresearch.hermesmobile.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

sealed interface GatewayConfigLoadResult {
    data object Empty : GatewayConfigLoadResult
    data class Loaded(val config: GatewayConfig) : GatewayConfigLoadResult
    data class Invalid(val message: String) : GatewayConfigLoadResult
}

data class GatewayStoreSnapshot(
    val connections: List<GatewayConfig> = emptyList(),
    val activeId: String? = null
) {
    val active: GatewayConfig? get() = connections.firstOrNull { it.id == activeId } ?: connections.firstOrNull()
}

sealed interface GatewayStoreLoadResult {
    data object Empty : GatewayStoreLoadResult
    data class Loaded(val snapshot: GatewayStoreSnapshot) : GatewayStoreLoadResult
    data class Invalid(val message: String) : GatewayStoreLoadResult
}

/**
 * Encrypted multi-connection store.
 *
 * The whole payload is AES-GCM encrypted with an Android Keystore key. Passwords are never
 * written here: Basic auth persists only the resulting cookie/native token material. The old
 * alpha1-alpha5 single `gateway` record is migrated once into the v2 connection registry.
 */
class GatewayConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("hermes_mobile", Context.MODE_PRIVATE)
    private val alias = "hermes_mobile_gateway_key"

    fun readPrivate(name: String): String? = synchronized(lock) {
        prefs.getString("private_$name", null)?.let { decrypt(it) }
    }
    fun writePrivate(name: String, value: String) = synchronized(lock) {
        check(prefs.edit().putString("private_$name", encrypt(value)).commit()) { "Could not save secure settings" }
    }
    fun loadSnapshotResult(): GatewayStoreLoadResult = synchronized(lock) {
        val encrypted = prefs.getString(KEY_CONNECTIONS, null)
        if (encrypted != null) return@synchronized decodeSnapshot(encrypted)

        // One-time migration from the alpha1-alpha5 single gateway record.
        val legacyEncrypted = prefs.getString(KEY_LEGACY_GATEWAY, null) ?: return@synchronized GatewayStoreLoadResult.Empty
        return@synchronized try {
            val o = JSONObject(decrypt(legacyEncrypted))
            val migrated = GatewayConfig(
                id = UUID.randomUUID().toString(),
                label = o.optString("label", "Hermes"),
                baseUrl = o.optString("baseUrl"),
                authMode = GatewayAuthMode.SESSION_TOKEN,
                token = o.optString("token"),
                extraHeaders = jsonMap(o.optJSONObject("headers"))
            )
            val snapshot = GatewayStoreSnapshot(listOf(migrated), migrated.id)
            writeSnapshot(snapshot)
            prefs.edit().remove(KEY_LEGACY_GATEWAY).apply()
            GatewayStoreLoadResult.Loaded(snapshot)
        } catch (_: AEADBadTagException) {
            GatewayStoreLoadResult.Invalid("La configuración cifrada de Hermes ya no puede descifrarse con la clave de este dispositivo.")
        } catch (t: Throwable) {
            GatewayStoreLoadResult.Invalid(t.message ?: "La configuración cifrada de conexiones no es válida.")
        }
    }

    fun snapshot(): GatewayStoreSnapshot = when (val loaded = loadSnapshotResult()) {
        is GatewayStoreLoadResult.Loaded -> loaded.snapshot
        else -> GatewayStoreSnapshot()
    }

    fun loadResult(): GatewayConfigLoadResult = when (val loaded = loadSnapshotResult()) {
        GatewayStoreLoadResult.Empty -> GatewayConfigLoadResult.Empty
        is GatewayStoreLoadResult.Invalid -> GatewayConfigLoadResult.Invalid(loaded.message)
        is GatewayStoreLoadResult.Loaded -> loaded.snapshot.active?.let(GatewayConfigLoadResult::Loaded)
            ?: GatewayConfigLoadResult.Empty
    }

    fun load(): GatewayConfig? = snapshot().active

    fun save(config: GatewayConfig, makeActive: Boolean = true) = synchronized(lock) {
        val normalized = config.copy(id = config.id.ifBlank { UUID.randomUUID().toString() })
        val current = snapshot()
        val next = (current.connections.filterNot { it.id == normalized.id } + normalized)
            .sortedWith(compareByDescending<GatewayConfig> { it.id == (if (makeActive) normalized.id else current.activeId) }.thenBy { it.label.lowercase() })
        writeSnapshot(
            GatewayStoreSnapshot(
                connections = next,
                activeId = if (makeActive) normalized.id else current.activeId ?: normalized.id
            )
        )
    }

    fun setActive(id: String) = synchronized(lock) {
        val current = snapshot()
        if (current.connections.none { it.id == id }) return@synchronized
        writeSnapshot(current.copy(activeId = id))
    }

    fun remove(id: String) = synchronized(lock) {
        val current = snapshot()
        val remaining = current.connections.filterNot { it.id == id }
        val active = if (current.activeId == id) remaining.firstOrNull()?.id else current.activeId
        writeSnapshot(GatewayStoreSnapshot(remaining, active))
    }

    fun clear() = synchronized(lock) {
        prefs.edit().remove(KEY_CONNECTIONS).remove(KEY_LEGACY_GATEWAY).apply()
    }

    private fun decodeSnapshot(encrypted: String): GatewayStoreLoadResult = try {
        val root = JSONObject(decrypt(encrypted))
        val rows = root.optJSONArray("connections") ?: JSONArray()
        val connections = buildList {
            for (i in 0 until rows.length()) rows.optJSONObject(i)?.let { add(configFromJson(it)) }
        }.filter { it.baseUrl.isNotBlank() }
        GatewayStoreLoadResult.Loaded(
            GatewayStoreSnapshot(
                connections = connections,
                activeId = root.optString("active_id").takeIf { it.isNotBlank() }
            )
        )
    } catch (_: AEADBadTagException) {
        GatewayStoreLoadResult.Invalid("La configuración cifrada de Hermes ya no puede descifrarse con la clave de este dispositivo.")
    } catch (t: Throwable) {
        GatewayStoreLoadResult.Invalid(t.message ?: "La configuración cifrada de conexiones no es válida.")
    }

    private fun writeSnapshot(snapshot: GatewayStoreSnapshot) {
        val root = JSONObject()
            .put("version", 2)
            .put("active_id", snapshot.activeId ?: JSONObject.NULL)
            .put("connections", JSONArray(snapshot.connections.map(::configToJson)))
        prefs.edit().putString(KEY_CONNECTIONS, encrypt(root.toString())).apply()
    }

    private fun configToJson(config: GatewayConfig): JSONObject = JSONObject()
        .put("id", config.id)
        .put("label", config.label)
        .put("base_url", config.baseUrl)
        .put("auth_mode", config.authMode.name)
        .put("token", config.token)
        .put("cookie", config.cookieHeader)
        .put("auth_provider", config.authProvider)
        .put("selected_profile", config.selectedProfile)
        .put("headers", JSONObject(config.extraHeaders))
        .put("native_tokens", JSONObject()
            .put("access_token", config.nativeTokens.accessToken)
            .put("refresh_token", config.nativeTokens.refreshToken)
            .put("expires_at", config.nativeTokens.expiresAt)
            .put("provider", config.nativeTokens.provider)
            .put("user_id", config.nativeTokens.userId)
        )

    private fun configFromJson(o: JSONObject): GatewayConfig = GatewayConfig(
        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
        label = o.optString("label", "Hermes"),
        baseUrl = o.optString("base_url").ifBlank { o.optString("baseUrl") },
        authMode = runCatching { GatewayAuthMode.valueOf(o.optString("auth_mode", "SESSION_TOKEN")) }
            .getOrDefault(GatewayAuthMode.SESSION_TOKEN),
        token = o.optString("token"),
        cookieHeader = o.optString("cookie"),
        nativeTokens = o.optJSONObject("native_tokens")?.let(NativeTokenSet::fromJson) ?: NativeTokenSet(),
        authProvider = o.optString("auth_provider"),
        selectedProfile = o.optString("selected_profile", "default"),
        extraHeaders = jsonMap(o.optJSONObject("headers"))
    )

    private fun jsonMap(o: JSONObject?): Map<String, String> = buildMap {
        val obj = o ?: return@buildMap
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            obj.optString(key).takeIf { it.isNotBlank() }?.let { put(key, it) }
        }
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(clear: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(clear.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(cipher.iv.size + encrypted.size)
        cipher.iv.copyInto(payload, 0)
        encrypted.copyInto(payload, cipher.iv.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        require(raw.size > 12) { "Invalid encrypted Hermes config" }
        val iv = raw.copyOfRange(0, 12)
        val encrypted = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    companion object {
        private val lock = Any()
        private const val KEY_CONNECTIONS = "gateway_connections_v2"
        private const val KEY_LEGACY_GATEWAY = "gateway"
    }
}
