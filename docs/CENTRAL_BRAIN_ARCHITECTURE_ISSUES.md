# 中央大脑架构疑点与风险登记表

版本：0.7
日期：2026-07-17
状态：Android 13 实际工程基线

## 使用规则

本文件记录架构图中需要确认、工程职责不清或因黑盒目标环境而无法关闭的事项。关闭软件检查项
不等于关闭真实车辆、NPU、Driver/HAL、安全或量产风险。

状态定义：

- `Open`：仍需外部输入或工程实现。
- `Proposed`：已有处理建议，等待 owner/证据。
- `Closed`：疑点已被需求、设计或验证明确。
- `Superseded`：原问题由新的 Android-only 问题继续跟踪。

## 疑点索引

| ID | 当前结论 | 后续跟踪 | 状态 |
| --- | --- | --- | --- |
| ISSUE-001 | UNIOS/应用边界已按“App 经 SDK/Binder 调 Runtime”固定。 | XSC-001/005/006 | Closed |
| ISSUE-002 | AI SDK 已实现为 Android client AAR，不是 App 内业务实现。 | ISSUE-021/027 | Closed |
| ISSUE-003 | TBOX/OTA/Diag 应用与 native ownership 仍依赖目标服务目录。 | ISSUE-030 | Superseded |
| ISSUE-004 | ADAS 闭环不进入本项目用户态控制路径。 | ISSUE-030 | Superseded |
| ISSUE-005 | SOA 负责语义服务，Runtime & Governance 负责控制面与决策门禁。 | XSC-003/005 | Closed |
| ISSUE-006 | 云外发和 Privacy Router 的 product owner 仍未确定。 | ISSUE-031 | Superseded |
| ISSUE-007 | 图中重复 Libs 的原始含义未获得厂商说明；当前用户态工程不猜测映射。 | ISSUE-027 | Superseded |
| ISSUE-008 | 外置 PCIe NPU 映射到 ModelProvider、C ABI/JNI、Vendor adapter 和 Driver/HAL gap。 | ISSUE-024 | Superseded |
| ISSUE-009 | 组织/代码 ownership 必须由目标平台清单确认。 | ISSUE-027/030 | Superseded |
| ISSUE-010 | Safety 链已定义，真实 owner/信号仍未知。 | ISSUE-023/030 | Superseded |
| ISSUE-011 | 黄色小太阳跨 SoC 合同保留，但当前只开发 Android。 | DEV-026 | Closed |
| ISSUE-012 | 当前交付对象已明确为 Android 13 座舱工程师。 | DEV-026 | Closed |
| ISSUE-013 | system/privileged service、签名、SELinux 和部署身份仍未知。 | ISSUE-027 | Superseded |
| ISSUE-014 | 量产 governance backend/process owner 仍未知。 | ISSUE-027 | Superseded |
| ISSUE-015 | 动态 extension/schema lifecycle 尚未实现。 | ISSUE-025 | Superseded |
| ISSUE-016 | 硬件空接口 owner、ABI、权限和证据仍依赖目标平台。 | ISSUE-024/030 | Superseded |
| ISSUE-017 | Vehicle Signal read/write bridge 尚无真实 property/service contract。 | ISSUE-030 | Superseded |
| ISSUE-018 | Event broker、cursor、QoS 和 callback owner 尚未进入 production。 | ISSUE-025 | Superseded |
| ISSUE-019 | Client2 patch 的源码、签名、导航几何与量产 HMI 边界未关闭。 | DEV-017/025 | Open |
| ISSUE-020 | 咔咔虾只可作为产品概念参考，不能声明兼容或等价。 | APP-004, DEV-009 | Proposed |
| ISSUE-021 | App-local typed AIDL 已完成，但 VINTF/system owner 未确定。 | XSC-006, DEV-018 | Open |
| ISSUE-022 | Durable workflow 已有软件基础，真实 effect/material/retention/encryption 未关闭。 | NV-F-001, NV-G-006/007 | Open |
| ISSUE-023 | 可信 Safety State、Vehicle State 和审批 authority 未接入。 | FW-S-005, NV-G-005 | Open |
| ISSUE-024 | Production Model Router、Vendor NPU provider、资源标定和 PCIe 链路未接入。 | NV-F-011, HW-002 | Open |
| ISSUE-025 | Event、Memory、Skill 的 production owner、隐私和治理生命周期未完成。 | FW-U-003/006/007 | Open |
| ISSUE-026 | 聚合软件验收不等于 production/hardware activation。 | DEL-001/003/005 | Open |
| ISSUE-027 | 黑盒 Android 13 的 system 能力、部署身份、签名和 vendor SDK 边界不完整。 | DEL-004/005 | Open |
| ISSUE-028 | Private GitHub 的 tester access、branch protection 和 connector 可见性未完全关闭。 | DEV-021 | Open |
| ISSUE-029 | “我累了”驾驶席动作的安全策略与批准 authority 未确定。 | S2-SAF-001 | Open |
| ISSUE-030 | 车辆控制 API、权限、area mapping、readback 和 owner 未确定。 | S2-ADP-002 | Open |
| ISSUE-031 | 场景目录、长期记忆和主动执行的产品/隐私 owner 未确定。 | S2-MEM-001, S2-EVT-001 | Open |
| ISSUE-032 | Python 原型退役后禁止把已删除 gateway/test oracle 当成 Android fallback。 | DEV-026 | Closed |
| ISSUE-033 | Client2 尚无意图编排四阶段、HVAC/Seat Effect 详情和可观察控制闭环。 | S2-HMI-001..006, DEV-024/025 | Open |

