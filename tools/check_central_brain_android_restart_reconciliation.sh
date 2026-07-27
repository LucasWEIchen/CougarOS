#!/usr/bin/env bash
set -euo pipefail

# Req IDs: FW-U-004, NV-F-001, NV-G-003/005/006/007, XSC-005/006, DEL-001/004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
CLIENT="central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/CentralBrainClient.java"
REPOSITORY="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence/DurableTaskRepository.java"
DAO="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence/RuntimeStateDao.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/persistence/RestartReconciliationProbeActivity.java"
INSTRUMENTATION="central-brain/android-runtime/demo-hmi/src/androidTest/java/com/centralbrain/demo/test/CentralBrainBinderInstrumentation.java"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android restart reconciliation file: $path" >&2
    exit 1
  fi
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
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android restart reconciliation pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in "$SERVICE" "$CLIENT" "$REPOSITORY" "$DAO" "$PROBE" "$INSTRUMENTATION"; do
  require_file "$path"
done

require_text "$DAO" "findTasksNeedingRestartReconciliation"
require_text "$DAO" "terminal_delivery_settled = 0"
require_text "$REPOSITORY" "reconcileInterruptedTasks"
require_text "$REPOSITORY" "AUDIT_TASK_RESTART_RECONCILED"
require_text "$REPOSITORY" "previousState + \"->\" + STATE_FAILED"
require_text "$REPOSITORY" "task.terminalDeliverySettled = false"
require_text "$REPOSITORY" "database.runInTransaction"
require_text "$SERVICE" "startupReconciliation = executor.submit"
require_text "$SERVICE" "awaitStartupReconciliation();"
require_text "$SERVICE" "restart_reconciliation_pending=true"
require_text "$SERVICE" "restart reconciliation completed"
require_text "$SERVICE" "task_execution_resume_enabled=false"
require_text "$SERVICE" "notifyDurableReplayWithoutLiveRecord"
require_text "$SERVICE" "central-brain-restart-replay-settlement-v1"
require_text "$CLIENT" "SerialExecutor"
require_text "$CLIENT" "deliveryExecutor.execute"
require_text "$CLIENT" "public synchronized void onTaskUpdate"
require_text "$CLIENT" "public synchronized void onTaskFailed"
require_text "$PROBE" "active_task_reconciled_failed="
require_text "$PROBE" "incomplete_completion_reconciled_failed="
require_text "$PROBE" "restart_reconciliation_idempotent="
require_text "$PROBE" "task_execution_resume_enabled=false"
require_text "$INSTRUMENTATION" "restart_reconciliation_verified=true"
require_text "$INSTRUMENTATION" "TASK_STATE_FAILED"
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" ".persistence.RestartReconciliationProbeActivity"
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" 'android:permission="android.permission.DUMP"'
require_text "tools/install_central_brain_android_runtime.sh" "restart_reconciliation_enabled=true"
require_text "tools/install_central_brain_android_runtime.sh" "incomplete_completion_reconciled_failed=true"
require_text "tools/test_central_brain_android_binder_lifecycle.sh" "restart_reconciliation_verified=true"

if grep -Fq "RestartReconciliationProbeActivity" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"; then
  echo "restart reconciliation probe must remain debug-only" >&2
  exit 1
fi
if grep -R -Fq "task_execution_resume_enabled=true" \
    "$ROOT_DIR/$SERVICE" "$ROOT_DIR/$REPOSITORY" "$ROOT_DIR/$PROBE"; then
  echo "R4C1 must not claim task execution resume" >&2
  exit 1
fi
if grep -Eiq 'PendingEffectEntity|OutboxEntity|enqueueEffect|dispatchAction|resumeTask' \
    "$ROOT_DIR/$SERVICE" "$ROOT_DIR/$REPOSITORY" "$ROOT_DIR/$PROBE"; then
  echo "R4C1 restart reconciliation must not enqueue, dispatch, or resume effects" >&2
  exit 1
fi
if grep -R -Eiq 'ioctl|sysfs|/dev/|VehicleHal|CarPropertyManager|vendor sdk|SharedMemory' \
    "$ROOT_DIR/$SERVICE" "$ROOT_DIR/$REPOSITORY" "$ROOT_DIR/$PROBE"; then
  echo "R4C1 restart reconciliation unexpectedly references hardware" >&2
  exit 1
fi

require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "R4C1 fail-closed restart reconciliation"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4C1 fail-closed restart reconciliation trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android R4C1 Fail-Closed Restart Reconciliation"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android R4C1 Fail-Closed Restart Reconciliation"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4C1 Restart Reconciliation Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4C1 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4C1 进展"

echo "Central Brain Android restart reconciliation check passed"
