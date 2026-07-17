#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-UX-001..003, S2-HMI-001..006, S2-SCN-001, APP-004, XSC-001/005/006.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="$ROOT_DIR/apk-labs/client2-central-brain"
STATE="$PROJECT/bridge/src/com/centralbrain/client2/CockpitHmiState.java"
REDUCER="$PROJECT/bridge/src/com/centralbrain/client2/CockpitHmiReducer.java"
HVAC_STATE="$PROJECT/bridge/src/com/centralbrain/client2/CockpitHvacState.java"
HVAC_INTENT="$PROJECT/bridge/src/com/centralbrain/client2/HvacControlIntent.java"
SEAT_STATE="$PROJECT/bridge/src/com/centralbrain/client2/CockpitSeatState.java"
SEAT_INTENT="$PROJECT/bridge/src/com/centralbrain/client2/SeatControlIntent.java"
EXECUTION_TIMELINE="$PROJECT/bridge/src/com/centralbrain/client2/CockpitExecutionTimeline.java"
RECOVERY_STATE="$PROJECT/bridge/src/com/centralbrain/client2/CockpitRecoveryState.java"
PRESENTATION_MODE="$PROJECT/bridge/src/com/centralbrain/client2/PanelPresentationMode.java"
DRIVING_UX_POLICY="$PROJECT/bridge/src/com/centralbrain/client2/DrivingUxPolicy.java"
ENGINEER_STATE="$PROJECT/bridge/src/com/centralbrain/client2/CockpitEngineerState.java"
SCENARIO_CONTROL="$PROJECT/bridge/src/com/centralbrain/client2/CockpitScenarioControlState.java"
DISPLAY_POLICY="$PROJECT/bridge/src/com/centralbrain/client2/CockpitDisplayPolicy.java"
COORDINATOR="$PROJECT/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
TEST_MAIN="$PROJECT/bridge/test/com/centralbrain/client2/CockpitHmiReducerTestMain.java"
SDK_AAR="$ROOT_DIR/central-brain/android-runtime/central-brain-sdk/build/outputs/aar/central-brain-sdk-debug.aar"
BUILD_ROOT="$ROOT_DIR/builds/client2-central-brain/hmi-reducer-test"

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/env.sh"
fi
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"
ANDROID_JAR="$ANDROID_HOME/platforms/android-36/android.jar"

for path in \
  "$STATE" "$REDUCER" "$HVAC_STATE" "$HVAC_INTENT" "$SEAT_STATE" "$SEAT_INTENT" \
  "$EXECUTION_TIMELINE" \
  "$RECOVERY_STATE" \
  "$PRESENTATION_MODE" "$DRIVING_UX_POLICY" \
  "$ENGINEER_STATE" \
  "$SCENARIO_CONTROL" "$DISPLAY_POLICY" \
  "$COORDINATOR" "$TEST_MAIN" "$ANDROID_JAR"; do
  test -f "$path"
done
if [[ ! -f "$SDK_AAR" ]]; then
  "$ROOT_DIR/central-brain/android-runtime/gradlew" \
    -p "$ROOT_DIR/central-brain/android-runtime" \
    :central-brain-sdk:assembleDebug >/dev/null
fi

if grep -Eq '^import android\.' \
    "$STATE" "$REDUCER" "$HVAC_STATE" "$HVAC_INTENT" "$SEAT_STATE" "$SEAT_INTENT" \
    "$EXECUTION_TIMELINE" "$RECOVERY_STATE" "$PRESENTATION_MODE" "$DRIVING_UX_POLICY" \
    "$ENGINEER_STATE" "$SCENARIO_CONTROL" "$DISPLAY_POLICY"; then
  echo "Cockpit HMI state/reducer must remain Android-view independent" >&2
  exit 1
fi
if grep -Eq 'putString\([^\n]*(summary|display|text|utterance|message)' "$COORDINATOR"; then
  echo "Cockpit HMI checkpoint must not persist user/model/display text" >&2
  exit 1
fi
if grep -Eq 'CentralBrainClient|AgentTaskRequest|submitAgentTask|onBridge(Status|Reply|Failure)' \
    "$COORDINATOR"; then
  echo "Cockpit HMI coordinator must consume typed Session/Event callbacks only" >&2
  exit 1
fi

mkdir -p "$BUILD_ROOT"
BUILD_DIR="$(mktemp -d "$BUILD_ROOT/run.XXXXXX")"
cleanup() {
  rm -rf "$BUILD_DIR"
}
trap cleanup EXIT
mkdir -p "$BUILD_DIR/aar" "$BUILD_DIR/classes"
(
  cd "$BUILD_DIR/aar"
  jar xf "$SDK_AAR" classes.jar
)
SDK_CLASSES="$BUILD_DIR/aar/classes.jar"

javac \
  -source 17 \
  -target 17 \
  -encoding UTF-8 \
  -classpath "$ANDROID_JAR:$SDK_CLASSES" \
  -d "$BUILD_DIR/classes" \
  "$HVAC_INTENT" "$HVAC_STATE" "$SEAT_INTENT" "$SEAT_STATE" \
  "$EXECUTION_TIMELINE" "$RECOVERY_STATE" "$PRESENTATION_MODE" "$DRIVING_UX_POLICY" \
  "$ENGINEER_STATE" \
  "$SCENARIO_CONTROL" "$DISPLAY_POLICY" "$STATE" "$REDUCER" "$TEST_MAIN"

java \
  -classpath "$ANDROID_JAR:$SDK_CLASSES:$BUILD_DIR/classes" \
  com.centralbrain.client2.CockpitHmiReducerTestMain

echo "Central Brain Android Client2 cockpit HMI reducer check passed"
