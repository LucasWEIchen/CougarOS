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
EVENT_SUBSCRIPTIONS_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscriptions)"
python3 - "$EVENT_SUBSCRIPTIONS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["subscription_state"] == "contract-only-not-brokered", response
assert payload["broker_active"] is False, response
assert payload["active_subscriptions"] == [], response
assert {"EV-SUB-001", "EV-SUB-002", "EV-SUB-003", "EV-SUB-004", "EV-SUB-005", "EV-SUB-006"} <= gate_ids, response
for key in [
    "broker_active",
    "subscription_persistence_active",
    "cursor_storage_active",
    "evidence_store_active",
    "review_workflow_active",
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["backpressure_qos_evidence_contract_active"] is True, response
assert payload["summary"]["readiness_rollup_contract_active"] is True, response
assert payload["summary"]["activation_evidence_contract_active"] is True, response
assert payload["summary"]["activation_evidence_status_contract_active"] is True, response
assert payload["summary"]["activation_evidence_retention_checklist_active"] is True, response
assert payload["summary"]["persisted_submission_count"] == 0, response
assert payload["summary"]["pending_review_count"] == 0, response
assert "getEventSubscriptionsJson" in encoded, response
assert "requestEventSubscriptionJson" in encoded, response
assert "cancelEventSubscriptionJson" in encoded, response
assert "getEventSubscriptionCallbackWatchShapeJson" in encoded, response
assert "getEventSubscriptionCursorReplayStorageJson" in encoded, response
assert "getEventSubscriptionBackpressureQosEvidenceJson" in encoded, response
assert "getEventSubscriptionReadinessRollupJson" in encoded, response
assert "submitEventSubscriptionActivationEvidenceJson" in encoded, response
assert "getEventSubscriptionActivationEvidenceStatusJson" in encoded, response
assert "getEventSubscriptionActivationEvidenceRetentionChecklistJson" in encoded, response
assert "uib.events.subscriptions.get" in encoded, response
assert "uib.events.subscriptions.request" in encoded, response
assert "uib.events.subscriptions.cancel" in encoded, response
assert "uib.events.subscriptions.callback.watch.shape" in encoded, response
assert "uib.events.subscriptions.cursor.replay.storage" in encoded, response
assert "uib.events.subscriptions.backpressure.qos.evidence" in encoded, response
assert "uib.events.subscriptions.readiness.rollup" in encoded, response
assert "uib.events.subscriptions.activation.evidence" in encoded, response
assert "uib.events.subscriptions.activation.evidence.status" in encoded, response
assert "uib.events.subscriptions.activation.evidence.retention.checklist" in encoded, response
assert "GetEventSubscriptions" in encoded, response
assert "RequestEventSubscription" in encoded, response
assert "CancelEventSubscription" in encoded, response
assert "GetEventSubscriptionCallbackWatchShape" in encoded, response
assert "GetEventSubscriptionCursorReplayStorage" in encoded, response
assert "GetEventSubscriptionBackpressureQosEvidence" in encoded, response
assert "GetEventSubscriptionReadinessRollup" in encoded, response
assert "SubmitEventSubscriptionActivationEvidence" in encoded, response
assert "GetEventSubscriptionActivationEvidenceStatus" in encoded, response
assert "GetEventSubscriptionActivationEvidenceRetentionChecklist" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded, response
PY
EVENT_SUBSCRIBE_REQUEST_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscribe-request)"
python3 - "$EVENT_SUBSCRIBE_REQUEST_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
assert response["status"] == "ok", response
assert payload["state"] == "validated_contract_only", response
assert payload["subscription_record"]["persisted"] is False, response
assert payload["subscription_record"]["active"] is False, response
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
    assert payload["summary"][key] is False, response
encoded = json.dumps(payload)
assert "XSC-005" in encoded and "NV-P-006" in encoded, response
PY
EVENT_SUBSCRIBE_CANCEL_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscribe-cancel)"
python3 - "$EVENT_SUBSCRIBE_CANCEL_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
assert response["status"] == "ok", response
assert payload["state"] == "cancelled_contract_only", response
assert payload["lifecycle_transition"]["matched_active_subscription"] is False, response
assert payload["subscription_record"]["persisted"] is False, response
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
    assert payload["summary"][key] is False, response
