# Automotive Safety & Stability Rules (Geely Galaxy M7 / Flyme Auto)

Apply these four architectural constraints to all code generation in KMAutoNav:

1. **Hardware Rate Limiting:**
   - Maximum 3 writes per second to `CarPropertyManager` / `ICarFunction`.
   - Minimum 200ms `Mutex` cooldown between consecutive hardware writes (`HalCommandRateLimiter`).
   - Sliders must dispatch writes only in `onValueChangeFinished`.

2. **Read-Only Discovery:**
   - Sniffer only reads configs and subscribes to `onChangeEvent`. No blind test writes.
   - All reflection calls wrapped in `runCatching` with a 1500ms timeout (`withTimeoutOrNull(1500L)`).

3. **Zero 12V Drain / Deep-Sleep Compliance:**
   - No `PowerManager.PARTIAL_WAKE_LOCK`.
   - On `ACTION_SCREEN_OFF` / `ACTION_SHUTDOWN`:
     - Save Odometer & Battery % snapshot to Room DB in < 100ms.
     - Release `AudioRecord` mic immediately.
     - Pause MapLibre GL rendering thread.
     - Stop Ktor server and unregister `ISensor` listeners.

4. **Drive-State Safety Interlocks:**
   - Tailgate opening is prohibited unless `gear == GearState.P` and `speedKmh == 0.0f`.
   - Warning toast in Khmer & English:
     `មិនអាចបើកគូទក្រោយពេលរថយន្តកំពុងបើកបរបានទេ! / Cannot open tailgate while vehicle is in motion or not in Park!`
