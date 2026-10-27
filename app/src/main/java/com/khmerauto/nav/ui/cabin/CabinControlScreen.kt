package com.khmerauto.nav.ui.cabin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khmerauto.nav.core.i18n.LocalAppStrings
import com.khmerauto.nav.hal.VehicleHalContract
import com.khmerauto.nav.map.CambodiaOfflineMapView
import com.khmerauto.nav.ui.cockpit.EzCabinSuite
import com.khmerauto.nav.ui.cockpit.EzTopStrip
import com.khmerauto.nav.ui.cockpit.CockpitOverlay
import com.khmerauto.nav.ui.components.EzBgDeep
import com.khmerauto.nav.core.i18n.LanguageManager
import com.khmerauto.nav.core.calendar.SolarCalculator
import com.khmerauto.nav.core.calendar.ThemeMode

/**
 * Modern Floating Cabin & Climate Control Screen for 2026 Geely Galaxy M7 (Flyme Auto).
 *
 * Full EZNav-grade dark glassmorphism:
 * - Live offline Cambodia map background
 * - Left floating Cabin Suite with Geely Galaxy M7 silhouette
 * - 7-speed fan ticks, stepped dual-temp selector, window toggles, roof shade, and safe tailgate
 * - Zero ugly white backgrounds or flat Material 2 controls
 */
@Composable
fun CabinControlScreen(
    currentHal: VehicleHalContract,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetry by currentHal.telemetryState.collectAsState()
    val strings = LocalAppStrings.current
    val currentLang by LanguageManager.currentLanguage.collectAsState()
    val themeMode by SolarCalculator.themeMode.collectAsState()

    Box(modifier = modifier.fillMaxSize().background(EzBgDeep)) {
        // 1. Full-screen map in background
        CambodiaOfflineMapView(
            vehicleLat = telemetry.latitude,
            vehicleLng = telemetry.longitude,
            vehicleHeading = telemetry.headingDegrees,
            vehicleSpeedKmh = telemetry.speedKmh,
            isNavigationActive = false,
            bottomDockPaddingDp = 0,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top navigation strip
        EzTopStrip(
            currentLang = currentLang,
            themeMode = themeMode,
            leftPanelVisible = true,
            activeOverlay = CockpitOverlay.CABIN,
            onSelectOverlay = { overlay ->
                if (overlay == CockpitOverlay.NONE) {
                    onClose()
                }
            },
            onToggleLeft = onClose,
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

        // 3. Left floating Cabin Suite (matching EZNav screenshot)
        EzCabinSuite(
            currentHal = currentHal,
            telemetry = telemetry,
            strings = strings,
            onClose = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 56.dp, bottom = 12.dp)
        )
    }
}
