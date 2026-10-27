package com.khmerauto.nav.hal.mock

import android.util.Log
import com.khmerauto.nav.hal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.sin

private const val TAG = "SimulatedHalAdapter"

/**
 * Realistic Physics-Based Vehicle Dynamics Simulator
 * Tuned specifically for the 2026 Geely Galaxy M7 (Thor PHEV Powertrain).
 *
 * Simulates:
 * - Driving across Phnom Penh urban routes (Norodom, Riverside, Koh Pich, Hun Sen Blvd)
 * - Real-time speed acceleration/braking with city stop-and-go traffic
 * - High-voltage 29.8 kWh battery SoC discharge & energy efficiency
 * - Dual-zone cabin thermal modeling (cooling towards setpoint against 34°C Phnom Penh ambient heat)
 * - Window & panoramic sunroof electric motor movement transitions
 * - Dynamic 4-wheel TPMS pressure & temperature heating curves
 */
class SimulatedHalAdapter : VehicleHalContract {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _telemetryState = MutableStateFlow(
        VehicleTelemetryState(
            batteryPercent = 88,
            usableKwh = 26.2f,
            maxCapacityKwh = 29.8f,
            evRangeKm = 192,
            fuelRangeKm = 940,
            odometerKm = 12450.6f,
            speedKmh = 0.0f,
            gear = GearState.P,
            isCharging = false,
            chargePowerKw = 0.0f,
            hvacState = HvacState(
                isPowerOn = true,
                driverTempC = 22.0f,
                passengerTempC = 22.5f,
                fanSpeed = 3,
                isAcOn = true,
                isAutoOn = true
            ),
            cabinState = CabinState(
                ambientLightColorHex = "#00E5FF", // Geely Cyan
                ambientLightOn = true,
                isDoorsLocked = true,
                isTailgateOpen = false
            ),
            tpmsState = TpmsState(
                flBar = 2.40f,
                frBar = 2.41f,
                rlBar = 2.38f,
                rrBar = 2.39f,
                flTempC = 33,
                frTempC = 33,
                rlTempC = 32,
                rrTempC = 32
            )
        )
    )
    override val telemetryState: StateFlow<VehicleTelemetryState> = _telemetryState.asStateFlow()

    private var simulationJob: Job? = null
    private var isSimulating = false

    // Target values for smooth motor animation
    private var targetWindowFl = 0
    private var targetWindowFr = 0
    private var targetWindowRl = 0
    private var targetWindowRr = 0
    private var targetSunroof = 0
    private var targetShade = 0

    // Thermal simulation
    private var currentCabinTemp = 24.5f
    private val ambientOutsideTemp = 34.0f // Phnom Penh sunny day

    // Sniffer event listener
    var snifferEventListener: ((propId: Int, areaId: Int, value: Any, source: String) -> Unit)? = null

    override fun start() {
        if (isSimulating) return
        isSimulating = true
        Log.i(TAG, "Starting Geely Galaxy M7 Simulator...")

        simulationJob = scope.launch {
            var stepCounter = 0L
            while (isActive && isSimulating) {
                stepCounter++
                simulatePhysicsTick(stepCounter)
                delay(250) // 4Hz simulation tick
            }
        }
    }

    override fun stop() {
        Log.i(TAG, "Stopping Geely Galaxy M7 Simulator...")
        isSimulating = false
        simulationJob?.cancel()
    }

