#!/usr/bin/env bash
set -euo pipefail

# Req IDs: FW-U-004, NV-F-001, NV-G-003/005/006/007, XSC-005/006, DEL-001/004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
REPOSITORY="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence/DurableTaskRepository.java"
DIGEST="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence/DurableDigest.java"
DAO="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence/RuntimeStateDao.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/persistence/RuntimeDurabilityProbeActivity.java"
DEMO="central-brain/android-runtime/demo-hmi/src/main/java/com/centralbrain/demo/DemoActivity.java"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android durable Runtime wiring file: $path" >&2
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
    echo "missing Android durable Runtime wiring pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in "$RUNTIME" "$GOVERNANCE" "$REPOSITORY" "$DIGEST" "$DAO" "$PROBE" "$DEMO"; do
  require_file "$path"
done
require_file "central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/persistence/DurableDigestTest.java"

require_text "$RUNTIME" "CentralBrainDatabase.open(this)"
require_text "$RUNTIME" "DurablePrincipalFingerprint.from(caller)"
require_text "$RUNTIME" "taskRepository.admit"
require_text "$RUNTIME" "requestDigest(request)"
require_text "$RUNTIME" "persistTransition(record"
require_text "$RUNTIME" "settleTerminalDelivery(record)"
require_text "$RUNTIME" "MAX_REPLAY_CALLBACKS_PER_TASK"
require_text "$RUNTIME" "synchronized (admissionLock)"
require_text "$RUNTIME" "runtime_repository_wired=true"
require_text "$RUNTIME" "task_recovery_enabled=false"
require_text "$RUNTIME" "durable_dispatch_enabled=false"
require_text "$REPOSITORY" "database.runInTransaction"
require_text "$REPOSITORY" "dao.insertCheckpoint"
require_text "$REPOSITORY" "AUDIT_TASK_TRANSITION"
require_text "$REPOSITORY" "AUDIT_TERMINAL_DELIVERY_SETTLED"
require_text "$REPOSITORY" "AdmissionRejectedException"
require_text "$REPOSITORY" "creationAllowed"
require_text "$DIGEST" "Domain-separated length-framed SHA-256"
require_text "$DAO" "findLatestCheckpoint"
require_text "$DAO" "countSettledTerminalTasks"
require_text "$DEMO" "replayHandle"
require_text "$DEMO" "idempotent replay returned a different task handle"
require_text "$DEMO" "submitConcurrentReplayTask"
require_text "central-brain/android-runtime/demo-hmi/src/main/res/values/strings.xml" "Concurrent replay: completed"
require_text "$PROBE" "durable_completed_task_verified="
require_text "$PROBE" "durable_cancelled_task_verified="
require_text "$PROBE" "durable_checkpoint_chain_verified="
require_text "$PROBE" "durable_terminal_settlement_verified="
require_text "$PROBE" "task_recovery_enabled=false"
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" ".persistence.RuntimeDurabilityProbeActivity"
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" 'android:permission="android.permission.DUMP"'
require_text "tools/install_central_brain_android_runtime.sh" "durable_replay_callback_verified=true"
require_text "tools/install_central_brain_android_runtime.sh" "durable_concurrent_replay_verified=true"
require_text "tools/install_central_brain_android_runtime.sh" "durable_terminal_settlement_verified=true"
require_text "tools/install_central_brain_android_runtime.sh" "runtime_repository_wired=true"
require_text "tools/install_central_brain_android_runtime.sh" "task_recovery_enabled=false"
require_text "tools/test_central_brain_android_binder_lifecycle.sh" "durable_recovery_pending_verified=true"
require_text "central-brain/android-runtime/demo-hmi/src/androidTest/java/com/centralbrain/demo/test/CentralBrainBinderInstrumentation.java" "durable recovery-pending replay"

if grep -Fq "RuntimeDurabilityProbeActivity" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"; then
  echo "Runtime durability probe must remain debug-only" >&2
  exit 1
fi
if grep -Eiq 'PendingEffectEntity|OutboxEntity|service dispatch|action dispatch' \
    "$ROOT_DIR/$RUNTIME"; then
  echo "R4B2 Runtime wiring must not enqueue or dispatch effects" >&2
  exit 1
fi
if grep -R -Eiq 'ioctl|sysfs|/dev/|VehicleHal|CarPropertyManager|vendor sdk|SharedMemory' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$REPOSITORY" "$ROOT_DIR/$PROBE"; then
  echo "R4B2 Runtime wiring unexpectedly references hardware or shared memory" >&2
  exit 1
fi

require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "R4B2 durable Runtime wiring"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4B2 durable Runtime wiring trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android R4B2 Durable Runtime Wiring"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android R4B2 Durable Runtime Wiring"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4B2 Durable Runtime Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4B2 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4B2 进展"

echo "Central Brain Android durable Runtime wiring check passed"
