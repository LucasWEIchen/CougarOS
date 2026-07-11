#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android Binder runtime file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android Binder runtime pattern '$pattern' in $path" >&2
    exit 1
  fi
}

SDK_MANIFEST="central-brain/android-runtime/central-brain-sdk/src/main/AndroidManifest.xml"
RUNTIME_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
DEMO_MANIFEST="central-brain/android-runtime/demo-hmi/src/main/AndroidManifest.xml"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
DIAGNOSTIC_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainDiagnosticService.java"
DIAGNOSTIC_PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/DiagnosticProbeActivity.java"
SDK_CLIENT="central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/CentralBrainClient.java"
DEMO_ACTIVITY="central-brain/android-runtime/demo-hmi/src/main/java/com/centralbrain/demo/DemoActivity.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

for path in \
  "$SDK_MANIFEST" \
  "$RUNTIME_MANIFEST" \
  "$DEMO_MANIFEST" \
  "$RUNTIME_SERVICE" \
  "$DIAGNOSTIC_SERVICE" \
  "$DIAGNOSTIC_PROBE" \
  "$SDK_CLIENT" \
  "$DEMO_ACTIVITY" \
  "$INSTALLER"; do
  require_file "$path"
done

require_text "$SDK_MANIFEST" '<package android:name="com.centralbrain.runtime" />'
require_text "$RUNTIME_MANIFEST" 'android:name="com.centralbrain.permission.BIND_RUNTIME"'
require_text "$RUNTIME_MANIFEST" 'android:name="com.centralbrain.permission.ACCESS_DIAGNOSTICS"'
require_text "$RUNTIME_MANIFEST" 'android:protectionLevel="signature"'
require_text "$RUNTIME_MANIFEST" 'android:name=".CentralBrainRuntimeService"'
require_text "$RUNTIME_MANIFEST" 'android:permission="com.centralbrain.permission.BIND_RUNTIME"'
require_text "$RUNTIME_MANIFEST" 'android:name=".CentralBrainDiagnosticService"'
require_text "$RUNTIME_MANIFEST" 'android:permission="com.centralbrain.permission.ACCESS_DIAGNOSTICS"'
require_text "$DEMO_MANIFEST" '<uses-permission android:name="com.centralbrain.permission.BIND_RUNTIME" />'

if grep -Fq "QUERY_ALL_PACKAGES" "$ROOT_DIR/$SDK_MANIFEST" "$ROOT_DIR/$DEMO_MANIFEST"; then
  echo "Central Brain SDK must use a narrow package query, not QUERY_ALL_PACKAGES" >&2
  exit 1
fi
if grep -Fq "ACCESS_DIAGNOSTICS" "$ROOT_DIR/$DEMO_MANIFEST"; then
  echo "Demo HMI must not request diagnostic access" >&2
  exit 1
fi

require_text "$RUNTIME_SERVICE" "ICentralBrainRuntime.Stub"
require_text "$RUNTIME_SERVICE" "ScheduledExecutorService"
require_text "$RUNTIME_SERVICE" "linkToDeath"
require_text "$RUNTIME_SERVICE" "CANCEL_REASON_CLIENT_DIED"
require_text "$RUNTIME_SERVICE" "validateCancelReason"
require_text "$RUNTIME_SERVICE" "executor.execute(() -> notifyCancellation"
require_text "$RUNTIME_SERVICE" "hardware_accessed=false"
require_text "$DIAGNOSTIC_SERVICE" "ICentralBrainDiagnostics.Stub"
require_text "$DIAGNOSTIC_SERVICE" "Math.max(1, Math.min("
require_text "$DIAGNOSTIC_SERVICE" "hardware_accessed=false"
require_text "$DIAGNOSTIC_PROBE" "diagnostic_probe_passed="
require_text "$DIAGNOSTIC_PROBE" "getPage(query)"
require_text "$SDK_CLIENT" "ICentralBrainRuntime.Stub.asInterface"
require_text "$SDK_CLIENT" "bindService"
require_text "$SDK_CLIENT" "linkToDeath"
require_text "$SDK_CLIENT" "safeUnlinkToDeath"
require_text "$SDK_CLIENT" "CallbackBridge"
require_text "$DEMO_ACTIVITY" "Typed Binder: completed"
require_text "$DEMO_ACTIVITY" "Cancel: confirmed"
require_text "$DEMO_ACTIVITY" "cancelTask(handle"
require_text "$INSTALLER" "typed_binder_callback_completed=true"
require_text "$INSTALLER" "signature_permission_enforced=true"
require_text "$INSTALLER" "diagnostic_permission_requested_by_demo=false"
require_text "$INSTALLER" "diagnostic_binder_page_verified=true"

if grep -R -Fq "ServiceSpecificException" \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main"; then
  echo "independent APK code must not depend on hidden ServiceSpecificException" >&2
  exit 1
fi
if grep -R -Eiq 'device.?node|/dev/|ioctl|sysfs|vendor.?sdk|pcie|dma.?buf' \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/java"; then
  echo "R2 Binder runtime must not access hardware or vendor runtime interfaces" >&2
  exit 1
fi

bash "$ROOT_DIR/tools/check_central_brain_android_aidl_contract.sh"

echo "Central Brain Android Binder runtime check passed"
