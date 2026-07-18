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
| DEV-056 | P4-W06 HMI 已能投影完整 typed timeline 合同，但当前 Runtime 只发布 Session 事件，未发布 Plan/Action/Effect/Observation。 | S2-UX-001, S2-HMI-003/006, S2-EVT-001, ISSUE-022/026/033 | Accepted Temporary |
| DEV-057 | P4-W07 recovery UX 只能消费 Session/Event V1，无法接收 ApprovalPrompt、EffectObservation.retryable 或 UndoHandle。 | S2-UX-003, S2-HMI-003, S2-SAF-001, S2-EFF-001, ISSUE-022/026/029/033 | Accepted Temporary |
| DEV-058 | P4-W08 driving presentation 尚无 production trusted global Context；实体默认只能验证 UNKNOWN 受限模式。 | S2-UX-002, S2-HMI-002, S2-SAF-001, ISSUE-023/029/030/033 | Accepted Temporary |
| DEV-059 | P4-W09 工程抽屉只在 Client2 本地投影 debug Controller 已确认状态；它不是 production Context、Safety 或 Effect authority。 | S2-HMI-004, S2-ADP-001, S2-OBS-001, ISSUE-023/029/030/033 | Accepted Temporary |
| DEV-060 | P4-W10 Client2 catalog device role 是 HMI 同步投影，不是 Runtime 发布的 Plan、Effect target 或车辆回读。 | S2-HMI-001..006, S2-SCN-001, ISSUE-022/026/030/033 | Accepted Temporary |
| DEV-061 | P4-W11 只认证三个固定横屏 profile 和 0.85..1.30 fontScale，不是 OEM 多屏/无障碍量产认证。 | S2-UX-003, S2-HMI-001/002, ISSUE-019/033 | Accepted Temporary |
| DEV-062 | P4-W12 关闭 application aggregate acceptance，但自动 Plan/Effect、approval/undo/readback 和 HMI-D4 仍未完成。 | S2-UX-001..003, S2-HMI-001..006, ISSUE-022/026/030/033 | Accepted Temporary |
| DEV-063 | P5-W01 Tool manifest/schema 是静态合同，不是注册、健康或执行。 | S2-TOL-001, ISSUE-036 | Accepted Temporary |
| DEV-064 | P5-W02 pure-Java Registry/Resolver 的 USABLE 不是 Runtime publication 或 execution authority。 | S2-TOL-001, S2-SAF-001, ISSUE-036/037 | Accepted Temporary |
| DEV-065 | P5-W03 rule/model/USABLE 交集只是静态 selection，不是 approval、Runtime publication 或 execution authority。 | S2-TOL-001, S2-SAF-001, ISSUE-036/037/038 | Accepted Temporary |
| DEV-066 | P5-W04 只执行同进程 built-in；signer evidence 由调用方输入且 cancel/deadline 依赖 cooperative checkpoint，不是 production Tool authority。 | S2-TOL-001, S2-SAF-001, ISSUE-036/039 | Accepted Temporary |
| DEV-067 | P5-W05 只验证调用方提供的 digest evidence；不获取平台 signer、不验证签名链、不加载或执行 Skill package。 | S2-TOL-001, S2-SAF-001, FW-U-008, ISSUE-036/040 | Accepted Temporary |
| DEV-068 | P5-W06 Working Memory 保存 process-local opaque bytes 并做 best-effort array wipe；尚无 Runtime session hook、durable encryption、tokenizer binding 或 model publication。 | S2-MEM-001, S2-SAF-001, ISSUE-025/031/041 | Accepted Temporary |
| DEV-069 | P5-W07 Profile Memory 只有 contract-test encryption owner 和 process-local sealed bytes；debug/test XOR 不是 production encrypted storage、Keystore/TEE 或 secure erase。 | S2-MEM-001, S2-SAF-001, ISSUE-025/031/041/042 | Accepted Temporary |
| DEV-070 | P5-W08 只保存 process-local typed episode summary/result；catalog、policy/read/erase authority、durable encrypted repository 与 Runtime/model publication 未完成。 | S2-MEM-001, S2-SAF-001, ISSUE-025/031/041/042/043 | Accepted Temporary |
| DEV-071 | P5-W09 只根据受信 token/byte metadata 生成预算指令；没有 production tokenizer、summary/truncation executor、budget authority 或 Runtime/model composition。 | S2-MEM-001, S2-MDL-001, S2-SAF-001, ISSUE-041/043/044 | Accepted Temporary |
| DEV-072 | P5-W10 只更新 process-local Memory consent HMI projection；没有 production consent authority、repository mutation 或 Runtime/model publication。 | S2-MEM-001, S2-UX-003, S2-SAF-001, ISSUE-041/042/043/045 | Accepted Temporary |
| DEV-073 | P6-W01 `InProcessDurableEventBroker` 只有 process-local append/replay；required 类名不代表 process-death durability 或 DDS middleware。 | S2-EVT-001, S2-SAF-001, ISSUE-025/034/046 | Accepted Temporary |
| DEV-078 | P7-W01 只冻结 digest-only ModelRequest/Result v2；未接 Provider registry/router、真实内容、模型/NPU 或 Runtime。 | S2-MDL-001, S2-SAF-001, ISSUE-024/044 | Accepted Temporary |
| DEV-079 | P7-W02 health 仅为 source/revision/freshness 元数据；HEALTHY 不能激活未实现的 local/vendor/cloud provider。 | S2-MDL-001, S2-SAF-001, ISSUE-024 | Accepted Temporary |
| DEV-080 | P7-W03 route decision 只做 admission metadata；不调用 Provider/model，不授予 action/Effect authority。 | S2-MDL-001, S2-SAF-001, ISSUE-024 | Accepted Temporary |
| DEV-081 | P7-W04 LocalModelProvider 仅存在于 debug source set；不是 release/production inference 或 Vendor NPU fallback。 | S2-MDL-001, S2-SAF-001, ISSUE-024 | Accepted Temporary |
| DEV-082 | P7-W05 只验证一次性进程内结构化模型候选；未接 Provider/Runtime、repair/evaluation 或生产 action authority。 | S2-MDL-001, S2-SAF-001, ISSUE-024 | Accepted Temporary |
| DEV-083 | P7-W06 evaluator 是固定 synthetic corpus 的离线摘要评测；不是生产模型调用、数据集质量认证或硬件验收。 | S2-MDL-001, S2-SAF-001, S2-OBS-001, ISSUE-024 | Accepted Temporary |
| DEV-084 | P7-W07 只消费 caller-owned resource/thermal metadata；没有可信 producer、Runtime wiring 或 Vendor NPU。 | S2-MDL-001, S2-SAF-001, S2-OBS-001, ISSUE-024 | Accepted Temporary |
| DEV-085 | P8-W01 只完成发现合同、矩阵模板和只读脱敏采集工具；目标 property/service/permission evidence 仍缺失。 | S2-ADP-002, S2-OBS-001, ISSUE-024/027/030/047 | Accepted Temporary |
| DEV-086 | P9-W01 只冻结 initial software budgets 并验证合成报告；没有目标测量、owner approval 或量产性能资格。 | S2-OBS-001, S2-REL-001, ISSUE-048 | Accepted Temporary |
| DEV-087 | P9-W02 只冻结并合成验证 18-case matrix；没有真实 fault injection、72h 运行或目标稳定性资格。 | S2-REL-001, S2-OBS-001, ISSUE-049 | Accepted Temporary |
| DEV-088 | P9-W03a 只提供三个 Java boundary 的 deterministic host corpus；不是 coverage-guided fuzz、AIDL/signature review 或目标安全资格。 | S2-SAF-001, S2-TOL-001, S2-OBS-001, ISSUE-050 | Accepted Temporary |

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

会话注册表最初提升为 Runtime 进程级 singleton，使显式 unbind/rebind 的 Service 实例重建不丢 active
session；P1-W06 随后用 `DurableSessionRegistry` 替换 production endpoint。以下是 P1-W05 原始边界，
不是当前累计状态：

```text
session_runtime_transient_registry=true
session_runtime_persistence_wired=false
session_runtime_process_death_rehydration=false
scenario_execution_enabled=false
```

Event V1 的 terminal page 禁止 `nextCursor`，facade 暂时重用该页的 request cursor 并按 sequence 去重
callback replay；该兼容策略由 `ISSUE-034` 跟踪，不能描述为 durable/high-volume broker。

2026-07-19 生命周期审计还发现健康 reconnect 原先只清空 transport callback map，未调用旧 Event
Binder 的 unregister；这不是被接受的架构偏差，已直接修复。当前 detach 在 unlink/unbind 前对称注销，
跨 generation 注册竞态立即撤销，API 33 ARM64 连续 6 次重连通过。仍接受的偏差仅是 app-layer
Service/system placement 与 Event V1 terminal cursor，不包含 callback 配额泄漏。

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

## DEV-056 P4-W06 timeline projection 完整但 Runtime execution event 未发布

P4-W06 的 `CockpitExecutionTimeline` 已实现 Plan/Action/Approval/Effect/Observation/Compensation allowlisted typed-event
projection，并严格根据 observation outcome/quality 区分 APPLIED、VERIFIED、MISMATCH、NO_EVIDENCE 和 UNAVAILABLE。
但当前 `DurableSessionRegistry` 只发布 Session lifecycle/`ScenarioRequested`，`activePlanRevision=0`，没有把 P2/P3
process-local Scenario/Graph/Effect foundation 接入 Session Event runtime。

因此实体 Client2 只能真实显示 Session admission、Plan NOT PUBLISHED、Graph NOT WIRED、Effect NOT DISPATCHED、Readback
UNAVAILABLE。host test 使用合同内 typed event 验证未来投影，不是实体 Runtime execution evidence；Media/Nav 默认
UNAVAILABLE。禁止通过 assistant text、desired state、固定 summary 或 debug Adapter 把页面推进至 APPLIED/VERIFIED。

状态：`Accepted Temporary`。关闭条件是 Runtime 以 version/hash 兼容方式发布持久化、连续 sequence、owner-scoped typed
Plan/Action/Effect/Observation event，接入 Graph/Effect coordinator/readback，并在 Android 13 process-death/replay 下证明
幂等和证据一致；生产 Adapter/车辆 readback 仍由 `ISSUE-030` 独立关闭。当前：
`cockpit_execution_timeline_implemented=true`、`cockpit_execution_typed_event_projection=true`、
`cockpit_execution_plan_published=false`、`cockpit_execution_effect_dispatch_enabled=false`、
`cockpit_execution_readback_available=false`、`production_ready=false`。

## DEV-057 P4-W07 recovery command details 未发布到 Client2

P4-W07 的 `CockpitRecoveryState` 已从 validated Session/Event 投影 approval status、终态 effect evidence、partial aggregate
和 compensation status，并将 approve/reject/retry/undo 命令纳入 UI。但是冻结 Event V1 的 Approval 事件为
`PAYLOAD_NONE`，Effect event 只携带简化 `ObservationEvent`，当前 Client2 surface 也没有 `UndoHandle`/command service。

因此实体 HMI 只能可靠显示审批状态及最近 validated Action capability target；reason/expiry、retryable 和 undo eligibility
必须显示 UNAVAILABLE。四个命令保持 visible+disabled。host test 中的 future typed events 只验证 projection 逻辑，不能作为
实体 approval/Effect/undo publication 证据；compensation observation 也不能自行创建 undo authority。

