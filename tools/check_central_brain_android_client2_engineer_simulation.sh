#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-HMI-004, S2-ADP-001, S2-OBS-001, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="$ROOT_DIR/apk-labs/client2-central-brain"
STATE="$PROJECT/bridge/src/com/centralbrain/client2/CockpitEngineerState.java"
CLIENT="$PROJECT/bridge/src/com/centralbrain/client2/DebugSimulationControllerClient.java"
HMI_STATE="$PROJECT/bridge/src/com/centralbrain/client2/CockpitHmiState.java"
REDUCER="$PROJECT/bridge/src/com/centralbrain/client2/CockpitHmiReducer.java"
COORDINATOR="$PROJECT/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
LAYOUT="$PROJECT/patches/main_layout.central_brain_panel.xml"
PATCHER="$PROJECT/scripts/apply_static_panel_patch.py"
DEX_BUILD="$PROJECT/scripts/build_binder_bridge_dex.sh"
AIDL="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/debug/aidl/com/centralbrain/runtime/simulation/IDebugSimulationController.aidl"
SERVICE="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/simulation/DebugSimulationControllerService.java"
DEBUG_POLICY="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/debug/res/xml/central_brain_capability_policy.xml"
MAIN_POLICY="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/res/xml/central_brain_capability_policy.xml"
MAIN_MANIFEST="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
DEVICE_TEST="$ROOT_DIR/tools/test_client2_central_brain_engineer_simulation.sh"

for path in "$STATE" "$CLIENT" "$HMI_STATE" "$REDUCER" "$COORDINATOR" "$LAYOUT" \
    "$PATCHER" "$DEX_BUILD" "$AIDL" "$SERVICE" "$DEBUG_POLICY" "$MAIN_POLICY" \
    "$MAIN_MANIFEST" "$DEVICE_TEST"; do
  test -f "$path"
done

python3 - "$LAYOUT" <<'PY'
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
root = ET.parse(sys.argv[1]).getroot()

def attr(node, name):
    return node.attrib.get(ANDROID + name, "")

def one(identifier):
    expected = {f"@+id/{identifier}", f"@id/{identifier}"}
    matches = [node for node in root.iter() if attr(node, "id") in expected]
    if len(matches) != 1:
        raise SystemExit(f"expected one engineer view {identifier}, found {len(matches)}")
    return matches[0]

entry = one("centralBrainEngineerDetailButton")
if attr(entry, "visibility") != "gone":
    raise SystemExit("engineer entry must be hidden until protected Binder connection")

for identifier in (
    "centralBrainEngineerSurface",
    "centralBrainEngineerStatusText",
    "centralBrainEngineerDrivingUnknownButton",
    "centralBrainEngineerDrivingParkedButton",
    "centralBrainEngineerDrivingMovingButton",
    "centralBrainEngineerOccupancyEmptyButton",
    "centralBrainEngineerOccupancyOccupiedButton",
    "centralBrainEngineerBeltBeltedButton",
    "centralBrainEngineerBeltUnbeltedButton",
    "centralBrainEngineerAdapterHvacButton",
    "centralBrainEngineerAdapterSeatButton",
    "centralBrainEngineerFaultNoneButton",
    "centralBrainEngineerFaultDelayButton",
    "centralBrainEngineerFaultTimeoutButton",
    "centralBrainEngineerFaultFailureButton",
    "centralBrainEngineerFaultTerminalButton",
    "centralBrainEngineerFaultMismatchButton",
    "centralBrainEngineerContextText",
    "centralBrainEngineerFaultText",
    "centralBrainEngineerResetButton",
):
    one(identifier)
PY

for marker in \
  'public final class CockpitEngineerState' \
  'ConnectionState { UNAVAILABLE, CONNECTING, CONNECTED, FAILED }' \
  'public boolean isProductionAvailable()' \
  'public boolean isEffectAuthorizationSource()' \
  'CockpitSeatState.EvidenceSource.SIMULATED' \
  'debug.simulated.hvac.v1' \
  'debug.simulated.seat.v1' \
  'engineer command revision must increase'; do
  grep -Fq -- "$marker" "$STATE"
done
for marker in \
  'public final class DebugSimulationControllerClient' \
  'IDebugSimulationController.Stub.asInterface' \
  'INTERFACE_VERSION' \
  'INTERFACE_HASH' \
  'Vehicle.Cabin.Seat.IsOccupied' \
  'Vehicle.Cabin.Seat.IsBelted' \
  'Executors.newSingleThreadExecutor()' \
  'Context.BIND_AUTO_CREATE'; do
  grep -Fq -- "$marker" "$CLIENT"
done
for marker in \
  'ENGINEER_CONNECTED' \
  'ENGINEER_DRIVING_APPLIED' \
  'ENGINEER_OCCUPANCY_APPLIED' \
  'ENGINEER_BELT_APPLIED' \
  'ENGINEER_FAULT_APPLIED' \
  'ENGINEER_RESET_APPLIED' \
  'finishEngineer('; do
  grep -Fq -- "$marker" "$REDUCER"
