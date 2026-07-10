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
EVENT_SUBSCRIPTION_ACTIVATION_EVIDENCE_DECISION_STATUS_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-evidence-decision-status-rollup)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_EVIDENCE_DECISION_STATUS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["decision_status_rollup_state"] == "contract-only-decision-status-blocked", response
assert payload["decision_status_consistent"] is True, response
assert payload["decision_status_passed"] is False, response
assert payload["source_surfaces"]["activation_evidence_intake"]["called_by_decision_status_rollup"] is False, response
assert {"EV-AED-001", "EV-AED-002", "EV-AED-003", "EV-AED-004", "EV-AED-005", "EV-AED-006", "EV-AED-007", "EV-AED-008"} <= gate_ids, response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["activation_evidence_decision_status_rollup_active"] is True, response
assert payload["summary"]["decision_status_consistent"] is True, response
assert payload["summary"]["persisted_submission_count"] == 0, response
assert payload["summary"]["pending_review_count"] == 0, response
assert "getEventSubscriptionActivationEvidenceDecisionStatusRollupJson" in encoded, response
assert "event-subscription-activation-evidence-decision-status-rollup" in encoded, response
assert "uib.events.subscriptions.activation.evidence.decision.status.rollup" in encoded, response
assert "GetEventSubscriptionActivationEvidenceDecisionStatusRollup" in encoded, response
assert "EV-AED-006" in encoded and "activation-approval-policy" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DRY_RUN_STATUS_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-dry-run-status)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DRY_RUN_STATUS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["approval_dry_run_status_state"] == "contract-only-approval-dry-run-no-store-status", response
assert payload["approval_dry_run_status_active"] is True, response
assert payload["source_decision_status_rollup"]["active"] is True, response
assert payload["source_decision_status_rollup"]["decision_status_passed"] is False, response
assert {"EV-AAS-001", "EV-AAS-002", "EV-AAS-003", "EV-AAS-004", "EV-AAS-005", "EV-AAS-006", "EV-AAS-007", "EV-AAS-008"} <= gate_ids, response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["activation_approval_dry_run_status_active"] is True, response
assert payload["summary"]["source_decision_status_rollup_bound"] is True, response
assert payload["summary"]["decision_status_consistent"] is True, response
assert payload["summary"]["persisted_dry_run_count"] == 0, response
assert payload["summary"]["pending_approval_count"] == 0, response
assert payload["summary"]["approved_gate_count"] == 0, response
assert "getEventSubscriptionActivationApprovalDryRunStatusJson" in encoded, response
assert "event-subscription-activation-approval-dry-run-status" in encoded, response
assert "uib.events.subscriptions.activation.approval.dry.run.status" in encoded, response
assert "GetEventSubscriptionActivationApprovalDryRunStatus" in encoded, response
assert "EV-AAS-004" in encoded and "approval-authority" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_AUTHORITY_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-authority-checklist)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_AUTHORITY_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["approval_authority_checklist_state"] == "contract-only-approval-authority-blocked", response
assert payload["approval_authority_checklist_active"] is True, response
assert payload["source_approval_dry_run_status"]["active"] is True, response
assert payload["source_approval_dry_run_status"]["approval_dry_run_invoked"] is False, response
assert {"EV-AAA-001", "EV-AAA-002", "EV-AAA-003", "EV-AAA-004", "EV-AAA-005", "EV-AAA-006", "EV-AAA-007", "EV-AAA-008"} <= gate_ids, response
assert payload["summary"]["activation_approval_authority_checklist_active"] is True, response
assert payload["summary"]["source_approval_dry_run_status_bound"] is True, response
assert payload["summary"]["approval_authority_checklist_complete"] is True, response
assert payload["summary"]["required_authority_item_count"] == payload["summary"]["unresolved_authority_item_count"], response
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
    "gates_closed",
    "activation_allowed",
    "broker_active",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
assert "getEventSubscriptionActivationApprovalAuthorityChecklistJson" in encoded, response
assert "event-subscription-activation-approval-authority-checklist" in encoded, response
assert "uib.events.subscriptions.activation.approval.authority.checklist" in encoded, response
assert "GetEventSubscriptionActivationApprovalAuthorityChecklist" in encoded, response
assert "EV-AAA-004" in encoded and "signature-rbac" in encoded, response
assert "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_AUTHORITY_AUDIT_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-authority-audit-consistency)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_AUTHORITY_AUDIT_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
finding_ids = {item["finding_id"] for item in payload["audit_findings"]}
assert response["status"] == "ok", response
assert payload["approval_authority_audit_state"] == "contract-only-approval-authority-audit-consistent", response
assert payload["approval_authority_audit_consistency_active"] is True, response
assert {"EV-AAC-001", "EV-AAC-002", "EV-AAC-003", "EV-AAC-004", "EV-AAC-005", "EV-AAC-006", "EV-AAC-007", "EV-AAC-008"} <= gate_ids, response
assert {"EV-AAC-AUD-001", "EV-AAC-AUD-002", "EV-AAC-AUD-003", "EV-AAC-AUD-004", "EV-AAC-AUD-005", "EV-AAC-AUD-006", "EV-AAC-AUD-007", "EV-AAC-AUD-008"} <= finding_ids, response
assert all(item["consistent"] is True for item in payload["audit_findings"]), response
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
    assert payload["summary"][key] is True, response