encoded = json.dumps(payload)
assert "XSC-005" in encoded and "NV-P-006" in encoded, response
PY
EVENT_SUBSCRIPTION_TRANSPORT_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-transport-readiness)"
python3 - "$EVENT_SUBSCRIPTION_TRANSPORT_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["readiness_state"] == "contract-only-no-transport-selected", response
assert payload["transport_selected"] is False, response
assert {"EV-TR-001", "EV-TR-002", "EV-TR-003", "EV-TR-004", "EV-TR-005", "EV-TR-006"} <= gate_ids, response
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
    assert payload["summary"][key] is False, response
assert "getEventSubscriptionTransportReadinessJson" in encoded, response
assert "event-subscription-transport-readiness" in encoded, response
assert "uib.events.subscriptions.transport.readiness" in encoded, response
assert "GetEventSubscriptionTransportReadiness" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_DECISION_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-decision-matrix)"
python3 - "$EVENT_SUBSCRIPTION_DECISION_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["decision_state"] == "contract-only-owner-matrix-open", response
assert payload["production_activation_allowed"] is False, response
assert {"EV-DM-001", "EV-DM-002", "EV-DM-003", "EV-DM-004", "EV-DM-005", "EV-DM-006", "EV-DM-007"} <= gate_ids, response
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
    assert payload["summary"][key] is False, response
assert "getEventSubscriptionDecisionMatrixJson" in encoded, response
assert "event-subscription-decision-matrix" in encoded, response
assert "uib.events.subscriptions.decision.matrix" in encoded, response
assert "GetEventSubscriptionDecisionMatrix" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-checklist)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["checklist"]}
assert response["status"] == "ok", response
assert payload["activation_state"] == "contract-only-activation-blocked", response
assert payload["activation_allowed"] is False, response
assert {"EV-ACT-001", "EV-ACT-002", "EV-ACT-003", "EV-ACT-004", "EV-ACT-005", "EV-ACT-006", "EV-ACT-007", "EV-ACT-008"} <= gate_ids, response
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
    assert payload["summary"][key] is False, response
