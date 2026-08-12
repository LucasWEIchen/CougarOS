#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SCN-001, S2-SAF-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCENARIO_DIR="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/scenario"
ASSET_DIR="central-brain/android-runtime/runtime-service/src/main/assets/scenarios"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/scenario/ScenarioManifestParserTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/scenario/ScenarioManifestProbeActivity.java"
MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
INSTALLER="tools/install_central_brain_android_runtime.sh"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"

require_file() {
  local path="$1"
  [[ -f "$ROOT_DIR/$path" ]] \
    || { echo "missing Android Scenario manifest file: $path" >&2; exit 1; }
}

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
  local path="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$path" \
    || { echo "missing Android Scenario marker '$marker' in $path" >&2; exit 1; }
}

for class in ScenarioManifest ScenarioManifestParser ScenarioCatalog; do
  require_file "$SCENARIO_DIR/$class.java"
done
for path in \
    "$ASSET_DIR/scene.comfort.cold.v1.json" \
    "$ASSET_DIR/scene.cabin.multimodal.assist.v1.json" \
    "$ASSET_DIR/scene.cabin.compliance.smoking.v1.json" \
    "$ASSET_DIR/scene.aios.freeform.v1.json" \
    "$ASSET_DIR/scene.fatigue.assist.v1.json" \
    "$ASSET_DIR/scene.rest.nap.v1.json" \
    "$ASSET_DIR/schema/scenario-manifest-v1.schema.json" \
    "$ASSET_DIR/scenarios-v1.sha256" \
    "$TEST" "$PROBE" "$MANIFEST" "$INSTALLER"; do
  require_file "$path"
done

require_text "central-brain/android-runtime/gradle/libs.versions.toml" 'gson = "2.11.0"'
require_text "central-brain/android-runtime/runtime-service/build.gradle.kts" 'implementation(libs.gson)'
require_text "$SCENARIO_DIR/ScenarioManifestParser.java" 'reader.setStrictness(Strictness.STRICT)'
require_text "$SCENARIO_DIR/ScenarioManifestParser.java" 'MAX_MANIFEST_BYTES = 64 * 1024'
require_text "$SCENARIO_DIR/ScenarioManifestParser.java" 'DUPLICATE_FIELD'
require_text "$SCENARIO_DIR/ScenarioManifestParser.java" 'UNKNOWN_FIELD'
require_text "$SCENARIO_DIR/ScenarioManifest.java" 'PlanContract.allowedNodeTypes()'
require_text "$SCENARIO_DIR/ScenarioManifest.java" 'planTemplate graph contains a cycle'
require_text "$SCENARIO_DIR/ScenarioManifest.java" 'HIGH scenario requires an approval node'
require_text "$SCENARIO_DIR/ScenarioCatalog.java" 'DUPLICATE_SCENARIO_ID'
require_text "$SCENARIO_DIR/ScenarioCatalog.java" 'isArtifactCryptographicallyVerified()'
require_text "$SCENARIO_DIR/ScenarioCatalog.java" 'isProductionTrusted()'

for test_name in \
  loadsSixVersionedBuiltInScenariosWithStableDigests \
  rejectsUnknownAndDuplicateFieldsUnderStrictPolicy \
  rejectsOversizeAndTrailingJson \
  duplicateScenarioIdsDisableAllCopiesWithoutAffectingOthers \
  invalidManifestIsIsolatedFromValidCatalogEntries \
  rejectsUnknownCapabilityAndCyclicTemplate \
  schemaDeclaresStrictVersionedObjects; do
  require_text "$TEST" "$test_name"
done

require_text "$MANIFEST" '.scenario.ScenarioManifestProbeActivity'
for marker in \
  scenario_manifest_parser_verified=true \
  scenario_manifest_schema_version_verified=true \
  scenario_catalog_digest_verified=true \
  scenario_manifest_artifact_digest_verified=true \
  scenario_manifest_fatigue_policy_verified=true \
  scenario_manifest_unknown_field_rejected=true \
  scenario_manifest_oversize_rejected=true \
  scenario_catalog_duplicate_id_rejected=true \
  scenario_manifest_invalid_dag_rejected=true \
  scenario_catalog_isolation_verified=true \
  scenario_manifest_android13_arm64_verified=true; do
  require_text "$PROBE" "${marker%=true}="
  require_text "$INSTALLER" "$marker"
done
require_text "$PROBE" 'scenario_catalog_count=" + catalog.size()'
require_text "$INSTALLER" 'scenario_catalog_count=6'
for marker in \
  scenario_manifest_artifact_crypto_verified=false \
  scenario_catalog_production_trusted=false \
  scenario_runtime_wired=false \
  scenario_graph_execution_enabled=false \
  effect_dispatch_enabled=false \
  vehicle_signal_provider_wired=false \
  hardware_accessed=false; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

