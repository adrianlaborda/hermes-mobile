package com.nousresearch.hermesmobile

import android.app.Application

class HermesApplication : Application() {
    override fun attachBaseContext(base: android.content.Context) { super.attachBaseContext(com.nousresearch.hermesmobile.ui.AppLanguage.wrap(base)) }
    override fun onCreate() {
        super.onCreate()
        // Before any FCM callback: a prebuilt APK only knows the user's imported Firebase project.
        runCatching { com.nousresearch.hermesmobile.push.FirebaseRuntime.ensureInitialized(this) }
        // Capture leftovers before any screen can start a new recording. Never touch arbitrary cache files.
        val oldAudio = cacheDir.listFiles()?.filter { it.name.startsWith("hermes-voice-") || it.name.startsWith("hermes-speech-") }.orEmpty()
        Thread({
            oldAudio.forEach { it.delete() }
            // Snapshot old imports only; new imports and all persisted drafts/queues are protected.
            runCatching {
                val used = com.nousresearch.hermesmobile.data.ChatComposerStore(this).referencedAttachmentUris()
                java.io.File(filesDir, "incoming").listFiles()?.filter {
                    System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L &&
                        android.net.Uri.fromFile(it).toString() !in used
                }?.forEach { it.delete() }
            }
            com.nousresearch.hermesmobile.features.MobileFiles.clean(this)
        }, "hermes-cache-cleanup").start()
    }
}
