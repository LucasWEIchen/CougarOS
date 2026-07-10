#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${CENTRAL_BRAIN_TEST_PORT:-18879}"
BASE_URL="http://127.0.0.1:${PORT}"
OLLAMA_URL="${CENTRAL_BRAIN_OLLAMA_URL:-http://127.0.0.1:11434}"
OLLAMA_MODEL="${CENTRAL_BRAIN_OLLAMA_MODEL:-qwen3.5:27b-optimized}"
TMP_DIR="$(mktemp -d)"
LOG_FILE="$TMP_DIR/backend.log"

cleanup() {
  if [[ -n "${BACKEND_PID:-}" ]]; then
    kill "$BACKEND_PID" >/dev/null 2>&1 || true
    wait "$BACKEND_PID" >/dev/null 2>&1 || true
  fi
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

curl -fsS --max-time 3 "$OLLAMA_URL/api/tags" >/dev/null

CENTRAL_BRAIN_PORT="$PORT" \
CENTRAL_BRAIN_SIMULATED_NPU_BACKEND=ollama \
CENTRAL_BRAIN_OLLAMA_URL="$OLLAMA_URL" \
CENTRAL_BRAIN_OLLAMA_MODEL="$OLLAMA_MODEL" \
CENTRAL_BRAIN_OLLAMA_TIMEOUT_MS="${CENTRAL_BRAIN_OLLAMA_TIMEOUT_MS:-120000}" \
CENTRAL_BRAIN_OLLAMA_NUM_PREDICT="${CENTRAL_BRAIN_OLLAMA_NUM_PREDICT:-16}" \
  python3 "$ROOT_DIR/central-brain/backend/mock_npu_service.py" --host 127.0.0.1 --port "$PORT" >"$LOG_FILE" 2>&1 &
BACKEND_PID=$!

for _ in $(seq 1 80); do
  if curl -fsS --max-time 1 "$BASE_URL/health" >/dev/null 2>&1; then
    break
  fi
  sleep 0.25
done

curl -fsS --max-time 2 "$BASE_URL/health" >/dev/null

python3 - "$BASE_URL" "$OLLAMA_MODEL" <<'PY'
from __future__ import annotations

import json
import sys
from urllib import request

base_url = sys.argv[1]
expected_model = sys.argv[2]


def get_json(path: str) -> dict:
    with request.urlopen(base_url + path, timeout=10) as response:
        return json.loads(response.read().decode("utf-8"))


def post_json(path: str, payload: dict, timeout: int = 180) -> dict:
    encoded = json.dumps(payload).encode("utf-8")
    req = request.Request(
        base_url + path,
        data=encoded,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with request.urlopen(req, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def assert_boundary(payload: dict) -> None:
    assert payload["hardware_accessed"] is False, payload
    assert payload["driver_development_triggered"] is False, payload
    assert payload["virtualization_development_triggered"] is False, payload
    assert payload["production_ready"] is False, payload


status = get_json("/npu/status")
assert status["runtime"] == "ollama-simulated-npu", status
assert status["simulated_npu_backend"] == "ollama", status
assert status["simulated_backend_status"]["reachable"] is True, status
assert status["simulated_backend_status"]["model"] == expected_model, status
assert_boundary(status)

infer_request = {
    "runtime": "ollama",
    "model": "central-intent-v0",
    "ollama_model": expected_model,
    "input": {"utterance": "query vehicle state"},
    "policy": {"safety_state_required": "normal", "timeout_ms": 2000},
}
direct_infer = post_json("/ai/infer", infer_request)
assert direct_infer["runtime"] == "ollama-simulated-npu", direct_infer
assert direct_infer["simulated_npu_backend"] == "ollama", direct_infer
assert direct_infer["backend_model"] == expected_model, direct_infer
assert direct_infer["status"] == "ok", direct_infer
assert direct_infer["result"]["ollama_done"] is True, direct_infer
assert "generated_text" in direct_infer["result"], direct_infer
assert direct_infer["metrics"]["ollama_eval_count"] is not None, direct_infer
assert direct_infer["metrics"]["ollama_eval_count"] > 0, direct_infer
assert_boundary(direct_infer)

soa_infer = post_json(
    "/soa/invoke",
    {
        "service": "npu-inference",
        "method": "infer",
        "caller_permissions": ["ai.infer", "service.read"],
        "payload": infer_request,
    },
)
result = soa_infer["payload"]["result"]
assert result["runtime"] == "ollama-simulated-npu", soa_infer
assert result["simulated_npu_backend"] == "ollama", soa_infer
assert result["status"] == "ok", soa_infer
assert result["result"]["ollama_done"] is True, soa_infer
assert "generated_text" in result["result"], soa_infer
assert result["metrics"]["ollama_eval_count"] is not None, soa_infer
assert result["metrics"]["ollama_eval_count"] > 0, soa_infer
assert_boundary(result)

print("ollama simulated NPU smoke passed")
PY
