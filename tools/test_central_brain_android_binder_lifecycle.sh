#!/usr/bin/env bash
set -euo pipefail

# Req IDs: XSC-006, NV-G-003, NV-G-006, NV-P-002, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/central-brain/android-runtime"
SERIAL="${ANDROID_SERIAL:-}"
BUILD=true
REQUIRE_API_33=false

usage() {
  cat <<'EOF'
Usage: test_central_brain_android_binder_lifecycle.sh [options]

Options:
  --serial SERIAL    Select an adb device explicitly.
  --skip-build       Reuse existing debug and androidTest artifacts.
  --require-api-33   Fail unless the selected device is exactly Android API 33.
  -h, --help         Show this help.
EOF
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
  # Local workspace toolchain; target integrators may provide these variables.
  source "$ROOT_DIR/env.sh"
fi

: "${JAVA_HOME:?JAVA_HOME must point to JDK 17}"
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"

export ANDROID_HOME
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.tools/gradle-home}"

ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
if [[ ! -x "$ADB" ]]; then
  echo "adb not executable: $ADB" >&2
  exit 1
fi

if [[ "$BUILD" == true ]]; then
  "$RUNTIME_DIR/gradlew" \
    --project-dir "$RUNTIME_DIR" \
    --no-daemon \
    --stacktrace \
    :central-brain-sdk:testDebugUnitTest \
    :runtime-service:assembleDebug \
    :demo-hmi:assembleDebug \
    :demo-hmi:assembleDebugAndroidTest
fi

RUNTIME_APK="$RUNTIME_DIR/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
DEMO_APK="$RUNTIME_DIR/demo-hmi/build/outputs/apk/debug/demo-hmi-debug.apk"
TEST_APK="$RUNTIME_DIR/demo-hmi/build/outputs/apk/androidTest/debug/demo-hmi-debug-androidTest.apk"
for artifact in "$RUNTIME_APK" "$DEMO_APK" "$TEST_APK"; do
  if [[ ! -f "$artifact" ]]; then
    echo "missing Android Binder lifecycle artifact: $artifact" >&2
    exit 1
  fi
done

if [[ -z "$SERIAL" ]]; then
  mapfile -t ONLINE_DEVICES < <("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [[ ${#ONLINE_DEVICES[@]} -ne 1 ]]; then
    echo "expected exactly one online adb device; use --serial when multiple exist" >&2
    "$ADB" devices -l >&2
    exit 1
  fi
  SERIAL="${ONLINE_DEVICES[0]}"
fi

ADB_DEVICE=("$ADB" -s "$SERIAL")
if [[ "$("${ADB_DEVICE[@]}" get-state)" != "device" ]]; then
  echo "adb device is not online: $SERIAL" >&2
  exit 1
fi

SDK="$("${ADB_DEVICE[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
ABI="$("${ADB_DEVICE[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
if [[ ! "$SDK" =~ ^[0-9]+$ ]] || ((SDK < 33)); then
  echo "Android API 33 or newer is required; device reported '$SDK'" >&2
  exit 1
fi
if [[ "$REQUIRE_API_33" == true && "$SDK" != "33" ]]; then
  echo "R2 API 33 evidence requested, but device reported API $SDK" >&2
  exit 1
fi

"${ADB_DEVICE[@]}" install -r "$RUNTIME_APK"
"${ADB_DEVICE[@]}" install -r "$DEMO_APK"
"${ADB_DEVICE[@]}" install -r -t "$TEST_APK"

set +e
INSTRUMENTATION_OUTPUT="$("${ADB_DEVICE[@]}" shell am instrument -w -r \
  com.centralbrain.demo.test/com.centralbrain.demo.test.CentralBrainBinderInstrumentation 2>&1)"
INSTRUMENTATION_STATUS=$?
set -e
printf '%s\n' "$INSTRUMENTATION_OUTPUT"
if [[ $INSTRUMENTATION_STATUS -ne 0 ]] \
    || grep -Fq "R2C Binder instrumentation failed" <<<"$INSTRUMENTATION_OUTPUT" \
    || ! grep -Fq "binder_service_death_verified=true" <<<"$INSTRUMENTATION_OUTPUT" \
    || ! grep -Fq "binder_reconnect_verified=true" <<<"$INSTRUMENTATION_OUTPUT" \
    || ! grep -Fq "binder_terminal_uniqueness_verified=true" <<<"$INSTRUMENTATION_OUTPUT" \
    || ! grep -Fq "binder_cancel_completion_race_verified=true" <<<"$INSTRUMENTATION_OUTPUT"; then
  echo "R2C Binder instrumentation did not pass" >&2
  exit 1
fi

"${ADB_DEVICE[@]}" shell am force-stop com.centralbrain.demo
"${ADB_DEVICE[@]}" shell am force-stop com.centralbrain.runtime
RUNTIME_PROBE_START="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.RuntimeProbeActivity)"
if ! grep -Fq "Status: ok" <<<"$RUNTIME_PROBE_START"; then
  echo "$RUNTIME_PROBE_START" >&2
  echo "Runtime lifecycle probe did not start before client death test" >&2
  exit 1
fi
"${ADB_DEVICE[@]}" logcat -c

NONCE="$(date +%s%N)"
PROBE_START="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.demo/.ClientDeathProbeActivity \
  --es nonce "$NONCE")"
if ! grep -Fq "Status: ok" <<<"$PROBE_START"; then
  echo "$PROBE_START" >&2
  echo "client death probe did not start" >&2
  exit 1
fi

TASK_ID=""
for _ in {1..40}; do
  PROBE_LOG="$("${ADB_DEVICE[@]}" logcat -d \
    -s CentralBrainDeathProbe:I CentralBrainRuntime:I '*:S')"
  SUBMITTED_LINE="$(grep -F \
    "nonce=$NONCE client_death_probe_submitted=true" <<<"$PROBE_LOG" | tail -n 1 || true)"
  if [[ -n "$SUBMITTED_LINE" ]]; then
    TASK_ID="$(sed -n 's/.*taskId=\([^ ]*\).*/\1/p' <<<"$SUBMITTED_LINE")"
    break
  fi
  sleep 0.05
done
if [[ -z "$TASK_ID" ]]; then
  echo "client death probe did not submit a task" >&2
  exit 1
fi

"${ADB_DEVICE[@]}" shell am force-stop com.centralbrain.demo
CLIENT_DEATH_VERIFIED=false
for _ in {1..40}; do
  RUNTIME_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CentralBrainRuntime:I '*:S')"
  if grep -Fq "cancelled taskId=$TASK_ID reason=2" <<<"$RUNTIME_LOG" \
      && grep -Fq "callback died taskId=$TASK_ID" <<<"$RUNTIME_LOG"; then
    CLIENT_DEATH_VERIFIED=true
    break
  fi
  sleep 0.05
done
if [[ "$CLIENT_DEATH_VERIFIED" != true ]]; then
  echo "Runtime did not cancel the task after client process death: $TASK_ID" >&2
  exit 1
fi

printf '%s\n' \
  "device_serial=$SERIAL" \
  "android_api=$SDK" \
  "device_abi=$ABI" \
  "binder_service_death_verified=true" \
  "binder_reconnect_verified=true" \
  "binder_terminal_uniqueness_verified=true" \
  "binder_cancel_completion_race_verified=true" \
  "binder_client_death_verified=true" \
  "r2_binder_exit_criteria_met=true" \
  "hardware_accessed=false" \
  "driver_development_triggered=false" \
  "virtualization_development_triggered=false"