## ISSUE-019 Client2 APK patch 验收边界

Client2 已通过 typed Binder、signature permission、current-signer capability、UI callback 和恢复矩阵
验证。2026-07-15 导航菜单进展证明当前 1920x1080 Android 13 ARM64 目标上的菜单交互可用。

未关闭项：闭源 APK 长期维护、底部导航几何、production signer/allowlist、OTA/MDM、Car UX
Restrictions、无障碍和支持显示矩阵。量产优先使用 OEM 可维护 HMI 源码或公开扩展点。

## ISSUE-020 咔咔虾公开产品参考边界

参考范围包括 task-as-service、多轮会话、主动关怀、长期记忆、Skill 组合、default-deny 和隐私
路由等公开概念。项目独立设计，不使用未公开接口，不声明产品兼容。每项能力必须映射到 Req ID、
Android Runtime 模块、治理门禁和可验证工作包。

## ISSUE-021 Android Binder 与部署稳定性

R2 typed production/governance/diagnostic AIDL、callback/cancel/death/version/hash 已达到
`android_integrated`。由于没有 AOSP/Soong SDK，当前不能声明 VINTF stable；目标
system/privileged service owner、SELinux、签名和 service placement 仍需厂商或 OEM 输入。

P1-W01 Session V1 只达到 `contract_defined`：5 个 DTO、边界校验、Parcel 和 checksum 已验证，但
`session_runtime_service_published=false`。其未来 service placement、permission/capability、Binder
death/reconnect 和 VINTF 边界仍由本问题跟踪，不能继承 R2 已集成结论。

P1-W02 Plan/Node V1 也只达到 `contract_defined`：4 个 DTO、allowlist、DAG/补偿/重试边界、Parcel 和
checksum 已验证，但 `plan_runtime_published=false`。未来 Plan publication、caller isolation、
Compiler/Graph owner、Room v4 persistence、Binder payload sizing 和 app-local AIDL/VINTF 边界仍由本问题
及 P1-W04..P1-W07 跟踪。

P1-W03 Event/callback V1 只达到 `contract_defined`：5 个 DTO、23 类 allowlist、顺序/父链/脱敏/
cursor/immutable replay、Parcel 和 checksum 已验证，但 `event_runtime_service_published=false`、
`event_callback_service_published=false`。未来 Event Service owner、signature permission/capability、
callback death/overflow/resubscribe、Room v4 durable source、Binder payload sizing 和 app-local AIDL/VINTF
边界继续由本问题及 P1-W05/P1-W06 跟踪。当前独立 surface 解决了不破坏 Session V1 的版本所有权，
但没有解决目标 system/privileged service ownership。

P1-W04 Effect/Approval V1 同样只达到 `contract_defined`：四个 DTO、完整状态转换、approval/undo
digest/version/TTL 绑定、Parcel 和 checksum 已验证，但本包刻意没有 Binder interface，
`effect_runtime_service_published=false`、`approval_response_service_published=false`、
`undo_service_published=false`。P1-W05 仍须确定 facade/Service principal、permission/capability、
Binder lifecycle 和 app-local AIDL ownership，P1-W06 才能评审 Room v4 持久化。

