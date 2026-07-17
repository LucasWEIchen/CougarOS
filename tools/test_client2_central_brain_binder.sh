#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001, XSC-005, XSC-006, NV-G-006, NV-P-002, DEL-001/003/004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${ANDROID_SERIAL:-}"
BUILD=true
REQUIRE_API_33=false
REPLACE_CONFLICTING_CLIENT2=false

usage() {
  cat <<'EOF'
Usage: test_client2_central_brain_binder.sh [options]

Options:
  --serial SERIAL    Select an adb device explicitly.
  --skip-build       Reuse existing Runtime and Client2 debug APKs.
  --require-api-33   Fail unless the selected device is exactly Android API 33.
  --replace-conflicting-client2
                     Remove an installed Client2 only on signer mismatch.
  -h, --help         Show this help.
EOF
}

while (($# > 0)); do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "--serial requires a value" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    --skip-build)
      BUILD=false
      shift
      ;;
    --require-api-33)
      REQUIRE_API_33=true
      shift
      ;;
    --replace-conflicting-client2)
      REPLACE_CONFLICTING_CLIENT2=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/env.sh"
fi

: "${JAVA_HOME:?JAVA_HOME must point to JDK 17}"
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"

ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
APKSIGNER="${APKSIGNER:-$ROOT_DIR/.tools/android-build-tools-current/apksigner}"
AAPT="${AAPT:-$ROOT_DIR/.tools/android-build-tools-current/aapt}"
JAR="${JAR:-$JAVA_HOME/bin/jar}"
RUNTIME_APK="$ROOT_DIR/central-brain/android-runtime/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
CLIENT2_APK="$ROOT_DIR/builds/client2-central-brain/signed/client2-central-brain.debug.apk"

adb_apk_path() {
  local path="$1"
  if [[ "$ADB" == *.exe ]] && command -v wslpath >/dev/null; then
    wslpath -w "$path"
  else
    printf '%s\n' "$path"
  fi
}

if [[ "$BUILD" == true ]]; then
  bash "$ROOT_DIR/tools/build_client2_central_brain_demo.sh"
fi

for artifact in "$RUNTIME_APK" "$CLIENT2_APK"; do
  [[ -f "$artifact" ]] || { echo "missing Binder test artifact: $artifact" >&2; exit 1; }
done
for tool in "$ADB" "$APKSIGNER" "$AAPT" "$JAR"; do
  [[ -x "$tool" ]] || { echo "required tool is not executable: $tool" >&2; exit 1; }
done

