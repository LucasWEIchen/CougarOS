#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SCN-001, S2-GRF-001, S2-EFF-001, S2-HMI-003/006,
# APP-004, XSC-001/005/006, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_p4_d4a_simulated_scenario_graph.json"
SOURCE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/scenario/SimulatedScenarioGraph.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/scenario/SimulatedScenarioGraphTest.java"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
MAIN_SOURCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/scenario/SimulatedScenarioGraph.java"
DOC="docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md"

for file in "$CONTRACT" "$SOURCE" "$TEST" "$RUNTIME" "$GOVERNANCE" "$DOC"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P4-D4a file missing: $file" >&2; exit 1; }
done
[[ ! -e "$ROOT_DIR/$MAIN_SOURCE" ]] \
  || { echo "P4-D4a debug graph leaked into main source" >&2; exit 1; }

python3 -B - "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$SOURCE" \
    "$ROOT_DIR/$TEST" "$ROOT_DIR/$DOC" <<'PY'
import json
import pathlib
import sys

contract_path, source_path, test_path, doc_path = map(pathlib.Path, sys.argv[1:])
contract = json.loads(contract_path.read_text(encoding="utf-8"))
source = source_path.read_text(encoding="utf-8")
test = test_path.read_text(encoding="utf-8")
doc = doc_path.read_text(encoding="utf-8")

if contract.get("schema_version") != 1 \
        or contract.get("profile_id") \
        != "android13-p4-d4a-simulated-scenario-graph-v1" \
        or contract.get("maturity") \
        != "debug_graph_composition_defined_runtime_wiring_pending":
    raise SystemExit("P4-D4a contract identity changed")
if contract.get("requirement_ids") != [
        "S2-SCN-001", "S2-GRF-001", "S2-EFF-001", "S2-HMI-003", "S2-HMI-006",
        "APP-004", "XSC-001", "XSC-005", "XSC-006", "DEL-001", "DEL-004", "DEL-005"]:
    raise SystemExit("P4-D4a Req ID set changed")
if contract.get("source_set") != "debug" \
        or contract.get("maximum_run_count") != 16:
    raise SystemExit("P4-D4a source or capacity boundary changed")
if contract.get("automatic_projection_node_types") != [
        "context.capture", "policy.evaluate", "summary.render"]:
    raise SystemExit("P4-D4a automatic node catalog changed")
if contract.get("external_result_node_types") != [
        {"node_type": "approval.interrupt", "pending_stage": "APPROVAL"},
        {"node_type": "effect.execute", "pending_stage": "EFFECT"},
        {"node_type": "effect.verify", "pending_stage": "READBACK"}]:
    raise SystemExit("P4-D4a pending node catalog changed")

expected_admission = {
    "accepted_or_degraded_resolution_required": True,
    "context_digest_match_required": True,
    "capability_digest_match_required": True,
    "manifest_digest_match_required": True,
    "compiled_plan_graph_validation_required": True,
    "moving_seat_branch_pruned_by_compiler": True,
    "unsupported_node_type_fails_closed": True,
}
if contract.get("admission") != expected_admission:
    raise SystemExit("P4-D4a admission boundary changed")

state = contract.get("claim_state", {})
for key in [
        "simulated_scenario_graph_defined", "simulated_scenario_graph_debug_only",
        "simulated_scenario_plan_published", "simulated_scenario_graph_progress_enabled"]:
    if state.get(key) is not True:
        raise SystemExit(f"P4-D4a software claim missing: {key}")
for key in [
        "simulated_scenario_android_runtime_wired",
        "simulated_scenario_client2_wired",
        "simulated_scenario_effect_dispatch_enabled",
        "simulated_scenario_readback_accessed",
        "simulated_scenario_approval_authority_available",
        "simulated_scenario_production_registered", "scenario_execution_enabled",
        "hardware_accessed", "production_ready", "target_hardware_validated"]:
    if state.get(key) is not False:
        raise SystemExit(f"P4-D4a forbidden claim raised: {key}")
if state.get("implementation_stage") != "P4-D4a":
    raise SystemExit("P4-D4a implementation stage changed")

for marker in [
        "coldPlanAutomaticallyProjectsContextAndPolicyThenWaitsForEffect",
        "explicitSuppliedOutcomesCompleteColdGraphWithoutDispatchingHardware",
        "parkedFatigueStopsAtApprovalBeforeSeatOrOtherEffects",
        "movingFatiguePlanExcludesApprovalAndReclineBranch",
        "requiredEffectFailureFailsGraphClosed",
        "missingPendingNodeAndUnknownRunAreRejected",
        "projectionDigestIsDeterministicAndBindsProgress",
        "debugCompositionClaimsRemainSimulationOnlyAndUnwired"]:
    if marker not in test:
        raise SystemExit(f"P4-D4a JVM marker missing: {marker}")
for marker in [
        "context.capture", "policy.evaluate", "summary.render",
        "approval.interrupt", "effect.execute", "effect.verify",
        "isEffectDispatchEnabled()", "isReadbackAccessed()",
        "isAndroidRuntimeWired()", "isProductionReady()"]:
    if marker not in source:
        raise SystemExit(f"P4-D4a source marker missing: {marker}")
for marker in [
        "P4-D4a", "simulated_scenario_graph_defined=true",
        "simulated_scenario_android_runtime_wired=false",
        "simulated_scenario_effect_dispatch_enabled=false"]:
    if marker not in doc:
        raise SystemExit(f"P4-D4a backlog marker missing: {marker}")
PY

if grep -Eiq \
    'import android[.]|java[.]io|java[.]net|PackageManager|ServiceManager|CarProperty|VehicleHal|/proc/|/dev/|ioctl|sysfs|ProcessBuilder|Runtime[.]getRuntime' \
    "$ROOT_DIR/$SOURCE"; then
  echo "P4-D4a graph accesses Android, storage, network, process, vehicle or hardware" >&2
  exit 1
fi
if grep -Fq 'SimulatedScenarioGraph' "$ROOT_DIR/$RUNTIME" \
    || grep -Fq 'SimulatedScenarioGraph' "$ROOT_DIR/$GOVERNANCE"; then
  echo "P4-D4a debug graph was wired into production Services" >&2
  exit 1
fi

printf '%s\n' \
  'Central Brain Android P4-D4a simulated scenario graph check passed' \
  'simulated_scenario_graph_defined=true' \
  'simulated_scenario_graph_debug_only=true' \
  'simulated_scenario_plan_published=true' \
  'simulated_scenario_graph_progress_enabled=true' \
  'simulated_scenario_android_runtime_wired=false' \
  'simulated_scenario_client2_wired=false' \
  'simulated_scenario_effect_dispatch_enabled=false' \
  'simulated_scenario_readback_accessed=false' \
  'simulated_scenario_approval_authority_available=false' \
  'simulated_scenario_production_registered=false' \
  'scenario_execution_enabled=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
