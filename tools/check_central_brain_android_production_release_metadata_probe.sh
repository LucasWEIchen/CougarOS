#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-REL-001, S2-SAF-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_p9_production_release_metadata_probe.json"
ADMISSION_CONTRACT="central-brain/contracts/central_brain_android_p9_production_release_admission.json"
PROJECTION="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/release/ProductionReleaseMetadataProjection.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/release/ProductionReleaseMetadataProjectionTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/release/ProductionReleaseMetadataProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
DRY_RUN="tools/probe_central_brain_android_release_metadata.sh"
INSTALLER="tools/install_central_brain_android_runtime.sh"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
DOC="docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md"

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
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P9-W05b marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$CONTRACT" "$ADMISSION_CONTRACT" "$PROJECTION" "$TEST" "$PROBE" \
    "$DEBUG_MANIFEST" "$MAIN_MANIFEST" "$DRY_RUN" "$INSTALLER" "$RUNTIME" \
    "$GOVERNANCE" "$DOC"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P9-W05b file missing: $file" >&2; exit 1; }
done

python3 -B - "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$ADMISSION_CONTRACT" \
    "$ROOT_DIR/$PROJECTION" "$ROOT_DIR/$TEST" "$ROOT_DIR/$PROBE" \
    "$ROOT_DIR/$DEBUG_MANIFEST" "$ROOT_DIR/$DRY_RUN" <<'PY'
import json
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

(contract_path, admission_path, projection_path, test_path, probe_path,
 manifest_path, dry_run_path) = map(pathlib.Path, sys.argv[1:])
contract = json.loads(contract_path.read_text(encoding="utf-8"))
admission = json.loads(admission_path.read_text(encoding="utf-8"))
projection = projection_path.read_text(encoding="utf-8")
test = test_path.read_text(encoding="utf-8")
probe = probe_path.read_text(encoding="utf-8")
dry_run = dry_run_path.read_text(encoding="utf-8")

if contract.get("schema_version") != "1.0.0" \
        or contract.get("profile_id") \
        != "android13-p9-production-release-metadata-probe-v1" \
        or contract.get("maturity") \
        != "debug_probe_available_target_execution_pending":
    raise SystemExit("P9-W05b contract identity changed")
if contract.get("req_ids") != [
        "S2-REL-001", "S2-SAF-001", "S2-OBS-001",
        "DEL-001", "DEL-004", "DEL-005"]:
    raise SystemExit("P9-W05b Req ID set changed")

expected_packages = [
    ("runtime-service", "com.centralbrain.runtime", 3),
    ("demo-hmi", "com.centralbrain.demo", 1),
    ("client2-demo", "com.tuanjie.urasclient2", 1),
]
actual_packages = [
    (item.get("package_id"), item.get("package_name"),
     item.get("repository_version_code"))
    for item in contract.get("package_queries", [])
]
if actual_packages != expected_packages:
    raise SystemExit("P9-W05b package query set changed")
admission_packages = [
    (item.get("package_id"), item.get("package_name"),
     item.get("repository_version_code"))
    for item in admission.get("required_package_set", [])
]
if actual_packages != admission_packages:
    raise SystemExit("P9-W05a and W05b package metadata differ")

metadata = contract.get("metadata_projection", {})
allowed = metadata.get("allowed_audit_keys", [])
if metadata.get("audit_key_count") != 27 or len(allowed) != 27 \
        or len(set(allowed)) != 27:
    raise SystemExit("P9-W05b audit key count changed")
match = re.search(
    r'buildAllowedAuditKeys\(\).*?new ArrayList<>\(List[.]of\((.*?)\)\);',
    projection,
    re.DOTALL,
)
if not match:
    raise SystemExit("P9-W05b Java audit key catalog missing")
java_allowed = re.findall(r'"([a-z0-9_]+)"', match.group(1))
if java_allowed != allowed:
    raise SystemExit("P9-W05b Java and JSON audit key catalogs differ")
if metadata.get("forbidden_projection_fields") != [
        "signer_digest", "certificate", "package_name", "package_path",
        "source_dir", "archive_path", "device_serial", "device_fingerprint",
        "raw_log", "payload"]:
    raise SystemExit("P9-W05b forbidden projection field set changed")

