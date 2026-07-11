#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004, NV-F-001/011, NV-G-004/006, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCHEDULER="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/scheduler/InferenceResourceScheduler.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/scheduler/InferenceResourceSchedulerTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/scheduler/InferenceSchedulerContractProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android inference scheduler file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android inference scheduler pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in \
  "$SCHEDULER" \
  "$TEST" \
  "$PROBE" \
  "$DEBUG_MANIFEST" \
  "$RUNTIME" \
  "$GOVERNANCE" \
  "$INSTALLER"; do
  require_file "$path"
done

require_text "$SCHEDULER" "interface ElapsedRealtimeClock"
require_text "$SCHEDULER" "TrustedSubmission fromRuntimePolicy"
require_text "$SCHEDULER" "EffectivePriority"
require_text "$SCHEDULER" "Admission admit(TrustedSubmission submission)"
require_text "$SCHEDULER" "Claim claimNext()"
require_text "$SCHEDULER" "CancelResult cancelOwned("
require_text "$SCHEDULER" "Settlement settle("
require_text "$SCHEDULER" "ExpiryReport sweepDeadlines()"
require_text "$SCHEDULER" "GLOBAL_QUEUE_QUOTA_EXCEEDED"
require_text "$SCHEDULER" "OWNER_QUEUE_QUOTA_EXCEEDED"
require_text "$SCHEDULER" "maxRunningGlobal"
require_text "$SCHEDULER" "maxRunningPerOwner"
require_text "$SCHEDULER" "getMaxConcurrentRequests()"
require_text "$SCHEDULER" "DISPATCH_ORDER"
require_text "$SCHEDULER" "CANCEL_REQUESTED"
require_text "$SCHEDULER" "record.cancelReason == CancelReason.OWNER_REQUEST"
require_text "$SCHEDULER" "static RouteTarget fromProfile"
require_text "$SCHEDULER" "forContractTest"
require_text "$SCHEDULER" "contract-test provider ID must start with test."
require_text "$TEST" "dispatchOrderIsPriorityThenDeadlineThenFifo"
require_text "$TEST" "admissionEnforcesOwnerAndGlobalQueueQuotas"
require_text "$TEST" "claimEnforcesOwnerRunningQuotaAndProviderSlots"
require_text "$TEST" "deadlinesExpireQueuedAndRequestRunningProviderCancellation"
require_text "$TEST" "queuedCancelIsLocalAndRunningCancelOnlyReturnsDirective"
require_text "$TEST" "currentProfilesRemainNonRoutable"
require_text "$TEST" "unsupportedCancellationKeepsSlotUntilTerminalAndReportsDeadlineOnce"
require_text "$TEST" "ownerIsolationAndLeaseValidationFailClosed"
require_text "$DEBUG_MANIFEST" ".scheduler.InferenceSchedulerContractProbeActivity"

for marker in \
  "inference_scheduler_contract_verified=true" \
  "trusted_effective_priority_verified=true" \
  "priority_deadline_fifo_order_verified=true" \
  "global_owner_queue_quota_verified=true" \
  "global_owner_running_quota_verified=true" \
  "provider_slot_quota_verified=true" \
  "queued_deadline_expiry_verified=true" \
  "running_deadline_cancel_directive_verified=true" \
  "queued_cancel_verified=true" \
  "running_cancel_requires_provider_verified=true" \
  "completion_after_cancel_resolved=true" \
  "current_profiles_non_routable_verified=true"; do
  require_text "$PROBE" "${marker%=true}="
  require_text "$INSTALLER" "$marker"
done
for marker in \
  "provider_cancel_invoked=false" \
  "scheduler_production_wired=false" \
  "model_provider_runtime_wired=false" \
  "model_router_dispatch_enabled=false" \
  "hardware_accessed=false"; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

if grep -Eq 'InferenceResourceScheduler|scheduler\.' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE"; then
  echo "R5A2 scheduler must not be wired into production Services" >&2
  exit 1
fi
if grep -Eq 'System\.currentTimeMillis|Instant\.now|new Date' "$ROOT_DIR/$SCHEDULER"; then
  echo "R5A2 deadlines must use the elapsed-realtime clock contract" >&2
  exit 1
fi
if grep -Eq '\.infer\(|provider\.cancel\(|ModelProvider[[:space:]]+[a-zA-Z]' \
    "$ROOT_DIR/$SCHEDULER"; then
  echo "R5A2 scheduler must produce leases/directives without invoking a provider" >&2
  exit 1
fi
if grep -R -Eiq \
    'java\.net|okhttp|http://|https://|ioctl|sysfs|/dev/|CarPropertyManager|VehicleHal' \
    "$ROOT_DIR/$SCHEDULER" "$ROOT_DIR/$PROBE"; then
  echo "R5A2 scheduler unexpectedly references network or hardware access" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "R5A2 Inference Resource Scheduler"
require_text "docs/CENTRAL_BRAIN_ANDROID_RUNTIME_EVOLUTION_PLAN.md" "R5A2 inference resource scheduler"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R5A2 inference resource scheduler trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android R5A2 Inference Resource Scheduler"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R5A2 Inference Resource Scheduler"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R5A2 Scheduler Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "R5A2 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "R5A2 进展"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "R5A2 inference resource scheduler"

echo "Central Brain Android inference scheduler check passed"
