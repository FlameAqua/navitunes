package ie.adrianszydlo.navitunes.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Downloads an update APK from the project's own GitHub release and hands it to
 * the system package installer.
 *
 * Safety notes: the APK is fetched over HTTPS from the release configured at build
 * time, and Android's installer independently verifies that the new APK is signed
 * with the **same key** as the installed app before it will update in place — a
 * tampered or third-party APK is rejected by the OS, not just by us. The user
 * confirms the install in the system UI; this never installs silently.
 */
class ApkInstaller(okHttp: OkHttpClient) {

    private val client: OkHttpClient = okHttp.newBuilder()
        .readTimeout(DOWNLOAD_TIMEOUT_MIN, TimeUnit.MINUTES)
        .callTimeout(DOWNLOAD_TIMEOUT_MIN, TimeUnit.MINUTES)
        .build()

    sealed interface Result {
        /** The installer UI was launched; the OS now drives the rest. */
        data object Launched : Result
        /** The user must first allow "install unknown apps"; settings were opened. */
        data object NeedsUnknownSourcesPermission : Result
        data class Failed(val message: String) : Result
    }

    /**
     * Downloads [apkUrl] into `cacheDir/updates`, reporting fractional progress
     * (0f..1f) via [onProgress], then launches the system installer.
     */
    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        versionName: String,
        onProgress: (Float) -> Unit
    ): Result = withContext(Dispatchers.IO) {
        // On API 26+ the app must hold "install unknown apps" before we can hand
        // over an APK. Bounce the user to that settings screen if it's not granted.
        if (!context.packageManager.canRequestPackageInstalls()) {
            withContext(Dispatchers.Main) { openUnknownSourcesSettings(context) }
            return@withContext Result.NeedsUnknownSourcesPermission
        }

        val target = runCatching { downloadTo(context, apkUrl, versionName, onProgress) }
            .getOrElse {
                Log.w(TAG, "download failed", it)
                return@withContext Result.Failed(it.message ?: "Download failed")
            }

        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            withContext(Dispatchers.Main) { context.startActivity(intent) }
            Result.Launched
        }.getOrElse {
            Log.w(TAG, "install launch failed", it)
            Result.Failed(it.message ?: "Couldn't open the installer")
        }
    }

    private fun downloadTo(
        context: Context,
        apkUrl: String,
        versionName: String,
        onProgress: (Float) -> Unit
    ): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Clear stale APKs so the cache doesn't grow unbounded.
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "navitunes-$versionName.apk")

        val request = Request.Builder().url(apkUrl).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val bodyStream = resp.body?.byteStream() ?: error("Empty response")
            val total = resp.body?.contentLength() ?: -1L
            out.outputStream().use { sink ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                var downloaded = 0L
                while (bodyStream.read(buffer).also { read = it } != -1) {
                    sink.write(buffer, 0, read)
                    downloaded += read
                    if (total > 0) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        return out
    }

    private fun openUnknownSourcesSettings(context: Context) {
        runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private companion object {
        const val TAG = "Navitunes/Update"
        const val DOWNLOAD_TIMEOUT_MIN = 5L
    }
}
