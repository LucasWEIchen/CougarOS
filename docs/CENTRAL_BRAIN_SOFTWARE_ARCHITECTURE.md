# CougarOS Central Brain 生产软件架构文档

版本：2.1
状态：生产架构权威基线
适用平台：Android 13 座舱域控制器
更新日期：2026-07-27

`production_document_scope=true`
`production_architecture_document=true`
`production_ready=false`
`target_hardware_validated=false`

## 1. 架构目标与边界

Central Brain 将“用户意图”转换为“经过治理、可追踪、可核验的车辆动作”。架构的核心不是聊天界面，
而是把感知、模型、计划、工具、执行器和 HMI 放入同一条受控运行链路。

生产软件运行在不可修改系统源码的 Android 13 座舱域控制器上，以普通 APK、独立 Runtime Service、
Java SDK 和 Native Library 交付。外部 AI 算力基座通过车载以太网访问；车辆信号与末端执行器通过
OEM/Vendor 提供的公开或授权接口接入。缺少接口时保持空 Adapter 和失败关闭状态，不推断 Driver/HAL。

### 1.1 架构原则

1. **意图优先**：HMI 输入目标，不要求用户逐个操作执行器。
2. **模型无执行权**：模型只能产生候选结构化计划。
3. **确定性治理**：白名单、策略、审批和车辆状态决定动作能否执行。
4. **状态可恢复**：Session、Plan、Event、Checkpoint 和 Effect 均有持久状态。
5. **desired/reported 分离**：请求成功不等于车辆已达到目标状态。
6. **失败关闭**：未知身份、未知能力、陈旧状态、非法模型输出和缺失 Adapter 一律拒绝。
7. **接口先行**：跨进程、Native、车辆和模型边界均有稳定版本合同。
8. **平台隔离**：业务编排不依赖具体车辆属性号、NPU ABI 或模型后端。

## 2. 系统上下文

```mermaid
flowchart LR
    Driver["驾驶员 / 乘员"]
    Cabin["座舱传感输入<br/>语音、图像、车辆状态"]
    Brain["Central Brain<br/>Android 13"]
    AI["外部 AI 算力基座<br/>语言/视觉语言模型服务"]
    Vehicle["车辆能力域<br/>HVAC、座椅、导航、媒体、购物"]
    HMI["Client2 座舱 HMI"]
    OEM["OEM / Vendor 平台接口"]

    Driver -->|"场景化目标"| HMI
    Cabin -->|"Context"| Brain
    HMI -->|"Session 请求"| Brain
    Brain <-->|"有界模型请求 / 流式结果"| AI
    Brain -->|"白名单 Effect"| OEM
    OEM <--> Vehicle
    Brain -->|"计划、进度、核验"| HMI
    HMI --> Driver
```

系统信任边界如下：

- HMI 输入是用户意图，不是已授权动作。
- 模型输出是不可信候选数据。
- OEM/Vendor Adapter 是车辆能力的唯一出口。
- 车辆 reported state 是 Effect 完成的主要依据。
- HMI 只投影 Runtime 状态，不拥有编排真相。

## 3. 生产部署架构

```mermaid
flowchart TB
    subgraph Android["Android 13 座舱域控制器"]
        subgraph HmiProcess["Client2 进程"]
            UI["Voice-first HMI"]
            Media["图像采集与缩略图"]
            Timeline["执行链路与审批 UI"]
            Client["Central Brain SDK Client"]
        end

        subgraph RuntimeProcess["Central Brain Runtime 进程"]
            Binder["AIDL Service Endpoints"]
            Orchestrator["Orchestration Runtime"]
            Governance["Governance / Safety"]
            ModelRouter["Model Router"]
            Effect["Effect Coordinator"]
            Store["Room Database"]
        end

        subgraph NativeProcess["Native Runtime"]
            JNI["JNI Bridge"]
            CABI["Stable C ABI"]
        end
    end

    AIBase["外部 AI 算力基座"]
    VehicleApi["OEM / Vendor Vehicle API"]
    NpuApi["Vendor NPU Runtime"]

    UI --> Client
    Media --> Client
    Timeline <--> Client
    Client <-->|"Binder"| Binder
    Binder --> Orchestrator
    Orchestrator --> Governance
    Orchestrator --> ModelRouter
    Orchestrator --> Effect
    Orchestrator <--> Store
    ModelRouter <-->|"Ethernet"| AIBase
    Effect <-->|"授权接口"| VehicleApi
    ModelRouter --> JNI --> CABI
    CABI -. "厂商扩展点" .-> NpuApi
```

