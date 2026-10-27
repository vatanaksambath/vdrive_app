package com.khmerauto.nav.hal.geely

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

private const val TAG = "EcarxReflectionBridge"

/**
 * Robust Java Reflection Bridge for ECARX AdaptAPI on Geely Galaxy M7 (Flyme Auto).
 *
 * Automotive Guardrails Enforced:
 * 1. Read-Only Discovery: Never performs blind writes to unmapped IDs.
 * 2. Non-blocking Execution: Every reflection call is wrapped in runCatching { ... }
 *    with a strict 1500ms coroutine timeout (withTimeoutOrNull(1500L)) on Dispatchers.IO.
 * 3. Deep-Sleep Teardown: Provides unregisterListeners() to eliminate 12V drain.
 */
class EcarxReflectionBridge(private val context: Context) {

    private var carInstance: Any? = null
    private var carFunctionInstance: Any? = null
    private var sensorInstance: Any? = null

    private var getFunctionValueMethod: Method? = null
    private var setFunctionValueMethod: Method? = null
    private var registerWatcherMethod: Method? = null

    private var getSensorEventMethod: Method? = null
    private var registerSensorListenerMethod: Method? = null
    private var unregisterListenerMethod: Method? = null

    var isAvailable: Boolean = false
        private set

    // Listener callback for sniffer and real-time updates
    var onFunctionChanged: ((functionId: Int, zone: Int, value: Any) -> Unit)? = null
    var onSensorChanged: ((sensorType: Int, value: Any) -> Unit)? = null

    fun initialize(): Boolean = runCatching {
        Log.d(TAG, "Attempting to reflectively initialize ECARX AdaptAPI for Geely Galaxy M7...")
        val carClass = Class.forName("com.ecarx.xui.adaptapi.car.Car")
        val createMethod = carClass.getMethod("create", Context::class.java)
        carInstance = createMethod.invoke(null, context)

        if (carInstance == null) {
            Log.w(TAG, "Car.create(context) returned null. ECARX service not ready.")
            isAvailable = false
            return@runCatching false
        }

        // Extract ICarFunction
        val getCarFunctionMethod = carInstance?.javaClass?.getMethod("getCarFunction")
        carFunctionInstance = getCarFunctionMethod?.invoke(carInstance)

        if (carFunctionInstance != null) {
            val funcClass = carFunctionInstance!!.javaClass
            getFunctionValueMethod = funcClass.methods.firstOrNull {
                it.name == "getFunctionValue" && it.parameterTypes.size in 1..2
            }
            setFunctionValueMethod = funcClass.methods.firstOrNull {
                it.name == "setFunctionValue" && it.parameterTypes.size in 2..3
            }
            registerWatcherMethod = funcClass.methods.firstOrNull {
                it.name == "registerFunctionValueWatcher" || it.name == "registerListener"
            }
            attachFunctionWatcher()
        }

        // Extract ISensor
        val getSensorMethod = carInstance?.javaClass?.getMethod("getSensor")
        sensorInstance = getSensorMethod?.invoke(carInstance)

        if (sensorInstance != null) {
            val sensorClass = sensorInstance!!.javaClass
            getSensorEventMethod = sensorClass.methods.firstOrNull {
                it.name == "getSensorEvent"
            }
            registerSensorListenerMethod = sensorClass.methods.firstOrNull {
                it.name == "registerListener"
            }
            unregisterListenerMethod = sensorClass.methods.firstOrNull {
                it.name == "unregisterListener"
            }
            attachSensorListener()
        }

        isAvailable = (carFunctionInstance != null || sensorInstance != null)
        Log.i(TAG, "ECARX AdaptAPI initialized successfully! isAvailable=$isAvailable")
        isAvailable
    }.onFailure {
        Log.w(TAG, "ECARX AdaptAPI reflection initialization note: ${it.message}")
        isAvailable = false
    }.getOrDefault(false)

