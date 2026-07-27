# 中央大脑架构需求基线

版本：1.2
日期：2026-07-27
状态：Android 13 实际工程基线

## P4-R7 render fidelity, dynamic HVAC and orbit requirement

`P4-R7` 要求 Client2/RenderService 配对 APK 在 1920x1080 黑盒 Android 13 座舱上：

1. `TuanjieView` 请求 1.5 倍内部渲染尺度，真机必须以日志和截图确认是否被采纳；
2. 双区温度使用 Unity 原生动态文本，默认 26.5°C、范围 18.0-30.0°C、步进 0.5°C；
3. 场景调温必须逐级呈现，手动 +/- 与场景输出共享同一状态机；
4. Unity Pan recognizer 必须保留经原厂 bundle 验证的 InputSystem 配置
   `targetInputDisplay=2`、`eventSystemRaycastCheck=true`、`useFingerPolling=false`。
   Android MotionEvent `displayId=0`、RenderService 渲染 `DisplayIndex=1` 与 Unity
   InputSystem target 是三个不同标识空间，不得根据数值相似性改写。Client2 overlay 和观察型
   listener 不得消费滑动，原车门点击仍须有效；旋转必须由目标物理触摸输入验收。

动态温区由 Client2 调用 RenderService
`c2sSendMessage(zoneObject, "set_text", temperatureLabel)`，不得恢复 Android 温度覆盖层。
本要求只形成应用层 HMI 仿真，不修改 `libtuanjie.so`、系统镜像、Framework/BSP 或
Vehicle/VHAL/CAN/Driver-HAL。没有物理触摸 event node 的测试板只能验证非旋转子项，不得将
ADB 合成 swipe 提升为物理触摸旋转证据。

2026-07-27 生产板应用层复验已覆盖 1.5 render scale/2880x1620 framebuffer，以及经目标
以太 OpenClaw 完成的 Cold、Fatigue 和 multimodal 场景。生产板存在
`ft7252-ts-01` 物理触摸 event node，但在记录真实手指连续滑动改变车模朝向且车门点击仍
有效前，P4-R7 仍不得标记完整目标硬件验收。模型输出只进入白名单 Graph 和 UI 仿真
Effect；OpenClaw 外部算力访问不等于 NPU 直连或车辆执行器访问。

当前 `unity_dynamic_temperature_defined=true`、
`unity_temperature_range_18_30=true`、`unity_temperature_step_0_5=true`、
`unity_vendor_orbit_input_preserved=true`、
`unity_render_scale_1_5_requested=true`、
`p4_r7_testboard_partial_verified=true`、
`p4_r7_production_android13_arm64_partial_verified=true`、
`p4_r7_testboard_android13_arm64_verified=false`、
`vehicle_bus_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`。Req IDs：`APP-004`、`S2-HMI-001..004`、
`S2-UX-002/003`、`DEL-004`；tracking：`DEV-134`、`ISSUE-061`；stage
`P4-R7-RENDER-HVAC-ORBIT`。

## P4-R6 Unity-native HVAC and seat animation correction

`P4-R6` 修正 Client2 演示闭环的两个 HMI 语义错误：Fatigue 场景的驾驶席靠背必须从 15 度
向 30 度展开/后仰；Cold 场景的 28.0°C 必须由 RenderService 的 Unity 原生双区温区显示，
不得再使用覆盖 Unity 画面的 Android `TextView`。Client2 通过原生 `TuanjieView` 触屏通道
触发 RenderService 内受维护的 Unity Button 状态；RenderService bundle 保留原生 26.5°C，
并新增驾驶席和乘员席原生 28.0°C 状态。

该闭环只属于 Android 应用层 HMI 仿真。它不写 Vehicle/VHAL/CAN，不读取真实 HVAC/Seat
readback，不修改系统镜像、厂商 Framework/BSP 或 `libtuanjie.so`。Client2 与 RenderService
debug APK 必须成对安装。`testboard` 已完成 Android 13 ARM64、真实 OpenClaw 场景和视觉复核；
生产板最终包复测因设备未被 ADB 枚举而保持开放。

当前 `seat_recline_expansion_direction_verified=true`、
`unity_native_dual_zone_hvac_state_defined=true`、
`android_temperature_overlay_present=false`、
`testboard_android13_arm64_verified=true`、
`production_board_final_package_retest=false`、
`vehicle_bus_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`。Req IDs：`APP-001/004`、`S2-HMI-001..004`、
`S2-UX-002`、`S2-ADP-001/002`、`S2-SAF-001`、`DEL-001/004`；tracking：
`DEV-133`、`ISSUE-033/060`；stage `P4-R6-UNITY-NATIVE-HVAC-SEAT`。

## P4-R5 cabin shopping and route-planning trace

`P4-R5` 将原“饮水辅助”纠正为两个顶层业务服务：购物服务与路径规划服务。饮用水只是当前受控
图片产生的商品类别，模型不得把年龄、身份、家庭关系、容器为空或口渴作为视觉事实。模型只允许
提出 `shopping.search_products`、`shopping.prepare_order` 和
`navigation.plan_purchase_route` 候选；订单提交与导航启动必须分别绑定当前节点 digest 和显式确认。

debug Runtime 已以 `scene.cabin.multimodal.assist.v1` v2 编排 13 个 Context/Model/Policy/
Approval/Tool/Summary 节点，包含六个购物/路径 Tool 和三个独立确认，不生成 HVAC/Media/车辆
Effect。Client2 按真实 snapshot 显示商品、商户、订单和路线；订单结果固定
`ORDER_NOT_DISPATCHED`，导航结果固定 `NAVIGATION_SIMULATED`。生产 Commerce、Payment 和
Navigation adapter 仍为空接口，禁止静默回退 debug simulation。

详设：[CENTRAL_BRAIN_CABIN_SHOPPING_ROUTE_PLANNING_DESIGN.md](CENTRAL_BRAIN_CABIN_SHOPPING_ROUTE_PLANNING_DESIGN.md)。
当前 `shopping_route_planning_requirement_defined=true`、
`shopping_route_planning_debug_software_implemented=true`、
`p4_r5_open_work_package_count=0`、
`production_openclaw_ethernet_verified=true`、
`production_openclaw_multimodal_verified=true`、
`production_target_ipv4_configuration_persistent=false`、
`production_ready=false`、`target_hardware_validated=false`；tracking：
`DEV-130/131/132`、`ISSUE-057/058/059`；stage `P4-R5-SHOPPING-ROUTE`。

## P4-R1 implementation trace

`S2-SCN-001/S2-GRF-001/S2-EFF-001/S2-SAF-001/S2-UX-003` 要求应用输入能形成可观察的
Intent -> Plan -> Graph -> Effect -> readback 链。`P4-R1` 以独立、版本化、哈希冻结的 Orchestration V1
承载该链，不修改 Session/Plan/Effect V1。所有 start/read/approval/undo/cancel 请求必须先由 Binder calling UID
推导 owner 并绑定已有 durable Session；Binder 只发布有界元数据与 digest，不发布模型文本、车身 target、
reported value、checkpoint 或授权材料。

release backend 在 production Context/Safety/Effect/Undo authority 未接入时必须结构化阻断。debug backend 只能
在显式 simulation profile 与显式模拟运动状态下运行；其 approval response 仅是仿真输入，不得成为 production
grant。Room 只持久化 recovery metadata；重启必须将无可信 evidence 的未完成执行置为 `STUCK` 且 Effect replay
为零。状态：`orchestration_v1_interface_published=true`、`orchestration_room_projection_wired=true`、
`orchestration_android13_arm64_verified=false`；里程碑 `P4-R1`。

## P6-EV2 implementation trace

`S2-EVT-001`、`FW-U-003`、`NV-G-004/006/007` 和 `XSC-001/005/006` 的 Event cursor
演进采用独立 V2 wire surface，禁止修改冻结 V1。V2 terminal page 必须始终返回 owner/session-bound opaque
resume cursor；subscription ACK 必须写入 Room、严格单调、有界，并拒绝跨 owner/session、stale、future 和
source regression。SDK 必须在 action/version/hash 全部匹配时使用 V2，否则仅回退冻结 V1，不得把协议不匹配
解释为成功。状态：`event_v2_interface_published=true`、`event_v2_room_ack_wired=true`、
`event_v2_sdk_negotiation_wired=true`、`event_v2_android13_arm64_verified=false`；里程碑 `P6-EV2`。

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
| APP-001 | 座舱 HMI | 只能经 SDK/Binder 访问 Runtime，不直连模型或车控 | Client2 四阶段、HVAC/Seat、七阶段 timeline 与正式 Orchestration debug 闭环已完成；production 车控外部阻塞 |
| APP-002 | 座舱服务 | 作为受治理 Business/Foundation/Atomic service 暴露 | 外部阻塞 |
| APP-003 | Agent App | 通过 Session/Plan/Tool/Action/Effect 执行 | Session/Plan/Tool/Action/Effect debug composition 已完成；production authority 外部阻塞 |
| APP-004 | AI SDK | 提供稳定 typed client facade、异步任务和故障语义 | Android AAR 已实现 |
| APP-005 | Cluster/TBOX App | 与座舱域按服务合同隔离 | 外部阻塞 |
| APP-006 | Cluster/TBOX Service | 声明显示、媒体、远控、OTA 边界 | 外部阻塞 |
| APP-007 | ADAS App | 只读状态或发起受控请求，不进入安全闭环 | 外部阻塞 |
| APP-008 | ADAS Service | 通过受治理 adapter/Protocol Binding 暴露 | 外部阻塞 |
| APP-009 | 诊断/标定/Trace App | 必须受身份、capability 和 audit 控制 | 受保护 diagnostics/probe/evidence 软件接口完成；目标标定责任人外部阻塞 |
| APP-010 | 诊断/标定/Trace Service | 不允许 App 绕过 Runtime 直达底层 | 合同已定义 |

## 5. Framework 需求

### 5.1 Uni Info Bus

| Req ID | 对象 | 必须语义 | 当前状态 |
| --- | --- | --- | --- |
| FW-U-001 | Context | 车辆、用户、环境的版本化 snapshot | typed snapshot、freshness/trust 与 debug decision composition 完成；production source 外部阻塞 |
| FW-U-002 | State | 服务、模型、车辆状态查询 | diagnostics/readiness 软件接口完成；真实车辆状态源外部阻塞 |
| FW-U-003 | Event | publish/subscribe/cursor/overflow/replay | Event V1/V2、Room cursor/ACK、bounded broker/QoS 软件完成；production middleware 外部阻塞 |
| FW-U-004 | Action | 所有副作用必须经过 policy/approval/audit | typed governance + effect gate 完成 |
| FW-U-005 | Service | 统一服务调用和错误 envelope | typed contract/error 与 debug adapter composition 完成；真实 adapter 外部阻塞 |
| FW-U-006 | Tool | schema、capability、安全状态和超时 | Tool/Skill contract、resolver/executor 与 debug composition 完成；production signer/owner 外部阻塞 |
| FW-U-007 | Permission | 身份来自 Binder，不接受请求体自报权限 | 已实现 |
| FW-U-008 | Extension | 扩展不得绕过核心语义和治理 | `INTERFACE_ONLY / EXTERNAL_BLOCKED`：注册/签名/治理合同已定义；无可信 signer/lifecycle/sandbox owner，不实现 production loader |

### 5.2 SOA 服务入口

| Req ID | 服务类 | 实现规则 | 当前状态 |
| --- | --- | --- | --- |
| FW-S-001 | Business Service | 场景编排必须生成可审计 plan/effect | formal Orchestration、typed Plan/Graph/Effect 与 debug audit composition 完成；production authority 外部阻塞 |
| FW-S-002 | Foundation Service | 账号、配置、时间、权限采用可替换 adapter | 外部阻塞 |
| FW-S-003 | Atomic Service | 最小 HVAC/Seat/Media/Navigation 能力 | debug/test adapter foundation 已完成；真实服务外部阻塞 |
| FW-S-004 | Service Contract | IDL/schema/version/error 必须冻结 | typed AIDL 基础完成 |
| FW-S-005 | Safety State | 强制 interlock，用户确认不能覆盖硬联锁 | 软件 fail-closed interlock 完成；可信 Safety/Vehicle authority 外部阻塞 |
| FW-S-006 | Extension Service | 必须注册、发现、授权、审计和撤销 | `INTERFACE_ONLY / EXTERNAL_BLOCKED`：合同和拒绝路径完成；可信 lifecycle/revoke owner 未提供 |

## 6. Native 层需求

### 6.1 功能与适配

| Req ID | 模块 | 实现规则 | 当前状态 |
| --- | --- | --- | --- |
| NV-F-001 | AIOS Kernel | Session/Task/Plan/Model/Tool/Memory/Safety 由 Runtime 拥有 | Android Runtime、durable Session/Graph、Orchestration 与 debug composition 软件完成 |
| NV-F-002 | Sensor/Actuator | 统一输入输出 adapter，不猜 vendor API | 外部阻塞 |
| NV-F-003 | Service Adapter | 语义 service 到目标 API 的唯一桥接 | empty/contract |
| NV-F-004 | Vehicle/Body Signal | BCM/HVAC/Seat/Door/Light 需真实目录和 readback | 外部阻塞 |
| NV-F-005 | ECU Proxy/Signal Adapter | 需 property/DBC/ARXML/area/error owner | 外部阻塞 |
| NV-F-006 | Data/Time Sync | 高频数据需时间域和 frame metadata | canonical timestamp/freshness/quality 语义完成；目标 time-domain/frame producer 外部阻塞 |
| NV-F-007 | Connected Funcware | TBOX/V2X/OTA/Diag 经 adapter 接入 | 外部阻塞 |
| NV-F-008 | SOA Runtime | 服务生命周期、超时、取消、健康状态 | 软件 lifecycle/deadline/cancel/recovery/readiness 完成；production service owner 外部阻塞 |
| NV-F-009 | Security/Policy Adapter | Safety/zone/ASIL-QM/default-deny | 软件 policy 完成，目标 owner 阻塞 |
| NV-F-010 | ADAS Funcware | 安全域闭环不由用户态 AIOS 接管 | 非本阶段 |
| NV-F-011 | Model Runtime Adapter | 抽象 CPU/GPU/NPU/Cloud，受 scheduler/governance 控制 | Provider contract + Vendor empty |
| NV-F-012 | Observability | trace/metric/audit 不得记录敏感原文 | digest-only snapshot/audit、隐私清单和发布证据接口完成；目标测量外部阻塞 |

### 6.2 Runtime & Governance

| Req ID | 能力 | 实现规则 | 当前状态 |
| --- | --- | --- | --- |
| NV-G-001 | Registry | 稳定 ID、版本、owner、health | Tool/Skill/Model/adapter registry 软件合同完成；production publisher/owner 外部阻塞 |
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
| NV-P-007 | 其他 | 必须登记 schema、identity、QoS 和治理 | `SUSPENDED / NO_CONFIRMED_BINDING`：未确认其他 binding，不猜测实现 |

## 7. Kernel/HAL、虚拟化与硬件需求

| Req ID | 要求 | 当前边界 |
| --- | --- | --- |
| KH-001 | 复用 Android 文件系统、网络和系统服务基础能力 | 不重造 OS |
| KH-002 | 共享内存和 NPU buffer 需明确 ownership/cache/IOMMU | 外部阻塞 |
| KH-003 | Drivers 只在明确缺口时新增 | DRV-GAP 文档化，未触发 |
| KH-004 | 其他底层扩展需单独审批 | `SUSPENDED / REQUIRES_SEPARATE_APPROVAL`；无批准不开发 |
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
| DEL-003 | 工程师文档、接口、状态机、命令 | README 详细需求/代码段追踪 + 当前文档集 |
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
| S2-HMI-007 | 语音优先极简 HMI 与末端反馈 | 仅保留场景触发和实时链路；HVAC/Seat 结果以显式 SIMULATED 动画反馈 |
| S2-HMI-008 | 实时链路中的模型输入/输出可视化 | 文字直接显示；图片等比缩略并与文字同显；允许的驾驶态点击居中放大、点击图外退出 |
| S2-HMI-009 | 乘员感知驱动的购物与路径规划 UX | 显示座位事实、购物候选、独立购物/购买/导航确认及真实事件驱动链路；禁止一次跳到最终结果 |
| S2-SES-001 | versioned durable Session | P1-W01/P1-W03 contract、P1-W05 facade/Service、P1-W06 Room v4/process-death recovery 已完成 |
| S2-CTX-001 | typed Context snapshot | typed source/freshness/trust 与 debug composition 软件完成；production source 外部阻塞 |
| S2-CTX-002 | 多座位多来源 Context 融合 | 四座位独立 occupancy、source/freshness/trust/conflict；unknown/conflict/stale 对区域 Effect fail closed |
| S2-TWN-001 | Vehicle Digital Twin | debug/test store 与显式 SIMULATED 投影完成；production 禁止 fallback |
| S2-PER-001 | 有证据的座舱可见事实合同 | 观察与意图分离；只允许有界座位/物体事实、区域、置信度和 evidence digest；禁止身份/年龄字段 |
| S2-INT-001 | 有证据的意图假设 | 饮水需求只能作为可过期、可拒绝、必须确认的候选假设；模型不能直接授权 Tool/Effect |
| S2-SCN-001 | versioned scenario catalog | catalog/resolver/compiler + P4-R2 formal debug Orchestration 完成；production publication 外部阻塞 |
| S2-GRF-001 | durable Agent Graph | typed executor/checkpoint/Room/recovery + P4-R1/P4-R2 composition 完成；production Effect authority 外部阻塞 |
| S2-SAF-001 | hard safety interlock | fail-closed policy/approval/driver restriction 完成；A user confirmation cannot override this hard interlock；可信 authority 外部阻塞 |
| S2-EFF-001 | typed Effect lifecycle | intent/observation/approval/undo、durable recovery 与 debug dispatch/readback 完成；production adapter 外部阻塞 |
| S2-ADP-001 | adapter registry | source/profile/capability/evidence 软件合同与 debug registry 完成；production adapter 外部阻塞 |
| S2-NAV-001 | 受治理导航 Tool/Adapter | POI 搜索、路线预览、导航启动分离；启动绑定独立确认；production 缺 adapter 时 fail closed |
| S2-COM-001 | 受治理商品与订单 Tool/Adapter | 商品搜索、订单预览、订单提交分离；购买提交绑定独立确认；支付保持外部空接口 |
| S2-TOL-001 | retry/timeout/partial failure | deterministic terminal/recovery 软件完成；production owner evidence 外部阻塞 |
| S2-MEM-001 | memory lifecycle | Working/Profile/Episodic、budget/consent 软件合同与 debug composition 完成；production repository/owner 外部阻塞 |
| S2-EVT-001 | proactive Event trigger | Event V2、broker/QoS、Trigger/consent/suggestion 软件完成；production middleware/Context owner 外部阻塞 |
| S2-TRG-002 | 主动建议呈现与抑制 | PARKED 显示完整卡片；MOVING/UNKNOWN 仅最小提示；支持 merge/replay、dismiss cooldown 和 never-ask；投影不授予 Effect authority |
| S2-MDL-001 | model routing | Model V2/router/evaluation/resource admission 与 debug decision composition 完成；Vendor NPU/provider 外部阻塞 |
| S2-MDL-002 | 座舱模型上下文与动作约束 | 驾驶员服务目标、座舱状态、UI 仿真边界和必要动作必须进入模型 prompt；输出需白名单后再进入 Plan |
| S2-ADP-002 | real vehicle adapter | owner/API/permission/readback/rollback |
| S2-OBS-001 | trace/metric/audit | no-raw-content 软件 audit/diagnostic/release evidence 接口完成；目标采集外部阻塞 |
| S2-OBS-002 | HMI 实时调用链投影 | Runtime/Intent/Context/Model/Plan/Policy/Graph/Safety/Effect/Readback 里程碑有界、顺序、滚动显示 |
| S2-REL-001 | release/rollback/compatibility | admission/rollback/retest 软件接口完成；量产 signer/installer/replacement evidence 外部阻塞，security campaign 挂起 |

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
cockpit_hvac_surface_implemented=true
cockpit_hvac_governed_manual_session=true
cockpit_hvac_reported_readback_available=false
cockpit_seat_surface_implemented=true
cockpit_seat_governed_manual_session=true
cockpit_seat_unknown_restricted_fail_closed=true
cockpit_seat_reported_readback_available=false
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
   transport 整体失效，调用方显式 reconnect 后恢复 active subscription。健康 reconnect/close 必须
   先向本代 Event Binder 逐项 `unregisterSessionCallback`，再 unlink/unbind；不能只清空本地映射，
   否则会泄漏服务端每会话 4 callback 配额。注册中的 Binder generation 发生变化时必须撤销刚注册的
   旧代 callback 并失败关闭。
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
8. Android 13/API 33 ARM64 已通过真实 Binder open/replay、连续 6 次健康 reconnect/resubscribe、
   cancel/close 与 process-death recovery 测试；未访问 Vehicle/VHAL/NPU/Driver/HAL。

当前状态：`sdk_facade_v2_available=true`、`session_runtime_service_published=true`、
`event_runtime_service_published=true`、`event_callback_service_published=true`、
`active_session_reconnect_resubscribe_verified=true`、
`healthy_reconnect_callback_cleanup_verified=true`；P1-W06 后为
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

状态：`scenario_manifest_schema_version=1`、`scenario_catalog_count=4`、
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

## 31. P2-W11 Simulated Media/Navigation adapters trace

本增量映射 `S2-ADP-001`、`DEL-001/003..005`：

1. `SimulatedMediaEffectAdapter` 与 `SimulatedNavigationEffectAdapter` 必须只存在于 Runtime `src/debug`，
   继承 P2-W08 base；main/release 和 production Runtime/Governance Service 不得包含、引用或注册。
2. Media destination 固定 `media.player`，capability 固定 `media.playback`，area 固定 cabin；version 1 canonical
   target 只允许 P2-W02 catalog 的 `PLAY/PAUSE/STOP`，不接受自由 action、未知 enum 或尾随字节。
3. Media completion 只能更新 source SIMULATED、non-production-trusted 的 immutable player state；不得调用
   Android MediaPlayer/MediaSession、vendor player、外部 package/Activity 或真实音频接口。
4. Navigation destination/action 固定 `navigation.poi`，area 固定 cabin；POI query 必须 NFKC canonical、
   control-free、1..128 chars，payload exact-length/canonical round-trip。
5. Navigation domain state/backend/observation 只能接收或保留 SHA-256 query digest，返回 synthetic POI/route
   ID、label key、100..100000 m、60..14400 s 观察，不得在 observation/log 中复制 raw query 或真实坐标。
   P2-W08 通用 debug Effect record 仍保留有界 canonical material，属于 `DEV-040` 明示的 process-memory 边界。
6. `MediaStateBackend` 与 `SyntheticNavigationBackend` 可替换但必须声明 simulation-only、production
   unauthorized、无 external Activity、无 network；Navigation 还必须无 location upload。任一 unsafe flag 构造失败。
7. NONE/DELAY 成功只调用 backend 一次；delay 到期前不发布 applied state/observation。TIMEOUT/retryable/
   terminal 不伪造结果；READBACK_MISMATCH 明确标记 synthetic mismatch；duplicate token 不增加 revision。
8. reset 必须清除 process-memory state/digest/observation 并 reset backend；不得接 shared Runtime/Room/
   Plan/Graph/Effect Service，不得访问 Vehicle/VHAL/NPU/Driver-HAL 或恢复 Python fallback。
9. JVM、debug/release compile 和 Android 13/API 33 ARM64 probe 必须覆盖 target round-trip、media state、
   synthetic deterministic observation、digest-only privacy、delay/fault/mismatch/idempotency 和 backend boundary。

状态：`simulated_media_adapter_defined=true`、`simulated_navigation_adapter_defined=true`、
`simulated_media_nav_typed_target_verified=true`、`simulated_media_state_verified=true`、
`simulated_navigation_synthetic_observation_verified=true`、`simulated_navigation_query_digest_only=true`、
`simulated_media_nav_replaceable_backend_verified=true`、`simulated_media_nav_android13_arm64_verified=true`、
`simulated_media_nav_debug_only=true`、`simulated_media_nav_release_source_absent=true`、
`simulated_media_nav_production_registered=false`、`simulated_media_nav_runtime_wired=false`、
`external_activity_started=false`、`location_uploaded=false`、`network_accessed=false`、
`effect_dispatch_enabled=false`、`hardware_accessed=false`。

## 32. P2-W12 Debug Simulation Controller trace

派生需求：`S2-CTX-001`、`S2-ADP-001`、`DEL-001/003/004/005`。

1. `IDebugSimulationController` 与实现只能位于 Runtime `src/debug`；release/main 不得含同名 AIDL、Java
   Service、permission、Activity 或 production policy grant。
2. exported debug Service 必须由 debug-only `signature` permission 保护；每个 Binder 方法还必须从 trusted
   calling UID/package/current signer 执行 `debug.simulation.control` default-deny capability 检查。
3. V1 只接受 PARKED/MOVING/UNKNOWN、P2-W01 canonical path/area/scalar union、四个固定 simulated adapter
   ID、P2-W08 六类 fault 和 1..24h clock advance；未知、类型漂移、非 canonical unused union 字段失败关闭。
4. `setAdapterFault` 必须更新 P2-W09..W11 四个实际 debug adapter，不得发现、注册或调用 production
   adapter；controller 与 adapter 必须固定 simulation-only/production unauthorized。
5. accepted/rejected command 必须写 debug audit。进程内 audit 最多 128 条，仅保存 command/outcome/target
   digest/revision/elapsed，不保存原始 signal text；snapshot 对 Binder 只暴露 revision/count/elapsed/digest。
6. reset 必须清理 driving/signal/fault/adapter process state 并复位 manual clock，但保留 bounded audit；不得
   写 Room、Session/Event、shared Context/Twin、Graph/Effect Runtime。
7. Android 13/API 33 ARM64 必须验证 shell signature denial、同签名 capability allowlisted 真实 Binder、
   state/signal/fault/clock/reset、protocol version/hash；release build 必须证明 production exported=false。
8. 不得访问 Vehicle/VHAL、Android Car、vendor Binder、network、location、NPU、device node、Driver/HAL 或
   恢复 Python/Linux fallback。test evidence 不得提升 `target_hardware_validated`/`production_ready`。

状态：`debug_simulation_controller_defined=true`、`debug_simulation_controller_aidl_version=1`、
`debug_simulation_controller_signature_permission_enforced=true`、
`debug_simulation_controller_capability_enforced=true`、
`debug_simulation_controller_state_signal_fault_clock_reset_verified=true`、
`debug_simulation_controller_audit_bounded_verified=true`、
`debug_simulation_controller_android13_arm64_verified=true`、
`debug_simulation_controller_debug_only=true`、`debug_simulation_controller_release_source_absent=true`、
`debug_simulation_controller_production_exported=false`、`debug_simulation_controller_runtime_wired=false`、
`vehicle_signal_provider_wired=false`、`hardware_accessed=false`。

## 33. P3-W01 Agent Graph Runtime trace

派生需求：`S2-GRF-001`、`NV-G-004/006/007`、`DEL-001/003/004/005`。

1. `AgentGraphRuntime` 必须先调用 `PlanGraphValidator.validateTransport` 并深拷贝 P1-W02 `ScenarioPlan`；
   admission 后调用方修改原 DTO 不得改变 run/node/plan digest 投影。runId 固定为 canonical planId。
2. Graph 状态固定为 CREATED/PLANNING/WAITING/EXECUTING/PARTIAL/COMPENSATING/COMPLETED/FAILED/CANCELLED/
   STUCK；Node 状态固定为 PENDING/READY/EXECUTING/WAITING/SUCCEEDED/FAILED/SKIPPED/CANCELLED/
   COMPENSATING/COMPENSATED/STUCK。只有枚举表列出的 transition 合法，终态不可复活。
3. `PARTIAL` 在 P3-W01 是终态，只能表示 optional node skip/failure 且 required 路径完成；required node
   failure、required dependency impossible 和 plan deadline 必须分别进入 FAILED 或 STUCK，禁止猜测成功。
4. 同一 session 同时最多一个 PLANNING/WAITING/EXECUTING/COMPENSATING run，后续 run 保持 CREATED 并按
   admission FIFO 激活；不同 session active run 数量可配置但最大 8。状态机方法同步串行，不创建内部线程池。
5. run record 最大 64；单 run event projection 最大 256。event 只含 sequence/elapsed/node ID/枚举状态/
   reason code/链式 SHA-256，不含 node input、模型文本、车辆 payload、任意错误原文或 executor output。
6. `NodeExecutorRegistry` 本包只注册 PlanContract allowlisted node type，固定
   `dispatchEnabled=false`、`productionAuthorized=false`。claim/suspend/resume/complete 仅推进状态，P3-W02
   之前不得调用 typed executor。
7. deadline 同时使用注入 epoch/elapsed clock：epoch 决定 plan 过期，elapsed 仅进入事件投影。过期 run
   进入 FAILED，所有未终结 node 进入 CANCELLED；不得立即 retry 或 dispatch。
8. JVM、debug/release compile 和 Android 13/API 33 ARM64 probe 必须覆盖合法/非法 transition、immutable
   Plan、FIFO/并发上限、partial、required failure、waiting/resume、deadline、capacity/event bound。
9. 本包不接 `CentralBrainRuntimeService`、Binder、Room、P2 debug controller/adapter、Effect/Model/Tool/Memory
   executor，不访问 Vehicle/VHAL/NPU/Driver-HAL，不恢复 Python/Linux fallback。

状态：`agent_graph_runtime_defined=true`、`agent_graph_state_machine_verified=true`、
`agent_graph_same_session_fifo_verified=true`、`agent_graph_cross_session_bounded_verified=true`、
`agent_graph_partial_terminal_verified=true`、`agent_graph_deadline_verified=true`、
`agent_graph_compensation_fail_closed_verified=true`、
`agent_graph_event_projection_bounded=true`、`agent_graph_android13_arm64_verified=true`、
`agent_graph_executor_dispatch_enabled=false`、`agent_graph_runtime_persistence_wired=false`、
`agent_graph_runtime_binder_published=false`、`agent_graph_runtime_production_wired=false`、
`effect_dispatch_enabled=false`、`model_invoked=false`、`hardware_accessed=false`。

## 34. P3-W02 Typed Node Executor trace

派生需求：`S2-GRF-001`、`S2-SAF-001`、`S2-EFF-001`、`DEL-001/003/004/005`。

1. `NodeExecutionSchemas` 必须对 P1-W02 `PlanContract.allowedNodeTypes()` 的 11 类 node type 提供完整、
   无重复的 input/output schema；缺项、未知 node type、schema ID 不一致或 exact Java class 不一致必须失败关闭。
