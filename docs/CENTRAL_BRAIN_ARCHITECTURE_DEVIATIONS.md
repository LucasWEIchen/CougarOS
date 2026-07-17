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
| DEV-030 | P2-W01 canonical path 是内部语义合同，不是 OEM/VHAL property mapping。 | S2-CTX-001, S2-TWN-001, ISSUE-030 | Accepted Temporary |
| DEV-031 | P2-W02 target ranges/risk 是 Stage 2 软件合同，不是 OEM 标定或生产授权。 | S2-TWN-001, S2-ADP-001, ISSUE-029/030 | Accepted Temporary |
| DEV-032 | P2-W03 Digital Twin 是进程内非持久化 foundation。 | S2-TWN-001, ISSUE-030 | Accepted Temporary |
| DEV-033 | P2-W04 Context 固定 non-production-trusted 且未接 Service/provider。 | S2-CTX-001, S2-SAF-001, ISSUE-029/030 | Accepted Temporary |
| DEV-034 | P2-W05 Scenario asset 只有 Git/CI SHA-256 build identity，没有独立 artifact 密码学签名或 Runtime wiring。 | S2-SCN-001, ISSUE-031 | Accepted Temporary |
| DEV-035 | P2-W06 Resolver 只提供固定规则和 process-local availability；production trust/Service/compiler 均未接。 | S2-SCN-001, S2-SAF-001, ISSUE-029/031 | Accepted Temporary |
| DEV-036 | P2-W07 Compiler 只生成 digest-only、未发布、不可执行的 Plan foundation。 | S2-SCN-001, S2-GRF-001, ISSUE-029/031 | Accepted Temporary |
| DEV-037 | P2-W08 仿真基类只存在于 debug source，并补充非生产 SimulationDescriptor；无 domain target、Runtime 注册或真实 readback。 | S2-ADP-001, S2-EFF-001, ISSUE-030/033 | Accepted Temporary |
| DEV-038 | P2-W09 HVAC fixed-binary target/range/Twin 是 debug internal contract，不是 OEM property/标定或 production adapter。 | S2-ADP-001, S2-EFF-001, ISSUE-030/033 | Accepted Temporary |
| DEV-039 | P2-W10 Seat safety 是 debug Runtime-owned gate，不是 OEM Safety authority。 | S2-ADP-001, S2-SAF-001, ISSUE-029/030/033 | Accepted Temporary |
| DEV-040 | P2-W11 Media/Nav 只提供 simulation state 与 synthetic digest observation。 | S2-ADP-001, ISSUE-030/031/033 | Accepted Temporary |
| DEV-041 | P2-W12 debug controller 不是 production Context 或车辆控制 authority。 | S2-CTX-001, S2-ADP-001, ISSUE-030/033 | Accepted Temporary |
| DEV-042 | P3-W01 Graph Runtime 是 process-local control-only state machine，不是 durable/executable production Graph。 | S2-GRF-001, ISSUE-022/026 | Accepted Temporary |
| DEV-043 | P3-W02 typed executor 只有 main contract 与 debug deterministic implementation，不是 Graph/Effect/model production execution。 | S2-GRF-001, S2-SAF-001, S2-EFF-001, ISSUE-022..024/026 | Accepted Temporary |
| DEV-044 | P3-W03 serializer 是 process-local canonical contract，尚未接 Graph/Room/restart recovery。 | S2-GRF-001, NV-G-006/007, ISSUE-022/026 | Accepted Temporary |
| DEV-045 | P3-W04 retry/timeout policy 尚未接 Graph scheduler 或 production Effect reconcile。 | S2-GRF-001, NV-G-004, ISSUE-022/026 | Accepted Temporary |
| DEV-046 | P3-W05 approval interrupt 只有 checkpoint-ready 合同，尚未接 Room/Graph/Binder grant/restart recovery。 | S2-SAF-001, S2-UX-003, S2-GRF-001, ISSUE-022/026/029 | Accepted Temporary |
| DEV-047 | P3-W06 EffectCoordinator 是进程内两阶段合同，尚未接 durable outbox、Graph、readback/reconcile 或 production adapter。 | S2-EFF-001, S2-SAF-001, ISSUE-022/026/030/033 | Accepted Temporary |
| DEV-048 | P3-W07 verifier/reconciler 是 caller-driven process-local 合同，尚未接 scheduler/Room/Graph 或 production readback。 | S2-EFF-001, S2-TWN-001, ISSUE-022/026/030/033 | Accepted Temporary |
| DEV-049 | P3-W08 Compensation/Undo 只形成 process-local 新 governed task，原 Effect terminal 不回退。 | S2-EFF-001, S2-UX-003, ISSUE-022/023/029/030 | Accepted Temporary |
| DEV-050 | P3-W09 Restart recovery repository 未注入 Runtime/Binder/Graph execution。 | S2-GRF-001, S2-EFF-001, ISSUE-022/026/030/033 | Accepted Temporary |
| DEV-051 | fixed UI alias 仍是闭源 Client2 兼容边界；legacy static owner 已由 P4-W02 Java coordinator 解除。 | S2-UX-001, S2-HMI-005, ISSUE-019/033 | Accepted Temporary |
| DEV-052 | P4-W02 process-recreation checkpoint 使用 app-private SharedPreferences，不是量产加密 HMI state store。 | S2-UX-001..003, NV-G-003, ISSUE-019/034 | Accepted Temporary |
| DEV-053 | P4-W03 固定 1920x1080 safe frame、UNKNOWN/UNAVAILABLE 投影和 placeholder drawer 不是量产多屏 HMI 或车辆回读。 | S2-HMI-001..003/006, ISSUE-019/033 | Accepted Temporary |
| DEV-054 | P4-W04 冻结 Session V1 以 canonical HVAC1 utterance/HMI_BUTTON 承载手动参数，不是 typed parameter/HMI_CONTROL transport。 | S2-HMI-001/005, XSC-001/006, ISSUE-033 | Accepted Temporary |
| DEV-055 | P4-W05 冻结 Session V1 以 canonical SEAT1 utterance/HMI_BUTTON 承载手动参数，且无 approval response，不是 typed safety transport。 | S2-HMI-002/003/005, S2-SAF-001, XSC-001/006, ISSUE-029/033 | Accepted Temporary |

