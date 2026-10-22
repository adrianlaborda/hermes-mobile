package com.nousresearch.hermesmobile.update

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nousresearch.hermesmobile.BuildConfig
import com.nousresearch.hermesmobile.MainActivity
import com.nousresearch.hermesmobile.R
import com.nousresearch.hermesmobile.data.HermesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** What the server's `hermes-android` plugin is, next to what this app needs and what is published. */
data class PluginStatus(val installed: AppVersion, val latest: AppVersion?) {
    /** Below what this app needs: notifications may not work until it is updated. */
    val required: Boolean get() = installed < AppUpdates.MIN_PLUGIN
    val updateAvailable: Boolean get() = latest != null && latest > installed
}

sealed interface InstallState {
    data object Idle : InstallState
    data class Downloading(val progress: Float) : InstallState
    /** Android asks the user, once, to let Hermes install apps. */
    data object NeedsPermission : InstallState
    data object Installing : InstallState
    data class Failed(val reason: String) : InstallState
}

data class UpdateState(
    val available: AppRelease? = null,
    val checking: Boolean = false,
    /** Set by a manual check: it found nothing newer, or it could not reach GitHub. */
    val upToDate: Boolean = false,
    val checkFailed: Boolean = false,
    val showDialog: Boolean = false,
    val install: InstallState = InstallState.Idle,
    val plugin: PluginStatus? = null
)

/**
 * Updates outside an app store. Once a day (and when the app opens, if that check is stale) the app reads the
 * GitHub releases of [BuildConfig.UPDATE_REPO]. A newer version is announced once: as a notification when it is
 * found in the background, as a dialog when the app is open, and it stays in Settings. Installing downloads the
 * APK, checks it against `SHA256SUMS` and the installed app's signing certificate, and hands it to Android's
 * package installer, which updates in place and keeps the app's data.
 *
 * It also reads the server's `hermes-android` plugin version (`plugins.manage list`) and says when it is older than
 * this app needs ([MIN_PLUGIN]) or than the newest plugin release.
 */
object AppUpdates {
    /** The plugin version this app's notifications need (CHANGELOG: "Requires `hermes-android` …"). */
    val MIN_PLUGIN = AppVersion(0, 5, 4, emptyList())
    const val PLUGIN_REPO = "adrianlaborda/hermes-android-plugin"
    const val PLUGIN_UPDATE_COMMAND = "hermes plugins update hermes-android"
    const val ACTION_OPEN = "hermes.update"

    private const val PREFS = "hermes_updates"
    private const val CHANNEL = "hermes_updates"
    private const val NOTIFICATION_ID = 7301
    private const val STALE_MS = 6 * 60 * 60 * 1000L
    private const val PLUGIN_STALE_MS = 12 * 60 * 60 * 1000L
    private const val MAX_APK_BYTES = 256L * 1024 * 1024

    val current: AppVersion = AppVersion.parse(BuildConfig.VERSION_NAME) ?: AppVersion(0, 0, 0, emptyList())
    val enabled: Boolean get() = BuildConfig.UPDATE_REPO.isNotBlank()

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http by lazy { OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build() }
    private var pluginCheckedAt = 0L
    private var pluginConnection: String? = null

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Pre-releases too. Defaults to on for a pre-release install: whoever runs a beta keeps getting betas. */
    fun beta(context: Context): Boolean = prefs(context).getBoolean("beta", current.prerelease)
    fun setBeta(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("beta", value).remove("last_check").apply()
        checkNow(context, manual = true)
    }

