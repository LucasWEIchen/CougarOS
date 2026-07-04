#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # shellcheck disable=SC1091
  source "$ROOT_DIR/env.sh" >/dev/null 2>&1 || true
fi

ANDROID_AIDL="$ROOT_DIR/central-brain/bindings/android/aidl/com/centralbrain/binding/ICentralBrainGateway.aidl"
ANDROID_BINDER_SERVICE="$ROOT_DIR/central-brain/bindings/android/java/com/centralbrain/binding/CentralBrainGatewayBinderService.java"
ANDROID_BINDER_CLIENT="$ROOT_DIR/central-brain/bindings/android/java/com/centralbrain/binding/CentralBrainGatewayClient.java"
LINUX_PROTO="$ROOT_DIR/central-brain/bindings/linux/proto/central_brain_gateway.proto"
LINUX_IPC_SCHEMA="$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_envelope.schema.json"
LINUX_IPC_DAEMON="$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_daemon.py"
LINUX_IPC_CLIENT="$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_client.py"

test -f "$ANDROID_AIDL"
test -f "$ANDROID_BINDER_SERVICE"
test -f "$ANDROID_BINDER_CLIENT"
test -f "$LINUX_PROTO"
test -f "$LINUX_IPC_SCHEMA"
test -f "$LINUX_IPC_DAEMON"
test -f "$LINUX_IPC_CLIENT"

grep -q "interface ICentralBrainGateway" "$ANDROID_AIDL"
grep -q "XSC-006" "$ANDROID_AIDL"
grep -q "NV-P-002" "$ANDROID_AIDL"
grep -q "getNativeAdaptersDetailJson" "$ANDROID_AIDL"

grep -q "Req IDs: XSC-002, XSC-003, XSC-004, XSC-005, XSC-006, NV-P-002, DEL-001" "$ANDROID_BINDER_SERVICE"
grep -q "extends Service" "$ANDROID_BINDER_SERVICE"
grep -q "getBindingDetailJson" "$ANDROID_BINDER_SERVICE"
grep -q "/native/adapters/detail" "$ANDROID_BINDER_SERVICE"
grep -q "Req IDs: XSC-002, XSC-003, XSC-004, XSC-005, XSC-006, NV-P-002, DEL-001" "$ANDROID_BINDER_CLIENT"
grep -q "bindService" "$ANDROID_BINDER_CLIENT"

grep -q "service CentralBrainGateway" "$LINUX_PROTO"
grep -q "XSC-006" "$LINUX_PROTO"
grep -q "NV-P-003" "$LINUX_PROTO"

python3 -m json.tool "$LINUX_IPC_SCHEMA" >/dev/null
grep -q "XSC-006" "$LINUX_IPC_SCHEMA"
grep -q "NV-P-002" "$LINUX_IPC_SCHEMA"

grep -q "Req IDs: XSC-006, NV-P-002, DEL-002" "$LINUX_IPC_DAEMON"
grep -q "OPERATION_MAP" "$LINUX_IPC_DAEMON"
grep -q "COMMANDS" "$LINUX_IPC_CLIENT"

if [[ -n "${ANDROID_HOME:-}" ]]; then
  AIDL_BIN="$(command -v aidl || find "$ANDROID_HOME" -name aidl -type f 2>/dev/null | head -1)"
  ANDROID_JAR="$ANDROID_HOME/platforms/android-36/android.jar"
  if [[ -n "$AIDL_BIN" && -f "$ANDROID_JAR" ]]; then
    TMP_DIR="$(mktemp -d)"
    trap 'rm -rf "$TMP_DIR"' EXIT
    "$AIDL_BIN" \
      -I"$ROOT_DIR/central-brain/bindings/android/aidl" \
      -o"$TMP_DIR/generated" \
      "$ANDROID_AIDL"
    javac -encoding UTF-8 -source 8 -target 8 \
      -bootclasspath "$ANDROID_JAR" \
      -d "$TMP_DIR/classes" \
      "$TMP_DIR/generated/com/centralbrain/binding/ICentralBrainGateway.java" \
      "$ANDROID_BINDER_SERVICE" \
      "$ANDROID_BINDER_CLIENT"
  fi
fi

echo "Central Brain binding artifacts check passed"