状态：`Accepted Temporary`。关闭条件是发布 versioned Client2-compatible ApprovalPrompt/response、完整
EffectObservation retry metadata、UndoHandle/compensation admission service，绑定 owner/session/plan/context/policy digest 与 TTL，
完成 Room/replay/process-death/幂等/Android 13 ARM64 测试，并保持 production vehicle dispatch 独立受控。当前：
`cockpit_recovery_state_reducer_owned=true`、`cockpit_approval_response_service_published=false`、
`cockpit_retry_service_published=false`、`cockpit_undo_service_published=false`、
`cockpit_recovery_commands_enabled=false`、`production_ready=false`、`target_hardware_validated=false`。

## DEV-058 P4-W08 driving presentation lacks production trusted global Context

P4-W08 已在 Client2 增加 pure Java `DrivingUxPolicy` 与 immutable `PanelPresentationMode`，并把 null、UNKNOWN、MOVING、
unavailable、非 OBSERVED 或无有效 revision 的 Context 全部映射到受限呈现。受限模式隐藏长文本、禁用 HVAC/Seat 参数编辑
和高风险休息场景；PARKED_FULL 只在可信已观测 Context 下恢复呈现。两种模式都明确不能作为 Effect 授权来源。

当前实体 Client2 尚未绑定 production trusted global Context provider，也没有可由测试脚本安全切换的受保护工程师入口。
因此 P4-W08 实体证据只能证明默认 UNKNOWN 受限路径，不能在本轮重新证明 PARKED_FULL 下的 HVAC/Seat manual Session。
历史 P4-W04/P4-W05 的实体 manual admission 证据仍保留，但当前 run 将
`cockpit_hvac_manual_session_admission_retested=false` 和
`cockpit_seat_manual_session_admission_retested=false` 明确公开，禁止为通过验收注入假驻车状态。

关闭条件：P4-W09 通过 signature/capability 保护的工程师仿真抽屉接入现有 debug Context Controller，在实体 Android 13
上分别验证 UNKNOWN/MOVING/PARKED 呈现、PARKED 参数入口、Context reset 以及 Runtime Policy 独立；production Context/
Safety authority 和真实车辆信号仍由 P8 与 `ISSUE-023/029/030` 关闭。

状态：`Accepted Temporary`。`vehicle_signal_provider_wired=false`、`production_ready=false`、
`target_hardware_validated=false`、`driver_development_triggered=false`。

## DEV-059 P4-W09 debug Context projection is not production authority

P4-W09 将 Client2 通过显式 debug Binder 连接到 P2-W12 `DebugSimulationController`。Controller 继续由 signature permission、
caller identity、debug capability 和 AIDL version/hash 保护；Client2 只在命令成功且 revision 严格递增后，把已确认的
PARKED/MOVING/UNKNOWN、occupancy、belt、adapter fault 投影进 immutable `CockpitEngineerState`。这使实体 Android 13
可以验证 P4-W08 的完整/受限呈现矩阵和 fault UI，但不改变 Runtime 的生产数据流。

当前 projection 是 Client2 application-local SIMULATED Context，不写入 shared ContextSnapshot、Room、Graph、Policy、
EffectCoordinator 或 vehicle provider。Runtime release variant 不含 Controller Service；production capability policy 不授予
`debug.simulation.control`。因此 PARKED_FULL 仍只代表 UI 可编辑，不能授权 Seat/HVAC Effect，也不能作为车辆状态证据。

关闭条件：P8 接入经 OEM 确认的 production vehicle signal provider、freshness/trust/revision、Safety owner 和 dispatch 前重验；
Client2 改为只消费该权威 Context 的版本化只读接口，并完成真实 property/service/permission/area/readback 的目标验收。关闭
`DEV-059` 不得仅删除 debug 抽屉或把 SIMULATED source 重命名为 TARGET。

状态：`Accepted Temporary`。`cockpit_engineer_simulation_drawer_implemented=true`、
`cockpit_engineer_runtime_release_service_absent=true`、`cockpit_engineer_effect_authorization_source=false`、
`cockpit_engineer_production_available=false`、`vehicle_signal_provider_wired=false`、`production_ready=false`、
`target_hardware_validated=false`。

## DEV-110 P8 public target inventory is not capability mapping

P8-W01 collector 已在 identity-redacted Android API 33 目标上完成只读公开 inventory。仓库只记录 69 项 feature、274 项
shell-visible Binder service、2 项 car 关键词匹配、265 项 command service、Automotive feature 布尔值、隐私确认和内部 evidence
reference；原始设备身份与 service 名留在仓库外。

该证据只关闭公开 surface inventory 采集子项。它没有公开 CarProperty ID/area/type/access、Vendor AIDL/SDK、permission/signature
owner、interface version 或 readback/fault/rollback semantics，因此八项 capability matrix 仍全部 EXTERNAL_BLOCKED。不得由 service
可见性推断可调用性、写权限、车辆 Effect authority、Vendor NPU 或 Driver/HAL gap。

状态：`Accepted Temporary`。`target_public_inventory_collected=true`、`target_public_inventory_identity_redacted=true`、
`target_public_inventory_privacy_confirmed=true`、`target_public_inventory_api33_verified=true`、
`target_capability_matrix_complete=false`、`production_adapter_registered=false`、`driver_development_triggered=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`。tracking：`ISSUE-047`。

## DEV-109 P9 debug probe acceptance is not production hardening qualification

P9-W01/W02/W03c/W04c/W05b/W06b/W07b 的 debug application probes 已在 Android 13 ARM64 统一执行通过。该证据只证明
synthetic budget/matrix、security boundary、privacy redaction、release metadata、driver-safety contract 和 field diagnostics
projection 可在目标 API/ABI 加载并失败关闭；不证明真实 workload、72h、fuzz、owner policy、正式 signer/installer、OEM Safety
State、车辆 Effect 或 target retest 完成。

状态：`Accepted Temporary`。历史文档中的 ADB-offline 记录是 probe 开发时状态，以本次 nonce-bound installer evidence 为当前状态。
应用探针使用专用 marker，避免提升 broader qualification：`performance_budget_contract_probe_android13_arm64_verified=true`、
`stability_matrix_contract_probe_android13_arm64_verified=true`、`security_boundary_probe_android13_arm64_verified=true`、
`privacy_redaction_probe_android13_arm64_verified=true`、`release_metadata_probe_android13_arm64_verified=true`、
`driver_safety_android_contract_probe_android13_arm64_verified=true`、`field_diagnostics_probe_android13_arm64_verified=true`。

`performance_budget_target_measurement_complete=false`、`stability_target_72h_complete=false`、
`security_coverage_guided_fuzz_complete=false`、`privacy_owner_policy_approved=false`、
`production_signer_owner_approved=false`、`driver_safety_android13_arm64_verified=false`、
`field_diagnostics_target_category_execution_complete=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false` 保持不变。

## DEV-108 P7 debug probe acceptance is not production inference or model quality

P7-W01..W07 已在 Android 13 ARM64 上通过 build-owned debug Activity 与统一安装回归。证据证明模型 DTO、Registry、Router、
development-only Provider、output validator、offline evaluator 和 resource admission 在目标 ABI/API 上可执行，不证明 production
Provider/Router/Runtime、真实 prompt/model output、云网络、Ollama、Vendor NPU、目标性能或模型质量已接入。

偏差状态为 `Accepted Temporary`。LocalModelProvider 仍只存在于 debug source 并使用 injected engine；evaluation 仍是固定 synthetic
metadata；resource/thermal snapshot 仍由调用方提供。关闭偏差需独立提供 provider owner、模型/版本/评测数据治理、真实资源 producer、
NPU/网络接口、Runtime composition、安全/隐私/性能和 P8/P9 目标证据，不得仅提升 probe marker。

当前 `p7_android13_arm64_probe_acceptance_complete=true`、`production_model_provider_published=false`、
`production_model_router_wired=false`、`production_inference_enabled=false`、`production_model_output_runtime_wired=false`、
`production_evaluation_authority_published=false`、`production_resource_snapshot_provider_wired=false`、
`provider_invoked=false`、`model_invoked=false`、`network_accessed=false`、`npu_accessed=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`。tracking：`ISSUE-024/044`。

## DEV-107 P6 debug probe acceptance is not production Event or proactive authority

P6-W01..W06 已在 Android 13 ARM64 上通过 build-owned debug Activity 和统一安装回归。证据证明 process-local Event、QoS、
Trigger、Consent、Context normalization 与 Suggestion projection 的 Java 合同可在目标 ABI/API 上执行，不证明跨进程/跨 SOC
事件中间件、持久化 cursor/cooldown/preference、真实 Context provider、主动服务 owner authority 或自动 Effect execution 已完成。

偏差状态为 `Accepted Temporary`。当前保留独立 queue 未接 Broker、Trigger 未接 source/runtime、Consent 未接生产 authority、
Context registry 未发布、Suggestion 未接生产 source/voice/Client2。关闭偏差前必须分别提供 owner、持久化、Binder/DDS/SOME-IP、
真实 Vehicle source、production Runtime composition 和 P8/P9 目标证据；不得通过改 marker 消除这些缺口。

当前 `p6_android13_arm64_probe_acceptance_complete=true`、`production_event_middleware_published=false`、
`production_trigger_runtime_wired=false`、`production_proactive_authority_published=false`、
`production_context_source_registry_published=false`、`production_active_suggestion_source_wired=false`、
`production_runtime_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`。tracking：`ISSUE-031/046`。

## DEV-060 P4-W10 catalog participation is not Runtime Plan publication

P4-W10 把 Client2 原有 14 个 alias/canonical ID 收敛到 `CockpitScenarioControlState`，并根据已冻结的 cold/fatigue/rest
manifest 给 HVAC/Seat 标注 `CATALOG_REQUIRED` 或 `CATALOG_OPTIONAL`。manual HVAC/Seat 标注 `MANUAL_TARGET`，但仍只通过
冻结 Session V1 的兼容参数载体进入 `ScenarioClient`。同一 reducer 记录 Session lifecycle、active Plan revision 与 event
sequence，解决四阶段和设备抽屉各自解释状态的问题。

该 role 只表示“catalog 预期涉及此设备”，不包含 Runtime 编译后的 PlanNode、typed target、Policy/Safety 结论、Effect
delivery 或 readback。当前 Runtime Snapshot 的 `activePlanRevision=0`，所以 UI 必须显示 Plan NOT PUBLISHED、Effect NOT
DISPATCHED、readback UNAVAILABLE。debug PARKED Context 只解锁测试呈现，不改变此边界。

状态：`Accepted Temporary`。关闭条件是 Runtime 将 P2 scenario resolver/compiler 接入 durable Session，发布 owner-scoped、
revisioned、可回放的 typed Plan/Action/Effect/Observation，并由生产 Adapter 与可信 readback 形成证据；Client2 随后删除
静态 device role，改为只消费 Runtime Plan。当前：`cockpit_scenario_plan_publication_inferred=false`、
`scenario_execution_enabled=false`、`production_effect_dispatch_enabled=false`、`target_hardware_validated=false`。

## DEV-061 P4-W11 display allowlist is not OEM multi-display qualification

