#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/central-brain/android-runtime"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android runtime Gradle file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in \
  central-brain/android-runtime/settings.gradle.kts \
  central-brain/android-runtime/build.gradle.kts \
  central-brain/android-runtime/gradle.properties \
  central-brain/android-runtime/gradle/libs.versions.toml \
  central-brain/android-runtime/gradle/wrapper/gradle-wrapper.jar \
  central-brain/android-runtime/gradle/wrapper/gradle-wrapper.properties \
  central-brain/android-runtime/gradlew \
  central-brain/android-runtime/central-brain-sdk/build.gradle.kts \
  central-brain/android-runtime/central-brain-sdk/src/main/AndroidManifest.xml \
  central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/CentralBrainSdk.java \
  central-brain/android-runtime/runtime-service/build.gradle.kts \
  central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml \
  central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java \
  central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml \
  central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/RuntimeProbeActivity.java \
  central-brain/android-runtime/demo-hmi/build.gradle.kts \
  central-brain/android-runtime/demo-hmi/src/main/AndroidManifest.xml \
  central-brain/android-runtime/demo-hmi/src/main/java/com/centralbrain/demo/DemoActivity.java \
  central-brain/android-runtime/README.md \
  tools/build_central_brain_android_runtime.sh \
  tools/install_central_brain_android_runtime.sh; do
  require_file "$path"
done

for module in central-brain-sdk runtime-service demo-hmi; do
  require_text "central-brain/android-runtime/settings.gradle.kts" "include(\":$module\")"
done

require_text "central-brain/android-runtime/gradle/libs.versions.toml" 'agp = "8.10.1"'
require_text "central-brain/android-runtime/gradle/wrapper/gradle-wrapper.properties" "gradle-8.11.1-bin.zip"
require_text "central-brain/android-runtime/gradle/wrapper/gradle-wrapper.properties" "distributionSha256Sum=f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6"
for module in central-brain-sdk runtime-service demo-hmi; do
  require_text "central-brain/android-runtime/$module/build.gradle.kts" "minSdk = 33"
done
require_text "central-brain/android-runtime/runtime-service/build.gradle.kts" 'applicationId = "com.centralbrain.runtime"'
require_text "central-brain/android-runtime/demo-hmi/build.gradle.kts" 'applicationId = "com.centralbrain.demo"'
require_text "central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml" 'android:exported="false"'
require_text "central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java" "R2 introduces the typed production and diagnostic Binder surfaces."
require_text "central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java" "hardware_accessed=false"
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" 'android:permission="android.permission.DUMP"'
require_text "central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml" 'android:exported="true"'
require_text "central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/RuntimeProbeActivity.java" "BuildConfig.DEBUG"
require_text "tools/install_central_brain_android_runtime.sh" "--require-api-33"
require_text "tools/install_central_brain_android_runtime.sh" "r1_api33_exit_criteria_met"
require_text "central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/CentralBrainSdk.java" 'MATURITY = "contract_defined"'
require_text "central-brain/android-runtime/README.md" "central_brain_api33_x86_64"
require_text "central-brain/android-runtime/README.md" 'overall Runtime remains `contract_defined`'
require_text "central-brain/android-runtime/README.md" "command-line tools understand SDK XML up to version 3"

if find "$RUNTIME_DIR" -type f -path '*/src/main/aidl/*' -print -quit | grep -q .; then
  echo "R1 must not introduce AIDL before the R2 contract increment" >&2
  exit 1
fi

if grep -R -Fq "android.permission.INTERNET" "$RUNTIME_DIR"; then
  echo "R1 Android runtime modules must not request network access" >&2
  exit 1
fi

if grep -Fq "RuntimeProbeActivity" "$RUNTIME_DIR/runtime-service/src/main/AndroidManifest.xml"; then
  echo "the ADB lifecycle probe must remain debug-only" >&2
  exit 1
fi

echo "Central Brain Android runtime Gradle foundation check passed"
