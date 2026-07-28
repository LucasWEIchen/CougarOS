# CougarOS Central Brain 生产软件开发文档

版本：2.1
状态：生产软件详设与接口权威基线
适用平台：Android 13 座舱域控制器
更新日期：2026-07-28

`production_document_scope=true`
`production_development_document=true`
`production_ready=false`
`target_hardware_validated=false`

## 1. 使用说明

本文面向负责 Central Brain Android 应用、Runtime、Native、模型和车辆适配的开发人员，定义模块职责、
源码边界、状态机、并发、持久化、错误处理和对外接口。需求及状态以
[需求文档](CENTRAL_BRAIN_REQUIREMENTS.md) 为准；跨模块关系以
[软件架构文档](CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md) 为准。

正式实现必须遵循以下约束：

- 只在应用、独立 Service、SDK 和 Native Library 范围内开发。
- 未获得 OEM/Vendor 合同前，不修改或推断 Android Framework、Kernel、Driver、HAL。
- 所有模型输出都是提案；只有 Governance 可以授予动作准入。
- 生产源集不得注册非生产 Provider、无权威数据源的 Adapter 或固定成功返回。
- 真实执行结果必须来自 Adapter 回读或明确的 unknown/failed 状态。

## 2. 源码结构与模块归属

| 路径 | 模块 | 生产职责 |
| --- | --- | --- |
| `central-brain/android-runtime/central-brain-sdk` | SDK/AIDL | 对 HMI 暴露稳定 Java 与 Binder 合同 |
| `central-brain/android-runtime/runtime-service` | Runtime | Session、Graph、Governance、Model、Effect、Persistence |
| `central-brain/android-runtime/native-runtime` | Native | JNI、稳定 C ABI、资源与 Vendor 扩展入口 |
| `central-brain/contracts` | 机器可读合同 | Schema、能力、发布和外部接口约束 |
| `apk-labs/client2-central-brain` | Client2 集成代码 | 座舱 HMI、输入、执行链路和 Unity 协同 |
| `apk-labs/renderservice-central-brain` | RenderService 集成代码 | Unity 原生 HVAC、座椅和渲染资源更新 |
| `docs/modules` | 模块详设 | 需求、源码、符号、接口、流程、校对清单和增量规则 |

`apk-labs` 当前承载已部署 HMI 集成源。量产发布前应迁移到 OEM 可持续构建的正式工程；迁移不得改变
本文定义的 SDK、Runtime 和状态语义。

### 2.1 模块详设索引

本文定义跨模块公共规则和接口摘要；代码校对与增量开发必须继续阅读对应模块详设。模块详设中的
“当前缺口”是生产激活边界，不能因为类或合同已存在而标记为已完成。

| 模块 | 详细设计 | 主要代码范围 |
| --- | --- | --- |
| SDK 与 Binder 合同 | [01-sdk-binder-contracts.md](modules/01-sdk-binder-contracts.md) | AIDL、Parcelable、Java facade、Binder 生命周期 |
| Runtime Service 组合 | [02-runtime-service-composition.md](modules/02-runtime-service-composition.md) | Application、Service、组合根、Task 生命周期 |
| Session 与持久化 | [03-session-persistence.md](modules/03-session-persistence.md) | Room v4、Repository、Outbox、恢复 |
| Context 与 Vehicle Twin | [04-context-vehicle-twin.md](modules/04-context-vehicle-twin.md) | 信号 Schema、Capability、Context、Digital Twin |
| Scenario 与 Plan | [05-scenario-plan.md](modules/05-scenario-plan.md) | Manifest、Catalog、Resolver、Compiler |
| Agent Graph 与 Orchestration | [06-agent-graph-orchestration.md](modules/06-agent-graph-orchestration.md) | Graph 状态机、Executor、Checkpoint、Endpoint |
| Governance、Approval 与 Identity | [07-governance-approval-identity.md](modules/07-governance-approval-identity.md) | 调用方身份、Capability、驾驶安全、审批 |
| Tool 与 Skill Runtime | [08-tool-skill-runtime.md](modules/08-tool-skill-runtime.md) | Manifest、Registry、Resolver、Executor、Skill 校验 |
| Memory Lifecycle | [09-memory-lifecycle.md](modules/09-memory-lifecycle.md) | Working/Profile/Episodic、Consent、Context Budget |
| Event、Trigger 与 Suggestion | [10-event-trigger-suggestion.md](modules/10-event-trigger-suggestion.md) | Broker、Cursor、QoS、Trigger、主动建议 |
| Model、Scheduler 与 OpenClaw | [11-model-scheduler-openclaw.md](modules/11-model-scheduler-openclaw.md) | Provider、Router、Scheduler、Prompt、输出校验 |
| Effect 与 Vehicle Adapter | [12-effect-vehicle-adapter.md](modules/12-effect-vehicle-adapter.md) | Effect Batch、Adapter、Readback、Compensation |
| Client2 HMI | [13-client2-hmi.md](modules/13-client2-hmi.md) | Reducer、状态树、Timeline、HVAC、座椅、多模态 |
| RenderService Unity | [14-renderservice-unity.md](modules/14-renderservice-unity.md) | Unity bundle、TextMeshPro、温度状态、触摸链 |
| Native Runtime | [15-native-runtime.md](modules/15-native-runtime.md) | C ABI、JNI、Native handle、NPU 扩展 |
| Security、Privacy、Release 与 Observability | [16-security-privacy-release-observability.md](modules/16-security-privacy-release-observability.md) | 安全库存、隐私、诊断、准入、复测 |

