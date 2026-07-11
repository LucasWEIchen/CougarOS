# Central Brain 模块调用用例图

版本：0.1
日期：2026-07-11

## 图的范围

本图同时表达四类参与者、Android/Linux 接入用例、中央大脑核心模块调用关系，以及真实硬件空接口边界。它是当前 Python 原型的用例-模块组合视图，不替代架构图需求基线。

覆盖 Req ID：`APP-004`、`XSC-001`、`XSC-002`、`XSC-003`、`XSC-004`、`XSC-005`、`XSC-006`、`FW-U-001..008`、`FW-S-001..006`、`NV-F-001`、`NV-F-003..005`、`NV-F-011`、`NV-G-001..007`、`NV-P-002`、`NV-P-003`、`NV-P-005`、`KH-003`、`KH-006`、`KH-007`、`HW-002`、`DEL-001..005`。

## 用例-模块调用关系图

```mermaid
flowchart TB
  subgraph ACTORS["参与者"]
    direction LR
    Driver["驾驶员 / 乘员<br/>Actor"]:::actor
    AndroidEngineer["Android 座舱工程师<br/>Actor"]:::actor
    LinuxEngineer["Linux 座舱工程师<br/>Actor"]:::actor
    Integrator["系统集成 / 测试工程师<br/>Actor"]:::actor
  end

  subgraph CENTRAL_BRAIN["Central Brain 系统边界"]
    direction TB

    subgraph L1["L1 应用与验收用例"]
      direction LR
      Client2UC(["运行 12 个 Agent 场景<br/>Client2 Panel<br/>APP-004 / DEL-001"]):::app
      AndroidDebugUC(["调试状态、Agent、Skill、治理<br/>Android Console<br/>DEL-001"]):::app
      LinuxDebugUC(["CLI / IPC / RPC 集成验收<br/>Linux Samples<br/>DEL-002"]):::app
      ReadinessUC(["读取完成度、偏差和驱动缺口<br/>DEL-003..005"]):::app
    end

    subgraph BINDING["Protocol Binding 与入口 - XSC-006"]
      direction LR
      AndroidBinder(["Android Binder / AIDL sample<br/>NV-P-002"]):::binding
      LinuxBinding(["Linux CLI / UDS / gRPC JSON sample<br/>NV-P-002 / NV-P-003"]):::binding
      RestGateway(["HTTP Semantic Gateway<br/>NV-P-005"]):::binding
      BindingRegistry(["Binding / Delivery Readiness<br/>XSC-006"]):::binding
    end

    subgraph CORE["中央大脑核心用例"]
      direction LR
      ScenarioHarness(["场景编排与验收<br/>Agent Scenario Test Harness<br/>XSC-001"]):::core
      AISDK(["任务规划、执行、Skill、Memory、Tool<br/>AI SDK / Agent<br/>XSC-001"]):::core
      UIB(["Context / State / Event / Action / Permission<br/>Uni Info Bus<br/>XSC-002"]):::core
      SOA(["Business / Foundation / Atomic Service<br/>SOA Service Entry<br/>XSC-003"]):::core
      Governance(["Registry / Discovery / Policy / QoS / Lifecycle<br/>Runtime & Governance<br/>XSC-005"]):::core
    end

    subgraph NATIVE["Native 与适配模块"]
      direction LR
      NativeAdapter(["AIOS Kernel / Native Adapter Registry<br/>XSC-004 / NV-F-001"]):::adapter
      VehicleAdapter(["Vehicle Signal Adapter<br/>NV-F-003..005"]):::adapter
      ModelAdapter(["Model Runtime Adapter<br/>NV-F-011"]):::adapter
      AuditReadiness(["Audit / Observability / Completion<br/>NV-G-007 / DEL-003"]):::adapter
    end

    subgraph BOUNDARY["Kernel、HAL 与硬件边界"]
      direction LR
      MockNPU(["Python Mock NPU<br/>当前可运行"]):::simulation
      Ollama(["Ollama simulated NPU<br/>用户态模型仿真"]):::simulation
      EmptyInterfaces(["Hardware Empty-Interface Registry<br/>KH-003 / KH-006 / KH-007"]):::boundary
      TargetHardware(["目标 Driver/HAL、PCIe NPU、车辆总线<br/>HW-002 / 未激活"]):::inactive
      Virtualization(["Hypervisor / ASIL-QM / 跨 VM<br/>只记录约束，不开发"]):::inactive
    end
  end

  Driver --> Client2UC
  AndroidEngineer --> AndroidDebugUC
  LinuxEngineer --> LinuxDebugUC
  Integrator --> ReadinessUC

  Client2UC -->|"POST /agent/scenarios/run<br/>临时 HTTP，DEV-017"| RestGateway
  AndroidDebugUC -->|"Binder JSON methods"| AndroidBinder
  LinuxDebugUC -->|"REST / UDS / RPC"| LinuxBinding
  ReadinessUC --> AndroidBinder
  ReadinessUC --> LinuxBinding

  AndroidBinder -->|"debug sample 上游"| RestGateway
  LinuxBinding -->|"prototype gateway"| RestGateway
  LinuxBinding -->|"shared governance socket<br/>precheck / runtime / audit"| Governance
  RestGateway --> ScenarioHarness
  RestGateway --> AISDK
  RestGateway --> UIB
  RestGateway --> SOA
  RestGateway --> Governance
  RestGateway --> BindingRegistry
  RestGateway --> NativeAdapter
  RestGateway --> ModelAdapter

  ScenarioHarness -->|"plan / execute / Skill / Memory"| AISDK
  ScenarioHarness -->|"state / action / permission"| UIB
  ScenarioHarness -->|"service contract"| SOA
  ScenarioHarness -->|"policy / audit decision"| Governance
  ScenarioHarness -->|"audit / completion result"| AuditReadiness
  ScenarioHarness -->|"infer / status"| ModelAdapter

  AISDK -->|"读取语义上下文、请求受控动作"| UIB
  AISDK -->|"调用服务与工具"| SOA
  AISDK -->|"模型路由"| ModelAdapter
  UIB -->|"Permission / Policy / Audit"| Governance
  SOA -->|"Registry / Discovery / Policy / QoS"| Governance
  Governance -->|"记录决策证据"| AuditReadiness
  BindingRegistry -->|"聚合交付状态"| AuditReadiness
  UIB -->|"读取 mock 信号"| VehicleAdapter
  SOA -->|"服务适配 contract"| NativeAdapter

  ModelAdapter -->|"默认"| MockNPU
  ModelAdapter -->|"可选仿真"| Ollama
  ModelAdapter -. "真实 NPU adapter 预留" .-> EmptyInterfaces
  VehicleAdapter -. "VHAL / SocketCAN / vendor gateway 预留" .-> EmptyInterfaces
  NativeAdapter -. "Driver/HAL gap 与 ABI 门禁" .-> EmptyInterfaces
  EmptyInterfaces -. "owner、ABI、smoke、Safety gate 未满足" .-> TargetHardware
  TargetHardware -. "部署约束引用" .-> Virtualization

  classDef actor fill:#FFFFFF,stroke:#20262E,stroke-width:2px,color:#20262E;
  classDef app fill:#E8F4EA,stroke:#357A46,stroke-width:1.5px,color:#173B22;
  classDef binding fill:#E7F0FA,stroke:#356A9A,stroke-width:1.5px,color:#18354D;
  classDef core fill:#FFF5CC,stroke:#8A6A00,stroke-width:1.5px,color:#443600;
  classDef adapter fill:#FCEBDD,stroke:#9A572D,stroke-width:1.5px,color:#4D2A16;
  classDef simulation fill:#E9F5F3,stroke:#25766A,stroke-width:1.5px,color:#133D37;
  classDef boundary fill:#F2F2F2,stroke:#60656B,stroke-width:1.5px,stroke-dasharray:5 3,color:#292C30;
  classDef inactive fill:#F7E7E7,stroke:#9B3D3D,stroke-width:1.5px,stroke-dasharray:6 4,color:#542020;
```

