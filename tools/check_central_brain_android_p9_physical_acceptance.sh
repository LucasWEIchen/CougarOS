#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-OBS-001, S2-REL-001, S2-SAF-001, S2-MEM-001,
# S2-UX-002, S2-EFF-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_p9_physical_acceptance.json"
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
  [[ -f "$path" ]] || { echo "missing P9 physical acceptance file: $path" >&2; exit 1; }
done

bash -n "$INSTALLER"
python3 -m json.tool "$CONTRACT" >/dev/null

python3 -B - "$CONTRACT" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
if payload.get("schema_version") != "1.0.0":
    raise SystemExit("P9 acceptance schema changed")
if payload.get("status") != "verified":
    raise SystemExit("P9 acceptance must remain verified")
if payload.get("required_android_api") != 33 or payload.get("required_abi") != "arm64-v8a":
    raise SystemExit("P9 acceptance target changed")
probes = payload.get("probe_modules", [])
expected_packages = ["P9-W01", "P9-W02", "P9-W03c", "P9-W04c", "P9-W05b", "P9-W06b", "P9-W07b"]
if [probe.get("work_package") for probe in probes] != expected_packages:
    raise SystemExit("P9 probe work packages changed")
expected_markers = [
    "performance_budget_contract_probe_android13_arm64_verified=true",
    "stability_matrix_contract_probe_android13_arm64_verified=true",
    "security_boundary_probe_android13_arm64_verified=true",
    "privacy_redaction_probe_android13_arm64_verified=true",
    "release_metadata_probe_android13_arm64_verified=true",
    "driver_safety_android_contract_probe_android13_arm64_verified=true",
    "field_diagnostics_probe_android13_arm64_verified=true",
]
if [probe.get("marker") for probe in probes] != expected_markers:
    raise SystemExit("P9 probe markers changed")
claims = payload.get("claim_state", {})
expected_true = {
    "p9_android13_arm64_probe_acceptance_complete",
    "android_runtime_full_install_regression_passed",
    "device_identity_redacted",
    "debug_probe_only",
}
expected_false = {
    "performance_budget_target_measurement_complete",
    "stability_target_72h_complete",
    "security_coverage_guided_fuzz_complete",
    "security_binder_calling_uid_spoof_android_verified",
    "security_package_signature_cryptographically_verified",
    "privacy_owner_policy_approved",
    "privacy_current_policy_admitted",
    "privacy_repository_mutation_wired",
    "production_signer_owner_approved",
    "production_release_candidate_admitted",
    "release_installer_wired",
    "release_rollback_executor_wired",
    "driver_safety_current_owner_policy_approved",
    "driver_safety_vehicle_state_provider_wired",
    "driver_safety_effect_runtime_wired",
    "driver_safety_android13_arm64_verified",
    "field_diagnostics_target_category_execution_complete",
    "release_evidence_target_report_admitted",
    "release_evidence_runtime_diagnostics_wired",
    "release_evidence_automatic_upload_enabled",
    "vehicle_accessed",
    "driver_hal_accessed",
    "hardware_accessed",
    "production_ready",
    "target_hardware_validated",
}
if claims.get("p9_probe_module_count") != 7:
    raise SystemExit("P9 probe module count changed")
if {key for key, value in claims.items() if value is True} != expected_true:
    raise SystemExit("P9 positive claims changed")
if {key for key, value in claims.items() if value is False} != expected_false:
    raise SystemExit("P9 false claims changed")
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
    || { echo "P9 installer marker missing: $marker" >&2; exit 1; }
done
if grep -Eq 'ro\.product\.model|device_serial=|device_model=' "$INSTALLER"; then
  echo "P9 installer must not emit raw device identity" >&2
  exit 1
fi