## 3. 构建与进程模型

### 3.1 APK 和 Library

| 产物 | 类型 | 关键权限/依赖 |
| --- | --- | --- |
| Client2 | 座舱 APK | `BIND_RUNTIME`，可选相机/媒体输入权限 |
| Runtime Service | 独立 APK | `INTERNET`，三个 signature Binder 权限 |
| Central Brain SDK | AAR | AIDL/Parcelable/Java facade |
| Native Runtime | AAR + `.so` | JNI，C ABI v1 |
| RenderService 集成 | OEM APK 增量 | Unity/Tuanjie 资源与跨进程展示接口 |

### 3.2 Runtime Service

`CentralBrainRuntimeApplication` 只做进程级依赖初始化。服务分为：

| Service | Binder 权限 | 接口 |
| --- | --- | --- |
| `CentralBrainRuntimeService` | `com.centralbrain.permission.BIND_RUNTIME` | Task、Session、Event、Orchestration |
| `CentralBrainGovernanceService` | `com.centralbrain.permission.BIND_GOVERNANCE` | Action policy 与 approval |
| `CentralBrainDiagnosticService` | `com.centralbrain.permission.ACCESS_DIAGNOSTICS` | 只读、有界诊断页 |

生产包必须由同一受控 signer 签署 Client2、SDK 使用方和 Runtime，或由 OEM 权限策略明确授权。

## 4. 公共合同规则

### 4.1 ID 与幂等

| 字段 | 规则 |
| --- | --- |
| `sessionId` | Runtime 生成，1..128 字符，owner-scoped |
| `clientRequestId` | 调用方生成，同一调用方内唯一 |
| `idempotencyKey` | 相同 key 只能对应相同 payload digest |
| `traceId` | 64 位小写 SHA-256，不包含原始内容 |
| `planRevision` | Session 内单调递增 |
| `eventSequence` | Session/topic 内单调递增 |
| `cursor` | 不透明字符串，调用方不得解析或构造 |
| `effectId` | Plan revision 内唯一，必须绑定 action digest |

相同幂等键和不同 payload 必须返回冲突，不得重放旧成功结果。

### 4.2 时间

- 跨进程 deadline 使用 `elapsedRealtime` 语义，持久化时同时保存必要的墙钟元数据。
- 所有安全 Context 带采集时间和最大年龄。
- 超时后节点进入 terminal 或 reconciliation，不允许无限等待。

### 4.3 有界数据

| 数据 | 上限 |
| --- | --- |
| 用户文本 | 4096 字符 |
| Event V2 page | 100 项 |
| Event V2 subscriber queue | 256 项 |
| 模型结构化输出 | 16 KiB |
| 模型参数 | 16 项 |
| 模型摘要 | 256 字符 |
| 模型输入 token | 32768 |
| 模型输出 token | 8192 |
| 模型总 token | 40960 |
| 模型端到端预算 | 1..120000 ms |
| 单张图片 | 6 MiB |
| 单次图片附件 | 1 张，PNG 或 JPEG |

超限必须在边界处拒绝，不能先完整解析再截断。

## 5. AIDL 对外接口

所有接口必须先调用 `getProtocolVersion()` 和 `getProtocolHash()`。hash 不匹配时 SDK 返回
`ERROR_PROTOCOL_MISMATCH`，不得继续调用业务事务。

### 5.1 `ICentralBrainRuntime` v1

```java
int getProtocolVersion();
String getProtocolHash();
TaskHandle submitAgentTask(
        in AgentTaskRequest request,
        ICentralBrainTaskCallback callback);
boolean cancelTask(in TaskHandle handle, int reasonCode);
TaskUpdate getTaskStatus(in TaskHandle handle);
```

