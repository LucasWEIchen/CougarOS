# 车载中央大脑接口设计

版本：0.1
日期：2026-07-07

## 接口设计原则

- 所有接口都必须有 `trace_id`，便于跨 App、网关、NPU runtime、车辆适配器追踪。
- 所有跨域调用都必须带 `caller` 和 `permission_context`。
- 所有可能影响车辆状态的调用必须先经过 `Policy.evaluate`。
- 所有接口先以 HTTP/JSON mock 验证，再迁移到 AIDL/OpenAPI/protobuf/SOME-IP/DDS。
- 上层 App 不直接访问 VHAL、ECU、PCIe NPU 或 vendor SDK。

## 通用 Envelope

### Request

```json
{
  "trace_id": "uuid",
  "caller": {
    "app_id": "com.centralbrain.console",
    "role": "debug_console",
    "user_id": "local-driver"
  },
  "permission_context": {
    "vehicle_state": "parked",
    "safety_state": "normal",
    "privacy_level": "local_only"
  },
  "payload": {}
}
```

### Response

```json
{
  "trace_id": "uuid",
  "status": "ok",
  "error": null,
  "payload": {},
  "metrics": {
    "queue_ms": 0.0,
    "execution_ms": 0.0
  }
}
```

### Error

```json
{
  "code": "POLICY_DENIED",
  "message": "Action is not allowed while driving",
  "retryable": false,
  "details": {
    "required_permission": "vehicle.control",
    "current_vehicle_state": "driving"
  }
}
```

## 接口域

| 域 | 职责 | 原型协议 | 量产协议 |
| --- | --- | --- | --- |
| System | 健康、版本、能力 | HTTP/JSON | AIDL system/privileged service + REST debug |
| Registry | 服务注册发现 | HTTP/JSON | AIDL/gRPC/SOME-IP-SD |
| SOA Contract | 服务契约、版本、Policy/Safety State、QoS、Lifecycle | HTTP/JSON active mock | AIDL + Linux IPC + gRPC |
| Context | 车辆/用户/环境上下文 | HTTP/JSON | AIDL + DDS event |
| Event | Uni Info Bus 事件 topic、发布、recent log、订阅生命周期命令契约 | HTTP/JSON active mock | AIDL + Linux IPC + gRPC；高频 topic 预留 DDS |
| Extension | Uni Info Bus 扩展语义、schema 状态、治理规则和 binding 可见性 | HTTP/JSON active mock | AIDL + Linux IPC + gRPC；动态 extension runtime 待后续 |
| Action | Uni Info Bus 受控动作请求、Policy 检查、执行状态 | HTTP/JSON active mock | AIDL + Linux IPC + gRPC；真实车控需 Vehicle Signal/ECU Adapter + Driver/HAL |
| Agent | 任务规划和执行 | HTTP/JSON active mock | AIDL/gRPC |
| Skill | 技能声明、调用、生命周期 | HTTP/JSON | AIDL + sandbox IPC |
| Memory | 用户偏好和长期记忆 | HTTP/JSON | AIDL + local encrypted store |
| Policy | 权限、安全状态、隐私路由 | HTTP/JSON | AIDL/native policy engine |
| Vehicle | VSS/VHAL/ECU 信号 | HTTP/JSON active mock for read-only catalog | VHAL/AIDL/SOME-IP |
| AI/NPU | 模型、推理、队列、后端 | HTTP/JSON | AIDL/native daemon/vendor SDK |
| Hardware Interfaces | 硬件依赖空接口、reserved methods、Android/Linux 目标路径、触发条件、激活前 owner/ABI/smoke 门禁和 replacement trigger checklist | HTTP/JSON active mock | AIDL + Linux IPC + gRPC；真实 HAL/vendor SDK/native adapter 待后续 |
| Observability | Trace、Metric、QoS、Audit、共享治理后端目标契约和迁移检查 | HTTP/JSON | AIDL + file/socket exporter + shared Runtime & Governance backend |
| Protocol Binding Readiness | Android Binder、Linux IPC、gRPC/RPC、REST、MQTT、SOME/IP、DDS readiness、阻塞项和验证命令 | HTTP/JSON active mock | AIDL + Linux IPC + gRPC |
| Delivery Readiness | Android/Linux 交付样例、验证 bundle、阻塞项和非目标边界 | HTTP/JSON active mock | AIDL + Linux IPC + gRPC |
| Prototype Readiness | Python 原型模块成熟度、Android/Linux 绑定可见性、偏差、问题和下一步候选增量 | HTTP/JSON active mock | AIDL + Linux IPC + gRPC |
| Native Adapters | AIOS Kernel、Service Adapter、Vehicle Signal、Model Runtime Adapter | HTTP/JSON registry mock | Binder/native service + Unix socket/gRPC daemon + HAL/vendor SDK bridge |

AI SDK/Agent 入口当前已新增 `GET /ai/sdk/capabilities`、`POST /agent/plan`、`POST /agent/execute`、`GET /skills`、`POST /skills/{skill_id}/invoke` 与 `POST /memory/query` active contract mock，覆盖 XSC-001、APP-004、NV-F-001、FW-U-006、FW-U-007、DEL-001、DEL-002。App 侧只能提交 intent/utterance/task graph 并获得任务图或受控执行边界；任务图中的执行步骤仍必须通过 Uni Info Bus、Tool、Action 或 SOA 服务入口，不能直连 Model Runtime Adapter、NPU vendor SDK 或设备节点。

Android AIDL/Binder 的 system/privileged service 集成约束见
`docs/CENTRAL_BRAIN_ANDROID_SYSTEM_SERVICE_INTEGRATION.md`。该约束覆盖
DEL-001、DEL-003、DEL-004、XSC-002、XSC-003、XSC-005、XSC-006、NV-P-002、
NV-P-005、FW-U-007、FW-S-005、NV-G-005，并明确 Binder caller identity 只是
Policy 输入，不能替代 Runtime & Governance 的权限、安全状态和审计检查。

## MVP HTTP 接口

### System

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| GET | `/health` | 系统健康、服务总线、NPU 状态 | 是 |
| GET | `/services` | 服务目录 | 是 |
| GET | `/version` | 系统和 contract 版本 | 否 |

### Vehicle

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| GET | `/vehicle/state` | 当前车辆信号快照 | 是 |
| GET | `/vehicle/signals` | Vehicle/Body Signal 只读 VSS-style catalog、ECU/Signal Adapter 边界和 Driver/HAL gap 链接 | 是 |
| GET | `/vehicle/signals/validation` | Vehicle Signal read-bridge schema source、owner、parity 和 DRV-GAP-002 校验证据 envelope | 是 |
| GET | `/vehicle/signals/{path}` | 单个信号读取 | 否 |
| POST | `/vehicle/actions` | legacy 车控动作候选入口 | 否 |

`GET /vehicle/signals` 覆盖 XSC-002、XSC-004、XSC-006、NV-F-004、NV-F-005、NV-P-002、NV-P-003、DEL-001、DEL-002、DEL-005。该接口返回 BCM/HVAC/Seat/Door/Light/Powertrain 等 VSS-style signal catalog、访问级别、governance tag、Adapter 边界和 DRV-GAP-002 链接；Android Binder `getVehicleSignalsJson`、Linux CLI `vehicle-signals`、Linux IPC `vehicle.signals.list` 与 Linux gRPC/RPC `GetVehicleSignals` 暴露同一视图。本接口明确 `dbc_arxml_loaded=false`、`real_vehicle_bus_connected=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；它不是 DBC/ARXML parser、不是 VHAL/HAL bridge、不是 SocketCAN/vendor gateway，也不执行单信号读取或真实车控写操作。

`GET /vehicle/signals/validation` 覆盖 XSC-002、XSC-004、XSC-006、NV-F-003、NV-F-004、NV-F-005、NV-P-001、NV-P-002、NV-P-003、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。该接口返回 schema source metadata、Vehicle Signal Adapter owner、platform ABI owner、Android/Linux parity evidence、DRV-GAP-002 evidence 和 no-write-before-read-bridge 门禁；Android Binder `getVehicleSignalValidationJson`、Linux CLI `vehicle-signal-validation`、Linux IPC `vehicle.signals.validation.get` 与 Linux gRPC/RPC `GetVehicleSignalValidation` 暴露同一视图。本接口明确 `read_bridge_activated=false`、`schema_source_attached=false`、`adapter_owner_confirmed=false`、`parity_evidence_attached=false`、`drv_gap_002_evidence_attached=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；它不是 DBC/ARXML parser、不是 VHAL/HAL bridge、不是 SocketCAN/vendor gateway，也不激活真实读桥。

### Uni Info Bus Action

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| POST | `/uib/actions/request` | 受控动作请求；执行 Permission/Safety State 检查并返回 mock 执行状态 | 是 |
| POST | `/actions/request` | legacy 兼容入口 | 是 |

`/uib/actions/request` 覆盖 XSC-002、FW-U-004、FW-U-007、XSC-005、NV-G-005、DEL-001、DEL-002。当前只返回 `execution_mode=policy-checked-mock` 和 `dispatch.driver_hal=not-dispatched`；真实座舱/车控写操作必须后续接入 Vehicle Signal/ECU Adapter、SOA Service Runtime、Driver/HAL 和 Safety Runtime，不允许由 App 或 REST binding 直连底层。

### SOA Service Contract

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| GET | `/soa/services` | 服务目录、领域、权限、安全状态和 runtime registration metadata | 是 |
| GET | `/soa/contracts` | 服务 contract、版本、Policy/Safety State、QoS、Lifecycle、schema source 和 no-dispatch 边界 | 是 |
| POST | `/soa/invoke` | 经 Runtime & Governance precheck 后调用服务 | 是 |

`/soa/contracts` 覆盖 XSC-003、FW-S-001..005、NV-G-001..003、DEL-001、DEL-002。该接口只读取 `runtime_governance.SERVICE_CATALOG`，不 dispatch SOA service，不消费 QoS，不访问 Driver/HAL、车辆总线或虚拟化层；Android Binder、Linux IPC 和 Linux gRPC/RPC 均暴露同一 contract view。

### Agent

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| GET | `/ai/sdk/capabilities` | AI SDK facade 能力、Android/Linux 交付入口和路由约束 | 是 |
| POST | `/agent/plan` | 用户意图转 policy-aware task graph | 是 |
| POST | `/agent/execute` | 校验任务图并返回 policy-checked contract mock 执行边界 | 是 |
| GET | `/agent/tasks/{task_id}` | 查询任务状态 | 否 |
| POST | `/agent/tasks/{task_id}/cancel` | 取消任务 | 否 |

### Skill

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| GET | `/skills` | 技能列表 | 是 |
| GET | `/skills/{skill_id}` | 技能详情 | 否 |
| POST | `/skills/{skill_id}/invoke` | 调用技能 contract mock | 是 |
| POST | `/skills/{skill_id}/enable` | 启用技能 | 否 |
| POST | `/skills/{skill_id}/disable` | 停用技能 | 否 |

### Memory

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| POST | `/memory/query` | 查询相关本地 mock 记忆 | 是 |
| POST | `/memory/items` | 写入记忆 | 否 |
| DELETE | `/memory/items/{memory_id}` | 删除记忆 | 否 |
| POST | `/privacy/evaluate` | 判断数据是否可外发 | 否 |

### Policy

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| POST | `/policy/evaluate` | 权限与安全状态评估 | 是 |
| GET | `/policy/permissions` | 权限矩阵 | 否 |
| GET | `/audit/recent` | 最近治理审计记录；设置 `CENTRAL_BRAIN_AUDIT_LOG` 后可恢复最近 50 条 | 是 |
| GET | `/governance/backend-contract` | 共享 Runtime & Governance 后端目标契约，固定 Binder/IPC/gRPC 共用的 precheck/runtime/audit 操作和替换规则 | 是 |
| GET | `/governance/migration-check` | 共享 Runtime & Governance 后端替换 readiness 检查，固定 SOA precheck、Policy/QoS 不复制、runtime/audit 只读诊断和非目标边界 | 是 |
| GET | `/governance/deployment-plan` | 共享 Runtime & Governance 后端部署计划 contract，固定 Android system service、Linux daemon、true gRPC/RPC 目标形态和开放决策 | 是 |

当前 A3 QoS 增量不新增独立接口，而是在 `POST /soa/invoke` 内执行 per-service fixed-window 检查。`npu-inference` 样例限制为每 1 秒 2 次；超过窗口时 response payload 中 `state=rejected`、`qos_decision.decision=deny`，并写入 `outcome=qos_rejected` 的 audit。该实现覆盖 XSC-005、NV-G-004、NV-G-007、FW-S-005、DEL-002；它仍是单进程原型，不代表量产多进程/多协议限流后端。

`GET /governance/backend-contract` 覆盖 XSC-005、XSC-006、NV-G-001..007、NV-P-002、NV-P-003、DEL-001、DEL-002。当前返回的是目标契约和替换规则：Android Binder、Linux IPC、Linux gRPC/RPC 后续必须共用 `governance.precheck`、`governance.runtime.get`、`audit.recent.get` 操作边界，不能在各 transport 内复制 Policy/QoS 逻辑。本接口不实现量产多进程治理后端、真实 gRPC runtime、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。

`GET /governance/migration-check` 覆盖 XSC-005、XSC-006、NV-G-001、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、NV-P-003、DEL-001、DEL-002、DEL-003、DEL-004。当前返回生产共享治理后端替换前的机器可读 readiness：SOA dispatch 必须经 `governance.precheck`，Android Binder/Linux IPC/Linux gRPC 不得复制 Policy/QoS 逻辑，runtime/audit 诊断保持只读且不能触发 Driver/HAL、车辆总线或虚拟化。该接口明确 `production_backend_ready=false`，只作为迁移检查，不实现量产治理后端。

`GET /governance/deployment-plan` 覆盖 XSC-005、XSC-006、NV-G-001..007、NV-P-002、NV-P-003、DEL-001、DEL-002、DEL-003、DEL-004。当前返回共享治理后端目标部署计划：Android system/privileged Binder service、Linux standalone daemon、true gRPC/RPC 三类形态都必须保留相同 governance operation envelope、身份输入和只读诊断边界。该接口同样明确 `production_backend_ready=false`，不实现量产治理后端、真实 gRPC runtime、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。

### Protocol Binding Readiness

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| GET | `/bindings` | Protocol Binding 状态列表 | 是 |
| GET | `/bindings/detail` | Protocol Binding artifact、语义入口和分层约束 | 是 |
| GET | `/bindings/readiness` | Android Binder、Linux IPC、Linux gRPC/RPC、REST、MQTT、SOME/IP、DDS readiness、阻塞项、验证命令和下一步决策 | 是 |
| GET | `/delivery/readiness` | Android/Linux 交付样例、验证 bundle、阻塞项和非目标边界 | 是 |
| GET | `/prototype/readiness` | Python 原型模块成熟度、Android/Linux 绑定可见性、偏差、问题、下一步候选增量和非目标边界 | 是 |

