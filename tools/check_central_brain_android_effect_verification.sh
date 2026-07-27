#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-EFF-001, S2-TWN-001, NV-G-005/006/007, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EFFECT_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/effects"
VERIFIER="$EFFECT_ROOT/EffectVerifier.java"
RECONCILER="$EFFECT_ROOT/DigitalTwinEffectReconciler.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/effects/EffectVerificationReconciliationTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/effects/EffectVerificationProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GRAPH_RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph/AgentGraphRuntime.java"
DATABASE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/persistence/CentralBrainDatabase.java"
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
    || { echo "P3-W07 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$VERIFIER" "$RECONCILER" "$TEST" "$PROBE" "$DEBUG_MANIFEST" \
    "$MAIN_MANIFEST" "$RUNTIME_SERVICE" "$GRAPH_RUNTIME" "$DATABASE" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P3-W07 file missing: $file" >&2; exit 1; }
done

for marker in \
  'public final class EffectVerifier' \
  'VERIFY_CALLBACK_ONLY' \
  'VERIFY_REPORTED_EQUALS' \
  'VERIFY_REPORTED_TOLERANCE' \
  'VERIFY_STATE_TRANSITION' \
  'VERIFY_COMPOSITE' \
  'STATE_APPLIED' \
  'STATE_VERIFIED' \
  'VERIFICATION_DEADLINE_EXCEEDED' \
  'callback-only is forbidden for readback or elevated-risk effects' \
  'targetValueDigest does not bind the verification target' \
  'effect.verifier.observation.v1'; do
  require_text "$VERIFIER" "$marker"
done

for marker in \
  'public final class DigitalTwinEffectReconciler' \
  'MAX_RECONCILE_SEQUENCE = 64' \
  'INITIAL_RECONCILE_DELAY_MS = 250L' \
  'PRODUCTION_READBACK_UNAVAILABLE' \
  'CONFIRMED_NOT_APPLIED' \
  'ADAPTER_STATUS_REGRESSION' \
  'ALREADY_VERIFIED' \
  'resolution.adapter().queryStatus(token)' \
  'effect.reconciler.readback.v1'; do
  require_text "$RECONCILER" "$marker"
done

for test_name in \
  verifierEmitsAppliedThenVerifiedAsSeparateTransitions \
  callbackOnlyIsRestrictedToLowRiskCapabilitiesWithoutReadback \
  toleranceMatchVerifiesWhileMismatchStaysApplied \
  stateTransitionAndCompositeUseBoundedTypedFields \
  deadlineAndProductionTrustFailClosed \
  unknownStatusSchedulesReconcileWithoutRedispatch \
  matchedTwinVerifiesAndVerifiedReplaySkipsAdapterQuery \
  mismatchedTwinRemainsAppliedAndSchedulesAnotherReadback \
  notAppliedAndProductionProfileNeverDispatchOrUseDebugReadback; do
  require_text "$TEST" "$test_name"
done

for marker in \
  effect_verification_probe_complete \
  effect_verifier_defined \
  effect_verification_policies_verified \
  effect_state_separation_verified \
  effect_unknown_reconciliation_verified \
  effect_verified_redispatch_blocked \
  effect_production_readback_fail_closed \
  effect_verification_android13_arm64_verified; do
  require_text "$PROBE" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
for marker in \
  effect_verification_reconciliation_runtime_wired=false \
  effect_verification_scheduler_wired=false \
  effect_verification_persistence_wired=false \
  effect_verification_production_readback_wired=false \
  effect_verification_graph_wired=false \
  production_effect_dispatch_enabled=false \
  model_invoked=false \
  network_accessed=false \
  hardware_accessed=false; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

require_text "$DEBUG_MANIFEST" '.effects.EffectVerificationProbeActivity'
if grep -Fq 'EffectVerificationProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P3-W07 debug probe leaked into the production manifest" >&2
  exit 1
fi
if grep -Eiq 'EffectVerifier|DigitalTwinEffectReconciler' \
    "$ROOT_DIR/$RUNTIME_SERVICE" "$ROOT_DIR/$GRAPH_RUNTIME" "$ROOT_DIR/$DATABASE"; then
  echo "P3-W07 verification was wired into Graph, Service, or Room" >&2
  exit 1
fi
if grep -Fq '.apply(' "$ROOT_DIR/$RECONCILER"; then
  echo "P3-W07 reconciler invokes apply instead of status/readback only" >&2
  exit 1
fi
if grep -R -Eiq \
    'System[.](currentTimeMillis|nanoTime)|Thread[.]sleep|new Thread|java[.]util[.]Random|SecureRandom|Executors[.]|android[.]os|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|java[.]net|okhttp|http://|https://|ioctl|sysfs|/dev/' \
    "$ROOT_DIR/$VERIFIER" "$ROOT_DIR/$RECONCILER"; then
  echo "P3-W07 main contract owns a clock/thread or references network, vehicle, or hardware APIs" >&2
  exit 1
fi
if grep -Fq '.toList()' "$ROOT_DIR/$PROBE"; then
  echo "P3-W07 debug probe uses Stream.toList(), which is unavailable on API 33" >&2
  exit 1
fi

require_text "README.md" "P3 Effect verification/reconciliation"
require_text "central-brain/android-runtime/README.md" "P3-W07 Effect verification/reconciliation"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" '`P3-W07` Effect verification/reconciliation'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P3-W07 Effect verification/reconciliation trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "P3-W07 implemented Effect verification/reconciliation"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android P3-W07 Effect verification/reconciliation"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android P3-W07 Effect verification/reconciliation"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P3-W07 Effect Verification Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P3-W07 Effect verification/reconciliation"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P3-W07 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P3-W07 Effect verification/reconciliation"

printf '%s\n' \
  "Central Brain Android Effect verification/reconciliation check passed" \
  "effect_verifier_defined=true" \
  "effect_verification_policies_verified=true" \
  "effect_state_separation_verified=true" \
  "effect_unknown_reconciliation_verified=true" \
  "effect_verified_redispatch_blocked=true" \
  "effect_production_readback_fail_closed=true" \
  "effect_verification_reconciliation_runtime_wired=false" \
  "effect_verification_scheduler_wired=false" \
  "effect_verification_persistence_wired=false" \
  "effect_verification_production_readback_wired=false" \
  "production_effect_dispatch_enabled=false" \
  "hardware_accessed=false"
