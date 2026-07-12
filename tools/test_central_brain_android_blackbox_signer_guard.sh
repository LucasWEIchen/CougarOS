#!/usr/bin/env bash
set -euo pipefail

# Req IDs: XSC-004/005, NV-G-003/006, NV-P-002, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/central-brain/android-runtime"
SERIAL="${ANDROID_SERIAL:-}"

usage() {
  cat <<'EOF'
Usage: test_central_brain_android_blackbox_signer_guard.sh [--serial SERIAL]

Creates temporary differently signed APK copies and proves that read-only preflight
rejects an existing-package signer mismatch before any installation command.
EOF
}

while (($# > 0)); do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "--serial requires a value" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unknown option: $1" >&2
      exit 2
      ;;
  esac
done

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  source "$ROOT_DIR/env.sh"
fi
: "${JAVA_HOME:?JAVA_HOME must point to JDK 17}"
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"
export PATH="$JAVA_HOME/bin:$PATH"

ADB="$ANDROID_HOME/platform-tools/adb"
APKSIGNER="$ANDROID_HOME/build-tools/37.0.0/apksigner"
KEYTOOL="$JAVA_HOME/bin/keytool"
for tool in "$ADB" "$APKSIGNER" "$KEYTOOL"; do
  [[ -x "$tool" ]] || { echo "missing signer-guard tool: $tool" >&2; exit 1; }
done
if [[ -z "$SERIAL" ]]; then
  mapfile -t DEVICES < <("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  [[ ${#DEVICES[@]} -eq 1 ]] \
    || { echo "expected exactly one online adb device; use --serial" >&2; exit 1; }
  SERIAL="${DEVICES[0]}"
fi

RUNTIME_APK="$RUNTIME_DIR/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
DEMO_APK="$RUNTIME_DIR/demo-hmi/build/outputs/apk/debug/demo-hmi-debug.apk"
for artifact in "$RUNTIME_APK" "$DEMO_APK"; do
  [[ -f "$artifact" ]] || { echo "missing signer-guard artifact: $artifact" >&2; exit 1; }
done

TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT
cp "$RUNTIME_APK" "$TEMP_DIR/runtime.apk"
cp "$DEMO_APK" "$TEMP_DIR/demo.apk"
"$KEYTOOL" -genkeypair -noprompt \
  -keystore "$TEMP_DIR/negative.jks" \
  -storepass changeit \
  -keypass changeit \
  -alias b3negative \
  -dname "CN=Central Brain B3 Negative" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 2 >/dev/null 2>&1
for artifact in "$TEMP_DIR/runtime.apk" "$TEMP_DIR/demo.apk"; do
  "$APKSIGNER" sign \
    --ks "$TEMP_DIR/negative.jks" \
    --ks-pass pass:changeit \
    --key-pass pass:changeit \
    "$artifact"
done

set +e
OUTPUT="$(bash "$ROOT_DIR/tools/preflight_central_brain_android13_blackbox.sh" \
  --serial "$SERIAL" \
  --require-api-33 \
  --runtime-apk "$TEMP_DIR/runtime.apk" \
  --demo-apk "$TEMP_DIR/demo.apk" 2>&1)"
STATUS=$?
set -e

[[ "$STATUS" -ne 0 ]] \
  || { echo "signer mismatch unexpectedly passed preflight" >&2; exit 1; }
grep -Fq "existing signer does not match delivered APK" <<<"$OUTPUT" \
  || { echo "signer mismatch failed for an unexpected reason" >&2; exit 1; }

printf '%s\n' \
  "blackbox_existing_signer_mismatch_rejected=true" \
  "blackbox_preflight_failed_before_install=true" \
  "blackbox_signer_negative_device_mutation_performed=false"
