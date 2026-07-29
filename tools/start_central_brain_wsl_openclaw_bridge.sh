#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MDL-001/002, S2-SAF-001, S2-OBS-001/002,
# XSC-001/005/006, DEL-001/003/004/005. Stage: P7-R4-OCDEV.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOST_PORT="${CENTRAL_BRAIN_OPENCLAW_HOST_PORT:-18789}"
DEVICE_PORT="${CENTRAL_BRAIN_OPENCLAW_DEVICE_PORT:-18789}"
MODEL_HOST_PORT="${CENTRAL_BRAIN_OPENCLAW_MODEL_PORT:-11435}"
MODEL_NAME="${CENTRAL_BRAIN_OPENCLAW_MODEL:-qwen3.6:27b}"

if [[ -n "${ADB_BIN:-}" ]]; then
  adb_base=("$ADB_BIN")
elif [[ -x /mnt/e/platform-tools/adb.exe ]]; then
  adb_base=(/mnt/e/platform-tools/adb.exe)
elif [[ -x "$ROOT/.tools/android-sdk/platform-tools/adb" ]]; then
  adb_base=("$ROOT/.tools/android-sdk/platform-tools/adb")
else
  echo "wsl_openclaw_bridge_ready=false reason=ADB_NOT_FOUND" >&2
  exit 2
fi

if [[ -n "${ADB_SERVER_PORT:-}" ]]; then
  [[ "$ADB_SERVER_PORT" =~ ^[0-9]+$ ]] \
    || { echo "wsl_openclaw_bridge_ready=false reason=INVALID_ADB_SERVER_PORT" >&2; exit 3; }
  adb_base+=( -P "$ADB_SERVER_PORT" )
fi

adb=("${adb_base[@]}")
if [[ -n "${ANDROID_TRANSPORT_ID:-}" ]]; then
  [[ "$ANDROID_TRANSPORT_ID" =~ ^[0-9]+$ ]] \
    || { echo "wsl_openclaw_bridge_ready=false reason=INVALID_TRANSPORT_ID" >&2; exit 3; }
  adb+=( -t "$ANDROID_TRANSPORT_ID" )
elif [[ -n "${ANDROID_SERIAL:-}" ]]; then
  adb+=( -s "$ANDROID_SERIAL" )
else
  device_count="$("${adb_base[@]}" devices | awk \
    'NR > 1 {gsub(/\r/, "", $2)} $2 == "device" {count++} END {print count+0}')"
  if [[ "$device_count" != "1" ]]; then
    echo "wsl_openclaw_bridge_ready=false reason=ANDROID_DEVICE_COUNT_${device_count}" >&2
    exit 4
  fi
fi

if ! systemctl --user is-active --quiet openclaw-gateway.service; then
  echo "wsl_openclaw_bridge_ready=false reason=OPENCLAW_SERVICE_INACTIVE" >&2
  exit 5
fi
if ! curl --silent --show-error --fail --max-time 5 \
    "http://127.0.0.1:${HOST_PORT}/" >/dev/null; then
  echo "wsl_openclaw_bridge_ready=false reason=OPENCLAW_GATEWAY_UNREACHABLE" >&2
  exit 6
fi
if ! curl --silent --show-error --fail --max-time 5 \
    "http://127.0.0.1:${MODEL_HOST_PORT}/api/tags" \
    | python3 -c 'import json,sys; expected=sys.argv[1]; data=json.load(sys.stdin); raise SystemExit(0 if expected in {m.get("name") for m in data.get("models", [])} else 1)' \
      "$MODEL_NAME"; then
  echo "wsl_openclaw_bridge_ready=false reason=OPENCLAW_MODEL_UNAVAILABLE" >&2
  exit 7
fi

android_api="$("${adb[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
android_abi="$("${adb[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
if [[ "$android_api" != "33" || "$android_abi" != arm64* ]]; then
  echo "wsl_openclaw_bridge_ready=false reason=ANDROID_TARGET_MISMATCH" >&2
  exit 8
fi

"${adb[@]}" reverse "tcp:${DEVICE_PORT}" "tcp:${HOST_PORT}" >/dev/null
if ! "${adb[@]}" reverse --list | awk -v device="tcp:${DEVICE_PORT}" \
    -v host="tcp:${HOST_PORT}" \
    '{gsub(/\r/, "", $3)} $2 == device && $3 == host {found=1} END {exit !found}'; then
  echo "wsl_openclaw_bridge_ready=false reason=ADB_REVERSE_NOT_REGISTERED" >&2
  exit 9
fi
if ! "${adb[@]}" shell curl -sS --fail --max-time 5 \
    "http://127.0.0.1:${DEVICE_PORT}/" >/dev/null; then
  echo "wsl_openclaw_bridge_ready=false reason=DEVICE_TO_WSL_PROBE_FAILED" >&2
  exit 10
fi

echo "wsl_openclaw_bridge_ready=true"
echo "endpoint_profile=development_wsl_openclaw"
echo "transport=ADB_REVERSE"
echo "device_endpoint=ws://127.0.0.1:${DEVICE_PORT}/"
echo "host_endpoint=ws://127.0.0.1:${HOST_PORT}/"
echo "model_available=true"
echo "android13_arm64_verified=true"
echo "ethernet_validated=false"
echo "raw_identity_logged=false"
echo "credential_logged=false"
echo "production_ready=false"
echo "target_hardware_validated=false"
