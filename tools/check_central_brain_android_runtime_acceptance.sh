#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/002/004/005/006, FW-U-003/004/005/006/007/008,
# NV-F-001/011/012, NV-G-003/004/005/006/007, NV-P-002, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SNAPSHOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/acceptance/RuntimeAcceptanceSnapshot.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/acceptance/RuntimeAcceptanceSnapshotTest.java"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
DIAGNOSTIC="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainDiagnosticService.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/DiagnosticProbeActivity.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android Runtime acceptance file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android Runtime acceptance pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in "$SNAPSHOT" "$TEST" "$RUNTIME" "$DIAGNOSTIC" "$PROBE" "$INSTALLER"; do
  require_file "$path"
done

for blocker in \
  "CLIENT2_BINDER_MIGRATION_PENDING" \
  "API33_END_TO_END_ACCEPTANCE_PENDING" \
  "TARGET_SYSTEM_INTEGRATION_OWNER_UNRESOLVED" \
  "PRODUCTION_EFFECT_DELIVERY_BLOCKED" \
  "PRODUCTION_MODEL_RUNTIME_BLOCKED" \
  "PRODUCTION_EVENT_RUNTIME_BLOCKED" \
  "PRODUCTION_MEMORY_RUNTIME_BLOCKED" \
  "PRODUCTION_SKILL_GOVERNANCE_BLOCKED" \
  "TARGET_HARDWARE_NOT_VALIDATED"; do
  require_text "$SNAPSHOT" "$blocker"
done
for pattern in \
  "isCoreSoftwareBaselineReady()" \
  "isR7ApplicationIntegrationComplete()" \
  "isClient2BinderMigrationComplete()" \
  "isApi33EndToEndAcceptanceComplete()" \
  "isProductionActivationAllowed()" \
  "isTargetHardwareValidated()" \
  "isTypedBinderIntegrated()" \
  "isTrustedGovernanceIntegrated()" \
  "isDurableWorkflowFoundationReady()" \
  "getRoomSchemaVersion()" \
  "getStandardArtifactCount()" \
  "getSignatureProtectedServiceCount()" \
  "diagnosticDetail()"; do
  require_text "$SNAPSHOT" "$pattern"
done
require_text "$TEST" "coreBaselineIsReadyButR7AndProductionRemainBlocked"
require_text "$TEST" "subsystemActivationAndDispatchRemainFailClosed"
require_text "$TEST" "blockersRemainOrderedAndImmutable"
require_text "$TEST" "diagnosticDetailSeparatesSoftwareFromProductionAcceptance"
require_text "$RUNTIME" "RuntimeAcceptanceSnapshot.current()"
require_text "$RUNTIME" "runtime_acceptance_snapshot_wired=true"
require_text "$RUNTIME" "runtime_acceptance_blockers="
require_text "$DIAGNOSTIC" '"runtime-acceptance"'
require_text "$DIAGNOSTIC" "runtimeAcceptance.diagnosticDetail()"
require_text "$DIAGNOSTIC" "9)"
require_text "$PROBE" "hasRuntimeAcceptance("
require_text "$PROBE" "runtime_acceptance_diagnostic_verified="

for marker in \
  "runtime_acceptance_diagnostic_verified=true" \
  "runtime_acceptance_snapshot_wired=true" \
  "runtime_acceptance_log_verified=true" \
  "runtime_acceptance_dumpsys_verified=true" \
  "core_software_baseline_ready=true" \
  "r7_application_integration_complete=false" \
  "client2_binder_migration_complete=false" \
  "api33_end_to_end_acceptance_complete=false" \
  "production_activation_allowed=false" \
  "target_hardware_validated=false" \
  "target_system_integration_owner_resolved=false" \
  "typed_binder_integrated=true" \
  "trusted_governance_integrated=true" \
  "durable_workflow_foundation_ready=true" \
  "standard_artifact_count=3" \
  "signature_protected_service_count=3"; do
  require_text "$INSTALLER" "$marker"
done

if grep -Eq \
    'CentralBrainDatabase\.open|createForContractTest|new Bounded|new FixedGovernanceMiddlewareChain|bindService|startService|startActivity' \
    "$ROOT_DIR/$SNAPSHOT"; then
  echo "Runtime acceptance snapshot must not open storage or activate runtime paths" >&2
  exit 1
fi
if grep -R -Eiq \
    'java\.net|okhttp|http://|https://|ioctl|sysfs|/dev/|CarPropertyManager|VehicleHal|SocketCAN|SharedMemory' \
    "$ROOT_DIR/$SNAPSHOT" "$ROOT_DIR/$PROBE"; then
  echo "R7A1 Runtime acceptance unexpectedly references transport or hardware" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "R7A1 Runtime Acceptance Snapshot"
require_text "docs/CENTRAL_BRAIN_ANDROID_RUNTIME_EVOLUTION_PLAN.md" "R7A1 aggregate Runtime acceptance"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R7A1 aggregate Runtime acceptance trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android R7A1 Runtime Acceptance Snapshot"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R7A1 Runtime Acceptance Snapshot"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R7A1 Runtime Acceptance Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "R7A1 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "R7A1 进展"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "R7A1 aggregate Runtime acceptance"

echo "Central Brain Android aggregate Runtime acceptance check passed"
