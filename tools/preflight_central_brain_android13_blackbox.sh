#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004/005/006, NV-F-001/011/012,
# NV-G-003/005/006/007, NV-P-002, KH-003/006, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/central-brain/android-runtime"
RUNTIME_APK="$RUNTIME_DIR/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
DEMO_APK="$RUNTIME_DIR/demo-hmi/build/outputs/apk/debug/demo-hmi-debug.apk"
SERIAL="${ANDROID_SERIAL:-}"
REPORT_PATH=""
REQUIRE_API_33=false

usage() {
  cat <<'EOF'
Usage: preflight_central_brain_android13_blackbox.sh [options]

Read-only preflight. This command never installs, uninstalls or changes the device.

Options:
  --serial SERIAL       Select an adb device explicitly.
  --runtime-apk PATH    Runtime APK to validate against the device.
  --demo-apk PATH       Demo APK to validate against the device.
  --report PATH         Write the key/value evidence report to PATH.
  --require-api-33      Fail unless the device is exactly Android API 33.
  -h, --help            Show this help.
EOF
}

while (($# > 0)); do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "--serial requires a value" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    --runtime-apk)
      [[ $# -ge 2 ]] || { echo "--runtime-apk requires a value" >&2; exit 2; }
      RUNTIME_APK="$2"
      shift 2
      ;;
    --demo-apk)
      [[ $# -ge 2 ]] || { echo "--demo-apk requires a value" >&2; exit 2; }
      DEMO_APK="$2"
      shift 2
      ;;
    --report)
      [[ $# -ge 2 ]] || { echo "--report requires a value" >&2; exit 2; }
      REPORT_PATH="$2"
      shift 2
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
APKSIGNER="${APKSIGNER:-$ANDROID_HOME/build-tools/37.0.0/apksigner}"
for tool in "$ADB" "$APKSIGNER"; do
  [[ -x "$tool" ]] || { echo "required preflight tool is unavailable: $tool" >&2; exit 1; }
done
for artifact in "$RUNTIME_APK" "$DEMO_APK"; do
  [[ -f "$artifact" ]] || { echo "missing black-box artifact: $artifact" >&2; exit 1; }
done

bash "$ROOT_DIR/tools/verify_central_brain_native_runtime_apk.sh" "$RUNTIME_APK" >/dev/null

signer_set() {
  "$APKSIGNER" verify --print-certs "$1" \
    | sed -n 's/^.*certificate SHA-256 digest: //p' \
    | tr 'A-F' 'a-f' \
    | LC_ALL=C sort -u \
    | paste -sd, -
}

RUNTIME_SIGNERS="$(signer_set "$RUNTIME_APK")"
DEMO_SIGNERS="$(signer_set "$DEMO_APK")"
[[ -n "$RUNTIME_SIGNERS" && "$RUNTIME_SIGNERS" == "$DEMO_SIGNERS" ]] \
  || { echo "Runtime and Demo delivered signer sets do not match" >&2; exit 1; }

if [[ -z "$SERIAL" ]]; then
  mapfile -t DEVICES < <("$ADB" devices | tr -d '\r' | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [[ ${#DEVICES[@]} -ne 1 ]]; then
    echo "expected exactly one online adb device; use --serial" >&2
    "$ADB" devices -l >&2
    exit 1
  fi
  SERIAL="${DEVICES[0]}"
fi
ADB_DEVICE=("$ADB" -s "$SERIAL")
[[ "$("${ADB_DEVICE[@]}" get-state | tr -d '\r')" == "device" ]] \
  || { echo "adb device is not online: $SERIAL" >&2; exit 1; }

get_prop() {
  "${ADB_DEVICE[@]}" shell getprop "$1" | tr -d '\r\n'
}

SDK="$(get_prop ro.build.version.sdk)"
RELEASE="$(get_prop ro.build.version.release)"
MODEL="$(get_prop ro.product.model)"
MANUFACTURER="$(get_prop ro.product.manufacturer)"
FINGERPRINT="$(get_prop ro.build.fingerprint)"
BUILD_TYPE="$(get_prop ro.build.type)"
BUILD_TAGS="$(get_prop ro.build.tags)"
PRIMARY_ABI="$(get_prop ro.product.cpu.abi)"
ABI_LIST="$(get_prop ro.product.cpu.abilist)"
ABI_LIST_64="$(get_prop ro.product.cpu.abilist64)"
QEMU="$(get_prop ro.kernel.qemu)"
VERIFIED_BOOT_STATE="$(get_prop ro.boot.verifiedbootstate)"
FLASH_LOCKED="$(get_prop ro.boot.flash.locked)"
VBMETA_DEVICE_STATE="$(get_prop ro.boot.vbmeta.device_state)"

[[ "$SDK" =~ ^[0-9]+$ && "$SDK" -ge 33 ]] \
  || { echo "Android API 33 or newer is required; found '$SDK'" >&2; exit 1; }
if [[ "$REQUIRE_API_33" == true && "$SDK" != "33" ]]; then
  echo "Android 13/API 33 evidence requested; found API $SDK" >&2
  exit 1
fi
case ",$ABI_LIST_64," in
  *,arm64-v8a,*|*,x86_64,*) TARGET_ABI_SUPPORTED=true ;;
  *)
    echo "device has no supported 64-bit Central Brain ABI: '$ABI_LIST_64'" >&2
    exit 1
    ;;
esac

FEATURES="$("${ADB_DEVICE[@]}" shell pm list features | tr -d '\r')"
if grep -Fq "feature:android.hardware.type.automotive" <<<"$FEATURES"; then
  AUTOMOTIVE_FEATURE=true
else
  AUTOMOTIVE_FEATURE=false
fi
SELINUX_STATE="$("${ADB_DEVICE[@]}" shell getenforce 2>/dev/null | tr -d '\r\n' || true)"
SELINUX_STATE="${SELINUX_STATE:-UNKNOWN}"

TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

inspect_existing_package() {
  local package_name="$1"
  local delivered_apk="$2"
  local paths base_entry device_path pulled_apk installed_signers package_dump package_flags
  paths="$("${ADB_DEVICE[@]}" shell pm path "$package_name" 2>/dev/null | tr -d '\r' || true)"
  if [[ -z "$paths" ]]; then
    printf '%s\t%s\t%s\t%s\t%s\n' false NOT_INSTALLED false NOT_INSTALLED NOT_INSTALLED
    return
  fi
  base_entry="$(grep -E '^package:/data/app/.*/base\.apk$' <<<"$paths" | head -n 1 || true)"
  [[ -n "$base_entry" ]] \
    || { echo "$package_name is not an ordinary /data/app package: $paths" >&2; exit 1; }
  device_path="${base_entry#package:}"
  pulled_apk="$TEMP_DIR/${package_name//./_}.apk"
  "${ADB_DEVICE[@]}" pull "$device_path" "$pulled_apk" >/dev/null 2>&1
  installed_signers="$(signer_set "$pulled_apk")"
  [[ -n "$installed_signers" && "$installed_signers" == "$(signer_set "$delivered_apk")" ]] \
    || { echo "$package_name existing signer does not match delivered APK" >&2; exit 1; }
  package_dump="$("${ADB_DEVICE[@]}" shell dumpsys package "$package_name" | tr -d '\r')"
  package_flags="$(sed -n 's/^[[:space:]]*pkgFlags=\[\(.*\)\]/\1/p' \
    <<<"$package_dump" | head -n 1)"
  if grep -Eq '(^|[[:space:]])(SYSTEM|PRIVILEGED|PERSISTENT)([[:space:]]|$)' \
      <<<"$package_flags"; then
    echo "$package_name unexpectedly has a system/privileged/persistent package flag" >&2
    exit 1
  fi
  printf '%s\t%s\t%s\t%s\t%s\n' \
    true MATCH true "$base_entry" "$installed_signers"
}

IFS=$'\t' read -r RUNTIME_INSTALLED RUNTIME_SIGNER_STATUS \
  RUNTIME_SIGNER_VERIFIED RUNTIME_INSTALL_PATH RUNTIME_INSTALLED_SIGNERS \
  < <(inspect_existing_package com.centralbrain.runtime "$RUNTIME_APK")
IFS=$'\t' read -r DEMO_INSTALLED DEMO_SIGNER_STATUS \
  DEMO_SIGNER_VERIFIED DEMO_INSTALL_PATH DEMO_INSTALLED_SIGNERS \
  < <(inspect_existing_package com.centralbrain.demo "$DEMO_APK")

if [[ "$QEMU" == "1" ]]; then
  EVIDENCE_SCOPE="api33-emulator-blackbox-preflight"
  PHYSICAL_DEVICE_OBSERVED=false
else
  EVIDENCE_SCOPE="api33-device-blackbox-preflight"
  PHYSICAL_DEVICE_OBSERVED=true
fi

RESULT=(
  "blackbox_preflight_passed=true"
  "preflight_mutation_performed=false"
  "device_serial=$SERIAL"
  "evidence_scope=$EVIDENCE_SCOPE"
  "physical_device_observed=$PHYSICAL_DEVICE_OBSERVED"
  "android_release=$RELEASE"
  "android_api=$SDK"
  "android_api_33_verified=$([[ "$SDK" == "33" ]] && echo true || echo false)"
  "device_model=$MODEL"
  "device_manufacturer=$MANUFACTURER"
  "device_fingerprint=$FINGERPRINT"
  "build_type=$BUILD_TYPE"
  "build_tags=$BUILD_TAGS"
  "primary_abi=$PRIMARY_ABI"
  "supported_abis=$ABI_LIST"
  "supported_64_bit_abis=$ABI_LIST_64"
  "target_64_bit_abi_supported=$TARGET_ABI_SUPPORTED"
  "automotive_feature_advertised=$AUTOMOTIVE_FEATURE"
  "selinux_state_observed=$SELINUX_STATE"
  "verified_boot_state_observed=${VERIFIED_BOOT_STATE:-UNKNOWN}"
  "flash_locked_observed=${FLASH_LOCKED:-UNKNOWN}"
  "vbmeta_device_state_observed=${VBMETA_DEVICE_STATE:-UNKNOWN}"
  "delivered_signer_sha256=$RUNTIME_SIGNERS"
  "existing_runtime_package_installed=$RUNTIME_INSTALLED"
  "existing_runtime_signer_status=$RUNTIME_SIGNER_STATUS"
  "existing_runtime_signer_verified=$RUNTIME_SIGNER_VERIFIED"
  "existing_runtime_install_path=$RUNTIME_INSTALL_PATH"
  "existing_runtime_signer_sha256=$RUNTIME_INSTALLED_SIGNERS"
  "existing_demo_package_installed=$DEMO_INSTALLED"
  "existing_demo_signer_status=$DEMO_SIGNER_STATUS"
  "existing_demo_signer_verified=$DEMO_SIGNER_VERIFIED"
  "existing_demo_install_path=$DEMO_INSTALL_PATH"
  "existing_demo_signer_sha256=$DEMO_INSTALLED_SIGNERS"
  "preinstall_signer_gate_passed=true"
  "ordinary_data_app_required=true"
  "private_vendor_api_probed=false"
  "device_nodes_scanned=false"
  "system_partition_write_capability=false"
  "target_hardware_validated=false"
  "native_vendor_npu_provider_available=false"
  "native_runtime_dispatch_enabled=false"
  "hardware_accessed=false"
  "driver_development_triggered=false"
  "virtualization_development_triggered=false"
)

if [[ -n "$REPORT_PATH" ]]; then
  mkdir -p "$(dirname "$REPORT_PATH")"
  REPORT_TEMP="$REPORT_PATH.tmp.$$"
  printf '%s\n' "${RESULT[@]}" >"$REPORT_TEMP"
  mv "$REPORT_TEMP" "$REPORT_PATH"
fi
printf '%s\n' "${RESULT[@]}"
