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
| ISSUE-034 | Event V1 terminal cursor 不能前移 ACK；当前靠 sequence 去重但不适合高吞吐 broker。 | S2-EVT-001, P6-W01/W02 | Open / Design Decided |
| ISSUE-035 | Client2 process-recreation checkpoint 的 production storage/backup/user owner 未确定。 | S2-UX-001..003, DEV-052 | Open |
| ISSUE-036 | Tool production owner、health source、artifact trust 与 execution authority 未确定。 | S2-TOL-001, P5-W02..W05 | Open |
| ISSUE-037 | Tool health publisher、production Registry composition 和 snapshot trust owner 未确定。 | S2-TOL-001, S2-SAF-001, P5-W03..W05/P8 | Open |
| ISSUE-038 | Production Tool rule catalog、condition publisher、Plan binding 与 approval authority 未确定。 | S2-TOL-001, S2-SAF-001, P5-W04/W05/P8 | Open |
| ISSUE-039 | Production built-in signer evidence、artifact revoke/rollback 与非合作实现的 deadline/cancel owner 未确定。 | S2-TOL-001, S2-SAF-001, P5-W05/P9 | Open |
| ISSUE-040 | Trusted signer evidence、Skill policy 原子发布、签名链、lifecycle 与动态装载 owner 未确定。 | S2-TOL-001, S2-SAF-001, FW-U-008, P8/P9 | Open |
| ISSUE-041 | Working Memory 的 production Session terminal source、tokenizer/budget authority、payload privacy 与 durable storage owner 未确定。 | S2-MEM-001, S2-SAF-001, P5-W07..W10/P9 | Open |
| ISSUE-042 | Profile Memory 的 user/seat identity、consent/revocation、Keystore/TEE key lifecycle、durable repository 与 export/delete owner 未确定。 | S2-MEM-001, S2-SAF-001, P5-W08..W10/P9 | Open |
| ISSUE-043 | Episodic Memory 的 production catalog、storage/read/erase authority、retention clock、durable repository 与 model publication owner 未确定。 | S2-MEM-001, S2-SAF-001, P5-W09/W10/P9 | Open |
| ISSUE-044 | Context 的 production tokenizer/version/digest、size evidence、budget authority、summary/truncation executor 与 Runtime/model composition owner 未确定。 | S2-MEM-001, S2-MDL-001, S2-SAF-001, P5-W10/P7/P9 | Open |
| ISSUE-045 | Memory consent 的 production identity/authority、HMI Service、repository mutation/delete evidence 与 trusted driving Context owner 未确定。 | S2-MEM-001, S2-UX-003, S2-SAF-001, P8/P9 | Open |
| ISSUE-046 | Event Broker 的 durable append/cursor repository、middleware/QoS、identity/policy 与跨进程 callback owner 未确定。 | S2-EVT-001, S2-SAF-001, P6-W02/P8/P9 | Open |
| ISSUE-047 | P8 目标 property/service/permission/owner/version/readback/fault evidence 未取得，真实 adapter 不能启动。 | S2-ADP-002, S2-OBS-001, P8-W01..W06 | Open / External Blocked |
| ISSUE-048 | P9 十项预算缺目标 Android 13 采集、30-sample 报告、owner approval 和 release qualification。 | S2-OBS-001, S2-REL-001, P9-W01/W02 | Open |
| ISSUE-049 | P9 稳定性矩阵缺真实 fault injector、目标 72h run、受控证据和 owner approval。 | S2-REL-001, S2-OBS-001, P9-W02 | Open / External Blocked |
| ISSUE-050 | P9 可执行 security campaign 已按用户决策撤回并挂起；仅保留外部证据接口。debug Binder identity/callback replay 保留，production signer/owner 未完成。 | S2-SAF-001, S2-TOL-001, S2-OBS-001, P9-W03 | Suspended |
| ISSUE-051 | P9 durable privacy lifecycle 缺 owner policy、repository enforcement 和目标 evidence。 | S2-MEM-001, S2-SAF-001, P9-W04 | Open |
| ISSUE-052 | P9 production signer、installer/rollback owner 和受控发布证据不可用。 | S2-REL-001, P9-W05 | Open / External Blocked |
| ISSUE-053 | P9 target field diagnostics、replacement release 与 owner retest evidence 不可用。 | S2-OBS-001, S2-REL-001, P9-W07 | Open / External Blocked |

## ISSUE-019 Client2 APK patch 验收边界

Client2 已通过 typed Session/Event Binder、signature permission、current-signer capability、snapshot/event/replay、
UI projection 和恢复矩阵验证。2026-07-17 证据证明当前 1920x1080 Android 13 ARM64 目标上的菜单交互、Runtime
process-death reconnect/duplicate suppression 和 Client2 restart 可用。

P4-W02 已删除旧 Smali state owner，maintained Java coordinator 直接持有 typed SessionConnection；物理设备已验证
hide 后 Client2 process restart、existing Session resume/replay、hidden state restore 和菜单重开。该结果降低 patch
维护风险，但不解决闭源 MainActivity hook、导航几何、production signer 或 Car UX 限制。

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

P3-W02 进展：已增加 11 类 exact schema 与 7 类 debug deterministic executor，但 registry/Graph 不调度。
Effect 固定 NOT_DISPATCHED，Compensation 固定拒绝，尚无 checkpoint/Room/reconcile/material owner；因此不改变
本问题 Open 状态，也不构成 durable Effect execution。

P3-W03 进展：已增加 registered DTO、bounded primitive tree、canonical JSON、type/version/digest 与 security
corpus，并在 API 33 ARM64 运行。serializer 未接 Graph/Room/Session/restart recovery，未定义 encryption/key/
retention/migration owner，也未把 mismatch 映射 STUCK；因此只关闭 codec 安全合同子项，本问题保持 Open。

P1-W04 的 Effect transition 和 UndoHandle 只定义 wire/validation 语义，不连接现有 effect/outbox
repository，也不执行补偿。Undo 必须在未来创建新的受治理 compensation operation；它不能被实现为
数据库状态回滚。Crash recovery、material/key、trusted clock、status reconciliation 和 durable binding
仍为本问题的开放项。

P3-W01 进展：新增 process-local Graph/Node state reducer、同 session FIFO、跨 session bounded slot、
manual deadline 和 digest-only event projection。它不写 Room/checkpoint、不执行 executor/Effect/compensation，
进程死亡会丢失全部 graph state；因此只关闭状态机结构子项，durability、reconcile 和副作用恢复仍保持 Open。

P3-W08 进展：已增加显式 reversible policy、VALID before snapshot、绝对 target、reverse dependency plan、
TTL/digest handle、Context/Policy/Safety 复验和新的 governed task admission。原 VERIFIED Effect 保持不可变，
不会通过数据库回滚或状态倒退伪造 Undo。当前 admission/before material/idempotency 仍是 process-local，
不接 Graph/Room/Binder/adapter，且 PRODUCTION 固定拒绝；P1 V1 的 COMPENSATING/COMPENSATED 枚举在冻结
transition 中不可达，已登记 `DEV-049`，需要独立 compensation operation contract。因此本问题保持 Open。

P3-W09 进展：已增加 WAITING/EXECUTING/UNKNOWN fail-closed reducer、Room v4 bounded recovery repository、
checkpoint mismatch STUCK、typed Effect/approval/undo reconcile directive、process-death reopen 和 exactly-once digest
audit。Android 13 ARM64 probe 在两次 `force-stop` 后确认相同 digest replay 不新增状态变化、审计或副作用，并以
A-B-A 结果顺序确认历史 digest 也按 event ID exactly-once。Effect observation 与
Compensation evidence 保持不可变。

该增量仍未接 `CentralBrainRuntimeService`、`AgentGraphRuntime`、Binder、trusted Evidence provider、scheduler 或
production Effect adapter，`graph_restart_runtime_wired=false`、`production_effect_dispatch_enabled=false`；已登记
`DEV-050`。因此 ISSUE-022 只关闭 recovery reducer/repository 子项，真实 material/key/trusted clock/retention、
Runtime hydration、adapter status authority 和目标故障证据仍开放。

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

### P7-W01 ModelRequest/Result v2 progress

已关闭 ISSUE-024 的“缺少统一 request/result metadata contract”子项：schema V2 已冻结 purpose、privacy、latency/token budget、
required capability、fallback、trace，以及 request fingerprint/result binding。该进展不关闭 ISSUE-024；Provider registry/health、
policy router、local/vendor/cloud provider、NPU C/JNI/Driver-HAL、资源标定和目标性能证据仍未完成。

与 ISSUE-044 的接口边界：P7-W01 只消费受信 token budget 与 input digest，不获取 tokenizer/version，不执行 summary/truncation，
不接 Memory/Context publication。原始内容不能通过 v2 contract 或 debug probe 泄露。

状态：`model_contract_v2_defined=true`、`model_request_v2_fields_verified=true`、
`model_result_v2_binding_verified=true`、`model_privacy_fallback_fail_closed=true`、
`model_raw_content_accepted=false`、`model_provider_registry_wired=false`、`model_policy_router_wired=false`、
`model_contract_v2_android13_arm64_verified=true`、`model_invoked=false`、`npu_accessed=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。tracking：`DEV-078`。

### P7-W02 ModelProviderRegistry/health progress

已关闭 ISSUE-024 的“缺少固定 Provider catalog 与 health freshness contract”子项。四项 descriptor、capability、source、revision、
validity、replay/conflict/stale 与 test/development/production separation 已冻结。

ISSUE-024 仍为 Open：production health publisher identity/authority、atomic catalog/config publication、local provider implementation、
vendor SDK/NPU provider、cloud consent/network owner、Router/Scheduler/Runtime composition 和目标性能/故障证据均未完成。当前 HEALTHY
placeholder 仍不可用、不可 production ready、不可路由。

状态：`model_provider_registry_defined=true`、`model_provider_count=4`、
`model_provider_health_freshness_verified=true`、`model_provider_health_replay_verified=true`、
`model_provider_availability_separation_verified=true`、`model_provider_placeholder_fail_closed=true`、
`model_contract_test_available_count=1`、`model_development_available_count=1`、`model_production_ready_count=0`、
`model_provider_registry_android13_arm64_verified=true`、`model_provider_registry_runtime_wired=false`、
`model_policy_router_wired=false`、`model_invoked=false`、`network_accessed=false`、`npu_accessed=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。tracking：`DEV-079`。

### P7-W03 PolicyAwareModelRouter progress

已关闭 ISSUE-024 的“缺少统一 route admission decision”软件子项。V2 request、fixed registry snapshot 和 freshness-bounded policy
snapshot 现在可以确定性生成 mode/health/privacy/network/thermal/latency/capability/quota rejection、primary/fallback metadata 和
canonical decision digest。fallback 最多 1 项，decision 固定不授予 action/Effect authority。

ISSUE-024 仍为 Open：policy/health/resource/quota production publisher identity 与 atomic revision、P7-W04 local provider、vendor SDK/NPU
provider、cloud consent/network owner、P7-W05 output schema、P7-W07 scheduler/resource composition、Runtime wiring、真实 inference/fallback/
cancel 和目标性能/故障证据均未完成。当前 production ready Provider 为 0，Router 不能执行模型。

