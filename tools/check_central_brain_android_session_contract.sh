#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AIDL_DIR="central-brain/android-runtime/central-brain-sdk/src/main/aidl/com/centralbrain/sdk/session"
JAVA_CONTRACT="central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/session/SessionContract.java"
UNIT_TEST="central-brain/android-runtime/central-brain-sdk/src/test/java/com/centralbrain/sdk/session/SessionContractTest.java"
DEVICE_TEST="central-brain/android-runtime/central-brain-sdk/src/androidTest/java/com/centralbrain/sdk/session/SessionParcelInstrumentation.java"
HASH_MANIFEST="central-brain/android-runtime/central-brain-sdk/aidl-api/session-v1.sha256"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Session contract file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Session contract pattern '$pattern' in $path" >&2
    exit 1
  fi
}

AIDL_TYPES=(
  SessionRequest
  SessionHandle
  SessionSnapshot
  SessionQuery
  SessionPage
  ICentralBrainSessionRuntime
)

for name in "${AIDL_TYPES[@]}"; do
  require_file "$AIDL_DIR/$name.aidl"
done
for name in SessionRequest SessionHandle SessionSnapshot SessionQuery SessionPage; do
  require_text "$AIDL_DIR/$name.aidl" "int schemaVersion = 1;"
done

INTERFACE="$AIDL_DIR/ICentralBrainSessionRuntime.aidl"
require_text "$INTERFACE" "const int INTERFACE_VERSION = 1;"
require_text "$INTERFACE" "SessionHandle openSession(in SessionRequest request);"
require_text "$INTERFACE" "SessionSnapshot getSession(in SessionHandle handle);"
require_text "$INTERFACE" "SessionPage listSessions(in SessionQuery query);"
require_text "$INTERFACE" "boolean cancelSession(in SessionHandle handle, int reasonCode);"
require_text "$INTERFACE" "SESSION_STATE_WAITING_FOR_CONFIRMATION"
require_text "$INTERFACE" "SESSION_STATE_PARTIALLY_COMPLETED"
require_text "$INTERFACE" "SESSION_STATE_CANCELLED"

declared_hash="$(
  sed -nE 's/.*INTERFACE_HASH = "([0-9a-f]{64})";.*/\1/p' "$ROOT_DIR/$INTERFACE"
)"
if [[ ${#declared_hash} -ne 64 ]]; then
  echo "Session interface hash must be a 64-character lowercase SHA-256 token" >&2
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
  echo "Session interface hash drift: declared=$declared_hash computed=$computed_hash" >&2
  exit 1
fi

require_file "$JAVA_CONTRACT"
require_text "$JAVA_CONTRACT" "MAX_UTTERANCE_CHARS = 1024"
require_text "$JAVA_CONTRACT" "MAX_SUMMARY_CHARS = 512"
require_text "$JAVA_CONTRACT" "MAX_PAGE_SIZE = 50"
require_text "$JAVA_CONTRACT" "MAX_DEADLINE_FUTURE_MS"
require_text "$JAVA_CONTRACT" "request.source is unknown"
require_text "$JAVA_CONTRACT" "snapshot.state is unknown"
require_text "$JAVA_CONTRACT" "CB_SESSION_CONTRACT:"

require_file "$UNIT_TEST"
require_text "$UNIT_TEST" "rejectsOversizeAndUnknownRequestFields"
require_text "$UNIT_TEST" "rejectsExpiredOrUnboundedDeadline"
require_file "$DEVICE_TEST"
require_text "$DEVICE_TEST" "session_parcel_round_trip_verified=true"
require_text "$DEVICE_TEST" "session_oversize_rejected=true"
require_text "$DEVICE_TEST" "session_runtime_service_published=false"
require_text \
  "central-brain/android-runtime/central-brain-sdk/build.gradle.kts" \
  "com.centralbrain.sdk.session.SessionParcelInstrumentation"

if grep -R -Eiq 'json|Bundle|ParcelFileDescriptor|SharedMemory|FileDescriptor' \
    "$ROOT_DIR/$AIDL_DIR"; then
  echo "Session AIDL must remain structured, bounded, and handle-free" >&2
  exit 1
fi
if grep -Eiq 'speed|gear|belt|permission|caller|signer|signature' \
    "$ROOT_DIR/$AIDL_DIR/SessionRequest.aidl"; then
  echo "SessionRequest must not accept authority, identity, or vehicle-safety assertions" >&2
  exit 1
fi

require_file "$HASH_MANIFEST"
(cd "$ROOT_DIR" && sha256sum -c "$HASH_MANIFEST" >/dev/null)
(cd "$ROOT_DIR" && sha256sum -c \
  central-brain/android-runtime/central-brain-sdk/aidl-api/v1.sha256 >/dev/null)
(cd "$ROOT_DIR" && sha256sum -c \
  central-brain/android-runtime/central-brain-sdk/aidl-api/governance-v1.sha256 >/dev/null)

for doc in \
  README.md \
  docs/CENTRAL_BRAIN_ANDROID_AIDL_CONTRACT.md \
  docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_ROADMAP.md \
  docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md \
  docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md; do
  require_text "$doc" "P1-W01"
done
require_text "README.md" "session_contract_v1_defined=true"
require_text "README.md" "session_parcel_physical_android13_arm64_verified=true"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" \
  "session_contract_v1_defined=true"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" \
  "session_runtime_service_published=true"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" \
  "session_parcel_physical_android13_arm64_verified=true"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" \
  "P1-W01 Session Contract Driver/HAL Boundary"

echo "Central Brain Android Session contract V1 check passed"