    private fun simulatePhysicsTick(tick: Long) {
        val dtHours = (250.0 / 3_600_000.0).toFloat() // delta time in hours

        _telemetryState.update { current ->
            // 1. Urban Driving Speed Simulation (Phnom Penh traffic cycle)
            val wave = sin(tick * 0.05).toFloat()
            val targetSpeed = if (wave > -0.2f) {
                ((wave + 0.2f) * 42.0f).coerceIn(0f, 62f)
            } else {
                0.0f // Stopped at red light
            }

            val speedDiff = targetSpeed - current.speedKmh
            val speed = (current.speedKmh + speedDiff * 0.15f).coerceAtLeast(0f)
            val gear = if (speed > 1.0f) GearState.D else GearState.P

            // 2. Odometer calculation & Phnom Penh GPS road navigation loop
            val distanceKm = speed * dtHours
            val newOdometer = current.odometerKm + distanceKm

            val loopProgress = ((newOdometer * 2.0f) % 10.0f).toDouble()
            val heading: Float
            val latDelta: Double
            val lngDelta: Double

            when {
                loopProgress < 3.0 -> { // Northbound on Preah Norodom Blvd
                    heading = 355f
                    latDelta = (distanceKm / 111.0)
                    lngDelta = 0.0
                }
                loopProgress < 6.0 -> { // Westbound on Russian Federation Blvd
                    heading = 270f
                    latDelta = 0.0
                    lngDelta = -(distanceKm / (111.0 * kotlin.math.cos(Math.toRadians(current.latitude))))
                }
                loopProgress < 8.0 -> { // Southbound on Street 271
                    heading = 175f
                    latDelta = -(distanceKm / 111.0)
                    lngDelta = 0.0
                }
                else -> { // Eastbound on Mao Tse Toung Blvd back to Norodom
                    heading = 85f
                    latDelta = 0.0
                    lngDelta = (distanceKm / (111.0 * kotlin.math.cos(Math.toRadians(current.latitude))))
                }
            }

            val newLat = (current.latitude + latDelta).coerceIn(11.5000, 11.6000)
            val newLng = (current.longitude + lngDelta).coerceIn(104.8500, 104.9500)

            // 3. Battery Discharge Dynamics
            // Base consumption: HVAC draw (1.2 kW) + driving traction draw
            val hvacDrawKw = if (current.hvacState.isPowerOn && current.hvacState.isAcOn) 1.2f else 0.2f
            val tractionDrawKw = (speed / 60f) * 14.5f // ~14.5 kWh/100km at 60km/h
            val totalPowerDrawKw = if (current.isCharging) -60.0f else (hvacDrawKw + tractionDrawKw)

            val energyDeltaKwh = totalPowerDrawKw * dtHours
            val newUsableKwh = (current.usableKwh - energyDeltaKwh).coerceIn(0.5f, current.maxCapacityKwh)
            val newBatteryPercent = ((newUsableKwh / current.maxCapacityKwh) * 100).toInt()
            val newEvRangeKm = ((newUsableKwh / current.maxCapacityKwh) * 218).toInt()

            // 4. Cabin Thermal Regulation (Phnom Penh climate)
            val targetHvac = current.hvacState.driverTempC
            if (current.hvacState.isPowerOn && current.hvacState.isAcOn) {
                // Cooling towards target setpoint
                val delta = targetHvac - currentCabinTemp
                currentCabinTemp += delta * 0.03f
            } else {
                // Warming up towards ambient outside
                val delta = ambientOutsideTemp - currentCabinTemp
                currentCabinTemp += delta * 0.01f
            }

            // 5. Electric Window & Sunroof Motor Animations (move 5% per tick)
            val wFl = approach(current.cabinState.windowFlPercent, targetWindowFl, 5)
            val wFr = approach(current.cabinState.windowFrPercent, targetWindowFr, 5)
            val wRl = approach(current.cabinState.windowRlPercent, targetWindowRl, 5)
            val wRr = approach(current.cabinState.windowRrPercent, targetWindowRr, 5)
            val sRoof = approach(current.cabinState.sunroofPercent, targetSunroof, 4)
            val sShade = approach(current.cabinState.roofShadePercent, targetShade, 4)

            // 6. TPMS dynamics (slight tire pressure & heat increase with speed)
            val tireHeat = (32 + (speed / 10f).toInt()).coerceIn(32, 42)
            val pressureFactor = 1.0f + (tireHeat - 32) * 0.005f

            current.copy(
                speedKmh = speed,
                gear = gear,
                odometerKm = newOdometer,
                usableKwh = newUsableKwh,
                batteryPercent = newBatteryPercent,
                evRangeKm = newEvRangeKm,
                chargePowerKw = if (current.isCharging) 60.0f else 0.0f,
                latitude = newLat,
                longitude = newLng,
                headingDegrees = heading,
                cabinState = current.cabinState.copy(
                    windowFlPercent = wFl,
                    windowFrPercent = wFr,
                    windowRlPercent = wRl,
                    windowRrPercent = wRr,
                    sunroofPercent = sRoof,
                    roofShadePercent = sShade
                ),
                tpmsState = current.tpmsState.copy(
                    flBar = (2.40f * pressureFactor),
                    frBar = (2.42f * pressureFactor),
                    rlBar = (2.38f * pressureFactor),
                    rrBar = (2.41f * pressureFactor),
                    flTempC = tireHeat,
                    frTempC = tireHeat + 1,
                    rlTempC = tireHeat - 1,
                    rrTempC = tireHeat
                ),
                timestampMs = System.currentTimeMillis()
            )
        }
    }