状态：`model_policy_router_defined=true`、`model_policy_router_privacy_network_thermal_verified=true`、
`model_policy_router_latency_capability_quota_verified=true`、`model_policy_router_fallback_bounded=true`、
`model_policy_router_no_action_authority=true`、`model_policy_router_android13_arm64_verified=true`、
`model_policy_router_runtime_wired=false`、`provider_invoked=false`、`model_invoked=false`、`network_accessed=false`、
`npu_accessed=false`、`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。tracking：`DEV-080`。

### P7-W04 LocalModelProvider progress

已关闭 ISSUE-024 的“缺少可执行 Android 本地开发 Provider”软件子项。debug variant 现在提供显式 factory、in-process engine port、
deadline/cancel、bounded streaming/non-streaming、typed terminal/fault/metrics 和 sanitized device probe。fixed catalog 的 local descriptor
development availability 为 1，但 production implementation/eligibility/readiness 仍为 0。

ISSUE-024 仍为 Open：P7-W05 prompt/output schema 与 validator、P7-W06 evaluation、P7-W07 resource/thermal composition、production
policy/health owner、Runtime dispatch、Vendor SDK/NPU Provider、cloud consent/network owner、真实 fallback/cancel/fault/性能证据均未完成。
debug Local Provider 不进入 release source，不得作为 Vendor NPU 或 production route 的回退。

状态：`local_model_provider_verified=true`、`local_model_provider_deadline_verified=true`、
`local_model_provider_cancel_verified=true`、`local_model_provider_stream_limit_verified=true`、
`local_model_provider_debug_only=true`、`local_model_provider_release_source_absent=true`、
`local_model_provider_runtime_wired=false`、`local_model_provider_vendor_npu_fallback_enabled=false`、
`local_model_provider_android13_arm64_verified=true`、`production_inference_enabled=false`、`network_accessed=false`、
`npu_accessed=false`、`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。tracking：`DEV-081`。

## ISSUE-025 Event、Memory、Skill 生命周期与治理链

Android 已有 bounded Event、durable cursor、bounded Memory、built-in Skill test runtime 和固定
governance middleware。尚缺 production broker、cursor/retention owner、隐私分类与删除、Skill
签名/revoke/rollback/sandbox、route registry 和持久 audit exporter。当前实现不得被描述为开放式
插件平台。

## ISSUE-026 Android 聚合验收与量产激活边界

R7 acceptance snapshot、Client2 Binder、application handoff 和 hybrid delivery 只证明仓库软件
一致性与 Android 应用集成。它们不能设置 `production_ready`、`target_hardware_validated`、
Driver/HAL 或 virtualization 标志。

P3-W01 进展：Android 13 ARM64 probe 只证明同一 process-local Graph 状态合同可运行；
P3-W02 进展：Android 13 ARM64 probe 只证明 typed schema、exact-class 与 deterministic fail-closed 行为；
P3-W03 进展：Android 13 ARM64 probe 只证明 checkpoint codec/digest/limit/security 行为；Graph dispatch、Room
recovery、production executor、Effect/model/vehicle/NPU/hardware 仍未接，不提升 production/hardware 状态。
`agent_graph_runtime_production_wired=false`、`effect_dispatch_enabled=false`、`hardware_accessed=false`，不能提升
production/target maturity，本问题保持 Open。

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

P2-W08 进展：debug-only base 已冻结幂等、delay/timeout/failure/readback mismatch 语义，但没有 HVAC/Seat
typed target、Safety provider 或 Runtime 注册，不能关闭本 issue。

P2-W10 进展：Seat recline 已在 debug adapter 的 admission 和 dispatch 两次校验 fresh NORMAL+PARKED、
driver availability、occupancy、belt 和 approval revision；moving/unknown/belt/approval race 永久拒绝且不写
reported。该输入均为 simulation-only 注入，不定义 OEM 最大角度、硬联锁或批准 authority，本问题保持 Open。

P9-W06a 进展：已新增 12-action 生产准入合同，固定 500 ms production-trusted Safety State、
PARKED/MOVING/UNKNOWN/FAULT UX、三 owner role 和 capability availability/authorization/readback/activation。moving 对驾驶分心
UI 与 driver recline 为 hard deny，parked recline 最多返回 approval required，所有 decision 均不授权 Effect dispatch。
该结果仍无真实 owner、vehicle state producer、IDLE 联合语义、座椅硬联锁或 Android target evidence，因此 ISSUE 保持 Open。

P9-W06b 进展：已新增 27-key 脱敏 projection、DUMP-protected debug Activity 和只读 ADB adapter。它只报告 W06a 固定目录的
计数/布尔值，不读取真实车辆状态、owner reference 或硬件。当前 target probe 未执行；未来即使 contract probe 在 API 33 ARM64
运行成功，也不等于座椅硬联锁、驾驶分心或 OEM owner 验收，因此 ISSUE 保持 Open。

当前 `driver_safety_admission_defined=true`、`driver_safety_moving_hard_interlock_verified=true`、
`driver_safety_current_owner_policy_approved=false`、`driver_safety_vehicle_state_provider_wired=false`、
`driver_safety_effect_runtime_wired=false`、`driver_safety_redacted_projection_defined=true`、
`driver_safety_android_debug_probe_available=true`、`driver_safety_android_debug_probe_executed=false`、
`driver_safety_android13_arm64_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`。tracking：`DEV-096/097`。

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

P2-W08 进展：simulation source、production trust false 和 delivery/readback 分离已固定；这不是 OEM
property/permission/area/readback contract，真实 adapter 仍必须返回 unavailable。

P2-W09 进展：HVAC power/temperature/fan typed target、software area/range/step 与 isolated Twin readback
已完成；没有 OEM property/service/permission/area mapping 或 production owner，本问题保持 Open。

P2-W10 进展：Seat heat/vent/recline typed target、isolated Twin 和模拟 Safety/occupancy/belt/approval race
已完成；没有 OEM Seat/Occupant property、权限、area/readback 或 Safety owner，本问题保持 Open。

P2-W11 进展：Media player state 和 Navigation POI/route observation 已在 debug adapter 中以 simulation-only
形式完成；没有目标 Android/vendor media/navigation API、权限、package owner 或真实 readback，本问题保持 Open。

P2-W12 进展：debug controller 已通过 signature/capability protected Binder 设置 canonical simulated signal、
fault 和 clock，但没有读取目标 property/service，也没有 production policy grant。该控制面只提高测试可控性，
不提供车辆 API、权限、area mapping、readback 或 owner，本问题保持 Open。

P9-W06a/W06b 进展：已冻结 12-action driver-safety admission，并增加只读脱敏 Android contract probe。projection 不接受真实
vehicle/provider 输入，adapter 不读取 property/service/vehicle scalar，也不触发 Effect。当前
`driver_safety_vehicle_state_provider_wired=false`、`driver_safety_effect_runtime_wired=false`、
`driver_safety_android_debug_probe_executed=false`、`driver_safety_android13_arm64_verified=false`；所以 capability/API/permission/
owner 缺口没有被软件探针关闭，ISSUE 保持 Open。tracking：`DEV-096/097`。

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

P2-W11 进展：Navigation backend 只接收 query digest，observation 不复制 raw query、坐标或真实 route，且
网络/位置上传默认拒绝。该 debug 隐私最小化不定义 POI 产品策略、地图 provider、同意或保留 owner，本问题保持 Open。

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

P2-W10 进展：Runtime debug source 已提供 Seat typed target、desired/reported、progress 和安全 race 拒绝，
为后续 HMI-D2/D3 提供可复用软件合同；它尚未接 Session/Graph/Effect Service 或 Client2 renderer，因此
当前 APK 仍不能展示完整 Seat UI/UX 闭环，本问题保持 Open。

P2-W11 进展：Runtime debug source 已提供 media state 和 digest-only synthetic POI/route observation，可供
HMI-D3 展示 optional Effect；仍未接 Session/Graph/Effect Service、Client2 renderer 或真实 player/navigation，
因此不能构成 APK 演示闭环，本问题保持 Open。

P2-W12 进展：debug controller 已提供工程师可控的 driving/signal/fault/clock/reset Binder，可作为后续
HMI-D2/D3 的测试输入；当前尚未由 Client2 engineer drawer 调用，也未接 Agent Graph/Effect timeline，
因此不能构成 APK 演示闭环，本问题保持 Open。

P4-W01 进展：Client2 已不再通过单次 `TaskResult` 驱动文本区。主桥接接口现为 typed Session/Event stream，
Android 13 ARM64 已验证 snapshot、顺序事件、cursor replay、Runtime process-death reconnect/duplicate suppression
和 Client2 restart；旧 `submit` 只作 Smali 二进制兼容。当前仍没有 immutable HMI reducer、四阶段 renderer、
HVAC/Seat surface 或 Runtime 场景执行，因此本问题保持 Open，下一关闭子项为 P4-W02。

P4-W02 进展：immutable `CockpitHmiState`、唯一 reducer、maintained Java coordinator、existing Session resume 和
text-free checkpoint 已完成；旧 Smali controller 已删除。Android 13 ARM64 已验证 Session replacement、Runtime death
replay、Client2 process restart、hidden-state restore 和 UI projection。当前仍无“意图/计划/执行/结果”四阶段 shell、
HVAC/Seat surface 或 Runtime scenario/Graph/Effect 执行，因此本问题保持 Open，下一关闭子项为 P4-W03。

P4-W03 进展：intent-first 四阶段 shell、四项自然场景、source/driving/connection Header、HVAC/Seat 次级详情抽屉、
1920x1080 safe frame 和 60% 半透明材质已进入 Client2 APK。Android 13 ARM64 已验证阶段切换、抽屉、导航显隐、
外部点击隐藏以及 Runtime/Client2 recovery。Plan/Execution/Result 明确显示 unavailable/not wired/not dispatched，
没有伪造车控成功。HVAC/Seat control surface、Runtime scenario/Graph/Effect 和真实 readback 仍未实现，因此本问题
保持 Open，下一关闭子项为 P4-W04；固定画布/placeholder 差异由 `DEV-053` 跟踪。

P4-W04 进展：HVAC control surface 已实现 power/zone/temperature/fan/AUTO/A-C/SYNC/airflow/preset、immutable
desired/request/evidence state、300 ms debounce 和 `scene.manual.hvac.adjust.v1` governed Session。Android 13/API 33
ARM64 证明三次快速温度输入只产生一个 Session，desired 更新为 24.0 C；Session admission 只显示 REQUESTED，reported/
source/quality 仍为 unavailable/no evidence，Effect/Adapter/hardware dispatch 为 0。Seat、Runtime scenario compiler/Graph/
Effect/readback、approval/undo 仍未闭环，因此 ISSUE-033 保持 Open，下一关闭子项为 P4-W05；V1 参数承载偏差由
`DEV-054` 跟踪。

P4-W05 进展：Seat control surface 已实现四座区、heat/vent 0-3 互斥、massage、recline 和 upright/comfort/rest
preset；immutable state 将 desired/request、Safety Context/decision 和 reported/source/quality/effect 分离。Android 13/API 33
ARM64 证明 heat 后 vent 合并为一个 governed Session，结果为 heat=0/vent=1；UNKNOWN_RESTRICTED 驾驶席靠背保持 0，
不创建新 Session、不触发 Effect/硬件。host policy 证明 parked+occupied+unbelted rest 只进入 WAITING_APPROVAL。
这仍不是 OEM Safety authority，真实 Context/approval/dispatch/readback 未接；ISSUE-033 保持 Open，下一关闭子项为
P4-W06 timeline，V1 Seat 参数/approval 承载偏差由 `DEV-055` 跟踪。

P4-W06 进展：Client2 Execution surface 已实现 Intent/Context/Plan/Policy/Graph/Effect/Readback 七阶段、Media STOP/
Navigation CANCEL projection 和最多八条脱敏 typed-event trace。host 覆盖 allowlisted Action/Approval/Effect/Observation/
Compensation 与 freshness/conflict；Android 13/API 33 ARM64 证明当前实体 Runtime 只完成 Session admission，Plan/Graph/
Effect/Readback 保持 NOT PUBLISHED/NOT WIRED/NOT DISPATCHED/UNAVAILABLE。Runtime execution event publication、approval/
partial/retry/undo、真实 dispatch/readback 未完成，因此 ISSUE-033 保持 Open，下一关闭子项为 P4-W07；投影与 Runtime
publication 差异由 `DEV-056` 跟踪。

