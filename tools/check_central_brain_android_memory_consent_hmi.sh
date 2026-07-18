#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MEM-001, S2-UX-003, S2-SAF-001, S2-OBS-001,
# FW-U-001/006/007, NV-F-001, NV-G-005/006/007, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/memory/MemoryConsentController.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/memory/MemoryConsentControllerTest.java"
HMI="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/memory/MemoryConsentHmiActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GRAPH_RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/AgentGraphRuntime.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P5-W10 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$MAIN" "$TEST" "$HMI" "$DEBUG_MANIFEST" "$MAIN_MANIFEST" \
    "$RUNTIME_SERVICE" "$GRAPH_RUNTIME" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P5-W10 file missing: $file" >&2; exit 1; }
done

for marker in \
  'public static final int SCHEMA_VERSION = 1' \
  'public static final int SOURCE_COUNT = 3' \
  'enum MemorySource' \
  'WORKING_SESSION' \
  'PROFILE_PREFERENCE' \
  'EPISODIC_SCENARIO' \
  'enum Purpose' \
  'enum Retention' \
  'enum DrivingState' \
  'PARKED' \
  'MOVING' \
  'UNKNOWN' \
  'SET_RETAINED_MEMORY_ENABLED' \
  'CLEAR_PROFILE_PREFERENCES' \
  'DRIVING_RESTRICTED' \
  'class HmiSnapshot' \
  'class MutationEvidence' \
  'interface MutationAuthority' \
  'snapshot(' \
  'setRetainedMemoryEnabled(' \
  'clearProfilePreferences(' \
  'isHmiProjectionOnly()' \
  'isProductionAuthorityWired()' \
  'isRepositoryMutationWired()' \
  'isRuntimeWired()' \
  'isModelContextPublished()' \
  'isContentLoggingEnabled()' \
  'isHardwareAccessed()'; do
  require_text "$MAIN" "$marker"
done

for test_name in \
  fixedSourcesAreVisibleWithoutContent \
  parkedUserCanDisableRetainedMemoryAndClearPreferences \
  movingAndUnknownBlockComplexManagementBeforeAuthority \
  malformedExpiredDeniedAndUnavailableEvidenceFailClosed \
  exactReplayIsIdempotentAndRequestConflictIsRejected \
  productionMemoryAndHardwareBoundariesRemainClosed; do
  require_text "$TEST" "$test_name"
done

for marker in \
  memory_consent_hmi_probe_complete \
  memory_consent_source_visibility_verified \
  memory_consent_disable_verified \
  memory_consent_preference_clear_verified \
  memory_consent_moving_restriction_verified \
  memory_consent_android13_arm64_verified; do
  require_text "$HMI" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
for marker in \
  memory_consent_hmi_projection_only=true \
  memory_consent_repository_mutation_wired=false \
  memory_consent_production_authority_wired=false \
  memory_consent_runtime_wired=false \
  memory_consent_model_context_published=false \
  memory_consent_content_logged=false \
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

require_text "$HMI" 'getBooleanExtra("automated", false)'
require_text "$HMI" 'Math.round(getResources().getDisplayMetrics().widthPixels * 0.38f)'
require_text "$HMI" 'Color.argb(220, 238, 242, 243)'
require_text "$DEBUG_MANIFEST" '.memory.MemoryConsentHmiActivity'
if grep -Fq 'MemoryConsentHmiActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P5-W10 Memory consent debug HMI leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq 'MemoryConsentController' \
    "$ROOT_DIR/$RUNTIME_SERVICE" "$ROOT_DIR/$GRAPH_RUNTIME"; then
  echo "P5-W10 Memory consent controller was wired into production Runtime/Graph" >&2
  exit 1
fi
if grep -Eiq 'android[.]util[.]Log|System[.]out|printStackTrace' "$ROOT_DIR/$MAIN"; then
  echo "P5-W10 main controller must not log identity or Memory state" >&2
  exit 1
fi
if grep -Eiq 'byte\[\]|ByteBuffer|InputStream|OutputStream|userText|modelText|promptText|contentText|rawPayload|Map<String, Object>' \
    "$ROOT_DIR/$MAIN"; then
  echo "P5-W10 Memory consent API accepts content or arbitrary payloads" >&2
  exit 1
fi
if grep -R -Eq \
    'androidx[.]room|CentralBrainDatabase|android[.]os[.]Binder|java[.]io|java[.]net|okhttp|http://|https://|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|ModelProvider|TokenizerProvider|NpuProvider|NPU|ioctl|sysfs|/dev/|SharedPreferences|FileOutputStream|ObjectOutputStream|android[.]security[.]keystore|java[.]security[.]KeyStore|javax[.]crypto' \
    "$ROOT_DIR/$MAIN" "$ROOT_DIR/$HMI"; then
  echo "P5-W10 references persistence, Binder, model, network, vehicle, crypto, or hardware APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P5-W10 Memory consent HMI/API"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P5-W10` Memory consent HMI/API'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P5-W10 Memory consent HMI/API trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P5-W10 Memory consent HMI/API"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P5-W10 Memory consent HMI/API architecture"
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" "P5-W10 Memory consent HMI/API detailed design"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P5-W10 Memory consent HMI/API"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P5-W10 Memory consent HMI/API Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "DEV-072 P5-W10 process-local Memory consent projection is not production Memory control"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "ISSUE-045 Memory consent authority and repository mutation publication"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P5-W10 Memory consent HMI/API"
require_text "README.md" "P5 Memory consent HMI/API"

printf '%s\n' \
  "Central Brain Android Memory consent HMI/API check passed" \
  "memory_consent_controller_defined=true" \
  "memory_consent_source_visibility_verified=true" \
  "memory_consent_disable_verified=true" \
  "memory_consent_preference_clear_verified=true" \
  "memory_consent_moving_restriction_verified=true" \
  "memory_consent_android13_arm64_verified=false" \
  "memory_consent_hmi_projection_only=true" \
  "memory_consent_repository_mutation_wired=false" \
  "memory_consent_production_authority_wired=false" \
  "memory_consent_runtime_wired=false" \
  "memory_consent_model_context_published=false" \
  "memory_consent_content_logged=false" \
  "graph_execution_enabled=false" \
  "effect_dispatch_enabled=false" \
  "vehicle_readback_accessed=false" \
  "model_invoked=false" \
  "npu_accessed=false" \
  "network_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
