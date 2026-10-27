package com.khmerauto.nav

import android.app.Application
import android.os.Build
import android.util.Log
import com.khmerauto.nav.core.database.AppDatabase
import com.khmerauto.nav.core.database.entity.VehicleStateSnapshot
import com.khmerauto.nav.core.power.CarPowerLifecycleObserver
import com.khmerauto.nav.core.database.entity.PlaceEntity
import com.khmerauto.nav.display.SecondaryClusterHudManager
import com.khmerauto.nav.hal.*
import com.khmerauto.nav.hal.geely.GeelyFlymeHalAdapter
import com.khmerauto.nav.hal.mock.SimulatedHalAdapter
import com.khmerauto.nav.hal.sniffer.VhalSnifferEngine
import com.khmerauto.nav.hal.wifi.CarHalWebSocketServer
import com.khmerauto.nav.hal.wifi.RemoteWifiHalAdapter
import com.khmerauto.nav.map.routing.NavigationRouteEngine
import com.khmerauto.nav.map.search.OfflinePlaceSearchRepository
import com.khmerauto.nav.telemetry.GapReconciliationEngine
import com.khmerauto.nav.voice.NavigationController
import com.khmerauto.nav.voice.VoiceAssistantService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "KMAutoNavApp"

/**
 * Main Application Class for KMAutoNav Geely Galaxy M7 Architecture.
 * Coordinates HAL switching between SIMULATOR, TABLET_WIFI_CLIENT, and CAR_NATIVE_HOST.
 *
 * Implements strict automotive power guardrail: CarPowerLifecycleObserver for zero 12V drain.
 */
class KMAutoNavApp : Application() {

    private val applicationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    lateinit var database: AppDatabase
        private set

    lateinit var powerLifecycleObserver: CarPowerLifecycleObserver
        private set

    lateinit var placeRepository: OfflinePlaceSearchRepository
        private set

    lateinit var routeEngine: NavigationRouteEngine
        private set

    lateinit var hudManager: SecondaryClusterHudManager
        private set

    lateinit var navigationController: NavigationController
        private set

    lateinit var voiceService: VoiceAssistantService
        private set

    lateinit var gapEngine: GapReconciliationEngine
        private set

    lateinit var locationProvider: com.khmerauto.nav.core.location.VehicleLocationProvider
        private set

    val snifferEngine = VhalSnifferEngine()

    private val _runtimeMode = MutableStateFlow(RuntimeMode.SIMULATOR)
    val runtimeMode: StateFlow<RuntimeMode> = _runtimeMode.asStateFlow()

    private var activeHal: VehicleHalContract? = null
    private var webSocketServer: CarHalWebSocketServer? = null
    private var progressTrackerJob: Job? = null

    val currentHal: VehicleHalContract
        get() = activeHal ?: throw IllegalStateException("HAL not initialized")

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Initializing KMAutoNav for Geely Galaxy M7 (Build: ${Build.MANUFACTURER} ${Build.MODEL})...")

        database = AppDatabase.getInstance(this)

        // Initialize Phase 3 Map, Search, and Cluster HUD Subsystems
        placeRepository = OfflinePlaceSearchRepository(database)
        routeEngine = NavigationRouteEngine()
        hudManager = SecondaryClusterHudManager(this, routeEngine).apply { start() }

        // Initialize GPS & Network Location Provider
        locationProvider = com.khmerauto.nav.core.location.VehicleLocationProvider(this) { lat, lng, speed, bearing ->
            activeHal?.updateLocation(lat, lng, speed, bearing)
        }
        locationProvider.startListening()

        // Initialize Phase 5 "Two Numbers Moved Without You" Gap Reconciliation Engine
        gapEngine = GapReconciliationEngine(database)

        // Initialize Navigation Controller for Voice Command Dispatching
        navigationController = object : NavigationController {
            override suspend fun navigateTo(destination: PlaceEntity) {
                val t = activeHal?.telemetryState?.value ?: com.khmerauto.nav.hal.VehicleTelemetryState()
                routeEngine.startNavigation(destination, t.batteryPercent, t.latitude, t.longitude)
            }
            override suspend fun navigateHome() {
                val home = placeRepository.getPlacesByCategory(com.khmerauto.nav.core.database.entity.PlaceCategory.SAVED_HOME).firstOrNull()
                    ?: placeRepository.getAllEvChargers().firstOrNull()
                if (home != null) navigateTo(home)
            }
            override suspend fun navigateWork() {
                val work = placeRepository.getPlacesByCategory(com.khmerauto.nav.core.database.entity.PlaceCategory.SAVED_WORK).firstOrNull()
                    ?: placeRepository.getAllEvChargers().getOrNull(1)
                if (work != null) navigateTo(work)
            }
            override suspend fun navigateNearestCharger() {
                val charger = placeRepository.getAllEvChargers().firstOrNull()
                if (charger != null) navigateTo(charger)
            }
            override suspend fun navigateNearestRepair() {
                val repair = placeRepository.getPlacesByCategory(com.khmerauto.nav.core.database.entity.PlaceCategory.REPAIR_SHOP).firstOrNull()
                    ?: placeRepository.getAllEvChargers().firstOrNull()
                if (repair != null) navigateTo(repair)
            }
            override fun stopNavigation() = routeEngine.stopNavigation()
            override fun zoomIn() {}
            override fun zoomOut() {}
            override fun recenter() {}
            override fun setThemeMode(mode: com.khmerauto.nav.core.calendar.ThemeMode) {
                com.khmerauto.nav.core.calendar.SolarCalculator.setThemeMode(mode)
            }
            override fun setVoiceMuted(muted: Boolean) {}
        }

