package ie.adrianszydlo.navitunes.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide "something in the library changed" signal. Mutations (uploads,
 * deletes, playlist edits) call [notifyChanged]; screens that cache data
 * observe [refresh] and reload when it ticks.
 */
class LibrarySignals {
    private val _refresh = MutableStateFlow(0L)
    val refresh: StateFlow<Long> = _refresh.asStateFlow()

    fun notifyChanged() { _refresh.value = _refresh.value + 1 }
}
