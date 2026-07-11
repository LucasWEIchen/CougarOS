#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PROJECT_DIR="$ROOT_DIR/apk-labs/client2-central-brain"
WORK_DIR="${CLIENT2_CB_WORK_DIR:-$ROOT_DIR/builds/client2-central-brain/workdir}"
OUT_ROOT="$ROOT_DIR/builds/client2-central-brain"
UNSIGNED_DIR="$OUT_ROOT/unsigned"
ALIGNED_DIR="$OUT_ROOT/aligned"
SIGNED_DIR="$OUT_ROOT/signed"
STAMP="$(date +%Y%m%d_%H%M%S)"
LOG_DIR="$ROOT_DIR/logs/test/client2-central-brain/$STAMP"

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/env.sh"
fi

mkdir -p "$UNSIGNED_DIR" "$ALIGNED_DIR" "$SIGNED_DIR" "$LOG_DIR"

"$PROJECT_DIR/scripts/prepare_workspace.sh" > "$LOG_DIR/prepare.log" 2>&1

KEYSTORE="$("$ROOT_DIR/tools/create_debug_keystore.sh")"
UNSIGNED_APK="$UNSIGNED_DIR/client2-central-brain.unsigned.apk"
ALIGNED_APK="$ALIGNED_DIR/client2-central-brain.aligned.apk"
SIGNED_APK="$SIGNED_DIR/client2-central-brain.debug.apk"

rm -f "$UNSIGNED_APK" "$ALIGNED_APK" "$SIGNED_APK"

apktool b "$WORK_DIR" -o "$UNSIGNED_APK" > "$LOG_DIR/apktool-build.log" 2>&1
zipalign -p -f 4 "$UNSIGNED_APK" "$ALIGNED_APK" > "$LOG_DIR/zipalign.log" 2>&1
apksigner sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$SIGNED_APK" \
  "$ALIGNED_APK" > "$LOG_DIR/apksigner-sign.log" 2>&1
apksigner verify --verbose --print-certs "$SIGNED_APK" > "$LOG_DIR/apksigner-verify.log" 2>&1
aapt dump badging "$SIGNED_APK" > "$LOG_DIR/aapt-badging.log" 2>&1

ln -sfn "$LOG_DIR" "$ROOT_DIR/logs/test/client2-central-brain/latest"
echo "signed APK: $SIGNED_APK"
echo "build logs: $LOG_DIR"
