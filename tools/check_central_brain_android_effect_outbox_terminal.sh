#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004, FW-U-004/005, NV-F-001, NV-G-006/007, DEL-001/004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPOSITORY="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence/DurableEffectRepository.java"
DAO="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence/RuntimeStateDao.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/persistence/DurableEffectRepositoryProbeActivity.java"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android effect terminal file: $path" >&2
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
    echo "missing Android effect terminal pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in "$REPOSITORY" "$DAO" "$PROBE" "$RUNTIME" "$GOVERNANCE" "$INSTALLER"; do
  require_file "$path"
done

for pattern in \
  "DEFAULT_MAX_ATTEMPTS = 3" \
  "recordSuccess(" \
  "scheduleRetry(" \
  "deadLetter(" \
  "cancelPrepared(" \
  "EFFECT_STATE_APPLIED" \
  "EFFECT_STATE_FAILED" \
  "EFFECT_STATE_CANCELLED" \
  "OUTBOX_STATE_DELIVERED" \
  "OUTBOX_STATE_DEAD_LETTER" \
  "OUTBOX_STATE_CANCELLED" \
  "AUDIT_EFFECT_RETRY_SCHEDULED" \
  "AUDIT_EFFECT_APPLIED" \
  "AUDIT_EFFECT_DEAD_LETTERED" \
  "AUDIT_EFFECT_CANCELLED" \
  "AUDIT_EFFECT_CLAIM_EXHAUSTED" \
  "Long.toString(retryDelayMs)" \
  "rows.outbox.attemptCount != expectedAttempt" \
  "outbox.attemptCount >= maxAttempts"; do
  require_text "$REPOSITORY" "$pattern"
done

require_text "$DAO" "effect_outbox.attempt_count < :maxAttempts"
require_text "$DAO" "findLatestAuditEvent"
require_text "$PROBE" "effect_retry_delay_conflict_verified="
require_text "$PROBE" "effect_cancel_stale_attempt_rejected="
require_text "$PROBE" "effect_max_attempt_crash_dead_lettered="
require_text "$PROBE" "effect_exhausted_reconciliation_idempotent="
require_text "$PROBE" "effect_terminal_states_verified="
require_text "$PROBE" "effect_repository_wired=false"
require_text "$PROBE" "outbox_dispatch_enabled="

for flag in \
  "effect_retry_idempotent_verified=true" \
  "effect_retry_delay_conflict_verified=true" \
  "effect_retry_not_before_verified=true" \
  "effect_final_claim_verified=true" \
  "effect_attempt_limit_verified=true" \
  "effect_dead_letter_idempotent_verified=true" \
  "effect_stale_attempt_rejected=true" \
  "effect_success_idempotent_verified=true" \
  "effect_cancel_stale_attempt_rejected=true" \
  "effect_max_attempt_crash_dead_lettered=true" \
  "effect_exhausted_reconciliation_idempotent=true" \
  "effect_terminal_states_verified=true" \
  "effect_repository_wired=false" \
  "outbox_dispatch_enabled=false"; do
  require_text "$INSTALLER" "$flag"
done

if grep -Fq "DurableEffectRepository" "$ROOT_DIR/$RUNTIME" \
    || grep -Fq "DurableEffectRepository" "$ROOT_DIR/$GOVERNANCE"; then
  echo "R4C2B repository must not be wired to production Services" >&2
  exit 1
fi
if grep -R -Eiq \
    'dispatchAction|invokeAdapter|sendEffect|ioctl|sysfs|/dev/|VehicleHal|CarPropertyManager|vendor sdk|SharedMemory' \
    "$ROOT_DIR/$REPOSITORY" "$ROOT_DIR/$PROBE"; then
  echo "R4C2B unexpectedly dispatches or references hardware" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "R4C2B Effect Retry And Terminal States"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "R4C2B effect retry and terminal states"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4C2B effect retry and terminal state trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android R4C2B Effect Retry And Terminal States"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android R4C2B Effect Retry And Terminal States"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4C2B Effect Terminal State Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4C2B 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4C2B 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4C2B repository-only retry/terminal"

echo "Central Brain Android effect retry/terminal check passed"
