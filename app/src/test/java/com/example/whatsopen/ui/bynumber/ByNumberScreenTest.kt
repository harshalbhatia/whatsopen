package com.example.whatsopen.ui.bynumber

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.whatsopen.CountryInfo
import com.example.whatsopen.CountryLookup
import com.example.whatsopen.R
import com.example.whatsopen.ui.theme.WhatsOpenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ByNumberScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    private val fakeLookup = object : CountryLookup {
        override fun lookup(numericCode: String): CountryInfo? = when (numericCode) {
            "91" -> CountryInfo("🇮🇳", "India")
            else -> null
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun setContent(vm: ByNumberViewModel, onLaunch: (String) -> Unit = {}) {
        composeTestRule.setContent {
            WhatsOpenTheme(dynamicColor = false) {
                ByNumberScreen(onLaunchWhatsApp = onLaunch, vm = vm)
            }
        }
    }

    @Test
    fun empty_state_button_visible() {
        val vm = ByNumberViewModel(SavedStateHandle(), fakeLookup)
        setContent(vm)

        composeTestRule
            .onNodeWithText(context.getString(R.string.open_in_whatsapp))
            .assertIsDisplayed()
    }

    @Test
    fun typing_country_code_shows_country_name() {
        val vm = ByNumberViewModel(SavedStateHandle(), fakeLookup)
        setContent(vm)

        composeTestRule
            .onNodeWithText(context.getString(R.string.country_code_hint))
            .performTextInput("91")

        composeTestRule.onNodeWithText("🇮🇳  India").assertIsDisplayed()
    }

    @Test
    fun submitting_empty_fields_shows_errors() {
        val vm = ByNumberViewModel(SavedStateHandle(), fakeLookup)
        setContent(vm)

        composeTestRule
            .onNodeWithText(context.getString(R.string.open_in_whatsapp))
            .performClick()

        composeTestRule
            .onNodeWithText(context.getString(R.string.error_country_code_required))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.error_phone_required))
            .assertIsDisplayed()
    }

    @Test
    fun valid_submit_invokes_callback_with_e164() {
        val vm = ByNumberViewModel(SavedStateHandle(), fakeLookup)
        var captured: String? = null
        setContent(vm) { captured = it }

        composeTestRule
            .onNodeWithText(context.getString(R.string.country_code_hint))
            .performTextInput("91")
        composeTestRule
            .onNodeWithText(context.getString(R.string.phone_number_hint))
            .performTextInput("9876543210")
        composeTestRule
            .onNodeWithText(context.getString(R.string.open_in_whatsapp))
            .performClick()

        composeTestRule.waitForIdle()
        assertEquals("919876543210", captured)
    }
}
