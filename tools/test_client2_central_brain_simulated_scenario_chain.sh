#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SCN-001, S2-GRF-001, S2-EVT-001, S2-EFF-001,
# S2-SAF-001, S2-HMI-003/006, APP-004, XSC-001/005/006, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${ANDROID_SERIAL:-}"
BUILD=true
REQUIRE_API_33=false
REPLACE_CONFLICTING_CLIENT2=false
ALLOW_X86_64=false

while (($# > 0)); do
  case "$1" in
    --serial) SERIAL="$2"; shift 2 ;;
    --skip-build) BUILD=false; shift ;;
    --require-api-33) REQUIRE_API_33=true; shift ;;
    --replace-conflicting-client2) REPLACE_CONFLICTING_CLIENT2=true; shift ;;
    --allow-x86-64) ALLOW_X86_64=true; shift ;;
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
  [[ -e "$path" ]] || { echo "missing simulated scenario-chain test input: $path" >&2; exit 1; }
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
if [[ "$ABI" != arm64-v8a ]]; then
  [[ "$ALLOW_X86_64" == true && "$ABI" == x86_64 ]] \
    || { echo "ARM64 target required unless --allow-x86-64 is explicit" >&2; exit 1; }
fi

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
LOG_DIR="$ROOT_DIR/logs/test/client2-simulated-scenario-chain/$STAMP"
mkdir -p "$LOG_DIR"
DEVICE_XML=/sdcard/client2-simulated-scenario-chain.xml

dump_ui() {
  local output_file="$1" attempt
  for attempt in {1..10}; do
    "${DEVICE[@]}" shell rm -f "$DEVICE_XML" >/dev/null 2>&1 || true
    if timeout 8s "${DEVICE[@]}" shell uiautomator dump "$DEVICE_XML" >/dev/null 2>&1 \
        && timeout 8s "${DEVICE[@]}" shell cat "$DEVICE_XML" >"$output_file" 2>/dev/null \
        && [[ -s "$output_file" ]]; then
      return 0
    fi
    sleep 0.4
  done
  echo "Simulated scenario-chain UI hierarchy unavailable after bounded retries" >&2
  return 1
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
  local resource="$1" file="$LOG_DIR/tap-$1.xml" bounds coordinates x y
  dump_ui "$file"
  bounds="$(node_value "$resource" bounds "$file")"
  coordinates="$(python3 - "$bounds" <<'PY'
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
)" || return 1
  read -r x y <<<"$coordinates"
  [[ "$x" =~ ^[0-9]+$ && "$y" =~ ^[0-9]+$ ]] \
    || { echo "resource has no tappable coordinates" >&2; return 1; }
  "${DEVICE[@]}" shell input tap "$x" "$y"
}

wait_log_marker() {
  local marker="$1"
  for _ in {1..60}; do
    if "${DEVICE[@]}" logcat -d -v brief \
        CbClient2Orchestration:I CbClient2Hmi:I '*:S' \
        | grep -Fq -- "$marker"; then
      return 0
    fi
    sleep 0.25
  done
  echo "required Client2 startup marker unavailable: $marker" >&2
  return 1
}

scroll_execution_to_edge() {
  local direction="$1" file="$LOG_DIR/scroll-execution-$1.xml" bounds x1 y1 x2 y2
  dump_ui "$file"
  bounds="$(node_value centralBrainExecutionSurface bounds "$file")"
  read -r x1 y1 x2 y2 < <(python3 - "$bounds" "$direction" <<'PY'
import re
import sys
match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", sys.argv[1])
if not match:
    raise SystemExit("execution surface has no bounds")
left, top, right, bottom = map(int, match.groups())
x = (left + right) // 2
upper = top + (bottom - top) // 5
lower = top + 4 * (bottom - top) // 5
if sys.argv[2] == "bottom":
    print(x, lower, x, upper)
elif sys.argv[2] == "top":
    print(x, upper, x, lower)
else:
    raise SystemExit("unsupported execution scroll direction")
PY
  )
  for _ in {1..6}; do
    "${DEVICE[@]}" shell input swipe "$x1" "$y1" "$x2" "$y2" 120
    sleep 0.1
  done
}

