#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-EVT-001, S2-SAF-001, S2-OBS-001, FW-U-001/006/007,
# NV-F-001, NV-G-004/005/006/007, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INTERFACE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/EventBroker.java"
IMPLEMENTATION="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/InProcessDurableEventBroker.java"
SUBSCRIPTION="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/EventSubscription.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/events/InProcessDurableEventBrokerTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/events/EventBrokerProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GRAPH_RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/AgentGraphRuntime.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P6-W01 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$INTERFACE" "$IMPLEMENTATION" "$SUBSCRIPTION" "$TEST" "$PROBE" \
    "$DEBUG_MANIFEST" "$MAIN_MANIFEST" "$RUNTIME_SERVICE" "$GRAPH_RUNTIME" \
    "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P6-W01 file missing: $file" >&2; exit 1; }
done

for marker in \
  'int SCHEMA_VERSION = 1' \
  'Topic<TaskStatePayload> TASK_STATE_TOPIC' \
  'Topic<PolicyDecisionPayload> POLICY_DECISION_TOPIC' \
  'Topic<ModelHealthPayload> MODEL_HEALTH_TOPIC' \
  'interface AccessAuthority' \
  'class AccessEvidence' \
  'class EventFilter' \
  'class PublishRequest' \
  'class SubscriptionRequest' \
  'class ReplayRequest' \
  'class EventRecord' \
  'class EventPage' \
  'class BrokerSnapshot' \
  'isAppendBeforeNotify()' \
  'isDurablePersistenceWired()' \
  'isDdsTransportWired()' \
  'isProductionBrokerPublished()' \
  'isRuntimeWired()' \
  'isHardwareAccessed()'; do
  require_text "$INTERFACE" "$marker"
done

for marker in \
  'implements EventBroker' \
  'createForContractTest(' \
  'PublishResult publish(' \
  'SubscribeResult subscribe(' \
  'EventPage replay(' \
  'CancelResult cancel(' \
  'hasRetentionGap(' \
  'checkAccess(' \
  'payload does not match typed topic'; do
  if [[ "$marker" == 'payload does not match typed topic' ]]; then
    require_text "$INTERFACE" "$marker"
  else
    require_text "$IMPLEMENTATION" "$marker"
  fi
done

for test_name in \
  fixedTopicsEnforcePayloadTypeAndFilterKind \
  publicationAppendsBeforeConsumerNotification \
  boundedReplayReportsGapFutureFilterAndNextCursor \
  identityPolicyAndEvidenceFailuresDoNotMutateBroker \
  subscriptionsAreIdempotentOwnerScopedAndCloseOnConsumerFailure \
  productionMiddlewareAndHardwareBoundariesRemainClosed; do
  require_text "$TEST" "$test_name"
done

for marker in \
  event_broker_probe_complete \
  event_broker_typed_topics_verified \
  event_broker_append_before_notify_verified \
  event_broker_bounded_replay_filter_verified \
  event_broker_identity_policy_verified \
  event_broker_subscription_lifecycle_verified \
  event_broker_android13_arm64_verified; do
  require_text "$PROBE" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
for marker in \
  event_broker_process_local=true \
  event_broker_durable_persistence_wired=false \
  event_broker_dds_transport_wired=false \
  event_broker_production_published=false \
  event_broker_runtime_wired=false \
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
require_text "$DEBUG_MANIFEST" '.events.EventBrokerProbeActivity'
if grep -Fq 'EventBrokerProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P6-W01 Event Broker debug probe leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq 'InProcessDurableEventBroker|EventBroker' \
    "$ROOT_DIR/$RUNTIME_SERVICE" "$ROOT_DIR/$GRAPH_RUNTIME"; then
  echo "P6-W01 Event Broker was wired into production Runtime/Graph" >&2
  exit 1
fi
if grep -R -Eiq \
    'androidx[.]room|CentralBrainDatabase|android[.]os[.]Binder|java[.]io|java[.]net|okhttp|http://|https://|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|ModelProvider|NpuProvider|ioctl|sysfs|/dev/|SharedPreferences|FileOutputStream|ObjectOutputStream|Thread|ExecutorService|ClassLoader|DexClassLoader' \
    "$ROOT_DIR/$INTERFACE" "$ROOT_DIR/$IMPLEMENTATION" "$ROOT_DIR/$SUBSCRIPTION"; then
  echo "P6-W01 references persistence, Binder, transport, vehicle, model, hardware, or dynamic runtime APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P6-W01 EventBroker interface/in-process implementation"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P6-W01` EventBroker interface/in-process implementation'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P6-W01 EventBroker interface/in-process implementation trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P6-W01 EventBroker interface/in-process implementation"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P6-W01 EventBroker interface/in-process architecture"
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" "P6-W01 EventBroker interface/in-process detailed design"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P6-W01 EventBroker interface/in-process implementation"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P6-W01 EventBroker Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "DEV-073 P6-W01 process-local Event Broker is not durable middleware"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "ISSUE-046 Event Broker durable repository and production middleware publication"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P6-W01 EventBroker interface/in-process implementation"
require_text "README.md" "P6 EventBroker interface/in-process"

printf '%s\n' \
  "Central Brain Android Event Broker check passed" \
  "event_broker_interface_defined=true" \
  "event_broker_typed_topics_verified=true" \
  "event_broker_append_before_notify_verified=true" \
  "event_broker_bounded_replay_filter_verified=true" \
  "event_broker_identity_policy_verified=true" \
  "event_broker_subscription_lifecycle_verified=true" \
  "event_broker_android13_arm64_verified=false" \
  "event_broker_process_local=true" \
  "event_broker_durable_persistence_wired=false" \
  "event_broker_dds_transport_wired=false" \
  "event_broker_production_published=false" \
  "event_broker_runtime_wired=false" \
  "graph_execution_enabled=false" \
  "effect_dispatch_enabled=false" \
  "vehicle_readback_accessed=false" \
  "model_invoked=false" \
  "npu_accessed=false" \
  "network_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
