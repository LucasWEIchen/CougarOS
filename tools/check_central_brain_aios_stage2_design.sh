#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-001/003/004, FW-U-001/003/004/006/007, FW-S-001/003/005,
# NV-F-001/003/004/005/008/009/011/012, NV-G-003/004/005/006/007,
# NV-P-002/006, XSC-001/002/003/004/005/006, KH-003/006, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RESEARCH="$ROOT_DIR/docs/CENTRAL_BRAIN_AIOS_OPEN_SOURCE_AND_INDUSTRY_RESEARCH.md"
UX="$ROOT_DIR/docs/CENTRAL_BRAIN_AIOS_STAGE2_PRODUCT_UX_PLAN.md"
BACKLOG="$ROOT_DIR/docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md"
DESIGN="$ROOT_DIR/docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md"
COCKPIT_HMI="$ROOT_DIR/docs/CENTRAL_BRAIN_COCKPIT_HMI_CONTROL_LOOP_PLAN.md"
COCKPIT_HMI_MOCKUPS="$ROOT_DIR/docs/CENTRAL_BRAIN_COCKPIT_HMI_UX_DESIGN_MOCKUPS.md"
REQUIREMENTS="$ROOT_DIR/docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md"
ROADMAP="$ROOT_DIR/docs/CENTRAL_BRAIN_ROADMAP.md"
DEVIATIONS="$ROOT_DIR/docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md"
ISSUES="$ROOT_DIR/docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md"
DELIVERY="$ROOT_DIR/docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md"
DRIVER="$ROOT_DIR/docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md"
README="$ROOT_DIR/README.md"

for file in "$RESEARCH" "$UX" "$BACKLOG" "$DESIGN" "$COCKPIT_HMI" "$COCKPIT_HMI_MOCKUPS" "$REQUIREMENTS" "$ROADMAP" \
    "$DEVIATIONS" "$ISSUES" "$DELIVERY" "$DRIVER" "$README"; do
  [[ -f "$file" ]] || { echo "AIOS Stage 2 design file missing: $file" >&2; exit 1; }
done

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$file" \
    || { echo "AIOS Stage 2 marker missing in ${file#$ROOT_DIR/}: $marker" >&2; exit 1; }
}

for marker in \
  '# Central Brain Client2 中控 UI/UX 设计稿' \
  'cockpit_hmi_design_mockups_ready=true' \
  '## 5. 四个主视图' \
  '## 6. AIOS 自动化调用链' \
  '## 8. Android 开发映射' \
  'cockpit_demo_control_loop_implemented=false'; do
  require_text "$COCKPIT_HMI_MOCKUPS" "$marker"
done

for marker in \
  '# Central Brain AIOS 开源项目与车载行业架构调研' \
  '## 4. 开源项目源码结论' \
  '## 5. 车载行业设计输入' \
  '## 8. 不采纳或延后能力' \
  '4171a8ea2d56f7d119a109c5317e998575b679e1' \
  '49ae27c2ae983cfb92091b0dea9f7bc37a716479' \
  'c2e72045b26d18b1e2a9ad7098a40c7690f5263e' \
  'ce67f9276aa360d16b2e9e619d41c96e5d9f19d2' \
  'e49a6e3610d40399b2b5ae858ec5f3f136066915'; do
  require_text "$RESEARCH" "$marker"
done

for marker in \
  '# Central Brain AIOS Stage 2 产品与 UI/UX 计划' \
  '## 6. “我累了”场景详设' \
  '## 7. “我冷了”场景详设' \
  '## 10. 失败、补偿和撤销 UX' \
  '## 13. 产品验收指标' \
  '行驶中不会调整驾驶席靠背' \
  'UNKNOWN_RESTRICTED' \
  'PARTIALLY_COMPLETED'; do
  require_text "$UX" "$marker"
done

