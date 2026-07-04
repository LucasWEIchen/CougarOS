#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing required virtualization/safety file: $path" >&2
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

require_file "docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md"
require_file "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md"
require_file "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md"
require_file "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md"
require_file "docs/CENTRAL_BRAIN_PLATFORM_DELTA.md"
require_file "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md"

for req_id in HV-001 HV-002 HV-003 FW-S-005 NV-G-005 NV-F-009 KH-007 DEL-004; do
  require_text "docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md" "$req_id"
done

require_text "docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md" "不开发 Hypervisor"
require_text "docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md" "跨 VM 通信必须保留 Uni Info Bus/SOA 语义"
require_text "docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md" "readonly fallback"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "Safety State -> Safety Runtime -> ASIL/QM domain"
require_text "docs/CENTRAL_BRAIN_PLATFORM_DELTA.md" "CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "HV-001..003"

echo "Central Brain virtualization/safety docs check passed"
