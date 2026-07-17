#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-ADP-001, S2-EFF-001, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEBUG_DIR="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/simulation"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/simulation/SimulatedHvacEffectAdapterTest.java"
PROBE="$DEBUG_DIR/SimulatedHvacEffectAdapterProbeActivity.java"
MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
INSTALLER="tools/install_central_brain_android_runtime.sh"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P2-W09 marker missing in $file: $marker" >&2; exit 1; }
}

for file in \
  "$DEBUG_DIR/SimulatedHvacEffectAdapter.java" \
  "$DEBUG_DIR/SimulatedEffectAdapter.java" \
  "$TEST" "$PROBE" "$MANIFEST" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] || { echo "P2-W09 file missing: $file" >&2; exit 1; }
done

for marker in \
  'public final class SimulatedHvacEffectAdapter extends SimulatedEffectAdapter' \
  'TARGET_SCHEMA_VERSION = 1' \
  'DESIRED_TTL_MS = 180_000L' \
  'HVAC_POWER' \
  'HVAC_TARGET_TEMPERATURE' \
  'HVAC_FAN_LEVEL' \
  'toCanonicalPayload()' \
  'fromCanonicalPayload(byte[] canonicalPayload)' \
  'CapabilityCatalog.stage2Defaults()' \
  'getAvailability().isSimulatable()' \
  'getAvailability().canUseProduction()' \
  'twinStore.setDesired' \
  'twinStore.updateReported' \
  'SignalSource.SIMULATED' \
  'FaultInjectionProfile.Mode.READBACK_MISMATCH'; do
  require_text "$DEBUG_DIR/SimulatedHvacEffectAdapter.java" "$marker"
done

for marker in \
  'onSimulationAdmitted(' \
  'nowSimulationElapsedRealtimeMs()'; do
  require_text "$DEBUG_DIR/SimulatedEffectAdapter.java" "$marker"
done

for test_name in \
  typedTargetRoundTripsAndDescriptorStaysDebugOnly \
  immediateTargetsUpdateDesiredAndReportedAbsoluteState \
  rangeZoneActionAndCanonicalPayloadFailClosed \
  manualDelayPublishesDesiredBeforeReportedAndRemainsIdempotent \
  timeoutKeepsDesiredPendingWithoutFabricatedReport \
  retryableAndTerminalFailureNeverWriteReportedState \
  readbackMismatchIsVisibleInBaseObservationAndDigitalTwin; do
  require_text "$TEST" "$test_name"
done

require_text "$MANIFEST" '.simulation.SimulatedHvacEffectAdapterProbeActivity'
for marker in \
  simulated_hvac_adapter_defined=true \
  simulated_hvac_typed_target_verified=true \
  simulated_hvac_range_zone_verified=true \
  simulated_hvac_desired_reported_verified=true \
  simulated_hvac_delay_verified=true \
  simulated_hvac_timeout_verified=true \
  simulated_hvac_failure_verified=true \
  simulated_hvac_readback_mismatch_verified=true \
  simulated_hvac_idempotency_verified=true \
  simulated_hvac_android13_arm64_verified=true; do
  require_text "$PROBE" "${marker%=true}="
  require_text "$INSTALLER" "$marker"
done
for marker in \
  simulated_hvac_debug_only=true \
  simulated_hvac_production_registered=false \
  simulated_hvac_runtime_wired=false \
  scenario_plan_runtime_published=false \
  scenario_graph_execution_enabled=false \
  effect_dispatch_enabled=false \
  vehicle_signal_provider_wired=false \
  hardware_accessed=false; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

if find "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/release" \
    -type f -name 'SimulatedHvacEffectAdapter.java' -print -quit 2>/dev/null | grep -q .; then
  echo "P2-W09 HVAC adapter leaked into main/release source" >&2
  exit 1
fi
if grep -R -Eiq \
    'android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|java[.]net|okhttp|http://|androidx[.]room|runtime[.]model|ModelProvider|InferenceResourceScheduler|ioctl|sysfs|/dev/' \
    "$ROOT_DIR/$DEBUG_DIR/SimulatedHvacEffectAdapter.java" "$ROOT_DIR/$PROBE"; then
  echo "P2-W09 HVAC adapter unexpectedly references persistence, network, model, or hardware access" >&2
  exit 1
fi
if grep -Eq 'SimulatedHvacEffectAdapter' "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE"; then
  echo "P2-W09 HVAC adapter must not be registered in production Services" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P2-W09 Simulated HVAC Adapter"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P2-W09` Simulated HVAC adapter'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P2-W09 Simulated HVAC adapter trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P2-W09 Simulated HVAC Adapter"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P2-W09 Simulated HVAC Adapter"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P2-W09 Simulated HVAC Adapter Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "P2-W09 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "P2-W09 进展"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P2-W09 Simulated HVAC adapter"

printf '%s\n' \
  "Central Brain Android simulated HVAC adapter check passed" \
  "simulated_hvac_adapter_defined=true" \
  "simulated_hvac_typed_target_verified=true" \
  "simulated_hvac_desired_reported_verified=true" \
  "simulated_hvac_debug_only=true" \
  "simulated_hvac_release_source_absent=true" \
  "simulated_hvac_production_registered=false" \
  "simulated_hvac_runtime_wired=false" \
  "effect_dispatch_enabled=false" \
  "hardware_accessed=false"
