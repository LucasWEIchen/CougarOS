#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-HMI-004, S2-ADP-001, S2-OBS-001, DEL-001/003/004/005.

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
AAPT="${AAPT:-$ROOT_DIR/.tools/android-build-tools-current/aapt}"
RUNTIME_DEBUG="$ROOT_DIR/central-brain/android-runtime/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
RUNTIME_RELEASE="$ROOT_DIR/central-brain/android-runtime/runtime-service/build/outputs/apk/release/runtime-service-release-unsigned.apk"
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
  "$ROOT_DIR/central-brain/android-runtime/gradlew" \
    -p "$ROOT_DIR/central-brain/android-runtime" \
    :runtime-service:assembleRelease >/dev/null
fi
for path in "$ADB" "$AAPT" "$RUNTIME_DEBUG" "$RUNTIME_RELEASE" "$CLIENT2_APK"; do
  [[ -e "$path" ]] || { echo "missing engineer simulation test input: $path" >&2; exit 1; }
done

if "$AAPT" dump xmltree "$RUNTIME_RELEASE" AndroidManifest.xml \
    | grep -Eq 'DebugSimulationController|CONTROL_DEBUG_SIMULATION'; then
  echo "debug simulation service leaked into Runtime release APK" >&2
  exit 1
fi
if ! "$AAPT" dump permissions "$CLIENT2_APK" \
    | grep -Fq 'com.centralbrain.permission.CONTROL_DEBUG_SIMULATION'; then
  echo "Client2 debug APK lacks simulation signature permission" >&2
  exit 1
fi

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

PACKAGE_DUMP="$("${DEVICE[@]}" shell dumpsys package com.tuanjie.urasclient2)"
grep -Fq 'com.centralbrain.permission.CONTROL_DEBUG_SIMULATION: granted=true' \
  <<<"$PACKAGE_DUMP" || { echo "simulation signature permission not granted" >&2; exit 1; }

STAMP="$(date +%Y%m%d_%H%M%S)"
LOG_DIR="$ROOT_DIR/logs/test/client2-engineer-simulation/$STAMP"
mkdir -p "$LOG_DIR"
DEVICE_XML=/sdcard/client2-engineer-simulation.xml

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
  for _ in {1..30}; do
    dump_ui "$file"
    value="$(node_value "$resource" text "$file")"
    [[ "$value" == *"$expected"* ]] && return 0
    sleep 0.2
  done
  echo "resource $resource did not contain expected text" >&2
  return 1
}

revision() {
  local file="$LOG_DIR/revision.xml" text_value
  dump_ui "$file"
  text_value="$(node_value centralBrainEngineerStatusText text "$file")"
  python3 - "$text_value" <<'PY'
import re
import sys
match = re.search(r"Revision[^0-9]*([0-9]+)", sys.argv[1])
if not match:
    raise SystemExit("engineer revision missing")
print(match.group(1))
PY
}

assert_revision_increased() {
  local before="$1" after
  for _ in {1..30}; do
    after="$(revision)"
    if ((after > before)); then
      printf '%s\n' "$after"
      return 0
    fi
    sleep 0.2
  done
  echo "engineer controller revision did not increase" >&2
  return 1
}

"${DEVICE[@]}" shell pm clear com.centralbrain.runtime >/dev/null
"${DEVICE[@]}" shell pm clear com.tuanjie.urasclient2 >/dev/null
"${DEVICE[@]}" logcat -c
"${DEVICE[@]}" shell am start -W -n com.tuanjie.urasclient2/.MainActivity \
  >"$LOG_DIR/activity-start.txt"
grep -Fq 'Status: ok' "$LOG_DIR/activity-start.txt"
sleep 1
tap_resource centralBrainNavigationTrigger
tap_resource centralBrainPlanTab
wait_text centralBrainEngineerDetailButton '工程仿真'
tap_resource centralBrainEngineerDetailButton
wait_text centralBrainEngineerStatusText 'CONNECTED'

REV="$(revision)"
tap_resource centralBrainEngineerDrivingParkedButton
REV="$(assert_revision_increased "$REV")"
wait_text centralBrainDrivingText 'PARKED'
tap_resource centralBrainEngineerOccupancyOccupiedButton
REV="$(assert_revision_increased "$REV")"
tap_resource centralBrainEngineerBeltUnbeltedButton
REV="$(assert_revision_increased "$REV")"
wait_text centralBrainEngineerContextText 'OCCUPIED'
wait_text centralBrainEngineerContextText 'UNBELTED'

tap_resource centralBrainEngineerFaultDelayButton
REV="$(assert_revision_increased "$REV")"
wait_text centralBrainEngineerFaultText 'DELAY'
tap_resource centralBrainEngineerFaultTimeoutButton
REV="$(assert_revision_increased "$REV")"
wait_text centralBrainEngineerFaultText 'TIMEOUT'
tap_resource centralBrainEngineerFaultFailureButton
REV="$(assert_revision_increased "$REV")"
wait_text centralBrainEngineerFaultText 'RETRYABLE_FAILURE'
tap_resource centralBrainEngineerAdapterSeatButton
tap_resource centralBrainEngineerFaultMismatchButton
REV="$(assert_revision_increased "$REV")"
wait_text centralBrainEngineerFaultText 'READBACK_MISMATCH'

tap_resource centralBrainEngineerDrivingMovingButton
REV="$(assert_revision_increased "$REV")"
wait_text centralBrainDrivingText 'MOVING'
tap_resource centralBrainEngineerDrivingUnknownButton
REV="$(assert_revision_increased "$REV")"
wait_text centralBrainDrivingText 'UNKNOWN'
tap_resource centralBrainEngineerResetButton
assert_revision_increased "$REV" >/dev/null
wait_text centralBrainEngineerContextText 'UNKNOWN_RESTRICTED'
wait_text centralBrainDrivingText 'UNKNOWN'

"${DEVICE[@]}" logcat -d -v threadtime >"$LOG_DIR/logcat.txt"
grep -Fq 'debug_simulation_audit_event=true' "$LOG_DIR/logcat.txt"
if grep -Fq 'capability denied capability=debug.simulation.control' "$LOG_DIR/logcat.txt"; then
  echo "debug simulation capability was denied" >&2
  exit 1
fi

printf '%s\n' \
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
  'vehicle_signal_provider_wired=false' \
  'production_effect_dispatch_enabled=false' \
  'hardware_accessed=false'
