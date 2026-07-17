#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-ADP-001, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEBUG_DIR="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/simulation"
MEDIA="$DEBUG_DIR/SimulatedMediaEffectAdapter.java"
NAV="$DEBUG_DIR/SimulatedNavigationEffectAdapter.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/simulation/SimulatedMediaNavigationEffectAdapterTest.java"
PROBE="$DEBUG_DIR/SimulatedMediaNavigationAdapterProbeActivity.java"
MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
INSTALLER="tools/install_central_brain_android_runtime.sh"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P2-W11 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$MEDIA" "$NAV" "$TEST" "$PROBE" "$MANIFEST" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] || { echo "P2-W11 file missing: $file" >&2; exit 1; }
done

for marker in \
  'public final class SimulatedMediaEffectAdapter extends SimulatedEffectAdapter' \
  'TARGET_SCHEMA_VERSION = 1' \
  'PlaybackCommand' \
  'MediaStateBackend' \
  'MediaStateObservation' \
  'CapabilityId.MEDIA_PLAYBACK' \
  'CapabilityCatalog.stage2Defaults()' \
  'getAvailability().isSimulatable()' \
  'getAvailability().canUseProduction()' \
  'startsExternalActivity()' \
  'usesNetwork()' \
  'SignalSource.SIMULATED'; do
  require_text "$MEDIA" "$marker"
done

for marker in \
  'public final class SimulatedNavigationEffectAdapter extends SimulatedEffectAdapter' \
  'TARGET_SCHEMA_VERSION = 1' \
  'SyntheticNavigationBackend' \
  'NavigationObservation' \
  'CapabilityId.NAVIGATION_POI' \
  'getQueryDigest()' \
  'isSynthetic()' \
  'isLocationUploaded()' \
  'isExternalActivityStarted()' \
  'startsExternalActivity()' \
  'usesNetwork()' \
  'uploadsLocation()' \
  'SignalSource.SIMULATED'; do
  require_text "$NAV" "$marker"
done

for test_name in \
  typedTargetsRoundTripAndNavigationNormalizesAtFactoryBoundary \
  mediaCommandsUpdateOnlySimulatedStateAndRemainIdempotent \
  mediaDelayTimeoutFailureAndMismatchStayObservable \
  navigationObservationIsDeterministicSyntheticAndDigestOnly \
  navigationDelayFaultMismatchAndDuplicateDoNotFabricateResults \
  mediaAndNavigationTargetsFailClosedOnRangeActionAndPayloadDrift \
  replaceableBackendsReceiveOnlyBoundedSimulatedInputs \
  unsafeBackendsAreRejectedBeforeAnyInvocation; do
  require_text "$TEST" "$test_name"
done

require_text "$MANIFEST" '.simulation.SimulatedMediaNavigationAdapterProbeActivity'
for marker in \
  simulated_media_adapter_defined=true \
  simulated_navigation_adapter_defined=true \
  simulated_media_nav_typed_target_verified=true \
  simulated_media_state_verified=true \
  simulated_navigation_synthetic_observation_verified=true \
  simulated_navigation_query_digest_only=true \
  simulated_media_nav_delay_verified=true \
  simulated_media_nav_fault_readback_verified=true \
  simulated_media_nav_idempotency_verified=true \
  simulated_media_nav_replaceable_backend_verified=true \
  simulated_media_nav_android13_arm64_verified=true; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done
for marker in \
  simulated_media_nav_debug_only=true \
  simulated_media_nav_production_registered=false \
  simulated_media_nav_runtime_wired=false \
  external_activity_started=false \
  location_uploaded=false \
  network_accessed=false \
  scenario_plan_runtime_published=false \
  scenario_graph_execution_enabled=false \
  effect_dispatch_enabled=false \
  hardware_accessed=false; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

if find "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/release" \
    -type f \( -name 'SimulatedMediaEffectAdapter.java' \
      -o -name 'SimulatedNavigationEffectAdapter.java' \) \
    -print -quit 2>/dev/null | grep -q .; then
  echo "P2-W11 Media/Navigation adapter leaked into main/release source" >&2
  exit 1
fi
if grep -R -Eiq \
    'android[.]content[.]Intent|startActivity|MediaPlayer|MediaSession|LocationManager|FusedLocation|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|java[.]net|okhttp|http://|https://|androidx[.]room|runtime[.]model|ModelProvider|InferenceResourceScheduler|ioctl|sysfs|/dev/' \
    "$ROOT_DIR/$MEDIA" "$ROOT_DIR/$NAV"; then
  echo "P2-W11 adapters unexpectedly reference external Activity, media, location, network, persistence, model, or hardware API" >&2
  exit 1
fi
if grep -Eq 'Simulated(Media|Navigation)EffectAdapter' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE"; then
  echo "P2-W11 adapters must not be registered in production Services" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P2-W11 Simulated Media/Navigation Adapters"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P2-W11` Simulated Media/Nav adapters'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P2-W11 Simulated Media/Navigation adapters trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P2-W11 Simulated Media/Navigation Adapters"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P2-W11 Simulated Media/Navigation Adapters"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P2-W11 Simulated Media/Navigation Adapter Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "P2-W11 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "P2-W11 进展"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P2-W11 Simulated Media/Nav adapters"

printf '%s\n' \
  "Central Brain Android simulated Media/Navigation adapter check passed" \
  "simulated_media_adapter_defined=true" \
  "simulated_navigation_adapter_defined=true" \
  "simulated_navigation_query_digest_only=true" \
  "simulated_media_nav_replaceable_backend_verified=true" \
  "simulated_media_nav_debug_only=true" \
  "simulated_media_nav_release_source_absent=true" \
  "simulated_media_nav_production_registered=false" \
  "simulated_media_nav_runtime_wired=false" \
  "external_activity_started=false" \
  "location_uploaded=false" \
  "network_accessed=false" \
  "effect_dispatch_enabled=false" \
  "hardware_accessed=false"
