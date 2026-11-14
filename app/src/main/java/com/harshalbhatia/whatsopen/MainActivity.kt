package com.harshalbhatia.whatsopen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.harshalbhatia.whatsopen.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets -> applyInsets(insets) }
        // In place of the bottom navigation's own handling, which would pad it for the keyboard too.
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { _, insets -> padBottomNavigation(insets) }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)
        binding.bottomNavigation.setOnItemReselectedListener { /* no-op */ }

        // A recreated activity, or one reopened from Recents, would replay an old request.
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) {
            handleIncomingIntent(intent)
        }
    }

    /**
     * Keeps the tabs clear of the status bar and display cutout. While the keyboard is up the
     * bottom navigation hides and the tabs end at the keyboard, so the scrolling tabs can bring
     * what's being typed into view.
     */
    private fun applyInsets(insets: WindowInsetsCompat): WindowInsetsCompat {
        val bars = insets.barsAndCutout()
        val keyboardShown = insets.isVisible(WindowInsetsCompat.Type.ime())
        // A floating keyboard has no height; the navigation bar still needs room then.
        val keyboardTop = maxOf(insets.getInsets(WindowInsetsCompat.Type.ime()).bottom, bars.bottom)
        binding.navHostFragmentContentMain.updatePadding(
            left = bars.left,
            top = bars.top,
            right = bars.right,
            bottom = if (keyboardShown) keyboardTop else 0,
        )
        binding.bottomNavigation.isGone = keyboardShown
        // Not consumed: up to API 29 that also keeps them from views added to the window after
        // this one, such as snackbars, which need them to clear the keyboard.
        return insets
    }

    /** Keeps the bottom navigation's items above the navigation bar, which its background extends behind. */
    private fun padBottomNavigation(insets: WindowInsetsCompat): WindowInsetsCompat {
        val bars = insets.barsAndCutout()
        binding.bottomNavigation.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
        return insets
    }

    private fun WindowInsetsCompat.barsAndCutout() =
        getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /** Opens the tab named by [EXTRA_OPEN_TAB]. Shared text arrives through [PendingShare]. */
    private fun handleIncomingIntent(intent: Intent) {
        val tab = try {
            intent.getStringExtra(EXTRA_OPEN_TAB)
        } catch (e: RuntimeException) {
            // Another app can send extras that fail to unparcel (BadParcelableException).
            null
        }
        when (tab) {
            TAB_CLIPBOARD -> binding.bottomNavigation.selectedItemId = R.id.navigation_clipboard
            TAB_CALL_LOGS -> binding.bottomNavigation.selectedItemId = R.id.navigation_call_logs
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        return navHostFragment.navController.navigateUp()
                || super.onSupportNavigateUp()
    }

    companion object {
        /**
         * Intent extra naming the tab to open: [TAB_CLIPBOARD] or [TAB_CALL_LOGS].
         * res/xml/shortcuts.xml repeats these values as literals.
         */
        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_CLIPBOARD = "clipboard"
        const val TAB_CALL_LOGS = "call_logs"

        /**
         * Opens the Clipboard tab from outside the app's task. If the app is open, its task comes
         * forward and the existing MainActivity gets the intent in [onNewIntent]. Put the text to
         * show in [PendingShare] first.
         */
        fun clipboardTabIntent(context: Context): Intent =
            // Matches the launcher's intent, so if this starts the task, tapping the app icon later
            // resumes it instead of stacking a second MainActivity on top.
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .putExtra(EXTRA_OPEN_TAB, TAB_CLIPBOARD)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
    }
}
