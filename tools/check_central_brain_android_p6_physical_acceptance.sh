#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-EVT-001, S2-SCN-001, S2-CTX-001, S2-UX-002,
# S2-TRG-002, S2-SAF-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_p6_physical_acceptance.json"
INSTALLER="$ROOT_DIR/tools/install_central_brain_android_runtime.sh"
REQUIREMENTS="$ROOT_DIR/docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md"
DEVIATIONS="$ROOT_DIR/docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md"
ISSUES="$ROOT_DIR/docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md"
DELIVERY="$ROOT_DIR/docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md"
DRIVER="$ROOT_DIR/docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md"
ROADMAP="$ROOT_DIR/docs/CENTRAL_BRAIN_ROADMAP.md"
REPORT="$ROOT_DIR/docs/CENTRAL_BRAIN_ANDROID13_PHYSICAL_TARGET_TEST_REPORT.md"
ARCHITECTURE="$ROOT_DIR/docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md"
INTERFACES="$ROOT_DIR/docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md"
DESIGN="$ROOT_DIR/docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md"
BACKLOG="$ROOT_DIR/docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md"
README="$ROOT_DIR/README.md"

for path in "$CONTRACT" "$INSTALLER" "$REQUIREMENTS" "$DEVIATIONS" "$ISSUES" "$DELIVERY" "$DRIVER" \
    "$ROADMAP" "$REPORT" "$ARCHITECTURE" "$INTERFACES" "$DESIGN" "$BACKLOG" "$README"; do
  [[ -f "$path" ]] || { echo "missing P6 physical acceptance file: $path" >&2; exit 1; }
done

bash -n "$INSTALLER"
python3 -m json.tool "$CONTRACT" >/dev/null

python3 -B - "$CONTRACT" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
if payload.get("schema_version") != "1.0.0":
    raise SystemExit("P6 acceptance schema changed")
if payload.get("status") != "verified":
    raise SystemExit("P6 acceptance must remain verified")
if payload.get("required_android_api") != 33 or payload.get("required_abi") != "arm64-v8a":
    raise SystemExit("P6 acceptance target changed")
probes = payload.get("probe_modules", [])
expected_packages = [f"P6-W{index:02d}" for index in range(1, 7)]
if [probe.get("work_package") for probe in probes] != expected_packages:
    raise SystemExit("P6 probe work packages changed")
expected_markers = [
    "event_broker_android13_arm64_verified=true",
    "event_qos_android13_arm64_verified=true",
    "trigger_engine_android13_arm64_verified=true",
    "proactive_consent_android13_arm64_verified=true",
    "context_source_android13_arm64_verified=true",
    "active_suggestion_android13_arm64_verified=true",
]
if [probe.get("marker") for probe in probes] != expected_markers:
    raise SystemExit("P6 probe markers changed")
claims = payload.get("claim_state", {})
expected_true = {
    "p6_android13_arm64_probe_acceptance_complete",
    "android_runtime_full_install_regression_passed",
    "device_identity_redacted",
    "debug_probe_only",
}
expected_false = {
    "production_event_middleware_published",
    "production_trigger_runtime_wired",
    "production_proactive_authority_published",
    "production_context_source_registry_published",
    "production_active_suggestion_source_wired",
    "production_runtime_wired",
    "vehicle_accessed",
    "npu_accessed",
    "driver_hal_accessed",
    "hardware_accessed",
    "production_ready",
    "target_hardware_validated",
}
if claims.get("p6_probe_module_count") != 6:
    raise SystemExit("P6 probe module count changed")
if {key for key, value in claims.items() if value is True} != expected_true:
    raise SystemExit("P6 positive claims changed")
if {key for key, value in claims.items() if value is False} != expected_false:
    raise SystemExit("P6 false claims changed")
PY

