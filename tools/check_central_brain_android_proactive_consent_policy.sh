#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SAF-001, S2-MEM-001, S2-EVT-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POLICY="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/ProactiveConsentPolicy.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/events/ProactiveConsentPolicyTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/events/ProactiveConsentPolicyProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
TRIGGER="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/events/TriggerEngine.java"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GRAPH_RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/AgentGraphRuntime.java"
EFFECT_COORDINATOR="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects/EffectCoordinator.java"
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
    || { echo "P6-W04 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$POLICY" "$TEST" "$PROBE" "$DEBUG_MANIFEST" "$MAIN_MANIFEST" \
    "$TRIGGER" "$RUNTIME_SERVICE" "$GRAPH_RUNTIME" "$EFFECT_COORDINATOR" \
    "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P6-W04 file missing: $file" >&2; exit 1; }
done

for marker in \
  'int SCHEMA_VERSION = 1' \
  'int MAX_GRANTS = 128' \
  'long MAX_GRANT_TTL_MS' \
  'enum RiskClass' \
  'HIGH' \
  'CRITICAL' \
  'class ConsentMutation' \
  'GRANT_AUTO_EXECUTE' \
  'REVOKE_AUTO_EXECUTE' \
  'class ConsentEvidence' \
  'interface ConsentAuthority' \
  'createForContractTest(' \
  'MutationResult mutate(' \
  'AdmissionDecision evaluate(' \
  'HIGH_RISK_GENERIC_GRANT_FORBIDDEN' \
  'EXPLICIT_APPROVAL_REQUIRED' \
  'isPolicyEligible()' \
  'isEffectDispatchAuthorized()' \
  'isSafetyRevalidationRequired()' \
  'isGrantPersistenceWired()' \
  'isProductionConsentAuthorityWired()' \
  'isAutoExecutionEnabled()' \
  'isRuntimeWired()' \
  'isHardwareAccessed()'; do
  require_text "$POLICY" "$marker"
done

for test_name in \
  exactGrantBindingMakesLowRiskCandidatePolicyEligibleOnly \
  highAndCriticalNeverReceiveGenericGrantOrAdmission \
  ownerScenarioCapabilityZoneDigestAndRiskMismatchFailClosed \
  ttlRevocationReplayConflictAndCapacityAreDeterministic \
  mutationRequiresParkedFreshEvidenceAndAvailableAuthority \
  productionAndExecutionBoundariesRemainClosed; do
  require_text "$TEST" "$test_name"
done

for marker in \
  proactive_consent_probe_complete \
  proactive_grant_binding_verified \
  proactive_high_critical_generic_grant_blocked \
  proactive_grant_ttl_revoke_verified \
  proactive_policy_fail_closed_verified \
  proactive_consent_android13_arm64_verified; do
  require_text "$PROBE" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
for marker in \
  proactive_policy_process_local=true \
  proactive_grant_persistence_wired=false \
  proactive_consent_authority_wired=false \
  proactive_auto_execution_enabled=false \
  proactive_runtime_wired=false \
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

require_text "$PROBE" 'getStringExtra("nonce")'
require_text "$DEBUG_MANIFEST" '.events.ProactiveConsentPolicyProbeActivity'
if grep -Fq 'ProactiveConsentPolicyProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P6-W04 proactive consent debug probe leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq 'ProactiveConsentPolicy' \
    "$ROOT_DIR/$TRIGGER" "$ROOT_DIR/$RUNTIME_SERVICE" \
    "$ROOT_DIR/$GRAPH_RUNTIME" "$ROOT_DIR/$EFFECT_COORDINATOR"; then
  echo "P6-W04 proactive consent policy was wired into Trigger/Runtime/Graph/Effect" >&2
  exit 1
fi
if grep -R -Eiq \
    'androidx[.]room|CentralBrainDatabase|android[.]os[.]Binder|java[.]io|java[.]net|okhttp|http://|https://|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|ModelProvider|NpuProvider|ioctl|sysfs|/dev/|SharedPreferences|FileOutputStream|ObjectOutputStream|Thread|ExecutorService|ClassLoader|DexClassLoader' \
    "$ROOT_DIR/$POLICY"; then
  echo "P6-W04 references persistence, Binder, transport, vehicle, model, hardware, or dynamic runtime APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P6-W04 Proactive consent/policy"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" '`P6-W04` Proactive consent/policy'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P6-W04 Proactive consent/policy trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android P6-W04 Proactive consent/policy"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P6-W04 Proactive consent/policy architecture"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "P6-W04 Proactive consent/policy detailed design"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android P6-W04 Proactive consent/policy"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P6-W04 Proactive consent/policy Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P6-W04 process-local proactive consent is not production authorization"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P6-W04 proactive consent progress"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P6-W04 Proactive consent/policy progress"
require_text "README.md" "P6 Proactive consent/policy"

printf '%s\n' \
  "Central Brain Android proactive consent policy check passed" \
  "proactive_consent_policy_defined=true" \
  "proactive_grant_binding_verified=true" \
  "proactive_high_critical_generic_grant_blocked=true" \
  "proactive_grant_ttl_revoke_verified=true" \
  "proactive_policy_fail_closed_verified=true" \
  "proactive_consent_android13_arm64_verified=true" \
  "proactive_policy_process_local=true" \
  "proactive_grant_persistence_wired=false" \
  "proactive_consent_authority_wired=false" \
  "proactive_auto_execution_enabled=false" \
  "proactive_runtime_wired=false" \
  "graph_execution_enabled=false" \
  "effect_dispatch_enabled=false" \
  "vehicle_readback_accessed=false" \
  "model_invoked=false" \
  "npu_accessed=false" \
  "network_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
