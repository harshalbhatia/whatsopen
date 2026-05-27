package com.example.whatsopen.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.whatsopen.R
import com.example.whatsopen.ui.theme.WhatsOpenTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WhatsOpenAppTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun setAppContent() {
        composeTestRule.setContent {
            WhatsOpenTheme(dynamicColor = false) { WhatsOpenApp() }
        }
    }

    @Test
    fun shows_by_number_destination_by_default() {
        setAppContent()

        composeTestRule
            .onNodeWithText(context.getString(R.string.subtitle))
            .assertIsDisplayed()
    }

    @Test
    fun tapping_call_logs_navigates_to_call_logs() {
        setAppContent()

        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.nav_call_logs))
            .performClick()

        composeTestRule.onNodeWithText("TODO: CallLogs").assertIsDisplayed()
    }

    @Test
    fun tapping_clipboard_navigates_to_clipboard() {
        setAppContent()

        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.nav_clipboard))
            .performClick()

        composeTestRule.onNodeWithText("TODO: Clipboard").assertIsDisplayed()
    }

    @Test
    fun tapping_by_number_returns_to_by_number() {
        setAppContent()

        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.nav_call_logs))
            .performClick()
        composeTestRule.onNodeWithText("TODO: CallLogs").assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.nav_by_number))
            .performClick()
        composeTestRule
            .onNodeWithText(context.getString(R.string.subtitle))
            .assertIsDisplayed()
    }
}