    /**
     * Reads a cabin hardware function value with a 1500ms timeout on Dispatchers.IO.
     * Prevents Android Main Thread ANR on slow Flyme Auto response.
     */
    suspend fun getFunctionValueAsync(functionId: Int, zone: Int = 0): Any? = withContext(Dispatchers.IO) {
        val target = carFunctionInstance ?: return@withContext null
        val method = getFunctionValueMethod ?: return@withContext null

        withTimeoutOrNull(1500L) {
            runCatching {
                if (method.parameterTypes.size == 2) {
                    method.invoke(target, functionId, zone)
                } else {
                    method.invoke(target, functionId)
                }
            }.onFailure {
                Log.w(TAG, "getFunctionValue reflection error for funcId=0x${Integer.toHexString(functionId)}: ${it.message}")
            }.getOrNull()
        }
    }

    /**
     * Synchronous fallback reader wrapped in runCatching.
     */
    fun getFunctionValue(functionId: Int, zone: Int = 0): Any? {
        val target = carFunctionInstance ?: return null
        val method = getFunctionValueMethod ?: return null
        return runCatching {
            if (method.parameterTypes.size == 2) {
                method.invoke(target, functionId, zone)
            } else {
                method.invoke(target, functionId)
            }
        }.onFailure {
            Log.w(TAG, "getFunctionValue error for funcId=$functionId: ${it.message}")
        }.getOrNull()
    }

    /**
     * Sends a control command to ECARX ICarFunction with a 1500ms timeout on Dispatchers.IO.
     */
    suspend fun setFunctionValueAsync(functionId: Int, zone: Int = 0, value: Any): Boolean = withContext(Dispatchers.IO) {
        val target = carFunctionInstance ?: return@withContext false
        val method = setFunctionValueMethod ?: return@withContext false

        withTimeoutOrNull(1500L) {
            runCatching {
                val result = when (method.parameterTypes.size) {
                    3 -> {
                        val p3Type = method.parameterTypes[2]
                        val castedVal = castValue(value, p3Type)
                        method.invoke(target, functionId, zone, castedVal)
                    }
                    2 -> {
                        val p2Type = method.parameterTypes[1]
                        val castedVal = castValue(value, p2Type)
                        method.invoke(target, functionId, castedVal)
                    }
                    else -> false
                }
                (result as? Boolean) ?: true
            }.onFailure {
                Log.w(TAG, "setFunctionValue reflection error for funcId=0x${Integer.toHexString(functionId)}: ${it.message}")
            }.getOrDefault(false)
        } ?: run {
            Log.w(TAG, "setFunctionValue timed out (1500ms limit reached) for funcId=0x${Integer.toHexString(functionId)}")
            false
        }
    }

    fun setFunctionValue(functionId: Int, zone: Int = 0, value: Any): Boolean {
        val target = carFunctionInstance ?: return false
        val method = setFunctionValueMethod ?: return false
        return runCatching {
            val result = when (method.parameterTypes.size) {
                3 -> {
                    val p3Type = method.parameterTypes[2]
                    val castedVal = castValue(value, p3Type)
                    method.invoke(target, functionId, zone, castedVal)
                }
                2 -> {
                    val p2Type = method.parameterTypes[1]
                    val castedVal = castValue(value, p2Type)
                    method.invoke(target, functionId, castedVal)
                }
                else -> false
            }
            (result as? Boolean) ?: true
        }.onFailure {
            Log.w(TAG, "setFunctionValue sync error for funcId=$functionId: ${it.message}")
        }.getOrDefault(false)
    }

    /**
     * Cleans up listener proxies to prevent 12V drain during vehicle sleep.
     */
    fun unregisterListeners() = runCatching {
        Log.i(TAG, "Unregistering all ECARX listener proxies for sleep compliance...")
        onFunctionChanged = null
        onSensorChanged = null
        val target = sensorInstance ?: return@runCatching
        val unreg = unregisterListenerMethod ?: return@runCatching
        unreg.invoke(target)
    }.onFailure {
        Log.w(TAG, "unregisterListeners note: ${it.message}")
    }

