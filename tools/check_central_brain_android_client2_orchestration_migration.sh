#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, S2-SCN-001, S2-GRF-001, S2-EVT-001, S2-EFF-001,
# S2-SAF-001, S2-HMI-003/006, XSC-001/005/006, DEL-001/003/004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="$ROOT_DIR/apk-labs/client2-central-brain"
SOURCE="$PROJECT/bridge/src/com/centralbrain/client2"
CLIENT="$SOURCE/OrchestrationRuntimeClient.java"
COORDINATOR="$SOURCE/CockpitControlCoordinator.java"
TIMELINE="$SOURCE/CockpitExecutionTimeline.java"
BUILD_SCRIPT="$PROJECT/scripts/build_binder_bridge_dex.sh"
VERIFY_SCRIPT="$PROJECT/scripts/verify_project.sh"
PROJECT_JSON="$PROJECT/client2-central-brain.project.json"
DEVICE_TEST="$ROOT_DIR/tools/test_client2_central_brain_simulated_scenario_chain.sh"
CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_client2_orchestration_migration_v1.json"
DEBUG_POLICY="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/debug/res/xml/central_brain_capability_policy.xml"
GRAPH_RECOVERY="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/GraphRestartReconciler.java"
DURABLE_PROJECTION="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence/DurableOrchestrationProjectionRepository.java"
DEBUG_BACKEND="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/orchestration/DebugSimulatedOrchestrationBackend.java"
DEBUG_INPUTS="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/scenario/SimulatedScenarioInputFactory.java"

for file in "$CLIENT" "$COORDINATOR" "$TIMELINE" "$BUILD_SCRIPT" \
    "$VERIFY_SCRIPT" "$PROJECT_JSON" "$DEVICE_TEST" "$CONTRACT" "$DEBUG_POLICY" \
    "$GRAPH_RECOVERY" "$DURABLE_PROJECTION" "$DEBUG_BACKEND" "$DEBUG_INPUTS"; do
  [[ -f "$file" ]] || { echo "P4-R2 artifact missing: $file" >&2; exit 1; }
done

test ! -f "$SOURCE/SimulatedScenarioRuntimeClient.java"
test ! -f "$PROJECT/bridge/src/com/centralbrain/runtime/scenario/SimulatedScenarioBinderSnapshot.java"
[[ "$(find "$PROJECT/bridge/src" -type f -name '*.java' | wc -l)" -eq 19 ]]

python3 -B - "$CONTRACT" "$PROJECT_JSON" "$COORDINATOR" <<'PY'
import json
import pathlib
import sys

contract = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
assert contract["schema_version"] == 1
assert contract["profile_id"] == "android13-client2-orchestration-migration-v1"
assert contract["client2"] == {
    "project_version": "0.19.0",
    "source_set": "debug_reverse_demo",
    "java_source_count": 19,
    "generated_debug_aidl_count": 1,
    "generated_debug_aidl": "com.centralbrain.runtime.simulation.IDebugSimulationController",
    "legacy_scenario_binder_client_present": False,
    "copied_scenario_parcelable_present": False,
}
assert contract["sequence"] == [
    "SessionClient.openSession",
    "SessionHandle.sessionId",
    "OrchestrationClient.getSnapshot",
    "OrchestrationClient.start_when_not_started",
    "OrchestrationClient.getPlan",
    "OrchestrationContract.validatePlanForSnapshot",
    "CockpitHmiReducer",
    "seven_stage_projection",
]
orchestration = contract["orchestration"]
assert orchestration["interface_version"] == 1
for key in (
    "resume_reads_before_start", "typed_plan_validated",
    "projection_digest_validated_by_sdk",
    "approval_response_bound_to_projection_digest",
    "release_backend_fail_closed",
):
    assert orchestration[key] is True
assert orchestration["approval_response_is_grant"] is False
claims = contract["claim_state"]
for key in (
    "client2_orchestration_sdk_v1_wired",
    "client2_session_before_orchestration",
    "client2_orchestration_resume_read_before_start",
    "client2_orchestration_plan_validated",
    "client2_orchestration_approval_projection_bound",
    "client2_orchestration_debug_capability_policy_wired",
    "client2_debug_effect_projection_enabled",
    "client2_debug_readback_projection_enabled",
):
    assert claims[key] is True
for key in (
    "client2_legacy_simulated_scenario_binder_used",
    "client2_android13_arm64_verified",
    "vehicle_bus_accessed", "npu_accessed", "hardware_accessed",
    "scenario_execution_enabled", "production_ready",
    "target_hardware_validated",
):
    assert claims[key] is False
assert claims["implementation_stage"] == "P4-R2"
assert orchestration["debug_client2_capability_policy_wired"] is True
assert all(contract["runtime_alignment"].values())
assert contract["validation"]["android13_x86_64_emulator_executed"] is True
assert contract["validation"]["android13_arm64_target_executed"] is False
assert claims["client2_android13_x86_64_verified"] is True

project = json.loads(pathlib.Path(sys.argv[2]).read_text(encoding="utf-8"))
assert project["version"] == "0.19.0"
assert any(item.startswith("DEV-119:") for item in project["tracked_deviations"])

