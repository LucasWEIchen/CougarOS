# CougarOS Central Brain 生产软件需求文档

版本：2.0
状态：生产需求权威基线
适用平台：Android 13 座舱域控制器
更新日期：2026-07-27

`production_document_scope=true`
`production_requirements_document=true`
`production_ready=false`
`target_hardware_validated=false`

## 1. 文档目的

本文是 CougarOS Central Brain 在生产环境中部署的软件系统的唯一需求文档，负责：

1. 定义产品目标、系统边界、功能需求和非功能需求。
2. 保存项目至今所有工作包 ID 的状态，防止需求在文档合并后丢失。
3. 将软件实现完成、量产集成完成和目标硬件验收完成严格区分。
4. 为软件架构和软件开发文档提供唯一的需求追踪入口。

本文只描述量产部署的软件边界、外部依赖和验收条件。退出生产基线的历史工作包只保留 ID 和状态，
不再作为生产软件设计依据。

## 2. 产品定义

Central Brain 是部署在 Android 13 座舱域控制器上的车载 AIOS 中枢。系统接收驾驶员语音和座舱图像，
结合车辆状态、用户授权和场景策略，调用外部 AI 算力基座完成意图理解与计划生成，再通过受控的
车辆能力接口执行空调、座椅、导航、媒体和购物等动作。HMI 必须实时展示输入、模型输出、计划、
执行进度、审批点和最终结果。

### 2.1 核心价值

- 用户只表达目标，例如“我有些疲惫”，系统负责理解、规划、审批、执行和反馈。
- 模型只提出候选计划，不能直接调用车辆执行器。
- 每个动作必须经过能力白名单、状态校验、安全策略、权限校验和结果核验。
- 所有跨进程合同必须版本化、有界、可恢复、可审计。
- 车辆或模型能力不可用时必须失败关闭，不能用默认成功掩盖缺口。

### 2.2 生产部署边界

生产交付由以下部分组成：

| 边界 | 生产职责 |
| --- | --- |
| Client2 座舱 HMI | 语音/图像输入、任务触发、执行链路、审批和结果展示 |
| Central Brain SDK | AIDL/DTO、连接管理、协议协商、事件订阅与重连 |
| Runtime Service | Session、Plan、Event、Graph、Tool、Memory、Governance、Effect |
| Native Runtime | 稳定 C ABI、资源槽位、健康状态和厂商扩展入口 |
| Model Adapter | 外部 AI 基座连接、流式结果、超时、取消、健康和故障隔离 |
| Vehicle Adapter | 车辆信号、能力目录、执行、回读和安全联锁 |
| Persistence | Session、Plan、事件游标、检查点、Effect outbox 和审计摘要 |

以下内容不属于当前生产交付：

- 虚拟化平台和 Hypervisor 实现。
- Android 系统镜像、厂商 Framework、Kernel、Driver 或 HAL 的无依据修改。
- Linux 前端和已经退役的 Python 运行时。
- 未经 OEM/Vendor 确认的车辆属性、服务、权限、NPU ABI 或安全策略。

## 3. 状态模型

| 状态 | 含义 |
| --- | --- |
| `已实现` | 主源码和静态/单元合同存在，但不自动代表量产激活 |
| `已实现/待量产集成` | 软件边界完整，仍缺真实 Provider、Adapter、权限或发布配置 |
| `设计完成/实现待合并` | 设计已进入主分支，软件实现仍在独立 Draft 中 |
| `外部阻塞` | 依赖 OEM/Vendor、真实车辆、NPU、签名或目标验收输入 |
| `挂起` | 已明确暂不实施，只保留失败关闭接口 |
| `已退出生产基线` | 历史工作项不再构成生产软件需求 |

任何局部的 `已实现` 都不能将全局状态提升为 `production_ready=true`。只有 P3 至 P9 的生产激活、
安全审批和目标硬件验收全部关闭后，才允许改变全局状态。

## 4. 系统级功能需求

### 4.1 输入与多模态

| Req ID | 需求 |
| --- | --- |
| `APP-001` | HMI 必须提供场景化任务入口，并支持语音转写文本作为主要输入。 |
| `APP-002` | HMI 必须支持单帧座舱图像与文本形成同一多模态请求。 |
| `APP-003` | 输入必须绑定 Session、时间戳、来源、内容类型和有界载荷。 |
| `APP-004` | 输入进入模型前必须注入座舱角色、驾驶服务目标、车辆能力和安全边界上下文。 |
| `APP-005` | HMI 必须展示实际发送给模型的文字；存在图像时显示缩略图并支持居中预览。 |
| `S2-PER-001` | 座舱感知结果必须把观察与意图分离，只输出有界座位、物体、区域、置信度和证据摘要，不输出身份或年龄推断。 |

### 4.2 Session、Plan 与事件

| Req ID | 需求 |
| --- | --- |
| `S2-SES-001` | 每次用户目标必须创建或恢复 owner-scoped Session，并持久化状态和截止时间。 |
| `S2-GRF-001` | 模型候选计划必须转换为不可变、有向无环、版本化的 ScenarioPlan。 |
| `S2-EVT-001` | 运行状态必须通过 typed Event 发布，支持游标、ACK、重放和订阅恢复。 |
| `S2-EFF-001` | 每个外部动作必须表示为 typed EffectIntent，并记录 desired/reported 状态。 |
| `S2-TRG-002` | 主动建议必须按驾驶状态限制信息量，支持合并、去重、冷却、拒绝和永久关闭，且不能授予 Effect 权限。 |

### 4.3 场景理解与计划

| Req ID | 需求 |
| --- | --- |
| `S2-SCN-001` | ScenarioResolver 只能选择构建时登记且签名/摘要匹配的场景。 |
| `S2-SCN-002` | 计划编译必须校验节点类型、依赖关系、并发、截止时间、审批点和补偿动作。 |
| `S2-SCN-003` | 购物与路径规划必须分离候选搜索、购买确认、目的地确认和导航启动。 |
| `S2-SCN-004` | 多座位感知必须保留 seat-area 语义，不能将乘员状态错误映射到驾驶员。 |
| `S2-SCN-005` | 无法从输入证据证明的购买、导航或车辆动作不得自动执行。 |
| `S2-INT-001` | 从感知结果推导的用户意图只能作为有时限、可拒绝且需确认的候选假设，不能直接授权 Tool 或 Effect。 |
| `S2-NAV-001` | 导航能力必须拆分 POI 搜索、路线预览和导航启动；导航启动必须绑定独立审批。 |
| `S2-COM-001` | 购物能力必须拆分商品搜索、订单预览和订单提交；订单提交必须绑定独立审批，支付保持受控外部接口。 |

### 4.4 模型运行时

| Req ID | 需求 |
| --- | --- |
| `S2-MDL-001` | ModelProvider 必须支持描述、健康、预热、推理、流式输出、取消、指标和故障查询。 |
| `S2-MDL-002` | PolicyAwareModelRouter 必须按场景、模态、资源、健康和 assurance 选择 Provider。 |
| `S2-MDL-003` | 当前生产版本通过车载以太网访问外部 OpenClaw，后续可替换为 Ollama Provider。 |
| `S2-MDL-004` | 模型输出必须通过结构化 Schema、字段长度、枚举和能力白名单校验。 |
| `S2-MDL-005` | Provider 不可用、输出非法、超时或取消失败时不得进入车辆执行阶段。 |
| `S2-MDL-006` | 凭据不得写入日志、事件正文或 HMI；当前固定凭据属于待整改的发布风险。 |

