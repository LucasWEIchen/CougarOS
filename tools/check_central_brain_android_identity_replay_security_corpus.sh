#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SAF-001, S2-TOL-001, S2-SES-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_p9_identity_replay_security_corpus.json"
IMPLEMENTATION="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/security/IdentityReplaySecurityCorpusContract.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/security/IdentityReplaySecurityCorpusContractTest.java"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
DOC="docs/CENTRAL_BRAIN_SECURITY_REVIEW_FUZZ.md"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P9-W03b marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$CONTRACT" "$IMPLEMENTATION" "$TEST" "$RUNTIME" "$GOVERNANCE" "$DOC"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P9-W03b file missing: $file" >&2; exit 1; }
done

python3 - "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$IMPLEMENTATION" "$ROOT_DIR/$TEST" <<'PY'
import json
import re
import sys

contract_path, java_path, test_path = sys.argv[1:]
with open(contract_path, encoding="utf-8") as stream:
    contract = json.load(stream)
java = open(java_path, encoding="utf-8").read()
test = open(test_path, encoding="utf-8").read()

if contract.get("schema_version") != "1.0.0":
    raise SystemExit("P9-W03b corpus schema version changed")
if contract.get("profile_id") != "android13-p9-identity-replay-security-v1":
    raise SystemExit("P9-W03b corpus profile changed")
if contract.get("maturity") != "deterministic_host_policy_regression_partial_security_review":
    raise SystemExit("P9-W03b corpus maturity changed")

expected = [
    ("caller.unresolved_identity.v1", "CALLER_POLICY", "UNRESOLVED_IDENTITY", "IDENTITY_UNRESOLVED"),
    ("caller.package_spoof.v1", "CALLER_POLICY", "PACKAGE_SPOOF", "PACKAGE_NOT_CONFIGURED"),
    ("caller.current_signer_spoof.v1", "CALLER_POLICY", "SIGNER_SPOOF", "CURRENT_SIGNER_MISMATCH"),
    ("caller.capability_escalation.v1", "CALLER_POLICY", "CAPABILITY_ESCALATION", "CAPABILITY_NOT_GRANTED"),
    ("caller.shared_uid_signer_confusion.v1", "CALLER_POLICY", "SHARED_UID_CONFUSION", "CURRENT_SIGNER_MISMATCH"),
    ("caller.principal_signer_rotation.v1", "CALLER_POLICY", "PRINCIPAL_ROTATION", "FINGERPRINT_CHANGED"),
    ("session.same_digest_replay.v1", "SESSION_REPLAY", "REPLAY", "SAME_HANDLE"),
    ("session.request_digest_conflict.v1", "SESSION_REPLAY", "REPLAY_CONFLICT", "IDEMPOTENCY_CONFLICT"),
    ("session.cross_owner_find.v1", "SESSION_REPLAY", "CROSS_OWNER_ACCESS", "NULL_SNAPSHOT"),
    ("session.cross_owner_events.v1", "SESSION_REPLAY", "CROSS_OWNER_ACCESS", "SESSION_NOT_FOUND"),
    ("session.cross_owner_cancel.v1", "SESSION_REPLAY", "CROSS_OWNER_ACCESS", "NO_CHANGE"),
    ("session.malformed_owner.v1", "SESSION_REPLAY", "MALFORMED_PRINCIPAL", "SECURITY_EXCEPTION"),
    ("signer.unknown.v1", "SIGNER_POLICY", "UNKNOWN_SIGNER", "UNKNOWN_SIGNER"),
    ("signer.not_yet_active.v1", "SIGNER_POLICY", "SIGNER_EPOCH", "SIGNER_NOT_YET_ACTIVE"),
    ("signer.retired.v1", "SIGNER_POLICY", "RETIRED_SIGNER", "RETIRED_SIGNER"),
    ("signer.revoked.v1", "SIGNER_POLICY", "REVOKED_SIGNER", "REVOKED_SIGNER"),
    ("signer.malformed_digest.v1", "SIGNER_POLICY", "MALFORMED_SIGNER_EVIDENCE", "POLICY_VIOLATION"),
    ("signer.nonpositive_epoch.v1", "SIGNER_POLICY", "SIGNER_EPOCH", "POLICY_VIOLATION"),
]
actual = [
    (case["case_id"], case["surface"], case["threat_class"], case["expected_outcome_code"])
    for case in contract.get("cases", [])
]
if actual != expected:
    raise SystemExit("P9-W03b JSON corpus changed")
if contract.get("surfaces") != ["CALLER_POLICY", "SESSION_REPLAY", "SIGNER_POLICY"]:
    raise SystemExit("P9-W03b surface catalog changed")

java_cases = re.findall(
    r'add\(cases,\s*"([^"]+)",\s*Surface\.([A-Z_]+),\s*ThreatClass\.([A-Z_]+),\s*"([A-Z_]+)"\);',
    java,
    re.DOTALL,
)
if java_cases != expected:
    raise SystemExit("P9-W03b Java and JSON corpora differ")