## ISSUE-022 Durable task/session/checkpoint 与副作用恢复

Room v2、task/checkpoint/approval/effect/outbox/event cursor、restart reconciliation 和
idempotency contract 已实现。Production effect dispatch 仍保持关闭，直到 durable encrypted
material、key owner、trusted clock、retention/export/delete、adapter status reconciliation 和目标
故障证据全部到位。任何不确定副作用必须失败关闭，不得假定成功。

P1-W04 的 Effect transition 和 UndoHandle 只定义 wire/validation 语义，不连接现有 effect/outbox
repository，也不执行补偿。Undo 必须在未来创建新的受治理 compensation operation；它不能被实现为
数据库状态回滚。Crash recovery、material/key、trusted clock、status reconciliation 和 durable binding
仍为本问题的开放项。

## ISSUE-023 Android 可信身份、capability 与审批

Binder caller identity、package/current signer、default-deny capability 和 typed governance 已实现。
当前 Safety/Vehicle State provider 不是硬件可信源，审批也没有 OEM authority。目标映射必须保持：
`Safety State -> Safety Runtime -> ASIL/QM domain`。用户确认不能覆盖驾驶中驾驶席靠背等硬联锁。

P1-W04 的 `ApprovalPrompt` 仅绑定 plan/action/target/context/policy 并拒绝 stale/expired resume；现有
Governance V1 仍无 approval response/grant 方法。该合同不构成审批 authority，也不允许 HMI 通过
request DTO 自报身份、权限或车辆 Safety 状态。

## ISSUE-024 Model Router、资源准入与 NPU provider 边界

已保留 `ModelProvider`、`InferenceResourceScheduler`、`vendor.npu.empty`、NPU C ABI/JNI、
状态码和 Driver/HAL gap。Deterministic provider/router 只允许 test source set。

解除条件：取得 Vendor SDK/ABI、模型格式、device/runtime owner、DMA/IOMMU/共享内存约束、取消和
超时语义、温控/功耗/内存预算、目标性能数据、故障回退和签名发布证据。此前
`target_hardware_validated=false`。

## ISSUE-025 Event、Memory、Skill 生命周期与治理链

Android 已有 bounded Event、durable cursor、bounded Memory、built-in Skill test runtime 和固定
governance middleware。尚缺 production broker、cursor/retention owner、隐私分类与删除、Skill
签名/revoke/rollback/sandbox、route registry 和持久 audit exporter。当前实现不得被描述为开放式
插件平台。

## ISSUE-026 Android 聚合验收与量产激活边界

R7 acceptance snapshot、Client2 Binder、application handoff 和 hybrid delivery 只证明仓库软件
一致性与 Android 应用集成。它们不能设置 `production_ready`、`target_hardware_validated`、
Driver/HAL 或 virtualization 标志。

## ISSUE-027 黑盒 Android 13 目标能力与部署身份未知

当前只能使用公开 Android 13 应用 SDK、ADB 和普通 APK/AAR/NDK 能力；不能修改已刷机 Framework、
BSP 或预编译厂商组件。仍需确认 package placement、签名策略、后台启动/保活、SELinux、隐藏 API
可用性、vendor service discovery、升级和回滚 owner。

## ISSUE-028 GitHub 远程硬件测试缺口

Private repository、Release、结构化 Issue 和 `gh` fallback 已建立。仍需 tester access list、
强制 branch protection 或等价控制、命名 release 的目标复测和最小脱敏证据。GitHub 不连接目标
ADB，不存储原始设备或车辆数据。

## ISSUE-029 “我累了”场景的驾驶席座椅安全策略与批准 authority

驾驶中不得放平或大幅调整驾驶席靠背；unknown state 必须使用 restricted UX。驻车状态是否允许、
最大角度、乘员检测、撤销/恢复和批准 authority 需 OEM Safety owner 确认。用户确认不能覆盖硬
联锁。对应 `S2-SAF-001`。

