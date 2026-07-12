#!/usr/bin/env bash
set -euo pipefail

# Req IDs: XSC-004/005/006, NV-F-001/011, NV-G-003/006/007,
# NV-P-002, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK_PATH="${1:-$ROOT_DIR/central-brain/android-runtime/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk}"
NDK_VERSION="27.3.13750724"

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  source "$ROOT_DIR/env.sh"
fi

: "${JAVA_HOME:?JAVA_HOME must point to JDK 17}"
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"

[[ -f "$APK_PATH" ]] || { echo "missing Runtime APK: $APK_PATH" >&2; exit 1; }
APK_PATH="$(realpath "$APK_PATH")"

JAR="$JAVA_HOME/bin/jar"
READELF="$ANDROID_HOME/ndk/$NDK_VERSION/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
AAPT="$ANDROID_HOME/build-tools/37.0.0/aapt"
APKSIGNER="$ANDROID_HOME/build-tools/37.0.0/apksigner"
for tool in "$JAR" "$READELF" "$AAPT" "$APKSIGNER"; do
  [[ -x "$tool" ]] || { echo "missing APK verifier tool: $tool" >&2; exit 1; }
done

TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT
(
  cd "$TEMP_DIR"
  "$JAR" xf "$APK_PATH"
)

mapfile -t native_entries < <(
  "$JAR" tf "$APK_PATH" | awk '/^lib\/.+\.so$/ { print }' | LC_ALL=C sort
)
expected_entries=(
  "lib/arm64-v8a/libcentral_brain_native.so"
  "lib/x86_64/libcentral_brain_native.so"
)
if [[ "${native_entries[*]}" != "${expected_entries[*]}" ]]; then
  printf 'unexpected Runtime APK native payload:\n' >&2
  printf '  %s\n' "${native_entries[@]}" >&2
  exit 1
fi

verify_elf() {
  local abi="$1"
  local expected_machine="$2"
  local library="$TEMP_DIR/lib/$abi/libcentral_brain_native.so"
  local header symbols dynamic program_headers

  header="$($READELF --file-header "$library")"
  symbols="$($READELF --dyn-syms --wide "$library")"
  dynamic="$($READELF --dynamic "$library")"
  program_headers="$($READELF --program-headers "$library")"

  grep -Eq "Machine:[[:space:]]+$expected_machine$" <<<"$header" \
    || { echo "$abi Runtime APK ELF machine mismatch" >&2; exit 1; }
  for symbol in JNI_OnLoad cb_runtime_create_v1 cb_runtime_get_health_v1 \
      cb_runtime_acquire_slot_v1 cb_runtime_release_slot_v1 cb_runtime_destroy_v1 \
      cb_status_name; do
    grep -Eq "[[:space:]]${symbol}($|@)" <<<"$symbols" \
      || { echo "$abi Runtime APK missing exported symbol: $symbol" >&2; exit 1; }
  done
  grep -Fq "BIND_NOW" <<<"$dynamic" \
    || { echo "$abi Runtime APK library missing BIND_NOW" >&2; exit 1; }
  grep -Fq "GNU_RELRO" <<<"$program_headers" \
    || { echo "$abi Runtime APK library missing GNU_RELRO" >&2; exit 1; }
  if grep -Eqi 'NEEDED.*(opencl|npu|neural|vendor|vehicle)' <<<"$dynamic"; then
    echo "$abi Runtime APK unexpectedly links a hardware/vendor library" >&2
    exit 1
  fi
}

verify_elf "arm64-v8a" "AArch64"
verify_elf "x86_64" "Advanced Micro Devices X86-64"

BADGING="$($AAPT dump badging "$APK_PATH")"
PERMISSIONS="$($AAPT dump permissions "$APK_PATH")"
MANIFEST="$($AAPT dump xmltree "$APK_PATH" AndroidManifest.xml)"
grep -Fq "package: name='com.centralbrain.runtime' versionCode='2' versionName='0.2.0-b2'" \
  <<<"$BADGING" || { echo "Runtime APK package/version mismatch" >&2; exit 1; }
grep -Fq "sdkVersion:'33'" <<<"$BADGING" \
  || { echo "Runtime APK minSdk mismatch" >&2; exit 1; }
grep -Fq "com.centralbrain.runtime.CentralBrainRuntimeApplication" <<<"$MANIFEST" \
  || { echo "Runtime APK process Application owner is missing" >&2; exit 1; }
if grep -Fq "android.permission.INTERNET" <<<"$PERMISSIONS"; then
  echo "Runtime APK must not request INTERNET" >&2
  exit 1
fi
"$APKSIGNER" verify "$APK_PATH"

echo "native_runtime_apk_verified=true"
echo "native_runtime_apk_version=0.2.0-b2"
echo "native_runtime_apk_abis=arm64-v8a,x86_64"
echo "native_runtime_process_owner=CentralBrainRuntimeApplication"
echo "native_runtime_dispatch_enabled=false"
echo "native_vendor_npu_provider_available=false"
echo "native_hardware_accessed=false"
