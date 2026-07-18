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
TARGET_DISCOVERY="$ROOT_DIR/docs/CENTRAL_BRAIN_TARGET_CAPABILITY_DISCOVERY.md"
TARGET_DISCOVERY_CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_p8_target_capability_discovery.json"
PERFORMANCE_BUDGET="$ROOT_DIR/docs/CENTRAL_BRAIN_PERFORMANCE_BUDGETS.md"
PERFORMANCE_BUDGET_CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_p9_performance_budget.json"
STABILITY_MATRIX="$ROOT_DIR/docs/CENTRAL_BRAIN_STABILITY_FAULT_MATRIX.md"
STABILITY_MATRIX_CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_p9_stability_fault_matrix.json"
SECURITY_REVIEW="$ROOT_DIR/docs/CENTRAL_BRAIN_SECURITY_REVIEW_FUZZ.md"
SECURITY_CORPUS_CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_p9_parser_security_corpus.json"
SECURITY_IDENTITY_CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_p9_identity_replay_security_corpus.json"
SECURITY_BOUNDARY_CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_p9_security_boundary_inventory.json"
PRIVACY_LIFECYCLE="$ROOT_DIR/docs/CENTRAL_BRAIN_PRIVACY_DATA_LIFECYCLE.md"
PRIVACY_INVENTORY_CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_p9_privacy_data_inventory.json"
PRIVACY_POLICY_CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_p9_privacy_policy_admission.json"
PRIVACY_REDACTION_CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_p9_privacy_redaction_audit.json"
PRODUCTION_RELEASE="$ROOT_DIR/docs/CENTRAL_BRAIN_PRODUCTION_RELEASE_ADMISSION.md"
PRODUCTION_RELEASE_CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_p9_production_release_admission.json"
PRODUCTION_RELEASE_PROBE_CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_p9_production_release_metadata_probe.json"
DRIVER_SAFETY="$ROOT_DIR/docs/CENTRAL_BRAIN_DRIVER_SAFETY_ADMISSION.md"
DRIVER_SAFETY_CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_p9_driver_safety_admission.json"
DRIVER_SAFETY_PROBE_CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_p9_driver_safety_audit_probe.json"

