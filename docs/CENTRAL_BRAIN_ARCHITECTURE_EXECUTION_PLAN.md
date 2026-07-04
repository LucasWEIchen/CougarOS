# 按架构图执行的开发计划

版本：0.1
日期：2026-07-04

## 执行原则

1. 架构图是需求基线，产品参考资料只能作为体验补充，不能替代图中的分层和模块。
2. 每个开发任务必须挂接至少一个 `Req ID`。
3. 每个临时实现必须登记到偏差表。
4. 每个架构疑点必须登记到疑点表，并在设计或代码中保留 TODO/decision。
5. 开发优先级按图中中间层闭环排序：Uni Info Bus 语义接口 -> SOA 服务入口 -> Runtime & Governance -> Protocol Binding -> Native adapters -> Kernel/HAL -> App 扩展。

## 新里程碑

| 里程碑 | 目标 | 对应架构层 | 交付物 | 验收 |
| --- | --- | --- | --- | --- |
| A0 | 建立架构需求基线 | 全部 | 需求矩阵、偏差表、疑点表 | 每个图中模块有 Req ID |
| A1 | Uni Info Bus 语义接口 mock | L2 | Context/State/Event/Action/Service/Tool/Permission contract | App 不再直连后端具体模型接口 |
| A2 | SOA 服务入口 mock | L2 | Business/Foundation/Atomic/Contract/SafetyState 服务目录 | 所有服务可查询、可校验 contract |
| A3 | Runtime & Governance mock | L3 | Registry/Discovery/Schema/QoS/Policy/Lifecycle/Audit | 每次调用有注册、策略、生命周期和审计记录 |
| A4 | Protocol Binding 分层 | L3 | REST binding 重构；IPC/gRPC/MQTT/SOME-IP/DDS adapter stub | REST 仅是 binding，不承载业务语义 |
| A5 | Native adapters mock | L3 | AIOS Kernel、Service Adapter、Vehicle Signal、Model Runtime Adapter | AI/信号/模型调用均通过 adapter |
| A6 | Kernel/HAL/NPU 设计落地 | L4/L6 | Driver/HAL/NPU runtime design、hardware discovery | 明确 PCIe NPU 接入路径 |
| A7 | Hypervisor/Safety 域映射 | L5 | ASIL/QM domain map、跨 VM 通信设计 | 每个服务有安全域归属 |
| A8 | 应用层扩展 | L1 | Seat/Agent/Cluster/TBOX/ADAS/Diag App views | App 页面按图中应用域组织 |

## A1 详细任务：Uni Info Bus 语义接口

| Task ID | Req ID | 任务 | 输出 |
| --- | --- | --- | --- |
| A1-T01 | FW-U-001 | 定义 `Context` envelope 和 mock endpoint | `/context` contract |
| A1-T02 | FW-U-002 | 定义 `State` 查询，包括 service/model/vehicle | `/state` contract |
| A1-T03 | FW-U-003 | 定义 `Event` topic、subscribe、publish mock | event contract |
| A1-T04 | FW-U-004 | 定义 `Action` 请求、审批、执行状态 | action contract |
| A1-T05 | FW-U-005 | 定义统一 `Service.invoke` | service invocation contract |
| A1-T06 | FW-U-006 | 定义 `Tool` schema 和 Agent 工具绑定 | tool schema |
| A1-T07 | FW-U-007 | 定义 `Permission.check` | policy precheck |
| A1-T08 | DEV-001 | 重构 Android Console，不直接调用 `/ai/infer`，改为调用 Uni Info Bus | app client |

## A2 详细任务：SOA 服务入口

| Task ID | Req ID | 任务 | 输出 |
| --- | --- | --- | --- |
| A2-T01 | FW-S-001 | 建立 Business Services 目录 | service registry |
| A2-T02 | FW-S-002 | 建立 Foundation Services 目录 | service registry |
| A2-T03 | FW-S-003 | 建立 Atomic Services 目录 | service registry |
| A2-T04 | FW-S-004 | 为每个服务声明 IDL/Schema/version | schema files |
| A2-T05 | FW-S-005 | Safety State 作为入口拦截器 | policy middleware |
| A2-T06 | FW-S-006 | 其他服务扩展机制 | extension registry |

## A3 详细任务：Runtime & Governance

| Task ID | Req ID | 任务 | 输出 |
| --- | --- | --- | --- |
| A3-T01 | NV-G-001 | Registry 模块化 | backend module |
| A3-T02 | NV-G-002 | Discovery 不再返回硬编码地址 | backend module |
| A3-T03 | NV-G-003 | Schema/IDL 校验 | contract validator |
| A3-T04 | NV-G-004 | QoS 优先级和限流字段 | QoS mock |
| A3-T05 | NV-G-005 | Policy engine mock | `/policy/evaluate` |
| A3-T06 | NV-G-006 | Lifecycle 状态机 | service/model lifecycle |
| A3-T07 | NV-G-007 | Audit/Diagnostics 记录 | `/audit/recent` |

## A4 详细任务：Protocol Binding

| Task ID | Req ID | 任务 | 输出 |
| --- | --- | --- | --- |
| A4-T01 | NV-P-005 | REST binding 下沉为 adapter | REST adapter |
| A4-T02 | NV-P-002 | IPC/Binder 设计草案 | AIDL draft |
| A4-T03 | NV-P-003 | gRPC/RPC adapter stub | RPC adapter |
| A4-T04 | NV-P-004 | MQTT adapter stub | MQTT adapter |
| A4-T05 | NV-P-001 | SOME/IP mapping design | SOME/IP plan |
| A4-T06 | NV-P-006 | DDS topic mapping design | DDS plan |

## A5 详细任务：Native adapters

| Task ID | Req ID | 任务 | 输出 |
| --- | --- | --- | --- |
| A5-T01 | NV-F-001 | AIOS Kernel mock 包含 Agent/Model/Tool/Memory/Safety | backend modules |
| A5-T02 | NV-F-003 | Service Adapter 抽象 | adapter interface |
| A5-T03 | NV-F-004, NV-F-005 | Vehicle Signal + ECU Proxy mock | signal adapter |
| A5-T04 | NV-F-008 | SOA Service Runtime 状态机 | runtime state machine |
| A5-T05 | NV-F-011 | Model Runtime Adapter 抽象 | model runtime selector |
| A5-T06 | NV-F-012 | Trace/Logging/Metric 平台化 | observability module |

## 每次开发检查清单

- [ ] 本次任务是否引用了 Req ID？
- [ ] 是否引入了新的偏差？若有，是否更新 `CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md`？
- [ ] 是否发现新的架构疑点？若有，是否更新 `CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md`？
- [ ] 是否绕过 Uni Info Bus 或 SOA 服务入口？若是，是否有临时偏差记录？
- [ ] 是否运行了可行的验证命令？
- [ ] 是否更新了路线图？
