#!/usr/bin/env bash
set -euo pipefail

# Req IDs: XSC-004/005/006, NV-F-001/011, NV-G-003/006/007,
# NV-P-002, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
DIAGNOSTIC="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainDiagnosticService.java"
APPLICATION="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeApplication.java"
PROCESS="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/nativebridge/NativeRuntimeProcess.java"
SNAPSHOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/nativebridge/NativeRuntimeProcessSnapshot.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/nativebridge/NativeRuntimeProbeActivity.java"
DIAGNOSTIC_PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/DiagnosticProbeActivity.java"

require_file() {
  [[ -f "$ROOT_DIR/$1" ]] || { echo "missing native integration file: $1" >&2; exit 1; }
}

require_text() {
  grep -Fq -- "$2" "$ROOT_DIR/$1" \
    || { echo "missing native integration pattern '$2' in $1" >&2; exit 1; }
}

for path in \
  "$RUNTIME" "$DIAGNOSTIC" "$APPLICATION" "$PROCESS" "$SNAPSHOT" "$PROBE" \
  "$DIAGNOSTIC_PROBE" \
  central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml \
  central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml \
  central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/nativebridge/NativeRuntimeProcessSnapshotTest.java \
  docs/CENTRAL_BRAIN_NATIVE_RUNTIME_C_ABI.md \
  docs/CENTRAL_BRAIN_BLACKBOX_ANDROID13_ENGINEERING_PLAN.md \
  tools/test_central_brain_android_native_runtime.sh \
  tools/verify_central_brain_native_runtime_apk.sh; do
  require_file "$path"
done

require_text "central-brain/android-runtime/runtime-service/build.gradle.kts" 'implementation(project(":native-runtime"))'
require_text "central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml" 'android:name=".CentralBrainRuntimeApplication"'
require_text "$APPLICATION" "NativeRuntimeProcess.start"
require_text "$APPLICATION" "getNativeRuntimeSnapshot()"
require_text "$PROCESS" "catch (LinkageError error)"
require_text "$PROCESS" "NATIVE_QUERY_ERROR"
require_text "$SNAPSHOT" "native_runtime_process_ready=%s"
require_text "$SNAPSHOT" "native_runtime_dispatch_enabled=false"
require_text "$SNAPSHOT" "native_vendor_npu_provider_available=%s"
require_text "$SNAPSHOT" "native_hardware_accessed=%s"
require_text "$RUNTIME" "native_runtime_process_wired=true"
require_text "$RUNTIME" "nativeRuntimeSnapshot().logFields()"
require_text "$DIAGNOSTIC" '"native-runtime-readiness"'
require_text "$DIAGNOSTIC" "nativeRuntime.diagnosticDetail()"
require_text "$DIAGNOSTIC_PROBE" "native_runtime_diagnostic_verified="
require_text "$DIAGNOSTIC_PROBE" "record.sequence == 10"
require_text "$PROBE" "native_runtime_capacity_rejected="
require_text "$PROBE" "native_runtime_busy_close_rejected="
require_text "$PROBE" "native_runtime_duplicate_release_rejected="
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" '.nativebridge.NativeRuntimeProbeActivity'
require_text "tools/test_central_brain_android_native_runtime.sh" "native_runtime_process_recovery_verified=true"
require_text "tools/verify_central_brain_native_runtime_apk.sh" "native_runtime_apk_abis=arm64-v8a,x86_64"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "B2 Native Runtime process integration trace"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "2026-07-12 B2 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "2026-07-12 B2 进展"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android B2 Native Runtime Integration Artifact"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "B2 Native Process Integration Driver/HAL Result"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android B2 Native Runtime Process Integration"

if grep -Fq "NativeRuntimeProbeActivity" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"; then
  echo "Native Runtime probe must remain debug-only" >&2
  exit 1
fi
if rg -n 'acquireSlot\(|releaseSlot\(' "$ROOT_DIR/$RUNTIME" "$ROOT_DIR/$DIAGNOSTIC"; then
  echo "production Runtime/Diagnostic must not dispatch through native slots in B2" >&2
  exit 1
fi
if rg -n \
    'java\.net|okhttp|http://|https://|FileInputStream|FileOutputStream|/dev/|ioctl|sysfs|CarPropertyManager|VehicleHal|dlopen' \
    "$ROOT_DIR/$PROCESS" "$ROOT_DIR/$SNAPSHOT" "$ROOT_DIR/$APPLICATION"; then
  echo "B2 process lifecycle unexpectedly references network, file or hardware access" >&2
  exit 1
fi

bash "$ROOT_DIR/tools/check_central_brain_native_runtime.sh"
echo "Central Brain Android B2 Native Runtime integration check passed"