P4-W11 新增 pure Java `CockpitDisplayPolicy`，只允许横屏 `1280x720@107dpi`、`1920x1080@160dpi` 和
`2560x1440@213dpi`，并把 `fontScale` 限定在 0.85..1.30。未列入的尺寸、density、方向、无效 metrics 或更大字体
必须禁用 Client2 导航入口并保持浮窗隐藏，不能通过缩放或猜测 OEM 参数继续运行。

Coordinator 在运行时为全部 Button 补齐 content description、focus/importantForAccessibility、至少 48dp 的像素等价值、
两行省略与 selected/stateDescription；XML 也提供静态 48dp 基线。实体 Android 13 ARM64 已覆盖三档 profile、1.30 字体、
最长中文、非颜色选中态与不支持 profile 失败关闭。

这只证明当前 APK 在定义矩阵内的应用层可达性与边界，不等价于 OEM 多显示器、旋转、自由 density、任意字体、TalkBack
完整巡检、驾驶分心法规或量产视觉认证。`CockpitDisplayPolicy.isEffectAuthorizationSource()` 始终为 false，显示 profile
不得参与车辆动作授权。状态：`Accepted Temporary`。关闭 owner 为 P9/OEM HMI 集成与目标平台无障碍认证；当前
`cockpit_display_matrix_defined=true`、`cockpit_accessibility_semantics_runtime_owned=true`、
`cockpit_display_matrix_android13_arm64_verified=true`、`cockpit_display_effect_authorization_source=false`、
`production_ready=false`、`target_hardware_validated=false`。

## DEV-062 P4-W12 application acceptance is not HMI-D4 execution closure

P4-W12 把 recovery、protected engineer fault、scenario/manual synchronization 和 accessibility/display 四个 Android 13
ARM64 子套件收敛到一个可恢复入口。每个子套件独立清空并检查 crash buffer，结束后重新启动 Client2 并获取 UI tree。
这证明应用集成、失败关闭和进程恢复可重复，不证明尚未发布的 Runtime 自动执行。

当前 natural/manual Session 仍只到 admission；`activePlanRevision=0`、Effect dispatch/readback 未发布。Media/Nav、approval、
partial、mismatch、undo/compensation 只有 host typed projection 和实体 unavailable/disabled 呈现。Runtime release 已证明无
debug simulation Service/adapter，但项目没有可宣称量产的 Client2 release artifact。因此 P4-W12 可以关闭 application
acceptance 工作包，但不得设置 `hmi_d4_demo_control_loop_complete=true`。

状态：`Accepted Temporary`。关闭条件是发布并持久化 Runtime Plan/Graph/Effect/approval/undo/readback 服务，使用 debug
simulated adapter 完成可重复演示闭环，再独立交付无工程抽屉的 production Client2 release artifact；真实车辆与 OEM 资格
仍由 P8/P9 关闭。当前：`p4_w12_application_acceptance_complete=true`、
`p4_plan_effect_projection_host_verified=true`、`p4_automatic_plan_runtime_published=false`、
`p4_production_effect_dispatch_enabled=false`、`client2_production_release_artifact_available=false`、
`hmi_d4_demo_control_loop_complete=false`、`production_ready=false`、`target_hardware_validated=false`。

## DEV-063 P5-W01 Tool contract is not Tool execution

P5-W01 在 Runtime main source 中增加了 typed `ToolManifest` 和 `ToolSchemaValidator`。它冻结 ID/version/owner、
input/output bounded scalar schema、capability、risk、timeout、idempotency 与 fail-closed health metadata，并提供 canonical
contract digest。debug probe 已实现；在 Android 13 ARM64 执行时只验证相同纯 Java 合同。本轮因 Windows 没有 ADB
interface 未执行，`tool_manifest_android13_arm64_verified=false`。

该结果不等价于 Tool 已 registered、resolved、healthy、usable 或 executed。Manifest 中只有 health check identity/freshness
要求，没有动态 health value；也没有 artifact/signature、Registry、RuleSolver、Executor、deadline cancellation、audit、
Effect dispatch 或 readback。尤其不能把 `capabilityId` 当作 Vendor property/API，不能把 schema validation 当作 Safety 授权。

同时修正了此前首页/路线图把 P5-W01 写成“Runtime scenario orchestration”的计划漂移：冻结 backlog 的 P5 是 Tool/Skill/
Memory，P5-W01 是 Tool manifest/schema，下一包是 P5-W02 Registry/Resolver。HMI 自动 Plan/Effect 闭环仍由 P3/P4 Runtime
wiring 与 P8 vehicle adapter 交叉关闭，不能借 P5 Tool 合同隐式完成。

状态：`Accepted Temporary`。关闭条件是 P5-W02..W04 完成 deterministic registry/resolver、rule intersection 和
signed built-in executor 边界，并单独接入 Runtime/Graph；真实车辆/NPU Tool 还需 P8 OEM/Vendor authority。当前：
`tool_manifest_contract_defined=true`、`tool_registry_published=false`、`tool_execution_enabled=false`、
`effect_dispatch_enabled=false`、`vehicle_readback_accessed=false`、`npu_accessed=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`。

## DEV-064 P5-W02 Registry usability is not execution authority

P5-W02 已实现 immutable family/version Registry、same-version digest conflict、最高兼容版本 Resolver 和动态 health
freshness。它将 REGISTERED、RESOLVED 与 USABLE 分开，并在最高版本 unhealthy 时返回 NOT_USABLE 而不降级。该结果关闭
版本选择和失败关闭的软件合同，不表示 Runtime 已发布 Tool catalog 或健康服务。

当前 Registry 由测试直接构造，不来自 signed artifact、build-owned production catalog 或受治理 Service；HealthSnapshot
也由调用方一次性提供，没有 production publisher、attestation、process ownership 或持久化。`USABLE` 只表示 Manifest
与 supplied health evidence 满足 P5-W02 约束，不能跳过 P5-W03 rule intersection、P5-W04 executor admission、Governance、
Safety、deadline/cancel/audit 或 Adapter readback。

debug probe 中的两个 Manifest 是合同样本，`tool_registry_probe_registration_count=2` 不得解释为 production Tool 已注册。
代码没有接 `CentralBrainRuntimeService`、AgentGraph、Binder、Room、Effect、Vehicle、Model/NPU、network 或 Driver/HAL，
`Resolution.isExecutionEnabled()` 固定 false。

状态：`Accepted Temporary`。关闭条件是确定 build/signer/health publisher owner，P5-W03..W05 完成规则、signed built-in
executor 和 artifact trust，并以独立 Runtime composition root/capability/Safety/evidence 发布；真实车辆/NPU Tool 仍需 P8
OEM/Vendor authority。当前：`tool_registry_contract_defined=true`、`tool_registry_published=false`、
`tool_registry_runtime_wired=false`、`tool_execution_enabled=false`、`production_tool_registered=false`、
`effect_dispatch_enabled=false`、`vehicle_readback_accessed=false`、`npu_accessed=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`。

## DEV-065 P5-W03 rule selection is not execution authority

P5-W03 已实现 init/child/conditional/terminal/required-before-exit/requires-approval 六类 immutable rule，并将静态 allowset
与 model-selected family、P5-W02 RESOLVED/USABLE family 做确定性交集。该结果关闭规则边界与空集失败关闭的软件合同，
不表示模型可信、条件可信、审批已完成或 Tool 可以执行。

当前 RuleSet 由 test/debug sample 直接构造，不来自 build-owned production catalog、signed Skill artifact 或受治理 Runtime
composition root。ConditionSnapshot 也由调用方直接提供，没有 source trust、Plan/Context version binding、publisher identity
或 freshness。requires-approval 只生成 annotation；`isApprovalGranted()` 和全部 execution flag 固定 false。

代码没有接 `CentralBrainRuntimeService`、AgentGraph、Binder、Room、ToolExecutor、Effect、Vehicle、Model/NPU、network 或
Driver/HAL。debug probe 的四个 sample Tool family 和 rule type count=6 只验证合同，不能解释为 production RuleSet 或 Tool
已发布。模型选择不能扩展 allowset，也不能触发重试模型、fallback Tool 或 synthetic condition。

状态：`Accepted Temporary`。关闭条件是 ISSUE-038 确认 rule catalog/condition/approval owner，P5-W04/W05 完成 signed
built-in executor 与 artifact trust，并以独立 Runtime/Graph composition、Plan/Context/Policy binding 和 audit 证据发布；
Vehicle/NPU Tool 仍需 P8 OEM/Vendor authority。当前：`tool_rule_set_contract_defined=true`、
`tool_rule_solver_published=false`、`tool_rule_solver_runtime_wired=false`、`tool_approval_authority_available=false`、
`tool_execution_enabled=false`、`production_tool_registered=false`、`effect_dispatch_enabled=false`、
`vehicle_readback_accessed=false`、`model_invoked=false`、`npu_accessed=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## DEV-084 P7-W07 admission uses caller-owned resource metadata

P7-W07 新增的 `ModelResourceAdmission` 完成了 request/route/policy/resource/context 绑定、foreground priority、热/资源降级和既有
scheduler composition，但 `PolicySnapshot/ResourceSnapshot` 都由调用方构造。当前没有可信 Runtime owner 从 Android thermal service、
Vendor NPU SDK 或目标资源管理器发布这些 snapshot。

因此 `AVAILABLE/CONSTRAINED/HOT` 等值只能用于合同与 debug probe，不能解释为目标硬件事实。实现也没有调用 Provider、claim lease、
执行模型或连接 Graph/Effect。状态：`Accepted Temporary`。关闭条件是 `ISSUE-024/P8` 冻结 producer owner/API/permission、monotonic
clock/revision、fault/freshness、NPU slot/memory/thermal semantics，并在目标 Android 13 ARM64 上完成故障与性能证据。

当前：`model_resource_admission_verified=true`、`resource_snapshot_producer_wired=false`、
`resource_admission_runtime_wired=false`、`model_resource_admission_android13_arm64_verified=true`、
`provider_invoked=false`、`model_invoked=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## DEV-089 P9-W03b host policy corpus is not Binder or APK crypto evidence

P9-W03b 新增 JSON/Java 三 surface、18-case policy corpus，并在 JVM 中调用既有 caller capability、durable principal、session
owner/replay 和 skill signer policy。它能证明 unresolved/package/current-signer/capability/shared-UID、request digest conflict、跨 owner
隔离及 signer state/epoch 的确定性失败关闭。

host test 构造的是 `CallerIdentitySnapshot`，没有控制 Android Binder driver 的 calling UID；signer digest 是确定性测试值，没有从目标
APK 重新提取并验证证书链。`SkillSignerPolicy` 也明确不获取 trusted signer evidence，不要求 hardware-backed attestation。因此当前结果
不能解释为 Binder spoof penetration test、目标 APK 密码学签名验证或 Android 13 ARM64 安全资格。

