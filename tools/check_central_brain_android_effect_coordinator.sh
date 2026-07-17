#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-EFF-001, S2-SAF-001, NV-G-005/006/007, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EFFECT_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects"
BATCH="$EFFECT_ROOT/EffectBatch.java"
PLANNER="$EFFECT_ROOT/EffectDependencyPlanner.java"
REGISTRY="$EFFECT_ROOT/AdapterRegistry.java"
COORDINATOR="$EFFECT_ROOT/EffectCoordinator.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/effects/EffectCoordinatorTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/effects/EffectCoordinatorProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GRAPH_RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/AgentGraphRuntime.java"
DATABASE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence/CentralBrainDatabase.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P3-W06 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$BATCH" "$PLANNER" "$REGISTRY" "$COORDINATOR" "$TEST" "$PROBE" \
    "$DEBUG_MANIFEST" "$MAIN_MANIFEST" "$RUNTIME_SERVICE" "$GRAPH_RUNTIME" \
    "$DATABASE" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P3-W06 file missing: $file" >&2; exit 1; }
done

for marker in \
  'public final class EffectBatch' \
  'MAX_EFFECTS = 16' \
  'MAX_DEPENDENCIES_PER_EFFECT = 16' \
  'batch effects do not share one governed action binding' \
  'required effect cannot depend on an optional effect' \
  'effect.batch.v1'; do
  require_text "$BATCH" "$marker"
done

for marker in \
  'public final class EffectDependencyPlanner' \
  'effect dependency graph contains a cycle' \
  'waveResources.add(entry.getResourceKey())' \
  'effect.dependency.plan.v1'; do
  require_text "$PLANNER" "$marker"
done

for marker in \
  'public final class AdapterRegistry' \
  'DEBUG_SIMULATION' \
  'PRODUCTION' \
  'CB_ERR_ADAPTER_UNAVAILABLE' \
  'no activated adapter for the exact capability/area/profile' \
  'production route is not explicitly authorized' \
  'registration.descriptor'; do
  require_text "$REGISTRY" "$marker"
done

for marker in \
  'public final class EffectCoordinator' \
  'Map<String, PreparedSlot> prepared = prepare' \
  'requiredPrepareRejected' \
  'abortBeforeDispatch' \
  'dependenciesDelivered' \
  'STATE_DELIVERED' \
  'STATE_UNKNOWN' \
  'effect.coordinator.observation.v1' \
  'effect.coordinator.result.v1'; do
  require_text "$COORDINATOR" "$marker"
done

for test_name in \
  batchFreezesInputsAndDigestBindsDependencyStructure \
  plannerOrdersDependenciesAndSerializesResourceConflicts \
  plannerRejectsDependencyCycles \
  requiredPrepareFailureAbortsBatchBeforeAnyDispatch \
  optionalPrepareFailureDegradesWithoutBlockingRequiredDispatch \
  registryRequiresExactProfileAndNeverFallsBackToSimulation \
  coordinatorEmitsOneTypedObservationPerMixedOutcome \
  unknownDependencyBlocksChildAndResultRemainsDefensive \
  preparedMaterialDefensivelyCopiesTransientBytes; do
  require_text "$TEST" "$test_name"
done

for marker in \
  effect_coordinator_probe_complete \
  effect_batch_defined \
  effect_dependency_plan_verified \
  effect_resource_conflict_serialized \
  effect_adapter_registry_profile_isolation_verified \
  effect_prepare_all_required_verified \
  effect_optional_degradation_verified \
  effect_independent_observation_verified \
  effect_coordinator_android13_arm64_verified; do
  require_text "$PROBE" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
for marker in \
  effect_coordinator_graph_wired=false \
  effect_coordinator_persistence_wired=false \
  production_effect_adapter_registered=false \
  production_effect_dispatch_enabled=false \
  effect_verification_reconciliation_wired=false \
  model_invoked=false \
  network_accessed=false \
  hardware_accessed=false; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

require_text "$DEBUG_MANIFEST" '.effects.EffectCoordinatorProbeActivity'
if grep -Fq 'EffectCoordinatorProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P3-W06 debug probe leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq 'EffectCoordinator|EffectBatch|EffectDependencyPlanner|AdapterRegistry' \
    "$ROOT_DIR/$RUNTIME_SERVICE" "$ROOT_DIR/$GRAPH_RUNTIME" "$ROOT_DIR/$DATABASE"; then
  echo "P3-W06 coordinator was wired into Graph, Service, or Room" >&2
  exit 1
fi
if grep -R -Eiq \
    'System[.](currentTimeMillis|nanoTime)|Thread[.]sleep|new Thread|java[.]util[.]Random|SecureRandom|Executors[.]|java[.]util[.]concurrent|android[.]os|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|java[.]net|okhttp|http://|https://|ioctl|sysfs|/dev/' \
    "$ROOT_DIR/$BATCH" "$ROOT_DIR/$PLANNER" "$ROOT_DIR/$REGISTRY" \
    "$ROOT_DIR/$COORDINATOR"; then
  echo "P3-W06 main contract owns a clock/thread/dispatcher or references network, vehicle, or hardware APIs" >&2
  exit 1
fi
if grep -Fq '.toList()' "$ROOT_DIR/$PROBE"; then
  echo "P3-W06 debug probe uses Stream.toList(), which is unavailable on API 33" >&2
  exit 1
fi

require_text "README.md" "P3 EffectCoordinator"
require_text "central-brain/android-runtime/README.md" "P3-W06 EffectCoordinator"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P3-W06` EffectCoordinator'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P3-W06 EffectCoordinator trace"
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" "P3-W06 implemented EffectCoordinator contract"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P3-W06 EffectCoordinator"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P3-W06 EffectCoordinator"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P3-W06 EffectCoordinator Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "P3-W06 EffectCoordinator"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "P3-W06 进展"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P3-W06 EffectCoordinator"

printf '%s\n' \
  "Central Brain Android EffectCoordinator check passed" \
  "effect_batch_defined=true" \
  "effect_dependency_plan_verified=true" \
  "effect_resource_conflict_serialized=true" \
  "effect_adapter_registry_profile_isolation_verified=true" \
  "effect_prepare_all_required_verified=true" \
  "effect_optional_degradation_verified=true" \
  "effect_independent_observation_verified=true" \
  "effect_coordinator_graph_wired=false" \
  "effect_coordinator_persistence_wired=false" \
  "production_effect_adapter_registered=false" \
  "production_effect_dispatch_enabled=false" \
  "effect_verification_reconciliation_wired=false" \
  "hardware_accessed=false"
