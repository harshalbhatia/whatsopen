package com.harshalbhatia.whatsopen

import android.app.Application
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the Call Logs tab shows. */
sealed interface CallLogsUiState {

    /** The device can't make phone calls, so there is no call log to ask for. */
    data object NoTelephony : CallLogsUiState

    /**
     * The app may not read the call log.
     *
     * @property permanentlyDenied Android won't ask the user again, so only the app's
     *   settings page can grant it.
     */
    data class NeedsPermission(val permanentlyDenied: Boolean) : CallLogsUiState

    data object Loading : CallLogsUiState

    /**
     * @property items The calls that pass [filter], grouped, newest first.
     * @property totalCalls How many calls were read before filtering.
     */
    data class Loaded(
        val items: List<CallLogItem>,
        val filter: CallLogFilter,
        val totalCalls: Int,
    ) : CallLogsUiState {
        /** How many calls [items] stand for. */
        val shownCalls: Int get() = items.sumOf { it.count }
    }

    /** The call log couldn't be read. */
    data object Error : CallLogsUiState
}

/**
 * Holds the Call Logs tab's state and reads the call log for it. The fragment decides, on
 * every resume, whether the call log may be read; this only reads it when told to.
 */
class CallLogsViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<CallLogsUiState>(CallLogsUiState.Loading)

    /** The tab's current state. */
    val state: StateFlow<CallLogsUiState> = _state.asStateFlow()

    /** Every call read, ungrouped; null until the call log has been read. Kept in memory only. */
    private var calls: List<CallLogItem>? = null
    private var filter = CallLogFilter()
    private var loadJob: Job? = null

    /** Shows that the device has no call log. */
    fun showNoTelephony() = show(CallLogsUiState.NoTelephony)

    /** Shows that the call log permission is needed. */
    fun showNeedsPermission(permanentlyDenied: Boolean) = show(CallLogsUiState.NeedsPermission(permanentlyDenied))

    /**
     * Reads the call log again. Calls already showing stay until the new ones arrive, so a
     * reload on resume doesn't flash the loading indicator.
     */
    fun load() {
        loadJob?.cancel()
        if (_state.value !is CallLogsUiState.Loaded) _state.value = CallLogsUiState.Loading
        val app = getApplication<Application>()
        loadJob = viewModelScope.launch {
            val read = withContext(ioDispatcher) {
                // Only the blocking query is guarded: a RuntimeException catch around a
                // suspension point would swallow cancellation.
                try {
                    Result.success(CallLogReader.query(app.contentResolver, DefaultRegion.get(app)))
                } catch (e: RuntimeException) {
                    Result.failure(e)
                }
            }
            calls = read.getOrNull()
            _state.value = calls?.let(::loaded) ?: failureState(read.exceptionOrNull())
        }
    }

    /** Shows only the calls that pass [filter]. */
    fun setFilter(filter: CallLogFilter) {
        this.filter = filter
        calls?.let { _state.value = loaded(it) }
    }

    private fun loaded(calls: List<CallLogItem>) =
        CallLogsUiState.Loaded(CallLogReader.group(calls.filter(filter::matches)), filter, calls.size)

    /** The state for a read that threw [e], or returned no cursor if [e] is null. */
    private fun failureState(e: Throwable?): CallLogsUiState = when (e) {
        // Granted as far as Android's check goes, yet refused, as some OEMs' own permission
        // layers do. The app's settings page is where that can be fixed.
        is SecurityException -> CallLogsUiState.NeedsPermission(permanentlyDenied = true)
        else -> {
            // The class name only: a provider's message could include call data.
            Log.w(TAG, "Couldn't read the call log: ${e?.javaClass?.name ?: "no cursor"}")
            CallLogsUiState.Error
        }
    }

    private fun show(state: CallLogsUiState) {
        loadJob?.cancel()
        calls = null
        _state.value = state
    }

    companion object {
        private const val TAG = "CallLogsViewModel"

        /** Where the call log is read. Replaceable in tests, to read it synchronously. */
        @VisibleForTesting
        internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    }
}
