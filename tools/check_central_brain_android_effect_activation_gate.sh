#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004, FW-U-004/005, NV-F-001, NV-G-006/007, DEL-001/004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/EffectMaterialSource.java"
EMPTY="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/EmptyEffectMaterialSource.java"
GATE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/EffectDeliveryActivationGate.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/effects/EffectDeliveryActivationGateTest.java"
FIXTURE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/persistence/DeterministicEffectMaterialSource.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/persistence/EffectDeliveryActivationProbeActivity.java"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android effect activation file: $path" >&2
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
    echo "missing Android effect activation pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in \
  "$SOURCE" "$EMPTY" "$GATE" "$TEST" "$FIXTURE" "$PROBE" \
  "$RUNTIME" "$GOVERNANCE" "$INSTALLER"; do
  require_file "$path"
done

for pattern in \
  "durableAcrossProcessRestart" \
  "encryptedAtRest" \
  "integrityBoundToEffect" \
  "deleteSupported" \
  "retentionMs" \
  "Assurance" \
  "PRODUCTION" \
  "Arrays.copyOf" \
  "MaterialUnavailableException"; do
  require_text "$SOURCE" "$pattern"
done

require_text "$EMPTY" "empty.effect.material"
require_text "$EMPTY" "Availability.EMPTY"
require_text "$EMPTY" "Assurance.UNTRUSTED"
require_text "$EMPTY" "canonical effect material is unavailable"

for blocker in \
  "ADAPTER_MISSING" \
  "ADAPTER_UNSAFE" \
  "MATERIAL_SOURCE_MISSING" \
  "MATERIAL_DESCRIPTOR_INVALID" \
  "MATERIAL_SOURCE_EMPTY" \
  "MATERIAL_SOURCE_NOT_PRODUCTION" \
  "MATERIAL_NOT_DURABLE" \
  "MATERIAL_NOT_ENCRYPTED" \
  "MATERIAL_INTEGRITY_UNBOUND" \
  "MATERIAL_DELETE_UNSUPPORTED" \
  "MATERIAL_RETENTION_INVALID"; do
  require_text "$GATE" "$blocker"
done
require_text "$GATE" "MAX_RETENTION_MS"
require_text "$GATE" "resolveInvocation"
require_text "$GATE" "materialSource.resolve"
require_text "$GATE" "EffectAdapterContract.requireInvocationMatches"
if grep -Eq 'adapter\.(apply|queryStatus)' "$ROOT_DIR/$GATE"; then
  echo "activation gate must not call adapter apply or status" >&2
  exit 1
fi

require_text "$FIXTURE" "debug.deterministic.material"
require_text "$PROBE" "current_empty_material_gate_verified="
require_text "$PROBE" "synthetic_positive_gate_verified="
require_text "$PROBE" "material_reopen_resolution_verified="
require_text "$PROBE" "material_digest_mismatch_rejected="
require_text "$PROBE" "activation_gate_no_side_effect_verified="
require_text "$PROBE" "production_effect_delivery_activation_allowed=false"
require_text "$PROBE" "production_effect_material_source=empty"
require_text "$PROBE" "raw_effect_material_persisted=false"
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" ".persistence.EffectDeliveryActivationProbeActivity"

for flag in \
  "current_empty_material_gate_verified=true" \
  "test_only_material_rejected=true" \
  "synthetic_positive_gate_verified=true" \
  "material_reopen_resolution_verified=true" \
  "material_defensive_copy_verified=true" \
  "material_digest_mismatch_rejected=true" \
  "material_missing_rejected=true" \
  "empty_material_resolution_blocked=true" \
  "activation_gate_no_side_effect_verified=true" \
  "material_activation_contract_verified=true" \
  "production_effect_delivery_activation_allowed=false" \
  "production_effect_material_source=empty" \
  "production_effect_material_durable=false" \
  "synthetic_material_source_process_only=true" \
  "raw_effect_material_persisted=false"; do
  require_text "$INSTALLER" "$flag"
done

if grep -Fq "EffectDeliveryActivationProbeActivity" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"; then
  echo "effect delivery activation probe must remain debug-only" >&2
  exit 1
fi
if grep -Eq 'EffectMaterialSource|EffectDeliveryActivationGate' "$ROOT_DIR/$RUNTIME" \
    || grep -Eq 'EffectMaterialSource|EffectDeliveryActivationGate' \
      "$ROOT_DIR/$GOVERNANCE"; then
  echo "R4C3B material source/gate must not be wired to production Services" >&2
  exit 1
fi
PRODUCTION_SOURCES="$(grep -R -l -E 'implements[[:space:]]+EffectMaterialSource' \
  "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/java" || true)"
if [[ "$PRODUCTION_SOURCES" != "$ROOT_DIR/$EMPTY" ]]; then
  echo "R4C3B production material implementation must remain the explicit empty source" >&2
  printf '%s\n' "$PRODUCTION_SOURCES" >&2
  exit 1
fi
if grep -R -Eiq \
    'ioctl|sysfs|/dev/|VehicleHal|CarPropertyManager|vendor sdk|SharedMemory|SocketCAN' \
    "$ROOT_DIR/$SOURCE" "$ROOT_DIR/$EMPTY" "$ROOT_DIR/$GATE" \
    "$ROOT_DIR/$FIXTURE" "$ROOT_DIR/$PROBE"; then
  echo "R4C3B material source/gate unexpectedly references hardware" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "R4C3B Effect Material Source And Activation Gate"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "R4C3B effect material source and activation gate"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4C3B effect material activation trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android R4C3B Effect Material Activation Gate"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android R4C3B Effect Material Activation Gate"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4C3B Effect Material Activation Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4C3B 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R4C3B 进展"

echo "Central Brain Android effect material activation gate check passed"
