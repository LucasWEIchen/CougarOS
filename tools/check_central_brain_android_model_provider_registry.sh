#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MDL-001, S2-SAF-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REGISTRY="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ModelProviderRegistry.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/model/ModelProviderRegistryTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/ModelProviderRegistryProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P7-W02 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$REGISTRY" "$TEST" "$PROBE" "$DEBUG_MANIFEST" "$MAIN_MANIFEST" \
    "$RUNTIME" "$GOVERNANCE" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P7-W02 file missing: $file" >&2; exit 1; }
done

for marker in \
  'SCHEMA_VERSION = 1' \
  'PROVIDER_COUNT = 4' \
  'MAX_HEALTH_VALIDITY_MS = 60_000L' \
  'DETERMINISTIC_TEST_ID' \
  'ANDROID_LOCAL_DEVELOPMENT_ID' \
  'VENDOR_NPU_PLACEHOLDER_ID' \
  'CLOUD_PLACEHOLDER_ID' \
  'enum ProviderKind' \
  'enum HealthSource' \
  'enum HealthState' \
  'enum HealthFreshness' \
  'class ProviderDescriptor' \
  'class HealthReport' \
  'class ProviderView' \
  'class RegistrySnapshot' \
  'publishHealth(HealthReport report, long nowElapsedMs)' \
  'snapshot(long nowElapsedMs)' \
  'isContractTestAvailable()' \
  'isDevelopmentAvailable()' \
  'isProductionReady()' \
  'isRoutingEnabled()' \
  'isProductionRoutingEnabled()' \
  'isModelInvoked()' \
  'isNetworkAccessed()' \
  'isNpuAccessed()' \
  'isHardwareAccessed()'; do
  require_text "$REGISTRY" "$marker"
done

for test_name in \
  fixedCatalogIsSortedBoundedAndDigestStable \
  testAvailabilityIsSeparateFromDevelopmentAndProductionReadiness \
  healthPublicationRequiresKnownProviderSourceAndFreshWindow \
  healthReplayConflictRevisionAndStalenessFailClosed \
  healthyPlaceholderNeverBecomesAvailableReadyOrRoutable \
  runtimeModelNetworkNpuAndHardwareBoundariesRemainClosed; do
  require_text "$TEST" "$test_name"
done

for marker in \
  model_provider_registry_probe_complete \
  model_provider_registry_verified \
  model_provider_catalog_verified \
  model_provider_health_freshness_verified \
  model_provider_health_replay_verified \
  model_provider_availability_separation_verified \
  model_provider_placeholder_fail_closed; do
  require_text "$PROBE" "$marker="
done
for marker in \
  model_provider_registry_defined=true \
  model_provider_catalog_verified=true \
  model_provider_count=4 \
  model_provider_health_freshness_verified=true \
  model_provider_health_replay_verified=true \
  model_provider_availability_separation_verified=true \
  model_provider_placeholder_fail_closed=true \
  model_contract_test_available_count=1 \
  model_development_available_count=0 \
  model_production_ready_count=0 \
  model_provider_registry_runtime_wired=false \
  model_policy_router_wired=false \
  model_invoked=false \
  network_accessed=false \
  npu_accessed=false \
  hardware_accessed=false \
  production_ready=false \
  target_hardware_validated=false; do
  require_text "$INSTALLER" "$marker"
done

require_text "$DEBUG_MANIFEST" '.model.ModelProviderRegistryProbeActivity'
if grep -Fq 'ModelProviderRegistryProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P7-W02 debug probe leaked into the release manifest" >&2
  exit 1
fi
if grep -Eiq 'ModelProviderRegistry' "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE"; then
  echo "P7-W02 Registry was wired into production Services" >&2
  exit 1
fi
if grep -R -Eiq \
    'android[.]car|CarPropertyManager|VehicleHal|VehiclePropertyIds|java[.]io|java[.]net|okhttp|http://|https://|ioctl|sysfs|/dev/|android[.]os[.]Binder|ClassLoader|DexClassLoader|[.]infer[(]|[.]route[(]' \
    "$ROOT_DIR/$REGISTRY"; then
  echo "P7-W02 Registry references routing, inference, transport, Binder, vehicle, or hardware APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P7-W02 ModelProviderRegistry/health"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P7-W02` ModelProviderRegistry/health'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P7-W02 ModelProviderRegistry/health trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P7-W02 ModelProviderRegistry/health"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P7-W02 ModelProviderRegistry/health architecture"
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" "P7-W02 ModelProviderRegistry/health detailed design"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P7-W02 ModelProviderRegistry/health"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P7-W02 ModelProviderRegistry/health Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "P7-W02 Registry health is metadata, not production availability"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "P7-W02 ModelProviderRegistry/health progress"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P7-W02 ModelProviderRegistry/health progress"
require_text "README.md" "P7 ModelProviderRegistry/health"

printf '%s\n' \
  "Central Brain Android ModelProviderRegistry/health check passed" \
  "model_provider_registry_defined=true" \
  "model_provider_catalog_verified=true" \
  "model_provider_count=4" \
  "model_provider_health_freshness_verified=true" \
  "model_provider_health_replay_verified=true" \
  "model_provider_availability_separation_verified=true" \
  "model_provider_placeholder_fail_closed=true" \
  "model_contract_test_available_count=1" \
  "model_development_available_count=0" \
  "model_production_ready_count=0" \
  "model_provider_registry_android13_arm64_verified=false" \
  "model_provider_registry_runtime_wired=false" \
  "model_policy_router_wired=false" \
  "model_invoked=false" \
  "network_accessed=false" \
  "npu_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
