package com.harshalbhatia.whatsopen

import android.Manifest
import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.view.View
import android.view.View.MeasureSpec
import android.widget.Button
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertArrayEquals
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
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class CallLogsFragmentTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var provider: FakeCallLogProvider

    private val lookupUri = "content://com.android.contacts/contacts/lookup/0r1-ABC/1"

    @Before
    fun setUp() {
        WhatsAppLauncher.resetForTesting()
        resetSnackbars()
        CallLogsViewModel.ioDispatcher = Dispatchers.Unconfined
        shadowOf(app.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).setNetworkCountryIso("us")
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_TELEPHONY, true)
        provider = Robolectric.setupContentProvider(FakeCallLogProvider::class.java, CallLog.AUTHORITY)
    }

    @After
    fun tearDown() {
        CallLogsViewModel.ioDispatcher = Dispatchers.IO
        WhatsAppLauncher.resetForTesting()
    }

    private fun launch(): FragmentScenario<CallLogsFragment> =
        launchFragmentInContainer<CallLogsFragment>(themeResId = R.style.Theme_WhatsOpen).also {
            it.moveToState(Lifecycle.State.RESUMED)
        }

    /** Runs [block] with the fragment and the list's item views, laid out tall enough to show them all. */
    private fun FragmentScenario<CallLogsFragment>.withItems(block: (CallLogsFragment, List<View>) -> Unit) {
        onFragment { fragment ->
            ShadowLooper.idleMainLooper()
            val list = fragment.requireView().findViewById<RecyclerView>(R.id.recycler_view)
            list.itemAnimator = null
            list.measure(MeasureSpec.makeMeasureSpec(1080, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(20_000, MeasureSpec.EXACTLY))
            list.layout(0, 0, 1080, 20_000)
            block(fragment, list.children.toList())
        }
    }

    private fun List<View>.rows() = filter { it.id == R.id.call_log_row }

    private fun List<View>.rowTexts() = rows().map { it.findViewById<TextView>(R.id.primary_text).text.toString() }

    private fun List<View>.messageTitle(): String? =
        firstNotNullOfOrNull { it.findViewById<TextView>(R.id.message_title) }?.takeIf { it.isShown }?.text?.toString()

    private fun List<View>.messageButton(): Button = firstNotNullOf { it.findViewById<Button>(R.id.message_button) }

    private fun List<View>.chip(id: Int): Chip = firstNotNullOf { it.findViewById<Chip>(id) }

    private fun List<View>.row(text: String): View =
        rows().single { it.findViewById<TextView>(R.id.primary_text).text.toString() == text }

    private fun grant() = shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG)

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

    /** Loads the call log with one contact (Mom) and one other number. */
    private fun launchWithCalls(): FragmentScenario<CallLogsFragment> {
        grant()
        provider.rows = listOf(
            callRow("+447911123456", date = 20, name = "Mom", lookupUri = lookupUri),
            callRow("(415) 555-2671", date = 10, type = CallLog.Calls.MISSED_TYPE),
        )
        return launch()
    }

    /** Opens the ⋮ menu of the row showing [text]. */
    private fun FragmentScenario<CallLogsFragment>.openMenu(text: String): androidx.appcompat.widget.PopupMenu {
        var menu: androidx.appcompat.widget.PopupMenu? = null
        withItems { fragment, items ->
            items.row(text).findViewById<View>(R.id.more_button).performClick()
            menu = fragment.rowMenu
        }
        return menu!!
    }

    private fun nextStarted(): Intent? = shadowOf(app).nextStartedActivity

    // Permission

    @Test
    fun `first open explains why and doesn't ask for the permission`() {
        val scenario = launch()
        scenario.withItems { fragment, items ->
            assertEquals(app.getString(R.string.call_logs_permission_title), items.messageTitle())
            assertEquals("Allow access", items.messageButton().text.toString())
            assertNull(shadowOf(fragment.requireActivity()).lastRequestedPermission)
            assertEquals(0, provider.queries)
        }
    }

    @Test
    fun `Allow access asks for the call log permission only`() {
        launch().withItems { fragment, items ->
            items.messageButton().performClick()
            val request = shadowOf(fragment.requireActivity()).lastRequestedPermission
            assertArrayEquals(arrayOf(Manifest.permission.READ_CALL_LOG), request.requestedPermissions)
        }
    }

    @Test
    fun `granting the permission loads the calls`() {
        provider.rows = listOf(callRow("+447911123456", name = "Mom"))
        val scenario = launch()
        scenario.withItems { fragment, items ->
            items.messageButton().performClick()
            val request = shadowOf(fragment.requireActivity()).lastRequestedPermission
            grant()
            fragment.requireActivity().onRequestPermissionsResult(
                request.requestCode, request.requestedPermissions, intArrayOf(PackageManager.PERMISSION_GRANTED)
            )
        }
        scenario.withItems { _, items -> assertEquals(listOf("Mom"), items.rowTexts()) }
    }

    @Test
    fun `denied with a rationale still offers Allow access`() {
        val scenario = launch()
        scenario.withItems { fragment, items ->
            items.messageButton().performClick()
            val request = shadowOf(fragment.requireActivity()).lastRequestedPermission
            shadowOf(app.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.READ_CALL_LOG, true)
            fragment.requireActivity().onRequestPermissionsResult(
                request.requestCode, request.requestedPermissions, intArrayOf(PackageManager.PERMISSION_DENIED)
            )
        }
        scenario.withItems { _, items -> assertEquals("Allow access", items.messageButton().text.toString()) }
    }

    @Test
    fun `permanently denied opens the app's settings`() {
        val scenario = launch()
        scenario.withItems { fragment, items ->
            items.messageButton().performClick()
            val request = shadowOf(fragment.requireActivity()).lastRequestedPermission
            // No rationale after a denial: "don't ask again", or denied twice on Android 11+.
            fragment.requireActivity().onRequestPermissionsResult(
                request.requestCode, request.requestedPermissions, intArrayOf(PackageManager.PERMISSION_DENIED)
            )
        }
        scenario.withItems { _, items ->
            assertEquals(app.getString(R.string.call_logs_permission_blocked_title), items.messageTitle())
            items.messageButton().performClick()
        }
        val intent = nextStarted()!!
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${app.packageName}", intent.dataString)
    }

    @Test
    fun `without telephony says so and never asks`() {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_TELEPHONY, false)
        launch().withItems { fragment, items ->
            assertEquals(app.getString(R.string.call_logs_no_telephony_title), items.messageTitle())
            assertFalse(items.messageButton().isShown)
            assertNull(shadowOf(fragment.requireActivity()).lastRequestedPermission)
        }
    }

    @Test
    fun `resuming re-checks the permission`() {
        provider.rows = listOf(callRow("+447911123456", name = "Mom"))
        val scenario = launch()
        scenario.withItems { _, items -> assertTrue(items.rows().isEmpty()) }

        // Granted in Settings while the app was in the background.
        scenario.moveToState(Lifecycle.State.STARTED)
        grant()
        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.withItems { _, items -> assertEquals(listOf("Mom"), items.rowTexts()) }

        scenario.moveToState(Lifecycle.State.STARTED)
        shadowOf(app).denyPermissions(Manifest.permission.READ_CALL_LOG)
        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.withItems { _, items ->
            assertTrue(items.rows().isEmpty())
            assertEquals(app.getString(R.string.call_logs_permission_title), items.messageTitle())
        }
    }

    @Test
    fun `manifest asks for the call log but not contacts`() {
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
        val permissions = info.requestedPermissions.orEmpty().toList()
        assertTrue(Manifest.permission.READ_CALL_LOG in permissions)
        assertFalse(Manifest.permission.READ_CONTACTS in permissions)
    }

    // Calls and filters

    @Test
    fun `shows the calls, with a count`() {
        launchWithCalls().withItems { _, items ->
            assertEquals(listOf("Mom", "+1 415-555-2671"), items.rowTexts())
            assertEquals("2 calls", items.firstNotNullOf { it.findViewById<TextView>(R.id.call_count) }.text.toString())
        }
    }

    @Test
    fun `Non-contacts chip works without the contacts permission`() {
        val scenario = launchWithCalls()
        scenario.withItems { _, items ->
            val chip = items.chip(R.id.chip_non_contacts_only)
            assertTrue(chip.isEnabled)
            chip.performClick()
        }
        scenario.withItems { _, items ->
            assertEquals(listOf("+1 415-555-2671"), items.rowTexts())
            assertEquals("1 call", items.firstNotNullOf { it.findViewById<TextView>(R.id.call_count) }.text.toString())
        }
    }

    @Test
    fun `Missed chip includes rejected and blocked calls`() {
        grant()
        provider.rows = listOf(
            callRow("+447911123450", date = 50, type = CallLog.Calls.INCOMING_TYPE),
            callRow("+447911123451", date = 40, type = CallLog.Calls.MISSED_TYPE),
            callRow("+447911123452", date = 30, type = CallLog.Calls.REJECTED_TYPE),
            callRow("+447911123453", date = 20, type = CallLog.Calls.BLOCKED_TYPE),
            callRow("+447911123454", date = 10, type = CallLog.Calls.OUTGOING_TYPE),
        )
        val scenario = launch()
        scenario.withItems { _, items -> items.chip(R.id.chip_missed).performClick() }
        scenario.withItems { _, items ->
            assertEquals(listOf("+44 7911 123451", "+44 7911 123452", "+44 7911 123453"), items.rowTexts().map { it.filter { c -> c.code < 0x2000 } })
        }
    }

    @Test
    fun `no matches offers Clear filters`() {
        grant()
        provider.rows = listOf(callRow("+447911123456", name = "Mom"))
        val scenario = launch()
        scenario.withItems { _, items -> items.chip(R.id.chip_non_contacts_only).performClick() }
        scenario.withItems { _, items ->
            assertEquals(app.getString(R.string.empty_filtered_call_logs_title), items.messageTitle())
            assertTrue(items.chip(R.id.chip_non_contacts_only).isChecked)
            items.messageButton().performClick()
        }
        scenario.withItems { _, items ->
            assertEquals(listOf("Mom"), items.rowTexts())
            assertFalse(items.chip(R.id.chip_non_contacts_only).isChecked)
        }
    }

    @Test
    fun `an empty call log says so, without filters`() {
        grant()
        launch().withItems { _, items ->
            assertEquals(app.getString(R.string.empty_call_logs_title), items.messageTitle())
            assertFalse(items.chip(R.id.chip_missed).isShown)
        }
    }

    @Test
    fun `an error offers Try again`() {
        grant()
        provider.error = IllegalStateException("Provider died")
        val scenario = launch()
        scenario.withItems { _, items ->
            assertEquals(app.getString(R.string.call_logs_error_title), items.messageTitle())
            provider.error = null
            provider.rows = listOf(callRow("+447911123456", name = "Mom"))
            items.messageButton().performClick()
        }
        scenario.withItems { _, items -> assertEquals(listOf("Mom"), items.rowTexts()) }
    }

    @Test
    fun `refused despite the permission sends the user to settings instead of crashing`() {
        grant()
        provider.error = SecurityException("Permission denial")
        launch().withItems { _, items ->
            assertEquals(app.getString(R.string.call_logs_permission_blocked_title), items.messageTitle())
        }
    }

    // Row actions

    @Test
    fun `tapping a row opens the WhatsApp chat`() {
        installWhatsApp()
        launchWithCalls().withItems { _, items -> items.row("Mom").performClick() }
        val intent = nextStarted()!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(WhatsAppApp.CONSUMER.packageName, intent.`package`)
        assertEquals("447911123456", intent.data?.getQueryParameter("phone"))
    }

    @Test
    fun `tapping a row without WhatsApp shows a snackbar`() {
        launchWithCalls().withItems { fragment, items ->
            items.row("Mom").performClick()
            assertEquals(app.getString(R.string.error_whatsapp_not_installed), snackbarText(fragment.requireActivity()))
        }
    }

    @Test
    fun `menu of a contact offers View contact, not Save`() {
        val menu = launchWithCalls().openMenu("Mom").menu
        assertTrue(menu.findItem(R.id.action_call).isVisible)
        assertTrue(menu.findItem(R.id.action_copy_number).isVisible)
        assertTrue(menu.findItem(R.id.action_view_contact).isVisible)
        assertFalse(menu.findItem(R.id.action_save_contact).isVisible)
    }

    @Test
    fun `menu of another number offers Save to contacts, not View`() {
        val menu = launchWithCalls().openMenu("+1 415-555-2671").menu
        assertTrue(menu.findItem(R.id.action_call).isVisible)
        assertTrue(menu.findItem(R.id.action_copy_number).isVisible)
        assertTrue(menu.findItem(R.id.action_save_contact).isVisible)
        assertFalse(menu.findItem(R.id.action_view_contact).isVisible)
    }

    @Test
    fun `Call opens the dialer with the number`() {
        launchWithCalls().openMenu("Mom").menu.performIdentifierAction(R.id.action_call, 0)
        val intent = nextStarted()!!
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel", intent.data?.scheme)
        assertEquals("+447911123456", intent.data?.schemeSpecificPart)
    }

    @Test
    fun `Call asks the default phone app, not this app's own tel handler`() {
        shadowOf(app.getSystemService(TelecomManager::class.java)).setDefaultDialerPackage("com.example.dialer")
        launchWithCalls().openMenu("Mom").menu.performIdentifierAction(R.id.action_call, 0)
        val intent = nextStarted()!!
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("com.example.dialer", intent.`package`)
    }

    @Test
    fun `Save to contacts opens the contacts app with the number`() {
        launchWithCalls().openMenu("+1 415-555-2671").menu.performIdentifierAction(R.id.action_save_contact, 0)
        val intent = nextStarted()!!
        assertEquals(Intent.ACTION_INSERT_OR_EDIT, intent.action)
        assertEquals(ContactsContract.Contacts.CONTENT_ITEM_TYPE, intent.type)
        assertEquals("+14155552671", intent.getStringExtra(ContactsContract.Intents.Insert.PHONE))
    }

    @Test
    fun `View contact opens the cached lookup URI`() {
        launchWithCalls().openMenu("Mom").menu.performIdentifierAction(R.id.action_view_contact, 0)
        val intent = nextStarted()!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(lookupUri, intent.dataString)
    }

    @Test
    fun `View contact doesn't open a lookup URI from outside the contacts provider`() {
        grant()
        provider.rows = listOf(callRow("+447911123456", name = "Mom", lookupUri = "https://example.com/"))
        launch().openMenu("Mom").menu.performIdentifierAction(R.id.action_view_contact, 0)
        val intent = nextStarted()!!
        assertEquals(ContactsContract.Intents.SHOW_OR_CREATE_CONTACT, intent.action)
        assertEquals("+447911123456", intent.data?.schemeSpecificPart)
    }

    @Test
    fun `Copy number puts the number on the clipboard`() {
        launchWithCalls().openMenu("Mom").menu.performIdentifierAction(R.id.action_copy_number, 0)
        val clip = (app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        assertEquals("+447911123456", clip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun `an action no app can handle shows a snackbar`() {
        val scenario = launchWithCalls()
        val menu = scenario.openMenu("Mom")
        scenario.onFragment { fragment ->
            shadowOf(app).checkActivities(true)
            menu.menu.performIdentifierAction(R.id.action_view_contact, 0)
            assertEquals(app.getString(R.string.error_no_app_for_action), snackbarText(fragment.requireActivity()))
        }
    }

    @Test
    fun `leaving the tab closes the menu`() {
        val scenario = launchWithCalls()
        assertNotNull(scenario.openMenu("Mom"))
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.onFragment { assertNull(it.rowMenu) }
    }

    // Layout and accessibility

    /** The list as the fragment laid it out in the window, without [withItems]'s fixed size. */
    private fun FragmentScenario<CallLogsFragment>.laidOutList(): RecyclerView {
        lateinit var list: RecyclerView
        onFragment { fragment ->
            ShadowLooper.idleMainLooper()
            list = fragment.requireView().findViewById(R.id.recycler_view)
        }
        return list
    }

    @Test
    @Config(qualifiers = "w900dp-h600dp")
    fun `on a wide window the list spans it and centres the rows`() {
        val list = launchWithCalls().laidOutList()
        assertEquals(app.resources.displayMetrics.widthPixels, list.width)
        // (900dp - content_max_width 600dp) / 2, at mdpi.
        assertEquals(150, list.paddingLeft)
        assertEquals(150, list.paddingRight)
    }

    @Test
    fun `on a phone the rows use the whole width`() {
        val list = launchWithCalls().laidOutList()
        assertEquals(app.resources.displayMetrics.widthPixels, list.width)
        assertEquals(0, list.paddingLeft)
        assertEquals(0, list.paddingRight)
    }

    @Test
    fun `the tab names itself to accessibility services, and its title is a heading`() {
        launchWithCalls().withItems { fragment, items ->
            assertEquals(app.getString(R.string.nav_call_logs), ViewCompat.getAccessibilityPaneTitle(fragment.requireView()))
            assertTrue(ViewCompat.isAccessibilityHeading(items.firstNotNullOf { it.findViewById<TextView>(R.id.title) }))
        }
    }
}
