package com.nousresearch.hermesmobile.assist

import android.content.Context
import androidx.core.content.edit

/** The voice assistant's preferences and the voice conversation to continue, per connection and profile. */
class AssistSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("voice_assistant", Context.MODE_PRIVATE)

    var allowWhileLocked: Boolean
        get() = prefs.getBoolean("allow_locked", false)
        set(value) { prefs.edit { putBoolean("allow_locked", value) } }

    /** Read answers with the device's voice (default) instead of the server's text-to-speech. Also used by the chat. */
    var deviceVoice: Boolean
        get() = prefs.getBoolean("device_voice", true)
        set(value) { prefs.edit { putBoolean("device_voice", value) } }

    /**
     * The model voice chats use, as "provider\tmodel"; blank keeps the profile's model. Voice wants a fast model:
     * a reasoning model spends seconds thinking before the first word.
     */
    var voiceModel: Pair<String, String>?
        get() = prefs.getString("voice_model", "").orEmpty().split('\t').takeIf { it.size == 2 && it[1].isNotBlank() }?.let { it[0] to it[1] }
        set(value) { prefs.edit { putString("voice_model", value?.let { "${it.first}\t${it.second}" } ?: "") } }

    /** Let the voice model reason before answering. Off by default: thinking costs seconds before the first word. */
    var voiceThinking: Boolean
        get() = prefs.getBoolean("voice_thinking", false)
        set(value) { prefs.edit { putBoolean("voice_thinking", value) } }

    /** Recognize speech on the device (default) instead of recording and transcribing on the server. */
    var deviceRecognition: Boolean
        get() = prefs.getBoolean("device_recognition", true)
        set(value) { prefs.edit { putBoolean("device_recognition", value) } }

    /** Blank means the profile selected in the app. */
    var profile: String
        get() = prefs.getString("profile", "").orEmpty()
        set(value) { prefs.edit { putString("profile", value) } }

    /** Blank means the same profile as [profile]. */
    var lockedProfile: String
        get() = prefs.getString("locked_profile", "").orEmpty()
        set(value) { prefs.edit { putString("locked_profile", value) } }

    fun lastSession(connection: String, profile: String): Pair<String, Long>? {
        val id = prefs.getString("session:$connection:$profile", null) ?: return null
        return id to prefs.getLong("session_at:$connection:$profile", 0L)
    }

    fun rememberSession(connection: String, profile: String, sessionId: String, at: Long) {
        prefs.edit { putString("session:$connection:$profile", sessionId); putLong("session_at:$connection:$profile", at) }
    }
}
