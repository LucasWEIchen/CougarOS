#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOC="docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md"
REQ="docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md"
DEV="docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md"
ISSUES="docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md"
DRIVER="docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md"

require_file() {
  [[ -f "$ROOT_DIR/$1" ]] \
    || { echo "missing virtualization/safety file: $1" >&2; exit 1; }
}

require_text() {
  grep -Fq -- "$2" "$ROOT_DIR/$1" \
    || { echo "missing virtualization/safety marker '$2' in $1" >&2; exit 1; }
}

for file in "$DOC" "$REQ" "$DEV" "$ISSUES" "$DRIVER"; do
  require_file "$file"
done

for req_id in HV-001 HV-002 HV-003 FW-S-005 NV-G-005 NV-F-009 KH-007 DEL-004; do
  require_text "$DOC" "$req_id"
  require_text "$REQ" "$req_id"
done

for marker in \
  '不开发 Hypervisor' \
  '跨 VM 通信必须保留 Uni Info Bus/SOA 语义' \
  'readonly fallback' \
  'virtualization_development_triggered=false' \
  'target_hardware_validated=false' \
  'production_ready=false'; do
  require_text "$DOC" "$marker"
done

require_text "$REQ" 'CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md'
require_text "$DEV" 'CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md'
require_text "$ISSUES" 'Safety State -> Safety Runtime -> ASIL/QM domain'
require_text "$DRIVER" 'HV-001..003'
require_text "$DRIVER" 'virtualization_development_triggered=false'

printf '%s\n' \
  'Central Brain virtualization/safety docs check passed' \
  'virtualization_development_triggered=false' \
  'target_hardware_validated=false' \
  'production_ready=false'