## DEV-017 Client2 APK 逆向演示路径

原因：目标 HMI 源码不可用，用户批准在隔离工程
`apk-labs/client2-central-brain/` 中进行资源、smali 和 secondary-dex patch。

现状：Client2 已通过 public Session/Event SDK/typed Binder 打开 owner-scoped 会话，APK 不申请网络权限，不保留
HTTP fallback。2026-07-17 已在 1920x1080 Android 13 ARM64 物理设备验证默认隐藏、显示、二次隐藏、面板外关闭、
snapshot/event/replay、Runtime process-death reconnect/duplicate suppression 和 Client2 进程恢复。

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

## DEV-030 P2-W01 canonical path 不是 OEM/VHAL property mapping

P2-W01 以 12 项 VSS-style canonical path 建立 Context/Twin 内部语义合同，并固定 typed scalar、unit、area、
quality/source 和 freshness。该 allowlist 不是 OEM property catalog，也没有绑定 `VehiclePropertyIds`、
CarPropertyManager、vendor Binder、SOA service、CAN/DBC 或设备节点。

`SignalSource.AAOS/VENDOR` 只是 observation provenance；它不表示服务存在、权限已授予、area 已映射、
readback 可用或 production authorized。生产 Service 没有注册 signal provider，Android 13 探针只构造
SIMULATED 值验证合同。

状态：`Accepted Temporary`，对应 `ISSUE-030`。P2-W02/P2-W03 可继续开发 capability/twin 软件合同；
真实映射只能在 P8 依据目标 SDK/权限/owner 证据实现。当前 `vehicle_signal_provider_wired=false`、
`vehicle_property_mapping_configured=false`、`hardware_accessed=false`。

## DEV-031 P2-W02 range/risk 不是 OEM 标定或生产授权

P2-W02 为 debug/test Digital Twin 和后续 Plan validation 固定 8 项 capability 的 target range、area、risk、
reported signal 与 fresh-signal dependency。温度、风量、座椅等级/角度和文本范围是可重复的软件合同，
不是车型标定、功能安全限值、VHAL property config 或用户授权。

全部 capability 的 `productionAvailable`/`productionAuthorized` 都是 false。Seat recline 的 HIGH risk 与
5 项 fresh dependency 只建立 fail-closed 输入要求，不能替代 OEM Safety authority 或硬联锁。真实 adapter
接入时必须提供版本化 capability evidence，不得静默修改现有 contract 或把 SIMULATED 标记提升为生产。

状态：`Accepted Temporary`，对应 `ISSUE-029/030`。当前
`vehicle_production_capability_authorized_count=0`、`vehicle_capability_adapter_registry_wired=false`、
`vehicle_property_mapping_configured=false`、`hardware_accessed=false`。

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
| P2-W01 进展 | 12 项 canonical vehicle signal schema、typed scalar、unit/area/source/quality/freshness 与 API 33 ARM64 probe 完成；真实 provider/property mapping 未接入。 |
| P2-W02 进展 | 8 项 capability/range/risk/readback/dependency 与 API 33 ARM64 probe 完成；production authorized count 为 0。 |
| P2-W03 进展 | 进程内 desired/reported Twin、monotonic revision、TTL/quality、atomic snapshot/reconciliation 与 API 33 ARM64 probe 完成；持久化/production wiring 未接入。 |
| P2-W04 进展 | versioned Context/freshness/trust/restricted foundation 与 API 33 ARM64 probe 完成；productionTrusted/Service/provider 仍关闭。 |
| P2-W05 进展 | cold/fatigue/rest build-owned manifest、strict parser/schema/checksum/isolation 与 API 33 ARM64 probe 完成；artifact crypto/production trust/Runtime/Graph 仍关闭。 |
| P2-W06 进展 | Deterministic Resolver 与 API 33 ARM64 probe 完成；model/compiler/production Service/Graph 仍关闭，ISSUE-029/031 仍开放。 |
| P2-W07 进展 | Digest-bound immutable typed Plan compiler 与 API 33 ARM64 probe 完成；target/Runtime publication/Graph/Effect 仍关闭，ISSUE-029/031 仍开放。 |
| P2-W08 进展 | Debug-only simulated Effect base、manual clock、fault matrix 与 API 33 ARM64 probe 完成；domain target/Runtime registration/真实 readback 仍关闭，ISSUE-030/033 仍开放。 |
| P2-W09 进展 | Debug-only HVAC typed target、isolated desired/reported Twin 与 API 33 ARM64 probe 完成；production property/Runtime/HMI 仍关闭，ISSUE-030/033 仍开放。 |
| P2-W10 进展 | Debug-only Seat typed target、dispatch-time Safety/occupancy/belt/approval revalidation、progress 与 API 33 ARM64 probe 完成；OEM Safety/production property/Runtime/HMI 仍关闭，ISSUE-029/030/033 仍开放。 |
| P2-W11 进展 | Debug-only Media state、digest-only synthetic POI/route、replaceable backend 与 API 33 ARM64 probe 完成；真实 media/navigation/location/Runtime/HMI 仍关闭，ISSUE-030/031/033 仍开放。 |

Safety/跨域边界继续由 `CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md` 管理；本项目不开发
虚拟化。

## DEV-032 P2-W03 Digital Twin 是进程内非持久化 foundation

完整架构要求 Digital Twin 支撑重启恢复、adapter readback 和 durable Graph reconciliation。P2-W03 为保持
最小可验证增量，只在 Runtime Java 层实现 synchronized desired/reported store、全局 monotonic revision、
TTL/quality、atomic snapshot 与 reconciliation；没有接入 Room、Service singleton/DI、Effect Coordinator
或任何车辆 provider。

因此 Runtime 进程死亡会丢失 Twin，revision 只在单进程生命周期内单调；API 33 ARM64 probe 仅证明软件
合同可运行，不是车身状态、跨进程恢复或 production adapter 证据。P2-W04 可以消费该 immutable snapshot，
但不能把它标记为 trusted hardware context。持久化/wiring 必须等相应工作包明确 schema、owner、migration
和 recovery 后单独验收。