P4-W07 进展：Client2 recovery UX 已新增 reducer-owned approval status/reason/target/expiry、VERIFIED/FAILED/INCONCLUSIVE
证据统计、Session partial aggregate 和 compensation projection。Android 13/API 33 ARM64 证明当前 Runtime 缺少
ApprovalPrompt/retry metadata/UndoHandle 时四类命令可见但禁用，outside dismiss/reopen 保留 Session/recovery state。
真实 approval response、retry、undo admission、Runtime Graph/Effect publication 和车辆 readback 仍未完成，因此
ISSUE-033 保持 Open，下一关闭子项为 P4-W08 Driving restriction renderer；命令详情/服务缺口由 `DEV-057` 跟踪。

P4-W08 进展：Client2 driving restriction renderer 已新增 reducer-owned `PanelPresentationMode` 和 pure Java
`DrivingUxPolicy`。UNKNOWN/MOVING/unavailable/untrusted Context 统一隐藏长详情、禁用 HVAC/Seat 参数编辑与高风险休息场景；
只有可信 PARKED 恢复完整呈现，且 UI mode 明确不能授予 Effect 权限。当前实体设备没有 trusted global Context provider，
所以本轮只复测默认受限路径，不伪造 PARKED，也不重跑 manual Session admission。ISSUE-033 保持 Open，下一关闭子项为
P4-W09 Engineer simulation drawer；实体 PARKED 复测和 production Context 差异由 `DEV-058` 跟踪。

P4-W09 进展：Client2 engineer simulation drawer 已新增 hidden-until-connected 工程入口、immutable
`CockpitEngineerState` 和 debug AIDL client。Android 13/API 33 ARM64 已验证 signature permission、debug capability、
version/hash、PARKED/MOVING/UNKNOWN、occupancy/belt、HVAC/Seat fault matrix、revision 单调与 reset 失败关闭；
Runtime release 中 Controller Service 不存在。该入口只投影 SIMULATED application-local Context，不能授予 Effect 或替代
production Safety/vehicle provider。因此 ISSUE-033 保持 Open，下一关闭子项为 P4-W10 Scenario/manual-control
synchronization；production authority 偏差由 `DEV-058/059` 跟踪。

关闭条件：`CENTRAL_BRAIN_COCKPIT_HMI_CONTROL_LOOP_PLAN.md` 的 HMI-D4 和 HMI-AI/AC/ST/CL 验收
全部在 Android 13 ARM64 Client2 APK 通过。该关闭只代表演示软件闭环，不关闭 `ISSUE-030`、
Driver/HAL、target hardware 或 production。状态：`Open`。

## ISSUE-034 Session/Event V1 cursor 与进程死亡恢复缺口

P1-W05 已发布 owner-scoped Session/Event app-layer Binder，并在 Android 13 ARM64 验证 Service
rebind 后 active session 重新订阅。P1-W06 已完成 Room v4 repository、v3->v4 migration、crash
transaction rollback 和 Runtime process-death rehydration；相同 sessionId/event history 可恢复，
callback 由 SDK replay 后重新注册。该问题的进程死亡子项已关闭。

2026-07-19 关闭 callback 配额泄漏子项：此前健康 `reconnect()` 会清空客户端 callback map，但没有向
仍存活的旧 Event Binder 注销 callback，连续重连可能命中每 session 4 callback 上限。transport 现已在
unbind 前对称注销，并拒绝跨 generation 注册竞态；API 33 ARM64 连续 6 次健康重连通过，
`healthy_reconnect_callback_cleanup_verified=true`。这不改变下述 Event V1 terminal cursor 缺口。

冻结的 Event V1 还存在 cursor 语义缺口：`hasMore=false` 的 terminal page 不提供可前移的 resume cursor。
当前 facade 只能保留该 terminal request cursor，并用递增 sequence 去除 register/reconnect replay 的
重复事件；结果正确但可能重复读取已见历史，不能扩展为高吞吐 durable broker。P1-W07 aggregate review
已决定新增独立 Event V2 resume cursor/ACK contract：terminal page 也返回可恢复 cursor，ACK 必须
monotonic、owner/session-scoped、有界留存并拒绝 stale/future cursor；不得修改已冻结 Event V1 hash。

状态：`Open / Design Decided`。P1-W07 决策与 aggregate gate 已完成，V2 Binder/Room ACK/SDK negotiation/
高吞吐 fault tests 由 `P6-W01/P6-W02` 实现。该问题不再阻塞 durable Session Runtime，但仍阻塞
production Event broker。`event_v2_interface_published=false`、
`session_runtime_process_death_rehydration=true`、`production_ready=false`。

P4-W02 的 HMI reducer 将 `lastEventSequence` 随 text-free checkpoint 保存，在 V1 cursor 不能前移时对重放事件做第二层
projection 去重。它不生成 cursor/ACK，也不能关闭本问题；高吞吐 broker 仍必须实施 Event V2。

## ISSUE-035 Client2 HMI checkpoint 的 production storage owner 未确定

P4-W02 为 debug Client2 process recreation 使用 app-private SharedPreferences，保存 panel、alias、SessionHandle metadata、
last sequence 和 opaque cursor，不保存 user/model/display text。该范围已通过 Android 13 ARM64 force-stop/relaunch 验证。

待确认：production APK 是否允许 backup、是否必须 Keystore-backed encryption、multi-user/seat 隔离 owner、OTA schema
migration、session token retention/erase policy 和 MDM data clear。目标 owner 未提供前，该 checkpoint 不能计入 Memory
模块、production security 或 target validation。状态：`Open`；实施跟踪 `DEV-052`、P4-W10/P8。

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
| P1-W05 进展 | SDK facade 与 Session/Event Service 真实 Binder rebind/resubscribe 完成；健康 detach 对称注销旧 callback，API 33 ARM64 连续 6 次重连通过。 |
| P1-W06 进展 | Room v4、Session/Event process-death rehydration 已完成；ISSUE-034 仅剩 Event V1 terminal cursor/ACK 演进。 |
| P2-W01 进展 | Canonical signal schema 与 API 33 ARM64 software probe 完成；ISSUE-030 的 property/service/permission/area/readback owner 仍开放。 |
| P2-W02 进展 | Capability catalog 与 API 33 ARM64 software probe 完成；全部 production authorized=false，ISSUE-029/030 仍开放。 |
| P2-W03 进展 | 进程内 Twin 与 API 33 ARM64 software probe 完成；无 provider/adapter/persistence，ISSUE-030 仍开放。 |
| P2-W04 进展 | Context/freshness/trust/restricted 与 API 33 ARM64 probe 完成；productionTrusted=false，ISSUE-029/030 仍开放。 |
| P2-W05 进展 | Scenario manifest/parser/schema/checksum/isolation 与 API 33 ARM64 probe 完成；artifact crypto、product/privacy owner、Runtime/Graph 仍开放。 |
| P2-W06 进展 | Deterministic Resolver 与 API 33 ARM64 probe 完成；product taxonomy/production trust/Service/compiler/Graph 仍开放。 |
| P2-W07 进展 | Digest-bound typed Plan compiler 与 API 33 ARM64 probe 完成；target material/production publication/Graph/Effect 仍开放。 |
| P2-W08 进展 | Debug-only simulated Effect base 与 API 33 ARM64 probe 完成；HVAC/Seat target、Runtime wiring 和真实车辆 readback 仍开放。 |
| P2-W09 进展 | Debug-only HVAC typed target/isolated Twin 与 API 33 ARM64 probe 完成；production property/Runtime/Client2 HVAC 闭环仍开放。 |
| P2-W10 进展 | Debug-only Seat typed target、安全二次校验/progress/isolated Twin 与 API 33 ARM64 probe 完成；OEM Safety/production property/Runtime/Client2 Seat 闭环仍开放。 |
| P2-W11 进展 | Debug-only Media state/digest-only synthetic Navigation observation 与 API 33 ARM64 probe 完成；真实 platform adapter/Runtime/Client2 optional Effect 闭环仍开放。 |
| P2-W12 进展 | Debug-only signature/capability controller 与 API 33 ARM64 Binder probe 完成；production Context/vehicle provider/Graph/HMI 均未接。 |
| P3-W01 进展 | Process-local Graph/Node state、FIFO/bounded sessions、deadline/partial/event projection 与 API 33 ARM64 probe 完成；executor/Room/Binder/Effect/model/hardware 均未接。 |
| P3-W02 进展 | Typed node schema/debug executor 与 API 33 ARM64 probe 完成；Graph dispatch、production executor、Effect/model/hardware 均未接。 |
| P3-W03 进展 | Registered DTO/canonical checkpoint serializer 与 API 33 ARM64 probe 完成；Graph/Room/restart recovery、Effect/model/hardware 均未接。 |
| P3-W04 进展 | Monotonic timeout、bounded attempt/backoff/jitter 与 Effect reconcile-before-retry API 33 ARM64 probe 完成；Graph/Room/production Effect/model/hardware 均未接，ISSUE-022/026 保持 Open。 |
| P3-W05 进展 | Approval binding/expiry/trusted decision/checkpoint/resume Safety revalidation 与 API 33 ARM64 probe 完成；Room/Graph/Binder grant/restart recovery/production Effect/hardware 均未接，ISSUE-022/026/029 保持 Open。 |
| P3-W06 进展 | Effect batch/dependency/resource wave/exact-profile registry/prepare-all/独立 observation 与 API 33 ARM64 probe 完成；Graph/Room/outbox/readback/reconcile/production adapter/hardware 均未接，ISSUE-022/026/030/033 保持 Open。 |
| P3-W07 进展 | 五种 typed verification、DELIVERED/APPLIED/VERIFIED 分层、UNKNOWN timed reconcile、Twin readback 与 VERIFIED no-query dedup 已完成软件/API 33 ARM64 证据；scheduler/Room/Graph/production readback/hardware 均未接，ISSUE-022/026/030/033 保持 Open。 |
| P3-W08 进展 | Explicit reversible policy、VALID before snapshot、absolute target、reverse dependency、TTL/Governance/new task/idempotent admission 已完成软件/API 33 ARM64 证据；原 VERIFIED 不变，Graph/Room/Binder/dispatch/production authority 均未接，DEV-049 与 ISSUE-022/023/026/029/030/033 保持 Open。 |
| P4-W10 进展 | 单一 scenario catalog/control state 已同步 cold/fatigue/rest、manual HVAC/Seat、Session lifecycle、Plan revision、event sequence 与设备详情；canonical mismatch 失败关闭。Runtime Plan/Graph/Effect/readback 仍未发布，ISSUE-033 保持 Open，下一子项为 P4-W11。 |
| P4-W11 进展 | 三档横屏 allowlist、1.30 fontScale、48dp、runtime accessibility semantics、最长中文与 unsupported fail-closed 已通过 Android 13 ARM64；这不提供 Runtime Plan/Effect/readback，ISSUE-033 保持 Open，下一子项为 P4-W12。 |
| P4-W12 进展 | Android 13 ARM64 recovery/fault/scenario/display 聚合、per-suite crash buffer 和最终 UI tree 已通过；自动 Plan/Effect、approval/undo/readback 与 production Client2 release 仍未完成，ISSUE-033 保持 Open。 |
| P5-W01 进展 | Tool manifest/schema、canonical digest 与 exact validator 已完成；实体 probe 因 ADB transport 不可用待复测，ISSUE-036 保持 Open。 |
| P5-W02 进展 | Registry/Resolver/dynamic health pure-Java 合同已完成；production composition/publisher/execution 未发布，ISSUE-036/037 保持 Open。 |
| P5-W03 进展 | Tool rule/model/USABLE deterministic intersection 已完成；production rule/condition/approval owner 未发布，ISSUE-038 保持 Open。 |
| P5-W04 进展 | in-process built-in executor boundary 已完成；production signer evidence、hard cancel 与 Runtime publication 未关闭，ISSUE-039 保持 Open。 |
| P5-W05 进展 | static Skill package verifier 已完成；trusted evidence、签名链、atomic policy/dynamic load 未关闭，ISSUE-040 保持 Open。 |
| P5-W06 进展 | process-local WorkingMemoryStore 已完成；Session terminal publisher、tokenizer、durable storage/privacy owner 未关闭，ISSUE-041 保持 Open。 |
| P5-W07 进展 | consent/field/scope/encryption-owner-gated ProfileMemoryStore 合同已完成；真实 identity、authority、key 与 repository 未关闭，ISSUE-042 保持 Open。 |
| P5-W08 进展 | typed summary/result-only EpisodicMemoryStore 已完成；production catalog/policy/read/erase authority、durable repository、trusted retention clock 与 model publication 未关闭，ISSUE-043 保持 Open。 |

