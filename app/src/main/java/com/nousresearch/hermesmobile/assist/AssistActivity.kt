package com.nousresearch.hermesmobile.assist

import android.Manifest
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nousresearch.hermesmobile.HermesApplication
import com.nousresearch.hermesmobile.MainActivity
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.ui.theme.HermesTheme

/**
 * Hermes as the digital assistant app (`ACTION_ASSIST`: holding the power button, the corner swipe…).
 *
 * The lock screen behaves like Gemini's: by default the assistant asks to unlock before it listens. With
 * "Use Hermes without unlocking" it listens and answers over the lock screen, showing only this overlay's turns;
 * opening the app, earlier conversations and confirming actions still need unlocking.
 */
class AssistActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) { super.attachBaseContext(com.nousresearch.hermesmobile.ui.AppLanguage.wrap(base)) }

    private val app get() = application as HermesApplication
    private lateinit var keyguard: KeyguardManager
    private lateinit var settings: AssistSettings
    private lateinit var controller: AssistController
    private lateinit var speech: AndroidSpeech

    private val unlocked = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { controller.locked = keyguard.isKeyguardLocked }
    }

    // A ComponentActivity, not a FragmentActivity: the old-Fragment request-code bug this lint guards against cannot apply.
    @android.annotation.SuppressLint("InvalidFragmentVersionForActivityResult")
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.listen() else controller.phase = AssistController.Phase.PERMISSION
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always drawn over the lock screen: either to ask for unlocking or, when allowed, to talk.
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        enableEdgeToEdge()
        keyguard = getSystemService(KeyguardManager::class.java)
        settings = AssistSettings(this)
        speech = AndroidSpeech(this)
        controller = AssistController(app, lifecycleScope, AssistVoice(this), speech, settings, getString(R.string.assist_voice_chat_title))
        controller.locked = keyguard.isKeyguardLocked
        ContextCompat.registerReceiver(this, unlocked, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent {
            HermesTheme {
                AssistOverlay(
                    controller = controller,
                    onDismiss = ::finish,
                    onUnlock = { unlockThen { begin() } },
                    onTalk = ::talk,
                    onOpenApp = { unlockThen(::openApp) }
                )
            }
        }
        if (savedInstanceState == null) begin()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The assist gesture again while open: talk again, unless already busy.
        if (!controller.busy) begin()
    }

    override fun onStart() { super.onStart(); app.screenStarted() }

    override fun onStop() {
        app.screenStopped()
        super.onStop()
        // Like Gemini's overlay, leaving it (screen off, home, another app) ends it.
        if (!isChangingConfigurations) { controller.cancel(); finish() }
    }

    override fun onDestroy() { unregisterReceiver(unlocked); speech.shutdown(); super.onDestroy() }

    private fun begin() {
        controller.locked = keyguard.isKeyguardLocked
        when (AssistPolicy.entry(controller.locked, settings.allowWhileLocked)) {
            AssistPolicy.Entry.UNLOCK_FIRST -> { controller.phase = AssistController.Phase.UNLOCK; unlockThen { begin() } }
            AssistPolicy.Entry.LISTEN -> talk()
        }
    }

    private fun talk() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) controller.listen()
        else if (controller.locked) unlockThen { micPermission.launch(Manifest.permission.RECORD_AUDIO) }
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    /** Runs [action] once the device is unlocked, showing the system's unlock prompt if needed. */
    private fun unlockThen(action: () -> Unit) {
        if (!keyguard.isKeyguardLocked) { controller.locked = false; action(); return }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { controller.locked = false; action() }
            override fun onDismissCancelled() { if (controller.phase == AssistController.Phase.UNLOCK) finish() }
            override fun onDismissError() { if (controller.phase == AssistController.Phase.UNLOCK) finish() }
        })
    }

    private fun openApp() {
        controller.cancel()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
