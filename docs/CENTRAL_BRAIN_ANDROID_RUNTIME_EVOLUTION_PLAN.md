# Central Brain Android Runtime Evolution Plan

版本：0.1
日期：2026-07-12
状态：Approved / In progress

## 目标与范围

本计划把已完成的 Central Brain Python contract/mock 原型演进为可安装在 Android 13 座舱硬件上的用户态中央大脑运行时。架构图仍是需求基线；本计划只补齐现有层内的量产化能力，不增加新的顶层架构层。

本阶段约束：

- 只聚焦 Android 交付，不开发 Linux 前端；既有 Linux 样例保留但不扩展。
- 不修改厂商 Android Framework、BSP、预编译系统组件或芯片 SDK 源码。
- 交付形态为 AI SDK AAR、独立 Runtime Service APK、Demo HMI APK，以及按需启用的 NDK/JNI adapter。
- Python 保留为仿真后端、contract conformance 和回归测试工具，不作为 Android 产品运行时。
- NPU 当前使用 deterministic stub；Ollama 只允许出现在 debug provider；Vendor NPU provider 保持 empty adapter。
- 不开发虚拟化。Driver/HAL 只在公开/vendor SDK 无法满足明确接口时登记最小缺口。

涉及 Req IDs：`APP-004`、`XSC-001`、`XSC-002`、`XSC-003`、`XSC-004`、`XSC-005`、`XSC-006`、`FW-U-003`、`FW-U-004`、`FW-U-006`、`FW-U-007`、`NV-F-001`、`NV-F-011`、`NV-F-012`、`NV-G-003`、`NV-G-004`、`NV-G-005`、`NV-G-006`、`NV-G-007`、`NV-P-002`、`DEL-001`、`DEL-003`、`DEL-004`、`DEL-005`。

## 成熟度模型

任何模块都必须使用下列单一状态，禁止用 `readiness=true` 代替实现成熟度：

| 状态 | 含义 | 最低证据 |
| --- | --- | --- |
| `contract_defined` | Schema、边界、错误和 Req ID 已定义 | contract parse + static check |
| `prototype_implemented` | 仿真环境中存在可执行实现 | deterministic unit/smoke test |
| `android_integrated` | Android 13 APK/AAR 路径可运行 | Binder/instrumentation + API 33 device test |
| `hardware_validated` | 目标硬件与 vendor 接口已验证 | target-device smoke + fault/rollback evidence |
| `production_qualified` | 性能、安全、隐私、升级和运维门禁关闭 | signed acceptance package |

状态规则：

- `python_prototype_current_scope_complete=true` 只说明旧 Python 原型范围完成，不提升任何模块到 `android_integrated`。
- readiness、checklist、rollup 和 no-store evidence 接口最多证明 `contract_defined`。
- mock、Ollama 和 Client2 HTTP 演示最多证明 `prototype_implemented`。
- hardware empty interface 永远不能标记为 `hardware_validated`。
- 每次状态提升必须记录验证命令、设备/ABI、commit 和未关闭风险。

## 分阶段实施

| 阶段 | 交付增量 | 退出条件 | 主要 Req IDs |
| --- | --- | --- | --- |
| R0 | 问题/偏差登记、成熟度模型、版本一致性门禁 | ISSUE-021..025、DEV-018/019、API baseline check 进入 CI 风格检查 | XSC-001..006, DEL-001/003/004 |
| R1 | Gradle 多模块 Android 工程 | `central-brain-sdk` AAR、`runtime-service` APK、`demo-hmi` APK 可在 API 33 构建安装 | DEL-001, NV-P-002 |
| R2 | 量产业务 AIDL 与诊断 AIDL 拆分 | typed Parcelable、快速返回、callback、cancel、Binder death 测试通过 | XSC-006, NV-G-003/006, NV-P-002 |
| R3 | Job Supervisor 与可信身份 | 任务状态机、Binder UID/package/signature capability mapping、default deny 生效 | NV-F-001, FW-U-007, NV-G-005/006 |
| R4 | Durable workflow | Room/SQLite checkpoint、pending effect、idempotency/outbox、重启恢复通过 | FW-U-004, NV-F-001, NV-G-006/007 |
| R5 | Scheduler 与 Model Router | deadline/priority/quota/cancel + Stub/Ollama-debug/Vendor-empty provider | APP-004, NV-F-011, NV-G-004/006 |
| R6 | Event、Memory、Skill 与 middleware | callback/cursor、memory lifecycle、signed built-in Skill、治理链测试通过 | FW-U-003/006/007, NV-G-005/007 |
| R7 | Observability、Client2 SDK 迁移与验收 | Client2 不再直连固定 HTTP；trace/metric、端到端和故障测试通过 | APP-004, NV-F-012, XSC-005/006, DEL-001 |

