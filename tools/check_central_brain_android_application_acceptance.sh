#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-UX-001, S2-HMI-005, APP-004, XSC-001/005/006,
# NV-F-001/012, NV-G-003/006/007, NV-P-002, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_r7c_acceptance.json"
FAULT_RECEIVER="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/RuntimeFaultProbeReceiver.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
DEVICE_TEST="tools/test_client2_central_brain_recovery.sh"
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
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android R7C acceptance pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in \
  "$CONTRACT" "$FAULT_RECEIVER" "$DEBUG_MANIFEST" "$MAIN_MANIFEST" \
  "$DEVICE_TEST" "$SNAPSHOT" "$SNAPSHOT_TEST" "$PROBE" "$INSTALLER" \
  docs/CENTRAL_BRAIN_ANDROID_R7C_APPLICATION_ACCEPTANCE.md; do
  require_file "$path"
done

bash -n "$ROOT_DIR/$DEVICE_TEST"
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
  "client2_legacy_stream_replacement_verified=true" \
  "runtime_process_death_injected=true" \
  "client2_session_reconnect_replay_verified=true" \
  "client2_session_duplicate_event_suppressed=true" \
  "runtime_service_death_failure_visible=false" \
  "runtime_service_death_terminal_emitted=false" \
  "runtime_service_death_recovered_without_terminal=true" \
  "runtime_service_restart_retry_completed=true" \
  "runtime_restart_reconciliation_fail_closed=true" \
  "client2_process_restart_rebind_completed=true" \
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

python3 - "$ROOT_DIR/$CONTRACT" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
if payload.get("schema_version") != "1.1.0":
    raise SystemExit("R7C acceptance schema must remain 1.1.0")
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
]:
    raise SystemExit("R7C evidence IDs/order changed")
claims = payload.get("claim_state", {})
expected_true = {
    "core_software_baseline_ready",
    "client2_binder_migration_complete",
    "api33_end_to_end_acceptance_complete",
    "r7_application_integration_complete",
}
expected_false = {
    "production_activation_allowed",
    "target_system_integration_owner_resolved",
    "target_hardware_validated",
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
  "docs/CENTRAL_BRAIN_ANDROID_RUNTIME_EVOLUTION_PLAN.md|R7C Android 13 application integration acceptance" \
  "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md|R7C Android 13 application integration acceptance trace" \
  "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md|Android R7C Application Integration Acceptance" \
  "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md|Android R7C Application Integration Acceptance" \
  "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md|R7C Application Acceptance Driver/HAL Boundary" \
  "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md|R7C 进展" \
  "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md|R7C 进展" \
  "docs/CENTRAL_BRAIN_ROADMAP.md|R7C Android 13 application integration acceptance"; do
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
bash "$ROOT_DIR/tools/check_central_brain_android_runtime_acceptance.sh"

echo "Central Brain Android R7C application acceptance check passed"
