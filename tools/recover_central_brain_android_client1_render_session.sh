#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-001/004, S2-HMI-001, DEL-004.
# Stage: P4-R7-CLIENT1-RENDER-SESSION-RECOVERY.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLIENT1_PACKAGE="com.tuanjie.urasclient"
CLIENT1_ACTIVITY="$CLIENT1_PACKAGE/.MainActivity"
CLIENT2_PACKAGE="com.tuanjie.urasclient2"
CLIENT2_ACTIVITY="$CLIENT2_PACKAGE/.MainActivity"
RENDER_SERVICE_PACKAGE="com.tuanjie.renderservice"
CLIENT1_DISPLAY_ID="${CENTRAL_BRAIN_CLIENT1_DISPLAY_ID:-2}"
CLIENT2_DISPLAY_ID="${CENTRAL_BRAIN_CLIENT2_DISPLAY_ID:-0}"
CLIENT1_STARTUP_WAIT_SECONDS="${CENTRAL_BRAIN_CLIENT1_STARTUP_WAIT_SECONDS:-6}"
CLIENT2_STARTUP_WAIT_SECONDS="${CENTRAL_BRAIN_CLIENT2_STARTUP_WAIT_SECONDS:-12}"

if [[ ! "$CLIENT1_DISPLAY_ID" =~ ^[0-9]+$ ]] \
  || [[ ! "$CLIENT2_DISPLAY_ID" =~ ^[0-9]+$ ]]; then
  echo "client1_render_session_recovered=false reason=INVALID_DISPLAY_ID" >&2
  exit 2
fi
if [[ ! "$CLIENT1_STARTUP_WAIT_SECONDS" =~ ^[0-9]+$ ]] \
  || [[ ! "$CLIENT2_STARTUP_WAIT_SECONDS" =~ ^[0-9]+$ ]]; then
  echo "client1_render_session_recovered=false reason=INVALID_STARTUP_WAIT" >&2
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
require_package "$CLIENT2_PACKAGE"
require_package "$RENDER_SERVICE_PACKAGE"

display_dump="$("${adb[@]}" shell dumpsys display | tr -d '\r')"
grep -Fq "mDisplayId=$CLIENT1_DISPLAY_ID" <<<"$display_dump" \
  || { echo "client1_render_session_recovered=false reason=CLIENT1_DISPLAY_MISSING" >&2; exit 6; }
grep -Fq "mDisplayId=$CLIENT2_DISPLAY_ID" <<<"$display_dump" \
  || { echo "client1_render_session_recovered=false reason=CLIENT2_DISPLAY_MISSING" >&2; exit 6; }

"${adb[@]}" shell am force-stop "$CLIENT2_PACKAGE"
"${adb[@]}" shell am force-stop "$CLIENT1_PACKAGE"
"${adb[@]}" shell am force-stop "$RENDER_SERVICE_PACKAGE"

recovery_marker="central_brain_render_recovery_$$_$(date +%s)"
"${adb[@]}" shell "log -p i -t CbRenderRecovery $recovery_marker"

client1_start_output="$("${adb[@]}" shell am start -W --display "$CLIENT1_DISPLAY_ID" \
  -n "$CLIENT1_ACTIVITY" | tr -d '\r')"
grep -Fq 'Status: ok' <<<"$client1_start_output" \
  || { echo "client1_render_session_recovered=false reason=CLIENT1_ACTIVITY_START_FAILED" >&2; exit 7; }

sleep "$CLIENT1_STARTUP_WAIT_SECONDS"
client1_pid="$("${adb[@]}" shell pidof "$CLIENT1_PACKAGE" | tr -d '\r')"
[[ -n "$client1_pid" ]] \
  || { echo "client1_render_session_recovered=false reason=CLIENT1_PROCESS_NOT_RUNNING" >&2; exit 8; }
render_service_pid="$("${adb[@]}" shell pidof "$RENDER_SERVICE_PACKAGE" | tr -d '\r')"
[[ -n "$render_service_pid" ]] \
  || { echo "client1_render_session_recovered=false reason=RENDER_SERVICE_NOT_RUNNING_AFTER_CLIENT1" >&2; exit 8; }

client2_start_output="$("${adb[@]}" shell am start -W --display "$CLIENT2_DISPLAY_ID" \
  -n "$CLIENT2_ACTIVITY" | tr -d '\r')"
grep -Fq 'Status: ok' <<<"$client2_start_output" \
  || { echo "client1_render_session_recovered=false reason=CLIENT2_ACTIVITY_START_FAILED" >&2; exit 7; }

sleep "$CLIENT2_STARTUP_WAIT_SECONDS"
client2_pid="$("${adb[@]}" shell pidof "$CLIENT2_PACKAGE" | tr -d '\r')"
[[ -n "$client2_pid" ]] \
  || { echo "client1_render_session_recovered=false reason=CLIENT2_PROCESS_NOT_RUNNING" >&2; exit 8; }

