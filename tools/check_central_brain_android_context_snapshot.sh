#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-CTX-001, S2-SAF-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTEXT_DIR="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/context"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/context/ContextSnapshotBuilderTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/context/ContextSnapshotBuilderProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android Context snapshot file: $path" >&2
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
    echo "missing Android Context snapshot pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for class in ContextSnapshotBuilder ContextFieldPolicy ContextSnapshot; do
  require_file "$CONTEXT_DIR/$class.java"
done
for path in "$TEST" "$PROBE" "$DEBUG_MANIFEST" "$RUNTIME" "$GOVERNANCE" "$INSTALLER"; do
  require_file "$path"
done

BUILDER="$CONTEXT_DIR/ContextSnapshotBuilder.java"
POLICY="$CONTEXT_DIR/ContextFieldPolicy.java"
SNAPSHOT="$CONTEXT_DIR/ContextSnapshot.java"

require_text "$BUILDER" "DigitalTwinSnapshot twin"
require_text "$BUILDER" "SafetyVehicleStateSnapshot runtimeState"
require_text "$BUILDER" "requiredFreshnessComplete"
require_text "$BUILDER" "productionTrusted = false"
require_text "$BUILDER" "MOVING_SPEED_THRESHOLD_KPH"
require_text "$BUILDER" "central-brain-context-v1"
require_text "$BUILDER" "MessageDigest.getInstance(\"SHA-256\")"
require_text "$POLICY" "public static ContextFieldPolicy general()"
require_text "$POLICY" "public static ContextFieldPolicy seatComfort()"
require_text "$POLICY" "public static ContextFieldPolicy seatRecline()"
require_text "$POLICY" "DEFAULT_RUNTIME_STATE_MAX_AGE_MS = 1_000"
for path in VEHICLE_SPEED CURRENT_GEAR PARKING_BRAKE_ENGAGED; do
  require_text "$POLICY" "VehicleSignalPath.$path"
done
for state in AVAILABLE MISSING STALE UNAVAILABLE ERROR CONFLICT; do
  require_text "$SNAPSHOT" "$state"
done
for marker in \
  "missingRequiredFields" \
  "staleFields" \
  "conflictFields" \
  "nonProductionTrustedFields" \
  "isRequiredFreshnessComplete()" \
  "isRestricted()" \
  "isProductionTrusted()"; do
  require_text "$SNAPSHOT" "$marker"
done

for test_name in \
  buildsDeterministicVersionedParkedContextFromOneTwinRevision \
  missingRequiredFieldFailsClosedWithExplicitFreshnessReport \
  reportsStaleAndConflictWithoutConflatingThemWithMissing \
  derivesMovingStateButLeavesActionRestrictionToSafetyPolicy \
  motionDisagreementUsesConservativeStateAndFailsClosed \
  seatReclinePolicyRequiresResolvedSeatSafetyFields \
  digestBindsPolicyMemoryAndValuesAndCollectionsAreImmutable \
  staleRuntimeStateRestrictsAndFutureRuntimeStateIsRejected \
  platformSourceAndTrustedRuntimeStillCannotClaimProductionTrust; do
  require_text "$TEST" "$test_name"
done
require_text "$DEBUG_MANIFEST" ".context.ContextSnapshotBuilderProbeActivity"
for marker in \
  "context_snapshot_builder_verified=true" \
  "context_snapshot_version_digest_verified=true" \
  "context_snapshot_required_field_policy_verified=true" \
  "context_snapshot_freshness_report_verified=true" \
  "context_snapshot_driving_state_verified=true" \
  "context_snapshot_restricted_fail_closed_verified=true" \
  "context_snapshot_source_trust_verified=true" \
  "context_snapshot_android13_arm64_verified=true"; do
  require_text "$PROBE" "${marker%=true}="
  require_text "$INSTALLER" "$marker"
done
for marker in \
  "context_snapshot_production_trusted=false" \
  "context_snapshot_production_wired=false" \
  "vehicle_signal_provider_wired=false" \
  "vehicle_capability_adapter_registry_wired=false" \
  "vehicle_property_mapping_configured=false" \
  "hardware_accessed=false"; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

if grep -R -Eq \
    'private[[:space:]]+(final[[:space:]]+)?Object|Map<[^>]*Object|JSONObject|JSONArray|Bundle|Parcelable|Parcel|Serializable' \
    "$ROOT_DIR/$CONTEXT_DIR"; then
  echo "Context snapshot must not expose arbitrary object, Android, or Java serialization payloads" >&2
  exit 1
fi
if grep -R -Eiq \
    'android\.car|CarPropertyManager|VehicleHal|VehicleProperty|java\.net|okhttp|http://|https://|ioctl|sysfs|/dev/|androidx\.room' \
    "$ROOT_DIR/$CONTEXT_DIR" "$ROOT_DIR/$PROBE"; then
  echo "P2-W04 Context snapshot unexpectedly references persistence, hardware, or network access" >&2
  exit 1
fi
if grep -Eq 'runtime\.context|ContextSnapshot(Builder)?|ContextFieldPolicy' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE"; then
  echo "P2-W04 Context snapshot must not be wired into production Services" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P2-W04 Trusted Context Snapshot"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" '`P2-W04` ContextSnapshotBuilder'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W04 trusted Context snapshot trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android P2-W04 Trusted Context Snapshot"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android P2-W04 Trusted Context Snapshot"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W04 Context Snapshot Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W04 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W04 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W04 ContextSnapshotBuilder"

printf '%s\n' \
  "Central Brain Android Context snapshot check passed" \
  "context_snapshot_defined=true" \
  "context_snapshot_version=1" \
  "context_snapshot_required_field_policy_verified=true" \
  "context_snapshot_restricted_fail_closed_verified=true" \
  "context_snapshot_production_trusted=false" \
  "context_snapshot_production_wired=false" \
  "hardware_accessed=false"
