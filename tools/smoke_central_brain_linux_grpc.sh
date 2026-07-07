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
EVENT_SUBSCRIPTIONS_OUTPUT="$(CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" event-subscriptions)"
python3 - "$EVENT_SUBSCRIPTIONS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = json.loads(response["payload_json"])
subscriptions = payload["gateway"]["payload"]
encoded = json.dumps(subscriptions)
gate_ids = {item["gate_id"] for item in subscriptions["mandatory_gates"]}
assert response["status"] == "ok", response
assert subscriptions["subscription_state"] == "contract-only-not-brokered", response
assert subscriptions["broker_active"] is False, response
assert subscriptions["active_subscriptions"] == [], response
assert {"EV-SUB-001", "EV-SUB-002", "EV-SUB-003", "EV-SUB-004", "EV-SUB-005"} <= gate_ids, response
for key in [
    "broker_active",
    "dds_runtime_active",
    "sse_websocket_active",
    "high_rate_data_plane_active",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert subscriptions["summary"][key] is False, response
assert "getEventSubscriptionsJson" in encoded, response
assert "uib.events.subscriptions.get" in encoded, response
assert "GetEventSubscriptions" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded, response
PY
EXTENSIONS_OUTPUT="$(CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" extensions)"
python3 - "$EXTENSIONS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = json.loads(response["payload_json"])
extensions = payload["gateway"]["payload"]
encoded = json.dumps(extensions)
assert response["status"] == "ok", response
assert any(item["extension_id"] == "diagnostic.trace.snapshot" for item in extensions["extensions"]), response
assert extensions["summary"]["dynamic_extension_runtime_ready"] is False, response
assert extensions["summary"]["service_dispatch_triggered"] is False, response
assert extensions["summary"]["driver_development_triggered"] is False, response
assert extensions["summary"]["virtualization_development_triggered"] is False, response
assert "FW-U-008" in encoded and "GetUibExtensions" in encoded, response
PY
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" ai-sdk >/dev/null
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" agent-plan >/dev/null
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" agent-execute >/dev/null
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" skill-invoke >/dev/null
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" memory-query >/dev/null
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" action-request >/dev/null
SERVICE_CONTRACTS_OUTPUT="$(CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" service-contracts)"
python3 - "$SERVICE_CONTRACTS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = json.loads(response["payload_json"])
contracts = payload["gateway"]["payload"]
encoded = json.dumps(contracts)
contract_names = {contract["service"] for contract in contracts["contracts"]}
assert response["status"] == "ok", response
assert "vehicle-state" in contract_names, response
assert "npu-inference" in contract_names, response
assert contracts["summary"]["service_dispatch_triggered"] is False, response
assert "FW-S-004" in encoded and "NV-G-003" in encoded, response
assert "not-dispatched" in encoded, response
PY
BINDING_READINESS_OUTPUT="$(CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" binding-readiness)"
python3 - "$BINDING_READINESS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = json.loads(response["payload_json"])
readiness = payload["gateway"]["payload"]
encoded = json.dumps(readiness)
binding_names = {row["binding"] for row in readiness["readiness"]}
assert response["status"] == "ok", response
assert "linux-grpc-rpc" in binding_names, response
assert "linux-ipc" in binding_names, response
assert "android-binder-aidl" in binding_names, response
assert readiness["summary"]["production_ready"] is False, response
assert readiness["summary"]["driver_development_triggered"] is False, response
assert readiness["summary"]["virtualization_development_triggered"] is False, response
assert "target distro" in encoded and "true gRPC runtime" in encoded, response
PY
DELIVERY_READINESS_OUTPUT="$(CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" delivery-readiness)"
python3 - "$DELIVERY_READINESS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = json.loads(response["payload_json"])
readiness = payload["gateway"]["payload"]
encoded = json.dumps(readiness)
targets = {row["target"] for row in readiness["readiness"]}
assert response["status"] == "ok", response
assert "android-debug-console" in targets, response
assert "linux-ipc-daemon-sample" in targets, response
assert "linux-grpc-rpc-sample" in targets, response
assert "driver-hal-gap-backlog" in targets, response
assert "hardware-empty-interface-registry" in targets, response
assert "vehicle-signal-activation-criteria" in targets, response
assert "vehicle-signal-validation-envelope" in targets, response
assert readiness["summary"]["production_ready"] is False, response
assert readiness["summary"]["android_debug_ready"] is True, response
assert readiness["summary"]["linux_samples_ready"] is True, response
assert readiness["summary"]["driver_development_triggered"] is False, response
assert readiness["summary"]["virtualization_development_triggered"] is False, response
assert "AAOS signing" in encoded and "target Linux distro" in encoded, response
PY
PROTOTYPE_READINESS_OUTPUT="$(CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" prototype-readiness)"
python3 - "$PROTOTYPE_READINESS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = json.loads(response["payload_json"])
readiness = payload["gateway"]["payload"]
encoded = json.dumps(readiness)
module_ids = {row["module_id"] for row in readiness["modules"]}
assert response["status"] == "ok", response
assert "ai-sdk-agent-facade" in module_ids, response
assert "uni-info-bus" in module_ids, response
assert "runtime-governance" in module_ids, response
assert "protocol-binding" in module_ids, response
assert "hardware-empty-interfaces" in module_ids, response
assert readiness["summary"]["python_prototype_ready_for_contract_demo"] is True, response
assert readiness["summary"]["production_ready"] is False, response
assert readiness["summary"]["hardware_accessed"] is False, response
assert readiness["summary"]["driver_development_triggered"] is False, response
assert readiness["summary"]["virtualization_development_triggered"] is False, response
assert readiness["summary"]["service_dispatch_triggered"] is False, response
assert "prototype.readiness.get" in encoded and "GetPrototypeReadiness" in encoded, response
assert "getVehicleSignalActivationJson" in encoded and "vehicle-signal-activation" in encoded, response
assert "getVehicleSignalValidationJson" in encoded and "vehicle-signal-validation" in encoded, response
assert "DEV-003" in encoded and "ISSUE-014" in encoded, response
PY
HARDWARE_INTERFACES_OUTPUT="$(CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" hardware-interfaces)"
python3 - "$HARDWARE_INTERFACES_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = json.loads(response["payload_json"])
hardware = payload["gateway"]["payload"]
encoded = json.dumps(hardware)
interface_ids = {item["interface_id"] for item in hardware["interfaces"]}
assert response["status"] == "ok", response
assert "npu-runtime" in interface_ids, response
assert "vehicle-bus" in interface_ids, response
assert "shared-memory-safety-runtime" in interface_ids, response
assert hardware["summary"]["implementation_state"] == "empty-interface-registry", response
assert hardware["summary"]["hardware_accessed"] is False, response
assert hardware["summary"]["driver_development_triggered"] is False, response
assert hardware["summary"]["virtualization_development_triggered"] is False, response
assert "hardware.interfaces.get" in encoded and "GetHardwareInterfaces" in encoded, response
PY
VEHICLE_SIGNALS_OUTPUT="$(CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" vehicle-signals)"
python3 - "$VEHICLE_SIGNALS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = json.loads(response["payload_json"])
vehicle_signals = payload["gateway"]["payload"]
encoded = json.dumps(vehicle_signals)
signal_paths = {item["path"] for item in vehicle_signals["signals"]}
assert response["status"] == "ok", response
assert "Vehicle.Speed" in signal_paths, response
assert "Vehicle.Cabin.HVAC.Station.Row1.Left.Temperature" in signal_paths, response
assert "Vehicle.Body.Door.Row1.Left.IsOpen" in signal_paths, response
assert vehicle_signals["summary"]["catalog_state"] == "read-only-mock-signal-catalog", response
assert vehicle_signals["summary"]["dbc_arxml_loaded"] is False, response
assert vehicle_signals["summary"]["real_vehicle_bus_connected"] is False, response
assert vehicle_signals["summary"]["hardware_accessed"] is False, response
assert vehicle_signals["summary"]["driver_development_triggered"] is False, response
assert vehicle_signals["summary"]["virtualization_development_triggered"] is False, response
assert vehicle_signals["summary"]["service_dispatch_triggered"] is False, response
assert "vehicle.signals.list" in encoded and "GetVehicleSignals" in encoded, response
assert "DRV-GAP-002" in encoded, response
PY
VEHICLE_SIGNAL_ACTIVATION_OUTPUT="$(CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" vehicle-signal-activation)"
python3 - "$VEHICLE_SIGNAL_ACTIVATION_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = json.loads(response["payload_json"])
activation = payload["gateway"]["payload"]
encoded = json.dumps(activation)
option_types = {item["source_type"] for item in activation["activation_options"]}
gate_ids = {item["gate_id"] for item in activation["mandatory_gates"]}
assert response["status"] == "ok", response
assert activation["activation_state"] == "criteria-only-not-activated", response
assert activation["read_bridge_activated"] is False, response
assert {"dbc-arxml", "android-vhal-or-vendor-aidl", "linux-socketcan", "vendor-gateway-or-someip"} <= option_types, response
assert {"VS-ACT-001", "VS-ACT-002", "VS-ACT-003", "VS-ACT-004", "VS-ACT-005"} <= gate_ids, response
for key in [
    "read_bridge_activated",
    "dbc_arxml_loaded",
    "vhal_connected",
    "socketcan_connected",
    "vendor_gateway_connected",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert activation["summary"][key] is False, response
assert "getVehicleSignalActivationJson" in encoded, response
assert "vehicle.signals.activation.get" in encoded, response
assert "GetVehicleSignalActivation" in encoded, response
assert "DRV-GAP-002" in encoded, response
PY
VEHICLE_SIGNAL_VALIDATION_OUTPUT="$(CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" vehicle-signal-validation)"
python3 - "$VEHICLE_SIGNAL_VALIDATION_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = json.loads(response["payload_json"])
validation = payload["gateway"]["payload"]
encoded = json.dumps(validation)
gate_ids = {item["gate_id"] for item in validation["mandatory_gates"]}
assert response["status"] == "ok", response
assert validation["validation_state"] == "metadata-only-not-activated", response
assert validation["read_bridge_activated"] is False, response
assert {"VS-VAL-001", "VS-VAL-002", "VS-VAL-003", "VS-VAL-004", "VS-VAL-005", "VS-VAL-006"} <= gate_ids, response
assert {"schema_source_metadata", "adapter_ownership", "android_linux_parity", "driver_gap_002", "write_path_guard"} <= set(validation["validation_envelope"]), response
for key in [
    "read_bridge_activated",
    "schema_source_attached",
    "adapter_owner_confirmed",
    "parity_evidence_attached",
    "drv_gap_002_evidence_attached",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert validation["summary"][key] is False, response
assert "getVehicleSignalValidationJson" in encoded, response
assert "vehicle.signals.validation.get" in encoded, response
assert "GetVehicleSignalValidation" in encoded, response
assert "DRV-GAP-002" in encoded, response
PY
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

DEPLOYMENT_PLAN_OUTPUT="$(CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" governance-deployment-plan)"
python3 - "$DEPLOYMENT_PLAN_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = json.loads(response["payload_json"])
deployment = payload["gateway"]["payload"]
encoded = json.dumps(deployment)
shape_ids = {item["id"] for item in deployment["deployment_shapes"]}
assert response["status"] == "ok", response
assert deployment["production_backend_ready"] is False, response
assert "GOV-DEPLOY-ANDROID-SYSTEM-SERVICE" in shape_ids, response
assert "GOV-DEPLOY-LINUX-DAEMON" in shape_ids, response
assert "GOV-DEPLOY-GRPC-RPC" in shape_ids, response
assert "governance.precheck" in encoded, response
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

