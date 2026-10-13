package com.nousresearch.hermesmobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(base: android.content.Context) { super.attachBaseContext(com.nousresearch.hermesmobile.ui.AppLanguage.wrap(base)) }
    private lateinit var vm: HermesViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = (application as HermesApplication).viewModel
        val freshViewModel = vm.attachActivity()
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
    override fun onStart() { super.onStart(); (application as HermesApplication).screenStarted(); com.nousresearch.hermesmobile.push.PushDelivery.schedule(this) }
    override fun onStop() { (application as HermesApplication).screenStopped(); super.onStop() }
}
