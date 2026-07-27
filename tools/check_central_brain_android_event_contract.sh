#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AIDL_DIR="central-brain/android-runtime/central-brain-sdk/src/main/aidl/com/centralbrain/sdk/event"
JAVA_CONTRACT="central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/event/EventContract.java"
UNIT_TEST="central-brain/android-runtime/central-brain-sdk/src/test/java/com/centralbrain/sdk/event/EventContractTest.java"
DEVICE_TEST="central-brain/android-runtime/central-brain-sdk/src/androidTest/java/com/centralbrain/sdk/session/SessionParcelInstrumentation.java"
HASH_MANIFEST="central-brain/android-runtime/central-brain-sdk/aidl-api/events-v1.sha256"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Event contract file: $path" >&2
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
    echo "missing Event contract pattern '$pattern' in $path" >&2
    exit 1
  fi
}

AIDL_TYPES=(
  RuntimeEvent
  ActionEvent
  ObservationEvent
  MessageEvent
  EventPage
  ICentralBrainSessionEventCallback
  ICentralBrainSessionEvents
)
for name in "${AIDL_TYPES[@]}"; do
  require_file "$AIDL_DIR/$name.aidl"
done
for name in RuntimeEvent ActionEvent ObservationEvent MessageEvent EventPage; do
  require_text "$AIDL_DIR/$name.aidl" "int schemaVersion = 1;"
done

require_text "$AIDL_DIR/RuntimeEvent.aidl" 'String eventId = "";'
require_text "$AIDL_DIR/RuntimeEvent.aidl" 'long sequence = 0;'
require_text "$AIDL_DIR/RuntimeEvent.aidl" 'String parentEventId = "";'
require_text "$AIDL_DIR/RuntimeEvent.aidl" 'long parentSequence = 0;'
require_text "$AIDL_DIR/RuntimeEvent.aidl" 'long occurredAtEpochMs = 0;'
require_text "$AIDL_DIR/RuntimeEvent.aidl" 'String eventDigest = "";'
require_text "$AIDL_DIR/EventPage.aidl" 'RuntimeEvent[] events = {};'
require_text "$AIDL_DIR/EventPage.aidl" 'boolean redactionApplied = false;'
require_text "$AIDL_DIR/MessageEvent.aidl" 'boolean redacted = false;'

INTERFACE="$AIDL_DIR/ICentralBrainSessionEvents.aidl"
CALLBACK="$AIDL_DIR/ICentralBrainSessionEventCallback.aidl"
require_text "$INTERFACE" 'const int INTERFACE_VERSION = 1;'
require_text "$INTERFACE" 'const int MAX_PAGE_SIZE = 100;'
require_text "$INTERFACE" 'EventPage getEvents(String sessionId, String cursor, int limit);'
require_text "$INTERFACE" 'boolean registerSessionCallback('
require_text "$INTERFACE" 'boolean unregisterSessionCallback('
require_text "$CALLBACK" 'oneway interface ICentralBrainSessionEventCallback'
require_text "$CALLBACK" 'void onEvent(in RuntimeEvent event);'
require_text "$CALLBACK" 'void onOverflow(String resumeCursor);'
require_text "$CALLBACK" 'void onClosed(int reasonCode, String resumeCursor);'

declared_hash="$(
  sed -nE 's/.*INTERFACE_HASH = "([0-9a-f]{64})";.*/\1/p' "$ROOT_DIR/$INTERFACE"
)"
if [[ ${#declared_hash} -ne 64 ]]; then
  echo "Event interface hash must be a 64-character lowercase SHA-256 token" >&2
  exit 1
fi
computed_hash="$({
  for name in "${AIDL_TYPES[@]}"; do
    sed -E \
      's/const String INTERFACE_HASH = "[0-9a-f]{64}";/const String INTERFACE_HASH = "<generated-by-checker>";/' \
      "$ROOT_DIR/$AIDL_DIR/$name.aidl"
  done
} | sha256sum | awk '{print $1}')"
if [[ "$computed_hash" != "$declared_hash" ]]; then
  echo "Event interface hash drift: declared=$declared_hash computed=$computed_hash" >&2
  exit 1
fi

require_file "$JAVA_CONTRACT"
for marker in \
  'MAX_PAGE_SIZE = 100' \
  'MAX_DISPLAY_TEXT_CHARS = 1024' \
  'UserMessageReceived' \
  'ActionAuthorized' \
  'EffectObserved' \
  'AssistantSummaryCreated' \
  'event.type is not allowlisted' \
  'page event sequence is not contiguous and ordered' \
  'event parent is missing or ordered after the child' \
  'observation subject does not match event type' \
  'redacted message has unsafe display text or reason' \
  'cursor replay does not continue the previous page' \
  'event identity was mutated during replay' \
  'CB_EVENT_CONTRACT:'; do
  require_text "$JAVA_CONTRACT" "$marker"
done

require_file "$UNIT_TEST"
for marker in \
  'acceptsTypedEventsAndBoundedPages' \
  'rejectsUnknownVersionTypeAndPayloadMismatch' \
  'rejectsOrderingGapAndInvalidParent' \
  'rejectsUnsafeRedactionAndPageMarkerMismatch' \
  'rejectsCursorReplayGapAndMutation' \
  'rejectsOversizePageMessageAndUnknownObservationEnums'; do
  require_text "$UNIT_TEST" "$marker"
done

require_file "$DEVICE_TEST"
for marker in \
  'event_parcel_round_trip_verified=true' \
  'event_ordering_rejected=true' \
  'event_parent_rejected=true' \
  'event_redaction_rejected=true' \
  'event_cursor_replay_verified=true' \
  'event_runtime_service_published=false' \
  'event_callback_service_published=false'; do
  require_text "$DEVICE_TEST" "$marker"
done

if grep -R -Eiq 'Bundle|ParcelFileDescriptor|SharedMemory|FileDescriptor|rawPayload|rawVehicle|rawModel' \
    "$ROOT_DIR/$AIDL_DIR"; then
  echo "Event AIDL must remain structured, bounded, digest-oriented and handle-free" >&2
  exit 1
fi
if grep -Eiq 'speed|gear|belt|occupancy|caller|signer|signature|permission' \
    "$ROOT_DIR/$AIDL_DIR/ICentralBrainSessionEvents.aidl"; then
  echo "Event query/callback contract must not accept identity or vehicle-safety assertions" >&2
  exit 1
fi

require_file "$HASH_MANIFEST"
(cd "$ROOT_DIR" && sha256sum -c "$HASH_MANIFEST" >/dev/null)
for manifest in v1.sha256 governance-v1.sha256 session-v1.sha256 plan-v1.sha256; do
  (cd "$ROOT_DIR" && sha256sum -c \
    "central-brain/android-runtime/central-brain-sdk/aidl-api/$manifest" >/dev/null)
done

for doc in \
  README.md \
  central-brain/README.md \
  central-brain/android-runtime/README.md \
  docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md \
  docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md; do
  require_text "$doc" "P1-W03"
done
require_text "README.md" "event_contract_v1_defined=true"
require_text "README.md" "event_parcel_physical_android13_arm64_verified=true"
require_text "README.md" "event_runtime_service_published=true"
require_text "README.md" "event_callback_service_published=true"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" \
  "event_contract_v1_defined=true"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" \
  "event_parcel_physical_android13_arm64_verified=true"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" \
  "P1-W03 Event Contract Driver/HAL Boundary"

echo "Central Brain Android Event contract V1 check passed"
