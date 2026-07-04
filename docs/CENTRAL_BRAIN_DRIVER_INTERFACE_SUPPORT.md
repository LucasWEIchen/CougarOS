# 驱动层接口支持矩阵

版本：0.1
日期：2026-07-04

## 范围声明

驱动层不作为默认开发范围。只有当前 Android/Linux 环境无法满足中央大脑架构接口时，才新增最小开发量。即便不开发驱动，驱动接口支持必须被明确记录，供座舱域工程师评估集成风险。

虚拟化层不开发；如果某个驱动接口依赖 Hypervisor、跨 VM 共享内存或安全域通信，本项目只记录依赖假设和 fallback，不实现虚拟化功能。

2026-07-04 本轮语义网关、Runtime & Governance 与 Protocol Binding contract skeleton 增量只新增 Uni Info Bus/SOA/Governance/Binding mock、Policy/Audit active prototype、Android AIDL skeleton、Linux IPC/gRPC contract skeleton 与 Linux CLI，不访问真实 NPU/GPU/Camera/Audio/ETH/Vehicle bus 驱动，因此未触发新增驱动开发条件。

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

无论 Android 还是 Linux，NPU 后端必须至少暴露以下抽象：

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
