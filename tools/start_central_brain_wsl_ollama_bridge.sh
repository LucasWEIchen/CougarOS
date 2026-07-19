#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_NAME="${CENTRAL_BRAIN_OLLAMA_MODEL:-qwen3.5:27b-optimized}"
HOST_PORT="${CENTRAL_BRAIN_OLLAMA_PORT:-11434}"
DEVICE_PORT="${CENTRAL_BRAIN_OLLAMA_DEVICE_PORT:-11434}"

if [[ -n "${ADB_BIN:-}" ]]; then
  adb=("$ADB_BIN")
elif [[ -x /mnt/e/platform-tools/adb.exe ]]; then
  adb=(/mnt/e/platform-tools/adb.exe)
elif [[ -x "$ROOT/.tools/android-sdk/platform-tools/adb" ]]; then
  adb=("$ROOT/.tools/android-sdk/platform-tools/adb")
else
  echo "wsl_ollama_bridge_ready=false reason=ADB_NOT_FOUND" >&2
  exit 2
fi

if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  adb+=( -s "$ANDROID_SERIAL" )
fi

if ! command -v ollama >/dev/null 2>&1; then
  echo "wsl_ollama_bridge_ready=false reason=OLLAMA_NOT_FOUND" >&2
  exit 3
fi

if ! ollama list | awk 'NR > 1 {print $1}' | grep -Fxq "$MODEL_NAME"; then
  echo "wsl_ollama_bridge_ready=false reason=MODEL_NOT_INSTALLED" >&2
  exit 4
fi

if ! curl --silent --show-error --fail --max-time 5 \
    "http://127.0.0.1:${HOST_PORT}/api/version" >/dev/null; then
  echo "wsl_ollama_bridge_ready=false reason=WSL_OLLAMA_UNREACHABLE" >&2
  exit 5
fi

device_count="$("${adb[@]}" devices | awk \
  'NR > 1 {gsub(/\r/, "", $2)} $2 == "device" {count++} END {print count+0}')"
if [[ "$device_count" != "1" ]]; then
  echo "wsl_ollama_bridge_ready=false reason=ANDROID_DEVICE_COUNT_${device_count}" >&2
  exit 6
fi

"${adb[@]}" reverse "tcp:${DEVICE_PORT}" "tcp:${HOST_PORT}" >/dev/null
if ! "${adb[@]}" reverse --list | awk -v device="tcp:${DEVICE_PORT}" \
    -v host="tcp:${HOST_PORT}" \
    '{gsub(/\r/, "", $3)} $2 == device && $3 == host {found=1} END {exit !found}'; then
  echo "wsl_ollama_bridge_ready=false reason=ADB_REVERSE_NOT_REGISTERED" >&2
  exit 7
fi

if ! "${adb[@]}" shell curl -sS --fail --max-time 5 \
    "http://127.0.0.1:${DEVICE_PORT}/api/version" >/dev/null; then
  echo "wsl_ollama_bridge_ready=false reason=DEVICE_TO_WSL_PROBE_FAILED" >&2
  exit 8
fi

echo "wsl_ollama_bridge_ready=true"
echo "endpoint_profile=development_wsl_adb_reverse"
echo "device_endpoint=http://127.0.0.1:${DEVICE_PORT}"
echo "host_endpoint=http://127.0.0.1:${HOST_PORT}"
echo "ollama_model=${MODEL_NAME}"
echo "raw_identity_logged=false"
echo "production_ready=false"
echo "target_hardware_validated=false"
