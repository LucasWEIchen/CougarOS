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
    ("GET", "/uib/events/subscriptions", None, "NV-P-006"),
    (
        "POST",
        "/uib/events/subscriptions/request",
        {
            "trace_id": "smoke-event-subscribe-request",
            "subscription_id": "smoke-contract-sub",
            "topics": ["vehicle.signal.changed"],
            "filters": {"source": "semantic-gateway-smoke", "safety_state": "normal"},
            "cursor": {"replay_limit": 5},
            "delivery": {"mode": "contract-only", "callback": "not-registered"},
            "caller": {"app_id": "semantic-gateway-smoke", "role": "test"},
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
        "NV-P-006",
    ),
    (
        "POST",
        "/uib/events/subscriptions/cancel",
        {
            "trace_id": "smoke-event-subscribe-cancel",
            "subscription_id": "smoke-contract-sub",
            "caller": {"app_id": "semantic-gateway-smoke", "role": "test"},
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
        "NV-P-006",
    ),
    ("GET", "/uib/events/subscriptions/transport-readiness", None, "NV-P-006"),
    ("GET", "/uib/events/subscriptions/decision-matrix", None, "NV-P-006"),
    ("GET", "/uib/events/subscriptions/activation-checklist", None, "NV-P-006"),
    ("GET", "/uib/events/subscriptions/callback-watch-shape", None, "NV-P-006"),
    ("GET", "/uib/events/subscriptions/cursor-replay-storage", None, "NV-P-006"),
    ("GET", "/uib/events/subscriptions/backpressure-qos-evidence", None, "NV-P-006"),
    ("GET", "/uib/events/subscriptions/readiness-rollup", None, "NV-P-006"),
    (
        "POST",
        "/uib/events/subscriptions/activation-evidence",
        {
            "trace_id": "smoke-event-subscription-activation-evidence",
            "evidence_submission_id": "smoke-activation-evidence",
            "target_gate_ids": ["EV-ACT-001", "EV-RU-001", "DRV-GAP-004"],
            "evidence_refs": [
                {
                    "ref_id": "smoke-evidence-doc",
                    "type": "doc",
                    "uri_or_path": "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md",
                    "owner": "semantic-gateway-smoke",
                    "summary": "contract-only evidence reference sample",
                }
            ],
            "reviewer": {"app_id": "semantic-gateway-smoke", "role": "test"},
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
        "NV-P-006",
    ),
    ("GET", "/uib/events/subscriptions/activation-evidence/status", None, "NV-P-006"),
    ("GET", "/uib/events/subscriptions/activation-evidence/retention-checklist", None, "NV-P-006"),
    ("GET", "/uib/events/subscriptions/activation-evidence/decision-status-rollup", None, "NV-P-006"),
    ("GET", "/uib/extensions", None, "FW-U-008"),
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
    ("GET", "/prototype/readiness", None, "DEL-003"),
    ("GET", "/native/adapters", None, "NV-F-011"),
    ("GET", "/native/adapters/detail", None, "XSC-004"),
    ("GET", "/native/driver-gaps", None, "DEL-005"),
    ("GET", "/hardware/interfaces", None, "HW-002"),
    ("GET", "/hardware/interfaces/activation-checklist", None, "HW-002"),
    ("GET", "/hardware/interfaces/owner-decision-status", None, "HW-002"),
    (
        "POST",
        "/hardware/interfaces/owner-decision-evidence",
        {
            "trace_id": "smoke-hardware-owner-decision-evidence",
            "evidence_submission_id": "smoke-hw-owner-evidence",
            "target_interface_ids": ["npu-runtime"],
            "target_gate_ids": ["HW-ODS-001", "HW-ODS-006", "DRV-GAP-001"],
            "evidence_refs": [
                {
                    "ref_id": "smoke-hw-owner-doc",
                    "type": "owner_approval",
                    "uri_or_path": "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md",
                    "owner": "semantic-gateway-smoke",
                    "summary": "contract-only hardware owner evidence reference",
                }
            ],
            "reviewer": {"app_id": "semantic-gateway-smoke", "role": "test"},
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
        "HW-002",
    ),
    ("GET", "/hardware/interfaces/owner-decision-evidence/status", None, "HW-002"),
    ("GET", "/hardware/interfaces/owner-decision-evidence/retention-checklist", None, "HW-002"),
    ("GET", "/hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist", None, "HW-002"),
    ("GET", "/hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist", None, "HW-002"),
    ("GET", "/hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup", None, "HW-002"),
    (
        "POST",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run",
        {
            "dry_run_request_id": "semantic-smoke-hw-adapter-load-dry-run",
            "selected_interface_id": "npu-runtime",
            "selected_adapter_id": "target-platform-npu-adapter",
            "adapter_version": "0.0.0-contract",
            "evidence_refs": [
                {
                    "ref_id": "semantic-smoke-hw-adapter-load-approval",
                    "type": "owner_approval",
                    "uri_or_path": "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md",
                    "owner": "semantic-gateway-smoke",
                    "summary": "contract-only adapter-load dry-run approval reference",
                }
            ],
            "requested_by": {"app_id": "semantic-gateway-smoke", "role": "test"},
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
        "HW-002",
    ),
    ("GET", "/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status", None, "HW-002"),
    ("GET", "/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/audit-consistency", None, "HW-002"),
    ("GET", "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist", None, "HW-002"),
    ("GET", "/vehicle/signals", None, "NV-F-004"),
    ("GET", "/vehicle/signals/activation", None, "NV-F-005"),
    ("GET", "/vehicle/signals/validation", None, "NV-F-005"),
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
        assert "hardware-empty-interface-registry" in target_names, "delivery readiness missing hardware empty-interface target"
        assert "vehicle-signal-activation-criteria" in target_names, "delivery readiness missing vehicle signal activation target"
        assert "vehicle-signal-validation-envelope" in target_names, "delivery readiness missing vehicle signal validation target"
        assert payload["payload"]["summary"]["production_ready"] is False, "delivery readiness overstated production maturity"
        assert payload["payload"]["summary"]["android_debug_ready"] is True, "delivery readiness missing Android debug status"
        assert payload["payload"]["summary"]["linux_samples_ready"] is True, "delivery readiness missing Linux sample status"
        assert payload["payload"]["summary"]["driver_development_triggered"] is False, "delivery readiness triggered driver development"
        assert payload["payload"]["summary"]["virtualization_development_triggered"] is False, "delivery readiness triggered virtualization development"
        assert "AAOS signing" in json.dumps(payload), "delivery readiness missing Android blocker"
        assert "target Linux distro" in json.dumps(payload), "delivery readiness missing Linux distro blocker"
    if path == "/prototype/readiness":
        readiness = payload["payload"]
        module_ids = {row["module_id"] for row in readiness["modules"]}
        assert "ai-sdk-agent-facade" in module_ids, "prototype readiness missing AI SDK module"
        assert "uni-info-bus" in module_ids, "prototype readiness missing Uni Info Bus module"
        assert "runtime-governance" in module_ids, "prototype readiness missing governance module"
        assert "protocol-binding" in module_ids, "prototype readiness missing Protocol Binding module"
        assert "hardware-empty-interfaces" in module_ids, "prototype readiness missing hardware empty-interface module"
        assert readiness["summary"]["python_prototype_ready_for_contract_demo"] is True, "prototype readiness missing contract-demo status"
        assert readiness["summary"]["production_ready"] is False, "prototype readiness overstated production maturity"
        assert readiness["summary"]["hardware_accessed"] is False, "prototype readiness touched hardware"
        assert readiness["summary"]["driver_development_triggered"] is False, "prototype readiness triggered driver development"
        assert readiness["summary"]["virtualization_development_triggered"] is False, "prototype readiness triggered virtualization development"
        assert readiness["summary"]["service_dispatch_triggered"] is False, "prototype readiness dispatched a service"
        encoded = json.dumps(readiness)
        assert "getPrototypeReadinessJson" in encoded, "Android prototype readiness binding visibility missing"
        assert "prototype.readiness.get" in encoded, "Linux IPC prototype readiness binding visibility missing"
        assert "GetPrototypeReadiness" in encoded, "gRPC prototype readiness binding visibility missing"
        assert "getVehicleSignalActivationJson" in encoded, "Android vehicle signal activation readiness missing"
        assert "vehicle-signal-activation" in encoded, "Linux vehicle signal activation readiness missing"
        assert "getVehicleSignalValidationJson" in encoded, "Android vehicle signal validation readiness missing"
        assert "vehicle-signal-validation" in encoded, "Linux vehicle signal validation readiness missing"
        assert "DEV-003" in encoded and "ISSUE-014" in encoded, "prototype readiness missing tracked deviation/issue visibility"
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
    if path == "/hardware/interfaces":
        hardware = payload["payload"]
        interface_ids = {item["interface_id"] for item in hardware["interfaces"]}
        assert "npu-runtime" in interface_ids, "hardware interfaces missing NPU runtime stub"
        assert "vehicle-bus" in interface_ids, "hardware interfaces missing vehicle bus stub"
        assert "shared-memory-safety-runtime" in interface_ids, "hardware interfaces missing safety runtime stub"
        assert hardware["summary"]["implementation_state"] == "empty-interface-registry", "hardware interfaces left empty-interface state"
        assert hardware["summary"]["hardware_accessed"] is False, "hardware endpoint touched hardware"
        assert hardware["summary"]["driver_development_triggered"] is False, "hardware endpoint triggered driver development"
        assert hardware["summary"]["virtualization_development_triggered"] is False, "hardware endpoint triggered virtualization development"
        assert "getHardwareInterfacesJson" in json.dumps(hardware), "Android hardware binding visibility missing"
        assert "hardware.interfaces.get" in json.dumps(hardware), "Linux IPC hardware binding visibility missing"
        assert "GetHardwareInterfaces" in json.dumps(hardware), "gRPC hardware binding visibility missing"
    if path == "/hardware/interfaces/activation-checklist":
        checklist = payload["payload"]
        encoded = json.dumps(checklist)
        gate_ids = {item["gate_id"] for item in checklist["mandatory_gates"]}
        assert checklist["activation_checklist_state"] == "contract-only-no-hardware-activation", "hardware activation checklist left contract-only state"
        assert checklist["activation_allowed"] is False, "hardware activation checklist allowed activation"
        assert {"HW-ACT-001", "HW-ACT-002", "HW-ACT-003", "HW-ACT-004", "HW-ACT-005", "HW-ACT-006", "HW-ACT-007", "HW-ACT-008"} <= gate_ids, "hardware activation checklist missing mandatory gates"
        assert checklist["owner_decision_shape"]["owner_decision_complete"] is False, "hardware activation owner decisions unexpectedly complete"
        assert checklist["test_evidence_shape"]["target_hardware_smoke_attached"] is False, "target hardware smoke unexpectedly attached"
        for key in [
            "owner_decision_complete",
            "android_abi_confirmed",
            "linux_abi_confirmed",
            "driver_gap_review_complete",
            "safety_policy_binding_confirmed",
            "target_hardware_smoke_attached",
            "activation_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert checklist["summary"][key] is False, f"hardware activation summary unexpectedly set {key}"
        assert checklist["summary"]["hardware_activation_checklist_active"] is True, "hardware activation checklist not active"
        assert "getHardwareInterfaceActivationChecklistJson" in encoded, "Android hardware activation binding missing"
        assert "hardware-interface-activation-checklist" in encoded, "Linux CLI hardware activation binding missing"
        assert "hardware.interfaces.activation.checklist" in encoded, "Linux IPC hardware activation binding missing"
        assert "GetHardwareInterfaceActivationChecklist" in encoded, "gRPC hardware activation binding missing"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware activation checklist missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-status":
        status = payload["payload"]
        encoded = json.dumps(status)
        gate_ids = {item["gate_id"] for item in status["decision_gates"]}
        open_gate_ids = set(status["rollup"]["open_gate_ids"])
        assert status["owner_decision_status_state"] == "contract-only-owner-decisions-open", "hardware owner status left contract-only state"
        assert status["activation_allowed"] is False, "hardware owner status allowed activation"
        assert {"HW-ODS-001", "HW-ODS-002", "HW-ODS-003", "HW-ODS-004", "HW-ODS-005", "HW-ODS-006", "HW-ODS-007", "HW-ODS-008"} <= gate_ids, "hardware owner status missing mandatory gates"
        assert {"HW-ODS-001", "HW-ODS-002", "HW-ODS-003", "HW-ODS-004", "HW-ODS-005", "HW-ODS-006", "HW-ODS-007"} <= open_gate_ids, "hardware owner status missing open gates"
        assert status["rollup"]["blocked_interface_count"] == len(status["interfaces"]), "hardware owner status blocked count mismatch"
        for key in [
            "all_required_owners_assigned",
            "target_interface_owner_assigned",
            "android_abi_owner_assigned",
            "linux_abi_owner_assigned",
            "driver_gap_owner_assigned",
            "safety_policy_owner_assigned",
            "target_hardware_smoke_attached",
            "rollback_fault_semantics_confirmed",
            "activation_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert status["summary"][key] is False, f"hardware owner status summary unexpectedly set {key}"
        assert status["summary"]["owner_decision_status_active"] is True, "hardware owner status not active"
        assert "getHardwareInterfaceOwnerDecisionStatusJson" in encoded, "Android hardware owner status binding missing"
        assert "hardware-interface-owner-decision-status" in encoded, "Linux CLI hardware owner status binding missing"
        assert "hardware.interfaces.owner.decision.status" in encoded, "Linux IPC hardware owner status binding missing"
        assert "GetHardwareInterfaceOwnerDecisionStatus" in encoded, "gRPC hardware owner status binding missing"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware owner status missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence":
        evidence = payload["payload"]
        encoded = json.dumps(evidence)
        gate_ids = {item["gate_id"] for item in evidence["mandatory_gates"]}
        assert evidence["operation"] == "hardware-owner-decision-evidence", "hardware owner evidence operation mismatch"
        assert evidence["evidence_intake_state"] == "validated_contract_only", "hardware owner evidence was not contract validated"
        assert evidence["intake_validated"] is True, "hardware owner evidence intake not validated"
        assert evidence["unknown_interface_ids"] == [], "hardware owner evidence reported unknown interface ids"
        assert evidence["invalid_evidence_ref_indexes"] == [], "hardware owner evidence reported invalid reference shape"
        assert evidence["validation"]["evidence_refs_shape_valid"] is True, "hardware owner evidence reference shape not validated"
        assert {"HW-ODE-001", "HW-ODE-002", "HW-ODE-003", "HW-ODE-004", "HW-ODE-005", "HW-ODE-006", "HW-ODE-007", "HW-ODE-008"} <= gate_ids, "hardware owner evidence missing mandatory gates"
        assert evidence["review_result"]["accepted_for_review"] is False, "hardware owner evidence was accepted for review"
        assert evidence["review_result"]["evidence_persisted"] is False, "hardware owner evidence was persisted"
        assert evidence["review_result"]["review_queue_updated"] is False, "hardware owner evidence updated a review queue"
        assert evidence["review_result"]["owner_assigned"] is False, "hardware owner evidence assigned an owner"
        assert evidence["review_result"]["gates_closed"] is False, "hardware owner evidence closed gates"
        for key in [
            "owner_decision_evidence_accepted_for_review",
            "owner_decision_evidence_persisted",
            "review_queue_updated",
            "owner_assigned",
            "gate_state_changed",
            "gates_closed",
            "activation_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert evidence["summary"][key] is False, f"hardware owner evidence summary unexpectedly set {key}"
        assert evidence["summary"]["owner_decision_evidence_contract_active"] is True, "hardware owner evidence contract not active"
        assert evidence["summary"]["owner_decision_evidence_validated"] is True, "hardware owner evidence contract not validated"
        assert "submitHardwareInterfaceOwnerDecisionEvidenceJson" in encoded, "Android hardware owner evidence binding missing"
        assert "hardware-interface-owner-decision-evidence" in encoded, "Linux CLI hardware owner evidence binding missing"
        assert "hardware.interfaces.owner.decision.evidence" in encoded, "Linux IPC hardware owner evidence binding missing"
        assert "SubmitHardwareInterfaceOwnerDecisionEvidence" in encoded, "gRPC hardware owner evidence binding missing"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware owner evidence missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/status":
        status = payload["payload"]
        encoded = json.dumps(status)
        gate_ids = {item["gate_id"] for item in status["mandatory_gates"]}
        assert status["owner_decision_evidence_status_state"] == "contract-only-no-evidence-store", "hardware owner evidence status left no-store state"
        assert status["review_pipeline"]["evidence_store_active"] is False, "hardware owner evidence status activated an evidence store"
        assert status["review_pipeline"]["review_workflow_active"] is False, "hardware owner evidence status activated a review workflow"
        assert status["counters"]["persisted_submission_count"] == 0, "hardware owner evidence status reported persisted submissions"
        assert status["counters"]["pending_review_count"] == 0, "hardware owner evidence status reported pending reviews"
        assert {"HW-OES-001", "HW-OES-002", "HW-OES-003", "HW-OES-004", "HW-OES-005", "HW-OES-006", "HW-OES-007", "HW-OES-008"} <= gate_ids, "hardware owner evidence status missing mandatory gates"
        for key in [
            "owner_decision_evidence_accepted_for_review",
            "owner_decision_evidence_persisted",
            "evidence_store_active",
            "review_workflow_active",
            "review_queue_updated",
            "owner_assigned",
            "gate_state_changed",
            "gates_closed",
            "activation_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert status["summary"][key] is False, f"hardware owner evidence status summary unexpectedly set {key}"
        assert status["summary"]["owner_decision_evidence_status_contract_active"] is True, "hardware owner evidence status contract not active"
        assert status["summary"]["review_status_available"] is True, "hardware owner evidence status not available"
        assert status["summary"]["persisted_submission_count"] == 0, "hardware owner evidence status summary reported persisted submissions"
        assert status["summary"]["pending_review_count"] == 0, "hardware owner evidence status summary reported pending reviews"
        assert "getHardwareInterfaceOwnerDecisionEvidenceStatusJson" in encoded, "Android hardware owner evidence status binding missing"
        assert "hardware-interface-owner-decision-evidence-status" in encoded, "Linux CLI hardware owner evidence status binding missing"
        assert "hardware.interfaces.owner.decision.evidence.status" in encoded, "Linux IPC hardware owner evidence status binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceStatus" in encoded, "gRPC hardware owner evidence status binding missing"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware owner evidence status missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/retention-checklist":
        retention = payload["payload"]
        encoded = json.dumps(retention)
        gate_ids = {item["gate_id"] for item in retention["mandatory_gates"]}
        assert retention["retention_closure_checklist_state"] == "contract-only-retention-closure-checklist-open", "hardware owner evidence retention checklist left contract-only state"
        assert retention["storage_activation_allowed"] is False, "hardware owner evidence retention activated storage"
        assert retention["gate_closure_allowed"] is False, "hardware owner evidence retention allowed gate closure"
        assert retention["owner_decision_complete"] is False, "hardware owner evidence retention completed owner decision"
        assert retention["evidence_uri_rules"]["uri_rules_confirmed"] is False, "hardware owner evidence URI rules unexpectedly confirmed"
        assert retention["retention_policy_shape"]["retention_policy_confirmed"] is False, "hardware owner evidence retention policy unexpectedly confirmed"
        assert {"HW-OER-001", "HW-OER-002", "HW-OER-003", "HW-OER-004", "HW-OER-005", "HW-OER-006", "HW-OER-007", "HW-OER-008"} <= gate_ids, "hardware owner evidence retention checklist missing mandatory gates"
        for key in [
            "owner_decision_complete",
            "retention_policy_confirmed",
            "evidence_uri_rules_confirmed",
            "review_workflow_owner_confirmed",
            "gate_closure_authority_confirmed",
            "deletion_export_semantics_confirmed",
            "rollback_fault_closure_confirmed",
            "approval_signature_confirmed",
            "evidence_store_active",
            "review_workflow_active",
            "delete_workflow_active",
            "export_workflow_active",
            "review_queue_updated",
            "owner_assigned",
            "gate_state_changed",
            "gates_closed",
            "activation_allowed",
            "storage_activation_allowed",
            "gate_closure_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert retention["summary"][key] is False, f"hardware owner evidence retention summary unexpectedly set {key}"
        assert retention["summary"]["owner_decision_evidence_retention_checklist_active"] is True, "hardware owner evidence retention checklist not active"
        assert retention["summary"]["owner_decision_evidence_status_contract_active"] is True, "hardware owner evidence status contract link missing"
        assert retention["summary"]["owner_decision_evidence_contract_active"] is True, "hardware owner evidence intake contract link missing"
        assert retention["summary"]["persisted_submission_count"] == 0, "hardware owner evidence retention summary reported persisted submissions"
        assert retention["summary"]["pending_review_count"] == 0, "hardware owner evidence retention summary reported pending reviews"
        assert "getHardwareInterfaceOwnerDecisionEvidenceRetentionChecklistJson" in encoded, "Android hardware owner evidence retention binding missing"
        assert "hardware-interface-owner-decision-evidence-retention-checklist" in encoded, "Linux CLI hardware owner evidence retention binding missing"
        assert "hardware.interfaces.owner.decision.evidence.retention.checklist" in encoded, "Linux IPC hardware owner evidence retention binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceRetentionChecklist" in encoded, "gRPC hardware owner evidence retention binding missing"
        assert "HW-OER-006" in encoded and "delete-export-semantics" in encoded, "hardware owner evidence retention missing delete/export gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware owner evidence retention missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist":
        replacement = payload["payload"]
        encoded = json.dumps(replacement)
        gate_ids = {item["gate_id"] for item in replacement["mandatory_gates"]}
        assert replacement["replacement_trigger_checklist_state"] == "contract-only-replacement-trigger-checklist-open", "hardware owner evidence replacement checklist left contract-only state"
        assert replacement["replacement_allowed"] is False, "hardware owner evidence replacement allowed adapter replacement"
        assert replacement["adapter_activation_allowed"] is False, "hardware owner evidence replacement activated an adapter"
        assert replacement["gate_closure_allowed"] is False, "hardware owner evidence replacement allowed gate closure"
        assert replacement["owner_decision_complete"] is False, "hardware owner evidence replacement completed owner decision"
        assert replacement["replacement_policy_shape"]["replacement_policy_confirmed"] is False, "replacement policy unexpectedly confirmed"
        assert replacement["replacement_policy_shape"]["adapter_readiness_criteria_confirmed"] is False, "adapter readiness criteria unexpectedly confirmed"
        assert replacement["rollback_policy_shape"]["rollback_to_empty_interface_plan_confirmed"] is False, "rollback-to-empty-interface plan unexpectedly confirmed"
        assert {"HW-OET-001", "HW-OET-002", "HW-OET-003", "HW-OET-004", "HW-OET-005", "HW-OET-006", "HW-OET-007", "HW-OET-008"} <= gate_ids, "hardware owner evidence replacement checklist missing mandatory gates"
        assert all(item["replacement_allowed"] is False for item in replacement["replacement_targets"]), "replacement target allowed replacement"
        assert all(item["adapter_activation_allowed"] is False for item in replacement["replacement_targets"]), "replacement target allowed adapter activation"
        assert all(item["driver_hal_development_triggered"] is False for item in replacement["replacement_targets"]), "replacement target triggered Driver/HAL work"
        for key in [
            "owner_decision_complete",
            "replacement_policy_confirmed",
            "replacement_target_selected",
            "adapter_readiness_criteria_confirmed",
            "driver_hal_gap_closure_evidence_confirmed",
            "android_linux_abi_replacement_parity_confirmed",
            "rollback_to_empty_interface_plan_confirmed",
            "safety_policy_replacement_review_confirmed",
            "smoke_harness_replacement_evidence_confirmed",
            "evidence_store_active",
            "review_workflow_active",
            "delete_workflow_active",
            "export_workflow_active",
            "review_queue_updated",
            "owner_assigned",
            "gate_state_changed",
            "gates_closed",
            "activation_allowed",
            "replacement_allowed",
            "adapter_activation_allowed",
            "gate_closure_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert replacement["summary"][key] is False, f"hardware owner evidence replacement summary unexpectedly set {key}"
        assert replacement["summary"]["owner_decision_evidence_replacement_trigger_checklist_active"] is True, "hardware owner evidence replacement checklist not active"
        assert replacement["summary"]["owner_decision_evidence_retention_checklist_active"] is True, "hardware owner evidence retention contract link missing"
        assert replacement["summary"]["owner_decision_evidence_status_contract_active"] is True, "hardware owner evidence status contract link missing"
        assert replacement["summary"]["owner_decision_evidence_contract_active"] is True, "hardware owner evidence intake contract link missing"
        assert "getHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklistJson" in encoded, "Android hardware owner evidence replacement binding missing"
        assert "hardware-interface-owner-decision-evidence-replacement-trigger-checklist" in encoded, "Linux CLI hardware owner evidence replacement binding missing"
        assert "hardware.interfaces.owner.decision.evidence.replacement.trigger.checklist" in encoded, "Linux IPC hardware owner evidence replacement binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklist" in encoded, "gRPC hardware owner evidence replacement binding missing"
        assert "HW-OET-005" in encoded and "rollback-to-empty-interface" in encoded, "hardware owner evidence replacement missing rollback gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware owner evidence replacement missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist":
        selected = payload["payload"]
        encoded = json.dumps(selected)
        gate_ids = {item["gate_id"] for item in selected["mandatory_gates"]}
        assert selected["selected_adapter_readiness_checklist_state"] == "contract-only-selected-adapter-readiness-checklist-open", "hardware selected adapter checklist left contract-only state"
        assert selected["adapter_candidate_recorded"] is False, "selected adapter candidate unexpectedly recorded"
        assert selected["adapter_load_allowed"] is False, "selected adapter checklist allowed adapter load"
        assert selected["adapter_activation_allowed"] is False, "selected adapter checklist allowed adapter activation"
        assert selected["hardware_access_allowed"] is False, "selected adapter checklist allowed hardware access"
        assert selected["gate_closure_allowed"] is False, "selected adapter checklist allowed gate closure"
        assert selected["owner_decision_complete"] is False, "selected adapter checklist completed owner decision"
        assert selected["adapter_evidence_shape"]["adapter_owner_assigned"] is False, "adapter owner unexpectedly assigned"
        assert selected["adapter_evidence_shape"]["adapter_interface_contract_approved"] is False, "adapter contract unexpectedly approved"
        assert selected["adapter_evidence_shape"]["driver_hal_gap_evidence_attached"] is False, "Driver/HAL evidence unexpectedly attached"
        assert selected["adapter_load_policy_shape"]["load_policy_confirmed"] is False, "adapter load policy unexpectedly confirmed"
        assert {"HW-OEA-001", "HW-OEA-002", "HW-OEA-003", "HW-OEA-004", "HW-OEA-005", "HW-OEA-006", "HW-OEA-007", "HW-OEA-008"} <= gate_ids, "hardware selected adapter checklist missing mandatory gates"
        assert all(item["adapter_candidate_recorded"] is False for item in selected["selected_adapter_candidates"]), "adapter candidate was recorded"
        assert all(item["adapter_load_allowed"] is False for item in selected["selected_adapter_candidates"]), "adapter candidate allowed load"
        assert all(item["adapter_activation_allowed"] is False for item in selected["selected_adapter_candidates"]), "adapter candidate allowed activation"
        assert all(item["hardware_access_allowed"] is False for item in selected["selected_adapter_candidates"]), "adapter candidate allowed hardware access"
        assert all(item["driver_hal_development_triggered"] is False for item in selected["selected_adapter_candidates"]), "adapter candidate triggered Driver/HAL work"
        for key in [
            "owner_decision_complete",
            "adapter_candidate_recorded",
            "adapter_owner_assigned",
            "adapter_interface_contract_approved",
            "driver_hal_gap_evidence_attached",
            "android_linux_binding_parity_approved",
            "safety_policy_fault_model_reviewed",
            "smoke_harness_plan_attached",
            "rollback_to_empty_interface_reviewed",
            "load_policy_confirmed",
            "evidence_store_active",
            "review_workflow_active",
            "review_queue_updated",
            "owner_assigned",
            "gate_state_changed",
            "gates_closed",
            "activation_allowed",
            "replacement_allowed",
            "adapter_load_allowed",
            "adapter_activation_allowed",
            "hardware_access_allowed",
            "gate_closure_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert selected["summary"][key] is False, f"hardware selected adapter summary unexpectedly set {key}"
        assert selected["summary"]["owner_decision_evidence_selected_adapter_readiness_checklist_active"] is True, "hardware selected adapter checklist not active"
        assert selected["summary"]["owner_decision_evidence_replacement_trigger_checklist_active"] is True, "hardware replacement contract link missing"
        assert selected["summary"]["owner_decision_evidence_retention_checklist_active"] is True, "hardware retention contract link missing"
        assert selected["summary"]["owner_decision_evidence_status_contract_active"] is True, "hardware status contract link missing"
        assert selected["summary"]["owner_decision_evidence_contract_active"] is True, "hardware owner evidence contract link missing"
        assert "getHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklistJson" in encoded, "Android hardware selected adapter binding missing"
        assert "hardware-interface-owner-decision-evidence-selected-adapter-readiness-checklist" in encoded, "Linux CLI hardware selected adapter binding missing"
        assert "hardware.interfaces.owner.decision.evidence.selected.adapter.readiness.checklist" in encoded, "Linux IPC hardware selected adapter binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklist" in encoded, "gRPC hardware selected adapter binding missing"
        assert "HW-OEA-007" in encoded and "rollback-to-empty-interface" in encoded, "hardware selected adapter missing rollback gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware selected adapter missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup":
        rollup = payload["payload"]
        encoded = json.dumps(rollup)
        gate_ids = {item["gate_id"] for item in rollup["mandatory_gates"]}
        assert rollup["adapter_load_blocker_rollup_state"] == "contract-only-adapter-load-blockers-open", "hardware adapter load blocker rollup left contract-only state"
        assert rollup["adapter_load_blocker_rollup_active"] is True, "hardware adapter load blocker rollup not active"
        assert rollup["adapter_load_ready"] is False, "hardware adapter load unexpectedly ready"
        assert rollup["adapter_load_allowed"] is False, "hardware adapter load unexpectedly allowed"
        assert rollup["adapter_activation_allowed"] is False, "hardware adapter activation unexpectedly allowed"
        assert rollup["hardware_access_allowed"] is False, "hardware access unexpectedly allowed"
        assert rollup["gate_closure_allowed"] is False, "gate closure unexpectedly allowed"
        assert rollup["owner_decision_complete"] is False, "owner decision unexpectedly complete"
        assert rollup["all_blockers_cleared"] is False, "all blockers unexpectedly cleared"
        assert {"HW-ALB-001", "HW-ALB-002", "HW-ALB-003", "HW-ALB-004", "HW-ALB-005", "HW-ALB-006", "HW-ALB-007", "HW-ALB-008"} <= gate_ids, "hardware adapter load blocker rollup missing mandatory gates"
        source_names = {item["source"] for item in rollup["source_checklists"]}
        assert {"activation-checklist", "owner-decision-status", "owner-evidence-status", "owner-evidence-retention-checklist", "replacement-trigger-checklist", "selected-adapter-readiness-checklist"} <= source_names, "hardware adapter load rollup missing source checklists"
        assert all(item["adapter_load_allowed"] is False for item in rollup["per_interface_blockers"]), "per-interface blocker allowed adapter load"
        assert all(item["adapter_activation_allowed"] is False for item in rollup["per_interface_blockers"]), "per-interface blocker allowed adapter activation"
        assert all(item["hardware_access_allowed"] is False for item in rollup["per_interface_blockers"]), "per-interface blocker allowed hardware access"
        assert all(item["adapter_load_blocked"] is True for item in rollup["per_interface_blockers"]), "per-interface blocker not marked blocked"
        for key in [
            "owner_decision_complete",
            "all_blockers_cleared",
            "adapter_load_ready",
            "adapter_candidate_recorded",
            "adapter_owner_assigned",
            "adapter_interface_contract_approved",
            "driver_hal_gap_evidence_attached",
            "android_linux_binding_parity_approved",
            "safety_policy_fault_model_reviewed",
            "smoke_harness_plan_attached",
            "rollback_to_empty_interface_reviewed",
            "load_policy_confirmed",
            "replacement_policy_confirmed",
            "replacement_allowed",
            "evidence_store_active",
            "review_workflow_active",
            "review_queue_updated",
            "owner_assigned",
            "gate_state_changed",
            "gates_closed",
            "activation_allowed",
            "adapter_load_allowed",
            "adapter_activation_allowed",
            "hardware_access_allowed",
            "gate_closure_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert rollup["summary"][key] is False, f"hardware adapter load blocker rollup summary unexpectedly set {key}"
        assert rollup["summary"]["owner_decision_evidence_adapter_load_blocker_rollup_active"] is True, "hardware adapter load blocker rollup summary not active"
        assert rollup["summary"]["owner_decision_evidence_selected_adapter_readiness_checklist_active"] is True, "hardware selected adapter contract link missing"
        assert rollup["summary"]["owner_decision_evidence_replacement_trigger_checklist_active"] is True, "hardware replacement contract link missing"
        assert rollup["summary"]["owner_decision_evidence_retention_checklist_active"] is True, "hardware retention contract link missing"
        assert rollup["summary"]["owner_decision_evidence_status_contract_active"] is True, "hardware status contract link missing"
        assert rollup["summary"]["owner_decision_evidence_contract_active"] is True, "hardware evidence contract link missing"
        assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollupJson" in encoded, "Android hardware adapter load blocker binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-blocker-rollup" in encoded, "Linux CLI hardware adapter load blocker binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.blocker.rollup" in encoded, "Linux IPC hardware adapter load blocker binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollup" in encoded, "gRPC hardware adapter load blocker binding missing"
        assert "HW-ALB-006" in encoded and "safety-policy-smoke-rollback" in encoded, "hardware adapter load blocker missing safety/smoke/rollback gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware adapter load blocker missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run":
        dry_run = payload["payload"]
        encoded = json.dumps(dry_run)
        gate_ids = {item["gate_id"] for item in dry_run["mandatory_gates"]}
        assert dry_run["adapter_load_dry_run_state"] == "rejected_blocked_contract_only", "hardware adapter load dry-run left blocked state"
        assert dry_run["dry_run_validated"] is True, "hardware adapter load dry-run request not validated"
        assert dry_run["adapter_load_blocked"] is True, "hardware adapter load dry-run not blocked"
        assert dry_run["adapter_load_allowed"] is False, "hardware adapter load dry-run unexpectedly allowed adapter load"
        assert dry_run["adapter_activation_allowed"] is False, "hardware adapter load dry-run unexpectedly allowed activation"
        assert dry_run["hardware_access_allowed"] is False, "hardware adapter load dry-run unexpectedly allowed hardware access"
        assert dry_run["gate_closure_allowed"] is False, "hardware adapter load dry-run unexpectedly allowed gate closure"
        assert dry_run["blocker_rollup_reference"]["adapter_load_ready"] is False, "hardware adapter load dry-run lost blocker rollup readiness"
        assert dry_run["blocker_rollup_reference"]["all_blockers_cleared"] is False, "hardware adapter load dry-run lost blocker rollup blocked status"
        assert {"HW-ALD-001", "HW-ALD-002", "HW-ALD-003", "HW-ALD-004", "HW-ALD-005", "HW-ALD-006", "HW-ALD-007", "HW-ALD-008"} <= gate_ids, "hardware adapter load dry-run missing mandatory gates"
        for key in [
            "owner_decision_complete",
            "all_blockers_cleared",
            "adapter_load_ready",
            "adapter_load_allowed",
            "adapter_activation_allowed",
            "hardware_access_allowed",
            "gate_closure_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert dry_run["summary"][key] is False, f"hardware adapter load dry-run summary unexpectedly set {key}"
        assert dry_run["summary"]["owner_decision_evidence_adapter_load_dry_run_active"] is True, "hardware adapter load dry-run summary not active"
        assert dry_run["summary"]["request_shape_valid"] is True, "hardware adapter load dry-run request shape not valid"
        assert dry_run["summary"]["dry_run_validated"] is True, "hardware adapter load dry-run summary not validated"
        assert "dryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadJson" in encoded, "Android hardware adapter load dry-run binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-dry-run" in encoded, "Linux CLI hardware adapter load dry-run binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.dry.run" in encoded, "Linux IPC hardware adapter load dry-run binding missing"
        assert "DryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoad" in encoded, "gRPC hardware adapter load dry-run binding missing"
        assert "HW-ALD-007" in encoded and "open-blockers-enforced" in encoded, "hardware adapter load dry-run missing blocker gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware adapter load dry-run missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status":
        status = payload["payload"]
        encoded = json.dumps(status)
        gate_ids = {item["gate_id"] for item in status["mandatory_gates"]}
        assert status["adapter_load_dry_run_status_state"] == "contract-only-no-store-status", "hardware adapter load dry-run status left no-store state"
        assert status["last_result_available"] is False, "hardware adapter load dry-run status unexpectedly has a last result"
        assert status["persisted_dry_run_count"] == 0, "hardware adapter load dry-run status persisted dry-run records"
        assert status["pending_review_count"] == 0, "hardware adapter load dry-run status created review work"
        assert status["review_queue_updated"] is False, "hardware adapter load dry-run status updated review queue"
        assert status["evidence_persisted"] is False, "hardware adapter load dry-run status persisted evidence"
        assert status["blocker_rollup_reference"]["adapter_load_ready"] is False, "hardware adapter load dry-run status lost blocker rollup readiness"
        assert status["blocker_rollup_reference"]["all_blockers_cleared"] is False, "hardware adapter load dry-run status lost blocker rollup blocked status"
        assert {"HW-ALS-001", "HW-ALS-002", "HW-ALS-003", "HW-ALS-004", "HW-ALS-005", "HW-ALS-006", "HW-ALS-007", "HW-ALS-008"} <= gate_ids, "hardware adapter load dry-run status missing mandatory gates"
        for key in [
            "request_payload_stored",
            "last_result_stored",
            "evidence_store_active",
            "review_workflow_active",
            "review_queue_updated",
            "owner_assigned",
            "gate_state_changed",
            "gates_closed",
            "adapter_selected",
            "adapter_loaded",
            "adapter_activated",
            "hardware_accessed",
        ]:
            assert status["no_store_invariants"][key] is False, f"hardware adapter load dry-run status invariant unexpectedly set {key}"
        for key in [
            "owner_decision_complete",
            "all_blockers_cleared",
            "adapter_load_ready",
            "adapter_load_allowed",
            "adapter_activation_allowed",
            "hardware_access_allowed",
            "gate_closure_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert status["summary"][key] is False, f"hardware adapter load dry-run status summary unexpectedly set {key}"
        assert status["summary"]["owner_decision_evidence_adapter_load_dry_run_status_active"] is True, "hardware adapter load dry-run status summary not active"
        assert status["summary"]["last_result_available"] is False, "hardware adapter load dry-run status summary unexpectedly has last result"
        assert status["summary"]["persisted_dry_run_count"] == 0, "hardware adapter load dry-run status summary persisted dry-run records"
        assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatusJson" in encoded, "Android hardware adapter load dry-run status binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-dry-run-status" in encoded, "Linux CLI hardware adapter load dry-run status binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.dry.run.status" in encoded, "Linux IPC hardware adapter load dry-run status binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatus" in encoded, "gRPC hardware adapter load dry-run status binding missing"
        assert "HW-ALS-004" in encoded and "last-result-not-stored" in encoded, "hardware adapter load dry-run status missing last-result gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware adapter load dry-run status missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/audit-consistency":
        consistency = payload["payload"]
        encoded = json.dumps(consistency)
        gate_ids = {item["gate_id"] for item in consistency["mandatory_gates"]}
        assert consistency["audit_consistency_state"] == "contract-only-consistent-blocked", "hardware adapter load dry-run audit consistency left blocked state"
        assert consistency["consistency_checked"] is True, "hardware adapter load dry-run audit consistency not checked"
        assert consistency["consistency_passed"] is True, "hardware adapter load dry-run audit consistency failed"
        assert consistency["no_store_consistent"] is True, "hardware adapter load dry-run audit no-store check failed"
        assert consistency["blocker_rollup_consistent"] is True, "hardware adapter load dry-run blocker rollup check failed"
        assert consistency["dry_run_rejection_consistent"] is True, "hardware adapter load dry-run rejection check failed"
        assert consistency["source_surfaces"]["dry_run_request"]["called_by_audit_consistency_view"] is False, "hardware audit consistency called dry-run POST"
        assert consistency["source_surfaces"]["dry_run_status"]["persisted_dry_run_count"] == 0, "hardware audit consistency saw persisted dry-run records"
        assert consistency["source_surfaces"]["blocker_rollup"]["adapter_load_ready"] is False, "hardware audit consistency lost blocker rollup readiness"
        assert {"HW-ALC-001", "HW-ALC-002", "HW-ALC-003", "HW-ALC-004", "HW-ALC-005", "HW-ALC-006", "HW-ALC-007", "HW-ALC-008"} <= gate_ids, "hardware adapter load dry-run audit consistency missing mandatory gates"
        for key in [
            "owner_decision_complete",
            "all_blockers_cleared",
            "adapter_load_ready",
            "adapter_load_allowed",
            "adapter_activation_allowed",
            "hardware_access_allowed",
            "gate_closure_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert consistency["summary"][key] is False, f"hardware adapter load dry-run audit consistency summary unexpectedly set {key}"
        assert consistency["summary"]["owner_decision_evidence_adapter_load_dry_run_audit_consistency_active"] is True, "hardware adapter load dry-run audit consistency summary not active"
        assert consistency["summary"]["consistency_passed"] is True, "hardware adapter load dry-run audit consistency summary failed"
        assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistencyJson" in encoded, "Android hardware adapter load dry-run audit consistency binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-dry-run-audit-consistency" in encoded, "Linux CLI hardware adapter load dry-run audit consistency binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.dry.run.audit.consistency" in encoded, "Linux IPC hardware adapter load dry-run audit consistency binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistency" in encoded, "gRPC hardware adapter load dry-run audit consistency binding missing"
        assert "HW-ALC-004" in encoded and "dry-run-rejection-contract-consistent" in encoded, "hardware adapter load dry-run audit consistency missing rejection gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware adapter load dry-run audit consistency missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist":
        approval = payload["payload"]
        encoded = json.dumps(approval)
        gate_ids = {item["gate_id"] for item in approval["mandatory_gates"]}
        decision_ids = {item["decision_id"] for item in approval["approval_authority_decisions"]}
        assert approval["approval_authority_checklist_state"] == "contract-only-approval-authority-checklist-open", "hardware adapter load approval authority checklist left open state"
        assert approval["approval_authority_assigned"] is False, "hardware adapter load approval authority unexpectedly assigned"
        assert approval["approval_policy_confirmed"] is False, "hardware adapter load approval policy unexpectedly confirmed"
        assert approval["approval_signature_rules_confirmed"] is False, "hardware adapter load approval signature rules unexpectedly confirmed"
        assert approval["approval_rbac_confirmed"] is False, "hardware adapter load approval RBAC unexpectedly confirmed"
        assert approval["approval_workflow_active"] is False, "hardware adapter load approval workflow unexpectedly active"
        assert approval["approval_record_persisted"] is False, "hardware adapter load approval record unexpectedly persisted"
        assert approval["adapter_load_allowed"] is False, "hardware adapter load approval checklist allowed adapter load"
        assert approval["hardware_access_allowed"] is False, "hardware adapter load approval checklist allowed hardware access"
        assert approval["source_surfaces"]["adapter_load_dry_run_request"]["called_by_approval_authority_checklist"] is False, "hardware approval checklist called dry-run POST"
        assert approval["source_surfaces"]["adapter_load_dry_run_status"]["persisted_dry_run_count"] == 0, "hardware approval checklist saw persisted dry-run records"
        assert approval["source_surfaces"]["adapter_load_dry_run_audit_consistency"]["consistency_passed"] is True, "hardware approval checklist lost audit consistency"
        assert {"HW-ALA-001", "HW-ALA-002", "HW-ALA-003", "HW-ALA-004", "HW-ALA-005", "HW-ALA-006", "HW-ALA-007", "HW-ALA-008"} <= gate_ids, "hardware adapter load approval authority checklist missing mandatory gates"
        assert {"HW-ALA-002", "HW-ALA-003", "HW-ALA-004", "HW-ALA-005", "HW-ALA-006"} <= decision_ids, "hardware adapter load approval authority checklist missing decisions"
        for key in [
            "approval_authority_assigned",
            "approval_policy_confirmed",
            "approval_signature_rules_confirmed",
            "approval_rbac_confirmed",
            "approval_workflow_active",
            "approval_record_persisted",
            "owner_decision_complete",
            "all_blockers_cleared",
            "adapter_load_ready",
            "adapter_load_allowed",
            "adapter_activation_allowed",
            "hardware_access_allowed",
            "gate_closure_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert approval["summary"][key] is False, f"hardware approval authority checklist summary unexpectedly set {key}"
        assert approval["summary"]["owner_decision_evidence_adapter_load_approval_authority_checklist_active"] is True, "hardware approval authority checklist summary not active"
        assert approval["summary"]["dry_run_audit_consistency_passed"] is True, "hardware approval authority checklist audit consistency did not pass"
        assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklistJson" in encoded, "Android hardware approval authority binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-approval-authority-checklist" in encoded, "Linux CLI hardware approval authority binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.authority.checklist" in encoded, "Linux IPC hardware approval authority binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklist" in encoded, "gRPC hardware approval authority binding missing"
        assert "approval without durable evidence record" in encoded, "hardware approval authority checklist missing durable evidence rule"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware approval authority checklist missing Req IDs"
    if path == "/vehicle/signals":
        vehicle_signals = payload["payload"]
        encoded = json.dumps(vehicle_signals)
        signal_paths = {item["path"] for item in vehicle_signals["signals"]}
        assert "Vehicle.Speed" in signal_paths, "vehicle signal catalog missing Vehicle.Speed"
        assert "Vehicle.Cabin.HVAC.Station.Row1.Left.Temperature" in signal_paths, "vehicle signal catalog missing HVAC signal"
        assert "Vehicle.Body.Door.Row1.Left.IsOpen" in signal_paths, "vehicle signal catalog missing door signal"
        assert vehicle_signals["summary"]["catalog_state"] == "read-only-mock-signal-catalog", "vehicle signal catalog left read-only state"
        assert vehicle_signals["summary"]["dbc_arxml_loaded"] is False, "vehicle signal catalog loaded DBC/ARXML"
        assert vehicle_signals["summary"]["real_vehicle_bus_connected"] is False, "vehicle signal catalog touched real vehicle bus"
        assert vehicle_signals["summary"]["hardware_accessed"] is False, "vehicle signal catalog touched hardware"
        assert vehicle_signals["summary"]["driver_development_triggered"] is False, "vehicle signal catalog triggered Driver/HAL work"
        assert vehicle_signals["summary"]["virtualization_development_triggered"] is False, "vehicle signal catalog triggered virtualization work"
        assert vehicle_signals["summary"]["service_dispatch_triggered"] is False, "vehicle signal catalog dispatched a service"
        assert "getVehicleSignalsJson" in encoded, "Android vehicle signal binding visibility missing"
        assert "vehicle.signals.list" in encoded, "Linux IPC vehicle signal binding visibility missing"
        assert "GetVehicleSignals" in encoded, "gRPC vehicle signal binding visibility missing"
        assert "DRV-GAP-002" in encoded, "vehicle signal catalog missing vehicle bus driver gap link"
    if path == "/vehicle/signals/activation":
        activation = payload["payload"]
        encoded = json.dumps(activation)
        assert activation["activation_state"] == "criteria-only-not-activated", "vehicle signal activation left criteria-only state"
        assert activation["read_bridge_activated"] is False, "vehicle signal activation enabled a read bridge"
        option_types = {item["source_type"] for item in activation["activation_options"]}
        assert "dbc-arxml" in option_types, "activation criteria missing DBC/ARXML option"
        assert "android-vhal-or-vendor-aidl" in option_types, "activation criteria missing Android VHAL/vendor AIDL option"
        assert "linux-socketcan" in option_types, "activation criteria missing Linux SocketCAN option"
        assert "vendor-gateway-or-someip" in option_types, "activation criteria missing vendor gateway option"
        gate_ids = {item["gate_id"] for item in activation["mandatory_gates"]}
        assert {"VS-ACT-001", "VS-ACT-002", "VS-ACT-003", "VS-ACT-004", "VS-ACT-005"} <= gate_ids, "activation criteria missing mandatory gates"
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
            assert activation["summary"][key] is False, f"activation summary unexpectedly set {key}"
        assert "getVehicleSignalActivationJson" in encoded, "Android vehicle signal activation binding visibility missing"
        assert "vehicle.signals.activation.get" in encoded, "Linux IPC vehicle signal activation binding visibility missing"
        assert "GetVehicleSignalActivation" in encoded, "gRPC vehicle signal activation binding visibility missing"
        assert "DRV-GAP-002" in encoded, "vehicle signal activation missing vehicle bus driver gap link"
    if path == "/vehicle/signals/validation":
        validation = payload["payload"]
        encoded = json.dumps(validation)
        assert validation["validation_state"] == "metadata-only-not-activated", "vehicle signal validation left metadata-only state"
        assert validation["read_bridge_activated"] is False, "vehicle signal validation enabled a read bridge"
        gate_ids = {item["gate_id"] for item in validation["mandatory_gates"]}
        assert {"VS-VAL-001", "VS-VAL-002", "VS-VAL-003", "VS-VAL-004", "VS-VAL-005", "VS-VAL-006"} <= gate_ids, "vehicle signal validation missing mandatory gates"
        required_sections = {"schema_source_metadata", "adapter_ownership", "android_linux_parity", "driver_gap_002", "write_path_guard"}
        assert required_sections <= set(validation["validation_envelope"]), "vehicle signal validation missing evidence sections"
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
            assert validation["summary"][key] is False, f"validation summary unexpectedly set {key}"
        assert "getVehicleSignalValidationJson" in encoded, "Android vehicle signal validation binding visibility missing"
        assert "vehicle.signals.validation.get" in encoded, "Linux IPC vehicle signal validation binding visibility missing"
        assert "GetVehicleSignalValidation" in encoded, "gRPC vehicle signal validation binding visibility missing"
        assert "DRV-GAP-002" in encoded, "vehicle signal validation missing vehicle bus driver gap link"
    if path == "/uib/events/subscriptions":
        subscriptions = payload["payload"]
        encoded = json.dumps(subscriptions)
        assert subscriptions["subscription_state"] == "contract-only-not-brokered", "event subscription left contract-only state"
        assert subscriptions["broker_active"] is False, "event subscription started a broker"
        assert subscriptions["active_subscriptions"] == [], "event subscription contract created active subscriptions"
        gate_ids = {item["gate_id"] for item in subscriptions["mandatory_gates"]}
        assert {"EV-SUB-001", "EV-SUB-002", "EV-SUB-003", "EV-SUB-004", "EV-SUB-005", "EV-SUB-006"} <= gate_ids, "event subscription missing mandatory gates"
        for key in [
            "broker_active",
            "subscription_persistence_active",
            "cursor_storage_active",
            "activation_evidence_accepted_for_review",
            "activation_evidence_persisted",
            "review_queue_updated",
            "gate_state_changed",
            "gates_closed",
            "retention_policy_confirmed",
            "evidence_uri_rules_confirmed",
            "deletion_export_semantics_confirmed",
            "backpressure_qos_evidence_confirmed",
            "readiness_rollup_confirmed",
            "callback_registered",
            "watch_started",
            "dds_runtime_active",
            "sse_websocket_active",
            "high_rate_data_plane_active",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert subscriptions["summary"][key] is False, f"event subscription summary unexpectedly set {key}"
        assert subscriptions["summary"]["backpressure_qos_evidence_contract_active"] is True, "backpressure/QoS evidence contract not active in subscription summary"
        assert subscriptions["summary"]["readiness_rollup_contract_active"] is True, "readiness rollup contract not active in subscription summary"
        assert subscriptions["summary"]["activation_evidence_contract_active"] is True, "activation evidence contract not active in subscription summary"
        assert subscriptions["summary"]["activation_evidence_status_contract_active"] is True, "activation evidence status contract not active in subscription summary"
        assert subscriptions["summary"]["activation_evidence_retention_checklist_active"] is True, "activation evidence retention checklist not active in subscription summary"
        assert subscriptions["summary"]["activation_evidence_decision_status_rollup_active"] is True, "activation evidence decision status rollup not active in subscription summary"
        assert subscriptions["summary"]["evidence_store_active"] is False, "subscription summary unexpectedly activated evidence store"
        assert subscriptions["summary"]["review_workflow_active"] is False, "subscription summary unexpectedly activated review workflow"
        assert subscriptions["summary"]["persisted_submission_count"] == 0, "subscription summary reported persisted evidence"
        assert subscriptions["summary"]["pending_review_count"] == 0, "subscription summary reported pending review"
        assert "getEventSubscriptionsJson" in encoded, "Android event subscription binding visibility missing"
        assert "requestEventSubscriptionJson" in encoded, "Android event subscription request binding visibility missing"
        assert "cancelEventSubscriptionJson" in encoded, "Android event subscription cancel binding visibility missing"
        assert "getEventSubscriptionCallbackWatchShapeJson" in encoded, "Android event subscription callback/watch shape binding visibility missing"
        assert "getEventSubscriptionCursorReplayStorageJson" in encoded, "Android event subscription cursor/replay storage binding visibility missing"
        assert "getEventSubscriptionBackpressureQosEvidenceJson" in encoded, "Android event subscription backpressure/QoS binding visibility missing"
        assert "getEventSubscriptionReadinessRollupJson" in encoded, "Android event subscription readiness rollup binding visibility missing"
        assert "submitEventSubscriptionActivationEvidenceJson" in encoded, "Android event subscription activation evidence binding visibility missing"
        assert "getEventSubscriptionActivationEvidenceStatusJson" in encoded, "Android event subscription activation evidence status binding visibility missing"
        assert "getEventSubscriptionActivationEvidenceRetentionChecklistJson" in encoded, "Android event subscription activation evidence retention binding visibility missing"
        assert "getEventSubscriptionActivationEvidenceDecisionStatusRollupJson" in encoded, "Android event subscription activation evidence decision status binding visibility missing"
        assert "uib.events.subscriptions.get" in encoded, "Linux IPC event subscription binding visibility missing"
        assert "uib.events.subscriptions.request" in encoded, "Linux IPC event subscription request binding visibility missing"
        assert "uib.events.subscriptions.cancel" in encoded, "Linux IPC event subscription cancel binding visibility missing"
        assert "uib.events.subscriptions.callback.watch.shape" in encoded, "Linux IPC event subscription callback/watch shape binding visibility missing"
        assert "uib.events.subscriptions.cursor.replay.storage" in encoded, "Linux IPC event subscription cursor/replay storage binding visibility missing"
        assert "uib.events.subscriptions.backpressure.qos.evidence" in encoded, "Linux IPC event subscription backpressure/QoS binding visibility missing"
        assert "uib.events.subscriptions.readiness.rollup" in encoded, "Linux IPC event subscription readiness rollup binding visibility missing"
        assert "uib.events.subscriptions.activation.evidence" in encoded, "Linux IPC event subscription activation evidence binding visibility missing"
        assert "uib.events.subscriptions.activation.evidence.status" in encoded, "Linux IPC event subscription activation evidence status binding visibility missing"
        assert "uib.events.subscriptions.activation.evidence.retention.checklist" in encoded, "Linux IPC event subscription activation evidence retention binding visibility missing"
        assert "uib.events.subscriptions.activation.evidence.decision.status.rollup" in encoded, "Linux IPC event subscription activation evidence decision status binding visibility missing"
        assert "GetEventSubscriptions" in encoded, "gRPC event subscription binding visibility missing"
        assert "RequestEventSubscription" in encoded, "gRPC event subscription request binding visibility missing"
        assert "CancelEventSubscription" in encoded, "gRPC event subscription cancel binding visibility missing"
        assert "GetEventSubscriptionCallbackWatchShape" in encoded, "gRPC event subscription callback/watch shape binding visibility missing"
        assert "GetEventSubscriptionCursorReplayStorage" in encoded, "gRPC event subscription cursor/replay storage binding visibility missing"
        assert "GetEventSubscriptionBackpressureQosEvidence" in encoded, "gRPC event subscription backpressure/QoS binding visibility missing"
        assert "GetEventSubscriptionReadinessRollup" in encoded, "gRPC event subscription readiness rollup binding visibility missing"
        assert "SubmitEventSubscriptionActivationEvidence" in encoded, "gRPC event subscription activation evidence binding visibility missing"
        assert "GetEventSubscriptionActivationEvidenceStatus" in encoded, "gRPC event subscription activation evidence status binding visibility missing"
        assert "GetEventSubscriptionActivationEvidenceRetentionChecklist" in encoded, "gRPC event subscription activation evidence retention binding visibility missing"
        assert "GetEventSubscriptionActivationEvidenceDecisionStatusRollup" in encoded, "gRPC event subscription activation evidence decision status binding visibility missing"
        assert "NV-P-006" in encoded and "FW-U-003" in encoded, "event subscription missing Req IDs"
    if path == "/uib/events/subscriptions/request":
        subscription = payload["payload"]
        assert subscription["state"] == "validated_contract_only", "event subscription request was not contract validated"
        assert subscription["lifecycle_transition"]["to"] == "validated", "event subscription request lifecycle did not validate"
        assert subscription["subscription_record"]["persisted"] is False, "event subscription request persisted state"
        assert subscription["subscription_record"]["active"] is False, "event subscription request activated state"
        for key in [
            "subscription_persisted",
            "broker_active",
            "callback_registered",
            "watch_started",
            "dds_runtime_active",
            "sse_websocket_active",
            "high_rate_data_plane_active",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert subscription["summary"][key] is False, f"event subscription request summary unexpectedly set {key}"
        encoded = json.dumps(subscription)
        assert "XSC-005" in encoded and "NV-P-006" in encoded, "event subscription request missing Req IDs"
    if path == "/uib/events/subscriptions/cancel":
        cancellation = payload["payload"]
        assert cancellation["state"] == "cancelled_contract_only", "event subscription cancel was not contract cancelled"
        assert cancellation["lifecycle_transition"]["to"] == "cancelled", "event subscription cancel lifecycle did not cancel"
        assert cancellation["lifecycle_transition"]["matched_active_subscription"] is False, "event subscription cancel matched active state"
        assert cancellation["subscription_record"]["persisted"] is False, "event subscription cancel touched persisted state"
        for key in [
            "subscription_persisted",
            "matched_active_subscription",
            "broker_active",
            "callback_registered",
            "watch_started",
            "dds_runtime_active",
            "sse_websocket_active",
            "high_rate_data_plane_active",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert cancellation["summary"][key] is False, f"event subscription cancel summary unexpectedly set {key}"
        encoded = json.dumps(cancellation)
        assert "XSC-005" in encoded and "NV-P-006" in encoded, "event subscription cancel missing Req IDs"
    if path == "/uib/events/subscriptions/transport-readiness":
        readiness = payload["payload"]
        encoded = json.dumps(readiness)
        gate_ids = {item["gate_id"] for item in readiness["mandatory_gates"]}
        assert readiness["readiness_state"] == "contract-only-no-transport-selected", "event subscription transport readiness selected transport"
        assert readiness["transport_selected"] is False, "event subscription transport was selected"
        assert {"EV-TR-001", "EV-TR-002", "EV-TR-003", "EV-TR-004", "EV-TR-005", "EV-TR-006"} <= gate_ids, "event transport readiness missing mandatory gates"
        for key in [
            "transport_selected",
            "broker_active",
            "subscription_persistence_active",
            "callback_registered",
            "watch_started",
            "cursor_storage_active",
            "dds_runtime_active",
            "sse_websocket_active",
            "high_rate_data_plane_active",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert readiness["summary"][key] is False, f"event subscription transport readiness summary unexpectedly set {key}"
        assert "getEventSubscriptionTransportReadinessJson" in encoded, "Android event subscription transport readiness binding missing"
        assert "event-subscription-transport-readiness" in encoded, "Linux CLI event subscription transport readiness binding missing"
        assert "uib.events.subscriptions.transport.readiness" in encoded, "Linux IPC event subscription transport readiness binding missing"
        assert "GetEventSubscriptionTransportReadiness" in encoded, "gRPC event subscription transport readiness binding missing"
        assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, "event subscription transport readiness missing Req IDs"
    if path == "/uib/events/subscriptions/decision-matrix":
        matrix = payload["payload"]
        encoded = json.dumps(matrix)
        gate_ids = {item["gate_id"] for item in matrix["mandatory_gates"]}
        assert matrix["decision_state"] == "contract-only-owner-matrix-open", "event subscription decision matrix unexpectedly closed"
        assert matrix["production_activation_allowed"] is False, "event subscription decision matrix allowed activation"
        assert {"EV-DM-001", "EV-DM-002", "EV-DM-003", "EV-DM-004", "EV-DM-005", "EV-DM-006", "EV-DM-007"} <= gate_ids, "event decision matrix missing mandatory gates"
        for key in [
            "production_activation_allowed",
            "all_required_owners_assigned",
            "broker_owner_confirmed",
            "cursor_storage_owner_confirmed",
            "backpressure_qos_owner_confirmed",
            "callback_watch_shape_confirmed",
            "transport_choice_confirmed",
            "broker_active",
            "subscription_persistence_active",
            "callback_registered",
            "watch_started",
            "cursor_storage_active",
            "dds_runtime_active",
            "sse_websocket_active",
            "high_rate_data_plane_active",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert matrix["summary"][key] is False, f"event subscription decision matrix summary unexpectedly set {key}"
        assert "getEventSubscriptionDecisionMatrixJson" in encoded, "Android event subscription decision matrix binding missing"
        assert "event-subscription-decision-matrix" in encoded, "Linux CLI event subscription decision matrix binding missing"
        assert "uib.events.subscriptions.decision.matrix" in encoded, "Linux IPC event subscription decision matrix binding missing"
        assert "GetEventSubscriptionDecisionMatrix" in encoded, "gRPC event subscription decision matrix binding missing"
        assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, "event subscription decision matrix missing Req IDs"
    if path == "/uib/events/subscriptions/activation-checklist":
        checklist = payload["payload"]
        encoded = json.dumps(checklist)
        gate_ids = {item["gate_id"] for item in checklist["checklist"]}
        assert checklist["activation_state"] == "contract-only-activation-blocked", "event subscription activation checklist unexpectedly opened"
        assert checklist["activation_allowed"] is False, "event subscription activation checklist allowed activation"
        assert {"EV-ACT-001", "EV-ACT-002", "EV-ACT-003", "EV-ACT-004", "EV-ACT-005", "EV-ACT-006", "EV-ACT-007", "EV-ACT-008"} <= gate_ids, "event activation checklist missing mandatory gates"
        for key in [
            "activation_allowed",
            "production_activation_allowed",
            "required_evidence_complete",
            "broker_owner_evidence_attached",
            "runtime_governance_binding_evidence_attached",
            "cursor_store_evidence_attached",
            "backpressure_qos_evidence_attached",
            "transport_runtime_evidence_attached",
            "driver_hal_scope_evidence_attached",
            "transport_selected",
            "broker_active",
            "subscription_persistence_active",
            "callback_registered",
            "watch_started",
            "cursor_storage_active",
            "dds_runtime_active",
            "sse_websocket_active",
            "high_rate_data_plane_active",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert checklist["summary"][key] is False, f"event subscription activation checklist summary unexpectedly set {key}"
        assert "getEventSubscriptionActivationChecklistJson" in encoded, "Android event subscription activation checklist binding missing"
        assert "event-subscription-activation-checklist" in encoded, "Linux CLI event subscription activation checklist binding missing"
        assert "uib.events.subscriptions.activation.checklist" in encoded, "Linux IPC event subscription activation checklist binding missing"
        assert "GetEventSubscriptionActivationChecklist" in encoded, "gRPC event subscription activation checklist binding missing"
        assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, "event subscription activation checklist missing Req IDs"
    if path == "/uib/events/subscriptions/callback-watch-shape":
        shape = payload["payload"]
        encoded = json.dumps(shape)
        gate_ids = {item["gate_id"] for item in shape["mandatory_gates"]}
        assert shape["shape_state"] == "contract-only-callback-watch-shape-draft", "event subscription callback/watch shape left draft state"
        assert shape["shape_confirmed"] is False, "event subscription callback/watch shape was confirmed"
        assert {"EV-CW-001", "EV-CW-002", "EV-CW-003", "EV-CW-004", "EV-CW-005", "EV-CW-006", "EV-CW-007", "EV-CW-008"} <= gate_ids, "event callback/watch shape missing mandatory gates"
        for key in [
            "callback_watch_shape_confirmed",
            "runtime_governance_binding_evidence_attached",
            "cursor_store_evidence_attached",
            "backpressure_qos_evidence_attached",
            "transport_runtime_evidence_attached",
            "callback_registered",
            "watch_started",
            "streaming_runtime_implemented",
            "broker_active",
            "subscription_persistence_active",
            "cursor_storage_active",
            "dds_runtime_active",
            "sse_websocket_active",
            "high_rate_data_plane_active",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert shape["summary"][key] is False, f"event subscription callback/watch shape summary unexpectedly set {key}"
        assert shape["summary"]["callback_watch_shape_contract_active"] is True, "callback/watch shape contract not active"
        assert shape["summary"]["android_callback_shape_drafted"] is True, "Android callback shape draft missing"
        assert shape["summary"]["linux_watch_shape_drafted"] is True, "Linux watch shape draft missing"
        assert "getEventSubscriptionCallbackWatchShapeJson" in encoded, "Android event subscription callback/watch shape binding missing"
        assert "event-subscription-callback-watch-shape" in encoded, "Linux CLI event subscription callback/watch shape binding missing"
        assert "uib.events.subscriptions.callback.watch.shape" in encoded, "Linux IPC event subscription callback/watch shape binding missing"
        assert "GetEventSubscriptionCallbackWatchShape" in encoded, "gRPC event subscription callback/watch shape binding missing"
        assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, "event subscription callback/watch shape missing Req IDs"
    if path == "/uib/events/subscriptions/cursor-replay-storage":
        storage = payload["payload"]
        encoded = json.dumps(storage)
        gate_ids = {item["gate_id"] for item in storage["mandatory_gates"]}
        assert storage["cursor_replay_state"] == "contract-only-cursor-replay-storage-draft", "event subscription cursor/replay storage left draft state"
        assert storage["cursor_replay_storage_confirmed"] is False, "event subscription cursor/replay storage was confirmed"
        assert {"EV-CRS-001", "EV-CRS-002", "EV-CRS-003", "EV-CRS-004", "EV-CRS-005", "EV-CRS-006", "EV-CRS-007", "EV-CRS-008"} <= gate_ids, "event cursor/replay storage missing mandatory gates"
        for key in [
            "cursor_replay_storage_confirmed",
            "storage_owner_confirmed",
            "schema_owner_confirmed",
            "replay_window_confirmed",
            "retention_policy_confirmed",
            "restart_recovery_confirmed",
            "runtime_governance_binding_evidence_attached",
            "backpressure_qos_evidence_attached",
            "broker_active",
            "subscription_persistence_active",
            "cursor_storage_active",
            "replay_index_active",
            "callback_registered",
            "watch_started",
            "streaming_runtime_implemented",
            "dds_runtime_active",
            "sse_websocket_active",
            "high_rate_data_plane_active",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert storage["summary"][key] is False, f"event subscription cursor/replay storage summary unexpectedly set {key}"
        assert storage["summary"]["cursor_replay_storage_contract_active"] is True, "cursor/replay storage contract not active"
        assert "getEventSubscriptionCursorReplayStorageJson" in encoded, "Android event subscription cursor/replay storage binding missing"
        assert "event-subscription-cursor-replay-storage" in encoded, "Linux CLI event subscription cursor/replay storage binding missing"
        assert "uib.events.subscriptions.cursor.replay.storage" in encoded, "Linux IPC event subscription cursor/replay storage binding missing"
        assert "GetEventSubscriptionCursorReplayStorage" in encoded, "gRPC event subscription cursor/replay storage binding missing"
        assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, "event subscription cursor/replay storage missing Req IDs"
    if path == "/uib/events/subscriptions/backpressure-qos-evidence":
        qos = payload["payload"]
        encoded = json.dumps(qos)
        gate_ids = {item["gate_id"] for item in qos["mandatory_gates"]}
        assert qos["backpressure_qos_state"] == "contract-only-backpressure-qos-evidence-draft", "event subscription backpressure/QoS evidence left draft state"
        assert qos["backpressure_qos_evidence_confirmed"] is False, "event subscription backpressure/QoS evidence was confirmed"
        assert {"EV-QOS-001", "EV-QOS-002", "EV-QOS-003", "EV-QOS-004", "EV-QOS-005", "EV-QOS-006", "EV-QOS-007", "EV-QOS-008"} <= gate_ids, "event backpressure/QoS evidence missing mandatory gates"
        for key in [
            "backpressure_qos_evidence_confirmed",
            "overflow_schema_confirmed",
            "qos_owner_confirmed",
            "runtime_governance_qos_evidence_attached",
            "high_rate_qos_mapping_confirmed",
            "driver_hal_scope_evidence_attached",
            "event_delivery_qos_active",
            "overflow_emission_active",
            "broker_active",
            "subscription_persistence_active",
            "cursor_storage_active",
            "replay_index_active",
            "callback_registered",
            "watch_started",
            "streaming_runtime_implemented",
            "dds_runtime_active",
            "sse_websocket_active",
            "high_rate_data_plane_active",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert qos["summary"][key] is False, f"event subscription backpressure/QoS summary unexpectedly set {key}"
        assert qos["summary"]["backpressure_qos_evidence_contract_active"] is True, "backpressure/QoS evidence contract not active"
        assert "getEventSubscriptionBackpressureQosEvidenceJson" in encoded, "Android event subscription backpressure/QoS binding missing"
        assert "event-subscription-backpressure-qos-evidence" in encoded, "Linux CLI event subscription backpressure/QoS binding missing"
        assert "uib.events.subscriptions.backpressure.qos.evidence" in encoded, "Linux IPC event subscription backpressure/QoS binding missing"
        assert "GetEventSubscriptionBackpressureQosEvidence" in encoded, "gRPC event subscription backpressure/QoS binding missing"
        assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, "event subscription backpressure/QoS evidence missing Req IDs"
    if path == "/uib/events/subscriptions/readiness-rollup":
        rollup = payload["payload"]
        encoded = json.dumps(rollup)
        section_ids = {item["section_id"] for item in rollup["readiness_sections"]}
        blocker_ids = {item["blocker_id"] for item in rollup["activation_blockers"]}
        assert rollup["readiness_rollup_state"] == "contract-only-readiness-rollup-blocked", "event subscription readiness rollup unexpectedly unblocked"
        assert rollup["readiness_rollup_confirmed"] is False, "event subscription readiness rollup was confirmed"
        assert {"EV-ROLLUP-LIFECYCLE", "EV-ROLLUP-TRANSPORT", "EV-ROLLUP-OWNER-DECISIONS", "EV-ROLLUP-ACTIVATION", "EV-ROLLUP-CALLBACK-WATCH", "EV-ROLLUP-CURSOR-REPLAY", "EV-ROLLUP-BACKPRESSURE-QOS"} <= section_ids, "event subscription readiness rollup missing sections"
        assert {"EV-RU-001", "EV-RU-002", "EV-RU-003", "EV-RU-004", "EV-RU-005", "EV-RU-006"} <= blocker_ids, "event subscription readiness rollup missing blockers"
        for key in [
            "readiness_rollup_confirmed",
            "broker_activation_ready",
            "production_activation_allowed",
            "all_required_evidence_complete",
            "transport_selected",
            "broker_active",
            "subscription_persistence_active",
            "cursor_storage_active",
            "replay_index_active",
            "event_delivery_qos_active",
            "overflow_emission_active",
            "callback_registered",
            "watch_started",
            "streaming_runtime_implemented",
            "dds_runtime_active",
            "sse_websocket_active",
            "high_rate_data_plane_active",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert rollup["summary"][key] is False, f"event subscription readiness rollup summary unexpectedly set {key}"
        assert rollup["summary"]["readiness_rollup_contract_active"] is True, "readiness rollup contract not active"
        assert rollup["summary"]["blocked_gate_count"] > 0, "readiness rollup did not report blocked gates"
        assert "getEventSubscriptionReadinessRollupJson" in encoded, "Android event subscription readiness rollup binding missing"
        assert "event-subscription-readiness-rollup" in encoded, "Linux CLI event subscription readiness rollup binding missing"
        assert "uib.events.subscriptions.readiness.rollup" in encoded, "Linux IPC event subscription readiness rollup binding missing"
        assert "GetEventSubscriptionReadinessRollup" in encoded, "gRPC event subscription readiness rollup binding missing"
        assert "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, "event subscription readiness rollup missing driver gap evidence link"
        assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, "event subscription readiness rollup missing Req IDs"
    if path == "/uib/events/subscriptions/activation-evidence":
        evidence = payload["payload"]
        encoded = json.dumps(evidence)
        gate_ids = {item["gate_id"] for item in evidence["mandatory_gates"]}
        assert evidence["evidence_intake_state"] == "validated_contract_only", "event subscription activation evidence was not contract validated"
        assert evidence["intake_validated"] is True, "event subscription activation evidence did not validate"
        assert {"EV-AE-001", "EV-AE-002", "EV-AE-003", "EV-AE-004", "EV-AE-005", "EV-AE-006", "EV-AE-007", "EV-AE-008"} <= gate_ids, "event activation evidence intake missing mandatory gates"
        assert evidence["review_result"]["accepted_for_review"] is False, "activation evidence entered review queue"
        assert evidence["review_result"]["evidence_persisted"] is False, "activation evidence was persisted"
        assert evidence["review_result"]["gates_closed"] is False, "activation evidence closed gates"
        assert evidence["review_result"]["activation_allowed"] is False, "activation evidence allowed broker activation"
        for key in [
            "activation_evidence_accepted_for_review",
            "activation_evidence_persisted",
            "review_queue_updated",
            "gate_state_changed",
            "gates_closed",
            "activation_allowed",
            "broker_activation_ready",
            "production_activation_allowed",
            "broker_active",
            "subscription_persistence_active",
            "cursor_storage_active",
            "event_delivery_qos_active",
            "callback_registered",
            "watch_started",
            "dds_runtime_active",
            "sse_websocket_active",
            "high_rate_data_plane_active",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert evidence["summary"][key] is False, f"event subscription activation evidence summary unexpectedly set {key}"
        assert evidence["summary"]["activation_evidence_contract_active"] is True, "activation evidence contract not active"
        assert "submitEventSubscriptionActivationEvidenceJson" in encoded, "Android activation evidence binding missing"
        assert "event-subscription-activation-evidence" in encoded, "Linux CLI activation evidence binding missing"
        assert "uib.events.subscriptions.activation.evidence" in encoded, "Linux IPC activation evidence binding missing"
        assert "SubmitEventSubscriptionActivationEvidence" in encoded, "gRPC activation evidence binding missing"
        assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, "event subscription activation evidence missing Req IDs"
    if path == "/uib/events/subscriptions/activation-evidence/status":
        status = payload["payload"]
        encoded = json.dumps(status)
        gate_ids = {item["gate_id"] for item in status["mandatory_gates"]}
        assert status["review_status_state"] == "contract-only-no-evidence-store", "activation evidence status claimed a store"
        assert {"EV-AES-001", "EV-AES-002", "EV-AES-003", "EV-AES-004", "EV-AES-005", "EV-AES-006"} <= gate_ids, "event activation evidence status missing mandatory gates"
        assert status["review_pipeline"]["evidence_store_active"] is False, "activation evidence status activated evidence store"
        assert status["review_pipeline"]["review_workflow_active"] is False, "activation evidence status activated review workflow"
        assert status["review_pipeline"]["gates_closed"] is False, "activation evidence status closed gates"
        assert status["counters"]["persisted_submission_count"] == 0, "activation evidence status reported persisted submissions"
        assert status["counters"]["pending_review_count"] == 0, "activation evidence status reported pending reviews"
        for key in [
            "activation_evidence_accepted_for_review",
            "activation_evidence_persisted",
            "evidence_store_active",
            "review_workflow_active",
            "review_queue_updated",
            "gate_state_changed",
            "gates_closed",
            "activation_allowed",
            "broker_activation_ready",
            "production_activation_allowed",
            "broker_active",
            "subscription_persistence_active",
            "cursor_storage_active",
            "event_delivery_qos_active",
            "callback_registered",
            "watch_started",
            "dds_runtime_active",
            "sse_websocket_active",
            "high_rate_data_plane_active",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert status["summary"][key] is False, f"event subscription activation evidence status summary unexpectedly set {key}"
        assert status["summary"]["activation_evidence_status_contract_active"] is True, "activation evidence status contract not active"
        assert status["summary"]["persisted_submission_count"] == 0, "activation evidence status summary reported persisted submissions"
        assert status["summary"]["pending_review_count"] == 0, "activation evidence status summary reported pending reviews"
        assert "getEventSubscriptionActivationEvidenceStatusJson" in encoded, "Android activation evidence status binding missing"
        assert "event-subscription-activation-evidence-status" in encoded, "Linux CLI activation evidence status binding missing"
        assert "uib.events.subscriptions.activation.evidence.status" in encoded, "Linux IPC activation evidence status binding missing"
        assert "GetEventSubscriptionActivationEvidenceStatus" in encoded, "gRPC activation evidence status binding missing"
        assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, "event subscription activation evidence status missing Req IDs"
    if path == "/uib/events/subscriptions/activation-evidence/retention-checklist":
        retention = payload["payload"]
        encoded = json.dumps(retention)
        gate_ids = {item["gate_id"] for item in retention["mandatory_gates"]}
        assert retention["retention_checklist_state"] == "contract-only-retention-owner-checklist-open", "activation evidence retention checklist left contract-only state"
        assert {"EV-AER-001", "EV-AER-002", "EV-AER-003", "EV-AER-004", "EV-AER-005", "EV-AER-006", "EV-AER-007", "EV-AER-008"} <= gate_ids, "event activation evidence retention checklist missing mandatory gates"
        assert retention["storage_activation_allowed"] is False, "activation evidence retention checklist allowed storage activation"
        assert retention["owner_decision_complete"] is False, "activation evidence retention checklist completed owner decisions"
        assert retention["evidence_uri_rules"]["uri_rules_confirmed"] is False, "activation evidence URI rules unexpectedly confirmed"
        assert retention["retention_policy_shape"]["retention_policy_confirmed"] is False, "activation evidence retention policy unexpectedly confirmed"
        for key in [
            "owner_decision_complete",
            "retention_policy_confirmed",
            "evidence_uri_rules_confirmed",
            "review_workflow_owner_confirmed",
            "gate_closure_authority_confirmed",
            "deletion_export_semantics_confirmed",
            "evidence_store_active",
            "review_workflow_active",
            "delete_workflow_active",
            "export_workflow_active",
            "review_queue_updated",
            "gate_state_changed",
            "gates_closed",
            "activation_allowed",
            "broker_activation_ready",
            "production_activation_allowed",
            "broker_active",
            "subscription_persistence_active",
            "cursor_storage_active",
            "event_delivery_qos_active",
            "callback_registered",
            "watch_started",
            "dds_runtime_active",
            "sse_websocket_active",
            "high_rate_data_plane_active",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert retention["summary"][key] is False, f"activation evidence retention summary unexpectedly set {key}"
        assert retention["summary"]["activation_evidence_retention_checklist_active"] is True, "activation evidence retention checklist not active"
        assert retention["summary"]["persisted_submission_count"] == 0, "activation evidence retention summary reported persisted submissions"
        assert retention["summary"]["pending_review_count"] == 0, "activation evidence retention summary reported pending reviews"
        assert "getEventSubscriptionActivationEvidenceRetentionChecklistJson" in encoded, "Android activation evidence retention binding missing"
        assert "event-subscription-activation-evidence-retention-checklist" in encoded, "Linux CLI activation evidence retention binding missing"
        assert "uib.events.subscriptions.activation.evidence.retention.checklist" in encoded, "Linux IPC activation evidence retention binding missing"
        assert "GetEventSubscriptionActivationEvidenceRetentionChecklist" in encoded, "gRPC activation evidence retention binding missing"
        assert "EV-AER-006" in encoded and "delete-export-semantics" in encoded, "activation evidence retention checklist missing delete/export semantics"
        assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, "event subscription activation evidence retention missing Req IDs"
    if path == "/uib/events/subscriptions/activation-evidence/decision-status-rollup":
        decision = payload["payload"]
        encoded = json.dumps(decision)
        gate_ids = {item["gate_id"] for item in decision["mandatory_gates"]}
        assert decision["decision_status_rollup_state"] == "contract-only-decision-status-blocked", "activation evidence decision status rollup left contract-only blocked state"
        assert decision["decision_status_consistent"] is True, "activation evidence decision status rollup is inconsistent"
        assert decision["decision_status_passed"] is False, "activation evidence decision status unexpectedly passed"
        assert decision["source_surfaces"]["activation_evidence_intake"]["called_by_decision_status_rollup"] is False, "decision status rollup called activation evidence intake"
        assert {"EV-AED-001", "EV-AED-002", "EV-AED-003", "EV-AED-004", "EV-AED-005", "EV-AED-006", "EV-AED-007", "EV-AED-008"} <= gate_ids, "activation evidence decision status rollup missing gates"
        for key in [
            "decision_status_passed",
            "owner_decision_complete",
            "activation_evidence_intake_called",
            "activation_evidence_persisted",
            "evidence_store_active",
            "review_workflow_active",
            "delete_workflow_active",
            "export_workflow_active",
            "review_queue_updated",
            "gate_state_changed",
            "gates_closed",
            "activation_allowed",
            "broker_activation_ready",
            "production_activation_allowed",
            "broker_active",
            "subscription_persistence_active",
            "cursor_storage_active",
            "event_delivery_qos_active",
            "callback_registered",
            "watch_started",
            "dds_runtime_active",
            "sse_websocket_active",
            "high_rate_data_plane_active",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert decision["summary"][key] is False, f"activation evidence decision status summary unexpectedly set {key}"
        assert decision["summary"]["activation_evidence_decision_status_rollup_active"] is True, "activation evidence decision status rollup not active"
        assert decision["summary"]["decision_status_consistent"] is True, "activation evidence decision status summary inconsistent"
        assert decision["summary"]["persisted_submission_count"] == 0, "activation evidence decision status reported persisted submissions"
        assert decision["summary"]["pending_review_count"] == 0, "activation evidence decision status reported pending reviews"
        assert "getEventSubscriptionActivationEvidenceDecisionStatusRollupJson" in encoded, "Android activation evidence decision status binding missing"
        assert "event-subscription-activation-evidence-decision-status-rollup" in encoded, "Linux CLI activation evidence decision status binding missing"
        assert "uib.events.subscriptions.activation.evidence.decision.status.rollup" in encoded, "Linux IPC activation evidence decision status binding missing"
        assert "GetEventSubscriptionActivationEvidenceDecisionStatusRollup" in encoded, "gRPC activation evidence decision status binding missing"
        assert "EV-AED-006" in encoded and "activation-approval-policy" in encoded, "activation evidence decision status missing approval policy blocker"
        assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, "event subscription activation evidence decision status missing Req IDs"
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
    if path == "/uib/extensions":
        extensions = payload["payload"]["extensions"]
        encoded = json.dumps(payload)
        assert extensions, "UIB extension registry is empty"
        assert any(item["extension_id"] == "diagnostic.trace.snapshot" for item in extensions), "missing trace extension"
        assert payload["payload"]["summary"]["dynamic_extension_runtime_ready"] is False, "extension runtime maturity overstated"
        assert payload["payload"]["summary"]["service_dispatch_triggered"] is False, "extension query dispatched service"
        assert payload["payload"]["summary"]["driver_development_triggered"] is False, "extension query triggered driver development"
        assert payload["payload"]["summary"]["virtualization_development_triggered"] is False, "extension query triggered virtualization development"
        assert "getUibExtensionsJson" in encoded and "uib.extensions.get" in encoded and "GetUibExtensions" in encoded, "extension binding visibility missing"
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
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" event-subscriptions >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" event-subscribe-request >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" event-subscribe-cancel >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" event-subscription-transport-readiness >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" event-subscription-decision-matrix >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" event-subscription-activation-checklist >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" event-subscription-callback-watch-shape >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" event-subscription-cursor-replay-storage >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" event-subscription-backpressure-qos-evidence >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" event-subscription-readiness-rollup >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" event-subscription-activation-evidence >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" event-subscription-activation-evidence-status >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" event-subscription-activation-evidence-retention-checklist >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" event-subscription-activation-evidence-decision-status-rollup >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" extensions >/dev/null
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
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" prototype-readiness >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" native-adapters-detail >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" driver-gaps >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interfaces >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-activation-checklist >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-status >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-status >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-retention-checklist >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-replacement-trigger-checklist >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-selected-adapter-readiness-checklist >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-blocker-rollup >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-dry-run >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-dry-run-status >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-dry-run-audit-consistency >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-approval-authority-checklist >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" vehicle-signals >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" vehicle-signal-activation >/dev/null

echo "linux cli smoke ok"
