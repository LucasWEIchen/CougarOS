#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/env.sh"

APK="$ROOT_DIR/central-brain/android-console/out/central-brain-console.debug.apk"

if [[ ! -f "$APK" ]]; then
  bash "$ROOT_DIR/tools/build_central_brain_console.sh"
fi

adb install -r "$APK"
adb shell monkey -p com.centralbrain.console 1
