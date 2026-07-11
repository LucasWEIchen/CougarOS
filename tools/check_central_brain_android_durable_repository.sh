#!/usr/bin/env bash
set -euo pipefail

# Req IDs: FW-U-004, NV-F-001, NV-G-006, NV-G-007, XSC-005, DEL-001/004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PERSISTENCE_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence"
IDENTITY_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/identity"
REPOSITORY="$PERSISTENCE_ROOT/DurableTaskRepository.java"
DAO="$PERSISTENCE_ROOT/RuntimeStateDao.java"
FINGERPRINT="$IDENTITY_ROOT/DurablePrincipalFingerprint.java"
FINGERPRINT_TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/identity/DurablePrincipalFingerprintTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/persistence/DurableRepositoryProbeActivity.java"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android durable repository file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android durable repository pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in "$REPOSITORY" "$DAO" "$FINGERPRINT" "$FINGERPRINT_TEST" "$PROBE"; do
  require_file "$path"
done

require_text "$FINGERPRINT" "central-brain-principal-v1"
require_text "$FINGERPRINT" "getAndroidUserSerial()"
require_text "$FINGERPRINT" "getCurrentSignerSha256()"
require_text "$FINGERPRINT_TEST" "isCanonicalAndStableAcrossUidReassignment"
require_text "$FINGERPRINT_TEST" "bindsAndroidUserPackageAndSignerPairs"
require_text "$REPOSITORY" "database.runInTransaction"
require_text "$REPOSITORY" "findTaskByOwnerAndIdempotency"
require_text "$REPOSITORY" "IdempotencyConflictException"
require_text "$REPOSITORY" "dao.insertTask(task)"
require_text "$REPOSITORY" "dao.insertAuditEvent(audit)"
require_text "$DAO" "owner_fingerprint = :ownerFingerprint"
require_text "$DAO" "idempotency_key = :idempotencyKey"
require_text "$PROBE" "task_admission_transaction_verified="
require_text "$PROBE" "task_idempotent_replay_verified="
require_text "$PROBE" "task_idempotency_conflict_verified="
require_text "$PROBE" "task_owner_isolation_verified="
require_text "$PROBE" "runtime_repository_wired=false"
require_text "$PROBE" "durable_dispatch_enabled=false"
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" ".persistence.DurableRepositoryProbeActivity"
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" 'android:permission="android.permission.DUMP"'
require_text "tools/install_central_brain_android_runtime.sh" "task_admission_transaction_verified=true"
require_text "tools/install_central_brain_android_runtime.sh" "task_idempotent_replay_verified=true"
require_text "tools/install_central_brain_android_runtime.sh" "runtime_repository_wired=false"

if grep -Fq "getUid()" "$ROOT_DIR/$FINGERPRINT"; then
  echo "durable principal fingerprint must not bind ownership to ephemeral UID" >&2
  exit 1
fi
if grep -Eiq 'utterance|model output|vehicle frame|signer bytes' "$ROOT_DIR/$REPOSITORY"; then
  echo "R4B1 repository must persist metadata/digests only" >&2
  exit 1
fi
if grep -R -Fq "DurableTaskRepository" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"; then
  echo "R4B1 repository must not be wired to production Services yet" >&2
  exit 1
fi
if grep -Fq "DurableRepositoryProbeActivity" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"; then
  echo "durable repository probe must remain debug-only" >&2
  exit 1
fi
if grep -R -Eiq 'ioctl|sysfs|/dev/|VehicleHal|CarPropertyManager|vendor sdk|SharedMemory' \
    "$ROOT_DIR/$REPOSITORY" "$ROOT_DIR/$FINGERPRINT" "$ROOT_DIR/$PROBE"; then
  echo "R4B1 durable repository unexpectedly references hardware or shared memory" >&2
  exit 1
fi

require_text "docs/CENTRAL_BRAIN_ANDROID_RUNTIME_EVOLUTION_PLAN.md" "R4B1 durable task admission"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R4B1 durable task admission trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android R4B1 Durable Task Admission"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R4B1 Durable Task Admission"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R4B1 Durable Repository Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "R4B1 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "R4B1 进展"

echo "Central Brain Android durable repository check passed"