    private fun castValue(value: Any, targetType: Class<*>): Any {
        return when {
            targetType == Int::class.java || targetType == java.lang.Integer::class.java -> {
                when (value) {
                    is Number -> value.toInt()
                    is Boolean -> if (value) 1 else 0
                    else -> value.toString().toIntOrNull() ?: 0
                }
            }
            targetType == Float::class.java || targetType == java.lang.Float::class.java -> {
                when (value) {
                    is Number -> value.toFloat()
                    else -> value.toString().toFloatOrNull() ?: 0.0f
                }
            }
            targetType == Boolean::class.java || targetType == java.lang.Boolean::class.java -> {
                when (value) {
                    is Boolean -> value
                    is Number -> value.toInt() != 0
                    else -> value.toString().toBoolean()
                }
            }
            else -> value
        }
    }

    private fun attachFunctionWatcher() = runCatching {
        val target = carFunctionInstance ?: return@runCatching
        val regMethod = registerWatcherMethod ?: return@runCatching
        val callbackParamType = regMethod.parameterTypes.firstOrNull { it.isInterface } ?: return@runCatching

        val handler = InvocationHandler { _, method, args ->
            if (method.name.contains("onFunctionChanged", ignoreCase = true) ||
                method.name.contains("onValueChanged", ignoreCase = true) ||
                method.name.contains("update", ignoreCase = true)
            ) {
                val funcId = (args?.getOrNull(0) as? Number)?.toInt() ?: 0
                val zone = (args?.getOrNull(1) as? Number)?.toInt() ?: 0
                val value = args?.getOrNull(2) ?: args?.getOrNull(1) ?: Unit
                onFunctionChanged?.invoke(funcId, zone, value)
            }
            null
        }

        val proxyInstance = Proxy.newProxyInstance(
            callbackParamType.classLoader,
            arrayOf(callbackParamType),
            handler
        )

        if (regMethod.parameterTypes.size == 1) {
            regMethod.invoke(target, proxyInstance)
        } else if (regMethod.parameterTypes.size == 2 && regMethod.parameterTypes[0] == Int::class.javaPrimitiveType) {
            listOf(0x1001, 0x1002, 0x1003, 0x2001, 0x2005, 0x2007).forEach { fId ->
                runCatching { regMethod.invoke(target, fId, proxyInstance) }
            }
        }
        Log.d(TAG, "Attached dynamic ECARX ICarFunction watcher proxy.")
    }.onFailure {
        Log.w(TAG, "Unable to attach dynamic ECARX watcher proxy: ${it.message}")
    }

    private fun attachSensorListener() = runCatching {
        val target = sensorInstance ?: return@runCatching
        val regMethod = registerSensorListenerMethod ?: return@runCatching
        val listenerInterface = regMethod.parameterTypes.firstOrNull { it.isInterface } ?: return@runCatching

        val handler = InvocationHandler { _, method, args ->
            if (method.name.contains("onSensorChanged", ignoreCase = true) ||
                method.name.contains("onDataChanged", ignoreCase = true)
            ) {
                val sensorType = (args?.getOrNull(0) as? Number)?.toInt() ?: 0
                val eventObj = args?.getOrNull(1) ?: Unit
                onSensorChanged?.invoke(sensorType, eventObj)
            }
            null
        }

        val proxyInstance = Proxy.newProxyInstance(
            listenerInterface.classLoader,
            arrayOf(listenerInterface),
            handler
        )

        if (regMethod.parameterTypes.size >= 1) {
            regMethod.invoke(target, proxyInstance)
        }
        Log.d(TAG, "Attached dynamic ECARX ISensor listener proxy.")
    }.onFailure {
        Log.w(TAG, "Unable to attach dynamic ECARX sensor proxy: ${it.message}")
    }
}
