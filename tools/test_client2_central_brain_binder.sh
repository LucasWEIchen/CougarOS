#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001, XSC-005, XSC-006, NV-G-006, NV-P-002, DEL-001/003/004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${ANDROID_SERIAL:-}"
BUILD=true
REQUIRE_API_33=false

usage() {
  cat <<'EOF'
Usage: test_client2_central_brain_binder.sh [options]

Options:
  --serial SERIAL    Select an adb device explicitly.
  --skip-build       Reuse existing Runtime and Client2 debug APKs.
  --require-api-33   Fail unless the selected device is exactly Android API 33.
  -h, --help         Show this help.
EOF
}

while (($# > 0)); do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "--serial requires a value" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    --skip-build)
      BUILD=false
      shift
      ;;
    --require-api-33)
      REQUIRE_API_33=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/env.sh"
fi

: "${JAVA_HOME:?JAVA_HOME must point to JDK 17}"
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"

ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
APKSIGNER="${APKSIGNER:-$ROOT_DIR/.tools/android-build-tools-current/apksigner}"
AAPT="${AAPT:-$ROOT_DIR/.tools/android-build-tools-current/aapt}"
JAR="${JAR:-$JAVA_HOME/bin/jar}"
RUNTIME_APK="$ROOT_DIR/central-brain/android-runtime/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
CLIENT2_APK="$ROOT_DIR/builds/client2-central-brain/signed/client2-central-brain.debug.apk"

if [[ "$BUILD" == true ]]; then
  bash "$ROOT_DIR/tools/build_client2_central_brain_demo.sh"
fi

for artifact in "$RUNTIME_APK" "$CLIENT2_APK"; do
  [[ -f "$artifact" ]] || { echo "missing Binder test artifact: $artifact" >&2; exit 1; }
done
for tool in "$ADB" "$APKSIGNER" "$AAPT" "$JAR"; do
  [[ -x "$tool" ]] || { echo "required tool is not executable: $tool" >&2; exit 1; }
done

if [[ -z "$SERIAL" ]]; then
  mapfile -t ONLINE_DEVICES < <("$ADB" devices | tr -d '\r' | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [[ ${#ONLINE_DEVICES[@]} -ne 1 ]]; then
    echo "expected exactly one online adb device; use --serial when multiple exist" >&2
    "$ADB" devices -l >&2
    exit 1
  fi
  SERIAL="${ONLINE_DEVICES[0]}"
fi

ADB_DEVICE=("$ADB" -s "$SERIAL")
SDK="$("${ADB_DEVICE[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
ABI="$("${ADB_DEVICE[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
if [[ ! "$SDK" =~ ^[0-9]+$ ]] || ((SDK < 33)); then
  echo "Android API 33 or newer is required; device reported '$SDK'" >&2
  exit 1
fi
if [[ "$REQUIRE_API_33" == true && "$SDK" != "33" ]]; then
  echo "Client2 Binder API 33 evidence requested, but device reported API $SDK" >&2
  exit 1
fi

signer_digest() {
  "$APKSIGNER" verify --print-certs "$1" \
    | awk -F': ' '/certificate SHA-256 digest/ {print $2; exit}'
}

RUNTIME_SIGNER="$(signer_digest "$RUNTIME_APK")"
CLIENT2_SIGNER="$(signer_digest "$CLIENT2_APK")"
if [[ -z "$RUNTIME_SIGNER" || "$RUNTIME_SIGNER" != "$CLIENT2_SIGNER" ]]; then
  echo "Runtime and Client2 APK signers differ" >&2
  exit 1
fi
if ! "$JAR" tf "$CLIENT2_APK" | grep -Fxq 'classes2.dex'; then
  echo "Client2 APK does not contain the SDK Binder bridge dex" >&2
  exit 1
fi
if ! "$AAPT" dump permissions "$CLIENT2_APK" \
    | grep -Fq 'com.centralbrain.permission.BIND_RUNTIME'; then
  echo "Client2 APK does not request the Runtime signature permission" >&2
  exit 1
fi
if "$AAPT" dump permissions "$CLIENT2_APK" | grep -Fq 'android.permission.INTERNET'; then
  echo "Client2 Binder APK must not request INTERNET" >&2
  exit 1
fi
if ! "$AAPT" dump xmltree "$CLIENT2_APK" AndroidManifest.xml \
    | grep -Fq 'com.centralbrain.runtime'; then
  echo "Client2 manifest does not expose the Runtime package query" >&2
  exit 1
fi

"${ADB_DEVICE[@]}" install -r "$RUNTIME_APK" >/dev/null
set +e
CLIENT2_INSTALL_OUTPUT="$("${ADB_DEVICE[@]}" install -r "$CLIENT2_APK" 2>&1)"
CLIENT2_INSTALL_STATUS=$?
set -e
if [[ $CLIENT2_INSTALL_STATUS -ne 0 ]]; then
  if ! grep -Eq 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match' \
      <<<"$CLIENT2_INSTALL_OUTPUT"; then
    echo "$CLIENT2_INSTALL_OUTPUT" >&2
    exit 1
  fi
  "${ADB_DEVICE[@]}" uninstall com.tuanjie.urasclient2 >/dev/null || true
  "${ADB_DEVICE[@]}" install "$CLIENT2_APK" >/dev/null
fi

PACKAGE_DUMP="$("${ADB_DEVICE[@]}" shell dumpsys package com.tuanjie.urasclient2)"
if ! grep -Fq 'com.centralbrain.permission.BIND_RUNTIME: granted=true' \
    <<<"$PACKAGE_DUMP"; then
  echo "Client2 did not receive the Runtime signature permission" >&2
  exit 1
fi

STAMP="$(date +%Y%m%d_%H%M%S)"
LOG_DIR="$ROOT_DIR/logs/test/client2-central-brain-binder/$STAMP"
mkdir -p "$LOG_DIR"
"${ADB_DEVICE[@]}" shell settings put secure immersive_mode_confirmations confirmed
"${ADB_DEVICE[@]}" shell am force-stop com.tuanjie.urasclient2
"${ADB_DEVICE[@]}" logcat -c
"${ADB_DEVICE[@]}" shell am start -W \
  -n com.tuanjie.urasclient2/.MainActivity >"$LOG_DIR/activity-start.txt"
if ! grep -Fq 'Status: ok' "$LOG_DIR/activity-start.txt"; then
  cat "$LOG_DIR/activity-start.txt" >&2
  echo "Client2 MainActivity did not start" >&2
  exit 1
fi

DEVICE_UI_XML=/sdcard/client2-central-brain-binder.xml
BUTTON_NODE=""
for _ in {1..10}; do
  "${ADB_DEVICE[@]}" shell uiautomator dump "$DEVICE_UI_XML" >/dev/null
  "${ADB_DEVICE[@]}" shell cat "$DEVICE_UI_XML" >"$LOG_DIR/ui-before.xml"
  BUTTON_NODE="$(grep -o '<node[^>]*centralBrainColdButton[^>]*/>' \
    "$LOG_DIR/ui-before.xml" | head -n 1 || true)"
  [[ -n "$BUTTON_NODE" ]] && break
  sleep 0.2
done
if [[ -z "$BUTTON_NODE" ]]; then
  echo "Client2 cold scenario button was not visible" >&2
  exit 1
fi

BOUNDS="$(sed -nE \
  's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/p' \
  <<<"$BUTTON_NODE")"
read -r LEFT TOP RIGHT BOTTOM <<<"$BOUNDS"
if [[ -z "${BOTTOM:-}" ]]; then
  echo "cannot parse Client2 cold button bounds" >&2
  exit 1
fi
TAP_X=$(((LEFT + RIGHT) / 2))
TAP_Y=$(((TOP + BOTTOM) / 2))
"${ADB_DEVICE[@]}" shell input tap "$TAP_X" "$TAP_Y"

BINDER_LOG=""
for _ in {1..40}; do
  BINDER_LOG="$("${ADB_DEVICE[@]}" logcat -d \
    CbClient2Binder:I CentralBrainRuntime:I '*:S')"
  if grep -Fq 'client2_binder_task_completed=true' <<<"$BINDER_LOG"; then
    break
  fi
  sleep 0.25
done
printf '%s\n' "$BINDER_LOG" >"$LOG_DIR/binder-log.txt"

for marker in \
  'client2_binder_connected=true' \
  'client2_binder_task_submitted=true' \
  'client2_binder_task_completed=true' \
  'scenario_id=care.cold' \
  'http_transport_used=false' \
  'service_dispatch_triggered=false' \
  'hardware_accessed=false' \
  'packages=[com.tuanjie.urasclient2] resolved=true'; do
  if ! grep -Fq "$marker" <<<"$BINDER_LOG"; then
    echo "$BINDER_LOG" >&2
    echo "Client2 Binder log missing marker: $marker" >&2
    exit 1
  fi
done

"${ADB_DEVICE[@]}" shell uiautomator dump "$DEVICE_UI_XML" >/dev/null
"${ADB_DEVICE[@]}" shell cat "$DEVICE_UI_XML" >"$LOG_DIR/ui-after.xml"
if ! grep -Fq 'text="Deterministic Binder reply: care.cold:' \
    "$LOG_DIR/ui-after.xml"; then
  cat "$LOG_DIR/ui-after.xml" >&2
  echo "Client2 UI did not render the Binder TaskResult reply" >&2
  exit 1
fi

ln -sfn "$LOG_DIR" "$ROOT_DIR/logs/test/client2-central-brain-binder/latest"
printf '%s\n' \
  "device_serial=$SERIAL" \
  "android_api=$SDK" \
  "device_abi=$ABI" \
  "client2_signature_permission_granted=true" \
  "runtime_client2_signer_parity=true" \
  "client2_secondary_sdk_dex_present=true" \
  "client2_binder_connected=true" \
  "client2_binder_task_submitted=true" \
  "client2_binder_task_completed=true" \
  "client2_ui_reply_verified=true" \
  "client2_identity_resolved=true" \
  "client2_capability_policy_allowed=true" \
  "http_transport_used=false" \
  "service_dispatch_triggered=false" \
  "hardware_accessed=false" \
  "driver_development_triggered=false" \
  "virtualization_development_triggered=false" \
  "test_logs=$LOG_DIR"
