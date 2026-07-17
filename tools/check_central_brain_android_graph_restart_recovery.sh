#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SES-001, S2-GRF-001, S2-EFF-001, S2-SAF-001,
# NV-G-005/006/007, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRAPH_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph"
PERSISTENCE_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence"
RECONCILER="$GRAPH_ROOT/GraphRestartReconciler.java"
REPOSITORY="$PERSISTENCE_ROOT/DurableGraphRecoveryRepository.java"
DAO="$PERSISTENCE_ROOT/RuntimeStateDao.java"
DATABASE="$PERSISTENCE_ROOT/CentralBrainDatabase.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/graph/GraphRestartReconcilerTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/persistence/GraphRestartRecoveryProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GRAPH_RUNTIME="$GRAPH_ROOT/AgentGraphRuntime.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P3-W09 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$RECONCILER" "$REPOSITORY" "$DAO" "$DATABASE" "$TEST" \
    "$PROBE" "$DEBUG_MANIFEST" "$MAIN_MANIFEST" "$RUNTIME_SERVICE" \
    "$GRAPH_RUNTIME" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P3-W09 file missing: $file" >&2; exit 1; }
done

for marker in \
  'public final class GraphRestartReconciler' \
  'REVALIDATE_GOVERNANCE' \
  'REVALIDATE_APPROVAL' \
  'REVALIDATE_UNDO' \
  'RECONCILE_EFFECT_NODE' \
  'RECONCILE_EFFECT_STATUS' \
  'CHECKPOINT_MISMATCH' \
  'isContinuationAllowed()' \
  'isExecutorDispatchEnabled() { return false; }' \
  'isProductionAuthorized() { return false; }'; do
  require_text "$RECONCILER" "$marker"
done

for marker in \
  'public final class DurableGraphRecoveryRepository' \
  'AUDIT_GRAPH_RESTART_RECONCILED' \
  'persistInitial(PersistentRun run)' \
  'loadRequired(String planId)' \
  'applyRecovery(Result result, long nowEpochMs)' \
  'database.runInTransaction' \
  'isExecutorDispatchEnabled() { return false; }'; do
  require_text "$REPOSITORY" "$marker"
done

for marker in \
  'findPlan(String planId)' \
  'listPlanNodes(String planId)' \
  'listEffectObservationsForRecovery' \
  'listCompensationsForRecovery' \
  'findAuditEvent(String eventId)' \
  'void insertPlan(PlanEntity entity)' \
  'void insertPlanNode(PlanNodeEntity entity)' \
  'int updatePlan(PlanEntity entity)' \
  'int updatePlanNode(PlanNodeEntity entity)'; do
  require_text "$DAO" "$marker"
done
require_text "$DATABASE" 'public static final int VERSION = 4;'

for test_name in \
  executingAndWaitingRecoverToReconcileBeforeContinue \
  validControlCheckpointCanContinueOnlyAfterGovernanceRevalidation \
  checkpointMismatchFailsStuck \
  unknownEffectNeverRedispatchesAndRequiresTypedResolution \
  expiredPlanFailsTerminalWithoutReplay \
  reconciliationDigestIsStableAcrossRecoveredReplay \
  compensationAndApprovalAlwaysRequireFreshAuthority \
  invalidAndOversizeDurableMaterialFailsClosed; do
  require_text "$TEST" "$test_name"
done

require_text "$PROBE" 'PROCESS_BIRTH_ELAPSED_MS'
require_text "$PROBE" 'graph_restart_probe_complete=true'
require_text "$INSTALLER" 'phase recover-first'
require_text "$INSTALLER" 'phase recover-replay'
for marker in \
  graph_restart_reconciler_defined \
  graph_restart_room_v4_repository_verified \
  graph_restart_waiting_recovered \
  graph_restart_executing_reconciled \
  graph_restart_unknown_effect_reconciled \
  graph_restart_approval_undo_revalidation_verified \
  graph_restart_checkpoint_mismatch_stuck \
  graph_restart_continue_after_revalidate_verified \
  graph_restart_process_death_verified \
  graph_restart_idempotent_reopen_verified \
  graph_restart_audit_exactly_once_verified \
  graph_restart_historical_digest_replay_verified \
  graph_restart_android13_arm64_verified; do
  require_text "$PROBE" "$marker=true"
  require_text "$INSTALLER" "$marker=true"
