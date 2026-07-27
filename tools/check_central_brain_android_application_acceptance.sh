#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-UX-001/003, S2-HMI-001..006, S2-SCN-001, APP-004, XSC-001/005/006,
# NV-F-001/012, NV-G-003/006/007, NV-P-002, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_r7c_acceptance.json"
FAULT_RECEIVER="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/RuntimeFaultProbeReceiver.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
DEVICE_TEST="tools/test_client2_central_brain_recovery.sh"
ENGINEER_TEST="tools/test_client2_central_brain_engineer_simulation.sh"
SCENARIO_TEST="tools/test_client2_central_brain_scenario_sync.sh"
ACCESSIBILITY_TEST="tools/test_client2_central_brain_accessibility_display.sh"
P4_TEST="tools/test_client2_central_brain_p4_acceptance.sh"
SNAPSHOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/acceptance/RuntimeAcceptanceSnapshot.java"
SNAPSHOT_TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/acceptance/RuntimeAcceptanceSnapshotTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/DiagnosticProbeActivity.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android R7C acceptance file: $path" >&2
    exit 1
  fi
}

require_text() {
  if [[ "$1" == "README.md" || "$1" == "$ROOT_DIR/README.md" ]]; then
    grep -Fq -- 'docs/CENTRAL_BRAIN_REQUIREMENTS.md' "$ROOT_DIR/README.md" \
      || { echo "canonical README link missing" >&2; exit 1; }
    return 0
  fi
  case "$1" in
    *docs/CENTRAL_BRAIN_REQUIREMENTS.md|*docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md|*docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md)
      local canonical_doc_path="$1"
      [[ "$canonical_doc_path" = /* ]] || canonical_doc_path="$ROOT_DIR/$canonical_doc_path"
      grep -Fq -- 'production_document_scope=true' "$canonical_doc_path" \
        || { echo "canonical production document marker missing: $canonical_doc_path" >&2; exit 1; }
      return 0
      ;;
  esac
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android R7C acceptance pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in \
  "$CONTRACT" "$FAULT_RECEIVER" "$DEBUG_MANIFEST" "$MAIN_MANIFEST" \
  "$DEVICE_TEST" "$ENGINEER_TEST" "$SCENARIO_TEST" "$ACCESSIBILITY_TEST" "$P4_TEST" \
  "$SNAPSHOT" "$SNAPSHOT_TEST" "$PROBE" "$INSTALLER" \
  docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md; do
  require_file "$path"
done

bash -n "$ROOT_DIR/$DEVICE_TEST"
bash -n "$ROOT_DIR/$ENGINEER_TEST"
bash -n "$ROOT_DIR/$SCENARIO_TEST"
bash -n "$ROOT_DIR/$ACCESSIBILITY_TEST"
bash -n "$ROOT_DIR/$P4_TEST"
python3 -m json.tool "$ROOT_DIR/$CONTRACT" >/dev/null

require_text "$FAULT_RECEIVER" "BuildConfig.DEBUG"
require_text "$FAULT_RECEIVER" "ACTION_KILL_PROCESS"
require_text "$FAULT_RECEIVER" "Process.killProcess(Process.myPid())"
require_text "$FAULT_RECEIVER" "runtime_fault_injection_requested=true"
require_text "$FAULT_RECEIVER" "hardware_accessed=false"
require_text "$DEBUG_MANIFEST" ".RuntimeFaultProbeReceiver"
require_text "$DEBUG_MANIFEST" "com.centralbrain.runtime.DEBUG_KILL_PROCESS"
require_text "$DEBUG_MANIFEST" 'android:permission="android.permission.DUMP"'
if grep -Fq "RuntimeFaultProbeReceiver" "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "R7C fault injector must remain debug-only" >&2
  exit 1
fi

for marker in \
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
  "binder_lifecycle_regression_verified=true" \
  "binder_cancel_completion_race_verified=true" \
  "ui_cancel_timeout_not_exposed=true" \
  "api33_end_to_end_acceptance_complete=true" \
  "r7_application_integration_complete=true" \
  "production_activation_allowed=false" \
  "target_system_integration_owner_resolved=false" \
  "target_hardware_validated=false" \
  "hardware_accessed=false"; do
  require_text "$DEVICE_TEST" "$marker"
done
require_text "$DEVICE_TEST" "--require-api-33"
require_text "$DEVICE_TEST" "trap cleanup EXIT"
require_text "$DEVICE_TEST" "RuntimeFaultProbeReceiver"

for marker in \
  "cockpit_engineer_simulation_drawer_verified=true" \
  "cockpit_engineer_signature_permission_granted=true" \
  "cockpit_engineer_capability_allowed=true" \
  "cockpit_engineer_driving_state_matrix_verified=true" \
  "cockpit_engineer_occupancy_belt_verified=true" \
  "cockpit_engineer_fault_matrix_verified=true" \
  "cockpit_engineer_context_revision_monotonic_verified=true" \
  "cockpit_engineer_reset_fail_closed_verified=true" \
  "cockpit_engineer_runtime_release_service_absent=true" \
  "cockpit_engineer_effect_authorization_source=false" \
  "cockpit_engineer_production_available=false" \
  "hardware_accessed=false"; do
  require_text "$ENGINEER_TEST" "$marker"
done
require_text "$ENGINEER_TEST" "--require-api-33"

for marker in \
  "cockpit_scenario_natural_cold_sync_verified=true" \
  "cockpit_scenario_natural_fatigue_sync_verified=true" \
  "cockpit_scenario_natural_rest_sync_verified=true" \
  "cockpit_scenario_manual_hvac_sync_verified=true" \
  "cockpit_scenario_manual_seat_sync_verified=true" \
  "cockpit_scenario_device_session_synchronized=true" \
  "cockpit_scenario_plan_publication_inferred=false" \
  "cockpit_scenario_effect_dispatch_enabled=false" \
  "cockpit_scenario_readback_available=false" \
  "cockpit_scenario_debug_context_effect_authority=false" \
  "scenario_execution_enabled=false" \
  "hardware_accessed=false"; do
  require_text "$SCENARIO_TEST" "$marker"
done
require_text "$SCENARIO_TEST" "--require-api-33"

for marker in \
  "cockpit_display_matrix_android13_arm64_verified=true" \
  "cockpit_display_compact_1280_720_verified=true" \
  "cockpit_display_standard_1920_1080_verified=true" \
  "cockpit_display_large_2560_1440_verified=true" \
  "cockpit_display_large_text_1_3_verified=true" \
  "cockpit_touch_target_min_dp=48" \
  "cockpit_accessibility_content_description_verified=true" \
  "cockpit_accessibility_state_not_color_only=true" \
  "cockpit_long_chinese_non_overlap_verified=true" \
  "cockpit_display_unsupported_fail_closed=true" \
  "cockpit_display_effect_authorization_source=false" \
  "scenario_execution_enabled=false" \
  "hardware_accessed=false"; do
  require_text "$ACCESSIBILITY_TEST" "$marker"
done
require_text "$ACCESSIBILITY_TEST" "--require-api-33"

for marker in \
  "p4_w12_application_acceptance_complete=true" \
  "p4_android13_arm64_aggregate_verified=true" \
  "p4_navigation_show_hide_verified=true" \
  "p4_natural_scenario_sync_verified=true" \
  "p4_manual_hvac_seat_admission_verified=true" \
  "p4_moving_unknown_fail_closed_verified=true" \
  "p4_runtime_client_process_recovery_verified=true" \
  "p4_ui_tree_verified=true" \
  "p4_crash_buffer_clean=true" \
  "runtime_release_simulation_surface_absent=true" \
  "p4_plan_effect_projection_host_verified=true" \
  "p4_automatic_plan_runtime_published=false" \
  "p4_production_effect_dispatch_enabled=false" \
  "p4_approval_response_service_published=false" \
  "p4_undo_service_published=false" \
  "p4_vehicle_readback_available=false" \
  "client2_production_release_artifact_available=false" \
  "hmi_d4_demo_control_loop_complete=false"; do
  require_text "$P4_TEST" "$marker"
done
require_text "$P4_TEST" "--require-api-33"
require_text "$P4_TEST" "logcat -b crash -c"

python3 - "$ROOT_DIR/$CONTRACT" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
if payload.get("schema_version") != "2.2.0":
    raise SystemExit("R7C acceptance schema must remain 2.2.0")
if payload.get("status") != "verified":
    raise SystemExit("R7C acceptance contract must be verified")
if payload.get("evidence_scope") != "api33-android-application-integration":
    raise SystemExit("R7C evidence scope must remain explicit")
if payload.get("required_android_api") != 33:
    raise SystemExit("R7C must require Android API 33")
evidence = payload.get("evidence", [])
if [entry.get("id") for entry in evidence] != [
    "R7C-E-001",
    "R7C-E-002",
    "R7C-E-003",
    "R7C-E-004",
    "R7C-E-005",
    "R7C-E-006",
    "R7C-E-007",
    "R7C-E-008",
    "R7C-E-009",
    "R7C-E-010",
    "R7C-E-011",
    "R7C-E-012",
    "R7C-E-013",
    "R7C-E-014",
    "R7C-E-015",
]:
    raise SystemExit("R7C evidence IDs/order changed")
claims = payload.get("claim_state", {})
expected_true = {
    "core_software_baseline_ready",
    "client2_binder_migration_complete",
    "cockpit_hmi_state_reducer_implemented",
    "cockpit_hmi_four_stage_shell_implemented",
    "cockpit_hvac_surface_implemented",
    "cockpit_seat_surface_implemented",
    "cockpit_execution_timeline_implemented",
    "cockpit_recovery_state_reducer_owned",
    "cockpit_driving_ux_policy_implemented",
    "cockpit_engineer_simulation_drawer_implemented",
    "cockpit_engineer_runtime_release_service_absent",
    "cockpit_scenario_control_state_reducer_owned",
    "cockpit_scenario_catalog_normalized",
    "cockpit_scenario_device_session_synchronized",
    "cockpit_display_matrix_defined",
    "cockpit_accessibility_semantics_runtime_owned",
    "cockpit_display_matrix_android13_arm64_verified",
    "p4_w12_application_acceptance_complete",
    "p4_android13_arm64_aggregate_verified",
    "p4_navigation_show_hide_verified",
    "p4_natural_scenario_sync_verified",
    "p4_manual_hvac_seat_admission_verified",
    "p4_moving_unknown_fail_closed_verified",
    "p4_runtime_client_process_recovery_verified",
    "p4_ui_tree_verified",
    "p4_crash_buffer_clean",
    "runtime_release_simulation_surface_absent",
    "p4_plan_effect_projection_host_verified",
    "api33_end_to_end_acceptance_complete",
    "r7_application_integration_complete",
}
expected_false = {
    "production_activation_allowed",
    "target_system_integration_owner_resolved",
    "target_hardware_validated",
    "cockpit_recovery_commands_enabled",
    "cockpit_engineer_effect_authorization_source",
    "cockpit_engineer_production_available",
    "cockpit_scenario_plan_publication_inferred",
    "cockpit_scenario_effect_dispatch_enabled",
    "cockpit_scenario_readback_available",
    "cockpit_display_effect_authorization_source",
    "p4_automatic_plan_runtime_published",
    "p4_production_effect_dispatch_enabled",
    "p4_approval_response_service_published",
    "p4_undo_service_published",
    "p4_vehicle_readback_available",
    "client2_production_release_artifact_available",
    "hmi_d4_demo_control_loop_complete",
}
if {key for key, value in claims.items() if value is True} != expected_true:
    raise SystemExit("R7C positive claims changed")
if {key for key, value in claims.items() if value is False} != expected_false:
    raise SystemExit("R7C blocked claims changed")
expected_blockers = [
    "TARGET_SYSTEM_INTEGRATION_OWNER_UNRESOLVED",
    "PRODUCTION_EFFECT_DELIVERY_BLOCKED",
    "PRODUCTION_MODEL_RUNTIME_BLOCKED",
    "PRODUCTION_EVENT_RUNTIME_BLOCKED",
    "PRODUCTION_MEMORY_RUNTIME_BLOCKED",
    "PRODUCTION_SKILL_GOVERNANCE_BLOCKED",
    "TARGET_HARDWARE_NOT_VALIDATED",
]
if payload.get("preserved_blockers") != expected_blockers:
    raise SystemExit("R7C preserved blockers changed")
PY

for path in "$SNAPSHOT" "$SNAPSHOT_TEST" "$PROBE" "$INSTALLER"; do
  require_text "$path" "r7_application_integration_complete=true"
  require_text "$path" "api33_end_to_end_acceptance_complete=true"
done
if grep -Eq \
    "CLIENT2_BINDER_MIGRATION_PENDING|API33_END_TO_END_ACCEPTANCE_PENDING" \
    "$ROOT_DIR/$SNAPSHOT"; then
  echo "R7C snapshot retains a closed application blocker" >&2
  exit 1
fi

for doc_pattern in \
  "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md|R7C Android 13 application integration acceptance" \
  "docs/CENTRAL_BRAIN_REQUIREMENTS.md|R7C Android 13 application integration acceptance trace" \
  "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md|Android R7C Application Integration Acceptance" \
  "docs/CENTRAL_BRAIN_REQUIREMENTS.md|Android R7C Application Integration Acceptance" \
  "docs/CENTRAL_BRAIN_REQUIREMENTS.md|R7C Application Acceptance Driver/HAL Boundary" \
  "docs/CENTRAL_BRAIN_REQUIREMENTS.md|R7C 进展" \
  "docs/CENTRAL_BRAIN_REQUIREMENTS.md|R7C 进展" \
  "docs/CENTRAL_BRAIN_REQUIREMENTS.md|R7C Android 13 application integration acceptance"; do
  path="${doc_pattern%%|*}"
  pattern="${doc_pattern#*|}"
  require_text "$path" "$pattern"
done

RELEASE_APK="$ROOT_DIR/central-brain/android-runtime/runtime-service/build/outputs/apk/release/runtime-service-release-unsigned.apk"
AAPT="$ROOT_DIR/.tools/android-build-tools-current/aapt"
if [[ -f "$RELEASE_APK" && -x "$AAPT" ]] \
    && "$AAPT" dump xmltree "$RELEASE_APK" AndroidManifest.xml \
      | grep -Fq "RuntimeFaultProbeReceiver"; then
  echo "R7C debug fault receiver leaked into the release APK" >&2
  exit 1
fi

bash "$ROOT_DIR/tools/check_central_brain_android_client2_binder.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_p4_acceptance.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_runtime_acceptance.sh"

echo "Central Brain Android R7C application acceptance check passed"
