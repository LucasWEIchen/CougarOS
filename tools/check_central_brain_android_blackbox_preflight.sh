#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004/005/006, NV-F-001/011/012,
# NV-G-003/005/006/007, NV-P-002, KH-003/006, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SNAPSHOT="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/blackbox/BlackBoxEnvironmentSnapshot.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/blackbox/BlackBoxEnvironmentProbeActivity.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/blackbox/BlackBoxEnvironmentSnapshotTest.java"
CONTRACT="central-brain/contracts/central_brain_android_b3_blackbox_acceptance.json"
DOC="docs/CENTRAL_BRAIN_ANDROID13_BLACKBOX_PREFLIGHT.md"
PREFLIGHT="tools/preflight_central_brain_android13_blackbox.sh"
ACCEPTANCE="tools/test_central_brain_android_blackbox_acceptance.sh"
SIGNER_GUARD="tools/test_central_brain_android_blackbox_signer_guard.sh"

require_file() {
  [[ -f "$ROOT_DIR/$1" ]] || { echo "missing B3 black-box file: $1" >&2; exit 1; }
}

require_text() {
  grep -Fq -- "$2" "$ROOT_DIR/$1" \
    || { echo "missing B3 pattern '$2' in $1" >&2; exit 1; }
}

for path in "$SNAPSHOT" "$PROBE" "$TEST" "$CONTRACT" "$DOC" \
    "$PREFLIGHT" "$ACCEPTANCE" "$SIGNER_GUARD"; do
  require_file "$path"
done

require_text "$SNAPSHOT" "REQUIRED_API_LEVEL = 33"
require_text "$SNAPSHOT" "private_vendor_api_probed=%s"
require_text "$SNAPSHOT" "device_nodes_scanned=%s"
require_text "$SNAPSHOT" "target_hardware_validated=false"
require_text "$PROBE" "PackageManager.GET_SIGNING_CERTIFICATES"
require_text "$PROBE" "Process.is64Bit()"
require_text "$PROBE" "Build.SUPPORTED_64_BIT_ABIS"
require_text "$PROBE" "nativeSnapshot.isRuntimeReady()"
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" ".blackbox.BlackBoxEnvironmentProbeActivity"
require_text "$PREFLIGHT" "preflight_mutation_performed=false"
require_text "$PREFLIGHT" "preinstall_signer_gate_passed=true"
require_text "$PREFLIGHT" "ro.product.cpu.abilist64"
require_text "$PREFLIGHT" "target_hardware_validated=false"
require_text "$ACCEPTANCE" "package_manager_signer_parity_verified=true"
require_text "$ACCEPTANCE" "binder_room_hmi_regression_verified=true"
require_text "$SIGNER_GUARD" "blackbox_preflight_failed_before_install=true"
require_text "$CONTRACT" '"preflight_mutations": []'
require_text "$DOC" "ISSUE-027"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "B3 black-box Android 13 preflight trace"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "2026-07-12 B3 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "2026-07-12 B3 进展"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android B3 Black-Box Preflight And Acceptance"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "B3 Black-Box Preflight Driver/HAL Result"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android B3 Black-Box Preflight Interfaces"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "| B3 | 黑盒验收 | 公开 API 能力探测、安全安装、API 33 设备证据 | 已完成（模拟器） |"

if grep -Fq "BlackBoxEnvironmentProbeActivity" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"; then
  echo "B3 black-box probe must remain debug-only" >&2
  exit 1
fi

if rg -n \
    '(^|[[:space:]])(adb[[:space:]]+root|fastboot|setenforce|mount[[:space:]]+-o|pm[[:space:]]+install|adb[[:space:]]+install)|ioctl|sysfs' \
    "$ROOT_DIR/$PREFLIGHT"; then
  echo "read-only B3 preflight contains a prohibited mutation or private-hardware path" >&2
  exit 1
fi
if rg -n -P '/dev/(?!null(?:[[:space:]]|$))' "$ROOT_DIR/$PREFLIGHT"; then
  echo "read-only B3 preflight must not inspect device nodes" >&2
  exit 1
fi
if rg -n 'install[[:space:]]+-r|uninstall[[:space:]]|remount([[:space:]]|$)' \
    "$ROOT_DIR/$PREFLIGHT"; then
  echo "read-only B3 preflight must not install, uninstall or remount" >&2
  exit 1
fi

bash "$ROOT_DIR/tools/check_central_brain_android_native_runtime_integration.sh"
echo "Central Brain Android B3 black-box preflight check passed"
