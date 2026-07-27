#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-OBS-001, S2-REL-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_p9_field_diagnostics_probe.json"
SOURCE_CONTRACT="central-brain/contracts/central_brain_android_p9_release_evidence_envelope.json"
PROJECTION="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/release/FieldDiagnosticsProjection.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/release/FieldDiagnosticsProjectionTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/release/FieldDiagnosticsProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
ADAPTER="tools/probe_central_brain_android_field_diagnostics.sh"
INSTALLER="tools/install_central_brain_android_runtime.sh"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
DOC="docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md"

for file in "$CONTRACT" "$SOURCE_CONTRACT" "$PROJECTION" "$TEST" "$PROBE" \
    "$DEBUG_MANIFEST" "$MAIN_MANIFEST" "$ADAPTER" "$INSTALLER" "$RUNTIME" \
    "$GOVERNANCE" "$DOC"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P9-W07b file missing: $file" >&2; exit 1; }
done

python3 -B - "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$SOURCE_CONTRACT" \
    "$ROOT_DIR/$PROJECTION" "$ROOT_DIR/$TEST" "$ROOT_DIR/$PROBE" \
    "$ROOT_DIR/$DEBUG_MANIFEST" "$ROOT_DIR/$ADAPTER" "$ROOT_DIR/$DOC" <<'PY'
import json
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

(contract_path, source_contract_path, projection_path, test_path, probe_path,
 manifest_path, adapter_path, doc_path) = map(pathlib.Path, sys.argv[1:])
contract = json.loads(contract_path.read_text(encoding="utf-8"))
source_contract = json.loads(source_contract_path.read_text(encoding="utf-8"))
projection = projection_path.read_text(encoding="utf-8")
test = test_path.read_text(encoding="utf-8")
probe = probe_path.read_text(encoding="utf-8")
adapter = adapter_path.read_text(encoding="utf-8")
doc = doc_path.read_text(encoding="utf-8")

if contract.get("schema_version") != 1 \
        or contract.get("profile_id") != "android13-p9-field-diagnostics-probe-v1" \
        or contract.get("maturity") \
        != "debug_probe_available_target_execution_pending":
    raise SystemExit("P9-W07b contract identity changed")
if contract.get("source_profile_id") != source_contract.get("profile_id"):
    raise SystemExit("P9-W07a and W07b profile binding changed")
if contract.get("requirement_ids") != [
        "S2-OBS-001", "S2-REL-001", "DEL-001", "DEL-004", "DEL-005"]:
    raise SystemExit("P9-W07b Req ID set changed")

categories = contract.get("diagnostic_categories", [])
if contract.get("diagnostic_category_count") != 8 \
        or [item.get("id") for item in categories] \
        != source_contract.get("diagnostic_categories"):
    raise SystemExit("P9-W07b diagnostic catalog changed")
if [item.get("adapter_mode") for item in categories] != [
        "EXECUTED", "NOT_RUN", "NOT_RUN", "EXECUTED", "EXECUTED",
        "EXECUTED", "EXECUTED", "NOT_RUN"]:
    raise SystemExit("P9-W07b category execution plan changed")
if contract.get("executed_category_count") != 5 \
        or contract.get("not_run_category_count") != 3:
    raise SystemExit("P9-W07b category execution counts changed")

allowed = contract.get("allowed_audit_keys", [])
if contract.get("audit_key_count") != 31 or len(allowed) != 31 \
        or len(set(allowed)) != 31:
    raise SystemExit("P9-W07b audit key catalog changed")
match = re.search(
    r'buildAllowedAuditKeys\(\).*?new ArrayList<>\(List[.]of\((.*?)\)\);',
    projection,
    re.DOTALL,
)
if not match:
    raise SystemExit("P9-W07b Java audit key catalog missing")
java_allowed = re.findall(r'"([a-z0-9_]+)"', match.group(1))
if java_allowed != allowed:
    raise SystemExit("P9-W07b Java and JSON audit key catalogs differ")

