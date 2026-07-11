#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004, FW-U-004/005, NV-F-001, NV-G-006/007, DEL-001/004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADAPTER="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/EffectAdapter.java"
CONTRACT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/EffectAdapterContract.java"
RECONCILER="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/EffectStatusReconciler.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/effects/EffectAdapterContractTest.java"
FIXTURE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/persistence/DeterministicEffectAdapter.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/persistence/EffectAdapterContractProbeActivity.java"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android effect adapter contract file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android effect adapter pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in \
  "$ADAPTER" "$CONTRACT" "$RECONCILER" "$TEST" "$FIXTURE" "$PROBE" \
  "$RUNTIME" "$GOVERNANCE" "$INSTALLER"; do
  require_file "$path"
done

for pattern in \
  "TOKEN_DEDUPLICATED" \
  "LINEARIZABLE" \
  "duplicateApplyReturnsOriginal" \
  "appliedStatusReturnsOriginalEvidence" \
  "MAX_PAYLOAD_BYTES" \
  "MAX_ENVELOPE_BYTES" \
  "canonicalPayload does not match payloadDigest" \
  "canonicalEnvelope does not match envelopeDigest" \
  "Arrays.copyOf" \
  "queryStatus"; do
  require_text "$ADAPTER" "$pattern"
done

require_text "$CONTRACT" "requireSafe"
require_text "$CONTRACT" "adapter status query is not linearizable"
require_text "$CONTRACT" "applied status does not return the original result evidence"
require_text "$CONTRACT" "requireInvocationMatches"
require_text "$CONTRACT" "requireApplyResultMatches"
require_text "$CONTRACT" "requireStatusResultMatches"
require_text "$RECONCILER" "adapter.queryStatus"
require_text "$RECONCILER" "AdapterUnavailableException"
require_text "$RECONCILER" "Decision.DEFERRED"
require_text "$RECONCILER" "case NOT_APPLIED"
require_text "$RECONCILER" "case UNKNOWN"
if grep -Fq "adapter.apply" "$ROOT_DIR/$RECONCILER"; then
  echo "status reconciler must never invoke an adapter operation" >&2
  exit 1
fi

require_text "$FIXTURE" "debug.deterministic.effect"
require_text "$FIXTURE" "idempotency token is bound to another adapter invocation"
require_text "$PROBE" "adapter_crash_after_apply_reconciled="
require_text "$PROBE" "adapter_status_matches_apply_result="
require_text "$PROBE" "adapter_crash_before_apply_retried="
require_text "$PROBE" "adapter_status_unavailable_deferred="
require_text "$PROBE" "adapter_unknown_status_dead_lettered="
require_text "$PROBE" "adapter_final_not_applied_dead_lettered="
require_text "$PROBE" "transient_effect_material_durable=false"
require_text "$PROBE" "effect_adapter_production_wired=false"
require_text "$PROBE" "real_adapter_dispatch_enabled=false"
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" ".persistence.EffectAdapterContractProbeActivity"
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" 'android:permission="android.permission.DUMP"'

for flag in \
  "effect_adapter_contract_verified=true" \
  "unsafe_adapter_rejected=true" \
  "adapter_destination_mismatch_rejected=true" \
  "adapter_duplicate_apply_idempotent=true" \
  "adapter_status_matches_apply_result=true" \
  "adapter_crash_after_apply_reconciled=true" \
  "adapter_crash_before_apply_retried=true" \
  "adapter_status_unavailable_deferred=true" \
  "adapter_unknown_status_dead_lettered=true" \
  "adapter_final_not_applied_dead_lettered=true" \
  "adapter_terminal_counts_verified=true" \
  "adapter_fault_matrix_verified=true" \
  "transient_effect_material_durable=false" \
  "effect_adapter_production_wired=false" \
  "real_adapter_dispatch_enabled=false"; do
  require_text "$INSTALLER" "$flag"
done

if grep -Fq "EffectAdapterContractProbeActivity" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"; then
  echo "effect adapter contract probe must remain debug-only" >&2
  exit 1
fi
if grep -Eq 'EffectAdapter|EffectStatusReconciler' "$ROOT_DIR/$RUNTIME" \
    || grep -Eq 'EffectAdapter|EffectStatusReconciler' "$ROOT_DIR/$GOVERNANCE"; then
  echo "R4C3A adapter contract must not be wired to production Services" >&2
  exit 1
fi
if grep -R -Eq 'implements[[:space:]]+EffectAdapter' \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/java"; then
  echo "R4C3A must not ship a production EffectAdapter implementation" >&2
  exit 1
fi
if grep -R -Eiq \
    'ioctl|sysfs|/dev/|VehicleHal|CarPropertyManager|vendor sdk|SharedMemory|SocketCAN' \
    "$ROOT_DIR/$ADAPTER" "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$RECONCILER" \
    "$ROOT_DIR/$FIXTURE" "$ROOT_DIR/$PROBE"; then
  echo "R4C3A adapter contract unexpectedly references hardware" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "R4C3A Effect Adapter Contract And Fault Matrix"
require_text "docs/CENTRAL_BRAIN_ANDROID_RUNTIME_EVOLUTION_PLAN.md" "R4C3A effect adapter contract and fault matrix"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R4C3A effect adapter contract trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android R4C3A Effect Adapter Contract"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R4C3A Effect Adapter Contract"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R4C3A Effect Adapter Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "R4C3A 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "R4C3A 进展"

echo "Central Brain Android effect adapter contract check passed"
