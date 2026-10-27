package com.khmerauto.nav.hal.safety

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "HalCommandRateLimiter"

/**
 * Strict Write Debouncing & Rate Limiting Guardrail.
 *
 * Rules:
 * 1. Never allow more than 3 hardware write commands per second to CarPropertyManager or ECARX ICarFunction.
 * 2. Enforces a minimum 200ms cooldown Mutex lock between consecutive hardware writes.
 * 3. Prevents bus congestion on the Geely Galaxy M7 CAN/Ethernet gateway and eliminates Flyme Auto UI stutter.
 */
class HalCommandRateLimiter(
    private val maxWritesPerSecond: Int = 3,
    private val minCooldownMs: Long = 200L
) {
    private val mutex = Mutex()
    private val writeTimestamps = ArrayDeque<Long>()
    private var lastWriteTimestamp = 0L

    /**
     * Executes a hardware write block with rate-limiting and cooldown enforcement.
     */
    suspend fun <T> executeWithRateLimit(
        actionName: String,
        block: suspend () -> Result<T>
    ): Result<T> = mutex.withLock {
        val now = System.currentTimeMillis()

        // 1. Enforce minimum cooldown between consecutive writes (at least 200ms)
        val elapsedSinceLast = now - lastWriteTimestamp
        if (elapsedSinceLast < minCooldownMs) {
            val waitTime = minCooldownMs - elapsedSinceLast
            Log.v(TAG, "[$actionName] Enforcing ${waitTime}ms cooldown before write...")
            delay(waitTime)
        }

        // 2. Enforce sliding window: max 3 writes per 1000ms
        pruneOldTimestamps(System.currentTimeMillis())
        while (writeTimestamps.size >= maxWritesPerSecond) {
            val oldest = writeTimestamps.first()
            val timeToWait = (1000L - (System.currentTimeMillis() - oldest)).coerceAtLeast(50L)
            Log.w(TAG, "[$actionName] Rate limit reached ($maxWritesPerSecond writes/sec). Waiting ${timeToWait}ms...")
            delay(timeToWait)
            pruneOldTimestamps(System.currentTimeMillis())
        }

        // 3. Record timestamp and execute write
        val currentWriteTime = System.currentTimeMillis()
        writeTimestamps.addLast(currentWriteTime)
        lastWriteTimestamp = currentWriteTime

        Log.d(TAG, "[$actionName] Rate limiter permitted hardware write at $currentWriteTime (Queue window: ${writeTimestamps.size}/$maxWritesPerSecond)")
        return block()
    }

    private fun pruneOldTimestamps(currentTime: Long) {
        while (writeTimestamps.isNotEmpty() && (currentTime - writeTimestamps.first()) > 1000L) {
            writeTimestamps.removeFirst()
        }
    }
}
