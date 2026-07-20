#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SCN-001, S2-GRF-001, S2-EVT-001, S2-EFF-001,
# S2-SAF-001, S2-HMI-003/006, APP-004, XSC-001/004/005/006, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEBUG_ROOT="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/debug"
COMPOSITION="$DEBUG_ROOT/java/com/centralbrain/runtime/scenario/SimulatedScenarioEffectComposition.java"
SERVICE="$DEBUG_ROOT/java/com/centralbrain/runtime/scenario/SimulatedScenarioRuntimeService.java"
SNAPSHOT="$DEBUG_ROOT/java/com/centralbrain/runtime/scenario/SimulatedScenarioBinderSnapshot.java"
PROBE="$DEBUG_ROOT/java/com/centralbrain/runtime/scenario/SimulatedScenarioCompositionProbeActivity.java"
RUNTIME="$DEBUG_ROOT/java/com/centralbrain/runtime/scenario/SimulatedScenarioRuntime.java"
AIDL="$DEBUG_ROOT/aidl/com/centralbrain/runtime/scenario/ISimulatedScenarioRuntime.aidl"
MANIFEST="$DEBUG_ROOT/AndroidManifest.xml"
TEST="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/scenario/SimulatedScenarioEffectCompositionTest.java"
CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_p4_d4d_simulated_effect_composition.json"

for file in "$COMPOSITION" "$SERVICE" "$SNAPSHOT" "$PROBE" "$RUNTIME" \
    "$AIDL" "$MANIFEST" "$TEST" "$CONTRACT"; do
  [[ -f "$file" ]] || { echo "P4-D4d artifact missing: $file" >&2; exit 1; }
done

python3 -B - "$CONTRACT" <<'PY'
import json
import pathlib
import sys

c = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
assert c["schema_version"] == 1
assert c["profile_id"] == "android13-p4-d4d-simulated-effect-composition-v1"
assert c["source_set"] == "debug"
assert c["binder_protocol_version"] == 2
assert c["binder_parcel_schema_version"] == 2
assert c["runtime_event_schema_count"] == 10
assert c["adapter_ids"] == [
    "debug.simulated.hvac.v1", "debug.simulated.seat.v1",
    "debug.simulated.media.v1", "debug.simulated.navigation.v1",
]
assert len(c["fixed_targets"]) == 7
expected = {
    "cold": (3, 3, 0, "COMPLETED"),
    "fatigue_parked_approved": (5, 3, 1, "COMPLETED"),
    "fatigue_parked_skipped": (4, 2, 1, "PARTIAL"),
    "fatigue_moving": (4, 2, 0, "COMPLETED"),
}
for name, values in expected.items():
    outcome = c["scenario_outcomes"][name]
    assert (
        outcome["effect_dispatch_count"], outcome["readback_match_count"],
        outcome["approval_input_count"], outcome["terminal_state"],
    ) == values
probe = c["probe"]
assert probe["permission"] == "android.permission.DUMP"
assert probe["same_signer_binder_client"] is True
assert probe["fixed_scenario_count"] == 2
assert probe["executed_on_android13"] is True
assert probe["observed_protocol_version"] == 2
assert probe["observed_effect_dispatch_count"] == 8
assert probe["observed_readback_match_count"] == 6
assert probe["observed_approval_input_count"] == 1
assert probe["observed_failure_count"] == 0
assert probe["raw_payload_logged"] is False
assert probe["device_identity_logged"] is False
s = c["claim_state"]
for key in (
    "simulated_scenario_effect_composition_defined",
    "simulated_scenario_effect_dispatch_enabled",
    "simulated_scenario_readback_accessed",
    "simulated_scenario_approval_input_explicit",
    "simulated_scenario_partial_stuck_projection_defined",
    "simulated_scenario_android_debug_probe_available",
    "simulated_scenario_android_debug_probe_executed",
    "simulated_scenario_binder_authorized_call_verified",
):
    assert s[key] is True
assert s["simulated_scenario_effect_adapter_count"] == 4
assert s["simulated_scenario_fixed_target_count"] == 7
assert s["simulated_scenario_binder_protocol_version"] == 2
assert s["simulated_scenario_binder_parcel_schema_version"] == 2
for key in (
    "simulated_scenario_hardware_effect_dispatch_enabled",
    "simulated_scenario_approval_authority_available",
    "simulated_scenario_client2_wired",
    "simulated_scenario_production_registered",
    "scenario_execution_enabled", "hardware_accessed",
    "production_ready", "target_hardware_validated",
):
    assert s[key] is False