状态：`Accepted Temporary`。关闭条件是 W03c 在受控 debug-only instrumentation 中验证 calling UID/package/current signer acquisition、
跨进程 replay 和目标签名证据，并由安全 owner 审核；coverage-guided fuzz 仍按 ISSUE-050 单独完成。当前：
`security_identity_replay_corpus_defined=true`、`security_caller_policy_host_verified=true`、
`security_session_replay_owner_policy_host_verified=true`、`security_signer_policy_host_verified=true`、
`security_binder_calling_uid_spoof_android_verified=false`、
`security_package_signature_cryptographically_verified=false`、`security_android13_arm64_verified=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## DEV-090 P9-W03c static inventory and debug probe availability are not target fuzz evidence

P9-W03c 从仓库实际 public AIDL 树冻结 37 项清单，并复用八个既有 validation family；新增 JVM 聚合只提交 bounded
model/path/oversize 输入。debug-only probe 已扩展且 release manifest 无入口，installer 对 API/ABI 与 marker 失败关闭。

静态 inventory 证明 surface 可追踪，不证明 Binder driver、Parcel unmarshalling 或跨进程 callback 已被 fuzz。debug APK 可编译也不证明
probe 在当前目标执行；本轮先观察到 ADB `online=0/offline=1`，提交前复核为
`online=0/offline=0/unauthorized=0/other=0`。没有 coverage feedback、mutation/minimization、目标 signer remeasurement、真实
calling UID spoof、SELinux/permission attack evidence 或安全 owner approval。

状态：`Accepted Temporary`。关闭条件是 ISSUE-050 的 target fuzz 计划、设备可达、debug probe 执行、Binder/signature evidence 和 owner
review 完成。当前 `security_aidl_parcel_inventory_complete=true`、`security_host_path_oversize_aggregate_verified=true`、
`security_android_debug_probe_available=true`、`security_android_debug_probe_executed=false`、
`security_coverage_guided_fuzz_complete=false`、`security_binder_calling_uid_spoof_android_verified=false`、
`security_package_signature_cryptographically_verified=false`、`security_android13_arm64_verified=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## DEV-082 P7-W05 model output is proposal-only

P7-W05 冻结 `scenario-output.v1` 并用 ScenarioManifest 与 CapabilityCatalog 双重验证 scenario/capability/area/scalar。它接受 bounded
natural-language summary 作为一次性返回对象，但不记录 raw JSON/summary，也不产生 Plan、Effect、approval、Tool 或 vendor property。

该 validator 未接 LocalModelProvider、PolicyAwareModelRouter、Runtime/Graph 或 repair fallback；成功只表示 schema 合法，不表示意图正确、
模型质量合格、动作安全或硬件可用。状态：`Accepted Temporary`。关闭条件是 P7-W06/W07 完成 synthetic evaluation 与 resource admission，
P8/P9 冻结 production model/target authority 和真实故障性能证据。当前：`structured_model_output_verified=true`、
`model_output_no_action_authority=true`、`model_output_schema_runtime_wired=false`、
`structured_model_output_android13_arm64_verified=true`、`model_invoked=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## DEV-083 P7-W06 evaluator is offline and digest-only

P7-W06 新增固定 12-case synthetic corpus 与 process-local evaluator。case 只保存 intent/disposition、DrivingState、Safety freshness、
threat class、unavailable capability 和 digest；不保存 utterance、prompt、模型回复、summary、车辆值或设备身份。一次性 raw output 仅传给
P7-W05 validator，CaseResult 只保留 output digest、typed error、计数与时延/token metadata。

当前 1000/0 基准来自 deterministic synthetic expected output，不是模型准确率声明。harness 不构造 Provider、不调用 inference、不接
Runtime/Graph/Effect，也不能授予 action/approval/effect authority。状态：`Accepted Temporary`。关闭条件是 P7-W07 完成资源/热准入，
P8/P9 在目标硬件上用经批准的数据治理、模型/provider、真实性能与故障证据完成生产资格。当前：
`scenario_evaluation_verified=true`、`evaluation_case_count=12`、`scenario_evaluation_runtime_wired=false`、
`raw_evaluation_content_logged=false`、`scenario_evaluation_android13_arm64_verified=true`、`model_invoked=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## DEV-081 P7-W04 local provider is development-only

P7-W04 新增可执行 `LocalModelProvider`，但类和 probe 只存在于 Android debug source set。它通过显式
`createForDevelopment` 注入 in-process engine，提供 deadline/cancel、bounded stream/non-stream、typed fault/terminal/metrics；
probe 的 deterministic bytes 只验证 Provider contract，不是模型、Ollama、Vendor runtime 或质量证据。

fixed registry 的 `developmentAvailable` 更新为 true，因此 DEVELOPMENT route metadata 可在 fresh HEALTHY report 后选择该 ID。
这不改变 `productionImplementationAvailable=false`、`productionEligible=false`、`productionReady=false`。descriptor 还固定
`DEBUG_ONLY`、`FallbackClass.NEVER`、hardware=false；release source 不含 executable 类，Runtime/Governance Service 和 Router dispatch
均未接，Vendor NPU failure 不会回退到它。

状态：`Accepted Temporary`。关闭条件不是把 debug Provider 升格为生产实现，而是 ISSUE-024/P7-W05..W07/P8 分别完成 output schema/
validator、evaluation/resource policy、production publisher/composition、Vendor SDK/NPU Provider 和目标故障/性能证据；开发 Provider
继续保持隔离。当前：`local_model_provider_verified=true`、`local_model_provider_debug_only=true`、
`local_model_provider_release_source_absent=true`、`local_model_provider_runtime_wired=false`、
`local_model_provider_vendor_npu_fallback_enabled=false`、`local_model_provider_android13_arm64_verified=true`、
`production_inference_enabled=false`、`network_accessed=false`、`npu_accessed=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## DEV-078 P7-W01 ModelRequest/Result v2 is a contract, not production inference

原因：Stage 2 必须先冻结 purpose/privacy/latency/token/capability/fallback/trace 合同，才能让后续 Registry 和 Router 在不依赖
Vendor SDK 的情况下并行开发。当前 NPU、模型 artifact、provider health owner、cloud consent 和生产 routing 尚不可用。

偏差：`ModelContractV2` 只接受 digest 与 bounded metadata，并能构造 synthetic result 做合同验证。该 result 不代表模型被调用、输出
通过 schema 安全验证或 NPU 参与。v2 未替换旧 `ModelProvider`，也未接 Runtime/Graph/Effect。

约束：不得把 debug probe、completed result 或 host Gradle 成功描述为 inference/NPU/target hardware 证据。P7-W02/W03 必须分别发布
registry health 与 policy routing；P7-W05 必须完成 prompt/output schema 和 validator。Vendor path 仍受 `ISSUE-024` 限制。

状态：`model_contract_v2_defined=true`、`model_request_v2_fields_verified=true`、
`model_result_v2_binding_verified=true`、`model_privacy_fallback_fail_closed=true`、
`model_raw_content_accepted=false`、`model_provider_registry_wired=false`、`model_policy_router_wired=false`、
`model_contract_v2_android13_arm64_verified=true`、`model_invoked=false`、`npu_accessed=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。

## DEV-079 P7-W02 Registry health is metadata, not production availability

原因：黑盒目标尚无 production provider/health authority，但 P7-W03 Router 需要稳定目录与 stale-health 语义。P7-W02 因此只冻结
四项 build-owned catalog 和 process-local health metadata。

偏差：contract-test availability 为 1，不代表旧 deterministic Provider 已进入 production composition。P7-W04 后 Android local 有
debug-only implementation 且 development availability 为 1；vendor NPU/cloud 仍是 placeholder。即使测试发布 HEALTHY，三者的
production implementation/eligibility/routing 仍为 false。

约束：不得把 provider count、catalog capability、fresh health、debug probe 或 cloud network-required/vendor hardware-expected 标记
描述成模型/NPU/network/hardware 已接。生产 health source、atomic catalog publication、Provider lifecycle 与 Router 接线继续由
`ISSUE-024` 管理。

状态：`model_provider_registry_defined=true`、`model_provider_count=4`、
`model_provider_health_freshness_verified=true`、`model_provider_health_replay_verified=true`、
`model_provider_availability_separation_verified=true`、`model_provider_placeholder_fail_closed=true`、
`model_contract_test_available_count=1`、`model_development_available_count=1`、`model_production_ready_count=0`、
`model_provider_registry_android13_arm64_verified=true`、`model_provider_registry_runtime_wired=false`、
`model_policy_router_wired=false`、`model_invoked=false`、`network_accessed=false`、`npu_accessed=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。

## DEV-077 P6-W06 Active suggestion UX is a projection, not production orchestration

P6-W06 新增 process-local suggestion merge/cooldown/never-ask 与 debug HMI，但没有 production suggestion publisher、Trigger/Event
composition、Client2 overlay wiring、durable preference、voice engine、approval、Graph 或 Effect。固定“疲惫/冷”计划文案只用于显示设计，
不得被解释为已经控制空调或座椅。

状态：`Accepted Temporary`。关闭条件为 ISSUE-031/P8/P9 冻结 production suggestion event schema/owner、identity 与 driving state authority、
durable cooldown/never-ask、HMI/voice disclosure、Client2 integration、approval/undo/partial-failure、Graph/Effect receipt、vehicle readback 和
Android 13 fault evidence。当前：`active_suggestion_controller_defined=true`、
`active_suggestion_android13_arm64_verified=true`、`active_suggestion_hmi_projection_only=true`、
`active_suggestion_production_source_wired=false`、`active_suggestion_preference_repository_wired=false`、
`active_suggestion_voice_engine_wired=false`、`effect_dispatch_enabled=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## DEV-076 P6-W05 Context source adapters are contracts, not production providers

P6-W05 新增三项 source descriptor 与 pure-Java normalization adapter，但没有 source acquisition/polling/scheduling、Runtime health
producer、system clock authority、Event publication 或 Trigger composition。simulated vehicle adapter 只接受 P2 canonical
`SignalValue(source=SIMULATED)`；它存在于 main source 是为了共享 contract 编译与测试，不代表 release profile 注册 simulation provider。

状态：`Accepted Temporary`。关闭条件为 ISSUE-031/P8 冻结 production source registry owner、Runtime health schema/producer、clock/
timezone authority、vehicle SDK service/property/area/rate/fault contract、identity/policy evidence、Event publication、Trigger binding 与 Android
13 fault/restart evidence。若真实 vehicle API 暴露明确缺口，必须另行登记最小 Driver/HAL 工作，不得在本偏差中猜测接口。

当前：`context_source_adapter_contract_defined=true`、`context_source_count=3`、
`context_source_allowlist_verified=true`、`context_source_freshness_quality_verified=true`、
`context_source_android13_arm64_verified=true`、`context_source_production_registry_published=false`、
`context_source_runtime_wired=false`、`context_source_trigger_engine_wired=false`、
`vehicle_signal_provider_wired=false`、`vehicle_property_mapping_configured=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## DEV-075 P6-W04 process-local proactive consent is not production authorization

P6-W04 的 `ProactiveConsentPolicy` 只保存 process-local digest-only generic grant，`ConsentAuthority` 仅能由
`createForContractTest` 注入。PARKED、receipt/privacy digest、TTL 和 exact binding 证明软件合同失败关闭，不证明真实用户身份、
HMI disclosure、签名/attestation、跨重启撤销、账户切换或 production policy owner 已存在。

`POLICY_ELIGIBLE` 不是 Effect authorization；其 accessor 固定 false，Safety revalidation 固定 true。HIGH/CRITICAL 无法获得通用
grant，但真实 risk classification、single-use approval、vehicle state 与 dispatch-time interlock 仍未接入。代码没有连接
TriggerEngine、EventBroker、Binder、Room/file、Runtime/Graph、Effect、Vehicle、Model/NPU、network 或 Driver/HAL。

状态：`Accepted Temporary`。关闭条件是 ISSUE-031 冻结 consent/privacy owner、identity/scope、HMI/voice disclosure、durable grant
schema/clock/revoke、single-use high-risk approval、Runtime publication、Safety revalidation 和 Android 13 fault evidence。当前：
`proactive_consent_policy_defined=true`、`proactive_consent_android13_arm64_verified=true`、
`proactive_policy_process_local=true`、`proactive_grant_persistence_wired=false`、
`proactive_consent_authority_wired=false`、`proactive_auto_execution_enabled=false`、
`proactive_runtime_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## DEV-080 P7-W03 route decision is not model execution