for file in "$RESEARCH" "$UX" "$BACKLOG" "$DESIGN" "$COCKPIT_HMI" "$COCKPIT_HMI_MOCKUPS" "$REQUIREMENTS" "$ROADMAP" \
    "$DEVIATIONS" "$ISSUES" "$DELIVERY" "$DRIVER" "$README" "$TARGET_DISCOVERY" "$TARGET_DISCOVERY_CONTRACT" \
    "$PERFORMANCE_BUDGET" "$PERFORMANCE_BUDGET_CONTRACT" "$STABILITY_MATRIX" "$STABILITY_MATRIX_CONTRACT" \
    "$SECURITY_REVIEW" "$SECURITY_CORPUS_CONTRACT" "$SECURITY_IDENTITY_CONTRACT" "$SECURITY_BOUNDARY_CONTRACT" \
    "$PRIVACY_LIFECYCLE" "$PRIVACY_INVENTORY_CONTRACT" "$PRIVACY_POLICY_CONTRACT" \
    "$PRIVACY_REDACTION_CONTRACT" "$PRODUCTION_RELEASE" "$PRODUCTION_RELEASE_CONTRACT" \
    "$PRODUCTION_RELEASE_PROBE_CONTRACT" "$DRIVER_SAFETY" "$DRIVER_SAFETY_CONTRACT" \
    "$DRIVER_SAFETY_PROBE_CONTRACT"; do
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
  '### `P5-W01` Tool manifest/schema' \
  '### `P5-W02` ToolRegistry/Resolver' \
  '### `P5-W06` WorkingMemoryStore' \
  '### `P5-W07` ProfileMemoryStore' \
  '### `P5-W08` EpisodicMemoryStore' \
  '### `P5-W09` ContextBudgetManager' \
  '### `P5-W10` Memory consent HMI/API' \
  '### `P6-W01` EventBroker interface/in-process implementation' \
  '### `P6-W04` Proactive consent/policy' \
  '### `P6-W05` Context source adapters' \
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
require_text "$ROADMAP" '`P4-W01 Bridge session/event API migration` 已完成'
require_text "$ROADMAP" '`P4-W02 Cockpit HMI state/reducer/reconnect` 已完成'
require_text "$ROADMAP" '`P4-W03 Intent-first four-stage overlay shell` 已完成'
require_text "$ROADMAP" '`P4-W04 HVAC control surface` 已完成'
require_text "$ROADMAP" '`P4-W05 Seat control surface` 已完成'
require_text "$ROADMAP" '`P4-W06 Plan/effect execution timeline` 已完成'
require_text "$ROADMAP" '`P4-W07 Approval/partial/retry/undo UX` 已完成 application-layer 投影'
require_text "$ROADMAP" '`P4-W08 Driving restriction renderer` 已完成'
require_text "$ROADMAP" '`P4-W09 Engineer simulation drawer` 已完成'
require_text "$ROADMAP" '### 2026-07-18 P4-W10 progress'
require_text "$ROADMAP" '### 2026-07-18 P4-W11 progress'
require_text "$ROADMAP" '### 2026-07-18 P4-W12 progress'
require_text "$ROADMAP" '### 2026-07-18 P5-W01 progress'
require_text "$ROADMAP" '### 2026-07-18 P5-W02 progress'
require_text "$ROADMAP" '### 2026-07-18 P5-W03 progress'
require_text "$ROADMAP" '### 2026-07-18 P5-W04 progress'
require_text "$ROADMAP" '### 2026-07-18 P5-W05 progress'
require_text "$ROADMAP" '### 2026-07-18 P5-W06 progress'
require_text "$ROADMAP" '### 2026-07-18 P5-W07 progress'
require_text "$ROADMAP" '### 2026-07-18 P5-W08 progress'
require_text "$ROADMAP" '### 2026-07-18 P5-W09 progress'
require_text "$ROADMAP" '### 2026-07-18 P5-W10 progress'
require_text "$BACKLOG" '状态：`DONE`（2026-07-17）；3 人日；需求：`S2-HMI-001/003/004/005`、`S2-ADP-001`。'
require_text "$BACKLOG" '状态：`DONE`（2026-07-17）；2 人日；需求：`S2-UX-001`、`S2-HMI-005`、`XSC-001`'
require_text "$BACKLOG" '### `P4-W02` Cockpit HMI state/reducer/reconnect'
require_text "$BACKLOG" '### `P4-W03` Intent-first four-stage overlay shell'
require_text "$BACKLOG" '状态：`COMPLETE`（2026-07-18）；2 人日；需求：`S2-HMI-001..006`、`S2-SCN-001`。'
require_text "$BACKLOG" '状态：`COMPLETE`（2026-07-18）；1.5-2.5 人日；需求：`S2-UX-003`、`S2-HMI-001/002`。'
require_text "$BACKLOG" '状态：`COMPLETE`（2026-07-18，application acceptance only）；2.5-4 人日；需求：P4 全部。'
require_text "$BACKLOG" '状态：`DEVELOPED`；2 人日；需求：`S2-TOL-001`。'
require_text "$BACKLOG" '状态：`DEVELOPED`（2026-07-18）；2 人日；需求：`S2-TOL-001`。'
require_text "$BACKLOG" '状态：`DEVELOPED`（2026-07-18）；2 人日；需求：`S2-MEM-001`、`S2-SAF-001`、`S2-OBS-001`。'
require_text "$BACKLOG" '状态：`DEVELOPED`（2026-07-18）；3 人日；需求：`S2-MEM-001`、`S2-SAF-001`、`S2-OBS-001`。'
require_text "$BACKLOG" '状态：`DEVELOPED`（2026-07-18）；2.5 人日；需求：`S2-MEM-001`、`S2-SAF-001`、`S2-OBS-001`。'
require_text "$BACKLOG" '状态：`DEVELOPED`（2026-07-18）；2 人日；需求：`S2-MEM-001`、`S2-MDL-001`、`S2-SAF-001`、'
require_text "$BACKLOG" '状态：`DEVELOPED`；2 人日；需求：`S2-MEM-001`、`S2-UX-003`。'
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
require_text "$DEVIATIONS" '## DEV-068 P5-W06 process-local Working Memory is not production Memory'
require_text "$ISSUES" '## ISSUE-041 Working Memory session owner, tokenizer and storage publication'
require_text "$DEVIATIONS" '## DEV-069 P5-W07 contract cipher is not production encrypted storage'
require_text "$ISSUES" '## ISSUE-042 Profile Memory authority, key owner and durable repository publication'
require_text "$DEVIATIONS" '## DEV-070 P5-W08 process-local episodic summaries are not production Memory'
require_text "$ISSUES" '## ISSUE-043 Episodic Memory policy, repository and erase authority publication'
require_text "$DEVIATIONS" '## DEV-071 P5-W09 decision-only context budget is not production model budgeting'
require_text "$ISSUES" '## ISSUE-044 Context tokenizer, summary executor and budget authority publication'
require_text "$DEVIATIONS" '## DEV-072 P5-W10 process-local Memory consent projection is not production Memory control'
require_text "$ISSUES" '## ISSUE-045 Memory consent authority and repository mutation publication'
require_text "$DEVIATIONS" '## DEV-073 P6-W01 process-local Event Broker is not durable middleware'
require_text "$ISSUES" '## ISSUE-046 Event Broker durable repository and production middleware publication'
require_text "$DEVIATIONS" '## DEV-074 P6-W03 process-local TriggerEngine is not production proactive intelligence'
require_text "$DEVIATIONS" '## DEV-075 P6-W04 process-local proactive consent is not production authorization'
require_text "$DEVIATIONS" '## DEV-076 P6-W05 Context source adapters are contracts, not production providers'
require_text "$DEVIATIONS" '## DEV-042 P3-W01 Graph Runtime 非 durable 且不执行 executor'
require_text "$DEVIATIONS" '## DEV-043 P3-W02 typed executor 非 production execution'
require_text "$DEVIATIONS" '## DEV-044 P3-W03 checkpoint serializer 尚未形成 durable Graph recovery'
require_text "$DEVIATIONS" '## DEV-045 P3-W04 retry/timeout policy 尚未接 Graph 或 production Effect'
require_text "$DEVIATIONS" '## DEV-046 P3-W05 approval interrupt 尚未形成 durable Graph approval'
require_text "$DEVIATIONS" '## DEV-047 P3-W06 EffectCoordinator 尚未形成 durable production Effect pipeline'
require_text "$DEVIATIONS" '## DEV-048 P3-W07 Effect verification/reconciliation 尚未形成 durable production readback loop'
require_text "$DEVIATIONS" '## DEV-049 P3-W08 Compensation/Undo 尚未形成 durable execution，P1 compensation state 不可达'
require_text "$DEVIATIONS" '## DEV-050 P3-W09 Restart recovery repository 尚未接 Runtime/Binder，P3 durable 名称不能解释为 production activation'
require_text "$DEVIATIONS" '## DEV-051 Client2 UI alias 仍是兼容边界，legacy static owner 已解除'
require_text "$DEVIATIONS" '## DEV-052 P4-W02 checkpoint 是 app-private 恢复层，不是量产加密 HMI store'
require_text "$DEVIATIONS" '## DEV-053 P4-W03 固定 1920x1080 安全框和 unavailable 投影不是量产多屏 HMI'
require_text "$DEVIATIONS" '## DEV-054 P4-W04 Session V1 HVAC 参数兼容层不是 versioned typed parameter transport'
require_text "$DEVIATIONS" '## DEV-055 P4-W05 Session V1 Seat 参数和 approval 兼容层不是 versioned typed transport'
require_text "$DEVIATIONS" '## DEV-056 P4-W06 timeline projection 完整但 Runtime execution event 未发布'
require_text "$DEVIATIONS" '## DEV-057 P4-W07 recovery command details 未发布到 Client2'
require_text "$DEVIATIONS" '## DEV-058 P4-W08 driving presentation lacks production trusted global Context'
require_text "$DEVIATIONS" '## DEV-059 P4-W09 debug Context projection is not production authority'
require_text "$DEVIATIONS" '## DEV-060 P4-W10 catalog participation is not Runtime Plan publication'
require_text "$DEVIATIONS" '## DEV-061 P4-W11 display allowlist is not OEM multi-display qualification'
require_text "$DEVIATIONS" '## DEV-062 P4-W12 application acceptance is not HMI-D4 execution closure'
require_text "$DEVIATIONS" '## DEV-063 P5-W01 Tool contract is not Tool execution'
require_text "$DEVIATIONS" '## DEV-064 P5-W02 Registry usability is not execution authority'
require_text "$DEVIATIONS" '### P6-W02 pressure queues remain process-local'
require_text "$ISSUES" '## ISSUE-029 “我累了”场景的驾驶席座椅安全策略与批准 authority'
require_text "$ISSUES" '## ISSUE-030 黑盒 Android 13 的车辆控制 API、权限和 owner 未确定'
require_text "$ISSUES" '## ISSUE-031 场景目录、长期记忆和主动执行的产品/隐私 owner 未确定'
require_text "$ISSUES" '## ISSUE-033 Client2 HVAC/Seat 中控演示闭环缺口'
require_text "$ISSUES" '## ISSUE-036 Tool production owner, health source and execution authority'
require_text "$ISSUES" '## ISSUE-037 Tool health publisher and production registry ownership'
require_text "$ISSUES" 'P4-W01 进展：Client2 已不再通过单次 `TaskResult` 驱动文本区'
require_text "$ISSUES" 'P4-W02 进展：immutable `CockpitHmiState`'
require_text "$ISSUES" 'P4-W03 进展：intent-first 四阶段 shell'
require_text "$ISSUES" 'P4-W04 进展：HVAC control surface'
require_text "$ISSUES" 'P4-W05 进展：Seat control surface'
require_text "$ISSUES" 'P4-W06 进展：Client2 Execution surface'
require_text "$ISSUES" 'P4-W07 进展：Client2 recovery UX'
require_text "$ISSUES" 'P4-W08 进展：Client2 driving restriction renderer'
require_text "$ISSUES" 'P4-W09 进展：Client2 engineer simulation drawer'
require_text "$ISSUES" '| P4-W10 进展 |'
require_text "$ISSUES" '| P4-W11 进展 |'
require_text "$ISSUES" '| P4-W12 进展 |'
require_text "$ISSUES" '### ISSUE-033 P4-W12 update'
require_text "$ISSUES" '### P6-W02 Backpressure/QoS progress'
require_text "$ISSUES" '### P6-W03 TriggerEngine progress (ISSUE-031)'
require_text "$ISSUES" '### P6-W04 proactive consent progress (ISSUE-031)'
require_text "$ISSUES" '### P6-W05 Context source adapters progress (ISSUE-031)'
require_text "$DELIVERY" '## 2026-07-17 AIOS Stage 2 交付范围'
require_text "$DELIVERY" '## 2026-07-16 Client2 中控 HVAC/Seat 交付规划'
require_text "$DELIVERY" '## 2026-07-18 P4-W10 Scenario/manual-control synchronization delivery'
require_text "$DELIVERY" '## 2026-07-18 P4-W11 Accessibility/display matrix delivery'
require_text "$DELIVERY" '## 2026-07-18 P4-W12 Android device acceptance/fault/recovery delivery'
require_text "$DELIVERY" '## Android P5-W01 Tool Manifest/Schema'
require_text "$DELIVERY" '## Android P5-W02 Tool Registry/Resolver'
require_text "$DELIVERY" '## Android P5-W07 ProfileMemoryStore'
require_text "$DELIVERY" '## Android P5-W08 EpisodicMemoryStore'
require_text "$DELIVERY" '## Android P5-W09 ContextBudgetManager'
require_text "$DELIVERY" '## Android P5-W10 Memory consent HMI/API'
require_text "$DELIVERY" '## Android P6-W01 EventBroker interface/in-process implementation'
require_text "$DELIVERY" '## Android P6-W02 Event Backpressure/QoS'
require_text "$DELIVERY" '## Android P6-W03 TriggerRule manifest/engine'
require_text "$DELIVERY" '## Android P6-W04 Proactive consent/policy'
require_text "$DELIVERY" '## Android P6-W05 Context source adapters'
require_text "$DRIVER" '## 2026-07-15 AIOS Stage 2 Driver/HAL 边界'
require_text "$DRIVER" '## 2026-07-16 Client2 中控 HVAC/Seat 规划边界'
require_text "$DRIVER" '### P4-W10 Scenario/manual synchronization Driver/HAL boundary'
require_text "$DRIVER" '### P4-W11 Accessibility/display matrix Driver/HAL boundary'
require_text "$DRIVER" '### P4-W12 Aggregate acceptance Driver/HAL boundary'
require_text "$DRIVER" '## P5-W01 Tool Manifest/Schema Driver/HAL Boundary'
require_text "$DRIVER" '## P5-W02 Tool Registry/Resolver Driver/HAL Boundary'
require_text "$DRIVER" '## P5-W07 ProfileMemoryStore Driver/HAL Boundary'
require_text "$DRIVER" '## P5-W08 EpisodicMemoryStore Driver/HAL Boundary'
require_text "$DRIVER" '## P5-W09 ContextBudgetManager Driver/HAL Boundary'
require_text "$DRIVER" '## P5-W10 Memory consent HMI/API Driver/HAL Boundary'
require_text "$DRIVER" '## P6-W01 EventBroker Driver/HAL Boundary'
require_text "$DRIVER" '## P6-W02 Event Backpressure/QoS Driver/HAL Boundary'
require_text "$DRIVER" '## P6-W03 TriggerEngine Driver/HAL Boundary'
require_text "$DRIVER" '## P6-W04 Proactive consent/policy Driver/HAL Boundary'
require_text "$DRIVER" '## P6-W05 Context source adapters Driver/HAL Boundary'
require_text "$README" 'design_baseline_complete=true'
require_text "$README" 'cockpit_hmi_design_mockups_ready=true'
require_text "$README" 'aios_intent_orchestration_ux_ready=true'
require_text "$README" 'cockpit_hmi_1920x1080_safe_frame_verified=true'
require_text "$README" 'cockpit_hmi_translucent_material_ready=true'
require_text "$README" 'cockpit_demo_control_loop_implemented=false'
require_text "$README" 'client2_session_event_primary_api=true'
require_text "$README" 'client2_session_reconnect_replay_verified=true'
require_text "$README" 'cockpit_hmi_state_reducer_implemented=true'
require_text "$README" 'client2_hmi_checkpoint_text_persisted=false'
require_text "$README" 'cockpit_hmi_four_stage_shell_implemented=true'
require_text "$README" 'cockpit_hmi_safe_frame_1920x1080_verified=true'
require_text "$README" 'cockpit_hmi_device_drawer_scaffolded=true'
require_text "$README" 'implementation_stage=P9-W03'
require_text "$README" 'target_capability_discovery_contract_defined=true'
require_text "$README" 'target_capability_discovery_hardware_mapping_complete=false'
require_text "$README" 'target_capability_discovery_external_blocked=true'
require_text "$TARGET_DISCOVERY" 'P8-W01 Target Capability Discovery Contract'
require_text "$TARGET_DISCOVERY" 'implementation_stage=P9-W03'
require_text "$BACKLOG" 'P8-W01 software preparation'
require_text "$REQUIREMENTS" 'P8-W01 target capability discovery trace'
require_text "$DEVIATIONS" 'DEV-085 P8-W01 discovery tooling does not complete target discovery'
require_text "$ISSUES" 'ISSUE-047 P8 target capability discovery evidence is unavailable'
require_text "$DELIVERY" 'Android P8-W01 Target Capability Discovery Preparation'
require_text "$DRIVER" 'P8-W01 Target Capability Discovery Driver/HAL Boundary'
require_text "$README" 'performance_budget_contract_defined=true'
require_text "$README" 'performance_budget_metric_count=10'
require_text "$PERFORMANCE_BUDGET" 'Central Brain P9-W01 Performance Budgets'
require_text "$PERFORMANCE_BUDGET" 'implementation_stage=P9-W03'
require_text "$BACKLOG" '`P9-W01` Performance budgets'
require_text "$REQUIREMENTS" 'P9-W01 performance budget trace'
require_text "$DEVIATIONS" 'DEV-086 P9-W01 initial budgets are not target measurements'
require_text "$ISSUES" 'ISSUE-048 P9 target performance evidence is unavailable'
require_text "$DELIVERY" 'Android P9-W01 Performance Budget Contract'
require_text "$DRIVER" 'P9-W01 Performance Budget Driver/HAL Boundary'
require_text "$README" 'stability_fault_matrix_contract_defined=true'
require_text "$README" 'stability_matrix_case_count=18'
require_text "$STABILITY_MATRIX" 'Central Brain P9-W02 Stability and Fault Matrix'
require_text "$STABILITY_MATRIX" 'implementation_stage=P9-W03'
require_text "$BACKLOG" '`P9-W02` 72h stability and fault matrix'
require_text "$REQUIREMENTS" 'P9-W02 stability and fault matrix trace'
require_text "$DEVIATIONS" 'DEV-087 P9-W02 synthetic matrix is not a 72h target run'
require_text "$ISSUES" 'ISSUE-049 P9 target 72h stability evidence is unavailable'
require_text "$DELIVERY" 'Android P9-W02 Stability Fault Matrix Contract'
require_text "$DRIVER" 'P9-W02 Stability Fault Matrix Driver/HAL Boundary'
require_text "$README" 'security_parser_corpus_defined=true'
require_text "$README" 'security_parser_case_count=18'
require_text "$SECURITY_REVIEW" 'Central Brain P9-W03 Security Review and Fuzz'
require_text "$SECURITY_REVIEW" 'W03C_SOFTWARE_BOUNDARIES_VERIFIED / TARGET_FUZZ_PENDING'
require_text "$BACKLOG" 'P9-W03a parser security corpus'
require_text "$REQUIREMENTS" 'P9-W03a parser security corpus trace'
require_text "$DEVIATIONS" 'DEV-088 P9-W03a deterministic corpus is not coverage-guided fuzzing'
require_text "$ISSUES" 'ISSUE-050 P9 complete security fuzz evidence is unavailable'
require_text "$DELIVERY" 'Android P9-W03a Parser Security Corpus'
require_text "$DRIVER" 'P9-W03a Parser Security Driver/HAL Boundary'
require_text "$README" 'security_identity_replay_corpus_defined=true'
require_text "$README" 'security_identity_replay_case_count=18'
require_text "$SECURITY_REVIEW" 'P9-W03b identity, replay and signer policy corpus'
require_text "$BACKLOG" 'P9-W03b identity/replay/signer policy corpus'
require_text "$REQUIREMENTS" 'P9-W03b identity/replay security corpus trace'
require_text "$DEVIATIONS" 'DEV-089 P9-W03b host policy corpus is not Binder or APK crypto evidence'
require_text "$DELIVERY" 'Android P9-W03b Identity/Replay Security Corpus'
require_text "$DRIVER" 'P9-W03b Identity/Replay Security Driver/HAL Boundary'
require_text "$README" 'security_aidl_parcel_inventory_complete=true'
require_text "$README" 'security_aidl_surface_count=37'
require_text "$README" 'security_validation_family_count=8'
require_text "$SECURITY_REVIEW" 'P9-W03c public boundary inventory and Android debug probe'
require_text "$BACKLOG" 'P9-W03c security boundary inventory and debug probe'
require_text "$REQUIREMENTS" 'P9-W03c security boundary inventory trace'
require_text "$DEVIATIONS" 'DEV-090 P9-W03c static inventory and debug probe availability are not target fuzz evidence'
require_text "$DELIVERY" 'Android P9-W03c Security Boundary Inventory'
require_text "$DRIVER" 'P9-W03c Security Boundary Inventory Driver/HAL Boundary'
require_text "$README" 'privacy_data_inventory_complete=true'
require_text "$README" 'privacy_data_surface_count=12'
require_text "$README" 'privacy_policy_gap_count=2'
require_text "$PRIVACY_LIFECYCLE" 'W04B_ADMISSION_DEFINED / OWNER_POLICY_INPUT_OPEN'
require_text "$BACKLOG" 'P9-W04a privacy data inventory'
require_text "$REQUIREMENTS" 'P9-W04a privacy data inventory trace'
require_text "$DEVIATIONS" 'DEV-091 P9-W04a inventory is not lifecycle enforcement'
require_text "$ISSUES" 'ISSUE-051 P9 durable privacy lifecycle policies are incomplete'
require_text "$DELIVERY" 'Android P9-W04a Privacy Data Inventory'
require_text "$DRIVER" 'P9-W04a Privacy Data Inventory Driver/HAL Boundary'
require_text "$README" 'privacy_policy_admission_defined=true'
require_text "$README" 'privacy_current_policy_admitted=false'
require_text "$BACKLOG" 'P9-W04b privacy policy admission'
require_text "$REQUIREMENTS" 'P9-W04b privacy policy admission trace'
require_text "$DEVIATIONS" 'DEV-092 P9-W04b admission is not an approved lifecycle policy'
require_text "$DELIVERY" 'Android P9-W04b Privacy Policy Admission'
require_text "$DRIVER" 'P9-W04b Privacy Policy Admission Driver/HAL Boundary'
require_text "$README" 'privacy_redacted_audit_projection_defined=true'
require_text "$README" 'privacy_android_debug_probe_executed=false'
require_text "$BACKLOG" 'P9-W04c privacy redaction/audit Android probe'
require_text "$REQUIREMENTS" 'P9-W04c privacy redaction/audit probe trace'
require_text "$DEVIATIONS" 'DEV-093 P9-W04c probe availability is not owner policy or target evidence'
require_text "$DELIVERY" 'Android P9-W04c Privacy Redaction/Audit Probe'
require_text "$DRIVER" 'P9-W04c Privacy Redaction/Audit Probe Driver/HAL Boundary'
require_text "$README" 'production_release_admission_defined=true'
require_text "$README" 'release_package_set_count=3'
require_text "$README" 'same_signer_upgrade_fail_closed=true'
require_text "$README" 'release_database_compatibility_fail_closed=true'
require_text "$README" 'release_rollback_decision_fail_closed=true'
require_text "$README" 'production_signer_owner_approved=false'
require_text "$README" 'production_release_candidate_admitted=false'
require_text "$PRODUCTION_RELEASE" 'DEBUG_METADATA_PROBE_AVAILABLE / TARGET_EXECUTION_PENDING'
require_text "$BACKLOG" 'P9-W05a production release admission'
require_text "$REQUIREMENTS" 'P9-W05a production release admission trace'
require_text "$DEVIATIONS" 'DEV-094 P9-W05a contract admission is not a production release'
require_text "$ISSUES" 'ISSUE-052 P9 production signer and rollback owner evidence is unavailable'
require_text "$DELIVERY" 'Android P9-W05a Production Release Admission'
require_text "$DRIVER" 'P9-W05a Production Release Admission Driver/HAL Boundary'
require_text "$README" 'release_metadata_projection_defined=true'
require_text "$README" 'release_installer_dry_run_adapter_defined=true'
require_text "$README" 'release_android_debug_probe_executed=false'
require_text "$BACKLOG" 'P9-W05b production release metadata Android probe'
require_text "$REQUIREMENTS" 'P9-W05b production release metadata probe trace'
require_text "$DEVIATIONS" 'DEV-095 P9-W05b metadata observation is not production signer qualification'
require_text "$DELIVERY" 'Android P9-W05b Production Release Metadata Probe'
require_text "$DRIVER" 'P9-W05b Production Release Metadata Probe Driver/HAL Boundary'
require_text "$README" 'driver_safety_admission_defined=true'
require_text "$README" 'driver_safety_action_rule_count=12'
require_text "$README" 'driver_safety_current_owner_policy_approved=false'
require_text "$BACKLOG" 'P9-W06a driver-distraction/safety admission contract'
require_text "$REQUIREMENTS" 'P9-W06a driver-distraction/safety admission trace'
require_text "$DEVIATIONS" 'DEV-096 P9-W06a software admission is not OEM safety acceptance'
require_text "$DELIVERY" 'Android P9-W06a Driver Safety Admission'
require_text "$DRIVER" 'P9-W06a Driver Safety Admission Driver/HAL Boundary'
require_text "$README" 'driver_safety_redacted_projection_defined=true'
require_text "$README" 'driver_safety_target_adapter_defined=true'
require_text "$BACKLOG" 'P9-W06b redacted Android probe and target evidence adapter'
require_text "$REQUIREMENTS" 'P9-W06b driver safety redacted probe trace'
require_text "$DEVIATIONS" 'DEV-097 P9-W06b Android contract probe is not target safety evidence'
require_text "$DELIVERY" 'Android P9-W06b Driver Safety Redacted Probe'
require_text "$DRIVER" 'P9-W06b Driver Safety Probe Driver/HAL Boundary'
require_text "$README" 'release_evidence_envelope_defined=true'
require_text "$README" 'release_evidence_diagnostic_category_count=8'
require_text "$README" 'release_evidence_target_owner_approved=false'
require_text "$README" 'release_evidence_runtime_diagnostics_wired=false'
require_text "$BACKLOG" 'P9-W07a release evidence envelope'
require_text "$REQUIREMENTS" 'P9-W07a release evidence envelope trace'
require_text "$DEVIATIONS" 'DEV-098 P9-W07a metadata eligibility is not target qualification'
require_text "$ISSUES" 'ISSUE-053 P9 target field diagnostics and retest evidence is unavailable'
require_text "$DELIVERY" 'Android P9-W07a Release Evidence Envelope'
require_text "$DRIVER" 'P9-W07a Release Evidence Envelope Driver/HAL Boundary'
require_text "$README" 'working_memory_store_defined=true'
require_text "$README" 'working_memory_terminal_cleanup_verified=true'
require_text "$README" 'working_memory_runtime_wired=false'
require_text "$README" 'profile_memory_store_defined=true'
require_text "$README" 'profile_memory_encryption_owner_gate_verified=true'
require_text "$README" 'profile_memory_durable_storage_wired=false'
require_text "$README" 'episodic_memory_store_defined=true'
require_text "$README" 'episodic_memory_summary_result_only_verified=true'
require_text "$README" 'episodic_memory_read_fail_closed=true'
require_text "$README" 'episodic_memory_raw_continuous_signal_stored=false'
require_text "$README" 'episodic_memory_runtime_wired=false'
require_text "$README" 'memory_consent_controller_defined=true'
require_text "$README" 'memory_consent_moving_restriction_verified=true'
require_text "$README" 'memory_consent_repository_mutation_wired=false'
require_text "$README" 'event_broker_interface_defined=true'
require_text "$README" 'event_broker_typed_topics_verified=true'
require_text "$README" 'event_broker_durable_persistence_wired=false'
require_text "$README" 'event_broker_dds_transport_wired=false'
require_text "$README" 'event_qos_contract_defined=true'
require_text "$README" 'event_qos_policy_count=4'
require_text "$README" 'event_qos_critical_no_silent_drop_verified=true'
require_text "$README" 'event_qos_deadline_priority_verified=true'
require_text "$README" 'event_qos_consumer_isolation_verified=true'
require_text "$README" 'event_qos_android13_arm64_verified=false'
require_text "$README" 'event_qos_process_local=true'
require_text "$README" 'event_qos_broker_wired=false'
require_text "$README" 'event_qos_durable_persistence_wired=false'
require_text "$README" 'event_qos_production_middleware_wired=false'
require_text "$README" 'trigger_rule_manifest_defined=true'
require_text "$README" 'trigger_rule_manifest_verified=true'
require_text "$README" 'trigger_threshold_window_debounce_verified=true'
require_text "$README" 'trigger_cooldown_scope_verified=true'
require_text "$README" 'trigger_input_fail_closed_verified=true'
require_text "$README" 'trigger_suggestion_only_verified=true'
require_text "$README" 'trigger_engine_android13_arm64_verified=false'
require_text "$README" 'trigger_engine_process_local=true'
require_text "$README" 'trigger_cooldown_persistence_wired=false'
require_text "$README" 'trigger_source_adapter_wired=false'
require_text "$README" 'trigger_auto_execution_enabled=false'
require_text "$README" 'trigger_runtime_wired=false'
require_text "$README" 'proactive_consent_policy_defined=true'
require_text "$README" 'proactive_grant_binding_verified=true'
require_text "$README" 'proactive_high_critical_generic_grant_blocked=true'
require_text "$README" 'proactive_grant_ttl_revoke_verified=true'
require_text "$README" 'proactive_policy_fail_closed_verified=true'
require_text "$README" 'proactive_consent_android13_arm64_verified=false'
require_text "$README" 'proactive_policy_process_local=true'
require_text "$README" 'proactive_grant_persistence_wired=false'
require_text "$README" 'proactive_consent_authority_wired=false'
require_text "$README" 'proactive_auto_execution_enabled=false'
require_text "$README" 'proactive_runtime_wired=false'
require_text "$README" 'context_source_adapter_contract_defined=true'
require_text "$README" 'context_source_count=3'
require_text "$README" 'context_source_allowlist_verified=true'
require_text "$README" 'context_source_runtime_health_verified=true'
require_text "$README" 'context_source_simulated_vehicle_verified=true'
require_text "$README" 'context_source_time_verified=true'
require_text "$README" 'context_source_freshness_quality_verified=true'
require_text "$README" 'context_source_fail_closed_verified=true'
require_text "$README" 'context_source_android13_arm64_verified=false'
require_text "$README" 'context_source_production_registry_published=false'
require_text "$README" 'context_source_runtime_wired=false'
require_text "$README" 'context_source_trigger_engine_wired=false'
require_text "$README" 'active_suggestion_controller_defined=true'
require_text "$README" 'active_suggestion_full_card_verified=true'
require_text "$README" 'active_suggestion_merge_replay_verified=true'
require_text "$README" 'active_suggestion_moving_minimal_verified=true'
require_text "$README" 'active_suggestion_never_ask_verified=true'
require_text "$README" 'active_suggestion_android13_arm64_verified=false'
require_text "$README" 'active_suggestion_hmi_projection_only=true'
require_text "$README" 'active_suggestion_production_source_wired=false'
require_text "$README" 'active_suggestion_preference_repository_wired=false'
require_text "$README" 'active_suggestion_voice_engine_wired=false'
require_text "$README" 'context_budget_manager_defined=true'
require_text "$README" 'context_budget_dual_limit_verified=true'
require_text "$README" 'context_budget_required_fail_closed=true'
require_text "$README" 'context_budget_tokenizer_wired=false'
require_text "$README" 'context_budget_summarizer_wired=false'
require_text "$README" 'context_budget_runtime_wired=false'
require_text "$README" 'cockpit_hvac_surface_implemented=true'
require_text "$README" 'cockpit_seat_surface_implemented=true'
require_text "$README" 'cockpit_execution_timeline_implemented=true'
require_text "$README" 'cockpit_execution_typed_event_projection=true'
require_text "$README" 'cockpit_driving_ux_policy_implemented=true'
require_text "$README" 'cockpit_unknown_driving_restricted=true'
require_text "$README" 'cockpit_runtime_policy_authority_independent=true'
require_text "$README" 'cockpit_engineer_simulation_drawer_implemented=true'
require_text "$README" 'cockpit_engineer_runtime_release_service_absent=true'
require_text "$README" 'cockpit_engineer_effect_authorization_source=false'
require_text "$README" 'cockpit_scenario_control_state_reducer_owned=true'
require_text "$README" 'cockpit_scenario_catalog_normalized=true'
require_text "$README" 'cockpit_scenario_device_session_synchronized=true'
require_text "$README" 'cockpit_display_matrix_defined=true'
require_text "$README" 'cockpit_touch_target_min_dp=48'
require_text "$README" 'cockpit_accessibility_semantics_runtime_owned=true'
require_text "$README" 'cockpit_display_matrix_android13_arm64_verified=true'
require_text "$README" 'cockpit_display_effect_authorization_source=false'
require_text "$README" 'p4_w12_application_acceptance_complete=true'
require_text "$README" 'p4_android13_arm64_aggregate_verified=true'
require_text "$README" 'p4_automatic_plan_runtime_published=false'
require_text "$README" 'hmi_d4_demo_control_loop_complete=false'
require_text "$README" 'tool_manifest_contract_defined=true'
require_text "$README" 'tool_manifest_contract_digest_verified=true'
require_text "$README" 'tool_manifest_android13_arm64_verified=false'
require_text "$README" 'tool_registry_contract_defined=true'
require_text "$README" 'tool_resolver_contract_defined=true'
require_text "$README" 'tool_registry_android13_arm64_verified=false'
require_text "$README" 'tool_registry_published=false'
require_text "$README" 'tool_rule_set_contract_defined=true'
require_text "$README" 'tool_rule_type_count=6'
require_text "$README" 'tool_rule_model_intersection_fail_closed=true'
require_text "$README" 'tool_rule_solver_android13_arm64_verified=false'
require_text "$README" 'tool_rule_solver_published=false'
require_text "$README" 'tool_executor_contract_defined=true'
require_text "$README" 'tool_invocation_context_defined=true'
require_text "$README" 'built_in_allowlist_enforced=true'
require_text "$README" 'built_in_signer_artifact_bound=true'
require_text "$README" 'tool_executor_host_execution_verified=true'
require_text "$README" 'tool_executor_deadline_cancel_verified=true'
require_text "$README" 'tool_executor_output_limit_verified=true'
require_text "$README" 'tool_executor_audit_bounded_verified=true'
require_text "$README" 'tool_executor_android13_arm64_verified=false'
require_text "$README" 'tool_executor_runtime_wired=false'
require_text "$README" 'tool_execution_enabled=false'
require_text "$README" 'production_tool_execution_enabled=false'
require_text "$README" 'skill_artifact_verifier_contract_defined=true'
require_text "$README" 'skill_signer_policy_contract_defined=true'
require_text "$README" 'skill_version_policy_contract_defined=true'
require_text "$README" 'skill_artifact_hash_verified=true'
require_text "$README" 'skill_manifest_digest_verified=true'
require_text "$README" 'skill_signer_policy_verified=true'
require_text "$README" 'skill_runtime_version_verified=true'
require_text "$README" 'skill_capability_policy_verified=true'
require_text "$README" 'skill_revocation_downgrade_fail_closed=true'
require_text "$README" 'skill_package_verifier_android13_arm64_verified=false'
require_text "$README" 'trusted_skill_evidence_source_configured=false'
require_text "$README" 'package_signature_cryptographically_verified=false'
require_text "$README" 'dynamic_skill_loading_enabled=false'
require_text "$README" 'skill_package_verifier_runtime_wired=false'
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

