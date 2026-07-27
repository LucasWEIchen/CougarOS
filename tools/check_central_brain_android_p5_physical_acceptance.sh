#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-TOL-001, S2-MEM-001, S2-MDL-001, S2-SAF-001,
# S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_p5_physical_acceptance.json"
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
  [[ -f "$path" ]] || { echo "missing P5 physical acceptance file: $path" >&2; exit 1; }
done

bash -n "$INSTALLER"
python3 -m json.tool "$CONTRACT" >/dev/null

python3 -B - "$CONTRACT" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
if payload.get("schema_version") != "1.0.0":
    raise SystemExit("P5 acceptance schema changed")
if payload.get("status") != "verified":
    raise SystemExit("P5 acceptance must remain verified")
if payload.get("required_android_api") != 33 or payload.get("required_abi") != "arm64-v8a":
    raise SystemExit("P5 acceptance target changed")
probes = payload.get("probe_modules", [])
expected_packages = [f"P5-W{index:02d}" for index in range(1, 11)]
if [probe.get("work_package") for probe in probes] != expected_packages:
    raise SystemExit("P5 probe work packages changed")
expected_markers = [
    "tool_manifest_android13_arm64_verified=true",
    "tool_registry_android13_arm64_verified=true",
    "tool_rule_solver_android13_arm64_verified=true",
    "tool_executor_android13_arm64_verified=true",
    "skill_package_verifier_android13_arm64_verified=true",
    "working_memory_android13_arm64_verified=true",
    "profile_memory_android13_arm64_verified=true",
    "episodic_memory_android13_arm64_verified=true",
    "context_budget_android13_arm64_verified=true",
    "memory_consent_android13_arm64_verified=true",
]
if [probe.get("marker") for probe in probes] != expected_markers:
    raise SystemExit("P5 probe markers changed")
claims = payload.get("claim_state", {})
expected_true = {
    "p5_android13_arm64_probe_acceptance_complete",
    "android_runtime_full_install_regression_passed",
    "device_identity_redacted",
    "debug_probe_only",
}
expected_false = {
    "production_tool_authority_published",
    "production_memory_authority_published",
    "production_runtime_wired",
    "vehicle_accessed",
    "npu_accessed",
    "driver_hal_accessed",
    "hardware_accessed",
    "production_ready",
    "target_hardware_validated",
}
if claims.get("p5_probe_module_count") != 10:
    raise SystemExit("P5 probe module count changed")
if {key for key, value in claims.items() if value is True} != expected_true:
    raise SystemExit("P5 positive claims changed")
if {key for key, value in claims.items() if value is False} != expected_false:
    raise SystemExit("P5 false claims changed")
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
    || { echo "P5 installer marker missing: $marker" >&2; exit 1; }
done
if grep -Eq 'ro\.product\.model|device_serial=|device_model=' "$INSTALLER"; then
  echo "P5 installer must not emit raw device identity" >&2
  exit 1
fi

while IFS= read -r marker; do
  grep -Fq -- "$marker" "$INSTALLER" \
    || { echo "P5 probe marker missing from installer: $marker" >&2; exit 1; }
done < <(python3 -B - "$CONTRACT" <<'PY'
import json
import pathlib
import sys
for item in json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))["probe_modules"]:
    print(item["marker"])
PY
)

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
    || { echo "P5 acceptance marker missing in ${file#$ROOT_DIR/}: $marker" >&2; exit 1; }
}

require_text "$REQUIREMENTS" '## 98. P5 Android 13 ARM64 Tool/Skill/Memory probe acceptance trace'
require_text "$DEVIATIONS" '## DEV-106 P5 debug probe acceptance is not production Tool or Memory authority'
require_text "$ISSUES" '### P5 Android 13 ARM64 probe acceptance update'
require_text "$DELIVERY" '## 2026-07-18 P5 Android 13 ARM64 Tool/Skill/Memory probe acceptance'
require_text "$DRIVER" '## P5 Android 13 ARM64 aggregate probe Driver/HAL boundary'
require_text "$ROADMAP" '### 2026-07-18 P5 Android 13 ARM64 aggregate probe acceptance'
require_text "$REPORT" '## 22. 2026-07-18 P5 Tool/Skill/Memory aggregate Android acceptance evidence'
require_text "$ARCHITECTURE" '## P5 Android 13 ARM64 aggregate probe acceptance architecture'
require_text "$INTERFACES" '## P5 Android 13 ARM64 aggregate probe acceptance interface'
require_text "$DESIGN" '## P5 implementation detail: aggregate Android probe acceptance'
require_text "$BACKLOG" '## 20. P5 Android physical probe acceptance update'
require_text "$README" '| `P5-W01` |'
require_text "$README" '| `P5-W10` |'
for marker in \
  'p5_android13_arm64_probe_acceptance_complete=true' \
  'p5_probe_module_count=10' \
  'device_identity_redacted=true' \
  'production_tool_authority_published=false' \
  'production_memory_authority_published=false' \
  'production_runtime_wired=false' \
  'driver_hal_accessed=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'; do
  require_text "$REPORT" "$marker"
done

bash "$ROOT_DIR/tools/check_central_brain_root_readme.sh" >/dev/null

printf '%s\n' \
  'p5_probe_acceptance_contract_verified=true' \
  'p5_probe_module_count=10' \
  'p5_android13_arm64_probe_acceptance_complete=true' \
  'device_identity_redacted=true' \
  'production_tool_authority_published=false' \
  'production_memory_authority_published=false' \
  'production_runtime_wired=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
