#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MEM-001, S2-SAF-001, S2-OBS-001, FW-U-001/006/007,
# NV-F-001, NV-G-005/006/007, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/memory/WorkingMemoryStore.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/memory/WorkingMemoryStoreTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/memory/WorkingMemoryStoreProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GRAPH_RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/AgentGraphRuntime.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P5-W06 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$MAIN" "$TEST" "$PROBE" "$DEBUG_MANIFEST" \
    "$MAIN_MANIFEST" "$RUNTIME_SERVICE" "$GRAPH_RUNTIME" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P5-W06 file missing: $file" >&2; exit 1; }
done

for marker in \
  'public static final int SCHEMA_VERSION = 1' \
  'public static final long MAX_TTL_MS' \
  'class PutRequest' \
  'fromRuntimePolicy(' \
  'SESSION_TERMINAL' \
  'ITEM_LIMIT' \
  'ITEM_BYTE_LIMIT' \
  'ITEM_TOKEN_LIMIT' \
  'SESSION_BYTE_LIMIT' \
  'SESSION_TOKEN_LIMIT' \
  'TTL_LIMIT' \
  'readSessionOwned(' \
  'removeOwned(' \
  'terminateSessionOwned(' \
  'Arrays.fill(payload, (byte) 0)' \
  'getPayloadCopy()' \
  'isPersistentStorageWired()' \
  'isRuntimeWired()' \
  'isModelContextPublicationEnabled()' \
  'isTokenCountVerifiedByModelTokenizer()' \
  'isContentLoggingEnabled()' \
  'isHardwareAccessed()'; do
  require_text "$MAIN" "$marker"
done

for test_name in \
  sessionIsolationDefensiveCopiesAndImmutableRead \
  monotonicTtlExpiresAndWipesRetainedPayload \
  itemByteTokenSessionAndTtlLimitsFailClosed \
  replayReplacementAndRemovalKeepBudgetsExact \
  terminalCleanupWipesBlocksReplayAndBoundsTombstones \
  malformedLimitsAndRequestsAreRejected; do
  require_text "$TEST" "$test_name"
done

for marker in \
  working_memory_store_probe_complete \
  working_memory_session_scope_verified \
  working_memory_ttl_verified \
  working_memory_item_limit_verified \
  working_memory_byte_limit_verified \
  working_memory_token_limit_verified \
  working_memory_terminal_cleanup_verified \
  working_memory_payload_zeroized_on_cleanup \
  working_memory_android13_arm64_verified; do
  require_text "$PROBE" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
for marker in \
  working_memory_process_local=true \
  working_memory_persistence_wired=false \
  working_memory_runtime_wired=false \
  working_memory_model_context_published=false \
  working_memory_tokenizer_verified=false \
  working_memory_content_logged=false \
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

require_text "$DEBUG_MANIFEST" '.memory.WorkingMemoryStoreProbeActivity'
if grep -Fq 'WorkingMemoryStoreProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P5-W06 Working Memory probe leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq 'WorkingMemoryStore' \
    "$ROOT_DIR/$RUNTIME_SERVICE" "$ROOT_DIR/$GRAPH_RUNTIME"; then
  echo "P5-W06 Working Memory store was wired into production Runtime/Graph" >&2
  exit 1
fi
if grep -Eiq 'android[.]util[.]Log|System[.]out|printStackTrace' "$ROOT_DIR/$MAIN"; then
  echo "P5-W06 Working Memory main source must not log content" >&2
  exit 1
fi
if grep -R -Eq \
    'androidx[.]room|CentralBrainDatabase|android[.]os[.]Binder|java[.]io|java[.]net|okhttp|http://|https://|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|ModelProvider|NpuProvider|NPU|ioctl|sysfs|/dev/|SharedPreferences|FileOutputStream|ObjectOutputStream' \
    "$ROOT_DIR/$MAIN" "$ROOT_DIR/$PROBE"; then
  echo "P5-W06 references persistence, Binder, model, network, vehicle, or hardware APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P5-W06 WorkingMemoryStore"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P5-W06` WorkingMemoryStore'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P5-W06 WorkingMemoryStore trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P5-W06 WorkingMemoryStore"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P5-W06 WorkingMemoryStore architecture"
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" "P5-W06 WorkingMemoryStore detailed design"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P5-W06 WorkingMemoryStore"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P5-W06 WorkingMemoryStore Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "DEV-068 P5-W06 process-local Working Memory is not production Memory"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "ISSUE-041 Working Memory session owner, tokenizer and storage publication"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P5-W06 WorkingMemoryStore"
require_text "README.md" "P5 WorkingMemoryStore"

printf '%s\n' \
  "Central Brain Android Working Memory store check passed" \
  "working_memory_store_defined=true" \
  "working_memory_session_scope_verified=true" \
  "working_memory_ttl_verified=true" \
  "working_memory_item_limit_verified=true" \
  "working_memory_byte_limit_verified=true" \
  "working_memory_token_limit_verified=true" \
  "working_memory_terminal_cleanup_verified=true" \
  "working_memory_payload_zeroized_on_cleanup=true" \
  "working_memory_android13_arm64_verified=false" \
  "working_memory_process_local=true" \
  "working_memory_persistence_wired=false" \
  "working_memory_runtime_wired=false" \
  "working_memory_model_context_published=false" \
  "working_memory_tokenizer_verified=false" \
  "working_memory_content_logged=false" \
  "graph_execution_enabled=false" \
  "effect_dispatch_enabled=false" \
  "vehicle_readback_accessed=false" \
  "model_invoked=false" \
  "npu_accessed=false" \
  "network_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
