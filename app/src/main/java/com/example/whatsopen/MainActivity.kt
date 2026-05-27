package com.example.whatsopen

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.whatsopen.ui.WhatsOpenApp
import com.example.whatsopen.ui.theme.WhatsOpenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Disable Android Q+ system bar contrast scrim so our Compose colors render edge-to-edge.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            WhatsOpenTheme { WhatsOpenApp() }
        }
    }
}
