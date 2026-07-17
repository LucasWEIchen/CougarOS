#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-ADP-001, S2-SAF-001, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEBUG_DIR="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/simulation"
ADAPTER="$DEBUG_DIR/SimulatedSeatEffectAdapter.java"
BASE="$DEBUG_DIR/SimulatedEffectAdapter.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/simulation/SimulatedSeatEffectAdapterTest.java"
PROBE="$DEBUG_DIR/SimulatedSeatEffectAdapterProbeActivity.java"
MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
INSTALLER="tools/install_central_brain_android_runtime.sh"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P2-W10 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$ADAPTER" "$BASE" "$TEST" "$PROBE" "$MANIFEST" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] || { echo "P2-W10 file missing: $file" >&2; exit 1; }
done

for marker in \
  'public final class SimulatedSeatEffectAdapter extends SimulatedEffectAdapter' \
  'TARGET_SCHEMA_VERSION = 1' \
  'DESIRED_TTL_MS = 180_000L' \
  'SEAT_HEATING' \
  'SEAT_VENTILATION' \
  'SEAT_RECLINE_ANGLE' \
  'SeatOccupantStateProvider' \
  'SeatApprovalVerifier' \
  'SafetyVehicleStateProvider' \
  'toCanonicalPayload()' \
  'fromCanonicalPayload(byte[] payload)' \
  'CapabilityCatalog.stage2Defaults()' \
  'getAvailability().isSimulatable()' \
  'getAvailability().canUseProduction()' \
  'twinStore.setDesired' \
  'twinStore.updateReported' \
  'SignalSource.SIMULATED' \
  'validateSimulationDispatch(' \
  'rejectSimulationDispatch(' \
  'SeatProgressObservation'; do
  require_text "$ADAPTER" "$marker"
done

for marker in \
  'SimulationDispatchRejectedException' \
  'validateSimulationDispatch(' \
  'rejectSimulationDispatch(String reasonCode)' \
  'DeliveryState.REJECTED' \
  'ReadbackState.TERMINAL_FAILURE'; do
  require_text "$BASE" "$marker"
done

for test_name in \
  typedTargetRoundTripsAndProvidersStaySimulationOnly \
  heatingAndVentilationUpdateAbsoluteDesiredAndReported \
  parkedApprovedReclinePublishesBoundedProgressAndReadback \
  movingUnknownBeltedOrUnoccupiedReclineRejectsBeforeAdmission \
  beltChangeRacePermanentlyRejectsAtDispatchWithoutReportedState \
  motionAndApprovalChangesAreRevalidatedAtDispatch \
  rangeAreaApprovalAndCanonicalPayloadFailClosed \
  timeoutRetryMismatchAndDuplicateStayObservableAndIdempotent; do
  require_text "$TEST" "$test_name"
done

require_text "$MANIFEST" '.simulation.SimulatedSeatEffectAdapterProbeActivity'
for marker in \
  simulated_seat_adapter_defined=true \
  simulated_seat_typed_target_verified=true \
  simulated_seat_heat_vent_verified=true \
  simulated_seat_recline_safety_verified=true \
  simulated_seat_dispatch_revalidation_verified=true \
  simulated_seat_belt_race_verified=true \
  simulated_seat_progress_verified=true \
  simulated_seat_fault_readback_verified=true \
  simulated_seat_idempotency_verified=true \
  simulated_seat_android13_arm64_verified=true; do
  require_text "$PROBE" "${marker%=true}="
  require_text "$INSTALLER" "$marker"
done
for marker in \
  simulated_seat_debug_only=true \
  simulated_seat_production_registered=false \
  simulated_seat_runtime_wired=false \
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
    -type f -name 'SimulatedSeatEffectAdapter.java' -print -quit 2>/dev/null | grep -q .; then
  echo "P2-W10 Seat adapter leaked into main/release source" >&2
  exit 1
fi
if grep -R -Eiq \
    'android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|java[.]net|okhttp|http://|androidx[.]room|runtime[.]model|ModelProvider|InferenceResourceScheduler|ioctl|sysfs|/dev/' \
    "$ROOT_DIR/$ADAPTER" "$ROOT_DIR/$PROBE"; then
  echo "P2-W10 Seat adapter unexpectedly references persistence, network, model, or hardware access" >&2
  exit 1
fi
if grep -Eq 'SimulatedSeatEffectAdapter' "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE"; then
  echo "P2-W10 Seat adapter must not be registered in production Services" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P2-W10 Simulated Seat Adapter"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P2-W10` Simulated Seat adapter'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P2-W10 Simulated Seat adapter trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P2-W10 Simulated Seat Adapter"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P2-W10 Simulated Seat Adapter"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P2-W10 Simulated Seat Adapter Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "P2-W10 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "P2-W10 进展"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P2-W10 Simulated Seat adapter"

printf '%s\n' \
  "Central Brain Android simulated Seat adapter check passed" \
  "simulated_seat_adapter_defined=true" \
  "simulated_seat_typed_target_verified=true" \
  "simulated_seat_recline_safety_verified=true" \
  "simulated_seat_dispatch_revalidation_verified=true" \
  "simulated_seat_progress_verified=true" \
  "simulated_seat_debug_only=true" \
  "simulated_seat_release_source_absent=true" \
  "simulated_seat_production_registered=false" \
  "simulated_seat_runtime_wired=false" \
  "effect_dispatch_enabled=false" \
  "hardware_accessed=false"
