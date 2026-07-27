#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-UX-002, S2-HMI-002, S2-SAF-001, APP-004, XSC-001/005/006.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="$ROOT_DIR/apk-labs/client2-central-brain"
LAYOUT="$PROJECT/patches/main_layout.central_brain_panel.xml"
MODE="$PROJECT/bridge/src/com/centralbrain/client2/PanelPresentationMode.java"
POLICY="$PROJECT/bridge/src/com/centralbrain/client2/DrivingUxPolicy.java"
STATE="$PROJECT/bridge/src/com/centralbrain/client2/CockpitHmiState.java"
REDUCER="$PROJECT/bridge/src/com/centralbrain/client2/CockpitHmiReducer.java"
COORDINATOR="$PROJECT/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
DEVICE_TEST="$ROOT_DIR/tools/test_client2_central_brain_binder.sh"

for path in "$LAYOUT" "$MODE" "$POLICY" "$STATE" "$REDUCER" "$COORDINATOR" "$DEVICE_TEST"; do
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
        raise SystemExit(f"expected one driving UX view {identifier}, found {len(matches)}")
    return matches[0]

restriction = node_by_id("centralBrainRestrictionText")
if "按行驶态限制" not in attr(restriction, "text"):
    raise SystemExit("driving restriction banner lacks fail-closed default")

for identifier in (
    "centralBrainNapButton",
    "centralBrainHvacTemperatureUpButton",
    "centralBrainSeatHeatUpButton",
    "centralBrainSeatReclineUpButton",
    "centralBrainSeatRestPresetButton",
    "centralBrainIntentChainText",
    "centralBrainExecutionChainText",
    "centralBrainResultEvidenceText",
):
    node_by_id(identifier)
PY

for marker in \
  'PARKED_FULL(true, true, true)' \
  'MOVING_RESTRICTED(false, false, false)' \
  'public boolean isLongTextVisible()' \
  'public boolean isParameterEditingEnabled()' \
  'public boolean isHighRiskScenarioEnabled()' \
  'public boolean isEffectAuthorizationSource()'; do
  grep -Fq -- "$marker" "$MODE"
done
for marker in \
  'public static PanelPresentationMode modeFor(' \
  'EvidenceSource.UNAVAILABLE' \
  'EvidenceQuality.OBSERVED' \
  'PanelPresentationMode.MOVING_RESTRICTED' \
  'PanelPresentationMode.PARKED_FULL' \
  '"skill.nap".equals(scenarioId)'; do
  grep -Fq -- "$marker" "$POLICY"
done
for marker in \
  'private final PanelPresentationMode presentationMode' \
  'public PanelPresentationMode getPresentationMode()' \
  'PanelPresentationMode presentationMode = PanelPresentationMode.MOVING_RESTRICTED'; do
  grep -Fq -- "$marker" "$STATE"
done
for marker in \
  'next.presentationMode = DrivingUxPolicy.modeFor(event.seatSafetyContext)' \
  'next.presentationMode = PanelPresentationMode.MOVING_RESTRICTED'; do
  grep -Fq -- "$marker" "$REDUCER"
done
for marker in \
  'renderPresentation(current, presentationMode)' \
  'setVisible(executionChainView, showLongText)' \
  'setButtonsEnabled(hvacSurface, parameterEditingEnabled)' \
  'setButtonsEnabled(seatSurface, parameterEditingEnabled)' \
  'setEnabled(napButton, presentationMode.isHighRiskScenarioEnabled())' \
  'cockpit_runtime_policy_authority_independent=true'; do
  grep -Fq -- "$marker" "$COORDINATOR"
done

if grep -Eq '^import android\.' "$MODE" "$POLICY" "$STATE" "$REDUCER"; then
  echo "Client2 driving UX policy must remain Android-view independent" >&2
  exit 1
fi
if grep -Eiq 'CarPropertyManager|android\.car|System\.loadLibrary|ioctl|sysfs|/dev/' \
    "$MODE" "$POLICY" "$STATE" "$REDUCER" "$COORDINATOR"; then
  echo "Client2 driving UX bypasses the typed Context/presentation boundary" >&2
  exit 1
fi

for marker in \
  'cockpit_driving_ux_policy_verified=true' \
  'cockpit_unknown_driving_restricted_verified=true' \
  'cockpit_restricted_long_text_hidden_verified=true' \
  'cockpit_restricted_parameter_editing_disabled_verified=true' \
  'cockpit_high_risk_controls_disabled_verified=true' \
  'cockpit_runtime_policy_authority_independent=true'; do
  grep -Fq -- "$marker" "$DEVICE_TEST"
done

for doc_marker in \
  'README.md|P4 Client2 driving restriction renderer' \
  'docs/CENTRAL_BRAIN_REQUIREMENTS.md|P4-W08` Driving restriction renderer' \
  'docs/CENTRAL_BRAIN_REQUIREMENTS.md|P4-W08 driving restriction renderer trace' \
  'docs/CENTRAL_BRAIN_REQUIREMENTS.md|DEV-058 P4-W08 driving presentation' \
  'docs/CENTRAL_BRAIN_REQUIREMENTS.md|P4-W08 进展：Client2 driving restriction renderer' \
  'docs/CENTRAL_BRAIN_REQUIREMENTS.md|P4-W08 Driving Restriction Renderer' \
  'docs/CENTRAL_BRAIN_REQUIREMENTS.md|P4-W08 Driving restriction renderer Driver/HAL Boundary' \
  'docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md|Client2 P4-W08 Driving Restriction Interfaces' \
  'docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md|R7C-E-011' \
  'docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md|P4-W08 driving restriction renderer evidence'; do
  path="${doc_marker%%|*}"
  marker="${doc_marker#*|}"
  grep -Fq -- "$marker" "$ROOT_DIR/$path"
done

bash "$ROOT_DIR/tools/check_central_brain_android_client2_hmi_reducer.sh"

printf '%s\n' \
  'cockpit_driving_ux_policy_implemented=true' \
  'cockpit_unknown_driving_restricted=true' \
  'cockpit_moving_long_text_hidden=true' \
  'cockpit_restricted_parameter_editing_disabled=true' \
  'cockpit_high_risk_controls_disabled=true' \
  'cockpit_runtime_policy_authority_independent=true' \
  'vehicle_signal_provider_wired=false' \
  'hardware_accessed=false'
echo "Central Brain Android Client2 driving restriction renderer check passed"
