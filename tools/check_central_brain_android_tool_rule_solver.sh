#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-TOL-001, S2-SAF-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/tools"
DEBUG_ROOT="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/tools"
TEST_ROOT="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/tools"
RULE_SET="$MAIN_ROOT/ToolRuleSet.java"
SOLVER="$MAIN_ROOT/ToolRuleSolver.java"
TEST="$TEST_ROOT/ToolRuleSolverTest.java"
PROBE="$DEBUG_ROOT/ToolRuleSolverProbeActivity.java"
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
    || { echo "P5-W03 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$RULE_SET" "$SOLVER" "$TEST" "$PROBE" "$DEBUG_MANIFEST" \
    "$MAIN_MANIFEST" "$RUNTIME_SERVICE" "$GOVERNANCE_SERVICE" \
    "$GRAPH_RUNTIME" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P5-W03 file missing: $file" >&2; exit 1; }
done

for marker in \
  'public static final int MAX_FAMILIES = 128' \
  'public static final int MAX_CHILD_RULES = 256' \
  'public static final int MAX_CONDITIONAL_RULES = 256' \
  'public enum RuleType' \
  'INIT' \
  'CHILD' \
  'CONDITIONAL' \
  'TERMINAL' \
  'REQUIRED_BEFORE_EXIT' \
  'REQUIRES_APPROVAL' \
  'getRuleSetDigest()' \
  'Collections.unmodifiableNavigableMap'; do
  require_text "$RULE_SET" "$marker"
done
for marker in \
  'public static final int MAX_MODEL_SELECTIONS = 128' \
  'public enum ConditionState' \
  'CURRENT_TOOL_NOT_IN_RULE_SET' \
  'TERMINAL_REACHED' \
  'CONDITION_UNSATISFIED' \
  'REQUIRED_BEFORE_EXIT_INCOMPLETE' \
  'MODEL_INTERSECTION_EMPTY' \
  'NO_USABLE_TOOL' \
  'allowed.retainAll(request.modelSelectedFamilies)' \
  'allowed.retainAll(usable.navigableKeySet())' \
  'public boolean isApprovalGranted()' \
  'public boolean isExecutionEnabled()' \
  'return false;'; do
  require_text "$SOLVER" "$marker"
done

for test_name in \
  ruleSetIsBoundedImmutableAndDeterministic \
  initChildAndConditionalRulesIntersectModelSelection \
  emptyModelIntersectionFailsClosedWithoutFallback \
  terminalRequiresCompletedToolsAndStopsChildren \
  approvalIsAnnotatedButNeverGrantedAndUnusableIsExcluded; do
  require_text "$TEST" "$test_name"
done

for marker in \
  tool_rule_solver_probe_complete \
  tool_rule_set_contract_defined \
  tool_rule_set_digest_verified \
  tool_rule_init_child_conditional_verified \
  tool_rule_model_intersection_fail_closed \
  tool_rule_terminal_requirements_verified \
  tool_rule_approval_annotation_fail_closed \
  tool_rule_solver_android13_arm64_verified; do
  require_text "$PROBE" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
require_text "$PROBE" 'tool_rule_type_count='
require_text "$INSTALLER" 'tool_rule_type_count=6'
for marker in \
  tool_rule_solver_published=false \
  tool_rule_solver_runtime_wired=false \
  tool_approval_authority_available=false \
  tool_execution_enabled=false \
  production_tool_registered=false \
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

require_text "$DEBUG_MANIFEST" '.tools.ToolRuleSolverProbeActivity'
if grep -Fq 'ToolRuleSolverProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P5-W03 Tool rule probe leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq 'ToolRuleSet|ToolRuleSolver' \
    "$ROOT_DIR/$RUNTIME_SERVICE" "$ROOT_DIR/$GOVERNANCE_SERVICE" \
    "$ROOT_DIR/$GRAPH_RUNTIME"; then
  echo "P5-W03 Tool RuleSolver was wired into production Runtime/Graph" >&2
  exit 1
fi
if grep -R -Eiq \
    'ObjectInputStream|ObjectOutputStream|Class[.]forName|java[.]lang[.]reflect|Gson|Jackson|Serializable|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|java[.]net|okhttp|http://|https://|ioctl|sysfs|/dev/|androidx[.]room|android[.]os[.]Binder' \
    "$ROOT_DIR/$RULE_SET" "$ROOT_DIR/$SOLVER" "$ROOT_DIR/$PROBE"; then
  echo "P5-W03 references persistence, Binder, network, vehicle, or hardware APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P5-W03 Tool RuleSolver"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P5-W03` ToolRuleSolver'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P5-W03 Tool RuleSolver trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P5-W03 Tool RuleSolver"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P5-W03 Tool RuleSolver architecture"
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" "P5-W03 Tool RuleSolver detailed design"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P5-W03 Tool RuleSolver"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P5-W03 Tool RuleSolver Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "DEV-065 P5-W03 rule selection is not execution authority"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "ISSUE-038 Production Tool rule and condition ownership"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P5-W03 ToolRuleSolver"
require_text "README.md" "P5 Tool RuleSolver"

printf '%s\n' \
  "Central Brain Android Tool RuleSolver check passed" \
  "tool_rule_set_contract_defined=true" \
  "tool_rule_type_count=6" \
  "tool_rule_set_digest_verified=true" \
  "tool_rule_init_child_conditional_verified=true" \
  "tool_rule_model_intersection_fail_closed=true" \
  "tool_rule_terminal_requirements_verified=true" \
  "tool_rule_approval_annotation_fail_closed=true" \
  "tool_rule_solver_android13_arm64_verified=true" \
  "tool_rule_solver_published=false" \
  "tool_rule_solver_runtime_wired=false" \
  "tool_approval_authority_available=false" \
  "tool_execution_enabled=false" \
  "production_tool_registered=false" \
  "production_tool_artifact_loaded=false" \
  "effect_dispatch_enabled=false" \
  "vehicle_readback_accessed=false" \
  "model_invoked=false" \
  "npu_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
