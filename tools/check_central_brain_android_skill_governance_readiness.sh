#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/002/004/005/006, FW-U-003/006/007/008,
# NV-F-001/012, NV-G-003/005/006/007, NV-P-002, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SNAPSHOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/governance/SkillGovernanceReadinessSnapshot.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/governance/SkillGovernanceReadinessSnapshotTest.java"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
DIAGNOSTIC="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainDiagnosticService.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/DiagnosticProbeActivity.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android Skill/Governance readiness file: $path" >&2
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
    echo "missing Android Skill/Governance readiness pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in "$SNAPSHOT" "$TEST" "$RUNTIME" "$DIAGNOSTIC" "$PROBE" "$INSTALLER"; do
  require_file "$path"
done

for blocker in \
  "ARTIFACT_CRYPTO_VERIFIER_NOT_CONFIGURED" \
  "SKILL_LIFECYCLE_STORE_NOT_IMPLEMENTED" \
  "SKILL_REVOCATION_NOT_CONFIGURED" \
  "SKILL_ROLLBACK_NOT_CONFIGURED" \
  "SKILL_SANDBOX_NOT_CONFIGURED" \
  "GOVERNANCE_AUTHORITIES_NOT_WIRED" \
  "ROUTE_OWNER_REGISTRY_NOT_WIRED" \
  "MIDDLEWARE_CHAIN_NOT_WIRED" \
  "AUDIT_PERSISTENCE_NOT_WIRED" \
  "SKILL_DISPATCHER_NOT_WIRED"; do
  require_text "$SNAPSHOT" "$blocker"
done
for pattern in \
  "isActivationAllowed()" \
  "isBoundedBuiltInSkillRuntimeImplementationAvailable()" \
  "getCompiledBuiltInSkillCount()" \
  "isCompileTimeSignerEvidenceAvailable()" \
  "isCryptographicArtifactVerificationPerformed()" \
  "isFixedGovernanceMiddlewareImplementationAvailable()" \
  "getMiddlewareStageCount()" \
  "isMiddlewareOrderFixed()" \
  "isSkillLifecycleStoreImplemented()" \
  "isSkillRevocationConfigured()" \
  "isSkillRollbackConfigured()" \
  "isSkillSandboxConfigured()" \
  "areGovernanceProductionAuthoritiesWired()" \
  "isRouteOwnerRegistryWired()" \
  "isMiddlewareProductionWired()" \
  "isAuditPersistenceWired()" \
  "isSkillDispatcherProductionWired()" \
  "diagnosticDetail()"; do
  require_text "$SNAPSHOT" "$pattern"
done
require_text "$TEST" "currentSnapshotIsImmutableAndFailClosed"
require_text "$TEST" "baselineAndBlockersRemainOrdered"
require_text "$TEST" "diagnosticDetailExposesEveryPrerequisite"
require_text "$RUNTIME" "SkillGovernanceReadinessSnapshot.current()"
require_text "$RUNTIME" "skill_governance_readiness_snapshot_wired=true"
require_text "$RUNTIME" "skill_governance_activation_blockers="
require_text "$DIAGNOSTIC" '"skill-governance-readiness"'
require_text "$DIAGNOSTIC" "skillGovernanceReadiness.diagnosticDetail()"
require_text "$DIAGNOSTIC" "8)"
require_text "$PROBE" "hasBlockedSkillGovernance("
require_text "$PROBE" "skill_governance_readiness_diagnostic_verified="

for marker in \
  "skill_governance_readiness_diagnostic_verified=true" \
  "skill_governance_readiness_snapshot_wired=true" \
  "skill_governance_readiness_log_verified=true" \
  "skill_governance_readiness_dumpsys_verified=true" \
  "skill_governance_activation_allowed=false" \
  "bounded_built_in_skill_runtime_implementation_available=true" \
  "compiled_built_in_skill_count=3" \
  "compile_time_skill_signer_evidence_available=true" \
  "skill_artifact_cryptographic_verification_performed=false" \
  "fixed_governance_middleware_implementation_available=true" \
  "governance_middleware_stage_count=9" \
  "governance_middleware_order_fixed=true" \
  "skill_lifecycle_store_implemented=false" \
  "skill_revocation_configured=false" \
  "skill_rollback_configured=false" \
  "skill_sandbox_configured=false" \
  "governance_production_authorities_wired=false" \
  "skill_route_owner_registry_wired=false" \
  "skill_governance_middleware_production_wired=false" \
  "skill_governance_audit_persistence_wired=false" \
  "skill_dispatcher_production_wired=false" \
  "skill_dynamic_loading_enabled=false" \
  "raw_skill_input_stored=false" \
  "raw_skill_output_stored=false" \
  "skill_network_access_enabled=false"; do
  require_text "$INSTALLER" "$marker"
done

if grep -Eq \
    'createForContractTest|new BoundedBuiltInSkillRuntime[[:space:]]*\(|new FixedGovernanceMiddlewareChain[[:space:]]*\(|\.stageOrder\(|\.listManifests\(|\.findManifest\(|androidx\.room|CentralBrainDatabase' \
    "$ROOT_DIR/$SNAPSHOT"; then
  echo "Skill/Governance readiness must not construct runtime, chain or storage" >&2
  exit 1
fi
if grep -Eq \
    'import .*BoundedBuiltInSkillRuntime|import .*FixedGovernanceMiddlewareChain|createForContractTest' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$DIAGNOSTIC"; then
  echo "R6C3 production Services must consume readiness only" >&2
  exit 1
fi
if grep -R -Eiq \
    'java\.net|okhttp|http://|https://|DexClassLoader|PathClassLoader|System\.load|System\.loadLibrary|ioctl|sysfs|/dev/|CarPropertyManager|VehicleHal|SocketCAN|SharedMemory' \
    "$ROOT_DIR/$SNAPSHOT" "$ROOT_DIR/$PROBE"; then
  echo "R6C3 Skill/Governance readiness unexpectedly references transport or hardware" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "R6C3 Skill And Governance Readiness"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "R6C3 Skill and Governance readiness"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6C3 Skill and Governance readiness trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android R6C3 Skill And Governance Readiness"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android R6C3 Skill And Governance Readiness"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6C3 Skill/Governance Readiness Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6C3 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6C3 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R6C3 Skill/Governance readiness"

echo "Central Brain Android Skill/Governance readiness check passed"
