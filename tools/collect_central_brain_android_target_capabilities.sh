#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-ADP-002, S2-OBS-001, XSC-001/004/005/006,
# KH-003/006/007, DEL-004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT_DIR="$(realpath -m "$ROOT_DIR")"
ADB_BIN="${ADB:-}"
SERIAL="${ANDROID_SERIAL:-}"
DEVICE_ALIAS=""
EVIDENCE_DIR=""

usage() {
  cat <<'EOF'
Usage: collect_central_brain_android_target_capabilities.sh [options]

Read-only P8 target capability evidence collection. Raw evidence remains in a
private directory outside the repository. The summary contains only a
non-secret alias, counts, booleans and SHA-256 evidence references.

Options:
  --adb PATH             adb executable; defaults to ADB or Android SDK adb.
  --serial SERIAL        select one adb device without publishing its serial.
  --device-alias ALIAS   required non-secret alias, for example cockpit-a13-lab.
  --evidence-dir PATH    required output directory outside this repository.
  -h, --help             show this help.
EOF
}

while (($# > 0)); do
  case "$1" in
    --adb)
      [[ $# -ge 2 ]] || { echo "--adb requires a value" >&2; exit 2; }
      ADB_BIN="$2"
      shift 2
      ;;
    --serial)
      [[ $# -ge 2 ]] || { echo "--serial requires a value" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    --device-alias)
      [[ $# -ge 2 ]] || { echo "--device-alias requires a value" >&2; exit 2; }
      DEVICE_ALIAS="$2"
      shift 2
      ;;
    --evidence-dir)
      [[ $# -ge 2 ]] || { echo "--evidence-dir requires a value" >&2; exit 2; }
      EVIDENCE_DIR="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unknown option" >&2
      usage >&2
      exit 2
      ;;
  esac
done

[[ "$DEVICE_ALIAS" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]] \
  || { echo "a bounded non-secret --device-alias is required" >&2; exit 2; }
[[ -n "$EVIDENCE_DIR" ]] \
  || { echo "--evidence-dir is required" >&2; exit 2; }
EVIDENCE_DIR="$(realpath -m "$EVIDENCE_DIR")"
case "$EVIDENCE_DIR/" in
  "$ROOT_DIR/"*)
    echo "evidence directory must remain outside the repository" >&2
    exit 2
    ;;
esac

if [[ -z "$ADB_BIN" ]]; then
  ANDROID_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  [[ -n "$ANDROID_SDK" ]] \
    || { echo "--adb or ANDROID_HOME/ANDROID_SDK_ROOT is required" >&2; exit 2; }
  ADB_BIN="$ANDROID_SDK/platform-tools/adb"
fi
[[ -x "$ADB_BIN" ]] || { echo "adb executable is unavailable" >&2; exit 1; }

if [[ -z "$SERIAL" ]]; then
  mapfile -t DEVICES < <(
    "$ADB_BIN" devices | tr -d '\r' \
      | awk 'NR > 1 && $2 == "device" { print $1 }'
  )
  if [[ ${#DEVICES[@]} -ne 1 ]]; then
    printf 'online_device_count=%d\n' "${#DEVICES[@]}" >&2
    echo "expected exactly one online device; use --serial" >&2
    exit 1
  fi
  SERIAL="${DEVICES[0]}"
fi
ADB_DEVICE=("$ADB_BIN" -s "$SERIAL")
[[ "$("${ADB_DEVICE[@]}" get-state 2>/dev/null | tr -d '\r\n')" == "device" ]] \
  || { echo "selected adb device is not online" >&2; exit 1; }

umask 077
mkdir -p "$EVIDENCE_DIR"
chmod 700 "$EVIDENCE_DIR"
TEMP_DIR="$(mktemp -d "$EVIDENCE_DIR/.collect.XXXXXX")"
trap 'rm -rf "$TEMP_DIR"' EXIT

capture_required() {
  local output="$1"
  shift
  "${ADB_DEVICE[@]}" shell "$@" 2>/dev/null | tr -d '\r' >"$TEMP_DIR/$output"
  [[ -s "$TEMP_DIR/$output" ]] \
    || { echo "required read-only evidence command returned no data" >&2; exit 1; }
}

capture_optional() {
  local output="$1"
  shift
  if "${ADB_DEVICE[@]}" shell "$@" 2>/dev/null | tr -d '\r' >"$TEMP_DIR/$output" \
      && [[ -s "$TEMP_DIR/$output" ]]; then
    return 0
  fi
  rm -f "$TEMP_DIR/$output"
  return 1
}

capture_required android_api.txt getprop ro.build.version.sdk
capture_required package_features.txt pm list features
SERVICE_LIST_COLLECTED=false
COMMAND_LIST_COLLECTED=false
if capture_optional binder_services.txt service list; then
  SERVICE_LIST_COLLECTED=true
fi
if capture_optional command_services.txt cmd -l; then
  COMMAND_LIST_COLLECTED=true
fi

ANDROID_API="$(tr -d '\n' <"$TEMP_DIR/android_api.txt")"
[[ "$ANDROID_API" =~ ^[0-9]+$ && "$ANDROID_API" -ge 33 ]] \
  || { echo "Android API 33 or newer is required" >&2; exit 1; }
FEATURE_COUNT="$(awk 'NF { count++ } END { print count + 0 }' "$TEMP_DIR/package_features.txt")"
if grep -Fq 'feature:android.hardware.type.automotive' "$TEMP_DIR/package_features.txt"; then
  AUTOMOTIVE_FEATURE=true
else
  AUTOMOTIVE_FEATURE=false
fi

SERVICE_COUNT=0
CAR_SERVICE_MATCH_COUNT=0
SERVICE_DIGEST=UNAVAILABLE
if [[ "$SERVICE_LIST_COLLECTED" == true ]]; then
  SERVICE_COUNT="$(awk 'NF { count++ } END { print count + 0 }' "$TEMP_DIR/binder_services.txt")"
  CAR_SERVICE_MATCH_COUNT="$(
    grep -Eic 'car_service|android[.]car' "$TEMP_DIR/binder_services.txt" || true
  )"
  SERVICE_DIGEST="$(sha256sum "$TEMP_DIR/binder_services.txt" | awk '{ print $1 }')"
fi
COMMAND_COUNT=0
COMMAND_DIGEST=UNAVAILABLE
if [[ "$COMMAND_LIST_COLLECTED" == true ]]; then
  COMMAND_COUNT="$(awk 'NF { count++ } END { print count + 0 }' "$TEMP_DIR/command_services.txt")"
  COMMAND_DIGEST="$(sha256sum "$TEMP_DIR/command_services.txt" | awk '{ print $1 }')"
fi
FEATURE_DIGEST="$(sha256sum "$TEMP_DIR/package_features.txt" | awk '{ print $1 }')"

cat >"$TEMP_DIR/target-capability-matrix.tsv" <<'EOF'
capability_id	surface_kind	public_identifier	service_interface	area	value_type	access	permission	signer_owner	interface_version	readback_semantics	fault_semantics	evidence_reference	discovery_status
vehicle.hvac.target_temperature	UNKNOWN	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	EXTERNAL_BLOCKED
vehicle.hvac.power	UNKNOWN	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	EXTERNAL_BLOCKED
vehicle.hvac.fan_level	UNKNOWN	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	EXTERNAL_BLOCKED
vehicle.seat.heating	UNKNOWN	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	EXTERNAL_BLOCKED
vehicle.seat.ventilation	UNKNOWN	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	EXTERNAL_BLOCKED
vehicle.seat.recline	UNKNOWN	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	EXTERNAL_BLOCKED
media.playback	UNKNOWN	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	EXTERNAL_BLOCKED
navigation.poi	UNKNOWN	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	UNAVAILABLE	EXTERNAL_BLOCKED
EOF

cat >"$TEMP_DIR/summary.properties" <<EOF
target_capability_collection_schema_version=1
device_alias=$DEVICE_ALIAS
android_api=$ANDROID_API
automotive_feature_advertised=$AUTOMOTIVE_FEATURE
package_feature_count=$FEATURE_COUNT
visible_binder_service_count=$SERVICE_COUNT
visible_car_service_match_count=$CAR_SERVICE_MATCH_COUNT
visible_command_service_count=$COMMAND_COUNT
service_list_collected=$SERVICE_LIST_COLLECTED
command_list_collected=$COMMAND_LIST_COLLECTED
package_feature_evidence_sha256=$FEATURE_DIGEST
binder_service_evidence_sha256=$SERVICE_DIGEST
command_service_evidence_sha256=$COMMAND_DIGEST
target_capability_matrix_row_count=8
public_car_property_list_available=false
vendor_service_contract_available=false
permission_signature_policy_available=false
target_capability_matrix_complete=false
target_capability_discovery_external_blocked=true
preflight_mutation_performed=false
raw_device_evidence_published=false
serial_published=false
fingerprint_published=false
device_model_published=false
private_vendor_api_probed=false
device_nodes_scanned=false
vehicle_property_mapping_configured=false
production_adapter_registered=false
vendor_npu_provider_available=false
driver_development_triggered=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
EOF

for file in android_api.txt package_features.txt binder_services.txt command_services.txt \
    target-capability-matrix.tsv summary.properties; do
  if [[ -f "$TEMP_DIR/$file" ]]; then
    mv "$TEMP_DIR/$file" "$EVIDENCE_DIR/$file"
    chmod 600 "$EVIDENCE_DIR/$file"
  fi
done

cat "$EVIDENCE_DIR/summary.properties"
