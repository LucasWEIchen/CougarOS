# 车载中央大脑接口设计

版本：0.1
日期：2026-07-04

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
| System | 健康、版本、能力 | HTTP/JSON | AIDL + REST debug |
| Registry | 服务注册发现 | HTTP/JSON | AIDL/gRPC/SOME-IP-SD |
| Context | 车辆/用户/环境上下文 | HTTP/JSON | AIDL + DDS event |
| Event | Uni Info Bus 事件 topic、发布、recent log | HTTP/JSON active mock | AIDL + Linux IPC + gRPC；高频 topic 预留 DDS |
| Agent | 任务规划和执行 | HTTP/JSON | AIDL/gRPC |
| Skill | 技能声明、调用、生命周期 | HTTP/JSON | AIDL + sandbox IPC |
| Memory | 用户偏好和长期记忆 | HTTP/JSON | AIDL + local encrypted store |
| Policy | 权限、安全状态、隐私路由 | HTTP/JSON | AIDL/native policy engine |
| Vehicle | VSS/VHAL/ECU 信号 | HTTP/JSON | VHAL/AIDL/SOME-IP |
| AI/NPU | 模型、推理、队列、后端 | HTTP/JSON | AIDL/native daemon/vendor SDK |
| Observability | Trace、Metric、Audit | HTTP/JSON | AIDL + file/socket exporter |
| Native Adapters | AIOS Kernel、Service Adapter、Vehicle Signal、Model Runtime Adapter | HTTP/JSON registry mock | Binder/native service + Unix socket/gRPC daemon + HAL/vendor SDK bridge |

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
| GET | `/vehicle/signals` | 信号 Schema | 否 |
| GET | `/vehicle/signals/{path}` | 单个信号读取 | 否 |
| POST | `/vehicle/actions` | 受控车控动作 | 否 |

### Agent

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| POST | `/agent/plan` | 用户意图转任务图 | 否 |
| POST | `/agent/execute` | 执行任务图 | 否 |
| GET | `/agent/tasks/{task_id}` | 查询任务状态 | 否 |
| POST | `/agent/tasks/{task_id}/cancel` | 取消任务 | 否 |

### Skill

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| GET | `/skills` | 技能列表 | 否 |
| GET | `/skills/{skill_id}` | 技能详情 | 否 |
| POST | `/skills/{skill_id}/invoke` | 调用技能 | 否 |
| POST | `/skills/{skill_id}/enable` | 启用技能 | 否 |
| POST | `/skills/{skill_id}/disable` | 停用技能 | 否 |

### Memory

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| POST | `/memory/query` | 查询相关记忆 | 否 |
| POST | `/memory/items` | 写入记忆 | 否 |
| DELETE | `/memory/items/{memory_id}` | 删除记忆 | 否 |
| POST | `/privacy/evaluate` | 判断数据是否可外发 | 否 |

### Policy

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| POST | `/policy/evaluate` | 权限与安全状态评估 | 是 |
| GET | `/policy/permissions` | 权限矩阵 | 否 |
| GET | `/audit/recent` | 最近治理审计记录 | 是 |

### Uni Info Bus Event

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| GET | `/uib/events/topics` | 事件 topic contract、订阅过滤字段、delivery 和 binding candidates | 是 |
| POST | `/uib/events/publish` | 通过 Uni Info Bus Event envelope 发布 mock 事件并写入 bounded recent log | 是 |
| GET | `/uib/events/recent` | 查询最近 Event 记录，作为订阅语义验证替身 | 是 |
| GET | `/events/topics` | legacy 兼容入口 | 是 |
| POST | `/events/publish` | legacy 兼容入口 | 是 |

### AI/NPU

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| GET | `/npu/status` | NPU/runtime 状态 | 是 |
| GET | `/models` | 模型列表 | 否 |
| POST | `/models/load` | 加载模型 | 否 |
| POST | `/models/unload` | 卸载模型 | 否 |
| POST | `/ai/infer` | 推理请求 | 是 |

