#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/env.sh"

APP_DIR="$ROOT_DIR/central-brain/android-console"
BINDING_DIR="$ROOT_DIR/central-brain/bindings/android"
OUT_DIR="$APP_DIR/out"
ANDROID_JAR="$ANDROID_HOME/platforms/android-36/android.jar"
KEYSTORE="$ROOT_DIR/keystores/debug.keystore"
APK_BASE="$OUT_DIR/central-brain-console.base.apk"
APK_ALIGNED="$OUT_DIR/central-brain-console.aligned.apk"
APK_SIGNED="$OUT_DIR/central-brain-console.debug.apk"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/res" "$OUT_DIR/generated" "$OUT_DIR/generated-aidl" "$OUT_DIR/classes" "$OUT_DIR/dex"

if [[ ! -f "$ANDROID_JAR" ]]; then
  echo "Missing Android platform jar: $ANDROID_JAR" >&2
  exit 1
fi

aapt2 compile --dir "$APP_DIR/res" -o "$OUT_DIR/res.zip"
aapt2 link \
  -I "$ANDROID_JAR" \
  --manifest "$APP_DIR/AndroidManifest.xml" \
  --java "$OUT_DIR/generated" \
  --min-sdk-version 24 \
  --target-sdk-version 36 \
  -o "$APK_BASE" \
  "$OUT_DIR/res.zip"

AIDL_BIN="$(command -v aidl || find "$ANDROID_HOME" -name aidl -type f 2>/dev/null | head -1)"
if [[ -z "$AIDL_BIN" ]]; then
  echo "Missing Android aidl compiler in PATH or ANDROID_HOME" >&2
  exit 1
fi

"$AIDL_BIN" \
  -I"$BINDING_DIR/aidl" \
  -o"$OUT_DIR/generated-aidl" \
  "$BINDING_DIR/aidl/com/centralbrain/binding/ICentralBrainGateway.aidl"

find "$APP_DIR/src" "$OUT_DIR/generated" "$OUT_DIR/generated-aidl" "$BINDING_DIR/java" -name "*.java" | sort > "$OUT_DIR/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$ANDROID_JAR" \
  -d "$OUT_DIR/classes" \
  @"$OUT_DIR/sources.txt"

jar cf "$OUT_DIR/classes.jar" -C "$OUT_DIR/classes" .
d8 --lib "$ANDROID_JAR" --output "$OUT_DIR/dex" "$OUT_DIR/classes.jar"

jar uf "$APK_BASE" -C "$OUT_DIR/dex" classes.dex

if [[ ! -f "$KEYSTORE" ]]; then
  bash "$ROOT_DIR/tools/create_debug_keystore.sh"
fi

zipalign -p -f 4 "$APK_BASE" "$APK_ALIGNED"
apksigner sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$APK_SIGNED" \
  "$APK_ALIGNED"
apksigner verify --verbose "$APK_SIGNED"

echo "$APK_SIGNED"
