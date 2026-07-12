#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/central-brain/android-runtime"

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # Local workspace toolchain; target integrators may provide the same vars externally.
  source "$ROOT_DIR/env.sh"
fi

: "${JAVA_HOME:?JAVA_HOME must point to JDK 17}"
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"

export ANDROID_HOME
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.tools/gradle-home}"

"$RUNTIME_DIR/gradlew" \
  --project-dir "$RUNTIME_DIR" \
  --no-daemon \
  --stacktrace \
  :native-runtime:testDebugUnitTest \
  :native-runtime:assembleDebug \
  :central-brain-sdk:testDebugUnitTest \
  :runtime-service:testDebugUnitTest \
  :central-brain-sdk:assembleDebug \
  :runtime-service:assembleDebug \
  :demo-hmi:assembleDebug

bash "$ROOT_DIR/tools/verify_central_brain_native_runtime_aar.sh"
bash "$ROOT_DIR/tools/verify_central_brain_native_runtime_apk.sh"

printf '%s\n' \
  "$RUNTIME_DIR/native-runtime/build/outputs/aar/native-runtime-debug.aar" \
  "$RUNTIME_DIR/central-brain-sdk/build/outputs/aar/central-brain-sdk-debug.aar" \
  "$RUNTIME_DIR/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk" \
  "$RUNTIME_DIR/demo-hmi/build/outputs/apk/debug/demo-hmi-debug.apk"
