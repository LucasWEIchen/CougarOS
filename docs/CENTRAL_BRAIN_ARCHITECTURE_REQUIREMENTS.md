# 中央大脑架构图需求基线

版本：0.1
日期：2026-07-04
来源：`docs/assets/central_brain_architecture_source.png`

## 基线声明

用户提供的《中央大脑软件部分展开》不是概念示意图，而是本项目的架构需求基线。后续开发计划、接口设计、代码实现和验证都必须追踪到该图中的层级、模块和接口。

任何未按图实现的内容必须进入 `docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md`；任何图中表达不清、边界重叠、工程上有风险或需要用户确认的内容必须进入 `docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md`。

## 用户明确约束

以下约束优先级高于此前计划：

1. 虚拟化层不需要被开发。Hypervisor、ASIL/QM 隔离、跨 VM 共享内存与安全域通信只作为架构接口、部署约束和适配说明记录，不产生代码开发量。
2. 驱动层仅在当前 Android/Linux 环境不能满足需求时新增开发量。驱动接口支持范围必须明确写入文档，包括 NPU/GPU/Camera/Audio/ETH 等接口边界。
3. 图中带黄色小太阳标记的组件会在多个 SoC 中出现，必须按跨 SoC 可移植平台组件设计。
4. 开发以 Android 环境为主，但交付时必须同时提供 Linux 版本。
5. 交付对象是使用 Android 和 Linux 系统的座舱域软件工程师，因此交付物必须包括接口说明、集成路径、验证命令和平台差异说明。

## 所有权颜色

| 颜色 | 图中含义 | 项目处理 |
| --- | --- | --- |
| 蓝色 | 展锐负责 | 默认按平台/底座能力实现或预留接口 |
| 肤色 | 芯片原有 | 默认按底层 OS/芯片能力依赖，不在 App 侧重造 |
| 绿色 | 生态合作 | 默认按 adapter/plugin/provider 接入 |
| 黄色 | 客户开发 | 默认按上层应用/业务服务实现 |

## 分层基线

| 层级 ID | 图中层级 | 需求约束 | 当前状态 |
| --- | --- | --- | --- |
| L1 | 应用层 | 承载客户开发的 Apps/Services，以及平台提供的 AI SDK | 部分原型 |
| L2 | Framework 层 | 必须包含 Uni Info Bus 语义接口和 SOA 服务入口 | 文档化，未完整实现 |
| L3 | Native 层 | 必须包含 AIOS Kernel、Signal/Service/Runtime/Policy/Model adapters，以及 Runtime & Governance、Protocol Binding | mock 后端仅覆盖极小子集 |
| L4 | Kernel & HAL 层 | 必须依托文件系统、网络、内存、Drivers、Libs、HAL、Safety Runtime、调度/中断/系统调用；新增开发仅限当前环境缺口 | 驱动接口矩阵 + NPU runtime interface 初版 + `/native/driver-gaps` |
| L5 | 虚拟化层 | 必须体现 Hypervisor、ASIL/QM 隔离、跨 VM 共享内存与安全域通信的接口约束；不开发虚拟化功能 | `CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md` 初版 |
| L6 | 硬件层 | 基线硬件为 UniSOC Automotive-solution，并扩展接入外置 PCIe NPU | 未实现，仅 mock |

## 跨 SoC 黄色小太阳组件

图中带黄色小太阳标记的组件按跨 SoC 复用平台组件处理。这些组件不能绑定单一 SoC、单一 Android 版本或单一 Linux 发行版。