### 3.1 进程职责

| 进程/组件 | 职责 | 禁止事项 |
| --- | --- | --- |
| Client2 | 输入、图片预览、链路展示、审批交互 | 不直接调用模型或车辆 Adapter |
| SDK | Binder 连接、DTO 校验、协议协商、重连 | 不保存 Runtime 权威状态 |
| Runtime Service | 编排、治理、持久化、模型路由、Effect | 不依赖 HMI 生命周期 |
| Native Runtime | 资源槽位、健康、厂商 ABI 入口 | 不拥有产品策略 |
| External AI | 推理和流式输出 | 不直接访问车辆执行器 |
| Vehicle Adapter | 能力映射、执行和回读 | 不接受未治理的自由文本 |

## 4. 逻辑分层

```mermaid
flowchart TB
    L1["应用体验层<br/>Intent、Multimodal、Timeline、Approval"]
    L2["SDK 与跨进程合同层<br/>Session、Plan、Event、Effect AIDL"]
    L3["语义与编排层<br/>Context、Scenario、Graph、Tool、Memory"]
    L4["治理层<br/>Identity、Capability、Safety、Consent、Audit"]
    L5["模型与执行协调层<br/>Model Router、Effect Coordinator、Reconciliation"]
    L6["平台适配层<br/>Vehicle Adapter、Model Provider、Native ABI"]
    L7["外部平台层<br/>AI 基座、OEM/Vendor 服务、NPU Runtime"]

    L1 --> L2 --> L3 --> L4 --> L5 --> L6 --> L7
    L7 -. "reported state / health" .-> L5
    L5 -. "typed event" .-> L3
    L3 -. "projection" .-> L2
    L2 -. "UI state" .-> L1
```

依赖只允许向下，状态反馈通过 typed Event 向上投影。平台适配层不得反向依赖 HMI 或场景文案。

## 5. 核心模块

### 5.1 Client2 座舱 HMI

HMI 由任务入口、输入/输出流、执行链路、审批条和执行器反馈组成。生产交互遵循：

- 首屏只保留场景任务入口，例如“我累了”“处理一下”。
- 输入和模型输出按到达顺序滚动显示。
- 图片以缩略图显示，点击后居中预览，点击外部区域退出。
- Plan 节点、Effect、审批和 readback 形成一条连续链路。
- HVAC 温度、座椅角度等 UI 状态来自 Runtime 投影，不使用固定结果文本。
- 1920x1080 下 HMI 不改变车模画布的原始比例、清晰度和触摸坐标。

### 5.2 Central Brain SDK

SDK 是 HMI 与 Runtime 的唯一正式入口，包括：

- `SessionClient`：打开、查询、取消和观察 Session。
- `OrchestrationClient`：启动场景、读取快照、提交审批、撤销和取消。
- Event V2：游标、ACK、回放和 oneway callback。
- 协议协商：interface version/hash、capability 和 schema version。
- 生命周期恢复：Binder death、重连、Session 恢复、事件续传。

### 5.3 Session 与持久化

Session 是端到端所有权边界。每个 Session 绑定调用方、场景、截止时间、活动 Plan revision 和状态。
Room 保存 Session、Plan、节点、事件、游标、检查点、审批、Effect、回读和审计摘要。

