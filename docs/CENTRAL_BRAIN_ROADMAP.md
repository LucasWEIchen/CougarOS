# 车载中央大脑路线图与进展

更新时间：2026-07-07

## 长期任务拆解

| 阶段 | 目标 | 主要交付物 | 状态 |
| --- | --- | --- | --- |
| M0 | 建立方向、文档、原型骨架 | 产品设计、架构设计、资料纪要、Android 原型、mock NPU 后端 | 已完成 |
| M0.1 | PM 级需求拆解和接口设计 | 需求拆解、接口设计、KaKaClaw 参考产品概念映射 | 已完成 |
| A0 | 架构图需求基线化 | 需求矩阵、偏差表、疑点表、按图执行计划 | 已完成 |
| A1 | Uni Info Bus 语义接口 mock | Context/State/Event/Action/Service/Tool/Permission contract 与 client | Event + Action active mock；Android/Linux 主路径初版 |
| A2 | SOA 服务入口 mock | Business/Foundation/Atomic/Contract/Safety State | SOA invoke + service contract visibility 初版 |
| A3 | Runtime & Governance mock | Registry、Discovery、Schema、QoS、Policy、Lifecycle、Audit | active prototype + JSONL audit persistence sample + fixed-window QoS + `/governance/precheck` + `/governance/backend-contract` + `/governance/migration-check` + `/governance/deployment-plan` + Linux shared governance daemon runtime/audit diagnostics used by IPC/gRPC |
| A4 | Protocol Binding 分层 | REST 下沉为 binding，IPC/gRPC/MQTT/SOME-IP/DDS stub | Linux IPC active sample with shared governance precheck/runtime/audit direct diagnostics + backend contract/migration/deployment/binding readiness visibility + fallback；Linux gRPC/RPC JSON contract sample with same shared diagnostics；Android Binder service stub sample；Android Console Binder path；Android system service integration note；Event semantic mapping |
| A5 | Native adapters mock | AIOS Kernel、Service Adapter、Vehicle Signal、Model Runtime Adapter | adapter registry 初版 |
| A6 | Kernel/HAL/NPU 设计落地 | Driver/HAL/NPU runtime design、PCIe 接入路径 | NPU runtime interface + driver gap backlog + hardware empty-interface registry contract |
| A6.1 | 驱动接口支持矩阵 | Android/Linux 驱动能力、缺口、最小新增开发量 | 初版完成 + `/native/driver-gaps` + `/hardware/interfaces` |
| A7 | Hypervisor/Safety 接口约束 | ASIL/QM domain map、跨 VM 通信假设；不开发虚拟化 | 接口约束初版 |
| A8 | 应用层扩展 | 座舱、Agent、Cluster/TBOX、ADAS、诊断视图 | AI SDK/Agent plan + execute/Skill/Memory contract mock + Android Console Binder debug path |
| A9 | Android/Linux 双平台交付 | Android APK/SDK sample、Linux CLI/daemon sample、平台差异说明 | Android system service integration note + Linux systemd 与平台差异初版 + Linux systemd hardening check + Linux package profile check + `/delivery/readiness` + `/prototype/readiness` |

## M0 任务清单

- [x] 读取并归档用户提供的软件架构图。
- [x] 调研 AAOS/VHAL/AIDL/VSS/SOME-IP/DDS/NPU 接入资料。
- [x] 核对现有 Android SDK、AVD、Git、OpenCLAW 环境。
- [x] 创建长期任务分支。
- [x] 编写产品设计文档初版。
- [x] 编写软件架构设计文档初版。
- [x] 编写资料纪要。
- [x] 创建 Android Console 原型。
- [x] 创建 mock NPU 后端。
- [x] 构建 Android APK。
- [x] 启动 mock 后端。
- [x] 安装 APK 到本地模拟器。
- [x] 验证 App 访问 `/health` 返回 `Health HTTP 200`。
- [x] 验证 App 触发 `/ai/infer` 返回 `Inference HTTP 200`。
- [x] 形成 M0 Git 提交。

## 当前工程策略

- 不把现有 APK 逆向产物作为本任务第一阶段的修改对象。
- 所有新系统代码放在 `central-brain/`。
- 构建和运行脚本放在 `tools/`。
- 文档放在 `docs/CENTRAL_BRAIN_*`。
- 后续每完成一个可运行增量，都创建 Git 提交。
- 架构图是最高优先级需求基线；所有产品参考、接口扩展和 mock 实现都必须映射回图中模块。
- 新增或保留任何软件偏差，必须同步更新 `docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md`。
- 发现图中边界不清或工程风险，必须同步更新 `docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md`。

## 最近进展

### 2026-07-07

- 推进 NV-F-004/NV-F-005 Vehicle/Body Signal catalog contract：
  - 新增 `central-brain/backend/vehicle_signals.py` 与 `GET /vehicle/signals`，以只读 VSS-style catalog 暴露 BCM/HVAC/Seat/Door/Light/Powertrain 等信号路径、访问级别、governance tag、Adapter 边界和 Driver/HAL 缺口链接。
  - Android Binder/AIDL 新增 `getVehicleSignalsJson`，Android Console 新增 `Vehicle Signals` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `vehicle-signals`/`vehicle.signals.list`/`GetVehicleSignals` 可见路径。
  - 本轮只完成信号目录和 Android/Linux 同步可见性，不加载 DBC/ARXML，不连接 VHAL、SocketCAN、vendor gateway 或真实车辆总线，不访问硬件，不开发 Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、XSC-004、XSC-006、NV-F-004、NV-F-005、FW-U-001、FW-U-002、FW-U-003、FW-U-004、NV-P-002、NV-P-003、DEL-001、DEL-002、DEL-005。