P7-W03 新增 `PolicyAwareModelRouter`，能够对 fixed registry snapshot 执行 mode/health/privacy/network/thermal/latency/capability/quota
admission，生成 primary/fallback metadata 和 immutable rejection evidence。该命名中的 Router 不代表 endpoint dispatch、load balance、
retry execution 或 production model service。

当前 `PolicySnapshot` 全部由调用方构造，没有可信 Connectivity/Thermal/Resource/Quota producer、跨进程 revision authority、签名、
持久 budget ledger 或目标故障证据。route profile 的 minimum latency/thermal 属性是 build-owned policy 常量，不是硬件测量。
当前 production provider count 为 0，因此 production route 必须失败关闭；contract-test selection 也只返回 provider ID，不调用 Stub。

代码未接 Runtime/Governance Service、Scheduler、Provider instance、Graph、Effect、Vehicle、network、NPU、Binder 或 Driver/HAL。
状态：`Accepted Temporary`。关闭条件是 ISSUE-024/P7-W04..W07/P8 冻结 production policy/health/resource owners、Provider implementation、
quota consumption、fallback/retry/cancel、output validation、Runtime composition 和目标 Android 13/NPU 证据。当前：
`model_policy_router_defined=true`、`model_policy_router_android13_arm64_verified=true`、
`model_policy_router_runtime_wired=false`、`provider_invoked=false`、`model_invoked=false`、`network_accessed=false`、
`npu_accessed=false`、`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。

## DEV-074 P6-W03 process-local TriggerEngine is not production proactive intelligence

P6-W03 新增 build-owned manifest、deterministic threshold/window/sample-gap/debounce/cooldown state machine 和 digest-only
`ScenarioSuggestion`。这些约束能避免把原始高频流直接交给模型，也能证明本地规则输出不会直接 dispatch Effect。

当前 rule manifest 没有 production signature/activation owner；observation metric/quality/evidence 均由 contract-test composition 输入，
不代表真实 Vehicle/DMS/Context source。rule state、observation replay 和 cooldown 在进程重启后全部丢失，不能作为量产免打扰、频控、
跨用户/seat 或多 SOC 一致性证据。`createForContractTest` 是唯一 composition，Runtime 不引用 Engine。

跟踪偏差：P6-W02 合并时根 README、Android README 与自动化记忆曾把 P6-W03 下一项误写为 durable append/cursor；权威 backlog
与 roadmap 始终定义 P6-W03 为 TriggerRule。该标签漂移在 P6-W03 编码前纠正，没有产生 durable repository 实现，也没有提升
durability/production 状态。

代码未接 Binder/Room/file、P6-W01 Broker、Runtime/Graph、production source adapter、Effect、Vehicle、Model/NPU、network 或
Driver/HAL。状态：`Accepted Temporary`。关闭条件是 ISSUE-031/P6-W04/P6-W05 冻结 proactive consent/policy、trusted source owner、
durable cooldown/identity/privacy/audit 和 target Android fault evidence，再单独评审 Runtime publication。当前：
`trigger_rule_manifest_defined=true`、`trigger_rule_manifest_verified=true`、
`trigger_threshold_window_debounce_verified=true`、`trigger_cooldown_scope_verified=true`、
`trigger_input_fail_closed_verified=true`、`trigger_suggestion_only_verified=true`、
`trigger_engine_android13_arm64_verified=true`、`trigger_engine_process_local=true`、
`trigger_cooldown_persistence_wired=false`、`trigger_source_adapter_wired=false`、
`trigger_auto_execution_enabled=false`、`trigger_runtime_wired=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## DEV-071 P5-W09 decision-only context budget is not production model budgeting

P5-W09 的 `ContextBudgetManager` 只消费调用方提供的 token/byte size metadata，并按固定 category/priority/ID 规则返回
INCLUDE、SUMMARIZE_TO_BUDGET、TRUNCATE_TO_BUDGET 或 DROP。它不接收原始内容，不验证 token count，也不执行摘要、截断、
prompt composition 或模型调用。

该实现关闭 deterministic allocation 合同，不关闭 production model budgeting。缺失项包括 tokenizer family/version/digest、
size evidence source、budget/quota authority、content identity binding、summary/truncation executor、post-transform recount、Runtime/
model route composition、quality/privacy/evaluation 与目标硬件证据。

状态：`Accepted Temporary`。关闭条件是 ISSUE-044 发布上述 owner/contract，并完成 Runtime/model/NPU 目标侧验收。当前：
`context_budget_manager_defined=true`、`context_budget_category_allocation_verified=true`、
`context_budget_dual_limit_verified=true`、`context_budget_deterministic_overflow_verified=true`、
`context_budget_required_fail_closed=true`、`context_budget_android13_arm64_verified=false`、
`context_budget_decision_only=true`、`context_budget_text_payload_accepted=false`、
`context_budget_tokenizer_wired=false`、`context_budget_summarizer_wired=false`、
`context_budget_production_authority_wired=false`、`context_budget_runtime_wired=false`、
`model_invoked=false`、`npu_accessed=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。


## DEV-072 P5-W10 process-local Memory consent projection is not production Memory control

P5-W10 新增固定三来源、retained-memory 开关、profile preference clear、owner-bound evidence、replay/conflict、PARKED-only
management 和 debug interactive HMI。成功结果只改变 `MemoryConsentController` 当前进程中的 projection revision；它不调用
P5-W07/P5-W08 store，不持久化 consent，也不删除 Room/file/Keystore 数据。

debug Activity 使用 fixed allow authority 以验证 UI/API 状态机。该 authority 不是用户身份、签名、policy、revocation 或审计证据；
`MutationResult.isRepositoryMutationApplied=false` 恒成立。MOVING/UNKNOWN 的拒绝只证明本地 Car UX gate，不替代 production trusted
Context 和独立 Safety/Governance 复验。

代码未接 Binder Service、durable consent/repository、Runtime/Graph/model、Effect、Vehicle/NPU 或 Driver/HAL。状态：
`Accepted Temporary`。关闭条件是 ISSUE-045 发布 production identity/consent authority、HMI-to-Service contract、transactional
repository mutation/delete evidence、revocation/process-death/audit 和可信 driving Context，并完成 Android 13 目标验收。当前：
`memory_consent_controller_defined=true`、`memory_consent_source_visibility_verified=true`、
`memory_consent_disable_verified=true`、`memory_consent_preference_clear_verified=true`、
`memory_consent_moving_restriction_verified=true`、`memory_consent_android13_arm64_verified=false`、
`memory_consent_hmi_projection_only=true`、`memory_consent_repository_mutation_wired=false`、
`memory_consent_production_authority_wired=false`、`memory_consent_runtime_wired=false`、
`memory_consent_model_context_published=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## DEV-073 P6-W01 process-local Event Broker is not durable middleware

P6-W01 新增 fixed generic typed topic、per-topic cursor、append-before-notify、bounded replay/filter、owner subscription 与
identity/policy authority contract。它比 R6A1 的旧 bounded callback runtime 提供了更完整的 Stage 2 facade，但 required 类名
`InProcessDurableEventBroker` 不能作为 durability 证据。

当前 retention、publication replay 和 closed-subscription tombstone 都在单 JVM process 内；重启后丢失。旧 R6A2
`DurableEventCursorRepository` 没有注入本实现，也没有 transactionally bind event append、publisher sequence、subscription ACK
与 process-death recovery。P6-W01 callback 仍同步执行；P6-W02 的独立 queue/backpressure/QoS 控制器尚未接入 broker。

代码未接 Binder/AIDL Service、Room/file、DDS/SOME-IP/network、Runtime/Graph、Effect、Vehicle、Model/NPU 或 Driver/HAL。
状态：`Accepted Temporary`。关闭条件是 ISSUE-046 冻结 production repository/middleware/identity-policy owners、事务与重启
语义、关键事件 no-silent-drop QoS 和 Android 13 目标 fault evidence。当前：`event_broker_interface_defined=true`、
`event_broker_android13_arm64_verified=true`、`event_broker_process_local=true`、
`event_broker_durable_persistence_wired=false`、`event_broker_dds_transport_wired=false`、
`event_broker_production_published=false`、`event_broker_runtime_wired=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

### P6-W02 pressure queues remain process-local

P6-W02 新增 per-subscription bounded queue、deadline/priority admission，以及 `DROP_OLD`、`COALESCE`、
`REJECT`、`DISCONNECT` 四种显式压力策略。critical Action Observation 不允许被 drop/coalesce；队列无法受理或
deadline 到期时必须返回 replay-required/disconnect，而不能伪造成功或静默丢失。

该控制器目前是 contract-test 可构造的单 JVM 组件，未接入 P6-W01 `InProcessDurableEventBroker`，也没有 Room/file
durable append、ACK/cursor transaction、Binder callback death、DDS/SOME-IP 或 vendor middleware。critical
no-silent-drop 只证明本地决策路径可观测，不代表 process death、断电或跨 SOC 场景下 durable no-loss。

状态：`Accepted Temporary`。关闭条件仍由 `ISSUE-046` 管理：冻结 production broker composition、durable repository、
跨进程 consumer lifecycle、QoS 映射与 Android 13 fault evidence。当前：
`event_qos_contract_defined=true`、`event_qos_policy_count=4`、
`event_qos_critical_no_silent_drop_verified=true`、`event_qos_deadline_priority_verified=true`、
`event_qos_consumer_isolation_verified=true`、`event_qos_android13_arm64_verified=true`、
`event_qos_process_local=true`、`event_qos_broker_wired=false`、`event_qos_durable_persistence_wired=false`、
`event_qos_production_middleware_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。


## DEV-066 P5-W04 built-in execution is not production Tool authority

P5-W04 首次提供可运行的 `InProcessBuiltInToolExecutor`，但执行面严格限制为同 APK/JVM 内、owner 为
`runtime.builtin` 的显式注册实现。allowlist 精确绑定 family、contract digest、signer digest 与 artifact digest；这关闭了
测试合同中的任意 family fallback，不代表已验证安装包 signer、Skill package 签名链、吊销或回滚策略。

`currentApplicationSignerDigest` 当前由受信 composition 调用方提供。P5-W04 不读取 PackageManager、keystore 或 vendor
trust store，也不动态加载 APK/AAR/JAR。deadline/cancel 在调用前后及实现主动调用 `checkpoint()` 时验证；同步同进程 Java
无法安全强杀一个永久阻塞且不 checkpoint 的实现，因此不能声明 hard preemption 或资源隔离。无 OS virtualization、进程
sandbox、subprocess 或 class loader。

