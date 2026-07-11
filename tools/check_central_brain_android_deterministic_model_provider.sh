#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004, NV-F-011, NV-G-004/006, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROVIDER="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/DeterministicStubModelProvider.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/model/DeterministicStubModelProviderTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/DeterministicStubProviderProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing deterministic model provider file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing deterministic model provider pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in \
  "$PROVIDER" \
  "$TEST" \
  "$PROBE" \
  "$DEBUG_MANIFEST" \
  "$RUNTIME" \
  "$GOVERNANCE" \
  "$INSTALLER"; do
  require_file "$path"
done

require_text "$PROVIDER" "implements ModelProvider"
require_text "$PROVIDER" "ModelProviderProfiles"
require_text "$PROVIDER" ".deterministicStub()"
require_text "$PROVIDER" "Assurance.TEST_ONLY"
require_text "$PROVIDER" "Snapshot warmup(ModelSpec modelSpec)"
require_text "$PROVIDER" "InferenceHandle infer("
require_text "$PROVIDER" "CancelState cancel(String requestId, String reason)"
require_text "$PROVIDER" "Metrics metrics()"
require_text "$PROVIDER" "FaultSnapshot lastFault()"
require_text "$PROVIDER" "RETRYABLE_BEFORE_FIRST_CHUNK"
require_text "$PROVIDER" "TERMINAL_AFTER_FIRST_CHUNK"
require_text "$PROVIDER" "FAULT_ISOLATE_BEFORE_FIRST_CHUNK"
require_text "$PROVIDER" "PENDING_PROVIDER_ACK"
require_text "$PROVIDER" "executor.execute"
require_text "$PROVIDER" "MAX_TERMINAL_HISTORY"
require_text "$TEST" "warmupAndStreamingInferenceAreDeterministic"
require_text "$TEST" "cancellationIsAcknowledgedAsynchronouslyAndReleasesSlot"
require_text "$TEST" "slotAndDeadlineAreFailClosed"
require_text "$TEST" "retryableAndIsolatedFaultsAreStructured"
require_text "$TEST" "closeTerminatesActiveWorkAndBlocksReuse"
require_text "$DEBUG_MANIFEST" ".model.DeterministicStubProviderProbeActivity"

for marker in \
  "deterministic_stub_provider_contract_verified=true" \
  "deterministic_stub_lifecycle_verified=true" \
  "deterministic_stub_stream_verified=true" \
  "deterministic_stub_output_deterministic=true" \
  "deterministic_stub_cancel_ack_verified=true" \
  "deterministic_stub_metrics_verified=true" \
  "deterministic_stub_retryable_fault_verified=true" \
  "deterministic_stub_fault_isolation_verified=true" \
  "deterministic_stub_profile_boundary_verified=true"; do
  require_text "$PROBE" "${marker%=true}="
  require_text "$INSTALLER" "$marker"
done
for marker in \
  "deterministic_stub_test_only=true" \
  "deterministic_stub_implementation_available=true" \
  "deterministic_stub_implementation_configured=false" \
  "deterministic_stub_routing_enabled=false" \
  "model_provider_runtime_wired=false" \
  "model_router_dispatch_enabled=false" \
  "production_inference_enabled=false" \
  "ollama_android_provider_configured=false" \
  "vendor_npu_provider_available=false" \
  "hardware_accessed=false"; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

if grep -Eq 'DeterministicStubModelProvider|new ModelProvider' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE"; then
  echo "R5B1 deterministic provider must not be wired into production Services" >&2
  exit 1
fi
if grep -R -Eiq \
    'java\.net|okhttp|http://|https://|ioctl|sysfs|/dev/|CarPropertyManager|VehicleHal|Runtime\.getRuntime' \
    "$ROOT_DIR/$PROVIDER" "$ROOT_DIR/$PROBE"; then
  echo "R5B1 deterministic provider unexpectedly references network or hardware" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "R5B1 Deterministic Stub Provider"
require_text "docs/CENTRAL_BRAIN_ANDROID_RUNTIME_EVOLUTION_PLAN.md" "R5B1 deterministic stub provider"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R5B1 deterministic stub provider trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android R5B1 Deterministic Stub Provider"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R5B1 Deterministic Stub Provider"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R5B1 Deterministic Provider Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "R5B1 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "R5B1 进展"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "R5B1 deterministic stub provider"

echo "Central Brain Android deterministic model provider check passed"
