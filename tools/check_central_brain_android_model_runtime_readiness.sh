#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004/005, NV-F-011/012, NV-G-006/007, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SNAPSHOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ModelRuntimeReadinessSnapshot.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/model/ModelRuntimeReadinessSnapshotTest.java"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
DIAGNOSTIC="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainDiagnosticService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/DiagnosticProbeActivity.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing model runtime readiness file: $path" >&2
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
    echo "missing model runtime readiness pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in \
  "$SNAPSHOT" \
  "$TEST" \
  "$RUNTIME" \
  "$DIAGNOSTIC" \
  "$GOVERNANCE" \
  "$PROBE" \
  "$INSTALLER"; do
  require_file "$path"
done

require_text "$SNAPSHOT" "ModelRuntimeReadinessSnapshot current()"
require_text "$SNAPSHOT" "ModelProviderProfiles.deterministicStub()"
require_text "$SNAPSHOT" "ModelProviderProfiles.vendorNpuEmpty()"
require_text "$SNAPSHOT" "current model runtime configuration must remain blocked"
require_text "$SNAPSHOT" "PRODUCTION_PROVIDER_MISSING"
require_text "$SNAPSHOT" "PRODUCTION_ROUTE_MISSING"
require_text "$SNAPSHOT" "SCHEDULER_NOT_WIRED"
require_text "$SNAPSHOT" "MODEL_ROUTER_NOT_WIRED"
require_text "$SNAPSHOT" "VENDOR_NPU_INTERFACE_EMPTY"
require_text "$SNAPSHOT" "isProductionInferenceAllowed()"
require_text "$SNAPSHOT" "isSchedulerProductionWired()"
require_text "$SNAPSHOT" "isProductionModelRouterWired()"
require_text "$SNAPSHOT" "isProductionModelRouterDispatchEnabled()"
require_text "$SNAPSHOT" "isVendorNpuProviderAvailable()"
require_text "$SNAPSHOT" "getDeterministicStubDetailCode()"
require_text "$SNAPSHOT" "getVendorNpuDetailCode()"
require_text "$SNAPSHOT" "diagnosticDetail()"
require_text "$TEST" "currentSnapshotIsImmutableAndFailClosed"
require_text "$TEST" "currentProfilesAndBlockersRemainExplicit"
require_text "$TEST" "diagnosticDetailSeparatesAvailabilityFromActivation"

require_text "$RUNTIME" "ModelRuntimeReadinessSnapshot.current()"
require_text "$RUNTIME" "model_runtime_readiness_snapshot_wired=true"
require_text "$RUNTIME" "production_inference_allowed="
require_text "$RUNTIME" "model_runtime_activation_blockers="
require_text "$RUNTIME" "protected void dump(FileDescriptor fd, PrintWriter writer, String[] args)"
require_text "$DIAGNOSTIC" "ModelRuntimeReadinessSnapshot.current()"
require_text "$DIAGNOSTIC" '"model-runtime-readiness"'
require_text "$DIAGNOSTIC" "modelRuntimeReadiness.diagnosticDetail()"
require_text "$PROBE" "model_runtime_readiness_diagnostic_verified="
require_text "$PROBE" "hasBlockedModelRuntime"

for marker in \
  "model_runtime_readiness_diagnostic_verified=true" \
  "model_runtime_readiness_snapshot_wired=true" \
  "model_runtime_readiness_log_verified=true" \
  "model_runtime_readiness_dumpsys_verified=true" \
  "production_inference_allowed=false" \
  "model_provider_contract_available=true" \
  "inference_scheduler_contract_available=true" \
  "test_model_router_implementation_available=true" \
  "model_router_test_only=true" \
  "deterministic_stub_profile_id=deterministic.stub" \
  "deterministic_stub_lifecycle=COLD" \
  "deterministic_stub_health=HEALTHY" \
  "deterministic_stub_detail_code=STUB_IMPLEMENTATION_NOT_WIRED" \
  "deterministic_stub_implementation_configured=false" \
  "deterministic_stub_routing_enabled=false" \
  "vendor_npu_profile_id=vendor.npu.empty" \
  "vendor_npu_lifecycle=UNAVAILABLE" \
  "vendor_npu_health=UNAVAILABLE" \
  "vendor_npu_detail_code=VENDOR_RUNTIME_UNAVAILABLE" \
  "vendor_npu_provider_available=false" \
  "scheduler_production_wired=false" \
  "production_model_router_wired=false" \
  "production_model_router_dispatch_enabled=false" \
  "ollama_android_provider_configured=false" \
  "hardware_accessed=false"; do
  require_text "$INSTALLER" "$marker"
done
require_text "$INSTALLER" "model_runtime_activation_blockers=PRODUCTION_PROVIDER_MISSING"

if grep -Eq \
    'DeterministicStubModelProvider|TestOnlyModelRouter|InferenceResourceScheduler' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$DIAGNOSTIC" "$ROOT_DIR/$GOVERNANCE"; then
  echo "R5C1 production Services must not reference executable model components" >&2
  exit 1
fi
if grep -Eq \
    'new (DeterministicStubModelProvider|TestOnlyModelRouter|InferenceResourceScheduler)|provider\.(warmup|infer|cancel)|\.claimNext\(' \
    "$ROOT_DIR/$SNAPSHOT" "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$DIAGNOSTIC"; then
  echo "R5C1 visibility must not execute or construct model runtime components" >&2
  exit 1
fi
if grep -R -Eiq \
    'java\.net|okhttp|http://|https://|ioctl|sysfs|/dev/|CarPropertyManager|VehicleHal|Runtime\.getRuntime' \
    "$ROOT_DIR/$SNAPSHOT"; then
  echo "R5C1 model readiness snapshot unexpectedly references network or hardware" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "R5C1 Production-Safe Model Runtime Readiness"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "R5C1 production-safe model runtime readiness"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R5C1 production-safe model runtime readiness trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android R5C1 Model Runtime Readiness Snapshot"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android R5C1 Production-Safe Model Runtime Readiness"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R5C1 Model Readiness Visibility Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android R5C1 model-runtime readiness"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R5C1 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R5C1 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R5C1 production-safe readiness"

echo "Central Brain Android model runtime readiness check passed"