### 4.5 Tool、Skill 与 Memory

| Req ID | 需求 |
| --- | --- |
| `S2-TOL-001` | Tool 必须由版本化 Manifest、Schema、健康和能力声明组成。 |
| `S2-TOL-002` | ToolResolver 必须确定性选择版本，健康异常时不得静默回退。 |
| `S2-SKL-001` | Skill 包必须校验签名、版本、摘要、依赖和权限。 |
| `S2-MEM-001` | Working/Profile/Episodic Memory 必须分层管理，并受用户同意和生命周期策略约束。 |
| `S2-MEM-002` | 原始用户文本、模型全文、车辆载荷和位置不得进入审计持久层。 |

### 4.6 车辆 Context 与 Digital Twin

| Req ID | 需求 |
| --- | --- |
| `S2-CTX-001` | 车辆信号必须使用 canonical path、质量、来源、时间戳和 schema version。 |
| `S2-CTX-002` | 多来源 Context 必须按座位区域独立融合 occupancy、source、freshness、trust 和 conflict；未知、冲突或陈旧数据对对应区域的 Effect 失败关闭。 |
| `S2-TWN-001` | Digital Twin 必须分离 desired 与 reported 状态，并对陈旧数据失败关闭。 |
| `S2-ADP-001` | CapabilityCatalog 必须声明 area、risk、adapter、authorization 和 availability。 |
| `S2-ADP-002` | Vehicle Adapter 必须实现 prepare/dispatch/readback，且不能由模型直接引用。 |
| `S2-ADP-003` | HVAC 温度范围为 18.0 至 30.0 摄氏度，步进 0.5 摄氏度。 |
| `S2-ADP-004` | 座椅靠背动作必须采用展开方向，并受车辆状态、座位区域和审批策略约束。 |

### 4.7 Governance 与安全

| Req ID | 需求 |
| --- | --- |
| `S2-SAF-001` | 所有 Effect 必须经过调用方身份、capability、驾驶状态、风险和审批校验。 |
| `S2-SAF-002` | 行驶中必须限制长文本、高风险控制和需要持续视觉注意的交互。 |
| `S2-SAF-003` | 座椅大角度调节、购物提交和导航启动必须支持独立审批。 |
| `S2-SAF-004` | 模型、Tool、Skill、HMI 和 Adapter 均不能绕过 Governance。 |
| `S2-SAF-005` | 安全类真实执行在 OEM 策略未批准时保持失败关闭。 |

### 4.8 HMI 与执行反馈

| Req ID | 需求 |
| --- | --- |
| `S2-UX-001` | HMI 第一层只呈现场景任务入口和实时执行链路，不以大量控制按钮替代 AIOS。 |
| `S2-UX-002` | 执行链路必须按输入、理解、计划、审批、执行、核验、结果顺序增量显示。 |
| `S2-UX-003` | 部分成功、失败、重试、撤销和补偿必须有不同且可理解的状态。 |
| `S2-HMI-001` | Client2 必须提供 HVAC 状态与控制投影，只经 SDK/Runtime 访问，并区分 desired 与 reported 状态。 |
| `S2-HMI-002` | Client2 必须提供座椅状态与控制投影；车辆状态未知或行驶中时，驾驶席受限动作失败关闭。 |
| `S2-HMI-003` | HMI 必须显示执行时间线、审批、部分成功、重试、撤销、补偿和恢复状态。 |
| `S2-HMI-004` | 生产车辆信号或执行器不可用时必须明确显示 unavailable，禁止以伪造值或默认成功掩盖缺口。 |
| `S2-HMI-005` | 场景任务与次级手动控件必须进入同一 Session、Governance、Effect 和 readback 链路。 |
| `S2-HMI-006` | 主交互必须以自然意图驱动，并适配 1920x1080 半透明悬浮布局，不遮挡关键座舱内容。 |
| `S2-HMI-007` | HVAC、座椅等执行结果必须驱动真实 UI 状态和渐进反馈，不能使用固定文本或直接跳到最终结果。 |
| `S2-HMI-008` | 实时链路必须显示模型输入与输出；图像等比缩略并与文字同显，允许时支持点击居中放大和点击图外退出。 |
| `S2-HMI-009` | 乘员感知场景必须显示座位事实、购物候选、独立购物/购买/导航确认和真实事件驱动链路。 |

### 4.9 可观测性与发布

| Req ID | 需求 |
| --- | --- |
| `S2-OBS-001` | Trace、Metric、Audit 和诊断只能保存有界状态、摘要和错误分类，不得保存原始用户、模型、图像或车辆载荷。 |
| `S2-OBS-002` | HMI 必须按顺序滚动投影 Runtime、Intent、Context、Model、Plan、Policy、Graph、Safety、Effect 和 Readback 里程碑。 |
| `S2-REL-001` | 生产发布必须执行签名、兼容性、准入、回滚和替换版本复测；缺少任一批准证据时不得提升生产状态。 |

## 5. 非功能需求

### 5.1 接口与兼容性

- AIDL 必须固定 interface version/hash，DTO 必须有 schema version 和大小上限。
- C ABI 使用 `struct_size + abi_version`，新增字段只能尾部扩展。
- 所有枚举对未知值失败关闭；不得把未知状态解释为成功或允许。
- SDK 必须处理 Binder death、重连、Session 恢复和事件游标续传。

### 5.2 性能与资源

- HMI 输入必须在 100 ms 内出现本地反馈。
- Runtime 接收任务必须在 300 ms 内发布第一个状态事件。
- 模型连接超时、读取超时和最大载荷必须由固定生产配置约束。
- 推理并发必须受 `InferenceResourceScheduler` 和 `ModelResourceAdmission` 管理。
- HMI 线程不得执行网络、数据库、模型推理或车辆 I/O。

### 5.3 可靠性

- Session、Plan、事件游标、检查点和 Effect outbox 必须可在进程重启后恢复。
- Effect 采用 prepare/dispatch/readback；未知结果进入 reconciliation，不得直接标记成功。
- Runtime 必须支持 deadline、取消、有限重试、补偿和幂等键。
- 生产发布必须具备签名准入、数据库兼容、回滚决策和替换版本复测流程。

### 5.4 隐私与审计

- 持久化只保存摘要、分类、状态码和受控元数据。
- 图像和语音文本按 Session 生命周期处理，除非得到明确同意不得写入长期记忆。
- 审计必须能够关联 request/session/plan/effect，但不能还原原始敏感内容。

## 6. 生产激活门槛