activity_dump="$("${adb[@]}" shell dumpsys activity activities | tr -d '\r')"
grep -Eq \
  "topResumedActivity=ActivityRecord\\{[^}]+ ${CLIENT1_PACKAGE}/\\.MainActivity\\} t[0-9]+ d${CLIENT1_DISPLAY_ID}\\}" \
  <<<"$activity_dump" \
  || { echo "client1_render_session_recovered=false reason=CLIENT1_ACTIVITY_NOT_RESUMED_ON_DISPLAY" >&2; exit 9; }
grep -Eq \
  "topResumedActivity=ActivityRecord\\{[^}]+ ${CLIENT2_PACKAGE}/\\.MainActivity\\} t[0-9]+ d${CLIENT2_DISPLAY_ID}\\}" \
  <<<"$activity_dump" \
  || { echo "client1_render_session_recovered=false reason=CLIENT2_ACTIVITY_NOT_RESUMED_ON_DISPLAY" >&2; exit 9; }

surface_dump="$("${adb[@]}" shell dumpsys SurfaceFlinger --list | tr -d '\r')"
grep -Fq "SurfaceView[$CLIENT1_PACKAGE/$CLIENT1_ACTIVITY]" <<<"$surface_dump" \
  || grep -Fq "SurfaceView[$CLIENT1_PACKAGE/$CLIENT1_PACKAGE.MainActivity]" <<<"$surface_dump" \
  || { echo "client1_render_session_recovered=false reason=CLIENT1_RENDER_SURFACE_MISSING" >&2; exit 10; }
grep -Fq "SurfaceView[$CLIENT2_PACKAGE/$CLIENT2_ACTIVITY]" <<<"$surface_dump" \
  || grep -Fq "SurfaceView[$CLIENT2_PACKAGE/$CLIENT2_PACKAGE.MainActivity]" <<<"$surface_dump" \
  || { echo "client1_render_session_recovered=false reason=CLIENT2_RENDER_SURFACE_MISSING" >&2; exit 10; }

render_log="$("${adb[@]}" logcat -d -v brief \
  'CbRenderRecovery:I' \
  'TuanjieRenderService[Client]:V' \
  'TuanjieRenderService[Server]:V' \
  'Tuanjie:V' \
  '*:S' | tr -d '\r')"
grep -Fq "$recovery_marker" <<<"$render_log" \
  || { echo "client1_render_session_recovered=false reason=RECOVERY_LOG_MARKER_MISSING" >&2; exit 11; }
render_log="${render_log#*"$recovery_marker"}"
grep -Fq 'DisplayIndex 0' <<<"$render_log" \
  || { echo "client1_render_session_recovered=false reason=CLIENT1_RENDER_INDEX_MISSING" >&2; exit 11; }
grep -Fq 'DisplayIndex 1' <<<"$render_log" \
  || { echo "client1_render_session_recovered=false reason=CLIENT2_RENDER_INDEX_MISSING" >&2; exit 11; }
grep -Fq 'resolution[1920x720]' <<<"$render_log" \
  || { echo "client1_render_session_recovered=false reason=CLIENT1_FRAMEBUFFER_MISSING" >&2; exit 11; }
grep -Fq 'resolution[1920x1080]' <<<"$render_log" \
  || { echo "client1_render_session_recovered=false reason=CLIENT2_NATIVE_FRAMEBUFFER_MISSING" >&2; exit 11; }
grep -Fq 'resolution[2880x1620]' <<<"$render_log" \
  || { echo "client1_render_session_recovered=false reason=CLIENT2_SCALED_FRAMEBUFFER_MISSING" >&2; exit 11; }
grep -Fq 'combinedDispMask:3' <<<"$render_log" \
  || { echo "client1_render_session_recovered=false reason=COMBINED_DISPLAY_MASK_MISSING" >&2; exit 11; }

printf '%s\n' \
  "cockpit_render_stack_recovered=true" \
  "client1_render_session_recovered=true" \
  "client1_started_before_client2=true" \
  "client1_android_display_id=$CLIENT1_DISPLAY_ID" \
  "client1_render_index=0" \
  "client1_surface_resolution=1920x720" \
  "client2_android_display_id=$CLIENT2_DISPLAY_ID" \
  "client2_render_index=1" \
  "client2_surface_resolution=1920x1080" \
  "client2_scaled_framebuffer_resolution=2880x1620" \
  "client1_process_running=true" \
  "client2_process_running=true" \
  "render_service_process_running=true" \
  "client1_activity_resumed=true" \
  "client2_activity_resumed=true" \
  "client1_render_surface_present=true" \
  "client2_render_surface_present=true" \
  "render_service_package_present=true" \
  "client1_data_cleared=false" \
  "client2_data_cleared=false" \
  "client2_restarted=true" \
  "system_partition_modified=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
