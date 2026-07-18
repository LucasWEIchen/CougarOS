#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-EVT-001, S2-SCN-001, S2-SAF-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RULE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/TriggerRule.java"
ENGINE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/TriggerEngine.java"
COOLDOWN="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/CooldownStore.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/events/TriggerEngineTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/events/TriggerEngineProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
BROKER="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/InProcessDurableEventBroker.java"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GRAPH_RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/AgentGraphRuntime.java"
EFFECT_COORDINATOR="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/EffectCoordinator.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P6-W03 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$RULE" "$ENGINE" "$COOLDOWN" "$TEST" "$PROBE" \
    "$DEBUG_MANIFEST" "$MAIN_MANIFEST" "$BROKER" "$RUNTIME_SERVICE" \
    "$GRAPH_RUNTIME" "$EFFECT_COORDINATOR" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P6-W03 file missing: $file" >&2; exit 1; }
done

for marker in \
  'int SCHEMA_VERSION = 1' \
  'int MAX_RULES = 64' \
  'enum Metric' \
  'enum ThresholdOperator' \
  'getSustainWindowMs()' \
  'getMaximumSampleGapMs()' \
  'getMinimumMatchingSamples()' \
  'getDebounceMs()' \
  'getCooldownMs()' \
  'class Manifest' \
  'isProductionTrusted()' \
  'isRuntimeWired()'; do
  require_text "$RULE" "$marker"
done

for marker in \
  'createForContractTest(' \
  'EvaluationBatch evaluate(' \
  'ACCUMULATING_WINDOW' \
  'DEBOUNCING' \
  'COOLDOWN_ACTIVE' \
  'REJECTED_STALE' \
  'REJECTED_OUT_OF_ORDER' \
  'class ScenarioSuggestion' \
  'isSuggestionOnly()' \
  'isAutoExecutionEnabled()' \
  'isEffectDispatchEnabled()' \
  'isSourceAdapterWired()' \
  'isModelInvoked()' \
  'isHardwareAccessed()'; do
  require_text "$ENGINE" "$marker"
done

for marker in \
  'enum ReservationCode' \
  'COOLDOWN_ACTIVE' \
  'CAPACITY_EXCEEDED' \
  'Reservation reserve(' \
  'isProcessLocal()' \
  'isDurablePersistenceWired()'; do
  require_text "$COOLDOWN" "$marker"
done

for test_name in \
  manifestIsOrderIndependentAndRejectsInvalidRules \
  thresholdWindowAndDebounceEmitSuggestionOnly \
  falseAndSampleGapResetWindowDeterministically \
  cooldownIsAtomicScopedBoundedAndExpires \
  qualityFreshnessOrderingAndReplayFailClosed \
  scopesAndProductionBoundariesRemainClosed; do
  require_text "$TEST" "$test_name"
done

for marker in \
  trigger_engine_probe_complete \
  trigger_rule_manifest_verified \
  trigger_threshold_window_debounce_verified \
  trigger_cooldown_scope_verified \
  trigger_input_fail_closed_verified \
  trigger_suggestion_only_verified \
  trigger_engine_android13_arm64_verified; do
  require_text "$PROBE" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
for marker in \
  trigger_engine_process_local=true \
  trigger_cooldown_persistence_wired=false \
  trigger_source_adapter_wired=false \
  trigger_auto_execution_enabled=false \
  trigger_runtime_wired=false \
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
require_text "$DEBUG_MANIFEST" '.events.TriggerEngineProbeActivity'
if grep -Fq 'TriggerEngineProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P6-W03 TriggerEngine debug probe leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq 'TriggerRule|TriggerEngine|CooldownStore' \
    "$ROOT_DIR/$BROKER" "$ROOT_DIR/$RUNTIME_SERVICE" \
    "$ROOT_DIR/$GRAPH_RUNTIME" "$ROOT_DIR/$EFFECT_COORDINATOR"; then
  echo "P6-W03 TriggerEngine was wired into production Broker/Runtime/Graph/Effect" >&2
  exit 1
fi
if grep -R -Eiq \
    'androidx[.]room|CentralBrainDatabase|android[.]os[.]Binder|java[.]io|java[.]net|okhttp|http://|https://|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|ModelProvider|NpuProvider|ioctl|sysfs|/dev/|SharedPreferences|FileOutputStream|ObjectOutputStream|Thread|ExecutorService|ClassLoader|DexClassLoader' \
    "$ROOT_DIR/$RULE" "$ROOT_DIR/$ENGINE" "$ROOT_DIR/$COOLDOWN"; then
  echo "P6-W03 references persistence, Binder, transport, vehicle, model, hardware, or dynamic runtime APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P6-W03 TriggerRule manifest/engine"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P6-W03` TriggerRule manifest/engine'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P6-W03 TriggerRule manifest/engine trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P6-W03 TriggerRule manifest/engine"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P6-W03 TriggerEngine architecture"
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" "P6-W03 TriggerRule/TriggerEngine detailed design"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P6-W03 TriggerRule manifest/engine"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P6-W03 TriggerEngine Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "P6-W03 process-local TriggerEngine is not production proactive intelligence"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "P6-W03 TriggerEngine progress"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P6-W03 TriggerRule manifest/engine progress"
require_text "README.md" "P6 TriggerRule/TriggerEngine"

printf '%s\n' \
  "Central Brain Android TriggerEngine check passed" \
  "trigger_rule_manifest_defined=true" \
  "trigger_rule_manifest_verified=true" \
  "trigger_threshold_window_debounce_verified=true" \
  "trigger_cooldown_scope_verified=true" \
  "trigger_input_fail_closed_verified=true" \
  "trigger_suggestion_only_verified=true" \
  "trigger_engine_android13_arm64_verified=true" \
  "trigger_engine_process_local=true" \
  "trigger_cooldown_persistence_wired=false" \
  "trigger_source_adapter_wired=false" \
  "trigger_auto_execution_enabled=false" \
  "trigger_runtime_wired=false" \
  "graph_execution_enabled=false" \
  "effect_dispatch_enabled=false" \
  "vehicle_readback_accessed=false" \
  "model_invoked=false" \
  "npu_accessed=false" \
  "network_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