while IFS='|' read -r checker marker; do
  output="$(bash "$ROOT_DIR/tools/$checker")"
  grep -Fq -- "$marker" <<<"$output" \
    || { echo "P9 module checker marker missing: $checker: $marker" >&2; exit 1; }
  grep -Fq -- "$marker" "$INSTALLER" \
    || { echo "P9 probe marker missing from installer: $marker" >&2; exit 1; }
done <<'EOF'
check_central_brain_android_performance_budget.sh|performance_budget_contract_probe_android13_arm64_verified=true
check_central_brain_android_stability_fault_matrix.sh|stability_matrix_contract_probe_android13_arm64_verified=true
check_central_brain_android_security_boundary_inventory.sh|security_boundary_probe_android13_arm64_verified=true
check_central_brain_android_privacy_redaction_audit.sh|privacy_redaction_probe_android13_arm64_verified=true
check_central_brain_android_production_release_metadata_probe.sh|release_metadata_probe_android13_arm64_verified=true
check_central_brain_android_driver_safety_probe.sh|driver_safety_android_contract_probe_android13_arm64_verified=true
check_central_brain_android_field_diagnostics_probe.sh|field_diagnostics_probe_android13_arm64_verified=true
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
    || { echo "P9 acceptance marker missing in ${file#$ROOT_DIR/}: $marker" >&2; exit 1; }
}

require_text "$REQUIREMENTS" '## 101. P9 Android 13 ARM64 hardening/release debug probe acceptance trace'
require_text "$DEVIATIONS" '## DEV-109 P9 debug probe acceptance is not production hardening qualification'
require_text "$ISSUES" '### P9 Android 13 ARM64 debug probe acceptance update'
require_text "$DELIVERY" '## 2026-07-18 P9 Android 13 ARM64 hardening/release debug probe acceptance'
require_text "$DRIVER" '## P9 Android 13 ARM64 aggregate debug probe Driver/HAL boundary'
require_text "$ROADMAP" '### 2026-07-18 P9 Android 13 ARM64 aggregate debug probe acceptance'
require_text "$REPORT" '## 25. 2026-07-18 P9 hardening/release aggregate Android probe evidence'
require_text "$ARCHITECTURE" '## P9 Android 13 ARM64 aggregate debug probe acceptance architecture'
require_text "$INTERFACES" '## P9 Android 13 ARM64 aggregate debug probe acceptance interface'
require_text "$DESIGN" '## P9 implementation detail: aggregate Android debug probe acceptance'
require_text "$BACKLOG" '## 23. P9 Android physical debug probe acceptance update'
require_text "$README" '| `P9-W01` |'
require_text "$README" '| `P9-W07c` |'
for marker in \
  'p9_android13_arm64_probe_acceptance_complete=true' \
  'p9_probe_module_count=7' \
  'device_identity_redacted=true' \
  'performance_budget_target_measurement_complete=false' \
  'stability_target_72h_complete=false' \
  'security_coverage_guided_fuzz_complete=false' \
  'privacy_owner_policy_approved=false' \
  'production_signer_owner_approved=false' \
  'driver_safety_android13_arm64_verified=false' \
  'field_diagnostics_target_category_execution_complete=false' \
  'release_evidence_target_report_admitted=false' \
  'driver_hal_accessed=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'; do
  require_text "$REPORT" "$marker"
done

bash "$ROOT_DIR/tools/check_central_brain_root_readme.sh" >/dev/null

printf '%s\n' \
  'p9_probe_acceptance_contract_verified=true' \
  'p9_probe_module_count=7' \
  'p9_android13_arm64_probe_acceptance_complete=true' \
  'device_identity_redacted=true' \
  'performance_budget_target_measurement_complete=false' \
  'stability_target_72h_complete=false' \
  'security_coverage_guided_fuzz_complete=false' \
  'privacy_owner_policy_approved=false' \
  'production_signer_owner_approved=false' \
  'driver_safety_android13_arm64_verified=false' \
  'field_diagnostics_target_category_execution_complete=false' \
  'release_evidence_target_report_admitted=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
