#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004, NV-F-011, NV-G-004/006, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROUTER="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/TestOnlyModelRouter.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/model/TestOnlyModelRouterTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/TestOnlyModelRouterProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing test model router file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing test model router pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in \
  "$ROUTER" \
  "$TEST" \
  "$PROBE" \
  "$DEBUG_MANIFEST" \
  "$RUNTIME" \
  "$GOVERNANCE" \
  "$INSTALLER"; do
  require_file "$path"
done

require_text "$ROUTER" 'TEST_ROUTE_ID = "test.deterministic.stub"'
require_text "$ROUTER" "createForContractTest("
require_text "$ROUTER" "routeTargetForContractTest("
require_text "$ROUTER" "FallbackPolicy.NO_FALLBACK"
require_text "$ROUTER" "scheduler.admit("
require_text "$ROUTER" "scheduler.claimNext()"
require_text "$ROUTER" "scheduler.settle("
require_text "$ROUTER" "provider.infer("
require_text "$ROUTER" "provider.cancel("
require_text "$ROUTER" "PROVIDER_IDENTITY_MISMATCH"
require_text "$ROUTER" "duplicateTerminalIgnoredCount++"
require_text "$ROUTER" "if (!record.matches(request))"
require_text "$ROUTER" "Router accepts only the deterministic TEST_ONLY provider"
require_text "$TEST" "schedulerLeaseDrivesTwoSequentialProviderRequests"
require_text "$TEST" "queuedAndRunningCancellationAreOwnerIsolated"
require_text "$TEST" "tickExpiresQueueAndCancelsRunningDeadline"
require_text "$TEST" "retryableAndIsolatedFaultsNeverFallback"
require_text "$TEST" "providerIdentityMismatchFailsClosed"
require_text "$TEST" "duplicateProviderTerminalIsIgnored"
require_text "$TEST" "exactReplayKeepsOriginalObserverAndChangedInputIsRejected"
require_text "$DEBUG_MANIFEST" ".model.TestOnlyModelRouterProbeActivity"

for marker in \
  "test_model_router_contract_verified=true" \
  "test_model_router_e2e_verified=true" \
  "scheduler_provider_lease_binding_verified=true" \
  "model_router_stream_forward_verified=true" \
  "model_router_cancel_verified=true" \
  "model_router_deadline_verified=true" \
  "model_router_no_fallback_verified=true" \
  "model_router_terminal_once_verified=true" \
  "model_router_provider_identity_verified=true" \
  "model_router_replay_validation_verified=true" \
  "model_router_profile_boundary_verified=true"; do
  require_text "$PROBE" "${marker%=true}="
  require_text "$INSTALLER" "$marker"
done
for marker in \
  "test_model_router_dispatch_verified=true" \
  "provider_infer_invoked_in_debug=true" \
  "model_router_test_only=true" \
  "model_router_implementation_available=true" \
  "production_model_router_wired=false" \
  "production_model_router_dispatch_enabled=false" \
  "production_inference_enabled=false" \
  "deterministic_stub_implementation_configured=false" \
  "deterministic_stub_routing_enabled=false" \
  "ollama_android_provider_configured=false" \
  "vendor_npu_provider_available=false" \
  "hardware_accessed=false"; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

if grep -Eq 'TestOnlyModelRouter' "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE"; then
  echo "R5B2 test model router must not be wired into production Services" >&2
  exit 1
fi
if grep -R -Eiq \
    'java\.net|okhttp|http://|https://|ioctl|sysfs|/dev/|CarPropertyManager|VehicleHal|Runtime\.getRuntime' \
    "$ROOT_DIR/$ROUTER" "$ROOT_DIR/$PROBE"; then
  echo "R5B2 test model router unexpectedly references network or hardware" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "R5B2 Test-Only Model Router"
require_text "docs/CENTRAL_BRAIN_ANDROID_RUNTIME_EVOLUTION_PLAN.md" "R5B2 test-only model router"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R5B2 test-only model router trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android R5B2 Test-Only Model Router"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R5B2 Test-Only Model Router"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R5B2 Test-Only Router Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md" "Android R5B2 Test-Only Model Router"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "R5B2 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "R5B2 进展"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "R5B2 test-only model router"

echo "Central Brain Android test-only model router check passed"
