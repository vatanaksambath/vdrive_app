package com.khmerauto.nav.hal.wifi

import com.khmerauto.nav.hal.SeatTarget
import com.khmerauto.nav.hal.VehicleTelemetryState
import com.khmerauto.nav.hal.WindowTarget
import com.khmerauto.nav.hal.Zone
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class HalPacketType {
    COMMAND,
    COMMAND_RESPONSE,
    TELEMETRY_UPDATE,
    SNIFFER_EVENT,
    HEARTBEAT
}

@Serializable
data class HalCommandPacket(
    val packetId: String = UUID.randomUUID().toString(),
    val commandType: String,
    val zone: Zone? = null,
    val window: WindowTarget? = null,
    val seat: SeatTarget? = null,
    val targetTempC: Float? = null,
    val fanSpeed: Int? = null,
    val ac: Boolean? = null,
    val auto: Boolean? = null,
    val maxAc: Boolean? = null,
    val recirc: Boolean? = null,
    val frontDefrost: Boolean? = null,
    val rearDefrost: Boolean? = null,
    val sync: Boolean? = null,
    val cleanAir: Boolean? = null,
    val driveMode: String? = null,
    val openPercent: Int? = null,
    val sunroofPercent: Int? = null,
    val shadePercent: Int? = null,
    val stateBool: Boolean? = null,
    val ventLevel: Int? = null,
    val heatLevel: Int? = null,
    val colorHex: String? = null,
    val propId: Int? = null,
    val areaId: Int? = null,
    val rawValue: String? = null,
    val rawType: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

@Serializable
data class HalResponsePacket(
    val packetId: String,
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

@Serializable
data class HalTelemetryPacket(
    val vehicleModel: String = "Geely Galaxy M7 (Flyme Auto)",
    val telemetryState: VehicleTelemetryState,
    val hostIp: String = "192.168.43.1",
    val timestampMs: Long = System.currentTimeMillis()
)

@Serializable
data class HalSnifferPacket(
    val propId: Int,
    val areaId: Int,
    val value: String,
    val source: String, // "ECARX", "AOSP", "SHELL_ROOT"
    val timestampMs: Long = System.currentTimeMillis()
)
