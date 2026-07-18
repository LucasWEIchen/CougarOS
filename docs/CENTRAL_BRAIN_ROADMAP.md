# Central Brain Android 13 开发路线图

版本：1.1
日期：2026-07-17
状态：Stage 2 P6 in progress

## 1. 基线与范围

用户提供的架构图是需求基线，不是示意图。当前开发对象是不能修改厂商 Framework/BSP/预编译
系统组件的黑盒 Android 13 座舱控制器，主语言为 Java、AIDL、C 和 JNI。

2026-07-16 范围决策：

- Python 仿真 runtime、REST/JSON gateway、Linux Python binding/CLI/daemon、旧 Android Console
  和专用测试/部署样例全部退役。
- 当前不开发 Linux 前端，不开发 Hypervisor，不猜测 vendor property/device node/ioctl。
- 保留 Android ModelProvider/Scheduler、`vendor.npu.empty`、NPU C ABI/JNI、Driver/HAL gap、
  Safety 边界和真实硬件交付工具。
- Android deterministic provider、Digital Twin 和 Effect adapter 只允许位于 debug/test 范围，
  不得进入 production profile，也不得替代真实硬件验收。
- Driver/HAL 只有在公开/vendor SDK 确认不能满足具体接口后，才登记最小新增工作量。
- GitHub `LucasWEIchen/CougarOS` 是完整受维护源码和文档的唯一远端基线；每个完成增量必须
  commit、push、通过远端检查，并同步默认分支首页的架构、开发进度和近期记录。

主要 Req IDs：`APP-001/003/004`、`XSC-001..006`、`FW-U-001/003/004/006/007`、
`FW-S-001/003/005`、`NV-F-001/003/004/005/008/009/011/012`、
`NV-G-003/004/005/006/007`、`NV-P-002/006`、`KH-003/006`、
`DEL-001/003/004/005`、`S2-UX-001..003`、`S2-SES-001`、`S2-CTX-001`、
`S2-TWN-001`、`S2-SCN-001`、`S2-GRF-001`、`S2-SAF-001`、`S2-EFF-001`、
`S2-HMI-001..006`、
`S2-ADP-001/002`、`S2-TOL-001`、`S2-MEM-001`、`S2-EVT-001`、
`S2-MDL-001`、`S2-OBS-001`、`S2-REL-001`。

## 2. 成熟度规则

| 状态 | 含义 | 可接受证据 |
| --- | --- | --- |
| `contract_defined` | 类型、边界、错误、Req ID 已定义 | static/contract check |
| `prototype_implemented` | Android debug/test 中有确定性实现 | JVM/instrumentation test |
| `android_integrated` | Android 13 APK/AAR 链路运行 | Binder/API 33 device evidence |
| `hardware_validated` | 目标 vendor/车辆/NPU 接口通过 | target smoke/fault/rollback |
| `production_qualified` | 性能、安全、隐私、升级、运维关闭 | signed acceptance package |

禁止状态提升：

- test double 不能提升到 `hardware_validated`。
- empty provider 不能提升到 `android_integrated` 的硬件含义。
- 模拟器证据不能替代 ARM64 物理设备或车辆/NPU 证据。
- repository check 不能设置 `production_ready=true`。

## 3. 已完成 Android 基础阶段

| 阶段 | 目标 | 交付 | 状态 |
| --- | --- | --- | --- |
| R0 | 架构、Req ID、成熟度和偏差基线 | requirements/roadmap/issues/deviations | 已完成 |
| R1 | Android Gradle 多模块交付骨架 | AI SDK AAR、Runtime Service APK、Demo HMI APK | 已完成 |
| R2 | Typed/async Protocol Binding | production/diagnostic AIDL、Parcelable、callback/cancel/death | 已完成 |
| R3 | Android Runtime 核心 | Job Supervisor、可信 Binder 身份、capability/policy | 已完成 |
| R4 | Durable workflow | Room、checkpoint、approval、effect/outbox、recovery | 已完成（软件基础） |
| R5 | Model contract | ModelProvider、scheduler、test router、Vendor empty provider | 已完成（合同/测试） |
| R6 | Event/Memory/Skill | bounded runtime、durable cursor、middleware/readiness | 已完成（软件基础） |
| R7 | 应用集成 | aggregate acceptance、Client2 Binder、recovery、handoff | 已完成（应用层） |
| B0 | 黑盒实际工程基线 | Java/C/JNI/ABI/部署边界 | 已完成 |
| B1 | Native C Runtime | C ABI V1、JNI、arm64-v8a/x86_64 AAR | 已完成 |
| B2 | Runtime 集成 | Native lifecycle 接入 Binder Runtime 与 Diagnostic | 已完成 |
| B3 | 黑盒验收 | 公开 API 能力探测、安全安装、API 33 设备证据 | 已完成（模拟器 + 物理应用层） |
| B4 | 实际工程交付 | APK/AAR、hash/signer/ABI、安装和使用指南 | 已完成（软件交付） |
| B5 | 内网硬件闭环 | Private Release、结构化 Issue、脱敏复测 | 已建立，持续维护 |

上述“已完成”只描述各阶段的软件退出条件。真实车辆控制、Vendor NPU、Driver/HAL、production
signer、system/privileged deployment 和整车资格仍未完成。

## 4. Android 证据追踪键

| 增量键 | 当前证据边界 |
| --- | --- |
| R4_DURABLE_WORKFLOW | Room/outbox/recovery 软件基础完成，production effect delivery 关闭。 |
| R4C2B repository-only retry/terminal | retry/dead-letter 只证明 repository 语义。 |
| R5A1 model provider contract | Provider request/result/error/cancel 合同完成。 |
| R5A2 inference resource scheduler | deadline/priority/quota/cancel 合同完成。 |
| R5B1 deterministic stub provider | 只存在于 Android test/debug 验证，不是 NPU。 |
| R5B2 test-only model router | 只路由明确 test profile，release 不激活。 |
| R5C1 production-safe readiness | readiness 失败关闭，不提升硬件状态。 |
| R5D1 application-layer deployment | Vendor provider 仍为 empty。 |
| R6A1 bounded Event runtime | 有界 callback/overflow 软件语义完成。 |
| R6A2A durable Event schema | cursor/metadata schema 完成。 |
| R6A2B durable Event repository | owner/idempotency/reopen 完成。 |
| R6A3 Event runtime readiness | production broker/transport 仍阻塞。 |
| R6B1 bounded Memory lifecycle | metadata/digest 生命周期完成。 |
| R6B2 Memory runtime readiness | production privacy owner 仍阻塞。 |
| R6C1 signed built-in Skill runtime | 只允许内建 allowlist/test artifact。 |
| R6C2 fixed governance middleware chain | identity/policy/privacy/QoS/audit 顺序固定。 |
| R6C3 Skill/Governance readiness | production sandbox/route owner 仍阻塞。 |
| R7A1 aggregate Runtime acceptance | 聚合软件状态，不是测试证书。 |
| R7B Client2 SDK/Binder migration | HTTP/INTERNET fallback 已移除。 |
| R7C Android 13 application integration acceptance | Binder/UI/recovery 应用层矩阵完成。 |
| R7D Android 13 software handoff | Android artifacts/manifest/install/rollback 已结构化。 |
| P1-W01 Session contract V1 | 5 DTO、独立 Binder V1、边界校验、Parcel/checksum 门禁完成；服务未发布。 |
| P1-W02 Plan/Node contract V1 | 4 DTO、11 类 allowlist、DAG/补偿/重试校验、Parcel/checksum 完成；Runtime 未发布。 |
| P1-W03 Event contract V1 | 5 DTO、23 类 allowlist、独立 Event/callback V1、顺序/父链/脱敏/cursor/replay 校验、Parcel/checksum 完成；服务未发布。 |
| P1-W04 Effect/Approval contract V1 | 4 DTO、完整 Effect 状态链、approval/undo stale/TTL 校验、Parcel/checksum 完成；Service/grant/undo execution 未发布。 |
| P5-W04 ToolExecutor boundary | signed built-in allowlist、digest-only context、schema/deadline/cancel/output/audit 已完成；Runtime/production authority 未发布。 |

## 5. Python 原型退役

| 退役对象 | 现行替代 |
| --- | --- |
| Python Agent/REST gateway | `ICentralBrainRuntime` + `CentralBrainClient` |
| Python governance/policy | `ICentralBrainGovernance` + Binder identity/capability |
| Python readiness/audit | protected Diagnostics + Room audit + log/dumpsys snapshot |
| Python Ollama simulated NPU | Android `ModelProvider` contract；Vendor provider 当前为空 |
| Python hardware registry | NPU interface + Driver/HAL gap + Android activation gate |
| Android Console | Demo HMI + Client2 typed Binder panel |
| Linux Python CLI/daemon | 当前无替代交付；正式范围外 |

退役门禁：`tools/check_central_brain_python_prototype_retirement.sh`。决策记录：
`DEV-026`、`ISSUE-032` 和 `CENTRAL_BRAIN_PYTHON_PROTOTYPE_RETIREMENT.md`。

## 6. Stage 2 产品化计划

| 阶段 | 目标 | 主要交付 | 状态 |
| --- | --- | --- | --- |
| S2-P0 | 完整 AIOS Stage 2 设计冻结 | 调研、UX、最小工作包、详设、HMI 高保真稿件、验收指标 | 已完成 |
| S2-P1 | Runtime Contract v2 | Session、Plan、Effect、Event、facade、Room 与 aggregate gate | 已完成（W01-W07） |
| S2-P2 | Context、Digital Twin 与 Scenario foundation | Android debug/test context/twin；build-owned manifest；deterministic resolver/compiler；simulated adapters/controller；production 无 fallback | 已完成（W01-W12） |
| S2-P3 | Durable Agent Graph | plan/step/checkpoint/recovery/compensation | 已完成软件 foundation（W01-W09；Runtime/production wiring 仍 false） |
| S2-P4 | Client2 HMI 与场景/Effect 投影 | P4-W01..W12 应用验收完成；Runtime 自动 Plan/Effect/readback 待接 | 应用层完成 / Runtime 未完成 |
| S2-P5 | Tool/Skill 与 Memory | Tool manifest/schema、Registry/Resolver/RuleSolver/Executor、Skill trust、Memory lifecycle | W01-W08 已完成，W09-W10 待开发 |
| S2-P6 | Event/Model 与高级 Memory 集成 | durable broker、proactive trigger、model routing、context budget | 未开始 |
| S2-P7 | 质量与发布 | fault matrix、性能、隐私、安全、升级 | 未开始 |
| S2-P8 | 真实车辆适配 | 按 capability 引入已确认的 vendor/public adapter | 外部阻塞 |

P0-P7 估算为 136-184 人日；其中 Client2 HVAC/Seat 中控闭环为 24-32 人日。该估算不含 Vendor
SDK、Driver/HAL、功能安全认证、量产 HMI 重写和
整车标定。详细工作包见 `CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md`。

`P1-W01 Session DTO/AIDL` 已完成：5 个 bounded DTO、`ICentralBrainSessionRuntime` V1、
`SessionContract`、JVM/Android Parcel 测试、独立 hash/checksum 门禁均已进入工程；既有 V1 checksum
未改变，Session Runtime Service 尚未发布。

`P1-W02 Plan/Node DTO/AIDL` 已完成：4 个 bounded DTO、11 类 node allowlist、`PlanContract` 的
DAG/重试/补偿/容量校验、JVM/Android Parcel 测试和独立 `plan-v1.sha256` 已进入工程；既有及
Session V1 checksum 未改变，Plan Compiler/Graph Runtime 尚未发布。

`P1-W03 Typed Event DTO/AIDL` 已完成：5 个 bounded DTO、23 类 event allowlist、独立
`ICentralBrainSessionEvents`/one-way callback V1、`EventContract` 的顺序/父链/脱敏/cursor/immutable
replay 校验、JVM/API 33 ARM64 Parcel 测试和 `events-v1.sha256` 已进入工程；Event Service/callback
publication/Room persistence 均关闭。

`P1-W04 Effect/Approval DTO 扩展` 已完成：4 个 bounded structured parcelable、typed scalar、完整 Effect
状态转换、approval plan/action/context/policy 绑定、Undo verified observation/compensation/TTL 绑定、
JVM/API 33 ARM64 Parcel 测试和 `effect-v1.sha256` 已进入工程；本包未新增 Binder interface，
Effect Service、approval response/grant、undo execution 和 Room persistence 均关闭。

`P1-W05 SDK facade v2` 已完成：UI 可通过无 Binder primitive 的 `ScenarioClient` 使用 Session/Event
V1；同一 Runtime Service 通过显式 action 发布两个 Binder，owner/capability、transient registry、
cursor replay、callback 去重和 Service rebind 恢复均已进入工程并通过 Android 13 ARM64 真机验证。

`P1-W06 Room v4 schema` 已完成：六类 Stage 2 entity、v3->v4 非破坏迁移、owner-scoped durable
Session/Event repository、事务回滚/索引计划门禁和 Android 13 Runtime 进程死亡恢复已进入工程。

`P1-W07 Contract v2 aggregate check` 已完成：机器可读 aggregate v2、SDK 常量/JVM regression 和单一
门禁已聚合 P1-W01..P1-W06 的 AIDL/hash、capability、稳定错误类别、payload/page/callback/latency、
Room v4 与 forbidden fallback。四组 V1 wire/hash 不变。Event V1 terminal cursor 限制保留，独立 Event
V2 terminal resume cursor + monotonic ACK 方案已冻结但未发布。

`P2-W01 canonical vehicle signal types` 已完成：Android Java 提供 12 项 VSS-style canonical path、四类
typed scalar、精确 unit/area、source/quality 和 receive-side monotonic freshness；JVM 与 Android 13/API 33
ARM64 debug probe 通过。该 schema 不读取 Vehicle/VHAL，AAOS/VENDOR 只表示来源类型，不构成 provider
activation 或 property mapping。

`P2-W02 vehicle capability catalog` 已完成：8 项 HVAC/Seat/Media/Nav capability 固定 readable/
writable/simulatable/productionAvailable/productionAuthorized、typed target range、area、risk、readback
path 和 required fresh signals。当前 production available/authorized 均为 false，范围是软件仿真合同，
不是 OEM 标定或车辆授权。

`P2-W03 VehicleDigitalTwinStore` 已完成：进程内 store 提供 thread-safe desired/reported 分离、全局
monotonic revision、TTL/quality、atomic filtered snapshot、stale/conflict rejection 和 reconciliation；
JVM 与 Android 13/API 33 ARM64 debug probe 通过。当前不持久化、不接 production Service/adapter，
不读取 Vehicle/VHAL。

`P2-W04 ContextSnapshotBuilder` 已完成：general/seat comfort/seat recline 固定 policy 基于同一 Twin
revision 和 Runtime state 构造 versioned/digested Context，显式输出 driving/safety/source mode、missing/
stale/conflict/trust report 和 restricted；JVM 与 Android 13/API 33 ARM64 debug probe 通过。当前
`productionTrusted=false` 且未接 production Service/provider。

`P2-W05 Scenario manifest/schema` 已完成：cold/fatigue/rest 三份 build-owned v1 asset、Gson strict
streaming parser、draft-2020-12 schema、SHA-256 sidecar、bounded template validator 和 invalid/duplicate
asset isolation 已进入工程并通过 JVM 与 Android 13/API 33 ARM64 assets probe。该 checksum 不是 artifact
密码学签名，catalog 未接 production Service，Resolver/Compiler/Graph/Effect 均未启用。

`P2-W06 DeterministicScenarioResolver` 已完成：显式 ID 优先、固定中英文 bounded alias、unknown/ambiguous
fail-closed、source/zone/Context/capability/PARKED_ONLY gate、accept/degrade/reject reason 和 deterministic
digest 已通过 JVM 与 Android 13/API 33 ARM64 debug probe。Resolver 不调用模型，未接 production Service。