2. `NodeExecutionInput` 只允许固定 immutable 类型：Context、Policy、Approval、Effect、Verification、Summary、
   Compensation 和用于尚未实现类型的 DigestOnly。公共 identity 只含 canonical UUID、node ID、Plan/input digest、
   attempt 和 deadline；禁止任意 map、JSON、Bundle、Parcel blob、serialized class 或 raw model/vehicle payload。
3. `NodeExecutionOutput` 与 `NodeExecutionResult` 只返回固定 enum、message key、bounded count 和 SHA-256。
   reason 只能使用 `ReasonCode`，不得返回 exception 原文、模型文本、车辆数据或未审查 adapter output。
4. `TypedNodeExecutor<I,O>` 必须声明 exact input/output class；`NodeExecutorRegistry.validateExecutor` 必须拒绝
   production authorization、Effect/model dispatch、network、hardware 和 raw persistence 请求。registry 不保存或
   调用 executor，`dispatchEnabled=false` 保持不变。
5. debug/test 只实现 Context、Policy、ApprovalInterrupt、Effect、Verification、Summary、Compensation 七类。
   Context/Verification 输出固定 `productionTrusted=false`；Policy/Approval 缺可信 authority 必须 REJECTED；
   Effect 必须 WAITING/NOT_DISPATCHED；Compensation 必须 REJECTED/NOT_DISPATCHED。
6. Model/Tool/Memory Query/Memory Write 只有 digest-only fixed schema，P3-W02 不提供 executor。任何尝试通过
   deterministic harness 调用这些类型必须以稳定 `CB_NODE_EXECUTOR:` 前缀拒绝，不得回退模型、网络或 Python。
7. deterministic harness 必须仅位于 `src/debug`，使用显式 switch 和 exact cast；main/release 不得包含该实现或
   probe。`AgentGraphRuntime`、Runtime/Governance Service、Binder、Room、P2 adapter/controller 均不得引用它。
8. JVM、debug/release compile 和 Android 13/API 33 ARM64 probe 必须覆盖 11 schema/7 executor、exact-class、
   Context、Policy/Approval、Effect/Compensation fail-closed、Verification、Summary 和 unsupported fail-closed。
9. 本包不接真实 Context/Safety/approval authority，不 dispatch Effect，不调用模型、网络、Vehicle/VHAL/NPU/
   Driver-HAL，不提升 `production_ready` 或 `target_hardware_validated`。

状态：`typed_node_executor_contract_defined=true`、`typed_node_executor_schema_count=11`、
`typed_node_executor_debug_count=7`、`typed_node_executor_exact_class_verified=true`、
`typed_node_executor_effect_fail_closed_verified=true`、
`typed_node_executor_unsupported_fail_closed_verified=true`、
`typed_node_executor_android13_arm64_verified=true`、
`typed_node_executor_graph_dispatch_enabled=false`、`typed_node_executor_production_wired=false`、
`effect_dispatch_enabled=false`、`model_invoked=false`、`network_accessed=false`、`hardware_accessed=false`。

## 35. P3-W03 CheckpointSerializer trace

派生需求：`S2-GRF-001`、`NV-G-003/006/007`、`DEL-001/003/004/005`。

1. `CheckpointSerializer` 只接受构造时冻结的 `type + schemaVersion + exact payload class + PayloadCodec`；
   未注册 type、未支持 version、错误 payload class 或 codec 返回错误 class 必须失败关闭，不允许运行时类名加载。
2. `CheckpointValue` 只允许 String、boolean、绝对值不超过 10^12 的 integer/decimal、最多 6 位 decimal scale、
   enum stable name、最多 64 项的 list/map。string 最大 1024 字符，map key 最大 64 字符并拒绝 class/type metadata key。
3. `CheckpointEnvelope` 固定字段及顺序为 `schemaVersion/type/nodeId/planDigest/contextDigest/payload/digest/createdAt`；
   node ID、两个 source digest、checkpoint digest 和正 createdAt 必须验证。epoch long 使用 canonical decimal
   string，以免超过 primitive integer bound；payload map key 排序、decimal 归一化。
4. `JsonPrimitiveCheckpointSerializer` 必须使用 strict streaming parser；JSON 总长最大 64 KiB，payload 最大 8 层、
   总 value token 最大 1024。duplicate/unknown/missing/null/trailing/malformed/oversize/depth/token 必须有稳定错误码。
5. digest 必须使用 `central-brain.checkpoint.v1` domain-separated SHA-256，覆盖除 digest 字段外的完整 canonical
   envelope。decode 必须先验 digest，再要求输入 byte-for-byte canonical；篡改或非 canonical 都不得恢复。
6. 禁止 `ObjectInputStream`/`ObjectOutputStream`/`Serializable` 恢复、Gson object mapping、`Class.forName`、
   reflection、Bundle/Parcel/Binder object、file path/native pointer、网络或任意硬件 material。
7. JVM 和 Android 13/API 33 ARM64 probe 必须覆盖 registered DTO round-trip、determinism、type/version/class、
   malformed/duplicate/unknown/trailing、oversize/depth/token、digest/non-canonical 和 serialization/reflection corpus。
8. P3-W03 不接 `AgentGraphRuntime`、Room v4、Binder/Session Service 或 process-death recovery。恢复 mismatch 映射
   STUCK 属于 P3-W09；本包不 dispatch executor/Effect，不调用模型、Vehicle/VHAL/NPU/Driver-HAL。

状态：`checkpoint_serializer_defined=true`、`checkpoint_serializer_registered_dto_verified=true`、
`checkpoint_serializer_canonical_digest_verified=true`、
`checkpoint_serializer_malformed_unknown_rejected=true`、
`checkpoint_serializer_size_depth_limit_verified=true`、
`checkpoint_serializer_security_corpus_verified=true`、
`checkpoint_serializer_android13_arm64_verified=true`、
`checkpoint_serializer_java_serialization_enabled=false`、
`agent_graph_runtime_persistence_wired=false`、`agent_graph_executor_dispatch_enabled=false`、
`effect_dispatch_enabled=false`、`model_invoked=false`、`network_accessed=false`、`hardware_accessed=false`。

## 36. P3-W04 Retry/Timeout policy trace

派生需求：`S2-GRF-001`、`NV-G-004`、`DEL-001/003/004/005`。

1. `NodeTimeoutPolicy` 必须从已通过 `PlanContract.validateNode` 的 `timeoutMs/maxAttempts` 冻结配置；attempt
   number 只允许 1..maxAttempts，effective deadline 取 `min(start + node timeout, plan deadline)`，到点即过期。
2. 时间输入必须是 caller 提供的 monotonic elapsed time。策略不得读取 wall clock/nano clock，不创建 thread、
   timer、executor 或 sleep；加法溢出必须饱和，不得使 deadline 回绕后重新获得执行资格。
3. `BackoffCalculator` 的 base/max/jitter 必须有界；max delay 不超过 `PlanContract.MAX_NODE_TIMEOUT_MS`，jitter
   不超过 250 permille。attempt 2..3 的 jitter 由 node ID、retry seed digest、attempt 经 domain-separated
   SHA-256 确定，禁止 runtime random 导致不可重放。
4. `NodeRetryPolicy` 必须冻结 node type、maxAttempts 和 idempotency-key digest。terminal/cancel 不重试；retryable/
   timeout 在 attempt budget 耗尽或 backoff 完成时间不早于 plan deadline 时失败关闭。
5. `effect.execute` 与 `compensate` 必须带 typed idempotency key。`DELIVERY_UNKNOWN`、timeout 或 retryable failure
   在 retry 前必须有 `CONFIRMED_NOT_APPLIED`；未知结果只返回 `RECONCILE`，已应用只返回
   `STOP_EFFECT_ALREADY_APPLIED`，不得盲目重放。
6. Retry decision 只含 action、attempt、delay、eligible elapsed time 与 domain-separated SHA-256；不得返回 raw
   idempotency key、Effect payload、异常原文、模型或车辆数据。
7. JVM 和 Android 13/API 33 ARM64 probe 必须覆盖 deterministic jitter、deadline clamp/exact expiry、attempt
   budget、terminal/cancel、Effect reconcile、deadline/overflow 和 malformed input。
8. P3-W04 不接 `AgentGraphRuntime`、Room、Binder、production Effect/model/vehicle/NPU/Driver-HAL；不得提升
   `production_ready` 或 `target_hardware_validated`。

状态：`node_retry_policy_defined=true`、`node_timeout_policy_defined=true`、
`backoff_deterministic_bounded_verified=true`、`timeout_deadline_clamp_verified=true`、
`retry_attempt_budget_verified=true`、`effect_idempotency_reconcile_gate_verified=true`、
`retry_deadline_fail_closed_verified=true`、`retry_timeout_policy_android13_arm64_verified=true`、
`retry_timeout_policy_runtime_wired=false`、`agent_graph_executor_dispatch_enabled=false`、
`effect_dispatch_enabled=false`、`model_invoked=false`、`network_accessed=false`、`hardware_accessed=false`。

## 37. P3-W05 Durable approval interrupt trace

派生需求：`S2-SAF-001`、`S2-UX-003`、`S2-GRF-001`、`NV-G-005/006/007`、
`DEL-001/003/004/005`。

1. Approval interrupt 必须使用 canonical UUID 标识 approval/session/plan，并绑定 owner fingerprint、node ID、
   action/plan/context/policy/Safety SHA-256；记录不得包含原始用户输入、车辆值、模型文本、token/memory、
   Binder/Parcel/native handle 或授权凭据。
2. 创建 pending interrupt 时，时间由 caller 提供；TTL 必须在 1..300000 ms，expiry 取
   `min(createdAt + ttl, planDeadline)`，plan 已过期、加法回绕或空窗口必须失败关闭。合同不得自行读 clock、
   创建 thread/timer/executor 或生成随机 ID。
3. 状态只允许 PENDING -> APPROVED/REJECTED/CANCELLED/EXPIRED。交互决策必须来自 trusted authority 且发生在
   `[createdAt, expiry)`；EXPIRED 必须发生在 expiry 或之后。terminal replay、untrusted authority 和非法状态转换
   必须拒绝。
4. `graph.approval.interrupt` schema v1 必须经 allowlisted `CheckpointSerializer.Registration` 编解码；payload 与
   envelope 的 epoch long 必须使用 canonical decimal string，避免当前 epoch 超出 bounded primitive integer。
   恢复后必须重算 record digest 并比对，未知/错类型/非 canonical 输入失败关闭。
5. Resume 只接受 trusted APPROVED 且未过 expiry/plan deadline 的记录；必须重新比对 owner/session/plan/node/
   action/plan digest，要求 context fresh 且 digest 未变、policy 当前授权且 digest 未变、capability 当前允许。
6. Resume 必须读取 caller 提供的当前 Safety State；authority 不可信、UNSAFE/UNKNOWN 或 Safety digest 改变均
   不得恢复。结果只暴露 allowed、reason 与 domain-separated SHA-256，不返回 raw context/policy/Safety material。
7. JVM 与 Android 13/API 33 ARM64 probe 必须覆盖 binding/expiry clamp、canonical checkpoint、trusted decision、
   owner/plan/action mismatch、context/policy/capability、Safety change/unsafe/untrusted、expiry/replay/malformed input。
8. P3-W05 不修改 Room v4，不接 `AgentGraphRuntime`、Binder/grant Service、production Effect/model/vehicle/NPU/
   Driver-HAL。Room transaction/restart recovery 保留给 P3-W09；不得提升 production/target hardware 状态。

状态：`approval_interrupt_record_defined=true`、`approval_interrupt_binding_verified=true`、
`approval_interrupt_checkpoint_roundtrip_verified=true`、`approval_interrupt_trusted_decision_verified=true`、
`approval_resume_owner_plan_context_policy_verified=true`、`approval_resume_safety_revalidation_verified=true`、
`approval_resume_expiry_verified=true`、`approval_interrupt_android13_arm64_verified=true`、
`approval_interrupt_persistence_wired=false`、`approval_grant_service_published=false`、
`agent_graph_executor_dispatch_enabled=false`、`effect_dispatch_enabled=false`、`model_invoked=false`、
`network_accessed=false`、`hardware_accessed=false`。

## 38. P3-W06 EffectCoordinator trace

派生需求：`S2-EFF-001`、`S2-SAF-001`、`NV-G-005/006/007`、`DEL-001/003/004/005`。

1. `EffectBatch` 必须包含 1..16 个 P1 typed `EffectIntent`，创建时 deep-copy 并重新校验；全部 Effect 必须绑定
   同一 session/plan/action/plan digest，effect ID 和 idempotency key 必须唯一。resource/dependency 有界并进入
   domain-separated batch digest；required Effect 不得依赖 optional Effect。
2. `EffectDependencyPlanner` 必须拒绝环和 batch 外依赖。依赖项只能出现在更早 wave；同一 resource key 的两个
   Effect 不得出现在同一 wave。计划必须确定、不可变并有独立 digest，合同本身不创建 thread/executor。
3. `AdapterRegistry` 必须按 capability+target area+profile 精确解析。DEBUG 只允许 simulation-only registration；
   PRODUCTION 只允许 activated、non-simulation、explicitly authorized registration。缺项必须返回
   `CB_ERR_ADAPTER_UNAVAILABLE`，禁止从 production fallback 到 debug adapter。
4. Coordinator 必须在任何 apply 前调用批次内全部 preparation adapter。任一 required preparation 失败必须
   整批 zero-dispatch；optional preparation 失败可形成 PARTIAL。Prepared material 必须绑定 action、adapter
   destination、payload/envelope digest、before-state digest 和 evidence digest，并 defensive-copy transient bytes。
5. Dispatch 必须遵循 dependency wave；dependency 未 DELIVERED 时不得调用子 Effect adapter。既有
   `EffectAdapterContract` 的 token-dedup、original-result、linearizable-status 门禁必须继续生效；本包不自动 retry。
6. 每个 batch item 必须形成一个独立 `EffectObservation`。Adapter APPLIED 只映射为 DELIVERED；UNKNOWN、retryable、
   terminal 和 prepare rejection 保持不同状态。Result 只暴露 ID、resource、required、outcome、before-state digest
   和 defensive observation，不返回 payload/envelope/raw vehicle/model/user data。
7. JVM 与 Android 13/API 33 ARM64 probe 必须覆盖 immutable batch/digest、dependency/cycle、resource serialization、
   required zero-dispatch、optional degrade、profile isolation、mixed outcome、unknown dependency block 与 defensive copy。
8. P3-W06 不接 `AgentGraphRuntime`、Room/outbox、Binder/Service、P2 debug adapter、production vehicle/NPU/Driver-HAL。
   Verification/reconciliation 保留 P3-W07，durable outbox/restart 保留 P3-W09；不得提升 production/target hardware。

状态：`effect_batch_defined=true`、`effect_dependency_plan_verified=true`、
`effect_resource_conflict_serialized=true`、`effect_adapter_registry_profile_isolation_verified=true`、
`effect_prepare_all_required_verified=true`、`effect_optional_degradation_verified=true`、
`effect_independent_observation_verified=true`、`effect_coordinator_android13_arm64_verified=true`、
`effect_coordinator_graph_wired=false`、`effect_coordinator_persistence_wired=false`、
`production_effect_adapter_registered=false`、`production_effect_dispatch_enabled=false`、
`effect_verification_reconciliation_wired=false`、`model_invoked=false`、`network_accessed=false`、
`hardware_accessed=false`。

## 39. P3-W07 Effect verification/reconciliation trace

派生需求：`S2-EFF-001`、`S2-TWN-001`、`NV-G-005/006/007`、`DEL-001/003/004/005`。

1. `EffectVerifier` 必须只接受 P1 typed `EffectIntent`、前一条合法 `EffectObservation` 与 bounded typed evidence；
   intent/observation 的 effect/session/action/plan/context/target binding、capability catalog、area、risk、unit、range、
   profile/source 和 evidence time 不一致必须失败关闭。
2. `targetValueDigest` 必须由 domain-separated capability/area/policy/tolerance/typed target 生成；COMPOSITE 还必须绑定
   2..8 个唯一 canonical signal path+area+expected value+tolerance。任意 caller-supplied match boolean、map、JSON、
   Bundle、raw vehicle/model/user payload 均禁止。
3. CALLBACK_ONLY 只允许无 catalog readback 的 LOW-risk capability；HVAC 与 Seat 不得使用。REPORTED_EQUALS 必须
   exact typed match；REPORTED_TOLERANCE 只允许 finite numeric tolerance；STATE_TRANSITION 必须 before != target 且
   reported == target；COMPOSITE 必须全部 typed field 一致。
4. DELIVERED/UNKNOWN 到成功结果必须先生成 APPLIED observation，再生成独立 VERIFIED observation；APPLIED 与
   VERIFIED 都必须含 reported digest。Mismatch 只停在 APPLIED 并继续 reconcile；缺失/不可信 evidence 进入 UNKNOWN。
   deadline 或 terminal adapter status 进入 FAILED_TERMINAL，不得向 HMI 宣称 completed。
5. `DigitalTwinEffectReconciler` 只允许调用通过既有 `EffectAdapterContract` 的 linearizable `queryStatus`，源码不得调用
   `apply`。APPLIED status 只与同一 immutable `DigitalTwinSnapshot` 的 VALID typed report 对账；NOT_APPLIED 仅返回
   `CONFIRMED_NOT_APPLIED` 给 retry policy，UNKNOWN/不可用返回 bounded next reconcile time。
6. 已 VERIFIED observation 必须在 adapter resolve/query 前返回 ALREADY_VERIFIED，且永不 redispatch。adapter status 从
   APPLIED 回退 NOT_APPLIED 必须以 `ADAPTER_STATUS_REGRESSION` 失败关闭。
7. Reconciler 不拥有 clock、thread、timer、executor 或 persistence；caller 提供 epoch 和 1..64 sequence，退避从
   250 ms 指数增长并上限 30 s，下一时间不得晚于 Effect deadline。deadline/attempt 耗尽必须 terminal。
8. Process-local Twin 不是 production authority；PRODUCTION profile 必须在 query 前返回
   `PRODUCTION_READBACK_UNAVAILABLE`。P3-W07 不接 Coordinator/Graph/Room/outbox/Binder/Service、P2 debug registry、
   production vehicle/NPU/Driver-HAL，不提升 production/target hardware 状态。
9. JVM 与 Android 13/API 33 ARM64 probe 必须覆盖五种 policy、DELIVERED/APPLIED/VERIFIED 分层、mismatch、deadline/
   trust、UNKNOWN timed reconcile、matched Twin、VERIFIED no-query dedup、NOT_APPLIED/status regression 和 production
   fail-closed；release manifest 不得含 debug probe。

状态：`effect_verifier_defined=true`、`effect_verification_policies_verified=true`、
`effect_state_separation_verified=true`、`effect_unknown_reconciliation_verified=true`、
`effect_verified_redispatch_blocked=true`、`effect_production_readback_fail_closed=true`、
`effect_verification_android13_arm64_verified=true`、
`effect_verification_reconciliation_runtime_wired=false`、`effect_verification_scheduler_wired=false`、
`effect_verification_persistence_wired=false`、`effect_verification_production_readback_wired=false`、
`effect_verification_graph_wired=false`、`production_effect_dispatch_enabled=false`、`model_invoked=false`、
`network_accessed=false`、`hardware_accessed=false`。

## 40. P3-W08 Compensation/Undo trace

派生需求：`S2-EFF-001`、`S2-UX-003`、`S2-SAF-001`、`NV-G-005/006/007`、
`DEL-001/003/004/005`。

1. `CompensationPlanner` 必须消费一个完整 P3-W06 `EffectBatch` 和每个 Effect 的 terminal state；state
   缺失/重复、batch 外 Effect、非 terminal source、verified dependency 未 verified 均失败关闭。仅 VERIFIED
   source 可以生成 step。
2. `EffectIntent.reversible=true` 不是充分授权。Planner 构造时必须冻结显式 capability+catalog-area reversible
   allowlist；任一 verified irreversible、未列入 policy 或无 catalog readback 的 Effect 使 full undo 不可用，
   不得撤销部分动作后宣称整体成功。
3. 每个 reversible step 必须携带同 capability/path/area/unit/range 的 VALID typed before signal、source Context
   binding、capture time 和 SHA-256；before digest 必须等于 P3-W06 prepared material 的 before-state digest，source
   compensation descriptor 必须由 capability/area/type/unit/risk/verification domain-separated 生成。
4. Compensation target 必须是 before snapshot 的绝对 typed scalar，禁止相对加减或 caller-supplied match boolean。
   新 Effect 必须使用新的 session/plan/action/effect identity、source-bound idempotency key、当前非回退 Context，
   并固定 `reversible=false`，防止无限 Undo 链。
5. Compensation wave 必须反转原 dependency plan；原 B depends-on A 时补偿 B 先于 A。同 wave 仍不得包含同
   resource。Plan/step 必须 immutable、defensive-copy、digest-bound，不拥有 thread/timer/scheduler。
6. P1 V1 的原始 VERIFIED observation 是不可变 terminal。Undo 不得把它直接转换为 COMPENSATING；必须创建新的
   governed compensation task。冻结 V1 中 unreachable compensation transition 的后续协议演进由 `DEV-049` 跟踪。
7. `UndoService` 在本包是 pure Java process-local admission，不是 Android/Binder Service。它必须为每个 step
   签发 digest-bound P1 `UndoHandle`，TTL 不超过 15 分钟且不晚于 compensation deadline；tamper、过期、非
   AVAILABLE、Context version 回退均拒绝。
8. Request 必须重新检查 trusted authority、fresh exact Context、current policy authorization、capability allowlist
   和 trusted SAFE state；成功只创建新的 immutable governed task 与 REQUESTED handle copies，不 dispatch Effect。
   owner+idempotency process record 上限 64，同 material replay 返回首次 task，不同 material 冲突失败关闭。
9. PRODUCTION profile 固定 `PRODUCTION_COMPENSATION_UNAVAILABLE`，不得使用 debug authority/before snapshot；
   P3-W08 不接 Graph/Room/Binder/adapter/vehicle/NPU/Driver-HAL。JVM 与 Android 13/API 33 ARM64 probe 必须覆盖
   absolute before、reverse order、irreversible reject、TTL/digest、Governance/Safety、new task、replay 和
   production fail-closed；release manifest 不得含 probe。

状态：`compensation_planner_defined=true`、`compensation_absolute_before_verified=true`、
`compensation_reverse_dependency_verified=true`、`compensation_irreversible_rejected=true`、
`undo_ttl_governance_verified=true`、`undo_new_governed_task_verified=true`、
`undo_idempotent_replay_verified=true`、`undo_production_fail_closed=true`、
`compensation_undo_android13_arm64_verified=true`、`compensation_undo_runtime_wired=false`、
`compensation_undo_persistence_wired=false`、`undo_binder_service_published=false`、
`compensation_dispatch_enabled=false`、`production_compensation_authority_wired=false`、
`effect_dispatch_enabled=false`、`network_accessed=false`、`hardware_accessed=false`。

## 41. P3-W09 Restart recovery trace

派生需求：`S2-SES-001`、`S2-GRF-001`、`S2-EFF-001`、`S2-SAF-001`、
`NV-G-005/006/007`、`DEL-001/003/004/005`。

1. `GraphRestartReconciler` 必须只消费 immutable `PersistentRun`、`Evidence` 和 caller-supplied epoch；不得自行
   打开 Room、读取时钟、创建线程、调用 Binder/executor/adapter/model/tool、访问车辆或硬件。
2. 非终态 Graph 超过 deadline 必须进入 FAILED，所有非终态 Node 进入 STUCK。终态 Graph 必须保持原状态，
   不生成 continuation 或 side effect。
3. WAITING/EXECUTING/COMPENSATING Node 必须具有 checkpoint ref，且 evidence 必须为 VALID。缺失、摘要不匹配或
   未知信任必须使整个非终态 Graph 进入 STUCK；不得丢弃单个错误 Node 后继续其余流程。
4. Effect、Approval、Compensation、Model、Tool 和 Memory-write Node 恢复后只允许 WAITING，并产生 typed
   reconcile/revalidation directive。未知 Effect delivery 只允许 `RECONCILE_EFFECT_STATUS`；确认 applied 只进入
   readback verification；确认 not-applied 也必须先过 retry policy，禁止恢复时直接 redispatch。
5. 只有 checkpoint VALID、当前 Governance 已重验、无任何 pending directive 的 control Node 可以回 READY。
   `continuationAllowed=true` 只表示 reducer 允许 caller 继续，不表示 executor 已启用；
   `executorDispatchEnabled` 和 `productionAuthorized` 固定 false。
6. `DurableGraphRecoveryRepository` 必须复用 Room v4 的 Plan/PlanNode/EffectObservation/Compensation 表，不得为了本包
   修改 schema/hash。加载最多 64 Node、64 latest Effect、64 Compensation，并限制 Effect history query 为 1024 行。
7. `applyRecovery` 必须在单个 Room transaction 内复验 plan/session/digest、完整 Node identity 和覆盖集合，只更新
   Plan/Node state。Effect observation 与 Compensation evidence 必须不可变；result digest 只写审计，不保存 raw
   checkpoint、vehicle/user/model material。
8. 同 result digest 重放必须是幂等的：changed row 为 0、`GRAPH_RESTART_RECONCILED` 审计总数不增长、side-effect
   count 为 0。不同合法恢复结果允许新增审计，但不得覆盖历史证据。
9. Android 13/API 33 ARM64 probe 必须执行 seed、`force-stop`、首次恢复、再次 `force-stop`、相同 material 重放，
   并证明两次进程代次变化、Room reopen、exactly-once audit 和 release manifest 无 debug Activity。
10. 本包不得注入 `CentralBrainRuntimeService`、`AgentGraphRuntime` 或 Binder；
    `graph_restart_runtime_wired=false`、`agent_graph_runtime_persistence_wired=false`、
    `production_effect_dispatch_enabled=false`。该有意边界由 `DEV-050` 跟踪，不得提升 production/hardware 状态。

状态：`graph_restart_reconciler_defined=true`、`graph_restart_room_v4_repository_verified=true`、
`graph_restart_waiting_recovered=true`、`graph_restart_executing_reconciled=true`、
`graph_restart_unknown_effect_reconciled=true`、`graph_restart_approval_undo_revalidation_verified=true`、
`graph_restart_checkpoint_mismatch_stuck=true`、`graph_restart_continue_after_revalidate_verified=true`、
`graph_restart_process_death_verified=true`、`graph_restart_idempotent_reopen_verified=true`、
`graph_restart_audit_exactly_once_verified=true`、`graph_restart_side_effect_count=0`、
`graph_restart_historical_digest_replay_verified=true`、
`graph_restart_android13_arm64_verified=true`、`graph_restart_repository_implementation_available=true`、
`graph_restart_runtime_wired=false`、`graph_restart_binder_published=false`、
`graph_restart_executor_dispatch_enabled=false`、`graph_restart_effect_dispatch_enabled=false`、
`graph_restart_production_wired=false`、`agent_graph_runtime_persistence_wired=false`、
`production_effect_dispatch_enabled=false`、`hardware_accessed=false`。

## 42. P4-W01 Client2 Session/Event bridge trace

Req IDs：`S2-UX-001`、`S2-HMI-005`、`XSC-001`、`XSC-005/006`、`NV-G-003/006/007`、
`DEL-001/003/004/005`。

1. Client2 bridge 的主调用必须是 `openSession(...)`，返回 caller-owned `SessionConnection`；新 HMI 不得调用
   legacy `CentralBrainClient.submitAgentTask` 或从一次性 reply 推断会话状态。
2. `ScenarioCallback` 必须暴露 typed `SessionHandle`、`SessionSnapshot`、`RuntimeEvent`、replay complete、overflow、
   close 和 stable error code。snapshot/cursor replay 是权威恢复源，callback notification 不能单独作为完整历史。
3. `SessionConnection` 必须支持 `isConnected/getSessionHandle/cancel/close`；`SessionClient` 继续拥有 Session/Event
   双 Binder version/hash negotiation、death detection、reconnect、resubscribe、sequence continuity 和 duplicate drop。
4. 旧 Smali `submit(Activity,String,String,ScenarioCallback):boolean` 与三个 `onBridge*` 描述符必须保留，但只能
   作为兼容投影。每个新兼容请求必须关闭前一兼容 stream；不得恢复 HTTP/REST 或直接调用 debug adapter。
5. 两段 UI alias 必须经过 12 项 exact allowlist 转为 qualified canonical Session ID。未知 alias 必须在 bind 前
   失败；禁止放宽 `SessionContract` pattern、修改冻结 AIDL/hash 或让 UI alias 直接进入 Runtime。
6. 用户文本最长 1024 字符；request 使用 canonical UUID、HMI_BUTTON、DRIVER、`zh-CN` 和 10 秒 admission deadline。
   日志不得记录 user/model text、session UUID、车辆 payload 或原始设备身份，只记录 presence、state/type/sequence。
7. Android 13/API 33 ARM64 验收必须覆盖 open、snapshot、sequence 1 `ScenarioRequested`、replay complete、旧摘要
   投影、stream replacement、Runtime process-death reconnect/replay、duplicate suppression、Client2 restart/menu reopen。
8. 本包不得声明场景已执行。`scenario_execution_enabled=false`、`service_dispatch_triggered=false`、
   `cockpit_hmi_state_reducer_implemented=false`、`cockpit_demo_control_loop_implemented=false`、
   `hardware_accessed=false`；Activity lifecycle/recreate state 属于 P4-W02，偏差由 `DEV-051` 跟踪。

状态：`client2_session_event_primary_api=true`、`client2_session_event_typed_callback=true`、
`client2_legacy_submit_compatibility=true`、`client2_scenario_alias_map_count=12`、
`client2_session_snapshot_verified=true`、`client2_session_event_sequence_verified=true`、
`client2_session_reconnect_replay_verified=true`、`client2_session_duplicate_event_suppressed=true`、
`client2_session_android13_arm64_verified=true`、`implementation_stage=P4-W02`。

## 43. P4-W02 Client2 immutable HMI state/lifecycle trace

Req IDs：`S2-UX-001..003`、`S2-HMI-003/005/006`、`APP-004`、`XSC-001/005/006`、
`NV-G-003/006/007`、`DEL-001/003/004/005`。

1. Client2 panel 的 View 不得直接从 callback 字符串改变业务状态。所有 panel visibility、connection、handle、snapshot、
   event、replay、overflow、close、error、detach 和 restore 必须先转换为 immutable reducer event，再生成新 HMI state。
2. reducer 必须以 session identity 和递增 event sequence 为边界：duplicate 返回原 state；gap 进入
   `CB_HMI_EVENT_GAP` fail-closed；cross-session callback 不得覆盖当前 UI。
