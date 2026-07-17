#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-UX-003, S2-HMI-001/002, APP-004, XSC-001/005/006, DEL-003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${ANDROID_SERIAL:-}"
BUILD=true
REQUIRE_API_33=false
REPLACE_CONFLICTING_CLIENT2=false

while (($# > 0)); do
  case "$1" in
    --serial) SERIAL="$2"; shift 2 ;;
    --skip-build) BUILD=false; shift ;;
    --require-api-33) REQUIRE_API_33=true; shift ;;
    --replace-conflicting-client2) REPLACE_CONFLICTING_CLIENT2=true; shift ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/env.sh"
fi
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"
ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
RUNTIME_APK="$ROOT_DIR/central-brain/android-runtime/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
CLIENT2_APK="$ROOT_DIR/builds/client2-central-brain/signed/client2-central-brain.debug.apk"

adb_path() {
  if [[ "$ADB" == *.exe ]] && command -v wslpath >/dev/null; then
    wslpath -w "$1"
  else
    printf '%s\n' "$1"
  fi
}

if [[ "$BUILD" == true ]]; then
  bash "$ROOT_DIR/tools/build_client2_central_brain_demo.sh" >/dev/null
fi
for path in "$ADB" "$RUNTIME_APK" "$CLIENT2_APK"; do
  [[ -e "$path" ]] || { echo "missing accessibility/display test input: $path" >&2; exit 1; }
done

