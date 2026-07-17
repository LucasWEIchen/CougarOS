#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SCN-001, S2-SAF-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCENARIO_DIR="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/scenario"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/scenario/DeterministicScenarioResolverTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/scenario/ScenarioResolverProbeActivity.java"
MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
INSTALLER="tools/install_central_brain_android_runtime.sh"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"

require_file() {
  local path="$1"
  [[ -f "$ROOT_DIR/$path" ]] \
    || { echo "missing Android Scenario resolver file: $path" >&2; exit 1; }
}

require_text() {
  local path="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$path" \
    || { echo "missing Android Scenario resolver marker '$marker' in $path" >&2; exit 1; }
}

for class in ScenarioResolver DeterministicScenarioResolver ScenarioResolution; do
  require_file "$SCENARIO_DIR/$class.java"
done
for path in "$TEST" "$PROBE" "$MANIFEST" "$INSTALLER"; do
  require_file "$path"
done

require_text "$SCENARIO_DIR/ScenarioResolver.java" 'ScenarioResolution resolve('
require_text "$SCENARIO_DIR/ScenarioResolver.java" 'MAX_INTENT_CHARS = 256'
require_text "$SCENARIO_DIR/ScenarioResolver.java" 'SOFTWARE_SIMULATION'
require_text "$SCENARIO_DIR/ScenarioResolver.java" 'PRODUCTION'
require_text "$SCENARIO_DIR/ScenarioResolver.java" 'isProductionTrusted()'
require_text "$SCENARIO_DIR/DeterministicScenarioResolver.java" 'scene.comfort.cold.v1'
require_text "$SCENARIO_DIR/DeterministicScenarioResolver.java" 'scene.fatigue.assist.v1'
require_text "$SCENARIO_DIR/DeterministicScenarioResolver.java" 'scene.rest.nap.v1'
require_text "$SCENARIO_DIR/DeterministicScenarioResolver.java" 'AMBIGUOUS_INTENT'
require_text "$SCENARIO_DIR/DeterministicScenarioResolver.java" 'CONTEXT_POLICY_MISMATCH'
require_text "$SCENARIO_DIR/DeterministicScenarioResolver.java" 'REQUIRED_CAPABILITY_POLICY_BLOCKED'
require_text "$SCENARIO_DIR/ScenarioResolution.java" 'ACCEPTED'
require_text "$SCENARIO_DIR/ScenarioResolution.java" 'DEGRADED'
require_text "$SCENARIO_DIR/ScenarioResolution.java" 'REJECTED'
require_text "$SCENARIO_DIR/ScenarioResolution.java" 'isExecutable()'
require_text "$SCENARIO_DIR/ScenarioResolution.java" 'central-brain-scenario-resolution-v1'

for test_name in \
  explicitButtonIdWinsAndProducesStableNonExecutableResolution \
  deterministicTextRulesCoverColdFatigueAndRest \
  unknownAndAmbiguousTextFailClosedWithoutSelectingManifest \
  sourceZonePolicyAndRestrictedContextAreRejected \
  requiredCapabilityRejectsWhileOptionalCapabilityDegrades \
  movingFatigueDegradesButMovingRestRejectsParkedOnlyCapability \
  productionProfileAndMalformedRequestFailClosed; do
  require_text "$TEST" "$test_name"
done

require_text "$MANIFEST" '.scenario.ScenarioResolverProbeActivity'
for marker in \
  scenario_resolver_defined=true \
  scenario_resolver_explicit_verified=true \
  scenario_resolver_cold_verified=true \
  scenario_resolver_fatigue_verified=true \
  scenario_resolver_rest_verified=true \
  scenario_resolver_unknown_intent_rejected=true \
  scenario_resolver_ambiguous_intent_rejected=true \
  scenario_resolver_capability_policy_verified=true \
  scenario_resolver_production_fail_closed=true \
  scenario_resolution_digest_verified=true \
  scenario_resolver_android13_arm64_verified=true; do
  require_text "$PROBE" "${marker%=true}="
  require_text "$INSTALLER" "$marker"
done
for marker in \
  scenario_resolver_model_invoked=false \
  scenario_resolver_runtime_wired=false \
  scenario_compiler_wired=false \
  scenario_graph_execution_enabled=false \
  effect_dispatch_enabled=false \
  vehicle_signal_provider_wired=false \
  hardware_accessed=false; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

if grep -R -Eiq \
    'android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|java[.]net|okhttp|http://|androidx[.]room|runtime[.]model|ModelProvider|InferenceResourceScheduler|EffectAdapter|EffectDelivery|ioctl|sysfs|/dev/' \
    "$ROOT_DIR/$SCENARIO_DIR/ScenarioResolver.java" \
    "$ROOT_DIR/$SCENARIO_DIR/DeterministicScenarioResolver.java" \
    "$ROOT_DIR/$SCENARIO_DIR/ScenarioResolution.java" \
    "$ROOT_DIR/$PROBE"; then
  echo "P2-W06 Scenario resolver unexpectedly references model, effect, persistence, network, or hardware access" >&2
  exit 1
fi
if grep -Eq 'ScenarioResolver|ScenarioResolution|DeterministicScenarioResolver' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE"; then
  echo "P2-W06 Scenario resolver must not be wired into production Services" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P2-W06 Deterministic Scenario Resolver"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P2-W06` DeterministicScenarioResolver'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P2-W06 Deterministic ScenarioResolver trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P2-W06 Deterministic Scenario Resolver"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P2-W06 Deterministic Scenario Resolver"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P2-W06 Scenario Resolver Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "P2-W06 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "P2-W06 进展"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P2-W06 DeterministicScenarioResolver"

printf '%s\n' \
  "Central Brain Android deterministic Scenario resolver check passed" \
  "scenario_resolver_defined=true" \
  "scenario_resolution_schema_version=1" \
  "scenario_resolver_model_invoked=false" \
  "scenario_resolver_runtime_wired=false" \
  "scenario_compiler_wired=false" \
  "scenario_graph_execution_enabled=false" \
  "effect_dispatch_enabled=false" \
  "hardware_accessed=false"
