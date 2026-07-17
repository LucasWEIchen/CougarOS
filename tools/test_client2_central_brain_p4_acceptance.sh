#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-UX-001..003, S2-HMI-001..006, S2-SCN-001, S2-SAF-001,
# S2-EFF-001, APP-004, XSC-001/005/006, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${ANDROID_SERIAL:-}"
BUILD=true
REQUIRE_API_33=false
REPLACE_CONFLICTING_CLIENT2=false

usage() {
  cat <<'EOF'
Usage: test_client2_central_brain_p4_acceptance.sh [options]

Options:
  --serial SERIAL                 Select one adb device without printing its identity.
  --skip-build                    Reuse existing Runtime and Client2 artifacts.
  --require-api-33                Require exact Android API 33 ARM64 evidence.
  --replace-conflicting-client2   Remove a signer-conflicting Client2 package.
  -h, --help                      Show this help.
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
[[ -x "$ADB" ]] || { echo "adb is not executable" >&2; exit 1; }

if [[ -z "$SERIAL" ]]; then
  mapfile -t DEVICES < <("$ADB" devices | tr -d '\r' | awk 'NR > 1 && $2 == "device" {print $1}')
  [[ ${#DEVICES[@]} -eq 1 ]] || { echo "expected exactly one online adb device" >&2; exit 1; }
  SERIAL="${DEVICES[0]}"
fi
DEVICE=("$ADB" -s "$SERIAL")
SDK="$("${DEVICE[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
ABI="$("${DEVICE[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
[[ "$SDK" =~ ^[0-9]+$ ]] && ((SDK >= 33)) || { echo "Android API 33+ required" >&2; exit 1; }
[[ "$REQUIRE_API_33" != true || "$SDK" == 33 ]] || { echo "exact Android API 33 required" >&2; exit 1; }
[[ "$ABI" == arm64-v8a ]] || { echo "ARM64 target required" >&2; exit 1; }

STAMP="$(date +%Y%m%d_%H%M%S)"
LOG_DIR="$ROOT_DIR/logs/test/client2-p4-acceptance/$STAMP"
mkdir -p "$LOG_DIR"
DEVICE_XML=/sdcard/client2-p4-acceptance.xml

assert_marker() {
  local file="$1" marker="$2"
  grep -Fq -- "$marker" "$file" || {
    echo "P4 acceptance suite missing marker: $marker" >&2
    return 1
  }
}

assert_no_central_brain_crash() {
  local suite="$1" crash_buffer
  crash_buffer="$("${DEVICE[@]}" logcat -b crash -d 2>/dev/null || true)"
  if grep -Eq 'com\.tuanjie\.urasclient2|com\.centralbrain\.runtime' <<<"$crash_buffer"; then
    echo "P4 acceptance detected a Central Brain application crash after $suite" >&2
    return 1
  fi
}

run_suite() {
  local name="$1" script="$2"
  shift 2
  "${DEVICE[@]}" logcat -b crash -c >/dev/null 2>&1 || true
  if ! bash "$ROOT_DIR/tools/$script" "$@" >"$LOG_DIR/$name.txt" 2>&1; then
    tail -80 "$LOG_DIR/$name.txt" >&2
    echo "P4 acceptance suite failed: $name" >&2
    return 1
  fi
  assert_no_central_brain_crash "$name"
}

COMMON_ARGS=(--serial "$SERIAL")
if [[ "$REQUIRE_API_33" == true ]]; then
  COMMON_ARGS+=(--require-api-33)
fi
if [[ "$REPLACE_CONFLICTING_CLIENT2" == true ]]; then
  COMMON_ARGS+=(--replace-conflicting-client2)
fi

RECOVERY_ARGS=("${COMMON_ARGS[@]}")
ENGINEER_ARGS=("${COMMON_ARGS[@]}")
if [[ "$BUILD" == false ]]; then
  RECOVERY_ARGS+=(--skip-build)
  ENGINEER_ARGS+=(--skip-build)
fi

run_suite recovery test_client2_central_brain_recovery.sh "${RECOVERY_ARGS[@]}"
run_suite engineer test_client2_central_brain_engineer_simulation.sh "${ENGINEER_ARGS[@]}"
run_suite scenario test_client2_central_brain_scenario_sync.sh "${COMMON_ARGS[@]}" --skip-build
run_suite display test_client2_central_brain_accessibility_display.sh "${COMMON_ARGS[@]}" --skip-build

for marker in \
  'client2_navigation_toggle_show_verified=true' \
  'client2_navigation_toggle_hide_verified=true' \
  'client2_outside_tap_dismiss_verified=true' \
  'client2_session_reconnect_replay_verified=true' \
  'client2_hmi_checkpoint_resume_verified=true' \
  'cockpit_execution_media_navigation_projection_verified=true' \
  'cockpit_approval_details_fail_closed_verified=true' \
  'cockpit_partial_outcome_projection_verified=true' \
  'cockpit_compensation_projection_verified=true' \
  'cockpit_recovery_commands_disabled_verified=true'; do
  assert_marker "$LOG_DIR/recovery.txt" "$marker"
done
for marker in \
  'cockpit_engineer_driving_state_matrix_verified=true' \
  'cockpit_engineer_fault_matrix_verified=true' \
  'cockpit_engineer_reset_fail_closed_verified=true' \
  'cockpit_engineer_runtime_release_service_absent=true' \
  'cockpit_engineer_production_available=false'; do
  assert_marker "$LOG_DIR/engineer.txt" "$marker"
done
for marker in \
  'cockpit_scenario_natural_cold_sync_verified=true' \
  'cockpit_scenario_natural_fatigue_sync_verified=true' \
  'cockpit_scenario_natural_rest_sync_verified=true' \
  'cockpit_scenario_manual_hvac_sync_verified=true' \
  'cockpit_scenario_manual_seat_sync_verified=true' \
  'cockpit_scenario_effect_dispatch_enabled=false'; do
  assert_marker "$LOG_DIR/scenario.txt" "$marker"
done
for marker in \
  'cockpit_display_matrix_android13_arm64_verified=true' \
  'cockpit_accessibility_content_description_verified=true' \
  'cockpit_accessibility_state_not_color_only=true' \
  'cockpit_display_unsupported_fail_closed=true'; do
  assert_marker "$LOG_DIR/display.txt" "$marker"
done

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
  echo "P4 final UI hierarchy unavailable after bounded retries" >&2
  return 1
}

"${DEVICE[@]}" logcat -b crash -c >/dev/null 2>&1 || true
"${DEVICE[@]}" shell am force-stop com.tuanjie.urasclient2
"${DEVICE[@]}" shell am start -W -n com.tuanjie.urasclient2/.MainActivity \
  >"$LOG_DIR/final-activity.txt"
grep -Fq 'Status: ok' "$LOG_DIR/final-activity.txt"
dump_ui "$LOG_DIR/final-ui.xml"
grep -Fq 'centralBrainNavigationTrigger' "$LOG_DIR/final-ui.xml"
assert_no_central_brain_crash final-ui-tree

printf '%s\n' \
  'p4_w12_application_acceptance_complete=true' \
  'p4_android13_arm64_aggregate_verified=true' \
  'p4_navigation_show_hide_verified=true' \
  'p4_natural_scenario_sync_verified=true' \
  'p4_manual_hvac_seat_admission_verified=true' \
  'p4_moving_unknown_fail_closed_verified=true' \
  'p4_runtime_client_process_recovery_verified=true' \
  'p4_ui_tree_verified=true' \
  'p4_crash_buffer_clean=true' \
  'runtime_release_simulation_surface_absent=true' \
  'p4_plan_effect_projection_host_verified=true' \
  'p4_automatic_plan_runtime_published=false' \
  'p4_production_effect_dispatch_enabled=false' \
  'p4_approval_response_service_published=false' \
  'p4_undo_service_published=false' \
  'p4_vehicle_readback_available=false' \
  'client2_production_release_artifact_available=false' \
  'hmi_d4_demo_control_loop_complete=false' \
  'hardware_accessed=false' \
  'target_hardware_validated=false' \
  'production_ready=false'
