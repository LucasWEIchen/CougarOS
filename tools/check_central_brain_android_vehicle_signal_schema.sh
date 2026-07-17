#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-CTX-001, S2-TWN-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCHEMA_DIR="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/vehicle/schema"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/vehicle/schema/SignalValueTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/vehicle/schema/VehicleSignalSchemaProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android vehicle signal schema file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android vehicle signal schema pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for class in \
  VehicleSignalPath \
  SignalValue \
  SignalQuality \
  SignalSource \
  SignalTimestamp; do
  require_file "$SCHEMA_DIR/$class.java"
done
for path in "$TEST" "$PROBE" "$DEBUG_MANIFEST" "$RUNTIME" "$GOVERNANCE" "$INSTALLER"; do
  require_file "$path"
done

PATH_FILE="$SCHEMA_DIR/VehicleSignalPath.java"
VALUE_FILE="$SCHEMA_DIR/SignalValue.java"
TIMESTAMP_FILE="$SCHEMA_DIR/SignalTimestamp.java"
QUALITY_FILE="$SCHEMA_DIR/SignalQuality.java"
SOURCE_FILE="$SCHEMA_DIR/SignalSource.java"

for canonical_path in \
  "Vehicle.Speed" \
  "Vehicle.Powertrain.Transmission.CurrentGear" \
  "Vehicle.Chassis.ParkingBrake.IsEngaged" \
  "Vehicle.Cabin.HVAC.IsAirConditioningActive" \
  "Vehicle.Cabin.HVAC.AmbientAirTemperature" \
  "Vehicle.Cabin.HVAC.Station.TargetTemperature" \
  "Vehicle.Cabin.HVAC.Station.FanSpeed" \
  "Vehicle.Cabin.Seat.IsOccupied" \
  "Vehicle.Cabin.Seat.IsBelted" \
  "Vehicle.Cabin.Seat.Heating" \
  "Vehicle.Cabin.Seat.Ventilation" \
  "Vehicle.Cabin.Seat.Position.Recline"; do
  require_text "$PATH_FILE" "\"$canonical_path\""
done
PATH_COUNT="$(grep -Ec '^[[:space:]]+"Vehicle\.' "$ROOT_DIR/$PATH_FILE")"
if [[ "$PATH_COUNT" != "12" ]]; then
  echo "vehicle signal path allowlist must contain exactly 12 canonical paths" >&2
  exit 1
fi

require_text "$PATH_FILE" "validateUnitAndArea"
require_text "$PATH_FILE" "fromCanonicalPath"
require_text "$PATH_FILE" "maximumAgeMs"
require_text "$VALUE_FILE" "enum ScalarType"
require_text "$VALUE_FILE" "ofBoolean("
require_text "$VALUE_FILE" "ofInteger("
require_text "$VALUE_FILE" "ofDecimal("
require_text "$VALUE_FILE" "ofText("
require_text "$VALUE_FILE" "withoutValue("
require_text "$VALUE_FILE" "validateFreshness("
require_text "$TIMESTAMP_FILE" "receivedElapsedRealtimeMs"
require_text "$TIMESTAMP_FILE" "isFresh("
require_text "$QUALITY_FILE" "UNAVAILABLE"
require_text "$QUALITY_FILE" "CONFLICT"
require_text "$SOURCE_FILE" "SIMULATED"
require_text "$SOURCE_FILE" "AAOS"
require_text "$SOURCE_FILE" "VENDOR"

for test_name in \
  acceptsAllowlistedPathsTypedScalarsUnitsAreasAndFreshness \
  rejectsUnknownPathAreaUnitAndScalarType \
  rejectsFreshnessQualityMismatchAndFutureReceiveTime \
  unavailableAndErrorStatesCannotCarryScalarValues \
  rejectsInvalidScalarTimestampAndRevisionAndContainsNoObjectField; do
  require_text "$TEST" "$test_name"
done
require_text "$DEBUG_MANIFEST" ".vehicle.schema.VehicleSignalSchemaProbeActivity"
for marker in \
  "vehicle_signal_schema_verified=true" \
  "vehicle_signal_path_allowlist_verified=true" \
  "vehicle_signal_typed_scalar_verified=true" \
  "vehicle_signal_unit_area_verified=true" \
  "vehicle_signal_freshness_quality_verified=true" \
  "vehicle_signal_schema_android13_arm64_verified=true"; do
  require_text "$PROBE" "${marker%=true}="
  require_text "$INSTALLER" "$marker"
done
for marker in \
  "vehicle_signal_provider_wired=false" \
  "vehicle_property_mapping_configured=false" \
  "hardware_accessed=false"; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

if grep -R -Eq \
    'private[[:space:]]+(final[[:space:]]+)?Object|Map<[^>]*Object|JSONObject|JSONArray|Bundle|Parcelable|Parcel' \
    "$ROOT_DIR/$SCHEMA_DIR"; then
  echo "vehicle signal schema must not expose arbitrary object or Android payload types" >&2
  exit 1
fi
if grep -R -Eiq \
    'android\.car|CarPropertyManager|VehicleHal|VehicleProperty|java\.net|okhttp|http://|https://|ioctl|sysfs|/dev/' \
    "$ROOT_DIR/$SCHEMA_DIR" "$ROOT_DIR/$PROBE"; then
  echo "P2-W01 vehicle signal schema unexpectedly references hardware or network access" >&2
  exit 1
fi
if grep -Eq 'vehicle\.schema|VehicleSignal(Path|Value)|SignalValue' \
    "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$GOVERNANCE"; then
  echo "P2-W01 vehicle signal provider must not be wired into production Services" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P2-W01 Canonical Vehicle Signal Types"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P2-W01` Canonical vehicle signal types'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P2-W01 canonical vehicle signal trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P2-W01 Canonical Vehicle Signal Schema"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P2-W01 Canonical Vehicle Signal Schema"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P2-W01 Vehicle Signal Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "P2-W01 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "P2-W01 进展"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P2-W01 canonical vehicle signal types"

printf '%s\n' \
  "Central Brain Android vehicle signal schema check passed" \
  "vehicle_signal_schema_defined=true" \
  "vehicle_signal_path_allowlist_count=12" \
  "vehicle_signal_typed_scalar_verified=true" \
  "vehicle_signal_freshness_quality_verified=true" \
  "vehicle_signal_provider_wired=false" \
  "vehicle_property_mapping_configured=false" \
  "hardware_accessed=false"