for key in [
    "approval_authority_ready",
    "approval_dry_run_invoked",
    "approval_result_store_created",
    "review_queue_updated",
    "gates_closed",
    "activation_allowed",
    "broker_active",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
assert payload["summary"]["required_authority_item_count"] == payload["summary"]["unresolved_authority_item_count"], response
assert payload["summary"]["persisted_dry_run_count"] == 0, response
assert "getEventSubscriptionActivationApprovalAuthorityAuditConsistencyJson" in encoded, response
assert "event-subscription-activation-approval-authority-audit-consistency" in encoded, response
assert "uib.events.subscriptions.activation.approval.authority.audit.consistency" in encoded, response
assert "GetEventSubscriptionActivationApprovalAuthorityAuditConsistency" in encoded, response
assert "EV-AAC-006" in encoded and "no-store" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_BLOCKER_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-decision-blocker-rollup)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_BLOCKER_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
blocker_ids = {item["blocker_id"] for item in payload["decision_blockers"]}
assert response["status"] == "ok", response
assert payload["approval_decision_blocker_rollup_state"] == "contract-only-approval-decision-blocked", response
assert payload["approval_decision_blocker_rollup_active"] is True, response
assert {"EV-ADB-001", "EV-ADB-002", "EV-ADB-003", "EV-ADB-004", "EV-ADB-005", "EV-ADB-006", "EV-ADB-007", "EV-ADB-008"} <= gate_ids, response
assert {"EV-ADB-001", "EV-ADB-002", "EV-ADB-003", "EV-ADB-004", "EV-ADB-005", "EV-ADB-006", "EV-ADB-007", "EV-ADB-008"} <= blocker_ids, response
assert payload["summary"]["activation_approval_decision_blocker_rollup_active"] is True, response
assert payload["summary"]["source_authority_audit_consistency_passed"] is True, response
assert payload["summary"]["decision_blocker_rollup_complete"] is True, response
assert payload["summary"]["required_blocker_count"] == 8, response
assert payload["summary"]["open_blocker_count"] == 7, response
for key in [
    "approval_decision_ready",
    "approval_dry_run_allowed",
    "approval_command_surface_ready",
    "approval_authority_ready",
    "approval_result_store_created",
    "review_queue_updated",
    "gates_closed",
    "broker_activation_allowed",
    "activation_allowed",
    "broker_active",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
assert "getEventSubscriptionActivationApprovalDecisionBlockerRollupJson" in encoded, response
assert "event-subscription-activation-approval-decision-blocker-rollup" in encoded, response
assert "uib.events.subscriptions.activation.approval.decision.blocker.rollup" in encoded, response
assert "GetEventSubscriptionActivationApprovalDecisionBlockerRollup" in encoded, response
assert "EV-ADB-007" in encoded and "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_DRY_RUN_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-decision-dry-run)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_DRY_RUN_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["approval_decision_dry_run_state"] == "rejected_blocked_contract_only", response
assert payload["approval_decision_dry_run_active"] is True, response
assert payload["approval_decision_dry_run_validated"] is True, response
assert {"EV-ADD-001", "EV-ADD-002", "EV-ADD-003", "EV-ADD-004", "EV-ADD-005", "EV-ADD-006", "EV-ADD-007", "EV-ADD-008"} <= gate_ids, response
assert payload["source_decision_blocker_rollup"]["open_blocker_count"] > 0, response
assert payload["request_validation"]["request_shape_valid"] is True, response
assert payload["request_validation"]["policy_allowed"] is True, response
assert payload["request_validation"]["source_decision_blocker_rollup_bound"] is True, response
assert payload["request_validation"]["rejected_by_open_blockers"] is True, response
assert payload["summary"]["activation_approval_decision_dry_run_active"] is True, response
assert payload["summary"]["rejected_blocked_contract_only"] is True, response
assert payload["summary"]["approval_command_surface_ready"] is True, response
for key in [
    "approval_decision_ready",
    "approval_dry_run_allowed",
    "approval_result_store_created",
    "dry_run_request_persisted",
    "dry_run_result_persisted",
    "approval_decision_persisted",
    "review_queue_updated",
    "gates_closed",
    "broker_activation_allowed",
    "activation_allowed",
    "broker_active",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
assert "dryRunEventSubscriptionActivationApprovalDecisionJson" in encoded, response
assert "event-subscription-activation-approval-decision-dry-run" in encoded, response
assert "uib.events.subscriptions.activation.approval.decision.dry.run" in encoded, response
assert "DryRunEventSubscriptionActivationApprovalDecision" in encoded, response
assert "EV-ADB-001" in encoded and "EV-ADD-004" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_DRY_RUN_STATUS_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-decision-dry-run-status)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_DRY_RUN_STATUS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "event-subscription-activation-approval-decision-dry-run-status", response
assert payload["approval_decision_dry_run_status_state"] == "contract-only-approval-decision-dry-run-status-no-store", response
assert payload["approval_decision_dry_run_status_active"] is True, response
assert payload["source_decision_dry_run_contract"]["contract_surface_active"] is True, response
assert payload["source_decision_dry_run_contract"]["status_invokes_post"] is False, response
assert payload["source_decision_blocker_rollup"]["active"] is True, response
assert payload["source_decision_blocker_rollup"]["open_blocker_count"] > 0, response
assert {"EV-ADS-001", "EV-ADS-002", "EV-ADS-003", "EV-ADS-004", "EV-ADS-005", "EV-ADS-006", "EV-ADS-007", "EV-ADS-008"} <= gate_ids, response
assert payload["last_result_status"]["last_approval_decision_result_available"] is False, response
assert payload["last_result_status"]["persisted_dry_run_request_count"] == 0, response
assert payload["last_result_status"]["persisted_dry_run_result_count"] == 0, response
assert payload["last_result_status"]["persisted_approval_decision_count"] == 0, response
assert payload["last_result_status"]["approval_result_store_created"] is False, response
assert payload["last_result_status"]["review_queue_updated"] is False, response
assert payload["last_result_status"]["gates_closed"] is False, response
assert payload["summary"]["activation_approval_decision_dry_run_status_active"] is True, response
assert payload["summary"]["source_decision_dry_run_contract_bound"] is True, response
assert payload["summary"]["source_decision_blocker_rollup_bound"] is True, response
assert payload["summary"]["decision_dry_run_post_called_by_status"] is False, response
assert payload["summary"]["persisted_dry_run_request_count"] == 0, response
assert payload["summary"]["persisted_dry_run_result_count"] == 0, response
assert payload["summary"]["persisted_approval_decision_count"] == 0, response
for key in [
    "approval_decision_status_passed",
    "approval_decision_ready",
    "approval_dry_run_allowed",
    "approval_result_store_created",
    "dry_run_request_persisted",
    "dry_run_result_persisted",
    "approval_decision_persisted",
    "review_queue_updated",
    "gates_closed",
    "broker_activation_allowed",
    "activation_allowed",
    "broker_active",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
assert "getEventSubscriptionActivationApprovalDecisionDryRunStatusJson" in encoded, response
assert "event-subscription-activation-approval-decision-dry-run-status" in encoded, response
assert "uib.events.subscriptions.activation.approval.decision.dry.run.status" in encoded, response
assert "GetEventSubscriptionActivationApprovalDecisionDryRunStatus" in encoded, response
assert "EV-ADB-001" in encoded and "EV-ADS-005" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_DRY_RUN_AUDIT_CONSISTENCY_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-decision-dry-run-audit-consistency)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_DRY_RUN_AUDIT_CONSISTENCY_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "event-subscription-activation-approval-decision-dry-run-audit-consistency", response
assert payload["approval_decision_dry_run_audit_state"] == "contract-only-approval-decision-dry-run-audit-consistent", response
assert payload["approval_decision_dry_run_audit_consistency_active"] is True, response
assert {"EV-ADA-001", "EV-ADA-002", "EV-ADA-003", "EV-ADA-004", "EV-ADA-005", "EV-ADA-006", "EV-ADA-007", "EV-ADA-008"} <= gate_ids, response
assert payload["source_surfaces"]["decision_dry_run_contract"]["post_called_by_audit_consistency"] is False, response
assert payload["source_surfaces"]["decision_dry_run_status"]["post_called_by_status"] is False, response
assert payload["source_surfaces"]["decision_dry_run_status"]["persisted_dry_run_request_count"] == 0, response
assert payload["source_surfaces"]["decision_dry_run_status"]["persisted_dry_run_result_count"] == 0, response
assert payload["source_surfaces"]["decision_dry_run_status"]["persisted_approval_decision_count"] == 0, response
assert payload["source_surfaces"]["decision_blocker_rollup"]["open_blocker_count"] > 0, response
for key in [
    "approval_decision_ready",
    "approval_dry_run_allowed",
    "approval_result_store_created",
    "dry_run_request_persisted",
    "dry_run_result_persisted",
    "approval_decision_persisted",
    "review_queue_updated",
    "gates_closed",
    "broker_activation_allowed",
    "activation_allowed",
    "broker_active",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
for key in [
    "activation_approval_decision_dry_run_audit_consistency_active",
    "consistency_passed",
    "source_status_bound",
    "source_decision_dry_run_contract_bound",
    "source_decision_blocker_rollup_bound",
    "blocker_count_consistent",
    "no_store_consistent",
    "decision_dry_run_rejection_consistent",
    "android_linux_parity_consistent",
    "no_side_effects_consistent",
]:
    assert payload["summary"][key] is True, response
assert payload["summary"]["decision_dry_run_post_called_by_audit_consistency"] is False, response
assert payload["summary"]["persisted_dry_run_request_count"] == 0, response
assert payload["summary"]["persisted_dry_run_result_count"] == 0, response
assert payload["summary"]["persisted_approval_decision_count"] == 0, response
assert payload["summary"]["open_blocker_count"] > 0, response
assert "getEventSubscriptionActivationApprovalDecisionDryRunAuditConsistencyJson" in encoded, response
assert "event-subscription-activation-approval-decision-dry-run-audit-consistency" in encoded, response
assert "uib.events.subscriptions.activation.approval.decision.dry.run.audit.consistency" in encoded, response
assert "GetEventSubscriptionActivationApprovalDecisionDryRunAuditConsistency" in encoded, response
assert "EV-ADS-001" in encoded and "EV-ADA-005" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_CLOSURE_BLOCKER_MATRIX_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-decision-closure-blocker-matrix)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_CLOSURE_BLOCKER_MATRIX_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "event-subscription-activation-approval-decision-closure-blocker-matrix", response
assert payload["approval_decision_closure_blocker_matrix_state"] == "contract-only-approval-decision-closure-blocked", response
assert payload["approval_decision_closure_blocker_matrix_active"] is True, response
assert {"EV-ACB-001", "EV-ACB-002", "EV-ACB-003", "EV-ACB-004", "EV-ACB-005", "EV-ACB-006", "EV-ACB-007", "EV-ACB-008", "EV-ACB-009", "EV-ACB-010"} <= gate_ids, response
assert payload["source_surfaces"]["decision_dry_run_audit_consistency"]["post_called_by_audit_consistency"] is False, response
assert payload["source_surfaces"]["decision_blocker_rollup"]["open_blocker_count"] > 0, response
for key in [
    "closure_ready",
    "approval_decision_closure_allowed",
    "approval_decision_ready",
    "approval_dry_run_allowed",
    "approval_result_store_created",
    "review_queue_updated",
    "gates_closed",
    "broker_activation_allowed",
    "activation_allowed",
    "broker_active",
    "high_rate_data_plane_active",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
for key in [
    "activation_approval_decision_closure_blocker_matrix_active",
    "closure_blocker_matrix_complete",
    "source_decision_dry_run_audit_consistency_bound",
    "source_decision_dry_run_audit_consistency_passed",
    "source_decision_blocker_rollup_bound",
    "no_store_consistent",
    "no_side_effects_consistent",
]:
    assert payload["summary"][key] is True, response
assert payload["summary"]["decision_dry_run_post_called_by_closure_blocker_matrix"] is False, response
assert payload["summary"]["persisted_dry_run_request_count"] == 0, response
assert payload["summary"]["persisted_dry_run_result_count"] == 0, response
assert payload["summary"]["persisted_approval_decision_count"] == 0, response
assert payload["summary"]["open_closure_blocker_count"] == 10, response
assert "getEventSubscriptionActivationApprovalDecisionClosureBlockerMatrixJson" in encoded, response
assert "event-subscription-activation-approval-decision-closure-blocker-matrix" in encoded, response
assert "uib.events.subscriptions.activation.approval.decision.closure.blocker.matrix" in encoded, response
assert "GetEventSubscriptionActivationApprovalDecisionClosureBlockerMatrix" in encoded, response
assert "EV-ADA-001" in encoded and "EV-ACB-010" in encoded, response
assert "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_CHECKLIST_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-decision-owner-handoff-checklist)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_CHECKLIST_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "event-subscription-activation-approval-decision-owner-handoff-checklist", response
assert payload["approval_decision_owner_handoff_checklist_state"] == "contract-only-owner-handoff-blocked", response
assert payload["approval_decision_owner_handoff_checklist_active"] is True, response
assert {"EV-ACH-001", "EV-ACH-002", "EV-ACH-003", "EV-ACH-004", "EV-ACH-005", "EV-ACH-006", "EV-ACH-007", "EV-ACH-008", "EV-ACH-009", "EV-ACH-010"} <= gate_ids, response
source = payload["source_surfaces"]["closure_blocker_matrix"]
assert source["active"] is True, response
assert source["complete"] is True, response
assert source["closure_ready"] is False, response
assert source["open_closure_blocker_count"] == 10, response
assert "EV-ACB-010" in source["open_closure_blocker_ids"], response
assert len(payload["owner_handoffs"]) == 10, response
for item in payload["owner_handoffs"]:
    assert item["handoff_state"] == "owner-unassigned", response
    assert item["escalation_state"] == "blocked-waiting-owner-assignment", response
    assert item["android_linux_parity_required"] is True, response
    for key in [
        "handoff_ready",
        "owner_assigned",
        "evidence_attached",
        "assignment_persisted",
        "review_queue_updated",
    ]:
        assert item[key] is False, response
    assert item["open"] is True, response
for key in [
    "activation_approval_decision_owner_handoff_checklist_active",
    "owner_handoff_checklist_complete",
    "source_closure_blocker_matrix_bound",
    "source_closure_blocker_matrix_complete",
    "no_store_consistent",
    "no_side_effects_consistent",
]:
    assert payload["summary"][key] is True, response
for key in [
    "source_closure_ready",
    "owner_handoff_ready",
    "owner_assignments_persisted",
    "owner_handoff_queue_updated",
    "decision_dry_run_post_called_by_owner_handoff_checklist",
    "closure_ready",
    "approval_decision_closure_allowed",
    "approval_decision_ready",
    "approval_dry_run_allowed",
    "approval_result_store_created",
    "approval_result_store_active",
    "review_queue_updated",
    "gate_state_changed",
    "gates_closed",
    "broker_activation_allowed",
    "activation_allowed",
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
assert payload["summary"]["owner_handoff_checklist_state"] == "contract-only-owner-handoff-blocked", response
assert payload["summary"]["source_open_closure_blocker_count"] == 10, response
assert payload["summary"]["required_owner_handoff_count"] == 10, response
assert payload["summary"]["open_owner_handoff_count"] == 10, response
assert payload["summary"]["assigned_owner_count"] == 0, response
assert payload["summary"]["unassigned_owner_count"] == 10, response
assert payload["summary"]["attached_evidence_count"] == 0, response
assert payload["summary"]["persisted_dry_run_request_count"] == 0, response
assert payload["summary"]["persisted_dry_run_result_count"] == 0, response
assert payload["summary"]["persisted_approval_decision_count"] == 0, response
assert payload["summary"]["pending_approval_decision_review_count"] == 0, response
assert "getEventSubscriptionActivationApprovalDecisionOwnerHandoffChecklistJson" in encoded, response
assert "event-subscription-activation-approval-decision-owner-handoff-checklist" in encoded, response
assert "uib.events.subscriptions.activation.approval.decision.owner.handoff.checklist" in encoded, response
assert "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffChecklist" in encoded, response
assert "EV-ACB-001" in encoded and "EV-ACH-010" in encoded, response
assert "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_AUDIT_CONSISTENCY_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-decision-owner-handoff-audit-consistency)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_AUDIT_CONSISTENCY_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "event-subscription-activation-approval-decision-owner-handoff-audit-consistency", response
assert payload["approval_decision_owner_handoff_audit_consistency_state"] == "contract-only-owner-handoff-audit-consistent", response
assert payload["approval_decision_owner_handoff_audit_consistency_active"] is True, response
assert {"EV-AHA-001", "EV-AHA-002", "EV-AHA-003", "EV-AHA-004", "EV-AHA-005", "EV-AHA-006", "EV-AHA-007", "EV-AHA-008", "EV-AHA-009", "EV-AHA-010"} <= gate_ids, response
owner_source = payload["source_surfaces"]["owner_handoff_checklist"]
closure_source = payload["source_surfaces"]["closure_blocker_matrix"]
assert owner_source["active"] is True and owner_source["complete"] is True, response
assert owner_source["owner_handoff_ready"] is False, response
assert owner_source["required_owner_handoff_count"] == 10 and owner_source["open_owner_handoff_count"] == 10, response
assert "EV-ACH-010" in owner_source["open_owner_handoff_ids"], response
assert closure_source["active"] is True and closure_source["complete"] is True, response
assert closure_source["closure_ready"] is False, response
assert closure_source["open_closure_blocker_count"] == 10, response
assert "EV-ACB-010" in closure_source["open_closure_blocker_ids"], response
for key in [
    "activation_approval_decision_owner_handoff_audit_consistency_active",
    "consistency_passed",
    "source_owner_handoff_checklist_bound",
    "source_closure_blocker_matrix_bound",
    "owner_handoff_count_consistent",
    "source_closure_blocker_binding_consistent",
    "open_handoff_state_consistent",
    "owner_assignment_absent_consistent",
    "evidence_attachment_absent_consistent",
    "no_store_consistent",
    "no_review_queue_gate_runtime_consistent",
    "android_linux_parity_consistent",
    "no_post_consistent",
    "no_side_effects_consistent",
]:
    assert payload["summary"][key] is True, response
for key in [
    "owner_handoff_ready",
    "owner_assignments_persisted",
    "owner_handoff_queue_updated",
    "decision_dry_run_post_called_by_owner_handoff_audit_consistency",
    "closure_ready",
    "approval_decision_closure_allowed",
    "approval_decision_ready",
    "approval_dry_run_allowed",
    "approval_result_store_created",
    "approval_result_store_active",
    "review_queue_updated",
    "gate_state_changed",
    "gates_closed",
    "broker_activation_allowed",
    "activation_allowed",
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
assert payload["summary"]["owner_handoff_audit_consistency_state"] == "contract-only-owner-handoff-audit-consistent", response
assert payload["summary"]["source_open_closure_blocker_count"] == 10, response
assert payload["summary"]["required_owner_handoff_count"] == 10, response
assert payload["summary"]["open_owner_handoff_count"] == 10, response
assert payload["summary"]["assigned_owner_count"] == 0, response
assert payload["summary"]["unassigned_owner_count"] == 10, response
assert payload["summary"]["attached_evidence_count"] == 0, response
assert payload["summary"]["persisted_dry_run_request_count"] == 0, response
assert payload["summary"]["persisted_dry_run_result_count"] == 0, response
assert payload["summary"]["persisted_approval_decision_count"] == 0, response
assert payload["summary"]["pending_approval_decision_review_count"] == 0, response
assert "getEventSubscriptionActivationApprovalDecisionOwnerHandoffAuditConsistencyJson" in encoded, response
assert "event-subscription-activation-approval-decision-owner-handoff-audit-consistency" in encoded, response
assert "uib.events.subscriptions.activation.approval.decision.owner.handoff.audit.consistency" in encoded, response
assert "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffAuditConsistency" in encoded, response
assert "EV-ACH-001" in encoded and "EV-AHA-010" in encoded and "EV-ACB-010" in encoded, response
assert "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_DECISION_ROLLUP_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-decision-owner-handoff-decision-rollup)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_DECISION_ROLLUP_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "event-subscription-activation-approval-decision-owner-handoff-decision-rollup", response
assert payload["approval_decision_owner_handoff_decision_rollup_state"] == "contract-only-owner-handoff-decision-blocked", response
assert payload["approval_decision_owner_handoff_decision_rollup_active"] is True, response
assert {"EV-AHD-001", "EV-AHD-002", "EV-AHD-003", "EV-AHD-004", "EV-AHD-005", "EV-AHD-006", "EV-AHD-007", "EV-AHD-008", "EV-AHD-009", "EV-AHD-010"} <= gate_ids, response
audit_source = payload["source_surfaces"]["owner_handoff_audit_consistency"]
checklist_source = payload["source_surfaces"]["owner_handoff_checklist"]
assert audit_source["active"] is True and audit_source["consistent"] is True, response
assert audit_source["owner_handoff_ready"] is False, response
assert checklist_source["active"] is True and checklist_source["complete"] is True, response
assert checklist_source["owner_handoff_ready"] is False, response
assert "EV-ACH-010" in audit_source["open_owner_handoff_ids"], response
assert "EV-ACH-010" in checklist_source["open_owner_handoff_ids"], response
assert len(payload["decision_items"]) == 10, response
for item in payload["decision_items"]:
    assert item["decision_state"] == "blocked", response
    assert item["decision_blocked"] is True, response
    assert item["open"] is True, response
    assert item["owner_assigned"] is False, response
    assert item["evidence_attached"] is False, response
    assert item["review_queue_updated"] is False, response
for key in [
    "activation_approval_decision_owner_handoff_decision_rollup_active",
    "decision_rollup_complete",
    "decision_rollup_consistent",
    "source_owner_handoff_audit_bound",
    "source_owner_handoff_checklist_bound",
    "owner_handoff_audit_consistent",
    "owner_handoff_checklist_complete",
    "owner_handoff_decision_blocked",
    "no_store_consistent",
    "no_post_consistent",
    "no_side_effects_consistent",
]:
    assert payload["summary"][key] is True, response
for key in [
    "owner_handoff_decision_ready",
    "approval_decision_ready",
    "approval_dry_run_allowed",
    "handoff_evidence_ready",
    "owner_assignments_persisted",
    "owner_handoff_queue_updated",
    "decision_dry_run_post_called_by_owner_handoff_decision_rollup",
    "approval_result_store_created",
    "approval_result_store_active",
    "review_queue_updated",
    "gate_state_changed",
    "gates_closed",
    "broker_activation_allowed",
    "activation_allowed",
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
assert payload["summary"]["owner_handoff_decision_rollup_state"] == "contract-only-owner-handoff-decision-blocked", response
assert payload["summary"]["decision"] == "blocked-by-unassigned-owners-and-missing-evidence", response
assert payload["summary"]["required_decision_count"] == 10, response
assert payload["summary"]["blocked_decision_count"] == 10, response
assert payload["summary"]["required_owner_handoff_count"] == 10, response
assert payload["summary"]["open_owner_handoff_count"] == 10, response
assert payload["summary"]["assigned_owner_count"] == 0, response
assert payload["summary"]["unassigned_owner_count"] == 10, response
assert payload["summary"]["attached_evidence_count"] == 0, response
assert payload["summary"]["persisted_dry_run_request_count"] == 0, response
assert payload["summary"]["persisted_dry_run_result_count"] == 0, response
assert payload["summary"]["persisted_approval_decision_count"] == 0, response
assert payload["summary"]["pending_approval_decision_review_count"] == 0, response
assert "getEventSubscriptionActivationApprovalDecisionOwnerHandoffDecisionRollupJson" in encoded, response
assert "event-subscription-activation-approval-decision-owner-handoff-decision-rollup" in encoded, response
assert "uib.events.subscriptions.activation.approval.decision.owner.handoff.decision.rollup" in encoded, response
assert "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffDecisionRollup" in encoded, response
assert "EV-ACH-001" in encoded and "EV-AHA-010" in encoded and "EV-AHD-010" in encoded and "EV-ACB-010" in encoded, response
assert "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_READINESS_MATRIX_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-decision-owner-handoff-evidence-readiness-matrix)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_READINESS_MATRIX_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "event-subscription-activation-approval-decision-owner-handoff-evidence-readiness-matrix", response
assert payload["approval_decision_owner_handoff_evidence_readiness_matrix_state"] == "contract-only-handoff-evidence-missing", response
assert payload["approval_decision_owner_handoff_evidence_readiness_matrix_active"] is True, response
assert {"EV-AHE-001", "EV-AHE-002", "EV-AHE-003", "EV-AHE-004", "EV-AHE-005", "EV-AHE-006", "EV-AHE-007", "EV-AHE-008", "EV-AHE-009", "EV-AHE-010"} <= gate_ids, response
source = payload["source_surfaces"]["owner_handoff_decision_rollup"]
assert source["active"] is True and source["complete"] is True and source["consistent"] is True, response
assert source["decision_blocked"] is True, response
assert "EV-AHD-010" in source["decision_gate_ids"], response
assert "EV-ACH-010" in source["open_owner_handoff_ids"], response
assert len(payload["evidence_packets"]) == 10, response
for packet in payload["evidence_packets"]:
    assert packet["packet_state"] == "missing", response
    assert packet["readiness_state"] == "blocked-missing-owner-and-evidence", response
    assert packet["open"] is True, response
    assert packet["owner_assigned"] is False, response
    assert packet["evidence_attached"] is False, response
    assert packet["evidence_uri_present"] is False, response
    assert packet["evidence_hash_present"] is False, response
    assert packet["owner_signature_present"] is False, response
    assert packet["evidence_persisted"] is False, response
    assert packet["review_queue_updated"] is False, response
    assert packet["source_decision_gate_id"].startswith("EV-AHD-"), response
    assert packet["source_handoff_id"].startswith("EV-ACH-"), response
    assert packet["source_blocker_id"].startswith("EV-ACB-"), response
for key in [
    "activation_approval_decision_owner_handoff_evidence_readiness_matrix_active",
    "readiness_matrix_complete",
    "readiness_matrix_consistent",
    "source_owner_handoff_decision_rollup_bound",
    "source_owner_handoff_decision_rollup_consistent",
    "source_owner_handoff_decision_blocked",
    "no_store_consistent",
    "no_post_consistent",
    "no_side_effects_consistent",
]:
    assert payload["summary"][key] is True, response
for key in [
    "handoff_evidence_ready",
    "approval_decision_ready",
    "approval_dry_run_allowed",
    "owner_assignments_persisted",
    "owner_handoff_queue_updated",
    "evidence_packets_attached",
    "evidence_store_created",
    "evidence_store_active",
    "decision_dry_run_post_called_by_handoff_evidence_readiness_matrix",
    "approval_result_store_created",
    "approval_result_store_active",
    "review_queue_updated",
    "gate_state_changed",
    "gates_closed",
    "broker_activation_allowed",
    "activation_allowed",
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
assert payload["summary"]["handoff_evidence_readiness_matrix_state"] == "contract-only-handoff-evidence-missing", response
assert payload["summary"]["decision"] == "blocked-by-missing-handoff-evidence-packets", response
assert payload["summary"]["required_evidence_packet_count"] == 10, response
assert payload["summary"]["missing_evidence_packet_count"] == 10, response
assert payload["summary"]["attached_evidence_count"] == 0, response
assert payload["summary"]["persisted_evidence_packet_count"] == 0, response
assert payload["summary"]["evidence_uri_count"] == 0, response
assert payload["summary"]["evidence_hash_count"] == 0, response
assert payload["summary"]["owner_signature_count"] == 0, response
assert payload["summary"]["required_owner_handoff_count"] == 10, response
assert payload["summary"]["open_owner_handoff_count"] == 10, response
assert payload["summary"]["assigned_owner_count"] == 0, response
assert payload["summary"]["unassigned_owner_count"] == 10, response
assert payload["summary"]["persisted_dry_run_request_count"] == 0, response
assert payload["summary"]["persisted_dry_run_result_count"] == 0, response
assert payload["summary"]["persisted_approval_decision_count"] == 0, response
assert payload["summary"]["pending_approval_decision_review_count"] == 0, response
assert "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessMatrixJson" in encoded, response
assert "event-subscription-activation-approval-decision-owner-handoff-evidence-readiness-matrix" in encoded, response
assert "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.readiness.matrix" in encoded, response
assert "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessMatrix" in encoded, response
assert "EV-AHE-001" in encoded and "EV-AHE-010" in encoded and "EV-AHD-010" in encoded and "EV-ACH-010" in encoded and "EV-ACB-010" in encoded, response
assert "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_READINESS_AUDIT_CONSISTENCY_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-decision-owner-handoff-evidence-readiness-audit-consistency)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_READINESS_AUDIT_CONSISTENCY_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "event-subscription-activation-approval-decision-owner-handoff-evidence-readiness-audit-consistency", response
assert payload["approval_decision_owner_handoff_evidence_readiness_audit_consistency_state"] == "contract-only-handoff-evidence-audit-consistent", response
assert payload["approval_decision_owner_handoff_evidence_readiness_audit_consistency_active"] is True, response
assert {"EV-AHF-001", "EV-AHF-002", "EV-AHF-003", "EV-AHF-004", "EV-AHF-005", "EV-AHF-006", "EV-AHF-007", "EV-AHF-008", "EV-AHF-009", "EV-AHF-010"} <= gate_ids, response
matrix_source = payload["source_surfaces"]["handoff_evidence_readiness_matrix"]
rollup_source = payload["source_surfaces"]["owner_handoff_decision_rollup"]
assert matrix_source["active"] is True and matrix_source["complete"] is True and matrix_source["consistent"] is True, response
assert matrix_source["handoff_evidence_ready"] is False, response
assert rollup_source["active"] is True and rollup_source["complete"] is True and rollup_source["consistent"] is True, response
assert rollup_source["decision_blocked"] is True, response
assert len(payload["audit_items"]) == 10, response
for item in payload["audit_items"]:
    assert item["result"] == "consistent", response
    assert item["passed"] is True, response
    assert item["readiness_still_blocked"] is True, response
    assert item["source_evidence_id"].startswith("EV-AHE-"), response
    assert item["source_decision_gate_id"].startswith("EV-AHD-"), response
    assert item["source_handoff_id"].startswith("EV-ACH-"), response
    assert item["source_blocker_id"].startswith("EV-ACB-"), response
for key in [
    "activation_approval_decision_owner_handoff_evidence_readiness_audit_consistency_active",
    "consistency_passed",
    "source_handoff_evidence_readiness_matrix_bound",
    "source_owner_handoff_decision_rollup_bound",
    "source_handoff_evidence_readiness_matrix_consistent",
    "source_owner_handoff_decision_rollup_consistent",
    "packet_count_consistent",
    "packet_state_consistent",
    "source_binding_consistent",
    "android_linux_parity_consistent",
    "no_store_consistent",
    "no_post_consistent",
    "no_side_effects_consistent",
]:
    assert payload["summary"][key] is True, response
for key in [
    "handoff_evidence_ready",
    "approval_decision_ready",
    "approval_dry_run_allowed",
    "owner_assignments_persisted",
    "owner_handoff_queue_updated",
    "evidence_packets_attached",
    "evidence_store_created",
    "evidence_store_active",
    "decision_dry_run_post_called_by_handoff_evidence_readiness_audit_consistency",
    "approval_result_store_created",
    "approval_result_store_active",
    "review_queue_updated",
    "gate_state_changed",
    "gates_closed",
    "broker_activation_allowed",
    "activation_allowed",
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
assert payload["summary"]["handoff_evidence_readiness_audit_consistency_state"] == "contract-only-handoff-evidence-audit-consistent", response
assert payload["summary"]["decision"] == "blocked-by-missing-handoff-evidence-packets", response
assert payload["summary"]["required_evidence_packet_count"] == 10, response
assert payload["summary"]["missing_evidence_packet_count"] == 10, response
assert payload["summary"]["attached_evidence_count"] == 0, response
assert payload["summary"]["persisted_evidence_packet_count"] == 0, response
assert payload["summary"]["evidence_uri_count"] == 0, response
assert payload["summary"]["evidence_hash_count"] == 0, response
assert payload["summary"]["owner_signature_count"] == 0, response
assert payload["summary"]["required_owner_handoff_count"] == 10, response
assert payload["summary"]["open_owner_handoff_count"] == 10, response
assert payload["summary"]["assigned_owner_count"] == 0, response
assert payload["summary"]["unassigned_owner_count"] == 10, response
assert payload["summary"]["persisted_dry_run_request_count"] == 0, response
assert payload["summary"]["persisted_dry_run_result_count"] == 0, response
assert payload["summary"]["persisted_approval_decision_count"] == 0, response
assert payload["summary"]["pending_approval_decision_review_count"] == 0, response
assert "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessAuditConsistencyJson" in encoded, response
assert "event-subscription-activation-approval-decision-owner-handoff-evidence-readiness-audit-consistency" in encoded, response
assert "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.readiness.audit.consistency" in encoded, response
assert "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessAuditConsistency" in encoded, response
assert "EV-AHF-001" in encoded and "EV-AHF-010" in encoded and "EV-AHE-010" in encoded and "EV-AHD-010" in encoded and "EV-ACH-010" in encoded and "EV-ACB-010" in encoded, response
assert "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_STATUS_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-status)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_STATUS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-status", response
assert payload["approval_decision_owner_handoff_evidence_acceptance_status_state"] == "contract-only-handoff-evidence-acceptance-blocked", response
assert payload["approval_decision_owner_handoff_evidence_acceptance_status_active"] is True, response
assert {"EV-AHG-001", "EV-AHG-002", "EV-AHG-003", "EV-AHG-004", "EV-AHG-005", "EV-AHG-006", "EV-AHG-007", "EV-AHG-008", "EV-AHG-009", "EV-AHG-010"} <= gate_ids, response
assert len(payload["acceptance_items"]) == 10, response
for item in payload["acceptance_items"]:
    assert item["acceptance_state"] == "blocked-missing-evidence-packet", response
    assert item["acceptance_allowed"] is False, response
    assert item["accepted"] is False, response
    assert item["acceptance_record_persisted"] is False, response
    assert item["evidence_attached"] is False, response
    assert item["evidence_persisted"] is False, response
    assert item["review_queue_updated"] is False, response
    assert item["source_audit_gate_id"].startswith("EV-AHF-"), response
    assert item["source_evidence_id"].startswith("EV-AHE-"), response
    assert item["source_decision_gate_id"].startswith("EV-AHD-"), response
for key in [
    "activation_approval_decision_owner_handoff_evidence_acceptance_status_active",
    "acceptance_status_complete",
    "acceptance_status_consistent",
    "source_handoff_evidence_readiness_audit_bound",
    "source_handoff_evidence_readiness_matrix_bound",
    "source_handoff_evidence_readiness_audit_consistent",
    "no_store_consistent",
    "no_post_consistent",
    "no_side_effects_consistent",
]:
    assert payload["summary"][key] is True, response
for key in [
    "handoff_evidence_acceptance_allowed",
    "handoff_evidence_ready",
    "approval_decision_ready",
    "approval_dry_run_allowed",
    "evidence_packets_attached",
    "evidence_store_created",
    "evidence_store_active",
    "decision_dry_run_post_called_by_handoff_evidence_acceptance_status",
    "approval_result_store_created",
    "review_queue_updated",
    "gates_closed",
    "broker_activation_allowed",
    "activation_allowed",
    "broker_active",
    "high_rate_data_plane_active",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
assert payload["summary"]["handoff_evidence_acceptance_status_state"] == "contract-only-handoff-evidence-acceptance-blocked", response
assert payload["summary"]["required_acceptance_count"] == 10, response
assert payload["summary"]["blocked_acceptance_count"] == 10, response
assert payload["summary"]["accepted_evidence_packet_count"] == 0, response
assert payload["summary"]["acceptance_record_persisted_count"] == 0, response
assert payload["summary"]["missing_evidence_packet_count"] == 10, response
assert payload["summary"]["attached_evidence_count"] == 0, response
assert payload["summary"]["persisted_evidence_packet_count"] == 0, response
assert "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceStatusJson" in encoded, response
assert "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-status" in encoded, response
assert "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.status" in encoded, response
assert "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceStatus" in encoded, response
assert "EV-AHG-001" in encoded and "EV-AHG-010" in encoded and "EV-AHF-010" in encoded and "EV-AHE-010" in encoded and "EV-AHD-010" in encoded, response
assert "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_AUDIT_CONSISTENCY_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-audit-consistency)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_AUDIT_CONSISTENCY_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-audit-consistency", response
assert payload["approval_decision_owner_handoff_evidence_acceptance_audit_consistency_state"] == "contract-only-handoff-evidence-acceptance-audit-consistent", response
assert payload["approval_decision_owner_handoff_evidence_acceptance_audit_consistency_active"] is True, response
assert {"EV-AHH-001", "EV-AHH-002", "EV-AHH-003", "EV-AHH-004", "EV-AHH-005", "EV-AHH-006", "EV-AHH-007", "EV-AHH-008", "EV-AHH-009", "EV-AHH-010"} <= gate_ids, response
assert len(payload["audit_items"]) == 10, response
for item in payload["audit_items"]:
    assert item["result"] == "consistent", response
    assert item["passed"] is True, response
    assert item["acceptance_still_blocked"] is True, response
    assert item["source_acceptance_gate_id"].startswith("EV-AHG-"), response
    assert item["source_readiness_audit_gate_id"].startswith("EV-AHF-"), response
    assert item["source_evidence_id"].startswith("EV-AHE-"), response
    assert item["source_decision_gate_id"].startswith("EV-AHD-"), response
    assert item["source_handoff_id"].startswith("EV-ACH-"), response
for key in [
    "activation_approval_decision_owner_handoff_evidence_acceptance_audit_consistency_active",
    "consistency_passed",
    "source_acceptance_status_bound",
    "source_handoff_evidence_readiness_audit_bound",
    "acceptance_status_consistent",
    "handoff_evidence_readiness_audit_consistent",
    "acceptance_count_consistent",
    "blocked_acceptance_state_consistent",
    "android_linux_parity_consistent",
    "no_store_consistent",
    "no_post_consistent",
    "no_side_effects_consistent",
]:
    assert payload["summary"][key] is True, response
for key in [
    "handoff_evidence_acceptance_allowed",
    "handoff_evidence_ready",
    "approval_decision_ready",
    "approval_dry_run_allowed",
    "evidence_packets_attached",
    "evidence_store_created",
    "evidence_store_active",
    "decision_dry_run_post_called_by_handoff_evidence_acceptance_audit_consistency",
    "approval_result_store_created",
    "review_queue_updated",
    "gates_closed",
    "broker_activation_allowed",
    "activation_allowed",
    "broker_active",
    "high_rate_data_plane_active",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
assert payload["summary"]["required_audit_count"] == 10, response
assert payload["summary"]["blocked_acceptance_count"] == 10, response
assert payload["summary"]["accepted_evidence_packet_count"] == 0, response
assert payload["summary"]["acceptance_record_persisted_count"] == 0, response
assert payload["summary"]["missing_evidence_packet_count"] == 10, response
assert "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceAuditConsistencyJson" in encoded, response
assert "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-audit-consistency" in encoded, response
assert "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.audit.consistency" in encoded, response
assert "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceAuditConsistency" in encoded, response
assert "EV-AHH-001" in encoded and "EV-AHH-010" in encoded and "EV-AHG-010" in encoded and "EV-AHF-010" in encoded and "EV-AHE-010" in encoded and "EV-AHD-010" in encoded, response
assert "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_DECISION_ROLLUP_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-decision-rollup)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_DECISION_ROLLUP_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-decision-rollup", response
assert payload["approval_decision_owner_handoff_evidence_acceptance_decision_rollup_state"] == "contract-only-handoff-evidence-acceptance-decision-blocked", response
assert payload["approval_decision_owner_handoff_evidence_acceptance_decision_rollup_active"] is True, response
assert payload["decision_rollup_complete"] is True, response
assert payload["decision_rollup_consistent"] is True, response
assert payload["source_surfaces_bound"] is True, response
assert {"EV-AHI-001", "EV-AHI-002", "EV-AHI-003", "EV-AHI-004", "EV-AHI-005", "EV-AHI-006", "EV-AHI-007", "EV-AHI-008", "EV-AHI-009", "EV-AHI-010"} <= gate_ids, response
assert len(payload["decision_rows"]) == 10, response
for item in payload["decision_rows"]:
    assert item["decision_confirmed"] is False, response
    assert item["blocks_handoff_acceptance"] is True, response
    assert item["blocks_gate_closure"] is True, response
    assert item["blocks_broker_activation"] is True, response
    assert item["source_acceptance_gate_id"].startswith("EV-AHG-"), response
    assert item["source_audit_gate_id"].startswith("EV-AHH-"), response
for key in [
    "activation_approval_decision_owner_handoff_evidence_acceptance_decision_rollup_active",
    "activation_approval_decision_owner_handoff_evidence_acceptance_audit_consistency_active",
    "activation_approval_decision_owner_handoff_evidence_acceptance_status_active",
    "decision_rollup_complete",
    "decision_rollup_consistent",
    "source_surfaces_bound",
    "source_acceptance_audit_bound",
    "source_acceptance_status_bound",
    "source_handoff_evidence_readiness_matrix_bound",
    "no_store_consistent",
    "no_post_consistent",
    "no_side_effects_consistent",
]:
    assert payload["summary"][key] is True, response
for key in [
    "acceptance_decision_ready",
    "handoff_evidence_acceptance_allowed",
    "handoff_evidence_ready",
    "approval_decision_ready",
    "approval_dry_run_allowed",
    "approval_review_allowed",
    "gate_closure_allowed",
    "evidence_packets_attached",
    "review_queue_updated",
    "gates_closed",
    "broker_activation_allowed",
    "activation_allowed",
    "high_rate_data_plane_active",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
assert payload["summary"]["required_decision_count"] == 10, response
assert payload["summary"]["blocked_decision_count"] == 10, response
assert payload["summary"]["accepted_evidence_packet_count"] == 0, response
assert payload["summary"]["acceptance_record_persisted_count"] == 0, response
assert payload["summary"]["missing_evidence_packet_count"] == 10, response
assert "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceDecisionRollupJson" in encoded, response
assert "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-decision-rollup" in encoded, response
assert "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.decision.rollup" in encoded, response
assert "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceDecisionRollup" in encoded, response
assert "EV-AHI-001" in encoded and "EV-AHI-010" in encoded and "EV-AHH-010" in encoded and "EV-AHG-010" in encoded, response
assert "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_CHECKLIST_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-checklist)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_CHECKLIST_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-checklist", response
assert payload["approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist_state"] == "contract-only-handoff-evidence-acceptance-closure-not-ready", response
assert payload["approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist_active"] is True, response
assert payload["closure_readiness_checklist_complete"] is True, response
assert payload["closure_readiness_consistent"] is True, response
assert payload["source_surfaces_bound"] is True, response
assert payload["closure_ready"] is False, response
assert {"EV-AHJ-001", "EV-AHJ-002", "EV-AHJ-003", "EV-AHJ-004", "EV-AHJ-005", "EV-AHJ-006", "EV-AHJ-007", "EV-AHJ-008", "EV-AHJ-009", "EV-AHJ-010"} <= gate_ids, response
assert len(payload["closure_items"]) == 10, response
for item in payload["closure_items"]:
    assert item["ready"] is False, response
    assert item["blocks_gate_closure"] is True, response
    assert item["blocks_broker_activation"] is True, response
    assert item["source_decision_gate_id"].startswith("EV-AHI-"), response
    assert item["source_acceptance_gate_id"].startswith("EV-AHG-"), response
    assert item["source_audit_gate_id"].startswith("EV-AHH-"), response
for key in [
    "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist_active",
    "activation_approval_decision_owner_handoff_evidence_acceptance_decision_rollup_active",
    "closure_readiness_checklist_complete",
    "closure_readiness_consistent",
    "source_surfaces_bound",
    "source_decision_rollup_bound",
    "source_acceptance_audit_bound",
    "source_acceptance_status_bound",
    "source_handoff_evidence_readiness_matrix_bound",
    "no_store_consistent",
    "no_post_consistent",
    "no_side_effects_consistent",
]:
    assert payload["summary"][key] is True, response
for key in [
    "closure_ready",
    "closure_allowed",
    "approval_review_allowed",
    "gate_closure_allowed",
    "broker_activation_allowed",
    "activation_allowed",
    "handoff_evidence_acceptance_allowed",
    "approval_decision_ready",
    "evidence_packets_attached",
    "review_queue_updated",
    "gates_closed",
    "high_rate_data_plane_active",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
assert payload["summary"]["required_closure_check_count"] == 10, response
assert payload["summary"]["open_closure_check_count"] == 10, response
assert payload["summary"]["blocked_decision_count"] == 10, response
assert payload["summary"]["accepted_evidence_packet_count"] == 0, response
assert payload["summary"]["acceptance_record_persisted_count"] == 0, response
assert payload["summary"]["missing_evidence_packet_count"] == 10, response
assert "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessChecklistJson" in encoded, response
assert "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-checklist" in encoded, response
assert "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.checklist" in encoded, response
assert "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessChecklist" in encoded, response
assert "EV-AHJ-001" in encoded and "EV-AHJ-010" in encoded and "EV-AHI-010" in encoded and "EV-AHH-010" in encoded and "EV-AHG-010" in encoded, response
assert "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_AUDIT_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-audit-consistency)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_AUDIT_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-audit-consistency", response
assert payload["approval_decision_owner_handoff_evidence_acceptance_closure_readiness_audit_consistency_state"] == "contract-only-handoff-evidence-acceptance-closure-audit-consistent", response
assert payload["approval_decision_owner_handoff_evidence_acceptance_closure_readiness_audit_consistency_active"] is True, response
assert payload["consistency_passed"] is True, response
assert {"EV-AHK-001", "EV-AHK-002", "EV-AHK-003", "EV-AHK-004", "EV-AHK-005", "EV-AHK-006", "EV-AHK-007", "EV-AHK-008", "EV-AHK-009", "EV-AHK-010"} <= gate_ids, response
assert len(payload["audit_items"]) == 10, response
for item in payload["audit_items"]:
    assert item["passed"] is True, response
    assert item["closure_still_blocked"] is True, response
    assert item["source_closure_gate_id"].startswith("EV-AHJ-"), response
    assert item["source_decision_gate_id"].startswith("EV-AHI-"), response
    assert item["source_acceptance_gate_id"].startswith("EV-AHG-"), response
    assert item["source_audit_gate_id"].startswith("EV-AHH-"), response
    assert item["source_evidence_id"].startswith("EV-AHE-"), response
for key in [
    "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_audit_consistency_active",
    "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist_active",
    "consistency_passed",
    "source_surfaces_bound",
    "source_closure_readiness_checklist_bound",
    "source_decision_rollup_bound",
    "source_acceptance_audit_bound",
    "source_acceptance_status_bound",
    "source_evidence_readiness_bound",
    "closure_check_count_consistent",
    "closure_blocker_state_consistent",
    "android_linux_parity_consistent",
    "no_store_consistent",
    "no_post_consistent",
    "no_side_effects_consistent",
]:
    assert payload["summary"][key] is True, response
for key in [
    "closure_ready",
    "closure_allowed",
    "approval_review_allowed",
    "gate_closure_allowed",
    "broker_activation_allowed",
    "activation_allowed",
    "handoff_evidence_acceptance_allowed",
    "approval_decision_ready",
    "owner_assignments_persisted",
    "owner_handoff_queue_updated",
    "evidence_packets_attached",
    "evidence_store_created",
    "review_queue_updated",
    "gates_closed",
    "high_rate_data_plane_active",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
assert payload["summary"]["required_audit_count"] == 10, response
assert payload["summary"]["required_closure_check_count"] == 10, response
assert payload["summary"]["open_closure_check_count"] == 10, response
assert payload["summary"]["blocked_decision_count"] == 10, response
assert payload["summary"]["blocked_acceptance_count"] == 10, response
assert payload["summary"]["accepted_evidence_packet_count"] == 0, response
assert payload["summary"]["acceptance_record_persisted_count"] == 0, response
assert payload["summary"]["missing_evidence_packet_count"] == 10, response
assert "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessAuditConsistencyJson" in encoded, response
assert "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-audit-consistency" in encoded, response
assert "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.audit.consistency" in encoded, response
assert "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessAuditConsistency" in encoded, response
assert "EV-AHK-001" in encoded and "EV-AHK-010" in encoded and "EV-AHJ-010" in encoded and "EV-AHI-010" in encoded and "EV-AHH-010" in encoded and "EV-AHG-010" in encoded and "EV-AHE-010" in encoded, response
assert "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_ROLLUP_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-rollup)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_ROLLUP_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-rollup", response
assert payload["approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_rollup_state"] == "contract-only-handoff-evidence-acceptance-closure-decision-blocked", response
assert payload["approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_rollup_active"] is True, response
assert payload["decision_rollup_complete"] is True, response
assert payload["decision_rollup_consistent"] is True, response
assert {"EV-AHL-001", "EV-AHL-002", "EV-AHL-003", "EV-AHL-004", "EV-AHL-005", "EV-AHL-006", "EV-AHL-007", "EV-AHL-008", "EV-AHL-009", "EV-AHL-010"} <= gate_ids, response
for item in payload["mandatory_gates"]:
    assert item["consistent"] is True, response
    assert item["blocks_gate_closure"] is True, response
    assert item["blocks_approval_review"] is True, response
    assert item["blocks_broker_activation"] is True, response
    assert item["source_closure_gate_id"].startswith("EV-AHJ-"), response
    assert item["source_decision_gate_id"].startswith("EV-AHI-"), response
    assert item["source_acceptance_gate_id"].startswith("EV-AHG-"), response
    assert item["source_audit_gate_id"].startswith("EV-AHK-"), response
    assert item["source_evidence_id"].startswith("EV-AHE-"), response
for key in [
    "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_rollup_active",
    "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_audit_consistency_active",
    "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist_active",
    "decision_rollup_complete",
    "decision_rollup_consistent",
    "closure_readiness_audit_consistent",
    "source_surfaces_bound",
    "no_store_decision_rollup",
    "no_post_decision_rollup",
    "no_side_effects_consistent",
    "closure_ready_decision_blocked",
    "evidence_acceptance_decision_blocked",
]:
    assert payload["summary"][key] is True, response
for key in [
    "closure_ready",
    "closure_allowed",
    "closure_decision_ready",
    "approval_review_allowed",
    "gate_closure_allowed",
    "broker_activation_allowed",
    "activation_allowed",
    "handoff_evidence_acceptance_allowed",
    "approval_decision_ready",
    "owner_assignments_persisted",
    "owner_handoff_queue_updated",
    "evidence_packets_attached",
    "evidence_store_created",
    "review_queue_updated",
    "gates_closed",
    "high_rate_data_plane_active",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
    "approval_result_store_created",
    "decision_dry_run_post_called_by_handoff_evidence_acceptance_closure_readiness_decision_rollup",
]:
    assert payload["summary"][key] is False, response
assert payload["summary"]["required_decision_count"] == 10, response
assert payload["summary"]["blocked_decision_count"] == 10, response
assert payload["summary"]["required_closure_check_count"] == 10, response
assert payload["summary"]["open_closure_check_count"] == 10, response
assert payload["summary"]["blocked_acceptance_count"] == 10, response
assert payload["summary"]["accepted_evidence_packet_count"] == 0, response
assert payload["summary"]["acceptance_record_persisted_count"] == 0, response
assert payload["summary"]["missing_evidence_packet_count"] == 10, response
assert payload["summary"]["persisted_dry_run_request_count"] == 0, response
assert payload["summary"]["persisted_dry_run_result_count"] == 0, response
assert payload["summary"]["persisted_approval_decision_count"] == 0, response
assert payload["summary"]["persisted_evidence_packet_count"] == 0, response
assert payload["summary"]["attached_evidence_count"] == 0, response
assert "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionRollupJson" in encoded, response
assert "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-rollup" in encoded, response
assert "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.rollup" in encoded, response
assert "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionRollup" in encoded, response
assert "EV-AHL-001" in encoded and "EV-AHL-010" in encoded and "EV-AHK-010" in encoded and "EV-AHJ-010" in encoded and "EV-AHI-010" in encoded and "EV-AHG-010" in encoded and "EV-AHE-010" in encoded, response
assert "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_ASSIGNMENT_CHECKLIST_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-checklist)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_ASSIGNMENT_CHECKLIST_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-checklist", response
assert payload["state"] == "contract-only-handoff-evidence-acceptance-closure-reviewers-unassigned", response
assert payload["reviewer_assignment_checklist_complete"] is True, response
assert payload["reviewer_assignment_ready"] is False, response
assert payload["source_decision_rollup_bound"] is True, response
assert payload["required_reviewer_assignment_count"] == 10, response
assert payload["assigned_reviewer_count"] == 0, response
assert payload["unassigned_reviewer_count"] == 10, response
assert {"EV-AHM-001", "EV-AHM-002", "EV-AHM-003", "EV-AHM-004", "EV-AHM-005", "EV-AHM-006", "EV-AHM-007", "EV-AHM-008", "EV-AHM-009", "EV-AHM-010"} <= gate_ids, response
for item in payload["reviewer_assignment_rows"]:
    assert item["reviewer_assignment_state"] == "unassigned", response
    assert item["reviewer_id"] is None, response
    assert item["assigned"] is False, response
    assert item["assignment_persisted"] is False, response
    assert item["queue_updated"] is False, response
    assert item["source_decision_gate_id"].startswith("EV-AHL-"), response
    assert item["source_audit_gate_id"].startswith("EV-AHK-"), response
for key in [
    "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_checklist_active",
    "reviewer_assignment_checklist_complete",
    "source_decision_rollup_bound",
    "reviewer_assignment_decision_blocked",
    "no_store_reviewer_assignment_checklist",
    "no_post_reviewer_assignment_checklist",
    "no_side_effects_consistent",
]:
    assert payload["summary"][key] is True, response
for key in [
    "reviewer_assignment_ready",
    "reviewer_assignments_persisted",
    "reviewer_assignment_queue_updated",
    "approval_review_allowed",
    "gate_closure_allowed",
    "broker_activation_allowed",
    "activation_allowed",
    "review_queue_updated",
    "gates_closed",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
assert payload["summary"]["assigned_reviewer_count"] == 0, response
assert payload["summary"]["unassigned_reviewer_count"] == 10, response
assert payload["summary"]["accepted_evidence_packet_count"] == 0, response
assert payload["summary"]["acceptance_record_persisted_count"] == 0, response
assert "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentChecklistJson" in encoded, response
assert "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-checklist" in encoded, response
assert "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.checklist" in encoded, response
assert "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentChecklist" in encoded, response
assert "EV-AHM-001" in encoded and "EV-AHM-010" in encoded and "EV-AHL-010" in encoded and "EV-AHK-010" in encoded, response
assert "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_ASSIGNMENT_AUDIT_CONSISTENCY_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-consistency)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_ASSIGNMENT_AUDIT_CONSISTENCY_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-consistency", response
assert payload["state"] == "contract-only-handoff-evidence-acceptance-closure-reviewer-assignment-audit-consistent", response
assert payload["consistency_passed"] is True, response
assert payload["source_reviewer_assignment_checklist_bound"] is True, response
assert payload["reviewer_assignment_count_consistent"] is True, response
assert payload["reviewer_assignment_blocker_state_consistent"] is True, response
assert payload["reviewer_assignment_ready"] is False, response
assert payload["assigned_reviewer_count"] == 0, response
assert payload["unassigned_reviewer_count"] == 10, response
assert {"EV-AHN-001", "EV-AHN-002", "EV-AHN-003", "EV-AHN-004", "EV-AHN-005", "EV-AHN-006", "EV-AHN-007", "EV-AHN-008", "EV-AHN-009", "EV-AHN-010"} <= gate_ids, response
assert len(payload["audit_items"]) == 10, response
for item in payload["audit_items"]:
    assert item["passed"] is True, response
    assert item["reviewer_assignment_still_blocked"] is True, response
    assert item["reviewer_assigned"] is False, response
    assert item["assignment_persisted"] is False, response
    assert item["queue_updated"] is False, response
    assert item["source_assignment_gate_id"].startswith("EV-AHM-"), response
for key in [
    "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency_active",
    "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_checklist_active",
    "consistency_passed",
    "source_reviewer_assignment_checklist_bound",
    "reviewer_assignment_count_consistent",
    "reviewer_assignment_blocker_state_consistent",
    "no_store_consistent",
    "no_post_consistent",
    "no_queue_gate_broker_consistent",
    "no_side_effects_consistent",
    "android_linux_reviewer_assignment_audit_parity",
]:
    assert payload["summary"][key] is True, response
for key in [
    "reviewer_assignment_ready",
    "reviewer_assignments_persisted",
    "reviewer_assignment_queue_updated",
    "approval_review_allowed",
    "gate_closure_allowed",
    "broker_activation_allowed",
    "activation_allowed",
    "review_queue_updated",
    "gates_closed",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
assert payload["summary"]["assigned_reviewer_count"] == 0, response
assert payload["summary"]["unassigned_reviewer_count"] == 10, response
assert "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistencyJson" in encoded, response
assert "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-consistency" in encoded, response
assert "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.consistency" in encoded, response
assert "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistency" in encoded, response
assert "EV-AHN-001" in encoded and "EV-AHN-010" in encoded and "EV-AHM-010" in encoded and "EV-AHL-010" in encoded, response
assert "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_ASSIGNMENT_AUDIT_DECISION_ROLLUP_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_ASSIGNMENT_AUDIT_DECISION_ROLLUP_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup", response
assert payload["state"] == "contract-only-handoff-evidence-acceptance-closure-reviewer-assignment-decision-blocked", response
assert payload["approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_active"] is True, response
assert payload["decision_rollup_complete"] is True and payload["decision_rollup_consistent"] is True, response
assert payload["source_reviewer_assignment_audit_bound"] is True and payload["reviewer_assignment_audit_consistent"] is True, response
assert payload["reviewer_assignment_decision_blocked"] is True and payload["reviewer_assignment_decision_ready"] is False, response
assert payload["reviewer_assignment_decision"] == "blocked-by-unassigned-reviewers", response
assert payload["required_decision_count"] == 10 and payload["blocked_decision_count"] == 10, response
assert payload["assigned_reviewer_count"] == 0 and payload["unassigned_reviewer_count"] == 10, response
assert {"EV-AHO-001", "EV-AHO-002", "EV-AHO-003", "EV-AHO-004", "EV-AHO-005", "EV-AHO-006", "EV-AHO-007", "EV-AHO-008", "EV-AHO-009", "EV-AHO-010"} <= gate_ids, response
assert len(payload["decision_items"]) == 10, response
for item in payload["decision_items"]:
    assert item["decision"] == "blocked-by-unassigned-reviewers", response
    assert item["decision_ready"] is False, response
    assert item["decision_blocked"] is True, response
    assert item["reviewer_assignment_audit_passed"] is True, response
    assert item["reviewer_assignment_still_blocked"] is True, response
    assert item["reviewer_assigned"] is False, response
    assert item["assignment_persisted"] is False, response
    assert item["queue_updated"] is False, response
    assert item["source_audit_gate_id"].startswith("EV-AHN-"), response
    assert item["source_assignment_gate_id"].startswith("EV-AHM-"), response
for key in [
    "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_active",
    "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency_active",
    "decision_rollup_complete",
    "decision_rollup_consistent",
    "source_reviewer_assignment_audit_bound",
    "reviewer_assignment_audit_consistent",
    "reviewer_assignment_decision_blocked",
    "consistency_passed",
    "reviewer_assignment_count_consistent",
    "reviewer_assignment_blocker_state_consistent",
    "no_store_consistent",
    "no_post_consistent",
    "no_side_effects_consistent",
    "android_linux_reviewer_assignment_audit_parity",
]:
    assert payload["summary"][key] is True, response
for key in [
    "reviewer_assignment_decision_ready",
    "reviewer_assignment_ready",
    "reviewer_assignments_persisted",
    "reviewer_assignment_queue_updated",
    "approval_review_allowed",
    "gate_closure_allowed",
    "broker_activation_allowed",
    "activation_allowed",
    "review_queue_updated",
    "gates_closed",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
assert payload["summary"]["assigned_reviewer_count"] == 0 and payload["summary"]["unassigned_reviewer_count"] == 10, response
assert "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupJson" in encoded, response
assert "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup" in encoded, response
assert "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup" in encoded, response
assert "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollup" in encoded, response
assert "EV-AHO-001" in encoded and "EV-AHO-010" in encoded and "EV-AHN-010" in encoded and "EV-AHM-010" in encoded, response
assert "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_HANDOFF_READINESS_SUMMARY_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-summary)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_HANDOFF_READINESS_SUMMARY_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-summary", response
assert payload["state"] == "contract-only-handoff-evidence-acceptance-closure-handoff-readiness-blocked", response
assert payload["approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_summary_active"] is True, response
assert payload["source_decision_rollup_bound"] is True and payload["decision_rollup_consistent"] is True, response
assert payload["closure_handoff_readiness_complete"] is True, response
assert payload["closure_handoff_ready"] is False and payload["handoff_ready"] is False, response
assert payload["activation_decision"] == "blocked-by-unassigned-reviewers", response
assert payload["reviewer_assignment_decision_blocked"] is True, response
assert payload["required_handoff_dependency_count"] == 10 and payload["open_handoff_dependency_count"] == 10, response
assert payload["assigned_reviewer_count"] == 0 and payload["unassigned_reviewer_count"] == 10, response
assert {"EV-AHP-001", "EV-AHP-002", "EV-AHP-003", "EV-AHP-004", "EV-AHP-005", "EV-AHP-006", "EV-AHP-007", "EV-AHP-008", "EV-AHP-009", "EV-AHP-010"} <= gate_ids, response
assert len(payload["handoff_dependencies"]) == 10, response
for item in payload["handoff_dependencies"]:
    assert item["dependency_state"] == "blocked", response
    assert item["handoff_ready"] is False, response
    assert item["blocks_approval_review"] is True, response
    assert item["blocks_gate_closure"] is True, response
    assert item["blocks_broker_activation"] is True, response
    assert item["source_gate_id"].startswith("EV-AHO-"), response
    assert item["source_audit_gate_id"].startswith("EV-AHN-"), response
    assert item["source_assignment_gate_id"].startswith("EV-AHM-"), response
for key in [
    "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_summary_active",
    "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_active",
    "source_decision_rollup_bound",
    "decision_rollup_consistent",
    "closure_handoff_readiness_complete",
    "reviewer_assignment_decision_blocked",
    "android_linux_closure_handoff_readiness_summary_parity",
    "no_store_handoff_readiness_summary",
    "no_queue_gate_broker_handoff_readiness_summary",
    "no_side_effects_handoff_readiness_summary",
]:
    assert payload["summary"][key] is True, response
for key in [
    "closure_handoff_ready",
    "handoff_ready",
    "reviewer_assignment_ready",
    "reviewer_assignment_decision_ready",
    "reviewer_assignments_persisted",
    "reviewer_assignment_queue_updated",
    "handoff_evidence_acceptance_allowed",
    "approval_review_allowed",
    "gate_closure_allowed",
    "broker_activation_allowed",
    "activation_allowed",
    "review_queue_updated",
    "gates_closed",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
assert payload["summary"]["required_handoff_dependency_count"] == 10 and payload["summary"]["open_handoff_dependency_count"] == 10, response
assert payload["summary"]["assigned_reviewer_count"] == 0 and payload["summary"]["unassigned_reviewer_count"] == 10, response
assert "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummaryJson" in encoded, response
assert "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-summary" in encoded, response
assert "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.summary" in encoded, response
assert "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummary" in encoded, response
assert "EV-AHP-001" in encoded and "EV-AHP-010" in encoded and "EV-AHO-010" in encoded and "EV-AHN-010" in encoded and "EV-AHM-010" in encoded, response
assert "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_HANDOFF_READINESS_AUDIT_CONSISTENCY_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-audit-consistency)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_HANDOFF_READINESS_AUDIT_CONSISTENCY_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-audit-consistency", response
assert payload["state"] == "contract-only-handoff-evidence-acceptance-closure-handoff-readiness-audit-consistent", response
assert payload["approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_consistency_active"] is True, response
assert payload["consistency_passed"] is True, response
assert payload["source_closure_handoff_readiness_summary_bound"] is True, response
assert payload["closure_handoff_dependency_count_consistent"] is True and payload["closure_handoff_blocker_state_consistent"] is True, response
assert payload["closure_handoff_ready"] is False and payload["handoff_ready"] is False, response
assert payload["required_handoff_dependency_count"] == 10 and payload["open_handoff_dependency_count"] == 10, response
assert payload["assigned_reviewer_count"] == 0 and payload["unassigned_reviewer_count"] == 10, response
assert {"EV-AHQ-001", "EV-AHQ-002", "EV-AHQ-003", "EV-AHQ-004", "EV-AHQ-005", "EV-AHQ-006", "EV-AHQ-007", "EV-AHQ-008", "EV-AHQ-009", "EV-AHQ-010"} <= gate_ids, response
assert len(payload["audit_items"]) == 10, response
for item in payload["audit_items"]:
    assert item["handoff_dependency_still_blocked"] is True, response
    assert item["reviewer_assigned"] is False, response
    assert item["assignment_persisted"] is False and item["queue_updated"] is False, response
    assert item["source_handoff_gate_id"].startswith("EV-AHP-"), response
    assert item["source_decision_gate_id"].startswith("EV-AHO-"), response
    assert item["source_audit_gate_id"].startswith("EV-AHN-"), response
    assert item["source_assignment_gate_id"].startswith("EV-AHM-"), response
for key in [
    "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_consistency_active",
    "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_summary_active",
    "consistency_passed",
    "source_closure_handoff_readiness_summary_bound",
    "closure_handoff_dependency_count_consistent",
    "closure_handoff_blocker_state_consistent",
    "no_store_consistent",
    "no_post_consistent",
    "no_queue_gate_broker_consistent",
    "no_side_effects_consistent",
    "android_linux_closure_handoff_readiness_audit_parity",
]:
    assert payload["summary"][key] is True, response
for key in [
    "closure_handoff_ready",
    "handoff_ready",
    "reviewer_assignment_ready",
    "reviewer_assignment_decision_ready",
    "reviewer_assignments_persisted",
    "reviewer_assignment_queue_updated",
    "handoff_evidence_acceptance_allowed",
    "approval_review_allowed",
    "gate_closure_allowed",
    "broker_activation_allowed",
    "activation_allowed",
    "review_queue_updated",
    "gates_closed",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
assert payload["summary"]["required_handoff_dependency_count"] == 10 and payload["summary"]["open_handoff_dependency_count"] == 10, response
assert payload["summary"]["assigned_reviewer_count"] == 0 and payload["summary"]["unassigned_reviewer_count"] == 10, response
assert "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditConsistencyJson" in encoded, response
assert "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-audit-consistency" in encoded, response
assert "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.audit.consistency" in encoded, response
assert "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditConsistency" in encoded, response
assert "EV-AHQ-001" in encoded and "EV-AHQ-010" in encoded and "EV-AHP-010" in encoded and "EV-AHO-010" in encoded and "EV-AHN-010" in encoded and "EV-AHM-010" in encoded, response
assert "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, response
assert "NV-P-006" in encoded and "FW-U-003" in encoded and "DEL-004" in encoded, response
PY
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_HANDOFF_READINESS_AUDIT_DECISION_ROLLUP_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-audit-decision-rollup)"
python3 - "$EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_HANDOFF_READINESS_AUDIT_DECISION_ROLLUP_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-audit-decision-rollup", response
assert payload["state"] == "contract-only-handoff-evidence-acceptance-closure-handoff-readiness-audit-decision-blocked", response
assert payload["approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup_active"] is True, response
assert payload["decision_rollup_complete"] is True and payload["decision_rollup_consistent"] is True, response
assert payload["closure_handoff_audit_decision_blocked"] is True and payload["closure_handoff_audit_decision_ready"] is False, response
assert payload["required_decision_count"] == 10 and payload["blocked_decision_count"] == 10, response
assert payload["passed_audit_count"] == 10 and payload["failed_audit_count"] == 0, response
assert payload["assigned_reviewer_count"] == 0 and payload["unassigned_reviewer_count"] == 10, response
assert {"EV-AHR-001", "EV-AHR-002", "EV-AHR-003", "EV-AHR-004", "EV-AHR-005", "EV-AHR-006", "EV-AHR-007", "EV-AHR-008", "EV-AHR-009", "EV-AHR-010"} <= gate_ids, response
assert len(payload["decision_items"]) == 10, response
for item in payload["decision_items"]:
    assert item["decision_state"] == "blocked", response
    assert item["decision_ready"] is False, response
    assert item["source_audit_gate_id"].startswith("EV-AHQ-"), response
    assert item["source_handoff_gate_id"].startswith("EV-AHP-"), response
    assert item["source_decision_gate_id"].startswith("EV-AHO-"), response
    assert item["reviewer_assigned"] is False and item["assignment_persisted"] is False and item["queue_updated"] is False, response
for key in [
    "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup_active",
    "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_consistency_active",
    "source_closure_handoff_readiness_audit_bound",
    "closure_handoff_readiness_audit_consistent",
    "decision_rollup_complete",
    "decision_rollup_consistent",
    "closure_handoff_audit_decision_blocked",
]:
    assert payload["summary"][key] is True, response
for key in [
    "closure_handoff_audit_decision_ready",
    "closure_handoff_ready",
    "handoff_ready",
    "reviewer_assignments_persisted",
    "reviewer_assignment_queue_updated",
    "approval_review_allowed",
    "gate_closure_allowed",
    "broker_activation_allowed",
    "activation_allowed",
    "review_queue_updated",
    "gates_closed",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
assert "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditDecisionRollupJson" in encoded, response
assert "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-audit-decision-rollup" in encoded, response
assert "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.audit.decision.rollup" in encoded, response
assert "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditDecisionRollup" in encoded, response
assert "EV-AHR-001" in encoded and "EV-AHR-010" in encoded and "EV-AHQ-010" in encoded and "EV-AHP-010" in encoded, response
assert "DRV-GAP-004" in encoded and "DRV-GAP-005" in encoded, response
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
HARDWARE_OWNER_EVIDENCE_REPLACEMENT_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-replacement-trigger-checklist)"
python3 - "$HARDWARE_OWNER_EVIDENCE_REPLACEMENT_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["replacement_trigger_checklist_state"] == "contract-only-replacement-trigger-checklist-open", response
assert payload["replacement_allowed"] is False, response
assert payload["adapter_activation_allowed"] is False, response
assert payload["gate_closure_allowed"] is False, response
assert payload["owner_decision_complete"] is False, response
assert payload["replacement_policy_shape"]["replacement_policy_confirmed"] is False, response
assert payload["replacement_policy_shape"]["adapter_readiness_criteria_confirmed"] is False, response
assert payload["rollback_policy_shape"]["rollback_to_empty_interface_plan_confirmed"] is False, response
assert {"HW-OET-001", "HW-OET-002", "HW-OET-003", "HW-OET-004", "HW-OET-005", "HW-OET-006", "HW-OET-007", "HW-OET-008"} <= gate_ids, response
assert all(item["replacement_allowed"] is False for item in payload["replacement_targets"]), response
assert all(item["adapter_activation_allowed"] is False for item in payload["replacement_targets"]), response
assert all(item["driver_hal_development_triggered"] is False for item in payload["replacement_targets"]), response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["owner_decision_evidence_replacement_trigger_checklist_active"] is True, response
assert payload["summary"]["owner_decision_evidence_retention_checklist_active"] is True, response
assert payload["summary"]["owner_decision_evidence_status_contract_active"] is True, response
assert payload["summary"]["owner_decision_evidence_contract_active"] is True, response
assert "getHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklistJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-replacement-trigger-checklist" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.replacement.trigger.checklist" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklist" in encoded, response
assert "HW-OET-005" in encoded and "rollback-to-empty-interface" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_SELECTED_ADAPTER_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-selected-adapter-readiness-checklist)"
python3 - "$HARDWARE_OWNER_EVIDENCE_SELECTED_ADAPTER_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["selected_adapter_readiness_checklist_state"] == "contract-only-selected-adapter-readiness-checklist-open", response
assert payload["adapter_candidate_recorded"] is False, response
assert payload["adapter_load_allowed"] is False, response
assert payload["adapter_activation_allowed"] is False, response
assert payload["hardware_access_allowed"] is False, response
assert payload["gate_closure_allowed"] is False, response
assert payload["owner_decision_complete"] is False, response
assert payload["adapter_evidence_shape"]["adapter_owner_assigned"] is False, response
assert payload["adapter_evidence_shape"]["adapter_interface_contract_approved"] is False, response
assert payload["adapter_evidence_shape"]["driver_hal_gap_evidence_attached"] is False, response
assert payload["adapter_load_policy_shape"]["load_policy_confirmed"] is False, response
assert {"HW-OEA-001", "HW-OEA-002", "HW-OEA-003", "HW-OEA-004", "HW-OEA-005", "HW-OEA-006", "HW-OEA-007", "HW-OEA-008"} <= gate_ids, response
assert all(item["adapter_candidate_recorded"] is False for item in payload["selected_adapter_candidates"]), response
assert all(item["adapter_load_allowed"] is False for item in payload["selected_adapter_candidates"]), response
assert all(item["adapter_activation_allowed"] is False for item in payload["selected_adapter_candidates"]), response
assert all(item["hardware_access_allowed"] is False for item in payload["selected_adapter_candidates"]), response
assert all(item["driver_hal_development_triggered"] is False for item in payload["selected_adapter_candidates"]), response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["owner_decision_evidence_selected_adapter_readiness_checklist_active"] is True, response
assert payload["summary"]["owner_decision_evidence_replacement_trigger_checklist_active"] is True, response
assert payload["summary"]["owner_decision_evidence_retention_checklist_active"] is True, response
assert payload["summary"]["owner_decision_evidence_status_contract_active"] is True, response
assert payload["summary"]["owner_decision_evidence_contract_active"] is True, response
assert "getHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklistJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-selected-adapter-readiness-checklist" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.selected.adapter.readiness.checklist" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklist" in encoded, response
assert "HW-OEA-007" in encoded and "rollback-to-empty-interface" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_BLOCKER_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-blocker-rollup)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_BLOCKER_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
source_names = {item["source"] for item in payload["source_checklists"]}
assert response["status"] == "ok", response
assert payload["adapter_load_blocker_rollup_state"] == "contract-only-adapter-load-blockers-open", response
assert payload["adapter_load_blocker_rollup_active"] is True, response
assert payload["adapter_load_ready"] is False, response
assert payload["adapter_load_allowed"] is False, response
assert payload["adapter_activation_allowed"] is False, response
assert payload["hardware_access_allowed"] is False, response
assert payload["gate_closure_allowed"] is False, response
assert payload["owner_decision_complete"] is False, response
assert payload["all_blockers_cleared"] is False, response
assert {"HW-ALB-001", "HW-ALB-002", "HW-ALB-003", "HW-ALB-004", "HW-ALB-005", "HW-ALB-006", "HW-ALB-007", "HW-ALB-008"} <= gate_ids, response
assert {"activation-checklist", "owner-decision-status", "owner-evidence-status", "owner-evidence-retention-checklist", "replacement-trigger-checklist", "selected-adapter-readiness-checklist"} <= source_names, response
assert all(item["adapter_load_allowed"] is False for item in payload["per_interface_blockers"]), response
assert all(item["adapter_activation_allowed"] is False for item in payload["per_interface_blockers"]), response
assert all(item["hardware_access_allowed"] is False for item in payload["per_interface_blockers"]), response
assert all(item["adapter_load_blocked"] is True for item in payload["per_interface_blockers"]), response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["owner_decision_evidence_adapter_load_blocker_rollup_active"] is True, response
assert payload["summary"]["owner_decision_evidence_selected_adapter_readiness_checklist_active"] is True, response
assert payload["summary"]["owner_decision_evidence_replacement_trigger_checklist_active"] is True, response
assert payload["summary"]["owner_decision_evidence_retention_checklist_active"] is True, response
assert payload["summary"]["owner_decision_evidence_status_contract_active"] is True, response
assert payload["summary"]["owner_decision_evidence_contract_active"] is True, response
assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollupJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-blocker-rollup" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.blocker.rollup" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollup" in encoded, response
assert "HW-ALB-006" in encoded and "safety-policy-smoke-rollback" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_DRY_RUN_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-dry-run)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_DRY_RUN_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["adapter_load_dry_run_state"] == "rejected_blocked_contract_only", response
assert payload["dry_run_validated"] is True, response
assert payload["adapter_load_blocked"] is True, response
assert payload["adapter_load_allowed"] is False, response
assert payload["adapter_activation_allowed"] is False, response
assert payload["hardware_access_allowed"] is False, response
assert payload["gate_closure_allowed"] is False, response
assert payload["selected_interface_id"] == "npu-runtime", response
assert payload["blocker_rollup_reference"]["adapter_load_ready"] is False, response
assert payload["blocker_rollup_reference"]["all_blockers_cleared"] is False, response
assert {"HW-ALD-001", "HW-ALD-002", "HW-ALD-003", "HW-ALD-004", "HW-ALD-005", "HW-ALD-006", "HW-ALD-007", "HW-ALD-008"} <= gate_ids, response
for key in [
    "owner_decision_complete",
    "all_blockers_cleared",
    "adapter_load_ready",
    "adapter_candidate_recorded",
    "adapter_load_allowed",
    "adapter_activation_allowed",
    "hardware_access_allowed",
    "gate_closure_allowed",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
assert payload["summary"]["owner_decision_evidence_adapter_load_dry_run_active"] is True, response
assert payload["summary"]["request_shape_valid"] is True, response
assert payload["summary"]["dry_run_validated"] is True, response
assert "dryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-dry-run" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.dry.run" in encoded, response
assert "DryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoad" in encoded, response
assert "HW-ALD-007" in encoded and "open-blockers-enforced" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_DRY_RUN_STATUS_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-dry-run-status)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_DRY_RUN_STATUS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["adapter_load_dry_run_status_state"] == "contract-only-no-store-status", response
assert payload["last_result_available"] is False, response
assert payload["persisted_dry_run_count"] == 0, response
assert payload["pending_review_count"] == 0, response
assert payload["review_queue_updated"] is False, response
assert payload["evidence_persisted"] is False, response
assert payload["blocker_rollup_reference"]["adapter_load_ready"] is False, response
assert payload["blocker_rollup_reference"]["all_blockers_cleared"] is False, response
assert {"HW-ALS-001", "HW-ALS-002", "HW-ALS-003", "HW-ALS-004", "HW-ALS-005", "HW-ALS-006", "HW-ALS-007", "HW-ALS-008"} <= gate_ids, response
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
    assert payload["no_store_invariants"][key] is False, response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["owner_decision_evidence_adapter_load_dry_run_status_active"] is True, response
assert payload["summary"]["last_result_available"] is False, response
assert payload["summary"]["persisted_dry_run_count"] == 0, response
assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatusJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-dry-run-status" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.dry.run.status" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatus" in encoded, response
assert "HW-ALS-004" in encoded and "last-result-not-stored" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_DRY_RUN_AUDIT_CONSISTENCY_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-dry-run-audit-consistency)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_DRY_RUN_AUDIT_CONSISTENCY_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["audit_consistency_state"] == "contract-only-consistent-blocked", response
assert payload["consistency_checked"] is True, response
assert payload["consistency_passed"] is True, response
assert payload["no_store_consistent"] is True, response
assert payload["blocker_rollup_consistent"] is True, response
assert payload["dry_run_rejection_consistent"] is True, response
assert payload["source_surfaces"]["dry_run_request"]["called_by_audit_consistency_view"] is False, response
assert payload["source_surfaces"]["dry_run_status"]["persisted_dry_run_count"] == 0, response
assert payload["source_surfaces"]["blocker_rollup"]["adapter_load_ready"] is False, response
assert {"HW-ALC-001", "HW-ALC-002", "HW-ALC-003", "HW-ALC-004", "HW-ALC-005", "HW-ALC-006", "HW-ALC-007", "HW-ALC-008"} <= gate_ids, response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["owner_decision_evidence_adapter_load_dry_run_audit_consistency_active"] is True, response
assert payload["summary"]["consistency_passed"] is True, response
assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistencyJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-dry-run-audit-consistency" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.dry.run.audit.consistency" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistency" in encoded, response
assert "HW-ALC-004" in encoded and "dry-run-rejection-contract-consistent" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_AUTHORITY_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-approval-authority-checklist)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_AUTHORITY_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
decision_ids = {item["decision_id"] for item in payload["approval_authority_decisions"]}
assert response["status"] == "ok", response
assert payload["approval_authority_checklist_state"] == "contract-only-approval-authority-checklist-open", response
assert payload["approval_authority_assigned"] is False, response
assert payload["approval_policy_confirmed"] is False, response
assert payload["approval_signature_rules_confirmed"] is False, response
assert payload["approval_rbac_confirmed"] is False, response
assert payload["approval_workflow_active"] is False, response
assert payload["approval_record_persisted"] is False, response
assert payload["adapter_load_allowed"] is False, response
assert payload["hardware_access_allowed"] is False, response
assert payload["source_surfaces"]["adapter_load_dry_run_request"]["called_by_approval_authority_checklist"] is False, response
assert payload["source_surfaces"]["adapter_load_dry_run_status"]["persisted_dry_run_count"] == 0, response
assert payload["source_surfaces"]["adapter_load_dry_run_audit_consistency"]["consistency_passed"] is True, response
assert {"HW-ALA-001", "HW-ALA-002", "HW-ALA-003", "HW-ALA-004", "HW-ALA-005", "HW-ALA-006", "HW-ALA-007", "HW-ALA-008"} <= gate_ids, response
assert {"HW-ALA-002", "HW-ALA-003", "HW-ALA-004", "HW-ALA-005", "HW-ALA-006"} <= decision_ids, response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["owner_decision_evidence_adapter_load_approval_authority_checklist_active"] is True, response
assert payload["summary"]["dry_run_audit_consistency_passed"] is True, response
assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklistJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-approval-authority-checklist" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.authority.checklist" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklist" in encoded, response
assert "approval without durable evidence record" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_AUTHORITY_STATUS_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-approval-authority-status)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_AUTHORITY_STATUS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["approval_authority_status_state"] == "contract-only-approval-authority-status-open", response
assert payload["approval_authority_checklist_available"] is True, response
assert payload["approval_record_available"] is False, response
assert payload["persisted_approval_record_count"] == 0, response
assert payload["pending_approval_review_count"] == 0, response
assert payload["approval_review_queue_updated"] is False, response
assert payload["approval_evidence_store_active"] is False, response
assert payload["approval_decision_passed"] is False, response
assert payload["no_store_consistent"] is True, response
assert payload["approval_decisions_open"] is True, response
assert payload["adapter_load_still_blocked"] is True, response
assert payload["source_surfaces"]["approval_authority_checklist"]["called_by_approval_authority_status"] is True, response
assert payload["source_surfaces"]["approval_record_store"]["state"] == "not-implemented-contract-only", response
assert {"HW-AAS-001", "HW-AAS-002", "HW-AAS-003", "HW-AAS-004", "HW-AAS-005", "HW-AAS-006", "HW-AAS-007", "HW-AAS-008"} <= gate_ids, response
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
    assert payload["summary"][key] is False, response
for key in [
    "owner_decision_evidence_adapter_load_approval_authority_status_active",
    "owner_decision_evidence_adapter_load_approval_authority_checklist_active",
    "approval_authority_checklist_available",
    "no_store_consistent",
    "approval_decisions_open",
    "adapter_load_still_blocked",
]:
    assert payload["summary"][key] is True, response
assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatusJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-approval-authority-status" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.authority.status" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatus" in encoded, response
assert "HW-AAS-002" in encoded and "zero-persisted-approval-records" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_AUTHORITY_AUDIT_CONSISTENCY_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-approval-authority-audit-consistency)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_AUTHORITY_AUDIT_CONSISTENCY_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["approval_authority_audit_consistency_state"] == "contract-only-approval-authority-consistent-blocked", response
assert payload["consistency_checked"] is True, response
assert payload["consistency_passed"] is True, response
assert payload["approval_status_no_store_consistent"] is True, response
assert payload["approval_decisions_open_consistent"] is True, response
assert payload["adapter_load_blocked_consistent"] is True, response
assert payload["dry_run_audit_consistency_passed"] is True, response
assert payload["source_surfaces"]["adapter_load_dry_run_audit_consistency"]["called_by_approval_authority_audit_consistency"] is True, response
assert payload["source_surfaces"]["approval_authority_status"]["persisted_approval_record_count"] == 0, response
assert {"HW-AAC-001", "HW-AAC-002", "HW-AAC-003", "HW-AAC-004", "HW-AAC-005", "HW-AAC-006", "HW-AAC-007", "HW-AAC-008"} <= gate_ids, response
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
    assert payload["summary"][key] is False, response
for key in [
    "owner_decision_evidence_adapter_load_approval_authority_audit_consistency_active",
    "owner_decision_evidence_adapter_load_approval_authority_status_active",
    "owner_decision_evidence_adapter_load_approval_authority_checklist_active",
    "owner_decision_evidence_adapter_load_dry_run_audit_consistency_active",
    "consistency_passed",
    "approval_status_no_store_consistent",
    "approval_decisions_open_consistent",
    "adapter_load_blocked_consistent",
]:
    assert payload["summary"][key] is True, response
assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistencyJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-approval-authority-audit-consistency" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.authority.audit.consistency" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistency" in encoded, response
assert "HW-AAC-002" in encoded and "approval-status-no-store-consistent" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_DECISION_DRY_RUN_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_DECISION_DRY_RUN_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["approval_decision_dry_run_state"] == "rejected_blocked_contract_only", response
assert payload["approval_decision_dry_run_validated"] is True, response
assert payload["approval_decision"] == "approve_adapter_load", response
assert payload["approval_authority_ready"] is False, response
assert payload["approval_signature_present"] is True, response
assert payload["adapter_load_blocked"] is True, response
assert payload["validation"]["request_shape_valid"] is True, response
assert payload["validation"]["approval_status_no_store_bound"] is True, response
assert {"HW-APD-001", "HW-APD-002", "HW-APD-003", "HW-APD-004", "HW-APD-005", "HW-APD-006", "HW-APD-007", "HW-APD-008"} <= gate_ids, response
for key in [
    "approval_authority_ready",
    "approval_record_available",
    "approval_record_persisted",
    "approval_decision_persisted",
    "approval_review_queue_updated",
    "approval_evidence_store_active",
    "approval_decision_passed",
    "adapter_load_allowed",
    "adapter_activation_allowed",
    "hardware_access_allowed",
    "gate_closure_allowed",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
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
    assert payload["summary"][key] is True, response
assert payload["decision_dry_run_result"]["allowed_to_persist_approval"] is False, response
assert payload["decision_dry_run_result"]["allowed_to_load_adapter"] is False, response
assert payload["decision_dry_run_result"]["hardware_accessed"] is False, response
assert "dryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.dry.run" in encoded, response
assert "DryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecision" in encoded, response
assert "HW-APD-006" in encoded and "blocked-contract-only-rejection" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_DECISION_DRY_RUN_STATUS_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run-status)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_DECISION_DRY_RUN_STATUS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["approval_decision_dry_run_status_state"] == "contract-only-approval-decision-dry-run-status-no-store", response
assert payload["approval_decision_dry_run_status_active"] is True, response
assert payload["last_approval_decision_result_available"] is False, response
assert payload["persisted_approval_decision_count"] == 0, response
assert payload["pending_approval_decision_review_count"] == 0, response
assert payload["source_surfaces"]["approval_decision_dry_run"]["called_by_status"] is False, response
assert payload["source_surfaces"]["approval_decision_dry_run"]["last_result_persisted"] is False, response
assert payload["source_surfaces"]["approval_authority_status"]["persisted_approval_record_count"] == 0, response
assert {"HW-APS-001", "HW-APS-002", "HW-APS-003", "HW-APS-004", "HW-APS-005", "HW-APS-006", "HW-APS-007", "HW-APS-008"} <= gate_ids, response
for key in [
    "last_approval_decision_result_available",
    "approval_decision_review_queue_updated",
    "approval_decision_evidence_store_active",
    "approval_decision_persisted",
    "approval_decision_passed",
    "approval_decision_dry_run_allowed_to_load_adapter",
    "decision_dry_run_post_called_by_status",
    "adapter_load_allowed",
    "adapter_activation_allowed",
    "hardware_access_allowed",
    "gate_closure_allowed",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
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
    assert payload["summary"][key] is True, response
assert payload["summary"]["persisted_approval_decision_count"] == 0, response
assert payload["summary"]["pending_approval_decision_review_count"] == 0, response
assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunStatusJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run-status" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.dry.run.status" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunStatus" in encoded, response
assert "HW-APS-005" in encoded and "last-approval-decision-result-not-stored" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_DECISION_DRY_RUN_AUDIT_CONSISTENCY_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run-audit-consistency)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_DECISION_DRY_RUN_AUDIT_CONSISTENCY_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["approval_decision_dry_run_audit_consistency_state"] == "contract-only-approval-decision-dry-run-audit-consistency", response
assert payload["approval_decision_dry_run_audit_consistency_active"] is True, response
assert payload["consistency_checked"] is True, response
assert payload["consistency_passed"] is True, response
assert payload["no_store_consistent"] is True, response
assert payload["decision_dry_run_rejection_consistent"] is True, response
assert payload["approval_authority_audit_consistent"] is True, response
assert payload["adapter_load_blocked_consistent"] is True, response
assert payload["source_surfaces"]["approval_decision_dry_run"]["called_by_audit_consistency"] is False, response
assert payload["source_surfaces"]["approval_decision_dry_run_status"]["post_called_by_status"] is False, response
assert payload["source_surfaces"]["approval_decision_dry_run_status"]["persisted_approval_decision_count"] == 0, response
assert {"HW-APA-001", "HW-APA-002", "HW-APA-003", "HW-APA-004", "HW-APA-005", "HW-APA-006", "HW-APA-007", "HW-APA-008"} <= gate_ids, response
for key in [
    "decision_dry_run_post_called_by_audit_consistency",
    "decision_dry_run_post_called_by_status",
    "last_approval_decision_result_available",
    "approval_decision_persisted",
    "approval_decision_review_queue_updated",
    "approval_decision_evidence_store_active",
    "approval_decision_passed",
    "adapter_load_allowed",
    "adapter_activation_allowed",
    "hardware_access_allowed",
    "gate_closure_allowed",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
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
]:
    assert payload["summary"][key] is True, response
assert payload["summary"]["persisted_approval_decision_count"] == 0, response
assert payload["summary"]["pending_approval_decision_review_count"] == 0, response
assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunAuditConsistencyJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run-audit-consistency" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.dry.run.audit.consistency" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunAuditConsistency" in encoded, response
assert "HW-APA-003" in encoded and "approval-decision-dry-run-rejection-consistent" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_DECISION_CLOSURE_BLOCKER_MATRIX_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-approval-decision-closure-blocker-matrix)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_DECISION_CLOSURE_BLOCKER_MATRIX_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
dependency_ids = set(payload["blockers_by_dependency"])
assert response["status"] == "ok", response
assert payload["approval_decision_closure_blocker_matrix_state"] == "contract-only-approval-decision-closure-blockers-open", response
assert payload["approval_decision_closure_blocker_matrix_active"] is True, response
assert payload["matrix_complete"] is True, response
assert payload["closure_ready"] is False, response
assert payload["closure_allowed"] is False, response
assert payload["approval_decision_closure_allowed"] is False, response
assert payload["unresolved_blocker_count"] == 13, response
assert len(payload["closure_blockers"]) == 13, response
assert all(item["state"] == "open" for item in payload["closure_blockers"]), response
assert all(item["blocks_adapter_load"] and item["blocks_gate_closure"] for item in payload["closure_blockers"]), response
assert all(item["passed"] for item in payload["source_surface_checks"]), response
assert {"HW-APM-001", "HW-APM-002", "HW-APM-003", "HW-APM-004", "HW-APM-005", "HW-APM-006", "HW-APM-007", "HW-APM-008"} <= gate_ids, response
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
} <= dependency_ids, response
assert payload["source_surfaces"]["approval_decision_dry_run_audit_consistency"]["consistency_passed"] is True, response
assert payload["source_surfaces"]["approval_decision_dry_run_status"]["persisted_approval_decision_count"] == 0, response
assert payload["source_surfaces"]["approval_authority_status"]["approval_decisions_open"] is True, response
assert payload["source_surfaces"]["approval_authority_audit_consistency"]["adapter_load_blocked_consistent"] is True, response
assert payload["source_surfaces"]["adapter_load_blocker_rollup"]["adapter_load_ready"] is False, response
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
    assert payload["summary"][key] is False, response
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
    assert payload["summary"][key] is True, response
assert payload["summary"]["unresolved_blocker_count"] == 13, response
assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionClosureBlockerMatrixJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-approval-decision-closure-blocker-matrix" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.closure.blocker.matrix" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionClosureBlockerMatrix" in encoded, response
assert "HW-APM-007" in encoded and "android-linux-closure-blocker-parity" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_DECISION_REVIEWER_MATRIX_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-approval-decision-reviewer-matrix)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_DECISION_REVIEWER_MATRIX_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
roles = {item["role"] for item in payload["reviewer_rows"]}
assert response["status"] == "ok", response
assert payload["approval_decision_reviewer_matrix_state"] == "contract-only-approval-decision-reviewers-unassigned", response
assert payload["approval_decision_reviewer_matrix_active"] is True, response
assert payload["matrix_complete"] is True, response
assert payload["review_ready"] is False, response
assert payload["approval_review_allowed"] is False, response
assert payload["retention_review_allowed"] is False, response
assert payload["gate_closure_allowed"] is False, response
assert payload["adapter_load_allowed"] is False, response
assert payload["unassigned_reviewer_count"] == 11, response
assert len(payload["reviewer_rows"]) == 11, response
assert all(item["state"] == "unassigned" for item in payload["reviewer_rows"]), response
assert all(item["source_blocker_state"] == "open" for item in payload["reviewer_rows"]), response
assert all(item["blocks_adapter_load"] and item["blocks_gate_closure"] for item in payload["reviewer_rows"]), response
assert all(item["passed"] for item in payload["source_surface_checks"]), response
assert {"HW-APR-001", "HW-APR-002", "HW-APR-003", "HW-APR-004", "HW-APR-005", "HW-APR-006", "HW-APR-007", "HW-APR-008"} <= gate_ids, response
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
} <= roles, response
assert payload["source_surfaces"]["approval_decision_closure_blocker_matrix"]["matrix_complete"] is True, response
assert payload["source_surfaces"]["approval_decision_closure_blocker_matrix"]["approval_decision_closure_allowed"] is False, response
for key in [
    "review_ready",
    "approval_review_allowed",
    "retention_review_allowed",
    "approval_decision_closure_allowed",
    "approval_decision_persisted",
    "approval_decision_review_queue_updated",
    "approval_decision_evidence_store_active",
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
    assert payload["summary"][key] is False, response
for key in [
    "owner_decision_evidence_adapter_load_approval_decision_reviewer_matrix_active",
    "owner_decision_evidence_adapter_load_approval_decision_closure_blocker_matrix_active",
    "matrix_complete",
    "no_side_effects_consistent",
]:
    assert payload["summary"][key] is True, response
assert payload["summary"]["unassigned_reviewer_count"] == 11, response
assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionReviewerMatrixJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-approval-decision-reviewer-matrix" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.reviewer.matrix" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionReviewerMatrix" in encoded, response
assert "HW-APR-007" in encoded and "android-linux-reviewer-matrix-parity" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_REVIEWER_EVIDENCE_HANDOFF_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-checklist)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_REVIEWER_EVIDENCE_HANDOFF_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
schema_fields = {item["field"] for item in payload["handoff_packet_schema"]}
assert response["status"] == "ok", response
assert payload["operation"] == "hardware-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-checklist", response
assert payload["approval_reviewer_evidence_handoff_state"] == "contract-only-reviewer-evidence-handoff-blocked", response
assert payload["approval_reviewer_evidence_handoff_checklist_active"] is True, response
assert payload["handoff_checklist_complete"] is True, response
assert payload["handoff_ready"] is False, response
assert payload["evidence_handoff_allowed"] is False, response
assert payload["approval_review_allowed"] is False, response
assert payload["retention_review_allowed"] is False, response
assert payload["gate_closure_allowed"] is False, response
assert payload["adapter_load_allowed"] is False, response
assert payload["required_handoff_packet_count"] == 11, response
assert payload["missing_handoff_packet_count"] == 11, response
assert len(payload["handoff_rows"]) == 11, response
assert {
    "reviewer_identity",
    "source_blocker_reference",
    "evidence_reference_uri",
    "owner_signature_reference",
    "acceptance_rule",
    "retention_policy_reference",
    "audit_export_reference",
    "rollback_fault_note",
} <= schema_fields, response
assert all(item["state"] == "missing" for item in payload["handoff_rows"]), response
assert all(item["handoff_packet_attached"] is False for item in payload["handoff_rows"]), response
assert all(item["evidence_handoff_ready"] is False for item in payload["handoff_rows"]), response
assert all(item["blocks_approval_review"] and item["blocks_retention_review"] for item in payload["handoff_rows"]), response
assert all(item["blocks_gate_closure"] and item["blocks_adapter_load"] for item in payload["handoff_rows"]), response
assert all(item["passed"] for item in payload["source_surface_checks"]), response
assert {"HW-ARH-001", "HW-ARH-002", "HW-ARH-003", "HW-ARH-004", "HW-ARH-005", "HW-ARH-006", "HW-ARH-007", "HW-ARH-008"} <= gate_ids, response
source = payload["source_surfaces"]["approval_decision_reviewer_matrix"]
assert source["matrix_complete"] is True, response
assert source["unassigned_reviewer_count"] == 11, response
assert source["review_ready"] is False, response
assert source["approval_review_allowed"] is False, response
assert source["retention_review_allowed"] is False, response
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
    assert payload["summary"][key] is False, response
for key in [
    "owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_checklist_active",
    "owner_decision_evidence_adapter_load_approval_decision_reviewer_matrix_active",
    "handoff_checklist_complete",
    "no_side_effects_consistent",
]:
    assert payload["summary"][key] is True, response
assert payload["summary"]["required_handoff_packet_count"] == 11, response
assert payload["summary"]["missing_handoff_packet_count"] == 11, response
assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffChecklistJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-checklist" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.checklist" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffChecklist" in encoded, response
assert "HW-ARH-007" in encoded and "android-linux-evidence-handoff-parity" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_REVIEWER_EVIDENCE_HANDOFF_ACCEPTANCE_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-status)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_REVIEWER_EVIDENCE_HANDOFF_ACCEPTANCE_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "hardware-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-status", response
assert payload["approval_reviewer_evidence_handoff_acceptance_state"] == "contract-only-handoff-acceptance-blocked", response
assert payload["approval_reviewer_evidence_handoff_acceptance_status_active"] is True, response
assert payload["acceptance_status_complete"] is True, response
assert payload["handoff_ready"] is False, response
assert payload["handoff_acceptance_ready"] is False, response
assert payload["handoff_acceptance_allowed"] is False, response
assert payload["evidence_handoff_allowed"] is False, response
assert payload["approval_review_allowed"] is False, response
assert payload["retention_review_allowed"] is False, response
assert payload["gate_closure_allowed"] is False, response
assert payload["adapter_load_allowed"] is False, response
assert payload["required_acceptance_count"] == 11, response
assert payload["blocked_acceptance_count"] == 11, response
assert payload["accepted_handoff_packet_count"] == 0, response
assert payload["acceptance_record_persisted_count"] == 0, response
assert payload["missing_handoff_packet_count"] == 11, response
assert len(payload["acceptance_rows"]) == 11, response
assert all(item["state"] == "blocked_missing_handoff_packet" for item in payload["acceptance_rows"]), response
assert all(item["handoff_packet_attached"] is False for item in payload["acceptance_rows"]), response
assert all(item["handoff_packet_acceptance_ready"] is False for item in payload["acceptance_rows"]), response
assert all(item["handoff_packet_accepted"] is False for item in payload["acceptance_rows"]), response
assert all(item["acceptance_record_persisted"] is False for item in payload["acceptance_rows"]), response
assert all(item["blocks_gate_closure"] and item["blocks_adapter_load"] for item in payload["acceptance_rows"]), response
assert all(item["passed"] for item in payload["source_surface_checks"]), response
assert {"HW-AHA-001", "HW-AHA-002", "HW-AHA-003", "HW-AHA-004", "HW-AHA-005", "HW-AHA-006", "HW-AHA-007", "HW-AHA-008"} <= gate_ids, response
source = payload["source_surfaces"]["approval_reviewer_evidence_handoff_checklist"]
assert source["handoff_checklist_complete"] is True, response
assert source["required_handoff_packet_count"] == 11, response
assert source["missing_handoff_packet_count"] == 11, response
assert source["handoff_ready"] is False, response
for key in [
    "handoff_acceptance_allowed",
    "evidence_handoff_allowed",
    "approval_review_allowed",
    "retention_review_allowed",
    "gate_closure_allowed",
    "handoff_packet_accepted",
    "acceptance_rule_accepted",
    "acceptance_record_persisted_count",
    "approval_decision_persisted",
    "review_queue_updated",
    "adapter_load_allowed",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] in (False, 0), response
for key in [
    "owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_status_active",
    "owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_checklist_active",
    "acceptance_status_complete",
    "no_side_effects_consistent",
]:
    assert payload["summary"][key] is True, response
assert payload["summary"]["blocked_acceptance_count"] == 11, response
assert payload["summary"]["accepted_handoff_packet_count"] == 0, response
assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceStatusJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-status" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.status" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceStatus" in encoded, response
assert "HW-AHA-007" in encoded and "android-linux-handoff-acceptance-parity" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_REVIEWER_EVIDENCE_HANDOFF_ACCEPTANCE_AUDIT_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-audit-consistency)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_REVIEWER_EVIDENCE_HANDOFF_ACCEPTANCE_AUDIT_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "hardware-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-audit-consistency", response
assert payload["approval_reviewer_evidence_handoff_acceptance_audit_state"] == "contract-only-handoff-acceptance-audit-consistent", response
assert payload["approval_reviewer_evidence_handoff_acceptance_audit_consistency_active"] is True, response
for key in [
    "consistency_passed",
    "handoff_checklist_consistent",
    "acceptance_status_consistent",
    "blocked_acceptance_state_consistent",
    "no_store_consistent",
    "no_review_gate_load_consistent",
    "no_side_effects_consistent",
]:
    assert payload[key] is True, response
    assert payload["summary"][key] is True, response
assert payload["required_handoff_packet_count"] == 11, response
assert payload["missing_handoff_packet_count"] == 11, response
assert payload["required_acceptance_count"] == 11, response
assert payload["blocked_acceptance_count"] == 11, response
assert payload["accepted_handoff_packet_count"] == 0, response
assert payload["acceptance_record_persisted_count"] == 0, response
assert all(item["passed"] for item in payload["audit_checks"]), response
assert {"HW-AHC-001", "HW-AHC-002", "HW-AHC-003", "HW-AHC-004", "HW-AHC-005", "HW-AHC-006", "HW-AHC-007", "HW-AHC-008"} <= gate_ids, response
source_handoff = payload["source_surfaces"]["approval_reviewer_evidence_handoff_checklist"]
source_acceptance = payload["source_surfaces"]["approval_reviewer_evidence_handoff_acceptance_status"]
assert source_handoff["handoff_checklist_complete"] is True, response
assert source_acceptance["acceptance_status_complete"] is True, response
assert source_handoff["missing_handoff_packet_count"] == 11, response
assert source_acceptance["blocked_acceptance_count"] == 11, response
for key in [
    "handoff_acceptance_allowed",
    "evidence_handoff_allowed",
    "approval_review_allowed",
    "retention_review_allowed",
    "gate_closure_allowed",
    "approval_decision_closure_allowed",
    "handoff_packet_accepted",
    "approval_decision_persisted",
    "approval_decision_review_queue_updated",
    "approval_decision_evidence_store_active",
    "review_queue_updated",
    "adapter_load_allowed",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
    "service_dispatch_triggered",
]:
    assert payload["summary"][key] is False, response
assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceAuditConsistencyJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-audit-consistency" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.audit.consistency" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceAuditConsistency" in encoded, response
assert "HW-AHC-007" in encoded and "android-linux-acceptance-audit-parity" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_REVIEWER_EVIDENCE_HANDOFF_ACCEPTANCE_DECISION_ROLLUP_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-decision-rollup)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_REVIEWER_EVIDENCE_HANDOFF_ACCEPTANCE_DECISION_ROLLUP_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "hardware-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-decision-rollup", response
assert payload["approval_reviewer_evidence_handoff_acceptance_decision_rollup_state"] == "contract-only-handoff-acceptance-decision-blocked", response
assert payload["approval_reviewer_evidence_handoff_acceptance_decision_rollup_active"] is True, response
assert payload["decision_rollup_complete"] is True, response
assert payload["decision_rollup_consistent"] is True, response
assert payload["required_decision_count"] == 8, response
assert payload["blocked_decision_count"] == 8, response
assert payload["required_handoff_packet_count"] == 11, response
assert payload["missing_handoff_packet_count"] == 11, response
assert payload["required_acceptance_count"] == 11, response
assert payload["blocked_acceptance_count"] == 11, response
assert payload["accepted_handoff_packet_count"] == 0, response
assert payload["acceptance_record_persisted_count"] == 0, response
assert all(item["decision_confirmed"] is False for item in payload["decision_rows"]), response
assert all(item["blocks_adapter_load"] is True for item in payload["decision_rows"]), response
assert {"HW-AHD-001", "HW-AHD-002", "HW-AHD-003", "HW-AHD-004", "HW-AHD-005", "HW-AHD-006", "HW-AHD-007", "HW-AHD-008"} <= gate_ids, response
assert payload["source_surfaces"]["approval_reviewer_evidence_handoff_acceptance_audit_consistency"]["consistency_passed"] is True, response
for key in [
    "acceptance_authority_confirmed",
    "acceptance_record_store_confirmed",
    "review_workflow_owner_confirmed",
    "audit_retention_owner_confirmed",
    "driver_hal_acceptance_reviewer_confirmed",
    "gate_closure_authority_confirmed",
    "handoff_packet_presence_confirmed",
    "acceptance_decision_ready",
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
    assert payload["summary"][key] is False, response
assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceDecisionRollupJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-decision-rollup" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.decision.rollup" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceDecisionRollup" in encoded, response
assert "HW-AHD-007" in encoded and "android-linux-decision-rollup-parity" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_REVIEWER_EVIDENCE_HANDOFF_ACCEPTANCE_CLOSURE_READINESS_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-checklist)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_REVIEWER_EVIDENCE_HANDOFF_ACCEPTANCE_CLOSURE_READINESS_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
check_ids = {item["check_id"] for item in payload["closure_readiness_checks"]}
assert response["status"] == "ok", response
assert payload["operation"] == "hardware-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-checklist", response
assert payload["approval_reviewer_evidence_handoff_acceptance_closure_readiness_state"] == "contract-only-handoff-acceptance-closure-not-ready", response
assert payload["approval_reviewer_evidence_handoff_acceptance_closure_readiness_checklist_active"] is True, response
assert payload["closure_readiness_complete"] is True, response
assert payload["closure_ready"] is False, response
assert payload["closure_blocker_count"] == 8, response
assert payload["required_closure_check_count"] == 8, response
assert payload["ready_closure_check_count"] == 0, response
assert payload["source_surfaces_bound"] is True, response
assert payload["decision_rollup_consistent"] is True, response
assert payload["required_decision_count"] == 8, response
assert payload["blocked_decision_count"] == 8, response
assert payload["required_handoff_packet_count"] == 11, response
assert payload["missing_handoff_packet_count"] == 11, response
assert payload["required_acceptance_count"] == 11, response
assert payload["blocked_acceptance_count"] == 11, response
assert payload["accepted_handoff_packet_count"] == 0, response
assert payload["acceptance_record_persisted_count"] == 0, response
assert all(item["ready"] is False for item in payload["closure_readiness_checks"]), response
assert all(item["blocks_adapter_load"] is True for item in payload["closure_readiness_checks"]), response
assert {"HW-AHE-001", "HW-AHE-002", "HW-AHE-003", "HW-AHE-004", "HW-AHE-005", "HW-AHE-006", "HW-AHE-007", "HW-AHE-008"} <= gate_ids, response
assert {"HW-AHE-CHECK-001", "HW-AHE-CHECK-002", "HW-AHE-CHECK-003", "HW-AHE-CHECK-004", "HW-AHE-CHECK-005", "HW-AHE-CHECK-006", "HW-AHE-CHECK-007", "HW-AHE-CHECK-008"} <= check_ids, response
assert payload["source_surfaces"]["approval_reviewer_evidence_handoff_acceptance_decision_rollup"]["decision_rollup_consistent"] is True, response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["closure_readiness_complete"] is True, response
assert payload["summary"]["closure_blocker_count"] == 8, response
assert payload["summary"]["ready_closure_check_count"] == 0, response
assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessChecklistJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-checklist" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.checklist" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessChecklist" in encoded, response
assert "HW-AHE-007" in encoded and "android-linux-closure-readiness-parity" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_REVIEWER_EVIDENCE_HANDOFF_ACCEPTANCE_CLOSURE_READINESS_AUDIT_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-audit-consistency)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_REVIEWER_EVIDENCE_HANDOFF_ACCEPTANCE_CLOSURE_READINESS_AUDIT_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
audit_ids = {item["audit_id"] for item in payload["closure_readiness_audit_rows"]}
assert response["status"] == "ok", response
assert payload["operation"] == "hardware-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-audit-consistency", response
assert payload["approval_reviewer_evidence_handoff_acceptance_closure_readiness_audit_state"] == "contract-only-handoff-acceptance-closure-readiness-audit-consistent", response
assert payload["approval_reviewer_evidence_handoff_acceptance_closure_readiness_audit_consistency_active"] is True, response
assert payload["consistency_passed"] is True, response
assert payload["closure_readiness_checklist_consistent"] is True, response
assert payload["closure_check_count_consistent"] is True, response
assert payload["closure_blocker_state_consistent"] is True, response
assert payload["decision_rollup_closure_consistent"] is True, response
assert payload["no_store_consistent"] is True, response
assert payload["no_review_gate_load_consistent"] is True, response
assert payload["no_side_effects_consistent"] is True, response
assert payload["source_surfaces_bound"] is True, response
assert payload["closure_readiness_complete"] is True, response
assert payload["closure_ready"] is False, response
assert payload["closure_blocker_count"] == 8, response
assert payload["required_closure_check_count"] == 8, response
assert payload["ready_closure_check_count"] == 0, response
assert all(item["consistent"] is True for item in payload["closure_readiness_audit_rows"]), response
assert all(item["blocks_adapter_load"] is True for item in payload["closure_readiness_audit_rows"]), response
assert {"HW-AHF-001", "HW-AHF-002", "HW-AHF-003", "HW-AHF-004", "HW-AHF-005", "HW-AHF-006", "HW-AHF-007", "HW-AHF-008"} <= gate_ids, response
assert {"HW-AHF-AUDIT-001", "HW-AHF-AUDIT-002", "HW-AHF-AUDIT-003", "HW-AHF-AUDIT-004", "HW-AHF-AUDIT-005", "HW-AHF-AUDIT-006", "HW-AHF-AUDIT-007", "HW-AHF-AUDIT-008"} <= audit_ids, response
assert payload["source_surfaces"]["approval_reviewer_evidence_handoff_acceptance_closure_readiness_checklist"]["closure_readiness_complete"] is True, response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["consistency_passed"] is True, response
assert payload["summary"]["closure_blocker_count"] == 8, response
assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessAuditConsistencyJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-audit-consistency" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.audit.consistency" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessAuditConsistency" in encoded, response
assert "HW-AHF-007" in encoded and "android-linux-closure-readiness-audit-parity" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_REVIEWER_EVIDENCE_HANDOFF_ACCEPTANCE_CLOSURE_READINESS_DECISION_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-rollup)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_REVIEWER_EVIDENCE_HANDOFF_ACCEPTANCE_CLOSURE_READINESS_DECISION_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
decision_ids = {item["decision_id"] for item in payload["decision_rows"]}
assert response["status"] == "ok", response
assert payload["operation"] == "hardware-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-rollup", response
assert payload["approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_rollup_state"] == "contract-only-handoff-acceptance-closure-readiness-decision-blocked", response
assert payload["approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_rollup_active"] is True, response
assert payload["decision_rollup_complete"] is True, response
assert payload["decision_rollup_consistent"] is True, response
assert payload["closure_readiness_audit_consistent"] is True, response
assert payload["closure_ready"] is False, response
assert payload["closure_decision_ready"] is False, response
assert payload["required_decision_count"] == 8, response
assert payload["blocked_decision_count"] == 8, response
assert payload["closure_blocker_count"] == 8, response
assert payload["required_handoff_packet_count"] == 11, response
assert payload["missing_handoff_packet_count"] == 11, response
assert payload["required_acceptance_count"] == 11, response
assert payload["blocked_acceptance_count"] == 11, response
assert payload["accepted_handoff_packet_count"] == 0, response
assert payload["acceptance_record_persisted_count"] == 0, response
assert all(item["consistent"] is True for item in payload["decision_rows"]), response
assert all(item["blocks_adapter_load"] is True for item in payload["decision_rows"]), response
assert {"HW-AHG-001", "HW-AHG-002", "HW-AHG-003", "HW-AHG-004", "HW-AHG-005", "HW-AHG-006", "HW-AHG-007", "HW-AHG-008"} <= gate_ids, response
assert {"HW-AHG-DECISION-001", "HW-AHG-DECISION-002", "HW-AHG-DECISION-003", "HW-AHG-DECISION-004", "HW-AHG-DECISION-005", "HW-AHG-DECISION-006", "HW-AHG-DECISION-007", "HW-AHG-DECISION-008"} <= decision_ids, response
assert payload["source_surfaces"]["approval_reviewer_evidence_handoff_acceptance_closure_readiness_audit_consistency"]["consistency_passed"] is True, response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["decision_rollup_consistent"] is True, response
assert payload["summary"]["closure_readiness_audit_consistent"] is True, response
assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionRollupJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-rollup" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.rollup" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionRollup" in encoded, response
assert "HW-AHG-007" in encoded and "android-linux-closure-readiness-decision-rollup-parity" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_REVIEWER_EVIDENCE_HANDOFF_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-checklist)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_REVIEWER_EVIDENCE_HANDOFF_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assignment_ids = {item["assignment_id"] for item in payload["reviewer_assignment_rows"]}
assert response["status"] == "ok", response
assert payload["operation"] == "hardware-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-checklist", response
assert payload["approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_state"] == "contract-only-handoff-acceptance-closure-readiness-decision-reviewers-unassigned", response
assert payload["approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_checklist_active"] is True, response
assert payload["reviewer_assignment_checklist_complete"] is True, response
assert payload["reviewer_assignment_ready"] is False, response
assert payload["reviewer_assignment_allowed"] is False, response
assert payload["source_decision_rollup_bound"] is True, response
assert payload["required_reviewer_assignment_count"] == 8, response
assert payload["assigned_reviewer_count"] == 0, response
assert payload["unassigned_reviewer_count"] == 8, response
assert payload["blocked_decision_count"] == 8, response
assert payload["closure_blocker_count"] == 8, response
assert payload["accepted_handoff_packet_count"] == 0, response
assert payload["acceptance_record_persisted_count"] == 0, response
assert all(item["reviewer_assigned"] is False for item in payload["reviewer_assignment_rows"]), response
assert all(item["blocks_adapter_load"] is True for item in payload["reviewer_assignment_rows"]), response
assert {"HW-AHH-001", "HW-AHH-002", "HW-AHH-003", "HW-AHH-004", "HW-AHH-005", "HW-AHH-006", "HW-AHH-007", "HW-AHH-008"} <= gate_ids, response
assert {"HW-AHH-REVIEWER-001", "HW-AHH-REVIEWER-002", "HW-AHH-REVIEWER-003", "HW-AHH-REVIEWER-004", "HW-AHH-REVIEWER-005", "HW-AHH-REVIEWER-006", "HW-AHH-REVIEWER-007", "HW-AHH-REVIEWER-008"} <= assignment_ids, response
source = payload["source_surfaces"]["approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_rollup"]
assert source["decision_rollup_complete"] is True, response
assert source["decision_rollup_consistent"] is True, response
assert source["closure_decision_ready"] is False, response
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
    assert payload["summary"][key] is False, response
