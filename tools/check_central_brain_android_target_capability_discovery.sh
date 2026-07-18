#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-ADP-002, S2-OBS-001, XSC-001/004/005/006,
# KH-003/006/007, DEL-004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_p8_target_capability_discovery.json"
COLLECTOR="tools/collect_central_brain_android_target_capabilities.sh"
DOC="docs/CENTRAL_BRAIN_TARGET_CAPABILITY_DISCOVERY.md"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P8-W01 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$CONTRACT" "$COLLECTOR" "$DOC"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P8-W01 file missing: $file" >&2; exit 1; }
done

python3 - "$ROOT_DIR/$CONTRACT" <<'PY'
import json
import sys

path = sys.argv[1]
with open(path, encoding="utf-8") as stream:
    value = json.load(stream)
expected = [
    "vehicle.hvac.target_temperature",
    "vehicle.hvac.power",
    "vehicle.hvac.fan_level",
    "vehicle.seat.heating",
    "vehicle.seat.ventilation",
    "vehicle.seat.recline",
    "media.playback",
    "navigation.poi",
]
if value.get("schema_version") != "1.1.0":
    raise SystemExit("P8-W01 discovery schema version changed")
if value.get("status") != "external_blocked":
    raise SystemExit("P8-W01 must remain external_blocked without target evidence")
if value.get("required_capability_ids") != expected:
    raise SystemExit("P8-W01 capability order must match the fixed Stage 2 catalog")
columns = value.get("matrix_columns", [])
required_columns = {
    "capability_id", "surface_kind", "public_identifier", "service_interface",
    "area", "value_type", "access", "permission", "signer_owner",
    "interface_version", "readback_semantics", "fault_semantics",
    "evidence_reference", "discovery_status",
}
if set(columns) != required_columns or len(columns) != len(required_columns):
    raise SystemExit("P8-W01 matrix columns are incomplete or duplicated")
state = value.get("claim_state", {})
snapshot = value.get("target_public_inventory_snapshot", {})
expected_snapshot = {
    "status": "collected_redacted",
    "evidence_reference": "internal:p8-capability-20260718",
    "privacy_confirmation": "raw evidence remains repository-external; device identity and service names are not published",
    "android_api": 33,
    "automotive_feature_advertised": True,
    "package_feature_count": 69,
    "visible_binder_service_count": 274,
    "visible_car_service_match_count": 2,
    "visible_command_service_count": 265,
    "service_list_collected": True,
    "command_list_collected": True,
}
if snapshot != expected_snapshot:
    raise SystemExit("P8-W01 redacted target public inventory changed")
required_true = [
    "target_capability_discovery_contract_defined",
    "target_capability_read_only_collector_defined",
    "target_public_inventory_collected",
    "target_public_inventory_identity_redacted",
    "target_public_inventory_privacy_confirmed",
    "target_public_inventory_api33_verified",
    "target_capability_discovery_external_blocked",
]
required_false = [
    "target_capability_matrix_complete",
    "public_car_property_list_available",
    "vendor_service_contract_available",
    "permission_signature_policy_available",
    "vehicle_property_mapping_configured",
    "production_adapter_registered",
    "vendor_npu_provider_available",
    "driver_development_triggered",
    "hardware_accessed",
    "production_ready",
    "target_hardware_validated",
]
if any(state.get(key) is not True for key in required_true):
    raise SystemExit("P8-W01 completed public-inventory marker is false")
if any(state.get(key) is not False for key in required_false):
    raise SystemExit("P8-W01 unavailable or authority marker was raised")
PY

for marker in \
  'evidence directory must remain outside the repository' \
  'pm list features' \
  'service list' \
  'cmd -l' \
  'target_capability_matrix_row_count=8' \
  'public_car_property_list_available=false' \
  'vendor_service_contract_available=false' \
  'permission_signature_policy_available=false' \
  'raw_device_evidence_published=false' \
  'serial_published=false' \
  'fingerprint_published=false' \
  'private_vendor_api_probed=false' \
  'device_nodes_scanned=false' \
  'vehicle_property_mapping_configured=false' \
  'production_adapter_registered=false' \
  'driver_development_triggered=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'; do
  require_text "$COLLECTOR" "$marker"
done

if rg -n --pcre2 \
    'adb[[:space:]]+root|remount|setenforce|pm[[:space:]]+(install|uninstall)|fastboot|ioctl|sysfs|/dev/(?!null(?:[^A-Za-z0-9_.-]|$))|setprop|service[[:space:]]+call' \
    "$ROOT_DIR/$COLLECTOR"; then
  echo "P8-W01 collector contains mutation or private-hardware operations" >&2
  exit 1
