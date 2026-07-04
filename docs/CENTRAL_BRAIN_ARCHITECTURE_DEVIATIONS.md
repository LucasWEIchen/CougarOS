# 中央大脑架构偏差登记表

版本：0.1
日期：2026-07-04

## 使用规则

本文件记录所有软件开发与架构图需求基线不一致的地方。偏差不等于错误，但必须有原因、影响、修正计划和状态。任何新增代码、接口、文档如果绕开架构图中的层级，都必须先更新本文件。

状态定义：
- Open：偏差存在，未修正。
- Accepted Temporary：阶段性接受，但已有回收计划。
- Resolved：已修正。

## 偏差列表

| ID | 偏差 | 涉及需求 | 当前原因 | 风险 | 修正计划 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| DEV-001 | 当前仍以 REST/HTTP 承载 mock 后端；Android Console 已改为绑定 `CentralBrainGatewayBinderService`，但 Binder service 与 Linux IPC sample 仍以上游 REST gateway 承载语义层 | FW-U-001..008, FW-S-001..006, NV-P-005, XSC-006 | M0 为快速验证端到端链路；A4/A4.2 已新增 Android Binder service/client sample、Linux Unix socket IPC active sample 和 gRPC contract skeleton；本轮已让 Android Console 经 Binder client 调用 `getStateJson` 与 `invokeServiceJson` | 如果误把 Binder/IPC sample 当成量产 gateway，会低估独立系统服务、权限身份、多进程治理和 native gateway 工作 | 下一步把 Android Binder/Linux IPC sample 的上游替换为独立 gateway/daemon，并补 Android system/privileged service 集成说明 | Accepted Temporary |
| DEV-002 | 当前 `mock_npu_service.py` 仍同时承担 HTTP gateway、vehicle state、NPU runtime；Registry/Discovery/Policy/Lifecycle/Audit 已拆到 `runtime_governance.py`，但仍是同进程原型 | NV-F-008, NV-F-011, NV-G-001..007 | M0/A3 用单进程 mock 降低复杂度，同时先形成可验证治理边界 | Gateway、SOA runtime、Model Runtime Adapter 尚未成为独立进程/模块 | M2 继续拆成 gateway、SOA runtime、model runtime adapter；保留 `runtime_governance.py` 作为治理边界原型 | Accepted Temporary |
| DEV-003 | Android Console 仍有“Invoke SOA Inference”调试入口，虽已走 `/soa/invoke`，但仍未经过图中 AI SDK 的多模态/意图/模型路由/工具规划 | APP-004, NV-F-001, NV-F-011, XSC-001 | M0 尚未实现 AI SDK；本轮只收敛到 SOA 服务入口 | App 仍可能过早理解模型细节 | M2 实现 `/agent/plan` 与 `/ai/route`，App 调用任务/意图接口 | Open |
| DEV-004 | 当前车辆信号只有 mock VSS 风格路径，没有落到 Vehicle/Body Signal、ECU Proxy、DBC/ARXML、VHAL/HAL | NV-F-004, NV-F-005, KH-006 | 没有真实车身信号源和 DBC/ARXML | 信号语义无法验证量产适配 | M4 建立信号目录和映射表；用户提供车型/DBC/ARXML 后进入 adapter | Open |
| DEV-005 | 外置 PCIe NPU 目前仅通过 Python mock 表达，没有 Driver、HAL、Safety Runtime、DMA/IOMMU、真实 Model Runtime Adapter | HW-002, KH-003, KH-006, KH-007, NV-F-011 | 缺少真实 NPU 卡型号和 vendor SDK；A6 已新增 `CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md` 固定 runtime/Driver/HAL contract，但驱动层只在环境缺口处新增开发 | AI 基座接口风险最高；如果后续绕过 Model Runtime Adapter 直连 vendor SDK，会破坏治理链路 | 获取硬件、vendor SDK、driver ABI 后只补 NPU runtime adapter + Driver/HAL bridge 缺口；DEV-005 在真实接入前保持 Open | Open |
| DEV-006 | Runtime & Governance 已参与 `/soa/invoke` 的 registry/discovery/policy/lifecycle/QoS/audit precheck，并新增 `CENTRAL_BRAIN_AUDIT_LOG` JSONL 审计持久化样例；QoS 当前是单进程 fixed-window active prototype | NV-G-002, NV-G-004, NV-G-005, NV-G-006, NV-G-007, XSC-005, DEL-002 | A3 先把治理从状态展示推进到 active prototype，本轮补 `/soa/invoke` per-service fixed-window QoS 检查 | 治理能力仍不能约束真实多进程/多协议服务；JSONL 只适合开发/单机集成，不等于量产审计后端；进程重启会清空 QoS 窗口 | M2 增加跨进程 QoS/限流、真实 registry/discovery、审计轮转/导出，并让 IPC/gRPC binding 共用治理模块 | Accepted Temporary |
| DEV-007 | 当前已有 `/uib/events/topics`、`/uib/events/publish`、`/uib/events/recent` Event active mock 和 Android Binder/Linux IPC/gRPC 映射，但仍没有真实订阅 broker、SSE/WebSocket/DDS 推送或高频 topic 数据面 | FW-U-003, NV-P-006, XSC-002, XSC-006 | 本轮先建立 Uni Info Bus Event 语义 contract、bounded recent log 和跨平台 binding 映射，避免把 DDS 当作同步 HTTP 替代品 | 车辆信号、服务健康、AI 任务仍无法以量产实时流方式推送；高频数据背压、QoS、订阅生命周期尚未验证 | M2 增加订阅 lifecycle 与 cursor；M3 选择 SSE/WebSocket/mock DDS；目标高频环境明确后接 DDS binding | Accepted Temporary |
| DEV-008 | 当前没有 Hypervisor、ASIL/QM 隔离、跨 VM 通信实现 | HV-001..003 | 用户已明确虚拟化层不需要开发；A7 已新增 `CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md` 固定接口约束 | 如果误纳入开发会浪费范围；如果下游忽略约束会影响 Safety/跨域集成 | 继续只维护安全域映射、依赖假设、跨 VM envelope 和 fallback；目标 SoC/Hypervisor 明确后进入集成验证，不开发虚拟化功能 | Accepted Temporary |
| DEV-009 | KaKaClaw/咖咖虾相关 Agentic OS 概念是产品参考，不属于原图直接模块；若直接扩展会偏离图中基线 | APP-004, NV-F-001, FW-U-006 | 用户要求参考该产品 | 产品概念覆盖架构图需求 | 所有 Agent/Skill/Memory 功能必须映射到 AI SDK、AIOS Kernel、Tool、Policy，不单独替代基线 | Accepted Temporary |
| DEV-010 | 当前路线图先写了 M0/M0.1，但没有明确“架构图需求基线优先级高于产品参考” | 全部 | 初期把图当概念参考 | 开发计划优先级错误 | 本轮修订路线图和跟踪规则 | Resolved |
| DEV-011 | Linux 已有 CLI、Unix socket IPC daemon/client、systemd 部署样例和平台差异说明，但尚未提供量产包管理、真实 IPC 服务治理和驱动/HAL 集成 | DEL-002, DEL-003, DEL-004, XSC-001..006 | 本轮补最小 Linux 同步交付路径，先满足座舱域工程师本地进程部署与验证 | Linux 座舱工程师仍需按目标发行版补包管理、安全加固、真实 Driver/HAL 和进程权限集成 | A9 继续补 Linux packaging、systemd hardening、真实 IPC/gRPC binding 与平台差异细节；A6 只在硬件/SDK 明确后补 Driver/HAL 缺口 | Accepted Temporary |
| DEV-012 | 当前未显式区分黄色小太阳跨 SoC 组件和普通应用/生态组件 | XSC-001..006 | 初版只按层级拆解 | 跨 SoC 复用组件可能被做成单平台实现 | 已新增 XSC-001..006；后续所有 XSC 组件必须同时规划 Android/Linux 和平台无关 contract | Resolved |
| DEV-013 | A4 Protocol Binding 目前 Android Console 已绑定 Binder service stub sample，Linux Unix socket IPC 是 active sample，gRPC 仍是 contract skeleton；Android Binder/Linux IPC 都仍代理到同进程 REST gateway，Event 也只是语义 mock/recent log | XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002 | 当前增量先固化跨 SoC 绑定 contract 和语义入口映射，并让 Android debug APK 实际走 Binder client/service 路径 | 集成工程师可能误以为 Binder/IPC sample 已是完整系统服务、多进程治理运行时或 DDS 事件总线 | `/bindings/detail` 明确 `android-binder-aidl` 为 `service-stub-sample`、`linux-ipc` 为 `active-sample`、gRPC 仍为 `contract-skeleton`、DDS 为计划态；下一步做 Android system/privileged service 集成说明或 Linux 独立 gateway/daemon | Accepted Temporary |
| DEV-014 | A5 Native adapters 目前是注册表和边界 mock，不是真实 AIOS Kernel、VHAL/ECU adapter、Model Runtime daemon 或 Driver/HAL bridge | XSC-004, NV-F-001, NV-F-003, NV-F-004, NV-F-005, NV-F-008, NV-F-011, KH-003, KH-006 | 当前 Android/Linux 环境没有真实车身信号源、DBC/ARXML、NPU vendor SDK 或 Driver/HAL；用户要求驱动层仅在能力不足时新增最小开发量 | 集成方可能误以为 Native adapter 已具备量产数据面能力 | `/native/adapters/detail` 明确状态、Android/Linux 交付路径和 Driver/HAL 依赖；后续按真实信号目录、vendor SDK 或 IPC daemon 逐项替换 mock | Accepted Temporary |

## 新增偏差记录模板

| ID | 偏差 | 涉及需求 | 当前原因 | 风险 | 修正计划 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| DEV-XXX |  |  |  |  |  | Open |
