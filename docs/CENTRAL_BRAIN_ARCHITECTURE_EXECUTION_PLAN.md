# 按架构图执行的开发计划

版本：0.1
日期：2026-07-04

## 执行原则

1. 架构图是需求基线，产品参考资料只能作为体验补充，不能替代图中的分层和模块。
2. 每个开发任务必须挂接至少一个 `Req ID`。
3. 每个临时实现必须登记到偏差表。
4. 每个架构疑点必须登记到疑点表，并在设计或代码中保留 TODO/decision。
5. 开发优先级按图中中间层闭环排序：Uni Info Bus 语义接口 -> SOA 服务入口 -> Runtime & Governance -> Protocol Binding -> Native adapters -> Kernel/HAL -> App 扩展。
6. 虚拟化层不开发，只维护接口约束和部署假设。
7. 驱动层不默认开发，只在当前 Android/Linux 环境不满足接口要求时新增最小开发量。
8. 带黄色小太阳的组件按跨 SoC 可移植组件处理，必须同时规划 Android 和 Linux 交付。

## 新里程碑

| 里程碑 | 目标 | 对应架构层 | 交付物 | 验收 |
| --- | --- | --- | --- | --- |
| A0 | 建立架构需求基线 | 全部 | 需求矩阵、偏差表、疑点表 | 每个图中模块有 Req ID |
| A1 | Uni Info Bus 语义接口 mock | L2 | Context/State/Event/Action/Service/Tool/Permission contract | App 不再直连后端具体模型接口 |
| A2 | SOA 服务入口 mock | L2 | Business/Foundation/Atomic/Contract/SafetyState 服务目录 | 所有服务可查询、可通过 `/soa/contracts` 查看 contract/版本/Policy/Safety/QoS/Lifecycle 且不触发 dispatch |
| A3 | Runtime & Governance mock | L3 | Registry/Discovery/Schema/QoS/Policy/Lifecycle/Audit | 每次调用有注册、策略、生命周期、QoS fixed-window 检查和审计记录；`/governance/precheck` 可只检查不调用；`/governance/backend-contract` 固定共享治理后端目标契约；`/governance/migration-check` 固定生产替换 readiness 不变量；`/governance/deployment-plan` 固定 Android/Linux/gRPC 目标部署形态；可选 JSONL 恢复最近审计；Linux shared governance daemon precheck/runtime/audit diagnostics；IPC/gRPC 共享 governance socket client 复用 precheck 和 runtime/audit 只读诊断 |
| A4 | Protocol Binding 分层 | L3 | REST binding 重构；IPC/gRPC/MQTT/SOME-IP/DDS adapter stub；Android system/privileged service integration note | REST 仅是 binding，不承载业务语义；Android Binder/Linux IPC/Linux gRPC-RPC 都可查询 SOA service contract、shared governance backend target、migration readiness 和 deployment plan；Linux IPC/gRPC 对 SOA 调用先走 shared governance client -> daemon precheck，不可用时回退本地 precheck；Linux IPC/gRPC 的 runtime/audit diagnostics 先走 shared governance socket，不可用时回退 REST |
| A5 | Native adapters mock | L3 | AIOS Kernel、Service Adapter、Vehicle Signal、Model Runtime Adapter | AI/信号/模型调用均通过 adapter |
| A6 | Kernel/HAL/NPU 接口文档与缺口补齐 | L4/L6 | Driver/HAL interface support matrix、NPU runtime interface、hardware discovery、`/native/driver-gaps` | 明确当前环境能力、缺口和最小新增开发量，不触发默认驱动开发 |
| A7 | Hypervisor/Safety 接口约束文档 | L5 | ASIL/QM domain map、跨 VM 通信假设、fallback | 不开发虚拟化功能，只记录集成约束 |
| A8 | 应用层扩展 | L1 | Seat/Agent/Cluster/TBOX/ADAS/Diag App views | App 页面按图中应用域组织 |
| A9 | Android/Linux 双平台交付 | 全部 | Android APK/SDK sample、Linux CLI/daemon sample、平台差异说明 | 座舱域工程师可在 Android 和 Linux 上集成验证 |

## A1 详细任务：Uni Info Bus 语义接口