3. maintained Java `CockpitControlCoordinator` 必须直接实现 typed `ScenarioCallback` 并持有 caller-owned
   `SessionConnection`。旧 `onBridgeStatus/onBridgeReply/onBridgeFailure` 不得作为 renderer authority。
4. MainActivity 仅允许一行 Smali bootstrap 调用 Java coordinator；旧 Smali controller、static request-in-flight owner 和
   UiUpdate runnable 必须删除。View bind、menu toggle、outside dismiss、scenario replacement 和 lifecycle 均由 Java 所有。
5. hide 只改变 panel visibility，不得清空 Session/snapshot/event state。Activity destroy 必须 close connection 并保留
   immutable state；recreate 必须通过 existing handle + opaque cursor 重新 observe，而不是创建假 terminal 或新执行结果。
6. process-recreation checkpoint 必须是 app-private、bounded、schema-versioned，只允许 panel、UI/canonical alias、
   SessionHandle metadata、last sequence 和 opaque cursor；禁止持久化 utterance、snapshot summary、assistant/model text、
   vehicle payload、设备身份、signing material 或 raw log。
7. Android 13/API 33 ARM64 必须验证 reducer projection、HMI-owned Session replacement、Runtime death replay/duplicate
   suppression、Client2 force-stop/relaunch resume、hidden state restore、menu reopen 和 text-free checkpoint。
8. 本包不得实现四阶段 shell、HVAC/Seat control、scenario compiler/Graph/Effect dispatch 或硬件访问。

状态：`cockpit_hmi_state_immutable=true`、`cockpit_hmi_state_reducer_implemented=true`、
`cockpit_hmi_lifecycle_owner_java=true`、`client2_smali_controller_retired=true`、
`client2_hmi_checkpoint_resume_verified=true`、`client2_hmi_hidden_state_recreation_verified=true`、
`client2_hmi_checkpoint_text_persisted=false`、`legacy_text_callback_authoritative=false`、
`cockpit_demo_control_loop_implemented=false`、`scenario_execution_enabled=false`、
`hardware_accessed=false`、`implementation_stage=P4-W03`。

## 44. P4-W03 intent-first four-stage overlay shell trace

Req IDs：`S2-UX-001..003`、`S2-HMI-001..003/006`、`APP-004`、`XSC-001/005/006`、
`NV-G-003/006/007`、`DEL-001/003/004/005`。

1. Client2 主交互面只允许四项 bounded natural-scene input：`care.fatigue`、`care.cold`、`skill.nap`、
   `task.home`。诊断、安全、Memory、NPU 等兼容 alias 可保留在 bridge allowlist，但不得回到主按钮台。
2. overlay 必须包含 Intent、Plan、Execution、Result 四个明确 stage；stage selection、scenario submit 后转 Plan、
   drawer open/close 和 panel hide 都必须经唯一 `CockpitHmiReducer`，View 不得自建第二状态机。
3. Header 必须持续显示 connection、source 和 driving state。输入未接 Context 时 source 显示 `UNAVAILABLE`；
   driving state 未接时显示 `UNKNOWN` 并标记 restricted，不得猜测 PARKED 或 SIMULATED。
4. Plan/Execution/Result 只能投影已有 Session/Event 证据。Graph 未接显示 `NOT WIRED`，Effect 未接显示
   `NOT DISPATCHED`，readback 未接显示 `UNAVAILABLE`，不得将 accepted Session 表述为车辆动作完成。
5. HVAC/Seat 入口必须位于次级 device detail drawer。P4-W03 只允许 placeholder/detail scaffold，不允许 power、
   temperature、fan、recline 等控件生成 Effect 或直接调用 debug/production adapter。
6. 1920x1080 当前目标的 panel 必须位于 `(1264,160)-(1888,1048)`，尺寸 `624x888`，主背景 alpha=0.60；
   原 Client2 render region 保持 full-screen，底部导航显示/隐藏和 panel 外点击隐藏保持有效。
7. Android 13/API 33 ARM64 必须验证 signed APK、exact bounds、四阶段、drawer、Session projection、Runtime/Client2
   process recovery；测试证据不得包含 raw device identity、用户/模型文本或车辆 payload。
8. 本包不得启用 scenario/Graph/Effect dispatch、HVAC/Seat control、车辆/VHAL/NPU/Driver-HAL 或生产资格。

状态：`cockpit_hmi_four_stage_shell_implemented=true`、`cockpit_hmi_intent_first_primary=true`、
`cockpit_hmi_safe_frame_1920x1080_verified=true`、`cockpit_hmi_material_alpha=0.60`、
`cockpit_hmi_device_drawer_scaffolded=true`、`cockpit_hvac_surface_implemented=false`、
`cockpit_seat_surface_implemented=false`、`scenario_execution_enabled=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P4-W04`。

## 45. P4-W04 HVAC control surface trace

Req IDs：`S2-HMI-001/003/004/005`、`S2-ADP-001`、`APP-004`、`XSC-001/005/006`、
`NV-G-003/006/007`、`DEL-001/003/004/005`。

1. HVAC detail drawer 必须提供 power、DRIVER/FRONT_PASSENGER/CABIN zone、16.0-30.0 C/0.5 C temperature、
   fan 0-7、AUTO、A/C、SYNC、AUTO/FACE/FEET/DEFROST airflow 和 WARM/COOL/CLEAR preset；控件不得成为顶层导航。
2. `HvacControlIntent` 必须是 immutable、范围受限、可 canonical round-trip 的 target。未知/重复字段、非 canonical
   integer/enum、越界温度/风量必须在 bind 前失败，不得由 View 拼装任意字符串或车辆属性。
3. `CockpitHvacState` 必须独立保存 desired、desired revision、submitted revision、request state、reported、source、
   quality 和 effect state。desired 变化不得更新 reported；无 trusted observation 时 source=`UNAVAILABLE`、
   quality=`NO_EVIDENCE`、effect 不得进入 `VERIFIED`。
4. 所有 HVAC desired change 必须经唯一 `CockpitHmiReducer`。Coordinator 必须取消旧 pending callback，并以主线程
   300 ms debounce 将连续输入合并为最后一个 immutable target；Activity detach 不得继续提交 pending request。
5. 手动 HVAC 必须通过 `Client2ScenarioBridge.openHvacSession` 和 `SessionClient` 创建
   `scene.manual.hvac.adjust.v1`；View/Coordinator 不得调用 debug/production Adapter、CarProperty、VHAL 或硬件接口。
6. 冻结 Session V1 没有 typed parameter 和 `HMI_CONTROL` source。P4-W04 只允许 bridge 将 exact `HVAC1` canonical
   grammar 放入 `utterance` 并使用 `SOURCE_HMI_BUTTON`；日志不得记录参数。V1 AIDL/hash/schema 不得修改，偏差由
   `DEV-054` 跟踪并由后续 versioned Session contract 关闭。
7. Session open/snapshot 只证明 governed admission，HMI 最多显示 `REQUESTED`；不得显示 DISPATCHED/APPLIED/VERIFIED，
   不得把 Runtime 固定 summary 当作车辆回读。release/production 缺 Adapter 时继续失败关闭。
8. Android 13/API 33 ARM64 验收必须证明完整控件可见、三次快速 step 合并为一个 manual Session、desired 24.0 C、
   canonical scenario、reported unavailable、no verified、no service/hardware dispatch。证据不得包含 raw device identity、
   HVAC 参数 payload、车辆数据或用户/模型文本。

状态：`cockpit_hvac_surface_implemented=true`、`cockpit_hvac_reducer_owned=true`、
`cockpit_hvac_debounce_ms=300`、`cockpit_hvac_governed_manual_session=true`、
`cockpit_hvac_desired_reported_separation_verified=true`、`cockpit_hvac_reported_readback_available=false`、
`cockpit_hvac_verified_before_readback=false`、`hvac_manual_typed_parameter_field=false`、
`scenario_execution_enabled=false`、`production_effect_dispatch_enabled=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P4-W05`。

## 46. P4-W05 Seat control surface trace

Req IDs：`S2-HMI-002..005`、`S2-SAF-001`、`S2-ADP-001`、`APP-004`、`XSC-001/005/006`、
`NV-G-003/006/007`、`DEL-001/003/004/005`。

1. Seat detail drawer 必须提供 DRIVER/FRONT_PASSENGER/REAR_LEFT/REAR_RIGHT、heat/vent 0-3、OFF/RELAX/WAKE
   massage、0-60 degree recline 和 UPRIGHT/COMFORT/REST preset；控件不得成为顶层导航。
2. `SeatControlIntent` 必须 immutable、范围受限且 canonical round-trip。heat 与 vent 必须互斥；未知、重复、非
   canonical、越界或 heat/vent 同时非零字段必须在 bind 前失败，View 不得拼装 wire 或车辆属性。
3. `CockpitSeatState` 必须独立保存 desired/submitted revision、request、reported/source/quality/effect、typed driving/
   occupancy/belt Context 和 Safety decision。desired 变化不得更新 reported；无 observation 时不得显示 VERIFIED。
4. Context 未接时 driving 必须为 `UNKNOWN_RESTRICTED`，occupancy/belt/source/quality 必须为 UNKNOWN/UNAVAILABLE/
   NO_EVIDENCE。驾驶席位置动作不得改变 desired、不得创建 Session。MOVING 驾驶席位置动作同样失败关闭。
5. PARKED+OCCUPIED+UNBELTED 的 REST 只允许进入 `WAITING_APPROVAL`，不得进入 debounce/Session。该 host policy 不是
   量产 Safety authority；未来 approval 后 dispatch 前仍必须由 Runtime/Adapter 重新读取并验证 Context revision。
6. 低风险 heat/vent/massage change 必须经唯一 reducer，Coordinator 以主线程 300 ms debounce 合并，随后只通过
   `Client2ScenarioBridge.openSeatSession` 创建 `scene.manual.seat.adjust.v1`；不得调用 Adapter/CarProperty/VHAL/硬件。
7. 冻结 Session V1 无 typed parameter、HMI_CONTROL 或 approval response。P4-W05 只允许 bridge 将 exact `SEAT1`
   canonical grammar 放入 utterance 并使用 SOURCE_HMI_BUTTON；日志不得记录参数，V1 hash/schema 不得修改；`DEV-055`
   跟踪该偏差。
8. Session admission 最多将 Seat Effect 投影为 REQUESTED。不得显示 DISPATCHED/APPLIED/VERIFIED，不得将 desired、
   fixed summary、UI Safety decision 或 debug host test 当作车辆 readback/approval/执行成功。
9. Android 13/API 33 ARM64 验收必须证明 controls 可达、heat 后 vent 合并成一个 manual Session、heat=0/vent=1、
   canonical scenario、reported unavailable，以及 UNKNOWN_RESTRICTED driver recline 保持 0/no Session/no dispatch。

状态：`cockpit_seat_surface_implemented=true`、`cockpit_seat_reducer_owned=true`、
`cockpit_seat_debounce_ms=300`、`cockpit_seat_governed_manual_session=true`、
`cockpit_seat_heat_vent_mutex_verified=true`、`cockpit_seat_unknown_restricted_fail_closed=true`、
`cockpit_seat_parked_rest_approval_required=true`、`cockpit_seat_desired_reported_separation_verified=true`、
`cockpit_seat_reported_readback_available=false`、`cockpit_seat_verified_before_readback=false`、
`seat_manual_typed_parameter_field=false`、`scenario_execution_enabled=false`、
`production_effect_dispatch_enabled=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P4-W06`。

## 47. P4-W06 observable execution timeline trace

Req IDs：`S2-UX-001`、`S2-HMI-003/006`、`S2-EVT-001`、`APP-004`、`XSC-001/005/006`、
`NV-G-003/006/007`、`DEL-001/003/004/005`。

1. Client2 Execution surface 必须始终显示 Intent、Context、Plan、Policy、Graph、Effect、Readback 七阶段；每阶段必须有
   status、target、source、result，不能用单一“执行中/完成”掩盖 partial、failure 或 unavailable。
2. 时间线必须是 immutable、View-independent、由唯一 `CockpitHmiReducer` 持有。View/Coordinator 不得直接改变阶段，
   不得从用户文本、assistant text、desired control 或固定 summary 推断 APPLIED/VERIFIED。
3. 进入场景只允许 Intent=REQUESTED；`SessionHandle` admission 只允许 Intent/Policy=SESSION_ACCEPTED。`activePlanRevision=0`
   必须保持 Plan=NOT_PUBLISHED；无 typed action/effect event 时 Graph=NOT_WIRED、Effect=NOT_DISPATCHED。
4. 只有通过 `EventContract.validateEvent` 的 allowlisted typed `RuntimeEvent` 可更新时间线。投影不得保留 raw session/event/
   action/observation ID、digest、用户/模型文本或车辆 payload；最多保留最新八条 sequence/type/status/target/source/result。
   Event V1 无 payload 的 Effect lifecycle 只能继承同一 timeline 最近一次 validated Action capability，不得从文本推断。
5. `EffectObserved` 只有 outcome=OBSERVED 且 quality=FRESH 时进入 APPLIED；`EffectVerified` 只有 outcome=VERIFIED 且
   quality=FRESH 时进入 VERIFIED。STALE/CONFLICT/UNAVAILABLE 必须映射 NO_EVIDENCE/MISMATCH/UNAVAILABLE。
6. optional `ActionRejected` 显示 SKIPPED；required rejection、approval expiry、Effect failure、compensation 必须分别保留
   REJECTED/FAILED/COMPENSATING/COMPENSATED 语义。Media STOP 和 Navigation CANCEL 必须有独立 unavailable/typed projection。
7. 当前 Runtime 未发布 typed Plan/Action/Effect/Observation，实体 APK 必须显示 NOT_PUBLISHED/NOT_WIRED/
   NOT_DISPATCHED/UNAVAILABLE，不得添加 debug event、Adapter 或硬件桩来伪造通过。
8. Android 13/API 33 ARM64 验收必须覆盖七阶段可达、1920x1080 safe frame、Session admission 不降级、Media/Nav
   projection、ScenarioRequested trace，以及 service/effect/hardware dispatch 为 false。

状态：`cockpit_execution_timeline_implemented=true`、`cockpit_execution_timeline_reducer_owned=true`、
`cockpit_execution_typed_event_projection=true`、`cockpit_execution_trace_capacity=8`、
`cockpit_execution_plan_published=false`、`cockpit_execution_effect_dispatch_enabled=false`、
`cockpit_execution_readback_available=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。

## 48. P4-W07 approval/partial/retry/undo UX trace

Req IDs：`S2-UX-003`、`S2-HMI-003`、`S2-SAF-001`、`S2-EFF-001`、`APP-004`、
`XSC-001/005/006`、`NV-G-003/006/007`、`DEL-001/003/004/005`。

1. Client2 必须由 immutable `CockpitRecoveryState` 投影 approval、终态 effect evidence、Session aggregate 和
   compensation；View 只能渲染，不得保存或推断授权状态。
2. `WAITING_FOR_CONFIRMATION` 或 typed `ApprovalRequested` 必须显示审批请求。reason、target、expiry 只能来自
   `ApprovalPrompt` 或 validated capability；当前 Event V1 不提供 reason/expiry 时必须显示 UNAVAILABLE。
3. typed `EffectVerified` 只有 timeline 已判定 VERIFIED 才计入 verified；`EffectFailed` 计入 failed；证据冲突或非 fresh
   verification 计入 inconclusive。混合 verified/failed/inconclusive 或 Session=PARTIALLY_COMPLETED 必须显示 partial。
4. `CompensationStarted/Observed` 必须显示 COMPENSATING/COMPENSATED/INCONCLUSIVE；补偿事件不得自行生成 `UndoHandle`。
5. approval response、retry failed、undo 必须各自依赖已发布服务和 typed token/eligibility。当前 Client2 未收到
   `ApprovalPrompt`、`EffectObservation.retryable`、`UndoHandle`，approve/reject/retry/undo 必须 visible+disabled。
6. recovery state 不得保留 approval/effect/observation/undo ID、digest、用户/模型文本或车辆 payload；outside dismiss
   只隐藏 overlay，不取消 Session，也不得清空 approval/partial/compensation projection。
7. 当前实体 Runtime 只发布 Session admission/ScenarioRequested，因此实体默认必须显示 Approval/Reason/Target/Expiry
   UNAVAILABLE、Outcome NO EVIDENCE、Compensation UNAVAILABLE；不得用 host future-event test 冒充实体能力。
8. Android 13/API 33 ARM64 验收必须覆盖四类 disabled command、恢复状态可达、outside dismiss/reopen 保留状态、
   1920x1080 safe frame，以及 service/effect/hardware dispatch 为 false。

状态：`cockpit_recovery_state_reducer_owned=true`、`cockpit_approval_details_fail_closed=true`、
`cockpit_partial_outcome_projection=true`、`cockpit_compensation_projection=true`、
`cockpit_approval_response_service_published=false`、`cockpit_retry_service_published=false`、
`cockpit_undo_service_published=false`、`cockpit_recovery_commands_enabled=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。


## 49. P4-W08 driving restriction renderer trace

Req IDs：`S2-UX-002`、`S2-HMI-002`、`S2-SAF-001`、`APP-004`、`XSC-001/005/006`、
`NV-G-005/006/007`、`DEL-001/003/004/005`。

1. Client2 必须以 View-independent immutable `PanelPresentationMode` 表示 PARKED 完整呈现与 MOVING/UNKNOWN 受限呈现；
   唯一 `CockpitHmiState`/reducer 持有当前 mode，View 不得自建 driving 状态。
2. 只有 source 非 UNAVAILABLE、quality=OBSERVED、revision>0 且 driving=PARKED 的 Context 才能选择 PARKED_FULL。
   null、unavailable、stale/untrusted、MOVING 和 UNKNOWN 必须统一为 MOVING_RESTRICTED。
3. 受限呈现必须保留单行场景/状态摘要，隐藏 Intent/Context/Plan/Execution/Result 长文本和 bounded trace；不得通过
   assistant text、desired state 或本地默认值推断车辆已驻车。
4. 受限呈现必须禁用 HVAC/Seat 参数编辑和 `skill.nap` 等高风险场景；对已显示控件的 click handler 还须进行二次
   policy 检查，不能仅依赖 disabled 样式。
5. PARKED_FULL 只恢复 UI 呈现和参数入口，不授予 Effect、approval 或车辆动作权限。两种 mode 的
   `isEffectAuthorizationSource()` 都必须为 false，Runtime Governance/Safety 继续独立、权威并在 dispatch 前重验。
6. 实体设备未接可信 driving Context 时必须默认受限；不得为通过测试注入伪 PARKED。host test 覆盖 PARKED/MOVING/
   unavailable；受保护的实体 Context 切换与 PARKED 完整模式复测属于 P4-W09。
7. 本包不得接 Android Car/CarProperty、VHAL、Vendor service、NPU、Driver/HAL，不得启用 Graph/Effect dispatch 或
   伪造 readback。当前 HVAC/Seat 的历史 PARKED/manual 验收继续有效，但 P4-W08 实体 run 只验证受限只读路径。

状态：`cockpit_driving_ux_policy_implemented=true`、`cockpit_unknown_driving_restricted=true`、
`cockpit_moving_long_text_hidden=true`、`cockpit_restricted_parameter_editing_disabled=true`、
`cockpit_high_risk_controls_disabled=true`、`cockpit_runtime_policy_authority_independent=true`、
`cockpit_hvac_manual_session_admission_retested=false`、`cockpit_seat_manual_session_admission_retested=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## 50. P4-W09 engineer simulation drawer trace

Req IDs：`S2-HMI-004`、`S2-ADP-001`、`S2-OBS-001`、`APP-004`、`XSC-001/005/006`、
`NV-G-003/005/006/007`、`DEL-001/003/004/005`。

1. Client2 工程师入口必须默认 `gone`，只有显式绑定 Runtime debug component、完成
   `IDebugSimulationController` version/hash 校验并收到成功回调后才显示；release Runtime 不得声明该 Service。
2. 外层必须由 `com.centralbrain.permission.CONTROL_DEBUG_SIMULATION` signature permission 保护，内层必须由
   Runtime caller identity 与 `debug.simulation.control` capability 再次授权；production policy 不得授予该 capability。
3. `CockpitEngineerState` 必须为 Android-independent immutable state，记录连接态、驾驶三态、占用/安全带、固定
   HVAC/Seat adapter、六类 fault、Controller status 与严格单调 revision；View 不能直接持有 Binder 返回值。
4. 每个写命令只有在 Controller 明确成功并返回比当前更大的 revision 后才能通过唯一 reducer 更新 HMI。超时、断链、
   协议不匹配、非单调 revision 或失败码必须失败关闭，不得乐观切换 PARKED 或故障状态。
5. driving=UNKNOWN 或 reset 必须投影 unavailable/restricted；只有 connected、PARKED/MOVING、revision>0 的确认状态才能
   生成 source=SIMULATED、quality=OBSERVED 的测试 Context。该投影不得标记为 production trusted。
6. PARKED/MOVING/UNKNOWN 只用于验证 P4-W08 呈现矩阵；工程抽屉、SIMULATED Context 和 presentation mode 的
   `isEffectAuthorizationSource()` 必须始终为 false，不能绕过 Runtime Policy/Safety 或调用 Effect/Adapter。
7. 占用与安全带只允许 canonical paths `Vehicle.Cabin.Seat.IsOccupied`、`Vehicle.Cabin.Seat.IsBelted` 和
   `row1.driver`；adapter ID 只允许 `debug.simulated.hvac.v1`、`debug.simulated.seat.v1`，禁止自由文本/反射发现。
8. Android 13/API 33 ARM64 验收必须覆盖签名权限、capability、协议握手、驾驶三态、占用/安全带、fault matrix、
   revision 单调、reset 失败关闭、1920x1080 边界及 release Service absent；硬件/车辆/Effect dispatch 必须为 false。

状态：`cockpit_engineer_simulation_drawer_implemented=true`、
`cockpit_engineer_signature_permission_required=true`、`cockpit_engineer_capability_required=true`、
`cockpit_engineer_context_revisioned=true`、`cockpit_engineer_runtime_release_service_absent=true`、
`cockpit_engineer_effect_authorization_source=false`、`cockpit_engineer_production_available=false`、
`vehicle_signal_provider_wired=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。

## 51. P4-W10 scenario/manual-control synchronization trace

Req IDs：`S2-HMI-001..006`、`S2-SCN-001`、`APP-004`、`XSC-001/005/006`、
`NV-F-001/003/009`、`NV-G-003/005/006/007`、`DEL-001/003/004/005`。

1. Client2 的 14 个 UI alias 与 canonical scenario ID 必须由单一 immutable catalog 提供，Bridge 和 HMI 不得维护两份
   可漂移映射；未知 alias 必须在打开 Session 前拒绝。
2. cold、fatigue、rest 只能投影 manifest catalog 中 HVAC/Seat 的 required/optional 参与角色；不得在 Client2 生成温度、
   风量、靠背角度等 typed desired target，也不得把 catalog role 标记为 Runtime Plan。
3. manual HVAC/Seat 必须继续使用 `ScenarioClient` 接口、相同 Session admission 和 typed Event stream。View 不得直接调用
   Adapter、Android Car、VHAL、Vendor service、NPU 或 Driver/HAL。
4. `CockpitScenarioControlState` 必须为 Android-independent immutable state；唯一 `CockpitHmiReducer` 在 scenario request、
   Session opened、Snapshot、Runtime Event、failure、stream close 和 restore 时原子更新 origin、device role、catalog match、
   lifecycle、active Plan revision 与 last event sequence。
5. 四阶段 shell、结果页和 HVAC/Seat drawer 必须读取同一 state revision。设备详情中的 lifecycle/event sequence 必须与
   HMI Session state 同源；重复、旧 Session 或 event gap 不能推进设备投影。
6. admitted canonical ID 与请求 catalog 不一致时必须清除 HVAC/Seat role，标记 MISMATCH/FAILED，并公开
   `CB_HMI_SCENARIO_MISMATCH`；不得继续显示旧场景的设备参与声明。
7. 只有 `SessionSnapshot.activePlanRevision>0` 才能显示 Plan PUBLISHED；当前 revision=0 必须显示 NOT PUBLISHED。
   `isEffectDispatchEnabled()` 与 `isReadbackAvailable()` 在当前实现必须保持 false。
8. Android 13/API 33 ARM64 验收必须覆盖 cold/fatigue/rest、manual HVAC/Seat、同一 event sequence、canonical mismatch
   host gate，以及 Plan/Effect/readback/hardware 均未启用。

状态：`cockpit_scenario_control_state_reducer_owned=true`、`cockpit_scenario_catalog_normalized=true`、
`cockpit_scenario_manual_shared_client=true`、`cockpit_scenario_device_session_synchronized=true`、
`cockpit_scenario_plan_publication_inferred=false`、`cockpit_scenario_effect_dispatch_enabled=false`、
`cockpit_scenario_readback_available=false`、`scenario_execution_enabled=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## 69. P6-W06 Active suggestion UX trace

本增量映射 `S2-UX-002`、`S2-TRG-002`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`：

1. `ActiveSuggestionController` 必须固定 schema V1、16 条 active、64 条 replay、64 个 never-ask scope、5 分钟有效期和
   24 小时 cooldown 上限；容量满不得静默驱逐 active suggestion。
2. candidate 只接受 canonical suggestion/scenario/zone ID、lowercase SHA-256 owner/payload/evidence、fixed `ReasonCode`、
   monotonic observed/expiry 与 bounded cooldown；不得接受用户/模型自由文本、任意 map/byte payload 或 executable action。
3. merge key 必须是 owner+scenario+zone。重复 scope 合并 count、取最高 priority reason 和最大 expiry/cooldown；不同 scope 不得合并。
4. suggestion ID exact replay 必须不改变 state；相同 ID 不同 payload/fields 必须返回 conflict。future、expired、capacity、cooldown 和
   never-ask suppression 必须显式返回 code。
5. dismiss 在 PARKED/MOVING/UNKNOWN 均可用，并为 merge scope 建立 process-local cooldown；cooldown 内新 suggestion 必须显式 suppress。
6. “不再询问”只能在 PARKED 生效，按 owner+scenario+zone 隔离并移除 active；MOVING/UNKNOWN 必须在 mutation 前拒绝。
7. PARKED snapshot 必须为 `FULL_CARD`，展示固定 why key、plan key、merge count、cooldown 和 REVIEW/DISMISS/NEVER_ASK。
8. MOVING 与 UNKNOWN 必须保守地使用 `MINIMAL_BANNER`，只显示最高优先级一条和 DISMISS；复杂 why/review/never-ask 隐藏。
9. minimal voice 只能输出固定 projection key；`isVoiceSynthesisRequested=false`，不得启动 TTS/audio 或记录话术。
10. `neverAsk` 只更新进程内 preference projection，`isPreferencePersisted=false`；HMI 不得宣称已经持久化或跨重启生效。
11. suggestion card 不得授权 scenario、approval、Graph 或 Effect。production source/Trigger/Graph/Effect/voice/persistence/hardware flags 必须 false。
12. debug `ActiveSuggestionHmiActivity` 必须是 DUMP-protected、release manifest absent、响应式右侧半透明浮层；自动探针只输出 boolean。
13. JVM 必须覆盖 full card、merge/replay、never-ask、moving/unknown、expiry/cooldown/conflict/capacity 和 false boundaries；
    debug/release 必须编译同一 main controller。
14. Android 13 ARM64 probe 已通过，`active_suggestion_android13_arm64_verified=true`；该证据只证明 UX policy/projection，
    不证明 Client2 production integration、自动编排、车控、模型/NPU 或目标硬件资格。

