#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-006, NV-P-002, DEL-001, DEL-003, DEL-004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

DEVICE_ENUMERATION_SCRIPTS=(
  tools/install_central_brain_android_delivery.sh
  tools/install_central_brain_android_hybrid_delivery.sh
  tools/install_central_brain_android_runtime.sh
  tools/preflight_central_brain_android13_blackbox.sh
  tools/run_central_brain_android_remote_acceptance.sh
  tools/test_central_brain_android_binder_lifecycle.sh
  tools/test_central_brain_android_blackbox_acceptance.sh
  tools/test_central_brain_android_blackbox_signer_guard.sh
  tools/test_central_brain_android_capability_policy.sh
  tools/test_central_brain_android_native_runtime.sh
  tools/test_central_brain_android_target_deployment.sh
  tools/test_client2_central_brain_binder.sh
  tools/test_client2_central_brain_recovery.sh
)

DEVICE_STATE_SCRIPTS=(
  tools/install_central_brain_android_delivery.sh
  tools/install_central_brain_android_hybrid_delivery.sh
  tools/install_central_brain_android_runtime.sh
  tools/preflight_central_brain_android13_blackbox.sh
  tools/run_central_brain_android_remote_acceptance.sh
  tools/test_central_brain_android_binder_lifecycle.sh
  tools/test_central_brain_android_blackbox_acceptance.sh
  tools/test_central_brain_android_native_runtime.sh
  tools/test_central_brain_android_target_deployment.sh
)

for relative in "${DEVICE_ENUMERATION_SCRIPTS[@]}"; do
  path="$ROOT_DIR/$relative"
  [[ -f "$path" ]] || { echo "missing adb script: $relative" >&2; exit 1; }
  if grep -Eq 'devices[[:space:]]*\|[[:space:]]*awk' "$path"; then
    echo "adb device parser does not normalize CRLF: $relative" >&2
    exit 1
  fi
  grep -Fq "devices | tr -d '\r' | awk" "$path" \
    || { echo "missing CRLF-safe adb device parser: $relative" >&2; exit 1; }
done

for relative in "${DEVICE_STATE_SCRIPTS[@]}"; do
  path="$ROOT_DIR/$relative"
  grep -Fq "get-state" "$path" \
    || { echo "missing adb state check: $relative" >&2; exit 1; }
  grep -Eq "get-state.*tr -d '\\\\r'" "$path" \
    || { echo "adb state check does not normalize CRLF: $relative" >&2; exit 1; }
done

grep -Fq 'ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"' \
  "$ROOT_DIR/tools/test_central_brain_android_blackbox_signer_guard.sh" \
  || { echo "black-box signer guard does not honor the selected adb tool" >&2; exit 1; }

parse_online_devices() {
  tr -d '\r' | awk 'NR > 1 && $2 == "device" { print $1 }'
}

mapfile -t LF_DEVICES < <(printf 'List of devices attached\nalpha\tdevice\nbeta\toffline\n' \
  | parse_online_devices)
mapfile -t CRLF_DEVICES < <(printf 'List of devices attached\r\nalpha\tdevice\r\nbeta\toffline\r\n' \
  | parse_online_devices)

[[ ${#LF_DEVICES[@]} -eq 1 && "${LF_DEVICES[0]}" == alpha ]] \
  || { echo "LF adb parser regression" >&2; exit 1; }
[[ ${#CRLF_DEVICES[@]} -eq 1 && "${CRLF_DEVICES[0]}" == alpha ]] \
  || { echo "CRLF adb parser regression" >&2; exit 1; }

[[ "$(printf 'device\r\n' | tr -d '\r')" == device ]] \
  || { echo "CRLF adb state regression" >&2; exit 1; }

echo "Central Brain Windows adb CRLF compatibility check passed"
