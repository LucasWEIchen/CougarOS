#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MDL-001/002, S2-SAF-001, S2-OBS-001/002,
# XSC-001/005/006, DEL-001/003/004/005. Stage: P7-R4-OCDEV.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="$ROOT/central-brain/android-runtime/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
ACTIVITY="com.centralbrain.runtime/.orchestration.OpenClawDevelopmentIntegrationProbeActivity"
TIMEOUT_SECONDS="${CENTRAL_BRAIN_OPENCLAW_PROBE_TIMEOUT_SECONDS:-150}"

if [[ -n "${ADB_BIN:-}" ]]; then
  adb_base=("$ADB_BIN")
elif [[ -x /mnt/e/platform-tools/adb.exe ]]; then
  adb_base=(/mnt/e/platform-tools/adb.exe)
elif [[ -x "$ROOT/.tools/android-sdk/platform-tools/adb" ]]; then
  adb_base=("$ROOT/.tools/android-sdk/platform-tools/adb")
else
  echo "openclaw_development_probe_complete=false reason=ADB_NOT_FOUND" >&2
  exit 2
fi

adb=("${adb_base[@]}")
if [[ -n "${ANDROID_TRANSPORT_ID:-}" ]]; then
  [[ "$ANDROID_TRANSPORT_ID" =~ ^[0-9]+$ ]] \
    || { echo "openclaw_development_probe_complete=false reason=INVALID_TRANSPORT_ID" >&2; exit 3; }
  adb+=( -t "$ANDROID_TRANSPORT_ID" )
elif [[ -n "${ANDROID_SERIAL:-}" ]]; then
  adb+=( -s "$ANDROID_SERIAL" )
else
  device_count="$("${adb_base[@]}" devices | awk \
    'NR > 1 {gsub(/\r/, "", $2)} $2 == "device" {count++} END {print count+0}')"
  if [[ "$device_count" != "1" ]]; then
    echo "openclaw_development_probe_complete=false reason=ANDROID_DEVICE_COUNT_${device_count}" >&2
    exit 4
  fi
fi

bridge_env=()
[[ -n "${ANDROID_TRANSPORT_ID:-}" ]] \
  && bridge_env+=("ANDROID_TRANSPORT_ID=$ANDROID_TRANSPORT_ID")
[[ -n "${ANDROID_SERIAL:-}" ]] \
  && bridge_env+=("ANDROID_SERIAL=$ANDROID_SERIAL")
[[ -n "${ADB_BIN:-}" ]] && bridge_env+=("ADB_BIN=$ADB_BIN")
env "${bridge_env[@]}" "$ROOT/tools/start_central_brain_wsl_openclaw_bridge.sh"

if [[ "${CENTRAL_BRAIN_SKIP_ANDROID_BUILD:-false}" != "true" ]]; then
  CENTRAL_BRAIN_MODEL_GATEWAY_PROFILE=development_wsl_openclaw \
    "$ROOT/tools/build_central_brain_android_runtime.sh" >/dev/null
fi
[[ -f "$APK" ]] \
  || { echo "openclaw_development_probe_complete=false reason=APK_NOT_FOUND" >&2; exit 5; }

"${adb[@]}" install -r -d -t "$APK" >/dev/null
nonce="$(date +%s%N | cut -c1-19)"
"${adb[@]}" logcat -c
"${adb[@]}" shell am start -W -n "$ACTIVITY" --es nonce "$nonce" >/dev/null

for _ in $(seq 1 "$TIMEOUT_SECONDS"); do
  logs="$("${adb[@]}" logcat -d \
    -s CbOpenClawDevProbe:I CentralBrainOpenClaw:I '*:S' | tr -d '\r')"
  if printf '%s\n' "$logs" \
      | grep -q "nonce=$nonce openclaw_development_probe_complete="; then
    printf '%s\n' "$logs" | grep -E \
      "nonce=$nonce|openclaw_(protocol_stage|inference_(started|completed))="
    if printf '%s\n' "$logs" \
        | grep -q "nonce=$nonce openclaw_development_probe_complete=true"; then
      printf '%s\n' \
        'development_wsl_openclaw_android13_arm64_verified=true' \
        'ethernet_validated=false' \
        'production_ready=false' \
        'target_hardware_validated=false'
      exit 0
    fi
    exit 6
  fi
  sleep 1
done

echo "openclaw_development_probe_complete=false reason=PROBE_TIMEOUT" >&2
exit 7
