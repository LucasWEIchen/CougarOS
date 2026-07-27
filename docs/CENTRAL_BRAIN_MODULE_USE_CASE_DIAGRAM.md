# Central Brain Android 模块用例与调用关系

版本：1.1
日期：2026-07-27
状态：`REPOSITORY_SOFTWARE_ARCHITECTURE_BASELINE_READY / PRODUCTION_ACTIVATION_EXTERNAL_BLOCKED`

本图只描述黑盒 Android 13 实机工程。Req ID：`APP-004`、`XSC-001..006`、
`NV-F-001/003/004/005/011/012`、`NV-G-003..007`、`NV-P-002`、`KH-003/006`、
`DEL-001/003/004/005`。

```mermaid
flowchart LR
  Driver["驾驶员"]
  CabinEngineer["座舱应用工程师"]
  PlatformEngineer["Android 平台工程师"]
  VendorEngineer["车辆/NPU 供应商工程师"]
  Tester["目标硬件测试人员"]

  subgraph App["Application"]
    Client1(["Client1 副屏 d2 / Render index0"])
    Client2(["Client2 主屏 d0 / Render index1"])
    Tuanjie["TuanjieView / shared RenderService"]
    Hvac["Unity dynamic HVAC"]
    Demo(["执行维护验收"])
    Sdk["Java SDK"]
  end

  subgraph Runtime["Android AIOS Runtime"]
    Binder["Typed Binder"]
    Identity["Identity/Capability"]
    Task["Session/Task/Graph"]
    Governance["Policy/Safety/Approval"]
    Store["Room/Checkpoint/Outbox/Audit"]
    Model["Scheduler/ModelProvider"]
    Effect["Effect/Verify/Reconcile"]
    SimEffect["Simulated Effect projection"]
    Diagnostics["Diagnostics/Readiness"]
  end

  subgraph Compute["External model compute"]
    OpenClaw["Target OpenClaw transitional"]
    NpuProduction["Production Ollama/Vendor NPU empty"]
  end

  subgraph Native["Native and vehicle boundary"]
    CAbi["JNI/C ABI"]
    VehicleAdapter["Vehicle adapter empty"]
    NpuAdapter["Vendor NPU provider empty"]
    External["Vendor service/Driver/HAL/Hardware"]
  end

  subgraph Delivery["Delivery loop"]
    Bundle["Signed bundle/hash/ABI"]
    Adb["Target-side ADB acceptance"]
    Issue["Sanitized Issue/retest"]
  end

  Driver --> Client2
  CabinEngineer --> Client2
  CabinEngineer --> Sdk
  PlatformEngineer --> Binder
  VendorEngineer --> VehicleAdapter
  VendorEngineer --> NpuAdapter
  Tester --> Adb

  Client2 --> Sdk
  Client2 --> Tuanjie --> Hvac
  Client1 --> Tuanjie
  Demo --> Sdk
  Sdk --> Binder --> Identity --> Task --> Governance --> Store
  Task --> Model --> OpenClaw
  Model -. "production qualification required" .-> NpuProduction
  Task --> Effect
  Governance --> Effect
  Effect --> SimEffect --> Client2
  Store --> Diagnostics
  Model --> NpuAdapter
  Effect --> VehicleAdapter
  Runtime --> CAbi
  CAbi -. "published SDK only" .-> External
  VehicleAdapter -. "owner/evidence required" .-> External
  NpuAdapter -. "owner/evidence required" .-> External
  Bundle --> Adb --> Issue
  Issue -. "replacement release" .-> Bundle
```

## 用例约束

| Actor | 用例 | 权威入口 | 当前边界 |
| --- | --- | --- | --- |
| 驾驶员 | 选择“我冷了”“我累了”“处理一下”等场景 | Client2 -> SDK -> Binder | 文字/受控图片进入真实模型路径；执行仍为 UI 仿真 |
| 座舱工程师 | 集成 SDK、显示 session/plan/effect | Java SDK/AIDL | 不直接调用模型或车辆 API |
| 平台工程师 | 配置 signer/capability/部署和 diagnostics | Runtime APK、policy XML、ADB | 不修改无源码系统组件 |
| Vendor 工程师 | 实现车辆/NPU adapter | Java/C contract、公开 SDK | ABI/权限/Safety/evidence 未齐不得激活 |
| 测试人员 | 安装、双屏渲染恢复、故障恢复、目标证据 | bundle + ADB scripts | Client1 必须先于 Client2 建立共享 RenderService；只提交脱敏摘要 |

## 关键时序

```mermaid
sequenceDiagram
  participant H as Client2 HMI
  participant S as CentralBrainClient
  participant R as Runtime Service
  participant M as Model Provider
  participant G as Governance
  participant D as Durable Store
  participant A as Target Adapter
  participant U as Client2/Unity HMI

  H->>S: submit text + optional controlled image
  S->>R: Binder request + callback
  R->>G: resolve identity/capability/policy
  G-->>R: allow, deny or approval-required
  R->>D: persist task/checkpoint/effect intent
  R->>M: bounded model request
  M-->>R: validated structured output
  alt adapter activated
    R->>A: dispatch bounded effect
    A-->>R: apply/readback status
    R->>D: verify/reconcile/audit
  else adapter unavailable
    R->>U: project simulated effect/readback
    R->>D: persist debug projection or blocked state
  end
  R-->>S: update/result/failure callback
  S-->>H: render authoritative state
```

当前 `hardware_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`、`architecture_document_set_ready=true`、
`production_ready=false`、`target_hardware_validated=false`。
