# Android/Linux 座舱域交付目标

版本：0.1
日期：2026-07-04

## 交付对象

本项目交付对象是使用 Android 和 Linux 系统的座舱域软件工程师。交付物必须能帮助他们完成集成、调试、验证和二次开发，而不只是展示 Demo。

## 平台优先级

| 平台 | 优先级 | 交付定位 | 当前状态 |
| --- | --- | --- | --- |
| Android | 主路径 | App、SDK client、AIDL/Binder 设计、Android system/privileged service 集成约束、模拟器/设备验证 | Console 已绑定 Binder service sample，并可触发 `planAgentTaskJson`、`executeAgentTaskJson`、`invokeSkillJson`、`queryMemoryJson`、`precheckGovernanceJson`、`getDriverHalGapsJson`；system service integration note 初版 |
| Linux | 同步交付 | CLI/client、daemon 形态、systemd/进程部署、IPC/REST/gRPC 集成、驱动接口说明 | CLI smoke 初版；Linux CLI 提供 `driver-gaps`；Unix socket IPC daemon/client active sample 已含 execute/Skill/Memory mock；gRPC/RPC JSON contract sample 初版；systemd 部署样例初版 + hardening check + package profile check |

## 每个核心模块的交付形态

| 模块 | Req ID | Android 交付 | Linux 交付 | 备注 |
| --- | --- | --- | --- | --- |
| AI SDK | XSC-001 | Android Binder/AIDL `planAgentTaskJson`、`executeAgentTaskJson`、Skill/Memory contract sample + `/ai/sdk/capabilities` | Linux CLI/IPC `agent-plan`、`agent-execute`、`skill-invoke`、`memory-query` active sample + `/ai/sdk/capabilities` | 黄色小太阳，跨 SoC；当前是 facade/plan/execute/Skill/Memory contract mock，不是真实 SDK library |
| Uni Info Bus 语义接口 | XSC-002 | Android client + contract | Linux client + contract | `/uib/context`、`/uib/state`、`/uib/events/*`、`/uib/actions/request` 初版 |
| SOA 服务入口 | XSC-003 | Android service/client | Linux daemon/client | `/soa/services`、`/soa/invoke` 初版 |
| AIOS Kernel | XSC-004 | Native service adapter + Agent execute/Skill/Memory boundary sample + Driver/HAL gap visibility | Linux service adapter + Agent execute/Skill/Memory boundary sample + `driver-gaps` CLI | `GET /native/adapters/detail` 与 `GET /native/driver-gaps`；AIOS Kernel 真实 runtime 仍未实现 |
| Runtime & Governance | XSC-005 | Registry/Policy/Lifecycle/QoS integration + Console `Precheck` 调用 `precheckGovernanceJson` + Binder `getGovernanceBackendContractJson` 目标契约可见性 | daemon modules + JSONL audit persistence sample + QoS fixed-window sample + Linux shared governance daemon precheck/runtime/audit diagnostics + IPC/gRPC shared governance client precheck + local SOA precheck fallback + `governance-precheck` + `governance-backend-contract` | `/governance/runtime`、`/governance/precheck`、`/governance/backend-contract`、`/policy/evaluate`、`/audit/recent` active prototype；`CENTRAL_BRAIN_AUDIT_LOG` 可恢复最近审计；`/soa/invoke`、Linux governance daemon 与 Linux IPC/gRPC `soa.service.invoke` 执行 NV-G-004 QoS 检查；shared governance socket 可直接查询 runtime/audit；`/governance/precheck` 默认只检查不消费 QoS；`/governance/backend-contract` 是目标契约，不是量产治理后端 |
| Protocol Binding | XSC-006 | Console Binder client path + Binder/AIDL service stub sample + Android system/privileged service integration note，service 上游仍代理 REST prototype，含 Event 语义映射和 shared governance backend target contract | REST active prototype + Unix socket IPC daemon/client active sample with shared governance client precheck/runtime/audit diagnostics/backend contract visibility + Linux gRPC/RPC JSON contract sample + systemd sample + unit hardening check + package profile check，含 Event 语义映射；MQTT/SOME-IP/DDS 计划态 | `/bindings/detail` 返回 binding artifact、sample 状态和 Req ID；当前 gRPC/RPC sample 因环境无 `grpcio` 使用 JSON TCP wrapper；DDS 不在本轮实现 |
| Model Runtime Adapter | NV-F-011 | Android native/runtime bridge + NPU runtime interface contract | Linux runtime bridge + NPU runtime interface contract | NPU/GPU/Cloud 后端可替换；见 `CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md` |
| Driver/HAL interface | KH-003, KH-006, DEL-005 | Android HAL/AIDL/NDK interface docs + Console `Driver Gaps` 调用 Binder `getDriverHalGapsJson` | Linux device node/ioctl/sysfs/libs docs + CLI `driver-gaps` | 只在缺口处新增开发；NPU 检查点和 Driver/HAL gap backlog 已文档化/可查询 |
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
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py driver-gaps
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py ai-sdk
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py agent-plan
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py agent-execute
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py skills
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py skill-invoke
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py memory-query
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py action-request
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py governance-precheck
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py governance-backend-contract
bash tools/check_central_brain_binding_artifacts.sh
bash tools/check_central_brain_delivery_docs.sh
bash tools/check_central_brain_linux_package_profile.sh
bash tools/check_central_brain_virtualization_docs.sh
bash tools/smoke_central_brain_audit_persistence.sh
bash tools/smoke_central_brain_qos.sh
bash tools/smoke_central_brain_linux_ipc.sh
bash tools/smoke_central_brain_linux_grpc.sh
```

Linux IPC `infer-denied` 样例用于验证 Unix socket binding 在转发到 REST prototype gateway 前先执行 Runtime & Governance precheck；配置 `CENTRAL_BRAIN_GOVERNANCE_SOCKET` 时优先走共享 governance daemon，不可用时回退本地 precheck。该 shared governance socket 还可直接处理 `governance.runtime.get` 与 `audit.recent.get` 诊断，验证 XSC-005/NV-G-007 的 Linux 同步可见性：

```bash
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py infer-denied
```

Linux systemd 部署样例：

- `central-brain/deploy/linux/central-brain.env.example`
- `central-brain/deploy/linux/central-brain.package-profile.json`
- `central-brain/deploy/linux/systemd/central-brain-backend.service`
- `central-brain/deploy/linux/systemd/central-brain-governance.service`
- `central-brain/deploy/linux/systemd/central-brain-linux-ipc.service`
- `central-brain/deploy/linux/systemd/central-brain-linux-grpc.service`
- `docs/CENTRAL_BRAIN_PLATFORM_DELTA.md`
- `docs/CENTRAL_BRAIN_ANDROID_SYSTEM_SERVICE_INTEGRATION.md`
- `docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md`
- `docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md`
- `tools/check_central_brain_android_system_service_docs.sh`
- `tools/check_central_brain_npu_interface.sh`
- `tools/check_central_brain_linux_systemd_hardening.sh`
- `tools/check_central_brain_linux_package_profile.sh`

## Android 版本最低要求

Android 版本必须提供：

- 可安装 APK 或 Android library sample。
- 与 Linux 共用的 contract。
- 模拟器或设备验证脚本。
- 日志与截图留档。
- AIDL/System Service 目标接口草案与 Binder service/client sample。
- Android system/privileged service 集成约束、权限/SELinux 假设和 Binder identity 到 Policy 的映射说明。

当前 Android Console 主路径：

- 绑定 `CentralBrainGatewayBinderService`。
- 通过 `CentralBrainGatewayClient.getStateJson` 调用 Uni Info Bus State。
- 通过 `CentralBrainGatewayClient.planAgentTaskJson` 调用 AI SDK/Agent task plan。
- 通过 `CentralBrainGatewayClient.executeAgentTaskJson` 验证 Agent execute contract mock，只返回 policy-checked dispatch 边界。
- 通过 `CentralBrainGatewayClient.invokeSkillJson` 验证 Skill/Tool contract mock，不运行真实 sandbox 或车身总线。
- 通过 `CentralBrainGatewayClient.queryMemoryJson` 验证本地 Memory query contract mock，不允许 cloud sync。
- 通过 `CentralBrainGatewayClient.precheckGovernanceJson` 验证 Runtime & Governance discovery、Policy、Lifecycle、QoS 的只检查不调用路径。
- 通过 `CentralBrainGatewayClient.getGovernanceBackendContractJson` 查看共享 Runtime & Governance 后端目标契约，确认 Binder/IPC/gRPC 未来替换时共用 `governance.precheck`、`governance.runtime.get`、`audit.recent.get` 操作边界。
- 通过 `CentralBrainGatewayClient.getDriverHalGapsJson` 查看 KH-003/KH-006/DEL-005 的 Driver/HAL gap backlog；该路径只读，不触发任何驱动开发或 HAL 调用。
- Binder service sample 内部仍以 REST prototype gateway 作为上游绑定，不代表量产 system service。

当前 Android binding service stub sample：

- `central-brain/bindings/android/aidl/com/centralbrain/binding/ICentralBrainGateway.aidl`
- `central-brain/bindings/android/java/com/centralbrain/binding/CentralBrainGatewayBinderService.java`
- `central-brain/bindings/android/java/com/centralbrain/binding/CentralBrainGatewayClient.java`
- `GET /bindings/detail`
- `GET /native/adapters/detail`
- `GET /native/driver-gaps`
- `GET /uib/events/topics`
- `POST /uib/events/publish`
- `GET /uib/events/recent`
- `GET /ai/sdk/capabilities`
- `POST /agent/plan`
- `POST /agent/execute`
- `GET /skills`
- `POST /skills/{skill_id}/invoke`
- `POST /memory/query`
- `POST /uib/actions/request`
- `POST /governance/precheck`
- `GET /governance/backend-contract`

当前 Android system/privileged service integration note：

- `docs/CENTRAL_BRAIN_ANDROID_SYSTEM_SERVICE_INTEGRATION.md`
- `bash tools/check_central_brain_android_system_service_docs.sh`
- 覆盖 DEL-001、DEL-003、DEL-004、XSC-002、XSC-003、XSC-005、XSC-006、NV-P-002、NV-P-005、FW-U-007、FW-S-005、NV-G-005。
- 本轮不新增 Android framework patch、priv-app 签名配置、SELinux policy、Driver/HAL、Safety Runtime 或虚拟化层代码。
