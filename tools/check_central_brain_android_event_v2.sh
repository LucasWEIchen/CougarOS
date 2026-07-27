#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-EVT-001, FW-U-003, NV-G-004/006/007,
# XSC-001/005/006, DEL-001/003/004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AIDL_DIR="central-brain/android-runtime/central-brain-sdk/src/main/aidl/com/centralbrain/sdk/event"
SDK_DIR="central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk"
RUNTIME_DIR="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime"
DEBUG_PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/persistence/DurableEventCursorRepositoryProbeActivity.java"
CONTRACT="central-brain/contracts/central_brain_android_event_v2.json"
HASH_MANIFEST="central-brain/android-runtime/central-brain-sdk/aidl-api/events-v2.sha256"

require_file() {
  [[ -f "$ROOT_DIR/$1" ]] || { echo "missing Event V2 file: $1" >&2; exit 1; }
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
  grep -Fq -- "$2" "$ROOT_DIR/$1" \
    || { echo "missing Event V2 marker '$2' in $1" >&2; exit 1; }
}

AIDL_TYPES=(
  EventAckRequest
  EventAckResult
  EventPageV2
  EventSubscriptionHandle
  EventSubscriptionRequest
  ICentralBrainSessionEventCallbackV2
  ICentralBrainSessionEventsV2
)
for name in "${AIDL_TYPES[@]}"; do
  require_file "$AIDL_DIR/$name.aidl"
done

INTERFACE="$AIDL_DIR/ICentralBrainSessionEventsV2.aidl"
CALLBACK="$AIDL_DIR/ICentralBrainSessionEventCallbackV2.aidl"
for marker in \
  'const int INTERFACE_VERSION = 2;' \
  'const int MAX_PAGE_SIZE = 100;' \
  'const int MAX_QUEUE_CAPACITY = 256;' \
  'EventPageV2 getEvents(String sessionId, String cursor, int limit);' \
  'EventAckResult acknowledge(in EventAckRequest request);' \
  'boolean cancelSubscription(in EventSubscriptionHandle handle);'; do
  require_text "$INTERFACE" "$marker"
done
require_text "$CALLBACK" 'oneway interface ICentralBrainSessionEventCallbackV2'
require_text "$CALLBACK" 'void onEvent(in RuntimeEvent event, String resumeCursor);'
require_text "$AIDL_DIR/EventPageV2.aidl" 'String resumeCursor = "";'
require_text "$AIDL_DIR/EventPageV2.aidl" 'long resumeSequence = 0;'

declared_hash="$(
  sed -nE 's/.*INTERFACE_HASH = "([0-9a-f]{64})";.*/\1/p' "$ROOT_DIR/$INTERFACE"
)"
computed_hash="$({
  for name in "${AIDL_TYPES[@]}"; do
    sed -E \
      's/const String INTERFACE_HASH = "[0-9a-f]{64}";/const String INTERFACE_HASH = "<generated-by-checker>";/' \
      "$ROOT_DIR/$AIDL_DIR/$name.aidl"
  done
} | sha256sum | awk '{print $1}')"
[[ "$declared_hash" == "$computed_hash" ]] \
  || { echo "Event V2 interface hash drift: declared=$declared_hash computed=$computed_hash" >&2; exit 1; }

require_file "$HASH_MANIFEST"
(cd "$ROOT_DIR" && sha256sum -c "$HASH_MANIFEST" >/dev/null)
for manifest in v1.sha256 governance-v1.sha256 session-v1.sha256 \
    plan-v1.sha256 events-v1.sha256 effect-v1.sha256; do
  (cd "$ROOT_DIR" && sha256sum -c \
    "central-brain/android-runtime/central-brain-sdk/aidl-api/$manifest" >/dev/null)
done

