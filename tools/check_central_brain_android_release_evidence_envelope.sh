#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-OBS-001, S2-REL-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_p9_release_evidence_envelope.json"
SOURCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/release/ReleaseEvidenceEnvelope.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/release/ReleaseEvidenceEnvelopeTest.java"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
DOC="docs/CENTRAL_BRAIN_RELEASE_EVIDENCE_FIELD_DIAGNOSTICS.md"

for file in "$CONTRACT" "$SOURCE" "$TEST" "$RUNTIME" "$GOVERNANCE" "$DOC"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P9-W07a file missing: $file" >&2; exit 1; }
done

python3 -B - "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$SOURCE" \
    "$ROOT_DIR/$TEST" "$ROOT_DIR/$DOC" <<'PY'
import json
import pathlib
import re
import sys

contract_path, source_path, test_path, doc_path = map(pathlib.Path, sys.argv[1:])
contract = json.loads(contract_path.read_text(encoding="utf-8"))
source = source_path.read_text(encoding="utf-8")
test = test_path.read_text(encoding="utf-8")
doc = doc_path.read_text(encoding="utf-8")

if contract.get("schema_version") != 1 \
        or contract.get("profile_id") != "android13-p9-release-evidence-envelope-v1" \
        or contract.get("maturity") \
        != "software_contract_defined_target_evidence_pending":
    raise SystemExit("P9-W07a contract identity changed")
if contract.get("requirement_ids") != [
        "S2-OBS-001", "S2-REL-001", "DEL-001", "DEL-004", "DEL-005"]:
    raise SystemExit("P9-W07a Req ID set changed")
if contract.get("evidence_modes") != ["HOST_SYNTHETIC", "TARGET"]:
    raise SystemExit("P9-W07a evidence mode catalog changed")

required_identity = [
    "release_tag", "source_git_commit", "archive_sha256", "delivery_id",
    "release_set_digest", "device_alias", "evidence_reference",
    "signer_cohort_observed", "privacy_confirmed",
    "raw_or_derived_device_identity_included", "automatic_upload_enabled",
]
if contract.get("required_identity_fields") != required_identity:
    raise SystemExit("P9-W07a required identity field set changed")

categories = [
    "release.bundle", "installer.dry_run", "installer.execute", "demo.launch",
    "client2.launch", "runtime.service", "diagnostics.service",
    "manual.scenario_matrix",
]
if contract.get("diagnostic_category_count") != 8 \
        or contract.get("diagnostic_categories") != categories:
    raise SystemExit("P9-W07a diagnostic category catalog changed")
if contract.get("diagnostic_statuses") != ["PASS", "FAIL", "BLOCKED", "NOT_RUN"]:
    raise SystemExit("P9-W07a diagnostic status catalog changed")
if contract.get("diagnostic_fact_fields") != [
        "category", "status", "result_code", "detail_digest"]:
    raise SystemExit("P9-W07a diagnostic fact fields changed")

match = re.search(r'enum DiagnosticCategory \{(.*?)\n    \}', source, re.DOTALL)
if not match:
    raise SystemExit("P9-W07a Java diagnostic category enum missing")
java_categories = re.findall(r'\("([a-z0-9._]+)"\)', match.group(1))
if java_categories != categories:
    raise SystemExit("P9-W07a Java and JSON category catalogs differ")

policy = contract.get("github_policy", {})
if policy != {
    "privacy_confirmation_required": True,
    "raw_or_derived_device_identity_allowed": False,
    "automatic_upload_allowed": False,
    "arbitrary_text_or_payload_allowed": False,
    "signer_material_allowed": False,
    "target_input_allowed": False,
    "raw_log_allowed": False,
}:
    raise SystemExit("P9-W07a GitHub policy changed")

eligibility = contract.get("target_owner_review_eligibility", {})
if eligibility != {
    "target_mode_required": True,
    "target_owner_approval_digest_required": True,
    "all_diagnostic_categories_executed_required": True,
    "sets_target_hardware_validated": False,
    "sets_production_ready": False,
}:
    raise SystemExit("P9-W07a target eligibility boundary changed")

state = contract.get("claim_state", {})
for key in [
        "release_evidence_envelope_defined",
        "release_evidence_report_digest_defined"]:
    if state.get(key) is not True:
        raise SystemExit(f"P9-W07a software claim is false: {key}")
if state.get("release_evidence_diagnostic_category_count") != 8:
    raise SystemExit("P9-W07a claim category count changed")
for key in [
        "release_evidence_target_owner_approved",
        "release_evidence_target_report_admitted",
        "release_evidence_runtime_diagnostics_wired",
        "release_evidence_retest_workflow_wired",
        "release_evidence_automatic_upload_enabled",
        "release_evidence_android13_arm64_verified",
        "hardware_accessed", "production_ready", "target_hardware_validated"]:
    if state.get(key) is not False:
        raise SystemExit(f"P9-W07a forbidden claim was raised: {key}")
if state.get("implementation_stage") != "P9-W07":
    raise SystemExit("P9-W07a implementation stage changed")

for marker in [
        "exactDiagnosticCatalogIsStableAndOrdered",
        "hostEnvelopeIsGithubSafeButNeverTargetEvidence",
        "targetEnvelopeRequiresOwnerAndAllDiagnosticsForReviewEligibility",
        "githubPolicyRejectsPrivacyIdentityOrUploadViolations",
        "reportDigestIsDeterministicAndBindsDiagnosticState",
        "identityRejectsNonCanonicalReleaseOrSensitiveFreeform",
        "diagnosticStatusAndResultCodeMustAgree",
        "reportRejectsMissingOrReorderedCategory",
        "repositoryClaimsRemainUnwiredUnexecutedAndUnqualified"]:
    if marker not in test:
        raise SystemExit(f"P9-W07a JVM marker missing: {marker}")
for marker in [
        "release_evidence_envelope_defined=true",
        "release_evidence_diagnostic_category_count=8",
        "release_evidence_target_owner_approved=false",
        "release_evidence_runtime_diagnostics_wired=false",
        "release_evidence_retest_workflow_wired=false",
        "release_evidence_android13_arm64_verified=false"]:
    if marker not in doc:
        raise SystemExit(f"P9-W07a design marker missing: {marker}")
PY

if grep -Eiq \
    'import android[.]|java[.]io|java[.]net|PackageManager|ServiceManager|CarProperty|VehicleHal|/proc/|/dev/|ioctl|sysfs|Runtime[.]getRuntime|ProcessBuilder' \
    "$ROOT_DIR/$SOURCE"; then
  echo "P9-W07a envelope reads platform, storage, network, vehicle or hardware state" >&2
  exit 1
fi
if grep -Fq 'ReleaseEvidenceEnvelope' "$ROOT_DIR/$RUNTIME" \
    || grep -Fq 'ReleaseEvidenceEnvelope' "$ROOT_DIR/$GOVERNANCE"; then
  echo "P9-W07a envelope was wired into production Services" >&2
  exit 1
fi

printf '%s\n' \
  'Central Brain Android P9-W07a release evidence envelope check passed' \
  'release_evidence_envelope_defined=true' \
  'release_evidence_diagnostic_category_count=8' \
  'release_evidence_report_digest_defined=true' \
  'release_evidence_target_owner_approved=false' \
  'release_evidence_target_report_admitted=false' \
  'release_evidence_runtime_diagnostics_wired=false' \
  'release_evidence_retest_workflow_wired=false' \
  'release_evidence_automatic_upload_enabled=false' \
  'release_evidence_android13_arm64_verified=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
