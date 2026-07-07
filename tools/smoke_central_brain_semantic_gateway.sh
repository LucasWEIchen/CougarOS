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
        assert "getEventSubscriptionsJson" in encoded, "Android event subscription binding visibility missing"
        assert "requestEventSubscriptionJson" in encoded, "Android event subscription request binding visibility missing"
        assert "cancelEventSubscriptionJson" in encoded, "Android event subscription cancel binding visibility missing"
        assert "getEventSubscriptionCallbackWatchShapeJson" in encoded, "Android event subscription callback/watch shape binding visibility missing"
        assert "getEventSubscriptionCursorReplayStorageJson" in encoded, "Android event subscription cursor/replay storage binding visibility missing"
        assert "getEventSubscriptionBackpressureQosEvidenceJson" in encoded, "Android event subscription backpressure/QoS binding visibility missing"
        assert "getEventSubscriptionReadinessRollupJson" in encoded, "Android event subscription readiness rollup binding visibility missing"
        assert "uib.events.subscriptions.get" in encoded, "Linux IPC event subscription binding visibility missing"
        assert "uib.events.subscriptions.request" in encoded, "Linux IPC event subscription request binding visibility missing"
        assert "uib.events.subscriptions.cancel" in encoded, "Linux IPC event subscription cancel binding visibility missing"
        assert "uib.events.subscriptions.callback.watch.shape" in encoded, "Linux IPC event subscription callback/watch shape binding visibility missing"
        assert "uib.events.subscriptions.cursor.replay.storage" in encoded, "Linux IPC event subscription cursor/replay storage binding visibility missing"
        assert "uib.events.subscriptions.backpressure.qos.evidence" in encoded, "Linux IPC event subscription backpressure/QoS binding visibility missing"
        assert "uib.events.subscriptions.readiness.rollup" in encoded, "Linux IPC event subscription readiness rollup binding visibility missing"
        assert "GetEventSubscriptions" in encoded, "gRPC event subscription binding visibility missing"
        assert "RequestEventSubscription" in encoded, "gRPC event subscription request binding visibility missing"
        assert "CancelEventSubscription" in encoded, "gRPC event subscription cancel binding visibility missing"
        assert "GetEventSubscriptionCallbackWatchShape" in encoded, "gRPC event subscription callback/watch shape binding visibility missing"
        assert "GetEventSubscriptionCursorReplayStorage" in encoded, "gRPC event subscription cursor/replay storage binding visibility missing"
        assert "GetEventSubscriptionBackpressureQosEvidence" in encoded, "gRPC event subscription backpressure/QoS binding visibility missing"
        assert "GetEventSubscriptionReadinessRollup" in encoded, "gRPC event subscription readiness rollup binding visibility missing"
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
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" vehicle-signals >/dev/null
CENTRAL_BRAIN_BASE_URL="$BASE_URL" python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" vehicle-signal-activation >/dev/null

echo "linux cli smoke ok"