状态：`Accepted Temporary`。`vehicle_digital_twin_persistence_wired=false`、
`vehicle_digital_twin_adapter_wired=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。

## DEV-033 P2-W04 Context 是非 production-trusted 的进程内 foundation

架构中的 Context 应由可信 Vehicle/Safety/User/Environment source 生成并供 Plan/Policy 使用。目标黑盒
Android 13 当前没有已确认的 vehicle provider/property mapping、Safety authority 或 profile-memory owner，
因此 P2-W04 只实现从 atomic Twin + Runtime state 生成 immutable snapshot、freshness/trust report、
restricted 和 digest 的软件 foundation。

SIMULATED 完整 Context 可在 debug/test 中为 `restricted=false`，表示 required field/safety/motion 数据
内部一致；这不等于 production trusted 或硬件验证。即使 test 输入 source=AAOS/VENDOR 且 Runtime state
标记 platform trusted，P2-W04 仍强制 `productionTrusted=false`，防止 provenance enum 越权成为 activation
evidence。完整 MOVING Context 也不自动 restricted，行驶中动作限制仍由 action-specific Safety Policy
执行。

状态：`Accepted Temporary`。`context_snapshot_production_trusted=false`、
`context_snapshot_production_wired=false`、`vehicle_signal_provider_wired=false`、
`hardware_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`。P8 必须用独立 provider/property/permission/readback evidence
关闭 trust；本偏差不能由 debug probe 关闭。

## DEV-034 P2-W05 Scenario 只有 build checksum，不是 production-signed catalog

完整 AIOS 的 built-in Scenario catalog 应具备独立 artifact signer allowlist、证书/密钥生命周期、revoke、
rollback、版本兼容与 activation owner。P2-W05 为完成最小 manifest/schema foundation，只把 cold/fatigue/
rest JSON 和 strict schema 纳入 APK build，使用仓库受控 `scenarios-v1.sha256` 在 CI 与 debug APK assets
中验证字节一致性。

该 sidecar 与 APK signer 共同提供可复现 build identity，但 parser/catalog 没有执行独立 artifact
signature verification，也没有 lifecycle store 或 revoke/rollback owner。因此 catalog 固定
`isArtifactCryptographicallyVerified=false`、`isProductionTrusted=false`；即使 manifest 校验通过也不能
接入 production Service、授予 approval 或执行 Graph/Effect。`ISSUE-031` 的场景产品/隐私 owner 保持开放。

状态：`Accepted Temporary`。`scenario_manifest_artifact_crypto_verified=false`、
`scenario_catalog_production_trusted=false`、`scenario_runtime_wired=false`、
`scenario_graph_execution_enabled=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。未来独立签名机制必须
单独版本化并通过 target/release evidence，不能把本 SHA-256 sidecar 重新解释为签名。

## DEV-035 P2-W06 Resolver 是固定规则、process-local availability foundation

完整 AIOS Resolver 最终需要 product-owned intent taxonomy、locale/version lifecycle、生产 capability
discovery、可信 Context/Safety source、可审计 rollout/revoke 和与 Session Service 的 durable wiring。P2-W06
只实现显式场景 ID 和固定中英文 alias 到三项 build-owned manifest 的确定性选择；它不调用模型，不接受
HMI 动态规则，也不创建 capability。

`CapabilitySnapshot` 当前只冻结 Stage 2 catalog metadata 与 Runtime 提供的 unavailable 集合。software
simulation profile 可用于 debug/test resolver contract；production profile 要求 production available+
authorized，但 P2-W02 全部为 false，同时 Context/Capability snapshot 的 production trust 固定 false，
因此 production resolution 必须失败关闭。该 snapshot 不是 adapter discovery、车辆授权或硬件证据。

`ScenarioResolution` 明确 `isExecutable=false`。Resolver 未接 production Service/Room，P2-W07 Compiler 和
后续 Graph/Effect 不得仅凭 scenario ID 绕过 request/Context/capability/resolution digest 或 hard policy。

状态：`Accepted Temporary`。`scenario_resolver_defined=true`、
`scenario_resolver_model_invoked=false`、`scenario_resolver_runtime_wired=false`、
`scenario_compiler_wired=false`、`scenario_graph_execution_enabled=false`、
`effect_dispatch_enabled=false`、`hardware_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`。关闭本偏差需要独立 production owner、可信 provider 与目标
evidence，不能用 debug alias/probe 提升状态。

## DEV-036 P2-W07 Compiler 是 digest-only、未发布的 Plan foundation

完整 AIOS 编译链最终需要 Session-owned plan identity、用户偏好/场景 target material、durable Graph
publication、可信 Safety/Capability source、Effect adapter 与每次 dispatch 前的 Governance 重验。P2-W07
只把 P2-W06 Resolution 和同一 immutable Context/Capability snapshot 编译为 P1 typed Plan；manifest 没有
target scalar，因此 compiler 不生成温度、风量、座椅角度、媒体或导航参数。

P1 AIDL DTO 是可变 transport 类型。为满足本阶段 immutable DAG 要求，`CompiledPlan` 在内部持有 deep
copy，并对每次 `toScenarioPlan()` 返回新的 deep copy；调用者修改 transport 不影响 owner。该设计不等于
Plan persistence、Graph runtime 或 authorization。Plan/Node digest 只提供输入一致性，不是 artifact signer、
Safety grant 或执行证明。

