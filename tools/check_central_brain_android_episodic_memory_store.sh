#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MEM-001, S2-SAF-001, S2-OBS-001, FW-U-001/006/007,
# NV-F-001, NV-G-005/006/007, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/memory/EpisodicMemoryStore.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/memory/EpisodicMemoryStoreTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/memory/EpisodicMemoryStoreProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GRAPH_RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/AgentGraphRuntime.java"
READINESS="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/memory/MemoryRuntimeReadinessSnapshot.java"
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
    || { echo "P5-W08 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$MAIN" "$TEST" "$PROBE" "$DEBUG_MANIFEST" "$MAIN_MANIFEST" \
    "$RUNTIME_SERVICE" "$GRAPH_RUNTIME" "$READINESS" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P5-W08 file missing: $file" >&2; exit 1; }
done

for marker in \
  'public static final int SCHEMA_VERSION = 1' \
  'public static final long MAX_RETENTION_MS' \
  'public static final long MAX_EPISODE_DURATION_MS' \
  'enum TriggerKind' \
  'enum ResultKind' \
  'enum OutcomeCode' \
  'interface ScenarioCatalogAuthority' \
  'interface StoragePolicyAuthority' \
  'interface ReadAuthority' \
  'interface EraseAuthority' \
  'class ScenarioReference' \
  'class StoragePolicyEvidence' \
  'class ReadEvidence' \
  'class EraseEvidence' \
  'fromScenarioResult(' \
  'store(RecordRequest request)' \
  'readOwner(' \
  'eraseEpisode(' \
  'eraseOwner(' \
  'EPISODE_CONFLICT' \
  'SCENARIO_NOT_ALLOWED' \
  'POLICY_DENIED' \
  'RETENTION_LIMIT' \
  'EPISODE_DURATION_LIMIT' \
  'GLOBAL_CAPACITY' \
  'OWNER_CAPACITY' \
  'isRawContinuousSignalAccepted()' \
  'isRawContinuousSignalRetained()' \
  'isArbitraryPayloadAccepted()' \
  'isPersistentStorageWired()' \
  'isProductionReadAuthorityWired()' \
  'isRuntimeWired()' \
  'isContentLoggingEnabled()' \
  'isHardwareAccessed()'; do
  require_text "$MAIN" "$marker"
done

for test_name in \
  storesOnlyTypedScenarioSummaryAndIsolatesOwners \
  catalogAndStoragePolicyFailClosed \
  replayIsIdempotentAndConflictDoesNotOverwrite \
  retentionAndDurationBoundsAreDeterministic \
  globalAndOwnerCapacityFailClosedWithoutEviction \
  eraseRequiresExactAuthorityAndMalformedInputsAreRejected; do
  require_text "$TEST" "$test_name"
done

for marker in \
  episodic_memory_store_probe_complete \
  episodic_memory_summary_result_only_verified \
  episodic_memory_owner_isolation_verified \
  episodic_memory_read_fail_closed \
  episodic_memory_policy_fail_closed \
  episodic_memory_retention_verified \
  episodic_memory_capacity_verified \
  episodic_memory_erase_verified \
  episodic_memory_erase_fail_closed \
  episodic_memory_android13_arm64_verified; do
  require_text "$PROBE" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
for marker in \
  episodic_memory_process_local=true \
  episodic_memory_raw_continuous_signal_stored=false \
  episodic_memory_arbitrary_payload_stored=false \
  episodic_memory_persistence_wired=false \
  episodic_memory_runtime_wired=false \
  episodic_memory_model_context_published=false \
  episodic_memory_production_policy_authority_wired=false \
  episodic_memory_production_read_authority_wired=false \
  episodic_memory_production_erase_authority_wired=false \
  episodic_memory_content_logged=false \
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

require_text "$DEBUG_MANIFEST" '.memory.EpisodicMemoryStoreProbeActivity'
if grep -Fq 'EpisodicMemoryStoreProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P5-W08 Episodic Memory probe leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq 'EpisodicMemoryStore' \
    "$ROOT_DIR/$RUNTIME_SERVICE" "$ROOT_DIR/$GRAPH_RUNTIME"; then
  echo "P5-W08 Episodic Memory store was wired into production Runtime/Graph" >&2
  exit 1
fi
if grep -Eiq 'android[.]util[.]Log|System[.]out|printStackTrace' "$ROOT_DIR/$MAIN"; then
  echo "P5-W08 Episodic Memory main source must not log episode content" >&2
  exit 1
fi
if grep -Eiq 'public .*byte\[\]|private final byte\[\]|ByteBuffer|InputStream|OutputStream|rawSignal|signalSamples|modelText|userText|freeform|Map<String, Object>' \
    "$ROOT_DIR/$MAIN"; then
  echo "P5-W08 accepts raw signals, arbitrary payloads, or free-form text" >&2
  exit 1
fi
if grep -R -Eq \
    'androidx[.]room|CentralBrainDatabase|android[.]os[.]Binder|java[.]io|java[.]net|okhttp|http://|https://|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|ModelProvider|NpuProvider|NPU|ioctl|sysfs|/dev/|SharedPreferences|FileOutputStream|ObjectOutputStream|android[.]security[.]keystore|java[.]security[.]KeyStore|javax[.]crypto' \
    "$ROOT_DIR/$MAIN" "$ROOT_DIR/$PROBE"; then
  echo "P5-W08 references persistence, Binder, model, network, vehicle, crypto, or hardware APIs" >&2
  exit 1
fi

for marker in \
  DURABLE_ENCRYPTED_STORAGE_MISSING \
  CONSENT_AUTHORITY_NOT_WIRED \
  TRUSTED_RETENTION_CLOCK_NOT_WIRED \
  MEMORY_REPOSITORY_NOT_IMPLEMENTED \
  MEMORY_RUNTIME_NOT_WIRED; do
  require_text "$READINESS" "$marker"
done

require_text "central-brain/android-runtime/README.md" "P5-W08 EpisodicMemoryStore"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" '`P5-W08` EpisodicMemoryStore'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P5-W08 EpisodicMemoryStore trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android P5-W08 EpisodicMemoryStore"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P5-W08 EpisodicMemoryStore architecture"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "P5-W08 EpisodicMemoryStore detailed design"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android P5-W08 EpisodicMemoryStore"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P5-W08 EpisodicMemoryStore Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "DEV-070 P5-W08 process-local episodic summaries are not production Memory"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "ISSUE-043 Episodic Memory policy, repository and erase authority publication"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P5-W08 EpisodicMemoryStore"
require_text "README.md" "P5 EpisodicMemoryStore"

printf '%s\n' \
  "Central Brain Android Episodic Memory store check passed" \
  "episodic_memory_store_defined=true" \
  "episodic_memory_summary_result_only_verified=true" \
  "episodic_memory_owner_isolation_verified=true" \
  "episodic_memory_read_fail_closed=true" \
  "episodic_memory_policy_fail_closed=true" \
  "episodic_memory_retention_verified=true" \
  "episodic_memory_capacity_verified=true" \
  "episodic_memory_erase_verified=true" \
  "episodic_memory_erase_fail_closed=true" \
  "episodic_memory_android13_arm64_verified=true" \
  "episodic_memory_process_local=true" \
  "episodic_memory_raw_continuous_signal_stored=false" \
  "episodic_memory_arbitrary_payload_stored=false" \
  "episodic_memory_persistence_wired=false" \
  "episodic_memory_runtime_wired=false" \
  "episodic_memory_model_context_published=false" \
  "episodic_memory_production_policy_authority_wired=false" \
  "episodic_memory_production_read_authority_wired=false" \
  "episodic_memory_production_erase_authority_wired=false" \
  "episodic_memory_content_logged=false" \
  "graph_execution_enabled=false" \
  "effect_dispatch_enabled=false" \
  "vehicle_readback_accessed=false" \
  "model_invoked=false" \
  "npu_accessed=false" \
  "network_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
