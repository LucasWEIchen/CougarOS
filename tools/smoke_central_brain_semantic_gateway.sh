#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${CENTRAL_BRAIN_SMOKE_PORT:-18787}"
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

python3 "$ROOT_DIR/central-brain/backend/mock_npu_service.py" --host 127.0.0.1 --port "$PORT" >"$LOG_FILE" 2>&1 &
SERVER_PID="$!"

python3 - "$BASE_URL" <<'PY'
import json
import sys
import time
from urllib import request
from urllib.error import URLError

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


deadline = time.time() + 8
while True:
    try:
        health = call("GET", "/health")
        assert health["status"] == "ok"
        break
    except (AssertionError, OSError, URLError):
        if time.time() > deadline:
            raise
        time.sleep(0.2)

checks = [
    ("GET", "/uib/context", None, "FW-U-001"),
    ("GET", "/uib/state", None, "FW-U-002"),
    ("GET", "/uib/events/topics", None, "FW-U-003"),
    (
        "POST",
        "/uib/events/publish",
        {
            "trace_id": "smoke-event",
            "topic": "vehicle.signal.changed",
            "source": "semantic-gateway-smoke",
            "safety_state": "normal",
            "payload": {"signal": "Vehicle.Speed", "value": 0},
        },
        "NV-P-006",
    ),
    ("GET", "/uib/events/recent", None, "FW-U-003"),
    ("GET", "/soa/services", None, "FW-S-004"),
    ("GET", "/governance/runtime", None, "NV-G-005"),
    ("GET", "/bindings", None, "NV-P-005"),
    ("GET", "/bindings/detail", None, "NV-P-002"),
    ("GET", "/native/adapters", None, "NV-F-011"),
    ("GET", "/native/adapters/detail", None, "XSC-004"),
    ("GET", "/ai/sdk/capabilities", None, "XSC-001"),
    (
        "POST",
        "/agent/plan",
        {
            "trace_id": "smoke-agent-plan",
            "utterance": "query vehicle state",
            "caller": {"app_id": "semantic-gateway-smoke", "role": "debug_console"},
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
        "APP-004",
    ),
    (
        "POST",
        "/policy/evaluate",
        {
            "trace_id": "smoke-policy-deny",
            "permissions": ["vehicle.control"],
            "caller_permissions": ["vehicle.read"],
            "vehicle_state": "driving",
            "safety_state": "normal",
        },
        "NV-G-005",
    ),
    (
        "POST",
        "/soa/invoke",
        {
            "service": "npu-inference",
            "method": "infer",
            "caller_permissions": ["ai.infer", "service.read"],
            "payload": {
                "model": "central-intent-v0",
                "input": {"utterance": "query vehicle state"},
                "policy": {"safety_state_required": "normal", "timeout_ms": 2000},
            },
        },
        "FW-S-005",
    ),
    ("GET", "/audit/recent", None, "NV-G-007"),
]

for method, path, body, req_id in checks:
    payload = call(method, path, body)
    encoded = json.dumps(payload)
    assert req_id in encoded, f"{path} missing {req_id}"
    assert payload.get("status", "ok") == "ok", f"{path} status not ok"
    if path == "/bindings/detail":
        artifacts = payload["payload"]["contract_artifacts"]
        assert "central-brain/bindings/android/aidl/com/centralbrain/binding/ICentralBrainGateway.aidl" in artifacts
        assert "central-brain/bindings/linux/proto/central_brain_gateway.proto" in artifacts
        assert "central-brain/bindings/linux/ipc/central_brain_ipc_envelope.schema.json" in artifacts
    if path == "/native/adapters/detail":
        adapter_names = {adapter["name"] for adapter in payload["payload"]["adapters"]}
        assert "aios-kernel" in adapter_names
        assert "soa-service-adapter" in adapter_names
        assert "vehicle-signal-adapter" in adapter_names
        assert "model-runtime-adapter" in adapter_names
    if path == "/agent/plan":
        task = payload["payload"]["task"]
        assert task["state"] == "planned", "agent plan was not accepted"
        assert task["steps"], "agent plan did not return task steps"
        assert "POST /soa/invoke" in json.dumps(task), "agent plan bypassed SOA"
    if path == "/uib/events/recent":
        events = payload["payload"]["events"]
        assert events, "event recent endpoint did not keep the published event"
        assert events[0]["topic"] == "vehicle.signal.changed", "latest event topic mismatch"
    if path == "/audit/recent":
        events = payload["payload"]["events"]
        assert events, "audit endpoint did not record the SOA call"
        assert events[0]["outcome"] == "completed", "latest audit event is not the SOA completion"

print("semantic gateway smoke ok")
PY

CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" state >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" events >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" event-publish >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" event-recent >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" infer >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" ai-sdk >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" agent-plan >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" audit >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" binding-detail >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" native-adapters-detail >/dev/null

echo "linux cli smoke ok"
