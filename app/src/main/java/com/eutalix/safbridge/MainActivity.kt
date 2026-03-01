package com.eutalix.safbridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.eutalix.safbridge.data.PreferencesManager
import com.eutalix.safbridge.ui.screens.HomeScreen
import com.eutalix.safbridge.ui.screens.SettingsScreen
import com.eutalix.safbridge.ui.theme.ZArchiverSafBridgeTheme

/**
 * The main entry point of the application.
 * It handles the initialization of data layers and high-level UI navigation.
 */
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize the data layer
        val prefsManager = PreferencesManager(this)

        setContent {
            ZArchiverSafBridgeTheme {
                // Simple state-based navigation for a lightweight app
                var currentScreen by remember { mutableStateOf("home") }

                when (currentScreen) {
                    "home" -> HomeScreen(
                        prefsManager = prefsManager,
                        onNavigateSettings = { currentScreen = "settings" }
                    )
                    "settings" -> SettingsScreen(
                        prefsManager = prefsManager,
                        onBack = { currentScreen = "home" }
                    )
                }
            }
        }
    }
}