for marker in \
  '# Central Brain AIOS Stage 2 开发计划与最小工作包' \
  'P0-P7 总计约' \
  '136-184 人日' \
  '### `P1-W01` Session DTO/AIDL' \
  '### `P1-W02` Plan/Node DTO/AIDL' \
  '### `P1-W03` Typed Event DTO/AIDL' \
  '### `P1-W04` Effect/Approval DTO 扩展' \
  '### `P2-W10` Simulated Seat adapter' \
  '### `P3-W09` Restart recovery' \
  '### `P4-W12` Android device acceptance/fault/recovery' \
  '### `P8-W03` AaosCarPropertyEffectAdapter' \
  '## 16. 阶段性完成定义'; do
  require_text "$BACKLOG" "$marker"
done

for marker in \
  '# Central Brain AIOS 完整软件开发设计说明' \
  '`DEVELOPED`' \
  '`PROTOTYPE`' \
  '`CONTRACT_ONLY`' \
  '`NOT_STARTED`' \
  '`EXTERNAL_BLOCKED`' \
  '## 8. SDK 与 Binder 设计' \
  '## 11. Context 与 Vehicle Digital Twin' \
  '## 13. Durable Agent Graph Runtime' \
  '## 15. Effect 系统' \
  '## 23. Room v4 数据设计' \
  '## 30. 测试设计' \
  '## 34. Client2 中控闭环实施顺序' \
  '`P1-W01 Session DTO/AIDL`' \
  '`P1-W02 Plan/Node DTO/AIDL`' \
  '`P1-W03 Typed Event DTO/AIDL`' \
  '`P1-W04 Effect/Approval DTO 扩展`'; do
  require_text "$DESIGN" "$marker"
done

for marker in \
  '# Central Brain 中控屏 HVAC/Seat 演示闭环规划' \
  '### 2.3 中控屏闭环 UI/UX 全量清单' \
  '### 2.4 单项能力的闭环完成定义' \
  '## 5. HVAC 控制页' \
  '## 6. Seat 控制页' \
  '## 14. 最小工作包与工作量' \
  'HMI-AC-01' \
  'HMI-ST-02' \
  'HMI-CL-06' \
  'HMI-CL-09' \
  'HMI-CL-10' \
  'SIMULATED' \
  'cockpit_demo_control_loop_implemented=false'; do
  require_text "$COCKPIT_HMI" "$marker"
done

derived_ids=(
  S2-UX-001 S2-UX-002 S2-UX-003 S2-HMI-001 S2-HMI-002 S2-HMI-003
  S2-HMI-004 S2-HMI-005 S2-HMI-006 S2-SES-001 S2-CTX-001 S2-TWN-001
  S2-SCN-001 S2-GRF-001 S2-SAF-001 S2-EFF-001 S2-ADP-001 S2-TOL-001
  S2-MEM-001 S2-EVT-001 S2-MDL-001 S2-ADP-002 S2-OBS-001 S2-REL-001
)
for id in "${derived_ids[@]}"; do
  require_text "$REQUIREMENTS" "$id"
  require_text "$BACKLOG" "$id"
  require_text "$DESIGN" "$id"
done

