#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-HMI-001/003/004/005, S2-ADP-001, APP-004, XSC-001/005/006.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="$ROOT_DIR/apk-labs/client2-central-brain"
LAYOUT="$PROJECT/patches/main_layout.central_brain_panel.xml"
INTENT="$PROJECT/bridge/src/com/centralbrain/client2/HvacControlIntent.java"
HVAC_STATE="$PROJECT/bridge/src/com/centralbrain/client2/CockpitHvacState.java"
HMI_STATE="$PROJECT/bridge/src/com/centralbrain/client2/CockpitHmiState.java"
REDUCER="$PROJECT/bridge/src/com/centralbrain/client2/CockpitHmiReducer.java"
COORDINATOR="$PROJECT/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
BRIDGE="$PROJECT/bridge/src/com/centralbrain/client2/Client2ScenarioBridge.java"
DEVICE_TEST="$ROOT_DIR/tools/test_client2_central_brain_binder.sh"

for path in \
  "$LAYOUT" "$INTENT" "$HVAC_STATE" "$HMI_STATE" "$REDUCER" \
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
        raise SystemExit(f"expected one HVAC view {identifier}, found {len(matches)}")
    return matches[0]

required_ids = {
    "centralBrainHvacSurface",
    "centralBrainHvacDesiredText",
    "centralBrainHvacPowerButton",
    "centralBrainHvacZoneDriverButton",
    "centralBrainHvacZonePassengerButton",
    "centralBrainHvacZoneCabinButton",
    "centralBrainHvacTemperatureDownButton",
    "centralBrainHvacTemperatureText",
    "centralBrainHvacTemperatureUpButton",
    "centralBrainHvacFanDownButton",
    "centralBrainHvacFanText",
    "centralBrainHvacFanUpButton",
    "centralBrainHvacAutoButton",
    "centralBrainHvacAcButton",
    "centralBrainHvacSyncButton",
    "centralBrainHvacAirflowButton",
    "centralBrainHvacWarmPresetButton",
    "centralBrainHvacCoolPresetButton",
    "centralBrainHvacClearPresetButton",
    "centralBrainHvacModesText",
    "centralBrainHvacEvidenceText",
    "centralBrainHvacRequestText",
    "centralBrainSeatSurface",
}
for identifier in required_ids:
    node_by_id(identifier)

expected_tags = {
    "centralBrainHvacPowerButton": "central_brain_hvac_power",
    "centralBrainHvacZoneDriverButton": "central_brain_hvac_zone_driver",
    "centralBrainHvacZonePassengerButton": "central_brain_hvac_zone_passenger",
    "centralBrainHvacZoneCabinButton": "central_brain_hvac_zone_cabin",
    "centralBrainHvacTemperatureDownButton": "central_brain_hvac_temp_down",
    "centralBrainHvacTemperatureUpButton": "central_brain_hvac_temp_up",
    "centralBrainHvacFanDownButton": "central_brain_hvac_fan_down",
    "centralBrainHvacFanUpButton": "central_brain_hvac_fan_up",
    "centralBrainHvacAutoButton": "central_brain_hvac_auto",
    "centralBrainHvacAcButton": "central_brain_hvac_ac",
    "centralBrainHvacSyncButton": "central_brain_hvac_sync",
    "centralBrainHvacAirflowButton": "central_brain_hvac_airflow",
    "centralBrainHvacWarmPresetButton": "central_brain_hvac_preset_warm",
    "centralBrainHvacCoolPresetButton": "central_brain_hvac_preset_cool",
    "centralBrainHvacClearPresetButton": "central_brain_hvac_preset_clear",
}
for identifier, tag in expected_tags.items():
    if attr(node_by_id(identifier), "tag") != tag:
        raise SystemExit(f"{identifier} must use stable tag {tag}")

surface = node_by_id("centralBrainHvacSurface")
if surface.tag != "ScrollView" or attr(surface, "layout_height") != "0.0dp":
    raise SystemExit("HVAC surface must scroll inside the fixed 1920x1080 drawer")
PY

for marker in \
  'public static final int MIN_TEMP_DECI_C = 160' \
  'public static final int MAX_TEMP_DECI_C = 300' \
  'public static final int TEMP_STEP_DECI_C = 5' \
  'public static final int MAX_FAN_LEVEL = 7' \
  'public String toWireValue()' \
  'public static HvacControlIntent parseWireValue(String value)' \
  'non-canonical HVAC wire value'; do
  grep -Fq -- "$marker" "$INTENT"
done

