#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-GRF-001, NV-G-004, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRAPH_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph"
RETRY="$GRAPH_ROOT/NodeRetryPolicy.java"
TIMEOUT="$GRAPH_ROOT/NodeTimeoutPolicy.java"
BACKOFF="$GRAPH_ROOT/BackoffCalculator.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/graph/NodeRetryTimeoutPolicyTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/graph/RetryTimeoutPolicyProbeActivity.java"
MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
GRAPH_RUNTIME="$GRAPH_ROOT/AgentGraphRuntime.java"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P3-W04 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$RETRY" "$TIMEOUT" "$BACKOFF" "$TEST" "$PROBE" \
    "$MANIFEST" "$MAIN_MANIFEST" "$GRAPH_RUNTIME" "$RUNTIME_SERVICE" \
    "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P3-W04 file missing: $file" >&2; exit 1; }
done

for marker in \
  'public final class BackoffCalculator' \
  'MAX_DELAY_MS = PlanContract.MAX_NODE_TIMEOUT_MS' \
  'MAX_JITTER_PERMILLE = 250' \
  'boundedDefault()' \
  'graph.retry.jitter.v1' \
  'nextAttempt < 2 || nextAttempt > PlanContract.MAX_ATTEMPTS'; do
  require_text "$BACKOFF" "$marker"
done

for marker in \
  'public final class NodeTimeoutPolicy' \
  'PlanContract.validateNode(node)' \
  'openAttempt(' \
  'saturatedAdd(startedAtElapsedMs, timeoutMs)' \
  'Math.min(nodeDeadline, planDeadlineElapsedMs)' \
  'nowElapsedMs >= window.deadlineElapsedMs' \
  'Status.EXPIRED'; do
  require_text "$TIMEOUT" "$marker"
done

for marker in \
  'public final class NodeRetryPolicy' \
  'RETRYABLE_FAILURE' \
  'DELIVERY_UNKNOWN' \
  'CONFIRMED_NOT_APPLIED' \
  'STOP_ATTEMPTS_EXHAUSTED' \
  'STOP_DEADLINE_EXCEEDED' \
  'STOP_EFFECT_ALREADY_APPLIED' \
  'graph.retry.key.v1' \
  'graph.retry.decision.v1' \
  'effectDispatchNode' \
  'return stop(Action.RECONCILE' \
  'eligibleAtElapsedMs >= planDeadlineElapsedMs'; do
  require_text "$RETRY" "$marker"
done

for test_name in \
  deterministicBackoffIsExponentiallyBoundedAndJittered \
  timeoutWindowUsesMonotonicTimeAndPlanDeadlineClamp \
  retryableNonEffectUsesAttemptBudgetAndStableDecision \
  terminalAndCancelledFailuresNeverRetry \
  effectRetryRequiresIdempotencyAndReconcileProof \
  effectReconcileRemainsRequiredAfterRetryBudgetExpires \
  planDeadlineAndOverflowPreventRetryDispatch \
  malformedPolicyInputsFailClosed; do
  require_text "$TEST" "$test_name"
done

for marker in \
  retry_timeout_policy_probe_complete \
  node_retry_policy_defined \
  node_timeout_policy_defined \
  backoff_deterministic_bounded_verified \
  timeout_deadline_clamp_verified \
  retry_attempt_budget_verified \
  effect_idempotency_reconcile_gate_verified \
  retry_deadline_fail_closed_verified \
  retry_timeout_policy_android13_arm64_verified; do
  require_text "$PROBE" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
for marker in \
  retry_timeout_policy_runtime_wired=false \
  agent_graph_executor_dispatch_enabled=false \
  effect_dispatch_enabled=false \
  model_invoked=false \
  network_accessed=false \
  hardware_accessed=false; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

require_text "$MANIFEST" '.graph.RetryTimeoutPolicyProbeActivity'
if grep -Fq 'RetryTimeoutPolicyProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P3-W04 debug probe leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq 'NodeRetryPolicy|NodeTimeoutPolicy|BackoffCalculator' \
    "$ROOT_DIR/$GRAPH_RUNTIME" "$ROOT_DIR/$RUNTIME_SERVICE"; then
  echo "P3-W04 retry/timeout policy was wired into Graph or a production Service" >&2
  exit 1
fi
if grep -R -Eiq \
    'System[.](currentTimeMillis|nanoTime)|Thread[.]sleep|java[.]util[.]Random|SecureRandom|Executor|java[.]util[.]concurrent|android[.]os|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|java[.]net|okhttp|http://|https://|ioctl|sysfs|/dev/' \
    "$ROOT_DIR/$RETRY" "$ROOT_DIR/$TIMEOUT" "$ROOT_DIR/$BACKOFF"; then
  echo "P3-W04 policy owns a clock/thread/dispatcher or references network, vehicle, or hardware APIs" >&2
  exit 1
fi
if grep -Fq '.toList()' "$ROOT_DIR/$PROBE"; then
  echo "P3-W04 debug probe uses Stream.toList(), which is unavailable on the API 33 target" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P3-W04 Retry/Timeout policy"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P3-W04` Retry/Timeout policy'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P3-W04 Retry/Timeout policy trace"
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" "P3-W04 implemented retry/timeout contract"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P3-W04 Retry/Timeout Policy"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P3-W04 Retry/Timeout Policy"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P3-W04 Retry/Timeout Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "P3-W04 retry/timeout policy"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "P3-W04 进展"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P3-W04 Retry/Timeout policy"

printf '%s\n' \
  "Central Brain Android Retry/Timeout policy check passed" \
  "node_retry_policy_defined=true" \
  "node_timeout_policy_defined=true" \
  "backoff_deterministic_bounded_verified=true" \
  "timeout_deadline_clamp_verified=true" \
  "retry_attempt_budget_verified=true" \
  "effect_idempotency_reconcile_gate_verified=true" \
  "retry_deadline_fail_closed_verified=true" \
  "retry_timeout_policy_runtime_wired=false" \
  "agent_graph_executor_dispatch_enabled=false" \
  "effect_dispatch_enabled=false" \
  "model_invoked=false" \
  "hardware_accessed=false"
