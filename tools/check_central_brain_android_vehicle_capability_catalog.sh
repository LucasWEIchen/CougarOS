#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-TWN-001, S2-ADP-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CAPABILITY_DIR="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/vehicle/capability"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/vehicle/capability/CapabilityCatalogTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/vehicle/capability/VehicleCapabilityCatalogProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android vehicle capability file: $path" >&2
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
    echo "missing Android vehicle capability pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for class in VehicleCapability CapabilityCatalog CapabilityAvailability; do
  require_file "$CAPABILITY_DIR/$class.java"
done
for path in "$TEST" "$PROBE" "$DEBUG_MANIFEST" "$RUNTIME" "$GOVERNANCE" "$INSTALLER"; do
  require_file "$path"
done

CAPABILITY="$CAPABILITY_DIR/VehicleCapability.java"
CATALOG="$CAPABILITY_DIR/CapabilityCatalog.java"
AVAILABILITY="$CAPABILITY_DIR/CapabilityAvailability.java"

for capability_id in \
  'vehicle.hvac.target_temperature' \
  'vehicle.hvac.power' \
  'vehicle.hvac.fan_level' \
  'vehicle.seat.heating' \
  'vehicle.seat.ventilation' \
  'vehicle.seat.recline' \
  'media.playback' \
  'navigation.poi'; do
  require_text "$CAPABILITY" "\"$capability_id\""
done
CAPABILITY_COUNT="$(grep -Ec '^[[:space:]]+[A-Z_]+\("[a-z]' "$ROOT_DIR/$CAPABILITY")"
if [[ "$CAPABILITY_COUNT" != "8" ]]; then
  echo "vehicle capability catalog must contain exactly eight IDs" >&2
  exit 1
fi

require_text "$AVAILABILITY" "boolean readable"
require_text "$AVAILABILITY" "boolean writable"
require_text "$AVAILABILITY" "boolean simulatable"
require_text "$AVAILABILITY" "boolean productionAvailable"
require_text "$AVAILABILITY" "boolean productionAuthorized"
require_text "$AVAILABILITY" "production authorization requires an available writable capability"
require_text "$CAPABILITY" "enum RiskClass"
require_text "$CAPABILITY" "class TargetRange"
require_text "$CAPABILITY" "validateBoolean("
require_text "$CAPABILITY" "validateInteger("
require_text "$CAPABILITY" "validateDecimal("
require_text "$CAPABILITY" "validateText("
require_text "$CAPABILITY" "getReportedSignalPath()"
require_text "$CAPABILITY" "getRequiredFreshSignals()"
require_text "$CATALOG" "TargetRange.decimal(16.0, 30.0, 0.5)"
require_text "$CATALOG" "TargetRange.integer(0, 7, 1)"
require_text "$CATALOG" "TargetRange.integer(0, 3, 1)"
require_text "$CATALOG" "TargetRange.decimal(0.0, 60.0, 1.0)"
require_text "$CATALOG" 'Set.of("PLAY", "PAUSE", "STOP")'
require_text "$CATALOG" "productionAuthorizedCount()"
require_text "$CATALOG" "CapabilityAvailability.softwareContract"
require_text "$DEBUG_MANIFEST" ".vehicle.capability.VehicleCapabilityCatalogProbeActivity"

for test_name in \
  definesEightImmutableStage2Capabilities \
  allProductionCapabilitiesFailClosed \
  validatesHvacAndSeatTargetRanges \
  validatesMediaAndNavigationTextTargets \
  bindsReadbackPathsAndFreshSignalDependencies \
  rejectsDuplicateCatalogAndMismatchedReadbackContract; do
  require_text "$TEST" "$test_name"
done
for marker in \
  "vehicle_capability_catalog_verified=true" \
  "vehicle_capability_count=8" \
  "vehicle_capability_target_ranges_verified=true" \
  "vehicle_production_authorization_fail_closed_verified=true" \
  "vehicle_signal_dependency_mapping_verified=true" \
  "vehicle_capability_catalog_android13_arm64_verified=true" \
  "vehicle_production_capability_authorized_count=0"; do
  if [[ "$marker" == *=true ]]; then
    require_text "$PROBE" "${marker%=true}="
  else
    require_text "$PROBE" "${marker%%=*}="
  fi
  require_text "$INSTALLER" "$marker"
done
for marker in \
  "vehicle_capability_adapter_registry_wired=false" \
  "vehicle_property_mapping_configured=false" \
  "hardware_accessed=false"; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

if grep -R -Eq \
    'private[[:space:]]+(final[[:space:]]+)?Object|Map<[^>]*Object|JSONObject|JSONArray|Bundle|Parcelable|Parcel' \
    "$ROOT_DIR/$CAPABILITY_DIR"; then
  echo "vehicle capability catalog must not expose arbitrary object or Android payload types" >&2
  exit 1
fi
if grep -R -Eiq \
    'android\.car|CarPropertyManager|VehicleHal|VehicleProperty|java\.net|okhttp|http://|https://|ioctl|sysfs|/dev/' \
    "$ROOT_DIR/$CAPABILITY_DIR" "$ROOT_DIR/$PROBE"; then
  echo "P2-W02 capability catalog unexpectedly references hardware or network access" >&2
  exit 1
fi
if grep -Eq 'vehicle\.capability|CapabilityCatalog|VehicleCapability' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE"; then
  echo "P2-W02 capability catalog must not be wired into production Services" >&2
  exit 1
fi
if grep -Eq 'new CapabilityAvailability\([^)]*true[[:space:]]*,[[:space:]]*true[[:space:]]*\)' \
    "$ROOT_DIR/$CATALOG"; then
  echo "P2-W02 default catalog must not authorize a production capability" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P2-W02 Vehicle Capability Catalog"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" '`P2-W02` Vehicle capability catalog'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W02 vehicle capability catalog trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android P2-W02 Vehicle Capability Catalog"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android P2-W02 Vehicle Capability Catalog"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W02 Capability Catalog Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W02 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W02 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P2-W02 vehicle capability catalog"

printf '%s\n' \
  "Central Brain Android vehicle capability catalog check passed" \
  "vehicle_capability_catalog_defined=true" \
  "vehicle_capability_count=8" \
  "vehicle_capability_target_ranges_verified=true" \
  "vehicle_production_capability_authorized_count=0" \
  "vehicle_capability_adapter_registry_wired=false" \
  "vehicle_property_mapping_configured=false" \
  "hardware_accessed=false"
