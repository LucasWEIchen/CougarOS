#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-004/005/006, NV-F-001/011, NV-G-006/007,
# NV-P-002, KH-003/006, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODULE="central-brain/android-runtime/native-runtime"

require_file() {
  local path="$1"
  [[ -f "$ROOT_DIR/$path" ]] || { echo "missing Native Runtime file: $path" >&2; exit 1; }
}

require_text() {
  local path="$1"
  local pattern="$2"
  grep -Fq -- "$pattern" "$ROOT_DIR/$path" \
    || { echo "missing Native Runtime pattern '$pattern' in $path" >&2; exit 1; }
}

for path in \
  "$MODULE/build.gradle.kts" \
  "$MODULE/README.md" \
  "$MODULE/consumer-rules.pro" \
  "$MODULE/src/main/AndroidManifest.xml" \
  "$MODULE/src/main/cpp/CMakeLists.txt" \
  "$MODULE/src/main/cpp/include/central_brain_native.h" \
  "$MODULE/src/main/cpp/central_brain_native.c" \
  "$MODULE/src/main/cpp/central_brain_jni.c" \
  "$MODULE/src/main/java/com/centralbrain/nativebridge/NativeRuntime.java" \
  "$MODULE/src/main/java/com/centralbrain/nativebridge/NativeRuntimeSnapshot.java" \
  "$MODULE/src/main/java/com/centralbrain/nativebridge/NativeRuntimeStatus.java" \
	  "$MODULE/src/test/c/native_runtime_host_test.c" \
	  "$MODULE/src/test/java/com/centralbrain/nativebridge/NativeRuntimeSnapshotTest.java" \
	  tools/test_central_brain_native_runtime_host.sh \
	  tools/verify_central_brain_native_runtime_aar.sh; do
  require_file "$path"
done

require_file "docs/CENTRAL_BRAIN_NATIVE_RUNTIME_C_ABI.md"

require_text "central-brain/android-runtime/settings.gradle.kts" 'include(":native-runtime")'
require_text "$MODULE/build.gradle.kts" 'ndkVersion = "27.3.13750724"'
require_text "$MODULE/build.gradle.kts" '"arm64-v8a", "x86_64"'
require_text "$MODULE/src/main/cpp/include/central_brain_native.h" "CB_NATIVE_ABI_VERSION"
require_text "$MODULE/src/main/cpp/include/central_brain_native.h" "struct_size"
require_text "$MODULE/src/main/cpp/include/central_brain_native.h" "abi_version"
require_text "$MODULE/src/main/cpp/central_brain_jni.c" "JNI_OnLoad"
require_text "$MODULE/src/main/cpp/central_brain_jni.c" "RegisterNatives"
require_text "$MODULE/src/main/java/com/centralbrain/nativebridge/NativeRuntime.java" 'System.loadLibrary("central_brain_native")'
require_text "$MODULE/src/main/java/com/centralbrain/nativebridge/NativeRuntimeSnapshot.java" "native_vendor_npu_provider_available=%s"
require_text "$MODULE/src/main/java/com/centralbrain/nativebridge/NativeRuntimeSnapshot.java" "native_hardware_accessed=%s"
require_text "docs/CENTRAL_BRAIN_NATIVE_RUNTIME_C_ABI.md" "CB_STATUS_CAPACITY_EXHAUSTED"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "B1 Native Runtime C ABI trace"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "2026-07-12 B1 进展"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "2026-07-12 B1 进展"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android B1 Native Runtime Artifact"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "B1 Native Artifact Driver/HAL Result"
require_text "tools/test_central_brain_native_runtime_host.sh" "-fsanitize=address,undefined"
require_text "tools/verify_central_brain_native_runtime_aar.sh" "native_runtime_aar_abis=arm64-v8a,x86_64"
require_text "tools/build_central_brain_android_runtime.sh" "verify_central_brain_native_runtime_aar.sh"
require_text "$MODULE/src/test/c/native_runtime_host_test.c" "CONCURRENCY_THREADS"

if rg -n \
    '(^|[^A-Za-z])(open|fopen|ioctl|dlopen|socket|connect|send|recv|system)[[:space:]]*\(' \
    "$ROOT_DIR/$MODULE/src/main/cpp"; then
  echo "Native Runtime core must not access files, device nodes, dynamic loaders or network" >&2
  exit 1
fi

bash "$ROOT_DIR/tools/test_central_brain_native_runtime_host.sh"

echo "Central Brain Native Runtime B1 static check passed"
