#!/usr/bin/env bash
set -euo pipefail

COMMAND="${1:-status}"

readonly AI_HOST="192.168.250.100"
readonly AI_USER="cix"
readonly SSH_KEY="${CENTRAL_BRAIN_TY1100_SSH_KEY:-${HOME}/.ssh/unicore_ty1100_ed25519}"
readonly CONTROL_SOCKET="${TMPDIR:-/tmp}/central-brain-ty1100-qwen35-2b.sock"
readonly LOCAL_PORT="10031"
readonly REMOTE_PORT="10031"
readonly MODEL="Qwen3.5-2B-AWQ"
readonly MODEL_DIR="/opt/models/Qwen3.5-2B-AWQ"
readonly BASELINE_CONTAINER="unicore-vllm"
readonly BENCHMARK_CONTAINER="central-brain-qwen35-2b-awq-benchmark"
readonly VLLM_IMAGE="harbor.iluvatar.com.cn:10443/saas/mr-bi150-4.4.0-aarch64-ubuntu20.04-py3.10-poc-llm-infer:v1.2.5-ty1100-4.4.0"
readonly VLLM_LD_PRELOAD="/usr/local/lib/python3.10/site-packages/scikit_learn.libs/libgomp-d22c30c5.so.1.0.0"
readonly WEIGHT_1_SHA256="06274f082d2a49f87ad9e21fe6002997445d74cfaea1004be03b2e8f83b57848"
readonly WEIGHT_2_SHA256="52767e7e0e561b81c14acee624ade25ec3e8aefe911310a588861a4a99d34489"

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

container_running() {
  remote "sudo -n nerdctl inspect -f '{{.State.Running}}' $1" 2>/dev/null \
    | tr -d '\r' | grep -qx true
}

tunnel_running() {
  [[ -S "$CONTROL_SOCKET" ]] \
    && "${SSH[@]}" -S "$CONTROL_SOCKET" -O check "${AI_USER}@${AI_HOST}" \
      >/dev/null 2>&1
}

verify_local_model() {
  curl -fsS --max-time 10 "http://127.0.0.1:${LOCAL_PORT}/health" >/dev/null
  curl -fsS --max-time 10 "http://127.0.0.1:${LOCAL_PORT}/v1/models" \
    | python3 -c '
import json
import sys
models = [item.get("id") for item in json.load(sys.stdin).get("data", [])]
if models != ["Qwen3.5-2B-AWQ"]:
    raise SystemExit("unexpected_model_catalog=" + ",".join(str(item) for item in models))
'
}

verify_remote_model_files() {
  local hashes
  hashes="$(remote "sudo -n sha256sum \
    $MODEL_DIR/model-00001-of-00002.safetensors \
    $MODEL_DIR/model-00002-of-00002.safetensors")"
  grep -q "^${WEIGHT_1_SHA256}  ${MODEL_DIR}/model-00001-of-00002.safetensors$" \
    <<<"$hashes" || die "candidate_weight_1_hash_mismatch"
  grep -q "^${WEIGHT_2_SHA256}  ${MODEL_DIR}/model-00002-of-00002.safetensors$" \
    <<<"$hashes" || die "candidate_weight_2_hash_mismatch"
}

start_tunnel() {
  if tunnel_running; then
    return
  fi
  [[ ! -e "$CONTROL_SOCKET" ]] || unlink "$CONTROL_SOCKET" 2>/dev/null || true
  if ss -lnt | grep -qE "127[.]0[.]0[.]1:${LOCAL_PORT}[[:space:]]"; then
    verify_local_model >/dev/null 2>&1 \
      || die "candidate_local_port_in_use_by_unverified_process"
    return
  fi
  "${SSH[@]}" -fN -M -S "$CONTROL_SOCKET" \
    -L "127.0.0.1:${LOCAL_PORT}:127.0.0.1:${REMOTE_PORT}" \
    "${AI_USER}@${AI_HOST}"
}

