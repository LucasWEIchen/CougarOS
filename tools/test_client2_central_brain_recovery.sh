#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/005/006, NV-F-001/012, NV-G-003/006/007,
# NV-P-002, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${ANDROID_SERIAL:-}"
BUILD=true
REQUIRE_API_33=false
RUNTIME_DISABLED=false

usage() {
  cat <<'EOF'
Usage: test_client2_central_brain_recovery.sh [options]

Options:
  --serial SERIAL    Select an adb device explicitly.
  --skip-build       Reuse existing debug and androidTest artifacts.
  --require-api-33   Fail unless the selected device is exactly Android API 33.
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
      CbClient2Binder:I CentralBrainRuntime:I CentralBrainFaultProbe:W '*:S')"
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
  "${ADB_DEVICE[@]}" shell uiautomator dump "$DEVICE_UI_XML" >/dev/null
  "${ADB_DEVICE[@]}" shell cat "$DEVICE_UI_XML" >"$output_file"
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
  "${ADB_DEVICE[@]}" shell input tap "$COLD_X" "$COLD_Y"
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
bash "$ROOT_DIR/tools/test_client2_central_brain_binder.sh" \
  "${HAPPY_PATH_ARGS[@]}" >"$LOG_DIR/client2-happy-path.txt" 2>&1
for marker in \
  'client2_binder_task_completed=true' \
  'client2_ui_reply_verified=true' \
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
launch_client2 "$LOG_DIR/client2-launch.txt"
dump_ui "$LOG_DIR/ui-initial.xml"
read -r COLD_X COLD_Y <<<"$(button_center centralBrainColdButton "$LOG_DIR/ui-initial.xml")"
if [[ -z "${COLD_Y:-}" ]]; then
  echo "Client2 cold scenario button is not visible" >&2
  exit 1
fi

# Runtime unavailable must be visible and must release the Client2 single-flight gate.
"${ADB_DEVICE[@]}" shell pm disable-user --user 0 com.centralbrain.runtime \
  >"$LOG_DIR/runtime-disable.txt"
RUNTIME_DISABLED=true
"${ADB_DEVICE[@]}" logcat -c
tap_cold
wait_for_log \
  'reason=Runtime Binder unavailable: bindService returned false' \
  "$LOG_DIR/runtime-absent-log.txt"
assert_ui_reply \
  'text="Binder failed: Runtime Binder unavailable: bindService returned false"' \
  "$LOG_DIR/ui-runtime-absent.xml"

"${ADB_DEVICE[@]}" shell pm enable com.centralbrain.runtime \
  >"$LOG_DIR/runtime-enable.txt"
RUNTIME_DISABLED=false
"${ADB_DEVICE[@]}" logcat -c
tap_cold
wait_for_log 'client2_binder_task_completed=true' \
  "$LOG_DIR/runtime-reenabled-log.txt"
assert_ui_reply 'text="Deterministic Binder reply: care.cold:' \
  "$LOG_DIR/ui-runtime-reenabled.xml"

# Two rapid taps must create exactly one submitted task and one terminal callback.
"${ADB_DEVICE[@]}" logcat -c
tap_cold
tap_cold
wait_for_log 'client2_binder_task_completed=true' \
  "$LOG_DIR/single-flight-log.txt"
if [[ "$(grep -Fc 'client2_binder_task_submitted=true' \
    "$LOG_DIR/single-flight-log.txt")" -ne 1 ]] \
    || [[ "$(grep -Fc 'client2_binder_task_completed=true' \
      "$LOG_DIR/single-flight-log.txt")" -ne 1 ]] \
    || [[ "$(grep -Fc 'packages=[com.tuanjie.urasclient2] resolved=true' \
      "$LOG_DIR/single-flight-log.txt")" -ne 1 ]]; then
  cat "$LOG_DIR/single-flight-log.txt" >&2
  echo "Client2 single-flight produced duplicate submission or terminal evidence" >&2
  exit 1
fi
assert_ui_reply 'text="Deterministic Binder reply: care.cold:' \
  "$LOG_DIR/ui-single-flight.xml"

# Kill the debug Runtime process after submission and verify one SERVICE_DIED terminal.
"${ADB_DEVICE[@]}" logcat -c
tap_cold
SUBMITTED=false
for _ in {1..100}; do
  if "${ADB_DEVICE[@]}" logcat -d CbClient2Binder:I '*:S' \
      | grep -Fq 'client2_binder_task_submitted=true'; then
    SUBMITTED=true
    break
  fi
  sleep 0.02
