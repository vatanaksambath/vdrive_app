package com.khmerauto.nav.hal.sniffer

import android.content.Context
import android.util.Log
import com.khmerauto.nav.hal.geely.GeelyPropertyMapping
import com.khmerauto.nav.hal.geely.PropertyConfigEntry
import com.khmerauto.nav.hal.geely.PropertyDataType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "VhalSnifferEngine"

data class SnifferPropertyItem(
    val propId: Int,
    val propIdHex: String = "0x" + Integer.toHexString(propId).uppercase(),
    val areaId: Int = 0,
    val source: String = "AOSP",
    val dataType: PropertyDataType = PropertyDataType.INT,
    val currentValue: String = "0",
    val previousValue: String? = null,
    val lastChangedTimestamp: Long = System.currentTimeMillis(),
    val isRecentlyChanged: Boolean = false,
    val assignedAction: String? = null
)

/**
 * Real-Time Geely Galaxy M7 VHAL & ECARX Property Sniffer Engine.
 *
 * Inspects all active hardware registers. When a driver presses a physical button
 * (AC temp knob, window switch, door lock, sunroof tilt) in the Geely M7, this engine
 * intercepts the change, triggers a glowing green UI flash, and allows 1-tap assignment
 * to dynamic JSON mapping (/sdcard/kmautonav/geely_m7_vhal_map.json).
 */
class VhalSnifferEngine(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val itemMap = ConcurrentHashMap<String, SnifferPropertyItem>()

    private val _propertiesList = MutableStateFlow<List<SnifferPropertyItem>>(emptyList())
    val propertiesList: StateFlow<List<SnifferPropertyItem>> = _propertiesList.asStateFlow()

    private val _isSniffing = MutableStateFlow(true)
    val isSniffing: StateFlow<Boolean> = _isSniffing.asStateFlow()

    private var activeMapping = GeelyPropertyMapping()

    init {
        loadInitialDefinitions()
    }

    private fun loadInitialDefinitions() {
        activeMapping = GeelyPropertyMapping.loadFromSdcard()

        // Pre-populate with mapped properties
        activeMapping.mappings.forEach { (action, config) ->
            val key = "${config.propId}_${config.areaId}"
            itemMap[key] = SnifferPropertyItem(
                propId = config.propId,
                areaId = config.areaId,
                source = if (config.ecarxFunctionId > 0) "ECARX" else "AOSP",
                dataType = config.dataType,
                currentValue = "INITIALIZED",
                assignedAction = action
            )
        }
        refreshList()
    }

    fun startSniffing() {
        _isSniffing.value = true
        Log.i(TAG, "VHAL Sniffer active for Geely Galaxy M7.")
    }

    fun stopSniffing() {
        _isSniffing.value = false
        Log.i(TAG, "VHAL Sniffer paused.")
    }

    /**
     * Called whenever a property event is captured from ECARX AdaptAPI,
     * AOSP CarPropertyManager, or root shell polling.
     */
    fun recordEvent(propId: Int, areaId: Int, value: Any, source: String) {
        if (!_isSniffing.value) return

        val key = "${propId}_$areaId"
        val existing = itemMap[key]
        val valStr = value.toString()

        val inferredType = when (value) {
            is Float, is Double -> PropertyDataType.FLOAT
            is Boolean -> PropertyDataType.BOOLEAN
            else -> PropertyDataType.INT
        }

        val hasChanged = existing?.currentValue != valStr

        val updated = SnifferPropertyItem(
            propId = propId,
            areaId = areaId,
            source = source,
            dataType = existing?.dataType ?: inferredType,
            currentValue = valStr,
            previousValue = existing?.currentValue,
            lastChangedTimestamp = System.currentTimeMillis(),
            isRecentlyChanged = hasChanged,
            assignedAction = existing?.assignedAction ?: findAssignedAction(propId, areaId)
        )

        itemMap[key] = updated
        refreshList()

        if (hasChanged) {
            // Schedule clearing the glowing green highlight after 2.5 seconds
            scope.launch {
                delay(2500)
                val cur = itemMap[key]
                if (cur != null && cur.lastChangedTimestamp == updated.lastChangedTimestamp) {
                    itemMap[key] = cur.copy(isRecentlyChanged = false)
                    refreshList()
                }
            }
        }
    }

    /**
     * Binds a detected hardware property directly to a cabin action
     * and persists it into /sdcard/kmautonav/geely_m7_vhal_map.json.
     */
    fun assignToAction(
        propId: Int,
        areaId: Int,
        actionName: String,
        dataType: PropertyDataType = PropertyDataType.INT,
        description: String = "Mapped via VHAL Sniffer UI"
    ): Boolean {
        try {
            val key = "${propId}_$areaId"
            val updatedMap = activeMapping.mappings.toMutableMap()

            val isEcarx = propId < 0x10000 // ECARX function IDs are typically 16-bit
            val entry = PropertyConfigEntry(
                actionName = actionName,
                propId = if (isEcarx) 0 else propId,
                areaId = areaId,
                dataType = dataType,
                ecarxFunctionId = if (isEcarx) propId else 0,
                ecarxZone = areaId,
                description = description
            )

            updatedMap[actionName] = entry
            val newMapping = activeMapping.copy(
                lastUpdated = System.currentTimeMillis(),
                mappings = updatedMap
            )

            val saved = GeelyPropertyMapping.saveToSdcard(newMapping)
            if (saved) {
                activeMapping = newMapping
                val cur = itemMap[key]
                if (cur != null) {
                    itemMap[key] = cur.copy(assignedAction = actionName, dataType = dataType)
                    refreshList()
                }
                Log.i(TAG, "Assigned 0x${Integer.toHexString(propId)} (area=$areaId) to action '$actionName'")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed assigning action: ${e.message}", e)
        }
        return false
    }

    private fun findAssignedAction(propId: Int, areaId: Int): String? {
        return activeMapping.mappings.entries.firstOrNull { (_, cfg) ->
            (cfg.propId == propId && cfg.areaId == areaId) ||
                    (cfg.ecarxFunctionId == propId && cfg.ecarxZone == areaId)
        }?.key
    }

    private fun refreshList() {
        val sorted = itemMap.values.sortedWith(
            compareByDescending<SnifferPropertyItem> { it.isRecentlyChanged }
                .thenByDescending { it.lastChangedTimestamp }
        )
        _propertiesList.value = sorted
    }

    fun scanAllCarProperties(context: Context) {
        scope.launch {
            try {
                Log.d(TAG, "Scanning registered CarPropertyManager properties on Geely M7...")
                val carClass = Class.forName("android.car.Car")
                val createMethod = carClass.getMethod("createCar", Context::class.java)
                val car = createMethod.invoke(null, context) ?: return@launch
                val getCarManagerMethod = carClass.getMethod("getCarManager", String::class.java)
                val cpm = getCarManagerMethod.invoke(car, "property") ?: return@launch

                val getPropertyListMethod = cpm.javaClass.methods.firstOrNull { it.name == "getPropertyList" }
                val configList = getPropertyListMethod?.invoke(cpm) as? List<*>

                configList?.forEach { config ->
                    if (config != null) {
                        try {
                            val getPropIdMethod = config.javaClass.getMethod("getPropertyId")
                            val propId = getPropIdMethod.invoke(config) as Int
                            recordEvent(propId, 0, "READ_READY", "CAR_PROPERTY_CONFIG")
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Scan car properties note: ${e.message}")
            }
        }
    }
}
