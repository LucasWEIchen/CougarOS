# Android 13 座舱域交付目标

版本：3.8

日期：2026-07-17

## 交付对象

本阶段只面向黑盒 Android 13 座舱域软件工程师、目标硬件测试人员和 OEM/Vendor 集成 owner。
Python/REST/Linux 仿真运行时已经退役，不再构成开发或交付产物。

覆盖 Req ID：`APP-004`、`XSC-001`、`XSC-004..006`、`NV-F-001/011/012`、
`NV-G-003/005/006/007`、`NV-P-002`、`KH-003/006`、`DEL-001/003/004/005`。

## 2026-07-16 当前交付范围

| 交付项 | 状态 | 说明 |
| --- | --- | --- |
| Java SDK AAR | 已形成 | typed Runtime/Governance/Diagnostics AIDL client；Session、Plan/Node、Event/callback 与 Effect/Approval contract |
| Native Runtime AAR | 已形成 | C ABI V1/JNI，arm64-v8a/x86_64 |
| Runtime Service APK | 已形成 | signature Binder、Room、Governance、readiness |
| Demo HMI APK | 已形成 | 维护和应用层验收 |
| Client2 Demo APK | 可选 | 当前为底部导航触发悬浮面板、12 场景和 typed Binder；尚无 HVAC/Seat 控制页 |
| Client2 中控 UI/UX 设计稿 | 已形成 | 四阶段原型、可观察自动化链、1920x1080 安全框、60% 半透明玻璃和四张 PNG；仅设计资产 |
| Android 13 安装/验收 | 已形成 | dry-run、signer guard、ADB、恢复矩阵 |
| GitHub 源码/文档基线 | 已形成 | 完整正式工程文件、首页架构/进度、pre-push/Actions 门禁 |
| Vendor NPU adapter | 空接口 | 保留 ModelProvider/C ABI；未接硬件 |
| Vehicle adapter | 空接口 | 未获得 AAOS/OEM 车辆 API/权限 |
| Driver/HAL | 未触发 | 仅保留接口矩阵和 gap gate |
| Linux frontend | 当前不交付 | 未来需要新的非 Python 正式工作包 |

`python_prototype_runtime_maintained=false`、`production_ready=false`、
`target_hardware_validated=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`。

## 2026-07-16 Client2 中控 HVAC/Seat 交付规划

`S2-HMI-001..006` 将自然场景意图和 HVAC/Seat Effect 明确为 `com.tuanjie.urasclient2` APK 内的
中控屏交付内容，不是独立 Demo HMI，也不是只显示模型回复的文本功能。当前已完成 HMI-D0 需求、
UX、模块、工作包、验收和高保真视觉基线；现有 hybrid bundle 中的 Client2 APK 不包含这些四阶段界面。

P4 计划用 24-32 人日交付意图/计划/执行/结果四阶段、可观察自动化链和 HVAC/Seat Effect 详情，
以及 manual/AI 共用 Session、Governance、Effect、readback、partial、retry、undo 和 restart recovery
的闭环。在真实车身信号尚未接入时，
Android debug/test 版本使用明确标记为 `SIMULATED` 的 Digital Twin/Effect adapter；release/production
缺少 target adapter 时必须显示 unavailable，不得回退仿真。

```text
aios_intent_orchestration_ux_ready=true
cockpit_hmi_design_mockups_ready=true
cockpit_hmi_1920x1080_safe_frame_verified=true
cockpit_hmi_translucent_material_ready=true
cockpit_hvac_surface_implemented=false
cockpit_seat_surface_implemented=false
cockpit_demo_control_loop_implemented=false
real_vehicle_effect_adapter_available=false
```

HMI-D4 只证明 Android 13 APK 的演示闭环。真实 HVAC/Seat 控制、车辆回读、权限、Safety owner、
fault 和 rollback 仍属于 P8/HMI-D5 外部集成，不因界面或 Digital Twin 验收而完成。

## GitHub 完整项目交付边界

Private `LucasWEIchen/CougarOS` 必须承载所有受维护的 Android Java/AIDL/C/JNI 源码、Client2
可复验 patch、中控 UI/UX 原型与设计稿、接口/配置、构建/测试/安装/打包工具和
`CENTRAL_BRAIN_*` 工程文档。每个完成增量
必须在本轮 commit、push 并通过远端 `contract` 检查；影响架构或状态时必须同时更新根 README
的 Mermaid 架构图、已开发/未开发表和近期记录。

`apks/`、`reverse/`、`builds/`、原始日志、设备身份、target-input、签名材料、用户/模型/车辆
payload 和本机环境不属于源码交付，不得提交。经过 manifest、hash、signer 和隐私审查的 APK/AAR
归档只通过 GitHub Release 交付。门禁：

```bash
bash tools/check_central_brain_github_repository_completeness.sh
bash tools/check_central_brain_github_publication_tree.sh HEAD
bash tools/check_central_brain_root_readme.sh
```

状态：`github_source_of_truth=true`、`github_sync_required=true`、
`maintained_project_files_synced=true`、`github_homepage_architecture_current=true`。

## 2026-07-17 AIOS Stage 2 交付范围

Stage 2 P0 设计基线、`P1-W01 Session DTO/AIDL`、`P1-W02 Plan/Node DTO/AIDL`、
`P1-W03 Typed Event DTO/AIDL`、`P1-W04 Effect/Approval DTO/AIDL`、
`P1-W05 SDK facade v2`、`P1-W06 Room v4 schema`、`P1-W07 Contract v2 aggregate check`、
`P2-W01..P2-W12 Context/Scenario/Simulation foundation`、`P3-W01 Agent Graph Runtime state machine` 和
`P3-W02 Typed node executors` 已完成，下一工作包为 `P3-W03 CheckpointSerializer`。P1-P7 交付必须进入
Android Java/AIDL/C 工程及其测试，不得恢复 Python gateway。P8 的 AAOS/Vendor/NPU adapter 只有在
owner、API/ABI、权限、Safety、smoke、fault 和 rollback 证据齐全后才能激活。

P1-W01 当前交付为 SDK AAR 中的 5 个 Session parcelable、独立 Session interface、Java validator、
JVM/Android Parcel tests 和 checksum checker。`session_contract_v1_defined=true`，但
`session_runtime_service_published=false`、`session_runtime_persistence_wired=false`；因此现有 hybrid
bundle 不能宣称 Session 场景闭环，直到后续 Runtime/SDK/HMI 工作包进入新的受审查交付版本。

2026-07-17 在 Android 13/API 33 ARM64 物理控制器上安装临时 SDK instrumentation APK，5 个 DTO
round-trip、oversize 和 unknown-version reject 均通过，随后卸载。状态：
`session_parcel_physical_android13_arm64_verified=true`；该应用层 Parcel 证据不提升
`target_hardware_validated=false`。

P1-W02 当前交付为 SDK AAR 中的 4 个 Plan/Node parcelable、11 类节点 allowlist、Java DAG/容量/
重试/补偿 validator、JVM/Android Parcel tests、`plan-v1.sha256` 和独立 checker。
`plan_contract_v1_defined=true`，但 `plan_runtime_published=false`：当前 bundle 不包含 Plan Compiler、
Graph 调度、Room v4 持久化或可由 HMI 调用的 Plan Service。

2026-07-17 同一 Android 13/API 33 ARM64 物理控制器通过 Plan/Node Parcel round-trip、cycle reject 和
unknown-node-type reject，随后卸载临时 test APK：
`plan_parcel_physical_android13_arm64_verified=true`。该应用层 wire 证据不访问车辆/NPU，且不提升
`target_hardware_validated=false`。

P1-W03 当前交付为 SDK AAR 中的 5 个 Event parcelable、独立 Event/callback V1、23 类 allowlist、
Java ordering/parent/redaction/cursor/replay validator、JVM/Android Parcel tests、`events-v1.sha256` 和
独立 checker。`event_contract_v1_defined=true`，但 `event_runtime_service_published=false`、
`event_callback_service_published=false`：当前 bundle 不包含 Event Service、Room v4 或 durable broker。

P1-W04 当前交付为 SDK AAR 中的 `EffectIntent`、`EffectObservation`、`ApprovalPrompt`、`UndoHandle`，
以及完整 Effect 状态转换、typed scalar、simulation/source、approval stale/expiry 和 undo
expiry/context validator、JVM/Android Parcel tests、`effect-v1.sha256` 和独立 checker。合同只传递有界
metadata、canonical ID 和 digest；不传递 raw 车辆/模型 payload、身份或 Safety authority。

Android 13/API 33 ARM64 物理控制器通过四个 DTO Parcel round-trip、合法状态链、illegal terminal、
stale approval 和 expired undo reject，随后卸载临时 test APK。状态：
`effect_contract_v1_defined=true`、`effect_parcel_physical_android13_arm64_verified=true`、
`effect_runtime_service_published=false`、`approval_response_service_published=false`、
`undo_service_published=false`、`hardware_accessed=false`。该证据不发布审批 grant、undo executor、
车辆 adapter 或 durable Effect Runtime，也不提升 `target_hardware_validated=false`。

Android debug/test 中的 Digital Twin 或 deterministic provider 必须明确 `TEST_ONLY` 或
`SIMULATED`，且 production registry 不得包含它们。

## 2026-07-12 当前实施阶段

R0-R7 和 B0-B5 已形成 Android 软件基线。该历史阶段的关键退出标志仍保留：

- `r1_api33_exit_criteria_met=false` 是 R1B 设备验收前状态；
- `r1_api33_exit_criteria_met=true` 是 R1C API 33 退出状态；
- `typed_binder_connected=true`、`r2_binder_exit_criteria_met=true`；
- `trusted_caller_identity_resolved=true`；
- `unknown_client_default_deny_verified=true`；
- `hybrid_software_handoff_ready=true`；
- `physical_controller_application_evidence_available=true`。

这些标志只描述应用层软件证据，不关闭 production、NPU、VHAL 或整车硬件 gate。

## 交付物

| 文件/产物 | 来源 | 验证 |
| --- | --- | --- |
| `central-brain-sdk-debug.aar` | `central-brain-sdk` | AIDL hash、API surface、consumer rules |
| `native-runtime-debug.aar` | `native-runtime` | ABI allowlist、ELF、C symbol/JNI |
| `runtime-service-debug.apk` | `runtime-service` | package/signer/manifest/Service/readiness |
| `demo-hmi-debug.apk` | `demo-hmi` | SDK/Binder/Governance/UI |
| `client2-central-brain.debug.apk` | isolated Client2 patch | signer、classes2.dex、panel、Binder/recovery |
| `DELIVERY-MANIFEST.json` | package builder | source commit、hash、size、signer、ABI |
| `SHA256SUMS` | package builder | exact path-safe bundle coverage |

Android AIDL 规范见 `CENTRAL_BRAIN_ANDROID_AIDL_CONTRACT.md`；NPU/硬件合同见
`CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md` 和 `CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md`。