`P2-W07 ScenarioPlanCompiler` 已完成：只消费已接受/降级的 Resolution 与同一 Context/capability snapshot，
复验 digest/version，编译 immutable typed DAG，精确裁剪 optional fallback，并验证 required verify、HIGH
approval 前驱和 moving/unknown unsafe branch absent。JVM 与 Android 13/API 33 ARM64 probe 已通过；Plan
仍不可执行，未接 production Service/Room/Graph/Effect。

`P2-W08 SimulatedVehicleAdapter base` 已完成：debug-only typed adapter、手动 clock、immutable fault profile、
有界幂等 record 和 delivery/readback 分离已通过 JVM、release-source compile 与 Android 13/API 33 ARM64
probe；production 不含/不注册，未接现有 Effect/Plan/Graph Runtime。

`P2-W09 Simulated HVAC adapter` 已完成：debug-only versioned typed absolute target、catalog-bound action/
area/range/step、adapter-owned desired/reported Twin、manual delay、timeout/failure/mismatch/idempotency 已通过
JVM、release-source compile 与 Android 13/API 33 ARM64 probe；production 不含/不注册，未接 Effect Runtime。

`P2-W10 Simulated Seat adapter` 已完成：debug-only heating/ventilation/recline typed target、adapter-owned
desired/reported Twin、recline admission+dispatch 双重 fresh Safety/occupancy/belt/approval gate、永久 race reject
和有界 progress observation 已通过 JVM、release-source compile 与 Android 13/API 33 ARM64 probe；production
不含/不注册，未接 Effect Runtime 或真实座椅接口。

`P2-W11 Simulated Media/Nav adapters` 已完成：debug-only typed playback state、digest-only synthetic POI/route
observation、可替换 simulation backend、delay/fault/mismatch/idempotency 和 external Activity/network/location
失败关闭已通过 JVM、release-source compile 与 Android 13/API 33 ARM64 probe；production 不含/不注册。

`P2-W12 Debug Context Controller` 已完成：debug-only AIDL/Service 通过 signature permission 与 calling
UID/package/current-signer capability 双层授权，typed driving/canonical signal、四 adapter fault、manual clock、
reset、128 条 digest-only audit 已通过 JVM、debug/release compile 与 Android 13/API 33 ARM64 Binder probe；
release 无 exported controller，production Context/vehicle/Graph/Effect 仍未接。

`P3-W01 AgentGraphRuntime state machine` 已完成：typed Plan admission/deep copy、合法 graph/node transition、
单 session FIFO、最多 8 个跨 session active run、manual-clock deadline、optional partial、required failure、
最多 256 条 retained digest event 已通过 JVM、debug/release compile 与 Android 13/API 33 ARM64 probe。
control-only registry 不调用 executor，Runtime/Room/Binder/Effect/model/vehicle/hardware 仍未接。

`P3-W02 Typed node executors` 已完成：11 类 Plan node exact input/output schema、只验证不调度的 registry、
7 类 debug deterministic executor、authority/trust gate、Effect/Compensation/unsupported fail-closed 已通过
JVM、debug/release compile 与 Android 13/API 33 ARM64 probe。Graph/Room/Binder/Effect/model/hardware 仍未接。

`P3-W03 CheckpointSerializer` 已完成：allowlisted type/version、exact registered DTO、immutable primitive tree、
canonical JSON、64 KiB/8 层/1024 token gate、SHA-256 和 malformed/unknown/oversize/security corpus 已通过 JVM、
debug/release compile 与 Android 13/API 33 ARM64 probe。Graph/Room/Binder/recovery/Effect/model/hardware 仍未接。

`P3-W04 Retry/Timeout policy` 已完成：node/plan deadline、attempt 1..3、deterministic bounded jitter、
Effect idempotency/reconcile-before-retry 已通过 JVM、debug/release compile 与 Android 13/API 33 ARM64 probe。
策略尚未接 Graph/Effect dispatch。

`P3-W05 Durable approval interrupt` 已完成：checkpoint-ready record 绑定 caller/session/plan/node/action/plan/
context/policy/Safety digest、expiry 与 trusted authority decision；resume 重新检查 context/policy/capability 和
Safety State。当前 `approval_interrupt_persistence_wired=false`、`approval_grant_service_published=false`，未接
Graph/Room/Binder/Effect/model/hardware。

`P3-W06 EffectCoordinator` 已完成：最多 16 项的 immutable Effect batch、同 action digest 绑定、依赖拓扑、
资源冲突 wave、capability+area+profile 精确 registry、prepare-all/required-failure zero-dispatch、optional degrade 与
每项 typed observation 已通过 JVM、debug/release compile 和 Android 13/API 33 ARM64 probe。APPLIED 只到 DELIVERED；
Graph/Room/outbox/Binder/production adapter/readback/hardware 仍未接。

`P3-W07 Effect verification/reconciliation` 已完成：五种 typed policy、target/composite digest、
DELIVERED/APPLIED/VERIFIED 分层、UNKNOWN bounded next-reconcile time、immutable Twin readback、NOT_APPLIED confirmation
和 VERIFIED no-query dedup 已通过 JVM、debug/release compile 与 Android 13/API 33 ARM64 probe。Coordinator/Graph/Room/
Binder/scheduler/production readback/hardware 仍未接。

`P3-W08 Compensation/Undo` 已完成：显式 reversible policy、VALID before snapshot、绝对 target、逆依赖 wave、
digest/TTL handle、Context/Policy/Governance/Safety 复验、新 governed task 与 process-local idempotent replay 已通过 JVM、
debug/release compile 和 Android 13/API 33 ARM64 probe。原 VERIFIED observation 保持不可变；Graph/Room/Binder/outbox/
production authority/adapter/vehicle readback 均未接，production 失败关闭。

`P3-W09 Restart recovery` 已完成：新增 fail-closed `GraphRestartReconciler` 与 Room v4
`DurableGraphRecoveryRepository`，覆盖 WAITING/EXECUTING/UNKNOWN、checkpoint mismatch、Effect/approval/undo 重验、
process death、幂等 reopen 和 exactly-once digest audit。三阶段 Android 13/API 33 ARM64 probe 在两次 `force-stop`
后确认 side-effect count 为 0。当前 `graph_restart_runtime_wired=false`、
`agent_graph_runtime_persistence_wired=false`、`production_effect_dispatch_enabled=false`，不得把 repository/probe
表述为 production Graph recovery activation；该边界登记为 `DEV-050`。

`P4-W01 Bridge session/event API migration` 已完成：Client2 主桥接接口迁移为 `openSession(...)` +
`SessionConnection`，callback 暴露 typed handle/snapshot/event/replay/overflow/close/error；旧 `submit(...)` 与
三个文本 callback 只保留默认兼容层。12 个既有两段 UI alias 通过固定 allowlist 映射到 canonical Session ID，
冻结 Session V1 校验不放宽。Android 13/API 33 ARM64 已验证 open/snapshot/sequence-1 event/replay、兼容订阅替换、
Runtime process-death 自动重连、重复事件抑制和 Client2 process restart。场景执行、Graph/Effect dispatch 与硬件仍未接。

`P4-W02 Cockpit HMI state/reducer/reconnect` 已完成：旧 Smali controller 已删除，maintained Java coordinator 是唯一
View/Session lifecycle owner；typed callback 只通过 immutable reducer 更新 HMI。app-private checkpoint 仅保存 handle、
cursor、sequence、alias 和 panel 状态，不保存 user/model text。Android 13 ARM64 已验证 hide 后 Client2 process restart、
existing Session resume/replay、hidden state restore 和菜单重开。

`P4-W03 Intent-first four-stage overlay shell` 已完成：12 按钮主测试台已收敛为四项自然场景输入和
“意图/计划/执行/结果”可观察 shell；Header 固定显示 source/driving/connection，HVAC/Seat 只作为次级详情抽屉。
1920x1080 实体 Android 13 ARM64 已验证 `(1264,160)-(1888,1048)` 安全框、60% 浅灰材质、阶段切换、抽屉和恢复。

`P4-W04 HVAC control surface` 已完成：power/zone/temperature/fan/AUTO/A-C/SYNC/airflow/preset、immutable
desired/request/evidence state、300 ms debounce 和 `scene.manual.hvac.adjust.v1` governed Session 已进入 Client2。
实体 Android 13/API 33 ARM64 验证三次快速温度输入只产生一个 Session；desired 到 24.0 C，但 reported/source/
quality 继续显示 unavailable/no evidence，未 dispatch Adapter/Effect。冻结 Session V1 的 bounded `HVAC1` 兼容承载
由 `DEV-054` 跟踪。重复验收还修复了显式 Session replacement 的 Service 重建竞态：先发起 replacement bind，
再 cancel/close 旧 Session，防止短暂无绑定窗口和 active-session 容量泄漏。

`P4-W05 Seat control surface` 已完成：四座区、heat/vent 0-3 互斥、massage、0-60 degree recline、
upright/comfort/rest preset、immutable desired/request/safety/evidence state、300 ms debounce 和
`scene.manual.seat.adjust.v1` governed Session 已进入 Client2。Context 未接时保持 UNKNOWN_RESTRICTED；驾驶席靠背和
rest preset 不改变 desired、不创建 Session。host policy 证明 PARKED+OCCUPIED+UNBELTED 的 rest 只进入
WAITING_APPROVAL，仍不 dispatch。冻结 Session V1 的 bounded `SEAT1`/approval 缺口由 `DEV-055` 跟踪。

`P4-W06 Plan/effect execution timeline` 已完成：Client2 以 reducer-owned immutable projection 持续显示 Intent、Context、
Plan、Policy、Graph、Effect、Readback 七阶段，并保留最多八条脱敏 typed-event 轨迹。Android 13/API 33 ARM64 已验证
当前 Runtime 只到 Session admission；Plan/Graph/Effect/readback 分别保持 NOT PUBLISHED/NOT WIRED/NOT DISPATCHED/
UNAVAILABLE，不把 desired 或 assistant text 表示为车辆执行成功。

`P4-W07 Approval/partial/retry/undo UX` 已完成 application-layer 投影。

`P4-W08 Driving restriction renderer` 已完成：新增 Android View-independent `DrivingUxPolicy` 与
`PanelPresentationMode`。只有可信、已观测且 revision 有效的 PARKED Context 进入完整呈现；MOVING、UNKNOWN、缺失或
不可信 Context 都进入受限模式，隐藏长文本、禁用 HVAC/Seat 参数编辑和高风险休息场景。呈现模式始终返回
`isEffectAuthorizationSource=false`，不能替代 Runtime Safety/Policy。当前实体设备没有可信 Context provider，故本轮
只验证受限模式；PARKED 完整模式实体复测由 `P4-W09 Engineer simulation drawer` 提供受保护输入后执行。

`P4-W09 Engineer simulation drawer` 已完成：Client2 通过 signature permission、caller capability 和 AIDL version/hash
连接 Runtime debug Controller；工程入口连接前隐藏，命令成功且 revision 严格递增后才投影 PARKED/MOVING/UNKNOWN、
occupancy/belt 和 HVAC/Seat fault。Android 13/API 33 ARM64 已覆盖完整矩阵、reset 失败关闭和 release Service absent。
SIMULATED projection 不是 production Context/Safety/Effect authority。P4-W10 已完成 Scenario/manual-control synchronization；
P4-W11 已完成 Accessibility/display matrix；P4-W12 已完成 application aggregate acceptance。P5-W01..W10 已完成 Tool
合同、Registry/Resolver、rule intersection、built-in executor boundary、Skill package static verifier，以及 Working/Profile/
Episodic Memory process-local foundation、metadata-only Context Budget 与 process-local Memory consent HMI/API；
`hmi_d4_demo_control_loop_complete=false`，自动 Plan/Effect/approval/
undo/readback 仍未发布。

P5-W01 Tool manifest/schema 已完成：immutable identity/owner/capability/risk/timeout/idempotency/health、bounded scalar
input/output、canonical contract digest 与 exact-class validator 已进入 Runtime main source；JVM、debug/release compile
完成。Android 13 ARM64 probe 已实现，但当前 Windows ADB transport 不可用，实体执行待复测。P5-W02 已完成 pure-Java
Registry/Resolver，P5-W03 已完成 six-rule deterministic intersection，P5-W04 已完成 in-process built-in executor boundary，
P5-W05 已完成 static Skill package verifier，P5-W06 已完成 process-local `WorkingMemoryStore`，P5-W07 已完成 consent/field/
user-seat/encryption-owner-gated `ProfileMemoryStore` contract，P5-W08 已完成 typed summary/result-only `EpisodicMemoryStore`，
P5-W09 已完成 decision-only `ContextBudgetManager`，P5-W10 已完成 fixed-source/retained-switch/preference-clear/driving-gate
`MemoryConsentController` 与 debug interactive HMI；Runtime/Graph/model publication、production Memory authority/repository mutation
均未发布，下一工作包为 P6-W01 EventBroker interface/in-process implementation。

## 7. 近期进展

### 2026-07-15

- Client2 面板改为底部导航触发；默认隐藏，二次点击或面板外点击关闭。
- Android 13 ARM64 物理设备通过 UI、typed Binder、回复和 Runtime/Client2 recovery 验收。
- Stage 2 开源/行业调研、产品 UX、开发 backlog 和完整软件设计冻结。

### 2026-07-16

- 删除 Python backend、REST contract、Linux Python binding/CLI/daemon、旧 Console、Linux
  systemd 样例及相关 smoke/checker/docs。
- README、软件架构、接口、NPU、Driver/HAL、Safety、交付、偏差、问题和路线图改为 Android-only。
- 新增 Python 原型退役门禁，保留 Android 模型/NPU/Driver-HAL 真实硬件接口。
- 退役门禁、NPU 接口、交付文档、Stage 2 设计和 Android Runtime 聚合门禁全部通过。
- README 新增 GitHub source-of-truth、完整项目发布边界和已开发/未开发进度总表；pre-push 与
  Actions 扩展为覆盖全部 Central Brain 正式源码、Client2 patch、工程文档和工具。
- 冻结 Client2 中控 HVAC/Seat 演示闭环：意图驱动四阶段、手动/AI 统一 Effect 链、SIMULATED 标识、
  desired/reported、partial/undo/recovery，并将 P4 扩展为 12 个最小工作包。
- 基于现有 Client2 车模和右侧悬浮面板交付可点击“意图/计划/执行/结果”原型、四张 1920x1080
  PNG、Intent -> Context -> Plan -> Policy -> Effect -> Readback 可观察链、Android 映射和静态门禁；
  仅完成 HMI-D0，HMI-D1/APK 实现仍未开始。
- 将 HMI-D0 Panel 收敛为 `(1264,160)-(1888,1048)` 1920x1080 安全框，主材质由 0.91 实色感
  调整为 0.60 半透明浅灰玻璃，并修复 Windows Chrome 连续渲染 profile 隔离。
- GitHub 默认分支 `main` 是权威进度基线；开发分支合并后不得单独保留状态结论。

### 2026-07-17

- 完成 `P1-W01` Session contract V1：5 个有界 AIDL DTO、独立 Session Binder 合同、Java 边界
  校验、JVM/Android Parcel 测试和 checksum 门禁。
- Android 13/API 33 ARM64 物理控制器通过 5 个 DTO Parcel round-trip、oversize 和 unknown-version
  reject；临时 test APK 验证后卸载，该证据不访问车辆/NPU。
- 保持 `session_runtime_service_published=false`、车辆/NPU/Driver-HAL 未接入；SDK reconnect 验收按
  正确所有权保留到 `P1-W05`。
- 完成 `P1-W02` Plan/Node contract V1：4 个有界 AIDL DTO、11 类节点 allowlist、DAG/补偿/重试
  校验、独立 hash/checksum 门禁。
- Android 13/API 33 ARM64 物理控制器通过 Plan Parcel round-trip、cycle 和 unknown-type reject；
  临时 test APK 验证后卸载，未访问车辆/NPU。
