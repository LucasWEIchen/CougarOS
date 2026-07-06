#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${CENTRAL_BRAIN_GRPC_SMOKE_BACKEND_PORT:-20787}"
GRPC_PORT="${CENTRAL_BRAIN_GRPC_SMOKE_PORT:-20788}"
BASE_URL="http://127.0.0.1:${PORT}"
GOVERNANCE_SOCKET_PATH="${CENTRAL_BRAIN_GOVERNANCE_SMOKE_SOCKET:-/tmp/central_brain_grpc_governance_smoke.sock}"
BACKEND_LOG="$(mktemp)"
GRPC_LOG="$(mktemp)"
GOVERNANCE_LOG="$(mktemp)"

cleanup() {
  if [[ -n "${GRPC_PID:-}" ]] && kill -0 "$GRPC_PID" 2>/dev/null; then
    kill "$GRPC_PID" 2>/dev/null || true
    wait "$GRPC_PID" 2>/dev/null || true
  fi
  if [[ -n "${BACKEND_PID:-}" ]] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    kill "$BACKEND_PID" 2>/dev/null || true
    wait "$BACKEND_PID" 2>/dev/null || true
  fi
  if [[ -n "${GOVERNANCE_PID:-}" ]] && kill -0 "$GOVERNANCE_PID" 2>/dev/null; then
    kill "$GOVERNANCE_PID" 2>/dev/null || true
    wait "$GOVERNANCE_PID" 2>/dev/null || true
  fi
  rm -f "$GOVERNANCE_SOCKET_PATH" "$BACKEND_LOG" "$GRPC_LOG" "$GOVERNANCE_LOG"
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

CENTRAL_BRAIN_GOVERNANCE_SOCKET="$GOVERNANCE_SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_governance_daemon.py" \
  --socket-path "$GOVERNANCE_SOCKET_PATH" >"$GOVERNANCE_LOG" 2>&1 &
GOVERNANCE_PID="$!"

python3 - "$GOVERNANCE_SOCKET_PATH" <<'PY'
import os
import sys
import time

socket_path = sys.argv[1]
deadline = time.time() + 8
while not os.path.exists(socket_path):
    if time.time() > deadline:
        raise TimeoutError(f"Governance socket did not appear: {socket_path}")
    time.sleep(0.2)
PY

CENTRAL_BRAIN_BASE_URL="$BASE_URL" CENTRAL_BRAIN_GOVERNANCE_SOCKET="$GOVERNANCE_SOCKET_PATH" \
  CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" \
  python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_server.py" \
  --host 127.0.0.1 --port "$GRPC_PORT" --base-url "$BASE_URL" >"$GRPC_LOG" 2>&1 &
GRPC_PID="$!"

python3 - "$GRPC_PORT" <<'PY'
import socket
import sys
import time

port = int(sys.argv[1])
deadline = time.time() + 8
while True:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as client:
        try:
            client.connect(("127.0.0.1", port))
            break
        except OSError:
            if time.time() > deadline:
                raise TimeoutError(f"gRPC/RPC sample did not listen on {port}")
            time.sleep(0.2)
PY

CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" state >/dev/null
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" events >/dev/null
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" event-publish >/dev/null
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" ai-sdk >/dev/null
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" agent-plan >/dev/null
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" agent-execute >/dev/null
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" skill-invoke >/dev/null
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" memory-query >/dev/null
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" action-request >/dev/null
BACKEND_CONTRACT_OUTPUT="$(CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" governance-backend-contract)"
python3 - "$BACKEND_CONTRACT_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = json.loads(response["payload_json"])
contract = payload["gateway"]["payload"]
encoded = json.dumps(contract)
assert response["status"] == "ok", response
assert "governance.precheck" in encoded, response
assert "governance.runtime.get" in encoded, response
assert "audit.recent.get" in encoded, response
assert "android_binder" in contract["binding_contract"], response
assert "linux_ipc" in contract["binding_contract"], response
assert "linux_grpc_rpc" in contract["binding_contract"], response
assert "Driver/HAL" in encoded and "virtualization" in encoded, response
PY

MIGRATION_CHECK_OUTPUT="$(CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" governance-migration-check)"
python3 - "$MIGRATION_CHECK_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = json.loads(response["payload_json"])
migration = payload["gateway"]["payload"]
encoded = json.dumps(migration)
assert response["status"] == "ok", response
assert migration["production_backend_ready"] is False, response
assert "GOV-MIG-001" in encoded, response
assert "android-binder-aidl" in encoded, response
assert "linux-ipc" in encoded, response
assert "linux-grpc-rpc" in encoded, response
assert "Driver/HAL" in encoded and "virtualization" in encoded, response
PY

PRECHECK_OUTPUT="$(CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" governance-precheck)"
python3 - "$PRECHECK_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = json.loads(response["payload_json"])
gateway_payload = payload["gateway"]["payload"]
assert response["status"] == "ok", response
assert gateway_payload["state"] == "allowed", response
assert gateway_payload["dispatch"]["service_invoked"] is False, response
assert gateway_payload["qos_decision"]["consumed"] is False, response
assert "NV-G-004" in json.dumps(gateway_payload), response
PY

CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" infer >/dev/null
DENIED_OUTPUT="$(CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" infer-denied)"
python3 - "$DENIED_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = json.loads(response["payload_json"])
precheck = payload["grpc_governance_precheck"]
assert response["status"] == "ok", response
assert payload["forwarding"] == "blocked-before-rest-gateway", response
assert "gateway" not in payload, response
assert precheck["state"] == "rejected", response
assert precheck["policy"]["decision"] == "deny", response
assert precheck["precheck_source"]["mode"] == "shared-linux-governance-daemon", response
assert "XSC-005" in json.dumps(precheck), response
assert "NV-G-005" in json.dumps(precheck), response
PY

BINDINGS_OUTPUT="$(CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" bindings)"
python3 - "$BINDINGS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = json.loads(response["payload_json"])
encoded = json.dumps(payload)
assert response["status"] == "ok", response
assert "grpc" in encoded, response
assert "grpc-json-active-sample" in encoded, response
assert "NV-P-003" in encoded and "DEL-002" in encoded, response
PY

echo "Central Brain Linux gRPC/RPC smoke test passed on 127.0.0.1:${GRPC_PORT}"
