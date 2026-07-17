#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-EFF-001, S2-UX-003, S2-SAF-001, NV-G-005/006/007,
# DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EFFECT_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects"
PLANNER="$EFFECT_ROOT/CompensationPlanner.java"
UNDO_SERVICE="$EFFECT_ROOT/UndoService.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/effects/CompensationUndoTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/effects/CompensationUndoProbeActivity.java"
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
    || { echo "P3-W08 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$PLANNER" "$UNDO_SERVICE" "$TEST" "$PROBE" "$DEBUG_MANIFEST" \
    "$MAIN_MANIFEST" "$RUNTIME_SERVICE" "$GRAPH_RUNTIME" "$DATABASE" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P3-W08 file missing: $file" >&2; exit 1; }
done

for marker in \
  'public final class CompensationPlanner' \
  'expectedCompensationDescriptorDigest' \
  'expectedCompensationIdempotencyKey' \
  'verified irreversible Effect makes full undo unavailable' \
  'compensation target is not the absolute before value' \
  'compensation Effect must not recursively advertise Undo' \
  'effect.compensation.before.v1' \
  'effect.compensation.plan.v1'; do
  require_text "$PLANNER" "$marker"
done

for marker in \
  'public final class UndoService' \
  'MAX_PROCESS_RECORDS = 64' \
  'PRODUCTION_COMPENSATION_UNAVAILABLE' \
  'GOVERNANCE_AUTHORITY_UNTRUSTED' \
  'CONTEXT_BINDING_CHANGED' \
  'SAFETY_UNSAFE' \
  'UNDO_HANDLE_EXPIRED' \
  'effect.undo.handle.v1' \
  'effect.undo.task.v1'; do
  require_text "$UNDO_SERVICE" "$marker"
done

for test_name in \
  plannerUsesAbsoluteBeforeTargetsInReverseDependencyOrder \
  irreversibleOrUnapprovedTargetsNeverAdvertiseUndo \
  plannerRejectsSnapshotDriftAndRelativeTargets \
  sourceVerifiedObservationRemainsTerminalAndUnchanged \
  undoHandlesAreDigestBoundAndDeadlineCapped \
  admittedUndoCreatesNewGovernedTaskAndIdempotentReplay \
  governanceSafetyContextAndExpiryFailClosed \
  productionProfileFailsClosedWithoutTaskOrDispatch; do
  require_text "$TEST" "$test_name"
done

require_text "$PROBE" 'compensation_undo_probe_complete=true'
require_text "$INSTALLER" 'compensation_undo_probe_complete=true'
for marker in \
  compensation_planner_defined \
  compensation_absolute_before_verified \
  compensation_reverse_dependency_verified \
  compensation_irreversible_rejected \
  undo_ttl_governance_verified \
  undo_new_governed_task_verified \
  undo_idempotent_replay_verified \
  undo_production_fail_closed \
  compensation_undo_android13_arm64_verified; do
  require_text "$PROBE" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
for marker in \
  compensation_undo_runtime_wired=false \
  compensation_undo_persistence_wired=false \
  undo_binder_service_published=false \
  compensation_dispatch_enabled=false \
  production_compensation_authority_wired=false \
  effect_dispatch_enabled=false \
  hardware_accessed=false; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

require_text "$DEBUG_MANIFEST" '.effects.CompensationUndoProbeActivity'
if grep -Fq 'CompensationUndoProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P3-W08 debug probe leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq 'CompensationPlanner|UndoService' \
    "$ROOT_DIR/$RUNTIME_SERVICE" "$ROOT_DIR/$GRAPH_RUNTIME" "$ROOT_DIR/$DATABASE"; then
  echo "P3-W08 compensation contract was wired into Graph, Service, or Room" >&2
  exit 1
fi
if grep -Fq '.apply(' "$ROOT_DIR/$PLANNER" "$ROOT_DIR/$UNDO_SERVICE"; then
  echo "P3-W08 contract invokes Effect apply" >&2
  exit 1
fi
if grep -Eiq 'extends[[:space:]]+Service|android[.]app[.]Service|onBind[(]' \
    "$ROOT_DIR/$UNDO_SERVICE"; then
  echo "P3-W08 UndoService was published as an Android Binder Service" >&2
  exit 1
fi
if grep -R -Eiq \
    'System[.](currentTimeMillis|nanoTime)|Thread[.]sleep|new Thread|java[.]util[.]Random|SecureRandom|Executors[.]|android[.]os|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|java[.]net|okhttp|http://|https://|ioctl|sysfs|/dev/' \
    "$ROOT_DIR/$PLANNER" "$ROOT_DIR/$UNDO_SERVICE"; then
  echo "P3-W08 main contract owns a clock/thread or references network, vehicle, or hardware APIs" >&2
  exit 1
fi
if grep -Fq '.toList()' "$ROOT_DIR/$PROBE"; then
  echo "P3-W08 debug probe uses Stream.toList(), which is unavailable on API 33" >&2
  exit 1
fi

require_text "README.md" "P3 Compensation/Undo"
require_text "central-brain/android-runtime/README.md" "P3-W08 Compensation/Undo"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P3-W08` Compensation/Undo'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P3-W08 Compensation/Undo trace"
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" "P3-W08 implemented Compensation/Undo"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P3-W08 Compensation/Undo"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P3-W08 Compensation/Undo"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P3-W08 Compensation/Undo Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "P3-W08 Compensation/Undo"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "P3-W08 进展"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P3-W08 Compensation/Undo"

printf '%s\n' \
  "Central Brain Android Compensation/Undo check passed" \
  "compensation_planner_defined=true" \
  "compensation_absolute_before_verified=true" \
  "compensation_reverse_dependency_verified=true" \
  "compensation_irreversible_rejected=true" \
  "undo_ttl_governance_verified=true" \
  "undo_new_governed_task_verified=true" \
  "undo_idempotent_replay_verified=true" \
  "undo_production_fail_closed=true" \
  "compensation_undo_runtime_wired=false" \
  "compensation_undo_persistence_wired=false" \
  "undo_binder_service_published=false" \
  "compensation_dispatch_enabled=false" \
  "production_compensation_authority_wired=false" \
  "effect_dispatch_enabled=false" \
  "hardware_accessed=false"
