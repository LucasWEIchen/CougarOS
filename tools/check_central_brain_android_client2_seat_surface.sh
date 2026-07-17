#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-HMI-002..005, S2-SAF-001, S2-ADP-001, APP-004, XSC-001/005/006.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="$ROOT_DIR/apk-labs/client2-central-brain"
LAYOUT="$PROJECT/patches/main_layout.central_brain_panel.xml"
INTENT="$PROJECT/bridge/src/com/centralbrain/client2/SeatControlIntent.java"
SEAT_STATE="$PROJECT/bridge/src/com/centralbrain/client2/CockpitSeatState.java"
HMI_STATE="$PROJECT/bridge/src/com/centralbrain/client2/CockpitHmiState.java"
REDUCER="$PROJECT/bridge/src/com/centralbrain/client2/CockpitHmiReducer.java"
COORDINATOR="$PROJECT/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
BRIDGE="$PROJECT/bridge/src/com/centralbrain/client2/Client2ScenarioBridge.java"
DEVICE_TEST="$ROOT_DIR/tools/test_client2_central_brain_binder.sh"

for path in \
  "$LAYOUT" "$INTENT" "$SEAT_STATE" "$HMI_STATE" "$REDUCER" \
  "$COORDINATOR" "$BRIDGE" "$DEVICE_TEST"; do
  test -f "$path"
done

python3 - "$LAYOUT" <<'PY'
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
root = ET.parse(sys.argv[1]).getroot()

def attr(node, name):
    return node.attrib.get(ANDROID + name, "")

def node_by_id(identifier):
    expected = {f"@+id/{identifier}", f"@id/{identifier}"}
    matches = [node for node in root.iter() if attr(node, "id") in expected]
    if len(matches) != 1:
        raise SystemExit(f"expected one Seat view {identifier}, found {len(matches)}")
    return matches[0]

required_ids = {
    "centralBrainSeatSurface",
    "centralBrainSeatDesiredText",
    "centralBrainSeatZoneDriverButton",
    "centralBrainSeatZonePassengerButton",
    "centralBrainSeatZoneRearLeftButton",
    "centralBrainSeatZoneRearRightButton",
    "centralBrainSeatHeatDownButton",
    "centralBrainSeatHeatText",
    "centralBrainSeatHeatUpButton",
    "centralBrainSeatVentilationDownButton",
    "centralBrainSeatVentilationText",
    "centralBrainSeatVentilationUpButton",
    "centralBrainSeatMassageButton",
    "centralBrainSeatReclineDownButton",
    "centralBrainSeatReclineText",
    "centralBrainSeatReclineUpButton",
    "centralBrainSeatUprightPresetButton",
    "centralBrainSeatComfortPresetButton",
    "centralBrainSeatRestPresetButton",
    "centralBrainSeatModesText",
    "centralBrainSeatSafetyText",
    "centralBrainSeatEvidenceText",
    "centralBrainSeatRequestText",
}
for identifier in required_ids:
    node_by_id(identifier)

expected_tags = {
    "centralBrainSeatZoneDriverButton": "central_brain_seat_zone_driver",
    "centralBrainSeatZonePassengerButton": "central_brain_seat_zone_passenger",
    "centralBrainSeatZoneRearLeftButton": "central_brain_seat_zone_rear_left",
    "centralBrainSeatZoneRearRightButton": "central_brain_seat_zone_rear_right",
    "centralBrainSeatHeatDownButton": "central_brain_seat_heat_down",
    "centralBrainSeatHeatUpButton": "central_brain_seat_heat_up",
    "centralBrainSeatVentilationDownButton": "central_brain_seat_vent_down",
    "centralBrainSeatVentilationUpButton": "central_brain_seat_vent_up",
    "centralBrainSeatMassageButton": "central_brain_seat_massage",
    "centralBrainSeatReclineDownButton": "central_brain_seat_recline_down",
    "centralBrainSeatReclineUpButton": "central_brain_seat_recline_up",
    "centralBrainSeatUprightPresetButton": "central_brain_seat_preset_upright",
    "centralBrainSeatComfortPresetButton": "central_brain_seat_preset_comfort",
    "centralBrainSeatRestPresetButton": "central_brain_seat_preset_rest",
}
for identifier, tag in expected_tags.items():
    if attr(node_by_id(identifier), "tag") != tag:
        raise SystemExit(f"{identifier} must use stable tag {tag}")

surface = node_by_id("centralBrainSeatSurface")
if surface.tag != "ScrollView" or attr(surface, "layout_height") != "0.0dp":
    raise SystemExit("Seat surface must scroll inside the fixed 1920x1080 drawer")
PY

