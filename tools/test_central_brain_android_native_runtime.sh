#!/usr/bin/env bash
set -euo pipefail

# Req IDs: XSC-004/005/006, NV-F-001/011, NV-G-003/006/007,
# NV-P-002, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_APK="$ROOT_DIR/central-brain/android-runtime/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
SERIAL="${ANDROID_SERIAL:-}"
BUILD=true
REQUIRE_API_33=false

usage() {
  cat <<'EOF'
Usage: test_central_brain_android_native_runtime.sh [options]

Options:
  --serial SERIAL    Select an adb device explicitly.
  --skip-build       Reuse the existing Runtime debug APK.
  --require-api-33   Fail unless the selected device is exactly API 33.
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
  source "$ROOT_DIR/env.sh"
fi
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"
ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
[[ -x "$ADB" ]] || { echo "adb is not executable: $ADB" >&2; exit 1; }

if [[ "$BUILD" == true ]]; then
  bash "$ROOT_DIR/tools/build_central_brain_android_runtime.sh"
fi
bash "$ROOT_DIR/tools/verify_central_brain_native_runtime_apk.sh" "$RUNTIME_APK"

if [[ -z "$SERIAL" ]]; then
  mapfile -t devices < <("$ADB" devices | tr -d '\r' | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [[ ${#devices[@]} -ne 1 ]]; then
    echo "expected exactly one online adb device; use --serial" >&2
    exit 1
  fi
  SERIAL="${devices[0]}"
fi
ADB_DEVICE=("$ADB" -s "$SERIAL")
[[ "$("${ADB_DEVICE[@]}" get-state | tr -d '\r')" == "device" ]] \
  || { echo "adb device is not online: $SERIAL" >&2; exit 1; }

SDK="$("${ADB_DEVICE[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
ABI="$("${ADB_DEVICE[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
MODEL="$("${ADB_DEVICE[@]}" shell getprop ro.product.model | tr -d '\r')"
[[ "$SDK" =~ ^[0-9]+$ ]] && ((SDK >= 33)) \
  || { echo "Android API 33 or newer is required; found '$SDK'" >&2; exit 1; }
if [[ "$REQUIRE_API_33" == true && "$SDK" != "33" ]]; then
  echo "API 33 evidence requested; found API $SDK" >&2
  exit 1
fi
if [[ "$ABI" != "arm64-v8a" && "$ABI" != "x86_64" ]]; then
  echo "unsupported target ABI for B2: $ABI" >&2
  exit 1
fi

"${ADB_DEVICE[@]}" install -r "$RUNTIME_APK"
"${ADB_DEVICE[@]}" shell am force-stop com.centralbrain.runtime
"${ADB_DEVICE[@]}" logcat -c
"${ADB_DEVICE[@]}" logcat -b crash -c

run_native_probe() {
  local nonce="$1"
  local output log
  output="$("${ADB_DEVICE[@]}" shell am start -W \
    -n com.centralbrain.runtime/.nativebridge.NativeRuntimeProbeActivity \
    --es nonce "$nonce")"
  grep -Fq "Status: ok" <<<"$output" \
    || { echo "Native Runtime probe did not start" >&2; exit 1; }

  for _ in {1..40}; do
    log="$("${ADB_DEVICE[@]}" logcat -d -s CbNativeRuntimeProbe:I '*:S')"
    if grep -Fq "nonce=$nonce native_runtime_probe_complete=true" <<<"$log" \
        && grep -Fq "native_runtime_probe_passed=true" <<<"$log"; then
      for marker in \
        "native_runtime_process_ready=true" \
        "native_library_loaded=true" \
        "native_runtime_initialized=true" \
        "native_runtime_abi_version=1" \
        "native_runtime_process_generation=1" \
        "native_runtime_capacity_rejected=true" \
        "native_runtime_busy_close_rejected=true" \
        "native_runtime_duplicate_release_rejected=true" \
        "native_runtime_drained=true" \
        "native_runtime_close_verified=true" \
        "native_software_provider_available=false" \
        "native_vendor_npu_provider_available=false" \
        "native_runtime_dispatch_enabled=false" \
        "native_hardware_accessed=false" \
        "hardware_accessed=false"; do
        grep -Fq "$marker" <<<"$log" \
          || { echo "Native Runtime probe missing marker: $marker" >&2; exit 1; }
      done
      return
    fi
    sleep 0.25
  done
  echo "Native Runtime probe timed out" >&2
  exit 1
}

FIRST_NONCE="b2-native-$(date +%s%N)"
run_native_probe "$FIRST_NONCE"
FIRST_PID="$("${ADB_DEVICE[@]}" shell pidof com.centralbrain.runtime | tr -d '\r')"
[[ -n "$FIRST_PID" ]] || { echo "Runtime process was not created" >&2; exit 1; }

RUNTIME_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.RuntimeProbeActivity)"
grep -Fq "Status: ok" <<<"$RUNTIME_OUTPUT" \
  || { echo "Runtime service probe did not start" >&2; exit 1; }
RUNTIME_DUMP="$("${ADB_DEVICE[@]}" shell dumpsys activity service \
  com.centralbrain.runtime/.CentralBrainRuntimeService)"
for marker in \
  "native_runtime_process_wired=true" \
  "native_runtime_process_ready=true" \
  "native_runtime_process_lifecycle=READY" \
  "native_runtime_detail_code=READY" \
  "native_library_loaded=true" \
  "native_runtime_initialized=true" \
  "native_runtime_max_slots=4" \
  "native_runtime_active_slots=0" \
  "native_runtime_generation=1" \
  "native_runtime_last_status=OK" \
  "native_software_provider_available=false" \
  "native_vendor_npu_provider_available=false" \
  "native_runtime_dispatch_enabled=false" \
  "native_hardware_accessed=false"; do
  grep -Fq "$marker" <<<"$RUNTIME_DUMP" \
    || { echo "Runtime dumpsys missing native marker: $marker" >&2; exit 1; }
done

DIAGNOSTIC_NONCE="b2-diagnostic-$(date +%s%N)"
DIAGNOSTIC_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.DiagnosticProbeActivity \
  --es nonce "$DIAGNOSTIC_NONCE")"
grep -Fq "Status: ok" <<<"$DIAGNOSTIC_OUTPUT" \
  || { echo "Diagnostic probe did not start" >&2; exit 1; }
DIAGNOSTIC_PASSED=false
for _ in {1..40}; do
  DIAGNOSTIC_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CentralBrainDiagProbe:I '*:S')"
  if grep -Fq "nonce=$DIAGNOSTIC_NONCE diagnostic_probe_passed=true" \
      <<<"$DIAGNOSTIC_LOG" \
      && grep -Fq "native_runtime_diagnostic_verified=true" <<<"$DIAGNOSTIC_LOG"; then
    DIAGNOSTIC_PASSED=true
    break
  fi
  sleep 0.25
done
[[ "$DIAGNOSTIC_PASSED" == true ]] \
  || { echo "Diagnostic native Runtime parity failed" >&2; exit 1; }

"${ADB_DEVICE[@]}" shell am force-stop com.centralbrain.runtime
STOPPED_PID="$("${ADB_DEVICE[@]}" shell pidof com.centralbrain.runtime 2>/dev/null || true)"
[[ -z "$STOPPED_PID" ]] || { echo "Runtime process survived force-stop" >&2; exit 1; }

SECOND_NONCE="b2-native-restart-$(date +%s%N)"
run_native_probe "$SECOND_NONCE"
SECOND_PID="$("${ADB_DEVICE[@]}" shell pidof com.centralbrain.runtime | tr -d '\r')"
[[ -n "$SECOND_PID" && "$SECOND_PID" != "$FIRST_PID" ]] \
  || { echo "Runtime process restart PID evidence failed" >&2; exit 1; }

CRASH_LOG="$("${ADB_DEVICE[@]}" logcat -d -b crash)"
[[ -z "$CRASH_LOG" ]] || { echo "Android crash buffer is not empty" >&2; exit 1; }

printf '%s\n' \
  "device_serial=$SERIAL" \
  "device_model=$MODEL" \
  "android_api=$SDK" \
  "device_abi=$ABI" \
  "native_runtime_apk_verified=true" \
  "native_runtime_load_verified=true" \
  "native_runtime_lifecycle_verified=true" \
  "native_runtime_capacity_verified=true" \
  "native_runtime_busy_close_verified=true" \
  "native_runtime_duplicate_release_verified=true" \
  "native_runtime_dumpsys_verified=true" \
  "native_runtime_diagnostic_verified=true" \
  "native_runtime_process_recovery_verified=true" \
  "native_runtime_first_pid=$FIRST_PID" \
  "native_runtime_second_pid=$SECOND_PID" \
  "native_software_provider_available=false" \
  "native_vendor_npu_provider_available=false" \
  "native_runtime_dispatch_enabled=false" \
  "native_hardware_accessed=false" \
  "driver_development_triggered=false" \
  "virtualization_development_triggered=false"
