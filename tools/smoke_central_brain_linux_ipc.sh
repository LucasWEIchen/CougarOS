#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${CENTRAL_BRAIN_IPC_SMOKE_PORT:-19787}"
BASE_URL="http://127.0.0.1:${PORT}"
SOCKET_PATH="${CENTRAL_BRAIN_IPC_SMOKE_SOCKET:-/tmp/central_brain_gateway_smoke.sock}"
BACKEND_LOG="$(mktemp)"
IPC_LOG="$(mktemp)"

cleanup() {
  if [[ -n "${IPC_PID:-}" ]] && kill -0 "$IPC_PID" 2>/dev/null; then
    kill "$IPC_PID" 2>/dev/null || true
    wait "$IPC_PID" 2>/dev/null || true
  fi
  if [[ -n "${BACKEND_PID:-}" ]] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    kill "$BACKEND_PID" 2>/dev/null || true
    wait "$BACKEND_PID" 2>/dev/null || true
  fi
  rm -f "$SOCKET_PATH" "$BACKEND_LOG" "$IPC_LOG"
}
trap cleanup EXIT

python3 "$ROOT_DIR/central-brain/backend/mock_npu_service.py" --host 127.0.0.1 --port "$PORT" >"$BACKEND_LOG" 2>&1 &
BACKEND_PID="$!"

python3 - "$BASE_URL" <<'PY'
import sys
import time
from urllib import request
from urllib.error import URLError

base_url = sys.argv[1].rstrip("/")
deadline = time.time() + 8
while True:
    try:
        with request.urlopen(base_url + "/health", timeout=2) as response:
            if response.status == 200:
                break
    except (OSError, URLError):
        if time.time() > deadline:
            raise
        time.sleep(0.2)
PY

CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_daemon.py" \
  --socket-path "$SOCKET_PATH" --base-url "$BASE_URL" >"$IPC_LOG" 2>&1 &
IPC_PID="$!"

python3 - "$SOCKET_PATH" <<'PY'
import os
import sys
import time

socket_path = sys.argv[1]
deadline = time.time() + 8
while not os.path.exists(socket_path):
    if time.time() > deadline:
        raise TimeoutError(f"IPC socket did not appear: {socket_path}")
    time.sleep(0.2)
PY

CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" state >/dev/null
CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" events >/dev/null
CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-publish >/dev/null
CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-recent >/dev/null
CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" policy >/dev/null
CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" ai-sdk >/dev/null
CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" agent-plan >/dev/null
CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" agent-execute >/dev/null
CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" skills >/dev/null
CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" skill-invoke >/dev/null
CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" memory-query >/dev/null
CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" action-request >/dev/null
CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" infer >/dev/null
DENIED_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" infer-denied)"
python3 - "$DENIED_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]
precheck = payload["ipc_governance_precheck"]
assert response["status"] == "ok", response
assert payload["forwarding"] == "blocked-before-rest-gateway", response
assert "gateway" not in payload, response
assert precheck["state"] == "rejected", response
assert precheck["policy"]["decision"] == "deny", response
assert "XSC-005" in json.dumps(precheck), response
assert "NV-G-005" in json.dumps(precheck), response
PY
CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" audit >/dev/null

python3 - "$SOCKET_PATH" <<'PY'
import json
import socket
import sys

socket_path = sys.argv[1]
envelope = {
    "trace_id": "linux-ipc-smoke-bindings",
    "operation": "bindings.list",
    "payload": {},
    "req_ids": ["XSC-006", "NV-P-002", "DEL-002"],
}
with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as client:
    client.connect(socket_path)
    client.sendall(json.dumps(envelope).encode("utf-8"))
    client.shutdown(socket.SHUT_WR)
    response = json.loads(client.recv(1024 * 1024).decode("utf-8"))

encoded = json.dumps(response)
assert response["status"] == "ok", response
assert "linux-ipc" in encoded, response
assert "active-sample" in encoded, response
assert "agent.plan" in encoded, response
assert "agent.execute" in encoded, response
assert "skills.invoke" in encoded, response
assert "memory.query" in encoded, response
assert "uib.actions.request" in encoded, response
assert "XSC-006" in encoded and "NV-P-002" in encoded and "DEL-002" in encoded, response
PY

echo "Central Brain Linux IPC smoke test passed on $SOCKET_PATH"
