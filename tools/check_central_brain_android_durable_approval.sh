#!/usr/bin/env bash
set -euo pipefail

# Req IDs: FW-U-004/007, NV-F-001, NV-G-005/006/007, XSC-005/006, DEL-001/004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
REPOSITORY="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence/DurableApprovalRepository.java"
DAO="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence/RuntimeStateDao.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/persistence/DurableApprovalRepositoryProbeActivity.java"
DEMO="central-brain/android-runtime/demo-hmi/src/main/java/com/centralbrain/demo/DemoActivity.java"
GOVERNANCE_AIDL="central-brain/android-runtime/central-brain-sdk/src/main/aidl/com/centralbrain/sdk/governance/ICentralBrainGovernance.aidl"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android durable approval file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android durable approval pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in "$SERVICE" "$REPOSITORY" "$DAO" "$PROBE" "$DEMO" "$GOVERNANCE_AIDL"; do
  require_file "$path"
done

require_text "$SERVICE" "CentralBrainDatabase.open(this)"
require_text "$SERVICE" "DurableApprovalRepository.create"
require_text "$SERVICE" "DurablePrincipalFingerprint.from(caller)"
require_text "$SERVICE" "approval_durable="
require_text "$SERVICE" "status.durable = true"
require_text "$SERVICE" "status.grantSupported = false"
require_text "$REPOSITORY" "database.runInTransaction"
require_text "$REPOSITORY" "findApprovalByOwnerAndIdempotency"
require_text "$REPOSITORY" "findExpiredPendingApprovals"
require_text "$REPOSITORY" "AUDIT_APPROVAL_REQUESTED"
require_text "$REPOSITORY" "AUDIT_APPROVAL_CANCELLED"
require_text "$REPOSITORY" "AUDIT_APPROVAL_EXPIRED"
require_text "$REPOSITORY" "ApprovalRejectedException"
require_text "$REPOSITORY" "supportsApprovalGrant"
require_text "$REPOSITORY" "return true"
require_text "$DAO" "updateApproval"
require_text "$DAO" "countApprovalsInState"
require_text "$PROBE" "approval_reopen_replay_verified="
require_text "$PROBE" "approval_idempotency_conflict_verified="
require_text "$PROBE" "approval_owner_isolation_verified="
require_text "$PROBE" "approval_cancel_idempotency_verified="
require_text "$PROBE" "approval_expiry_verified="
require_text "$PROBE" "approval_grant_supported=false"
require_text "$DEMO" "replayedHandle"
require_text "$DEMO" "approval_idempotent_replay_verified=true"
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" ".persistence.DurableApprovalRepositoryProbeActivity"
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" 'android:permission="android.permission.DUMP"'
require_text "tools/install_central_brain_android_runtime.sh" "approval_reopen_replay_verified=true"
require_text "tools/install_central_brain_android_runtime.sh" "production_durable_approval_verified=true"
require_text "tools/install_central_brain_android_runtime.sh" "approval_durable=true"
require_text "tools/install_central_brain_android_runtime.sh" "approval_grant_supported=false"
require_text "tools/install_central_brain_android_runtime.sh" "service_dispatch_triggered=false"

if grep -Fq "InMemoryApprovalRegistry" "$ROOT_DIR/$SERVICE"; then
  echo "R4B3 Governance Service must not use the process-local approval registry" >&2
  exit 1
fi
if grep -Eq 'approve|grantApproval|APPROVED' "$ROOT_DIR/$GOVERNANCE_AIDL"; then
  echo "R4B3 must not add an approval grant API" >&2
  exit 1
fi
if grep -Fq "DurableApprovalRepositoryProbeActivity" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"; then
  echo "durable approval probe must remain debug-only" >&2
  exit 1
fi
if grep -Eiq 'PendingEffectEntity|OutboxEntity|enqueueEffect|dispatchAction' \
    "$ROOT_DIR/$SERVICE" "$ROOT_DIR/$REPOSITORY"; then
  echo "R4B3 approval persistence must not enqueue or dispatch effects" >&2
  exit 1
fi
if grep -R -Eiq 'ioctl|sysfs|/dev/|VehicleHal|CarPropertyManager|vendor sdk|SharedMemory' \
    "$ROOT_DIR/$SERVICE" "$ROOT_DIR/$REPOSITORY" "$ROOT_DIR/$PROBE"; then
  echo "R4B3 approval persistence unexpectedly references hardware or shared memory" >&2
  exit 1
fi

require_text "docs/CENTRAL_BRAIN_ANDROID_RUNTIME_EVOLUTION_PLAN.md" "R4B3 durable approval"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R4B3 durable approval trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android R4B3 Durable Approval"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R4B3 Durable Approval"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R4B3 Durable Approval Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "R4B3 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "R4B3 进展"

echo "Central Brain Android durable approval check passed"
