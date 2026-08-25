#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PROJECT_DIR="$ROOT_DIR/apk-labs/tuanjie-client-render-quality"
WORK_DIR="${TUANJIE_CLIENT_WORK_DIR:-$ROOT_DIR/builds/client-render-quality/workdir}"
OUT_ROOT="$ROOT_DIR/builds/client-render-quality"
UNSIGNED_APK="$OUT_ROOT/unsigned/client-render-quality.unsigned.apk"
ALIGNED_APK="$OUT_ROOT/aligned/client-render-quality.aligned.apk"
SIGNED_APK="$OUT_ROOT/signed/client-render-quality.debug.apk"

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/env.sh"
fi

DEFAULT_ANDROID_USER_HOME="${ANDROID_USER_HOME:-$HOME/.android}"
KEYSTORE="${CENTRAL_BRAIN_ANDROID_DEBUG_KEYSTORE:-$DEFAULT_ANDROID_USER_HOME/debug.keystore}"
test -f "$KEYSTORE"
mkdir -p "$(dirname "$UNSIGNED_APK")" "$(dirname "$ALIGNED_APK")" "$(dirname "$SIGNED_APK")"

"$PROJECT_DIR/scripts/prepare_client_workspace.sh"
rm -f "$UNSIGNED_APK" "$ALIGNED_APK" "$SIGNED_APK"
apktool b "$WORK_DIR" -o "$UNSIGNED_APK" >/dev/null
zipalign -p -f 4 "$UNSIGNED_APK" "$ALIGNED_APK"
apksigner sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$SIGNED_APK" \
  "$ALIGNED_APK"
apksigner verify --verbose --print-certs "$SIGNED_APK" >/dev/null
aapt dump badging "$SIGNED_APK" | rg -q "package: name='com.tuanjie.urasclient'"
"$PROJECT_DIR/scripts/verify_project.sh"

printf '%s\n' \
  'client_surface_target=1920x1080' \
  'client_render_scale=1.25' \
  'client_internal_render_target=2400x1350' \
  "signed APK: $SIGNED_APK"
