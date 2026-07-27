#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-OBS-001, S2-REL-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_p9_release_retest_workflow.json"
SOURCE_CONTRACT="central-brain/contracts/central_brain_android_p9_release_evidence_envelope.json"
REMOTE_CONTRACT="central-brain/contracts/central_brain_github_remote_testing.json"
SOURCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/release/ReleaseRetestWorkflow.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/release/ReleaseRetestWorkflowTest.java"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
DOC="docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md"
REMOTE_DOC="docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md"

for file in "$CONTRACT" "$SOURCE_CONTRACT" "$REMOTE_CONTRACT" "$SOURCE" "$TEST" \
    "$RUNTIME" "$GOVERNANCE" "$DOC" "$REMOTE_DOC"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P9-W07c file missing: $file" >&2; exit 1; }
done

python3 -B - "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$SOURCE_CONTRACT" \
    "$ROOT_DIR/$REMOTE_CONTRACT" "$ROOT_DIR/$SOURCE" "$ROOT_DIR/$TEST" \
    "$ROOT_DIR/$DOC" "$ROOT_DIR/$REMOTE_DOC" <<'PY'
import json
import pathlib
import re
import sys

contract_path, source_contract_path, remote_contract_path, source_path, test_path, \
        doc_path, remote_doc_path = map(
    pathlib.Path, sys.argv[1:]
)
contract = json.loads(contract_path.read_text(encoding="utf-8"))
source_contract = json.loads(source_contract_path.read_text(encoding="utf-8"))
remote_contract = json.loads(remote_contract_path.read_text(encoding="utf-8"))
source = source_path.read_text(encoding="utf-8")
test = test_path.read_text(encoding="utf-8")
doc = doc_path.read_text(encoding="utf-8")
remote_doc = remote_doc_path.read_text(encoding="utf-8")

if contract.get("schema_version") != 1 \
        or contract.get("profile_id") \
        != "android13-p9-release-retest-workflow-v1" \
        or contract.get("maturity") \
        != "software_state_machine_defined_target_retest_pending":
    raise SystemExit("P9-W07c contract identity changed")
if contract.get("source_profile_id") != source_contract.get("profile_id"):
    raise SystemExit("P9-W07a and W07c profile binding changed")
if contract.get("source_issue_contract_id") != remote_contract.get("contract_id"):
    raise SystemExit("B5 and P9-W07c issue contract binding changed")
if contract.get("requirement_ids") != [
        "S2-OBS-001", "S2-REL-001", "DEL-001", "DEL-004", "DEL-005"]:
    raise SystemExit("P9-W07c Req ID set changed")

states = [
    "state/triage", "state/reproduced", "state/fix-ready", "state/retest",
    "state/verified",
]
if contract.get("issue_state_count") != 5 \
        or contract.get("issue_states") != states:
    raise SystemExit("P9-W07c issue state catalog changed")
if remote_contract.get("issue_contract", {}).get("issue_state_machine") != states:
    raise SystemExit("B5 and P9-W07c issue state catalogs differ")
match = re.search(r'enum IssueState \{(.*?)\n    \}', source, re.DOTALL)
if not match:
    raise SystemExit("P9-W07c Java issue state enum missing")
if re.findall(r'\("(state/[a-z-]+)"\)', match.group(1)) != states:
    raise SystemExit("P9-W07c Java and JSON state catalogs differ")

transitions = contract.get("transitions", [])
expected_transitions = [
    {"from": "state/triage", "to": "state/reproduced", "actor": "MAINTAINER"},
    {"from": "state/reproduced", "to": "state/fix-ready", "actor": "MAINTAINER"},
    {"from": "state/fix-ready", "to": "state/retest", "actor": "MAINTAINER"},
    {"from": "state/retest", "to": "state/verified", "actor": "TARGET_TESTER"},
    {"from": "state/retest", "to": "state/fix-ready", "actor": "TARGET_TESTER"},
]
if contract.get("transition_count") != 5 or transitions != expected_transitions:
    raise SystemExit("P9-W07c transition matrix changed")

replacement = contract.get("replacement_release", {})
if replacement != {
    "required_fields": [
        "release_tag", "source_git_commit", "archive_sha256",
        "release_set_digest", "release_owner_approval_digest",
    ],
    "strictly_newer_than_current_required": True,
    "distinct_source_archive_release_set_required": True,
    "old_release_asset_replacement_allowed": False,
    "publishes_release": False,
    "installs_release": False,
}:
    raise SystemExit("P9-W07c replacement release boundary changed")