## 安装与验收要求

1. 默认执行 maintenance dry-run，不改目标设备。
2. 实际安装前检查 API 33、ABI、包名、APK signer 和已安装 signer。
3. Client2 异签默认返回 `SIGNER_MIGRATION_REQUIRED`；只有明确授权才允许受控卸载。
4. 验收覆盖 Binder connect/death/reconnect、callback uniqueness、Room recovery、UI 和 no-hardware。
5. GitHub 只接收版本、source commit、archive SHA-256、非秘密 alias、脱敏结果和内部证据引用。
6. 原始 serial/fingerprint、签名材料、target-input、raw log、用户/模型/Memory/token/车辆 payload 禁止上传。

## 模型与实体硬件保留项

- `ModelProvider`、`InferenceResourceScheduler` 和 `ModelRuntimeReadinessSnapshot`；
- `vendor.npu.empty` 失败关闭 profile；
- Native C ABI/JNI 和未来 Vendor provider slot；
- NPU lifecycle、buffer、fault、permission、evidence 和 Driver/HAL gap 文档；
- Effect adapter/material/activation/reconcile 合同；
- B3/R7C/GitHub remote acceptance JSON。

保留这些接口不等于真实硬件可用。实体 NPU/VHAL/车辆 service 未接入时必须返回 unavailable。

## Android R3C1 Action Governance Core 交付补充

R3C1 在 `runtime-service` 内交付纯 Java `ActionGovernancePolicy`、`SafetyVehicleStateProvider`、`RuntimeOwnedSafetyVehicleStateProvider` 和 `InMemoryApprovalRegistry`，以及 deterministic JVM tests 和 `tools/check_central_brain_android_action_governance.sh`。本增量只属于 Android 软件基线。

交付检查必须证明：五类 exact Action ID 风险映射；unknown/default deny；caller 不提供 Safety/Vehicle State 或 risk class；high-risk moving deny 与 parked approval-required；owner 隔离；pending 不压力淘汰；expiry/cancel；`dispatchAllowed=false`、`supportsApprovalGrant=false`、`isDurable=false`、`hardwareBacked=false`、`productionTrusted=false`。R3C2 前不存在 Android Governance Binder 交付声明，R4 前不存在 durable approval 或 checkpoint/outbox 声明。

该增量不访问 VHAL、Safety Runtime、Vehicle bus、PCIe NPU、device node、HAL/vendor SDK 或 shared memory，不修改厂商 Android 系统，不开发虚拟化。Req IDs：`XSC-001`、`XSC-004`、`XSC-005`、`XSC-006`、`FW-U-004`、`FW-U-007`、`FW-S-005`、`NV-F-001`、`NV-G-005`、`NV-G-006`、`NV-G-007`、`NV-P-002`、`DEL-001`、`DEL-003`、`DEL-004`、`DEL-005`。

## Android R3C2 Typed Governance Binder 交付补充

R3C2 交付 `ICentralBrainGovernance` 与 `ActionRequest`、`ActionDecision`、`ApprovalHandle`、`ApprovalStatus`，`CentralBrainGovernanceClient`，独立 `CentralBrainGovernanceService`，`BIND_GOVERNANCE` signature permission 和 `governance-v1.sha256`。原 task/diagnostic V1 checksum 不变；标准交付仍只有 SDK AAR、Runtime APK 和 Demo APK，`policy-probe` 仅为 test-only artifact。

API 33 标准安装门禁必须输出 `governance_typed_binder_connected=true`、`action_risk_classes_verified=true`、`runtime_owned_state_provider_verified=true`、`high_risk_pending_approval_verified=true`、`approval_cancel_verified=true`、`approval_grant_supported=false`、`approval_durable=false`、`service_dispatch_triggered=false`。同 signer Probe 门禁必须输出 `outer_governance_signature_permission_passed=true` 和 `governance_capability_default_deny_verified=true`。

R3C2 只证明 Android 13 typed Governance 和 default-deny capability 已集成，成熟度为 `android_integrated`。它不交付真实 VHAL/Safety Runtime source、approval grant authority、durable idempotency/recovery、真实车控 dispatch、目标硬件或 production qualification；这些分别留给目标平台和 R4。无 Linux 前端、Driver/HAL、厂商系统源码或虚拟化变更。

## Android R4A Room Durable Schema 交付补充

R4A 交付 AndroidX Room `2.8.4` 依赖、`CentralBrainDatabase` v2、8 个 Entity、`RuntimeStateDao`、exported schema JSON、explicit `MIGRATION_1_2`、debug-only migration probe 和 `tools/check_central_brain_android_durable_schema.sh`。标准 build 仍只产出 SDK AAR、Runtime APK、Demo APK；schema JSON 是源码审查/迁移门禁 artifact。

API 33 安装门禁新增 `room_schema_version=2`、`room_table_count=8`、`room_wal_enabled=true`、`room_migration_1_2_verified=true`、`legacy_task_preserved=true`、`legacy_approval_preserved=true`、`durable_dispatch_enabled=false`。Release manifest 必须排除 Migration Probe，且代码不得使用 destructive migration fallback。

该阶段只交付 schema/migration，不声明 Runtime/Governance 已 durable。production Services 尚未打开数据库，不恢复 task/approval，不处理 outbox，不 dispatch action。无 Linux 前端、真实硬件、Driver/HAL、厂商系统源码或虚拟化变更；raw utterance/model output/vehicle frame 不进入 schema。

## Android R4B1 Durable Task Admission 交付补充

R4B1 交付 `DurablePrincipalFingerprint`、`DurableTaskRepository`、DAO owner/idempotency lookup、JVM fingerprint tests、debug-only `DurableRepositoryProbeActivity` 和 `tools/check_central_brain_android_durable_repository.sh`。不修改 frozen AIDL，不新增 APK artifact，也不把 probe 放入 release。

API 33 标准安装门禁新增 `task_admission_transaction_verified=true`、`task_idempotent_replay_verified=true`、`task_idempotency_conflict_verified=true`、`task_owner_isolation_verified=true` 和 `runtime_repository_wired=false`。隔离数据库关闭/重开后必须保持 exactly two owner-scoped tasks and exactly two acceptance audits。

R4B1 不是 production Service integration：Runtime/Governance 不打开 repository，现有 Binder 行为不宣称 durable，approval 仍为 process-local。无 effect/outbox dispatch、Linux 前端、真实硬件、Driver/HAL、厂商系统源码或虚拟化变更。

## Android R4B2 Durable Runtime Wiring 交付补充

R4B2 交付 production Runtime Service repository wiring、`DurableDigest`、transactional transition/checkpoint/settlement、bounded same-process replay observers、durable owner-status fallback、debug-only `RuntimeDurabilityProbeActivity`、Demo replay UI 和 `tools/check_central_brain_android_durable_runtime_wiring.sh`。Frozen AIDL/checksum 不变。

API 33 标准门禁输出 `runtime_repository_wired=true`、`durable_replay_callback_verified=true`、`durable_concurrent_replay_verified=true`、`durable_completed_task_verified=true`、`durable_cancelled_task_verified=true`、`durable_checkpoint_chain_verified=true`、`durable_terminal_settlement_verified=true`、`task_recovery_enabled=false`、`durable_dispatch_enabled=false`；service-death instrumentation 还必须输出 `durable_recovery_pending_verified=true`。R2C death/race 与 R3 unknown-client default-deny 必须回归通过。

本增量不交付 restart execution recovery、approval persistence、pending effect/outbox processing 或真实 action dispatch。存在但未恢复的 task 返回既有 handle 和 retryable recovery-pending failure，不重复执行。无 Linux 前端、真实硬件、Driver/HAL、厂商系统源码或虚拟化变更。

## Android R4B3 Durable Approval 交付补充

R4B3 交付 `DurableApprovalRepository`、Governance Service Room wiring、approval DAO query/update/count、debug-only reopen probe、Demo duplicate approval evidence、production DB read probe 和 `tools/check_central_brain_android_durable_approval.sh`。Frozen Governance AIDL/checksum 不变，R3 `InMemoryApprovalRegistry` 仅保留历史 core test，不再被 Service 引用。

API 33 门禁新增 `approval_reopen_replay_verified=true`、`approval_idempotency_conflict_verified=true`、`approval_owner_isolation_verified=true`、`approval_expiry_verified=true`、`production_durable_approval_verified=true` 和 `approval_durable=true`；同时必须保持 `approval_grant_supported=false`、`service_dispatch_triggered=false`。

该交付只使 request/status/cancel/expiry durable，不提供 approve/grant authority、真实 Safety/VHAL source、effect/outbox dispatch、task restart execution recovery 或硬件资格。Expiry 是 access-triggered wall-clock sweep；terminal retention、trusted clock、encryption/key lifecycle 继续由 ISSUE-022 跟踪。

## Android R4C1 Fail-Closed Restart Reconciliation 交付补充

R4C1 交付 DAO restart candidate query、transactional FAILED/checkpoint/audit reconciliation、Runtime 后台启动屏障、无 live record 的 owner-scoped replay/settlement、SDK per-task serial callback delivery、debug-only isolated restart probe 和 `tools/check_central_brain_android_restart_reconciliation.sh`。Frozen AIDL/checksum 与标准三项 artifact 不变。

API 33 标准安装门禁新增 `restart_reconciliation_enabled=true`、`restart_reconciliation_idempotent=true`、`incomplete_completion_reconciled_failed=true` 和 `task_execution_resume_enabled=false`；service-death instrumentation 必须输出 `restart_reconciliation_verified=true`，并继续通过 terminal uniqueness、cancel-completion race 和 no-hardware 门禁。

该阶段是 fail-closed reconciliation，不是执行恢复：active 与未结算 COMPLETED task 均转为 FAILED；exact replay 返回相同 handle 和 retryable failure。它不保存/重放 raw utterance/result，不创建 pending effect/outbox，不 dispatch Action/SOA/Skill，不访问真实硬件，不新增 Driver/HAL、厂商系统源码、Linux 前端或虚拟化开发。

## Android R4C2A Effect Prepare And Claim 交付补充

R4C2A 交付 `DurableEffectRepository`、effect/outbox DAO query/update、owner-scoped idempotency token、PREPARED/PENDING→IN_FLIGHT claim、IN_FLIGHT reopen requeue、debug-only isolated probe 和 `tools/check_central_brain_android_effect_outbox.sh`。不修改 Room schema version、frozen AIDL 或标准三项 artifact。

API 33 标准安装门禁新增 effect prepare/reopen replay/conflict/owner scope、outbox claim/reopen requeue/fairness/idempotent reconciliation、second claim attempt=2 和 audit count 证据，同时固定 `effect_repository_wired=false`、`outbox_dispatch_enabled=false`、`service_dispatch_triggered=false`。

该交付不包含 retry/backoff、success/dead-letter/cancel 终态，不接 production Service，不调用 UIB/SOA/Skill，不声明 adapter exactly-once，不保存 raw operation payload，不访问硬件，不新增 Driver/HAL、厂商系统源码、Linux 前端或虚拟化开发。