`GET /bindings/readiness` 覆盖 XSC-006、NV-P-001..006、DEL-001、DEL-002、DEL-003、DEL-004。该接口只返回 binding readiness contract，明确 `production_ready=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；Android Binder `getBindingReadinessJson`、Linux IPC `bindings.readiness.get` 与 Linux gRPC/RPC `GetBindingReadiness` 暴露同一视图，不实现真实 gRPC runtime、MQTT broker、SOME/IP stack、DDS broker、Driver/HAL、Safety Runtime 或虚拟化层。

`GET /delivery/readiness` 覆盖 DEL-001、DEL-002、DEL-003、DEL-004、DEL-005、XSC-001..006。该接口汇总 Android debug Console/Binder、Android system service note、Linux CLI、Linux IPC、Linux gRPC/RPC、Linux systemd/package profile、Driver/HAL gap backlog、hardware empty-interface registry 和虚拟化约束的当前状态、验证命令、阻塞项和非目标边界；Android Binder `getDeliveryReadinessJson`、Linux IPC `delivery.readiness.get` 与 Linux gRPC/RPC `GetDeliveryReadiness` 暴露同一视图。该接口明确 `production_ready=false`，不 dispatch SOA service，不消费 QoS，不实现 Android system service、真实 gRPC runtime、量产包管理、生产共享治理后端、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。

`GET /prototype/readiness` 覆盖 XSC-001..006、DEL-001..005、HW-002、KH-003、KH-006、KH-007。该接口汇总 Python 原型中 AI SDK、Uni Info Bus、SOA、Runtime & Governance、Protocol Binding、Native adapters/Driver-HAL backlog 和 hardware empty-interface registry 的成熟度状态，并列出 Android 主路径、Linux 同步路径、开放偏差、开放问题和下一步候选增量；Android Binder `getPrototypeReadinessJson`、Linux CLI `prototype-readiness`、Linux IPC `prototype.readiness.get` 与 Linux gRPC/RPC `GetPrototypeReadiness` 暴露同一视图。该接口明确 `production_ready=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`，只作为产品/架构/交付状态总览，不实现新 runtime、不 dispatch SOA service、不访问 Driver/HAL、车辆总线或虚拟化层。

### Uni Info Bus Event

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| GET | `/uib/events/topics` | 事件 topic contract、订阅过滤字段、delivery 和 binding candidates | 是 |
| POST | `/uib/events/publish` | 通过 Uni Info Bus Event envelope 发布 mock 事件并写入 bounded recent log | 是 |
| GET | `/uib/events/recent` | 查询最近 Event 记录，作为订阅语义验证替身 | 是 |
| GET | `/uib/events/subscriptions` | 查询 Event subscription lifecycle、cursor、backpressure、governance、request/cancel 命令和 binding parity 契约 | 是 |
| POST | `/uib/events/subscriptions/request` | 验证 Event subscription request lifecycle command，返回 contract-only 结果，不持久化订阅 | 是 |
| POST | `/uib/events/subscriptions/cancel` | 验证 Event subscription cancel lifecycle command，返回 contract-only 结果，不联系 broker | 是 |
| GET | `/uib/events/subscriptions/transport-readiness` | 查询 Event subscription callback/watch transport readiness、broker/cursor/backpressure owner 和 binding parity 契约，不选择 transport | 是 |
| GET | `/uib/events/subscriptions/decision-matrix` | 查询 Event subscription broker/cursor/backpressure owner decision matrix、callback/watch shape 和 transport selection 门禁，不激活 runtime | 是 |
| GET | `/uib/events/subscriptions/activation-checklist` | 查询 Event subscription broker activation 前置证据清单、activation allowed 状态和 Driver/HAL high-rate scope 门禁，不激活 runtime | 是 |
| GET | `/uib/events/subscriptions/callback-watch-shape` | 查询 Android callback 与 Linux watch API shape、event/overflow/close envelope 和 no-runtime-registration 门禁，不注册 callback/watch | 是 |
| GET | `/uib/events/subscriptions/cursor-replay-storage` | 查询 Event subscription cursor schema、ack shape、replay window、retention/cleanup 和 no-persistence-runtime 门禁，不创建 cursor storage | 是 |
| GET | `/uib/events/subscriptions/backpressure-qos-evidence` | 查询 Event subscription overflow schema、per-caller throttling、replay rate、ack timeout 和 no-runtime-qos-activation 门禁，不激活事件 QoS | 是 |
| GET | `/uib/events/subscriptions/readiness-rollup` | 查询 Event subscription lifecycle、transport、owner、activation、callback/watch、cursor/replay 和 QoS evidence 端到端阻塞汇总，不关闭 gate | 是 |
| POST | `/uib/events/subscriptions/activation-evidence` | 验证 activation evidence reference envelope，不持久化 evidence，不更新 review queue | 是 |
| GET | `/uib/events/subscriptions/activation-evidence/status` | 查询 activation evidence no-store review status，不读取 evidence store，不关闭 gate | 是 |
| GET | `/uib/events/subscriptions/activation-evidence/retention-checklist` | 查询 activation evidence durable store、URI、retention、review、gate closure、delete/export 决策清单，不创建 store/workflow | 是 |
| GET | `/uib/events/subscriptions/activation-evidence/decision-status-rollup` | 查询 activation evidence intake/status/retention 与 readiness rollup 决策状态汇总，不调用 POST，不关闭 gate | 是 |
| GET | `/uib/events/subscriptions/activation-evidence/approval-dry-run/status` | 查询 activation approval dry-run no-store status、last-result shape 和 EV-AAS 门禁，不调用 dry-run POST、不持久化结果、不激活 broker | 是 |
| GET | `/uib/events/subscriptions/activation-evidence/approval-authority-checklist` | 查询 activation approval authority、policy、signature/RBAC、result store、review queue、gate closure、broker owner 和 EV-AAA 门禁，不执行审批 | 是 |
| GET | `/uib/events/subscriptions/activation-evidence/approval-authority-checklist/audit-consistency` | 查询 activation approval authority checklist、approval dry-run status、decision status rollup、Android/Linux parity 和 no-side-effect 的 EV-AAC 审计一致性，不执行审批、不激活 broker | 是 |
| GET | `/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-blocker-rollup` | 查询 activation approval decision blocker rollup、approval dry-run/review queue/gate closure/broker activation 阻塞原因和 EV-ADB 门禁，不执行审批、不激活 broker | 是 |
| POST | `/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run` | 校验 activation approval decision dry-run request shape，绑定 EV-ADB blocker rollup，并返回 EV-ADD blocked/no-store dry-run 结果 | 是 |
| GET | `/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/status` | 查询 activation approval decision dry-run no-store status、last-result unavailable、EV-ADS 门禁和 no-side-effect counters，不调用 dry-run POST、不保存审批结果 | 是 |
| GET | `/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/audit-consistency` | 查询 activation approval decision dry-run audit consistency、EV-ADA 门禁、zero persisted counters 和 no-side-effect parity，不调用 dry-run POST、不保存审批结果 | 是 |
| GET | `/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix` | 查询 activation approval decision closure blocker matrix、EV-ACB 门禁、open closure blockers 和 no-side-effect parity，不调用 dry-run POST、不保存审批结果 | 是 |
| GET | `/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist` | 查询 activation approval decision owner handoff checklist、EV-ACH 门禁、owner slots 和 evidence type，不分配 owner、不保存审批结果 | 是 |
| GET | `/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency` | 查询 activation approval decision owner handoff audit consistency、EV-AHA 门禁、handoff/closure count parity、no owner/evidence/no-store 和 no-side-effect parity，不分配 owner、不保存审批结果 | 是 |
| GET | `/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup` | 查询 activation approval decision owner handoff decision rollup、EV-AHD 门禁和 blocked owner/evidence/review/gate/Driver gap 决策汇总，不分配 owner、不保存审批结果 | 是 |
| GET | `/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix` | 查询 activation approval decision owner handoff evidence readiness matrix、EV-AHE missing evidence packets 和 no-store handoff evidence 阻塞项，不分配 owner、不附加 evidence、不保存审批结果 | 是 |
| GET | `/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency` | 查询 activation approval decision owner handoff evidence readiness audit consistency、EV-AHF audit gates 和 no-store/no-POST/no-side-effect 一致性，不分配 owner、不附加 evidence、不保存审批结果 | 是 |
| GET | `/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status` | 查询 activation approval decision owner handoff evidence acceptance status、EV-AHG acceptance gates 和 no-store/no-accept/no-side-effect 状态，不接受 packet、不附加 evidence、不保存审批结果 | 是 |
| GET | `/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency` | 查询 activation approval decision owner handoff evidence acceptance audit consistency、EV-AHH audit gates、Android/Linux parity、no-store/no-POST/no-side-effect 状态，不接受 packet、不附加 evidence、不保存审批结果 | 是 |
| GET | `/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup` | 查询 activation approval decision owner handoff evidence acceptance decision rollup、EV-AHI decision gates、acceptance decision blocked 状态，不接受 packet、不创建 store/queue、不关闭 gate、不激活 broker | 是 |
| GET | `/events/topics` | legacy 兼容入口 | 是 |
| POST | `/events/publish` | legacy 兼容入口 | 是 |

`GET /uib/events/subscriptions`、`POST /uib/events/subscriptions/request`、`POST /uib/events/subscriptions/cancel`、`GET /uib/events/subscriptions/transport-readiness`、`GET /uib/events/subscriptions/decision-matrix`、`GET /uib/events/subscriptions/activation-checklist`、`GET /uib/events/subscriptions/callback-watch-shape`、`GET /uib/events/subscriptions/cursor-replay-storage`、`GET /uib/events/subscriptions/backpressure-qos-evidence`、`GET /uib/events/subscriptions/readiness-rollup`、`POST /uib/events/subscriptions/activation-evidence`、`GET /uib/events/subscriptions/activation-evidence/status`、`GET /uib/events/subscriptions/activation-evidence/retention-checklist` 与 `GET /uib/events/subscriptions/activation-evidence/decision-status-rollup` 覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。这组接口返回订阅 lifecycle、cursor/replay storage、filter、QoS/backpressure、Runtime & Governance、transport candidates、callback/watch transport readiness、broker/cursor/backpressure owner decision matrix、activation evidence gates、activation evidence intake/status/retention checklist/decision status rollup、callback/watch API shape、cursor schema、ack shape、replay window、retention/cleanup、overflow schema、per-caller throttling、replay rate、ack timeout、end-to-end readiness blockers、`EV-SUB-001..006`/`EV-TR-001..006`/`EV-DM-001..007`/`EV-ACT-001..008`/`EV-CW-001..008`/`EV-CRS-001..008`/`EV-QOS-001..008`/`EV-RU-001..006`/`EV-AE-001..008`/`EV-AES-001..006`/`EV-AER-001..008`/`EV-AED-001..008` 门禁和 Android/Linux binding visibility；Android Binder `getEventSubscriptionsJson`/`requestEventSubscriptionJson`/`cancelEventSubscriptionJson`/`getEventSubscriptionTransportReadinessJson`/`getEventSubscriptionDecisionMatrixJson`/`getEventSubscriptionActivationChecklistJson`/`getEventSubscriptionCallbackWatchShapeJson`/`getEventSubscriptionCursorReplayStorageJson`/`getEventSubscriptionBackpressureQosEvidenceJson`/`getEventSubscriptionReadinessRollupJson`/`submitEventSubscriptionActivationEvidenceJson`/`getEventSubscriptionActivationEvidenceStatusJson`/`getEventSubscriptionActivationEvidenceRetentionChecklistJson`/`getEventSubscriptionActivationEvidenceDecisionStatusRollupJson`、Linux CLI `event-subscriptions`/`event-subscribe-request`/`event-subscribe-cancel`/`event-subscription-transport-readiness`/`event-subscription-decision-matrix`/`event-subscription-activation-checklist`/`event-subscription-callback-watch-shape`/`event-subscription-cursor-replay-storage`/`event-subscription-backpressure-qos-evidence`/`event-subscription-readiness-rollup`/`event-subscription-activation-evidence`/`event-subscription-activation-evidence-status`/`event-subscription-activation-evidence-retention-checklist`/`event-subscription-activation-evidence-decision-status-rollup`、Linux IPC `uib.events.subscriptions.get`/`uib.events.subscriptions.request`/`uib.events.subscriptions.cancel`/`uib.events.subscriptions.transport.readiness`/`uib.events.subscriptions.decision.matrix`/`uib.events.subscriptions.activation.checklist`/`uib.events.subscriptions.callback.watch.shape`/`uib.events.subscriptions.cursor.replay.storage`/`uib.events.subscriptions.backpressure.qos.evidence`/`uib.events.subscriptions.readiness.rollup`/`uib.events.subscriptions.activation.evidence`/`uib.events.subscriptions.activation.evidence.status`/`uib.events.subscriptions.activation.evidence.retention.checklist`/`uib.events.subscriptions.activation.evidence.decision.status.rollup` 与 Linux gRPC/RPC `GetEventSubscriptions`/`RequestEventSubscription`/`CancelEventSubscription`/`GetEventSubscriptionTransportReadiness`/`GetEventSubscriptionDecisionMatrix`/`GetEventSubscriptionActivationChecklist`/`GetEventSubscriptionCallbackWatchShape`/`GetEventSubscriptionCursorReplayStorage`/`GetEventSubscriptionBackpressureQosEvidence`/`GetEventSubscriptionReadinessRollup`/`SubmitEventSubscriptionActivationEvidence`/`GetEventSubscriptionActivationEvidenceStatus`/`GetEventSubscriptionActivationEvidenceRetentionChecklist`/`GetEventSubscriptionActivationEvidenceDecisionStatusRollup` 暴露同一视图。这些接口明确 `activation_evidence_intake_called=false`、`activation_evidence_persisted=false`、`decision_status_passed=false`、`cursor_replay_storage_confirmed=false`、`backpressure_qos_evidence_confirmed=false`、`readiness_rollup_confirmed=false`、`event_delivery_qos_active=false`、`overflow_emission_active=false`、`shape_confirmed=false`、`activation_allowed=false`、`broker_activation_ready=false`、`production_activation_allowed=false`、`all_required_evidence_complete=false`、`transport_selected=false`、`subscription_persisted=false`、`subscription_persistence_active=false`、`broker_active=false`、`callback_registered=false`、`watch_started=false`、`cursor_storage_active=false`、`replay_index_active=false`、`dds_runtime_active=false`、`sse_websocket_active=false`、`high_rate_data_plane_active=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`；它们不是真实订阅 broker，不创建 cursor storage，不建立 replay index，不分配量产 owner，不选择 transport，不调用 activation evidence POST，不持久化 evidence，不激活事件 QoS，不关闭 readiness gate，不注册 callback/watch，不启动 SSE/WebSocket 或 DDS runtime，也不访问 Driver/HAL、车辆总线或虚拟化层。

`GET /uib/events/subscriptions/activation-evidence/approval-dry-run/status` 是同一组 Event subscription activation gate 的只读补充，覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。Android Binder `getEventSubscriptionActivationApprovalDryRunStatusJson`、Android Console `Sub ApStat`、Linux CLI `event-subscription-activation-approval-dry-run-status`、Linux IPC `uib.events.subscriptions.activation.approval.dry.run.status` 与 Linux gRPC/RPC `GetEventSubscriptionActivationApprovalDryRunStatus` 暴露同一状态视图。该视图固定 `EV-AAS-001..008`、`approval_dry_run_invoked=false`、`last_result_available=false`、`persisted_dry_run_count=0`、`pending_approval_count=0`、`approval_authority_assigned=false`、`approval_policy_confirmed=false`、`approval_result_store_active=false`、`review_queue_updated=false`、`gates_closed=false`、`activation_allowed=false`、`broker_active=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；它不调用 approval dry-run POST，不保存请求或结果，不创建 approval review queue，不关闭 readiness gate，不激活 broker、DDS、高频数据面、Driver/HAL 或虚拟化层。

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist` 是同一组 Event subscription activation gate 的只读补充，覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。Android Binder `getEventSubscriptionActivationApprovalAuthorityChecklistJson`、Android Console `Sub ApAuth`、Linux CLI `event-subscription-activation-approval-authority-checklist`、Linux IPC `uib.events.subscriptions.activation.approval.authority.checklist` 与 Linux gRPC/RPC `GetEventSubscriptionActivationApprovalAuthorityChecklist` 暴露同一清单视图。该视图固定 `EV-AAA-001..008`、`approval_authority_ready=false`、`approval_authority_assigned=false`、`approval_policy_confirmed=false`、`approval_signature_rbac_confirmed=false`、`approval_result_store_active=false`、`review_queue_owner_assigned=false`、`gate_closure_authority_assigned=false`、`broker_activation_owner_assigned=false`、`driver_gap_review_owner_assigned=false`、`approval_dry_run_invoked=false`、`approval_result_store_created=false`、`review_queue_updated=false`、`gates_closed=false`、`activation_allowed=false`、`broker_active=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；它不调用 approval dry-run POST，不分配 approval authority，不创建 result store，不更新 review queue，不关闭 readiness gate，不激活 broker、DDS、高频数据面、Driver/HAL 或虚拟化层。

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/audit-consistency` 是同一组 Event subscription activation gate 的只读审计一致性补充，覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。Android Binder `getEventSubscriptionActivationApprovalAuthorityAuditConsistencyJson`、Android Console `Sub ApAudit`、Linux CLI `event-subscription-activation-approval-authority-audit-consistency`、Linux IPC `uib.events.subscriptions.activation.approval.authority.audit.consistency` 与 Linux gRPC/RPC `GetEventSubscriptionActivationApprovalAuthorityAuditConsistency` 暴露同一审计视图。该视图固定 `EV-AAC-001..008`、`consistency_passed=true`、`source_authority_checklist_bound=true`、`source_approval_dry_run_status_bound=true`、`source_decision_status_rollup_bound=true`、`approval_no_store_consistent=true`、`android_linux_parity_consistent=true`、`no_side_effects_consistent=true`、`approval_authority_ready=false`、`approval_dry_run_invoked=false`、`review_queue_updated=false`、`gates_closed=false`、`activation_allowed=false`、`broker_active=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；它不调用 approval dry-run POST，不分配 approval authority，不创建 approval result store，不更新 review queue，不关闭 readiness gate，不激活 broker、DDS、高频数据面、Driver/HAL 或虚拟化层。

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-blocker-rollup` 是同一组 Event subscription activation gate 的只读审批决策阻塞汇总，覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。Android Binder `getEventSubscriptionActivationApprovalDecisionBlockerRollupJson`、Android Console `Sub ApBlock`、Linux CLI `event-subscription-activation-approval-decision-blocker-rollup`、Linux IPC `uib.events.subscriptions.activation.approval.decision.blocker.rollup` 与 Linux gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionBlockerRollup` 暴露同一 blocker rollup 视图。该视图固定 `EV-ADB-001..008`、`open_blocker_count=7`、`approval_decision_ready=false`、`approval_dry_run_allowed=false`、`approval_result_store_created=false`、`review_queue_updated=false`、`gates_closed=false`、`broker_activation_allowed=false`、`activation_allowed=false`、`broker_active=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；它不持久化 approval decision，不分配 approval authority，不创建 approval result store，不更新 review queue，不关闭 readiness gate，不激活 broker、DDS、高频数据面、Driver/HAL 或虚拟化层。

`POST /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run` 是同一组 Event subscription activation gate 的 blocked/no-store approval decision dry-run request，覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。Android Binder `dryRunEventSubscriptionActivationApprovalDecisionJson`、Android Console `Sub ApDec`、Linux CLI `event-subscription-activation-approval-decision-dry-run`、Linux IPC `uib.events.subscriptions.activation.approval.decision.dry.run` 与 Linux gRPC/RPC `DryRunEventSubscriptionActivationApprovalDecision` 暴露同一 dry-run request 视图。该接口固定 `EV-ADD-001..008`、`rejected_blocked_contract_only=true`、`approval_decision_ready=false`、`approval_dry_run_allowed=false`、`dry_run_request_persisted=false`、`dry_run_result_persisted=false`、`approval_decision_persisted=false`、`review_queue_updated=false`、`gates_closed=false`、`broker_activation_allowed=false`、`activation_allowed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；它不保存请求或结果，不创建 approval result store，不更新 review queue，不关闭 readiness gate，不激活 broker、DDS、高频数据面、Driver/HAL 或虚拟化层。

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/status` 是同一组 Event subscription activation gate 的 blocked/no-store approval decision dry-run status 视图，覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。Android Binder `getEventSubscriptionActivationApprovalDecisionDryRunStatusJson`、Android Console `Sub ApDStat`、Linux CLI `event-subscription-activation-approval-decision-dry-run-status`、Linux IPC `uib.events.subscriptions.activation.approval.decision.dry.run.status` 与 Linux gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionDryRunStatus` 暴露同一 status 视图。该接口固定 `EV-ADS-001..008`、`last_approval_decision_result_available=false`、`persisted_dry_run_request_count=0`、`persisted_dry_run_result_count=0`、`persisted_approval_decision_count=0`、`decision_dry_run_post_called_by_status=false`、`approval_result_store_created=false`、`review_queue_updated=false`、`gates_closed=false`、`broker_activation_allowed=false`、`activation_allowed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；它不调用 dry-run POST，不保存 last-result/request/result/approval decision，不创建 approval result store，不更新 review queue，不关闭 readiness gate，不激活 broker、DDS、高频数据面、Driver/HAL 或虚拟化层。

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/audit-consistency` 是同一组 Event subscription activation gate 的 blocked/no-store approval decision dry-run audit consistency 视图，覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。Android Binder `getEventSubscriptionActivationApprovalDecisionDryRunAuditConsistencyJson`、Android Console `Sub ApDAudit`、Linux CLI `event-subscription-activation-approval-decision-dry-run-audit-consistency`、Linux IPC `uib.events.subscriptions.activation.approval.decision.dry.run.audit.consistency` 与 Linux gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionDryRunAuditConsistency` 暴露同一 audit consistency 视图。该接口固定 `EV-ADA-001..008`、`activation_approval_decision_dry_run_audit_consistency_active=true`、`consistency_passed=true`、`source_decision_dry_run_contract_bound=true`、`source_decision_blocker_rollup_bound=true`、`blocker_count_consistent=true`、`no_store_consistent=true`、`decision_dry_run_rejection_consistent=true`、`android_linux_parity_consistent=true`、`no_side_effects_consistent=true`、`decision_dry_run_post_called_by_audit_consistency=false`、`persisted_dry_run_request_count=0`、`persisted_dry_run_result_count=0`、`persisted_approval_decision_count=0`、`review_queue_updated=false`、`gates_closed=false`、`broker_activation_allowed=false`、`activation_allowed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；它不调用 dry-run POST，不保存 request/result/approval decision，不创建 approval result store，不更新 review queue，不关闭 readiness gate，不激活 broker、DDS、高频数据面、Driver/HAL 或虚拟化层。

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix` 是同一组 Event subscription activation gate 的 blocked/no-store approval decision closure blocker matrix 视图，覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。Android Binder `getEventSubscriptionActivationApprovalDecisionClosureBlockerMatrixJson`、Android Console `Sub ApClose`、Linux CLI `event-subscription-activation-approval-decision-closure-blocker-matrix`、Linux IPC `uib.events.subscriptions.activation.approval.decision.closure.blocker.matrix` 与 Linux gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionClosureBlockerMatrix` 暴露同一 closure blocker matrix 视图。该接口固定 `EV-ACB-001..010`、`activation_approval_decision_closure_blocker_matrix_active=true`、`closure_blocker_matrix_complete=true`、`closure_ready=false`、`open_closure_blocker_count=10`、`decision_dry_run_post_called_by_closure_blocker_matrix=false`、`persisted_dry_run_request_count=0`、`persisted_dry_run_result_count=0`、`persisted_approval_decision_count=0`、`review_queue_updated=false`、`gates_closed=false`、`broker_activation_allowed=false`、`activation_allowed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；它不调用 dry-run POST，不保存 request/result/approval decision，不创建 approval result store，不更新 review queue，不关闭 readiness gate，不激活 broker、DDS、高频数据面、Driver/HAL 或虚拟化层。

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist` 是同一组 Event subscription activation gate 的 blocked/no-store owner handoff checklist 视图，覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。Android Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffChecklistJson`、Android Console `Sub ApHand`、Linux CLI `event-subscription-activation-approval-decision-owner-handoff-checklist`、Linux IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.checklist` 与 Linux gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffChecklist` 暴露同一 owner handoff checklist 视图。该接口固定 `EV-ACH-001..010`、`activation_approval_decision_owner_handoff_checklist_active=true`、`owner_handoff_checklist_complete=true`、`owner_handoff_ready=false`、`open_owner_handoff_count=10`、`assigned_owner_count=0`、`unassigned_owner_count=10`、`owner_assignments_persisted=false`、`owner_handoff_queue_updated=false`、`decision_dry_run_post_called_by_owner_handoff_checklist=false`、`review_queue_updated=false`、`gates_closed=false`、`broker_activation_allowed=false`、`activation_allowed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；它不分配 owner，不调用 dry-run POST，不保存 handoff/request/result/approval decision，不创建 approval result store，不更新 review queue，不关闭 readiness gate，不激活 broker、DDS、高频数据面、Driver/HAL 或虚拟化层。

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency` 是同一组 Event subscription activation gate 的 blocked/no-store owner handoff audit consistency 视图，覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。Android Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffAuditConsistencyJson`、Android Console `Sub ApHAud`、Linux CLI `event-subscription-activation-approval-decision-owner-handoff-audit-consistency`、Linux IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.audit.consistency` 与 Linux gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffAuditConsistency` 暴露同一 owner handoff audit consistency 视图。该接口固定 `EV-AHA-001..010`、`activation_approval_decision_owner_handoff_audit_consistency_active=true`、`consistency_passed=true`、`owner_handoff_ready=false`、`open_owner_handoff_count=10`、`assigned_owner_count=0`、`unassigned_owner_count=10`、`attached_evidence_count=0`、`owner_assignments_persisted=false`、`owner_handoff_queue_updated=false`、`decision_dry_run_post_called_by_owner_handoff_audit_consistency=false`、`review_queue_updated=false`、`gates_closed=false`、`broker_activation_allowed=false`、`activation_allowed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；它不分配 owner，不附加 evidence，不调用 dry-run POST，不保存 handoff/request/result/approval/review state，不创建 approval result store，不更新 review queue，不关闭 readiness gate，不激活 broker、DDS、高频数据面、Driver/HAL 或虚拟化层。

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup` 是同一组 Event subscription activation gate 的 blocked/no-store owner handoff decision rollup 视图，覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。Android Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffDecisionRollupJson`、Android Console `Sub ApHRoll`、Linux CLI `event-subscription-activation-approval-decision-owner-handoff-decision-rollup`、Linux IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.decision.rollup` 与 Linux gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffDecisionRollup` 暴露同一 owner handoff decision rollup 视图。该接口固定 `EV-AHD-001..010`、`activation_approval_decision_owner_handoff_decision_rollup_active=true`、`decision_rollup_complete=true`、`decision_rollup_consistent=true`、`owner_handoff_decision_blocked=true`、`approval_decision_ready=false`、`blocked_decision_count=10`、`assigned_owner_count=0`、`unassigned_owner_count=10`、`attached_evidence_count=0`、`review_queue_updated=false`、`gates_closed=false`、`broker_activation_allowed=false`、`activation_allowed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；它不分配 owner，不附加 evidence，不调用 dry-run POST，不保存 handoff/request/result/approval/review state，不创建 approval result store，不更新 review queue，不关闭 readiness gate，不激活 broker、DDS、高频数据面、Driver/HAL 或虚拟化层。

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix` 是同一组 Event subscription activation gate 的 blocked/no-store owner handoff evidence readiness matrix 视图，覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。Android Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessMatrixJson`、Android Console `Sub ApHEv`、Linux CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-readiness-matrix`、Linux IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.readiness.matrix` 与 Linux gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessMatrix` 暴露同一 handoff evidence readiness matrix 视图。该接口固定 `EV-AHE-001..010`、`activation_approval_decision_owner_handoff_evidence_readiness_matrix_active=true`、`readiness_matrix_complete=true`、`readiness_matrix_consistent=true`、`handoff_evidence_ready=false`、`approval_decision_ready=false`、`required_evidence_packet_count=10`、`missing_evidence_packet_count=10`、`attached_evidence_count=0`、`persisted_evidence_packet_count=0`、`evidence_uri_count=0`、`owner_signature_count=0`、`review_queue_updated=false`、`gates_closed=false`、`broker_activation_allowed=false`、`activation_allowed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；它不分配 owner，不附加 evidence，不调用 dry-run POST，不保存 evidence/request/result/approval/review state，不创建 evidence store 或 approval result store，不更新 review queue，不关闭 readiness gate，不激活 broker、DDS、高频数据面、Driver/HAL 或虚拟化层。

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency` 是同一组 Event subscription activation gate 的 blocked/no-store owner handoff evidence readiness audit consistency 视图，覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。Android Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessAuditConsistencyJson`、Android Console `Sub ApHEvAud`、Linux CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-readiness-audit-consistency`、Linux IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.readiness.audit.consistency` 与 Linux gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessAuditConsistency` 暴露同一 audit consistency 视图。该接口固定 `EV-AHF-001..010`、`consistency_passed=true`、`packet_count_consistent=true`、`packet_state_consistent=true`、`source_binding_consistent=true`、`android_linux_parity_consistent=true`、`no_store_consistent=true`、`no_post_consistent=true`、`no_side_effects_consistent=true`、`handoff_evidence_ready=false`、`missing_evidence_packet_count=10`、`attached_evidence_count=0`、`persisted_evidence_packet_count=0`、`evidence_uri_count=0`、`owner_signature_count=0`、`review_queue_updated=false`、`gates_closed=false`、`broker_activation_allowed=false`、`activation_allowed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；它不分配 owner，不附加 evidence，不调用 dry-run POST，不保存 evidence/request/result/approval/review state，不创建 evidence store 或 approval result store，不更新 review queue，不关闭 readiness gate，不激活 broker、DDS、高频数据面、Driver/HAL 或虚拟化层。

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status` 是同一组 Event subscription activation gate 的 blocked/no-store owner handoff evidence acceptance status 视图，覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。Android Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceStatusJson`、Android Console `Sub ApHAcc`、Linux CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-status`、Linux IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.status` 与 Linux gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceStatus` 暴露同一 acceptance status 视图。该接口固定 `EV-AHG-001..010`、`acceptance_status_complete=true`、`acceptance_status_consistent=true`、`handoff_evidence_acceptance_allowed=false`、`required_acceptance_count=10`、`blocked_acceptance_count=10`、`accepted_evidence_packet_count=0`、`acceptance_record_persisted_count=0`、`missing_evidence_packet_count=10`、`attached_evidence_count=0`、`persisted_evidence_packet_count=0`、`evidence_uri_count=0`、`owner_signature_count=0`、`review_queue_updated=false`、`gates_closed=false`、`broker_activation_allowed=false`、`activation_allowed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；它不接受 packet，不附加 evidence，不分配 owner，不调用 dry-run POST，不保存 evidence/request/result/approval/handoff/review state，不创建 evidence store 或 approval result store，不更新 review queue，不关闭 readiness gate，不激活 broker、DDS、高频数据面、Driver/HAL 或虚拟化层。

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency` 是同一组 Event subscription activation gate 的 blocked/no-store owner handoff evidence acceptance audit consistency 视图，覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。Android Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceAuditConsistencyJson`、Android Console `Sub ApHAcAud`、Linux CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-audit-consistency`、Linux IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.audit.consistency` 与 Linux gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceAuditConsistency` 暴露同一 audit consistency 视图。该接口固定 `EV-AHH-001..010`、`consistency_passed=true`、`acceptance_count_consistent=true`、`blocked_acceptance_state_consistent=true`、`android_linux_parity_consistent=true`、`no_store_consistent=true`、`no_post_consistent=true`、`no_side_effects_consistent=true`、`required_audit_count=10`、`blocked_acceptance_count=10`、`accepted_evidence_packet_count=0`、`acceptance_record_persisted_count=0`、`review_queue_updated=false`、`gates_closed=false`、`broker_activation_allowed=false`、`activation_allowed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；它不接受 packet，不附加 evidence，不分配 owner，不调用 dry-run POST，不保存 evidence/request/result/approval/handoff/review state，不创建 evidence store 或 approval result store，不更新 review queue，不关闭 readiness gate，不激活 broker、DDS、高频数据面、Driver/HAL 或虚拟化层。

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup` 是同一组 Event subscription activation gate 的 blocked/no-store owner handoff evidence acceptance decision rollup 视图，覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。Android Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceDecisionRollupJson`、Android Console `Sub ApHDec`、Linux CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-decision-rollup`、Linux IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.decision.rollup` 与 Linux gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceDecisionRollup` 暴露同一 decision rollup 视图。该接口固定 `EV-AHI-001..010`、`decision_rollup_complete=true`、`decision_rollup_consistent=true`、`source_surfaces_bound=true`、`required_decision_count=10`、`blocked_decision_count=10`、`acceptance_decision_ready=false`、`accepted_evidence_packet_count=0`、`acceptance_record_persisted_count=0`、`review_queue_updated=false`、`gates_closed=false`、`broker_activation_allowed=false`、`activation_allowed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；它不接受 packet，不附加 evidence，不分配 owner，不调用 POST，不创建 evidence store、approval result store 或 review queue，不关闭 readiness gate，不激活 broker、DDS、高频数据面、Driver/HAL 或虚拟化层。

