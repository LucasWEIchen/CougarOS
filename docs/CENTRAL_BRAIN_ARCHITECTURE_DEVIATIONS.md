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
| DEV-001 | 当前仍以 REST/HTTP 承载 mock 后端，但 Android/Linux 主路径已改为 `/uib/*` 和 `/soa/*`，REST 作为 prototype Protocol Binding | FW-U-001..008, FW-S-001..006, NV-P-005, XSC-006 | M0 为快速验证端到端链路；A4 已新增 Binder/IPC/gRPC contract skeleton，但未实现真实进程间 binding | 如果继续让业务代码直接调用 legacy REST endpoint，会绕过语义层 | 下一步将 Android Console 从 REST prototype 迁移到 Binder service stub，Linux 增加 IPC/gRPC daemon sample | Accepted Temporary |
| DEV-002 | 当前 `mock_npu_service.py` 仍同时承担 HTTP gateway、vehicle state、NPU runtime；Registry/Discovery/Policy/Lifecycle/Audit 已拆到 `runtime_governance.py`，但仍是同进程原型 | NV-F-008, NV-F-011, NV-G-001..007 | M0/A3 用单进程 mock 降低复杂度，同时先形成可验证治理边界 | Gateway、SOA runtime、Model Runtime Adapter 尚未成为独立进程/模块 | M2 继续拆成 gateway、SOA runtime、model runtime adapter；保留 `runtime_governance.py` 作为治理边界原型 | Accepted Temporary |
| DEV-003 | Android Console 仍有“Invoke SOA Inference”调试入口，虽已走 `/soa/invoke`，但仍未经过图中 AI SDK 的多模态/意图/模型路由/工具规划 | APP-004, NV-F-001, NV-F-011, XSC-001 | M0 尚未实现 AI SDK；本轮只收敛到 SOA 服务入口 | App 仍可能过早理解模型细节 | M2 实现 `/agent/plan` 与 `/ai/route`，App 调用任务/意图接口 | Open |
| DEV-004 | 当前车辆信号只有 mock VSS 风格路径，没有落到 Vehicle/Body Signal、ECU Proxy、DBC/ARXML、VHAL/HAL | NV-F-004, NV-F-005, KH-006 | 没有真实车身信号源和 DBC/ARXML | 信号语义无法验证量产适配 | M4 建立信号目录和映射表；用户提供车型/DBC/ARXML 后进入 adapter | Open |
| DEV-005 | 外置 PCIe NPU 目前仅通过 Python mock 表达，没有 Driver、HAL、Safety Runtime、DMA/IOMMU、Model Runtime Adapter | HW-002, KH-003, KH-006, KH-007, NV-F-011 | 缺少真实 NPU 卡型号和 vendor SDK；驱动层只在环境缺口处新增开发 | AI 基座接口风险最高 | A6 先明确驱动接口支持矩阵和 NPU runtime 抽象；获取硬件信息后只补缺口 | Open |
| DEV-006 | Runtime & Governance 已参与 `/soa/invoke` 的 registry/discovery/policy/lifecycle/audit precheck，但仍是内存态、单进程、无真实限流和多协议服务治理 | NV-G-002, NV-G-004, NV-G-005, NV-G-006, NV-G-007, XSC-005 | A3 本轮先把治理从状态展示推进到 active prototype | 治理能力仍不能约束真实多进程/多协议服务，重启后审计丢失 | M2 增加持久化 audit、真实 QoS/限流、多进程 registry/discovery，并让 IPC/gRPC binding 共用治理模块 | Accepted Temporary |
| DEV-007 | 当前没有 Event 订阅机制，只有请求/响应 | FW-U-003, NV-P-006 | M0 只验证同步 HTTP | 车辆信号、服务健康、AI 任务无法实时推送 | M2 增加事件模型；M3 选择 SSE/WebSocket/mock DDS | Open |
| DEV-008 | 当前没有 Hypervisor、ASIL/QM 隔离、跨 VM 通信实现 | HV-001..003 | 用户已明确虚拟化层不需要开发 | 如果误纳入开发会浪费范围；如果不记录接口会影响集成 | A7 只建立安全域映射、依赖假设和跨 VM 通信接口说明，不开发虚拟化功能 | Accepted Temporary |
| DEV-009 | KaKaClaw/咖咖虾相关 Agentic OS 概念是产品参考，不属于原图直接模块；若直接扩展会偏离图中基线 | APP-004, NV-F-001, FW-U-006 | 用户要求参考该产品 | 产品概念覆盖架构图需求 | 所有 Agent/Skill/Memory 功能必须映射到 AI SDK、AIOS Kernel、Tool、Policy，不单独替代基线 | Accepted Temporary |
| DEV-010 | 当前路线图先写了 M0/M0.1，但没有明确“架构图需求基线优先级高于产品参考” | 全部 | 初期把图当概念参考 | 开发计划优先级错误 | 本轮修订路线图和跟踪规则 | Resolved |
| DEV-011 | Linux 已有 CLI smoke 样例，但尚未提供 daemon、systemd 部署、真实 IPC 和驱动/HAL 集成说明 | DEL-002, DEL-003, XSC-001..006 | 本轮补最小 Linux 同步交付路径 | Linux 座舱工程师仍缺少进程部署和平台差异细节 | A9 继续提供 Linux daemon/client、systemd 示例、IPC/gRPC binding 与平台差异说明 | Accepted Temporary |
| DEV-012 | 当前未显式区分黄色小太阳跨 SoC 组件和普通应用/生态组件 | XSC-001..006 | 初版只按层级拆解 | 跨 SoC 复用组件可能被做成单平台实现 | 已新增 XSC-001..006；后续所有 XSC 组件必须同时规划 Android/Linux 和平台无关 contract | Resolved |
| DEV-013 | A4 Protocol Binding 目前是 AIDL/proto/schema contract skeleton 和注册表发现，不是真实 Binder、Unix socket 或 gRPC runtime | XSC-006, NV-P-002, NV-P-003, DEL-001, DEL-002 | 当前增量先固化跨 SoC 绑定 contract 和语义入口映射，避免在语义层未稳定前开发多套 runtime | 集成工程师可能误以为 Binder/gRPC 已可部署 | 文档和 `/bindings/detail` 明确 `contract-skeleton` 状态；下一步实现 Android Binder service stub 或 Linux IPC daemon sample | Accepted Temporary |
| DEV-014 | A5 Native adapters 目前是注册表和边界 mock，不是真实 AIOS Kernel、VHAL/ECU adapter、Model Runtime daemon 或 Driver/HAL bridge | XSC-004, NV-F-001, NV-F-003, NV-F-004, NV-F-005, NV-F-008, NV-F-011, KH-003, KH-006 | 当前 Android/Linux 环境没有真实车身信号源、DBC/ARXML、NPU vendor SDK 或 Driver/HAL；用户要求驱动层仅在能力不足时新增最小开发量 | 集成方可能误以为 Native adapter 已具备量产数据面能力 | `/native/adapters/detail` 明确状态、Android/Linux 交付路径和 Driver/HAL 依赖；后续按真实信号目录、vendor SDK 或 IPC daemon 逐项替换 mock | Accepted Temporary |

## 新增偏差记录模板

| ID | 偏差 | 涉及需求 | 当前原因 | 风险 | 修正计划 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| DEV-XXX |  |  |  |  |  | Open |
