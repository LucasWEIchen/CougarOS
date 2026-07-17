# Central Brain Android 13 开发路线图

版本：1.1
日期：2026-07-17
状态：Stage 2 P3 in progress

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
| S2-P4 | 场景与 Effect 编排 | “我冷了”“我累了”“休息模式”等 | 未开始 |
| S2-P5 | Client2 中控 HMI 闭环 | 意图/计划/执行/结果、可观察编排链、HVAC/Seat Effect 详情、approval/undo | 未开始 |
| S2-P6 | Memory/Event/Model 集成 | privacy lifecycle、proactive trigger、model routing | 未开始 |
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

下一实现工作包为 `P4-W02 Cockpit HMI state/reducer/reconnect`，把 typed stream reduce 为 immutable HMI state，
补 Activity lifecycle ownership 和隐藏/recreate state 恢复；不得把 legacy 文本投影作为权威状态。

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
client2_scenario_alias_map_count=12
client2_session_snapshot_verified=true
client2_session_event_sequence_verified=true
client2_session_reconnect_replay_verified=true
client2_session_duplicate_event_suppressed=true
client2_legacy_stream_replacement_verified=true
client2_session_android13_arm64_verified=true
client2_smali_descriptor_unchanged=true
cockpit_hmi_state_reducer_implemented=false
cockpit_demo_control_loop_implemented=false
implementation_stage=P4-W02
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
