#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004/005/006, NV-F-001/012,
# NV-G-006/007, NV-P-002, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_github_remote_testing.json"
PROFILE="$ROOT_DIR/central-brain/delivery/android-hybrid/central-brain.android-hybrid-delivery-profile.json"
DOC="$ROOT_DIR/docs/CENTRAL_BRAIN_GITHUB_REMOTE_HARDWARE_TESTING.md"
ISSUE_FORM="$ROOT_DIR/.github/ISSUE_TEMPLATE/hardware-test.yml"
ISSUE_CONFIG="$ROOT_DIR/.github/ISSUE_TEMPLATE/config.yml"
WORKFLOW="$ROOT_DIR/.github/workflows/central-brain-remote-test-contract.yml"
RUNNER="$ROOT_DIR/tools/run_central_brain_android_remote_acceptance.sh"
PUBLICATION_CHECKER="$ROOT_DIR/tools/check_central_brain_github_publication_tree.sh"
ROOT_README_CHECKER="$ROOT_DIR/tools/check_central_brain_root_readme.sh"
REPOSITORY_COMPLETENESS_CHECKER="$ROOT_DIR/tools/check_central_brain_github_repository_completeness.sh"
RETIREMENT_CHECKER="$ROOT_DIR/tools/check_central_brain_python_prototype_retirement.sh"
PRE_PUSH_HOOK="$ROOT_DIR/.githooks/pre-push"

for file in "$CONTRACT" "$PROFILE" "$DOC" "$ISSUE_FORM" "$ISSUE_CONFIG" \
    "$WORKFLOW" "$RUNNER" "$PUBLICATION_CHECKER" "$ROOT_README_CHECKER" \
    "$REPOSITORY_COMPLETENESS_CHECKER" "$RETIREMENT_CHECKER" "$PRE_PUSH_HOOK"; do
  [[ -f "$file" ]] || { echo "missing GitHub remote testing artifact: $file" >&2; exit 1; }
done

python3 -B - "$CONTRACT" "$PROFILE" <<'PY'
import json
import pathlib
import re
import sys

contract = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
profile = json.loads(pathlib.Path(sys.argv[2]).read_text(encoding="utf-8"))

assert contract["schema_version"] == "1.0.0"
assert contract["contract_id"] == "central-brain.github-remote-hardware-testing.v1"
assert contract["stage"] == "B5"
assert set(contract["req_ids"]) == {
    "APP-004", "XSC-001", "XSC-004", "XSC-005", "XSC-006",
    "NV-F-001", "NV-F-012", "NV-G-006", "NV-G-007", "NV-P-002",
    "DEL-001", "DEL-003", "DEL-004", "DEL-005",
}
status = contract["status"]
assert status["local_remote_test_contract_ready"] is True
for key in (
    "github_repository_configured",
    "github_remote_pushed",
    "github_issue_intake_active",
    "gh_cli_repository_access",
    "event_automation_active",
    "first_immutable_release_published",
):
    assert status[key] is True, key
assert status["event_poll_interval_minutes"] == 15
assert status["github_connector_repository_access"] is False
assert status["branch_protection_active"] is False
assert status["branch_protection_unavailable_on_current_private_plan"] is True
for key in (
    "direct_target_access_available",
    "physical_controller_evidence_available",
    "production_ready",
    "target_hardware_validated",
):
    assert status[key] is False, key

release = contract["release_contract"]
assert re.fullmatch(release["tag_pattern"], "android13-hwtest-v0.5.0-rc.2")
assert release["required_assets"] == [
    "central-brain-android13-hybrid.tar.gz",
    "central-brain-android13-hybrid.tar.gz.sha256",
]
assert release["controlled_workstation_build_required"] is True
assert release["github_actions_build_authoritative"] is False
assert release["first_published_tag"] == "android13-hwtest-v0.5.0-rc.2"
assert release["withdrawn_unreleased_tags"][0]["tag"] == "android13-hwtest-v0.5.0-rc.1"

repository = contract["repository"]
assert repository == {
    "owner": "LucasWEIchen",
    "name": "CougarOS",
    "visibility": "private",
    "maintainer": "LucasWEIchen",
    "remote_default_branch": "main",
    "local_publication_branch": "codex/github-publication",
}

publication = contract["publication_history_guard"]
assert publication["checker_path"] == "tools/check_central_brain_github_publication_tree.sh"
assert publication["local_publication_branch"] == "codex/github-publication"
assert publication["remote_default_branch"] == "main"
assert publication["max_reachable_blob_bytes"] == 20 * 1024 * 1024
assert publication["targeted_ref_push_required"] is True
assert publication["mirror_push_allowed"] is False
assert publication["codex_internal_refs_publish_allowed"] is False
assert publication["local_pre_push_hook_path"] == ".githooks/pre-push"
assert publication["local_pre_push_hook_installed"] is True

polling = contract["issue_polling"]
assert polling["automation_id"] == "cougaros-github-issue-maintenance"
assert polling["transport"] == "gh-cli"
assert polling["interval_minutes"] == 15
assert polling["active"] is True
assert polling["first_poll_verified"] is True
assert polling["first_poll_issue_number"] == 1
assert polling["first_poll_transport"] == "gh-cli-fallback"
assert polling["first_poll_connector_result"] == "PRIVATE_REPOSITORY_INACCESSIBLE_422"
assert polling["first_poll_result"] == "CONTROL_PLANE_ONLY"
assert polling["first_poll_hardware_evidence_accepted"] is False
assert polling["automatic_issue_close_allowed"] is False

