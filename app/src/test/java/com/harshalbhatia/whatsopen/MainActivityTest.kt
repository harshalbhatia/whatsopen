package com.harshalbhatia.whatsopen

import android.content.Intent
import android.net.Uri
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    @After
    fun tearDown() {
        PendingShare.text = null
    }

    private fun launchWithIntent(intent: Intent): MainActivity {
        return Robolectric.buildActivity(MainActivity::class.java, intent)
            .create()
            .start()
            .resume()
            .get()
    }

    @Test
    fun `default launch selects by-number tab`() {
        val activity = launchWithIntent(Intent())
        val nav = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)
        assertEquals(R.id.navigation_by_number, nav.selectedItemId)
    }

    @Test
    fun `open_tab clipboard extra selects clipboard tab`() {
        val intent = Intent().apply { putExtra("open_tab", "clipboard") }
        val activity = launchWithIntent(intent)
        val nav = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)
        assertEquals(R.id.navigation_clipboard, nav.selectedItemId)
    }

    @Test
    fun `open_tab call_logs extra selects call logs tab`() {
        val intent = Intent().apply { putExtra("open_tab", "call_logs") }
        val activity = launchWithIntent(intent)
        val nav = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)
        assertEquals(R.id.navigation_call_logs, nav.selectedItemId)
    }

    @Test
    fun `ACTION_SEND with non-number text routes to clipboard`() {
        // PendingShare is set then consumed by ClipboardFragment.onResume — assert tab routing only.
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "hello world no numbers here")
        }
        val activity = launchWithIntent(intent)
        val nav = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)
        assertEquals(R.id.navigation_clipboard, nav.selectedItemId)
    }

    @Test
    fun `ACTION_SEND with valid number does not store PendingShare`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Call me at +14155551234")
        }
        launchWithIntent(intent)
        assertNull(PendingShare.text)
    }

    @Test
    fun `tel ACTION_VIEW does not crash and does not change tab`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tel:+14155551234"))
        val activity = launchWithIntent(intent)
        val nav = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)
        assertEquals(R.id.navigation_by_number, nav.selectedItemId)
    }

    @Test
    fun `tel ACTION_DIAL does not change tab`() {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:919876543210"))
        val activity = launchWithIntent(intent)
        val nav = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)
        assertEquals(R.id.navigation_by_number, nav.selectedItemId)
    }
}
