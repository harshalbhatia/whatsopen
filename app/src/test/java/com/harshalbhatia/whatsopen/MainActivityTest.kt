package com.harshalbhatia.whatsopen

import android.content.Intent
import android.os.BadParcelableException
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    @After
    fun tearDown() {
        PendingShare.text = null
    }

    private fun build(intent: Intent): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java, intent).setup()

    private fun launchWithIntent(intent: Intent): MainActivity = build(intent).get()

    private fun selectedTab(activity: MainActivity): Int =
        activity.findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId

    private fun currentDestination(activity: MainActivity): Int? =
        (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment)
            .navController.currentDestination?.id

    private fun openTab(tab: String) = Intent().putExtra(MainActivity.EXTRA_OPEN_TAB, tab)

    @Test
    fun `default launch selects by-number tab`() {
        assertEquals(R.id.navigation_by_number, selectedTab(launchWithIntent(Intent())))
    }

    @Test
    fun `open_tab clipboard extra selects clipboard tab`() {
        assertEquals(R.id.navigation_clipboard, selectedTab(launchWithIntent(openTab(MainActivity.TAB_CLIPBOARD))))
    }

    @Test
    fun `open_tab call_logs extra selects call logs tab`() {
        assertEquals(R.id.navigation_call_logs, selectedTab(launchWithIntent(openTab(MainActivity.TAB_CALL_LOGS))))
    }

    @Test
    fun `constants match the literals in shortcuts xml`() {
        assertEquals("open_tab", MainActivity.EXTRA_OPEN_TAB)
        assertEquals("clipboard", MainActivity.TAB_CLIPBOARD)
        assertEquals("call_logs", MainActivity.TAB_CALL_LOGS)
    }

    @Test
    fun `onNewIntent with open_tab switches tab and becomes the intent`() {
        val controller = build(Intent())
        val newIntent = openTab(MainActivity.TAB_CLIPBOARD)
        controller.newIntent(newIntent)
        assertEquals(R.id.navigation_clipboard, selectedTab(controller.get()))
        assertSame(newIntent, controller.get().intent)

        controller.newIntent(openTab(MainActivity.TAB_CALL_LOGS))
        assertEquals(R.id.navigation_call_logs, selectedTab(controller.get()))
    }

    @Test
    fun `recreating does not handle the intent again`() {
        val controller = build(openTab(MainActivity.TAB_CALL_LOGS))
        controller.get().findViewById<BottomNavigationView>(R.id.bottom_navigation)
            .selectedItemId = R.id.navigation_by_number

        controller.recreate()
        ShadowLooper.idleMainLooper()

        // The bottom bar restores its own selection, so check where navigation actually is.
        assertEquals(R.id.navigation_by_number, currentDestination(controller.get()))
        assertEquals(R.id.navigation_by_number, selectedTab(controller.get()))
    }

    @Test
    fun `launch from Recents does not replay open_tab`() {
        val intent = openTab(MainActivity.TAB_CALL_LOGS).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertEquals(R.id.navigation_by_number, selectedTab(launchWithIntent(intent)))
    }

    @Test
    fun `extras that fail to unparcel are ignored`() {
        val intent = object : Intent() {
            override fun getStringExtra(name: String?): String = throw BadParcelableException("bad")
        }
        assertEquals(R.id.navigation_by_number, selectedTab(launchWithIntent(intent)))
    }

    @Test
    fun `share actions sent straight to MainActivity are ignored`() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "hello world")
        assertEquals(R.id.navigation_by_number, selectedTab(launchWithIntent(intent)))
        assertNull(PendingShare.text)
    }

    @Test
    fun `clipboardTabIntent reuses the open MainActivity and looks like a launcher intent`() {
        val intent = MainActivity.clipboardTabIntent(ApplicationProvider.getApplicationContext())
        assertEquals(MainActivity::class.java.name, intent.component!!.className)
        assertEquals(MainActivity.TAB_CLIPBOARD, intent.getStringExtra(MainActivity.EXTRA_OPEN_TAB))
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        assertEquals(flags, intent.flags and flags)
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_LAUNCHER))
    }

    @Test
    fun `clipboardTabIntent delivered to the open activity selects the clipboard tab`() {
        val controller = build(Intent())
        controller.newIntent(MainActivity.clipboardTabIntent(controller.get()))
        assertEquals(R.id.navigation_clipboard, selectedTab(controller.get()))
    }
}