        // Initialize Phase 4 Hybrid Khmer Voice Assistant
        voiceService = VoiceAssistantService(
            context = this,
            database = database,
            hal = object : VehicleHalContract {
                override val telemetryState get() = currentHal.telemetryState
                override suspend fun setHvacPower(on: Boolean) = currentHal.setHvacPower(on)
                override suspend fun setTemperature(zone: Zone, tempC: Float) = currentHal.setTemperature(zone, tempC)
                override suspend fun setFanSpeed(level: Int) = currentHal.setFanSpeed(level)
                override suspend fun setAcMode(ac: Boolean?, auto: Boolean?, maxAc: Boolean?, recirc: Boolean?, frontDefrost: Boolean?, rearDefrost: Boolean?) = currentHal.setAcMode(ac, auto, maxAc, recirc, frontDefrost, rearDefrost)
                override suspend fun setWindowPosition(window: WindowTarget, openPercent: Int) = currentHal.setWindowPosition(window, openPercent)
                override suspend fun setSunroofAndShade(sunroofPercent: Int?, shadePercent: Int?) = currentHal.setSunroofAndShade(sunroofPercent, shadePercent)
                override suspend fun setDoorLocks(locked: Boolean) = currentHal.setDoorLocks(locked)
                override suspend fun setTailgate(open: Boolean) = currentHal.setTailgate(open)
                override suspend fun setSeatClimate(seat: SeatTarget, ventLevel: Int?, heatLevel: Int?) = currentHal.setSeatClimate(seat, ventLevel, heatLevel)
                override suspend fun setAmbientLight(on: Boolean, colorHex: String?) = currentHal.setAmbientLight(on, colorHex)
                override suspend fun executeRawProperty(propId: Int, areaId: Int, value: Any) = currentHal.executeRawProperty(propId, areaId, value)
                override fun start() = currentHal.start()
                override fun stop() = currentHal.stop()
            },
            navController = navigationController
        )

        // Pre-seed offline POIs, EV fast chargers, and perform initial gap reconciliation
        applicationScope.launch {
            placeRepository.initializeSeedDataIfNeeded()
            delay(1200)
            try {
                val initialTelemetry = activeHal?.telemetryState?.value ?: com.khmerauto.nav.hal.VehicleTelemetryState()
                gapEngine.reconcileGapIfNeeded(initialTelemetry)
            } catch (e: Exception) {
                Log.w(TAG, "Initial gap catch-up note: ${e.message}")
            }
        }

        // Initialize Deep-Sleep & 12V Battery Compliance Observer
        powerLifecycleObserver = CarPowerLifecycleObserver(
            context = this,
            database = database,
            getLatestTelemetry = { activeHal?.telemetryState?.value ?: com.khmerauto.nav.hal.VehicleTelemetryState() }
        ).apply {
            onReleaseMicrophone = {
                voiceService.stopListeningAndRelease()
                locationProvider.stopListening()
            }
            onPauseMapRendering = {
                hudManager.stop()
            }
            onStopWebSocketServer = {
                webSocketServer?.stop()
                webSocketServer = null
            }
            onUnregisterSensors = {
                (activeHal as? GeelyFlymeHalAdapter)?.unregisterSensors()
                locationProvider.stopListening()
            }
            onResumeSubsystems = {
                hudManager.start()
                voiceService.startListening()
                locationProvider.startListening()
                if (_runtimeMode.value == RuntimeMode.CAR_NATIVE_HOST) {
                    setRuntimeMode(RuntimeMode.CAR_NATIVE_HOST)
                }
                // Perform gap reconciliation on vehicle wake
                applicationScope.launch {
                    delay(800)
                    try {
                        val wakeTelemetry = activeHal?.telemetryState?.value ?: com.khmerauto.nav.hal.VehicleTelemetryState()
                        gapEngine.reconcileGapIfNeeded(wakeTelemetry)
                    } catch (e: Exception) {
                        Log.w(TAG, "Wake gap catch-up note: ${e.message}")
                    }
                }
            }
            register()
        }

