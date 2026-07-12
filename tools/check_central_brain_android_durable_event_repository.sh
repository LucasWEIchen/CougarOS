#!/usr/bin/env bash
set -euo pipefail

# Req IDs: XSC-002/004/005, FW-U-003/004, NV-G-004/006/007, NV-P-002/006, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PERSISTENCE_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence"
REPOSITORY="$PERSISTENCE_ROOT/DurableEventCursorRepository.java"
DAO="$PERSISTENCE_ROOT/RuntimeStateDao.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/persistence/DurableEventCursorRepositoryProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android durable Event repository file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android durable Event repository pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in "$REPOSITORY" "$DAO" "$PROBE" "$DEBUG_MANIFEST" "$INSTALLER"; do
  require_file "$path"
done

require_text "$REPOSITORY" "STATE_ACTIVE"
require_text "$REPOSITORY" "STATE_RESYNC_REQUIRED"
require_text "$REPOSITORY" "STATE_CANCELLED"
require_text "$REPOSITORY" "RegisterResult register("
require_text "$REPOSITORY" "AckOutcome acknowledgeOwned("
require_text "$REPOSITORY" "OverflowOutcome markOverflowOwned("
require_text "$REPOSITORY" "ResyncOutcome completeResyncOwned("
require_text "$REPOSITORY" "CancelOutcome cancelOwned("
require_text "$REPOSITORY" "SOURCE_REGRESSION"
require_text "$REPOSITORY" "requiresDurableMonotonicEventSource()"
require_text "$REPOSITORY" "isProductionWired()"
require_text "$REPOSITORY" "database.runInTransaction(operation)"
require_text "$DAO" "countActiveEventCursorsByOwner("
require_text "$DAO" "countCancelledEventCursors()"
require_text "$DAO" "findOldestCancelledEventCursorExcept("
require_text "$DAO" "deleteCancelledEventCursor("
require_text "$DEBUG_MANIFEST" ".persistence.DurableEventCursorRepositoryProbeActivity"

for marker in \
  "durable_event_cursor_repository_verified=true" \
  "event_cursor_registration_idempotency_verified=true" \
  "event_cursor_admission_bounds_verified=true" \
  "event_cursor_owner_isolation_verified=true" \
  "event_cursor_ack_monotonic_verified=true" \
  "event_cursor_source_regression_blocked=true" \
  "event_cursor_overflow_resync_verified=true" \
  "event_cursor_reopen_recovery_verified=true" \
  "event_cursor_cancel_idempotency_verified=true" \
  "event_cursor_record_bounds_verified=true" \
  "event_cursor_audit_exactly_once_verified=true" \
  "event_cursor_probe_persistence_verified=true" \
  "event_cursor_repository_implementation_available=true" \
  "event_cursor_repository_production_wired=false" \
  "event_cursor_persistence_wired=false" \
  "durable_event_source_available=false" \
  "event_broker_production_wired=false"; do
  require_text "$PROBE" "${marker%%=*}="
  require_text "$INSTALLER" "$marker"
done

for service in \
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java \
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java \
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainDiagnosticService.java; do
  if grep -Fq "DurableEventCursorRepository" "$ROOT_DIR/$service"; then
    echo "R6A2B durable Event repository must not be wired into production Services" >&2
    exit 1
  fi
done

if grep -R -Eiq \
    'java\.net|okhttp|http://|https://|ioctl|sysfs|/dev/|CarPropertyManager|VehicleHal|SocketCAN|SharedMemory' \
    "$ROOT_DIR/$REPOSITORY" "$ROOT_DIR/$PROBE"; then
  echo "R6A2B durable Event repository unexpectedly references transport or hardware" >&2
  exit 1
fi
if grep -Eiq 'raw[_ ]?payload|utterance|model[_ ]?output|vehicle[_ ]?frame' \
    "$ROOT_DIR/$REPOSITORY"; then
  echo "R6A2B durable Event repository must persist metadata only" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "R6A2B Durable Event Repository"
require_text "docs/CENTRAL_BRAIN_ANDROID_RUNTIME_EVOLUTION_PLAN.md" "R6A2B durable Event repository"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R6A2B durable Event repository trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android R6A2B Durable Event Repository"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R6A2B Durable Event Repository"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R6A2B Event Repository Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "R6A2B 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "R6A2B 进展"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "R6A2B durable Event repository"

echo "Central Brain Android durable Event repository check passed"
