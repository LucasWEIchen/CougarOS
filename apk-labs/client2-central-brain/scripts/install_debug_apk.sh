#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SIGNED_APK="$ROOT_DIR/builds/client2-central-brain/signed/client2-central-brain.debug.apk"

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/env.sh"
fi

if [[ ! -f "$SIGNED_APK" ]]; then
  echo "Missing signed APK. Build it first with tools/build_client2_central_brain_demo.sh" >&2
  exit 1
fi

adb install -r "$SIGNED_APK"
echo "Installed: $SIGNED_APK"