### ISSUE-033 P4-W10 update

P4-W10 已使自然场景和手动 HVAC/Seat 共用 `ScenarioClient`、Session admission 与 typed Event sequence；四阶段和设备抽屉
不再分别解释请求状态。catalog 设备 role 不包含 Runtime PlanNode、typed target、Effect 或 readback，相关页面继续明确显示
NOT PUBLISHED/NOT DISPATCHED/UNAVAILABLE。因此 ISSUE-033 不能关闭；P4-W11 负责 accessibility/display matrix，P4-W12
负责聚合设备验收，production 执行仍受 `ISSUE-022/026/030` 阻塞。tracking：`DEV-060`。

### ISSUE-033 P4-W11 update

P4-W11 已使 Client2 在 `1280x720@107dpi`、`1920x1080@160dpi`、`2560x1440@213dpi` 以及 1.30 字体下保持
浮窗边界、48dp 触控、非空 accessibility 语义和非颜色状态。未列入 profile 会禁用入口，且显示策略不能授权 Effect。
因此显示/无障碍子项已关闭，但 ISSUE-033 仍不能关闭：P4-W12 负责 Android 设备聚合故障/恢复验收，production
Plan/Graph/Effect/readback 继续受 `ISSUE-022/026/030` 阻塞。tracking：`DEV-061`。

### ISSUE-033 P4-W12 update

P4-W12 的单一 runner 已在 Android 13 ARM64 重新执行 recovery、protected engineer fault、cold/fatigue/rest、manual
HVAC/Seat admission 和三档显示矩阵；每个子套件检查 crash buffer，最终 Activity/UI tree 可达。该结果关闭 application
acceptance 子项，不关闭演示闭环。

Plan/Effect/Media/Nav/approval/partial/mismatch/undo 在当前证据中仍是 host projection 或实体 unavailable/disabled；没有
production Client2 release artifact，`hmi_d4_demo_control_loop_complete=false`。因此 ISSUE-033 保持 Open，后续 Runtime
execution wiring 与真实车辆分别由 `ISSUE-022/026/030` 推进。tracking：`DEV-062`。

### ISSUE-033 P4-D4a update

P4-D4a 已把 P2 `ScenarioPlanCompiler` 与 P3 control-only `AgentGraphRuntime` 组合为 debug-only runner。Cold 会自动完成 Context/Policy
并停在 HVAC Effect；parked fatigue 停在 approval；moving fatigue 不会恢复被 Compiler 裁掉的 seat recline 分支；required Effect
失败会使 Graph 失败关闭。Snapshot 可为后续 HMI 展示 Plan/Graph/pending-node 调用链。

该 runner 没有注册 Android Service，也未接 Session/Event Binder、Client2、Effect adapter、approval response 或 readback；JVM supplied
outcome 不能算车辆执行。因此 ISSUE-033 仍为 Open，下一软件增量是 P4-D4b debug Runtime Session/Event projection。当前
`simulated_scenario_graph_defined=true`、`simulated_scenario_android_runtime_wired=false`、
`simulated_scenario_client2_wired=false`、`simulated_scenario_effect_dispatch_enabled=false`、
`scenario_execution_enabled=false`、`hmi_d4_demo_control_loop_complete=false`。tracking：`DEV-101`。

## ISSUE-036 Tool production owner, health source and execution authority

P5-W01 已冻结 Tool 静态合同、bounded scalar input/output schema、canonical digest 和 mandatory health freshness metadata，
并通过 JVM 与 debug/release compile。Android 13 ARM64 probe 已实现，但当前 Windows 可见 COM7/ADB interface、adb transport=0，
实体执行待复测。现有软件证据解决“Tool 如何描述和拒绝非法 payload”，不解决“谁注册、谁报告健康、谁允许执行、谁拥有副作用”。

待确认/开发项包括：build-owned 或 signed artifact owner、同 ID/version digest 冲突策略、动态 HEALTHY/UNHEALTHY/STALE
来源、capability/Safety/policy owner、模型选择与规则 allowset 交集、deadline/cancel/output/audit、process death、撤销/补偿，
以及真实车辆/NPU Tool 对应的 OEM/Vendor API、权限和 readback。

状态：`Open`。P5-W02 关闭 registered/resolved/usable 与 deterministic version resolution；P5-W03 关闭规则集合；P5-W04
只允许 signed built-in executor；P5-W05 关闭 artifact/signature/version static trust。生产车辆/NPU execution 仍由
`ISSUE-023/024/026/027/030` 阻塞。当前 `tool_registry_published=false`、`tool_execution_enabled=false`、
`production_tool_artifact_loaded=false`、`production_ready=false`、`target_hardware_validated=false`。

P5-W02 进展：pure-Java Registry 已完成 family/version 排序、duplicate digest 幂等与 conflict reject；Resolver 已完成
最高兼容版本、exact capability/digest 和 dynamic health 失败关闭。该进展不发布 production Registry 或 health source，
不接 Runtime/Graph/Executor。因此 ISSUE-036 的版本选择子项已关闭，其余 artifact/authority/execution 子项保持 Open，
更具体的 publisher/composition ownership 由 ISSUE-037 跟踪。

## ISSUE-037 Tool health publisher and production registry ownership

P5-W02 的 `ToolHealthSnapshot` 是 immutable input value，不是 health collection service。当前没有 owner 决定哪些进程可以
发布 Tool health、如何证明进程和 artifact 身份、如何处理 publisher death/restart、如何原子替换 Registry/Health snapshot、
如何防止旧 snapshot 与新 catalog 组合，以及 health revision/clock domain 是否跨进程可信。

同样没有 production Registry composition root。当前 tests/probe 直接构造两个 build-time Manifest，只验证 deterministic
合同；production Tool count 为 0。若未来从 APK/AAR/Skill artifact 加载 Manifest，必须先确定 signer allowlist、artifact
digest、version/revoke/rollback、owner capability、health check implementation 与 audit。不得把 probe count、matching digest
或 supplied HEALTHY 直接提升为生产注册或执行授权。

建议关闭顺序：P5-W03 只消费 P5-W02 USABLE 集合并与 rule allowset 求交；P5-W04 只实现 signed built-in executor 边界；
P5-W05 冻结 artifact signer/version policy；之后单独增加 production composition/publisher 工作包，并在 Runtime Binder
identity/capability、trusted elapsed clock、process death、atomic update 和 rollback 证据通过后才考虑 publication。Vehicle/NPU
health 还必须等待 P8 OEM/Vendor API 与 readback authority。

状态：`Open`。当前 `tool_registry_contract_defined=true`、`tool_resolver_contract_defined=true`、
`tool_health_dynamic_snapshot_defined=true`、`tool_registry_published=false`、`tool_resolver_published=false`、
`tool_registry_runtime_wired=false`、`tool_execution_enabled=false`、`production_tool_registered=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`。tracking：`DEV-064`。

## ISSUE-038 Production Tool rule and condition ownership

P5-W03 的 `ToolRuleSet` 与 `ConditionSnapshot` 是 immutable input value，不是 production rule service 或 trusted Context
provider。当前没有 owner 决定 rule catalog 随 APK、AAR、Skill artifact 还是受治理配置发布；也没有 signer/revoke/rollback、
catalog epoch、atomic Registry+RuleSet publication、process restart 或 historical Plan replay 合同。

ConditionSnapshot 当前只有 canonical ID 与 TRUE/FALSE/UNKNOWN，没有 source、freshness、Context digest、Safety revision 或
publisher identity。量产不能由模型/HMI/用户文本构造 `condition.vehicle.parked=true`，也不能把 UNKNOWN 当 FALSE。必须确定
Context/Policy owner、Plan/Session binding、clock/revision、process death、stale invalidation 和 digest-only audit。

requires-approval 当前只是静态 annotation，没有 ApprovalPrompt/response Service、caller identity、expiry、Plan/Context/Policy
digest 或 dispatch-time Safety revalidation。P3-W05 的 approval interrupt 合同可作为后续 binding 基础，但未接 Runtime/Room/
Graph，不能被 P5-W03 直接消费或假定为 grant。

建议关闭顺序：P5-W04 只实现 signed built-in executor boundary，并继续把 approval-required selection 拒绝为未授权；P5-W05
冻结 artifact signer/version policy；之后单独发布 build-owned RuleSet/condition composition，以 Runtime identity/capability、
Plan/Context/Policy binding、atomic epoch、restart/replay 和 audit 验证。Vehicle/NPU condition 与 Tool 仍需 P8 OEM/Vendor API。

状态：`Open`。当前 `tool_rule_set_contract_defined=true`、`tool_rule_solver_android13_arm64_verified=false`、
`tool_rule_solver_published=false`、`tool_rule_solver_runtime_wired=false`、`tool_approval_authority_available=false`、
`tool_execution_enabled=false`、`production_tool_registered=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-065`。

## ISSUE-039 Production built-in signer and cooperative cancellation ownership

P5-W04 构造器要求 caller 提供当前应用 signer digest，并与 build-owned allowlist 和 registration 的 contract/artifact digest
精确一致。该输入足够验证 pure-Java admission 语义，但当前没有 production owner 指定从 PackageManager signing history、
OEM trust store、系统 signer allowlist 或发布 manifest 中获取证据，也没有 signer rotation、revoke、downgrade/rollback 和
artifact epoch 的原子更新合同。

执行器是同步 in-process 调用。它在 admission、调用前后和 `ExecutionControl.checkpoint()` 处检查 elapsed deadline/cancel，
但无法强制终止不合作或阻塞 native/vendor 调用。生产需要 owner 选择受控独立进程、bounded worker、vendor cancellable API
或其他可证明的隔离策略，并定义超时后的资源回收、进程健康、重复调用、idempotency 和审计归属。不能用 Java
`Thread.stop`、未受控 subprocess 或动态 class loading 规避该问题。

建议关闭顺序：P5-W05 先实现只读 artifact verifier 和 signer/version policy，保持 dynamic load=false；目标平台 owner 再提供
可信 signer evidence 与 rotation/revoke/rollback 规则；P9 完成阻塞/崩溃/取消/资源耗尽故障矩阵后，单独评审 Runtime/Graph
composition。Vehicle/NPU Tool 还需 P8 vendor cancellable API 与 readback 合同。

