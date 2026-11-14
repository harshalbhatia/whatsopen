package com.harshalbhatia.whatsopen

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.Uri
import android.telephony.TelephonyManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import androidx.core.view.ViewCompat
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ApplicationProvider
import com.harshalbhatia.whatsopen.databinding.FragmentFirstBinding
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class FirstFragmentTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() {
        WhatsAppLauncher.resetForTesting()
        resetSnackbars()
        WhatsAppLauncher.clearRememberedApp(app)
        setRegion("in")
    }

    @After
    fun tearDown() {
        WhatsAppLauncher.resetForTesting()
        WhatsAppLauncher.clearRememberedApp(app)
    }

    private fun setRegion(iso: String) {
        val telephony = shadowOf(app.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
        telephony.setNetworkCountryIso(iso)
        telephony.setSimCountryIso("")
    }

    private fun launch(): FragmentScenario<FirstFragment> =
        launchFragmentInContainer<FirstFragment>(themeResId = R.style.Theme_WhatsOpen)
            .also { it.moveToState(Lifecycle.State.RESUMED) }

    private fun FragmentScenario<FirstFragment>.withBinding(block: (FragmentFirstBinding, FirstFragment) -> Unit) =
        onFragment { block(FragmentFirstBinding.bind(it.requireView()), it) }

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

    // --- Country code ---

    @Test
    fun `country code input layout shows plus prefix`() {
        launch().withBinding { binding, _ ->
            assertEquals("+", binding.countryCodeInputLayout.prefixText.toString())
        }
    }

    @Test
    fun `country code defaults to the device's region`() {
        setRegion("gb")
        launch().withBinding { binding, _ ->
            assertEquals("44", binding.countryCodeInput.text.toString())
            assertTrue(binding.countryInfo.text.contains("United Kingdom"))
        }
    }

    @Test
    fun `country code is empty when the region is unknown`() {
        setRegion("")
        val locale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            launch().withBinding { binding, _ ->
                assertEquals("", binding.countryCodeInput.text.toString())
                assertEquals("", binding.countryInfo.text.toString())
            }
        } finally {
            Locale.setDefault(locale)
        }
    }

    @Test
    fun `typed and cleared codes survive recreation instead of the default`() {
        val scenario = launch()
        scenario.withBinding { binding, _ -> binding.countryCodeInput.setText("44") }
        scenario.recreate()
        scenario.withBinding { binding, _ ->
            assertEquals("44", binding.countryCodeInput.text.toString())
            assertTrue(binding.countryInfo.text.contains("United Kingdom"))
            binding.countryCodeInput.setText("")
        }
        scenario.recreate()
        scenario.withBinding { binding, _ -> assertEquals("", binding.countryCodeInput.text.toString()) }
    }

    @Test
    fun `typing a known country code shows its country and describes it`() {
        launch().withBinding { binding, _ ->
            binding.countryCodeInput.setText("91")
            assertTrue(binding.countryInfo.text.contains("India"))
            assertEquals("Country: India", binding.countryInfo.contentDescription)
            assertEquals(app.getString(R.string.country_code_hint), binding.countryCodeInputLayout.hint)
        }
    }

    @Test
    fun `an unassigned code says so, but not while more digits could still make it valid`() {
        launch().withBinding { binding, _ ->
            binding.countryCodeInput.setText("999")
            assertEquals("Unknown code", binding.countryInfo.text.toString())
            binding.countryCodeInput.setText("9")
            assertEquals("", binding.countryInfo.text.toString())
            binding.countryCodeInput.setText("")
            assertEquals("", binding.countryInfo.text.toString())
            assertNull(binding.countryInfo.contentDescription)
        }
    }

    // --- Validation ---

    @Test
    fun `submit with empty country code shows error`() {
        launch().withBinding { binding, _ ->
            binding.countryCodeInput.setText("")
            binding.phoneInput.setText("9876543210")
            binding.openWhatsappButton.performClick()
            assertNotNull(binding.countryCodeInputLayout.error)
        }
    }

    @Test
    fun `submit with empty phone shows error`() {
        launch().withBinding { binding, _ ->
            binding.openWhatsappButton.performClick()
            assertNotNull(binding.phoneInputLayout.error)
            assertNull(binding.countryCodeInputLayout.error)
        }
    }

    @Test
    fun `editing country code clears previous error`() {
        launch().withBinding { binding, _ ->
            binding.countryCodeInput.setText("")
            binding.openWhatsappButton.performClick()
            assertNotNull(binding.countryCodeInputLayout.error)
            binding.countryCodeInput.setText("1")
            assertNull(binding.countryCodeInputLayout.error)
        }
    }

    @Test
    fun `submit with an unassigned code puts the error on the code`() {
        launch().withBinding { binding, _ ->
            binding.countryCodeInput.setText("999")
            binding.phoneInput.setText("123456789")
            binding.openWhatsappButton.performClick()
            assertEquals("Unknown code", binding.countryCodeInputLayout.error)
        }
    }

    // --- Numbers ---

    @Test
    fun `national number with a trunk prefix opens the right chat`() {
        installWhatsApp()
        launch().withBinding { binding, _ ->
            binding.countryCodeInput.setText("44")
            binding.phoneInput.setText("07911 123456")
            binding.openWhatsappButton.performClick()
        }
        assertEquals("447911123456", nextChatUri()?.getQueryParameter("phone"))
    }

    @Test
    fun `shared code shows the user's own country`() {
        setRegion("ca")
        launch().withBinding { binding, _ ->
            assertEquals("1", binding.countryCodeInput.text.toString())
            assertTrue(binding.countryInfo.text.contains("Canada"))
        }
    }

    @Test
    fun `pasting an international number splits it over both fields`() {
        launch().withBinding { binding, _ ->
            binding.phoneInput.setText("+44 7911 123456")
            assertEquals("44", binding.countryCodeInput.text.toString())
            assertEquals("7911123456", binding.phoneInput.text.toString())
            assertEquals(binding.phoneInput.length(), binding.phoneInput.selectionEnd)
        }
    }

    @Test
    fun `pasting a 00 number splits it too`() {
        launch().withBinding { binding, _ ->
            binding.phoneInput.setText("0044 7911 123456")
            assertEquals("44", binding.countryCodeInput.text.toString())
            assertEquals("7911123456", binding.phoneInput.text.toString())
        }
    }

    @Test
    fun `typing an international number one key at a time leaves it alone`() {
        launch().withBinding { binding, _ ->
            "+447911123456".forEach { binding.phoneInput.text!!.append(it) }
            assertEquals("91", binding.countryCodeInput.text.toString())
            assertEquals("+447911123456", binding.phoneInput.text.toString())
        }
    }

    @Test
    fun `submitting a typed international number splits it and opens it`() {
        installWhatsApp()
        launch().withBinding { binding, _ ->
            "+447911123456".forEach { binding.phoneInput.text!!.append(it) }
            binding.openWhatsappButton.performClick()
            assertEquals("44", binding.countryCodeInput.text.toString())
            assertEquals("7911123456", binding.phoneInput.text.toString())
        }
        assertEquals("447911123456", nextChatUri()?.getQueryParameter("phone"))
    }

    @Test
    fun `submitting an invalid international number shows an error`() {
        launch().withBinding { binding, _ ->
            "+12".forEach { binding.phoneInput.text!!.append(it) }
            binding.openWhatsappButton.performClick()
            assertEquals(app.getString(R.string.error_invalid_phone_number), binding.phoneInputLayout.error)
        }
    }

    // --- Message ---

    @Test
    fun `message field is hidden until asked for, and its text goes in the chat link`() {
        installWhatsApp()
        launch().withBinding { binding, _ ->
            assertFalse(binding.messageInputLayout.isShown)
            binding.addMessageButton.performClick()
            assertTrue(binding.messageInputLayout.isShown)
            assertFalse(binding.addMessageButton.isShown)
            binding.phoneInput.setText("9876543210")
            binding.messageInput.setText("Hi & welcome")
            binding.openWhatsappButton.performClick()
        }
        val uri = nextChatUri()
        assertEquals("919876543210", uri?.getQueryParameter("phone"))
        assertEquals("Hi & welcome", uri?.getQueryParameter("text"))
    }

    @Test
    fun `message field stays open after recreation`() {
        val scenario = launch()
        scenario.withBinding { binding, _ ->
            binding.addMessageButton.performClick()
            binding.messageInput.setText("Hello")
        }
        scenario.recreate()
        scenario.withBinding { binding, _ ->
            assertTrue(binding.messageInputLayout.isShown)
            assertEquals("Hello", binding.messageInput.text.toString())
        }
    }

    @Test
    fun `blank message is left out of the chat link`() {
        installWhatsApp()
        launch().withBinding { binding, _ ->
            binding.addMessageButton.performClick()
            binding.phoneInput.setText("9876543210")
            binding.messageInput.setText("   ")
            binding.openWhatsappButton.performClick()
        }
        assertNull(nextChatUri()?.getQueryParameter("text"))
    }

    // --- Keyboard ---

    @Test
    fun `hardware Enter submits once`() {
        installWhatsApp()
        launch().withBinding { binding, _ ->
            binding.phoneInput.setText("9876543210")
            assertTrue(binding.phoneInput.onKeyDown(KeyEvent.KEYCODE_ENTER, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)))
            assertTrue(binding.phoneInput.onKeyUp(KeyEvent.KEYCODE_ENTER, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)))
        }
        assertEquals("919876543210", nextChatUri()?.getQueryParameter("phone"))
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun `keyboard Go action submits`() {
        launch().withBinding { binding, _ ->
            binding.phoneInput.onEditorAction(EditorInfo.IME_ACTION_GO)
            assertNotNull(binding.phoneInputLayout.error)
        }
    }

    // --- Long press and snackbars ---

    @Test
    fun `long press resets the remembered app and says so`() {
        app.getSharedPreferences("whatsapp_launcher", Context.MODE_PRIVATE).edit()
            .putString("remembered_app", WhatsAppApp.BUSINESS.packageName).commit()
        assertEquals(WhatsAppApp.BUSINESS, WhatsAppLauncher.rememberedApp(app))
        launch().withBinding { binding, fragment ->
            assertTrue(binding.openWhatsappButton.performLongClick())
            assertNull(WhatsAppLauncher.rememberedApp(app))
            assertEquals("WhatsApp app choice reset", snackbarText(fragment.requireActivity()))
        }
    }

    @Test
    fun `long press with nothing remembered says there was nothing to reset`() {
        launch().withBinding { binding, fragment ->
            binding.openWhatsappButton.performLongClick()
            assertEquals("No app choice to reset", snackbarText(fragment.requireActivity()))
        }
    }

    @Test
    fun `long press has a label for screen readers`() {
        launch().withBinding { binding, _ ->
            val info = binding.openWhatsappButton.createAccessibilityNodeInfo()
            val action = info.actionList.single { it.id == AccessibilityNodeInfo.ACTION_LONG_CLICK }
            assertEquals("Reset WhatsApp app choice", action.label)
        }
    }

    @Test
    fun `WhatsApp not installed shows a snackbar, not a toast`() {
        launch().withBinding { binding, fragment ->
            binding.phoneInput.setText("9876543210")
            binding.openWhatsappButton.performClick()
            assertEquals(app.getString(R.string.error_whatsapp_not_installed), snackbarText(fragment.requireActivity()))
        }
        assertNull(ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `snackbars sit above the bottom navigation`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java, Intent()).setup().get()
        val navHost = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val fragment = navHost.childFragmentManager.fragments.filterIsInstance<FirstFragment>().single()
        val snackbar = fragment.showSnackbar(R.string.app_choice_reset)
        assertEquals(R.id.bottom_navigation, snackbar?.anchorView?.id)
    }

    @Test
    fun `chatNumber reads the number in the code's country first`() {
        assertEquals("+447911123456", FirstFragment.chatNumber("44", "07911 123456", "GB"))
        assertEquals("+919876543210", FirstFragment.chatNumber("91", "98765 43210", "IN"))
        // Not valid for the platform, but has a "+" and an assigned code: accepted as written.
        assertEquals("+15555550100", FirstFragment.chatNumber("1", "555 555 0100", "US"))
        assertNull(FirstFragment.chatNumber("999", "123456789", null))
        // A repeated trunk "1" is dropped, never kept as a 12-digit +1 number.
        assertEquals("+14165550123", FirstFragment.chatNumber("1", "1 416 555 0123", "US"))
        assertNull(FirstFragment.chatNumber("1", "1 555 123 4567", "US"))
    }

    // --- Accessibility ---

    @Test
    fun `the tab names itself to accessibility services, and its title is a heading`() {
        launch().withBinding { binding, _ ->
            assertEquals(app.getString(R.string.nav_by_number), ViewCompat.getAccessibilityPaneTitle(binding.root))
            assertTrue(ViewCompat.isAccessibilityHeading(binding.title))
            assertFalse(ViewCompat.isAccessibilityHeading(binding.subtitle))
        }
    }
}