admission = contract.get("target_report_admission", {})
if admission != {
    "target_mode_required": True,
    "replacement_identity_match_required": True,
    "all_eight_categories_executed_required": True,
    "all_eight_categories_pass_required_for_verified": True,
    "target_owner_approval_digest_required": True,
    "release_owner_approval_digest_required": True,
    "diagnostics_owner_approval_digest_required": True,
    "tester_verification_digest_required": True,
    "approval_digests_distinct_required": True,
    "signer_cohort_observed_required": True,
    "privacy_confirmation_required": True,
    "automatic_upload_allowed": False,
    "sets_target_hardware_validated": False,
    "sets_production_ready": False,
}:
    raise SystemExit("P9-W07c target admission boundary changed")

close_rule = contract.get("issue_close_rule", {})
if close_rule != {
    "verified_state_required": True,
    "named_replacement_release_required": True,
    "target_tester_verification_required": True,
    "automatic_issue_close_allowed": False,
    "mutates_github_issue": False,
}:
    raise SystemExit("P9-W07c issue close boundary changed")

if contract.get("forbidden_inputs_or_actions") != [
    "issue_title_or_body", "device_serial_or_fingerprint",
    "signer_or_certificate_material", "target_input_file",
    "raw_or_unreviewed_log", "internal_path", "user_or_model_text",
    "memory_or_token", "vehicle_payload", "github_issue_mutation",
    "release_publication", "package_install_or_uninstall",
    "rollback_execution", "automatic_upload",
]:
    raise SystemExit("P9-W07c forbidden boundary changed")

state = contract.get("claim_state", {})
if state.get("release_retest_state_machine_defined") is not True \
        or state.get("release_retest_issue_state_count") != 5 \
        or state.get("release_retest_transition_count") != 5:
    raise SystemExit("P9-W07c software claims changed")
for key in [
        "release_retest_replacement_release_published",
        "release_evidence_target_report_admitted",
        "release_evidence_retest_workflow_wired",
        "release_retest_github_issue_mutation_wired",
        "release_retest_automatic_issue_close_allowed",
        "release_evidence_automatic_upload_enabled",
        "release_retest_android13_arm64_verified",
        "hardware_accessed", "production_ready", "target_hardware_validated"]:
    if state.get(key) is not False:
        raise SystemExit(f"P9-W07c forbidden claim was raised: {key}")
if state.get("implementation_stage") != "P9-W07":
    raise SystemExit("P9-W07c implementation stage changed")

for marker in [
        "exactIssueStateCatalogIsStableAndOrdered",
        "maintainerAndTesterCompleteNamedReplacementWorkflow",
        "actorAndTransitionMatrixFailsClosed",
        "replacementMustBeStrictlyNewerAndArtifactDistinct",
        "verificationRequiresMatchingCompleteTargetOwnerEvidence",
        "failedRetestReturnsSameIssueToFixReadyAndRequiresNewRelease",
        "workflowDigestIsDeterministicAndBindsStateAndEvidence",
        "workflowRejectsFreeformOrMalformedIdentity",
        "repositoryClaimsRemainUnpublishedUnmutatedAndUnqualified"]:
    if marker not in test:
        raise SystemExit(f"P9-W07c JVM marker missing: {marker}")
if "production_document_scope=true" not in doc \
        or "production_document_scope=true" not in remote_doc:
    raise SystemExit("P9-W07c production development document marker missing")
PY

if grep -Eiq \
    'import android[.]|java[.]io|java[.]net|PackageManager|ServiceManager|CarProperty|VehicleHal|/proc/|/dev/|ioctl|sysfs|ProcessBuilder|Runtime[.]getRuntime|com[.]github|org[.]kohsuke|gh issue|curl|https?://' \
    "$ROOT_DIR/$SOURCE"; then
  echo "P9-W07c state machine accesses platform, GitHub, storage, network, vehicle or hardware" >&2
  exit 1
fi
if grep -Fq 'ReleaseRetestWorkflow' "$ROOT_DIR/$RUNTIME" \
    || grep -Fq 'ReleaseRetestWorkflow' "$ROOT_DIR/$GOVERNANCE"; then
  echo "P9-W07c state machine was wired into production Services" >&2
  exit 1
fi

printf '%s\n' \
  'Central Brain Android P9-W07c release/retest workflow check passed' \
  'release_retest_state_machine_defined=true' \
  'release_retest_issue_state_count=5' \
  'release_retest_transition_count=5' \
  'release_retest_replacement_release_published=false' \
  'release_evidence_target_report_admitted=false' \
  'release_evidence_retest_workflow_wired=false' \
  'release_retest_github_issue_mutation_wired=false' \
  'release_retest_automatic_issue_close_allowed=false' \
  'release_evidence_automatic_upload_enabled=false' \
  'release_retest_android13_arm64_verified=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
