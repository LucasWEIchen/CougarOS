#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SCN-001, S2-GRF-001, S2-EVT-001, S2-HMI-003/006,
# APP-004, XSC-001/004/005/006, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEBUG_ROOT="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/debug"
AIDL="$DEBUG_ROOT/aidl/com/centralbrain/runtime/scenario/ISimulatedScenarioRuntime.aidl"
PARCEL_AIDL="$DEBUG_ROOT/aidl/com/centralbrain/runtime/scenario/SimulatedScenarioBinderSnapshot.aidl"
SNAPSHOT="$DEBUG_ROOT/java/com/centralbrain/runtime/scenario/SimulatedScenarioBinderSnapshot.java"
FACTORY="$DEBUG_ROOT/java/com/centralbrain/runtime/scenario/SimulatedScenarioInputFactory.java"
SERVICE="$DEBUG_ROOT/java/com/centralbrain/runtime/scenario/SimulatedScenarioRuntimeService.java"
TEST="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/scenario/SimulatedScenarioInputFactoryTest.java"
MANIFEST="$DEBUG_ROOT/AndroidManifest.xml"
DEBUG_POLICY="$DEBUG_ROOT/res/xml/central_brain_capability_policy.xml"
CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_p4_d4c_simulated_scenario_binder.json"

for file in "$AIDL" "$PARCEL_AIDL" "$SNAPSHOT" "$FACTORY" "$SERVICE" \
    "$TEST" "$MANIFEST" "$DEBUG_POLICY" "$CONTRACT"; do
  [[ -f "$file" ]] || { echo "P4-D4c artifact missing: $file" >&2; exit 1; }
done

python3 -B - "$CONTRACT" <<'PY'
import json
import pathlib
import sys

c = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
assert c["schema_version"] == 1
assert c["profile_id"] == "android13-p4-d4c-simulated-scenario-binder-v1"
assert c["source_set"] == "debug"
assert c["aidl_interface"] == "com.centralbrain.runtime.scenario.ISimulatedScenarioRuntime"
assert c["aidl_version"] == 1
assert c["signature_permission"] == "com.centralbrain.permission.CONTROL_DEBUG_SIMULATION"
assert c["capability"] == "debug.simulation.control"
assert c["fixed_scenarios"] == ["COLD", "FATIGUE"]
assert c["fixed_driving_profiles"] == ["PARKED", "MOVING"]
assert c["accepted_outcomes"] == ["SUCCEEDED", "FAILED", "SKIPPED"]
assert c["operations"] == [
    "getProtocolVersion", "getProtocolHash", "startScenario", "getSnapshot",
    "supplyPendingOutcome", "cancel",
]
p = c["projection"]
for key in (
    "session_plan_graph_metadata_exposed", "pending_node_metadata_exposed",
    "event_sequence_and_count_exposed", "false_authority_claims_exposed",
):
    assert p[key] is True
for key in (
    "raw_user_or_model_text_accepted", "arbitrary_vehicle_scalar_accepted",
    "external_scenario_file_accepted", "device_identity_exposed",
):
    assert p[key] is False
s = c["claim_state"]
for key in (
    "simulated_scenario_binder_defined",
    "simulated_scenario_binder_signature_permission_enforced",
    "simulated_scenario_binder_capability_enforced",
    "simulated_scenario_android_runtime_wired",
    "simulated_scenario_android_service_published",
    "simulated_scenario_session_event_binder_published",
    "simulated_scenario_debug_only", "simulated_scenario_release_source_absent",
    "simulated_scenario_binder_android13_install_verified",
    "simulated_scenario_binder_unauthorized_access_denied_verified",
):
    assert s[key] is True
assert s["simulated_scenario_binder_protocol_version"] == 1
assert s["simulated_scenario_fixed_scenario_count"] == 2
assert s["simulated_scenario_fixed_driving_profile_count"] == 2
for key in (
    "simulated_scenario_client2_wired", "simulated_scenario_effect_dispatch_enabled",
    "simulated_scenario_readback_accessed",
    "simulated_scenario_approval_authority_available",
    "simulated_scenario_binder_authorized_call_verified",
    "simulated_scenario_production_registered", "scenario_execution_enabled",
    "hardware_accessed", "production_ready", "target_hardware_validated",
):
    assert s[key] is False
