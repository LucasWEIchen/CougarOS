#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MDL-001, S2-SAF-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROVIDER="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/LocalModelProvider.java"
TEST="central-brain/android-runtime/runtime-service/src/testDebug/java/com/centralbrain/runtime/model/LocalModelProviderTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/LocalModelProviderProbeActivity.java"
PROFILE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ModelProviderProfiles.java"
REGISTRY="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ModelProviderRegistry.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

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
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P7-W04 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$PROVIDER" "$TEST" "$PROBE" "$PROFILE" "$REGISTRY" \
    "$DEBUG_MANIFEST" "$MAIN_MANIFEST" "$RUNTIME" "$GOVERNANCE" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P7-W04 file missing: $file" >&2; exit 1; }
done

for marker in \
  'implements ModelProvider' \
  'createForDevelopment(' \
  'interface LocalInferenceEngine' \
  'interface CancellationSignal' \
  'class StreamLimits' \
  'class EngineOutput' \
  'ABSOLUTE_MAX_STREAM_CHUNKS = 32' \
  'ABSOLUTE_MAX_TOTAL_BYTES = 262_144' \
  'MAX_STREAM_CHUNK_BYTES' \
  'isCancellationRequested()' \
  'isDeadlineExceeded()' \
  'LOCAL_OUTPUT_LIMIT_EXCEEDED' \
  'Assurance.DEBUG_ONLY' \
  'FallbackClass.NEVER' \
  'isProductionEligible()' \
  'isHardwareBacked()'; do
  require_text "$PROVIDER" "$marker"
done

for test_name in \
  developmentProfileStreamsBoundedInProcessOutput \
  cancellationIsVisibleToEngineAndAcknowledged \
  deadlineIsCheckedBeforeAdmissionAndAfterEngine \
  chunkCountAndByteLimitsFailClosed \
  nonStreamingOutputIsMergedAndCloseCancelsQueuedWork \
  registryAndPolicyKeepLocalProviderOutOfProduction; do
  require_text "$TEST" "$test_name"
done

for marker in \
  local_model_provider_probe_complete \
  local_model_provider_verified \
  local_model_provider_lifecycle_verified \
  local_model_provider_stream_limit_verified \
  local_model_provider_cancel_verified \
  local_model_provider_deadline_verified \
  local_model_provider_overflow_rejected \
  local_model_provider_profile_boundary_verified \
  local_model_provider_registry_boundary_verified; do
  require_text "$PROBE" "$marker="
done

for marker in \
  local_model_provider_verified=true \
  local_model_provider_lifecycle_verified=true \
  local_model_provider_stream_limit_verified=true \
  local_model_provider_cancel_verified=true \
  local_model_provider_deadline_verified=true \
  local_model_provider_overflow_rejected=true \
  local_model_provider_profile_boundary_verified=true \
  local_model_provider_registry_boundary_verified=true \
  local_model_provider_debug_only=true \
  local_model_provider_release_source_absent=true \
  local_model_provider_runtime_wired=false \
  local_model_provider_vendor_npu_fallback_enabled=false \
  production_inference_enabled=false \
  raw_model_content_logged=false \
  network_accessed=false \
  npu_accessed=false \
  hardware_accessed=false \
  production_ready=false \
  target_hardware_validated=false; do
  require_text "$INSTALLER" "$marker"
done

require_text "$PROFILE" 'ANDROID_LOCAL_DEVELOPMENT_ID'
require_text "$PROFILE" 'BackendKind.ANDROID_LOCAL_DEVELOPMENT'
require_text "$PROFILE" 'Assurance.DEBUG_ONLY'
require_text "$REGISTRY" 'ANDROID_LOCAL_DEVELOPMENT_ID'
require_text "$DEBUG_MANIFEST" '.model.LocalModelProviderProbeActivity'

if [[ -e "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/LocalModelProvider.java" ]]; then
  echo "P7-W04 local provider leaked into the release source set" >&2
  exit 1
fi
if grep -Fq 'LocalModelProviderProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P7-W04 local provider probe leaked into the release manifest" >&2
  exit 1
fi
if grep -Eiq 'LocalModelProvider|androidLocalDevelopment' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE"; then
  echo "P7-W04 local provider was wired into production Services" >&2
  exit 1
fi
if grep -R -Eiq \
    'android[.]car|CarPropertyManager|VehicleHal|VehiclePropertyIds|java[.]net|okhttp|http://|https://|ioctl|sysfs|/dev/|android[.]os[.]Binder|ClassLoader|DexClassLoader|Runtime[.]getRuntime|ProcessBuilder' \
    "$ROOT_DIR/$PROVIDER" "$ROOT_DIR/$PROBE"; then
  echo "P7-W04 local provider references network, Binder, vehicle, or hardware APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P7-W04 LocalModelProvider"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" '`P7-W04` LocalModelProvider'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W04 LocalModelProvider trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android P7-W04 LocalModelProvider"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P7-W04 LocalModelProvider architecture"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "P7-W04 LocalModelProvider detailed design"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android P7-W04 LocalModelProvider"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W04 LocalModelProvider Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W04 local provider is development-only"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W04 LocalModelProvider progress"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W04 LocalModelProvider progress"
require_text "README.md" "P7 LocalModelProvider"

printf '%s\n' \
  "Central Brain Android LocalModelProvider check passed" \
  "local_model_provider_verified=true" \
  "local_model_provider_deadline_verified=true" \
  "local_model_provider_cancel_verified=true" \
  "local_model_provider_stream_limit_verified=true" \
  "local_model_provider_debug_only=true" \
  "local_model_provider_release_source_absent=true" \
  "local_model_provider_runtime_wired=false" \
  "local_model_provider_vendor_npu_fallback_enabled=false" \
  "local_model_provider_android13_arm64_verified=true" \
  "production_inference_enabled=false" \
  "network_accessed=false" \
  "npu_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
