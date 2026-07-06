# 驱动层接口支持矩阵

版本：0.1
日期：2026-07-04

## 范围声明

驱动层不作为默认开发范围。只有当前 Android/Linux 环境无法满足中央大脑架构接口时，才新增最小开发量。即便不开发驱动，驱动接口支持必须被明确记录，供座舱域工程师评估集成风险。

虚拟化层不开发；如果某个驱动接口依赖 Hypervisor、跨 VM 共享内存或安全域通信，本项目只记录依赖假设和 fallback，不实现虚拟化功能。

2026-07-04/05 本轮语义网关、Runtime & Governance、Protocol Binding contract skeleton、Native adapters mock、Linux IPC active sample、Linux gRPC/RPC JSON contract sample、Android Binder service stub sample、Linux systemd 部署样例与 Android/Linux 平台差异说明增量只新增 Uni Info Bus/SOA/Governance/Binding/Native Adapter mock、Policy/Audit active prototype、Android AIDL 与 Binder service/client sample、Linux Unix socket IPC daemon/client sample、Linux gRPC/RPC contract sample、Linux CLI、systemd unit 和交付文档，不访问真实 NPU/GPU/Camera/Audio/ETH/Vehicle bus 驱动，因此未触发新增驱动开发条件。

A7 虚拟化与 Safety 接口约束增量只新增 `docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md` 和 `tools/check_central_brain_virtualization_docs.sh`，覆盖 HV-001..003、FW-S-005、NV-G-005、NV-F-009、KH-007、DEL-004；不新增 Hypervisor、Safety Runtime、跨 VM 共享内存、Driver/HAL 或 NPU/GPU/Camera/Audio/ETH/Vehicle bus 代码。

A1 Event active mock 增量只新增 `/uib/events/topics`、`/uib/events/publish`、`/uib/events/recent` 语义入口，以及 Android Binder、Linux IPC、gRPC contract skeleton 的 Event 映射；覆盖 XSC-002、XSC-006、FW-U-003、NV-P-002、NV-P-006、DEL-001、DEL-002。不新增 DDS、高频传感器 topic、共享内存、Driver/HAL、NPU/GPU/Camera/Audio/ETH/Vehicle bus 或虚拟化代码。

A5 Native adapters mock 的 `/native/adapters/detail` 只记录 AIOS Kernel、SOA Service Adapter、Vehicle Signal Adapter、Model Runtime Adapter、Security/Policy Adapter 的 Android/Linux 交付边界和 Driver/HAL 依赖，不新增驱动代码。

A6 NPU Runtime Adapter 接口约束增量新增 `docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md` 和 `tools/check_central_brain_npu_interface.sh`，覆盖 HW-002、NV-F-011、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005；只固定 Android/Linux runtime contract、状态机、错误码和 Driver/HAL 集成检查点，不新增 NPU driver、HAL、DMA/IOMMU、Safety Runtime、vendor SDK bridge 或虚拟化代码。

A3 Runtime & Governance 审计持久化增量新增 `CENTRAL_BRAIN_AUDIT_LOG` JSONL 最近审计恢复样例和 `tools/smoke_central_brain_audit_persistence.sh`，覆盖 XSC-005、NV-G-007、DEL-002；只写普通文件系统日志，不访问 NPU/GPU/Camera/Audio/ETH/Vehicle bus 驱动，不新增 Driver/HAL、Safety Runtime 或虚拟化代码。

A3 Runtime & Governance QoS 增量新增 `/soa/invoke` per-service fixed-window QoS 检查和 `tools/smoke_central_brain_qos.sh`，覆盖 XSC-005、NV-G-004、NV-G-007、FW-S-005、DEL-002；只在单进程治理原型内做限流决策与审计记录，不访问 NPU/GPU/Camera/Audio/ETH/Vehicle bus 驱动，不新增 Driver/HAL、Safety Runtime、多进程 QoS 后端或虚拟化代码。