assert payload["summary"]["no_assignment_side_effects"] is True, response
assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentChecklistJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-checklist" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.reviewer.assignment.checklist" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentChecklist" in encoded, response
assert "HW-AHH-007" in encoded and "android-linux-closure-decision-reviewer-assignment-parity" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_REVIEWER_EVIDENCE_HANDOFF_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_AUDIT_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-consistency)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_REVIEWER_EVIDENCE_HANDOFF_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_AUDIT_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
assert response["status"] == "ok", response
assert payload["operation"] == "hardware-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-consistency", response
assert payload["approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_audit_state"] == "contract-only-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-consistent", response
assert payload["approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency_active"] is True, response
assert payload["consistency_passed"] is True, response
assert payload["source_reviewer_assignment_checklist_bound"] is True, response
assert payload["reviewer_assignment_checklist_consistent"] is True, response
assert payload["reviewer_assignment_count_consistent"] is True, response
assert payload["reviewer_assignment_blocker_state_consistent"] is True, response
assert payload["no_store_consistent"] is True, response
assert payload["no_review_queue_gate_load_consistent"] is True, response
assert payload["no_side_effects_consistent"] is True, response
assert payload["android_linux_reviewer_assignment_audit_parity"] is True, response
assert payload["required_reviewer_assignment_count"] == 8, response
assert payload["assigned_reviewer_count"] == 0, response
assert payload["unassigned_reviewer_count"] == 8, response
assert {"HW-AHI-001", "HW-AHI-002", "HW-AHI-003", "HW-AHI-004", "HW-AHI-005", "HW-AHI-006", "HW-AHI-007", "HW-AHI-008"} <= gate_ids, response
source = payload["source_surfaces"]["approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_checklist"]
assert source["reviewer_assignment_checklist_complete"] is True, response
assert source["reviewer_assignment_ready"] is False, response
assert source["assigned_reviewer_count"] == 0, response
assert source["unassigned_reviewer_count"] == 8, response
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
    assert payload["summary"][key] is False, response
assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistencyJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-consistency" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.reviewer.assignment.audit.consistency" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistency" in encoded, response
assert "HW-AHI-007" in encoded and "android-linux-reviewer-assignment-audit-parity" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_REVIEWER_EVIDENCE_HANDOFF_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_AUDIT_DECISION_ROLLUP_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_REVIEWER_EVIDENCE_HANDOFF_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_AUDIT_DECISION_ROLLUP_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
decision_ids = {item["decision_id"] for item in payload["decision_rows"]}
summary = payload["summary"]
assert response["status"] == "ok", response
assert payload["operation"] == "hardware-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup", response
assert payload["approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_state"] == "contract-only-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-blocked", response
assert payload["approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_active"] is True, response
assert summary["decision_rollup_complete"] is True and summary["decision_rollup_consistent"] is True, response
assert summary["source_reviewer_assignment_audit_bound"] is True and summary["reviewer_assignment_audit_consistent"] is True, response
assert summary["reviewer_assignment_decision_blocked"] is True, response
assert summary["adapter_load_decision"] == "blocked-by-unassigned-reviewers", response
assert summary["required_decision_count"] == 8 and summary["blocked_decision_count"] == 8, response
assert summary["assigned_reviewer_count"] == 0 and summary["unassigned_reviewer_count"] == 8, response
assert {"HW-AHJ-001", "HW-AHJ-002", "HW-AHJ-003", "HW-AHJ-004", "HW-AHJ-005", "HW-AHJ-006", "HW-AHJ-007", "HW-AHJ-008"} <= gate_ids, response
assert {"HW-AHJ-DECISION-001", "HW-AHJ-DECISION-002", "HW-AHJ-DECISION-003", "HW-AHJ-DECISION-004", "HW-AHJ-DECISION-005", "HW-AHJ-DECISION-006", "HW-AHJ-DECISION-007", "HW-AHJ-DECISION-008"} <= decision_ids, response
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
    assert summary[key] is False, response
assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollup" in encoded, response
assert "HW-AHJ-007" in encoded and "android-linux-reviewer-assignment-audit-decision-rollup-parity" in encoded, response
assert "HW-002" in encoded and "KH-003" in encoded and "DEL-005" in encoded, response
PY
HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_REVIEWER_EVIDENCE_HANDOFF_ACCEPTANCE_CLOSURE_HANDOFF_READINESS_SUMMARY_OUTPUT="$(CENTRAL_BRAIN_IPC_SOCKET="$SOCKET_PATH" python3 "$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py" hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-summary)"
python3 - "$HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_APPROVAL_REVIEWER_EVIDENCE_HANDOFF_ACCEPTANCE_CLOSURE_HANDOFF_READINESS_SUMMARY_OUTPUT" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
payload = response["payload"]["gateway"]["payload"]
encoded = json.dumps(payload)
gate_ids = {item["gate_id"] for item in payload["mandatory_gates"]}
dependency_ids = {item["dependency_id"] for item in payload["handoff_dependencies"]}
summary = payload["summary"]
assert response["status"] == "ok", response
assert payload["operation"] == "hardware-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-summary", response
assert payload["closure_handoff_readiness_summary_state"] == "contract-only-closure-handoff-readiness-blocked", response
assert payload["closure_handoff_readiness_summary_active"] is True, response
assert summary["closure_handoff_readiness_complete"] is True and summary["closure_handoff_ready"] is False and summary["handoff_ready"] is False, response
assert summary["source_decision_rollup_bound"] is True and summary["decision_rollup_consistent"] is True, response
assert summary["reviewer_assignment_decision_blocked"] is True, response
assert summary["adapter_load_decision"] == "blocked-by-unassigned-reviewers", response
assert summary["required_handoff_dependency_count"] == 8 and summary["open_handoff_dependency_count"] == 8, response
assert summary["assigned_reviewer_count"] == 0 and summary["unassigned_reviewer_count"] == 8, response
assert {"HW-AHK-001", "HW-AHK-002", "HW-AHK-003", "HW-AHK-004", "HW-AHK-005", "HW-AHK-006", "HW-AHK-007", "HW-AHK-008"} <= gate_ids, response
assert {"HW-AHK-HANDOFF-001", "HW-AHK-HANDOFF-002", "HW-AHK-HANDOFF-003", "HW-AHK-HANDOFF-004", "HW-AHK-HANDOFF-005", "HW-AHK-HANDOFF-006", "HW-AHK-HANDOFF-007", "HW-AHK-HANDOFF-008"} <= dependency_ids, response
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
    assert summary[key] is False, response
assert "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummaryJson" in encoded, response
assert "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-summary" in encoded, response
assert "hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.summary" in encoded, response
assert "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummary" in encoded, response
assert "HW-AHK-007" in encoded and "android-linux-closure-handoff-readiness-summary-parity" in encoded, response
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
