#!/usr/bin/env bash
set -euo pipefail

# Req IDs: XSC-004, NV-F-001, NV-F-011, NV-P-002, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODULE="$ROOT_DIR/central-brain/android-runtime/native-runtime"
CC_BIN="${CC:-$(command -v cc || true)}"

[[ -n "$CC_BIN" && -x "$CC_BIN" ]] || { echo "host C compiler is unavailable" >&2; exit 1; }

TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

"$CC_BIN" \
  -std=c11 \
  -Wall \
  -Wextra \
  -Werror \
  -Wpedantic \
  -Wconversion \
  -Wshadow \
  -Wformat=2 \
  -Wundef \
  -fno-omit-frame-pointer \
  -fsanitize=address,undefined \
  -pthread \
  -I"$MODULE/src/main/cpp/include" \
  "$MODULE/src/main/cpp/central_brain_native.c" \
  "$MODULE/src/test/c/native_runtime_host_test.c" \
  -o "$TEMP_DIR/native_runtime_host_test"

ASAN_OPTIONS=detect_leaks=1 UBSAN_OPTIONS=halt_on_error=1 \
  "$TEMP_DIR/native_runtime_host_test"