assert "getEventSubscriptionActivationChecklistJson" in encoded, response
assert "event-subscription-activation-checklist" in encoded, response
assert "uib.events.subscriptions.activation.checklist" in encoded, response
assert "GetEventSubscriptionActivationChecklist" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_CALLBACK_SHAPE_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-callback-watch-shape)"
python3 - "$EVENT_SUBSCRIPTION_CALLBACK_SHAPE_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["shape_state"] == "contract-only-callback-watch-shape-draft", response
assert payload["shape_confirmed"] is False, response
assert {"EV-CW-001", "EV-CW-002", "EV-CW-003", "EV-CW-004", "EV-CW-005", "EV-CW-006", "EV-CW-007", "EV-CW-008"} <= gate_ids, response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["callback_watch_shape_contract_active"] is True, response
assert payload["summary"]["android_callback_shape_drafted"] is True, response
assert payload["summary"]["linux_watch_shape_drafted"] is True, response
assert "getEventSubscriptionCallbackWatchShapeJson" in encoded, response
assert "event-subscription-callback-watch-shape" in encoded, response
assert "uib.events.subscriptions.callback.watch.shape" in encoded, response
assert "GetEventSubscriptionCallbackWatchShape" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_CURSOR_REPLAY_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-cursor-replay-storage)"
python3 - "$EVENT_SUBSCRIPTION_CURSOR_REPLAY_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["cursor_replay_state"] == "contract-only-cursor-replay-storage-draft", response
assert payload["cursor_replay_storage_confirmed"] is False, response
assert {"EV-CRS-001", "EV-CRS-002", "EV-CRS-003", "EV-CRS-004", "EV-CRS-005", "EV-CRS-006", "EV-CRS-007", "EV-CRS-008"} <= gate_ids, response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["cursor_replay_storage_contract_active"] is True, response
assert "getEventSubscriptionCursorReplayStorageJson" in encoded, response
assert "event-subscription-cursor-replay-storage" in encoded, response
assert "uib.events.subscriptions.cursor.replay.storage" in encoded, response
assert "GetEventSubscriptionCursorReplayStorage" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_BACKPRESSURE_QOS_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-backpressure-qos-evidence)"
python3 - "$EVENT_SUBSCRIPTION_BACKPRESSURE_QOS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["backpressure_qos_state"] == "contract-only-backpressure-qos-evidence-draft", response
assert payload["backpressure_qos_evidence_confirmed"] is False, response
assert {"EV-QOS-001", "EV-QOS-002", "EV-QOS-003", "EV-QOS-004", "EV-QOS-005", "EV-QOS-006", "EV-QOS-007", "EV-QOS-008"} <= gate_ids, response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["backpressure_qos_evidence_contract_active"] is True, response
assert "getEventSubscriptionBackpressureQosEvidenceJson" in encoded, response
assert "event-subscription-backpressure-qos-evidence" in encoded, response
assert "uib.events.subscriptions.backpressure.qos.evidence" in encoded, response
assert "GetEventSubscriptionBackpressureQosEvidence" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_READINESS_ROLLUP_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-readiness-rollup)"
python3 - "$EVENT_SUBSCRIPTION_READINESS_ROLLUP_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
section_ids = {item["section_id"] for item in payload["readiness_sections"]}
blocker_ids = {item["blocker_id"] for item in payload["activation_blockers"]}
assert response["status"] == "ok", response
assert payload["readiness_rollup_state"] == "contract-only-readiness-rollup-blocked", response
assert payload["readiness_rollup_confirmed"] is False, response
assert {"EV-ROLLUP-LIFECYCLE", "EV-ROLLUP-TRANSPORT", "EV-ROLLUP-OWNER-DECISIONS", "EV-ROLLUP-ACTIVATION", "EV-ROLLUP-CALLBACK-WATCH", "EV-ROLLUP-CURSOR-REPLAY", "EV-ROLLUP-BACKPRESSURE-QOS"} <= section_ids, response
assert {"EV-RU-001", "EV-RU-002", "EV-RU-003", "EV-RU-004", "EV-RU-005", "EV-RU-006"} <= blocker_ids, response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["readiness_rollup_contract_active"] is True, response
assert payload["summary"]["blocked_gate_count"] > 0, response
assert "getEventSubscriptionReadinessRollupJson" in encoded, response
assert "event-subscription-readiness-rollup" in encoded, response
assert "uib.events.subscriptions.readiness.rollup" in encoded, response
assert "GetEventSubscriptionReadinessRollup" in encoded, response
assert "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_EVIDENCE_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-evidence)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_EVIDENCE_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["evidence_intake_state"] == "validated_contract_only", response
assert payload["intake_validated"] is True, response
assert {"EV-AE-001", "EV-AE-002", "EV-AE-003", "EV-AE-004", "EV-AE-005", "EV-AE-006", "EV-AE-007", "EV-AE-008"} <= gate_ids, response
assert payload["review_result"]["accepted_for_review"] is False, response
assert payload["review_result"]["evidence_persisted"] is False, response
assert payload["review_result"]["gates_closed"] is False, response
assert payload["review_result"]["activation_allowed"] is False, response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["activation_evidence_contract_active"] is True, response
assert "submitEventSubscriptionActivationEvidenceJson" in encoded, response
assert "event-subscription-activation-evidence" in encoded, response
assert "uib.events.subscriptions.activation.evidence" in encoded, response
assert "SubmitEventSubscriptionActivationEvidence" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_EVIDENCE_STATUS_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-evidence-status)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_EVIDENCE_STATUS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["review_status_state"] == "contract-only-no-evidence-store", response
assert {"EV-AES-001", "EV-AES-002", "EV-AES-003", "EV-AES-004", "EV-AES-005", "EV-AES-006"} <= gate_ids, response
assert payload["review_pipeline"]["evidence_store_active"] is False, response
assert payload["review_pipeline"]["review_workflow_active"] is False, response
assert payload["review_pipeline"]["gates_closed"] is False, response
assert payload["counters"]["persisted_submission_count"] == 0, response
assert payload["counters"]["pending_review_count"] == 0, response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["activation_evidence_status_contract_active"] is True, response
assert payload["summary"]["persisted_submission_count"] == 0, response
assert payload["summary"]["pending_review_count"] == 0, response
assert "getEventSubscriptionActivationEvidenceStatusJson" in encoded, response
assert "event-subscription-activation-evidence-status" in encoded, response
assert "uib.events.subscriptions.activation.evidence.status" in encoded, response
assert "GetEventSubscriptionActivationEvidenceStatus" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_EVIDENCE_RETENTION_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-evidence-retention-checklist)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_EVIDENCE_RETENTION_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["retention_checklist_state"] == "contract-only-retention-owner-checklist-open", response
assert {"EV-AER-001", "EV-AER-002", "EV-AER-003", "EV-AER-004", "EV-AER-005", "EV-AER-006", "EV-AER-007", "EV-AER-008"} <= gate_ids, response
assert payload["storage_activation_allowed"] is False, response
assert payload["owner_decision_complete"] is False, response
assert payload["evidence_uri_rules"]["uri_rules_confirmed"] is False, response
assert payload["retention_policy_shape"]["retention_policy_confirmed"] is False, response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["activation_evidence_retention_checklist_active"] is True, response
assert payload["summary"]["persisted_submission_count"] == 0, response
assert payload["summary"]["pending_review_count"] == 0, response
assert "getEventSubscriptionActivationEvidenceRetentionChecklistJson" in encoded, response
assert "event-subscription-activation-evidence-retention-checklist" in encoded, response
assert "uib.events.subscriptions.activation.evidence.retention.checklist" in encoded, response
assert "GetEventSubscriptionActivationEvidenceRetentionChecklist" in encoded, response
assert "EV-AER-006" in encoded and "delete-export-semantics" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
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
assert "vehicle-signal-activation-criteria" in targets, response
assert "vehicle-signal-validation-envelope" in targets, response
assert payload["summary"]["production_ready"] is False, response
assert payload["summary"]["android_debug_ready"] is True, response
assert payload["summary"]["linux_samples_ready"] is True, response
assert payload["summary"]["driver_development_triggered"] is False, response
assert payload["summary"]["virtualization_development_triggered"] is False, response
assert "AAOS signing" in encoded and "target Linux distro" in encoded, response
PY
PROTOTYPE_READINESS_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" prototype-readiness)"
python3 - "$PROTOTYPE_READINESS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
module_ids = {row["module_id"] for row in payload["modules"]}
assert response["status"] == "ok", response
assert "ai-sdk-agent-facade" in module_ids, response
assert "uni-info-bus" in module_ids, response
assert "runtime-governance" in module_ids, response
assert "protocol-binding" in module_ids, response
assert "hardware-empty-interfaces" in module_ids, response
assert payload["summary"]["python_prototype_ready_for_contract_demo"] is True, response
assert payload["summary"]["production_ready"] is False, response
assert payload["summary"]["hardware_accessed"] is False, response
assert payload["summary"]["driver_development_triggered"] is False, response
assert payload["summary"]["virtualization_development_triggered"] is False, response
assert payload["summary"]["service_dispatch_triggered"] is False, response
assert "prototype.readiness.get" in encoded and "GetPrototypeReadiness" in encoded, response
assert "getVehicleSignalActivationJson" in encoded and "vehicle-signal-activation" in encoded, response
assert "getVehicleSignalValidationJson" in encoded and "vehicle-signal-validation" in encoded, response
assert "DEV-003" in encoded and "ISSUE-014" in encoded, response
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
HARDWARE_ACTIVATION_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-activation-checklist)"
python3 - "$HARDWARE_ACTIVATION_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["activation_checklist_state"] == "contract-only-no-hardware-activation", response
assert payload["activation_allowed"] is False, response
assert {"HW-ACT-001", "HW-ACT-002", "HW-ACT-003", "HW-ACT-004", "HW-ACT-005", "HW-ACT-006", "HW-ACT-007", "HW-ACT-008"} <= gate_ids, response
assert payload["owner_decision_shape"]["owner_decision_complete"] is False, response
assert payload["test_evidence_shape"]["target_hardware_smoke_attached"] is False, response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["hardware_activation_checklist_active"] is True, response
assert "getHardwareInterfaceActivationChecklistJson" in encoded, response
assert "hardware-interface-activation-checklist" in encoded, response
assert "hardware.interfaces.activation.checklist" in encoded, response
assert "GetHardwareInterfaceActivationChecklist" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_STATUS_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-status)"
python3 - "$HARDWARE_OWNER_STATUS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["decision_gates"]}
open_gate_ids = set(payload["rollup"]["open_gate_ids"])
assert response["status"] == "ok", response
assert payload["owner_decision_status_state"] == "contract-only-owner-decisions-open", response
assert payload["activation_allowed"] is False, response
assert {"HW-ODS-001", "HW-ODS-002", "HW-ODS-003", "HW-ODS-004", "HW-ODS-005", "HW-ODS-006", "HW-ODS-007", "HW-ODS-008"} <= gate_ids, response
assert {"HW-ODS-001", "HW-ODS-002", "HW-ODS-003", "HW-ODS-004", "HW-ODS-005", "HW-ODS-006", "HW-ODS-007"} <= open_gate_ids, response
assert payload["rollup"]["blocked_interface_count"] == len(payload["interfaces"]), response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["owner_decision_status_active"] is True, response
assert "getHardwareInterfaceOwnerDecisionStatusJson" in encoded, response
assert "hardware-interface-owner-decision-status" in encoded, response
assert "hardware.interfaces.owner.decision.status" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionStatus" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence)"
python3 - "$HARDWARE_OWNER_EVIDENCE_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "hardware-owner-decision-evidence", response
assert payload["evidence_intake_state"] == "validated_contract_only", response
assert payload["intake_validated"] is True, response
assert payload["unknown_interface_ids"] == [], response
assert payload["invalid_evidence_ref_indexes"] == [], response
assert payload["validation"]["evidence_refs_shape_valid"] is True, response
assert {"HW-ODE-001", "HW-ODE-002", "HW-ODE-003", "HW-ODE-004", "HW-ODE-005", "HW-ODE-006", "HW-ODE-007", "HW-ODE-008"} <= gate_ids, response
assert payload["review_result"]["accepted_for_review"] is False, response
assert payload["review_result"]["evidence_persisted"] is False, response
assert payload["review_result"]["review_queue_updated"] is False, response
assert payload["review_result"]["owner_assigned"] is False, response
assert payload["review_result"]["gates_closed"] is False, response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["owner_decision_evidence_contract_active"] is True, response
assert payload["summary"]["owner_decision_evidence_validated"] is True, response
assert "submitHardwareInterfaceOwnerDecisionEvidenceJson" in encoded, response
assert "hardware-interface-owner-decision-evidence" in encoded, response
assert "hardware.interfaces.owner.decision.evidence" in encoded, response
assert "SubmitHardwareInterfaceOwnerDecisionEvidence" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_STATUS_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-status)"
python3 - "$HARDWARE_OWNER_EVIDENCE_STATUS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["owner_decision_evidence_status_state"] == "contract-only-no-evidence-store", response
assert payload["review_pipeline"]["evidence_store_active"] is False, response
assert payload["review_pipeline"]["review_workflow_active"] is False, response
assert payload["counters"]["persisted_submission_count"] == 0, response
assert payload["counters"]["pending_review_count"] == 0, response
assert {"HW-OES-001", "HW-OES-002", "HW-OES-003", "HW-OES-004", "HW-OES-005", "HW-OES-006", "HW-OES-007", "HW-OES-008"} <= gate_ids, response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["owner_decision_evidence_status_contract_active"] is True, response
assert payload["summary"]["review_status_available"] is True, response
assert payload["summary"]["persisted_submission_count"] == 0, response
assert payload["summary"]["pending_review_count"] == 0, response
assert "getHardwareInterfaceOwnerDecisionEvidenceStatusJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-status" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.status" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceStatus" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_RETENTION_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-retention-checklist)"
python3 - "$HARDWARE_OWNER_EVIDENCE_RETENTION_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["retention_closure_checklist_state"] == "contract-only-retention-closure-checklist-open", response
assert payload["storage_activation_allowed"] is False, response
assert payload["gate_closure_allowed"] is False, response
assert payload["owner_decision_complete"] is False, response
assert payload["evidence_uri_rules"]["uri_rules_confirmed"] is False, response
assert payload["retention_policy_shape"]["retention_policy_confirmed"] is False, response
assert {"HW-OER-001", "HW-OER-002", "HW-OER-003", "HW-OER-004", "HW-OER-005", "HW-OER-006", "HW-OER-007", "HW-OER-008"} <= gate_ids, response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["owner_decision_evidence_retention_checklist_active"] is True, response
assert payload["summary"]["owner_decision_evidence_status_contract_active"] is True, response
assert payload["summary"]["owner_decision_evidence_contract_active"] is True, response
assert payload["summary"]["persisted_submission_count"] == 0, response
assert payload["summary"]["pending_review_count"] == 0, response
assert "getHardwareInterfaceOwnerDecisionEvidenceRetentionChecklistJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-retention-checklist" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.retention.checklist" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceRetentionChecklist" in encoded, response
assert "HW-OER-006" in encoded and "delete-export-semantics" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
VEHICLE_SIGNALS_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" vehicle-signals)"
python3 - "$VEHICLE_SIGNALS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
signal_paths = {item["path"] for item in payload["signals"]}
assert response["status"] == "ok", response
assert "Vehicle.Speed" in signal_paths, response
assert "Vehicle.Cabin.HVAC.Station.Row1.Left.Temperature" in signal_paths, response
assert "Vehicle.Body.Door.Row1.Left.IsOpen" in signal_paths, response
assert payload["summary"]["catalog_state"] == "read-only-mock-signal-catalog", response
assert payload["summary"]["dbc_arxml_loaded"] is False, response
assert payload["summary"]["real_vehicle_bus_connected"] is False, response
assert payload["summary"]["hardware_accessed"] is False, response
assert payload["summary"]["driver_development_triggered"] is False, response
assert payload["summary"]["virtualization_development_triggered"] is False, response
assert payload["summary"]["service_dispatch_triggered"] is False, response
assert "vehicle.signals.list" in encoded and "GetVehicleSignals" in encoded, response
assert "DRV-GAP-002" in encoded, response
PY
VEHICLE_SIGNAL_ACTIVATION_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" vehicle-signal-activation)"
python3 - "$VEHICLE_SIGNAL_ACTIVATION_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
option_types = {item["source_type"] for item in payload["activation_options"]}
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["activation_state"] == "criteria-only-not-activated", response
assert payload["read_bridge_activated"] is False, response
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
    assert payload["summary"][key] is False, response
