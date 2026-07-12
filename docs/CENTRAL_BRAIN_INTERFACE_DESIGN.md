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

### EV-AE..EV-AHS prototype readiness closure chain summary

- REST: `GET /prototype/readiness`
- Android: `getPrototypeReadinessJson`
- Linux: `prototype-readiness`, `prototype.readiness.get`, `GetPrototypeReadiness`
- Payload: `event_subscription_activation_closure_chain_summary` with `source_range=EV-AE..EV-AHS`, 30 stage ids, `event_subscription_activation_closure_chain_stage_count=30`, `event_subscription_activation_closure_chain_ready=false`, `first_gate=EV-AE-001`, `last_gate=EV-AHS-010`, Android/Linux binding parity, no-store/no-POST/no-side-effect consistency, `driver_development_triggered=false`, `virtualization_development_triggered=false`, and `service_dispatch_triggered=false`.
- Scope: read-only readiness summary over existing EV-AE..EV-AHS surfaces; no new deep endpoint, no owner/reviewer assignment, no evidence persistence, no queue/gate/broker activation, no hardware, no Driver/HAL, and no virtualization work.

## Android R2 Typed AIDL Contract

The Android product runtime no longer extends the legacy String/JSON Binder surface for new business operations. R2 uses `ICentralBrainRuntime` for typed task control, `ICentralBrainTaskCallback` for oneway async results, and `ICentralBrainDiagnostics` for bounded read-only cursor pages. Full V1 types, method latency, cancellation, death handling, version/hash rules and the no-hardware boundary are defined in `CENTRAL_BRAIN_ANDROID_AIDL_CONTRACT.md`.

This is Gradle application structured AIDL because the project cannot modify the prebuilt vendor/AOSP Soong build. It must not be labeled VINTF stable AIDL. Req IDs: `XSC-001`, `XSC-004`, `XSC-005`, `XSC-006`, `NV-F-001`, `NV-G-003`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-003`, `DEL-004`; deviation/issue tracking: `DEV-018`, `ISSUE-021`.

R2B published `ICentralBrainRuntime` and `ICentralBrainDiagnostics` from separate signature-protected Service components. `CentralBrainClient` uses an explicit component, narrow package visibility, executor-dispatched callbacks and a service `DeathRecipient`; Runtime task work runs off Binder threads and diagnostic pages remain bounded/read-only. Its API 33 evidence covered protocol negotiation, completion, duplicate cancel, permission denial and diagnostic paging; process-death, rebind and cancel-completion race evidence was completed in R2C.

R2C completes that lifecycle contract: death recipients are scoped to exact Binder instances, active callbacks fail once with `ERROR_SERVICE_DIED`, `reconnect()` explicitly unbinds/rebinds, and post-terminal updates are suppressed. API 33 instrumentation covers Runtime force-stop/recovery, duplicate disconnect suppression, 15-task cancel-completion races and separate client-process death. The typed Android Protocol Binding is `android_integrated`; legacy JSON Binder/HTTP migration remains DEV-018/ISSUE-021 work for R7.

## Android R3A Job Supervisor And Trusted Identity Interfaces

R3A keeps the frozen V1 AIDL unchanged and adds internal AIOS Kernel/Runtime & Governance interfaces:

| Interface | Input | Output/failure | Owner |
| --- | --- | --- | --- |
| `JobSupervisor.admit` | Runtime task ID, resolved caller snapshot, initial message | accepted snapshot + pressure-evicted terminal IDs; rejects duplicate ID, unresolved owner or full active registry | AIOS Kernel `NV-F-001` |
| `JobSupervisor.transition` | task ID, target state, monotonic progress, message | applied/latest snapshot; rejects illegal transition or progress regression | AIOS Kernel/Lifecycle `NV-G-006` |
| `JobSupervisor.cancelOwned` | task ID, trusted current caller, reason message | applied, already-cancelled, terminal, or not-found/not-owner without existence disclosure | Permission/Policy `FW-U-007`, `NV-G-005` |
| `JobSupervisor.findOwned` | task ID, trusted current caller | snapshot or null for both missing and non-owner | Runtime & Governance `XSC-005` |
| `JobSupervisor.pruneExpired` | elapsed realtime | terminal task IDs removed after retention; never removes active work | Lifecycle/Audit `NV-G-006/007` |
| `JobSupervisor.markTerminalDeliverySettled` | terminal task ID after completion/failure callback attempt or confirmed callback death | makes the terminal record eligible for later retention/pressure eviction; rejects non-terminal settlement | Lifecycle/Audit `NV-G-006/007` |
| `AndroidCallerIdentityResolver.resolveCallingIdentity` | current Binder transaction | UID, Android user serial, sorted package/current-signer SHA-256 evidence; unresolved result fails closed | Protocol Binding/Policy `XSC-006`, `NV-P-002` |

The package and each current signer remain paired in `CallerIdentitySnapshot.PackageIdentity`; a flat package/digest cross-product is forbidden. No request field participates in identity or authorization. R3B consumes this snapshot through a package+signer capability policy and default-deny unknown clients; R3C will add trusted Safety/Vehicle State and approval inputs.

## Android R3B Capability Policy Interfaces

| Interface | Input | Output/failure | Rule |
| --- | --- | --- | --- |
| `AndroidCapabilityPolicyLoader.load` | APK XML + Runtime own trusted identity | immutable policy or startup failure | root must be V1/default deny; only literal package and `runtime-current` signer rules |
| `CallerCapabilityPolicy.evaluate` | complete caller snapshot + capability enum | allow or stable deny reason | exact package/current-signer pair; no request assertions or signer intersection |
| `resolveAuthorizedCaller` | active Binder caller + required production/diagnostic capability | trusted caller snapshot or `SecurityException` | runs before request parsing/task lookup and writes bounded denial audit |

Production capability IDs are `runtime.protocol.read`, `runtime.task.submit`, `runtime.task.status.own`, and `runtime.task.cancel.own`; diagnostics use `runtime.diagnostics.read`. For shared UID identities, correctly signed configured packages contribute capabilities to the UID principal; a signer mismatch on any configured package fails closed. Stable denial reasons are `IDENTITY_UNRESOLVED`, `PACKAGE_NOT_CONFIGURED`, `CURRENT_SIGNER_MISMATCH`, and `CAPABILITY_NOT_GRANTED`.

`policy-probe` is a separate same-signer APK used only to prove that manifest signature permission is not treated as capability authorization. It binds both Services successfully but is absent from the policy, so every production method and diagnostic read is denied on API 33. It is not an AI SDK or Runtime delivery module and adds no architecture layer.

## Android R3C1 Action Governance Core Interfaces

R3C1 keeps the frozen task/diagnostic AIDL unchanged and defines the internal Governance core that R3C2 publishes through a separate typed Binder.

| Interface | Trusted input | Output/failure | Boundary |
| --- | --- | --- | --- |
| `SafetyVehicleStateProvider.currentSnapshot` | Runtime-owned provider only | immutable source/revision/Safety/Motion/driver snapshot | no Binder payload state; current stub is not production trusted |
| `ActionGovernancePolicy.classify` | exact stable Action ID | one of five risk classes or `UNKNOWN` | caller cannot submit or lower a risk class |
| `ActionGovernancePolicy.evaluate` | Action ID + provider snapshot | `ALLOW_POLICY_ONLY`, `APPROVAL_REQUIRED`, or `DENY` with stable reason | always `dispatchAllowed=false` |
| `InMemoryApprovalRegistry.request` | high-risk approval-required decision + trusted caller snapshot | owner-bound `PENDING` record or capacity rejection | no approval grant and no durable recovery |
| `InMemoryApprovalRegistry.findOwned` | approval ID + trusted current caller | snapshot or null for missing/non-owner | no existence disclosure across owners |
| `InMemoryApprovalRegistry.cancelOwned` | approval ID + trusted current caller | idempotent cancel or false | expired/missing/non-owner cannot be cancelled |

The exact Action catalog is `vehicle.state.read`, `cabin.temperature.set`, `driver.display.video.play`, `vehicle.diagnostics.write`, and `system.ota.install`. Read-only remains visible even during emergency state; comfort is policy-only when Safety/Motion are known; driver-distraction, diagnostic-write and OTA require parked/normal/driver-available state before a pending approval may be created. Moving high-risk actions are denied before approval creation.

The current provider uses `RUNTIME_OWNED_STUB` with `hardwareBacked=false` and `productionTrusted=false`. The current registry uses bounded process memory with `supportsApprovalGrant=false` and `isDurable=false`. These are deliberate R3C1 limits, not approval completion or target Safety evidence. Req IDs: `FW-U-004`, `FW-U-007`, `FW-S-005`, `XSC-005`, `XSC-006`, `NV-G-005`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-004`.

