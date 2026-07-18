#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SCN-001, S2-GRF-001, S2-EVT-001, S2-EFF-001,
# S2-HMI-003/006, APP-004, XSC-001/005/006, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/scenario/SimulatedScenarioRuntime.java"
TEST="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/scenario/SimulatedScenarioRuntimeTest.java"
CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_p4_d4b_simulated_scenario_runtime.json"

for file in "$SOURCE" "$TEST" "$CONTRACT"; do
  [[ -f "$file" ]] || { echo "P4-D4b artifact missing: $file" >&2; exit 1; }
done

python3 -B - "$CONTRACT" <<'PY'
import json
import pathlib
import sys

contract = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
assert contract["schema_version"] == 1
assert contract["profile_id"] == "android13-p4-d4b-simulated-scenario-runtime-v1"
assert contract["source_set"] == "debug"
assert contract["maximum_session_count"] == 16
assert contract["retained_event_limit"] == 64
assert contract["published_topic_ids"] == [
    "runtime.task.state", "governance.policy.decision"
]
assert contract["event_schema_ids"] == [
    "cougaros.sim.plan.published.v1",
    "cougaros.sim.outcome.supplied.v1",
    "cougaros.sim.pending.approval.v1",
    "cougaros.sim.pending.effect.v1",
    "cougaros.sim.pending.readback.v1",
    "cougaros.sim.session.completed.v1",
    "cougaros.sim.session.failed.v1",
    "cougaros.sim.session.cancelled.v1",
]
projection = contract["projection"]
for key in (
    "session_identity_exposed", "plan_identity_exposed", "graph_state_exposed",
    "pending_stage_exposed", "pending_node_id_exposed",
    "pending_capability_id_exposed", "event_sequence_exposed",
    "event_schema_exposed", "event_payload_digest_only",
    "stable_session_projection_digest_exposed",
):
    assert projection[key] is True
for key in (
    "raw_user_or_model_text_exposed", "vehicle_payload_exposed",
    "device_identity_exposed",
):
    assert projection[key] is False
claims = contract["claim_state"]
for key in (
    "simulated_scenario_debug_runtime_projection_defined",
    "simulated_scenario_debug_runtime_wired",
    "simulated_scenario_session_projection_enabled",
    "simulated_scenario_event_projection_enabled",
    "simulated_scenario_process_local",
):
    assert claims[key] is True
assert claims["simulated_scenario_event_topic_count"] == 2
assert claims["simulated_scenario_event_schema_count"] == 8
for key in (
    "simulated_scenario_android_runtime_wired",
    "simulated_scenario_android_service_published",
    "simulated_scenario_session_event_binder_published",
    "simulated_scenario_client2_wired",
    "simulated_scenario_effect_dispatch_enabled",
    "simulated_scenario_readback_accessed",
    "simulated_scenario_approval_authority_available",
    "simulated_scenario_production_registered",
    "scenario_execution_enabled", "hardware_accessed", "production_ready",
    "target_hardware_validated",
):
    assert claims[key] is False
assert claims["implementation_stage"] == "P4-D4b"
PY

for marker in \
  'class SimulatedScenarioRuntime' \
  'new SimulatedScenarioGraph(clock)' \
  'BoundedEventRuntime.createForContractTest' \
  'TOPIC_TASK_STATE' \
  'TOPIC_POLICY_DECISION' \
  'SCHEMA_PLAN_PUBLISHED' \
  'SCHEMA_PENDING_APPROVAL' \
  'SCHEMA_PENDING_EFFECT' \
  'SCHEMA_PENDING_READBACK' \
  'SCHEMA_SESSION_COMPLETED' \
  'SCHEMA_SESSION_FAILED' \
  'SCHEMA_SESSION_CANCELLED' \
  'isDebugRuntimeWired()' \
  'isAndroidServicePublished()' \
  'isSessionEventBinderPublished()' \
  'isEffectDispatchEnabled()' \
  'isReadbackAccessed()' \
  'isHardwareAccessed()'; do
  grep -Fq -- "$marker" "$SOURCE" \
    || { echo "P4-D4b source marker missing: $marker" >&2; exit 1; }
done

for test_name in \
  coldStartPublishesPlanAndEffectPendingSessionProjection \
  retainedPlanAndEffectEventsAreDeliveredThroughExistingEventRuntime \
  parkedFatigueApprovalUsesPolicyTopic \
  suppliedOutcomePublishesProgressAndMovesToReadback \
  explicitOutcomesCompleteSessionWithoutDispatchOrHardware \
  failedAndCancelledRunsProjectDistinctTerminalStates \
  projectionDigestIsDeterministicAndBindsEventProgress \
  debugRuntimeClaimsRemainProcessLocalAndUnpublished; do
  grep -Fq -- "$test_name" "$TEST" \
    || { echo "P4-D4b JVM test missing: $test_name" >&2; exit 1; }
done

if find "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/release" \
    -type f -print 2>/dev/null | xargs -r grep -l 'SimulatedScenarioRuntime'; then
  echo "P4-D4b runtime leaked into main/release source" >&2
  exit 1
fi

if grep -Eq 'import android\.|java\.io\.|java\.net\.|ProcessBuilder|Runtime\.getRuntime|PackageManager|VehicleProperty|Npu' "$SOURCE"; then
  echo "P4-D4b projection contains Android, IO, network, process, vehicle or NPU access" >&2
  exit 1
fi

for doc_marker in \
  'P4-D4b Simulated Scenario Runtime' \
  'simulated_scenario_debug_runtime_projection_defined=true' \
  'simulated_scenario_android_service_published=true' \
  'simulated_scenario_session_event_binder_published=true' \
  'simulated_scenario_effect_dispatch_enabled=true' \
  'simulated_scenario_readback_accessed=true' \
  'implementation_stage=P4-D4d'; do
  grep -Fq -- "$doc_marker" "$ROOT_DIR/README.md" \
    || { echo "P4-D4b README marker missing: $doc_marker" >&2; exit 1; }
done

printf '%s\n' \
  'Central Brain Android P4-D4b simulated scenario runtime check passed' \
  'simulated_scenario_debug_runtime_projection_defined=true' \
  'simulated_scenario_debug_runtime_wired=true' \
  'simulated_scenario_session_projection_enabled=true' \
  'simulated_scenario_event_projection_enabled=true' \
  'simulated_scenario_event_topic_count=2' \
  'simulated_scenario_event_schema_count=8' \
  'simulated_scenario_process_local=true' \
  'simulated_scenario_android_runtime_wired=false' \
  'simulated_scenario_android_service_published=false' \
  'simulated_scenario_session_event_binder_published=false' \
  'simulated_scenario_client2_wired=false' \
  'simulated_scenario_effect_dispatch_enabled=false' \
  'simulated_scenario_readback_accessed=false' \
  'simulated_scenario_approval_authority_available=false' \
  'simulated_scenario_production_registered=false' \
  'scenario_execution_enabled=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