代码未接 `CentralBrainRuntimeService`、AgentGraph、Binder、Room、approval service、Effect、Vehicle、Model/NPU、network 或
Driver/HAL。approval-required selection 始终拒绝；debug sample 的执行成功只证明 bounded built-in 合同，不授权 production
Tool。状态：`Accepted Temporary`。关闭条件是 P5-W05 冻结 signer/version/revoke/rollback verifier，生产 composition 从可信
平台 signer evidence 派生身份，并通过独立进程/线程预算或可取消 API 解决 hard deadline ownership；随后才能单独评审
Runtime/Graph publication。当前：`tool_executor_contract_defined=true`、`tool_executor_runtime_wired=false`、
`tool_execution_enabled=false`、`production_tool_execution_enabled=false`、`production_tool_registered=false`、
`os_virtualization_enabled=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## DEV-067 P5-W05 static package verification is not production artifact trust

P5-W05 新增 `SkillSignerPolicy`、`SkillVersionPolicy` 与 `SkillArtifactVerifier`，能够对 canonical manifest、measured artifact
digest、observed signer digest、artifact epoch、Runtime compatibility、防降级和 capability allowlist 做确定性失败关闭。通过结果
只含摘要与 immutable capability，不允许 load 或 execution。

但 measured/observed digest 仍由调用方输入。当前代码不读取 PackageManager signing history、APK/JAR/dex/certificate、
keystore、TEE 或 vendor trust store，也不验证证书链、proof-of-possession、安装来源或硬件 attestation。ACTIVE/RETIRED/
REVOKED 和 artifact epoch 是静态 policy 语义，不是可信 policy publisher、原子 epoch 或持久 lifecycle store。

代码未接 `CentralBrainRuntimeService`、Governance Service、AgentGraph、Binder、Room、P5-W04 executor、Effect、Vehicle、
Model/NPU、network 或 Driver/HAL；无 file/parser/class loader/subprocess。状态：`Accepted Temporary`。关闭条件是 ISSUE-040
确定可信 evidence acquisition、policy signer/owner、atomic publish/restart/revoke/rollback 与审计，再通过独立 Runtime
composition 和 P9 fault/security evidence。当前：`skill_artifact_verifier_contract_defined=true`、
`trusted_skill_evidence_source_configured=false`、`package_signature_cryptographically_verified=false`、
`dynamic_skill_loading_enabled=false`、`skill_execution_enabled=false`、`skill_package_verifier_runtime_wired=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## DEV-068 P5-W06 process-local Working Memory is not production Memory

P5-W06 新增真实 payload-bearing `WorkingMemoryStore`，以 owner/session/item 隔离 opaque bytes，执行 monotonic TTL、item/byte/
token budgets、copy-on-input/read、replace/remove 和 terminal cleanup。相比 R6B1 digest-only lifecycle，它能够承载后续工作上下文，
但当前只存在于单 JVM process，未由 Runtime 或 Graph 构造。

store 对自己保留的 byte array 在 expiry/remove/replacement/terminal cleanup 时执行 `Arrays.fill`。这不保证调用方 request、
返回 snapshot、VM copy、GC page、swap 或 crash dump 被密码学擦除，也不构成 encrypted-at-rest。token count 由调用方提供，未
绑定 model/tokenizer name、version 或 digest。terminal tombstone 有界，逐出后必须依赖未来 production Session authority
防止 ID 复用。

代码未接 Binder、Room/file、Memory Service、AgentGraph、ModelProvider、Effect、Vehicle、NPU、network 或 Driver/HAL。
状态：`Accepted Temporary`。关闭条件是 ISSUE-041 确定 production Session terminal publisher、tokenizer/budget authority、
payload schema/privacy classification、persistence/encryption/retention 和 Runtime/model composition，并完成 P5-W07..W10 与 P9
privacy/security evidence。当前：`working_memory_store_defined=true`、`working_memory_android13_arm64_verified=false`、
`working_memory_process_local=true`、`working_memory_persistence_wired=false`、`working_memory_runtime_wired=false`、
`working_memory_model_context_published=false`、`working_memory_tokenizer_verified=false`、
`working_memory_content_logged=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## DEV-069 P5-W07 contract cipher is not production encrypted storage

P5-W07 新增 `ProfileMemoryStore`，能够执行 explicit consent、field allowlist、user/seat scope、read/update/delete/export、
retention/capacity、encryption-owner gate 与 sealed-byte lifecycle。store 不保留 `ProfileValue` 引用，只保留带 owner/key metadata
的 `SealedPayload`，并在 replace/delete/expiry 时覆零 retained ciphertext。

当前 main 只有 `createForContractTest` 和 owner interface。JVM/debug 的 XOR owner 是确定性 test double，没有 confidentiality、
integrity、nonce/tag、key secrecy、hardware backing、attestation、rotation、revocation 或 crash/power-loss evidence。`Arrays.fill`
也不能保证 VM copy、GC page、swap、backup 或 crash dump 被安全擦除。不得把 host/API33 contract probe 描述成加密静态存储。

代码未接 production consent/revocation authority、Android Keystore/TEE、Room/file repository、Binder、Runtime/Graph、model、
Effect、Vehicle、NPU 或 Driver/HAL。状态：`Accepted Temporary`。关闭条件是 ISSUE-042 冻结 identity/consent/key/repository
owners、真实 AEAD 与 key lifecycle、schema migration/backup policy、process-death/revoke/delete/export evidence，并通过 P5-W10/P9
隐私安全验收。当前：`profile_memory_store_defined=true`、`profile_memory_android13_arm64_verified=false`、
`profile_memory_process_local=true`、`profile_memory_durable_storage_wired=false`、
`profile_memory_production_encryption_owner_configured=false`、`profile_memory_consent_authority_production_wired=false`、
`profile_memory_runtime_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## DEV-070 P5-W08 process-local episodic summaries are not production Memory

P5-W08 新增 `EpisodicMemoryStore`，它只接受 build-owned `ScenarioReference`、固定 trigger/result/outcome enum、动作计数、elapsed
时间和 retention。main API 没有 raw signal array、任意 byte payload、user/model text 或自由文本入口；同 episode 的 exact replay
幂等，内容冲突失败关闭，容量超限不 silent eviction。

当前 `ScenarioCatalogAuthority`、`StoragePolicyAuthority`、`ReadAuthority` 与 `EraseAuthority` 只有 contract-test composition。store 只在单 JVM
process 保存分类摘要，没有 durable/encrypted repository、process-death recovery、可信跨重启 retention clock、consent/revocation
push、production identity 或 audit publisher。类型收窄降低原始信号滞留风险，但不等于 privacy/compliance qualification。

代码未接 Binder、Room/file、Runtime/Graph、ModelProvider/context assembly、Effect、Vehicle、NPU、network 或 Driver/HAL。状态：
`Accepted Temporary`。关闭条件是 ISSUE-043 冻结 catalog/policy/read/erase owner、durable encrypted schema、retention/clock、consent、
backup/migration 与 process-death evidence，并完成 P5-W09/W10 和 P9 验收。当前：`episodic_memory_store_defined=true`、
`episodic_memory_android13_arm64_verified=false`、`episodic_memory_process_local=true`、
`episodic_memory_read_fail_closed=true`、`episodic_memory_production_read_authority_wired=false`、
`episodic_memory_raw_continuous_signal_stored=false`、`episodic_memory_persistence_wired=false`、
`episodic_memory_runtime_wired=false`、`episodic_memory_model_context_published=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## DEV-085 P8-W01 discovery tooling does not complete target discovery

P8-W01 新增固定八能力/14 列 matrix contract、只读 ADB collector 和动态假设备门禁。collector 只读取 API level、公开 feature、
shell 可见 Binder/command inventory；原始证据强制写到 Git 仓库外并使用私有权限，summary 只包含非秘密 alias、计数、布尔值和
SHA-256 reference。该实现降低了目标发现时的越权、误发布和矩阵漂移风险。

这不等于取得 `CarPropertyManager` property list、Vendor service AIDL/SDK、permission/signature policy、owner/version 或
readback/fault/rollback evidence。Automotive feature 和 service name 不能证明 writable capability；模板八行继续全部
`EXTERNAL_BLOCKED`，不得根据 service 名、debug Digital Twin 或 deterministic provider 猜测 mapping。

状态：`Accepted Temporary`。解除条件是目标 owner 为每个 capability 提供并批准完整矩阵证据，随后单独执行 P8-W02..W06。
当前 `target_capability_discovery_contract_defined=true`、`target_capability_read_only_collector_verified=true`、
`target_capability_matrix_complete=false`、`target_capability_discovery_external_blocked=true`、
`vehicle_property_mapping_configured=false`、`production_adapter_registered=false`、`driver_development_triggered=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## DEV-086 P9-W01 initial budgets are not target measurements

P9-W01 新增 versioned JSON/Java budget catalog、七类十项固定 metric、三种 evidence mode、strict aggregate report 和 debug-only
synthetic probe。JVM 测试证明阈值边界、missing、unit mismatch、insufficient samples、duplicate 和 canonical digest 的确定性。

初始阈值是工程 baseline，不是 OEM/目标 owner 批准的量产 KPI。`CONTRACT_TEST` 和 debug probe 使用 catalog limit 的合成值，不读取
Android profiler、Perfetto、`/proc`、system clock、车辆接口或 NPU telemetry。当前设备 offline，未形成 30-sample target report；即使
未来结构完整的 `TARGET_ANDROID13` report 通过，合同也不会自动把 target/production qualification 置 true。

状态：`Accepted Temporary`。关闭条件是 ISSUE-048 冻结真实采集工具/场景/clock、目标 release/source/alias/evidence、owner approval，
在目标 Android 13 ARM64 上完成十项重复测量并由独立 release gate 评审。当前：
`performance_budget_contract_defined=true`、`performance_budget_report_validation_verified=true`、
`performance_budget_target_owner_approved=false`、`performance_budget_target_measurement_complete=false`、
`performance_budget_android13_arm64_verified=false`、`performance_budget_runtime_wired=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## DEV-087 P9-W02 synthetic matrix is not a 72h target run

P9-W02 新增 versioned JSON/Java 三 workload、六 fault、18-case cross-product、三种 evidence mode、strict aggregate report 和
debug-only synthetic probe。JVM 测试只证明矩阵形状、crash/ANR/invariant/outcome/recovery/duration gate 和 canonical digest 确定性。

当前实现不循环真实 Session/Graph/Effect，不杀 Adapter/Runtime，不制造磁盘或网络故障，也不读取 tombstone、ANR trace、系统资源、
车辆接口或 NPU。`CONTRACT_TEST` 的 18 条记录是合成合同证据，不能解释为 Android soak，更不能解释为 72h target run。

状态：`Accepted Temporary`。关闭条件是 ISSUE-049 冻结 production-like workload、fault injector、observation owner、终止条件和仓库外
证据，在命名目标 Android 13 release 上完成至少 72h 并由 owner 评审。当前：
`stability_fault_matrix_contract_defined=true`、`stability_matrix_case_count=18`、
`stability_target_72h_complete=false`、`stability_target_owner_approved=false`、
`stability_android13_arm64_verified=false`、`stability_fault_injection_runtime_wired=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## DEV-088 P9-W03a deterministic corpus is not coverage-guided fuzzing

