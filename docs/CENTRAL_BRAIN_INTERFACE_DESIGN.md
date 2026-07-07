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
| Hardware Interfaces | 硬件依赖空接口、reserved methods、Android/Linux 目标路径和触发条件 | HTTP/JSON active mock | AIDL + Linux IPC + gRPC；真实 HAL/vendor SDK/native adapter 待后续 |
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
| GET | `/events/topics` | legacy 兼容入口 | 是 |
| POST | `/events/publish` | legacy 兼容入口 | 是 |

`GET /uib/events/subscriptions`、`POST /uib/events/subscriptions/request`、`POST /uib/events/subscriptions/cancel`、`GET /uib/events/subscriptions/transport-readiness`、`GET /uib/events/subscriptions/decision-matrix` 与 `GET /uib/events/subscriptions/activation-checklist` 覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。这组接口返回订阅 lifecycle、cursor/replay、filter、QoS/backpressure、Runtime & Governance、transport candidates、callback/watch transport readiness、broker/cursor/backpressure owner decision matrix、activation evidence gates、`EV-SUB-001..006`/`EV-TR-001..006`/`EV-DM-001..007`/`EV-ACT-001..008` 门禁和 Android/Linux binding visibility；Android Binder `getEventSubscriptionsJson`/`requestEventSubscriptionJson`/`cancelEventSubscriptionJson`/`getEventSubscriptionTransportReadinessJson`/`getEventSubscriptionDecisionMatrixJson`/`getEventSubscriptionActivationChecklistJson`、Linux CLI `event-subscriptions`/`event-subscribe-request`/`event-subscribe-cancel`/`event-subscription-transport-readiness`/`event-subscription-decision-matrix`/`event-subscription-activation-checklist`、Linux IPC `uib.events.subscriptions.get`/`uib.events.subscriptions.request`/`uib.events.subscriptions.cancel`/`uib.events.subscriptions.transport.readiness`/`uib.events.subscriptions.decision.matrix`/`uib.events.subscriptions.activation.checklist` 与 Linux gRPC/RPC `GetEventSubscriptions`/`RequestEventSubscription`/`CancelEventSubscription`/`GetEventSubscriptionTransportReadiness`/`GetEventSubscriptionDecisionMatrix`/`GetEventSubscriptionActivationChecklist` 暴露同一视图。这些接口明确 `activation_allowed=false`、`production_activation_allowed=false`、`transport_selected=false`、`subscription_persisted=false`、`broker_active=false`、`callback_registered=false`、`watch_started=false`、`cursor_storage_active=false`、`dds_runtime_active=false`、`sse_websocket_active=false`、`high_rate_data_plane_active=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`；它们不是真实订阅 broker，不分配量产 owner，不选择 transport，不注册 callback/watch，不启动 SSE/WebSocket 或 DDS runtime，也不访问 Driver/HAL、车辆总线或虚拟化层。

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
| GET | `/models` | 模型列表 | 否 |
| POST | `/models/load` | 加载模型 | 否 |
| POST | `/models/unload` | 卸载模型 | 否 |
| POST | `/ai/infer` | 推理请求 | 是 |

当前 A6 增量新增 `docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md`，将 AI/NPU 域的量产接口边界明确为 Model Runtime Adapter -> Driver/HAL contract，而不是 App 直连 vendor SDK 或设备节点。该 contract 覆盖 HW-002、NV-F-011、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005；本轮不新增真实 NPU driver、HAL、DMA/IOMMU、Safety Runtime 或虚拟化代码。

### Hardware Interfaces

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| GET | `/hardware/interfaces` | 查询 NPU、Vehicle bus、Camera/Audio/Sensors、Ethernet/SOME-IP/DDS/TSN、Shared memory/Safety Runtime 的空接口、reserved methods、Android 主路径、Linux 同步路径和触发条件 | 是 |