## Android R4C2B Effect Retry And Terminal States 交付补充

R4C2B 在 `DurableEffectRepository` 内交付 bounded retry/backoff、APPLIED/DELIVERED、FAILED/DEAD_LETTER、CANCELLED/CANCELLED、attempt-aware exact replay，以及最终 IN_FLIGHT claim 崩溃后的 `EFFECT_CLAIM_EXHAUSTED` 失败关闭。它新增 `tools/check_central_brain_android_effect_outbox_terminal.sh`，不修改 Room schema version、frozen AIDL 或标准三项 artifact。

API 33 标准安装门禁必须验证 retry delay/digest 冲突、not-before gating、final attempt 不可重试、stale attempt 拒绝、success/dead-letter/cancel exact replay、终态计数、最终 attempt 崩溃转死信和重复对账幂等；同时保持 `effect_repository_wired=false`、`outbox_dispatch_enabled=false`、`service_dispatch_triggered=false` 及全部 no-hardware 标志。

该交付只关闭 repository 本地状态机，不交付 dispatcher 或 adapter。最终 attempt 崩溃转死信只表示本地结果未知并失败关闭，不证明目标端副作用未发生。R4C3 必须先交付 adapter idempotency token/status reconciliation 接口和 crash-point fault matrix，production Service 才可评审 wiring。Trusted clock、retention、encryption/key lifecycle 继续开放；不新增 Driver/HAL、厂商系统源码、Linux 前端或虚拟化开发。

## Android R4C3A Effect Adapter Contract 交付补充

R4C3A 交付 `EffectAdapter`、`EffectAdapterContract`、`EffectStatusReconciler`、纯 Java contract 单测、debug-only deterministic adapter/probe 和 `tools/check_central_brain_android_effect_adapter_contract.sh`。它不修改 Room schema、frozen AIDL 或标准 SDK AAR/Runtime APK/Demo APK 三项交付形状。

API 33 标准安装门禁必须输出 safe/unsafe contract、destination mismatch、duplicate apply、apply 前/后崩溃、status unavailable defer、UNKNOWN/final NOT_APPLIED dead-letter、terminal count 和 fault matrix 证据。Release APK 必须排除 probe，production Runtime/Governance 不得引用 adapter/reconciler。

该交付只证明 contract 和 debug fault algorithm，deterministic adapter 不代表 UIB/SOA/Skill/vendor/hardware。Canonical payload/envelope 仅为 probe 瞬时材料，固定 `transient_effect_material_durable=false`；因此 production activation 仍被 R4C3B durable material source/gate 阻塞，并保持 `effect_adapter_production_wired=false`、`real_adapter_dispatch_enabled=false`、`service_dispatch_triggered=false`。无 Driver/HAL、厂商系统源码、Linux 前端或虚拟化开发。

## Android R4C3B Effect Material Activation Gate 交付补充

R4C3B 交付 `EffectMaterialSource`、`EmptyEffectMaterialSource`、`EffectDeliveryActivationGate`、JVM gate tests、debug-only synthetic material source/probe 和 `tools/check_central_brain_android_effect_activation_gate.sh`。Room schema/frozen AIDL/标准三项 artifact 不变，main source 不新增非空 material provider 或 adapter implementation。

API 33 门禁必须验证 current empty blocker、TEST_ONLY 拒绝、synthetic positive contract、Room reopen resolution、defensive copy、digest mismatch/missing material/empty resolve 拒绝和 no-side-effect。Release APK 必须排除 material probe；production Runtime/Governance 在 R4C3B 仍不得引用 gate/source。

当前交付固定 `production_effect_delivery_activation_allowed=false`、`production_effect_material_source=empty`、`production_effect_material_durable=false`、`raw_effect_material_persisted=false`。Synthetic source 仅为进程内测试，不提供 key/retention/delete 或 process-death 证据。R4C3C 只可把该阻塞结果接到 production diagnostics，不得启用 dispatch。无 Driver/HAL、厂商系统源码、Linux 前端或虚拟化开发。

## Android R4C3C Production Fail-Closed Activation Visibility 交付补充

R4C3C 交付 immutable `EffectDeliveryActivationSnapshot`、Runtime startup/dumpsys status 和现有 Diagnostic Binder 中的 bounded `effect-delivery-activation` record，以及 `tools/check_central_brain_android_effect_gate_wiring.sh`。Frozen AIDL/checksum、Room schema 和三项 artifact 形状不变。

API 33 门禁必须验证 diagnostic record、Runtime log 与 dumpsys 同时报告 gate wired、activation false、adapter/material/apply/status false、empty source 和 ordered blocker；R2C lifecycle/race 与 R3 default-deny 必须回归。Release APK 必须只有 3 个 signature-protected Service 和 0 Activity/probe。

R4 以 `R4_DURABLE_WORKFLOW` / `android_integrated` 关闭基础阶段，但 effect delivery 仍被明确阻塞。Production Service 不实例化 adapter、不 resolve material、不 query/apply、不引用 effect repository dispatch。Target key/material/adapter/trusted-clock/hardware evidence 不属于本完成声明；无 Driver/HAL、厂商系统源码、Linux 前端或虚拟化开发。

## Android R5A1 Model Provider Contract

R5A1 继续保持 Android 13 应用层源码交付，不修改 vendor/AOSP/BSP 或已编译系统。新增 main-source `ModelProvider`/`ModelProviderProfiles`、JVM unit test、DUMP-protected debug probe 和 `tools/check_central_brain_android_model_provider_contract.sh`；frozen AIDL、Room schema、SDK AAR + Runtime APK + Demo APK 三项标准 artifact 不变。

Android API 33 验收必须输出 `model_provider_contract_verified=true`、`deterministic_stub_profile_verified=true`、`vendor_npu_empty_profile_verified=true`、`unsafe_provider_descriptor_rejected=true`，并同时输出 provider/runtime/router/local-development/vendor/hardware 全部未激活标志。Release APK 不得包含 ModelProvider probe。

本阶段聚焦 Android 硬件环境，因此不新增 Linux 前端 artifact。跨 SoC Provider contract 的 Linux 交付映射保留在既有 NPU 接口文档中，待用户恢复 Linux scope 时实现；这项范围收缩必须与当前 Android-only phase 一起解释，不得宣称双平台 R5 已完成。

## Android R5A2 Inference Resource Scheduler

R5A2 新增 main-source pure-Java `InferenceResourceScheduler`、JVM unit test、DUMP-protected debug probe 和 `tools/check_central_brain_android_inference_scheduler.sh`。不修改 AIDL、SDK public API、Room schema 或三项标准 artifact 形状；release Runtime 不得包含 Scheduler probe。

Android API 33 验收必须覆盖 trusted effective priority、priority/deadline/FIFO、global/per-owner queue/running quota、provider slot、queued expiry、running deadline cancellation directive、queued/running cancel、completion-after-cancel deterministic resolution 和 current-profile non-routing。验收同时固定 `provider_cancel_invoked=false`、`scheduler_production_wired=false`、`model_provider_runtime_wired=false`、`model_router_dispatch_enabled=false` 和全部 no-hardware 标志。

该阶段不交付真实推理 UI/SDK 调用、provider execution、local-development provider、Vendor NPU adapter 或 Linux 前端。Scheduler 的 contract-test route 不是产品 route；R5B 只有在 deterministic provider fault/cancel/stream contract 通过后才能启用 test-only routing。

## Android R5B1 Deterministic Stub Provider

R5B1 新增 main-source TEST_ONLY provider、JVM unit test、DUMP-protected debug probe 和 `tools/check_central_brain_android_deterministic_model_provider.sh`。Standard artifact 形状、AIDL、SDK public API 和 Room schema 不变；release Runtime 不得包含 provider probe，production Services 不得实例化 provider。

API 33 验收必须输出 lifecycle/stream/deterministic-output/cancel-ack/metrics/retryable-fault/fault-isolation/profile-boundary evidence，同时区分 `implementation_available=true` 与 `implementation_configured=false`/`routing_enabled=false`。`production_inference_enabled=false`、local-development/Vendor unavailable 和全部 no-hardware 标志必须保持。

该实现用于后续 R5B2 Router 的 deterministic test path，不是量产模型 runtime，不处理真实模型权重/输入，不证明性能、GPU/NPU utilization 或硬件 fault。当前 Android-only phase 不新增 Linux 前端。

## Android R5B2 Test-Only Model Router

R5B2 新增 main-source test-only Router、JVM unit test、DUMP-protected debug probe 和 `tools/check_central_brain_android_test_model_router.sh`。它只在 unit/debug composition 中把 R5A2 Scheduler 与 R5B1 provider 连接；AIDL、SDK public API、Room schema 和 SDK AAR + Runtime APK + Demo APK 形状不变。Release Runtime 不得包含 Router probe，production Services 不得引用 Router。

API 33 验收必须输出 end-to-end dispatch、Scheduler/provider lease binding、stream、cancel、deadline、identity、replay、single-terminal 和 `NO_FALLBACK` evidence；同时保持 `model_router_test_only=true`、`production_model_router_wired=false`、`production_model_router_dispatch_enabled=false`、profile configured/routing false、local-development/Vendor unavailable 和全部 no-hardware 标志。

该交付不包含 production Binder/SDK inference API、真实模型输入、Room route recovery、production fallback/熔断、local-development provider、Vendor NPU adapter 或 Linux 前端。R5B2 的 executable test route 不能作为量产 routing 或 NPU 验收声明。

## Android R5C1 Production-Safe Model Runtime Readiness

R5C1 新增 immutable main-source readiness snapshot 和 JVM test，并复用现有 Runtime Service、Diagnostic Service、debug Diagnostic probe 与安装验收脚本。AIDL/checksum、SDK public API、Room schema 和三项标准 artifact 形状不变；不新增 Activity，release 仍不得包含任何 probe。

API 33 验收必须同时验证 Diagnostic Binder、Runtime startup log 和 protected dumpsys 的 profile ID、assurance、lifecycle、health、detail code、production wiring flags 与 ordered blocker parity。输出必须包含 `model_runtime_readiness_*_verified=true`，同时保持 production inference/router/scheduler/local-development/Vendor/hardware false。

该交付是座舱集成工程师的阻塞原因可见性，不是 production model service、实时健康监控、硬件探测、性能数据或 NPU 验收。当前 Android-only phase 不新增 Linux 前端；R5D 继续验证 Android 13 目标部署与 empty-interface 边界。

## Android R5D1 Application-Layer Target Deployment Acceptance

R5D1 交付 `tools/test_central_brain_android_target_deployment.sh`、静态守卫和 `CENTRAL_BRAIN_ANDROID_TARGET_DEPLOYMENT_ACCEPTANCE.md`。默认命令会构建并运行完整 API 33 安装/Binder/governance/readiness gate，再输出设备、artifact、签名、package 和 Model Runtime 证据。

