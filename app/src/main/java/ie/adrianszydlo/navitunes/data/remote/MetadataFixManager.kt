package ie.adrianszydlo.navitunes.data.remote

import android.util.Log
import ie.adrianszydlo.navitunes.data.LibrarySignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Process-scoped tracker for the server-side metadata fix.
 *
 * State lives here (not in a composable), and the *server* is the source of truth, so
 * progress survives leaving Settings, backgrounding, and even an app restart — on
 * re-entry we just re-read `/fix/status`. While a job is running the manager polls for
 * stage updates, and when it finishes it ticks the library signal so screens reload.
 */
class MetadataFixManager(
    private val scope: CoroutineScope,
    private val service: MetadataFixService,
    private val librarySignals: LibrarySignals? = null
) {
    private val _state = MutableStateFlow(FixStatus())
    val state: StateFlow<FixStatus> = _state.asStateFlow()

    private var pollJob: Job? = null

    /** Re-read the server's status — call when the Settings screen appears. */
    fun refresh() {
        scope.launch { update(service.status()) }
    }

    /** Start a fix. No-ops while one is already running (button stays disabled). */
    fun start() {
        if (_state.value.running) return
        scope.launch {
            _state.value = FixStatus(running = true, stage = "starting", step = 0)
            update(service.start())
        }
    }

    private fun update(next: FixStatus) {
        val was = _state.value
        // Don't let a transient network blip clear a known-running job.
        if (next.unreachable && was.running) {
            _state.value = was.copy(message = next.message)
        } else {
            _state.value = next
        }
        if (next.running) {
            startPolling()
        } else if (was.running && !next.running) {
            pollJob?.cancel()
            pollJob = null
            // The library almost certainly changed (retags + rescan) — nudge screens.
            librarySignals?.notifyChanged()
            Log.i(TAG, "fix finished ok=${next.ok} msg=${next.message}")
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (_state.value.running) {
                delay(POLL_MS.milliseconds)
                update(service.status())
            }
        }
    }

    private companion object {
        const val TAG = "Navitunes/FixMgr"
        const val POLL_MS = 2_000L
    }
}
