#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004/005/006, NV-F-001/011/012,
# NV-G-003/005/006/007, NV-P-002, KH-003/006, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/central-brain/android-runtime"
SERIAL="${ANDROID_SERIAL:-}"
BUILD=true
REPORT_DIR="$ROOT_DIR/builds/central-brain-blackbox-evidence"

usage() {
  cat <<'EOF'
Usage: test_central_brain_android_blackbox_acceptance.sh [options]

Options:
  --serial SERIAL    Select an adb device explicitly.
  --skip-build       Reuse existing debug artifacts.
  --report-dir PATH  Store pre/post-install reports under PATH.
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
    --report-dir)
      [[ $# -ge 2 ]] || { echo "--report-dir requires a value" >&2; exit 2; }
      REPORT_DIR="$2"
      shift 2
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
APKSIGNER="${APKSIGNER:-$ANDROID_HOME/build-tools/37.0.0/apksigner}"

if [[ "$BUILD" == true ]]; then
  bash "$ROOT_DIR/tools/build_central_brain_android_runtime.sh"
fi
RUNTIME_APK="$RUNTIME_DIR/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
DEMO_APK="$RUNTIME_DIR/demo-hmi/build/outputs/apk/debug/demo-hmi-debug.apk"

if [[ -z "$SERIAL" ]]; then
  mapfile -t DEVICES < <("$ADB" devices | tr -d '\r' | awk 'NR > 1 && $2 == "device" { print $1 }')
  [[ ${#DEVICES[@]} -eq 1 ]] \
    || { echo "expected exactly one online adb device; use --serial" >&2; exit 1; }
  SERIAL="${DEVICES[0]}"
fi
ADB_DEVICE=("$ADB" -s "$SERIAL")
[[ "$("${ADB_DEVICE[@]}" get-state | tr -d '\r')" == "device" ]] \
  || { echo "adb device is not online: $SERIAL" >&2; exit 1; }

mkdir -p "$REPORT_DIR"
PRE_REPORT="$REPORT_DIR/pre-install.properties"
POST_REPORT="$REPORT_DIR/post-install.properties"
INSTALL_REPORT="$REPORT_DIR/install-acceptance.properties"
NATIVE_REPORT="$REPORT_DIR/native-runtime-acceptance.properties"
SIGNER_NEGATIVE_REPORT="$REPORT_DIR/signer-negative.properties"

bash "$ROOT_DIR/tools/preflight_central_brain_android13_blackbox.sh" \
  --serial "$SERIAL" \
  --runtime-apk "$RUNTIME_APK" \
  --demo-apk "$DEMO_APK" \
  --report "$PRE_REPORT" \
  --require-api-33 >/dev/null

bash "$ROOT_DIR/tools/test_central_brain_android_blackbox_signer_guard.sh" \
  --serial "$SERIAL" >"$SIGNER_NEGATIVE_REPORT"

bash "$ROOT_DIR/tools/install_central_brain_android_runtime.sh" \
  --serial "$SERIAL" \
  --skip-build \
  --require-api-33 >"$INSTALL_REPORT"

bash "$ROOT_DIR/tools/test_central_brain_android_native_runtime.sh" \
  --serial "$SERIAL" \
  --skip-build \
  --require-api-33 >"$NATIVE_REPORT"

RECOVERY_DEMO_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.demo/.DemoActivity)"
grep -Fq "Status: ok" <<<"$RECOVERY_DEMO_OUTPUT" \
  || { echo "post-recovery Demo HMI did not start" >&2; exit 1; }

RECOVERY_UI_DUMP=""
RECOVERY_HMI_VERIFIED=false
for _ in {1..40}; do
  "${ADB_DEVICE[@]}" shell uiautomator dump \
    /sdcard/central-brain-demo-recovery.xml >/dev/null
  RECOVERY_UI_DUMP="$("${ADB_DEVICE[@]}" exec-out cat \
    /sdcard/central-brain-demo-recovery.xml | tr -d '\r')"
  if grep -Fq "Typed Binder: connected v1" <<<"$RECOVERY_UI_DUMP" \
      && grep -Fq "Governance: verified v1" <<<"$RECOVERY_UI_DUMP" \
      && ! grep -Fq "Typed Binder: disconnected" <<<"$RECOVERY_UI_DUMP" \
      && ! grep -Fq "Governance: disconnected" <<<"$RECOVERY_UI_DUMP"; then
    RECOVERY_HMI_VERIFIED=true
    break
  fi
  sleep 0.5
done
[[ "$RECOVERY_HMI_VERIFIED" == true ]] \
  || { echo "post-recovery Demo HMI did not refresh Binder status" >&2; exit 1; }

"${ADB_DEVICE[@]}" logcat -c
NONCE="b3-blackbox-$(date +%s%N)"
START_OUTPUT="$("${ADB_DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.blackbox.BlackBoxEnvironmentProbeActivity \
  --es nonce "$NONCE")"
grep -Fq "Status: ok" <<<"$START_OUTPUT" \
  || { echo "black-box Java probe did not start" >&2; exit 1; }

PROBE_PASSED=false
PROBE_LOG=""
for _ in {1..40}; do
  PROBE_LOG="$("${ADB_DEVICE[@]}" logcat -d -s CbBlackBoxProbe:I '*:S')"
  if grep -Fq "nonce=$NONCE blackbox_probe_complete=true" <<<"$PROBE_LOG" \
      && grep -Fq "blackbox_runtime_probe_passed=true" <<<"$PROBE_LOG"; then
    PROBE_PASSED=true
    break
  fi
  sleep 0.25
done
[[ "$PROBE_PASSED" == true ]] \
  || { echo "black-box Java probe timed out or failed" >&2; exit 1; }

for marker in \
  "android_api=33" \
  "process_64_bit=true" \
  "target_abi_supported=true" \
  "ordinary_data_app=true" \
  "app_private_data_dir_verified=true" \
  "native_runtime_process_ready=true" \
  "native_library_loaded=true" \
  "private_vendor_api_probed=false" \
  "device_nodes_scanned=false" \
  "target_hardware_validated=false" \
  "native_vendor_npu_provider_available=false" \
  "native_runtime_dispatch_enabled=false" \
  "hardware_accessed=false"; do
  grep -Fq "$marker" <<<"$PROBE_LOG" \
    || { echo "black-box Java probe missing marker: $marker" >&2; exit 1; }
done

DELIVERED_SIGNERS="$("$APKSIGNER" verify --print-certs "$RUNTIME_APK" \
  | sed -n 's/^.*certificate SHA-256 digest: //p' \
  | tr 'A-F' 'a-f' \
  | LC_ALL=C sort -u \
  | paste -sd, -)"
INSTALLED_SIGNERS="$(sed -n \
  's/.* installed_signer_sha256=\([^ ]*\) .*/\1/p' <<<"$PROBE_LOG" | tail -n 1)"
[[ -n "$DELIVERED_SIGNERS" && "$INSTALLED_SIGNERS" == "$DELIVERED_SIGNERS" ]] \
  || { echo "installed PackageManager signer does not match delivered APK" >&2; exit 1; }

bash "$ROOT_DIR/tools/preflight_central_brain_android13_blackbox.sh" \
  --serial "$SERIAL" \
  --runtime-apk "$RUNTIME_APK" \
  --demo-apk "$DEMO_APK" \
  --report "$POST_REPORT" \
  --require-api-33 >/dev/null

for report in "$PRE_REPORT" "$POST_REPORT" "$INSTALL_REPORT" "$NATIVE_REPORT" \
    "$SIGNER_NEGATIVE_REPORT"; do
  [[ -s "$report" ]] || { echo "missing B3 evidence report: $report" >&2; exit 1; }
done
grep -Fq "blackbox_preflight_passed=true" "$PRE_REPORT"
grep -Fq "preflight_mutation_performed=false" "$PRE_REPORT"
grep -Fq "existing_runtime_signer_status=MATCH" "$POST_REPORT"
grep -Fq "existing_runtime_signer_verified=true" "$POST_REPORT"
grep -Fq "native_runtime_process_recovery_verified=true" "$NATIVE_REPORT"
grep -Fq "blackbox_existing_signer_mismatch_rejected=true" "$SIGNER_NEGATIVE_REPORT"
grep -Fq "blackbox_preflight_failed_before_install=true" "$SIGNER_NEGATIVE_REPORT"

EVIDENCE_SCOPE="$(sed -n 's/^evidence_scope=//p' "$POST_REPORT" | head -n 1)"
AUTOMOTIVE_FEATURE="$(sed -n \
  's/^automotive_feature_advertised=//p' "$POST_REPORT" | head -n 1)"
SELINUX_STATE="$(sed -n 's/^selinux_state_observed=//p' "$POST_REPORT" | head -n 1)"

printf '%s\n' \
  "blackbox_android13_acceptance_passed=true" \
  "evidence_scope=$EVIDENCE_SCOPE" \
  "device_serial=$SERIAL" \
  "preinstall_read_only_preflight_verified=true" \
  "preinstall_signer_gate_passed=true" \
  "existing_signer_mismatch_rejected=true" \
  "signer_mismatch_failed_before_install=true" \
  "postinstall_signer_verified=true" \
  "package_manager_signer_parity_verified=true" \
  "ordinary_data_app_verified=true" \
  "app_private_data_dir_verified=true" \
  "target_64_bit_abi_supported=true" \
  "native_runtime_process_recovery_verified=true" \
  "post_recovery_hmi_rebind_verified=true" \
  "binder_room_hmi_regression_verified=true" \
  "automotive_feature_advertised=$AUTOMOTIVE_FEATURE" \
  "selinux_state_observed=$SELINUX_STATE" \
  "preinstall_report=$PRE_REPORT" \
  "postinstall_report=$POST_REPORT" \
  "target_hardware_validated=false" \
  "private_vendor_api_probed=false" \
  "device_nodes_scanned=false" \
  "native_vendor_npu_provider_available=false" \
  "native_runtime_dispatch_enabled=false" \
  "hardware_accessed=false" \
  "driver_development_triggered=false" \
  "virtualization_development_triggered=false"