状态：`Open`。当前 `tool_executor_contract_defined=true`、`built_in_allowlist_enforced=true`、
`built_in_signer_artifact_bound=true`、`tool_executor_runtime_wired=false`、`tool_execution_enabled=false`、
`production_tool_execution_enabled=false`、`production_tool_registered=false`、`os_virtualization_enabled=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。tracking：`DEV-066`。

P5-W05 进展：只读 verifier 已冻结 ACTIVE/RETIRED/REVOKED、artifact epoch、Runtime compatibility、防降级与 capability
allowlist，关闭 pure-Java static policy 子项。它不解决 signer evidence acquisition、签名链、atomic publish 或 hard cancel，
所以 ISSUE-039 保持 Open；可信包发布责任收敛到 ISSUE-040。

## ISSUE-040 Trusted signer evidence and atomic Skill policy publication ownership

P5-W05 的 `VerificationEvidence` 只接受 digest，不拥有 digest 的采集链。量产必须指定 signer evidence 来自 PackageManager
signing history、OEM trust store、TEE/keystore attestation 还是受签发布清单，并证明证书链、轮换、撤销、proof-of-possession、
安装来源和 artifact bytes 的测量一致性。不能把调用方提供的 64 字符串直接当成量产可信 signer。

Signer、version、capability、minimum epoch 与 rollback policy 还需要一个受治理 owner 和原子 publication 合同：policy epoch、
签名、持久化、进程死亡恢复、旧快照失效、Registry/RuleSet/Verifier 一致性、历史 Plan replay、审计和 rollback authorization。
动态 APK/AAR/JAR/dex loading 若未来获批，还必须确定 sandbox/process identity、资源限额、native code policy、class namespace、
卸载/升级和故障隔离；P5-W05 明确未实现这些能力。

状态：`Open`。当前 `skill_artifact_verifier_contract_defined=true`、`skill_signer_policy_contract_defined=true`、
`skill_version_policy_contract_defined=true`、`skill_revocation_downgrade_fail_closed=true`、
`trusted_skill_evidence_source_configured=false`、`package_signature_cryptographically_verified=false`、
`dynamic_skill_loading_enabled=false`、`skill_execution_enabled=false`、`skill_package_verifier_runtime_wired=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。
tracking：`DEV-067`。

## ISSUE-041 Working Memory session owner, tokenizer and storage publication

P5-W06 的 `WorkingMemoryStore` 接受 `fromRuntimePolicy` 请求，但尚无 production composition 指定谁从 durable Session state
发布 terminal signal、谁派生 owner fingerprint、谁选择 payload schema/privacy class，以及进程死亡后工作上下文应丢弃还是恢复。
当前 bounded terminal tombstone 只能在单进程内阻止 late write，不能替代跨进程 Session authority。

token count 也是受信输入。量产需要冻结 tokenizer/model family、version/digest、计数错误策略、byte/token 双预算和 P5-W09
ContextBudgetManager 的截断/摘要顺序；在此之前不得把 caller token count 当成真实模型预算证据。若 Working Memory 未来持久化，
还必须确定 Room/schema owner、Keystore key lifecycle、加密、backup、multi-user/seat isolation、retention、delete/export、crash
recovery 和日志/诊断脱敏。P5-W07 Profile 与 P5-W08 Episodic Memory 不能复用 Session working policy 绕过 consent。

状态：`Open`。当前 `working_memory_store_defined=true`、`working_memory_android13_arm64_verified=false`、
`working_memory_process_local=true`、`working_memory_persistence_wired=false`、`working_memory_runtime_wired=false`、
`working_memory_model_context_published=false`、`working_memory_tokenizer_verified=false`、
`working_memory_content_logged=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-068`。

## ISSUE-042 Profile Memory authority, key owner and durable repository publication

P5-W07 的 owner fingerprint、consent/authorization evidence 和 `EncryptionOwnerState` 都是受信 composition 输入；当前没有
production owner 指定 Android multi-user/driver profile 到 fingerprint 的派生、seat occupancy/account switching、consent UI、
revocation push、authority process identity、证据签名与进程死亡后的重新验证。HMI checkbox 或模型输出不能成为 consent grant。

量产 key/storage 必须冻结 Android Keystore/TEE 或 vendor secure-storage owner、AEAD algorithm/nonce/AAD、key alias/generation、
rotation/revocation、hardware-backed/attestation 要求、locked-user/direct-boot 行为、Room/file schema、transaction、migration、
backup/restore、factory reset、multi-user delete、wear/capacity 与 crash/power-loss recovery。delete 必须在 consent 撤回后仍可执行；
export 必须定义授权、格式、分页、审计与敏感字段脱敏，且不得产生未加密临时文件。

P5-W07 只完成 process-local contract-test path；debug/test XOR 不是密码学证据。状态：`Open`。当前
`profile_memory_store_defined=true`、`profile_memory_android13_arm64_verified=false`、
`profile_memory_process_local=true`、`profile_memory_durable_storage_wired=false`、
`profile_memory_production_encryption_owner_configured=false`、`profile_memory_consent_authority_production_wired=false`、
`profile_memory_runtime_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-069`。

## ISSUE-043 Episodic Memory policy, repository and erase authority publication

P5-W08 的 `ScenarioCatalogAuthority`、`StoragePolicyEvidence` 与 `EraseEvidence` 是显式失败关闭接口，但当前全部来自 contract-test
composition。量产前必须确定谁发布 build-owned catalog digest、谁把用户/seat identity 映射为 owner fingerprint、谁签发和撤销
storage policy、谁授权 owner read 与单 episode/owner erase，以及 authority 崩溃或重启时的行为。模型输出、HMI checkbox 或场景结果本身都不能
自授权长期记忆。

repository owner 还必须冻结 encrypted schema、transaction/idempotency、可信跨重启 retention clock、capacity/pressure policy、
process-death recovery、migration/backup/factory-reset、多用户清除和脱敏 audit。P5-W09 ContextBudgetManager 只能消费经过授权且仍在
retention 内的摘要；不得读取原始连续信号，也不能把 episode summary 自动扩展为用户画像。

状态：`Open`。当前 `episodic_memory_store_defined=true`、`episodic_memory_summary_result_only_verified=true`、
`episodic_memory_android13_arm64_verified=false`、`episodic_memory_process_local=true`、
`episodic_memory_read_fail_closed=true`、`episodic_memory_production_read_authority_wired=false`、
`episodic_memory_raw_continuous_signal_stored=false`、`episodic_memory_persistence_wired=false`、
`episodic_memory_production_policy_authority_wired=false`、`episodic_memory_production_erase_authority_wired=false`、
`episodic_memory_runtime_wired=false`、`episodic_memory_model_context_published=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-070`。

## ISSUE-044 Context tokenizer, summary executor and budget authority publication

P5-W09 已冻结五类 metadata、global/category token+byte 双预算、required no-partial admission 与 deterministic overflow
directive，但 production source size evidence 和执行 owner 未发布。调用方 token count 仍可能与真实模型 tokenizer 不一致；
SUMMARIZE/TRUNCATE 也只是目标，不是内容变换或质量证据。

关闭前必须确定并验证：

1. tokenizer family、version、artifact digest、模型绑定和 byte/token recount failure policy；
2. per-session/user/provider quota 与 SYSTEM/CONTEXT/PROFILE/EPISODE/HISTORY budget authority；
3. exact content identity、consent/retention/privacy source 和 decision-to-content 防替换绑定；
4. summary/truncation executor、deadline/cancel、质量阈值、post-transform recount 与 fail-closed fallback；
5. Runtime/Graph/model route composition、bounded audit、process-death/replay 和 Android 13 目标证据。

状态：`Open`。当前 `context_budget_manager_defined=true`、`context_budget_category_allocation_verified=true`、
`context_budget_dual_limit_verified=true`、`context_budget_deterministic_overflow_verified=true`、
`context_budget_required_fail_closed=true`、`context_budget_android13_arm64_verified=false`、
`context_budget_decision_only=true`、`context_budget_text_payload_accepted=false`、
`context_budget_tokenizer_wired=false`、`context_budget_summarizer_wired=false`、
`context_budget_production_authority_wired=false`、`context_budget_runtime_wired=false`、
`model_invoked=false`、`npu_accessed=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-071`。

## ISSUE-045 Memory consent authority and repository mutation publication

P5-W10 已冻结来源、purpose/retention、retained enable/disable、profile clear、evidence、replay/conflict 和 driving restriction，但
只存在 process-local projection。量产前必须确定：

1. user/profile/seat identity owner，以及跨 user switch、guest、valet、factory reset 的映射；
2. consent grant/revoke/delete/export 的签发者、调用者权限、签名/attestation、有效期、撤销推送和审计；
3. HMI 到 Runtime/Memory Service 的 versioned Binder contract、timeout/retry/idempotency 与 process-death reconciliation；
4. Profile/Episode encrypted repository 的原子 enable/disable、clear、retention、backup/migration 和可验证 deletion semantics；
5. trusted driving Context 与 Car UX policy owner，以及 MOVING/UNKNOWN 的跨进程强制门禁；
6. source status 的 privacy disclosure policy，确保未授权 HMI 不通过 count/presence 推断用户行为；
7. Android 13 目标上的身份、重启、撤销、删除、行驶限制和 audit evidence。

HMI checkbox 或模型输出不能直接授权记忆；debug allow authority 和 projection revision 不能作为 production consent 或 repository
erase evidence。状态：`Open`。当前 `memory_consent_controller_defined=true`、
`memory_consent_source_visibility_verified=true`、`memory_consent_disable_verified=true`、
`memory_consent_preference_clear_verified=true`、`memory_consent_moving_restriction_verified=true`、
`memory_consent_android13_arm64_verified=false`、`memory_consent_hmi_projection_only=true`、
`memory_consent_repository_mutation_wired=false`、`memory_consent_production_authority_wired=false`、
`memory_consent_runtime_wired=false`、`memory_consent_model_context_published=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-072`。

## ISSUE-046 Event Broker durable repository and production middleware publication

P6-W01 已冻结三个 typed topic、cursor、filter、append-before-notify、replay 和 owner-scoped subscription，但 production
publication 仍需确定：

1. durable event append、publisher sequence、cursor/ACK、retention 与 transaction repository owner；
2. process-death/reboot recovery、schema migration、corruption、disk-full、clock/sequence exhaustion 与 rollback 行为；
3. Binder/SDK identity 到 middleware identity/policy 的映射、policy publisher、撤销与 audit；
4. DDS/SOME-IP 或 vendor middleware 的选择、discovery、topic/schema compatibility、partition 和 SELinux 权限；
5. P6-W02 drop-old/coalesce/reject/disconnect、critical no-silent-drop、deadline/priority 与 consumer isolation；
6. callback Binder lifecycle、death recipient、slow/failed consumer、replay handoff 与跨 SOC 兼容；
7. Android 13 目标上的并发、重启、故障注入、资源上限、latency 和 privacy/security evidence。

当前 process-local retention 和 debug allow authority 不能替代以上 owner。状态：`Open`。当前
`event_broker_interface_defined=true`、`event_broker_typed_topics_verified=true`、
`event_broker_android13_arm64_verified=true`、`event_broker_process_local=true`、
`event_broker_durable_persistence_wired=false`、`event_broker_dds_transport_wired=false`、
`event_broker_production_published=false`、`event_broker_runtime_wired=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-073`。

### P6-W02 Backpressure/QoS progress

P6-W02 已完成 process-local per-subscription queue 合同和四种压力策略验证。`DROP_OLD` 只移除不高于 incoming
priority 的非关键事件；`COALESCE` 只替换同 digest key 的非关键事件；`REJECT` 与 `DISCONNECT` 返回显式结果；
critical Action Observation 无可用容量或到期时进入 replay-required/disconnect，不能静默 drop/coalesce。

