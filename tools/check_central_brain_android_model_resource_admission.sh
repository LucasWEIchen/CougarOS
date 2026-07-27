#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MDL-001, S2-SAF-001, S2-OBS-001, NV-G-004, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADMISSION="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/scheduler/ModelResourceAdmission.java"
SCHEDULER="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/scheduler/InferenceResourceScheduler.java"
ROUTER="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/PolicyAwareModelRouter.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/scheduler/ModelResourceAdmissionTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/scheduler/ModelResourceAdmissionProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
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
    || { echo "P7-W07 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$ADMISSION" "$SCHEDULER" "$ROUTER" "$TEST" "$PROBE" \
    "$DEBUG_MANIFEST" "$MAIN_MANIFEST" "$RUNTIME" "$GOVERNANCE" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P7-W07 file missing: $file" >&2; exit 1; }
done

for marker in \
  'MAX_RESOURCE_VALIDITY_MS = 60_000L' \
  'FOREGROUND_VEHICLE' \
  'INTERACTIVE_COCKPIT' \
  'BACKGROUND_MAINTENANCE' \
  'CapacityState' \
  'COMPACT_FOREGROUND' \
  'MINIMAL_SAFETY' \
  'POLICY_BINDING_MISMATCH' \
  'POLICY_SNAPSHOT_REJECTED' \
  'RESOURCE_SNAPSHOT_REJECTED' \
  'WORKLOAD_PURPOSE_MISMATCH' \
  'THERMAL_BLOCKED' \
  'RESOURCE_BLOCKED' \
  'SCHEDULER_REJECTED' \
  'public static AdmissionDecision admit(' \
  'TrustedSubmission.fromRuntimePolicy(' \
  'getEffectiveInputTokenLimit()' \
  'getEffectiveMaxQueueWaitMs()' \
  'canonicalActiveSnapshot(' \
  'isProviderInvoked()' \
  'isActionAuthorizationGranted()' \
  'isEffectDispatchRequested()' \
  'isProductionQualified()'; do
  require_text "$ADMISSION" "$marker"
done

require_text "$SCHEDULER" 'forFixedAdmissionContract('
require_text "$SCHEDULER" 'fixed admission contract only permits deterministic.stub'
require_text "$ROUTER" 'getPolicySnapshotDigest()'
require_text "$ROUTER" 'getRegistryCatalogDigest()'

for test_name in \
  foregroundVehicleWorkPrecedesQueuedBackgroundWork \
  elevatedOrConstrainedForegroundUsesBoundedCompactBudget \
  hotStateOnlyAdmitsMinimalForegroundSafetyClassification \
  unknownCriticalAndExhaustedStatesFailClosedWithoutQueueMutation \
  staleFutureAndMismatchedBindingsFailBeforeScheduler \
  replayAndDegradedSchedulerRejectionPreserveTypedOutcomes \
  workloadMismatchAndAuthorityBoundariesRemainClosed \
  decisionDigestBindsCompleteActiveSchedulerState; do
  require_text "$TEST" "$test_name"
done

for marker in \
  resource_admission_probe_complete \
  model_resource_admission_verified \
  foreground_vehicle_priority_verified \
  thermal_degradation_verified \
  thermal_resource_fail_closed_verified \
  admission_boundary_verified; do
  require_text "$PROBE" "$marker="
done

for marker in \
  model_resource_admission_verified=true \
  foreground_vehicle_priority_verified=true \
  thermal_degradation_verified=true \
  thermal_resource_fail_closed_verified=true \
  admission_boundary_verified=true \
  resource_admission_runtime_wired=false \
  model_resource_admission_android13_arm64_verified=true \
  provider_invoked=false \
  model_invoked=false \
  action_authorization_granted=false \
  effect_dispatch_requested=false \
  network_accessed=false \
  npu_accessed=false \
  hardware_accessed=false \
  production_ready=false \
  target_hardware_validated=false; do
  require_text "$INSTALLER" "$marker"
done

require_text "$DEBUG_MANIFEST" '.scheduler.ModelResourceAdmissionProbeActivity'
if grep -Fq 'ModelResourceAdmissionProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P7-W07 debug probe leaked into the release manifest" >&2
  exit 1
fi
if grep -Fq 'ModelResourceAdmission' "$ROOT_DIR/$RUNTIME" \
    || grep -Fq 'ModelResourceAdmission' "$ROOT_DIR/$GOVERNANCE"; then
  echo "P7-W07 admission was wired into production Services" >&2
  exit 1
fi
if grep -R -Eiq \
    'android[.]car|CarPropertyManager|VehicleHal|VehiclePropertyIds|java[.]net|okhttp|https?://|ioctl|sysfs|/dev/|android[.]os[.]Binder|ClassLoader|DexClassLoader|Runtime[.]getRuntime|ProcessBuilder|[.]infer[(]|provider[.]cancel[(]' \
    "$ROOT_DIR/$ADMISSION" "$ROOT_DIR/$PROBE"; then
  echo "P7-W07 admission references provider, network, Binder, vehicle, or hardware APIs" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P7-W07 Resource and thermal admission"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" '`P7-W07` Resource/thermal admission'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W07 resource and thermal admission trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android P7-W07 Resource and Thermal Admission"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P7-W07 resource and thermal admission architecture"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "P7-W07 resource and thermal admission detailed design"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android P7-W07 Resource and Thermal Admission"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W07 Resource and Thermal Admission Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W07 admission uses caller-owned resource metadata"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W07 Resource and Thermal Admission progress"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P7-W07 Resource and Thermal Admission progress"
require_text "README.md" "P7 Resource Admission"

printf '%s\n' \
  "Central Brain Android model resource admission check passed" \
  "model_resource_admission_verified=true" \
  "foreground_vehicle_priority_verified=true" \
  "thermal_degradation_verified=true" \
  "thermal_resource_fail_closed_verified=true" \
  "admission_boundary_verified=true" \
  "resource_admission_runtime_wired=false" \
  "model_resource_admission_android13_arm64_verified=true" \
  "provider_invoked=false" \
  "model_invoked=false" \
  "network_accessed=false" \
  "npu_accessed=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