if [[ -z "$SERIAL" ]]; then
  mapfile -t ONLINE_DEVICES < <("$ADB" devices | tr -d '\r' | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [[ ${#ONLINE_DEVICES[@]} -ne 1 ]]; then
    echo "expected exactly one online adb device; use --serial when multiple exist" >&2
    "$ADB" devices -l >&2
    exit 1
  fi
  SERIAL="${ONLINE_DEVICES[0]}"
fi

ADB_DEVICE=("$ADB" -s "$SERIAL")
SDK="$("${ADB_DEVICE[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
ABI="$("${ADB_DEVICE[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
if [[ ! "$SDK" =~ ^[0-9]+$ ]] || ((SDK < 33)); then
  echo "Android API 33 or newer is required; device reported '$SDK'" >&2
  exit 1
fi
if [[ "$REQUIRE_API_33" == true && "$SDK" != "33" ]]; then
  echo "Client2 Binder API 33 evidence requested, but device reported API $SDK" >&2
  exit 1
fi

signer_digest() {
  "$APKSIGNER" verify --print-certs "$1" \
    | awk -F': ' '/certificate SHA-256 digest/ {print $2; exit}'
}

RUNTIME_SIGNER="$(signer_digest "$RUNTIME_APK")"
CLIENT2_SIGNER="$(signer_digest "$CLIENT2_APK")"
if [[ -z "$RUNTIME_SIGNER" || "$RUNTIME_SIGNER" != "$CLIENT2_SIGNER" ]]; then
  echo "Runtime and Client2 APK signers differ" >&2
  exit 1
fi
if ! "$JAR" tf "$CLIENT2_APK" | grep -Fxq 'classes2.dex'; then
  echo "Client2 APK does not contain the SDK Binder bridge dex" >&2
  exit 1
fi
if ! "$AAPT" dump permissions "$CLIENT2_APK" \
    | grep -Fq 'com.centralbrain.permission.BIND_RUNTIME'; then
  echo "Client2 APK does not request the Runtime signature permission" >&2
  exit 1
fi
if "$AAPT" dump permissions "$CLIENT2_APK" | grep -Fq 'android.permission.INTERNET'; then
  echo "Client2 Binder APK must not request INTERNET" >&2
  exit 1
fi
if ! "$AAPT" dump xmltree "$CLIENT2_APK" AndroidManifest.xml \
    | grep -Fq 'com.centralbrain.runtime'; then
  echo "Client2 manifest does not expose the Runtime package query" >&2
  exit 1
fi

RUNTIME_APK_ARGUMENT="$(adb_apk_path "$RUNTIME_APK")"
CLIENT2_APK_ARGUMENT="$(adb_apk_path "$CLIENT2_APK")"
"${ADB_DEVICE[@]}" install -r "$RUNTIME_APK_ARGUMENT" >/dev/null
set +e
CLIENT2_INSTALL_OUTPUT="$("${ADB_DEVICE[@]}" install -r "$CLIENT2_APK_ARGUMENT" 2>&1)"
CLIENT2_INSTALL_STATUS=$?
set -e
SIGNER_MIGRATION_PERFORMED=false
if [[ $CLIENT2_INSTALL_STATUS -ne 0 ]]; then
  if ! grep -Eq 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match' \
      <<<"$CLIENT2_INSTALL_OUTPUT"; then
    echo "$CLIENT2_INSTALL_OUTPUT" >&2
    exit 1
  fi
  if [[ "$REPLACE_CONFLICTING_CLIENT2" != true ]]; then
    echo "SIGNER_MIGRATION_REQUIRED package=com.tuanjie.urasclient2" >&2
    echo "No package was removed. Re-run with --replace-conflicting-client2 only after approving data loss." >&2
    exit 1
  fi
  "${ADB_DEVICE[@]}" uninstall com.tuanjie.urasclient2 >/dev/null
  "${ADB_DEVICE[@]}" install "$CLIENT2_APK_ARGUMENT" >/dev/null
  SIGNER_MIGRATION_PERFORMED=true
fi

PACKAGE_DUMP="$("${ADB_DEVICE[@]}" shell dumpsys package com.tuanjie.urasclient2)"
if ! grep -Fq 'com.centralbrain.permission.BIND_RUNTIME: granted=true' \
    <<<"$PACKAGE_DUMP"; then
  echo "Client2 did not receive the Runtime signature permission" >&2
  exit 1
fi

STAMP="$(date +%Y%m%d_%H%M%S)"
LOG_DIR="$ROOT_DIR/logs/test/client2-central-brain-binder/$STAMP"
mkdir -p "$LOG_DIR"
"${ADB_DEVICE[@]}" shell settings put secure immersive_mode_confirmations confirmed
"${ADB_DEVICE[@]}" shell pm clear com.centralbrain.runtime >/dev/null
"${ADB_DEVICE[@]}" shell pm clear com.tuanjie.urasclient2 >/dev/null
"${ADB_DEVICE[@]}" shell am force-stop com.tuanjie.urasclient2
"${ADB_DEVICE[@]}" logcat -c
"${ADB_DEVICE[@]}" shell am start -W \
  -n com.tuanjie.urasclient2/.MainActivity >"$LOG_DIR/activity-start.txt"
if ! grep -Fq 'Status: ok' "$LOG_DIR/activity-start.txt"; then
  cat "$LOG_DIR/activity-start.txt" >&2
  echo "Client2 MainActivity did not start" >&2
  exit 1
fi

DEVICE_UI_XML=/sdcard/client2-central-brain-binder.xml
dump_ui() {
  local output_file="$1"
  "${ADB_DEVICE[@]}" shell uiautomator dump "$DEVICE_UI_XML" >/dev/null
  "${ADB_DEVICE[@]}" shell cat "$DEVICE_UI_XML" >"$output_file"
}

node_center() {
  local resource_id="$1"
  local ui_file="$2"
  local node bounds left top right bottom
  node="$(grep -o "<node[^>]*${resource_id}[^>]*/>" "$ui_file" \
    | head -n 1 || true)"
  [[ -n "$node" ]] || return 1
  bounds="$(sed -nE \
    's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/p' \
    <<<"$node")"
  read -r left top right bottom <<<"$bounds"
  [[ -n "${bottom:-}" ]] || return 1
  printf '%s %s\n' "$(((left + right) / 2))" "$(((top + bottom) / 2))"
}

tap_resource() {
  local resource_id="$1"
  local ui_file="$2"
  local center x y
  dump_ui "$ui_file"
  center="$(node_center "$resource_id" "$ui_file" || true)"
  read -r x y <<<"$center"
  if [[ -z "${y:-}" ]]; then
    echo "Client2 resource is not tappable: $resource_id" >&2
    return 1
  fi
  "${ADB_DEVICE[@]}" shell input tap "$x" "$y"
}

wait_for_resource_state() {
  local resource_id="$1"
  local expected_state="$2"
  local output_file="$3"
  local actual_state
  for _ in {1..20}; do
    dump_ui "$output_file"
    if grep -Fq "$resource_id" "$output_file"; then
      actual_state=visible
    else
      actual_state=hidden
    fi
    [[ "$actual_state" == "$expected_state" ]] && return 0
    sleep 0.1
  done
  echo "Client2 resource $resource_id did not become $expected_state" >&2
  return 1
}

wait_for_resource_state \
  centralBrainNavigationTrigger visible "$LOG_DIR/ui-initial.xml"
if grep -Fq 'centralBrainColdButton' "$LOG_DIR/ui-initial.xml"; then
  echo "Client2 Central Brain panel must be hidden after launch" >&2
  exit 1
fi

TRIGGER_CENTER="$(node_center centralBrainNavigationTrigger \
  "$LOG_DIR/ui-initial.xml" || true)"
read -r TRIGGER_X TRIGGER_Y <<<"$TRIGGER_CENTER"
if [[ -z "${TRIGGER_Y:-}" ]]; then
  echo "Client2 Central Brain navigation trigger was not visible" >&2
  exit 1
fi

"${ADB_DEVICE[@]}" shell input tap "$TRIGGER_X" "$TRIGGER_Y"
wait_for_resource_state \
  centralBrainColdButton visible "$LOG_DIR/ui-menu-shown.xml"
if ! grep -q 'centralBrainPanel[^>]\+bounds="\[1264,160\]\[1888,1048\]"' \
    "$LOG_DIR/ui-menu-shown.xml"; then
  cat "$LOG_DIR/ui-menu-shown.xml" >&2
  echo "Client2 AIOS panel is outside the 1920x1080 safe frame" >&2
  exit 1
fi
for marker in \
  'centralBrainIntentTab' \
  'centralBrainPlanTab' \
  'centralBrainExecutionTab' \
  'centralBrainResultTab' \
  'text="UNAVAILABLE"' \
  'text="UNKNOWN · 受限"'; do
  if ! grep -Fq "$marker" "$LOG_DIR/ui-menu-shown.xml"; then
    cat "$LOG_DIR/ui-menu-shown.xml" >&2
    echo "Client2 four-stage header missing marker: $marker" >&2
    exit 1
  fi
done
tap_resource centralBrainPlanTab "$LOG_DIR/ui-before-plan-tab.xml"
wait_for_resource_state \
  centralBrainPlanSummaryText visible "$LOG_DIR/ui-plan-tab.xml"
tap_resource centralBrainIntentTab "$LOG_DIR/ui-before-intent-tab.xml"
wait_for_resource_state \
  centralBrainColdButton visible "$LOG_DIR/ui-intent-tab.xml"

"${ADB_DEVICE[@]}" shell input tap "$TRIGGER_X" "$TRIGGER_Y"
wait_for_resource_state \
  centralBrainColdButton hidden "$LOG_DIR/ui-menu-toggle-hidden.xml"

"${ADB_DEVICE[@]}" shell input tap "$TRIGGER_X" "$TRIGGER_Y"
wait_for_resource_state \
  centralBrainColdButton visible "$LOG_DIR/ui-menu-shown-again.xml"

DISPLAY_SIZE="$("${ADB_DEVICE[@]}" shell wm size | tr -d '\r' | tail -n 1 \
  | sed -nE 's/.*: ([0-9]+)x([0-9]+)/\1 \2/p')"
read -r DISPLAY_WIDTH DISPLAY_HEIGHT <<<"$DISPLAY_SIZE"
if [[ -z "${DISPLAY_HEIGHT:-}" ]]; then
  echo "cannot determine display size for outside-panel dismissal" >&2
  exit 1
fi
OUTSIDE_X=$((DISPLAY_WIDTH / 6))
OUTSIDE_Y=$((DISPLAY_HEIGHT / 3))
"${ADB_DEVICE[@]}" shell input tap "$OUTSIDE_X" "$OUTSIDE_Y"
wait_for_resource_state \
  centralBrainColdButton hidden "$LOG_DIR/ui-outside-dismissed.xml"

"${ADB_DEVICE[@]}" shell input tap "$TRIGGER_X" "$TRIGGER_Y"
wait_for_resource_state \
  centralBrainColdButton visible "$LOG_DIR/ui-before.xml"

BUTTON_CENTER="$(node_center centralBrainColdButton \
  "$LOG_DIR/ui-before.xml" || true)"
read -r TAP_X TAP_Y <<<"$BUTTON_CENTER"
if [[ -z "${TAP_Y:-}" ]]; then
  echo "cannot parse Client2 cold button bounds" >&2
  exit 1
fi
"${ADB_DEVICE[@]}" shell input tap "$TAP_X" "$TAP_Y"

BINDER_LOG=""
for _ in {1..40}; do
  BINDER_LOG="$("${ADB_DEVICE[@]}" logcat -d \
    CbClient2Session:I CbClient2Hmi:I CentralBrainRuntime:I '*:S')"
  if grep -Fq 'client2_session_replay_complete=true' <<<"$BINDER_LOG"; then
    break
  fi
  sleep 0.25
done
printf '%s\n' "$BINDER_LOG" >"$LOG_DIR/binder-log.txt"

for marker in \
  'client2_session_transport_connected=true' \
  'client2_session_opened=true' \
  'client2_session_snapshot_received=true' \
  'client2_session_event_received=true' \
  'event_type=ScenarioRequested' \
  'event_sequence=1' \
  'client2_session_replay_complete=true' \
  'client2_hmi_replay_projected=true' \
  'cockpit_hmi_state_reducer_implemented=true' \
  'cockpit_hmi_lifecycle_owner_java=true' \
  'cockpit_hmi_four_stage_shell_implemented=true' \
  'cockpit_hmi_intent_first_primary=true' \
  'cockpit_hmi_device_drawer_scaffolded=true' \
  'cockpit_hvac_surface_implemented=true' \
  'cockpit_hvac_reducer_owned=true' \
  'cockpit_hvac_debounce_ms=300' \
  'cockpit_hvac_governed_manual_session=true' \
  'cockpit_hvac_reported_readback_available=false' \
  'cockpit_seat_surface_implemented=true' \
  'cockpit_seat_reducer_owned=true' \
  'cockpit_seat_debounce_ms=300' \
  'cockpit_seat_governed_manual_session=true' \
  'cockpit_seat_unknown_restricted_fail_closed=true' \
  'cockpit_seat_reported_readback_available=false' \
  'cockpit_execution_timeline_implemented=true' \
  'cockpit_execution_timeline_reducer_owned=true' \
  'cockpit_execution_typed_event_projection=true' \
  'cockpit_execution_plan_published=false' \
  'cockpit_execution_effect_dispatch_enabled=false' \
  'cockpit_execution_readback_available=false' \
  'cockpit_recovery_state_reducer_owned=true' \
  'cockpit_approval_response_service_published=false' \
  'cockpit_retry_service_published=false' \
  'cockpit_undo_service_published=false' \
  'cockpit_recovery_commands_enabled=false' \
  'client2_hmi_checkpoint_text_persisted=false' \
  'ui_scenario_id=care.cold' \
  'scenario_id=scene.comfort.cold.v1' \
  'session_event_transport_used=true' \
  'legacy_callback_compatibility=false' \
  'legacy_text_callback_authoritative=false' \
  'http_transport_used=false' \
  'service_dispatch_triggered=false' \
  'hardware_accessed=false'; do
  if ! grep -Fq "$marker" <<<"$BINDER_LOG"; then
    echo "$BINDER_LOG" >&2
    echo "Client2 Binder log missing marker: $marker" >&2
    exit 1
  fi
done

wait_for_resource_state \
  centralBrainPlanSummaryText visible "$LOG_DIR/ui-plan-after-session.xml"
tap_resource centralBrainHvacDetailButton "$LOG_DIR/ui-before-hvac-drawer.xml"
wait_for_resource_state \
  centralBrainHvacDesiredText visible "$LOG_DIR/ui-hvac-drawer.xml"

HVAC_UP_CENTER="$(node_center centralBrainHvacTemperatureUpButton \
  "$LOG_DIR/ui-hvac-drawer.xml" || true)"
read -r HVAC_UP_X HVAC_UP_Y <<<"$HVAC_UP_CENTER"
if [[ -z "${HVAC_UP_Y:-}" ]]; then
  cat "$LOG_DIR/ui-hvac-drawer.xml" >&2
  echo "HVAC temperature stepper is not visible" >&2
  exit 1
fi
"${ADB_DEVICE[@]}" logcat -c
for _ in {1..3}; do
  "${ADB_DEVICE[@]}" shell input tap "$HVAC_UP_X" "$HVAC_UP_Y"
  sleep 0.03
done

HVAC_LOG=""
for _ in {1..60}; do
  HVAC_LOG="$("${ADB_DEVICE[@]}" logcat -d \
    CbClient2Session:I CbClient2Hmi:I CentralBrainRuntime:I '*:S')"
  if grep -Fq 'ui_scenario_id=manual.hvac' <<<"$HVAC_LOG" \
      && grep -Fq 'client2_session_replay_complete=true' <<<"$HVAC_LOG"; then
    break
  fi
  sleep 0.1
done
printf '%s\n' "$HVAC_LOG" >"$LOG_DIR/hvac-log.txt"
for marker in \
  'cockpit_hvac_desired_changed=true' \
  'hvac_debounce_scheduled=true' \
  'cockpit_hvac_manual_session_submitted=true' \
  'hvac_parameter_logged=false' \
  'ui_scenario_id=manual.hvac' \
  'scenario_id=scene.manual.hvac.adjust.v1' \
  'hvac_manual_intent_governed_session=true' \
  'hvac_manual_bounded_parameter_wire=true' \
  'hvac_manual_typed_parameter_field=false' \
  'service_dispatch_triggered=false' \
  'hardware_accessed=false'; do
  if ! grep -Fq "$marker" <<<"$HVAC_LOG"; then
    echo "$HVAC_LOG" >&2
    echo "Client2 HVAC log missing marker: $marker" >&2
    exit 1
  fi
done
if [[ "$(grep -Fc 'cockpit_hvac_manual_session_submitted=true' \
    <<<"$HVAC_LOG")" -ne 1 ]]; then
  echo "$HVAC_LOG" >&2
  echo "three HVAC inputs were not coalesced into exactly one governed Session" >&2
  exit 1
fi
if [[ "$(grep -F 'ui_scenario_id=manual.hvac' <<<"$HVAC_LOG" \
    | grep -Fc 'client2_session_opened=true')" -ne 1 ]]; then
  echo "$HVAC_LOG" >&2
  echo "coalesced HVAC request did not complete governed Session admission" >&2
  exit 1
fi
dump_ui "$LOG_DIR/ui-hvac-after-debounce.xml"
for marker in \
  'text="24.0 C"' \
  'Reported：UNAVAILABLE' \
  'Source：UNAVAILABLE' \
  'Quality：NO_EVIDENCE' \
  'Effect：REQUESTED' \
  'SESSION ACCEPTED'; do
  if ! grep -Fq "$marker" "$LOG_DIR/ui-hvac-after-debounce.xml"; then
    cat "$LOG_DIR/ui-hvac-after-debounce.xml" >&2
    echo "Client2 HVAC UI missing desired/readback marker: $marker" >&2
    exit 1
  fi
done
tap_resource centralBrainDrawerCloseButton "$LOG_DIR/ui-before-drawer-close.xml"
wait_for_resource_state \
  centralBrainHvacDesiredText hidden "$LOG_DIR/ui-drawer-closed.xml"
tap_resource centralBrainSeatDetailButton "$LOG_DIR/ui-before-seat-drawer.xml"
wait_for_resource_state \
  centralBrainSeatDesiredText visible "$LOG_DIR/ui-seat-drawer.xml"

SEAT_HEAT_UP_CENTER="$(node_center centralBrainSeatHeatUpButton \
  "$LOG_DIR/ui-seat-drawer.xml" || true)"
SEAT_VENT_UP_CENTER="$(node_center centralBrainSeatVentilationUpButton \
  "$LOG_DIR/ui-seat-drawer.xml" || true)"
read -r SEAT_HEAT_UP_X SEAT_HEAT_UP_Y <<<"$SEAT_HEAT_UP_CENTER"
read -r SEAT_VENT_UP_X SEAT_VENT_UP_Y <<<"$SEAT_VENT_UP_CENTER"
if [[ -z "${SEAT_HEAT_UP_Y:-}" || -z "${SEAT_VENT_UP_Y:-}" ]]; then
  cat "$LOG_DIR/ui-seat-drawer.xml" >&2
  echo "Seat heat/vent controls are not visible" >&2
  exit 1
fi
"${ADB_DEVICE[@]}" logcat -c
"${ADB_DEVICE[@]}" shell input tap "$SEAT_HEAT_UP_X" "$SEAT_HEAT_UP_Y"
sleep 0.03
"${ADB_DEVICE[@]}" shell input tap "$SEAT_VENT_UP_X" "$SEAT_VENT_UP_Y"

SEAT_LOG=""
for _ in {1..60}; do
  SEAT_LOG="$("${ADB_DEVICE[@]}" logcat -d \
    CbClient2Session:I CbClient2Hmi:I CentralBrainRuntime:I '*:S')"
  if grep -Fq 'ui_scenario_id=manual.seat' <<<"$SEAT_LOG" \
      && grep -Fq 'client2_session_replay_complete=true' <<<"$SEAT_LOG"; then
    break
  fi
  sleep 0.1
done
printf '%s\n' "$SEAT_LOG" >"$LOG_DIR/seat-log.txt"
for marker in \
  'cockpit_seat_desired_changed=true' \
  'seat_debounce_scheduled=true' \
  'cockpit_seat_manual_session_submitted=true' \
  'seat_parameter_logged=false' \
  'ui_scenario_id=manual.seat' \
  'scenario_id=scene.manual.seat.adjust.v1' \
  'seat_manual_intent_governed_session=true' \
  'seat_manual_bounded_parameter_wire=true' \
  'seat_manual_typed_parameter_field=false' \
  'service_dispatch_triggered=false' \
  'hardware_accessed=false'; do
  if ! grep -Fq "$marker" <<<"$SEAT_LOG"; then
    echo "$SEAT_LOG" >&2
    echo "Client2 Seat log missing marker: $marker" >&2
    exit 1
  fi
done
if [[ "$(grep -Fc 'cockpit_seat_manual_session_submitted=true' \
    <<<"$SEAT_LOG")" -ne 1 ]]; then
  echo "$SEAT_LOG" >&2
  echo "Seat heat/vent inputs were not coalesced into exactly one governed Session" >&2
  exit 1
fi
if [[ "$(grep -F 'ui_scenario_id=manual.seat' <<<"$SEAT_LOG" \
    | grep -Fc 'client2_session_opened=true')" -ne 1 ]]; then
  echo "$SEAT_LOG" >&2
  echo "coalesced Seat request did not complete governed Session admission" >&2
  exit 1
fi
dump_ui "$LOG_DIR/ui-seat-after-debounce.xml"
for marker in \
  'text="HEAT 0"' \
  'text="VENT 1"'; do
  if ! grep -Fq "$marker" "$LOG_DIR/ui-seat-after-debounce.xml"; then
    cat "$LOG_DIR/ui-seat-after-debounce.xml" >&2
    echo "Client2 Seat UI missing heat/vent mutex marker: $marker" >&2
    exit 1
  fi
done

SEAT_RECLINE_CENTER=""
for _ in {1..4}; do
  "${ADB_DEVICE[@]}" shell input swipe 1700 900 1700 420 250
  sleep 0.1
  dump_ui "$LOG_DIR/ui-seat-scrolled.xml"
  SEAT_RECLINE_CENTER="$(node_center centralBrainSeatReclineUpButton \
    "$LOG_DIR/ui-seat-scrolled.xml" || true)"
  [[ -n "$SEAT_RECLINE_CENTER" ]] && break
done
read -r SEAT_RECLINE_X SEAT_RECLINE_Y <<<"$SEAT_RECLINE_CENTER"
if [[ -z "${SEAT_RECLINE_Y:-}" ]]; then
  cat "$LOG_DIR/ui-seat-scrolled.xml" >&2
  echo "Seat recline control is not reachable in the fixed drawer" >&2
  exit 1
fi
"${ADB_DEVICE[@]}" logcat -c
"${ADB_DEVICE[@]}" shell input tap "$SEAT_RECLINE_X" "$SEAT_RECLINE_Y"
sleep 0.5
SEAT_RESTRICT_LOG="$("${ADB_DEVICE[@]}" logcat -d CbClient2Hmi:I CbClient2Session:I '*:S')"
printf '%s\n' "$SEAT_RESTRICT_LOG" >"$LOG_DIR/seat-restriction-log.txt"
for marker in \
  'cockpit_seat_position_request_blocked=true' \
  'seat_safety_decision=DENIED_UNKNOWN_CONTEXT' \
  'seat_dispatch_triggered=false'; do
  if ! grep -Fq "$marker" <<<"$SEAT_RESTRICT_LOG"; then
    echo "$SEAT_RESTRICT_LOG" >&2
    echo "Client2 Seat restriction log missing marker: $marker" >&2
    exit 1
  fi
done
if grep -Fq 'cockpit_seat_manual_session_submitted=true' <<<"$SEAT_RESTRICT_LOG"; then
  echo "$SEAT_RESTRICT_LOG" >&2
  echo "restricted driver recline incorrectly submitted a governed Session" >&2
  exit 1
fi
dump_ui "$LOG_DIR/ui-seat-restricted.xml"
for marker in \
  'text="0 deg"' \
  'Driving：UNKNOWN_RESTRICTED' \
  'Decision：DENIED_UNKNOWN_CONTEXT' \
  'Reported：UNAVAILABLE' \
  'Effect：REQUESTED'; do
  if ! grep -Fq "$marker" "$LOG_DIR/ui-seat-restricted.xml"; then
    cat "$LOG_DIR/ui-seat-restricted.xml" >&2
    echo "Client2 Seat UI missing restriction/readback marker: $marker" >&2
    exit 1
  fi
done
tap_resource centralBrainDrawerCloseButton "$LOG_DIR/ui-before-seat-drawer-close.xml"
wait_for_resource_state \
  centralBrainSeatDesiredText hidden "$LOG_DIR/ui-seat-drawer-closed.xml"
tap_resource centralBrainExecutionTab "$LOG_DIR/ui-before-execution-tab.xml"
wait_for_resource_state \
  centralBrainExecutionSummaryText visible "$LOG_DIR/ui-execution-tab.xml"
for marker in \
  'centralBrainTimelineIntentText' \
  '01 Intent：SESSION ACCEPTED' \
  '02 Context：UNAVAILABLE' \
  '03 Plan：NOT PUBLISHED' \
  '04 Policy：SESSION ACCEPTED'; do
  if ! grep -Fq "$marker" "$LOG_DIR/ui-execution-tab.xml"; then
    cat "$LOG_DIR/ui-execution-tab.xml" >&2
    echo "Client2 execution timeline missing upper marker: $marker" >&2
    exit 1
  fi
done
for _ in {1..4}; do
  "${ADB_DEVICE[@]}" shell input swipe 1700 900 1700 400 250
  sleep 0.1
  dump_ui "$LOG_DIR/ui-execution-scrolled.xml"
  if grep -Fq '07 Readback：UNAVAILABLE' "$LOG_DIR/ui-execution-scrolled.xml"; then
    break
  fi
done
for marker in \
  '05 Graph：NOT WIRED' \
  '06 Effect：NOT DISPATCHED' \
  '07 Readback：UNAVAILABLE' \
  'Media STOP：UNAVAILABLE · Navigation CANCEL：UNAVAILABLE' \
  'ScenarioRequested · REQUESTED'; do
  if ! grep -Fq "$marker" "$LOG_DIR/ui-execution-scrolled.xml"; then
    cat "$LOG_DIR/ui-execution-scrolled.xml" >&2
    echo "Client2 execution timeline missing lower marker: $marker" >&2
    exit 1
  fi
done
for _ in {1..5}; do
  "${ADB_DEVICE[@]}" shell input swipe 1700 900 1700 360 250
  sleep 0.1
  dump_ui "$LOG_DIR/ui-recovery-scrolled.xml"
  if grep -Fq 'centralBrainApprovalStateText' "$LOG_DIR/ui-recovery-scrolled.xml" \
      && grep -Fq 'centralBrainUndoButton' "$LOG_DIR/ui-recovery-scrolled.xml"; then
    break
  fi
done
for marker in \
  'Approval：UNAVAILABLE' \
  'Reason：UNAVAILABLE · Target：UNAVAILABLE' \
  'Expiry：UNAVAILABLE · Response service：NOT PUBLISHED' \
  'Outcome evidence：NO EVIDENCE' \
  'VERIFIED 0 · FAILED 0 · INCONCLUSIVE 0' \
  'Compensation：UNAVAILABLE · Undo handle：NOT PUBLISHED'; do
  if ! grep -Fq "$marker" "$LOG_DIR/ui-recovery-scrolled.xml"; then
    cat "$LOG_DIR/ui-recovery-scrolled.xml" >&2
    echo "Client2 recovery UX missing fail-closed marker: $marker" >&2
    exit 1
  fi
done
for resource_id in \
  centralBrainApproveButton \
  centralBrainRejectButton \
  centralBrainRetryButton \
  centralBrainUndoButton; do
  node="$(grep -o "<node[^>]*${resource_id}[^>]*/>" \
    "$LOG_DIR/ui-recovery-scrolled.xml" | head -n 1 || true)"
  if [[ -z "$node" || "$node" != *'enabled="false"'* ]]; then
    cat "$LOG_DIR/ui-recovery-scrolled.xml" >&2
    echo "Client2 recovery command must remain visible and disabled: $resource_id" >&2
    exit 1
  fi
done

"${ADB_DEVICE[@]}" shell input tap "$OUTSIDE_X" "$OUTSIDE_Y"
wait_for_resource_state \
  centralBrainApprovalStateText hidden "$LOG_DIR/ui-recovery-outside-dismissed.xml"
"${ADB_DEVICE[@]}" shell input tap "$TRIGGER_X" "$TRIGGER_Y"
wait_for_resource_state \
  centralBrainApprovalStateText visible "$LOG_DIR/ui-recovery-restored.xml"
if ! grep -Fq 'Outcome evidence：NO EVIDENCE' "$LOG_DIR/ui-recovery-restored.xml"; then
  cat "$LOG_DIR/ui-recovery-restored.xml" >&2
  echo "outside dismiss did not preserve Client2 recovery state" >&2
  exit 1
fi
tap_resource centralBrainResultTab "$LOG_DIR/ui-before-result-tab.xml"
wait_for_resource_state \
  centralBrainResultSummaryText visible "$LOG_DIR/ui-result-tab.xml"

"${ADB_DEVICE[@]}" shell uiautomator dump "$DEVICE_UI_XML" >/dev/null
"${ADB_DEVICE[@]}" shell cat "$DEVICE_UI_XML" >"$LOG_DIR/ui-after.xml"
if ! grep -Fq 'text="Scenario accepted; execution is not enabled"' \
    "$LOG_DIR/ui-after.xml"; then
  cat "$LOG_DIR/ui-after.xml" >&2
  echo "Client2 UI did not render the immutable HMI Session projection" >&2
  exit 1
fi

ln -sfn "$LOG_DIR" "$ROOT_DIR/logs/test/client2-central-brain-binder/latest"
printf '%s\n' \
  "device_serial=$SERIAL" \
  "android_api=$SDK" \
  "device_abi=$ABI" \
  "client2_signature_permission_granted=true" \
  "runtime_client2_signer_parity=true" \
  "client2_secondary_sdk_dex_present=true" \
  "client2_session_transport_connected=true" \
  "client2_session_opened=true" \
  "client2_session_snapshot_received=true" \
  "client2_session_event_received=true" \
  "client2_session_event_sequence_verified=true" \
  "client2_session_replay_complete=true" \
  "client2_hmi_replay_projected=true" \
  "cockpit_hmi_state_reducer_implemented=true" \
  "cockpit_hmi_lifecycle_owner_java=true" \
  "cockpit_hmi_four_stage_shell_verified=true" \
  "cockpit_hmi_safe_frame_1920x1080_verified=true" \
  "cockpit_hmi_device_drawer_verified=true" \
  "cockpit_hvac_surface_implemented=true" \
  "cockpit_hvac_controls_verified=true" \
  "cockpit_hvac_debounce_verified=true" \
  "cockpit_hvac_manual_session_admission_verified=true" \
  "cockpit_hvac_desired_reported_separation_verified=true" \
  "cockpit_hvac_reported_readback_available=false" \
  "cockpit_hvac_verified_before_readback=false" \
  "hvac_manual_typed_parameter_field=false" \
  "cockpit_seat_surface_implemented=true" \
  "cockpit_seat_controls_verified=true" \
  "cockpit_seat_heat_vent_mutex_verified=true" \
  "cockpit_seat_unknown_restricted_fail_closed=true" \
  "cockpit_seat_manual_session_admission_verified=true" \
  "cockpit_seat_desired_reported_separation_verified=true" \
  "cockpit_seat_reported_readback_available=false" \
  "cockpit_seat_verified_before_readback=false" \
  "seat_manual_typed_parameter_field=false" \
  "cockpit_execution_timeline_verified=true" \
  "cockpit_execution_plan_not_published_verified=true" \
  "cockpit_execution_graph_not_wired_verified=true" \
  "cockpit_execution_effect_not_dispatched_verified=true" \
  "cockpit_execution_readback_unavailable_verified=true" \
  "cockpit_execution_media_navigation_projection_verified=true" \
  "cockpit_execution_typed_event_trace_verified=true" \
  "cockpit_recovery_state_reducer_owned=true" \
  "cockpit_approval_details_fail_closed_verified=true" \
  "cockpit_partial_outcome_projection_verified=true" \
  "cockpit_compensation_projection_verified=true" \
  "cockpit_recovery_commands_disabled_verified=true" \
  "cockpit_recovery_outside_dismiss_preserved=true" \
  "cockpit_approval_response_service_published=false" \
  "cockpit_retry_service_published=false" \
  "cockpit_undo_service_published=false" \
  "client2_hmi_checkpoint_text_persisted=false" \
  "legacy_text_callback_authoritative=false" \
  "client2_ui_session_projection_verified=true" \
  "client2_panel_initially_hidden=true" \
  "client2_navigation_toggle_show_verified=true" \
  "client2_navigation_toggle_hide_verified=true" \
  "client2_outside_tap_dismiss_verified=true" \
  "client2_identity_resolved=true" \
  "client2_capability_policy_allowed=true" \
  "client2_signer_migration_performed=$SIGNER_MIGRATION_PERFORMED" \
  "automatic_uninstall_enabled=false" \
  "session_event_transport_used=true" \
  "http_transport_used=false" \
  "scenario_execution_enabled=false" \
  "service_dispatch_triggered=false" \
  "hardware_accessed=false" \
  "driver_development_triggered=false" \
  "virtualization_development_triggered=false" \
  "test_logs=$LOG_DIR"
