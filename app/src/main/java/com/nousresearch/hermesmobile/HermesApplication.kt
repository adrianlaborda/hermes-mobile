package com.nousresearch.hermesmobile

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Owns the one [HermesViewModel] of the process, so the app and the voice assistant overlay share the gateway
 * connection and the open chat. It is created on first use, never by a push wake-up.
 */
class HermesApplication : Application(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    val viewModel: HermesViewModel by lazy {
        ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(this))[HermesViewModel::class.java]
            .also { startNetworkTracking() }
    }

    // The gateway counts as foreground while any Hermes screen (app or assistant overlay) is started.
    private var startedScreens = 0
    private var lastNetwork: Network? = null
    private val main = Handler(Looper.getMainLooper())

    fun screenStarted() { startedScreens++; com.nousresearch.hermesmobile.push.AppPresence.foreground = true; reportEnvironment() }
    fun screenStopped() {
        startedScreens = (startedScreens - 1).coerceAtLeast(0)
        com.nousresearch.hermesmobile.push.AppPresence.foreground = anyScreenStarted
        reportEnvironment()
    }
    val anyScreenStarted: Boolean get() = startedScreens > 0

    private fun startNetworkTracking() {
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = report()
            override fun onLost(network: Network) = report()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = report()
            private fun report() { main.post { reportEnvironment() } }
        })
    }

    private fun reportEnvironment() {
        // LAN gateways can work without Android's public-Internet validation.
        val active = getSystemService(ConnectivityManager::class.java).activeNetwork
        val networkChanged = active != lastNetwork
        lastNetwork = active
        viewModel.onEnvironmentChanged(anyScreenStarted, active != null, networkChanged)
    }

    override fun attachBaseContext(base: android.content.Context) { super.attachBaseContext(com.nousresearch.hermesmobile.ui.AppLanguage.wrap(base)) }
    override fun onCreate() {
        super.onCreate()
        // Before any FCM callback: a prebuilt APK only knows the user's imported Firebase project.
        runCatching { com.nousresearch.hermesmobile.push.FirebaseRuntime.ensureInitialized(this) }
        com.nousresearch.hermesmobile.update.AppUpdates.schedule(this)
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
