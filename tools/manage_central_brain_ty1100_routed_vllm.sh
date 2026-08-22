#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMMAND="${1:-status}"

readonly AI_HOST="192.168.250.100"
readonly AI_USER="cix"
readonly SSH_KEY="${CENTRAL_BRAIN_TY1100_SSH_KEY:-${HOME}/.ssh/unicore_ty1100_ed25519}"
readonly CONTROL_SOCKET="${TMPDIR:-/tmp}/central-brain-ty1100-routed-vllm.sock"
readonly LEGACY_CONTROL_SOCKET="${TMPDIR:-/tmp}/central-brain-ty1100-vllm.sock"
readonly GENERAL_PORT="10030"
readonly GENERAL_MODEL="Qwen3.5-9B-AWQ"
readonly GENERAL_CONTEXT="8192"
readonly GENERAL_GPU_UTIL="0.55"
readonly GENERAL_MODEL_DIR="/opt/models/Qwen3.5-9B-AWQ"
readonly GENERAL_CONTAINER="central-brain-routed-qwen35-9b"
readonly SMOKING_PORT="10031"
readonly SMOKING_MODEL="Qwen3.5-2B-AWQ"
readonly SMOKING_CONTEXT="4096"
readonly SMOKING_GPU_UTIL="0.30"
readonly SMOKING_MODEL_DIR="/opt/models/Qwen3.5-2B-AWQ"
readonly SMOKING_CONTAINER="central-brain-routed-qwen35-2b"
readonly BASELINE_CONTAINER="unicore-vllm"
readonly VLLM_IMAGE="harbor.iluvatar.com.cn:10443/saas/mr-bi150-4.4.0-aarch64-ubuntu20.04-py3.10-poc-llm-infer:v1.2.5-ty1100-4.4.0"
readonly VLLM_LD_PRELOAD="/usr/local/lib/python3.10/site-packages/scikit_learn.libs/libgomp-d22c30c5.so.1.0.0"
readonly DEVICE_SERIAL="${CENTRAL_BRAIN_ANDROID_SERIAL:-testboard}"
readonly WINDOWS_ADB="/mnt/e/platform-tools/adb.exe"
readonly ADB_SERVER_PORT_VALUE="${ADB_SERVER_PORT:-5038}"

SSH=(
  ssh
  -i "$SSH_KEY"
  -o BatchMode=yes
  -o ConnectTimeout=8
  -o ExitOnForwardFailure=yes
  -o IdentitiesOnly=yes
  -o StrictHostKeyChecking=accept-new
)

die() {
  printf 'error=%s\n' "$1" >&2
  exit 1
}

