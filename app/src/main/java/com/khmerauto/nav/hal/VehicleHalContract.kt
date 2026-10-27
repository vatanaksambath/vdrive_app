package com.khmerauto.nav.hal

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Unified Vehicle Hardware Abstraction Layer (HAL) Contract
 * Tailored specifically for the 2026 Geely Galaxy M7 (Flyme Auto / ECARX platform).
 *
 * Implemented by:
 * 1. GeelyFlymeHalAdapter (Runs directly on Geely Galaxy M7 head unit)
 * 2. RemoteWifiHalAdapter (Runs on external Android tablet connected via car Wi-Fi hotspot)
 * 3. SimulatedHalAdapter (Physics-based vehicle dynamics simulator for offline testing)
 */
interface VehicleHalContract {

    /**
     * Continuous reactive stream of vehicle telemetry, updated at 250ms cadence
     * or instantly upon any hardware event.
     */
    val telemetryState: StateFlow<VehicleTelemetryState>

    /**
     * Master HVAC power control.
     */
    suspend fun setHvacPower(on: Boolean): Result<Unit>

    /**
     * Target cabin temperature setpoint in Celsius (16.0°C to 30.0°C in 0.5°C steps).
     */
    suspend fun setTemperature(zone: Zone, tempC: Float): Result<Unit>

    /**
     * HVAC blower fan speed (1 to 7).
     */
    suspend fun setFanSpeed(level: Int): Result<Unit>

    /**
     * HVAC operational modes: A/C compressor, Auto mode, Max A/C, Recirculation,
     * Front windshield defrost, and Rear window / mirror defogger.
     */
    suspend fun setAcMode(
        ac: Boolean? = null,
        auto: Boolean? = null,
        maxAc: Boolean? = null,
        recirc: Boolean? = null,
        frontDefrost: Boolean? = null,
        rearDefrost: Boolean? = null
    ): Result<Unit>

    /**
     * Electric window position: 0% = fully closed, 100% = fully open.
     */
    suspend fun setWindowPosition(window: WindowTarget, openPercent: Int): Result<Unit>

    /**
     * Panoramic sunroof glass tilt/slide position and electric roller shade position.
     */
    suspend fun setSunroofAndShade(sunroofPercent: Int? = null, shadePercent: Int? = null): Result<Unit>

    /**
     * Central door locks (true = locked, false = unlocked).
     */
    suspend fun setDoorLocks(locked: Boolean): Result<Unit>

    /**
     * Geely Galaxy M7 powered smart tailgate (true = open, false = closed).
     */
    suspend fun setTailgate(open: Boolean): Result<Unit>

    /**
     * Front seat ventilation (0=off, 1..3) and seat heating (0=off, 1..3).
     */
    suspend fun setSeatClimate(
        seat: SeatTarget,
        ventLevel: Int? = null,
        heatLevel: Int? = null
    ): Result<Unit>

    /**
     * Geely Galaxy M7 256-color ambient lighting.
     */
    suspend fun setAmbientLight(on: Boolean, colorHex: String? = null): Result<Unit>

    /**
     * Low-level raw property execution into Geely Galaxy M7 VHAL or ECARX AdaptAPI.
     * Used for real-time testing and newly discovered sniffer property assignment.
     */
    suspend fun executeRawProperty(propId: Int, areaId: Int, value: Any): Result<Unit>

    /**
     * Lifecycle start: begins polling, listeners, or WebSocket connection.
     */
    fun start()

    /**
     * Lifecycle stop: cleans up listeners, sockets, and timers.
     */
    fun stop()
}

/**
 * Runtime execution mode of the KMAutoNav architecture.
 */
enum class RuntimeMode {
    SIMULATOR,            // Realistic physics-based Geely Galaxy M7 simulation
    TABLET_WIFI_CLIENT,   // External tablet connecting over car Wi-Fi hotspot to ws://<car-ip>:8888/ws/hal
    CAR_NATIVE_HOST       // Running directly on Geely Galaxy M7 head unit + hosting Ktor WebSocket server
}

/**
 * Complete immutable snapshot of vehicle telemetry.
 */
@Serializable
data class VehicleTelemetryState(
    val batteryPercent: Int = 85,
    val usableKwh: Float = 25.3f,
    val maxCapacityKwh: Float = 29.8f, // Geely Galaxy M7 PHEV battery spec
    val evRangeKm: Int = 185,
    val fuelRangeKm: Int = 920,
    val odometerKm: Float = 14285.4f,
    val speedKmh: Float = 0.0f,
    val gear: GearState = GearState.P,
    val isCharging: Boolean = false,
    val chargePowerKw: Float = 0.0f,
    val latitude: Double = 11.5564,
    val longitude: Double = 104.9282,
    val headingDegrees: Float = 0.0f,
    val hvacState: HvacState = HvacState(),
    val cabinState: CabinState = CabinState(),
    val tpmsState: TpmsState = TpmsState(),
    val timestampMs: Long = System.currentTimeMillis()
)

@Serializable
enum class GearState {
    P, R, N, D
}

@Serializable
enum class Zone {
    DRIVER, PASSENGER, REAR, ALL
}

@Serializable
enum class WindowTarget {
    FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT, ALL
}

@Serializable
enum class SeatTarget {
    DRIVER, PASSENGER
}

@Serializable
data class HvacState(
    val isPowerOn: Boolean = true,
    val driverTempC: Float = 22.0f,
    val passengerTempC: Float = 22.5f,
    val fanSpeed: Int = 3, // 1 to 7
    val isAcOn: Boolean = true,
    val isAutoOn: Boolean = true,
    val isMaxAcOn: Boolean = false,
    val isRecircOn: Boolean = true,
    val isFrontDefrostOn: Boolean = false,
    val isRearDefrostOn: Boolean = false
)

@Serializable
data class CabinState(
    val windowFlPercent: Int = 0,
    val windowFrPercent: Int = 0,
    val windowRlPercent: Int = 0,
    val windowRrPercent: Int = 0,
    val sunroofPercent: Int = 0,
    val roofShadePercent: Int = 0,
    val isDoorsLocked: Boolean = true,
    val isTailgateOpen: Boolean = false,
    val driverSeatVentLevel: Int = 0,    // 0 = off, 1 = low, 2 = med, 3 = max
    val passengerSeatVentLevel: Int = 0,
    val driverSeatHeatLevel: Int = 0,    // 0 = off, 1 = low, 2 = med, 3 = max
    val passengerSeatHeatLevel: Int = 0,
    val ambientLightColorHex: String = "#00E5FF", // Geely Cyan
    val ambientLightOn: Boolean = true
)

@Serializable
data class TpmsState(
    val flBar: Float = 2.40f,
    val frBar: Float = 2.42f,
    val rlBar: Float = 2.38f,
    val rrBar: Float = 2.41f,
    val flTempC: Int = 33,
    val frTempC: Int = 34,
    val rlTempC: Int = 32,
    val rrTempC: Int = 33
)
