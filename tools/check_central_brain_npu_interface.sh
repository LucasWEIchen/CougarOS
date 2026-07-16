#!/usr/bin/env bash
set -euo pipefail

# Req IDs: HW-002, NV-F-001/011/012, NV-G-004..007,
# KH-003/006/007, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOC="docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md"
REQ="docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md"
DRIVER="docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md"
PROVIDER="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ModelProvider.java"
PROFILES="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ModelProviderProfiles.java"
SCHEDULER="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/scheduler/InferenceResourceScheduler.java"
READINESS="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ModelRuntimeReadinessSnapshot.java"
HEADER="central-brain/android-runtime/native-runtime/src/main/cpp/include/central_brain_native.h"
NATIVE="central-brain/android-runtime/native-runtime/src/main/cpp/central_brain_native.c"
JNI="central-brain/android-runtime/native-runtime/src/main/cpp/central_brain_jni.c"

require_file() {
  [[ -f "$ROOT_DIR/$1" ]] \
    || { echo "missing NPU interface artifact: $1" >&2; exit 1; }
}

require_text() {
  grep -Fq -- "$2" "$ROOT_DIR/$1" \
    || { echo "missing NPU interface marker '$2' in $1" >&2; exit 1; }
}

for file in "$DOC" "$REQ" "$DRIVER" "$PROVIDER" "$PROFILES" "$SCHEDULER" \
    "$READINESS" "$HEADER" "$NATIVE" "$JNI"; do
  require_file "$file"
done

for req_id in HW-002 NV-F-001 NV-F-011 NV-F-012 NV-G-004 NV-G-005 NV-G-006 \
    NV-G-007 KH-003 KH-006 KH-007 DEL-001 DEL-003 DEL-004 DEL-005; do
  require_text "$DOC" "$req_id"
  require_text "$REQ" "$req_id"
done

for marker in \
  'Android R5B2 Test-Only Model Router' \
  'Android R5C1 model-runtime readiness' \
  'NpuDevice.discover()' \
  'NpuDevice.getStatus()' \
  'NpuModel.load(model_spec)' \
  'NpuModel.unload(model_id)' \
  'NpuSession.create(model_id, qos, safety_state)' \
  'NpuSession.infer(input_buffers, output_spec, timeout_ms)' \
  'NpuSession.cancel(request_id)' \
  'NpuDevice.getMetrics()' \
  'NpuDevice.reset(reason)' \
  'UNAVAILABLE -> INITIALIZING -> READY -> DEGRADED -> FAULT_ISOLATED -> CLOSED' \
  'NPU_DEVICE_UNAVAILABLE' \
  'NPU_PERMISSION_DENIED' \
  'NPU_POLICY_DENIED' \
  'NPU_MODEL_REJECTED' \
  'NPU_RESOURCE_EXHAUSTED' \
  'NPU_TIMEOUT' \
  'NPU_FAULT_ISOLATED' \
  'NPU_RESULT_INVALID' \
  'target_hardware_validated=false' \
  'production_ready=false'; do
  require_text "$DOC" "$marker"
done

for operation in \
  'Descriptor descriptor()' \
  'Snapshot snapshot()' \
  'Snapshot warmup(ModelSpec modelSpec)' \
  'InferenceHandle infer(InferenceRequest request, StreamObserver observer)' \
  'CancelState cancel(String requestId, String reason)' \
  'Metrics metrics()' \
  'FaultSnapshot lastFault()' \
  'void close()'; do
  require_text "$PROVIDER" "$operation"
done

for marker in \
  'VENDOR_NPU_EMPTY_ID = "vendor.npu.empty"' \
  'ModelProvider.Assurance.EMPTY' \
  'ModelProvider.FallbackClass.NEVER' \
  '"VENDOR_RUNTIME_UNAVAILABLE"' \
  'return new Profile(descriptor, snapshot, false, false)'; do
  require_text "$PROFILES" "$marker"
done

for marker in \
  'CB_NATIVE_ABI_VERSION' \
  'cb_runtime_create_v1' \
  'cb_runtime_get_health_v1' \
  'cb_runtime_acquire_slot_v1' \
  'cb_runtime_release_slot_v1' \
  'cb_runtime_destroy_v1' \
  'vendor_npu_provider_available' \
  'hardware_accessed'; do
  require_text "$HEADER" "$marker"
done

require_text "$DRIVER" 'DRV-GAP-001'
require_text "$DRIVER" 'driver_development_triggered=false'

if find "$ROOT_DIR/central-brain" -type f -name '*.py' -print -quit | grep -q .; then
  echo "Python runtime cannot implement the retained NPU interface" >&2
  exit 1
fi

if rg -n 'ioctl|/dev/|sysfs|CarPropertyManager|VehicleHal' \
    "$ROOT_DIR/$PROFILES" "$ROOT_DIR/$SCHEDULER" "$ROOT_DIR/$READINESS"; then
  echo "Android NPU contract must not guess a vendor device or vehicle API" >&2
  exit 1
fi

bash "$ROOT_DIR/tools/check_central_brain_android_model_provider_contract.sh"
bash "$ROOT_DIR/tools/check_central_brain_native_runtime.sh"

printf '%s\n' \
  'Central Brain NPU runtime interface check passed' \
  'android_model_provider_contract_retained=true' \
  'vendor_npu_provider_available=false' \
  'hardware_accessed=false' \
  'driver_development_triggered=false' \
  'target_hardware_validated=false'
