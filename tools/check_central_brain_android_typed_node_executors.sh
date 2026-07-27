#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-GRF-001, S2-SAF-001, S2-EFF-001, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph"
DEBUG_ROOT="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/graph"
TEST_ROOT="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/graph"
CONTRACT="$MAIN_ROOT/NodeExecutionContract.java"
INPUT="$MAIN_ROOT/NodeExecutionInput.java"
OUTPUT="$MAIN_ROOT/NodeExecutionOutput.java"
RESULT="$MAIN_ROOT/NodeExecutionResult.java"
SCHEMAS="$MAIN_ROOT/NodeExecutionSchemas.java"
EXECUTOR="$MAIN_ROOT/TypedNodeExecutor.java"
REGISTRY="$MAIN_ROOT/NodeExecutorRegistry.java"
DEBUG_EXECUTORS="$DEBUG_ROOT/DeterministicNodeExecutors.java"
TEST="$TEST_ROOT/TypedNodeExecutorsTest.java"
PROBE="$DEBUG_ROOT/TypedNodeExecutorsProbeActivity.java"
MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GRAPH_RUNTIME="$MAIN_ROOT/AgentGraphRuntime.java"
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
    || { echo "P3-W02 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$CONTRACT" "$INPUT" "$OUTPUT" "$RESULT" "$SCHEMAS" \
    "$EXECUTOR" "$REGISTRY" "$DEBUG_EXECUTORS" "$TEST" "$PROBE" \
    "$MANIFEST" "$MAIN_MANIFEST" "$RUNTIME_SERVICE" "$GRAPH_RUNTIME" \
    "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P3-W02 file missing: $file" >&2; exit 1; }
done

for marker in \
  'public abstract class NodeExecutionInput' \
  'public static final class ContextInput' \
  'public static final class PolicyInput' \
  'public static final class ApprovalInput' \
  'public static final class EffectInput' \
  'public static final class VerificationInput' \
  'public static final class SummaryInput' \
  'public static final class CompensationInput' \
  'public static final class DigestOnlyInput'; do
  require_text "$INPUT" "$marker"
done
for marker in \
  'public abstract class NodeExecutionOutput' \
  'public static final class ContextOutput' \
  'public static final class PolicyOutput' \
  'public static final class ApprovalOutput' \
  'public static final class EffectOutput' \
  'public static final class VerificationOutput' \
  'public static final class SummaryOutput' \
  'public static final class CompensationOutput' \
  'public static final class DigestOnlyOutput'; do
  require_text "$OUTPUT" "$marker"
done
for marker in \
  'public interface TypedNodeExecutor' \
  'default boolean isProductionAuthorized()' \
  'default boolean mayDispatchEffect()' \
  'default boolean mayInvokeModel()' \
  'default boolean mayAccessNetwork()' \
  'default boolean mayAccessHardware()' \
  'default boolean mayPersistRawData()'; do
  require_text "$EXECUTOR" "$marker"
done
for node_type in \
  context.capture policy.evaluate approval.interrupt effect.execute effect.verify \
  tool.invoke model.invoke memory.query memory.write summary.render compensate; do
  require_text "$SCHEMAS" "\"$node_type\""
done
for marker in \
  'input.getClass() != schema.inputType' \
  'result.getOutput().getClass() != schema.outputType' \
  'validateExecutor(TypedNodeExecutor' \
  'executor requests a forbidden P3-W02 capability' \
  'return false;'; do
  if [[ "$marker" == validateExecutor* || "$marker" == executor* ]]; then
    require_text "$REGISTRY" "$marker"
  elif [[ "$marker" == 'return false;' ]]; then
    require_text "$EXECUTOR" "$marker"
  else
    require_text "$SCHEMAS" "$marker"
  fi
done

for marker in \
  'public final class DeterministicNodeExecutors' \
  'case "context.capture"' \
  'case "policy.evaluate"' \
  'case "approval.interrupt"' \
  'case "effect.execute"' \
  'case "effect.verify"' \
  'case "summary.render"' \
  'case "compensate"' \
  'EFFECT_DISPATCH_DISABLED' \
  'COMPENSATION_DISABLED'; do
  require_text "$DEBUG_EXECUTORS" "$marker"
