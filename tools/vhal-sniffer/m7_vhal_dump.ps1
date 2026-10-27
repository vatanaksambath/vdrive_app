# Geely Galaxy M7 (Flyme Auto) VHAL Property Diagnostic & Bridge Helper
param(
    [string]$CarIp = "192.168.43.1",
    [int]$AdbPort = 5555,
    [string]$Action = "status"
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  KMAutoNav • Geely Galaxy M7 Flyme Auto VHAL Diagnostic" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$TargetDevice = "$CarIp`:$AdbPort"

switch ($Action) {
    "connect" {
        Write-Host "Connecting to Geely Galaxy M7 at $TargetDevice..." -ForegroundColor Yellow
        adb connect $TargetDevice
        adb devices
    }
    "status" {
        Write-Host "Checking Ktor WebSocket Bridge on http://$CarIp`:8888/api/status..." -ForegroundColor Yellow
        try {
            $response = Invoke-RestMethod -Uri "http://$CarIp`:8888/api/status" -TimeoutSec 3
            $response | Format-List
        } catch {
            Write-Host "Bridge server not responding on $CarIp`:8888. Ensure KMAutoNav is running in CAR_NATIVE_HOST mode." -ForegroundColor Red
        }
    }
    "dump_properties" {
        Write-Host "Dumping Car Service Property Values from Geely M7..." -ForegroundColor Yellow
        adb -s $TargetDevice shell dumpsys car_service --property
    }
    "push_map" {
        Write-Host "Pushing local geely_m7_vhal_map.json to Geely Galaxy M7 head unit..." -ForegroundColor Yellow
        adb -s $TargetDevice shell mkdir -p /sdcard/kmautonav/
        adb -s $TargetDevice push ./app/src/main/assets/geely_m7_vhal_map.json /sdcard/kmautonav/geely_m7_vhal_map.json
        Write-Host "Mapping successfully updated on /sdcard/kmautonav/geely_m7_vhal_map.json" -ForegroundColor Green
    }
    "pull_map" {
        Write-Host "Pulling active mapping from Geely Galaxy M7..." -ForegroundColor Yellow
        adb -s $TargetDevice pull /sdcard/kmautonav/geely_m7_vhal_map.json ./geely_m7_vhal_map_pulled.json
        Write-Host "Pulled to ./geely_m7_vhal_map_pulled.json" -ForegroundColor Green
    }
    "log_ecarx" {
        Write-Host "Streaming ECARX & Flyme Auto AdaptAPI logs..." -ForegroundColor Yellow
        adb -s $TargetDevice logcat -s EcarxAdaptAPI:V AdaptAPI:V GeelyFlymeHalAdapter:D CarHalWebSocketServer:D
    }
    default {
        Write-Host "Available actions: connect, status, dump_properties, push_map, pull_map, log_ecarx" -ForegroundColor White
    }
}
