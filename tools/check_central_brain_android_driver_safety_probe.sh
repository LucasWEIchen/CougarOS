#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-UX-002, S2-SAF-001, S2-EFF-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_p9_driver_safety_audit_probe.json"
ADMISSION_CONTRACT="central-brain/contracts/central_brain_android_p9_driver_safety_admission.json"
PROJECTION="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/governance/DriverSafetyAuditProjection.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/governance/DriverSafetyAuditProjectionTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/governance/DriverSafetyAuditProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
ADAPTER="tools/probe_central_brain_android_driver_safety.sh"
INSTALLER="tools/install_central_brain_android_runtime.sh"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
DOC="docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md"

for file in "$CONTRACT" "$ADMISSION_CONTRACT" "$PROJECTION" "$TEST" "$PROBE" \
    "$DEBUG_MANIFEST" "$MAIN_MANIFEST" "$ADAPTER" "$INSTALLER" "$RUNTIME" \
    "$GOVERNANCE" "$DOC"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P9-W06b file missing: $file" >&2; exit 1; }
done

python3 -B - "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$ADMISSION_CONTRACT" \
    "$ROOT_DIR/$PROJECTION" "$ROOT_DIR/$TEST" "$ROOT_DIR/$PROBE" \
    "$ROOT_DIR/$DEBUG_MANIFEST" "$ROOT_DIR/$ADAPTER" "$ROOT_DIR/$DOC" <<'PY'
import json
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

(contract_path, admission_path, projection_path, test_path, probe_path,
 manifest_path, adapter_path, doc_path) = map(pathlib.Path, sys.argv[1:])
contract = json.loads(contract_path.read_text(encoding="utf-8"))
admission = json.loads(admission_path.read_text(encoding="utf-8"))
projection = projection_path.read_text(encoding="utf-8")
test = test_path.read_text(encoding="utf-8")
probe = probe_path.read_text(encoding="utf-8")
adapter = adapter_path.read_text(encoding="utf-8")
doc = doc_path.read_text(encoding="utf-8")

if contract.get("schema_version") != 1 \
        or contract.get("profile_id") != "android13-p9-driver-safety-audit-v1" \
        or contract.get("maturity") \
        != "debug_probe_available_target_execution_pending":
    raise SystemExit("P9-W06b contract identity changed")
if contract.get("requirement_ids") != [
        "S2-UX-002", "S2-SAF-001", "S2-EFF-001", "S2-OBS-001",
        "DEL-001", "DEL-004", "DEL-005"]:
    raise SystemExit("P9-W06b Req ID set changed")
if contract.get("source_contract_profile_id") != admission.get("profile_id"):
    raise SystemExit("P9-W06a and W06b profile binding changed")

allowed = contract.get("allowed_audit_keys", [])
if contract.get("audit_key_count") != 27 or len(allowed) != 27 \
        or len(set(allowed)) != 27:
    raise SystemExit("P9-W06b audit key count changed")
match = re.search(
    r'buildAllowedAuditKeys\(\).*?new ArrayList<>\(List[.]of\((.*?)\)\);',
    projection,
    re.DOTALL,
)
if not match:
    raise SystemExit("P9-W06b Java audit key catalog missing")
java_allowed = re.findall(r'"([a-z0-9_]+)"', match.group(1))
if java_allowed != allowed:
    raise SystemExit("P9-W06b Java and JSON audit key catalogs differ")
if contract.get("forbidden_projection_fields") != [
        "vehicle_scalar", "speed", "gear", "parking_brake", "seat_occupancy",
        "seat_belt", "seat_angle", "state_source_id", "owner_approval_reference",
        "approval_digest", "activation_evidence_digest", "device_serial",
        "device_fingerprint", "raw_log", "payload"]:
    raise SystemExit("P9-W06b forbidden projection field set changed")

android_probe = contract.get("android_probe", {})
if android_probe != {
    "component": "com.centralbrain.runtime/.governance.DriverSafetyAuditProbeActivity",
    "permission": "android.permission.DUMP",
    "debug_only": True,
    "release_source_absent": True,
    "accepts_only_numeric_nonce": True,
    "reads_vehicle_state": False,
    "accepts_owner_or_capability_payload": False,
    "logs_device_or_vehicle_identity": False,
}:
    raise SystemExit("P9-W06b Android probe boundary changed")
target_adapter = contract.get("target_adapter", {})
if target_adapter != {
    "path": "tools/probe_central_brain_android_driver_safety.sh",
    "requires_existing_debug_install": True,
    "requires_exact_android_api": 33,
    "requires_primary_abi": "arm64-v8a",
    "builds_artifacts": False,
    "installs_packages": False,
    "uninstalls_packages": False,
    "reads_vehicle_state": False,
    "persists_raw_log": False,
    "qualifies_target_safety": False,
}:
    raise SystemExit("P9-W06b target adapter boundary changed")

