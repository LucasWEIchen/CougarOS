#!/usr/bin/env bash
set -euo pipefail

# Req IDs: XSC-004/005, NV-F-001/011, NV-G-006/007, NV-P-002,
# DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AAR_PATH="${1:-$ROOT_DIR/central-brain/android-runtime/native-runtime/build/outputs/aar/native-runtime-debug.aar}"
NDK_VERSION="27.3.13750724"

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # Local workspace toolchain; target integrators may provide the same vars externally.
  source "$ROOT_DIR/env.sh"
fi

: "${JAVA_HOME:?JAVA_HOME must point to JDK 17}"
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"

NDK_ROOT="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/$NDK_VERSION}"
READELF="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
JAR="$JAVA_HOME/bin/jar"

[[ -f "$AAR_PATH" ]] || { echo "missing Native Runtime AAR: $AAR_PATH" >&2; exit 1; }
AAR_PATH="$(realpath "$AAR_PATH")"
[[ -x "$READELF" ]] || { echo "missing llvm-readelf: $READELF" >&2; exit 1; }
[[ -x "$JAR" ]] || { echo "missing JDK jar tool: $JAR" >&2; exit 1; }

TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT
(
  cd "$TEMP_DIR"
  "$JAR" xf "$AAR_PATH"
)

mapfile -t native_entries < <(
  "$JAR" tf "$AAR_PATH" | awk '/^jni\/.+\.so$/ { print }' | LC_ALL=C sort
)
expected_entries=(
  "jni/arm64-v8a/libcentral_brain_native.so"
  "jni/x86_64/libcentral_brain_native.so"
)

if [[ "${native_entries[*]}" != "${expected_entries[*]}" ]]; then
  printf 'unexpected AAR native payload:\n' >&2
  printf '  %s\n' "${native_entries[@]}" >&2
  exit 1
fi

verify_elf() {
  local abi="$1"
  local expected_machine="$2"
  local library="$TEMP_DIR/jni/$abi/libcentral_brain_native.so"
  local header symbols dynamic program_headers

  header="$($READELF --file-header "$library")"
  symbols="$($READELF --dyn-syms --wide "$library")"
  dynamic="$($READELF --dynamic "$library")"
  program_headers="$($READELF --program-headers "$library")"

  grep -Eq "Machine:[[:space:]]+$expected_machine$" <<<"$header" \
    || { echo "$abi ELF machine mismatch" >&2; exit 1; }
  grep -Eq 'Type:[[:space:]]+DYN \(Shared object file\)$' <<<"$header" \
    || { echo "$abi is not a shared object" >&2; exit 1; }

  for symbol in \
    JNI_OnLoad \
    cb_runtime_create_v1 \
    cb_runtime_get_health_v1 \
    cb_runtime_acquire_slot_v1 \
    cb_runtime_release_slot_v1 \
    cb_runtime_destroy_v1 \
    cb_status_name; do
    grep -Eq "[[:space:]]${symbol}($|@)" <<<"$symbols" \
      || { echo "$abi missing exported symbol: $symbol" >&2; exit 1; }
  done

  if grep -Eq '[[:space:]]Java_[A-Za-z0-9_]*($|@)' <<<"$symbols"; then
    echo "$abi exposes name-based JNI symbols; RegisterNatives is required" >&2
    exit 1
  fi
  grep -Fq "BIND_NOW" <<<"$dynamic" \
    || { echo "$abi missing BIND_NOW" >&2; exit 1; }
  grep -Fq "GNU_RELRO" <<<"$program_headers" \
    || { echo "$abi missing GNU_RELRO" >&2; exit 1; }

  if grep -Eqi 'NEEDED.*(opencl|npu|neural|vendor|vehicle)' <<<"$dynamic"; then
    echo "$abi unexpectedly links a hardware/vendor library" >&2
    exit 1
  fi
}

verify_elf "arm64-v8a" "AArch64"
verify_elf "x86_64" "Advanced Micro Devices X86-64"

echo "native_runtime_aar_verified=true"
echo "native_runtime_aar_abis=arm64-v8a,x86_64"
echo "native_runtime_register_natives_verified=true"
echo "native_runtime_relro_now_verified=true"
echo "native_vendor_npu_provider_available=false"
echo "native_hardware_accessed=false"