done
for marker in \
  'debugSimulationClient.connect()' \
  'current.getEngineerState().isAvailable()' \
  'cockpit_engineer_simulation_drawer_debug_only=true' \
  'cockpit_engineer_signature_permission_required=true' \
  'cockpit_engineer_capability_required=true' \
  'cockpit_engineer_effect_authorization_source=false' \
  'cockpit_engineer_production_available=false'; do
  grep -Fq -- "$marker" "$COORDINATOR"
done

grep -Fq 'com.centralbrain.permission.CONTROL_DEBUG_SIMULATION' "$PATCHER"
grep -Fq 'Expected exactly fifteen Client2 HMI/Session/debug-control Java sources' "$DEX_BUILD"
grep -Fq 'IDebugSimulationController.aidl' "$DEX_BUILD"
grep -Fq -- '--lang=java' "$DEX_BUILD"
grep -Fq 'android:permission="com.centralbrain.permission.CONTROL_DEBUG_SIMULATION"' \
  "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
grep -Fq 'Capability.SIMULATION_CONTROL' "$SERVICE"

python3 - "$DEBUG_POLICY" "$MAIN_POLICY" <<'PY'
import sys
import xml.etree.ElementTree as ET

debug_root = ET.parse(sys.argv[1]).getroot()
main_root = ET.parse(sys.argv[2]).getroot()

def capabilities(root, package):
    matches = [p for p in root.findall("principal") if p.attrib.get("packageName") == package]
    if len(matches) != 1:
        raise SystemExit(f"expected one principal for {package}")
    return {c.attrib.get("name") for c in matches[0].findall("capability")}

debug_caps = capabilities(debug_root, "com.tuanjie.urasclient2")
main_caps = capabilities(main_root, "com.tuanjie.urasclient2")
if "debug.simulation.control" not in debug_caps:
    raise SystemExit("Client2 debug principal lacks simulation capability")
if "debug.simulation.control" in main_caps:
    raise SystemExit("simulation capability leaked into production policy")
PY

if grep -Eq 'DebugSimulationController|CONTROL_DEBUG_SIMULATION|debug[.]simulation[.]control' \
    "$MAIN_MANIFEST" "$MAIN_POLICY"; then
  echo "engineer simulation surface leaked into production Runtime resources" >&2
  exit 1
fi
if grep -Eq '^import android\.' "$STATE" "$HMI_STATE" "$REDUCER"; then
  echo "engineer state/reducer must remain Android-view independent" >&2
  exit 1
fi
if grep -Eiq 'android[.]car|CarPropertyManager|VehicleHal|ioctl|sysfs|/dev/|java[.]net|okhttp|http://|https://' \
    "$STATE" "$CLIENT" "$COORDINATOR"; then
  echo "Client2 engineer drawer unexpectedly accesses vehicle, native, or network APIs" >&2
  exit 1
fi

for marker in \
  'cockpit_engineer_simulation_drawer_verified=true' \
  'cockpit_engineer_signature_permission_granted=true' \
  'cockpit_engineer_capability_allowed=true' \
  'cockpit_engineer_driving_state_matrix_verified=true' \
  'cockpit_engineer_occupancy_belt_verified=true' \
  'cockpit_engineer_fault_matrix_verified=true' \
  'cockpit_engineer_context_revision_monotonic_verified=true' \
  'cockpit_engineer_reset_fail_closed_verified=true' \
  'cockpit_engineer_runtime_release_service_absent=true' \
  'cockpit_engineer_effect_authorization_source=false' \
  'cockpit_engineer_production_available=false' \
  'hardware_accessed=false'; do
  grep -Fq -- "$marker" "$DEVICE_TEST"
done

for doc_marker in \
  'README.md|P4 Client2 engineer simulation drawer' \
  'docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md|P4-W09` Engineer simulation drawer' \
  'docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md|P4-W09 engineer simulation drawer trace' \
  'docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md|DEV-059 P4-W09 debug Context projection' \
  'docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md|P4-W09 进展：Client2 engineer simulation drawer' \
  'docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md|P4-W09 Engineer Simulation Drawer' \
  'docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md|P4-W09 Engineer simulation drawer Driver/HAL Boundary' \
  'docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md|Client2 P4-W09 Engineer Simulation Interfaces' \
  'docs/CENTRAL_BRAIN_ANDROID_R7C_APPLICATION_ACCEPTANCE.md|R7C-E-012' \
  'docs/CENTRAL_BRAIN_ANDROID13_PHYSICAL_TARGET_TEST_REPORT.md|P4-W09 engineer simulation drawer evidence'; do
  path="${doc_marker%%|*}"
  marker="${doc_marker#*|}"
  grep -Fq -- "$marker" "$ROOT_DIR/$path"
done

bash "$ROOT_DIR/tools/check_central_brain_android_client2_hmi_reducer.sh"

printf '%s\n' \
  'cockpit_engineer_simulation_drawer_implemented=true' \
  'cockpit_engineer_signature_permission_required=true' \
  'cockpit_engineer_capability_required=true' \
  'cockpit_engineer_context_revisioned=true' \
  'cockpit_engineer_runtime_release_service_absent=true' \
  'cockpit_engineer_effect_authorization_source=false' \
  'cockpit_engineer_production_available=false' \
  'vehicle_signal_provider_wired=false' \
  'hardware_accessed=false'
echo "Central Brain Android Client2 engineer simulation drawer check passed"
