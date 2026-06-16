package ie.adrianszydlo.navitunes.data.offline

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import ie.adrianszydlo.navitunes.data.api.ApiClient
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.data.auth.ProfileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.File

/**
 * Front-end for the offline subsystem: enqueues downloads, tracks status,
 * and answers "do we have a local copy of song X?" for the active profile.
 */
class DownloadRepository(
    private val context: Context,
    private val db: DownloadDb,
    private val api: ApiClient,
    private val profileStore: ProfileStore
) {

    fun observeForActiveProfile(): Flow<List<DownloadEntity>> {
        val id = profileStore.activeId.value ?: return emptyFlow()
        return db.dao().observe(id)
    }

    suspend fun fileUriForSongOrNull(songId: String): String? {
        val pid = profileStore.activeId.value ?: return null
        val row = db.dao().bySongId(songId, pid) ?: return null
        if (row.status != STATUS_COMPLETED) return null
        val file = File(row.filePath)
        if (!file.exists() || file.length() == 0L) return null
        // android.net.Uri.fromFile produces "file:///abs/path" — three slashes —
        // which ExoPlayer's DefaultDataSource resolves to FileDataSource.
        return android.net.Uri.fromFile(file).toString()
    }

    suspend fun enqueueSong(song: Song, wifiOnly: Boolean) {
        val pid = profileStore.activeId.value ?: return
        val targetFile = fileFor(pid, song)
        val row = DownloadEntity(
            songId = song.id,
            profileId = pid,
            title = song.title,
            artist = song.artist.orEmpty(),
            album = song.album.orEmpty(),
            coverArt = song.coverArt,
            duration = song.duration,
            filePath = targetFile.absolutePath,
            sizeBytes = 0L,
            status = STATUS_QUEUED
        )
        db.dao().upsert(row)

        val data = Data.Builder()
            .putString(DownloadWorker.KEY_SONG_ID, song.id)
            .putString(DownloadWorker.KEY_PROFILE_ID, pid)
            .putString(DownloadWorker.KEY_STREAM_URL, api.downloadUrl(song.id))
            .putString(DownloadWorker.KEY_TARGET_PATH, targetFile.absolutePath)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .addTag("download:$pid")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "download-${song.id}-$pid",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    suspend fun enqueueAll(songs: List<Song>, wifiOnly: Boolean) {
        songs.forEach { enqueueSong(it, wifiOnly) }
    }

    suspend fun removeDownload(songId: String) {
        val pid = profileStore.activeId.value ?: return
        val row = db.dao().bySongId(songId, pid) ?: return
        runCatching { File(row.filePath).delete() }
        db.dao().delete(songId, pid)
    }

    suspend fun clearAllForActiveProfile() {
        val pid = profileStore.activeId.value ?: return
        db.dao().allForProfile(pid).forEach { runCatching { File(it.filePath).delete() } }
        db.dao().deleteAllForProfile(pid)
    }

    suspend fun totalBytesForActiveProfile(): Long {
        val pid = profileStore.activeId.value ?: return 0L
        return db.dao().allForProfile(pid)
            .filter { it.status == STATUS_COMPLETED }
            .sumOf { it.sizeBytes }
    }

    private fun fileFor(profileId: String, song: Song): File {
        val dir = File(context.filesDir, "offline/$profileId").apply { mkdirs() }
        val ext = song.suffix?.lowercase()?.takeIf { it.matches(Regex("[a-z0-9]{1,5}")) } ?: "mp3"
        return File(dir, "${sanitize(song.id)}.$ext")
    }

    private fun sanitize(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    companion object {
        const val STATUS_QUEUED = "queued"
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
    }
}
