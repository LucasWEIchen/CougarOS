# Central Brain AIOS 开源项目与车载行业架构调研

版本：1.0

日期：2026-07-15

状态：Stage 2 设计输入

适用需求：`APP-001`、`APP-003`、`APP-004`、`FW-U-001..008`、`FW-S-001..006`、`NV-F-001`、`NV-F-003..005`、`NV-F-008..012`、`NV-G-001..007`、`NV-P-002`、`DEL-001`、`DEL-003..005`

## 1. 调研目标

本调研回答三个工程问题：

1. Central Brain 当前 Android 13 原型相对成熟 Agent/AIOS 项目缺少哪些运行时能力；
2. 通用 Agent 运行时的哪些模式可以进入车载座舱，哪些模式因安全、权限、实时性或黑盒平台约束不能直接采用；
3. 如何把开源经验映射回用户架构图中的 AI SDK、Uni Info Bus、SOA、AIOS Kernel、Runtime & Governance 和 Protocol Binding，而不是另建一套平行架构。

本调研不把 GitHub 热度等同于车规成熟度。所选项目用于分析软件机制，不代表它们已经通过汽车功能安全、网络安全或量产认证。

## 2. 选择标准与源码快照

选择标准：公开源码、架构边界清晰、包含可执行运行时而非只有提示词、在 Agent 调度/持久化/工具/记忆/消息协议中至少一个方向具有代表性。

| 项目/标准 | 本地审阅 commit | 重点源码/规范 | 对 Central Brain 的用途 |
| --- | --- | --- | --- |
| AGI Research AIOS | `4171a8ea2d56f7d119a109c5317e998575b679e1` | `aios/syscall/syscall.py`、`aios/scheduler/*`、`aios/tool/manager.py`、`aios/memory/manager.py` | Kernel syscall、资源管理器、模型/工具/记忆分离 |
| Cerebrum | `be132ade22a302173cde9f6ac1c7721830e77faf` | Agent SDK、LLM/Tool/Memory/Storage API、MCP pool、package manager | 平台 Kernel 与开发者 SDK/技能包边界 |
| LangGraph | `49ae27c2ae983cfb92091b0dea9f7bc37a716479` | `langgraph/types.py`、`pregel/_runner.py`、checkpoint/serializer | 可恢复图执行、interrupt、retry、timeout、持久 checkpoint |
| Microsoft AutoGen | `027ecf0a379bcc1d09956d46d12d44a3ad9cee14` | `_agent_runtime.py`、`_serialization.py`、`_single_threaded_agent_runtime.py` | typed message、direct/pub-sub、agent factory 与状态保存 |
| OpenHands | `5adf4dac2d9bf07605d51b5746891d457bd23076` | 应用侧 event service、SDK pinning | 会话事件持久化和产品集成边界 |
| OpenHands SDK | `c2e72045b26d18b1e2a9ad7098a40c7690f5263e` | `event/base.py`、`security/analyzer.py`、`confirmation_policy.py`、`tool/registry.py`、`conversation/state.py` | Action/Observation、风险确认、工具可用性、会话状态 |
| Letta | `b76da9092518cbaa2d09042e52fdcbde69243e18` | `schemas/block.py`、`schemas/memory.py`、`schemas/run.py`、`schemas/tool_rule.py`、`helpers/tool_rule_solver.py` | 分层记忆、上下文预算、工具规则、run 生命周期 |
| COVESA VSS | `ce67f9276aa360d16b2e9e619d41c96e5d9f19d2` | `spec/Cabin/HVAC.vspec`、`spec/Cabin/Seat.vspec` | 车身信号标准命名和座椅/HVAC 能力模型 |
| Eclipse uProtocol | `e49a6e3610d40399b2b5ae858ec5f3f136066915` | `up-l1`、`up-l2`、`up-l3/utwin/v2`、permissions | 跨 Android/Linux/ECU transport、RPC/pub-sub、数字孪生、权限 |

外部链接：