| Req ID | 图中组件 | 所属层级 | 跨 SoC 要求 | Android 交付 | Linux 交付 |
| --- | --- | --- | --- | --- | --- |
| XSC-001 | AI SDK | 应用层 | SDK API 稳定，底层 runtime 可替换 | Android Binder/AIDL contract sample + Console `planAgentTaskJson`/`executeAgentTaskJson`/`invokeSkillJson`/`queryMemoryJson` debug path + `/ai/sdk/capabilities` + `/agent/plan` + `/agent/execute` + Skill/Memory contract mock | Linux CLI/IPC active sample + `/ai/sdk/capabilities` + `/agent/plan` + `/agent/execute` + Skill/Memory contract mock |
| XSC-002 | Uni Info Bus 语义接口 | Framework 层 | 语义对象和 contract 跨 SoC 一致 | Android client/API | Linux client/API |
| XSC-003 | SOA 服务入口 | Framework 层 | 服务目录、契约、安全状态跨 SoC 一致 | Android service/client + Binder `getServiceContractsJson` | Linux daemon/client + CLI/IPC/gRPC `service-contracts`/`soa.contracts.get`/`GetServiceContracts` |
| XSC-004 | AIOS Kernel | Native 层 | Agent/Model/Tool/Memory/Safety 核心可移植 | Android native service adapter + Console `Driver Gaps` 调试入口调用 `getDriverHalGapsJson` contract visibility | Linux service adapter；adapter registry 初版；`driver-gaps` CLI 可查 Driver/HAL backlog |
| XSC-005 | Uni Info Bus Runtime & Governance | Native 层 | Registry/Discovery/Schema/QoS/Policy/Lifecycle/Audit 可移植 | Android runtime integration + Console `Precheck` 调试入口调用 `precheckGovernanceJson` contract + Binder `getGovernanceBackendContractJson`/`getGovernanceMigrationCheckJson`/`getGovernanceDeploymentPlanJson` 目标契约、迁移检查与部署计划可见性 | Linux runtime integration；JSONL audit persistence sample；QoS fixed-window active prototype；`/governance/precheck`/`governance-precheck`；`/governance/backend-contract`/`governance-backend-contract`；`/governance/migration-check`/`governance-migration-check`；`/governance/deployment-plan`/`governance-deployment-plan`；Linux shared governance daemon 提供 precheck/runtime/audit diagnostics；IPC/gRPC 通过 shared governance client 复用 precheck envelope、runtime/audit diagnostic envelope + fallback |
| XSC-006 | Uni Info Bus Protocol Binding | Native 层 | 协议 binding 可按平台启停，但上层语义不变 | Console 已绑定 Binder service sample；system/privileged service integration note 初版；Binder contract 暴露 shared governance backend target、migration readiness 和 deployment plan；REST 仍为 service 上游 prototype binding | REST active prototype + Unix socket IPC active sample with shared governance client precheck/runtime/audit direct diagnostics/backend contract/migration/deployment visibility + Linux gRPC/RPC JSON contract sample with same diagnostics + systemd hardening sample + Linux package profile check，MQTT/SOME-IP/DDS 计划态 |

## 交付对象与平台要求

| Req ID | 要求 | 说明 | 当前状态 |
| --- | --- | --- | --- |
| DEL-001 | Android 主开发路径 | 优先在 Android 模拟器/Android 设备验证 App、SDK、服务接口 | Android Console 已通过 Binder client 调用 Uni Info Bus State、AI SDK/Agent plan、Agent execute、Skill invoke 与 Memory query contract mock；Android system/privileged service integration note 初版 |
| DEL-002 | Linux 同步交付路径 | 每个核心接口需要 Linux 版示例、CLI 或 daemon 集成说明 | CLI、IPC/gRPC daemon 样例 + systemd hardening check + package profile check |
| DEL-003 | 座舱域工程师文档 | 交付给 Android/Linux 座舱软件工程师，必须给出集成步骤、接口、验证命令 | Android system service integration note + Linux 部署文档初版 |
| DEL-004 | 平台差异说明 | Android 与 Linux 的 IPC、权限、服务部署、日志、驱动接口差异必须记录 | `CENTRAL_BRAIN_PLATFORM_DELTA.md` + Android system service integration note + Linux systemd sample + Linux unit hardening/package profile constraints |
| DEL-005 | 驱动接口支持文档 | 明确当前环境已有能力、缺口、新增开发边界和 mock/fallback | 初版 + Driver/HAL gap backlog contract |

## L1 应用层需求