evidence = contract["evidence_policy"]
assert evidence["github_safe_files"] == [
    "github-safe/summary.env",
    "github-safe/issue-body.md",
]
assert evidence["automatic_upload_enabled"] is False
assert evidence["raw_evidence_upload_allowed"] is False
assert contract["activation_blockers"] == [
    "GITHUB_TESTER_ACCESS_LIST_REQUIRED",
    "PRIVATE_PLAN_REMOTE_BRANCH_PROTECTION_UNAVAILABLE",
]

support = {item["bundle_path"] for item in profile["support_files"]}
for expected in (
    "contracts/central_brain_github_remote_testing.json",
    "docs/CENTRAL_BRAIN_GITHUB_REMOTE_HARDWARE_TESTING.md",
    "tools/run_central_brain_android_remote_acceptance.sh",
):
    assert expected in support, expected

print("github_remote_contract_json_verified=true")
print("github_repository_configured=true")
print("github_issue_intake_active=true")
print("event_poll_interval_minutes=15")
print("first_issue_poll_verified=true")
print("target_hardware_validated=false")
PY

bash -n "$RUNNER" "$PUBLICATION_CHECKER" "$ROOT_README_CHECKER" \
  "$REPOSITORY_COMPLETENESS_CHECKER" \
  "$RETIREMENT_CHECKER" "$PRE_PUSH_HOOK"
"$RUNNER" --help >/dev/null

for field in release_tag source_git_commit archive_sha256 device_alias evidence_reference install_profile \
    test_phase result failing_scenarios expected_behavior actual_behavior \
    github_safe_summary privacy_confirmation; do
  grep -Fq "id: $field" "$ISSUE_FORM" \
    || { echo "hardware-test Issue Form field missing: $field" >&2; exit 1; }
done
grep -Fq 'labels:' "$ISSUE_FORM"
grep -Fq '"kind/hardware-test"' "$ISSUE_FORM"
grep -Fq '"state/triage"' "$ISSUE_FORM"
grep -Fq '"LucasWEIchen"' "$ISSUE_FORM"
grep -Fq 'blank_issues_enabled: false' "$ISSUE_CONFIG"
grep -Fq 'permissions:' "$WORKFLOW"
grep -Fq 'contents: read' "$WORKFLOW"
grep -Fq 'persist-credentials: false' "$WORKFLOW"
grep -Fq 'fetch-depth: 0' "$WORKFLOW"
grep -Fq 'bash tools/check_central_brain_github_publication_tree.sh HEAD' "$WORKFLOW"
grep -Fq 'bash tools/check_central_brain_github_remote_testing.sh' "$WORKFLOW"
grep -Fq 'bash tools/check_central_brain_root_readme.sh' "$WORKFLOW"
grep -Fq 'bash tools/check_central_brain_github_repository_completeness.sh' "$WORKFLOW"
grep -Fq -- '--changed-range "$base_revision" "$GITHUB_SHA"' "$WORKFLOW"
grep -Fq 'bash tools/check_central_brain_python_prototype_retirement.sh' "$WORKFLOW"
grep -Fq -- '- "README.md"' "$WORKFLOW"
grep -Fq -- '- "central-brain/**"' "$WORKFLOW"
grep -Fq -- '- "apk-labs/client2-central-brain/**"' "$WORKFLOW"
grep -Fq -- '- "docs/CENTRAL_BRAIN_*"' "$WORKFLOW"
grep -Fq -- '- "tools/*central_brain*"' "$WORKFLOW"
if grep -Eq 'gh release|upload-artifact|adb install|gradlew' "$WORKFLOW"; then
  echo "GitHub contract workflow must not publish, install, or claim full Android builds" >&2
  exit 1
fi

for marker in \
  'LucasWEIchen/CougarOS' \
  'cougaros-github-issue-maintenance' \
  'github_issue_intake_active=true' \
  'Issue #1' \
  'android13-hwtest-v0.5.0-rc.2' \
  'state/triage -> state/reproduced -> state/fix-ready -> state/retest ->' \
  'central-brain-android13-hybrid.tar.gz.sha256' \
  'github_source_of_truth=true' \
  'tools/check_central_brain_github_repository_completeness.sh' \
  'codex/github-publication:main'; do
  grep -Fq -- "$marker" "$DOC" \
    || { echo "remote hardware testing document marker missing: $marker" >&2; exit 1; }
done

grep -Fq 'target input status must be a bounded ASCII identifier' "$RUNNER"
grep -Fq 'manifest delivery ID is invalid' "$RUNNER"
grep -Fq 'raw_or_derived_device_identity_included=false' "$RUNNER"
grep -Fq 'central-brain-remote-evidence-*/' "$ROOT_DIR/.gitignore"

grep -Fq 'B5' "$ROOT_DIR/docs/CENTRAL_BRAIN_ROADMAP.md"
grep -Fq '| B5 | GitHub 远程硬件测试闭环 |' \
  "$ROOT_DIR/docs/CENTRAL_BRAIN_BLACKBOX_ANDROID13_ENGINEERING_PLAN.md"
grep -Fq 'ISSUE-028' "$ROOT_DIR/docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md"
grep -Fq 'DEV-021' "$ROOT_DIR/docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md"
grep -Fq 'B5 GitHub Remote Hardware Test Loop' \
  "$ROOT_DIR/docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md"
grep -Fq 'B5 GitHub Remote Test Driver/HAL Result' \
  "$ROOT_DIR/docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md"

printf '%s\n' \
  'Central Brain B5 GitHub remote testing check passed' \
  'local_remote_test_contract_ready=true' \
  'github_repository_configured=true' \
  'github_issue_intake_active=true' \
  'event_poll_interval_minutes=15' \
  'first_issue_poll_verified=true' \
  'automatic_upload_enabled=false' \
  'raw_evidence_upload_allowed=false' \
  'physical_controller_evidence_available=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
