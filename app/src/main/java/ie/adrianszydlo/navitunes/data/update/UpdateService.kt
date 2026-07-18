package ie.adrianszydlo.navitunes.data.update

import android.util.Log
import ie.adrianszydlo.navitunes.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Outcome of an update check. */
sealed interface UpdateStatus {
    /** Running the newest published release (or newer, for local dev builds). */
    data object UpToDate : UpdateStatus

    /** A newer release exists. */
    data class Available(
        val versionName: String,      // e.g. "0.5.0"
        val tag: String,              // raw tag, e.g. "v0.5.0"
        val notes: String,            // release body (may be blank)
        val apkUrl: String?,          // direct APK asset download URL, if published
        val releaseUrl: String        // human-facing release page
    ) : UpdateStatus

    /** Check couldn't complete (offline, rate-limited, no releases yet, etc.). */
    data class Failed(val message: String) : UpdateStatus
}

/**
 * Checks GitHub Releases for a newer version of the app.
 *
 * Queries `releases/latest` for [BuildConfig.GITHUB_REPO], compares the release
 * tag against [BuildConfig.VERSION_NAME] with a semantic-version comparison, and
 * reports whether an update is available (and where to get the APK). Network-only,
 * read-only, unauthenticated — never throws.
 */
class UpdateService(
    okHttp: OkHttpClient,
    private val repo: String = BuildConfig.GITHUB_REPO,
    private val currentVersion: String = BuildConfig.VERSION_NAME
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient = okHttp.newBuilder()
        .connectTimeout(CHECK_TIMEOUT_S, TimeUnit.SECONDS)
        .callTimeout(CHECK_TIMEOUT_S, TimeUnit.SECONDS)
        .build()

    suspend fun check(): UpdateStatus = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$repo/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Navitunes-Updater")
            .get()
            .build()

        runCatching {
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                when {
                    resp.isSuccessful -> parse(body)
                    resp.code == 404 -> UpdateStatus.Failed("No releases published yet.")
                    resp.code == 403 -> UpdateStatus.Failed("GitHub rate limit reached — try again later.")
                    else -> UpdateStatus.Failed("GitHub returned HTTP ${resp.code}.")
                }
            }
        }.getOrElse {
            Log.w(TAG, "update check failed", it)
            UpdateStatus.Failed(it.message ?: "Network error")
        }
    }

    private fun parse(body: String): UpdateStatus {
        val release = json.decodeFromString(GithubRelease.serializer(), body)
        val tag = release.tagName.orEmpty()
        val latest = tag.trimStart('v', 'V').trim()
        if (latest.isBlank()) return UpdateStatus.Failed("Latest release has no version tag.")

        return if (compareVersions(latest, currentVersion.trimStart('v', 'V')) > 0) {
            val apk = release.assets
                .firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?.browserDownloadUrl
            UpdateStatus.Available(
                versionName = latest,
                tag = tag,
                notes = release.body.orEmpty().trim(),
                apkUrl = apk,
                releaseUrl = release.htmlUrl ?: "https://github.com/$repo/releases/latest"
            )
        } else {
            UpdateStatus.UpToDate
        }
    }

    private companion object {
        const val TAG = "Navitunes/Update"
        const val CHECK_TIMEOUT_S = 10L
    }

    @Serializable
    private data class GithubRelease(
        @SerialName("tag_name") val tagName: String? = null,
        @SerialName("html_url") val htmlUrl: String? = null,
        val body: String? = null,
        val prerelease: Boolean = false,
        val assets: List<GithubAsset> = emptyList()
    )

    @Serializable
    private data class GithubAsset(
        val name: String = "",
        @SerialName("browser_download_url") val browserDownloadUrl: String? = null
    )
}

/**
 * Compares two dotted version strings numerically (e.g. "0.10.0" > "0.9.9").
 * A trailing pre-release suffix (`-beta`, `-rc1`) sorts *below* the same base
 * release. Returns >0 if [a] is newer than [b], 0 if equal, <0 if older.
 */
internal fun compareVersions(a: String, b: String): Int {
    fun parts(v: String): Pair<List<Int>, String> {
        val dash = v.indexOf('-')
        val core = if (dash >= 0) v.substring(0, dash) else v
        val pre = if (dash >= 0) v.substring(dash + 1) else ""
        val nums = core.split('.').map { it.trim().toIntOrNull() ?: 0 }
        return nums to pre
    }

    val (an, apre) = parts(a)
    val (bn, bpre) = parts(b)
    val size = maxOf(an.size, bn.size)
    for (i in 0 until size) {
        val cmp = (an.getOrElse(i) { 0 }).compareTo(bn.getOrElse(i) { 0 })
        if (cmp != 0) return cmp
    }
    // Equal cores: a release (no pre-release tag) outranks a pre-release.
    return when {
        apre.isBlank() && bpre.isBlank() -> 0
        apre.isBlank() -> 1
        bpre.isBlank() -> -1
        else -> apre.compareTo(bpre)
    }
}
