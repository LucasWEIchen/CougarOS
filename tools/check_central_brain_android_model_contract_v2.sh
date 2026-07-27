#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MDL-001, S2-SAF-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ModelContractV2.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/model/ModelContractV2Test.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/ModelContractV2ProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
LEGACY_PROVIDER="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ModelProvider.java"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
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
    || { echo "P7-W01 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$CONTRACT" "$TEST" "$PROBE" "$DEBUG_MANIFEST" "$MAIN_MANIFEST" \
    "$LEGACY_PROVIDER" "$RUNTIME" "$GOVERNANCE" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P7-W01 file missing: $file" >&2; exit 1; }
done

for marker in \
  'SCHEMA_VERSION = 2' \
  'enum Purpose' \
  'enum PrivacyClass' \
  'class LatencyBudget' \
  'class TokenBudget' \
  'enum RequiredCapability' \
  'enum FallbackPolicy' \
  'class ModelRequest' \
  'class ModelResult' \
  'getPurpose()' \
  'getPrivacyClass()' \
  'getLatencyBudget()' \
  'getTokenBudget()' \
  'getRequiredCapability()' \
  'getFallbackPolicy()' \
  'getTraceId()' \
  'getRequestFingerprint()' \
  'isActionAuthorizationGranted()' \
  'isEffectDispatchRequested()' \
  'isRawContentAccepted()' \
  'isProviderRegistryWired()' \
  'isPolicyRouterWired()' \
  'isModelInvoked()' \
  'isNpuAccessed()' \
  'isHardwareAccessed()'; do
  require_text "$CONTRACT" "$marker"
done

for test_name in \
  requestCarriesRequiredFieldsAndStableFingerprint \
  requestFingerprintChangesWhenRoutingInputChanges \
  privacyAndFallbackCombinationFailsClosed \
  latencyTokenAndDigestBoundsFailClosed \
  resultIsBoundToRequestAndTokenBudget \
  v2ContractDoesNotWireProviderRouterOrHardware; do
  require_text "$TEST" "$test_name"
done

for marker in \
  model_contract_v2_probe_complete \
  model_contract_v2_verified \
  model_request_v2_fields_verified \
  model_result_v2_binding_verified \
  model_privacy_fallback_fail_closed; do
  require_text "$PROBE" "$marker="
done
for marker in \
  model_contract_v2_defined=true \
  model_request_v2_fields_verified=true \
  model_result_v2_binding_verified=true \
  model_privacy_fallback_fail_closed=true \
  model_raw_content_accepted=false \
  model_provider_registry_wired=false \
  model_policy_router_wired=false \
  model_invoked=false \
  npu_accessed=false \
  hardware_accessed=false \
  production_ready=false \
  target_hardware_validated=false; do
  require_text "$INSTALLER" "$marker"
done

require_text "$DEBUG_MANIFEST" '.model.ModelContractV2ProbeActivity'
if grep -Fq 'ModelContractV2ProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P7-W01 debug probe leaked into the release manifest" >&2
  exit 1
fi
if grep -Fq 'ModelContractV2' "$ROOT_DIR/$LEGACY_PROVIDER"; then
  echo "P7-W01 v2 contract unexpectedly changed the legacy ModelProvider surface" >&2
  exit 1
fi
if grep -Eiq 'ModelContractV2|ModelRequest|ModelResult' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE"; then
  echo "P7-W01 v2 contract was wired into production Services" >&2
  exit 1
fi
if grep -R -Eiq \
    'android[.]car|CarPropertyManager|VehicleHal|VehiclePropertyIds|java[.]io|java[.]net|okhttp|http://|https://|NpuProvider|ioctl|sysfs|/dev/|android[.]os[.]Binder|ClassLoader|DexClassLoader' \
    "$ROOT_DIR/$CONTRACT"; then
  echo "P7-W01 contract references transport, Binder, vehicle, NPU, hardware, or dynamic runtime APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P7-W01 ModelRequest/Result v2"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" '`P7-W01` ModelRequest/Result v2'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W01 ModelRequest/Result v2 trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android P7-W01 ModelRequest/Result v2"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P7-W01 ModelRequest/Result v2 architecture"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "P7-W01 ModelRequest/Result v2 detailed design"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android P7-W01 ModelRequest/Result v2"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W01 ModelRequest/Result v2 Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W01 ModelRequest/Result v2 is a contract, not production inference"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W01 ModelRequest/Result v2 progress"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W01 ModelRequest/Result v2 progress"
require_text "README.md" "P7 ModelRequest/Result v2"

printf '%s\n' \
  "Central Brain Android ModelRequest/Result v2 check passed" \
  "model_contract_v2_defined=true" \
  "model_request_v2_fields_verified=true" \
  "model_result_v2_binding_verified=true" \
  "model_privacy_fallback_fail_closed=true" \
  "model_raw_content_accepted=false" \
  "model_provider_registry_wired=false" \
  "model_policy_router_wired=false" \
  "model_contract_v2_android13_arm64_verified=true" \
  "model_invoked=false" \
  "npu_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
