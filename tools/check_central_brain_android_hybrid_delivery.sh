#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004/005/006, NV-F-001/011/012,
# NV-G-003/005/006/007, NV-P-002, KH-003/006, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROFILE="central-brain/delivery/android-hybrid/central-brain.android-hybrid-delivery-profile.json"
TARGET_INPUTS="central-brain/delivery/android-hybrid/target-inputs.example.json"
README="central-brain/delivery/android-hybrid/README.md"
GUIDE="docs/CENTRAL_BRAIN_ANDROID13_HYBRID_INSTALLATION_AND_USAGE.md"
PY_TOOL="tools/central_brain_android_hybrid_delivery.py"
PACKAGE_TOOL="tools/package_central_brain_android_hybrid_delivery.sh"
INSTALLER="tools/install_central_brain_android_hybrid_delivery.sh"
REMOTE_CONTRACT="central-brain/contracts/central_brain_github_remote_testing.json"
REMOTE_GUIDE="docs/CENTRAL_BRAIN_GITHUB_REMOTE_HARDWARE_TESTING.md"
REMOTE_RUNNER="tools/run_central_brain_android_remote_acceptance.sh"

require_file() {
  [[ -f "$ROOT_DIR/$1" ]] || { echo "missing B4 hybrid delivery file: $1" >&2; exit 1; }
}

require_text() {
  grep -Fq -- "$2" "$ROOT_DIR/$1" \
    || { echo "missing B4 hybrid pattern '$2' in $1" >&2; exit 1; }
}

for path in "$PROFILE" "$TARGET_INPUTS" "$README" "$GUIDE" "$PY_TOOL" \
    "$PACKAGE_TOOL" "$INSTALLER" "$REMOTE_CONTRACT" "$REMOTE_GUIDE" \
    "$REMOTE_RUNNER"; do
  require_file "$path"
done

require_text "$PROFILE" '"delivery_scope": "android13-blackbox-hybrid-application-layer"'
require_text "$PROFILE" '"id": "native-runtime"'
require_text "$PROFILE" '"jni/arm64-v8a/libcentral_brain_native.so"'
require_text "$PROFILE" '"lib/x86_64/libcentral_brain_native.so"'
require_text "$PROFILE" '"vendor_npu_provider_available": false'
require_text "$PROFILE" '"runtime_dispatch_enabled": false'
require_text "$PROFILE" '"default_install": false'
require_text "$PROFILE" '"automatic_uninstall_on_signer_mismatch": false'
require_text "$PROFILE" '"bundle_path": "contracts/central_brain_github_remote_testing.json"'
require_text "$PROFILE" '"bundle_path": "tools/run_central_brain_android_remote_acceptance.sh"'
require_text "$TARGET_INPUTS" '"physical_controller_evidence_available": false'
require_text "$GUIDE" "SIGNER_MIGRATION_REQUIRED"
require_text "$GUIDE" "--include-client2"
require_text "$GUIDE" "native_runtime_process_ready=true"
require_text "$GUIDE" "B4 hybrid software handoff verified on API 33 emulator"
require_text "$PY_TOOL" "hybrid_delivery_bundle_verified=true"
require_text "$PY_TOOL" "ELF_MACHINES"
require_text "$PACKAGE_TOOL" "native_artifact_count=2"
require_text "$INSTALLER" "EXECUTE=false"
require_text "$INSTALLER" "INCLUDE_CLIENT2=false"
require_text "$INSTALLER" "automatic_uninstall_enabled=false"
require_text "$INSTALLER" "SIGNER_MIGRATION_REQUIRED"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "B4 hybrid C/Java software handoff trace"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "2026-07-12 B4 结果"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "2026-07-12 B4 进展"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android B4 Hybrid C/Java Software Handoff"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "B4 Hybrid Delivery Driver/HAL Result"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android B4 Hybrid Delivery Contracts"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "| B4 | 实际工程交付 | APK/AAR、hash/signer/ABI、安装和使用指南 | 已完成（软件交付） |"

if rg -n 'central-brain/deploy/linux|linux frontend' "$ROOT_DIR/$PROFILE"; then
  echo "B4 Android hybrid profile must not contain Linux frontend artifacts" >&2
  exit 1
fi
if rg -n \
    '(^|[[:space:]])(adb[[:space:]]+root|fastboot|setenforce|mount[[:space:]]+-o)|remount([[:space:]]|$)|pm[[:space:]]+uninstall|adb[[:space:]]+uninstall' \
    "$ROOT_DIR/$INSTALLER"; then
  echo "B4 hybrid installer contains a prohibited device operation" >&2
  exit 1
fi

python3 - "$ROOT_DIR/$PY_TOOL" <<'PY'
import ast
import pathlib
import sys

ast.parse(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
PY
python3 -B "$ROOT_DIR/$PY_TOOL" validate-profile \
  --profile "$ROOT_DIR/$PROFILE" \
  --target-inputs "$ROOT_DIR/$TARGET_INPUTS"
bash -n "$ROOT_DIR/$PACKAGE_TOOL" "$ROOT_DIR/$INSTALLER" "$ROOT_DIR/$REMOTE_RUNNER"
bash "$ROOT_DIR/tools/check_central_brain_android_blackbox_preflight.sh"

echo "Central Brain Android B4 hybrid delivery check passed"