### Uni Info Bus Extension

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| GET | `/uib/extensions` | 查询扩展语义对象、schema 状态、治理规则、binding 可见性和 no-dispatch 边界 | 是 |

`/uib/extensions` 覆盖 XSC-002、FW-U-008、XSC-005、XSC-006、NV-P-002、NV-P-003、DEL-001、DEL-002。该接口只暴露 extension registry contract，明确 `dynamic_extension_runtime_ready=false`、`service_dispatch_triggered=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；Android Binder `getUibExtensionsJson`、Linux IPC `uib.extensions.get` 与 Linux gRPC/RPC `GetUibExtensions` 暴露同一视图，不加载插件、不 dispatch SOA service、不访问 Driver/HAL、车辆总线或虚拟化层。

### AI/NPU

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| GET | `/npu/status` | NPU/runtime 状态 | 是 |
| GET | `/hardware/interfaces` | 硬件依赖空接口目录；含 NPU runtime reserved methods | 是 |
| GET | `/hardware/interfaces/activation-checklist` | 硬件接口激活前 owner、ABI、Driver/HAL gap、Safety/Policy 和 smoke evidence 门禁 | 是 |
| GET | `/hardware/interfaces/owner-decision-status` | 硬件接口 target owner、Android/Linux ABI owner、Driver/HAL gap owner、Safety/Policy owner、smoke evidence owner 和 rollback/fault owner 决策状态 | 是 |
| POST | `/hardware/interfaces/owner-decision-evidence` | 硬件接口 owner/smoke/ABI/Driver-HAL/Safety gate evidence reference envelope 入站校验；不持久化、不更新 review queue、不关闭 gate | 是 |
| GET | `/hardware/interfaces/owner-decision-evidence/status` | 硬件接口 owner evidence no-store status；不读取 evidence store、不创建 review queue、不关闭 gate | 是 |
| GET | `/hardware/interfaces/owner-decision-evidence/retention-checklist` | 硬件接口 owner evidence retention/closure checklist；不创建 evidence store、不读取 evidence URI、不创建 delete/export workflow、不关闭 gate | 是 |
| GET | `/hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist` | 硬件接口 owner evidence replacement trigger checklist；不替换 adapter、不激活 adapter、不关闭 gate | 是 |
| GET | `/hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist` | 硬件接口 owner evidence selected-adapter readiness checklist；不选择 adapter、不加载 adapter、不激活 adapter、不关闭 gate | 是 |
| GET | `/hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup` | 硬件接口 owner evidence adapter-load blocker rollup；不选择 adapter、不加载 adapter、不替换 adapter、不激活 adapter、不关闭 gate | 是 |
| POST | `/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run` | 硬件接口 owner evidence adapter-load approval dry-run；校验请求形状并固定拒绝，不加载 adapter、不激活 adapter、不关闭 gate | 是 |
| GET | `/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status` | 硬件接口 owner evidence adapter-load dry-run no-store status；报告 last-result shape 与 zero persisted counters，不保存请求或结果 | 是 |
| GET | `/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/audit-consistency` | 硬件接口 owner evidence adapter-load dry-run audit consistency；只读核对 dry-run、status、blocker rollup 与 gate family，不调用 dry-run POST | 是 |
| GET | `/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist` | 硬件接口 owner evidence adapter-load approval authority checklist；只读报告 approval authority、policy、signature/RBAC、evidence workflow 和 target smoke/rollback/fault evidence 待确认项 | 是 |
| GET | `/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/status` | 硬件接口 owner evidence adapter-load approval authority no-store status；报告 approval record/review/evidence store 仍未创建且 adapter load 仍被阻塞 | 是 |
| GET | `/models` | 模型列表 | 否 |
| POST | `/models/load` | 加载模型 | 否 |
| POST | `/models/unload` | 卸载模型 | 否 |
| POST | `/ai/infer` | 推理请求 | 是 |

当前 A6 增量新增 `docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md`，将 AI/NPU 域的量产接口边界明确为 Model Runtime Adapter -> Driver/HAL contract，而不是 App 直连 vendor SDK 或设备节点。该 contract 覆盖 HW-002、NV-F-011、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005；本轮不新增真实 NPU driver、HAL、DMA/IOMMU、Safety Runtime 或虚拟化代码。

### Hardware Interfaces

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| GET | `/hardware/interfaces` | 查询 NPU、Vehicle bus、Camera/Audio/Sensors、Ethernet/SOME-IP/DDS/TSN、Shared memory/Safety Runtime 的空接口、reserved methods、Android 主路径、Linux 同步路径和触发条件 | 是 |
| GET | `/hardware/interfaces/activation-checklist` | 查询硬件接口激活前 owner、Android ABI、Linux ABI、Driver/HAL gap review、Safety/Policy、smoke evidence、rollback/fault 语义和 no-hardware-access 门禁 | 是 |
| GET | `/hardware/interfaces/owner-decision-status` | 查询硬件接口 `HW-ODS-001..008` owner 决策状态、blocked interface count、open gate ids 和 Android/Linux binding visibility | 是 |
| POST | `/hardware/interfaces/owner-decision-evidence` | 校验硬件接口 `HW-ODE-001..008` owner evidence reference envelope、reviewer identity 和 policy/audit 输入 | 是 |
| GET | `/hardware/interfaces/owner-decision-evidence/status` | 查询硬件接口 `HW-OES-001..008` owner evidence no-store status、zero persisted counters、open evidence store/review/closure decisions 和 Android/Linux binding visibility | 是 |
| GET | `/hardware/interfaces/owner-decision-evidence/retention-checklist` | 查询硬件接口 `HW-OER-001..008` owner evidence retention/closure checklist、delete/export/gate-closure blockers 和 Android/Linux binding visibility | 是 |
| GET | `/hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist` | 查询硬件接口 `HW-OET-001..008` owner evidence replacement trigger checklist、replacement/adapter activation blockers 和 Android/Linux binding visibility | 是 |
| GET | `/hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist` | 查询硬件接口 `HW-OEA-001..008` owner evidence selected-adapter readiness checklist、adapter load/activation/hardware access blockers 和 Android/Linux binding visibility | 是 |
| GET | `/hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup` | 查询硬件接口 `HW-ALB-001..008` owner evidence adapter-load blocker rollup、activation/owner/evidence/retention/replacement/selected-adapter blockers 和 Android/Linux binding visibility | 是 |
| POST | `/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run` | 校验硬件接口 `HW-ALD-001..008` adapter-load approval dry-run request、请求 shape、blocker rollup reference、policy/audit context 和 no-adapter-load rejection visibility | 是 |
| GET | `/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status` | 查询硬件接口 `HW-ALS-001..008` adapter-load dry-run no-store status、last-result shape、zero persisted counters、blocker rollup reference 和 no-side-effect visibility | 是 |
| GET | `/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/audit-consistency` | 查询硬件接口 `HW-ALC-001..008` adapter-load dry-run audit consistency、no-store counters、blocker rollup consistency、rejection contract 和 gate family cross-check | 是 |
| GET | `/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist` | 查询硬件接口 `HW-ALA-001..008` adapter-load approval authority、policy、signature/RBAC、durable evidence workflow、target smoke/rollback/fault evidence 和 no-load parity | 是 |
| GET | `/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/status` | 查询硬件接口 `HW-AAS-001..008` adapter-load approval authority no-store status、zero approval records、no review queue 和 no adapter load | 是 |

`GET /hardware/interfaces` 覆盖 XSC-004、XSC-006、HW-002、KH-001、KH-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。该接口只返回 `empty-interface-registry`，并明确 `hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`；Android Binder `getHardwareInterfacesJson`、Linux CLI `hardware-interfaces`、Linux IPC `hardware.interfaces.get` 与 Linux gRPC/RPC `GetHardwareInterfaces` 暴露同一视图。本接口不打开 device node、不调用 HAL/vendor SDK、不分配共享内存、不访问车辆总线，也不开发虚拟化层。

`GET /hardware/interfaces/activation-checklist` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。该接口只返回 `HW-ACT-001..008` 激活前门禁和每个硬件空接口的 blocked 状态，并明确 `activation_allowed=false`、`owner_decision_complete=false`、`android_abi_confirmed=false`、`linux_abi_confirmed=false`、`driver_gap_review_complete=false`、`safety_policy_binding_confirmed=false`、`target_hardware_smoke_attached=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`；Android Binder `getHardwareInterfaceActivationChecklistJson`、Linux CLI `hardware-interface-activation-checklist`、Linux IPC `hardware.interfaces.activation.checklist` 与 Linux gRPC/RPC `GetHardwareInterfaceActivationChecklist` 暴露同一视图。本接口不激活硬件、不启动服务、不新增 Driver/HAL 或虚拟化开发。

`GET /hardware/interfaces/owner-decision-status` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。该接口只返回 `HW-ODS-001..008` owner 决策状态和每个硬件空接口的 blocked owner decision 列表，并明确 `owner_decision_status_active=true`、`all_required_owners_assigned=false`、`target_hardware_smoke_attached=false`、`rollback_fault_semantics_confirmed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`；Android Binder `getHardwareInterfaceOwnerDecisionStatusJson`、Linux CLI `hardware-interface-owner-decision-status`、Linux IPC `hardware.interfaces.owner.decision.status` 与 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionStatus` 暴露同一视图。本接口不分配 owner、不关闭 gate、不激活硬件、不启动服务、不新增 Driver/HAL 或虚拟化开发。

