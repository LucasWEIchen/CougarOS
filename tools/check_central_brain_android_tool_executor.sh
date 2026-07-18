#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-TOL-001, S2-SAF-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/tools"
DEBUG_ROOT="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/tools"
TEST_ROOT="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/tools"
CONTEXT="$MAIN_ROOT/ToolInvocationContext.java"
CONTRACT="$MAIN_ROOT/ToolExecutor.java"
EXECUTOR="$MAIN_ROOT/InProcessBuiltInToolExecutor.java"
TEST="$TEST_ROOT/InProcessBuiltInToolExecutorTest.java"
PROBE="$DEBUG_ROOT/ToolExecutorProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
GRAPH_RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/AgentGraphRuntime.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P5-W04 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$CONTEXT" "$CONTRACT" "$EXECUTOR" "$TEST" "$PROBE" \
    "$DEBUG_MANIFEST" "$MAIN_MANIFEST" "$RUNTIME_SERVICE" \
    "$GOVERNANCE_SERVICE" "$GRAPH_RUNTIME" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P5-W04 file missing: $file" >&2; exit 1; }
done

for marker in \
  'public static final int SCHEMA_VERSION = 1' \
  'getInvocationDigest()' \
  'getSessionDigest()' \
  'getPlanDigest()' \
  'getNodeDigest()' \
  'getAuditCorrelationDigest()' \
  'getDeadlineElapsedRealtimeMs()' \
  'getMaximumOutputBytes()' \
  'public boolean isProductionAuthority()' \
  'return false;'; do
  require_text "$CONTEXT" "$marker"
done
for marker in \
  'enum Outcome' \
  'enum FailureCode' \
  'interface CancellationSignal' \
  'interface ExecutionControl' \
  'interface BuiltInTool' \
  'APPROVAL_REQUIRED' \
  'DEADLINE_EXCEEDED' \
  'OUTPUT_TOO_LARGE' \
  'IMPLEMENTATION_FAILURE' \
  'List<AuditRecord> recentAudits(int limit)' \
  'boolean isProductionWired()' \
  'boolean isOsVirtualizationEnabled()'; do
  require_text "$CONTRACT" "$marker"
done
for marker in \
  'public static final int MAX_BINDINGS = 64' \
  'public static final int MAX_AUDIT_RECORDS = 128' \
  '"runtime.builtin".equals(manifest.getOwnerId())' \
  'CURRENT_SIGNER_NOT_ALLOWLISTED' \
  'CONTRACT_BINDING_MISMATCH' \
  'SIGNER_BINDING_MISMATCH' \
  'ARTIFACT_BINDING_MISMATCH' \
  'selection.isApprovalRequired()' \
  'schemaValidator.validateInput' \
  'schemaValidator.validateOutput' \
  'control.checkpoint()' \
  'while (audits.size() > MAX_AUDIT_RECORDS)' \
  'public boolean isProductionWired()' \
  'public boolean isOsVirtualizationEnabled()'; do
  require_text "$EXECUTOR" "$marker"
done

for test_name in \
  invocationContextIsBoundedCanonicalAndNotAuthority \
  constructionRequiresExactBuiltInSignerArtifactAndAllowlist \
  exactSelectionExecutesWithValidatedOutputAndDigestOnlyAudit \
  approvalDeadlineAndCancellationFailClosedWithoutInvocation \
  invalidInputOutputAndImplementationAreAuditedAndBounded; do
  require_text "$TEST" "$test_name"
done

for marker in \
  tool_executor_probe_complete \
  tool_executor_contract_defined \
  tool_invocation_context_defined \
  built_in_allowlist_enforced \
  built_in_signer_artifact_bound \
  tool_executor_success_verified \
  tool_executor_deadline_cancel_verified \
  tool_executor_output_limit_verified \
  tool_executor_audit_bounded_verified \
  tool_executor_android13_arm64_verified; do
  require_text "$PROBE" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
for marker in \
  tool_executor_runtime_wired=false \
  tool_execution_enabled=false \
  production_tool_execution_enabled=false \
  production_tool_registered=false \
  tool_approval_authority_available=false \
  os_virtualization_enabled=false \
  subprocess_started=false \
  dynamic_class_loading_enabled=false \
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

require_text "$DEBUG_MANIFEST" '.tools.ToolExecutorProbeActivity'
if grep -Fq 'ToolExecutorProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P5-W04 Tool executor probe leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq 'ToolExecutor|ToolInvocationContext|InProcessBuiltInToolExecutor' \
    "$ROOT_DIR/$RUNTIME_SERVICE" "$ROOT_DIR/$GOVERNANCE_SERVICE" \
    "$ROOT_DIR/$GRAPH_RUNTIME"; then
  echo "P5-W04 Tool Executor was wired into production Runtime/Graph" >&2
  exit 1
fi
if grep -R -Eiq \
    'ObjectInputStream|ObjectOutputStream|Class[.]forName|ClassLoader|java[.]lang[.]reflect|ProcessBuilder|Runtime[.]getRuntime|java[.]net|okhttp|http://|https://|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|ioctl|sysfs|/dev/|androidx[.]room|android[.]os[.]Binder' \
    "$ROOT_DIR/$CONTEXT" "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$EXECUTOR" \
    "$ROOT_DIR/$PROBE"; then
  echo "P5-W04 references dynamic loading, subprocess, persistence, network, vehicle, Binder, or hardware APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P5-W04 Tool Executor boundary"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P5-W04` ToolExecutor boundary'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P5-W04 Tool Executor boundary trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P5-W04 Tool Executor"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P5-W04 Tool Executor architecture"
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" "P5-W04 Tool Executor detailed design"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P5-W04 Tool Executor"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P5-W04 Tool Executor Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "DEV-066 P5-W04 built-in execution is not production Tool authority"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "ISSUE-039 Production built-in signer and cooperative cancellation ownership"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P5-W04 ToolExecutor boundary"
require_text "README.md" "P5 Tool Executor boundary"

printf '%s\n' \
  "Central Brain Android Tool Executor check passed" \
  "tool_executor_contract_defined=true" \
  "tool_invocation_context_defined=true" \
  "built_in_allowlist_enforced=true" \
  "built_in_signer_artifact_bound=true" \
  "tool_executor_host_execution_verified=true" \
  "tool_executor_deadline_cancel_verified=true" \
  "tool_executor_output_limit_verified=true" \
  "tool_executor_audit_bounded_verified=true" \
  "tool_executor_android13_arm64_verified=true" \
  "tool_executor_runtime_wired=false" \
  "tool_execution_enabled=false" \
  "production_tool_execution_enabled=false" \
  "production_tool_registered=false" \
  "tool_approval_authority_available=false" \
  "os_virtualization_enabled=false" \
  "subprocess_started=false" \
  "dynamic_class_loading_enabled=false" \
  "effect_dispatch_enabled=false" \
  "vehicle_readback_accessed=false" \
  "model_invoked=false" \
  "npu_accessed=false" \
  "network_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
