#!/usr/bin/env bash
set -euo pipefail

# Aggregate production design trace for the former Stage 2 document suite.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REQUIREMENTS="$ROOT_DIR/docs/CENTRAL_BRAIN_REQUIREMENTS.md"
DEVELOPMENT="$ROOT_DIR/docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md"

bash "$ROOT_DIR/tools/check_central_brain_production_document_set.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_root_readme.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_cockpit_hmi_design.sh" >/dev/null

required_contracts=(
  central-brain/contracts/central_brain_android_p8_target_capability_discovery.json
  central-brain/contracts/central_brain_android_p9_performance_budget.json
  central-brain/contracts/central_brain_android_p9_stability_fault_matrix.json
  central-brain/contracts/central_brain_android_p9_security_boundary_inventory.json
  central-brain/contracts/central_brain_android_p9_privacy_data_inventory.json
  central-brain/contracts/central_brain_android_p9_production_release_admission.json
  central-brain/contracts/central_brain_android_p9_driver_safety_admission.json
)
for relative in "${required_contracts[@]}"; do
  [[ -f "$ROOT_DIR/$relative" ]] || {
    echo "production design contract is missing: $relative" >&2
    exit 1
  }
done

for id in \
  S2-SES-001 S2-CTX-001 S2-TWN-001 S2-SCN-001 S2-GRF-001 \
  S2-SAF-001 S2-EFF-001 S2-ADP-001 S2-ADP-002 S2-TOL-001 \
  S2-MEM-001 S2-EVT-001 S2-MDL-001 S2-OBS-001 S2-REL-001; do
  grep -Fq "$id" "$REQUIREMENTS" || {
    echo "production design requirement is missing: $id" >&2
    exit 1
  }
done

for section in \
  '## 8. Session 与 Persistence' \
  '## 10. Scenario 与 Plan' \
  '## 11. Agent Graph Runtime' \
  '## 12. Governance 与 Approval' \
  '## 13. Tool、Skill 与 Memory' \
  '## 14. Event Broker' \
  '## 15. Model Runtime 与 OpenClaw' \
  '## 16. Effect 与 Adapter' \
  '## 17. Client2 HMI 详设' \
  '## 18. Native Runtime'; do
  grep -Fq "$section" "$DEVELOPMENT" || {
    echo "production development section is missing: $section" >&2
    exit 1
  }
done

printf '%s\n' \
  'Central Brain consolidated production design check passed' \
  'production_document_set_count=3' \
  'production_design_contract_trace_ready=true' \
  'legacy_stage2_document_suite_required=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