Android Console Binder 绑定增量只修改 debug APK 的 manifest、构建脚本和 `MainActivity` 调用路径，使 App 层通过 `CentralBrainGatewayClient` 绑定 `CentralBrainGatewayBinderService` 后访问 Uni Info Bus/SOA；覆盖 XSC-002、XSC-003、XSC-006、NV-P-002、NV-P-005、DEL-001。Binder service 仍代理 REST prototype gateway，不访问 NPU/GPU/Camera/Audio/ETH/Vehicle bus 驱动，不新增 Android system service、Driver/HAL、Safety Runtime 或虚拟化代码。

Android system/privileged service 集成说明增量新增 `docs/CENTRAL_BRAIN_ANDROID_SYSTEM_SERVICE_INTEGRATION.md` 和 `tools/check_central_brain_android_system_service_docs.sh`，覆盖 DEL-001、DEL-003、DEL-004、XSC-002、XSC-003、XSC-005、XSC-006、NV-P-002、NV-P-005、FW-U-007、FW-S-005、NV-G-005。该增量只记录 AAOS system/privileged service 目标形态、manifest 权限、Binder identity 到 Policy 的映射、SELinux/deployment 假设和验证检查项，不新增 Android framework patch、priv-app 签名配置、SELinux policy、Driver/HAL、Safety Runtime、NPU/GPU/Camera/Audio/ETH/Vehicle bus 或虚拟化代码。

AI SDK/Agent 任务规划入口增量新增 `GET /ai/sdk/capabilities`、`POST /agent/plan`、`central-brain/backend/ai_sdk.py`、Android Binder/AIDL contract 映射、Linux CLI/IPC active sample 和 gRPC skeleton 映射，覆盖 XSC-001、APP-004、NV-F-001、FW-U-006、FW-U-007、XSC-002、XSC-003、FW-S-005、XSC-006、NV-P-002、NV-P-003、DEL-001、DEL-002。该增量只生成 policy-aware task graph，不执行真实 Skill、Memory、Model Runtime Adapter、NPU vendor SDK、Driver/HAL、Safety Runtime、NPU/GPU/Camera/Audio/ETH/Vehicle bus 或虚拟化代码。

Android Console AI SDK/Agent 主任务路径增量只修改 `MainActivity` 与静态检查，使第二个主按钮通过 Binder `planAgentTaskJson` 调用 `/agent/plan`，覆盖 XSC-001、APP-004、XSC-002、XSC-003、XSC-006、NV-P-002、NV-P-005、DEL-001。该增量只提交 utterance/caller/permission/safety context 并返回任务图，不执行真实 Skill、Memory、Model Runtime Adapter、NPU vendor SDK、Driver/HAL、Safety Runtime、NPU/GPU/Camera/Audio/ETH/Vehicle bus 或虚拟化代码。

FW-U-004 Action active mock 增量新增 `POST /uib/actions/request`、Linux CLI/IPC `action-request`/`uib.actions.request`、Android Binder/AIDL `requestActionJson` 和 gRPC `RequestAction` contract 映射，覆盖 XSC-002、XSC-005、XSC-006、FW-U-004、FW-U-007、NV-G-005、NV-P-002、NV-P-005、NV-P-003、DEL-001、DEL-002。该增量只做 Permission/Safety State 检查并返回 `execution_mode=policy-checked-mock`；不执行真实车控、Vehicle Signal/ECU Adapter、Driver/HAL、Safety Runtime、NPU/GPU/Camera/Audio/ETH/Vehicle bus 或虚拟化代码。

AI SDK/Agent execute、Skill 和 Memory contract mock 增量新增 `POST /agent/execute`、`GET /skills`、`POST /skills/{skill_id}/invoke`、`POST /memory/query`、Android Binder/AIDL `executeAgentTaskJson`/`invokeSkillJson`/`queryMemoryJson`、Linux CLI/IPC `agent-execute`/`skill-invoke`/`memory-query` 和 gRPC skeleton 映射，覆盖 XSC-001、APP-004、NV-F-001、FW-U-006、FW-U-007、XSC-006、NV-P-002、NV-P-003、DEL-001、DEL-002。该增量只返回 policy-checked contract mock、sandbox metadata、local-only memory mock 和 dispatch 边界；不执行真实 Agent runtime、Skill sandbox、Memory store、Model Runtime Adapter、NPU vendor SDK、Driver/HAL、Safety Runtime、NPU/GPU/Camera/Audio/ETH/Vehicle bus 或虚拟化代码。