状态：`Accepted Temporary`。`scenario_plan_compiler_defined=true`、
`scenario_plan_compiler_runtime_wired=false`、`scenario_plan_runtime_published=false`、
`scenario_graph_execution_enabled=false`、`effect_dispatch_enabled=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。关闭本偏差需要 P3/P4 的
durable Graph/Effect material/dispatch 及 production owner/evidence；不得将 debug compiler probe 提升为执行证据。

## DEV-037 P2-W08 仿真基类不是 production adapter 或真实车辆回读

完整 AIOS adapter 应由 profile-aware registry 激活，绑定 domain typed target、capability/area/range、安全策略、
desired/reported Twin、durable Effect material 和真实目标 readback。P2-W08 为先冻结故障与幂等语义，只在
`src/debug` 提供通用 `SimulatedEffectAdapter`、manual `SimulationClock` 与 immutable
`FaultInjectionProfile`；它不解析任何 HVAC/Seat/Media/Nav target，也不接 Runtime Service。

现有 P1 `EffectAdapter.Descriptor` 没有 simulation provenance 字段，因此 debug 基类补充独立
`SimulationDescriptor`。该 descriptor 只用于工程观测，固定 `simulation=true`、
`productionAuthorized=false`、source `SIMULATED`，不能进入 production registry 或提升 Effect trust。
process-memory record 只用于 debug fault test，不提供进程恢复、审计持久性或硬件证据。

状态：`Accepted Temporary`。 `simulated_effect_adapter_debug_only=true`、
`simulated_effect_adapter_release_source_absent=true`、`simulated_effect_adapter_production_registered=false`、
`simulated_effect_adapter_runtime_wired=false`、`effect_dispatch_enabled=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。P2-W09..P2-W11 必须分别
实现 typed domain target；P3/P8 必须另行完成 durable Runtime/production adapter 与真实 readback evidence。

## DEV-038 P2-W09 HVAC target 与 Twin 不是 OEM 车控合同

P2-W09 用 fixed-binary version 1 payload 冻结 power、target-temperature 和 fan 的 typed absolute target，
并复用 P2-W02 的 software range/area 与 P2-W03 的 in-process Twin。该结构用于 debug/test 可复验性，不是
AAOS CarProperty、vendor SOA、CAN/DBC、标定数据或 production authorization；未来 production adapter
可以采用不同 transport，但必须在 Effect boundary 显式映射同一 canonical semantics。

adapter-owned Twin 与 shared Runtime/Room 不连接，进程死亡或 reset 会丢失。SIMULATED reported 只证明故障
矩阵与 UX 可观测性，不是实体 HVAC readback。production source set 不包含 adapter，缺真实接口时继续
fail closed，不允许把 debug payload 发给未知 vendor service。

状态：`Accepted Temporary`。 `simulated_hvac_debug_only=true`、
`simulated_hvac_release_source_absent=true`、`simulated_hvac_production_registered=false`、
`simulated_hvac_runtime_wired=false`、`vehicle_property_mapping_configured=false`、
`effect_dispatch_enabled=false`、`hardware_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`。关闭本偏差需要 P3 shared Runtime contract 和 P8 目标平台
property/permission/area/readback/owner evidence。

## DEV-039 P2-W10 Seat safety 是 debug Runtime-owned gate，不是 OEM Safety authority

P2-W10 用 fixed-binary version 1 payload 冻结 heating、ventilation 和 recline absolute target，并在 recline
admission 与 dispatch 两次读取注入的 `SafetyVehicleStateProvider`、`SeatOccupantStateProvider` 和
`SeatApprovalVerifier`。这套 gate 用于验证 motion/belt/occupancy/approval race 的 fail-closed 软件语义，
不是实体 Occupant ECU、seat controller 硬联锁、OEM Safety policy 或 production approval grant。

Seat provider 与 approval verifier 只存在于 debug/test 注入边界，source 固定 SIMULATED、production
authorization 固定 false。adapter-owned Twin 和 progress projection 不连接 shared Runtime/Room，且不代表
实体座椅角度/readback；进程死亡或 reset 会丢失。未知或变化状态会永久拒绝而不是猜测 vendor API。

状态：`Accepted Temporary`。`simulated_seat_debug_only=true`、
`simulated_seat_release_source_absent=true`、`simulated_seat_production_registered=false`、
`simulated_seat_runtime_wired=false`、`vehicle_signal_provider_wired=false`、
`effect_dispatch_enabled=false`、`hardware_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`。关闭本偏差需要 OEM Safety/approval owner、P3 durable Runtime
和 P8 target property/permission/occupancy/belt/readback evidence；debug probe 不能提升其状态。

## DEV-040 P2-W11 synthetic Media/Navigation 不是平台播放器或真实导航

P2-W11 的 Media adapter 只保存 PLAY/PAUSE/STOP immutable simulated state；Navigation adapter 只将 canonical
POI query 转为 SHA-256，并用 deterministic backend 生成 synthetic ID、label key、distance 和 duration。该输出
用于验证 Effect observation/fault/UX 合同，不是 Android MediaSession、vendor player、真实地图检索、定位、
route planning 或外部导航应用集成。

replaceable backend 仍是 debug simulation contract：constructor 强制 production unauthorized、无 external
Activity、无 network，Navigation 还强制无 location upload。该检查不等于 production sandbox 或隐私审批；
base 仍在 process memory 保留 bounded canonical material，reset/进程死亡会清除。