done

for test_name in \
  schemasExactlyCoverPlanAllowlistAndExposeSevenDebugContracts \
  registryEnforcesExactClassesAndRejectsUnsafeExecutorDeclarations \
  contextExecutorBindsExpectedSnapshotAndNeverUpgradesTrust \
  policyAndApprovalRequireExplicitTrustedAuthorityEvidence \
  effectAndCompensationExecutorsAlwaysFailClosedWithoutDispatch \
  verificationDistinguishesUnavailableMismatchAndMatch \
  summaryIsBoundedDeterministicAndContainsNoFreeFormModelText \
  modelToolAndMemorySchemasHaveNoDebugExecutorOrFallback; do
  require_text "$TEST" "$test_name"
done

for marker in \
  typed_node_executor_probe_complete \
  typed_node_executor_contract_defined \
  typed_node_executor_exact_class_verified \
  typed_node_executor_context_verified \
  typed_node_executor_policy_approval_verified \
  typed_node_executor_effect_fail_closed_verified \
  typed_node_executor_verification_verified \
  typed_node_executor_summary_verified \
  typed_node_executor_unsupported_fail_closed_verified \
  typed_node_executor_android13_arm64_verified; do
  require_text "$PROBE" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
for marker in \
  typed_node_executor_graph_dispatch_enabled=false \
  typed_node_executor_production_wired=false \
  effect_dispatch_enabled=false \
  model_invoked=false \
  network_accessed=false \
  hardware_accessed=false; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

require_text "$MANIFEST" '.graph.TypedNodeExecutorsProbeActivity'
if grep -R -Fq 'DeterministicNodeExecutors' \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/release" 2>/dev/null; then
  echo "P3-W02 deterministic executor leaked into main/release source" >&2
  exit 1
fi
if grep -Fq 'TypedNodeExecutorsProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P3-W02 debug probe leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq 'TypedNodeExecutor|DeterministicNodeExecutors|NodeExecutionInput' \
    "$ROOT_DIR/$RUNTIME_SERVICE" "$ROOT_DIR/$GRAPH_RUNTIME"; then
  echo "P3-W02 typed executors were wired into Graph or a production Service" >&2
  exit 1
fi
if grep -R -Eiq \
    'ObjectInputStream|ObjectOutputStream|Class[.]forName|java[.]lang[.]reflect|Gson|Jackson|Serializable|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|java[.]net|okhttp|http://|https://|ioctl|sysfs|/dev/' \
    "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$INPUT" "$ROOT_DIR/$OUTPUT" \
    "$ROOT_DIR/$RESULT" "$ROOT_DIR/$SCHEMAS" "$ROOT_DIR/$EXECUTOR" \
    "$ROOT_DIR/$DEBUG_EXECUTORS"; then
  echo "P3-W02 typed executor contract references arbitrary serialization, reflection, network, vehicle, or hardware APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P3-W02 Typed node executors"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" '`P3-W02` Typed node executors'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P3-W02 Typed Node Executor trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android P3-W02 Typed Node Executors"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android P3-W02 Typed Node Executors"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P3-W02 Typed Node Executor Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P3-W02 typed executor"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P3-W02 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P3-W02 Typed node executors"

printf '%s\n' \
  "Central Brain Android Typed Node Executor check passed" \
  "typed_node_executor_contract_defined=true" \
  "typed_node_executor_schema_count=11" \
  "typed_node_executor_debug_count=7" \
  "typed_node_executor_exact_class_verified=true" \
  "typed_node_executor_effect_fail_closed_verified=true" \
  "typed_node_executor_unsupported_fail_closed_verified=true" \
  "typed_node_executor_graph_dispatch_enabled=false" \
  "typed_node_executor_production_wired=false" \
  "effect_dispatch_enabled=false" \
  "model_invoked=false" \
  "hardware_accessed=false"
