#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SAF-001, S2-TOL-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/central-brain/android-runtime"
SERIAL="${ANDROID_SERIAL:-}"
BUILD=true
REQUIRE_API_33=false

usage() {
  printf '%s\n' \
    'Usage: test_central_brain_android_callback_replay_security.sh [options]' \
    '  --serial SERIAL    Select an adb device explicitly.' \
    '  --skip-build       Reuse existing Runtime/demo/androidTest APKs.' \
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
    :central-brain-sdk:testDebugUnitTest \
    :runtime-service:assembleDebug \
    :runtime-service:assembleRelease \
    :demo-hmi:assembleDebug \
    :demo-hmi:assembleDebugAndroidTest \
    :central-brain-sdk:assembleDebugAndroidTest
fi

RUNTIME_APK="$RUNTIME_DIR/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
DEMO_APK="$RUNTIME_DIR/demo-hmi/build/outputs/apk/debug/demo-hmi-debug.apk"
DEMO_TEST_APK="$RUNTIME_DIR/demo-hmi/build/outputs/apk/androidTest/debug/demo-hmi-debug-androidTest.apk"
SDK_TEST_APK="$RUNTIME_DIR/central-brain-sdk/build/outputs/apk/androidTest/debug/central-brain-sdk-debug-androidTest.apk"
for artifact in "$RUNTIME_APK" "$DEMO_APK" "$DEMO_TEST_APK" "$SDK_TEST_APK"; do
  [[ -f "$artifact" ]] || { echo "missing callback replay artifact: $artifact" >&2; exit 1; }
done

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
  "${ADB_DEVICE[@]}" uninstall com.centralbrain.demo.test >/dev/null 2>&1 || true
}
trap cleanup EXIT

"${ADB_DEVICE[@]}" install -r "$RUNTIME_APK" >/dev/null
"${ADB_DEVICE[@]}" install -r "$DEMO_APK" >/dev/null
"${ADB_DEVICE[@]}" install -r -t "$DEMO_TEST_APK" >/dev/null
"${ADB_DEVICE[@]}" install -r -t "$SDK_TEST_APK" >/dev/null

NONCE="$(date +%s%N)"
SEED_OUTPUT="$("${ADB_DEVICE[@]}" shell am instrument -w -r \
  -e callbackReplayOwnerSeed true \
  -e callbackReplayNonce "$NONCE" \
  com.centralbrain.demo.test/com.centralbrain.demo.test.CentralBrainBinderInstrumentation \
  | tr -d '\r')"
for marker in \
  'callback_replay_owner_seeded=true' \
  'callback_replay_owner_seed_terminal_unique=true' \
  'hardware_accessed=false'; do
  grep -Fq "$marker" <<<"$SEED_OUTPUT" \
    || { printf '%s\n' "$SEED_OUTPUT" >&2; echo "callback replay owner seed missing: $marker" >&2; exit 1; }
done
FOREIGN_TASK_ID="$(sed -n \
  's/.*callback_replay_foreign_task_id=\([^[:space:]]*\).*/\1/p' \
  <<<"$SEED_OUTPUT" | tail -n 1)"
[[ -n "$FOREIGN_TASK_ID" ]] \
  || { echo "callback replay owner seed returned no task reference" >&2; exit 1; }

PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am instrument -w -r \
  -e callbackReplaySecurity true \
  -e callbackReplayNonce "$NONCE" \
  -e callbackReplayForeignTaskId "$FOREIGN_TASK_ID" \
  com.centralbrain.sdk.test/com.centralbrain.sdk.session.SessionParcelInstrumentation \
  | tr -d '\r')"
for marker in \
  'security_task_callback_replay_android_verified=true' \
  'security_callback_sequence_replay_suppressed=true' \
  'security_callback_terminal_replay_unique=true' \
  'security_idempotency_conflict_callback_silent=true' \
  'security_cross_uid_callback_owner_isolation_verified=true' \
  'security_distinct_callback_owner_uids_verified=true' \
  'security_debug_test_principal_release_excluded=true' \
  'security_coverage_guided_fuzz_complete=false' \
  'security_production_signer_verified=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'; do
  grep -Fq "$marker" <<<"$PROBE_OUTPUT" \
    || { printf '%s\n' "$PROBE_OUTPUT" >&2; echo "callback replay device evidence missing: $marker" >&2; exit 1; }
done

printf '%s\n' \
  'Central Brain Android callback replay security test passed' \
  "android_api=$SDK" \
  "android_abi=$ABI" \
  'device_identity_redacted=true' \
  'security_task_callback_replay_android_verified=true' \
  'security_callback_sequence_replay_suppressed=true' \
  'security_callback_terminal_replay_unique=true' \
  'security_idempotency_conflict_callback_silent=true' \
  'security_cross_uid_callback_owner_isolation_verified=true' \
  'security_distinct_callback_owner_uids_verified=true' \
  'security_debug_test_principal_release_excluded=true' \
  'security_coverage_guided_fuzz_complete=false' \
  'security_production_signer_verified=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
