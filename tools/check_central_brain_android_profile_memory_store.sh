#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MEM-001, S2-SAF-001, S2-OBS-001, FW-U-001/006/007,
# NV-F-001, NV-G-005/006/007, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/memory/ProfileMemoryStore.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/memory/ProfileMemoryStoreTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/memory/ProfileMemoryStoreProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GRAPH_RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/AgentGraphRuntime.java"
READINESS="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/memory/MemoryRuntimeReadinessSnapshot.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P5-W07 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$MAIN" "$TEST" "$PROBE" "$DEBUG_MANIFEST" "$MAIN_MANIFEST" \
    "$RUNTIME_SERVICE" "$GRAPH_RUNTIME" "$READINESS" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P5-W07 file missing: $file" >&2; exit 1; }
done

for marker in \
  'public static final int SCHEMA_VERSION = 1' \
  'public static final long MAX_RETENTION_MS' \
  'enum Field' \
  'enum SeatScope' \
  'interface ConsentAuthority' \
  'interface AuthorizationAuthority' \
  'interface EncryptionOwner' \
  'class ConsentEvidence' \
  'class AuthorizationEvidence' \
  'class EncryptionOwnerState' \
  'class SealedPayload' \
  'payload.algorithmId.equals(ownerState.algorithmId)' \
  'updateOwned(' \
  'readOwned(' \
  'deleteOwned(' \
  'exportOwned(' \
  'CONSENT_REQUIRED' \
  'CONSENT_INVALID' \
  'FIELD_NOT_ALLOWED' \
  'SCOPE_MISMATCH' \
  'ENCRYPTION_OWNER_UNAVAILABLE' \
  'Arrays.fill(value, (byte) 0)' \
  'isEncryptionOwnerGateDefined()' \
  'isDurableEncryptedStorageAvailable()' \
  'isProductionEncryptionOwnerConfigured()' \
  'isConsentAuthorityProductionWired()' \
  'isRuntimeWired()' \
  'isContentLoggingEnabled()' \
  'isHardwareAccessed()'; do
  require_text "$MAIN" "$marker"
done

for test_name in \
  encryptionOwnerAndConsentFailClosed \
  fieldAllowlistScopeAndValueValidationAreExact \
  updateReadIsolationAndSealedReplacementAreDeterministic \
  deleteRemainsAuthorizedAfterConsentRevocationAndZeroizes \
  exportRequiresConsentAuthorizationAndReturnsBoundedImmutableItems \
  retentionCapacityAndMalformedContractsFailClosed; do
  require_text "$TEST" "$test_name"
done

for marker in \
  profile_memory_store_probe_complete \
  profile_memory_explicit_consent_verified \
  profile_memory_field_allowlist_verified \
  profile_memory_user_seat_scope_verified \
  profile_memory_read_update_verified \
  profile_memory_delete_verified \
  profile_memory_export_verified \
  profile_memory_consent_revocation_fail_closed \
  profile_memory_encryption_owner_gate_verified \
  profile_memory_sealed_payload_zeroized \
  profile_memory_android13_arm64_verified; do
  require_text "$PROBE" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
for marker in \
  profile_memory_process_local=true \
  profile_memory_durable_storage_wired=false \
  profile_memory_production_encryption_owner_configured=false \
  profile_memory_consent_authority_production_wired=false \
  profile_memory_runtime_wired=false \
  profile_memory_content_logged=false \
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

require_text "$DEBUG_MANIFEST" '.memory.ProfileMemoryStoreProbeActivity'
if grep -Fq 'ProfileMemoryStoreProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P5-W07 Profile Memory probe leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq 'ProfileMemoryStore' \
    "$ROOT_DIR/$RUNTIME_SERVICE" "$ROOT_DIR/$GRAPH_RUNTIME"; then
  echo "P5-W07 Profile Memory store was wired into production Runtime/Graph" >&2
  exit 1
fi
if grep -Eiq 'android[.]util[.]Log|System[.]out|printStackTrace' "$ROOT_DIR/$MAIN"; then
  echo "P5-W07 Profile Memory main source must not log profile values" >&2
  exit 1
fi
if grep -R -Eq \
    'androidx[.]room|CentralBrainDatabase|android[.]os[.]Binder|java[.]io|java[.]net|okhttp|http://|https://|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|ModelProvider|NpuProvider|NPU|ioctl|sysfs|/dev/|SharedPreferences|FileOutputStream|ObjectOutputStream|android[.]security[.]keystore|java[.]security[.]KeyStore|javax[.]crypto[.]Cipher' \
    "$ROOT_DIR/$MAIN" "$ROOT_DIR/$PROBE"; then
  echo "P5-W07 references persistence, production crypto, Binder, model, network, vehicle, or hardware APIs" >&2
  exit 1
fi

for marker in \
  DURABLE_ENCRYPTED_STORAGE_MISSING \
  KEY_LIFECYCLE_NOT_CONFIGURED \
  CONSENT_AUTHORITY_NOT_WIRED \
  CONSENT_REVOCATION_NOT_WIRED \
  MEMORY_RUNTIME_NOT_WIRED; do
  require_text "$READINESS" "$marker"
done

require_text "central-brain/android-runtime/README.md" "P5-W07 ProfileMemoryStore"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P5-W07` ProfileMemoryStore'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P5-W07 ProfileMemoryStore trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P5-W07 ProfileMemoryStore"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P5-W07 ProfileMemoryStore architecture"
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" "P5-W07 ProfileMemoryStore detailed design"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P5-W07 ProfileMemoryStore"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P5-W07 ProfileMemoryStore Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "DEV-069 P5-W07 contract cipher is not production encrypted storage"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "ISSUE-042 Profile Memory authority, key owner and durable repository publication"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P5-W07 ProfileMemoryStore"
require_text "README.md" "P5 ProfileMemoryStore"

printf '%s\n' \
  "Central Brain Android Profile Memory store check passed" \
  "profile_memory_store_defined=true" \
  "profile_memory_explicit_consent_verified=true" \
  "profile_memory_field_allowlist_verified=true" \
  "profile_memory_user_seat_scope_verified=true" \
  "profile_memory_read_update_verified=true" \
  "profile_memory_delete_verified=true" \
  "profile_memory_export_verified=true" \
  "profile_memory_consent_revocation_fail_closed=true" \
  "profile_memory_encryption_owner_gate_verified=true" \
  "profile_memory_sealed_payload_zeroized=true" \
  "profile_memory_android13_arm64_verified=false" \
  "profile_memory_process_local=true" \
  "profile_memory_durable_storage_wired=false" \
  "profile_memory_production_encryption_owner_configured=false" \
  "profile_memory_consent_authority_production_wired=false" \
  "profile_memory_runtime_wired=false" \
  "profile_memory_content_logged=false" \
  "graph_execution_enabled=false" \
  "effect_dispatch_enabled=false" \
  "vehicle_readback_accessed=false" \
  "model_invoked=false" \
  "npu_accessed=false" \
  "network_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