- 保持 `plan_runtime_published=false`；Compiler/完整 Graph Validator 属于 P2-W07，durable 执行属于 P3。
- 完成 `P1-W03` Event contract V1：5 个有界 DTO、23 类 event allowlist、独立 Event/callback V1、
  顺序/父链/脱敏/cursor/immutable replay 校验和独立 hash/checksum 门禁。
- Android 13/API 33 ARM64 物理控制器通过 Event Parcel round-trip、ordering/parent/redaction/cursor replay
  验证；临时 test APK 验证后卸载，未访问车辆/NPU。
- 保持 `event_runtime_service_published=false`、`event_callback_service_published=false`；Service/Room
  分别属于 P1-W05/P1-W06。
- 完成 `P1-W04` Effect/Approval contract V1：4 个有界 DTO、typed scalar、Effect transition/retry/
  terminal validator、approval stale/expiry 和 undo expiry/context reject、独立 hash/checksum 门禁。
- Android 13/API 33 ARM64 物理控制器通过 Effect DTO Parcel、完整状态链、illegal terminal、stale
  approval 和 expired undo 验证；临时 test APK 验证后卸载，未访问车辆/NPU。
- 保持 `effect_runtime_service_published=false`、`approval_response_service_published=false`、
  `undo_service_published=false`；下一工作包为 `P1-W05 SDK facade v2`。
- 完成 `P1-W05 SDK facade v2`：新增无 Binder primitive 的 Scenario/Session facade、内部双 action
  transport、Runtime Session/Event Binder publication、owner-scoped transient registry 和七项 capability。
- JVM 覆盖 fake transport、callback race、protocol failure、close/reconnect 幂等、owner/capacity/cursor；
  Android 13/API 33 ARM64 真实 Binder 验证 active session rebind/resubscribe 与重复 replay 去重。
- 当前 `sdk_facade_v2_available=true`、`session_runtime_service_published=true`、
  `event_runtime_service_published=true`、`event_callback_service_published=true`；但
  `scenario_execution_enabled=false`，下一工作包为 `P1-W06 Room v4 schema`。
- 完成 `P1-W06 Room v4 schema`：新增 Session/Plan/Node/Event/Observation/Compensation 六类 entity，
  `runtime_session` legacy row 迁移、13-table schema JSON、FK/unique index 和 8 KiB canonical payload 边界。
- P1-W05 Session/Event production endpoint 已切换到 `DurableSessionRegistry`；Android 13/API 33 ARM64
  通过 migration fixture、crash transaction rollback、owner query index、Runtime process-death rehydration、
  event replay 和 terminal cancel 幂等验证。
- 当前 `room_schema_version=4`、`session_runtime_persistence_wired=true`、
  `session_runtime_process_death_rehydration=true`；Plan/Effect/Scenario execution 仍关闭，下一工作包为
  `P1-W07 Contract v2 aggregate check`。
- 完成 `P1-W07 Contract v2 aggregate check`：聚合 P1 frozen wire/capability/error/bounds/Room v4，
  Android 13 ARM64 aggregate instrumentation 通过；Event V2 cursor/ACK 仅冻结设计，未发布。
- 完成 `P2-W01 canonical vehicle signal types`：12 项 canonical path、typed scalar、unit/area、source/
  quality/freshness 校验和 Android 13 ARM64 debug probe 通过；provider/property mapping/hardware 仍关闭，
  下一工作包为 `P2-W02 Vehicle capability catalog`。
- 完成 `P2-W02 vehicle capability catalog`：8 项 HVAC/Seat/Media/Nav capability、typed range、readback/
  safety dependency 和 fail-closed production flags通过 JVM/API 33 ARM64 probe；authorized count 为 0，
  下一工作包为 `P2-W03 VehicleDigitalTwinStore`。
- 完成 `P2-W03 VehicleDigitalTwinStore`：desired/reported 分离、monotonic revision、TTL/quality、atomic
  snapshot、stale/conflict rejection 和 reconciliation 通过 JVM/API 33 ARM64 probe；persistence/adapter/
  property mapping/hardware 保持关闭，下一工作包为 `P2-W04 ContextSnapshotBuilder`。
- 完成 `P2-W04 ContextSnapshotBuilder`：固定 field policy、同 Twin revision、driving/safety 派生、
  freshness/trust/restricted report 和 deterministic digest 通过 JVM/API 33 ARM64 probe；production trusted/
  wiring/provider/hardware 保持 false，下一工作包为 `P2-W05 Scenario manifest/schema`。
- 完成 `P2-W05 Scenario manifest/schema`：cold/fatigue/rest build-owned v1 assets、strict parser/schema、
  checksum、bounded template/DAG/capability/risk/fallback/UI 校验与 invalid isolation 通过 JVM/API 33 ARM64
  probe；artifact crypto/trust/runtime/graph/effect/hardware 保持 false，下一工作包为
  `P2-W06 DeterministicScenarioResolver`。
- 完成 `P2-W06 DeterministicScenarioResolver`：显式 ID 与固定中英文文本规则、Context/source/zone/
  capability/PARKED_ONLY gate、unknown/ambiguous fail-closed、accept/degrade/reject 和 deterministic digest
  通过 JVM/API 33 ARM64 probe；model/runtime/compiler/graph/effect/hardware 保持 false，下一工作包为
  `P2-W07 ScenarioPlanCompiler`。
- 完成 `P2-W07 ScenarioPlanCompiler`：digest-bound immutable typed DAG、optional fallback 裁剪、required
  verify/HIGH approval/moving seat semantic validation 通过 JVM/API 33 ARM64 probe；Runtime/Graph/Effect/
  hardware 保持 false，下一工作包为 `P2-W08 SimulatedVehicleAdapter base`。
- 完成 `P2-W08 SimulatedVehicleAdapter base`：debug-only typed adapter、manual clock、bounded fault matrix、
  idempotency 与 delivery/readback 分离通过 JVM/release compile/API 33 ARM64 probe；production registration/
  Runtime/Effect/hardware 保持 false，下一工作包为 `P2-W09 Simulated HVAC adapter`。
- 完成 `P2-W09 Simulated HVAC adapter`：typed absolute target、catalog area/range/step、desired/reported
  Twin、manual delay、timeout/retry/terminal/mismatch/idempotency 通过 JVM/release compile/API 33 ARM64 probe；
  production registration/Runtime/Effect/hardware 保持 false，下一工作包为 `P2-W10 Simulated Seat adapter`。
- 完成 `P2-W10 Simulated Seat adapter`：typed heat/vent/recline、admission+dispatch fresh Safety/occupancy/
  belt/approval gate、race 永久拒绝、progress 与 isolated desired/reported Twin 通过 JVM/release compile/API 33
  ARM64 probe；production registration/Runtime/Effect/hardware 保持 false，下一工作包为 P2-W11 Media/Nav。
- 完成 `P2-W11 Simulated Media/Nav adapters`：typed player state、digest-only synthetic POI/route、replaceable
  backend、delay/fault/mismatch/idempotency 与 no Activity/network/location 通过 JVM/release compile/API 33 ARM64
  probe；production registration/Runtime/Effect/hardware 保持 false，下一工作包为 P2-W12 Debug Controller。
- 完成 `P2-W12 Debug Context Controller`：debug-only AIDL、signature+capability、typed state/signal/fault/clock/
  reset、bounded digest-only audit 通过 JVM/debug/release/API 33 ARM64 Binder probe；production exported/Runtime/
  vehicle provider/hardware 保持 false，下一工作包为 P3-W01 AgentGraphRuntime。
- 完成 `P3-W01 AgentGraphRuntime state machine`：process-local typed Plan deep copy、合法 graph/node state、
  单 session FIFO、跨 session 有界 slot、manual deadline、partial/failure 与 bounded digest event 通过 JVM/
  debug/release/API 33 ARM64 probe；executor dispatch/Room/Binder/Effect/model/hardware 保持 false，下一工作包
  为 P3-W02 Typed node executors。
- 完成 `P3-W02 Typed node executors`：11 类 exact schema、7 类 debug deterministic executor、exact-class、
  authority/trust gate、Effect/Compensation/unsupported fail-closed 通过 JVM/debug/release/API 33 ARM64 probe；
  Graph dispatch/Room/Binder/Effect/model/hardware 保持 false，下一工作包为 P3-W03 CheckpointSerializer。
- 完成 `P3-W03 CheckpointSerializer`：registered DTO、canonical primitive JSON、type/version/digest、64 KiB/8 层/
  token gate 和 security corpus 通过 JVM/debug/release/API 33 ARM64 probe；Graph/Room/recovery/Effect/model/hardware
  保持 false，下一工作包为 P3-W04 Retry/Timeout policy。
- 完成 `P3-W04 Retry/Timeout policy`：monotonic node/plan deadline、bounded attempts、deterministic SHA-256 jitter、
  Effect idempotency + reconcile-before-retry 通过 JVM/debug/release/API 33 ARM64 probe；Graph/Effect wiring 保持
  false，下一工作包为 P3-W05 Durable approval interrupt。
- 完成 `P3-W05 Durable approval interrupt`：approval binding/expiry/trusted decision、registered checkpoint codec、
  owner/plan/context/policy/capability/Safety resume revalidation 与 API 33 ARM64 probe；同时修正 checkpoint envelope
  current epoch 超过 primitive integer bound 的缺陷。Room/Graph/Binder/grant Service/Effect/hardware 保持 false，
  下一工作包为 P3-W06 EffectCoordinator。
- 完成 `P3-W06 EffectCoordinator`：immutable typed Effect batch、dependency/resource wave、exact profile registry、
  prepare-all/required zero-dispatch、optional degrade 与独立 typed observation 通过 JVM/debug/release/API 33 ARM64
  probe；Graph/Room/outbox/Binder/production adapter/readback/hardware 保持 false，下一工作包为 P3-W07
  Verification + reconciliation。
- 完成 `P3-W07 Effect verification/reconciliation`：五种 typed policy、target/composite digest、
  DELIVERED/APPLIED/VERIFIED 分层、UNKNOWN timed reconcile、Twin readback、status regression fail-closed 与 VERIFIED
  no-query dedup 通过 JVM/debug/release/API 33 ARM64 probe；Coordinator/Graph/Room/Binder/scheduler/production readback/
  hardware 保持 false，下一工作包为 P3-W08 Compensation/Undo。
- 完成 `P3-W08 Compensation/Undo`：显式 reversible allowlist、VALID before snapshot、absolute target、reverse wave、
  TTL/digest handle、Context/Policy/Governance/Safety 复验、新 governed task 与 idempotent replay 通过 JVM、
  debug/release compile 和 Android 13/API 33 ARM64 probe；原 VERIFIED Effect 不变，Graph/Room/Binder/outbox/production
  authority/adapter/hardware 保持 false，下一工作包为 P3-W09 Restart recovery。
- 完成 `P3-W09 Restart recovery`：WAITING/EXECUTING/UNKNOWN fail-closed reducer、Room v4 bounded repository、
  checkpoint mismatch STUCK、Effect/approval/undo reconcile directive、process-death/reopen 与 exactly-once audit 通过 JVM、
  debug/release 和 Android 13/API 33 ARM64 三阶段 probe；Runtime/Binder/executor/effect dispatch/hardware 保持 false；
  后续 P4-W01 Bridge session/event API migration 已完成。
- 完成 `P4-W01 Bridge session/event API migration`：Client2 主接口改为 typed Session/Event stream，旧 Smali
  `submit` 描述符仅作兼容；12 项 UI alias 显式映射 canonical Session ID。APK build/static gate、Android 13 ARM64
  happy path 与 Runtime/Client2 process-death recovery matrix 通过；场景执行和中控 reducer 仍为 false，下一工作包为
  P4-W02 Cockpit HMI state/reducer/reconnect。
- 完成 `P4-W02 Cockpit HMI state/reducer/reconnect`：新增 immutable HMI state、唯一 reducer、Java lifecycle
  coordinator 和 existing Session resume API；删除旧 Smali controller。host-JVM、APK build、Android 13 ARM64
  Runtime death 及 Client2 process restart/hidden-state restore 通过；四阶段 shell/HVAC/Seat/执行闭环仍为 false，
  下一工作包为 P4-W03。
- 完成 `P4-W03 Intent-first four-stage overlay shell`：将主界面收敛为四项自然场景，新增四阶段 renderer、
  source/driving/connection Header、HVAC/Seat 次级抽屉和 1920x1080/alpha 0.60 资源；host、APK build、
  Android 13 ARM64 happy/recovery 均通过。HVAC/Seat control、scenario/Graph/Effect dispatch 和真实 readback 仍为 false，
  下一工作包为 P4-W04。
- 完成 `P4-W04 HVAC control surface`：新增 immutable HVAC target/state、完整空调控件、300 ms debounce、
  `manual.hvac -> scene.manual.hvac.adjust.v1` governed Session 和 desired/reported/source/quality/effect 分层；host、APK、
  static gate 与 Android 13 ARM64 实体测试通过。Session admission 仅为 REQUESTED，readback/Effect/Adapter/硬件保持
  unavailable/not-dispatched；下一工作包为 P4-W05 Seat control surface。
- 完成 `P4-W05 Seat control surface`：新增 immutable Seat target/state、安全上下文与 decision、四区 heat/vent/massage/
  recline/preset 控件、300 ms debounce 和 `manual.seat -> scene.manual.seat.adjust.v1` governed Session。heat/vent 互斥，
  UNKNOWN_RESTRICTED 驾驶席靠背不改变 desired、不创建 Session；reported/Effect/Adapter/硬件保持 unavailable/not-dispatched。
  下一工作包为 P4-W06 Plan/effect execution timeline。
- 完成 `P4-W06 Plan/effect execution timeline`：新增 immutable 七阶段 projection、八条 bounded typed-event trace、
  Media STOP/Navigation CANCEL 状态和 1920x1080 可滚动执行页。host、APK、static gate 与 Android 13/API 33 ARM64
  实体验证通过；当前 Runtime 无 Plan/Effect publication，页面保持 NOT PUBLISHED/NOT WIRED/NOT DISPATCHED/
  UNAVAILABLE。
- 完成 `P4-W07 Approval/partial/retry/undo UX`：新增 immutable `CockpitRecoveryState`、审批状态/原因/目标/过期时间
  fail-closed 显示、VERIFIED/FAILED/INCONCLUSIVE 证据统计、Session partial aggregate 和 compensation projection。
  approve/reject/retry/undo 在对应 Binder/typed detail 未发布时保持 visible+disabled；outside dismiss 保留 Session/recovery state。
- 完成 `P4-W08 Driving restriction renderer`：新增 immutable presentation mode 和纯 Java driving policy；UNKNOWN/MOVING/
  unavailable/untrusted Context 隐藏长详情、禁用参数编辑及高风险休息场景，只有可信 PARKED 恢复完整呈现。UI mode
  不授予 Effect 权限；当前实体默认受限，PARKED 实体路径由 P4-W09 的受保护工程师抽屉复测。
- 完成 `P4-W09 Engineer simulation drawer`：新增 debug-only Binder client、immutable engineer state、hidden-until-connected
  工程抽屉和 revisioned reducer projection；signature/capability/protocol、三态 Context、occupancy/belt、fault matrix、
  reset 与 release Service absent 已通过 Android 13/API 33 ARM64。该入口不接 production Context/Effect/Vehicle；下一
  工作包为 P4-W10 Scenario/manual-control synchronization。

## 8. 当前门禁

必须通过：

```bash
bash tools/check_central_brain_python_prototype_retirement.sh
bash tools/check_central_brain_github_repository_completeness.sh
bash tools/check_central_brain_npu_interface.sh
bash tools/check_central_brain_virtualization_docs.sh
bash tools/check_central_brain_cockpit_hmi_design.sh
bash tools/check_central_brain_aios_stage2_design.sh
bash tools/check_central_brain_android_plan_contract.sh
bash tools/check_central_brain_android_event_contract.sh
bash tools/check_central_brain_android_effect_contract.sh
bash tools/check_central_brain_android_sdk_facade.sh
bash tools/check_central_brain_android_vehicle_signal_schema.sh
bash tools/check_central_brain_android_vehicle_capability_catalog.sh
bash tools/check_central_brain_android_scenario_manifest.sh
bash tools/check_central_brain_android_scenario_resolver.sh
bash tools/check_central_brain_android_debug_simulation_controller.sh
bash tools/check_central_brain_android_agent_graph_runtime.sh
bash tools/check_central_brain_android_effect_coordinator.sh
bash tools/check_central_brain_android_effect_verification.sh
bash tools/check_central_brain_android_runtime_evolution.sh
```

