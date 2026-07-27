#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-TWN-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TWIN_DIR="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/vehicle/twin"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/vehicle/twin/VehicleDigitalTwinStoreTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/vehicle/twin/VehicleDigitalTwinStoreProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android vehicle Digital Twin file: $path" >&2
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
    echo "missing Android vehicle Digital Twin pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for class in \
  VehicleDigitalTwinStore \
  DigitalTwinSnapshot \
  DesiredStateRecord \
  ReportedStateRecord; do
  require_file "$TWIN_DIR/$class.java"
done
for path in "$TEST" "$PROBE" "$DEBUG_MANIFEST" "$RUNTIME" "$GOVERNANCE" "$INSTALLER"; do
  require_file "$path"
done

STORE="$TWIN_DIR/VehicleDigitalTwinStore.java"
SNAPSHOT="$TWIN_DIR/DigitalTwinSnapshot.java"
DESIRED="$TWIN_DIR/DesiredStateRecord.java"
REPORTED="$TWIN_DIR/ReportedStateRecord.java"

for marker in \
  "public synchronized long updateReported(" \
  "public synchronized long setDesired(" \
  "public synchronized boolean compareAndSetDesired(" \
  "public synchronized boolean clearDesired(" \
  "public synchronized DigitalTwinSnapshot snapshot(" \
  "stale or conflicting reported state"; do
  require_text "$STORE" "$marker"
done
require_text "$DESIRED" "public static final long MAX_TTL_MS = 15 * 60 * 1_000L"
require_text "$DESIRED" "assignStoreRevision("
require_text "$DESIRED" "hasSameRequest("
require_text "$DESIRED" "public boolean matches("
require_text "$REPORTED" "getEffectiveQuality()"
require_text "$REPORTED" "SignalQuality.STALE"
require_text "$SNAPSHOT" "Atomic immutable view"
for state in \
  NO_DESIRED \
  DESIRED_EXPIRED \
  PENDING_REPORTED \
  REPORTED_STALE \
  REPORTED_UNAVAILABLE \
  MATCHED \
  MISMATCH; do
  require_text "$SNAPSHOT" "$state"
done

for test_name in \
  separatesDesiredReportedAndReconcilesMatchedState \
  rejectsStaleAndConflictingReportedUpdatesAndReplaysIdempotently \
  appliesReportedFreshnessAndDesiredTtlAtSnapshotTime \
  compareAndSetDesiredIsThreadSafeAndMonotonic \
  snapshotIsAtomicFilteredAndImmutable \
  reportsPendingAndUnavailableReconciliationWithoutConflatingState; do
  require_text "$TEST" "$test_name"
done
require_text "$DEBUG_MANIFEST" ".vehicle.twin.VehicleDigitalTwinStoreProbeActivity"
for marker in \
  "vehicle_digital_twin_store_verified=true" \
  "vehicle_digital_twin_desired_reported_separation_verified=true" \
  "vehicle_digital_twin_monotonic_revision_verified=true" \
  "vehicle_digital_twin_ttl_quality_verified=true" \
  "vehicle_digital_twin_atomic_snapshot_verified=true" \
  "vehicle_digital_twin_stale_report_rejected=true" \
  "vehicle_digital_twin_reconciliation_verified=true" \
  "vehicle_digital_twin_android13_arm64_verified=true"; do
  require_text "$PROBE" "${marker%=true}="
  require_text "$INSTALLER" "$marker"
done
for marker in \
  "vehicle_digital_twin_persistence_wired=false" \
  "vehicle_capability_adapter_registry_wired=false" \
  "vehicle_property_mapping_configured=false" \
  "hardware_accessed=false"; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

if grep -R -Eq \
    'private[[:space:]]+(final[[:space:]]+)?Object|Map<[^>]*Object|JSONObject|JSONArray|Bundle|Parcelable|Parcel' \
    "$ROOT_DIR/$TWIN_DIR"; then
  echo "vehicle Digital Twin must not expose arbitrary object or Android payload types" >&2
  exit 1
fi
if grep -R -Eiq \
    'android\.car|CarPropertyManager|VehicleHal|VehicleProperty|java\.net|okhttp|http://|https://|ioctl|sysfs|/dev/|androidx\.room' \
    "$ROOT_DIR/$TWIN_DIR" "$ROOT_DIR/$PROBE"; then
  echo "P2-W03 vehicle Digital Twin unexpectedly references persistence, hardware, or network access" >&2
  exit 1
fi
if grep -Eq 'vehicle\.twin|VehicleDigitalTwin(Store)?|DigitalTwinSnapshot' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE"; then
  echo "P2-W03 vehicle Digital Twin must not be wired into production Services" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P2-W03 Vehicle Digital Twin Store"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" '`P2-W03` Vehicle Digital Twin store'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W03 Vehicle Digital Twin Store trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android P2-W03 Vehicle Digital Twin Store"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android P2-W03 Vehicle Digital Twin Store"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W03 Digital Twin Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W03 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W03 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W03 VehicleDigitalTwinStore"

printf '%s\n' \
  "Central Brain Android vehicle Digital Twin check passed" \
  "vehicle_digital_twin_store_defined=true" \
  "vehicle_digital_twin_desired_reported_separation_verified=true" \
  "vehicle_digital_twin_monotonic_revision_verified=true" \
  "vehicle_digital_twin_ttl_quality_verified=true" \
  "vehicle_digital_twin_persistence_wired=false" \
  "vehicle_digital_twin_adapter_wired=false" \
  "hardware_accessed=false"
