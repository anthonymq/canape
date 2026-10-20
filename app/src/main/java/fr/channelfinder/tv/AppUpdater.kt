package fr.channelfinder.tv

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object Current : UpdateStatus
    data class Available(val release: UpdateRelease) : UpdateStatus
    data class Downloading(val release: UpdateRelease, val percent: Int) : UpdateStatus
    data class Ready(val release: UpdateRelease, val file: File) : UpdateStatus
    data class Failed(val message: String, val release: UpdateRelease? = null) : UpdateStatus
}

internal class AppUpdater(private val context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val installed = context.packageManager.getPackageInfo(context.packageName, 0)
    val installedVersion: String = installed.versionName.orEmpty()
    private val installedCode = PackageInfoCompat.getLongVersionCode(installed)
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES).build()
    private val directory = File(context.cacheDir, "updates")
    private var operation: Job? = null
    private var lastCheck: Long? = null
    private var offeredVersion: Long? = null
    var status: UpdateStatus by mutableStateOf(UpdateStatus.Idle)
        private set
    var dialogOpen by mutableStateOf(false)
        private set
    var automatic by mutableStateOf(prefs.getBoolean("automatic", true))
        private set

    init { directory.listFiles()?.forEach(File::delete) }

    fun toggleAutomatic() {
        automatic = !automatic
        prefs.edit { putBoolean("automatic", automatic) }
    }

    fun check(manual: Boolean = false) {
        if (manual) dialogOpen = true
        if (operation?.isActive == true) return
        val now = SystemClock.elapsedRealtime()
        if (!manual && (!automatic || lastCheck?.let { now - it < 6 * 60 * 60_000L } == true)) return
        if (status is UpdateStatus.Ready) return
        lastCheck = now
        operation = scope.launch {
            status = UpdateStatus.Checking
            try {
                val release = withContext(Dispatchers.IO) {
                    response("${updateManifestUrl(context.packageName)}?check=${System.currentTimeMillis()}").use { reply ->
                        val body = reply.body ?: throw IOException()
                        val bytes = body.byteStream().use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(4096)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= 32 * 1024)
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                        UpdateRelease.parse(bytes.toString(Charsets.UTF_8), context.packageName)
                    }
                }
                status = when {
                    release.versionCode <= installedCode -> UpdateStatus.Current
                    release.minSdk > Build.VERSION.SDK_INT -> UpdateStatus.Failed("Cette version nécessite une version plus récente d’Android.")
                    else -> UpdateStatus.Available(release)
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                status = UpdateStatus.Failed("Impossible de vérifier les mises à jour. Vérifiez Internet, puis réessayez.")
            }
        }
    }

    fun offerOnHome() {
        val release = (status as? UpdateStatus.Available)?.release ?: return
        if (!automatic || offeredVersion == release.versionCode) return
        offeredVersion = release.versionCode
        dialogOpen = true
    }

    fun open() {
        dialogOpen = true
        if (status is UpdateStatus.Idle || status is UpdateStatus.Current || status is UpdateStatus.Failed) check(manual = true)
    }

    fun dismiss() {
        if (status is UpdateStatus.Downloading) cancelDownload()
        dialogOpen = false
    }

    fun cancelDownload() {
        val release = (status as? UpdateStatus.Downloading)?.release ?: return
        operation?.cancel()
        client.dispatcher.cancelAll()
        status = UpdateStatus.Available(release)
    }

    fun download(release: UpdateRelease) {
        if (operation?.isActive == true) return
        operation = scope.launch {
            status = UpdateStatus.Downloading(release, 0)
            var partial: File? = null
            val apk = File(directory, "${release.versionCode}-${release.sha256}.apk")
            try {
                withContext(Dispatchers.IO) {
                    kotlin.check(directory.isDirectory || directory.mkdirs())
                    val temporary = File.createTempFile("download-", ".part.apk", directory)
                    partial = temporary
                    apk.delete()
                    val digest = MessageDigest.getInstance("SHA-256")
                    response(release.apkUrl).use { reply ->
                        val body = reply.body ?: throw IOException()
                        body.byteStream().use { input ->
                            temporary.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var total = 0L
                                while (true) {
                                    ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    require(total <= release.size)
                                    output.write(buffer, 0, count)
                                    digest.update(buffer, 0, count)
                                    val percent = (total * 100 / release.size).toInt()
                                    withContext(Dispatchers.Main) { status = UpdateStatus.Downloading(release, percent) }
                                }
                                require(total == release.size)
                            }
                        }
                    }
                    val actualHash = digest.digest().hex()
                    require(actualHash.equals(release.sha256, ignoreCase = true))
                    validateArchive(temporary, release)
                    ensureActive()
                    kotlin.check(temporary.renameTo(apk))
                }
                status = UpdateStatus.Ready(release, apk)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                status = UpdateStatus.Failed("Téléchargement impossible ou fichier non valide. Réessayez depuis cette application.", release)
            } finally { partial?.delete() }
        }
    }

    fun installationFailed() {
        status = UpdateStatus.Failed("L’installation n’a pas pu démarrer. Vérifiez l’autorisation d’installation dans les réglages Android.",
            (status as? UpdateStatus.Ready)?.release)
    }

    fun validateReady(): File? {
        val ready = status as? UpdateStatus.Ready ?: return null
        return ready.file.takeIf { it.isFile && it.length() == ready.release.size }
    }

    private fun response(initialUrl: String): Response {
        var url = initialUrl
        repeat(6) {
            require(trustedUpdateUrl(url))
            val reply = client.newCall(Request.Builder().url(url).header("User-Agent", "Canape-Updater")
                .header("Cache-Control", "no-cache").build()).execute()
            if (reply.code in 300..399) {
                val next = reply.header("Location")?.let { reply.request.url.resolve(it)?.toString() }
                reply.close()
                url = next ?: throw IOException()
            } else {
                if (!reply.isSuccessful) { reply.close(); throw IOException() }
                return reply
            }
        }
        throw IOException()
    }

    private fun validateArchive(file: File, release: UpdateRelease) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: error("Invalid APK")
        val current = pm.getPackageInfo(context.packageName, flags)
        require(matchingUpdateIdentity(release, context.packageName, installedCode, archive.packageName,
            PackageInfoCompat.getLongVersionCode(archive), signerHashes(current), signerHashes(archive)))
    }

    @Suppress("DEPRECATION")
    private fun signerHashes(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return signatures.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() }.toSet()
    }
}

private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