当前状态：

```text
python_prototype_runtime_maintained=false
github_source_of_truth=true
github_sync_required=true
maintained_project_files_synced=true
github_homepage_architecture_current=true
design_baseline_complete=true
aios_intent_orchestration_ux_ready=true
cockpit_hmi_design_mockups_ready=true
cockpit_hmi_1920x1080_safe_frame_verified=true
cockpit_hmi_translucent_material_ready=true
session_contract_v1_defined=true
session_parcel_physical_android13_arm64_verified=true
sdk_facade_v2_available=true
session_runtime_service_published=true
active_session_reconnect_resubscribe_verified=true
session_runtime_transient_registry=false
room_schema_version=4
session_runtime_persistence_wired=true
session_runtime_process_death_rehydration=true
runtime_contract_v2_defined=true
runtime_contract_v2_verified=true
runtime_contract_v2_physical_android13_arm64_verified=true
frozen_v1_hashes_unchanged=true
vehicle_signal_schema_defined=true
vehicle_signal_path_allowlist_count=12
vehicle_signal_schema_android13_arm64_verified=true
vehicle_signal_provider_wired=false
vehicle_property_mapping_configured=false
vehicle_capability_catalog_defined=true
vehicle_capability_count=8
vehicle_capability_catalog_android13_arm64_verified=true
vehicle_production_capability_authorized_count=0
vehicle_capability_adapter_registry_wired=false
scenario_manifest_schema_version=1
scenario_catalog_count=3
scenario_manifest_android13_arm64_verified=true
scenario_manifest_artifact_crypto_verified=false
scenario_catalog_production_trusted=false
scenario_resolver_defined=true
scenario_resolution_schema_version=1
scenario_resolver_android13_arm64_verified=true
scenario_resolver_model_invoked=false
scenario_resolver_runtime_wired=false
scenario_compiler_wired=false
scenario_runtime_wired=false
scenario_graph_execution_enabled=false
debug_simulation_controller_defined=true
debug_simulation_controller_aidl_version=1
debug_simulation_controller_signature_permission_enforced=true
debug_simulation_controller_capability_enforced=true
debug_simulation_controller_android13_arm64_verified=true
debug_simulation_controller_debug_only=true
debug_simulation_controller_release_source_absent=true
debug_simulation_controller_production_exported=false
debug_simulation_controller_runtime_wired=false
agent_graph_runtime_defined=true
agent_graph_state_machine_verified=true
agent_graph_same_session_fifo_verified=true
agent_graph_cross_session_bounded_verified=true
agent_graph_partial_terminal_verified=true
agent_graph_deadline_verified=true
agent_graph_compensation_fail_closed_verified=true
agent_graph_event_projection_bounded=true
agent_graph_android13_arm64_verified=true
agent_graph_executor_dispatch_enabled=false
agent_graph_runtime_persistence_wired=false
agent_graph_runtime_binder_published=false
agent_graph_runtime_production_wired=false
typed_node_executor_contract_defined=true
typed_node_executor_schema_count=11
typed_node_executor_debug_count=7
typed_node_executor_exact_class_verified=true
typed_node_executor_effect_fail_closed_verified=true
typed_node_executor_unsupported_fail_closed_verified=true
typed_node_executor_android13_arm64_verified=true
typed_node_executor_graph_dispatch_enabled=false
typed_node_executor_production_wired=false
checkpoint_serializer_defined=true
checkpoint_serializer_registered_dto_verified=true
checkpoint_serializer_canonical_digest_verified=true
checkpoint_serializer_size_depth_limit_verified=true
checkpoint_serializer_security_corpus_verified=true
checkpoint_serializer_android13_arm64_verified=true
checkpoint_serializer_java_serialization_enabled=false
node_retry_policy_defined=true
node_timeout_policy_defined=true
backoff_deterministic_bounded_verified=true
timeout_deadline_clamp_verified=true
retry_attempt_budget_verified=true
effect_idempotency_reconcile_gate_verified=true
retry_deadline_fail_closed_verified=true
retry_timeout_policy_android13_arm64_verified=true
retry_timeout_policy_runtime_wired=false
effect_batch_defined=true
effect_dependency_plan_verified=true
effect_resource_conflict_serialized=true
effect_adapter_registry_profile_isolation_verified=true
effect_prepare_all_required_verified=true
effect_optional_degradation_verified=true
effect_independent_observation_verified=true
effect_coordinator_android13_arm64_verified=true
effect_coordinator_graph_wired=false
effect_coordinator_persistence_wired=false
production_effect_adapter_registered=false
production_effect_dispatch_enabled=false
effect_verification_reconciliation_wired=false
effect_verifier_defined=true
effect_verification_policies_verified=true
effect_state_separation_verified=true
effect_unknown_reconciliation_verified=true
effect_verified_redispatch_blocked=true
effect_production_readback_fail_closed=true
effect_verification_android13_arm64_verified=true
effect_verification_reconciliation_runtime_wired=false
effect_verification_scheduler_wired=false
effect_verification_persistence_wired=false
effect_verification_production_readback_wired=false
effect_verification_graph_wired=false
compensation_planner_defined=true
compensation_absolute_before_verified=true
compensation_reverse_dependency_verified=true
compensation_irreversible_rejected=true
undo_ttl_governance_verified=true
undo_new_governed_task_verified=true
undo_idempotent_replay_verified=true
undo_production_fail_closed=true
compensation_undo_android13_arm64_verified=true
compensation_undo_runtime_wired=false
compensation_undo_persistence_wired=false
undo_binder_service_published=false
compensation_dispatch_enabled=false
graph_restart_reconciler_defined=true
graph_restart_room_v4_repository_verified=true
graph_restart_waiting_recovered=true
graph_restart_executing_reconciled=true
graph_restart_unknown_effect_reconciled=true
graph_restart_approval_undo_revalidation_verified=true
graph_restart_checkpoint_mismatch_stuck=true
graph_restart_continue_after_revalidate_verified=true
graph_restart_process_death_verified=true
graph_restart_idempotent_reopen_verified=true
graph_restart_audit_exactly_once_verified=true
graph_restart_historical_digest_replay_verified=true
graph_restart_side_effect_count=0
graph_restart_android13_arm64_verified=true
graph_restart_repository_implementation_available=true
graph_restart_runtime_wired=false
graph_restart_binder_published=false
graph_restart_executor_dispatch_enabled=false
graph_restart_effect_dispatch_enabled=false
graph_restart_production_wired=false
agent_graph_runtime_persistence_wired=false
production_effect_dispatch_enabled=false
client2_session_event_primary_api=true
client2_session_event_typed_callback=true
client2_legacy_submit_compatibility=true
client2_scenario_alias_map_count=14
client2_session_snapshot_verified=true
client2_session_event_sequence_verified=true
client2_session_reconnect_replay_verified=true
client2_session_duplicate_event_suppressed=true
client2_session_android13_arm64_verified=true
cockpit_hmi_state_reducer_implemented=true
cockpit_hmi_state_immutable=true
cockpit_hmi_lifecycle_owner_java=true
client2_legacy_smali_controller_retired=true
client2_hmi_session_replacement_verified=true
client2_hmi_checkpoint_resume_verified=true
client2_hmi_hidden_state_recreation_verified=true
client2_hmi_checkpoint_text_persisted=false
legacy_text_callback_authoritative=false
cockpit_hmi_four_stage_shell_implemented=true
cockpit_hmi_intent_first_primary=true
cockpit_hmi_safe_frame_1920x1080_verified=true
cockpit_hmi_device_drawer_scaffolded=true
cockpit_hvac_surface_implemented=true
cockpit_hvac_reducer_owned=true
cockpit_hvac_debounce_ms=300
cockpit_hvac_governed_manual_session=true
cockpit_hvac_reported_readback_available=false
hvac_manual_typed_parameter_field=false
cockpit_seat_surface_implemented=true
cockpit_seat_reducer_owned=true
cockpit_seat_debounce_ms=300
cockpit_seat_governed_manual_session=true
cockpit_seat_heat_vent_mutex_verified=true
cockpit_seat_unknown_restricted_fail_closed=true
cockpit_seat_reported_readback_available=false
seat_manual_typed_parameter_field=false
cockpit_execution_timeline_implemented=true
cockpit_execution_timeline_reducer_owned=true
cockpit_execution_typed_event_projection=true
cockpit_execution_trace_capacity=8
cockpit_execution_plan_published=false
cockpit_execution_effect_dispatch_enabled=false
cockpit_execution_readback_available=false
cockpit_recovery_state_reducer_owned=true
cockpit_approval_details_fail_closed=true
cockpit_partial_outcome_projection=true
cockpit_compensation_projection=true
cockpit_approval_response_service_published=false
cockpit_retry_service_published=false
cockpit_undo_service_published=false
cockpit_recovery_commands_enabled=false
cockpit_driving_ux_policy_implemented=true
cockpit_unknown_driving_restricted=true
cockpit_moving_long_text_hidden=true
cockpit_restricted_parameter_editing_disabled=true
cockpit_high_risk_controls_disabled=true
cockpit_runtime_policy_authority_independent=true
cockpit_hvac_manual_session_admission_retested=false
cockpit_seat_manual_session_admission_retested=false
cockpit_scenario_control_state_reducer_owned=true
cockpit_scenario_catalog_normalized=true
cockpit_scenario_manual_shared_client=true
cockpit_scenario_device_session_synchronized=true
cockpit_scenario_plan_publication_inferred=false
cockpit_scenario_effect_dispatch_enabled=false
cockpit_scenario_readback_available=false
cockpit_display_matrix_defined=true
cockpit_display_profile_count=3
cockpit_touch_target_min_dp=48
cockpit_accessibility_semantics_runtime_owned=true
cockpit_accessibility_state_not_color_only=true
cockpit_display_large_text_1_3_verified=true
cockpit_display_unsupported_fail_closed=true
cockpit_display_matrix_android13_arm64_verified=true
cockpit_display_effect_authorization_source=false
p4_w12_application_acceptance_complete=true
p4_android13_arm64_aggregate_verified=true
p4_ui_tree_verified=true
p4_crash_buffer_clean=true
runtime_release_simulation_surface_absent=true
p4_plan_effect_projection_host_verified=true
p4_automatic_plan_runtime_published=false
p4_production_effect_dispatch_enabled=false
p4_approval_response_service_published=false
p4_undo_service_published=false
p4_vehicle_readback_available=false
client2_production_release_artifact_available=false
hmi_d4_demo_control_loop_complete=false
cockpit_demo_control_loop_implemented=false
tool_rule_set_contract_defined=true
tool_rule_type_count=6
tool_rule_set_digest_verified=true
tool_rule_init_child_conditional_verified=true
tool_rule_model_intersection_fail_closed=true
tool_rule_terminal_requirements_verified=true
tool_rule_approval_annotation_fail_closed=true
tool_rule_solver_android13_arm64_verified=false
tool_rule_solver_published=false
tool_rule_solver_runtime_wired=false
tool_approval_authority_available=false
tool_execution_enabled=false
working_memory_store_defined=true
working_memory_session_scope_verified=true
working_memory_ttl_verified=true
working_memory_item_limit_verified=true
working_memory_byte_limit_verified=true
working_memory_token_limit_verified=true
working_memory_terminal_cleanup_verified=true
working_memory_payload_zeroized_on_cleanup=true
working_memory_android13_arm64_verified=false
working_memory_runtime_wired=false
working_memory_model_context_published=false
implementation_stage=P9-W03
event_v2_cursor_ack_required=true
event_v2_interface_published=false
plan_contract_v1_defined=true
plan_parcel_physical_android13_arm64_verified=true
plan_runtime_published=false
event_contract_v1_defined=true
event_parcel_physical_android13_arm64_verified=true
event_runtime_service_published=true
event_callback_service_published=true
effect_contract_v1_defined=true
effect_parcel_physical_android13_arm64_verified=true
effect_runtime_service_published=false
approval_response_service_published=false
undo_service_published=false
scenario_execution_enabled=false
production_ready=false
target_hardware_validated=false
driver_development_triggered=false
virtualization_development_triggered=false
```

### 2026-07-18 P4-W10 progress

`CockpitScenarioControlState` 已把 14 个 alias/canonical ID、cold/fatigue/rest catalog device role、manual HVAC/Seat target、
Session lifecycle、active Plan revision 与 event sequence 收敛到唯一 reducer 状态。Bridge 通过 `ScenarioClient` 接口使用同一
Session/Event 链，四阶段和设备 drawer 同步渲染；canonical mismatch 失败关闭。Host/static/APK/R7C 2.0/API 33 ARM64
验证完成后进入 P4-W11 Accessibility/display matrix。

Req IDs：`S2-HMI-001..006`、`S2-SCN-001`、`APP-004`、`XSC-001/005/006`；tracking：`DEV-060`、
`ISSUE-022/026/030/033`。Plan/Graph/Effect/readback/车辆/NPU/Driver-HAL 仍未启用，`production_ready=false`、
`target_hardware_validated=false`。

### 2026-07-18 P4-W11 progress

`CockpitDisplayPolicy` 已把 Client2 认证面收敛为三档横屏 allowlist，并由 Coordinator 为全部 Button 统一提供 48dp
最小触控、content description、focus/accessibility importance、两行省略和 selected/stateDescription。XML 静态基线、
host test、Web preview scale guard、R7C 2.1 `R7C-E-014` 与 Android 13/API 33 ARM64 已覆盖三档 profile、1.30 字体、
最长中文、非颜色状态、无重叠和 unsupported profile 失败关闭。P4-W11 完成后进入 P4-W12 Android device
acceptance/fault/recovery 聚合验收。

Req IDs：`S2-UX-003`、`S2-HMI-001/002`、`APP-004`、`XSC-001/005/006`；tracking：`DEV-061`、
`ISSUE-019/033`。显示策略不是 Effect authority，Plan/Graph/Effect/readback/车辆/NPU/Driver-HAL 仍未启用，
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

### 2026-07-18 P4-W12 progress

新增 P4 分层验收合同与单一 Android 13 ARM64 runner，重新执行 recovery、protected engineer fault、natural/manual
scenario sync 和 display/accessibility 四个子套件。每个子套件清空并检查 crash buffer；最终重新启动 Client2、获取
UI tree 并确认导航 trigger。子测试 UIAutomator dump 使用有界重试，恢复脚本可显式处理 signer-conflicting Client2。

P4-W12 只关闭 application acceptance。Plan/Effect/Media/Nav/approval/partial/mismatch/undo 仍是 host projection 或实体
fail-closed；无 production Client2 release artifact，`hmi_d4_demo_control_loop_complete=false`。下一工作包为 P5-W01
Tool manifest/schema。

Req IDs：`S2-UX-001..003`、`S2-HMI-001..006`、`S2-SCN-001`、`S2-SAF-001`、`S2-EFF-001`、
`APP-004`、`XSC-001/005/006`；tracking：`DEV-062`、`ISSUE-022/026/030/033`。车辆/NPU/Driver-HAL 未启用，
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

### 2026-07-18 P5-W01 progress

`ToolManifest` 已冻结 versioned Tool identity、owner、input/output schema、capability、risk、timeout、idempotency 和 mandatory
health freshness contract。`ToolSchemaValidator` 只接受 32-field/16 KiB bounded scalar object，以 exact Java class 拒绝
missing/unknown/null/type/range/oversize，并返回 defensive immutable map；canonical digest 对字段顺序稳定。