    private fun approach(current: Int, target: Int, step: Int): Int {
        return if (abs(target - current) <= step) {
            target
        } else if (target > current) {
            current + step
        } else {
            current - step
        }
    }

    // =========================================================================
    // Control Implementations
    // =========================================================================

    override suspend fun setHvacPower(on: Boolean): Result<Unit> {
        _telemetryState.update { it.copy(hvacState = it.hvacState.copy(isPowerOn = on)) }
        snifferEventListener?.invoke(0x15200500, 1, on, "SIMULATOR_HVAC_POWER")
        return Result.success(Unit)
    }

    override suspend fun setTemperature(zone: Zone, tempC: Float): Result<Unit> {
        val clamped = tempC.coerceIn(16.0f, 30.0f)
        _telemetryState.update {
            val h = if (zone == Zone.PASSENGER) it.hvacState.copy(passengerTempC = clamped)
            else it.hvacState.copy(driverTempC = clamped)
            it.copy(hvacState = h)
        }
        val area = if (zone == Zone.PASSENGER) 4 else 1
        snifferEventListener?.invoke(0x15600503, area, clamped, "SIMULATOR_HVAC_TEMP")
        return Result.success(Unit)
    }

    override suspend fun setFanSpeed(level: Int): Result<Unit> {
        val clamped = level.coerceIn(1, 7)
        _telemetryState.update { it.copy(hvacState = it.hvacState.copy(fanSpeed = clamped)) }
        snifferEventListener?.invoke(0x15400500, 1, clamped, "SIMULATOR_FAN_SPEED")
        return Result.success(Unit)
    }

    override suspend fun setAcMode(
        ac: Boolean?,
        auto: Boolean?,
        maxAc: Boolean?,
        recirc: Boolean?,
        frontDefrost: Boolean?,
        rearDefrost: Boolean?
    ): Result<Unit> {
        _telemetryState.update { s ->
            s.copy(
                hvacState = s.hvacState.copy(
                    isAcOn = ac ?: s.hvacState.isAcOn,
                    isAutoOn = auto ?: s.hvacState.isAutoOn,
                    isMaxAcOn = maxAc ?: s.hvacState.isMaxAcOn,
                    isRecircOn = recirc ?: s.hvacState.isRecircOn,
                    isFrontDefrostOn = frontDefrost ?: s.hvacState.isFrontDefrostOn,
                    isRearDefrostOn = rearDefrost ?: s.hvacState.isRearDefrostOn
                )
            )
        }
        ac?.let { snifferEventListener?.invoke(0x15200505, 1, it, "SIMULATOR_AC") }
        return Result.success(Unit)
    }

    override suspend fun setWindowPosition(window: WindowTarget, openPercent: Int): Result<Unit> {
        val clamped = openPercent.coerceIn(0, 100)
        when (window) {
            WindowTarget.FRONT_LEFT -> targetWindowFl = clamped
            WindowTarget.FRONT_RIGHT -> targetWindowFr = clamped
            WindowTarget.REAR_LEFT -> targetWindowRl = clamped
            WindowTarget.REAR_RIGHT -> targetWindowRr = clamped
            WindowTarget.ALL -> {
                targetWindowFl = clamped
                targetWindowFr = clamped
                targetWindowRl = clamped
                targetWindowRr = clamped
            }
        }
        snifferEventListener?.invoke(0x13400BC0, 1, clamped, "SIMULATOR_WINDOW")
        return Result.success(Unit)
    }

