#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SAF-001, S2-TOL-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_p9_parser_security_corpus.json"
IMPLEMENTATION="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/security/ParserSecurityCorpusContract.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/security/ParserSecurityCorpusContractTest.java"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
DOC="docs/CENTRAL_BRAIN_SECURITY_REVIEW_FUZZ.md"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P9-W03a marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$CONTRACT" "$IMPLEMENTATION" "$TEST" "$RUNTIME" "$GOVERNANCE" "$DOC"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P9-W03a file missing: $file" >&2; exit 1; }
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
    raise SystemExit("P9-W03a corpus schema version changed")
if contract.get("profile_id") != "android13-p9-parser-security-v1":
    raise SystemExit("P9-W03a corpus profile changed")
if contract.get("maturity") != "deterministic_host_regression_partial_security_review":
    raise SystemExit("P9-W03a corpus maturity changed")

expected = [
    ("checkpoint.malformed_json.v1", "CHECKPOINT", "MALFORMED_INPUT", "MALFORMED_JSON"),
    ("checkpoint.duplicate_field.v1", "CHECKPOINT", "DUPLICATE_FIELD", "DUPLICATE_FIELD"),
    ("checkpoint.unknown_field.v1", "CHECKPOINT", "UNKNOWN_FIELD", "UNKNOWN_FIELD"),
    ("checkpoint.oversize.v1", "CHECKPOINT", "OVERSIZE", "OVERSIZE"),
    ("checkpoint.digest_tamper.v1", "CHECKPOINT", "REPLAY_OR_TAMPER", "DIGEST_MISMATCH"),
    ("checkpoint.privileged_path_key.v1", "CHECKPOINT", "PATH_TRAVERSAL", "PAYLOAD_REJECTED"),
    ("scenario.invalid_source_path.v1", "SCENARIO_MANIFEST", "PATH_TRAVERSAL", "INVALID_SOURCE"),
    ("scenario.unknown_field.v1", "SCENARIO_MANIFEST", "UNKNOWN_FIELD", "UNKNOWN_FIELD"),
    ("scenario.duplicate_field.v1", "SCENARIO_MANIFEST", "DUPLICATE_FIELD", "DUPLICATE_FIELD"),
    ("scenario.oversize.v1", "SCENARIO_MANIFEST", "OVERSIZE", "OVERSIZE"),
    ("scenario.trailing_json.v1", "SCENARIO_MANIFEST", "MALFORMED_INPUT", "MALFORMED_JSON"),
    ("scenario.depth_bomb.v1", "SCENARIO_MANIFEST", "DEPTH_BOMB", "MALFORMED_JSON"),
    ("tool_schema.missing_field.v1", "TOOL_SCHEMA", "MISSING_FIELD", "MISSING_FIELD"),
    ("tool_schema.unknown_field.v1", "TOOL_SCHEMA", "UNKNOWN_FIELD", "UNKNOWN_FIELD"),
    ("tool_schema.null_value.v1", "TOOL_SCHEMA", "NULL_VALUE", "NULL_VALUE"),
    ("tool_schema.type_confusion.v1", "TOOL_SCHEMA", "TYPE_CONFUSION", "TYPE_MISMATCH"),
    ("tool_schema.value_boundary.v1", "TOOL_SCHEMA", "VALUE_BOUNDARY", "VALUE_OUT_OF_RANGE"),
    ("tool_schema.payload_oversize.v1", "TOOL_SCHEMA", "OVERSIZE", "PAYLOAD_TOO_LARGE"),
]
actual = [
    (case["case_id"], case["surface"], case["threat_class"], case["expected_error_code"])
    for case in contract.get("cases", [])
]
if actual != expected:
    raise SystemExit("P9-W03a JSON corpus changed")
if contract.get("surfaces") != ["CHECKPOINT", "SCENARIO_MANIFEST", "TOOL_SCHEMA"]:
    raise SystemExit("P9-W03a surface catalog changed")

java_cases = re.findall(
    r'add\(cases,\s*"([^"]+)",\s*Surface\.([A-Z_]+),\s*ThreatClass\.([A-Z_]+),\s*"([A-Z_]+)"\);',
    java,
    re.DOTALL,
)
if java_cases != expected:
    raise SystemExit("P9-W03a Java and JSON corpora differ")
for case_id, _, _, _ in expected:
    if case_id not in test:
        raise SystemExit(f"P9-W03a case is not executed by JVM test: {case_id}")

