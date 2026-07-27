#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MEM-001, S2-SAF-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_p9_privacy_redaction_audit.json"
PROJECTION="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/privacy/PrivacyRedactionAuditProjection.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/privacy/PrivacyRedactionAuditProjectionTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/privacy/PrivacyRedactionAuditProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
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
    || { echo "P9-W04c marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$CONTRACT" "$PROJECTION" "$TEST" "$PROBE" "$DEBUG_MANIFEST" \
    "$MAIN_MANIFEST" "$INSTALLER" "$RUNTIME" "$GOVERNANCE" "$DOC"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P9-W04c file missing: $file" >&2; exit 1; }
done

python3 - "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$PROJECTION" \
    "$ROOT_DIR/$TEST" "$ROOT_DIR/$PROBE" "$ROOT_DIR/$DEBUG_MANIFEST" <<'PY'
import json
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

contract_path, projection_path, test_path, probe_path, manifest_path = map(
    pathlib.Path, sys.argv[1:])
contract = json.loads(contract_path.read_text(encoding="utf-8"))
projection = projection_path.read_text(encoding="utf-8")
test = test_path.read_text(encoding="utf-8")
probe = probe_path.read_text(encoding="utf-8")

if contract.get("schema_version") != "1.0.0" \
        or contract.get("profile_id") != "android13-p9-privacy-redaction-audit-v1" \
        or contract.get("maturity") \
        != "debug_probe_available_target_execution_pending":
    raise SystemExit("P9-W04c contract identity changed")

allowed = contract.get("allowed_audit_keys", [])
if contract.get("audit_key_count") != 21 or len(allowed) != 21 \
        or len(set(allowed)) != 21:
    raise SystemExit("P9-W04c audit key count changed")
match = re.search(
    r'buildAllowedAuditKeys\(\).*?new ArrayList<>\(List[.]of\((.*?)\)\);',
    projection,
    re.DOTALL,
)
if not match:
    raise SystemExit("P9-W04c Java audit key catalog missing")
java_allowed = re.findall(r'"([a-z0-9_]+)"', match.group(1))
if java_allowed != allowed:
    raise SystemExit("P9-W04c Java and JSON audit key catalogs differ")

expected_forbidden = [
    "surface_id",
    "source_class",
    "raw_user_text",
    "raw_model_output",
    "raw_vehicle_payload",
    "location",
    "owner_approval_reference",
    "authorization_digest",
    "consent_digest",
    "device_identity",
]
if contract.get("forbidden_projection_fields") != expected_forbidden:
    raise SystemExit("P9-W04c forbidden projection fields changed")

android_probe = contract.get("android_probe", {})
if android_probe != {
    "component": "com.centralbrain.runtime/.privacy.PrivacyRedactionAuditProbeActivity",
    "permission": "android.permission.DUMP",
    "debug_only": True,
    "release_source_absent": True,
    "accepts_content_payload": False,
}:
    raise SystemExit("P9-W04c Android probe boundary changed")

state = contract.get("claim_state", {})
if state.get("privacy_redacted_audit_projection_defined") is not True \
        or state.get("privacy_android_debug_probe_available") is not True:
    raise SystemExit("P9-W04c software availability claim is false")
for key in [
    "privacy_android_debug_probe_executed",
    "privacy_android13_arm64_verified",
    "privacy_owner_policy_approved",
    "privacy_repository_mutation_wired",
    "privacy_runtime_lifecycle_wiring_complete",
    "hardware_accessed",
    "production_ready",
    "target_hardware_validated",
]:
    if state.get(key) is not False:
        raise SystemExit(f"P9-W04c forbidden claim was raised: {key}")
if state.get("implementation_stage") != "P9-W04":
    raise SystemExit("P9-W04c implementation stage changed")

for marker in [
    "currentDraftProjectionUsesExactAllowlistedKeysAndCounts",
    "projectionContainsOnlyDigestsCountsAndBooleanMetadata",
    "projectionKeepsContentAndAuthorityClaimsFalse",
    "repositoryClaimsProbeAvailableButNotExecutedOrQualified",
]:
    if marker not in test:
        raise SystemExit(f"P9-W04c JVM marker missing: {marker}")
if "snapshot.auditMetadata()" not in probe or 'TAG = "CbPrivacyProbe"' not in probe:
    raise SystemExit("P9-W04c probe does not use the fixed projection")
if 'nonce.matches("[0-9]{1,24}")' not in probe:
    raise SystemExit("P9-W04c probe nonce is not bounded")
if "getStringExtra(\"nonce\")" not in probe:
    raise SystemExit("P9-W04c probe correlation nonce missing")
for forbidden in ["getExtras()", "getData()", "getClipData()", "putExtra(", "toString()"]:
    if forbidden in probe:
        raise SystemExit(f"P9-W04c probe accepts or formats unbounded input: {forbidden}")

android = "{http://schemas.android.com/apk/res/android}"
root = ET.parse(manifest_path).getroot()
activities = [node for node in root.findall("./application/activity")
              if node.get(android + "name")
              == ".privacy.PrivacyRedactionAuditProbeActivity"]
if len(activities) != 1:
    raise SystemExit("P9-W04c debug manifest component count changed")
activity = activities[0]
if activity.get(android + "permission") != "android.permission.DUMP" \
        or activity.get(android + "exported") != "true" \
        or activity.get(android + "noHistory") != "true":
    raise SystemExit("P9-W04c debug probe permission/export boundary changed")
PY

if grep -Fq 'PrivacyRedactionAuditProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P9-W04c debug probe leaked into the main/release manifest" >&2
  exit 1
fi
if find "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/release" \
    -type f -print 2>/dev/null | grep -q .; then
  if grep -R -Fq 'PrivacyRedactionAuditProbeActivity' \
      "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/release"; then
    echo "P9-W04c debug probe leaked into release source" >&2
    exit 1
  fi
fi
if grep -Fq 'PrivacyRedactionAuditProjection' "$ROOT_DIR/$RUNTIME" \
    || grep -Fq 'PrivacyRedactionAuditProjection' "$ROOT_DIR/$GOVERNANCE"; then
  echo "P9-W04c projection was wired into production Services" >&2
  exit 1
fi
if grep -Eiq \
    'android[.]|java[.]io|java[.]nio[.]file|java[.]net|okhttp|https?://|Room[.(]|CarPropertyManager|VehicleHal|/proc/|/dev/|ioctl|sysfs' \
    "$ROOT_DIR/$PROJECTION"; then
  echo "P9-W04c main projection reads platform, storage, network, vehicle or hardware state" >&2
  exit 1
fi

for marker in \
  'privacy_redaction_probe_complete=true' \
  'privacy_redacted_audit_projection_verified=true' \
  'privacy_inventory_digest=' \
  'privacy_policy_body_digest=' \
  'privacy_surface_count=12' \
  'privacy_unresolved_surface_count=2' \
  'privacy_admission_code_count=3' \
  'privacy_operation_code_count=1' \
  'privacy_current_policy_admitted=false' \
  'privacy_raw_user_text_logged=false' \
  'privacy_raw_model_output_logged=false' \
  'privacy_raw_vehicle_payload_logged=false' \
  'privacy_location_logged=false' \
  'privacy_owner_reference_logged=false' \
  'privacy_authorization_digest_logged=false' \
  'privacy_consent_digest_logged=false' \
  'privacy_repository_mutation_wired=false' \
  'privacy_runtime_lifecycle_wiring_complete=false' \
  'privacy_android_debug_probe_available=true' \
  'privacy_android_debug_probe_executed=true'; do
  require_text "$INSTALLER" "$marker"
done

require_text "$DOC" 'W04C_SOFTWARE_VERIFIED / TARGET_PROBE_PENDING'
require_text "README.md" 'P9 Privacy Redaction/Audit Probe'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W04c privacy redaction/audit Android probe'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W04c privacy redaction/audit probe trace'
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" 'Android P9-W04c Privacy Redaction/Audit Probe Contract'
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" 'P9-W04c privacy redaction/audit probe architecture'
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" 'P9-W04c privacy redaction/audit probe detailed design'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'Android P9-W04c Privacy Redaction/Audit Probe'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W04c Privacy Redaction/Audit Probe Driver/HAL Boundary'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'DEV-093 P9-W04c probe availability is not owner policy or target evidence'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'ISSUE-051 P9 durable privacy lifecycle policies are incomplete'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W04c Privacy Redaction/Audit Probe progress'

printf '%s\n' \
  'Central Brain Android privacy redaction/audit probe check passed' \
  'privacy_redacted_audit_projection_defined=true' \
  'privacy_audit_key_count=21' \
  'privacy_forbidden_projection_field_count=10' \
  'privacy_android_debug_probe_available=true' \
  'privacy_android_debug_probe_executed=true' \
  'privacy_redaction_probe_android13_arm64_verified=true' \
  'privacy_android13_arm64_verified=false' \
  'privacy_owner_policy_approved=false' \
  'privacy_repository_mutation_wired=false' \
  'privacy_runtime_lifecycle_wiring_complete=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'implementation_stage=P9-W04'
