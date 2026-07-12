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
- `R3C2 typed Governance Binder` 已完成：新增独立 `ICentralBrainGovernance`、四个 structured Parcelable、Governance V1 checksum freeze、`CentralBrainGovernanceClient` 和 `CentralBrainGovernanceService`；原 task/diagnostic V1 checksum 不变。
- Governance Service 使用独立 `BIND_GOVERNANCE` signature permission，并在内层分别执行 protocol/evaluate/request/status-own/cancel-own capability。`ActionRequest` 不包含 risk、Safety/Vehicle State、caller、package、signer 或 permission assertion；接口故意没有 approve/grant 方法。
- API 33 allowed Demo 已验证 read/comfort policy-only、OTA approval-required、pending owner status 和 duplicate cancel；同 signer 未配置 Probe 已通过外层 Governance permission 并成功 bind，但五项 Governance capability 均以 `PACKAGE_NOT_CONFIGURED` 拒绝。
- API 33 输出 `governance_typed_binder_connected=true`、`action_risk_classes_verified=true`、`runtime_owned_state_provider_verified=true`、`high_risk_pending_approval_verified=true`、`approval_cancel_verified=true`、`governance_capability_default_deny_verified=true`、`approval_grant_supported=false`、`approval_durable=false`、`service_dispatch_triggered=false` 和全部 no-hardware flags。
- R3 退出条件已关闭，`CentralBrainSdk.EVOLUTION_STAGE=R3_TRUSTED_GOVERNANCE`，成熟度保持 `android_integrated`。`DEV-019`/`ISSUE-023` 继续跟踪目标 VHAL/Safety Runtime source、量产签名/审批 authority 和 R4 durable approval/checkpoint/outbox，不能据此宣称 production qualification。
- Req IDs：`XSC-001`、`XSC-004`、`XSC-005`、`XSC-006`、`FW-U-004`、`FW-U-007`、`FW-S-005`、`NV-F-001`、`NV-G-005`、`NV-G-006`、`NV-G-007`、`NV-P-002`、`DEL-001`、`DEL-003`、`DEL-004`、`DEL-005`。

### R4 实施状态

