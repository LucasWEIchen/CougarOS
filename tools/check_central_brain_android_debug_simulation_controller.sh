#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-CTX-001, S2-ADP-001, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEBUG_ROOT="central-brain/android-runtime/runtime-service/src/debug"
CONTROLLER="$DEBUG_ROOT/java/com/centralbrain/runtime/simulation/DebugSimulationController.java"
SERVICE="$DEBUG_ROOT/java/com/centralbrain/runtime/simulation/DebugSimulationControllerService.java"
PROBE="$DEBUG_ROOT/java/com/centralbrain/runtime/simulation/DebugSimulationControllerProbeActivity.java"
AIDL="$DEBUG_ROOT/aidl/com/centralbrain/runtime/simulation/IDebugSimulationController.aidl"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/simulation/DebugSimulationControllerTest.java"
MANIFEST="$DEBUG_ROOT/AndroidManifest.xml"
DEBUG_POLICY="$DEBUG_ROOT/res/xml/central_brain_capability_policy.xml"
MAIN_POLICY="central-brain/android-runtime/runtime-service/src/main/res/xml/central_brain_capability_policy.xml"
CAPABILITY_POLICY="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/policy/CallerCapabilityPolicy.java"
BUILD="central-brain/android-runtime/runtime-service/build.gradle.kts"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P2-W12 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$CONTROLLER" "$SERVICE" "$PROBE" "$AIDL" "$TEST" \
    "$MANIFEST" "$DEBUG_POLICY" "$MAIN_POLICY" "$CAPABILITY_POLICY" \
    "$BUILD" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P2-W12 file missing: $file" >&2; exit 1; }
done

for marker in \
  'interface IDebugSimulationController' \
  'const int INTERFACE_VERSION = 1' \
  'setDrivingState' \
  'setSignal' \
  'setAdapterFault' \
  'advanceSimulationClock' \
  'reset()' \
  'getSnapshotDigest'; do
  require_text "$AIDL" "$marker"
done

for marker in \
  'public final class DebugSimulationController' \
  'MAX_AUDIT_ENTRIES = 128' \
  'VehicleSignalPath' \
  'SignalSource.SIMULATED' \
  'SimulatedHvacEffectAdapter' \
  'SimulatedSeatEffectAdapter' \
  'SimulatedMediaEffectAdapter' \
  'SimulatedNavigationEffectAdapter' \
  'FaultInjectionProfile' \
  'appendAudit' \
  'isProductionAuthorized()' \
  'return false;'; do
  require_text "$CONTROLLER" "$marker"
done

for marker in \
  'public final class DebugSimulationControllerService extends Service' \
  'CONTROL_DEBUG_SIMULATION' \
  'BuildConfig.DEBUG' \
  'enforceCallingOrSelfPermission' \
  'Capability.SIMULATION_CONTROL' \
  'resolveCallingIdentity()' \
  'debug_simulation_audit_event=true' \
  'outcome=REJECTED' \
  'hardware_accessed=false'; do
  require_text "$SERVICE" "$marker"
done

require_text "$BUILD" 'aidl = true'
require_text "$MANIFEST" 'com.centralbrain.permission.CONTROL_DEBUG_SIMULATION'
require_text "$MANIFEST" 'android:protectionLevel="signature"'
require_text "$MANIFEST" '<uses-permission'
require_text "$MANIFEST" '.simulation.DebugSimulationControllerService'
require_text "$MANIFEST" '.simulation.DebugSimulationControllerProbeActivity'
require_text "$MANIFEST" 'android:exported="true"'
require_text "$CAPABILITY_POLICY" 'SIMULATION_CONTROL("debug.simulation.control")'
require_text "$DEBUG_POLICY" '<capability name="debug.simulation.control" />'
if grep -Fq 'debug.simulation.control' "$ROOT_DIR/$MAIN_POLICY"; then
  echo "P2-W12 debug capability leaked into the production policy resource" >&2
  exit 1
fi

for test_name in \
  defaultStateIsBoundedSimulationOnlyAndProductionUnauthorized \
  drivingAndCanonicalTypedSignalsChangeVersionedDigest \
  scalarUnionAndCanonicalPathAreaFailClosed \
  fixedAdapterFaultRegistryUpdatesActualSimulatedAdapters \
  clockAndResetClearStateButRetainBoundedAudit \
  auditEvictsOldestWithoutRetainingRawSignalText \
  sameCommandSequenceProducesDeterministicSnapshotDigest; do
  require_text "$TEST" "$test_name"
done

for marker in \
  debug_simulation_controller_probe_complete=true \
  debug_simulation_controller_defined=true \
  debug_simulation_controller_aidl_version=1 \
  debug_simulation_controller_signature_permission_enforced=true \
  debug_simulation_controller_capability_enforced=true \
  debug_simulation_controller_state_signal_fault_clock_reset_verified=true \
  debug_simulation_controller_audit_bounded_verified=true \
  debug_simulation_controller_android13_arm64_verified=true \
  debug_simulation_controller_debug_only=true \
  debug_simulation_controller_production_exported=false \
  debug_simulation_controller_runtime_wired=false \
  vehicle_signal_provider_wired=false \
  hardware_accessed=false; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

if find "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/release" \
    -type f \( -name 'DebugSimulationController*.java' \
      -o -name 'IDebugSimulationController.aidl' \) \
    -print -quit 2>/dev/null | grep -q .; then
  echo "P2-W12 controller leaked into main/release source" >&2
  exit 1
fi
if grep -R -Eq \
    'DebugSimulationController|CONTROL_DEBUG_SIMULATION|debug[.]simulation[.]control' \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"; then
  echo "P2-W12 controller was registered in a production Service or manifest" >&2
  exit 1
fi
if grep -R -Eiq \
    'android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|java[.]net|okhttp|http://|https://|ioctl|sysfs|/dev/|ModelProvider|InferenceResourceScheduler' \
    "$ROOT_DIR/$CONTROLLER" "$ROOT_DIR/$SERVICE"; then
  echo "P2-W12 controller unexpectedly references vehicle, network, model, or hardware APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P2-W12 Debug Simulation Controller"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P2-W12` Debug Context Controller'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P2-W12 Debug Simulation Controller trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P2-W12 Debug Simulation Controller"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P2-W12 Debug Simulation Controller"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P2-W12 Debug Simulation Controller Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "P2-W12 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "P2-W12 进展"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P2-W12 Debug Context Controller"

printf '%s\n' \
  "Central Brain Android debug simulation controller check passed" \
  "debug_simulation_controller_defined=true" \
  "debug_simulation_controller_aidl_version=1" \
  "debug_simulation_controller_signature_permission_enforced=true" \
  "debug_simulation_controller_capability_enforced=true" \
  "debug_simulation_controller_debug_only=true" \
  "debug_simulation_controller_release_source_absent=true" \
  "debug_simulation_controller_production_exported=false" \
  "debug_simulation_controller_runtime_wired=false" \
  "vehicle_signal_provider_wired=false" \
  "hardware_accessed=false"
