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

RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
SESSION_ENDPOINT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/session/TransientSessionEndpoint.java"
for service in \
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java \
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainDiagnosticService.java; do
  if grep -Fq "DurableEventCursorRepository" "$ROOT_DIR/$service"; then
    echo "R6A2B durable Event repository escaped the owner-scoped Session Event V2 surface" >&2
    exit 1
  fi
done
require_text "$RUNTIME_SERVICE" "DurableEventCursorRepository.create(database, 128, 16, 64)"
require_text "$SESSION_ENDPOINT" "durableEventCursors.registerSession("
require_text "$SESSION_ENDPOINT" "durableEventCursors.acknowledgeSessionOwned("
if grep -Eiq 'InProcessDurableEventBroker|EventBroker[ (]|java[.]net|okhttp|http://|https://|WebSocket' \
    "$ROOT_DIR/$RUNTIME_SERVICE" "$ROOT_DIR/$SESSION_ENDPOINT"; then
  echo "Event V2 durable cursor wiring must not activate production middleware" >&2
  exit 1
fi

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
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "R6A2B durable Event repository"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6A2B durable Event repository trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android R6A2B Durable Event Repository"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android R6A2B Durable Event Repository"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6A2B Event Repository Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6A2B 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6A2B 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6A2B durable Event repository"

echo "Central Brain Android durable Event repository check passed"