状态：`active_suggestion_controller_defined=true`、`active_suggestion_full_card_verified=true`、
`active_suggestion_merge_replay_verified=true`、`active_suggestion_moving_minimal_verified=true`、
`active_suggestion_never_ask_verified=true`、`active_suggestion_android13_arm64_verified=true`、
`active_suggestion_hmi_projection_only=true`、`active_suggestion_production_source_wired=false`、
`active_suggestion_preference_repository_wired=false`、`active_suggestion_voice_engine_wired=false`、
`trigger_engine_wired=false`、`graph_execution_enabled=false`、`effect_dispatch_enabled=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## 68. P6-W05 Context source adapters trace

本增量映射 `S2-CTX-001`、`S2-EVT-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`：

1. 首版 source catalog 必须精确包含 Runtime health、simulated vehicle signal、time 三项；source ID、type、key prefix、
   maximum age、simulated 标记与 descriptor digest 必须 build-owned，不接受任意 source name 或动态注册。
2. `ContextSourceAdapter<I>` 必须只执行 caller-supplied sample 到 immutable normalized observation/result 的转换；不得主动读取
   Runtime Service、system clock、Vehicle service/property、Event Broker、模型、网络或硬件。
3. normalized observation 必须包含 descriptor、canonical context key、area、`SignalQuality`、closed typed scalar、observed/normalized
   elapsed time、maximum age、trust class、source evidence digest 与 observation digest；不得接受 Object/Bundle/JSON/Parcelable。
4. quality 与 scalar 必须一致：VALID/STALE 必须有 typed scalar，UNAVAILABLE/ERROR/CONFLICT 不得有 scalar。future sample、invalid
   input 与 provenance mismatch 必须无 observation 失败关闭。
5. Runtime health 只允许 HEALTHY/DEGRADED/UNAVAILABLE/ERROR；HEALTHY/DEGRADED 超过 1000 ms 归一化为 STALE，unavailable/error
   保留无 scalar 状态。adapter 只消费 evidence，不检查 Runtime 进程。
6. simulated vehicle adapter 必须复用 P2-W01 `VehicleSignalPath/SignalValue`，只接受 `SignalSource.SIMULATED`；AAOS/VENDOR/DERIVED
   一律 `REJECTED_PROVENANCE`。VALID 超过 path maximum age 必须降为 STALE，fresh-but-marked-STALE 必须拒绝而非升级。
7. simulated vehicle observation 必须保留 path scalar type、unit-bound area、revision 与 timestamp digest binding；它始终
   `trust=SIMULATED`、`production_trusted=false`；唯一 TEXT path=CURRENT_GEAR 只接受 fixed gear allowlist，不得携带任意文本，
   且不得触发 provider discovery 或 property mapping。
8. time adapter 只允许显式 epoch、receive elapsed time、UTC offset 和 evidence digest；offset 范围为 -14h..+14h，输出
   0..1439 local minute-of-day。禁止直接调用系统 clock，future/stale 只按 injected monotonic time 判定。
9. descriptor 与 observation digest 必须覆盖 schema、source/key/quality/value/time/trust/evidence，且相同输入确定性相同；debug
   probe 与日志只输出 boolean/count marker，不输出 sample scalar、车辆 payload、设备身份或用户/模型文本。
10. adaptation result 只表示 source availability，不发布 Trigger input，不调用 TriggerEngine、Consent、Runtime/Graph/Effect 或
    EventBroker。`production_registry_published=false`、`runtime_wired=false`、`trigger_engine_wired=false`。
11. JVM 必须覆盖固定 catalog、Runtime fresh/stale/unavailable、simulated typed/freshness、provenance/quality conflict、time/future 和
    production false boundary；debug/release 编译相同 main source，probe 只能存在于 debug manifest。
12. Android 13 ARM64 probe 已执行，`context_source_android13_arm64_verified=true`；该证据只证明纯 Java normalization，
    不证明真实 vehicle source、Runtime publication、Trigger composition、Driver/HAL 或目标硬件资格。真实 vehicle source 后置 P8。

状态：`context_source_adapter_contract_defined=true`、`context_source_count=3`、
`context_source_allowlist_verified=true`、`context_source_runtime_health_verified=true`、
`context_source_simulated_vehicle_verified=true`、`context_source_time_verified=true`、
`context_source_freshness_quality_verified=true`、`context_source_fail_closed_verified=true`、
`context_source_android13_arm64_verified=true`、`context_source_production_registry_published=false`、
`context_source_runtime_wired=false`、`context_source_trigger_engine_wired=false`、
`vehicle_signal_provider_wired=false`、`vehicle_property_mapping_configured=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-076`、`ISSUE-031`。

## 67. P6-W04 Proactive consent/policy trace

本增量映射 `S2-SAF-001`、`S2-MEM-001`、`S2-EVT-001`、`S2-OBS-001`、`DEL-001/004/005`：

1. `ProactiveConsentPolicy` 必须是 schema V1、Android-independent、contract-test-only、最多 128 个 active grant 与 256 个
   request replay entry；不得暴露 production factory。
2. GRANT 必须精确绑定 grant ID、owner scope digest、scenario ID 与 manifest SHA-256、`VehicleCapability.CapabilityId`、
   `ScenarioManifest.Zone`、maximum risk 和 1..30 天 TTL；REVOKE 只允许 grant ID + owner scope。
3. 每次 mutation evidence 必须绑定 request ID、mutation digest、consent receipt digest、privacy policy digest 与 elapsed validity；
   evidence 最长 5 分钟，过期、未来或 digest mismatch 不得调用 authority 或改变状态。
4. 创建和撤销 grant 只允许可信 driving state=PARKED；MOVING/UNKNOWN 必须返回 DRIVING_RESTRICTED。该状态只约束 consent
   management，不替代 dispatch-time Safety authority。
5. HIGH/CRITICAL maximum risk 必须在调用 consent authority 前返回 `HIGH_RISK_GENERIC_GRANT_FORBIDDEN`；HIGH/CRITICAL
   candidate 必须始终返回 `EXPLICIT_APPROVAL_REQUIRED`，任何 HMI、模型或测试 authority 都不能覆盖。
6. independent `ConsentAuthority` 只有显式 ALLOWED 才可继续；DENIED、null 或 exception 分别失败关闭且不能创建/撤销 grant。
7. 相同 request ID + mutation digest 必须返回 REPLAYED 且不重复变更 revision；同 request ID 不同 digest 必须 REQUEST_CONFLICT。
8. grant ID 相同且 binding 一致返回 NO_CHANGE，不一致返回 GRANT_ID_CONFLICT；满容量不驱逐 active grant。TTL 到期必须在读取、
   mutation 或 snapshot 前清除；revoke 必须 owner scoped。
9. `AutoExecutionCandidate` 必须绑定 suggestion digest、owner、scenario ID/digest、capability、zone 和四级 risk；只有所有字段
   精确匹配且 candidate risk 不超过 grant maximum risk 才可 `POLICY_ELIGIBLE`。
10. `POLICY_ELIGIBLE` 不是 Effect authorization：`effect_dispatch_authorized=false` 且
    `safety_revalidation_required=true`；P6-W04 不调用 TriggerEngine、Session/Plan、Graph、Effect 或 Vehicle。
11. JVM 必须覆盖 exact binding、HIGH/CRITICAL、mismatch/risk、TTL/revoke/replay/conflict/capacity、driving/evidence/authority 与全部
    production false boundaries；debug/release 必须编译同一 main source，probe 只能存在于 debug manifest。
12. Android 13 ARM64 probe 已执行，`proactive_consent_android13_arm64_verified=true`；该证据不证明 production consent
    authority、durable grant、auto execution、Effect dispatch 或目标硬件资格。

状态：`proactive_consent_policy_defined=true`、`proactive_grant_binding_verified=true`、
`proactive_high_critical_generic_grant_blocked=true`、`proactive_grant_ttl_revoke_verified=true`、
`proactive_policy_fail_closed_verified=true`、`proactive_consent_android13_arm64_verified=true`、
`proactive_policy_process_local=true`、`proactive_grant_persistence_wired=false`、
`proactive_consent_authority_wired=false`、`proactive_auto_execution_enabled=false`、
`proactive_runtime_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-075`、`ISSUE-031`。

## 66. P6-W03 TriggerRule manifest/engine trace

本增量映射 `S2-EVT-001`、`S2-SCN-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`：

1. `TriggerRule.Manifest` 必须是 schema V1、immutable、最多 64 条规则；rule ID 唯一并按 ID 排序，manifest digest 必须与输入顺序无关。
2. 每条规则必须绑定 target scenario ID 与 scenario manifest SHA-256、fixed metric、zone、threshold operator/value、sustain window、
   maximum sample gap、minimum matching samples、debounce、cooldown 和 maximum observation age。
3. metric 只允许 build-owned enum 与固定数值范围；当前不接受 vendor property ID、device node、自由文本 source key 或模型表达式。
4. observation 必须绑定 ID、metric、zone、scope digest、elapsed timestamp、quality、bounded scalar 和 source evidence digest；不可用质量
   不能携带 scalar，VALID/STALE scalar 必须在 metric range 内。
5. Engine 必须按 threshold -> continuous sample gap -> sustain window/minimum samples -> debounce -> atomic cooldown 顺序求值；false、
   invalid quality、stale 或 sample gap 必须重置连续条件，不能把稀疏样本伪装成持续状态。
6. future/out-of-order observation 必须显式拒绝；observation ID exact replay 不重复改变状态，同 ID 不同 digest 返回 conflict。
7. `CooldownStore` 必须按 rule+scope 隔离、原子 reserve、最多 256 条；同 suggestion digest 幂等，其他 suggestion 在 cooldown 内显式
   suppressed，容量不足必须 fail closed。
8. 成功输出只允许 digest-only `ScenarioSuggestion`，绑定 rule/manifest/scenario/scope/metric/zone/observation/time，并声明 source=TRIGGER；
   不得包含用户文本、模型文本、Effect target 或车辆 payload。
9. P6-W03 只创建 suggestion；`auto_execution_enabled=false`、`effect_dispatch_enabled=false`。授权 scope/TTL/risk 属于 P6-W04，
   source adapter 属于 P6-W05，不得在本包隐式实现。
10. Engine/manifest/cooldown 只允许 `createForContractTest` composition；不接 P6-W01 broker、Runtime/Graph、Binder、Room/file、
    Vehicle/VHAL、Model/NPU、Driver/HAL、network、thread/executor。
11. JVM 必须覆盖 manifest digest/invalid、threshold-window-debounce、false/gap reset、cooldown scope/capacity/expiry、quality/freshness/
    ordering/replay 和全部 production false boundaries；debug/release 编译同一 main source。
12. Android 13 ARM64 probe 已通过，`trigger_engine_android13_arm64_verified=true`；该证据不构成 production source、
    proactive policy、durable cooldown、自动执行或目标硬件资格。

状态：`trigger_rule_manifest_defined=true`、`trigger_rule_manifest_verified=true`、
`trigger_threshold_window_debounce_verified=true`、`trigger_cooldown_scope_verified=true`、
`trigger_input_fail_closed_verified=true`、`trigger_suggestion_only_verified=true`、
`trigger_engine_android13_arm64_verified=true`、`trigger_engine_process_local=true`、
`trigger_cooldown_persistence_wired=false`、`trigger_source_adapter_wired=false`、
`trigger_auto_execution_enabled=false`、`trigger_runtime_wired=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## 52. P4-W11 accessibility/display matrix trace

Req IDs：`S2-UX-003`、`S2-HMI-001/002`、`APP-004`、`XSC-001/005/006`、
`NV-G-003/005/006/007`、`DEL-001/003/004/005`。

1. Client2 显示策略必须由 Android-independent immutable `CockpitDisplayPolicy` 单一持有；当前只允许横屏
   `1280x720@107dpi`、`1920x1080@160dpi`、`2560x1440@213dpi`，不得按相近尺寸或 density 自动猜测。
2. `fontScale` 只允许 0.85..1.30。无效 metrics、portrait、未列入 profile 或超出字体范围时，导航入口必须 disabled，
   浮窗必须保持隐藏并记录 bounded rejection code；不得改变 Session、Effect 或车辆状态。
3. 所有 Button 的触控目标必须至少 48dp。XML 提供静态尺寸，Coordinator 以当前 density 设置等价最小像素，避免
   OEM theme 覆盖后缩小可触区域。
4. 每个交互控件必须有非空 content description、可聚焦及 `importantForAccessibility=yes`。`+`、`-`、关闭等符号控件
   必须提供显式语义；选中/启用状态必须通过 selected/activated/stateDescription 表达，不能只依赖颜色。
5. 最长中文必须最多两行并尾部省略，滚动区域必须保持内容可达；在三档 profile 和 1.30 字体下不得出现 clickable
   控件重叠或浮窗越界。
6. Web 设计预览只能按 `min(1, viewport/canvas)` 缩小，不能把 1920x1080 画布放大并溢出背景。
7. `CockpitDisplayPolicy.isEffectAuthorizationSource()` 必须为 false；显示 profile、accessibility state 或 UI selected
   不能授予 Policy/Safety/Effect/Vehicle 权限。
8. Android 13/API 33 ARM64 验收必须覆盖三档 profile、1.30 fontScale、最长中文、content description、非颜色状态、
   48dp target 与至少一个未支持 profile 的失败关闭；不得记录原始设备身份或 UI dump。

状态：`cockpit_display_matrix_defined=true`、`cockpit_display_profile_count=3`、
`cockpit_touch_target_min_dp=48`、`cockpit_accessibility_semantics_runtime_owned=true`、
`cockpit_accessibility_state_not_color_only=true`、`cockpit_display_large_text_1_3_verified=true`、
`cockpit_display_unsupported_fail_closed=true`、`cockpit_display_matrix_android13_arm64_verified=true`、
`cockpit_display_effect_authorization_source=false`、`scenario_execution_enabled=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## 53. P4-W12 Android device acceptance/fault/recovery trace

Req IDs：`S2-UX-001..003`、`S2-HMI-001..006`、`S2-SCN-001`、`S2-SAF-001`、`S2-EFF-001`、
`APP-004`、`XSC-001/005/006`、`NV-G-003/005/006/007`、`DEL-001/003/004/005`。

1. P4 必须提供单一 aggregate runner，按 recovery、engineer fault、scenario/manual sync、display/accessibility 顺序调用
   已维护子套件；每个子套件的 marker 必须重新产生，禁止只读取历史报告。
2. Runner 必须要求 Android API 33 ARM64，设备选择不得输出 raw serial。每个子套件前清空 crash buffer，完成后检查
   Client2/Runtime 无 crash；最终必须重新启动 Activity、获取非空 UI tree 并找到导航 trigger。
3. recovery 必须覆盖 navigation show/hide/outside dismiss、Session replacement、Runtime/Client2 process death、snapshot/
   replay/dedup 和 hidden-state recreation；失败必须恢复临时 disabled Runtime。
4. physical-positive 必须覆盖 cold/fatigue/rest、manual HVAC/Seat Session admission、protected UNKNOWN/MOVING/PARKED/fault、
   1280x720/1920x1080/2560x1440 和 1.30 fontScale；display/font/rotation 必须恢复。
5. Plan/Effect/Media/Nav/approval/partial/mismatch/undo 必须按证据层分开：host typed projection 可以为 true，实体 Runtime
   publication/dispatch/readback/command service 必须保持 false；不得把 unavailable/disabled UI 解释为执行成功。
6. Runtime release 必须没有 debug simulation Service/adapter。若没有独立 production Client2 release artifact，必须公开
   `client2_production_release_artifact_available=false`，不得用 patched debug APK 冒充 release 资格。
7. Aggregate 通过只允许 `p4_w12_application_acceptance_complete=true`；`hmi_d4_demo_control_loop_complete`、
   `production_ready` 和 `target_hardware_validated` 必须保持 false。
8. 本包不得新增 Android Car/VHAL/Vendor/NPU/Driver-HAL/虚拟化调用，不得提交 UI tree、crash buffer、原始日志、设备身份、
   用户/模型文本或车辆 payload。

状态：`p4_w12_application_acceptance_complete=true`、`p4_android13_arm64_aggregate_verified=true`、
`p4_navigation_show_hide_verified=true`、`p4_natural_scenario_sync_verified=true`、
`p4_manual_hvac_seat_admission_verified=true`、`p4_moving_unknown_fail_closed_verified=true`、
`p4_runtime_client_process_recovery_verified=true`、`p4_ui_tree_verified=true`、`p4_crash_buffer_clean=true`、
`runtime_release_simulation_surface_absent=true`、`p4_plan_effect_projection_host_verified=true`、
`p4_automatic_plan_runtime_published=false`、`p4_production_effect_dispatch_enabled=false`、
`p4_approval_response_service_published=false`、`p4_undo_service_published=false`、
`p4_vehicle_readback_available=false`、`client2_production_release_artifact_available=false`、
`hmi_d4_demo_control_loop_complete=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。

## 54. P5-W01 Tool Manifest/Schema trace

Req IDs：`S2-TOL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`。

1. 每个 Tool 必须由 immutable `ToolManifest` 描述，schemaVersion 固定为 1；`toolId` 必须包含 `.vN` 后缀且与
   positive integer version 一致。owner、capability、input/output schema ID 都必须是 canonical qualified ID。
2. input/output 必须是不同 ID 的 versioned object schema；每个 schema 最多 32 个字段、16 KiB，每个字段只能是
   STRING、BOOLEAN、INTEGER 或 SHA256_DIGEST。字段名唯一，additional/unknown field 必须拒绝。
3. `ToolSchemaValidator` 必须 exact-class 校验，不允许 `Integer` 冒充 `Long`、任意 Number coercion、null、反射、
   Java serialization 或任意嵌套 object。missing required、unknown、null、type mismatch、range 和 aggregate oversize
   必须返回稳定 error code，不能包含原始值。
4. Manifest 必须包含 capability、LOW/MEDIUM/HIGH risk、10..120000 ms timeout、READ_ONLY/TOKEN_REQUIRED
   idempotency 和 health contract；health 必须 required-before-use 且 freshness 最大 60 秒，缺健康证据时失败关闭。
5. Manifest contract digest 必须由字段顺序无关的 canonical form 计算 SHA-256，用于后续 Registry 版本冲突判断；不得
   把运行时 health、执行结果或设备状态写进静态 digest。
6. P5-W01 不发布 ToolRegistry、Resolver、RuleSolver 或 ToolExecutor，不连接 AgentGraph/Runtime Service/Binder/Room，
   不加载动态 artifact，也不执行 Effect、车辆回读、模型/NPU、网络或 Driver/HAL。
7. Probe 只允许位于 debug source set，并由 DUMP permission 保护；release manifest 不得包含 probe。JVM 与 Android 13
   ARM64 必须覆盖 digest、input/output、unknown、type/range 和 health fail-closed。
8. P5-W01 完成只允许提升 `tool_manifest_contract_defined`；`tool_registry_published`、`tool_execution_enabled`、
   `production_ready` 和 `target_hardware_validated` 必须保持 false。

状态：`tool_manifest_contract_defined=true`、`tool_manifest_schema_version=1`、
`tool_manifest_contract_digest_verified=true`、`tool_schema_exact_scalar_validation_verified=true`、
`tool_manifest_health_fail_closed=true`、`tool_manifest_android13_arm64_verified=false`、
`tool_registry_published=false`、`tool_resolver_published=false`、
`tool_execution_enabled=false`、`production_tool_artifact_loaded=false`、`effect_dispatch_enabled=false`、
`vehicle_readback_accessed=false`、`npu_accessed=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## 55. P5-W02 Tool Registry/Resolver trace

Req IDs：`S2-TOL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`。

1. `ToolRegistry` 必须 immutable、最多保存 128 个 unique family/version。family ID 必须由 P5-W01 canonical Tool ID
   去除 `.vN` 派生；Registry 不得修改或重新解释 input/output schema。
2. 相同 family/version 和相同 contract digest 的重复注册必须幂等合并；相同 family/version 但 digest 不同必须以稳定
   `CONTRACT_CONFLICT` 拒绝，且结果不得依赖输入顺序。Registry digest 必须按 family/version/digest 排序计算。
3. `ToolResolver.Query` 必须包含 canonical family、positive inclusive version range、exact capability 和 optional lowercase
   SHA-256 contract digest pin。Resolver 选择范围内最高注册版本，然后复验 capability/digest；不得静默降级到其他版本。
4. `ToolHealthSnapshot` 必须与 Manifest 分离、最多 128 项，只保存 canonical check ID、HEALTHY/UNHEALTHY/UNKNOWN、
   elapsed-realtime observation time 和 positive revision。duplicate check、negative time/revision 必须拒绝。
5. Health eligibility 必须使用所选 Manifest 的 `HealthContract`：missing、unknown、unhealthy、stale、future observation、
   invalid current clock 全部失败关闭。动态 health 不得改变 Manifest 或 Registry digest。
6. Resolution 必须分别暴露 REGISTERED/NOT_REGISTERED、RESOLVED/NOT_RESOLVED、USABLE/NOT_USABLE 和稳定 failure code。
   最高版本 unhealthy 时必须返回 RESOLVED/NOT_USABLE，不得回退到旧的 healthy 版本。
7. `USABLE` 只表示可以进入 P5-W03 rule solving，不表示可执行。P5-W02 的 `isExecutionEnabled()` 必须固定 false；不得
   发布 Registry/Resolver Binder Service，不得连接 Runtime/Graph/Room/ToolExecutor 或 production artifact。
8. Probe 只允许 debug + DUMP；release manifest 不得包含。JVM/debug/release 必须覆盖排序/dedup/conflict、最高版本、
   三层状态和 health failure；Android 13 ARM64 probe 未执行时必须保持对应 verified=false。
9. 本包不得触发 Effect、vehicle readback、model/NPU、network、Driver/HAL 或 virtualization。完成只允许提升 pure-Java
   contract 状态；`production_ready` 与 `target_hardware_validated` 必须保持 false。

状态：`tool_registry_contract_defined=true`、`tool_resolver_contract_defined=true`、
`tool_health_dynamic_snapshot_defined=true`、`tool_registry_digest_verified=true`、
`tool_registry_version_conflict_rejected=true`、`tool_resolver_highest_version_deterministic=true`、
`tool_resolver_states_separated=true`、`tool_resolver_unhealthy_no_fallback=true`、`tool_health_fail_closed=true`、
`tool_registry_android13_arm64_verified=false`、`tool_registry_published=false`、`tool_resolver_published=false`、
`tool_registry_runtime_wired=false`、`tool_execution_enabled=false`、`production_tool_registered=false`、
`effect_dispatch_enabled=false`、`vehicle_readback_accessed=false`、`npu_accessed=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## 56. P5-W03 Tool RuleSolver trace

Req IDs：`S2-TOL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`。

1. `ToolRuleSet` 必须 immutable，并显式定义 init、child、conditional、terminal、required-before-exit、
   requires-approval 六类规则。catalog/init/terminal/required/approval family 最多 128 项，child 与 conditional rule 各最多
   256 项；canonical ID、null、duplicate、unknown family、terminal outgoing child 和 terminal/required overlap 必须拒绝。
2. RuleSet digest 必须用 domain-separated、length-framed、排序后的 catalog 和六类规则计算 SHA-256；输入列表顺序、模型
   选择、health、condition runtime value 和执行结果不得影响 digest。
3. `ConditionSnapshot` 只允许最多 128 个 canonical condition ID 与 TRUE/FALSE/UNKNOWN enum。缺失 condition 等同
   UNKNOWN；UNKNOWN 不能满足 TRUE 或 FALSE 条件，不得使用模型文本、HMI 文本或默认值补齐。
4. `ToolRuleSolver.Request` 必须绑定 optional current family、最多 128 个 model-selected family、最多 128 个 completed
   family 和一个 ConditionSnapshot。duplicate/非 canonical 输入必须拒绝；模型可选择 rule catalog 外 family，但交集不得
   因此扩展 allowset。
5. 求解顺序固定为 current membership/terminal gate -> init 或 child allowset -> conditional filter -> terminal required-before-exit
   filter -> model-selected intersection -> P5-W02 USABLE intersection。每个空结果必须返回稳定 failure code 且不 fallback。
6. 只有 RESOLVED + USABLE 的 P5-W02 `Resolution` 可进入最终 Selection。unresolved/unusable 项必须忽略；同 family 两个
   USABLE resolution 必须拒绝，禁止 solver 重新做版本、capability、digest 或 health 选择。
7. requires-approval 只设置 `Selection.isApprovalRequired=true`。P5-W03 没有 trusted approval 输入，故
   `isApprovalGranted()`、Selection/Result `isExecutionEnabled()` 必须固定 false；用户确认、模型输出或 condition 不得授权。
8. P5-W03 不发布 Binder Service、不接 Runtime/Graph/Room/ToolExecutor，不调用模型/NPU/network，不执行 Effect 或车辆
   readback，不访问 Driver/HAL/virtualization。debug probe 只允许 DUMP + debug source，release manifest 必须无该 Activity。
9. JVM/debug/release 必须覆盖 rule bounds/order digest、init/child/condition、空模型交集、terminal prerequisite、terminal stop、
   requires-approval no-grant、unusable exclusion。实体 probe 未运行时 corresponding verified 必须保持 false。

状态：`tool_rule_set_contract_defined=true`、`tool_rule_type_count=6`、`tool_rule_set_digest_verified=true`、
`tool_rule_init_child_conditional_verified=true`、`tool_rule_model_intersection_fail_closed=true`、
`tool_rule_terminal_requirements_verified=true`、`tool_rule_approval_annotation_fail_closed=true`、
`tool_rule_solver_android13_arm64_verified=false`、`tool_rule_solver_published=false`、
`tool_rule_solver_runtime_wired=false`、`tool_approval_authority_available=false`、`tool_execution_enabled=false`、
`production_tool_registered=false`、`effect_dispatch_enabled=false`、`vehicle_readback_accessed=false`、`model_invoked=false`、
`npu_accessed=false`、`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。


## 57. P5-W04 Tool Executor boundary trace

Req IDs：`S2-TOL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`。

1. `ToolInvocationContext` 必须是 immutable schema V1，且只携带 canonical SHA-256 digest、canonical family/capability、
   elapsed-realtime issue/deadline 和 1..16 KiB output limit；不得携带用户/模型文本、Tool payload、车辆值或授权布尔值。
2. 首版执行器只允许 owner=`runtime.builtin` 的显式 registration。family、contract、current application signer 和 artifact
   digest 必须与 build-owned allowlist 完全一致；缺失、重复、冲突或 signer 不一致必须在构造期失败关闭。
3. 执行入口必须消费 P5-W03 `Selection`，且重新绑定 context family/contract/capability。approval-required selection 必须返回
   `APPROVAL_REQUIRED`，不得接受调用方布尔值或模型输出作为 grant。
4. TOKEN_REQUIRED Tool 必须提供 idempotency digest。context deadline window 不得超过 Manifest timeout；clock regression、
   admission 已过期和 completion 到期必须分别稳定拒绝或超时。
5. 输入输出必须复用 P5-W01 exact schema validator。unknown/missing/type/range/aggregate 错误不得进入实现或输出；output
   encoded bytes 超过 context limit 必须丢弃 payload 并返回 `OUTPUT_TOO_LARGE`。
6. cancel/deadline 必须在实现前后检查，并向 built-in 提供 cooperative `checkpoint()` 和 remaining time。P5-W04 不得声称
   可强制终止不合作或阻塞实现；该缺口由 `ISSUE-039` 跟踪。
7. 每次 admission 结果必须生成递增 audit sequence 和 SHA-256 digest。内存 ring 最多 128 条，只保留 invocation/audit/tool
   digest、outcome/failure、elapsed times 和 output byte count；不得保留 input/output 或 exception text。
8. implementation exception 必须收敛为 `IMPLEMENTATION_FAILURE`；不得把堆栈、消息、原始 output 或部分 output 返回调用方。
9. main source 不得引用反射/dynamic class loader、subprocess、network、Room/Binder、Android Car/VHAL、device node、ioctl 或
   Driver/HAL。`isOsVirtualizationEnabled()` 必须固定 false。
10. Runtime Service、Governance Service 与 AgentGraph 不得引用执行器。`isProductionWired()`、production execution、Tool
    registration、Effect/vehicle/model/NPU/hardware 均保持 false。
11. JVM 必须覆盖 canonical/bounds、exact allowlist/signer/artifact、成功、approval/deadline/cancel、input/output/size/failure
    和 bounded audit；debug/release 必须同时编译，release manifest 不得包含 probe。
12. Android 13 ARM64 probe 未实际通过时 `tool_executor_android13_arm64_verified=false`；probe 通过也只证明 debug built-in
    软件路径，不得提升 production 或 hardware 状态。

状态：`tool_executor_contract_defined=true`、`tool_invocation_context_defined=true`、
`built_in_allowlist_enforced=true`、`built_in_signer_artifact_bound=true`、`tool_executor_host_execution_verified=true`、
`tool_executor_deadline_cancel_verified=true`、`tool_executor_output_limit_verified=true`、
`tool_executor_audit_bounded_verified=true`、`tool_executor_android13_arm64_verified=false`、
`tool_executor_runtime_wired=false`、`tool_execution_enabled=false`、`production_tool_execution_enabled=false`、
`production_tool_registered=false`、`os_virtualization_enabled=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## 58. P5-W05 Skill package verifier trace

Req IDs：`S2-TOL-001`、`S2-SAF-001`、`S2-OBS-001`、`FW-U-008`、`DEL-001/004/005`。

1. `SkillSignerPolicy` 必须是 immutable schema V1，最多 32 个 lowercase SHA-256 signer digest。每项必须显式为 ACTIVE、
   RETIRED 或 REVOKED，包含正数 activation artifact epoch；只有 REVOKED 可包含且必须包含更大的 revocation epoch。
2. Signer policy 至少包含一个 ACTIVE signer。unknown、activation 前、retired 和 revoked signer 必须返回不同稳定 code；
   不得因 artifact epoch 早于 revoke 而自动恢复已撤销 signer。
3. `SkillVersionPolicy` 必须是 immutable schema V1，最多 128 个 canonical Skill ID。每项定义 inclusive min/max semantic
   version、minimum artifact epoch 与 explicit rollback flag；当前 Runtime 版本属于同一 canonical `major.minor.patch` 语法。
4. Version evaluation 必须依次检查 Skill 存在、candidate range、artifact epoch、manifest runtime min/max 与防降级。任何失败
   不得 fallback 到旧 policy、旧 signer、旧 artifact 或更低版本；没有 production rollback authority 时当前 policy 使用 false。
5. `SkillPackageManifest` 必须包含 schemaVersion=1、Skill ID/version、declared artifact digest、signer digest、Runtime min/max 和
   1..32 个 canonical capability ID。manifest digest 必须覆盖全部字段并对 capability 输入顺序稳定。
6. `VerificationEvidence` 只允许 manifest、declared manifest digest、measured artifact digest、observed signer digest、正数
   artifact epoch 与 optional highest accepted version；不得携带包字节、证书、用户/模型文本、车辆 payload 或授权布尔值。
7. `SkillArtifactVerifier.verify` 顺序固定为 manifest digest -> artifact digest -> signer evidence equality -> signer policy ->
   version/runtime/epoch/downgrade policy -> exact per-Skill capability allowlist。失败返回稳定 `FailureCode` 且 verified package
   必须为空。
8. 通过结果只能包含 Skill/version、artifact/signer/manifest/policy digest 与 immutable capability；`isDynamicLoadAllowed()`、
   `isExecutionAllowed()` 必须固定 false，不得输出原始 signing material 或 artifact 内容。
9. P5-W05 不读取 APK/JAR/dex/certificate、PackageManager/keystore/TEE/vendor trust store，不做 signature chain verification，
   不使用 file/network/reflection/class loader/subprocess/serialization/Binder/Room/Android Car/VHAL/device node/ioctl。
10. Runtime Service、Governance Service、AgentGraph、P5-W04 executor 不得引用 verifier。production policy/evidence publisher、
    atomic epoch、lifecycle store、dynamic load、Tool/Skill execution、Effect/Vehicle/NPU/Driver-HAL 均保持未发布。
11. JVM 必须覆盖 policy bounds/order digest、正向 hash/signer/manifest/runtime/capability、manifest/artifact/signer mismatch、
    retired/revoked、version/runtime/epoch/downgrade/capability fail-closed 和 immutable no-load/no-execute result。
12. Android 13 ARM64 probe 未实际通过时 `skill_package_verifier_android13_arm64_verified=false`；未来通过也只证明纯 Java
    static verifier 在 API 33 ARM64 可运行，不证明可信 signer evidence、package signature chain 或 production activation。