wait_for_candidate() {
  local attempt
  for attempt in $(seq 1 60); do
    if verify_local_model >/dev/null 2>&1; then
      return
    fi
    container_running "$BENCHMARK_CONTAINER" || return 1
    sleep 5
  done
  return 1
}

restore_baseline() {
  if remote "sudo -n nerdctl inspect $BENCHMARK_CONTAINER >/dev/null 2>&1"; then
    remote "sudo -n nerdctl stop $BENCHMARK_CONTAINER >/dev/null 2>&1 || true"
    remote "sudo -n nerdctl rm $BENCHMARK_CONTAINER >/dev/null 2>&1 || true"
  fi
  if ! container_running "$BASELINE_CONTAINER"; then
    remote "sudo -n nerdctl start $BASELINE_CONTAINER >/dev/null"
  fi
  remote "sudo -n nerdctl update --restart=always $BASELINE_CONTAINER >/dev/null"
}

start_candidate() {
  [[ -f "$SSH_KEY" ]] || die "ty1100_ssh_key_missing"
  remote "true" >/dev/null
  verify_remote_model_files
  if ! container_running "$BENCHMARK_CONTAINER"; then
    if remote "sudo -n nerdctl inspect $BENCHMARK_CONTAINER >/dev/null 2>&1"; then
      remote "sudo -n nerdctl rm $BENCHMARK_CONTAINER >/dev/null"
    fi
    remote "sudo -n nerdctl update --restart=no $BASELINE_CONTAINER >/dev/null"
    remote "sudo -n nerdctl stop $BASELINE_CONTAINER >/dev/null"
    if container_running "$BASELINE_CONTAINER"; then
      restore_baseline
      die "baseline_container_did_not_stop"
    fi
    if ! remote "sudo -n nerdctl run -d \
      --name $BENCHMARK_CONTAINER \
      --network host \
      --privileged \
      --ipc private \
      --shm-size 8g \
      --restart no \
      -e LD_PRELOAD=$VLLM_LD_PRELOAD \
      -v /dev:/dev \
      -v $MODEL_DIR:/models/$MODEL:ro \
      $VLLM_IMAGE \
      /bin/bash -lc 'exec python3 -m vllm.entrypoints.openai.api_server \
        --model /models/$MODEL \
        --served-model-name $MODEL \
        --gpu-memory-utilization 0.9 \
        --max-model-len 100000 \
        --tensor-parallel-size 1 \
        --host 127.0.0.1 \
        --port $REMOTE_PORT \
        --trust-remote-code'" >/dev/null; then
      restore_baseline
      die "candidate_container_start_failed_baseline_restored"
    fi
  fi
  start_tunnel
  if ! wait_for_candidate; then
    restore_baseline
    die "candidate_readiness_timeout_baseline_restored"
  fi
  print_status
}

restore() {
  if tunnel_running; then
    "${SSH[@]}" -S "$CONTROL_SOCKET" -O exit "${AI_USER}@${AI_HOST}" >/dev/null
  fi
  [[ ! -e "$CONTROL_SOCKET" ]] || unlink "$CONTROL_SOCKET" 2>/dev/null || true
  restore_baseline
  print_status
}

print_status() {
  local baseline_running=false
  local candidate_running=false
  local candidate_verified=false
  container_running "$BASELINE_CONTAINER" && baseline_running=true
  container_running "$BENCHMARK_CONTAINER" && candidate_running=true
  verify_local_model >/dev/null 2>&1 && candidate_verified=true
  printf '%s\n' \
    "benchmark_profile=qwen35-2b-awq" \
    "model=${MODEL}" \
    "baseline_container_running=${baseline_running}" \
    "candidate_container_running=${candidate_running}" \
    "candidate_model_verified=${candidate_verified}" \
    "local_endpoint=http://127.0.0.1:${LOCAL_PORT}" \
    "production_configuration_changed=false"
}

case "$COMMAND" in
  start) start_candidate ;;
  status) print_status ;;
  restore) restore ;;
  *) die "usage_start_status_restore" ;;
esac
