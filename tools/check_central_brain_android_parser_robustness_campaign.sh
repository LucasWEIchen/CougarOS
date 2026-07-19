#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SAF-001, S2-TOL-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_p9_parser_robustness_campaign.json"
VERSION_CATALOG="central-brain/android-runtime/gradle/libs.versions.toml"
GRADLE_BUILD="central-brain/android-runtime/runtime-service/build.gradle.kts"
TARGET="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/security/ParserSecurityFuzzTarget.java"
TARGET_TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/security/ParserSecurityFuzzTargetTest.java"
SEED_DIR="central-brain/android-runtime/runtime-service/src/test/resources/fuzz/p9-w03f/parser-security"
RUNNER="tools/test_central_brain_android_parser_robustness_campaign.sh"
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
    || { echo "P9-W03f marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$CONTRACT" "$VERSION_CATALOG" "$GRADLE_BUILD" "$TARGET" \
    "$TARGET_TEST" "$RUNNER" "$WORKFLOW"; do
  [[ -f "$ROOT_DIR/$file" ]] || { echo "P9-W03f file missing: $file" >&2; exit 1; }
done
bash -n "$ROOT_DIR/$RUNNER"

python3 -B - "$ROOT_DIR/$CONTRACT" <<'PY'
import json
import sys

payload = json.load(open(sys.argv[1], encoding="utf-8"))
if payload.get("schema_version") != "1.0.0":
    raise SystemExit("P9-W03f schema version changed")
if payload.get("profile_id") != "android13-p9-parser-robustness-host-campaign-v1":
    raise SystemExit("P9-W03f profile changed")
if payload.get("work_package") != "P9-W03f":
    raise SystemExit("P9-W03f work package changed")
if payload.get("maturity") != "bounded_host_robustness_evidence":
    raise SystemExit("P9-W03f maturity changed")
if payload.get("requirements") != [
    "S2-SAF-001", "S2-TOL-001", "S2-OBS-001", "DEL-001", "DEL-004", "DEL-005"
]:
    raise SystemExit("P9-W03f requirements changed")
if payload.get("engine") != {
    "name": "Jazzer",
    "version": "0.30.0",
    "mode": "coverage_guided_jvm",
    "dependency_scope": "parserSecurityFuzzRuntime",
}:
    raise SystemExit("P9-W03f engine changed")
if payload.get("campaign") != {
    "default_budget_seconds": 20,
    "maximum_budget_seconds": 300,
    "per_input_timeout_seconds": 5,
    "maximum_input_bytes": 65538,
    "maximum_rss_mib": 2048,
    "seed_count": 6,
    "retained_input_policy": "generated_synthetic_corpus_build_directory_only",
    "crash_artifact_policy": "local_build_directory_only",
}:
    raise SystemExit("P9-W03f campaign changed")
expected_surfaces = [
    ("checkpoint.envelope.deserialize.v1", "JsonPrimitiveCheckpointSerializer.deserialize", "CheckpointException"),
    ("scenario.manifest.parse.v1", "ScenarioManifestParser.parse", "ParseException"),
    ("tool.input.validate.v1", "ToolSchemaValidator.validateInput", "ValidationException"),
]
actual_surfaces = [
    (item.get("surface_id"), item.get("entrypoint"), item.get("accepted_rejection"))
    for item in payload.get("surfaces", [])
]
if actual_surfaces != expected_surfaces:
    raise SystemExit("P9-W03f surfaces changed")
if payload.get("evidence_policy") != {
    "minimum_executed_units": 1,
    "minimum_edge_coverage": 1,
    "every_surface_must_execute": True,
    "crash_count_must_equal": 0,
    "raw_input_logging_allowed": False,
    "network_access_allowed": False,
    "android_device_access_required": False,
}:
    raise SystemExit("P9-W03f evidence policy changed")
state = payload.get("claim_state", {})
expected_state = {
    "security_parser_robustness_engine_pinned": True,
    "security_parser_robustness_budget_defined": True,
    "security_parser_robustness_surface_count": 3,
    "security_parser_robustness_seed_count": 6,
    "security_parser_robustness_host_campaign_verified": True,
    "security_coverage_guided_fuzz_complete": False,
    "security_production_signer_verified": False,
    "network_accessed": False,
    "hardware_accessed": False,
    "production_ready": False,
    "target_hardware_validated": False,
}
if state != expected_state:
    raise SystemExit("P9-W03f claim state changed")
PY

require_text "$VERSION_CATALOG" 'jazzer = "0.30.0"'
require_text "$VERSION_CATALOG" 'module = "com.code-intelligence:jazzer"'
for marker in \
  'parserSecurityFuzzRuntime by configurations.creating' \
  'add(parserSecurityFuzzRuntime.name, libs.jazzer)' \
  'prepareParserSecurityFuzzCorpus' \
  'fuzzParserSecurity' \
  'centralBrainFuzzSeconds' \
  '--target_class=com.centralbrain.runtime.security.ParserSecurityFuzzTarget' \
  '-max_total_time=$seconds' \
  '-timeout=5' \
  '-max_len=65538'; do
  require_text "$GRADLE_BUILD" "$marker"
done
for marker in \
  'JsonPrimitiveCheckpointSerializer' \
  'ScenarioManifestParser' \
  'ToolSchemaValidator' \
  'catch (CheckpointException expected)' \
  'catch (ParseException expected)' \
  'catch (ValidationException expected)' \
  'central_brain_fuzz_checkpoint_calls=' \
  'central_brain_fuzz_scenario_calls=' \
  'central_brain_fuzz_tool_calls=' \
  'central_brain_fuzz_raw_input_logged=false'; do
  require_text "$TARGET" "$marker"
done
for marker in \
  'allRawAndStructuredSurfaceSelectorsFailClosed' \
  'targetDoesNotMutateCallerInput'; do
  require_text "$TARGET_TEST" "$marker"
done

EXPECTED_SEEDS=(
  checkpoint-raw.seed
  checkpoint-structured.seed
  scenario-raw.seed
  scenario-structured.seed
  tool-arbitrary.seed
  tool-structured.seed
)
mapfile -t ACTUAL_SEEDS < <(find "$ROOT_DIR/$SEED_DIR" -maxdepth 1 -type f -printf '%f\n' | sort)
[[ "${ACTUAL_SEEDS[*]}" == "${EXPECTED_SEEDS[*]}" ]] \
  || { echo "P9-W03f seed corpus changed" >&2; exit 1; }

for marker in \
  'CENTRAL_BRAIN_FUZZ_SECONDS' \
  'security_parser_robustness_executed_units=' \
  'security_parser_robustness_edge_coverage=' \
  'security_parser_robustness_crash_count=' \
  'security_parser_robustness_raw_input_logged=' \
  'security_coverage_guided_fuzz_complete=false' \
  'production_ready=false' \
  'target_hardware_validated=false'; do
  require_text "$RUNNER" "$marker"
done
require_text "$WORKFLOW" 'check_central_brain_android_parser_robustness_campaign.sh'
for document in "${DOCUMENTS[@]}"; do
  require_text "$document" 'P9-W03f'
done

printf '%s\n' \
  'Central Brain Android P9-W03f parser robustness contract check passed' \
  'security_parser_robustness_engine_pinned=true' \
  'security_parser_robustness_budget_defined=true' \
  'security_parser_robustness_surface_count=3' \
  'security_parser_robustness_seed_count=6' \
  'security_parser_robustness_host_campaign_verified=true' \
  'security_coverage_guided_fuzz_complete=false' \
  'security_production_signer_verified=false' \
  'network_accessed=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