- [AIOS](https://github.com/agiresearch/AIOS)
- [Cerebrum](https://github.com/agiresearch/Cerebrum)
- [LangGraph](https://github.com/langchain-ai/langgraph)
- [AutoGen](https://github.com/microsoft/autogen)
- [OpenHands](https://github.com/All-Hands-AI/OpenHands)
- [OpenHands SDK](https://github.com/All-Hands-AI/software-agent-sdk)
- [Letta](https://github.com/letta-ai/letta)
- [COVESA Vehicle Signal Specification](https://covesa.github.io/vehicle_signal_specification/)
- [Eclipse uProtocol](https://github.com/eclipse-uprotocol)

## 3. 当前原型基线

### 3.1 已具备

当前 Android 工程已经不是单一聊天 APK，已有以下 AIOS 基础骨架：

- typed AIDL Runtime/Governance/Diagnostic 接口和 Java SDK；
- Binder caller identity、signature permission、capability policy；
- `JobSupervisor`、`InferenceResourceScheduler`；
- Room v3 task/session/checkpoint/approval/outbox/effect/event cursor 持久化骨架；
- Effect prepare/claim/retry/reconcile、adapter contract、production activation fail-closed；
- model provider contract、deterministic test provider、test-only router；
- bounded Event、Memory、signed built-in Skill runtime；
- C ABI/JNI process lifecycle；
- Demo HMI 和 Client2 场景面板、Android 13 真机应用层验收。

### 3.2 尚不构成完整 AIOS 的原因

现有实现验证了接口、身份、持久化和 fail-closed 边界，但缺少把这些模块组合成可感知产品行为的执行层：

- 场景按钮仍近似“发送文本 -> 显示模型回复”，没有可执行场景计划；
- 没有标准化车辆 Context Snapshot 和 Digital Twin；
- 没有 Agent Graph 节点执行、恢复、分支、补偿和用户中断；
- Tool/Skill 只有 bounded contract，缺少签名包、版本、依赖、健康、运行隔离和调用状态；
- Memory 没有 working/profile/episodic 分层、用户同意、保留/删除；
- Effect 没有 HVAC/Seat/Nav/Media 的实际或仿真 adapter，也没有 applied/verified 反馈；
- HMI 看不到计划、执行进度、部分失败、确认、撤销和恢复；
- 真实 VHAL/NPU/vendor service 仍是外部阻塞项。

## 4. 开源项目源码结论

### 4.1 AIOS：系统调用和资源管理器分离

AIOS 的 syscall 层把 LLM、Memory、Storage 和 Tool 请求转换为不同类型的 syscall，放入对应 manager 队列；scheduler 为不同资源执行循环和时间片调度。这证明“Agent 不直接操作资源、统一进入 Kernel resource manager”是合理边界。

采纳：

- AI SDK 只提交 typed task/action/tool request；
- Kernel 为 Model、Tool、Memory、Effect 建立独立 manager；
- 所有 manager 统一输出 request id、queue time、execution time、terminal state；
- resource scheduler 只调度，不承担业务语义。

不直接照搬：

- 实验性线程队列不足以承担车辆动作；
- Tool manager 的进程启动和名称冲突处理不等于签名验证、权限治理和健康管理；
- 车载请求必须持久化、幂等、可审计并经过 Safety State。

### 4.2 Cerebrum：Kernel 与 Agent SDK 解耦

Cerebrum 将 Agent SDK、LLM/Tool/Memory/Storage API、MCP connection pool 和 Agent/Tool package manager 分开。其价值是让开发者编写 Agent 时面对稳定 SDK，而不是依赖 Kernel 内部类。

采纳：

- `central-brain-sdk` 只保留跨进程 DTO、client facade 和 callback；
- Runtime 内部 graph、policy、effect 实现不得泄漏到 SDK；
- Skill 以 manifest + schema + signature + minimum runtime version 交付；
- OEM/vendor adapter 是受控 package，不是 HMI 代码的一部分。

不直接照搬：

- 不允许运行时从公网社区自动下载 Agent/Tool；
- 黑盒 Android 平台首阶段只支持 built-in/allowlisted skill；
- 动态代码加载必须等待 signer、SELinux/MDM 和 OEM 更新策略明确。

### 4.3 LangGraph：持久图、interrupt 和 checkpoint

LangGraph 的 runner 把图节点作为任务执行并向 checkpointer 提交 writes、errors 和 interrupts；`RetryPolicy`、`TimeoutPolicy` 和 resumable interrupt 将失败恢复与人工确认纳入运行时。这个机制直接对应“我累了”场景中的计划、确认、动作和部分失败。

采纳：

- 每个场景编译为有版本的有向图，不把自然语言计划直接执行；
- 每个 node 有 input/output schema、timeout、retry、idempotency、compensation；
- approval node 进入 `WAITING_FOR_CONFIRMATION`，重启后仍可恢复；
- checkpoint 在 node commit 后原子保存；
- durability mode 明确 `SYNC`、`ASYNC`、`ON_EXIT`，车辆 Effect 只能使用 `SYNC`。

安全补充：

LangGraph 曾出现 checkpoint deserialization 安全公告 [GHSA-g48c-2wqr-h844](https://github.com/langchain-ai/langgraph/security/advisories/GHSA-g48c-2wqr-h844)。Central Brain 不保存任意 Java/Python 对象，只允许版本化 protobuf/JSON 基元、固定 enum 和 allowlist type；未知 type/version 一律拒绝恢复。

### 4.4 AutoGen：typed message 与 direct/pub-sub 语义

AutoGen runtime 区分 direct send 和 publish，支持 agent factory、subscription、serializer 和 runtime state。该设计适合 Uni Info Bus 的 Action/Event/Service 边界。

采纳：

- 所有跨模块通信使用 `Envelope<T>`，不传 implementation object；
- direct request 用于有唯一责任方的 Tool/Effect/Service；
- pub-sub 用于 Context/Event/Observation；
- serializer registry 绑定 `type + schemaVersion`；
- agent instance 由 factory 创建，状态保存与消息投递分离。

不直接照搬：

- AutoGen 单线程 runtime 源码注明 subscription persistence 尚不完整；Central Brain 必须将 subscription、cursor 和 replay state 一起持久化；
- Agent message 不可绕过 Runtime & Governance 直达 adapter。

### 4.5 OpenHands SDK：Action/Observation 与 fail-closed 风险确认

OpenHands SDK 把 Event 建模为不可变 typed event，区分 Action、Observation、Message；ConversationState 包含 `IDLE/RUNNING/PAUSED/WAITING_FOR_CONFIRMATION/FINISHED/ERROR/STUCK`，并组合 security analyzer、confirmation policy、tool registry 和 secret registry。风险分析失败默认高风险是值得采纳的安全原则。

采纳：

- HMI 和审计使用同一 Action/Observation 事件树；
- planner 产生 `ActionProposed`，policy 产生 `ActionAuthorized/Rejected`，adapter 产生 `ActionApplied/Failed`；
- risk classifier 异常、超时或返回未知值时按 `HIGH`；
- `WAITING_FOR_CONFIRMATION` 是持久状态，不是临时 dialog；
- Tool resolve 后还必须执行 usability/health/capability 检查。

车载强化：

- 通用“确认后执行”仍不够；行驶中放平驾驶席属于安全互锁禁止项，即使用户确认也不得执行；
- secret registry 不得存放车辆原始标识、生产签名材料或未审查日志。

### 4.6 Letta：分层记忆和确定性工具规则

Letta 使用有 label、limit、read-only 和 metadata 的 memory block，并对上下文 token budget 计数；tool rules 支持 init、child、conditional、terminal、required-before-exit 和 requires-approval。

采纳：

- `WorkingMemory`：单次场景短期状态，随 session 关闭清理；
- `ProfileMemory`：温度、座椅等偏好，必须用户同意、可查看/删除；
- `EpisodicMemory`：场景摘要和结果，不保存原始连续车身数据；
- 每层都有 byte/token/item/TTL 上限；
- Tool graph 由 manifest rules 限制，模型只能在允许集合中选择；
- 场景退出前必须执行 required verification node。

不直接照搬：

- 不把完整会话或车辆轨迹永久放入长期记忆；
- 不允许模型修改 read-only safety/context block；
- 量产前必须确定 consent owner、retention policy 和 export/delete API。

## 5. 车载行业设计输入

### 5.1 COVESA VSS：统一车辆语义

VSS 的 HVAC 和 Seat 规范已经覆盖舱温、目标温度、风量、内循环、除霜、座椅占用/安全带、加热、通风、按摩、位置和靠背倾角。Central Brain 应采用 VSS 风格 canonical path，避免 planner 直接绑定某车型 property id。

示例内部路径：

```text
Vehicle.Cabin.HVAC.Station.Row1.Driver.Temperature
Vehicle.Cabin.Seat.Row1.Driver.Backrest.Recline
Vehicle.Cabin.Seat.Row1.Driver.IsOccupied
Vehicle.Cabin.Seat.Row1.Driver.Occupant.Identifier
```

这些是内部 canonical schema；目标 AAOS adapter 再映射为标准 `VehiclePropertyIds` 或 vendor contract。没有公开映射时保持 `UNAVAILABLE`，不得猜测 property id。

### 5.2 Android Automotive：CarPropertyManager 和安全权限

Android 官方 `CarPropertyManager` 提供 property get/set/subscribe 和异步 set 结果；`VehiclePropertyIds` 定义 HVAC、座椅温度/通风/移动等属性，但写入通常依赖 privileged/signature 权限。参考：

- [CarPropertyManager](https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager)
- [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds)
- [AAOS VHAL overview](https://source.android.com/docs/automotive/vhal)
- [VHAL vendor properties](https://source.android.com/docs/automotive/vhal/special-properties)

结论：

- 黑盒普通 APK 可以编写 `AaosCarPropertyEffectAdapter`，但激活取决于目标是否公开 `android.car` API、property list 和权限；
- 优先使用标准 system property；vendor property 仅在标准属性确实不能表达且 OEM 提供正式 contract 时使用；
- 不修改已刷机 framework/VHAL，不遍历私有 device node；
- `setProperty` 成功只代表调用被接受，仍须通过 callback/readback/车辆反馈验证 applied state。

### 5.3 驾驶分心与状态自适应 UI

AAOS Car UX Restrictions 区分 parked、idling、moving，moving 状态具有最严格限制；车载 HMI 必须按驾驶状态限制文本、步骤和交互。参考：

- [Car UX Restrictions](https://source.android.com/docs/automotive/driver_distraction/car_uxr)
- [Driver distraction guidelines](https://source.android.com/docs/automotive/driver_distraction/guidelines)
- [Design for Driving](https://developers.google.com/cars/design/design-foundations)

Central Brain 的产品约束：

- `MOVING`：大按钮、单层操作、简短状态，不显示长模型文本，不允许复杂参数编辑；
- `PARKED`：可以显示计划详情、参数、记忆和日志；
- 驾驶状态未知时按 `MOVING_RESTRICTED` 处理；
- UI 不负责决定动作安全性，Runtime policy 必须独立复核。

### 5.4 uProtocol：跨设备消息和 Digital Twin

uProtocol L1/L2 定义 transport-agnostic message、pub/sub 和 RPC，L3 uTwin 提供设备本地 last-known-state；规范强调 send success 不表示目标已经处理消息。这一点决定 Effect 的状态不能只有 success/failure。

Central Brain Effect lifecycle：

```text
PROPOSED -> AUTHORIZED -> PREPARED -> DISPATCHED -> DELIVERED
         -> APPLIED -> VERIFIED
```

任何状态都可进入 `REJECTED`、`FAILED_RETRYABLE`、`FAILED_TERMINAL`、`UNKNOWN` 或 `COMPENSATED`。HMI 只有在 `VERIFIED` 后才能显示“已完成”；`DISPATCHED` 只能显示“正在执行”。

参考：[uProtocol overview](https://uprotocol.org/) 和 [Eclipse uProtocol specifications](https://github.com/eclipse-uprotocol/up-spec)。

### 5.5 SOAFEE：云原生方法的车载边界

SOAFEE 强调可移植、云原生开发方法和车载运行环境之间的标准化。Central Brain 采纳其“开发环境与目标部署解耦、接口和制品可验证”的方法，但不在本项目开发 Hypervisor，也不假设黑盒 Android 支持容器。

参考：[SOAFEE Architecture](https://gitlab.com/soafee/architecture)。

### 5.6 MCP 和 A2A：外部工具/Agent 互操作

MCP 提供 client/host/server 资源与工具协议，A2A 提供跨 Agent task/message/artifact 协作。两者适合作为受控外部 binding，而不是 AIOS Kernel 内部总线。

- [MCP specification](https://modelcontextprotocol.io/specification/2024-11-05/basic)
- [A2A specification](https://github.com/a2aproject/A2A/blob/main/docs/specification.md)

约束：

- MCP/A2A 请求必须转换为 Uni Info Bus Tool/Action/Service envelope；
- 外部 server 不获得车辆执行权限，只能声明 capability；
- 网络不可用不影响本地安全场景；
- 不在第二阶段首批实现公网动态发现。

## 6. 对比矩阵

| 能力 | 当前 Central Brain | 参考项目成熟模式 | Stage 2 目标 |
| --- | --- | --- | --- |
| Runtime API | typed AIDL task/callback | AutoGen typed message/runtime | 增加 session/event/plan/effect typed API |
| Agent 执行 | task admission 骨架 | LangGraph durable graph | 有版本图、checkpoint、interrupt、retry、compensation |
| 资源调度 | JobSupervisor + model scheduler | AIOS resource managers | Model/Tool/Memory/Effect 独立队列和统一观测 |
| Tool/Skill | bounded built-in skill | Cerebrum package、OpenHands registry、Letta rules | 签名 manifest、规则、健康、版本、allowlist executor |
| Memory | bounded lifecycle | Letta block/context budget | working/profile/episodic、consent、TTL、删除 |
| Event | bounded event + cursor schema | AutoGen pub-sub、OpenHands event tree | 持久 action/observation tree、订阅与 backpressure |
| Policy | capability + action governance | OpenHands risk/confirmation | 驾驶态、安全互锁、fail-closed risk、持久 approval |
| Effect | durable outbox/adapter gate | uProtocol delivery semantics | canonical vehicle intents、Digital Twin、applied/verified、补偿 |
| HMI | 场景按钮 + 文本回复 | conversation/task state | 计划、进度、确认、部分失败、撤销、驾驶态 UI |
| Vehicle schema | VSS-style read catalog mock | COVESA VSS/AAOS properties | VSS canonical model + mock adapter + AAOS mapping gate |
| Model | test provider/router | AIOS model manager | local/stub/NPU/cloud profiles、budget、fallback、evaluation |
| Deployment | ordinary Android APKs | SOAFEE portable artifacts | 黑盒 Android app-layer deployment，真实接口按 gate 激活 |

## 7. 必须新增的架构能力

优先级按“先让演示体现 AIOS，再逐步替换真实平台依赖”排列：

1. `Scenario Orchestrator`：意图、Context、场景模板、计划图；
2. `Durable Agent Graph Runtime`：节点执行、checkpoint、interrupt、retry、timeout、补偿；
3. `Vehicle Digital Twin`：canonical signal、freshness、quality、desired/reported state；
4. `Effect Coordinator`：多设备动作、幂等、依赖、并行、验证、部分失败；
5. `Simulated Vehicle Adapter`：在没有 VHAL 权限时提供可观察的 HVAC/Seat/Nav/Media 仿真；
6. `Session + Event Tree`：用户请求、计划、动作、观察、回复和恢复；
7. `Layered Memory`：working/profile/episodic 及 consent/retention；
8. `Tool/Skill Platform`：manifest、schema、signature、rule、health、executor；
9. `Context Trigger Engine`：受策略控制的主动场景，而不是只响应按钮；
10. `Model Router and Evaluation`：stub/local NPU/cloud 路由、预算、质量、隐私；
11. `Automotive HMI`：驾驶态自适应、进度、确认、撤销和可解释结果；
12. `Production Adapter Gates`：AAOS CarProperty/vendor service/NPU 的 owner、permission、ABI、smoke 和 rollback。

## 8. 不采纳或延后能力

| 能力 | 结论 | 原因 |
| --- | --- | --- |
| 模型直接调用车控 API | 禁止 | 绕过 Action/Policy/Safety/Effect 审计 |
| 公网自动下载 Agent/Tool | 延后 | 黑盒设备 signer、MDM、供应链和更新策略未知 |
| 任意对象 checkpoint 反序列化 | 禁止 | 反序列化攻击和版本漂移 |
| 行驶中确认后放平驾驶席 | 禁止 | 确认不能覆盖安全互锁 |
| 猜测 vendor property/device node/ioctl | 禁止 | 无公开 SDK，可能损坏系统或绕过权限 |
| 在 APK 内实现 VHAL/Driver | 禁止 | 黑盒 Android 普通应用无该所有权；只实现 adapter contract |
| 开发 Hypervisor/虚拟机 | 不在范围 | 用户明确排除；只保留接口约束 |
| 首阶段多 Agent 自由协商 | 延后 | 先建立确定性场景图、权限和 Effect 闭环 |
| 首阶段公网 MCP/A2A | 延后 | 本地安全场景优先，外部网络与凭据策略未定 |

## 9. 总体结论

Central Brain 已具备一部分成熟项目通常缺失的车载治理基础：可信 Binder 身份、capability、持久 approval/effect/outbox、production adapter fail-closed 和 Android 13 真机恢复验证。其主要不足不是“再接一个大模型”，而是缺少把 Context、场景、计划图、Tool、Effect、车辆反馈和 HMI 串成可恢复闭环的 AIOS 执行架构。

Stage 2 应以确定性场景图和 Digital Twin 为主线：模型只参与意图补全、参数建议和自然语言总结；所有车辆动作由版本化模板、Policy/Safety、Effect Coordinator 和 adapter 执行。这样既能在当前无 NPU/VHAL 的黑盒 Android 上用仿真展示 AIOS，也能在公开 vendor contract 到位后逐个替换 adapter，而不重写产品层与 Kernel。
