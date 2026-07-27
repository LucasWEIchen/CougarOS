#!/usr/bin/env bash
set -euo pipefail

# Req IDs: XSC-001/004/005, FW-U-006/007, NV-F-001, NV-G-005/006/007,
# NV-P-002, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIFECYCLE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/memory/BoundedMemoryLifecycle.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/memory/BoundedMemoryLifecycleTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/memory/MemoryLifecycleProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
DIAGNOSTIC="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainDiagnosticService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android Memory lifecycle file: $path" >&2
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
    echo "missing Android Memory lifecycle pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in \
  "$LIFECYCLE" \
  "$TEST" \
  "$PROBE" \
  "$DEBUG_MANIFEST" \
  "$RUNTIME" \
  "$GOVERNANCE" \
  "$DIAGNOSTIC" \
  "$INSTALLER"; do
  require_file "$path"
done

for pattern in \
  "enum Scope" \
  "EPHEMERAL" \
  "SESSION" \
  "PROFILE" \
  "enum Purpose" \
  "MAX_EPHEMERAL_TTL_MS" \
  "MAX_SESSION_TTL_MS" \
  "MAX_PROFILE_TTL_MS" \
  "TrustedConsentEvidence grantedByGovernance(" \
  "TrustedExportAuthorization grantedByGovernance(" \
  "TrustedWrite fromRuntimePolicy(" \
  "synchronized WriteResult write(" \
  "synchronized List<RedactedRecord> queryOwned(" \
  "synchronized DeleteOutcome deleteOwned(" \
  "synchronized ExportResult exportOwned(" \
  "requestFingerprint(TrustedWrite request)" \
  "request.consent.ownerFingerprint" \
  "request.consent.expiresAtElapsedRealtimeMs" \
  "contentDigestExposed = false" \
  "entry.contentDigest = \"\"" \
  "isPersistentStorageWired()" \
  "isProductionServiceWired()" \
  "isRawContentStored()"; do
  require_text "$LIFECYCLE" "$pattern"
done

for test_name in \
  "scopeTtlAndProfileConsentFailClosed" \
  "ownerClientReplayConflictAndCapacityAreBounded" \
  "queryIsOwnerScopedAndRedacted" \
  "expiryDeleteAndTerminalRetentionClearContentReference" \
  "exportRequiresScopeAndTrustedAuthorization"; do
  require_text "$TEST" "$test_name"
done

require_text "$DEBUG_MANIFEST" ".memory.MemoryLifecycleProbeActivity"
for marker in \
  "memory_lifecycle_contract_verified=true" \
  "memory_scope_policy_verified=true" \
  "memory_profile_consent_verified=true" \
  "memory_write_idempotency_verified=true" \
  "memory_owner_isolation_verified=true" \
  "memory_query_redaction_verified=true" \
  "memory_ttl_expiry_verified=true" \
  "memory_delete_idempotency_verified=true" \
  "memory_export_authorization_verified=true" \
  "memory_record_bounds_verified=true"; do
  require_text "$PROBE" "${marker%=true}="
  require_text "$INSTALLER" "$marker"
done
for marker in \
  "memory_process_only=true" \
  "memory_persistence_wired=false" \
  "memory_production_service_wired=false" \
  "raw_memory_content_stored=false" \
  "memory_profile_storage_durable=false" \
  "memory_consent_revocation_wired=false" \
  "memory_encryption_key_configured=false" \
  "service_dispatch_triggered=false" \
  "hardware_accessed=false"; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

if grep -Eq \
    'import .*BoundedMemoryLifecycle|new BoundedMemoryLifecycle|createForContractTest' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE" "$ROOT_DIR/$DIAGNOSTIC"; then
  echo "R6B1 Memory lifecycle must not be wired into production Services" >&2
  exit 1
fi
if grep -Eq \
    'androidx\.room|CentralBrainDatabase|MemoryEntity|MemoryDao|android\.os\.Binder|android\.os\.IBinder' \
    "$ROOT_DIR/$LIFECYCLE" "$ROOT_DIR/$PROBE"; then
  echo "R6B1 Memory lifecycle must remain process-local and outside Binder/Room" >&2
  exit 1
fi
if grep -R -Eiq \
    'java\.net|okhttp|http://|https://|ioctl|sysfs|/dev/|CarPropertyManager|VehicleHal|SocketCAN|SharedMemory' \
    "$ROOT_DIR/$LIFECYCLE" "$ROOT_DIR/$PROBE"; then
  echo "R6B1 Memory lifecycle unexpectedly references transport or hardware" >&2
  exit 1
fi
if grep -Eq \
    'String (rawContent|content|utterance|transcript|modelOutput)([,;) ]|$)' \
    "$ROOT_DIR/$LIFECYCLE"; then
  echo "R6B1 Memory lifecycle must not accept or store raw Memory content" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "R6B1 Bounded Memory Lifecycle"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "R6B1 bounded Memory lifecycle"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6B1 bounded Memory lifecycle trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android R6B1 Bounded Memory Lifecycle"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android R6B1 Bounded Memory Lifecycle"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6B1 Memory Lifecycle Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6B1 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6B1 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6B1 bounded Memory lifecycle"

echo "Central Brain Android bounded Memory lifecycle check passed"
