#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-001/003/004/009/010, FW-U-001..008, FW-S-001/003..006,
# NV-F-001/006/008/009/011/012, NV-G-001..007, NV-P-002/005/007,
# KH-003/004, S2-UX-001..003, S2-HMI-001..009, S2-SES/CTX/TWN/PER/INT/
# SCN/GRF/SAF/EFF/ADP/TOL/NAV/COM/MEM/EVT/MDL/OBS/REL, XSC-001..006,
# DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_software_completion_v1.json"

python3 -B - "$CONTRACT" "$ROOT_DIR" <<'PY'
import json
import pathlib
import sys

contract = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
root = pathlib.Path(sys.argv[2])
assert contract["schema_version"] == 5
assert contract["profile_id"] == "android13-repository-software-completion-v5"
assert contract["maturity"] == "repository_software_complete_external_production_blocked"
assert len(contract["requirement_ids"]) == len(set(contract["requirement_ids"]))
assert contract["classification"]["unclassified_repository_requirements"] == []
assert contract["phase_state"] == {
    "S2-P0": "SOFTWARE_COMPLETE",
    "S2-P1": "SOFTWARE_COMPLETE",
    "S2-P2": "SOFTWARE_COMPLETE",
    "S2-P3": "SOFTWARE_COMPLETE_EXTERNAL_PRODUCTION_BLOCKED",
    "S2-P4": "SOFTWARE_COMPLETE_EXTERNAL_PRODUCTION_BLOCKED",
    "S2-P5": "SOFTWARE_COMPLETE_EXTERNAL_PRODUCTION_BLOCKED",
    "S2-P6": "SOFTWARE_COMPLETE_EXTERNAL_PRODUCTION_BLOCKED",
    "S2-P7": "SOFTWARE_COMPLETE_EXTERNAL_PRODUCTION_BLOCKED",
    "S2-P8": "EXTERNAL_BLOCKED",
    "S2-P9": "SOFTWARE_INTERFACE_COMPLETE_EXTERNAL_QUALIFICATION_BLOCKED",
}
claims = contract["claim_state"]
assert contract["classification"]["software_open"] == []
assert {"S2-HMI-007", "S2-HMI-008", "S2-HMI-009", "S2-CTX-002",
        "S2-PER-001", "S2-INT-001", "S2-NAV-001", "S2-COM-001",
        "S2-MDL-002", "S2-OBS-002"}.issubset(
    contract["requirement_ids"])
assert claims["repository_software_requirements_complete"] is True
assert claims["open_repository_software_requirement_count"] == 0
assert claims["unclassified_repository_requirement_count"] == 0
assert claims["security_requirement_suspended"] is True
for key in (
    "production_vehicle_adapter_wired", "production_model_provider_wired",
    "production_signer_owner_approved", "vehicle_bus_accessed", "npu_accessed",
    "hardware_accessed", "production_ready", "target_hardware_validated",
):
    assert claims[key] is False
assert claims["implementation_stage"] == "P10-R1"
assert contract["validation"]["p4_r4_requirement_gate"] is True
assert contract["validation"]["p4_r4_implementation"] is True
assert contract["validation"]["p4_r5_requirement_gate"] is True
assert contract["validation"]["p4_r5_implementation"] is True
assert contract["validation"]["p4_r6_requirement_gate"] is True
assert contract["validation"]["p4_r6_implementation"] is True
assert contract["validation"]["android13_x86_64_client2_e2e"] is True
assert contract["validation"]["android13_arm64_completion_retest"] is True

readme = (root / "README.md").read_text(encoding="utf-8")
for canonical in (
    "docs/CENTRAL_BRAIN_REQUIREMENTS.md",
    "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md",
    "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md",
):
    if canonical not in readme:
        raise SystemExit(f"canonical document is not linked from README: {canonical}")

requirements = (root / "docs/CENTRAL_BRAIN_REQUIREMENTS.md").read_text(encoding="utf-8")
for stale in (
    "Runtime 执行闭环待开发", "APP-003 | Agent App | 通过 Session/Plan/Tool/Action/Effect 执行 | Stage 2 待开发",
    "FW-U-001 | Context | 车辆、用户、环境的版本化 snapshot | Stage 2 待开发",
    "FW-S-001 | Business Service | 场景编排必须生成可审计 plan/effect | Stage 2 待开发",
    "NV-F-006 | Data/Time Sync | 高频数据需时间域和 frame metadata | 未实现",
):
    if stale in requirements:
        raise SystemExit(f"stale requirement state remains: {stale}")

if "`P10-R1` P10-R1 Android repository software completion" not in requirements:
    raise SystemExit("P10-R1 completion work package is missing")
PY

bash "$ROOT_DIR/tools/check_central_brain_production_document_set.sh" >/dev/null

bash "$ROOT_DIR/tools/check_central_brain_aios_stage2_design.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_runtime_contract_v2.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_android_runtime_acceptance.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_android_client2_p4_acceptance.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_android_client2_orchestration_migration.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_android_multimodal_model_io_hmi_requirement.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_android_cabin_shopping_route_planning.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_android_p5_physical_acceptance.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_android_p6_physical_acceptance.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_android_p7_physical_acceptance.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_android_p9_physical_acceptance.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_npu_interface.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_python_prototype_retirement.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_delivery_docs.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_root_readme.sh" >/dev/null

printf '%s\n' \
  'Central Brain Android repository software completion state check passed' \
  'repository_software_requirements_complete=true' \
  'open_repository_software_requirement_count=0' \
  'unclassified_repository_requirement_count=0' \
  'external_activation_requirements_classified=true' \
  'security_requirement_suspended=true' \
  'production_vehicle_adapter_wired=false' \
  'production_model_provider_wired=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'implementation_stage=P10-R1'
