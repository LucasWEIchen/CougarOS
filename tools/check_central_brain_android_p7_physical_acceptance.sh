#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MDL-001, S2-SAF-001, S2-OBS-001, NV-G-004,
# DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_p7_physical_acceptance.json"
INSTALLER="$ROOT_DIR/tools/install_central_brain_android_runtime.sh"
REQUIREMENTS="$ROOT_DIR/docs/CENTRAL_BRAIN_REQUIREMENTS.md"
DEVIATIONS="$ROOT_DIR/docs/CENTRAL_BRAIN_REQUIREMENTS.md"
ISSUES="$ROOT_DIR/docs/CENTRAL_BRAIN_REQUIREMENTS.md"
DELIVERY="$ROOT_DIR/docs/CENTRAL_BRAIN_REQUIREMENTS.md"
DRIVER="$ROOT_DIR/docs/CENTRAL_BRAIN_REQUIREMENTS.md"
ROADMAP="$ROOT_DIR/docs/CENTRAL_BRAIN_REQUIREMENTS.md"
REPORT="$ROOT_DIR/docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md"
ARCHITECTURE="$ROOT_DIR/docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md"
INTERFACES="$ROOT_DIR/docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md"
DESIGN="$ROOT_DIR/docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md"
BACKLOG="$ROOT_DIR/docs/CENTRAL_BRAIN_REQUIREMENTS.md"
README="$ROOT_DIR/README.md"

for path in "$CONTRACT" "$INSTALLER" "$REQUIREMENTS" "$DEVIATIONS" "$ISSUES" "$DELIVERY" "$DRIVER" \
    "$ROADMAP" "$REPORT" "$ARCHITECTURE" "$INTERFACES" "$DESIGN" "$BACKLOG" "$README"; do
  [[ -f "$path" ]] || { echo "missing P7 physical acceptance file: $path" >&2; exit 1; }
done

bash -n "$INSTALLER"
python3 -m json.tool "$CONTRACT" >/dev/null

python3 -B - "$CONTRACT" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
if payload.get("schema_version") != "1.0.0":
    raise SystemExit("P7 acceptance schema changed")
if payload.get("status") != "verified":
    raise SystemExit("P7 acceptance must remain verified")
if payload.get("required_android_api") != 33 or payload.get("required_abi") != "arm64-v8a":
    raise SystemExit("P7 acceptance target changed")
probes = payload.get("probe_modules", [])
expected_packages = [f"P7-W{index:02d}" for index in range(1, 8)]
if [probe.get("work_package") for probe in probes] != expected_packages:
    raise SystemExit("P7 probe work packages changed")
expected_markers = [
    "model_contract_v2_android13_arm64_verified=true",
    "model_provider_registry_android13_arm64_verified=true",
    "model_policy_router_android13_arm64_verified=true",
    "local_model_provider_android13_arm64_verified=true",
    "structured_model_output_android13_arm64_verified=true",
    "scenario_evaluation_android13_arm64_verified=true",
    "model_resource_admission_android13_arm64_verified=true",
]
if [probe.get("marker") for probe in probes] != expected_markers:
    raise SystemExit("P7 probe markers changed")
claims = payload.get("claim_state", {})
expected_true = {
    "p7_android13_arm64_probe_acceptance_complete",
    "android_runtime_full_install_regression_passed",
    "device_identity_redacted",
    "debug_probe_only",
}
expected_false = {
    "production_model_provider_published",
    "production_model_router_wired",
    "production_inference_enabled",
    "production_model_output_runtime_wired",
    "production_evaluation_authority_published",
    "production_resource_snapshot_provider_wired",
    "production_runtime_wired",
    "provider_invoked",
    "model_invoked",
    "network_accessed",
    "npu_accessed",
    "vehicle_accessed",
    "driver_hal_accessed",
    "hardware_accessed",
    "production_ready",
    "target_hardware_validated",
}
if claims.get("p7_probe_module_count") != 7:
    raise SystemExit("P7 probe module count changed")
if {key for key, value in claims.items() if value is True} != expected_true:
    raise SystemExit("P7 positive claims changed")
if {key for key, value in claims.items() if value is False} != expected_false:
    raise SystemExit("P7 false claims changed")
PY

for marker in \
  'device_transport_selected=true' \
  'device_identity_redacted=true' \
  'android_api=$SDK' \
  'device_abi=$ABI' \
  'model_invoked=false' \
  'npu_accessed=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'android_runtime_full_install_regression_passed=true'; do
  grep -Fq -- "$marker" "$INSTALLER" \
    || { echo "P7 installer marker missing: $marker" >&2; exit 1; }
