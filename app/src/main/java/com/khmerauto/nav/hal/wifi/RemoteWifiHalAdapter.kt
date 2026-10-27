package com.khmerauto.nav.hal.wifi

import android.util.Log
import com.khmerauto.nav.hal.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "RemoteWifiHalAdapter"

enum class WifiConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

/**
 * Remote Vehicle HAL Adapter running on the external Android Tablet.
 * Connects over the Geely Galaxy M7's Wi-Fi Hotspot to ws://<car-ip>:8888/ws/hal.
 *
 * Implements VehicleHalContract transparently so any UI interaction or Khmer voice
 * command executed on the tablet controls the physical Geely Galaxy M7 car hardware.
 */
class RemoteWifiHalAdapter(
    var carIp: String = "192.168.43.1",
    var carPort: Int = 8888
) : VehicleHalContract {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _telemetryState = MutableStateFlow(VehicleTelemetryState())
    override val telemetryState: StateFlow<VehicleTelemetryState> = _telemetryState.asStateFlow()

    private val _connectionState = MutableStateFlow(WifiConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WifiConnectionState> = _connectionState.asStateFlow()

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val httpClient = HttpClient(OkHttp) {
        install(WebSockets) {
            pingInterval = 10_000
        }
    }

    private var activeSession: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null
    private var isShouldBeConnected = false

    // Pending command completions (packetId -> CompletableDeferred<HalResponsePacket>)
    private val pendingCommands = ConcurrentHashMap<String, CompletableDeferred<HalResponsePacket>>()

    // Sniffer event forwarder
    var onSnifferPacketReceived: ((HalSnifferPacket) -> Unit)? = null

    override fun start() {
        if (isShouldBeConnected) return
        isShouldBeConnected = true
        Log.i(TAG, "Starting Remote Wi-Fi HAL Adapter for Geely Galaxy M7 ($carIp:$carPort)...")
        launchConnectionLoop()
    }

    override fun stop() {
        Log.i(TAG, "Stopping Remote Wi-Fi HAL Adapter...")
        isShouldBeConnected = false
        connectionJob?.cancel()
        scope.launch {
            try {
                activeSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Client stopping"))
            } catch (_: Exception) {}
            activeSession = null
            _connectionState.value = WifiConnectionState.DISCONNECTED
        }
    }

    fun updateHost(ip: String, port: Int = 8888) {
        carIp = ip.trim()
        carPort = port
        if (isShouldBeConnected) {
            connectionJob?.cancel()
            launchConnectionLoop()
        }
    }

    private fun launchConnectionLoop() {
        connectionJob = scope.launch {
            var backoffMs = 1000L
            while (isActive && isShouldBeConnected) {
                try {
                    _connectionState.value = WifiConnectionState.CONNECTING
                    val wsUrl = "ws://$carIp:$carPort/ws/hal"
                    Log.i(TAG, "Attempting connection to Geely M7: $wsUrl")

                    httpClient.webSocket(host = carIp, port = carPort, path = "/ws/hal") {
                        activeSession = this
                        _connectionState.value = WifiConnectionState.CONNECTED
                        backoffMs = 1000L
                        Log.i(TAG, "Successfully connected to Geely Galaxy M7 at $wsUrl!")

                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                handleIncomingFrame(frame.readText())
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "WebSocket connection error: ${e.message}")
                    _connectionState.value = WifiConnectionState.RECONNECTING
                } finally {
                    activeSession = null
                }

                if (isShouldBeConnected) {
                    delay(backoffMs)
                    backoffMs = (backoffMs * 1.5).toLong().coerceAtMost(10_000L)
                }
            }
            _connectionState.value = WifiConnectionState.DISCONNECTED
        }
    }

    private fun handleIncomingFrame(text: String) {
        try {
            // Check if it's a Telemetry Packet
            if (text.contains("\"telemetryState\"")) {
                val packet = json.decodeFromString<HalTelemetryPacket>(text)
                _telemetryState.value = packet.telemetryState
                return
            }

            // Check if it's a Command Response Packet
            if (text.contains("\"isSuccess\"")) {
                val response = json.decodeFromString<HalResponsePacket>(text)
                val deferred = pendingCommands.remove(response.packetId)
                deferred?.complete(response)
                return
            }

            // Check if it's a Sniffer Packet
            if (text.contains("\"propId\"") && text.contains("\"source\"")) {
                val snifferPacket = json.decodeFromString<HalSnifferPacket>(text)
                onSnifferPacketReceived?.invoke(snifferPacket)
                return
            }
        } catch (e: Exception) {
            Log.v(TAG, "handleIncomingFrame decode error: ${e.message}")
        }
    }

    private suspend fun sendCommand(cmd: HalCommandPacket): Result<Unit> {
        val session = activeSession ?: return Result.failure(IllegalStateException("Not connected to Geely Galaxy M7"))
        val deferred = CompletableDeferred<HalResponsePacket>()
        pendingCommands[cmd.packetId] = deferred

        return try {
            val text = json.encodeToString(cmd)
            session.send(Frame.Text(text))

            // Wait for car response with 3.5 second timeout
            withTimeout(3500) {
                val response = deferred.await()
                if (response.isSuccess) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(response.errorMessage ?: "Car command rejected"))
                }
            }
        } catch (e: TimeoutCancellationException) {
            pendingCommands.remove(cmd.packetId)
            Result.failure(Exception("Command timed out waiting for Geely M7 head unit response"))
        } catch (e: Exception) {
            pendingCommands.remove(cmd.packetId)
            Result.failure(e)
        }
    }

    // =========================================================================
    // VehicleHalContract Suspend Functions (Routed over Wi-Fi)
    // =========================================================================

    override suspend fun setHvacPower(on: Boolean): Result<Unit> {
        return sendCommand(HalCommandPacket(commandType = "SET_HVAC_POWER", stateBool = on))
    }

    override suspend fun setTemperature(zone: Zone, tempC: Float): Result<Unit> {
        return sendCommand(HalCommandPacket(commandType = "SET_TEMPERATURE", zone = zone, targetTempC = tempC))
    }

    override suspend fun setFanSpeed(level: Int): Result<Unit> {
        return sendCommand(HalCommandPacket(commandType = "SET_FAN_SPEED", fanSpeed = level))
    }

    override suspend fun setAcMode(
        ac: Boolean?,
        auto: Boolean?,
        maxAc: Boolean?,
        recirc: Boolean?,
        frontDefrost: Boolean?,
        rearDefrost: Boolean?
    ): Result<Unit> {
        return sendCommand(
            HalCommandPacket(
                commandType = "SET_AC_MODE",
                ac = ac,
                auto = auto,
                maxAc = maxAc,
                recirc = recirc,
                frontDefrost = frontDefrost,
                rearDefrost = rearDefrost
            )
        )
    }

    override suspend fun setWindowPosition(window: WindowTarget, openPercent: Int): Result<Unit> {
        return sendCommand(HalCommandPacket(commandType = "SET_WINDOW_POS", window = window, openPercent = openPercent))
    }

    override suspend fun setSunroofAndShade(sunroofPercent: Int?, shadePercent: Int?): Result<Unit> {
        return sendCommand(
            HalCommandPacket(
                commandType = "SET_SUNROOF_SHADE",
                sunroofPercent = sunroofPercent,
                shadePercent = shadePercent
            )
        )
    }

    override suspend fun setDoorLocks(locked: Boolean): Result<Unit> {
        return sendCommand(HalCommandPacket(commandType = "SET_DOOR_LOCKS", stateBool = locked))
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
        return sendCommand(HalCommandPacket(commandType = "SET_TAILGATE", stateBool = open))
    }

    override suspend fun setSeatClimate(seat: SeatTarget, ventLevel: Int?, heatLevel: Int?): Result<Unit> {
        return sendCommand(
            HalCommandPacket(
                commandType = "SET_SEAT_CLIMATE",
                seat = seat,
                ventLevel = ventLevel,
                heatLevel = heatLevel
            )
        )
    }

    override suspend fun setAmbientLight(on: Boolean, colorHex: String?): Result<Unit> {
        return sendCommand(HalCommandPacket(commandType = "SET_AMBIENT_LIGHT", stateBool = on, colorHex = colorHex))
    }

    override suspend fun executeRawProperty(propId: Int, areaId: Int, value: Any): Result<Unit> {
        val typeStr = when (value) {
            is Float -> "FLOAT"
            is Boolean -> "BOOLEAN"
            else -> "INT"
        }
        return sendCommand(
            HalCommandPacket(
                commandType = "EXECUTE_RAW_PROPERTY",
                propId = propId,
                areaId = areaId,
                rawValue = value.toString(),
                rawType = typeStr
            )
        )
    }
}