仍未关闭 ISSUE-046：P6-W02 队列没有接 P6-W01 broker、durable event/ACK/cursor repository、Binder consumer death、
production identity/policy、DDS/SOME-IP/vendor middleware 或跨 SOC QoS。Android 13 ARM64 probe 因 ADB transport offline
尚未执行。当前 `event_qos_contract_defined=true`、`event_qos_policy_count=4`、
`event_qos_critical_no_silent_drop_verified=true`、`event_qos_deadline_priority_verified=true`、
`event_qos_consumer_isolation_verified=true`、`event_qos_android13_arm64_verified=true`、
`event_qos_process_local=true`、`event_qos_broker_wired=false`、`event_qos_durable_persistence_wired=false`、
`event_qos_production_middleware_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-073`。

### P6-W03 TriggerEngine progress (ISSUE-031)

P6-W03 已完成 fixed metric、scenario digest、threshold/window/sample-gap/min-sample/debounce/cooldown manifest 与 process-local Engine。
它只输出 source=TRIGGER 的 digest-only suggestion，auto-execution 和 Effect dispatch 固定 false；原始用户/模型文本、vendor property、
车辆 payload 和自由规则表达式不进入 API。

ISSUE-031 仍为 Open，关闭前必须确定：

1. rule manifest 的产品/隐私 owner、签名、epoch、启停、升级/回滚和 scenario catalog 一致性；
2. Vehicle/DMS/Context source 的 identity、permission、单位/范围、quality/freshness、rate 和 privacy policy；
3. user/seat/account scope、免打扰、frequency budget、durable cooldown、重启/换挡/换用户语义；
4. P6-W04 proactive consent/grant 的 scenario/capability/zone/TTL/risk binding 与撤销；
5. suggestion HMI、why evidence、dismiss/snooze、moving restriction、audit/retention 和 Android 13 fault evidence；
6. Runtime/Event publication owner，以及不得由模型、HMI 文本或 rule output 自授权 Effect 的强制边界。

上一 P6-W02 进度文档中的 P6-W03 durable append/cursor 标签已经纠正，未产生实现偏差。当前
`trigger_rule_manifest_defined=true`、`trigger_rule_manifest_verified=true`、
`trigger_threshold_window_debounce_verified=true`、`trigger_cooldown_scope_verified=true`、
`trigger_input_fail_closed_verified=true`、`trigger_suggestion_only_verified=true`、
`trigger_engine_android13_arm64_verified=true`、`trigger_engine_process_local=true`、
`trigger_cooldown_persistence_wired=false`、`trigger_source_adapter_wired=false`、
`trigger_auto_execution_enabled=false`、`trigger_runtime_wired=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-074`。

### P6-W04 proactive consent progress (ISSUE-031)

P6-W04 已冻结 exact generic grant、PARKED mutation、short-lived consent/privacy evidence、authority、TTL/revoke、replay/conflict 与
HIGH/CRITICAL hard block。它关闭了“通用 grant 必须绑定 scenario/capability/zone/TTL 且不得覆盖高风险”的软件合同缺口。

ISSUE-031 仍为 Open。production 前仍需确定 user/profile/seat identity owner、真实 consent receipt 签发与撤销、privacy disclosure、
HMI/voice 操作、durable encrypted repository、跨重启/换用户 clock、single-use HIGH/CRITICAL approval、DND/frequency budget、
Runtime/Event publication、Safety revalidation 与 Android 13 fault/rollback evidence。P6-W04 的 test authority 和 process-local state
不得用作上述 owner。

当前 `proactive_consent_policy_defined=true`、`proactive_grant_binding_verified=true`、
`proactive_high_critical_generic_grant_blocked=true`、`proactive_grant_ttl_revoke_verified=true`、
`proactive_policy_fail_closed_verified=true`、`proactive_consent_android13_arm64_verified=true`、
`proactive_policy_process_local=true`、`proactive_grant_persistence_wired=false`、
`proactive_consent_authority_wired=false`、`proactive_auto_execution_enabled=false`、
`proactive_runtime_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-075`。

### P6-W05 Context source adapters progress (ISSUE-031)

P6-W05 已完成三项 fixed descriptor、typed observation/result、Runtime health、SIMULATED vehicle、injected time 和
freshness/quality/provenance fail-closed 合同，关闭了“Trigger 输入来源没有统一 source/freshness/trust envelope”的 pure-Java 缺口。

ISSUE-031 保持 Open。仍需 production owner 明确：source registry 的签名/版本/回滚；Runtime health producer 与 fault schema；可信
clock/timezone/change handling；真实 vehicle service/property/area/rate/permission；DMS/OMS 等额外 source 的 privacy/consent；Event
publication cursor/QoS；observation-to-rule mapping；跨重启 freshness 与 Android 13 fault evidence。P6-W05 不读取目标 SDK，也不发布
Trigger input。

当前 `context_source_adapter_contract_defined=true`、`context_source_count=3`、
`context_source_allowlist_verified=true`、`context_source_runtime_health_verified=true`、
`context_source_simulated_vehicle_verified=true`、`context_source_time_verified=true`、
`context_source_freshness_quality_verified=true`、`context_source_fail_closed_verified=true`、
`context_source_android13_arm64_verified=true`、`context_source_production_registry_published=false`、
`context_source_runtime_wired=false`、`context_source_trigger_engine_wired=false`、
`vehicle_signal_provider_wired=false`、`vehicle_property_mapping_configured=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-076`。

### P6-W06 Active suggestion UX progress (ISSUE-031)

P6-W06 已完成 fixed reason/why/plan/voice-key、owner+scenario+zone merge、replay/conflict、expiry/capacity、dismiss cooldown、
PARKED-only never-ask 和 MOVING/UNKNOWN minimal-banner 合同，并提供 debug-only 半透明 HMI 显示自动化链路。

ISSUE-031 保持 Open。仍需 production owner 明确：suggestion event producer/schema/cursor、identity 与 driving-state authority、跨重启
cooldown/never-ask、voice/TTS 策略、Client2 production overlay、approval/undo/partial-failure、Graph/Effect/vehicle readback，以及目标 Android 13
故障和恢复证据。当前 HMI 不消费真实 Trigger/Event，不执行空调/座椅。

当前 `active_suggestion_controller_defined=true`、`active_suggestion_full_card_verified=true`、
`active_suggestion_merge_replay_verified=true`、`active_suggestion_moving_minimal_verified=true`、
`active_suggestion_never_ask_verified=true`、`active_suggestion_android13_arm64_verified=true`、
`active_suggestion_hmi_projection_only=true`、`active_suggestion_production_source_wired=false`、
`active_suggestion_preference_repository_wired=false`、`active_suggestion_voice_engine_wired=false`、
`trigger_engine_wired=false`、`graph_execution_enabled=false`、`effect_dispatch_enabled=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。tracking：`DEV-077`。

### P7-W05 Structured Model Output progress (ISSUE-024)

已关闭 ISSUE-024 的“缺少模型结构化输出 schema/validator”软件子项。main source 现在只接受 strict bounded
`scenarioId/parameters/summary`，并通过 ScenarioCatalog + CapabilityCatalog 验证 capability/area/type/range/step；unknown capability、
unknown/duplicate field 和不匹配 request capability 均失败关闭。AcceptedOutput 不授予 action/approval/effect authority。

ISSUE-024 保持 Open：P7-W06 evaluation corpus/metric、P7-W07 thermal/resource composition、production prompt/composer/repair policy、
Provider/Router/Runtime wiring、可信 health/policy owner、Vendor SDK/NPU Provider、cloud consent/network owner和目标性能故障证据仍未完成。

当前 `structured_model_output_verified=true`、`model_output_catalog_binding_verified=true`、
`model_output_unknown_capability_rejected=true`、`model_output_no_action_authority=true`、
`model_output_schema_runtime_wired=false`、`structured_model_output_android13_arm64_verified=true`、
`model_invoked=false`、`raw_model_content_logged=false`、`network_accessed=false`、`npu_accessed=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。tracking：`DEV-082`。

### P7-W06 Scenario Evaluation progress (ISSUE-024)

已关闭 ISSUE-024 的“缺少可复验 synthetic evaluation corpus 与 metric contract”软件子项。固定 12-case corpus 覆盖 nominal、
driving guard、stale safety、required/optional capability 缺失、prompt injection、oversize、malformed 和 unknown intent。Evaluator 对
P7-W05 validator 的 accepted/error metadata 计算 intent、unsafe、invalid、fallback、latency 与 token-cost 指标，并拒绝不完整、重复或
catalog revision 混用的报告。

ISSUE-024 保持 Open：P7-W07 resource/thermal admission composition、production prompt/composer/repair policy、真实 Provider/Router/
Runtime wiring、可信 health/policy/resource owner、Vendor SDK/NPU Provider、cloud consent/network owner、受治理评测数据和目标性能/
故障证据仍未完成。

当前 `scenario_evaluation_verified=true`、`evaluation_corpus_verified=true`、`evaluation_metrics_verified=true`、
`evaluation_boundary_verified=true`、`evaluation_case_count=12`、`scenario_evaluation_runtime_wired=false`、
`raw_evaluation_content_logged=false`、`scenario_evaluation_android13_arm64_verified=true`、`model_invoked=false`、
`network_accessed=false`、`npu_accessed=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-083`。

### P7-W07 Resource and Thermal Admission progress (ISSUE-024)

已关闭 ISSUE-024 的“缺少模型请求资源/热准入与 foreground priority composition”软件子项。main-source admission 现在要求 selected
route、request fingerprint、policy/resource snapshot digest、provider、freshness 和 workload/purpose 全部一致；ELEVATED/CONSTRAINED
按固定 budget 降级，HOT 只保留 minimal safety，UNKNOWN/CRITICAL/EXHAUSTED 在 scheduler mutation 前失败关闭。

ISSUE-024 保持 Open：真实 resource/thermal producer、production prompt/composer/repair、Provider/Router/Runtime dispatch、Vendor SDK/NPU
Provider、cloud consent/network owner、model artifact/quality/performance/fault 和目标硬件证据仍未完成。P8-W01 首先需要获得目标 capability/
service/permission 文档，不能用 caller-owned snapshot 或 deterministic stub 冒充。

当前 `model_resource_admission_verified=true`、`foreground_vehicle_priority_verified=true`、
`thermal_degradation_verified=true`、`thermal_resource_fail_closed_verified=true`、
`admission_boundary_verified=true`、`resource_admission_runtime_wired=false`、
`resource_snapshot_producer_wired=false`、`model_resource_admission_android13_arm64_verified=true`、
`provider_invoked=false`、`model_invoked=false`、`network_accessed=false`、`npu_accessed=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。tracking：`DEV-084`。

## ISSUE-047 P8 target capability discovery evidence is unavailable

P8-W01 软件准备已完成：机器可读 contract 固定八项 capability 和 14 列矩阵；只读 collector 只读取公开 feature 与 shell 可见
service/command inventory，把原始证据保存到仓库外的私有目录，并只输出非秘密 alias、计数、布尔值与 SHA-256 reference。
动态假设备测试证明 summary 不泄漏 serial、fingerprint、车型或原始 Vendor service 名。

ISSUE-047 仍为 Open / External Blocked。2026-07-18 已在 identity-redacted Android API 33 目标完成公开只读 inventory；仓库仅记录
feature/Binder/car-match/command count 69/274/2/265、隐私确认和内部引用 `internal:p8-capability-20260718`。原始设备身份与 service
清单未发布。仍未提供公开 Android Car property list、Vendor service AIDL/SDK、permission/signature policy、owner/version、
readback/fault/rollback 文档。service 可见性与 Automotive feature 不能替代这些输入，不能用于配置 property mapping、注册
production adapter 或触发 Driver/HAL。tracking：`DEV-085/110`。

解除条件：目标测试人员使用非秘密设备 alias 生成内部 evidence reference，目标 owner 审核并补齐八项 capability matrix；此后每个
capability 分别进入 P8-W02..W06，不允许全局 activation。当前：
`target_capability_discovery_contract_defined=true`、`target_capability_read_only_collector_verified=true`、
`target_capability_matrix_complete=false`、`public_car_property_list_available=false`、
`vendor_service_contract_available=false`、`permission_signature_policy_available=false`、
`target_capability_discovery_external_blocked=true`、`vehicle_property_mapping_configured=false`、
`production_adapter_registered=false`、`vendor_npu_provider_available=false`、`driver_development_triggered=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。
tracking：`DEV-085`、`ISSUE-024/027/030`。

