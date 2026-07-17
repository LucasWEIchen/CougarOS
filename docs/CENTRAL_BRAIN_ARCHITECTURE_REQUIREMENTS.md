# 中央大脑架构需求基线

版本：0.9
日期：2026-07-17
状态：Android 13 实际工程基线

## 1. 基线声明

用户提供的架构图是需求基线，不是示意图。实现、测试、交付和文档必须引用 Req ID；任何偏离图中
层级、所有权、调用关系或交付范围的内容必须登记到架构偏差和问题台账。

## 2. 用户明确约束

1. 目标是黑盒 Android 13 座舱控制器；不得修改不可获得源码的厂商 Framework、BSP 或预编译组件。
2. 当前只开发 Android 版本，主语言为 Java、AIDL、C 和 JNI。
3. Python 仿真 runtime 不再维护；Linux Python 前端/daemon 不再交付。
4. 不开发虚拟化；只维护 Safety、ASIL/QM 和跨域接口约束。
5. Driver/HAL 仅在公开或 Vendor SDK 不能满足明确接口时登记最小缺口。
6. 外置 PCIe NPU 当前保留 ModelProvider、C ABI/JNI、Vendor empty provider 和 Driver/HAL 合同。
7. 真实车辆、模型、NPU 或安全 authority 不可用时，production 必须失败关闭。
8. Android debug/test double 不能作为真实硬件、production 或量产验收证据。
9. Client2 APK 必须规划并实现 HVAC/Seat 中控演示页；无真实信号时使用显式 SIMULATED 来源，
   手动控制和 AI 场景必须复用 Runtime 治理/Effect/readback 链路。
10. Client2 的 AIOS 主交互必须以自然场景意图为入口，自动展示 Context、Plan、Policy、Effect 和
    readback 调用链；HVAC/Seat 手动控件只能作为 Effect 详情和受治理的次级兜底入口。

## 3. 分层需求

| 层级 | Req ID | 必须包含 | 当前 Android 映射 |
| --- | --- | --- | --- |
| L1 应用 | APP-001..010 | HMI、座舱/Cluster/TBOX/ADAS/其他 App 与 AI SDK 接入 | Client2、Demo HMI、Central Brain SDK |
| L2 Framework | FW-U-001..008, FW-S-001..006 | Uni Info Bus 语义对象、SOA 服务入口、Safety State | typed AIDL、Context/Event/Memory/Action/Skill 合同 |
| L3 Native | NV-F-001..012, NV-G-001..007, NV-P-001..007 | AIOS Kernel、adapter、Runtime & Governance、Protocol Binding | Runtime Service、Room、policy、scheduler、C ABI/JNI |
| L4 Kernel/HAL | KH-001..009 | OS 基础能力、Driver、HAL、Safety Runtime | 只使用公开能力；缺口按 Driver/HAL 文档登记 |
| L5 虚拟化 | HV-001..003 | Hypervisor/ASIL-QM/跨域约束 | 只记录接口，不开发 |
| L6 硬件 | HW-001..002 | UniSOC 基座与外置 PCIe NPU | 真实 NPU 未接入，Vendor provider 为空 |

## 4. 应用层需求

| Req ID | 要求 | 实现规则 | 当前状态 |
| --- | --- | --- | --- |
| APP-001 | 座舱 HMI | 只能经 SDK/Binder 访问 Runtime，不直连模型或车控 | Client2 场景面板已集成；HVAC/Seat 页待开发 |
| APP-002 | 座舱服务 | 作为受治理 Business/Foundation/Atomic service 暴露 | 外部阻塞 |
| APP-003 | Agent App | 通过 Session/Plan/Tool/Action/Effect 执行 | Stage 2 待开发 |
| APP-004 | AI SDK | 提供稳定 typed client facade、异步任务和故障语义 | Android AAR 已实现 |
| APP-005 | Cluster/TBOX App | 与座舱域按服务合同隔离 | 外部阻塞 |
| APP-006 | Cluster/TBOX Service | 声明显示、媒体、远控、OTA 边界 | 外部阻塞 |
| APP-007 | ADAS App | 只读状态或发起受控请求，不进入安全闭环 | 外部阻塞 |
| APP-008 | ADAS Service | 通过受治理 adapter/Protocol Binding 暴露 | 外部阻塞 |
| APP-009 | 诊断/标定/Trace App | 必须受身份、capability 和 audit 控制 | 部分实现 |
| APP-010 | 诊断/标定/Trace Service | 不允许 App 绕过 Runtime 直达底层 | 合同已定义 |

## 5. Framework 需求

### 5.1 Uni Info Bus

| Req ID | 对象 | 必须语义 | 当前状态 |
| --- | --- | --- | --- |
| FW-U-001 | Context | 车辆、用户、环境的版本化 snapshot | Stage 2 待开发 |
| FW-U-002 | State | 服务、模型、车辆状态查询 | diagnostics/readiness 部分实现 |
| FW-U-003 | Event | publish/subscribe/cursor/overflow/replay | Android bounded + durable cursor 基础完成 |
| FW-U-004 | Action | 所有副作用必须经过 policy/approval/audit | typed governance + effect gate 完成 |
| FW-U-005 | Service | 统一服务调用和错误 envelope | Stage 2 待接真实 adapter |
| FW-U-006 | Tool | schema、capability、安全状态和超时 | built-in Skill 合同基础完成 |
| FW-U-007 | Permission | 身份来自 Binder，不接受请求体自报权限 | 已实现 |
| FW-U-008 | Extension | 扩展不得绕过核心语义和治理 | production loader 未实现 |

### 5.2 SOA 服务入口

| Req ID | 服务类 | 实现规则 | 当前状态 |
| --- | --- | --- | --- |
| FW-S-001 | Business Service | 场景编排必须生成可审计 plan/effect | Stage 2 待开发 |
| FW-S-002 | Foundation Service | 账号、配置、时间、权限采用可替换 adapter | 外部阻塞 |
| FW-S-003 | Atomic Service | 最小 HVAC/Seat/Media/Navigation 能力 | debug/demo adapter 待开发；真实服务外部阻塞 |
| FW-S-004 | Service Contract | IDL/schema/version/error 必须冻结 | typed AIDL 基础完成 |
| FW-S-005 | Safety State | 强制 interlock，用户确认不能覆盖硬联锁 | owner 未接入 |
| FW-S-006 | Extension Service | 必须注册、发现、授权、审计和撤销 | 未实现 |

## 6. Native 层需求

### 6.1 功能与适配

| Req ID | 模块 | 实现规则 | 当前状态 |
| --- | --- | --- | --- |
| NV-F-001 | AIOS Kernel | Session/Task/Plan/Model/Tool/Memory/Safety 由 Runtime 拥有 | Android Runtime 基础完成 |
| NV-F-002 | Sensor/Actuator | 统一输入输出 adapter，不猜 vendor API | 外部阻塞 |
| NV-F-003 | Service Adapter | 语义 service 到目标 API 的唯一桥接 | empty/contract |
| NV-F-004 | Vehicle/Body Signal | BCM/HVAC/Seat/Door/Light 需真实目录和 readback | 外部阻塞 |
| NV-F-005 | ECU Proxy/Signal Adapter | 需 property/DBC/ARXML/area/error owner | 外部阻塞 |
| NV-F-006 | Data/Time Sync | 高频数据需时间域和 frame metadata | 未实现 |
| NV-F-007 | Connected Funcware | TBOX/V2X/OTA/Diag 经 adapter 接入 | 外部阻塞 |
| NV-F-008 | SOA Runtime | 服务生命周期、超时、取消、健康状态 | 部分实现 |
| NV-F-009 | Security/Policy Adapter | Safety/zone/ASIL-QM/default-deny | 软件 policy 完成，目标 owner 阻塞 |
| NV-F-010 | ADAS Funcware | 安全域闭环不由用户态 AIOS 接管 | 非本阶段 |
| NV-F-011 | Model Runtime Adapter | 抽象 CPU/GPU/NPU/Cloud，受 scheduler/governance 控制 | Provider contract + Vendor empty |
| NV-F-012 | Observability | trace/metric/audit 不得记录敏感原文 | 软件 snapshot/audit 部分完成 |