`POST /hardware/interfaces/owner-decision-evidence` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。该接口只校验 `target_interface_ids`、`target_gate_ids`、`evidence_refs`、`reviewer` 和 Runtime & Governance policy/audit 输入，返回 `HW-ODE-001..008` 门禁与 `validated_contract_only`/`rejected_missing_evidence`/`rejected_by_policy` 状态，并明确 `owner_decision_evidence_contract_active=true`、`owner_decision_evidence_persisted=false`、`review_queue_updated=false`、`owner_assigned=false`、`gate_state_changed=false`、`gates_closed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`；Android Binder `submitHardwareInterfaceOwnerDecisionEvidenceJson`、Linux CLI `hardware-interface-owner-decision-evidence`、Linux IPC `hardware.interfaces.owner.decision.evidence` 与 Linux gRPC/RPC `SubmitHardwareInterfaceOwnerDecisionEvidence` 暴露同一视图。本接口不持久化 evidence、不读取 evidence URI、不更新 review queue、不分配 owner、不关闭 gate、不激活硬件、不启动服务、不新增 Driver/HAL 或虚拟化开发。

`GET /hardware/interfaces/owner-decision-evidence/status` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。该接口只返回 `HW-OES-001..008` no-store status 门禁、evidence store/review workflow/gate closure authority 待定项和 zero persisted counters，并明确 `owner_decision_evidence_status_contract_active=true`、`evidence_store_active=false`、`review_workflow_active=false`、`persisted_submission_count=0`、`pending_review_count=0`、`review_queue_updated=false`、`owner_assigned=false`、`gate_state_changed=false`、`gates_closed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`；Android Binder `getHardwareInterfaceOwnerDecisionEvidenceStatusJson`、Linux CLI `hardware-interface-owner-decision-evidence-status`、Linux IPC `hardware.interfaces.owner.decision.evidence.status` 与 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceStatus` 暴露同一视图。本接口不读取 evidence store、不创建 review queue、不分配 owner、不关闭 gate、不激活硬件、不启动服务、不新增 Driver/HAL 或虚拟化开发。

`GET /hardware/interfaces/owner-decision-evidence/retention-checklist` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。该接口只返回 `HW-OER-001..008` retention/closure 门禁、durable evidence store owner、evidence URI rules、retention policy owner、review workflow owner、gate closure authority、delete/export semantics、rollback/fault closure evidence 和 Android/Linux contract parity，并明确 `owner_decision_evidence_retention_checklist_active=true`、`owner_decision_complete=false`、`retention_policy_confirmed=false`、`evidence_uri_rules_confirmed=false`、`review_workflow_owner_confirmed=false`、`gate_closure_authority_confirmed=false`、`deletion_export_semantics_confirmed=false`、`rollback_fault_closure_confirmed=false`、`approval_signature_confirmed=false`、`evidence_store_active=false`、`review_workflow_active=false`、`delete_workflow_active=false`、`export_workflow_active=false`、`gate_closure_allowed=false`、`gates_closed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`；Android Binder `getHardwareInterfaceOwnerDecisionEvidenceRetentionChecklistJson`、Linux CLI `hardware-interface-owner-decision-evidence-retention-checklist`、Linux IPC `hardware.interfaces.owner.decision.evidence.retention.checklist` 与 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceRetentionChecklist` 暴露同一视图。本接口不创建 durable evidence store、不读取或 dereference evidence URI、不创建 review queue、不创建 delete/export workflow、不分配 owner、不关闭 gate、不激活硬件、不启动服务、不新增 Driver/HAL 或虚拟化开发。