- `R4A Room durable schema` 已完成实现：Runtime APK 引入 AndroidX Room `2.8.4`，导出 `CentralBrainDatabase` v2 schema，开启 app-private WAL，并禁止 destructive migration fallback。
- Schema 固定 8 张表：`runtime_session`、`runtime_task`、`task_checkpoint`、`pending_effect`、`effect_outbox`、`approval_request`、`audit_event`、`event_cursor`；task/approval/effect/outbox/event cursor 具备 owner/idempotency unique index，checkpoint/effect/outbox 具备明确 foreign-key ownership。
- `MIGRATION_1_2` 从旧 task/approval 最小表迁移，使用 `legacy:<id>` 回填幂等键并保留状态/owner/timestamp。debug-only DUMP probe 使用隔离数据库验证 schema version、table count、WAL 和 legacy task/approval 数据保留。
- 当前 schema 只保存 payload/checkpoint/outbox/audit digest，不保存 raw utterance、模型输出、车辆帧或 signer bytes。数据库尚未接入 production Service，固定 `durable_dispatch_enabled=false`。
- `R4B1 durable task admission` 已完成：stable owner fingerprint 由 Android user serial + canonical package/current-signer pairs 计算，排除易变 UID；`DurableTaskRepository` 在单个 Room transaction 中完成 owner/idempotency lookup、task insert 和 acceptance audit insert。
- Exact replay 返回原 task 且不重复写 task/audit；同 owner/key 的不同 session/client request/payload digest 明确冲突，另一 owner 可独立复用 key。API 33 隔离 probe 在关闭并重开数据库后验证 2 owners = 2 tasks = 2 acceptance audits。
- R4B1 尚未接入 production Runtime/Governance Service，固定 `runtime_repository_wired=false`；task transition/checkpoint/terminal delivery、approval repository 和 restart recovery 仍待后续 R4B/R4C。
- `R4B2 durable Runtime wiring` 已完成：production Runtime 在返回 handle 前完成 durable admission；ACCEPTED sequence 1、后续 transition + checkpoint + audit、terminal callback settlement 均采用明确 Room transaction，Job Supervisor 只在 durable transition 成功后推进。
- Same-process exact replay 返回同 handle 并支持最多 4 个有界 observer callback；短 admission lock 覆盖 Room admission 到 live-map publish，Demo/API 33 已验证 sequential + concurrent replay callback 完成。Exact existing replay 即使原 deadline 已过仍可返回，new expired request 不产生 task。
- Owner status 可 fallback 到 durable metadata；数据库存在但当前进程未恢复的 replay 明确回调 retryable `ERROR_INTERNAL`，固定 `task_recovery_enabled=false`，不会重复执行。API 33 service-death/reconnect instrumentation 已输出 `durable_recovery_pending_verified=true`；后续 R4C 负责定义 restart handling/fault injection，R4C1 最终选择 fail-closed reconciliation 而不是执行续跑。
- `R4B3 durable approval` 已完成：Governance 使用 owner-scoped Room request/status/cancel/expiry/audit；跨 DB reopen exact key/action replay、mismatch conflict、owner isolation、cancel idempotency 和 lazy expiry 均有 API 33 证据。`ApprovalStatus.durable=true`，但 grant/dispatch 仍为 false。
- `R4C1 fail-closed restart reconciliation` 已完成：Runtime 在单线程后台执行器中先完成启动对账，并以 Future 屏障阻止 task submit/cancel/status 越过对账。ACCEPTED/RUNNING 与未完成终态回执的 COMPLETED 在单 Room transaction 中转为 FAILED，追加 checkpoint/audit，且保持 terminal delivery 未结算。
- 由于当前只保存 digest/metadata，不保存可重放的原始 utterance/result，R4C1 不恢复执行。Exact replay 返回原 handle，按顺序回调 durable FAILED status 和 retryable `ERROR_INTERNAL`，回调尝试后再幂等结算；SDK 对每个 task callback 使用串行投递器，避免 update/terminal 乱序。
- API 33 已验证对账幂等、active/incomplete-completion 两类失败关闭、进程死亡后同 handle/FAILED replay、终态唯一和 cancel-completion race；固定 `restart_reconciliation_enabled=true`、`task_execution_resume_enabled=false`、`durable_dispatch_enabled=false`。
- `R4C2A effect prepare and claim` 已完成：RUNNING task 的 effect/outbox/audit 在单 transaction 中 prepare，调用方 key 先与 owner fingerprint 做域分离哈希以适配现有全局唯一索引；exact reopen replay 不重复写，mismatch 冲突，另一 owner 可复用原始 key。
- Claim 只选择 due PENDING + PREPARED + RUNNING 组合，在单 transaction 中把 effect/outbox 转为 IN_FLIGHT、递增 attempt 并审计。进程中断后 repository 可把 IN_FLIGHT 幂等回退到队尾 PENDING，先服务等待更久的工作，再以 attempt+1 重新 claim。
- `R4C2B effect retry and terminal states` 已完成：IN_FLIGHT claim 可按 expected attempt 事务性 success、bounded-delay retry 或 dead-letter，PREPARED/PENDING 可按当前 attempt cancel；所有变更校验 owner/effect/outbox，exact replay 不重复审计，变化的 digest 或 retry delay 冲突。
- 默认最多 claim 3 次，DAO 不再选择 exhausted row，最终 attempt 禁止 retry。若进程在最终 IN_FLIGHT claim 后崩溃，reopen reconciliation 失败关闭为 FAILED/DEAD_LETTER 并写 `EFFECT_CLAIM_EXHAUSTED`；第二次对账不再修改。该终态不证明外部副作用是否发生。
- `R4C3A effect adapter contract and fault matrix` 已完成：安全 adapter 必须以持久化 token 去重并在重复 apply 时返回原结果，status query 必须 linearizable 且 APPLIED 返回同一原始结果证据；transient canonical payload/envelope 必须与 Room digest 一致且采用 defensive copy。
- Status reconciler 只 query、不 apply：APPLIED 收敛成功，仍有次数的权威 NOT_APPLIED 才可重试，REJECTED/UNKNOWN 失败关闭，query unavailable 保持 IN_FLIGHT，最终 NOT_APPLIED 进入 dead letter。API 33 已覆盖 apply 前/后崩溃、重复 apply、不可用/未知状态和终态回放。
- `R4C3B effect material source and activation gate` 已完成：material source 必须 available/production-assured、跨进程 durable、at-rest encrypted、effect-bound integrity、bounded retention 且支持 delete；gate 同时要求 R4C3A safe adapter，并返回稳定 blocker，不调用 apply/query。
- 当前 main source 只有 `EmptyEffectMaterialSource`，正向合规路径仅由 debug synthetic source 验证；API 33 已覆盖 empty/test-only blocker、Room reopen resolution、defensive copy、digest mismatch/missing material 和 no-side-effect。固定 `production_effect_delivery_activation_allowed=false`、`raw_effect_material_persisted=false`。
- `R4C3C production fail-closed activation visibility` 已完成：Runtime/Diagnostic Service 共享 immutable current snapshot；Runtime startup log 与 dumpsys、现有 read-only diagnostic page 都暴露同一 blocked/empty-source/blocker 结果。Snapshot 不 resolve material，不 query/apply adapter，也不打开 effect repository dispatch。
- API 33 已验证 diagnostic record、Runtime log、dumpsys、R2C lifecycle/race 和 R3 default-deny；release 仍只有 3 个 signature-protected Service、0 probe/activity。固定 adapter/material/apply/status/dispatch/hardware false。
- R4 durable-workflow foundation 退出条件关闭，`CentralBrainSdk.EVOLUTION_STAGE=R4_DURABLE_WORKFLOW`，成熟度保持 `android_integrated`。这不代表真实 effect delivery、target key/retention/delete/trusted-clock 或 hardware validation；这些继续由 ISSUE-022/Driver-HAL gate 和后续集成跟踪。
- Req IDs：`XSC-001`、`XSC-005`、`XSC-006`、`FW-U-004`、`NV-F-001`、`NV-G-006`、`NV-G-007`、`NV-P-002`、`DEL-001`、`DEL-003`、`DEL-004`、`DEL-005`。

