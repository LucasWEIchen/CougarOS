#!/usr/bin/env bash
set -euo pipefail

# Req IDs: XSC-004, XSC-005, XSC-006, NV-F-001, NV-P-002, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/central-brain/android-runtime"
SERIAL="${ANDROID_SERIAL:-}"
BUILD=true
REQUIRE_API_33=false

usage() {
  cat <<'EOF'
Usage: install_central_brain_android_runtime.sh [options]

Options:
  --serial SERIAL    Select an adb device explicitly.
  --skip-build       Reuse existing debug artifacts.
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
  # Local workspace toolchain; target integrators may provide these variables.
  source "$ROOT_DIR/env.sh"
fi

: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"

ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
if [[ ! -x "$ADB" ]]; then
  echo "adb not executable: $ADB" >&2
  exit 1
fi

if [[ "$BUILD" == true ]]; then
  "$ROOT_DIR/tools/build_central_brain_android_runtime.sh"
fi

RUNTIME_APK="$RUNTIME_DIR/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
DEMO_APK="$RUNTIME_DIR/demo-hmi/build/outputs/apk/debug/demo-hmi-debug.apk"
for artifact in "$RUNTIME_APK" "$DEMO_APK"; do
  if [[ ! -f "$artifact" ]]; then
    echo "missing Android runtime artifact: $artifact" >&2
    exit 1
  fi
done

if [[ -z "$SERIAL" ]]; then
  mapfile -t ONLINE_DEVICES < <("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [[ ${#ONLINE_DEVICES[@]} -ne 1 ]]; then
    echo "expected exactly one online adb device; use --serial when multiple exist" >&2
    "$ADB" devices -l >&2
    exit 1
  fi
  SERIAL="${ONLINE_DEVICES[0]}"
fi

ADB_DEVICE=("$ADB" -s "$SERIAL")
if [[ "$("${ADB_DEVICE[@]}" get-state)" != "device" ]]; then
  echo "adb device is not online: $SERIAL" >&2
  exit 1
fi

SDK="$("${ADB_DEVICE[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
ABI="$("${ADB_DEVICE[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
MODEL="$("${ADB_DEVICE[@]}" shell getprop ro.product.model | tr -d '\r')"
if [[ ! "$SDK" =~ ^[0-9]+$ ]] || ((SDK < 33)); then
  echo "Android API 33 or newer is required; device reported '$SDK'" >&2
  exit 1
fi
if [[ "$REQUIRE_API_33" == true && "$SDK" != "33" ]]; then
  echo "R1 API 33 exit evidence requested, but device reported API $SDK" >&2
  exit 1
fi

"${ADB_DEVICE[@]}" install -r "$RUNTIME_APK"
"${ADB_DEVICE[@]}" install -r "$DEMO_APK"
"${ADB_DEVICE[@]}" shell am force-stop com.centralbrain.runtime
"${ADB_DEVICE[@]}" shell am force-stop com.centralbrain.demo

PROBE_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W -n com.centralbrain.runtime/.RuntimeProbeActivity)"
if ! grep -Fq "Status: ok" <<<"$PROBE_OUTPUT"; then
  echo "$PROBE_OUTPUT" >&2
  echo "runtime debug probe did not start successfully" >&2
  exit 1
fi

sleep 1
SERVICE_DUMP="$("${ADB_DEVICE[@]}" shell dumpsys activity services com.centralbrain.runtime)"
if ! grep -Fq "com.centralbrain.runtime/.CentralBrainRuntimeService" <<<"$SERVICE_DUMP"; then
  echo "CentralBrainRuntimeService is not running" >&2
  exit 1
fi
RUNTIME_PID="$("${ADB_DEVICE[@]}" shell pidof com.centralbrain.runtime 2>/dev/null | tr -d '\r' || true)"
if [[ -z "$RUNTIME_PID" ]]; then
  echo "runtime-service process is not running" >&2
  exit 1
fi

DEMO_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W -n com.centralbrain.demo/.DemoActivity)"
if ! grep -Fq "Status: ok" <<<"$DEMO_OUTPUT"; then
  echo "$DEMO_OUTPUT" >&2
  echo "Demo HMI did not start successfully" >&2
  exit 1
fi

ACTIVITY_DUMP="$("${ADB_DEVICE[@]}" shell dumpsys activity activities)"
if ! grep -Eq 'mResumedActivity.*com\.centralbrain\.demo/.DemoActivity|topResumedActivity=.*com\.centralbrain\.demo/.DemoActivity' <<<"$ACTIVITY_DUMP"; then
  echo "DemoActivity is not the resumed activity" >&2
  exit 1
fi

"${ADB_DEVICE[@]}" shell uiautomator dump /sdcard/central-brain-demo.xml >/dev/null
UI_DUMP="$("${ADB_DEVICE[@]}" exec-out cat /sdcard/central-brain-demo.xml | tr -d '\r')"
for expected in "Central Brain" "contract_defined"; do
  if ! grep -Fq "$expected" <<<"$UI_DUMP"; then
    echo "Demo HMI UI missing expected text: $expected" >&2
    exit 1
  fi
done

API_33_EXIT=false
if [[ "$SDK" == "33" ]]; then
  API_33_EXIT=true
fi

printf '%s\n' \
  "device_serial=$SERIAL" \
  "device_model=$MODEL" \
  "android_api=$SDK" \
  "device_abi=$ABI" \
  "runtime_service_running=true" \
  "runtime_pid=$RUNTIME_PID" \
  "demo_hmi_resumed=true" \
  "demo_ui_contract_defined=true" \
  "r1_api33_exit_criteria_met=$API_33_EXIT" \
  "hardware_accessed=false" \
  "driver_development_triggered=false" \
  "virtualization_development_triggered=false"
