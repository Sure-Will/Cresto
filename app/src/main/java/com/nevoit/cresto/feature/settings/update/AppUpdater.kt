package com.nevoit.cresto.feature.settings.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import com.nevoit.cresto.BuildConfig
import com.nevoit.cresto.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed interface UpdateInstallState {
    data object Idle : UpdateInstallState

    /** Waiting for the user to allow installing apps in system settings. */
    data object AwaitingPermission : UpdateInstallState
    data class Downloading(val progress: Float) : UpdateInstallState
    data object Verifying : UpdateInstallState

    /** Handed to the system installer, which asks the user to confirm. */
    data object Installing : UpdateInstallState
    data class Failed(@param:StringRes val messageRes: Int, val detail: String? = null) :
        UpdateInstallState
}

val UpdateInstallState.isBusy: Boolean
    get() = this is UpdateInstallState.Downloading ||
        this is UpdateInstallState.Verifying ||
        this is UpdateInstallState.Installing

private class UpdateException(@param:StringRes val messageRes: Int, val detail: String? = null) :
    Exception()

/**
 * In-app update: download the APK from the release, check its size, SHA-256, package name,
 * version and signer, then hand it to PackageInstaller for the system confirmation.
 */
class AppUpdater private constructor(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadDir = File(app.filesDir, "updates")
    private var job: Job? = null
    private var awaitingPermission: UpdateManifest? = null

    private val _state = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    val state: StateFlow<UpdateInstallState> = _state.asStateFlow()

    /** System installer confirmation; the visible UI starts it and calls [consumeUserAction]. */
    private val _userAction = MutableStateFlow<Intent?>(null)
    val userAction: StateFlow<Intent?> = _userAction.asStateFlow()

    fun canInstall(): Boolean = app.packageManager.canRequestPackageInstalls()

    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${app.packageName}".toUri())

    /** Returns false when installs must be allowed first; the caller then opens [permissionIntent]. */
    fun update(manifest: UpdateManifest): Boolean {
        if (_state.value.isBusy) return true
        if (!canInstall()) {
            awaitingPermission = manifest
            _state.value = UpdateInstallState.AwaitingPermission
            return false
        }
        start(manifest)
        return true
    }

    /** Continues once the user comes back after allowing installs. */
    fun onResume() {
        val manifest = awaitingPermission ?: return
        if (canInstall()) {
            awaitingPermission = null
            start(manifest)
        }
    }

    /** Stops a download or verification; the system installer cannot be stopped from here. */
    fun cancel() {
        if (_state.value is UpdateInstallState.Installing) return
        job?.cancel()
        awaitingPermission = null
        _state.value = UpdateInstallState.Idle
    }

    fun consumeUserAction() {
        _userAction.value = null
    }

    fun onUserActionFailed() {
        _state.value = UpdateInstallState.Failed(R.string.update_error_start_install)
    }

    /** Deletes partial downloads and packages that are not newer than the running app. */
    suspend fun pruneDownloads() = withContext(Dispatchers.IO) {
        if (_state.value.isBusy) return@withContext
        downloadDir.listFiles()?.forEach { file ->
            val versionCode = file.name.removePrefix(FILE_PREFIX).removeSuffix(".apk").toIntOrNull()
            if (versionCode == null || versionCode <= BuildConfig.VERSION_CODE) file.delete()
        }
    }

    private fun start(manifest: UpdateManifest) {
        job = scope.launch {
            try {
                val apk = obtainApk(manifest)
                currentCoroutineContext().ensureActive()
                _state.value = UpdateInstallState.Verifying
                verifyArchive(apk, manifest)
                commitInstall(apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateException) {
                _state.value = UpdateInstallState.Failed(e.messageRes, e.detail)
            } catch (_: IOException) {
                _state.value = UpdateInstallState.Failed(R.string.update_error_network)
            } catch (_: Exception) {
                _state.value = UpdateInstallState.Failed(R.string.update_error_generic)
            }
        }
    }

    /** Called with the PackageInstaller result, forwarded by [UpdateReceiver]. */
    internal fun onInstallStatus(intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) _userAction.value = confirm else onUserActionFailed()
            }
            // On success the system stops this process and the new version starts fresh.
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                _state.value = UpdateInstallState.Failed(R.string.update_install_cancelled)
            }

            else -> _state.value = UpdateInstallState.Failed(
                R.string.update_error_install,
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            )
        }
    }

    /** Reuses a package that was already downloaded and verified, e.g. after a cancelled install. */
    private suspend fun obtainApk(manifest: UpdateManifest): File {
        val size = requireNotNull(manifest.apkSize)
        val expected = hex(requireNotNull(manifest.apkSha256))
        downloadDir.mkdirs()
        val target = File(downloadDir, "$FILE_PREFIX${manifest.versionCode}.apk")
        if (target.isFile && target.length() == size && MessageDigest.isEqual(sha256(target), expected)) {
            return target
        }
        downloadDir.listFiles()?.forEach { it.delete() }
        val part = File(downloadDir, "${target.name}.part")
        _state.value = UpdateInstallState.Downloading(0f)

        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        open(manifest.downloadUrl).useConnection { connection ->
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw UpdateException(R.string.update_error_download, "HTTP $responseCode")
            }
            val length = connection.contentLengthLong
            if (length > 0 && length != size) throw UpdateException(R.string.update_error_size)
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var lastEmitted = 0f
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > size) throw UpdateException(R.string.update_error_size)
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        val progress = total.toFloat() / size
                        if (progress - lastEmitted >= 0.01f) {
                            lastEmitted = progress
                            _state.value = UpdateInstallState.Downloading(progress)
                        }
                    }
                }
            }
        }
        if (total != size) throw UpdateException(R.string.update_error_size)
        if (!MessageDigest.isEqual(digest.digest(), expected)) {
            part.delete()
            throw UpdateException(R.string.update_error_checksum)
        }
        if (!part.renameTo(target)) throw UpdateException(R.string.update_error_storage)
        return target
    }

    private fun verifyArchive(apk: File, manifest: UpdateManifest) {
        val pm = app.packageManager
        val archive = pm.archiveInfo(apk.path) ?: throw UpdateException(R.string.update_error_package)
        if (archive.packageName != app.packageName) throw UpdateException(R.string.update_error_package)
        if (archive.longVersionCode != manifest.versionCode.toLong() ||
            archive.versionName != manifest.versionName ||
            archive.applicationInfo?.minSdkVersion != manifest.minSdk
        ) {
            throw UpdateException(R.string.update_error_version)
        }
        // Some Android versions cannot read the signers of an uninstalled archive. The system
        // installer still rejects an update signed with a different key.
        val incoming = signers(archive.signingInfo)
        if (incoming.isNotEmpty() && incoming != signers(pm.installedInfo(app.packageName).signingInfo)) {
            throw UpdateException(R.string.update_error_signature)
        }
    }

    private suspend fun commitInstall(apk: File) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = try {
            installer.createSession(params)
        } catch (_: Exception) {
            throw UpdateException(R.string.update_error_start_install)
        }
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                currentCoroutineContext().ensureActive()
                val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
                val callback = PendingIntent.getBroadcast(
                    app,
                    sessionId,
                    Intent(app, UpdateReceiver::class.java).setAction(UpdateReceiver.ACTION_INSTALL_STATUS),
                    PendingIntent.FLAG_UPDATE_CURRENT or mutable
                )
                _state.value = UpdateInstallState.Installing
                session.commit(callback.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            if (e is CancellationException) throw e
            throw UpdateException(R.string.update_error_start_install)
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", "Cresto/${BuildConfig.VERSION_NAME}")
        }

    private inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T =
        try {
            block(this)
        } finally {
            disconnect()
        }

    companion object {
        private const val FILE_PREFIX = "update-"

        @Volatile
        private var instance: AppUpdater? = null

        fun get(context: Context): AppUpdater =
            instance ?: synchronized(this) {
                instance ?: AppUpdater(context).also { instance = it }
            }

        private fun PackageManager.archiveInfo(path: String): PackageInfo? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getPackageArchiveInfo(
                    path,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)
            }

        private fun PackageManager.installedInfo(packageName: String): PackageInfo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            }

        private fun signers(info: SigningInfo?): Set<String> =
            info?.apkContentsSigners.orEmpty().map { cert ->
                MessageDigest.getInstance("SHA-256").digest(cert.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }.toSet()

        private fun sha256(file: File): ByteArray {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest()
        }

        private fun hex(value: String): ByteArray = ByteArray(value.length / 2) {
            value.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }
}
