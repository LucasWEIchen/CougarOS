#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MEM-001, S2-MDL-001, S2-SAF-001, S2-OBS-001,
# FW-U-001/006/007, NV-F-001, NV-G-005/006/007, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/memory/ContextBudgetManager.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/memory/ContextBudgetManagerTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/memory/ContextBudgetManagerProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GRAPH_RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/AgentGraphRuntime.java"
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
    || { echo "P5-W09 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$MAIN" "$TEST" "$PROBE" "$DEBUG_MANIFEST" "$MAIN_MANIFEST" \
    "$RUNTIME_SERVICE" "$GRAPH_RUNTIME" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P5-W09 file missing: $file" >&2; exit 1; }
done

for marker in \
  'public static final int SCHEMA_VERSION = 1' \
  'public static final int MAX_TOTAL_TOKENS' \
  'public static final int MAX_TOTAL_BYTES' \
  'public static final int MAX_ITEMS' \
  'enum Category' \
  'SYSTEM' \
  'CONTEXT' \
  'PROFILE' \
  'EPISODE' \
  'HISTORY' \
  'enum Handling' \
  'INCLUDE' \
  'SUMMARIZE_TO_BUDGET' \
  'TRUNCATE_TO_BUDGET' \
  'DROP' \
  'class CategoryLimit' \
  'class BudgetPolicy' \
  'class ContextDescriptor' \
  'fromTrustedMetadata(' \
  'allocate(' \
  'REQUIRED_BUDGET_EXCEEDED' \
  'isDecisionOnly()' \
  'isTextPayloadAccepted()' \
  'isTokenizerWired()' \
  'isSummarizerWired()' \
  'isProductionBudgetAuthorityWired()' \
  'isRuntimeWired()' \
  'isContentLoggingEnabled()' \
  'isModelInvoked()' \
  'isHardwareAccessed()'; do
  require_text "$MAIN" "$marker"
done

for test_name in \
  allocatesAllCategoriesInStablePriorityOrder \
  requiredBudgetFailureReturnsNoPartialPlan \
  overBudgetItemsGetDeterministicDirectivesOnly \
  categoryEnvelopesDoNotBorrowOrSilentlyEvict \
  malformedAndDuplicateMetadataAreRejected \
  outputIsImmutableAndProductionBoundariesRemainClosed; do
  require_text "$TEST" "$test_name"
done

for marker in \
  context_budget_manager_probe_complete \
  context_budget_category_allocation_verified \
  context_budget_dual_limit_verified \
  context_budget_deterministic_overflow_verified \
  context_budget_required_fail_closed \
  context_budget_android13_arm64_verified; do
  require_text "$PROBE" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
require_text "$PROBE" '"profile.probe", 5, 10, false, true, 80'
for marker in \
  context_budget_decision_only=true \
  context_budget_text_payload_accepted=false \
  context_budget_tokenizer_wired=false \
  context_budget_summarizer_wired=false \
  context_budget_production_authority_wired=false \
  context_budget_runtime_wired=false \
  context_budget_content_logged=false \
  graph_execution_enabled=false \
  effect_dispatch_enabled=false \
  vehicle_readback_accessed=false \
  model_invoked=false \
  npu_accessed=false \
  network_accessed=false \
  hardware_accessed=false \
  production_ready=false \
  target_hardware_validated=false; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

require_text "$DEBUG_MANIFEST" '.memory.ContextBudgetManagerProbeActivity'
if grep -Fq 'ContextBudgetManagerProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P5-W09 Context Budget probe leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq 'ContextBudgetManager' \
    "$ROOT_DIR/$RUNTIME_SERVICE" "$ROOT_DIR/$GRAPH_RUNTIME"; then
  echo "P5-W09 Context Budget manager was wired into production Runtime/Graph" >&2
  exit 1
fi
if grep -Eiq 'android[.]util[.]Log|System[.]out|printStackTrace' "$ROOT_DIR/$MAIN"; then
  echo "P5-W09 Context Budget main source must not log context metadata or content" >&2
  exit 1
fi
if grep -Eiq 'byte\[\]|ByteBuffer|InputStream|OutputStream|userText|modelText|promptText|contentText|rawPayload|Map<String, Object>' \
    "$ROOT_DIR/$MAIN"; then
  echo "P5-W09 Context Budget manager accepts raw content or arbitrary payloads" >&2
  exit 1
fi
if grep -R -Eq \
    'androidx[.]room|CentralBrainDatabase|android[.]os[.]Binder|java[.]io|java[.]net|okhttp|http://|https://|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|ModelProvider|TokenizerProvider|NpuProvider|NPU|ioctl|sysfs|/dev/|SharedPreferences|FileOutputStream|ObjectOutputStream|android[.]security[.]keystore|java[.]security[.]KeyStore|javax[.]crypto' \
    "$ROOT_DIR/$MAIN" "$ROOT_DIR/$PROBE"; then
  echo "P5-W09 references persistence, Binder, model, network, vehicle, crypto, or hardware APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P5-W09 ContextBudgetManager"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" '`P5-W09` ContextBudgetManager'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P5-W09 ContextBudgetManager trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android P5-W09 ContextBudgetManager"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P5-W09 ContextBudgetManager architecture"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "P5-W09 ContextBudgetManager detailed design"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android P5-W09 ContextBudgetManager"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P5-W09 ContextBudgetManager Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "DEV-071 P5-W09 decision-only context budget is not production model budgeting"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "ISSUE-044 Context tokenizer, summary executor and budget authority publication"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P5-W09 ContextBudgetManager"
require_text "README.md" "P5 ContextBudgetManager"

printf '%s\n' \
  "Central Brain Android Context Budget manager check passed" \
  "context_budget_manager_defined=true" \
  "context_budget_category_allocation_verified=true" \
  "context_budget_dual_limit_verified=true" \
  "context_budget_deterministic_overflow_verified=true" \
  "context_budget_required_fail_closed=true" \
  "context_budget_android13_arm64_verified=true" \
  "context_budget_decision_only=true" \
  "context_budget_text_payload_accepted=false" \
  "context_budget_tokenizer_wired=false" \
  "context_budget_summarizer_wired=false" \
  "context_budget_production_authority_wired=false" \
  "context_budget_runtime_wired=false" \
  "context_budget_content_logged=false" \
  "graph_execution_enabled=false" \
  "effect_dispatch_enabled=false" \
  "vehicle_readback_accessed=false" \
  "model_invoked=false" \
  "npu_accessed=false" \
  "network_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
