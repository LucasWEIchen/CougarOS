#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${CENTRAL_BRAIN_IPC_SMOKE_PORT:-19787}"
BASE_URL="http://127.0.0.1:${PORT}"
SOCKET_PATH="${CENTRAL_BRAIN_IPC_SMOKE_SOCKET:-/tmp/central_brain_gateway_smoke.sock}"
GOVERNANCE_SOCKET_PATH="${CENTRAL_BRAIN_GOVERNANCE_SMOKE_SOCKET:-/tmp/central_brain_governance_smoke.sock}"
BACKEND_LOG="$(mktemp)"
IPC_LOG="$(mktemp)"
GOVERNANCE_LOG="$(mktemp)"

cleanup() {
  if [[ -n "${IPC_PID:-}" ]] && kill -0 "$IPC_PID" 2>/dev/null; then
    kill "$IPC_PID" 2>/dev/null || true
    wait "$IPC_PID" 2>/dev/null || true
  fi
  if [[ -n "${BACKEND_PID:-}" ]] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    kill "$BACKEND_PID" 2>/dev/null || true
    wait "$BACKEND_PID" 2>/dev/null || true
  fi
  if [[ -n "${GOVERNANCE_PID:-}" ]] && kill -0 "$GOVERNANCE_PID" 2>/dev/null; then
    kill "$GOVERNANCE_PID" 2>/dev/null || true
    wait "$GOVERNANCE_PID" 2>/dev/null || true
  fi
  rm -f "$SOCKET_PATH" "$GOVERNANCE_SOCKET_PATH" "$BACKEND_LOG" "$IPC_LOG" "$GOVERNANCE_LOG"
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
  python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_daemon.py" \
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
EXTENSIONS_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" extensions)"
python3 - "$EXTENSIONS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
assert response["status"] == "ok", response
assert any(item["extension_id"] == "diagnostic.trace.snapshot" for item in payload["extensions"]), response
assert payload["summary"]["dynamic_extension_runtime_ready"] is False, response
assert payload["summary"]["service_dispatch_triggered"] is False, response
assert payload["summary"]["driver_development_triggered"] is False, response
assert payload["summary"]["virtualization_development_triggered"] is False, response
assert "FW-U-008" in encoded and "uib.extensions.get" in encoded, response
PY
CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" policy >/dev/null
CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" ai-sdk >/dev/null
CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" agent-plan >/dev/null
CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" agent-execute >/dev/null
CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" skills >/dev/null
CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" skill-invoke >/dev/null
CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" memory-query >/dev/null
CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" action-request >/dev/null
SERVICE_CONTRACTS_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" service-contracts)"
python3 - "$SERVICE_CONTRACTS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
contract_names = {contract["service"] for contract in payload["contracts"]}
assert response["status"] == "ok", response
assert "vehicle-state" in contract_names, response
assert "npu-inference" in contract_names, response
assert payload["summary"]["service_dispatch_triggered"] is False, response
assert "FW-S-004" in encoded and "NV-G-003" in encoded, response
assert "not-dispatched" in encoded, response
PY
BINDING_READINESS_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" binding-readiness)"
python3 - "$BINDING_READINESS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
binding_names = {row["binding"] for row in payload["readiness"]}
assert response["status"] == "ok", response
assert "linux-ipc" in binding_names, response
assert "linux-grpc-rpc" in binding_names, response
assert "android-binder-aidl" in binding_names, response
assert payload["summary"]["production_ready"] is False, response
assert payload["summary"]["driver_development_triggered"] is False, response
assert payload["summary"]["virtualization_development_triggered"] is False, response
assert "target distro" in encoded and "true gRPC runtime" in encoded, response
PY
DELIVERY_READINESS_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" delivery-readiness)"
python3 - "$DELIVERY_READINESS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
targets = {row["target"] for row in payload["readiness"]}
assert response["status"] == "ok", response
assert "android-debug-console" in targets, response
assert "linux-ipc-daemon-sample" in targets, response
assert "linux-grpc-rpc-sample" in targets, response
assert "driver-hal-gap-backlog" in targets, response
assert "hardware-empty-interface-registry" in targets, response
assert payload["summary"]["production_ready"] is False, response
assert payload["summary"]["android_debug_ready"] is True, response
assert payload["summary"]["linux_samples_ready"] is True, response
assert payload["summary"]["driver_development_triggered"] is False, response
assert payload["summary"]["virtualization_development_triggered"] is False, response
assert "AAOS signing" in encoded and "target Linux distro" in encoded, response
PY
HARDWARE_INTERFACES_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interfaces)"
python3 - "$HARDWARE_INTERFACES_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
interface_ids = {item["interface_id"] for item in payload["interfaces"]}
assert response["status"] == "ok", response
assert "npu-runtime" in interface_ids, response
assert "vehicle-bus" in interface_ids, response
assert "shared-memory-safety-runtime" in interface_ids, response
assert payload["summary"]["implementation_state"] == "empty-interface-registry", response
assert payload["summary"]["hardware_accessed"] is False, response
assert payload["summary"]["driver_development_triggered"] is False, response
assert payload["summary"]["virtualization_development_triggered"] is False, response
assert "hardware.interfaces.get" in encoded and "GetHardwareInterfaces" in encoded, response
PY
BACKEND_CONTRACT_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" governance-backend-contract)"
python3 - "$BACKEND_CONTRACT_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
assert response["status"] == "ok", response
assert "governance.precheck" in encoded, response
assert "governance.runtime.get" in encoded, response
assert "audit.recent.get" in encoded, response
assert "android_binder" in payload["binding_contract"], response
assert "linux_ipc" in payload["binding_contract"], response
assert "linux_grpc_rpc" in payload["binding_contract"], response
assert "Driver/HAL" in encoded and "virtualization" in encoded, response
PY
MIGRATION_CHECK_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" governance-migration-check)"
python3 - "$MIGRATION_CHECK_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
assert response["status"] == "ok", response
assert payload["production_backend_ready"] is False, response
assert "GOV-MIG-001" in encoded, response
assert "android-binder-aidl" in encoded, response
assert "linux-ipc" in encoded, response
assert "linux-grpc-rpc" in encoded, response
assert "Driver/HAL" in encoded and "virtualization" in encoded, response
PY
DEPLOYMENT_PLAN_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" governance-deployment-plan)"
python3 - "$DEPLOYMENT_PLAN_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
shape_ids = {item["id"] for item in payload["deployment_shapes"]}
assert response["status"] == "ok", response
assert payload["production_backend_ready"] is False, response
assert "GOV-DEPLOY-ANDROID-SYSTEM-SERVICE" in shape_ids, response
assert "GOV-DEPLOY-LINUX-DAEMON" in shape_ids, response
assert "GOV-DEPLOY-GRPC-RPC" in shape_ids, response
assert "governance.precheck" in encoded, response
assert "Driver/HAL" in encoded and "virtualization" in encoded, response
PY
PRECHECK_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" governance-precheck)"
python3 - "$PRECHECK_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
assert response["status"] == "ok", response
assert payload["state"] == "allowed", response
assert payload["dispatch"]["service_invoked"] is False, response
assert payload["qos_decision"]["consumed"] is False, response
assert "NV-G-004" in json.dumps(payload), response
PY
GOVERNANCE_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" governance)"
python3 - "$GOVERNANCE_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]
diagnostic = payload["shared_governance_diagnostic"]
assert response["status"] == "ok", response
assert payload["forwarding"] == "shared-governance-socket", response
assert diagnostic["diagnostic_source"]["operation"] == "governance.runtime.get", response
assert diagnostic["shared_daemon"]["dispatch"]["service_invoked"] is False, response
assert "NV-G-001" in json.dumps(diagnostic), response
PY
AUDIT_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" audit)"
python3 - "$AUDIT_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]
diagnostic = payload["shared_governance_diagnostic"]
assert response["status"] == "ok", response
assert payload["forwarding"] == "shared-governance-socket", response
assert diagnostic["diagnostic_source"]["operation"] == "audit.recent.get", response
assert diagnostic["shared_daemon"]["dispatch"]["service_invoked"] is False, response
assert "NV-G-007" in json.dumps(diagnostic), response
PY
python3 - "$GOVERNANCE_SOCKET_PATH" <<'PY'
import json
import socket
import sys