    /** Daily background check; replaces nothing already scheduled. Debug builds only check by hand. */
    fun schedule(context: Context) {
        if (!enabled || BuildConfig.DEBUG) return
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("hermes-update-check", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** The app came to the foreground: restore what is known and check again if that is old. */
    fun onAppStarted(context: Context) {
        val app = context.applicationContext
        if (!enabled) return
        restore(app)
        val last = prefs(app).getLong("last_check", 0)
        if (!BuildConfig.DEBUG && System.currentTimeMillis() - last > STALE_MS) checkNow(app, manual = false)
        else announceInApp(app)
    }

    fun checkNow(context: Context, manual: Boolean) {
        val app = context.applicationContext
        if (!enabled || _state.value.checking) return
        _state.update { it.copy(checking = true, upToDate = false, checkFailed = false) }
        scope.launch {
            val found = runCatching { fetch(app) }
            _state.update { it.copy(checking = false, upToDate = manual && found.isSuccess && it.available == null,
                checkFailed = manual && found.isFailure) }
            if (found.isSuccess) announceInApp(app)
        }
    }

    /** Reads the release lists and stores the result. Returns the newer release, if any. */
    internal suspend fun fetch(context: Context): AppRelease? = withContext(Dispatchers.IO) {
        val releases = ReleaseFeed.parse(get("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases?per_page=30"))
        val newest = ReleaseFeed.newest(releases, current, beta(context))
        val plugin = runCatching { ReleaseFeed.newestStable(get("https://api.github.com/repos/$PLUGIN_REPO/releases?per_page=30")) }.getOrNull()
        prefs(context).edit().apply {
            putLong("last_check", System.currentTimeMillis())
            if (newest != null) putString("release", encode(newest)) else remove("release")
            plugin?.let { putString("plugin_latest", it.toString()) }
        }.apply()
        _state.update { s -> s.copy(available = newest, plugin = s.plugin?.copy(latest = plugin ?: s.plugin.latest)) }
        newest
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "HermesMobile/${BuildConfig.VERSION_NAME}")
            .build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            return response.body.string()
        }
    }

    private fun restore(context: Context) {
        if (_state.value.available != null) return
        val release = prefs(context).getString("release", null)?.let { runCatching { decode(it) }.getOrNull() }
        _state.update { it.copy(available = release?.takeIf { r -> r.version > current }) }
    }

    /** Opens the dialog once per version while the app is in use. */
    private fun announceInApp(context: Context) {
        val release = _state.value.available ?: return
        if (prefs(context).getString("announced", null) == release.version.toString()) return
        prefs(context).edit().putString("announced", release.version.toString()).apply()
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        _state.update { it.copy(showDialog = true) }
    }