## 阅读规则

- 实线表示当前原型中存在的调用或查询路径。
- 虚线表示 contract、空接口或目标平台预留路径，不表示已经访问真实硬件。
- Client2 到 HTTP Gateway 是 `DEV-017` 临时演示路径；正式 Android 路径仍应迁移到 Binder/SDK/system service。
- Android 与 Linux 客户端都不能直接调用 Model Runtime、Driver/HAL 或 PCIe NPU。
- SOA 和 Uni Info Bus 的受控操作必须进入 Runtime & Governance，完成 Registry、Policy、QoS、Lifecycle 和 Audit 检查。
- `Ollama simulated NPU` 只替代模型推理后端，不关闭 `DRV-GAP-001`，也不代表真实 NPU 性能或驱动已验收。
- 虚拟化层只保留部署和安全域约束，不产生实现调用。

## 主要用例结果

| 参与者用例 | 主要入口 | 核心模块链 | 当前边界 |
| --- | --- | --- | --- |
| 驾驶员运行 Agent 场景 | Client2 `/agent/scenarios/run` | Scenario Harness -> AI SDK/UIB/SOA/Governance/Model Adapter | HTTP demo；真实车控和服务 dispatch 为 false |
| Android 工程师调试 | Console -> Binder/AIDL | Binder -> Gateway -> core modules | 普通 debug APK sample，非 privileged service |
| Linux 工程师集成 | CLI/UDS/gRPC JSON | Linux Binding -> Gateway/shared governance | gRPC JSON sample，非量产 broker |
| 系统工程师验收 | readiness/completion/driver-gap API | Binding/Governance/Audit/Native registries | 只读证据，不关闭量产 gate |

所有路径固定保持 `hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `production_ready=false`。
