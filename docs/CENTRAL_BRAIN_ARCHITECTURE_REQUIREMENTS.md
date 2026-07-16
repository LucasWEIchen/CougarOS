# 中央大脑架构需求基线

版本：0.3
日期：2026-07-16
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
| APP-001 | 座舱 HMI | 只能经 SDK/Binder 访问 Runtime，不直连模型或车控 | Client2/Demo 已集成 |
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
| FW-S-003 | Atomic Service | 最小 HVAC/Seat/Media/Navigation 能力 | 外部阻塞 |
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
| S2-SES-001 | versioned durable Session | owner、TTL、state、idempotency |
| S2-CTX-001 | typed Context snapshot | source/freshness/trust |
| S2-TWN-001 | Vehicle Digital Twin | debug/test only，显式 simulated |
| S2-SCN-001 | versioned scenario catalog | owner、precondition、rollback |
| S2-GRF-001 | durable Agent Graph | step/checkpoint/dependency/terminal |
| S2-SAF-001 | hard safety interlock | A user confirmation cannot override this hard interlock |
| S2-EFF-001 | typed Effect lifecycle | prepare/apply/verify/compensate |
| S2-ADP-001 | adapter registry | source/profile/capability/evidence |
| S2-TOL-001 | retry/timeout/partial failure | deterministic terminal result |
| S2-MEM-001 | memory lifecycle | purpose/retention/delete/export |
| S2-EVT-001 | proactive Event trigger | consent/rate-limit/DND/policy |
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

`production_ready=false`、`target_hardware_validated=false`、
`driver_development_triggered=false`、`virtualization_development_triggered=false`。