JVM 与 debug/release compile 已覆盖合同 digest、正向 input/output、unknown、type/range 和 health fail-closed。API 33
ARM64 debug probe 已接入统一 installer，但本轮 Windows 只枚举 COM7、没有 Android ADB interface，因此未执行，
`tool_manifest_android13_arm64_verified=false`。probe 不进入 release manifest；Runtime/Graph/Room/Binder 未引用 Tool
contract。下一工作包为 P5-W02 ToolRegistry/Resolver。

Req IDs：`S2-TOL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-063`、`ISSUE-036`。
`tool_registry_published=false`、`tool_execution_enabled=false`、`production_tool_artifact_loaded=false`、
`effect_dispatch_enabled=false`、`vehicle_readback_accessed=false`、`npu_accessed=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

### 2026-07-18 P5-W02 progress

新增 immutable `ToolRegistry`、`ToolHealthSnapshot` 与 `ToolResolver`。Registry 最多 128 个 unique family/version，按固定
顺序计算 digest；相同版本相同 digest 幂等、不同 digest 冲突。Resolver 在显式版本范围内选择最高版本，复验 exact
capability/optional digest，再以 elapsed-realtime health freshness 区分 REGISTERED/RESOLVED/USABLE。最高版本 unhealthy
时返回 NOT_USABLE，不回退旧版本，execution 固定关闭。

JVM 与 debug/release compile 已覆盖排序/dedup/conflict、版本范围、capability/digest、missing/unknown/unhealthy/stale/
future-clock 和 no-fallback。Android 13 ARM64 debug probe 已接入 installer，但当前 adb transport=0，
`tool_registry_android13_arm64_verified=false`。Runtime/Graph/Binder/Room 未引用 Registry，production Tool count=0；下一
工作包为 P5-W03 ToolRuleSolver。

Req IDs：`S2-TOL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-064`、`ISSUE-037`。
`tool_registry_published=false`、`tool_resolver_published=false`、`tool_registry_runtime_wired=false`、
`tool_execution_enabled=false`、`production_tool_registered=false`、`effect_dispatch_enabled=false`、
`vehicle_readback_accessed=false`、`npu_accessed=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。

### 2026-07-18 P5-W03 progress

新增 immutable `ToolRuleSet` 与 `ToolRuleSolver`。RuleSet 冻结 init/child/conditional/terminal/required-before-exit/
requires-approval 六类规则、canonical ID/bounds、structural fail-closed 和 order-independent SHA-256 digest。Solver 按固定顺序
生成 allowset，并与模型选择和 P5-W02 RESOLVED/USABLE family 求交；condition UNKNOWN、terminal 前置缺失和空交集均稳定
拒绝。requires-approval 只做标记，approval grant 与 execution 固定 false。

五项 JVM test 与 debug/release compile 已覆盖规则边界、digest、init/child/condition、模型空集、terminal、approval no-grant
和 unusable exclusion。Android 13 ARM64 debug probe 已接入 installer，但当前 ADB transport 不可用，
`tool_rule_solver_android13_arm64_verified=false`。Runtime/Graph/Binder/Room/Executor 未引用 RuleSolver，production RuleSet/Tool
count=0；下一工作包为 P5-W04 ToolExecutor boundary。

Req IDs：`S2-TOL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-065`、`ISSUE-038`。
`tool_rule_solver_published=false`、`tool_rule_solver_runtime_wired=false`、`tool_approval_authority_available=false`、
`tool_execution_enabled=false`、`production_tool_registered=false`、`effect_dispatch_enabled=false`、
`vehicle_readback_accessed=false`、`model_invoked=false`、`npu_accessed=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。


### 2026-07-18 P5-W04 progress

新增 `ToolInvocationContext`、`ToolExecutor` 与 `InProcessBuiltInToolExecutor`。执行只接受 P5-W03 selection 与
`runtime.builtin` exact allowlist，构造期绑定 Tool contract、当前应用 signer digest 和 artifact digest；调用期绑定
invocation/session/plan/node/audit digest、capability、idempotency、elapsed deadline 和 output byte limit。输入输出复用
P5-W01 exact schema；approval-required、stale deadline、取消、无效输入输出、超限和 implementation failure 均稳定失败关闭。

五项 JVM test 与 debug/release build 已通过；debug probe 与 installer/CI/独立门禁已接入。审计 ring 最多 128 条且只保留
digest、枚举、时间和 output bytes。该同步执行器只支持 cooperative checkpoint，不能强制抢占非合作 built-in；当前 signer
digest 由受信 composition 输入，不是生产 PackageManager/keystore 证据。Runtime/Graph/Binder/Room/Effect/车辆/NPU/Driver-HAL
均未接，OS virtualization、subprocess、dynamic class loading 均为 false。当前 ADB transport=0，
`tool_executor_android13_arm64_verified=false`；下一工作包为 P5-W05 Skill package verifier。

Req IDs：`S2-TOL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-066`、`ISSUE-039`。
`tool_executor_runtime_wired=false`、`tool_execution_enabled=false`、`production_tool_execution_enabled=false`、
`production_tool_registered=false`、`effect_dispatch_enabled=false`、`vehicle_readback_accessed=false`、`model_invoked=false`、
`npu_accessed=false`、`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。

### 2026-07-18 P5-W05 progress

新增 immutable `SkillSignerPolicy`、`SkillVersionPolicy` 与 `SkillArtifactVerifier`。Signer policy 最多 32 项并区分 ACTIVE/
RETIRED/REVOKED 与 activation/revocation artifact epoch；Version policy 最多 128 个 Skill，绑定 Runtime compatibility、
minimum artifact epoch 与 anti-downgrade。Verifier 固定检查 canonical manifest、measured artifact/observed signer digest、
signer/version/runtime/epoch 和 exact capability allowlist；失败无 verified package，通过结果仍固定 load/execution=false。

五项 JVM test 与 debug/release build 已通过；debug probe、installer、独立 checker 与 CI/runtime evolution 已接入。它不读取
APK/JAR/dex/certificate、PackageManager/keystore/TEE，不验证签名链，不接 Runtime/Graph/Binder/Room/P5-W04 executor/
Effect/车辆/NPU/Driver-HAL。当前 ADB transport 不可用，`skill_package_verifier_android13_arm64_verified=false`；下一工作包
为 P5-W06 WorkingMemoryStore。

Req IDs：`S2-TOL-001`、`S2-SAF-001`、`S2-OBS-001`、`FW-U-008`、`DEL-001/004/005`；tracking：`DEV-067`、
`ISSUE-040`。`trusted_skill_evidence_source_configured=false`、`package_signature_cryptographically_verified=false`、
`dynamic_skill_loading_enabled=false`、`skill_execution_enabled=false`、`skill_package_verifier_runtime_wired=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

### 2026-07-18 P5-W06 progress

新增 Android-independent `WorkingMemoryStore`，以 owner fingerprint + Session + item 做隔离，真实保存 bounded opaque bytes。
store 同时执行 active Session、per-item/per-Session item/byte/token、read、TTL 与 terminal tombstone bounds；put 在 mutation 前计算
replacement projected budget，exact replay 不刷新 TTL，read/输入均防御性 copy。

expiry、replacement、remove 与 Session terminal cleanup 都覆零 store-retained byte array；terminal result 只返回 cleaned item/byte/
token count，bounded tombstone 在保留期内阻止 late write。六项 JVM test、debug/release compile、debug-only DUMP probe、installer、
独立 checker 与 CI/runtime evolution 已接入。当前 ADB transport 不可用，
`working_memory_android13_arm64_verified=false`。

P5-W06 不接 Runtime/Graph/Binder/Room/model context、Effect、车辆、NPU 或 Driver/HAL；token count 仍是调用方提供的受信预算
元数据，不是 production tokenizer evidence。下一工作包为 P5-W07 ProfileMemoryStore。Req IDs：`S2-MEM-001`、
`S2-SAF-001`、`S2-OBS-001`、`FW-U-001/006/007`、`NV-F-001`、`NV-G-005/006/007`、`DEL-001/004/005`；tracking：
`DEV-068`、`ISSUE-041`。`working_memory_store_defined=true`、`working_memory_process_local=true`、
`working_memory_persistence_wired=false`、`working_memory_runtime_wired=false`、
`working_memory_model_context_published=false`、`working_memory_tokenizer_verified=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

### 2026-07-18 P5-W07 progress

新增 Android-independent `ProfileMemoryStore`，以 owner fingerprint + user/seat scope + build-owned Field 做精确隔离。固定七字段
allowlist 同时约束 value kind/range 与 USER/SEAT scope；update/read 必须通过 explicit consent 和 active authority，delete 使用独立
authorization 以保证 consent 撤回后仍可清除，export 同时要求 consent 与 EXPORT authorization。

store 在任何 value 操作前执行 `EncryptionOwnerState` gate，并仅保留 owner/key-generation-bound `SealedPayload`。plaintext 只在
bounded transient array 中编解码并覆零；replacement/delete/expiry 覆零 retained ciphertext。record、per-owner record/bytes、
payload、export 与 30-day retention 均有绝对上限。六项 JVM test、debug/release compile、debug-only DUMP probe、installer、
独立 checker 与 CI/runtime evolution 已接入；当前 ADB server 重启后 transport=0，
`profile_memory_android13_arm64_verified=false`。

P5-W07 没有 production consent/revocation authority、Android Keystore/TEE、Room/file repository 或 Runtime/Graph/Binder/model/
Effect/Vehicle/NPU/Driver-HAL 接线。debug/test XOR 不是密码学 evidence。下一工作包为 P5-W08 EpisodicMemoryStore。Req IDs：
`S2-MEM-001`、`S2-SAF-001`、`S2-OBS-001`、`FW-U-001/006/007`、`NV-F-001`、`NV-G-005/006/007`、
`DEL-001/004/005`；tracking：`DEV-069`、`ISSUE-042`。`profile_memory_store_defined=true`、
`profile_memory_explicit_consent_verified=true`、`profile_memory_field_allowlist_verified=true`、
`profile_memory_user_seat_scope_verified=true`、`profile_memory_encryption_owner_gate_verified=true`、
`profile_memory_durable_storage_wired=false`、`profile_memory_production_encryption_owner_configured=false`、
`profile_memory_consent_authority_production_wired=false`、`profile_memory_runtime_wired=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

### 2026-07-18 P5-W08 progress

新增 Android-independent `EpisodicMemoryStore`。写接口只接收 catalog-digest-bound scenario reference、固定 trigger/result/outcome
enum、bounded action count、elapsed interval、retention 和 owner/episode-bound storage policy evidence；main API 不包含 raw continuous
signal、任意 byte/map payload、user/model text 或自由文本。

store 执行 catalog/policy authority fail-closed、owner isolation、exact replay/content conflict、30-day retention/24-hour duration
absolute ceiling、global/per-owner capacity、owner-bound read 和 operation-bound episode/owner erase。六项 JVM test、debug/release compile、debug-only
DUMP probe、installer、独立 checker 与 CI/runtime evolution 已接入；当前 ADB transport 不可用，
`episodic_memory_android13_arm64_verified=false`。

P5-W08 不接 production catalog/policy/read/erase authority、Room/file encrypted repository、trusted cross-restart clock、Binder/Runtime/
Graph/model context、Effect/Vehicle/NPU/Driver-HAL。下一工作包为 P5-W09 ContextBudgetManager。Req IDs：`S2-MEM-001`、
`S2-SAF-001`、`S2-OBS-001`、`FW-U-001/006/007`、`NV-F-001`、`NV-G-005/006/007`、`DEL-001/004/005`；
tracking：`DEV-070`、`ISSUE-043`。`episodic_memory_store_defined=true`、
`episodic_memory_summary_result_only_verified=true`、`episodic_memory_raw_continuous_signal_stored=false`、
`episodic_memory_read_fail_closed=true`、`episodic_memory_production_read_authority_wired=false`、
`episodic_memory_persistence_wired=false`、`episodic_memory_runtime_wired=false`、
`episodic_memory_model_context_published=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。

### 2026-07-18 P5-W09 progress

新增 Android-independent `ContextBudgetManager`。输入只包含固定 SYSTEM/CONTEXT/PROFILE/EPISODE/HISTORY category、canonical ID、
受信 token/byte size、required/summaryAllowed 和 priority；输出只包含 INCLUDE、SUMMARIZE_TO_BUDGET、
TRUNCATE_TO_BUDGET、DROP 及目标 size，不接收、保存或记录原始文本/byte payload。

分配同时执行 global/category token+byte 双包络，required 先行且任何失败返回空 plan；optional 按 category、priority、ID
确定性分配，不跨 category 借预算。六项 JVM test、debug/release compile、debug-only DUMP probe、installer、独立 checker 与
CI/runtime evolution 已接入；当前 ADB transport 不可用，`context_budget_android13_arm64_verified=false`。

P5-W09 不接 production tokenizer/version/digest、summary/truncation executor、budget authority、Binder/Runtime/Graph/model、
Effect/Vehicle/NPU/Driver-HAL。下一工作包为 P5-W10 Memory consent HMI/API。Req IDs：`S2-MEM-001`、`S2-MDL-001`、
`S2-SAF-001`、`S2-OBS-001`、`FW-U-001/006/007`、`NV-F-001`、`NV-G-005/006/007`、`DEL-001/004/005`；
tracking：`DEV-071`、`ISSUE-044`。`context_budget_manager_defined=true`、
`context_budget_category_allocation_verified=true`、`context_budget_dual_limit_verified=true`、
`context_budget_deterministic_overflow_verified=true`、`context_budget_required_fail_closed=true`、
`context_budget_android13_arm64_verified=false`、`context_budget_decision_only=true`、
`context_budget_text_payload_accepted=false`、`context_budget_tokenizer_wired=false`、
`context_budget_summarizer_wired=false`、`context_budget_production_authority_wired=false`、
`context_budget_runtime_wired=false`、`context_budget_content_logged=false`、`model_invoked=false`、
`npu_accessed=false`、`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。

### 2026-07-18 P5-W10 progress

新增 Android-independent `MemoryConsentController`，固定展示 Working Session、Profile Preference、Episodic Scenario 的 purpose、
retention、enabled 与 privacy-safe storage presence。关闭 retained memory 只影响 Profile/Episode projection；Working 继续保持
session-scoped。Profile clear 不返回内容或 count，且 `repositoryMutationApplied=false`。

mutation 精确绑定 owner fingerprint、request ID、operation/target 和 elapsed window；exact replay 幂等、冲突复用拒绝、authority
deny/null/exception 失败关闭。PARKED 可管理，MOVING/UNKNOWN 在 authority 前拒绝且隐藏 preference presence。六项 JVM test、
debug/release compile、响应式 38% 半透明 debug HMI、automated probe、installer、独立 checker 与 CI/runtime evolution 已接入；
当前 ADB transport 不可用，`memory_consent_android13_arm64_verified=false`。

P5-W10 不接 production identity/consent authority、Binder Memory Service、Profile/Episode durable repository、Runtime/Graph/model、
Effect/Vehicle/NPU/Driver-HAL。下一工作包为 P6-W01 EventBroker。Req IDs：`S2-MEM-001`、`S2-UX-003`、`S2-SAF-001`、
`S2-OBS-001`、`FW-U-001/006/007`、`NV-F-001`、`NV-G-005/006/007`、`DEL-001/004/005`；tracking：
`DEV-072`、`ISSUE-045`。`memory_consent_controller_defined=true`、`memory_consent_source_visibility_verified=true`、
`memory_consent_disable_verified=true`、`memory_consent_preference_clear_verified=true`、
`memory_consent_moving_restriction_verified=true`、`memory_consent_android13_arm64_verified=false`、
`memory_consent_hmi_projection_only=true`、`memory_consent_repository_mutation_wired=false`、
`memory_consent_production_authority_wired=false`、`memory_consent_runtime_wired=false`、
`memory_consent_model_context_published=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。

### 2026-07-18 P6-W01 progress