API 33 emulator 已通过：SDK/Runtime/Demo hash 可归档，Runtime/Demo signer 一致，两个 APK 均为普通 UID 且位于 `/data/app`，Runtime 有三项 signature-protected Service，不请求 INTERNET、无 native `.so`，production inference/Vendor NPU/hardware 均 false。证据明确为 emulator scope，真实目标应用层验收仍需在交付设备重跑默认命令。

R5 contract/test software track 因此完成，但不提升为 `hardware_validated` 或 `production_qualified`。物理目标、量产 signer/MDM/SELinux 策略、真实 NPU/Driver/HAL、性能/热/故障与安全认证仍是后续集成工作。

## Android R6A1 Bounded Event Runtime

R6A1 交付 main-source `BoundedEventRuntime`、JVM tests、DUMP-protected debug probe、安装门禁与 `tools/check_central_brain_android_event_runtime.sh`。AIDL、Room schema、SDK public API 和标准 SDK AAR + Runtime APK + Demo APK 形状不变；release 不得包含 Event probe。

API 33 验收必须输出 trusted-topic、monotonic-sequence、cursor-replay、overflow-before-delivery、owner isolation、subscription idempotency、cancel idempotency 和 observer retry evidence，同时固定 process-only/cursor-persistence/broker/Binder/DDS/network/vehicle-bus/hardware 边界。

该交付不是 production Event broker，不持久化订阅/cursor，不发送真实业务 payload，不接 Binder callback、SSE/WebSocket、DDS、车辆总线或 Linux 前端。现有 Room `event_cursor` table 只保留为 R6A2 schema foundation，本阶段不得写入。

## Android R6A2A Durable Event Schema

R6A2A 交付 `CentralBrainDatabase` v3、保留的 v2 schema export、v3 schema export、`MIGRATION_2_3`、扩展后的 `EventCursorEntity`/DAO、迁移 probe 和静态门禁。标准 artifact 仍是 SDK AAR + Runtime APK + Demo APK，表数量仍为 8，AIDL 与 public SDK 不变。

API 33 必须新增 `room_schema_version=3`、`room_migration_2_3_verified=true`、`legacy_event_cursor_preserved=true`、`event_cursor_schema_v3_verified=true`、`event_cursor_schema_ready=true` 和 `event_cursor_repository_wired=false`，同时保留 v1/v2 task/approval migration、WAL 与 release probe isolation 证据。

该小步不交付 Event repository、reopen recovery、production callback 或 broker，也不把 R6A1 process events 写入 Room。Raw payload、网络/DDS/车辆总线、NPU、Driver/HAL、Linux 前端和虚拟化均不在交付范围；R6A2B 才实现 repository-only 状态机。

## Android R6A2B Durable Event Repository

R6A2B 交付 `DurableEventCursorRepository`、DAO capacity/retention query、DUMP-protected isolated reopen probe、安装门禁和静态检查。AIDL、SDK API、Room v3 schema 和 SDK AAR + Runtime APK + Demo APK artifact 形状不变；release 不得包含 repository probe。

API 33 必须输出 registration idempotency/admission bounds、owner isolation、monotonic ACK、source regression blocked、overflow/resync、reopen recovery、cancel idempotency、record bounds、audit exactly-once 和 probe persistence evidence。同时必须输出 `event_cursor_repository_implementation_available=true`、`event_cursor_repository_production_wired=false`、`event_cursor_persistence_wired=false`、`durable_event_source_available=false`。

该交付只证明 repository transaction/reopen contract。它不把 R6A1 events 写入 Room，不创建 durable publisher log，不接 production Service/Binder callback/broker，也不交付网络/DDS/车辆总线、NPU、Driver/HAL、Linux 前端或虚拟化。

## Android R6A3 Event Runtime Readiness

R6A3 交付 immutable `EventRuntimeReadinessSnapshot`、JVM tests、Runtime log/dumpsys integration、现有 Diagnostic Binder record/probe 扩展、安装门禁和静态检查。AIDL/checksum、Room schema、public SDK 与三项标准 artifact 不变。

API 33 必须验证 Diagnostic Binder、Runtime log、真实 dumpsys 三路 parity，并输出 readiness snapshot wired、activation false、implementation availability、trusted topic count、durable source false、production wiring false 和完整 ordered blocker。Release 仍须为 3 个 signature-protected Service、0 Activity/probe。

该交付不打开 Event repository，不激活 cursor persistence、callback Binder、broker、middleware 或 transport，不访问 raw payload、DDS、网络、车辆总线、NPU/Driver/HAL，也不修改厂商系统软件。

## Android R6B1 Bounded Memory Lifecycle

R6B1 交付 main-source `BoundedMemoryLifecycle`、JVM tests、DUMP-protected debug probe、安装门禁与 `tools/check_central_brain_android_memory_lifecycle.sh`。AIDL/checksum、Room v3 schema、public SDK 与 SDK AAR + Runtime APK + Demo APK artifact 形状不变；release 不得包含 Memory probe。

API 33 必须输出 scope policy、PROFILE consent、write idempotency、owner isolation、query redaction、TTL expiry、delete idempotency、export authorization 和 record bounds evidence，并固定 `memory_process_only=true`、persistence/production/durable-profile/consent-revocation/encryption false、raw content false 与 hardware false。

该交付不是可量产的个人记忆库。它不存 raw utterance/model output，不跨进程恢复，不新增 Binder API/Room table/production Service，也不声称 consent revocation、trusted wall clock、encrypted key lifecycle 或 durable PROFILE storage 已完成；Driver/HAL、Linux 前端和虚拟化均不在本阶段范围。

## Android R6B2 Memory Runtime Readiness

R6B2 交付 immutable `MemoryRuntimeReadinessSnapshot`、JVM tests、Runtime log/dumpsys integration、现有 Diagnostic Binder record/probe 扩展、安装门禁和静态检查。AIDL/checksum、Room v3 schema、public SDK 与 SDK AAR + Runtime APK + Demo APK artifact 形状不变。

API 33 必须验证 Diagnostic Binder、Runtime log、真实 dumpsys 三路 parity，并输出 snapshot wired、activation false、R6B1 implementation available、scope count 3、schema/repository/storage/key/consent/revocation/clock/production/middleware false 和完整 ordered blocker。Release 仍须为 3 个 signature-protected Service、0 Activity/probe。

该交付不构造 Memory lifecycle，不新增持久化、Keystore key、consent authority、Binder method 或 middleware dispatch。它只把后续 durable encrypted Memory 的必要前置条件显式化；raw content、NPU、Driver/HAL、Linux 前端和虚拟化均不在交付范围。

## Android R6C1 Signed Built-In Skill Runtime

R6C1 交付 main-source `BoundedBuiltInSkillRuntime`、JVM tests、DUMP-protected debug probe、安装门禁与 `tools/check_central_brain_android_built_in_skill_runtime.sh`。AIDL/checksum、Room v3 schema、public SDK 与 SDK AAR + Runtime APK + Demo APK artifact 形状不变；release 不得包含 Skill probe。

API 33 必须输出 catalog、signer allowlist、manifest schema、invocation idempotency、capability policy、safety state、owner isolation、cancel idempotency 和 record bounds evidence，并固定 process-only、compile-time signer evidence、dynamic-loading false、artifact crypto verification false、production/network/raw-input/hardware false。

该交付不执行 Skill route，不扫描或加载 APK/JAR/dex/native plugin，不验证真实 artifact bytes，不新增 Binder/Room/production Service，也不访问网络、车辆总线或硬件。真实签名发布流水线、撤销/version rollback policy、sandbox 和 production dispatcher 仍需后续设计；Linux 前端和虚拟化不在当前阶段。

## Android R6C2 Fixed Governance Middleware Chain

R6C2 交付 main-source `FixedGovernanceMiddlewareChain`、JVM tests、DUMP-protected debug probe、安装门禁与 `tools/check_central_brain_android_governance_middleware.sh`。AIDL/checksum、Room v3 schema、public SDK 与 SDK AAR + Runtime APK + Demo APK artifact 形状不变；release 不得包含 middleware probe。

API 33 必须输出 fixed order、allow path、first rejection、audit finalizer、privacy/policy/QoS/output guard 和 audit bounds evidence，并固定 process-only、production wiring false、dispatch execution false、service dispatch false、raw input/output false、audit persistence false、network/hardware false。

该交付是治理执行顺序和 fail-closed 行为的 contract evidence，不是量产 middleware activation。它不新增 Binder/Room，不调用 SOA/UIB/Agent route，不持久化/导出 audit，也不访问网络、NPU、车辆总线或硬件。R6C3 将交付 production-safe readiness visibility；Linux 前端和虚拟化不在当前阶段。

## Android R6C3 Skill And Governance Readiness

R6C3 交付 immutable `SkillGovernanceReadinessSnapshot`、JVM tests、Runtime log/dumpsys integration、现有 Diagnostic Binder record/probe 扩展、安装门禁和静态检查。AIDL/checksum、Room v3 schema、public SDK 与 SDK AAR + Runtime APK + Demo APK artifact 形状不变。

API 33 必须验证 Diagnostic Binder sequence 8、Runtime log、真实 dumpsys 三路 parity，并输出 Skill count 3、middleware stage count 9/order fixed、R6C1/R6C2 implementation available 和完整十项 blocker。Release 仍须为 3 个 signature-protected Service、0 Activity/probe。

该交付不构造 Skill runtime/middleware，不执行 artifact crypto verification、动态加载、route dispatch、audit persistence 或 sandbox，也不新增 Binder/Room/network/hardware。R6 software foundation 可结束，但 production activation 和 ISSUE-025 仍开放；Linux 前端和虚拟化不在当前阶段。

## Android R7A1 Runtime Acceptance Snapshot

R7A1 交付 immutable `RuntimeAcceptanceSnapshot`、JVM tests、Runtime log/dumpsys integration、现有 Diagnostic Binder sequence 9/probe 扩展、安装门禁与静态检查。AIDL/checksum、Room v3 schema、public SDK 和三项 artifact shape 不变。

API 33 必须验证 aggregate diagnostic/log/dumpsys parity、core software ready、R7 integration false、production/hardware false 和完整九项 blocker，同时复跑所有 R2-R6 probes。Release 仍须为 3 个 signature-protected Service、0 Activity/probe。

该交付不代表 R7 完成或目标硬件通过。R7B 处理 Client2 Binder/SDK，R7C 处理 API 33 E2E；system integration owner、production adapters/runtimes 和 target hardware 只能由后续目标平台证据关闭。

## Android R7B Client2 SDK/Binder Migration

R7B 交付隔离 Client2 APK patch 工程、两文件 Java bridge、SDK/AIDL `classes2.dex` 构建、Runtime 最小 capability principal、可重复 API 33 验收脚本和更新后的 aggregate acceptance snapshot。原 APK 与 decoded baseline 不进入提交，标准 SDK AAR + Runtime APK + Demo APK 的三项 Gradle artifact shape 不变。

