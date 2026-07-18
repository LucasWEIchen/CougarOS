#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SAF-001, S2-TOL-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/central-brain/android-runtime"
SERIAL="${ANDROID_SERIAL:-}"
BUILD=true
REQUIRE_API_33=false
CLEAN_RUNTIME_INSTALL=false

usage() {
  printf '%s\n' \
    'Usage: test_central_brain_android_security_identity.sh [options]' \
    '  --serial SERIAL    Select an adb device explicitly.' \
    '  --skip-build       Reuse existing Runtime/androidTest APKs.' \
    '  --clean-runtime-install' \
    '                     Remove only com.centralbrain.runtime before install.' \
    '  --require-api-33   Require Android 13 / API 33 exactly.'
}

while (($# > 0)); do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "--serial requires a value" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    --skip-build)
      BUILD=false
      shift
      ;;
    --clean-runtime-install)
      CLEAN_RUNTIME_INSTALL=true
      shift
      ;;
    --require-api-33)
      REQUIRE_API_33=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  source "$ROOT_DIR/env.sh"
fi
: "${JAVA_HOME:?JAVA_HOME must point to JDK 17}"
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"
export ANDROID_HOME
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$ROOT_DIR/.tools/home/.android}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.tools/gradle-home}"

ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
[[ -x "$ADB" ]] || { echo "adb not executable: $ADB" >&2; exit 1; }

if [[ "$BUILD" == true ]]; then
  "$RUNTIME_DIR/gradlew" \
    --project-dir "$RUNTIME_DIR" \
    --no-daemon \
    :runtime-service:assembleDebug \
    :central-brain-sdk:assembleDebugAndroidTest
fi

RUNTIME_APK="$RUNTIME_DIR/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
TEST_APK="$RUNTIME_DIR/central-brain-sdk/build/outputs/apk/androidTest/debug/central-brain-sdk-debug-androidTest.apk"
[[ -f "$RUNTIME_APK" ]] || { echo "missing Runtime APK" >&2; exit 1; }
[[ -f "$TEST_APK" ]] || { echo "missing SDK androidTest APK" >&2; exit 1; }

if [[ -z "$SERIAL" ]]; then
  mapfile -t ONLINE_DEVICES < <("$ADB" devices | tr -d '\r' \
    | awk 'NR > 1 && $2 == "device" { print $1 }')
  [[ ${#ONLINE_DEVICES[@]} -eq 1 ]] \
    || { echo "expected exactly one online adb device" >&2; exit 1; }
  SERIAL="${ONLINE_DEVICES[0]}"
fi
ADB_DEVICE=("$ADB" -s "$SERIAL")
[[ "$("${ADB_DEVICE[@]}" get-state | tr -d '\r')" == "device" ]] \
  || { echo "selected adb device is not online" >&2; exit 1; }
SDK="$("${ADB_DEVICE[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
ABI="$("${ADB_DEVICE[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
[[ "$SDK" =~ ^[0-9]+$ ]] && ((SDK >= 33)) \
  || { echo "Android API 33 or newer is required" >&2; exit 1; }
if [[ "$REQUIRE_API_33" == true && "$SDK" != "33" ]]; then
  echo "API 33 was required, device reported API $SDK" >&2
  exit 1
fi
[[ "$ABI" == "arm64-v8a" ]] \
  || { echo "arm64-v8a target is required, device reported $ABI" >&2; exit 1; }

cleanup() {
  "${ADB_DEVICE[@]}" uninstall com.centralbrain.sdk.test >/dev/null 2>&1 || true
}
trap cleanup EXIT

if [[ "$CLEAN_RUNTIME_INSTALL" == true ]]; then
  "${ADB_DEVICE[@]}" uninstall com.centralbrain.runtime >/dev/null 2>&1 || true
fi
"${ADB_DEVICE[@]}" install -r "$RUNTIME_APK" >/dev/null
"${ADB_DEVICE[@]}" install -r -t "$TEST_APK" >/dev/null

OUTPUT="$("${ADB_DEVICE[@]}" shell am instrument -w -r \
  -e securityIdentity true \
  com.centralbrain.sdk.test/com.centralbrain.sdk.session.SessionParcelInstrumentation \
  | tr -d '\r')"
for marker in \
  "security_identity_device_probe_verified=true" \
  "security_distinct_app_uids_verified=true" \
  "security_binder_calling_uid_spoof_android_verified=true" \
  "security_package_signature_cryptographically_verified=true" \
  "security_same_signer_debug_binding_verified=true" \
  "security_production_signer_verified=false" \
  "security_coverage_guided_fuzz_complete=false" \
  "security_runtime_wired=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false"; do
  grep -Fq "$marker" <<<"$OUTPUT" \
    || { echo "security identity evidence missing: $marker" >&2; exit 1; }
done

printf '%s\n' \
  'Central Brain Android security identity test passed' \
  "android_api=$SDK" \
  "android_abi=$ABI" \
  'device_identity_redacted=true' \
  'security_identity_device_probe_verified=true' \
  'security_distinct_app_uids_verified=true' \
  'security_binder_calling_uid_spoof_android_verified=true' \
  'security_package_signature_cryptographically_verified=true' \
  'security_same_signer_debug_binding_verified=true' \
  'security_production_signer_verified=false' \
  'security_coverage_guided_fuzz_complete=false' \
  'security_runtime_wired=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
