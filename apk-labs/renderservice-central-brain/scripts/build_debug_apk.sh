#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PROJECT_DIR="$ROOT_DIR/apk-labs/renderservice-central-brain"
SOURCE_APK="${CENTRAL_BRAIN_RENDERSERVICE_SOURCE_APK:-$ROOT_DIR/apks/original/service_20260306_171242.apk}"
OUT_ROOT="$ROOT_DIR/builds/renderservice-central-brain"
UNALIGNED_APK="$OUT_ROOT/unsigned/renderservice-central-brain.unaligned.apk"
ALIGNED_APK="$OUT_ROOT/aligned/renderservice-central-brain.aligned.apk"
SIGNED_APK="$OUT_ROOT/signed/renderservice-central-brain.debug.apk"

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/env.sh"
fi

DEFAULT_ANDROID_USER_HOME="${ANDROID_USER_HOME:-$HOME/.android}"
KEYSTORE="${CENTRAL_BRAIN_ANDROID_DEBUG_KEYSTORE:-$DEFAULT_ANDROID_USER_HOME/debug.keystore}"
if [[ ! -f "$SOURCE_APK" ]]; then
  echo "Missing original RenderService APK: $SOURCE_APK" >&2
  exit 1
fi
if [[ ! -f "$KEYSTORE" ]]; then
  echo "Missing Android debug keystore: $KEYSTORE" >&2
  exit 1
fi

mkdir -p "$(dirname "$UNALIGNED_APK")" "$(dirname "$ALIGNED_APK")" \
  "$(dirname "$SIGNED_APK")"
rm -f "$UNALIGNED_APK" "$ALIGNED_APK" "$SIGNED_APK"

python "$PROJECT_DIR/scripts/build_unaligned_apk.py" \
  --source-apk "$SOURCE_APK" \
  --output-apk "$UNALIGNED_APK"
zipalign -p -f 4 "$UNALIGNED_APK" "$ALIGNED_APK"
apksigner sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$SIGNED_APK" \
  "$ALIGNED_APK"
apksigner verify --verbose --print-certs "$SIGNED_APK" >/dev/null
aapt dump badging "$SIGNED_APK" \
  | rg -q "package: name='com.tuanjie.renderservice'"

"$PROJECT_DIR/scripts/verify_project.sh"
echo "signed APK: $SIGNED_APK"