for marker in \
  'device_transport_selected=true' \
  'device_identity_redacted=true' \
  'android_api=$SDK' \
  'device_abi=$ABI' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'android_runtime_full_install_regression_passed=true'; do
  grep -Fq -- "$marker" "$INSTALLER" \
    || { echo "P6 installer marker missing: $marker" >&2; exit 1; }
done
if grep -Eq 'ro\.product\.model|device_serial=|device_model=' "$INSTALLER"; then
  echo "P6 installer must not emit raw device identity" >&2
  exit 1
fi

while IFS='|' read -r checker marker; do
  output="$(bash "$ROOT_DIR/tools/$checker")"
  grep -Fq -- "$marker" <<<"$output" \
    || { echo "P6 module checker marker missing: $checker: $marker" >&2; exit 1; }
  grep -Fq -- "$marker" "$INSTALLER" \
    || { echo "P6 probe marker missing from installer: $marker" >&2; exit 1; }
done <<'EOF'
check_central_brain_android_event_broker.sh|event_broker_android13_arm64_verified=true
check_central_brain_android_event_backpressure_qos.sh|event_qos_android13_arm64_verified=true
check_central_brain_android_trigger_engine.sh|trigger_engine_android13_arm64_verified=true
check_central_brain_android_proactive_consent_policy.sh|proactive_consent_android13_arm64_verified=true
check_central_brain_android_context_source_adapters.sh|context_source_android13_arm64_verified=true
check_central_brain_android_active_suggestion_ux.sh|active_suggestion_android13_arm64_verified=true
EOF

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$file" \
    || { echo "P6 acceptance marker missing in ${file#$ROOT_DIR/}: $marker" >&2; exit 1; }
}

require_text "$REQUIREMENTS" '## 99. P6 Android 13 ARM64 Event/Proactive/Context probe acceptance trace'
require_text "$DEVIATIONS" '## DEV-107 P6 debug probe acceptance is not production Event or proactive authority'
require_text "$ISSUES" '### P6 Android 13 ARM64 probe acceptance update'
require_text "$DELIVERY" '## 2026-07-18 P6 Android 13 ARM64 Event/Proactive/Context probe acceptance'
require_text "$DRIVER" '## P6 Android 13 ARM64 aggregate probe Driver/HAL boundary'
require_text "$ROADMAP" '### 2026-07-18 P6 Android 13 ARM64 aggregate probe acceptance'
require_text "$REPORT" '## 23. 2026-07-18 P6 Event/Proactive/Context aggregate Android acceptance evidence'
require_text "$ARCHITECTURE" '## P6 Android 13 ARM64 aggregate probe acceptance architecture'
require_text "$INTERFACES" '## P6 Android 13 ARM64 aggregate probe acceptance interface'
require_text "$DESIGN" '## P6 implementation detail: aggregate Android probe acceptance'
require_text "$BACKLOG" '## 21. P6 Android physical probe acceptance update'
require_text "$README" '| P6 Android aggregate probe acceptance |'
for marker in \
  'p6_android13_arm64_probe_acceptance_complete=true' \
  'p6_probe_module_count=6' \
  'device_identity_redacted=true' \
  'production_event_middleware_published=false' \
  'production_trigger_runtime_wired=false' \
  'production_proactive_authority_published=false' \
  'production_context_source_registry_published=false' \
  'production_active_suggestion_source_wired=false' \
  'production_runtime_wired=false' \
  'driver_hal_accessed=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'; do
  require_text "$README" "$marker"
  require_text "$REPORT" "$marker"
done

printf '%s\n' \
  'p6_probe_acceptance_contract_verified=true' \
  'p6_probe_module_count=6' \
  'p6_android13_arm64_probe_acceptance_complete=true' \
  'device_identity_redacted=true' \
  'production_event_middleware_published=false' \
  'production_trigger_runtime_wired=false' \
  'production_proactive_authority_published=false' \
  'production_context_source_registry_published=false' \
  'production_active_suggestion_source_wired=false' \
  'production_runtime_wired=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
