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
    ("GET", "/soa/contracts", None, "NV-G-003"),
    ("GET", "/governance/runtime", None, "NV-G-005"),
    ("GET", "/governance/backend-contract", None, "NV-P-003"),
    ("GET", "/governance/migration-check", None, "DEL-004"),
    ("GET", "/governance/deployment-plan", None, "DEL-003"),
    ("GET", "/bindings", None, "NV-P-005"),
    ("GET", "/bindings/detail", None, "NV-P-002"),
    ("GET", "/bindings/readiness", None, "NV-P-003"),
    ("GET", "/delivery/readiness", None, "DEL-003"),
    ("GET", "/native/adapters", None, "NV-F-011"),
    ("GET", "/native/adapters/detail", None, "XSC-004"),
    ("GET", "/native/driver-gaps", None, "DEL-005"),
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
        "/agent/execute",
        {
            "trace_id": "smoke-agent-execute",
            "task": {
                "task_id": "task-smoke",
                "steps": [
                    {
                        "step_id": "state",
                        "type": "read_state",
                        "semantic_entry": "GET /uib/state",
                    },
                    {
                        "step_id": "query_vehicle_state",
                        "type": "invoke_service",
                        "service": "vehicle-state",
                        "method": "getState",
                        "semantic_entry": "POST /soa/invoke",
                    },
                ],
                "policy": {"required_permissions": ["vehicle.read"]},
            },
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
        "FW-U-006",
    ),
    ("GET", "/skills", None, "FW-U-006"),
    (
        "POST",
        "/skills/vehicle.state.query/invoke",
        {
            "trace_id": "smoke-skill-invoke",
            "input": {"signals": ["Vehicle.Speed"]},
            "permissions": ["vehicle.read"],
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
        "FW-U-006",
    ),
    (
        "POST",
        "/memory/query",
        {
            "trace_id": "smoke-memory-query",
            "query": "cabin temperature preference",
            "scope": "driver_profile",
            "permissions": ["vehicle.read"],
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
        "NV-F-001",
    ),
    (
        "POST",
        "/uib/actions/request",
        {
            "trace_id": "smoke-action",
            "action": "Cabin.SetTemperature",
            "target": {"zone": "row1-left", "temperature_c": 22.5},
            "permissions": ["vehicle.control"],
            "caller_permissions": ["vehicle.read", "vehicle.control"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
        "FW-U-004",
    ),
    (
        "POST",
        "/governance/precheck",
        {
            "trace_id": "smoke-governance-precheck",
            "service": "npu-inference",
            "method": "infer",
            "caller_permissions": ["ai.infer", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
            "consume_qos": False,
        },
        "NV-G-004",
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
    if path == "/bindings/readiness":
        readiness = payload["payload"]["readiness"]
        binding_names = {row["binding"] for row in readiness}
        assert "android-binder-aidl" in binding_names, "binding readiness missing Android Binder"
        assert "linux-ipc" in binding_names, "binding readiness missing Linux IPC"
        assert "linux-grpc-rpc" in binding_names, "binding readiness missing Linux gRPC/RPC"
        assert "dds" in binding_names, "binding readiness missing DDS planned path"
        assert payload["payload"]["summary"]["production_ready"] is False, "readiness overstated production maturity"
        assert payload["payload"]["summary"]["driver_development_triggered"] is False, "readiness triggered driver development"
        assert payload["payload"]["summary"]["virtualization_development_triggered"] is False, "readiness triggered virtualization development"
        assert "true gRPC runtime" in json.dumps(payload), "readiness missing gRPC blocker"
    if path == "/delivery/readiness":
        readiness = payload["payload"]["readiness"]
        target_names = {row["target"] for row in readiness}
        assert "android-debug-console" in target_names, "delivery readiness missing Android debug target"
        assert "linux-ipc-daemon-sample" in target_names, "delivery readiness missing Linux IPC target"
        assert "linux-grpc-rpc-sample" in target_names, "delivery readiness missing Linux gRPC/RPC target"
        assert "driver-hal-gap-backlog" in target_names, "delivery readiness missing Driver/HAL gap target"
        assert payload["payload"]["summary"]["production_ready"] is False, "delivery readiness overstated production maturity"
        assert payload["payload"]["summary"]["android_debug_ready"] is True, "delivery readiness missing Android debug status"
        assert payload["payload"]["summary"]["linux_samples_ready"] is True, "delivery readiness missing Linux sample status"
        assert payload["payload"]["summary"]["driver_development_triggered"] is False, "delivery readiness triggered driver development"
        assert payload["payload"]["summary"]["virtualization_development_triggered"] is False, "delivery readiness triggered virtualization development"
        assert "AAOS signing" in json.dumps(payload), "delivery readiness missing Android blocker"
        assert "target Linux distro" in json.dumps(payload), "delivery readiness missing Linux distro blocker"
    if path == "/native/adapters/detail":
        adapter_names = {adapter["name"] for adapter in payload["payload"]["adapters"]}
        assert "aios-kernel" in adapter_names
        assert "soa-service-adapter" in adapter_names
        assert "vehicle-signal-adapter" in adapter_names
        assert "model-runtime-adapter" in adapter_names
        assert payload["payload"]["driver_hal_gap_backlog"], "native detail missing driver/HAL gap backlog"
    if path == "/native/driver-gaps":
        gaps = payload["payload"]["gaps"]
        gap_ids = {gap["gap_id"] for gap in gaps}
        assert "DRV-GAP-001" in gap_ids, "driver gap backlog missing NPU gap"
        assert "DRV-GAP-002" in gap_ids, "driver gap backlog missing vehicle bus gap"
        assert payload["payload"]["summary"]["driver_development_triggered"] is False, "driver gap endpoint triggered development"
        assert "future HAL/AIDL/vendor bridge" in json.dumps(payload), "Android Driver/HAL target missing"
        assert "future device node/vendor daemon" in json.dumps(payload), "Linux Driver/HAL target missing"
    if path == "/soa/contracts":
        contracts = payload["payload"]["contracts"]
        contract_names = {contract["service"] for contract in contracts}
        assert "vehicle-state" in contract_names, "SOA contracts missing vehicle-state"
        assert "npu-inference" in contract_names, "SOA contracts missing npu-inference"
        assert payload["payload"]["summary"]["service_dispatch_triggered"] is False, "SOA contract query dispatched a service"
        assert "FW-S-004" in json.dumps(payload), "SOA contracts missing Service Contract Req ID"
        assert "NV-G-003" in json.dumps(payload), "SOA contracts missing Schema Req ID"
        assert "not-dispatched" in json.dumps(payload), "SOA contracts missing no-dispatch boundary"
    if path == "/agent/plan":
        task = payload["payload"]["task"]
        assert task["state"] == "planned", "agent plan was not accepted"
        assert task["steps"], "agent plan did not return task steps"
        assert "POST /soa/invoke" in json.dumps(task), "agent plan bypassed SOA"
    if path == "/agent/execute":
        execution = payload["payload"]["task_execution"]
        assert execution["state"] == "validated_mock", "agent execute was not validated"
        assert execution["execution_mode"] == "policy-checked-contract-mock", "agent execute left contract mock mode"
        assert "not-dispatched" in json.dumps(execution), "agent execute dispatched below semantic layer"
    if path == "/skills":
        skills = payload["payload"]["skills"]
        assert any(skill["skill_id"] == "vehicle.state.query" for skill in skills), "skill registry missing vehicle.state.query"
    if path.startswith("/skills/") and path.endswith("/invoke"):
        invocation = payload["payload"]["skill_invocation"]
        assert invocation["state"] == "completed_mock", "skill invocation was not policy accepted"
        assert invocation["execution_mode"] == "sandbox-contract-mock", "skill invocation left contract mock mode"
    if path == "/memory/query":
        memory = payload["payload"]["memory_query"]
        assert memory["state"] == "completed_mock", "memory query was not accepted"
        assert memory["privacy"]["cloud_sync"] is False, "memory query allowed cloud sync"
    if path == "/uib/actions/request":
        action = payload["payload"]
        assert action["state"] == "accepted", "action request was not accepted"
        assert action["dispatch"]["driver_hal"] == "not-dispatched", "action mock dispatched to Driver/HAL"
    if path == "/governance/precheck":
        precheck = payload["payload"]
        assert precheck["state"] == "allowed", "governance precheck was not allowed"
        assert precheck["dispatch"]["service_invoked"] is False, "governance precheck dispatched service"
        assert precheck["qos_decision"]["consumed"] is False, "diagnostic precheck consumed QoS window"
    if path == "/governance/backend-contract":
        contract = payload["payload"]
        operations = {item["operation"] for item in contract["required_operations"]}
        assert "governance.precheck" in operations, "backend contract missing precheck operation"
        assert "governance.runtime.get" in operations, "backend contract missing runtime diagnostic operation"
        assert "audit.recent.get" in operations, "backend contract missing audit diagnostic operation"
        assert "android_binder" in contract["binding_contract"], "backend contract missing Android binding"
        assert "linux_ipc" in contract["binding_contract"], "backend contract missing Linux IPC binding"
        assert "linux_grpc_rpc" in contract["binding_contract"], "backend contract missing Linux gRPC/RPC binding"
        assert "Driver/HAL" in json.dumps(contract), "backend contract missing Driver/HAL non-goal"
        assert "virtualization" in json.dumps(contract), "backend contract missing virtualization non-goal"
    if path == "/governance/migration-check":
        migration = payload["payload"]
        encoded = json.dumps(migration)
        assert migration["production_backend_ready"] is False, "migration check must not claim production backend is ready"
        assert "GOV-MIG-001" in encoded, "migration check missing SOA precheck invariant"
        assert "android-binder-aidl" in encoded, "migration check missing Android binding readiness"
        assert "linux-ipc" in encoded, "migration check missing Linux IPC readiness"
        assert "linux-grpc-rpc" in encoded, "migration check missing Linux gRPC/RPC readiness"
        assert "Driver/HAL" in encoded and "virtualization" in encoded, "migration check missing non-goal boundaries"
    if path == "/governance/deployment-plan":
        deployment = payload["payload"]
        encoded = json.dumps(deployment)
        shape_ids = {item["id"] for item in deployment["deployment_shapes"]}
        assert deployment["production_backend_ready"] is False, "deployment plan must not claim production backend is ready"
        assert "GOV-DEPLOY-ANDROID-SYSTEM-SERVICE" in shape_ids, "deployment plan missing Android system service shape"
        assert "GOV-DEPLOY-LINUX-DAEMON" in shape_ids, "deployment plan missing Linux daemon shape"
        assert "GOV-DEPLOY-GRPC-RPC" in shape_ids, "deployment plan missing gRPC/RPC shape"
        assert "governance.precheck" in encoded, "deployment plan missing governance precheck invariant"
        assert "Driver/HAL" in encoded and "virtualization" in encoded, "deployment plan missing non-goal boundaries"
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
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" service-contracts >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" events >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" event-publish >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" event-recent >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" infer >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" ai-sdk >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" agent-plan >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" agent-execute >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" skills >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" skill-invoke >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" memory-query >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" action-request >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" governance-precheck >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" audit >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" binding-detail >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" binding-readiness >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" delivery-readiness >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" native-adapters-detail >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" driver-gaps >/dev/null

echo "linux cli smoke ok"