### R1 实施状态

- `R1A Gradle foundation` 已完成：`central-brain/android-runtime` 使用 AGP `8.10.1`、Gradle Wrapper `8.11.1`、JDK 17、`compileSdk=36`、`minSdk=33`，Wrapper 固定官方分发包 SHA-256。
- 已真实构建并验证 `central-brain-sdk-debug.aar`、`runtime-service-debug.apk`、`demo-hmi-debug.apk`；SDK JUnit、APK package/minSdk 和 APK v2 签名校验通过。
- `runtime-service` 当前是非导出、无网络权限、无 Binder 的生命周期边界；R1 不提前引入 R2 typed/async AIDL。
- 工作区已安装 Android SDK Platform 33 revision 3 与 Google APIs x86_64 system image revision 17，并创建 `central_brain_api33_x86_64` AVD；保留 API 36 AVD 用于前向兼容测试。
- 当前本地 Android SDK command-line tools 只识别 XML version 3，而已安装 SDK 含 version 4 metadata；构建成功但存在工具版本警告，量产 CI 前必须对齐 command-line tools 与 SDK。
- `R1B device lifecycle check` 已完成：新增 DUMP-protected、debug-only `RuntimeProbeActivity` 和 `tools/install_central_brain_android_runtime.sh`，可安装 Runtime/Demo、启动非导出 Service、核验进程/前台 Activity/UI 并输出硬件边界。
- API 36 x86_64 AVD 兼容测试通过，输出 `runtime_service_running=true`、`demo_hmi_resumed=true`、`demo_ui_contract_defined=true`，同时正确保持 `r1_api33_exit_criteria_met=false`；`--require-api-33` 在 API 36 上按预期失败。
- Release APK manifest 已验证不含 `RuntimeProbeActivity`，只保留 `CentralBrainRuntimeService exported=false`。
- `R1C API 33 exit` 已完成：`tools/install_central_brain_android_runtime.sh --skip-build --serial emulator-5554 --require-api-33` 在 Android 13/API 33/x86_64、`1920x1080` AVD 上通过，输出 `runtime_service_running=true`、`demo_hmi_resumed=true`、`demo_ui_contract_defined=true`、`r1_api33_exit_criteria_met=true`。
- 验证设备 fingerprint 为 `google/sdk_gphone64_x86_64/emu64x:13/TE1A.240213.009/12342917:userdebug/dev-keys`，Runtime 与 Demo `versionName=0.1.0`，Runtime log 固定 `maturity=contract_defined hardware_accessed=false`。
- R1 退出条件已关闭。整体 Runtime 暂不提升为 `android_integrated`，因为成熟度模型还要求 R2 production Binder/instrumentation 证据。
- Req IDs：`XSC-001`、`XSC-004`、`XSC-005`、`XSC-006`、`NV-F-001`、`NV-P-002`、`DEL-001`、`DEL-003`、`DEL-004`、`DEL-005`。

### R2 实施状态