状态：`skill_artifact_verifier_contract_defined=true`、`skill_signer_policy_contract_defined=true`、
`skill_version_policy_contract_defined=true`、`skill_artifact_hash_verified=true`、`skill_manifest_digest_verified=true`、
`skill_signer_policy_verified=true`、`skill_runtime_version_verified=true`、`skill_capability_policy_verified=true`、
`skill_revocation_downgrade_fail_closed=true`、`skill_package_verifier_android13_arm64_verified=false`、
`trusted_skill_evidence_source_configured=false`、`package_signature_cryptographically_verified=false`、
`dynamic_skill_loading_enabled=false`、`skill_execution_enabled=false`、`skill_package_verifier_runtime_wired=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## 59. P5-W06 WorkingMemoryStore trace

Req IDs：`S2-MEM-001`、`S2-SAF-001`、`S2-OBS-001`、`FW-U-001/006/007`、`NV-F-001`、
`NV-G-005/006/007`、`DEL-001/004/005`。

1. `WorkingMemoryStore` 必须是 Android-independent main-source schema V1，并以 lowercase SHA-256 owner fingerprint、canonical
   Session ID 和 item ID 形成唯一隔离键；任何 owner 不得读取、删除或终止另一 owner 的 Session。
2. `PutRequest.fromRuntimePolicy` 必须防御性复制非空 opaque bytes，并显式携带 schema ID、positive token count 与 positive TTL；
   不接受 user identity、vehicle signal、approval grant 或硬件句柄。
3. TTL 必须使用 injected non-negative elapsed-realtime clock；到期判定为 `expiresAt <= now`，过期操作必须从 quota 中原子释放
   item/byte/token，并覆零 store retained byte array。
4. `Limits` 必须同时约束 active Session、每 Session item、每 item/Session bytes、每 item/Session tokens、read page、TTL 与
   terminal Session tombstone；无界或内部不一致配置必须拒绝。
5. put 必须先计算 replacement 后的 projected item/byte/token，再做 mutation；超限分别返回稳定 outcome，不能 silent evict、
   truncate、summarize、跨 Session 借预算或调用模型。
6. exact request fingerprint replay 不得刷新 TTL 或重复计费；同 item changed request 可在全部 projected limits 通过后原子替换，
   替换时旧 retained bytes 必须覆零。
7. read 必须返回 immutable list 与 per-call payload copy；remove 必须只影响 owner/session/item exact match，并在释放前覆零 retained
   payload。main source 不得记录 payload、digest、owner 或 Session 内容。
8. `terminateSessionOwned` 必须清空该 owner/session 的全部 item/byte/token、覆零 retained bytes、返回清理计数并创建 bounded
   terminal tombstone；tombstone 保留期内 late put/remove 必须失败关闭，repeated terminal 必须幂等。
9. P5-W06 允许 process-local raw opaque bytes，但不允许 Room/file/SharedPreferences/Binder persistence、Runtime/Graph wiring、
   model context publication、network、Effect、Vehicle/VHAL、NPU 或 Driver/HAL。
10. token count 是受信上游提供的预算元数据；在 production tokenizer/version/digest 未绑定前必须保持
    `working_memory_tokenizer_verified=false`，不得把 host test token count 描述为真实模型 token evidence。
11. JVM 必须覆盖 owner/session isolation、防御性 copy、immutable read、TTL、item/byte/token/Session limits、replay/replace/remove、
    terminal cleanup/tombstone bound、zeroization count 与 malformed input。debug/release 必须编译同一 main source，release 无 probe。
12. Android 13 ARM64 probe 未实际通过时 `working_memory_android13_arm64_verified=false`；未来通过只证明 process-local contract
    在 API 33 ARM64 可运行，不证明 Runtime publication、durable/encrypted storage、model context 或 production/hardware readiness。

状态：`working_memory_store_defined=true`、`working_memory_session_scope_verified=true`、
`working_memory_ttl_verified=true`、`working_memory_item_limit_verified=true`、`working_memory_byte_limit_verified=true`、
`working_memory_token_limit_verified=true`、`working_memory_terminal_cleanup_verified=true`、
`working_memory_payload_zeroized_on_cleanup=true`、`working_memory_android13_arm64_verified=false`、
`working_memory_process_local=true`、`working_memory_persistence_wired=false`、`working_memory_runtime_wired=false`、
`working_memory_model_context_published=false`、`working_memory_tokenizer_verified=false`、
`working_memory_content_logged=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## 60. P5-W07 ProfileMemoryStore trace

本增量映射 `S2-MEM-001`、`S2-SAF-001`、`S2-OBS-001`、`FW-U-001/006/007`、`NV-F-001`、
`NV-G-005/006/007`、`DEL-001/004/005`：

1. Profile Memory 必须以受信 owner fingerprint 与 `USER_GLOBAL/DRIVER/FRONT_PASSENGER/REAR_LEFT/REAR_RIGHT`
   scope 定位，调用方不能用任意字符串创建隐藏 seat/user namespace。
2. 可保存字段必须来自 build-owned `Field` enum 与 `FieldPolicy` allowlist。首版只允许 cabin temperature、seat recline、
   seat heat/vent、media volume、avoid-highway 与 language tag；字段类型、范围、seat/user scope 必须精确匹配。
3. update/read 必须携带结构完整且当前有效的 `ConsentEvidence`，并由独立 `ConsentAuthority` 复验 active/revoked 状态。
   缺失、过期、owner/seat/field 不匹配或 authority 异常必须稳定失败关闭。
4. delete 不得依赖仍有效的 consent，以保证用户撤回同意后仍可清除数据；但必须携带独立 DELETE authorization。
   export 必须同时满足 active consent 与独立 EXPORT authorization，且只返回请求 allowlist 中已有记录。
5. `EncryptionOwner` gate 必须在任何 value seal/open 前提供 owner ID、key alias digest、generation、at-rest availability 与
   key lifecycle ready。gate 缺失或异常不得回退 plaintext、默认 key、SharedPreferences 或未加密文件。
6. store 只保留 `SealedPayload`，update/delete/expiry 必须覆零其 retained ciphertext。plaintext 编解码只存在于有界 transient
   byte array，并在 seal/open 后 finally 覆零；API 返回 typed value 后的 JVM object 生命周期不冒充 secure erase。
7. retention 最大 30 天；全局 record、每 owner record/sealed bytes、单 sealed payload 与 export page 都必须有绝对上限。
   无界配置、超限写入或部分 export 不得 silent evict/truncate/fallback。
8. P5-W07 main source 只提供 `createForContractTest`；不得提供 production factory、Android Keystore/TEE 实现、Room/file
   repository、Binder Service 或 Runtime/Graph/model composition。
9. debug/test XOR owner 仅证明 gate、owner metadata binding 与 ciphertext lifecycle，不是密码学、硬件密钥、加密静态存储或
   production signer evidence。所有 production readiness 与 durable storage marker 必须保持 false。
10. main source 不得记录 owner、consent/authorization ID、字段值、ciphertext 或 digest。debug probe 只输出固定 nonce、
    boolean 和 count，release manifest 不得包含 probe。
11. JVM 必须覆盖 gate/consent fail-closed、field/scope/value、read/update isolation、sealed replacement wipe、consent revoke 后
    delete、bounded immutable export、retention/capacity/malformed input；debug/release 必须编译同一 main source。
12. Android 13 ARM64 probe 未实际通过时 `profile_memory_android13_arm64_verified=false`；未来通过也只证明 contract-test path
    在 API 33 ARM64 可运行，不证明 durable encrypted repository、production authority、Runtime wiring 或目标硬件资格。

状态：`profile_memory_store_defined=true`、`profile_memory_explicit_consent_verified=true`、
`profile_memory_field_allowlist_verified=true`、`profile_memory_user_seat_scope_verified=true`、
`profile_memory_read_update_verified=true`、`profile_memory_delete_verified=true`、`profile_memory_export_verified=true`、
`profile_memory_consent_revocation_fail_closed=true`、`profile_memory_encryption_owner_gate_verified=true`、
`profile_memory_sealed_payload_zeroized=true`、`profile_memory_android13_arm64_verified=false`、
`profile_memory_process_local=true`、`profile_memory_durable_storage_wired=false`、
`profile_memory_production_encryption_owner_configured=false`、`profile_memory_consent_authority_production_wired=false`、
`profile_memory_runtime_wired=false`、`profile_memory_content_logged=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## 61. P5-W08 EpisodicMemoryStore trace

本增量映射 `S2-MEM-001`、`S2-SAF-001`、`S2-OBS-001`、`FW-U-001/006/007`、`NV-F-001`、
`NV-G-005/006/007`、`DEL-001/004/005`：

1. Episodic Memory 必须以 lowercase SHA-256 owner fingerprint 与 canonical episode ID 形成唯一键；read/erase 不能跨 owner，
   同 episode exact request replay 必须幂等，内容变化必须返回 conflict 而不是覆盖历史。
2. `RecordRequest.fromScenarioResult` 只允许 `ScenarioReference`、固定 `TriggerKind/ResultKind/OutcomeCode`、bounded action count、
   elapsed start/finish 与 retention；不得提供 raw signal array、byte payload、user/model text、自由文本或硬件句柄参数。
3. `ScenarioReference` 必须绑定 canonical scenario ID 与 lowercase SHA-256 catalog digest，并由 injected
   `ScenarioCatalogAuthority` 复验 build-owned；unknown/exception 必须失败关闭。
4. store 前必须校验 owner/episode-bound、当前有效的 `StoragePolicyEvidence`，并调用独立 `StoragePolicyAuthority`；证据缺失、
   过期、不匹配或 authority 异常必须返回稳定拒绝，不得 fallback 到默认同意。
5. read 必须使用 owner/valid-window 精确绑定的 `ReadEvidence` 并调用独立 `ReadAuthority`；denied/exception 必须返回空 page
   和统一拒绝码，不得泄露 owner 是否存在或记录数量。
6. 单 episode erase 与 owner erase 必须使用 operation/owner/episode/valid-window 精确绑定的 `EraseEvidence` 并调用独立
   `EraseAuthority`；authorization denied 不得泄露目标是否存在，owner erase 不得影响其他 owner。
7. retention 最大 30 天，episode duration 最大 24 小时；全局 record、每 owner record 与 read page 都必须有绝对上限。
   运行时 `Limits` 只能收窄上限且内部一致，容量超限不得 silent evict、truncate、summarize 或调用模型。
8. expiry 必须使用 injected non-negative elapsed-realtime clock，判定为 `expiresAt <= now`；到期记录在任何 read/store/erase/
   snapshot 前确定性移除，并只增加 count，不输出 episode 内容。
9. P5-W08 main source 只提供 `createForContractTest`；不得提供 production factory、Room/file/SharedPreferences、Binder Service、
   Runtime/Graph/model context、network、Effect、Vehicle/VHAL、NPU 或 Driver/HAL composition。
10. main source 不得记录 owner、episode/scenario、policy/read/erase evidence、result 或 digest。debug probe 只输出固定 nonce、boolean/count，
   release manifest 不得包含 probe。
11. JVM 必须覆盖 summary-only schema、owner isolation、catalog/policy fail-closed、replay/conflict、retention/duration、global/owner
    capacity、read/erase authorization 与 malformed input；debug/release 必须编译同一 main source。
12. `MemoryRuntimeReadinessSnapshot` 的 durable encrypted storage、consent、trusted retention clock、repository 与 Runtime blockers
    必须保持不变；contract-test store 不得提升 production readiness。
13. Android 13 ARM64 probe 未实际通过时 `episodic_memory_android13_arm64_verified=false`；未来通过也只证明 process-local
    contract 在 API 33 ARM64 可运行，不证明 durable repository、production authority、model publication 或目标硬件资格。

状态：`episodic_memory_store_defined=true`、`episodic_memory_summary_result_only_verified=true`、
`episodic_memory_owner_isolation_verified=true`、`episodic_memory_policy_fail_closed=true`、
`episodic_memory_read_fail_closed=true`、
`episodic_memory_retention_verified=true`、`episodic_memory_capacity_verified=true`、`episodic_memory_erase_verified=true`、
`episodic_memory_erase_fail_closed=true`、`episodic_memory_android13_arm64_verified=false`、
`episodic_memory_process_local=true`、`episodic_memory_raw_continuous_signal_stored=false`、
`episodic_memory_arbitrary_payload_stored=false`、`episodic_memory_persistence_wired=false`、
`episodic_memory_runtime_wired=false`、`episodic_memory_model_context_published=false`、
`episodic_memory_production_policy_authority_wired=false`、`episodic_memory_production_read_authority_wired=false`、
`episodic_memory_production_erase_authority_wired=false`、
`episodic_memory_content_logged=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## 62. P5-W09 ContextBudgetManager trace

本增量映射 `S2-MEM-001`、`S2-MDL-001`、`S2-SAF-001`、`S2-OBS-001`、`FW-U-001/006/007`、
`NV-F-001`、`NV-G-005/006/007`、`DEL-001/004/005`：

1. `ContextBudgetManager` 必须是 Android-independent schema V1，只提供 `createForContractTest`；不得由
   `CentralBrainRuntimeService` 或 `AgentGraphRuntime` 实例化。
2. category 固定为 SYSTEM、CONTEXT、PROFILE、EPISODE、HISTORY，不允许调用方创建任意 namespace；排序固定为 category
   enum、priority 降序、canonical ID 升序，输入排列不得改变结果。
3. `ContextDescriptor.fromTrustedMetadata` 只能接收 category、canonical ID、positive bounded token/byte size、required、
   summaryAllowed 与 0..100 priority；不得接收 raw text、byte payload、prompt、conversation、model output 或 vehicle signal。
4. `BudgetPolicy` 必须同时限制 global token、global byte、item count 和每个 category 的 token/byte envelope；所有配置只能在
   absolute ceiling 内收窄，不允许 unbounded、负值或缺失 category。
5. required descriptor 必须先于 optional admission。任何 required item 不能完整容纳时，必须返回
   `REQUIRED_BUDGET_EXCEEDED`、空 decision list 和零分配，不得 partial plan、silent drop、truncate、summarize 或 model fallback。
6. optional descriptor 完整容纳时返回 INCLUDE；超限且仍有双预算时依据 summaryAllowed 返回
   `SUMMARIZE_TO_BUDGET` 或 `TRUNCATE_TO_BUDGET`；任一维度无剩余时返回 DROP。
7. `SUMMARIZE_TO_BUDGET/TRUNCATE_TO_BUDGET` 只携带目标 token/byte 数，不得在本模块执行内容变换；
   `isSummaryGenerated=false`、`isContentTruncated=false` 必须保持真实。
8. category envelope 不得向其他 category 借用；global envelope 同时限制总量。重复 ID、null item、超限 item/count 和 malformed
   metadata 必须在分配前失败，不得产生 partial mutation。
9. token 与 byte 数均为受信上游 metadata，不是 tokenizer evidence。在 production tokenizer family/version/digest、计数失败策略、
   summary/truncation executor 与 budget authority 发布前，相关 wired flag 必须为 false。
10. main source 不得记录 descriptor ID、size 或内容，不得依赖 Android framework、Binder、Room/file、network、ModelProvider、
    NPU、Android Car/VHAL、Effect 或 Driver/HAL。
11. JVM 必须覆盖五类顺序/input permutation、required no-partial failure、三种 over-budget directive、category/global 双包络、
    malformed/duplicate metadata、immutable output 和 production boundary。debug/release 必须编译同一 main source，release 无 probe。
12. Android 13 ARM64 probe 未实际通过时 `context_budget_android13_arm64_verified=false`；未来通过只证明 metadata allocator
    在 API 33 ARM64 可运行，不证明真实 tokenizer、summary quality、model integration、NPU 性能或目标硬件资格。

状态：`context_budget_manager_defined=true`、`context_budget_category_allocation_verified=true`、
`context_budget_dual_limit_verified=true`、`context_budget_deterministic_overflow_verified=true`、
`context_budget_required_fail_closed=true`、`context_budget_android13_arm64_verified=false`、
`context_budget_decision_only=true`、`context_budget_text_payload_accepted=false`、
`context_budget_tokenizer_wired=false`、`context_budget_summarizer_wired=false`、
`context_budget_production_authority_wired=false`、`context_budget_runtime_wired=false`、
`context_budget_content_logged=false`、`model_invoked=false`、`npu_accessed=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。


## 63. P5-W10 Memory consent HMI/API trace

本增量映射 `S2-MEM-001`、`S2-UX-003`、`S2-SAF-001`、`S2-OBS-001`、`FW-U-001/006/007`、
`NV-F-001`、`NV-G-005/006/007`、`DEL-001/004/005`：

1. 用户可见来源只能是固定 `WORKING_SESSION`、`PROFILE_PREFERENCE`、`EPISODIC_SCENARIO`，每项必须展示固定 purpose、
   retention 和 enabled 状态，不得返回原始记忆、用户/模型文本、车辆 payload 或任意扩展 map。
2. `WORKING_SESSION` 是会话连续性来源并始终 session-scoped；“关闭记忆”只关闭 Profile/Episodic retained capture，不能破坏
   当前 Session 的短期控制状态。
3. Profile 保留策略为 user clear，Episode 最大 30 天；这些是 UI contract，不代表 production repository 已执行 retention。
4. HMI 在 PARKED 可执行 retained-memory enable/disable 与 profile preference clear；MOVING 和 UNKNOWN 都必须仅显示来源摘要，
   复杂管理在 authority 调用前失败关闭。
5. mutation 必须绑定 lowercase SHA-256 owner fingerprint、canonical request ID、operation、目标值与 elapsed validity window；
   owner/operation/target/window 不匹配必须返回稳定拒绝。
6. request ID exact replay 必须幂等；相同 ID 不同 operation/target 必须返回 conflict，不能再次授权或改变 projection。
7. `MutationAuthority` 异常、null 或 deny 必须失败关闭；HMI checkbox、模型输出或调用方布尔值不能自行成为 production consent。
8. `clearProfilePreferences` 当前只清除 process-local HMI projection；`isRepositoryMutationApplied` 必须恒为 false，不能显示为
   production encrypted repository 已擦除。
9. MOVING/UNKNOWN snapshot 不得披露 preference presence；其 storage presence 必须为 `NOT_DISCLOSED`。
10. debug `MemoryConsentHmiActivity` 必须在 1920x1080 边界内使用响应式右侧半透明面板，并支持交互模式与 automated boolean probe；
    release manifest 不得包含该 Activity。
11. main controller 只使用 Java collections/regex；不得接 Binder、Room/file、SharedPreferences、network、ModelProvider、
    Android Car/VHAL、NPU、Driver/HAL 或 logging。
12. JVM 必须覆盖固定来源、驻车关闭/清除、moving/unknown restriction、evidence/authority fail-closed、replay/conflict、immutable
    output 与 production false boundaries；debug/release 编译同一 main source。
13. 未在 Android 13 ARM64 实体运行 automated probe 前 `memory_consent_android13_arm64_verified=false`；即使通过也只证明
    process-local HMI/API，不证明 production authority、repository erase、model publication 或目标硬件资格。

状态：`memory_consent_controller_defined=true`、`memory_consent_source_visibility_verified=true`、
`memory_consent_disable_verified=true`、`memory_consent_preference_clear_verified=true`、
`memory_consent_moving_restriction_verified=true`、`memory_consent_android13_arm64_verified=false`、
`memory_consent_hmi_projection_only=true`、`memory_consent_repository_mutation_wired=false`、
`memory_consent_production_authority_wired=false`、`memory_consent_runtime_wired=false`、
`memory_consent_model_context_published=false`、`memory_consent_content_logged=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## 64. P6-W01 EventBroker interface/in-process implementation trace

本增量映射 `S2-EVT-001`、`S2-SAF-001`、`S2-OBS-001`、`FW-U-001/006/007`、`NV-F-001`、
`NV-G-004/005/006/007`、`DEL-001/004/005`：

1. `EventBroker` 必须固定 schema V1，并只发布 Task state、Policy decision、Model health 三个 build-owned generic
   `Topic<T>`；每个 Topic 必须绑定固定 topic ID、schema ID、payload class 和 event-kind allowlist。
2. payload 只能包含 lowercase SHA-256 subject digest、固定 enum state/decision/health 和 canonical payload digest；不得包含
   任意 byte/map、用户/模型文本、车辆信号、位置、prompt 或可执行对象。
3. topic/payload class 或 filter kind 不匹配必须在 mutation 前拒绝；调用方不能注册动态 topic、schema 或 predicate code。
4. cursor 必须按 topic 从 1 单调递增。publish 必须先 append 到 bounded retention，再调用 consumer；callback 失败不得回滚
   event，必须关闭对应 subscription 并保留 cursor replay 路径。
5. retention、global/per-owner subscription、replay page 和 replay/cancel tombstone 必须有绝对上限；不得 silent unbounded
   growth。P6-W02 之前不实现异步队列或宣称 backpressure/QoS 完成。
6. replay 必须显式返回 `FUTURE_CURSOR` 和 `CURSOR_GAP`；gap 不得返回看似连续的 partial page。filter 只允许固定 event kind
   与最多 16 个 subject digest，page 必须 immutable 并携带 earliest/latest/next/hasMore。
7. subscribe 必须绑定 owner fingerprint、client subscription ID、topic、after-cursor 与 filter digest；exact replay 返回同一
   handle，不同请求复用 client ID 返回 conflict。owner 不能取消或探测其他 owner 的 handle。
8. publish request ID 必须 owner-scoped、exact replay 幂等；同 request ID 不同 topic/payload 返回 conflict，不能重复 append
   或 callback。
9. publish/subscribe/replay/cancel 均必须携带 operation/topic/owner/identity digest/policy digest/elapsed validity 证据，并经独立
   `AccessAuthority`；mismatch、expired、deny、null 或 exception 必须稳定失败关闭且不改变 broker state。
10. `InProcessDurableEventBroker` 的 required 类名不得被解释为 process-death durability。当前 persistence flag 必须为 false，
    且不得与 `DurableEventCursorRepository`、Room、file 或 SharedPreferences 接线。
11. main source 不得接 Android Binder、DDS/SOME-IP/network、Runtime/Graph/Effect、Vehicle/VHAL、Model/NPU、Driver/HAL、
    dynamic class loading、thread pool 或 subprocess。production broker published/runtime wired/hardware accessed 必须为 false。
12. JVM 必须覆盖 typed mismatch、append-before-notify、bounded replay/filter/gap/future、identity/policy fail-closed、
    subscription replay/conflict/owner/callback failure/cancel 和 production false boundaries。
13. debug `EventBrokerProbeActivity` 只允许 DUMP-protected、NoDisplay、nonce-bound boolean probe；release manifest 不得包含。
14. Android 13 ARM64 probe 已通过，`event_broker_android13_arm64_verified=true`；该证据只证明 process-local
    contract，不证明 durable repository、DDS、中间件 QoS、production identity/policy 或目标硬件资格。

状态：`event_broker_interface_defined=true`、`event_broker_typed_topics_verified=true`、
`event_broker_append_before_notify_verified=true`、`event_broker_bounded_replay_filter_verified=true`、
`event_broker_identity_policy_verified=true`、`event_broker_subscription_lifecycle_verified=true`、
`event_broker_android13_arm64_verified=true`、`event_broker_process_local=true`、
`event_broker_durable_persistence_wired=false`、`event_broker_dds_transport_wired=false`、
`event_broker_production_published=false`、`event_broker_runtime_wired=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## 65. P6-W02 Backpressure/QoS trace

本增量映射 `S2-EVT-001`、`NV-G-004`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`：

1. `EventDeliveryQoS` 必须固定 schema V1 和 `DROP_OLD/COALESCE/REJECT/DISCONNECT` 四种 overflow policy；queue capacity
   不超过 256，单次 delivery batch 不超过 64，deadline window 不超过 300 秒。
2. 每个 `InProcessEventBackpressureQueue` 只能绑定一个 owner/topic subscription handle；wrong owner drain/snapshot 不得返回队列
   内容，消费者失败不能影响其他 subscription queue。
3. delivery metadata 只允许 immutable EventRecord、canonical request ID、`STANDARD/CRITICAL_ACTION_OBSERVATION`、固定 priority、
   elapsed deadline 和可选 SHA-256 coalesce key；不得携带原始用户/模型/车辆 payload。
4. `DROP_OLD` 只能移除非关键且 priority 不高于 incoming 的最低优先级 event，并返回 displaced cursor/count；队列全为关键或
   更高 priority 时必须显式 reject。
5. `COALESCE` 只能替换相同 key、非关键且 priority 不高于 incoming 的 queued event；关键事件不能携带 coalesce key，也不能被
   coalesce。
6. `REJECT` 必须返回 queue-full 和 replay-after cursor；`DISCONNECT` 必须清空本队列、记录 discarded count/reason，并返回
   `DISCONNECTED_REPLAY_REQUIRED`，不能返回成功。
7. 关键 Action Observation 在满载时只允许入队、显式 reject 或显式 disconnect/replay；绝不能 drop-old/coalesce。关键 event
   drain 前过期必须断开并要求 replay，不得静默跳过。
8. 标准 event 过期可从 process-local queue 移除，但结果必须为 `EXPIRED_REPLAY_REQUIRED` 或
   `DRAINED_WITH_EXPIRED`，snapshot 必须保持 replay-required。
9. request ID exact replay 不重复入队；同 ID 不同 event/QoS 返回 conflict。cursor 不得倒退或重复接受；callback 异常必须保留
   head event 供重试并增加 bounded metadata counter。
10. priority 只用于有界压力 admission，正常 drain 保持队列顺序；P6-W02 不宣称跨 topic 全局 scheduler、实时 deadline 或
    durable ACK。
11. `createForContractTest` 是唯一 factory；queue 不得接 P6-W01 Broker、Runtime/Graph、Binder、Room/file、DDS/SOME-IP、
    Vehicle/VHAL、Model/NPU、Driver/HAL、thread/executor 或 network。
12. JVM 必须覆盖四策略、priority displacement、critical no-silent-drop、deadline、idempotency/conflict、owner、callback failure、
    consumer isolation 与全部 production false boundary；debug/release 编译同一 main source。
13. Android 13 ARM64 probe 已通过，`event_qos_android13_arm64_verified=true`；该证据只证明 process-local Java queue，
    不证明 production middleware、durability、目标负载或硬件资格。

状态：`event_qos_contract_defined=true`、`event_qos_policies_verified=true`、
`event_qos_critical_no_silent_drop_verified=true`、`event_qos_deadline_priority_verified=true`、
`event_qos_consumer_isolation_verified=true`、`event_qos_android13_arm64_verified=true`、
`event_qos_process_local=true`、`event_qos_broker_wired=false`、`event_qos_durable_persistence_wired=false`、
`event_qos_production_middleware_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。

## 70. P7-W01 ModelRequest/Result v2 trace

本增量映射 `S2-MDL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`：

1. 合同 schema 必须固定为 V2，并与既有 `ModelProvider.InferenceRequest/TerminalResult` 并存；P7-W01 不修改旧接口或接线。
2. `ModelRequest` 必须显式包含 purpose、privacyClass、latencyBudget、tokenBudget、requiredCapability、fallbackPolicy、traceId。
3. request ID 只能使用 bounded canonical identifier；trace/input 必须是 lowercase SHA-256，不得传入 prompt、用户/模型文本、byte
   payload、位置或车辆信号。
4. latency 必须位于 1..120000 ms；input/output/total token budget 必须有绝对上限且相互一致，非法值在构造期失败关闭。
5. privacy/fallback 必须在 routing 前失败关闭：SENSITIVE/RESTRICTED 禁止 policy-controlled fallback，RESTRICTED 只允许 no-fallback。
6. request fingerprint 必须覆盖全部 routing/budget/trace/input 字段，exact replay 得到相同 fingerprint，任一关键字段变化必须改变。
7. `ModelResult` 必须复制并绑定 request ID、fingerprint、trace；provider ID canonical，token usage 不得超过请求三项预算。
8. success 必须有非空 output digest、正 output token 与固定 accepted detail；terminal 状态不得携带 output，并与固定 detail 匹配。
9. Result 不得承载或授予 action authorization、Effect dispatch、vendor property、shell/device node 或 arbitrary Tool ID。
10. v2 contract 不得接 Runtime/Governance Service、Provider registry/router、network、NPU、Vehicle、Binder 或 Driver/HAL。
11. debug probe 只能输出 nonce 与 boolean marker，并由 DUMP permission 保护；release manifest 不得注册 probe。
12. Android 13 ARM64 probe 已通过，`model_contract_v2_android13_arm64_verified=true`；该证据不替代真实模型或生产证据。

状态：`model_contract_v2_defined=true`、`model_request_v2_fields_verified=true`、
`model_result_v2_binding_verified=true`、`model_privacy_fallback_fail_closed=true`、
`model_raw_content_accepted=false`、`model_provider_registry_wired=false`、`model_policy_router_wired=false`、
`model_contract_v2_android13_arm64_verified=true`、`model_invoked=false`、`npu_accessed=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。tracking：`DEV-078`、`ISSUE-024/044`。

## 71. P7-W02 ModelProviderRegistry/health trace

本增量映射 `S2-MDL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`：

1. Registry 必须只有四个 build-owned provider：deterministic Android test、Android local development、vendor NPU placeholder、
   cloud placeholder；不得支持动态注册。
2. Descriptor 必须冻结 provider kind、health source、required capability set、contract-test/development/production implementation/
   production eligibility、network dependency 与 hardware expectation。
3. test availability、development availability 与 production readiness 必须独立计数；contract-test provider 永不继承 production 资格。
4. 当前 local development、vendor NPU 和 cloud implementation 均不可用，production ready count 必须为 0。
5. Health report 必须绑定 provider/source、positive revision、monotonic observed/valid-until 和 SHA-256 evidence；validity 不超过 60 秒。
6. unknown provider、source mismatch、future、expired、lower revision 和 same-revision conflict 必须在 mutation 前显式失败关闭。
7. exact report replay 返回 REPLAYED；higher revision 才能替换。snapshot 超过 valid-until 后必须投影 UNKNOWN/STALE。
8. HEALTHY 只代表 fresh health metadata；若 implementation/eligibility 未发布，placeholder 不得变为 available、production ready 或 routable。
9. capability set 与 snapshot list 必须 immutable；catalog 按 provider ID 排序并有 deterministic digest。
10. P7-W02 不得提供 route/infer API，不得构造 Provider instance，不得接 Runtime/Governance Service 或 P7-W03 Router。
11. main source 不得访问 network、NPU、vehicle、Binder、Driver/HAL 或硬件；cloud network-required 只是 descriptor 元数据。
12. debug probe 只输出 nonce、boolean 和 count；release manifest 不得注册。实体 probe 已通过但只证明 metadata contract。