for current, expected in ((deviations, [f"DEV-{n:03d}" for n in range(1, 67)]),
                          (issues, [f"ISSUE-{n:03d}" for n in range(1, 40)])):
    present = set(re.findall(r"(?:^## |^\| )(DEV-[0-9]{3}|ISSUE-[0-9]{3})\b", current, re.MULTILINE))
    missing = [item for item in expected if item not in present]
    if missing:
        raise SystemExit(f"architecture tracking IDs missing: {missing}")

print(f"aios_stage2_work_package_count={len(work_packages)}")
PY

bash "$ROOT_DIR/tools/check_central_brain_cockpit_hmi_design.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_hmi_reducer.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_intent_shell.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_hvac_surface.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_seat_surface.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_execution_timeline.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_recovery_ux.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_driving_restriction.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_engineer_simulation.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_scenario_sync.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_accessibility_display.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_p4_acceptance.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_tool_manifest.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_tool_registry.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_tool_rule_solver.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_tool_executor.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_target_capability_discovery.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_performance_budget.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_stability_fault_matrix.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_parser_security_corpus.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_identity_replay_security_corpus.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_security_boundary_inventory.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_privacy_data_inventory.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_privacy_policy_admission.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_privacy_redaction_audit.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_production_release_admission.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_production_release_metadata_probe.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_driver_safety_admission.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_driver_safety_probe.sh"

echo "Central Brain AIOS Stage 2 design check passed"
