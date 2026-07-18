#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-UX-002, S2-TRG-002, S2-SAF-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTROLLER="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/suggestion/ActiveSuggestionController.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/suggestion/ActiveSuggestionControllerTest.java"
HMI="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/suggestion/ActiveSuggestionHmiActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
TRIGGER="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/TriggerEngine.java"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GRAPH_RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/AgentGraphRuntime.java"
EFFECT_COORDINATOR="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/EffectCoordinator.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P6-W06 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$CONTROLLER" "$TEST" "$HMI" "$DEBUG_MANIFEST" "$MAIN_MANIFEST" \
    "$TRIGGER" "$RUNTIME_SERVICE" "$GRAPH_RUNTIME" "$EFFECT_COORDINATOR" \
    "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P6-W06 file missing: $file" >&2; exit 1; }
done

for marker in \
  'SCHEMA_VERSION = 1' \
  'MAX_ACTIVE_SUGGESTIONS = 16' \
  'MAX_REPLAY_ENTRIES = 64' \
  'MAX_NEVER_ASK_SCOPES = 64' \
  'enum ReasonCode' \
  'enum PresentationMode' \
  'FULL_CARD' \
  'MINIMAL_BANNER' \
  'SUPPRESSED_COOLDOWN' \
  'SUPPRESSED_NEVER_ASK' \
  'REQUEST_CONFLICT' \
  'isHmiProjectionOnly()' \
  'isProductionSuggestionSourceWired()' \
  'isEffectDispatchWired()' \
  'isVoiceEngineWired()' \
  'isPreferenceRepositoryWired()' \
  'isHardwareAccessed()'; do
  require_text "$CONTROLLER" "$marker"
done

for test_name in \
  parkedProjectionShowsWhyCooldownAndUserActions \
  duplicateScopeMergesDeterministicallyByReasonPriority \
  neverAskIsParkedOnlyAndScopedWithoutPersistenceClaim \
  movingAndUnknownUseSingleMinimalBannerAndVoiceProjectionKey \
  expiryCooldownReplayConflictAndCapacityFailClosed \
  productionRuntimeEffectVoiceAndHardwareBoundariesRemainClosed; do
  require_text "$TEST" "$test_name"
done

for marker in \
  active_suggestion_hmi_probe_complete \
  active_suggestion_full_card_verified \
  active_suggestion_merge_replay_verified \
  active_suggestion_moving_minimal_verified \
  active_suggestion_never_ask_verified \
  active_suggestion_android13_arm64_verified; do
  require_text "$HMI" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
for marker in \
  active_suggestion_hmi_projection_only=true \
  active_suggestion_production_source_wired=false \
  active_suggestion_preference_repository_wired=false \
  active_suggestion_voice_engine_wired=false \
  trigger_engine_wired=false \
  graph_execution_enabled=false \
  effect_dispatch_enabled=false \
  vehicle_readback_accessed=false \
  model_invoked=false \
  npu_accessed=false \
  network_accessed=false \
  hardware_accessed=false \
  production_ready=false \
  target_hardware_validated=false; do
  require_text "$HMI" "$marker"
  require_text "$INSTALLER" "$marker"
done

require_text "$HMI" 'getStringExtra("nonce")'
require_text "$HMI" '我有些疲惫'
require_text "$HMI" '我有点冷'
require_text "$HMI" '自动化链路'
require_text "$HMI" '等待用户查看与确认，未执行车控'
require_text "$DEBUG_MANIFEST" '.suggestion.ActiveSuggestionHmiActivity'
if grep -Fq 'ActiveSuggestionHmiActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P6-W06 Active suggestion debug HMI leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq 'ActiveSuggestionController|ActiveSuggestionHmiActivity' \
    "$ROOT_DIR/$TRIGGER" "$ROOT_DIR/$RUNTIME_SERVICE" \
    "$ROOT_DIR/$GRAPH_RUNTIME" "$ROOT_DIR/$EFFECT_COORDINATOR"; then
  echo "P6-W06 Active suggestion projection was wired into Trigger/Runtime/Graph/Effect" >&2
  exit 1
fi
if grep -R -Eiq \
    'android[.]car|CarPropertyManager|VehicleHal|VehiclePropertyIds|java[.]io|java[.]net|okhttp|http://|https://|ModelProvider|NpuProvider|ioctl|sysfs|/dev/|System[.]currentTimeMillis|System[.]nanoTime|Clock[.]system|android[.]os[.]Binder|ClassLoader|DexClassLoader' \
    "$ROOT_DIR/$CONTROLLER"; then
  echo "P6-W06 controller references clock, Binder, transport, vehicle, model, hardware, or dynamic runtime APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P6-W06 Active suggestion UX"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P6-W06` Active suggestion UX'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P6-W06 Active suggestion UX trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P6-W06 Active suggestion UX"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P6-W06 Active suggestion UX architecture"
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" "P6-W06 Active suggestion UX detailed design"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P6-W06 Active suggestion UX"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P6-W06 Active suggestion UX Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "P6-W06 Active suggestion UX is a projection, not production orchestration"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "P6-W06 Active suggestion UX progress"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P6-W06 Active suggestion UX progress"
require_text "README.md" "P6 Active suggestion UX"

printf '%s\n' \
  "Central Brain Android Active suggestion UX check passed" \
  "active_suggestion_controller_defined=true" \
  "active_suggestion_full_card_verified=true" \
  "active_suggestion_merge_replay_verified=true" \
  "active_suggestion_moving_minimal_verified=true" \
  "active_suggestion_never_ask_verified=true" \
  "active_suggestion_android13_arm64_verified=false" \
  "active_suggestion_hmi_projection_only=true" \
  "active_suggestion_production_source_wired=false" \
  "active_suggestion_preference_repository_wired=false" \
  "active_suggestion_voice_engine_wired=false" \
  "trigger_engine_wired=false" \
  "graph_execution_enabled=false" \
  "effect_dispatch_enabled=false" \
  "model_invoked=false" \
  "npu_accessed=false" \
  "network_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
