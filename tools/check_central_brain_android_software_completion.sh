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
assert contract["schema_version"] == 4
assert contract["profile_id"] == "android13-repository-software-completion-v4"
assert contract["maturity"] == "repository_software_open_p4_r5_requirements_defined"
assert len(contract["requirement_ids"]) == len(set(contract["requirement_ids"]))
assert contract["classification"]["unclassified_repository_requirements"] == []
assert contract["phase_state"] == {
    "S2-P0": "SOFTWARE_COMPLETE",
    "S2-P1": "SOFTWARE_COMPLETE",
    "S2-P2": "SOFTWARE_COMPLETE",
    "S2-P3": "SOFTWARE_COMPLETE_EXTERNAL_PRODUCTION_BLOCKED",
    "S2-P4": "SOFTWARE_OPEN_REQUIREMENTS_DEFINED",
    "S2-P5": "SOFTWARE_COMPLETE_EXTERNAL_PRODUCTION_BLOCKED",
    "S2-P6": "SOFTWARE_COMPLETE_EXTERNAL_PRODUCTION_BLOCKED",
    "S2-P7": "SOFTWARE_COMPLETE_EXTERNAL_PRODUCTION_BLOCKED",
    "S2-P8": "EXTERNAL_BLOCKED",
    "S2-P9": "SOFTWARE_INTERFACE_COMPLETE_EXTERNAL_QUALIFICATION_BLOCKED",
}
claims = contract["claim_state"]
assert contract["classification"]["software_open"] == [
    f"P4-R5{suffix}" for suffix in "abcdefghijkl"]
assert {"S2-HMI-007", "S2-HMI-008", "S2-HMI-009", "S2-CTX-002",
        "S2-PER-001", "S2-INT-001", "S2-NAV-001", "S2-COM-001",
        "S2-MDL-002", "S2-OBS-002"}.issubset(
    contract["requirement_ids"])
assert claims["repository_software_requirements_complete"] is False
assert claims["open_repository_software_requirement_count"] == 12
assert claims["unclassified_repository_requirement_count"] == 0
assert claims["security_requirement_suspended"] is True
for key in (
    "production_vehicle_adapter_wired", "production_model_provider_wired",
    "production_signer_owner_approved", "vehicle_bus_accessed", "npu_accessed",
    "hardware_accessed", "production_ready", "target_hardware_validated",
):
    assert claims[key] is False
assert claims["implementation_stage"] == "P4-R5-REQUIREMENT"
assert contract["validation"]["p4_r4_requirement_gate"] is True
assert contract["validation"]["p4_r4_implementation"] is True
assert contract["validation"]["p4_r5_requirement_gate"] is True
assert contract["validation"]["p4_r5_implementation"] is False
assert contract["validation"]["android13_x86_64_client2_e2e"] is True
assert contract["validation"]["android13_arm64_completion_retest"] is True

readme = (root / "README.md").read_text(encoding="utf-8")
remaining = readme.split("### 未开发或外部阻塞", 1)[1].split("\n## ", 1)[0]
for stale in ("`IN_PROGRESS`", "`NOT_STARTED`", "待开发", "未开始"):
    if stale in remaining:
        raise SystemExit(f"unclassified repository work remains in README: {stale}")

requirements = (root / "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md").read_text(encoding="utf-8")
for stale in (
    "Runtime 执行闭环待开发", "APP-003 | Agent App | 通过 Session/Plan/Tool/Action/Effect 执行 | Stage 2 待开发",
    "FW-U-001 | Context | 车辆、用户、环境的版本化 snapshot | Stage 2 待开发",
    "FW-S-001 | Business Service | 场景编排必须生成可审计 plan/effect | Stage 2 待开发",
    "NV-F-006 | Data/Time Sync | 高频数据需时间域和 frame metadata | 未实现",
):
    if stale in requirements:
        raise SystemExit(f"stale requirement state remains: {stale}")

roadmap = (root / "docs/CENTRAL_BRAIN_ROADMAP.md").read_text(encoding="utf-8")
for stale in ("应用层完成 / Runtime 未完成", "W09-W10 待开发", "| S2-P6 | Event/Model 与高级 Memory 集成 |", "| S2-P7 | 质量与发布 | fault matrix、性能、隐私、安全、升级 | 未开始"):
    if stale in roadmap:
        raise SystemExit(f"stale roadmap state remains: {stale}")
PY

for doc in \
  README.md \
  docs/CENTRAL_BRAIN_ROADMAP.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md \
  docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md \
  docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md \
  docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md \
  docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md \
  docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md; do
  grep -Fq 'P10-R1 Android repository software completion' "$ROOT_DIR/$doc" \
    || { echo "P10-R1 completion marker missing: $doc" >&2; exit 1; }
done

bash "$ROOT_DIR/tools/check_central_brain_aios_stage2_design.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_runtime_contract_v2.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_android_runtime_acceptance.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_android_client2_p4_acceptance.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_android_client2_orchestration_migration.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_android_multimodal_model_io_hmi_requirement.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_android_cabin_hydration_assistance_requirement.sh" >/dev/null
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
  'repository_software_requirements_complete=false' \
  'open_repository_software_requirement_count=12' \
  'unclassified_repository_requirement_count=0' \
  'external_activation_requirements_classified=true' \
  'security_requirement_suspended=true' \
  'production_vehicle_adapter_wired=false' \
  'production_model_provider_wired=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'implementation_stage=P4-R5-REQUIREMENT'