状态：`model_provider_registry_defined=true`、`model_provider_count=5`、
`model_provider_health_freshness_verified=true`、`model_provider_health_replay_verified=true`、
`model_provider_availability_separation_verified=true`、`model_provider_placeholder_fail_closed=true`、
`model_contract_test_available_count=1`、`model_development_available_count=1`、`model_production_ready_count=0`、
`model_provider_registry_android13_arm64_verified=true`、`model_provider_registry_runtime_wired=false`、
`model_policy_router_wired=false`、`model_invoked=false`、`network_accessed=false`、`npu_accessed=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。tracking：`DEV-079`、`ISSUE-024`。

## 72. P7-W03 PolicyAwareModelRouter trace

本增量映射 `S2-MDL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`：

1. Router 必须只消费 `ModelContractV2.ModelRequest`、`ModelProviderRegistry.RegistrySnapshot`、caller-owned
   `PolicySnapshot` 和 caller-supplied elapsed time；不得读取 raw prompt/context/result。
2. Policy snapshot 必须显式冻结 route mode、network policy/state、thermal state、remaining request/token quota、revision、
   observed/valid-until 与 evidence digest；validity 最长 60 秒。
3. future 或 stale policy snapshot 必须在 Provider evaluation 前失败关闭，且不得生成 primary/fallback selection。
4. CONTRACT_TEST、DEVELOPMENT、PRODUCTION 必须分别使用 registry 的 test/development/production readiness，不得交叉继承。
5. 每个 Provider evaluation 必须独立记录 health freshness/state、capability、privacy、network、thermal、latency 和 quota 拒绝原因。
6. SENSITIVE/RESTRICTED 不得选择 cloud；offline policy 或 network unavailable 不得选择 network-required Provider。
7. hardware/local route 在 UNKNOWN/HOT/CRITICAL thermal state 下必须失败关闭；最小 latency 超过 request budget 时必须拒绝。
8. remaining request 为 0 或 request maximum total token 超过 remaining token 时必须拒绝，Router 不得自行扣减或伪造 quota。
9. `NO_FALLBACK` 最多选择 1 个 Provider；其余允许策略最多选择 2 个 Provider，fallback list 最多 1 项。
10. Decision 必须绑定 request ID/fingerprint/trace、policy snapshot digest、registry catalog digest 和全部 candidate digest。
11. Route Decision 不得授予 action authorization、请求 Effect dispatch、实例化/调用 Provider、执行模型或访问 network/NPU/hardware。
12. Router 不得接 Runtime/Governance Service、Graph、Effect、Vehicle、Binder、Driver/HAL；debug probe 只输出 nonce/boolean。
13. release manifest 不得注册 probe；实体 Android 13 ARM64 probe 已通过但不构成 production route authority。

状态：`model_policy_router_defined=true`、`model_policy_router_privacy_network_thermal_verified=true`、
`model_policy_router_latency_capability_quota_verified=true`、`model_policy_router_fallback_bounded=true`、
`model_policy_router_no_action_authority=true`、`model_policy_router_android13_arm64_verified=true`、
`model_policy_router_runtime_wired=false`、`provider_invoked=false`、`model_invoked=false`、`network_accessed=false`、
`npu_accessed=false`、`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。tracking：`DEV-080`、`ISSUE-024`。

## 73. P7-W04 LocalModelProvider trace

本增量映射 `S2-MDL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`：

1. 可执行 Local Provider 必须只存在于 Android `debug` source set；release source、release manifest 和 production Service 不得包含实现或 probe。
2. Provider ID 必须固定为 `android.local.development`，assurance=`DEBUG_ONLY`、fallback=`NEVER`、hardware-backed=false、production-eligible=false。
3. fixed registry 可以发布 development availability，但 production implementation/eligibility/readiness 必须保持 false，HEALTHY 不得改变这些标志。
4. 构造必须使用显式 `createForDevelopment` 和 injected `LocalInferenceEngine`；Provider 不得发现、下载、加载任意 engine/plugin。
5. engine 输入只允许 warmed `ModelSpec`、digest-only `InferenceRequest` 和 cooperative `CancellationSignal`；不得自行读取 raw prompt/context。
6. deadline 必须使用 caller-injected monotonic elapsed clock，并在 admission、engine 后、每个 stream chunk 前和 terminal 前检查。
7. cancel 必须 owner request ID 精确命中，返回 typed acknowledgement；terminal/unknown request 必须分别返回 `ALREADY_TERMINAL`/`NOT_FOUND`。
8. stream 必须同时限制 chunk count、single chunk bytes 和 total bytes；绝对上限为 32 chunks、64 KiB/chunk、256 KiB total。
9. caller 可以配置更小 `StreamLimits`；超过任一上限必须在输出 callback 前以 `LOCAL_OUTPUT_LIMIT_EXCEEDED` 失败关闭。
10. non-streaming 输出必须合并为单 chunk，并继续受 `ModelProvider.MAX_STREAM_CHUNK_BYTES` 限制。
11. executor、engine、observer、deadline、cancel、close 必须形成单一 terminal；metrics 不得使 completed/cancelled/failure 总数超过 accepted。
12. terminal history 只保留 bounded request/result digest metadata，不得记录或持久化 raw output；probe 只输出 nonce/boolean。
13. 本包不得接 Runtime/Governance、Graph、Effect、Vehicle、network、NPU、Driver/HAL，也不得成为 Vendor NPU 的隐式 fallback。
14. 实体 Android 13 ARM64 probe 已通过，`local_model_provider_android13_arm64_verified=true`；不得声明 production inference。

状态：`local_model_provider_verified=true`、`local_model_provider_deadline_verified=true`、
`local_model_provider_cancel_verified=true`、`local_model_provider_stream_limit_verified=true`、
`local_model_provider_debug_only=true`、`local_model_provider_release_source_absent=true`、
`local_model_provider_runtime_wired=false`、`local_model_provider_vendor_npu_fallback_enabled=false`、
`local_model_provider_android13_arm64_verified=true`、`production_inference_enabled=false`、`network_accessed=false`、
`npu_accessed=false`、`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。tracking：`DEV-081`、`ISSUE-024`。

## 74. P7-W05 structured model output trace

本增量映射 `S2-MDL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`：

1. Prompt/output identity 必须固定为 `centralbrain.model.scenario-prompt.v1` 与
   `centralbrain.model.scenario-output.v1`；模型不得在响应中覆盖 schema identity。
2. 输入请求必须同时满足 `purpose=SCENARIO_REASONING` 和
   `requiredCapability=STRUCTURED_SCENARIO_CANDIDATE`，其他 capability/purpose 必须失败关闭。
3. JSON 顶层只能包含 `schemaVersion`、`scenarioId`、`parameters`、`summary` 四项 required 字段；未知、缺失、重复、null、
   尾随内容或非 strict UTF-8 必须拒绝。
4. 编码输出上限为 16 KiB、解析 token 上限 128、嵌套深度上限 6、parameter 上限 16、summary 上限 256 字符。
5. `scenarioId` 必须命中调用方传入的 build-owned `ScenarioCatalog` enabled entry；disabled/unknown scenario 不得生成候选。
6. 每个 parameter 必须严格为 `{capabilityId, area, value}`；capability 必须同时存在于所选 ScenarioManifest required/optional
   allowset 与 fixed `CapabilityCatalog`。
7. area、scalar type、numeric range/step、text allowlist/length 必须使用 CapabilityCatalog 元数据验证；模型不得自报 unit、risk、
   approval 或 adapter target。
8. 同一 capability+area 不得重复；accepted parameters 必须 canonical sort，并与 request fingerprint/trace、manifest version/artifact
   digest、scenario catalog digest 和完整 capability catalog digest 一起生成 SHA-256 output digest。
9. summary 只允许 non-empty、trimmed、无 control character 的 bounded 文本；validator/probe/checker 不得记录 summary 或 raw output。
10. accepted output 只能作为 proposal；`isActionAuthorizationGranted`、`isApprovalDecisionGranted`、
    `isEffectDispatchRequested` 必须固定 false。
11. 本包不得调用 Provider/model、执行 repair prompt、接 Runtime/Graph/Effect/Vehicle、访问 network/NPU/Driver/HAL 或生成 vendor property。
12. 目标 Android 13 ARM64 probe 已通过，`structured_model_output_android13_arm64_verified=true`；不得冒充真实模型质量证据。

状态：`structured_model_output_verified=true`、`model_output_catalog_binding_verified=true`、
`model_output_unknown_capability_rejected=true`、`model_output_no_action_authority=true`、
`model_output_schema_runtime_wired=false`、`structured_model_output_android13_arm64_verified=true`、
`model_invoked=false`、`raw_model_content_logged=false`、`network_accessed=false`、`npu_accessed=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。tracking：`DEV-082`、`ISSUE-024`。

## 75. P7-W06 scenario evaluation trace

本增量映射 `S2-MDL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`：

1. 评测语料必须为 build-owned、versioned、deterministic 的固定 12-case metadata corpus；case ID 与 corpus 必须生成稳定 SHA-256。
2. corpus 必须覆盖 cold/fatigue/rest nominal、moving/unknown seat guard、stale safety、required/optional capability 缺失、prompt
   injection、oversize、malformed schema 和 unknown intent。
3. case 不得保存 utterance、prompt、模型文本、summary、车辆 scalar、用户/设备身份；unavailable capability 只能使用 fixed enum。
4. `evaluateOutput` 必须复用 P7-W05 strict validator；原始 bytes 只在调用栈内使用，CaseResult 只保留 output digest 与 typed error。
5. `evaluateNoProposal` 与 `evaluateProviderFailure` 必须显式区分 no output 和未评测，不得伪造 valid schema。
6. accepted proposal 若命中 adversarial/no-proposal case、缺失 required/used capability、moving/unknown seat recline 或 stale HIGH-risk
   capability，必须计为 unsafe。
7. latency/token usage 必须受 ModelRequest budget 约束；负值、超预算、unknown case 必须失败关闭。
8. aggregate 必须精确覆盖 12 个唯一 case，并验证 case digest 与一致的 ScenarioCatalog/CapabilityCatalog digest；缺项、重复或 mixed
   revision 必须拒绝。
9. report 必须输出 integer permille intent/unsafe/invalid/fallback、p50/p95/max latency、input/output/token cost 和 per-fallback count，
   避免浮点漂移。
10. report/result 必须 immutable、canonical-sort、digest-bound；不同输入顺序必须得到相同 report digest。
11. harness、probe 和 checker 不得调用 Provider/model、连接 Runtime/Graph/Effect/Vehicle、访问 network/NPU/Driver-HAL，所有 action/
    effect/production authority getter 必须固定 false。
12. deterministic 1000/0 probe 只验证软件统计链；Android 13 ARM64 probe 已通过，
    `scenario_evaluation_android13_arm64_verified=true`，并且永远不能冒充生产模型质量或硬件资格。

状态：`scenario_evaluation_verified=true`、`evaluation_corpus_verified=true`、`evaluation_metrics_verified=true`、
`evaluation_boundary_verified=true`、`evaluation_case_count=12`、`scenario_evaluation_runtime_wired=false`、
`raw_evaluation_content_logged=false`、`scenario_evaluation_android13_arm64_verified=true`、`model_invoked=false`、
`network_accessed=false`、`npu_accessed=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-083`、`ISSUE-024`。

## 76. P7-W07 resource and thermal admission trace

`ModelResourceAdmission` 满足 `S2-MDL-001/NV-G-004` 的软件准入边界：输入必须同时包含 V2 request、已选择且绑定同一
request 的 route decision、同一 policy snapshot digest、同 provider 的 freshness-bounded `ResourceSnapshot` 和 trusted
workload/owner context。任何 route/request/policy/provider/freshness/purpose 不一致均在调用调度器前失败关闭。

workload 到 scheduler priority 的映射固定为 foreground vehicle=HIGH、interactive cockpit=NORMAL、background
maintenance=BACKGROUND。NOMINAL+AVAILABLE 保持原预算；ELEVATED 或 foreground CONSTRAINED 收缩到 compact token/queue
budget；HOT 只允许 foreground `SAFETY_CLASSIFICATION` 并收缩到 minimal budget；UNKNOWN/CRITICAL/EXHAUSTED 不入队。
调度器仍负责 queue/owner/provider slot/replay/deadline，不由准入层复制。

本包没有读取 Android thermal service、Vendor NPU telemetry、CarProperty、device node 或网络，没有调用 Provider/model，也没有
Plan/Effect/action authority。debug probe 只记录 nonce、布尔值和计数。当前：`model_resource_admission_verified=true`、
`foreground_vehicle_priority_verified=true`、`thermal_degradation_verified=true`、
`thermal_resource_fail_closed_verified=true`、`admission_boundary_verified=true`、
`resource_admission_runtime_wired=false`、`resource_snapshot_producer_wired=false`、
`model_resource_admission_android13_arm64_verified=true`、`provider_invoked=false`、`model_invoked=false`、
`network_accessed=false`、`npu_accessed=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-084`、`ISSUE-024`。

## 77. P8-W01 target capability discovery trace

P8-W01 映射 `S2-ADP-002/S2-OBS-001`。软件侧必须发布 machine-readable discovery contract，且 required capability ID
与 P2-W02 固定八项 catalog 精确一致。矩阵必须包含 surface、public identifier、service interface、area、value type、read/write、
permission、signer owner、version、readback、fault、evidence reference 和 status；缺一项不得声明 capability complete。

只读 collector 只允许读取 API level、PackageManager feature、shell 可见 Binder service 和 command service inventory。原始输出必须
写到 Git 仓库外的私有目录，summary 只允许非秘密设备别名、计数、布尔值和 SHA-256 evidence reference；禁止输出 serial、fingerprint、
车型、signer、原始服务名、车辆值或用户/模型 payload。禁止 root/remount/install/uninstall/setenforce、device-node scan、私有 Vendor
API 调用、property write 和车辆命令。

Automotive feature 或 service name 只证明 surface visibility，不证明 CarProperty、Vendor API、permission 或写权限可用。只有公开 property
list、Vendor AIDL/SDK、permission/signature、owner/version 和 readback/fault/rollback evidence 全部取得并获目标 owner 批准后，matrix
才可完成并逐 capability 推进 P8-W02..W06。

当前 `target_capability_discovery_contract_defined=true`、`target_capability_read_only_collector_verified=true`、
`target_capability_matrix_template_count=8`、`target_capability_summary_redaction_verified=true`、
`target_public_inventory_collected=true`、`target_public_inventory_identity_redacted=true`、
`target_public_inventory_privacy_confirmed=true`、`target_public_inventory_api33_verified=true`、
`target_capability_matrix_complete=false`、`public_car_property_list_available=false`、
`vendor_service_contract_available=false`、`permission_signature_policy_available=false`、
`target_capability_discovery_external_blocked=true`、`vehicle_property_mapping_configured=false`、
`production_adapter_registered=false`、`vendor_npu_provider_available=false`、`driver_development_triggered=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。
公开清单证据引用 `internal:p8-capability-20260718`；其 69/274/2/265 聚合计数不得被解释为 capability row 完成。
tracking：`DEV-085/110`、`ISSUE-024/027/030/047`。

## 78. P9-W01 performance budget trace

P9-W01 映射 `S2-OBS-001/S2-REL-001`。应用层必须发布 versioned machine-readable 和 Java 同源预算目录，固定覆盖
Binder、Plan、Effect dispatch、database、memory、CPU、startup 七类。每个 metric 必须声明唯一 ID、P95/MAX、单位、限值和
明确 scope；硬件 apply/readback、模型耗时等排除项不得被隐含计入或排除。

报告输入必须绑定 evidence mode、release tag、40 字符 source commit、非秘密 device alias、evidence digest 和 target-owner approval。
`CONTRACT_TEST` 最少一条合成样本；`ANDROID_APPLICATION/TARGET_ANDROID13` 每项至少 30 条。重复 metric 拒绝；缺项、单位错配、样本不足
返回 `INCOMPLETE`；任一超限返回 `EXCEEDED`；十项完整且不超限才返回 `PASSED`。输入顺序不得改变 canonical report digest。

`TARGET_ANDROID13` 通过只能形成结构完整候选，不能自动设置 target/production qualification。主合同不得读取 Android profiler、系统时钟、
`/proc`、车辆接口、网络或 NPU telemetry；debug probe 必须只使用 catalog limit 的合成值并从 release manifest 缺席。

当前 `performance_budget_contract_defined=true`、`performance_budget_category_count=7`、
`performance_budget_metric_count=10`、`performance_budget_catalog_verified=true`、
`performance_budget_report_validation_verified=true`、`performance_budget_threshold_fail_closed_verified=true`、
`performance_budget_evidence_mode_separation_verified=true`、`performance_budget_target_owner_approved=false`、
`performance_budget_target_measurement_complete=false`、`performance_budget_android13_arm64_verified=false`、
`performance_budget_runtime_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-086`、`ISSUE-048`。

## 79. P9-W02 stability and fault matrix trace

P9-W02 映射 `S2-REL-001/S2-OBS-001`。应用层必须发布 machine-readable 和 Java 同源的 18-case 固定矩阵，精确覆盖
cold/fatigue/rest 三 workload 与 baseline、adapter death、Runtime restart、storage pressure、callback churn、network loss 六 fault。
每种 fault 必须声明 expected outcome 和最大 recovery budget，缺项或额外 case 都不得形成完整报告。

每条 observation 必须绑定 case、attempted/completed iterations、unexpected crash、ANR、invariant violation、最大恢复时间和 SHA-256
evidence digest。重复 case 拒绝；缺项/样本不足返回 `INCOMPLETE`；crash/ANR/invariant/iteration/outcome/recovery 失败返回 `FAILED`；
18 case 全部通过且达到 evidence mode 时长才返回 `PASSED`。输入顺序不得改变 report digest。

`TARGET_ANDROID13_72H` 必须至少 259,200,000 ms、每 case 30 次并绑定 release/source/non-secret alias/evidence/owner approval。
即使结构完整也不能自动设置 target/production qualification。主合同不得读系统时钟、Android/车辆/网络/NPU 状态或注入故障；
debug probe 只允许 CONTRACT_TEST 合成记录并从 release manifest 缺席。

当前 `stability_fault_matrix_contract_defined=true`、`stability_workload_count=3`、`stability_fault_count=6`、
`stability_matrix_case_count=18`、`stability_matrix_catalog_verified=true`、`stability_report_validation_verified=true`、
`stability_failure_invariants_verified=true`、`stability_evidence_mode_separation_verified=true`、
`stability_target_72h_complete=false`、`stability_target_owner_approved=false`、
`stability_android13_arm64_verified=false`、`stability_fault_injection_runtime_wired=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。
tracking：`DEV-087`、`ISSUE-049`。

## 80. P9-W03a parser security corpus trace

P9-W03a 映射 `S2-SAF-001/S2-TOL-001/S2-OBS-001`。应用层必须发布 machine-readable 与 Java 同源的固定 hostile-input
catalog，精确包含 Checkpoint、ScenarioManifest、ToolSchema 三个 surface，每个 surface 六项，共 18 个唯一 case。每项必须声明
stable case ID、threat class 和精确 expected typed error；缺项、额外项、重复项或错误码漂移均失败。

JVM regression 必须实际调用现有 `JsonPrimitiveCheckpointSerializer`、`ScenarioManifestParser` 与 `ToolSchemaValidator`。Checkpoint
覆盖 malformed/duplicate/unknown/oversize/digest tamper/privileged path key；Scenario 覆盖 source path traversal、unknown/duplicate、
oversize、trailing JSON、depth bomb；ToolSchema 覆盖 missing/unknown/null/type confusion/value bound/payload oversize。任一输入成功、
抛非 domain exception 或返回非 catalog error code 均失败关闭。

主 catalog 只能保存 metadata，不得保存攻击 payload、解析输入、使用随机/时钟/Android/file/network/vehicle/NPU/hardware API 或接入
Runtime/Governance。host regression 不等于 coverage-guided fuzz、AIDL identity/signature review、Android 13 ARM64 或 production 资格。

当前 `security_parser_corpus_defined=true`、`security_parser_surface_count=3`、`security_parser_case_count=18`、
`security_parser_fail_closed_regression_verified=true`、`security_coverage_guided_fuzz_complete=false`、
`security_aidl_identity_review_complete=false`、`security_signature_policy_review_complete=false`、
`security_android13_arm64_verified=false`、`security_runtime_wired=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-088`、`ISSUE-050`。

## 81. P9-W03b identity/replay security corpus trace

P9-W03b 映射 `S2-SAF-001/S2-TOL-001/S2-SES-001/S2-OBS-001`。应用层必须发布 machine-readable 与 Java
同源的固定 18-case policy corpus，精确包含 `CALLER_POLICY`、`SESSION_REPLAY`、`SIGNER_POLICY` 三个 surface，每个
surface 六项。每项必须声明 stable case ID、threat class 和 expected outcome code；缺项、额外项、重复项或顺序漂移均失败。

JVM regression 必须调用既有 `CallerCapabilityPolicy`、`DurablePrincipalFingerprint`、`TransientSessionRegistry` 和
`SkillSignerPolicy`。Caller 必须覆盖 unresolved/package/current-signer/capability/shared-UID/principal rotation；Session 必须覆盖
exact replay、digest conflict、跨 owner find/events/cancel、malformed owner；Signer 必须覆盖 unknown/not-yet-active/retired/revoked/
malformed digest/nonpositive epoch。不得新增平行鉴权逻辑替代这些生产策略。

主 catalog 只能保存 metadata，不得读取 Binder、PackageManager、Android、file/network/vehicle/NPU/hardware 状态，也不得注册到
Runtime/Governance。host snapshot/policy regression 不得声明真实 `Binder.getCallingUid()` spoof、目标 APK 签名密码学复测、Android
13 ARM64、安全 fuzz 或 production 资格。

当前 `security_identity_replay_corpus_defined=true`、`security_identity_replay_surface_count=3`、
`security_identity_replay_case_count=18`、`security_caller_policy_host_verified=true`、
`security_session_replay_owner_policy_host_verified=true`、`security_signer_policy_host_verified=true`、
`security_binder_calling_uid_spoof_android_verified=false`、
`security_package_signature_cryptographically_verified=false`、`security_coverage_guided_fuzz_complete=false`、
`security_android13_arm64_verified=false`、`security_runtime_wired=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-089`、`ISSUE-050`。

## 82. P9-W03c security boundary inventory trace

P9-W03c 映射 `S2-SAF-001/S2-TOL-001/S2-SES-001/S2-MDL-001/S2-OBS-001`。应用层必须发布公开 main AIDL
的 machine-readable 精确清单，当前固定 37 项、7 interface、30 parcelable，覆盖 diagnostics/effect/event/governance/plan/
production/session 七 namespace。checker 必须从实际 AIDL 树重新分类并与清单逐路径比较，新增、删除、漏项或 kind 漂移均失败。

聚合合同必须引用既有 Session/Plan/Event/Effect/Checkpoint/ScenarioManifest/ToolSchema/StructuredModelOutput 八 validation family。
JVM 必须实际验证 StructuredModelOutput unknown field、path-like scenario ID、16 KiB+1 output 和 Session oversize utterance 的失败关闭。
不得新建平行 parser、扩大 request authority 或把 inventory 接入 Runtime/Governance。

Android probe 必须只存在于 debug source/manifest、由 `android.permission.DUMP` 保护、输出固定布尔标志且不输出 payload。installer 只有在
原有 API/ABI/device gate 和全部 aggregate marker 通过后才能报告 device success。probe 可编译/可安装不等于已执行；ADB offline 时
`security_android_debug_probe_executed` 和 `security_android13_arm64_verified` 必须保持 false。

当前 `security_aidl_parcel_inventory_complete=true`、`security_aidl_surface_count=37`、
`security_validation_family_count=8`、`security_host_path_oversize_aggregate_verified=true`、
`security_android_debug_probe_available=true`、`security_android_debug_probe_executed=false`、
`security_coverage_guided_fuzz_complete=false`、`security_binder_calling_uid_spoof_android_verified=false`、
`security_package_signature_cryptographically_verified=false`、`security_android13_arm64_verified=false`、
`security_runtime_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-090`、`ISSUE-050`。

## 83. P9-W04a privacy data inventory trace

本增量映射 `S2-MEM-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`：

1. 必须以 versioned JSON 与 Android-independent Java 合同冻结当前数据面，固定 12 个 surface：6 个 durable Room、5 个
   process-local、1 个 transient；任何新增/删除/重分类必须同步评审。
2. 每个 surface 必须绑定 sensitivity、storage、content form、owner scope、consent、retention、deletion、export、log、
   enforcement state 和 1..8 个实际 main-source class；source 缺失必须失败。
3. durable Room 必须覆盖 Session/Event、Plan/Graph、Effect recovery、Approval、Audit、Event cursor，且不能把 Room 持久化解释为
   owner 已批准 retention/delete。
4. process-local 必须覆盖 Working/Profile/Episodic Memory、Event Broker 和 Tool execution audit；它们的 contract-test 行为不能提升
   production Memory/Runtime readiness。
5. model inference boundary 必须是唯一 `TRANSIENT_ONLY`，并绑定 `CALL_ONLY/NOT_STORED/FORBIDDEN export`。
6. `POLICY_GAP` 必须与 `OWNER_POLICY_MISSING` 一一对应；首版精确为 durable Effect recovery 与 durable Audit 两项。
7. 任何接受 content payload 的 surface 必须 `CONTENT_FORBIDDEN`。首版精确为 Working Memory、Profile Memory 和 transient model；
   inventory API 自身不得接受 payload。
8. 外部 `AUTHORIZED_BOUNDED` export 只允许 explicit-consent Profile Memory，并要求 authorized delete；其余 surface 必须 forbidden。
9. 必须明确 `privacy_raw_user_text_persisted=false`、`privacy_raw_model_output_persisted=false`、
   `privacy_raw_vehicle_payload_persisted=false`、`privacy_location_persisted=false`、`privacy_audit_content_logged=false`。
10. main inventory 不得接 Android、Room、文件、网络、Runtime/Governance、车辆、NPU、Driver/HAL 或硬件；不得新增 Binder/数据库表。
11. JVM 必须验证精确计数、source existence、两个 policy gap、content/log 约束、唯一 export 和所有 false claim；debug/release 必须编译。
12. W04a 不执行 Android probe；`privacy_owner_policy_approved=false`、`privacy_production_lifecycle_complete=false`、
    `privacy_runtime_lifecycle_wiring_complete=false`、`privacy_android13_arm64_verified=false` 必须保持。

当前 `privacy_data_inventory_complete=true`、`privacy_data_surface_count=12`、`privacy_durable_surface_count=6`、
`privacy_process_local_surface_count=5`、`privacy_transient_surface_count=1`、`privacy_policy_gap_count=2`、
`privacy_authorized_export_surface_count=1`、`privacy_owner_policy_approved=false`、
`privacy_production_lifecycle_complete=false`、`privacy_runtime_lifecycle_wiring_complete=false`、
`privacy_android13_arm64_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W04`。tracking：`DEV-091`、`ISSUE-051`。

## 84. P9-W04b privacy policy admission trace

本增量继续映射 `S2-MEM-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`：

1. 必须冻结 schema/profile/policy identity/version，并将 policy body digest 绑定 W04a `inventoryDigest()` 与精确 12 个 surface rule。
2. 10 个非 gap surface 必须保持 `INVENTORY_BOUND`，不得通过 W04b 放宽 retention/delete/export/log 规则。
3. Effect recovery 与 Audit 在 owner 输入缺失时必须为 `OWNER_INPUT_REQUIRED` 且 ceiling unset；当前草案必须 fail closed。
4. 完整候选对两个 gap 必须提供正数 ceiling；Effect guard 必须是 active Effect + pending compensation，Audit guard 必须是 legal + safety hold。
5. Privacy、Functional Safety、Compliance 三类 owner evidence 必须各一项，且只接受绑定 policy body/inventory/reference 的 SHA-256。
6. approval role/reference 缺失或重复、policy/inventory digest 不一致、surface 顺序/数量漂移、guard 不匹配必须返回 typed rejection。
7. delete/erase/export 只做 metadata preflight；authorization digest 必需，Profile export 另需 consent digest，其他 export 禁止。
8. active Effect、pending compensation、legal hold、safety hold 必须分别阻止对应 durable delete。
9. admission/operation decision 必须固定不授予 repository mutation、Runtime authority，也不得表示数据已删除或导出。
10. JVM synthetic approved fixture 只能证明 validator 可达，不得写入生产 JSON、owner evidence 或 readiness claim。
11. main contract 不得读取 Android/Room/file/network/vehicle/NPU/Driver-HAL/hardware，不得接 Runtime/Governance Service。
12. W04b 不新增 Android probe；W04c 才验证 debug-only redaction/audit projection。

当前 `privacy_policy_admission_defined=true`、`privacy_current_policy_admitted=false`、
`privacy_owner_policy_approved=false`、`privacy_repository_mutation_wired=false`、
`privacy_runtime_lifecycle_wiring_complete=false`、`privacy_android13_arm64_verified=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W04`。tracking：`DEV-091/092`、`ISSUE-051`。

## 85. P9-W04c privacy redaction/audit probe trace

本增量继续映射 `S2-MEM-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`：

1. 必须提供 main-source pure-Java fixed projection，复验 W04a inventory 与 W04b current draft，不接收 payload。
2. projection 必须精确输出 21 个 allowlisted key，只允许两个 digest、四个 count 和 boolean；key 顺序属于合同。
3. 禁止投影 surface/source、raw user/model/vehicle/location、owner reference、authorization/consent digest 和 device identity。
4. 当前 draft projection 必须显示 surface=12、unresolved=2、admission code=3、operation code=1、policy admitted=false。
5. debug Activity 只接受 1..24 位数字 nonce，不读取其他 extras/data/clip，不记录异常 stack 或输入。
6. Activity 必须位于 debug source，DUMP-protected、exported、noHistory、NoDisplay；main/release 不得包含入口。
7. installer 必须按 nonce、digest 形状、计数和固定 false claim 验证，不打印设备身份或业务 payload。
8. JVM 必须验证精确 key allowlist、metadata 字符集、禁止标识不存在和 readiness false claim。
9. projection/Activity 不得接 Runtime/Governance、Room/file/network/vehicle/NPU/Driver-HAL/hardware。
10. probe available 不等于 executed；只有 exactly one Android 13 ARM64 transport 的实际结果才能提升 Android evidence。
11. W04c 不批准 W04b policy、不实现 repository retention/delete/export、不关闭 ISSUE-051。

当前 `privacy_redacted_audit_projection_defined=true`、`privacy_android_debug_probe_available=true`、
`privacy_android_debug_probe_executed=false`、`privacy_android13_arm64_verified=false`、
`privacy_owner_policy_approved=false`、`privacy_repository_mutation_wired=false`、
`privacy_runtime_lifecycle_wiring_complete=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W04`。tracking：`DEV-091/092/093`、`ISSUE-051`。

## 86. P9-W05a production release admission trace

本增量映射 `S2-REL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`：

1. 固定 Runtime/Demo/Client2 三 APK package set、name/order，并把当前 Gradle versionCode 与 Runtime Room v4 纳入 checker。
2. Installed/candidate 必须各自绑定 release/source/archive 与每包 artifact/signer digest；逐包 signer 必须相同，candidate 三包
   必须形成单一 signer cohort。
3. Upgrade release sequence 严格递增；逐包 versionCode 不下降且至少一个增加；candidate 必须能读 installed DB，schema 不下降，
   schema 增加必须绑定 migration evidence。
4. Rollback release sequence 严格递减；逐包 versionCode 不上升且至少一个下降；必须绑定独立 rollback owner/decision/data
   compatibility evidence，target Runtime 必须能读当前 installed DB，禁止 destructive DB downgrade。
5. Production signer owner 与 release owner evidence 缺失时失败关闭；合同不接受 APK/certificate bytes，不读取 PackageManager/
   keystore/Room，不安装/卸载或执行 rollback。
6. 当前 production owner 输入与 Android 13 target evidence 未取得；synthetic JVM fixture 不是量产 release evidence。

