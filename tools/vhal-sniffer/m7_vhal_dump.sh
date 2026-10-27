#!/bin/bash
# Geely Galaxy M7 (Flyme Auto) VHAL Property Diagnostic & Bridge Helper
CAR_IP=${1:-"192.168.43.1"}
ADB_PORT=${2:-5555}
ACTION=${3:-"status"}
TARGET_DEVICE="$CAR_IP:$ADB_PORT"

echo "=========================================================="
echo "  KMAutoNav • Geely Galaxy M7 Flyme Auto VHAL Diagnostic"
echo "=========================================================="

case "$ACTION" in
  connect)
    echo "Connecting to Geely Galaxy M7 at $TARGET_DEVICE..."
    adb connect "$TARGET_DEVICE"
    adb devices
    ;;
  status)
    echo "Checking Ktor WebSocket Bridge on http://$CAR_IP:8888/api/status..."
    curl -s --connect-timeout 3 "http://$CAR_IP:8888/api/status" || echo "Bridge server not reachable. Ensure KMAutoNav is running in CAR_NATIVE_HOST mode."
    echo ""
    ;;
  dump_properties)
    echo "Dumping Car Service Property Values from Geely M7..."
    adb -s "$TARGET_DEVICE" shell dumpsys car_service --property
    ;;
  push_map)
    echo "Pushing geely_m7_vhal_map.json to Geely Galaxy M7..."
    adb -s "$TARGET_DEVICE" shell mkdir -p /sdcard/kmautonav/
    adb -s "$TARGET_DEVICE" push ./app/src/main/assets/geely_m7_vhal_map.json /sdcard/kmautonav/geely_m7_vhal_map.json
    echo "Mapping successfully updated on /sdcard/kmautonav/geely_m7_vhal_map.json"
    ;;
  pull_map)
    echo "Pulling active mapping from Geely Galaxy M7..."
    adb -s "$TARGET_DEVICE" pull /sdcard/kmautonav/geely_m7_vhal_map.json ./geely_m7_vhal_map_pulled.json
    ;;
  log_ecarx)
    echo "Streaming ECARX & Flyme Auto AdaptAPI logs..."
    adb -s "$TARGET_DEVICE" logcat -s EcarxAdaptAPI:V AdaptAPI:V GeelyFlymeHalAdapter:D CarHalWebSocketServer:D
    ;;
  *)
    echo "Usage: ./m7_vhal_dump.sh <CAR_IP> <ADB_PORT> <connect|status|dump_properties|push_map|pull_map|log_ecarx>"
    ;;
esac