P2-W07 进展：Compiler 在 MOVING/UNKNOWN 时移除 optional fatigue seat approval/recline/verify branch，
required rest recline 已在 Resolver 阶段拒绝；`PlanGraphValidator` 还要求 HIGH Effect 具有 approval 前驱。
这些是 fail-closed 软件结构证据，不定义驻车最大角度、批准 authority 或真实 Safety source，因此本问题
保持 Open，`scenario_graph_execution_enabled=false`。

## ISSUE-030 黑盒 Android 13 的车辆控制 API、权限和 owner 未确定

当前没有可发布的 HVAC/Seat/Media/Navigation property/service 目录、写权限、area mapping、
readback、幂等 token、故障码和 rollback contract。Production adapter 必须返回 unavailable；
不得猜测 VHAL property、vendor Binder、device node 或 ioctl。只有确认公开/vendor SDK 不满足
明确缺口后，才登记最小 Driver/HAL 工作量。

P2-W01 进展：已定义 12 项内部 canonical signal path、typed scalar、unit/area、source/quality 和
monotonic freshness，并通过 JVM/API 33 ARM64 debug probe。该结果不关闭本问题：canonical path 尚未
映射目标 property/service，`SignalSource.AAOS/VENDOR` 不是 availability/authorization 证据，生产
signal provider 仍未接线。P2-W02 capability catalog 必须继续将 `productionAuthorized=false` 作为默认值。

P2-W02 进展：8 项 capability catalog 已固定 semantic readable/writable/simulatable、typed range、area、
risk、readback path 和 fresh-signal dependency，并通过 JVM/API 33 ARM64 probe。全部 production
available/authorized 仍为 false；这些范围不是 OEM 标定，未解决 property/service/permission/area/readback
owner。P2-W03 可以据此构建 debug/test Twin，但不得关闭本问题或激活 production adapter。

P2-W03 进展：进程内 Twin 已完成 desired/reported 分离、monotonic revision、TTL/quality、atomic snapshot
和 reconciliation，并通过 JVM/API 33 ARM64 software probe。本问题仍开放：reported 只来自 test 构造值，
没有 provider/property/service/permission/area mapping；Twin 无持久化且未接 production Service/adapter。
P2-W04 构建的 Context 必须保留 source/quality/restricted 语义，不能把 software probe 当作 trusted vehicle
evidence。

P2-W04 进展：Context foundation 已完成 fixed field policy、同 Twin revision、Runtime-state freshness、
driving/safety/source/trust report、restricted 和 digest，并通过 JVM/API 33 ARM64 probe。该结果不关闭本
问题：snapshot 固定 `productionTrusted=false`，没有 vehicle provider/property/permission/readback 或
Safety authority，未接 production Service。P2-W05 Scenario 只能消费该明确 untrusted/debug Context，
不得自行提升 trust。

## ISSUE-031 场景目录、长期记忆和主动执行的产品/隐私 owner 未确定

场景版本、冲突规则、用户偏好、保留期、删除/导出、跨账号边界、主动触发频率、免打扰和模型文本
外发策略需产品/隐私 owner 决定。未确认前只实现最小数据合同和 default-deny；不保存原始用户或
模型文本。

P2-W05 进展：cold/fatigue/rest 三份 build-owned v1 manifest、strict parser/schema、SHA-256 sidecar、
bounded template validator 和 invalid isolation 已完成。该增量不包含独立 artifact 签名、catalog
activation/lifecycle、用户偏好/记忆、主动触发或 production Service wiring；因此本问题保持 Open，
`scenario_catalog_production_trusted=false`。

P2-W06 进展：显式 ID 与固定中英文 alias 的 deterministic resolver、Context/source/zone/capability/
PARKED_ONLY gate、unknown/ambiguous fail-closed 和 immutable resolution digest 已完成。固定 alias 不是
完整产品 taxonomy；没有 locale rollout/revoke owner、模型候选策略、生产 capability/trust 或 Session
Service wiring，因此本问题保持 Open，`scenario_resolver_runtime_wired=false`。

P2-W07 进展：Resolution/Context/Capability/manifest-bound Compiler、optional-only fallback 和 immutable
Plan digest 已完成。Manifest 仍没有产品 target/preference，compiler 未接 Session/Room/Runtime，不发布或
执行 Graph；因此本问题保持 Open，`scenario_plan_compiler_runtime_wired=false`。

## ISSUE-032 Python 原型退役后的引用与回退风险