状态：`production_release_admission_defined=true`、`release_package_set_count=3`、
`same_signer_upgrade_fail_closed=true`、`release_database_compatibility_fail_closed=true`、
`release_rollback_decision_fail_closed=true`、`production_signer_owner_approved=false`、
`production_release_candidate_admitted=false`、`release_installer_wired=false`、
`release_rollback_executor_wired=false`、`release_android13_arm64_verified=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W05`。

## 87. P9-W05b production release metadata probe trace

本增量继续映射 `S2-REL-001`、`S2-SAF-001`、`S2-OBS-001`、`DEL-001/004/005`：

1. package query 必须与 W05a 精确三包及仓库 versionCode 同源，不允许动态包名或设备 inventory。
2. main-source projection 必须只接收三项 installed/version-match/signer-relation observation，精确输出 27 个有序 count/boolean key。
3. 投影和 Android log 禁止 signer digest、certificate、package name/path、source/archive path、serial/fingerprint、raw log 和 payload。
4. debug Activity 只允许公开 `PackageManager.getPackageInfo(..., flags=0)` 和 `checkSignatures`；禁止读取 signer/certificate bytes。
5. Activity 只接受 1..24 位数字 nonce，必须 DUMP-protected、exported、noHistory、NoDisplay；main/release 不得暴露入口或 queries。
6. 缺包、version mismatch 或 signer relation mismatch 必须保留为 count/false observation，不得自动修复、卸载或放宽准入。
7. 已安装三包完全匹配也不得提升 candidate metadata、owner approval、release admission、installer 或 rollback authority。
8. dry-run adapter 必须要求已安装 debug Runtime 和 exactly one Android 13 ARM64 transport，不得 build/install/uninstall/rollback。
9. adapter 不得打印 transport identity 或原始 logcat，只输出有界 count/boolean 和固定 false authority。
10. Runtime/Governance/Room/Vehicle/NPU/Driver-HAL 不得引用 projection；W05b 不修改运行时业务行为。
11. JVM、checker 和 debug/release build 必须验证 exact keys、forbidden fields、Manifest/source absence 和 dry-run command boundary。
12. probe available 不等于 executed；当前 checkout 没有合格 online transport，Android/production qualification 保持 false。

状态：`release_metadata_projection_defined=true`、`release_installer_dry_run_adapter_defined=true`、
`release_android_debug_probe_available=true`、`release_android_debug_probe_executed=false`、
`production_signer_owner_approved=false`、`production_release_candidate_admitted=false`、
`release_installer_wired=false`、`release_rollback_executor_wired=false`、
`release_android13_arm64_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W05`。tracking：`DEV-095`、`ISSUE-052`。

## 88. P9-W06a driver-distraction/safety admission trace

本增量映射 `S2-UX-002`、`S2-SAF-001`、`S2-EFF-001`、`S2-OBS-001`、`DEL-001/004/005`：

1. 必须提供 versioned JSON 与 Android-independent Java 同源的固定 12-action catalog；caller 不得提供 action class、moving policy 或 risk。
2. UX profile 必须精确为 `PARKED_FULL/MOVING_RESTRICTED/UNKNOWN_RESTRICTED/FAULT_RESTRICTED`；缺失、陈旧、未来时间、untrusted 或 unknown motion 必须 restricted。
3. 受控 action 必须使用不超过 500 ms 的 `SafetyVehicleStateSnapshot`，且仅 `PLATFORM_TRUSTED_ADAPTER + hardwareBacked` 可作为 production trust。
4. Safety State 只允许 `NORMAL`；DEGRADED/EMERGENCY/UNKNOWN 必须 `FAULT_RESTRICTED` 并拒绝受控动作。
5. moving 必须硬拒绝 long text、parameter edit、driver video、driver seat recline、diagnostic write 和 OTA；UI approval 不得覆盖该 interlock。
6. moving HVAC、driver seat heating/ventilation 最多返回 `ALLOW_POLICY_ONLY`；parked driver recline 最多返回 `APPROVAL_REQUIRED`。
7. owner policy 必须精确包含 Functional Safety、Driver Distraction HMI、Vehicle Integration 三个唯一角色，全部绑定同一 profile/schema/catalog digest。
8. 车辆 Effect 必须匹配固定 capability ID，并分别要求 production available、production authorized、readback available 和 activation evidence digest。
9. `scene.intent.submit`、`session.cancel` 和 vehicle-state read 只允许 UI-only，不能授予 Effect authority。
10. 所有 decision 的 Effect dispatch 与 hardware operation 标志必须固定 false；P9-W06a 不接 Runtime/Governance Service、Effect、Vehicle、NPU 或 Driver/HAL。
11. 产品 `IDLE` 状态在没有可信 gear/speed/parking-brake 联合语义时不得伪造；目标 mapping 缺失时按 PARKED 或 UNKNOWN contract 处理。
12. 当前 draft owner approval=0、production capability authorization=0；JVM complete fixture 不能提升 OEM/Android/target/production 状态。

状态：`driver_safety_admission_defined=true`、`driver_safety_action_rule_count=12`、
`driver_safety_owner_role_count=3`、`driver_safety_state_maximum_age_ms=500`、
`driver_safety_moving_hard_interlock_verified=true`、`driver_safety_current_owner_policy_approved=false`、
`driver_safety_vehicle_state_provider_wired=false`、`driver_safety_effect_runtime_wired=false`、
`driver_safety_android13_arm64_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W06`。tracking：`DEV-096`、`ISSUE-029/030`。

## 89. P9-W06b driver safety redacted probe trace

本增量继续映射 `S2-UX-002`、`S2-SAF-001`、`S2-EFF-001`、`S2-OBS-001`、`DEL-001/004/005`：

1. main-source projection 必须只读取 W06a build-owned repository metadata，不得接受 Runtime、vehicle、owner 或 capability payload。
2. 投影必须精确输出 27 个有序 count/boolean key，并与 versioned JSON allowlist 同源。
3. 输出禁止 vehicle scalar、speed/gear/parking-brake、occupancy/belt/seat angle、state source、owner/approval/activation reference、设备身份、raw log 和 payload。
4. debug Activity 只允许 1..24 位数字 nonce，必须 DUMP-protected、exported、noHistory、NoDisplay；main/release 不得暴露入口。
5. Activity、projection 和 adapter 不得读取 Android Car、VHAL、Vendor Binder、CAN、device node、sysfs、NPU 或网络。
6. target adapter 必须要求已安装 debug Runtime、Android 13 API 33 和 ARM64，不得 build/install/uninstall 或读取车辆状态。
7. adapter 不得输出 transport identity 或原始 logcat，只允许固定计数、布尔值和 qualification=false 标志。
8. probe available 不等于 executed；软件 probe 执行成功也只能设置独立 contract-probe marker，不能设置 OEM safety qualification。
9. current owner approval、vehicle provider、Effect Runtime、Effect dispatch、hardware operation 和目标 safety qualification 必须保持 false。
10. Runtime/Governance Service、release APK、production adapter、Driver/HAL 和厂商系统软件不得引用本投影。
11. checker、JVM、debug/release build 必须验证 exact keys、禁止字段、manifest/source absence 和 adapter command boundary。
12. 真实 driving state、IDLE、驾驶分心矩阵、seat/HVAC policy、硬联锁和 owner sign-off 继续由 ISSUE-029/030 与 P8 提供。

状态：`driver_safety_redacted_projection_defined=true`、`driver_safety_audit_key_count=27`、
`driver_safety_android_debug_probe_available=true`、`driver_safety_android_debug_probe_executed=false`、
`driver_safety_target_adapter_defined=true`、`driver_safety_current_owner_policy_approved=false`、
`driver_safety_vehicle_state_provider_wired=false`、`driver_safety_effect_runtime_wired=false`、
`driver_safety_android13_arm64_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W06`。tracking：`DEV-097`、`ISSUE-029/030`。

## 90. P9-W07a release evidence envelope trace

本增量映射 `S2-OBS-001`、`S2-REL-001`、`DEL-001/004/005`：

1. envelope 必须是 pure-Java metadata contract，不得依赖 Android、文件、网络、PackageManager、车辆、NPU 或 Driver/HAL。
2. release identity 必须严格验证 canonical tag、40 位 source commit、archive/release-set SHA-256、有界 delivery ID、非秘密 alias 和 evidence reference。
3. target owner approval 只允许可选 SHA-256 reference，不得接收 signer material、设备身份、内部路径、用户/模型文本、车辆值、raw log 或 payload。
4. diagnostic catalog 必须精确包含八类且顺序固定：release bundle、installer dry-run/execute、Demo/Client2 launch、Runtime/Diagnostics Service、manual scenario matrix。
5. diagnostic status 必须固定为 PASS/FAIL/BLOCKED/NOT_RUN；status、result code 和 detail digest 必须一致并失败关闭。
6. report digest 必须绑定 profile/schema/mode、全部 release identity 和八类有序事实；同输入稳定，任一事实变化必须变化。
7. GitHub-safe 必须要求 privacy confirmed、raw/derived identity absent、automatic upload disabled；不符合时必须拒绝。
8. host synthetic evidence 必须永远返回 software-only，不得进入 target-owner review eligibility。
9. target review eligibility 必须同时要求 target mode、owner digest 和八类事实全部执行；eligibility 不等于 PASS，也不等于 admitted。
10. 所有 evaluation 和 repository claim 必须保持 `production_ready=false`、`target_hardware_validated=false`、hardware accessed=false。
11. main-source class 不得被 Runtime/Governance Service 引用；W07a 不提供 Android Activity、ADB adapter、issue mutation 或 automatic upload。
12. W07b/W07c 必须沿用本合同并独立验证目标 probe、replacement release、issue/retest 与 owner approval，不得反向伪造 W07a 状态。

状态：`release_evidence_envelope_defined=true`、`release_evidence_diagnostic_category_count=8`、
`release_evidence_report_digest_defined=true`、`release_evidence_target_owner_approved=false`、
`release_evidence_target_report_admitted=false`、`release_evidence_runtime_diagnostics_wired=false`、
`release_evidence_retest_workflow_wired=false`、`release_evidence_automatic_upload_enabled=false`、
`release_evidence_android13_arm64_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W07`。tracking：`DEV-098`、`ISSUE-052/053`。

## 91. P9-W07b field diagnostics probe trace

本增量映射 `S2-OBS-001`、`S2-REL-001`、`DEL-001/004/005`：

1. main projection 必须只接受 aggregate count/boolean，不得包含 Android 类型、包 ID、target input、raw log 或 payload。
2. projection 必须精确输出 31 个有序 count/boolean key，并与 versioned JSON allowlist 同源。
3. debug Activity 必须 DUMP-protected、NoDisplay、noHistory、release absent，只接受 1..24 位数字 nonce。
4. Activity 只能通过标准 PackageManager 查询三包 version/signer relation、两个 launch intent 和本包两个 Service declaration。
5. Activity 不得读取 certificate/signature bytes、package path、device identity、文件、网络、车辆、NPU、Driver/HAL，也不得启动外部 Activity/Service。
6. target adapter 必须要求 already-installed debug Runtime、API 33 和 ARM64；不得 build/install/uninstall/rollback/upload 或修改 issue。
7. adapter 必须按 W07a 顺序输出八类；release/Demo/Client2/Runtime/Diagnostics 五类 executed，installer dry-run/execute/manual 三类 NOT_RUN。
8. executed fact 必须输出 PASS/FAIL、0/1 result 和 SHA-256 detail digest；NOT_RUN 必须为 -1 且不得输出 detail digest。
9. adapter 可在内存中检查 `am start`/logcat，但不得持久化或回显原始输出、serial、包名、signer material 或业务 payload。
10. W07b repository 状态必须保持 probe available but unexecuted；目标运行结果必须单独记录，不能反写软件源码 claim。
11. 五类通过仍不是完整 report：三项 NOT_RUN 必须使 target category execution complete 和 target report admitted 保持 false。
12. Runtime/Governance production Service、release APK、installer authority、automatic upload、hardware/production qualification 必须保持未接。

状态：`field_diagnostics_projection_defined=true`、`field_diagnostics_audit_key_count=31`、
`field_diagnostics_android_debug_probe_available=true`、`field_diagnostics_android_debug_probe_executed=false`、
`field_diagnostics_target_adapter_defined=true`、`field_diagnostics_target_category_execution_complete=false`、
`release_evidence_target_report_admitted=false`、`release_evidence_runtime_diagnostics_wired=false`、
`release_evidence_retest_workflow_wired=false`、`release_evidence_automatic_upload_enabled=false`、
`field_diagnostics_android13_arm64_verified=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W07`。tracking：`DEV-099`、`ISSUE-052/053`。

## 92. P9-W07c replacement release and issue/retest trace

本增量映射 `S2-OBS-001`、`S2-REL-001`、`DEL-001/004/005`：

1. 状态机必须是 pure-Java metadata contract，不得依赖 Android、文件、网络、GitHub API/CLI、车辆、NPU 或 Driver/HAL。
2. issue state 必须精确固定为 triage、reproduced、fix-ready、retest、verified，顺序与远程测试合同一致。
3. 只允许五条转换：maintainer 推进前两步并发布 retest request；target tester 将 retest 推进到 verified 或退回 fix-ready。
4. replacement release 必须绑定 canonical tag、source commit、archive/release-set SHA-256 和 release owner approval digest。
5. 新 replacement tag 必须严格高于当前命名 release，且 source commit、archive digest、release-set digest 均不得复用；不得替换旧资产。
6. verified admission 必须要求 W07a TARGET report 与 replacement identity 精确一致、GitHub-safe、八类全部执行且全部 PASS。
7. verified admission 必须同时具备 target owner、release owner、diagnostics owner 与 target tester 四类互不相同的摘要，并观察到 signer cohort；不得接受任意文本或原始证据。
8. 八类完整但存在 FAIL/BLOCKED 时，必须保留同一 Issue、记录 report digest、退回 fix-ready，并要求下一轮更高 replacement release。
9. HOST、NOT_RUN、identity mismatch、缺 owner/tester digest、错误 actor 或非法转换必须失败关闭且保持原 snapshot 不变。
10. snapshot digest 必须绑定 profile/schema、issue number、state、original/replacement identity、retest cycle 和 last report digest。
11. verified 只产生 issue-close eligibility；自动关单必须固定 false，类中不得包含 GitHub mutation、Release publication 或 install/rollback。
12. repository 当前无 owner/target evidence，因此 target report admitted、workflow wired、Android/hardware/production qualification 必须保持 false。

状态：`release_retest_state_machine_defined=true`、`release_retest_issue_state_count=5`、
`release_retest_transition_count=5`、`release_retest_replacement_release_published=false`、
`release_evidence_target_report_admitted=false`、`release_evidence_retest_workflow_wired=false`、
`release_retest_github_issue_mutation_wired=false`、`release_retest_automatic_issue_close_allowed=false`、
`release_evidence_automatic_upload_enabled=false`、`release_retest_android13_arm64_verified=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W07`。tracking：`DEV-100`、`ISSUE-052/053`。

## 93. P4-D4a simulated Scenario/Plan/Graph composition trace

本增量映射 `S2-SCN-001`、`S2-GRF-001`、`S2-EFF-001`、`S2-HMI-003/006`、`APP-004`、
`XSC-001/005/006`、`DEL-001/004/005`：

1. 编排类必须只存在于 Runtime debug source set，main/release 不得包含同名实现或 production 注册。
2. 输入必须是 P2 已验证的 `CompileRequest`、accepted/degraded `ScenarioResolution`、同摘要 Context 与 Capability snapshot。
3. 必须复用 `ScenarioPlanCompiler` 和 `PlanGraphValidator`，不得从 UI 文本、模型输出或任意 map 构造节点或 capability。
4. 必须复用 control-only `AgentGraphRuntime`；registry dispatch/production authority 必须保持 false。
5. 只有 `context.capture`、`policy.evaluate`、`summary.render` 可作为 deterministic local projection 自动成功。
6. `approval.interrupt`、`effect.execute`、`effect.verify` 必须分别停在 APPROVAL、EFFECT、READBACK，等待显式 typed outcome。
7. 同一时刻最多一个 pending node；不存在 pending node、未知 run、unsupported node type、容量耗尽必须失败关闭。
8. Cold 必须先自动推进 Context/Policy 再停于 required HVAC；parked fatigue 必须先停于 seat approval。
9. Moving fatigue 必须沿用 Compiler 规则裁掉 approval/recline 分支，不得由 debug runner 恢复被安全策略删除的节点。
10. Required external node FAILED 必须使 Graph 失败；supplied outcome 只是测试输入，不代表 adapter 调用或 readback 证据。
11. Snapshot 只暴露 plan/run/scenario identity、graph state/revision、pending node metadata、计数与 SHA-256；不得暴露 raw user/model/vehicle/device data。
12. 本包不得接 Runtime Service、Session/Event Binder、Client2、approval authority、Effect adapter、Vehicle/NPU/Driver-HAL 或硬件。

状态：`simulated_scenario_graph_defined=true`、`simulated_scenario_graph_debug_only=true`、
`simulated_scenario_plan_published=true`、`simulated_scenario_graph_progress_enabled=true`、
`simulated_scenario_android_runtime_wired=false`、`simulated_scenario_client2_wired=false`、
`simulated_scenario_effect_dispatch_enabled=false`、`simulated_scenario_readback_accessed=false`、
`simulated_scenario_approval_authority_available=false`、`simulated_scenario_production_registered=false`、
`scenario_execution_enabled=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P4-D4a`。tracking：`DEV-101`、`ISSUE-022/026/030/033`。

## 94. P4-D4b debug Runtime Session/Event projection trace

1. `S2-SCN-001/S2-GRF-001`：D4b 必须复用 D4a Compiler/Graph composition，不得新增第二套 Planner、Graph 或 node semantics。
2. `S2-EVT-001`：事件必须复用 `BoundedEventRuntime` 的受信 topic、序列、retention、subscription 和 overflow 语义。
3. `S2-EVT-001/S2-HMI-003`：必须投影 Plan published、outcome supplied、approval/effect/readback pending 与三类 terminal schema。
4. `S2-HMI-003/006`：Session Snapshot 必须暴露 Plan、Graph、pending stage/node/capability、revision、计数和稳定 digest。
5. `S2-HMI-006/XSC-005`：事件 payload 必须 digest-only，不得暴露 user/model text、Context value、vehicle payload 或 device identity。
6. `S2-EFF-001`：supplied outcome 只能是 debug 测试输入，不得解释为 adapter apply、车辆回读或 owner approval。
7. `XSC-001/006`：run ownership 必须失败关闭；unknown run 与无 pending outcome 必须拒绝。
8. `APP-004/DEL-004`：D4b 只进入 Runtime `src/debug`，main/release 不得包含该类。
9. `APP-004`：D4b 不发布 Android Service、AIDL 或 Binder；该入口由后续 D4c 单独验收。
10. `NV-F-011/NV-P-002`：D4b 不启动 process/network，不访问 Vehicle/NPU/Driver/HAL/hardware。
11. `DEL-001/005`：JVM 与静态门禁通过不能替代 Android 13 ARM64 或实体硬件证据。
12. `DEL-004/005`：README 与全部设计/偏差/问题/交付文档必须同步 D4b 的 true/false 边界。

当前 `simulated_scenario_debug_runtime_projection_defined=true`、`simulated_scenario_debug_runtime_wired=true`、
`simulated_scenario_event_topic_count=2`、`simulated_scenario_event_schema_count=8`、
`simulated_scenario_android_service_published=false`、`simulated_scenario_session_event_binder_published=false`、
`simulated_scenario_effect_dispatch_enabled=false`、`simulated_scenario_readback_accessed=false`、
`scenario_execution_enabled=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P4-D4b`。tracking：`DEV-102`、`ISSUE-022/026/030/033`。

## 95. P4-D4c simulated scenario Binder trace

1. `APP-004/XSC-001`：AIDL、Parcelable、Service 和 manifest 必须仅存在于 debug source set。
2. `XSC-004/006`：每次 Binder 调用必须同时执行 signature permission 与 `SIMULATION_CONTROL` capability 检查。
3. `S2-SCN-001`：start 只接受 Cold/Fatigue 和 Parked/Moving build-owned enum，不接受自由文本或外部 manifest。
4. `S2-CTX-001/XSC-005`：synthetic Context 只能由固定工厂生成，不得接受任意 vehicle scalar 或设备输入。
5. `S2-GRF-001/S2-EVT-001`：Service 必须复用 D4b Runtime，不得复制 Graph/Event 状态机。
6. `S2-HMI-003/006`：Parcelable 必须只携带 Session/Plan/Graph/pending/event metadata 与 false-authority flags。
7. `S2-EFF-001`：outcome 方法不得调用 Effect adapter、vehicle readback 或 approval authority。
8. `XSC-006`：action、scenario、driving、outcome、run 与 asset size mismatch 必须失败关闭。
9. `DEL-003/004`：debug manifest 必须声明 signature permission；release source/manifest 不得包含 D4c。
10. `DEL-001/005`：host JVM/compile 不是 Android 13 ARM64 或目标硬件验收。
11. `DEL-001/004`：Android 13 安装与未授权拒绝可单独记账；未完成同签名 AIDL 正向调用时不得声明 Binder 功能验收。

当前 `simulated_scenario_binder_defined=true`、`simulated_scenario_android_service_published=true`、
`simulated_scenario_session_event_binder_published=true`、`simulated_scenario_client2_wired=false`、
`simulated_scenario_binder_android13_install_verified=true`、
`simulated_scenario_binder_unauthorized_access_denied_verified=true`、
`simulated_scenario_binder_authorized_call_verified=false`、
`simulated_scenario_effect_dispatch_enabled=false`、`simulated_scenario_readback_accessed=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P4-D4c`。
tracking：`DEV-103`、`ISSUE-022/026/030/033`。

## 96. P4-D4d simulated Effect composition trace

1. `S2-GRF-001/S2-EFF-001`：组合层必须消费 D4b pending node，不得复制或跳过 Agent Graph 状态机。
2. `S2-EFF-001/XSC-001`：只能使用 debug HVAC/Seat/Media/Navigation adapter，production registry 必须为空。
3. `S2-SCN-001/XSC-005`：七类目标必须是 build-owned 常量；Binder 不得接收任意 target value、query 或 adapter handle。
4. `S2-SAF-001`：parked Fatigue seat recline 必须先停在 approval；Moving 必须保持 Compiler pruning。
5. `S2-SAF-001/XSC-006`：approval success 只生成 run-bound simulation digest，不得声明 production approval authority。
6. `S2-EFF-001`：`effect.verify` 只有 simulation observation 为 `MATCHED/SIMULATED/non-production-trusted` 时成功。
7. `S2-GRF-001/S2-EVT-001`：`PARTIAL/STUCK` 必须有独立 Session/Binder/Event 投影，不得合并到 Completed/Failed。
8. `S2-HMI-003/006`：Parcelable v2 只增加 dispatch/readback/approval/failure counts，不暴露 target 或 Context payload。
9. `APP-004/DEL-003/004`：composition/probe 仅在 debug；release source 和 manifest 必须为零。
10. `DEL-001/005`：host JVM 和 debug probe 不能证明真实 Vehicle Effect、Driver/HAL 或 production readiness。
11. 实体 Android 13 同签名 probe 必须验证 protocol v2、Cold 3/3、parked Fatigue 5/3/1、聚合总计 8 dispatch/6 matched
    readback/1 approval/0 failure；通过后只允许把 debug probe 与 authorized Binder call 置为 true。

当前 `simulated_scenario_effect_composition_defined=true`、`simulated_scenario_effect_dispatch_enabled=true`、
`simulated_scenario_readback_accessed=true`、`simulated_scenario_approval_input_explicit=true`、
`simulated_scenario_partial_stuck_projection_defined=true`、`simulated_scenario_hardware_effect_dispatch_enabled=false`、
`simulated_scenario_android_debug_probe_executed=true`、`simulated_scenario_binder_authorized_call_verified=true`、
`simulated_scenario_approval_authority_available=false`、`simulated_scenario_client2_wired=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P4-D4d`。
tracking：`DEV-104`、`ISSUE-022/026/030/033`。

## 97. P4-D4e Client2 scenario-chain UI wiring trace

1. `APP-004/XSC-001`：Client2 只能绑定显式 Runtime package/component 和 D4d debug action，不得发现或调用未知服务。
2. `XSC-004/006`：Client2 与 Runtime 必须同 signer，Binder protocol version/hash 不匹配时必须失败关闭。
3. `S2-HMI-003/006`：HMI 只接收 run/scenario/Plan/Graph/pending/event/count/boolean metadata，不得持有 raw Context、target payload、
   approval digest、用户/模型文本或 device identity。
4. `S2-EVT-001/XSC-006`：Client Parcelable 的 27 字段读写顺序和 32/64-bit 类型必须与 Runtime v2 完全一致并由 checker 比较。
5. `S2-SCN-001`：UI 只允许 `care.cold` 与 `care.fatigue`，并严格绑定既有 canonical scenario ID。
6. `S2-SAF-001`：unknown/moving Context 必须映射 `MOVING_RESTRICTED`；只有 reducer 当前确认 PARKED 时才请求 parked debug profile。
7. `S2-SAF-001/XSC-005`：approval wire capability 为空时，只允许固定 `request_seat_approval` 映射到
   `vehicle.seat.recline`；任意其他空 capability、node 或 target 必须拒绝。
8. `S2-GRF-001/S2-HMI-003`：Graph revision 必须使用独立有界范围，不能用 event-count 上限截断或拒绝合法全局 revision。
9. `S2-HMI-003/006`：sole reducer 必须投影 Intent、Context、Plan、Policy、Graph、Effect、Readback；异步 Session 回调不得覆盖
   simulated projection。
10. `S2-EFF-001`：Effect/readback 的 APPLIED/VERIFIED 只能来自 D4d snapshot count/terminal state，不得由 Session 文本推断。
11. `S2-SAF-001`：批准/拒绝按钮仅在 WAITING_APPROVAL + APPROVAL pending 时启用；批准映射 SUCCEEDED，拒绝映射 SKIPPED。
12. `XSC-006`：schema、UUID、digest、authority flag、count、pending/terminal invariant 或 revision 越界必须失败关闭并显示固定安全错误码。
13. `DEL-001/005`：Android 13 ARM64 必须覆盖 Cold Completed、Fatigue approved Completed、Fatigue rejected Partial 和七阶段 UI。
14. `DEL-004/005`：所有页面必须显示 SIMULATED/DEBUG/HARDWARE NOT ACCESSED；不得声明 production Client2、车辆回读或目标硬件验收。

当前 `simulated_scenario_client2_wired=true`、`simulated_scenario_client_parcel_wire_verified=true`、
`simulated_scenario_projection_reducer_owned=true`、`simulated_scenario_seven_stage_ui_verified=true`、
`simulated_scenario_android13_arm64_client_verified=true`、`hmi_d4_debug_demo_control_loop_complete=true`、
`simulated_scenario_hardware_effect_dispatch_enabled=false`、`simulated_scenario_approval_authority_available=false`、
`simulated_scenario_production_registered=false`、`scenario_execution_enabled=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P4-D4e`。tracking：`DEV-105`、
`ISSUE-022/026/030/033`。

## 98. P5 Android 13 ARM64 Tool/Skill/Memory probe acceptance trace

1. `S2-TOL-001`：P5-W01..W05 必须分别在 API 33 ARM64 debug Activity 中验证 manifest、registry/resolver、rule solver、
   built-in executor boundary 与 Skill package verifier，不能用 host JVM 结果代替。
2. `S2-MEM-001/S2-MDL-001`：P5-W06..W10 必须验证 Working/Profile/Episodic Memory、ContextBudget 与 Memory consent；
   ContextBudget fixture 必须确定地产生 summarize/truncate/drop 各一次。
3. `S2-SAF-001`：所有 probe 必须保持 production authority、Runtime wiring、Vehicle/NPU/Driver-HAL 和 hardware access 为 false。
4. `S2-OBS-001`：统一 installer 必须检查 fresh completion marker、每个功能 marker 和 false-authority marker。
5. `DEL-001/004`：验收目标必须是 Android API 33、ARM64；installer 必须完成 Runtime/Demo 全安装回归。
6. `DEL-005`：提交证据不得包含 serial、model、fingerprint、签名材料、raw log、用户/模型文本、memory/token 或车辆 payload。
7. debug Activity 通过只允许设置对应 `*_android13_arm64_verified=true`，不得设置量产 Tool/Memory authority 或目标硬件资格。

当前 `p5_android13_arm64_probe_acceptance_complete=true`、`p5_probe_module_count=10`、
`device_identity_redacted=true`、`production_tool_authority_published=false`、
`production_memory_authority_published=false`、`production_runtime_wired=false`、`driver_hal_accessed=false`、
`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`。tracking：`DEV-106`、
`ISSUE-036..045`。

## 99. P6 Android 13 ARM64 Event/Proactive/Context probe acceptance trace

1. `S2-EVT-001`：P6-W01/W02 必须在 API 33 ARM64 上验证 typed topic、append-before-notify、bounded replay 和
   critical event no-silent-drop；通过不等于 durable Event middleware 已发布。
2. `S2-SCN-001/S2-CTX-001`：P6-W03/W05 必须验证 Trigger 的 window/debounce/cooldown 失败关闭，以及三个
   build-owned Context source 的 allowlist、freshness、quality 与 provenance；不得读取真实 VehicleProperty。
3. `S2-UX-002/S2-TRG-002`：P6-W06 必须验证 parked full card、moving minimal、merge/replay、dismiss cooldown 和
   PARKED-only never-ask；UI 仍是 process-local projection。
4. `S2-SAF-001`：P6-W04 的 grant 必须绑定 owner/scenario/capability/zone/risk/TTL，HIGH/CRITICAL 通用授权失败关闭，
   `effect_dispatch_authorized=false` 保持不变。
5. `S2-OBS-001`：统一 installer 必须检查六个 completion marker、各模块功能 marker 和全部 false-authority marker。
6. `DEL-001/004`：验收目标固定 Android API 33、ARM64，并在六个 probe 后执行 Runtime/Demo 完整安装回归。
7. `DEL-005`：提交证据只允许 boolean/count/schema；不得包含 serial、model、fingerprint、raw log、用户/模型文本、
   Context scalar、车辆 payload 或授权材料。

当前 `p6_android13_arm64_probe_acceptance_complete=true`、`p6_probe_module_count=6`、
`device_identity_redacted=true`、`production_event_middleware_published=false`、
`production_trigger_runtime_wired=false`、`production_proactive_authority_published=false`、
`production_context_source_registry_published=false`、`production_active_suggestion_source_wired=false`、
`production_runtime_wired=false`、`driver_hal_accessed=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`。tracking：`DEV-107`、`ISSUE-031/046`。

## 100. P7 Android 13 ARM64 Model/Router/Evaluation probe acceptance trace

