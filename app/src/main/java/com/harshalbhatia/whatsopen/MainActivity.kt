package com.harshalbhatia.whatsopen

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

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.getStringExtra("open_tab")) {
            "clipboard" -> binding.bottomNavigation.selectedItemId = R.id.navigation_clipboard
            "call_logs" -> binding.bottomNavigation.selectedItemId = R.id.navigation_call_logs
        }

        val region = DefaultRegion.get(this)
        val number = IncomingIntentParser.extractPhoneNumber(intent, region)
        if (number != null) {
            WhatsAppLauncher.openChat(this, number, region)
            return
        }

        // Shared text without exactly one number goes to the Clipboard tab for the user to confirm.
        val sharedText = IncomingIntentParser.sharedText(intent)
        if (sharedText != null) {
            PendingShare.text = sharedText
            binding.bottomNavigation.selectedItemId = R.id.navigation_clipboard
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        return navHostFragment.navController.navigateUp()
                || super.onSupportNavigateUp()
    }
}
