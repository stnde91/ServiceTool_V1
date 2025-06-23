package com.example.servicetool

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var loggingManager: LoggingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize services
        initializeServices()
        
        // Setup navigation
        setupBottomNavigation()
        
        // Log app startup
        logAppInfo()
    }

    private fun initializeServices() {
        loggingManager = LoggingManager.getInstance(this)
    }

    private fun setupBottomNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setupWithNavController(navController)
        
        // Optional: Handle navigation item reselection
        bottomNav.setOnItemReselectedListener { item ->
            // Do nothing to prevent fragment reload on reselection
        }
    }

    private fun logAppInfo() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            
            loggingManager.logInfo("APP", "ServiceTool gestartet - Version: $versionName ($versionCode)")
            loggingManager.logInfo("APP", "Android Version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            loggingManager.logInfo("APP", "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            
        } catch (e: PackageManager.NameNotFoundException) {
            loggingManager.logError("APP", "Fehler beim Ermitteln der App-Version", e)
        }
    }
}