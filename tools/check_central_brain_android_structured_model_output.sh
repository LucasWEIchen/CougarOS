#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MDL-001, S2-SAF-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/StructuredModelOutput.java"
SCHEMA="central-brain/android-runtime/runtime-service/src/main/assets/model/model-structured-output-v1.schema.json"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/model/StructuredModelOutputTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/StructuredModelOutputProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

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
    || { echo "P7-W05 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$CONTRACT" "$SCHEMA" "$TEST" "$PROBE" "$DEBUG_MANIFEST" \
    "$MAIN_MANIFEST" "$RUNTIME" "$GOVERNANCE" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P7-W05 file missing: $file" >&2; exit 1; }
done

for marker in \
  'PROMPT_CONTRACT_ID' \
  'OUTPUT_SCHEMA_ID' \
  'MAX_OUTPUT_BYTES = 16 * 1024' \
  'MAX_PARAMETERS = 16' \
  'MAX_SUMMARY_CHARS = 256' \
  'REQUEST_CAPABILITY_MISMATCH' \
  'UNKNOWN_SCENARIO' \
  'UNKNOWN_CAPABILITY' \
  'CAPABILITY_NOT_REGISTERED_FOR_SCENARIO' \
  'DUPLICATE_PARAMETER' \
  'VALUE_OUT_OF_RANGE' \
  'Strictness.STRICT' \
  'CodingErrorAction.REPORT' \
  'ScenarioCatalog' \
  'CapabilityCatalog' \
  'getCapabilityCatalogDigest()' \
  'STRUCTURED_SCENARIO_CANDIDATE' \
  'isActionAuthorizationGranted()' \
  'isApprovalDecisionGranted()' \
  'isEffectDispatchRequested()'; do
  require_text "$CONTRACT" "$marker"
done

for marker in \
  '"$id": "centralbrain.model.scenario-output.v1"' \
  '"additionalProperties": false' \
  '"required": ["schemaVersion", "scenarioId", "parameters", "summary"]' \
  '"maxItems": 16' \
  '"maxLength": 256'; do
  require_text "$SCHEMA" "$marker"
done

for test_name in \
  acceptsCatalogBoundTypedParametersAndStableDigest \
  rejectsUnknownScenarioAndCapability \
  rejectsCapabilityOutsideScenarioAndUnregisteredArea \
  rejectsWrongScalarTypeRangeStepAndDuplicateParameter \
  strictJsonRejectsUnknownDuplicateTrailingAndOversize \
  requestPurposeAndCapabilityFailClosed \
  summaryAndAcceptedOutputNeverGrantExecutionAuthority \
  publishedJsonSchemaHasClosedShape; do
  require_text "$TEST" "$test_name"
done

for marker in \
  structured_model_output_probe_complete \
  structured_model_output_verified \
  model_output_catalog_binding_verified \
  model_output_unknown_capability_rejected \
  model_output_no_action_authority; do
  require_text "$PROBE" "$marker="
done

for marker in \
  structured_model_output_verified=true \
  model_output_catalog_binding_verified=true \
  model_output_unknown_capability_rejected=true \
  model_output_no_action_authority=true \
  model_output_schema_runtime_wired=false \
  structured_model_output_android13_arm64_verified=true \
  model_invoked=false \
  raw_model_content_logged=false \
  network_accessed=false \
  npu_accessed=false \
  hardware_accessed=false \
  production_ready=false \
  target_hardware_validated=false; do
  require_text "$INSTALLER" "$marker"
done

require_text "$DEBUG_MANIFEST" '.model.StructuredModelOutputProbeActivity'
if grep -Fq 'StructuredModelOutputProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P7-W05 debug probe leaked into the release manifest" >&2
  exit 1
fi
if grep -Fq 'StructuredModelOutput' "$ROOT_DIR/$RUNTIME" \
    || grep -Fq 'StructuredModelOutput' "$ROOT_DIR/$GOVERNANCE"; then
  echo "P7-W05 output validator was wired into production Services" >&2
  exit 1
fi
if grep -R -Eiq \
    'android[.]car|CarPropertyManager|VehicleHal|VehiclePropertyIds|java[.]net|okhttp|http://|ioctl|sysfs|/dev/|android[.]os[.]Binder|ClassLoader|DexClassLoader|Runtime[.]getRuntime|ProcessBuilder' \
    "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$PROBE"; then
  echo "P7-W05 output validator references network, Binder, vehicle, or hardware APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P7-W05 Structured model output schema"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" '`P7-W05` Prompt/Output schema'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W05 structured model output trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android P7-W05 Structured Model Output"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P7-W05 structured model output architecture"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "P7-W05 structured model output detailed design"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android P7-W05 Structured Model Output"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W05 Structured Model Output Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W05 model output is proposal-only"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W05 Structured Model Output progress"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W05 Structured Model Output progress"
require_text "README.md" "P7 Structured Model Output"

printf '%s\n' \
  "Central Brain Android structured model output check passed" \
  "structured_model_output_verified=true" \
  "model_output_catalog_binding_verified=true" \
  "model_output_unknown_capability_rejected=true" \
  "model_output_no_action_authority=true" \
  "model_output_schema_runtime_wired=false" \
  "structured_model_output_android13_arm64_verified=true" \
  "model_invoked=false" \
  "raw_model_content_logged=false" \
  "network_accessed=false" \
  "npu_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
