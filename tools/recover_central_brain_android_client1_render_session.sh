#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-001/004, S2-HMI-001, DEL-004.
# Stage: P4-R7-CLIENT1-RENDER-SESSION-RECOVERY.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLIENT1_PACKAGE="com.tuanjie.urasclient"
CLIENT1_ACTIVITY="$CLIENT1_PACKAGE/.MainActivity"
RENDER_SERVICE_PACKAGE="com.tuanjie.renderservice"
CLIENT1_DISPLAY_ID="${CENTRAL_BRAIN_CLIENT1_DISPLAY_ID:-2}"

if [[ ! "$CLIENT1_DISPLAY_ID" =~ ^[0-9]+$ ]]; then
  echo "client1_render_session_recovered=false reason=INVALID_DISPLAY_ID" >&2
  exit 2
fi

if [[ -n "${ADB_BIN:-}" ]]; then
  adb_base=("$ADB_BIN")
elif [[ -x /mnt/e/platform-tools/adb.exe ]]; then
  adb_base=(/mnt/e/platform-tools/adb.exe)
elif [[ -x "$ROOT/.tools/android-sdk/platform-tools/adb" ]]; then
  adb_base=("$ROOT/.tools/android-sdk/platform-tools/adb")
elif command -v adb >/dev/null 2>&1; then
  adb_base=(adb)
else
  echo "client1_render_session_recovered=false reason=ADB_NOT_FOUND" >&2
  exit 3
fi

if [[ -n "${ADB_SERVER_PORT:-}" ]]; then
  [[ "$ADB_SERVER_PORT" =~ ^[0-9]+$ ]] \
    || { echo "client1_render_session_recovered=false reason=INVALID_ADB_SERVER_PORT" >&2; exit 4; }
  adb_base+=( -P "$ADB_SERVER_PORT" )
fi

adb=("${adb_base[@]}")
if [[ -n "${ANDROID_TRANSPORT_ID:-}" ]]; then
  [[ "$ANDROID_TRANSPORT_ID" =~ ^[0-9]+$ ]] \
    || { echo "client1_render_session_recovered=false reason=INVALID_TRANSPORT_ID" >&2; exit 4; }
  adb+=( -t "$ANDROID_TRANSPORT_ID" )
elif [[ -n "${ANDROID_SERIAL:-}" ]]; then
  adb+=( -s "$ANDROID_SERIAL" )
else
  device_count="$("${adb_base[@]}" devices | awk \
    'NR > 1 {gsub(/\r/, "", $2)} $2 == "device" {count++} END {print count+0}')"
  if [[ "$device_count" != "1" ]]; then
    echo "client1_render_session_recovered=false reason=ANDROID_DEVICE_COUNT_${device_count}" >&2
    exit 5
  fi
fi

require_package() {
  local package_name="$1"
  local installed_path
  installed_path="$("${adb[@]}" shell pm path "$package_name" | tr -d '\r')"
  [[ "$installed_path" == package:* ]] \
    || { echo "client1_render_session_recovered=false reason=PACKAGE_MISSING" >&2; return 1; }
}

require_package "$CLIENT1_PACKAGE"
require_package "$RENDER_SERVICE_PACKAGE"

display_dump="$("${adb[@]}" shell dumpsys display | tr -d '\r')"
grep -Fq "mDisplayId=$CLIENT1_DISPLAY_ID" <<<"$display_dump" \
  || { echo "client1_render_session_recovered=false reason=DISPLAY_MISSING" >&2; exit 6; }

"${adb[@]}" shell am force-stop "$CLIENT1_PACKAGE"
start_output="$("${adb[@]}" shell am start -W --display "$CLIENT1_DISPLAY_ID" \
  -n "$CLIENT1_ACTIVITY" | tr -d '\r')"
grep -Fq 'Status: ok' <<<"$start_output" \
  || { echo "client1_render_session_recovered=false reason=ACTIVITY_START_FAILED" >&2; exit 7; }

sleep 2
client1_pid="$("${adb[@]}" shell pidof "$CLIENT1_PACKAGE" | tr -d '\r')"
[[ -n "$client1_pid" ]] \
  || { echo "client1_render_session_recovered=false reason=PROCESS_NOT_RUNNING" >&2; exit 8; }

activity_dump="$("${adb[@]}" shell dumpsys activity activities | tr -d '\r')"
grep -Eq \
  "topResumedActivity=ActivityRecord\\{[^}]+ ${CLIENT1_PACKAGE}/\\.MainActivity\\} t[0-9]+ d${CLIENT1_DISPLAY_ID}\\}" \
  <<<"$activity_dump" \
  || { echo "client1_render_session_recovered=false reason=ACTIVITY_NOT_RESUMED_ON_DISPLAY" >&2; exit 9; }

surface_dump="$("${adb[@]}" shell dumpsys SurfaceFlinger --list | tr -d '\r')"
grep -Fq "SurfaceView[$CLIENT1_PACKAGE/$CLIENT1_ACTIVITY]" <<<"$surface_dump" \
  || grep -Fq "SurfaceView[$CLIENT1_PACKAGE/$CLIENT1_PACKAGE.MainActivity]" <<<"$surface_dump" \
  || { echo "client1_render_session_recovered=false reason=RENDER_SURFACE_MISSING" >&2; exit 10; }

printf '%s\n' \
  "client1_render_session_recovered=true" \
  "client1_android_display_id=$CLIENT1_DISPLAY_ID" \
  "client1_process_running=true" \
  "client1_activity_resumed=true" \
  "client1_render_surface_present=true" \
  "render_service_package_present=true" \
  "client1_data_cleared=false" \
  "client2_restarted=false" \
  "system_partition_modified=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