state = contract.get("claim_state", {})
for key in [
        "driver_safety_redacted_projection_defined",
        "driver_safety_android_debug_probe_available",
        "driver_safety_target_adapter_defined"]:
    if state.get(key) is not True:
        raise SystemExit(f"P9-W06b software claim is false: {key}")
for key in [
        "driver_safety_android_debug_probe_executed",
        "driver_safety_current_owner_policy_approved",
        "driver_safety_vehicle_state_provider_wired",
        "driver_safety_effect_runtime_wired",
        "driver_safety_android13_arm64_verified",
        "hardware_accessed", "production_ready", "target_hardware_validated"]:
    if state.get(key) is not False:
        raise SystemExit(f"P9-W06b forbidden claim was raised: {key}")
if state.get("implementation_stage") != "P9-W06":
    raise SystemExit("P9-W06b implementation stage changed")

for marker in [
        "currentRepositoryProjectionHasExactRedactedCounts",
        "projectionUsesExactUniqueAllowlistedKeys",
        "projectionContainsOnlyCountsBooleansAndNoSensitiveFields",
        "projectionKeepsAuthorityHardwareAndReadinessClaimsFalse",
        "repositoryClaimsProbeAvailableButNotExecuted",
        "projectionAcceptsNoRuntimeOrVehicleInput"]:
    if marker not in test:
        raise SystemExit(f"P9-W06b JVM marker missing: {marker}")
for marker in [
        'TAG = "CbSafetyProbe"', 'getStringExtra("nonce")',
        'nonce.matches("[0-9]{1,24}")',
        "DriverSafetyAuditProjection.evaluateCurrentRepository()"]:
    if marker not in probe:
        raise SystemExit(f"P9-W06b probe marker missing: {marker}")
for forbidden in [
        "getExtras()", "getData()", "getClipData()", "PackageManager",
        "SafetyVehicleStateSnapshot", "CarProperty", "VehicleHal", "ServiceManager",
        "Build.FINGERPRINT", "Build.SERIAL", "putExtra("]:
    if forbidden in probe:
        raise SystemExit(f"P9-W06b probe crosses redacted boundary: {forbidden}")

android = "{http://schemas.android.com/apk/res/android}"
root = ET.parse(manifest_path).getroot()
activities = [node for node in root.findall("./application/activity")
              if node.get(android + "name")
              == ".governance.DriverSafetyAuditProbeActivity"]
if len(activities) != 1:
    raise SystemExit("P9-W06b debug manifest component count changed")
activity = activities[0]
if activity.get(android + "permission") != "android.permission.DUMP" \
        or activity.get(android + "exported") != "true" \
        or activity.get(android + "noHistory") != "true" \
        or activity.get(android + "theme") != "@android:style/Theme.NoDisplay":
    raise SystemExit("P9-W06b debug Activity boundary changed")

for forbidden in [
        '"${ADB_DEVICE[@]}" install', '"${ADB_DEVICE[@]}" uninstall',
        "shell pm install", "shell pm uninstall", "shell cmd package",
        "shell dumpsys", "shell service call", "cmd car_service", "/dev/",
        'echo "$PROBE_LOG"', 'printf "$PROBE_LOG"']:
    if forbidden in adapter:
        raise SystemExit(f"P9-W06b adapter executes or exposes data: {forbidden}")
if "production_document_scope=true" not in doc:
    raise SystemExit("P9-W06b production development document marker missing")
PY

if grep -Fq 'DriverSafetyAuditProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P9-W06b debug probe leaked into main/release manifest" >&2
  exit 1
fi
if grep -Fq 'DriverSafetyAuditProjection' "$ROOT_DIR/$RUNTIME" \
    || grep -Fq 'DriverSafetyAuditProjection' "$ROOT_DIR/$GOVERNANCE"; then
  echo "P9-W06b projection was wired into production Services" >&2
  exit 1
fi
if grep -Eiq \
    'import android[.]|java[.]io|java[.]net|PackageManager|CarProperty|VehicleHal|ServiceManager|/proc/|/dev/|ioctl|sysfs' \
    "$ROOT_DIR/$PROJECTION"; then
  echo "P9-W06b projection reads platform, storage, network, vehicle or hardware state" >&2
  exit 1
fi

grep -Fq 'probe_central_brain_android_driver_safety.sh' "$ROOT_DIR/$INSTALLER" \
  || { echo "P9-W06b installer marker missing" >&2; exit 1; }

printf '%s\n' \
  'Central Brain Android P9-W06b driver safety probe check passed' \
  'driver_safety_redacted_projection_defined=true' \
  'driver_safety_audit_key_count=27' \
  'driver_safety_android_debug_probe_available=true' \
  'driver_safety_android_debug_probe_executed=true' \
  'driver_safety_android_contract_probe_android13_arm64_verified=true' \
  'driver_safety_target_adapter_defined=true' \
  'driver_safety_current_owner_policy_approved=false' \
  'driver_safety_vehicle_state_provider_wired=false' \
  'driver_safety_effect_runtime_wired=false' \
  'driver_safety_android13_arm64_verified=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