remote() {
  "${SSH[@]}" "${AI_USER}@${AI_HOST}" "$1"
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

container_exists() {
  remote "sudo -n nerdctl inspect $1 >/dev/null 2>&1"
}

container_running() {
  remote "sudo -n nerdctl inspect -f '{{.State.Running}}' $1" 2>/dev/null \
    | tr -d '\r' | grep -qx true
}

tunnel_running() {
  [[ -S "$CONTROL_SOCKET" ]] \
    && "${SSH[@]}" -S "$CONTROL_SOCKET" -O check "${AI_USER}@${AI_HOST}" \
      >/dev/null 2>&1
}

validate_catalog() {
  local expected_model="$1"
  local expected_context="$2"
  python3 -c '
import json
import sys
expected_model, expected_context = sys.argv[1], int(sys.argv[2])
payload = json.load(sys.stdin)
data = payload.get("data", [])
if len(data) != 1 or data[0].get("id") != expected_model:
    raise SystemExit("model_catalog_identity_mismatch")
if data[0].get("max_model_len") != expected_context:
    raise SystemExit("model_catalog_context_mismatch")
' "$expected_model" "$expected_context"
}

verify_remote_model() {
  local port="$1"
  local model="$2"
  local context="$3"
  remote "curl -fsS --max-time 8 http://127.0.0.1:${port}/health >/dev/null"
  remote "curl -fsS --max-time 8 http://127.0.0.1:${port}/v1/models" \
    | validate_catalog "$model" "$context"
}

verify_local_model() {
  local port="$1"
  local model="$2"
  local context="$3"
  curl -fsS --max-time 8 "http://127.0.0.1:${port}/health" >/dev/null
  curl -fsS --max-time 8 "http://127.0.0.1:${port}/v1/models" \
    | validate_catalog "$model" "$context"
}

verify_remote_model_dirs() {
  remote "test -f ${GENERAL_MODEL_DIR}/config.json \
    && test -f ${SMOKING_MODEL_DIR}/config.json"
}

remove_container_if_present() {
  local name="$1"
  if container_exists "$name"; then
    remote "sudo -n nerdctl stop $name >/dev/null 2>&1 || true; \
      sudo -n nerdctl rm $name >/dev/null 2>&1 || true"
  fi
}

restore_baseline() {
  remove_container_if_present "$SMOKING_CONTAINER"
  remove_container_if_present "$GENERAL_CONTAINER"
  if ! container_running "$BASELINE_CONTAINER"; then
    remote "sudo -n nerdctl start $BASELINE_CONTAINER >/dev/null 2>&1 || true"
  fi
  local attempt
  for attempt in $(seq 1 12); do
    container_running "$BASELINE_CONTAINER" && break
    sleep 1
  done
  container_running "$BASELINE_CONTAINER" || die "baseline_restore_failed"
  remote "sudo -n nerdctl update --restart=always $BASELINE_CONTAINER >/dev/null"
}

start_model_container() {
  local name="$1"
  local model="$2"
  local model_dir="$3"
  local port="$4"
  local context="$5"
  local gpu_util="$6"
  remote "sudo -n nerdctl run -d \
    --name $name \
    --network host \
    --privileged \
    --ipc private \
    --shm-size 8g \
    --restart always \
    -e LD_PRELOAD=$VLLM_LD_PRELOAD \
    -v /dev:/dev \
    -v $model_dir:/models/$model:ro \
    $VLLM_IMAGE \
    python3 -m vllm.entrypoints.openai.api_server \
      --model /models/$model \
      --served-model-name $model \
      --gpu-memory-utilization $gpu_util \
      --max-model-len $context \
      --tensor-parallel-size 1 \
      --host 127.0.0.1 \
      --port $port \
      --default-chat-template-kwargs '{\"enable_thinking\":false}' \
      --trust-remote-code" >/dev/null
}

wait_for_models() {
  local attempt
  for attempt in $(seq 1 90); do
    if verify_remote_model "$GENERAL_PORT" "$GENERAL_MODEL" "$GENERAL_CONTEXT" \
        >/dev/null 2>&1 \
        && verify_remote_model "$SMOKING_PORT" "$SMOKING_MODEL" "$SMOKING_CONTEXT" \
        >/dev/null 2>&1; then
      return
    fi
    container_running "$GENERAL_CONTAINER" || return 1
    container_running "$SMOKING_CONTAINER" || return 1
    printf 'routed_vllm_readiness_attempt=%s\n' "$attempt"
    sleep 5
  done
  return 1
}

wait_for_one_model() {
  local container="$1"
  local port="$2"
  local model="$3"
  local context="$4"
  local attempt
  for attempt in $(seq 1 90); do
    if verify_remote_model "$port" "$model" "$context" >/dev/null 2>&1; then
      return
    fi
    container_running "$container" || return 1
    printf 'routed_vllm_%s_readiness_attempt=%s\n' "$port" "$attempt"
    sleep 5
  done
  return 1
}

print_route_failure_logs() {
  if container_exists "$GENERAL_CONTAINER"; then
    printf 'general_container_log_tail_begin\n' >&2
    remote "sudo -n nerdctl logs --tail 40 $GENERAL_CONTAINER 2>&1" >&2 || true
    printf 'general_container_log_tail_end\n' >&2
  fi
  if container_exists "$SMOKING_CONTAINER"; then
    printf 'smoking_container_log_tail_begin\n' >&2
    remote "sudo -n nerdctl logs --tail 40 $SMOKING_CONTAINER 2>&1" >&2 || true
    printf 'smoking_container_log_tail_end\n' >&2
  fi
}

stop_legacy_tunnel() {
  if [[ -S "$LEGACY_CONTROL_SOCKET" ]]; then
    "${SSH[@]}" -S "$LEGACY_CONTROL_SOCKET" -O exit "${AI_USER}@${AI_HOST}" \
      >/dev/null 2>&1 || true
    rm -f "$LEGACY_CONTROL_SOCKET"
  fi
}

start_tunnel() {
  if tunnel_running; then
    return
  fi
  stop_legacy_tunnel
  [[ ! -e "$CONTROL_SOCKET" ]] || rm -f "$CONTROL_SOCKET"
  if ss -lnt | grep -qE "127[.]0[.]0[.]1:(${GENERAL_PORT}|${SMOKING_PORT})[[:space:]]"; then
    verify_local_model "$GENERAL_PORT" "$GENERAL_MODEL" "$GENERAL_CONTEXT" \
      >/dev/null 2>&1 \
      && verify_local_model "$SMOKING_PORT" "$SMOKING_MODEL" "$SMOKING_CONTEXT" \
        >/dev/null 2>&1 \
      && return
    die "local_routed_vllm_port_in_use"
  fi
  "${SSH[@]}" -fN -M -S "$CONTROL_SOCKET" \
    -L "127.0.0.1:${GENERAL_PORT}:127.0.0.1:${GENERAL_PORT}" \
    -L "127.0.0.1:${SMOKING_PORT}:127.0.0.1:${SMOKING_PORT}" \
    "${AI_USER}@${AI_HOST}"
}

prewarm_models() {
  verify_local_model "$GENERAL_PORT" "$GENERAL_MODEL" "$GENERAL_CONTEXT"
  verify_local_model "$SMOKING_PORT" "$SMOKING_MODEL" "$SMOKING_CONTEXT"
  python3 "$ROOT_DIR/tools/prewarm_central_brain_ty1100_vllm_models.py" \
    --passes 2 \
    --output "$ROOT_DIR/central-brain/integration/ty1100-vllm-prototype/last-routed-prewarm.json"
}

configure_android_reverse() {
  local state
  state="$("${ADB[@]}" get-state | tr -d '\r')"
  [[ "$state" == "device" ]] || die "android_device_not_ready"
  "${ADB[@]}" reverse "tcp:${GENERAL_PORT}" "tcp:${GENERAL_PORT}" >/dev/null
  "${ADB[@]}" reverse "tcp:${SMOKING_PORT}" "tcp:${SMOKING_PORT}" >/dev/null
  "${ADB[@]}" shell "toybox nc -w 5 127.0.0.1 ${GENERAL_PORT} </dev/null" \
    >/dev/null 2>&1 || die "android_general_reverse_probe_failed"
  "${ADB[@]}" shell "toybox nc -w 5 127.0.0.1 ${SMOKING_PORT} </dev/null" \
    >/dev/null 2>&1 || die "android_smoking_reverse_probe_failed"
}

start_services() {
  [[ -f "$SSH_KEY" ]] || die "ty1100_ssh_key_missing"
  remote "true" >/dev/null
  verify_remote_model_dirs
  if container_running "$GENERAL_CONTAINER" && container_running "$SMOKING_CONTAINER"; then
    wait_for_models || die "existing_routed_models_not_ready"
    return
  fi

  local restore_required=true
  restore_on_failure() {
    if [[ "$restore_required" == true ]]; then
      print_route_failure_logs || true
      restore_baseline || true
    fi
  }
  trap restore_on_failure EXIT

  remove_container_if_present "$SMOKING_CONTAINER"
  remove_container_if_present "$GENERAL_CONTAINER"
  remote "sudo -n nerdctl update --restart=no $BASELINE_CONTAINER >/dev/null"
  if container_running "$BASELINE_CONTAINER"; then
    remote "sudo -n nerdctl stop $BASELINE_CONTAINER >/dev/null"
  fi
  container_running "$BASELINE_CONTAINER" && die "baseline_container_did_not_stop"
  start_model_container \
    "$GENERAL_CONTAINER" "$GENERAL_MODEL" "$GENERAL_MODEL_DIR" \
    "$GENERAL_PORT" "$GENERAL_CONTEXT" "$GENERAL_GPU_UTIL"
  wait_for_one_model \
    "$GENERAL_CONTAINER" "$GENERAL_PORT" "$GENERAL_MODEL" "$GENERAL_CONTEXT" \
    || die "general_model_readiness_failed_baseline_restored"
  start_model_container \
    "$SMOKING_CONTAINER" "$SMOKING_MODEL" "$SMOKING_MODEL_DIR" \
    "$SMOKING_PORT" "$SMOKING_CONTEXT" "$SMOKING_GPU_UTIL"
  wait_for_models || die "routed_models_readiness_failed_baseline_restored"
  restore_required=false
  trap - EXIT
}

stop_tunnel() {
  "${ADB[@]}" reverse --remove "tcp:${GENERAL_PORT}" >/dev/null 2>&1 || true
  "${ADB[@]}" reverse --remove "tcp:${SMOKING_PORT}" >/dev/null 2>&1 || true
  if tunnel_running; then
    "${SSH[@]}" -S "$CONTROL_SOCKET" -O exit "${AI_USER}@${AI_HOST}" \
      >/dev/null 2>&1 || true
  fi
  [[ ! -e "$CONTROL_SOCKET" ]] || rm -f "$CONTROL_SOCKET"
}

print_status() {
  local baseline_running=false
  local general_running=false
  local smoking_running=false
  local general_verified=false
  local smoking_verified=false
  local bridge_active=false
  local android_general_reverse=false
  local android_smoking_reverse=false
  container_running "$BASELINE_CONTAINER" && baseline_running=true
  container_running "$GENERAL_CONTAINER" && general_running=true
  container_running "$SMOKING_CONTAINER" && smoking_running=true
  verify_local_model "$GENERAL_PORT" "$GENERAL_MODEL" "$GENERAL_CONTEXT" \
    >/dev/null 2>&1 && general_verified=true
  verify_local_model "$SMOKING_PORT" "$SMOKING_MODEL" "$SMOKING_CONTEXT" \
    >/dev/null 2>&1 && smoking_verified=true
  tunnel_running && bridge_active=true
  if "${ADB[@]}" reverse --list 2>/dev/null | tr -d '\r' \
      | grep -qE "(^|[[:space:]])tcp:${GENERAL_PORT}[[:space:]]+tcp:${GENERAL_PORT}$"; then
    android_general_reverse=true
  fi
  if "${ADB[@]}" reverse --list 2>/dev/null | tr -d '\r' \
      | grep -qE "(^|[[:space:]])tcp:${SMOKING_PORT}[[:space:]]+tcp:${SMOKING_PORT}$"; then
    android_smoking_reverse=true
  fi
  printf '%s\n' \
    "profile=ty1100-routed-dual-model-v1" \
    "baseline_container_running=${baseline_running}" \
    "general_model=${GENERAL_MODEL}" \
    "general_context_tokens=${GENERAL_CONTEXT}" \
    "general_container_running=${general_running}" \
    "general_model_verified=${general_verified}" \
    "smoking_model=${SMOKING_MODEL}" \
    "smoking_context_tokens=${SMOKING_CONTEXT}" \
    "smoking_container_running=${smoking_running}" \
    "smoking_model_verified=${smoking_verified}" \
    "thinking_enabled=false" \
    "bridge_active=${bridge_active}" \
    "android_serial=${DEVICE_SERIAL}" \
    "android_general_reverse_active=${android_general_reverse}" \
    "android_smoking_reverse_active=${android_smoking_reverse}" \
    "production_configuration_changed=false" \
    "production_ready=false" \
    "target_hardware_validated=false"
}

start_all() {
  start_services
  start_tunnel
  prewarm_models
  configure_android_reverse
  print_status
}

restore_all() {
  stop_tunnel
  restore_baseline
  print_status
}

case "$COMMAND" in
  start) start_all ;;
  start-services)
    start_services
    start_tunnel
    prewarm_models
    print_status
    ;;
  prewarm)
    start_tunnel
    prewarm_models
    print_status
    ;;
  status) print_status ;;
  restore) restore_all ;;
  *) die "usage_start_start-services_prewarm_status_restore" ;;
esac