android_probe = contract.get("android_probe", {})
if android_probe != {
    "component": "com.centralbrain.runtime/.release.ProductionReleaseMetadataProbeActivity",
    "permission": "android.permission.DUMP",
    "debug_only": True,
    "release_source_absent": True,
    "reads_package_manager_metadata": True,
    "reads_signer_or_certificate_bytes": False,
    "logs_package_or_device_identity": False,
    "accepts_content_payload": False,
}:
    raise SystemExit("P9-W05b Android probe boundary changed")

adapter = contract.get("installer_dry_run_adapter", {})
if adapter != {
    "path": "tools/probe_central_brain_android_release_metadata.sh",
    "requires_existing_debug_install": True,
    "requires_exact_android_api": 33,
    "requires_primary_abi": "arm64-v8a",
    "builds_artifacts": False,
    "installs_packages": False,
    "uninstalls_packages": False,
    "executes_rollback": False,
    "persists_raw_log": False,
}:
    raise SystemExit("P9-W05b dry-run adapter boundary changed")

state = contract.get("claim_state", {})
for key in [
    "release_metadata_projection_defined",
    "release_installer_dry_run_adapter_defined",
    "release_android_debug_probe_available",
]:
    if state.get(key) is not True:
        raise SystemExit(f"P9-W05b software claim is false: {key}")
for key in [
    "release_android_debug_probe_executed",
    "production_signer_owner_approved",
    "production_release_candidate_admitted",
    "release_installer_wired",
    "release_rollback_executor_wired",
    "release_android13_arm64_verified",
    "hardware_accessed",
    "production_ready",
    "target_hardware_validated",
]:
    if state.get(key) is not False:
        raise SystemExit(f"P9-W05b forbidden claim was raised: {key}")
if state.get("implementation_stage") != "P9-W05":
    raise SystemExit("P9-W05b implementation stage changed")

for marker in [
    "exactInstalledSetProducesRedactedCountsButNeverAdmission",
    "missingPackageRemainsValidObservationAndFailsClosed",
    "projectionUsesExactAllowlistedKeys",
    "projectionContainsOnlyCountsBooleansAndNoIdentifiers",
    "projectionKeepsExecutionAndReadinessClaimsFalse",
    "repositoryClaimsProbeAvailableButNotExecuted",
    "observationSetMustRemainExact",
]:
    if marker not in test:
        raise SystemExit(f"P9-W05b JVM marker missing: {marker}")

for marker in [
    "PackageManager.PackageInfoFlags.of(0)",
    "getLongVersionCode()",
    "checkSignatures(runtimePackage, packageName)",
    'TAG = "CbReleaseProbe"',
    'getStringExtra("nonce")',
    'nonce.matches("[0-9]{1,24}")',
]:
    if marker not in probe:
        raise SystemExit(f"P9-W05b probe marker missing: {marker}")
for forbidden in [
    "GET_SIGNING_CERTIFICATES", "SigningInfo", "import android.content.pm.Signature",
    "Signature[]", "toByteArray()",
    "sourceDir", "dataDir", "Build.FINGERPRINT", "Build.SERIAL",
    "getExtras()", "getData()", "getClipData()", "putExtra(",
]:
    if forbidden in probe:
        raise SystemExit(f"P9-W05b probe crosses metadata boundary: {forbidden}")

android = "{http://schemas.android.com/apk/res/android}"
root = ET.parse(manifest_path).getroot()
queries = [node.get(android + "name") for node in root.findall("./queries/package")]
if queries != [item[1] for item in expected_packages]:
    raise SystemExit("P9-W05b debug package visibility set changed")
activities = [node for node in root.findall("./application/activity")
              if node.get(android + "name")
              == ".release.ProductionReleaseMetadataProbeActivity"]
if len(activities) != 1:
    raise SystemExit("P9-W05b debug manifest component count changed")
activity = activities[0]
if activity.get(android + "permission") != "android.permission.DUMP" \
        or activity.get(android + "exported") != "true" \
        or activity.get(android + "noHistory") != "true" \
        or activity.get(android + "theme") \
        != "@android:style/Theme.NoDisplay":
    raise SystemExit("P9-W05b debug Activity boundary changed")