新增 schema V1 `EventBroker`、`EventSubscription` 与 `InProcessDurableEventBroker`。Task state、Policy decision、Model health
使用固定 generic typed topic/payload catalog；topic/payload/filter mismatch 在 mutation 前拒绝。publish 使用 per-topic monotonic
cursor 并先 append bounded process-local retention，再同步通知匹配 subscription；callback failure 关闭 consumer，但 event 保留供
cursor replay。

publish 与 subscribe 支持 owner-scoped exact replay/conflict。replay 对 page/filter/retention 有界，明确返回 future cursor 与
cursor gap；subscribe/cancel 绑定 owner。所有操作经 operation/topic/owner/identity/policy/elapsed evidence 与 injected authority，
deny/null/exception/expired/mismatch 均失败关闭。六项 JVM test、debug/release compile、DUMP-protected debug probe、installer、
独立 checker 与 CI/runtime evolution 已接入；当前 ADB offline，`event_broker_android13_arm64_verified=false`。

required 类名中的 Durable 不代表 process-death durability；旧 Room cursor repository 未接线，DDS/SOME-IP、Binder publication、
Backpressure/QoS、Runtime/Graph/Effect/Vehicle/NPU/Driver-HAL 均未启用。下一工作包为 P6-W02 Backpressure/QoS。Req IDs：
`S2-EVT-001`、`S2-SAF-001`、`S2-OBS-001`、`FW-U-001/006/007`、`NV-F-001`、`NV-G-004/005/006/007`、
`DEL-001/004/005`；tracking：`DEV-073`、`ISSUE-046`。`event_broker_interface_defined=true`、
`event_broker_typed_topics_verified=true`、`event_broker_durable_persistence_wired=false`、
`event_broker_dds_transport_wired=false`、`event_broker_production_published=false`、`event_broker_runtime_wired=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

### 2026-07-18 P6-W02 progress

新增 schema V1 `EventDeliveryQoS` 与 `InProcessEventBackpressureQueue`。每个实例绑定单一 owner/topic subscription，固定
capacity/max batch/deadline window 和 `DROP_OLD/COALESCE/REJECT/DISCONNECT`；所有 pressure outcome 都携带显式 code、
displaced/discarded count 与 replay-after cursor。

`CRITICAL_ACTION_OBSERVATION` 不能 coalesce，也不会被 drop-old；满载只能显式 reject/disconnect，关键 head 过期必须断连并要求
replay。drop-old 按 priority 只替换非关键低优先级候选，coalesce 只替换相同 digest key。callback failure 保留 head，双 queue
隔离已由六项 JVM test 验证；debug/release compile、DUMP probe、installer、独立 checker 与 CI/runtime evolution 已接入。

本轮 queue 尚未接 P6-W01 Broker、旧 Room cursor repository、Binder/DDS/SOME-IP、Runtime/Graph/Effect/Vehicle/Model/NPU/
Driver-HAL；实体 ADB offline，`event_qos_android13_arm64_verified=false`。下一工作包为 P6-W03 TriggerRule manifest/engine。
Req IDs：`S2-EVT-001`、`NV-G-004`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-073`、
`ISSUE-046`。`event_qos_contract_defined=true`、`event_qos_critical_no_silent_drop_verified=true`、
`event_qos_broker_wired=false`、`event_qos_durable_persistence_wired=false`、
`event_qos_production_middleware_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。

### 2026-07-18 P6-W03 TriggerRule manifest/engine progress

新增 schema V1 `TriggerRule`、immutable `TriggerRule.Manifest`、`TriggerEngine` 与 `CooldownStore`。规则绑定 target scenario digest、
fixed metric/zone、threshold、持续窗口、最大采样间隔、最小样本数、去抖、冷却和 observation age；manifest 对 rule ID 排序并计算
order-independent digest。

Engine 对 quality/future/stale/out-of-order、observation replay/conflict、state/cooldown capacity 失败关闭。continuous condition 在 false、
invalid/stale 或 sample gap 时重置；满足 window/sample/debounce 后通过 rule+scope atomic cooldown 生成 digest-only suggestion。输出 source
固定 TRIGGER，auto-execution 与 Effect dispatch 恒为 false。

六项 JVM test、debug/release compile、DUMP probe、installer、独立 checker 与 CI/runtime evolution 已接入。当前不接 production source
adapter、P6-W01 Broker、Runtime/Graph、Vehicle、Model/NPU/Driver-HAL；实体 ADB offline，
`trigger_engine_android13_arm64_verified=false`。下一工作包为 P6-W04 Proactive consent/policy。

基线纠正：P6-W02 合并时根 README、Android README 和自动化记忆曾把 P6-W03 误标为 durable append/cursor；权威 backlog 与本路线图
始终定义 P6-W03 为 TriggerRule。该跟踪漂移在实现前纠正并记录于 DEV-074，没有产生 durable repository 代码或状态误报。

Req IDs：`S2-EVT-001`、`S2-SCN-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-074`、
`ISSUE-031`。`trigger_rule_manifest_defined=true`、`trigger_threshold_window_debounce_verified=true`、
`trigger_cooldown_scope_verified=true`、`trigger_suggestion_only_verified=true`、
`trigger_engine_process_local=true`、`trigger_cooldown_persistence_wired=false`、`trigger_source_adapter_wired=false`、
`trigger_auto_execution_enabled=false`、`trigger_runtime_wired=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

### 2026-07-18 P6-W04 Proactive consent/policy progress

P6-W04 已完成 process-local consent/policy contract。grant 只在 PARKED 且 exact digest evidence 与 independent authority 明确允许
时创建或撤销，并绑定 owner、scenario ID/digest、capability、zone、LOW/MEDIUM maximum risk 与 bounded TTL。HIGH/CRITICAL 在
authority 前硬拒绝通用 grant，candidate 也始终要求 explicit approval。

六项 JVM 测试和 debug/release 编译覆盖 exact binding、risk/mismatch、TTL/revoke、replay/conflict/capacity、driving/evidence/
authority 与 production boundary。debug probe 已实现；ADB transport 当前 offline，因此没有安装或实体证据。

Req IDs：`S2-SAF-001`、`S2-MEM-001`、`S2-EVT-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-075`、
`ISSUE-031`。`proactive_consent_policy_defined=true`、`proactive_grant_binding_verified=true`、
`proactive_high_critical_generic_grant_blocked=true`、`proactive_grant_ttl_revoke_verified=true`、
`proactive_policy_fail_closed_verified=true`、`proactive_consent_android13_arm64_verified=false`、
`proactive_policy_process_local=true`、`proactive_grant_persistence_wired=false`、
`proactive_consent_authority_wired=false`、`proactive_auto_execution_enabled=false`、
`proactive_runtime_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。下一工作包：`P6-W05 Context source adapters`。

### 2026-07-18 P6-W05 Context source adapters progress

P6-W05 已完成 fixed three-source normalization contract。Runtime health、canonical SIMULATED vehicle signal 与 injected time 均输出
相同 immutable descriptor/typed value/quality/freshness/trust/evidence envelope；future、provenance mismatch、quality conflict 和 source
unavailable/error/conflict 显式失败关闭。

六项 JVM 测试和 debug/release 编译覆盖 catalog、三来源、freshness/quality、provenance 与 production boundary。debug-only probe、
installer 和独立/Stage2/Runtime/CI 门禁已接入；ADB 当前无可见设备，因此没有安装或实体证据。真实 vehicle source 继续后置 P8，
本增量不触发 Driver/HAL 开发。

Req IDs：`S2-CTX-001`、`S2-EVT-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-076`、
`ISSUE-031`。`context_source_adapter_contract_defined=true`、`context_source_count=3`、
`context_source_allowlist_verified=true`、`context_source_runtime_health_verified=true`、
`context_source_simulated_vehicle_verified=true`、`context_source_time_verified=true`、
`context_source_freshness_quality_verified=true`、`context_source_fail_closed_verified=true`、
`context_source_android13_arm64_verified=false`、`context_source_production_registry_published=false`、
`context_source_runtime_wired=false`、`context_source_trigger_engine_wired=false`、
`vehicle_signal_provider_wired=false`、`vehicle_property_mapping_configured=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。下一工作包：
`P6-W06 Active suggestion UX`。

### 2026-07-18 P6-W06 Active suggestion UX progress

P6-W06 已完成 process-local `ActiveSuggestionController`。Candidate 使用 fixed reason 与 digest-only metadata；controller 对
suggestion ID replay/conflict、owner+scenario+zone merge、expiry/capacity、dismiss cooldown 和 PARKED-only never-ask 做确定性处理。

PARKED 输出 why/plan/cooldown/merge count 与 review/dismiss/never-ask full card；MOVING/UNKNOWN 只输出最高优先级 minimal banner、
dismiss 和固定 voice projection key。debug-only 响应式半透明 HMI 展示“场景输入→原因→方案→等待确认”链路，并明确未执行车控。

六项 JVM 测试和 debug/release 编译已通过，DUMP probe、installer 与独立/Stage2/Runtime/CI 门禁已接入。ADB 当前
`online=0/offline=1/unauthorized=0`，因此没有安装或实体证据。生产 suggestion source、Client2 wiring、voice engine、durable preference、Trigger/Graph/Effect/vehicle/
Model/NPU/Driver-HAL 均未接。

Req IDs：`S2-UX-002`、`S2-TRG-002`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：
`DEV-077`、`ISSUE-031`。`active_suggestion_controller_defined=true`、`active_suggestion_full_card_verified=true`、
`active_suggestion_merge_replay_verified=true`、`active_suggestion_moving_minimal_verified=true`、
`active_suggestion_never_ask_verified=true`、`active_suggestion_android13_arm64_verified=false`、
`active_suggestion_hmi_projection_only=true`、`active_suggestion_production_source_wired=false`、
`active_suggestion_preference_repository_wired=false`、`active_suggestion_voice_engine_wired=false`、
`effect_dispatch_enabled=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。下一工作包：`P7-W01 ModelRequest/Result v2`。

### 2026-07-18 P7-W01 ModelRequest/Result v2 progress

P7-W01 已新增独立 `ModelContractV2`，冻结 schema V2 `ModelRequest` 的 purpose、privacy class、latency/token budget、
required capability、fallback policy、trace ID，并补充 request ID、input digest 与 canonical request fingerprint。所有内容只以
fixed enum、bounded number、canonical ID 和 SHA-256 digest 进入合同，不接收用户原文、prompt、模型原文或车辆 payload。

`ModelResult` 必须绑定 request ID/fingerprint/trace，token usage 不得超过请求预算；成功与六类 terminal 状态使用固定 state/detail
组合，failure 不得携带输出。SENSITIVE/RESTRICTED 回退失败关闭，RESTRICTED 只允许 no-fallback。结果不能授予车辆动作或请求
Effect dispatch。

六项 JVM 测试、debug/release source、DUMP probe、installer 和独立/Runtime/CI 门禁已接入。旧 `ModelProvider.InferenceRequest/
TerminalResult` 未修改，Provider registry、policy router、模型执行、NPU、network、Vehicle 与 Driver/HAL 均未接。实体状态将在本轮
ADB transport 复核结果为 `online=0/offline=0/unauthorized=0`，因此未安装 probe，也没有生成实体证据。

Req IDs：`S2-MDL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-078`、
`ISSUE-024/044`。`model_contract_v2_defined=true`、`model_request_v2_fields_verified=true`、
`model_result_v2_binding_verified=true`、`model_privacy_fallback_fail_closed=true`、
`model_raw_content_accepted=false`、`model_provider_registry_wired=false`、`model_policy_router_wired=false`、
`model_contract_v2_android13_arm64_verified=false`、`model_invoked=false`、`npu_accessed=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。下一工作包：`P7-W02 ModelProviderRegistry/health`。

### 2026-07-18 P7-W02 ModelProviderRegistry/health progress

P7-W02 已新增 fixed-catalog `ModelProviderRegistry`，目录包含 deterministic Android contract-test、Android local development、
vendor NPU placeholder、cloud placeholder 四项。每项冻结 provider kind、health source、capability、network/hardware expectation 与
test/development/production 三层 availability；catalog 按 provider ID 排序并计算 canonical digest。

Health report 使用 source binding、revision、monotonic observed/valid-until 和 SHA-256 evidence；unknown source/provider、future、expired、
out-of-order、same-revision conflict 均确定性拒绝，exact replay 不重复更新。snapshot 将超时 health 降为 UNKNOWN/STALE，不修改目录。
即使 vendor/cloud 报告 HEALTHY，由于 implementation/eligibility 均未发布，production ready 仍为 0 且 routing 恒为 false。

六项 JVM test、debug-only DUMP probe、installer 与独立/Runtime/CI 门禁已接入。当前 test available=1、development available=0、
production ready=0；Registry 未接 Runtime/Provider instance/Router，未调用模型、network、NPU 或硬件。ADB 复核为
`online=0/offline=0/unauthorized=0`，因此未安装 probe，实体 verified 保持 false。

Req IDs：`S2-MDL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-079`、
`ISSUE-024`。`model_provider_registry_defined=true`、`model_provider_count=4`、
`model_provider_health_freshness_verified=true`、`model_provider_health_replay_verified=true`、
`model_provider_availability_separation_verified=true`、`model_provider_placeholder_fail_closed=true`、
`model_contract_test_available_count=1`、`model_development_available_count=1`、`model_production_ready_count=0`、
`model_provider_registry_android13_arm64_verified=false`、`model_provider_registry_runtime_wired=false`、
`model_policy_router_wired=false`、`model_invoked=false`、`network_accessed=false`、`npu_accessed=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。下一工作包：`P7-W03 PolicyAwareModelRouter`。

### 2026-07-18 P7-W03 PolicyAwareModelRouter progress

P7-W03 已新增 pure-Java `PolicyAwareModelRouter`。Router 消费 digest-only V2 request、P7-W02 immutable registry snapshot 与
caller-owned `PolicySnapshot`，不读取系统网络、温度、配额或硬件。每个 fixed Provider 都得到 deterministic
mode/health/privacy/network/thermal/latency/capability/quota evaluation，decision 绑定 request ID/fingerprint/trace、policy digest、
catalog digest 和 candidate digests。

Policy snapshot 的 revision、elapsed validity 与 evidence digest 在构造/决策时失败关闭；future/stale 不产生 candidate selection。
CONTRACT_TEST、DEVELOPMENT、PRODUCTION availability 不互相继承。`NO_FALLBACK` 最多一个选择，其余允许策略也最多两个 Provider、
一个 fallback；当前 production ready count 仍为 0，所以 production 决策保持无可用 Provider。

六项 JVM test、debug-only DUMP probe、installer 与独立/Runtime/CI 门禁已接入。Router 只返回 metadata，不构造 Provider instance，
不执行 inference，不访问 network/NPU/vehicle/hardware，不接 Runtime/Graph/Effect；模型结果也没有改变 action authorization 的入口。

Req IDs：`S2-MDL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-080`、
`ISSUE-024`。`model_policy_router_defined=true`、`model_policy_router_privacy_network_thermal_verified=true`、
`model_policy_router_latency_capability_quota_verified=true`、`model_policy_router_fallback_bounded=true`、
`model_policy_router_no_action_authority=true`、`model_policy_router_android13_arm64_verified=false`、
`model_policy_router_runtime_wired=false`、`provider_invoked=false`、`model_invoked=false`、`network_accessed=false`、
`npu_accessed=false`、`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。下一工作包：`P7-W04 LocalModelProvider`。

### P7-W04 LocalModelProvider progress

状态：`DEVELOPED`（2026-07-18）。新增 debug-only `LocalModelProvider`、injected `LocalInferenceEngine`、bounded `EngineOutput`/
`StreamLimits`、cooperative cancellation、monotonic deadline、typed lifecycle/metrics/fault/terminal history、六项 JVM test、DUMP probe、
安装器和静态/CI 门禁。debug/release build 均通过，release source 不包含 executable Provider。

fixed catalog 的 `android.local.development` 现在 `developmentAvailable=true`，所以当前
`model_contract_test_available_count=1`、`model_development_available_count=1`、`model_production_ready_count=0`。该变化不启用
production route：profile 固定 DEBUG_ONLY/NEVER/non-hardware/non-production，Runtime/Router 不 dispatch，Vendor NPU 不回退到它。

