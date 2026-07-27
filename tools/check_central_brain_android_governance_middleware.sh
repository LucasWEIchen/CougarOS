#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/002/004/005, FW-U-003/006/007/008,
# NV-F-001, NV-G-003/005/006/007, NV-P-002, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHAIN="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/governance/FixedGovernanceMiddlewareChain.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/governance/FixedGovernanceMiddlewareChainTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/governance/GovernanceMiddlewareProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
DIAGNOSTIC="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainDiagnosticService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android governance middleware file: $path" >&2
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
    echo "missing Android governance middleware pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in \
  "$CHAIN" \
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
  "enum StageId" \
  "StageId.IDENTITY" \
  "StageId.SCHEMA" \
  "StageId.PRIVACY" \
  "StageId.POLICY" \
  "StageId.QOS" \
  "StageId.TRACE" \
  "StageId.DISPATCH_GATE" \
  "StageId.OUTPUT_GUARD" \
  "StageId.AUDIT" \
  "createForContractTest(" \
  "synchronized EvaluationResult evaluate(" \
  "ReasonCode.PREVIOUS_STAGE_REJECTED" \
  "StageStatus.RECORDED" \
  "central-brain-governance-middleware-request-v1" \
  "central-brain-governance-middleware-audit-v1"; do
  require_text "$CHAIN" "$pattern"
done

for test_name in \
  "fixedStageOrderIsImmutable" \
  "allowedContractRunsEveryStageWithoutDispatch" \
  "firstRejectionSkipsBusinessStagesAndAlwaysAudits" \
  "identitySchemaPolicyQosTraceAndDispatchFailClosed" \
  "remainingDecisionBranchesFailClosed" \
  "outputGuardAndAuditEvidenceAreDigestOnlyAndBounded" \
  "snapshotKeepsProductionNetworkAndHardwareDisabled"; do
  require_text "$TEST" "$test_name"
done
require_text "$DEBUG_MANIFEST" ".governance.GovernanceMiddlewareProbeActivity"

for marker in \
  "governance_middleware_contract_verified=true" \
  "governance_middleware_order_verified=true" \
  "governance_middleware_allow_path_verified=true" \
  "governance_middleware_first_rejection_verified=true" \
  "governance_middleware_audit_finalizer_verified=true" \
  "governance_middleware_privacy_verified=true" \
  "governance_middleware_policy_verified=true" \
  "governance_middleware_qos_verified=true" \
  "governance_middleware_output_guard_verified=true" \
  "governance_middleware_audit_bounds_verified=true"; do
  require_text "$PROBE" "${marker%=true}="
  require_text "$INSTALLER" "$marker"
done
for marker in \
  "governance_middleware_process_only=true" \
  "governance_middleware_production_wired=false" \
  "governance_dispatch_execution_enabled=false" \
  "governance_service_dispatch_triggered=false" \
  "raw_governance_input_stored=false" \
  "raw_governance_output_stored=false" \
  "governance_audit_persistence_wired=false" \
  "governance_network_access_enabled=false" \
  "hardware_accessed=false"; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

if grep -Eq \
    'import .*FixedGovernanceMiddlewareChain|new FixedGovernanceMiddlewareChain|createForContractTest' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE" "$ROOT_DIR/$DIAGNOSTIC"; then
  echo "R6C2 governance middleware must not be wired into production Services" >&2
  exit 1
fi
if grep -R -Eiq \
    'android\.|androidx\.|java\.net|okhttp|http://|https://|DexClassLoader|PathClassLoader|System\.load|System\.loadLibrary|ioctl|sysfs|/dev/|CarPropertyManager|VehicleHal|SocketCAN|SharedMemory|CentralBrainDatabase' \
    "$ROOT_DIR/$CHAIN"; then
  echo "R6C2 governance middleware unexpectedly references Android, storage, transport or hardware" >&2
  exit 1
fi
if grep -Eq \
    'String (rawInput|rawOutput|inputJson|outputJson|utterance|transcript|modelOutput)([,;) ]|$)|byte\[\] (input|output|payload)' \
    "$ROOT_DIR/$CHAIN"; then
  echo "R6C2 governance middleware must not accept raw input or output" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "R6C2 Fixed Governance Middleware Chain"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "R6C2 fixed governance middleware chain"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6C2 fixed governance middleware trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android R6C2 Fixed Governance Middleware Chain"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android R6C2 Fixed Governance Middleware Chain"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6C2 Governance Middleware Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6C2 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6C2 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6C2 fixed governance middleware chain"

echo "Central Brain Android fixed governance middleware check passed"