### 6.2 Runtime & Governance

| Req ID | 能力 | 实现规则 | 当前状态 |
| --- | --- | --- | --- |
| NV-G-001 | Registry | 稳定 ID、版本、owner、health | production registry 待 Stage 2 |
| NV-G-002 | Discovery | App 不硬编码 provider/service 地址 | Binder explicit component 当前受控 |
| NV-G-003 | Schema/IDL | typed、versioned、checksum/hash | 已实现 |
| NV-G-004 | QoS | deadline、priority、quota、cancel | scheduler contract 完成 |
| NV-G-005 | Policy | identity/capability/safety/privacy/default-deny | 软件基础完成 |
| NV-G-006 | Lifecycle | submit/status/cancel/death/restart/recovery | 软件基础完成 |
| NV-G-007 | Audit/Diagnostics | durable metadata/digest、受保护查询 | 软件基础完成 |

### 6.3 Protocol Binding

| Req ID | Binding | 当前规则 | 当前状态 |
| --- | --- | --- | --- |
| NV-P-001 | SOME/IP | 只有目标网络/IDL/owner 到位后实现 | 外部阻塞 |
| NV-P-002 | IPC | Android typed Binder 是当前唯一应用主线 | android_integrated |
| NV-P-003 | gRPC/RPC | 当前 Android 范围不交付 | 非本阶段 |
| NV-P-004 | MQTT | 必须经过 Privacy/Policy；当前不交付 | 非本阶段 |
| NV-P-005 | REST | 不作为本地 AIOS Runtime binding，不得恢复 Python gateway | Retired |
| NV-P-006 | DDS | 高频 topic owner/QoS/Driver-HAL 明确后实现 | 外部阻塞 |
| NV-P-007 | 其他 | 必须登记 schema、identity、QoS 和治理 | 未实现 |

## 7. Kernel/HAL、虚拟化与硬件需求

| Req ID | 要求 | 当前边界 |
| --- | --- | --- |
| KH-001 | 复用 Android 文件系统、网络和系统服务基础能力 | 不重造 OS |
| KH-002 | 共享内存和 NPU buffer 需明确 ownership/cache/IOMMU | 外部阻塞 |
| KH-003 | Drivers 只在明确缺口时新增 | DRV-GAP 文档化，未触发 |
| KH-004 | 其他底层扩展需单独审批 | 未实现 |
| KH-005 | 基础 Libs 需锁定版本/ABI/license | Gradle/NDK 依赖受控 |
| KH-006 | HAL 必须有版本、错误、取消、恢复和安全边界 | NPU/VHAL 合同，未实现真实 HAL |
| KH-007 | Safety Runtime 是硬联锁 authority | 目标接口未提供 |
| KH-008 | 图中第二组 Libs 含义不明 | 不猜测，见 ISSUE-007/027 |
| KH-009 | 调度/中断/异常由 OS/vendor runtime 负责 | 上层只消费稳定错误 |
| HV-001 | 不开发 Hypervisor | 只记录接口约束 |
| HV-002 | 不开发 ASIL/QM 隔离机制 | 只定义服务到安全域映射 |
| HV-003 | 不开发跨 VM transport | 只定义 envelope/fallback |
| HW-001 | UniSOC Automotive-solution 是硬件基线 | 黑盒目标，不修改 BSP |
| HW-002 | 外置 PCIe NPU 是模型计算底座 | 当前只保留空接口和 C ABI/JNI |

虚拟化和 Safety 详细约束见 `CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md`。

## 8. 跨 SoC 组件与交付

| Req ID | 图中黄色小太阳组件 | 平台无关合同 | 当前 Android 交付 |
| --- | --- | --- | --- |
| XSC-001 | AI SDK | Task/Session/Plan/Result/Error | SDK AAR + typed Binder |
| XSC-002 | Uni Info Bus | Context/State/Event/Action/Tool/Permission | Runtime typed objects |
| XSC-003 | SOA 服务入口 | Service/Method/Effect/Error | adapter contract，真实服务阻塞 |
| XSC-004 | AIOS Kernel | lifecycle/model/memory/safety C/Java boundary | Runtime Service + Native AAR |
| XSC-005 | Runtime & Governance | identity/policy/QoS/lifecycle/audit | Binder/Room/middleware |
| XSC-006 | Protocol Binding | version/hash/error/death/cancel | Android app-local AIDL |

`XSC-001..006` 原要求具备 Android/Linux parity；当前用户批准 Android-only，偏差记录为
`DEV-026`。平台无关数据合同继续避免绑定 Vendor API。

| Req ID | 交付要求 | 当前实现 |
| --- | --- | --- |
| DEL-001 | Android 13 APK/AAR/C ABI、安装和验证 | 已有软件交付链 |
| DEL-002 | Linux 同步交付 | 当前范围暂停，旧 Python 样例已删除 |
| DEL-003 | 工程师文档、接口、状态机、命令 | 当前文档集 |
| DEL-004 | 黑盒 Android 平台差异、签名、SELinux、Vendor/Driver-HAL 边界 | delivery/driver/preflight docs |
| DEL-005 | Driver/HAL 接口支持和最小 gap | Driver/HAL matrix + NPU contract |

## 9. AIOS Stage 2 derived requirement baseline

| Req ID | 最小要求 | 验收边界 |
| --- | --- | --- |
| S2-UX-001 | 面板显示 session/plan/effect 状态 | reducer/render，不持有权威状态 |
| S2-UX-002 | approval/partial failure/undo UX | driving restriction 优先 |
| S2-UX-003 | 可访问性、显示矩阵、错误恢复 | Client2 demo 与量产 HMI 分离 |
| S2-HMI-001 | Client2 APK 内中控 HVAC 控制页 | 只经 SDK/Runtime；desired/reported 分离 |
| S2-HMI-002 | Client2 APK 内中控 Seat 控制页 | unknown/moving 驾驶席动作 fail closed |
| S2-HMI-003 | 执行闭环 UX | timeline/approval/partial/retry/undo/recovery |
| S2-HMI-004 | 无真实信号的演示来源 | Android debug/test Digital Twin；持续显示 SIMULATED |
| S2-HMI-005 | 统一请求链 | 场景和手动控件都进入 Governance/Effect/readback |
| S2-HMI-006 | 意图驱动的 AIOS 主交互 | 自然表达 -> Context -> Plan -> Policy -> Effect -> readback；设备按钮降为次级入口 |
| S2-SES-001 | versioned durable Session | P1-W01/P1-W03 contract、P1-W05 facade/Service、P1-W06 Room v4/process-death recovery 已完成 |
| S2-CTX-001 | typed Context snapshot | source/freshness/trust |
| S2-TWN-001 | Vehicle Digital Twin | debug/test only，显式 simulated |
| S2-SCN-001 | versioned scenario catalog | P1-W02 Plan contract、P2-W05 catalog、P2-W06 resolver、P2-W07 compiler 已完成；Runtime activation 待开发 |
| S2-GRF-001 | durable Agent Graph | P1-W02 node/DAG contract 已完成；durable runtime 待开发 |
| S2-SAF-001 | hard safety interlock | P1-W04 Approval/Undo 绑定合同已完成；A user confirmation cannot override this hard interlock；可信 Safety authority 待接入 |
| S2-EFF-001 | typed Effect lifecycle | P1-W04 intent/observation/approval/undo 合同与状态转换已完成；Service/adapter/持久化待开发 |
| S2-ADP-001 | adapter registry | source/profile/capability/evidence |
| S2-TOL-001 | retry/timeout/partial failure | deterministic terminal result |
| S2-MEM-001 | memory lifecycle | purpose/retention/delete/export |
| S2-EVT-001 | proactive Event trigger | P1-W03 typed event/replay/callback contract 已完成；durable broker/trigger/consent/rate-limit/DND/policy 待开发 |
| S2-MDL-001 | model routing | deadline/quota/privacy/provider |
| S2-ADP-002 | real vehicle adapter | owner/API/permission/readback/rollback |
| S2-OBS-001 | trace/metric/audit | no raw user/model/vehicle payload |
| S2-REL-001 | release/rollback/compatibility | signed manifest + replacement evidence |

