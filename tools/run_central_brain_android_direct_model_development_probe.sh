#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-002, APP-004, S2-MDL-001/002/003/004/005/006,
# S2-OBS-001/002, S2-SAF-001. Development validation only.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="$ROOT/central-brain/android-runtime/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
ACTIVITY="com.centralbrain.runtime/.model.DirectModelServiceDevelopmentProbeActivity"
TIMEOUT_SECONDS="${CENTRAL_BRAIN_DIRECT_MODEL_PROBE_TIMEOUT_SECONDS:-180}"
MODEL_NAME="${CENTRAL_BRAIN_OLLAMA_MODEL:-qwen3.5:27b-optimized}"
DEVICE_SERIAL="${CENTRAL_BRAIN_ADB_SERIAL:-${ANDROID_SERIAL:-testboard}}"

if [[ -n "${ADB_BIN:-}" ]]; then
  adb_base=("$ADB_BIN")
elif [[ -x "$ROOT/.tools/android-sdk/platform-tools/adb" ]]; then
  adb_base=("$ROOT/.tools/android-sdk/platform-tools/adb")
elif command -v adb >/dev/null 2>&1; then
  adb_base=("$(command -v adb)")
else
  echo "direct_model_development_probe_complete=false reason=ADB_NOT_FOUND" >&2
  exit 2
fi
adb=("${adb_base[@]}" -s "$DEVICE_SERIAL")

if ! "${adb_base[@]}" devices | awk -v serial="$DEVICE_SERIAL" \
    'NR > 1 {gsub(/\r/, "", $1); gsub(/\r/, "", $2)}
     $1 == serial && $2 == "device" {found=1} END {exit !found}'; then
  echo "direct_model_development_probe_complete=false reason=TESTBOARD_OFFLINE" >&2
  exit 3
fi

ADB_BIN="${adb_base[0]}" \
ANDROID_SERIAL="$DEVICE_SERIAL" \
CENTRAL_BRAIN_OLLAMA_MODEL="$MODEL_NAME" \
  "$ROOT/tools/start_central_brain_wsl_ollama_bridge.sh" >/dev/null

if [[ "${CENTRAL_BRAIN_SKIP_ANDROID_BUILD:-false}" != "true" ]]; then
  if [[ -f "$ROOT/env.sh" ]]; then
    # Optional local toolchain configuration; CI and integrators may export it directly.
    source "$ROOT/env.sh"
  fi
  : "${JAVA_HOME:?JAVA_HOME must point to JDK 17}"
  : "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
  : "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"
  export ANDROID_HOME
  export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
  export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT/.tools/gradle-home}"
  export PATH="$JAVA_HOME/bin:$PATH"
  "$ROOT/central-brain/android-runtime/gradlew" \
    --project-dir "$ROOT/central-brain/android-runtime" \
    --no-daemon \
    -PcentralBrainDevelopmentOllama=true \
    :runtime-service:testDebugUnitTest \
    :runtime-service:assembleDebug >/dev/null
fi
[[ -f "$APK" ]] \
  || { echo "direct_model_development_probe_complete=false reason=APK_NOT_FOUND" >&2; exit 4; }

install_output="$(mktemp)"
trap 'rm -f "$install_output"' EXIT
if ! "${adb[@]}" install -r -d -t "$APK" >"$install_output" 2>&1; then
  if grep -q 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' "$install_output"; then
    "${adb[@]}" uninstall com.centralbrain.runtime >/dev/null || true
    "${adb[@]}" install -r -d -t "$APK" >/dev/null
  else
    cat "$install_output" >&2
    echo "direct_model_development_probe_complete=false reason=APK_INSTALL_FAILED" >&2
    exit 5
  fi
fi

nonce="$(date +%s%N | cut -c1-19)"
"${adb[@]}" logcat -c
"${adb[@]}" shell am start -W -n "$ACTIVITY" --es nonce "$nonce" >/dev/null

for _ in $(seq 1 "$TIMEOUT_SECONDS"); do
  logs="$("${adb[@]}" logcat -d \
    -s CbDirectModelDevProbe:I AndroidRuntime:E '*:S' | tr -d '\r')"
  if printf '%s\n' "$logs" \
      | grep -q "nonce=$nonce direct_model_development_probe_complete="; then
    printf '%s\n' "$logs" | grep -F "nonce=$nonce"
    if printf '%s\n' "$logs" \
        | grep -q "nonce=$nonce direct_model_development_probe_complete=true"; then
      printf '%s\n' \
        'wsl_ollama_accessed=true' \
        'android_testboard_verified=true' \
        'agent_gateway_used=false' \
        'vehicle_effect_dispatch_authorized=false' \
        'ethernet_validated=false' \
        'production_model_provider_wired=false' \
        'production_ready=false' \
        'target_hardware_validated=false'
      exit 0
    fi
    exit 6
  fi
  if printf '%s\n' "$logs" \
      | grep -q 'Process: com.centralbrain.runtime'; then
    printf '%s\n' "$logs" | tail -80 >&2
    echo "direct_model_development_probe_complete=false reason=ANDROID_PROCESS_CRASHED" >&2
    exit 8
  fi
  sleep 1
done

echo "direct_model_development_probe_complete=false reason=PROBE_TIMEOUT" >&2
exit 7
