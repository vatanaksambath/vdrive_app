package com.khmerauto.nav

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.khmerauto.nav.core.calendar.SolarCalculator
import com.khmerauto.nav.core.i18n.AppStrings
import com.khmerauto.nav.core.i18n.LanguageManager
import com.khmerauto.nav.core.i18n.LocalAppStrings
import com.khmerauto.nav.service.CarBridgeService
import com.khmerauto.nav.ui.Phase1DiagnosticScreen
import com.khmerauto.nav.ui.cabin.CabinControlScreen
import com.khmerauto.nav.ui.cockpit.CockpitScreen
import com.khmerauto.nav.ui.theme.KMAutoNavTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions callback
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Automotive Ergonomics: Keep screen on while vehicle is operating
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        checkAndRequestPermissions()

        val app = application as KMAutoNavApp

        setContent {
            val currentLang by LanguageManager.currentLanguage.collectAsState()
            val themeMode by SolarCalculator.themeMode.collectAsState()
            val isDaytime = remember(themeMode) { SolarCalculator.isDayAppearance() }

            CompositionLocalProvider(LocalAppStrings provides AppStrings.get(currentLang)) {
                KMAutoNavTheme(isDaytime = isDaytime) {
                    Surface(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        var currentScreen by remember { mutableStateOf("cockpit") }
                        val runtimeMode by app.runtimeMode.collectAsState()
                        val gapSummary by app.gapEngine.reconciliationSummary.collectAsState()

                        Box(modifier = Modifier.fillMaxSize()) {
                            AnimatedContent(
                                targetState = currentScreen,
                                transitionSpec = {
                                    fadeIn() togetherWith fadeOut()
                                },
                                label = "screenTransition"
                            ) { screen ->
                                when (screen) {
                                    "cockpit" -> CockpitScreen(
                                        currentHal = app.currentHal,
                                        gapSummary = gapSummary,
                                        onDismissGapSummary = { app.gapEngine.dismissSummary() },
                                        onNavigateToCabin = { currentScreen = "cabin" },
                                        onNavigateToDiagnostics = { currentScreen = "diagnostics" },
                                        onNavigateToMap = { currentScreen = "map" },
                                        onNavigateToSpending = { currentScreen = "spending" },
                                        onNavigateToVoice = { currentScreen = "voice" }
                                    )
                                    "spending" -> com.khmerauto.nav.ui.spending.SpendingAndMaintenanceScreen(
                                        currentHal = app.currentHal,
                                        gapEngine = app.gapEngine,
                                        navController = app.navigationController,
                                        onBackToCockpit = { currentScreen = "cockpit" }
                                    )
                                    "voice" -> com.khmerauto.nav.ui.voice.VoiceScreen(
                                        currentHal = app.currentHal,
                                        voiceService = app.voiceService,
                                        navController = app.navigationController,
                                        onBackToCockpit = { currentScreen = "cockpit" }
                                    )
                                    "map" -> com.khmerauto.nav.ui.map.FullMapView(
                                        currentHal = app.currentHal,
                                        placeRepository = app.placeRepository,
                                        routeEngine = app.routeEngine,
                                        hudManager = app.hudManager,
                                        onBackToCockpit = { currentScreen = "cockpit" }
                                    )
                                    "cabin" -> CabinControlScreen(
                                        currentHal = app.currentHal,
                                        onClose = { currentScreen = "cockpit" }
                                    )
                                    "diagnostics" -> Phase1DiagnosticScreen(
                                        currentHal = app.currentHal,
                                        runtimeMode = runtimeMode,
                                        onModeSelected = { newMode ->
                                            app.setRuntimeMode(newMode)
                                            if (newMode == com.khmerauto.nav.hal.RuntimeMode.CAR_NATIVE_HOST) {
                                                CarBridgeService.start(this@MainActivity)
                                            } else {
                                                CarBridgeService.stop(this@MainActivity)
                                            }
                                        },
                                        snifferEngine = app.snifferEngine,
                                        onIpUpdated = { newIp ->
                                            app.setRuntimeMode(com.khmerauto.nav.hal.RuntimeMode.TABLET_WIFI_CLIENT, carIp = newIp)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            requestPermissionsLauncher.launch(needed.toTypedArray())
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val app = application as? KMAutoNavApp
        if (app != null && app.voiceService.handleHardwareVoiceKey(keyCode)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