## ISSUE-048 P9 target performance evidence is unavailable

P9-W01 软件合同已完成：JSON/Java 固定七类十项 initial budget；三种 evidence mode 分离 host contract、Android application 和 target；
aggregate report 对 missing/unit/sample/threshold/duplicate 失败关闭并生成稳定 digest。debug probe 只验证合成阈值，没有读取真实性能。

ISSUE-048 保持 Open。当前 ADB transport 为 offline，没有目标 Runtime APK probe、Perfetto/系统统计采集、30-sample 十项结果、受控原始证据、
目标 release/source/non-secret alias 绑定或 owner approval。Plan/Effect 当前也未 production-wired，真实车辆 apply/readback 与 Vendor NPU
耗时不在本预算测量中，不能被应用层 admission 指标替代。

解除条件：冻结测量 workload、warm/cold 状态、clock、采样窗口、P95/MAX 计算、DB/WAL/SHM 与 PSS/CPU 口径；在目标 Android 13 ARM64
上绑定命名 release/source/evidence 执行至少 30 样本并由 owner 评审。P9-W02 还需把 72h stability/fault 的资源趋势纳入独立验收。
当前 `performance_budget_contract_defined=true`、`performance_budget_target_owner_approved=false`、
`performance_budget_target_measurement_complete=false`、`performance_budget_android13_arm64_verified=false`、
`performance_budget_runtime_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-086`。

## ISSUE-049 P9 target 72h stability evidence is unavailable

P9-W02 软件合同已完成：JSON/Java 固定 cold/fatigue/rest 三 workload、baseline/adapter death/Runtime restart/storage pressure/
callback churn/network loss 六 fault 和 18-case matrix；报告对缺项、重复、样本/时长不足、unexpected crash、ANR、invariant、
iteration/outcome/recovery 失败关闭。debug probe 只验证合成 CONTRACT_TEST 记录。

ISSUE-049 保持 Open / External Blocked。当前 ADB offline，且真实 Runtime 场景执行、production adapter、fault injector、72h scheduler、
crash/ANR/resource observation owner、仓库外 evidence 和 owner approval 均未发布。P8 Adapter/NPU 阻塞也不能由本矩阵关闭。

解除条件：冻结命名 release/source/archive、非秘密 alias、场景循环频率、fault 触发/恢复方法、资源趋势、停止条件和 evidence policy；
在目标 Android 13 ARM64 上连续执行至少 259,200,000 ms，18 case 每项至少 30 次并由 owner 评审。当前：
`stability_fault_matrix_contract_defined=true`、`stability_target_72h_complete=false`、
`stability_target_owner_approved=false`、`stability_android13_arm64_verified=false`、
`stability_fault_injection_runtime_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-087`。

## ISSUE-050 P9 complete security fuzz evidence is unavailable

P9-W03a 已完成三个 Java parser/validator boundary 的固定 18-case host regression。P9-W03b 又完成 CallerPolicy、SessionReplay、
SignerPolicy 三 surface / 18-case host policy regression，覆盖 package/current signer/capability/shared UID、stable owner replay/isolation 和
signer rotation state/revoke/epoch。P9-W03c 已冻结 37 项 public AIDL/Parcel inventory、八 validation family 和 model/path/oversize
aggregate，并交付 debug-only Android probe。P9-W03d 已在 API 33 ARM64 的不同 UID 进程间验证真实 `Binder.getCallingUid()`、package resolution
和 installed debug APK current signer SHA-256，并用 Runtime UID/package/伪 digest 覆盖 spoof 负例。P9-W03e 又在 API 33 ARM64 上以两个
不同 owner UID 通过真实 task Binder 验证 active/terminal callback replay、idempotency conflict 静默和跨 owner callback isolation；SDK
现拒绝 cross-task/malformed callback，并丢弃 duplicate/stale sequence。P9-W03f 固定 Jazzer 0.30.0、20 秒默认预算和 6 个 synthetic seed，
对 checkpoint/scenario/tool 三类 production Java parser 完成受控 host campaign，三类入口均执行且未产生 crash artifact。完整安全验收仍缺
目标级 Android Binder/Parcel/长预算证据、production signer/release 资格与安全 owner approval。

ISSUE 状态改为 `Suspended`。W03f 可执行实现已由 P9-W03g 完整撤回，仓库只保留外部证据 submission interface；项目不申请 trusted-access
permission。未来 evidence 必须由外部 security owner 提供批准 profile、release/source/digest、non-secret alias、内部 reference 和 privacy
confirmation。不得上传 raw user/model/vehicle payload、设备身份、签名材料、credential、raw input 或未审日志。

关闭条件：所有 W03 surface 有稳定 case/owner/expected result，受控 fuzz 达到批准预算且 crash/hang 已归零或有接受记录，目标 Android
13 release 完成命名 device evidence 并经安全 owner 评审。当前 `security_parser_corpus_defined=true`、
`security_parser_fail_closed_regression_verified=true`、`security_coverage_guided_fuzz_complete=false`、
`security_identity_replay_corpus_defined=true`、`security_caller_policy_host_verified=true`、
`security_session_replay_owner_policy_host_verified=true`、`security_signer_policy_host_verified=true`、
`security_aidl_parcel_inventory_complete=true`、`security_host_path_oversize_aggregate_verified=true`、
`security_android_debug_probe_available=true`、`security_android_debug_probe_executed=true`、
`security_boundary_probe_android13_arm64_verified=true`、`security_identity_device_probe_verified=true`、
`security_distinct_app_uids_verified=true`、
`security_binder_calling_uid_spoof_android_verified=true`、
`security_package_signature_cryptographically_verified=true`、`security_same_signer_debug_binding_verified=true`、
`security_task_callback_replay_android_verified=true`、`security_callback_sequence_replay_suppressed=true`、
`security_callback_terminal_replay_unique=true`、`security_idempotency_conflict_callback_silent=true`、
`security_cross_uid_callback_owner_isolation_verified=true`、`security_debug_test_principal_release_excluded=true`、
`security_external_evidence_interface_defined=true`、`security_requirement_suspended=true`、
`security_test_implementation_present=false`、`security_test_execution_enabled=false`、
`security_external_evidence_admitted=false`、`security_coverage_guided_fuzz_complete=false`、
`security_production_signer_verified=false`、
`security_android13_arm64_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-088/089/090/111/112/113/114`。

## ISSUE-051 P9 durable privacy lifecycle policies are incomplete

状态：`Open / Policy Owner Input Required`。

W04a 已盘点 12 个数据面并验证现有源码边界。Working/Profile/Episodic Memory、Event Broker、Tool audit 和 model transient path
已有有界 contract；Room 的 Session/Plan/Approval/Cursor 也有技术性 cascade/expiry/capacity 行为。但 durable Effect recovery 与
durable Audit 缺少 owner-approved retention ceiling、delete/erase authority、legal/safety hold 和 lifecycle evidence。

W04b 在没有产品隐私 owner、功能安全 owner 和合规 owner 一致批准前不得猜测期限。实现必须防止删除 active/pending Effect、未完成
compensation 或安全审计依赖，同时禁止 raw user/model/vehicle/location payload 和未授权 export。关闭 ISSUE 需要命名 policy version/
digest、每 surface 规则、JVM/Android evidence、迁移/回滚行为和 owner approval reference。

W04b 已关闭“缺少机器可判定准入与 active/hold guard”的软件子项：draft policy 绑定 12 surface 和 inventory digest；三 owner evidence、
ceiling、Effect/compensation 与 legal/safety hold、Profile export consent/authorization 均 fail closed。当前 draft 因两个 ceiling 和全部 owner
evidence 缺失而拒绝激活，ISSUE 保持 Open。后续仍需真实 owner 输入、repository enforcement、迁移/回滚和 W04c Android evidence。

W04c 已关闭“缺少脱敏 debug projection 与 release-absence/installer 门禁”的软件子项。21 个固定 key 不携带内容、surface/source、
owner/auth/consent reference 或设备身份；但当前没有 ADB transport，probe 未执行。真实 owner policy、repository enforcement、迁移/回滚、
Android probe evidence 和合规审计持久化仍未完成，ISSUE 保持 Open。

当前 `privacy_data_inventory_complete=true`、`privacy_policy_gap_count=2`、
`privacy_authorized_export_surface_count=1`、`privacy_owner_policy_approved=false`、
`privacy_policy_admission_defined=true`、`privacy_current_policy_admitted=false`、
`privacy_repository_mutation_wired=false`、
`privacy_redacted_audit_projection_defined=true`、`privacy_android_debug_probe_available=true`、
`privacy_android_debug_probe_executed=false`、
`privacy_production_lifecycle_complete=false`、`privacy_runtime_lifecycle_wiring_complete=false`、
`privacy_android13_arm64_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W04`。tracking：`DEV-091/092/093`。
## ISSUE-052 P9 production signer and rollback owner evidence is unavailable

状态：`Open / External Blocked`。

W05a 已交付机器可判定的发布集准入合同，但当前仓库只有 debug signer 软件包。没有 production signer owner/rotation policy、正式
release sequence、OTA/MDM owner、目标普通包/系统包安装策略、rollback decision owner、目标数据库兼容 evidence 或受控 rehearsal。
不得上传私钥、keystore、certificate bytes、设备 serial/fingerprint、未审日志、内部路径或原始业务 payload。

W05b 已交付固定三包的 debug-only installed/version/signer-relation metadata probe 和 no-install ADB dry-run adapter。该探针不读取或输出
signer/certificate bytes、包路径或设备身份；当前 transport offline，未执行。即使未来三包 observation 全匹配，也只证明当前 debug
installed set 的关系，不提供正式 candidate、owner approval、OTA/MDM 或 rollback authority，因此本 ISSUE 保持 Open。

解除条件：

1. 以 digest reference 提供命名 production signer/release/rollback owner approval；
2. 绑定正式 source commit、archive SHA-256、三 APK SHA-256/versionCode/package/signer measurement；
3. 对所有 schema transition 提供 Room migration fixture 与 rollback readable-range evidence；
4. 通过受控 OTA/MDM 或批准 installer 完成 same-signer upgrade；
5. 在非生产数据目标上完成 rollback rehearsal，证明旧 Runtime 可读现存 DB 且没有 destructive downgrade；
6. 由 release、安全与数据 owner 评审仓库外 evidence，并发布命名 replacement release。

当前 `production_release_admission_defined=true`、`production_signer_owner_approved=false`、
`production_release_candidate_admitted=false`、`release_installer_wired=false`、
`release_rollback_executor_wired=false`、`release_android13_arm64_verified=false`、`hardware_accessed=false`、
`release_metadata_projection_defined=true`、`release_installer_dry_run_adapter_defined=true`、
`release_android_debug_probe_available=true`、`release_android_debug_probe_executed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W05`。tracking：`DEV-094/095`。

## ISSUE-053 P9 target field diagnostics and retest evidence is unavailable

状态：`Open / External Blocked`。

P9-W07a 已交付 metadata-only release evidence envelope：严格绑定 release/source/archive/release-set、非秘密 alias/reference、八类有序
diagnostic fact 和稳定 report digest，并对 GitHub privacy、raw identity、automatic upload 失败关闭。它没有 Android probe、ADB execution、
GitHub issue mutation、replacement release 或 target-owner admission；Host 报告永远不是 target evidence。