socket_path = sys.argv[1]

def call_governance(operation, payload=None):
    envelope = {
        "trace_id": f"linux-governance-smoke-{operation}",
        "operation": operation,
        "payload": payload or {},
        "req_ids": ["XSC-005", "XSC-006", "NV-P-002", "DEL-002"],
    }
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as client:
        client.connect(socket_path)
        client.sendall(json.dumps(envelope).encode("utf-8"))
        client.shutdown(socket.SHUT_WR)
        response = json.loads(client.recv(1024 * 1024).decode("utf-8"))
    assert response["status"] == "ok", response
    return response

direct_precheck = call_governance(
    "governance.precheck",
    {
        "service": "npu-inference",
        "method": "infer",
        "caller_permissions": ["ai.infer", "service.read"],
        "vehicle_state": "parked",
        "safety_state": "normal",
        "consume_qos": False,
    },
)
precheck_payload = direct_precheck["payload"]
assert precheck_payload["state"] == "allowed", direct_precheck
assert precheck_payload["dispatch"]["service_invoked"] is False, direct_precheck
assert precheck_payload["precheck_mode"]["source"] == "linux-governance-daemon", direct_precheck

runtime = call_governance("governance.runtime.get")
runtime_payload = runtime["payload"]
assert runtime_payload["registry"]["state"] == "ok", runtime
assert runtime_payload["shared_daemon"]["operation"] == "governance.runtime.get", runtime
assert runtime_payload["shared_daemon"]["dispatch"]["service_invoked"] is False, runtime
assert "NV-G-001" in json.dumps(runtime_payload), runtime

audit = call_governance("audit.recent.get", {"limit": 10})
audit_payload = audit["payload"]
encoded_audit = json.dumps(audit_payload)
assert audit_payload["shared_daemon"]["operation"] == "audit.recent.get", audit
assert "shared_governance_precheck_allowed" in encoded_audit, audit
assert "linux-governance-daemon" in encoded_audit, audit
assert "NV-G-007" in encoded_audit, audit
PY
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
assert precheck["precheck_source"]["mode"] == "shared-linux-governance-daemon", response
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
assert "soa.contracts.get" in encoded, response
assert "governance.precheck" in encoded, response
assert "governance.backend.contract.get" in encoded, response
assert "XSC-006" in encoded and "NV-P-002" in encoded and "DEL-002" in encoded, response
PY

echo "Central Brain Linux IPC smoke test passed on $SOCKET_PATH"
