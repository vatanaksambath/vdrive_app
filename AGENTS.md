# KMAutoNav • Architecture & Mandatory Automotive Safety Guardrails

This project is **KMAutoNav** (offline-first Khmer Navigation, Voice & Cabin Control system modeled after EZNav), specifically engineered for the **2026 Geely Galaxy M7 (Flyme Auto / ECARX Android head unit)**.

## MANDATORY AUTOMOTIVE SAFETY & STABILITY GUARDRAILS (ALL PHASES)

Whenever writing or modifying code across ANY phase (HAL, UI, Voice, Map, Database, Telemetry), you MUST strictly comply with these four rules:

### 1. Strict Write Debouncing & Rate Limiting (`HalCommandRateLimiter`)
- **Max 3 hardware writes/second:** Never allow more than 3 hardware write commands per second to `CarPropertyManager` or `ECARX ICarFunction`.
- **200ms Cooldown Mutex:** Every hardware write dispatcher must enforce a minimum `200ms` cooldown between writes using a `Mutex`.
- **Jetpack Compose Sliders:** For all sliders (HVAC Temperature, Fan Speed, Window Positions, Sunroof/Shade, Volume), UI state must update locally during gestures, and hardware writes must ONLY be dispatched inside `onValueChangeFinished`.

### 2. Read-Only Discovery (Zero Blind Writes)
- **VHAL Sniffer:** Must strictly operate in **Read & Subscribe (`onChangeEvent`)** mode. NEVER iterate and write test values to unmapped or unknown property IDs.
- **1500ms Non-Blocking Timeout:** Every single reflection invocation (`CarPropertyManager`, `ICarFunction`, `ISensor`) must be wrapped inside `runCatching { withTimeoutOrNull(1500L) { ... } }.onFailure { Log.w(...) }` on `Dispatchers.IO`. Never block the Android Main/UI thread to prevent ANRs.

### 3. Zero 12V Battery Drain / Deep-Sleep Compliance (`CarPowerLifecycleObserver`)
- **No Wake Locks:** Do NOT acquire `PowerManager.PARTIAL_WAKE_LOCK`.
- **Immediate Sleep Teardown (< 100ms):** When `Intent.ACTION_SCREEN_OFF` or `Intent.ACTION_SHUTDOWN` is received:
  1. Persist the final `VehicleStateSnapshot` (Odometer & Battery %) to Room DB in `< 100ms`.
  2. Immediately release the `AudioRecord` microphone instance.
  3. Pause the `MapLibre GL` rendering surface thread.
  4. Stop the embedded Ktor WebSocket server (`CarHalWebSocketServer`) and unregister all high-frequency `ISensor` / `CarPropertyManager` listeners.

### 4. Drive-State Safety Interlocks
- **Tailgate Safety:** Before executing `setTailgate(open = true)`, verify `telemetryState.value.gear == GearState.P && telemetryState.value.speedKmh == 0.0f`.
- **Software Refusal:** If the car is not in Park or is in motion, refuse command execution in software and display a bilingual Khmer & English warning toast:
  `"មិនអាចបើកគូទក្រោយពេលរថយន្តកំពុងបើកបរបានទេ! / Cannot open tailgate while vehicle is in motion or not in Park!"`
