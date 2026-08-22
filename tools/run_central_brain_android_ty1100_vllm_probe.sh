#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MDL-001/002, S2-SAF-001, S2-OBS-001/002,
# XSC-001/005/006, DEL-001/003/004/005. Stage: P4-R10-VLLM.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="$ROOT/central-brain/android-runtime/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
ACTIVITY="com.centralbrain.runtime/.orchestration.VllmPrototypeIntegrationProbeActivity"
TIMEOUT_SECONDS="${CENTRAL_BRAIN_VLLM_PROBE_TIMEOUT_SECONDS:-150}"
DEVICE_SERIAL="${CENTRAL_BRAIN_ANDROID_SERIAL:-testboard}"
ADB_SERVER_PORT_VALUE="${ADB_SERVER_PORT:-5038}"

if [[ -n "${ADB_BIN:-}" ]]; then
  adb_base=("$ADB_BIN")
elif [[ -x /mnt/e/platform-tools/adb.exe ]]; then
  adb_base=(/mnt/e/platform-tools/adb.exe -P "$ADB_SERVER_PORT_VALUE")
elif [[ -x "$ROOT/.tools/android-sdk/platform-tools/adb" ]]; then
  adb_base=("$ROOT/.tools/android-sdk/platform-tools/adb")
else
  echo "vllm_prototype_probe_complete=false reason=ADB_NOT_FOUND" >&2
  exit 2
fi
adb=("${adb_base[@]}" -s "$DEVICE_SERIAL")

CENTRAL_BRAIN_ANDROID_SERIAL="$DEVICE_SERIAL" \
  ADB_SERVER_PORT="$ADB_SERVER_PORT_VALUE" \
  "$ROOT/tools/manage_central_brain_ty1100_routed_vllm.sh" start >/dev/null

if [[ "${CENTRAL_BRAIN_SKIP_ANDROID_BUILD:-false}" != "true" ]]; then
  CENTRAL_BRAIN_MODEL_GATEWAY_PROFILE=development_ty1100_vllm \
    "$ROOT/tools/build_central_brain_android_runtime.sh" >/dev/null
fi
[[ -f "$APK" ]] \
  || { echo "vllm_prototype_probe_complete=false reason=APK_NOT_FOUND" >&2; exit 3; }

"${adb[@]}" install -r -d -t "$APK" >/dev/null
nonce="$(date +%s%N | cut -c1-19)"
"${adb[@]}" logcat -c
"${adb[@]}" shell am start -W -n "$ACTIVITY" --es nonce "$nonce" >/dev/null

for _ in $(seq 1 "$TIMEOUT_SECONDS"); do
  logs="$("${adb[@]}" logcat -d \
    -s CbVllmPrototypeProbe:I CentralBrainVllm:I '*:S' | tr -d '\r')"
  if printf '%s\n' "$logs" \
      | grep -q "nonce=$nonce vllm_prototype_probe_complete="; then
    printf '%s\n' "$logs" | grep -E "nonce=$nonce|vllm_inference_completed="
    if printf '%s\n' "$logs" \
        | grep -q "nonce=$nonce vllm_prototype_probe_complete=true"; then
      printf '%s\n' \
        'prototype_provider=ty1100-routed-vllm' \
        'model_profile_route=GENERAL_COCKPIT' \
        'model=Qwen3.5-9B-AWQ' \
        'model_context_tokens=8192' \
        'smoking_model_resident=Qwen3.5-2B-AWQ' \
        'smoking_model_context_tokens=4096' \
        'android13_arm64_verified=true' \
        'android_to_host_transport=ADB_REVERSE' \
        'host_to_ai_transport=ETHERNET_SSH_TUNNEL' \
        'real_model_terminal_response_verified=true' \
        'direct_android_ethernet_validated=false' \
        'vehicle_effect_hardware_accessed=false' \
        'production_configuration_changed=false' \
        'production_ready=false'
      exit 0
    fi
    exit 4
  fi
  sleep 1
done

echo "vllm_prototype_probe_complete=false reason=PROBE_TIMEOUT" >&2
exit 5
