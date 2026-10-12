package com.nousresearch.hermesmobile

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(base: android.content.Context) { super.attachBaseContext(com.nousresearch.hermesmobile.ui.AppLanguage.wrap(base)) }
    private lateinit var vm: HermesViewModel
    private lateinit var connectivity: ConnectivityManager
    private var foreground = false
    private var lastNetwork: Network? = null
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = update(true)
        override fun onLost(network: Network) = update(true)
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = update(false)
    }
    private fun update(changed: Boolean) = runOnUiThread {
        // LAN gateways can work without Android's public-Internet validation.
        val active = connectivity.activeNetwork
        val networkChanged = active != lastNetwork
        lastNetwork = active
        vm.onEnvironmentChanged(foreground, active != null, networkChanged)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this)[HermesViewModel::class.java]
        val freshViewModel = vm.attachActivity()
        connectivity = getSystemService(ConnectivityManager::class.java)
        connectivity.registerDefaultNetworkCallback(callback)
        enableEdgeToEdge()
        setContent { HermesMobileApp(vm) }
        if (savedInstanceState == null) { vm.receiveShare(intent); receiveNotification(intent) }
        // Only a new ViewModel (process recreated) needs this: after a rotation the surviving one already knows.
        else if (freshViewModel) vm.restoreNavigation(savedInstanceState.getBundle(NAV_STATE))
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle(NAV_STATE, vm.navigationState())
    }
    private companion object { const val NAV_STATE = "hermes.navigation" }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.receiveShare(intent)
        receiveNotification(intent)
    }
    private fun receiveNotification(intent: Intent?) {
        val id = intent?.getStringExtra("event_id") ?: return
        val event = runCatching { com.nousresearch.hermesmobile.push.PushStore(this).events().firstOrNull { it.optString("event_id") == id } }.getOrNull()
        if (event != null) vm.openNotification(event)
        else { vm.notificationInboxRequested = true; com.nousresearch.hermesmobile.push.PushDelivery.schedule(this, id) }
    }
    override fun onStart() { super.onStart(); foreground = true; update(false); com.nousresearch.hermesmobile.push.PushDelivery.schedule(this) }
    override fun onStop() { foreground = false; update(false); super.onStop() }
    override fun onDestroy() { connectivity.unregisterNetworkCallback(callback); super.onDestroy() }
}
