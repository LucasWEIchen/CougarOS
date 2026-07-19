#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SCN-001, S2-GRF-001, S2-EFF-001, S2-SAF-001, S2-UX-003,
# NV-F-001, NV-G-004..007, XSC-001/005/006, DEL-001/003/004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AIDL_DIR="central-brain/android-runtime/central-brain-sdk/src/main/aidl/com/centralbrain/sdk/orchestration"
SDK_DIR="central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk"
RUNTIME_DIR="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime"
DEBUG_DIR="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime"
RELEASE_DIR="central-brain/android-runtime/runtime-service/src/release/java/com/centralbrain/runtime"
CONTRACT="central-brain/contracts/central_brain_android_orchestration_v1.json"
HASH_MANIFEST="central-brain/android-runtime/central-brain-sdk/aidl-api/orchestration-v1.sha256"
PROBE="$DEBUG_DIR/persistence/OrchestrationRuntimeProbeActivity.java"

require_file() {
  [[ -f "$ROOT_DIR/$1" ]] \
    || { echo "missing Orchestration V1 file: $1" >&2; exit 1; }
}

require_text() {
  grep -Fq -- "$2" "$ROOT_DIR/$1" \
    || { echo "missing Orchestration V1 marker '$2' in $1" >&2; exit 1; }
}

AIDL_TYPES=(
  ApprovalResponse
  ICentralBrainOrchestration
  OrchestrationEffect
  OrchestrationNode
  OrchestrationSnapshot
  OrchestrationStartRequest
  UndoRequest
)
for name in "${AIDL_TYPES[@]}"; do
  require_file "$AIDL_DIR/$name.aidl"
done

INTERFACE="$AIDL_DIR/ICentralBrainOrchestration.aidl"
for marker in \
  'const int INTERFACE_VERSION = 1;' \
  'OrchestrationSnapshot start(in OrchestrationStartRequest request);' \
  'ScenarioPlan getPlan(String sessionId);' \
  'OrchestrationSnapshot respondToApproval(in ApprovalResponse response);' \
  'OrchestrationSnapshot requestUndo(in UndoRequest request);' \
  'OrchestrationSnapshot cancel(String sessionId, int reasonCode);'; do
  require_text "$INTERFACE" "$marker"
done

declared_hash="$(
  sed -nE 's/.*INTERFACE_HASH = "([0-9a-f]{64})";.*/\1/p' "$ROOT_DIR/$INTERFACE"
)"
computed_hash="$({
  for name in "${AIDL_TYPES[@]}"; do
    sed -E \
      's/const String INTERFACE_HASH = "[0-9a-f]{64}";/const String INTERFACE_HASH = "<generated-by-checker>";/' \
      "$ROOT_DIR/$AIDL_DIR/$name.aidl"
  done
} | sha256sum | awk '{print $1}')"
[[ "$declared_hash" == "$computed_hash" ]] \
  || { echo "Orchestration V1 hash drift: declared=$declared_hash computed=$computed_hash" >&2; exit 1; }

require_file "$HASH_MANIFEST"
(cd "$ROOT_DIR" && sha256sum -c "$HASH_MANIFEST" >/dev/null)
for manifest in v1.sha256 governance-v1.sha256 session-v1.sha256 \
    plan-v1.sha256 events-v1.sha256 events-v2.sha256 effect-v1.sha256; do
  (cd "$ROOT_DIR" && sha256sum -c \
    "central-brain/android-runtime/central-brain-sdk/aidl-api/$manifest" >/dev/null)
done

for file in \
  "$SDK_DIR/orchestration/OrchestrationContract.java" \
  "$SDK_DIR/OrchestrationClient.java" \
  "$RUNTIME_DIR/orchestration/OrchestrationBackend.java" \
  "$RUNTIME_DIR/orchestration/OrchestrationEndpoint.java" \
  "$RUNTIME_DIR/persistence/DurableOrchestrationProjectionRepository.java" \
  "$DEBUG_DIR/orchestration/DebugSimulatedOrchestrationBackend.java" \
  "$DEBUG_DIR/orchestration/OrchestrationBackendFactory.java" \
  "$RELEASE_DIR/orchestration/OrchestrationBackendFactory.java" \
  "$RUNTIME_DIR/CentralBrainRuntimeService.java" \
  "$PROBE" \
  "$CONTRACT"; do
  require_file "$file"
done

require_text "$SDK_DIR/CentralBrainSdk.java" \
  'ACTION_ORCHESTRATION ='