| 门槛 | 当前状态 | 关闭条件 |
| --- | --- | --- |
| Runtime 生产编排 | 未完成 | release backend 发布并接通 durable graph/effect |
| Model Provider | 未完成 | OpenClaw Provider 达到 production assurance 并通过故障/恢复验收 |
| Vehicle Adapter | 外部阻塞 | OEM/Vendor 提供 property/service/permission/safety 基线 |
| NPU Provider | 外部阻塞 | 厂商提供 ABI、模型生命周期、buffer 和故障恢复合同 |
| Driver Safety | 外部阻塞 | 可信车速、档位、DMS/身份和 OEM 联锁策略获批 |
| Privacy | 外部阻塞 | owner 批准保存上限、删除、导出和审计策略 |
| Release | 外部阻塞 | 生产签名、候选版本、OTA/安装和回滚演练完成 |
| Target Qualification | 外部阻塞 | 性能、72h 稳定性、故障注入和现场复测完成 |

## 7. 全量需求跟踪

下面的矩阵由历史需求目录归并而来。矩阵中的“软件已实现”只表示仓库主源码存在；“生产激活”必须
同时满足第 6 章门槛。

| 工作包 | 需求说明 | Req ID | 负责模块 | 验收输出 | 当前状态 |
| --- | --- | --- | --- | --- | --- |
| `P0-W01` 开源与行业证据基线 | 开源与行业证据基线 | 全部。 | 产品/架构文档与需求治理。 | AIOS/座舱行业证据、采用与延后边界。 | `已实现` |
| `P0-W02` 产品与 UX 基线 | 产品与 UX 基线 | S2-UX-001..003, S2-HMI-006, S2-SCN-001, S2-SAF-001, S2-EFF-001。 | 产品/架构文档与需求治理。 | 意图优先、行驶限制、失败/补偿/撤销 UX。 | `已实现` |
| `P0-W03` 工程详设和 backlog | 工程详设和 backlog | 全部。 | 产品/架构文档与需求治理。 | 模块、接口、依赖、验证、DoD 与 Req ID 追踪。 | `已实现` |
| `P1-W01` Session DTO/AIDL | Session DTO/AIDL | S2-SES-001, S2-UX-001。 | central-brain-sdk、runtime-service、Room 持久化。 | 五个有界 DTO；session_contract_v1_defined=true、session_parcel_physical_android13_arm64_verified=true。 | `已实现` |
| `P1-W02` Plan/Node DTO/AIDL | Plan/Node DTO/AIDL | S2-SCN-001, S2-GRF-001。 | central-brain-sdk、runtime-service、Room 持久化。 | immutable DAG/11 node allowlist；plan_contract_v1_defined=true、plan_parcel_physical_android13_arm64_verified=true、plan_runtime_published=false。 | `已实现` |
| `P1-W03` Typed Event DTO/AIDL | Typed Event DTO/AIDL | S2-SES-001, S2-EVT-001。 | central-brain-sdk、runtime-service、Room 持久化。 | cursor/replay/callback；event_contract_v1_defined=true、event_parcel_physical_android13_arm64_verified=true、event_runtime_service_published=true、event_callback_service_published=true。 | `已实现` |
| `P1-W04` Effect/Approval DTO | Effect/Approval DTO | S2-EFF-001, S2-SAF-001, S2-UX-003。 | central-brain-sdk、runtime-service、Room 持久化。 | typed target/state/approval/undo；effect_contract_v1_defined=true、effect_parcel_physical_android13_arm64_verified=true、effect_runtime_service_published=false、approval_response_service_published=false、undo_service_published=false。 | `已实现` |
| `P1-W05` SDK facade v2 | SDK facade v2 | S2-SES-001, S2-UX-001..003, S2-EVT-001, APP-004, XSC-001/006。 | central-brain-sdk、runtime-service、Room 持久化。 | ScenarioClient/SessionClient、replay/resubscribe、Binder death/reconnect；sdk_facade_v2_available=true。 | `已实现` |
| `P1-W06` Room v4 schema | Room v4 schema | S2-SES-001, S2-GRF-001, S2-EFF-001, S2-EVT-001。 | central-brain-sdk、runtime-service、Room 持久化。 | durable Session/Event、v3->v4 migration、process-death rehydration；room_schema_version=4。 | `已实现` |
| `P1-W07` Contract v2 aggregate | Contract v2 aggregate | P1 全部。 | central-brain-sdk、runtime-service、Room 持久化。 | AIDL hash、capability、bounds、Room、SDK 与 forbidden fallback 聚合门禁。 | `已实现` |
| `P6-EV2` Durable Session Event V2 | Durable Session Event V2 | S2-EVT-001, S2-SES-001。 | runtime-service 的 Event、Trigger、Consent 与主动建议。 | terminal cursor、owner/session Room ACK、SDK V2 negotiation/V1 fallback。 | `已实现/待量产集成` |
| `P2-W01` Canonical Vehicle Signal Types | Canonical Vehicle Signal Types | S2-CTX-001, S2-TWN-001。 | runtime-service 的 Context/Scenario/Digital Twin 与 生产 adapter。 | 12 路径、timestamp/quality/source/schema；vehicle_signal_schema_defined=true。 | `已实现` |
| `P2-W02` Vehicle Capability Catalog | Vehicle Capability Catalog | S2-TWN-001, S2-ADP-001。 | runtime-service 的 Context/Scenario/Digital Twin 与 生产 adapter。 | 8 capability、area/risk/adapter/authorization；production authorized=0。 | `已实现` |
| `P2-W03` Vehicle Digital Twin Store | Vehicle Digital Twin Store | S2-TWN-001。 | runtime-service 的 Context/Scenario/Digital Twin 与 生产 adapter。 | desired/reported/quality/revision；process-local、非 production trust。 | `已实现` |
| `P2-W04` Trusted Context Snapshot | Trusted Context Snapshot | S2-CTX-001, S2-SAF-001。 | runtime-service 的 Context/Scenario/Digital Twin 与 生产 adapter。 | freshness/trust/restricted report；production source 未接。 | `已实现` |
| `P2-W05` Scenario Manifest | Scenario Manifest | S2-SCN-001, S2-SAF-001。 | runtime-service 的 Context/Scenario/Digital Twin 与 生产 adapter。 | 4 个版本化 build-owned 场景、strict schema、catalog digest 和 checksum；production-signed artifact 未接。 | `已实现` |
| `P2-W06` Deterministic Scenario Resolver | Deterministic Scenario Resolver | S2-SCN-001, S2-SAF-001。 | runtime-service 的 Context/Scenario/Digital Twin 与 生产 adapter。 | fixed selection、availability fail-closed；model 未参与。 | `已实现` |
| `P2-W07` Scenario Plan Compiler | Scenario Plan Compiler | S2-SCN-001, S2-GRF-001, S2-SAF-001。 | runtime-service 的 Context/Scenario/Digital Twin 与 生产 adapter。 | immutable DAG、moving seat branch removal；Runtime publication 未接。 | `已实现` |
| `P3-W01` Agent Graph Runtime state machine | Agent Graph Runtime state machine | S2-GRF-001, NV-G-004/006/007。 | runtime-service 的 Agent Graph、Effect 与恢复模块。 | 10-state bounded control runtime、dependency-ready、failure closed。 | `已实现` |
| `P3-W02` Typed node executors | Typed node executors | S2-GRF-001, S2-SAF-001, S2-EFF-001。 | runtime-service 的 Agent Graph、Effect 与恢复模块。 | 11 node schemas、7 deterministic executors；dispatch disabled。 | `已实现` |
| `P3-W03` CheckpointSerializer | CheckpointSerializer | S2-GRF-001, NV-G-003/006/007。 | runtime-service 的 Agent Graph、Effect 与恢复模块。 | registered DTO、canonical JSON、size/depth/digest guards。 | `已实现` |
| `P3-W04` Retry/Timeout policy | Retry/Timeout policy | S2-GRF-001, NV-G-004。 | runtime-service 的 Agent Graph、Effect 与恢复模块。 | bounded deadline/attempt/backoff；Effect retry requires reconciliation。 | `已实现` |
| `P3-W05` P3 Durable approval interrupt | P3 Durable approval interrupt | S2-SAF-001, S2-UX-003, S2-GRF-001。 | runtime-service 的 Agent Graph、Effect 与恢复模块。 | owner/plan/context/policy/Safety/expiry binding；authority 未发布。 | `已实现` |
| `P3-W06` P3 EffectCoordinator | P3 EffectCoordinator | S2-EFF-001, S2-SAF-001。 | runtime-service 的 Agent Graph、Effect 与恢复模块。 | prepare-all、dependency waves、exact adapter registry；production dispatch=false。 | `已实现` |
| `P3-W07` P3 Effect verification/reconciliation | P3 Effect verification/reconciliation | S2-EFF-001, S2-TWN-001。 | runtime-service 的 Agent Graph、Effect 与恢复模块。 | delivered/applied/verified 分离、Twin reconcile、no redispatch。 | `已实现` |
| `P3-W08` P3 Compensation/Undo | P3 Compensation/Undo | S2-EFF-001, S2-UX-003, S2-SAF-001。 | runtime-service 的 Agent Graph、Effect 与恢复模块。 | before snapshot、TTL、reverse dependency、new governed task。 | `已实现` |
| `P3-W09` P3 Restart recovery | P3 Restart recovery | S2-SES-001, S2-GRF-001, S2-EFF-001, S2-SAF-001。 | runtime-service 的 Agent Graph、Effect 与恢复模块。 | Room recovery reducer、reconcile directives、exactly-once audit。 | `已实现` |
| `P4-R1` Durable Runtime Orchestration V1 | Durable Runtime Orchestration V1 | S2-SES-001, S2-SCN-001, S2-GRF-001, S2-EFF-001。 | Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。 | 生产合同和主源码已形成；量产适配、外部服务和目标验收状态以第 6 章为准。 | `已实现` |
| `P4-W01` Bridge Session/Event API migration | Bridge Session/Event API migration | S2-UX-001, S2-HMI-005, XSC-001。 | Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。 | Client2 typed Session/Event callbacks。 | `已实现` |
| `P4-W02` Cockpit HMI state/reducer/reconnect | Cockpit HMI state/reducer/reconnect | S2-UX-001..003, S2-HMI-003/005/006。 | Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。 | immutable state、single reducer、Binder reconnect。 | `已实现` |
| `P4-W03` Intent-first overlay shell | Intent-first overlay shell | S2-HMI-001..003/006。 | Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。 | Intent/Plan/Execution/Result 工程状态壳。 | `已实现` |
| `P4-W04` P4 Client2 HVAC control surface | P4 Client2 HVAC control surface | S2-HMI-001/003/004/005, S2-ADP-001。 | Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。 | immutable desired、300 ms debounce、scenario ownership。 | `已实现` |
| `P4-W05` P4 Client2 Seat control surface | P4 Client2 Seat control surface | S2-HMI-002..005, S2-SAF-001, S2-ADP-001。 | Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。 | seat typed state、Safety/approval gate。 | `已实现` |
| `P4-W06` P4 Client2 observable execution timeline | P4 Client2 observable execution timeline | S2-UX-001, S2-HMI-003/006, S2-EVT-001。 | Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。 | 七阶段、最多八条 typed event、partial visibility。 | `已实现` |
| `P4-W07` P4 Client2 approval/recovery UX | P4 Client2 approval/recovery UX | S2-UX-003, S2-HMI-003, S2-SAF-001。 | Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。 | approval/partial/retry/undo projection。 | `已实现` |
| `P4-W08` P4 Client2 driving restriction renderer | P4 Client2 driving restriction renderer | S2-UX-002, S2-HMI-002。 | Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。 | PARKED/MOVING/UNKNOWN_RESTRICTED rendering。 | `已实现` |
| `P4-W10` Scenario/manual-control synchronization | Scenario/manual-control synchronization | S2-HMI-001..006, S2-SCN-001。 | Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。 | canonical roles、lifecycle、sequence ownership。 | `已实现` |
| `P4-W11` Accessibility/display matrix | Accessibility/display matrix | S2-UX-003, S2-HMI-001/002。 | Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。 | 48dp、非颜色单一表达、1920x1080 safe frame。 | `已实现` |
| `P4-W12` P4 Android aggregate acceptance | P4 Android aggregate acceptance | P4 全部。 | Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。 | navigation/scenario/fault/restart/display application acceptance。 | `已实现` |
| `P4-R2` Client2 Orchestration V1 migration | Client2 Orchestration V1 migration | APP-004, S2-SES-001, S2-SCN-001, S2-EVT-001。 | Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。 | legacy scenario Binder removed；formal SDK/Room/Effect projection。 | `已实现` |
| `P4-R3` Voice-first live cockpit HMI | Voice-first live cockpit HMI | S2-HMI-001..006, S2-MDL-001, S2-EFF-001。 | Client2 HMI、Central Brain Java SDK 与 Orchestration Runtime。 | 两个自然触发、32 行调用链、HVAC/Seat 动画；voice_first_hmi_implemented=true。 | `已实现` |
| `P4-R4` Multimodal model I/O live HMI | Multimodal model I/O live HMI | S2-HMI-003/007/008, S2-MDL-002, S2-OBS-002, S2-SAF-001, XSC-001/005/006。 | Client2 HMI、Central Brain Java SDK 与 Runtime 模型 I/O 投影。 | 生产合同和主源码已形成；量产适配、外部服务和目标验收状态以第 6 章为准。 | `已实现` |
| `P4-R5a` Controlled multimodal input binding | 软件必须交付“Controlled multimodal input binding”，满足 APP-004, S2-HMI-008, S2-PER-001, S2-OBS-001，把文字、单图 FD、摘要、Session 和场景绑定为一次消费输入。 | APP-004, S2-HMI-008, S2-PER-001, S2-OBS-001。 | Client2 多模态输入、SDK 生产 AIDL 与 Runtime input store。 | 生产合同和主源码已形成；量产适配、外部服务和目标验收状态以第 6 章为准。 | `已实现` |
| `P4-R5b` Cabin observation projection | 软件必须交付“Cabin observation projection”，满足 S2-PER-001, S2-MDL-002, S2-SAF-001, S2-OBS-001，只投影座位区域占用和可见饮水容器事实。 | S2-PER-001, S2-MDL-002, S2-SAF-001, S2-OBS-001。 | Runtime model boundary 与 Client2 event projection。 | 调用链显示三个已占用区域和后排右侧饮水容器，并与当前 Run 绑定。 | `已实现` |
| `P4-R5c` Controlled multi-seat Context | 软件必须交付“Controlled multi-seat Context”，满足 S2-CTX-001/002, S2-TWN-001, S2-SAF-001，使场景支持 CABIN 与四座位区域并投影当前夹具占用。 | S2-CTX-001/002, S2-TWN-001, S2-SAF-001。 | Scenario manifest、生产 decision composition 和 Client2 feedback。 | 场景 zones 包含四座位；UI 显示三个已占用区域并保持 受控 边界。 | `已实现` |
| `P4-R5d` Shopping semantic model allowlist | 软件必须交付“Shopping semantic model allowlist”，满足 S2-MDL-001/002, S2-PER-001, S2-INT-001, S2-SAF-001，将多模态输出限制为购物与购买路线候选。 | S2-MDL-001/002, S2-PER-001, S2-INT-001, S2-SAF-001。 | Cockpit prompt、OpenClaw/Ollama provider 和 structured output validator。 | 必须包含 shopping.search_products 与 navigation.plan_purchase_route；不得要求 HVAC/Media。 | `已实现` |
| `P4-R5e` Evidence-bound shopping intent | 软件必须交付“Evidence-bound shopping intent”，满足 S2-INT-001, S2-PER-001, S2-CTX-002, S2-SAF-001，把购物需求表达为必须确认的候选意图。 | S2-INT-001, S2-PER-001, S2-CTX-002, S2-SAF-001。 | Scenario policy node、model action validator 和 Orchestration projection。 | resolve_shopping_intent 只能进入购物同意中断；未确认不得运行 Tool。 | `已实现` |
| `P4-R5f` Shopping and route-planning scenario DAG | 软件必须交付“Shopping and route-planning scenario DAG”，满足 S2-SCN-001, S2-GRF-001, S2-INT-001, S2-TOL-001，编排观察、购物意图、确认、搜索、预览、提交和总结。 | S2-SCN-001, S2-GRF-001, S2-INT-001, S2-TOL-001。 | Scenario manifest/schema/catalog、compiler、Graph runtime 和 checksum。 | 交付 scene.cabin.multimodal.assist.v1 v2 的 13 节点 DAG、六 Tool、三确认和冻结摘要；目录门禁固定该资产为 v2，并保持其他三个内置资产为 v1。 | `已实现` |
| `P4-R5g` Independent shopping purchase navigation confirmations | 软件必须交付“Independent shopping purchase navigation confirmations”，满足 S2-SAF-001, S2-HMI-003/009, S2-NAV-001, S2-COM-001，定义购物同意、订单提交和导航启动三个互不兼容的确认。 | S2-SAF-001, S2-HMI-003/009, S2-NAV-001, S2-COM-001。 | Scenario composition、Orchestration approval handling 与 Client2 controls。 | 三个不同 pending_node_id 依次中断；每个 approval digest 只允许对应 Tool 使用。 | `已实现` |
| `P4-R5h` Shopping route Tool orchestration | 软件必须交付“Shopping route Tool orchestration”，满足 S2-TOL-001, S2-NAV-001, S2-COM-001, S2-SAF-001，按场景节点执行六个 allowlisted 购物与路线 Tool。 | S2-TOL-001, S2-NAV-001, S2-COM-001, S2-SAF-001。 | Scenario Graph、pending Tool state 和 SimulatedShoppingPlanningService。 | 六个 Tool 都生成有界 result digest；未知 Tool 或缺确认时失败关闭。 | `已实现` |
| `P4-R5i` Route-planning search preview start | 软件必须交付“Route-planning search preview start”，满足 S2-NAV-001, S2-SAF-001, S2-TOL-001, S2-OBS-002，拆分商户 POI、路线预览和导航启动。 | S2-NAV-001, S2-SAF-001, S2-TOL-001, S2-OBS-002。 | POI Tool、route preview Tool、navigation start Tool 和 HMI route projection。 | 三个 受控 商户候选、2.4 km/4 min 预览及 NAVIGATION_SIMULATED。 | `已实现` |
| `P4-R5j` Shopping search prepare commit | 软件必须交付“Shopping search prepare commit”，满足 S2-COM-001, S2-SAF-001, S2-TOL-001, S2-OBS-001，将商品搜索、商户搜索、订单预览和提交建模为独立 Tool。 | S2-COM-001, S2-SAF-001, S2-TOL-001, S2-OBS-001。 | 生产 product/merchant/order service、purchase approval 和 Client2 projection。 | 三项商品、三项商户和订单预览；commit 返回 ORDER_NOT_DISPATCHED。 | `已实现` |
| `P4-R5k` External dispatch fail-closed boundary | 软件必须交付“External dispatch fail-closed boundary”，满足 S2-CTX-002, S2-EFF-001, S2-ADP-001/002, S2-SAF-001，确保购物、支付、地图和车辆接口缺失时不外发。 | S2-CTX-002, S2-EFF-001, S2-ADP-001/002, S2-SAF-001。 | Shopping Tool result authority flags、scenario composition 和 HMI boundary labels。 | externalDispatchPerformed=false、paymentMaterialAccessed=false、vehicleHardwareAccessed=false。 | `已实现` |
| `P4-R5l` Client2 event-driven shopping route HMI | 软件必须交付“Client2 event-driven shopping route HMI”，满足 S2-HMI-003/007/008/009, S2-OBS-002, S2-UX-002/003，实时显示模型输入/输出、购物意图、确认、商品、订单和路线。 | S2-HMI-003/007/008/009, S2-OBS-002, S2-UX-002/003。 | Client2 immutable state/reducer/coordinator/XML、Orchestration callback 和 live trace projection。 | 生产合同和主源码已形成；量产适配、外部服务和目标验收状态以第 6 章为准。 | `已实现` |
| `P4-R6` Unity 原生 HVAC 与座椅展开修复 | Cold 场景必须更新 RenderService/Unity 原生驾驶席和乘员席温度，禁止使用 Android TextView 覆盖 Unity；Fatigue 座椅靠背必须从 15 度向 30 度展开。 | S2-HMI-001/002/003/004, S2-UX-002, S2-ADP-001/002, S2-SAF-001, DEL-004。 | Client2 CockpitControlCoordinator、TuanjieView 跨进程触控、RenderService Unity Addressables patch。 | 生产合同和主源码已形成；量产适配、外部服务和目标验收状态以第 6 章为准。 | `已实现` |
| `P5-W01` P5 Tool Manifest/Schema | P5 Tool Manifest/Schema | S2-TOL-001。 | runtime-service 的 Tool、Skill、Memory 与 Context Budget。 | versioned manifest、bounded input/output schema。 | `已实现` |
| `P5-W02` P5 Tool Registry/Resolver | P5 Tool Registry/Resolver | S2-TOL-001。 | runtime-service 的 Tool、Skill、Memory 与 Context Budget。 | version map、health freshness、registered/resolved/usable separation。 | `已实现` |
| `P5-W03` P5 Tool RuleSolver | P5 Tool RuleSolver | S2-TOL-001, S2-SAF-001。 | runtime-service 的 Tool、Skill、Memory 与 Context Budget。 | rule x model x usable intersection、fail-closed。 | `已实现` |
| `P5-W04` P5 Tool Executor boundary | P5 Tool Executor boundary | S2-TOL-001, S2-SAF-001, S2-OBS-001。 | runtime-service 的 Tool、Skill、Memory 与 Context Budget。 | signed built-in executor、deadline/output bound、digest-only invocation。 | `已实现` |
| `P5-W05` P5 Skill package verifier | P5 Skill package verifier | S2-TOL-001, S2-SAF-001, S2-OBS-001。 | runtime-service 的 Tool、Skill、Memory 与 Context Budget。 | signer/version/digest/anti-downgrade；no dynamic load。 | `已实现` |
| `P5-W06` P5 WorkingMemoryStore | P5 WorkingMemoryStore | S2-MEM-001, S2-SAF-001, S2-OBS-001。 | runtime-service 的 Tool、Skill、Memory 与 Context Budget。 | owner/session bounded process-local store。 | `已实现` |
| `P5-W07` P5 ProfileMemoryStore | P5 ProfileMemoryStore | S2-MEM-001, S2-SAF-001, S2-OBS-001。 | runtime-service 的 Tool、Skill、Memory 与 Context Budget。 | consent/field/user-seat binding；contract cipher only。 | `已实现` |
| `P5-W08` P5 EpisodicMemoryStore | P5 EpisodicMemoryStore | S2-MEM-001, S2-SAF-001, S2-OBS-001。 | runtime-service 的 Tool、Skill、Memory 与 Context Budget。 | typed bounded scenario summaries；process-local。 | `已实现` |
| `P5-W09` P5 ContextBudgetManager | P5 ContextBudgetManager | S2-MEM-001, S2-MDL-001, S2-SAF-001。 | runtime-service 的 Tool、Skill、Memory 与 Context Budget。 | metadata-only budget decision；tokenizer/summary execution 未接。 | `已实现` |
| `P5-W10` P5 Memory consent HMI/API | P5 Memory consent HMI/API | S2-MEM-001, S2-UX-003。 | runtime-service 的 Tool、Skill、Memory 与 Context Budget。 | source visibility、disable、clear preference、moving restriction。 | `已实现` |
| `P6-W01` P6 EventBroker interface/in-process | P6 EventBroker interface/in-process | S2-EVT-001, S2-SAF-001。 | runtime-service 的 Event、Trigger、Consent 与主动建议。 | typed topic、append-before-notify、bounded replay、identity policy。 | `已实现` |
| `P6-W02` P6 Event Backpressure/QoS | P6 Event Backpressure/QoS | S2-EVT-001, NV-G-004, S2-SAF-001。 | runtime-service 的 Event、Trigger、Consent 与主动建议。 | 4 policies、critical no-silent-drop、consumer isolation。 | `已实现` |
| `P6-W03` P6 TriggerRule/TriggerEngine | P6 TriggerRule/TriggerEngine | S2-EVT-001, S2-SCN-001, S2-SAF-001。 | runtime-service 的 Event、Trigger、Consent 与主动建议。 | threshold/window/debounce/cooldown、suggestion-only。 | `已实现` |
| `P6-W04` P6 Proactive consent/policy | P6 Proactive consent/policy | S2-SAF-001, S2-MEM-001, S2-EVT-001。 | runtime-service 的 Event、Trigger、Consent 与主动建议。 | exact grant binding、HIGH/CRITICAL hard block、TTL/revoke。 | `已实现` |
| `P6-W05` P6 Context source adapters | P6 Context source adapters | S2-CTX-001, S2-EVT-001, S2-SAF-001。 | runtime-service 的 Event、Trigger、Consent 与主动建议。 | Runtime health、time 与 vehicle source normalization。 | `已实现` |
| `P6-W06` P6 Active suggestion UX | P6 Active suggestion UX | S2-UX-002, S2-TRG-002, S2-SAF-001。 | runtime-service 的 Event、Trigger、Consent 与主动建议。 | full/minimal card、merge/replay、never-ask。 | `已实现` |
| `P7-W01` P7 ModelRequest/Result v2 | P7 ModelRequest/Result v2 | S2-MDL-001, S2-SAF-001, S2-OBS-001。 | runtime-service 的 Model Provider/Registry/Router 与模型网关。 | privacy/latency/token/capability/fallback/digest contract。 | `已实现` |
| `P7-W02` P7 ModelProviderRegistry/health | P7 ModelProviderRegistry/health | S2-MDL-001, S2-SAF-001, S2-OBS-001。 | runtime-service 的 Model Provider/Registry/Router 与模型网关。 | 5-provider catalog、health source/freshness/replay。 | `已实现` |
| `P7-W03` P7 PolicyAwareModelRouter | P7 PolicyAwareModelRouter | S2-MDL-001, S2-SAF-001, S2-OBS-001。 | runtime-service 的 Model Provider/Registry/Router 与模型网关。 | privacy/network/thermal/latency/quota/fallback decision。 | `已实现` |
| `P7-W05` P7 Structured Model Output | P7 Structured Model Output | S2-MDL-001, S2-SAF-001, S2-OBS-001。 | runtime-service 的 Model Provider/Registry/Router 与模型网关。 | exact JSON、scenario binding、action allowlist、raw log=false。 | `已实现` |
| `P7-W06` P7 Scenario Evaluation | P7 Scenario Evaluation | S2-MDL-001, S2-SAF-001, S2-OBS-001。 | runtime-service 的 Model Provider/Registry/Router 与模型网关。 | deterministic evaluation harness、bounded metrics。 | `已实现` |
| `P7-W07` P7 Resource Admission | P7 Resource Admission | S2-MDL-001, NV-G-004。 | runtime-service 的 Model Provider/Registry/Router 与模型网关。 | foreground priority、thermal degradation、fail-closed。 | `已实现` |
| `P7-R3-OC2` OpenClaw target transitional gateway | OpenClaw target transitional gateway | S2-MDL-001/002, S2-SAF-001, S2-OBS-001/002。 | runtime-service 的 Model Provider/Registry/Router 与模型网关。 | 已实现 WebSocket v3、challenge/auth/send/history/abort、文字与图片附件及 Client2 projection；量产网络、凭据治理和发布资格仍须完成第 6 章门槛。 | `已实现/待量产集成` |
| `P9-W01` P9 Performance Budget Contract | P9 Performance Budget Contract | S2-OBS-001。 | 质量、隐私、安全、发布与诊断合同/探针。 | 7 categories/10 metrics、strict report、受控 probe。 | `已实现/待量产集成` |
| `P9-W02` P9 Stability Fault Matrix Contract | P9 Stability Fault Matrix Contract | S2-REL-001, S2-OBS-001。 | 质量、隐私、安全、发布与诊断合同/探针。 | 3 workloads x 6 faults = 18 cases、strict report。 | `已实现/待量产集成` |
| `P9-W03a` P9 Parser Security Corpus | P9 Parser Security Corpus | S2-SAF-001, S2-TOL-001, S2-SES-001, S2-MDL-001。 | 质量、隐私、安全、发布与诊断合同/探针。 | 3 parser surfaces/18 hostile cases、deterministic regression。 | `已实现` |
| `P9-W03b` P9 Identity/Replay Security Corpus | P9 Identity/Replay Security Corpus | S2-SAF-001, S2-SES-001。 | 质量、隐私、安全、发布与诊断合同/探针。 | caller/session/signer 3 surfaces/18 cases。 | `已实现` |
| `P9-W03c` P9 Security Boundary Inventory | P9 Security Boundary Inventory | S2-SAF-001, S2-OBS-001。 | 质量、隐私、安全、发布与诊断合同/探针。 | 37 public AIDL items、8 validation families、生产 probe。 | `已实现` |
| `P9-W03d` Binder identity device evidence | Binder identity device evidence | S2-SAF-001, S2-OBS-001。 | 质量、隐私、安全、发布与诊断合同/探针。 | UID/package/current signer、spoof negative case。 | `已实现` |
| `P9-W03e` Callback replay device evidence | Callback replay device evidence | S2-SAF-001, S2-TOL-001, S2-OBS-001。 | 质量、隐私、安全、发布与诊断合同/探针。 | task/sequence/single-terminal/cross-owner isolation。 | `已实现` |
| `P9-W03f` Executable parser robustness campaign | “Executable parser robustness campaign”的历史可执行实现必须保持退役，仓库不得重新引入该能力；相关需求只保留审计记录。 | S2-SAF-001。 | 质量、隐私、安全、发布与诊断合同/探针。 | 验收要求仓库中无现行执行路径，并保留“历史实现已按用户决定完整撤回”的历史结论。 | `已实现` |
| `P9-W03g` External security evidence interface | “External security evidence interface”只允许保留非执行接口和状态记录，不得在仓库内启用执行器或自动传输。 | S2-SAF-001, S2-OBS-001。 | 质量、隐私、安全、发布与诊断合同/探针。 | 验收只检查接口字段、禁止项和执行器缺失；当前交付为“8-field metadata/digest/reference interface；无 executor/transport”。 | `挂起` |
| `P9-W04a` P9 Privacy Data Inventory | P9 Privacy Data Inventory | S2-MEM-001, S2-SAF-001, S2-OBS-001。 | 质量、隐私、安全、发布与诊断合同/探针。 | 12 surfaces、retention/delete/export/log/enforcement classification。 | `已实现` |
| `P9-W04b` P9 Privacy Policy Admission | P9 Privacy Policy Admission | S2-MEM-001, S2-SAF-001。 | 质量、隐私、安全、发布与诊断合同/探针。 | owner evidence、delete/hold/export guards；draft denied。 | `已实现` |
| `P9-W04c` P9 Privacy Redaction/Audit Probe | P9 Privacy Redaction/Audit Probe | S2-SAF-001, S2-OBS-001。 | 质量、隐私、安全、发布与诊断合同/探针。 | 21 keys、10 forbidden field classes、生产 Activity。 | `已实现` |
| `P9-W05a` P9 Production Release Admission | P9 Production Release Admission | S2-REL-001。 | 质量、隐私、安全、发布与诊断合同/探针。 | exact 3 APK set、same-signer/cohort/version/schema/rollback gate。 | `已实现` |
| `P9-W05b` P9 Production Release Metadata Probe | P9 Production Release Metadata Probe | S2-REL-001, S2-OBS-001。 | 质量、隐私、安全、发布与诊断合同/探针。 | 27-key projection、read-only no-install adapter。 | `已实现` |
| `P9-W06a` Driver Safety Admission | Driver Safety Admission | S2-UX-002, S2-SAF-001, S2-EFF-001, S2-OBS-001。 | 质量、隐私、安全、发布与诊断合同/探针。 | 12 actions、4 UX profiles、500 ms state、moving hard interlock。 | `已实现` |
| `P9-W06b` Driver Safety Redacted Probe | Driver Safety Redacted Probe | S2-SAF-001, S2-OBS-001。 | 质量、隐私、安全、发布与诊断合同/探针。 | 27-key projection、DUMP Activity、no-install adapter。 | `已实现` |
| `P9-W07a` Release Evidence Envelope | Release Evidence Envelope | S2-OBS-001, S2-REL-001, DEL-001/004/005。 | 质量、隐私、安全、发布与诊断合同/探针。 | release identity、8 diagnostic categories、stable report digest。 | `已实现` |
| `P9-W07b` Field Diagnostics Probe | Field Diagnostics Probe | S2-OBS-001, S2-REL-001。 | 质量、隐私、安全、发布与诊断合同/探针。 | 31-key projection、5 executed + 3 NOT_RUN adapter。 | `已实现` |
| `P9-W07c` Release Retest Workflow | Release Retest Workflow | S2-REL-001, DEL-004/005。 | 质量、隐私、安全、发布与诊断合同/探针。 | 5 states/5 transitions、replacement identity、four-party digest。 | `已实现` |
| `P10-R1` P10-R1 Android repository software completion | P10-R1 Android repository software completion | 全部已分类 Req IDs。 | 仓库级需求治理与软件完成度门禁。 | P4-R5 生产 软件完成后 repository_software_requirements_complete=true、open_repository_software_requirement_count=0、unclassified_repository_requirement_count=0。 | `已实现` |
| `P3-ACT-01` Agent Graph production wiring | Agent Graph production wiring | S2-GRF-001, S2-EFF-001。 | 对应 Runtime 模块与目标平台集成 owner。 | 完成条件：取得并审查“Effect material/authority、retention/encryption、target fault evidence；ISSUE-022”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。 | `外部阻塞` |
| `P4-ACT-01` 中控 AIOS 真实车辆闭环 | 中控 AIOS 真实车辆闭环 | S2-HMI-001..006, S2-EFF-001, S2-ADP-002。 | 对应 Runtime 模块与目标平台集成 owner。 | 完成条件：取得并审查“vehicle service、approval/undo authority、target validation；ISSUE-019/023/030/033”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。 | `外部阻塞` |
| `P5-ACT-01` Tool/Skill/Memory production publication | Tool/Skill/Memory production publication | S2-TOL-001, S2-MEM-001。 | 对应 Runtime 模块与目标平台集成 owner。 | 完成条件：取得并审查“signer、storage、identity、privacy owner；ISSUE-040..045”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。 | `外部阻塞` |
| `P6-ACT-01` Durable production Event/Trigger/Consent | Durable production Event/Trigger/Consent | S2-EVT-001, S2-SAF-001。 | 对应 Runtime 模块与目标平台集成 owner。 | 完成条件：取得并审查“publisher/middleware/source/identity/receipt owner；ISSUE-031/046”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。 | `外部阻塞` |
| `P7-ACT-01` Production Model Provider | Production Model Provider | S2-MDL-001/002, S2-OBS-001/002。 | 对应 Runtime 模块与目标平台集成 owner。 | 完成条件：取得并审查“TLS、credential owner、health/version、artifact、resource producer、NPU evidence；ISSUE-024/044/054”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。 | `外部阻塞` |
| `P8-W01` P8 Target Capability Discovery | P8 Target Capability Discovery | S2-ADP-002。 | 目标平台集成 owner、Vehicle/Vendor/NPU adapter。 | 完成条件：取得并审查“14-column software contract/collector 已完成；缺 OEM property/service/permission/owner/version evidence；ISSUE-047”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。 | `外部阻塞` |
| `P8-W02` VSS to AAOS mapping | VSS to AAOS mapping | S2-ADP-002。 | 目标平台集成 owner、Vehicle/Vendor/NPU adapter。 | 完成条件：取得并审查“缺公开 CarProperty/service schema、area/type/read-write/permission”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。 | `外部阻塞` |
| `P8-W03` AaosCarPropertyEffectAdapter | AaosCarPropertyEffectAdapter | S2-ADP-002。 | 目标平台集成 owner、Vehicle/Vendor/NPU adapter。 | 生产合同和主源码已形成；量产适配、外部服务和目标验收状态以第 6 章为准。 | `外部阻塞` |
| `P8-W04` Vendor service adapter | Vendor service adapter | S2-ADP-002。 | 目标平台集成 owner、Vehicle/Vendor/NPU adapter。 | 完成条件：取得并审查“缺 Vendor service ABI/AIDL、owner、version、permission”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。 | `外部阻塞` |
| `P8-W05` Vendor NPU provider | Vendor NPU provider | S2-MDL-001, S2-ADP-002。 | 目标平台集成 owner、Vehicle/Vendor/NPU adapter。 | 完成条件：取得并审查“缺 Vendor SDK、PCIe runtime、model artifact、memory/cancel/performance evidence”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。 | `外部阻塞` |
| `P8-W06` Production activation evidence | Production activation evidence | S2-ADP-002, DEL-005。 | 目标平台集成 owner、Vehicle/Vendor/NPU adapter。 | 完成条件：取得并审查“每项 capability 的 owner/ABI/permission/safety/smoke/rollback 证据未提供”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。 | `外部阻塞` |
| `P9-EXT-01` Target performance measurement | Target performance measurement | S2-OBS-001。 | 目标验证、OEM/Vendor owner 与发布责任人。 | 完成条件：取得并审查“owner-approved 30-sample target evidence；ISSUE-048”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。 | `外部阻塞` |
| `P9-EXT-02` 72h stability/fault run | 72h stability/fault run | S2-REL-001, S2-OBS-001。 | 目标验证、OEM/Vendor owner 与发布责任人。 | 完成条件：取得并审查“real workload/fault injection/72h evidence；ISSUE-049”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。 | `外部阻塞` |
| `P9-EXT-03` Security external evidence | Security external evidence | S2-SAF-001。 | 目标验证、OEM/Vendor owner 与发布责任人。 | 完成条件：取得并审查“executable campaign suspended；只接受批准的非秘密 evidence interface；ISSUE-050”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。 | `挂起` |
| `P9-EXT-04` Privacy owner policy and enforcement | Privacy owner policy and enforcement | S2-MEM-001, S2-SAF-001。 | 目标验证、OEM/Vendor owner 与发布责任人。 | 完成条件：取得并审查“owner ceiling/evidence、repository enforcement、target probe；ISSUE-051”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。 | `外部阻塞` |
| `P9-EXT-05` Production signer/OTA/rollback | Production signer/OTA/rollback | S2-REL-001。 | 目标验证、OEM/Vendor owner 与发布责任人。 | 完成条件：取得并审查“signer owner、candidate、MDM/OTA、rollback rehearsal；ISSUE-052”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。 | `外部阻塞` |
| `P9-EXT-06` OEM driver-safety acceptance | OEM driver-safety acceptance | S2-UX-002, S2-SAF-001, S2-EFF-001。 | 目标验证、OEM/Vendor owner 与发布责任人。 | 完成条件：取得并审查“trusted state、DMS/identity、seat/HVAC policy、hard interlock、owner sign-off；ISSUE-029/030”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。 | `外部阻塞` |
| `P9-EXT-07` Complete release/field retest | Complete release/field retest | S2-OBS-001, S2-REL-001, DEL-004/005。 | 目标验证、OEM/Vendor owner 与发布责任人。 | 完成条件：取得并审查“named replacement release、target report、owner/tester evidence；ISSUE-052/053”，随后通过目标 smoke、错误/恢复、权限、安全和回滚证据；在此之前状态不得提升。 | `外部阻塞` |
| `SCOPE-04` Unapproved kernel/Driver/HAL extension | 在未取得独立批准和明确接口基线前，项目不得实现或推断“Unapproved kernel/Driver/HAL extension”。 | 用户批准的范围约束。 | 项目治理；不分配实现模块。 | 通过仓库静态门禁证明当前交付中不存在被禁止的实现；范围结论保持 SUSPENDED。 | `挂起` |
| `SCOPE-05` Unconfirmed protocol binding | 在未取得独立批准和明确接口基线前，项目不得实现或推断“Unconfirmed protocol binding”。 | 用户批准的范围约束。 | 项目治理；不分配实现模块。 | 通过仓库静态门禁证明当前交付中不存在被禁止的实现；范围结论保持 SUSPENDED。 | `挂起` |
| `P4-R7` 双屏渲染、动态 HVAC 与车模触摸交互 | 生产 HMI 必须保持 1920x1080 清晰渲染、连续温度状态和原生车模触摸交互。 | S2-HMI-006, S2-HMI-007, S2-ADP-003 | Client2 座舱 HMI、RenderService 集成层 | 设计已进入主分支；实现仍在独立 Draft，合入前不得声明主分支已实现。 | `设计完成/实现待合并` |

