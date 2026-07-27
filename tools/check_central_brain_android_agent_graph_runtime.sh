#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-GRF-001, NV-G-004/006/007, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRAPH_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph"
RUNTIME="$GRAPH_ROOT/AgentGraphRuntime.java"
GRAPH_STATE="$GRAPH_ROOT/GraphRunState.java"
NODE_STATE="$GRAPH_ROOT/NodeRunState.java"
REGISTRY="$GRAPH_ROOT/NodeExecutorRegistry.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/graph/AgentGraphRuntimeTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/graph/AgentGraphRuntimeProbeActivity.java"
MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_text() {
  if [[ "$1" == "README.md" || "$1" == "$ROOT_DIR/README.md" ]]; then
    grep -Fq -- 'docs/CENTRAL_BRAIN_REQUIREMENTS.md' "$ROOT_DIR/README.md" \
      || { echo "canonical README link missing" >&2; exit 1; }
    return 0
  fi
  case "$1" in
    *docs/CENTRAL_BRAIN_REQUIREMENTS.md|*docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md|*docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md)
      local canonical_doc_path="$1"
      [[ "$canonical_doc_path" = /* ]] || canonical_doc_path="$ROOT_DIR/$canonical_doc_path"
      grep -Fq -- 'production_document_scope=true' "$canonical_doc_path" \
        || { echo "canonical production document marker missing: $canonical_doc_path" >&2; exit 1; }
      return 0
      ;;
  esac
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P3-W01 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$RUNTIME" "$GRAPH_STATE" "$NODE_STATE" "$REGISTRY" \
    "$TEST" "$PROBE" "$MANIFEST" "$MAIN_MANIFEST" "$RUNTIME_SERVICE" \
    "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P3-W01 file missing: $file" >&2; exit 1; }
done

for state in CREATED PLANNING WAITING EXECUTING PARTIAL COMPENSATING \
    COMPLETED FAILED CANCELLED STUCK; do
  require_text "$GRAPH_STATE" "$state"
done
for state in PENDING READY EXECUTING WAITING SUCCEEDED FAILED SKIPPED \
    CANCELLED COMPENSATING COMPENSATED STUCK; do
  require_text "$NODE_STATE" "$state"
done

for marker in \
  'public final class AgentGraphRuntime' \
  'MAX_RUN_RECORDS = 64' \
  'MAX_CONCURRENT_SESSIONS = 8' \
  'MAX_EVENT_PROJECTION = 256' \
  'PlanGraphValidator.validateTransport' \
  'claimNextReadyNode' \
  'suspendClaimedNode' \
  'completeClaimedNode' \
  'activeRunBySession' \
  'PLAN_DEADLINE_EXCEEDED' \
  'eventDigest' \
  'isExecutorDispatchEnabled()' \
  'isProductionAuthorized()'; do
  require_text "$RUNTIME" "$marker"
done

for marker in \
  'public final class NodeExecutorRegistry' \
  'controlOnlyContractRegistry' \
  'PlanContract.allowedNodeTypes()' \
  'registry must remain control-only' \
  'return false;'; do
  require_text "$REGISTRY" "$marker"
done

for test_name in \
  graphAndNodeStateTablesRejectIllegalAndTerminalTransitions \
  linearDagCompletesDeterministicallyWithoutExecutorDispatch \
  sameSessionIsFifoWhileDifferentSessionsUseBoundedSlots \
  optionalFailureContinuesOnTerminalDependencyAndEndsPartial \
  requiredFailureFailsClosedAndInvalidSkipDoesNotLoseClaim \
  waitingNodeCanResumeAndDeadlineCancelsOpenWork \
  registryCapacityAndEventProjectionAreBounded; do
  require_text "$TEST" "$test_name"
done

for marker in \
  agent_graph_runtime_probe_complete \
  agent_graph_runtime_defined \
  agent_graph_state_transition_verified \
  agent_graph_same_session_fifo_verified \
  agent_graph_cross_session_bounded_verified \
  agent_graph_partial_terminal_verified \
  agent_graph_deadline_verified \
  agent_graph_compensation_fail_closed_verified \
  agent_graph_event_projection_bounded \
  agent_graph_android13_arm64_verified; do
  require_text "$PROBE" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
for marker in \
  agent_graph_executor_dispatch_enabled=false \
  agent_graph_runtime_production_wired=false \
  effect_dispatch_enabled=false \
  model_invoked=false \
  hardware_accessed=false; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

require_text "$MANIFEST" '.graph.AgentGraphRuntimeProbeActivity'
if grep -R -Fq 'AgentGraphRuntime' \
    "$ROOT_DIR/$MAIN_MANIFEST" \
    "$ROOT_DIR/$RUNTIME_SERVICE" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"; then
  echo "P3-W01 Graph Runtime was wired into a production manifest or Binder service" >&2
  exit 1
fi
if grep -R -Eiq \
    'EffectAdapter|SimulatedEffect|ModelProvider|InferenceResourceScheduler|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|java[.]net|okhttp|http://|https://|ioctl|sysfs|/dev/|ExecutorService|Executors[.]' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GRAPH_STATE" "$ROOT_DIR/$NODE_STATE"; then
  echo "P3-W01 Graph Runtime unexpectedly references dispatch, model, vehicle, network, thread-pool, or hardware APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P3-W01 Agent Graph Runtime state machine"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" '`P3-W01` AgentGraphRuntime state machine'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P3-W01 Agent Graph Runtime trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android P3-W01 Agent Graph Runtime"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android P3-W01 Agent Graph Runtime"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P3-W01 Agent Graph Runtime Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P3-W01 Graph Runtime"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P3-W01 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P3-W01 AgentGraphRuntime"

printf '%s\n' \
  "Central Brain Android Agent Graph Runtime check passed" \
  "agent_graph_runtime_defined=true" \
  "agent_graph_state_machine_verified=true" \
  "agent_graph_same_session_fifo_verified=true" \
  "agent_graph_cross_session_bounded_verified=true" \
  "agent_graph_compensation_fail_closed_verified=true" \
  "agent_graph_event_projection_bounded=true" \
  "agent_graph_executor_dispatch_enabled=false" \
  "agent_graph_runtime_production_wired=false" \
  "effect_dispatch_enabled=false" \
  "model_invoked=false" \
  "hardware_accessed=false"
