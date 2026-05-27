package com.example.whatsopen.ui.calllogs

import android.content.Context
import android.provider.CallLog
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.whatsopen.data.CallLogItem
import com.example.whatsopen.data.FakeCallLogRepository
import com.example.whatsopen.ui.theme.WhatsOpenTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CallLogsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    private val sample = listOf(
        CallLogItem("111", 1_000, CallLog.Calls.INCOMING_TYPE, isContact = true),
        CallLogItem("222", 2_000, CallLog.Calls.MISSED_TYPE, isContact = false),
        CallLogItem("333", 3_000, CallLog.Calls.OUTGOING_TYPE, isContact = false),
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun setContent(
        vm: CallLogsViewModel,
        onOpenChat: (String) -> Unit = {},
    ) {
        composeTestRule.setContent {
            WhatsOpenTheme(dynamicColor = false) {
                CallLogsScreen(vm = vm, onOpenChat = onOpenChat)
            }
        }
    }

    private fun grantedVm(items: List<CallLogItem> = sample): CallLogsViewModel {
        val repo = FakeCallLogRepository(items = items)
        val vm = CallLogsViewModel(SavedStateHandle(), repo)
        vm.onCallLogPermissionResult(callLogGranted = true, contactsGranted = true)
        return vm
    }

    @Test
    fun permission_needed_state_shows_grant_button() {
        val repo = FakeCallLogRepository()
        val vm = CallLogsViewModel(SavedStateHandle(), repo)
        setContent(vm)

        composeTestRule
            .onNodeWithTag(CallLogsTags.GRANT_PERMISSION_BUTTON)
            .assertIsDisplayed()
    }

    @Test
    fun granted_with_logs_shows_list() {
        val vm = grantedVm()
        setContent(vm)

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(CallLogsTags.CALL_LOG_LIST)
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithTag(CallLogsTags.CALL_LOG_ROW)
            .assertCountEquals(3)
    }

    @Test
    fun granted_no_logs_shows_empty_state() {
        val vm = grantedVm(items = emptyList())
        setContent(vm)

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(CallLogsTags.EMPTY_STATE)
            .assertIsDisplayed()
    }

    @Test
    fun tapping_incoming_chip_filters_list() {
        val vm = grantedVm()
        setContent(vm)

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(CallLogsTags.CHIP_INCOMING)
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule
            .onAllNodesWithTag(CallLogsTags.CALL_LOG_ROW)
            .assertCountEquals(1)
    }

    @Test
    fun chip_state_preserved_through_recomposition() {
        val vm = grantedVm()
        setContent(vm)

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(CallLogsTags.CHIP_MISSED)
            .performClick()
        composeTestRule.waitForIdle()

        // Re-click should toggle off, then back on, exercising state survival
        composeTestRule
            .onNodeWithTag(CallLogsTags.CHIP_MISSED)
            .performClick()
        composeTestRule
            .onNodeWithTag(CallLogsTags.CHIP_MISSED)
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule
            .onAllNodesWithTag(CallLogsTags.CALL_LOG_ROW)
            .assertCountEquals(1) // only the missed row
    }

    @Test
    fun tapping_chat_button_invokes_callback() {
        val vm = grantedVm(items = listOf(sample[0]))
        var captured: String? = null
        setContent(vm) { captured = it }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(CallLogsTags.CHAT_BUTTON)
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals("111", captured)
    }

    @Test
    fun filtered_empty_state_visible_when_filter_yields_no_results() {
        // Only one outgoing item, then filter to incoming
        val vm = grantedVm(items = listOf(sample[2]))
        setContent(vm)

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(CallLogsTags.CHIP_INCOMING)
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag(CallLogsTags.FILTERED_EMPTY_STATE)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(CallLogsTags.CALL_LOG_LIST)
            .assertIsNotDisplayed()
    }
}

