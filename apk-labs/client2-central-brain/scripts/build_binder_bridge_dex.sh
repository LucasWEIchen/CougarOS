#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PROJECT_DIR="$ROOT_DIR/apk-labs/client2-central-brain"
WORK_DIR="${CLIENT2_CB_WORK_DIR:-$ROOT_DIR/builds/client2-central-brain/workdir}"
BUILD_DIR="$ROOT_DIR/builds/client2-central-brain/bridge"
SDK_AAR="$ROOT_DIR/central-brain/android-runtime/central-brain-sdk/build/outputs/aar/central-brain-sdk-debug.aar"
SIM_AIDL_ROOT="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/debug/aidl"
SIM_AIDL="$SIM_AIDL_ROOT/com/centralbrain/runtime/simulation/IDebugSimulationController.aidl"

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/env.sh"
fi

: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"

ANDROID_JAR="$ANDROID_HOME/platforms/android-36/android.jar"
if [[ ! -f "$SDK_AAR" ]]; then
  echo "Missing Central Brain SDK AAR: $SDK_AAR" >&2
  exit 1
fi
if [[ ! -f "$ANDROID_JAR" ]]; then
  echo "Missing Android API jar: $ANDROID_JAR" >&2
  exit 1
fi
if [[ ! -d "$WORK_DIR" ]]; then
  echo "Missing Client2 generated workdir: $WORK_DIR" >&2
  exit 1
fi
: "${AIDL:=$(command -v aidl || true)}"
if [[ -z "$AIDL" || ! -x "$AIDL" ]]; then
  echo "Android aidl compiler is unavailable" >&2
  exit 1
fi
if [[ ! -f "$SIM_AIDL" ]]; then
  echo "Missing debug simulation AIDL: $SIM_AIDL" >&2
  exit 1
fi

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/aar" "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$BUILD_DIR/generated"
(
  cd "$BUILD_DIR/aar"
  jar xf "$SDK_AAR" classes.jar
)
SDK_CLASSES="$BUILD_DIR/aar/classes.jar"
if [[ ! -f "$SDK_CLASSES" ]]; then
  echo "SDK AAR does not contain classes.jar" >&2
  exit 1
fi

mapfile -t SOURCES < <(find "$PROJECT_DIR/bridge/src" -type f -name '*.java' -print | sort)
if [[ "${#SOURCES[@]}" -ne 15 ]]; then
  echo "Expected exactly fifteen Client2 HMI/Session/debug-control Java sources" >&2
  exit 1
fi
"$AIDL" --lang=java -I"$SIM_AIDL_ROOT" -o "$BUILD_DIR/generated" "$SIM_AIDL"
mapfile -t GENERATED_SOURCES < <(find "$BUILD_DIR/generated" -type f -name '*.java' -print | sort)
if [[ "${#GENERATED_SOURCES[@]}" -ne 1 ]]; then
  echo "Expected exactly one generated debug simulation Binder source" >&2
  exit 1
fi

javac \
  -source 17 \
  -target 17 \
  -encoding UTF-8 \
  -classpath "$ANDROID_JAR:$SDK_CLASSES" \
  -d "$BUILD_DIR/classes" \
  "${SOURCES[@]}" \
  "${GENERATED_SOURCES[@]}"
jar cf "$BUILD_DIR/client2-binder-bridge.jar" -C "$BUILD_DIR/classes" .

d8 \
  --min-api 33 \
  --lib "$ANDROID_JAR" \
  --output "$BUILD_DIR/dex" \
  "$SDK_CLASSES" \
  "$BUILD_DIR/client2-binder-bridge.jar"

if [[ ! -f "$BUILD_DIR/dex/classes.dex" ]]; then
  echo "D8 did not produce the Client2 Binder bridge dex" >&2
  exit 1
fi
mkdir -p "$WORK_DIR/unknown"
cp "$BUILD_DIR/dex/classes.dex" "$WORK_DIR/unknown/classes2.dex"

echo "Client2 Binder bridge dex: $WORK_DIR/unknown/classes2.dex"