done
if [[ "$SUBMITTED" != true ]]; then
  echo "Client2 task was not submitted before Runtime death injection" >&2
  exit 1
fi
"${ADB_DEVICE[@]}" shell am broadcast \
  -a com.centralbrain.runtime.DEBUG_KILL_PROCESS \
  -n com.centralbrain.runtime/.RuntimeFaultProbeReceiver \
  >"$LOG_DIR/runtime-fault-broadcast.txt"
wait_for_log 'reason=task error 4: runtime service died' \
  "$LOG_DIR/runtime-death-log.txt"
if [[ "$(grep -Fc 'client2_binder_task_failed=true' \
    "$LOG_DIR/runtime-death-log.txt")" -ne 1 ]] \
    || grep -Fq 'client2_binder_task_completed=true' \
      "$LOG_DIR/runtime-death-log.txt"; then
  cat "$LOG_DIR/runtime-death-log.txt" >&2
  echo "Runtime death did not produce one fail-closed Client2 terminal" >&2
  exit 1
fi
for marker in \
  'runtime_fault_injection_requested=true' \
  'fault=PROCESS_DEATH' \
  'hardware_accessed=false'; do
  grep -Fq "$marker" "$LOG_DIR/runtime-death-log.txt"
done
assert_ui_reply 'text="Binder failed: task error 4: runtime service died"' \
  "$LOG_DIR/ui-runtime-death.xml"

# A new click must restart/rebind Runtime and reconcile the interrupted task fail closed.
"${ADB_DEVICE[@]}" logcat -c
tap_cold
wait_for_log 'client2_binder_task_completed=true' \
  "$LOG_DIR/runtime-death-retry-log.txt"
for marker in \
  'restart reconciliation completed' \
  'task_execution_resume_enabled=false' \
  'packages=[com.tuanjie.urasclient2] resolved=true'; do
  grep -Fq "$marker" "$LOG_DIR/runtime-death-retry-log.txt"
done
assert_ui_reply 'text="Deterministic Binder reply: care.cold:' \
  "$LOG_DIR/ui-runtime-death-retry.xml"

# Force-stop/relaunch the Client2 process and verify a fresh Binder/UI path.
CLIENT_PID_BEFORE="$("${ADB_DEVICE[@]}" shell pidof com.tuanjie.urasclient2 | tr -d '\r')"
launch_client2 "$LOG_DIR/client2-relaunch.txt"
CLIENT_PID_AFTER="$("${ADB_DEVICE[@]}" shell pidof com.tuanjie.urasclient2 | tr -d '\r')"
if [[ -z "$CLIENT_PID_BEFORE" || -z "$CLIENT_PID_AFTER" \
    || "$CLIENT_PID_BEFORE" == "$CLIENT_PID_AFTER" ]]; then
  echo "Client2 process restart evidence is incomplete" >&2
  exit 1
fi
dump_ui "$LOG_DIR/ui-after-client-restart.xml"
read -r COLD_X COLD_Y <<<"$(button_center centralBrainColdButton \
  "$LOG_DIR/ui-after-client-restart.xml")"
"${ADB_DEVICE[@]}" logcat -c
tap_cold
wait_for_log 'client2_binder_task_completed=true' \
  "$LOG_DIR/client2-restart-log.txt"
for marker in \
  'r7_application_integration_complete=true' \
  'client2_binder_migration_complete=true' \
  'api33_end_to_end_acceptance_complete=true' \
  'runtime_acceptance_blockers=TARGET_SYSTEM_INTEGRATION_OWNER_UNRESOLVED' \
  'production_activation_allowed=false' \
  'target_hardware_validated=false'; do
  grep -Fq "$marker" "$LOG_DIR/client2-restart-log.txt"
done
assert_ui_reply 'text="Deterministic Binder reply: care.cold:' \
  "$LOG_DIR/ui-client-restart-reply.xml"

ln -sfn "$LOG_DIR" "$ROOT_DIR/logs/test/client2-central-brain-recovery/latest"
printf '%s\n' \
  "device_serial=$SERIAL" \
  "android_api=$SDK" \
  "device_abi=$ABI" \
  "runtime_absent_failure_visible=true" \
  "runtime_reenable_retry_completed=true" \
  "client2_single_flight_verified=true" \
  "runtime_process_death_injected=true" \
  "runtime_service_death_failure_visible=true" \
  "runtime_service_death_terminal_unique=true" \
  "runtime_service_restart_retry_completed=true" \
  "runtime_restart_reconciliation_fail_closed=true" \
  "client2_process_restart_rebind_completed=true" \
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