Req IDs：`S2-MDL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-081`、`ISSUE-024`。
`local_model_provider_verified=true`、`local_model_provider_deadline_verified=true`、
`local_model_provider_cancel_verified=true`、`local_model_provider_stream_limit_verified=true`、
`local_model_provider_debug_only=true`、`local_model_provider_release_source_absent=true`、
`local_model_provider_runtime_wired=false`、`local_model_provider_vendor_npu_fallback_enabled=false`、
`local_model_provider_android13_arm64_verified=false`、`production_inference_enabled=false`、`network_accessed=false`、
`npu_accessed=false`、`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。下一工作包：`P7-W05 Prompt/Output schema`。

### P7-W05 Structured Model Output progress

状态：`DEVELOPED`（2026-07-18）。新增 main-source `StructuredModelOutput`、fixed prompt/output ID、machine-readable JSON Schema、strict
UTF-8/JSON parser、ScenarioCatalog + CapabilityCatalog binding、typed parameter/summary、canonical digest、八项 JVM test、debug DUMP probe、
installer、独立/Runtime/CI 门禁。

模型输出只能提出 enabled scenario 和该 manifest 已声明的 catalog capability；area/type/range/step/duplicate/size 均失败关闭。accepted
candidate 不创建 Plan/Effect/approval，不接 Provider/Router/Runtime，不访问 network/NPU/vehicle/hardware，也不记录 raw output。

Req IDs：`S2-MDL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-082`、`ISSUE-024`。
`structured_model_output_verified=true`、`model_output_catalog_binding_verified=true`、
`model_output_unknown_capability_rejected=true`、`model_output_no_action_authority=true`、
`model_output_schema_runtime_wired=false`、`structured_model_output_android13_arm64_verified=false`、
`model_invoked=false`、`raw_model_content_logged=false`、`network_accessed=false`、`npu_accessed=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。下一工作包：`P7-W06 Scenario evaluation harness`。

### P7-W06 Scenario Evaluation progress

状态：`DEVELOPED`（2026-07-18）。新增 main-source `ScenarioEvaluationHarness`、固定 12-case synthetic metadata corpus、digest-bound
CaseResult/EvaluationReport、intent/unsafe/invalid/fallback permille、p50/p95/max latency、token-cost/fallback 分类、六项 JVM test、debug
DUMP probe、installer 与独立/Runtime/CI 门禁。

case 不包含 utterance、模型文本或车辆标量；raw output 仅一次性交给 P7-W05 validator，report 只保留 digest/enums/counts。完整 coverage、
case digest 与 scenario/capability catalog revision 必须一致；缺项、重复、预算越界或混用 revision 均失败关闭。harness 不调用模型，
不接 Runtime/Graph/Effect/Vehicle/NPU/Driver-HAL，不授予执行权。

本轮 Windows platform-tools 仅返回脱敏传输状态 `online=0/offline=1/unauthorized=0/other=0`，因此未安装 debug APK、未运行
DUMP probe，实体 Android 13 ARM64 与目标硬件验证标志保持 false。

Req IDs：`S2-MDL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-083`、`ISSUE-024`。
`scenario_evaluation_verified=true`、`evaluation_corpus_verified=true`、`evaluation_metrics_verified=true`、
`evaluation_boundary_verified=true`、`evaluation_case_count=12`、`scenario_evaluation_runtime_wired=false`、
`raw_evaluation_content_logged=false`、`scenario_evaluation_android13_arm64_verified=false`、`model_invoked=false`、
`network_accessed=false`、`npu_accessed=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。下一工作包：`P7-W07 Resource/thermal admission`。

### P7-W07 Resource and Thermal Admission progress

状态：`DEVELOPED`（2026-07-18）。新增 main-source `ModelResourceAdmission`、fresh policy/resource binding、fixed workload priority、
compact/minimal degradation、typed scheduler projection、八项 JVM test、debug DUMP probe、installer 与独立/Runtime/CI 门禁。

准入前拒绝不改变 scheduler；已接纳和 replay 保留 active metadata；调度器配额拒绝保留 typed reason，不触发重试或 Provider。代码不读取
真实 thermal/NPU/vehicle，不调用模型，不接 Runtime/Graph/Effect，不授予 action authority。真实 snapshot producer 和 Vendor NPU
后置 P8。

本轮 Windows platform-tools 仅返回脱敏传输状态 `online=0/offline=1/unauthorized=0/other=0`，因此未安装 debug APK、未运行
DUMP probe，实体 Android 13 ARM64 与目标硬件验证标志保持 false。

Req IDs：`S2-MDL-001`、`S2-SAF-001`、`S2-OBS-001`、`NV-G-004`、`DEL-001/004/005`；tracking：
`DEV-084`、`ISSUE-024`。`model_resource_admission_verified=true`、
`foreground_vehicle_priority_verified=true`、`thermal_degradation_verified=true`、
`thermal_resource_fail_closed_verified=true`、`admission_boundary_verified=true`、
`resource_admission_runtime_wired=false`、`resource_snapshot_producer_wired=false`、
`model_resource_admission_android13_arm64_verified=false`、`provider_invoked=false`、`model_invoked=false`、
`network_accessed=false`、`npu_accessed=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。下一工作包：`P8-W01 Target capability discovery`（外部阻塞）。

### P8-W01 Target Capability Discovery software preparation

状态：`SOFTWARE_PREPARED / EXTERNAL_BLOCKED`（2026-07-18）。新增 machine-readable discovery contract、固定八能力/14 列
matrix template、只读 ADB collector、仓库外私有 evidence 约束和动态 fake-ADB redaction 门禁。

collector 只读取 Android API、PackageManager feature、shell 可见 Binder/command inventory。summary 不包含 serial、fingerprint、车型、
signer、原始 service name、车辆值或用户/模型 payload；代码禁止 root/remount/install/uninstall、SELinux mutation、device-node scan、
unpublished Vendor API 和 property write。

当前 Windows platform-tools 脱敏状态仍为 `online=0/offline=1/unauthorized=0/other=0`，且没有公开 property list、Vendor AIDL/SDK、
permission/signature/owner/version/readback/fault/rollback 文档，所以 matrix 全部保持 EXTERNAL_BLOCKED，P8-W02..W06 不启动。独立软件加固
已转入并完成 P9-W01 Performance budgets 软件合同。

Req IDs：`S2-ADP-002`、`S2-OBS-001`、`XSC-001/004/005/006`、`KH-003/006/007`、`DEL-004/005`；tracking：
`DEV-085`、`ISSUE-024/027/030/047`。`target_capability_discovery_contract_defined=true`、
`target_capability_read_only_collector_verified=true`、`target_capability_matrix_template_count=8`、
`target_capability_summary_redaction_verified=true`、`target_capability_matrix_complete=false`、
`public_car_property_list_available=false`、`vendor_service_contract_available=false`、
`permission_signature_policy_available=false`、`target_capability_discovery_external_blocked=true`、
`vehicle_property_mapping_configured=false`、`production_adapter_registered=false`、`vendor_npu_provider_available=false`、
`driver_development_triggered=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。P9-W01 软件合同现已完成，下一工作包见下节。

### P9-W01 Performance Budget progress

状态：`DEVELOPED / TARGET_MEASUREMENT_PENDING`（2026-07-18）。新增 machine-readable/Java 同步 budget profile、七类十项 metric、
三种 evidence mode、strict aggregate evaluator、八项 JVM test、debug-only synthetic probe、安装器和独立/Runtime/CI 门禁。

预算覆盖 Binder 10/50/30/50 ms、Plan 300 ms、Effect admission 200 ms、DB 64 MiB、PSS 256 MiB、single-core CPU 300 permille、
cold startup 3 s。阈值为 initial engineering baseline；missing/unit/sample/duplicate/threshold 均失败关闭，target report 不自动提升资格。

本轮 Windows platform-tools 脱敏状态仍为 `online=0/offline=1/unauthorized=0/other=0`，因此未安装 APK、未运行实体 probe，也没有
30-sample target evidence 或 owner approval。P8-W01..W06 的外部阻塞不变。

Req IDs：`S2-OBS-001`、`S2-REL-001`、`XSC-001/004/005/006`、`KH-003/006`、`DEL-001/004/005`；tracking：
`DEV-086`、`ISSUE-048`。`performance_budget_contract_defined=true`、`performance_budget_category_count=7`、
`performance_budget_metric_count=10`、`performance_budget_catalog_verified=true`、
`performance_budget_report_validation_verified=true`、`performance_budget_target_owner_approved=false`、
`performance_budget_target_measurement_complete=false`、`performance_budget_android13_arm64_verified=false`、
`performance_budget_runtime_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。下一工作包：`P9-W02 72h stability and fault matrix`。

### P9-W02 Stability Fault Matrix progress

状态：`DEVELOPED / TARGET_72H_PENDING`（2026-07-18）。新增 machine-readable/Java 同步 3 x 6 / 18-case matrix、三种 evidence mode、
strict aggregate evaluator、九项 JVM test、debug-only synthetic probe、安装器和独立/Runtime/CI 门禁。

matrix 覆盖 cold/fatigue/rest 与 baseline、adapter death、Runtime restart、storage pressure、callback churn、network loss；对 missing、
duplicate、sample/duration、crash/ANR/invariant、iteration/outcome/recovery 全部失败关闭，target report 不自动提升资格。

本轮 ADB 脱敏状态仍为 `online=0/offline=1/unauthorized=0/other=0`，因此未安装 APK、未运行实体 probe，也没有真实 fault injection、
72h target evidence 或 owner approval。P8-W01..W06 外部阻塞不变。

Req IDs：`S2-REL-001`、`S2-OBS-001`、`XSC-001/004/005/006`、`KH-003/006`、`DEL-001/004/005`；tracking：
`DEV-087`、`ISSUE-049`。`stability_fault_matrix_contract_defined=true`、`stability_workload_count=3`、
`stability_fault_count=6`、`stability_matrix_case_count=18`、`stability_report_validation_verified=true`、
`stability_target_72h_complete=false`、`stability_target_owner_approved=false`、
`stability_android13_arm64_verified=false`、`stability_fault_injection_runtime_wired=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。
下一工作包：`P9-W03 Security review/fuzz`。

### P9-W03a Parser Security Corpus progress

状态：`DEVELOPED / P9-W03_IN_PROGRESS`（2026-07-18）。新增 machine-readable/Java 同源三 surface / 18-case security corpus、
五组 JVM security regression、独立 checker 和正式设计/交付/偏差/Issue 跟踪。

Checkpoint 六项覆盖 malformed/duplicate/unknown/oversize/digest tamper/path key；ScenarioManifest 六项覆盖 source traversal、
unknown/duplicate/oversize/trailing/depth；ToolSchema 六项覆盖 missing/unknown/null/type/value/payload bound。每项实际调用现有边界并要求
精确 typed error；host test 已通过。

W03a 不等于 coverage-guided fuzz、AIDL caller spoof/replay、signature policy、Android 13 instrumentation 或目标安全资格。下一小步
`P9-W03b Binder identity/replay/signature policy`，随后 W03c 完成剩余 schema/aggregation/debug probe。

Req IDs：`S2-SAF-001`、`S2-TOL-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-088`、`ISSUE-050`。
`security_parser_corpus_defined=true`、`security_parser_surface_count=3`、`security_parser_case_count=18`、
`security_parser_fail_closed_regression_verified=true`、`security_coverage_guided_fuzz_complete=false`、
`security_aidl_identity_review_complete=false`、`security_signature_policy_review_complete=false`、
`security_android13_arm64_verified=false`、`security_runtime_wired=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

### P9-W03b Identity/Replay Security Corpus progress

状态：`DEVELOPED / P9-W03_IN_PROGRESS`（2026-07-18）。新增 machine-readable/Java 同源 CallerPolicy、SessionReplay、
SignerPolicy 三 surface / 18-case corpus、五组 JVM regression、独立 checker 和正式需求/接口/架构/详设/交付/偏差跟踪。

CallerPolicy 覆盖 unresolved/package/current signer/capability/shared UID/principal rotation；SessionReplay 覆盖 exact replay、digest
conflict、跨 owner find/events/cancel 与 malformed owner；SignerPolicy 覆盖 unknown/not-active/retired/revoked/malformed/epoch。所有
测试直接调用既有策略实现，host JVM 已通过。

W03b 不等于真实 Binder calling UID spoof、目标 APK 签名密码学验证、Android 13 instrumentation、coverage-guided fuzz 或目标安全资格。
下一小步 `P9-W03c model/schema/path/oversize aggregation and Android debug probe`；P8-W01..W06 外部阻塞不变。

Req IDs：`S2-SAF-001`、`S2-TOL-001`、`S2-SES-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：
`DEV-089`、`ISSUE-050`。`security_identity_replay_corpus_defined=true`、`security_identity_replay_surface_count=3`、
`security_identity_replay_case_count=18`、`security_caller_policy_host_verified=true`、
`security_session_replay_owner_policy_host_verified=true`、`security_signer_policy_host_verified=true`、
`security_binder_calling_uid_spoof_android_verified=false`、
`security_package_signature_cryptographically_verified=false`、`security_android13_arm64_verified=false`、
`security_runtime_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。

### P9-W03c Security Boundary Inventory progress

状态：`SOFTWARE_DEVELOPED / TARGET_FUZZ_PENDING`（2026-07-18）。新增 37 项 public main AIDL 精确 inventory（7 interface、
30 parcelable）、八 validation family 聚合、四项 model/session path/oversize JVM regression，并扩展现有 DUMP-protected debug-only
Android probe 和 installer marker gate。

host test/debug/release compile 已通过。本增量先观察到 ADB `online=0/offline=1/unauthorized=0/other=0`，提交前复核为
`online=0/offline=0/unauthorized=0/other=0`，因此未安装 APK、未运行新 probe；真实 Binder calling UID spoof、目标 APK 签名密码学
证据、coverage-guided fuzz 和安全 owner approval 保持外部待办。

W03a/W03b/W03c 仓库内软件项已交付；W03 完整安全资格仍由 ISSUE-050 跟踪。下一工作方向为 P9-W04 privacy/data lifecycle 的
软件增量，同时不绕过 W03/P8 外部证据阻塞。

Req IDs：`S2-SAF-001`、`S2-TOL-001`、`S2-SES-001`、`S2-MDL-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：
`DEV-090`、`ISSUE-050`。`security_aidl_parcel_inventory_complete=true`、`security_aidl_surface_count=37`、
`security_validation_family_count=8`、`security_host_path_oversize_aggregate_verified=true`、
`security_android_debug_probe_available=true`、`security_android_debug_probe_executed=false`、
`security_coverage_guided_fuzz_complete=false`、`security_android13_arm64_verified=false`、
`security_runtime_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。

### P9-W04a Privacy Data Inventory progress

状态：`DEVELOPED / P9-W04_IN_PROGRESS`（2026-07-18）。新增 12-surface versioned JSON/Java inventory：6 Room、5
process-local、1 transient，绑定 sensitivity/storage/content/owner/consent/retention/delete/export/log/enforcement 和实际 source class。

五组 JVM test 已验证 source/count、两个 policy gap、content logging prohibition、Profile 唯一 authorized export 与全部 false claim。
durable Effect recovery 与 Audit 没有 owner-approved retention/delete policy，明确保持 `POLICY_GAP`；W04a 不修改运行行为。

下一小步 `P9-W04b retention/delete/export policy`。必须先冻结 owner policy version/digest 与 active safety recovery guard；不得猜测期限、
删除 active Effect/compensation 或启用未授权 export。随后 W04c 才提供 debug-only Android redaction/audit probe。

Req IDs：`S2-MEM-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-091`、`ISSUE-051`。
`privacy_data_inventory_complete=true`、`privacy_data_surface_count=12`、`privacy_policy_gap_count=2`、
`privacy_owner_policy_approved=false`、`privacy_production_lifecycle_complete=false`、
`privacy_runtime_lifecycle_wiring_complete=false`、`privacy_android13_arm64_verified=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W04`。