```mermaid
stateDiagram-v2
    [*] --> Created
    Created --> Running: Plan accepted
    Running --> WaitingApproval: approval node
    WaitingApproval --> Running: approved
    WaitingApproval --> Cancelled: rejected / expired
    Running --> Succeeded: all required effects verified
    Running --> Partial: optional effect failed
    Running --> Failed: required effect failed
    Running --> Cancelled: cancel accepted
    Partial --> Compensating: policy requests compensation
    Failed --> Compensating: rollback available
    Compensating --> Compensated
    Succeeded --> [*]
    Cancelled --> [*]
    Compensated --> [*]
```

### 5.4 Context 与 Vehicle Digital Twin

Context 层把车辆和座舱输入转换为 canonical signal：

`path + area + typed value + timestamp + quality + source + schemaVersion`

Digital Twin 分离：

- `reported`：从可信车辆接口读取的实际状态。
- `desired`：通过治理后准备执行的目标状态。
- `freshness`：状态是否仍可用于安全决策。
- `revision`：防止旧写入覆盖新状态。

### 5.5 Scenario 与 Plan Compiler

Scenario Manifest 描述允许的场景、节点类型、能力、审批和补偿规则。Resolver 根据 Context 确定场景，
Compiler 生成不可变 DAG。模型可以填充参数和提出候选分支，但不能创建未知节点类型或能力。

```mermaid
flowchart LR
    Input["用户目标 + Context"]
    Candidate["模型候选意图"]
    Schema["Structured Output 校验"]
    Resolve["ScenarioResolver"]
    Manifest["签名 Scenario Manifest"]
    Compile["ScenarioPlanCompiler"]
    Validate["PlanGraphValidator"]
    Plan["Immutable ScenarioPlan"]

    Input --> Candidate --> Schema --> Resolve
    Manifest --> Resolve
    Resolve --> Compile --> Validate --> Plan
    Schema -. "非法" .-> Reject["拒绝并发布错误事件"]
    Validate -. "未知节点/环/越权" .-> Reject
```

### 5.6 Agent Graph Runtime

Graph Runtime 按依赖和策略执行 typed node。节点类型包括模型、规则、Tool、审批、Effect、等待、核验和补偿。
每次状态迁移先写检查点，再发布事件。重启后由 `GraphRestartReconciler` 根据持久状态恢复，不重复提交
已经确认的外部动作。

### 5.7 Governance

Governance 位于模型和 Effect 之间，是唯一动作准入权威：

```mermaid
flowchart LR
    Intent["EffectIntent"]
    Identity["Caller Identity"]
    Capability["Capability Policy"]
    VehicleState["Trusted Vehicle State"]
    Safety["Driver Safety Policy"]
    Consent["Consent / Approval"]
    Decision{"ALLOW?"}
    Execute["Effect Coordinator"]
    Deny["Rejected Event"]

    Intent --> Identity --> Capability --> VehicleState --> Safety --> Consent --> Decision
    Decision -->|Yes| Execute
    Decision -->|No / Unknown| Deny
```

安全策略使用确定性代码，不接受模型生成的策略文本。行驶状态未知等价于受限状态。

### 5.8 Tool 与 Skill

Tool 是受控能力调用，Skill 是可验证的复合行为包。Tool Registry 只登记 Manifest、Schema、版本、
健康和 capability；RuleSolver 将模型候选与系统规则求交集；Executor 只执行内建或已验证实现。

Tool 调用与 Vehicle Effect 必须分开：搜索商品可以是 Tool，提交订单和启动车辆导航必须经过独立审批。

### 5.9 Memory

Memory 分为：

- Working Memory：当前 Session 的短期状态。
- Profile Memory：用户偏好，必须具备同意、授权和加密 owner。
- Episodic Memory：有界摘要，不保存原始输入和模型全文。
- Context Budget：按安全优先级为模型分配上下文预算。

Memory 不是审计日志。审计只保存摘要、状态码和关联 ID。

### 5.10 Event Broker

Event Broker 传递 Observation、Action、Runtime 和 Message 事件。生产事件必须有：

