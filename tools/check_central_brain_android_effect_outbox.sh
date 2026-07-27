#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004, FW-U-004/005, NV-F-001, NV-G-006/007, DEL-001/004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPOSITORY="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence/DurableEffectRepository.java"
DAO="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence/RuntimeStateDao.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/persistence/DurableEffectRepositoryProbeActivity.java"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android effect/outbox file: $path" >&2
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
    echo "missing Android effect/outbox pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in "$REPOSITORY" "$DAO" "$PROBE" "$RUNTIME" "$GOVERNANCE"; do
  require_file "$path"
done

require_text "$REPOSITORY" "central-brain-effect-idempotency-v1"
require_text "$REPOSITORY" "DurableTaskRepository.STATE_RUNNING"
require_text "$REPOSITORY" "dao.insertPendingEffect(effect)"
require_text "$REPOSITORY" "dao.insertOutbox(outbox)"
require_text "$REPOSITORY" "AUDIT_EFFECT_PREPARED"
require_text "$REPOSITORY" "findNextClaimableOutbox"
require_text "$REPOSITORY" "outbox.attemptCount++"
require_text "$REPOSITORY" "AUDIT_EFFECT_CLAIMED"
require_text "$REPOSITORY" "reconcileInterruptedClaims"
require_text "$REPOSITORY" "AUDIT_EFFECT_CLAIM_RECOVERED"
require_text "$REPOSITORY" "return false"
require_text "$DAO" "INNER JOIN pending_effect"
require_text "$DAO" "INNER JOIN runtime_task"
require_text "$DAO" "runtime_task.state = 'RUNNING'"
require_text "$DAO" "updatePendingEffect"
require_text "$DAO" "updateOutbox"
require_text "$PROBE" "effect_prepare_transaction_verified="
require_text "$PROBE" "effect_non_running_task_rejected="
require_text "$PROBE" "effect_route_mismatch_rejected="
require_text "$PROBE" "effect_owner_scoped_token_verified="
require_text "$PROBE" "effect_reopen_replay_verified="
require_text "$PROBE" "effect_owner_scope_verified="
require_text "$PROBE" "outbox_reopen_requeue_verified="
require_text "$PROBE" "outbox_fair_requeue_verified="
require_text "$PROBE" "outbox_claim_attempt="
require_text "$PROBE" "effect_repository_wired=false"
require_text "$PROBE" "outbox_dispatch_enabled="
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" ".persistence.DurableEffectRepositoryProbeActivity"
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" 'android:permission="android.permission.DUMP"'
require_text "tools/install_central_brain_android_runtime.sh" "effect_prepare_transaction_verified=true"
require_text "tools/install_central_brain_android_runtime.sh" "outbox_fair_requeue_verified=true"
require_text "tools/install_central_brain_android_runtime.sh" "effect_repository_wired=false"
require_text "tools/install_central_brain_android_runtime.sh" "outbox_dispatch_enabled=false"

if grep -Fq "DurableEffectRepositoryProbeActivity" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"; then
  echo "durable effect/outbox probe must remain debug-only" >&2
  exit 1
fi
if grep -Fq "DurableEffectRepository" "$ROOT_DIR/$RUNTIME" \
    || grep -Fq "DurableEffectRepository" "$ROOT_DIR/$GOVERNANCE"; then
  echo "R4C2A repository must not be wired to production Services" >&2
  exit 1
fi
if grep -Eiq 'dispatchAction|invokeAdapter|sendEffect' \
    "$ROOT_DIR/$REPOSITORY" "$ROOT_DIR/$PROBE"; then
  echo "effect/outbox repository must not dispatch effects or invoke adapters" >&2
  exit 1
fi
if grep -R -Eiq 'ioctl|sysfs|/dev/|VehicleHal|CarPropertyManager|vendor sdk|SharedMemory' \
    "$ROOT_DIR/$REPOSITORY" "$ROOT_DIR/$PROBE"; then
  echo "R4C2A effect/outbox repository unexpectedly references hardware" >&2
  exit 1
fi

require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "R4C2A effect prepare and claim"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4C2A effect prepare and claim trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android R4C2A Effect Prepare And Claim"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android R4C2A Effect Prepare And Claim"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4C2A Effect Outbox Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4C2A 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4C2A 进展"

echo "Central Brain Android effect/outbox check passed"
