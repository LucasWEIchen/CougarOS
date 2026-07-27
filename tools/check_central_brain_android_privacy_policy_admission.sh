#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MEM-001, S2-SAF-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_p9_privacy_policy_admission.json"
INVENTORY="central-brain/contracts/central_brain_android_p9_privacy_data_inventory.json"
IMPLEMENTATION="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/privacy/PrivacyLifecyclePolicyAdmission.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/privacy/PrivacyLifecyclePolicyAdmissionTest.java"
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
    || { echo "P9-W04b marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$CONTRACT" "$INVENTORY" "$IMPLEMENTATION" "$TEST" \
    "$RUNTIME" "$GOVERNANCE" "$DOC"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P9-W04b file missing: $file" >&2; exit 1; }
done

python3 - "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$INVENTORY" \
    "$ROOT_DIR/$IMPLEMENTATION" "$ROOT_DIR/$TEST" <<'PY'
import json
import pathlib
import sys

contract_path, inventory_path, java_path, test_path = map(pathlib.Path, sys.argv[1:])
contract = json.loads(contract_path.read_text(encoding="utf-8"))
inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
java = java_path.read_text(encoding="utf-8")
test = test_path.read_text(encoding="utf-8")

if contract.get("schema_version") != "1.0.0" \
        or contract.get("profile_id") != "android13-p9-privacy-policy-admission-v1" \
        or contract.get("policy_id") != "cougaros-privacy-draft" \
        or contract.get("policy_version") != "0.1.0-draft" \
        or contract.get("maturity") != "fail_closed_owner_input_required":
    raise SystemExit("P9-W04b contract identity changed")
if contract.get("inventory_profile_id") != inventory.get("profile_id"):
    raise SystemExit("P9-W04b inventory profile binding changed")
if contract.get("required_surface_count") != 12:
    raise SystemExit("P9-W04b surface count changed")
if contract.get("required_owner_roles") != [
        "PRIVACY", "FUNCTIONAL_SAFETY", "COMPLIANCE"]:
    raise SystemExit("P9-W04b owner roles changed")

policies = contract.get("surface_policies", [])
inventory_ids = [surface["id"] for surface in inventory.get("surfaces", [])]
if [policy.get("surface_id") for policy in policies] != inventory_ids:
    raise SystemExit("P9-W04b surface order differs from inventory")
gaps = [policy for policy in policies if policy.get("state") == "OWNER_INPUT_REQUIRED"]
if [(policy.get("surface_id"), policy.get("retention_ceiling_seconds"),
        policy.get("hold_guard")) for policy in gaps] != [
        ("durable.effect_recovery", None, "ACTIVE_EFFECT_AND_COMPENSATION"),
        ("durable.audit", None, "LEGAL_AND_SAFETY")]:
    raise SystemExit("P9-W04b unresolved owner policy rows changed")
if any(policy.get("state") != "INVENTORY_BOUND"
       or policy.get("retention_ceiling_seconds") is not None
       or policy.get("hold_guard") != "NONE"
       for policy in policies if policy not in gaps):
    raise SystemExit("P9-W04b inventory-bound row changed")
if contract.get("owner_approval_evidence") != []:
    raise SystemExit("P9-W04b repository draft claimed owner approval evidence")

preflight = contract.get("operation_preflight", {})
expected_preflight = {
    "effect_delete_guard": "ACTIVE_EFFECT_AND_COMPENSATION",
    "audit_delete_guard": "LEGAL_AND_SAFETY",
    "authorized_export_surface": "memory.profile",
    "authorization_digest_required": True,
    "profile_export_consent_digest_required": True,
    "accepts_content_payload": False,
    "mutates_repository": False,
    "exports_data": False,
}
if preflight != expected_preflight:
    raise SystemExit("P9-W04b operation preflight changed")

state = contract.get("claim_state", {})
if state.get("privacy_policy_admission_defined") is not True:
    raise SystemExit("P9-W04b admission claim is false")
for key in [
    "privacy_current_policy_admitted",
    "privacy_owner_policy_approved",
    "privacy_repository_mutation_wired",
    "privacy_runtime_lifecycle_wiring_complete",
    "privacy_android13_arm64_verified",
    "hardware_accessed",
    "production_ready",
    "target_hardware_validated",
]:
    if state.get(key) is not False:
        raise SystemExit(f"P9-W04b forbidden claim was raised: {key}")
if state.get("implementation_stage") != "P9-W04":
    raise SystemExit("P9-W04b implementation stage changed")

for marker in [
    "CURRENT_POLICY_ID = \"cougaros-privacy-draft\"",
    "CURRENT_POLICY_VERSION = \"0.1.0-draft\"",
    "REQUIRED_SURFACE_COUNT = 12",
    "REQUIRED_OWNER_APPROVAL_COUNT = 3",
    "ACTIVE_EFFECT_AND_COMPENSATION",
    "LEGAL_AND_SAFETY",
    "isCurrentPolicyAdmitted()",
    "isOwnerPolicyApproved()",
    "isRepositoryMutationWired()",
    "isRuntimeLifecycleWiringComplete()",
    "isAndroid13Arm64Verified()",
    "isHardwareAccessed()",
    "isProductionReady()",
    "isTargetHardwareValidated()",
]:
    if marker not in java:
        raise SystemExit(f"P9-W04b Java marker missing: {marker}")
for marker in [
    "currentDraftBindsAllSurfacesAndFailsClosedWithoutOwnerInput",
    "syntheticApprovedFixtureRequiresAllOwnersAndExactDigestBinding",
    "activeEffectAndCompensationBlockDeletionBeforeRepositoryMutation",
    "legalAndSafetyHoldBlockAuditDeletion",
    "onlyProfileExportWithAuthorizationAndConsentCanPassPreflight",
    "admittedPreflightNeverMutatesExportsOrGrantsRuntimeAuthority",
    "repositoryClaimsRemainUnapprovedUnwiredAndUnverified",
]:
    if marker not in test:
        raise SystemExit(f"P9-W04b JVM marker missing: {marker}")
PY

if grep -Fq 'PrivacyLifecyclePolicyAdmission' "$ROOT_DIR/$RUNTIME" \
    || grep -Fq 'PrivacyLifecyclePolicyAdmission' "$ROOT_DIR/$GOVERNANCE"; then
  echo "P9-W04b policy admission was wired into production Services" >&2
  exit 1
fi
if grep -Eiq \
    'android[.]|java[.]io|java[.]nio[.]file|java[.]net|okhttp|https?://|Room[.(]|CarPropertyManager|VehicleHal|/proc/|/dev/|ioctl|sysfs' \
    "$ROOT_DIR/$IMPLEMENTATION"; then
  echo "P9-W04b policy admission reads platform, storage, network, vehicle or hardware state" >&2
  exit 1
fi

require_text "$DOC" 'W04B_ADMISSION_DEFINED / OWNER_POLICY_INPUT_OPEN'
require_text "$DOC" 'privacy_policy_admission_defined=true'
require_text "README.md" 'P9 Privacy Policy Admission'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W04b privacy policy admission'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W04b privacy policy admission trace'
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" 'Android P9-W04b Privacy Policy Admission Contract'
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" 'P9-W04b privacy policy admission architecture'
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" 'P9-W04b privacy policy admission detailed design'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'Android P9-W04b Privacy Policy Admission'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W04b Privacy Policy Admission Driver/HAL Boundary'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'DEV-092 P9-W04b admission is not an approved lifecycle policy'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'ISSUE-051 P9 durable privacy lifecycle policies are incomplete'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W04b Privacy Policy Admission progress'

printf '%s\n' \
  'Central Brain Android privacy policy admission check passed' \
  'privacy_policy_admission_defined=true' \
  'privacy_policy_surface_count=12' \
  'privacy_policy_required_owner_approval_count=3' \
  'privacy_policy_unresolved_surface_count=2' \
  'privacy_active_effect_delete_guard_defined=true' \
  'privacy_audit_hold_guard_defined=true' \
  'privacy_profile_export_preflight_defined=true' \
  'privacy_current_policy_admitted=false' \
  'privacy_owner_policy_approved=false' \
  'privacy_repository_mutation_wired=false' \
  'privacy_runtime_lifecycle_wiring_complete=false' \
  'privacy_android13_arm64_verified=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'implementation_stage=P9-W04'