wait_value() {
  local resource="$1" attribute="$2" expected="$3"
  local file="$LOG_DIR/wait-$resource-$attribute.xml" value
  for _ in {1..60}; do
    dump_ui "$file"
    value="$(node_value "$resource" "$attribute" "$file")"
    [[ "$value" == *"$expected"* ]] && return 0
    sleep 0.25
  done
  echo "resource $resource did not expose expected bounded state" >&2
  return 1
}

wait_text() {
  wait_value "$1" text "$2"
}

wait_enabled() {
  wait_value "$1" enabled "$2"
}

assert_completed_chain() {
  local effect_count="$1" readback_count="$2"
  wait_text centralBrainExecutionSummaryText 'COMPLETED'
  wait_text centralBrainTimelineIntentText 'SESSION ACCEPTED'
  wait_text centralBrainTimelineContextText 'CAPTURED'
  wait_text centralBrainTimelinePlanText 'PUBLISHED'
  wait_text centralBrainTimelinePolicyText 'ACTIVE'
  wait_text centralBrainTimelineGraphText 'VERIFIED'
  wait_text centralBrainTimelineEffectText 'APPLIED'
  wait_text centralBrainTimelineReadbackText 'VERIFIED'
  wait_text centralBrainExecutionActionsText "Effect $effect_count"
  wait_text centralBrainExecutionActionsText "Readback $readback_count"
}

"${DEVICE[@]}" shell pm clear com.centralbrain.runtime >/dev/null
"${DEVICE[@]}" shell pm clear com.tuanjie.urasclient2 >/dev/null
"${DEVICE[@]}" logcat -c
FILTERED_LOGCAT="$LOG_DIR/central-brain-filtered-logcat.txt"
"${DEVICE[@]}" logcat -v threadtime \
  CbClient2Orchestration:V CentralBrainRuntime:V CbClient2Hmi:V '*:S' \
  >"$FILTERED_LOGCAT" 2>&1 &
FILTERED_LOGCAT_PID=$!
cleanup_filtered_logcat() {
  kill "$FILTERED_LOGCAT_PID" >/dev/null 2>&1 || true
  wait "$FILTERED_LOGCAT_PID" >/dev/null 2>&1 || true
}
trap cleanup_filtered_logcat EXIT
"${DEVICE[@]}" shell am start -W -n com.tuanjie.urasclient2/.MainActivity \
  >"$LOG_DIR/activity-start.txt"
grep -Fq 'Status: ok' "$LOG_DIR/activity-start.txt"
sleep 1
wait_log_marker 'cockpit_display_supported=true'
wait_log_marker 'client2_orchestration_sdk_connected=true'
tap_resource centralBrainNavigationTrigger

# PARKED is build-owned debug Context and never grants production Effect authority.
tap_resource centralBrainPlanTab
tap_resource centralBrainEngineerDetailButton
wait_text centralBrainEngineerStatusText 'CONNECTED'
tap_resource centralBrainEngineerDrivingParkedButton
wait_text centralBrainDrivingText 'PARKED'
tap_resource centralBrainDrawerCloseButton

tap_resource centralBrainIntentTab
tap_resource centralBrainColdButton
assert_completed_chain 3 '3/3'
tap_resource centralBrainResultTab
wait_text centralBrainResultSummaryText 'Debug 仿真执行完成'
wait_text centralBrainResultEvidenceText 'Hardware：NOT ACCESSED'

