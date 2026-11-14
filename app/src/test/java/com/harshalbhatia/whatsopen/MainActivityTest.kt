package com.harshalbhatia.whatsopen

import android.content.Intent
import android.graphics.Rect
import android.os.BadParcelableException
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

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

    private fun currentFragment(activity: MainActivity): Fragment =
        (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment)
            .childFragmentManager.primaryNavigationFragment!!

    /**
     * Applies insets as the system would: a status bar, navigation bar and display cutout, and a
     * keyboard [keyboard] pixels tall. A floating keyboard is shown but has no height. Returns
     * what the root passed on to its children.
     */
    private fun MainActivity.applyInsets(
        statusBar: Int = 0,
        navigationBar: Insets = Insets.NONE,
        cutout: Insets = Insets.NONE,
        keyboard: Int = 0,
        keyboardShown: Boolean = keyboard > 0,
    ): WindowInsetsCompat {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusBar, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), navigationBar)
            .setInsets(WindowInsetsCompat.Type.displayCutout(), cutout)
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboard))
            .setVisible(WindowInsetsCompat.Type.ime(), keyboardShown)
            .build()
        val root = findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        return ViewCompat.dispatchApplyWindowInsets(root, insets)
    }

    private val View.padding get() = listOf(paddingLeft, paddingTop, paddingRight, paddingBottom)

    private fun ViewGroup.boundsOf(descendant: View) =
        Rect().also { descendant.getDrawingRect(it); offsetDescendantRectToMyCoords(descendant, it) }

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

    // Edge to edge

    @Test
    fun `system bars and cutout pad the tabs, and the navigation bar pads the bottom navigation`() {
        val activity = launchWithIntent(Intent())
        val passedOn = activity.applyInsets(
            statusBar = 60,
            navigationBar = Insets.of(0, 0, 30, 40),
            cutout = Insets.of(80, 0, 0, 0),
        )

        val tabs = activity.findViewById<View>(R.id.nav_host_fragment_content_main)
        val bottomNavigation = activity.findViewById<View>(R.id.bottom_navigation)
        assertEquals(listOf(80, 60, 30, 0), tabs.padding)
        assertEquals(listOf(80, 30, 40), bottomNavigation.padding.let { listOf(it[0], it[2], it[3]) })
        assertTrue(bottomNavigation.isShown)
        // Up to API 29, consuming them would also keep them from snackbars added to the window later.
        assertFalse(passedOn.isConsumed)
        assertEquals(60, passedOn.getInsets(WindowInsetsCompat.Type.statusBars()).top)
    }

    @Test
    fun `keyboard hides the bottom navigation and ends the tabs at the keyboard`() {
        val activity = launchWithIntent(Intent())
        val tabs = activity.findViewById<View>(R.id.nav_host_fragment_content_main)
        val bottomNavigation = activity.findViewById<View>(R.id.bottom_navigation)

        activity.applyInsets(statusBar = 60, navigationBar = Insets.of(0, 0, 0, 40), keyboard = 300)
        assertFalse(bottomNavigation.isShown)
        assertEquals(listOf(0, 60, 0, 300), tabs.padding)
        // Its own inset handling would have added the keyboard as well.
        assertEquals(40, bottomNavigation.paddingBottom)

        activity.applyInsets(statusBar = 60, navigationBar = Insets.of(0, 0, 0, 40))
        assertTrue(bottomNavigation.isShown)
        assertEquals(0, tabs.paddingBottom)
    }

    @Test
    fun `a floating keyboard leaves room for the navigation bar`() {
        val activity = launchWithIntent(Intent())
        activity.applyInsets(navigationBar = Insets.of(0, 0, 0, 40), keyboardShown = true)

        assertFalse(activity.findViewById<View>(R.id.bottom_navigation).isShown)
        assertEquals(40, activity.findViewById<View>(R.id.nav_host_fragment_content_main).paddingBottom)
    }

    @Test
    fun `snackbars sit on the bottom navigation, or above the keyboard while it hides`() {
        val activity = launchWithIntent(Intent())
        val bottomNavigation = activity.findViewById<View>(R.id.bottom_navigation)
        assertSame(bottomNavigation, currentFragment(activity).showSnackbar(R.string.number_copied)!!.anchorView)

        activity.applyInsets(keyboard = 300)
        assertNull(currentFragment(activity).showSnackbar(R.string.number_copied)!!.anchorView)
    }

    @Test
    fun `keyboard keeps the focused number field and Start Chat in view`() {
        val activity = launchWithIntent(Intent())
        val scrollView = currentFragment(activity).requireView() as ViewGroup
        val phoneInput = activity.findViewById<View>(R.id.phone_input)
        val button = activity.findViewById<View>(R.id.open_whatsapp_button)
        phoneInput.requestFocus()
        ShadowLooper.idleMainLooper()
        // A keyboard that leaves room for the field and the button together, but only scrolled.
        val content = scrollView.getChildAt(0) as ViewGroup
        val needed = content.boundsOf(button).bottom - content.boundsOf(phoneInput).top
        activity.applyInsets(keyboard = scrollView.height - needed - 10)
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        // Drawing is what moves a smooth scroll along, and Robolectric doesn't draw.
        scrollView.computeScroll()

        val visibleTop = scrollView.scrollY
        val visibleBottom = scrollView.scrollY + scrollView.height
        assertTrue("the tab scrolled", visibleTop > 0)
        assertTrue("field in view", content.boundsOf(phoneInput).top >= visibleTop)
        assertTrue("button in view", content.boundsOf(button).bottom <= visibleBottom)
    }
}