Production adapter registry must return adapter unavailable rather than silently falling back to simulation.

## 10. Android 软件增量追踪

下列追踪标题是源码/检查器与需求的稳定关联键：

- R1A Android Gradle foundation trace
- R1B Android device lifecycle trace
- R1C Android 13 exit trace
- R2A compiled AIDL contract trace
- R2B typed Binder runtime trace
- R2C Binder lifecycle and race trace
- R3A Job Supervisor foundation trace
- R3B capability policy trace
- R3C1 action governance core trace
- R3C2 typed Governance Binder trace
- R4A Room durable schema trace
- R4B1 durable task admission trace
- R4B2 durable Runtime wiring trace
- R4B3 durable approval trace
- R4C1 fail-closed restart reconciliation trace
- R4C2A effect prepare and claim trace
- R4C2B effect retry and terminal state trace
- R4C3A effect adapter contract trace
- R4C3B effect material activation trace
- R4C3C production fail-closed activation visibility trace
- R5A1 model provider contract trace
- R5A2 inference resource scheduler trace
- R5B1 deterministic stub provider trace
- R5B2 test-only model router trace
- R5C1 production-safe model runtime readiness trace
- R5D1 Android 13 application-layer deployment acceptance trace
- R6A1 bounded Event runtime trace
- R6A2A durable Event schema trace
- R6A2B durable Event repository trace
- R6A3 Event runtime readiness trace
- R6B1 bounded Memory lifecycle trace
- R6B2 Memory runtime readiness trace
- R6C1 signed built-in Skill runtime trace
- R6C2 fixed governance middleware trace
- R6C3 Skill and Governance readiness trace
- R7A1 aggregate Runtime acceptance trace
- R7B Client2 SDK/Binder migration trace
- R7C Android 13 application integration acceptance trace
- R7D Android 13 software handoff trace
- Client2 navigation-triggered menu trace
- B0 black-box Android 13 engineering trace
- B1 Native Runtime C ABI trace
- B2 Native Runtime process integration trace
- B3 black-box Android 13 preflight trace
- B4 hybrid C/Java software handoff trace
- P1-W01 Session contract V1 trace
- P1-W02 Plan/Node contract V1 trace
- P1-W03 Event contract V1 trace
- P1-W04 Effect/Approval contract V1 trace

这些追踪键只证明对应 Android 软件增量通过其门禁，不代表真实车辆/NPU、Driver/HAL 或量产状态。

## 11. Python 原型退役需求

1. `central-brain/` 下不得存在 Python runtime。
2. 当前 README、架构、接口、交付和 CI 不得引用已删除的 gateway、Linux binding 或旧 Console。
3. Python 只允许作为宿主侧确定性构建/打包工具，不得承载 AIOS 服务或推理。
4. Android ModelProvider、NPU C ABI/JNI、Driver/HAL 和 Safety 合同必须保留。
5. 未来本机模型调试只能作为显式 Android ModelProvider development profile。
6. 未来 Linux 交付必须建立新的非 Python 工作包。

对应 `DEV-026`、`ISSUE-032` 和
`CENTRAL_BRAIN_PYTHON_PROTOTYPE_RETIREMENT.md`。

## 12. GitHub 源码与文档基线需求

本节映射 `APP-004`、`XSC-001/004/005/006`、`NV-G-007` 和
`DEL-001/003/004/005`：

1. Private `LucasWEIchen/CougarOS` 是受维护源码、接口、配置、检查器和工程文档的唯一远端基线。
2. 每个完成的增量必须在同一轮形成 commit、push 和远端检查结果，不允许仅保存在本地工作区。
3. 默认分支根 `README.md` 必须维护当前 Mermaid 总架构图、已开发模块、未开发/外部阻塞模块和近期记录。
4. 架构、模块、接口、交付或状态变化必须与对应 README 更新处于同一推送范围。
5. `central-brain/`、`apk-labs/client2-central-brain/`、`docs/CENTRAL_BRAIN_*`、`.github/`、
   `.githooks/` 和 Central Brain 工具中的正式文件必须由 Git 跟踪。
6. 原始 APK/逆向输入、生成包、密钥、凭据、原始设备日志和车辆/用户/模型 payload 不属于源码
   完整性范围，必须继续排除；可发布二进制只允许进入受审查 GitHub Release。
7. pre-push 与 GitHub Actions 必须执行仓库完整性、发布历史和 README 状态门禁。

状态：`github_source_of_truth=true`、`github_sync_required=true`、
`maintained_project_files_synced=true`、`github_homepage_architecture_current=true`。

## 13. Client2 AIOS 意图编排与 HVAC/Seat 闭环需求

1. 底部导航打开的现有右侧悬浮菜单必须扩展为“意图/计划/执行/结果”四阶段，保持 overlay
   形态，不改成分屏，不另起脱离 Client2 的演示 App。HVAC/Seat 只能作为 Effect 详情和手动兜底，
   不得成为顶层主导航。
2. 用户主输入必须是“我有些疲惫”等简短自然场景表达。模型/规则只能将表达归一化为 allowlist 中
   的 bounded scenario，不能直接创建 Effect 或绕过 Runtime/Governance。
3. HMI 必须连续显示 Intent -> Context -> Plan -> Policy/Approval -> Effect -> Readback 链路，
   包含每一步的状态、原因、目标/当前值和证据；不得只显示模型文本或最终动画。
4. LOW/MEDIUM 且 Policy 允许的 Effect 自动执行；只有 HIGH-risk 或 Policy 明确要求的步骤请求用户
   批准。以疲劳关怀为例，空调/媒体可自动执行，驾驶席靠背调整必须单独确认。
5. HVAC 首版至少包含 power、zone、temperature、fan、AUTO、A/C、SYNC、airflow 和 comfort
   preset；Seat 首版至少包含 zone、heating、ventilation、massage、recline 和三种 preset。
6. 控件不能直接调用 Adapter。手动 HVAC/Seat 必须创建 bounded deterministic scenario session，
   与 cold/fatigue/rest 场景复用 SDK、Policy、Approval、Durable Effect、readback 和 Audit。
7. HMI 必须分别显示 desired/reported/source/quality/revision/effect state；dispatch 不得直接显示完成。
8. 无真实车身信号时，只有 debug/test profile 可以注册 Simulated Adapter，并持续显示 SIMULATED；
   release/production adapter unavailable 时控件必须禁用，不得隐式 fallback。
9. 默认 driving state 为 UNKNOWN_RESTRICTED。HMI 不得提交 speed/gear/belt/occupancy；驾驶席
   recline 在 MOVING/UNKNOWN 下 UI 禁用且 Runtime dispatch count 必须为 0。
10. approval、partial failure、readback mismatch、retry、governed undo、Runtime restart 和面板
   隐藏/重开必须进入 Android 13 ARM64 验收。
11. 所有进入演示 plan 的 HVAC/Seat/Media/Navigation Effect 必须在中控“执行”视图有 target、source、
   progress、reported result 和适用的 stop/cancel/undo projection；禁止只在模型文本中宣称完成。
12. HMI-D0/HMI-D1 以 1920x1080 为固定设计坐标，Panel 必须完全位于 Client2 可见安全区；浏览器
   预览只能等比缩小。主 Panel 使用可辨认背景的半透明浅灰材质，不得恢复接近实色的 0.91 alpha。

完整设计和 22 项验收矩阵见 `CENTRAL_BRAIN_COCKPIT_HMI_CONTROL_LOOP_PLAN.md`。当前状态：

```text
aios_intent_orchestration_ux_ready=true
cockpit_hmi_1920x1080_safe_frame_verified=true
cockpit_hmi_translucent_material_ready=true
cockpit_hvac_surface_implemented=false
cockpit_seat_surface_implemented=false
cockpit_demo_control_loop_implemented=false
real_vehicle_effect_adapter_available=false
```

