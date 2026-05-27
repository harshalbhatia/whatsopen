package com.example.whatsopen.ui.clipboard

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.whatsopen.R
import com.example.whatsopen.ui.theme.WhatsOpenTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ClipboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun setContent(vm: ClipboardViewModel, onLaunch: (String) -> Unit = {}) {
        composeTestRule.setContent {
            WhatsOpenTheme(dynamicColor = false) {
                ClipboardScreen(onLaunchWhatsApp = onLaunch, vm = vm)
            }
        }
    }

    @Test
    fun empty_input_then_submit_shows_error() {
        val vm = ClipboardViewModel(SavedStateHandle())
        setContent(vm)

        composeTestRule
            .onNodeWithTag(ClipboardTags.SUBMIT)
            .performClick()

        composeTestRule
            .onNodeWithText(context.getString(R.string.error_no_valid_numbers))
            .assertIsDisplayed()
    }

    @Test
    fun valid_input_then_submit_invokes_callback() {
        val vm = ClipboardViewModel(SavedStateHandle())
        var captured: String? = null
        setContent(vm) { captured = it }

        composeTestRule
            .onNodeWithTag(ClipboardTags.TEXT_FIELD)
            .performTextInput("+14155551234")
        composeTestRule
            .onNodeWithTag(ClipboardTags.SUBMIT)
            .performClick()

        composeTestRule.waitForIdle()
        assertEquals("+14155551234", captured)
    }
}