- 单调 sequence 和稳定 cursor。
- owner/session/topic 过滤。
- 有界 page 和 subscriber queue。
- ACK 与重放。
- 背压策略和丢弃计数。
- 终态事件不可重复发布。

### 5.11 Model Runtime

Model Runtime 由 `ModelProviderRegistry`、`PolicyAwareModelRouter`、`InferenceResourceScheduler` 和
Provider 构成。当前生产目标是 `DIRECT_MODEL_SERVICE`：Central Brain 自己持有会话上下文、Prompt、
工具编排、结构化输出、重试、取消和 deadline，并通过协议 Adapter 直接访问基座模型服务。

```mermaid
flowchart LR
    Request["ModelRequest"]
    Admission["Resource Admission"]
    Registry["Provider Registry"]
    Router["Policy-aware Router"]
    Direct["Direct Model Service Provider"]
    Session["AIOS Session / Context / Prompt"]
    Protocol["Model Protocol Adapter<br/>Ollama / OpenAI-compatible / Vendor"]
    Base["Base Model Service"]
    Vendor["Vendor NPU Provider"]
    Stream["Stream Observer"]
    Validate["Structured Output Validator"]
    Tools["Tool Resolver / Governance"]

    Request --> Admission --> Router
    Registry --> Router
    Router --> Direct
    Router -. "厂商接口就绪后" .-> Vendor
    Session --> Direct --> Protocol --> Base
    Base --> Protocol --> Stream --> Validate
    Validate --> Tools
    Vendor --> Stream
```

直接模型服务只负责受控推理，不拥有 Tool 或 Effect 权限。模型输出必须返回 Central Brain 定义的
provider-neutral Schema，再由 Scenario、Governance 和 Tool Runtime 决定后续动作。Router 依据 assurance、
健康、文字/图像模态、并发、热状态和场景策略选择 Provider。Direct Model Provider 未达到 production
assurance 前，全局 `production_ready` 保持 false。

### 5.12 Effect 与 Vehicle Adapter

Effect 流程分为 prepare、dispatch、readback、reconcile：

```mermaid
sequenceDiagram
    participant G as Graph Runtime
    participant V as Governance
    participant E as EffectCoordinator
    participant A as VehicleAdapter
    participant T as DigitalTwin
    participant B as EventBroker

    G->>V: authorize(EffectIntent, Context)
    V-->>G: ALLOW / APPROVAL_REQUIRED / DENY
    G->>E: execute(EffectBatch)
    E->>A: prepare(intent)
    A-->>E: PreparedMaterial / Rejected
    E->>A: dispatch(prepared)
    A-->>E: accepted / unknown / failed
    E->>A: readback(target)
    A-->>E: EffectObservation
    E->>T: update desired/reported
    E->>B: publish typed result
```

Adapter 不可用时返回明确错误。UI 动画只能投影状态，不能作为 readback 或量产证据。

### 5.13 Native Runtime

Native Runtime 提供稳定 C ABI：

- `cb_runtime_create_v1`
- `cb_runtime_get_health_v1`
- `cb_runtime_acquire_slot_v1`
- `cb_runtime_release_slot_v1`
- `cb_runtime_destroy_v1`

该层当前负责资源槽位和健康合同。厂商 NPU、共享内存、DMA、模型生命周期和故障恢复在 Vendor ABI
确认后通过扩展 Adapter 实现，不改变上层 Java 业务合同。

## 6. 关键业务流程

### 6.1 语音场景流程

```mermaid
sequenceDiagram
    actor User as 驾驶员
    participant H as Client2 HMI
    participant S as SDK
    participant R as Runtime
    participant M as Model Provider
    participant G as Governance
    participant A as Vehicle Adapter

    User->>H: “我有些疲惫”
    H->>S: openSession + startScenario
    S->>R: OrchestrationStartRequest
    R-->>H: INPUT_ACCEPTED
    R->>M: cockpit context + text
    M-->>R: streamed candidate plan
    R-->>H: MODEL_OUTPUT / PLAN_READY
    R->>G: authorize HVAC + seat effects
    G-->>R: decision
    R->>A: governed effects
    A-->>R: readback
    R-->>H: EXECUTING / VERIFIED / RESULT
```

