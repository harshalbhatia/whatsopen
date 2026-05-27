package com.example.whatsopen.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.whatsopen.ui.theme.WhatsOpenTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WhatsOpenAppTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun shows_by_number_destination_by_default() {
        composeTestRule.setContent {
            WhatsOpenTheme(dynamicColor = false) { WhatsOpenApp() }
        }

        composeTestRule.onNodeWithText("TODO: ByNumber").assertIsDisplayed()
    }

    @Test
    fun tapping_call_logs_navigates_to_call_logs() {
        composeTestRule.setContent {
            WhatsOpenTheme(dynamicColor = false) { WhatsOpenApp() }
        }

        composeTestRule.onNodeWithText("Call Logs").performClick()

        composeTestRule.onNodeWithText("TODO: CallLogs").assertIsDisplayed()
    }

    @Test
    fun tapping_clipboard_navigates_to_clipboard() {
        composeTestRule.setContent {
            WhatsOpenTheme(dynamicColor = false) { WhatsOpenApp() }
        }

        composeTestRule.onNodeWithText("Clipboard").performClick()

        composeTestRule.onNodeWithText("TODO: Clipboard").assertIsDisplayed()
    }

    @Test
    fun tapping_by_number_returns_to_by_number() {
        composeTestRule.setContent {
            WhatsOpenTheme(dynamicColor = false) { WhatsOpenApp() }
        }

        composeTestRule.onNodeWithText("Call Logs").performClick()
        composeTestRule.onNodeWithText("TODO: CallLogs").assertIsDisplayed()

        composeTestRule.onNodeWithText("By Number").performClick()
        composeTestRule.onNodeWithText("TODO: ByNumber").assertIsDisplayed()
    }
}
