#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SES-001, S2-EVT-001, XSC-005/006, NV-G-003/006/007,
# NV-P-002, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/central-brain/android-runtime"
SERIAL="${ANDROID_SERIAL:-}"
BUILD=true
REQUIRE_API_33=false

usage() {
  printf '%s\n' \
    'Usage: test_central_brain_android_session_durability.sh [options]' \
    '  --serial SERIAL    Select an adb device explicitly.' \
    '  --skip-build       Reuse existing Runtime/androidTest APKs.' \
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
    :runtime-service:testDebugUnitTest \
    :central-brain-sdk:testDebugUnitTest \
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
[[ "$SDK" =~ ^[0-9]+$ ]] && ((SDK >= 33)) \
  || { echo "Android API 33 or newer is required" >&2; exit 1; }
if [[ "$REQUIRE_API_33" == true && "$SDK" != "33" ]]; then
  echo "API 33 was required, device reported API $SDK" >&2
  exit 1
fi

cleanup() {
  "${ADB_DEVICE[@]}" uninstall com.centralbrain.sdk.test >/dev/null 2>&1 || true
}
trap cleanup EXIT

"${ADB_DEVICE[@]}" install -r "$RUNTIME_APK" >/dev/null
"${ADB_DEVICE[@]}" install -r -t "$TEST_APK" >/dev/null
"${ADB_DEVICE[@]}" logcat -c

NONCE="p1-w06-$(date +%s%N)"
"${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.persistence.MigrationProbeActivity \
  --es nonce "$NONCE" >/dev/null
MIGRATION_PASSED=false
for _ in {1..40}; do
  MIGRATION_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbMigrationProbe:I)"
  if grep -Fq "nonce=$NONCE migration_probe_complete=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "room_migration_3_4_verified=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "legacy_session_v1_exposure_blocked=true" <<<"$MIGRATION_LOG" \
      && grep -Fq "room_v4_crash_transaction_rollback_verified=true" \
        <<<"$MIGRATION_LOG"; then
    MIGRATION_PASSED=true
    break
  fi
  sleep 0.25
done
[[ "$MIGRATION_PASSED" == true ]] \
  || { echo "Room v4 migration probe failed" >&2; exit 1; }

REQUEST_ID="$(tr 'A-F' 'a-f' </proc/sys/kernel/random/uuid)"
SEED_OUTPUT="$("${ADB_DEVICE[@]}" shell am instrument -w -r \
  -e durableSeed true \
  -e durableRequestId "$REQUEST_ID" \
  com.centralbrain.sdk.test/com.centralbrain.sdk.session.SessionParcelInstrumentation \
  | tr -d '\r')"
grep -Fq "durable_session_seeded=true" <<<"$SEED_OUTPUT" \
  || { echo "durable Session seed failed" >&2; exit 1; }
SESSION_ID="$(sed -n 's/^durable_session_id=//p' <<<"$SEED_OUTPUT" | tail -n 1)"
[[ "$SESSION_ID" =~ ^[0-9a-f-]{36}$ ]] \
  || { echo "durable Session seed returned no canonical session ID" >&2; exit 1; }

"${ADB_DEVICE[@]}" shell am broadcast \
  -a com.centralbrain.runtime.DEBUG_KILL_PROCESS \
  -n com.centralbrain.runtime/.RuntimeFaultProbeReceiver >/dev/null
sleep 0.5

RECOVERY_OUTPUT="$("${ADB_DEVICE[@]}" shell am instrument -w -r \
  -e durableVerify true \
  -e durableRequestId "$REQUEST_ID" \
  -e durableSessionId "$SESSION_ID" \
  com.centralbrain.sdk.test/com.centralbrain.sdk.session.SessionParcelInstrumentation \
  | tr -d '\r')"
for marker in \
  "session_runtime_process_death_rehydration=true" \
  "durable_session_identity_preserved=true" \
  "durable_event_replay_after_process_death=true" \
  "durable_cancel_after_process_death=true" \
  "durable_terminal_state_immutable=true" \
  "scenario_execution_enabled=false" \
  "hardware_accessed=false"; do
  grep -Fq "$marker" <<<"$RECOVERY_OUTPUT" \
    || { echo "durability evidence missing: $marker" >&2; exit 1; }
done

printf '%s\n' \
  'Central Brain Android Session durability test passed' \
  "android_api=$SDK" \
  'room_schema_version=4' \
  'room_migration_3_4_verified=true' \
  'legacy_session_v1_exposure_blocked=true' \
  'room_v4_crash_transaction_rollback_verified=true' \
  'session_runtime_process_death_rehydration=true' \
  'durable_event_replay_after_process_death=true' \
  'scenario_execution_enabled=false' \
  'hardware_accessed=false'