| Req ID | 图中模块 | 所有权 | 内容 | 实现要求 | 当前状态 |
| --- | --- | --- | --- | --- | --- |
| APP-001 | 座舱 Apps | 客户开发 | HMI/车控/场景/... | Android 前端必须支持座舱 HMI 与车控场景入口 | console 原型仅健康/推理 |
| APP-002 | 座舱服务 | 客户开发 | 音频/蓝牙/车控/... | 应作为 Business/Foundation/Atomic services 暴露 | 未实现 |
| APP-003 | Agent Apps | 客户开发 | 车控/座舱/诊断/导航/... | Agent 应通过 Tool/Permission/Action 调用底层能力 | 仅接口设计 |
| APP-004 | AI SDK | 展锐负责 | 多模态/意图/模型路由/工具规划/... | App 不应直连模型，应经 AI SDK 到 Uni Info Bus/AIOS Kernel | `/ai/sdk/capabilities`、`/agent/plan`、`/agent/execute` active contract mock；Android Console 已调用 `planAgentTaskJson`、`executeAgentTaskJson`、`invokeSkillJson`、`queryMemoryJson`；Skill/Memory contract mock 已可见；仍未实现真实 SDK/Agent 执行，偏差 DEV-003 |
| APP-005 | Cluster & TBOX | 客户开发 | 仪表/警告/TSP/OTA/远控/... | Cluster/TBOX 应独立服务域建模 | 未实现 |
| APP-006 | Cluster/TBOX 服务 | 客户开发 | Weston/GStreamer/... | 应声明显示/媒体服务边界 | 未实现 |
| APP-007 | 智驾应用 | 客户开发 | NOA/TJA/APA/... | App 只能读取/请求智驾服务，不能绕过 Safety State | 未实现 |
| APP-008 | 智驾服务 | 客户开发 | 感知/地图/位置/... | 通过 ADAS Funcware/Protocol Binding 暴露 | 未实现 |
| APP-009 | 其他应用 | 客户开发 | 诊断/标定/Trace/... | 必须接入权限、审计和 Trace | 未实现 |
| APP-010 | 其他服务 | 客户开发 | 网关管理/诊断/标定/Trace/... | 应作为受控服务，不允许直连底层 | 未实现 |

## L2 Framework 层需求

### Uni Info Bus 语义接口

| Req ID | 图中模块 | 所有权 | 内容 | 实现要求 | 当前状态 |
| --- | --- | --- | --- | --- | --- |
| FW-U-001 | Context | 展锐负责 | 车辆/用户/环境 | 必须有统一 Context API | `/context` 与 `/uib/context` mock |
| FW-U-002 | State | 展锐负责 | 服务状态查询 | 必须有服务/模型/车辆状态查询 API | Android/Linux 调用 `/uib/state` |
| FW-U-003 | Event | 展锐负责 | 事件订阅 | 必须支持订阅/发布模型 | `/uib/events/topics`、`/uib/events/publish`、`/uib/events/recent` active mock；legacy `/events/*` 兼容 |
| FW-U-004 | Action | 展锐负责 | 受控动作 | 车控/诊断/OTA 等必须经 Action + Policy | `/uib/actions/request` architecture-named active mock；legacy `/actions/request` 兼容；Android Binder/Linux IPC/gRPC contract 已映射 |
| FW-U-005 | Service | 展锐负责 | 方法调用 | 必须有统一服务调用入口 | `/service/invoke` 与 `/soa/invoke` mock |
| FW-U-006 | Tool | 展锐负责 | AI 工具 Schema | Agent 工具必须声明 schema、权限、安全状态 | `/tools` mock + `/agent/plan` 输出 policy-aware task graph；`/agent/execute`、`/skills`、`/skills/{skill_id}/invoke`、`/memory/query` contract mock 已执行 Policy/Safety State 检查并暴露 sandbox/dispatch 边界 |
| FW-U-007 | Permission | 展锐负责 | 权限检查 | 所有跨域调用必须先检查 Permission | `/permission/check` + `/soa/invoke` mock |
| FW-U-008 | 其他 | 展锐负责 | 扩展语义 | 必须有扩展机制且不可破坏核心对象 | 未实现 |

### SOA 服务入口

| Req ID | 图中模块 | 所有权 | 内容 | 实现要求 | 当前状态 |
| --- | --- | --- | --- | --- | --- |
| FW-S-001 | Business Services | 展锐负责 | 场景服务 | 按场景编排应用能力 | registry 中已有 planned business service |
| FW-S-002 | Foundation Services | 展锐负责 | 复用能力 | 账号、配置、时间、权限等公共能力 | registry 中已有 foundation service |
| FW-S-003 | Atomic Services | 展锐负责 | 最小能力 | 最小车控/信号/诊断能力 | registry 中已有 vehicle-state atomic service |
| FW-S-004 | Service Contract | 展锐负责 | IDL/Schema | 所有服务必须有 contract 和版本 | JSON contract + runtime registry + `/soa/contracts` contract visibility |
| FW-S-005 | Safety State | 展锐负责 | 降级/互锁 | 必须作为服务入口的强制检查项 | `/soa/invoke` 强制 policy/lifecycle precheck |
| FW-S-006 | 其他 | 展锐负责 | 扩展服务 | 必须纳入 registry/discovery/schema/policy | 未实现 |

