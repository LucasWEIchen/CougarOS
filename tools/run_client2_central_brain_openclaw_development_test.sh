#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MDL-001/002, S2-SAF-001, S2-OBS-001/002,
# XSC-001/005/006, DEL-001/003/004/005. Stage: P7-R4-OCDEV.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_APK="$ROOT/central-brain/android-runtime/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
CLIENT2_APK="$ROOT/builds/client2-central-brain/signed/client2-central-brain.debug.apk"
CLIENT2_PACKAGE="com.tuanjie.urasclient2"
CLIENT2_ACTIVITY="$CLIENT2_PACKAGE/.MainActivity"
TIMEOUT_SECONDS="${CENTRAL_BRAIN_CLIENT2_OPENCLAW_TIMEOUT_SECONDS:-180}"
SCENARIO="${CENTRAL_BRAIN_CLIENT2_SCENARIO:-cold}"

case "$SCENARIO" in
  cold)
    scenario_button="centralBrainColdButton"
    ;;
  fatigue)
    scenario_button="centralBrainTiredButton"
    ;;
  *)
    echo "client2_openclaw_development_test_complete=false reason=INVALID_SCENARIO" >&2
    exit 2
    ;;
esac

if [[ -n "${ADB_BIN:-}" ]]; then
  adb_base=("$ADB_BIN")
elif [[ -x /mnt/e/platform-tools/adb.exe ]]; then
  adb_base=(/mnt/e/platform-tools/adb.exe)
elif [[ -x "$ROOT/.tools/android-sdk/platform-tools/adb" ]]; then
  adb_base=("$ROOT/.tools/android-sdk/platform-tools/adb")
else
  echo "client2_openclaw_development_test_complete=false reason=ADB_NOT_FOUND" >&2
  exit 3
fi

adb=("${adb_base[@]}")
if [[ -n "${ANDROID_TRANSPORT_ID:-}" ]]; then
  [[ "$ANDROID_TRANSPORT_ID" =~ ^[0-9]+$ ]] \
    || { echo "client2_openclaw_development_test_complete=false reason=INVALID_TRANSPORT_ID" >&2; exit 4; }
  adb+=( -t "$ANDROID_TRANSPORT_ID" )
elif [[ -n "${ANDROID_SERIAL:-}" ]]; then
  adb+=( -s "$ANDROID_SERIAL" )
else
  device_count="$("${adb_base[@]}" devices | awk \
    'NR > 1 {gsub(/\r/, "", $2)} $2 == "device" {count++} END {print count+0}')"
  if [[ "$device_count" != "1" ]]; then
    echo "client2_openclaw_development_test_complete=false reason=ANDROID_DEVICE_COUNT_${device_count}" >&2
    exit 5
  fi
fi

