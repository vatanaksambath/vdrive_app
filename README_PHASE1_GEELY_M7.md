# VDrive (វីដ្រាយ) • Geely Galaxy M7 (Flyme Auto / ECARX) Offline Khmer Cockpit & Navigation

This repository contains the complete, production-ready implementation for **VDrive**, tailored specifically and exclusively for the **2026 Geely Galaxy M7 (Flyme Auto / ECARX Android head unit)**.

---

## 1. Runtime Modes Supported

```
┌────────────────────────────────────────────────────────┐
│                      KMAutoNav                         │
├──────────────────────────┬─────────────────────────────┤
│   TABLET_REMOTE_CLIENT   │       CAR_NATIVE_HOST       │
│  (External Android Tablet│  (Directly on Geely M7 HU   │
│   connected via Wi-Fi)   │  + Ktor Server port 8888)   │
└────────────┬─────────────┴──────────────┬──────────────┘
             │ WebSocket ws://<car-ip>:8888/ws/hal
             └────────────────────────────┘
```

1. **`CAR_NATIVE_HOST`** (Runs natively on the Geely Galaxy M7 Head Unit):
   - Initializes `GeelyFlymeHalAdapter` using the 4-tier fallback chain.
   - Starts `CarHalWebSocketServer` (Ktor CIO on port `8888`) bound to `/ws/hal`.
   - Starts `CarBridgeService` as an Android Foreground Service to keep WebSocket connections alive in the background.
   - Broadcasts real-time vehicle telemetry every 250ms and streams hardware sniffer events.
   - Generates an on-screen pairing QR code and displays the car's local IP address (`192.168.43.1`).

2. **`TABLET_REMOTE_CLIENT`** (Runs on External Android Tablet over Wi-Fi Hotspot):
   - Connects to `ws://<car-ip>:8888/ws/hal` with automatic reconnection and exponential backoff.
   - Transparently implements `VehicleHalContract`.
   - Any UI button press or future Khmer voice command on the tablet dispatches `HalCommandPacket` JSON over Wi-Fi to control physical car hardware in < 150ms.

3. **`SIMULATOR`** (Physics-based Geely Galaxy M7 Simulation):
   - Simulates 29.8 kWh battery SoC, 218 km EV range, Phnom Penh urban driving dynamics, AC thermal cooling against 34°C ambient heat, window/sunroof motor timing, and 4-wheel TPMS heating curves.

---

## 2. 4-Tier Hardware Fallback Chain (`GeelyFlymeHalAdapter.kt`)

| Tier | Name | Target Mechanism |
|---|---|---|
| **Tier 1** | Dynamic JSON Override Map | Reads `/sdcard/kmautonav/geely_m7_vhal_map.json`. Allows hot-reloading property mappings without recompiling the APK. |
| **Tier 2** | ECARX AdaptAPI Reflection Bridge | Dynamically loads `com.ecarx.xui.adaptapi.car.Car`, binds `ICarFunction` (HVAC, Windows, Sunroof, Tailgate, Seats, Locks) and `ISensor` (Speed, Battery, Odometer, TPMS) via Java Reflection & dynamic proxies. |
| **Tier 3** | AOSP CarPropertyManager Bridge | Uses reflection on `android.car.Car` to read/write standard AOSP property IDs (`0x11600204`, `0x15600503`, `0x13400BC0`) and vendor `0x20000000` ranges. |
| **Tier 4** | Root / Shell Service Fallback | If `SecurityException` occurs on unsigned Flyme Auto APKs, automatically executes `cmd car_service set-property-value <propId> <areaId> <val>` via root `su` or shell process. |

---

## 3. Directory Layout

```text
d:\code_cgap/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── tools/
│   └── vhal-sniffer/
│       ├── m7_vhal_dump.ps1      # PowerShell ADB sniffer script
│       └── m7_vhal_dump.sh       # Bash ADB sniffer script
└── app/
    ├── build.gradle.kts
    └── src/
        └── main/
            ├── AndroidManifest.xml
            ├── assets/
            │   └── geely_m7_vhal_map.json  # Pre-seeded Geely M7 VHAL mapping
            ├── res/
            └── java/com/khmerauto/nav/
                ├── KMAutoNavApp.kt
                ├── MainActivity.kt
                ├── service/
                │   └── CarBridgeService.kt # Foreground service for Ktor server
                ├── core/database/
                │   ├── AppDatabase.kt
                │   ├── dao/VehicleDao.kt
                │   └── entity/             # Snapshot, Trip, Charge entities
                ├── hal/
                │   ├── VehicleHalContract.kt # Unified interface & telemetry model
                │   ├── geely/
                │   │   ├── GeelyFlymeHalAdapter.kt   # 4-tier native adapter
                │   │   ├── GeelyPropertyMapping.kt   # JSON mapping persistence
                │   │   └── EcarxReflectionBridge.kt  # ECARX AdaptAPI reflection
                │   ├── wifi/
                │   │   ├── HalPackets.kt             # Serializable packet protocol
                │   │   ├── CarHalWebSocketServer.kt  # Ktor server on port 8888
                │   │   └── RemoteWifiHalAdapter.kt   # Tablet WebSocket client
                │   ├── mock/
                │   │   └── SimulatedHalAdapter.kt    # Physics-based M7 simulator
                │   └── sniffer/
                │       └── VhalSnifferEngine.kt      # Change detector & assigner
                └── ui/
                    ├── Phase1DiagnosticScreen.kt     # Full Jetpack Compose UI
                    ├── components/QrCodeImage.kt     # ZXing QR Code generator
                    └── theme/                        # Geely Flyme Auto automotive palette
```

---

## 4. How to Test with the External Tablet on the Geely M7

1. Turn on the Geely Galaxy M7 Wi-Fi Hotspot from the central screen.
2. Connect your Android tablet to the car's Wi-Fi.
3. Install and launch KMAutoNav on the tablet.
4. Select **`TABLET REMOTE`** at the top.
5. Tap **Change IP** and confirm `192.168.43.1` (the default gateway IP of the car hotspot).
6. As soon as the car host is running or ADB bridged, the status indicator turns green (`CONNECTED`).
7. Test controlling HVAC temperature, fan speed, windows, sunroof, tailgate, and seat climate directly from the tablet!