任务状态：`UNKNOWN / ACCEPTED / RUNNING / COMPLETED / FAILED / CANCELLED`。回调为 oneway；服务必须
限制每个任务的回放回调数，并在调用方死亡时按策略取消。

### 5.2 `ICentralBrainSessionRuntime` v1

```java
SessionHandle openSession(in SessionRequest request);
SessionSnapshot getSession(in SessionHandle handle);
SessionPage listSessions(in SessionQuery query);
boolean cancelSession(in SessionHandle handle, int reasonCode);
```

`SessionRequest` 至少绑定 source、seat zone、scenario hint、client request、idempotency 和 deadline。
`SessionSnapshot` 是 Runtime 的不可变投影，调用方不得修改后回传。

### 5.3 `ICentralBrainSessionEventsV2` v2

```java
EventPageV2 getEvents(String sessionId, String cursor, int limit);
EventSubscriptionHandle registerSessionCallback(
        in EventSubscriptionRequest request,
        ICentralBrainSessionEventCallbackV2 callback);
EventAckResult acknowledge(in EventAckRequest request);
boolean unregisterSessionCallback(
        in EventSubscriptionHandle handle,
        ICentralBrainSessionEventCallbackV2 callback);
boolean cancelSubscription(in EventSubscriptionHandle handle);
```

ACK 结果区分 `APPLIED / REPLAYED / NOT_FOUND / NOT_ACTIVE / RESYNC_REQUIRED / STALE / FUTURE /
SOURCE_REGRESSION`。出现 `RESYNC_REQUIRED` 后 SDK 必须分页读取缺失事件，再重新订阅。

### 5.4 `ICentralBrainOrchestration` v1

```java
OrchestrationSnapshot start(in OrchestrationStartRequest request);
OrchestrationSnapshot getSnapshot(String sessionId);
ScenarioPlan getPlan(String sessionId);
OrchestrationSnapshot respondToApproval(in ApprovalResponse response);
OrchestrationSnapshot requestUndo(in UndoRequest request);
OrchestrationSnapshot cancel(String sessionId, int reasonCode);
```

生产调用只允许 `PROFILE_PRODUCTION`。`ApprovalResponse` 和 `UndoRequest` 必须绑定 Session、
Plan revision、目标 ID 和幂等键。

### 5.5 `ICentralBrainGovernance` v1

```java
ActionDecision evaluateAction(in ActionRequest request);
ApprovalHandle requestApproval(in ActionRequest request);
ApprovalStatus getApprovalStatus(in ApprovalHandle handle);
boolean cancelApproval(in ApprovalHandle handle);
```

该接口故意不提供 `grant` 方法。审批结果只能由受信任 HMI 流程提交给 Orchestration，不能由任意
Binder 调用方写入。

### 5.6 `ICentralBrainDiagnostics` v1

```java
DiagnosticPage getPage(in DiagnosticQuery query);
```

诊断接口只返回有界、脱敏记录，不接受任务控制命令。分页结果不得包含用户文本、模型全文、图像、
凭据或原始车辆载荷。

## 6. SDK 详设

### 6.1 `CentralBrainSdk`

负责公开常量、组件名、成熟度和 facade 创建。SDK 不直接 new Runtime 内部类，不暴露数据库实体。

### 6.2 `SessionClient`

主要方法：

```java
boolean connect();
boolean reconnect();
boolean isConnected();
SessionHandle openSession(SessionRequest request, RuntimeEventListener listener);
SessionSnapshot getSession(SessionHandle handle);
SessionPage listSessions(SessionQuery query);
boolean cancelSession(SessionHandle handle, int reasonCode);
void observeSession(SessionHandle handle, String resumeCursor, RuntimeEventListener listener);
void stopObserving(SessionHandle handle);
void close();
```

实现要求：

1. 对所有 DTO 调用 Contract validator。
2. callback 经调用方提供的 `Executor` 串行化，禁止在 Binder thread 直接更新 UI。
3. 连接建立后校验 Session/Event/Event V2 的 version/hash。
4. 保存每个订阅最后确认的 cursor。
5. 重连后先分页 replay，再注册 callback，避免事件空洞。
6. `close()` 必须幂等并停止所有订阅。

### 6.3 `OrchestrationClient`

客户端不在本地推断 Plan 或 Effect。所有命令转换成 AIDL DTO，返回的 `OrchestrationSnapshot` 先做
schema、owner、revision 和状态校验，再交给 HMI reducer。

### 6.4 错误模型

SDK 对外使用稳定错误类：