`production_ready=false`、`target_hardware_validated=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。

## 14. P1-W01 Session contract V1 trace

本增量映射 `S2-SES-001`、`S2-UX-001`、`APP-004`、`XSC-001/006` 和 `NV-G-003`：

1. SDK 新增 `SessionRequest/Handle/Snapshot/Query/Page` 和独立
   `ICentralBrainSessionRuntime` V1；既有 production/diagnostic/governance V1 文件和 checksum 不变。
2. `SessionContract` 限制 UUID、scenario/source/seat/state enum、字符串、deadline、revision、cursor
   和 page；未知 schema/enum 失败关闭。
3. owner 不进入请求体，只能由未来 Runtime Binder principal 派生；HMI 不能提交 speed、gear、belt、
   permission、caller 或 signer 断言。
4. JVM 验证边界和拒绝行为；Android 13/API 33 ARM64 物理控制器 instrumentation 验证 5 个 DTO 的
   真实 Parcel round-trip、oversize reject 和 unknown-version reject，随后卸载临时 test APK。
5. `session-v1.sha256` 和规范化 interface hash 由独立 checker 冻结，并复验既有 V1 checksum。

当前状态：`session_contract_v1_defined=true`、
`session_parcel_physical_android13_arm64_verified=true`、`session_runtime_service_published=false`、
`session_runtime_persistence_wired=false`。P1-W01 是 `contract_defined`，不是 Session Runtime、Client2
闭环、车辆控制或目标硬件验收。

## 15. P1-W02 Plan/Node contract V1 trace

本增量映射 `S2-SCN-001`、`S2-GRF-001`、`FW-S-001`、`NV-F-001`、`NV-F-008` 和 `NV-G-004`：

1. SDK 新增 `ScenarioPlan`、`PlanNode`、`NodeDependency`、`NodePolicy` 四个 versioned structured
   parcelable；既有 task/diagnostic/governance/session V1 文件和 checksum 不变。
2. `PlanNode` 显式携带 timeout、maxAttempts、idempotency、required、compensation 和 policy metadata；
   不允许 JSON、Bundle、FD、SharedMemory 或 HMI 自报 Safety/identity authority。
3. `PlanContract` 只允许详设定义的 11 类 node，限制 node/edge/depth/parallelism/deadline，并拒绝
   unknown schema/type/policy、重复 ID、断链、DAG cycle、unsafe retry 和 compensation loop。
4. JVM 测试覆盖正反合同；Android 13/API 33 ARM64 物理控制器验证 Parcel round-trip、cycle reject 和
   unknown-type reject，随后卸载临时 test APK，`hardware_accessed=false`。
5. `plan-v1.sha256` 和独立 checker 冻结四个 AIDL 源文件并复验前三组 V1 checksum。

当前状态：`plan_contract_v1_defined=true`、
`plan_parcel_physical_android13_arm64_verified=true`、`plan_runtime_published=false`。
P1-W02 是 `contract_defined`，不是 Scenario Catalog/Compiler、完整语义 Graph Validator、durable Graph
执行、车辆控制、NPU 或目标硬件资格。

## 16. P1-W03 Event contract V1 trace

本增量映射 `S2-SES-001`、`S2-EVT-001`、`FW-U-003`、`NV-F-009`、`NV-G-003` 和
`NV-G-007`：

1. SDK 新增 `RuntimeEvent`、`ActionEvent`、`ObservationEvent`、`MessageEvent`、`EventPage` 五个
   versioned structured parcelable，以及独立 `ICentralBrainSessionEvents` V1 和 one-way callback；
   task/diagnostic/governance/session/plan V1 文件和 checksum 不变。
2. `EventContract` 只允许详设定义的 23 类事件，限制 UUID、digest、source/privacy/payload enum、
   page/cursor/display text，并拒绝 payload/type mismatch、sequence gap、forward/missing parent、unsafe
   redaction、cursor discontinuity 和 immutable replay mutation。
3. `getEvents` 的 cursor replay 是权威恢复路径；callback 只通知 event/overflow/closed。query 和 callback
   不接受 caller、signer、permission 或车辆 Safety 断言，未来 Service 必须由 Binder principal 派生 owner。
4. JVM 测试覆盖正反合同；Android 13/API 33 ARM64 物理控制器验证五类 DTO Parcel round-trip、排序、
   parent、redaction 和 cursor replay 拒绝路径，随后卸载临时 test APK，`hardware_accessed=false`。
5. `events-v1.sha256`、七文件合并 interface hash 和独立 checker 冻结合同，并复验全部既有 V1 checksum。

当前状态：`event_contract_v1_defined=true`、
`event_parcel_physical_android13_arm64_verified=true`、`event_runtime_service_published=false`、
`event_callback_service_published=false`。P1-W03 是 `contract_defined`，不是 durable Event broker、主动触发、
Room persistence、车辆控制、NPU、Driver/HAL 或目标硬件资格。

## 17. P1-W04 Effect/Approval contract V1 trace

本增量映射 `S2-EFF-001`、`S2-SAF-001`、`S2-UX-002`、`FW-S-005`、`NV-F-001`、
`NV-G-005..007`：

1. SDK 新增 `EffectIntent`、`EffectObservation`、`ApprovalPrompt`、`UndoHandle` 四个 versioned
   structured parcelable；既有 task/diagnostic/governance/session/plan/event V1 文件和 checksum 不变。
2. `EffectIntent` 只允许一个有界 typed scalar，绑定 session/plan/node/action/capability/area、target、
   idempotency、plan/context digest、context version、risk、verification、deadline 和 compensation metadata；
   不接受 caller、permission、车辆 Safety 状态或原始车辆/模型 payload。
3. `EffectContract` 区分 proposed/authorized/prepared/dispatched/delivered/applied/verified/unknown/
   retry/compensation/terminal 状态，拒绝越级、终态后变更、非连续重试、模拟来源伪装和 identity 漂移。
4. Approval 必须绑定 plan/action/context/policy 且最多存活 5 分钟；恢复时过期或任一 digest/version
   变化均失败关闭。Undo 只绑定已验证 Effect/Observation 和 compensation digest，最多存活 15 分钟，
   表示未来受治理的补偿操作，不是数据库回滚或绕过 Safety 的授权。
5. JVM 测试覆盖正反合同；Android 13/API 33 ARM64 物理控制器 instrumentation 验证四个 DTO Parcel
   round-trip、合法/非法转换、stale approval 和 expired undo，随后卸载临时 test APK，
   `hardware_accessed=false`。
6. `effect-v1.sha256`、四文件合并 identity 和独立 checker 冻结合同，并复验全部既有 V1 checksum。

当前状态：`effect_contract_v1_defined=true`、
`effect_parcel_physical_android13_arm64_verified=true`、`effect_runtime_service_published=false`、
`approval_response_service_published=false`、`undo_service_published=false`。P1-W04 是
`contract_defined`，不是 Effect Runtime、审批 authority、undo executor、durable persistence、车辆控制、
NPU、Driver/HAL 或目标硬件资格。

## 18. P1-W05 SDK facade v2 trace

本增量映射 `S2-SES-001`、`S2-UX-001..003`、`S2-EVT-001`、`APP-004`、`XSC-001/006`、
`NV-G-003/004`、`DEL-001/003..005`：

1. `ScenarioClient` 是应用/HMI 的唯一 Stage 2 会话入口；其 public API 只暴露 typed DTO、稳定
   `Failure.code` 和 `RuntimeEventListener`，禁止 `IBinder`、AIDL Stub/Proxy 或 `RemoteException` 泄漏。
2. `SessionClient` 必须先精确协商 Session/Event V1 version/hash，再允许 open/get/list/cancel/observe；
   callback 必须经串行 executor 分发，stop/close 后的晚到 callback 必须丢弃。
3. `AndroidScenarioTransport` 只使用显式 `CentralBrainRuntimeService` component，并以
   `ACTION_SESSION_RUNTIME`、`ACTION_SESSION_EVENTS` 建立两个独立 Binder；任一 Binder 死亡使本代
   transport 整体失效，调用方显式 reconnect 后恢复 active subscription。
4. Runtime Service 不新增 Manifest component。它按 action 返回 Session/Event Binder，所有操作先以
   Binder UID/package/current signer 通过 default-deny capability，再生成 durable principal fingerprint；
   request DTO 不能提供 owner/permission/Safety authority。
5. P1-W05 初始 registry 只在 Runtime 进程内存活，最大 64 session、每 session 最大 8 个当前合同事件；
   requestId+digest 幂等冲突失败关闭，owner 不可互见，原始 utterance 只参与内存中即时 SHA-256，
   不进入 record、event、snapshot、log 或持久层。
6. 恢复顺序固定为 get snapshot -> cursor replay -> sequence deduplicate -> register callback；P1-W05
   完成 Service rebind，P1-W06 后 Runtime 进程死亡可由 Room v4 恢复。
7. 生产 capability XML 只授权 Demo/Client2；`com.centralbrain.sdk.test` 仅存在于 debug resource overlay，
   且仍要求与 Runtime current signer 相同。release policy 不得包含测试 principal。
8. Android 13/API 33 ARM64 已通过真实 Binder open/replay/reconnect/resubscribe/cancel/close 测试；未访问
   Vehicle/VHAL/NPU/Driver/HAL。

当前状态：`sdk_facade_v2_available=true`、`session_runtime_service_published=true`、
`event_runtime_service_published=true`、`event_callback_service_published=true`、
`active_session_reconnect_resubscribe_verified=true`；P1-W06 后为
`session_runtime_persistence_wired=true`、`session_runtime_process_death_rehydration=true`、
`scenario_execution_enabled=false`、`effect_runtime_service_published=false`、
`approval_response_service_published=false`、`undo_service_published=false`、
`hardware_accessed=false`。

## 19. P1-W06 Room v4 durable Session/Event trace

本增量映射 `S2-SES-001`、`S2-GRF-001`、`S2-EFF-001`、`S2-EVT-001`、`XSC-005/006`、
`NV-G-003/004/006/007`、`NV-P-002`、`DEL-001/003..005`：

1. Room current schema 必须为 v4，并提交 schema JSON。新增 `sessions`、`plans`、`plan_nodes`、
   `runtime_events`、`effect_observations`、`compensations`；保留既有 task/checkpoint/pending-effect/
   outbox/approval/audit/event-cursor 表。
2. `MIGRATION_3_4` 必须把旧 `runtime_session` owner/session-key/state/timestamps 映射到新 `sessions`，
   不能 destructive migration；v1 task/approval、v2/v3 event cursor 和全部既有 durable 数据不得丢失。
   旧行因 sessionId/request 无法满足 Session V1 UUID/canonical request 合同，必须使用固定 legacy digest
   marker，非 terminal state 失败关闭为 `FAILED`，并从 owner-scoped Session V1 查询面隔离。
3. `sessions` 以 owner+clientRequestId unique，Plan 以 session+revision unique，Node/Compensation 具有
   idempotency unique，Event 以 session+sequence unique；Plan/Event/Observation/Compensation 通过
   `ON DELETE CASCADE` FK 归属 Session。
4. `DurableSessionRegistry` 是 Session/Event Binder 的生产 repository。session+initial event 和
   cancellation+terminal event 必须在单个 Room transaction 中提交；callback 只能在 commit 后通知。
5. DB 不保存原始 utterance、Binder/native pointer、任意 Java serialization、签名材料或原始模型 token；
   request 只保存 domain-separated digest，canonical typed event payload 上限 8192 UTF-8 bytes。
6. owner 必须继续由 Binder UID/package/current signer 派生，查询、分页、event replay、cancel 和淘汰均
   owner scoped；容量满时只可删除最旧 terminal Session，全部 active 时失败关闭。
7. migration fixture 必须验证 v1->v4、13-table、WAL、legacy 数据留存、legacy Session V1 隔离、FK、
   owner query index plan 和模拟 crash transaction rollback；不得只以 schema compile 作为迁移证据。
8. Android 13/API 33 ARM64 必须 seed Session，杀死 Runtime 进程，再通过 SDK 重绑恢复相同 sessionId、
   event replay 和 terminal cancel 幂等。callback registration 本身不持久化，进程重启后依赖
   snapshot/cursor replay 重建。
9. 本包不发布 Plan Compiler/Graph Runtime、Effect/approval-response/undo Service，不访问 Vehicle、
   VHAL、NPU、Driver/HAL，不恢复 Python/Linux 或虚拟化路径。

当前状态：`room_schema_version=4`、`room_migration_3_4_verified=true`、
`session_runtime_persistence_wired=true`、`session_runtime_process_death_rehydration=true`、
`scenario_execution_enabled=false`、`effect_runtime_service_published=false`、`hardware_accessed=false`。

## 20. P1-W07 Runtime Contract v2 aggregate trace

本增量映射 P1 全部 Req，重点为 `APP-004`、`S2-SES-001`、`S2-GRF-001`、`S2-EFF-001`、
`S2-EVT-001`、`XSC-001/005/006`、`NV-G-003/004/006/007`、`DEL-001/003..005`：

1. aggregate contract schema version 为 2，但 Session/Plan/Event/Effect wire/DTO 均继续使用冻结 V1；
   不得修改既有 AIDL transaction、interface version/hash 或 checksum manifest。
2. 机器可读合同必须同时绑定四组 wire/DTO、七项 owner capability、SDK facade error contract、Room v4/
   13-table、durable repository 和禁用中的 Plan/Effect/approval-response/undo/scenario capability。
3. public SDK lifecycle error 固定为 `NOT_CONNECTED/PROTOCOL_MISMATCH/TRANSPORT/SUBSCRIPTION/CLOSED`；
   DTO contract violation 保持带 domain prefix 的 `IllegalArgumentException`，authorization 保持
   `SecurityException`，`RemoteException`/Binder primitive 不得泄漏到 public facade。
4. 固定上限：Session page 50、Event page 100、cursor 256 chars、replay 64 pages、callback 4/session 和
   128 total、durable session 64、当前 Event 8/session、canonical Event payload 8192 UTF-8 bytes。
5. Binder target latency 为 protocol 10 ms、open 50 ms、read/cancel 30 ms、callback registration 50 ms；
   这是应用层合同预算，不是目标硬件性能或量产资格。
6. Event V1 terminal page 不前移 opaque cursor 的限制必须保留并显式声明。演进决策是独立 Event V2：
   terminal resume cursor、monotonic ACK、owner/session binding、bounded retention、stale/future reject；
   P1-W07 不发布 V2 Binder，也不关闭 production Event broker blocker。
7. aggregate checker 必须一次执行 V1 checksum、各 P1 checker、Room schema、SDK test source、capability、
   bounds 和 forbidden network/Python fallback 校验；禁止以文档状态替代源码验证。
8. 本增量不访问 Vehicle/VHAL/NPU/Driver/HAL，不启用 Scenario/Plan/Effect，不恢复 Python/Linux/虚拟化。

状态：`runtime_contract_v2_defined=true`、`runtime_contract_v2_verified=true`、
`runtime_contract_v2_physical_android13_arm64_verified=true`、
`frozen_v1_hashes_unchanged=true`、`event_v2_cursor_ack_required=true`、
`event_v2_interface_published=false`、`scenario_execution_enabled=false`、`hardware_accessed=false`。

## 21. P2-W01 canonical vehicle signal trace

本增量映射 `S2-CTX-001`、`S2-TWN-001`、`DEL-001/003..005`：

1. `VehicleSignalPath` 必须是固定 allowlist，不接受任意 path。首版精确包含 speed、gear、parking
   brake、HVAC active/cabin temperature/target/fan、seat occupied/belted/heating/ventilation/recline 12 项。
2. 每个 path 固定 scalar type、unit、允许 area 和 maximum age。`SignalValue` 只允许 boolean、integer、
   finite decimal、最长 64 字符无控制符 text；禁止 arbitrary `Object`、JSON、Bundle/Parcel payload。
3. 时间戳同时携带 source wall time 和 receive-side elapsed realtime；freshness 只使用 monotonic receive
   time，拒绝 future receive time、`VALID` stale value 和 `STALE` fresh value。
4. `VALID/STALE` 必须携带 typed scalar；`UNAVAILABLE/ERROR/CONFLICT` 不得携带 scalar。只有 `VALID`
   可直接参与后续决策，具体安全必需字段由 P2-W04 policy 继续收敛。
5. source 为 `SIMULATED/AAOS/VENDOR/DERIVED` provenance。AAOS/VENDOR enum 不证明 provider 已配置、
   property 已映射、权限可用或 production authorized。
6. P2-W01 不做 capability range、desired/reported twin、Context snapshot 或 production adapter；这些分别
   由 P2-W02..P2-W04/P8 实现。
7. static/JVM/API 33 ARM64 debug probe 必须验证 allowlist、type、unit/area、freshness/quality，并持续断言
   `vehicle_signal_provider_wired=false`、`vehicle_property_mapping_configured=false`、`hardware_accessed=false`。
8. 本增量不访问 Vehicle/VHAL/vendor service、NPU、Driver/HAL，不恢复 Python/Linux/虚拟化路径。

状态：`vehicle_signal_schema_defined=true`、`vehicle_signal_path_allowlist_count=12`、
`vehicle_signal_schema_android13_arm64_verified=true`、`vehicle_signal_provider_wired=false`、
`vehicle_property_mapping_configured=false`、`hardware_accessed=false`。

## 22. P2-W02 vehicle capability catalog trace

本增量映射 `S2-TWN-001`、`S2-ADP-001`、`DEL-001/003..005`：

1. catalog 必须精确包含 HVAC target temperature/power/fan、seat heating/ventilation/recline、media
   playback、navigation POI 8 项稳定 ID；拒绝 duplicate/null capability，保持确定顺序和 immutable view。
2. 每项显式声明 readable、writable、simulatable、productionAvailable、productionAuthorized；当前 catalog
   后两项全部为 false，authorized 要求 available+writable，禁止 silent simulation fallback。
3. target contract 固定 scalar type、unit、areas 和 range：temperature 16..30 celsius/0.5，fan 0..7，
   seat heat/vent 0..3，recline 0..60 degree，HVAC power boolean，media PLAY/PAUSE/STOP，POI 最长 128 字符。
4. 数值必须 finite、范围内并对齐 step；text 必须非空、bounded、无 control character，存在 allowlist 时
   必须精确匹配。禁止 arbitrary `Object`、JSON、Bundle/Parcel target。
5. Vehicle domain capability 必须绑定 type/unit/area 一致的 reported `VehicleSignalPath`；Media/Nav 不伪造
   vehicle readback path。Seat recline 为 HIGH risk，并要求 speed/gear/parking brake/occupancy/belt fresh。
6. 这些 range/risk/dependency 是 Stage 2 软件与 debug/test 仿真合同，不是 OEM 标定、安全认证、VHAL
   property、权限或 production authorization；真实值只能在 P8 依据目标证据变更并版本化。
7. static/JVM/API 33 ARM64 debug probe 必须验证 catalog count、ranges、dependencies 和 production fail
   closed，并持续断言 adapter registry/property mapping/hardware 未接线。
8. 本增量不创建 Digital Twin store、Context snapshot、adapter、Effect 或 NPU，不访问 Vehicle/VHAL/
   vendor service/Driver-HAL，不恢复 Python/Linux/虚拟化。

状态：`vehicle_capability_catalog_defined=true`、`vehicle_capability_count=8`、
`vehicle_capability_catalog_android13_arm64_verified=true`、
`vehicle_production_capability_authorized_count=0`、
`vehicle_capability_adapter_registry_wired=false`、`vehicle_property_mapping_configured=false`、
`hardware_accessed=false`。

## 23. P2-W03 Vehicle Digital Twin Store trace

本增量映射 `S2-TWN-001`、`DEL-001/003..005`：

1. `VehicleDigitalTwinStore` 必须以结构化 path+area key 分离 desired/reported，两者不能相互覆盖或
   由 HMI 本地状态伪造；所有 state-changing write 分配一个全局单调 store revision。
2. Reported update 必须验证 receive-side monotonic freshness；old source/receive time 与同 timestamp
   conflicting payload 必须失败关闭，exact replay 幂等且不推进 revision。
3. Desired 必须使用 boolean/integer/finite decimal/bounded text typed factory，匹配 canonical path 的
   scalar/unit/area；TTL 必须大于 0 且不超过 15 分钟。CAS/clear 使用该 key 当前 desired revision。
4. `DigitalTwinSnapshot` 必须在同一 store lock/revision window 中复制 path-filtered desired/reported，
   collection immutable；capture time 投影 reported effective quality，不修改原始 observation。
5. Reconciliation 必须区分 no desired、desired expired、pending reported、reported stale/unavailable、
   matched 与 mismatch；requested state 不得被描述为 delivered/applied/verified evidence。
6. JVM 必须覆盖并发 CAS、stale/conflict reject、idempotency、TTL/freshness、snapshot immutability/filter
   与 reconciliation；API 33 ARM64 debug probe 必须在无硬件访问下验证同一合同。
7. P2-W03 是进程内 foundation，不接 Room、production Runtime/Governance Service、adapter registry、
   Vehicle/VHAL/vendor property、Effect 或 NPU。P2-W04 只能消费 immutable snapshot 构造 Context。
8. 本增量持续断言 `vehicle_digital_twin_persistence_wired=false`、
   `vehicle_digital_twin_adapter_wired=false`、`vehicle_property_mapping_configured=false`、
   `hardware_accessed=false`；不触发 Driver/HAL、Python/Linux 或虚拟化开发。

状态：`vehicle_digital_twin_store_defined=true`、
`vehicle_digital_twin_android13_arm64_verified=true`、
`vehicle_digital_twin_persistence_wired=false`、`vehicle_digital_twin_adapter_wired=false`、
`hardware_accessed=false`。

## 24. P2-W04 trusted Context snapshot trace

本增量映射 `S2-CTX-001`、`S2-SAF-001`、`DEL-001/003..005`：

1. Context 必须从一个 atomic `DigitalTwinSnapshot` revision 与 Runtime-owned Safety state 构造；HMI、
   模型或请求 payload 不得选择任意字段、source trust、Safety/Driving state 或 restricted 结果。
2. `ContextFieldPolicy` 必须是固定 allowlist。General 至少要求 fresh speed/gear/parking brake；seat recline
   还要求 selected-seat occupancy/belt/reported angle。未解析 seat area 必须作为 required missing 失败关闭。
3. Field 必须区分 AVAILABLE/MISSING/STALE/UNAVAILABLE/ERROR/CONFLICT，并分别输出 missing required、
   stale、conflict 与 non-production-trusted report；不能把 desired state 或缺失值伪装为 reported scalar。
4. Runtime state 必须不晚于 Twin capture 且 age <= 1000 ms。Safety stale/UNKNOWN/DEGRADED/EMERGENCY、
   Driving UNKNOWN、motion disagreement 或 required field 不可决策必须设置 `restricted=true`。
5. Driving 派生采用保守规则：speed > 0.5 km/h 为 MOVING；只有 speed <= 0.5、P/PARK 且 parking brake
   engaged 可由 signals 证明 PARKED。完整 MOVING Context 不自动 restricted；action-specific Safety Policy
   仍必须禁止行驶中驾驶席 recline，用户批准不能覆盖硬联锁。
6. Snapshot 必须 immutable、schema/versioned，SHA-256 digest 必须绑定 policy、Twin/Runtime revision、
   capture time、seat、memory availability、派生状态和全部 typed field，等价输入结果确定。
7. SIMULATED 必须可见；AAOS/VENDOR 仍只是 provenance，不能自动成为 production trust。P2-W04 固定
   `productionTrusted=false`，直到 P8 provider/property/permission/activation evidence 独立验收。
8. JVM/API 33 ARM64 debug probe 必须覆盖 complete/missing/stale/conflict、motion conflict、seat policy、
   digest 和 trust；本增量不接 production Service/Room/adapter，不访问 Vehicle/VHAL/NPU/Driver/HAL。

状态：`context_snapshot_defined=true`、`context_snapshot_android13_arm64_verified=true`、
`context_snapshot_production_trusted=false`、`context_snapshot_production_wired=false`、
`vehicle_signal_provider_wired=false`、`hardware_accessed=false`。

## 25. P2-W05 Scenario manifest/schema trace

本增量映射 `S2-SCN-001`、`S2-SAF-001`、`DEL-001/003..005`：

1. Manifest 必须是 APK build-owned v1 asset，Runtime/HMI/模型不能提交、替换或动态扩展 manifest；首批
   精确包含 `scene.comfort.cold.v1`、`scene.fatigue.assist.v1`、`scene.rest.nap.v1` 三项。
2. 每项必须显式携带 schema/scenario/version、supported source/zone、fixed Context policy、required/
   optional canonical Context/capability、最高 risk、bounded plan template、fallback 和 UI resource key。
3. Parser 必须使用 strict structured JSON parser，input <=64 KiB、depth<=16、token<=4096；unknown/
   duplicate field、null、trailing content、type/version/enum/path/capability 错误和 oversize 必须拒绝。
4. Template 必须复用 Plan Contract node allowlist，并限制 node<=64、edge<=256、depth<=16、parallel<=8、
   timeout/retry/idempotency、required/optional、compensation、policy/risk/approval/driving/failure metadata。
5. duplicate node/edge/scenario ID、unknown dependency/capability、DAG cycle、invalid compensation、risk mismatch、
   HIGH 无 approval、fallback 引用 required/unknown node 必须失败关闭。duplicate scenario ID 的全部副本禁用，
   单个 invalid asset 不影响其他 unique valid scene。
6. Fatigue/rest 的 seat-recline template 必须固定 `PARKED_ONLY` 和 approval-required；该 metadata 不能授予
   approval，不能覆盖 driving/safety hard interlock，也不能直接生成 Effect target 或 dispatch。
7. JSON Schema 与 SHA-256 sidecar 必须在 CI 和 packaged APK assets probe 中验证。Sidecar 只证明 build
   identity，不是独立 artifact 密码学签名、revoke/rollback 或 production trust evidence。
8. JVM/API 33 ARM64 probe 必须覆盖三项 catalog、schema/digest、unknown/oversize/duplicate/cycle/isolation；
   P2-W05 不接 Resolver/Compiler/Graph/Effect/production Service，不访问 Vehicle/VHAL/NPU/Driver-HAL。

状态：`scenario_manifest_schema_version=1`、`scenario_catalog_count=3`、
`scenario_manifest_android13_arm64_verified=true`、`scenario_manifest_artifact_crypto_verified=false`、
`scenario_catalog_production_trusted=false`、`scenario_runtime_wired=false`、
`scenario_graph_execution_enabled=false`、`effect_dispatch_enabled=false`、`hardware_accessed=false`。

## 26. P2-W06 Deterministic ScenarioResolver trace

本增量映射 `S2-SCN-001`、`S2-SAF-001`、`DEL-001/003..005`：

1. Resolver 必须只选择 `ScenarioCatalog` 中已注册 manifest。非空显式 scenario ID 的优先级高于文本，
   HMI 按钮无需模型；catalog 不存在该 ID 时失败关闭，不能动态创建 scene/capability。
2. 文本输入限制为 <=256 字符且拒绝 control character；只允许 NFKC/Locale.ROOT 规范化后的固定中英文
   alias。unknown 必须拒绝；由受控分隔符形成的多个不同 scene 候选必须返回 ambiguous 并拒绝。
3. Resolution 必须校验 manifest 支持的 source/zone、Context seat zone、固定 Context policy、required fresh
   canonical Context path、runtime-owned capability snapshot 和 capability area。任一 required 缺失即拒绝，
   optional 缺失只能在 manifest fallback 允许时降级。
4. `PARKED_ONLY` capability node 在 MOVING/UNKNOWN 下不得成为可用 branch：required node 使场景拒绝，
   optional node 使场景降级。approval metadata 不能覆盖该 hard gate。
5. Capability snapshot 必须区分 `SOFTWARE_SIMULATION` 与 `PRODUCTION`。当前 capability/Context 均没有
   production trust；production profile 必须拒绝，不能隐式回退 simulation。
6. `ScenarioResolution` 只允许 immutable `ACCEPTED/DEGRADED/REJECTED`、稳定 reason code、候选 ID 和
   unavailable required/optional 列表；SHA-256 identity 必须绑定 request、Context、capability、manifest
   digest 与所有 decision metadata，且不得保留原始文本以外的新 payload 副本。
7. Resolution 必须固定 `isExecutable=false`、`isProductionTrusted=false`。P2-W06 不编译 Plan、不创建
   Graph/Effect、不调用 ModelProvider，不接 production Service/Room/adapter，不访问 Vehicle/VHAL/NPU/
   Driver/HAL。
8. JVM 与 Android 13/API 33 ARM64 probe 必须覆盖 explicit/cold/fatigue/rest、unknown/ambiguous、required
   reject/optional degrade、moving fatigue degrade/moving rest reject、production fail-closed 与 digest replay。

状态：`scenario_resolver_defined=true`、`scenario_resolution_schema_version=1`、
`scenario_resolver_android13_arm64_verified=true`、`scenario_resolver_model_invoked=false`、
`scenario_resolver_runtime_wired=false`、`scenario_compiler_wired=false`、
`scenario_graph_execution_enabled=false`、`effect_dispatch_enabled=false`、`hardware_accessed=false`。

## 27. P2-W07 ScenarioPlanCompiler trace

本增量映射 `S2-SCN-001`、`S2-GRF-001`、`S2-SAF-001`、`DEL-001/003..005`：

1. Compiler 只能消费 `ACCEPTED` 或 `DEGRADED` Resolution，并必须同时接收产生该 Resolution 的原始
   immutable Context 和 Capability snapshot；`REJECTED` 不得携带或编译 manifest。
2. 编译前必须复算 Resolution digest，并逐项核对 Context digest、Capability digest、scenario ID、manifest
   schema/version/artifact digest、Context policy、required freshness 和 required capability availability；任一
   漂移必须以 `CB_SCENARIO_COMPILE` 失败关闭。
3. 输出必须使用 P1-W02 冻结的 `ScenarioPlan/PlanNode/NodeDependency/NodePolicy` typed DTO。内部 owner
   必须 immutable，向 AIDL/Runtime 边界只能返回 deep copy；Plan 与每个 node input 的 SHA-256 必须绑定
   resolution、manifest、Context、Capability 及完整 DAG metadata。
4. `DEGRADED` 只能剔除 manifest `DEGRADED_OPTIONAL_ONLY` 明确列出的 optional branch；required node、未声明
   fallback、digest 不一致或无法映射的 unavailable capability 必须拒绝。无效 optional 前驱必须一并裁剪，
   不能留下无意义 approval root。
5. `PlanGraphValidator` 除 P1 结构上限外，必须验证 required Effect 可达同 capability verify、HIGH Effect
   具有 approval predecessor、compensation 引用合法，并确保 MOVING/UNKNOWN graph 不含 `PARKED_ONLY`
   node 或驾驶席 `vehicle.seat.recline` dispatch。approval metadata 不能覆盖该 gate。
6. Manifest 没有 target scalar，P2-W07 不得自行生成温度、风量、角度或媒体/导航参数。compiled Plan 固定
   `isExecutable=false`、`isProductionTrusted=false`；Graph Runtime 在 P3、Effect target/dispatch 在后续受
   Governance 约束的工作包实现。
7. JVM 与 Android 13/API 33 ARM64 probe 必须覆盖 cold golden plan、optional fallback、moving fatigue
   seat branch absent、stable digest、immutable transport copy、cycle reject、required verify 与 HIGH approval。
8. 本包不接 production Service/Room/Session，不发布 Plan Runtime，不访问 Vehicle/VHAL/NPU/Driver/HAL，
   不恢复 Python 或 Linux frontend。

状态：`scenario_plan_compiler_defined=true`、`scenario_plan_schema_version=1`、
`scenario_plan_compiler_android13_arm64_verified=true`、`scenario_plan_compiler_runtime_wired=false`、
`scenario_plan_runtime_published=false`、`scenario_graph_execution_enabled=false`、
`effect_dispatch_enabled=false`、`hardware_accessed=false`。

## 28. P2-W08 Simulated Effect Adapter base trace

本增量映射 `S2-ADP-001`、`S2-EFF-001`、`DEL-001/003..005`：

1. `SimulatedEffectAdapter`、`SimulationClock`、`FaultInjectionProfile` 必须只存在于 Runtime `src/debug`
   source set；main/release 不得包含同名类，production Runtime/Governance Service 不得引用或注册。
2. Simulation descriptor 必须固定 `simulation=true`、`productionAuthorized=false` 和 observation source
   `SIMULATED`；不得通过 adapter ID、Effect descriptor 或 debug signer 提升 production trust。
3. Base adapter 必须复用现有 typed `EffectAdapter` destination/token/digest defensive-copy contract，使用
   `TOKEN_DEDUPLICATED` 和 linearizable current status；同 token/同 invocation 返回原始 apply result，同 token/
   不同 invocation 必须拒绝。
4. Process-memory record 上限固定 128。P2-W08 不持久化 canonical payload/envelope，不把原始 Effect/user/
   vehicle payload 写入 audit、log 或 probe；reset 只清除 debug simulated state。
5. `SimulationClock` 必须是显式 monotonic manual clock，不得 `sleep` 或依赖 wall clock；单次 advance 受限，
   overflow 失败关闭。`FaultInjectionProfile` 必须 immutable/digested，timing fault 限制为 1..60000 ms。
6. Fault mode 至少包括 NONE、DELAY、TIMEOUT、RETRYABLE_FAILURE、TERMINAL_FAILURE 和
   READBACK_MISMATCH。Delay 在时钟到点前 delivery/readback 均 pending，之后只执行一次 apply callback；
   timeout 保持 delivery UNKNOWN，readback 到期为 TIMED_OUT。
7. Delivery 与 readback 必须分离：READBACK_MISMATCH 的 delivery 可以是 APPLIED，但 simulation observation
   必须为 MISMATCH、source SIMULATED、productionTrusted=false；不能把投递成功伪装为 verified Effect。
8. 本包不解析 HVAC/Seat/Media/Nav target、不更新 Digital Twin、不接 compiled Plan/Graph/Room/Effect Service，
   不访问 Vehicle/VHAL/NPU/Driver-HAL。JVM、debug/release compile 和 API 33 ARM64 probe 必须验证边界。

状态：`simulated_effect_adapter_base_defined=true`、`simulated_effect_adapter_debug_only=true`、
`simulated_effect_adapter_release_source_absent=true`、
`simulated_effect_adapter_android13_arm64_verified=true`、
`simulated_effect_adapter_production_registered=false`、`simulated_effect_adapter_runtime_wired=false`、
`effect_dispatch_enabled=false`、`hardware_accessed=false`。

## 29. P2-W09 Simulated HVAC adapter trace

本增量映射 `S2-ADP-001`、`S2-EFF-001`、`DEL-001/003..005`：

1. `SimulatedHvacEffectAdapter` 必须只存在于 Runtime `src/debug`，继承 P2-W08 base；main/release source
   和 production Runtime/Governance Service 不得包含、引用或注册。
2. destination 固定 `vehicle.hvac`。canonical payload 是 version 1 fixed binary typed absolute target，
   必须精确绑定 action/capability/area/scalar，不接受自由文本 target、相对增减、未知字段或尾随字节。
3. 支持且仅支持 HVAC power、target temperature、fan level。area/range/step 必须复用 P2-W02 catalog：
   power cabin boolean；temperature 四座区 16..30 celsius/0.5；fan cabin/前排区 0..7 level/1。
4. capability 必须 writable+simulatable 且 `canUseProduction=false`；enum provenance、debug signer 或合法
   range 不能提升 production availability/authorization。
5. invocation admission 必须先完成 payload/action/area/range validation，再向 adapter-owned P2-W03 Twin 写
   absolute desired，TTL 固定 180000 ms。失败 validation 不得创建 Effect record 或 Twin side effect。
6. NONE/DELAY 到期只写一次 source SIMULATED、quality VALID 的 reported。Delay 到期前 desired 可见、
   reported absent；duplicate apply/status 不得增加 Twin revision。
7. TIMEOUT、RETRYABLE_FAILURE、TERMINAL_FAILURE 不得伪造 reported；READBACK_MISMATCH 的 delivery 可为
   APPLIED，但 Twin 必须 reconcile MISMATCH，base observation 也必须 MISMATCH。
8. reset 必须清除本 adapter 的 process-memory record 与隔离 Twin。不得写 shared production Twin、Room、
   audit raw payload、Plan/Graph/Effect Service，或访问 Vehicle/VHAL/NPU/Driver-HAL。
9. JVM、debug/release compile 和 Android 13/API 33 ARM64 probe 必须覆盖 success、canonical round-trip、
   out-of-range/zone/action/trailing reject、delay、timeout、retry/terminal、mismatch 和 idempotency。

状态：`simulated_hvac_adapter_defined=true`、`simulated_hvac_typed_target_verified=true`、
`simulated_hvac_desired_reported_verified=true`、`simulated_hvac_android13_arm64_verified=true`、
`simulated_hvac_debug_only=true`、`simulated_hvac_release_source_absent=true`、
`simulated_hvac_production_registered=false`、`simulated_hvac_runtime_wired=false`、
`effect_dispatch_enabled=false`、`hardware_accessed=false`。

## 30. P2-W10 Simulated Seat adapter trace

本增量映射 `S2-ADP-001`、`S2-SAF-001`、`DEL-001/003..005`：

1. `SimulatedSeatEffectAdapter` 必须只存在于 Runtime `src/debug`，继承 P2-W08 base；main/release source
   和 production Runtime/Governance Service 不得包含、引用或注册。
2. destination 固定 `vehicle.seat`。version 1 fixed-binary canonical target 必须精确绑定 action/capability/
   area/scalar/approval digest，不接受自由文本、相对动作、未知字段、尾随字节或非 canonical 编码。
3. 支持且仅支持 Seat heating、ventilation、recline。area/range/step 必须复用 P2-W02 catalog：heat/vent
   driver/passenger 0..3 level/1；recline driver/passenger 0..60 degree/1。
4. capability 必须 writable+simulatable 且 `canUseProduction=false`。Seat provider 与 approval verifier
   必须由 debug/test 注入，并固定为 simulation-only、非 production authority。
5. heating/ventilation admission 必须验证 fresh occupied seat；recline admission 还必须验证 fresh
   NORMAL+PARKED、driver availability、unbelted 和与 canonical target 绑定的有效 approval digest。
6. recline 在实际 dispatch 前必须再次读取 Safety、occupancy/belt 和 approval。MOVING、UNKNOWN、非 NORMAL、
   stale、unoccupied、belted、driver unavailable 或 approval 变化均永久拒绝；不得写 reported。
7. admission 后写 adapter-owned desired，TTL 180000 ms。NONE/DELAY 完成时只写一次 source SIMULATED、
   quality VALID 的 reported；duplicate apply/status 不得增加 Twin revision。
8. delayed recline 必须提供 0..100 的有界 progress observation；完成前不得把部分角度伪造为 authoritative
   reported。TIMEOUT/retryable/terminal 不写 reported，READBACK_MISMATCH 必须可由 Twin reconciliation 观察。
9. reset 必须清除 process-memory record、operation 与隔离 Twin；不得接 shared Runtime/Room/Plan/Graph/
   Effect Service，不得访问 Vehicle/VHAL/NPU/Driver-HAL。
10. JVM、debug/release compile 和 Android 13/API 33 ARM64 probe 必须覆盖 parked approval、moving/unknown
    reject、belt/motion/approval race、heat/vent、partial progress、fault/readback 和 idempotency。

状态：`simulated_seat_adapter_defined=true`、`simulated_seat_typed_target_verified=true`、
`simulated_seat_recline_safety_verified=true`、`simulated_seat_dispatch_revalidation_verified=true`、
`simulated_seat_progress_verified=true`、`simulated_seat_android13_arm64_verified=true`、
`simulated_seat_debug_only=true`、`simulated_seat_release_source_absent=true`、
`simulated_seat_production_registered=false`、`simulated_seat_runtime_wired=false`、
`effect_dispatch_enabled=false`、`hardware_accessed=false`。