assert "getVehicleSignalActivationJson" in encoded, response
assert "vehicle.signals.activation.get" in encoded, response
assert "GetVehicleSignalActivation" in encoded, response
assert "DRV-GAP-002" in encoded, response
PY
VEHICLE_SIGNAL_VALIDATION_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" vehicle-signal-validation)"
python3 - "$VEHICLE_SIGNAL_VALIDATION_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["validation_state"] == "metadata-only-not-activated", response
assert payload["read_bridge_activated"] is False, response
assert {"VS-VAL-001", "VS-VAL-002", "VS-VAL-003", "VS-VAL-004", "VS-VAL-005", "VS-VAL-006"} <= gate_ids, response
assert {"schema_source_metadata", "adapter_ownership", "android_linux_parity", "driver_gap_002", "write_path_guard"} <= set(payload["validation_envelope"]), response
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
    assert payload["summary"][key] is False, response
assert "getVehicleSignalValidationJson" in encoded, response
assert "vehicle.signals.validation.get" in encoded, response
assert "GetVehicleSignalValidation" in encoded, response
assert "DRV-GAP-002" in encoded, response
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
assert "uib.events.subscriptions.get" in encoded, response
assert "uib.events.subscriptions.decision.matrix" in encoded, response
assert "uib.events.subscriptions.callback.watch.shape" in encoded, response
assert "uib.events.subscriptions.readiness.rollup" in encoded, response
assert "governance.precheck" in encoded, response
assert "governance.backend.contract.get" in encoded, response
assert "hardware.interfaces.activation.checklist" in encoded, response
assert "hardware.interfaces.owner.decision.status" in encoded, response
assert "vehicle.signals.list" in encoded, response
assert "vehicle.signals.activation.get" in encoded, response
assert "vehicle.signals.validation.get" in encoded, response
assert "XSC-006" in encoded and "NV-P-002" in encoded and "DEL-002" in encoded, response
PY

echo "Central Brain Linux IPC smoke test passed on $SOCKET_PATH"
