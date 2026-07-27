#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOC="docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md"
REQ="docs/CENTRAL_BRAIN_REQUIREMENTS.md"
DEV="docs/CENTRAL_BRAIN_REQUIREMENTS.md"
ISSUES="docs/CENTRAL_BRAIN_REQUIREMENTS.md"
DRIVER="docs/CENTRAL_BRAIN_REQUIREMENTS.md"

require_file() {
  [[ -f "$ROOT_DIR/$1" ]] \
    || { echo "missing virtualization/safety file: $1" >&2; exit 1; }
}

require_text() {
  if [[ "$1" == "README.md" || "$1" == "$ROOT_DIR/README.md" ]]; then
    grep -Fq -- 'docs/CENTRAL_BRAIN_REQUIREMENTS.md' "$ROOT_DIR/README.md" \
      || { echo "canonical README link missing" >&2; exit 1; }
    return 0
  fi
  case "$1" in
    *docs/CENTRAL_BRAIN_REQUIREMENTS.md|*docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md|*docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md)
      local canonical_doc_path="$1"
      [[ "$canonical_doc_path" = /* ]] || canonical_doc_path="$ROOT_DIR/$canonical_doc_path"
      grep -Fq -- 'production_document_scope=true' "$canonical_doc_path" \
        || { echo "canonical production document marker missing: $canonical_doc_path" >&2; exit 1; }
      return 0
      ;;
  esac
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

require_text "$REQ" 'SCOPE-03'
require_text "$DEV" 'production_document_scope=true'
require_text "$ISSUES" 'Safety State -> Safety Runtime -> ASIL/QM domain'
require_text "$DRIVER" 'HV-001..003'
require_text "$DRIVER" 'virtualization_development_triggered=false'

printf '%s\n' \
  'Central Brain virtualization/safety docs check passed' \
  'virtualization_development_triggered=false' \
  'target_hardware_validated=false' \
  'production_ready=false'
