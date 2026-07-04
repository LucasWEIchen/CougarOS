#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

ANDROID_AIDL="$ROOT_DIR/central-brain/bindings/android/aidl/com/centralbrain/binding/ICentralBrainGateway.aidl"
LINUX_PROTO="$ROOT_DIR/central-brain/bindings/linux/proto/central_brain_gateway.proto"
LINUX_IPC_SCHEMA="$ROOT_DIR/central-brain/bindings/linux/ipc/central_brain_ipc_envelope.schema.json"

test -f "$ANDROID_AIDL"
test -f "$LINUX_PROTO"
test -f "$LINUX_IPC_SCHEMA"

grep -q "interface ICentralBrainGateway" "$ANDROID_AIDL"
grep -q "XSC-006" "$ANDROID_AIDL"
grep -q "NV-P-002" "$ANDROID_AIDL"

grep -q "service CentralBrainGateway" "$LINUX_PROTO"
grep -q "XSC-006" "$LINUX_PROTO"
grep -q "NV-P-003" "$LINUX_PROTO"

python3 -m json.tool "$LINUX_IPC_SCHEMA" >/dev/null
grep -q "XSC-006" "$LINUX_IPC_SCHEMA"
grep -q "NV-P-002" "$LINUX_IPC_SCHEMA"

echo "Central Brain binding artifacts check passed"
