#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SCN-001, S2-GRF-001, S2-SAF-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCENARIO_DIR="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/scenario"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/scenario/ScenarioPlanCompilerTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/scenario/ScenarioPlanCompilerProbeActivity.java"
MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
INSTALLER="tools/install_central_brain_android_runtime.sh"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"

require_file() {
  local path="$1"
  [[ -f "$ROOT_DIR/$path" ]] \
    || { echo "missing Android Scenario compiler file: $path" >&2; exit 1; }
}

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
  local path="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$path" \
    || { echo "missing Android Scenario compiler marker '$marker' in $path" >&2; exit 1; }
}

for class in ScenarioPlanCompiler PlanGraphValidator PlanDigest; do
  require_file "$SCENARIO_DIR/$class.java"
done
for path in "$TEST" "$PROBE" "$MANIFEST" "$INSTALLER"; do
  require_file "$path"
done

require_text "$SCENARIO_DIR/ScenarioPlanCompiler.java" 'CompiledPlan compile('
require_text "$SCENARIO_DIR/ScenarioPlanCompiler.java" 'resolution digest drift detected'
require_text "$SCENARIO_DIR/ScenarioPlanCompiler.java" 'capability digest drift detected'
require_text "$SCENARIO_DIR/ScenarioPlanCompiler.java" 'DEGRADED_OPTIONAL_ONLY'
require_text "$SCENARIO_DIR/ScenarioPlanCompiler.java" 'isExecutable()'
require_text "$SCENARIO_DIR/ScenarioPlanCompiler.java" 'isProductionTrusted()'
require_text "$SCENARIO_DIR/PlanGraphValidator.java" 'required effect has no reachable verification'
require_text "$SCENARIO_DIR/PlanGraphValidator.java" 'HIGH effect has no approval predecessor'
require_text "$SCENARIO_DIR/PlanGraphValidator.java" 'moving or unknown driver branch contains recline dispatch'
require_text "$SCENARIO_DIR/PlanDigest.java" 'central-brain-compiled-scenario-plan-v1'
require_text "$SCENARIO_DIR/PlanDigest.java" 'central-brain-scenario-node-input-v1'

for test_name in \
  coldGoldenPlanIsStableTypedAndNonExecutable \
  optionalCapabilityFallbackIsPrunedAndResultIsImmutable \
  movingFatigueOmitsUnsafeSeatApprovalAndReclineBranch \
  rejectedResolutionAndSnapshotDigestDriftFailClosed \
  graphValidatorRejectsCycleAndMissingRequiredVerification \
  highRiskEffectRequiresApprovalPredecessor \
  malformedCompileMetadataFailsClosed; do
  require_text "$TEST" "$test_name"
done

require_text "$MANIFEST" '.scenario.ScenarioPlanCompilerProbeActivity'
for marker in \
  scenario_plan_compiler_defined=true \
  scenario_plan_golden_verified=true \
  scenario_plan_degraded_fallback_verified=true \
  scenario_plan_moving_seat_absent=true \
  scenario_plan_cycle_rejected=true \
  scenario_plan_digest_verified=true \
  scenario_plan_immutable_verified=true \
  scenario_plan_compiler_android13_arm64_verified=true; do
  require_text "$PROBE" "${marker%=true}="
  require_text "$INSTALLER" "$marker"
done
for marker in \
  scenario_plan_compiler_runtime_wired=false \
  scenario_plan_runtime_published=false \
  scenario_graph_execution_enabled=false \
  effect_dispatch_enabled=false \
  vehicle_signal_provider_wired=false \
  hardware_accessed=false; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

if grep -R -Eiq \
    'android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|java[.]net|okhttp|http://|androidx[.]room|runtime[.]model|ModelProvider|InferenceResourceScheduler|EffectAdapter|EffectDelivery|ioctl|sysfs|/dev/' \
    "$ROOT_DIR/$SCENARIO_DIR/ScenarioPlanCompiler.java" \
    "$ROOT_DIR/$SCENARIO_DIR/PlanGraphValidator.java" \
    "$ROOT_DIR/$SCENARIO_DIR/PlanDigest.java" \
    "$ROOT_DIR/$PROBE"; then
  echo "P2-W07 Scenario compiler unexpectedly references model, effect runtime, persistence, network, or hardware access" >&2
  exit 1
fi
if grep -Eq 'ScenarioPlanCompiler|PlanGraphValidator|PlanDigest' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE"; then
  echo "P2-W07 Scenario compiler must not be wired into production Services" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P2-W07 Scenario Plan Compiler"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" '`P2-W07` ScenarioPlanCompiler'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W07 ScenarioPlanCompiler trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android P2-W07 Scenario Plan Compiler"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android P2-W07 Scenario Plan Compiler"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W07 Scenario Plan Compiler Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W07 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W07 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W07 ScenarioPlanCompiler"

printf '%s\n' \
  "Central Brain Android Scenario plan compiler check passed" \
  "scenario_plan_compiler_defined=true" \
  "scenario_plan_schema_version=1" \
  "scenario_plan_compiler_runtime_wired=false" \
  "scenario_plan_runtime_published=false" \
  "scenario_graph_execution_enabled=false" \
  "effect_dispatch_enabled=false" \
  "hardware_accessed=false"