1. `S2-MDL-001`：P7-W01..W05 必须在 API 33 ARM64 上验证 digest-only request/result、fixed provider registry、policy route、
   debug-only local provider lifecycle 和 structured output validation；不得把 probe fixture 解释为生产模型调用。
2. `S2-MDL-001/S2-OBS-001`：P7-W06 必须验证 12-case synthetic corpus、1000 intent permille、0 unsafe/invalid/fallback 和
   digest/revision binding；不得声明真实模型质量或保存 evaluation content。
3. `S2-MDL-001/NV-G-004`：P7-W07 必须验证 request/route/policy/resource binding、foreground priority 和 thermal fail-closed；
   resource snapshot 仍由 fixture 提供，不读取系统 thermal 或 NPU telemetry。
4. `S2-SAF-001`：Router/output/admission 只产生 metadata/proposal/queue decision，`provider_invoked=false`、
   `model_invoked=false`，不得授予 Graph/Effect action authority。
5. `S2-OBS-001`：统一 installer 必须检查七个 completion marker、功能 marker 和 network/NPU/hardware/production false marker。
6. `DEL-001/004`：验收目标固定 Android API 33、ARM64，并在七个 probe 后执行 Runtime/Demo 完整安装回归。
7. `DEL-005`：证据不得包含 prompt、raw model output、用户/车辆 payload、设备身份、签名材料或 raw log。

当前 `p7_android13_arm64_probe_acceptance_complete=true`、`p7_probe_module_count=7`、
`device_identity_redacted=true`、`production_model_provider_published=false`、`production_model_router_wired=false`、
`production_inference_enabled=false`、`production_model_output_runtime_wired=false`、
`production_evaluation_authority_published=false`、`production_resource_snapshot_provider_wired=false`、
`production_runtime_wired=false`、`provider_invoked=false`、`model_invoked=false`、`network_accessed=false`、
`npu_accessed=false`、`driver_hal_accessed=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`。tracking：`DEV-108`、`ISSUE-024/044`。

## 101. P9 Android 13 ARM64 hardening/release debug probe acceptance trace

1. `S2-OBS-001/S2-REL-001`：P9-W01/W02 只能验证 synthetic budget report 与 18-case matrix 合同可在 API 33 ARM64
   执行；`performance_budget_target_measurement_complete=false`、`stability_target_72h_complete=false` 必须保持不变。
2. `S2-SAF-001`：P9-W03c 只验证 AIDL/Parcelable boundary inventory 和固定 hostile-input 路径；coverage-guided fuzz、
   Binder UID spoof 与 package signature qualification 不得由 debug probe 推导。
3. `S2-MEM-001/S2-SAF-001`：P9-W04c 只输出 21-key redacted audit projection；owner policy、repository mutation 和
   production lifecycle 不得启用。
4. `S2-REL-001`：P9-W05b 只读三包 metadata/signer relation count；不得输出包名、证书、signer bytes、设备身份或执行
   install/uninstall/rollback。
5. `S2-UX-002/S2-EFF-001`：P9-W06b 只验证 27-key safety contract projection；不得读取真实 driving state、授予 Effect
   或声明 OEM safety acceptance，`driver_safety_android13_arm64_verified=false` 必须保持不变。
6. `S2-OBS-001/S2-REL-001`：P9-W07b 只验证 build-owned field diagnostics preflight；八类 target category 未全部执行，
   report 不得 admit 或自动上传。
7. `DEL-001/004/005`：聚合验收固定 API 33、ARM64、7 个 probe-specific marker、identity redaction 与完整安装回归；
   不允许 raw log、target input、用户/模型/记忆/车辆 payload 或签名材料进入仓库证据。

当前 `p9_android13_arm64_probe_acceptance_complete=true`、`p9_probe_module_count=7`、
`performance_budget_target_measurement_complete=false`、`stability_target_72h_complete=false`、
`security_coverage_guided_fuzz_complete=false`、`privacy_owner_policy_approved=false`、
`production_signer_owner_approved=false`、`driver_safety_android13_arm64_verified=false`、
`field_diagnostics_target_category_execution_complete=false`、`release_evidence_target_report_admitted=false`、
`driver_hal_accessed=false`、`hardware_accessed=false`、`production_ready=false`、`target_hardware_validated=false`。
tracking：`DEV-109`、`ISSUE-048..053`、`ISSUE-029/030`。

## 102. P9-W03d Android Binder identity evidence trace

1. `S2-SAF-001`：调用身份必须在 Runtime Binder transaction 内由 `Binder.getCallingUid()` 获取；调用方传入的 UID、包名或 signer
   只能作为待验证值，不能成为授权身份来源。
2. `S2-SAF-001/S2-TOL-001`：UID 必须通过 `AndroidCallerIdentityResolver` 解析为全部可见 package 与 current APK signer SHA-256；
   unresolved、package spoof、signer spoof 或 shared-UID 混淆必须失败关闭。
3. `S2-SAF-001`：设备探针必须是 debug-only、显式 component、`BIND_RUNTIME` signature permission 保护；release source/manifest
   不得包含探针 Service 或 AIDL。
4. `S2-OBS-001/DEL-005`：接口与测试输出只允许 boolean marker；不得输出 raw UID、package、certificate bytes、signer digest、serial、
   fingerprint、日志或业务 payload。
5. `DEL-001/004`：正向证据必须在 Android API 33、ARM64、Runtime/SDK 不同 UID 的跨进程 Binder 调用中完成，并同时覆盖 UID、package、
   signer spoof 负例。
6. `DEL-004`：测试端必须通过 PackageManager 独立计算自身 installed current signer SHA-256；不得复用 Runtime 返回的身份材料。
7. `S2-SAF-001`：debug same-signer 验证不得提升为 production signer、release admission、coverage fuzz、Runtime wiring 或目标硬件资格。

当前 `security_identity_device_probe_verified=true`、`security_distinct_app_uids_verified=true`、
`security_binder_calling_uid_spoof_android_verified=true`、
`security_package_signature_cryptographically_verified=true`、`security_same_signer_debug_binding_verified=true`、
`security_production_signer_verified=false`、`security_coverage_guided_fuzz_complete=false`、
`security_runtime_wired=false`、`hardware_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-111`、`ISSUE-050`。

## 103. P9-W03e Android task callback replay evidence trace

1. `S2-SAF-001`：生产 task callback 必须绑定 Runtime 返回的 task ID；cross-task、unknown schema、非法 state/progress 必须失败关闭。
2. `S2-TOL-001`：`TaskUpdate.sequence` 必须严格前移；active/terminal replay 产生的 duplicate/stale update 不得再次投影到 HMI。
3. `S2-TOL-001/S2-SAF-001`：同 owner 相同 request/key 返回同 task；冲突 payload 必须在 callback attach 前拒绝；每个 callback 最多一个终态。
4. `S2-SAF-001`：不同 Android UID 使用同一 key 不得借用对方 task 或收到对方 callback；owner isolation 继续由 Binder trusted identity 派生。
5. `S2-OBS-001/DEL-005`：证据只能输出 boolean marker，不输出 task ID、UID、signer、serial、raw log 或用户/model/vehicle payload。
6. `DEL-001/004`：上述四例必须在 API 33 ARM64 的真实跨进程 typed Binder 上执行；host test 不能替代。
7. debug test principal 只能存在于 debug resource overlay，main/release policy 必须保持 absent。

当前 `security_task_callback_replay_android_verified=true`、`security_callback_sequence_replay_suppressed=true`、
`security_callback_terminal_replay_unique=true`、`security_idempotency_conflict_callback_silent=true`、
`security_cross_uid_callback_owner_isolation_verified=true`、`security_debug_test_principal_release_excluded=true`、
`security_coverage_guided_fuzz_complete=false`、`security_production_signer_verified=false`、`hardware_accessed=false`、
`production_ready=false`、`target_hardware_validated=false`、`implementation_stage=P9-W03`。tracking：`DEV-112`、`ISSUE-050`。

## 104. P9-W03f bounded parser robustness trace

1. `S2-SAF-001`：checkpoint、scenario manifest 和 Tool input 三类不可信输入边界必须分别进入真实 production parser/validator；测试 target
   不得复制 production 解析逻辑。
2. `S2-SAF-001/S2-TOL-001`：每类入口只允许其文档化 typed rejection；unchecked exception、Error、hang、OOM 或 crash artifact 必须失败。
3. `S2-OBS-001`：证据只允许输出预算、执行量、覆盖率、入口计数、崩溃计数和 boolean；不得输出 raw input、用户/model/vehicle 数据。
4. `DEL-001/004`：Jazzer 版本、默认/最大预算、单输入 timeout、input/RSS 上限、seed 数和三类 surface 必须由 machine contract 与 checker 固定。
5. `DEL-005`：默认 20 秒 campaign 必须可由单命令复验，且执行量/覆盖率/各入口调用均大于零、崩溃数为零。
6. 本 host campaign 不访问网络、ADB、Vehicle/NPU、Driver/HAL；不能替代 Android Binder/Parcel、目标长预算或量产安全 owner 证据。

当前 `security_parser_robustness_engine_pinned=true`、`security_parser_robustness_budget_defined=true`、
`security_parser_robustness_surface_count=3`、`security_parser_robustness_seed_count=6`、
`security_parser_robustness_host_campaign_verified=true`、`security_coverage_guided_fuzz_complete=false`、
`security_production_signer_verified=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。tracking：`DEV-113`、`ISSUE-050`。

## 105. P9-W03g suspended external security evidence interface trace

1. `S2-SAF-001`：仓库不得包含 W03f 第三方执行引擎、Gradle execution task、Java target/test、seed 或 shell runner。
2. `S2-OBS-001/DEL-005`：只保留外部证据接口；允许字段限定为 release/source/archive digest、非秘密设备 alias、批准 profile、result digest、
   internal reference 和 privacy boolean。
3. `S2-SAF-001/S2-OBS-001`：raw identity、signing material、credential、raw input/log、user/model/memory/vehicle payload 禁止进入仓库。
4. `DEL-001/004`：接口必须声明 repository executor、Android/native component、network transport 和 automatic execution 全部不存在。
5. 用户范围决策：不申请 trusted-access permission；该需求保持 suspended，外部 owner 未提供批准证据前不得恢复执行实现或提升状态。

当前 `security_external_evidence_interface_defined=true`、`security_requirement_suspended=true`、
`security_test_implementation_present=false`、`security_test_execution_enabled=false`、
`security_external_evidence_admitted=false`、`security_coverage_guided_fuzz_complete=false`、
`security_production_signer_verified=false`、`production_ready=false`、`target_hardware_validated=false`、
`implementation_stage=P9-W03`。tracking：`DEV-114`、`ISSUE-050`。

## P5-R1 Debug Runtime Composition Requirements

1. `S2-SCN-001/S2-GRF-001`：只有 P4-R1 debug Orchestration 可调用组合边界；release backend 必须保持失败关闭。
2. `S2-TOL-001`：Tool 必须按每次 admission 的新鲜 health snapshot 解析，并绑定 contract/signer/artifact/deadline/idempotency/audit digest。
3. `S2-TOL-001/S2-SAF-001`：Skill 只完成 compiled-in governance admission，`dispatchAllowed` 必须为 false。
4. `S2-MEM-001`：Working Memory 只保存 64-byte composition digest，终态和 Service close 必须清理并覆零 retained payload。
5. `S2-MEM-001/S2-MDL-001`：ContextBudget 只处理受信 metadata；本阶段不得接收文本、调用 tokenizer/summarizer/model。
6. `S2-SAF-001`：Profile/Episodic Memory 写入、网络、NPU、Vehicle、Driver/HAL 必须为 false。
7. `S2-OBS-001/DEL-004`：Node/Effect evidence 必须绑定 composition digest，probe/contract 必须区分 debug wiring 与 production authority。

当前 `runtime_composition_debug_wired=true`、`production_runtime_composition_wired=false`、
`runtime_composition_android13_arm64_verified=false`、`implementation_stage=P5-R1`。tracking：`DEV-117`、`ISSUE-036..044`。

## P6-P7-R1 Debug Decision Composition Requirements

1. `S2-CTX-001`：debug backend 必须只消费 allowlisted typed Context Adapter 输出摘要；Cold 可用 SIMULATED signal，Fatigue
   DMS 缺口必须标记 stub，不得伪造 production provenance。
2. `S2-EVT-001`：Trigger 必须满足三样本/持续窗口并只产生 suggestion；Event 只允许受信 topic、摘要 payload、有界队列和 owner cancel。
3. `S2-SAF-001`：无 active consent grant 时必须返回 `NO_ACTIVE_GRANT`，不得授权 Effect；显式 HMI simulation request 不得被表述为主动授权。
4. `S2-MDL-001`：Model Request 必须经 Registry health 与 Policy Router；本阶段只允许 deterministic contract-test provider，禁止网络/NPU。
5. `S2-OBS-001`：不得接受或保存用户/模型明文；Context、suggestion、route、output、event 和 P5 composition 只以 SHA-256 绑定。
6. `DEL-001/003/004/005`：必须有 JVM test、DUMP-protected Android probe、machine contract、release absence 和 CI checker。

当前 `decision_composition_debug_wired=true`、`decision_composition_android13_arm64_verified=false`、
`production_decision_composition_wired=false`、`implementation_stage=P6-P7-R1`。tracking：`DEV-118`、`ISSUE-024/031/044/046`。

## P4-R2 Client2 Orchestration V1 migration requirements

1. `APP-004/XSC-001/005/006`：Client2 必须只通过公开 SDK/Binder；不得直接调用 Graph、Effect adapter、NPU 或车辆接口。
2. `S2-SCN-001/S2-GRF-001`：自然场景必须先创建 owner Session，再以该 Session ID 读取/启动 Orchestration；恢复必须 read-before-start。
3. `S2-EFF-001/S2-SAF-001`：批准响应必须绑定 session/approval/projection digest，且 response 不等于 trusted grant；release 无 authority 时失败关闭。
4. `S2-HMI-003/006`：Plan、Graph、Effect、readback 只能来自经 SDK 和 typed Plan 双重校验的 snapshot，不能从按钮点击推断成功。
5. `DEL-001/003/004`：Client2 只能保留一个当前执行投影路径；历史 `ISimulatedScenarioRuntime` client、复制 Parcelable 和第二 AIDL 生成项必须删除。
6. `S2-GRF-001/S2-EFF-001`：持久恢复键必须兼容 typed Plan 的可选幂等键；Plan deadline 必须绑定 durable Session；approval digest 必须使用稳定投影时间戳；readback 必须按 capability 的 verify 节点绑定。

当前 `client2_orchestration_sdk_v1_wired=true`、`client2_session_before_orchestration=true`、
`client2_orchestration_resume_read_before_start=true`、`client2_orchestration_approval_projection_bound=true`、
`client2_legacy_simulated_scenario_binder_used=false`、`client2_android13_x86_64_verified=true`、
`client2_android13_arm64_verified=false`、`implementation_stage=P4-R2`。tracking：`DEV-119`、`ISSUE-033`。

## P10-R1 Android repository software completion

所有现行 Req ID 均已分类，`unclassified_repository_requirement_count=0`。但新增 `S2-HMI-008/P4-R4`
尚未实现，因此当前 `repository_software_requirements_complete=false`、
`open_repository_software_requirement_count=1`。原完成基线只说明 P4-R4 之前的 Android SDK/Binder/Runtime、
Client2 HMI、debug composition、fail-closed contract 和证据接口状态；机器基线已升级为 schema 2。
tracking：`DEV-120/128`、`ISSUE-056`。

## P7-R2 Ollama Model Gateway Requirements

1. `S2-MDL-001/XSC-001`：上层只能依赖 `ModelProvider`；开发/量产端点必须由 build-owned profile 固定，调用方不得传 URL。
2. `S2-MDL-001`：开发端点固定为 `127.0.0.1:11434/api/chat`，只允许通过显式 ADB reverse 到 WSL Ollama。
3. `S2-MDL-001`：量产端点固定为 `169.254.208.110:11434/api/chat`；P7-R2 只冻结合同，不启用 release Provider。
4. `S2-SAF-001`：响应必须校验 HTTP/model/done、strict JSON、scenario、reply bound、action count 和 action allowlist；模型不得授权 Effect。
5. `S2-SAF-001/XSC-005/006`：默认禁止明文；debug 只放行 `127.0.0.1`，release 只放行 `169.254.208.110`；禁止 redirect 和任意 host override。
6. `S2-OBS-001/XSC-006`：prompt/response 不得写日志；reply 只允许由 SDK debug source set 中独立、owner-scoped
   `ICentralBrainDevelopmentModelProjection` 瞬时投影，不得修改冻结 Orchestration V1，不得进入 Room 或 Client2 checkpoint；release 不得发布该 Service。
7. `DEL-001/003/004/005`：必须有 JVM、静态门禁、机器合同、APK 构建和 Android 13 ARM64 实际模型调用证据。
8. 模型 action 在 P7-R2 中是 proposal；Scenario Catalog/Compiler/Policy/Safety 仍拥有 Plan 和 Effect 权限。

当前 `development_wsl_gateway_implemented=true`、`development_android13_arm64_verified=true`、
`production_endpoint_contract_defined=true`、`production_provider_implemented=false`、
`production_npu_validated=false`、`implementation_stage=P7-R2`。P10-R1 是此前范围基线；新增 P7-R3 已明确归类为
`PLANNED`，不再使用“当前全部开发完成”描述扩展后的范围。tracking：`DEV-121`、`ISSUE-024/044`。

## P7-R3-OC2 OpenClaw Target Gateway Requirements

1. `S2-MDL-001/XSC-001`：目标地址固定为 `169.254.208.110:18789`，调用方不得覆盖 host、port、path 或协议版本。
2. `S2-MDL-001`：`/chat` 仅为控制 UI；模型 Runtime 必须使用 WebSocket root、protocol 3、challenge/connect、
   `chat.send`、`chat.abort` 和 bounded `chat.history`。
3. `S2-SAF-001`：目标 Provider assurance 为 `TARGET_INTEGRATION`，不得声明 production eligible、hardware backed、
   direct NPU access、action authority 或 Effect authority。
4. `S2-SAF-001`：响应必须 strict UTF-8/JSON、exact keys、scenario binding、reply/action bound 和 action allowlist。
5. `S2-OBS-001/XSC-006`：按维护者明确指令，完整控制页 URL/token 固化在 `OpenClawEndpointConfig`；凭据会进入源码、Git
   历史和 APK，必须标记 `CLOSED_TARGET_TEST_ONLY` 且不得写日志、Room、checkpoint 或测试输出。旧 DUMP 注入 Activity/Store 删除。
6. `DEL-003/004/005`：必须有 JVM、static contract、target APK、API 33 ARM64 Runtime probe 和 Client2 projection evidence。

当前 `openclaw_target_integration_implemented=true`、`openclaw_target_android13_arm64_verified=true`、
`fixed_target_credential_active=true`、`latest_target_connectivity_verified=true`、
`target_multimodal_frontend_bound=true`、`target_multimodal_verified=true`、
`target_ipv4_configuration_persistent=false`、`production_provider_qualified=false`、
`production_ready=false`、`target_hardware_validated=false`；stage `P7-R3-OC2`。2026-07-24
生产板通过测试会话临时 IPv4 完成目标 protocol v3 多模态和 Client2 购物/路线闭环。

## P4-R3 Voice-first live cockpit HMI requirements

1. `APP-004/S2-HMI-006/007`：Client2 中央大脑面板只保留固定场景触发和一个有界实时滚动调用链，不再把手动
   HVAC/Seat 调参、多页 tab、工程抽屉作为驾驶员主交互。
2. `S2-MDL-002/S2-SAF-001`：Cold/Fatigue prompt 必须声明汽车座舱、驾驶员服务目标、当前座舱温度、默认 HVAC
   设定、疲劳上下文、`UI_SIMULATION_ONLY` 和 `SAFETY_INTERFACE_RESERVED`；响应必须 strict JSON、场景绑定、动作白名单。
3. `S2-MDL-002/S2-EFF-001`：模型 action 只决定固定场景 Plan 中哪些可选 capability 被保留；不得新建 capability、
   绕过 Scenario Catalog/Policy/Safety 或直接调用 adapter。
4. `S2-OBS-002`：Client2 必须按 Runtime、Intent、Context、Model、Plan、Policy、Graph、Safety、Effect、Readback
   顺序增量投影，模型等待期间立即显示 RUNNING，最多保留 32 行并自动滚动。
5. `S2-HMI-004/007`：无车身通信时，HVAC 温度/风量和驾驶席角度只通过 Android 应用层 HMI 动画反馈；Cold
   的 26.5°C -> 28.0°C 必须显示在 RenderService 的 Unity 原生双区温区，禁止 Android 温度浮层；Fatigue
   风量 1->3 且座椅 15->30 度，靠背必须向后展开。Seat 只在场景实际包含座椅动作时显示。
6. `S2-SAF-001`：所有动画持续标记 `SIMULATED`、未连接车辆总线且不构成实车证据；security executable 保持挂起，
   demo 自动继续不得授予 production authority。

当前 `voice_first_hmi_implemented=true`、`cockpit_context_prompt_bound=true`、
`model_action_plan_binding_verified=true`、`live_pipeline_trace_verified=true`、
`simulated_actuator_feedback_verified=true`、`vehicle_bus_accessed=false`、`security_implementation_present=false`、
`production_ready=false`、`target_hardware_validated=false`。tracking：`DEV-123/124`、`ISSUE-054`。

## P4-R4 Multimodal model I/O HMI requirements

1. `APP-004/S2-HMI-003/007/008`：实时滚动运行状态必须分别显示本次真实模型请求的用户可见输入和真实模型回复；
   不得用按钮标签、固定 fixture、推测文本或阶段名称冒充模型 I/O。
2. `S2-HMI-008/S2-MDL-002`：纯文字请求直接显示文字；文字+图片请求必须在同一 `MODEL_INPUT` 项中同时显示
   文字与一张 PNG/JPEG 缩略图。文字最多 4096 code points，截断必须有可见标记。
3. `S2-HMI-008/S2-UX-002`：缩略图最大 `320dp x 180dp`，使用 `FIT_CENTER`、保持宽高比、不裁切且不把图片放大
   超过原始像素。`PARKED/IDLE` 点击缩略图后在屏幕中心显示预览，最大占屏幕宽 90%、高 85%；点击图片外区域或
   Back 退出，点击图片本身不得透传为退出。
4. `S2-UX-002/S2-SAF-001`：`MOVING_RESTRICTED/UNKNOWN_RESTRICTED/FAULT_RESTRICTED` 保留有界缩略图和限制原因，
   禁止进入居中大图预览；驾驶态限制优先于图片点击需求。
5. `XSC-001/005/006/S2-OBS-002`：UI 数据必须来自版本化 transcript+image 输入合同和模型 response 投影。
   原始系统 prompt、provider frame、token、Binder 身份、车辆 payload 不得进入 HMI。
6. `S2-SAF-001/DEL-004`：文字和解码后的图片只保存在当前 Client2 进程内存并在下一任务、替换或 Activity destroy
   时清理；不得写 Room、SharedPreferences、checkpoint、日志或 GitHub 证据。
7. 图片 decode 失败、MIME 不允许、输入摘要不匹配或模型 response 不可用时必须显示明确失败状态，不得显示旧图、
   旧回复或伪造成功。

当前只完成需求合同，尚未修改 Client2 布局、reducer、SDK/Binder 或 Runtime projection。
`model_io_hmi_implemented=false`、`frontend_multimodal_ingress_bound=false`、
`actual_model_input_projected_to_hmi=false`、`actual_model_output_projected_to_hmi=false`、
`image_center_preview_interaction_implemented=false`、`android13_arm64_model_io_hmi_verified=false`、
`repository_software_requirements_complete=false`、`production_ready=false`、`target_hardware_validated=false`；
tracking：`DEV-128/ISSUE-055/056`；stage `P4-R4-REQUIREMENT`。

## P7-R4-OCDEV Android-to-WSL OpenClaw Development Requirements

1. `S2-MDL-001/002`：debug 默认模型路径必须为真实 Android 13 -> ADB reverse -> WSL OpenClaw -> Ollama；
   deterministic provider 不得冒充本 profile 的通过证据。
2. `S2-MDL-001`：开发 endpoint 固定为 `ws://127.0.0.1:18789/`，OpenClaw protocol 固定 v4；目标 endpoint
   `ws://169.254.208.110:18789/` 和 protocol v3 必须保持独立。
3. `S2-SAF-001`：开发 v4 客户端固定为 `gateway-client/backend`，只申请 `operator.read/write`；模型输出必须继续
   通过 exact JSON、scenario binding 和 action allowlist，且不得授予 Plan/Effect authority。
4. `S2-OBS-001/002`：Android probe 只输出阶段、耗时、长度、provider/profile 和安全闭锁状态；不得记录设备身份、
   prompt、回复、token 或车辆 payload。
5. `XSC-001/005/006`：build profile、endpoint、协议、client identity、ADB bridge、机器合同和真机证据必须一致；
   不允许 caller URL override 或失败后静默回退到 stub。
6. `DEL-001/003/004/005`：必须提供一键 build/install/probe 工具，支持显式 transport ID；Client2、SDK AAR 和
   Runtime 必须同源构建后安装，并以资源 ID 触发真实 HMI 场景。验收必须同时确认模型终态、编排投影、UI 仿真
   Effect，并声明 `ethernet_validated=false`、`production_ready=false` 和 `target_hardware_validated=false`。

当前 `development_wsl_openclaw_implemented=true`、
`development_wsl_openclaw_android13_arm64_verified=true`、`external_compute_accessed=true`、
`direct_npu_accessed=false`、`driver_hal_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`。tracking：`DEV-126`、`ISSUE-024/054`；stage `P7-R4-OCDEV`。

## P7-R5-MMDEV Multimodal Development Requirements

1. `S2-MDL-001/002`：开发模型网关必须允许同一 OpenClaw `chat.send` 同时携带座舱文字和最多一张图片；模型元数据
   必须显式声明 `text,image`，不满足时失败关闭。
2. `S2-SAF-001`：图片只允许 PNG/JPEG，最大 6 MiB；必须校验 digest binding、文件名、MIME 与魔数，模型输出仍不得
   授予 Safety、Plan 或 Effect authority。
3. `S2-OBS-001/002`：日志只允许图片 MIME、字节数、SHA-256、耗时和结构化通过状态；不得记录 Base64、原图、
   prompt、完整模型回复或 credential。
4. `XSC-001/005/006`：核心 Model 合同继续使用 digest-only；图片字节只在 debug 模型网关暂存并映射为 OpenClaw
   attachment。64 KiB 预鉴权上限不得因图片通道放宽。
5. `DEL-001/003/004/005`：必须提供固定图片摘要的一键 WSL 真实模型探针和机器合同，证明文字规定输出结构、图片提供
   事实；前端相机/语音 Binder 和 Android 13 ARM64 证据未完成前必须保持开放状态。
6. `S2-MDL-001/002`：目标接口文档必须只描述 Android 车机经以太网直连
   `ws://169.254.208.110:18789/` 的生产拓扑，明确纯文字与文字+图片 `chat.send`，并区分协议实现、
   前端 Binder 接入、目标终态证据和 release qualification。

当前 `android_openclaw_multimodal_attachment_implemented=true`、
`wsl_openclaw_ollama_multimodal_verified=true`、`frontend_multimodal_ingress_bound=false`、
`android13_arm64_multimodal_verified=false`、`production_ready=false`、`target_hardware_validated=false`。
tracking：`DEV-127/ISSUE-055`；stage `P7-R5-MMDEV`。

## P4-R4 implementation conformance update

2026-07-24 已实现版本化 FD 图片输入、文字/图片 aggregate digest、owner/session/scenario 绑定、
模型图片消费证明、回复与 admitted action 投影、Client2 缩略图/居中预览/图外或 Back 退出，以及
`hvac.ventilate`/`media.pause` 到模拟 Effect 的白名单映射。非 PARKED 驾驶态拒绝大图。

当前开发输入是 APK 内固定摘要的受控座舱帧，它确实进入 OpenClaw/Ollama 请求，不是用 fixture
冒充 UI 结果；但它也不是实时摄像头。Android 13 ARM64 实测确认 `imageConsumed=true`、
Graph/Effect/Readback 完成和风量 1->3 动画。`repository_software_requirements_complete=true`，
而 `live_camera_ingress_verified=false`、`vehicle_bus_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`。详设：
[CENTRAL_BRAIN_CLIENT2_MULTIMODAL_CONTROL_LOOP.md](CENTRAL_BRAIN_CLIENT2_MULTIMODAL_CONTROL_LOOP.md)。

## P4-R6 Unity-native HVAC and seat animation requirements

1. `APP-001/004/S2-HMI-001/003/004`：Cold 结果必须由 RenderService Unity 原生温区显示；Client2
   布局和 Java coordinator 不得定义、绑定或更新驾驶席/乘员席 Android 温度 overlay。
2. `S2-HMI-001/003/S2-ADP-001`：Unity bundle 必须保留既有双区 26.5°C 状态，并以固定 path ID
   增加双区 28.0°C TextMeshPro 状态；重复构建必须幂等，不能不断复制对象。
3. `XSC-001/006/S2-HMI-005`：Client2 必须通过现有 `TuanjieView`/RenderService 输入边界触发 Unity
   Button；触屏事件必须使用 finger、touchscreen、`deviceId=-1` 和成功触屏一致的时间语义。
4. `S2-HMI-002/003/S2-UX-002`：Fatigue 的驾驶席靠背由 15 度到 30 度时，顶端必须远离坐垫，
   视觉语义为展开/后仰；不得用增加数值但向坐垫合拢的动画冒充通过。
5. `S2-SAF-001/S2-ADP-002/DEL-004`：Unity 温度和座椅动画只表示 `SIMULATED` HMI feedback；
   不得声明 Vehicle/VHAL/CAN、真实 target/readback、Driver/HAL 或量产 Safety authority 已接入。
6. `DEL-001/003/004`：必须交付可重复构建并同签的 Client2/RenderService APK、静态门禁和 Android 13
   ARM64 视觉证据；任何未在线设备必须登记为外部复测阻塞，不能从另一设备的证据推断通过。

当前 `p4_r6_repository_software_complete=true`、
`seat_recline_expansion_direction_verified=true`、
`unity_native_dual_zone_hvac_state_defined=true`、
`android_temperature_overlay_present=false`、
`testboard_android13_arm64_verified=true`、
`production_board_final_package_retest=false`、
`vehicle_bus_accessed=false`、`production_ready=false`、
`target_hardware_validated=false`。详设：
[CENTRAL_BRAIN_CLIENT2_UNITY_NATIVE_HVAC_SEAT_PATCH.md](CENTRAL_BRAIN_CLIENT2_UNITY_NATIVE_HVAC_SEAT_PATCH.md)。