GOVERNANCE_OUTPUT="$(CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" governance)"
python3 - "$GOVERNANCE_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = json.loads(response["payload_json"])
diagnostic = payload["shared_governance_diagnostic"]
assert response["status"] == "ok", response
assert payload["forwarding"] == "shared-governance-socket", response
assert diagnostic["diagnostic_source"]["operation"] == "governance.runtime.get", response
assert diagnostic["shared_daemon"]["dispatch"]["service_invoked"] is False, response
assert "NV-G-001" in json.dumps(diagnostic), response
PY

AUDIT_OUTPUT="$(CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT="$GRPC_PORT" python3 "$ROOT_DIR/central-brain/bindings/linux/grpc/central_brain_grpc_client.py" audit)"
python3 - "$AUDIT_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = json.loads(response["payload_json"])
diagnostic = payload["shared_governance_diagnostic"]
assert response["status"] == "ok", response
assert payload["forwarding"] == "shared-governance-socket", response
assert diagnostic["diagnostic_source"]["operation"] == "audit.recent.get", response
assert diagnostic["shared_daemon"]["dispatch"]["service_invoked"] is False, response
assert "NV-G-007" in json.dumps(diagnostic), response
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
assert "GetEventSubscriptions" in encoded, response
assert "GetVehicleSignals" in encoded, response
assert "GetVehicleSignalActivation" in encoded, response
assert "GetVehicleSignalValidation" in encoded, response
assert "NV-P-003" in encoded and "DEL-002" in encoded, response
PY

echo "Central Brain Linux gRPC/RPC smoke test passed on 127.0.0.1:${GRPC_PORT}"
