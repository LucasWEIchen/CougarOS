#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-001..005, S2-UX-001..003, S2-HMI-001..009.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REQUIREMENTS="$ROOT_DIR/docs/CENTRAL_BRAIN_REQUIREMENTS.md"
ARCHITECTURE="$ROOT_DIR/docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md"
DEVELOPMENT="$ROOT_DIR/docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md"
PROJECT="$ROOT_DIR/apk-labs/client2-central-brain"

bash "$ROOT_DIR/tools/check_central_brain_production_document_set.sh" >/dev/null

for path in \
  "$PROJECT/patches/main_layout.central_brain_panel.xml" \
  "$PROJECT/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java" \
  "$PROJECT/bridge/src/com/centralbrain/client2/CockpitHmiReducer.java" \
  "$PROJECT/bridge/src/com/centralbrain/client2/CockpitHmiState.java"; do
  [[ -f "$path" ]] || {
    echo "production HMI source mapping is missing: ${path#$ROOT_DIR/}" >&2
    exit 1
  }
done

for id in \
  APP-001 APP-002 APP-003 APP-004 APP-005 \
  S2-UX-001 S2-UX-002 S2-UX-003 \
  S2-HMI-001 S2-HMI-002 S2-HMI-003 S2-HMI-004 S2-HMI-005 \
  S2-HMI-006 S2-HMI-007 S2-HMI-008 S2-HMI-009; do
  grep -Fq "$id" "$REQUIREMENTS" || {
    echo "production HMI requirement is missing: $id" >&2
    exit 1
  }
done

grep -Fq 'Client2 HMI' "$ARCHITECTURE"
grep -Fq '## 17. Client2 HMI 详设' "$DEVELOPMENT"
grep -Fq '### 17.4 HVAC 与座椅' "$DEVELOPMENT"
grep -Fq '### 17.5 显示与触摸' "$DEVELOPMENT"

printf '%s\n' \
  'Central Brain production HMI design trace check passed' \
  'cockpit_hmi_production_requirements_traced=true' \
  'cockpit_hmi_source_mapping_present=true' \
  'cockpit_hmi_preview_assets_required=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
