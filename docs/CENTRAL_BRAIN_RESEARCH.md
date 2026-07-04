# 车载中央大脑资料纪要

更新时间：2026-07-04

## 输入材料

- 用户提供的架构图已归档到 `docs/assets/central_brain_architecture_source.png`。
- 图中核心分层：应用层、Framework 层、Native 层、Kernel & HAL 层、虚拟化层、硬件层。
- 图中关键机制：Uni Info Bus 语义接口、SOA 服务入口、Uni Info Bus Runtime & Governance、Protocol Binding、AIOS Kernel、Model Runtime Adapter、Safety State、Security/Policy Adapter、Registry/Discovery/Schema/QoS/Policy/Lifecycle。

## 外部资料结论

1. Android Automotive OS 是直接运行在车载硬件上的完整开源车载平台，可作为座舱/车机侧系统集成基线。
   来源：https://source.android.com/docs/automotive

2. VHAL 是 AAOS 与车辆能力之间的属性抽象层。Android 13 及以上 VHAL 已迁移到 AIDL；新 VHAL 实现应使用 AIDL。
   来源：https://source.android.com/docs/automotive/vhal

3. Stable AIDL 适合做跨进程、跨版本的 Android 系统/供应商接口边界，接口可以被版本化并保持后向兼容。
   来源：https://source.android.com/docs/core/architecture/aidl/stable-aidl

4. NNAPI 已在 Android 15 弃用，因此新项目不应把外置 NPU 接入强绑定到 NNAPI。优先设计为供应商推理运行时 + 系统服务/API 适配层，必要时再提供 LiteRT/TensorFlow Lite、ONNX Runtime 或厂商 SDK bridge。
   来源：https://developer.android.com/ndk/guides/neuralnetworks/migration-guide

5. COVESA VSS 可作为车辆信号命名与语义模型基线，用于减少不同车型、ECU、云端和应用之间的信号碎片化。
   来源：https://covesa.global/project/vehicle-signal-specification/

6. SOME/IP-SD 在车载 IP 网络中用于服务发现、实例运行状态检测和发布订阅处理；适合对接传统/量产车载 SOA 网络。
   来源：https://www.autosar.org/fileadmin/standards/R22-11/FO/AUTOSAR_PRS_SOMEIPServiceDiscoveryProtocol.pdf

7. DDS 适合高性能发布订阅数据分发。Eclipse Cyclone DDS 是开源 DDS 实现，并被 ROS 2 作为一级中间件使用；适合 ADAS/感知/融合类数据通道验证。
   来源：https://cyclonedds.io/docs/cyclonedds/latest/about_dds/eclipse_cyclone_dds.html

8. Android Auto / AAOS 应用需要满足车载质量、分心控制和应用类别规则。中央大脑的手机/座舱前端必须区分驾驶中可用能力和驻车/调试能力。
   来源：https://developer.android.com/docs/quality-guidelines/car-app-quality

9. Linux PCIe + DMA 是外置 NPU 卡接入的底层基线。驱动必须处理 BAR、中断、电源状态、DMA coherent/streaming 映射、IOMMU 隔离与错误恢复。
   来源：https://www.kernel.org/doc/html/latest/driver-api/pci/pci.html
   来源：https://www.kernel.org/doc/html/latest/core-api/dma-api-howto.html
   来源：https://www.kernel.org/doc/html/latest/driver-api/vfio.html

10. 产品级车载软件需要从第一天保留功能安全、网络安全、OTA 与追溯能力。设计基线参考 ISO 26262、ISO/SAE 21434、UNECE R155/R156。
    来源：https://www.iso.org/standard/43464.html
    来源：https://www.iso.org/standard/70918.html
    来源：https://unece.org/transport/documents/2021/03/standards/un-regulation-no-155-cyber-security-and-cyber-security
    来源：https://unece.org/transport/documents/2021/03/standards/un-regulation-no-156-software-update-and-software-update

## 本地环境发现

- Git 可用：`git version 2.43.0`。
- Android SDK/Emulator 工具链已存在于项目 `.tools/`，主要版本：
  - Java 17
  - Android platform 36
  - Android build-tools 37.0.0
  - Android Emulator 36.6.11
  - x86_64 API 36 Google APIs system image
- 现有 AVD：`cabin_client_api36_x86_64`。
- 当前仓库已有三套 APK 逆向产物和模拟器测试脚本，本任务不会在第一阶段修改它们。
- OpenCLAW 可用但未加入当前 shell 的 `PATH`。绝对路径可运行：`/home/normad400/.npm-global/bin/openclaw`，版本 `OpenClaw 2026.6.9`。
- 当前直接运行 `openclaw` 失败，原因是 `PATH` 未包含 `/home/normad400/.npm-global/bin`。

## 对本项目的设计约束

- Android 侧短期用普通 Android App + HTTP mock 服务验证应用层/中间层契约；长期再拆为 AAOS 系统服务、AIDL HAL、VHAL/Car API、Native runtime。
- NPU 侧短期用 Python mock 服务模拟 PCIe NPU 后端；长期需要替换为 Linux/Android kernel driver、vendor runtime daemon、模型管理服务和推理 API。
- 服务总线要支持两类协议：车内量产网络优先 SOME/IP；AI/工具/云/开发链路优先 gRPC/REST/MQTT；感知高频数据保留 DDS 通道。
- 信号模型优先映射到 VSS，再桥接 AAOS VHAL 属性、ECU 信号和应用语义上下文。
- 安全上默认分域：驾驶安全域、座舱信息域、AI/工具域、云连接域。跨域调用必须走策略检查、审计和降级状态。