P9-W07b 已交付 31-key projection、DUMP debug Activity 和 no-install adapter。它执行五类 bounded check，另外三类明确 NOT_RUN；当前
ADB offline，probe 未执行。即使未来五类 PASS，也因 category 不完整而不能 admit target report，不能自动关闭 issue。

P9-W07c 已交付 5-state/5-transition pure-Java workflow、严格递增且 artifact identity 不复用的 replacement release，以及 target/release/
diagnostics/tester 四方摘要 admission。完整 PASS fixture 可返回 close eligibility，完整非 PASS 返回同一 Issue 的 fix-ready；但代码不调用
GitHub、不发布 release、不安装 APK、不自动关闭 issue，repository 也没有真实 owner/target evidence。

仓库软件已经具备 debug diagnostics 入口、只读 adapter 和 release/retest admission 状态机。仍缺的是外部事实：命名并实际发布的 replacement
release、完整八类 target report、目标 tester 与 target/release/diagnostics owner approval。不得提交 serial/fingerprint、signing material、
target-input files、内部路径、raw/未审日志、用户/模型文本、memory/token 或车辆 payload，也不得自动关闭 issue。

解除条件：八类 target fact 由命名 release 和非秘密 alias 产生，report digest 与仓库外受控 evidence reference 一致，owner 审核 privacy 和
diagnostic completeness，tester 对命名 replacement release 复测并记录结果。production signer/installer/rollback authority 还必须独立关闭
ISSUE-052。当前 `release_evidence_envelope_defined=true`、`release_evidence_target_owner_approved=false`、
`release_evidence_target_report_admitted=false`、`release_evidence_runtime_diagnostics_wired=false`、
`release_evidence_retest_workflow_wired=false`、`release_retest_state_machine_defined=true`、
`release_retest_replacement_release_published=false`、`release_retest_github_issue_mutation_wired=false`、
`release_retest_automatic_issue_close_allowed=false`、`release_evidence_android13_arm64_verified=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W07`。
tracking：`DEV-098/099/100`。

### ISSUE-033 P4-D4b update

P4-D4b 已把 D4a Graph 接入 debug-only `SimulatedScenarioRuntime`。Session projection 可表达 Plan identity、Graph/session state、pending
approval/effect/readback 与 terminal state；Event projection 通过既有 `BoundedEventRuntime` 发布两类 topic 和八类 digest-only schema。

该增量仍没有 Android Service/Binder、Client2、adapter apply、readback 或 approval response。ISSUE-033 保持 Open，下一软件增量是
P4-D4c signature-protected debug Binder Service；后续仍需 adapter/readback 与 Client2 展示链路。

当前 `simulated_scenario_debug_runtime_wired=true`、`simulated_scenario_android_service_published=false`、
`simulated_scenario_session_event_binder_published=false`、`simulated_scenario_client2_wired=false`、
`simulated_scenario_effect_dispatch_enabled=false`、`scenario_execution_enabled=false`、
`hmi_d4_demo_control_loop_complete=false`、`hardware_accessed=false`。tracking：`DEV-101/102`。

### ISSUE-033 P4-D4c update

P4-D4c 已提供固定场景 debug Binder。Client2 未来可使用 Cold/Fatigue、Parked/Moving enum 启动 session，并读取 Plan/Graph/pending/event
metadata；Service 具有 signature permission 与 capability 双门禁，release source absent。

Android 13 Debug APK 已安装，Service/action/permission 可见，ADB shell 未授权调用被系统拒绝；同签名 AIDL 正向调用未验证。

ISSUE-033 仍 Open：Binder 尚未调用 simulated adapters/readback，Client2 尚未绑定，approval 仍无 authority。下一软件增量为 P4-D4d
debug adapter/readback composition。当前 `simulated_scenario_client2_wired=false`、
`simulated_scenario_binder_android13_install_verified=true`、
`simulated_scenario_binder_unauthorized_access_denied_verified=true`、
`simulated_scenario_binder_authorized_call_verified=false`、
`simulated_scenario_effect_dispatch_enabled=false`、`simulated_scenario_readback_accessed=false`、
`hmi_d4_demo_control_loop_complete=false`。tracking：`DEV-101/102/103`。

### ISSUE-033 P4-D4d update

P4-D4d 已将固定场景 pending Effect/verify 节点组合到四个 debug simulated adapters，并保持 approval 显式挂起、Moving seat branch pruning、
required failure fail-closed。Binder v2 现可提供自动 dispatch/readback/approval/failure counts 和 Partial/Stuck 状态。
实体 Android 13 同签名 probe 已验证 protocol v2、2 个场景、8 dispatch、6 matched readback、1 approval input、0 failure；
这只关闭 D4d debug Binder/composition 可执行性证据缺口。

ISSUE-033 仍 Open：Client2 尚未绑定 v2 Binder，HMI 尚未展示 Context -> Plan -> Policy -> Effect -> readback 链路；真实 Vehicle adapter、
owner approval 与 production authority 也未提供。下一软件增量为 P4-D4e Client2 scenario chain UI wiring。

当前 `simulated_scenario_effect_dispatch_enabled=true`、`simulated_scenario_readback_accessed=true`、
`simulated_scenario_hardware_effect_dispatch_enabled=false`、`simulated_scenario_client2_wired=false`、
`simulated_scenario_android_debug_probe_executed=true`、`simulated_scenario_binder_authorized_call_verified=true`、
`hmi_d4_demo_control_loop_complete=false`。tracking：`DEV-101/102/103/104`。

### ISSUE-033 P4-D4e update

P4-D4e 已关闭 ISSUE-033 中的 Client2 debug 展示子项：Client2 绑定 Binder v2，reducer 显示 Context -> Plan -> Policy -> Graph ->
Effect -> Readback，并允许 parked Fatigue 的显式模拟批准/拒绝。实体 Android 13 ARM64 已验证 Cold 3/3、批准 5/3、拒绝 4/2 Partial，
Parcelable wire order 与 approval node binding 已加入持续门禁。

ISSUE-033 仍 Open，因为闭环仅使用 debug process-local simulation。真实 Vehicle adapter、production approval/Effect runtime、OEM Context、
正式 Client2 release、retry/undo 的真实执行语义、P8 capability evidence 和 P9 owner/target qualification 尚未提供。

当前 `simulated_scenario_client2_wired=true`、`simulated_scenario_client_parcel_wire_verified=true`、
`simulated_scenario_android13_arm64_client_verified=true`、`hmi_d4_debug_demo_control_loop_complete=true`、
`simulated_scenario_hardware_effect_dispatch_enabled=false`、`simulated_scenario_approval_authority_available=false`、
`scenario_execution_enabled=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`。tracking：`DEV-101/102/103/104/105`。

### P5 Android 13 ARM64 probe acceptance update

P5-W01..W10 的十个 debug probe 已在 Android 13 ARM64 上由统一 installer 执行通过：Tool manifest、Registry/Resolver、RuleSolver、
Executor、Skill verifier、Working/Profile/Episodic Memory、ContextBudget 和 Memory consent。完整 Runtime/Demo 安装回归也已通过，
设备身份输出已脱敏。此前 ADB transport offline 只保留为历史记录，不再是这些应用层 probe 的阻塞项。

ISSUE-036..045 仍保持 Open：probe fixture 不提供 production signer/health/rule/consent authority，不发布 Runtime，不提供 durable encrypted
repository，不调用模型/NPU/车辆或 Driver/HAL。当前 `p5_android13_arm64_probe_acceptance_complete=true`、
`p5_probe_module_count=10`、`production_tool_authority_published=false`、`production_memory_authority_published=false`、
`production_runtime_wired=false`、`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`。
tracking：`DEV-106`、`ISSUE-036..045`。

### P6 Android 13 ARM64 probe acceptance update

P6-W01..W06 的六个 debug probe 已在 Android API 33 / ARM64 上统一通过：EventBroker、Backpressure/QoS、TriggerEngine、
Proactive consent、Context source adapters 与 Active suggestion UX。完整 Runtime/Demo 安装回归通过，证据输出已脱敏。

`ISSUE-046` 仍 Open：EventBroker retention/cursor 和 QoS queue 仅进程内，未接 durable repository、生产 Broker callback、
Binder/DDS/SOME-IP。`ISSUE-031` 仍 Open：Trigger/Consent/Context/Suggestion 没有 production source、authority、preference store、
Runtime/Graph/Effect 或真实车辆接线。实机 probe 只关闭 Android ABI/API 可执行性子项，不关闭上述生产职责。

当前 `p6_android13_arm64_probe_acceptance_complete=true`、`p6_probe_module_count=6`、
`production_event_middleware_published=false`、`production_trigger_runtime_wired=false`、
`production_proactive_authority_published=false`、`production_context_source_registry_published=false`、
`production_active_suggestion_source_wired=false`、`production_runtime_wired=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`。tracking：`DEV-107`、`ISSUE-031/046`。

### P7 Android 13 ARM64 probe acceptance update

P7-W01..W07 的七个 debug probe 已在 Android API 33 / ARM64 上统一通过：Model contract v2、Provider registry、Policy router、
LocalModelProvider、Structured output、Scenario evaluation 与 Resource admission。完整 Runtime/Demo 安装回归通过，证据输出已脱敏。

`ISSUE-024` 仍 Open：没有 production model/provider owner、真实模型调用、Runtime composition、NPU/network endpoint、目标性能与正式
评测。`ISSUE-044` 仍 Open：真实 tokenizer/context budget 与 model-context composition 未接。实机 probe 只关闭 Android ABI/API
可执行性子项，不能用 12-case synthetic 结果替代模型质量或硬件验收。

当前 `p7_android13_arm64_probe_acceptance_complete=true`、`p7_probe_module_count=7`、
`production_model_provider_published=false`、`production_model_router_wired=false`、`production_inference_enabled=false`、
`production_model_output_runtime_wired=false`、`production_evaluation_authority_published=false`、
`production_resource_snapshot_provider_wired=false`、`provider_invoked=false`、`model_invoked=false`、
`network_accessed=false`、`npu_accessed=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`。tracking：`DEV-108`、`ISSUE-024/044`。

### P9 Android 13 ARM64 debug probe acceptance update

P9 七个 debug application probes 已在 API 33 ARM64 上统一通过，关闭 `ISSUE-048..053` 中“应用探针未执行”的子项：synthetic
performance budget、synthetic stability matrix、security boundary inventory、privacy redaction、production release metadata、
driver-safety contract projection 和 field diagnostics projection。完整 Runtime/Demo 安装回归同时通过，输出仅保留脱敏 count/boolean。

Issues 仍保持 Open：`ISSUE-048` 缺 target performance samples/owner；`ISSUE-049` 缺 72h workload/fault injection；`ISSUE-050`
缺 coverage fuzz、Binder UID spoof 和 signer crypto review；`ISSUE-051` 缺 privacy owner policy/repository lifecycle；`ISSUE-052/053`
缺 production signer/installer/rollback、八类 target evidence、replacement release 与 tester admission；`ISSUE-029/030` 缺 OEM Safety State、
车辆能力、readback 和硬联锁。

当前 `p9_android13_arm64_probe_acceptance_complete=true`、`p9_probe_module_count=7`、
`performance_budget_target_measurement_complete=false`、`stability_target_72h_complete=false`、
`security_coverage_guided_fuzz_complete=false`、`privacy_owner_policy_approved=false`、
`production_signer_owner_approved=false`、`driver_safety_android13_arm64_verified=false`、
`field_diagnostics_target_category_execution_complete=false`、`release_evidence_target_report_admitted=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`。tracking：`DEV-109`。