done
if grep -Eq 'ro\.product\.model|device_serial=|device_model=' "$INSTALLER"; then
  echo "P7 installer must not emit raw device identity" >&2
  exit 1
fi

while IFS='|' read -r checker marker; do
  output="$(bash "$ROOT_DIR/tools/$checker")"
  grep -Fq -- "$marker" <<<"$output" \
    || { echo "P7 module checker marker missing: $checker: $marker" >&2; exit 1; }
  grep -Fq -- "$marker" "$INSTALLER" \
    || { echo "P7 probe marker missing from installer: $marker" >&2; exit 1; }
done <<'EOF'
check_central_brain_android_model_contract_v2.sh|model_contract_v2_android13_arm64_verified=true
check_central_brain_android_model_provider_registry.sh|model_provider_registry_android13_arm64_verified=true
check_central_brain_android_policy_aware_model_router.sh|model_policy_router_android13_arm64_verified=true
check_central_brain_android_local_model_provider.sh|local_model_provider_android13_arm64_verified=true
check_central_brain_android_structured_model_output.sh|structured_model_output_android13_arm64_verified=true
check_central_brain_android_scenario_evaluation.sh|scenario_evaluation_android13_arm64_verified=true
check_central_brain_android_model_resource_admission.sh|model_resource_admission_android13_arm64_verified=true
EOF

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
  grep -Fq -- "$marker" "$file" \
    || { echo "P7 acceptance marker missing in ${file#$ROOT_DIR/}: $marker" >&2; exit 1; }
}

require_text "$REQUIREMENTS" '## 100. P7 Android 13 ARM64 Model/Router/Evaluation probe acceptance trace'
require_text "$DEVIATIONS" '## DEV-108 P7 debug probe acceptance is not production inference or model quality'
require_text "$ISSUES" '### P7 Android 13 ARM64 probe acceptance update'
require_text "$DELIVERY" '## 2026-07-18 P7 Android 13 ARM64 Model/Router/Evaluation probe acceptance'
require_text "$DRIVER" '## P7 Android 13 ARM64 aggregate probe Driver/HAL boundary'
require_text "$ROADMAP" '### 2026-07-18 P7 Android 13 ARM64 aggregate probe acceptance'
require_text "$REPORT" '## 24. 2026-07-18 P7 Model/Router/Evaluation aggregate Android acceptance evidence'
require_text "$ARCHITECTURE" '## P7 Android 13 ARM64 aggregate probe acceptance architecture'
require_text "$INTERFACES" '## P7 Android 13 ARM64 aggregate probe acceptance interface'
require_text "$DESIGN" '## P7 implementation detail: aggregate Android probe acceptance'
require_text "$BACKLOG" '## 22. P7 Android physical probe acceptance update'
require_text "$README" '| `P7-W01` |'
require_text "$README" '| `P7-W07` |'
for marker in \
  'p7_android13_arm64_probe_acceptance_complete=true' \
  'p7_probe_module_count=7' \
  'device_identity_redacted=true' \
  'production_model_provider_published=false' \
  'production_model_router_wired=false' \
  'production_inference_enabled=false' \
  'production_model_output_runtime_wired=false' \
  'production_evaluation_authority_published=false' \
  'production_resource_snapshot_provider_wired=false' \
  'production_runtime_wired=false' \
  'provider_invoked=false' \
  'model_invoked=false' \
  'network_accessed=false' \
  'npu_accessed=false' \
  'driver_hal_accessed=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'; do
  require_text "$REPORT" "$marker"
done

bash "$ROOT_DIR/tools/check_central_brain_root_readme.sh" >/dev/null

printf '%s\n' \
  'p7_probe_acceptance_contract_verified=true' \
  'p7_probe_module_count=7' \
  'p7_android13_arm64_probe_acceptance_complete=true' \
  'device_identity_redacted=true' \
  'production_model_provider_published=false' \
  'production_model_router_wired=false' \
  'production_inference_enabled=false' \
  'production_model_output_runtime_wired=false' \
  'production_evaluation_authority_published=false' \
  'production_resource_snapshot_provider_wired=false' \
  'production_runtime_wired=false' \
  'provider_invoked=false' \
  'model_invoked=false' \
  'network_accessed=false' \
  'npu_accessed=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
