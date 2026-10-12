package com.nousresearch.hermesmobile.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted per-conversation drafts and client-side send queue. */
class ChatComposerStore(context: Context) {
    private val prefs = context.getSharedPreferences("hermes_mobile_chat", Context.MODE_PRIVATE)
    private val alias = "hermes_mobile_chat_key"

    data class State(
        val draft: String = "",
        val attachments: List<ComposerAttachment> = emptyList(),
        val queue: List<QueuedPrompt> = emptyList()
    )

    fun load(key: String): State {
        val encoded = prefs.getString(key, null) ?: return State()
        return runCatching {
            val root = JSONObject(decrypt(encoded))
            State(
                draft = root.optString("draft"),
                attachments = attachmentsFromJson(root.optJSONArray("attachments")),
                queue = buildList {
                    val arr = root.optJSONArray("queue") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { row ->
                            add(
                                QueuedPrompt(
                                    id = row.optString("id"),
                                    text = row.optString("text"),
                                    attachments = attachmentsFromJson(row.optJSONArray("attachments")),
                                    createdAt = row.optLong("created_at", 0L)
                                )
                            )
                        }
                    }
                }
            )
        }.getOrDefault(State())
    }

    fun save(key: String, state: State) {
        if (state.draft.isBlank() && state.attachments.isEmpty() && state.queue.isEmpty()) {
            prefs.edit().remove(key).apply()
            return
        }
        val root = JSONObject()
            .put("draft", state.draft)
            .put("attachments", attachmentsToJson(state.attachments))
            .put("queue", JSONArray(state.queue.map { row ->
                JSONObject()
                    .put("id", row.id)
                    .put("text", row.text)
                    .put("created_at", row.createdAt)
                    .put("attachments", attachmentsToJson(row.attachments))
            }))
        prefs.edit().putString(key, encrypt(root.toString())).apply()
    }

    fun referencedAttachmentUris(): Set<String> = prefs.all.keys.flatMap { key ->
        val state = load(key)
        state.attachments.map { it.uri } + state.queue.flatMap { it.attachments.map { file -> file.uri } }
    }.toSet()

    fun remove(key: String) = prefs.edit().remove(key).apply()

    private fun attachmentsToJson(items: List<ComposerAttachment>): JSONArray = JSONArray(items.map {
        JSONObject()
            .put("id", it.id)
            .put("uri", it.uri)
            .put("name", it.name)
            .put("mime", it.mimeType)
            .put("size", it.size)
    })

    private fun attachmentsFromJson(array: JSONArray?): List<ComposerAttachment> = buildList {
        val arr = array ?: return@buildList
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let {
            add(
                ComposerAttachment(
                    id = it.optString("id"),
                    uri = it.optString("uri"),
                    name = it.optString("name"),
                    mimeType = it.optString("mime", "application/octet-stream"),
                    size = it.optLong("size", 0L)
                )
            )
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
        val body = cipher.doFinal(clear.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(cipher.iv.size + body.size)
        cipher.iv.copyInto(payload)
        body.copyInto(payload, cipher.iv.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        require(raw.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        return String(cipher.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8)
    }
}