## Android R3C2 Typed Governance Binder Interfaces

The Android product path publishes `ICentralBrainGovernance` as an independent production control plane. It is not part of the legacy JSON gateway and does not change task/diagnostic V1 transaction order.

| Binder method | Required inner capability | Result | Side-effect boundary |
| --- | --- | --- | --- |
| `getProtocolVersion/getProtocolHash` | `governance.protocol.read` | Governance V1 identity | no policy or approval mutation |
| `evaluateAction(ActionRequest)` | `governance.action.evaluate` | typed risk/outcome/reason + Runtime state-source metadata | no approval creation and no dispatch |
| `requestApproval(ActionRequest)` | `governance.approval.request` | owner-bound pending `ApprovalHandle` | only high-risk approval-required decisions; no grant |
| `getApprovalStatus(ApprovalHandle)` | `governance.approval.status.own` | owner status or `UNKNOWN` | missing/non-owner indistinguishable |
| `cancelApproval(ApprovalHandle)` | `governance.approval.cancel.own` | idempotent true for owner-cancelled, false otherwise | no action dispatch |

Outer access uses `com.centralbrain.permission.BIND_GOVERNANCE` (`signature`). Inner policy still requires exact package plus complete current signer set, so sharing the signer alone does not authorize a package. `CentralBrainGovernanceClient` binds the explicit Service component, negotiates version/hash and handles Binder death; it never accepts a caller-supplied identity or state provider.

`ActionRequest` has exactly `schemaVersion`, `clientRequestId`, `actionId`, and `idempotencyKey`. `ActionDecision` exposes derived risk/outcome and Runtime state-source metadata. `ApprovalHandle/ApprovalStatus` expose bounded pending/cancelled/expired state; status always reports grant/durable/dispatch false. Governance V1 is frozen by `central-brain-sdk/aidl-api/governance-v1.sha256` and intentionally has no approval resolution method.