`GET /hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。该接口只返回 `HW-OET-001..008` replacement trigger 门禁、replacement target interface、adapter readiness criteria、Driver/HAL gap closure evidence、Android/Linux ABI replacement parity、rollback-to-empty-interface plan、Safety/Policy replacement review、smoke harness replacement evidence 和 Android/Linux contract parity，并明确 `owner_decision_evidence_replacement_trigger_checklist_active=true`、`replacement_policy_confirmed=false`、`replacement_target_selected=false`、`adapter_readiness_criteria_confirmed=false`、`driver_hal_gap_closure_evidence_confirmed=false`、`rollback_to_empty_interface_plan_confirmed=false`、`replacement_allowed=false`、`adapter_activation_allowed=false`、`gate_closure_allowed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`；Android Binder `getHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklistJson`、Linux CLI `hardware-interface-owner-decision-evidence-replacement-trigger-checklist`、Linux IPC `hardware.interfaces.owner.decision.evidence.replacement.trigger.checklist` 与 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklist` 暴露同一视图。本接口不替换 adapter、不激活 adapter、不关闭 gate、不创建 durable evidence store、不读取或 dereference evidence URI、不创建 review queue、不分配 owner、不激活硬件、不启动服务、不新增 Driver/HAL 或虚拟化开发。

`GET /hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。该接口只返回 `HW-OEA-001..008` selected-adapter readiness 门禁、selected adapter owner、adapter interface contract、Driver/HAL gap evidence、Android/Linux binding parity、Safety/Policy fault model、smoke harness plan、rollback-to-empty-interface review 和 no-adapter-load contract parity，并明确 `owner_decision_evidence_selected_adapter_readiness_checklist_active=true`、`adapter_candidate_recorded=false`、`adapter_owner_assigned=false`、`adapter_interface_contract_approved=false`、`driver_hal_gap_evidence_attached=false`、`android_linux_binding_parity_approved=false`、`safety_policy_fault_model_reviewed=false`、`smoke_harness_plan_attached=false`、`rollback_to_empty_interface_reviewed=false`、`adapter_load_allowed=false`、`adapter_activation_allowed=false`、`hardware_access_allowed=false`、`gate_closure_allowed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`；Android Binder `getHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklistJson`、Linux CLI `hardware-interface-owner-decision-evidence-selected-adapter-readiness-checklist`、Linux IPC `hardware.interfaces.owner.decision.evidence.selected.adapter.readiness.checklist` 与 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklist` 暴露同一视图。本接口不选择 adapter、不加载 adapter、不激活 adapter、不关闭 gate、不创建 durable evidence store、不读取或 dereference evidence URI、不创建 review queue、不分配 owner、不激活硬件、不启动服务、不新增 Driver/HAL 或虚拟化开发。

`GET /hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。该接口只返回 `HW-ALB-001..008` adapter-load blocker rollup、activation checklist、owner decision status、owner evidence status、retention checklist、replacement trigger checklist 和 selected-adapter readiness checklist 的未清阻塞项，并明确 `owner_decision_evidence_adapter_load_blocker_rollup_active=true`、`adapter_load_ready=false`、`all_blockers_cleared=false`、`adapter_load_allowed=false`、`adapter_activation_allowed=false`、`hardware_access_allowed=false`、`gate_closure_allowed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`；Android Binder `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollupJson`、Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-blocker-rollup`、Linux IPC `hardware.interfaces.owner.decision.evidence.adapter.load.blocker.rollup` 与 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollup` 暴露同一视图。本接口不选择 adapter、不加载 adapter、不替换 adapter、不激活 adapter、不关闭 gate、不创建 durable evidence store、不读取或 dereference evidence URI、不创建 review queue、不分配 owner、不激活硬件、不启动服务、不新增 Driver/HAL 或虚拟化开发。

`POST /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。该接口只校验 `HW-ALD-001..008` adapter-load approval dry-run request shape、selected interface、selected adapter、adapter version、requested_by、evidence refs、policy/audit context 和 blocker rollup reference，并明确 `owner_decision_evidence_adapter_load_dry_run_active=true`、`adapter_load_dry_run_state=rejected_blocked_contract_only`、`dry_run_validated=true`、`adapter_load_allowed=false`、`adapter_activation_allowed=false`、`hardware_access_allowed=false`、`gate_closure_allowed=false`、`evidence_persisted=false`、`review_queue_updated=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`；Android Binder `dryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadJson`、Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-dry-run`、Linux IPC `hardware.interfaces.owner.decision.evidence.adapter.load.dry.run` 与 Linux gRPC/RPC `DryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoad` 暴露同一拒绝路径。本接口不选择 adapter、不加载 adapter、不替换 adapter、不激活 adapter、不关闭 gate、不创建 durable evidence store、不读取或 dereference evidence URI、不创建 review queue、不分配 owner、不激活硬件、不启动服务、不新增 Driver/HAL 或虚拟化开发。

`GET /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。该接口只查询 `HW-ALS-001..008` adapter-load dry-run no-store status/last-result shape，并明确 `owner_decision_evidence_adapter_load_dry_run_status_active=true`、`last_result_available=false`、`persisted_dry_run_count=0`、`pending_review_count=0`、`review_queue_updated=false`、`evidence_persisted=false`、`adapter_load_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`；Android Binder `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatusJson`、Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-dry-run-status`、Linux IPC `hardware.interfaces.owner.decision.evidence.adapter.load.dry.run.status` 与 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatus` 暴露同一 no-store 视图。本接口不保存 dry-run 请求或结果、不创建 evidence store、不创建 review queue、不选择 adapter、不加载 adapter、不激活 adapter、不关闭 gate、不访问硬件、不启动服务、不新增 Driver/HAL 或虚拟化开发。

`GET /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/audit-consistency` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。该接口只查询 `HW-ALC-001..008` adapter-load dry-run audit consistency，并明确 `owner_decision_evidence_adapter_load_dry_run_audit_consistency_active=true`、`consistency_passed=true`、`no_store_consistent=true`、`blocker_rollup_consistent=true`、`dry_run_rejection_consistent=true`、`adapter_load_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`；Android Binder `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistencyJson`、Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-dry-run-audit-consistency`、Linux IPC `hardware.interfaces.owner.decision.evidence.adapter.load.dry.run.audit.consistency` 与 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistency` 暴露同一 no-store 审计一致性视图。本接口不调用 dry-run POST、不保存 dry-run 请求或结果、不创建 evidence store、不创建 review queue、不选择 adapter、不加载 adapter、不激活 adapter、不关闭 gate、不访问硬件、不启动服务、不新增 Driver/HAL 或虚拟化开发。

`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。该接口只查询 `HW-ALA-001..008` adapter-load approval authority checklist，并明确 `owner_decision_evidence_adapter_load_approval_authority_checklist_active=true`、`approval_authority_assigned=false`、`approval_policy_confirmed=false`、`approval_signature_rules_confirmed=false`、`approval_rbac_confirmed=false`、`approval_workflow_active=false`、`approval_record_persisted=false`、`adapter_load_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`；Android Binder `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklistJson`、Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-authority-checklist`、Linux IPC `hardware.interfaces.owner.decision.evidence.adapter.load.approval.authority.checklist` 与 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklist` 暴露同一 no-load approval authority 决策视图。本接口不调用 dry-run POST、不持久化 approval record、不创建 evidence store、不创建 review queue、不选择 adapter、不加载 adapter、不激活 adapter、不关闭 gate、不访问硬件、不启动服务、不新增 Driver/HAL 或虚拟化开发。

`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/status` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。该接口只查询 `HW-AAS-001..008` adapter-load approval authority no-store status，并明确 `owner_decision_evidence_adapter_load_approval_authority_status_active=true`、`approval_record_available=false`、`persisted_approval_record_count=0`、`pending_approval_review_count=0`、`approval_review_queue_updated=false`、`approval_evidence_store_active=false`、`approval_decision_passed=false`、`no_store_consistent=true`、`approval_decisions_open=true`、`adapter_load_still_blocked=true`、`adapter_load_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`；Android Binder `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatusJson`、Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-authority-status`、Linux IPC `hardware.interfaces.owner.decision.evidence.adapter.load.approval.authority.status` 与 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatus` 暴露同一 no-store approval status 视图。本接口不调用 dry-run POST、不持久化 approval record、不创建 evidence store、不创建 review queue、不选择 adapter、不加载 adapter、不激活 adapter、不关闭 gate、不访问硬件、不启动服务、不新增 Driver/HAL 或虚拟化开发。

`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/audit-consistency` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。该接口只查询 `HW-AAC-001..008` adapter-load approval authority audit consistency，并明确 `owner_decision_evidence_adapter_load_approval_authority_audit_consistency_active=true`、`consistency_passed=true`、`approval_status_no_store_consistent=true`、`approval_decisions_open_consistent=true`、`adapter_load_blocked_consistent=true`、`adapter_load_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`；Android Binder `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistencyJson`、Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-authority-audit-consistency`、Linux IPC `hardware.interfaces.owner.decision.evidence.adapter.load.approval.authority.audit.consistency` 与 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistency` 暴露同一 no-store approval audit consistency 视图。本接口不调用 dry-run POST、不持久化 approval record、不创建 evidence store、不创建 review queue、不选择 adapter、不加载 adapter、不激活 adapter、不关闭 gate、不访问硬件、不启动服务、不新增 Driver/HAL 或虚拟化开发。

### Observability

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| GET | `/trace/recent` | 最近调用 Trace | 否 |
| GET | `/metrics` | 指标快照 | 否 |
| GET | `/audit/recent` | 最近审计记录；可选 JSONL 持久化样例 | 是 |

### Native Adapters

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| GET | `/native/adapters` | Native adapter 注册表，覆盖 AIOS Kernel、SOA Service Adapter、Vehicle Signal Adapter、Model Runtime Adapter、Security/Policy Adapter | 是 |
| GET | `/native/adapters/detail` | Android/Linux 交付路径、Driver/HAL 依赖、虚拟化约束和跨 SoC 约束 | 是 |

## 核心数据模型

### Task Graph

```json
{
  "task_id": "task-001",
  "intent": "prepare_for_commute",
  "steps": [
    {
      "step_id": "s1",
      "type": "query_context",
      "service": "vehicle-state",
      "input": ["Vehicle.Speed", "Vehicle.Powertrain.TractionBattery.StateOfCharge.Current"]
    },
    {
      "step_id": "s2",
      "type": "invoke_skill",
      "skill_id": "cabin.precondition",
      "depends_on": ["s1"]
    }
  ],
  "policy": {
    "requires": ["vehicle.read", "vehicle.control"],
    "allowed_safety_states": ["normal"]
  }
}
```

### Skill Manifest

```json
{
  "skill_id": "vehicle.state.query",
  "name": "Vehicle State Query",
  "version": "0.1.0",
  "permissions": ["vehicle.read"],
  "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
  "inputs": {
    "signals": "string[]"
  },
  "outputs": {
    "signals": "object"
  },
  "sandbox": {
    "network": "none",
    "vehicle_write": false,
    "cloud_access": false
  }
}
```

### Memory Item

```json
{
  "memory_id": "mem-001",
  "scope": "driver_profile",
  "classification": "local_sensitive",
  "ttl": "long_term",
  "content": {
    "preference": "cabin_temperature",
    "value": 22.5
  },
  "privacy": {
    "cloud_sync": false,
    "requires_user_consent": true
  }
}
```

### Policy Evaluation

```json
{
  "request": {
    "caller": "com.centralbrain.console",
    "action": "Cabin.SetTemperature",
    "resource": "Vehicle.Cabin.HVAC",
    "permissions": ["vehicle.control"],
    "vehicle_state": "parked",
    "safety_state": "normal"
  },
  "decision": "allow",
  "reason": "caller has debug permission and vehicle is parked"
}
```

### NPU Inference

```json
{
  "model": "central-intent-v0",
  "input": {
    "utterance": "帮我准备上班通勤"
  },
  "policy": {
    "safety_state_required": "normal",
    "timeout_ms": 2000,
    "fallback": ["cpu", "cloud"]
  }
}
```

## AIDL 草案

后续迁移到 Android/AAOS 系统形态时，建议拆成以下接口：

```text
ICentralBrainService
  getHealth()
  getServices()
  getVersion()

IAgentService
  plan(TaskRequest)
  execute(TaskGraph)
  getTask(TaskId)
  cancel(TaskId)

ISkillService
  listSkills()
  getSkill(SkillId)
  invoke(SkillInvocation)

IMemoryService
  query(MemoryQuery)
  write(MemoryItem)
  forget(MemoryId)