    override suspend fun setSunroofAndShade(sunroofPercent: Int?, shadePercent: Int?): Result<Unit> {
        sunroofPercent?.let { targetSunroof = it.coerceIn(0, 100) }
        shadePercent?.let { targetShade = it.coerceIn(0, 100) }
        snifferEventListener?.invoke(0x20000001, 0, targetSunroof, "SIMULATOR_SUNROOF")
        return Result.success(Unit)
    }

    override suspend fun setDoorLocks(locked: Boolean): Result<Unit> {
        _telemetryState.update { it.copy(cabinState = it.cabinState.copy(isDoorsLocked = locked)) }
        snifferEventListener?.invoke(0x16200B02, 0, locked, "SIMULATOR_LOCKS")
        return Result.success(Unit)
    }

    override suspend fun setTailgate(open: Boolean): Result<Unit> {
        if (open) {
            val current = _telemetryState.value
            val isParked = current.gear == GearState.P && current.speedKmh == 0.0f
            if (!isParked) {
                val errMsg = "Safety Interlock: Cannot open tailgate while vehicle is in motion or not in Park! (មិនអាចបើកគូទក្រោយពេលរថយន្តកំពុងបើកបរបានទេ!) [Gear=${current.gear}, Speed=${current.speedKmh} km/h]"
                Log.e(TAG, errMsg)
                return Result.failure(IllegalStateException(errMsg))
            }
        }
        _telemetryState.update { it.copy(cabinState = it.cabinState.copy(isTailgateOpen = open)) }
        snifferEventListener?.invoke(0x20000003, 0, open, "SIMULATOR_TAILGATE")
        return Result.success(Unit)
    }

    override suspend fun setSeatClimate(seat: SeatTarget, ventLevel: Int?, heatLevel: Int?): Result<Unit> {
        _telemetryState.update { s ->
            val isDriver = seat == SeatTarget.DRIVER
            val newCabin = s.cabinState.copy(
                driverSeatVentLevel = if (isDriver) ventLevel?.coerceIn(0, 3) ?: s.cabinState.driverSeatVentLevel else s.cabinState.driverSeatVentLevel,
                passengerSeatVentLevel = if (!isDriver) ventLevel?.coerceIn(0, 3) ?: s.cabinState.passengerSeatVentLevel else s.cabinState.passengerSeatVentLevel,
                driverSeatHeatLevel = if (isDriver) heatLevel?.coerceIn(0, 3) ?: s.cabinState.driverSeatHeatLevel else s.cabinState.driverSeatHeatLevel,
                passengerSeatHeatLevel = if (!isDriver) heatLevel?.coerceIn(0, 3) ?: s.cabinState.passengerSeatHeatLevel else s.cabinState.passengerSeatHeatLevel
            )
            s.copy(cabinState = newCabin)
        }
        snifferEventListener?.invoke(0x20000004, if (seat == SeatTarget.DRIVER) 1 else 4, ventLevel ?: 0, "SIMULATOR_SEAT")
        return Result.success(Unit)
    }

    override suspend fun setAmbientLight(on: Boolean, colorHex: String?): Result<Unit> {
        _telemetryState.update { s ->
            s.copy(
                cabinState = s.cabinState.copy(
                    ambientLightOn = on,
                    ambientLightColorHex = colorHex ?: s.cabinState.ambientLightColorHex
                )
            )
        }
        snifferEventListener?.invoke(0x20000007, 0, on, "SIMULATOR_AMBIENT")
        return Result.success(Unit)
    }

    override suspend fun executeRawProperty(propId: Int, areaId: Int, value: Any): Result<Unit> {
        Log.i(TAG, "Simulated executeRawProperty: propId=0x${Integer.toHexString(propId)}, areaId=$areaId, val=$value")
        snifferEventListener?.invoke(propId, areaId, value, "SIMULATOR_RAW")
        return Result.success(Unit)
    }
}
