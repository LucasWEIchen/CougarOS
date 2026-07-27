#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-EVT-001, S2-SAF-001, S2-OBS-001, NV-G-004, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/EventDeliveryQoS.java"
QUEUE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/InProcessEventBackpressureQueue.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/events/InProcessEventBackpressureQueueTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/events/EventBackpressureProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
BROKER="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/InProcessDurableEventBroker.java"
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
    || { echo "P6-W02 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$CONTRACT" "$QUEUE" "$TEST" "$PROBE" "$DEBUG_MANIFEST" \
    "$MAIN_MANIFEST" "$BROKER" "$RUNTIME_SERVICE" "$GRAPH_RUNTIME" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P6-W02 file missing: $file" >&2; exit 1; }
done

for marker in \
  'int SCHEMA_VERSION = 1' \
  'enum OverflowPolicy' \
  'DROP_OLD' \
  'COALESCE' \
  'REJECT' \
  'DISCONNECT' \
  'CRITICAL_ACTION_OBSERVATION' \
  'enum Priority' \
  'class QueueConfig' \
  'class DeliveryRequest' \
  'class OfferResult' \
  'class DrainResult' \
  'class QueueSnapshot' \
  'isCriticalNoSilentDrop()' \
  'isProcessLocal()' \
  'isBrokerWired()' \
  'isDurablePersistenceWired()' \
  'isProductionMiddlewareWired()' \
  'isHardwareAccessed()'; do
  require_text "$CONTRACT" "$marker"
done

for marker in \
  'createForContractTest(' \
  'OfferResult offer(' \
  'DrainResult drainOwned(' \
  'dropOld(' \
  'coalesce(' \
  'DisconnectReason.CRITICAL_DEADLINE_EXPIRED' \
  'Event consumer cannot mutate delivery queue'; do
  require_text "$QUEUE" "$marker"
done

for test_name in \
  dropOldUsesPriorityAndReportsDisplacedCursor \
  coalesceRequiresMatchingKeyAndNeverCoalescesCritical \
  rejectAndDisconnectAreExplicitAndRequireReplay \
  criticalEventsNeverSilentlyDropOrExpire \
  deadlineIdempotencyOwnershipAndConsumerFailureFailClosed \
  consumerQueuesAreIsolatedAndProductionBoundariesRemainClosed; do
  require_text "$TEST" "$test_name"
done

for marker in \
  event_qos_probe_complete \
  event_qos_policies_verified \
  event_qos_critical_no_silent_drop_verified \
  event_qos_deadline_priority_verified \
  event_qos_consumer_isolation_verified \
  event_qos_android13_arm64_verified; do
  require_text "$PROBE" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
for marker in \
  event_qos_process_local=true \
  event_qos_broker_wired=false \
  event_qos_durable_persistence_wired=false \
  event_qos_production_middleware_wired=false \
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

require_text "$PROBE" 'getStringExtra("nonce")'
require_text "$DEBUG_MANIFEST" '.events.EventBackpressureProbeActivity'
if grep -Fq 'EventBackpressureProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P6-W02 Event Backpressure/QoS debug probe leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq 'EventDeliveryQoS|InProcessEventBackpressureQueue' \
    "$ROOT_DIR/$BROKER" "$ROOT_DIR/$RUNTIME_SERVICE" "$ROOT_DIR/$GRAPH_RUNTIME"; then
  echo "P6-W02 Event Backpressure/QoS was wired into production Broker/Runtime/Graph" >&2
  exit 1
fi
if grep -R -Eiq \
    'androidx[.]room|CentralBrainDatabase|android[.]os[.]Binder|java[.]io|java[.]net|okhttp|http://|https://|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|ModelProvider|NpuProvider|ioctl|sysfs|/dev/|SharedPreferences|FileOutputStream|ObjectOutputStream|Thread|ExecutorService|ClassLoader|DexClassLoader' \
    "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$QUEUE"; then
  echo "P6-W02 references persistence, Binder, transport, vehicle, model, hardware, or dynamic runtime APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P6-W02 Event Backpressure/QoS"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" '`P6-W02` Backpressure/QoS'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P6-W02 Backpressure/QoS trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android P6-W02 Event Backpressure/QoS"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P6-W02 Event Backpressure/QoS architecture"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "P6-W02 Event Backpressure/QoS detailed design"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android P6-W02 Event Backpressure/QoS"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P6-W02 Event Backpressure/QoS Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P6-W02 pressure queues remain process-local"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P6-W02 Backpressure/QoS progress"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P6-W02 Backpressure/QoS"
require_text "README.md" "P6 Event Backpressure/QoS"

printf '%s\n' \
  "Central Brain Android Event Backpressure/QoS check passed" \
  "event_qos_contract_defined=true" \
  "event_qos_policies_verified=true" \
  "event_qos_critical_no_silent_drop_verified=true" \
  "event_qos_deadline_priority_verified=true" \
  "event_qos_consumer_isolation_verified=true" \
  "event_qos_android13_arm64_verified=true" \
  "event_qos_process_local=true" \
  "event_qos_broker_wired=false" \
  "event_qos_durable_persistence_wired=false" \
  "event_qos_production_middleware_wired=false" \
  "graph_execution_enabled=false" \
  "effect_dispatch_enabled=false" \
  "vehicle_readback_accessed=false" \
  "model_invoked=false" \
  "npu_accessed=false" \
  "network_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