## L3 Native 层需求

### 功能与适配模块

| Req ID | 图中模块 | 所有权 | 内容 | 实现要求 | 当前状态 |
| --- | --- | --- | --- | --- | --- |
| NV-F-001 | AIOS Kernel | 展锐负责 | Agent/Model/Tool/Memory/Safety/... | AI/Agent 核心不能仅在 App 或后端散落实现 | `native_adapters.py` contract mock + `agent-task-planner`/`agent-task-executor`/`skill-registry`/`memory-query` registry entries + `/agent/plan`、`/agent/execute`、Skill/Memory contract mock |
| NV-F-002 | Sensor/Actuator | 展锐负责 | Camera/Radar/USS/IMU/Mic/... | 传感器/执行器统一适配 | 未实现 |
| NV-F-003 | Service Adapters | 展锐负责 | Signal Map/ECU Proxy/Impl/... | 业务服务到 ECU/Signal 的 adapter | SOA Service Adapter registry 初版 |
| NV-F-004 | Vehicle/Body Signal | 生态合作 | BCM/HVAC/Seat/Door/Light/... | 车辆/车身信号按合作生态接入 | mock VSS snapshot + adapter boundary |
| NV-F-005 | ECU Proxy / Signal Adapter | 生态合作 | DBC/ARXML/... | CAN/Ethernet 信号需 DBC/ARXML 映射 | adapter boundary，待真实 DBC/ARXML |
| NV-F-006 | Data/Time Sync | 展锐负责 | TSN/PTP/Frame Meta/... | 高频数据必须有时间同步和帧元数据 | 未实现 |
| NV-F-007 | Connected Funcware | 生态合作 | TBOX/V2X/OTA/Diag/... | 互联功能软件经 adapter 接入 | 未实现 |
| NV-F-008 | SOA Service Runtime | 展锐负责 | 服务容器/状态机/Impl/... | 服务生命周期和状态机运行时 | SOA Service Adapter active prototype |
| NV-F-009 | Security/Policy Adapter | 生态合作 | ASIL/QM/Zone/... | 安全域、权限、区域策略适配 | Policy adapter active prototype + 虚拟化约束 |
| NV-F-010 | ADAS Funcware | 生态合作 | Perception/Fusion/Scene/... | 智驾能力通过 ADAS adapter 接入 | 未实现 |
| NV-F-011 | Model Runtime Adapter | 展锐负责 | GPU/NPU/Cloud/... | 模型运行时必须抽象 GPU/NPU/Cloud | mock NPU runtime adapter boundary + `CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md` |
| NV-F-012 | 其他 | 展锐负责 | Trace/Logging/Metric/... | 原生层可观测性必须平台化 | 未实现 |

### Uni Info Bus Runtime & Governance

| Req ID | 图中模块 | 所有权 | 内容 | 实现要求 | 当前状态 |
| --- | --- | --- | --- | --- | --- |
| NV-G-001 | Registry | 展锐负责/生态合作 | 服务注册 | 服务必须注册后被发现和调用 | `runtime_governance.py` service catalog + `/soa/services`；Linux shared governance daemon 可直接查询 `governance.runtime.get`，且 Linux IPC/gRPC runtime diagnostic path 优先复用该 socket |
| NV-G-002 | Discovery | 展锐负责/生态合作 | 服务发现 | 调用方不能硬编码服务位置 | `/soa/invoke` 通过 runtime discovery precheck；`/governance/precheck` 可只检查服务发现结果而不调用服务 |
| NV-G-003 | Schema/IDL | 展锐负责/生态合作 | 契约管理 | 契约必须版本化和校验 | JSON contract + registry contract metadata + `/soa/contracts` service contract visibility |
| NV-G-004 | QoS | 展锐负责/生态合作 | 优先级/限流 | 车控/智驾/AI 请求必须有优先级与限流 | `/soa/invoke` 已执行单进程 fixed-window QoS active prototype；`/governance/precheck` 默认以 `consume_qos=false` 返回诊断决策；`/governance/backend-contract` 固定未来共享治理后端的 QoS precheck 替换规则；`/governance/migration-check` 固定替换时不得在各 transport 复制 QoS 逻辑；`/governance/deployment-plan` 固定 Android/Linux/gRPC 部署形态中 Policy/QoS 不复制的约束；Linux IPC/gRPC 对 `soa.service.invoke` 可通过 shared governance client 调用共享 governance daemon 执行 QoS precheck，不可用时回退本地 precheck；Linux IPC/gRPC runtime diagnostic path 可直接查询 shared governance daemon 的 runtime/QoS 状态；仍未覆盖量产多进程/多协议限流 |
| NV-G-005 | Policy | 展锐负责/生态合作 | 权限/安全 | 所有 Action/Tool/Service 必须经 Policy | `/policy/evaluate` + `/soa/invoke` + `/governance/precheck` active prototype |
| NV-G-006 | Lifecycle | 展锐负责/生态合作 | 启动/升级/降级 | 服务和模型必须有生命周期状态 | `/soa/invoke` 拒绝非 ready service；`/governance/precheck` 暴露 lifecycle 决策 |
| NV-G-007 | 其他 | 展锐负责/生态合作 | 审计/诊断/... | 审计和诊断不可作为后补项 | `/audit/recent` 记录 SOA 与 governance precheck；`CENTRAL_BRAIN_AUDIT_LOG` 可选 JSONL 恢复最近 50 条；Linux shared governance daemon 可用 `CENTRAL_BRAIN_GOVERNANCE_AUDIT_LOG`，并通过 direct socket `audit.recent.get` 查询；Linux IPC/gRPC audit diagnostic path 优先复用 shared governance socket，不可用时回退 REST；IPC fallback 可用 `CENTRAL_BRAIN_IPC_AUDIT_LOG` |

