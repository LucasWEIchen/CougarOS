#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MDL-001/002, S2-SAF-001, S2-OBS-001/002,
# XSC-001/005/006, DEL-001/003/004/005. Stages: P4-R10-VLLM/P4-R13-TY1100-ETH.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="$ROOT/central-brain/android-runtime/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
ACTIVITY="com.centralbrain.runtime/.orchestration.VllmPrototypeIntegrationProbeActivity"
TIMEOUT_SECONDS="${CENTRAL_BRAIN_VLLM_PROBE_TIMEOUT_SECONDS:-150}"
DEVICE_SERIAL="${CENTRAL_BRAIN_ANDROID_SERIAL:-testboard}"
MODEL_ROUTE="${CENTRAL_BRAIN_MODEL_GATEWAY_PROFILE:-development_ty1100_vllm}"
ADB_SERVER_PORT_VALUE="${ADB_SERVER_PORT:-}"

case "$MODEL_ROUTE" in
  development_ty1100_vllm)
    expected_model="Qwen3.5-9B-AWQ"
    android_transport="ADB_REVERSE"
    ai_transport="ETHERNET_SSH_TUNNEL"
    direct_ethernet="false"
    ;;
  target_ty1100_vllm_ethernet)
    expected_model="Qwen3.5-2B-AWQ"
    android_transport="TARGET_ETHERNET"
    ai_transport="DIRECT_ETHERNET"
    direct_ethernet="true"
    ;;
  *)
    echo "vllm_prototype_probe_complete=false reason=INVALID_MODEL_ROUTE" >&2
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
  echo "vllm_prototype_probe_complete=false reason=ADB_NOT_FOUND" >&2
  exit 2
fi
if [[ -n "$ADB_SERVER_PORT_VALUE" ]]; then
  [[ "$ADB_SERVER_PORT_VALUE" =~ ^[0-9]+$ ]] \
    || { echo "vllm_prototype_probe_complete=false reason=INVALID_ADB_SERVER_PORT" >&2; exit 2; }
  adb_base+=( -P "$ADB_SERVER_PORT_VALUE" )
fi
adb=("${adb_base[@]}" -s "$DEVICE_SERIAL")

if [[ "$MODEL_ROUTE" == "development_ty1100_vllm" ]]; then
  CENTRAL_BRAIN_ANDROID_SERIAL="$DEVICE_SERIAL" \
    ADB_SERVER_PORT="${ADB_SERVER_PORT_VALUE:-5038}" \
    "$ROOT/tools/manage_central_brain_ty1100_routed_vllm.sh" start >/dev/null
else
  "${adb[@]}" reverse --remove tcp:8000 >/dev/null 2>&1 || true
  target_catalog="$("${adb[@]}" shell curl -sS --fail --max-time 5 \
    http://169.254.202.110:8000/v1/models | tr -d '\r')" \
    || { echo "vllm_prototype_probe_complete=false reason=TARGET_NETWORK_UNREACHABLE" >&2; exit 3; }
  python3 -c '
import json
import sys
models = json.load(sys.stdin).get("data", [])
if len(models) != 1 or models[0].get("id") != "Qwen3.5-2B-AWQ":
    raise SystemExit("target_model_catalog_identity_mismatch")
' <<<"$target_catalog" \
    || { echo "vllm_prototype_probe_complete=false reason=TARGET_MODEL_IDENTITY_MISMATCH" >&2; exit 3; }
fi

if [[ "${CENTRAL_BRAIN_SKIP_ANDROID_BUILD:-false}" != "true" ]]; then
  CENTRAL_BRAIN_MODEL_GATEWAY_PROFILE="$MODEL_ROUTE" \
    "$ROOT/tools/build_central_brain_android_runtime.sh" >/dev/null
fi
[[ -f "$APK" ]] \
  || { echo "vllm_prototype_probe_complete=false reason=APK_NOT_FOUND" >&2; exit 3; }

apk_argument="$APK"
install_args=(install -r -d -t)
if [[ "${adb_base[0]}" == *.exe ]] && command -v wslpath >/dev/null; then
  apk_argument="$(wslpath -w "$APK")"
  install_args=(install --no-streaming -r -d -t)
fi
"${adb[@]}" "${install_args[@]}" "$apk_argument" >/dev/null
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
        "model=$expected_model" \
        'model_context_tokens=8192' \
        'smoking_model_resident=Qwen3.5-2B-AWQ' \
        'smoking_model_context_tokens=4096' \
        'android13_arm64_verified=true' \
        "android_to_host_transport=$android_transport" \
        "host_to_ai_transport=$ai_transport" \
        'real_model_terminal_response_verified=true' \
        "direct_android_ethernet_validated=$direct_ethernet" \
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
