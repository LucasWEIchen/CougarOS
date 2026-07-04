#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${CENTRAL_BRAIN_QOS_TEST_PORT:-20787}"
BASE_URL="http://127.0.0.1:${PORT}"
LOG_FILE="$(mktemp)"

cleanup() {
  if [[ -n "${SERVER_PID:-}" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true
  fi
  rm -f "$LOG_FILE"
}
trap cleanup EXIT

python3 "$ROOT_DIR/central-brain/backend/mock_npu_service.py" \
  --host 127.0.0.1 --port "$PORT" >"$LOG_FILE" 2>&1 &
SERVER_PID="$!"

for _ in {1..40}; do
  if curl -fsS "$BASE_URL/health" >/dev/null 2>&1; then
    break
  fi
  sleep 0.2
done

curl -fsS "$BASE_URL/health" >/dev/null

python3 - "$BASE_URL" <<'PY'
import json
import sys
from urllib import request

base_url = sys.argv[1].rstrip("/")


def call(method, path, body=None):
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    req = request.Request(base_url + path, data=data, headers=headers, method=method)
    with request.urlopen(req, timeout=5) as response:
        return json.loads(response.read().decode("utf-8"))


def infer(trace_id):
    return call(
        "POST",
        "/soa/invoke",
        {
            "trace_id": trace_id,
            "service": "npu-inference",
            "method": "infer",
            "caller_permissions": ["ai.infer", "service.read"],
            "payload": {
                "model": "central-intent-v0",
                "input": {"utterance": "qos burst probe"},
                "policy": {"safety_state_required": "normal", "timeout_ms": 2000},
            },
        },
    )


first = infer("qos-smoke-1")
second = infer("qos-smoke-2")
third = infer("qos-smoke-3")

assert first["payload"]["state"] == "completed", "first request should pass QoS"
assert second["payload"]["state"] == "completed", "second request should pass QoS"
assert third["payload"]["state"] == "rejected", "third request should be QoS rejected"
assert third["payload"]["qos_decision"]["decision"] == "deny", "QoS decision was not deny"
assert "NV-G-004" in json.dumps(third), "QoS response missing NV-G-004"

governance = call("GET", "/governance/runtime")
assert governance["payload"]["qos"]["state"] == "active-prototype-fixed-window"
assert "POST /soa/invoke" in governance["payload"]["qos"]["enforced_on"]

audit = call("GET", "/audit/recent")
events = audit["payload"]["events"]
assert events, "audit events missing"
assert events[0]["outcome"] == "qos_rejected", "latest audit event should be qos_rejected"
assert events[0]["qos_decision"] == "deny", "audit should record QoS denial"

print("qos smoke ok")
PY

CENTRAL_BRAIN_BASE_URL="$BASE_URL" \
  python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" governance >/dev/null