### P9-W04c Privacy Redaction/Audit Probe progress

状态：`SOFTWARE_DEVELOPED / TARGET_PROBE_PENDING / OWNER_POLICY_BLOCKED`（2026-07-18）。新增 21-key fixed redacted projection、10 类 forbidden
field contract、四组 JVM test、DUMP-protected debug Activity、installer marker 与 release-absence/Stage2/Runtime/CI 门禁。

Host/JVM/debug-release compile 可证明 projection 与入口存在。当前 ADB `online=0/offline=0/unauthorized=0`，未安装 APK、未运行 probe，故
`executed=false`、Android evidence=false。W04a/W04b/W04c 仓库内软件项已完成；真实 owner policy、repository enforcement 和目标 evidence
继续由 ISSUE-051 跟踪。下一软件工作包 `P9-W05 Production signer/upgrade/rollback`。

Req IDs：`S2-MEM-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-091/092/093`、`ISSUE-051`。
`privacy_redacted_audit_projection_defined=true`、`privacy_android_debug_probe_available=true`、
`privacy_android_debug_probe_executed=false`、`privacy_android13_arm64_verified=false`、
`privacy_owner_policy_approved=false`、`privacy_repository_mutation_wired=false`、
`privacy_runtime_lifecycle_wiring_complete=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W04`。

### P9-W04b Privacy Policy Admission progress

状态：`SOFTWARE_DEVELOPED / OWNER_POLICY_BLOCKED`（2026-07-18）。新增 versioned draft policy、纯 Java admission/preflight contract 与七组 JVM
test。Policy body 绑定 W04a inventory digest、12 个有序 surface rule 和 Privacy/Functional Safety/Compliance 三 owner digest evidence。

Effect recovery/Audit 分别要求正数 retention ceiling 与 active Effect/compensation、legal/safety hold guard。delete/erase/export preflight 需要
authorization；Profile export 另需 consent，其他 export 禁止。decision 不修改 repository、不导出数据、不授予 Runtime authority。

当前 draft 两个 ceiling unset、owner evidence 为空，因此确定性拒绝激活；JVM complete policy 只是 synthetic fixture。下一小步
`P9-W04c redaction/audit Android probe`，增加 DUMP-protected debug-only evidence，release 不暴露入口；真实 owner policy 与 repository enforcement
继续由 ISSUE-051 阻塞。

Req IDs：`S2-MEM-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-091/092`、`ISSUE-051`。
`privacy_policy_admission_defined=true`、`privacy_current_policy_admitted=false`、
`privacy_owner_policy_approved=false`、`privacy_repository_mutation_wired=false`、
`privacy_runtime_lifecycle_wiring_complete=false`、`privacy_android13_arm64_verified=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W04`。
### P9-W05a Production Release Admission progress

状态：`SOFTWARE_CONTRACT_DEFINED / PRODUCTION_OWNER_INPUT_OPEN`（2026-07-18）。新增固定三 APK set、installed/candidate
same-signer/cohort、release/package version 单调性、Room readable range、migration evidence 与 rollback owner/decision/data
compatibility 的纯 Java准入合同和八组 JVM tests。

合同不接 PackageManager/keystore/Room/Service，不安装、卸载或执行 rollback。仓库当前仍是 debug signer，production signer owner、
release/rollback owner evidence、OTA/MDM installer 和目标 rehearsal 均缺失，因此 candidate 固定不宣称 admitted。下一小步
`P9-W05b release metadata Android probe`；外部证据继续由 ISSUE-052 跟踪。

Req IDs：`S2-REL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-094`、`ISSUE-052`。
`production_release_admission_defined=true`、`release_package_set_count=3`、
`same_signer_upgrade_fail_closed=true`、`release_database_compatibility_fail_closed=true`、
`release_rollback_decision_fail_closed=true`、`production_signer_owner_approved=false`、
`production_release_candidate_admitted=false`、`release_installer_wired=false`、
`release_rollback_executor_wired=false`、`release_android13_arm64_verified=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W05`。

### P9-W05b Production Release Metadata Probe progress

状态：`SOFTWARE_DEVELOPED / TARGET_PROBE_PENDING / PRODUCTION_OWNER_BLOCKED`（2026-07-18）。新增 W05a 同源固定三包 metadata query、
27-key count/boolean projection、七组 JVM tests、DUMP-protected debug Activity、只读 ADB dry-run adapter 和 release-absence/CI 门禁。

探针只读取 installed/versionCode 和 Android signer relation code，不读取或输出证书/签名 bytes、包路径、设备身份、原始日志或业务 payload；
adapter 不 build/install/uninstall/rollback。当前 ADB `online=0/offline=1/unauthorized=0/other=0`，未运行 probe，因此 Android evidence=false。

W05a/W05b 仓库内软件项已完成；正式 signer/candidate/OTA/rollback rehearsal 继续由 ISSUE-052 外部阻塞。下一可执行软件增量为
`P9-W06a driver-distraction/safety admission contract`，只定义失败关闭策略，不伪造 vehicle state 或 OEM owner evidence。

Req IDs：`S2-REL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：`DEV-094/095`、`ISSUE-052`。
`release_metadata_projection_defined=true`、`release_installer_dry_run_adapter_defined=true`、
`release_android_debug_probe_available=true`、`release_android_debug_probe_executed=false`、
`production_signer_owner_approved=false`、`production_release_candidate_admitted=false`、
`release_installer_wired=false`、`release_rollback_executor_wired=false`、
`release_android13_arm64_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W05`。

### P9-W06a Driver Safety Admission progress

状态：`SOFTWARE_CONTRACT_DEFINED / OEM_OWNER_AND_TARGET_EVIDENCE_OPEN`（2026-07-18）。新增 12-action JSON/Java catalog、
PARKED/MOVING/UNKNOWN/FAULT 四 UX profile、500 ms production-trusted Safety State、三 owner role、HVAC/Seat capability
availability/authorization/readback/activation gate、stable decision digest 和八组 JVM tests。

moving 对 long text/parameter/video/driver recline/diagnostic/OTA 固定 hard deny；moving HVAC/seat heat/vent 最多 policy-only，parked
driver recline 最多 approval-required。所有 decision 固定不授权 Effect dispatch/hardware operation。IDLE 因缺少可信 gear/speed/
parking-brake 联合语义不在本轮伪造。

当前 owner approval=0、production capability authorization=0，Runtime/Governance/Effect/Vehicle 未接，Android probe 未提供。
Req IDs：`S2-UX-002`、`S2-SAF-001`、`S2-EFF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：
`DEV-096`、`ISSUE-029/030`。`driver_safety_admission_defined=true`、
`driver_safety_moving_hard_interlock_verified=true`、`driver_safety_current_owner_policy_approved=false`、
`driver_safety_vehicle_state_provider_wired=false`、`driver_safety_effect_runtime_wired=false`、
`driver_safety_android13_arm64_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W06`。下一小步：`P9-W06b redacted Android probe and target evidence adapter`。

### P9-W06b Driver Safety Redacted Probe progress

状态：`SOFTWARE_DEVELOPED / TARGET_PROBE_PENDING / OEM_OWNER_BLOCKED`（2026-07-18）。新增 27-key repository-only
count/boolean projection、六组 JVM tests、DUMP-protected debug Activity、只读 no-install ADB adapter 和
release-absence/Stage2/Runtime/CI 门禁。

探针不读取车辆 scalar、设备身份、owner/activation reference、原始日志或业务 payload，不进入 Runtime/Governance Service，
不执行 Effect/Vehicle/hardware。当前 ADB 没有合格 online transport，probe 未执行；未来 contract probe 的 API33 ARM64 成功标志
也不能提升 OEM safety qualification。

W06a/W06b 仓库软件项已完成；真实 owner policy、Safety State producer、IDLE、驾驶分心 acceptance matrix、seat/HVAC policy
和硬联锁继续由 ISSUE-029/030 与 P8 外部阻塞。下一软件增量为 `P9-W07 Release evidence and field diagnostics`。

Req IDs：`S2-UX-002`、`S2-SAF-001`、`S2-EFF-001`、`S2-OBS-001`、`DEL-001/004/005`；tracking：
`DEV-096/097`、`ISSUE-029/030`。`driver_safety_redacted_projection_defined=true`、
`driver_safety_audit_key_count=27`、`driver_safety_android_debug_probe_available=true`、
`driver_safety_android_debug_probe_executed=false`、`driver_safety_target_adapter_defined=true`、
`driver_safety_current_owner_policy_approved=false`、`driver_safety_vehicle_state_provider_wired=false`、
`driver_safety_effect_runtime_wired=false`、`driver_safety_android13_arm64_verified=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W06`。

### P9-W07a Release Evidence Envelope progress

状态：`SOFTWARE_DEVELOPED / TARGET_EVIDENCE_PENDING`（2026-07-18）。新增 pure-Java
`ReleaseEvidenceEnvelope`、versioned JSON、九组 JVM regression、稳定 report SHA-256 和专用静态门禁。合同固定 release tag、
source commit、archive/release-set digest、非秘密 alias/evidence reference、可选 owner digest，以及 release/install/launch/service/manual
八类有序 diagnostic fact。

GitHub policy 对 privacy confirmation、raw/derived identity 和 automatic upload 失败关闭；Host 报告只能是 software-only。Target 报告即使
具备 owner digest 且八类全部执行，也只达到 owner review eligible，`production_ready` 和 `target_hardware_validated` 始终为 false。
本增量不读取目标、Android、PackageManager、vehicle、NPU、Driver/HAL，不连接 Runtime/Governance Service。

下一小步：`P9-W07b debug-only field diagnostics probe and host adapter`；随后 `P9-W07c replacement release and issue/retest state machine`。
目标 transport、正式报告、owner/retest approval 由 ISSUE-052/053 外部跟踪。

Req IDs：`S2-OBS-001`、`S2-REL-001`、`DEL-001/004/005`；tracking：`DEV-098`、`ISSUE-052/053`。
`release_evidence_envelope_defined=true`、`release_evidence_diagnostic_category_count=8`、
`release_evidence_report_digest_defined=true`、`release_evidence_target_owner_approved=false`、
`release_evidence_target_report_admitted=false`、`release_evidence_runtime_diagnostics_wired=false`、
`release_evidence_retest_workflow_wired=false`、`release_evidence_automatic_upload_enabled=false`、
`release_evidence_android13_arm64_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W07`。

### P9-W07b Field Diagnostics Probe progress

状态：`SOFTWARE_DEVELOPED / TARGET_PROBE_PENDING`（2026-07-18）。新增 31-key pure-Java redacted projection、DUMP-protected
debug-only Activity、七组 JVM regression、no-install target adapter 和 manifest/source/command/CI 门禁。

APK preflight 只查询三包 aggregate version/signer relation、两个 launcher intent 和 Runtime/Diagnostics Service declaration，不输出包名、
路径、设备身份、签名材料、raw log 或业务 payload。Adapter 实际执行 release bundle、Demo/Client2 launch、Runtime/Diagnostics probe 五类，
installer dry-run/execute 与 manual matrix 明确 NOT_RUN；所有执行项只输出 status/result/digest。

当前 ADB 为 offline，未执行目标 probe。W07b 即使在 API33 ARM64 上完成，也只提供 field-diagnostic contract evidence，三项 NOT_RUN 使
category execution incomplete，不能 admit target report。下一小步：`P9-W07c replacement release and issue/retest state machine`。

Req IDs：`S2-OBS-001`、`S2-REL-001`、`DEL-001/004/005`；tracking：`DEV-098/099`、`ISSUE-052/053`。
`field_diagnostics_projection_defined=true`、`field_diagnostics_audit_key_count=31`、
`field_diagnostics_android_debug_probe_available=true`、`field_diagnostics_android_debug_probe_executed=false`、
`field_diagnostics_target_adapter_defined=true`、`field_diagnostics_target_category_execution_complete=false`、
`release_evidence_target_report_admitted=false`、`release_evidence_runtime_diagnostics_wired=false`、
`release_evidence_retest_workflow_wired=false`、`field_diagnostics_android13_arm64_verified=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W07`。

### P9-W07c Release Retest Workflow progress

状态：`SOFTWARE_DEVELOPED / TARGET_RETEST_PENDING`（2026-07-18）。新增 pure-Java `ReleaseRetestWorkflow`、versioned JSON、
九组 JVM regression 和独立/Stage2/Runtime/CI 门禁。状态目录与既有 GitHub 远程测试合同一致：triage、reproduced、fix-ready、
retest、verified；只允许五条 actor-bound 转换。

进入 retest 必须由 maintainer 绑定严格递增且 source/archive/release-set 均不同的命名 replacement release，并提供 release owner digest。
进入 verified 必须由 target tester 提交身份匹配、GitHub-safe、八类全部执行且全部 PASS 的 W07a TARGET report，同时具备 target/release/
diagnostics owner 与 tester 四方摘要。完整非 PASS report 回到同一 Issue 的 fix-ready，并要求下一轮使用更高 release。

本状态机只返回 metadata decision、workflow digest、target-report admission 与 issue-close eligibility；不发布 Release、不安装 APK、不修改
GitHub Issue，也没有自动关单 API。W07a/W07b/W07c 仓库软件项至此完成；真实 target report、命名 owner/retest 与正式 signer/installer/
rollback 仍由 ISSUE-052/053 外部阻塞，P8 真实 adapter 仍未解锁。

Req IDs：`S2-OBS-001`、`S2-REL-001`、`DEL-001/004/005`；tracking：`DEV-098/099/100`、`ISSUE-052/053`。
`release_retest_state_machine_defined=true`、`release_retest_issue_state_count=5`、
`release_retest_transition_count=5`、`release_retest_replacement_release_published=false`、
`release_evidence_target_report_admitted=false`、`release_evidence_retest_workflow_wired=false`、
`release_retest_github_issue_mutation_wired=false`、`release_retest_automatic_issue_close_allowed=false`、
`release_retest_android13_arm64_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W07`。

### P4-D4a Simulated Scenario/Plan/Graph composition progress

状态：`DEVELOPED / DEBUG_RUNTIME_WIRING_PENDING`（2026-07-18）。在 P9 软件包完成后的全局未完成项审计中，确认 HMI-D4
仍存在一个不依赖真实车身接口的组合缺口。新增 debug-only `SimulatedScenarioGraph`、versioned JSON、八组 JVM regression 和
独立/Stage2/Runtime/CI 门禁。

该组件复用现有 `ScenarioPlanCompiler` 和 control-only `AgentGraphRuntime`。它自动完成 Context、Policy 与 Summary 本地投影；
approval、Effect execution、readback verification 必须转为单一 pending node，只有外部显式提交 typed outcome 才能继续。Cold 自动
推进到 HVAC Effect；parked fatigue 先停在 seat approval；moving fatigue 继续使用 Compiler 的 approval/recline pruning；required
Effect failure 使 Graph 失败关闭。

本增量没有 Android Service/Binder/Client2 wiring，也没有 adapter apply、readback、approval response、生产注册或硬件访问。
下一增量为 P4-D4b debug Runtime Session/Event projection；P8 真实 adapter 和 production authority 仍保持外部阻塞。

Req IDs：`S2-SCN-001`、`S2-GRF-001`、`S2-EFF-001`、`S2-HMI-003/006`、`APP-004`、
`XSC-001/005/006`、`DEL-001/004/005`；tracking：`DEV-101`、`ISSUE-022/026/030/033`。
`simulated_scenario_graph_defined=true`、`simulated_scenario_graph_debug_only=true`、
`simulated_scenario_plan_published=true`、`simulated_scenario_graph_progress_enabled=true`、
`simulated_scenario_android_runtime_wired=false`、`simulated_scenario_client2_wired=false`、
`simulated_scenario_effect_dispatch_enabled=false`、`simulated_scenario_readback_accessed=false`、
`scenario_execution_enabled=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P4-D4a`。