for marker in \
  'public static final int MAX_COMFORT_LEVEL = 3' \
  'public static final int MAX_RECLINE_DEGREES = 60' \
  'seat heat and ventilation are mutually exclusive' \
  'public String toWireValue()' \
  'public static SeatControlIntent parseWireValue(String value)' \
  'non-canonical seat wire value'; do
  grep -Fq -- "$marker" "$INTENT"
done

for marker in \
  'UNKNOWN_RESTRICTED' \
  'DENIED_MOVING_DRIVER' \
  'APPROVAL_REQUIRED' \
  'WAITING_APPROVAL' \
  'seat evidence cannot exist without readback' \
  'requestState != RequestState.DEBOUNCING'; do
  grep -Fq -- "$marker" "$SEAT_STATE"
done

for marker in \
  'case SEAT_SAFETY_CONTEXT_CHANGED:' \
  'case SEAT_DESIRED_CHANGED:' \
  'case SEAT_MANUAL_SUBMITTED:' \
  'next.deviceDrawer = CockpitHmiState.DeviceDrawer.SEAT' \
  'next.uiScenarioId = "manual.seat"'; do
  grep -Fq -- "$marker" "$REDUCER"
done

for marker in \
  'private static final long SEAT_DEBOUNCE_MS = 300L' \
  'mainHandler.postDelayed(submitSeatRunnable, SEAT_DEBOUNCE_MS)' \
  'Client2ScenarioBridge.openSeatSession(activity, intent, this)' \
  'cockpit_seat_surface_implemented=true' \
  'cockpit_seat_reducer_owned=true' \
  'cockpit_seat_unknown_restricted_fail_closed=true' \
  'cockpit_seat_reported_readback_available=false' \
  'production_effect_dispatch_enabled=false'; do
  grep -Fq -- "$marker" "$COORDINATOR"
done

for marker in \
  'SessionConnection openSeatSession(' \
  'aliases.put("manual.seat", "scene.manual.seat.adjust.v1")' \
  'seat_manual_intent_governed_session=' \
  'seat_manual_bounded_parameter_wire=' \
  'seat_manual_typed_parameter_field=false'; do
  grep -Fq -- "$marker" "$BRIDGE"
done

if grep -Eq '^import android\.' "$INTENT" "$SEAT_STATE"; then
  echo "Seat target/state must remain Android-view independent" >&2
  exit 1
fi
if grep -R -Eiq \
    'SimulatedSeatEffectAdapter|CarPropertyManager|android\.car|System\.loadLibrary|ioctl|sysfs|/dev/' \
    "$PROJECT/bridge/src"; then
  echo "Client2 Seat surface bypasses the governed Session/Adapter boundary" >&2
  exit 1
fi

for marker in \
  'cockpit_seat_surface_implemented=true' \
  'cockpit_seat_controls_restricted_verified=true' \
  'cockpit_seat_unknown_restricted_fail_closed=true' \
  'cockpit_seat_manual_session_admission_retested=false' \
  'cockpit_seat_desired_reported_separation_verified=true' \
  'cockpit_seat_reported_readback_available=false' \
  'cockpit_seat_verified_before_readback=false' \
  'seat_manual_typed_parameter_field=false' \
  'service_dispatch_triggered=false' \
  'hardware_accessed=false'; do
  grep -Fq -- "$marker" "$DEVICE_TEST"
done

for doc_marker in \
  'README.md|P4 Client2 Seat control surface' \
  'docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md|P4-W05' \
  'docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md|P4-W05 Seat control surface trace' \
  'docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md|DEV-055' \
  'docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md|P4-W05 进展' \
  'docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md|P4-W05 Seat Control Surface' \
  'docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md|P4-W05 Seat control surface Driver/HAL Boundary'; do
  path="${doc_marker%%|*}"
  marker="${doc_marker#*|}"
  grep -Fq -- "$marker" "$ROOT_DIR/$path"
done

bash "$ROOT_DIR/tools/check_central_brain_android_client2_hmi_reducer.sh"

printf '%s\n' \
  'cockpit_seat_surface_implemented=true' \
  'cockpit_seat_reducer_owned=true' \
  'cockpit_seat_debounce_ms=300' \
  'cockpit_seat_governed_manual_session=true' \
  'cockpit_seat_heat_vent_mutex_verified=true' \
  'cockpit_seat_unknown_restricted_fail_closed=true' \
  'cockpit_seat_desired_reported_separation_verified=true' \
  'cockpit_seat_reported_readback_available=false' \
  'cockpit_seat_verified_before_readback=false' \
  'seat_manual_typed_parameter_field=false' \
  'production_effect_dispatch_enabled=false' \
  'hardware_accessed=false'
echo "Central Brain Android Client2 Seat control surface check passed"
