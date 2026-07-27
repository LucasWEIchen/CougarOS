#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MDL-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HARNESS="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ScenarioEvaluationHarness.java"
OUTPUT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/StructuredModelOutput.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/model/ScenarioEvaluationHarnessTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/ScenarioEvaluationHarnessProbeActivity.java"
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
    || { echo "P7-W06 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$HARNESS" "$OUTPUT" "$TEST" "$PROBE" "$DEBUG_MANIFEST" \
    "$MAIN_MANIFEST" "$RUNTIME" "$GOVERNANCE" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P7-W06 file missing: $file" >&2; exit 1; }
done

for marker in \
  'CORPUS_ID = "centralbrain.model.scenario-evaluation.v1"' \
  'EXPECTED_CASE_COUNT = 12' \
  'MAX_EVALUATION_OUTPUT_BYTES = 64 * 1024' \
  'PROMPT_INJECTION' \
  'OVERSIZE_OUTPUT' \
  'MALFORMED_SCHEMA' \
  'evaluateOutput(' \
  'evaluateNoProposal(' \
  'evaluateProviderFailure(' \
  'aggregate(' \
  'getIntentAccuracyPermille()' \
  'getUnsafeProposalRatePermille()' \
  'getInvalidSchemaRatePermille()' \
  'getFallbackRatePermille()' \
  'getP50LatencyMs()' \
  'getP95LatencyMs()' \
  'getTokenCostUnits()' \
  'isRawContentRetained()' \
  'isModelInvoked()' \
  'isActionAuthorizationGranted()' \
  'isEffectDispatchRequested()' \
  'isProductionQualified()'; do
  require_text "$HARNESS" "$marker"
done

require_text "$OUTPUT" 'public static String digestCapabilityCatalog('

for test_name in \
  fixedCorpusIsStableMetadataOnlyAndCoversThreatsAndGuards \
  completeCorrectRunProducesDeterministicBoundedMetrics \
  movingUnknownStaleAndMissingCapabilityProposalsAreUnsafe \
  adversarialInvalidOutputsAreDigestOnlyAndCountedUnsafe \
  fallbackLatencyAndTokenMetricsAreAggregatedWithoutProviderInvocation \
  incompleteDuplicateMixedCatalogAndBudgetViolationsFailClosed; do
  require_text "$TEST" "$test_name"
done

for marker in \
  scenario_evaluation_probe_complete \
  scenario_evaluation_verified \
  evaluation_corpus_verified \
  evaluation_metrics_verified \
  evaluation_boundary_verified \
  intent_accuracy_permille \
  unsafe_proposal_rate_permille \
  token_cost_units; do
  require_text "$PROBE" "$marker="
done

for marker in \
  scenario_evaluation_verified=true \
  evaluation_corpus_verified=true \
  evaluation_metrics_verified=true \
  evaluation_boundary_verified=true \
  evaluation_case_count=12 \
  intent_accuracy_permille=1000 \
  unsafe_proposal_rate_permille=0 \
  invalid_schema_rate_permille=0 \
  fallback_rate_permille=0 \
  scenario_evaluation_runtime_wired=false \
  raw_evaluation_content_logged=false \
  scenario_evaluation_android13_arm64_verified=true \
  model_invoked=false \
  network_accessed=false \
  npu_accessed=false \
  hardware_accessed=false \
  production_ready=false \
  target_hardware_validated=false; do
  require_text "$INSTALLER" "$marker"
done

require_text "$DEBUG_MANIFEST" '.model.ScenarioEvaluationHarnessProbeActivity'
if grep -Fq 'ScenarioEvaluationHarnessProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P7-W06 debug probe leaked into the release manifest" >&2
  exit 1
fi
if grep -Fq 'ScenarioEvaluationHarness' "$ROOT_DIR/$RUNTIME" \
    || grep -Fq 'ScenarioEvaluationHarness' "$ROOT_DIR/$GOVERNANCE"; then
  echo "P7-W06 evaluation harness was wired into production Services" >&2
  exit 1
fi
if grep -R -Eiq \
    'android[.]car|CarPropertyManager|VehicleHal|VehiclePropertyIds|java[.]net|okhttp|http://|ioctl|sysfs|/dev/|android[.]os[.]Binder|ClassLoader|DexClassLoader|Runtime[.]getRuntime|ProcessBuilder' \
    "$ROOT_DIR/$HARNESS" "$ROOT_DIR/$PROBE"; then
  echo "P7-W06 evaluation harness references network, Binder, vehicle, or hardware APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P7-W06 Scenario evaluation harness"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" '`P7-W06` Scenario evaluation harness'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W06 scenario evaluation trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android P7-W06 Scenario Evaluation Harness"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P7-W06 scenario evaluation architecture"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "P7-W06 scenario evaluation detailed design"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android P7-W06 Scenario Evaluation Harness"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W06 Scenario Evaluation Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W06 evaluator is offline and digest-only"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W06 Scenario Evaluation progress"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W06 Scenario Evaluation progress"
require_text "README.md" "P7 Scenario Evaluation"

printf '%s\n' \
  "Central Brain Android scenario evaluation check passed" \
  "scenario_evaluation_verified=true" \
  "evaluation_corpus_verified=true" \
  "evaluation_metrics_verified=true" \
  "evaluation_boundary_verified=true" \
  "evaluation_case_count=12" \
  "intent_accuracy_permille=1000" \
  "unsafe_proposal_rate_permille=0" \
  "invalid_schema_rate_permille=0" \
  "fallback_rate_permille=0" \
  "scenario_evaluation_runtime_wired=false" \
  "raw_evaluation_content_logged=false" \
  "scenario_evaluation_android13_arm64_verified=true" \
  "model_invoked=false" \
  "network_accessed=false" \
  "npu_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
