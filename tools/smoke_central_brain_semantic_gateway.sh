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
    ("GET", "/soa/services", None, "FW-S-004"),
    ("GET", "/governance/runtime", None, "NV-G-005"),
    ("GET", "/bindings", None, "NV-P-005"),
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
]

for method, path, body, req_id in checks:
    payload = call(method, path, body)
    encoded = json.dumps(payload)
    assert req_id in encoded, f"{path} missing {req_id}"
    assert payload.get("status", "ok") == "ok", f"{path} status not ok"

print("semantic gateway smoke ok")
PY

CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" state >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" infer >/dev/null

echo "linux cli smoke ok"
