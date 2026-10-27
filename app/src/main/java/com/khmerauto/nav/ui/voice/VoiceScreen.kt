package com.khmerauto.nav.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khmerauto.nav.core.calendar.SolarCalculator
import com.khmerauto.nav.core.calendar.ThemeMode
import com.khmerauto.nav.core.i18n.LanguageManager
import com.khmerauto.nav.hal.VehicleHalContract
import com.khmerauto.nav.map.CambodiaOfflineMapView
import com.khmerauto.nav.ui.cockpit.CockpitOverlay
import com.khmerauto.nav.ui.cockpit.EzTopStrip
import com.khmerauto.nav.ui.cockpit.EzVoiceModal
import com.khmerauto.nav.ui.components.EzBgDeep
import com.khmerauto.nav.voice.NavigationController
import com.khmerauto.nav.voice.VoiceAssistantService

/**
 * Modern Floating Khmer Voice Assistant Screen matching EZNav layout.
 *
 * Full EZNav-grade dark glassmorphism:
 * - Live offline Cambodia map background
 * - Centered floating dark frosted-glass modal with purple ambient glow
 * - 7 category vertical tabs on left
 * - 2-column glowing command cards grid on right
 * - Instant execution & testing of 140+ commands
 * - Zero ugly white backgrounds or flat Material 2 controls
 */
@Composable
fun VoiceScreen(
    currentHal: VehicleHalContract,
    voiceService: VoiceAssistantService,
    navController: NavigationController,
    onBackToCockpit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetry by currentHal.telemetryState.collectAsState()
    val currentLang by LanguageManager.currentLanguage.collectAsState()
    val themeMode by SolarCalculator.themeMode.collectAsState()

    Box(modifier = modifier.fillMaxSize().background(EzBgDeep)) {
        // 1. Full-screen map in background
        CambodiaOfflineMapView(
            vehicleLat = telemetry.latitude,
            vehicleLng = telemetry.longitude,
            vehicleHeading = telemetry.headingDegrees,
            vehicleSpeedKmh = telemetry.speedKmh,
            bottomDockPaddingDp = 0,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top navigation strip
        EzTopStrip(
            currentLang = currentLang,
            themeMode = themeMode,
            leftPanelVisible = true,
            activeOverlay = CockpitOverlay.VOICE,
            onSelectOverlay = { overlay ->
                if (overlay == CockpitOverlay.NONE) {
                    onBackToCockpit()
                }
            },
            onToggleLeft = onBackToCockpit,
            onLanguageToggle = { LanguageManager.toggleLanguage() },
            onThemeToggle = {
                SolarCalculator.setThemeMode(
                    when (themeMode) {
                        ThemeMode.AUTO -> ThemeMode.DAY
                        ThemeMode.DAY -> ThemeMode.NIGHT
                        ThemeMode.NIGHT -> ThemeMode.AUTO
                    }
                )
            }
        )

        // 3. Center floating Voice Command Modal (matching EZNav screenshot)
        EzVoiceModal(
            currentHal = currentHal,
            voiceService = voiceService,
            navController = navController,
            onClose = onBackToCockpit,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 56.dp, bottom = 16.dp, start = 20.dp, end = 20.dp)
        )
    }
}
