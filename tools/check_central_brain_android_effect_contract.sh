#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AIDL_DIR="central-brain/android-runtime/central-brain-sdk/src/main/aidl/com/centralbrain/sdk/effect"
JAVA_CONTRACT="central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/effect/EffectContract.java"
UNIT_TEST="central-brain/android-runtime/central-brain-sdk/src/test/java/com/centralbrain/sdk/effect/EffectContractTest.java"
DEVICE_TEST="central-brain/android-runtime/central-brain-sdk/src/androidTest/java/com/centralbrain/sdk/session/SessionParcelInstrumentation.java"
HASH_MANIFEST="central-brain/android-runtime/central-brain-sdk/aidl-api/effect-v1.sha256"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Effect contract file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Effect contract pattern '$pattern' in $path" >&2
    exit 1
  fi
}

AIDL_TYPES=(EffectIntent EffectObservation ApprovalPrompt UndoHandle)
for name in "${AIDL_TYPES[@]}"; do
  require_file "$AIDL_DIR/$name.aidl"
  require_text "$AIDL_DIR/$name.aidl" "int schemaVersion = 1;"
done

for marker in \
  'String effectId = "";' \
  'String sessionId = "";' \
  'String planId = "";' \
  'int valueKind = 0;' \
  'String targetValueDigest = "";' \
  'String idempotencyKey = "";' \
  'String planDigest = "";' \
  'String contextDigest = "";' \
  'long contextVersion = 0;' \
  'int verificationPolicy = 0;' \
  'boolean reversible = false;' \
  'long deadlineEpochMs = 0;'; do
  require_text "$AIDL_DIR/EffectIntent.aidl" "$marker"
done
for marker in \
  'int state = 0;' \
  'int source = 0;' \
  'int attempt = 0;' \
  'String reportedValueDigest = "";' \
  'String evidenceDigest = "";' \
  'boolean terminal = false;' \
  'boolean retryable = false;' \
  'boolean simulated = false;'; do
  require_text "$AIDL_DIR/EffectObservation.aidl" "$marker"
done
for marker in \
  'String approvalId = "";' \
  'String planDigest = "";' \
  'String actionDigest = "";' \
  'String contextDigest = "";' \
  'long contextVersion = 0;' \
  'int policyVersion = 0;' \
  'String approvalDigest = "";' \
  'long expiresAtEpochMs = 0;'; do
  require_text "$AIDL_DIR/ApprovalPrompt.aidl" "$marker"
done
for marker in \
  'String undoId = "";' \
  'String sourceObservationId = "";' \
  'String verifiedObservationDigest = "";' \
  'String compensationDigest = "";' \
  'long issuedContextVersion = 0;' \
  'int state = 0;' \
  'long expiresAtEpochMs = 0;'; do
  require_text "$AIDL_DIR/UndoHandle.aidl" "$marker"
done

require_file "$JAVA_CONTRACT"
declared_hash="$(
  sed -nE 's/.*"([0-9a-f]{64})";.*/\1/p' "$ROOT_DIR/$JAVA_CONTRACT" | head -n 1
)"
computed_hash="$({
  for name in "${AIDL_TYPES[@]}"; do
    cat "$ROOT_DIR/$AIDL_DIR/$name.aidl"
  done
} | sha256sum | awk '{print $1}')"
if [[ "$declared_hash" != "$computed_hash" ]]; then
  echo "Effect contract hash drift: declared=$declared_hash computed=$computed_hash" >&2
  exit 1
fi

for marker in \
  'MAX_EFFECT_DEADLINE_MS = 15 * 60 * 1000L' \
  'MAX_APPROVAL_TTL_MS = 5 * 60 * 1000L' \
  'STATE_DISPATCHED = 5' \
  'STATE_DELIVERED = 6' \
  'STATE_APPLIED = 7' \
  'STATE_VERIFIED = 8' \
  'intent carries data in an inactive typed-value field' \
  'simulation source and marker must agree' \
  'terminal effect state cannot transition' \
  'bounded retry must increment the attempt exactly once' \
  'stale approval binding rejected' \
  'undo handle is not currently available' \
  'CB_EFFECT_CONTRACT:'; do
  require_text "$JAVA_CONTRACT" "$marker"
done

require_file "$UNIT_TEST"
for marker in \
  'acceptsTypedEffectApprovalAndUndoContracts' \
  'rejectsInactiveValueAndUnsafeVerification' \
  'distinguishesDispatchDeliveryApplyAndVerify' \
  'rejectsIllegalTerminalAndRetryTransitions' \
  'rejectsStaleOrExpiredApproval' \
  'rejectsUnsafeUndoAndSimulationMarker'; do
  require_text "$UNIT_TEST" "$marker"
done

require_file "$DEVICE_TEST"
for marker in \
  'effect_parcel_round_trip_verified=true' \
  'effect_state_transitions_verified=true' \
  'effect_illegal_terminal_transition_rejected=true' \
  'stale_approval_rejected=true' \
  'expired_undo_rejected=true' \
  'effect_runtime_service_published=false' \
  'approval_response_service_published=false' \
  'undo_service_published=false'; do
  require_text "$DEVICE_TEST" "$marker"
done

if grep -R -Eiq 'Bundle|ParcelFileDescriptor|SharedMemory|FileDescriptor|rawPayload|rawVehicle|rawModel' \
    "$ROOT_DIR/$AIDL_DIR"; then
  echo "Effect AIDL must remain structured, bounded, digest-oriented and handle-free" >&2
  exit 1
fi
if grep -R -Eiq 'caller|signer|signature|permission|speed|gear|belt|occupancy|principal' \
    "$ROOT_DIR/$AIDL_DIR"; then
  echo "Effect DTOs must not accept caller identity or vehicle-safety authority" >&2
  exit 1
fi
if grep -R -Eq '(^|[[:space:]])(oneway[[:space:]]+)?interface[[:space:]]' \
    "$ROOT_DIR/$AIDL_DIR"; then
  echo "P1-W04 must not publish an Effect, approval-response or undo Binder interface" >&2
  exit 1
fi

require_file "$HASH_MANIFEST"
(cd "$ROOT_DIR" && sha256sum -c "$HASH_MANIFEST" >/dev/null)
for manifest in v1.sha256 governance-v1.sha256 session-v1.sha256 plan-v1.sha256 events-v1.sha256; do
  (cd "$ROOT_DIR" && sha256sum -c \
    "central-brain/android-runtime/central-brain-sdk/aidl-api/$manifest" >/dev/null)
done

for doc in \
  README.md \
  central-brain/README.md \
  central-brain/android-runtime/README.md \
  docs/CENTRAL_BRAIN_ANDROID_AIDL_CONTRACT.md \
  docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md \
  docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_ROADMAP.md \
  docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md \
  docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md; do
  require_text "$doc" "P1-W04"
done
require_text "README.md" "effect_contract_v1_defined=true"
require_text "README.md" "effect_parcel_physical_android13_arm64_verified=true"
require_text "README.md" "effect_runtime_service_published=false"
require_text "README.md" "approval_response_service_published=false"
require_text "README.md" "undo_service_published=false"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" \
  "effect_contract_v1_defined=true"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" \
  "effect_parcel_physical_android13_arm64_verified=true"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" \
  "P1-W04 Effect/Approval Contract Driver/HAL Boundary"

echo "Central Brain Android Effect/Approval contract V1 check passed"
