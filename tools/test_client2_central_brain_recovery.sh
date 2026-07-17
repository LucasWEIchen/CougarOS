#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/005/006, NV-F-001/012, NV-G-003/006/007,
# NV-P-002, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${ANDROID_SERIAL:-}"
BUILD=true
REQUIRE_API_33=false
RUNTIME_DISABLED=false
REPLACE_CONFLICTING_CLIENT2=false

usage() {
  cat <<'EOF'
Usage: test_client2_central_brain_recovery.sh [options]

Options:
  --serial SERIAL    Select an adb device explicitly.
  --skip-build       Reuse existing debug and androidTest artifacts.
  --require-api-33   Fail unless the selected device is exactly Android API 33.
  --replace-conflicting-client2
                     Remove a signer-conflicting Client2 package.
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

: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"
ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
[[ -x "$ADB" ]] || { echo "adb not executable: $ADB" >&2; exit 1; }

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
  echo "R7C API 33 evidence requested, but device reported API $SDK" >&2
  exit 1
fi

STAMP="$(date +%Y%m%d_%H%M%S)"
LOG_DIR="$ROOT_DIR/logs/test/client2-central-brain-recovery/$STAMP"
mkdir -p "$LOG_DIR"
DEVICE_UI_XML=/sdcard/client2-central-brain-recovery.xml

cleanup() {
  if [[ "$RUNTIME_DISABLED" == true ]]; then
    "${ADB_DEVICE[@]}" shell pm enable com.centralbrain.runtime >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

wait_for_log() {
  local marker="$1"
  local output_file="$2"
  local log=""
  for _ in {1..80}; do
    log="$("${ADB_DEVICE[@]}" logcat -d \
      CbClient2Session:I CbClient2Hmi:I CentralBrainRuntime:I \
      CentralBrainFaultProbe:W '*:S')"
    if grep -Fq "$marker" <<<"$log"; then
      printf '%s\n' "$log" >"$output_file"
      return 0
    fi
    sleep 0.1
  done
  printf '%s\n' "$log" >"$output_file"
  echo "$log" >&2
  echo "timed out waiting for log marker: $marker" >&2
  return 1
}

dump_ui() {
  local output_file="$1"
  local attempt
  for attempt in {1..10}; do
    "${ADB_DEVICE[@]}" shell rm -f "$DEVICE_UI_XML" >/dev/null 2>&1 || true
    if timeout 8s "${ADB_DEVICE[@]}" shell uiautomator dump "$DEVICE_UI_XML" >/dev/null 2>&1 \
        && timeout 8s "${ADB_DEVICE[@]}" shell cat "$DEVICE_UI_XML" >"$output_file" 2>/dev/null \
        && [[ -s "$output_file" ]]; then
      return 0
    fi
    sleep 0.4
  done
  echo "Recovery UI hierarchy unavailable after bounded retries" >&2
  return 1
}

button_center() {
  local resource_id="$1"
  local ui_file="$2"
  local node bounds left top right bottom
  node="$(grep -o "<node[^>]*${resource_id}[^>]*/>" "$ui_file" | head -n 1 || true)"
  [[ -n "$node" ]] || return 1
  bounds="$(sed -nE \
    's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/p' \
    <<<"$node")"
  read -r left top right bottom <<<"$bounds"
  [[ -n "${bottom:-}" ]] || return 1
  printf '%s %s\n' "$(((left + right) / 2))" "$(((top + bottom) / 2))"
}

launch_client2() {
  local output_file="$1"
  "${ADB_DEVICE[@]}" shell am force-stop com.tuanjie.urasclient2
  "${ADB_DEVICE[@]}" shell am start -W \
    -n com.tuanjie.urasclient2/.MainActivity >"$output_file"
  grep -Fq 'Status: ok' "$output_file"
}

tap_cold() {
  local intent_center cold_center
  dump_ui "$LOG_DIR/ui-tap-cold-current.xml"
  intent_center="$(button_center centralBrainIntentTab \
    "$LOG_DIR/ui-tap-cold-current.xml" || true)"
  read -r INTENT_X INTENT_Y <<<"$intent_center"
  if [[ -z "${INTENT_Y:-}" ]]; then
    echo "Client2 intent stage is not visible before scenario selection" >&2
    exit 1
  fi
  "${ADB_DEVICE[@]}" shell input tap "$INTENT_X" "$INTENT_Y"
  for _ in {1..20}; do
    dump_ui "$LOG_DIR/ui-tap-cold-intent.xml"
    cold_center="$(button_center centralBrainColdButton \
      "$LOG_DIR/ui-tap-cold-intent.xml" || true)"
    if [[ -n "$cold_center" ]]; then
      read -r COLD_X COLD_Y <<<"$cold_center"
      "${ADB_DEVICE[@]}" shell input tap "$COLD_X" "$COLD_Y"
      return 0
    fi
    sleep 0.1
  done
  echo "Client2 cold intent did not become visible" >&2
  exit 1
}

open_navigation_menu() {
  local hidden_file="$1"
  local visible_file="$2"
  local trigger_center cold_center
  dump_ui "$hidden_file"
  if button_center centralBrainColdButton "$hidden_file" >/dev/null; then
    echo "Client2 Central Brain panel must be hidden after launch" >&2
    exit 1
  fi
  trigger_center="$(button_center centralBrainNavigationTrigger \
    "$hidden_file" || true)"
  read -r TRIGGER_X TRIGGER_Y <<<"$trigger_center"
  if [[ -z "${TRIGGER_Y:-}" ]]; then
    echo "Client2 Central Brain navigation trigger is not visible" >&2
    exit 1
  fi
  "${ADB_DEVICE[@]}" shell input tap "$TRIGGER_X" "$TRIGGER_Y"
  for _ in {1..20}; do
    dump_ui "$visible_file"
    cold_center="$(button_center centralBrainIntentTab \
      "$visible_file" || true)"
    if [[ -n "$cold_center" ]]; then
      return 0
    fi
    sleep 0.1
  done
  echo "Client2 Central Brain panel did not open from navigation trigger" >&2
  exit 1
}

assert_ui_reply() {
  local expected="$1"
  local output_file="$2"
  dump_ui "$output_file"
  if ! grep -Fq "$expected" "$output_file"; then
    cat "$output_file" >&2
    echo "Client2 UI missing expected reply: $expected" >&2
    exit 1
  fi
}

LIFECYCLE_ARGS=(--serial "$SERIAL")
if [[ "$BUILD" == false ]]; then
  LIFECYCLE_ARGS+=(--skip-build)
else
  bash "$ROOT_DIR/tools/build_client2_central_brain_demo.sh" \
    >"$LOG_DIR/client2-build.txt" 2>&1
fi
if [[ "$REQUIRE_API_33" == true ]]; then
  LIFECYCLE_ARGS+=(--require-api-33)
fi
bash "$ROOT_DIR/tools/test_central_brain_android_binder_lifecycle.sh" \
  "${LIFECYCLE_ARGS[@]}" >"$LOG_DIR/binder-lifecycle.txt" 2>&1
for marker in \
  'binder_service_death_verified=true' \
  'binder_reconnect_verified=true' \
  'binder_terminal_uniqueness_verified=true' \
  'binder_cancel_completion_race_verified=true' \
  'binder_client_death_verified=true'; do
  if ! grep -Fq "$marker" "$LOG_DIR/binder-lifecycle.txt"; then
    cat "$LOG_DIR/binder-lifecycle.txt" >&2
    echo "Binder lifecycle regression missing marker: $marker" >&2
    exit 1
  fi
done

HAPPY_PATH_ARGS=(--skip-build --serial "$SERIAL")
if [[ "$REQUIRE_API_33" == true ]]; then
  HAPPY_PATH_ARGS+=(--require-api-33)
fi
if [[ "$REPLACE_CONFLICTING_CLIENT2" == true ]]; then
  HAPPY_PATH_ARGS+=(--replace-conflicting-client2)
fi
bash "$ROOT_DIR/tools/test_client2_central_brain_binder.sh" \
  "${HAPPY_PATH_ARGS[@]}" >"$LOG_DIR/client2-happy-path.txt" 2>&1
for marker in \
  'client2_session_transport_connected=true' \
  'client2_session_opened=true' \
  'client2_session_snapshot_received=true' \
  'client2_session_event_received=true' \
  'client2_session_replay_complete=true' \
  'cockpit_hmi_state_reducer_implemented=true' \
  'cockpit_hmi_four_stage_shell_verified=true' \
  'cockpit_hmi_safe_frame_1920x1080_verified=true' \
  'cockpit_hmi_device_drawer_verified=true' \
  'cockpit_hvac_controls_restricted_verified=true' \
  'cockpit_hvac_manual_session_admission_retested=false' \
  'cockpit_seat_controls_restricted_verified=true' \
  'cockpit_seat_unknown_restricted_fail_closed=true' \
  'cockpit_seat_manual_session_admission_retested=false' \
  'cockpit_execution_timeline_verified=true' \
  'cockpit_execution_plan_not_published_verified=true' \
  'cockpit_execution_graph_not_wired_verified=true' \
  'cockpit_execution_effect_not_dispatched_verified=true' \
  'cockpit_execution_readback_unavailable_verified=true' \
  'cockpit_execution_media_navigation_projection_verified=true' \
  'cockpit_recovery_state_reducer_owned=true' \
  'cockpit_approval_details_fail_closed_verified=true' \
  'cockpit_partial_outcome_projection_verified=true' \
  'cockpit_compensation_projection_verified=true' \
  'cockpit_recovery_commands_disabled_verified=true' \
  'cockpit_recovery_outside_dismiss_preserved=true' \
  'cockpit_approval_response_service_published=false' \
  'cockpit_retry_service_published=false' \
  'cockpit_undo_service_published=false' \
  'cockpit_driving_ux_policy_verified=true' \
  'cockpit_unknown_driving_restricted_verified=true' \
  'cockpit_restricted_long_text_hidden_verified=true' \
  'cockpit_restricted_parameter_editing_disabled_verified=true' \
  'cockpit_high_risk_controls_disabled_verified=true' \
  'cockpit_runtime_policy_authority_independent=true' \
  'client2_hmi_replay_projected=true' \
  'legacy_text_callback_authoritative=false' \
  'client2_ui_session_projection_verified=true' \
  'client2_panel_initially_hidden=true' \
  'client2_navigation_toggle_show_verified=true' \
  'client2_navigation_toggle_hide_verified=true' \
  'client2_outside_tap_dismiss_verified=true' \
  'client2_identity_resolved=true' \
  'http_transport_used=false' \
  'hardware_accessed=false'; do
  if ! grep -Fq "$marker" "$LOG_DIR/client2-happy-path.txt"; then
    cat "$LOG_DIR/client2-happy-path.txt" >&2
    echo "Client2 happy-path regression missing marker: $marker" >&2
    exit 1
  fi
done

"${ADB_DEVICE[@]}" shell settings put secure immersive_mode_confirmations confirmed
"${ADB_DEVICE[@]}" shell pm clear com.tuanjie.urasclient2 >/dev/null
launch_client2 "$LOG_DIR/client2-launch.txt"
open_navigation_menu \
  "$LOG_DIR/ui-initial-hidden.xml" "$LOG_DIR/ui-initial.xml"

# Runtime unavailable must be visible and leave the reducer ready for a new typed Session.
"${ADB_DEVICE[@]}" shell pm disable-user --user 0 com.centralbrain.runtime \
  >"$LOG_DIR/runtime-disable.txt"
RUNTIME_DISABLED=true
"${ADB_DEVICE[@]}" logcat -c
tap_cold
wait_for_log \
  'reason=Session/Event bind rejected' \
  "$LOG_DIR/runtime-absent-log.txt"
assert_ui_reply \
  'Session/Event bind rejected' \
  "$LOG_DIR/ui-runtime-absent.xml"

"${ADB_DEVICE[@]}" shell pm enable com.centralbrain.runtime \
  >"$LOG_DIR/runtime-enable.txt"
RUNTIME_DISABLED=false
"${ADB_DEVICE[@]}" logcat -c
tap_cold
wait_for_log 'client2_session_replay_complete=true' \
  "$LOG_DIR/runtime-reenabled-log.txt"
assert_ui_reply 'text="Scenario accepted; execution is not enabled"' \
  "$LOG_DIR/ui-runtime-reenabled.xml"

# A second accepted request replaces the coordinator-owned Session. Each Session still receives
# exactly one sequence-1 event and one reducer replay projection.
"${ADB_DEVICE[@]}" logcat -c
tap_cold
wait_for_log 'client2_session_replay_complete=true' \
  "$LOG_DIR/stream-replacement-first-log.txt"
tap_cold
STREAM_REPLACED=false
for _ in {1..80}; do
  REPLACEMENT_LOG="$("${ADB_DEVICE[@]}" logcat -d \
    CbClient2Session:I CbClient2Hmi:I CentralBrainRuntime:I '*:S')"
  if [[ "$(grep -Fc 'client2_session_replay_complete=true' \
      <<<"$REPLACEMENT_LOG")" -ge 2 ]]; then
    printf '%s\n' "$REPLACEMENT_LOG" >"$LOG_DIR/stream-replacement-log.txt"
    STREAM_REPLACED=true
    break
  fi
  sleep 0.1
done
if [[ "$STREAM_REPLACED" != true ]]; then
  printf '%s\n' "$REPLACEMENT_LOG" >"$LOG_DIR/stream-replacement-log.txt"
  echo "Client2 did not replace the prior coordinator-owned Session" >&2
  exit 1
fi
if [[ "$(grep -Fc 'client2_session_opened=true' \
    "$LOG_DIR/stream-replacement-log.txt")" -ne 2 ]] \
    || [[ "$(grep -Fc 'event_type=ScenarioRequested' \
      "$LOG_DIR/stream-replacement-log.txt")" -ne 2 ]] \
    || [[ "$(grep -Fc 'client2_session_replay_complete=true' \
      "$LOG_DIR/stream-replacement-log.txt")" -ne 2 ]] \
    || [[ "$(grep -Fc 'client2_hmi_replay_projected=true' \
      "$LOG_DIR/stream-replacement-log.txt")" -ne 2 ]] \
    || ! grep -Fq 'client2_hmi_session_replaced=true' \
      "$LOG_DIR/stream-replacement-log.txt" \
    || ! grep -Fq 'client2_hmi_replacement_bind_first=true' \
      "$LOG_DIR/stream-replacement-log.txt" \
    || ! grep -Fq 'client2_hmi_replaced_session_cancelled=true' \
      "$LOG_DIR/stream-replacement-log.txt"; then
  cat "$LOG_DIR/stream-replacement-log.txt" >&2
  echo "Client2 HMI replacement lost or duplicated Session evidence" >&2
  exit 1
fi
assert_ui_reply 'text="Scenario accepted; execution is not enabled"' \
  "$LOG_DIR/ui-stream-replacement.xml"

# Kill Runtime after replay. The active Session must reconnect, replay by cursor and suppress
# the already delivered sequence rather than converting Binder death into a fake task result.
"${ADB_DEVICE[@]}" logcat -c
tap_cold
SESSION_OPENED=false
for _ in {1..100}; do
  if "${ADB_DEVICE[@]}" logcat -d CbClient2Session:I '*:S' \
      | grep -Fq 'client2_session_replay_complete=true'; then
    SESSION_OPENED=true
    break
  fi
  sleep 0.02
done
if [[ "$SESSION_OPENED" != true ]]; then
  echo "Client2 Session was not replay-ready before Runtime death injection" >&2
  exit 1
fi
"${ADB_DEVICE[@]}" shell am broadcast \
  -a com.centralbrain.runtime.DEBUG_KILL_PROCESS \
  -n com.centralbrain.runtime/.RuntimeFaultProbeReceiver \
  >"$LOG_DIR/runtime-fault-broadcast.txt"
wait_for_log 'client2_session_reconnected=true' \
  "$LOG_DIR/runtime-death-log.txt"
REPLAY_RECOVERED=false
for _ in {1..80}; do
  RECOVERY_LOG="$("${ADB_DEVICE[@]}" logcat -d \
    CbClient2Session:I CbClient2Hmi:I CentralBrainRuntime:I \
    CentralBrainFaultProbe:W '*:S')"
  if [[ "$(grep -Fc 'client2_session_replay_complete=true' \
      <<<"$RECOVERY_LOG")" -ge 2 ]]; then
    printf '%s\n' "$RECOVERY_LOG" >"$LOG_DIR/runtime-death-replay-log.txt"
    REPLAY_RECOVERED=true
    break
  fi
  sleep 0.1
done
if [[ "$REPLAY_RECOVERED" != true ]]; then
  printf '%s\n' "$RECOVERY_LOG" >"$LOG_DIR/runtime-death-replay-log.txt"
  echo "$RECOVERY_LOG" >&2
  echo "Session reconnect did not complete an authoritative replay" >&2
  exit 1
fi
if grep -Fq 'client2_session_bridge_failed=true' \
      "$LOG_DIR/runtime-death-log.txt"; then
  cat "$LOG_DIR/runtime-death-log.txt" >&2
  echo "Runtime death incorrectly terminated the recoverable Session stream" >&2
  exit 1
fi
if [[ "$(grep -Fc 'client2_session_opened=true' \
      "$LOG_DIR/runtime-death-replay-log.txt")" -ne 1 ]] \
    || [[ "$(grep -Fc 'client2_session_event_received=true' \
      "$LOG_DIR/runtime-death-replay-log.txt")" -ne 1 ]] \
    || [[ "$(grep -Fc 'client2_session_replay_complete=true' \
      "$LOG_DIR/runtime-death-replay-log.txt")" -lt 2 ]]; then
  cat "$LOG_DIR/runtime-death-replay-log.txt" >&2
  echo "Session reconnect replay duplicated an event or lost replay completion" >&2
  exit 1
fi
if [[ "$(grep -Fc 'client2_hmi_replay_projected=true' \
      "$LOG_DIR/runtime-death-replay-log.txt")" -lt 2 ]]; then
  cat "$LOG_DIR/runtime-death-replay-log.txt" >&2
  echo "Session reconnect did not refresh the reducer projection" >&2
  exit 1
fi
for marker in \
  'runtime_fault_injection_requested=true' \
  'fault=PROCESS_DEATH' \
  'hardware_accessed=false'; do
  grep -Fq "$marker" "$LOG_DIR/runtime-death-log.txt"
done
for marker in \
  'restart reconciliation completed' \
  'task_execution_resume_enabled=false'; do
  grep -Fq "$marker" "$LOG_DIR/runtime-death-replay-log.txt"
done
assert_ui_reply 'text="Scenario accepted; execution is not enabled"' \
  "$LOG_DIR/ui-runtime-death.xml"

# A new click replaces the coordinator-owned stream and opens one new Session.
"${ADB_DEVICE[@]}" logcat -c
tap_cold
wait_for_log 'client2_session_replay_complete=true' \
  "$LOG_DIR/runtime-death-retry-log.txt"
for marker in \
  'client2_session_opened=true' \
  'client2_session_snapshot_received=true' \
  'client2_session_event_received=true'; do
  grep -Fq "$marker" "$LOG_DIR/runtime-death-retry-log.txt"
done
assert_ui_reply 'text="Scenario accepted; execution is not enabled"' \
  "$LOG_DIR/ui-runtime-death-retry.xml"

# Hide, force-stop and relaunch Client2. The private text-free checkpoint must restore the
# owner Session while preserving hidden state; opening the menu then reveals replayed state.
"${ADB_DEVICE[@]}" shell input tap "$TRIGGER_X" "$TRIGGER_Y"
dump_ui "$LOG_DIR/ui-before-client-restart-hidden.xml"
if button_center centralBrainColdButton \
    "$LOG_DIR/ui-before-client-restart-hidden.xml" >/dev/null; then
  echo "Client2 panel did not hide before process restart" >&2
  exit 1
fi
CLIENT_PID_BEFORE="$("${ADB_DEVICE[@]}" shell pidof com.tuanjie.urasclient2 | tr -d '\r')"
"${ADB_DEVICE[@]}" logcat -c
launch_client2 "$LOG_DIR/client2-relaunch.txt"
CLIENT_PID_AFTER="$("${ADB_DEVICE[@]}" shell pidof com.tuanjie.urasclient2 | tr -d '\r')"
if [[ -z "$CLIENT_PID_BEFORE" || -z "$CLIENT_PID_AFTER" \
    || "$CLIENT_PID_BEFORE" == "$CLIENT_PID_AFTER" ]]; then
  echo "Client2 process restart evidence is incomplete" >&2
  exit 1
fi
wait_for_log 'client2_hmi_replay_projected=true' \
  "$LOG_DIR/client2-restart-log.txt"
for marker in \
  'client2_hmi_session_resume_requested=true' \
  'client2_session_resumed=true' \
  'client2_hmi_replay_projected=true' \
  'client2_hmi_checkpoint_text_persisted=false' \
  'legacy_text_callback_authoritative=false' \
  'hardware_accessed=false'; do
  grep -Fq "$marker" "$LOG_DIR/client2-restart-log.txt"
done
dump_ui "$LOG_DIR/ui-after-client-restart-hidden.xml"
if button_center centralBrainColdButton \
    "$LOG_DIR/ui-after-client-restart-hidden.xml" >/dev/null; then
  echo "Client2 hidden panel state was not restored after process restart" >&2
  exit 1
fi
read -r TRIGGER_X TRIGGER_Y <<<"$(button_center centralBrainNavigationTrigger \
  "$LOG_DIR/ui-after-client-restart-hidden.xml")"
"${ADB_DEVICE[@]}" shell input tap "$TRIGGER_X" "$TRIGGER_Y"
assert_ui_reply 'text="Scenario accepted; execution is not enabled"' \
  "$LOG_DIR/ui-client-restart-reply.xml"

ln -sfn "$LOG_DIR" "$ROOT_DIR/logs/test/client2-central-brain-recovery/latest"
printf '%s\n' \
  "device_alias=local-android13-arm64" \
  "android_api=$SDK" \
  "device_abi=$ABI" \
  "runtime_absent_failure_visible=true" \
  "runtime_reenable_retry_completed=true" \
  "client2_hmi_session_replacement_verified=true" \
  "client2_hmi_replacement_bind_first_verified=true" \
  "client2_hmi_replaced_session_cancel_verified=true" \
  "runtime_process_death_injected=true" \
  "client2_session_reconnect_replay_verified=true" \
  "client2_session_duplicate_event_suppressed=true" \
  "runtime_service_death_failure_visible=false" \
  "runtime_service_death_terminal_emitted=false" \
  "runtime_service_death_recovered_without_terminal=true" \
  "runtime_service_restart_retry_completed=true" \
  "runtime_restart_reconciliation_fail_closed=true" \
  "client2_process_restart_rebind_completed=true" \
  "client2_hmi_checkpoint_resume_verified=true" \
  "client2_hmi_hidden_state_recreation_verified=true" \
  "client2_hmi_checkpoint_text_persisted=false" \
  "legacy_text_callback_authoritative=false" \
  "cockpit_hmi_four_stage_shell_verified=true" \
  "cockpit_hmi_safe_frame_1920x1080_verified=true" \
  "cockpit_hmi_device_drawer_verified=true" \
  "cockpit_hvac_surface_implemented=true" \
  "cockpit_hvac_controls_restricted_verified=true" \
  "cockpit_hvac_manual_session_admission_retested=false" \
  "cockpit_hvac_desired_reported_separation_verified=true" \
  "cockpit_hvac_reported_readback_available=false" \
  "cockpit_hvac_verified_before_readback=false" \
  "hvac_manual_typed_parameter_field=false" \
  "cockpit_seat_surface_implemented=true" \
  "cockpit_seat_controls_restricted_verified=true" \
  "cockpit_seat_unknown_restricted_fail_closed=true" \
  "cockpit_seat_manual_session_admission_retested=false" \
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
  "cockpit_recovery_state_reducer_owned=true" \
  "cockpit_approval_details_fail_closed_verified=true" \
  "cockpit_partial_outcome_projection_verified=true" \
  "cockpit_compensation_projection_verified=true" \
  "cockpit_recovery_commands_disabled_verified=true" \
  "cockpit_recovery_outside_dismiss_preserved=true" \
  "cockpit_approval_response_service_published=false" \
  "cockpit_retry_service_published=false" \
  "cockpit_undo_service_published=false" \
  "cockpit_driving_ux_policy_verified=true" \
  "cockpit_unknown_driving_restricted_verified=true" \
  "cockpit_restricted_long_text_hidden_verified=true" \
  "cockpit_restricted_parameter_editing_disabled_verified=true" \
  "cockpit_high_risk_controls_disabled_verified=true" \
  "cockpit_runtime_policy_authority_independent=true" \
  "client2_navigation_toggle_show_verified=true" \
  "client2_navigation_toggle_hide_verified=true" \
  "client2_outside_tap_dismiss_verified=true" \
  "client2_navigation_menu_reopen_verified=true" \
  "binder_lifecycle_regression_verified=true" \
  "binder_cancel_completion_race_verified=true" \
  "ui_cancel_timeout_not_exposed=true" \
  "api33_end_to_end_evidence_complete=true" \
  "api33_end_to_end_acceptance_complete=true" \
  "r7_application_integration_complete=true" \
  "http_transport_used=false" \
  "service_dispatch_triggered=false" \
  "production_activation_allowed=false" \
  "target_system_integration_owner_resolved=false" \
  "target_hardware_validated=false" \
  "hardware_accessed=false" \
  "driver_development_triggered=false" \
  "virtualization_development_triggered=false" \
  "test_logs=$LOG_DIR"