- `R2A compiled AIDL contract` 已完成：`central-brain-sdk` 开启 AIDL，编译 5 个 production Parcelable、3 个 diagnostic Parcelable、production/callback/diagnostic 三个接口，并冻结 `aidl-api/v1.sha256`。
- Production 只包含 typed agent task submit/cancel/status，禁止 JSON/Bundle/fd/shared memory；callback 为 `oneway`，生成代码使用 `IBinder.FLAG_ONEWAY`；diagnostic 为只读 cursor page，`MAX_PAGE_SIZE=100`。
- Gradle 应用层无法使用需 Soong/AOSP 构建的 `aidl_interface`/VINTF stable AIDL，因此使用显式 `getProtocolVersion/getProtocolHash` 和 checksum freeze；该限制继续记录在 `DEV-018`/`ISSUE-021`，不得宣称 VINTF stable。
- `R2B typed Binder runtime` 已完成：Runtime APK 发布独立 production/diagnostic Service，分别受 `BIND_RUNTIME`/`ACCESS_DIAGNOSTICS` signature 权限保护；Demo 只请求 production 权限。
- `CentralBrainClient` 使用 explicit component 和 SDK AAR 的 narrow package visibility query，提供 typed submit/cancel/status、callback executor 和 service `DeathRecipient`；Runtime 为每个 remote callback 注册 `DeathRecipient`。
- Deterministic task runner 在单线程 executor 上发出 ACCEPTED/RUNNING/COMPLETED，cancel 只在 Binder 线程标记状态并异步通知，重复 cancel 幂等；diagnostic 为只读有界 cursor page。所有路径固定 `hardware_accessed=false`。
- API 33 x86_64 设备门禁已验证 typed Binder connect/version/hash、completion callback、duplicate cancel、两个 signature permission 拒绝和 diagnostic page；release APK 已确认排除两个 DUMP-protected debug probe。
- `R2C Binder lifecycle/race instrumentation` 已完成：SDK death recipient 与具体 Binder 实例绑定，stale/duplicate death 被忽略，`reconnect()` 明确执行 unbind/rebind，terminal 后排队 update 被抑制。
- Custom Android instrumentation 在 API 33 x86_64 上 force-stop Runtime，验证活动任务只收到一次 `SERVICE_DIED`、只通知一次 disconnect、显式重连后新任务完成；15-task 并发测试同时得到 completed/cancelled 且每任务只有一个 terminal callback。
- Debug-only client-death probe 在独立 app process 提交任务后被 force-stop；保持 started 的 Runtime 观察 callback Binder death 并以 `CANCEL_REASON_CLIENT_DIED` 取消。所有测试组件受 DUMP 保护且 release APK 不包含。
- R2 退出条件已关闭，`central-brain-sdk`/typed Android Protocol Binding 提升到 `android_integrated`。这不代表 R3..R7、真实硬件或量产资格完成；`DEV-018`/`ISSUE-021` 继续跟踪旧 JSON Binder/HTTP compatibility migration。
- Req IDs：`XSC-001`、`XSC-004`、`XSC-005`、`XSC-006`、`NV-F-001`、`NV-G-003`、`NV-G-006`、`NV-G-007`、`NV-P-002`、`DEL-001`、`DEL-003`、`DEL-004`。

### R3 实施状态