| Task ID | Req ID | 任务 | 输出 |
| --- | --- | --- | --- |
| A1-T01 | FW-U-001 | 定义 `Context` envelope 和 mock endpoint | `/context` contract |
| A1-T02 | FW-U-002 | 定义 `State` 查询，包括 service/model/vehicle | `/state` contract |
| A1-T03 | FW-U-003 | 定义 `Event` topic、subscribe、publish mock | event contract |
| A1-T04 | FW-U-004 | 定义 `Action` 请求、审批、执行状态 | `/uib/actions/request` active mock + Android/Linux binding mapping |
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
| A3-T04 | NV-G-004 | QoS 优先级和限流字段 | `/soa/invoke` fixed-window QoS active prototype + `/governance/precheck` diagnostic peek + Linux shared governance daemon precheck/runtime diagnostics + Linux IPC/gRPC shared client `soa.service.invoke` fallback precheck + runtime diagnostic direct path |
| A3-T05 | NV-G-005 | Policy engine mock | `/policy/evaluate` + `/governance/precheck` |
| A3-T06 | NV-G-006 | Lifecycle 状态机 | service/model lifecycle |
| A3-T07 | NV-G-007, DEL-001, DEL-002, DEL-003, DEL-004 | Audit/Diagnostics 记录 | `/audit/recent` + `/governance/precheck` + `/governance/backend-contract` + `/governance/migration-check` + `/governance/deployment-plan` + Linux IPC/gRPC shared socket audit diagnostic path + `CENTRAL_BRAIN_AUDIT_LOG` JSONL sample + `CENTRAL_BRAIN_GOVERNANCE_AUDIT_LOG` shared daemon sample + `CENTRAL_BRAIN_IPC_AUDIT_LOG` fallback sample |

## A4 详细任务：Protocol Binding

| Task ID | Req ID | 任务 | 输出 |
| --- | --- | --- | --- |
| A4-T01 | NV-P-005 | REST binding 下沉为 adapter | REST adapter |
| A4-T02 | NV-P-002 | IPC/Binder 设计草案与 Android system/privileged service 集成约束 | AIDL draft + Linux IPC active sample with shared governance client SOA precheck/runtime/audit diagnostics + shared governance backend target/migration/deployment visibility + `CENTRAL_BRAIN_ANDROID_SYSTEM_SERVICE_INTEGRATION.md` |
| A4-T03 | NV-P-003 | gRPC/RPC adapter stub | JSON TCP contract sample + proto + shared governance client precheck/runtime/audit diagnostics + `GetGovernanceBackendContract` + `GetGovernanceMigrationCheck` + `GetGovernanceDeploymentPlan`；真实 gRPC runtime 待目标环境 |
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

## A6 详细任务：Kernel/HAL/NPU 接口

| Task ID | Req ID | 任务 | 输出 |
| --- | --- | --- | --- |
| A6-T01 | KH-003, KH-006 | 建立驱动/HAL 接口支持矩阵 | `CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md` |
| A6-T02 | HW-002, NV-F-011 | 定义 NPU runtime 最低抽象 | `CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md` 初版 |
| A6-T03 | DEL-004, DEL-005 | 明确 Android 与 Linux 驱动接口差异 | platform delta |
| A6-T04 | KH-003, KH-006, DEL-005 | 只对当前环境缺口建立开发任务 | `/native/driver-gaps` + Android `getDriverHalGapsJson` + Linux CLI `driver-gaps` |

## A7 详细任务：虚拟化接口约束

| Task ID | Req ID | 任务 | 输出 |
| --- | --- | --- | --- |
| A7-T01 | HV-001 | 记录 Hypervisor 依赖假设，不开发 | deployment assumptions |
| A7-T02 | HV-002 | 定义 Safety State 到 ASIL/QM 的映射 | safety domain map |
| A7-T03 | HV-003 | 记录跨 VM 通信接口需求和 fallback | integration note |

## A9 详细任务：Android/Linux 双平台交付

| Task ID | Req ID | 任务 | 输出 |
| --- | --- | --- | --- |
| A9-T01 | DEL-001 | Android 构建、安装、验证脚本 | APK + scripts |
| A9-T02 | DEL-002 | Linux client/CLI 或 daemon 示例 | Linux delivery sample + systemd hardening check |
| A9-T03 | DEL-003 | 座舱域工程师集成文档 | Android system service integration guide + Linux deployment guide |
| A9-T04 | DEL-004 | Android/Linux 平台差异说明 | platform delta doc + Android permission/SELinux assumptions + Linux unit hardening notes |
| A9-T05 | XSC-001..006 | 黄色小太阳组件跨 SoC 交付矩阵 | cross-SoC matrix |

## 每次开发检查清单

- [ ] 本次任务是否引用了 Req ID？
- [ ] 是否引入了新的偏差？若有，是否更新 `CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md`？
- [ ] 是否发现新的架构疑点？若有，是否更新 `CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md`？
- [ ] 是否绕过 Uni Info Bus 或 SOA 服务入口？若是，是否有临时偏差记录？
- [ ] 是否运行了可行的验证命令？
- [ ] 是否更新了路线图？
- [ ] 是否影响 Android/Linux 双平台交付？
- [ ] 是否影响驱动接口支持矩阵？
- [ ] 是否误把虚拟化层变成开发任务？
