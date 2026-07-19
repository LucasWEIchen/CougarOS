#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SAF-001, S2-TOL-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_p9_security_evidence_interface.json"
VERSION_CATALOG="central-brain/android-runtime/gradle/libs.versions.toml"
GRADLE_BUILD="central-brain/android-runtime/runtime-service/build.gradle.kts"
WORKFLOW=".github/workflows/central-brain-remote-test-contract.yml"
DOCUMENTS=(
  "README.md"
  "central-brain/README.md"
  "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md"
  "docs/CENTRAL_BRAIN_SECURITY_REVIEW_FUZZ.md"
  "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md"
  "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md"
  "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md"
  "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md"
  "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md"
  "docs/CENTRAL_BRAIN_ROADMAP.md"
  "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md"
  "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md"
  "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md"
  "docs/CENTRAL_BRAIN_ANDROID13_PHYSICAL_TARGET_TEST_REPORT.md"
)

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P9-W03g marker missing in $file: $marker" >&2; exit 1; }
}

[[ -f "$ROOT_DIR/$CONTRACT" ]] || { echo "P9-W03g contract missing" >&2; exit 1; }

python3 -B - "$ROOT_DIR/$CONTRACT" <<'PY'
import json
import sys

payload = json.load(open(sys.argv[1], encoding="utf-8"))
if payload.get("schema_version") != "1.0.0":
    raise SystemExit("P9-W03g schema version changed")
if payload.get("profile_id") != "android13-p9-external-security-evidence-interface-v1":
    raise SystemExit("P9-W03g profile changed")
if payload.get("work_package") != "P9-W03g":
    raise SystemExit("P9-W03g work package changed")
if payload.get("status") != "suspended_external_interface_only":
    raise SystemExit("P9-W03g suspension state changed")
if payload.get("provider_boundary") != {
    "owner": "external_security_owner",
    "repository_executor_present": False,
    "android_component_present": False,
    "native_component_present": False,
    "network_transport_present": False,
    "automatic_execution_allowed": False,
}:
    raise SystemExit("P9-W03g provider boundary changed")
expected_fields = [
    "release_id",
    "source_commit",
    "archive_sha256",
    "device_alias",
    "approved_profile_id",
    "result_summary_sha256",
    "internal_evidence_reference",
    "privacy_confirmation",
]
if [item.get("field") for item in payload.get("submission_fields", [])] != expected_fields:
    raise SystemExit("P9-W03g submission fields changed")
if any(item.get("required") is not True for item in payload.get("submission_fields", [])):
    raise SystemExit("P9-W03g optional submission field found")
expected_forbidden = [
    "raw_device_identity",
    "signing_material",
    "credentials",
    "raw_test_input",
    "raw_execution_log",
    "user_text",
    "model_text",
    "memory_content",
    "vehicle_payload",
]
if payload.get("forbidden_repository_inputs") != expected_forbidden:
    raise SystemExit("P9-W03g forbidden input set changed")
if payload.get("claim_state") != {
    "security_external_evidence_interface_defined": True,
    "security_requirement_suspended": True,
    "security_test_implementation_present": False,
    "security_test_execution_enabled": False,
    "security_external_evidence_admitted": False,
    "security_coverage_guided_fuzz_complete": False,
    "security_production_signer_verified": False,
    "network_accessed": False,
    "hardware_accessed": False,
    "production_ready": False,
    "target_hardware_validated": False,
}:
    raise SystemExit("P9-W03g claim state changed")
PY

FORBIDDEN_FILES=(
  "central-brain/contracts/central_brain_android_p9_parser_robustness_campaign.json"
  "central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/security/ParserSecurityFuzzTarget.java"
  "central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/security/ParserSecurityFuzzTargetTest.java"
  "tools/test_central_brain_android_parser_robustness_campaign.sh"
  "tools/check_central_brain_android_parser_robustness_campaign.sh"
)
for file in "${FORBIDDEN_FILES[@]}"; do
  [[ ! -e "$ROOT_DIR/$file" ]] || { echo "P9-W03g retired executor remains: $file" >&2; exit 1; }
done
if grep -Eq 'jazzer|parserSecurityFuzzRuntime|fuzzParserSecurity|prepareParserSecurityFuzzCorpus' \
    "$ROOT_DIR/$VERSION_CATALOG" "$ROOT_DIR/$GRADLE_BUILD"; then
  echo "P9-W03g retired executable dependency remains" >&2
  exit 1
fi
if find "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/test/resources" \
    -path '*/p9-w03f/*' -type f -print -quit 2>/dev/null | grep -q .; then
  echo "P9-W03g retired seed input remains" >&2
  exit 1
fi

require_text "$WORKFLOW" 'check_central_brain_android_security_evidence_interface.sh'
for document in "${DOCUMENTS[@]}"; do
  require_text "$document" 'P9-W03g'
done

printf '%s\n' \
  'Central Brain Android P9-W03g external security evidence interface check passed' \
  'security_external_evidence_interface_defined=true' \
  'security_requirement_suspended=true' \
  'security_test_implementation_present=false' \
  'security_test_execution_enabled=false' \
  'security_external_evidence_admitted=false' \
  'security_coverage_guided_fuzz_complete=false' \
  'security_production_signer_verified=false' \
  'network_accessed=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