- `R3A Job Supervisor foundation` 已完成：Runtime Service 新增独立的纯 Java `JobSupervisor`，显式限制 `ACCEPTED -> RUNNING -> COMPLETED` 以及到 `FAILED/CANCELLED` 的合法转换，拒绝跳过阶段、终态再转换和进度回退。
- 注册表上限固定为 128 条；活动任务及尚未完成终态 callback 结算的任务不会被淘汰，容量耗尽时拒绝新任务；已结算终态记录保留 5 分钟，并可在过期或容量压力下确定性淘汰。该策略当前仍是内存态，R4 才迁移到 SQLite durable owner。
- 每个 production Binder 方法在 Binder 身份仍有效时读取 `Binder.getCallingUid()`，通过 `PackageManager.getPackagesForUid`、当前 APK signer SHA-256 和 `UserManager` Android user serial 构造不可由 `AgentTaskRequest` 伪造的调用者快照。
- task owner 由 Supervisor 保存；非 owner 的 status 返回 `UNKNOWN`，cancel 返回 false，无法区分任务不存在与越权。身份无法完整解析时默认拒绝。请求体没有 permission/capability 字段。
- JVM 单测覆盖合法/非法状态转换、进度单调、终态唯一、重复取消、owner 隔离、全活动容量耗尽、终态 callback 结算门禁、终态压力淘汰、retention 到期以及多包/签名配对。
- API 33 x86_64 实测日志输出 `uid=10175 userSerial=0 packages=[com.centralbrain.demo] resolved=true`；标准 typed Binder 完成/取消门禁和 R2C service/client death、reconnect、15-task race 回归均通过，输出 `trusted_caller_identity_resolved=true`、`hardware_accessed=false`。
- `R3B capability policy` 已完成：Runtime APK 内置严格解析的 V1 XML，唯一默认值为 deny；Demo production principal 和 Runtime diagnostic principal 都必须同时匹配字面包名与 Runtime 当前 signer 完整集合，禁止 wildcard、请求体授权和 signer 交集放宽。
- `runtime.protocol.read`、`runtime.task.submit`、`runtime.task.status.own`、`runtime.task.cancel.own` 分别在每个 production Binder 方法执行，`runtime.diagnostics.read` 在 diagnostic version/hash/page 执行。共享 UID 可合并多个已配置包的 capability，但任一已配置包 signer 不一致即整体拒绝。
- 新增 test-only `policy-probe` APK。它与 Runtime/Demo 使用同一 debug signer，成功获得外层 `BIND_RUNTIME` 和 `ACCESS_DIAGNOSTICS` signature permission 并成功 bind，但包名未配置；API 33 上 production 与 diagnostic capability 全部抛出 `SecurityException`，audit reason 均为 `PACKAGE_NOT_CONFIGURED`。
- API 33 R3B 输出 `outer_signature_permission_passed=true`、`outer_diagnostic_signature_permission_passed=true`、`test_only_install_enforced=true`、`allowed_client_capabilities_verified=true`、`unknown_client_default_deny_verified=true`、`diagnostic_capability_default_deny_verified=true`、`package_and_current_signer_mapping_verified=true`、`production_capability_denial_audited=true`。Probe 不属于标准交付产物。
- `R3C1 action governance core` 已完成：纯 Java `ActionGovernancePolicy` 只从 Runtime-owned stable Action catalog 派生读取、舒适控制、驾驶干扰、诊断写、OTA 五类风险，未知 Action 和不安全状态默认拒绝；读取/舒适决策仅为 policy-only，所有结果固定 `dispatchAllowed=false`。
- Safety/Vehicle State 只能通过 `SafetyVehicleStateProvider` 输入。当前 `RuntimeOwnedSafetyVehicleStateProvider` 是 caller-independent、hardware-free fixture，固定 `RUNTIME_OWNED_STUB`、`hardwareBacked=false`、`productionTrusted=false`，不能替代目标 VHAL/Safety Runtime 证据。
- `InMemoryApprovalRegistry` 只接收停车状态下三类 high-risk `APPROVAL_REQUIRED` 决策，按完整 trusted caller snapshot 隔离 owner，pending 不因容量压力淘汰，并支持有界 expiry/cancel；它明确 `supportsApprovalGrant=false`、`isDurable=false`，不伪造审批授权或重启恢复。
- R3 尚未关闭：R3C2 仍需独立 typed Governance AIDL/Service、capability 和 API 33 跨包拒绝/允许验证；R4 再提供 durable approval/checkpoint/outbox。`CentralBrainSdk.EVOLUTION_STAGE` 暂不提升，`DEV-019`/`ISSUE-023` 保持 Open。
- Req IDs：`XSC-001`、`XSC-004`、`XSC-005`、`XSC-006`、`FW-U-007`、`NV-F-001`、`NV-G-005`、`NV-G-006`、`NV-G-007`、`NV-P-002`、`DEL-001`、`DEL-003`、`DEL-004`、`DEL-005`。

## 架构落点

| 架构图层 | 本计划新增实现 |
| --- | --- |
| AI SDK | typed Android client、session/task facade、callback API |
| Uni Info Bus | `EventEnvelope`、Action 幂等键、低频 callback/cursor |
| SOA | 统一 Operation envelope 和副作用 outbox |
| AIOS Kernel | Job Supervisor、Scheduler、Checkpoint、Model Router |
| Runtime & Governance | Binder identity、capability、approval、QoS、audit、middleware |
| Protocol Binding | production/diagnostic AIDL 分离和版本协商 |
| Native adapters | deterministic stub、Vendor NPU empty provider、按需 JNI/C ABI |

## 问题与偏差绑定

- `ISSUE-021`: Android Binder 业务/诊断接口、typed/async/cancel/death 语义。
- `ISSUE-022`: durable task/session/checkpoint/idempotency/outbox owner。
- `ISSUE-023`: Android 可信身份、capability 和高风险审批。
- `ISSUE-024`: Model Router、资源准入、fallback 和 NPU empty-provider 边界。
- `ISSUE-025`: Event、Memory、Skill 生命周期和治理链。
- `DEV-018`: 当前 107 个 String/JSON AIDL 方法与同步 HTTP proxy 偏离目标 Protocol Binding。
- `DEV-019`: 当前请求体自报权限与进程内非持久状态偏离目标 Runtime & Governance/AIOS Kernel。

## 自动推进规则

- 每轮只实现一个可独立验证的增量。
- 每轮先执行强制 preflight，再更新相关 Req ID、问题、偏差和路线图。
- 每轮通过相关 static/unit/smoke/device 验证后提交 Git。
- 每轮提交后直接选择下一未完成阶段，不等待用户再次确认。
- 遇到 vendor SDK、系统签名、SELinux、硬件 ABI 或真实 NPU 阻塞时保持 empty adapter，并记录 owner 和解除条件；不得伪造完成状态。