    /** From the background check: a notification, once per version. */
    internal fun notifyInBackground(context: Context, release: AppRelease) {
        val prefs = prefs(context)
        if (prefs.getString("announced", null) == release.version.toString()) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled() ||
            (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.update_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val tap = PendingIntent.getActivity(context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).setAction(ACTION_OPEN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_hermes).setColor(0xFF0053FD.toInt())
            .setContentTitle(context.getString(R.string.update_available_title, release.version.toString()))
            .setContentText(context.getString(R.string.update_available_tap))
            .setContentIntent(tap).setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
        prefs.edit().putString("announced", release.version.toString()).apply()
    }

    fun openDialog(context: Context) {
        restore(context.applicationContext)
        _state.update { it.copy(showDialog = it.available != null) }
    }

    fun dismissDialog() = _state.update {
        it.copy(showDialog = false, install = if (it.install is InstallState.Downloading || it.install is InstallState.Installing) it.install else InstallState.Idle)
    }

    fun resetInstall() = _state.update { it.copy(install = InstallState.Idle) }

    fun clearMessage() = _state.update { it.copy(upToDate = false, checkFailed = false) }

    /** Downloads, verifies and installs [UpdateState.available]. */
    fun install(context: Context) {
        val app = context.applicationContext
        val release = _state.value.available ?: return
        val busy = _state.value.install
        if (busy is InstallState.Downloading || busy is InstallState.Installing) return
        if (!app.packageManager.canRequestPackageInstalls()) {
            _state.update { it.copy(install = InstallState.NeedsPermission) }
            return
        }
        _state.update { it.copy(install = InstallState.Downloading(0f)) }
        scope.launch {
            runCatching {
                val apk = download(app, release)
                verify(app, apk, release)
                _state.update { it.copy(install = InstallState.Installing) }
                commit(app, apk)
            }.onFailure { e -> _state.update { it.copy(install = InstallState.Failed(e.message ?: e.javaClass.simpleName)) } }
        }
    }

    private fun download(context: Context, release: AppRelease): File {
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "hermes-mobile-${release.version}.apk")
        val expected = release.checksumsUrl?.let { ReleaseFeed.checksum(get(it), file.name) ?: error("checksum_missing") }
        val digest = MessageDigest.getInstance("SHA-256")
        val request = Request.Builder().url(release.apkUrl).header("User-Agent", "HermesMobile/${BuildConfig.VERSION_NAME}").build()
        http.newBuilder().callTimeout(15, TimeUnit.MINUTES).build().newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val total = response.body.contentLength().takeIf { it > 0 } ?: release.apkSize
            check(total <= MAX_APK_BYTES) { "too_large" }
            response.body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        read += n
                        check(read <= MAX_APK_BYTES) { "too_large" }
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        if (total > 0) _state.update { it.copy(install = InstallState.Downloading((read.toFloat() / total).coerceIn(0f, 1f))) }
                    }
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (expected != null && actual != expected) { file.delete(); error("checksum_mismatch") }
        return file
    }

    /** Same package, a higher version, and signed by the certificate this install was signed with. */
    private fun verify(context: Context, apk: File, release: AppRelease) {
        val pm = context.packageManager
        val archive = archiveInfo(pm, apk.path) ?: error("not_an_apk")
        val installed = installedInfo(pm, context.packageName)
        check(archive.packageName == context.packageName) { "wrong_package" }
        val ours = signers(installed)
        val theirs = signers(archive)
        check(ours.isNotEmpty() && (theirs == ours || history(archive).containsAll(ours))) { "signature_mismatch" }
        check(archive.longVersionCodeCompat() > installed.longVersionCodeCompat() && release.version > current) { "not_newer" }
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    private fun commit(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            // Android 12+: once Hermes installed itself, later updates need no confirmation.
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(context, id, Intent(context, UpdateInstallReceiver::class.java), flags)
            session.commit(callback.intentSender)
        }
    }

    internal fun onInstallStatus(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            // On success Android replaces and restarts the app; this process does not survive to see it.
            PackageInstaller.STATUS_SUCCESS -> _state.update { it.copy(install = InstallState.Idle, showDialog = false) }
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.update { it.copy(install = InstallState.Idle) }
            else -> _state.update {
                it.copy(install = InstallState.Failed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"))
            }
        }
    }

    /** After a connection is ready: which `hermes-android` the server runs. Silent when it has none. */
    fun checkPlugin(context: Context, connectionId: String, repo: HermesRepository) {
        val now = System.currentTimeMillis()
        if (connectionId == pluginConnection && now - pluginCheckedAt < PLUGIN_STALE_MS) return
        if (connectionId != pluginConnection) _state.update { it.copy(plugin = null) }
        pluginConnection = connectionId
        pluginCheckedAt = now
        val latest = prefs(context).getString("plugin_latest", null)?.let(AppVersion::parse)
        scope.launch {
            val rows = runCatching { repo.gateway.requestObject("plugins.manage", JSONObject().put("action", "list"), 60_000) }
                .getOrNull()?.optJSONArray("plugins") ?: return@launch
            val row = (0 until rows.length()).mapNotNull(rows::optJSONObject)
                .firstOrNull { it.optString("name") == "hermes-android" || it.optString("key").substringAfterLast('/') == "hermes-android" }
            val installed = row?.optString("version")?.let(AppVersion::parse)
            if (connectionId != pluginConnection) return@launch
            _state.update { it.copy(plugin = installed?.let { v -> PluginStatus(v, latest ?: it.plugin?.latest) }) }
        }
    }

    private fun encode(r: AppRelease) = JSONObject().put("version", r.version.toString()).put("notes", r.notes)
        .put("page", r.pageUrl).put("apk", r.apkUrl).put("size", r.apkSize).put("sums", r.checksumsUrl ?: "").toString()

    private fun decode(text: String): AppRelease {
        val o = JSONObject(text)
        return AppRelease(AppVersion.parse(o.getString("version"))!!, o.optString("notes"), o.optString("page"),
            o.getString("apk"), o.optLong("size"), o.optString("sums").ifBlank { null })
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, path: String): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33) pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(signingFlag().toLong()))
        else pm.getPackageArchiveInfo(path, signingFlag())

    @Suppress("DEPRECATION")
    private fun installedInfo(pm: PackageManager, name: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(name, PackageManager.PackageInfoFlags.of(signingFlag().toLong()))
        else pm.getPackageInfo(name, signingFlag())

    @Suppress("DEPRECATION")
    private fun signingFlag() = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val certs = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return certs.orEmpty().map { sha256(it.toByteArray()) }.toSet()
    }

    /** Certificates the APK's signing lineage vouches for (key rotation, Android 9+). */
    private fun history(info: PackageInfo): Set<String> =
        if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.signingCertificateHistory.orEmpty().map { sha256(it.toByteArray()) }.toSet()
        else emptySet()

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long = if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()
}

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = AppUpdates.onInstallStatus(context, intent)
}

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val release = runCatching { AppUpdates.fetch(applicationContext) }.getOrElse { return Result.retry() }
        release?.let { AppUpdates.notifyInBackground(applicationContext, it) }
        return Result.success()
    }
}