### R5 实施状态

- `R5A1 model provider contract` 已完成：新增纯 Java `ModelProvider`，统一 descriptor、health/lifecycle snapshot、warmup、infer/stream、cancel、metrics、fault 和 close 语义；stream chunk 有界且 defensive copy。
- Descriptor 默认失败关闭：deterministic stub 与 Ollama debug 不得声明 hardware-backed/production，EMPTY provider 不得声明 inference slot、warmup/stream/cancel/metrics 或 fallback。
- 当前只登记 `deterministic.stub` 与 `vendor.npu.empty` 两个 immutable profile。前者 TEST_ONLY/COLD、后者 EMPTY/UNAVAILABLE；两者 `implementationConfigured=false`、`routingEnabled=false`，production Runtime/Governance 不引用 provider。
- JVM 与 debug-only API 33 probe 验证 profile、unsafe descriptor rejection 和 no-hardware/no-routing 边界；frozen AIDL、Room schema、标准 artifact shape 均未改变。
- `R5A2 inference resource scheduler` 已完成：pure-Java synchronized state machine 使用 Runtime-policy-only effective priority、elapsed-realtime task/queue deadline、global/per-owner queue/running quota、provider slot 和 priority/deadline/FIFO 稳定排序。
- Queued cancel 本地移除；RUNNING cancel/deadline 只转 `CANCEL_REQUESTED` 并产生 lease-bound provider directive。Scheduler 不调用 provider；迟到 completion 仍作为 terminal acknowledgement 释放 slot，但本地映射为 CANCELLED/DEADLINE_EXCEEDED 且不接受输出，最终 task 状态仍归 Job Supervisor/durable workflow。
- 当前 profile 仍转换为 disabled route；只有 `test.*` contract route 可在 unit/debug probe 中 enabled。Production Runtime/Governance 不引用 Scheduler，AIDL/Room/artifact shape 不变。
- `R5B1 deterministic stub provider` 已完成：TEST_ONLY implementation 支持 COLD->READY warmup、单 slot async infer、两段 ordered stream、cancel acknowledgement、bounded terminal history、metrics 和 retryable/terminal/fault-isolated injection。
- 同 model/input digest 跨 provider instance 输出一致；deadline/slot/duplicate/model mismatch 默认拒绝。Provider 不访问网络/vendor/hardware，descriptor 固定 non-production/non-hardware。
- JVM/API 33 只实例化 debug/test provider；immutable production profile 仍 `implementationConfigured=false`、`routingEnabled=false`，Runtime/Governance/Scheduler/Router 不引用 instance。
- `R5B2 test-only model router` 已完成：唯一 `test.deterministic.stub` route 把 trusted request、Scheduler admission/lease/directive/settlement 与 deterministic provider infer/stream/cancel 连接，production 无 factory/wiring。
- Router 对 provider/request/lease identity 失败关闭；exact active replay 保留原 observer，changed duplicate 拒绝，duplicate terminal 只结算一次。Queued/running cancel、queue/running deadline、retryable/fault-isolated terminal 均有 JVM/API 33 evidence，fallback 固定 `NO_FALLBACK`。
- `R5C1 production-safe model runtime readiness` 已完成：immutable snapshot 经 Runtime log、protected dumpsys 和现有 Diagnostic Binder page 暴露 profile configuration/lifecycle/health/detail code 与 ordered activation blockers，不构造或执行 Provider/Scheduler/Router。
- Deterministic profile 明确 TEST_ONLY/COLD/HEALTHY/NOT_WIRED，Vendor NPU 明确 EMPTY/UNAVAILABLE/UNAVAILABLE；contract/test implementation availability 与 production activation 分离，AIDL/Room/artifact shape 不变。
- `R5D1 Android 13 application-layer deployment acceptance` 已完成 tooling/emulator evidence：校验 API 33/ABI/fingerprint、artifact hash/signer、普通 UID、`/data/app` 安装、三项 signature-protected Service、no-INTERNET/no-native-payload 和 fail-closed Model Runtime。
- R5 contract/test software track 已关闭，可进入 R6；production evolution stage 仍保持 R4 durable foundation。物理目标应用层验收、production Provider/Router、Ollama/Vendor NPU 和 hardware qualification 不包含在关闭声明内，继续由 ISSUE-024、DEV-019 与 `DRV-GAP-001` 跟踪。
- Req IDs：`APP-004`、`XSC-001`、`XSC-004`、`NV-F-011`、`NV-G-004`、`NV-G-006`、`DEL-001`、`DEL-004`、`DEL-005`。

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
