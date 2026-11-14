package com.harshalbhatia.whatsopen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
