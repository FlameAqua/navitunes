package ie.adrianszydlo.navitunes.data.remote

import android.util.Log
import ie.adrianszydlo.navitunes.data.LibrarySignals
import ie.adrianszydlo.navitunes.data.discovery.SpotifyResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

enum class ServerDownloadStatus { PENDING, DOWNLOADING, DONE, FAILED }

/** One server-side (spotdl) download request, tracked through its lifecycle. */
data class ServerDownload(
    val key: String,            // "track:<id>" — unique per Spotify entity
    val type: String,           // track / album / artist / playlist
    val spotifyId: String,
    val title: String,
    val subtitle: String,
    val coverUrl: String?,
    val status: ServerDownloadStatus,
    val error: String? = null,      // last failure/retry reason (may be set while PENDING)
    val attempts: Int = 0,          // real request attempts (server reached but failed/busy)
    val offlineWaits: Int = 0,      // consecutive "server unreachable" waits
    val requestedAt: Long = System.currentTimeMillis(),
    val nextAttemptAt: Long = 0L    // earliest time this item may be (re)tried
) {
    val isActive: Boolean
        get() = status == ServerDownloadStatus.PENDING || status == ServerDownloadStatus.DOWNLOADING
}

/**
 * Process-scoped, resilient queue for server-side downloads.
 *
 * Robustness guarantees:
 *  - **One at a time.** The server is single-flight (`sendRunning`), so we mirror
 *    that: requests run sequentially. A waiting item holds no socket, so a long
 *    queue never causes waiting items to time out — each is started fresh, with a
 *    liveness probe, when it reaches the front.
 *  - **Liveness-gated.** Right before each request we probe `/health`. If the
 *    server is unreachable, the item is *not* failed — it waits and retries
 *    (bounded), so a brief server restart or wifi blip doesn't wipe the queue.
 *  - **Retry with backoff.** Transient failures and `409 busy` are retried with
 *    exponential backoff before giving up.
 *  - **Survives navigation.** State lives here, not in any composable, so the same
 *    in-progress items show wherever you are — and a re-search of the same song
 *    shows "downloading" instead of offering a duplicate request.
 */