assert s["implementation_stage"] == "P4-D4c"
PY

for marker in \
  'const int INTERFACE_VERSION = 2;' \
  'const int SCENARIO_COLD = 1;' \
  'const int SCENARIO_FATIGUE = 2;' \
  'SimulatedScenarioBinderSnapshot startScenario' \
  'SimulatedScenarioBinderSnapshot getSnapshot' \
  'SimulatedScenarioBinderSnapshot supplyPendingOutcome' \
  'SimulatedScenarioBinderSnapshot cancel'; do
  grep -Fq -- "$marker" "$AIDL" \
    || { echo "P4-D4c AIDL marker missing: $marker" >&2; exit 1; }
done

for marker in \
  'enforceCallingOrSelfPermission' \
  'Capability.SIMULATION_CONTROL' \
  'new SimulatedScenarioEffectComposition' \
  'new SimulatedScenarioInputFactory' \
  'fixed_scenario_count=2' \
  'simulated_effect_dispatch_enabled=true' \
  'hardware_effect_dispatch_enabled=false' \
  'hardware_accessed=false'; do
  grep -Fq -- "$marker" "$SERVICE" \
    || { echo "P4-D4c Service marker missing: $marker" >&2; exit 1; }
done

grep -Fq 'android:name=".scenario.SimulatedScenarioRuntimeService"' "$MANIFEST"
grep -Fq 'android:permission="com.centralbrain.permission.CONTROL_DEBUG_SIMULATION"' "$MANIFEST"
grep -Fq 'com.centralbrain.runtime.action.BIND_SIMULATED_SCENARIO_RUNTIME' "$MANIFEST"
grep -Fq '<capability name="debug.simulation.control" />' "$DEBUG_POLICY"

for test_name in \
  coldParkedInputStartsAtEffectPending \
  fatigueParkedInputStartsAtApprovalPending \
  fatigueMovingInputKeepsSeatApprovalAndReclinePruned \
  nullOrInvalidFactoryInputsFailClosed \
  generatedPlanAndSessionIdentitiesAreUnique \
  binderSnapshotContainsOnlyMetadataAndFalseAuthorityClaims; do
  grep -Fq -- "$test_name" "$TEST" \
    || { echo "P4-D4c JVM test missing: $test_name" >&2; exit 1; }
done

if find "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/release" \
    -type f -print 2>/dev/null | xargs -r grep -l -E \
    'ISimulatedScenarioRuntime|SimulatedScenarioRuntimeService|SimulatedScenarioInputFactory'; then
  echo "P4-D4c Binder leaked into main/release source" >&2
  exit 1
fi

bash "$ROOT_DIR/tools/check_central_brain_root_readme.sh" >/dev/null
grep -Fq -- '| `P4-D4c` | `已退出生产基线` |' \
  "$ROOT_DIR/docs/CENTRAL_BRAIN_REQUIREMENTS.md"

printf '%s\n' \
  'Central Brain Android P4-D4c simulated scenario Binder check passed' \
  'simulated_scenario_binder_defined=true' \
  'simulated_scenario_binder_protocol_version=1' \
  'simulated_scenario_binder_signature_permission_enforced=true' \
  'simulated_scenario_binder_capability_enforced=true' \
  'simulated_scenario_fixed_scenario_count=2' \
  'simulated_scenario_fixed_driving_profile_count=2' \
  'simulated_scenario_android_runtime_wired=true' \
  'simulated_scenario_android_service_published=true' \
  'simulated_scenario_session_event_binder_published=true' \
  'simulated_scenario_debug_only=true' \
  'simulated_scenario_release_source_absent=true' \
  'simulated_scenario_binder_android13_install_verified=true' \
  'simulated_scenario_binder_unauthorized_access_denied_verified=true' \
  'simulated_scenario_binder_authorized_call_verified=false' \
  'simulated_scenario_client2_wired=false' \
  'simulated_scenario_effect_dispatch_enabled=false' \
  'simulated_scenario_readback_accessed=false' \
  'simulated_scenario_approval_authority_available=false' \
  'simulated_scenario_production_registered=false' \
  'scenario_execution_enabled=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
