#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMMAND="${1:-status}"

readonly AI_HOST="192.168.250.100"
readonly AI_USER="cix"
readonly AI_VLLM_PORT="10030"
readonly EXPECTED_MODEL="Qwen3.5-9B-AWQ"
readonly LOCAL_PORT="10030"
readonly CONTROL_SOCKET="${TMPDIR:-/tmp}/central-brain-ty1100-vllm.sock"
readonly SSH_KEY="${CENTRAL_BRAIN_TY1100_SSH_KEY:-${HOME}/.ssh/unicore_ty1100_ed25519}"
readonly DEVICE_SERIAL="${CENTRAL_BRAIN_ANDROID_SERIAL:-testboard}"
readonly WINDOWS_ADB="/mnt/e/platform-tools/adb.exe"
readonly ADB_SERVER_PORT_VALUE="${ADB_SERVER_PORT:-5038}"

die() {
  printf 'error=%s\n' "$1" >&2
  exit 1
}

adb_command() {
  if [[ -x "$WINDOWS_ADB" ]]; then
    printf '%s\n' "$WINDOWS_ADB" "-P" "$ADB_SERVER_PORT_VALUE"
    return
  fi
  command -v adb >/dev/null 2>&1 || die "adb_not_found"
  printf '%s\n' "$(command -v adb)"
}

mapfile -t ADB_BASE < <(adb_command)
ADB=("${ADB_BASE[@]}" -s "$DEVICE_SERIAL")
SSH=(
  ssh
  -i "$SSH_KEY"
  -o BatchMode=yes
  -o ConnectTimeout=5
  -o ExitOnForwardFailure=yes
  -o IdentitiesOnly=yes
  -o StrictHostKeyChecking=accept-new
  -S "$CONTROL_SOCKET"
)

ssh_master_running() {
  [[ -S "$CONTROL_SOCKET" ]] \
    && "${SSH[@]}" -O check "${AI_USER}@${AI_HOST}" >/dev/null 2>&1
}

validate_model() {
  curl -fsS --max-time 10 "http://127.0.0.1:${LOCAL_PORT}/health" >/dev/null
  curl -fsS --max-time 10 "http://127.0.0.1:${LOCAL_PORT}/v1/models" \
    | python3 -c '
import json
import sys
payload = json.load(sys.stdin)
models = [item.get("id") for item in payload.get("data", [])]
if models != ["Qwen3.5-9B-AWQ"]:
    raise SystemExit("unexpected_model_catalog=" + ",".join(str(item) for item in models))
'
}

validate_android() {
  local state
  state="$("${ADB[@]}" get-state | tr -d '\r')"
  [[ "$state" == "device" ]] || die "android_device_not_ready"
  "${ADB[@]}" shell "toybox nc -w 5 127.0.0.1 ${LOCAL_PORT} </dev/null" \
    >/dev/null 2>&1 || die "android_reverse_probe_failed"
}

start_bridge() {
  [[ -f "$SSH_KEY" ]] || die "ty1100_ssh_key_missing"
  if ! ssh_master_running; then
    [[ ! -e "$CONTROL_SOCKET" ]] || rm -f "$CONTROL_SOCKET"
    if ss -lnt | grep -qE "127[.]0[.]0[.]1:${LOCAL_PORT}[[:space:]]"; then
      validate_model >/dev/null 2>&1 \
        || die "local_vllm_port_in_use_by_unverified_process"
    else
      "${SSH[@]}" -fN -M \
        -L "127.0.0.1:${LOCAL_PORT}:127.0.0.1:${AI_VLLM_PORT}" \
        "${AI_USER}@${AI_HOST}"
    fi
  fi
  validate_model
  "${ADB[@]}" reverse "tcp:${LOCAL_PORT}" "tcp:${LOCAL_PORT}" >/dev/null
  validate_android
  print_status
}

stop_bridge() {
  local bridge_active=false
  "${ADB[@]}" reverse --remove "tcp:${LOCAL_PORT}" >/dev/null 2>&1 || true
  if ssh_master_running; then
    "${SSH[@]}" -O exit "${AI_USER}@${AI_HOST}" >/dev/null
  fi
  [[ ! -e "$CONTROL_SOCKET" ]] || rm -f "$CONTROL_SOCKET"
  if validate_model >/dev/null 2>&1; then
    bridge_active=true
  fi
  printf '%s\n' \
    "bridge_active=${bridge_active}" \
    "bridge_owned=false" \
    "android_reverse_active=false" \
    "production_configuration_changed=false"
}

print_status() {
  local bridge_active=false
  local bridge_owned=false
  local model_verified=false
  local android_reverse_active=false
  if ssh_master_running; then
    bridge_active=true
    bridge_owned=true
  fi
  if validate_model >/dev/null 2>&1; then
    model_verified=true
    bridge_active=true
  fi
  if "${ADB[@]}" reverse --list 2>/dev/null | tr -d '\r' \
      | grep -qE "(^|[[:space:]])tcp:${LOCAL_PORT}[[:space:]]+tcp:${LOCAL_PORT}$"; then
    android_reverse_active=true
  fi
  printf '%s\n' \
    "prototype_provider=ty1100-vllm" \
    "ai_host=${AI_HOST}" \
    "model=${EXPECTED_MODEL}" \
    "bridge_active=${bridge_active}" \
    "bridge_owned=${bridge_owned}" \
    "model_verified=${model_verified}" \
    "android_serial=${DEVICE_SERIAL}" \
    "android_reverse_active=${android_reverse_active}" \
    "android_endpoint=http://127.0.0.1:${LOCAL_PORT}" \
    "production_configuration_changed=false" \
    "workspace=${ROOT_DIR}"
}

case "$COMMAND" in
  start) start_bridge ;;
  status) print_status ;;
  stop) stop_bridge ;;
  *) die "usage_start_status_stop" ;;
esac