for file in \
  "$SDK_DIR/event/EventV2Contract.java" \
  "$SDK_DIR/ScenarioTransport.java" \
  "$SDK_DIR/AndroidScenarioTransport.java" \
  "$SDK_DIR/SessionClient.java" \
  "$RUNTIME_DIR/session/SessionEventCursorCodec.java" \
  "$RUNTIME_DIR/session/TransientSessionEndpoint.java" \
  "$RUNTIME_DIR/persistence/DurableEventCursorRepository.java" \
  "$RUNTIME_DIR/CentralBrainRuntimeService.java" \
  "$DEBUG_PROBE" \
  "$CONTRACT"; do
  require_file "$file"
done

for marker in \
  'registerSession(' \
  'findSessionOwned(' \
  'acknowledgeSessionOwned(' \
  'cancelSessionOwned('; do
  require_text "$RUNTIME_DIR/persistence/DurableEventCursorRepository.java" "$marker"
done
for marker in \
  'ICentralBrainSessionEventsV2.Stub eventBinderV2' \
  'EventV2Contract.validateSubscriptionRequest(request)' \
  'durableEventCursors.registerSession(' \
  'durableEventCursors.acknowledgeSessionOwned(' \
  'page.resumeCursor = cursorCodec.encode('; do
  require_text "$RUNTIME_DIR/session/TransientSessionEndpoint.java" "$marker"
done
require_text "$RUNTIME_DIR/CentralBrainRuntimeService.java" 'ACTION_SESSION_EVENTS_V2.equals(action)'
require_text "$RUNTIME_DIR/CentralBrainRuntimeService.java" 'event_v2_interface_published=true'
require_text "$SDK_DIR/SessionClient.java" 'transport.supportsEventV2()'
require_text "$SDK_DIR/SessionClient.java" 'recoverV2(subscription)'
require_text "$SDK_DIR/SessionClient.java" 'transport.acknowledgeV2(request)'
require_text "$SDK_DIR/SessionClient.java" 'recoverV1(subscription)'

require_text \
  "central-brain/android-runtime/central-brain-sdk/src/test/java/com/centralbrain/sdk/SessionClientTest.java" \
  'eventV2UsesTerminalCursorAndAcknowledgesLiveEvents'
require_text \
  "central-brain/android-runtime/central-brain-sdk/src/test/java/com/centralbrain/sdk/SessionClientTest.java" \
  'eventV2ProtocolMismatchFailsClosed'
require_text "$DEBUG_PROBE" 'event_v2_durable_session_cursor_verified='
require_text "$DEBUG_PROBE" 'event_v2_owner_session_isolation_verified='
require_text "$DEBUG_PROBE" 'event_v2_ack_monotonic_verified='
require_text "$DEBUG_PROBE" 'event_v2_reopen_recovery_verified='

python3 - "$ROOT_DIR/$CONTRACT" "$declared_hash" <<'PY'
import json
import sys

path, expected_hash = sys.argv[1:]
with open(path, encoding="utf-8") as handle:
    contract = json.load(handle)
assert contract["binder"]["interface_version"] == 2
assert contract["binder"]["interface_hash"] == expected_hash
assert contract["binder"]["aidl_type_count"] == 7
assert contract["cursor"]["terminal_page_cursor_required"] is True
assert contract["acknowledgement"]["room_persisted"] is True
assert contract["sdk"]["v1_hashes_unchanged"] is True
assert contract["claim_state"]["event_v2_interface_published"] is True
assert contract["claim_state"]["event_v2_android13_arm64_verified"] is False
assert contract["claim_state"]["production_ready"] is False
assert contract["claim_state"]["target_hardware_validated"] is False
PY

for doc in \
  README.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md; do
  require_text "$doc" 'P6-EV2'
done

printf '%s\n' \
  'Central Brain Android Event V2 check passed' \
  'event_v2_interface_published=true' \
  'event_v2_terminal_cursor_implemented=true' \
  'event_v2_room_ack_wired=true' \
  'event_v2_sdk_negotiation_wired=true' \
  'event_v2_durable_session_cursor_verified=true' \
  'event_v2_android13_arm64_verified=false' \
  'frozen_v1_hashes_unchanged=true' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