Android Console execute/Skill/Memory 调试路径增量只修改 `MainActivity` 与静态检查，使 debug APK 通过 Binder `executeAgentTaskJson`、`invokeSkillJson` 和 `queryMemoryJson` 直接触发已有 contract mock；覆盖 XSC-001、APP-004、NV-F-001、FW-U-006、FW-U-007、XSC-006、NV-P-002、NV-P-005、DEL-001。该增量不新增真实 Agent runtime、Skill sandbox、Memory store、Model Runtime Adapter、NPU vendor SDK、Driver/HAL、Safety Runtime、NPU/GPU/Camera/Audio/ETH/Vehicle bus 或虚拟化代码。

Android Console Governance/Driver gap 可见性增量只修改 `MainActivity` 与静态检查，使 debug APK 通过 Binder `precheckGovernanceJson` 和 `getDriverHalGapsJson` 直接触发已有 Runtime & Governance precheck 与 Driver/HAL gap backlog contract；覆盖 XSC-004、XSC-005、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、KH-003、KH-006、DEL-001、DEL-005。该增量只执行只读/只检查 contract，不 dispatch SOA service，不访问 HAL、device node、vendor SDK、Safety Runtime、NPU/GPU/Camera/Audio/ETH/Vehicle bus 或虚拟化层，也不新增真实驱动开发量。

Linux IPC Runtime & Governance precheck 增量只在 `central_brain_ipc_daemon.py` 内复用 `runtime_governance.py`，对 `soa.service.invoke` 执行 service discovery、Policy/Safety State、Lifecycle、QoS 和 IPC audit，并新增 `CENTRAL_BRAIN_IPC_AUDIT_LOG` Linux 部署样例；覆盖 XSC-005、XSC-006、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、DEL-002、DEL-004。该增量只作用于 Unix socket binding 的语义转发边界，不访问 NPU/GPU/Camera/Audio/ETH/Vehicle bus 驱动，不新增 Driver/HAL、Safety Runtime、多进程治理后端或虚拟化代码。

Runtime & Governance 显式 precheck 契约增量新增 `POST /governance/precheck`、Android Binder/AIDL `precheckGovernanceJson`、Linux CLI/IPC `governance-precheck`/`governance.precheck` 和 gRPC `PrecheckGovernance` contract skeleton；覆盖 XSC-005、XSC-006、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、NV-P-003、DEL-001、DEL-002。该增量只返回 discovery、Policy/Safety State、Lifecycle 和 QoS 决策，默认不消费 QoS 窗口且不 dispatch 到 SOA service、Driver/HAL、NPU/GPU/Camera/Audio/ETH/Vehicle bus 或虚拟化层，不新增驱动开发量。

Linux Runtime & Governance 共享 daemon 样例新增 `central_brain_governance_daemon.py`、`central-brain-governance.service`、`CENTRAL_BRAIN_GOVERNANCE_SOCKET` 和 `CENTRAL_BRAIN_GOVERNANCE_AUDIT_LOG`，让 Linux IPC `soa.service.invoke` 可在转发到 REST prototype gateway 前优先复用共享 governance socket，并在不可用时回退本地 precheck；覆盖 XSC-005、XSC-006、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、DEL-002、DEL-004。该增量只使用 Unix domain socket 和普通 JSONL 文件，不访问 NPU/GPU/Camera/Audio/ETH/Vehicle bus 驱动，不新增 Driver/HAL、Safety Runtime、多机治理后端或虚拟化代码。

Linux shared Runtime & Governance daemon 诊断可见性增量在 `central_brain_governance_daemon.py` 中新增 direct socket operations `governance.runtime.get` 和 `audit.recent.get`，并在 `tools/smoke_central_brain_linux_ipc.sh` 中直接验证 runtime registry/QoS 状态和 shared daemon audit；覆盖 XSC-005、XSC-006、NV-G-001、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、DEL-002。该增量只复用现有 `RuntimeGovernance` 数据结构、Unix domain socket 和普通 JSONL 审计样例，不 dispatch SOA service，不访问 NPU/GPU/Camera/Audio/ETH/Vehicle bus 驱动，不新增 Driver/HAL、Safety Runtime、多机治理后端或虚拟化代码。

