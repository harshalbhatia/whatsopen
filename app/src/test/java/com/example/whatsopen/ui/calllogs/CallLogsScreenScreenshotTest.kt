package com.example.whatsopen.ui.calllogs

import android.provider.CallLog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.whatsopen.data.CallLogItem
import com.example.whatsopen.data.FakeCallLogRepository
import com.example.whatsopen.ui.theme.WhatsOpenTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
class CallLogsScreenScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val populated = listOf(
        CallLogItem("+15551234567", baseDate, CallLog.Calls.INCOMING_TYPE, isContact = true),
        CallLogItem("+447700900123", baseDate - 60_000, CallLog.Calls.MISSED_TYPE, isContact = false),
        CallLogItem("+919876543210", baseDate - 3_600_000, CallLog.Calls.OUTGOING_TYPE, isContact = false),
        CallLogItem("5552223333", baseDate - 86_400_000, CallLog.Calls.INCOMING_TYPE, isContact = false),
    )

    @Test
    fun needs_permission_light() = capture(
        dark = false,
        scenario = Scenario.NEEDS_PERMISSION,
        name = "needs_permission_light",
    )

    @Test
    fun denied_permission_light() = capture(
        dark = false,
        scenario = Scenario.DENIED,
        name = "denied_permission_light",
    )

    @Test
    fun empty_list_light() = capture(
        dark = false,
        scenario = Scenario.EMPTY,
        name = "empty_list_light",
    )

    @Test
    fun populated_light() = capture(
        dark = false,
        scenario = Scenario.POPULATED,
        name = "populated_light",
    )

    @Test
    fun populated_dark() = capture(
        dark = true,
        scenario = Scenario.POPULATED,
        name = "populated_dark",
    )

    @Test
    fun populated_with_incoming_filter_active_light() = capture(
        dark = false,
        scenario = Scenario.POPULATED_FILTERED,
        name = "populated_with_incoming_filter_active_light",
    )

    private enum class Scenario { NEEDS_PERMISSION, DENIED, EMPTY, POPULATED, POPULATED_FILTERED }

    private fun capture(dark: Boolean, scenario: Scenario, name: String) {
        val repo = FakeCallLogRepository(
            items = if (scenario == Scenario.EMPTY) emptyList() else populated,
        )
        val vm = CallLogsViewModel(SavedStateHandle(), repo)
        when (scenario) {
            Scenario.NEEDS_PERMISSION -> { /* default state */ }
            Scenario.DENIED -> vm.onCallLogPermissionResult(false, false)
            Scenario.EMPTY,
            Scenario.POPULATED -> vm.onCallLogPermissionResult(true, true)
            Scenario.POPULATED_FILTERED -> {
                vm.onCallLogPermissionResult(true, true)
                vm.toggleIncoming(true)
            }
        }
        composeTestRule.setContent {
            WhatsOpenTheme(darkTheme = dark, dynamicColor = false) {
                CallLogsScreen(onOpenChat = {}, vm = vm)
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/CallLogs_$name.png")
    }

    private companion object {
        // Stable fake "now" so screenshots are deterministic.
        // 2025-01-15T12:00:00Z = 1736942400000
        const val baseDate = 1_736_942_400_000L
    }
}
