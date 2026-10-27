package com.khmerauto.nav.hal.wifi

import android.util.Log
import com.khmerauto.nav.hal.VehicleHalContract
import com.khmerauto.nav.hal.WindowTarget
import com.khmerauto.nav.hal.Zone
import com.khmerauto.nav.hal.SeatTarget
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.Inet4Address
import java.net.NetworkInterface
import java.time.Duration
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "CarHalWebSocketServer"

/**
 * Embedded Ktor WebSocket & REST Bridge Server.
 * Runs on port 8888 on the Geely Galaxy M7 head unit (or Simulator host).
 *
 * Provides:
 * 1. WebSocket /ws/hal: 250ms reactive telemetry streaming + instant command execution from tablet.
 * 2. Real-time sniffer event distribution to all paired tablets.
 * 3. REST GET /api/status: Lightweight handshake for tablet auto-discovery.
 */
class CarHalWebSocketServer(
    private val vehicleHal: VehicleHalContract,
    val port: Int = 8888
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var engine: ApplicationEngine? = null

    private val connectedSessions = Collections.newSetFromMap(ConcurrentHashMap<WebSocketSession, Boolean>())

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var isRunning: Boolean = false
        private set

    fun start() {
        if (isRunning) return
        Log.i(TAG, "Starting Car HAL WebSocket Bridge Server on port $port...")

        try {
            engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                install(WebSockets) {
                    pingPeriod = Duration.ofSeconds(15)
                    timeout = Duration.ofSeconds(30)
                    maxFrameSize = Long.MAX_VALUE
                    masking = false
                }
                install(ContentNegotiation) {
                    json(json)
                }

                routing {
                    get("/api/status") {
                        call.respond(
                            mapOf(
                                "vehicle" to "2026 Geely Galaxy M7 (Flyme Auto / ECARX)",
                                "status" to "ONLINE",
                                "port" to port.toString(),
                                "clientsConnected" to connectedSessions.size.toString(),
                                "timestamp" to System.currentTimeMillis().toString()
                            )
                        )
                    }

                    webSocket("/ws/hal") {
                        Log.i(TAG, "New Tablet Remote connected from: ${call.request.local.remoteHost}")
                        connectedSessions.add(this)

                        // Immediately send current state snapshot
                        val initialTelemetry = HalTelemetryPacket(
                            telemetryState = vehicleHal.telemetryState.value,
                            hostIp = getLocalIpAddress()
                        )
                        send(Frame.Text(json.encodeToString(initialTelemetry)))

                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    val text = frame.readText()
                                    handleIncomingCommand(text, this)
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "WebSocket session exception: ${e.message}")
                        } finally {
                            connectedSessions.remove(this)
                            Log.i(TAG, "Tablet Remote disconnected. Active clients: ${connectedSessions.size}")
                        }
                    }
                }
            }.start(wait = false)

            isRunning = true

            // Launch background broadcast loop (250ms cadence and on state updates)
            startTelemetryBroadcastLoop()

            Log.i(TAG, "Car HAL Server successfully bound on port $port. Local IP: ${getLocalIpAddress()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting Car HAL Server: ${e.message}", e)
            isRunning = false
        }
    }

    fun stop() {
        if (!isRunning) return
        Log.i(TAG, "Stopping Car HAL WebSocket Server...")
        isRunning = false
        scope.cancel()
        engine?.stop(1000, 2000)
        engine = null
        connectedSessions.clear()
    }

    /**
     * Broadcasts live VHAL sniffer change events to all connected tablets.
     */
    fun broadcastSnifferEvent(propId: Int, areaId: Int, value: Any, source: String) {
        if (connectedSessions.isEmpty()) return
        scope.launch {
            try {
                val packet = HalSnifferPacket(
                    propId = propId,
                    areaId = areaId,
                    value = value.toString(),
                    source = source
                )
                val text = json.encodeToString(packet)
                val frame = Frame.Text(text)
                connectedSessions.forEach { session ->
                    try { session.send(frame) } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.v(TAG, "Error broadcasting sniffer event: ${e.message}")
            }
        }
    }

    private fun startTelemetryBroadcastLoop() {
        scope.launch {
            while (isActive && isRunning) {
                broadcastCurrentTelemetry()
                delay(250) // 250ms continuous refresh rate
            }
        }

        // Also react immediately to state updates
        scope.launch {
            vehicleHal.telemetryState.collectLatest {
                broadcastCurrentTelemetry()
            }
        }
    }

    private suspend fun broadcastCurrentTelemetry() {
        if (connectedSessions.isEmpty()) return
        try {
            val packet = HalTelemetryPacket(
                telemetryState = vehicleHal.telemetryState.value,
                hostIp = getLocalIpAddress()
            )
            val jsonText = json.encodeToString(packet)
            val frame = Frame.Text(jsonText)
            connectedSessions.forEach { session ->
                try {
                    session.send(frame)
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.v(TAG, "broadcastCurrentTelemetry: ${e.message}")
        }
    }

    private suspend fun handleIncomingCommand(text: String, session: WebSocketSession) {
        try {
            val cmd = json.decodeFromString<HalCommandPacket>(text)
            Log.d(TAG, "Executing received tablet command: ${cmd.commandType} (ID=${cmd.packetId})")

            val executionResult: Result<Unit> = when (cmd.commandType) {
                "SET_HVAC_POWER" -> vehicleHal.setHvacPower(cmd.stateBool ?: true)
                "SET_TEMPERATURE" -> vehicleHal.setTemperature(cmd.zone ?: Zone.DRIVER, cmd.targetTempC ?: 22.0f)
                "SET_FAN_SPEED" -> vehicleHal.setFanSpeed(cmd.fanSpeed ?: 3)
                "SET_AC_MODE" -> vehicleHal.setAcMode(
                    ac = cmd.ac,
                    auto = cmd.auto,
                    maxAc = cmd.maxAc,
                    recirc = cmd.recirc,
                    frontDefrost = cmd.frontDefrost,
                    rearDefrost = cmd.rearDefrost,
                    sync = cmd.sync,
                    cleanAir = cmd.cleanAir
                )
                "SET_DRIVE_MODE" -> {
                    val m = cmd.driveMode?.let { runCatching { com.khmerauto.nav.hal.GeelyDriveMode.valueOf(it) }.getOrNull() }
                        ?: com.khmerauto.nav.hal.GeelyDriveMode.SMART
                    vehicleHal.setDriveMode(m)
                }
                "SET_WINDOW_POS" -> vehicleHal.setWindowPosition(cmd.window ?: WindowTarget.FRONT_LEFT, cmd.openPercent ?: 0)
                "SET_SUNROOF_SHADE" -> vehicleHal.setSunroofAndShade(cmd.sunroofPercent, cmd.shadePercent)
                "SET_DOOR_LOCKS" -> vehicleHal.setDoorLocks(cmd.stateBool ?: true)
                "SET_TAILGATE" -> vehicleHal.setTailgate(cmd.stateBool ?: false)
                "SET_SEAT_CLIMATE" -> vehicleHal.setSeatClimate(cmd.seat ?: SeatTarget.DRIVER, cmd.ventLevel, cmd.heatLevel)
                "SET_AMBIENT_LIGHT" -> vehicleHal.setAmbientLight(cmd.stateBool ?: true, cmd.colorHex)
                "EXECUTE_RAW_PROPERTY" -> {
                    val pId = cmd.propId ?: 0
                    val aId = cmd.areaId ?: 0
                    val rawVal: Any = when (cmd.rawType?.uppercase()) {
                        "FLOAT" -> cmd.rawValue?.toFloatOrNull() ?: 0f
                        "BOOLEAN" -> cmd.rawValue?.toBoolean() ?: false
                        else -> cmd.rawValue?.toIntOrNull() ?: 0
                    }
                    vehicleHal.executeRawProperty(pId, aId, rawVal)
                }
                else -> Result.failure(IllegalArgumentException("Unknown command: ${cmd.commandType}"))
            }

            val response = HalResponsePacket(
                packetId = cmd.packetId,
                isSuccess = executionResult.isSuccess,
                errorMessage = executionResult.exceptionOrNull()?.message
            )
            session.send(Frame.Text(json.encodeToString(response)))

        } catch (e: Exception) {
            Log.e(TAG, "Error processing incoming command: ${e.message}", e)
            val errResponse = HalResponsePacket(
                packetId = "UNKNOWN",
                isSuccess = false,
                errorMessage = e.message
            )
            session.send(Frame.Text(json.encodeToString(errResponse)))
        }
    }

    companion object {
        fun getLocalIpAddress(): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    // Check wlan (Wi-Fi client or Wi-Fi hotspot ap0/wlan0)
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val inetAddress = addresses.nextElement()
                        if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                            return inetAddress.hostAddress ?: "127.0.0.1"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.v(TAG, "Unable to get local IP: ${e.message}")
            }
            return "192.168.43.1" // Standard Android Wi-Fi Hotspot Gateway IP
        }
    }
}