### 6.2 多模态座舱流程

```mermaid
sequenceDiagram
    actor User as 用户
    participant H as Client2 HMI
    participant R as Runtime
    participant M as Multimodal Provider
    participant P as Plan Compiler
    participant T as Tool Runtime
    participant G as Governance

    User->>H: “处理一下” + 当前座舱图像
    H->>R: bounded text/image request
    R-->>H: 输入文字与图片缩略图
    R->>M: cockpit prompt + multimodal content
    M-->>R: occupants + intent candidates
    R->>P: evidence-bound candidate
    P-->>R: shopping / route DAG
    R->>T: search product and merchant
    T-->>R: bounded candidates
    R->>G: purchase and navigation approvals
    G-->>H: independent approval prompts
    H-->>R: explicit user decisions
    R-->>H: execution and final result
```

模型识别到乘员和饮水意图后，系统只能准备购物与路径候选；购买提交和导航启动是两个独立确认点。

### 6.3 审批与撤销

```mermaid
stateDiagram-v2
    [*] --> Proposed
    Proposed --> Waiting: policy requires approval
    Proposed --> Rejected: policy denies
    Waiting --> Approved: valid owner response
    Waiting --> Rejected: reject
    Waiting --> Expired: deadline reached
    Approved --> Executing
    Executing --> Verified
    Executing --> Unknown
    Verified --> UndoAvailable: reversible effect
    UndoAvailable --> Compensating: user requests undo
    Compensating --> Compensated
```

审批必须绑定 owner、Session、Plan revision、Effect digest 和 deadline，旧审批不能作用于新计划。

### 6.4 故障与恢复

```mermaid
flowchart TB
    Failure["Binder / 进程 / 网络 / Provider 故障"]
    Persist["读取持久 Session、Checkpoint、Outbox"]
    Classify{"动作是否已确认提交?"}
    Retry["按策略有限重试"]
    Readback["查询 reported state"]
    Reconcile["Reconciliation"]
    Compensate["补偿 / 撤销"]
    Terminal["发布唯一终态"]

    Failure --> Persist --> Classify
    Classify -->|否| Retry
    Classify -->|是或未知| Readback
    Readback --> Reconcile
    Reconcile -->|达到目标| Terminal
    Reconcile -->|可补偿| Compensate --> Terminal
    Reconcile -->|未知| Terminal
    Retry --> Terminal
```

## 7. 数据架构

```mermaid
erDiagram
    SESSION ||--o{ PLAN : owns
    PLAN ||--o{ PLAN_NODE : contains
    SESSION ||--o{ RUNTIME_EVENT : emits
    SESSION ||--o{ TASK_CHECKPOINT : checkpoints
    PLAN_NODE ||--o{ APPROVAL_REQUEST : may_require
    PLAN_NODE ||--o{ PENDING_EFFECT : produces
    PENDING_EFFECT ||--o{ EFFECT_OBSERVATION : verifies
    PENDING_EFFECT ||--o{ OUTBOX : dispatches
    SESSION ||--o{ AUDIT_EVENT : summarizes
    SESSION ||--o{ EVENT_CURSOR : acknowledges
```

### 7.1 权威状态

| 数据 | 权威 owner | 持久化 |
| --- | --- | --- |
| Session/Plan/Graph | Runtime Service | Room |
| HMI 展示状态 | Client2 reducer | 可由 Runtime 重建 |
| Vehicle reported state | Vehicle Adapter / Digital Twin | 有界快照 |
| Model health | Model Provider Registry | 进程内快照 |
| Approval | Governance + durable repository | Room |
| Effect dispatch | Effect outbox | Room |
| 审计 | Audit projection | 只保存摘要 |

## 8. 安全、隐私与发布架构

