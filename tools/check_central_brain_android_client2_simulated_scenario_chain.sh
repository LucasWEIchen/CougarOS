#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SCN-001, S2-GRF-001, S2-EVT-001, S2-EFF-001,
# S2-SAF-001, S2-HMI-003/006, APP-004, XSC-001/004/005/006, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="$ROOT_DIR/apk-labs/client2-central-brain"
RUNTIME_SNAPSHOT="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/scenario/SimulatedScenarioBinderSnapshot.java"
CLIENT_SNAPSHOT="$PROJECT/bridge/src/com/centralbrain/runtime/scenario/SimulatedScenarioBinderSnapshot.java"
CLIENT="$PROJECT/bridge/src/com/centralbrain/client2/SimulatedScenarioRuntimeClient.java"
STATE="$PROJECT/bridge/src/com/centralbrain/client2/CockpitSimulatedScenarioState.java"
REDUCER="$PROJECT/bridge/src/com/centralbrain/client2/CockpitHmiReducer.java"
TIMELINE="$PROJECT/bridge/src/com/centralbrain/client2/CockpitExecutionTimeline.java"
COORDINATOR="$PROJECT/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
PROJECT_JSON="$PROJECT/client2-central-brain.project.json"
DEVICE_TEST="$ROOT_DIR/tools/test_client2_central_brain_simulated_scenario_chain.sh"
CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_p4_d4e_client2_scenario_chain.json"

for file in "$RUNTIME_SNAPSHOT" "$CLIENT_SNAPSHOT" "$CLIENT" "$STATE" "$REDUCER" \
    "$TIMELINE" "$COORDINATOR" "$PROJECT_JSON" "$DEVICE_TEST" "$CONTRACT"; do
  [[ -f "$file" ]] || { echo "P4-D4e artifact missing: $file" >&2; exit 1; }
done

python3 -B - "$CONTRACT" "$RUNTIME_SNAPSHOT" "$CLIENT_SNAPSHOT" "$PROJECT_JSON" <<'PY'
import json
import pathlib
import sys

contract = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
assert contract["schema_version"] == 1
assert contract["profile_id"] == "android13-p4-d4e-client2-scenario-chain-v1"
assert contract["source_set"] == "debug_reverse_demo"
assert contract["binder_protocol_version"] == 2
assert contract["binder_parcel_schema_version"] == 2
assert contract["scenario_bindings"] == {
    "care.cold": "scene.comfort.cold.v1",
    "care.fatigue": "scene.fatigue.assist.v1",
}
assert contract["approval_binding"] == {
    "pending_node_id": "request_seat_approval",
    "pending_capability_id_on_wire": "",
    "hmi_target_id": "vehicle.seat.recline",
    "authority": "simulation_only",
}
assert contract["hmi_stages"] == [
    "Intent", "Context", "Plan", "Policy", "Graph", "Effect", "Readback",
]
assert contract["projection_limits"] == {
    "maximum_effect_count": 16,
    "maximum_event_count": 64,
    "maximum_graph_revision": 1000000,
}
observation = contract["android13_arm64_observation"]
assert observation == {
    "executed": True,
    "scenario_path_count": 3,
    "cold_effect_dispatch_count": 3,
    "cold_readback_match_count": 3,
    "fatigue_approved_effect_dispatch_count": 5,
    "fatigue_approved_readback_match_count": 3,
    "fatigue_rejected_effect_dispatch_count": 4,
    "fatigue_rejected_readback_match_count": 2,
    "approval_input_count": 2,
    "seven_stage_ui_verified": True,
    "raw_payload_logged": False,
    "device_identity_logged": False,
}
claims = contract["claim_state"]
for key in (
    "simulated_scenario_client2_wired",
    "simulated_scenario_client_parcel_wire_verified",
    "simulated_scenario_projection_reducer_owned",
    "simulated_scenario_seven_stage_ui_verified",
    "simulated_scenario_effect_dispatch_enabled",
    "simulated_scenario_readback_accessed",
    "simulated_scenario_approval_input_explicit",
    "simulated_scenario_android13_arm64_client_verified",
    "hmi_d4_debug_demo_control_loop_complete",
):
    assert claims[key] is True