        // Auto-detect initial mode: If running on Flyme Auto / ECARX head unit, default to CAR_NATIVE_HOST
        val isGeelyOrFlyme = Build.MANUFACTURER.contains("geely", ignoreCase = true) ||
                Build.BRAND.contains("geely", ignoreCase = true) ||
                Build.MODEL.contains("m7", ignoreCase = true) ||
                Build.FINGERPRINT.contains("ecarx", ignoreCase = true)

        val initialMode = if (isGeelyOrFlyme) RuntimeMode.CAR_NATIVE_HOST else RuntimeMode.SIMULATOR
        setRuntimeMode(initialMode)

        // Start background snapshot persistence loop
        startDatabaseSnapshotRecorder()
    }

    override fun onTerminate() {
        locationProvider.stopListening()
        hudManager.stop()
        powerLifecycleObserver.unregister()
        super.onTerminate()
    }

    fun setRuntimeMode(newMode: RuntimeMode, carIp: String = "192.168.43.1") {
        Log.i(TAG, "Switching runtime mode to: $newMode")

        // Stop existing HAL and server
        activeHal?.stop()
        webSocketServer?.stop()
        webSocketServer = null

        val hal: VehicleHalContract = when (newMode) {
            RuntimeMode.SIMULATOR -> {
                val sim = SimulatedHalAdapter()
                sim.snifferEventListener = { propId, areaId, value, source ->
                    snifferEngine.recordEvent(propId, areaId, value, source)
                }
                sim.start()
                sim
            }
            RuntimeMode.TABLET_WIFI_CLIENT -> {
                val remote = RemoteWifiHalAdapter(carIp = carIp, carPort = 8888)
                remote.onSnifferPacketReceived = { packet ->
                    snifferEngine.recordEvent(packet.propId, packet.areaId, packet.value, packet.source)
                }
                remote.start()
                remote
            }
            RuntimeMode.CAR_NATIVE_HOST -> {
                val nativeGeely = GeelyFlymeHalAdapter(this)
                nativeGeely.snifferEventListener = { propId, areaId, value, source ->
                    snifferEngine.recordEvent(propId, areaId, value, source)
                    webSocketServer?.broadcastSnifferEvent(propId, areaId, value, source)
                }
                nativeGeely.start()

                // Start Ktor WebSocket Bridge Server on port 8888 for external tablet connections
                val server = CarHalWebSocketServer(vehicleHal = nativeGeely, port = 8888)
                server.start()
                webSocketServer = server

                nativeGeely
            }
        }

        activeHal = hal
        _runtimeMode.value = newMode
        startTelemetryProgressTracker()
    }

    private fun startTelemetryProgressTracker() {
        progressTrackerJob?.cancel()
        val hal = activeHal ?: return
        progressTrackerJob = applicationScope.launch {
            launch {
                hal.telemetryState.collect { telemetry ->
                    routeEngine.updateVehicleProgress(
                        currentLat = telemetry.latitude,
                        currentLng = telemetry.longitude,
                        currentSpeed = telemetry.speedKmh,
                        batteryPercent = telemetry.batteryPercent
                    )
                }
            }
            launch {
                routeEngine.navState.collect { nav ->
                    val sim = activeHal as? SimulatedHalAdapter
                    if (sim != null) {
                        if (nav.isNavigating && nav.routePolyline.isNotEmpty()) {
                            sim.setActiveNavigationRoute(nav.routePolyline)
                        } else if (!nav.isNavigating) {
                            sim.clearActiveNavigationRoute()
                        }
                    }
                }
            }
        }
    }

    private fun startDatabaseSnapshotRecorder() {
        applicationScope.launch {
            while (isActive) {
                delay(5000) // Snapshot every 5 seconds for "Two Numbers Moved Without You" reconciliation
                if (powerLifecycleObserver.isAsleep.get()) continue

                try {
                    val telemetry = activeHal?.telemetryState?.value ?: continue
                    val snapshot = VehicleStateSnapshot(
                        timestamp = System.currentTimeMillis(),
                        odometerKm = telemetry.odometerKm,
                        batteryPercent = telemetry.batteryPercent,
                        usableKwh = telemetry.usableKwh,
                        isCharging = telemetry.isCharging,
                        speedKmh = telemetry.speedKmh
                    )
                    database.vehicleDao().insertSnapshot(snapshot)
                    gapEngine.saveSnapshot(telemetry)
                } catch (e: Exception) {
                    Log.v(TAG, "Snapshot record note: ${e.message}")
                }
            }
        }
    }
}
