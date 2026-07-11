#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android Job Supervisor file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android Job Supervisor pattern '$pattern' in $path" >&2
    exit 1
  fi
}

SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
IDENTITY="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/identity/CallerIdentitySnapshot.java"
RESOLVER="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/identity/AndroidCallerIdentityResolver.java"
SUPERVISOR="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/supervisor/JobSupervisor.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/supervisor/JobSupervisorTest.java"
IDENTITY_TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/identity/CallerIdentitySnapshotTest.java"

for path in "$SERVICE" "$IDENTITY" "$RESOLVER" "$SUPERVISOR" "$TEST" "$IDENTITY_TEST"; do
  require_file "$path"
done

for state in ACCEPTED RUNNING COMPLETED FAILED CANCELLED; do
  require_text "$SUPERVISOR" "$state"
done
require_text "$SUPERVISOR" "validateTransition"
require_text "$SUPERVISOR" "CapacityExceededException"
require_text "$SUPERVISOR" "pruneExpired"
require_text "$SUPERVISOR" "markTerminalDeliverySettled"
require_text "$SUPERVISOR" "terminalDeliveryPending"
require_text "$SUPERVISOR" "samePrincipal"
require_text "$RESOLVER" "Binder.getCallingUid()"
require_text "$RESOLVER" "getPackagesForUid"
require_text "$RESOLVER" "PackageManager.GET_SIGNING_CERTIFICATES"
require_text "$RESOLVER" "getApkContentsSigners"
require_text "$RESOLVER" 'MessageDigest.getInstance("SHA-256")'
require_text "$RESOLVER" "getSerialNumberForUser"
require_text "$SERVICE" "MAX_TASK_RECORDS = 128"
require_text "$SERVICE" "TERMINAL_RETENTION_MS"
require_text "$SERVICE" "resolveTrustedCaller"
require_text "$SERVICE" "jobSupervisor.cancelOwned"
require_text "$SERVICE" "jobSupervisor.findOwned"
require_text "$IDENTITY_TEST" "swappedSigners"
require_text "$SERVICE" "hardware_accessed=false"
require_text "central-brain/android-runtime/runtime-service/build.gradle.kts" "testImplementation(libs.junit)"
require_text "tools/build_central_brain_android_runtime.sh" ":runtime-service:testDebugUnitTest"

if grep -R -Eiq 'caller_?permissions|requested_?permissions|self.?reported.?permission' \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main" \
    "$ROOT_DIR/central-brain/android-runtime/central-brain-sdk/src/main"; then
  echo "Android production runtime must not authorize self-reported request permissions" >&2
  exit 1
fi

if grep -R -Eiq 'device.?node|/dev/|ioctl|sysfs|vendor.?sdk|pcie|dma.?buf' \
    "$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/java"; then
  echo "R3A must not access hardware or vendor runtime interfaces" >&2
  exit 1
fi

echo "Central Brain Android Job Supervisor check passed"
