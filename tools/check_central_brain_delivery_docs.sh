#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing required delivery file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq "$pattern" "$ROOT_DIR/$path"; then
    echo "missing pattern '$pattern' in $path" >&2
    exit 1
  fi
}

require_file "docs/CENTRAL_BRAIN_PLATFORM_DELTA.md"
require_file "docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md"
require_file "central-brain/deploy/linux/README.md"
require_file "central-brain/deploy/linux/central-brain.env.example"
require_file "central-brain/deploy/linux/systemd/central-brain-backend.service"
require_file "central-brain/deploy/linux/systemd/central-brain-linux-ipc.service"

for req_id in DEL-001 DEL-002 DEL-003 DEL-004 XSC-002 XSC-003 XSC-005 XSC-006 NV-P-002; do
  require_text "docs/CENTRAL_BRAIN_PLATFORM_DELTA.md" "$req_id"
  require_text "central-brain/deploy/linux/README.md" "$req_id"
done

require_text "central-brain/deploy/linux/systemd/central-brain-backend.service" "Central Brain semantic gateway prototype"
require_text "central-brain/deploy/linux/systemd/central-brain-backend.service" "tools/run_central_brain_backend.sh"
require_text "central-brain/deploy/linux/systemd/central-brain-backend.service" "LogsDirectory=central-brain"
require_text "central-brain/deploy/linux/systemd/central-brain-linux-ipc.service" "Central Brain Linux IPC binding sample"
require_text "central-brain/deploy/linux/systemd/central-brain-linux-ipc.service" "central_brain_ipc_daemon.py"
require_text "central-brain/deploy/linux/central-brain.env.example" "CENTRAL_BRAIN_IPC_SOCKET"
require_text "central-brain/deploy/linux/central-brain.env.example" "CENTRAL_BRAIN_AUDIT_LOG"
require_text "central-brain/deploy/linux/README.md" "CENTRAL_BRAIN_AUDIT_LOG"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "CENTRAL_BRAIN_PLATFORM_DELTA.md"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "smoke_central_brain_audit_persistence.sh"
require_text "docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md" "HW-002"
require_text "docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md" "NV-F-011"
require_text "docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md" "KH-003"
require_text "docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md" "KH-006"
require_text "docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md" "DEL-005"

echo "Central Brain delivery docs check passed"