| 错误 | 触发条件 | 调用方处理 |
| --- | --- | --- |
| `ERROR_INVALID_ARGUMENT` | DTO、枚举、范围或 digest 非法 | 修复调用参数，不重试 |
| `ERROR_PROTOCOL_MISMATCH` | AIDL version/hash 不一致 | 阻止使用并要求升级 |
| `ERROR_TRANSPORT` | Binder 异常或服务死亡 | 重连并恢复 Session |
| `ERROR_DEADLINE_EXCEEDED` | deadline 已过 | 展示超时，不重复提交 |
| `ERROR_POLICY_BLOCKED` | Governance 拒绝 | 展示原因，不绕过 |
| `ERROR_CAPABILITY_UNAVAILABLE` | Provider/Adapter 缺失 | 降级 UI，不标记成功 |

## 7. Runtime 组合

### 7.1 启动顺序

1. 加载 `CallerCapabilityPolicy`。
2. 打开 `CentralBrainDatabase` 并校验 schema。
3. 执行 task、graph 和 outbox reconciliation。
4. 初始化 Session/Event endpoints。
5. 初始化 Governance、Tool、Memory 和 Model Registry。
6. 仅登记符合 production assurance 的 Provider/Adapter。
7. 发布 Binder Service。

任何关键步骤失败时服务保持 unavailable；不能发布部分可执行、部分未治理的状态。

### 7.2 线程模型

| 执行域 | 用途 | 约束 |
| --- | --- | --- |
| Binder pool | 参数校验、授权、快速查询 | 不执行网络、数据库长事务或模型调用 |
| Runtime serial executor | Session/Graph 状态迁移 | 单 Session 顺序一致 |
| I/O executor | Room、网络、Adapter | 必须带 deadline 和取消 |
| Model stream executor | token/chunk 回调 | 每请求顺序，终态唯一 |
| HMI main thread | reducer 后的视图更新 | 不执行 Runtime 工作 |

锁顺序固定为 `admission -> session -> graph -> effect`。禁止持有数据库事务时调用外部 Provider 或 Adapter。

## 8. Session 与 Persistence

### 8.1 `DurableSessionRegistry`

职责：

- 根据 caller fingerprint 和幂等键创建 Session。
- 对 query/list 强制 owner 隔离。
- 校验状态转换和 deadline。
- 为 Orchestration 提供活动 Plan revision。

### 8.2 Room v4 实体

| Entity | 主键/关联 | 内容 |
| --- | --- | --- |
| `SessionEntity` | `sessionId` | owner、state、deadline、revision |
| `PlanEntity` | `sessionId + revision` | manifest digest、graph digest |
| `PlanNodeEntity` | `plan + nodeId` | node type、state、dependency |
| `RuntimeEventEntity` | `session + sequence` | topic、type、digest、metadata |
| `EventCursorEntity` | `owner + subscription` | last acknowledged cursor |
| `TaskCheckpointEntity` | `session + nodeId` | serializer version、state digest |
| `ApprovalRequestEntity` | `approvalId` | target digest、deadline、state |
| `PendingEffectEntity` | `effectId` | desired、dispatch、terminal state |
| `EffectObservationEntity` | `effectId + sequence` | reported state、quality、source |
| `OutboxEntity` | `outboxId` | action digest、attempt、lease |
| `CompensationEntity` | `effectId` | reverse action、eligibility |
| `AuditEventEntity` | sequence | category、reason、digest |

迁移必须向前兼容并在发布前验证。不得使用 destructive migration。

### 8.3 Reconciliation

启动时：

- 非终态 task 标记为 interrupted，并根据策略恢复或失败。
- `DISPATCHING` Effect 不能直接重发，先查询 readback。
- 已 ACK 事件游标继续有效。
- 过期审批转为 expired。
- 不可恢复的 Graph 转为 stuck，并发布唯一终态。

## 9. Context 与 Vehicle Digital Twin

### 9.1 `VehicleSignalPath`

canonical path 由枚举或构建时表定义，不接受模型或网络返回的任意字符串。每个 `SignalValue` 必须携带：

```text
path, area, scalarType, value, timestamp, quality, source, schemaVersion
```

### 9.2 `CapabilityCatalog`

`VehicleCapability` 定义：

```text
capabilityId, targetArea, scalarType, minimum, maximum, step,
riskClass, adapterId, authorizationClass, availability
```

HVAC 温度的约束为 `18.0 <= value <= 30.0` 且 `(value - 18.0) % 0.5 == 0`。

### 9.3 `VehicleDigitalTwinStore`

主要操作：

- `updateReported(...)`：只接受受信来源和单调时间。
- `setDesired(...)`：只接受治理通过的 Effect。
- `snapshot(...)`：返回不可变、带 revision 的快照。
- `isFresh(...)`：按 capability 的最大年龄判断。

