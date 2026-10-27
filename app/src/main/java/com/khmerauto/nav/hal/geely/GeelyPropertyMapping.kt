package com.khmerauto.nav.hal.geely

import android.os.Environment
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private const val TAG = "GeelyPropertyMapping"

@Serializable
enum class PropertyDataType {
    INT, FLOAT, BOOLEAN, STRING
}

@Serializable
data class PropertyConfigEntry(
    val actionName: String,
    val propId: Int,
    val areaId: Int = 0,
    val dataType: PropertyDataType = PropertyDataType.INT,
    val ecarxFunctionId: Int = 0,
    val ecarxZone: Int = 0,
    val scaleFactor: Float = 1.0f,
    val offset: Float = 0.0f,
    val description: String = ""
)

@Serializable
data class GeelyPropertyMapping(
    val vehicleModel: String = "Geely Galaxy M7 (Flyme Auto / ECARX)",
    val schemaVersion: Int = 1,
    val lastUpdated: Long = System.currentTimeMillis(),
    val mappings: Map<String, PropertyConfigEntry> = defaultMappings()
) {
    companion object {
        const val CONFIG_FILE_PATH = "/sdcard/kmautonav/geely_m7_vhal_map.json"

        private val jsonParser = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun loadFromSdcard(): GeelyPropertyMapping {
            val file = File(CONFIG_FILE_PATH)
            if (file.exists()) {
                try {
                    val content = file.readText()
                    val parsed = jsonParser.decodeFromString<GeelyPropertyMapping>(content)
                    Log.i(TAG, "Loaded dynamic Geely M7 VHAL mapping with ${parsed.mappings.size} rules from $CONFIG_FILE_PATH")
                    return parsed
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse $CONFIG_FILE_PATH, falling back to defaults: ${e.message}", e)
                }
            } else {
                Log.w(TAG, "$CONFIG_FILE_PATH not found. Creating default seed file...")
                val defaultMap = GeelyPropertyMapping()
                saveToSdcard(defaultMap)
                return defaultMap
            }
            return GeelyPropertyMapping()
        }

        fun saveToSdcard(mapping: GeelyPropertyMapping): Boolean {
            return try {
                val file = File(CONFIG_FILE_PATH)
                file.parentFile?.mkdirs()
                val jsonString = jsonParser.encodeToString(mapping)
                file.writeText(jsonString)
                Log.i(TAG, "Successfully persisted Geely M7 mapping to $CONFIG_FILE_PATH (${mapping.mappings.size} actions)")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing to $CONFIG_FILE_PATH: ${e.message}", e)
                false
            }
        }

        fun defaultMappings(): Map<String, PropertyConfigEntry> {
            val map = mutableMapOf<String, PropertyConfigEntry>()

            // Telemetry & Powertrain
            map["ODOMETER"] = PropertyConfigEntry(
                actionName = "ODOMETER",
                propId = 0x11600204, // PERF_ODOMETER
                areaId = 0,
                dataType = PropertyDataType.FLOAT,
                description = "Hardware total distance traveled in km"
            )
            map["SPEED"] = PropertyConfigEntry(
                actionName = "SPEED",
                propId = 0x11600207, // PERF_VEHICLE_SPEED
                areaId = 0,
                dataType = PropertyDataType.FLOAT,
                scaleFactor = 3.6f, // m/s to km/h
                description = "Instantaneous vehicle speed in km/h"
            )
            map["GEAR"] = PropertyConfigEntry(
                actionName = "GEAR",
                propId = 0x11400400, // GEAR_SELECTION
                areaId = 0,
                dataType = PropertyDataType.INT,
                description = "Transmission gear: 1=P, 2=R, 4=N, 8=D"
            )
            map["BATTERY_LEVEL"] = PropertyConfigEntry(
                actionName = "BATTERY_LEVEL",
                propId = 0x11600309, // EV_BATTERY_LEVEL
                areaId = 0,
                dataType = PropertyDataType.FLOAT,
                description = "PHEV Battery state of charge (SoC) %"
            )
            map["CHARGE_RATE"] = PropertyConfigEntry(
                actionName = "CHARGE_RATE",
                propId = 0x1160030C, // EV_BATTERY_INSTANTANEOUS_CHARGE_RATE
                areaId = 0,
                dataType = PropertyDataType.FLOAT,
                description = "Charging power in milliwatts/kW"
            )

            // HVAC Dual-Zone
            map["HVAC_POWER"] = PropertyConfigEntry(
                actionName = "HVAC_POWER",
                propId = 0x15200500,
                areaId = 1,
                dataType = PropertyDataType.BOOLEAN,
                ecarxFunctionId = 0x1001,
                ecarxZone = 0,
                description = "HVAC Master System Power"
            )
            map["TEMP_DRIVER"] = PropertyConfigEntry(
                actionName = "TEMP_DRIVER",
                propId = 0x15600503, // HVAC_TEMPERATURE_SET
                areaId = 1, // Area 1 = Driver Row 1 Left
                dataType = PropertyDataType.FLOAT,
                ecarxFunctionId = 0x1002,
                ecarxZone = 1,
                description = "Driver side temperature setpoint (°C)"
            )
            map["TEMP_PASSENGER"] = PropertyConfigEntry(
                actionName = "TEMP_PASSENGER",
                propId = 0x15600503,
                areaId = 4, // Area 4 = Row 1 Right
                dataType = PropertyDataType.FLOAT,
                ecarxFunctionId = 0x1002,
                ecarxZone = 2,
                description = "Passenger side temperature setpoint (°C)"
            )
            map["FAN_SPEED"] = PropertyConfigEntry(
                actionName = "FAN_SPEED",
                propId = 0x15400500, // HVAC_FAN_SPEED
                areaId = 1,
                dataType = PropertyDataType.INT,
                ecarxFunctionId = 0x1003,
                ecarxZone = 0,
                description = "Main blower fan level (1..7)"
            )
            map["AC_SWITCH"] = PropertyConfigEntry(
                actionName = "AC_SWITCH",
                propId = 0x15200505, // HVAC_AC_ON
                areaId = 1,
                dataType = PropertyDataType.BOOLEAN,
                ecarxFunctionId = 0x1004,
                ecarxZone = 0,
                description = "A/C Compressor Clutch Power"
            )
            map["AUTO_SWITCH"] = PropertyConfigEntry(
                actionName = "AUTO_SWITCH",
                propId = 0x1520050A, // HVAC_AUTO_ON
                areaId = 1,
                dataType = PropertyDataType.BOOLEAN,
                ecarxFunctionId = 0x1005,
                description = "Automatic Climate Regulation"
            )
            map["RECIRC_SWITCH"] = PropertyConfigEntry(
                actionName = "RECIRC_SWITCH",
                propId = 0x15200508, // HVAC_RECIRC_ON
                areaId = 1,
                dataType = PropertyDataType.BOOLEAN,
                ecarxFunctionId = 0x1006,
                description = "Air Recirculation / Fresh Air Flap"
            )
            map["DEFROST_FRONT"] = PropertyConfigEntry(
                actionName = "DEFROST_FRONT",
                propId = 0x13200504, // HVAC_DEFROST
                areaId = 1,
                dataType = PropertyDataType.BOOLEAN,
                ecarxFunctionId = 0x1007,
                description = "Windshield Defroster Max Flow"
            )

            // Windows & Panoramic Sunroof
            map["WINDOW_FL"] = PropertyConfigEntry(
                actionName = "WINDOW_FL",
                propId = 0x13400BC0, // WINDOW_POS
                areaId = 1, // Front Left
                dataType = PropertyDataType.INT,
                ecarxFunctionId = 0x2001,
                ecarxZone = 1,
                description = "Front Left Window (0=Closed, 100=Open)"
            )
            map["WINDOW_FR"] = PropertyConfigEntry(
                actionName = "WINDOW_FR",
                propId = 0x13400BC0,
                areaId = 4, // Front Right
                dataType = PropertyDataType.INT,
                ecarxFunctionId = 0x2002,
                ecarxZone = 2,
                description = "Front Right Window (0=Closed, 100=Open)"
            )
            map["WINDOW_RL"] = PropertyConfigEntry(
                actionName = "WINDOW_RL",
                propId = 0x13400BC0,
                areaId = 16, // Rear Left
                dataType = PropertyDataType.INT,
                ecarxFunctionId = 0x2003,
                ecarxZone = 3,
                description = "Rear Left Window (0=Closed, 100=Open)"
            )
            map["WINDOW_RR"] = PropertyConfigEntry(
                actionName = "WINDOW_RR",
                propId = 0x13400BC0,
                areaId = 64, // Rear Right
                dataType = PropertyDataType.INT,
                ecarxFunctionId = 0x2004,
                ecarxZone = 4,
                description = "Rear Right Window (0=Closed, 100=Open)"
            )
            map["SUNROOF"] = PropertyConfigEntry(
                actionName = "SUNROOF",
                propId = 0x20000001, // Vendor Sunroof
                areaId = 0,
                dataType = PropertyDataType.INT,
                ecarxFunctionId = 0x2005,
                description = "Panoramic Sunroof Glass Position (0..100)"
            )
            map["SUNSHADE"] = PropertyConfigEntry(
                actionName = "SUNSHADE",
                propId = 0x20000002, // Vendor Sunshade
                areaId = 0,
                dataType = PropertyDataType.INT,
                ecarxFunctionId = 0x2006,
                description = "Sunroof Electric Roller Shade (0..100)"
            )

            // Doors & Tailgate
            map["CENTRAL_LOCK"] = PropertyConfigEntry(
                actionName = "CENTRAL_LOCK",
                propId = 0x16200B02, // DOOR_LOCK
                areaId = 0,
                dataType = PropertyDataType.BOOLEAN,
                ecarxFunctionId = 0x2008,
                description = "Central Door Locking State (true=Locked)"
            )
            map["TAILGATE"] = PropertyConfigEntry(
                actionName = "TAILGATE",
                propId = 0x20000003, // Vendor Tailgate
                areaId = 0,
                dataType = PropertyDataType.BOOLEAN,
                ecarxFunctionId = 0x2007,
                description = "Geely M7 Powered Smart Tailgate (true=Open)"
            )

            // Seat Ventilation & Heating
            map["SEAT_VENT_FL"] = PropertyConfigEntry(
                actionName = "SEAT_VENT_FL",
                propId = 0x20000004,
                areaId = 1,
                dataType = PropertyDataType.INT,
                ecarxFunctionId = 0x3001,
                description = "Driver Seat Ventilation Level (0..3)"
            )
            map["SEAT_VENT_FR"] = PropertyConfigEntry(
                actionName = "SEAT_VENT_FR",
                propId = 0x20000005,
                areaId = 4,
                dataType = PropertyDataType.INT,
                ecarxFunctionId = 0x3002,
                description = "Passenger Seat Ventilation Level (0..3)"
            )
            map["SEAT_HEAT_FL"] = PropertyConfigEntry(
                actionName = "SEAT_HEAT_FL",
                propId = 0x20000006,
                areaId = 1,
                dataType = PropertyDataType.INT,
                ecarxFunctionId = 0x3003,
                description = "Driver Seat Heating Level (0..3)"
            )

            // Ambient Light
            map["AMBIENT_LIGHT_SWITCH"] = PropertyConfigEntry(
                actionName = "AMBIENT_LIGHT_SWITCH",
                propId = 0x20000007,
                areaId = 0,
                dataType = PropertyDataType.BOOLEAN,
                ecarxFunctionId = 0x4001,
                description = "Geely M7 256-Color Ambient Light Power"
            )
            map["AMBIENT_LIGHT_COLOR"] = PropertyConfigEntry(
                actionName = "AMBIENT_LIGHT_COLOR",
                propId = 0x20000008,
                areaId = 0,
                dataType = PropertyDataType.INT,
                ecarxFunctionId = 0x4002,
                description = "Ambient Light Hex/Index Color"
            )

            // TPMS 4 Wheels
            map["TPMS_PRESSURE_FL"] = PropertyConfigEntry(
                actionName = "TPMS_PRESSURE_FL",
                propId = 0x17600309, // TIRE_PRESSURE
                areaId = 1,
                dataType = PropertyDataType.FLOAT,
                description = "Front Left Tire Pressure in Bar"
            )

            return map
        }
    }
}
