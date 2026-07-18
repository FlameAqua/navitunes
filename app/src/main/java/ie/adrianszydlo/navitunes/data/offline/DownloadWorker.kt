package ie.adrianszydlo.navitunes.data.offline

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ie.adrianszydlo.navitunes.NavitunesApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * Streams a single song from Subsonic `download.view` to local storage.
 * Writes to a `.part` temp file and atomically renames on success so a
 * cancelled / failed download never leaves a half-written real file.
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val songId = inputData.getString(KEY_SONG_ID) ?: return@withContext Result.failure()
        val profileId = inputData.getString(KEY_PROFILE_ID) ?: return@withContext Result.failure()
        val streamUrl = inputData.getString(KEY_STREAM_URL) ?: return@withContext Result.failure()
        val targetPath = inputData.getString(KEY_TARGET_PATH) ?: return@withContext Result.failure()

        Log.d(TAG, "Worker started for song=$songId profile=$profileId target=$targetPath")

        val container = NavitunesApp.container()
        val dao = container.downloadDb.dao()
        dao.updateStatus(songId, profileId, DownloadRepository.STATUS_DOWNLOADING)

        val target = File(targetPath)
        val tmp = File(target.absolutePath + ".part")

        try {
            target.parentFile?.mkdirs()
            val response = container.apiClient.okHttp.newCall(
                Request.Builder().url(streamUrl).get().build()
            ).execute()

            if (!response.isSuccessful) {
                dao.updateStatus(songId, profileId, DownloadRepository.STATUS_FAILED, "HTTP ${response.code}")
                return@withContext Result.retry()
            }

            // OkHttp 5 guarantees a non-null body on a successful response.
            response.body.use { rb ->
                rb.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        input.copyTo(output, 64 * 1024)
                    }
                }
            }

            if (!tmp.renameTo(target)) {
                // Fallback: copy + delete
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }

            val size = target.length()
            val row = dao.bySongId(songId, profileId)
            val completed = row?.copy(
                sizeBytes = size,
                status = DownloadRepository.STATUS_COMPLETED,
                errorMessage = null
            )
            if (completed != null) {
                dao.upsert(completed)
                // Drop a metadata sidecar so the index can be rebuilt from disk
                // after an app wipe (see DownloadRepository.reconcile).
                container.downloadRepository.writeSidecar(target, completed)
            } else {
                dao.updateStatus(songId, profileId, DownloadRepository.STATUS_COMPLETED)
            }
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Download failed for $songId", t)
            runCatching { tmp.delete() }
            dao.updateStatus(songId, profileId, DownloadRepository.STATUS_FAILED, t.message ?: t.javaClass.simpleName)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "Navitunes/DL"
        const val KEY_SONG_ID = "songId"
        const val KEY_PROFILE_ID = "profileId"
        const val KEY_STREAM_URL = "streamUrl"
        const val KEY_TARGET_PATH = "targetPath"
    }
}