```mermaid
flowchart LR
    App["调用方 APK"]
    Binder["Signature Permission + UID"]
    Runtime["Runtime Authority"]
    Model["Untrusted Model Output"]
    Schema["Schema / Allowlist"]
    Policy["Governance"]
    Adapter["Signed Adapter"]
    Vehicle["Vehicle Domain"]

    App --> Binder --> Runtime
    Runtime --> Model --> Schema --> Policy --> Adapter --> Vehicle
    Vehicle -. "readback" .-> Adapter
```

- Binder 使用签名权限、UID 和 owner fingerprint。
- 模型输出先经过结构化校验，再进入 Scenario/Tool/Effect。
- Tool 和 Adapter 使用构建时登记、版本和摘要。
- 驾驶状态、安全联锁和审批均由确定性策略执行。
- 原始输入不进入审计；日志中禁止凭据、图像、模型全文和车辆原始载荷。
- 发布由 signer、数据库兼容、候选版本、安装策略和回滚策略共同准入。

## 9. 可用性与性能

```mermaid
flowchart LR
    HMI["HMI lifecycle"]
    SDK["SDK reconnect"]
    Binder["Binder service"]
    Session["Durable Session"]
    Graph["Checkpointed Graph"]
    Outbox["Effect Outbox"]
    Adapter["Adapter readback"]

    HMI --> SDK --> Binder --> Session --> Graph --> Outbox --> Adapter
    Adapter -. "reconcile" .-> Outbox
    Session -. "rehydrate" .-> Binder
    Binder -. "resubscribe" .-> SDK
```

性能由分层预算控制：HMI 本地响应、Binder 接收、模型首 token、Plan 编译、Effect prepare/dispatch、
readback 和最终投影分别测量。任何目标值只有在量产硬件和真实 Provider/Adapter 上测得后才能成为
发布证据。

## 10. 生产集成缺口

| 缺口 | 架构处理 |
| --- | --- |
| 真实 Vehicle property/service 未确认 | 保留 canonical Adapter；运行时返回 unavailable |
| NPU ABI 未确认 | 保留 `ModelProvider` 与 C ABI；Vendor Provider 不注册 |
| Direct Model Provider 尚未进入 release | 固定 catalog 已切换但路由失败关闭，直到协议、流式、取消和目标资格完成 |
| 历史 OpenClaw 代码仍在迁移期 | build profile 已不可选择；不登记到新 Provider catalog，不允许 Router 选择，分阶段删除历史执行器 |
| 可信车速/档位/DMS 未接入 | 高风险动作失败关闭 |
| 生产签名/OTA/回滚未确认 | Release Admission 拒绝发布 |
| 隐私 owner 策略未批准 | Profile/Episodic 写入和导出关闭 |

## 11. 模块与需求映射

| 模块 | 主要 Req ID |
| --- | --- |
| Client2 HMI | `APP-001..005`, `S2-UX-*`, `S2-HMI-*`, `P4-*` |
| SDK/AIDL | `S2-SES-001`, `S2-EVT-001`, `P1-*` |
| Context/Digital Twin | `S2-CTX-001`, `S2-TWN-001`, `P2-W01..07` |
| Scenario/Graph | `S2-SCN-*`, `S2-GRF-001`, `P3-*` |
| Governance/Effect | `S2-SAF-*`, `S2-EFF-001`, `P3-W05..09` |
| Tool/Skill/Memory | `S2-TOL-*`, `S2-SKL-001`, `S2-MEM-*`, `P5-*` |
| Event/Proactive | `S2-EVT-001`, `P6-*` |
| Model Runtime | `S2-MDL-*`, `P7-*` |
| Vehicle/NPU Adapter | `S2-ADP-*`, `P8-*` |
| Quality/Release | `S2-OBS-001`, `S2-REL-001`, `P9-*` |

实现类、AIDL、C ABI、数据库实体和错误语义见
[软件开发文档](CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md)。全量状态见
[需求文档](CENTRAL_BRAIN_REQUIREMENTS.md)。
