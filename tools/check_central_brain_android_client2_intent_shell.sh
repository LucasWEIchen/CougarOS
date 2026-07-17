#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-UX-001..003, S2-HMI-001..003/006, APP-004, XSC-001/005/006.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="$ROOT_DIR/apk-labs/client2-central-brain"
LAYOUT="$PROJECT/patches/main_layout.central_brain_panel.xml"
PANEL_BACKGROUND="$PROJECT/patches/res/drawable/central_brain_panel_background.xml"
STAGE_TAB="$PROJECT/patches/res/drawable/central_brain_stage_tab.xml"
DRAWER_BACKGROUND="$PROJECT/patches/res/drawable/central_brain_drawer_background.xml"
STATE="$PROJECT/bridge/src/com/centralbrain/client2/CockpitHmiState.java"
REDUCER="$PROJECT/bridge/src/com/centralbrain/client2/CockpitHmiReducer.java"
COORDINATOR="$PROJECT/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
DEVICE_TEST="$ROOT_DIR/tools/test_client2_central_brain_binder.sh"

for path in \
  "$LAYOUT" "$PANEL_BACKGROUND" "$STAGE_TAB" "$DRAWER_BACKGROUND" \
  "$STATE" "$REDUCER" "$COORDINATOR" "$DEVICE_TEST"; do
  test -f "$path"
done

python3 - "$LAYOUT" "$PANEL_BACKGROUND" <<'PY'
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
layout = ET.parse(sys.argv[1]).getroot()

def attr(node, name):
    return node.attrib.get(ANDROID + name, "")

def node_by_id(identifier):
    expected = {f"@+id/{identifier}", f"@id/{identifier}"}
    found = [node for node in layout.iter() if attr(node, "id") in expected]
    if len(found) != 1:
        raise SystemExit(f"expected one {identifier}, found {len(found)}")
    return found[0]

panel = node_by_id("centralBrainPanel")
expected_geometry = {
    "layout_width": "624.0dp",
    "layout_height": "888.0dp",
    "layout_marginTop": "160.0dp",
    "layout_marginRight": "32.0dp",
    "layout_gravity": "top|right",
}
for name, value in expected_geometry.items():
    if attr(panel, name) != value:
        raise SystemExit(f"Client2 safe-frame {name} must be {value}")

required_ids = {
    "centralBrainHeader",
    "centralBrainSourceText",
    "centralBrainDrivingText",
    "centralBrainConnectionText",
    "centralBrainStageNavigation",
    "centralBrainIntentTab",
    "centralBrainPlanTab",
    "centralBrainExecutionTab",
    "centralBrainResultTab",
    "centralBrainIntentSurface",
    "centralBrainPlanSurface",
    "centralBrainExecutionSurface",
    "centralBrainResultSurface",
    "centralBrainSessionStrip",
    "centralBrainDeviceDrawer",
    "centralBrainHvacDetailButton",
    "centralBrainSeatDetailButton",
}
for identifier in required_ids:
    node_by_id(identifier)

expected_stage_tags = {
    "centralBrainIntentTab": "central_brain_stage_intent",
    "centralBrainPlanTab": "central_brain_stage_plan",
    "centralBrainExecutionTab": "central_brain_stage_execution",
    "centralBrainResultTab": "central_brain_stage_result",
}
for identifier, value in expected_stage_tags.items():
    if attr(node_by_id(identifier), "tag") != value:
        raise SystemExit(f"{identifier} must use stable stage tag {value}")

scenario_tags = {
    attr(node, "tag")
    for node in layout.iter()
    if attr(node, "tag") in {
        "care.cold", "care.fatigue", "task.home", "skill.nap",
        "state.vehicle", "memory.preference", "skills.catalog", "governance.audit",
        "security.denied", "security.privacy", "runtime.npu", "system.overview",
    }
}
if scenario_tags != {"care.cold", "care.fatigue", "task.home", "skill.nap"}:
    raise SystemExit(f"intent-first primary scenario set changed: {scenario_tags}")

background = ET.parse(sys.argv[2]).getroot()
solid = background.find("solid")
if solid is None or attr(solid, "color").upper() != "#99EEF2F3":
    raise SystemExit("Client2 main glass must use rgba(238,242,243,0.60)")
PY

for marker in \
  'public enum SurfaceStage { INTENT, PLAN, EXECUTION, RESULT }' \
  'public enum DeviceDrawer { CLOSED, HVAC, SEAT }'; do
  grep -Fq -- "$marker" "$STATE"
done
for marker in \
  'case SURFACE_SELECTED:' \
  'case DRAWER_SELECTED:' \
  'next.surfaceStage = CockpitHmiState.SurfaceStage.PLAN'; do
  grep -Fq -- "$marker" "$REDUCER"
done
for marker in \
  'cockpit_hmi_four_stage_shell_implemented=true' \
  'cockpit_hmi_intent_first_primary=true' \
  'cockpit_hmi_device_drawer_scaffolded=true' \
  'cockpit_hvac_surface_implemented=true' \
  'cockpit_seat_surface_implemented=true' \
  'cockpit_execution_timeline_implemented=true'; do
  grep -Fq -- "$marker" "$COORDINATOR"
done

if grep -Eq \
    'CentralBrainClient|AgentTaskRequest|submitAgentTask|android\.car|CarPropertyManager|System\.loadLibrary' \
    "$COORDINATOR"; then
  echo "Client2 four-stage shell bypasses the typed Session/Event or hardware boundary" >&2
  exit 1
fi

for marker in \
  'cockpit_hmi_four_stage_shell_verified=true' \
  'cockpit_hmi_safe_frame_1920x1080_verified=true' \
  'cockpit_hmi_device_drawer_verified=true' \
  'cockpit_hvac_surface_implemented=true' \
  'cockpit_seat_surface_implemented=true' \
  'scenario_execution_enabled=false' \
  'hardware_accessed=false'; do
  grep -Fq -- "$marker" "$DEVICE_TEST"
done

bash "$ROOT_DIR/tools/check_central_brain_android_client2_hmi_reducer.sh"

printf '%s\n' \
  'cockpit_hmi_four_stage_shell_implemented=true' \
  'cockpit_hmi_intent_first_primary=true' \
  'cockpit_hmi_safe_frame_1920x1080_verified=true' \
  'cockpit_hmi_material_alpha=0.60' \
  'cockpit_hmi_device_drawer_scaffolded=true' \
  'cockpit_hvac_surface_implemented=true' \
  'cockpit_seat_surface_implemented=true' \
  'scenario_execution_enabled=false' \
  'hardware_accessed=false'
echo "Central Brain Android Client2 intent-first four-stage shell check passed"