assert s["implementation_stage"] == "P4-D4d"
PY

for marker in \
  'new SimulatedHvacEffectAdapter' \
  'new SimulatedSeatEffectAdapter' \
  'new SimulatedMediaEffectAdapter' \
  'new SimulatedNavigationEffectAdapter' \
  'case EFFECT:' \
  'case READBACK:' \
  'ReadbackState.MATCHED' \
  'run is not waiting for approval input' \
  'scenario_execution_enabled'; do
  grep -Fq -- "$marker" "$COMPOSITION" "$CONTRACT" \
    || { echo "P4-D4d composition marker missing: $marker" >&2; exit 1; }
done

grep -Fq 'const int INTERFACE_VERSION = 2;' "$AIDL"
grep -Fq 'const int SESSION_PARTIAL = 7;' "$AIDL"
grep -Fq 'const int SESSION_STUCK = 8;' "$AIDL"
grep -Fq 'SimulatedScenarioEffectComposition' "$SERVICE"
grep -Fq 'simulatedEffectDispatchCount' "$SNAPSHOT"
grep -Fq 'SCHEMA_SESSION_PARTIAL' "$RUNTIME"
grep -Fq 'SCHEMA_SESSION_STUCK' "$RUNTIME"
grep -Fq 'android:name=".scenario.SimulatedScenarioCompositionProbeActivity"' "$MANIFEST"
grep -Fq 'android:permission="android.permission.DUMP"' "$MANIFEST"
grep -Fq 'probe_result=PASS protocol_version=2' "$PROBE"
grep -Fq 'raw_payload' "$CONTRACT"

for test_name in \
  coldScenarioAutomaticallyDispatchesAndMatchesReadback \
  parkedFatigueWaitsForApprovalThenRunsAllSimulatedEffects \
  movingFatigueKeepsApprovalAndReclineBranchPruned \
  skippedApprovalCompletesPartialWithoutSeatRecline \
  requiredEffectFailureFailsGraphClosed \
  mismatchedReadbackFailsRequiredVerification \
  binderProjectionCarriesCountsAndNoProductionAuthority \
  onlyApprovalPendingAcceptsExternalOutcome; do
  grep -Fq -- "$test_name" "$TEST" \
    || { echo "P4-D4d JVM test missing: $test_name" >&2; exit 1; }
done

if find "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/release" \
    -type f -print 2>/dev/null | xargs -r grep -l -E \
    'SimulatedScenarioEffectComposition|SimulatedScenarioCompositionProbeActivity'; then
  echo "P4-D4d composition leaked into main/release source" >&2
  exit 1
fi

bash "$ROOT_DIR/tools/check_central_brain_root_readme.sh" >/dev/null
grep -Fq -- '| `P4-D4d` |' "$ROOT_DIR/README.md" \
  || { echo "P4-D4d README tracking row missing" >&2; exit 1; }

printf '%s\n' \
  'Central Brain Android P4-D4d simulated Effect composition check passed' \
  'simulated_scenario_effect_composition_defined=true' \
  'simulated_scenario_effect_adapter_count=4' \
  'simulated_scenario_fixed_target_count=7' \
  'simulated_scenario_effect_dispatch_enabled=true' \
  'simulated_scenario_readback_accessed=true' \
  'simulated_scenario_approval_input_explicit=true' \
  'simulated_scenario_partial_stuck_projection_defined=true' \
  'simulated_scenario_binder_protocol_version=2' \
  'simulated_scenario_binder_parcel_schema_version=2' \
  'simulated_scenario_android_debug_probe_available=true' \
  'simulated_scenario_android_debug_probe_executed=true' \
  'simulated_scenario_binder_authorized_call_verified=true' \
  'simulated_scenario_probe_effect_dispatch_count=8' \
  'simulated_scenario_probe_readback_match_count=6' \
  'simulated_scenario_probe_approval_input_count=1' \
  'simulated_scenario_probe_failure_count=0' \
  'simulated_scenario_hardware_effect_dispatch_enabled=false' \
  'simulated_scenario_approval_authority_available=false' \
  'simulated_scenario_client2_wired=false' \
  'simulated_scenario_production_registered=false' \
  'scenario_execution_enabled=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