API 33 allowed-client evidence is emitted by `tools/install_central_brain_android_runtime.sh`; same-signer unknown-client denial is emitted by `tools/test_central_brain_android_capability_policy.sh`. Req IDs: `FW-U-004`, `FW-U-007`, `FW-S-005`, `XSC-005`, `XSC-006`, `NV-G-005`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-004`.

## Android R4A Durable Schema Interfaces

R4A establishes the persistence ownership boundary without changing production AIDL or wiring a dispatcher.

| Room object/table | Key and integrity rule | Intended R4 owner |
| --- | --- | --- |
| `runtime_session` | session PK; unique owner fingerprint + session key | session lifecycle |
| `runtime_task` | task PK; unique owner fingerprint + idempotency key | Job Supervisor durable mirror |
| `task_checkpoint` | checkpoint PK; unique task + sequence; task FK cascade | task step/checkpoint recovery |
| `pending_effect` | effect PK; global unique idempotency key; task FK cascade | prepare-before-side-effect boundary |
| `effect_outbox` | outbox PK; one row per effect; effect FK cascade | retryable delivery intent |
| `approval_request` | approval PK; unique owner fingerprint + idempotency key | durable pending/decision lifecycle |
| `audit_event` | auto sequence; unique event ID | ordered durable governance audit |
| `event_cursor` | cursor PK; unique owner fingerprint + topic | replay/cursor recovery |

`CentralBrainDatabase.open` configures WAL and only registers explicit `MIGRATION_1_2`; destructive fallback is forbidden. `RuntimeStateDao` currently exposes migration reads and insert primitives. It is not yet the production repository API, and production Services do not open it in R4A.

The debug migration probe creates a separate v1 database, inserts task/approval rows, migrates to v2, checks the eight-table schema and WAL, then deletes only the probe database. Payload-bearing columns are digests, not raw payload. R4B must add transactions and repository invariants; R4C must add restart/outbox recovery. Req IDs: `FW-U-004`, `NV-F-001`, `NV-G-006`, `NV-G-007`, `XSC-005`, `XSC-006`, `DEL-001`, `DEL-004`.
## Android R4B1 Durable Task Admission

R4B1 adds an internal Java repository boundary; it does not change frozen task/diagnostic or Governance AIDL.

| Interface | Input | Output/failure | Transactional rule |
| --- | --- | --- | --- |
| `DurablePrincipalFingerprint.from` | resolved trusted caller snapshot | lowercase SHA-256 owner fingerprint; unresolved identity throws | canonical Android user + package/current-signer pairs; UID excluded |
| `DurableTaskRepository.admit` | owner fingerprint, session/client request/idempotency metadata, payload digest | `CREATED` or `REPLAYED` admission; mismatched replay throws `IdempotencyConflictException` | owner/key lookup + task insert + `TASK_ACCEPTED` audit insert in one Room transaction |
| `RuntimeStateDao.findTaskByOwnerAndIdempotency` | owner fingerprint + idempotency key | matching mutable entity or null, internal only | backed by `index_runtime_task_owner_idempotency` unique index |

The repository never accepts a raw utterance, caller-supplied identity, risk classification or permission assertion. `payloadDigest` is a lowercase SHA-256 placeholder; production keying/HMAC policy remains open under ISSUE-022. R4B1 is not referenced by production Services, does not write checkpoint/effect/outbox rows, and cannot dispatch an Action.

## Android R4B2 Durable Runtime Wiring

Frozen `ICentralBrainRuntime` V1 is unchanged. Durability is an internal Service/repository contract.

| Call/path | Durable behavior | Callback/recovery behavior |
| --- | --- | --- |
| `submitAgentTask` new key | trusted owner + request digest; task/ACCEPTED checkpoint/audit commit before handle | schedules deterministic stub only after durable admission |
| `submitAgentTask` exact live replay | returns original handle; no new task/checkpoint/audit; admission-to-live-map publication is serialized | same callback Binder is deduplicated; up to four observer Binders receive current and terminal events |
| `submitAgentTask` existing but unrecovered | returns original handle | emits durable status then retryable `ERROR_INTERNAL`; no execution until R4C |
| RUNNING/terminal transition | task update + next checkpoint + transition audit in one transaction | Job Supervisor advances after commit |
| terminal callback settlement | task settled flag + settlement audit, idempotent | executed after callback attempts; failed persistence remains pending |
| `getTaskStatus` | live owner snapshot first, then owner-fingerprint Room lookup | durable fallback message states recovery is pending |

`DurableDigest` length-frames every UTF-8 field and domain-separates request/checkpoint/settlement hashes. Deadline policy is transactional: exact existing replay is returned even when creation is no longer allowed; a new expired request throws before any task row is inserted. R4B2 does not expose database handles through AIDL and does not access pending-effect/outbox dispatch.

## Android R4B3 Durable Approval

Frozen `ICentralBrainGovernance` V1 remains unchanged; the implementation backing changes from process-local registry to Room.

| Repository call | Result | Transaction rule |
| --- | --- | --- |
| `request(owner,key,action,risk,reason,creationAllowed)` | `CREATED` or `REPLAYED`; conflict/rejection/capacity exception | expire due rows, lookup owner/key, then optional PENDING insert + request audit |
| `findOwned(approvalId,owner)` | durable snapshot or null | expire due rows first; non-owner is indistinguishable from missing |
| `cancelOwned(approvalId,owner)` | `APPLIED`, `REPLAYED`, `NOT_FOUND`, `NOT_PENDING` | one PENDING→CANCELLED update + one cancel audit |

The persisted equivalence key is owner + idempotency key + exact Action ID. `clientRequestId` remains tracing metadata and does not create another approval for the same operation key. Stored risk/reason are Runtime-derived originals. Replaying an existing approval under changed policy returns its current PENDING/CANCELLED/EXPIRED state but cannot grant or dispatch it. Wall timestamps are converted to elapsed-realtime fields for AIDL responses; trusted clock and reboot/direct-boot qualification remain open.

## Android R4C1 Fail-Closed Restart Reconciliation

Frozen production AIDL remains unchanged. Restart behavior is an internal Runtime/Room contract.

| Interface/path | Behavior | Boundary |
| --- | --- | --- |
| `RuntimeStateDao.findTasksNeedingRestartReconciliation` | selects ACCEPTED/RUNNING and COMPLETED with unsettled terminal delivery | internal DAO only; owner-scoped Binder authorization remains at Service entry |
| `DurableTaskRepository.reconcileInterruptedTasks` | atomically changes each selected task to FAILED and inserts the next checkpoint plus restart audit | idempotent second pass; no raw payload reconstruction |
| Runtime startup Future barrier | runs Room work on the single task executor; task submit/cancel/status await completion | no main-thread database transaction and no admission/reconciliation race |
| exact replay without live record | returns existing handle, sends FAILED update then retryable `ERROR_INTERNAL`, then attempts durable settlement | no task execution resume, effect creation, outbox delivery or hardware dispatch |
| SDK `SerialExecutor` per callback | preserves update-before-terminal delivery over a concurrent caller executor | terminal remains exactly once and updates are not reordered behind it |

A COMPLETED task with an unsettled callback is conservatively changed to FAILED because R4C1 stores no result payload that can be proven equivalent after process loss. This is an explicit availability tradeoff in favor of no false-success/no duplicate-effect semantics. R4C2 owns pending-effect/outbox state; real resumable task execution requires an approved durable input/result format and is not implied by `restart_reconciliation_enabled=true`.

## Android R4C2A Effect Prepare And Claim

`DurableEffectRepository` is an internal Java/Room boundary and is not referenced by a production Service in this increment.

| Call | Transactional result | Explicit non-result |
| --- | --- | --- |
| `prepare(owner,task,key,type,action,payloadDigest,destination,envelopeDigest)` | owner/key digest lookup; exact replay or PREPARED effect + PENDING outbox + audit | no adapter lookup or dispatch |
| `claimNext(destination)` | due/eligible row moves PREPARED/PENDING→IN_FLIGHT/IN_FLIGHT, attempt increments, audit appends | returns digest metadata only; does not call destination |
| `reconcileInterruptedClaims()` | interrupted pairs return to PREPARED/PENDING at current `not_before`, one recovery audit each | startup/offline reconciliation only; does not assert whether an external side effect occurred |
| `findOwned(effectId,owner)` | owner-isolated effect/outbox snapshot | no cross-owner existence disclosure |

The stored `idempotency_key` is a domain-separated owner+caller-key digest, called the idempotency token in repository snapshots. A claim carries IDs, owner token, type/action, payload/envelope digests, destination, state and attempt count; no raw command exists to dispatch. Route pairs are fixed to UIB Action, SOA Operation and Skill. Because an IN_FLIGHT crash is ambiguous once a real adapter exists, requeue alone provides at-least-once infrastructure, not exactly-once execution; adapter idempotency/status contracts remain a hard activation gate.

## Android R4C2B Effect Retry And Terminal States

The internal Room API closes local effect lifecycle semantics while keeping all destination adapters disconnected.

| Call | Required current state | Transactional result |
| --- | --- | --- |
| `recordSuccess(effect,outbox,owner,expectedAttempt,resultDigest)` | matching IN_FLIGHT pair and attempt | APPLIED/DELIVERED + `EFFECT_APPLIED`; exact replay returns `REPLAYED` |
| `scheduleRetry(effect,outbox,owner,expectedAttempt,delay,failureDigest)` | matching non-final IN_FLIGHT pair | PREPARED/PENDING + bounded `not_before` + `EFFECT_RETRY_SCHEDULED` |
| `deadLetter(effect,outbox,owner,expectedAttempt,failureDigest)` | matching IN_FLIGHT pair | FAILED/DEAD_LETTER + `EFFECT_DEAD_LETTERED` |
| `cancelPrepared(effect,outbox,owner,expectedAttempt,reasonDigest)` | matching PREPARED/PENDING pair and attempt | CANCELLED/CANCELLED + `EFFECT_CANCELLED` |
| `reconcileInterruptedClaims()` | IN_FLIGHT pairs after reopen | attempts remaining: requeue; exhausted: FAILED/DEAD_LETTER + `EFFECT_CLAIM_EXHAUSTED` |

Result, failure and cancellation content enters Room only as lowercase SHA-256 digests. Replay validation binds IDs, expected attempt and operation-specific input; retry also binds the requested delay and persisted not-before timestamp. Default `maxAttempts=3`, constructor bounds are 1..100, and delay bounds are 0..24 hours.

`EFFECT_CLAIM_EXHAUSTED` is a local fail-closed outcome for an unknown final-attempt result. It does not authorize blind re-delivery and does not state that the destination never applied the operation. No dispatcher, adapter call or production Service wiring exists in R4C2B; R4C3 must supply destination idempotency/status interfaces and crash-point evidence first.

## Android R4C3A Effect Adapter Contract

`EffectAdapter` is an internal Java contract, not a Binder, HAL or vendor implementation.

| Interface | Input | Result and invariant |
| --- | --- | --- |
| `descriptor()` | none | adapter ID/destination, `TOKEN_DEDUPLICATED`, duplicate returns original, APPLIED status returns original evidence, `LINEARIZABLE` status, bounded operation timeout |
| `apply(Invocation)` | effect/outbox IDs, persisted token, route/action, attempt, bounded transient canonical payload/envelope | typed apply state + echoed token + evidence digest; duplicate token must not repeat the side effect |
| `queryStatus(token)` | persisted idempotency token | NOT_APPLIED/APPLIED/REJECTED/UNKNOWN + echoed token + evidence digest; query transport failure is a distinct exception |
| `EffectAdapterContract.requireSafe` | adapter + expected destination | rejects route mismatch, non-token idempotency, changed duplicate result or non-linearizable status |
| `EffectStatusReconciler.reconcile` | durable IN_FLIGHT snapshot + safe adapter + retry delay | queries status only and atomically calls repository success/retry/dead-letter, or defers with no mutation |

APPLIED maps to APPLIED/DELIVERED. NOT_APPLIED maps to PREPARED/PENDING only while attempts remain and otherwise to FAILED/DEAD_LETTER. REJECTED/UNKNOWN map to FAILED/DEAD_LETTER. Adapter unavailability keeps IN_FLIGHT unchanged so a transport failure is not mistaken for destination state.

The debug fixture retains token status only in process memory and receives canonical bytes directly from the probe. It validates the algorithm across Room close/reopen but does not solve command-material recovery after process death. No production Service references these interfaces in R4C3A; R4C3B must bind any retry-capable activation to a trusted durable material source whose confidentiality, digest verification and lifecycle are explicit.

## Android R4C3B Effect Material Activation Gate

| Interface | Input | Output/failure |
| --- | --- | --- |
| `EffectMaterialSource.descriptor` | none | availability, assurance, restart durability, at-rest encryption, effect integrity binding, deletion and retention metadata |
| `EffectMaterialSource.resolve` | durable claim | canonical payload/envelope + effect ID + source revision, or `MaterialUnavailableException` |
| `EmptyEffectMaterialSource` | any claim | always unavailable; current main-source product boundary |
| `EffectDeliveryActivationGate.evaluate` | adapter, material source, expected destination | ordered blocker list; no material resolution, adapter status query or apply |
| `EffectDeliveryActivationGate.resolveInvocation` | blocker-free adapter/source + IN_FLIGHT claim | digest-verified `EffectAdapter.Invocation`, or activation/material/integrity failure |

Stable blockers cover missing/unsafe adapter; missing/invalid/empty/non-production source; non-durable, unencrypted or integrity-unbound material; missing delete support; and invalid retention. A blocker-free result is only a code-level necessary condition: target evidence must still bind the actual signed provider, key owner, storage policy and vendor adapter conformance.

The current production configuration has no adapter and uses the empty source, so it remains blocked. The debug synthetic source can exercise the positive branch and resolve bytes after a Room reopen, but it does not survive process death and is never a release implementation. R4C3B does not add payload columns or blobs to Room.

## Android R4C3C Production Activation Snapshot

`EffectDeliveryActivationSnapshot.current()` is the only production visibility object. It is an immutable singleton derived from `EffectDeliveryActivationGate.evaluate(null, EmptyEffectMaterialSource, UIB_ACTION)`. It exposes activation, adapter/material/apply/status booleans, source ID, ordered blockers and a bounded diagnostic detail string.

`CentralBrainRuntimeService` reads the snapshot for startup logging and protected Service dumpsys. `CentralBrainDiagnosticService` reads the same snapshot for record ID `effect-delivery-activation`, summary `blocked`, sequence 4 in the existing cursor-paged diagnostic interface. No new AIDL transaction or Parcelable is added.

This interface is observation-only. Neither Service gets an adapter or material-source handle from the snapshot; neither can call apply/query/resolve or claim an outbox. Current blocker visibility therefore cannot be used as an activation command.

## Android R5A1 Model Provider Contract

映射 Req ID：`APP-004`、`XSC-001`、`XSC-004`、`NV-F-011`、`NV-G-004`、`NV-G-006`、`DEL-001`、`DEL-004`、`DEL-005`。

| 接口/类型 | 调用方 -> 实现方 | 语义 | 当前实现 |
| --- | --- | --- | --- |
| `ModelProvider.descriptor()` | Model Router -> Provider | backend/assurance/fallback/operation/concurrency immutable contract | 类型已实现；无 provider instance |
| `snapshot()` | Scheduler/Diagnostics -> Provider | lifecycle、health、loaded/active/queued、hardware evidence | profile snapshot only |
| `warmup(ModelSpec)` | Model Router -> Provider | model id/version/artifact digest lifecycle transition | contract only |
| `infer(InferenceRequest, StreamObserver)` | Scheduler -> Provider | deadline-bound async inference; chunks transient and <=64 KiB | contract only |
| `cancel(requestId, reason)` | Scheduler -> Provider | cancelled/pending-ack/terminal/not-found/unsupported | contract only |
| `metrics()` / `lastFault()` | Governance/Diagnostics -> Provider | bounded counters and fault/isolation status | contract only |
| `close()` | Runtime lifecycle -> Provider | stop provider and reject new work | contract only |

`deterministic.stub` 为 TEST_ONLY、COLD、1 个声明 slot，所有 operation 仅表示 R5B 要实现的 contract，当前未配置/未路由。`vendor.npu.empty` 为 EMPTY/UNAVAILABLE、0 slot，只能暴露 unavailable health/fault metadata。Stub/Ollama debug 不能声明 production/hardware；EMPTY 不能声明 inference/fallback。R5A2 Scheduler 才能形成排队/准入调用关系，R5B 才允许 Router 调用 deterministic provider。

## Android R5A2 Inference Resource Scheduler

| 接口/类型 | 输入 | 输出/约束 |
| --- | --- | --- |
| `TrustedSubmission.fromRuntimePolicy` | request/owner/model/provider、effective priority、elapsed task deadline、queue wait | 唯一 priority 构造入口；不接受 Binder payload priority |
| `admit` | trusted submission | ADMITTED/REPLAYED/duplicate/deadline/timeout/global-owner quota/route unavailable |
| `claimNext` | 无 | 按 priority -> queue deadline -> FIFO -> request ID 选择，返回 lease；同时返回 deadline sweep report |
| `cancelOwned` | request ID + durable owner fingerprint | queued 本地移除；running 返回 provider cancellation directive；非 owner 不泄露 |
| `sweepDeadlines` | injected elapsed-realtime now | queued expiry、running cancellation directive、cancel-unsupported blocker |
| `settle` | request ID + lease ID + provider terminal outcome | stale lease/invalid state/cancel race 检查，释放 slot 并返回 local terminal mapping |
| `RouteTarget.fromProfile` | R5A1 immutable profile | 当前两个 profile 均 disabled |
| `RouteTarget.forContractTest` | `test.*` ID、slot、cancel capability | 仅 unit/debug contract route，不是 production activation |

`CANCEL_REQUESTED` 继续占用 running/global/owner/provider slot，直到 provider acknowledgement；Scheduler 不调用 `ModelProvider.infer/cancel`。Deadline cancellation 的 local terminal 固定为 DEADLINE_EXCEEDED；owner cancellation 后的迟到 COMPLETED 只作为资源释放 acknowledgement，本地映射为 CANCELLED 且不接受输出。Scheduler 只拥有 active resource admission，terminal task/checkpoint/audit 仍由 Job Supervisor 和 Room repository 持有。

## Android R5B1 Deterministic Stub Provider

| 接口 | 行为 | 失败/边界 |
| --- | --- | --- |
| `warmup(ModelSpec)` | allowlisted model ID/version/artifact digest，COLD->READY | mismatch、closed、fault-isolated 拒绝 |
| `infer(request, observer)` | injected executor 两阶段执行；stream sequence 1/2；terminal digest | not-ready、deadline、duplicate、slot-full 拒绝 |
| `cancel(requestId, reason)` | active 标记 cancel，返回 `PENDING_PROVIDER_ACK` | missing/terminal/unsupported typed state |
| `snapshot()` | lifecycle/health/loaded=0..1/active=0..1，hardware=false | 无 queue；Scheduler 单独拥有排队 |
| `metrics()` | accepted/completed/cancelled/failed bounded counters | terminal sum 不得超过 accepted |
| `lastFault()` | NONE/retryable/terminal/fault-isolated code | 仅 test fault injection |
| `close()` | active 终结为 CANCELLED，进入 STOPPED | 重复 close 幂等，禁止 reuse |

Provider output 只由 `modelId + inputDigest` 生成 synthetic bytes；不接收真实 utterance 或 buffer。Terminal history 上限 64。R5B1 不创建 Model Router，不接 Scheduler lease；class availability 与 profile activation 分离，当前 profile 仍 implementation/routing false。

## Android R5B2 Test-Only Model Router

| 接口/类型 | 调用关系 | 输出/约束 |
| --- | --- | --- |
| `createForContractTest(scheduler, provider)` | debug/test composition root -> Router | 只接受 deterministic TEST_ONLY provider；无 production factory |
| `routeTargetForContractTest(provider)` | Router -> Scheduler route catalog | 固定 `test.deterministic.stub`，slot/cancel 来自受检 descriptor |
| `submit(TrustedRouteRequest, observer)` | trusted test caller -> Scheduler -> Provider | typed ADMITTED/REPLAYED/REJECTED/PROVIDER_UNAVAILABLE；claim 后才 infer |
| `pump()` | Router -> Scheduler `claimNext` -> Provider `infer` | lease/request/provider identity 全匹配才计为 dispatched |
| `cancelOwned(requestId, owner, reason)` | caller -> Scheduler directive -> Provider cancel | queued 本地 terminal；running provider ack 后归一化 terminal |
| `tick()` | elapsed clock -> Scheduler sweep -> Provider cancel | queue expiry、running deadline、unsupported cancel 失败关闭 |
| `snapshot()` | debug diagnostics -> Router | bounded counters、`NO_FALLBACK`、production/hardware false |

Provider chunks 仅在 matching active lease 下转发；terminal 先由 Scheduler settle，再向原 observer 交付且最多一次。Exact replay 不替换 observer；changed duplicate 拒绝。Router 不写 Room、不拥有最终 durable task 状态，也不允许 Ollama/Vendor fallback。Production Runtime/Governance 不引用该 class，current profile 仍 configuration/routing false。

## Android R5C1 Model Runtime Readiness Snapshot

| 接口/字段 | 数据来源 | 约束 |
| --- | --- | --- |
| `ModelRuntimeReadinessSnapshot.current()` | immutable `ModelProviderProfiles` | singleton；当前配置若可激活则初始化失败 |
| `diagnosticDetail()` | profile metadata + fixed production wiring flags | bounded key/value detail；无 model material、caller input 或 runtime execution |
| Runtime startup log | shared snapshot | configuration/lifecycle/health/detail/blocker visibility |
| Runtime protected `dump()` | shared snapshot | 与 startup/Diagnostic 值一致；shell DUMP 只读 |
| Diagnostic record `model-runtime-readiness` | shared snapshot | 现有 paged AIDL、signature permission 和 capability policy，不增接口 |

Deterministic Stub 报告 TEST_ONLY/COLD/HEALTHY/`STUB_IMPLEMENTATION_NOT_WIRED`；Vendor NPU 报告 EMPTY/UNAVAILABLE/UNAVAILABLE/`VENDOR_RUNTIME_UNAVAILABLE`。`HEALTHY` 只属于 immutable Stub descriptor/profile contract，不代表 provider instance。Snapshot 不引用 executable Router/Scheduler/provider class，不执行 warmup/infer/cancel/dispatch，production activation 固定 false。

## Android R5D1 Target Deployment Evidence Contract

| Evidence field | Source | Acceptance meaning |
| --- | --- | --- |
| `android_api`, `device_abi`, `device_fingerprint` | public Android properties | API must be exactly 33; identifies evidence environment |
| artifact/signing SHA-256 | host artifacts + `apksigner` | reproducible SDK/APK and common signer identity |
| package path/UID/version | `pm path` + `dumpsys package` | ordinary `/data/app` application deployment |
| manifest SDK/service/permission | `apkanalyzer` | minSdk/targetSdk and three signature-protected Service shape |
| network/native flags | manifest + archive listing | current artifacts request no INTERNET and carry no `.so` |
| Model Runtime flags | protected Runtime dumpsys | production inference/provider/router/hardware remain blocked |
| `evidence_scope` | `ro.kernel.qemu` | emulator vs device application-layer evidence is explicit |

The script exits non-zero on any mismatch and prints key/value evidence only after every check passes. It does not expose a new Binder API. `target_hardware_validated=false` is invariant and application-layer acceptance does not close `DRV-GAP-001`.

## Android R6A1 Bounded Event Runtime

| Interface/type | Input | Output/constraint |
| --- | --- | --- |
| `TrustedPublication.fromRuntimePolicy` | trusted topic, schema ID, payload SHA-256 | no raw payload; unknown topic is typed rejection |
| `publish` | trusted publication | global monotonic `EventEnvelope`, bounded retention, subscriber enqueue |
| `TrustedSubscription.fromRuntimePolicy` | client ID, owner fingerprint, topic set, global cursor, queue capacity | 1..8 unique topics; owner is lowercase SHA-256 |
| `subscribe` | trusted request + process observer | CREATED/REPLAYED/CONFLICT/topic/cursor/quota outcome; replay keeps original observer |
| `dispatchOwned` | subscription ID, owner, bounded batch | overflow callback first, then events; observer failure or reentrant mutation retains head |
| `cancelOwned` | subscription ID + owner | CANCELLED/ALREADY_CANCELLED/not-owner; one close callback |
| `findOwned` / `snapshot` | trusted owner or diagnostics | no cross-owner leakage; bounded counts and no-production flags |

The cursor is global across the three trusted low-frequency topics. A retention gap is therefore a conservative global overflow range; consumers must resynchronize state after overflow. An observer callback cannot reenter publish, subscribe, dispatch or cancel on the same runtime; the attempt is treated as `OBSERVER_FAILED` before queue ownership advances. Event/subscription/cursor state is process-only, callback dispatch is an explicit test call, and no Room/Binder/DDS/network/hardware path is active.

## Android R6A2A Durable Event Schema

| Interface/type | Persisted input | Constraint |
| --- | --- | --- |
| `EventCursorEntity` identity | cursor ID, owner fingerprint, client subscription ID | unique owner + client; no cross-owner key |
| Subscription shape | canonical topics, requested cursor, queue capacity | metadata only; compared by R6A2B repository |
| Recovery state | acknowledged cursor, ACTIVE/RESYNC/CANCELLED, overflow range/count | no event payload or callback object |
| `MIGRATION_2_3` | v2 owner/topic/last sequence/update time | deterministic legacy client identity; all source values retained |
| DAO shape | find by cursor, find by owner/client, insert, update | repository-only foundation; no production Service call |

The schema remains eight tables at version 3. A v2 cursor becomes ACTIVE with requested and acknowledged sequence both equal to its prior `last_sequence`, queue capacity 1, zero overflow and `created_at_wall_ms` copied from the previous update time. This compatibility mapping does not claim that a historical callback registration existed.

## Android R6A2B Durable Event Repository

| Method | Input | Result/constraint |
| --- | --- | --- |
| `register` | owner, client ID, trusted topics, after cursor, queue, trusted latest | CREATED/REPLAYED/CONFLICT/future/global limit/owner limit/source regression |
| `findOwned` | cursor ID + owner | snapshot or null without cross-owner existence disclosure |
| `acknowledgeOwned` | cursor, owner, ACK, trusted latest | monotonic apply/replay; regression/future/source reset/RESYNC blocked |
| `markOverflowOwned` | cursor, owner, dropped range, trusted latest | conservative union; ACTIVE -> RESYNC_REQUIRED; exact range replay |
| `completeResyncOwned` | cursor, owner, snapshot sequence, trusted latest | requires coverage through overflow last; clears range atomically |
| `cancelOwned` | cursor + owner | APPLIED/REPLAYED/not found; bounded cancelled-row retention |

Applied state changes and their digest-only audit rows share one Room transaction. `knownLatestSequence` is trusted Runtime input, not request-body authority. A latest value below persisted ACK returns `SOURCE_REGRESSION`; this prevents accidental reuse after a process-local publisher resets but does not itself provide a durable sequence source.

## Android R6A3 Event Runtime Readiness

| Surface | Record | Constraint |
| --- | --- | --- |
| Runtime startup log | `event_runtime_readiness_snapshot_wired=true` plus activation/wiring/blockers | immutable metadata only |
| Runtime dumpsys | complete key/value readiness snapshot | protected framework diagnostic path; no repository query |
| Diagnostic Binder | `runtime/event-runtime-readiness`, summary `blocked`, sequence 6 | existing paged V1 contract; no AIDL change |

The snapshot reports implementation availability for R6A1/R6A2A/B, trusted topic count 3 and six ordered activation blockers. It never reports live subscription counts or opens the database; those would create a runtime dependency and a privacy surface before production Event ownership is approved.

## Android R6B1 Bounded Memory Lifecycle

| Interface/type | Input | Result/constraint |
| --- | --- | --- |
| `TrustedWrite.fromRuntimePolicy` | owner, client ID, scope, purpose, session, schema, SHA-256 digest, TTL, optional consent | metadata only; PROFILE has no session and requires eligible purpose/consent |
| `write` | trusted write | CREATED/REPLAYED/CONFLICT/policy/consent/quota outcome |
| `queryOwned` | owner plus optional scope/purpose and bounded limit | active redacted records; no digest or cross-owner disclosure |
| `findOwned` | memory ID + owner | lifecycle state/expiry/content-reference-presence only |
| `deleteOwned` | memory ID + owner | APPLIED/REPLAYED/not found; digest cleared and terminal retention bounded |
| `exportOwned` | memory ID + owner + Governance authorization | digest-only SESSION/PROFILE export; EPHEMERAL forbidden |
| `snapshot` | none | bounded counts plus persistence/production/raw-content false flags |

TTL uses an injected monotonic elapsed clock and therefore has no restart guarantee. `TrustedConsentEvidence.grantedByGovernance` and `TrustedExportAuthorization.grantedByGovernance` are internal contract factories, not Binder APIs or production decision authorities. Delete/expiry preserve only a domain-separated request fingerprint for bounded replay; once terminal retention evicts a record, its replay guarantee ends.

## Android R6B2 Memory Runtime Readiness

| Surface | Record | Constraint |
| --- | --- | --- |
| Runtime startup log | `memory_runtime_readiness_snapshot_wired=true` plus activation/prerequisites/blockers | immutable metadata only; no lifecycle instance |
| Runtime dumpsys | complete Memory readiness key/value snapshot | protected framework diagnostics; no Room/Keystore query |
| Diagnostic Binder | `runtime/memory-runtime-readiness`, summary `blocked`, sequence 7 | existing paged V1 contract; no AIDL change |

The snapshot reports R6B1 implementation availability and scope count 3, while schema/repository, encrypted storage, key lifecycle, consent/revocation, trusted retention clock and production wiring remain false. Its eight ordered blocker IDs are the admission gate for any later durable Memory design; the record is not a consent decision or storage-health probe.

## Android R6C1 Signed Built-In Skill Runtime

| Interface/type | Input | Result/constraint |
| --- | --- | --- |
| `listManifests` / `findManifest` | built-in Skill ID | immutable 3-item catalog; no filesystem/package scan |
| `SkillManifest` | compiled version/schema/route/capability/risk/safety/digest/signer constants | allowlist matched, artifact digest bound, real artifact crypto verification false |
| `TrustedInvocation.fromRuntimePolicy` | owner/client, Skill/version/schema, input SHA-256, granted capabilities, trusted safety state | metadata only; no raw input or request-provided identity |
| `admit` | trusted invocation | ADMITTED/REPLAYED/CONFLICT/unknown/version/schema/capability/safety/quota outcome; dispatch false |
| `findOwned` / `cancelOwned` | invocation ID + owner | no cross-owner disclosure; owner cancel idempotent while retained |
| `snapshot` | none | catalog/active/cancelled counts and no-loading/no-network/no-production/no-hardware flags |

Routes (`SOA_OPERATION`, `UIB_ACTION`, `AGENT_PLAN`) are declarative targets only. R6C1 neither calls the route nor performs cryptographic verification over artifact bytes. The signer digest is compile-time contract evidence awaiting a real build/publish verifier and production Skill dispatcher.

## Android R6C2 Fixed Governance Middleware Chain

| Interface/type | Input | Result/constraint |
| --- | --- | --- |
| `stageOrder` | none | immutable identity/schema/privacy/policy/QoS/trace/dispatch/output/audit order |
| `TrustedExchange.fromRuntimePolicy` | trusted identity, compiled Skill manifest, schema/digests, privacy, capabilities/safety, QoS, trace, route and output metadata | metadata only; no raw request/output or request-provided authority |
| `evaluate` | trusted exchange | ALLOWED or first-stage DENIED; later decision stages skipped; AUDIT always recorded once |
| `StageEvidence` | stage/status/reason | domain-separated SHA-256 evidence; immutable and ordered |
| `AuditRecord` | request fingerprint, decision and first rejection | bounded process-local sequence/digest; no raw data or durable claim |
| `recentAudits` | bounded count | immutable newest retained audit window |
| `snapshot` | none | counts plus production/dispatch/raw/audit-persistence/network/hardware false flags |

`DISPATCH_GATE` is a route admission check, not a dispatcher. `dispatchContractAllowed=true` can coexist with `serviceDispatchTriggered=false`, including a later output-guard rejection. AUDIT is deliberately a terminal finalizer after the short-circuited decision chain so denied requests remain observable without evaluating skipped business stages.

## Android R6C3 Skill And Governance Readiness

| Surface | Record | Constraint |
| --- | --- | --- |
| Runtime startup log | Skill/middleware implementation, fixed counts/order, wiring flags and blockers | immutable constants only; no runtime construction |
| Runtime dumpsys | complete Skill/Governance readiness key/value snapshot | protected framework diagnostic path; no catalog/storage query |
| Diagnostic Binder | `runtime/skill-governance-readiness`, summary `blocked`, sequence 8 | existing paged V1 contract; no AIDL change |

The snapshot distinguishes compile-time signer evidence from cryptographic artifact verification and fixed middleware code from production wiring. It reports lifecycle/revocation/rollback, sandbox, authority, route owner, audit persistence and dispatcher gaps without probing an APK, package signer, database, service or hardware device.

## Android R7A1 Runtime Acceptance Snapshot

| Surface | Record | Constraint |
| --- | --- | --- |
| Runtime startup log | core/R7/production/hardware dimensions plus ordered blockers | separate bounded log entry; no subsystem activation |
| Runtime dumpsys | complete aggregate acceptance key/value snapshot | protected framework diagnostic path; no Room query |
| Diagnostic Binder | `runtime/runtime-acceptance`, summary `core-ready-production-blocked`, sequence 9 | existing paged V1 contract; no AIDL change |

The rollup consumes immutable child snapshots, SDK maturity/stage constants and Room schema version only. `core_software_baseline_ready` is a software composition statement; Client2 migration and API 33 E2E remain explicit R7 blockers, while system owner, production subsystems and target hardware are independent blockers that application-layer tests cannot close.

## Android R7B Client2 SDK/Binder Migration

| Surface | Input/output | Constraint |
| --- | --- | --- |
| `Client2ScenarioBridge.submit` | Activity, allowlisted scenario ID, bounded UI text, callback | creates one typed `AgentTaskRequest`; no HTTP/model/hardware API |
| `CentralBrainClient` | explicit Runtime component, protocol version/hash, async task callback | signature permission plus Runtime capability policy remain authoritative |
| `ScenarioCallback` | status, terminal reply or terminal failure | main-executor UI update; one in-flight panel task |
| Client2 manifest | Runtime package query and `BIND_RUNTIME` permission | no INTERNET or cleartext opt-in |
| Runtime capability principal | package + complete current signer set | exactly protocol read and owned task submit/status/cancel |
| API 33 acceptance script | signed Runtime/Client2 APKs and visible cold-scenario button | verifies Binder identity/callback/UI; reports no dispatch/hardware |

The SDK AAR and the two bridge Java sources are compiled by D8 into an embedded `classes2.dex`; the existing Client2 Activity is hooked only after `setContentView`. The debug signer is intentionally shared with Runtime so Android can grant the signature permission, while the inner package/current-signer policy still applies least privilege. This is an APK-level test integration, not a claim that the original Client2 signer or RenderService trust contract is preserved.

## Android R7C Application Integration Acceptance

| Test surface | Trigger | Expected contract |
| --- | --- | --- |
| Runtime availability | disable/enable Runtime package from adb shell | visible bind failure, in-flight release, same-Activity retry success |
| Client2 single-flight | two immediate taps | one `AgentTaskRequest`, one trusted admission, one terminal callback |
| Runtime death | DUMP-protected debug broadcast after submit | one `ERROR_SERVICE_DIED`, no completion, next-click rebind and fail-closed reconciliation |
| Client2 restart | force-stop/relaunch Activity process | fresh panel hook, SDK bind, callback and UI reply |
| SDK lifecycle regression | existing androidTest instrumentation | service death/reconnect, callback death, terminal uniqueness, cancel/completion race |

`RuntimeFaultProbeReceiver` is a debug-only test interface, not a Runtime product API. Client2 cannot invoke it, release packaging excludes it, and the acceptance script restores Runtime package state through an EXIT trap. The evidence contract is stored in `central_brain_android_r7c_acceptance.json`; its positive claims stop at API 33 application integration.

## Android R7D Delivery And Empty Integration Slots

R7D adds deployment contracts and host tooling, not a new runtime service API. The application call path remains Client2/HMI -> public SDK -> typed Binder -> identity/capability/governance -> durable Runtime. Packaging never bypasses this path.

| Contract | Producer | Consumer | Invariant |
| --- | --- | --- | --- |
| `central-brain.android-delivery-profile.json` | repository owner | package builder/static gate | four ordered artifacts, one signer cohort, seven inactive slots |
| `DELIVERY-MANIFEST.json` | package builder | verifier/installer/integrator | source commit, artifact facts, support inventory, status and blocker snapshot |
| `SHA256SUMS` | package builder | verifier/integrator | exact path-safe coverage of every non-symlink bundle file except the checksum list itself; consistency only, not publisher authentication |
| `target-inputs.example.json` | project team | target integration owner | unresolved owner/deployment/vendor/evidence decisions; no guessed positive claim |
| installer dry-run output | bundle installer | release/acceptance owner | API 33, existing signer parity, no install/uninstall and blocked production/hardware state |

The seven empty slots are `target.system.owner.empty`, `effect.delivery.empty`, `model.vendor.npu.empty`, `event.runtime.empty`, `memory.runtime.empty`, `skill.governance.empty` and `target.hardware.evidence.empty`. Each slot carries one blocker ID, `activation_allowed=false` and replacement evidence requirements. The vendor NPU slot contains no native implementation; C/C++ is allowed only after a published vendor SDK requires a native adapter at the existing Model Provider boundary.

The installer resolves `adb`, `aapt`, `apksigner` and Java from explicit variables, `PATH` or standard Android/JDK roots. It re-reads each delivered APK package/signer, then verifies all already-installed signer digests before issuing the first fixed-order `adb install -r`; a mismatch returns `SIGNER_MIGRATION_REQUIRED` with no package mutation.

The target deployment and Client2 recovery commands are source-checkout acceptance bindings. Their inclusion in the bundle supplies the executable test entrypoints and traceability, not a claim that the archive contains the complete Gradle/Client2 build graph.

## Android B0 C/Java Ownership Boundary

| Boundary | Owner | Allowed data | Forbidden responsibility |
| --- | --- | --- | --- |
| App/SDK -> Runtime | Java/AIDL | typed task/action/status and callbacks | raw pointer, vendor SDK object, device handle |
| Runtime -> Native | Java/JNI | ABI version, fixed-width values, bounded byte arrays | caller identity, permission assertion, long Binder work |
| Native core | C ABI V1 | lifecycle, resource counters, provider descriptors/status | Binder/PackageManager, Room, network, device nodes, policy |
| Native -> Vendor slot | versioned C provider contract | published SDK-owned descriptor and opaque adapter state | guessed ioctl/HAL, implicit ownership, unbounded buffers |

Public C structs begin with `struct_size`/`abi_version`, use fixed-width integer types and caller-owned outputs. JNI registers through `JNI_OnLoad`/`RegisterNatives`, does not cache `JNIEnv*` or Java local references, and converts C status into immutable Java snapshots. Initial ABIs are `arm64-v8a` and `x86_64`; Vendor NPU/VHAL remain `UNAVAILABLE` with `hardware_accessed=false`.

## Android B1 Native Runtime API V1

| Surface | Operations | Ownership/error model |
| --- | --- | --- |
| C ABI | `create/get_health/acquire_slot/release_slot/destroy/status_name` | opaque handle; caller-owned outputs; typed status; active lease blocks destroy |
| JNI | `nativeCreate/nativeSnapshot/nativeAcquireSlot/nativeReleaseSlot/nativeDestroy` | static registered methods; no cached refs; Java owns handle serialization |
| Java | `NativeRuntime.snapshot/acquireSlot/releaseSlot/close` | synchronized, `AutoCloseable`, invalid/closed state fails visibly |
| Diagnostic value | `NativeRuntimeSnapshot` | immutable strict 10-field parse; ABI/range/boolean/provider/hardware drift fails closed |

The B1 interface is process-local and does not accept caller identity, Binder objects, file descriptors, model buffers or hardware handles. B2 may expose its readiness through existing Runtime/Diagnostic surfaces but may not transfer Governance ownership into C or enable provider dispatch.

## Android B2 Native Runtime Process Integration

| Caller/surface | Callee/data | Invariant |
| --- | --- | --- |
| Android process start | `CentralBrainRuntimeApplication -> NativeRuntimeProcess.start(4)` | one process-owned handle; failure becomes `UNAVAILABLE` |
| Runtime Service log/dumpsys | `NativeRuntimeProcessSnapshot` | read-only readiness; no slot lease or dispatch |
| Diagnostic Service | sequence 10 `runtime/native-runtime-readiness` | same snapshot and ordered ABI/lifecycle/provider fields |
| Debug native probe | isolated `NativeRuntime(2)` | load/capacity/busy-close/release/drain/close only |
| Host verifier | Runtime APK native payload and ELF metadata | exact arm64/x86_64 allowlist, signer and hardening checks |

The production call relationship is `Binder client -> Java Runtime/Governance -> durable Java workflow`; it does not continue into C in B2. Native Runtime is a process-health and future-provider boundary only. `software_provider_available`, `vendor_npu_provider_available`, `runtime_dispatch_enabled` and `hardware_accessed` remain false on every production and diagnostic surface.