API 33 必须验证 Client2/Runtime signer parity、signature permission granted、secondary SDK dex、12 场景 allowlist 中真实按钮点击、Runtime package identity、typed task completion 和 UI reply，并固定 `http_transport_used=false`、`service_dispatch_triggered=false`、`hardware_accessed=false`。Client2 APK 不得声明 INTERNET/cleartext，也不得保留旧 HTTP RequestTask。

该交付只关闭 Client2 Binder migration blocker。闭源 APK 维护、原始 signer/RenderService trust、目标系统 owner、R7 完整 E2E、production Effect/Model/Event/Memory/Skill-Governance 和目标硬件仍未通过；无 Driver/HAL、厂商 SDK/system binary、Linux 前端或虚拟化开发。

## Android R7C Application Integration Acceptance

R7C 交付 API 33 application acceptance JSON contract、详细说明、debug-only/DUMP-protected Runtime process-death receiver、Client2 恢复矩阵脚本、静态门禁和更新后的 aggregate snapshot。标准 SDK AAR + Runtime APK + Demo APK 形状、AIDL/checksum 与 Room v3 不变；release 不得包含 fault receiver 或任何 debug probe。

API 33 必须输出 Runtime absent/retry、single-flight、Runtime death unique failure/retry、restart reconciliation fail-closed、Client2 process restart/rebind、Binder lifecycle/cancel race 和最终 aggregate state evidence。Client2 happy path、identity/capability、UI reply 与 no-HTTP/no-hardware 仍须回归通过。

该交付允许 `r7_application_integration_complete=true` 和 `api33_end_to_end_acceptance_complete=true`，但证据范围仅为 emulator application integration。Target system owner、五类 production subsystem 和 target hardware 七项 blocker 保持；无 Driver/HAL、厂商系统软件、Linux 前端或虚拟化开发。

## Android R7D Software Handoff Package

R7D 交付 Android 13 application-layer bundle：`central-brain-sdk-debug.aar`、`runtime-service-debug.apk`、`demo-hmi-debug.apk`、`client2-central-brain.debug.apk`，以及 delivery profile、target-input template、迁移/验收文档、bundle verifier、dry-run/install 工具、`DELIVERY-MANIFEST.json`、`SHA256SUMS` 和 normalized tar archive。

Bundle 同时携带 `test_central_brain_android_target_deployment.sh` 与 `test_client2_central_brain_recovery.sh`。两者用于完整源代码检出环境，依赖 Gradle/Client2 构建产物和关联测试工具；交付包不宣称为自包含源码树。

Package builder 必须验证四项 artifact 的 hash/size、APK package/minSdk/targetSdk、Runtime/Demo/Client2 signer cohort、所有 artifact 无 native payload、Client2 有 `classes2.dex`，并记录 source Git commit。Verifier 拒绝非规范路径/符号链接并把 manifest 逐项绑定回 profile；installer 再读取 bundle APK 实际 package/signer。SHA/checksum 只证明一致性，archive SHA 必须经可信发布通道传递。当前包明确为 debug signing；量产重签名、原 Client2 升级签名迁移和 RenderService/vendor trust 由目标 owner 决策。

Installer 默认 dry-run；执行安装必须同时提供 `--execute --allow-debug-signing`，且先完成 API 33 与全部已安装 package signer preflight。安装顺序固定 Runtime -> Demo -> Client2，只允许 `adb install -r`；自动卸载、root/remount/fastboot、system/vendor partition write 均无实现路径。

交付状态为 `software_handoff_ready=true`、`production_ready=false`、`target_hardware_validated=false`。七项 target-owner/production/hardware blocker 保持为 inactive empty slots；真机 `/data/app` 验收、量产 subsystem 与硬件资格必须由后续目标证据分别关闭。

## Android B0 Black-Box Engineering Baseline

B0 新增 `CENTRAL_BRAIN_BLACKBOX_ANDROID13_ENGINEERING_PLAN.md` 和静态门禁，固定实际工程的 Java/AIDL/Room、C ABI/JNI、APK/AAR 与黑盒设备预检边界。B1-B4 在此基础上形成 hybrid artifact 和独立证据。

目标 artifact 形状为 SDK AAR、Native Runtime AAR、Runtime APK、Demo APK 和 Client2 APK。Native Runtime 首版只允许 `arm64-v8a`/`x86_64` 的 `libcentral_brain_native.so`，Vendor NPU/VHAL provider 保持 unavailable，`hardware_accessed=false`。

B0 只定义 contract，尚未交付 `.so`。完成状态必须由 B1 build、B2 API 33 integration、B3 black-box preflight 和 B4 installation/usage handoff 逐级证明。

## Android B1 Native Runtime Artifact

B1 已交付 `native-runtime-debug.aar`，其中只包含 `jni/arm64-v8a/libcentral_brain_native.so` 与 `jni/x86_64/libcentral_brain_native.so`。Host C ASan/UBSan、Java 6 项单测、Gradle build、ELF machine、公开符号、RELRO/NOW 和 vendor/hardware linkage 门禁均通过。

该 AAR 当前是独立 artifact，尚未进入 Runtime APK。B2 才接入进程生命周期和 Diagnostic，B3 才提供 API 33/目标黑盒部署证据。B1 不改变 R7D 历史无-native bundle，也不表示 NPU/VHAL/Driver/HAL 或目标硬件可用；`vendor_npu_provider_available=false`、`hardware_accessed=false`。

## Android B2 Native Runtime Integration Artifact

B2 Runtime APK `runtime-service-debug.apk` 已升级为 versionCode 2/versionName `0.2.0-b2`，并且只包含 `lib/arm64-v8a/libcentral_brain_native.so` 与 `lib/x86_64/libcentral_brain_native.so`。构建流程同时验证 Native AAR 与最终 APK 的 ABI、ELF machine、七个导出入口、RELRO/NOW、无 vendor/hardware linkage、无 INTERNET 和 APK signer。

`CentralBrainRuntimeApplication` 是进程 owner；Runtime log/dumpsys 与 Diagnostic Binder sequence 10 读取同一 snapshot。API 33 x86_64 设备测试已输出 `native_runtime_apk_verified=true`、`native_runtime_load_verified=true`、`native_runtime_diagnostic_verified=true` 与 `native_runtime_process_recovery_verified=true`。

B2 交付不提供 native inference 或硬件 adapter。`native_software_provider_available=false`、`native_vendor_npu_provider_available=false`、`native_runtime_dispatch_enabled=false`、`native_hardware_accessed=false`，目标黑盒预检和 hybrid package 分别由 B3/B4 完成。

## Android B3 Black-Box Preflight And Acceptance

B3 交付 `central_brain_android_b3_blackbox_acceptance.json`、只读 preflight、临时异签名负向测试、受控 acceptance、debug-only Java PackageManager probe 和详细设备执行文档。Runtime APK versionCode 3/versionName `0.3.0-b3`；安装前和安装后都比较已安装 Runtime/Demo 与交付 APK signer，任一 mismatch 在安装前失败关闭。

API 33 x86_64 模拟器已通过 ordinary `/data/app`、普通 UID、app-private data/native library path、64-bit process、PackageManager signer parity、Native Runtime process recovery 和 Binder/Room/HMI regression。其证据 scope 固定 `api33-emulator-blackbox-application`。2026-07-14 物理 API 33 ARM64 Automotive 控制器又通过同一 B3 应用层流程，并增加 Runtime force-stop 后 Demo Runtime/Governance 有界重连 UI 断言。

B3 物理结果只允许 `physical_controller_application_evidence_available=true`。`production_signing_approved=false`、`background_policy_approved=false`、`render_service_trust_approved=false`、`vendor_interface_contract_available=false`、`target_hardware_validated=false` 仍保持；B4 交付不能从应用层证据推断这些状态。

## Android B4 Hybrid C/Java Software Handoff

B4 新建 `android-hybrid` 交付轨道，打包 `native-runtime-debug.aar`、`central-brain-sdk-debug.aar`、`runtime-service-debug.apk`、`demo-hmi-debug.apk` 与 `client2-central-brain.debug.apk`。R7D 四项 no-native 交付继续作为历史证据，不被新 profile 改写。

Manifest 记录 5 项 artifact 的 path/size/SHA-256；Native AAR 和 Runtime APK 是唯一 2 项 native artifact，各自只允许 arm64-v8a/x86_64 `libcentral_brain_native.so` 并记录 ELF machine。Runtime/Demo/Client2 signer cohort、Client2 `classes2.dex`、support inventory、七项 blocker、install profiles 和 target-input template 均被 checksum 绑定。

Installer 默认 maintenance dry-run，Client2 需显式 `--include-client2`；执行 debug 测试安装需 `--execute --allow-debug-signing`。API 33 x86_64 已通过两个 dry-run 和两个实际安装 profile、Demo/Client2 启动、Client2 button/Binder UI 与 recovery regression。交付状态为 `hybrid_software_handoff_ready=true`、`production_ready=false`、`physical_controller_evidence_available=false`、`target_hardware_validated=false`。

## B5 GitHub Remote Hardware Test Loop

B5 面向维护者无法直接访问的内网 Android 13 控制器。Private GitHub Release 只分发 B4
归档和外层 SHA-256；Issue 必须绑定 release tag、manifest source commit、archive SHA-256
和 delivery ID。测试人员在目标侧执行 ADB，维护者按不可变版本修复并发布新的 RC。

Bundle 增加 `central_brain_github_remote_testing.json`、远程测试指南和
`run_central_brain_android_remote_acceptance.sh`。工具默认 bundle verify + installer dry-run +
只读证据采集；安装需要显式 `--execute-install`，debug signer 还需显式授权。输出拆分为
`github-safe/` 和 local-only `private/`，不自动上传任何内容。

GitHub Actions 只运行 B5 静态合同门禁，不构建完整 Client2 delivery。
publication-tree guard 会扫描目标 ref 全部可达历史，拒绝旧 APK/reverse/key 路径、20 MiB 以上
blob 和 credential marker；发布只能精确推送 `codex/github-publication:main`，禁止 mirror/internal
ref push。Private `LucasWEIchen/CougarOS` 的 remote、维护者写权限、labels、`main`、首个 RC2
Release 和每 15 分钟 Issue 维护自动化已经激活，`github_repository_configured=true`、
`github_issue_intake_active=true`。Codex GitHub connector 仍不可见该仓库，自动化使用已授权
`gh` CLI。tester access list 和服务端 branch protection 仍阻塞；当前 Private 套餐拒绝后者，
tracked pre-push hook + Actions 不等价于服务端保护。`physical_controller_evidence_available=false`、
`production_ready=false` 和 `target_hardware_validated=false` 继续保持。

## 2026-07-14 Physical Android 13 Application-Layer Acceptance