for key in (
    "simulated_scenario_hardware_effect_dispatch_enabled",
    "simulated_scenario_approval_authority_available",
    "simulated_scenario_production_registered",
    "client2_production_release_artifact_available",
    "scenario_execution_enabled", "hardware_accessed",
    "production_ready", "target_hardware_validated",
):
    assert claims[key] is False
assert claims["implementation_stage"] == "P4-D4e"

fields = contract["parcel_field_order"]
read_expr = {
    "schemaVersion": "schemaVersion = input.readInt();",
    "runId": "runId = nonNull(input.readString());",
    "sessionId": "sessionId = nonNull(input.readString());",
    "scenarioId": "scenarioId = nonNull(input.readString());",
    "planDigest": "planDigest = nonNull(input.readString());",
    "planRevision": "planRevision = input.readInt();",
    "sessionState": "sessionState = input.readInt();",
    "graphRevision": "graphRevision = input.readLong();",
    "automaticProjectionCount": "automaticProjectionCount = input.readInt();",
    "suppliedOutcomeCount": "suppliedOutcomeCount = input.readInt();",
    "pendingStage": "pendingStage = input.readInt();",
    "pendingNodeId": "pendingNodeId = nonNull(input.readString());",
    "pendingCapabilityId": "pendingCapabilityId = nonNull(input.readString());",
    "lastEventSequence": "lastEventSequence = input.readLong();",
    "projectedEventCount": "projectedEventCount = input.readInt();",
    "projectionDigest": "projectionDigest = nonNull(input.readString());",
    "simulatedEffectDispatchCount": "simulatedEffectDispatchCount = input.readInt();",
    "simulatedReadbackAttemptCount": "simulatedReadbackAttemptCount = input.readInt();",
    "simulatedReadbackMatchCount": "simulatedReadbackMatchCount = input.readInt();",
    "simulatedApprovalInputCount": "simulatedApprovalInputCount = input.readInt();",
    "simulatedFailureCount": "simulatedFailureCount = input.readInt();",
    "effectDispatchEnabled": "effectDispatchEnabled = input.readInt() != 0;",
    "readbackAccessed": "readbackAccessed = input.readInt() != 0;",
    "approvalAuthorityAvailable": "approvalAuthorityAvailable = input.readInt() != 0;",
    "hardwareAccessed": "hardwareAccessed = input.readInt() != 0;",
    "productionReady": "productionReady = input.readInt() != 0;",
    "targetHardwareValidated": "targetHardwareValidated = input.readInt() != 0;",
}
write_expr = {
    field: "output.writeInt(%s ? 1 : 0);" % field
    if field in {
        "effectDispatchEnabled", "readbackAccessed", "approvalAuthorityAvailable",
        "hardwareAccessed", "productionReady", "targetHardwareValidated",
    }
    else "output.writeLong(%s);" % field
    if field in {"graphRevision", "lastEventSequence"}
    else "output.writeString(%s);" % field
    if field in {
        "runId", "sessionId", "scenarioId", "planDigest", "pendingNodeId",
        "pendingCapabilityId", "projectionDigest",
    }
    else "output.writeInt(%s);" % field
    for field in fields
}
for source_path in sys.argv[2:4]:
    source = pathlib.Path(source_path).read_text(encoding="utf-8")
    cursor = -1
    for field in fields:
        cursor = source.find(read_expr[field], cursor + 1)
        assert cursor >= 0, (source_path, "read", field)
    cursor = -1
    for field in fields:
        cursor = source.find(write_expr[field], cursor + 1)
        assert cursor >= 0, (source_path, "write", field)

project = json.loads(pathlib.Path(sys.argv[4]).read_text(encoding="utf-8"))
assert project["version"] == "0.18.0"
assert any("DEV-105" in item for item in project["tracked_deviations"])
PY

for marker in \
  'Math.toIntExact(snapshot.graphRevision)' \
  'request_seat_approval' \
  'vehicle.seat.recline' \
  'CB_SIM_SCENARIO_APPROVAL_PROJECTION' \
  'CB_SIM_SCENARIO_START_PROJECTION'; do
  grep -Fq -- "$marker" "$CLIENT" \
    || { echo "P4-D4e client validation marker missing: $marker" >&2; exit 1; }
