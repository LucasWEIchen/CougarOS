# Android/Linux 座舱域交付目标

版本：0.1
日期：2026-07-04

## 交付对象

本项目交付对象是使用 Android 和 Linux 系统的座舱域软件工程师。交付物必须能帮助他们完成集成、调试、验证和二次开发，而不只是展示 Demo。

## 平台优先级

| 平台 | 优先级 | 交付定位 | 当前状态 |
| --- | --- | --- | --- |
| Android | 主路径 | App、SDK client、AIDL/Binder 设计、Android service 集成、模拟器/设备验证 | Console 已绑定 Binder service sample，并经 Binder 调用 Uni Info Bus/SOA |
| Linux | 同步交付 | CLI/client、daemon 形态、systemd/进程部署、IPC/REST/gRPC 集成、驱动接口说明 | CLI smoke 初版；Unix socket IPC daemon/client active sample；gRPC contract skeleton 初版；systemd 部署样例初版 |

## 每个核心模块的交付形态

| 模块 | Req ID | Android 交付 | Linux 交付 | 备注 |
| --- | --- | --- | --- | --- |
| AI SDK | XSC-001 | Android library/API sample | Linux SDK sample 或 CLI | 黄色小太阳，跨 SoC |
| Uni Info Bus 语义接口 | XSC-002 | Android client + contract | Linux client + contract | `/uib/context`、`/uib/state`、`/uib/events/*` 初版 |
| SOA 服务入口 | XSC-003 | Android service/client | Linux daemon/client | `/soa/services`、`/soa/invoke` 初版 |
| AIOS Kernel | XSC-004 | Native service adapter | Linux service adapter | `GET /native/adapters/detail` 初版 |
| Runtime & Governance | XSC-005 | Registry/Policy/Lifecycle/QoS integration | daemon modules + JSONL audit persistence sample + QoS fixed-window sample | `/governance/runtime`、`/policy/evaluate`、`/audit/recent` active prototype；`CENTRAL_BRAIN_AUDIT_LOG` 可恢复最近审计；`/soa/invoke` 执行 NV-G-004 QoS 检查 |
| Protocol Binding | XSC-006 | Console Binder client path + Binder/AIDL service stub sample，service 上游仍代理 REST prototype，含 Event 语义映射 | REST active prototype + Unix socket IPC daemon/client active sample + gRPC contract skeleton + systemd sample，含 Event 语义映射；MQTT/SOME-IP/DDS 计划态 | `/bindings/detail` 返回 binding artifact、sample 状态和 Req ID；DDS 不在本轮实现 |
| Model Runtime Adapter | NV-F-011 | Android native/runtime bridge + NPU runtime interface contract | Linux runtime bridge + NPU runtime interface contract | NPU/GPU/Cloud 后端可替换；见 `CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md` |
| Driver/HAL interface | KH-003, KH-006 | Android HAL/AIDL/NDK interface docs | Linux device node/ioctl/sysfs/libs docs | 只在缺口处新增开发；NPU 检查点已文档化 |
| Hypervisor/Safety constraints | HV-001, HV-002, HV-003 | Android domain、Binder identity、Safety State 和 Policy 集成假设 | Linux domain、service identity、IPC fallback 和 Safety State 集成假设 | 只记录接口约束和部署假设，不开发虚拟化 |

## 交付包要求

每个阶段交付必须包含：

- 接口文档：contract、字段、错误码、权限、安全状态。
- Android 使用说明：构建、安装、运行、日志、验证命令。
- Linux 使用说明：启动、配置、CLI/API、日志、验证命令。
- 平台差异说明：IPC、权限、服务部署、日志路径、驱动接口差异。
- 偏差记录：当前实现与架构图基线不一致之处。
- 缺口记录：当前环境无法满足的驱动、HAL、协议或硬件能力。

## Linux 版本最低要求

短期 Linux 版本不要求 UI，但必须提供：

- 可运行的 backend/daemon。
- 可调用 Uni Info Bus/SOA Gateway 的 CLI 或 Python client。
- 与 Android 相同的 contract。
- 可执行 smoke test。
- 驱动/HAL 接口支持矩阵。

当前最低 Linux 样例：

```bash
bash tools/smoke_central_brain_semantic_gateway.sh
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py state
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py events
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-publish
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-recent
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py audit
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py binding-detail
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py native-adapters-detail
bash tools/check_central_brain_binding_artifacts.sh
bash tools/check_central_brain_delivery_docs.sh
bash tools/check_central_brain_virtualization_docs.sh
bash tools/smoke_central_brain_audit_persistence.sh
bash tools/smoke_central_brain_qos.sh
bash tools/smoke_central_brain_linux_ipc.sh
```

Linux systemd 部署样例：

- `central-brain/deploy/linux/central-brain.env.example`
- `central-brain/deploy/linux/systemd/central-brain-backend.service`
- `central-brain/deploy/linux/systemd/central-brain-linux-ipc.service`
- `docs/CENTRAL_BRAIN_PLATFORM_DELTA.md`
- `docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md`
- `docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md`
- `tools/check_central_brain_npu_interface.sh`

## Android 版本最低要求

Android 版本必须提供：

- 可安装 APK 或 Android library sample。
- 与 Linux 共用的 contract。
- 模拟器或设备验证脚本。
- 日志与截图留档。
- AIDL/System Service 目标接口草案与 Binder service/client sample。

当前 Android Console 主路径：

- 绑定 `CentralBrainGatewayBinderService`。
- 通过 `CentralBrainGatewayClient.getStateJson` 调用 Uni Info Bus State。
- 通过 `CentralBrainGatewayClient.invokeServiceJson` 调用 SOA Inference。
- Binder service sample 内部仍以 REST prototype gateway 作为上游绑定，不代表量产 system service。

当前 Android binding service stub sample：

- `central-brain/bindings/android/aidl/com/centralbrain/binding/ICentralBrainGateway.aidl`
- `central-brain/bindings/android/java/com/centralbrain/binding/CentralBrainGatewayBinderService.java`
- `central-brain/bindings/android/java/com/centralbrain/binding/CentralBrainGatewayClient.java`
- `GET /bindings/detail`
- `GET /native/adapters/detail`
- `GET /uib/events/topics`
- `POST /uib/events/publish`
- `GET /uib/events/recent`