首轮直接 USB/ADB 目标测试使用 WSL 脚本调用 Windows `adb.exe`。B3 read-only preflight、B4
maintenance dry-run、Runtime -> Demo 安装、普通 `/data/app`、signature permission、Typed Binder、
Room/Governance/HMI、Native C ABI V1 和 process recovery 均在 Android 13/API 33/arm64-v8a
Automotive 控制器通过。

物理截图随后暴露 process recovery 后 Demo 状态陈旧；修复后 Demo 使用两个公开 SDK client 执行
有界显式重连，B3 必须读取恢复后 UI tree 才能输出 `post_recovery_hmi_rebind_verified=true`。

交付工具新增 Windows ADB CRLF compatibility gate。所有设备枚举和 `get-state` 解析兼容 LF/CRLF，
嵌套 signer guard 保留调用方 ADB。该修复只改变 host-side test orchestration，不改变 APK/AAR、
AIDL、C ABI、Room schema、Driver/HAL 或系统软件。

目标机原 `com.tuanjie.urasclient2` 与 debug Client2 signer 不一致；Client2 profile dry-run 按设计
在首次安装前失败关闭。用户随后明确授权清除普通 `/data/app` 原包及数据，完成同包 debug signer
迁移。新增的 `--replace-conflicting-client2` 只在显式调用且 Android 确认 signer mismatch 时执行；
迁移后 Client2 Binder/UI/RenderService 与 R7C Runtime/Client2 恢复矩阵通过。当前交付状态为：

2026-07-15 在同一目标上复验导航菜单：Activity 启动后面板隐藏，底部导航首次点击显示、再次
点击隐藏，面板外点击关闭，再次打开后 `care.cold` Binder/UI reply 通过；Client2 进程重启后菜单
可重新打开。面板视觉、typed Binder contract、签名 cohort 和硬件边界均未改变。

```text
physical_controller_application_evidence_available=true
runtime_demo_physical_acceptance_passed=true
client2_physical_acceptance_passed=true
client2_navigation_menu_acceptance_passed=true
production_ready=false
target_hardware_validated=false
hardware_accessed=false
driver_development_triggered=false
virtualization_development_triggered=false
```

脱敏过程和完整边界见 `CENTRAL_BRAIN_ANDROID13_PHYSICAL_TARGET_TEST_REPORT.md`。Req IDs：
`APP-004`、`XSC-001`、`XSC-004`、`XSC-005`、`XSC-006`、`NV-F-001`、`NV-F-011`、
`NV-F-012`、`NV-G-003`、`NV-G-005`、`NV-G-006`、`NV-G-007`、`NV-P-002`、
`KH-003`、`KH-006`、`DEL-001`、`DEL-003`、`DEL-004`、`DEL-005`。

覆盖 Req ID：`APP-004`、`XSC-001`、`XSC-004`、`XSC-005`、`XSC-006`、
`NV-F-001`、`NV-F-012`、`NV-G-006`、`NV-G-007`、`NV-P-002`、`DEL-001`、
`DEL-003`、`DEL-004`、`DEL-005`。

## Complete Software Design Engineering Handoff

`docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md` 是 `DEL-003` 的实现级工程师交付物，
覆盖当前仓库的 Android Java/AIDL/Room/C/JNI 实际工程、Client2/Demo 集成、硬件空接口和 Stage 2
工作包。文档给出模块设计意图、接口与字段、状态机、线程/时钟/数据所有权、持久化、错误语义、
配置、扩展步骤和验证矩阵；`tools/check_central_brain_aios_stage2_design.sh` 与
`tools/check_central_brain_python_prototype_retirement.sh` 从源码反查关键合同并进入持续门禁。

该文档交付只证明工程设计可追踪，不新增 APK/AAR artifact，不启用 production Scheduler/Model Router、
Effect adapter、Event broker、Memory store、Skill dispatcher、Vendor NPU、VHAL、Driver/HAL 或虚拟化。
`production_ready=false`、`target_hardware_validated=false`、`hardware_accessed=false` 保持不变。

## Stage 2 P1-W03 Event Contract V1 Delivery

P1-W03 在 Java SDK AAR 源码中交付 `RuntimeEvent`、`ActionEvent`、`ObservationEvent`、
`MessageEvent`、`EventPage`、独立 `ICentralBrainSessionEvents` V1、one-way callback、
`EventContract`、JVM/Android instrumentation 和 `tools/check_central_brain_android_event_contract.sh`。
合同由 `central-brain-sdk/aidl-api/events-v1.sha256` 冻结；既有 task、Governance、Session 和 Plan
checksums 不变，标准 artifact 形状仍为 SDK AAR、Native AAR、Runtime APK、Demo APK 与可选 Client2 APK。

Android 13/API 33 ARM64 物理控制器验证五类 DTO 的 Parcel round trip，以及 ordering、parent、redaction
和 cursor replay 的失败关闭行为。验证后卸载临时 instrumentation APK，证据状态固定为：

```text
event_contract_v1_defined=true
event_parcel_physical_android13_arm64_verified=true
event_runtime_service_published=false
event_callback_service_published=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该交付不发布 Event Binder Service，不连接现有 R6 process-only Event runtime，不打开 Room v3 event
cursor repository，不发送业务 payload，不创建主动触发，也不访问 Vehicle/VHAL/NPU/Driver/HAL。
P1-W05 已负责 Service/callback 生命周期，P1-W06 负责 Room v4 持久化。Req IDs：`S2-SES-001`、
`S2-EVT-001`、`FW-U-003`、`NV-F-009`、`NV-G-003`、`NV-G-007`、`DEL-001/003/004/005`。

## Stage 2 P1-W05 SDK Facade v2 Delivery

P1-W05 在 SDK AAR 中交付 `ScenarioClient`、`SessionClient`、`RuntimeEventListener` 和内部
`AndroidScenarioTransport`。HMI public API 不暴露 Binder primitive；transport 以一个显式 Runtime
component、两个 action 分别绑定 Session/Event V1。Runtime APK 复用现有
`CentralBrainRuntimeService` 发布两个 Binder，不增加第四个 app Service；七项 capability 继续由
Binder UID/package/current signer default-deny policy 执行。

Runtime registry 是进程级、有界、owner-scoped、request digest 幂等的 transient 实现：不保留原始
utterance，Service rebind 后可读取 snapshot/cursor replay 并重订阅，Runtime 进程死亡后不恢复。
生产 policy 不授权 test package；同签名 `com.centralbrain.sdk.test` 只存在于 debug resource overlay。

JVM 测试覆盖 fake transport、protocol mismatch、callback race、replay 去重、close/reconnect 幂等、
owner isolation、idempotency conflict、capacity 与 cursor。Android 13/API 33 ARM64 物理控制器以真实
signature permission/Binder 完成 open -> event replay -> explicit reconnect -> active resubscribe ->
cancel -> second event -> duplicate close。临时 instrumentation APK 验证后卸载。证据状态固定为：

```text
sdk_facade_v2_available=true
session_runtime_service_published=true
event_runtime_service_published=true
event_callback_service_published=true
active_session_reconnect_resubscribe_verified=true
callback_replay_deduplicated=true
close_reconnect_idempotency_verified=true
session_runtime_transient_registry=true
session_runtime_persistence_wired=false
session_runtime_process_death_rehydration=false
scenario_execution_enabled=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该交付不是 durable Session Runtime、Scenario compiler/graph executor、Effect service、approval response、
undo execution、Vehicle/VHAL/NPU/Driver-HAL 或 production Event broker。P1-W06 必须完成 Room v4 和
process-death rehydration；Event V1 terminal cursor 限制由 `ISSUE-034` 继续跟踪。Req IDs：
`S2-SES-001`、`S2-UX-001..003`、`S2-EVT-001`、`APP-004`、`XSC-001/006`、
`NV-G-003/004`、`DEL-001/003..005`。

## Stage 2 P1-W06 Room v4 Delivery

P1-W06 交付 `SessionEntity`、`PlanEntity`、`PlanNodeEntity`、`RuntimeEventEntity`、
`EffectObservationEntity`、`CompensationEntity`、Room schema `4.json`、`MIGRATION_3_4`、
`SessionRegistry` persistence boundary 和 `DurableSessionRegistry`。旧 `runtime_session` 行迁移到
`sessions`；既有 runtime task/checkpoint/pending effect/outbox/approval/audit/event cursor 数据保持。

生产 Session/Event Binder 已从 process-local Map 切到 owner-scoped Room repository。Session admission
与首事件、cancel 与 terminal event 分别原子提交；callback 仍为进程内 Binder registration，Runtime
进程重启后由 SDK snapshot/cursor replay 重建。Plan/Node/EffectObservation/Compensation 只交付 schema，
没有 Compiler/Graph/Effect authority 或执行器。

API 33 ARM64 交付证据必须包含：

```text
room_schema_version=4
room_migration_3_4_verified=true
room_table_count=13
legacy_runtime_session_preserved=true
room_v4_foreign_keys_verified=true
room_v4_query_index_verified=true
room_v4_crash_transaction_rollback_verified=true
session_runtime_persistence_wired=true
session_runtime_process_death_rehydration=true
durable_session_identity_preserved=true
durable_event_replay_after_process_death=true
durable_terminal_state_immutable=true
scenario_execution_enabled=false
hardware_accessed=false
```

该交付不等于 Scenario/Plan/Effect 执行、approval-response/undo、production Event broker、车辆/NPU/
Driver-HAL、目标硬件资格或量产。Event V1 terminal cursor/ACK 仍由 `ISSUE-034` 和 P1-W07 跟踪。
Req IDs：`S2-SES-001`、`S2-GRF-001`、`S2-EFF-001`、`S2-EVT-001`、`XSC-005/006`、
`NV-G-003/004/006/007`、`NV-P-002`、`DEL-001/003..005`。

## Stage 2 P1-W07 Runtime Contract v2 Aggregate Delivery

受维护交付新增：

1. `central-brain/contracts/central_brain_runtime_contract_v2.json`：P1 aggregate capability/error/bounds/
   persistence/compatibility source of truth；
2. SDK `RuntimeContractV2` 与 JVM regression：聚合版本、V1 wire version、page/cursor 和稳定 facade error；
3. `tools/check_central_brain_runtime_contract_v2.sh`：一次验证全部 P1 checksum/checker、Room v4、
   capability、SDK tests、forbidden fallback 和 Event cursor evolution boundary。

累计 SDK instrumentation 在 Android 13/API 33 ARM64 上验证相同 aggregate constants 与未发布边界，
输出 `runtime_contract_v2_physical_android13_arm64_verified=true`；该测试不调用车辆/NPU/Driver/HAL。

交付标志：

