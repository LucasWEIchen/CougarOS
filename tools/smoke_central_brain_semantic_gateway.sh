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
    ("GET", "/uib/events/subscriptions/activation-evidence/approval-dry-run/status", None, "NV-P-006"),
    ("GET", "/uib/events/subscriptions/activation-evidence/approval-authority-checklist", None, "NV-P-006"),
    ("GET", "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/audit-consistency", None, "NV-P-006"),
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
    ("GET", "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/status", None, "HW-002"),
    ("GET", "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/audit-consistency", None, "HW-002"),
    (
        "POST",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run",
        {
            "approval_decision_request_id": "semantic-smoke-hw-approval-decision-dry-run",
            "selected_interface_id": "npu-runtime",
            "selected_adapter_id": "target-platform-npu-adapter",
            "adapter_version": "0.0.0-contract",
            "approval_decision": "approve_adapter_load",
            "approval_authority": "target-platform-approval-authority",
            "approval_signature": "contract-only-signature-placeholder",
            "evidence_refs": [
                {
                    "ref_id": "semantic-smoke-hw-approval-decision-evidence",
                    "type": "approval_authority",
                    "uri_or_path": "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md",
                    "owner": "semantic-gateway-smoke",
                    "summary": "contract-only approval decision dry-run evidence reference",
                }
            ],
            "requested_by": {"app_id": "semantic-gateway-smoke", "role": "test"},
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
        "HW-002",
    ),
    (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/status",
        None,
        "HW-002",
    ),
    (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/audit-consistency",
        None,
        "HW-002",
    ),
    (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix",
        None,
        "HW-002",
    ),
    (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix",
        None,
        "HW-002",
    ),
    (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist",
        None,
        "HW-002",
    ),
    (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status",
        None,
        "HW-002",
    ),
    (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/audit-consistency",
        None,
        "HW-002",
    ),
    (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup",
        None,
        "HW-002",
    ),
    (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist",
        None,
        "HW-002",
    ),
    (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency",
        None,
        "HW-002",
    ),
    (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup",
        None,
        "HW-002",
    ),
    (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist",
        None,
        "HW-002",
    ),
    (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency",
        None,
        "HW-002",
    ),
    (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup",
        None,
        "HW-002",
    ),
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
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/status":
        status = payload["payload"]
        encoded = json.dumps(status)
        gate_ids = {item["gate_id"] for item in status["mandatory_gates"]}
        assert status["approval_authority_status_state"] == "contract-only-approval-authority-status-open", "hardware approval authority status left open state"
        assert status["approval_authority_checklist_available"] is True, "hardware approval authority status lost checklist source"
        assert status["approval_record_available"] is False, "hardware approval authority status found approval record"
        assert status["persisted_approval_record_count"] == 0, "hardware approval authority status persisted records"
        assert status["pending_approval_review_count"] == 0, "hardware approval authority status created pending review"
        assert status["approval_review_queue_updated"] is False, "hardware approval authority status updated review queue"
        assert status["approval_evidence_store_active"] is False, "hardware approval authority status activated evidence store"
        assert status["approval_decision_passed"] is False, "hardware approval authority status passed approval"
        assert status["no_store_consistent"] is True, "hardware approval authority status lost no-store consistency"
        assert status["approval_decisions_open"] is True, "hardware approval authority status closed approval decisions"
        assert status["adapter_load_still_blocked"] is True, "hardware approval authority status unblocked adapter load"
        assert status["source_surfaces"]["approval_authority_checklist"]["called_by_approval_authority_status"] is True, "hardware approval authority status did not bind checklist source"
        assert status["source_surfaces"]["approval_record_store"]["state"] == "not-implemented-contract-only", "hardware approval authority status changed approval record store"
        assert {"HW-AAS-001", "HW-AAS-002", "HW-AAS-003", "HW-AAS-004", "HW-AAS-005", "HW-AAS-006", "HW-AAS-007", "HW-AAS-008"} <= gate_ids, "hardware approval authority status missing mandatory gates"
        for key in [
            "approval_record_available",
            "approval_review_queue_updated",
            "approval_evidence_store_active",
            "approval_decision_passed",
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
            assert status["summary"][key] is False, f"hardware approval authority status summary unexpectedly set {key}"
        for key in [
            "owner_decision_evidence_adapter_load_approval_authority_status_active",
            "owner_decision_evidence_adapter_load_approval_authority_checklist_active",
            "approval_authority_checklist_available",
            "no_store_consistent",
            "approval_decisions_open",
            "adapter_load_still_blocked",
        ]:
            assert status["summary"][key] is True, f"hardware approval authority status summary did not set {key}"
        assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatusJson" in encoded, "Android hardware approval authority status binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-approval-authority-status" in encoded, "Linux CLI hardware approval authority status binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.authority.status" in encoded, "Linux IPC hardware approval authority status binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatus" in encoded, "gRPC hardware approval authority status binding missing"
        assert "HW-AAS-002" in encoded and "zero-persisted-approval-records" in encoded, "hardware approval authority status missing zero records gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware approval authority status missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/audit-consistency":
        consistency = payload["payload"]
        encoded = json.dumps(consistency)
        gate_ids = {item["gate_id"] for item in consistency["mandatory_gates"]}
        check_ids = {item["check_id"] for item in consistency["consistency_checks"]}
        assert consistency["approval_authority_audit_consistency_state"] == "contract-only-approval-authority-consistent-blocked", "hardware approval authority audit consistency left blocked state"
        assert consistency["consistency_checked"] is True, "hardware approval authority audit consistency did not run"
        assert consistency["consistency_passed"] is True, "hardware approval authority audit consistency failed"
        assert consistency["approval_status_no_store_consistent"] is True, "hardware approval authority audit consistency lost no-store status"
        assert consistency["approval_decisions_open_consistent"] is True, "hardware approval authority audit consistency closed decisions"
        assert consistency["adapter_load_blocked_consistent"] is True, "hardware approval authority audit consistency unblocked adapter load"
        assert consistency["dry_run_audit_consistency_passed"] is True, "hardware approval authority audit consistency lost dry-run audit source"
        assert consistency["source_surfaces"]["adapter_load_dry_run_audit_consistency"]["called_by_approval_authority_audit_consistency"] is True, "hardware approval authority audit consistency did not bind dry-run audit source"
        assert consistency["source_surfaces"]["approval_authority_checklist"]["called_by_approval_authority_audit_consistency"] is True, "hardware approval authority audit consistency did not bind checklist source"
        assert consistency["source_surfaces"]["approval_authority_status"]["called_by_approval_authority_audit_consistency"] is True, "hardware approval authority audit consistency did not bind status source"
        assert consistency["source_surfaces"]["approval_authority_status"]["persisted_approval_record_count"] == 0, "hardware approval authority audit consistency saw persisted approval records"
        assert {"HW-AAC-001", "HW-AAC-002", "HW-AAC-003", "HW-AAC-004", "HW-AAC-005", "HW-AAC-006", "HW-AAC-007", "HW-AAC-008"} <= gate_ids, "hardware approval authority audit consistency missing mandatory gates"
        assert {"HW-AAC-CHECK-001", "HW-AAC-CHECK-002", "HW-AAC-CHECK-003", "HW-AAC-CHECK-004", "HW-AAC-CHECK-005"} <= check_ids, "hardware approval authority audit consistency missing checks"
        for key in [
            "approval_record_available",
            "approval_review_queue_updated",
            "approval_evidence_store_active",
            "approval_decision_passed",
            "approval_authority_assigned",
            "approval_policy_confirmed",
            "approval_workflow_active",
            "approval_record_persisted",
            "adapter_load_allowed",
            "adapter_activation_allowed",
            "hardware_access_allowed",
            "gate_closure_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert consistency["summary"][key] is False, f"hardware approval authority audit consistency summary unexpectedly set {key}"
        for key in [
            "owner_decision_evidence_adapter_load_approval_authority_audit_consistency_active",
            "owner_decision_evidence_adapter_load_approval_authority_status_active",
            "owner_decision_evidence_adapter_load_approval_authority_checklist_active",
            "owner_decision_evidence_adapter_load_dry_run_audit_consistency_active",
            "consistency_passed",
            "approval_status_no_store_consistent",
            "approval_decisions_open_consistent",
            "adapter_load_blocked_consistent",
            "dry_run_audit_consistency_passed",
        ]:
            assert consistency["summary"][key] is True, f"hardware approval authority audit consistency summary did not set {key}"
        assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistencyJson" in encoded, "Android hardware approval authority audit consistency binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-approval-authority-audit-consistency" in encoded, "Linux CLI hardware approval authority audit consistency binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.authority.audit.consistency" in encoded, "Linux IPC hardware approval authority audit consistency binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistency" in encoded, "gRPC hardware approval authority audit consistency binding missing"
        assert "HW-AAC-002" in encoded and "approval-status-no-store-consistent" in encoded, "hardware approval authority audit consistency missing no-store gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware approval authority audit consistency missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run":
        decision = payload["payload"]
        encoded = json.dumps(decision)
        gate_ids = {item["gate_id"] for item in decision["mandatory_gates"]}
        assert decision["approval_decision_dry_run_state"] == "rejected_blocked_contract_only", "hardware approval decision dry-run did not reject contract-only"
        assert decision["approval_decision_dry_run_validated"] is True, "hardware approval decision dry-run did not validate sample shape"
        assert decision["approval_decision"] == "approve_adapter_load", "hardware approval decision dry-run lost decision intent"
        assert decision["approval_authority_ready"] is False, "hardware approval decision dry-run unexpectedly approved authority"
        assert decision["approval_signature_present"] is True, "hardware approval decision dry-run lost signature marker"
        assert decision["adapter_load_blocked"] is True, "hardware approval decision dry-run did not bind blocker rollup"
        assert decision["validation"]["request_shape_valid"] is True, "hardware approval decision dry-run request shape invalid"
        assert decision["validation"]["approval_status_no_store_bound"] is True, "hardware approval decision dry-run did not bind no-store status"
        assert {"HW-APD-001", "HW-APD-002", "HW-APD-003", "HW-APD-004", "HW-APD-005", "HW-APD-006", "HW-APD-007", "HW-APD-008"} <= gate_ids, "hardware approval decision dry-run missing mandatory gates"
        for key in [
            "approval_authority_ready",
            "approval_record_available",
            "approval_record_persisted",
            "approval_decision_persisted",
            "approval_review_queue_updated",
            "approval_evidence_store_active",
            "approval_decision_passed",
            "approval_authority_assigned",
            "approval_policy_confirmed",
            "approval_workflow_active",
            "adapter_load_allowed",
            "adapter_activation_allowed",
            "hardware_access_allowed",
            "gate_closure_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert decision["summary"][key] is False, f"hardware approval decision dry-run summary unexpectedly set {key}"
        for key in [
            "owner_decision_evidence_adapter_load_approval_decision_dry_run_active",
            "owner_decision_evidence_adapter_load_approval_authority_audit_consistency_active",
            "owner_decision_evidence_adapter_load_approval_authority_status_active",
            "owner_decision_evidence_adapter_load_approval_authority_checklist_active",
            "owner_decision_evidence_adapter_load_blocker_rollup_active",
            "request_shape_valid",
            "approval_decision_dry_run_validated",
            "policy_allowed",
            "approval_status_no_store_consistent",
            "approval_decisions_open",
            "adapter_load_blocked_consistent",
        ]:
            assert decision["summary"][key] is True, f"hardware approval decision dry-run summary did not set {key}"
        assert decision["decision_dry_run_result"]["allowed_to_persist_approval"] is False, "hardware approval decision dry-run allowed approval persistence"
        assert decision["decision_dry_run_result"]["allowed_to_load_adapter"] is False, "hardware approval decision dry-run allowed adapter load"
        assert decision["decision_dry_run_result"]["hardware_accessed"] is False, "hardware approval decision dry-run touched hardware"
        assert "dryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionJson" in encoded, "Android hardware approval decision dry-run binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run" in encoded, "Linux CLI hardware approval decision dry-run binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.dry.run" in encoded, "Linux IPC hardware approval decision dry-run binding missing"
        assert "DryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecision" in encoded, "gRPC hardware approval decision dry-run binding missing"
        assert "HW-APD-006" in encoded and "blocked-contract-only-rejection" in encoded, "hardware approval decision dry-run missing blocked rejection gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware approval decision dry-run missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/status":
        status = payload["payload"]
        encoded = json.dumps(status)
        gate_ids = {item["gate_id"] for item in status["mandatory_gates"]}
        assert status["approval_decision_dry_run_status_state"] == "contract-only-approval-decision-dry-run-status-no-store", "hardware approval decision dry-run status wrong state"
        assert status["approval_decision_dry_run_status_active"] is True, "hardware approval decision dry-run status inactive"
        assert status["last_approval_decision_result_available"] is False, "hardware approval decision dry-run status unexpectedly had last result"
        assert status["persisted_approval_decision_count"] == 0, "hardware approval decision dry-run status persisted decisions"
        assert status["pending_approval_decision_review_count"] == 0, "hardware approval decision dry-run status created review queue"
        assert status["source_surfaces"]["approval_decision_dry_run"]["called_by_status"] is False, "hardware approval decision dry-run status called POST"
        assert status["source_surfaces"]["approval_decision_dry_run"]["last_result_persisted"] is False, "hardware approval decision dry-run status persisted last result"
        assert status["source_surfaces"]["approval_authority_status"]["persisted_approval_record_count"] == 0, "hardware approval decision dry-run status saw persisted approval record"
        assert status["source_surfaces"]["approval_authority_audit_consistency"]["adapter_load_blocked_consistent"] is True, "hardware approval decision dry-run status lost blocked consistency"
        assert {"HW-APS-001", "HW-APS-002", "HW-APS-003", "HW-APS-004", "HW-APS-005", "HW-APS-006", "HW-APS-007", "HW-APS-008"} <= gate_ids, "hardware approval decision dry-run status missing mandatory gates"
        for key in [
            "last_approval_decision_result_available",
            "approval_decision_review_queue_updated",
            "approval_decision_evidence_store_active",
            "approval_decision_persisted",
            "approval_decision_passed",
            "approval_decision_dry_run_allowed_to_load_adapter",
            "decision_dry_run_post_called_by_status",
            "approval_record_available",
            "approval_record_persisted",
            "approval_review_queue_updated",
            "approval_evidence_store_active",
            "adapter_load_allowed",
            "adapter_activation_allowed",
            "hardware_access_allowed",
            "gate_closure_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert status["summary"][key] is False, f"hardware approval decision dry-run status summary unexpectedly set {key}"
        for key in [
            "owner_decision_evidence_adapter_load_approval_decision_dry_run_status_active",
            "owner_decision_evidence_adapter_load_approval_decision_dry_run_active",
            "owner_decision_evidence_adapter_load_approval_authority_audit_consistency_active",
            "owner_decision_evidence_adapter_load_approval_authority_status_active",
            "owner_decision_evidence_adapter_load_blocker_rollup_active",
            "no_store_consistent",
            "approval_decisions_open",
            "approval_status_no_store_consistent",
            "approval_decisions_open_consistent",
            "adapter_load_blocked_consistent",
        ]:
            assert status["summary"][key] is True, f"hardware approval decision dry-run status summary did not set {key}"
        assert status["summary"]["persisted_approval_decision_count"] == 0, "hardware approval decision dry-run status summary persisted decisions"
        assert status["summary"]["pending_approval_decision_review_count"] == 0, "hardware approval decision dry-run status summary created reviews"
        assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunStatusJson" in encoded, "Android hardware approval decision dry-run status binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run-status" in encoded, "Linux CLI hardware approval decision dry-run status binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.dry.run.status" in encoded, "Linux IPC hardware approval decision dry-run status binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunStatus" in encoded, "gRPC hardware approval decision dry-run status binding missing"
        assert "HW-APS-005" in encoded and "last-approval-decision-result-not-stored" in encoded, "hardware approval decision dry-run status missing last-result gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware approval decision dry-run status missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/audit-consistency":
        audit = payload["payload"]
        encoded = json.dumps(audit)
        gate_ids = {item["gate_id"] for item in audit["mandatory_gates"]}
        assert audit["approval_decision_dry_run_audit_consistency_state"] == "contract-only-approval-decision-dry-run-audit-consistency", "hardware approval decision dry-run audit wrong state"
        assert audit["approval_decision_dry_run_audit_consistency_active"] is True, "hardware approval decision dry-run audit inactive"
        assert audit["consistency_checked"] is True, "hardware approval decision dry-run audit did not check consistency"
        assert audit["consistency_passed"] is True, "hardware approval decision dry-run audit failed consistency"
        assert audit["no_store_consistent"] is True, "hardware approval decision dry-run audit lost no-store consistency"
        assert audit["decision_dry_run_rejection_consistent"] is True, "hardware approval decision dry-run audit lost rejection consistency"
        assert audit["approval_authority_audit_consistent"] is True, "hardware approval decision dry-run audit lost authority consistency"
        assert audit["adapter_load_blocked_consistent"] is True, "hardware approval decision dry-run audit lost blocker consistency"
        assert audit["gate_sets_cross_checked"] is True, "hardware approval decision dry-run audit did not cross-check gates"
        assert audit["source_surfaces"]["approval_decision_dry_run"]["called_by_audit_consistency"] is False, "hardware approval decision dry-run audit called POST"
        assert audit["source_surfaces"]["approval_decision_dry_run_status"]["post_called_by_status"] is False, "hardware approval decision dry-run audit saw status POST call"
        assert audit["source_surfaces"]["approval_decision_dry_run_status"]["persisted_approval_decision_count"] == 0, "hardware approval decision dry-run audit saw persisted decisions"
        assert audit["source_surfaces"]["approval_authority_audit_consistency"]["adapter_load_blocked_consistent"] is True, "hardware approval decision dry-run audit lost approval authority blocker consistency"
        assert audit["source_surfaces"]["adapter_load_blocker_rollup"]["adapter_load_ready"] is False, "hardware approval decision dry-run audit saw adapter load ready"
        assert {"HW-APA-001", "HW-APA-002", "HW-APA-003", "HW-APA-004", "HW-APA-005", "HW-APA-006", "HW-APA-007", "HW-APA-008"} <= gate_ids, "hardware approval decision dry-run audit missing mandatory gates"
        for key in [
            "decision_dry_run_post_called_by_audit_consistency",
            "decision_dry_run_post_called_by_status",
            "last_approval_decision_result_available",
            "approval_decision_persisted",
            "approval_decision_review_queue_updated",
            "approval_decision_evidence_store_active",
            "approval_decision_passed",
            "approval_record_available",
            "approval_record_persisted",
            "approval_review_queue_updated",
            "approval_evidence_store_active",
            "adapter_load_allowed",
            "adapter_activation_allowed",
            "hardware_access_allowed",
            "gate_closure_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert audit["summary"][key] is False, f"hardware approval decision dry-run audit summary unexpectedly set {key}"
        for key in [
            "owner_decision_evidence_adapter_load_approval_decision_dry_run_audit_consistency_active",
            "owner_decision_evidence_adapter_load_approval_decision_dry_run_status_active",
            "owner_decision_evidence_adapter_load_approval_decision_dry_run_active",
            "owner_decision_evidence_adapter_load_approval_authority_audit_consistency_active",
            "owner_decision_evidence_adapter_load_approval_authority_status_active",
            "owner_decision_evidence_adapter_load_blocker_rollup_active",
            "consistency_passed",
            "no_store_consistent",
            "decision_dry_run_rejection_consistent",
            "approval_authority_audit_consistent",
            "adapter_load_blocked_consistent",
            "gate_sets_cross_checked",
            "approval_decisions_open",
        ]:
            assert audit["summary"][key] is True, f"hardware approval decision dry-run audit summary did not set {key}"
        assert audit["summary"]["persisted_approval_decision_count"] == 0, "hardware approval decision dry-run audit summary persisted decisions"
        assert audit["summary"]["pending_approval_decision_review_count"] == 0, "hardware approval decision dry-run audit summary created reviews"
        assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunAuditConsistencyJson" in encoded, "Android hardware approval decision dry-run audit binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run-audit-consistency" in encoded, "Linux CLI hardware approval decision dry-run audit binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.dry.run.audit.consistency" in encoded, "Linux IPC hardware approval decision dry-run audit binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunAuditConsistency" in encoded, "gRPC hardware approval decision dry-run audit binding missing"
        assert "HW-APA-003" in encoded and "approval-decision-dry-run-rejection-consistent" in encoded, "hardware approval decision dry-run audit missing rejection gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware approval decision dry-run audit missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix":
        matrix = payload["payload"]
        encoded = json.dumps(matrix)
        gate_ids = {item["gate_id"] for item in matrix["mandatory_gates"]}
        dependency_ids = set(matrix["blockers_by_dependency"])
        assert matrix["approval_decision_closure_blocker_matrix_state"] == "contract-only-approval-decision-closure-blockers-open", "hardware approval closure blocker matrix wrong state"
        assert matrix["approval_decision_closure_blocker_matrix_active"] is True, "hardware approval closure blocker matrix inactive"
        assert matrix["matrix_complete"] is True, "hardware approval closure blocker matrix incomplete"
        assert matrix["closure_ready"] is False, "hardware approval closure blocker matrix unexpectedly closure-ready"
        assert matrix["closure_allowed"] is False, "hardware approval closure blocker matrix allowed closure"
        assert matrix["approval_decision_closure_allowed"] is False, "hardware approval closure blocker matrix allowed approval decision closure"
        assert matrix["approval_decision_closure_status"] == "blocked_contract_only", "hardware approval closure blocker matrix wrong closure status"
        assert matrix["unresolved_blocker_count"] == 13, "hardware approval closure blocker matrix lost blocker count"
        assert len(matrix["closure_blockers"]) == 13, "hardware approval closure blocker matrix lost blockers"
        assert all(item["state"] == "open" for item in matrix["closure_blockers"]), "hardware approval closure blocker matrix has non-open blocker"
        assert all(item["blocks_adapter_load"] and item["blocks_gate_closure"] for item in matrix["closure_blockers"]), "hardware approval closure blocker matrix blockers are not enforced"
        assert all(item["passed"] for item in matrix["source_surface_checks"]), "hardware approval closure blocker matrix source checks failed"
        assert {
            "HW-APM-001",
            "HW-APM-002",
            "HW-APM-003",
            "HW-APM-004",
            "HW-APM-005",
            "HW-APM-006",
            "HW-APM-007",
            "HW-APM-008",
        } <= gate_ids, "hardware approval closure blocker matrix missing mandatory gates"
        assert {
            "approval_authority",
            "approval_policy",
            "owner_signature_source",
            "rbac_mapping",
            "approval_record_schema",
            "approval_evidence_store_owner",
            "review_workflow_owner",
            "target_smoke_evidence",
            "rollback_plan",
            "fault_model",
            "driver_hal_gap_closure_evidence",
            "audit_owner",
            "gate_closure_authority",
        } <= dependency_ids, "hardware approval closure blocker matrix missing dependency blockers"
        assert matrix["source_surfaces"]["approval_decision_dry_run_audit_consistency"]["consistency_passed"] is True, "hardware approval closure blocker matrix lost decision audit source"
        assert matrix["source_surfaces"]["approval_decision_dry_run_status"]["persisted_approval_decision_count"] == 0, "hardware approval closure blocker matrix saw persisted decisions"
        assert matrix["source_surfaces"]["approval_authority_status"]["approval_decisions_open"] is True, "hardware approval closure blocker matrix lost approval-open source"
        assert matrix["source_surfaces"]["approval_authority_audit_consistency"]["adapter_load_blocked_consistent"] is True, "hardware approval closure blocker matrix lost approval audit source"
        assert matrix["source_surfaces"]["adapter_load_blocker_rollup"]["adapter_load_ready"] is False, "hardware approval closure blocker matrix saw adapter load ready"
        for key in [
            "closure_ready",
            "closure_allowed",
            "approval_decision_closure_allowed",
            "approval_authority_assigned",
            "approval_policy_confirmed",
            "approval_signature_rules_confirmed",
            "approval_rbac_confirmed",
            "approval_record_schema_confirmed",
            "approval_record_available",
            "approval_record_persisted",
            "approval_evidence_store_owner_confirmed",
            "approval_evidence_store_active",
            "review_workflow_owner_confirmed",
            "review_workflow_active",
            "target_smoke_evidence_attached",
            "rollback_plan_confirmed",
            "fault_model_confirmed",
            "driver_hal_gap_closure_evidence_attached",
            "audit_owner_confirmed",
            "gate_closure_authority_confirmed",
            "decision_dry_run_post_called_by_closure_matrix",
            "approval_decision_persisted",
            "approval_decision_review_queue_updated",
            "approval_decision_evidence_store_active",
            "approval_decision_passed",
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
            assert matrix["summary"][key] is False, f"hardware approval closure blocker matrix summary unexpectedly set {key}"
        for key in [
            "owner_decision_evidence_adapter_load_approval_decision_closure_blocker_matrix_active",
            "owner_decision_evidence_adapter_load_approval_decision_dry_run_audit_consistency_active",
            "owner_decision_evidence_adapter_load_approval_decision_dry_run_status_active",
            "owner_decision_evidence_adapter_load_approval_authority_audit_consistency_active",
            "owner_decision_evidence_adapter_load_approval_authority_status_active",
            "owner_decision_evidence_adapter_load_blocker_rollup_active",
            "matrix_complete",
            "approval_decisions_open",
            "no_side_effects_consistent",
        ]:
            assert matrix["summary"][key] is True, f"hardware approval closure blocker matrix summary did not set {key}"
        assert matrix["summary"]["unresolved_blocker_count"] == 13, "hardware approval closure blocker matrix summary lost blocker count"
        assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionClosureBlockerMatrixJson" in encoded, "Android hardware approval closure blocker matrix binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-approval-decision-closure-blocker-matrix" in encoded, "Linux CLI hardware approval closure blocker matrix binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.closure.blocker.matrix" in encoded, "Linux IPC hardware approval closure blocker matrix binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionClosureBlockerMatrix" in encoded, "gRPC hardware approval closure blocker matrix binding missing"
        assert "HW-APM-007" in encoded and "android-linux-closure-blocker-parity" in encoded, "hardware approval closure blocker matrix missing synchronized binding gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware approval closure blocker matrix missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix":
        reviewer_matrix = payload["payload"]
        encoded = json.dumps(reviewer_matrix)
        gate_ids = {item["gate_id"] for item in reviewer_matrix["mandatory_gates"]}
        roles = {item["role"] for item in reviewer_matrix["reviewer_rows"]}
        assert reviewer_matrix["approval_decision_reviewer_matrix_state"] == "contract-only-approval-decision-reviewers-unassigned", "hardware approval reviewer matrix wrong state"
        assert reviewer_matrix["approval_decision_reviewer_matrix_active"] is True, "hardware approval reviewer matrix inactive"
        assert reviewer_matrix["matrix_complete"] is True, "hardware approval reviewer matrix incomplete"
        assert reviewer_matrix["review_ready"] is False, "hardware approval reviewer matrix unexpectedly review-ready"
        assert reviewer_matrix["approval_review_allowed"] is False, "hardware approval reviewer matrix allowed approval review"
        assert reviewer_matrix["retention_review_allowed"] is False, "hardware approval reviewer matrix allowed retention review"
        assert reviewer_matrix["gate_closure_allowed"] is False, "hardware approval reviewer matrix allowed gate closure"
        assert reviewer_matrix["adapter_load_allowed"] is False, "hardware approval reviewer matrix allowed adapter load"
        assert reviewer_matrix["approval_decision_reviewer_matrix_status"] == "blocked_contract_only", "hardware approval reviewer matrix wrong status"
        assert reviewer_matrix["unassigned_reviewer_count"] == 11, "hardware approval reviewer matrix lost reviewer count"
        assert len(reviewer_matrix["reviewer_rows"]) == 11, "hardware approval reviewer matrix lost rows"
        assert all(item["state"] == "unassigned" for item in reviewer_matrix["reviewer_rows"]), "hardware approval reviewer matrix assigned reviewers"
        assert all(item["source_blocker_state"] == "open" for item in reviewer_matrix["reviewer_rows"]), "hardware approval reviewer matrix lost open source blocker state"
        assert all(item["blocks_adapter_load"] and item["blocks_gate_closure"] for item in reviewer_matrix["reviewer_rows"]), "hardware approval reviewer matrix blockers are not enforced"
        assert all(item["passed"] for item in reviewer_matrix["source_surface_checks"]), "hardware approval reviewer matrix source checks failed"
        assert {
            "HW-APR-001",
            "HW-APR-002",
            "HW-APR-003",
            "HW-APR-004",
            "HW-APR-005",
            "HW-APR-006",
            "HW-APR-007",
            "HW-APR-008",
        } <= gate_ids, "hardware approval reviewer matrix missing mandatory gates"
        assert {
            "approval_authority_reviewer",
            "approval_policy_reviewer",
            "signature_rbac_reviewer",
            "approval_record_schema_reviewer",
            "approval_evidence_store_reviewer",
            "review_workflow_reviewer",
            "target_smoke_reviewer",
            "rollback_fault_reviewer",
            "driver_hal_gap_reviewer",
            "audit_export_reviewer",
            "gate_closure_reviewer",
        } <= roles, "hardware approval reviewer matrix missing reviewer roles"
        assert reviewer_matrix["source_surfaces"]["approval_decision_closure_blocker_matrix"]["matrix_complete"] is True, "hardware approval reviewer matrix lost closure source matrix"
        assert reviewer_matrix["source_surfaces"]["approval_decision_closure_blocker_matrix"]["approval_decision_closure_allowed"] is False, "hardware approval reviewer matrix allowed closure source"
        for key in [
            "review_ready",
            "approval_review_allowed",
            "retention_review_allowed",
            "approval_decision_closure_allowed",
            "approval_authority_reviewer_assigned",
            "approval_policy_reviewer_assigned",
            "signature_rbac_reviewer_assigned",
            "approval_record_schema_reviewer_assigned",
            "approval_evidence_store_reviewer_assigned",
            "review_workflow_reviewer_assigned",
            "target_smoke_reviewer_assigned",
            "rollback_fault_reviewer_assigned",
            "driver_hal_gap_reviewer_assigned",
            "audit_export_reviewer_assigned",
            "gate_closure_reviewer_assigned",
            "approval_decision_persisted",
            "approval_decision_review_queue_updated",
            "approval_decision_evidence_store_active",
            "approval_evidence_store_active",
            "review_workflow_active",
            "review_queue_updated",
            "gate_state_changed",
            "gates_closed",
            "adapter_load_allowed",
            "adapter_activation_allowed",
            "hardware_access_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert reviewer_matrix["summary"][key] is False, f"hardware approval reviewer matrix summary unexpectedly set {key}"
        for key in [
            "owner_decision_evidence_adapter_load_approval_decision_reviewer_matrix_active",
            "owner_decision_evidence_adapter_load_approval_decision_closure_blocker_matrix_active",
            "matrix_complete",
            "no_side_effects_consistent",
        ]:
            assert reviewer_matrix["summary"][key] is True, f"hardware approval reviewer matrix summary did not set {key}"
        assert reviewer_matrix["summary"]["unassigned_reviewer_count"] == 11, "hardware approval reviewer matrix summary lost reviewer count"
        assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionReviewerMatrixJson" in encoded, "Android hardware approval reviewer matrix binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-approval-decision-reviewer-matrix" in encoded, "Linux CLI hardware approval reviewer matrix binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.reviewer.matrix" in encoded, "Linux IPC hardware approval reviewer matrix binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionReviewerMatrix" in encoded, "gRPC hardware approval reviewer matrix binding missing"
        assert "HW-APR-007" in encoded and "android-linux-reviewer-matrix-parity" in encoded, "hardware approval reviewer matrix missing synchronized binding gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware approval reviewer matrix missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist":
        handoff_checklist = payload["payload"]
        encoded = json.dumps(handoff_checklist)
        gate_ids = {item["gate_id"] for item in handoff_checklist["mandatory_gates"]}
        schema_fields = {item["field"] for item in handoff_checklist["handoff_packet_schema"]}
        assert handoff_checklist["operation"] == "hardware-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-checklist", "hardware approval reviewer handoff wrong operation"
        assert handoff_checklist["approval_reviewer_evidence_handoff_state"] == "contract-only-reviewer-evidence-handoff-blocked", "hardware approval reviewer handoff wrong state"
        assert handoff_checklist["approval_reviewer_evidence_handoff_checklist_active"] is True, "hardware approval reviewer handoff inactive"
        assert handoff_checklist["handoff_checklist_complete"] is True, "hardware approval reviewer handoff incomplete"
        assert handoff_checklist["handoff_ready"] is False, "hardware approval reviewer handoff unexpectedly ready"
        assert handoff_checklist["evidence_handoff_allowed"] is False, "hardware approval reviewer handoff allowed evidence handoff"
        assert handoff_checklist["approval_review_allowed"] is False, "hardware approval reviewer handoff allowed approval review"
        assert handoff_checklist["retention_review_allowed"] is False, "hardware approval reviewer handoff allowed retention review"
        assert handoff_checklist["gate_closure_allowed"] is False, "hardware approval reviewer handoff allowed gate closure"
        assert handoff_checklist["adapter_load_allowed"] is False, "hardware approval reviewer handoff allowed adapter load"
        assert handoff_checklist["required_handoff_packet_count"] == 11, "hardware approval reviewer handoff lost required packet count"
        assert handoff_checklist["missing_handoff_packet_count"] == 11, "hardware approval reviewer handoff lost missing packet count"
        assert len(handoff_checklist["handoff_rows"]) == 11, "hardware approval reviewer handoff lost rows"
        assert {
            "reviewer_identity",
            "source_blocker_reference",
            "evidence_reference_uri",
            "owner_signature_reference",
            "acceptance_rule",
            "retention_policy_reference",
            "audit_export_reference",
            "rollback_fault_note",
        } <= schema_fields, "hardware approval reviewer handoff missing schema fields"
        assert all(item["state"] == "missing" for item in handoff_checklist["handoff_rows"]), "hardware approval reviewer handoff attached rows"
        assert all(item["handoff_packet_attached"] is False for item in handoff_checklist["handoff_rows"]), "hardware approval reviewer handoff packet was attached"
        assert all(item["evidence_handoff_ready"] is False for item in handoff_checklist["handoff_rows"]), "hardware approval reviewer handoff row became ready"
        assert all(item["blocks_approval_review"] and item["blocks_retention_review"] for item in handoff_checklist["handoff_rows"]), "hardware approval reviewer handoff rows do not block review"
        assert all(item["blocks_gate_closure"] and item["blocks_adapter_load"] for item in handoff_checklist["handoff_rows"]), "hardware approval reviewer handoff rows do not block gate/load"
        assert all(item["passed"] for item in handoff_checklist["source_surface_checks"]), "hardware approval reviewer handoff source checks failed"
        assert {
            "HW-ARH-001",
            "HW-ARH-002",
            "HW-ARH-003",
            "HW-ARH-004",
            "HW-ARH-005",
            "HW-ARH-006",
            "HW-ARH-007",
            "HW-ARH-008",
        } <= gate_ids, "hardware approval reviewer handoff missing mandatory gates"
        source = handoff_checklist["source_surfaces"]["approval_decision_reviewer_matrix"]
        assert source["matrix_complete"] is True, "hardware approval reviewer handoff lost reviewer matrix source"
        assert source["unassigned_reviewer_count"] == 11, "hardware approval reviewer handoff lost reviewer count"
        assert source["review_ready"] is False, "hardware approval reviewer handoff reviewer matrix became ready"
        assert source["approval_review_allowed"] is False, "hardware approval reviewer handoff source allowed approval review"
        assert source["retention_review_allowed"] is False, "hardware approval reviewer handoff source allowed retention review"
        for key in [
            "handoff_ready",
            "evidence_handoff_allowed",
            "approval_review_allowed",
            "retention_review_allowed",
            "gate_closure_allowed",
            "approval_decision_closure_allowed",
            "handoff_packet_attached",
            "reviewer_identity_confirmed",
            "evidence_reference_uri_confirmed",
            "owner_signature_reference_confirmed",
            "acceptance_rule_confirmed",
            "retention_policy_reference_confirmed",
            "audit_export_reference_confirmed",
            "rollback_fault_note_confirmed",
            "approval_decision_persisted",
            "approval_decision_review_queue_updated",
            "approval_decision_evidence_store_active",
            "approval_evidence_store_active",
            "review_workflow_active",
            "review_queue_updated",
            "gate_state_changed",
            "gates_closed",
            "adapter_load_allowed",
            "adapter_activation_allowed",
            "hardware_access_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert handoff_checklist["summary"][key] is False, f"hardware approval reviewer handoff summary unexpectedly set {key}"
        for key in [
            "owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_checklist_active",
            "owner_decision_evidence_adapter_load_approval_decision_reviewer_matrix_active",
            "handoff_checklist_complete",
            "no_side_effects_consistent",
        ]:
            assert handoff_checklist["summary"][key] is True, f"hardware approval reviewer handoff summary did not set {key}"
        assert handoff_checklist["summary"]["required_handoff_packet_count"] == 11, "hardware approval reviewer handoff summary lost required packet count"
        assert handoff_checklist["summary"]["missing_handoff_packet_count"] == 11, "hardware approval reviewer handoff summary lost missing packet count"
        assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffChecklistJson" in encoded, "Android hardware approval reviewer handoff binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-checklist" in encoded, "Linux CLI hardware approval reviewer handoff binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.checklist" in encoded, "Linux IPC hardware approval reviewer handoff binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffChecklist" in encoded, "gRPC hardware approval reviewer handoff binding missing"
        assert "HW-ARH-007" in encoded and "android-linux-evidence-handoff-parity" in encoded, "hardware approval reviewer handoff missing synchronized binding gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware approval reviewer handoff missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status":
        acceptance_status = payload["payload"]
        encoded = json.dumps(acceptance_status)
        gate_ids = {item["gate_id"] for item in acceptance_status["mandatory_gates"]}
        assert acceptance_status["operation"] == "hardware-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-status", "hardware approval reviewer handoff acceptance wrong operation"
        assert acceptance_status["approval_reviewer_evidence_handoff_acceptance_state"] == "contract-only-handoff-acceptance-blocked", "hardware approval reviewer handoff acceptance wrong state"
        assert acceptance_status["approval_reviewer_evidence_handoff_acceptance_status_active"] is True, "hardware approval reviewer handoff acceptance inactive"
        assert acceptance_status["acceptance_status_complete"] is True, "hardware approval reviewer handoff acceptance incomplete"
        assert acceptance_status["handoff_ready"] is False, "hardware approval reviewer handoff acceptance handoff unexpectedly ready"
        assert acceptance_status["handoff_acceptance_ready"] is False, "hardware approval reviewer handoff acceptance unexpectedly ready"
        assert acceptance_status["handoff_acceptance_allowed"] is False, "hardware approval reviewer handoff acceptance allowed acceptance"
        assert acceptance_status["evidence_handoff_allowed"] is False, "hardware approval reviewer handoff acceptance allowed evidence handoff"
        assert acceptance_status["approval_review_allowed"] is False, "hardware approval reviewer handoff acceptance allowed approval review"
        assert acceptance_status["retention_review_allowed"] is False, "hardware approval reviewer handoff acceptance allowed retention review"
        assert acceptance_status["gate_closure_allowed"] is False, "hardware approval reviewer handoff acceptance allowed gate closure"
        assert acceptance_status["adapter_load_allowed"] is False, "hardware approval reviewer handoff acceptance allowed adapter load"
        assert acceptance_status["required_acceptance_count"] == 11, "hardware approval reviewer handoff acceptance lost required count"
        assert acceptance_status["blocked_acceptance_count"] == 11, "hardware approval reviewer handoff acceptance lost blocked count"
        assert acceptance_status["accepted_handoff_packet_count"] == 0, "hardware approval reviewer handoff acceptance accepted packets"
        assert acceptance_status["acceptance_record_persisted_count"] == 0, "hardware approval reviewer handoff acceptance persisted records"
        assert acceptance_status["missing_handoff_packet_count"] == 11, "hardware approval reviewer handoff acceptance lost missing packet count"
        assert len(acceptance_status["acceptance_rows"]) == 11, "hardware approval reviewer handoff acceptance lost rows"
        assert all(item["state"] == "blocked_missing_handoff_packet" for item in acceptance_status["acceptance_rows"]), "hardware approval reviewer handoff acceptance rows not blocked"
        assert all(item["handoff_packet_attached"] is False for item in acceptance_status["acceptance_rows"]), "hardware approval reviewer handoff acceptance packet attached"
        assert all(item["handoff_packet_acceptance_ready"] is False for item in acceptance_status["acceptance_rows"]), "hardware approval reviewer handoff acceptance row ready"
        assert all(item["handoff_packet_accepted"] is False for item in acceptance_status["acceptance_rows"]), "hardware approval reviewer handoff acceptance row accepted"
        assert all(item["acceptance_record_persisted"] is False for item in acceptance_status["acceptance_rows"]), "hardware approval reviewer handoff acceptance row persisted"
        assert all(item["blocks_gate_closure"] and item["blocks_adapter_load"] for item in acceptance_status["acceptance_rows"]), "hardware approval reviewer handoff acceptance rows do not block gate/load"
        assert all(item["passed"] for item in acceptance_status["source_surface_checks"]), "hardware approval reviewer handoff acceptance source checks failed"
        assert {
            "HW-AHA-001",
            "HW-AHA-002",
            "HW-AHA-003",
            "HW-AHA-004",
            "HW-AHA-005",
            "HW-AHA-006",
            "HW-AHA-007",
            "HW-AHA-008",
        } <= gate_ids, "hardware approval reviewer handoff acceptance missing mandatory gates"
        source = acceptance_status["source_surfaces"]["approval_reviewer_evidence_handoff_checklist"]
        assert source["handoff_checklist_complete"] is True, "hardware approval reviewer handoff acceptance lost handoff source"
        assert source["required_handoff_packet_count"] == 11, "hardware approval reviewer handoff acceptance lost source required packet count"
        assert source["missing_handoff_packet_count"] == 11, "hardware approval reviewer handoff acceptance lost source missing packet count"
        assert source["handoff_ready"] is False, "hardware approval reviewer handoff acceptance source became ready"
        assert source["evidence_handoff_allowed"] is False, "hardware approval reviewer handoff acceptance source allowed handoff"
        assert source["approval_review_allowed"] is False, "hardware approval reviewer handoff acceptance source allowed approval review"
        assert source["retention_review_allowed"] is False, "hardware approval reviewer handoff acceptance source allowed retention review"
        for key in [
            "handoff_ready",
            "handoff_acceptance_ready",
            "handoff_acceptance_allowed",
            "evidence_handoff_allowed",
            "approval_review_allowed",
            "retention_review_allowed",
            "gate_closure_allowed",
            "approval_decision_closure_allowed",
            "handoff_packet_attached",
            "handoff_packet_accepted",
            "reviewer_identity_accepted",
            "evidence_reference_uri_accepted",
            "owner_signature_reference_accepted",
            "acceptance_rule_accepted",
            "retention_policy_reference_accepted",
            "audit_export_reference_accepted",
            "rollback_fault_note_accepted",
            "approval_decision_persisted",
            "approval_decision_review_queue_updated",
            "approval_decision_evidence_store_active",
            "approval_evidence_store_active",
            "review_workflow_active",
            "review_queue_updated",
            "gate_state_changed",
            "gates_closed",
            "adapter_load_allowed",
            "adapter_activation_allowed",
            "hardware_access_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert acceptance_status["summary"][key] is False, f"hardware approval reviewer handoff acceptance summary unexpectedly set {key}"
        for key in [
            "owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_status_active",
            "owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_checklist_active",
            "acceptance_status_complete",
            "no_side_effects_consistent",
        ]:
            assert acceptance_status["summary"][key] is True, f"hardware approval reviewer handoff acceptance summary did not set {key}"
        assert acceptance_status["summary"]["required_acceptance_count"] == 11, "hardware approval reviewer handoff acceptance summary lost required count"
        assert acceptance_status["summary"]["blocked_acceptance_count"] == 11, "hardware approval reviewer handoff acceptance summary lost blocked count"
        assert acceptance_status["summary"]["accepted_handoff_packet_count"] == 0, "hardware approval reviewer handoff acceptance summary accepted packets"
        assert acceptance_status["summary"]["acceptance_record_persisted_count"] == 0, "hardware approval reviewer handoff acceptance summary persisted records"
        assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceStatusJson" in encoded, "Android hardware approval reviewer handoff acceptance binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-status" in encoded, "Linux CLI hardware approval reviewer handoff acceptance binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.status" in encoded, "Linux IPC hardware approval reviewer handoff acceptance binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceStatus" in encoded, "gRPC hardware approval reviewer handoff acceptance binding missing"
        assert "HW-AHA-007" in encoded and "android-linux-handoff-acceptance-parity" in encoded, "hardware approval reviewer handoff acceptance missing synchronized binding gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware approval reviewer handoff acceptance missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/audit-consistency":
        audit = payload["payload"]
        encoded = json.dumps(audit)
        gate_ids = {item["gate_id"] for item in audit["mandatory_gates"]}
        assert audit["operation"] == "hardware-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-audit-consistency", "hardware approval reviewer handoff acceptance audit wrong operation"
        assert audit["approval_reviewer_evidence_handoff_acceptance_audit_state"] == "contract-only-handoff-acceptance-audit-consistent", "hardware approval reviewer handoff acceptance audit wrong state"
        assert audit["approval_reviewer_evidence_handoff_acceptance_audit_consistency_active"] is True, "hardware approval reviewer handoff acceptance audit inactive"
        for key in [
            "consistency_passed",
            "handoff_checklist_consistent",
            "acceptance_status_consistent",
            "blocked_acceptance_state_consistent",
            "no_store_consistent",
            "no_review_gate_load_consistent",
            "no_side_effects_consistent",
        ]:
            assert audit[key] is True, f"hardware approval reviewer handoff acceptance audit failed {key}"
            assert audit["summary"][key] is True, f"hardware approval reviewer handoff acceptance audit summary failed {key}"
        assert audit["required_handoff_packet_count"] == 11, "hardware approval reviewer handoff acceptance audit lost required handoff count"
        assert audit["missing_handoff_packet_count"] == 11, "hardware approval reviewer handoff acceptance audit lost missing handoff count"
        assert audit["required_acceptance_count"] == 11, "hardware approval reviewer handoff acceptance audit lost required acceptance count"
        assert audit["blocked_acceptance_count"] == 11, "hardware approval reviewer handoff acceptance audit lost blocked acceptance count"
        assert audit["accepted_handoff_packet_count"] == 0, "hardware approval reviewer handoff acceptance audit accepted packets"
        assert audit["acceptance_record_persisted_count"] == 0, "hardware approval reviewer handoff acceptance audit persisted records"
        assert all(item["passed"] for item in audit["audit_checks"]), "hardware approval reviewer handoff acceptance audit checks failed"
        assert {
            "HW-AHC-001",
            "HW-AHC-002",
            "HW-AHC-003",
            "HW-AHC-004",
            "HW-AHC-005",
            "HW-AHC-006",
            "HW-AHC-007",
            "HW-AHC-008",
        } <= gate_ids, "hardware approval reviewer handoff acceptance audit missing mandatory gates"
        source_handoff = audit["source_surfaces"]["approval_reviewer_evidence_handoff_checklist"]
        source_acceptance = audit["source_surfaces"]["approval_reviewer_evidence_handoff_acceptance_status"]
        assert source_handoff["handoff_checklist_complete"] is True, "hardware approval reviewer handoff acceptance audit lost handoff source"
        assert source_acceptance["acceptance_status_complete"] is True, "hardware approval reviewer handoff acceptance audit lost acceptance source"
        assert source_handoff["missing_handoff_packet_count"] == 11, "hardware approval reviewer handoff acceptance audit lost source missing handoff count"
        assert source_acceptance["blocked_acceptance_count"] == 11, "hardware approval reviewer handoff acceptance audit lost source blocked acceptance count"
        for key in [
            "handoff_ready",
            "handoff_acceptance_ready",
            "handoff_acceptance_allowed",
            "evidence_handoff_allowed",
            "approval_review_allowed",
            "retention_review_allowed",
            "gate_closure_allowed",
            "approval_decision_closure_allowed",
            "handoff_packet_attached",
            "handoff_packet_accepted",
            "approval_decision_persisted",
            "approval_decision_review_queue_updated",
            "approval_decision_evidence_store_active",
            "approval_evidence_store_active",
            "review_workflow_active",
            "review_queue_updated",
            "gate_state_changed",
            "gates_closed",
            "adapter_load_allowed",
            "adapter_activation_allowed",
            "hardware_access_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert audit["summary"][key] is False, f"hardware approval reviewer handoff acceptance audit summary unexpectedly set {key}"
        assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceAuditConsistencyJson" in encoded, "Android hardware approval reviewer handoff acceptance audit binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-audit-consistency" in encoded, "Linux CLI hardware approval reviewer handoff acceptance audit binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.audit.consistency" in encoded, "Linux IPC hardware approval reviewer handoff acceptance audit binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceAuditConsistency" in encoded, "gRPC hardware approval reviewer handoff acceptance audit binding missing"
        assert "HW-AHC-007" in encoded and "android-linux-acceptance-audit-parity" in encoded, "hardware approval reviewer handoff acceptance audit missing synchronized binding gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware approval reviewer handoff acceptance audit missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup":
        rollup = payload["payload"]
        encoded = json.dumps(rollup)
        gate_ids = {item["gate_id"] for item in rollup["mandatory_gates"]}
        assert rollup["operation"] == "hardware-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-decision-rollup", "hardware approval reviewer handoff acceptance decision rollup wrong operation"
        assert rollup["approval_reviewer_evidence_handoff_acceptance_decision_rollup_state"] == "contract-only-handoff-acceptance-decision-blocked", "hardware approval reviewer handoff acceptance decision rollup wrong state"
        assert rollup["approval_reviewer_evidence_handoff_acceptance_decision_rollup_active"] is True, "hardware approval reviewer handoff acceptance decision rollup inactive"
        assert rollup["decision_rollup_complete"] is True, "hardware approval reviewer handoff acceptance decision rollup incomplete"
        assert rollup["decision_rollup_consistent"] is True, "hardware approval reviewer handoff acceptance decision rollup inconsistent"
        assert rollup["source_surfaces_bound"] is True, "hardware approval reviewer handoff acceptance decision rollup source surfaces unbound"
        assert rollup["required_decision_count"] == 8, "hardware approval reviewer handoff acceptance decision rollup lost required decisions"
        assert rollup["blocked_decision_count"] == 8, "hardware approval reviewer handoff acceptance decision rollup lost blocked decisions"
        assert rollup["required_handoff_packet_count"] == 11, "hardware approval reviewer handoff acceptance decision rollup lost required handoff count"
        assert rollup["missing_handoff_packet_count"] == 11, "hardware approval reviewer handoff acceptance decision rollup lost missing handoff count"
        assert rollup["required_acceptance_count"] == 11, "hardware approval reviewer handoff acceptance decision rollup lost required acceptance count"
        assert rollup["blocked_acceptance_count"] == 11, "hardware approval reviewer handoff acceptance decision rollup lost blocked acceptance count"
        assert rollup["accepted_handoff_packet_count"] == 0, "hardware approval reviewer handoff acceptance decision rollup accepted packets"
        assert rollup["acceptance_record_persisted_count"] == 0, "hardware approval reviewer handoff acceptance decision rollup persisted records"
        assert all(item["decision_confirmed"] is False for item in rollup["decision_rows"]), "hardware approval reviewer handoff acceptance decision rollup confirmed a decision"
        assert all(item["blocks_adapter_load"] is True for item in rollup["decision_rows"]), "hardware approval reviewer handoff acceptance decision rollup failed to block adapter load"
        assert {
            "HW-AHD-001",
            "HW-AHD-002",
            "HW-AHD-003",
            "HW-AHD-004",
            "HW-AHD-005",
            "HW-AHD-006",
            "HW-AHD-007",
            "HW-AHD-008",
        } <= gate_ids, "hardware approval reviewer handoff acceptance decision rollup missing mandatory gates"
        sources = rollup["source_surfaces"]
        assert sources["approval_reviewer_evidence_handoff_checklist"]["missing_handoff_packet_count"] == 11, "hardware approval reviewer handoff acceptance decision rollup lost checklist source"
        assert sources["approval_reviewer_evidence_handoff_acceptance_status"]["blocked_acceptance_count"] == 11, "hardware approval reviewer handoff acceptance decision rollup lost acceptance source"
        assert sources["approval_reviewer_evidence_handoff_acceptance_audit_consistency"]["consistency_passed"] is True, "hardware approval reviewer handoff acceptance decision rollup lost audit source"
        for key in [
            "acceptance_authority_confirmed",
            "acceptance_record_store_confirmed",
            "review_workflow_owner_confirmed",
            "audit_retention_owner_confirmed",
            "rollback_fault_acceptance_owner_confirmed",
            "driver_hal_acceptance_reviewer_confirmed",
            "gate_closure_authority_confirmed",
            "handoff_packet_presence_confirmed",
            "acceptance_decision_ready",
            "handoff_acceptance_allowed",
            "evidence_handoff_allowed",
            "approval_review_allowed",
            "retention_review_allowed",
            "gate_closure_allowed",
            "approval_decision_closure_allowed",
            "approval_decision_persisted",
            "approval_decision_review_queue_updated",
            "approval_decision_evidence_store_active",
            "approval_evidence_store_active",
            "review_workflow_active",
            "review_queue_updated",
            "gate_state_changed",
            "gates_closed",
            "adapter_load_allowed",
            "adapter_activation_allowed",
            "hardware_access_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert rollup["summary"][key] is False, f"hardware approval reviewer handoff acceptance decision rollup summary unexpectedly set {key}"
        assert rollup["summary"]["decision_rollup_consistent"] is True, "hardware approval reviewer handoff acceptance decision rollup summary inconsistent"
        assert rollup["summary"]["blocked_decision_count"] == 8, "hardware approval reviewer handoff acceptance decision rollup summary lost blocked count"
        assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceDecisionRollupJson" in encoded, "Android hardware approval reviewer handoff acceptance decision rollup binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-decision-rollup" in encoded, "Linux CLI hardware approval reviewer handoff acceptance decision rollup binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.decision.rollup" in encoded, "Linux IPC hardware approval reviewer handoff acceptance decision rollup binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceDecisionRollup" in encoded, "gRPC hardware approval reviewer handoff acceptance decision rollup binding missing"
        assert "HW-AHD-007" in encoded and "android-linux-decision-rollup-parity" in encoded, "hardware approval reviewer handoff acceptance decision rollup missing synchronized binding gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware approval reviewer handoff acceptance decision rollup missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist":
        closure = payload["payload"]
        encoded = json.dumps(closure)
        gate_ids = {item["gate_id"] for item in closure["mandatory_gates"]}
        check_ids = {item["check_id"] for item in closure["closure_readiness_checks"]}
        assert closure["operation"] == "hardware-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-checklist", "hardware approval reviewer handoff acceptance closure readiness wrong operation"
        assert closure["approval_reviewer_evidence_handoff_acceptance_closure_readiness_state"] == "contract-only-handoff-acceptance-closure-not-ready", "hardware approval reviewer handoff acceptance closure readiness wrong state"
        assert closure["approval_reviewer_evidence_handoff_acceptance_closure_readiness_checklist_active"] is True, "hardware approval reviewer handoff acceptance closure readiness inactive"
        assert closure["closure_readiness_complete"] is True, "hardware approval reviewer handoff acceptance closure readiness incomplete"
        assert closure["closure_ready"] is False, "hardware approval reviewer handoff acceptance closure became ready"
        assert closure["closure_blocker_count"] == 8, "hardware approval reviewer handoff acceptance closure lost blockers"
        assert closure["required_closure_check_count"] == 8, "hardware approval reviewer handoff acceptance closure lost required checks"
        assert closure["ready_closure_check_count"] == 0, "hardware approval reviewer handoff acceptance closure unexpectedly passed checks"
        assert closure["source_surfaces_bound"] is True, "hardware approval reviewer handoff acceptance closure source surfaces unbound"
        assert closure["decision_rollup_consistent"] is True, "hardware approval reviewer handoff acceptance closure lost decision rollup consistency"
        assert closure["required_decision_count"] == 8, "hardware approval reviewer handoff acceptance closure lost required decisions"
        assert closure["blocked_decision_count"] == 8, "hardware approval reviewer handoff acceptance closure lost blocked decisions"
        assert closure["required_handoff_packet_count"] == 11, "hardware approval reviewer handoff acceptance closure lost required handoff count"
        assert closure["missing_handoff_packet_count"] == 11, "hardware approval reviewer handoff acceptance closure lost missing handoff count"
        assert closure["required_acceptance_count"] == 11, "hardware approval reviewer handoff acceptance closure lost required acceptance count"
        assert closure["blocked_acceptance_count"] == 11, "hardware approval reviewer handoff acceptance closure lost blocked acceptance count"
        assert closure["accepted_handoff_packet_count"] == 0, "hardware approval reviewer handoff acceptance closure accepted packets"
        assert closure["acceptance_record_persisted_count"] == 0, "hardware approval reviewer handoff acceptance closure persisted records"
        assert all(item["ready"] is False for item in closure["closure_readiness_checks"]), "hardware approval reviewer handoff acceptance closure unexpectedly marked a check ready"
        assert all(item["blocks_adapter_load"] is True for item in closure["closure_readiness_checks"]), "hardware approval reviewer handoff acceptance closure failed to block adapter load"
        assert {
            "HW-AHE-001",
            "HW-AHE-002",
            "HW-AHE-003",
            "HW-AHE-004",
            "HW-AHE-005",
            "HW-AHE-006",
            "HW-AHE-007",
            "HW-AHE-008",
        } <= gate_ids, "hardware approval reviewer handoff acceptance closure readiness missing mandatory gates"
        assert {
            "HW-AHE-CHECK-001",
            "HW-AHE-CHECK-002",
            "HW-AHE-CHECK-003",
            "HW-AHE-CHECK-004",
            "HW-AHE-CHECK-005",
            "HW-AHE-CHECK-006",
            "HW-AHE-CHECK-007",
            "HW-AHE-CHECK-008",
        } <= check_ids, "hardware approval reviewer handoff acceptance closure readiness missing required checks"
        sources = closure["source_surfaces"]
        assert sources["approval_reviewer_evidence_handoff_checklist"]["missing_handoff_packet_count"] == 11, "hardware approval reviewer handoff acceptance closure lost checklist source"
        assert sources["approval_reviewer_evidence_handoff_acceptance_status"]["blocked_acceptance_count"] == 11, "hardware approval reviewer handoff acceptance closure lost acceptance source"
        assert sources["approval_reviewer_evidence_handoff_acceptance_audit_consistency"]["consistency_passed"] is True, "hardware approval reviewer handoff acceptance closure lost audit source"
        assert sources["approval_reviewer_evidence_handoff_acceptance_decision_rollup"]["decision_rollup_consistent"] is True, "hardware approval reviewer handoff acceptance closure lost rollup source"
        for key in [
            "acceptance_authority_ready",
            "acceptance_record_store_ready",
            "review_workflow_ready",
            "audit_retention_ready",
            "rollback_fault_acceptance_ready",
            "driver_hal_acceptance_ready",
            "gate_closure_authority_ready",
            "handoff_packet_presence_ready",
            "closure_ready",
            "handoff_ready",
            "handoff_acceptance_ready",
            "handoff_acceptance_allowed",
            "evidence_handoff_allowed",
            "approval_review_allowed",
            "retention_review_allowed",
            "gate_closure_allowed",
            "approval_decision_closure_allowed",
            "approval_decision_persisted",
            "approval_decision_review_queue_updated",
            "approval_decision_evidence_store_active",
            "approval_evidence_store_active",
            "review_workflow_active",
            "review_queue_updated",
            "gate_state_changed",
            "gates_closed",
            "adapter_load_allowed",
            "adapter_activation_allowed",
            "hardware_access_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert closure["summary"][key] is False, f"hardware approval reviewer handoff acceptance closure readiness summary unexpectedly set {key}"
        assert closure["summary"]["closure_readiness_complete"] is True, "hardware approval reviewer handoff acceptance closure readiness summary incomplete"
        assert closure["summary"]["closure_blocker_count"] == 8, "hardware approval reviewer handoff acceptance closure readiness summary lost blocker count"
        assert closure["summary"]["ready_closure_check_count"] == 0, "hardware approval reviewer handoff acceptance closure readiness summary passed checks"
        assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessChecklistJson" in encoded, "Android hardware approval reviewer handoff acceptance closure readiness binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-checklist" in encoded, "Linux CLI hardware approval reviewer handoff acceptance closure readiness binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.checklist" in encoded, "Linux IPC hardware approval reviewer handoff acceptance closure readiness binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessChecklist" in encoded, "gRPC hardware approval reviewer handoff acceptance closure readiness binding missing"
        assert "HW-AHE-007" in encoded and "android-linux-closure-readiness-parity" in encoded, "hardware approval reviewer handoff acceptance closure readiness missing synchronized binding gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware approval reviewer handoff acceptance closure readiness missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency":
        closure_audit = payload["payload"]
        encoded = json.dumps(closure_audit)
        gate_ids = {item["gate_id"] for item in closure_audit["mandatory_gates"]}
        audit_ids = {item["audit_id"] for item in closure_audit["closure_readiness_audit_rows"]}
        assert closure_audit["operation"] == "hardware-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-audit-consistency", "hardware approval reviewer handoff acceptance closure readiness audit wrong operation"
        assert closure_audit["approval_reviewer_evidence_handoff_acceptance_closure_readiness_audit_state"] == "contract-only-handoff-acceptance-closure-readiness-audit-consistent", "hardware approval reviewer handoff acceptance closure readiness audit wrong state"
        assert closure_audit["approval_reviewer_evidence_handoff_acceptance_closure_readiness_audit_consistency_active"] is True, "hardware approval reviewer handoff acceptance closure readiness audit inactive"
        assert closure_audit["consistency_passed"] is True, "hardware approval reviewer handoff acceptance closure readiness audit failed consistency"
        assert closure_audit["closure_readiness_checklist_consistent"] is True, "hardware approval reviewer handoff acceptance closure readiness audit lost checklist consistency"
        assert closure_audit["closure_check_count_consistent"] is True, "hardware approval reviewer handoff acceptance closure readiness audit lost count consistency"
        assert closure_audit["closure_blocker_state_consistent"] is True, "hardware approval reviewer handoff acceptance closure readiness audit lost blocker consistency"
        assert closure_audit["decision_rollup_closure_consistent"] is True, "hardware approval reviewer handoff acceptance closure readiness audit lost rollup consistency"
        assert closure_audit["no_store_consistent"] is True, "hardware approval reviewer handoff acceptance closure readiness audit lost no-store consistency"
        assert closure_audit["no_review_gate_load_consistent"] is True, "hardware approval reviewer handoff acceptance closure readiness audit lost no-review/gate/load consistency"
        assert closure_audit["no_side_effects_consistent"] is True, "hardware approval reviewer handoff acceptance closure readiness audit lost no-side-effect consistency"
        assert closure_audit["source_surfaces_bound"] is True, "hardware approval reviewer handoff acceptance closure readiness audit source surfaces unbound"
        assert closure_audit["closure_readiness_complete"] is True, "hardware approval reviewer handoff acceptance closure readiness audit incomplete"
        assert closure_audit["closure_ready"] is False, "hardware approval reviewer handoff acceptance closure readiness audit became ready"
        assert closure_audit["closure_blocker_count"] == 8, "hardware approval reviewer handoff acceptance closure readiness audit lost blockers"
        assert closure_audit["required_closure_check_count"] == 8, "hardware approval reviewer handoff acceptance closure readiness audit lost required checks"
        assert closure_audit["ready_closure_check_count"] == 0, "hardware approval reviewer handoff acceptance closure readiness audit unexpectedly passed checks"
        assert all(item["consistent"] is True for item in closure_audit["closure_readiness_audit_rows"]), "hardware approval reviewer handoff acceptance closure readiness audit row inconsistent"
        assert all(item["blocks_adapter_load"] is True for item in closure_audit["closure_readiness_audit_rows"]), "hardware approval reviewer handoff acceptance closure readiness audit failed to block adapter load"
        assert {
            "HW-AHF-001",
            "HW-AHF-002",
            "HW-AHF-003",
            "HW-AHF-004",
            "HW-AHF-005",
            "HW-AHF-006",
            "HW-AHF-007",
            "HW-AHF-008",
        } <= gate_ids, "hardware approval reviewer handoff acceptance closure readiness audit missing mandatory gates"
        assert {
            "HW-AHF-AUDIT-001",
            "HW-AHF-AUDIT-002",
            "HW-AHF-AUDIT-003",
            "HW-AHF-AUDIT-004",
            "HW-AHF-AUDIT-005",
            "HW-AHF-AUDIT-006",
            "HW-AHF-AUDIT-007",
            "HW-AHF-AUDIT-008",
        } <= audit_ids, "hardware approval reviewer handoff acceptance closure readiness audit missing rows"
        sources = closure_audit["source_surfaces"]
        assert sources["approval_reviewer_evidence_handoff_acceptance_closure_readiness_checklist"]["closure_readiness_complete"] is True, "hardware approval reviewer handoff acceptance closure readiness audit lost closure source"
        assert sources["approval_reviewer_evidence_handoff_acceptance_decision_rollup"]["decision_rollup_consistent"] is True, "hardware approval reviewer handoff acceptance closure readiness audit lost rollup source"
        for key in [
            "closure_ready",
            "handoff_acceptance_allowed",
            "evidence_handoff_allowed",
            "approval_review_allowed",
            "retention_review_allowed",
            "gate_closure_allowed",
            "adapter_load_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert closure_audit["summary"][key] is False, f"hardware approval reviewer handoff acceptance closure readiness audit summary unexpectedly set {key}"
        assert closure_audit["summary"]["consistency_passed"] is True, "hardware approval reviewer handoff acceptance closure readiness audit summary failed"
        assert closure_audit["summary"]["closure_blocker_count"] == 8, "hardware approval reviewer handoff acceptance closure readiness audit summary lost blocker count"
        assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessAuditConsistencyJson" in encoded, "Android hardware approval reviewer handoff acceptance closure readiness audit binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-audit-consistency" in encoded, "Linux CLI hardware approval reviewer handoff acceptance closure readiness audit binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.audit.consistency" in encoded, "Linux IPC hardware approval reviewer handoff acceptance closure readiness audit binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessAuditConsistency" in encoded, "gRPC hardware approval reviewer handoff acceptance closure readiness audit binding missing"
        assert "HW-AHF-007" in encoded and "android-linux-closure-readiness-audit-parity" in encoded, "hardware approval reviewer handoff acceptance closure readiness audit missing synchronized binding gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware approval reviewer handoff acceptance closure readiness audit missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup":
        closure_decision = payload["payload"]
        encoded = json.dumps(closure_decision)
        gate_ids = {item["gate_id"] for item in closure_decision["mandatory_gates"]}
        decision_ids = {item["decision_id"] for item in closure_decision["decision_rows"]}
        assert closure_decision["operation"] == "hardware-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-rollup", "hardware approval reviewer handoff acceptance closure readiness decision rollup wrong operation"
        assert closure_decision["approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_rollup_state"] == "contract-only-handoff-acceptance-closure-readiness-decision-blocked", "hardware approval reviewer handoff acceptance closure readiness decision rollup wrong state"
        assert closure_decision["approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_rollup_active"] is True, "hardware approval reviewer handoff acceptance closure readiness decision rollup inactive"
        assert closure_decision["decision_rollup_complete"] is True, "hardware approval reviewer handoff acceptance closure readiness decision rollup incomplete"
        assert closure_decision["decision_rollup_consistent"] is True, "hardware approval reviewer handoff acceptance closure readiness decision rollup inconsistent"
        assert closure_decision["closure_readiness_audit_consistent"] is True, "hardware approval reviewer handoff acceptance closure readiness decision rollup lost audit consistency"
        assert closure_decision["closure_ready"] is False, "hardware approval reviewer handoff acceptance closure readiness decision rollup became closure ready"
        assert closure_decision["closure_decision_ready"] is False, "hardware approval reviewer handoff acceptance closure readiness decision rollup became decision ready"
        assert closure_decision["required_decision_count"] == 8, "hardware approval reviewer handoff acceptance closure readiness decision rollup wrong required decision count"
        assert closure_decision["blocked_decision_count"] == 8, "hardware approval reviewer handoff acceptance closure readiness decision rollup wrong blocked decision count"
        assert closure_decision["closure_blocker_count"] == 8, "hardware approval reviewer handoff acceptance closure readiness decision rollup lost blockers"
        assert closure_decision["required_handoff_packet_count"] == 11, "hardware approval reviewer handoff acceptance closure readiness decision rollup lost handoff count"
        assert closure_decision["missing_handoff_packet_count"] == 11, "hardware approval reviewer handoff acceptance closure readiness decision rollup lost missing handoff count"
        assert closure_decision["required_acceptance_count"] == 11, "hardware approval reviewer handoff acceptance closure readiness decision rollup lost acceptance count"
        assert closure_decision["blocked_acceptance_count"] == 11, "hardware approval reviewer handoff acceptance closure readiness decision rollup lost blocked acceptance count"
        assert closure_decision["accepted_handoff_packet_count"] == 0, "hardware approval reviewer handoff acceptance closure readiness decision rollup accepted handoff packet"
        assert closure_decision["acceptance_record_persisted_count"] == 0, "hardware approval reviewer handoff acceptance closure readiness decision rollup persisted acceptance record"
        assert all(item["consistent"] is True for item in closure_decision["decision_rows"]), "hardware approval reviewer handoff acceptance closure readiness decision row inconsistent"
        assert all(item["blocks_adapter_load"] is True for item in closure_decision["decision_rows"]), "hardware approval reviewer handoff acceptance closure readiness decision row failed to block adapter load"
        assert {
            "HW-AHG-001",
            "HW-AHG-002",
            "HW-AHG-003",
            "HW-AHG-004",
            "HW-AHG-005",
            "HW-AHG-006",
            "HW-AHG-007",
            "HW-AHG-008",
        } <= gate_ids, "hardware approval reviewer handoff acceptance closure readiness decision rollup missing mandatory gates"
        assert {
            "HW-AHG-DECISION-001",
            "HW-AHG-DECISION-002",
            "HW-AHG-DECISION-003",
            "HW-AHG-DECISION-004",
            "HW-AHG-DECISION-005",
            "HW-AHG-DECISION-006",
            "HW-AHG-DECISION-007",
            "HW-AHG-DECISION-008",
        } <= decision_ids, "hardware approval reviewer handoff acceptance closure readiness decision rollup missing rows"
        sources = closure_decision["source_surfaces"]
        assert sources["approval_reviewer_evidence_handoff_acceptance_closure_readiness_audit_consistency"]["consistency_passed"] is True, "hardware approval reviewer handoff acceptance closure readiness decision rollup lost audit source"
        assert sources["approval_reviewer_evidence_handoff_acceptance_closure_readiness_checklist"]["closure_ready"] is False, "hardware approval reviewer handoff acceptance closure readiness decision rollup lost closure source"
        for key in [
            "closure_ready",
            "closure_decision_ready",
            "handoff_acceptance_allowed",
            "evidence_handoff_allowed",
            "approval_review_allowed",
            "retention_review_allowed",
            "gate_closure_allowed",
            "adapter_load_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert closure_decision["summary"][key] is False, f"hardware approval reviewer handoff acceptance closure readiness decision rollup summary unexpectedly set {key}"
        assert closure_decision["summary"]["decision_rollup_consistent"] is True, "hardware approval reviewer handoff acceptance closure readiness decision rollup summary failed"
        assert closure_decision["summary"]["closure_readiness_audit_consistent"] is True, "hardware approval reviewer handoff acceptance closure readiness decision rollup summary lost audit consistency"
        assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionRollupJson" in encoded, "Android hardware approval reviewer handoff acceptance closure readiness decision rollup binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-rollup" in encoded, "Linux CLI hardware approval reviewer handoff acceptance closure readiness decision rollup binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.rollup" in encoded, "Linux IPC hardware approval reviewer handoff acceptance closure readiness decision rollup binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionRollup" in encoded, "gRPC hardware approval reviewer handoff acceptance closure readiness decision rollup binding missing"
        assert "HW-AHG-007" in encoded and "android-linux-closure-readiness-decision-rollup-parity" in encoded, "hardware approval reviewer handoff acceptance closure readiness decision rollup missing synchronized binding gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware approval reviewer handoff acceptance closure readiness decision rollup missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist":
        reviewer_assignment = payload["payload"]
        encoded = json.dumps(reviewer_assignment)
        gate_ids = {item["gate_id"] for item in reviewer_assignment["mandatory_gates"]}
        assignment_ids = {item["assignment_id"] for item in reviewer_assignment["reviewer_assignment_rows"]}
        assert reviewer_assignment["operation"] == "hardware-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-checklist", "hardware closure decision reviewer assignment wrong operation"
        assert reviewer_assignment["approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_state"] == "contract-only-handoff-acceptance-closure-readiness-decision-reviewers-unassigned", "hardware closure decision reviewer assignment wrong state"
        assert reviewer_assignment["approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_checklist_active"] is True, "hardware closure decision reviewer assignment inactive"
        assert reviewer_assignment["reviewer_assignment_checklist_complete"] is True, "hardware closure decision reviewer assignment checklist incomplete"
        assert reviewer_assignment["reviewer_assignment_ready"] is False, "hardware closure decision reviewer assignment became ready"
        assert reviewer_assignment["reviewer_assignment_allowed"] is False, "hardware closure decision reviewer assignment became allowed"
        assert reviewer_assignment["source_decision_rollup_bound"] is True, "hardware closure decision reviewer assignment lost source decision rollup"
        assert reviewer_assignment["required_reviewer_assignment_count"] == 8, "hardware closure decision reviewer assignment wrong required count"
        assert reviewer_assignment["assigned_reviewer_count"] == 0, "hardware closure decision reviewer assignment unexpectedly assigned reviewers"
        assert reviewer_assignment["unassigned_reviewer_count"] == 8, "hardware closure decision reviewer assignment lost unassigned reviewers"
        assert reviewer_assignment["blocked_decision_count"] == 8, "hardware closure decision reviewer assignment lost blocked decision count"
        assert reviewer_assignment["closure_blocker_count"] == 8, "hardware closure decision reviewer assignment lost closure blockers"
        assert reviewer_assignment["accepted_handoff_packet_count"] == 0, "hardware closure decision reviewer assignment accepted handoff packets"
        assert reviewer_assignment["acceptance_record_persisted_count"] == 0, "hardware closure decision reviewer assignment persisted acceptance records"
        assert all(item["reviewer_assigned"] is False for item in reviewer_assignment["reviewer_assignment_rows"]), "hardware closure decision reviewer assignment assigned a reviewer"
        assert all(item["blocks_adapter_load"] is True for item in reviewer_assignment["reviewer_assignment_rows"]), "hardware closure decision reviewer assignment row failed to block adapter load"
        assert {
            "HW-AHH-001",
            "HW-AHH-002",
            "HW-AHH-003",
            "HW-AHH-004",
            "HW-AHH-005",
            "HW-AHH-006",
            "HW-AHH-007",
            "HW-AHH-008",
        } <= gate_ids, "hardware closure decision reviewer assignment missing mandatory gates"
        assert {
            "HW-AHH-REVIEWER-001",
            "HW-AHH-REVIEWER-002",
            "HW-AHH-REVIEWER-003",
            "HW-AHH-REVIEWER-004",
            "HW-AHH-REVIEWER-005",
            "HW-AHH-REVIEWER-006",
            "HW-AHH-REVIEWER-007",
            "HW-AHH-REVIEWER-008",
        } <= assignment_ids, "hardware closure decision reviewer assignment missing rows"
        source = reviewer_assignment["source_surfaces"]["approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_rollup"]
        assert source["decision_rollup_complete"] is True, "hardware closure decision reviewer assignment lost decision source completeness"
        assert source["decision_rollup_consistent"] is True, "hardware closure decision reviewer assignment lost decision source consistency"
        assert source["closure_decision_ready"] is False, "hardware closure decision reviewer assignment lost blocked source"
        for key in [
            "reviewer_assignment_ready",
            "reviewer_assignment_allowed",
            "reviewer_assignments_persisted",
            "reviewer_assignment_queue_updated",
            "handoff_acceptance_allowed",
            "evidence_handoff_allowed",
            "approval_review_allowed",
            "retention_review_allowed",
            "gate_closure_allowed",
            "adapter_load_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert reviewer_assignment["summary"][key] is False, f"hardware closure decision reviewer assignment summary unexpectedly set {key}"
        assert reviewer_assignment["summary"]["no_assignment_side_effects"] is True, "hardware closure decision reviewer assignment side effects detected"
        assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentChecklistJson" in encoded, "Android hardware closure decision reviewer assignment binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-checklist" in encoded, "Linux CLI hardware closure decision reviewer assignment binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.reviewer.assignment.checklist" in encoded, "Linux IPC hardware closure decision reviewer assignment binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentChecklist" in encoded, "gRPC hardware closure decision reviewer assignment binding missing"
        assert "HW-AHH-007" in encoded and "android-linux-closure-decision-reviewer-assignment-parity" in encoded, "hardware closure decision reviewer assignment missing synchronized binding gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware closure decision reviewer assignment missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency":
        reviewer_assignment_audit = payload["payload"]
        encoded = json.dumps(reviewer_assignment_audit)
        gate_ids = {item["gate_id"] for item in reviewer_assignment_audit["mandatory_gates"]}
        assert reviewer_assignment_audit["operation"] == "hardware-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-consistency", "hardware closure decision reviewer assignment audit wrong operation"
        assert reviewer_assignment_audit["approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_audit_state"] == "contract-only-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-consistent", "hardware closure decision reviewer assignment audit wrong state"
        assert reviewer_assignment_audit["approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency_active"] is True, "hardware closure decision reviewer assignment audit inactive"
        assert reviewer_assignment_audit["consistency_passed"] is True, "hardware closure decision reviewer assignment audit consistency failed"
        assert reviewer_assignment_audit["source_reviewer_assignment_checklist_bound"] is True, "hardware closure decision reviewer assignment audit lost source checklist"
        assert reviewer_assignment_audit["reviewer_assignment_checklist_consistent"] is True, "hardware closure decision reviewer assignment audit checklist inconsistent"
        assert reviewer_assignment_audit["reviewer_assignment_count_consistent"] is True, "hardware closure decision reviewer assignment audit count inconsistent"
        assert reviewer_assignment_audit["reviewer_assignment_blocker_state_consistent"] is True, "hardware closure decision reviewer assignment audit blocker state inconsistent"
        assert reviewer_assignment_audit["no_store_consistent"] is True, "hardware closure decision reviewer assignment audit no-store inconsistent"
        assert reviewer_assignment_audit["no_review_queue_gate_load_consistent"] is True, "hardware closure decision reviewer assignment audit queue/gate/load state inconsistent"
        assert reviewer_assignment_audit["no_side_effects_consistent"] is True, "hardware closure decision reviewer assignment audit side effects detected"
        assert reviewer_assignment_audit["android_linux_reviewer_assignment_audit_parity"] is True, "hardware closure decision reviewer assignment audit parity failed"
        assert reviewer_assignment_audit["required_reviewer_assignment_count"] == 8, "hardware closure decision reviewer assignment audit wrong required count"
        assert reviewer_assignment_audit["assigned_reviewer_count"] == 0, "hardware closure decision reviewer assignment audit assigned reviewers"
        assert reviewer_assignment_audit["unassigned_reviewer_count"] == 8, "hardware closure decision reviewer assignment audit lost unassigned reviewers"
        assert {
            "HW-AHI-001",
            "HW-AHI-002",
            "HW-AHI-003",
            "HW-AHI-004",
            "HW-AHI-005",
            "HW-AHI-006",
            "HW-AHI-007",
            "HW-AHI-008",
        } <= gate_ids, "hardware closure decision reviewer assignment audit missing mandatory gates"
        source = reviewer_assignment_audit["source_surfaces"]["approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_checklist"]
        assert source["reviewer_assignment_checklist_complete"] is True, "hardware closure decision reviewer assignment audit lost source checklist completeness"
        assert source["reviewer_assignment_ready"] is False, "hardware closure decision reviewer assignment audit source became ready"
        assert source["assigned_reviewer_count"] == 0, "hardware closure decision reviewer assignment audit source assigned reviewers"
        assert source["unassigned_reviewer_count"] == 8, "hardware closure decision reviewer assignment audit source lost unassigned reviewers"
        for key in [
            "reviewer_assignment_ready",
            "reviewer_assignment_allowed",
            "reviewer_assignments_persisted",
            "reviewer_assignment_queue_updated",
            "handoff_acceptance_allowed",
            "evidence_handoff_allowed",
            "approval_review_allowed",
            "retention_review_allowed",
            "gate_closure_allowed",
            "approval_decision_closure_allowed",
            "approval_decision_persisted",
            "approval_decision_review_queue_updated",
            "approval_decision_evidence_store_active",
            "approval_evidence_store_active",
            "review_workflow_active",
            "review_queue_updated",
            "gate_state_changed",
            "gates_closed",
            "adapter_load_allowed",
            "adapter_activation_allowed",
            "hardware_access_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert reviewer_assignment_audit["summary"][key] is False, f"hardware closure decision reviewer assignment audit summary unexpectedly set {key}"
        assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistencyJson" in encoded, "Android hardware closure decision reviewer assignment audit binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-consistency" in encoded, "Linux CLI hardware closure decision reviewer assignment audit binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.reviewer.assignment.audit.consistency" in encoded, "Linux IPC hardware closure decision reviewer assignment audit binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistency" in encoded, "gRPC hardware closure decision reviewer assignment audit binding missing"
        assert "HW-AHI-007" in encoded and "android-linux-reviewer-assignment-audit-parity" in encoded, "hardware closure decision reviewer assignment audit missing synchronized binding gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware closure decision reviewer assignment audit missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup":
        decision_rollup = payload["payload"]
        encoded = json.dumps(decision_rollup)
        gate_ids = {item["gate_id"] for item in decision_rollup["mandatory_gates"]}
        decision_ids = {item["decision_id"] for item in decision_rollup["decision_rows"]}
        summary = decision_rollup["summary"]
        assert decision_rollup["operation"] == "hardware-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup", "hardware closure decision reviewer assignment audit decision rollup wrong operation"
        assert decision_rollup["approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_state"] == "contract-only-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-blocked", "hardware closure decision reviewer assignment audit decision rollup wrong state"
        assert decision_rollup["approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_active"] is True, "hardware closure decision reviewer assignment audit decision rollup inactive"
        assert summary["decision_rollup_complete"] is True and summary["decision_rollup_consistent"] is True, "hardware closure decision reviewer assignment audit decision rollup inconsistent"
        assert summary["source_reviewer_assignment_audit_bound"] is True and summary["reviewer_assignment_audit_consistent"] is True, "hardware closure decision reviewer assignment audit decision rollup lost audit source"
        assert summary["reviewer_assignment_decision_blocked"] is True, "hardware closure decision reviewer assignment audit decision unexpectedly allowed"
        assert summary["adapter_load_decision"] == "blocked-by-unassigned-reviewers", "hardware closure decision reviewer assignment audit decision rollup wrong adapter decision"
        assert summary["required_decision_count"] == 8 and summary["blocked_decision_count"] == 8, "hardware closure decision reviewer assignment audit decision rollup wrong decision counts"
        assert summary["assigned_reviewer_count"] == 0 and summary["unassigned_reviewer_count"] == 8, "hardware closure decision reviewer assignment audit decision rollup reviewer counts changed"
        assert {
            "HW-AHJ-001",
            "HW-AHJ-002",
            "HW-AHJ-003",
            "HW-AHJ-004",
            "HW-AHJ-005",
            "HW-AHJ-006",
            "HW-AHJ-007",
            "HW-AHJ-008",
        } <= gate_ids, "hardware closure decision reviewer assignment audit decision rollup missing mandatory gates"
        assert {
            "HW-AHJ-DECISION-001",
            "HW-AHJ-DECISION-002",
            "HW-AHJ-DECISION-003",
            "HW-AHJ-DECISION-004",
            "HW-AHJ-DECISION-005",
            "HW-AHJ-DECISION-006",
            "HW-AHJ-DECISION-007",
            "HW-AHJ-DECISION-008",
        } <= decision_ids, "hardware closure decision reviewer assignment audit decision rollup missing decision rows"
        for key in [
            "reviewer_assignment_ready",
            "reviewer_assignment_allowed",
            "reviewer_assignments_persisted",
            "reviewer_assignment_queue_updated",
            "handoff_acceptance_allowed",
            "adapter_load_allowed",
            "adapter_activation_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert summary[key] is False, f"hardware closure decision reviewer assignment audit decision rollup unexpectedly set {key}"
        assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupJson" in encoded, "Android hardware closure decision reviewer assignment audit decision rollup binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup" in encoded, "Linux CLI hardware closure decision reviewer assignment audit decision rollup binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup" in encoded, "Linux IPC hardware closure decision reviewer assignment audit decision rollup binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollup" in encoded, "gRPC hardware closure decision reviewer assignment audit decision rollup binding missing"
        assert "HW-AHJ-007" in encoded and "android-linux-reviewer-assignment-audit-decision-rollup-parity" in encoded, "hardware closure decision reviewer assignment audit decision rollup missing synchronized binding gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware closure decision reviewer assignment audit decision rollup missing Req IDs"
    if path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary":
        handoff_readiness = payload["payload"]
        encoded = json.dumps(handoff_readiness)
        gate_ids = {item["gate_id"] for item in handoff_readiness["mandatory_gates"]}
        dependency_ids = {item["dependency_id"] for item in handoff_readiness["handoff_dependencies"]}
        summary = handoff_readiness["summary"]
        assert handoff_readiness["operation"] == "hardware-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-summary", "hardware closure handoff readiness summary wrong operation"
        assert handoff_readiness["closure_handoff_readiness_summary_state"] == "contract-only-closure-handoff-readiness-blocked", "hardware closure handoff readiness summary wrong state"
        assert handoff_readiness["closure_handoff_readiness_summary_active"] is True, "hardware closure handoff readiness summary inactive"
        assert summary["closure_handoff_readiness_complete"] is True and summary["closure_handoff_ready"] is False and summary["handoff_ready"] is False, "hardware closure handoff readiness summary unexpectedly ready"
        assert summary["source_decision_rollup_bound"] is True and summary["decision_rollup_consistent"] is True, "hardware closure handoff readiness summary lost decision rollup source"
        assert summary["reviewer_assignment_decision_blocked"] is True, "hardware closure handoff readiness summary unexpectedly allowed reviewer assignment"
        assert summary["adapter_load_decision"] == "blocked-by-unassigned-reviewers", "hardware closure handoff readiness summary wrong adapter decision"
        assert summary["required_handoff_dependency_count"] == 8 and summary["open_handoff_dependency_count"] == 8, "hardware closure handoff readiness summary wrong dependency counts"
        assert summary["assigned_reviewer_count"] == 0 and summary["unassigned_reviewer_count"] == 8, "hardware closure handoff readiness summary reviewer counts changed"
        assert {
            "HW-AHK-001",
            "HW-AHK-002",
            "HW-AHK-003",
            "HW-AHK-004",
            "HW-AHK-005",
            "HW-AHK-006",
            "HW-AHK-007",
            "HW-AHK-008",
        } <= gate_ids, "hardware closure handoff readiness summary missing mandatory gates"
        assert {
            "HW-AHK-HANDOFF-001",
            "HW-AHK-HANDOFF-002",
            "HW-AHK-HANDOFF-003",
            "HW-AHK-HANDOFF-004",
            "HW-AHK-HANDOFF-005",
            "HW-AHK-HANDOFF-006",
            "HW-AHK-HANDOFF-007",
            "HW-AHK-HANDOFF-008",
        } <= dependency_ids, "hardware closure handoff readiness summary missing dependency rows"
        for key in [
            "reviewer_assignment_ready",
            "reviewer_assignment_allowed",
            "reviewer_assignments_persisted",
            "reviewer_assignment_queue_updated",
            "handoff_acceptance_allowed",
            "evidence_handoff_allowed",
            "approval_review_allowed",
            "retention_review_allowed",
            "gate_closure_allowed",
            "adapter_load_allowed",
            "adapter_activation_allowed",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]:
            assert summary[key] is False, f"hardware closure handoff readiness summary unexpectedly set {key}"
        assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummaryJson" in encoded, "Android hardware closure handoff readiness summary binding missing"
        assert "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-summary" in encoded, "Linux CLI hardware closure handoff readiness summary binding missing"
        assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.summary" in encoded, "Linux IPC hardware closure handoff readiness summary binding missing"
        assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummary" in encoded, "gRPC hardware closure handoff readiness summary binding missing"
        assert "HW-AHK-007" in encoded and "android-linux-closure-handoff-readiness-summary-parity" in encoded, "hardware closure handoff readiness summary missing synchronized binding gate"
        assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, "hardware closure handoff readiness summary missing Req IDs"
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
    if path == "/uib/events/subscriptions/activation-evidence/approval-dry-run/status":
        status = payload["payload"]
        encoded = json.dumps(status)
        gate_ids = {item["gate_id"] for item in status["mandatory_gates"]}
        assert status["approval_dry_run_status_state"] == "contract-only-approval-dry-run-no-store-status", "activation approval dry-run status left no-store contract state"
        assert status["approval_dry_run_status_active"] is True, "activation approval dry-run status is not active"
        assert status["source_decision_status_rollup"]["active"] is True, "activation approval dry-run status lost decision rollup source"
        assert status["source_decision_status_rollup"]["decision_status_passed"] is False, "activation approval dry-run status unexpectedly passed source decision status"
        assert {"EV-AAS-001", "EV-AAS-002", "EV-AAS-003", "EV-AAS-004", "EV-AAS-005", "EV-AAS-006", "EV-AAS-007", "EV-AAS-008"} <= gate_ids, "activation approval dry-run status missing gates"
        for key in [
            "decision_status_passed",
            "owner_decision_complete",
            "approval_dry_run_invoked",
            "last_result_available",
            "approval_authority_assigned",
            "approval_policy_confirmed",
            "approval_result_store_active",
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
            assert status["summary"][key] is False, f"activation approval dry-run status summary unexpectedly set {key}"
        assert status["summary"]["activation_approval_dry_run_status_active"] is True, "activation approval dry-run status summary not active"
        assert status["summary"]["source_decision_status_rollup_bound"] is True, "activation approval dry-run status not bound to decision status rollup"
        assert status["summary"]["decision_status_consistent"] is True, "activation approval dry-run status source inconsistent"
        assert status["summary"]["persisted_dry_run_count"] == 0, "activation approval dry-run status reported persisted dry-runs"
        assert status["summary"]["pending_approval_count"] == 0, "activation approval dry-run status reported pending approvals"
        assert status["summary"]["approved_gate_count"] == 0, "activation approval dry-run status reported approved gates"
        assert "getEventSubscriptionActivationApprovalDryRunStatusJson" in encoded, "Android activation approval dry-run status binding missing"
        assert "event-subscription-activation-approval-dry-run-status" in encoded, "Linux CLI activation approval dry-run status binding missing"
        assert "uib.events.subscriptions.activation.approval.dry.run.status" in encoded, "Linux IPC activation approval dry-run status binding missing"
        assert "GetEventSubscriptionActivationApprovalDryRunStatus" in encoded, "gRPC activation approval dry-run status binding missing"
        assert "EV-AAS-004" in encoded and "approval-authority" in encoded, "activation approval dry-run status missing approval authority blocker"
        assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, "event subscription activation approval dry-run status missing Req IDs"
    if path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist":
        authority = payload["payload"]
        encoded = json.dumps(authority)
        gate_ids = {item["gate_id"] for item in authority["mandatory_gates"]}
        unresolved_items = [item for item in authority["authority_items"] if not item["ready"]]
        assert authority["operation"] == "event-subscription-activation-approval-authority-checklist", "activation approval authority checklist operation mismatch"
        assert authority["approval_authority_checklist_state"] == "contract-only-approval-authority-blocked", "activation approval authority checklist left blocked contract state"
        assert authority["approval_authority_checklist_active"] is True, "activation approval authority checklist is not active"
        assert authority["source_approval_dry_run_status"]["active"] is True, "activation approval authority checklist lost dry-run status source"
        assert authority["source_approval_dry_run_status"]["approval_dry_run_invoked"] is False, "activation approval authority checklist invoked dry-run"
        assert {"EV-AAA-001", "EV-AAA-002", "EV-AAA-003", "EV-AAA-004", "EV-AAA-005", "EV-AAA-006", "EV-AAA-007", "EV-AAA-008"} <= gate_ids, "activation approval authority checklist missing gates"
        assert len(unresolved_items) == len(authority["authority_items"]), "activation approval authority checklist unexpectedly resolved an item"
        for key in [
            "approval_authority_ready",
            "approval_authority_assigned",
            "approval_policy_confirmed",
            "approval_signature_rbac_confirmed",
            "approval_result_store_active",
            "review_queue_owner_assigned",
            "gate_closure_authority_assigned",
            "broker_activation_owner_assigned",
            "driver_gap_review_owner_assigned",
            "approval_dry_run_invoked",
            "last_result_available",
            "approval_result_store_created",
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
            assert authority["summary"][key] is False, f"activation approval authority checklist summary unexpectedly set {key}"
        assert authority["summary"]["activation_approval_authority_checklist_active"] is True, "activation approval authority checklist summary not active"
        assert authority["summary"]["source_approval_dry_run_status_bound"] is True, "activation approval authority checklist not bound to dry-run status"
        assert authority["summary"]["approval_authority_checklist_complete"] is True, "activation approval authority checklist not complete"
        assert authority["summary"]["required_authority_item_count"] == len(authority["authority_items"]), "activation approval authority checklist item count mismatch"
        assert authority["summary"]["unresolved_authority_item_count"] == len(unresolved_items), "activation approval authority checklist unresolved item count mismatch"
        assert authority["summary"]["persisted_dry_run_count"] == 0, "activation approval authority checklist reported persisted dry-runs"
        assert authority["summary"]["pending_approval_count"] == 0, "activation approval authority checklist reported pending approvals"
        assert "getEventSubscriptionActivationApprovalAuthorityChecklistJson" in encoded, "Android activation approval authority checklist binding missing"
        assert "event-subscription-activation-approval-authority-checklist" in encoded, "Linux CLI activation approval authority checklist binding missing"
        assert "uib.events.subscriptions.activation.approval.authority.checklist" in encoded, "Linux IPC activation approval authority checklist binding missing"
        assert "GetEventSubscriptionActivationApprovalAuthorityChecklist" in encoded, "gRPC activation approval authority checklist binding missing"
        assert "EV-AAA-004" in encoded and "signature-rbac" in encoded, "activation approval authority checklist missing signature/RBAC blocker"
        assert "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, "activation approval authority checklist missing Driver/HAL gap references"
        assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, "event subscription activation approval authority checklist missing Req IDs"
    if path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/audit-consistency":
        audit = payload["payload"]
        encoded = json.dumps(audit)
        gate_ids = {item["gate_id"] for item in audit["mandatory_gates"]}
        finding_ids = {item["finding_id"] for item in audit["audit_findings"]}
        assert audit["operation"] == "event-subscription-activation-approval-authority-audit-consistency", "activation approval authority audit operation mismatch"
        assert audit["approval_authority_audit_state"] == "contract-only-approval-authority-audit-consistent", "activation approval authority audit left consistent state"
        assert audit["approval_authority_audit_consistency_active"] is True, "activation approval authority audit is not active"
        assert {"EV-AAC-001", "EV-AAC-002", "EV-AAC-003", "EV-AAC-004", "EV-AAC-005", "EV-AAC-006", "EV-AAC-007", "EV-AAC-008"} <= gate_ids, "activation approval authority audit missing gates"
        assert {"EV-AAC-AUD-001", "EV-AAC-AUD-002", "EV-AAC-AUD-003", "EV-AAC-AUD-004", "EV-AAC-AUD-005", "EV-AAC-AUD-006", "EV-AAC-AUD-007", "EV-AAC-AUD-008"} <= finding_ids, "activation approval authority audit missing findings"
        assert all(item["consistent"] is True for item in audit["audit_findings"]), "activation approval authority audit findings inconsistent"
        for key in [
            "approval_authority_ready",
            "approval_authority_assigned",
            "approval_policy_confirmed",
            "approval_signature_rbac_confirmed",
            "approval_result_store_active",
            "review_queue_owner_assigned",
            "gate_closure_authority_assigned",
            "broker_activation_owner_assigned",
            "driver_gap_review_owner_assigned",
            "approval_dry_run_invoked",
            "approval_result_store_created",
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
            assert audit["summary"][key] is False, f"activation approval authority audit summary unexpectedly set {key}"
        for key in [
            "activation_approval_authority_audit_consistency_active",
            "consistency_passed",
            "source_authority_checklist_bound",
            "source_approval_dry_run_status_bound",
            "source_decision_status_rollup_bound",
            "authority_item_count_consistent",
            "authority_blocker_state_consistent",
            "approval_no_store_consistent",
            "android_linux_parity_consistent",
            "no_side_effects_consistent",
        ]:
            assert audit["summary"][key] is True, f"activation approval authority audit summary did not set {key}"
        assert audit["summary"]["required_authority_item_count"] == audit["summary"]["unresolved_authority_item_count"], "activation approval authority audit unexpectedly resolved an authority item"
        assert audit["summary"]["blocking_decision_count"] > 0, "activation approval authority audit lost blocker count"
        assert audit["summary"]["persisted_dry_run_count"] == 0, "activation approval authority audit reported persisted dry-runs"
        assert "getEventSubscriptionActivationApprovalAuthorityAuditConsistencyJson" in encoded, "Android activation approval authority audit binding missing"
        assert "event-subscription-activation-approval-authority-audit-consistency" in encoded, "Linux CLI activation approval authority audit binding missing"
        assert "uib.events.subscriptions.activation.approval.authority.audit.consistency" in encoded, "Linux IPC activation approval authority audit binding missing"
        assert "GetEventSubscriptionActivationApprovalAuthorityAuditConsistency" in encoded, "gRPC activation approval authority audit binding missing"
        assert "EV-AAC-006" in encoded and "no-store" in encoded, "activation approval authority audit missing no-store finding"
        assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, "event subscription activation approval authority audit missing Req IDs"
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
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" event-subscription-activation-approval-dry-run-status >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" event-subscription-activation-approval-authority-checklist >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" event-subscription-activation-approval-authority-audit-consistency >/dev/null
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
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-approval-authority-status >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-approval-authority-audit-consistency >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run-status >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run-audit-consistency >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-approval-decision-closure-blocker-matrix >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-approval-decision-reviewer-matrix >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-checklist >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-status >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-audit-consistency >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-decision-rollup >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-checklist >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-audit-consistency >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-rollup >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-checklist >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-consistency >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-summary >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" vehicle-signals >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" vehicle-signal-activation >/dev/null

echo "linux cli smoke ok"
