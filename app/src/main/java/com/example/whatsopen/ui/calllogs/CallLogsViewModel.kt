package com.example.whatsopen.ui.calllogs

import android.provider.CallLog
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.whatsopen.data.CallLogItem
import com.example.whatsopen.data.CallLogRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PermissionsState { NeedsRequest, Denied, Granted }

enum class ContactsPermission { NotGranted, Granted }

data class CallLogsUiState(
    val permissionsState: PermissionsState = PermissionsState.NeedsRequest,
    val contactsPermission: ContactsPermission = ContactsPermission.NotGranted,
    val allLogs: List<CallLogItem> = emptyList(),
    val filteredLogs: List<CallLogItem> = emptyList(),
    val filterIncoming: Boolean = false,
    val filterMissed: Boolean = false,
    val filterNonContactsOnly: Boolean = false,
    val loadError: Boolean = false,
)

class CallLogsViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: CallLogRepository,
) : ViewModel() {

    private val permissionsStateFlow = MutableStateFlow(PermissionsState.NeedsRequest)
    private val contactsPermissionFlow = MutableStateFlow(ContactsPermission.NotGranted)
    private val callLogsFlow = MutableStateFlow<List<CallLogItem>>(emptyList())
    private val loadErrorFlow = MutableStateFlow(false)

    private val filterIncomingFlow = savedStateHandle.getStateFlow(KEY_FILTER_INCOMING, false)
    private val filterMissedFlow = savedStateHandle.getStateFlow(KEY_FILTER_MISSED, false)
    private val filterNonContactsFlow =
        savedStateHandle.getStateFlow(KEY_FILTER_NON_CONTACTS, false)

    val uiState: StateFlow<CallLogsUiState> = combine(
        combine(
            permissionsStateFlow,
            contactsPermissionFlow,
            callLogsFlow,
            loadErrorFlow,
        ) { perm, contacts, logs, err -> Quad(perm, contacts, logs, err) },
        filterIncomingFlow,
        filterMissedFlow,
        filterNonContactsFlow,
    ) { quad, fIncoming, fMissed, fNonContacts ->
        buildState(
            permissionsState = quad.permissions,
            contactsPermission = quad.contacts,
            allLogs = quad.logs,
            loadError = quad.error,
            filterIncoming = fIncoming,
            filterMissed = fMissed,
            filterNonContactsOnly = fNonContacts,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = buildState(
            permissionsState = permissionsStateFlow.value,
            contactsPermission = contactsPermissionFlow.value,
            allLogs = callLogsFlow.value,
            loadError = loadErrorFlow.value,
            filterIncoming = filterIncomingFlow.value,
            filterMissed = filterMissedFlow.value,
            filterNonContactsOnly = filterNonContactsFlow.value,
        ),
    )

    private val _openChatChannel = Channel<String>(Channel.BUFFERED)
    val openChatEvents: Flow<String> = _openChatChannel.receiveAsFlow()

    fun onCallLogPermissionResult(callLogGranted: Boolean, contactsGranted: Boolean) {
        contactsPermissionFlow.value =
            if (contactsGranted) ContactsPermission.Granted else ContactsPermission.NotGranted
        if (callLogGranted) {
            permissionsStateFlow.value = PermissionsState.Granted
            loadLogs()
        } else {
            permissionsStateFlow.value = PermissionsState.Denied
        }
    }

    fun onContactsPermissionResult(granted: Boolean) {
        contactsPermissionFlow.value =
            if (granted) ContactsPermission.Granted else ContactsPermission.NotGranted
        if (granted && permissionsStateFlow.value == PermissionsState.Granted) {
            loadLogs()
        }
    }

    fun toggleIncoming(checked: Boolean) {
        savedStateHandle[KEY_FILTER_INCOMING] = checked
    }

    fun toggleMissed(checked: Boolean) {
        savedStateHandle[KEY_FILTER_MISSED] = checked
    }

    fun toggleNonContacts(checked: Boolean) {
        savedStateHandle[KEY_FILTER_NON_CONTACTS] = checked
    }

    fun retry() {
        loadLogs()
    }

    fun onLogClicked(number: String) {
        viewModelScope.launch { _openChatChannel.send(number) }
    }

    private fun loadLogs() {
        val includeContactStatus = contactsPermissionFlow.value == ContactsPermission.Granted
        viewModelScope.launch {
            try {
                val items = repository.loadCallLogs(includeContactStatus)
                callLogsFlow.value = items
                loadErrorFlow.value = false
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                callLogsFlow.value = emptyList()
                loadErrorFlow.value = true
            }
        }
    }

    private fun buildState(
        permissionsState: PermissionsState,
        contactsPermission: ContactsPermission,
        allLogs: List<CallLogItem>,
        loadError: Boolean,
        filterIncoming: Boolean,
        filterMissed: Boolean,
        filterNonContactsOnly: Boolean,
    ): CallLogsUiState {
        val filtered = allLogs.filter { item ->
            val matchesType = when {
                filterIncoming && filterMissed ->
                    item.callType == CallLog.Calls.INCOMING_TYPE ||
                        item.callType == CallLog.Calls.MISSED_TYPE
                filterIncoming -> item.callType == CallLog.Calls.INCOMING_TYPE
                filterMissed -> item.callType == CallLog.Calls.MISSED_TYPE
                else -> true
            }
            val matchesContact = !filterNonContactsOnly || !item.isContact
            matchesType && matchesContact
        }
        return CallLogsUiState(
            permissionsState = permissionsState,
            contactsPermission = contactsPermission,
            allLogs = allLogs,
            filteredLogs = filtered,
            filterIncoming = filterIncoming,
            filterMissed = filterMissed,
            filterNonContactsOnly = filterNonContactsOnly,
            loadError = loadError,
        )
    }

    private data class Quad(
        val permissions: PermissionsState,
        val contacts: ContactsPermission,
        val logs: List<CallLogItem>,
        val error: Boolean,
    )

    companion object {
        private const val KEY_FILTER_INCOMING = "call_logs_filter_incoming"
        private const val KEY_FILTER_MISSED = "call_logs_filter_missed"
        private const val KEY_FILTER_NON_CONTACTS = "call_logs_filter_non_contacts"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