for forbidden in [
    '"${ADB_DEVICE[@]}" install', '"${ADB_DEVICE[@]}" uninstall',
    "shell pm install", "shell pm uninstall", "shell cmd package install",
    "shell cmd package uninstall", "shell cmd rollback",
    'echo "$PROBE_LOG"', 'printf "$PROBE_LOG"',
]:
    if forbidden in dry_run:
        raise SystemExit(f"P9-W05b dry-run adapter executes or exposes data: {forbidden}")
PY

if grep -Fq 'ProductionReleaseMetadataProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P9-W05b debug probe leaked into the main/release manifest" >&2
  exit 1
fi
if grep -Fq 'com.centralbrain.demo' "$ROOT_DIR/$MAIN_MANIFEST" \
    || grep -Fq 'com.tuanjie.urasclient2' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P9-W05b peer package visibility leaked into the main/release manifest" >&2
  exit 1
fi
if find "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/release" \
    -type f -print 2>/dev/null | grep -q .; then
  if grep -R -Fq 'ProductionReleaseMetadataProbeActivity' \
      "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/release"; then
    echo "P9-W05b debug probe leaked into release source" >&2
    exit 1
  fi
fi
if grep -Fq 'ProductionReleaseMetadataProjection' "$ROOT_DIR/$RUNTIME" \
    || grep -Fq 'ProductionReleaseMetadataProjection' "$ROOT_DIR/$GOVERNANCE"; then
  echo "P9-W05b projection was wired into production Services" >&2
  exit 1
fi
if grep -Eiq \
    'android[.]|java[.]io|java[.]nio[.]file|java[.]net|okhttp|https?://|PackageManager|KeyStore|Room[.(]|CarPropertyManager|VehicleHal|/proc/|/dev/|ioctl|sysfs' \
    "$ROOT_DIR/$PROJECTION"; then
  echo "P9-W05b projection reads platform, storage, network, vehicle or hardware state" >&2
  exit 1
fi

require_text "$INSTALLER" 'probe_central_brain_android_release_metadata.sh'
for marker in \
  'release_metadata_probe_complete=true' \
  'release_metadata_projection_verified=true' \
  'release_package_query_count=3' \
  'release_installed_package_count=[0-3]' \
  'release_repository_version_match_count=[0-3]' \
  'release_same_signer_pair_query_count=2' \
  'release_same_signer_pair_match_count=[0-2]' \
  'release_candidate_metadata_complete=false' \
  'release_dry_run_admitted=false' \
  'production_signer_owner_approved=false' \
  'production_release_candidate_admitted=false' \
  'release_installer_wired=false' \
  'release_install_executed=false' \
  'release_uninstall_executed=false' \
  'release_rollback_executor_wired=false' \
  'release_rollback_executed=false' \
  'release_signer_material_logged=false' \
  'release_certificate_material_logged=false' \
  'release_package_name_logged=false' \
  'release_device_identity_logged=false' \
  'release_raw_log_persisted=false' \
  'release_android_debug_probe_available=true' \
  'release_android_debug_probe_executed=true'; do
  require_text "$DRY_RUN" "$marker"
done

require_text "$DOC" 'DEBUG_METADATA_PROBE_AVAILABLE / TARGET_EXECUTION_PENDING'
require_text "README.md" 'P9 Production Release Metadata Probe'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W05b production release metadata Android probe'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W05b production release metadata probe trace'
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" 'Android P9-W05b Production Release Metadata Probe Contract'
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" 'P9-W05b production release metadata probe architecture'
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" 'P9-W05b production release metadata probe detailed design'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'Android P9-W05b Production Release Metadata Probe'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W05b Production Release Metadata Probe Driver/HAL Boundary'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'DEV-095 P9-W05b metadata observation is not production signer qualification'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'ISSUE-052 P9 production signer and rollback owner evidence is unavailable'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W05b Production Release Metadata Probe progress'

printf '%s\n' \
  'Central Brain Android production release metadata probe check passed' \
  'release_metadata_projection_defined=true' \
  'release_package_query_count=3' \
  'release_metadata_audit_key_count=27' \
  'release_installer_dry_run_adapter_defined=true' \
  'release_android_debug_probe_available=true' \
  'release_android_debug_probe_executed=true' \
  'release_metadata_probe_android13_arm64_verified=true' \
  'production_signer_owner_approved=false' \
  'production_release_candidate_admitted=false' \
  'release_installer_wired=false' \
  'release_rollback_executor_wired=false' \
  'release_android13_arm64_verified=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'implementation_stage=P9-W05'