reported 状态过期时，Governance 必须拒绝依赖它的高风险动作。

## 10. Scenario 与 Plan

### 10.1 `ScenarioManifest`

Manifest 至少包含：

```text
scenarioId, version, artifactDigest, allowedCapabilities,
nodeTemplates, approvalRules, compensationRules, contextRequirements
```

Manifest 由构建或发布系统提供，Runtime 校验摘要后载入。

### 10.2 `DeterministicScenarioResolver`

输入为结构化模型结果、Context snapshot 和 catalog revision。选择规则固定排序：

1. scenario ID 精确匹配。
2. 所需 capability 全部可用。
3. Context 满足 freshness 和 trust。
4. 驾驶状态满足场景限制。
5. manifest version 和 digest 受信。

无候选时返回明确 `NO_ELIGIBLE_SCENARIO`。

### 10.3 `ScenarioPlanCompiler`

Compiler 将 manifest template 和已验证参数转换为 `ScenarioPlan`。必须检查：

- node ID 唯一。
- 依赖节点存在且无环。
- node type 在 allowlist。
- required/optional 语义明确。
- deadline 不超过 Session deadline。
- 审批节点位于受控动作之前。
- 补偿节点只引用可逆动作。

## 11. Agent Graph Runtime

### 11.1 状态

`GraphRunState`：`CREATED / RUNNING / WAITING / SUCCEEDED / PARTIAL / FAILED / CANCELLED /
COMPENSATING / COMPENSATED / STUCK`。

`NodeRunState`：`PENDING / READY / EXECUTING / WAITING / SUCCEEDED / FAILED / SKIPPED /
CANCELLED / COMPENSATING / COMPENSATED / STUCK`。

### 11.2 `TypedNodeExecutor`

```java
NodeExecutionResult execute(
        NodeExecutionInput input,
        NodeExecutionContract contract);
```

Executor 必须：

- 只读取显式输入和受控依赖结果。
- 尊重 deadline/cancellation。
- 返回 typed output 或稳定 failure code。
- 不修改 Graph 共享状态。
- 不自行发布终态；由 Graph Runtime 提交迁移。

### 11.3 重试

`NodeRetryPolicy` 决定是否重试，`BackoffCalculator` 计算有界退避。以下情况禁止重试：

- 参数或 schema 非法。
- Governance 拒绝。
- 审批拒绝或过期。
- 幂等冲突。
- terminal Adapter failure。

外部请求已经接受但结果未知时进入 reconciliation，不作为普通重试。

## 12. Governance 与 Approval

### 12.1 调用方身份

`AndroidCallerIdentityResolver` 从 Binder 获取 UID/package/signature，并生成
`DurablePrincipalFingerprint`。调用方提供的 owner 字段只能与系统解析结果比较，不能作为身份来源。

### 12.2 固定治理链

`FixedGovernanceMiddlewareChain` 的固定顺序：

```text
IDENTITY -> CAPABILITY -> PRIVACY -> VEHICLE_STATE -> DRIVER_SAFETY
-> CONSENT -> QOS -> TRACE -> DISPATCH_CONTRACT -> OUTPUT_POLICY
```

任何 stage 返回 unknown/reject，后续 dispatch 均关闭。审计记录只保存 request fingerprint、
stage、reason 和 evidence digest。

### 12.3 Driver Safety

`DriverSafetyAdmissionContract` 使用车速、档位、DMS/身份、目标区域、动作风险和状态年龄。
车辆状态未知或过期时按 moving/restricted 处理。座椅大角度调整、视频、诊断写和 OTA 在行驶中禁止。

### 12.4 Approval

`ApprovalRequest` 必须包含：

```text
approvalId, ownerFingerprint, sessionId, planRevision,
targetId, targetDigest, riskClass, issuedAt, expiresAt
```

审批回复只在 owner、revision、digest 和 deadline 全部匹配时生效。重复相同回复幂等；冲突回复拒绝。

## 13. Tool、Skill 与 Memory

### 13.1 Tool

`ToolManifest` 定义 tool ID、version、input/output schema、capability、timeout 和 signer digest。
`ToolRegistry` 负责登记，`ToolResolver` 按版本和健康确定性选择，`ToolRuleSolver` 将模型提案与规则求交集，
`InProcessBuiltInToolExecutor` 执行内建工具。

Tool 输出必须有最大字节数和稳定 failure code。Tool 不得返回可直接绕过 Governance 的 Adapter handle。

### 13.2 Skill

`SkillArtifactVerifier` 依次校验：

1. signer policy。
2. artifact digest。
3. version policy。
4. dependency allowlist。
5. requested capability。
6. entry contract。

