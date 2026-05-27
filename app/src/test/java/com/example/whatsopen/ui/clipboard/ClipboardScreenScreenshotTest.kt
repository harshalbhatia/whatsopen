package com.example.whatsopen.ui.clipboard

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class ClipboardScreenScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun empty_light() = capture(dark = false, populated = false, error = false, name = "empty_light")

    @Test
    fun empty_dark() = capture(dark = true, populated = false, error = false, name = "empty_dark")

    @Test
    fun populated_light() = capture(dark = false, populated = true, error = false, name = "populated_light")

    @Test
    fun populated_dark() = capture(dark = true, populated = true, error = false, name = "populated_dark")

    @Test
    fun error_light() = capture(dark = false, populated = false, error = true, name = "error_light")

    @Test
    fun error_dark() = capture(dark = true, populated = false, error = true, name = "error_dark")

    private fun capture(dark: Boolean, populated: Boolean, error: Boolean, name: String) {
        val vm = ClipboardViewModel(SavedStateHandle())
        if (populated) {
            vm.onTextChanged("+1 415 555 1234")
        }
        if (error) {
            vm.onSubmit()
        }
        composeTestRule.setContent {
            WhatsOpenTheme(darkTheme = dark, dynamicColor = false) {
                ClipboardScreen(onLaunchWhatsApp = {}, vm = vm)
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/Clipboard_$name.png")
    }
}
