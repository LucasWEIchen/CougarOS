# 中央大脑架构偏差登记表

版本：0.7
日期：2026-07-17
状态：Android 13 实际工程基线

## 使用规则

本文件记录实现与用户架构图需求基线之间的全部已知偏差。架构图是需求，不是示意图。
每项偏差必须包含 Req ID、原因、影响、处理和状态；没有目标证据时不得通过代码默认值把偏差
标记为已关闭。

状态定义：

- `Open`：偏差存在且没有获批的临时边界。
- `Accepted Temporary`：偏差已显式接受，并有替换或解除条件。
- `Accepted Scope`：用户已明确修改交付范围，但原架构差异仍保留可追溯记录。
- `Resolved`：软件范围内已修正；不自动代表硬件或量产验收通过。
- `Retired`：只属于 2026-07-16 已删除的 Python 原型，不再维护。

全局状态始终保持：

`production_ready=false`、`target_hardware_validated=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。

## 偏差索引

| ID | 当前结论 | 关联 Req ID/跟踪项 | 状态 |
| --- | --- | --- | --- |
| DEV-001 | Python REST/HTTP gateway、旧 Binder/IPC/gRPC 转发层已删除；Android typed Binder 是唯一应用协议主线。 | XSC-006, DEV-026 | Retired |
| DEV-002 | Python 单进程 gateway/NPU/vehicle-state 聚合实现已删除。 | NV-F-008/011, DEV-026 | Retired |
| DEV-003 | Python Agent/Skill/Memory contract mock 已删除；Android Runtime 各模块继续独立演进。 | APP-004, NV-F-001, ISSUE-025 | Retired |
| DEV-004 | 真实车辆信号尚未接入，Android production adapter 必须失败关闭。 | NV-F-003..005, ISSUE-030 | Accepted Temporary |
| DEV-005 | 外置 PCIe NPU 仍只有 ModelProvider/C ABI/Driver-HAL 合同和空 provider。 | HW-002, NV-F-011, ISSUE-024 | Accepted Temporary |
| DEV-006 | Python governance daemon 已删除；Android Binder identity、capability、Room audit 是现行实现。 | NV-G-001..007, DEV-019 | Retired |
| DEV-007 | Python Event subscription closure 链已删除；Android Event Runtime 只保留有界软件实现。 | FW-U-003, ISSUE-025 | Retired |
| DEV-008 | 本项目不实现 Hypervisor；只维护 Safety/跨域接口约束。 | HV-001..003 | Accepted Scope |
| DEV-009 | 咔咔虾仅作为公开产品参考，不形成第三方兼容声明。 | APP-004, ISSUE-020 | Accepted Temporary |
| DEV-010 | 架构图优先级已明确高于产品参考。 | 全部 | Resolved |
| DEV-011 | 早期 Linux Python CLI/daemon/package 样例已删除；当前阶段不交付 Linux 前端。 | DEL-002/004, DEV-026 | Retired |
| DEV-012 | 黄色小太阳跨 SoC 组件仍采用平台无关合同，但当前仅实现 Android 版本。 | XSC-001..006, DEV-026 | Accepted Scope |
| DEV-013 | 旧 JSON Protocol Binding 样例已删除；Android app-local typed AIDL 受 DEV-018 约束。 | XSC-006, DEV-018 | Retired |
| DEV-014 | 真实 VHAL/NPU/vendor adapter 尚未激活，现行 production registry 必须返回 unavailable。 | XSC-004, ISSUE-024/030 | Accepted Temporary |
| DEV-015 | 动态扩展 loader/schema registry 尚未实现。 | FW-U-008, ISSUE-025 | Accepted Temporary |
| DEV-016 | Python hardware registry 已删除；真实硬件缺口改由 Android empty provider、NPU C ABI 和 Driver/HAL 文档管理。 | KH-003/006, ISSUE-024/030 | Retired |
| DEV-017 | Client2 是闭源 APK patch 演示路径，不是量产 HMI。 | APP-004, XSC-001/006, DEL-001/004 | Accepted Temporary |
| DEV-018 | Gradle app-local AIDL 不是 AOSP/VINTF stable AIDL。 | XSC-006, NV-P-002, ISSUE-021 | Accepted Temporary |
| DEV-019 | 软件治理已实现，但 target VHAL/Safety/approval/model/effect authority 尚未接线。 | NV-G-005..007, ISSUE-022..025 | Accepted Temporary |
| DEV-020 | Android 实际工程引入 Native C Runtime，与早期纯 Java 交付形状不同。 | XSC-004, NV-F-001/011 | Resolved |
| DEV-021 | 目标内网测试通过 GitHub Release/Issue 闭环，维护者不能直接控制内网 ADB。 | DEL-001/003/004/005 | Accepted Temporary |
| DEV-022 | WSL 调 Windows ADB 的 CRLF/工具继承问题已修复。 | DEL-001/003/004 | Resolved |
| DEV-023 | Runtime process recovery 后 Demo HMI 未复验的问题已修复。 | APP-004, XSC-006 | Resolved |
| DEV-024 | Stage 2 在真实车辆 API 不可用时仅允许 Android debug/test Digital Twin，不允许 production fallback。 | S2-TWN-001, S2-ADP-001/002 | Accepted Temporary |
| DEV-025 | Client2 patched APK 是演示 HMI，不是量产 AAOS 产品 HMI。 | S2-UX-001..003, DEL-001/004 | Accepted Temporary |
| DEV-026 | Python 原型与 Linux Python 交付已退役；当前用户批准只维护 Android 13 Java/AIDL/C 工程。 | XSC-001..006, DEL-001..005 | Accepted Scope |

## DEV-017 Client2 APK 逆向演示路径

原因：目标 HMI 源码不可用，用户批准在隔离工程
`apk-labs/client2-central-brain/` 中进行资源、smali 和 secondary-dex patch。

现状：Client2 已通过 public SDK/typed Binder 提交任务，APK 不申请网络权限，不保留 HTTP
fallback。2026-07-15 导航菜单进展已经在 1920x1080 Android 13 ARM64 物理设备验证默认隐藏、
显示、二次隐藏、面板外关闭、Binder callback 和进程恢复。

风险：底部导航触点依赖闭源 Tuanjie/RenderService 画面几何；debug 重签名、量产 allowlist、
OTA/MDM、Car UX Restrictions、无障碍和多分辨率均未完成。

解除条件：取得可维护 HMI 源码或厂商稳定导航事件，完成 production signer、显示矩阵、驾驶分心
和升级回滚验收。状态：`Accepted Temporary`。

## DEV-018 Android AIDL 业务面与诊断面边界

R2 已拆分 production、governance、diagnostics typed AIDL，提供 Parcelable、oneway callback、
cancel、version/hash 和 Binder death。R2C 已通过 API 33 service/client death、重连和竞态验证。

2026-07-17 P1-W01 新增独立 Session app-layer AIDL V1，并单独冻结 interface hash 与 source checksum；
既有三套 V1 checksum 未改变。该接口当前仅为 `contract_defined`，没有 Service publication 或 VINTF
声明。原 backlog 的 reconnect 测试已移到拥有 Binder 连接生命周期的 P1-W05，避免 DTO 层伪证据。

同日 P1-W02 新增独立 Plan/Node app-layer structured AIDL V1、DAG validator 与
`plan-v1.sha256`；task/diagnostic/governance/session checksum 均未改变。Plan 合同同样只达到
`contract_defined`，没有 Binder publication、Compiler、Graph Runtime 或 VINTF 声明。API 33 ARM64
Parcel 证据不能继承为 target platform stable-AIDL 或 Graph 执行证据。

同日 P1-W03 新增独立 Event/callback app-layer AIDL V1、五个 structured DTO、cursor replay/parent/
redaction/immutability validator 与 `events-v1.sha256`；此前全部 V1 checksum 均未改变。Event 合同
同样只达到 `contract_defined`，`event_runtime_service_published=false`、
`event_callback_service_published=false`，没有 Room publication 或 VINTF 声明。独立 Event surface
避免改动已冻结 Session transaction order；callback 仅通知，cursor replay 才是权威恢复路径。API 33
ARM64 Parcel 证据不能继承为 Event broker、target stable-AIDL、车辆/NPU 或 production 证据。

同日 P1-W04 新增四个 Effect/Approval app-layer structured parcelable、完整 Effect transition validator、
approval/undo digest/version/TTL 绑定和 `effect-v1.sha256`；此前全部 V1 checksum 均未改变。该合同没有
新增 Binder interface，`effect_runtime_service_published=false`、
`approval_response_service_published=false`、`undo_service_published=false`。Android 13 ARM64 Parcel
证据只证明 DTO wire 和纯校验逻辑，不能继承为 Effect Service、approval authority、undo executor、
车辆 adapter、target stable-AIDL 或 production 证据。

偏差仍存在：黑盒厂商系统不能用 Soong `aidl_interface` 注册 VINTF stable AIDL，当前接口是
Gradle 应用层 Binder 合同。目标 system/privileged placement、SELinux 和稳定性 owner 未确定。
状态：`Accepted Temporary`，对应 `ISSUE-021`。

## DEV-019 目标 authority 与生产适配器未接线

Android Runtime 已使用 Binder UID/package/current signer、default-deny capability、Room durable
repository、governance middleware 和 activation gate；不再读取请求体自报权限，也不依赖 Python
状态。

偏差仍存在：Safety/Vehicle State、审批 authority、加密材料、production model provider、真实
effect adapter 和 audit exporter 均缺目标 owner/evidence。Production 无适配器时必须返回
`CB_ERR_ADAPTER_UNAVAILABLE`，不得静默回退到 test double。状态：`Accepted Temporary`。

P1-W04 只冻结 approval prompt 与 undo eligibility 的数据绑定，不提供 approval response/grant 或
compensation execution，也不把现有 process-local/durable approval repository 提升为 OEM authority。
这些生产 authority 与 adapter 缺口继续由本偏差和 `ISSUE-022/023/030` 跟踪。

## DEV-020 黑盒实际工程引入 Native C 运行时

C ABI V1、JNI、双 ABI AAR、ELF allowlist、RELRO/NOW、ASan/UBSan host test、API 33 load/recovery
和 hybrid delivery 已完成。Native 层不读取 Binder 身份，不访问私有 device node，不直接激活
Vendor NPU/VHAL。2026-07-12 B1 进展、2026-07-12 B2 进展、2026-07-12 B3 进展和
2026-07-12 B4 结果均已由对应检查器覆盖。

状态：`Resolved`（软件交付形状）；硬件激活仍由 `ISSUE-024/027/030` 跟踪。

## DEV-021 目标内网远程测试闭环

测试人员从 private GitHub Release 获取不可变包，在内网执行 ADB 验收，只提交脱敏结构化 Issue。
GitHub 不接收序列号、fingerprint、原始日志、车辆 payload、用户/模型文本、密钥或签名材料。
connector 不可见 private repository 时允许使用已认证 `gh` CLI。Branch protection 当前受套餐
限制，Actions 与 pre-push hook 不等价于强制保护。状态：`Accepted Temporary`。

## DEV-022 WSL/Windows ADB 输出兼容

设备枚举和 `get-state` 已统一剔除 CRLF，嵌套工具继承调用方 `ADB`。物理 Android 13 测试
通过。状态：`Resolved`。

## DEV-023 Runtime 恢复后的 HMI 重连

Demo HMI 已实现有界显式重连，B3 在 force-stop/recovery 后重新读取真实 UI tree，不再复用恢复前
结果。物理 Android 13 测试通过。状态：`Resolved`。

## DEV-024 Stage 2 车辆多设备动作先使用 Digital Twin 仿真

在车辆 property/service contract 未提供前，Stage 2 只允许在 Android `debug`/`test` source
set 使用 `source=SIMULATED`、`productionAuthorized=false` 的 Digital Twin/Effect test double。
Production profile 不包含它们；adapter 缺失时必须失败关闭。该边界不允许恢复 Python 仿真。

Client2 HVAC/Seat 中控页面可以在该 debug/test profile 下形成演示闭环，但 Header 和每项 Effect
必须持续显示 SIMULATED。页面不能直接修改本地 reported state，不能调用模拟 adapter，也不能
把截图或动画作为真实车辆证据；请求必须经 SDK/Governance/Durable Effect/readback 返回。

涉及需求：`S2-CTX-001`、`S2-TWN-001`、`S2-SCN-001`、`S2-EFF-001`、
`S2-ADP-001/002`、`S2-HMI-001..006`。状态：`Accepted Temporary`。

## DEV-025 Client2 patched APK 是演示 HMI，不是量产 AAOS 产品 HMI

Client2 只验证面板 UX、typed Binder 和故障恢复。权威 session/plan/effect 状态必须保留在
Runtime，HMI 只做 reducer/render；unknown driving state 使用 restricted UI。量产前 OEM 必须提供
可维护扩展点并完成 UX restriction、签名、升级、分辨率和整车验证。状态：`Accepted Temporary`。

2026-07-16 HMI-D0 已将设计 Panel 收敛到 Client2 1920x1080 安全框并提高透明度，但这仍是设计资产；
多 DPI、多窗口、system inset 和真实 Android View 边界必须在 HMI-D1/D4 重新取证，不能由 PNG 关闭。

## DEV-026 Python 原型退役与 Android-only 范围

2026-07-16 用户明确停止维护本地 Python 仿真，并将当前开发聚焦于黑盒 Android 13 控制器。
因此删除 Python backend、REST/JSON contract、Linux Python binding/CLI/daemon、旧 Console、
systemd 样例及其 smoke/checker/documentation。

这是相对原架构 `XSC-001..006` 多 SoC 和 `DEL-002/004` Android/Linux 同步交付的显式范围偏差。
保留的硬件相关资产为 Android ModelProvider/Scheduler、Vendor empty provider、NPU C ABI/JNI、
Driver/HAL 缺口矩阵、Safety/虚拟化约束和目标硬件交付工具。未来恢复 Linux 必须建立新的非
Python 工作包和 contract parity，不得复活旧样例。

状态：`Accepted Scope`。详细边界见
`CENTRAL_BRAIN_PYTHON_PROTOTYPE_RETIREMENT.md`。

## DEV-027 P1-W05 复用 Runtime Service 与进程内 Session/Event registry

架构需求要求 Session/Event owner、capability、callback lifecycle 位于 Android Runtime；但黑盒系统
不能新增 system service/VINTF stable-AIDL，现有软件验收又冻结为三个 signature-protected app
Service。P1-W05 因此不增加 Manifest component，而让 `CentralBrainRuntimeService.onBind()` 按两个显式
action 返回独立 Session/Event V1 Binder。旧无 action 绑定仍返回 `ICentralBrainRuntime`，避免破坏
Client2 既有路径。

会话注册表提升为 Runtime 进程级 singleton，使显式 unbind/rebind 的 Service 实例重建不丢 active
session；它没有 Room 持久化，Runtime 进程死亡后仍会丢失。因此当前固定声明：

```text
session_runtime_transient_registry=true
session_runtime_persistence_wired=false
session_runtime_process_death_rehydration=false
scenario_execution_enabled=false
```

Event V1 的 terminal page 禁止 `nextCursor`，facade 暂时重用该页的 request cursor 并按 sequence 去重
callback replay；该兼容策略由 `ISSUE-034` 跟踪，不能描述为 durable/high-volume broker。

此外，详设 8.8 早期草图中的 `approve(ApprovalResponse)` 和 `undo(UndoRequest)` 超过 P1-W04 已冻结
合同：当前没有 ApprovalResponse/UndoRequest DTO，也没有 grant/undo Binder。P1-W05 facade 只发布
Session/Event；approval response 与 undo execution 继续保持 false，不得由 SDK 自行发明 authority。

状态：`Accepted Temporary`。P1-W06 负责 Room v4/process-death rehydration，P1-W07/V2 aggregate review
负责 cursor 演进；目标 system placement/VINTF 仍由 `ISSUE-021/027` 跟踪。

## DEV-028 P1-W06 仅持久化 Session/Event live path，其他 v4 entity 暂为 schema foundation

架构基线要求 Room v4 同时覆盖 Session、Plan、Node、RuntimeEvent、EffectObservation 和
Compensation。P1-W06 已交付全部六类 entity、FK/index、schema JSON 和 v3->v4 migration；但当前唯一
已发布执行面仍是 P1-W05 Session/Event Binder，因此 production wiring 只接入 `sessions` 与
`runtime_events`。Plan/Node/EffectObservation/Compensation 表不得在缺少 Compiler/Graph/Effect authority
时被测试代码伪造为已执行状态。

`TransientSessionEndpoint` 类名因冻结的 P1-W05 审查引用暂时保留，但其 production registry 已通过
constructor injection 切换为 `DurableSessionRegistry`；仅 callback registration/death recipient 仍是
进程内对象。SDK 在 Runtime 进程死亡后以 snapshot -> cursor replay -> sequence deduplicate -> callback
register 重建订阅，不持久化 Binder callback。

v3 `runtime_session` 行仍迁移留存，但历史 ID/request 不满足 Session V1 UUID/canonical request 合同；
因此以固定 legacy digest 标记，非 terminal 状态失败关闭为 `FAILED`，并从 owner-scoped Session V1 查询
隔离。该兼容边界由 migration probe 验证，不将旧数据伪装成可恢复的新 Session。

当前固定声明：

```text
session_runtime_transient_registry=false
room_schema_version=4
session_runtime_persistence_wired=true
session_runtime_process_death_rehydration=true
plan_runtime_published=false
effect_runtime_service_published=false
scenario_execution_enabled=false
```

状态：`Accepted Temporary`。P2/P3 分别负责 Plan/Effect/Graph runtime wiring；P1-W07 负责 aggregate
contract/cursor 演进评审。该偏差不触发 Driver/HAL、厂商系统、Python/Linux 或虚拟化开发。

## DEV-029 Aggregate Contract v2 不等于 wire V2，Event cursor/ACK 延后独立发布

P1-W07 将 P1-W01..P1-W06 聚合为 schema `2.0.0` 的机器可读 capability/compatibility contract，但
Session/Plan/Event/Effect 的 wire/DTO 仍全部冻结在 V1。这样可在不破坏 Client2/SDK 兼容性的前提下，
统一校验 hash、capability、error、bounds、Room v4 和 forbidden fallback。

Event V1 terminal page 无 forward cursor 的限制不能在冻结接口中修补。聚合评审已决定未来独立发布
Event V2 terminal resume cursor + monotonic owner/session-scoped ACK；P1-W07 只冻结必需语义，不新增 AIDL、
capability 或 broker。当前 sequence dedup 只保证低容量重连正确性，不得描述为高吞吐 ACK broker。

状态：`Accepted Temporary`。实现所有权进入 `P6-W01/P6-W02`，届时必须分配新 interface version/hash、
Room ACK retention、SDK negotiation 和 process-death tests。当前 `event_v2_interface_published=false`、
`production_ready=false`、`target_hardware_validated=false`。

## Android 实现证据索引

下列短语是历史软件增量的稳定追踪键，指向仍保留的 Android 源码和检查器；它们不表示硬件或
量产状态：

| 追踪键 | 当前结论 |
| --- | --- |
| R1 Gradle 多模块、API 33 | SDK AAR、Runtime APK、Demo APK 软件基线完成。 |
| R2C 已通过 API 33 | typed Binder lifecycle/race 完成。 |
| R3C1 进展 | Action governance core 完成。 |
| R4A 进展 | Room durable schema 完成。 |
| R4B1 进展 | durable task admission 完成。 |
| R4B2 进展 | Runtime durable wiring 完成。 |
| R4B3 进展 | durable approval repository 完成。 |
| R4C1 进展 | fail-closed restart reconciliation 完成。 |
| R4C2A 进展 | effect prepare/claim 完成。 |
| R4C2B 进展 | retry/terminal/dead-letter 完成。 |
| R4C3A 进展 | effect adapter contract/fault matrix 完成。 |
| R4C3B 进展 | effect material activation gate 完成，production 仍关闭。 |
| R4C3C 进展 | production fail-closed visibility 完成。 |
| R5A1 进展 | ModelProvider contract 完成。 |
| R5A2 进展 | inference scheduler contract 完成。 |
| R5B1 进展 | deterministic provider 仅限 test。 |
| R5B2 进展 | test-only router 完成。 |
| R5C1 进展 | model readiness snapshot 完成。 |
| R5D1 进展 | target deployment gate 完成，Vendor provider 仍为空。 |
| R6A1 进展 | bounded Event runtime 完成。 |
| R6A2A 进展 | Event persistence schema 完成。 |
| R6A2B 进展 | durable Event repository 完成。 |
| R6A3 进展 | Event readiness wiring 完成。 |
| R6B1 进展 | bounded Memory lifecycle 完成。 |
| R6B2 进展 | Memory readiness wiring 完成。 |
| R6C1 进展 | signed built-in Skill test runtime 完成。 |
| R6C2 进展 | fixed governance middleware 完成。 |
| R6C3 进展 | Skill/governance readiness wiring 完成。 |
| R7A1 进展 | aggregate software acceptance snapshot 完成。 |
| R7B 进展 | Client2 typed Binder migration 完成。 |
| R7C 进展 | Client2/Runtime recovery matrix 完成。 |
| R7D 进展 | Android application handoff 完成。 |
| P1-W03 进展 | Event/callback V1 合同完成；Service/Room/hardware 均未发布。 |
| P1-W04 进展 | Effect/Approval V1 合同完成；Service/grant/undo/Room/hardware 均未发布。 |
| P1-W05 进展 | SDK facade、Session/Event app-layer Service、rebind/resubscribe 完成；Room/process-death/scenario/hardware 均未发布。 |
| P1-W06 进展 | Room v4、Session/Event durable repository 和 Runtime process-death rehydration 完成；Plan/Effect/scenario/hardware 均未发布。 |

Safety/跨域边界继续由 `CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md` 管理；本项目不开发
虚拟化。