生产 Skill 只允许受控内建实现；不允许动态下载后直接加载代码。

### 13.3 Memory

| 类 | 作用 | 持久化要求 |
| --- | --- | --- |
| `WorkingMemoryStore` | Session 临时事实 | Session 结束清理 |
| `ProfileMemoryStore` | 用户偏好 | consent、authorization、encryption owner |
| `EpisodicMemoryStore` | 摘要事件 | 禁止原始文本和模型全文 |
| `ContextBudgetManager` | 模型上下文裁剪 | system/safety 项不可裁剪 |
| `MemoryConsentController` | HMI 同意与清除 | owner-scoped、revisioned |

## 14. Event Broker

`EventBroker` 发布 immutable typed event。生产实现必须将 durable event 与 callback 解耦：

1. 在 Room 事务中写入事件和 sequence。
2. 提交事务。
3. 通知进程内订阅者。
4. callback 失败不回滚持久事件。
5. queue 溢出时标记 `RESYNC_REQUIRED`。

事件正文只包含 HMI 所需的有界投影；大型图像通过受控媒体句柄传递，不能嵌入事件日志。

## 15. Model Runtime 与 OpenClaw

### 15.1 `ModelContractV2`

`ModelRequest`：

```text
schemaVersion=2
requestId
purpose
privacyClass
latencyBudget
tokenBudget
requiredCapability
fallbackPolicy
traceId
inputDigest
requestFingerprint
```

`ModelResult` 绑定 request fingerprint、provider、state、output digest、token usage 和 detail code。

### 15.2 `ModelProvider`

```java
Descriptor descriptor();
Snapshot snapshot();
Snapshot warmup(ModelSpec modelSpec);
InferenceHandle infer(InferenceRequest request, StreamObserver observer);
CancelState cancel(String requestId, String reason);
Metrics metrics();
FaultSnapshot lastFault();
void close();
```

Provider assurance 为 `EMPTY / TEST_ONLY / DEBUG_ONLY / TARGET_INTEGRATION / PRODUCTION`。只有
`PRODUCTION` 可以设置 `productionEligible=true`。

### 15.3 Provider 选择

`PolicyAwareModelRouter` 输入：

- required capability 和模态。
- privacy class。
- latency/token budget。
- Provider health 和 assurance。
- resource/thermal admission。
- fallback policy。

选择结果必须记录 provider ID 和决策摘要。`NO_FALLBACK` 不得路由到第二 Provider。

### 15.4 OpenClaw 过渡 Provider

生产目标连接：

```text
WebSocket: ws://169.254.208.110:18789/
Control UI: http://169.254.208.110:18789/chat
Protocol: 3
```

凭据由受控发布配置持有，不在本文展示。当前源码中的固定凭据可从 APK 提取，是必须在量产准入前整改的
风险，且不得记录到日志、事件或 HMI。

协议流程：

1. 建立 RFC 6455 WebSocket。
2. 接收 `connect.challenge` 和 nonce。
3. 发送 `connect` 认证请求。
4. 使用 `chat.send` 发送 session/run/idempotency/text。
5. 多模态请求在同一个 `chat.send` 中附带一张 PNG/JPEG。
6. 接收流式事件并绑定当前 run ID。
7. 必要时用 `chat.history` 查询当前请求的终态。
8. 取消时发送 `chat.abort`。

限制：

- 最大一张图片，6 MiB。
- 最大已认证多模态帧 8,500,000 bytes。
- 连接超时 3000 ms，读取超时 120000 ms。
- 任意 endpoint override 禁止。
- 模型结果必须通过 `StructuredModelOutput`。

当前过渡 Provider 尚未达到 `PRODUCTION` assurance，因此 release 路由必须保持关闭，直到实现进入生产
源集并完成凭据、故障恢复、资源、隐私和目标验收。

### 15.5 `StructuredModelOutput`

输出 schema 只允许：

```json
{
  "scenarioId": "cataloged-id",
  "parameters": [
    {
      "capabilityId": "cataloged-capability",
      "area": "cataloged-area",
      "value": "typed-value"
    }
  ],
  "summary": "bounded text"
}
```

未知字段、重复字段、未知场景、未知能力、错误类型、越界值或超过 16 KiB 必须拒绝。
`AcceptedOutput.isActionAuthorizationGranted()` 永远返回 false。

## 16. Effect 与 Adapter

### 16.1 `EffectBatch`

每个条目包含：

```text
effectId, capabilityId, targetArea, actionId, desiredValue,
required, dependencyIds, deadline, idempotencyKey, compensation
```

### 16.2 `AdapterRegistry`

Adapter descriptor 必须绑定：