- 推进 Python 原型成熟度总览 contract：
  - 新增 `central-brain/backend/prototype_readiness.py` 与 `GET /prototype/readiness`，集中暴露 AI SDK、Uni Info Bus、SOA、Runtime & Governance、Protocol Binding、Native adapters/Driver-HAL backlog、hardware empty interfaces 的当前成熟度、Android 主路径、Linux 同步路径、开放偏差、开放问题和下一步候选增量。
  - Android Binder/AIDL 新增 `getPrototypeReadinessJson`，Android Console 新增 `Prototype` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `prototype-readiness`/`prototype.readiness.get`/`GetPrototypeReadiness` 可见路径。
  - 本轮只补项目/产品/架构状态总览，不 dispatch SOA service，不访问真实硬件，不开发 Driver/HAL、vendor SDK、Safety Runtime、共享内存、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-001、XSC-002、XSC-003、XSC-004、XSC-005、XSC-006、DEL-001、DEL-002、DEL-003、DEL-004、DEL-005、HW-002、KH-003、KH-006、KH-007。
- 推进 A6/A6.1 Python 原型硬件空接口注册表：
  - 新增 `central-brain/backend/hardware_interfaces.py` 与 `GET /hardware/interfaces`，覆盖外置 PCIe NPU、Vehicle bus、Camera/Audio/Sensors、Ethernet/SOME-IP/DDS/TSN、Shared memory/Safety Runtime 五类硬件依赖空接口。
  - Android Binder/AIDL 新增 `getHardwareInterfacesJson`，Android Console 新增 `Hardware IF` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interfaces`/`hardware.interfaces.get`/`GetHardwareInterfaces` 可见路径。
  - 本轮只完成 Python 原型中的接口预留和 Android/Linux 同步可见性，不访问真实硬件，不开发 Driver/HAL、vendor SDK、Safety Runtime、共享内存、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005、NV-F-011、NV-P-002、NV-P-003。
- 推进 A1 FW-U-008 Uni Info Bus extension registry contract：
  - 新增 `GET /uib/extensions`，以只读 contract 暴露扩展语义对象、schema 状态、治理规则、binding 可见性和 no-dispatch 边界，避免“其他/扩展”能力绕开 Uni Info Bus、SOA、Runtime & Governance 或 Protocol Binding。
  - Android Binder/AIDL 新增 `getUibExtensionsJson`；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `extensions`/`uib.extensions.get`/`GetUibExtensions` 可见路径。
  - 本轮只补 FW-U-008 扩展机制 contract 可查询能力，不实现动态插件 runtime、真实 extension loader、SOA dispatch、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-008、XSC-005、XSC-006、NV-P-002、NV-P-003、DEL-001、DEL-002。
- 推进 A9 Android/Linux delivery readiness contract：
  - 新增 `GET /delivery/readiness`，集中暴露 Android debug Console/Binder、Android system service note、Linux CLI、Linux IPC、Linux gRPC/RPC、Linux systemd/package profile、Driver/HAL gap backlog 和虚拟化约束的当前状态、阻塞项、验证命令和非目标边界。
  - Android Binder/AIDL 新增 `getDeliveryReadinessJson`；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `delivery-readiness`/`delivery.readiness.get`/`GetDeliveryReadiness` 可见路径。
  - 本轮只补 Android/Linux 交付 readiness 可查询能力，不实现 Android system service、真实 gRPC runtime、量产包管理、生产共享治理后端、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：DEL-001、DEL-002、DEL-003、DEL-004、DEL-005、XSC-001、XSC-002、XSC-003、XSC-004、XSC-005、XSC-006、NV-P-002、NV-P-003、KH-003、KH-006、KH-007。
- 推进 A4 Protocol Binding readiness contract：
  - 新增 `GET /bindings/readiness`，集中暴露 Android Binder、Linux IPC、Linux gRPC/RPC、REST、MQTT、SOME/IP、DDS 的当前状态、阻塞项、验证命令、下一步决策和非目标边界。
  - Android Binder/AIDL 新增 `getBindingReadinessJson`；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `binding-readiness`/`bindings.readiness.get`/`GetBindingReadiness` 可见路径。
  - 本轮只补 Protocol Binding readiness 可查询能力，不实现量产 shared governance backend、true gRPC runtime、MQTT/SOME/IP/DDS、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-006、NV-P-001、NV-P-002、NV-P-003、NV-P-004、NV-P-005、NV-P-006、DEL-001、DEL-002、DEL-003、DEL-004。
- 推进 A2 SOA service contract 可见性：
  - 新增 `GET /soa/contracts`，从 `runtime_governance.SERVICE_CATALOG` 暴露服务 contract、版本、domain、Policy/Safety State、QoS、Lifecycle、schema source 和 no-dispatch 边界。
  - Android Binder/AIDL 新增 `getServiceContractsJson`；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `service-contracts`/`soa.contracts.get`/`GetServiceContracts` 可见路径。
  - 本轮只补 SOA contract 可查询能力，不 dispatch SOA service，不消费 QoS，不访问 Driver/HAL、车辆总线、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-003、FW-S-001、FW-S-002、FW-S-003、FW-S-004、FW-S-005、NV-G-001、NV-G-002、NV-G-003、NV-P-002、NV-P-003、DEL-001、DEL-002。

### 2026-07-06

- 推进 shared Runtime & Governance backend deployment plan contract：
  - 新增 `GET /governance/deployment-plan`，用 Runtime & Governance payload 固定未来共享治理后端的 Android system/privileged service、Linux standalone daemon、true gRPC/RPC 三类目标部署形态、身份输入、开放决策和非目标边界。
  - Android Binder/AIDL 新增 `getGovernanceDeploymentPlanJson`；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `governance-deployment-plan`/`governance.deployment.plan.get`/`GetGovernanceDeploymentPlan` 可见路径。
  - 本轮只新增部署计划 contract 和验证，不实现生产多进程治理后端、真实 gRPC runtime、Android framework/SELinux patch、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-005、XSC-006、NV-G-001、NV-G-002、NV-G-003、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、NV-P-003、DEL-001、DEL-002、DEL-003、DEL-004。
- 推进 Linux IPC/gRPC shared governance runtime/audit diagnostic path：
  - `central_brain_governance_client.py` 新增 `get_runtime_via_socket` 与 `get_audit_via_socket`，让 Linux IPC 与 Linux gRPC/RPC sample 在 `governance.runtime.get`/`GetRuntimeGovernance` 和 `audit.recent.get`/`GetRecentAudit` 上复用同一 shared governance socket envelope。
  - `central_brain_ipc_daemon.py` 与 `central_brain_grpc_server.py` 对 runtime/audit 只读诊断优先走 shared governance daemon，不可用时回退 REST prototype gateway；SOA `InvokeService` precheck 仍保留 shared precheck + local Runtime & Governance fallback。
  - Linux IPC/gRPC smoke 现在验证 runtime/audit 诊断经 `forwarding=shared-governance-socket` 返回，且不 dispatch SOA service、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 本轮未新增生产多进程治理后端、真实 gRPC runtime、Android framework/SELinux patch、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-005、XSC-006、NV-G-001、NV-G-002、NV-G-004、NV-G-007、NV-P-002、NV-P-003、DEL-002。
- 推进 shared Runtime & Governance backend migration readiness check：
  - 新增 `GET /governance/migration-check`，用机器可读 payload 固定生产共享治理后端替换前必须保持的三类不变量：SOA dispatch 必须经 `governance.precheck`、各 transport 不复制 Policy/QoS 逻辑、runtime/audit 诊断只读且不触发 Driver/HAL/车辆总线/虚拟化。
  - Android Binder/AIDL 新增 `getGovernanceMigrationCheckJson`；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `governance-migration-check`/`governance.migration.check`/`GetGovernanceMigrationCheck` 可见路径。
  - 本轮只新增迁移 readiness contract 和验证，不实现量产多进程治理后端、真实 gRPC runtime、Android framework/SELinux patch、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-005、XSC-006、NV-G-001、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、NV-P-003、DEL-001、DEL-002、DEL-003、DEL-004。
- 推进 shared Runtime & Governance backend target contract：
  - 新增 `GET /governance/backend-contract`，用 Runtime & Governance payload 固定未来共享治理后端必须支持的 `governance.precheck`、`governance.runtime.get`、`audit.recent.get` 三类操作、Android Binder/Linux IPC/Linux gRPC-RPC 接入形态、替换规则和非目标边界。
  - Android Binder/AIDL 新增 `getGovernanceBackendContractJson`；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `governance-backend-contract`/`governance.backend.contract.get`/`GetGovernanceBackendContract` 可见路径。
  - Protocol Binding registry、API contract、smoke/static checks、接口设计、平台差异、交付目标、偏差和驱动支持边界同步说明该能力是共享治理后端目标契约，不是量产多进程治理后端、真实 gRPC runtime、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-005、XSC-006、NV-G-001、NV-G-002、NV-G-003、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、NV-P-003、DEL-001、DEL-002。
- 推进 Linux Protocol Binding shared governance client 收敛：
  - 新增 `central_brain_governance_client.py`，把 Linux IPC 与 Linux gRPC/RPC sample 调用 shared governance socket 的 `governance.precheck` envelope 收敛到同一 client helper。
  - `central_brain_ipc_daemon.py` 与 `central_brain_grpc_server.py` 继续保留各自 local Runtime & Governance fallback，但 shared daemon 调用路径不再重复实现 socket 读写与 envelope 组装。
  - Protocol Binding registry、API contract、Linux README、接口设计、偏差和驱动支持边界同步说明该 helper 只是 Linux 本地共享治理样例的 client boundary，不是量产治理后端、真实 gRPC runtime、Driver/HAL 或虚拟化层。
  - 覆盖 Req ID：XSC-005、XSC-006、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、NV-P-003、DEL-002。
- 推进 Linux shared Runtime & Governance daemon 诊断可见性：
  - `central_brain_governance_daemon.py` 在现有 `governance.precheck` 基础上新增 direct socket operation：`governance.runtime.get` 与 `audit.recent.get`，复用 `RuntimeGovernance.governance_payload()` 和 `audit_payload()`。
  - Linux IPC smoke 现在直接连接 shared governance socket，验证 runtime registry/QoS 状态和 precheck 审计事件可通过该 daemon 查询，且不 dispatch SOA service、Driver/HAL 或虚拟化层。
  - Protocol Binding registry、API contract、Linux README、delivery docs、需求矩阵、偏差和驱动支持边界同步说明该能力仍是 Linux 单机共享治理样例，不是量产多进程治理后端。
  - 覆盖 Req ID：XSC-005、XSC-006、NV-G-001、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、DEL-002。
- 推进 Android Console Governance/Driver gap Binder 可见性：
  - `MainActivity` 在现有 `Refresh`、`Plan Agent Task`、`Execute Task`、`Invoke Skill`、`Query Memory` 基础上新增 `Precheck` 与 `Driver Gaps` 调试入口，分别调用 `precheckGovernanceJson` 和 `getDriverHalGapsJson`。
  - Android Console 现在可直接验证 XSC-005 的 Runtime & Governance 只检查不调用路径，以及 KH-003/KH-006/DEL-005 的 Driver/HAL gap backlog 只读可见性。
  - 静态绑定检查新增对 Console governance precheck 与 driver gaps 按钮路径的断言，防止 Android 主路径只停留在 contract 文档。
  - 本轮未新增 Android system service、真实 Driver/HAL、Safety Runtime、车辆总线、NPU vendor SDK、真实共享治理后端或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-005、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、KH-003、KH-006、DEL-001、DEL-005。
- 推进 A9 Linux package/profile 静态契约：
  - 新增 `central-brain/deploy/linux/central-brain.package-profile.json`，用机器可读清单固定 Linux 样例的安装根、服务身份、环境文件、runtime/log 目录、四个 systemd unit、Req ID、hardening 要求和非目标边界。
  - 新增 `tools/check_central_brain_linux_package_profile.sh`，验证 package profile、env example 与 backend/governance/IPC/gRPC-RPC unit 的 service identity、`WorkingDirectory`、`EnvironmentFile`、`ExecStart`、`ReadWritePaths`、runtime 目录和 hardening 约束一致。
  - `tools/check_central_brain_delivery_docs.sh` 纳入 package profile 检查项；交付目标、平台差异、偏差和驱动支持文档同步说明该 profile 是 Linux cockpit-domain 样例，不是量产包管理。
  - 本轮未新增真实 gRPC runtime、package manager 集成、LSM/SELinux/AppArmor policy、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：DEL-002、DEL-003、DEL-004、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-G-007。

### 2026-07-05

- 推进 A9 Linux systemd hardening sample：
  - 四个 Linux systemd unit 新增 `ProtectSystem=strict`、`ProtectHome=true`、`PrivateDevices=true`、`RestrictSUIDSGID=true`、`LockPersonality=true`、`PYTHONDONTWRITEBYTECODE=1` 和最小 `ReadWritePaths`。
  - 新增 `tools/check_central_brain_linux_systemd_hardening.sh`，验证 gateway、governance、IPC、gRPC/RPC 样例 unit 的服务身份、日志目录、运行目录和 hardening 约束，并在可用时运行 `systemd-analyze verify`。
  - 本轮只收紧 Linux 交付样例部署约束；未新增量产包管理、真实 IPC/gRPC runtime、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：DEL-002、DEL-003、DEL-004、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-G-007。
- 推进 A6 Driver/HAL gap backlog contract：
  - `native_adapters.py` 新增 Driver/HAL gap backlog，覆盖 NPU、Vehicle bus、Camera/Audio/Sensors、Ethernet/SOME-IP/DDS/TSN、Shared memory/Safety Runtime 五类缺口。
  - 后端新增 `GET /native/driver-gaps`；Android Binder/AIDL 新增 `getDriverHalGapsJson`；Linux CLI 新增 `driver-gaps`，均只返回触发条件、Android 主路径、Linux 同步路径和最小新增开发量。
  - `/native/adapters/detail` 同步包含 `driver_hal_gap_backlog`，方便 Native adapter 交付边界与 Driver/HAL 缺口一起检查。
  - 本轮未开发 NPU/GPU/Camera/Audio/ETH/Vehicle bus driver、HAL、Safety Runtime、共享内存、真实 vendor SDK bridge 或虚拟化层。
  - 覆盖 Req ID：KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005、HW-002、NV-F-002、NV-F-004、NV-F-005、NV-F-006、NV-F-011、NV-P-001、NV-P-006、HV-001、HV-002、HV-003。
- 推进 Android Console execute/Skill/Memory Binder 调试路径：
  - `MainActivity` 在现有 `Refresh` 与 `Plan Agent Task` 基础上新增 `Execute Task`、`Invoke Skill`、`Query Memory`，分别调用 `executeAgentTaskJson`、`invokeSkillJson`、`queryMemoryJson`。
  - Android Console 现在可直接验证 XSC-001/FW-U-006 的 Agent execute、Skill/Tool 与 Memory contract mock；所有调用仍经 Binder service 上游 REST prototype gateway，不绕过 AI SDK/Uni Info Bus/SOA/Runtime & Governance 边界。
  - 静态绑定检查新增对 Console execute/Skill/Memory 按钮与 Binder client 方法的断言，防止 Android 主路径只停留在 plan。
  - 本轮未开发真实 Agent runtime、Skill sandbox、Memory store、Model Runtime Adapter、Driver/HAL、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-001、APP-004、NV-F-001、FW-U-006、FW-U-007、XSC-006、NV-P-002、NV-P-005、DEL-001。
- 推进 Linux gRPC/RPC contract active sample：
  - 新增 `central_brain_grpc_server.py` 与 `central_brain_grpc_client.py`，用当前环境可运行的 JSON TCP wrapper 验证 `central_brain_gateway.proto` 中 GatewayRequest/GatewayResponse 与 RPC 名称映射。
  - `InvokeService` 在转发到 REST prototype gateway 前优先调用 shared Linux governance daemon precheck，不可用时回退本地 Runtime & Governance precheck。
  - 新增 `central-brain-linux-grpc.service`、`CENTRAL_BRAIN_GRPC_PORT`、`CENTRAL_BRAIN_GRPC_AUDIT_LOG` 和 `tools/smoke_central_brain_linux_grpc.sh`。
  - 当前环境无 `grpcio`，因此本轮不是量产 gRPC server；仍未开发 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-001、XSC-002、XSC-003、XSC-005、XSC-006、APP-004、FW-U-003、FW-U-004、FW-U-006、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-003、DEL-002、DEL-004。
- 推进 Linux Runtime & Governance 共享 daemon 样例：
  - 新增 `central_brain_governance_daemon.py`，通过 Unix socket 提供 `governance.precheck` 决策，Linux IPC `soa.service.invoke` 可优先调用该共享 socket。
  - Linux IPC daemon 在 `CENTRAL_BRAIN_GOVERNANCE_SOCKET` 不可用时保留本地 Runtime & Governance precheck fallback，避免绕过 Policy/Lifecycle/QoS。
  - Linux env/systemd 样例新增 `central-brain-governance.service`、`CENTRAL_BRAIN_GOVERNANCE_SOCKET` 和 `CENTRAL_BRAIN_GOVERNANCE_AUDIT_LOG`。
  - 本轮仍未开发量产多进程治理后端、独立 native gateway、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-005、XSC-006、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、DEL-002、DEL-004。
- 推进 Runtime & Governance 显式 precheck 契约：
  - 后端新增 `POST /governance/precheck`，对服务执行 discovery、Policy/Safety State、Lifecycle 和 QoS 决策检查，但默认 `consume_qos=false`，不 dispatch 到 SOA service、Driver/HAL、车辆总线或虚拟化层。
  - Android Binder/AIDL 新增 `precheckGovernanceJson`；Linux CLI/IPC 新增 `governance-precheck` / `governance.precheck`；gRPC contract skeleton 新增 `PrecheckGovernance`。
  - Protocol Binding registry、API contract、smoke test、交付文档与检查脚本同步覆盖该 precheck 路径。
  - 本轮仍未开发共享量产治理 daemon、多进程 QoS 后端、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-005、XSC-006、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、NV-P-003、DEL-001、DEL-002。
- 推进 Linux IPC Runtime & Governance 前置检查样例：
  - `central_brain_ipc_daemon.py` 对 `soa.service.invoke` 新增本地 Runtime & Governance precheck，覆盖 service discovery、Policy/Safety State、Lifecycle、QoS 和 IPC audit。
  - 允许的 SOA 调用继续转发到 REST semantic gateway；拒绝的 SOA 调用直接在 IPC 边界返回 `forwarding=blocked-before-rest-gateway` 和 `ipc_governance_precheck`。
  - Linux env/systemd 样例新增 `CENTRAL_BRAIN_IPC_AUDIT_LOG=/var/log/central-brain/ipc-audit.jsonl`，用于区分 IPC binding 审计与后端 gateway 审计。
  - 本轮仍未开发独立量产 gateway、多进程治理后端、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-005、XSC-006、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、DEL-002、DEL-004。
- 推进 XSC-001/FW-U-006 Agent execute、Skill 与 Memory contract mock：
  - 后端新增 `POST /agent/execute`、`GET /skills`、`POST /skills/{skill_id}/invoke`、`POST /memory/query`，执行 Permission/Safety State 检查并写入 Runtime & Governance audit。
  - execute 只返回 `execution_mode=policy-checked-contract-mock` 和 SOA/Tool/Action dispatch 边界，不运行真实 Skill sandbox、Memory store、Model Runtime Adapter、Driver/HAL、车身总线或虚拟化层。
  - Android Binder/AIDL、Linux CLI/IPC 与 gRPC contract skeleton 同步新增 execute/Skill/Memory 映射。
  - 覆盖 Req ID：XSC-001、APP-004、NV-F-001、FW-U-006、FW-U-007、XSC-005、XSC-006、NV-G-005、NV-P-002、NV-P-003、DEL-001、DEL-002。
- 推进 FW-U-004 Uni Info Bus Action active mock：
  - 后端新增架构命名动作入口：`POST /uib/actions/request`；legacy `/actions/request` 仅保留兼容。
  - Action 请求执行 Permission/Safety State 检查并返回 `execution_mode=policy-checked-mock`，明确不 dispatch 到 Driver/HAL、Vehicle bus 或虚拟化层。
  - Linux CLI 与 Linux IPC active sample 新增 `action-request` / `uib.actions.request` 验证路径。
  - Android Binder/AIDL 与 gRPC contract skeleton 新增 `requestActionJson` / `RequestAction` 映射。
  - 本轮未开发真实车控执行、Vehicle Signal/ECU Adapter、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、XSC-005、XSC-006、FW-U-004、FW-U-007、NV-G-005、NV-P-002、NV-P-005、NV-P-003、DEL-001、DEL-002。

### 2026-07-04

- 确认图片可通过 WSL 路径读取，并归档到 `docs/assets/central_brain_architecture_source.png`。
- 确认当前仓库已有 Android 逆向和模拟器工具链。
- 确认 OpenCLAW 绝对路径可用：`/home/normad400/.npm-global/bin/openclaw`。
- 创建分支：`codex/central-brain-ecosystem`。
- 新增中央大脑文档和第一阶段代码骨架。
- 构建并签名 Android APK：`central-brain/android-console/out/central-brain-console.debug.apk`。
- 启动 WSL mock 后端并验证 `/health`、`/vehicle/state`、`/ai/infer`。
- 在本地 AVD `cabin_client_api36_x86_64` 安装并启动 `com.centralbrain.console`。
- 验证模拟器 App 通过 `10.0.2.2:8787` 连通 WSL 后端。
- 保存验证截图：
  - `logs/test/central-brain/console-launch.png`
  - `logs/test/central-brain/console-inference.png`
- 参考地平线 KaKaClaw 咖咖虾公开资料，补充产品概念映射：
  - Agentic Car OS
  - 任务即服务
  - Soul / Skill / Memory
  - 舱驾协同
  - Skill 沙箱
  - Privacy Router
- 新增 PM 级需求拆解：`docs/CENTRAL_BRAIN_REQUIREMENTS_BREAKDOWN.md`。
- 新增接口设计文档：`docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md`。
- 创建 20 小时自动进展推进任务：每 20 分钟一次，共 60 次，自动化 ID `20`。
- 用户明确要求将架构图作为真实需求基线，而非示意图。
- 新增架构图需求矩阵：`docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md`。
- 新增软件偏差登记表：`docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md`。
- 新增架构疑点登记表：`docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md`。
- 新增按图执行计划：`docs/CENTRAL_BRAIN_ARCHITECTURE_EXECUTION_PLAN.md`。
- 更新自动化 ID `20`：每 20 分钟推进时必须先检查架构需求矩阵、偏差登记表和疑点登记表。
- 用户明确虚拟化层不开发；本项目只记录虚拟化接口约束和部署假设。
- 用户明确驱动层仅在当前 Android/Linux 环境能力不足时新增开发量，但驱动接口支持必须文档化。
- 用户明确黄色小太阳组件会在多个 SoC 出现；已按跨 SoC 可移植平台组件建立 `XSC-001..006`。
- 用户明确以 Android 开发为主，交付时同时提供 Linux 版本；交付对象为 Android/Linux 座舱域软件工程师。
- 新增交付目标文档：`docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md`。
- 新增驱动接口支持矩阵：`docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md`。
- 立即手动执行一次自动化任务：
  - 完成 A1 后端 mock 初版：`/context`、`/state`、`/events/topics`、`/events/publish`、`/actions/request`、`/service/invoke`、`/tools`、`/permission/check`。
  - 新增 Linux/WSL smoke test：`tools/test_central_brain_bus.sh`。
  - 注意：Android Console 仍未切到 Uni Info Bus client，`DEV-001` 保持临时偏差。
- 推进语义网关主路径：
  - Android Console 改为调用 `GET /uib/state` 和 `POST /soa/invoke`。
  - 后端新增架构命名入口：`/uib/context`、`/uib/state`、`/soa/services`、`/soa/invoke`、`/governance/runtime`、`/bindings`。
  - 新增 Linux CLI：`central-brain/linux-cli/central_brain_cli.py`。
  - 新增 smoke test：`tools/smoke_central_brain_semantic_gateway.sh`。
  - 覆盖 Req ID：XSC-002、XSC-003、XSC-005、XSC-006、FW-U-001、FW-U-002、FW-U-005、FW-U-007、FW-S-004、FW-S-005、NV-G-001..007、NV-P-001..006。
- 推进 Runtime & Governance active prototype：
  - 新增 `central-brain/backend/runtime_governance.py`，集中承载服务注册、发现、Policy、Lifecycle、QoS 元数据和内存审计。
  - `/soa/invoke` 现在先通过 registry/discovery/policy/lifecycle precheck，再写入 audit。
  - 新增架构命名入口：`POST /policy/evaluate`、`GET /audit/recent`。
  - Linux CLI 与 smoke test 覆盖 policy deny、SOA invoke audit 和 governance 状态。
  - 覆盖 Req ID：XSC-005、FW-S-001、FW-S-002、FW-S-003、FW-S-004、FW-S-005、NV-G-001..007。
- 推进 Protocol Binding contract skeleton：
  - 新增 `central-brain/backend/protocol_bindings.py`，把 REST、Android Binder/AIDL、Linux IPC、gRPC、MQTT、SOME/IP、DDS 纳入绑定注册表。
  - 新增 Android AIDL artifact：`central-brain/bindings/android/aidl/com/centralbrain/binding/ICentralBrainGateway.aidl`。
  - 新增 Linux artifact：`central-brain/bindings/linux/proto/central_brain_gateway.proto` 与 `central-brain/bindings/linux/ipc/central_brain_ipc_envelope.schema.json`。
  - 新增 `/bindings/detail` 与 Linux CLI `binding-detail`，可查看绑定 artifact、语义入口映射和分层约束。
  - 新增 `tools/check_central_brain_binding_artifacts.sh`，验证 AIDL/proto/schema artifact 存在、可解析并包含 Req ID。
  - 覆盖 Req ID：XSC-002、XSC-003、XSC-005、XSC-006、NV-P-001..006、DEL-001、DEL-002。
- 推进 A5 Native adapters mock：
  - 新增 `central-brain/backend/native_adapters.py`，建立 AIOS Kernel、SOA Service Adapter、Vehicle Signal Adapter、Model Runtime Adapter、Security/Policy Adapter 注册表。
  - 后端新增 `/native/adapters` 与 `/native/adapters/detail`，返回 Android 主开发路径、Linux 同步交付路径、Driver/HAL 依赖与虚拟化约束。
  - Linux CLI 与语义网关 smoke test 新增 `native-adapters-detail` 校验。
  - 覆盖 Req ID：XSC-004、NV-F-001、NV-F-003、NV-F-004、NV-F-005、NV-F-008、NV-F-009、NV-F-011、DEL-001、DEL-002、DEL-005。
- 推进 Linux IPC binding active sample：
  - 新增 Unix domain socket daemon：`central-brain/bindings/linux/ipc/central_brain_ipc_daemon.py`。
  - 新增 IPC client：`central-brain/bindings/linux/ipc/central_brain_ipc_client.py`。
  - `linux-ipc` 在 `/bindings/detail` 中从 `contract-skeleton` 推进为 `active-sample`，映射 `uib.context.get`、`uib.state.get`、`soa.services.list`、`soa.service.invoke`、`policy.evaluate`、`governance.runtime.get`、`audit.recent.get`、`bindings.list`。
  - 新增 smoke test：`tools/smoke_central_brain_linux_ipc.sh`。
  - 覆盖 Req ID：XSC-002、XSC-003、XSC-005、XSC-006、FW-U-001、FW-U-002、FW-S-004、FW-S-005、NV-G-005、NV-G-007、NV-P-002、DEL-002。
- 推进 Android Binder service stub sample：
  - 扩展 AIDL：`getBindingDetailJson`、`getNativeAdaptersDetailJson`，让 Android IPC contract 覆盖 Protocol Binding 与 Native adapter 可见性。
  - 新增 Android service/client sample：`CentralBrainGatewayBinderService.java`、`CentralBrainGatewayClient.java`，将 Binder 方法映射到 `/uib/*`、`/soa/*`、`/policy/evaluate`、`/governance/runtime`、`/bindings/detail`、`/native/adapters/detail`。
  - `/bindings/detail` 中 `android-binder-aidl` 从 `contract-skeleton` 推进为 `service-stub-sample`；REST 仍只是上游 prototype binding。
  - 覆盖 Req ID：XSC-002、XSC-003、XSC-004、XSC-005、XSC-006、NV-P-002、DEL-001。
- 推进 A9 Android/Linux 交付样例：
  - 新增平台差异说明：`docs/CENTRAL_BRAIN_PLATFORM_DELTA.md`，覆盖 Android/Linux IPC、权限、部署、日志、Driver/HAL、虚拟化差异。
  - 新增 Linux 部署样例：`central-brain/deploy/linux/central-brain.env.example`、`central-brain/deploy/linux/systemd/central-brain-backend.service`、`central-brain/deploy/linux/systemd/central-brain-linux-ipc.service`。
  - 新增静态验证脚本：`tools/check_central_brain_delivery_docs.sh`。
  - 覆盖 Req ID：DEL-001、DEL-002、DEL-003、DEL-004、XSC-002、XSC-003、XSC-005、XSC-006、NV-P-002。
- 推进 A7 Hypervisor/Safety 接口约束：
  - 新增 `docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md`，记录 HV-001..003 的非开发范围、ASIL/QM domain 假设、Safety State 映射、跨 VM envelope 和 fallback。
  - 新增静态验证脚本：`tools/check_central_brain_virtualization_docs.sh`。
  - 未开发虚拟化层、Safety Runtime、共享内存驱动或 Driver/HAL。
  - 覆盖 Req ID：HV-001、HV-002、HV-003、FW-S-005、NV-G-005、NV-F-009、KH-007、DEL-004。
- 推进 FW-U-003 Uni Info Bus Event active mock：
  - 后端新增架构命名事件入口：`GET /uib/events/topics`、`POST /uib/events/publish`、`GET /uib/events/recent`；legacy `/events/*` 仅保留兼容。
  - Linux CLI 与 Linux IPC active sample 新增 `events`、`event-publish`、`event-recent` 验证路径。
  - Android Binder/AIDL 与 gRPC contract skeleton 新增事件 topic、publish、recent 方法。
  - 本轮未实现 DDS、高频推送、真实订阅 broker、Driver/HAL 或虚拟化层。
  - 覆盖 Req ID：XSC-002、XSC-006、FW-U-003、NV-P-002、NV-P-006、DEL-001、DEL-002。
- 推进 A6 NPU Runtime Adapter 接口约束：
  - 新增 `docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md`，固定外置 PCIe NPU 从 Uni Info Bus/SOA 到 Model Runtime Adapter、Driver/HAL 的分层边界。
  - 明确 Android 主开发路径、Linux 同步交付路径、最低 API 抽象、统一 envelope、状态机、错误码和 Driver/HAL 集成检查点。
  - 新增静态验证脚本：`tools/check_central_brain_npu_interface.sh`。
  - 本轮未开发 NPU driver、HAL、DMA/IOMMU、Safety Runtime、虚拟化层或 vendor SDK bridge。
  - 覆盖 Req ID：HW-002、NV-F-011、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 A3 Runtime & Governance audit persistence sample：
  - `RuntimeGovernance` 新增可选 `CENTRAL_BRAIN_AUDIT_LOG` JSONL 审计日志，服务重启后可恢复最近 50 条 SOA audit。
  - `/audit/recent` 和 `/governance/runtime` 返回 persistence state、path、last_error，便于 Android/Linux 座舱工程师确认集成状态。
  - Linux systemd/env 样例新增 `CENTRAL_BRAIN_AUDIT_LOG=/var/log/central-brain/audit.jsonl` 与 `LogsDirectory=central-brain`。
  - 新增验证脚本：`tools/smoke_central_brain_audit_persistence.sh`。
  - 本轮未开发 Driver/HAL、Safety Runtime、虚拟化层、真实审计后端或日志轮转。
  - 覆盖 Req ID：XSC-005、NV-G-007、DEL-002、DEL-004。
- 推进 A3 Runtime & Governance QoS active prototype：
  - `RuntimeGovernance` 新增 per-service fixed-window QoS 检查，`/soa/invoke` 在 Policy/Lifecycle 之后执行限流。
  - `npu-inference` 样例限制为每 1 秒 2 次，超出后返回 `qos_decision=deny` 并写入 `qos_rejected` audit。
  - 新增验证脚本：`tools/smoke_central_brain_qos.sh`。
  - 本轮未开发多进程限流、真实服务治理后端、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-005、NV-G-004、NV-G-007、FW-S-005、DEL-002。
- 推进 Android Console Binder 绑定路径：
  - Debug APK 构建脚本生成 `ICentralBrainGateway` AIDL Java，并把 Android Binder service/client sample 编入 Console APK。
  - `AndroidManifest.xml` 声明 `CentralBrainGatewayBinderService`，Console 启动后先绑定该 service，再通过 `CentralBrainGatewayClient` 调用 `getStateJson` 和 `invokeServiceJson`。
  - Android App 层不再直接发起 `/uib/state`、`/soa/invoke` HTTP 调用；REST 仍仅由 Binder service 作为上游 prototype binding 代理。
  - 本轮未开发 Android system service、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、XSC-003、XSC-006、NV-P-002、NV-P-005、DEL-001。
- 推进 Android system/privileged service 集成说明：
  - 新增 `docs/CENTRAL_BRAIN_ANDROID_SYSTEM_SERVICE_INTEGRATION.md`，明确 debug APK Binder sample 与目标 AAOS system/privileged service 的差异。
  - 文档化 manifest/signature permission、Binder identity 到 Runtime & Governance Policy、SELinux/deployment 假设和验证检查项。
  - 新增 `tools/check_central_brain_android_system_service_docs.sh`，并纳入交付文档校验。
  - 登记 ISSUE-013：目标 AAOS 镜像签名、priv-app 白名单、SELinux domain、service manager 注册方式和 native gateway 形态待确认。
  - 本轮未开发 Android framework patch、priv-app 签名配置、SELinux policy、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：DEL-001、DEL-003、DEL-004、XSC-002、XSC-003、XSC-005、XSC-006、NV-P-002、NV-P-005、FW-U-007、FW-S-005、NV-G-005。
- 推进 XSC-001 AI SDK/Agent 任务规划入口：
  - 新增 `central-brain/backend/ai_sdk.py`，提供 AI SDK facade capabilities 与 policy-aware task graph planner。
  - 后端新增 `GET /ai/sdk/capabilities` 与 `POST /agent/plan`，App 提交 intent/utterance 后只获得任务图，执行步骤仍必须经 Uni Info Bus、Tool、Action 或 SOA 服务入口。
  - Runtime & Governance registry 新增 `agent-task-planner`，Protocol Binding 增加 Android Binder/AIDL、Linux IPC 和 gRPC contract 映射。
  - Linux CLI/IPC active sample 新增 `ai-sdk`、`agent-plan` 验证路径，semantic gateway 和 Linux IPC smoke test 已覆盖。
  - 本轮未开发 AI SDK 真库、Agent execute、Skill sandbox、Memory store、真实 Model Runtime Adapter、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-001、APP-004、NV-F-001、FW-U-006、FW-U-007、XSC-002、XSC-003、FW-S-005、XSC-006、NV-P-002、NV-P-003、DEL-001、DEL-002。
- 推进 Android Console AI SDK/Agent 主任务路径：
  - `MainActivity` 第二个主按钮从 SOA inference 调试入口切换为 `CentralBrainGatewayClient.planAgentTaskJson`。
  - Console 现在通过 Binder 提交 utterance、caller、permission、vehicle/safety state 到 `/agent/plan`，只获得任务图；实际执行仍必须经 SOA/Tool/Action。
  - 静态绑定检查新增对 Console `Plan Agent Task` 与 `planAgentTaskJson` 的断言，防止 App 主路径回退到直按 SOA 推理。
  - 本轮未开发 AI SDK 真库、Agent execute、Skill sandbox、Memory store、真实 Model Runtime Adapter、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-001、APP-004、XSC-002、XSC-003、XSC-006、NV-P-002、NV-P-005、DEL-001。