if [[ -z "$SERIAL" ]]; then
  mapfile -t DEVICES < <("$ADB" devices | tr -d '\r' | awk 'NR > 1 && $2 == "device" {print $1}')
  [[ ${#DEVICES[@]} -eq 1 ]] || { echo "expected exactly one adb device" >&2; exit 1; }
  SERIAL="${DEVICES[0]}"
fi
DEVICE=("$ADB" -s "$SERIAL")
SDK="$("${DEVICE[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
ABI="$("${DEVICE[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
[[ "$SDK" =~ ^[0-9]+$ ]] && ((SDK >= 33)) || { echo "Android API 33+ required" >&2; exit 1; }
[[ "$REQUIRE_API_33" != true || "$SDK" == 33 ]] || { echo "exact Android API 33 required" >&2; exit 1; }
[[ "$ABI" == arm64-v8a ]] || { echo "ARM64 target required" >&2; exit 1; }

"${DEVICE[@]}" install -r "$(adb_path "$RUNTIME_APK")" >/dev/null
set +e
INSTALL_OUTPUT="$("${DEVICE[@]}" install -r "$(adb_path "$CLIENT2_APK")" 2>&1)"
INSTALL_STATUS=$?
set -e
if ((INSTALL_STATUS != 0)); then
  if ! grep -Eq 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match' <<<"$INSTALL_OUTPUT"; then
    echo "$INSTALL_OUTPUT" >&2
    exit 1
  fi
  [[ "$REPLACE_CONFLICTING_CLIENT2" == true ]] \
    || { echo "SIGNER_MIGRATION_REQUIRED package=com.tuanjie.urasclient2" >&2; exit 1; }
  "${DEVICE[@]}" uninstall com.tuanjie.urasclient2 >/dev/null
  "${DEVICE[@]}" install "$(adb_path "$CLIENT2_APK")" >/dev/null
fi

STAMP="$(date +%Y%m%d_%H%M%S)"
LOG_DIR="$ROOT_DIR/logs/test/client2-central-brain-accessibility-display/$STAMP"
mkdir -p "$LOG_DIR"
DEVICE_UI_XML=/sdcard/client2-central-brain-accessibility-display.xml

SIZE_OUTPUT="$("${DEVICE[@]}" shell wm size | tr -d '\r')"
DENSITY_OUTPUT="$("${DEVICE[@]}" shell wm density | tr -d '\r')"
ORIGINAL_SIZE_OVERRIDE="$(sed -n 's/^Override size: //p' <<<"$SIZE_OUTPUT")"
ORIGINAL_DENSITY_OVERRIDE="$(sed -n 's/^Override density: //p' <<<"$DENSITY_OUTPUT")"
ORIGINAL_FONT_SCALE="$("${DEVICE[@]}" shell settings get system font_scale | tr -d '\r')"
ORIGINAL_ACCELEROMETER="$("${DEVICE[@]}" shell settings get system accelerometer_rotation | tr -d '\r')"
ORIGINAL_ROTATION="$("${DEVICE[@]}" shell settings get system user_rotation | tr -d '\r')"

restore_device() {
  if [[ -n "$ORIGINAL_SIZE_OVERRIDE" ]]; then
    "${DEVICE[@]}" shell wm size "$ORIGINAL_SIZE_OVERRIDE" >/dev/null || true
  else
    "${DEVICE[@]}" shell wm size reset >/dev/null || true
  fi
  if [[ -n "$ORIGINAL_DENSITY_OVERRIDE" ]]; then
    "${DEVICE[@]}" shell wm density "$ORIGINAL_DENSITY_OVERRIDE" >/dev/null || true
  else
    "${DEVICE[@]}" shell wm density reset >/dev/null || true
  fi
  if [[ -z "$ORIGINAL_FONT_SCALE" || "$ORIGINAL_FONT_SCALE" == null ]]; then
    "${DEVICE[@]}" shell settings delete system font_scale >/dev/null || true
  else
    "${DEVICE[@]}" shell settings put system font_scale "$ORIGINAL_FONT_SCALE" >/dev/null || true
  fi
  if [[ -z "$ORIGINAL_ACCELEROMETER" || "$ORIGINAL_ACCELEROMETER" == null ]]; then
    "${DEVICE[@]}" shell settings delete system accelerometer_rotation >/dev/null || true
  else
    "${DEVICE[@]}" shell settings put system accelerometer_rotation "$ORIGINAL_ACCELEROMETER" >/dev/null || true
  fi
  if [[ -z "$ORIGINAL_ROTATION" || "$ORIGINAL_ROTATION" == null ]]; then
    "${DEVICE[@]}" shell settings delete system user_rotation >/dev/null || true
  else
    "${DEVICE[@]}" shell settings put system user_rotation "$ORIGINAL_ROTATION" >/dev/null || true
  fi
  "${DEVICE[@]}" shell am force-stop com.tuanjie.urasclient2 >/dev/null || true
  "${DEVICE[@]}" shell am start -W -n com.tuanjie.urasclient2/.MainActivity >/dev/null || true
}
trap restore_device EXIT

dump_ui() {
  local output_file="$1"
  local attempt
  for attempt in {1..10}; do
    "${DEVICE[@]}" shell rm -f "$DEVICE_UI_XML" >/dev/null 2>&1 || true
    if "${DEVICE[@]}" shell uiautomator dump "$DEVICE_UI_XML" >/dev/null 2>&1 \
        && "${DEVICE[@]}" shell cat "$DEVICE_UI_XML" >"$output_file" 2>/dev/null \
        && [[ -s "$output_file" ]]; then
      return 0
    fi
    sleep 0.4
  done
  echo "UI hierarchy unavailable after bounded retries" >&2
  return 1
}

node_center() {
  local resource="$1" file="$2" bounds
  bounds="$(grep -o "<node[^>]*${resource}[^>]*/>" "$file" | head -n 1 \
    | sed -nE 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/p')"
  python3 - "$bounds" <<'PY'
import sys
values = [int(value) for value in sys.argv[1].split()]
if len(values) != 4:
    raise SystemExit(1)
print((values[0] + values[2]) // 2, (values[1] + values[3]) // 2)
PY
}

wait_resource() {
  local resource="$1" file="$2"
  for _ in {1..30}; do
    dump_ui "$file"
    grep -Fq "$resource" "$file" && return 0
    sleep 0.2
  done
  echo "resource did not become visible: $resource" >&2
  return 1
}

tap_resource() {
  local resource="$1" file="$2" center x y
  dump_ui "$file"
  center="$(node_center "$resource" "$file")"
  read -r x y <<<"$center"
  "${DEVICE[@]}" shell input tap "$x" "$y"
}

validate_ui() {
  local file="$1" width="$2" height="$3" density="$4" expected_profile="$5"
  python3 - "$file" "$width" "$height" "$density" "$expected_profile" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, width, height, density, expected_profile = sys.argv[1:]
width, height, density = map(int, (width, height, density))
root = ET.parse(path).getroot()

def bounds(node):
    values = [int(value) for value in re.findall(r"\d+", node.attrib.get("bounds", ""))]
    if len(values) != 4:
        raise SystemExit("invalid UI bounds")
    return tuple(values)

def node(identifier):
    matches = [item for item in root.iter() if item.attrib.get("resource-id", "").endswith("/" + identifier)]
    if len(matches) != 1:
        raise SystemExit(f"expected one visible {identifier}, found {len(matches)}")
    return matches[0]

panel = node("centralBrainPanel")
panel_bounds = bounds(panel)
panel_width = round(624 * density / 160)
panel_height = round(888 * density / 160)
top = round(160 * density / 160)
right_margin = round(32 * density / 160)
expected = (width - right_margin - panel_width, top, width - right_margin, top + panel_height)
if panel_bounds != expected:
    raise SystemExit(f"{expected_profile} panel bounds {panel_bounds} != {expected}")
if panel_bounds[0] < 0 or panel_bounds[1] < 0 or panel_bounds[2] > width or panel_bounds[3] > height:
    raise SystemExit(f"{expected_profile} panel exceeds display")

controls = []
for item in root.iter():
    resource = item.attrib.get("resource-id", "")
    class_name = item.attrib.get("class", "")
    if not resource.startswith("com.tuanjie.urasclient2:id/centralBrain"):
        continue
    if class_name != "android.widget.Button":
        continue
    left, top_value, right, bottom = bounds(item)
    width_dp = (right - left) * 160.0 / density
    height_dp = (bottom - top_value) * 160.0 / density
    if width_dp < 47.0 or height_dp < 47.0:
        raise SystemExit(
            f"{expected_profile} touch target below 48dp: {resource} {width_dp:.2f}x{height_dp:.2f}"
        )
    if not item.attrib.get("content-desc", "").strip():
        raise SystemExit(f"{expected_profile} missing runtime content description: {resource}")
    if left < panel_bounds[0] or top_value < panel_bounds[1] \
            or right > panel_bounds[2] or bottom > panel_bounds[3]:
        raise SystemExit(f"{expected_profile} control outside panel: {resource}")
    controls.append((resource, (left, top_value, right, bottom)))

if len(controls) < 10:
    raise SystemExit(f"{expected_profile} visible control coverage too small: {len(controls)}")
for index, (left_resource, left_bounds) in enumerate(controls):
    for right_resource, right_bounds in controls[index + 1:]:
        intersection_width = min(left_bounds[2], right_bounds[2]) - max(left_bounds[0], right_bounds[0])
        intersection_height = min(left_bounds[3], right_bounds[3]) - max(left_bounds[1], right_bounds[1])
        if intersection_width > 0 and intersection_height > 0:
            raise SystemExit(f"{expected_profile} controls overlap: {left_resource} / {right_resource}")

intent = node("centralBrainIntentTab")
plan = node("centralBrainPlanTab")
if intent.attrib.get("selected") != "true" or plan.attrib.get("selected") != "false":
    raise SystemExit(f"{expected_profile} selected state is not exposed to accessibility")
if not node("centralBrainTiredButton").attrib.get("text", "").startswith("我有些疲惫"):
    raise SystemExit(f"{expected_profile} longest Chinese scenario label was lost")
PY
}

apply_profile() {
  local name="$1" width="$2" height="$3" density="$4" font_scale="$5"
  "${DEVICE[@]}" shell wm size "${width}x${height}" >/dev/null
  "${DEVICE[@]}" shell wm density "$density" >/dev/null
  "${DEVICE[@]}" shell settings put system font_scale "$font_scale" >/dev/null
  "${DEVICE[@]}" shell settings put system accelerometer_rotation 0 >/dev/null
  "${DEVICE[@]}" shell settings put system user_rotation 0 >/dev/null
  "${DEVICE[@]}" shell am force-stop com.tuanjie.urasclient2
  "${DEVICE[@]}" shell pm clear com.tuanjie.urasclient2 >/dev/null
  "${DEVICE[@]}" logcat -c
  "${DEVICE[@]}" shell am start -W -n com.tuanjie.urasclient2/.MainActivity \
    >"$LOG_DIR/${name}-activity.txt"
  grep -Fq 'Status: ok' "$LOG_DIR/${name}-activity.txt"
  wait_resource centralBrainNavigationTrigger "$LOG_DIR/${name}-initial.xml"
  tap_resource centralBrainNavigationTrigger "$LOG_DIR/${name}-trigger.xml"
  wait_resource centralBrainColdButton "$LOG_DIR/${name}-panel.xml"
  validate_ui "$LOG_DIR/${name}-panel.xml" "$width" "$height" "$density" "$name"
  local logcat_output
  logcat_output="$("${DEVICE[@]}" logcat -d CbClient2Hmi:I '*:S')"
  grep -Fq 'cockpit_display_supported=true' <<<"$logcat_output"
  grep -Fq "cockpit_display_profile=$name" <<<"$logcat_output"
}

apply_profile COMPACT_1280_720 1280 720 107 1.0
apply_profile STANDARD_1920_1080 1920 1080 160 1.0
apply_profile STANDARD_1920_1080 1920 1080 160 1.3
mv "$LOG_DIR/STANDARD_1920_1080-panel.xml" "$LOG_DIR/STANDARD_1920_1080-large-text-panel.xml"
apply_profile LARGE_2560_1440 2560 1440 213 1.0

# An unlisted display remains visible as the original app, but the AIOS trigger is disabled.
"${DEVICE[@]}" shell wm size 1366x768 >/dev/null
"${DEVICE[@]}" shell wm density 114 >/dev/null
"${DEVICE[@]}" shell settings put system font_scale 1.0 >/dev/null
"${DEVICE[@]}" shell am force-stop com.tuanjie.urasclient2
"${DEVICE[@]}" shell pm clear com.tuanjie.urasclient2 >/dev/null
"${DEVICE[@]}" logcat -c
"${DEVICE[@]}" shell am start -W -n com.tuanjie.urasclient2/.MainActivity \
  >"$LOG_DIR/unsupported-activity.txt"
wait_resource centralBrainNavigationTrigger "$LOG_DIR/unsupported.xml"
UNSUPPORTED_NODE="$(grep -o '<node[^>]*centralBrainNavigationTrigger[^>]*/>' \
  "$LOG_DIR/unsupported.xml" | head -n 1)"
grep -Fq 'enabled="false"' <<<"$UNSUPPORTED_NODE"
UNSUPPORTED_LOG="$("${DEVICE[@]}" logcat -d CbClient2Hmi:I '*:S')"
grep -Fq 'cockpit_display_profile=UNSUPPORTED' <<<"$UNSUPPORTED_LOG"
grep -Fq 'cockpit_display_rejection=DISPLAY_MATRIX_MISMATCH' <<<"$UNSUPPORTED_LOG"

restore_device
trap - EXIT

printf '%s\n' \
  'cockpit_display_matrix_android13_arm64_verified=true' \
  'cockpit_display_compact_1280_720_verified=true' \
  'cockpit_display_standard_1920_1080_verified=true' \
  'cockpit_display_large_2560_1440_verified=true' \
  'cockpit_display_large_text_1_3_verified=true' \
  'cockpit_touch_target_min_dp=48' \
  'cockpit_accessibility_content_description_verified=true' \
  'cockpit_accessibility_state_not_color_only=true' \
  'cockpit_long_chinese_non_overlap_verified=true' \
  'cockpit_display_unsupported_fail_closed=true' \
  'cockpit_display_effect_authorization_source=false' \
  'scenario_execution_enabled=false' \
  'production_effect_dispatch_enabled=false' \
  'hardware_accessed=false'