if [[ "$TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] && ((TIMEOUT_SECONDS >= 30)); then
  :
else
  echo "client2_openclaw_development_test_complete=false reason=INVALID_TIMEOUT" >&2
  exit 6
fi

if [[ "${CENTRAL_BRAIN_SKIP_CLIENT2_BUILD:-false}" != "true" ]]; then
  CENTRAL_BRAIN_MODEL_GATEWAY_PROFILE=development_wsl_openclaw \
    "$ROOT/apk-labs/client2-central-brain/scripts/build_debug_apk.sh" >/dev/null
fi
for apk in "$RUNTIME_APK" "$CLIENT2_APK"; do
  [[ -f "$apk" ]] \
    || { echo "client2_openclaw_development_test_complete=false reason=APK_NOT_FOUND" >&2; exit 7; }
done

apk_argument() {
  if [[ "${adb_base[0]}" == *.exe ]] && command -v wslpath >/dev/null; then
    wslpath -w "$1"
  else
    printf '%s\n' "$1"
  fi
}

"${adb[@]}" install -r -d -t "$(apk_argument "$RUNTIME_APK")" >/dev/null
"${adb[@]}" install -r -d -t "$(apk_argument "$CLIENT2_APK")" >/dev/null

bridge_env=()
[[ -n "${ANDROID_TRANSPORT_ID:-}" ]] \
  && bridge_env+=("ANDROID_TRANSPORT_ID=$ANDROID_TRANSPORT_ID")
[[ -n "${ANDROID_SERIAL:-}" ]] \
  && bridge_env+=("ANDROID_SERIAL=$ANDROID_SERIAL")
[[ -n "${ADB_BIN:-}" ]] && bridge_env+=("ADB_BIN=$ADB_BIN")
env "${bridge_env[@]}" "$ROOT/tools/start_central_brain_wsl_openclaw_bridge.sh" >/dev/null

android_api="$("${adb[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
android_abi="$("${adb[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
display_size="$("${adb[@]}" shell wm size | tr -d '\r' | sed -n 's/.*size: //p' | tail -n 1)"
if [[ "$android_api" != "33" || "$android_abi" != arm64* \
    || "$display_size" != "1920x1080" ]]; then
  echo "client2_openclaw_development_test_complete=false reason=ANDROID_HMI_TARGET_MISMATCH" >&2
  exit 8
fi

dump_ui() {
  "${adb[@]}" shell uiautomator dump /data/local/tmp/central-brain-client2.xml >/dev/null
  "${adb[@]}" exec-out cat /data/local/tmp/central-brain-client2.xml | tr -d '\r'
}

tap_resource() {
  local resource_id="$1"
  local ui node bounds left top right bottom
  ui="$(dump_ui)"
  node="$(printf '%s\n' "$ui" \
    | grep -o "<node[^>]*resource-id=\"${CLIENT2_PACKAGE}:id/${resource_id}\"[^>]*>" \
    | head -n 1 || true)"
  bounds="$(printf '%s\n' "$node" | sed -n \
    's/.*bounds="\[\([0-9][0-9]*\),\([0-9][0-9]*\)\]\[\([0-9][0-9]*\),\([0-9][0-9]*\)\]".*/\1 \2 \3 \4/p')"
  [[ -n "$bounds" ]] || return 1
  read -r left top right bottom <<<"$bounds"
  "${adb[@]}" shell input tap "$(((left + right) / 2))" "$(((top + bottom) / 2))"
}

"${adb[@]}" shell am force-stop "$CLIENT2_PACKAGE"
"${adb[@]}" shell am start -W -n "$CLIENT2_ACTIVITY" >/dev/null
sleep 4
if ! dump_ui | grep -q "${CLIENT2_PACKAGE}:id/${scenario_button}"; then
  tap_resource centralBrainNavigationTrigger \
    || { echo "client2_openclaw_development_test_complete=false reason=NAVIGATION_TRIGGER_NOT_FOUND" >&2; exit 9; }
  sleep 1
fi

"${adb[@]}" logcat -c
tap_resource "$scenario_button" \
  || { echo "client2_openclaw_development_test_complete=false reason=SCENARIO_TRIGGER_NOT_FOUND" >&2; exit 10; }

for _ in $(seq 1 "$TIMEOUT_SECONDS"); do
  logs="$("${adb[@]}" logcat -d -v brief \
    -s CentralBrainOpenClaw:I CbClient2Orchestration:I '*:S' | tr -d '\r')"
  if printf '%s\n' "$logs" | grep -Eq \
      'client2_orchestration_command_failed=true|openclaw_inference_failed=true'; then
    printf '%s\n' "$logs" | grep -E \
      'client2_orchestration_(failure_diagnosed|command_failed)=true|openclaw_inference_failed=true' >&2
    echo "client2_openclaw_development_test_complete=false reason=RUNTIME_FAILURE" >&2
    exit 11
  fi
  if printf '%s\n' "$logs" | grep -q \
      'openclaw_inference_completed=true endpoint_profile=development_wsl_openclaw protocol=4' \
      && printf '%s\n' "$logs" | grep -Eq \
      'client2_orchestration_snapshot_projected=true .*model_projection_available=true .*simulated_only=true .*hardware_accessed=false'; then
    ui="$(dump_ui)"
    if ! printf '%s\n' "$ui" | grep -q 'RESULT / COMPLETED' \
        || ! printf '%s\n' "$ui" | grep -q 'centralBrainActuatorOverlay' \
        || ! printf '%s\n' "$ui" | grep -q 'VEHICLE BUS NOT ACCESSED'; then
      echo "client2_openclaw_development_test_complete=false reason=HMI_FEEDBACK_INCOMPLETE" >&2
      exit 12
    fi
    printf '%s\n' "$logs" | grep -E \
      'openclaw_(protocol_stage|inference_(started|completed))=|client2_orchestration_snapshot_projected=true'
    printf '%s\n' \
      'client2_openclaw_development_test_complete=true' \
      "scenario=$SCENARIO" \
      'sdk_runtime_client2_same_build=true' \
      'android13_arm64_1920x1080_verified=true' \
      'transport=ADB_REVERSE' \
      'real_wsl_openclaw_ollama_accessed=true' \
      'simulated_hmi_effect_verified=true' \
      'vehicle_effect_hardware_accessed=false' \
      'ethernet_validated=false' \
      'production_ready=false' \
      'target_hardware_validated=false'
    exit 0
  fi
  sleep 1
done

echo "client2_openclaw_development_test_complete=false reason=TEST_TIMEOUT" >&2
exit 13