```text
adapterId, profile, capabilityId, targetArea, destination,
signerDigest, version, health, supportsReadback
```

生产 profile 不能解析到未达到 production assurance 的 Adapter。

### 16.3 `EffectCoordinator`

算法：

1. 校验所有 `EffectIntent`。
2. `EffectDependencyPlanner` 生成 wave。
3. 为每个 Effect 调用 `prepare`。
4. 任一 required Effect prepare 失败时，整个 batch 在 dispatch 前中止。
5. 按 wave dispatch；依赖失败时下游标记 blocked。
6. 记录 delivered/unknown/retryable/terminal。
7. 对 accepted/unknown 调用 readback。
8. 更新 Digital Twin 并发布唯一结果事件。

### 16.4 Vehicle Adapter 接口

生产 Adapter 应实现：

```java
PrepareResult prepare(EffectIntent intent, long nowElapsedMs);
DispatchResult dispatch(PreparedMaterial material, long deadlineElapsedMs);
EffectObservation readback(
        String capabilityId,
        String targetArea,
        long deadlineElapsedMs);
HealthSnapshot health();
```

OEM 属性号、服务名、权限和错误码只能在具体 Adapter 内出现。上层只能使用 canonical capability。

### 16.5 购物与导航接口

购物服务分为：

- `searchProducts(query, context)`：返回有界商品候选。
- `searchMerchants(product, routeContext)`：返回有界商户候选。
- `prepareOrder(product, merchant, quantity)`：生成订单预览和 approval digest。
- `commitOrder(approval)`：只有批准后调用。

导航服务分为：

- `searchDestination(query)`。
- `previewRoute(destination, vehicleContext)`。
- `startNavigation(routeId, approval)`。

`commitOrder` 与 `startNavigation` 使用不同 approval ID，任一批准不能授权另一个动作。

## 17. Client2 HMI 详设

### 17.1 状态所有权

HMI 使用 immutable state + reducer。核心状态：

```text
session
inputProjection
modelOutputProjection
planTimeline
pendingApproval
hvacState
seatState
shoppingState
navigationState
connectionState
presentationMode
```

View 只根据 state 渲染；点击事件转换为 reducer input 或 SDK command。

### 17.2 任务触发

“我累了”：

1. 创建 fatigue Session。
2. 显示输入。
3. 请求模型生成舒适性计划。
4. 编译 HVAC 与座椅 Effect。
5. Governance 校验驾驶状态和座椅策略。
6. 执行并根据 readback 更新温度和靠背角度。

“处理一下”：

1. 获取当前座舱帧。
2. 将图片与文本绑定到同一请求。
3. 显示文字和图片缩略图。
4. 模型输出乘员和意图候选。
5. 编译购物与路线 DAG。
6. 显示候选、独立审批和后续执行。

### 17.3 执行链路

HMI 只消费 typed event：

```text
INPUT_ACCEPTED
MODEL_STARTED
MODEL_CHUNK
MODEL_COMPLETED
PLAN_READY
APPROVAL_REQUIRED
EFFECT_PREPARING
EFFECT_DISPATCHED
EFFECT_READBACK
NODE_COMPLETED
SESSION_COMPLETED / PARTIAL / FAILED
```

每条记录包含 sequence、stage、summary、status、timestamp 和可选 target。相同 sequence 必须去重。

### 17.4 HVAC 与座椅

- 温度 UI 范围 18.0..30.0，步进 0.5。
- 温度使用与 Unity 原界面一致的字体、位置和材质。
- 禁止用 Android 文本覆盖 Unity 原生温度。
- 座椅靠背 15 度到 30 度为展开方向。
- 动画进度不等于 Effect readback；完成状态由 Runtime 投影。

### 17.5 显示与触摸

- 设计分辨率 1920x1080。
- Client2 悬浮层不得修改车模渲染 viewport。
- RenderService 保留原生触摸输入；覆盖层只拦截自身可交互区域。
- 图片居中预览时点击遮罩关闭，点击图片本身不关闭。
- 行驶受限模式隐藏长文本、禁用高风险入口并保留紧急取消。

## 18. Native Runtime

### 18.1 C ABI v1

```c
cb_status_t cb_runtime_create_v1(
        const cb_runtime_config_v1_t *config,
        cb_runtime_t **out_runtime);
cb_status_t cb_runtime_get_health_v1(
        cb_runtime_t *runtime,
        cb_runtime_health_v1_t *out_health);
cb_status_t cb_runtime_acquire_slot_v1(
        cb_runtime_t *runtime,
        uint64_t *out_lease_id);
cb_status_t cb_runtime_release_slot_v1(
        cb_runtime_t *runtime,
        uint64_t lease_id);
cb_status_t cb_runtime_destroy_v1(cb_runtime_t *runtime);
const char *cb_status_name(cb_status_t status);
```

