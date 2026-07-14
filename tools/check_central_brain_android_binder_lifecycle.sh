#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android Binder lifecycle file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android Binder lifecycle pattern '$pattern' in $path" >&2
    exit 1
  fi
}

SDK_CLIENT="central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/CentralBrainClient.java"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
DEMO_BUILD="central-brain/android-runtime/demo-hmi/build.gradle.kts"
DEMO_ACTIVITY="central-brain/android-runtime/demo-hmi/src/main/java/com/centralbrain/demo/DemoActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/demo-hmi/src/debug/AndroidManifest.xml"
CLIENT_DEATH_PROBE="central-brain/android-runtime/demo-hmi/src/debug/java/com/centralbrain/demo/ClientDeathProbeActivity.java"
INSTRUMENTATION="central-brain/android-runtime/demo-hmi/src/androidTest/java/com/centralbrain/demo/test/CentralBrainBinderInstrumentation.java"
DEVICE_TEST="tools/test_central_brain_android_binder_lifecycle.sh"

for path in \
  "$SDK_CLIENT" \
  "$RUNTIME_SERVICE" \
  "$DEMO_BUILD" \
  "$DEMO_ACTIVITY" \
  "$DEBUG_MANIFEST" \
  "$CLIENT_DEATH_PROBE" \
  "$INSTRUMENTATION" \
  "$DEVICE_TEST"; do
  require_file "$path"
done

require_text "$SDK_CLIENT" "public boolean reconnect()"
require_text "$SDK_CLIENT" "handleServiceDeath(service)"
require_text "$SDK_CLIENT" "expectedBinder != null && runtimeBinder != expectedBinder"
require_text "$SDK_CLIENT" "isCurrentConnection(service)"
require_text "$SDK_CLIENT" "hasLiveConnection()"
require_text "$SDK_CLIENT" "safeUnbind()"
require_text "$RUNTIME_SERVICE" "BuildConfig.DEBUG ? 3000 : 160"
require_text "$DEMO_BUILD" "CentralBrainBinderInstrumentation"
require_text "$DEMO_ACTIVITY" "governance protocol verification failed after reconnect"
require_text "$DEMO_ACTIVITY" '"Typed Binder: connected v" + version'
require_text "$DEMO_ACTIVITY" "client.reconnect();"
require_text "$DEMO_ACTIVITY" "governanceClient.reconnect();"
require_text "$DEMO_ACTIVITY" "MAX_RECONNECT_ATTEMPTS = 10"
require_text "$DEMO_ACTIVITY" "postDelayed(runtimeReconnectTask, RECONNECT_DELAY_MS)"
require_text "$DEMO_ACTIVITY" "postDelayed(governanceReconnectTask, RECONNECT_DELAY_MS)"
require_text "$DEBUG_MANIFEST" 'android:name=".ClientDeathProbeActivity"'
require_text "$DEBUG_MANIFEST" 'android:permission="android.permission.DUMP"'
require_text "$DEBUG_MANIFEST" 'android:process=":death_probe"'
require_text "$CLIENT_DEATH_PROBE" "client_death_probe_submitted=true"
require_text "$INSTRUMENTATION" "public void onCreate(Bundle arguments)"
require_text "$INSTRUMENTATION" "start();"
require_text "$INSTRUMENTATION" "binder_service_death_verified=true"
require_text "$INSTRUMENTATION" "binder_reconnect_verified=true"
require_text "$INSTRUMENTATION" "binder_terminal_uniqueness_verified=true"
require_text "$INSTRUMENTATION" "binder_cancel_completion_race_verified=true"
require_text "$DEVICE_TEST" "binder_client_death_verified=true"
require_text "$DEVICE_TEST" "r2_binder_exit_criteria_met=true"
require_text "$DEVICE_TEST" "RuntimeProbeActivity"

if grep -Fq "ClientDeathProbeActivity" \
    "$ROOT_DIR/central-brain/android-runtime/demo-hmi/src/main/AndroidManifest.xml"; then
  echo "client death probe must remain debug-only" >&2
  exit 1
fi
if grep -R -Eiq 'device.?node|/dev/|ioctl|sysfs|vendor.?sdk|pcie|dma.?buf' \
    "$ROOT_DIR/central-brain/android-runtime/demo-hmi/src/androidTest" \
    "$ROOT_DIR/central-brain/android-runtime/demo-hmi/src/debug"; then
  echo "R2C Binder lifecycle tests must not access hardware/vendor interfaces" >&2
  exit 1
fi

echo "Central Brain Android Binder lifecycle check passed"
