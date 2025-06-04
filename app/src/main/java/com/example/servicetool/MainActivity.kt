package com.example.servicetool

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
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

        // Define top-level destinations
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_dashboard,
                R.id.nav_multicell_overview,
                R.id.nav_settings
            ), drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)

        // WICHTIG: Navigation View Setup NACH AppBarConfiguration
        navView.setupWithNavController(navController)

        // Debug: Log navigation events mit mehr Details
        navController.addOnDestinationChangedListener { controller, destination, arguments ->
            Log.d("MainActivity", "=== NAVIGATION EVENT ===")
            Log.d("MainActivity", "Von: ${controller.previousBackStackEntry?.destination?.label ?: "START"}")
            Log.d("MainActivity", "Zu: ${destination.label} (ID: ${destination.id})")
            Log.d("MainActivity", "Destination Route: ${destination.route ?: "keine Route"}")
            Log.d("MainActivity", "========================")
        }

        // EXTRA: Manueller Navigation Listener für Menu Items
        navView.setNavigationItemSelectedListener { menuItem ->
            Log.d("MainActivity", "Menu Item geklickt: ${menuItem.title} (ID: ${menuItem.itemId})")
            Log.d("MainActivity", "Aktuelle Destination: ${navController.currentDestination?.label} (ID: ${navController.currentDestination?.id})")

            val handled = when (menuItem.itemId) {
                R.id.nav_dashboard -> {
                    Log.d("MainActivity", "Navigiere zu Dashboard")
                    if (navController.currentDestination?.id != R.id.nav_dashboard) {
                        try {
                            navController.navigate(R.id.nav_dashboard)
                            Log.d("MainActivity", "Navigation zu Dashboard erfolgreich")
                            true
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Fehler bei Navigation zu Dashboard: ${e.message}")
                            false
                        }
                    } else {
                        Log.d("MainActivity", "Bereits im Dashboard")
                        true // Still handled, just don't navigate
                    }
                }
                R.id.nav_multicell_overview -> {
                    Log.d("MainActivity", "Navigiere zu Multi-Cell")
                    if (navController.currentDestination?.id != R.id.nav_multicell_overview) {
                        try {
                            navController.navigate(R.id.nav_multicell_overview)
                            Log.d("MainActivity", "Navigation zu Multi-Cell erfolgreich")
                            true
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Fehler bei Navigation zu Multi-Cell: ${e.message}")
                            false
                        }
                    } else {
                        Log.d("MainActivity", "Bereits in Multi-Cell")
                        true
                    }
                }
                R.id.nav_settings -> {
                    Log.d("MainActivity", "Navigiere zu Settings")
                    if (navController.currentDestination?.id != R.id.nav_settings) {
                        try {
                            navController.navigate(R.id.nav_settings)
                            Log.d("MainActivity", "Navigation zu Settings erfolgreich")
                            true
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Fehler bei Navigation zu Settings: ${e.message}")
                            false
                        }
                    } else {
                        Log.d("MainActivity", "Bereits in Settings")
                        true
                    }
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

    private fun initializeServices() {
        // Initialize settings manager
        SettingsManager.getInstance(this)

        // WICHTIG: MultiCellConfig mit Settings initialisieren
        MultiCellConfig.initialize(this)

        // Initialize logging
        LoggingManager.getInstance(this).apply {
            logInfo("MainActivity", "Service Tool gestartet")
            logInfo("Settings", "Moxa Konfiguration: ${MultiCellConfig.getMoxaIpAddress()}:${MultiCellConfig.getMoxaPort()}")

            // Get version info safely
            try {
                val versionName = packageManager.getPackageInfo(packageName, 0).versionName
                logInfo("System", "Version: $versionName")
            } catch (e: Exception) {
                logInfo("System", "Version: Unbekannt")
            }
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