P9-W03a 新增 JSON/Java 三 surface、18-case metadata catalog，并以 JVM 测试向实际 Checkpoint、ScenarioManifest、ToolSchema
边界提交固定 hostile input，要求精确 typed error。它能阻止已知 malformed/duplicate/unknown/oversize/tamper/path/depth/type/value
回归，但不搜索未知输入空间，也没有 coverage feedback、mutation engine、crash minimization 或 corpus retention policy。

当前没有测试 Binder calling UID/package/current signer spoof、callback/request replay、signer rotation/revoke、全部 AIDL/Parcel、
StructuredModelOutput/其他 schema、Android instrumentation/SELinux 或 Vendor/硬件攻击面。host JVM 成功不能解释为 target penetration
test、量产安全审查或 Android 13 ARM64 evidence。

状态：`Accepted Temporary`。关闭条件是 W03b/W03c 完成 caller/replay/signature 与剩余 schema/device probe，ISSUE-050 冻结并执行
受控 coverage-guided fuzz/目标 evidence，由安全 owner 评审。当前：`security_parser_corpus_defined=true`、
`security_parser_case_count=18`、`security_parser_fail_closed_regression_verified=true`、
`security_coverage_guided_fuzz_complete=false`、`security_aidl_identity_review_complete=false`、
`security_signature_policy_review_complete=false`、`security_android13_arm64_verified=false`、
`security_runtime_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## DEV-091 P9-W04a inventory is not lifecycle enforcement

P9-W04a 冻结 12 个当前 Android 数据面及其 consent/retention/delete/export/redaction 分类，并通过源码存在性和 JSON/Java 同源
检查。该清单可以发现边界漂移，但不会改变 Room、Memory、Event、Tool 或 Model 的运行行为。

durable Effect recovery 与 durable Audit 当前没有 owner-approved retention/delete policy，因此明确记录为两个 `POLICY_GAP`。
不能因为记录均为固定 metadata/digest 就推断可以无限保留，也不能为了通过门禁虚构 retention 时长或删除 active safety recovery。

状态：`Accepted Temporary`。关闭条件是 W04b 获得 policy owner 输入并实现 version/digest、retention ceiling、active-state guard、
delete/erase/export authorization 和 redacted evidence；W04c 再完成 Android probe。当前
`privacy_data_inventory_complete=true`、`privacy_policy_gap_count=2`、`privacy_owner_policy_approved=false`、
`privacy_production_lifecycle_complete=false`、`privacy_runtime_lifecycle_wiring_complete=false`、
`privacy_android13_arm64_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W04`。

## DEV-092 P9-W04b admission is not an approved lifecycle policy

W04b 新增 policy body digest、三 owner evidence 校验和 delete/erase/export preflight，但仓库 policy 仍是
`cougaros-privacy-draft/0.1.0-draft`。Effect recovery 与 Audit ceiling 未设置，owner evidence 数组为空，所以当前 policy 必须拒绝激活。

JVM 使用的正数 ceiling 与 digest 只是 synthetic contract fixture，用于证明完整输入可通过 validator；它们不是产品 retention schedule、
合规决定、功能安全放行或目标设备证据。即使 preflight 返回 admitted，也固定不修改 repository、不导出数据、不授予 Runtime authority。

状态：`Accepted Temporary`。关闭条件仍是三类 owner 对同一 policy/inventory 的真实批准引用、迁移/回滚设计、repository enforcement 与
W04c Android evidence。当前 `privacy_policy_admission_defined=true`、`privacy_current_policy_admitted=false`、
`privacy_owner_policy_approved=false`、`privacy_repository_mutation_wired=false`、
`privacy_runtime_lifecycle_wiring_complete=false`、`privacy_android13_arm64_verified=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W04`。

## DEV-093 P9-W04c probe availability is not owner policy or target evidence

W04c 在 debug APK 中提供 DUMP-protected redacted projection probe，并把 release absence 与 installer markers 纳入门禁。Host JVM 与 APK
compile 只能证明入口和脱敏规则存在；在 ADB 无 transport 时不能把 `available=true` 提升为 `executed=true` 或 Android 13 ARM64 evidence。

Probe 只观察 current draft 的 rejected metadata，不读取 repository，也不证明 retention/delete/export enforcement。它不能关闭 W04b 的 owner
policy 缺口；21-key 日志同样不是合规审计持久化或法律证据。

状态：`Accepted Temporary`。当前 `privacy_redacted_audit_projection_defined=true`、
`privacy_android_debug_probe_available=true`、`privacy_android_debug_probe_executed=false`、
`privacy_android13_arm64_verified=false`、`privacy_owner_policy_approved=false`、
`privacy_repository_mutation_wired=false`、`privacy_runtime_lifecycle_wiring_complete=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W04`。
## DEV-094 P9-W05a contract admission is not a production release

W05a 用 synthetic digest/metadata fixture 证明 exact package set、same-signer/cohort、version/schema、migration 与 rollback gate 可达。
它不测量真实 APK signer，不验证证书链/轮换历史，不生成或持有私钥，不调用 OTA/MDM，也不执行升级或 rollback。

仓库 JSON 的 owner evidence 均为 null，当前 APK class 仍是 debug；JVM `ADMITTED` 只表示一组完整 synthetic 输入满足静态规则，不能
提升 `production_signer_owner_approved`、`production_release_candidate_admitted`、Android target 或 production readiness。

状态：`Accepted Temporary`。关闭条件是 ISSUE-052 获得命名 production signer/release/rollback owner、受控 signer measurement、正式
release/source/archive、Room migration/rollback compatibility evidence，并在目标 Android 13 上完成 same-signer upgrade 与 rollback
rehearsal。当前 `production_signer_owner_approved=false`、`production_release_candidate_admitted=false`、
`release_installer_wired=false`、`release_rollback_executor_wired=false`、
`release_android13_arm64_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W05`。

## DEV-095 P9-W05b metadata observation is not production signer qualification

W05b 通过 Android public PackageManager 在 debug Activity 中观察固定三包的 installed/version-match 和 signer relation，并将结果压缩为
27 个 count/boolean key。`checkSignatures` 没有输出 signer/certificate bytes，但也没有验证 certificate chain、rotation history、正式
candidate APK、source/archive/artifact digest 或 owner approval。

只读 ADB adapter 要求已安装 debug Runtime；现有 debug installer 可在自身安装后调用它，但 adapter 本身不安装、卸载或 rollback。即使
三包 observed set/cohort/version 都匹配，candidate metadata/admission 与 installer authority 仍为 false。当前 transport 不在线，探针未执行。

状态：`Accepted Temporary`。关闭条件仍是 ISSUE-052 的正式 production signer/release/rollback owner、受控 candidate evidence、批准
installer/OTA 与目标 upgrade/rollback rehearsal。当前 `release_metadata_projection_defined=true`、
`release_installer_dry_run_adapter_defined=true`、`release_android_debug_probe_available=true`、
`release_android_debug_probe_executed=false`、`production_signer_owner_approved=false`、
`production_release_candidate_admitted=false`、`release_installer_wired=false`、
`release_rollback_executor_wired=false`、`release_android13_arm64_verified=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W05`。

## DEV-096 P9-W06a software admission is not OEM safety acceptance

P9-W06a 将 12 项 action、四类 UX profile、500 ms Safety State、三 owner role 和四项 vehicle capability evidence gate
冻结为纯 Java 合同。moving hard interlock、stale/untrusted reject 和 parked recline approval-required 的 JVM 结果只证明
deterministic policy 行为，不证明目标平台提供可信 driving state、座椅硬联锁或驾驶分心法规符合性。

JVM 中 `PLATFORM_TRUSTED_ADAPTER + hardwareBacked` 和三份 owner digest 是 synthetic fixture。仓库当前 draft approval 数为 0，
P2 capability production authorization 数为 0；合同未接 Runtime/Governance/Effect/Vehicle Service，也没有 Android debug probe。
`ALLOW_POLICY_ONLY` 不是 dispatch grant，`APPROVAL_REQUIRED` 不是用户已批准，更不能覆盖 moving hard interlock。

产品设计中的 IDLE 需要可信 gear/speed/parking-brake 联合语义；黑盒目标 mapping 未提供时，本合同只暴露 PARKED/MOVING/
UNKNOWN/FAULT，不能把单一软件值解释为 IDLE 或 OEM Safety source。

状态：`Accepted Temporary`。关闭条件是 ISSUE-029/030 与 P8 提供命名 Safety/HMI/Vehicle owner、公开 signal/capability/
permission/readback/activation contract、目标 Android 13 evidence、驾驶分心 acceptance matrix 和座椅硬联锁复测。当前
`driver_safety_admission_defined=true`、`driver_safety_current_owner_policy_approved=false`、
`driver_safety_vehicle_state_provider_wired=false`、`driver_safety_effect_runtime_wired=false`、
`driver_safety_android13_arm64_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W06`。

## DEV-097 P9-W06b Android contract probe is not target safety evidence

W06b 的 27-key projection 只重新计算 W06a build-owned catalog、owner count 和 capability authorization count。debug Activity
不读取真实 speed、gear、parking brake、occupancy、belt、seat angle、state source 或车辆故障，也不接受 owner/activation payload。
因此 host/JVM/build 通过只证明脱敏软件入口和失败关闭声明一致，不证明目标驾驶态、座椅硬联锁或法规符合性。

只读 ADB adapter 即使在 Android 13 ARM64 上成功执行，也只能设置独立的
`driver_safety_android_contract_probe_android13_arm64_verified=true`。它不得设置
`driver_safety_android13_arm64_verified=true`，不得批准 owner policy、激活 capability、触发 Effect 或访问硬件。当前 transport
未执行，`driver_safety_android_debug_probe_executed=false`。

状态：`Accepted Temporary`。关闭条件与 DEV-096/ISSUE-029/030 相同：命名 Safety/HMI/Vehicle owner，公开 signal/capability/
permission/readback/activation contract，目标状态与故障矩阵，驾驶分心 acceptance matrix 和座椅硬联锁复测。当前
`driver_safety_redacted_projection_defined=true`、`driver_safety_target_adapter_defined=true`、
`driver_safety_current_owner_policy_approved=false`、`driver_safety_vehicle_state_provider_wired=false`、
`driver_safety_effect_runtime_wired=false`、`driver_safety_android13_arm64_verified=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W06`。

## DEV-098 P9-W07a metadata eligibility is not target qualification

W07a 把发布身份和八类现场诊断统一为稳定摘要合同，但不采集或验证真实 target evidence。Host synthetic 报告即使八类 PASS 也只能返回
`HOST_SOFTWARE_ONLY`；Target 报告即使存在 owner digest 且没有 NOT_RUN，也只返回 `TARGET_OWNER_REVIEW_ELIGIBLE`。该结果不判断
PASS/FAIL/BLOCKED 组合能否验收，不证明 source/archive/artifact、signer、安装、启动、服务或场景结果真实。

偏差原因是当前缺少在线目标 transport、命名 release/diagnostics/test owner、受控 evidence 和 replacement release。W07a 禁止 automatic
upload，不接 Android probe、GitHub mutation、Runtime/Governance Service 或 installer。它不能关闭 ISSUE-052，也不能继承 W05 debug
metadata observation 成为 production candidate。

状态：`Accepted Temporary`。关闭条件是 W07b/W07c 提供受保护的目标 probe、脱敏报告 admission、命名 replacement release、issue/retest
状态机和 owner-approved target evidence；production signer/installer/rollback 仍需 ISSUE-052。当前
`release_evidence_envelope_defined=true`、`release_evidence_target_owner_approved=false`、
`release_evidence_target_report_admitted=false`、`release_evidence_runtime_diagnostics_wired=false`、
`release_evidence_retest_workflow_wired=false`、`release_evidence_android13_arm64_verified=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W07`。

## DEV-100 P9-W07c software admission is not a published replacement release or completed retest

W07c 可以在 JVM 中构造满足四方摘要和八类 PASS 的 metadata fixture，并返回 `targetReportAdmitted=true` 与
`issueCloseEligible=true`。这只验证 admission 算法；repository 没有真实 target report、owner digest、tester confirmation 或命名
replacement Release，因此静态 claim 必须继续为 false。

状态机不调用 GitHub、不创建 tag/asset、不安装 APK，也不自动关闭 Issue。`state/verified` 仅表示输入元数据满足关闭前置条件，真实
Issue 仍必须由 maintainer 在 target tester 对具体 replacement release 复测后人工推进。production signer/installer/rollback 还要独立关闭
ISSUE-052，W07c 不能替代 W05 或 P8 证据。

状态：`Accepted Temporary`。关闭条件是仓库外 target report 与 release/archive digest 一致，target/release/diagnostics owner 和 tester
完成审核，命名 replacement release 在目标复测，并由维护者记录 Issue 时间线。当前
`release_retest_state_machine_defined=true`、`release_retest_replacement_release_published=false`、
`release_evidence_target_report_admitted=false`、`release_evidence_retest_workflow_wired=false`、
`release_retest_github_issue_mutation_wired=false`、`release_retest_automatic_issue_close_allowed=false`、
`release_retest_android13_arm64_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W07`。

## DEV-099 P9-W07b debug diagnostics are not complete field acceptance

W07b 在目标上可执行五类 bounded diagnostic，但 installer dry-run、installer execute 和 manual scenario matrix 明确 NOT_RUN。APK 的
PackageManager preflight 只证明当前安装集合、launcher/service declaration 的低敏元数据；`am start` 与 Diagnostics Binder probe 只证明
debug entry 可启动，不证明业务场景质量、正式 release signer、installer/rollback、72h 稳定性或 production owner approval。

为避免伪造，adapter 不接收 artifact 或 target-input file，不执行 install/uninstall/rollback，不上传或修改 GitHub issue。即使五类 PASS，
`field_diagnostics_target_category_execution_complete=false` 和 `release_evidence_target_report_admitted=false`；只有 W07c 对命名
replacement release、全八类事实和 owner/retest evidence 进行 admission 后才可变化。

状态：`Accepted Temporary`。关闭条件是 W07c 提供 complete report/replacement release/issue-retest state machine，并由目标 tester/owner
复测；production signer/installer/rollback 还需 ISSUE-052。当前 `field_diagnostics_android_debug_probe_executed=false`、
`field_diagnostics_android13_arm64_verified=false`、`release_evidence_retest_workflow_wired=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W07`。

## DEV-101 P4-D4a debug graph progress is not Runtime Effect execution

P4-D4a 复用真实 Scenario Compiler 与 control-only AgentGraph，并自动推进 Context/Policy/Summary 本地节点；这比 HMI 静态 projection
多了一层确定性 Plan/Graph 组合。但 approval、Effect 与 readback 仍只形成 pending node，JVM supplied outcome 是测试输入，不是 adapter、
车辆回读或 owner approval 证据。

该类仅存在于 debug source set，没有 Android Service、Session/Event Binder 或 Client2 注册。生产 Runtime 仍输出
`scenario_execution_enabled=false`，release source 不包含该类。因此不能把 `simulated_scenario_plan_published=true` 解释为 production
Plan publication，也不能关闭 ISSUE-022/026/030/033。

状态：`Accepted Temporary`。P4-D4b 负责 debug Runtime Session/Event projection，后续包再分别接 debug adapter/readback 和 Client2；
真实 production Effect 仍需 P8 capability/permission/owner evidence。当前 `simulated_scenario_graph_defined=true`、
`simulated_scenario_android_runtime_wired=false`、`simulated_scenario_client2_wired=false`、
`simulated_scenario_effect_dispatch_enabled=false`、`simulated_scenario_readback_accessed=false`、
`scenario_execution_enabled=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P4-D4a`。

## DEV-102 P4-D4b process-local Runtime projection is not an Android Binder runtime

P4-D4b 已把 D4a Graph 接入 debug 进程内 Session/Plan/Event projection，并复用 P6 `BoundedEventRuntime` 的 topic、sequence、retention
和 subscription。相比 D4a，它能产生可订阅的调用链 metadata，但事件 payload 只有 digest，且没有跨进程入口。

该类不在 main/release，没有 Android Service、AIDL、Session/Event Binder、Client2 binding 或 production broker。JVM supplied outcome 仍不是
Effect adapter、readback 或 approval authority；`simulated_scenario_debug_runtime_wired=true` 不能解释为
`simulated_scenario_android_runtime_wired=true`。

状态：`Accepted Temporary`。P4-D4c 负责 signature-protected debug Binder；后续包再接 debug adapter/readback 和 Client2。
P8 真实 vehicle/owner evidence 到位前，production dispatch 不得开启。

当前 `simulated_scenario_android_runtime_wired=false`、`simulated_scenario_android_service_published=false`、
`simulated_scenario_session_event_binder_published=false`、`simulated_scenario_client2_wired=false`、
`simulated_scenario_effect_dispatch_enabled=false`、`simulated_scenario_readback_accessed=false`、
`scenario_execution_enabled=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P4-D4b`。

## DEV-103 P4-D4c debug Binder publication is not Effect or production execution

P4-D4c 已发布 signature/capability 双保护的 debug Android Service/AIDL，Client 可跨进程启动固定场景并读取 metadata。该进展关闭了
D4b 的“无 Android 入口”差距，但 Binder outcome 是显式仿真输入，不是 adapter、readback 或 owner approval 证据。

Service、AIDL、Parcelable 与 input factory 全部在 debug source，release absent。固定 Context 使用 build-owned synthetic values；不能用于
目标车辆状态、驾驶安全或硬件验收。状态：`Accepted Temporary`。下一步 D4d 才接 simulated adapter/readback，随后接 Client2。

Android 13 已验证 Debug 安装可见和未授权调用被签名权限拒绝；同签名 AIDL 正向调用仍缺失。该结果缩小安装/权限偏差，不关闭
Binder 功能、Client2、Effect/readback 或目标硬件偏差。

当前 `simulated_scenario_android_runtime_wired=true`、`simulated_scenario_android_service_published=true`、
`simulated_scenario_session_event_binder_published=true`、`simulated_scenario_client2_wired=false`、
`simulated_scenario_binder_android13_install_verified=true`、
`simulated_scenario_binder_unauthorized_access_denied_verified=true`、
`simulated_scenario_binder_authorized_call_verified=false`、
`simulated_scenario_effect_dispatch_enabled=false`、`simulated_scenario_readback_accessed=false`、
`simulated_scenario_production_registered=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P4-D4c`。

## DEV-104 P4-D4d simulated Effect success is not vehicle Effect authority

P4-D4d 将四个既有 debug simulated adapters 接入场景 Graph，关闭“Binder 只能人工伪造 Effect/readback outcome”的软件偏差。目标值、
readback 和 approval digest 都是 build-owned process-local simulation；没有 VehicleProperty/Vendor SOA、真实 readback 或 owner approval。

Binder v2 增加计数和 Partial/Stuck 状态，允许 Client2 后续展示自动执行链路，但这些字段不是车辆执行证据。状态：`Accepted Temporary`；
P4-D4e 只接 Client2，真实车辆路径仍由 P8/OEM evidence 阻塞。

实体 Android 13 同签名 probe 已通过 8 次 simulated dispatch、6 次 matched readback、1 次显式 approval input 和 0 failure；这只关闭
debug Binder/composition 的执行证据，不改变本偏差状态。

当前 `simulated_scenario_effect_dispatch_enabled=true`、`simulated_scenario_readback_accessed=true`、
`simulated_scenario_hardware_effect_dispatch_enabled=false`、`simulated_scenario_approval_authority_available=false`、
`simulated_scenario_android_debug_probe_executed=true`、`simulated_scenario_binder_authorized_call_verified=true`、
`simulated_scenario_client2_wired=false`、`simulated_scenario_production_registered=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P4-D4d`。

### DEV-105 P4-D4e Client2 debug loop is not production vehicle execution

**Status:** Open / tracked

Client2 现在可以从自然场景按钮启动 D4d debug Binder，显示七阶段链路，并对 parked Fatigue 提供显式批准/拒绝。UI 中 APPLIED、VERIFIED、
Completed 和 Partial 均来自 process-local simulated adapters 与 simulated observation，不是 VehicleProperty/Vendor SOA/CAN/Media/Nav/Seat/HVAC
硬件证据。

本偏差要求所有 D4e 表面保持 `SIMULATED`、`DEBUG ONLY`、`HARDWARE NOT ACCESSED`；`scenario_execution_enabled=false` 表示量产执行
仍未启用。D4e APK 仍是逆向集成 debug artifact，不是正式 vendor source-tree Client2 release。批准按钮只向 debug graph 提供测试 outcome，
不构成 production approval authority。

实体 Android 13 ARM64 已验证 Cold 3/3、Fatigue approved 5/3、Fatigue rejected 4/2 Partial、两次显式输入与七阶段 UI；这只关闭 D4e
应用层仿真验收，不关闭 P8/P9、真实 Driver/HAL、production signer/installer、车辆安全 owner 或 target hardware qualification。

当前 `simulated_scenario_client2_wired=true`、`simulated_scenario_android13_arm64_client_verified=true`、
`hmi_d4_debug_demo_control_loop_complete=true`、`simulated_scenario_hardware_effect_dispatch_enabled=false`、
`simulated_scenario_approval_authority_available=false`、`client2_production_release_artifact_available=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P4-D4e`。

## DEV-106 P5 debug probe acceptance is not production Tool or Memory authority

P5-W01..W10 已在 Android 13 ARM64 上通过统一 debug probe 与完整安装回归。探针使用 build-owned fixture、进程内对象和 DUMP-protected
Activity，证明 Java contract 在目标 Android ABI/API 上可加载并按预期失败关闭；它不证明 production Registry、health publisher、
Rule/Skill signer authority、durable encrypted Memory、consent authority、model context composition 或 Runtime publication。

installer 已停止输出 raw serial/model，并在 Demo acceptance 前停止 Client2、清空 logcat，避免 renderer 日志挤掉 trusted-caller marker。
ContextBudget fixture 固定为必须触发 summarize/truncate/drop 的预算组合。状态：`Accepted Temporary`；ISSUE-036..045 保持 Open，
直到 owner、持久化、跨进程发布、P8 Vendor 接口和 P9 量产证据分别关闭。

当前 `p5_android13_arm64_probe_acceptance_complete=true`、`device_identity_redacted=true`、
`production_tool_authority_published=false`、`production_memory_authority_published=false`、
`production_runtime_wired=false`、`driver_hal_accessed=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`。
