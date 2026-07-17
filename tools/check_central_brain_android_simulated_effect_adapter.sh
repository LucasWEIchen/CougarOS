#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-ADP-001, S2-EFF-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEBUG_DIR="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/simulation"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/simulation/SimulatedEffectAdapterTest.java"
PROBE="$DEBUG_DIR/SimulatedEffectAdapterProbeActivity.java"
MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
INSTALLER="tools/install_central_brain_android_runtime.sh"
MAIN_DIR="central-brain/android-runtime/runtime-service/src/main/java"
RUNTIME="$MAIN_DIR/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="$MAIN_DIR/com/centralbrain/runtime/CentralBrainGovernanceService.java"

require_file() {
  local path="$1"
  [[ -f "$ROOT_DIR/$path" ]] \
    || { echo "missing Android simulated Effect adapter file: $path" >&2; exit 1; }
}

require_text() {
  local path="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$path" \
    || { echo "missing Android simulated Effect adapter marker '$marker' in $path" >&2; exit 1; }
}

for class in SimulatedEffectAdapter SimulationClock FaultInjectionProfile; do
  require_file "$DEBUG_DIR/$class.java"
done
for path in "$TEST" "$PROBE" "$MANIFEST" "$INSTALLER"; do
  require_file "$path"
done

require_text "$DEBUG_DIR/SimulatedEffectAdapter.java" 'implements EffectAdapter'
require_text "$DEBUG_DIR/SimulatedEffectAdapter.java" 'MAX_RECORDS = 128'
require_text "$DEBUG_DIR/SimulatedEffectAdapter.java" 'SignalSource.SIMULATED'
require_text "$DEBUG_DIR/SimulatedEffectAdapter.java" 'isProductionAuthorized()'
require_text "$DEBUG_DIR/SimulatedEffectAdapter.java" 'READBACK_MISMATCH'
require_text "$DEBUG_DIR/SimulatedEffectAdapter.java" 'RETRYABLE_FAILURE'
require_text "$DEBUG_DIR/SimulatedEffectAdapter.java" 'TERMINAL_FAILURE'
require_text "$DEBUG_DIR/SimulationClock.java" 'advanceBy(long durationMs)'
require_text "$DEBUG_DIR/FaultInjectionProfile.java" 'MAX_DURATION_MS = 60_000L'
require_text "$DEBUG_DIR/FaultInjectionProfile.java" 'central-brain-simulation-fault-v1'

for test_name in \
  descriptorIsSafeExplicitlySimulatedAndNeverProductionAuthorized \
  immediateApplyIsIdempotentAndTokenConflictFailsClosed \
  manualClockCompletesDelayWithoutSleepingOrDuplicateCallback \
  timeoutRemainsUnknownAndReadbackBecomesTimedOut \
  retryableAndTerminalFailuresStayDistinct \
  readbackMismatchDoesNotRewriteAppliedDeliveryState \
  clockFaultBoundsAndResetFailClosed; do
  require_text "$TEST" "$test_name"
done

require_text "$MANIFEST" '.simulation.SimulatedEffectAdapterProbeActivity'
for marker in \
  simulated_effect_adapter_base_defined=true \
  simulation_descriptor_verified=true \
  simulation_clock_verified=true \
  simulation_delay_verified=true \
  simulation_timeout_verified=true \
  simulation_failure_verified=true \
  simulation_readback_mismatch_verified=true \
  simulation_idempotency_verified=true \
  simulated_effect_adapter_android13_arm64_verified=true; do
  require_text "$PROBE" "${marker%=true}="
  require_text "$INSTALLER" "$marker"
done
for marker in \
  simulated_effect_adapter_debug_only=true \
  simulated_effect_adapter_production_registered=false \
  simulated_effect_adapter_runtime_wired=false \
  scenario_plan_runtime_published=false \
  scenario_graph_execution_enabled=false \
  effect_dispatch_enabled=false \
  vehicle_signal_provider_wired=false \
  hardware_accessed=false; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

for class in SimulatedEffectAdapter SimulationClock FaultInjectionProfile; do
  if find "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main" \
      "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/release" \
      -type f -name "$class.java" -print -quit 2>/dev/null | grep -q .; then
    echo "P2-W08 simulation class leaked into main/release source: $class" >&2
    exit 1
  fi
done
if grep -R -Eiq \
    'android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|java[.]net|okhttp|http://|androidx[.]room|runtime[.]model|ModelProvider|InferenceResourceScheduler|ioctl|sysfs|/dev/' \
    "$ROOT_DIR/$DEBUG_DIR/SimulatedEffectAdapter.java" \
    "$ROOT_DIR/$DEBUG_DIR/SimulationClock.java" \
    "$ROOT_DIR/$DEBUG_DIR/FaultInjectionProfile.java" \
    "$ROOT_DIR/$PROBE"; then
  echo "P2-W08 simulation base unexpectedly references persistence, network, model, or hardware access" >&2
  exit 1
fi
if grep -Eq 'SimulatedEffectAdapter|SimulationClock|FaultInjectionProfile' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE"; then
  echo "P2-W08 simulation base must not be registered in production Services" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P2-W08 Simulated Effect Adapter Base"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P2-W08` SimulatedVehicleAdapter base'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P2-W08 Simulated Effect Adapter base trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P2-W08 Simulated Effect Adapter Base"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P2-W08 Simulated Effect Adapter Base"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P2-W08 Simulated Effect Adapter Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "P2-W08 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "P2-W08 进展"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P2-W08 SimulatedVehicleAdapter base"

printf '%s\n' \
  "Central Brain Android simulated Effect adapter base check passed" \
  "simulated_effect_adapter_base_defined=true" \
  "simulated_effect_adapter_debug_only=true" \
  "simulated_effect_adapter_release_source_absent=true" \
  "simulated_effect_adapter_production_registered=false" \
  "simulated_effect_adapter_runtime_wired=false" \
  "effect_dispatch_enabled=false" \
  "hardware_accessed=false"