class DownloadManager(
    private val scope: CoroutineScope,
    private val service: DownloadService,
    private val librarySignals: LibrarySignals? = null
) {
    private val _items = MutableStateFlow<List<ServerDownload>>(emptyList())
    val items: StateFlow<List<ServerDownload>> = _items.asStateFlow()

    /** Keys of everything currently pending or downloading — cheap for row lookups. */
    val activeKeys: StateFlow<Set<String>> = _items
        .map { list -> list.filter { it.isActive }.map { it.key }.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /**
     * Whether the server is currently reachable, polled while work is active.
     * `null` = idle / unknown (no active downloads). Surfaced by the manager UI.
     */
    private val _serverOnline = MutableStateFlow<Boolean?>(null)
    val serverOnline: StateFlow<Boolean?> = _serverOnline.asStateFlow()

    // Conflated wake-up: nudges the worker to re-evaluate the queue (new item
    // enqueued, or a retry timer needs recomputing).
    private val wake = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch { worker() }
        scope.launch { healthMonitor() }
    }

    fun key(type: String, id: String) = "$type:$id"

    /**
     * Queue a download. No-ops if the same entity is already pending/downloading,
     * so double taps and re-searches can't create duplicates.
     */
    fun enqueue(result: SpotifyResult) {
        val key = key(result.type.apiValue, result.id)
        if (_items.value.any { it.key == key && it.isActive }) return

        val item = ServerDownload(
            key = key,
            type = result.type.apiValue,
            spotifyId = result.id,
            title = result.title,
            subtitle = result.subtitle,
            coverUrl = result.coverUrl,
            status = ServerDownloadStatus.PENDING
        )
        // Replace any prior finished attempt for the same entity.
        _items.update { list -> list.filterNot { it.key == key } + item }
        wake.trySend(Unit)
    }

    /** Drop finished (done/failed) entries from the list. */
    fun clearFinished() {
        _items.update { list -> list.filter { it.isActive } }
    }

    /**
     * Force the server to stop whatever it's currently downloading — even a job this
     * app instance no longer tracks (e.g. a playlist that got orphaned across an app
     * restart and is now wedging the single-flight lock, causing "server busy").
     */
    fun cancelServerJob() {
        scope.launch { runCatching { service.requestCancel() } }
    }

    /** Remove a single finished entry. */
    fun remove(key: String) {
        _items.update { list -> list.filterNot { it.key == key && !it.isActive } }
    }

    /**
     * Cancel a queued or in-flight download. Drops it from the list and — if the
     * server is actively downloading it — asks the server to kill the spotdl process.
     * The in-flight request then returns to a no-op (the item is already gone, so it
     * isn't retried).
     */
    fun cancel(key: String) {
        val item = _items.value.firstOrNull { it.key == key } ?: return
        val wasDownloading = item.status == ServerDownloadStatus.DOWNLOADING
        _items.update { list -> list.filterNot { it.key == key } }
        if (wasDownloading) {
            scope.launch { runCatching { service.requestCancel() } }
        }
        wake.trySend(Unit)
        Log.i(TAG, "cancelled $key (wasDownloading=$wasDownloading)")
    }

    /** Manually re-queue a failed item, resetting its retry budget. */
    fun retry(key: String) {
        _items.update { list ->
            list.map {
                if (it.key == key && it.status == ServerDownloadStatus.FAILED) {
                    it.copy(
                        status = ServerDownloadStatus.PENDING,
                        error = null,
                        attempts = 0,
                        offlineWaits = 0,
                        nextAttemptAt = 0L
                    )
                } else it
            }
        }
        wake.trySend(Unit)
    }

    // ------------------------------------------------------------------------
    // Worker — processes one runnable PENDING item at a time.
    // ------------------------------------------------------------------------
    private suspend fun worker() {
        while (true) {
            val now = System.currentTimeMillis()
            val pending = _items.value.filter { it.status == ServerDownloadStatus.PENDING }
            val ready = pending.filter { it.nextAttemptAt <= now }.minByOrNull { it.requestedAt }

            if (ready == null) {
                // Nothing runnable now: sleep until the soonest retry timer is due,
                // or until something is enqueued (whichever comes first).
                val soonest = pending.minOfOrNull { it.nextAttemptAt }
                if (soonest == null) {
                    wake.receive()
                } else {
                    withTimeoutOrNull((soonest - now).coerceAtLeast(0L).milliseconds) { wake.receive() }
                }
                continue
            }

            process(ready)
        }
    }

    private suspend fun process(item: ServerDownload) {
        val key = item.key
        patch(key) { it.copy(status = ServerDownloadStatus.DOWNLOADING, error = null) }

        // Liveness gate — never send the request to an unreachable server.
        if (!service.isAlive()) {
            _serverOnline.value = false
            onUnreachable(item)
            return
        }
        _serverOnline.value = true

        when (val outcome = service.requestDownload("${item.type} ${item.spotifyId}")) {
            DownloadService.Outcome.Success -> {
                // The server now accepts the job instantly (202) and downloads in the
                // background, so "success" only means "accepted". Wait for the server
                // to actually finish (poll its busy flag) before calling it done — and
                // keep the worker here so the next item doesn't start mid-download.
                awaitServerIdle(key)
                // If the item was cancelled meanwhile it's gone; patch no-ops.
                patch(key) { it.copy(status = ServerDownloadStatus.DONE, error = null) }
                librarySignals?.notifyChanged()   // nudge screens to pick up the new song(s)
                Log.i(TAG, "done $key")
            }
            DownloadService.Outcome.Busy ->
                onRequestFailed(item, "Server busy")
            is DownloadService.Outcome.Failure ->
                onRequestFailed(item, outcome.message)
        }
    }

    /**
     * Blocks until the server reports no active download (or a safety cap elapses).
     * Stops early if the item was cancelled/removed. Degrades to a near-instant
     * return if the server doesn't expose a `downloading` flag.
     */
    private suspend fun awaitServerIdle(key: String) {
        val deadline = System.currentTimeMillis() + MAX_JOB_MS
        delay(1_500.milliseconds)   // give the server a moment to flip 'downloading' on
        while (System.currentTimeMillis() < deadline) {
            if (_items.value.none { it.key == key }) return    // cancelled/removed
            if (!service.isDownloading()) return
            delay(4_000.milliseconds)
        }
    }

    /** Server didn't answer the liveness probe — wait and retry, up to a bound. */
    private fun onUnreachable(item: ServerDownload) {
        val waits = item.offlineWaits + 1
        if (waits >= MAX_OFFLINE_WAITS) {
            patch(item.key) {
                it.copy(status = ServerDownloadStatus.FAILED, error = "Server unreachable", offlineWaits = waits)
            }
            Log.w(TAG, "failed ${item.key}: server unreachable after $waits waits")
            return
        }
        patch(item.key) {
            it.copy(
                status = ServerDownloadStatus.PENDING,
                error = "Waiting for server…",
                offlineWaits = waits,
                nextAttemptAt = System.currentTimeMillis() + OFFLINE_BACKOFF_MS
            )
        }
        wake.trySend(Unit)
    }

    /** Server was reached but the request failed or was busy — backed-off retry. */
    private fun onRequestFailed(item: ServerDownload, reason: String) {
        val attempts = item.attempts + 1
        if (attempts >= MAX_ATTEMPTS) {
            patch(item.key) {
                it.copy(status = ServerDownloadStatus.FAILED, error = reason, attempts = attempts)
            }
            Log.w(TAG, "failed ${item.key} after $attempts attempts: $reason")
            return
        }
        val backoff = (5_000L * pow3(attempts - 1)).coerceAtMost(60_000L) // 5s, 15s, 45s → cap 60s
        patch(item.key) {
            it.copy(
                status = ServerDownloadStatus.PENDING,
                error = "$reason — retrying",
                attempts = attempts,
                nextAttemptAt = System.currentTimeMillis() + backoff
            )
        }
        wake.trySend(Unit)
        Log.i(TAG, "retry ${item.key} in ${backoff}ms ($reason)")
    }

    private fun pow3(n: Int): Long {
        var r = 1L
        repeat(n.coerceAtLeast(0)) { r *= 3 }
        return r
    }

    private fun patch(key: String, f: (ServerDownload) -> ServerDownload) {
        _items.update { list -> list.map { if (it.key == key) f(it) else it } }
    }

    // ------------------------------------------------------------------------
    // Health monitor — the "occasional /health check" while downloads are active.
    // Drives the online/offline indicator in the manager UI.
    // ------------------------------------------------------------------------
    private suspend fun healthMonitor() {
        while (true) {
            if (_items.value.any { it.isActive }) {
                _serverOnline.value = service.isAlive()
                delay(HEALTH_POLL_MS.milliseconds)
            } else {
                _serverOnline.value = null
                activeKeys.first { it.isNotEmpty() } // suspend cheaply until work returns
            }
        }
    }

    private companion object {
        const val TAG = "Navitunes/DownloadMgr"
        const val MAX_ATTEMPTS = 4              // real request attempts before giving up
        const val MAX_OFFLINE_WAITS = 40        // ~40 × 8s ≈ 5 min of patiently waiting
        const val OFFLINE_BACKOFF_MS = 8_000L
        const val HEALTH_POLL_MS = 10_000L
        const val MAX_JOB_MS = 30 * 60 * 1000L  // safety cap while waiting for a server download
    }
}