for marker in \
  'getProtocolVersion()' \
  'getProtocolHash()' \
  'validateStartRequest(' \
  'validateSnapshot(' \
  'PlanContract.validatePlan(' \
  'respondToApproval(' \
  'requestUndo('; do
  require_text "$SDK_DIR/OrchestrationClient.java" "$marker"
done
for marker in \
  'findSessionOwned(' \
  'commitOwned(' \
  'reconcileInterrupted()' \
  'commitListener.onCommitted('; do
  require_text "$RUNTIME_DIR/orchestration/OrchestrationEndpoint.java" "$marker"
done
require_text "$RUNTIME_DIR/session/TransientSessionEndpoint.java" \
  'dispatchCommittedOwned('
for marker in \
  'graphRepository.persistInitial(' \
  'appendEventIfChanged(' \
  'new Evidence(false, Collections.emptyMap(), Collections.emptyMap())' \
  'getTargetGraphState() == GraphRunState.STUCK'; do
  require_text "$RUNTIME_DIR/persistence/DurableOrchestrationProjectionRepository.java" "$marker"
done
require_text "$RELEASE_DIR/orchestration/OrchestrationBackendFactory.java" \
  'return new FailClosedOrchestrationBackend();'
for marker in \
  'PROFILE_DEBUG_SIMULATION' \
  'approvalAuthorityTrusted = false' \
  'UNDO_AUTHORITY_UNAVAILABLE' \
  'hardwareAccessed = false' \
  'productionReady = false'; do
  require_text "$DEBUG_DIR/orchestration/DebugSimulatedOrchestrationBackend.java" "$marker"
done
for marker in \
  'ACTION_ORCHESTRATION.equals(action)' \
  'orchestration_runtime_service_published=true' \
  'startupOrchestrationReconciliation = executor.submit(' \
  'orchestrationEndpoint.reconcileInterrupted()' \
  'awaitOrchestrationStartupReconciliation();'; do
  require_text "$RUNTIME_DIR/CentralBrainRuntimeService.java" "$marker"
done
for marker in \
  'orchestration_v1_probe_complete=true' \
  'orchestration_room_projection_verified=true' \
  'orchestration_restart_stuck_verified=true' \
  'orchestration_restart_effect_replay_count=0' \
  'approval_authority_trusted=false' \
  'undo_authority_available=false'; do
  require_text "$PROBE" "$marker"
done
require_text \
  'central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml' \
  '.persistence.OrchestrationRuntimeProbeActivity'

python3 - "$ROOT_DIR/$CONTRACT" "$declared_hash" <<'PY'
import json
import sys

path, expected_hash = sys.argv[1:]
with open(path, encoding="utf-8") as handle:
    contract = json.load(handle)
assert contract["binder"]["interface_version"] == 1
assert contract["binder"]["interface_hash"] == expected_hash
assert contract["binder"]["aidl_type_count"] == 7
assert contract["binder"]["owner_session_scoped"] is True
assert contract["persistence"]["restart_unfinished_to_stuck"] is True
assert contract["persistence"]["restart_effect_replay_enabled"] is False
assert contract["persistence"]["full_plan_rehydration_after_restart"] is False
assert contract["authority"]["release_backend_fail_closed"] is True
assert contract["authority"]["approval_response_is_grant"] is False
state = contract["claim_state"]
assert state["orchestration_v1_interface_published"] is True
assert state["orchestration_runtime_service_published"] is True
assert state["orchestration_debug_probe_executed"] is False
assert state["orchestration_android13_arm64_verified"] is False
assert state["hardware_accessed"] is False
assert state["production_ready"] is False
assert state["target_hardware_validated"] is False
assert state["implementation_stage"] == "P4-R1"
PY

for doc in \
  README.md \
  docs/CENTRAL_BRAIN_ROADMAP.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md \
  docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md \
  docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md \
  docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md \
  docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md \
  docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md; do
  require_text "$doc" 'P4-R1'
done

printf '%s\n' \
  'Central Brain Android Orchestration V1 check passed' \
  'orchestration_v1_interface_published=true' \
  'orchestration_runtime_service_published=true' \
  'orchestration_sdk_negotiation_wired=true' \
  'orchestration_room_projection_wired=true' \
  'orchestration_restart_stuck_no_replay_wired=true' \
  'orchestration_debug_probe_available=true' \
  'orchestration_debug_probe_executed=false' \
  'orchestration_android13_arm64_verified=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'implementation_stage=P4-R1'