`CB_NATIVE_ABI_VERSION=1`，最大槽位 64。每个结构体首字段为 `struct_size` 和 `abi_version`。

状态码：

```text
OK, INVALID_ARGUMENT, ABI_MISMATCH, OUT_OF_MEMORY,
CAPACITY_EXHAUSTED, NOT_FOUND, BUSY, CLOSED, INTERNAL_ERROR
```

调用方必须保证 destroy 与其他操作串行。Native 层不记录用户输入或模型内容。

### 18.2 Vendor NPU 扩展

Vendor Provider 就绪后应在 Native Runtime 后增加独立 Adapter，至少定义：

```text
provider_open / provider_close
model_load / model_unload
infer_submit / infer_cancel
buffer_import / buffer_release
health / metrics / last_fault
```

ABI、内存所有权、cache coherency、并发、取消和复位必须由 Vendor 文档确认后实现。

## 19. 安全、隐私和日志

### 19.1 安全边界

- 所有 exported Service 使用 signature permission。
- 每次 Binder 调用重新解析 UID，不缓存调用方自报身份。
- 数据库查询强制 owner 条件。
- 模型和 Tool 参数进行严格 schema 校验。
- Adapter registry 由构建或发布配置拥有，运行时不接受任意类名。
- 安全策略异常等价于拒绝。

### 19.2 日志允许字段

允许：

```text
requestId, sessionId 的不可逆摘要, stage, status, reasonCode,
providerId, adapterId, duration bucket, byte count, sequence
```

禁止：

```text
语音全文、模型全文、图片、凭据、精确位置、原始车辆 payload、
用户身份原值、支付信息、签名材料
```

### 19.3 数据删除

Session 结束后清理 Working Memory 和临时媒体。Profile/Episodic 数据的清除必须 owner-scoped、
可审计且不留下可恢复原文。处于活动 Effect 或法定 hold 的记录按批准策略处理。

## 20. 发布与扩展流程

### 20.1 增加新车辆能力

1. 在需求文档分配 Req ID。
2. 增加 canonical capability 和 scalar/range/area。
3. 更新 CapabilityCatalog 和 Governance policy。
4. 定义 Adapter descriptor 和 prepare/dispatch/readback。
5. 在 Scenario Manifest 中显式允许。
6. 增加 typed Effect 和 HMI projection。
7. 验证权限、错误、超时、回读、重启和回滚。
8. 获得 OEM/Vendor owner 批准后才注册 production Adapter。

### 20.2 增加新模型 Provider

1. 实现 `ModelProvider` 全部生命周期。
2. 定义固定 endpoint 和凭据 owner。
3. 声明 capability、模态、并发、token 和 privacy。
4. 实现流式顺序、终态唯一、取消和超时。
5. 接入 Resource Admission 和 Router。
6. 通过结构化输出、故障恢复、隐私和性能验收。
7. 达到 `PRODUCTION` assurance 后进入 release registry。

### 20.3 增加新 AIDL

1. 优先尾部扩展 Parcelable；不改变现有 transaction 语义。
2. 必要时创建新版本接口并保留旧接口兼容期。
3. 更新 interface version/hash。
4. 增加 SDK 协商、fallback 或明确拒绝。
5. 更新三个权威文档和机器可读合同。

## 21. 当前实现与量产缺口

| 模块 | 主源码状态 | 量产状态 |
| --- | --- | --- |
| SDK/AIDL | 已实现 | 待 signer 与正式 Client2 工程集成 |
| Session/Event/Room | 已实现 | 待完整 Runtime 发布与升级验收 |
| Scenario/Graph | 已实现 | release backend 尚未激活 |
| Governance/Approval | 已实现 | OEM driver-safety 和 consent owner 未批准 |
| Tool/Skill/Memory | 已实现 | production registry/authority 未发布 |
| Model Contract/Router | 已实现 | production Provider 未合格 |
| OpenClaw | 过渡接口已实现 | production assurance、凭据和发布路由未关闭 |
| Effect Coordinator | 已实现 | 真实 Vehicle Adapter 未注册 |
| Native C ABI | 已实现 | Vendor NPU Provider 未实现 |
| Client2 HMI | 已实现主要闭环 | P4-R7 实现仍在 Draft，正式工程迁移未完成 |
| Release/Privacy/Safety | 合同已实现 | owner 审批和目标证据外部阻塞 |

因此当前仓库的软件合同和大部分模块已形成，但不能声明为量产就绪，也不能声明真实车辆或 NPU
闭环已经验收。
