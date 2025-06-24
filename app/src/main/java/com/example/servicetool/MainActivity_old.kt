package com.example.servicetool

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen // NEU: Import für den Splash Screen
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView

class MainActivity_Old : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        // NEU: Installiert den Splash Screen.
        // Dieser Aufruf muss VOR super.onCreate() stehen.
        installSplashScreen()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupNavigation()
        initializeServices()
    }

    private fun setupNavigation() {
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // ERWEITERT: Alle Top-Level Destinations definieren (inkl. Moxa Settings)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_dashboard,
                R.id.nav_multicell_overview,
                R.id.nav_settings,
                R.id.nav_moxa_settings,          // Moxa Settings als Top-Level
                R.id.cellConfigurationFragment
            ), drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)

        // WICHTIG: Navigation View Setup NACH AppBarConfiguration
        navView.setupWithNavController(navController)

        // Debug: Log navigation events mit Details
        navController.addOnDestinationChangedListener { controller, destination, _ ->
            Log.d("MainActivity", "=== NAVIGATION EVENT ===")
            Log.d("MainActivity", "Von: ${controller.previousBackStackEntry?.destination?.label ?: "START"}")
            Log.d("MainActivity", "Zu: ${destination.label} (ID: ${destination.id})")
            Log.d("MainActivity", "Destination Route: ${destination.route ?: "keine Route"}")
            Log.d("MainActivity", "========================")
        }

        // ERWEITERT: Manueller Navigation Listener für alle Menu Items
        navView.setNavigationItemSelectedListener { menuItem ->
            Log.d("MainActivity", "Menu Item geklickt: ${menuItem.title} (ID: ${menuItem.itemId})")
            Log.d("MainActivity", "Aktuelle Destination: ${navController.currentDestination?.label} (ID: ${navController.currentDestination?.id})")

            val handled = when (menuItem.itemId) {
                R.id.nav_dashboard -> {
                    navigateIfNeeded(R.id.nav_dashboard, "Dashboard")
                }
                R.id.nav_multicell_overview -> {
                    navigateIfNeeded(R.id.nav_multicell_overview, "Multi-Cell Übersicht")
                }
                R.id.nav_settings -> {
                    navigateIfNeeded(R.id.nav_settings, "App Einstellungen")
                }
                R.id.nav_moxa_settings -> {
                    navigateIfNeeded(R.id.nav_moxa_settings, "Moxa Einstellungen")
                }
                R.id.cellConfigurationFragment -> {
                    navigateIfNeeded(R.id.cellConfigurationFragment, "Zellen Konfiguration")
                }
                else -> {
                    Log.w("MainActivity", "Unbekanntes Menu Item: ${menuItem.title} (ID: ${menuItem.itemId})")
                    false
                }
            }

            if (handled) {
                drawerLayout.closeDrawers()
            }
            handled
        }
    }

    /**
     * Hilfsmethode für saubere Navigation mit Fehlerbehandlung
     */
    private fun navigateIfNeeded(destinationId: Int, destinationName: String): Boolean {
        return try {
            if (navController.currentDestination?.id != destinationId) {
                Log.d("MainActivity", "Navigiere zu $destinationName")
                navController.navigate(destinationId)
                Log.d("MainActivity", "Navigation zu $destinationName erfolgreich")
                true
            } else {
                Log.d("MainActivity", "Bereits in $destinationName")
                true // Still handled, just don't navigate
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Fehler bei Navigation zu $destinationName: ${e.message}", e)
            false
        }
    }

    private fun initializeServices() {
        // Initialize settings manager
        SettingsManager.getInstance(this)

        // WICHTIG: MultiCellConfig mit Settings initialisieren
        MultiCellConfig.initialize(this)

        // Initialize logging mit erweiterten Informationen
        LoggingManager.getInstance(this).apply {
            logInfo("MainActivity", "Service Tool gestartet mit Moxa-Unterstützung")
            logInfo("Settings", "Moxa Konfiguration: ${MultiCellConfig.getMoxaIpAddress()}:${MultiCellConfig.getMoxaPort()}")

            // Get version info safely
            try {
                val versionName = packageManager.getPackageInfo(packageName, 0).versionName
                logInfo("System", "App Version: $versionName")
            } catch (e: Exception) {
                logInfo("System", "App Version: Unbekannt")
            }

            // Log verfügbare Navigation-Ziele
            logInfo("Navigation", "Verfügbare Bereiche: Dashboard, Multi-Cell, Zellen-Config, App-Settings, Moxa-Settings")
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        Log.d("MainActivity", "onSupportNavigateUp aufgerufen")
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onDestroy() {
        super.onDestroy()
        LoggingManager.getInstance(this).logInfo("MainActivity", "Service Tool beendet")
    }
}