## 8. 退出生产基线的历史 ID

本节只用于证明需求没有在文档合并中丢失。这里的 ID 不再定义生产软件行为，也不提供其环境或实现说明。

| 工作包 | 当前状态 |
| --- | --- |
| `P2-W08` | `已退出生产基线` |
| `P2-W09` | `已退出生产基线` |
| `P2-W10` | `已退出生产基线` |
| `P2-W11` | `已退出生产基线` |
| `P2-W12` | `已退出生产基线` |
| `P4-W09` | `已退出生产基线` |
| `P4-D4a` | `已退出生产基线` |
| `P4-D4b` | `已退出生产基线` |
| `P4-D4c` | `已退出生产基线` |
| `P4-D4d` | `已退出生产基线` |
| `P4-D4e` | `已退出生产基线` |
| `P5-R1` | `已退出生产基线` |
| `P6-P7-R1` | `已退出生产基线` |
| `P7-W04` | `已退出生产基线` |
| `P7-R2` | `已退出生产基线` |
| `P7-R4-OCDEV` | `已退出生产基线` |
| `P7-R5-MMDEV` | `已退出生产基线` |
| `SCOPE-01` | `已退出生产基线` |
| `SCOPE-02` | `已退出生产基线` |
| `SCOPE-03` | `已退出生产基线` |

## 9. 需求变更规则

1. 新需求必须获得唯一 Req ID 和工作包 ID。
2. 修改接口时必须同时更新本文、软件架构文档、软件开发文档和对应代码合同。
3. 状态提升必须附带与状态语义一致的证据；软件检查不能替代量产硬件证据。
4. 外部输入缺失时只能维持 `外部阻塞` 或 `挂起`，不能以占位实现关闭需求。
5. 被生产需求替代的历史工作包进入第 8 章，不删除 ID。
