package com.harshalbhatia.whatsopen

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Looper
import android.telephony.TelephonyManager
import android.text.InputFilter
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.chip.Chip
import com.harshalbhatia.whatsopen.databinding.FragmentClipboardBinding
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast
import org.robolectric.shadows.ShadowViewRootImpl
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class ClipboardFragmentTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    /** How many times the fragment read the clipboard's content. */
    private var reads = 0

    @Before
    fun setUp() {
        WhatsAppLauncher.resetForTesting()
        resetSnackbars()
        shadowOf(app.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).setNetworkCountryIso("us")
        ClipboardFragment.readPrimaryClip = {
            reads++
            it.primaryClip
        }
    }

    @After
    fun tearDown() {
        PendingShare.text = null
        clipboard.clearPrimaryClip()
        ClipboardFragment.readPrimaryClip = ClipboardManager::getPrimaryClip
        WhatsAppLauncher.resetForTesting()
    }

    private fun setClipboard(text: String, timestamp: Long = 0L) {
        val clip = ClipData.newPlainText("label", text)
        // Android stamps clips when they're copied; Robolectric doesn't.
        if (timestamp != 0L) {
            ReflectionHelpers.callInstanceMethod<Unit>(
                clip.description, "setTimestamp", ClassParameter.from(Long::class.javaPrimitiveType, timestamp)
            )
        }
        clipboard.setPrimaryClip(clip)
    }

    private fun launch(): FragmentScenario<ClipboardFragment> =
        launchFragmentInContainer<ClipboardFragment>(themeResId = R.style.Theme_WhatsOpen)
            .also { it.moveToState(Lifecycle.State.RESUMED) }

    private fun FragmentScenario<ClipboardFragment>.withBinding(
        block: (FragmentClipboardBinding, ClipboardFragment) -> Unit,
    ) = onFragment { block(FragmentClipboardBinding.bind(it.requireView()), it) }

    /** Gives the window focus again, as when the user comes back from another app or a dialog. */
    private fun refocus(activity: Activity) {
        val root = ReflectionHelpers.callInstanceMethod<Any>(activity.window.decorView, "getViewRootImpl")
        val shadow = Shadow.extract<ShadowViewRootImpl>(root)
        shadow.callWindowFocusChanged(false)
        shadow.callWindowFocusChanged(true)
        ShadowLooper.idleMainLooper()
    }

    /** Lets the debounced search run. */
    private fun idleForResults() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ClipboardFragment.RESULTS_DELAY_MS))
    }

    private fun installWhatsApp() {
        val info = PackageInfo().apply {
            packageName = WhatsAppApp.CONSUMER.packageName
            applicationInfo = ApplicationInfo().apply {
                packageName = WhatsAppApp.CONSUMER.packageName
                enabled = true
            }
        }
        shadowOf(app.packageManager).installPackage(info)
    }

    private fun nextChatUri(): Uri? = shadowOf(app).nextStartedActivity?.data

    private fun FragmentClipboardBinding.endIcon(): View =
        numbersInputLayout.findViewById(com.google.android.material.R.id.text_input_end_icon)

    // --- Clipboard suggestion ---

    @Test
    fun `clipboard is never read on resume or focus`() {
        setClipboard("+14155552671")
        val scenario = launch()
        scenario.withBinding { binding, fragment ->
            refocus(fragment.requireActivity())
            assertTrue(binding.clipboardSuggestion.isShown)
            assertEquals("", binding.numbersInput.text.toString())
        }
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        assertEquals(0, reads)
    }

    @Test
    fun `card shows only while the clipboard has text`() {
        val scenario = launch()
        scenario.withBinding { binding, fragment ->
            assertFalse(binding.clipboardSuggestion.isShown)

            setClipboard("hello")
            refocus(fragment.requireActivity())
            assertTrue(binding.clipboardSuggestion.isShown)
            assertEquals(app.getString(R.string.clipboard_suggestion_text), binding.clipboardSuggestionText.text)

            clipboard.clearPrimaryClip()
            refocus(fragment.requireActivity())
            assertFalse(binding.clipboardSuggestion.isShown)
        }
        assertEquals(0, reads)
    }

    @Test
    fun `card ignores clips that aren't text`() {
        clipboard.setPrimaryClip(ClipData("image", arrayOf("image/png"), ClipData.Item(Uri.parse("content://media/1"))))
        launch().withBinding { binding, fragment ->
            refocus(fragment.requireActivity())
            assertFalse(binding.clipboardSuggestion.isShown)
        }
    }

    @Test
    fun `tapping Paste reads the clipboard into the box and hides the card`() {
        setClipboard("Call +1 415-555-2671 today")
        launch().withBinding { binding, fragment ->
            refocus(fragment.requireActivity())
            binding.clipboardSuggestion.performClick()
            assertEquals(1, reads)
            assertEquals("Call +1 415-555-2671 today", binding.numbersInput.text.toString())
            assertFalse(binding.clipboardSuggestion.isShown)
            // Shown straight away, without waiting for the typing pause.
            assertTrue(binding.singleNumber.isShown)
        }
    }

    @Test
    fun `a pasted clip isn't offered again, a new one is`() {
        setClipboard("+14155552671", timestamp = 1_000L)
        launch().withBinding { binding, fragment ->
            refocus(fragment.requireActivity())
            binding.clipboardSuggestion.performClick()
            refocus(fragment.requireActivity())
            assertFalse(binding.clipboardSuggestion.isShown)

            setClipboard("+14155552672", timestamp = 2_000L)
            refocus(fragment.requireActivity())
            assertTrue(binding.clipboardSuggestion.isShown)
        }
    }

    @Test
    fun `paste icon in the box reads the clipboard and replaces the text`() {
        setClipboard("+14155552671")
        launch().withBinding { binding, _ ->
            binding.numbersInput.setText("old text")
            assertEquals(app.getString(R.string.paste), binding.numbersInputLayout.endIconContentDescription)
            binding.endIcon().performClick()
            assertEquals(1, reads)
            assertEquals("+14155552671", binding.numbersInput.text.toString())
        }
    }

    @Test
    fun `pasting an empty clipboard says so in a snackbar`() {
        launch().withBinding { binding, fragment ->
            binding.endIcon().performClick()
            assertEquals(app.getString(R.string.error_clipboard_empty), snackbarText(fragment.requireActivity()))
            assertEquals("", binding.numbersInput.text.toString())
        }
    }

    // --- Results ---

    @Test
    fun `one number is shown formatted with the open button`() {
        installWhatsApp()
        launch().withBinding { binding, _ ->
            binding.numbersInput.setText("Call me at 415 555 2671, thanks")
            idleForResults()
            assertEquals("1 number found", binding.resultsCount.text.toString())
            assertTrue(binding.singleNumber.isShown)
            assertTrue(binding.singleNumber.text.contains("415-555-2671"))
            assertFalse(binding.numbersChipGroup.isShown)
            assertTrue(binding.processNumbersButton.isShown)
            binding.processNumbersButton.performClick()
        }
        assertEquals("14155552671", nextChatUri()?.getQueryParameter("phone"))
    }

    @Test
    fun `several numbers become chips, and a chip opens its own chat`() {
        installWhatsApp()
        launch().withBinding { binding, _ ->
            binding.numbersInput.setText("+1 415-555-2671\n+44 7911 123456\n+91 98765 43210")
            idleForResults()
            assertEquals("3 numbers found", binding.resultsCount.text.toString())
            assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, binding.resultsCount.accessibilityLiveRegion)
            assertFalse(binding.singleNumber.isShown)
            assertFalse(binding.processNumbersButton.isShown)
            assertEquals(3, binding.numbersChipGroup.childCount)
            val chip = binding.numbersChipGroup.getChildAt(1) as Chip
            assertTrue(chip.text.contains("7911 123456"))
            chip.performClick()
        }
        assertEquals("447911123456", nextChatUri()?.getQueryParameter("phone"))
    }

    @Test
    fun `text without a number says so, and the button shows it as an error`() {
        launch().withBinding { binding, _ ->
            binding.numbersInput.setText("hello world")
            idleForResults()
            assertEquals("No phone number found", binding.resultsCount.text.toString())
            assertFalse(binding.singleNumber.isShown)
            assertFalse(binding.numbersChipGroup.isShown)
            binding.processNumbersButton.performClick()
            assertEquals("No phone number found", binding.numbersInputLayout.error)
            assertFalse(binding.resultsCount.isShown)

            binding.numbersInput.setText("+14155552671")
            assertNull(binding.numbersInputLayout.error)
            idleForResults()
            assertTrue(binding.singleNumber.isShown)
        }
    }

    @Test
    fun `results wait for a pause in typing`() {
        launch().withBinding { binding, _ ->
            binding.numbersInput.setText("+14155552671")
            assertFalse(binding.singleNumber.isShown)
            idleForResults()
            assertTrue(binding.singleNumber.isShown)
        }
    }

    @Test
    fun `empty box shows no results`() {
        launch().withBinding { binding, _ ->
            assertFalse(binding.resultsCount.isShown)
            binding.processNumbersButton.performClick()
            assertNotNull(binding.numbersInputLayout.error)
        }
    }

    @Test
    fun `results come back after recreation`() {
        val scenario = launch()
        scenario.withBinding { binding, _ -> binding.numbersInput.setText("+14155552671, +447911123456") }
        scenario.recreate()
        scenario.withBinding { binding, _ ->
            assertEquals(2, binding.numbersChipGroup.childCount)
        }
    }

    @Test
    fun `text box is limited to the searched length`() {
        launch().withBinding { binding, _ ->
            val limit = binding.numbersInput.filters.filterIsInstance<InputFilter.LengthFilter>().single().max
            assertEquals(PhoneNumbers.MAX_TEXT_CHARS, limit)
        }
    }

    @Test
    fun `WhatsApp not installed shows a snackbar, not a toast`() {
        launch().withBinding { binding, fragment ->
            binding.numbersInput.setText("+14155552671")
            binding.processNumbersButton.performClick()
            assertEquals(app.getString(R.string.error_whatsapp_not_installed), snackbarText(fragment.requireActivity()))
        }
        assertNull(ShadowToast.getTextOfLatestToast())
    }

    // --- Shared text ---

    @Test
    fun `shared text fills the box and is consumed`() {
        PendingShare.text = "+14155551234"
        launch().withBinding { binding, _ ->
            assertEquals("+14155551234", binding.numbersInput.text.toString())
            assertTrue(binding.singleNumber.isShown)
        }
        assertNull(PendingShare.text)
        assertEquals(0, reads)
    }

    @Test
    fun `shared text replaces what's in the box, once`() {
        val scenario = launch()
        scenario.withBinding { binding, _ -> binding.numbersInput.setText("old text") }

        PendingShare.text = "+14155551234"
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.withBinding { binding, _ ->
            assertEquals("+14155551234", binding.numbersInput.text.toString())
            binding.numbersInput.setText("edited")
        }

        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.withBinding { binding, _ -> assertEquals("edited", binding.numbersInput.text.toString()) }
    }

    // --- Accessibility ---

    @Test
    fun `the tab names itself to accessibility services, and its title is a heading`() {
        launch().withBinding { binding, _ ->
            assertEquals(app.getString(R.string.nav_clipboard), ViewCompat.getAccessibilityPaneTitle(binding.root))
            assertTrue(ViewCompat.isAccessibilityHeading(binding.title))
        }
    }

    @Test
    fun `the whole Paste card is one button, named Paste for screen readers`() {
        launch().withBinding { binding, _ ->
            val card = binding.clipboardSuggestion
            assertTrue(card.isClickable && card.isFocusable)
            val click = card.createAccessibilityNodeInfo().actionList.single { it.id == AccessibilityNodeInfo.ACTION_CLICK }
            assertEquals(app.getString(R.string.paste), click.label)
        }
    }
}