Linux gRPC/RPC contract sample 增量新增 `central_brain_grpc_server.py`、`central_brain_grpc_client.py`、`central-brain-linux-grpc.service`、`CENTRAL_BRAIN_GRPC_PORT`、`CENTRAL_BRAIN_GRPC_AUDIT_LOG` 和 `tools/smoke_central_brain_linux_grpc.sh`，覆盖 XSC-001、XSC-002、XSC-003、XSC-005、XSC-006、APP-004、FW-U-003、FW-U-004、FW-U-006、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-003、DEL-002、DEL-004。该增量只用标准库 TCP JSON wrapper 验证 gRPC proto contract、Req ID、`InvokeService` shared governance precheck 和 local fallback；当前环境无 `grpcio`，不新增真实 gRPC runtime、Driver/HAL、Safety Runtime、NPU/GPU/Camera/Audio/ETH/Vehicle bus 或虚拟化代码。

Driver/HAL gap backlog contract 增量新增 `GET /native/driver-gaps`、Android Binder/AIDL `getDriverHalGapsJson`、Linux CLI `driver-gaps`，并把 `driver_hal_gap_backlog` 纳入 `/native/adapters/detail`；覆盖 KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005、HW-002、NV-F-002、NV-F-004、NV-F-005、NV-F-006、NV-F-011、NV-P-001、NV-P-006、HV-001..003。该增量只记录 NPU、Vehicle bus、Camera/Audio/Sensors、Ethernet/SOME-IP/DDS/TSN、Shared memory/Safety Runtime 的触发条件、Android/Linux 目标接口和最小新增开发量，不新增 NPU/GPU/Camera/Audio/ETH/Vehicle bus Driver/HAL、Safety Runtime、共享内存、vendor SDK bridge 或虚拟化代码。

Linux systemd hardening sample 增量只收紧 `central-brain-backend.service`、`central-brain-governance.service`、`central-brain-linux-ipc.service` 和 `central-brain-linux-grpc.service` 的部署约束，并新增 `tools/check_central_brain_linux_systemd_hardening.sh`；覆盖 DEL-002、DEL-003、DEL-004、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-G-007。该增量只使用 systemd sandbox 配置、普通文件日志目录和 `/run/central-brain` socket 目录，不访问 NPU/GPU/Camera/Audio/ETH/Vehicle bus 驱动，不新增 Driver/HAL、Safety Runtime、共享内存、vendor SDK bridge 或虚拟化代码。

Linux package/profile 静态契约增量新增 `central-brain/deploy/linux/central-brain.package-profile.json` 和 `tools/check_central_brain_linux_package_profile.sh`，覆盖 DEL-002、DEL-003、DEL-004、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-G-007。该增量只校验 Linux 样例的安装根、service identity、env file、runtime/log 目录、systemd unit、hardening 和非目标边界；不新增 package manager 集成、LSM/SELinux/AppArmor policy、真实 gRPC runtime、NPU/GPU/Camera/Audio/ETH/Vehicle bus Driver/HAL、Safety Runtime、共享内存、vendor SDK bridge 或虚拟化代码。

## 驱动接口矩阵

