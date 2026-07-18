package ie.adrianszydlo.navitunes.data.offline

import android.content.Context
import android.os.Environment
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import ie.adrianszydlo.navitunes.data.api.ApiClient
import ie.adrianszydlo.navitunes.data.api.Song
import ie.adrianszydlo.navitunes.data.auth.ProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Front-end for the offline subsystem: enqueues downloads, tracks status,
 * and answers "do we have a local copy of song X?" for the active profile.
 *
 * Files live in the PUBLIC Music/Navitunes/{profileId}/ folder so they survive
 * an app wipe/reinstall. Each audio file is accompanied by a small `.nmeta`
 * JSON sidecar holding its metadata, which lets [reconcile] rebuild the (wiped)
 * Room index purely from disk — no server round-trip required.
 */
class DownloadRepository(
    private val context: Context,
    private val db: DownloadDb,
    private val api: ApiClient,
    private val profileStore: ProfileStore
) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    @Serializable
    private data class OfflineMeta(
        val songId: String,
        val title: String,
        val artist: String,
        val album: String,
        val coverArt: String?,
        val duration: Int
    )

    fun hasStorageAccess(): Boolean = StoragePermission.hasAccess(context)

    fun observeForActiveProfile(): Flow<List<DownloadEntity>> {
        val id = profileStore.activeId.value ?: return emptyFlow()
        return db.dao().observe(id)
    }

    /** Stream of song IDs that are *fully downloaded* under the active profile. */
    fun observeDownloadedIdsForActiveProfile(): Flow<Set<String>> {
        val id = profileStore.activeId.value ?: return emptyFlow()
        return kotlinx.coroutines.flow.flow {
            db.dao().observe(id).collect { rows ->
                emit(
                    rows.asSequence()
                        .filter { it.status == STATUS_COMPLETED }
                        .map { it.songId }
                        .toSet()
                )
            }
        }
    }

    suspend fun fileUriForSongOrNull(songId: String): String? {
        val pid = profileStore.activeId.value ?: return null
        val row = db.dao().bySongId(songId, pid) ?: return null
        if (row.status != STATUS_COMPLETED) return null
        val file = File(row.filePath)
        if (!file.exists() || file.length() == 0L) return null
        // Uri.fromFile produces "file:///abs/path" which ExoPlayer's
        // DefaultDataSource routes to FileDataSource.
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
        runCatching { sidecarFor(File(row.filePath)).delete() }
        db.dao().delete(songId, pid)
    }

    suspend fun clearAllForActiveProfile() {
        val pid = profileStore.activeId.value ?: return
        db.dao().allForProfile(pid).forEach {
            runCatching { File(it.filePath).delete() }
            runCatching { sidecarFor(File(it.filePath)).delete() }
        }
        db.dao().deleteAllForProfile(pid)
    }

    suspend fun totalBytesForActiveProfile(): Long {
        val pid = profileStore.activeId.value ?: return 0L
        return db.dao().allForProfile(pid)
            .filter { it.status == STATUS_COMPLETED }
            .sumOf { it.sizeBytes }
    }

    /**
     * Reconciles the Room index with what's actually on disk for the active
     * profile. Called on launch so downloads survive an app wipe:
     *   - imports audio files present on disk but missing from the DB (via sidecar),
     *   - drops completed DB rows whose file has vanished.
     */
    suspend fun reconcile() = withContext(Dispatchers.IO) {
        val pid = profileStore.activeId.value ?: return@withContext
        if (!hasStorageAccess()) return@withContext

        val dir = profileDir(pid)
        val known = db.dao().allForProfile(pid).associateBy { it.songId }.toMutableMap()

        val audioOnDisk = dir.listFiles { f ->
            f.isFile && f.extension.lowercase() in AUDIO_EXTS
        }.orEmpty()

        // Import orphaned files.
        for (file in audioOnDisk) {
            val meta = readSidecar(sidecarFor(file)) ?: continue
            val existing = known[meta.songId]
            if (existing == null || existing.status != STATUS_COMPLETED) {
                db.dao().upsert(
                    DownloadEntity(
                        songId = meta.songId,
                        profileId = pid,
                        title = meta.title,
                        artist = meta.artist,
                        album = meta.album,
                        coverArt = meta.coverArt,
                        duration = meta.duration,
                        filePath = file.absolutePath,
                        sizeBytes = file.length(),
                        status = STATUS_COMPLETED
                    )
                )
            }
            known.remove(meta.songId)
        }

        // Any completed row left in `known` whose file no longer exists → prune.
        known.values
            .filter { it.status == STATUS_COMPLETED && !File(it.filePath).exists() }
            .forEach { db.dao().delete(it.songId, pid) }
    }

    /** Writes the metadata sidecar next to a freshly downloaded file. */
    fun writeSidecar(targetFile: File, entity: DownloadEntity) {
        runCatching {
            val meta = OfflineMeta(
                songId = entity.songId,
                title = entity.title,
                artist = entity.artist,
                album = entity.album,
                coverArt = entity.coverArt,
                duration = entity.duration
            )
            sidecarFor(targetFile).writeText(json.encodeToString(meta))
        }
    }

    private fun readSidecar(sidecar: File): OfflineMeta? =
        runCatching { json.decodeFromString<OfflineMeta>(sidecar.readText()) }.getOrNull()

    private fun sidecarFor(audioFile: File): File =
        File(audioFile.parentFile, audioFile.name + SIDECAR_EXT)

    private fun musicRoot(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Navitunes")

    private fun profileDir(profileId: String): File =
        File(musicRoot(), sanitize(profileId)).apply { mkdirs() }

    private fun fileFor(profileId: String, song: Song): File {
        val dir = profileDir(profileId)
        val ext = song.suffix?.lowercase()?.takeIf { it.matches(Regex("[a-z0-9]{1,5}")) } ?: "mp3"
        return File(dir, "${sanitize(song.id)}.$ext")
    }

    private fun sanitize(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    companion object {
        const val STATUS_QUEUED = "queued"
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"

        private const val SIDECAR_EXT = ".nmeta"
        private val AUDIO_EXTS = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav")
    }
}
