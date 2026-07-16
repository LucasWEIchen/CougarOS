# Central Brain Android 模块用例与调用关系

日期：2026-07-16

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
    Client2(["触发场景和查看进度"])
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
    Diagnostics["Diagnostics/Readiness"]
  end

  subgraph Native["Native and target boundary"]
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
  Demo --> Sdk
  Sdk --> Binder --> Identity --> Task --> Governance --> Store
  Task --> Model
  Task --> Effect
  Governance --> Effect
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
| 驾驶员 | 选择“我冷了”“我累了”等场景 | Client2 -> SDK -> Binder | 当前只能证明任务/UI；真实车控未接入 |
| 座舱工程师 | 集成 SDK、显示 session/plan/effect | Java SDK/AIDL | 不直接调用模型或车辆 API |
| 平台工程师 | 配置 signer/capability/部署和 diagnostics | Runtime APK、policy XML、ADB | 不修改无源码系统组件 |
| Vendor 工程师 | 实现车辆/NPU adapter | Java/C contract、公开 SDK | ABI/权限/Safety/evidence 未齐不得激活 |
| 测试人员 | 安装、故障恢复、目标证据 | B4 bundle + ADB scripts | 只提交脱敏摘要，不上传设备原始身份 |

## 关键时序

```mermaid
sequenceDiagram
  participant H as Client2 HMI
  participant S as CentralBrainClient
  participant R as Runtime Service
  participant G as Governance
  participant D as Durable Store
  participant A as Target Adapter

  H->>S: submit typed task
  S->>R: Binder request + callback
  R->>G: resolve identity/capability/policy
  G-->>R: allow, deny or approval-required
  R->>D: persist task/checkpoint/effect intent
  alt adapter activated
    R->>A: dispatch bounded effect
    A-->>R: apply/readback status
    R->>D: verify/reconcile/audit
  else adapter unavailable
    R->>D: persist blocked/failed state
  end
  R-->>S: update/result/failure callback
  S-->>H: render authoritative state
```

当前 `hardware_accessed=false`、`driver_development_triggered=false`、
`virtualization_development_triggered=false`、`production_ready=false`。
