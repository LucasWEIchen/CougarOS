#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004/005/006, NV-F-001/011/012,
# NV-G-003/006/007, NV-P-002, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROFILE="central-brain/delivery/android/central-brain.android-delivery-profile.json"
TARGET_INPUTS="central-brain/delivery/android/target-inputs.example.json"
DELIVERY_TOOL="tools/central_brain_android_delivery.py"
PACKAGE_TOOL="tools/package_central_brain_android_delivery.sh"
INSTALL_TOOL="tools/install_central_brain_android_delivery.sh"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android R7D handoff file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android R7D handoff pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in \
  "$PROFILE" \
  "$TARGET_INPUTS" \
  central-brain/delivery/android/README.md \
  docs/CENTRAL_BRAIN_ANDROID_HARDWARE_MIGRATION_GUIDE.md \
  "$DELIVERY_TOOL" \
  "$PACKAGE_TOOL" \
  "$INSTALL_TOOL"; do
  require_file "$path"
done

bash -n "$ROOT_DIR/$PACKAGE_TOOL"
bash -n "$ROOT_DIR/$INSTALL_TOOL"
python3 - "$ROOT_DIR" <<'PY'
import importlib.util
import json
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
tool_path = root / "tools/central_brain_android_delivery.py"
compile(tool_path.read_text(encoding="utf-8"), str(tool_path), "exec")
spec = importlib.util.spec_from_file_location("central_brain_android_delivery", tool_path)
if spec is None or spec.loader is None:
    raise SystemExit("cannot load Android delivery verifier")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

profile_path = root / "central-brain/delivery/android/central-brain.android-delivery-profile.json"
target_path = root / "central-brain/delivery/android/target-inputs.example.json"
profile = json.loads(profile_path.read_text(encoding="utf-8"))
target = json.loads(target_path.read_text(encoding="utf-8"))
module.validate_profile(profile)
module.validate_target_inputs(target_path)
for unsafe in ("../escape", "/absolute", "double//separator", "windows\\path"):
    try:
        module.validate_relative_path(unsafe, "negative-test")
    except SystemExit:
        pass
    else:
        raise SystemExit(f"R7D unsafe path was accepted: {unsafe}")

expected_support = {
    "README.md",
    "contracts/central_brain_android_r7c_acceptance.json",
    "contracts/central-brain.android-delivery-profile.json",
    "contracts/target-inputs.example.json",
    "docs/CENTRAL_BRAIN_ANDROID_HARDWARE_MIGRATION_GUIDE.md",
    "docs/CENTRAL_BRAIN_ANDROID_TARGET_DEPLOYMENT_ACCEPTANCE.md",
    "docs/CENTRAL_BRAIN_ANDROID_R7C_APPLICATION_ACCEPTANCE.md",
    "tools/test_central_brain_android_target_deployment.sh",
    "tools/test_client2_central_brain_recovery.sh",
    "docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md",
    "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md",
    "tools/central_brain_android_delivery.py",
    "tools/install_central_brain_android_delivery.sh",
}
actual_support = {item["bundle_path"] for item in profile["support_files"]}
if actual_support != expected_support:
    raise SystemExit("R7D support inventory changed")

unresolved = sum(
    value is None
    for section in ("owners", "deployment_decisions", "evidence_references")
    for value in target[section].values()
)
if unresolved != 17:
    raise SystemExit(f"R7D target-input unresolved count changed: {unresolved}")
if any(item.get("current_native_code_present") is True for item in profile["empty_interfaces"]):
    raise SystemExit("R7D empty integration slot unexpectedly contains native code")
PY

for item in \
  "android_delivery_package_ready=true" \
  "software_handoff_ready=true" \
  "production_ready=false" \
  "target_hardware_validated=false" \
  "bundle_archive_sha256="; do
  require_text "$PACKAGE_TOOL" "$item"
done

for item in \
  "--execute" \
  "--allow-debug-signing" \
  "SIGNER_MIGRATION_REQUIRED" \
  "BUNDLE_PACKAGE_MISMATCH" \
  "BUNDLE_SIGNATURE_INVALID" \
  "BUNDLE_SIGNER_MISMATCH" \
  "certificate SHA-256 digest: " \
  'install -r' \
  "target_delivery_preflight_verified=true" \
  "bundle_apk_identity_verified=true" \
  "install_executed=false" \
  "automatic_uninstall_enabled=false" \
  "system_partition_write_capability=false" \
  "production_ready=false" \
  "target_hardware_validated=false" \
  "JAVA_HOME"; do
  require_text "$INSTALL_TOOL" "$item"
done

if grep -Eq '\$\{?ADB[^[:space:]]*\}?[^\n]*(root|remount|uninstall)' \
    "$ROOT_DIR/$PACKAGE_TOOL" "$ROOT_DIR/$INSTALL_TOOL" \
    || grep -Eq '(^|[[:space:]])fastboot[[:space:]]' \
      "$ROOT_DIR/$PACKAGE_TOOL" "$ROOT_DIR/$INSTALL_TOOL"; then
  echo "R7D tooling contains a prohibited device mutation command" >&2
  exit 1
fi

for trace in \
  "docs/CENTRAL_BRAIN_ANDROID_RUNTIME_EVOLUTION_PLAN.md|R7D Android 13 software handoff" \
  "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md|R7D Android 13 software handoff trace" \
  "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md|Android R7D Delivery And Empty Integration Slots" \
  "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md|Android R7D Software Handoff Package" \
  "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md|R7D Handoff Driver/HAL Boundary" \
  "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md|R7D 进展" \
  "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md|R7D 进展" \
  "docs/CENTRAL_BRAIN_ROADMAP.md|R7D Android 13 software handoff" \
  "central-brain/android-runtime/README.md|R7D Android Software Handoff"; do
  IFS='|' read -r path pattern <<<"$trace"
  require_text "$path" "$pattern"
done

bash "$ROOT_DIR/tools/check_central_brain_android_application_acceptance.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_target_deployment.sh"

printf '%s\n' \
  "android_delivery_profile_verified=true" \
  "android_delivery_empty_slot_count=7" \
  "android_delivery_signer_preflight_required=true" \
  "android_delivery_dry_run_default=true" \
  "android_delivery_automatic_uninstall_enabled=false" \
  "android_delivery_system_partition_write_capability=false" \
  "android_delivery_production_ready=false" \
  "android_delivery_target_hardware_validated=false" \
  "Central Brain Android R7D delivery handoff check passed"
