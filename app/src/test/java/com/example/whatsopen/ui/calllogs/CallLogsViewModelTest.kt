package com.example.whatsopen.ui.calllogs

import android.provider.CallLog
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.example.whatsopen.MainDispatcherRule
import com.example.whatsopen.data.CallLogItem
import com.example.whatsopen.data.FakeCallLogRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CallLogsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val sample = listOf(
        // 3 incoming
        CallLogItem(1L, "111", 1_000, CallLog.Calls.INCOMING_TYPE, isContact = true),
        CallLogItem(2L, "112", 2_000, CallLog.Calls.INCOMING_TYPE, isContact = false),
        CallLogItem(3L, "113", 3_000, CallLog.Calls.INCOMING_TYPE, isContact = false),
        // 2 missed
        CallLogItem(4L, "221", 4_000, CallLog.Calls.MISSED_TYPE, isContact = true),
        CallLogItem(5L, "222", 5_000, CallLog.Calls.MISSED_TYPE, isContact = false),
        // 2 outgoing
        CallLogItem(6L, "331", 6_000, CallLog.Calls.OUTGOING_TYPE, isContact = true),
        CallLogItem(7L, "332", 7_000, CallLog.Calls.OUTGOING_TYPE, isContact = false),
    )

    private fun newViewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        items: List<CallLogItem> = sample,
        shouldThrow: Boolean = false,
    ): Pair<CallLogsViewModel, FakeCallLogRepository> {
        val repo = FakeCallLogRepository(items = items, shouldThrow = shouldThrow)
        val vm = CallLogsViewModel(handle, repo)
        return vm to repo
    }

    @Test
    fun init_state_is_needs_request() = runTest {
        val (vm, _) = newViewModel()
        vm.uiState.test {
            val s = awaitItem()
            assertEquals(PermissionsState.NeedsRequest, s.permissionsState)
            assertTrue(s.allLogs.isEmpty())
            assertTrue(s.filteredLogs.isEmpty())
        }
    }

    @Test
    fun granting_permissions_loads_logs() = runTest {
        val (vm, repo) = newViewModel()
        vm.onCallLogPermissionResult(callLogGranted = true, contactsGranted = true)
        vm.uiState.test {
            val s = expectMostRecentItem()
            assertEquals(PermissionsState.Granted, s.permissionsState)
            assertEquals(ContactsPermission.Granted, s.contactsPermission)
            assertEquals(sample, s.allLogs)
        }
        assertEquals(1, repo.loadCount)
        assertEquals(true, repo.lastIncludeContactStatus)
    }

    @Test
    fun denying_permissions_emits_denied_state() = runTest {
        val (vm, repo) = newViewModel()
        vm.onCallLogPermissionResult(callLogGranted = false, contactsGranted = false)
        vm.uiState.test {
            val s = expectMostRecentItem()
            assertEquals(PermissionsState.Denied, s.permissionsState)
            assertTrue(s.allLogs.isEmpty())
        }
        assertEquals(0, repo.loadCount)
    }

    @Test
    fun no_filters_returns_all_logs() = runTest {
        val (vm, _) = newViewModel()
        vm.onCallLogPermissionResult(callLogGranted = true, contactsGranted = true)
        vm.uiState.test {
            val s = expectMostRecentItem()
            assertEquals(sample, s.filteredLogs)
        }
    }

    @Test
    fun incoming_filter_only_returns_incoming() = runTest {
        val (vm, _) = newViewModel()
        vm.onCallLogPermissionResult(callLogGranted = true, contactsGranted = true)
        vm.toggleIncoming(true)
        vm.uiState.test {
            val s = expectMostRecentItem()
            assertTrue(s.filteredLogs.all { it.callType == CallLog.Calls.INCOMING_TYPE })
            assertEquals(3, s.filteredLogs.size)
        }
    }

    @Test
    fun missed_filter_only_returns_missed() = runTest {
        val (vm, _) = newViewModel()
        vm.onCallLogPermissionResult(callLogGranted = true, contactsGranted = true)
        vm.toggleMissed(true)
        vm.uiState.test {
            val s = expectMostRecentItem()
            assertTrue(s.filteredLogs.all { it.callType == CallLog.Calls.MISSED_TYPE })
            assertEquals(2, s.filteredLogs.size)
        }
    }

    @Test
    fun incoming_or_missed_combines_with_or() = runTest {
        val (vm, _) = newViewModel()
        vm.onCallLogPermissionResult(callLogGranted = true, contactsGranted = true)
        vm.toggleIncoming(true)
        vm.toggleMissed(true)
        vm.uiState.test {
            val s = expectMostRecentItem()
            assertTrue(
                s.filteredLogs.all {
                    it.callType == CallLog.Calls.INCOMING_TYPE ||
                        it.callType == CallLog.Calls.MISSED_TYPE
                },
            )
            assertEquals(5, s.filteredLogs.size)
        }
    }

    @Test
    fun non_contacts_filter_excludes_contacts() = runTest {
        val (vm, _) = newViewModel()
        vm.onCallLogPermissionResult(callLogGranted = true, contactsGranted = true)
        vm.toggleNonContacts(true)
        vm.uiState.test {
            val s = expectMostRecentItem()
            assertTrue(s.filteredLogs.none { it.isContact })
            assertEquals(4, s.filteredLogs.size)
        }
    }

    @Test
    fun incoming_AND_non_contacts_returns_incoming_non_contacts() = runTest {
        val (vm, _) = newViewModel()
        vm.onCallLogPermissionResult(callLogGranted = true, contactsGranted = true)
        vm.toggleIncoming(true)
        vm.toggleNonContacts(true)
        vm.uiState.test {
            val s = expectMostRecentItem()
            assertTrue(
                s.filteredLogs.all {
                    it.callType == CallLog.Calls.INCOMING_TYPE && !it.isContact
                },
            )
            assertEquals(2, s.filteredLogs.size)
        }
    }

    @Test
    fun filter_state_survives_process_death() = runTest {
        val handle = SavedStateHandle()
        val (vmA, _) = newViewModel(handle)
        vmA.toggleIncoming(true)
        vmA.toggleNonContacts(true)

        val (vmB, _) = newViewModel(handle)
        vmB.uiState.test {
            val s = awaitItem()
            assertEquals(true, s.filterIncoming)
            assertEquals(true, s.filterNonContactsOnly)
            assertEquals(false, s.filterMissed)
        }
    }

    @Test
    fun repo_failure_emits_empty_list_with_load_error() = runTest {
        val (vm, _) = newViewModel(shouldThrow = true)
        vm.onCallLogPermissionResult(callLogGranted = true, contactsGranted = true)
        vm.uiState.test {
            val s = expectMostRecentItem()
            assertEquals(PermissionsState.Granted, s.permissionsState)
            assertTrue(s.allLogs.isEmpty())
            assertTrue(s.filteredLogs.isEmpty())
            assertEquals(true, s.loadError)
        }
    }

    @Test
    fun clicking_log_emits_open_chat_event() = runTest {
        val (vm, _) = newViewModel()
        vm.openChatEvents.test {
            vm.onLogClicked("+15551234567")
            assertEquals("+15551234567", awaitItem())
        }
    }

    @Test
    fun contacts_granted_after_initial_grant_updates_state() = runTest {
        val (vm, _) = newViewModel()
        vm.onCallLogPermissionResult(callLogGranted = true, contactsGranted = false)
        vm.uiState.test {
            val s1 = expectMostRecentItem()
            assertEquals(ContactsPermission.NotGranted, s1.contactsPermission)

            vm.onContactsPermissionResult(true)
            val s2 = awaitItem()
            assertEquals(ContactsPermission.Granted, s2.contactsPermission)
        }
    }
}