for marker in \
  'public enum RequestState { IDLE, DIRTY, DEBOUNCING, SUBMITTING, ACCEPTED, FAILED }' \
  'public enum EvidenceSource { UNAVAILABLE, SIMULATED, TARGET }' \
  'public enum EvidenceQuality { NO_EVIDENCE, STALE, OBSERVED, VERIFIED }' \
  'public enum EffectState { NOT_DISPATCHED, REQUESTED, DISPATCHED, APPLIED, VERIFIED, FAILED }' \
  'HVAC evidence cannot exist without readback'; do
  grep -Fq -- "$marker" "$HVAC_STATE"
done

for marker in \
  'case HVAC_DESIRED_CHANGED:' \
  'case HVAC_MANUAL_SUBMITTED:' \
  'next.deviceDrawer = CockpitHmiState.DeviceDrawer.HVAC' \
  'next.uiScenarioId = "manual.hvac"'; do
  grep -Fq -- "$marker" "$REDUCER"
done

for marker in \
  'private static final long HVAC_DEBOUNCE_MS = 300L' \
  'mainHandler.postDelayed(submitHvacRunnable, HVAC_DEBOUNCE_MS)' \
  'Client2ScenarioBridge.openHvacSession(activity, intent, this)' \
  'cockpit_hvac_surface_implemented=true' \
  'cockpit_hvac_reducer_owned=true' \
  'cockpit_hvac_debounce_ms=300' \
  'cockpit_hvac_governed_manual_session=true' \
  'cockpit_hvac_reported_readback_available=false' \
  'production_effect_dispatch_enabled=false'; do
  grep -Fq -- "$marker" "$COORDINATOR"
done

for marker in \
  'SessionConnection openHvacSession(' \
  'intent.toWireValue()' \
  'aliases.put("manual.hvac", "scene.manual.hvac.adjust.v1")' \
  'hvac_manual_intent_governed_session=' \
  'hvac_manual_bounded_parameter_wire=' \
  'hvac_manual_typed_parameter_field=false'; do
  grep -Fq -- "$marker" "$BRIDGE"
done

if grep -Eq '^import android\.' "$INTENT" "$HVAC_STATE"; then
  echo "HVAC target/state must remain Android-view independent" >&2
  exit 1
fi
if grep -R -Eiq \
    'SimulatedHvacEffectAdapter|CarPropertyManager|android\.car|System\.loadLibrary|ioctl|sysfs|/dev/' \
    "$PROJECT/bridge/src"; then
  echo "Client2 HVAC surface bypasses the governed Session/Adapter boundary" >&2
  exit 1
fi

for marker in \
  'cockpit_hvac_surface_implemented=true' \
  'cockpit_hvac_controls_verified=true' \
  'cockpit_hvac_debounce_verified=true' \
  'cockpit_hvac_manual_session_admission_verified=true' \
  'cockpit_hvac_desired_reported_separation_verified=true' \
  'cockpit_hvac_reported_readback_available=false' \
  'cockpit_hvac_verified_before_readback=false' \
  'hvac_manual_typed_parameter_field=false' \
  'service_dispatch_triggered=false' \
  'hardware_accessed=false'; do
  grep -Fq -- "$marker" "$DEVICE_TEST"
done

for doc_marker in \
  'README.md|P4 Client2 HVAC control surface' \
  'docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md|P4-W04' \
  'docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md|P4-W04 HVAC control surface trace' \
  'docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md|DEV-054' \
  'docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md|P4-W04 进展' \
  'docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md|P4-W04 HVAC Control Surface' \
  'docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md|P4-W04 HVAC control surface Driver/HAL Boundary'; do
  path="${doc_marker%%|*}"
  marker="${doc_marker#*|}"
  grep -Fq -- "$marker" "$ROOT_DIR/$path"
done

bash "$ROOT_DIR/tools/check_central_brain_android_client2_hmi_reducer.sh"

printf '%s\n' \
  'cockpit_hvac_surface_implemented=true' \
  'cockpit_hvac_reducer_owned=true' \
  'cockpit_hvac_debounce_ms=300' \
  'cockpit_hvac_governed_manual_session=true' \
  'cockpit_hvac_desired_reported_separation_verified=true' \
  'cockpit_hvac_reported_readback_available=false' \
  'cockpit_hvac_verified_before_readback=false' \
  'hvac_manual_typed_parameter_field=false' \
  'production_effect_dispatch_enabled=false' \
  'hardware_accessed=false'
echo "Central Brain Android Client2 HVAC control surface check passed"