### Observability

| Method | Path | 用途 | 已实现 |
| --- | --- | --- | --- |
| GET | `/trace/recent` | 最近调用 Trace | 否 |
| GET | `/metrics` | 指标快照 | 否 |
| GET | `/audit/recent` | 最近审计记录 | 是 |

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
| Android Binder/AIDL | `central-brain/bindings/android/aidl/com/centralbrain/binding/ICentralBrainGateway.aidl`、`central-brain/bindings/android/java/com/centralbrain/binding/CentralBrainGatewayBinderService.java`、`central-brain/bindings/android/java/com/centralbrain/binding/CentralBrainGatewayClient.java` | `/uib/context`、`/uib/state`、`/soa/invoke`、`/policy/evaluate`、`/governance/runtime`、`/bindings/detail`、`/native/adapters/detail` | XSC-002、XSC-003、XSC-004、XSC-005、XSC-006、NV-P-002、DEL-001 | service stub sample |
| Linux IPC | `central-brain/bindings/linux/ipc/central_brain_ipc_envelope.schema.json` | `uib.context.get`、`uib.state.get`、`soa.service.invoke`、`policy.evaluate` | XSC-002、XSC-003、XSC-005、XSC-006、NV-P-002、DEL-002 | contract skeleton |
| Linux gRPC/RPC | `central-brain/bindings/linux/proto/central_brain_gateway.proto` | `CentralBrainGateway.GetState`、`InvokeService`、`EvaluatePolicy` | XSC-002、XSC-003、XSC-005、XSC-006、NV-P-003、DEL-002 | contract skeleton |

验证命令：

```bash
bash tools/check_central_brain_binding_artifacts.sh
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py binding-detail
```

## Native Adapter Contract Mock

A5 增量新增 `central-brain/backend/native_adapters.py`，将图中的 Native 层能力先收敛成可查询注册表。当前状态不是 Driver/HAL 或真实 daemon 实现，只用于固定分层边界、Req ID 和 Android/Linux 交付路径。

| Adapter | 语义入口 | Req IDs | 状态 |
| --- | --- | --- | --- |
| AIOS Kernel | Tool、Service、Permission、Action | XSC-004、NV-F-001 | contract mock |
| SOA Service Adapter | `/soa/invoke`、`/soa/services` | XSC-003、NV-F-003、NV-F-008、FW-S-004、FW-S-005 | active prototype |
| Vehicle Signal Adapter | `/uib/context`、`/uib/state`、`vehicle-state` service | NV-F-004、NV-F-005、KH-006 | mock VSS snapshot |
| Model Runtime Adapter | `npu-inference` service、`/npu/status` | NV-F-011、KH-003、KH-006、HW-002 | mock NPU runtime |
| Security/Policy Adapter | `/policy/evaluate`、`/soa/invoke` | NV-F-009、NV-G-005、FW-U-007、FW-S-005 | active prototype |

验证命令：

```bash
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py native-adapters-detail
bash tools/smoke_central_brain_semantic_gateway.sh
```

## Event Topic 设计

当前 FW-U-003 增量已实现 `/uib/events/topics`、`/uib/events/publish`、`/uib/events/recent` active mock，并在 Android Binder、Linux IPC、gRPC contract skeleton 中建立映射。该实现只验证 Uni Info Bus 事件语义、Req ID 和跨平台 binding，不实现 DDS、高频推送 broker、真实订阅生命周期或 Driver/HAL 数据面；这些仍按 NV-P-006 计划态推进。

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
2. 为 Agent/Skill/Memory 增加 mock endpoint，并让 Policy/Audit 继续共用 Runtime & Governance。
3. Android Console 增加服务目录、车辆信号、推理、Trace 四个视图。
4. 为所有 mock API 增加 `trace_id`。
5. 增加 smoke test：验证 `/health`、`/services`、`/vehicle/state`、`/policy/evaluate`、`/ai/infer`。