```text
runtime_contract_v2_defined=true
runtime_contract_v2_verified=true
runtime_contract_v2_physical_android13_arm64_verified=true
frozen_v1_hashes_unchanged=true
event_v2_cursor_ack_required=true
event_v2_interface_published=false
forbidden_network_python_fallback=false
scenario_execution_enabled=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该包不新增 APK/Service/签名权限，不发布 Event V2、Plan/Effect execution 或车辆/NPU adapter，也不代表
Binder latency 已在目标量产硬件上定标。Event V2 实现与高吞吐证据保留到 `P6-W01/P6-W02`。

## Android P2-W01 Canonical Vehicle Signal Schema

受维护交付新增：

1. `runtime-service/.../vehicle/schema/VehicleSignalPath.java`：12 项 VSS-style path allowlist，固定
   scalar type、unit、area 和 maximum age；
2. `SignalValue/SignalQuality/SignalSource/SignalTimestamp`：显式 typed scalar、no-value quality、
   provenance 和 receive-side monotonic freshness；
3. `SignalValueTest`：path/unit/area/type、stale/invalid quality、future timestamp、invalid payload；
4. `VehicleSignalSchemaProbeActivity`：DUMP-protected debug-only API 33 ARM64 contract probe；
5. `tools/check_central_brain_android_vehicle_signal_schema.sh`：源码、测试、文档、生产未接线和禁用硬件
   引用的单一静态门禁。

交付标志：

```text
vehicle_signal_schema_defined=true
vehicle_signal_path_allowlist_count=12
vehicle_signal_schema_android13_arm64_verified=true
vehicle_signal_provider_wired=false
vehicle_property_mapping_configured=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该包不交付 CapabilityCatalog、Digital Twin、ContextSnapshot、AAOS/Vendor provider、VHAL/property
mapping、车辆 read/write、Effect 或 NPU。API 33 ARM64 证据只证明同一 Java 合同可在目标 Android ABI
运行，不证明车辆硬件能力。Req IDs：`S2-CTX-001`、`S2-TWN-001`、`DEL-001/003..005`；偏差/问题：
`DEV-030`、`ISSUE-030`。

## Android P2-W02 Vehicle Capability Catalog

受维护交付新增：

1. `VehicleCapability`：8 个稳定 capability ID、version、areas、typed `TargetRange`、risk、optional
   reported signal 和 required fresh signals；
2. `CapabilityAvailability`：readable/writable/simulatable 与独立 productionAvailable/
   productionAuthorized，授权要求 available+writable；
3. `CapabilityCatalog`：确定顺序、immutable view、duplicate rejection、production authorized count；
4. `CapabilityCatalogTest`：range/step/text、immutability、readback/dependency、fail-closed activation；
5. `VehicleCapabilityCatalogProbeActivity` 和独立 checker：API 33 ARM64 软件合同与无硬件引用验证。

交付标志：

```text
vehicle_capability_catalog_defined=true
vehicle_capability_count=8
vehicle_capability_catalog_android13_arm64_verified=true
vehicle_capability_target_ranges_verified=true
vehicle_production_capability_authorized_count=0
vehicle_capability_adapter_registry_wired=false
vehicle_property_mapping_configured=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该包不交付 Digital Twin state/store、ContextSnapshot、adapter registry、AAOS/Vendor mapping、Effect 或
NPU。Range/risk/dependency 是 Stage 2 debug/test 软件合同，不是 OEM 标定/安全认证。Req IDs：
`S2-TWN-001`、`S2-ADP-001`、`DEL-001/003..005`；偏差/问题：`DEV-031`、`ISSUE-029/030`。

## Android P2-W03 Vehicle Digital Twin Store

受维护交付新增：

1. `runtime-service/.../vehicle/twin/` 四个纯 Java类：thread-safe store、atomic snapshot、typed desired、
   effective-quality reported record；
2. `VehicleDigitalTwinStoreTest`：并发 CAS、revision/idempotency、stale/conflict、TTL/freshness、immutable
   filtered snapshot 和 reconciliation；
3. `VehicleDigitalTwinStoreProbeActivity`：DUMP-protected debug-only API 33 ARM64 software contract probe；
4. `tools/check_central_brain_android_vehicle_digital_twin.sh`：源码/测试/文档、生产未接线和禁止硬件/
   persistence 引用的静态门禁；
5. 累计 Android installer 与 GitHub CI 已接入上述探针/门禁。

交付标志：

```text
vehicle_digital_twin_store_defined=true
vehicle_digital_twin_android13_arm64_verified=true
vehicle_digital_twin_persistence_wired=false
vehicle_digital_twin_adapter_wired=false
vehicle_property_mapping_configured=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该包不交付 Room persistence、production Service wiring、adapter registry、AAOS/Vendor mapping、真实
vehicle read/write、Effect execution 或 NPU。API 33 ARM64 证据只证明进程内合同可在目标 Android ABI
运行。Req IDs：`S2-TWN-001`、`DEL-001/003..005`；偏差/问题：`DEV-032`、`ISSUE-030`。

## Android P2-W04 Trusted Context Snapshot

受维护交付新增：

1. `ContextFieldPolicy`：general、seat comfort、seat recline 固定 path/area/required policy；
2. `ContextSnapshotBuilder`：single-Twin-revision input、Runtime state freshness、driving/motion conflict、
   Safety/restricted/source mode 和 deterministic SHA-256 identity；
3. `ContextSnapshot`：immutable typed fields 与 missing/stale/conflict/non-production-trusted reports；
4. `ContextSnapshotBuilderTest`：9 组 complete/fail-closed/digest/trust/seat/motion tests；
5. DUMP-protected API 33 ARM64 debug probe、独立 checker、累计 installer 和 GitHub CI wiring。

交付标志：

```text
context_snapshot_defined=true
context_snapshot_android13_arm64_verified=true
context_snapshot_production_trusted=false
context_snapshot_production_wired=false
vehicle_signal_provider_wired=false
vehicle_property_mapping_configured=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该包不交付 AIDL/production Service、Room persistence、vehicle provider/property mapping、Safety authority、
Effect/Graph execution 或 NPU。SIMULATED complete Context 只用于 debug/test；AAOS/VENDOR enum 不构成
production trust。Req IDs：`S2-CTX-001`、`S2-SAF-001`、`DEL-001/003..005`；偏差/问题：
`DEV-033`、`ISSUE-029/030`。

## Android P2-W05 Scenario Manifest

受维护交付新增：

1. `runtime-service/.../scenario/ScenarioManifest.java`：immutable source/zone/context/capability/risk/
   Plan-template/fallback/UI metadata；
2. `ScenarioManifestParser.java`：Gson strict streaming、64 KiB/depth/token bound、unknown/duplicate/type/
   version/path/capability reject；
3. `ScenarioCatalog.java`：deterministic index、duplicate scenario-ID disable、invalid asset isolation 和
   catalog SHA-256 digest；
4. cold/fatigue/rest 三份 build-owned v1 JSON、draft-2020-12 strict schema 和 `scenarios-v1.sha256`；
5. 7 组 JVM tests、DUMP-protected API 33 ARM64 packaged-assets probe、独立 checker、累计 installer 与 CI。

交付标志：

```text
scenario_manifest_schema_version=1
scenario_catalog_count=3
scenario_manifest_android13_arm64_verified=true
scenario_manifest_artifact_crypto_verified=false
scenario_catalog_production_trusted=false
scenario_runtime_wired=false
scenario_graph_execution_enabled=false
effect_dispatch_enabled=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该包不交付独立 artifact signer/revoke/rollback、Resolver、Compiler、Graph Runtime、Effect dispatch、
production Service、vehicle/VHAL/NPU adapter 或 Driver/HAL。SHA-256 sidecar 是 build identity，不是签名；
API 33 ARM64 证据只证明同一严格 parser/catalog 与打包 assets 可运行。Req IDs：`S2-SCN-001`、
`S2-SAF-001`、`DEL-001/003..005`；偏差/问题：`DEV-034`、`ISSUE-029/031`。

## Android P2-W06 Deterministic Scenario Resolver

受维护交付新增：

1. `ScenarioResolver`：bounded internal Request、software/production capability profile 和 immutable
   capability availability snapshot；
2. `DeterministicScenarioResolver`：explicit ID priority、固定中英文 alias、unknown/ambiguous fail-closed、
   source/zone/Context/capability/PARKED_ONLY gate；
3. `ScenarioResolution`：immutable accept/degrade/reject、stable reason、candidate/unavailable lists 和
   request/Context/capability/manifest-bound SHA-256 identity；
4. 7 组 JVM tests、DUMP-protected Android 13 ARM64 debug probe、独立 checker、累计 installer 与 CI。

交付标志：

```text
scenario_resolver_defined=true
scenario_resolution_schema_version=1
scenario_resolver_android13_arm64_verified=true
scenario_resolver_model_invoked=false
scenario_resolver_runtime_wired=false
scenario_compiler_wired=false
scenario_graph_execution_enabled=false
effect_dispatch_enabled=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该包不交付 product intent taxonomy/rollout owner、模型候选、Plan Compiler、Graph Runtime、Effect dispatch、
Room/production Service、vehicle/VHAL/NPU adapter 或 Driver/HAL。software simulation availability 只用于
debug/test；production profile 在当前 non-trusted Context/capability foundation 上必须拒绝。API 33 ARM64
证据只证明同一 resolver contract 可运行。Req IDs：`S2-SCN-001`、`S2-SAF-001`、
`DEL-001/003..005`；偏差/问题：`DEV-035`、`ISSUE-029/031`。

## Android P2-W07 Scenario Plan Compiler

受维护交付新增：

1. `ScenarioPlanCompiler`：严格绑定 Resolution/Context/Capability/manifest 的 compile request 和
   optional-only fallback branch pruning；
2. `CompiledPlan`：immutable owner、deep-copy P1 typed Plan transport、非执行/非生产信任边界；
3. `PlanDigest`：node input 与完整 DAG 的 canonical SHA-256；
4. `PlanGraphValidator`：P1 structure 加 required verify、HIGH approval predecessor、compensation、
   moving/unknown `PARKED_ONLY`/driver recline absence；
5. 7 组 JVM tests、DUMP-protected Android 13 ARM64 debug probe、独立 checker、累计 installer 与 CI。

交付标志：

```text
scenario_plan_compiler_defined=true
scenario_plan_schema_version=1
scenario_plan_compiler_android13_arm64_verified=true
scenario_plan_compiler_runtime_wired=false
scenario_plan_runtime_published=false
scenario_graph_execution_enabled=false
effect_dispatch_enabled=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该包不交付 Effect target/material、Graph scheduler/checkpoint/recovery、Session/Room/production Service wiring、
simulated/production adapter、vehicle/VHAL/NPU 或 Driver/HAL。API 33 ARM64 证据只证明编译、裁剪、摘要与
拒绝逻辑在目标 Android Java/Parcelable 环境可运行，不表示 Plan 已发布或车辆动作已执行。Req IDs：
`S2-SCN-001`、`S2-GRF-001`、`S2-SAF-001`、`DEL-001/003..005`；偏差/问题：
`DEV-036`、`ISSUE-029/031`。

## Android P2-W08 Simulated Effect Adapter Base