require_text "$REQUIREMENTS" 'AIOS Stage 2 derived requirement baseline'
require_text "$REQUIREMENTS" 'A user confirmation cannot override this hard interlock'
require_text "$REQUIREMENTS" 'return adapter unavailable rather than silently falling back to simulation.'
require_text "$ROADMAP" '| S2-P0 | 完整 AIOS Stage 2 设计冻结'
require_text "$ROADMAP" '| S2-P1 | Runtime Contract v2'
require_text "$ROADMAP" '`P1-W01 Session DTO/AIDL` 已完成'
require_text "$ROADMAP" '`P1-W02 Plan/Node DTO/AIDL` 已完成'
require_text "$ROADMAP" '`P1-W03 Typed Event DTO/AIDL` 已完成'
require_text "$ROADMAP" '`P1-W04 Effect/Approval DTO 扩展` 已完成'
require_text "$ROADMAP" '`P1-W05 SDK facade v2` 已完成'
require_text "$ROADMAP" '`P1-W06 Room v4 schema` 已完成'
require_text "$ROADMAP" '`P1-W07 Contract v2 aggregate check` 已完成'
require_text "$ROADMAP" '`P2-W01 canonical vehicle signal types` 已完成'
require_text "$ROADMAP" '`P2-W02 vehicle capability catalog` 已完成'
require_text "$ROADMAP" '`P2-W03 VehicleDigitalTwinStore` 已完成'
require_text "$ROADMAP" '`P2-W04 ContextSnapshotBuilder` 已完成'
require_text "$ROADMAP" '`P2-W05 Scenario manifest/schema` 已完成'
require_text "$ROADMAP" '`P2-W06 DeterministicScenarioResolver` 已完成'
require_text "$ROADMAP" '`P2-W07 ScenarioPlanCompiler` 已完成'
require_text "$ROADMAP" '`P2-W08 SimulatedVehicleAdapter base` 已完成'
require_text "$ROADMAP" '`P2-W09 Simulated HVAC adapter` 已完成'
require_text "$ROADMAP" '`P2-W10 Simulated Seat adapter` 已完成'
require_text "$ROADMAP" '`P2-W11 Simulated Media/Nav adapters` 已完成'
require_text "$ROADMAP" '`P2-W12 Debug Context Controller` 已完成'
require_text "$ROADMAP" '`P3-W01 AgentGraphRuntime state machine` 已完成'
require_text "$ROADMAP" '`P3-W02 Typed node executors` 已完成'
require_text "$ROADMAP" '`P3-W03 CheckpointSerializer` 已完成'
require_text "$ROADMAP" '`P3-W04 Retry/Timeout policy` 已完成'
require_text "$ROADMAP" '`P3-W05 Durable approval interrupt` 已完成'
require_text "$ROADMAP" '`P3-W06 EffectCoordinator` 已完成'
require_text "$ROADMAP" '`P3-W07 Effect verification/reconciliation` 已完成'
require_text "$ROADMAP" '`P3-W08 Compensation/Undo` 已完成'
require_text "$ROADMAP" '`P3-W09 Restart recovery` 已完成'
require_text "$ROADMAP" '下一实现工作包为 `P4-W01 Bridge session/event API migration`'
require_text "$DEVIATIONS" '## DEV-024 Stage 2 车辆多设备动作先使用 Digital Twin 仿真'
require_text "$DEVIATIONS" '## DEV-025 Client2 patched APK 是演示 HMI，不是量产 AAOS 产品 HMI'
require_text "$DEVIATIONS" '## DEV-032 P2-W03 Digital Twin 是进程内非持久化 foundation'
require_text "$DEVIATIONS" '## DEV-033 P2-W04 Context 是非 production-trusted 的进程内 foundation'
require_text "$DEVIATIONS" '## DEV-034 P2-W05 Scenario 只有 build checksum，不是 production-signed catalog'
require_text "$DEVIATIONS" '## DEV-035 P2-W06 Resolver 是固定规则、process-local availability foundation'
require_text "$DEVIATIONS" '## DEV-036 P2-W07 Compiler 是 digest-only、未发布的 Plan foundation'
require_text "$DEVIATIONS" '## DEV-037 P2-W08 仿真基类不是 production adapter 或真实车辆回读'
require_text "$DEVIATIONS" '## DEV-038 P2-W09 HVAC target 与 Twin 不是 OEM 车控合同'
require_text "$DEVIATIONS" '## DEV-039 P2-W10 Seat safety 是 debug Runtime-owned gate，不是 OEM Safety authority'
require_text "$DEVIATIONS" '## DEV-040 P2-W11 synthetic Media/Navigation 不是平台播放器或真实导航'
require_text "$DEVIATIONS" '## DEV-041 P2-W12 debug controller 不是 production Context 或车辆控制 authority'
require_text "$DEVIATIONS" '## DEV-042 P3-W01 Graph Runtime 非 durable 且不执行 executor'
require_text "$DEVIATIONS" '## DEV-043 P3-W02 typed executor 非 production execution'
require_text "$DEVIATIONS" '## DEV-044 P3-W03 checkpoint serializer 尚未形成 durable Graph recovery'
require_text "$DEVIATIONS" '## DEV-045 P3-W04 retry/timeout policy 尚未接 Graph 或 production Effect'
require_text "$DEVIATIONS" '## DEV-046 P3-W05 approval interrupt 尚未形成 durable Graph approval'
require_text "$DEVIATIONS" '## DEV-047 P3-W06 EffectCoordinator 尚未形成 durable production Effect pipeline'
require_text "$DEVIATIONS" '## DEV-048 P3-W07 Effect verification/reconciliation 尚未形成 durable production readback loop'
require_text "$DEVIATIONS" '## DEV-049 P3-W08 Compensation/Undo 尚未形成 durable execution，P1 compensation state 不可达'
require_text "$DEVIATIONS" '## DEV-050 P3-W09 Restart recovery repository 尚未接 Runtime/Binder，P3 durable 名称不能解释为 production activation'
require_text "$ISSUES" '## ISSUE-029 “我累了”场景的驾驶席座椅安全策略与批准 authority'
require_text "$ISSUES" '## ISSUE-030 黑盒 Android 13 的车辆控制 API、权限和 owner 未确定'
require_text "$ISSUES" '## ISSUE-031 场景目录、长期记忆和主动执行的产品/隐私 owner 未确定'
require_text "$ISSUES" '## ISSUE-033 Client2 HVAC/Seat 中控演示闭环缺口'
require_text "$DELIVERY" '## 2026-07-17 AIOS Stage 2 交付范围'
require_text "$DELIVERY" '## 2026-07-16 Client2 中控 HVAC/Seat 交付规划'
require_text "$DRIVER" '## 2026-07-15 AIOS Stage 2 Driver/HAL 边界'
require_text "$DRIVER" '## 2026-07-16 Client2 中控 HVAC/Seat 规划边界'
require_text "$README" 'design_baseline_complete=true'
require_text "$README" 'cockpit_hmi_design_mockups_ready=true'
require_text "$README" 'aios_intent_orchestration_ux_ready=true'
require_text "$README" 'cockpit_hmi_1920x1080_safe_frame_verified=true'
require_text "$README" 'cockpit_hmi_translucent_material_ready=true'
require_text "$README" 'cockpit_demo_control_loop_implemented=false'
require_text "$README" 'CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md'
require_text "$README" 'CENTRAL_BRAIN_COCKPIT_HMI_CONTROL_LOOP_PLAN.md'