state = contract.get("claim_state", {})
required_true = {
    "security_parser_corpus_defined",
    "security_parser_fail_closed_regression_verified",
}
required_false = {
    "security_coverage_guided_fuzz_complete",
    "security_aidl_identity_review_complete",
    "security_signature_policy_review_complete",
    "security_android13_arm64_verified",
    "security_runtime_wired",
    "hardware_accessed",
    "production_ready",
    "target_hardware_validated",
}
if any(state.get(key) is not True for key in required_true):
    raise SystemExit("P9-W03a required host regression claim is false")
if any(state.get(key) is not False for key in required_false):
    raise SystemExit("P9-W03a broader security or production claim was raised")
if state.get("security_parser_surface_count") != 3:
    raise SystemExit("P9-W03a surface count changed")
if state.get("security_parser_case_count") != 18:
    raise SystemExit("P9-W03a case count changed")
PY

for marker in \
  'SURFACE_COUNT = 3' \
  'CASES_PER_SURFACE = 6' \
  'CASE_COUNT = SURFACE_COUNT * CASES_PER_SURFACE' \
  'isDeterministicHostRegressionVerified()' \
  'isCoverageGuidedFuzzComplete()' \
  'isAidlIdentityReviewComplete()' \
  'isSignaturePolicyReviewComplete()' \
  'isAndroid13Arm64Verified()' \
  'isRuntimeWired()' \
  'isHardwareAccessed()' \
  'isProductionReady()' \
  'isTargetHardwareValidated()'; do
  require_text "$IMPLEMENTATION" "$marker"
done

for test_name in \
  fixedCatalogHasThreeSurfacesAndEighteenUniqueCases \
  checkpointCorpusRejectsSixHostileInputsWithExactErrors \
  scenarioManifestCorpusRejectsSixHostileInputsWithExactErrors \
  toolSchemaCorpusRejectsSixHostileInputsWithExactErrors \
  corpusIsMetadataOnlyAndDoesNotClaimBroaderSecurityQualification; do
  require_text "$TEST" "$test_name"
done

if grep -Fq 'ParserSecurityCorpusContract' "$ROOT_DIR/$RUNTIME" \
    || grep -Fq 'ParserSecurityCorpusContract' "$ROOT_DIR/$GOVERNANCE"; then
  echo "P9-W03a security test catalog was wired into production Services" >&2
  exit 1
fi
if grep -Eiq \
    'java[.]util[.]Random|SecureRandom|System[.](currentTimeMillis|nanoTime)|android[.]|java[.]io|java[.]nio[.]file|java[.]net|okhttp|https?://|CarPropertyManager|VehicleHal|/proc/|/dev/|ioctl|sysfs' \
    "$ROOT_DIR/$IMPLEMENTATION"; then
  echo "P9-W03a main catalog reads random, time, platform, file, network, vehicle, or hardware state" >&2
  exit 1
fi

require_text "$DOC" 'Central Brain P9-W03 Security Review and Fuzz'
require_text "$DOC" 'W03D_ANDROID_IDENTITY_VERIFIED / TARGET_FUZZ_PENDING'
require_text "$DOC" 'implementation_stage=P9-W03'
require_text "README.md" 'P9 Parser Security Corpus'
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" 'P9-W03a parser security corpus'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" 'P9-W03a parser security corpus trace'
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" 'Android P9-W03a Parser Security Corpus Contract'
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" 'P9-W03a parser security corpus architecture'
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" 'P9-W03a parser security corpus detailed design'
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" 'Android P9-W03a Parser Security Corpus'
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" 'P9-W03a Parser Security Driver/HAL Boundary'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" 'P9-W03a deterministic corpus is not coverage-guided fuzzing'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" 'ISSUE-050 P9 complete security fuzz evidence is unavailable'
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" 'P9-W03a Parser Security Corpus progress'

printf '%s\n' \
  'Central Brain Android parser security corpus check passed' \
  'security_parser_corpus_defined=true' \
  'security_parser_surface_count=3' \
  'security_parser_case_count=18' \
  'security_parser_catalog_verified=true' \
  'security_parser_fail_closed_regression_verified=true' \
  'security_coverage_guided_fuzz_complete=false' \
  'security_aidl_identity_review_complete=false' \
  'security_signature_policy_review_complete=false' \
  'security_android13_arm64_verified=false' \
  'security_runtime_wired=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'implementation_stage=P9-W03'