### Uni Info Bus Protocol Binding

| Req ID | 图中模块 | 所有权 | 内容 | 实现要求 | 当前状态 |
| --- | --- | --- | --- | --- | --- |
| NV-P-001 | SOME/IP | 展锐负责/生态合作 | 跨 ECU 服务 | 车内跨 ECU 服务优先通过 SOME/IP binding | `/bindings` 计划态，待车载网络环境 |
| NV-P-002 | IPC | 展锐负责/生态合作 | 同 SoC 调用 | 同 SoC 调用必须有 IPC/Binder/UDS 路径 | Android Console 绑定 Binder/AIDL service sample；Android Binder 暴露 `getServiceContractsJson`、`getGovernanceBackendContractJson`、`getGovernanceMigrationCheckJson` 和 `getGovernanceDeploymentPlanJson`；Android system/privileged service integration note 初版；Linux Unix socket IPC active sample 已通过 reusable shared governance client 对 SOA 调用执行 shared governance daemon precheck，并保留本地 fallback；`soa.contracts.get` 可查 SOA service contract；`governance.runtime.get`/`audit.recent.get` 也优先复用 shared governance socket diagnostic path；`governance.backend.contract.get`/`governance.migration.check`/`governance.deployment.plan.get` 可查共享治理后端目标契约、迁移 readiness 和部署计划 |
| NV-P-003 | gRPC/RPC | 展锐负责/生态合作 | AI/工具服务/... | AI/工具服务可通过 RPC | Linux `central_brain_gateway.proto` + JSON TCP contract sample；新增 `GetServiceContracts`、`GetGovernanceBackendContract`、`GetGovernanceMigrationCheck` 与 `GetGovernanceDeploymentPlan` RPC 映射；`InvokeService` 通过同一 shared governance client 复用 shared governance daemon precheck；`GetRuntimeGovernance`/`GetRecentAudit` 优先复用 shared governance socket diagnostic path；真实 gRPC runtime 待目标环境提供 `grpcio`/C++ gRPC |
| NV-P-004 | MQTT | 展锐负责/生态合作 | 云车消息 | 云车消息必须受 Privacy/Policy 管控 | `/bindings` 计划态 |
| NV-P-005 | REST | 展锐负责/生态合作 | 云/工具 API/... | REST 仅作为 binding，不能绕过语义层 | Android App 层经 Binder sample；Linux IPC 对 SOA 调用先做本地治理 precheck 后再代理 REST prototype gateway；Binder 仍代理 REST prototype gateway |
| NV-P-006 | DDS | 展锐负责/生态合作 | Topic/Context/... | 高频 Topic/Context 订阅预留 DDS | `/uib/events/*` 已建立 Event 语义 contract 与 Android Binder/Linux IPC/gRPC 映射；DDS 数据面仍为计划态，待高频 topic 环境 |
| NV-P-007 | 其他 | 展锐负责/生态合作 | 大数据/... | 大数据通道必须纳入协议绑定与治理 | 未实现 |