source = pathlib.Path(sys.argv[3]).read_text(encoding="utf-8")
start = source.index("private void startScenario(")
end = source.index("private void handleHvacControl(", start)
assert "orchestrationClient" not in source[start:end]
opened = source.index("public void onSessionOpened(")
opened_end = source.index("public void onSessionSnapshot(", opened)
opened_body = source[opened:opened_end]
assert opened_body.index("sessionOpened(handle, scenarioId)") \
       < opened_body.index("orchestrationClient.openOrResume(")
PY

python3 -B - "$DEBUG_POLICY" <<'PY'
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
principal = next(
    item for item in root.findall("principal")
    if item.attrib["packageName"] == "com.tuanjie.urasclient2"
)
granted = {item.attrib["name"] for item in principal.findall("capability")}
required = {
    "runtime.orchestration.protocol.read",
    "runtime.orchestration.start.own",
    "runtime.orchestration.read.own",
    "runtime.orchestration.approval.respond.own",
    "runtime.orchestration.cancel.own",
}
assert required <= granted
assert "runtime.orchestration.undo.request.own" not in granted
PY

for marker in \
  'new OrchestrationClient(' \
  'client.getSnapshot(start.sessionId)' \
  'isNotStarted(snapshot)' \
  'client.start(request)' \
  'client.getPlan(start.sessionId)' \
  'OrchestrationContract.validatePlanForSnapshot(plan, snapshot)' \
  'response.expectedProjectionDigest = snapshot.projectionDigest' \
  'client.respondToApproval(response)' \
  'PROFILE_DEBUG_SIMULATION' \
  'approvalAuthorityTrusted' \
  'hardwareAccessed' \
  'targetHardwareValidated'; do
  grep -Fq -- "$marker" "$CLIENT" \
    || { echo "P4-R2 client marker missing: $marker" >&2; exit 1; }
done

grep -Fq 'IDEMPOTENCY_REQUIRED_NODE_TYPES' "$GRAPH_RECOVERY"
grep -Fq 'recoveryIdempotencyKey(node)' "$DURABLE_PROJECTION"
grep -Fq 'session.getDeadlineEpochMs()' "$DEBUG_BACKEND"
grep -Fq 'stableProjection(record, current)' "$DEBUG_BACKEND"
grep -Fq 'verifiedCapabilities.contains(node.capabilityId)' "$DEBUG_BACKEND"
grep -Fq 'effect.state == EffectContract.STATE_VERIFIED' "$CLIENT"

for marker in \
  'cockpit_orchestration_sdk_v1_wired=true' \
  'cockpit_legacy_simulated_scenario_binder_used=false'; do
  grep -Fq -- "$marker" "$COORDINATOR" \
    || { echo "P4-R2 coordinator marker missing: $marker" >&2; exit 1; }
done
for marker in ORCHESTRATION_V1 RUNTIME_SDK GRAPH_V1 EFFECT_V1_DEBUG READBACK_V1_DEBUG; do
  grep -Fq -- "$marker" "$TIMELINE" \
    || { echo "P4-R2 timeline marker missing: $marker" >&2; exit 1; }
done

grep -Fq 'Expected exactly nineteen Client2' "$BUILD_SCRIPT"
grep -Fq 'Expected exactly one generated debug simulation-controller Binder source' "$BUILD_SCRIPT"
if grep -Fq 'ISimulatedScenarioRuntime' "$BUILD_SCRIPT"; then
  echo "legacy simulated-scenario AIDL remains in Client2 build" >&2
  exit 1
fi

for marker in \
  'client2_orchestration_sdk_v1_wired=true' \
  'client2_orchestration_resume_read_before_start=true' \
  'client2_orchestration_approval_projection_bound=true' \
  'client2_orchestration_debug_capability_policy_wired=true' \
  'client2_legacy_simulated_scenario_binder_used=false'; do
  grep -Fq -- "$marker" "$DEVICE_TEST" \
    || { echo "P4-R2 device marker missing: $marker" >&2; exit 1; }
done

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
  grep -Fq 'P4-R2 Client2 Orchestration V1 migration' "$ROOT_DIR/$doc" \
    || { echo "P4-R2 documentation marker missing: $doc" >&2; exit 1; }
done

bash "$ROOT_DIR/tools/check_central_brain_android_orchestration_v1.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_android_client2_hmi_reducer.sh" >/dev/null

printf '%s\n' \
  'Central Brain Android P4-R2 Client2 Orchestration migration check passed' \
  'client2_orchestration_sdk_v1_wired=true' \
  'client2_session_before_orchestration=true' \
  'client2_orchestration_resume_read_before_start=true' \
  'client2_orchestration_plan_validated=true' \
  'client2_orchestration_approval_projection_bound=true' \
  'client2_orchestration_debug_capability_policy_wired=true' \
  'client2_legacy_simulated_scenario_binder_used=false' \
  'client2_debug_effect_projection_enabled=true' \
  'client2_debug_readback_projection_enabled=true' \
  'client2_android13_x86_64_verified=true' \
  'client2_android13_arm64_verified=false' \
  'hardware_accessed=false' \
  'scenario_execution_enabled=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'implementation_stage=P4-R2'
