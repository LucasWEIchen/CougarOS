#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004/005, FW-U-006/007/008, NV-F-001,
# NV-G-005/006/007, NV-P-002, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_CORE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/skills/BoundedBuiltInSkillRuntime.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/skills/BoundedBuiltInSkillRuntimeTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/skills/BuiltInSkillRuntimeProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
DIAGNOSTIC="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainDiagnosticService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android built-in Skill file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android built-in Skill pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in \
  "$RUNTIME_CORE" \
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
  'SKILL_VEHICLE_STATE_QUERY = "vehicle.state.query"' \
  'SKILL_CABIN_PRECONDITION = "cabin.precondition"' \
  'SKILL_CABIN_SCENE_NAP = "cabin.scene.nap"' \
  "enum Capability" \
  "enum RiskClass" \
  "enum SafetyState" \
  "enum RouteKind" \
  "createForContractTest(" \
  "TrustedInvocation fromRuntimePolicy(" \
  "synchronized AdmissionResult admit(" \
  "synchronized CancelOutcome cancelOwned(" \
  "isCompiledIn()" \
  "isSignerAllowlistMatched()" \
  "isArtifactDigestBound()" \
  "isCryptographicArtifactVerificationPerformed()" \
  "isDynamicLoadingAllowed()" \
  "central-brain-built-in-skill-invocation-v1"; do
  require_text "$RUNTIME_CORE" "$pattern"
done

for test_name in \
  "compiledCatalogIsImmutableAndSignerBound" \
  "invocationAdmissionIsIdempotentAndSchemaBound" \
  "capabilityAndSafetyStateFailClosed" \
  "ownerIsolationCancellationAndBoundsAreEnforced" \
  "snapshotKeepsExecutionAndHardwareDisabled"; do
  require_text "$TEST" "$test_name"
done
require_text "$DEBUG_MANIFEST" ".skills.BuiltInSkillRuntimeProbeActivity"

for marker in \
  "skill_runtime_contract_verified=true" \
  "skill_catalog_verified=true" \
  "skill_signer_allowlist_verified=true" \
  "skill_manifest_schema_verified=true" \
  "skill_invocation_idempotency_verified=true" \
  "skill_capability_policy_verified=true" \
  "skill_safety_state_verified=true" \
  "skill_owner_isolation_verified=true" \
  "skill_cancel_idempotency_verified=true" \
  "skill_record_bounds_verified=true"; do
  require_text "$PROBE" "${marker%=true}="
  require_text "$INSTALLER" "$marker"
done
for marker in \
  "skill_process_only=true" \
  "skill_manifest_signer_evidence_compile_time_only=true" \
  "skill_dynamic_loading_enabled=false" \
  "skill_cryptographic_artifact_verification_performed=false" \
  "skill_production_service_wired=false" \
  "raw_skill_input_stored=false" \
  "skill_network_access_enabled=false" \
  "service_dispatch_triggered=false" \
  "hardware_accessed=false"; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

if grep -Eq \
    'import .*BoundedBuiltInSkillRuntime|new BoundedBuiltInSkillRuntime|createForContractTest' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE" "$ROOT_DIR/$DIAGNOSTIC"; then
  echo "R6C1 built-in Skill runtime must not be wired into production Services" >&2
  exit 1
fi
if grep -R -Eiq \
    'DexClassLoader|PathClassLoader|URLClassLoader|Class\.forName|loadClass|\.apk|\.jar|System\.load|System\.loadLibrary|java\.net|okhttp|http://|https://|ioctl|sysfs|/dev/|CarPropertyManager|VehicleHal|SocketCAN|SharedMemory|androidx\.room|CentralBrainDatabase' \
    "$ROOT_DIR/$RUNTIME_CORE" "$ROOT_DIR/$PROBE"; then
  echo "R6C1 built-in Skill runtime unexpectedly references dynamic loading, storage, transport or hardware" >&2
  exit 1
fi
if grep -Eq \
    'String (rawInput|inputJson|utterance|transcript|modelOutput)([,;) ]|$)|byte\[\] input' \
    "$ROOT_DIR/$RUNTIME_CORE"; then
  echo "R6C1 built-in Skill runtime must not accept raw Skill input" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "R6C1 Signed Built-In Skill Runtime"
require_text "docs/CENTRAL_BRAIN_ANDROID_RUNTIME_EVOLUTION_PLAN.md" "R6C1 signed built-in Skill runtime"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R6C1 signed built-in Skill runtime trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android R6C1 Signed Built-In Skill Runtime"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R6C1 Signed Built-In Skill Runtime"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R6C1 Built-In Skill Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "R6C1 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "R6C1 进展"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "R6C1 signed built-in Skill runtime"

echo "Central Brain Android signed built-in Skill runtime check passed"
