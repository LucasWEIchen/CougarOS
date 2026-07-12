#!/usr/bin/env bash
set -euo pipefail

# Req IDs: XSC-001/004/005/006, FW-U-006/007, NV-F-001/012,
# NV-G-005/006/007, NV-P-002, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SNAPSHOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/memory/MemoryRuntimeReadinessSnapshot.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/memory/MemoryRuntimeReadinessSnapshotTest.java"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
DIAGNOSTIC="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainDiagnosticService.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/DiagnosticProbeActivity.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android Memory readiness file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android Memory readiness pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in "$SNAPSHOT" "$TEST" "$RUNTIME" "$DIAGNOSTIC" "$PROBE" "$INSTALLER"; do
  require_file "$path"
done

for blocker in \
  "DURABLE_ENCRYPTED_STORAGE_MISSING" \
  "KEY_LIFECYCLE_NOT_CONFIGURED" \
  "CONSENT_AUTHORITY_NOT_WIRED" \
  "CONSENT_REVOCATION_NOT_WIRED" \
  "TRUSTED_RETENTION_CLOCK_NOT_WIRED" \
  "MEMORY_REPOSITORY_NOT_IMPLEMENTED" \
  "MEMORY_RUNTIME_NOT_WIRED" \
  "MIDDLEWARE_CHAIN_NOT_WIRED"; do
  require_text "$SNAPSHOT" "$blocker"
done
for pattern in \
  "isActivationAllowed()" \
  "isBoundedMemoryLifecycleImplementationAvailable()" \
  "isMemorySchemaReady()" \
  "isDurableEncryptedStorageAvailable()" \
  "isEncryptionKeyLifecycleConfigured()" \
  "isConsentAuthorityWired()" \
  "isConsentRevocationWired()" \
  "isTrustedRetentionClockWired()" \
  "isMemoryRuntimeProductionWired()" \
  "isRawMemoryContentStored()" \
  "diagnosticDetail()"; do
  require_text "$SNAPSHOT" "$pattern"
done
require_text "$TEST" "currentSnapshotIsImmutableAndFailClosed"
require_text "$TEST" "blockersRemainOrderedAndImmutable"
require_text "$TEST" "diagnosticDetailExposesEveryPrerequisite"
require_text "$RUNTIME" "MemoryRuntimeReadinessSnapshot.current()"
require_text "$RUNTIME" "memory_runtime_readiness_snapshot_wired=true"
require_text "$RUNTIME" "memory_runtime_activation_blockers="
require_text "$DIAGNOSTIC" '"memory-runtime-readiness"'
require_text "$DIAGNOSTIC" "memoryRuntimeReadiness.diagnosticDetail()"
require_text "$PROBE" "hasBlockedMemoryRuntime("
require_text "$PROBE" "memory_runtime_readiness_diagnostic_verified="

for marker in \
  "memory_runtime_readiness_diagnostic_verified=true" \
  "memory_runtime_readiness_snapshot_wired=true" \
  "memory_runtime_readiness_log_verified=true" \
  "memory_runtime_readiness_dumpsys_verified=true" \
  "memory_runtime_activation_allowed=false" \
  "bounded_memory_lifecycle_implementation_available=true" \
  "memory_scope_count=3" \
  "memory_schema_ready=false" \
  "memory_repository_implementation_available=false" \
  "durable_encrypted_memory_storage_available=false" \
  "memory_encryption_key_lifecycle_configured=false" \
  "memory_consent_authority_wired=false" \
  "memory_consent_revocation_wired=false" \
  "trusted_memory_retention_clock_wired=false" \
  "memory_runtime_production_wired=false" \
  "memory_repository_production_wired=false" \
  "memory_middleware_chain_wired=false" \
  "raw_memory_content_stored=false" \
  "profile_memory_storage_durable=false"; do
  require_text "$INSTALLER" "$marker"
done

if grep -Eq \
    'androidx\.room|CentralBrainDatabase|MemoryEntity|MemoryDao|new BoundedMemoryLifecycle[[:space:]]*\(|createForContractTest' \
    "$ROOT_DIR/$SNAPSHOT"; then
  echo "Memory readiness snapshot must not open or construct Memory storage/runtime" >&2
  exit 1
fi
if grep -Eq \
    'import .*BoundedMemoryLifecycle|new BoundedMemoryLifecycle[[:space:]]*\(|createForContractTest' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$DIAGNOSTIC"; then
  echo "R6B2 production Services must consume Memory readiness only" >&2
  exit 1
fi
if grep -R -Eiq \
    'java\.net|okhttp|http://|https://|ioctl|sysfs|/dev/|CarPropertyManager|VehicleHal|SocketCAN|SharedMemory' \
    "$ROOT_DIR/$SNAPSHOT" "$ROOT_DIR/$PROBE"; then
  echo "R6B2 Memory readiness unexpectedly references transport or hardware" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "R6B2 Memory Runtime Readiness"
require_text "docs/CENTRAL_BRAIN_ANDROID_RUNTIME_EVOLUTION_PLAN.md" "R6B2 Memory runtime readiness"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R6B2 Memory runtime readiness trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android R6B2 Memory Runtime Readiness"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R6B2 Memory Runtime Readiness"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R6B2 Memory Readiness Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "R6B2 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "R6B2 进展"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "R6B2 Memory runtime readiness"

echo "Central Brain Android Memory runtime readiness check passed"