2026-07-16 已删除 Python runtime、REST contract、Linux Python binding/CLI/daemon、旧 Console、
systemd 样例及专用测试。CI 新增退役门禁，确保 `central-brain/` 下没有 Python runtime，当前文档
和 workflow 不引用已删除路径，并确认 Android Model/NPU/Driver-HAL 资产仍存在。

该问题在仓库一致性范围内 `Closed`。未来若恢复 Linux 或本机模型调试，必须建立新的非 Python
工作包或 Android ModelProvider development profile，禁止恢复旧 gateway 作为隐式 fallback。

## ISSUE-033 Client2 HVAC/Seat 中控演示闭环缺口

当前 Client2 只有导航触发的场景按钮和回复文本，没有独立 HVAC/Seat 控制页、desired/reported
状态、Effect timeline、approval、partial、retry、undo 或 restart rehydration。因此“我冷了”或
“我累了”的文本回复不能构成 AIOS 中控演示闭环。

2026-07-16 设计审查又发现首版高保真稿以 HVAC/Seat 按钮为顶层导航，仍更像智能中控而不是
AIOS。设计已纠正为“意图/计划/执行/结果”四阶段：用户只表达自然场景，界面明确展示 Intent、
Context、Plan、Policy、Effect 和 readback；HVAC/Seat 降为 Effect 详情与受治理手动兜底。

同日第二次视觉审查发现原高保真 Panel `y=12, h=1056` 几乎贴满 1920x1080 画布，且 0.91 alpha
过于接近实色。HMI-D0 已改为 `(1264,160)-(1888,1048)` 安全框和 0.60 浅灰玻璃；浏览器预览
scale 上限为 1。该修正只关闭设计越界风险，不代表 HMI-D1 APK 或多显示矩阵已完成。

处理计划：业务状态和 renderer 进入 maintained Java secondary-dex，Smali 只保留 bootstrap；自然
场景与手动微调均通过 typed SDK 进入 Runtime。无真实车身信号时使用持续标注 SIMULATED 的 Android
debug/test Digital Twin，真实 adapter 仍由 `ISSUE-030` 跟踪。

关闭条件：`CENTRAL_BRAIN_COCKPIT_HMI_CONTROL_LOOP_PLAN.md` 的 HMI-D4 和 HMI-AI/AC/ST/CL 验收
全部在 Android 13 ARM64 Client2 APK 通过。该关闭只代表演示软件闭环，不关闭 `ISSUE-030`、
Driver/HAL、target hardware 或 production。状态：`Open`。

## ISSUE-034 Session/Event V1 cursor 与进程死亡恢复缺口

P1-W05 已发布 owner-scoped Session/Event app-layer Binder，并在 Android 13 ARM64 验证 Service
rebind 后 active session 重新订阅。P1-W06 已完成 Room v4 repository、v3->v4 migration、crash
transaction rollback 和 Runtime process-death rehydration；相同 sessionId/event history 可恢复，
callback 由 SDK replay 后重新注册。该问题的进程死亡子项已关闭。

冻结的 Event V1 还存在 cursor 语义缺口：`hasMore=false` 的 terminal page 不提供可前移的 resume cursor。
当前 facade 只能保留该 terminal request cursor，并用递增 sequence 去除 register/reconnect replay 的
重复事件；结果正确但可能重复读取已见历史，不能扩展为高吞吐 durable broker。P1-W07 aggregate review
已决定新增独立 Event V2 resume cursor/ACK contract：terminal page 也返回可恢复 cursor，ACK 必须
monotonic、owner/session-scoped、有界留存并拒绝 stale/future cursor；不得修改已冻结 Event V1 hash。

状态：`Open / Design Decided`。P1-W07 决策与 aggregate gate 已完成，V2 Binder/Room ACK/SDK negotiation/
高吞吐 fault tests 由 `P6-W01/P6-W02` 实现。该问题不再阻塞 durable Session Runtime，但仍阻塞
production Event broker。`event_v2_interface_published=false`、
`session_runtime_process_death_rehydration=true`、`production_ready=false`。

## Android 实现证据索引

这些追踪键由保留的源码和独立检查器验证，只说明软件增量存在：