tap_resource centralBrainIntentTab
tap_resource centralBrainTiredButton
wait_text centralBrainExecutionSummaryText 'WAITING_APPROVAL'
wait_text centralBrainTimelinePolicyText 'APPROVAL REQUIRED'
scroll_execution_to_edge bottom
wait_enabled centralBrainApproveButton true
wait_enabled centralBrainRejectButton true
tap_resource centralBrainApproveButton
wait_text centralBrainApprovalStateText 'Input count：1'
wait_enabled centralBrainApproveButton false
scroll_execution_to_edge top
assert_completed_chain 5 '3/3'

tap_resource centralBrainIntentTab
tap_resource centralBrainTiredButton
wait_text centralBrainExecutionSummaryText 'WAITING_APPROVAL'
scroll_execution_to_edge bottom
tap_resource centralBrainRejectButton
wait_text centralBrainApprovalStateText 'Input count：1'
scroll_execution_to_edge top
wait_text centralBrainExecutionSummaryText 'PARTIAL'
wait_text centralBrainTimelineGraphText 'SKIPPED'
wait_text centralBrainTimelineEffectText 'APPLIED'
wait_text centralBrainTimelineReadbackText 'VERIFIED'
wait_text centralBrainExecutionActionsText 'Effect 4'
wait_text centralBrainExecutionActionsText 'Readback 2/2'

"${DEVICE[@]}" logcat -d -v threadtime >"$LOG_DIR/logcat.txt"
cleanup_filtered_logcat
trap - EXIT
for marker in \
  'cockpit_orchestration_sdk_v1_wired=true' \
  'cockpit_legacy_simulated_scenario_binder_used=false' \
  'cockpit_simulated_scenario_projection_reducer_owned=true' \
  'cockpit_simulated_scenario_effect_dispatch_enabled=true' \
  'cockpit_simulated_scenario_readback_available=true' \
  'cockpit_simulated_approval_input_explicit=true' \
  'cockpit_simulated_hardware_effect_dispatch_enabled=false'; do
  grep -Fq -- "$marker" "$FILTERED_LOGCAT" \
    || { echo "simulated scenario-chain log marker missing" >&2; exit 1; }
done
if grep -Fq 'hardware_accessed=true' "$LOG_DIR/logcat.txt"; then
  echo "unexpected hardware access claim" >&2
  exit 1
fi

printf '%s\n' \
  'client2_orchestration_sdk_v1_wired=true' \
  'client2_session_before_orchestration=true' \
  'client2_orchestration_resume_read_before_start=true' \
  'client2_orchestration_plan_validated=true' \
  'client2_orchestration_approval_projection_bound=true' \
  'client2_orchestration_debug_capability_policy_wired=true' \
  'client2_legacy_simulated_scenario_binder_used=false' \
  'client2_simulated_cold_completed_verified=true' \
  'client2_simulated_cold_effect_dispatch_count=3' \
  'client2_simulated_cold_readback_match_count=3' \
  'client2_simulated_fatigue_approval_wait_verified=true' \
  'client2_simulated_fatigue_approved_completed_verified=true' \
  'client2_simulated_fatigue_approved_effect_dispatch_count=5' \
  'client2_simulated_fatigue_approved_readback_match_count=3' \
  'client2_simulated_fatigue_rejected_partial_verified=true' \
  'client2_simulated_fatigue_rejected_effect_dispatch_count=4' \
  'client2_simulated_fatigue_rejected_readback_match_count=2' \
  'client2_simulated_approval_input_count=2' \
  'client2_simulated_seven_stage_ui_verified=true' \
  'client2_simulated_hardware_effect_dispatch_enabled=false' \
  'scenario_execution_enabled=false' \
  'hardware_accessed=false' \
  'target_hardware_validated=false' \
  'production_ready=false'
if [[ "$ABI" == x86_64 ]]; then
  printf '%s\n' \
    'client2_android13_x86_64_verified=true' \
    'client2_android13_arm64_verified=false'
else
  printf '%s\n' \
    'client2_android13_x86_64_verified=false' \
    'client2_android13_arm64_verified=true'
fi
