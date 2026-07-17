#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-HMI-001..006, S2-SCN-001, APP-004, XSC-001/005/006, DEL-001/003/004/005.

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
RUNTIME_DEBUG="$ROOT_DIR/central-brain/android-runtime/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
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
for path in "$ADB" "$RUNTIME_DEBUG" "$CLIENT2_APK"; do
  [[ -e "$path" ]] || { echo "missing scenario synchronization test input: $path" >&2; exit 1; }
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

"${DEVICE[@]}" install -r "$(adb_path "$RUNTIME_DEBUG")" >/dev/null
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
LOG_DIR="$ROOT_DIR/logs/test/client2-scenario-sync/$STAMP"
mkdir -p "$LOG_DIR"
DEVICE_XML=/sdcard/client2-scenario-sync.xml

dump_ui() {
  "${DEVICE[@]}" shell uiautomator dump "$DEVICE_XML" >/dev/null
  "${DEVICE[@]}" shell cat "$DEVICE_XML" >"$1"
}

node_value() {
  local resource="$1" attribute="$2" file="$3"
  python3 - "$resource" "$attribute" "$file" <<'PY'
import sys
import xml.etree.ElementTree as ET
resource, attribute, path = sys.argv[1:]
root = ET.parse(path).getroot()
for node in root.iter("node"):
    if node.attrib.get("resource-id", "").endswith("/" + resource):
        print(node.attrib.get(attribute, ""))
        break
PY
}

tap_resource() {
  local resource="$1" file="$LOG_DIR/tap-$1.xml" bounds
  dump_ui "$file"
  bounds="$(node_value "$resource" bounds "$file")"
  python3 - "$bounds" <<'PY' | while read -r x y; do "${DEVICE[@]}" shell input tap "$x" "$y"; done
import re
import sys
match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", sys.argv[1])
if not match:
    raise SystemExit("resource has no tappable bounds")
left, top, right, bottom = map(int, match.groups())
if right <= left or bottom <= top:
    raise SystemExit("resource has empty bounds")
print((left + right) // 2, (top + bottom) // 2)
PY
}

wait_text() {
  local resource="$1" expected="$2" file="$LOG_DIR/wait-$1.xml" value
  for _ in {1..40}; do
    dump_ui "$file"
    value="$(node_value "$resource" text "$file")"
    [[ "$value" == *"$expected"* ]] && return 0
    sleep 0.25
  done
  echo "resource $resource did not contain expected bounded text" >&2
  return 1
}

open_intent_surface() {
  tap_resource centralBrainDrawerCloseButton 2>/dev/null || true
  tap_resource centralBrainIntentTab
}

inspect_device_role() {
  local detail_resource="$1" request_resource="$2" role="$3"
  tap_resource centralBrainPlanTab
  tap_resource "$detail_resource"
  wait_text "$request_resource" "$role"
  wait_text "$request_resource" 'NOT PUBLISHED'
  wait_text "$request_resource" 'Event #1'
}

"${DEVICE[@]}" shell pm clear com.centralbrain.runtime >/dev/null
"${DEVICE[@]}" shell pm clear com.tuanjie.urasclient2 >/dev/null
"${DEVICE[@]}" logcat -c
"${DEVICE[@]}" shell am start -W -n com.tuanjie.urasclient2/.MainActivity \
  >"$LOG_DIR/activity-start.txt"
grep -Fq 'Status: ok' "$LOG_DIR/activity-start.txt"
sleep 1
tap_resource centralBrainNavigationTrigger

# Debug Context enables the otherwise restricted parameter UI; it never grants Effect authority.
tap_resource centralBrainPlanTab
wait_text centralBrainEngineerDetailButton '工程仿真'
tap_resource centralBrainEngineerDetailButton
wait_text centralBrainEngineerStatusText 'CONNECTED'
tap_resource centralBrainEngineerDrivingParkedButton
wait_text centralBrainDrivingText 'PARKED'
tap_resource centralBrainEngineerOccupancyOccupiedButton
wait_text centralBrainEngineerContextText 'OCCUPIED'
tap_resource centralBrainEngineerBeltUnbeltedButton
wait_text centralBrainEngineerContextText 'UNBELTED'

open_intent_surface
tap_resource centralBrainColdButton
wait_text centralBrainConnectionText '已连接'
inspect_device_role centralBrainHvacDetailButton centralBrainHvacRequestText 'CATALOG REQUIRED'

open_intent_surface
tap_resource centralBrainTiredButton
wait_text centralBrainConnectionText '已连接'
inspect_device_role centralBrainSeatDetailButton centralBrainSeatRequestText 'CATALOG OPTIONAL'

open_intent_surface
tap_resource centralBrainNapButton
wait_text centralBrainConnectionText '已连接'
inspect_device_role centralBrainSeatDetailButton centralBrainSeatRequestText 'CATALOG REQUIRED'

tap_resource centralBrainDrawerCloseButton
tap_resource centralBrainHvacDetailButton
tap_resource centralBrainHvacTemperatureUpButton
wait_text centralBrainHvacRequestText 'MANUAL TARGET'
wait_text centralBrainHvacRequestText 'SESSION_ACCEPTED'

tap_resource centralBrainDrawerCloseButton
tap_resource centralBrainSeatDetailButton
tap_resource centralBrainSeatHeatUpButton
wait_text centralBrainSeatRequestText 'MANUAL TARGET'
wait_text centralBrainSeatRequestText 'SESSION_ACCEPTED'

"${DEVICE[@]}" logcat -d -v threadtime >"$LOG_DIR/logcat.txt"
grep -Fq 'cockpit_scenario_device_session_synchronized=true' "$LOG_DIR/logcat.txt"
if grep -Fq 'hardware_accessed=true' "$LOG_DIR/logcat.txt"; then
  echo "unexpected hardware access claim" >&2
  exit 1
fi

printf '%s\n' \
  'cockpit_scenario_natural_cold_sync_verified=true' \
  'cockpit_scenario_natural_fatigue_sync_verified=true' \
  'cockpit_scenario_natural_rest_sync_verified=true' \
  'cockpit_scenario_manual_hvac_sync_verified=true' \
  'cockpit_scenario_manual_seat_sync_verified=true' \
  'cockpit_scenario_device_session_synchronized=true' \
  'cockpit_scenario_plan_publication_inferred=false' \
  'cockpit_scenario_effect_dispatch_enabled=false' \
  'cockpit_scenario_readback_available=false' \
  'cockpit_scenario_debug_context_effect_authority=false' \
  'scenario_execution_enabled=false' \
  'production_effect_dispatch_enabled=false' \
  'hardware_accessed=false'
