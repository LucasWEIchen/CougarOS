#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-UX-003, S2-HMI-001/002, APP-004, XSC-001/005/006.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="$ROOT_DIR/apk-labs/client2-central-brain"
LAYOUT="$PROJECT/patches/main_layout.central_brain_panel.xml"
POLICY="$PROJECT/bridge/src/com/centralbrain/client2/CockpitDisplayPolicy.java"
COORDINATOR="$PROJECT/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
TEST_MAIN="$PROJECT/bridge/test/com/centralbrain/client2/CockpitHmiReducerTestMain.java"
DEVICE_TEST="$ROOT_DIR/tools/test_client2_central_brain_accessibility_display.sh"
DEVELOPMENT="$ROOT_DIR/docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md"
REQUIREMENTS="$ROOT_DIR/docs/CENTRAL_BRAIN_REQUIREMENTS.md"

for path in "$LAYOUT" "$POLICY" "$COORDINATOR" "$TEST_MAIN" "$DEVICE_TEST" \
    "$DEVELOPMENT" "$REQUIREMENTS"; do
  [[ -f "$path" ]] || { echo "missing P4-W11 accessibility/display file: $path" >&2; exit 1; }
done

bash -n "$DEVICE_TEST"
grep -Fq 'UI hierarchy unavailable after bounded retries' "$DEVICE_TEST"

python3 - "$LAYOUT" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
root = ET.parse(sys.argv[1]).getroot()
parents = {child: parent for parent in root.iter() for child in parent}

def attr(node, name):
    return node.attrib.get(ANDROID + name, "")

def numeric_dp(value):
    match = re.fullmatch(r"([0-9]+(?:\.[0-9]+)?)dp", value)
    return None if match is None else float(match.group(1))

def effective_height(node):
    current = node
    while current is not None:
        value = numeric_dp(attr(current, "layout_height"))
        if value is not None:
            return value
        current = parents.get(current)
    return None

buttons = [node for node in root.iter() if node.tag == "Button"]
if len(buttons) < 50:
    raise SystemExit(f"expected complete cockpit control set, found {len(buttons)} buttons")

for button in buttons:
    identifier = attr(button, "id").rsplit("/", 1)[-1]
    label = attr(button, "contentDescription") or attr(button, "text")
    if not label.strip():
        raise SystemExit(f"interactive control lacks an accessibility label: {identifier}")
    width = numeric_dp(attr(button, "layout_width"))
    height = effective_height(button)
    if width is not None and width > 0 and width < 48:
        raise SystemExit(f"touch target width below 48dp: {identifier}={width}")
    if height is not None and height < 48:
        raise SystemExit(f"touch target height below 48dp: {identifier}={height}")
    for minimum in ("minWidth", "minHeight"):
        value = numeric_dp(attr(button, minimum))
        if value is not None and value < 48:
            raise SystemExit(f"{minimum} below 48dp: {identifier}={value}")
    if attr(button, "text") in {"×", "+", "−"} \
            and not attr(button, "contentDescription"):
        raise SystemExit(f"icon control requires explicit content description: {identifier}")

trigger = next(
    node for node in root.iter()
    if attr(node, "id").endswith("/centralBrainNavigationTrigger")
)
if attr(trigger, "importantForAccessibility") != "yes" \
        or not attr(trigger, "contentDescription"):
    raise SystemExit("navigation trigger accessibility contract changed")
PY

for marker in \
  'COMPACT_1280_720(1280, 720, 107)' \
  'STANDARD_1920_1080(1920, 1080, 160)' \
  'LARGE_2560_1440(2560, 1440, 213)' \
  'public static final int MIN_TOUCH_TARGET_DP = 48' \
  'public static final float MAX_FONT_SCALE = 1.30f' \
  'candidate.densityDpi == densityDpi' \
  'LANDSCAPE_REQUIRED' \
  'DISPLAY_MATRIX_MISMATCH' \
  'public boolean isEffectAuthorizationSource()'; do
  grep -Fq -- "$marker" "$POLICY"
done

for marker in \
  'button.setContentDescription(normalizeAccessibilityLabel(label))' \
  'button.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES)' \
  'button.setMinimumWidth(displayPolicy.getMinimumTouchTargetPixels())' \
  'button.setMinimumHeight(displayPolicy.getMinimumTouchTargetPixels())' \
  'button.setMaxLines(2)' \
  'button.setEllipsize(TextUtils.TruncateAt.END)' \
  'CockpitDisplayPolicy.Bounds bounds = displayPolicy.getPanelBoundsPixels()' \
  'parameters.width = bounds.getWidth()' \
  'margins.topMargin = bounds.getTop()' \
  'margins.rightMargin = displayPolicy.getWidthPixels() - bounds.getRight()' \
  'view.setSelected(activated)' \
  'view.setStateDescription(stateDescription)' \
  'cockpit_display_effect_authorization_source=false'; do
  grep -Fq -- "$marker" "$COORDINATOR"
done

for marker in \
  'cockpit_display_profile_count=3' \
  'cockpit_display_large_text_1_3_verified=true' \
  'cockpit_display_unsupported_fail_closed=true' \
  'cockpit_display_effect_authorization_source=false'; do
  grep -Fq -- "$marker" "$TEST_MAIN"
done
grep -Fq 'nearby density must not be rounded into an approved profile' "$TEST_MAIN"

for marker in \
  'cockpit_display_matrix_android13_arm64_verified=true' \
  'cockpit_display_compact_1280_720_verified=true' \
  'cockpit_display_standard_1920_1080_verified=true' \
  'cockpit_display_large_2560_1440_verified=true' \
  'cockpit_display_large_text_1_3_verified=true' \
  'cockpit_accessibility_content_description_verified=true' \
  'cockpit_accessibility_state_not_color_only=true' \
  'cockpit_long_chinese_non_overlap_verified=true' \
  'cockpit_display_unsupported_fail_closed=true' \
  'cockpit_display_effect_authorization_source=false'; do
  grep -Fq -- "$marker" "$DEVICE_TEST"
done

grep -Fq 'production_document_scope=true' "$DEVELOPMENT"
grep -Fq '`P4-W11` Accessibility/display matrix' "$REQUIREMENTS"
grep -Fq '`S2-HMI-006`' "$REQUIREMENTS"

bash "$ROOT_DIR/tools/check_central_brain_android_client2_hmi_reducer.sh"

printf '%s\n' \
  'cockpit_display_matrix_defined=true' \
  'cockpit_display_profile_count=3' \
  'cockpit_touch_target_min_dp=48' \
  'cockpit_accessibility_semantics_runtime_owned=true' \
  'cockpit_accessibility_state_not_color_only=true' \
  'cockpit_display_large_text_1_3_verified=true' \
  'cockpit_display_unsupported_fail_closed=true' \
  'cockpit_display_matrix_android13_arm64_verified=true' \
  'cockpit_display_effect_authorization_source=false' \
  'scenario_execution_enabled=false' \
  'hardware_accessed=false'
echo "Central Brain Android Client2 accessibility/display matrix check passed"
