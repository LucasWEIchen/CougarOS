# Central Brain Android Runtime Evolution Plan

版本：0.1
日期：2026-07-12
状态：Approved / In progress

## 目标与范围

本计划把已完成的 Central Brain Python contract/mock 原型演进为可安装在 Android 13 座舱硬件上的用户态中央大脑运行时。架构图仍是需求基线；本计划只补齐现有层内的量产化能力，不增加新的顶层架构层。

本阶段约束：

- 只聚焦 Android 交付，不开发 Linux 前端；既有 Linux 样例保留但不扩展。
- 不修改厂商 Android Framework、BSP、预编译系统组件或芯片 SDK 源码。
- 交付形态为 AI SDK AAR、独立 Runtime Service APK、Demo HMI APK，以及按需启用的 NDK/JNI adapter。
- Python 保留为仿真后端、contract conformance 和回归测试工具，不作为 Android 产品运行时。
- NPU 当前使用 deterministic stub；Ollama 只允许出现在 debug provider；Vendor NPU provider 保持 empty adapter。
- 不开发虚拟化。Driver/HAL 只在公开/vendor SDK 无法满足明确接口时登记最小缺口。

涉及 Req IDs：`APP-004`、`XSC-001`、`XSC-002`、`XSC-003`、`XSC-004`、`XSC-005`、`XSC-006`、`FW-U-003`、`FW-U-004`、`FW-U-006`、`FW-U-007`、`NV-F-001`、`NV-F-011`、`NV-F-012`、`NV-G-003`、`NV-G-004`、`NV-G-005`、`NV-G-006`、`NV-G-007`、`NV-P-002`、`DEL-001`、`DEL-003`、`DEL-004`、`DEL-005`。

## 成熟度模型

任何模块都必须使用下列单一状态，禁止用 `readiness=true` 代替实现成熟度：

| 状态 | 含义 | 最低证据 |
| --- | --- | --- |
| `contract_defined` | Schema、边界、错误和 Req ID 已定义 | contract parse + static check |
| `prototype_implemented` | 仿真环境中存在可执行实现 | deterministic unit/smoke test |
| `android_integrated` | Android 13 APK/AAR 路径可运行 | Binder/instrumentation + API 33 device test |
| `hardware_validated` | 目标硬件与 vendor 接口已验证 | target-device smoke + fault/rollback evidence |
| `production_qualified` | 性能、安全、隐私、升级和运维门禁关闭 | signed acceptance package |

状态规则：

- `python_prototype_current_scope_complete=true` 只说明旧 Python 原型范围完成，不提升任何模块到 `android_integrated`。
- readiness、checklist、rollup 和 no-store evidence 接口最多证明 `contract_defined`。
- mock、Ollama 和 Client2 HTTP 演示最多证明 `prototype_implemented`。
- hardware empty interface 永远不能标记为 `hardware_validated`。
- 每次状态提升必须记录验证命令、设备/ABI、commit 和未关闭风险。

## 分阶段实施

| 阶段 | 交付增量 | 退出条件 | 主要 Req IDs |
| --- | --- | --- | --- |
| R0 | 问题/偏差登记、成熟度模型、版本一致性门禁 | ISSUE-021..025、DEV-018/019、API baseline check 进入 CI 风格检查 | XSC-001..006, DEL-001/003/004 |
| R1 | Gradle 多模块 Android 工程 | `central-brain-sdk` AAR、`runtime-service` APK、`demo-hmi` APK 可在 API 33 构建安装 | DEL-001, NV-P-002 |
| R2 | 量产业务 AIDL 与诊断 AIDL 拆分 | typed Parcelable、快速返回、callback、cancel、Binder death 测试通过 | XSC-006, NV-G-003/006, NV-P-002 |
| R3 | Job Supervisor 与可信身份 | 任务状态机、Binder UID/package/signature capability mapping、default deny 生效 | NV-F-001, FW-U-007, NV-G-005/006 |
| R4 | Durable workflow | Room/SQLite checkpoint、pending effect、idempotency/outbox、重启恢复通过 | FW-U-004, NV-F-001, NV-G-006/007 |
| R5 | Scheduler 与 Model Router | deadline/priority/quota/cancel + Stub/Ollama-debug/Vendor-empty provider | APP-004, NV-F-011, NV-G-004/006 |
| R6 | Event、Memory、Skill 与 middleware | callback/cursor、memory lifecycle、signed built-in Skill、治理链测试通过 | FW-U-003/006/007, NV-G-005/007 |
| R7 | Observability、Client2 SDK 迁移与验收 | Client2 不再直连固定 HTTP；trace/metric、端到端和故障测试通过 | APP-004, NV-F-012, XSC-005/006, DEL-001 |

## 架构落点

| 架构图层 | 本计划新增实现 |
| --- | --- |
| AI SDK | typed Android client、session/task facade、callback API |
| Uni Info Bus | `EventEnvelope`、Action 幂等键、低频 callback/cursor |
| SOA | 统一 Operation envelope 和副作用 outbox |
| AIOS Kernel | Job Supervisor、Scheduler、Checkpoint、Model Router |
| Runtime & Governance | Binder identity、capability、approval、QoS、audit、middleware |
| Protocol Binding | production/diagnostic AIDL 分离和版本协商 |
| Native adapters | deterministic stub、Vendor NPU empty provider、按需 JNI/C ABI |

## 问题与偏差绑定

- `ISSUE-021`: Android Binder 业务/诊断接口、typed/async/cancel/death 语义。
- `ISSUE-022`: durable task/session/checkpoint/idempotency/outbox owner。
- `ISSUE-023`: Android 可信身份、capability 和高风险审批。
- `ISSUE-024`: Model Router、资源准入、fallback 和 NPU empty-provider 边界。
- `ISSUE-025`: Event、Memory、Skill 生命周期和治理链。
- `DEV-018`: 当前 107 个 String/JSON AIDL 方法与同步 HTTP proxy 偏离目标 Protocol Binding。
- `DEV-019`: 当前请求体自报权限与进程内非持久状态偏离目标 Runtime & Governance/AIOS Kernel。

## 自动推进规则

- 每轮只实现一个可独立验证的增量。
- 每轮先执行强制 preflight，再更新相关 Req ID、问题、偏差和路线图。
- 每轮通过相关 static/unit/smoke/device 验证后提交 Git。
- 每轮提交后直接选择下一未完成阶段，不等待用户再次确认。
- 遇到 vendor SDK、系统签名、SELinux、硬件 ABI 或真实 NPU 阻塞时保持 empty adapter，并记录 owner 和解除条件；不得伪造完成状态。
