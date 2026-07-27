#!/usr/bin/env bash
set -euo pipefail

# Req IDs: XSC-002/004/005, FW-U-003, NV-G-004/006/007, NV-P-002/006, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_CORE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/BoundedEventRuntime.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/events/BoundedEventRuntimeTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/events/BoundedEventRuntimeProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
DIAGNOSTIC="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainDiagnosticService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android bounded Event runtime file: $path" >&2
    exit 1
  fi
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
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android bounded Event runtime pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in \
  "$RUNTIME_CORE" \
  "$TEST" \
  "$PROBE" \
  "$DEBUG_MANIFEST" \
  "$RUNTIME" \
  "$GOVERNANCE" \
  "$DIAGNOSTIC" \
  "$INSTALLER"; do
  require_file "$path"
done

require_text "$RUNTIME_CORE" 'TOPIC_TASK_STATE = "runtime.task.state"'
require_text "$RUNTIME_CORE" 'TOPIC_POLICY_DECISION = "governance.policy.decision"'
require_text "$RUNTIME_CORE" 'TOPIC_MODEL_HEALTH = "model.runtime.health"'
require_text "$RUNTIME_CORE" "createForContractTest("
require_text "$RUNTIME_CORE" "TrustedPublication fromRuntimePolicy("
require_text "$RUNTIME_CORE" "TrustedSubscription fromRuntimePolicy("
require_text "$RUNTIME_CORE" "synchronized PublishResult publish("
require_text "$RUNTIME_CORE" "synchronized SubscribeResult subscribe("
require_text "$RUNTIME_CORE" "synchronized DispatchResult dispatchOwned("
require_text "$RUNTIME_CORE" "synchronized CancelResult cancelOwned("
require_text "$RUNTIME_CORE" "UNKNOWN_TOPIC"
require_text "$RUNTIME_CORE" "FUTURE_CURSOR"
require_text "$RUNTIME_CORE" "OBSERVER_FAILED"
require_text "$RUNTIME_CORE" "ALREADY_CANCELLED"
require_text "$RUNTIME_CORE" "rejectObserverReentrancy"
require_text "$RUNTIME_CORE" "markRetentionGap"
require_text "$RUNTIME_CORE" "markOverflow"
require_text "$RUNTIME_CORE" "MAX_CANCELLED_TOMBSTONES"
require_text "$RUNTIME_CORE" "isCursorPersistenceWired()"
require_text "$RUNTIME_CORE" "isProductionBrokerWired()"
require_text "$TEST" "trustedTopicsUseMonotonicBoundedSequence"
require_text "$TEST" "subscriptionReplayIsIdempotentAndOwnerScoped"
require_text "$TEST" "cursorAndQueueOverflowPrecedeRetainedEvent"
require_text "$TEST" "observerFailureRetainsEventForRetry"
require_text "$TEST" "observerReentrantMutationFailsClosedWithoutQueueCorruption"
require_text "$TEST" "cancellationIsOwnerIsolatedAndIdempotent"
require_text "$TEST" "invalidTopicFutureCursorAndQueueLimitFailClosed"
require_text "$DEBUG_MANIFEST" ".events.BoundedEventRuntimeProbeActivity"

for marker in \
  "event_runtime_contract_verified=true" \
  "event_trusted_topic_verified=true" \
  "event_monotonic_sequence_verified=true" \
  "event_cursor_replay_verified=true" \
  "event_overflow_before_delivery_verified=true" \
  "event_owner_isolation_verified=true" \
  "event_subscription_idempotency_verified=true" \
  "event_cancel_idempotency_verified=true" \
  "event_observer_retry_verified=true"; do
  require_text "$PROBE" "${marker%=true}="
  require_text "$INSTALLER" "$marker"
done
for marker in \
  "event_runtime_process_only=true" \
  "event_cursor_persistence_wired=false" \
  "event_broker_production_wired=false" \
  "event_callback_binder_wired=false" \
  "dds_runtime_active=false" \
  "network_transport_active=false" \
  "vehicle_bus_accessed=false" \
  "service_dispatch_triggered=false" \
  "hardware_accessed=false"; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

if grep -Eq 'import .*BoundedEventRuntime|new BoundedEventRuntime|createForContractTest' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE" "$ROOT_DIR/$DIAGNOSTIC"; then
  echo "R6A1 Event runtime must not be wired into production Services" >&2
  exit 1
fi
if grep -Eq 'EventCursorEntity|RuntimeStateDao|CentralBrainDatabase' \
    "$ROOT_DIR/$RUNTIME_CORE" "$ROOT_DIR/$PROBE"; then
  echo "R6A1 Event cursor/subscription state must remain process-only" >&2
  exit 1
fi
if grep -R -Eiq \
    'java\.net|okhttp|http://|https://|ioctl|sysfs|/dev/|CarPropertyManager|VehicleHal|SocketCAN|SharedMemory' \
    "$ROOT_DIR/$RUNTIME_CORE" "$ROOT_DIR/$PROBE"; then
  echo "R6A1 Event runtime unexpectedly references transport or hardware" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "R6A1 Bounded Event Runtime Contract"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "R6A1 bounded Event runtime contract"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6A1 bounded Event runtime trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android R6A1 Bounded Event Runtime"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android R6A1 Bounded Event Runtime"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6A1 Event Runtime Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6A1 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6A1 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6A1 bounded Event runtime"

echo "Central Brain Android bounded Event runtime check passed"