| 接口域 | 图中位置 | Android 期望接口 | Linux 期望接口 | 当前环境能力 | 缺口/新增开发条件 |
| --- | --- | --- | --- | --- | --- |
| NPU | Drivers:NPU, HAL, Model Runtime Adapter | Vendor HAL/AIDL/NDK bridge 或 vendor SDK JNI/native bridge | `/dev/*`、ioctl、sysfs、vendor runtime library 或 PCIe userspace daemon | 仅 mock；可枚举 WSL PCI 设备样本 | 获得真实 PCIe NPU 卡、vendor id、SDK、driver 后评估 |
| GPU | Drivers:GPU, Libs | Android GLES/Vulkan/NN runtime fallback | Mesa/Vulkan/OpenCL/CUDA/厂商库，视 SoC | Android emulator host GPU/llvmpipe 可用 | 仅当模型或渲染要求当前 GPU 不满足时新增适配 |
| Camera | Drivers:Camera, Sensor/Actuator | Android Camera HAL/Camera2 | V4L2 或厂商 SDK | 当前无真实车载 camera | 接入真实摄像头/传感器时新增 adapter |
| Audio/Mic | Drivers:Audio, Sensor/Actuator | Audio HAL/AAudio/AudioRecord | ALSA/PulseAudio/PipeWire 或厂商 SDK | WSL/AVD 仅具备基础能力 | 语音 Agent 真机验证时补适配 |
| Ethernet/ETH | Drivers:ETH, Network stack | Android network stack/VHAL 或 vendor net service | Linux netdev/socket/SOME-IP stack | WSL 网络可用于 REST mock | SOME/IP、DDS、TSN/PTP 验证时补环境 |
| Vehicle bus | ECU Proxy/Signal Adapter | VHAL/AIDL vendor service | CAN SocketCAN、DBC/ARXML parser、vendor gateway | 仅 mock VSS signals | 用户提供 DBC/ARXML/网关后补 adapter |
| Shared memory | Memory management, cross-domain communication | AIDL shared memory/ashmem/HardwareBuffer | POSIX shm/memfd/DMA-BUF | 未使用 | 高频感知/模型数据接入时补设计 |
| Time sync | Data/Time Sync | Android time service/PTP support if available | PTP/TSN/linuxptp | 未使用 | ADAS/传感器融合验证时补适配 |

## NPU 接口最低抽象

无论 Android 还是 Linux，NPU 后端必须至少暴露以下抽象；完整 contract、状态机、错误码和集成检查点见 `docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md`：

```text
NpuDevice.discover()
NpuDevice.getStatus()
NpuModel.load(model_spec)
NpuModel.unload(model_id)
NpuSession.create(model_id, qos, safety_state)
NpuSession.infer(input_buffers, output_spec, timeout_ms)
NpuSession.cancel(request_id)
NpuDevice.getMetrics()
NpuDevice.reset(reason)
```

## 驱动缺口记录模板

| ID | 接口域 | 当前环境缺口 | 触发条件 | 新增开发量 | 状态 |
| --- | --- | --- | --- | --- | --- |
| DRV-GAP-001 | NPU | 无真实 PCIe NPU driver/vendor SDK | 用户提供硬件和 SDK | NPU runtime adapter + driver/HAL bridge | Open |

## 当前 Driver/HAL gap backlog

`GET /native/driver-gaps` 是当前可查询 backlog，供 Android/Linux 座舱域工程师确认哪些底层接口尚未进入开发。该接口的 `summary.driver_development_triggered=false` 是本轮验收条件，表示只建立缺口记录，不启动真实驱动工作。

| ID | 接口域 | 当前环境缺口 | 触发条件 | 最小新增开发量 | 状态 |
| --- | --- | --- | --- | --- | --- |
| DRV-GAP-001 | NPU | 无真实 PCIe NPU driver、HAL、vendor SDK | 用户提供真实 PCIe NPU 硬件、vendor id、SDK、driver ABI | Model Runtime Adapter bridge + Driver/HAL adapter shim | Open |
| DRV-GAP-002 | Vehicle bus | 无 VHAL、SocketCAN、DBC、ARXML、vendor gateway | 目标车型信号目录、DBC/ARXML、VHAL contract 或 gateway 可用 | Vehicle Signal Adapter 映射表 + read-only signal bridge，先读后控 | Open |
| DRV-GAP-003 | Camera/Audio/Sensors | 无真实车载 camera、mic array、radar、USS、IMU | 多模态 Agent 或 ADAS adapter 需要真实传感器数据 | Sensor/Actuator adapter read path + timestamp/quality metadata | Planned |
| DRV-GAP-004 | Ethernet/SOME-IP/DDS/TSN | WSL 网络只验证 REST/JSON；未验证 SOME/IP、DDS、TSN、PTP | 跨 ECU 服务、高频 topic 或时间同步数据成为必需 | Protocol Binding adapter + time-sync metadata contract | Planned |
| DRV-GAP-005 | Shared memory/Safety Runtime | 无 shared memory、DMA-BUF、IOMMU、Safety Runtime 集成 | 高吞吐模型/传感器数据或 ASIL/QM domain 集成需要 | Buffer envelope + Safety State bridge，等待目标平台约束 | Planned |
