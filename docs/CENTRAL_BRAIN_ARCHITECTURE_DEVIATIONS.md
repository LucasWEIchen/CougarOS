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
| DEV-001 | 当前 Android App 通过 REST 直接访问 mock 后端，绕开 Uni Info Bus 语义接口、SOA 服务入口和 Protocol Binding 分层 | FW-U-001..008, FW-S-001..006, NV-P-005 | M0 为快速验证端到端链路 | 形成错误架构习惯，后续难以收敛 | M1 增加本地 Uni Info Bus client；M2 后端拆出 Gateway 层，REST 仅作为 binding | Accepted Temporary |
| DEV-002 | 当前 `mock_npu_service.py` 同时承担 health、service registry、vehicle state、NPU runtime，未按图拆分 SOA Runtime、Model Runtime Adapter、Registry/Discovery/Policy | NV-F-008, NV-F-011, NV-G-001..007 | M0 用单进程 mock 降低复杂度 | 职责混杂，后续接口膨胀 | M2 拆成 gateway、registry、policy、runtime adapter 模块 | Accepted Temporary |
| DEV-003 | Android Console 中的“Run Mock Inference”直接表达 AI 推理，未经过图中 AI SDK 的多模态/意图/模型路由/工具规划 | APP-004, NV-F-001, NV-F-011 | M0 尚未实现 AI SDK | App 过早理解模型细节 | M2 实现 `/agent/plan` 与 `/ai/route`，App 调用任务/意图接口 | Open |
| DEV-004 | 当前车辆信号只有 mock VSS 风格路径，没有落到 Vehicle/Body Signal、ECU Proxy、DBC/ARXML、VHAL/HAL | NV-F-004, NV-F-005, KH-006 | 没有真实车身信号源和 DBC/ARXML | 信号语义无法验证量产适配 | M4 建立信号目录和映射表；用户提供车型/DBC/ARXML 后进入 adapter | Open |
| DEV-005 | 外置 PCIe NPU 目前仅通过 Python mock 表达，没有 Driver、HAL、Safety Runtime、DMA/IOMMU、Model Runtime Adapter | HW-002, KH-003, KH-006, KH-007, NV-F-011 | 缺少真实 NPU 卡型号和 vendor SDK | AI 基座架构风险最高 | M5 定义 NPU adapter 接口；获取硬件信息后补 driver/HAL 设计 | Open |
| DEV-006 | 当前没有 Runtime & Governance 中的 Discovery、QoS、Policy、Lifecycle、Audit 实现 | NV-G-002, NV-G-004, NV-G-005, NV-G-006, NV-G-007 | M0 只做静态服务列表 | 后续服务不可治理 | M2 优先实现 Policy、Trace、Lifecycle mock | Open |
| DEV-007 | 当前没有 Event 订阅机制，只有请求/响应 | FW-U-003, NV-P-006 | M0 只验证同步 HTTP | 车辆信号、服务健康、AI 任务无法实时推送 | M2 增加事件模型；M3 选择 SSE/WebSocket/mock DDS | Open |
| DEV-008 | 当前没有 Hypervisor、ASIL/QM 隔离、跨 VM 通信实现 | HV-001..003 | WSL/AVD 无法模拟真实虚拟化 | 安全域设计可能滞后 | M3 先建立安全域映射文档；真实硬件阶段落地 | Open |
| DEV-009 | KaKaClaw/咖咖虾相关 Agentic OS 概念是产品参考，不属于原图直接模块；若直接扩展会偏离图中基线 | APP-004, NV-F-001, FW-U-006 | 用户要求参考该产品 | 产品概念覆盖架构图需求 | 所有 Agent/Skill/Memory 功能必须映射到 AI SDK、AIOS Kernel、Tool、Policy，不单独替代基线 | Accepted Temporary |
| DEV-010 | 当前路线图先写了 M0/M0.1，但没有明确“架构图需求基线优先级高于产品参考” | 全部 | 初期把图当概念参考 | 开发计划优先级错误 | 本轮修订路线图和跟踪规则 | Resolved |

## 新增偏差记录模板

| ID | 偏差 | 涉及需求 | 当前原因 | 风险 | 修正计划 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| DEV-XXX |  |  |  |  |  | Open |