for file in "$UX" "$BACKLOG" "$DESIGN" "$COCKPIT_HMI" "$REQUIREMENTS" "$DEVIATIONS" "$DRIVER"; do
  require_text "$file" 'driver_development_triggered=false'
  require_text "$file" 'virtualization_development_triggered=false'
done

python3 -B - "$BACKLOG" "$DEVIATIONS" "$ISSUES" <<'PY'
import pathlib
import re
import sys

backlog = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
deviations = pathlib.Path(sys.argv[2]).read_text(encoding="utf-8")
issues = pathlib.Path(sys.argv[3]).read_text(encoding="utf-8")

work_packages = re.findall(r"^### `((?:P[0-9])-W[0-9]{2})`", backlog, re.MULTILINE)
if len(work_packages) != 79:
    raise SystemExit(f"AIOS Stage 2 backlog work package count changed unexpectedly: {len(work_packages)} != 79")
if len(work_packages) != len(set(work_packages)):
    raise SystemExit("AIOS Stage 2 backlog contains duplicate work package IDs")

for current, expected in ((deviations, [f"DEV-{n:03d}" for n in range(1, 27)]),
                          (issues, [f"ISSUE-{n:03d}" for n in range(1, 34)])):
    present = set(re.findall(r"(?:^## |^\| )(DEV-[0-9]{3}|ISSUE-[0-9]{3})\b", current, re.MULTILINE))
    missing = [item for item in expected if item not in present]
    if missing:
        raise SystemExit(f"architecture tracking IDs missing: {missing}")

print(f"aios_stage2_work_package_count={len(work_packages)}")
PY

bash "$ROOT_DIR/tools/check_central_brain_cockpit_hmi_design.sh"

echo "Central Brain AIOS Stage 2 design check passed"
