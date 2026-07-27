#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004, NV-F-011, NV-G-004/006, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ModelProvider.java"
PROFILES="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ModelProviderProfiles.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/model/ModelProviderContractTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/ModelProviderContractProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android model provider contract file: $path" >&2
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
    echo "missing Android model provider pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in \
  "$CONTRACT" \
  "$PROFILES" \
  "$TEST" \
  "$PROBE" \
  "$DEBUG_MANIFEST" \
  "$RUNTIME" \
  "$GOVERNANCE" \
  "$INSTALLER"; do
  require_file "$path"
done

for operation in \
  "Descriptor descriptor()" \
  "Snapshot snapshot()" \
  "Snapshot warmup(ModelSpec modelSpec)" \
  "InferenceHandle infer(InferenceRequest request, StreamObserver observer)" \
  "CancelState cancel(String requestId, String reason)" \
  "Metrics metrics()" \
  "FaultSnapshot lastFault()" \
  "void close()"; do
  require_text "$CONTRACT" "$operation"
done
require_text "$CONTRACT" "DETERMINISTIC_STUB"
require_text "$CONTRACT" "ANDROID_LOCAL_DEVELOPMENT"
require_text "$CONTRACT" "OLLAMA_DEBUG"
require_text "$CONTRACT" "VENDOR_NPU"
require_text "$CONTRACT" "FAULT_ISOLATED"
require_text "$CONTRACT" "POLICY_CONTROLLED"
require_text "$CONTRACT" "development providers cannot claim hardware or production"
require_text "$CONTRACT" "empty providers must remain unavailable and non-routable"
require_text "$CONTRACT" "MAX_STREAM_CHUNK_BYTES"
require_text "$CONTRACT" "Arrays.copyOf"

require_text "$PROFILES" 'DETERMINISTIC_STUB_ID = "deterministic.stub"'
require_text "$PROFILES" 'ANDROID_LOCAL_DEVELOPMENT_ID ='
require_text "$PROFILES" 'VENDOR_NPU_EMPTY_ID = "vendor.npu.empty"'
require_text "$PROFILES" '"STUB_IMPLEMENTATION_NOT_WIRED"'
require_text "$PROFILES" '"VENDOR_RUNTIME_UNAVAILABLE"'
require_text "$PROFILES" "return new Profile(descriptor, snapshot, false, false)"
require_text "$TEST" "unsafeStubAndEmptyDescriptorsAreRejected"
require_text "$TEST" "androidLocalProfileIsDevelopmentOnlyAndNotConfiguredByDefault"
require_text "$TEST" "streamChunksAreBoundedAndDefensivelyCopied"
require_text "$DEBUG_MANIFEST" ".model.ModelProviderContractProbeActivity"

for marker in \
  "model_provider_contract_verified=true" \
  "deterministic_stub_profile_verified=true" \
  "vendor_npu_empty_profile_verified=true" \
  "unsafe_provider_descriptor_rejected=true"; do
  require_text "$INSTALLER" "$marker"
  require_text "$PROBE" "${marker%=true}="
done
for marker in \
  "deterministic_stub_implementation_configured=false" \
  "deterministic_stub_routing_enabled=false" \
  "vendor_npu_provider_available=false" \
  "model_provider_runtime_wired=false" \
  "model_router_dispatch_enabled=false" \
  "ollama_android_provider_configured=false" \
  "hardware_accessed=false"; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

if grep -Eq \
    'import com\.centralbrain\.runtime\.model\.ModelProvider(Profiles)?;|DeterministicStubModelProvider|TestOnlyModelRouter|InferenceResourceScheduler|ModelProviderProfiles\.(deterministicStub|vendorNpuEmpty)|provider\.(warmup|infer|cancel)' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE"; then
  echo "R5A1 executable provider path must not be wired into production Services" >&2
  exit 1
fi
if grep -R -Eiq \
    'java\.net|okhttp|http://|https://|ioctl|sysfs|/dev/|CarPropertyManager|VehicleHal' \
    "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$PROFILES" "$ROOT_DIR/$PROBE"; then
  echo "R5A1 provider contract unexpectedly references network or hardware access" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "R5A1 Model Provider Contract"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "R5A1 model provider contract"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R5A1 model provider contract trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android R5A1 Model Provider Contract"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android R5A1 Model Provider Contract"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R5A1 Model Provider Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R5A1 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R5A1 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R5A1 model provider contract"

echo "Central Brain Android model provider contract check passed"
