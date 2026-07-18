#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-CTX-001, S2-EVT-001, S2-SAF-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/ContextSourceAdapter.java"
RUNTIME_SOURCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/RuntimeHealthContextSourceAdapter.java"
VEHICLE_SOURCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/SimulatedVehicleSignalContextSourceAdapter.java"
TIME_SOURCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/TimeContextSourceAdapter.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/events/ContextSourceAdaptersTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/events/ContextSourceAdaptersProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
TRIGGER="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/TriggerEngine.java"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GRAPH_RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/AgentGraphRuntime.java"
EFFECT_COORDINATOR="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/EffectCoordinator.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P6-W05 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$CONTRACT" "$RUNTIME_SOURCE" "$VEHICLE_SOURCE" "$TIME_SOURCE" \
    "$TEST" "$PROBE" "$DEBUG_MANIFEST" "$MAIN_MANIFEST" "$TRIGGER" \
    "$RUNTIME_SERVICE" "$GRAPH_RUNTIME" "$EFFECT_COORDINATOR" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P6-W05 file missing: $file" >&2; exit 1; }
done

for marker in \
  'int SCHEMA_VERSION = 1' \
  'int INITIAL_SOURCE_COUNT = 3' \
  'RUNTIME_HEALTH(' \
  'SIMULATED_VEHICLE_SIGNAL(' \
  'TIME(' \
  'enum ResultCode' \
  'REJECTED_FUTURE' \
  'REJECTED_PROVENANCE' \
  'class Descriptor' \
  'class SourceValue' \
  'class Observation' \
  'class AdaptationResult' \
  'isProductionPublished()' \
  'isProductionRegistryPublished()' \
  'isTriggerEngineWired()' \
  'isVehiclePropertyMappingConfigured()' \
  'isHardwareAccessed()'; do
  require_text "$CONTRACT" "$marker"
done

require_text "$RUNTIME_SOURCE" 'enum HealthState'
require_text "$RUNTIME_SOURCE" 'class RuntimeHealthSample'
require_text "$RUNTIME_SOURCE" 'ResultCode.SOURCE_UNAVAILABLE'
require_text "$RUNTIME_SOURCE" 'ResultCode.STALE'
require_text "$VEHICLE_SOURCE" 'input.getSource().isSimulated()'
require_text "$VEHICLE_SOURCE" 'ResultCode.REJECTED_PROVENANCE'
require_text "$VEHICLE_SOURCE" 'normalizedQuality = SignalQuality.STALE'
require_text "$VEHICLE_SOURCE" 'Set.of("P", "R", "N", "D", "S", "L", "M", "UNKNOWN")'
require_text "$TEST" '"driver message"'
require_text "$TIME_SOURCE" 'class TimeSample'
require_text "$TIME_SOURCE" 'Math.floorMod('
require_text "$TIME_SOURCE" 'ResultCode.REJECTED_FUTURE'

for test_name in \
  initialDescriptorCatalogIsFixedAndUnpublished \
  runtimeHealthNormalizesFreshStaleAndUnavailable \
  simulatedVehicleSignalPreservesTypeAndNormalizesFreshness \
  simulatedVehicleSignalRejectsNonSimulatedAndContradictoryQuality \
  timeSourceUsesInjectedClockAndRejectsFutureSample \
  contractRemainsFailClosedAndDisconnected; do
  require_text "$TEST" "$test_name"
done

for marker in \
  context_source_probe_complete \
  context_source_allowlist_verified \
  context_source_runtime_health_verified \
  context_source_simulated_vehicle_verified \
  context_source_time_verified \
  context_source_freshness_quality_verified \
  context_source_fail_closed_verified \
  context_source_android13_arm64_verified; do
  require_text "$PROBE" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
for marker in \
  context_source_count=3 \
  context_source_production_registry_published=false \
  context_source_runtime_wired=false \
  context_source_trigger_engine_wired=false \
  vehicle_signal_provider_wired=false \
  vehicle_property_mapping_configured=false \
  graph_execution_enabled=false \
  effect_dispatch_enabled=false \
  model_invoked=false \
  npu_accessed=false \
  network_accessed=false \
  hardware_accessed=false \
  production_ready=false \
  target_hardware_validated=false; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

require_text "$PROBE" 'getStringExtra("nonce")'
require_text "$DEBUG_MANIFEST" '.events.ContextSourceAdaptersProbeActivity'
if grep -Fq 'ContextSourceAdaptersProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P6-W05 Context source debug probe leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq \
    'ContextSourceAdapter|RuntimeHealthContextSourceAdapter|SimulatedVehicleSignalContextSourceAdapter|TimeContextSourceAdapter' \
    "$ROOT_DIR/$TRIGGER" "$ROOT_DIR/$RUNTIME_SERVICE" \
    "$ROOT_DIR/$GRAPH_RUNTIME" "$ROOT_DIR/$EFFECT_COORDINATOR"; then
  echo "P6-W05 Context source adapters were wired into Trigger/Runtime/Graph/Effect" >&2
  exit 1
fi
if grep -R -Eiq \
    'android[.]car|CarPropertyManager|VehicleHal|VehiclePropertyIds|java[.]io|java[.]net|okhttp|http://|https://|ModelProvider|NpuProvider|ioctl|sysfs|/dev/|System[.]currentTimeMillis|System[.]nanoTime|Clock[.]system|android[.]os[.]Binder|ClassLoader|DexClassLoader' \
    "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$RUNTIME_SOURCE" \
    "$ROOT_DIR/$VEHICLE_SOURCE" "$ROOT_DIR/$TIME_SOURCE"; then
  echo "P6-W05 references direct clock, Binder, transport, vehicle, model, hardware, or dynamic runtime APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P6-W05 Context source adapters"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P6-W05` Context source adapters'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P6-W05 Context source adapters trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P6-W05 Context source adapters"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P6-W05 Context source adapters architecture"
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" "P6-W05 Context source adapters detailed design"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P6-W05 Context source adapters"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P6-W05 Context source adapters Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "P6-W05 Context source adapters are contracts, not production providers"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "P6-W05 Context source adapters progress"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P6-W05 Context source adapters progress"
require_text "README.md" "P6 Context source adapters"

printf '%s\n' \
  "Central Brain Android Context source adapters check passed" \
  "context_source_adapter_contract_defined=true" \
  "context_source_count=3" \
  "context_source_allowlist_verified=true" \
  "context_source_runtime_health_verified=true" \
  "context_source_simulated_vehicle_verified=true" \
  "context_source_time_verified=true" \
  "context_source_freshness_quality_verified=true" \
  "context_source_fail_closed_verified=true" \
  "context_source_android13_arm64_verified=false" \
  "context_source_production_registry_published=false" \
  "context_source_runtime_wired=false" \
  "context_source_trigger_engine_wired=false" \
  "vehicle_signal_provider_wired=false" \
  "vehicle_property_mapping_configured=false" \
  "graph_execution_enabled=false" \
  "effect_dispatch_enabled=false" \
  "model_invoked=false" \
  "npu_accessed=false" \
  "network_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