if contract.get("forbidden_projection_fields") != [
        "package_name", "package_path", "device_serial", "device_fingerprint",
        "certificate", "signature", "signer_material", "target_input", "raw_log",
        "user_text", "model_text", "memory_or_token", "vehicle_value", "payload"]:
    raise SystemExit("P9-W07b forbidden field set changed")

android_probe = contract.get("android_probe", {})
if android_probe != {
    "component": "com.centralbrain.runtime/.release.FieldDiagnosticsProbeActivity",
    "permission": "android.permission.DUMP",
    "debug_only": True,
    "release_source_absent": True,
    "accepts_only_numeric_nonce": True,
    "package_query_count": 3,
    "launch_target_query_count": 2,
    "service_query_count": 2,
    "starts_external_activity": False,
    "invokes_service": False,
    "logs_identity_or_payload": False,
}:
    raise SystemExit("P9-W07b Android probe boundary changed")
target_adapter = contract.get("target_adapter", {})
if target_adapter != {
    "path": "tools/probe_central_brain_android_field_diagnostics.sh",
    "requires_existing_debug_install": True,
    "requires_exact_android_api": 33,
    "requires_primary_abi": "arm64-v8a",
    "builds_artifacts": False,
    "installs_packages": False,
    "uninstalls_packages": False,
    "executes_rollback": False,
    "executes_exact_launch_checks": 4,
    "persists_raw_log": False,
    "automatic_upload": False,
    "qualifies_target_release": False,
}:
    raise SystemExit("P9-W07b target adapter boundary changed")

state = contract.get("claim_state", {})
for key in [
        "field_diagnostics_projection_defined",
        "field_diagnostics_android_debug_probe_available",
        "field_diagnostics_target_adapter_defined"]:
    if state.get(key) is not True:
        raise SystemExit(f"P9-W07b software claim is false: {key}")
if state.get("field_diagnostics_audit_key_count") != 31:
    raise SystemExit("P9-W07b claim key count changed")
for key in [
        "field_diagnostics_android_debug_probe_executed",
        "field_diagnostics_target_category_execution_complete",
        "release_evidence_target_report_admitted",
        "release_evidence_runtime_diagnostics_wired",
        "release_evidence_retest_workflow_wired",
        "release_evidence_automatic_upload_enabled",
        "field_diagnostics_android13_arm64_verified",
        "hardware_accessed", "production_ready", "target_hardware_validated"]:
    if state.get(key) is not False:
        raise SystemExit(f"P9-W07b forbidden claim was raised: {key}")
if state.get("implementation_stage") != "P9-W07":
    raise SystemExit("P9-W07b implementation stage changed")

for marker in [
        "exactAuditCatalogIsStableUniqueAndOrdered",
        "completeObservationPassesOnlyRedactedPreflight",
        "incompleteObservationRemainsAValidFailedPreflight",
        "malformedCountRelationshipFailsClosed",
        "auditMetadataUsesOnlyAllowlistedCountAndBooleanKeys",
        "auditMetadataContainsNoIdentitySignerLogOrPayload",
        "repositoryClaimsProbeAvailableButUnexecutedAndUnqualified"]:
    if marker not in test:
        raise SystemExit(f"P9-W07b JVM marker missing: {marker}")
for marker in [
        'TAG = "CbFieldDiag"', 'getStringExtra("nonce")',
        'nonce.matches("[0-9]{1,24}")',
        "ProductionReleaseMetadataProjection.requiredPackageName",
        "FieldDiagnosticsProjection.evaluate(collectObservation())"]:
    if marker not in probe:
        raise SystemExit(f"P9-W07b probe marker missing: {marker}")
for forbidden in [
        "getExtras()", "getData()", "getClipData()", "Build.FINGERPRINT",
        "Build.SERIAL", "getImei(", "getSubscriberId(", "getSigningCertificateHistory",
        "GET_SIGNING_CERTIFICATES", "putExtra(", "startActivity(", "startService(",
        "bindService(", "java.io", "java.net"]:
    if forbidden in probe:
        raise SystemExit(f"P9-W07b probe crosses redacted boundary: {forbidden}")