受维护交付新增：

1. debug-only `SimulationClock`：显式单调时间推进，不依赖 wall clock 或 `sleep`；
2. debug-only `FaultInjectionProfile`：immutable digest-bound fault selection，覆盖 delay、timeout、retryable/
   terminal failure 和 readback mismatch；
3. debug-only `SimulatedEffectAdapter`：复用 typed `EffectAdapter`，有界 128 条 process-memory record、token
   幂等、linearizable status 和 delivery/readback 分离；
4. 7 组 JVM tests、debug/release source compile、DUMP-protected Android 13 ARM64 probe、独立 checker、累计
   installer 与 CI gate。

交付标志：

```text
simulated_effect_adapter_base_defined=true
simulated_effect_adapter_android13_arm64_verified=true
simulated_effect_adapter_debug_only=true
simulated_effect_adapter_release_source_absent=true
simulated_effect_adapter_production_registered=false
simulated_effect_adapter_runtime_wired=false
effect_dispatch_enabled=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该包不交付 HVAC/Seat/Media/Nav typed target、domain adapter、Twin wiring、Room persistence、Plan/Graph/Effect
Runtime、真实 Vehicle/VHAL/NPU 或 Driver/HAL。probe 只证明同一 debug software contract 能在 Android 13
ARM64 上执行，不能作为真实车辆回读、production activation 或目标硬件资格证据。Req IDs：
`S2-ADP-001`、`S2-EFF-001`、`DEL-001/003..005`；偏差/问题：`DEV-037`、`ISSUE-030/033`。

## Android P2-W09 Simulated HVAC Adapter

受维护交付新增：

1. debug-only `SimulatedHvacEffectAdapter` 和 versioned fixed-binary `HvacTarget`；
2. HVAC power/target-temperature/fan absolute target、action/capability/area/range/step validation；
3. adapter-owned P2-W03 desired/reported Twin、180 秒 desired TTL、source SIMULATED readback；
4. delay/timeout/retryable/terminal/mismatch/idempotency fault matrix；
5. 7 组 JVM tests、debug/release compile、Android 13 ARM64 probe、checker、累计 installer 与 CI。

交付标志：

```text
simulated_hvac_adapter_defined=true
simulated_hvac_typed_target_verified=true
simulated_hvac_desired_reported_verified=true
simulated_hvac_android13_arm64_verified=true
simulated_hvac_debug_only=true
simulated_hvac_release_source_absent=true
simulated_hvac_production_registered=false
simulated_hvac_runtime_wired=false
effect_dispatch_enabled=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该包不交付 production HVAC adapter/property mapping、shared Twin/Room、Plan/Graph/Effect Runtime、Client2
HVAC 页面或真实 Vehicle/VHAL/NPU/Driver-HAL。API 33 ARM64 证据只证明 debug software target/fault/readback
合同可运行。Req IDs：`S2-ADP-001`、`S2-EFF-001`、`DEL-001/003..005`；偏差/问题：
`DEV-038`、`ISSUE-030/033`。

## Android P2-W10 Simulated Seat Adapter

受维护交付新增：

1. debug-only `SimulatedSeatEffectAdapter` 和 versioned fixed-binary `SeatTarget`；
2. heating/ventilation/recline absolute target、action/capability/area/range/step validation；
3. recline admission+dispatch fresh Safety/occupancy/belt/approval revalidation 与永久 race reject；
4. adapter-owned desired/reported Twin、180 秒 TTL、bounded progress observation 和 fault/readback matrix；
5. 8 组 JVM tests、debug/release compile、Android 13 ARM64 probe、checker、累计 installer 与 CI。

交付标志：

```text
simulated_seat_adapter_defined=true
simulated_seat_typed_target_verified=true
simulated_seat_recline_safety_verified=true
simulated_seat_dispatch_revalidation_verified=true
simulated_seat_progress_verified=true
simulated_seat_android13_arm64_verified=true
simulated_seat_debug_only=true
simulated_seat_release_source_absent=true
simulated_seat_production_registered=false
simulated_seat_runtime_wired=false
effect_dispatch_enabled=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该包不交付 OEM Safety/approval authority、production Seat adapter/property mapping、shared Twin/Room、
Plan/Graph/Effect Runtime、Client2 Seat 页面或真实 Vehicle/VHAL/NPU/Driver-HAL。API 33 ARM64 证据只证明
debug software safety race 与 readback 合同可运行。Req IDs：`S2-ADP-001`、`S2-SAF-001`、
`DEL-001/003..005`；偏差/问题：`DEV-039`、`ISSUE-029/030/033`。

## Android P2-W11 Simulated Media/Navigation Adapters

受维护交付新增：

1. debug-only `SimulatedMediaEffectAdapter` 与 `SimulatedNavigationEffectAdapter`；
2. version 1 PLAY/PAUSE/STOP 和 NFKC POI typed targets；
3. immutable simulated player state 与 digest-only deterministic synthetic POI/route observation；
4. replaceable backend safety descriptor、delay/timeout/failure/mismatch/idempotency；
5. 8 组 JVM tests、debug/release compile、Android 13 ARM64 probe、checker、累计 installer 与 CI。

交付标志：

```text
simulated_media_adapter_defined=true
simulated_navigation_adapter_defined=true
simulated_media_nav_typed_target_verified=true
simulated_media_state_verified=true
simulated_navigation_synthetic_observation_verified=true
simulated_navigation_query_digest_only=true
simulated_media_nav_replaceable_backend_verified=true
simulated_media_nav_android13_arm64_verified=true
simulated_media_nav_debug_only=true
simulated_media_nav_release_source_absent=true
simulated_media_nav_production_registered=false
simulated_media_nav_runtime_wired=false
external_activity_started=false
location_uploaded=false
network_accessed=false
effect_dispatch_enabled=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该包不交付 Android/vendor media 或 navigation engine、真实 POI/route/location、外部 Activity、network、
production adapter、shared Runtime/Room/Graph/Effect 或真实 Vehicle/VHAL/NPU/Driver-HAL。Req IDs：
`S2-ADP-001`、`DEL-001/003..005`；偏差/问题：`DEV-040`、`ISSUE-030/031/033`。

## Android P2-W12 Debug Simulation Controller

受维护交付新增：

1. debug-only `IDebugSimulationController` V1、`DebugSimulationController` 与 protected Service；
2. debug-only signature permission + calling UID/package/current-signer capability 双层授权；
3. typed driving/canonical signal、四 adapter fault、manual clock、reset 和 digest-only snapshot；
4. 128 条 bounded digest-only audit，accepted/rejected Service audit；
5. 7 组 JVM tests、debug/release compile、Android 13 ARM64 Binder probe、checker、累计 installer 与 CI。

交付标志：

```text
debug_simulation_controller_defined=true
debug_simulation_controller_aidl_version=1
debug_simulation_controller_signature_permission_enforced=true
debug_simulation_controller_capability_enforced=true
debug_simulation_controller_state_signal_fault_clock_reset_verified=true
debug_simulation_controller_audit_bounded_verified=true
debug_simulation_controller_android13_arm64_verified=true
debug_simulation_controller_debug_only=true
debug_simulation_controller_release_source_absent=true
debug_simulation_controller_production_exported=false
debug_simulation_controller_runtime_wired=false
vehicle_signal_provider_wired=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该包不交付 production Context provider、vehicle signal provider、shared Twin/Room、Graph/Effect execution、
Client2 engineer drawer、真实 Vehicle/VHAL/NPU/Driver-HAL。API 33 ARM64 只证明 debug control-plane Binder 与
授权可运行。Req IDs：`S2-CTX-001`、`S2-ADP-001`、`DEL-001/003..005`；偏差/问题：`DEV-041`、
`ISSUE-030/033`。

## Android P3-W01 Agent Graph Runtime

受维护交付新增：

1. Runtime main-source `AgentGraphRuntime`、`GraphRunState`、`NodeRunState` 与 control-only
   `NodeExecutorRegistry`；
2. P1/P2 typed Plan validation/deep copy、合法 graph/node transition、同 session FIFO、最多 8 个跨 session
   active run 和最多 64 个 process record；
3. manual-clock plan deadline、optional PARTIAL、required fail-closed、waiting/resume 和 terminal capacity eviction；
4. 每 run 最多 256 条 retained digest-only event，累计 count 与链式摘要覆盖已淘汰 entry；
5. 7 组 JVM tests、debug/release compile、Android 13 ARM64 probe、checker、累计 installer 与 CI。

交付标志：

```text
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
effect_dispatch_enabled=false
model_invoked=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

该包是 process-local state-machine foundation，不是 Durable Agent Graph 完成交付：它不调用 typed executor，
不写 Room/checkpoint，不发布 Binder/Session/Plan Service，不执行 Effect/Model/Tool/Memory，也不调用 P2 debug
adapter/controller 或真实 Vehicle/VHAL/NPU/Driver-HAL。API 33 ARM64 只证明相同状态合同在目标 Android 运行。
Req IDs：`S2-GRF-001`、`NV-G-004/006/007`、`DEL-001/003..005`；偏差/问题：`DEV-042`、
`ISSUE-022/026`。

## Android P3-W02 Typed Node Executors

受维护交付新增：

1. Runtime main-source fixed typed input/output/result、11 类 exact-class schema、`TypedNodeExecutor` 与
   control-only registry validation；
2. Runtime debug-source 7 类 deterministic executor 和显式 harness；Model/Tool/Memory 无 executor；
3. Context/Verification no trust upgrade、Policy/Approval authority gate、Effect/Compensation fail-closed、
   bounded Summary；
4. 8 组 JVM tests、debug/release compile、Android 13 ARM64 probe、checker、累计 installer 与 CI。

交付标志：

```text
typed_node_executor_contract_defined=true
typed_node_executor_schema_count=11
typed_node_executor_debug_count=7
typed_node_executor_exact_class_verified=true
typed_node_executor_context_verified=true
typed_node_executor_policy_approval_verified=true
typed_node_executor_effect_fail_closed_verified=true
typed_node_executor_verification_verified=true
typed_node_executor_summary_verified=true
typed_node_executor_unsupported_fail_closed_verified=true
typed_node_executor_android13_arm64_verified=true
typed_node_executor_graph_dispatch_enabled=false
typed_node_executor_production_wired=false
effect_dispatch_enabled=false
model_invoked=false
network_accessed=false
hardware_accessed=false
production_ready=false
target_hardware_validated=false
```

本包交付 schema 与 debug/test executor 行为，不交付 Graph dispatch、checkpoint/Room、Binder、production
Context/Safety/approval、Effect adapter/model provider 或车辆/NPU/Driver-HAL。Release 仅含合同类，不含
`DeterministicNodeExecutors` 或 probe。Req IDs：`S2-GRF-001`、`S2-SAF-001`、`S2-EFF-001`、
`DEL-001/003..005`；偏差/问题：`DEV-043`、`ISSUE-022/023/024/026`。
