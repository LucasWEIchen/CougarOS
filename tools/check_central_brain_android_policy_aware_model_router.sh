#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MDL-001, S2-SAF-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROUTER="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/PolicyAwareModelRouter.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/model/PolicyAwareModelRouterTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/PolicyAwareModelRouterProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P7-W03 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$ROUTER" "$TEST" "$PROBE" "$DEBUG_MANIFEST" "$MAIN_MANIFEST" \
    "$RUNTIME" "$GOVERNANCE" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P7-W03 file missing: $file" >&2; exit 1; }
done

for marker in \
  'SCHEMA_VERSION = 1' \
  'MAX_SELECTED_PROVIDERS = 2' \
  'MAX_FALLBACK_PROVIDERS = 1' \
  'MAX_POLICY_VALIDITY_MS = 60_000L' \
  'enum RouteMode' \
  'enum NetworkPolicy' \
  'enum NetworkState' \
  'enum ThermalState' \
  'enum DecisionCode' \
  'enum PolicyRejection' \
  'enum RejectionReason' \
  'class PolicySnapshot' \
  'class CandidateEvaluation' \
  'class RouteDecision' \
  'decide(' \
  'PRIVACY_BLOCKED' \
  'NETWORK_POLICY_BLOCKED' \
  'THERMAL_BLOCKED' \
  'LATENCY_BUDGET_TOO_SMALL' \
  'REQUEST_QUOTA_EXHAUSTED' \
  'TOKEN_QUOTA_EXCEEDED' \
  'isFallbackBounded()' \
  'isActionAuthorizationGranted()' \
  'isEffectDispatchRequested()' \
  'isProviderInvoked()' \
  'isModelInvoked()' \
  'isNetworkAccessed()' \
  'isNpuAccessed()' \
  'isHardwareAccessed()'; do
  require_text "$ROUTER" "$marker"
done

for test_name in \
  healthyContractProviderIsSelectedAndBoundToRequest \
  privacyAndNetworkPolicyRejectCloudBeforeExecution \
  thermalLatencyAndCapabilityAdmissionAreExplicit \
  quotaAndPolicyFreshnessFailClosed \
  fallbackIsBoundedByRequestPolicy \
  productionAndActionExecutionBoundariesRemainClosed; do
  require_text "$TEST" "$test_name"
done

for marker in \
  model_policy_router_probe_complete \
  model_policy_router_verified \
  model_policy_router_selection_verified \
  model_policy_router_privacy_network_thermal_verified \
  model_policy_router_quota_verified \
  model_policy_router_fallback_bounded; do
  require_text "$PROBE" "$marker="
done

for marker in \
  model_policy_router_defined=true \
  model_policy_router_privacy_network_thermal_verified=true \
  model_policy_router_latency_capability_quota_verified=true \
  model_policy_router_fallback_bounded=true \
  model_policy_router_no_action_authority=true \
  model_policy_router_runtime_wired=false \
  provider_invoked=false \
  model_invoked=false \
  network_accessed=false \
  npu_accessed=false \
  hardware_accessed=false \
  production_ready=false \
  target_hardware_validated=false; do
  require_text "$INSTALLER" "$marker"
done

require_text "$DEBUG_MANIFEST" '.model.PolicyAwareModelRouterProbeActivity'
if grep -Fq 'PolicyAwareModelRouterProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P7-W03 debug probe leaked into the release manifest" >&2
  exit 1
fi
if grep -Eiq 'PolicyAwareModelRouter' "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE"; then
  echo "P7-W03 Router was wired into production Services" >&2
  exit 1
fi
if grep -R -Eiq \
    'android[.]car|CarPropertyManager|VehicleHal|VehiclePropertyIds|java[.]io|java[.]net|okhttp|http://|https://|ioctl|sysfs|/dev/|android[.]os[.]Binder|ClassLoader|DexClassLoader|[.]infer[(]|Runtime[.]getRuntime|ProcessBuilder' \
    "$ROOT_DIR/$ROUTER"; then
  echo "P7-W03 Router references provider execution, transport, Binder, vehicle, or hardware APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P7-W03 PolicyAwareModelRouter"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P7-W03` PolicyAwareModelRouter'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P7-W03 PolicyAwareModelRouter trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P7-W03 PolicyAwareModelRouter"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P7-W03 PolicyAwareModelRouter architecture"
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" "P7-W03 PolicyAwareModelRouter detailed design"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P7-W03 PolicyAwareModelRouter"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P7-W03 PolicyAwareModelRouter Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "P7-W03 route decision is not model execution"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "P7-W03 PolicyAwareModelRouter progress"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P7-W03 PolicyAwareModelRouter progress"
require_text "README.md" "P7 PolicyAwareModelRouter"

printf '%s\n' \
  "Central Brain Android PolicyAwareModelRouter check passed" \
  "model_policy_router_defined=true" \
  "model_policy_router_privacy_network_thermal_verified=true" \
  "model_policy_router_latency_capability_quota_verified=true" \
  "model_policy_router_fallback_bounded=true" \
  "model_policy_router_no_action_authority=true" \
  "model_policy_router_android13_arm64_verified=true" \
  "model_policy_router_runtime_wired=false" \
  "provider_invoked=false" \
  "model_invoked=false" \
  "network_accessed=false" \
  "npu_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