| 追踪键 | 结论 |
| --- | --- |
| R1 SDK AAR、Runtime Service APK、Demo HMI APK | Android 多模块基础完成。 |
| R2C | typed Binder lifecycle/race 完成。 |
| R3C1 进展 | Action governance core 完成。 |
| R4A 进展 | Room durable schema 完成。 |
| R4B1 进展 | task admission 完成。 |
| R4B2 进展 | durable Runtime wiring 完成。 |
| R4B3 进展 | durable approval 完成。 |
| R4C1 进展 | restart reconciliation 完成。 |
| R4C2A 进展 | effect prepare/claim 完成。 |
| R4C2B 进展 | retry/terminal 完成。 |
| R4C3A 进展 | adapter contract/fault matrix 完成。 |
| R4C3B 进展 | activation gate 完成，production 关闭。 |
| R4C3C 进展 | fail-closed gate visibility 完成。 |
| R5A1 进展 | ModelProvider contract 完成。 |
| R5A2 进展 | inference scheduler 完成。 |
| R5B1 进展 | deterministic provider 仅限 test。 |
| R5B2 进展 | test-only router 完成。 |
| R5C1 进展 | model readiness 完成。 |
| R5D1 进展 | target deployment gate 完成。 |
| R6A1 进展 | Event runtime 完成。 |
| R6A2A 进展 | Event persistence schema 完成。 |
| R6A2B 进展 | durable Event repository 完成。 |
| R6A3 进展 | Event readiness 完成。 |
| R6B1 进展 | Memory lifecycle 完成。 |
| R6B2 进展 | Memory readiness 完成。 |
| R6C1 进展 | built-in Skill test runtime 完成。 |
| R6C2 进展 | governance middleware 完成。 |
| R6C3 进展 | Skill/governance readiness 完成。 |
| R7A1 进展 | aggregate acceptance snapshot 完成。 |
| R7B 进展 | Client2 Binder migration 完成。 |
| R7C 进展 | application recovery matrix 完成。 |
| R7D 进展 | Android application handoff 完成。 |
| 2026-07-12 B1 进展 | Native C ABI/JNI 软件基线完成。 |
| 2026-07-12 B2 进展 | Runtime/native integration 完成。 |
| 2026-07-12 B3 进展 | black-box preflight/acceptance 软件链完成。 |
| 2026-07-12 B4 进展 | hybrid delivery 软件包完成。 |
| 2026-07-15 导航菜单进展 | 当前物理设备 Client2 菜单交互完成。 |
| P1-W03 进展 | Event/callback V1 合同与物理 API 33 Parcel 证据完成；Service/Room/hardware 均未发布。 |
| P1-W04 进展 | Effect/Approval V1 合同与物理 API 33 Parcel 证据完成；Service/grant/undo/Room/hardware 均未发布。 |
| P1-W05 进展 | SDK facade 与 Session/Event Service 真实 Binder rebind/resubscribe 完成；process-death/Room/scenario/hardware 仍未发布。 |
| P1-W06 进展 | Room v4、Session/Event process-death rehydration 已完成；ISSUE-034 仅剩 Event V1 terminal cursor/ACK 演进。 |
| P2-W01 进展 | Canonical signal schema 与 API 33 ARM64 software probe 完成；ISSUE-030 的 property/service/permission/area/readback owner 仍开放。 |
| P2-W02 进展 | Capability catalog 与 API 33 ARM64 software probe 完成；全部 production authorized=false，ISSUE-029/030 仍开放。 |
| P2-W03 进展 | 进程内 Twin 与 API 33 ARM64 software probe 完成；无 provider/adapter/persistence，ISSUE-030 仍开放。 |
| P2-W04 进展 | Context/freshness/trust/restricted 与 API 33 ARM64 probe 完成；productionTrusted=false，ISSUE-029/030 仍开放。 |
| P2-W05 进展 | Scenario manifest/parser/schema/checksum/isolation 与 API 33 ARM64 probe 完成；artifact crypto、product/privacy owner、Runtime/Graph 仍开放。 |
| P2-W06 进展 | Deterministic Resolver 与 API 33 ARM64 probe 完成；product taxonomy/production trust/Service/compiler/Graph 仍开放。 |
| P2-W07 进展 | Digest-bound typed Plan compiler 与 API 33 ARM64 probe 完成；target material/production publication/Graph/Effect 仍开放。 |