IPolicyService
  evaluate(PolicyRequest)
  getAudit(AuditQuery)

IVehicleContextService
  getState()
  getSignal(SignalPath)
  subscribe(SignalSubscription)
  requestAction(VehicleAction)

IAiRuntimeService
  getNpuStatus()
  listModels()
  loadModel(ModelSpec)
  infer(InferenceRequest)
```

## Protocol Binding Contract Skeleton

A4 增量把 REST 明确下沉为 `NV-P-005` prototype binding，并新增 Android/Linux binding artifact。所有 binding 只能承载 Uni Info Bus/SOA 语义入口，不能绕过 `Policy`、`Lifecycle` 和 `Audit`。

| Binding | Artifact | 语义入口 | Req IDs | 状态 |
| --- | --- | --- | --- | --- |
| Android Binder/AIDL | `central-brain/bindings/android/aidl/com/centralbrain/binding/ICentralBrainGateway.aidl`、`central-brain/bindings/android/java/com/centralbrain/binding/CentralBrainGatewayBinderService.java`、`central-brain/bindings/android/java/com/centralbrain/binding/CentralBrainGatewayClient.java`，并已编入 Android Console debug APK | `/uib/context`、`/uib/state`、`/uib/events/*`、`/uib/events/subscriptions`、`/uib/events/subscriptions/request`、`/uib/events/subscriptions/cancel`、`/uib/events/subscriptions/transport-readiness`、`/uib/events/subscriptions/decision-matrix`、`/uib/events/subscriptions/activation-checklist`、`/uib/events/subscriptions/callback-watch-shape`、`/uib/events/subscriptions/cursor-replay-storage`、`/uib/events/subscriptions/backpressure-qos-evidence`、`/uib/actions/request`、`/ai/sdk/capabilities`、`/agent/plan`、`/agent/execute`、`/skills`、`/memory/query`、`/soa/invoke`、`/policy/evaluate`、`/governance/backend-contract`、`/governance/migration-check`、`/governance/deployment-plan`、`/governance/runtime`、`/bindings/detail`、`/bindings/readiness`、`/delivery/readiness`、`/prototype/readiness`、`/native/adapters/detail`、`/hardware/interfaces`、`/hardware/interfaces/activation-checklist`、`/hardware/interfaces/owner-decision-status`、`/hardware/interfaces/owner-decision-evidence`、`/hardware/interfaces/owner-decision-evidence/status`、`/hardware/interfaces/owner-decision-evidence/retention-checklist`、`/hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist`、`/hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist`、`/hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup`、`/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run`、`/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status`、`/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/audit-consistency`、`/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist`、`/vehicle/signals`、`/vehicle/signals/activation`、`/vehicle/signals/validation` | XSC-001、APP-004、XSC-002、XSC-003、XSC-004、XSC-005、XSC-006、FW-U-003、FW-U-004、FW-U-006、HW-002、NV-F-003、NV-F-004、NV-F-005、NV-P-002、NV-P-005、NV-P-006、DEL-001 | Console Binder path + service stub sample；Console 已暴露 `planAgentTaskJson`、`executeAgentTaskJson`、`invokeSkillJson`、`queryMemoryJson`、`getEventSubscriptionsJson`、`requestEventSubscriptionJson`、`cancelEventSubscriptionJson`、`getEventSubscriptionTransportReadinessJson`、`getEventSubscriptionDecisionMatrixJson`、`getEventSubscriptionActivationChecklistJson`、`getEventSubscriptionCallbackWatchShapeJson`、`getEventSubscriptionCursorReplayStorageJson`、`getEventSubscriptionBackpressureQosEvidenceJson`、`getHardwareInterfacesJson`、`getHardwareInterfaceActivationChecklistJson`、`getHardwareInterfaceOwnerDecisionStatusJson`、`submitHardwareInterfaceOwnerDecisionEvidenceJson`、`getHardwareInterfaceOwnerDecisionEvidenceStatusJson`、`getHardwareInterfaceOwnerDecisionEvidenceRetentionChecklistJson`、`getHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklistJson`、`getHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklistJson`、`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollupJson`、`dryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadJson`、`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatusJson`、`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistencyJson`、`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklistJson`、`getVehicleSignalsJson`、`getVehicleSignalActivationJson`、`getVehicleSignalValidationJson`；execute/Skill/Memory/Event subscription lifecycle, transport readiness, owner decision matrix, activation checklist, callback/watch shape, cursor/replay storage, backpressure/QoS evidence, hardware owner evidence replacement trigger, selected-adapter readiness, adapter-load blocker rollup, adapter-load dry-run/status/audit consistency/approval authority checklist and Vehicle Signal activation/validation 为 contract mock；service 上游仍代理 REST prototype |
| Linux IPC | `central-brain/bindings/linux/ipc/central_brain_ipc_envelope.schema.json`、`central-brain/bindings/linux/ipc/central_brain_governance_client.py` | `uib.context.get`、`uib.state.get`、`uib.events.*`、`uib.events.subscriptions.request`、`uib.events.subscriptions.cancel`、`uib.events.subscriptions.transport.readiness`、`uib.events.subscriptions.decision.matrix`、`uib.events.subscriptions.activation.checklist`、`uib.events.subscriptions.callback.watch.shape`、`uib.events.subscriptions.cursor.replay.storage`、`uib.events.subscriptions.backpressure.qos.evidence`、`uib.actions.request`、`agent.plan`、`agent.execute`、`skills.*`、`memory.query`、`hardware.interfaces.get`、`hardware.interfaces.activation.checklist`、`hardware.interfaces.owner.decision.status`、`hardware.interfaces.owner.decision.evidence`、`hardware.interfaces.owner.decision.evidence.status`、`hardware.interfaces.owner.decision.evidence.retention.checklist`、`hardware.interfaces.owner.decision.evidence.replacement.trigger.checklist`、`hardware.interfaces.owner.decision.evidence.selected.adapter.readiness.checklist`、`hardware.interfaces.owner.decision.evidence.adapter.load.blocker.rollup`、`hardware.interfaces.owner.decision.evidence.adapter.load.dry.run`、`hardware.interfaces.owner.decision.evidence.adapter.load.dry.run.status`、`hardware.interfaces.owner.decision.evidence.adapter.load.dry.run.audit.consistency`、`vehicle.signals.list`、`vehicle.signals.activation.get`、`vehicle.signals.validation.get`、`soa.service.invoke` 通过 shared governance client precheck 后转发、不通时 local fallback、`policy.evaluate`、`governance.precheck`、`governance.backend.contract.get`、`governance.migration.check`、`governance.deployment.plan.get`、`governance.runtime.get`/`audit.recent.get` 通过 shared governance client diagnostic 后回退 REST | XSC-001、XSC-002、XSC-003、XSC-004、XSC-005、XSC-006、FW-U-003、FW-U-004、FW-U-006、HW-002、NV-F-003、NV-F-004、NV-F-005、NV-G-001、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、NV-P-006、KH-003、KH-006、KH-007、DEL-002 | active sample with reusable governance socket client for precheck and read-only runtime/audit diagnostics |
| Linux gRPC/RPC | `central-brain/bindings/linux/proto/central_brain_gateway.proto`、`central-brain/bindings/linux/ipc/central_brain_governance_client.py`、`central-brain/bindings/linux/grpc/central_brain_grpc_server.py`、`central-brain/bindings/linux/grpc/central_brain_grpc_client.py` | `CentralBrainGateway.GetState`、`RequestEventSubscription`、`CancelEventSubscription`、`GetEventSubscriptionTransportReadiness`、`GetEventSubscriptionDecisionMatrix`、`GetEventSubscriptionActivationChecklist`、`GetEventSubscriptionCallbackWatchShape`、`GetEventSubscriptionCursorReplayStorage`、`GetEventSubscriptionBackpressureQosEvidence`、`GetHardwareInterfaces`、`GetHardwareInterfaceActivationChecklist`、`GetHardwareInterfaceOwnerDecisionStatus`、`SubmitHardwareInterfaceOwnerDecisionEvidence`、`GetHardwareInterfaceOwnerDecisionEvidenceStatus`、`GetHardwareInterfaceOwnerDecisionEvidenceRetentionChecklist`、`GetHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklist`、`GetHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklist`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollup`、`DryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoad`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatus`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistency`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklist`、`RequestAction`、`InvokeService`、`EvaluatePolicy`、`PrecheckGovernance`、`GetGovernanceBackendContract`、`GetGovernanceMigrationCheck`、`GetGovernanceDeploymentPlan`、`GetRuntimeGovernance`、`GetRecentAudit`、`ListBindings`、`GetVehicleSignals`、`GetVehicleSignalActivation`、`GetVehicleSignalValidation`；`InvokeService` 先走同一个 shared governance client -> daemon precheck，不可用时 local fallback；runtime/audit diagnostics 先走 shared governance socket，不可用时 REST fallback | XSC-001、XSC-002、XSC-003、XSC-004、XSC-005、XSC-006、FW-U-003、FW-U-004、FW-U-006、HW-002、NV-F-003、NV-F-004、NV-F-005、NV-G-001、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-003、NV-P-006、KH-003、KH-006、KH-007、DEL-002 | JSON TCP contract sample；真实 gRPC runtime 待目标环境提供 `grpcio`/C++ gRPC |

HW-APD 增量把 `POST /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run` 作为 approval authority audit 后的决策请求干跑入口加入同一绑定族。Android 主路径为 `dryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionJson` 和 Console `HW ApDec`，Linux 同步路径为 CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run`、IPC `hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.dry.run` 和 gRPC/RPC `DryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecision`。该入口只校验 approval decision request shape 并返回 blocked contract-only 结果，不持久化 approval decision、不更新 review queue、不关闭 gate、不选择或加载 adapter、不访问硬件、不触发 Driver/HAL 或虚拟化开发。

HW-APS 增量把 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/status` 作为 approval decision dry-run 后的 no-store status 加入同一绑定族。Android 主路径为 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunStatusJson` 和 Console `HW ApDStat`，Linux 同步路径为 CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run-status`、IPC `hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.dry.run.status` 和 gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunStatus`。该入口只报告 last approval decision result 不可用、approval decision persisted count 为 0、无 approval decision review queue、无 evidence store 和 `decision_dry_run_post_called_by_status=false`，不调用 dry-run POST、不持久化 approval decision、不更新 review queue、不关闭 gate、不选择或加载 adapter、不访问硬件、不触发 Driver/HAL 或虚拟化开发。

HW-APA 增量把 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/audit-consistency` 作为 approval decision dry-run/status 后的 audit consistency 加入同一绑定族。Android 主路径为 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunAuditConsistencyJson` 和 Console `HW ApDAudit`，Linux 同步路径为 CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run-audit-consistency`、IPC `hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.dry.run.audit.consistency` 和 gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunAuditConsistency`。该入口只交叉核对 decision dry-run/status、approval authority audit/status 和 blocker rollup，固定 `HW-APA-001..008`、`consistency_passed=true`、`decision_dry_run_post_called_by_audit_consistency=false`、`approval_decision_persisted=false`、`adapter_load_allowed=false` 和 `hardware_accessed=false`，不调用 dry-run POST、不持久化 approval decision、不更新 review queue、不关闭 gate、不选择或加载 adapter、不访问硬件、不触发 Driver/HAL 或虚拟化开发。

HW-APM 增量把 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix` 作为 approval decision closure 前的 blocker matrix 加入同一绑定族。Android 主路径为 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionClosureBlockerMatrixJson` 和 Console `HW ApBlock`，Linux 同步路径为 CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-decision-closure-blocker-matrix`、IPC `hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.closure.blocker.matrix` 和 gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionClosureBlockerMatrix`。该入口只把 approval authority、approval policy、owner signature source、RBAC mapping、approval record schema、evidence store owner、review workflow、target smoke evidence、rollback plan、fault model、Driver/HAL gap closure evidence、audit owner 和 gate closure authority 固定为开放阻塞项，报告 `HW-APM-001..008`、`closure_ready=false`、`approval_decision_closure_allowed=false`、`unresolved_blocker_count=13`、`adapter_load_allowed=false` 和 `hardware_accessed=false`，不调用 dry-run POST、不持久化 approval decision、不关闭 gate、不选择或加载 adapter、不访问硬件、不触发 Driver/HAL 或虚拟化开发。

HW-APR 增量把 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix` 作为 approval decision closure 前的 reviewer matrix 加入同一绑定族。Android 主路径为 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionReviewerMatrixJson` 和 Console `HW ApRev`，Linux 同步路径为 CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-decision-reviewer-matrix`、IPC `hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.reviewer.matrix` 和 gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionReviewerMatrix`。该入口只把 approval authority、approval policy、signature/RBAC、approval record schema、approval evidence store、review workflow、target smoke、rollback/fault、Driver/HAL gap、audit export 和 gate closure reviewer 固定为未分配项，报告 `HW-APR-001..008`、`unassigned_reviewer_count=11`、`review_ready=false`、`approval_review_allowed=false`、`retention_review_allowed=false`、`gate_closure_allowed=false`、`adapter_load_allowed=false` 和 `hardware_accessed=false`，不分配 reviewer、不持久化 approval decision、不创建 review queue、不关闭 gate、不选择或加载 adapter、不访问硬件、不触发 Driver/HAL 或虚拟化开发。

HW-ARH 增量把 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist` 作为 approval reviewer evidence handoff 前的 checklist 加入同一绑定族。Android 主路径为 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffChecklistJson` 和 Console `HW ApHand`，Linux 同步路径为 CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-checklist`、IPC `hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.checklist` 和 gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffChecklist`。该入口只暴露 11 个 reviewer handoff packet 的必填字段和缺失状态，报告 `HW-ARH-001..008`、`required_handoff_packet_count=11`、`missing_handoff_packet_count=11`、`handoff_ready=false`、`evidence_handoff_allowed=false`、`approval_review_allowed=false`、`retention_review_allowed=false`、`gate_closure_allowed=false`、`adapter_load_allowed=false` 和 `hardware_accessed=false`，不接收 evidence handoff、不持久化 evidence、不创建 review queue、不关闭 gate、不选择或加载 adapter、不访问硬件、不触发 Driver/HAL 或虚拟化开发。

HW-AHA 增量把 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status` 作为 approval reviewer evidence handoff acceptance status 加入同一绑定族。Android 主路径为 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceStatusJson` 和 Console `HW ApHStat`，Linux 同步路径为 CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-status`、IPC `hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.status` 和 gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceStatus`。该入口只暴露 11 个 reviewer handoff packet acceptance 阻塞状态，报告 `HW-AHA-001..008`、`required_acceptance_count=11`、`blocked_acceptance_count=11`、`accepted_handoff_packet_count=0`、`acceptance_record_persisted_count=0`、`handoff_acceptance_allowed=false`、`approval_review_allowed=false`、`retention_review_allowed=false`、`gate_closure_allowed=false`、`adapter_load_allowed=false` 和 `hardware_accessed=false`，不接受 evidence handoff、不持久化 acceptance record、不创建 review queue、不关闭 gate、不选择或加载 adapter、不访问硬件、不触发 Driver/HAL 或虚拟化开发。

HW-AHC 增量把 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/audit-consistency` 作为 approval reviewer evidence handoff acceptance audit consistency 加入同一绑定族。Android 主路径为 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceAuditConsistencyJson` 和 Console `HW ApHAud`，Linux 同步路径为 CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-audit-consistency`、IPC `hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.audit.consistency` 和 gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceAuditConsistency`。该入口只交叉校验 handoff checklist、acceptance status、Android/Linux parity 和 no-side-effect counters，报告 `HW-AHC-001..008`、`consistency_passed=true`、`handoff_checklist_consistent=true`、`acceptance_status_consistent=true`、`blocked_acceptance_state_consistent=true`、`no_store_consistent=true`、`no_review_gate_load_consistent=true`、`no_side_effects_consistent=true`、`required_handoff_packet_count=11`、`missing_handoff_packet_count=11`、`required_acceptance_count=11`、`blocked_acceptance_count=11`、`accepted_handoff_packet_count=0`、`acceptance_record_persisted_count=0`、`handoff_acceptance_allowed=false`、`adapter_load_allowed=false` 和 `hardware_accessed=false`，不接受 evidence handoff、不持久化 acceptance record、不创建 review queue、不关闭 gate、不选择或加载 adapter、不访问硬件、不触发 Driver/HAL 或虚拟化开发。

HW-AHD 增量把 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup` 作为 approval reviewer evidence handoff acceptance decision rollup 加入同一绑定族。Android 主路径为 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceDecisionRollupJson` 和 Console `HW ApHRoll`，Linux 同步路径为 CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-decision-rollup`、IPC `hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.decision.rollup` 和 gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceDecisionRollup`。该入口只汇总 acceptance authority、acceptance record store、review workflow、audit retention、rollback/fault、Driver/HAL acceptance reviewer、gate closure authority 和 handoff packet presence 的未确认决策，报告 `HW-AHD-001..008`、`decision_rollup_complete=true`、`decision_rollup_consistent=true`、`required_decision_count=8`、`blocked_decision_count=8`、`acceptance_decision_ready=false`、`accepted_handoff_packet_count=0`、`acceptance_record_persisted_count=0`、`handoff_acceptance_allowed=false`、`adapter_load_allowed=false` 和 `hardware_accessed=false`，不接受 evidence handoff、不持久化 acceptance record/approval/evidence、不创建 review queue、不关闭 gate、不选择或加载 adapter、不访问硬件、不触发 Driver/HAL 或虚拟化开发。

HW-AHE 增量把 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist` 作为 approval reviewer evidence handoff acceptance closure readiness checklist 加入同一绑定族。Android 主路径为 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessChecklistJson` 和 Console `HW ApHClose`，Linux 同步路径为 CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-checklist`、IPC `hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.checklist` 和 gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessChecklist`。该入口只汇总 acceptance authority、acceptance record store、review workflow、audit retention、rollback/fault、Driver/HAL acceptance、gate closure authority 和 handoff packet presence 的 closure readiness 阻塞项，报告 `HW-AHE-001..008`、`closure_readiness_complete=true`、`closure_ready=false`、`required_closure_check_count=8`、`closure_blocker_count=8`、`ready_closure_check_count=0`、`decision_rollup_consistent=true`、`accepted_handoff_packet_count=0`、`acceptance_record_persisted_count=0`、`handoff_acceptance_allowed=false`、`adapter_load_allowed=false` 和 `hardware_accessed=false`，不接受 evidence handoff、不持久化 acceptance record/approval/evidence、不创建 review queue、不关闭 gate、不选择或加载 adapter、不访问硬件、不触发 Driver/HAL 或虚拟化开发。

HW-AHF 增量把 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency` 作为 approval reviewer evidence handoff acceptance closure readiness audit consistency 加入同一绑定族。Android 主路径为 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessAuditConsistencyJson` 和 Console `HW ApHCAud`，Linux 同步路径为 CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-audit-consistency`、IPC `hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.audit.consistency` 和 gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessAuditConsistency`。该入口只交叉校验 closure readiness checklist、closure blocker state、decision rollup、Android/Linux parity、no-store/no-review-gate-load 和 no-side-effect counters，报告 `HW-AHF-001..008`、`consistency_passed=true`、`closure_readiness_checklist_consistent=true`、`closure_check_count_consistent=true`、`closure_blocker_state_consistent=true`、`decision_rollup_closure_consistent=true`、`no_store_consistent=true`、`no_review_gate_load_consistent=true`、`no_side_effects_consistent=true`、`closure_ready=false`、`required_closure_check_count=8`、`closure_blocker_count=8`、`ready_closure_check_count=0`、`handoff_acceptance_allowed=false`、`adapter_load_allowed=false` 和 `hardware_accessed=false`，不接受 evidence handoff、不持久化 acceptance record/approval/evidence、不创建 review queue、不关闭 gate、不选择或加载 adapter、不访问硬件、不触发 Driver/HAL 或虚拟化开发。

HW-AHG 增量把 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup` 作为 approval reviewer evidence handoff acceptance closure readiness decision rollup 加入同一绑定族。Android 主路径为 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionRollupJson` 和 Console `HW ApHCRoll`，Linux 同步路径为 CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-rollup`、IPC `hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.rollup` 和 gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionRollup`。该入口只汇总 closure readiness audit、acceptance decision、closure blocker、Android/Linux parity 和 no-side-effect 决策状态，报告 `HW-AHG-001..008`、`decision_rollup_complete=true`、`decision_rollup_consistent=true`、`closure_readiness_audit_consistent=true`、`closure_ready=false`、`closure_decision_ready=false`、`required_decision_count=8`、`blocked_decision_count=8`、`source_surfaces_bound=true`、`closure_ready_decision_blocked=true`、`evidence_handoff_acceptance_decision_blocked=true`、`no_store_decision_rollup=true`、`no_review_gate_load_decision_rollup=true`、`no_side_effects_consistent=true`、`handoff_acceptance_allowed=false`、`adapter_load_allowed=false` 和 `hardware_accessed=false`，不接受 evidence handoff、不持久化 acceptance record/approval/evidence、不创建 review queue、不关闭 gate、不选择或加载 adapter、不访问硬件、不触发 Driver/HAL 或虚拟化开发。

HW-AHH 增量把 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist` 作为 approval reviewer evidence handoff acceptance closure decision reviewer assignment checklist 加入同一绑定族。Android 主路径为 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentChecklistJson` 和 Console `HW ApHCRev`，Linux 同步路径为 CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-checklist`、IPC `hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.reviewer.assignment.checklist` 和 gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentChecklist`。该入口只列出 closure readiness authority、acceptance authority、acceptance record store、review workflow/audit retention、rollback/fault、Driver/HAL、gate closure 和 Android/Linux parity reviewer assignment 仍未分配，报告 `HW-AHH-001..008`、`reviewer_assignment_checklist_complete=true`、`reviewer_assignment_ready=false`、`reviewer_assignment_allowed=false`、`source_decision_rollup_bound=true`、`required_reviewer_assignment_count=8`、`assigned_reviewer_count=0`、`unassigned_reviewer_count=8`、`reviewer_assignments_persisted=false`、`reviewer_assignment_queue_updated=false`、`adapter_load_allowed=false` 和 `hardware_accessed=false`，不分配 reviewer、不接受 evidence handoff、不持久化 acceptance record/approval/evidence、不创建 review queue、不关闭 gate、不选择或加载 adapter、不访问硬件、不触发 Driver/HAL 或虚拟化开发。

HW-AHI 增量把 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency` 作为 approval reviewer evidence handoff acceptance closure decision reviewer assignment audit consistency 加入同一绑定族。Android 主路径为 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistencyJson` 和 Console `HW ApHRvA`，Linux 同步路径为 CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-consistency`、IPC `hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.reviewer.assignment.audit.consistency` 和 gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistency`。该入口只校验 reviewer assignment checklist 的 source binding、row count、blocked assignment state、no-store、no-review/gate/load、Android/Linux parity 和 no-side-effect，报告 `HW-AHI-001..008`、`consistency_passed=true`、`reviewer_assignment_count_consistent=true`、`reviewer_assignment_blocker_state_consistent=true`、`reviewer_assignment_ready=false`、`assigned_reviewer_count=0`、`unassigned_reviewer_count=8`、`reviewer_assignments_persisted=false`、`reviewer_assignment_queue_updated=false`、`adapter_load_allowed=false` 和 `hardware_accessed=false`，不分配 reviewer、不接受 evidence handoff、不持久化 acceptance record/approval/evidence/reviewer assignment、不创建 review queue、不关闭 gate、不选择或加载 adapter、不访问硬件、不触发 Driver/HAL 或虚拟化开发。

验证命令：

```bash
bash tools/check_central_brain_binding_artifacts.sh
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py binding-detail
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py binding-readiness
```

HW-AHJ 增量把 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup` 作为 approval reviewer evidence handoff acceptance closure decision reviewer assignment audit decision rollup 加入同一绑定族。Android 主路径为 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupJson` 和 Console `HW ApHRvRoll`，Linux 同步路径为 CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup`、IPC `hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup` 和 gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollup`。该入口只汇总 reviewer assignment audit consistency、未分配 reviewer 和 adapter load 阻塞决策，报告 `HW-AHJ-001..008`、`decision_rollup_complete=true`、`decision_rollup_consistent=true`、`reviewer_assignment_decision_blocked=true`、`adapter_load_decision=blocked-by-unassigned-reviewers`、`assigned_reviewer_count=0`、`unassigned_reviewer_count=8`、`reviewer_assignments_persisted=false`、`reviewer_assignment_queue_updated=false`、`adapter_load_allowed=false` 和 `hardware_accessed=false`，不分配 reviewer、不接受 evidence handoff、不持久化 acceptance record/approval/evidence/reviewer assignment、不创建 review queue、不关闭 gate、不选择或加载 adapter、不访问硬件、不触发 Driver/HAL 或虚拟化开发。

HW-AHK 增量把 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary` 作为 approval reviewer evidence handoff acceptance closure handoff readiness summary 加入同一绑定族。Android 主路径为 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummaryJson` 和 Console `HW ApHReady`，Linux 同步路径为 CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-summary`、IPC `hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.summary` 和 gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummary`。该入口只汇总 reviewer assignment audit decision rollup 后仍开放的 8 个 handoff dependency，报告 `HW-AHK-001..008`、`closure_handoff_readiness_complete=true`、`closure_handoff_ready=false`、`handoff_ready=false`、`open_handoff_dependency_count=8`、`reviewer_assignment_decision_blocked=true`、`adapter_load_decision=blocked-by-unassigned-reviewers`、`assigned_reviewer_count=0`、`unassigned_reviewer_count=8`、`reviewer_assignments_persisted=false`、`reviewer_assignment_queue_updated=false`、`handoff_acceptance_allowed=false`、`adapter_load_allowed=false` 和 `hardware_accessed=false`，不分配 reviewer、不接受 evidence handoff、不持久化 acceptance record/approval/evidence/reviewer assignment、不创建 review queue、不关闭 gate、不选择或加载 adapter、不访问硬件、不触发 Driver/HAL 或虚拟化开发。

## Native Adapter Contract Mock

A5 增量新增 `central-brain/backend/native_adapters.py`，将图中的 Native 层能力先收敛成可查询注册表。当前状态不是 Driver/HAL 或真实 daemon 实现，只用于固定分层边界、Req ID 和 Android/Linux 交付路径。

| Adapter | 语义入口 | Req IDs | 状态 |
| --- | --- | --- | --- |
| AIOS Kernel | Tool、Service、Permission、Action | XSC-004、NV-F-001 | contract mock |
| SOA Service Adapter | `/soa/invoke`、`/soa/services` | XSC-003、NV-F-003、NV-F-008、FW-S-004、FW-S-005 | active prototype |
| Vehicle Signal Adapter | `/uib/context`、`/uib/state`、`/vehicle/signals`、`/vehicle/signals/activation`、`/vehicle/signals/validation`、`vehicle-state` service | NV-F-003、NV-F-004、NV-F-005、KH-006 | mock VSS snapshot + read-bridge activation criteria + validation envelope only; no real VHAL/SocketCAN/vendor gateway |
| Model Runtime Adapter | `npu-inference` service、`/npu/status` | NV-F-011、KH-003、KH-006、HW-002 | mock NPU runtime |
| Security/Policy Adapter | `/policy/evaluate`、`/soa/invoke` | NV-F-009、NV-G-005、FW-U-007、FW-S-005 | active prototype |

验证命令：

```bash
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py native-adapters-detail
bash tools/smoke_central_brain_semantic_gateway.sh
```

## Event Topic 设计

当前 FW-U-003 增量已实现 `/uib/events/topics`、`/uib/events/publish`、`/uib/events/recent` active mock、`/uib/events/subscriptions` subscription contract、`/uib/events/subscriptions/request` 和 `/uib/events/subscriptions/cancel` contract-only lifecycle command、`/uib/events/subscriptions/transport-readiness` callback/watch transport readiness contract、`/uib/events/subscriptions/decision-matrix` broker/cursor/backpressure owner decision matrix contract、`/uib/events/subscriptions/activation-checklist` broker activation prerequisite evidence checklist contract、`/uib/events/subscriptions/callback-watch-shape` Android callback/Linux watch API shape contract，以及 `/uib/events/subscriptions/cursor-replay-storage` cursor/replay storage contract，并在 Android Binder、Linux IPC、Linux gRPC/RPC contract sample 中建立映射。该实现只验证 Uni Info Bus 事件语义、Req ID、订阅 lifecycle/cursor/replay/backpressure/governance、request/cancel 命令、callback/watch readiness、owner decision matrix、activation evidence gates、callback/watch shape、cursor/replay storage 和跨平台 binding，不实现 DDS、高频推送 broker、callback/watch、SSE/WebSocket、真实订阅数据面、cursor storage 或 Driver/HAL 数据面；这些仍按 NV-P-006 计划态推进。

| Topic | 载荷 | 订阅方 |
| --- | --- | --- |
| `vehicle.signal.changed` | signal path、value、quality、timestamp | App、Agent、Trace |
| `service.health.changed` | service、old/new status | Console、Lifecycle |
| `agent.task.updated` | task id、state、step | App、Trace |
| `skill.invocation.completed` | skill id、result、latency | Agent、Audit |
| `policy.decision.created` | decision、reason、caller | Audit、Security |
| `ai.inference.completed` | model、runtime、latency、status | App、Metric |
| `npu.runtime.changed` | device、backend、health | Console、Lifecycle |

## 版本策略

- Contract 使用 `major.minor.patch`。
- 增加可选字段：minor。
- 修改字段语义或删除字段：major。
- mock 阶段使用 JSON contract。
- M2 后引入 OpenAPI。
- M5 后引入 Stable AIDL 草案。
- 车载 SOA 接入时为 SOME/IP/DDS 单独生成映射文件。

## 下一步接口任务

1. 把 `central-brain/contracts/central_brain_api.json` 扩展为按域组织的 contract。
2. 将 `/governance/precheck` 后续迁移到共享治理 daemon，并让 Binder/Linux IPC/gRPC 复用同一治理状态与 QoS 窗口；当前 Linux IPC/gRPC 已先把 precheck 和 runtime/audit 只读诊断收敛到同一 shared socket client。
3. Android Console 增加服务目录、车辆信号、推理、Trace 四个视图。
4. 为所有 mock API 增加 `trace_id`。
5. 增加 smoke test：验证 `/health`、`/services`、`/vehicle/state`、`/policy/evaluate`、`/ai/infer`。

## EV-AHJ Event Subscription Closure Readiness Checklist

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist` 覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。该接口从 EV-AHI acceptance decision rollup 派生 `EV-AHJ-001..010` closure readiness checklist，向 Android/Linux 座舱域工程师说明 acceptance authority、acceptance record store、review workflow、audit retention、evidence packet presence、gate closure authority、broker activation owner、DRV-GAP-004/005 和 Android/Linux parity evidence 仍未满足。

Android 主路径为 Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessChecklistJson` 与 Console `Sub ApHClose`；Linux 同步路径为 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-checklist`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.checklist` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessChecklist`。

该接口固定 `closure_readiness_checklist_complete=true`、`closure_readiness_consistent=true`、`closure_ready=false`、`required_closure_check_count=10`、`open_closure_check_count=10`、`blocked_decision_count=10`、`blocked_acceptance_count=10`、`accepted_evidence_packet_count=0`、`acceptance_record_persisted_count=0`、`missing_evidence_packet_count=10`、`approval_review_allowed=false`、`gate_closure_allowed=false`、`broker_activation_allowed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`。它不接受 packet，不附加 evidence，不分配 owner，不调用 POST，不持久化 evidence/request/result/approval/handoff/review state，不创建 evidence store、approval result store 或 review queue，不关闭 gate，不激活 broker、DDS 或高频数据面，不访问 Driver/HAL，不开发虚拟化层。

## EV-AHK Event Subscription Closure Readiness Audit Consistency

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency` 覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。该接口从 EV-AHJ closure readiness checklist 派生 `EV-AHK-001..010` audit consistency，向 Android/Linux 座舱域工程师说明 closure checklist、acceptance decision rollup、acceptance audit/status、evidence readiness matrix、Driver/HAL gap references 和 no-side-effect 边界互相一致，但 closure 仍被 unresolved authority/evidence/store/review/gate blockers 阻塞。

Android 主路径为 Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessAuditConsistencyJson` 与 Console `Sub ApHClAud`；Linux 同步路径为 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-audit-consistency`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.audit.consistency` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessAuditConsistency`。

该接口固定 `consistency_passed=true`、`source_closure_readiness_checklist_bound=true`、`source_decision_rollup_bound=true`、`source_acceptance_audit_bound=true`、`source_acceptance_status_bound=true`、`source_evidence_readiness_bound=true`、`closure_check_count_consistent=true`、`closure_blocker_state_consistent=true`、`android_linux_parity_consistent=true`、`no_store_consistent=true`、`no_post_consistent=true`、`no_side_effects_consistent=true`、`required_audit_count=10`、`open_closure_check_count=10`、`blocked_decision_count=10`、`blocked_acceptance_count=10`、`accepted_evidence_packet_count=0`、`acceptance_record_persisted_count=0`、`closure_ready=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`。它不接受 packet，不附加 evidence，不分配 owner，不调用 POST，不持久化 evidence/request/result/approval/handoff/review state，不创建 evidence store、approval result store 或 review queue，不关闭 gate，不激活 broker、DDS 或高频数据面，不访问 Driver/HAL，不开发虚拟化层。

## EV-AHL Event Subscription Closure Readiness Decision Rollup

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup` 覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。该接口从 EV-AHK closure readiness audit consistency 派生 `EV-AHL-001..010` decision rollup，向 Android/Linux 座舱域工程师说明 closure readiness audit、closure blockers、acceptance decision、no-store、no-POST、Android/Linux parity 和 no-side-effect 边界虽然一致，但 closure decision 仍被 unresolved authority/evidence/store/review/gate blockers 阻塞。

Android 主路径为 Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionRollupJson` 与 Console `Sub ApHClR`；Linux 同步路径为 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-rollup`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.rollup` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionRollup`。

该接口固定 `decision_rollup_complete=true`、`decision_rollup_consistent=true`、`closure_readiness_audit_consistent=true`、`closure_ready=false`、`closure_decision_ready=false`、`required_decision_count=10`、`blocked_decision_count=10`、`open_closure_check_count=10`、`blocked_acceptance_count=10`、`accepted_evidence_packet_count=0`、`acceptance_record_persisted_count=0`、`review_queue_updated=false`、`gates_closed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`。它不接受 packet，不附加 evidence，不分配 owner，不调用 POST，不持久化 evidence/request/result/approval/handoff/review state，不创建 evidence store、approval result store 或 review queue，不关闭 gate，不激活 broker、DDS 或高频数据面，不访问 Driver/HAL，不开发虚拟化层。

## EV-AHM Event Subscription Closure Decision Reviewer Assignment Checklist

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist` 覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。该接口从 EV-AHL closure readiness decision rollup 派生 `EV-AHM-001..010` reviewer assignment checklist，向 Android/Linux 座舱域工程师说明 closure review authority、evidence acceptance reviewer、gate closure reviewer、broker activation reviewer、DRV-GAP-004/005 reviewer 和 Android/Linux parity reviewer 仍未分配。

Android 主路径为 Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentChecklistJson` 与 Console `Sub ApHRev`；Linux 同步路径为 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-checklist`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.checklist` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentChecklist`。

该接口固定 `reviewer_assignment_checklist_complete=true`、`reviewer_assignment_ready=false`、`source_decision_rollup_bound=true`、`required_reviewer_assignment_count=10`、`assigned_reviewer_count=0`、`unassigned_reviewer_count=10`、`reviewer_assignments_persisted=false`、`reviewer_assignment_queue_updated=false`、`closure_ready=false`、`closure_decision_ready=false`、`approval_review_allowed=false`、`gate_closure_allowed=false`、`broker_activation_allowed=false`、`activation_allowed=false`、`review_queue_updated=false`、`gates_closed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`。它不分配 reviewer，不接受 packet，不附加 evidence，不调用 POST，不持久化 request/result/approval/handoff/review/evidence/reviewer state，不创建 evidence store、approval result store 或 review queue，不关闭 gate，不激活 broker、DDS 或高频数据面，不访问 Driver/HAL，不开发虚拟化层。

## EV-AHN Event Subscription Closure Decision Reviewer Assignment Audit Consistency

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency` 覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。该接口从 EV-AHM reviewer assignment checklist 派生 `EV-AHN-001..010` audit consistency，向 Android/Linux 座舱域工程师说明 reviewer assignment checklist、count、blocked state、no-store、no-POST、no-queue/gate/broker、Android/Linux parity 和 no-side-effect 仍一致。

Android 主路径为 Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistencyJson` 与 Console `Sub ApHRvA`；Linux 同步路径为 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-consistency`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.consistency` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistency`。

该接口固定 `consistency_passed=true`、`source_reviewer_assignment_checklist_bound=true`、`reviewer_assignment_count_consistent=true`、`reviewer_assignment_blocker_state_consistent=true`、`no_store_consistent=true`、`no_post_consistent=true`、`no_queue_gate_broker_consistent=true`、`no_side_effects_consistent=true`、`reviewer_assignment_ready=false`、`assigned_reviewer_count=0`、`unassigned_reviewer_count=10`、`reviewer_assignments_persisted=false`、`reviewer_assignment_queue_updated=false`、`review_queue_updated=false`、`gates_closed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`。它不分配 reviewer，不接受 packet，不附加 evidence，不调用 POST，不持久化 request/result/approval/handoff/review/evidence/reviewer state，不创建 evidence store、approval result store 或 review queue，不关闭 gate，不激活 broker、DDS 或高频数据面，不访问 Driver/HAL，不开发虚拟化层。

## EV-AHO Event Subscription Closure Decision Reviewer Assignment Audit Decision Rollup

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup` 覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。该接口从 EV-AHN reviewer assignment audit consistency 派生 `EV-AHO-001..010` decision rollup，向 Android/Linux 座舱域工程师说明 reviewer assignment audit 已一致，但 reviewer assignment decision 仍因 reviewer 全部未分配而 blocked。

Android 主路径为 Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupJson` 与 Console `Sub ApHRvRoll`；Linux 同步路径为 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollup`。

该接口固定 `decision_rollup_complete=true`、`decision_rollup_consistent=true`、`source_reviewer_assignment_audit_bound=true`、`reviewer_assignment_audit_consistent=true`、`reviewer_assignment_decision_blocked=true`、`reviewer_assignment_decision_ready=false`、`reviewer_assignment_decision=blocked-by-unassigned-reviewers`、`required_decision_count=10`、`blocked_decision_count=10`、`assigned_reviewer_count=0`、`unassigned_reviewer_count=10`、`reviewer_assignments_persisted=false`、`reviewer_assignment_queue_updated=false`、`review_queue_updated=false`、`gates_closed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`。它不分配 reviewer，不接受 packet，不附加 evidence，不调用 POST，不持久化 request/result/approval/handoff/review/evidence/reviewer state，不创建 evidence store、approval result store 或 review queue，不关闭 gate，不激活 broker、DDS 或高频数据面，不访问 Driver/HAL，不开发虚拟化层。

## EV-AHP Event Subscription Closure Handoff Readiness Summary

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary` 覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。该接口从 EV-AHO reviewer assignment audit decision rollup 派生 `EV-AHP-001..010` closure handoff readiness summary，向 Android/Linux 座舱域工程师说明 closure handoff dependencies 已被汇总，但 handoff 仍因 reviewer 全部未分配而 blocked。

Android 主路径为 Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummaryJson` 与 Console `Sub ApHReady`；Linux 同步路径为 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-summary`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.summary` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummary`。

## EV-AHQ Event Subscription Closure Handoff Readiness Audit Consistency

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary/audit-consistency` 覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。该接口从 EV-AHP closure handoff readiness summary 派生 `EV-AHQ-001..010` audit consistency，校验 source summary binding、handoff dependency count、blocked state、Android/Linux parity、no-store、no-POST、no-queue/gate/broker、Driver/HAL/virtualization boundary 和 no-side-effect 是否一致。

Android 主路径为 Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditConsistencyJson` 与 Console `Sub ApHReadyA`；Linux 同步路径为 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-audit-consistency`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.audit.consistency` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditConsistency`。

返回体固定 `consistency_passed=true`、`source_closure_handoff_readiness_summary_bound=true`、`closure_handoff_dependency_count_consistent=true`、`closure_handoff_blocker_state_consistent=true`、`no_store_consistent=true`、`no_post_consistent=true`、`no_queue_gate_broker_consistent=true`、`no_side_effects_consistent=true`、`closure_handoff_ready=false`、`handoff_ready=false`、`required_handoff_dependency_count=10`、`open_handoff_dependency_count=10`、`assigned_reviewer_count=0`、`unassigned_reviewer_count=10`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。该接口只读，不分配 reviewer，不接受 evidence packet，不创建 queue/store，不关闭 gate，不激活 broker/DDS/high-rate data plane，不触发 Driver/HAL 或虚拟化。

该接口固定 `closure_handoff_readiness_complete=true`、`closure_handoff_ready=false`、`handoff_ready=false`、`source_decision_rollup_bound=true`、`decision_rollup_consistent=true`、`reviewer_assignment_decision_blocked=true`、`reviewer_assignment_decision=blocked-by-unassigned-reviewers`、`required_handoff_dependency_count=10`、`open_handoff_dependency_count=10`、`assigned_reviewer_count=0`、`unassigned_reviewer_count=10`、`reviewer_assignments_persisted=false`、`reviewer_assignment_queue_updated=false`、`review_queue_updated=false`、`gates_closed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`。它不分配 reviewer，不接受 packet，不附加 evidence，不调用 POST，不持久化 request/result/approval/handoff/review/evidence/reviewer state，不创建 evidence store、approval result store 或 review queue，不关闭 gate，不激活 broker、DDS 或高频数据面，不访问 Driver/HAL，不开发虚拟化层。

## EV-AHR Event Subscription Closure Handoff Readiness Audit Decision Rollup

`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary/audit-consistency/decision-rollup` 覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。该接口从 EV-AHQ closure handoff readiness audit consistency 派生 `EV-AHR-001..010` audit decision rollup，说明 closure handoff audit 已一致，但 closure handoff audit decision 仍因 reviewer 未分配而 blocked。

Android 主路径为 Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditDecisionRollupJson` 与 Console `Sub ApHReadyR`；Linux 同步路径为 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-audit-decision-rollup`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.audit.decision.rollup` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditDecisionRollup`。

返回体固定 `decision_rollup_complete=true`、`decision_rollup_consistent=true`、`closure_handoff_audit_decision_ready=false`、`closure_handoff_audit_decision_blocked=true`、`closure_handoff_audit_decision=blocked-by-unassigned-reviewers`、`required_decision_count=10`、`blocked_decision_count=10`、`passed_audit_count=10`、`failed_audit_count=0`、`assigned_reviewer_count=0`、`unassigned_reviewer_count=10`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。该接口只读，不分配 reviewer，不接受 evidence packet，不创建 queue/store，不关闭 gate，不激活 broker/DDS/high-rate data plane，不触发 Driver/HAL 或虚拟化。

### EV-AHS Event subscription closure blocker matrix

- REST: `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary/audit-consistency/decision-rollup/closure-blocker-matrix`
- Android: `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditDecisionRollupClosureBlockerMatrixJson` via Console `Sub ApHReadyB`
- Linux: `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-audit-decision-rollup-closure-blocker-matrix`, `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.audit.decision.rollup.closure.blocker.matrix`, `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditDecisionRollupClosureBlockerMatrix`
- Payload: `EV-AHS-001..010` blocker items derived from `EV-AHR-001..010`, with `closure_blocker_matrix_complete=true`, `closure_blocker_matrix_consistent=true`, `closure_handoff_closure_ready=false`, `open_blocker_count=10`, `closed_blocker_count=0`, `driver_development_triggered=false`, and `virtualization_development_triggered=false`.
- Scope: read-only contract surface; no POST, persistence, queue, gate, broker, hardware, Driver/HAL, or virtualization work.