## L4 Kernel & HAL 层需求

| Req ID | 图中模块 | 所有权 | 内容 | 实现要求 | 当前状态 |
| --- | --- | --- | --- | --- | --- |
| KH-001 | 文件系统管理/网络协议栈/... | 芯片原有 | OS 基础能力 | 上层不得重造基础 OS 能力 | 依赖宿主/Android |
| KH-002 | 内存管理 | 芯片原有 | 内存管理 | NPU/ADAS/多媒体需考虑共享内存与隔离 | 未实现 |
| KH-003 | Drivers | 芯片原有 | NPU/GPU/Camera/Audio/ETH/... | 仅在当前 Android/Linux 环境能力不足时新增开发；必须明确驱动接口支持矩阵 | 驱动接口矩阵 + NPU runtime interface 初版；`/native/driver-gaps` 记录触发条件和最小新增开发量；真实 driver 未实现 |
| KH-004 | 其他 | 芯片原有 | 底层扩展 | 需后续明确 | 未实现 |
| KH-005 | Libs | 芯片原有 | 基础库 | 需记录依赖库边界 | 未实现 |
| KH-006 | HAL | 芯片原有 | 硬件抽象层 | NPU、传感器、车身信号需 HAL 边界 | NPU HAL 边界文档化；Driver/HAL gap backlog 暴露 Android/Linux 目标接口；真实 HAL 未实现 |
| KH-007 | Safety Runtime | 芯片原有 | 安全运行时 | ASIL/QM 策略必须落到 runtime | Safety/NPU fault state 约束文档化；shared-memory-safety-runtime gap 记录触发条件；真实 Safety Runtime 未实现 |
| KH-008 | Libs | 芯片原有 | 另一组基础库 | 图中重复 Libs 需确认含义，见 ISSUE-007 | 未实现 |
| KH-009 | 进程&线程调度/中断与异常管理/系统调用接口/... | 芯片原有 | OS 调度和异常 | 真实 NPU/ADAS 接入必须定义异常恢复 | 未实现 |

## L5 虚拟化层需求

| Req ID | 图中模块 | 所有权 | 内容 | 实现要求 | 当前状态 |
| --- | --- | --- | --- | --- | --- |
| HV-001 | Hypervisor | 展锐负责 | 虚拟化基座 | 不开发虚拟化功能；仅记录依赖、接口假设和部署约束 | `CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md` 初版 |
| HV-002 | ASIL/QM 隔离 | 芯片原有 | 安全等级隔离 | 不开发隔离机制；必须定义服务到 ASIL/QM 的映射和假设 | Safety State 到安全域映射初版 |
| HV-003 | 跨 VM 共享内存与安全域通信 | 芯片原有 | 跨域通信 | 不开发跨 VM 通信；仅定义接口需求、fallback 和集成说明 | 跨 VM envelope 约束初版 |

## L6 硬件层需求

| Req ID | 图中模块 | 所有权 | 内容 | 实现要求 | 当前状态 |
| --- | --- | --- | --- | --- | --- |
| HW-001 | UniSOC Automotive-solution | 展锐负责 | 中央计算硬件基线 | 软件架构默认基于 UniSOC 车规方案 | 未实现 |
| HW-002 | 外置 PCIe NPU 算力卡 | 用户补充需求 | 后端 AI 基座由 PCIe NPU 实现 | 必须映射到 KH-003、KH-006、NV-F-011 | mock + `CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md` + DRV-GAP-001 |

## 开发顺序约束

1. 先补齐 L2/L3 的契约和治理骨架，再扩展上层 App。
2. 所有 App 能力必须经 Uni Info Bus 语义接口进入 SOA 服务入口。
3. REST/gRPC/MQTT/SOME-IP/DDS 只能作为 Protocol Binding，不能成为绕过 Uni Info Bus 的主架构。
4. NPU 调用必须经 Model Runtime Adapter，最终接口落到 Driver/HAL；mock 只能作为开发阶段替身。
5. Safety State、Policy、Lifecycle、Audit 不是后续附加模块，必须与接口设计同步推进。
6. 虚拟化层不产生开发任务，只产生文档、接口假设和集成约束。
7. 驱动层不默认产生开发任务，只在当前 Android/Linux 环境不能满足接口时记录缺口并新增最小开发量。
8. 跨 SoC 黄色小太阳组件必须同时提供 Android 与 Linux 交付路径。