android = "{http://schemas.android.com/apk/res/android}"
root = ET.parse(manifest_path).getroot()
activities = [node for node in root.findall("./application/activity")
              if node.get(android + "name")
              == ".release.FieldDiagnosticsProbeActivity"]
if len(activities) != 1:
    raise SystemExit("P9-W07b debug manifest component count changed")
activity = activities[0]
if activity.get(android + "permission") != "android.permission.DUMP" \
        or activity.get(android + "exported") != "true" \
        or activity.get(android + "noHistory") != "true" \
        or activity.get(android + "theme") != "@android:style/Theme.NoDisplay":
    raise SystemExit("P9-W07b debug Activity boundary changed")

for marker in [
        "field_diagnostics_adapter_complete=true",
        "field_diagnostics_category_count=8",
        "field_diagnostics_executed_category_count=5",
        "field_diagnostics_not_run_category_count=3",
        "field_diagnostics_1_category=release.bundle",
        "field_diagnostics_8_category=manual.scenario_matrix",
        "release_evidence_target_report_admitted=false",
        "field_diagnostics_automatic_upload_enabled=false"]:
    if marker not in adapter:
        raise SystemExit(f"P9-W07b adapter marker missing: {marker}")
if "production_document_scope=true" not in doc:
    raise SystemExit("P9-W07b production development document marker missing")
PY

if grep -Fq 'FieldDiagnosticsProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P9-W07b debug probe leaked into main/release manifest" >&2
  exit 1
fi
if grep -Fq 'FieldDiagnosticsProjection' "$ROOT_DIR/$RUNTIME" \
    || grep -Fq 'FieldDiagnosticsProjection' "$ROOT_DIR/$GOVERNANCE"; then
  echo "P9-W07b projection was wired into production Services" >&2
  exit 1
fi
if grep -Eiq \
    'import android[.]|java[.]io|java[.]net|PackageManager|ServiceManager|CarProperty|VehicleHal|/proc/|/dev/|ioctl|sysfs' \
    "$ROOT_DIR/$PROJECTION"; then
  echo "P9-W07b projection reads platform, storage, network, vehicle or hardware state" >&2
  exit 1
fi

for forbidden in \
    '"${ADB_DEVICE[@]}" install' \
    '"${ADB_DEVICE[@]}" uninstall' \
    'shell pm install' \
    'shell pm uninstall' \
    'shell cmd rollback' \
    'adb push' \
    'curl ' \
    'gh issue' \
    'echo "$PROBE_LOG"' \
    'printf "$PROBE_LOG"'; do
  if grep -Fq "$forbidden" "$ROOT_DIR/$ADAPTER"; then
    echo "P9-W07b adapter installs, uploads or exposes raw evidence" >&2
    exit 1
  fi
done
grep -Fq 'probe_central_brain_android_field_diagnostics.sh' "$ROOT_DIR/$INSTALLER" \
  || { echo "P9-W07b installer marker missing" >&2; exit 1; }

printf '%s\n' \
  'Central Brain Android P9-W07b field diagnostics probe check passed' \
  'field_diagnostics_projection_defined=true' \
  'field_diagnostics_audit_key_count=31' \
  'field_diagnostics_android_debug_probe_available=true' \
  'field_diagnostics_android_debug_probe_executed=true' \
  'field_diagnostics_probe_android13_arm64_verified=true' \
  'field_diagnostics_target_adapter_defined=true' \
  'field_diagnostics_target_category_execution_complete=false' \
  'release_evidence_target_report_admitted=false' \
  'release_evidence_runtime_diagnostics_wired=false' \
  'release_evidence_retest_workflow_wired=false' \
  'release_evidence_automatic_upload_enabled=false' \
  'field_diagnostics_android13_arm64_verified=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
