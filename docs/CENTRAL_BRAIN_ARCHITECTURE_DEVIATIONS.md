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
| DEV-001 | 当前仍以 REST/HTTP 承载 mock 后端，但 Android/Linux 主路径已改为 `/uib/*` 和 `/soa/*`，REST 作为 prototype Protocol Binding | FW-U-001..008, FW-S-001..006, NV-P-005, XSC-006 | M0 为快速验证端到端链路；本轮先收敛语义路径，未实现 Binder/IPC/gRPC | 如果继续让业务代码直接调用 legacy REST endpoint，会绕过语义层 | 下一步后端拆出 Gateway/binding 边界，Android 迁移到 AIDL/Binder 草案，Linux 增加 IPC/gRPC 样例 | Accepted Temporary |
| DEV-002 | 当前 `mock_npu_service.py` 同时承担 health、service registry、vehicle state、NPU runtime，未按图拆分 SOA Runtime、Model Runtime Adapter、Registry/Discovery/Policy | NV-F-008, NV-F-011, NV-G-001..007 | M0 用单进程 mock 降低复杂度 | 职责混杂，后续接口膨胀 | M2 拆成 gateway、registry、policy、runtime adapter 模块 | Accepted Temporary |
| DEV-003 | Android Console 仍有“Invoke SOA Inference”调试入口，虽已走 `/soa/invoke`，但仍未经过图中 AI SDK 的多模态/意图/模型路由/工具规划 | APP-004, NV-F-001, NV-F-011, XSC-001 | M0 尚未实现 AI SDK；本轮只收敛到 SOA 服务入口 | App 仍可能过早理解模型细节 | M2 实现 `/agent/plan` 与 `/ai/route`，App 调用任务/意图接口 | Open |
| DEV-004 | 当前车辆信号只有 mock VSS 风格路径，没有落到 Vehicle/Body Signal、ECU Proxy、DBC/ARXML、VHAL/HAL | NV-F-004, NV-F-005, KH-006 | 没有真实车身信号源和 DBC/ARXML | 信号语义无法验证量产适配 | M4 建立信号目录和映射表；用户提供车型/DBC/ARXML 后进入 adapter | Open |
| DEV-005 | 外置 PCIe NPU 目前仅通过 Python mock 表达，没有 Driver、HAL、Safety Runtime、DMA/IOMMU、Model Runtime Adapter | HW-002, KH-003, KH-006, KH-007, NV-F-011 | 缺少真实 NPU 卡型号和 vendor SDK；驱动层只在环境缺口处新增开发 | AI 基座接口风险最高 | A6 先明确驱动接口支持矩阵和 NPU runtime 抽象；获取硬件信息后只补缺口 | Open |
| DEV-006 | Runtime & Governance 目前只有 `/governance/runtime` 状态和 mock policy，没有真实 Registry/Discovery/QoS/Lifecycle/Audit 运行时 | NV-G-002, NV-G-004, NV-G-005, NV-G-006, NV-G-007, XSC-005 | A3 初版先提供可验证接口和 Req ID 追踪 | 治理能力仍不能约束真实多进程/多协议服务 | M2 拆出 registry、policy、lifecycle 模块，并让 `/soa/invoke` 强依赖治理模块 | Accepted Temporary |
| DEV-007 | 当前没有 Event 订阅机制，只有请求/响应 | FW-U-003, NV-P-006 | M0 只验证同步 HTTP | 车辆信号、服务健康、AI 任务无法实时推送 | M2 增加事件模型；M3 选择 SSE/WebSocket/mock DDS | Open |
| DEV-008 | 当前没有 Hypervisor、ASIL/QM 隔离、跨 VM 通信实现 | HV-001..003 | 用户已明确虚拟化层不需要开发 | 如果误纳入开发会浪费范围；如果不记录接口会影响集成 | A7 只建立安全域映射、依赖假设和跨 VM 通信接口说明，不开发虚拟化功能 | Accepted Temporary |
| DEV-009 | KaKaClaw/咖咖虾相关 Agentic OS 概念是产品参考，不属于原图直接模块；若直接扩展会偏离图中基线 | APP-004, NV-F-001, FW-U-006 | 用户要求参考该产品 | 产品概念覆盖架构图需求 | 所有 Agent/Skill/Memory 功能必须映射到 AI SDK、AIOS Kernel、Tool、Policy，不单独替代基线 | Accepted Temporary |
| DEV-010 | 当前路线图先写了 M0/M0.1，但没有明确“架构图需求基线优先级高于产品参考” | 全部 | 初期把图当概念参考 | 开发计划优先级错误 | 本轮修订路线图和跟踪规则 | Resolved |
| DEV-011 | Linux 已有 CLI smoke 样例，但尚未提供 daemon、systemd 部署、真实 IPC 和驱动/HAL 集成说明 | DEL-002, DEL-003, XSC-001..006 | 本轮补最小 Linux 同步交付路径 | Linux 座舱工程师仍缺少进程部署和平台差异细节 | A9 继续提供 Linux daemon/client、systemd 示例、IPC/gRPC binding 与平台差异说明 | Accepted Temporary |
| DEV-012 | 当前未显式区分黄色小太阳跨 SoC 组件和普通应用/生态组件 | XSC-001..006 | 初版只按层级拆解 | 跨 SoC 复用组件可能被做成单平台实现 | 已新增 XSC-001..006；后续所有 XSC 组件必须同时规划 Android/Linux 和平台无关 contract | Resolved |

## 新增偏差记录模板

| ID | 偏差 | 涉及需求 | 当前原因 | 风险 | 修正计划 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| DEV-XXX |  |  |  |  |  | Open |