done
for marker in \
  'MAX_REVISION = 1_000_000' \
  'CockpitSimulatedScenarioState' \
  'simulatedScenario' \
  'CockpitExecutionTimeline.Phase.READBACK' \
  'cockpit_simulated_scenario_binder_v2_wired=true' \
  'cockpit_simulated_scenario_effect_dispatch_enabled=true' \
  'cockpit_simulated_hardware_effect_dispatch_enabled=false'; do
  grep -Fq -- "$marker" "$STATE" "$REDUCER" "$TIMELINE" "$COORDINATOR" \
    || { echo "P4-D4e HMI marker missing: $marker" >&2; exit 1; }
done
for marker in \
  'client2_simulated_cold_completed_verified=true' \
  'client2_simulated_fatigue_approved_completed_verified=true' \
  'client2_simulated_fatigue_rejected_partial_verified=true' \
  'client2_simulated_seven_stage_ui_verified=true' \
  'client2_simulated_hardware_effect_dispatch_enabled=false'; do
  grep -Fq -- "$marker" "$DEVICE_TEST" \
    || { echo "P4-D4e device-test marker missing: $marker" >&2; exit 1; }
done

HMI_OUTPUT="$(bash "$ROOT_DIR/tools/check_central_brain_android_client2_hmi_reducer.sh")"
for marker in \
  'cockpit_simulated_scenario_projection_reducer_owned=true' \
  'cockpit_simulated_scenario_seven_stage_projection_verified=true' \
  'cockpit_simulated_scenario_partial_projection_verified=true' \
  'cockpit_simulated_graph_revision_bound_independent=true'; do
  grep -Fq -- "$marker" <<<"$HMI_OUTPUT" \
    || { echo "P4-D4e reducer output missing: $marker" >&2; exit 1; }
done

for marker in \
  '`P4-D4e` Client2 simulated scenario chain' \
  'P4-D4e Client2 scenario-chain UI wiring trace' \
  'DEV-105 P4-D4e Client2 debug loop is not production vehicle execution' \
  'ISSUE-033 P4-D4e update' \
  'Android P4-D4e Client2 Simulated Scenario Chain' \
  'P4-D4e Client2 Simulated Scenario Chain Driver/HAL Boundary' \
  'P4-D4e Client2 scenario-chain interface' \
  'P4-D4e Client2 scenario-chain architecture' \
  'P4-D4e Client2 Simulated Scenario Chain detailed design'; do
  grep -FRq -- "$marker" "$ROOT_DIR/docs" \
    || { echo "P4-D4e documentation marker missing: $marker" >&2; exit 1; }
done
for marker in \
  'P4-D4e Client2 Simulated Scenario Chain' \
  'simulated_scenario_client2_wired=true' \
  'simulated_scenario_client_parcel_wire_verified=true' \
  'simulated_scenario_android13_arm64_client_verified=true' \
  'hmi_d4_debug_demo_control_loop_complete=true' \
  'simulated_scenario_hardware_effect_dispatch_enabled=false' \
  'implementation_stage=P4-D4e'; do
  grep -Fq -- "$marker" "$ROOT_DIR/README.md" \
    || { echo "P4-D4e README marker missing: $marker" >&2; exit 1; }
done

printf '%s\n' \
  'Central Brain Android P4-D4e Client2 simulated scenario-chain check passed' \
  'simulated_scenario_client2_wired=true' \
  'simulated_scenario_client_parcel_wire_verified=true' \
  'simulated_scenario_projection_reducer_owned=true' \
  'simulated_scenario_seven_stage_ui_verified=true' \
  'simulated_scenario_effect_dispatch_enabled=true' \
  'simulated_scenario_readback_accessed=true' \
  'simulated_scenario_approval_input_explicit=true' \
  'simulated_scenario_android13_arm64_client_verified=true' \
  'client2_simulated_scenario_path_count=3' \
  'client2_simulated_approval_input_count=2' \
  'hmi_d4_debug_demo_control_loop_complete=true' \
  'simulated_scenario_hardware_effect_dispatch_enabled=false' \
  'simulated_scenario_approval_authority_available=false' \
  'scenario_execution_enabled=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
