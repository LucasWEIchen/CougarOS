#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-007, S2-HMI-008/012, S2-MDL-002/007, S2-OBS-002.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="$ROOT_DIR/apk-labs/client2-central-brain"
INPUT="$PROJECT/bridge/src/com/centralbrain/client2/CockpitMultimodalInput.java"
TEST_MAIN="$PROJECT/bridge/test/com/centralbrain/client2/CockpitMultimodalInputDatasetTestMain.java"
SDK_AAR="$ROOT_DIR/central-brain/android-runtime/central-brain-sdk/build/outputs/aar/central-brain-sdk-debug.aar"
WORK_DIR="${CLIENT2_CB_WORK_DIR:-$ROOT_DIR/builds/client2-central-brain/workdir}"
SIGNED_APK="$ROOT_DIR/builds/client2-central-brain/signed/client2-central-brain.debug.apk"
BUILD_ROOT="$ROOT_DIR/builds/client2-central-brain/smoking-dataset-test"

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/env.sh"
fi
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"
ANDROID_JAR="$ANDROID_HOME/platforms/android-36/android.jar"

for path in "$INPUT" "$TEST_MAIN" "$ANDROID_JAR"; do
  test -f "$path"
done
if [[ ! -f "$SDK_AAR" ]]; then
  "$ROOT_DIR/central-brain/android-runtime/gradlew" \
    -p "$ROOT_DIR/central-brain/android-runtime" \
    :central-brain-sdk:assembleDebug >/dev/null
fi

check_dataset() {
  local category="$1"
  local source_dir="$2"
  local tracked_count frame_count frame
  tracked_count="$(git -C "$ROOT_DIR" ls-files "$source_dir/*.jpg" | wc -l)"
  frame_count="$(find "$ROOT_DIR/$source_dir" -maxdepth 1 -type f -name '[0-9][0-9][0-9].jpg' | wc -l)"
  if [[ "$tracked_count" -ne 100 || "$frame_count" -ne 100 ]]; then
    echo "$category dataset must contain exactly 100 tracked JPEG frames" >&2
    exit 1
  fi
  while IFS= read -r frame; do
    if ! file -b "$frame" | rg -Fq '1920x1080'; then
      echo "dataset frame is not exactly 1920x1080: $frame" >&2
      exit 1
    fi
  done < <(find "$ROOT_DIR/$source_dir" -maxdepth 1 -type f -name '*.jpg' | sort)
}

check_dataset positive passenger_smoking_100
check_dataset negative passenger_unbelted_nonsmoking_100

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
  "$INPUT" "$TEST_MAIN"
java \
  -classpath "$ANDROID_JAR:$SDK_CLASSES:$BUILD_DIR/classes" \
  com.centralbrain.client2.CockpitMultimodalInputDatasetTestMain

if [[ -d "$WORK_DIR/res/raw" ]]; then
  test "$(find "$WORK_DIR/res/raw" -maxdepth 1 -type f \
    -name 'central_brain_smoking_positive_*.jpg' | wc -l)" -eq 100
  test "$(find "$WORK_DIR/res/raw" -maxdepth 1 -type f \
    -name 'central_brain_smoking_negative_*.jpg' | wc -l)" -eq 100
fi

if [[ -f "$SIGNED_APK" ]]; then
  test "$(jar tf "$SIGNED_APK" \
    | rg -c '^res/raw/central_brain_smoking_positive_[0-9]{3}\.jpg$')" -eq 100
  test "$(jar tf "$SIGNED_APK" \
    | rg -c '^res/raw/central_brain_smoking_negative_[0-9]{3}\.jpg$')" -eq 100
fi

printf '%s\n' \
  'smoking_dataset_bundled_frames=200' \
  'smoking_dataset_resolution=1920x1080' \
  'smoking_dataset_selection=random_per_trigger' \
  'smoking_dataset_ground_truth_sent_to_model=false'