for case_id, _, _, _ in expected:
    if case_id not in test:
        raise SystemExit(f"P9-W03b case is not executed by JVM test: {case_id}")

state = contract.get("claim_state", {})
required_true = {
    "security_identity_replay_corpus_defined",
    "security_caller_policy_host_verified",
    "security_session_replay_owner_policy_host_verified",
    "security_signer_policy_host_verified",
}
required_false = {
    "security_binder_calling_uid_spoof_android_verified",
    "security_package_signature_cryptographically_verified",
    "security_coverage_guided_fuzz_complete",
    "security_android13_arm64_verified",
    "security_runtime_wired",
    "hardware_accessed",
    "production_ready",
    "target_hardware_validated",
}
if any(state.get(key) is not True for key in required_true):
    raise SystemExit("P9-W03b required host policy claim is false")
if any(state.get(key) is not False for key in required_false):
    raise SystemExit("P9-W03b Android, crypto, fuzz or production claim was raised")
if state.get("security_identity_replay_surface_count") != 3:
    raise SystemExit("P9-W03b surface count changed")
if state.get("security_identity_replay_case_count") != 18:
    raise SystemExit("P9-W03b case count changed")
PY

for marker in \
  'SURFACE_COUNT = 3' \
  'CASES_PER_SURFACE = 6' \
  'CASE_COUNT = SURFACE_COUNT * CASES_PER_SURFACE' \
  'isCallerPolicyHostVerified()' \
  'isSessionReplayOwnerPolicyHostVerified()' \
  'isSignerPolicyHostVerified()' \
  'isBinderCallingUidSpoofAndroidVerified()' \
  'isPackageSignatureCryptographicallyVerified()' \
  'isCoverageGuidedFuzzComplete()' \
  'isAndroid13Arm64Verified()' \
  'isRuntimeWired()' \
  'isHardwareAccessed()' \
  'isProductionReady()' \
  'isTargetHardwareValidated()'; do
  require_text "$IMPLEMENTATION" "$marker"
done

for test_name in \
  fixedCatalogHasThreeSurfacesAndEighteenUniqueCases \
  callerPolicyCorpusRejectsSixIdentityAndCapabilityAttacks \
  sessionCorpusEnforcesReplayDigestAndOwnerIsolation \
  signerPolicyCorpusRejectsSixSignerAndEpochAttacks \
  hostCorpusDoesNotClaimBinderCryptoAndroidOrProductionQualification; do
  require_text "$TEST" "$test_name"
done

if grep -Fq 'IdentityReplaySecurityCorpusContract' "$ROOT_DIR/$RUNTIME" \
    || grep -Fq 'IdentityReplaySecurityCorpusContract' "$ROOT_DIR/$GOVERNANCE"; then
  echo "P9-W03b security test catalog was wired into production Services" >&2
  exit 1
fi
if grep -Eiq \
    'java[.]util[.]Random|SecureRandom|System[.](currentTimeMillis|nanoTime)|android[.]|java[.]io|java[.]nio[.]file|java[.]net|okhttp|https?://|CarPropertyManager|VehicleHal|/proc/|/dev/|ioctl|sysfs' \
    "$ROOT_DIR/$IMPLEMENTATION"; then
  echo "P9-W03b main catalog reads random, time, platform, file, network, vehicle, or hardware state" >&2
  exit 1
fi

require_text "$DOC" 'IN_PROGRESS / W03B_HOST_POLICY_VERIFIED'
require_text "$DOC" 'implementation_stage=P9-W03'
require_text "README.md" 'P9 Identity/Replay Security Corpus'
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" 'P9-W03b identity/replay/signer policy corpus'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" 'P9-W03b identity/replay security corpus trace'
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" 'Android P9-W03b Identity/Replay Security Corpus Contract'
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" 'P9-W03b identity/replay security corpus architecture'
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" 'P9-W03b identity/replay security corpus detailed design'
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" 'Android P9-W03b Identity/Replay Security Corpus'
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" 'P9-W03b Identity/Replay Security Driver/HAL Boundary'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" 'DEV-089 P9-W03b host policy corpus is not Binder or APK crypto evidence'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" 'ISSUE-050 P9 complete security fuzz evidence is unavailable'
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" 'P9-W03b Identity/Replay Security Corpus progress'

printf '%s\n' \
  'Central Brain Android identity/replay security corpus check passed' \
  'security_identity_replay_corpus_defined=true' \
  'security_identity_replay_surface_count=3' \
  'security_identity_replay_case_count=18' \
  'security_caller_policy_host_verified=true' \
  'security_session_replay_owner_policy_host_verified=true' \
  'security_signer_policy_host_verified=true' \
  'security_binder_calling_uid_spoof_android_verified=false' \
  'security_package_signature_cryptographically_verified=false' \
  'security_coverage_guided_fuzz_complete=false' \
  'security_android13_arm64_verified=false' \
  'security_runtime_wired=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'implementation_stage=P9-W03'