fi

TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT
FAKE_ADB="$TEMP_ROOT/fake-adb"
cat >"$FAKE_ADB" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$1" == "devices" ]]; then
  printf 'List of devices attached\r\nsecret-serial\tdevice\r\n'
  exit 0
fi
[[ "$1" == "-s" && "$2" == "secret-serial" ]]
shift 2
if [[ "$1" == "get-state" ]]; then
  printf 'device\r\n'
  exit 0
fi
[[ "$1" == "shell" ]]
shift
case "$*" in
  'getprop ro.build.version.sdk') printf '33\r\n' ;;
  'pm list features')
    printf 'feature:android.hardware.type.automotive\r\nfeature:android.hardware.audio.output\r\n'
    ;;
  'service list')
    printf 'Found 2 services:\r\n0 secret.vendor.ICockpit\r\n1 car_service\r\n'
    ;;
  'cmd -l') printf 'Currently running services:\r\n  activity\r\n  car_service\r\n' ;;
  *) exit 1 ;;
esac
EOF
chmod +x "$FAKE_ADB"

OUTPUT="$($ROOT_DIR/$COLLECTOR \
  --adb "$FAKE_ADB" \
  --serial secret-serial \
  --device-alias cockpit-a13-contract \
  --evidence-dir "$TEMP_ROOT/evidence")"
for marker in \
  'android_api=33' \
  'automotive_feature_advertised=true' \
  'visible_binder_service_count=3' \
  'visible_car_service_match_count=1' \
  'target_capability_matrix_row_count=8' \
  'target_capability_discovery_external_blocked=true' \
  'raw_device_evidence_published=false' \
  'target_hardware_validated=false'; do
  grep -Fq "$marker" <<<"$OUTPUT" \
    || { echo "P8-W01 fake-device marker missing: $marker" >&2; exit 1; }
done
if grep -Eq 'secret-serial|secret[.]vendor|(^|_)fingerprint=|device_model=' <<<"$OUTPUT"; then
  echo "P8-W01 summary leaked a raw target identifier" >&2
  exit 1
fi
[[ "$(stat -c '%a' "$TEMP_ROOT/evidence/summary.properties")" == "600" ]] \
  || { echo "P8-W01 evidence permissions are not private" >&2; exit 1; }
[[ "$(awk 'END { print NR - 1 }' "$TEMP_ROOT/evidence/target-capability-matrix.tsv")" == "8" ]] \
  || { echo "P8-W01 generated matrix row count is not eight" >&2; exit 1; }

for marker in \
  'P8-W01 Target Capability Discovery Contract' \
  'target_public_inventory_collected=true' \
  'target_public_inventory_identity_redacted=true' \
  'target_public_inventory_privacy_confirmed=true' \
  'target_public_inventory_api33_verified=true' \
  'target_capability_discovery_external_blocked=true' \
  'implementation_stage=P9-W03'; do
  require_text "$DOC" "$marker"
done
require_text "README.md" "P8 Target Capability Discovery"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" "P8-W01 public inventory"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P8-W01 target capability discovery trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P8-W01 Target Capability Discovery"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P8-W01 target capability discovery architecture"
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" "P8-W01 target capability discovery detailed design"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P8-W01 Target Capability Discovery Preparation"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P8-W01 Target Capability Discovery Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "P8-W01 discovery tooling does not complete target discovery"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "ISSUE-047 P8 target capability discovery evidence is unavailable"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P8-W01 Target Capability Discovery software preparation"

printf '%s\n' \
  "Central Brain Android target capability discovery check passed" \
  "target_capability_discovery_contract_defined=true" \
  "target_capability_read_only_collector_verified=true" \
  "target_capability_matrix_template_count=8" \
  "target_capability_summary_redaction_verified=true" \
  "target_public_inventory_collected=true" \
  "target_public_inventory_identity_redacted=true" \
  "target_public_inventory_privacy_confirmed=true" \
  "target_public_inventory_api33_verified=true" \
  "target_capability_matrix_complete=false" \
  "public_car_property_list_available=false" \
  "vendor_service_contract_available=false" \
  "permission_signature_policy_available=false" \
  "target_capability_discovery_external_blocked=true" \
  "vehicle_property_mapping_configured=false" \
  "production_adapter_registered=false" \
  "vendor_npu_provider_available=false" \
  "driver_development_triggered=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false" \
  "implementation_stage=P9-W03"
