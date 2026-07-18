#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-UX-001..003, S2-HMI-001..006, S2-SCN-001, S2-SAF-001,
# S2-EFF-001, APP-004, XSC-001/005/006, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_p4_hmi_acceptance.json"
DEVICE_TEST="$ROOT_DIR/tools/test_client2_central_brain_p4_acceptance.sh"
REQUIREMENTS="$ROOT_DIR/docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md"
DEVIATIONS="$ROOT_DIR/docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md"
ISSUES="$ROOT_DIR/docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md"
DELIVERY="$ROOT_DIR/docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md"
DRIVER="$ROOT_DIR/docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md"
ROADMAP="$ROOT_DIR/docs/CENTRAL_BRAIN_ROADMAP.md"
ARCHITECTURE="$ROOT_DIR/docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md"
INTERFACES="$ROOT_DIR/docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md"
DESIGN="$ROOT_DIR/docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md"
ROOT_README="$ROOT_DIR/README.md"
CLIENT_README="$ROOT_DIR/apk-labs/client2-central-brain/README.md"
PHYSICAL_REPORT="$ROOT_DIR/docs/CENTRAL_BRAIN_ANDROID13_PHYSICAL_TARGET_TEST_REPORT.md"

for path in "$CONTRACT" "$DEVICE_TEST" "$REQUIREMENTS" "$DEVIATIONS" "$ISSUES" "$DELIVERY" "$DRIVER" "$ROADMAP" \
    "$ARCHITECTURE" "$INTERFACES" "$DESIGN" "$ROOT_README" "$CLIENT_README" "$PHYSICAL_REPORT"; do
  [[ -f "$path" ]] || { echo "missing P4 acceptance file: $path" >&2; exit 1; }
done
bash -n "$DEVICE_TEST"
python3 -m json.tool "$CONTRACT" >/dev/null

python3 -B - "$CONTRACT" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
if payload.get("schema_version") != "1.0.0":
    raise SystemExit("P4 acceptance schema changed")
if payload.get("status") != "verified":
    raise SystemExit("P4 acceptance must remain verified")
if payload.get("required_android_api") != 33:
    raise SystemExit("P4 acceptance must require Android API 33")
suites = payload.get("suites", [])
if [item.get("id") for item in suites] != [f"P4-A-{index:03d}" for index in range(1, 7)]:
    raise SystemExit("P4 acceptance suite IDs changed")
expected_modes = [
    "physical-positive",
    "physical-debug-only",
    "physical-positive",
    "physical-positive",
    "host-projection-and-physical-fail-closed",
    "release-static-and-apk",
]
if [item.get("evidence_mode") for item in suites] != expected_modes:
    raise SystemExit("P4 evidence modes changed")
claims = payload.get("claim_state", {})
expected_true = {
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
}
expected_false = {
    "p4_automatic_plan_runtime_published",
    "p4_production_effect_dispatch_enabled",
    "p4_approval_response_service_published",
    "p4_undo_service_published",
    "p4_vehicle_readback_available",
    "client2_production_release_artifact_available",
    "hmi_d4_demo_control_loop_complete",
    "target_hardware_validated",
    "production_ready",
}
if {key for key, value in claims.items() if value is True} != expected_true:
    raise SystemExit("P4 positive claims changed")
if {key for key, value in claims.items() if value is False} != expected_false:
    raise SystemExit("P4 false claims changed")
PY

for marker in \
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
  'target_hardware_validated=false' \
  'production_ready=false'; do
  grep -Fq -- "$marker" "$DEVICE_TEST"
done
grep -Fq 'logcat -b crash -c' "$DEVICE_TEST"
grep -Fq 'P4 final UI hierarchy unavailable after bounded retries' "$DEVICE_TEST"
for device_script in \
    "$ROOT_DIR/tools/test_client2_central_brain_binder.sh" \
    "$ROOT_DIR/tools/test_client2_central_brain_recovery.sh" \
    "$ROOT_DIR/tools/test_client2_central_brain_engineer_simulation.sh" \
    "$ROOT_DIR/tools/test_client2_central_brain_scenario_sync.sh" \
    "$ROOT_DIR/tools/test_client2_central_brain_accessibility_display.sh" \
    "$DEVICE_TEST"; do
  grep -Fq 'timeout 8s' "$device_script" \
    || { echo "P4 UI dump timeout missing: ${device_script#$ROOT_DIR/}" >&2; exit 1; }
  if grep -Fq 'device_serial=' "$device_script"; then
    echo "P4 device script must not emit raw serial: ${device_script#$ROOT_DIR/}" >&2
    exit 1
  fi
done
grep -Fq 'scroll_resource_to_edge centralBrainSeatSurface bottom' \
  "$ROOT_DIR/tools/test_client2_central_brain_scenario_sync.sh"
for marker in \
  'client2_navigation_toggle_show_verified=true' \
  'client2_navigation_toggle_hide_verified=true' \
  'client2_outside_tap_dismiss_verified=true'; do
  grep -Fq -- "$marker" "$ROOT_DIR/tools/test_client2_central_brain_recovery.sh"
done

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$file" \
    || { echo "P4 acceptance marker missing in ${file#$ROOT_DIR/}: $marker" >&2; exit 1; }
}

require_text "$REQUIREMENTS" '## 53. P4-W12 Android device acceptance/fault/recovery trace'
require_text "$DEVIATIONS" '## DEV-062 P4-W12 application acceptance is not HMI-D4 execution closure'
require_text "$ISSUES" '### ISSUE-033 P4-W12 update'
require_text "$DELIVERY" '## 2026-07-18 P4-W12 Android device acceptance/fault/recovery delivery'
require_text "$DRIVER" '### P4-W12 Aggregate acceptance Driver/HAL boundary'
require_text "$ROADMAP" '### 2026-07-18 P4-W12 progress'
require_text "$ARCHITECTURE" '## P4-W12 aggregate Android acceptance architecture'
require_text "$INTERFACES" '## P4-W12 aggregate Android acceptance interface'
require_text "$DESIGN" '## P4-W12 implementation detail: aggregate device acceptance'
require_text "$ROOT_README" '| P4 Android aggregate acceptance |'
require_text "$CLIENT_README" '## P4-W12 aggregate Android acceptance'
require_text "$PHYSICAL_REPORT" '## 19. 2026-07-18 P4-W12 aggregate Android application acceptance evidence'
require_text "$PHYSICAL_REPORT" 'p4_android13_arm64_aggregate_verified=true'
for marker in \
  'p4_w12_application_acceptance_complete=true' \
  'p4_automatic_plan_runtime_published=false' \
  'hmi_d4_demo_control_loop_complete=false' \
  'implementation_stage=P5-W08'; do
  require_text "$ROOT_README" "$marker"
  require_text "$CLIENT_README" "$marker"
done

bash "$ROOT_DIR/tools/check_central_brain_android_client2_hmi_reducer.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_recovery_ux.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_engineer_simulation.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_scenario_sync.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_accessibility_display.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_simulated_effect_adapter.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_simulated_hvac_adapter.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_simulated_seat_adapter.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_simulated_media_navigation_adapters.sh"

printf '%s\n' \
  'p4_w12_acceptance_contract_verified=true' \
  'p4_acceptance_evidence_modes_separated=true' \
  'hmi_d4_demo_control_loop_complete=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
echo "Central Brain Android Client2 P4 aggregate acceptance check passed"