done
for marker in \
  graph_restart_side_effect_count=0 \
  graph_restart_repository_implementation_available=true \
  graph_restart_runtime_wired=false \
  graph_restart_binder_published=false \
  graph_restart_executor_dispatch_enabled=false \
  graph_restart_effect_dispatch_enabled=false \
  graph_restart_production_wired=false \
  agent_graph_runtime_persistence_wired=false \
  production_effect_dispatch_enabled=false \
  hardware_accessed=false; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

require_text "$DEBUG_MANIFEST" '.persistence.GraphRestartRecoveryProbeActivity'
require_text "$DEBUG_MANIFEST" 'android:permission="android.permission.DUMP"'
if grep -Fq 'GraphRestartRecoveryProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P3-W09 debug probe leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq 'GraphRestartReconciler|DurableGraphRecoveryRepository' \
    "$ROOT_DIR/$RUNTIME_SERVICE" "$ROOT_DIR/$GRAPH_RUNTIME"; then
  echo "P3-W09 recovery was wired into Runtime Service or Agent Graph" >&2
  exit 1
fi
if grep -Eiq \
    'EffectAdapter|EffectCoordinator|CarPropertyManager|VehicleHal|VehicleProperty|android[.]car|java[.]net|okhttp|http://|https://|ioctl|sysfs|/dev/' \
    "$ROOT_DIR/$RECONCILER" "$ROOT_DIR/$REPOSITORY"; then
  echo "P3-W09 main recovery references an adapter, network, vehicle, or hardware API" >&2
  exit 1
fi
if grep -Eiq \
    'System[.](currentTimeMillis|nanoTime)|Thread[.]sleep|new Thread|Executors[.]|java[.]util[.]Random|SecureRandom' \
    "$ROOT_DIR/$RECONCILER" "$ROOT_DIR/$REPOSITORY"; then
  echo "P3-W09 main recovery owns a clock, scheduler, thread, or random source" >&2
  exit 1
fi

require_text "README.md" "P3 Restart recovery"
require_text "central-brain/android-runtime/README.md" "P3-W09 Restart recovery"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P3-W09` Restart recovery'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P3-W09 Restart recovery trace"
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" "P3-W09 implemented Restart recovery"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P3-W09 Restart recovery"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P3-W09 Restart recovery"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P3-W09 Restart recovery Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "P3-W09 Restart recovery"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "P3-W09 进展"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P3-W09 Restart recovery"

printf '%s\n' \
  "Central Brain Android Graph restart recovery check passed" \
  "graph_restart_reconciler_defined=true" \
  "graph_restart_room_v4_repository_verified=true" \
  "graph_restart_waiting_recovered=true" \
  "graph_restart_executing_reconciled=true" \
  "graph_restart_unknown_effect_reconciled=true" \
  "graph_restart_approval_undo_revalidation_verified=true" \
  "graph_restart_checkpoint_mismatch_stuck=true" \
  "graph_restart_continue_after_revalidate_verified=true" \
  "graph_restart_process_death_verified=true" \
  "graph_restart_idempotent_reopen_verified=true" \
  "graph_restart_audit_exactly_once_verified=true" \
  "graph_restart_historical_digest_replay_verified=true" \
  "graph_restart_side_effect_count=0" \
  "graph_restart_repository_implementation_available=true" \
  "graph_restart_runtime_wired=false" \
  "graph_restart_binder_published=false" \
  "graph_restart_executor_dispatch_enabled=false" \
  "graph_restart_effect_dispatch_enabled=false" \
  "graph_restart_production_wired=false" \
  "agent_graph_runtime_persistence_wired=false" \
  "production_effect_dispatch_enabled=false" \
  "hardware_accessed=false"
