#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOC="$ROOT_DIR/docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md"
DRIVER_DOC="$ROOT_DIR/docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md"
REQUIREMENTS_DOC="$ROOT_DIR/docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md"

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq "$pattern" "$path"; then
    echo "missing pattern '$pattern' in ${path#$ROOT_DIR/}" >&2
    exit 1
  fi
}

test -f "$DOC"

for req_id in HW-002 NV-F-011 KH-003 KH-006 KH-007 DEL-001 DEL-002 DEL-005; do
  require_text "$DOC" "$req_id"
done

for symbol in \
  "NpuDevice.discover()" \
  "NpuDevice.getStatus()" \
  "NpuModel.load(model_spec)" \
  "NpuModel.unload(model_id)" \
  "NpuSession.create(model_id, qos, safety_state)" \
  "NpuSession.infer(input_buffers, output_spec, timeout_ms)" \
  "NpuSession.cancel(request_id)" \
  "NpuDevice.getMetrics()" \
  "NpuDevice.reset(reason)"
do
  require_text "$DOC" "$symbol"
done

for state in unavailable ready degraded fault_isolated interlocked; do
  require_text "$DOC" "$state"
done

for error_code in \
  NPU_DEVICE_UNAVAILABLE \
  NPU_POLICY_DENIED \
  NPU_MODEL_REJECTED \
  NPU_TIMEOUT \
  NPU_FAULT_ISOLATED \
  NPU_BACKEND_FALLBACK_REQUIRED
do
  require_text "$DOC" "$error_code"
done

require_text "$DRIVER_DOC" "CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md"
require_text "$DRIVER_DOC" "/hardware/interfaces"
require_text "$REQUIREMENTS_DOC" "CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md"
require_text "$REQUIREMENTS_DOC" "/hardware/interfaces"
require_text "$DOC" "/hardware/interfaces"
require_text "$DOC" "/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status"
require_text "$DRIVER_DOC" "/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status"
require_text "$REQUIREMENTS_DOC" "/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status"
require_text "$DOC" "/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/audit-consistency"
require_text "$DRIVER_DOC" "/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/audit-consistency"
require_text "$REQUIREMENTS_DOC" "/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/audit-consistency"

echo "Central Brain NPU runtime interface check passed"