`GET /hardware/interfaces` 覆盖 XSC-004、XSC-006、HW-002、KH-001、KH-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。该接口只返回 `empty-interface-registry`，并明确 `hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`；Android Binder `getHardwareInterfacesJson`、Linux CLI `hardware-interfaces`、Linux IPC `hardware.interfaces.get` 与 Linux gRPC/RPC `GetHardwareInterfaces` 暴露同一视图。本接口不打开 device node、不调用 HAL/vendor SDK、不分配共享内存、不访问车辆总线，也不开发虚拟化层。

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
| Android Binder/AIDL | `central-brain/bindings/android/aidl/com/centralbrain/binding/ICentralBrainGateway.aidl`、`central-brain/bindings/android/java/com/centralbrain/binding/CentralBrainGatewayBinderService.java`、`central-brain/bindings/android/java/com/centralbrain/binding/CentralBrainGatewayClient.java`，并已编入 Android Console debug APK | `/uib/context`、`/uib/state`、`/uib/events/*`、`/uib/events/subscriptions`、`/uib/events/subscriptions/request`、`/uib/events/subscriptions/cancel`、`/uib/events/subscriptions/transport-readiness`、`/uib/events/subscriptions/decision-matrix`、`/uib/events/subscriptions/activation-checklist`、`/uib/actions/request`、`/ai/sdk/capabilities`、`/agent/plan`、`/agent/execute`、`/skills`、`/memory/query`、`/soa/invoke`、`/policy/evaluate`、`/governance/backend-contract`、`/governance/migration-check`、`/governance/deployment-plan`、`/governance/runtime`、`/bindings/detail`、`/bindings/readiness`、`/delivery/readiness`、`/prototype/readiness`、`/native/adapters/detail`、`/hardware/interfaces`、`/vehicle/signals`、`/vehicle/signals/activation`、`/vehicle/signals/validation` | XSC-001、APP-004、XSC-002、XSC-003、XSC-004、XSC-005、XSC-006、FW-U-003、FW-U-004、FW-U-006、NV-F-003、NV-F-004、NV-F-005、NV-P-002、NV-P-005、NV-P-006、DEL-001 | Console Binder path + service stub sample；Console 已暴露 `planAgentTaskJson`、`executeAgentTaskJson`、`invokeSkillJson`、`queryMemoryJson`、`getEventSubscriptionsJson`、`requestEventSubscriptionJson`、`cancelEventSubscriptionJson`、`getEventSubscriptionTransportReadinessJson`、`getEventSubscriptionDecisionMatrixJson`、`getEventSubscriptionActivationChecklistJson`、`getVehicleSignalsJson`、`getVehicleSignalActivationJson`、`getVehicleSignalValidationJson`；execute/Skill/Memory/Event subscription lifecycle, transport readiness, owner decision matrix and activation checklist/Vehicle Signal activation/validation 为 contract mock；service 上游仍代理 REST prototype |
| Linux IPC | `central-brain/bindings/linux/ipc/central_brain_ipc_envelope.schema.json`、`central-brain/bindings/linux/ipc/central_brain_governance_client.py` | `uib.context.get`、`uib.state.get`、`uib.events.*`、`uib.events.subscriptions.request`、`uib.events.subscriptions.cancel`、`uib.events.subscriptions.transport.readiness`、`uib.events.subscriptions.decision.matrix`、`uib.events.subscriptions.activation.checklist`、`uib.actions.request`、`agent.plan`、`agent.execute`、`skills.*`、`memory.query`、`vehicle.signals.list`、`vehicle.signals.activation.get`、`vehicle.signals.validation.get`、`soa.service.invoke` 通过 shared governance client precheck 后转发、不通时 local fallback、`policy.evaluate`、`governance.precheck`、`governance.backend.contract.get`、`governance.migration.check`、`governance.deployment.plan.get`、`governance.runtime.get`/`audit.recent.get` 通过 shared governance client diagnostic 后回退 REST | XSC-001、XSC-002、XSC-003、XSC-005、XSC-006、FW-U-003、FW-U-004、FW-U-006、NV-F-003、NV-F-004、NV-F-005、NV-G-001、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、NV-P-006、DEL-002 | active sample with reusable governance socket client for precheck and read-only runtime/audit diagnostics |
| Linux gRPC/RPC | `central-brain/bindings/linux/proto/central_brain_gateway.proto`、`central-brain/bindings/linux/ipc/central_brain_governance_client.py`、`central-brain/bindings/linux/grpc/central_brain_grpc_server.py`、`central-brain/bindings/linux/grpc/central_brain_grpc_client.py` | `CentralBrainGateway.GetState`、`RequestEventSubscription`、`CancelEventSubscription`、`GetEventSubscriptionTransportReadiness`、`GetEventSubscriptionDecisionMatrix`、`GetEventSubscriptionActivationChecklist`、`RequestAction`、`InvokeService`、`EvaluatePolicy`、`PrecheckGovernance`、`GetGovernanceBackendContract`、`GetGovernanceMigrationCheck`、`GetGovernanceDeploymentPlan`、`GetRuntimeGovernance`、`GetRecentAudit`、`ListBindings`、`GetVehicleSignals`、`GetVehicleSignalActivation`、`GetVehicleSignalValidation`；`InvokeService` 先走同一个 shared governance client -> daemon precheck，不可用时 local fallback；runtime/audit diagnostics 先走 shared governance socket，不可用时 REST fallback | XSC-001、XSC-002、XSC-003、XSC-005、XSC-006、FW-U-003、FW-U-004、FW-U-006、NV-F-003、NV-F-004、NV-F-005、NV-G-001、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-003、NV-P-006、DEL-002 | JSON TCP contract sample；真实 gRPC runtime 待目标环境提供 `grpcio`/C++ gRPC |

验证命令：

```bash
bash tools/check_central_brain_binding_artifacts.sh
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py binding-detail
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py binding-readiness
```

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

当前 FW-U-003 增量已实现 `/uib/events/topics`、`/uib/events/publish`、`/uib/events/recent` active mock、`/uib/events/subscriptions` subscription contract、`/uib/events/subscriptions/request` 和 `/uib/events/subscriptions/cancel` contract-only lifecycle command、`/uib/events/subscriptions/transport-readiness` callback/watch transport readiness contract、`/uib/events/subscriptions/decision-matrix` broker/cursor/backpressure owner decision matrix contract，以及 `/uib/events/subscriptions/activation-checklist` broker activation prerequisite evidence checklist contract，并在 Android Binder、Linux IPC、Linux gRPC/RPC contract sample 中建立映射。该实现只验证 Uni Info Bus 事件语义、Req ID、订阅 lifecycle/cursor/backpressure/governance、request/cancel 命令、callback/watch readiness、owner decision matrix、activation evidence gates 和跨平台 binding，不实现 DDS、高频推送 broker、callback/watch、SSE/WebSocket、真实订阅数据面或 Driver/HAL 数据面；这些仍按 NV-P-006 计划态推进。

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
