package com.example.servicetool

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.DynamicColors

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate started")
        
        try {
            // Install splash screen
            installSplashScreen()
            Log.d(TAG, "Splash screen installed")
            
            // Apply dynamic colors (Material You) - Android 12+
            if (DynamicColors.isDynamicColorAvailable()) {
                DynamicColors.applyToActivityIfAvailable(this)
                Log.d(TAG, "Dynamic colors applied (Material You)")
            } else {
                Log.d(TAG, "Dynamic colors not available, using static theme")
            }
            
            super.onCreate(savedInstanceState)
            Log.d(TAG, "super.onCreate completed")
            
            // Enable edge-to-edge display
            WindowCompat.setDecorFitsSystemWindows(window, false)
            Log.d(TAG, "Edge-to-edge enabled")
            
            setContentView(R.layout.activity_main)
            Log.d(TAG, "Layout set")

            // Initialize services
            initializeServices()
            Log.d(TAG, "Services initialized")
            
            // Setup navigation
            setupBottomNavigation()
            Log.d(TAG, "Navigation setup completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            throw e
        }
    }

    private fun initializeServices() {
        try {
            Log.d(TAG, "Initializing LoggingManager")
            val loggingManager = LoggingManager.getInstance(this)
            
            Log.d(TAG, "Initializing SettingsManager")
            SettingsManager.getInstance(this)
            
            Log.d(TAG, "Initializing MultiCellConfig")
            MultiCellConfig.initialize(this)
            
            loggingManager.logInfo("APP", "ServiceTool V0.106 gestartet")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing services", e)
            throw e
        }
    }

    private fun setupBottomNavigation() {
        try {
            Log.d(TAG, "Finding NavHostFragment")
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            
            if (navHostFragment == null) {
                Log.e(TAG, "NavHostFragment not found!")
                return
            }
            
            val navController = navHostFragment.navController
            Log.d(TAG, "NavController obtained")
            
            val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
            if (bottomNav == null) {
                Log.e(TAG, "BottomNavigationView not found!")
                return
            }
            
            Log.d(TAG, "Setting up navigation with controller")
            bottomNav.setupWithNavController(navController)
            
            // Apply window insets for edge-to-edge display
            val mainContainer = findViewById<android.view.View>(R.id.main_container)
            ViewCompat.setOnApplyWindowInsetsListener(mainContainer) { _, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

                // Apply top padding to the NavHostFragment so its content is below the status bar
                val navHost = findViewById<androidx.fragment.app.FragmentContainerView>(R.id.nav_host_fragment)
                navHost.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)

                // Apply bottom padding to the BottomNavigationView so its content is above the navigation bar
                val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
                bottomNav.setPadding(0, bottomNav.paddingTop, 0, systemBars.bottom)

                // Return the original insets, so that children can also handle them if they need to
                insets
            }
            
            // Handle navigation item reselection
            bottomNav.setOnItemReselectedListener { item ->
                Log.d(TAG, "Item reselected: ${item.title}")
                // Do nothing to prevent fragment reload on reselection
            }
            
            Log.d(TAG, "Bottom navigation setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up navigation", e)
            throw e
        }
    }
}