(cd "$ROOT_DIR/$ASSET_DIR" && sha256sum -c scenarios-v1.sha256)

python3 -B - "$ROOT_DIR/$ASSET_DIR" <<'PY'
import json
import pathlib
import sys

asset_dir = pathlib.Path(sys.argv[1])
required_root = {
    "schemaVersion", "scenarioId", "version", "supportedSources", "supportedZones",
    "contextPolicyId", "requiredContext", "optionalContext", "requiredCapabilities",
    "optionalCapabilities", "riskClass", "planTemplate", "fallback", "ui",
}
expected_ids = {
    "scene.aios.freeform.v1",
    "scene.cabin.compliance.smoking.v1",
    "scene.cabin.multimodal.assist.v1",
    "scene.comfort.cold.v1",
    "scene.fatigue.assist.v1",
    "scene.rest.nap.v1",
}
expected_versions = {
    "scene.aios.freeform.v1": 1,
    "scene.cabin.compliance.smoking.v1": 1,
    "scene.cabin.multimodal.assist.v1": 2,
    "scene.comfort.cold.v1": 1,
    "scene.fatigue.assist.v1": 1,
    "scene.rest.nap.v1": 1,
}

def no_duplicates(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result

documents = []
for path in sorted(asset_dir.glob("scene.*.json")):
    document = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=no_duplicates)
    if set(document) != required_root:
        raise SystemExit(f"Scenario root fields changed: {path.name}: {set(document)}")
    if document["schemaVersion"] != 1:
        raise SystemExit(f"Scenario schema version changed: {path.name}")
    if document["version"] != expected_versions.get(document["scenarioId"]):
        raise SystemExit(f"Scenario version changed: {path.name}")
    if len(document["planTemplate"]["nodes"]) > 64:
        raise SystemExit(f"Scenario node bound exceeded: {path.name}")
    if len(document["planTemplate"]["dependencies"]) > 256:
        raise SystemExit(f"Scenario dependency bound exceeded: {path.name}")
    documents.append(document)

ids = {document["scenarioId"] for document in documents}
if ids != expected_ids or len(documents) != 6:
    raise SystemExit(f"Unexpected built-in Scenario catalog: {ids}")

fatigue = next(value for value in documents if value["scenarioId"] == "scene.fatigue.assist.v1")
seat_nodes = [
    node for node in fatigue["planTemplate"]["nodes"]
    if node["capabilityId"] == "vehicle.seat.recline"
]
if not seat_nodes or not all(
        node["policy"]["drivingPolicy"] == "PARKED_ONLY"
        and node["policy"]["approvalRequired"]
        for node in seat_nodes):
    raise SystemExit("Fatigue seat template is not fail-closed parked/approval metadata")

schema = json.loads(
    (asset_dir / "schema/scenario-manifest-v1.schema.json").read_text(encoding="utf-8"),
    object_pairs_hook=no_duplicates,
)
if schema.get("additionalProperties") is not False:
    raise SystemExit("Scenario schema root must reject additional properties")
if schema.get("properties", {}).get("schemaVersion", {}).get("const") != 1:
    raise SystemExit("Scenario schema version must be fixed at 1")

print("scenario_manifest_json_schema_verified=true")
print(f"scenario_catalog_count={len(documents)}")
PY

if grep -R -Eiq \
    'android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|java[.]net|okhttp|http://|ioctl|sysfs|/dev/|androidx[.]room' \
    "$ROOT_DIR/$SCENARIO_DIR" "$ROOT_DIR/$PROBE"; then
  echo "P2-W05 Scenario manifest unexpectedly references hardware, persistence, or network access" >&2
  exit 1
fi
if grep -Eq 'runtime[.]scenario|ScenarioManifest|ScenarioCatalog|ScenarioManifestParser' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE"; then
  echo "P2-W05 Scenario manifest must not be wired into production Services" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P2-W05 Scenario Manifest"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" '`P2-W05` Scenario manifest/schema'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W05 Scenario manifest/schema trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android P2-W05 Scenario Manifest"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android P2-W05 Scenario Manifest"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W05 Scenario Manifest Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W05 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W05 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W05 Scenario manifest/schema"

printf '%s\n' \
  "Central Brain Android Scenario manifest check passed" \
  "scenario_manifest_schema_version=1" \
  "scenario_catalog_count=6" \
  "scenario_manifest_strict_parser_verified=true" \
  "scenario_manifest_artifact_crypto_verified=false" \
  "scenario_catalog_production_trusted=false" \
  "scenario_runtime_wired=false" \
  "scenario_graph_execution_enabled=false" \
  "hardware_accessed=false"
