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
require_text "central-brain/deploy/linux/systemd/central-brain-linux-ipc.service" "Central Brain Linux IPC binding sample"
require_text "central-brain/deploy/linux/systemd/central-brain-linux-ipc.service" "central_brain_ipc_daemon.py"
require_text "central-brain/deploy/linux/central-brain.env.example" "CENTRAL_BRAIN_IPC_SOCKET"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "CENTRAL_BRAIN_PLATFORM_DELTA.md"

echo "Central Brain delivery docs check passed"