状态：`Accepted Temporary`。`simulated_media_nav_debug_only=true`、
`simulated_media_nav_release_source_absent=true`、`simulated_media_nav_production_registered=false`、
`simulated_media_nav_runtime_wired=false`、`external_activity_started=false`、`location_uploaded=false`、
`network_accessed=false`、`effect_dispatch_enabled=false`、`hardware_accessed=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。关闭本偏差需要 P3/P4
durable Effect/HMI 和 P8 target media/navigation owner/API/permission/readback/privacy evidence。

## DEV-041 P2-W12 debug controller 不是 production Context 或车辆控制 authority

P2-W12 进展：工程师已有 debug-only AIDL 控制面，用于设置 simulated driving/canonical signal、四个 debug
adapter fault、manual clock 和 reset。即使该 Service 经过 signature permission 与 current-signer capability
双层授权，它的输入仍是测试人员提供的 process-local fixture，不是可信 VHAL/Safety/vehicle signal。

控制器不写 shared Context/Twin/Room，不发布 Plan/Graph/Effect，不注册 production adapter。release source
没有 AIDL、permission、Service、probe Activity 或 policy grant；debug snapshot/audit digest 也不是车辆 readback
证据。debug signer 不能提升 production trust，API 33 ARM64 probe 不能提升 target hardware 状态。

状态：`Accepted Temporary`。`debug_simulation_controller_debug_only=true`、
`debug_simulation_controller_release_source_absent=true`、
`debug_simulation_controller_production_exported=false`、`debug_simulation_controller_runtime_wired=false`、
`vehicle_signal_provider_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`。关闭本偏差需要 P3/P4 将受治理的软件链显式接入 debug controller，P8
另以 OEM/Vendor provider、permission、Safety/readback 和目标证据替换仿真输入；两者不得共用完成标志。

## DEV-042 P3-W01 Graph Runtime 非 durable 且不执行 executor

P3-W01 Graph Runtime 已位于 Runtime main source，因为 graph 状态合同最终属于 production AIOS Kernel；但
当前没有从 `CentralBrainRuntimeService`、Session/Plan Binder 或 Room 引用它。`NodeExecutorRegistry` 只保存
PlanContract node type 的 control-only registration，claim/complete 由 test harness 显式推进，不能解释为
Context/Policy/Effect/Model/Tool/Memory executor 已运行。

run/node/event 只保留进程内内存；进程死亡会丢失。deadline 使用可注入 clock，event 是 digest-only bounded
projection，不是 durable audit/checkpoint。`COMPENSATING` 只在状态表预留，P3-W08 前没有 compensation；
required compensation path 当前进入 STUCK，而不是伪造 rollback。

状态：`Accepted Temporary`。`agent_graph_runtime_defined=true`、
`agent_graph_executor_dispatch_enabled=false`、`agent_graph_runtime_persistence_wired=false`、
`agent_graph_runtime_binder_published=false`、`agent_graph_runtime_production_wired=false`、
`effect_dispatch_enabled=false`、`model_invoked=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`。P3-W02 提供 typed executor，P3-W03 提供 bounded
checkpoint，P3-W09 才关闭 restart durability；P8 另行关闭车辆/NPU/Driver-HAL，不能复用本偏差完成标志。

## DEV-043 P3-W02 typed executor 非 production execution

P3-W02 的 immutable input/output/result、11 类 exact-class schema 与 registry validation 位于 Runtime main
source，作为后续 Durable Graph 的类型合同。七类 deterministic executor 和显式 harness 仅位于 `src/debug`；
Release 不含实现或 probe。registry 不保存/调用 executor，`AgentGraphRuntime` 与 Runtime/Governance Service
也没有引用该执行链。

Context/Verification debug output 不提升 production trust；Policy/Approval 的 trusted flag 仅为测试输入，
不是 OEM authority。Effect 固定 WAITING/NOT_DISPATCHED，Compensation 固定 REJECTED/NOT_DISPATCHED；
Model/Tool/Memory 只有 digest-only schema，无 executor/fallback。因此 API 33 ARM64 证据只证明 schema 与
deterministic fail-closed 行为可运行，不能声明车辆、模型或副作用已执行。

状态：`Accepted Temporary`。`typed_node_executor_contract_defined=true`、
`typed_node_executor_schema_count=11`、`typed_node_executor_debug_count=7`、
`typed_node_executor_graph_dispatch_enabled=false`、`typed_node_executor_production_wired=false`、
`effect_dispatch_enabled=false`、`model_invoked=false`、`network_accessed=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`。P3-W03..W09 依次补 checkpoint、retry、approval、
Effect/verification、compensation、Room/recovery；P8 另行关闭真实 Vehicle/NPU/Driver-HAL，不能复用本偏差标志。

## DEV-044 P3-W03 checkpoint serializer 尚未形成 durable Graph recovery

P3-W03 的 `CheckpointValue`、registered codec、canonical JSON 和 digest envelope 位于 Runtime main source，
因为未来 production recovery 必须使用同一稳定合同。当前 `AgentGraphRuntime`、Room v4、Session/Binder Service
均未引用 serializer；没有 checkpoint row transaction、migration、process-death rehydrate 或 mismatch -> STUCK
状态映射。API 33 ARM64 probe 只在 Activity 进程内做 encode/decode 与拒绝测试。

serializer 只允许显式注册的 exact class 经 `PayloadCodec` 转换为 bounded primitive tree；它拒绝 Java
serialization、class-name reflection、arbitrary Binder/Parcel blob 和未知 type/version。该安全合同不能代替
durable owner、encryption/key、retention、migration 或 rollback evidence，也不能使 P3-W01 Graph 可执行。

状态：`Accepted Temporary`。`checkpoint_serializer_defined=true`、
`checkpoint_serializer_java_serialization_enabled=false`、
`agent_graph_runtime_persistence_wired=false`、`agent_graph_executor_dispatch_enabled=false`、
`effect_dispatch_enabled=false`、`model_invoked=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`。P3-W09 必须接 Room transaction/restart recovery 并把
digest/type/version mismatch 映射 STUCK；P8 另行关闭 Vehicle/NPU/Driver-HAL，不能复用本偏差标志。

## DEV-045 P3-W04 retry/timeout policy 尚未接 Graph 或 production Effect

P3-W04 的 `NodeRetryPolicy`、`NodeTimeoutPolicy` 与 `BackoffCalculator` 位于 Runtime main source，作为后续
Durable Graph 的稳定 QoS 合同。当前 `AgentGraphRuntime`、Room、Binder 与 Runtime Service 均未引用这些类；
没有 scheduler wake-up、durable attempt row、Effect adapter dispatch 或真实 reconcile 调用。

确定性 SHA-256 jitter 只用于可重放软件策略，不是实时调度精度证据。Effect 门禁要求 idempotency key 与
`CONFIRMED_NOT_APPLIED`，但该 reconcile state 当前只由 JVM/debug probe 输入，不来自 production adapter/readback；
因此不能声明重复副作用已经在目标车辆上被消除。

状态：`Accepted Temporary`。`node_retry_policy_defined=true`、`node_timeout_policy_defined=true`、
`effect_idempotency_reconcile_gate_verified=true`、`retry_timeout_policy_runtime_wired=false`、
`agent_graph_executor_dispatch_enabled=false`、`effect_dispatch_enabled=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`。P3-W06/P3-W09 必须分别接 durable Effect reconcile 与
Graph restart/attempt recovery；P8 另行关闭 Vehicle/NPU/Driver-HAL，不能复用本偏差标志。

## DEV-046 P3-W05 approval interrupt 尚未形成 durable Graph approval

P3-W05 的 `ApprovalInterruptRecord`、`ApprovalInterruptExecutor` 与 `ApprovalResumeValidator` 位于 Runtime main
source，作为后续 Graph checkpoint/restart 的稳定合同。它已绑定 owner/session/plan/node/action/context/policy/
Safety digest、expiry 与 trusted authority decision，并在 resume 时重新检查 Context、Policy、Capability 和
Safety State。API 33 ARM64 probe 只在单进程内证明状态转换、codec 和 fail-closed 校验。

当前 Room schema 保持 v4，既有 `DurableApprovalRepository` 未修改；`AgentGraphRuntime`、Runtime/Governance
Service、Client2 approval UI 均未引用该合同。没有 unique transaction、process-death restore、terminal race
settlement 或 production grant authority。`Durable` 在工作包名称中指目标语义和 checkpoint-ready record，不是
当前持久化完成声明。

P3-W05 同时把 checkpoint envelope `createdAt` 改为 canonical decimal string，修复当前 epoch 超过 bounded
primitive integer 的缺陷；由于 serializer 尚未写入 Graph/Room，不需要迁移已发布 durable row。后续若形成
稳定外部 checkpoint artifact，schema/version migration 必须显式管理。

状态：`Accepted Temporary`。`approval_interrupt_record_defined=true`、
`approval_interrupt_persistence_wired=false`、`approval_grant_service_published=false`、
`agent_graph_executor_dispatch_enabled=false`、`effect_dispatch_enabled=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`。P3-W09 必须接 Room transaction/restart recovery；
P3-W06..W08 接 Effect/verification/compensation；P8 另行关闭 Vehicle/NPU/Driver-HAL，不能复用本偏差标志。

## DEV-047 P3-W06 EffectCoordinator 尚未形成 durable production Effect pipeline

P3-W06 在 Runtime main source 新增 immutable `EffectBatch`、deterministic `EffectDependencyPlanner`、exact-profile
`AdapterRegistry` 与 two-phase `EffectCoordinator`。合同会在任何 dispatch 前 prepare 全部 Effect；required prepare
失败使整批零下发，optional prepare 失败允许降级。dependency 和 resource wave 防止有序依赖被提前执行，并为未来
无冲突并发提供确定性计划；每项都形成独立 P1 typed `EffectObservation`。

当前 Coordinator 是 caller 驱动的单进程对象，不接 `AgentGraphRuntime`、Room/outbox transaction、Binder Service、
P2 debug simulation registry 或 production vehicle adapter。prepared payload/envelope 只在单次调用内短暂存在；
before-state 仅以 digest 返回，没有 durable snapshot。Adapter APPLIED 只映射到 DELIVERED，不调用 `queryStatus`，
不宣称 APPLIED/VERIFIED；P3-W07 已提供独立 verifier/reconciler，但尚未由 Coordinator 调用。

状态：`Accepted Temporary`。`effect_batch_defined=true`、`effect_dependency_plan_verified=true`、
`effect_prepare_all_required_verified=true`、`effect_independent_observation_verified=true`、
`effect_coordinator_graph_wired=false`、`effect_coordinator_persistence_wired=false`、
`production_effect_adapter_registered=false`、`production_effect_dispatch_enabled=false`、
`effect_verification_reconciliation_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`。P3-W07 已增加独立 verification/reconciliation，P3-W09 接 durable outbox/restart；
P8 仍需由目标平台 owner 提供 vehicle API/permission/readback，不能用 debug registration 代替。

## DEV-048 P3-W07 Effect verification/reconciliation 尚未形成 durable production readback loop

P3-W07 在 Runtime main source 新增 pure Java `EffectVerifier` 与 `DigitalTwinEffectReconciler`。Verifier 已实现五种
typed policy、target/spec digest、DELIVERED/APPLIED/VERIFIED 分层、deadline/trust fail-closed；Reconciler 已实现
linearizable status query、immutable Twin readback、UNKNOWN next-reconcile time、NOT_APPLIED confirmation 和 VERIFIED
no-query dedup。源码没有 `apply` 调用，因此本包不会重复下发 Effect。

当前 Reconciler 仍由 caller 单次调用，不持有 timer/thread，不写 Room/outbox，不接 `EffectCoordinator`、
`AgentGraphRuntime` 或 Binder。Process-local `VehicleDigitalTwinStore` 只有 debug/test SIMULATED evidence，
`productionTrusted=false`；PRODUCTION profile 在 query 前失败关闭。Twin snapshot 没有 before/composite target set 时，
STATE_TRANSITION/COMPOSITE 只在 direct verifier contract 可验证，Reconciler 保持 UNKNOWN，不能伪造完成。

状态：`Accepted Temporary`。`effect_verifier_defined=true`、`effect_state_separation_verified=true`、
`effect_unknown_reconciliation_verified=true`、`effect_verified_redispatch_blocked=true`、
`effect_verification_reconciliation_runtime_wired=false`、`effect_verification_scheduler_wired=false`、
`effect_verification_persistence_wired=false`、`effect_verification_production_readback_wired=false`、
`effect_verification_graph_wired=false`、`production_effect_dispatch_enabled=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`。P3-W09 必须完成 durable timer/Room/restart wiring；P8 必须由
目标平台 owner 提供可信 target readback/API/permission/Safety evidence，不能用 process-local Twin 或 debug adapter 关闭。

## DEV-049 P3-W08 Compensation/Undo 尚未形成 durable execution，P1 compensation state 不可达

P3-W08 在 Runtime main source 新增 pure Java `CompensationPlanner` 与名为 `UndoService` 的 process-local
admission object。Planner 已用显式 reversible policy、VALID before snapshot、prepared-before digest、absolute target、
source-bound idempotency 和 reverse dependency wave 形成新的 compensation plan；Undo admission 已用 P1 handle TTL/
digest、Context/Policy/capability/Safety 复验和 bounded idempotency record 创建新的 governed task。它不修改原
VERIFIED observation，也不调用 adapter。

现有 P1 V1 存在需要后续协议演进的矛盾：`EffectContract` 把 VERIFIED 定义为 immutable terminal，
`validateTransition` 因而禁止 VERIFIED -> COMPENSATING；同时枚举和早期详设状态图又保留 COMPENSATING/
COMPENSATED。P3-W08 按“Undo 是新操作”的冻结文字合同处理，不通过放松 terminal 规则或修改 AIDL/hash 来隐藏
矛盾。新的 governed task 未来必须有独立 Compensation operation/state contract；原 Effect 只保留来源证据。

当前 before snapshot、Governance/Safety trust 和 64 条 admission record 都是 caller/process-local；没有 Room
transaction、process-death replay、Graph node transition、Binder API、production authority、adapter compensate/apply
或 completion observation。PRODUCTION 固定拒绝，API 33 ARM64 probe 只证明软件合同可运行。

状态：`Accepted Temporary`。`compensation_planner_defined=true`、
`compensation_absolute_before_verified=true`、`compensation_reverse_dependency_verified=true`、
`undo_new_governed_task_verified=true`、`compensation_undo_runtime_wired=false`、
`compensation_undo_persistence_wired=false`、`undo_binder_service_published=false`、
`compensation_dispatch_enabled=false`、`production_compensation_authority_wired=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`。P3-W09 必须把 task/handle/
before reference/idempotency 接入 durable transaction 和 restart reconcile；后续 Runtime Contract v2+ 必须冻结独立
compensation state wire 语义；P8 仍需 OEM/Vendor rollback capability、Safety authority 和目标故障证据。

## DEV-050 P3-W09 Restart recovery repository 尚未接 Runtime/Binder，P3 durable 名称不能解释为 production activation

P3-W09 已在 Runtime main source 增加 pure Java `GraphRestartReconciler` 和 Room v4
`DurableGraphRecoveryRepository`。Reducer 已覆盖 WAITING/EXECUTING/UNKNOWN、checkpoint missing/mismatch/untrusted、
Effect delivery reconcile、approval/undo revalidation、deadline 和 Governance-revalidated continuation；Repository 已实现
bounded Plan/Node/Effect/Compensation projection、transactional Plan/Node state apply 和 digest-only exactly-once audit。
Android 13 ARM64 probe 通过两次 `force-stop` 验证 Room reopen、result digest replay 和 side-effect count 0。

与完整设计的偏差是：`CentralBrainRuntimeService` 启动路径和 `AgentGraphRuntime` 没有构造或调用这两个对象；没有
trusted checkpoint/effect/Governance evidence provider、Binder API、background scheduler、executor continuation 或
production Effect adapter。P3-W09 的 debug Activity 直接驱动 repository/reducer，仅证明合同与数据库边界可运行，
不能证明应用提交的真实 Graph 在进程死亡后自动恢复。早期 R4C1 对 legacy `runtime_task` 的 fail-closed cleanup 也不能
替代 Stage 2 Graph hydration。

状态：`Accepted Temporary`。`graph_restart_reconciler_defined=true`、
`graph_restart_room_v4_repository_verified=true`、`graph_restart_process_death_verified=true`、
`graph_restart_idempotent_reopen_verified=true`、`graph_restart_audit_exactly_once_verified=true`、
`graph_restart_historical_digest_replay_verified=true`、
`graph_restart_side_effect_count=0`、`graph_restart_repository_implementation_available=true`、
`graph_restart_runtime_wired=false`、`graph_restart_binder_published=false`、
`graph_restart_executor_dispatch_enabled=false`、`graph_restart_effect_dispatch_enabled=false`、
`graph_restart_production_wired=false`、`agent_graph_runtime_persistence_wired=false`、
`production_effect_dispatch_enabled=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`。后续集成必须先提供可信 Evidence 和单 owner transaction/scheduler，再接 Runtime；
在此之前不得把 P3 foundation 状态提升为 production recovery。

## DEV-051 Client2 UI alias 仍是兼容边界，legacy static owner 已解除

Client2 既有 XML/Smali 用两段式 UI alias（如 `care.cold`），冻结的 Session V1 合同和 P2 scenario manifest
要求至少三段 qualified ID。真机首次迁移因此在 admission 前被 `SessionContract` 正确拒绝。修复没有放宽合同或
修改 AIDL/hash，而是在 Client2 bridge 内建立 12 项固定 alias -> canonical Session ID allowlist；其中已有 catalog
项映射到 `scene.comfort.cold.v1`、`scene.fatigue.assist.v1`、`scene.rest.nap.v1`，其余 canonical ID 只表示会话命名，
在 Runtime scenario catalog/Graph 未接入前不代表可执行场景。

P4-W02 已删除旧 `CentralBrainPanelController*.smali`。MainActivity 只保留一行 hook 调用 maintained Java
`CockpitControlCoordinator`；Coordinator 直接实现 typed callback、持有 `SessionConnection` 并在 Activity destroy
关闭，在重建时用 handle/cursor resume。`submit(...):boolean` 和三个 legacy default callback 仍留在 bridge 作为
Stage 1 binary compatibility API，但当前 Client2 不调用，且 `legacy_text_callback_authoritative=false`。

状态：alias 部分仍为 `Accepted Temporary`；static lifecycle owner 子项为 `Resolved`。
`client2_session_event_primary_api=true`、
`client2_scenario_alias_map_count=12`、`client2_legacy_submit_compatibility=true`、
`client2_session_reconnect_replay_verified=true`、`client2_session_duplicate_event_suppressed=true`、
`cockpit_hmi_state_reducer_implemented=true`、`cockpit_hmi_lifecycle_owner_java=true`、
`client2_smali_controller_retired=true`、`legacy_text_callback_authoritative=false`、`scenario_execution_enabled=false`、
`cockpit_demo_control_loop_implemented=false`、`service_dispatch_triggered=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`。alias 的关闭 owner 为可维护 HMI 源码或 OEM stable
navigation/scenario ID contract；canonical catalog/执行由 Runtime integration work package 关闭，不得由 HMI alias 映射冒充。

## DEV-052 P4-W02 checkpoint 是 app-private 恢复层，不是量产加密 HMI store

为在闭源 Client2 的 Activity/process recreate 后恢复已有 owner Session，P4-W02 使用 schema-versioned private
SharedPreferences 保存 panel visibility、UI/canonical alias、SessionHandle metadata、last sequence 和 opaque cursor。
该 checkpoint 不保存 utterance、snapshot summary、assistant/model text、event/vehicle payload、设备身份或日志；terminal、
expired、malformed state 不恢复。

该实现满足 debug APK 的确定性恢复，但不等同于量产 encrypted storage、multi-user/seat isolation、backup/restore policy、
keystore lifecycle 或 OEM HMI state owner。量产接入必须由 target owner 决定是否禁用 backup、采用 encrypted store、
处理用户切换和 OTA schema migration；在此之前不得把 checkpoint 计入 Memory 模块或 production security evidence。

状态：`Accepted Temporary`。`client2_hmi_checkpoint_resume_verified=true`、
`client2_hmi_checkpoint_text_persisted=false`、`memory_runtime_production_wired=false`、
`production_ready=false`、`target_hardware_validated=false`。关闭 owner 为 P4-W10/P8 target integration。

## DEV-053 P4-W03 固定 1920x1080 安全框和 unavailable 投影不是量产多屏 HMI

P4-W03 按当前 Client2 实体目标固定 panel 为 `(1264,160)-(1888,1048)`，主材质 alpha=0.60。Header 在 Context、
driving signal 和 vehicle adapter 未接时分别显示 `UNAVAILABLE`、`UNKNOWN · 受限`；HVAC drawer 已由 P4-W04
实现控制面，Seat drawer 已由 P4-W05 实现控制面。该范围解决当前 1920x1080 演示 UI 边界和 AIOS 四阶段可观察性，
但没有 density/rotation/
multi-display layout policy、OEM distraction rule、真实 source/quality/readback 或设备控制。

状态：`Accepted Temporary`。`cockpit_hmi_four_stage_shell_implemented=true`、
`cockpit_hmi_safe_frame_1920x1080_verified=true`、`cockpit_hmi_material_alpha=0.60`、
`cockpit_hmi_device_drawer_scaffolded=true`、`cockpit_hvac_surface_implemented=true`、
`cockpit_seat_surface_implemented=true`、`scenario_execution_enabled=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`。P4-W04/W05 已关闭设备 surface placeholder；P4-W08/W09
关闭 driving/multi-resolution；P8 目标 owner 关闭真实 source/readback。任何一项关闭前均不得把当前固定画布解释为量产 HMI。

## DEV-054 P4-W04 Session V1 HVAC 参数兼容层不是 versioned typed parameter transport

架构需求要求手动 HVAC 以 bounded parameter 和 `source=HMI_CONTROL` 进入统一 Governance/Effect 链。当前冻结
`SessionRequest` V1 只有 `utterance` 和 `SOURCE_HMI_BUTTON`，没有 typed parameter bundle 或 HMI_CONTROL 常量；修改
现有 AIDL/hash 会破坏已经发布的 Client2/Runtime V1 compatibility。

P4-W04 因此在 `Client2ScenarioBridge.openHvacSession` 内将 immutable `HvacControlIntent` 编码为 exact canonical
`HVAC1` grammar，固定映射 `manual.hvac -> scene.manual.hvac.adjust.v1`，并使用 V1 `SOURCE_HMI_BUTTON`。View 和
Coordinator 不接触 wire string；parser 拒绝未知/重复/非 canonical/越界字段；日志不记录参数。该 Session 当前只完成
admission，reported/source/quality 继续 unavailable/no evidence，Adapter/Effect/hardware dispatch 为 false。

状态：`Accepted Temporary`。关闭条件是发布 versioned Session parameter contract 和独立 `HMI_CONTROL` source，完成
SDK/Runtime 双端 version/hash negotiation、migration、replay/audit/隐私测试，并将 Client2 bridge 切换到 typed field；不得
回改冻结 V1。当前：`hvac_manual_bounded_parameter_wire=true`、`hvac_manual_typed_parameter_field=false`、
`cockpit_hvac_governed_manual_session=true`、`scenario_execution_enabled=false`、
`production_effect_dispatch_enabled=false`、`hardware_accessed=false`、`production_ready=false`。

## DEV-055 P4-W05 Session V1 Seat 参数和 approval 兼容层不是 versioned typed transport

架构需求要求手动 Seat target、`source=HMI_CONTROL`、Safety decision 和 approval 进入统一 Governance/Effect 链。当前冻结
`SessionRequest` V1 只有 `utterance`、seat-zone 和 `SOURCE_HMI_BUTTON`，没有 typed parameter bundle、HMI_CONTROL、
Safety Context binding 或 approval response；修改现有 AIDL/hash 会破坏已发布 Client2/Runtime V1 compatibility。

P4-W05 因此在 `Client2ScenarioBridge.openSeatSession` 内将 immutable `SeatControlIntent` 编码为 exact canonical
`SEAT1` grammar，固定映射 `manual.seat -> scene.manual.seat.adjust.v1`。View/Coordinator 不拼接 wire string，parser
拒绝未知、重复、非 canonical、越界及 heat/vent 同时非零字段，日志不记录参数。UNKNOWN_RESTRICTED/MOVING driver
position 在 reducer 保持 desired 不变；trusted parked rest 只进入 WAITING_APPROVAL。两者都不构成 Runtime Safety authority，
也不 dispatch Adapter/Effect/hardware。

状态：`Accepted Temporary`。关闭条件是发布 versioned typed Session parameter、HMI_CONTROL、Safety Context revision 和
approval response contract，完成 SDK/Runtime 双端 negotiation、replay/audit/隐私测试，并在 dispatch 前重新校验 Context；
不得回改冻结 V1。当前：`seat_manual_bounded_parameter_wire=true`、`seat_manual_typed_parameter_field=false`、
`cockpit_seat_governed_manual_session=true`、`cockpit_seat_unknown_restricted_fail_closed=true`、
`scenario_execution_enabled=false`、`production_effect_dispatch_enabled=false`、`hardware_accessed=false`、
`production_ready=false`。
