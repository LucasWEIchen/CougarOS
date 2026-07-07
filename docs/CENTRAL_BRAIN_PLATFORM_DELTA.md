# Android/Linux 平台差异说明

版本：0.1
日期：2026-07-07

## 范围

本文件覆盖 DEL-001、DEL-002、DEL-003、DEL-004，以及跨 SoC 组件
XSC-001、XSC-002、XSC-003、XSC-004、XSC-005、XSC-006 的 Android 主开发路径与 Linux 同步交付路径差异。

虚拟化层不开发；相关内容只作为 HV-001..003 的部署假设，详见
`docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md`。驱动层不默认新增开发；
Driver/HAL 缺口仍按 DEL-005、KH-003、KH-006 在
`docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md` 维护，并可通过
`GET /native/driver-gaps`、Android Binder `getDriverHalGapsJson` 与 Linux CLI
`driver-gaps` 查询。硬件依赖空接口按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces`、Android Binder `getHardwareInterfacesJson`、Linux CLI
`hardware-interfaces`、Linux IPC `hardware.interfaces.get` 与 Linux gRPC/RPC
`GetHardwareInterfaces` 查询，且不触发真实硬件访问。

Vehicle/Body Signal 只读目录按 NV-F-004、NV-F-005、XSC-004、DEL-005 在
`GET /vehicle/signals`、Android Binder `getVehicleSignalsJson`、Linux CLI
`vehicle-signals`、Linux IPC `vehicle.signals.list` 与 Linux gRPC/RPC
`GetVehicleSignals` 查询。该视图只暴露 VSS-style catalog、ECU/Signal Adapter
边界和 DRV-GAP-002 链接，不加载 DBC/ARXML，不连接 VHAL、SocketCAN、vendor gateway
或真实车辆总线。

Vehicle Signal 读桥激活准入按 NV-F-003、NV-F-004、NV-F-005、XSC-004、DEL-005 在
`GET /vehicle/signals/activation`、Android Binder `getVehicleSignalActivationJson`、Linux CLI
`vehicle-signal-activation`、Linux IPC `vehicle.signals.activation.get` 与 Linux gRPC/RPC
`GetVehicleSignalActivation` 查询。该视图只暴露 DBC/ARXML、Android VHAL/vendor AIDL、
Linux SocketCAN、vendor gateway/SOME-IP 的准入门禁，不激活真实读桥，不触发 Driver/HAL 开发。

Vehicle Signal 读桥校验证据按 NV-F-003、NV-F-004、NV-F-005、XSC-004、DEL-005 在
`GET /vehicle/signals/validation`、Android Binder `getVehicleSignalValidationJson`、Linux CLI
`vehicle-signal-validation`、Linux IPC `vehicle.signals.validation.get` 与 Linux gRPC/RPC
`GetVehicleSignalValidation` 查询。该视图只暴露 schema source metadata、adapter owner、
ABI owner、Android/Linux parity 和 DRV-GAP-002 evidence 门禁，不解析 DBC/ARXML，不激活真实读桥，
不触发 Driver/HAL 开发。

Python 原型成熟度总览按 XSC-001..006、DEL-001..005、HW-002、KH-003、KH-006、KH-007 在
`GET /prototype/readiness`、Android Binder `getPrototypeReadinessJson`、Linux CLI
`prototype-readiness`、Linux IPC `prototype.readiness.get` 与 Linux gRPC/RPC
`GetPrototypeReadiness` 查询。该视图只汇总模块成熟度、Android/Linux 绑定可见性、
开放偏差、开放问题和下一步候选增量，不 dispatch SOA service，不访问硬件，不触发
Driver/HAL 或虚拟化开发。

Uni Info Bus Event 订阅生命周期与 transport readiness 契约按 FW-U-003、NV-P-006、XSC-002、XSC-006 在
`GET /uib/events/subscriptions`、`POST /uib/events/subscriptions/request`、`POST /uib/events/subscriptions/cancel`、`GET /uib/events/subscriptions/transport-readiness`、Android Binder
`getEventSubscriptionsJson`/`requestEventSubscriptionJson`/`cancelEventSubscriptionJson`/`getEventSubscriptionTransportReadinessJson`、Linux CLI
`event-subscriptions`/`event-subscribe-request`/`event-subscribe-cancel`/`event-subscription-transport-readiness`、Linux IPC
`uib.events.subscriptions.get`/`uib.events.subscriptions.request`/`uib.events.subscriptions.cancel`/`uib.events.subscriptions.transport.readiness` 与 Linux gRPC/RPC
`GetEventSubscriptions`/`RequestEventSubscription`/`CancelEventSubscription`/`GetEventSubscriptionTransportReadiness` 查询。该视图只暴露订阅 lifecycle、cursor/replay、filter、
QoS/backpressure、Runtime & Governance、request/cancel contract-only 命令、callback/watch transport readiness 和 binding parity，不持久化 subscription，不选择 transport，不注册
callback/watch，不启动真实订阅 broker、SSE/WebSocket、DDS runtime 或高频数据面，也不触发 Driver/HAL 或虚拟化开发。

## 差异矩阵

| 维度 | Android 主开发路径 | Linux 同步交付路径 | 当前交付状态 | Req ID |
| --- | --- | --- | --- | --- |
| 应用入口 | Android Console APK 和后续 AI SDK client；Console 已通过 Binder 暴露 `planAgentTaskJson`、`executeAgentTaskJson`、`invokeSkillJson`、`queryMemoryJson`、`getEventSubscriptionsJson`、`requestEventSubscriptionJson`、`cancelEventSubscriptionJson`、`getEventSubscriptionTransportReadinessJson`、`precheckGovernanceJson`、`getGovernanceDeploymentPlanJson`、`getBindingReadinessJson`、`getDeliveryReadinessJson`、`getPrototypeReadinessJson`、`getDriverHalGapsJson`、`getHardwareInterfacesJson`、`getVehicleSignalsJson`、`getVehicleSignalActivationJson`、`getVehicleSignalValidationJson` | CLI/client，无 UI 最低样例；CLI/IPC 暴露 `agent-plan`、`agent-execute`、`skill-invoke`、`memory-query`、`event-subscriptions`/`event-subscribe-request`/`event-subscribe-cancel`/`event-subscription-transport-readiness`、`uib.events.subscriptions.get`/`uib.events.subscriptions.request`/`uib.events.subscriptions.cancel`/`uib.events.subscriptions.transport.readiness`、`governance-precheck`、`governance-deployment-plan`、`binding-readiness`、`delivery-readiness`、`prototype-readiness`、`driver-gaps`、`hardware-interfaces`、`vehicle-signals`/`vehicle.signals.list`、`vehicle-signal-activation`/`vehicle.signals.activation.get`、`vehicle-signal-validation`/`vehicle.signals.validation.get` | Console + CLI 初版；AI SDK/Agent plan + execute/Skill/Memory contract mock；Event subscription lifecycle + transport readiness contract；Governance precheck、deployment plan、Protocol Binding readiness、Delivery readiness、Prototype readiness、Driver/HAL gap backlog、hardware empty-interface registry、Vehicle/Body Signal catalog、read-bridge activation criteria 与 validation envelope 可见 | DEL-001, DEL-002, DEL-003, DEL-004, DEL-005, XSC-001, XSC-002, XSC-004, XSC-005, XSC-006, APP-004, FW-U-003, FW-U-006, NV-F-003, NV-F-004, NV-F-005, NV-P-006, HW-002, KH-003, KH-006, KH-007 |
| Uni Info Bus | App/client 调用 `/uib/*`，Binder sample 暴露 `getContextJson`、`getStateJson` | CLI 和 Unix socket IPC operation 映射 `uib.context.get`、`uib.state.get` | active prototype | XSC-002, FW-U-001, FW-U-002 |
| SOA 服务入口 | Binder sample 暴露服务目录和 invoke 方法，当前代理语义网关 | CLI、Unix socket IPC 和 systemd gateway sample | active prototype + Linux unit sample | XSC-003, FW-S-004, FW-S-005 |
| Runtime & Governance | 通过 `/policy/evaluate`、`/governance/precheck`、`/governance/backend-contract`、`/governance/migration-check`、`/governance/deployment-plan`、`/governance/runtime`、`/audit/recent` 验证；Console `Precheck` 按钮通过 Binder `precheckGovernanceJson` 直接触发只检查不调用路径；Binder `getGovernanceBackendContractJson`/`getGovernanceMigrationCheckJson`/`getGovernanceDeploymentPlanJson` 可查共享治理后端目标契约、替换 readiness 和部署计划 | 同一 contract；Linux CLI/IPC 提供 `governance-precheck`/`governance.precheck`、`governance-backend-contract`/`governance.backend.contract.get`、`governance-migration-check`/`governance.migration.check` 和 `governance-deployment-plan`/`governance.deployment.plan.get`；Linux IPC/gRPC 对 `soa.service.invoke` 优先通过 reusable shared governance client 调用 governance daemon precheck，不可用时回退本地 precheck；shared governance socket 可直接查询 `governance.runtime.get` 和 `audit.recent.get` | active prototype；`/governance/precheck` 默认不消费 QoS 且不 dispatch 服务；`/governance/backend-contract` 是目标契约；`/governance/migration-check` 是 readiness contract；`/governance/deployment-plan` 是部署形态 contract；Linux governance daemon/client 是共享 socket 样例，不是量产治理后端 | XSC-005, NV-G-001..007 |
| Protocol Binding | Binder/AIDL service stub sample；REST 只是 prototype binding；Binder contract 暴露 `/uib/events/subscriptions`、`/uib/events/subscriptions/request`、`/uib/events/subscriptions/cancel`、`/uib/events/subscriptions/transport-readiness`、`/soa/contracts`、`/vehicle/signals`、`/vehicle/signals/activation`、`/vehicle/signals/validation`、shared governance backend target、migration readiness、deployment plan、binding readiness、delivery readiness 和 prototype readiness | Unix socket IPC active sample；gRPC/RPC JSON contract sample；REST prototype binding；systemd unit hardening check + package profile check；IPC/gRPC 均可查询 `/uib/events/subscriptions`、`/uib/events/subscriptions/request`、`/uib/events/subscriptions/cancel`、`/uib/events/subscriptions/transport-readiness`、`/soa/contracts`、`/vehicle/signals`、`/vehicle/signals/activation`、`/vehicle/signals/validation`、backend contract、migration readiness、deployment plan、binding readiness、delivery readiness 和 prototype readiness | Android stub + Linux active samples；`/bindings/readiness` 汇总 active sample、planned binding、阻塞项和验证命令；`/delivery/readiness` 汇总 Android/Linux 交付样例、验证 bundle 和阻塞项；`/prototype/readiness` 汇总原型模块成熟度、偏差、问题和下一步候选增量；当前 gRPC/RPC sample 因环境无 `grpcio` 使用 JSON TCP wrapper | XSC-002, XSC-003, XSC-006, FW-U-003, NV-P-001, NV-P-002, NV-P-003, NV-P-004, NV-P-005, NV-P-006, DEL-001, DEL-002, DEL-003, DEL-004, DEL-005 |
| 服务部署 | Debug APK 内置 Binder sample；量产目标为 AAOS system/privileged service 约束 | `central-brain-backend.service` + `central-brain-governance.service` + `central-brain-linux-ipc.service` + `central-brain-linux-grpc.service` 样例，含 unit hardening check 与 `central-brain.package-profile.json` | Android system service integration note + Linux systemd sample + hardening/package profile check | DEL-001, DEL-002, DEL-003, DEL-004 |
| 权限模型 | Android app permission、Binder caller identity、signature permission、Runtime & Governance policy | Linux service user/group、Unix socket mode、Runtime & Governance policy | Android 权限/SELinux 假设文档化，未接入真实系统权限 | FW-U-007, FW-S-005, NV-G-005, DEL-004 |
| 日志与审计 | Android logcat + `/audit/recent`；可通过服务配置指定 `CENTRAL_BRAIN_AUDIT_LOG` | journald + `/audit/recent`；可指定 gateway JSONL audit log 路径、shared governance audit log、IPC fallback audit log | JSONL 持久化样例已可验证，量产仍需轮转/导出/权限加固 | XSC-005, NV-G-007, DEL-002, DEL-004 |
| Driver/HAL | Android HAL/AIDL/NDK/vendor SDK bridge，当前不新增驱动；Console `Driver Gaps`、`Hardware IF`、`Vehicle Signals`、`Signal Gate` 和 `Signal Check` 按钮通过 Binder `getDriverHalGapsJson`/`getHardwareInterfacesJson`/`getVehicleSignalsJson`/`getVehicleSignalActivationJson`/`getVehicleSignalValidationJson` 可查 gap backlog、空接口目录、只读信号目录、读桥准入条件和读桥校验证据 | Linux device node/ioctl/sysfs/vendor lib，当前不新增驱动；CLI `driver-gaps`/`hardware-interfaces`/`vehicle-signals`/`vehicle-signal-activation`/`vehicle-signal-validation`、IPC `hardware.interfaces.get`/`vehicle.signals.list`/`vehicle.signals.activation.get`/`vehicle.signals.validation.get` 和 gRPC/RPC `GetHardwareInterfaces`/`GetVehicleSignals`/`GetVehicleSignalActivation`/`GetVehicleSignalValidation` 可查 gap backlog、空接口目录、只读信号目录、读桥准入条件与读桥校验证据 | 接口矩阵 + `/native/driver-gaps` contract + `/hardware/interfaces` empty-interface registry + `/vehicle/signals` read-only catalog + `/vehicle/signals/activation` activation criteria + `/vehicle/signals/validation` validation envelope | DEL-005, KH-003, KH-006, KH-007, HW-002, NV-F-003, NV-F-004, NV-F-005, XSC-004, XSC-006 |
| 虚拟化 | 只记录 Hypervisor/ASIL/QM 接口约束 | 只记录跨 VM 通信假设和 fallback | 非开发范围 | HV-001..003 |

## 虚拟化与 Safety 约束

A7 交付文档 `docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md` 固定以下边界：

- HV-001：Hypervisor 仅作为 domain、transport、共享内存和启动依赖假设，不开发虚拟化功能。
- HV-002：ASIL/QM 隔离只做服务到安全域映射和降级策略说明，不实现隔离机制。
- HV-003：跨 VM 通信必须保留 Uni Info Bus/SOA 语义，不能让 App 直连 Hypervisor channel。
- FW-S-005、NV-G-005、NV-F-009：Safety State、Policy 和 Security/Policy Adapter 是 Android/Linux 共同约束。

## Linux systemd 样例

Linux 部署样例位于 `central-brain/deploy/linux/`：

- `central-brain.env.example`：端口、base URL、Unix socket 路径、gateway audit log、shared governance audit log 和 IPC fallback audit log。
- `systemd/central-brain-backend.service`：语义网关 backend。
- `systemd/central-brain-governance.service`：Linux shared Runtime & Governance socket sample。
- `systemd/central-brain-linux-ipc.service`：Linux IPC Protocol Binding daemon。
- `systemd/central-brain-linux-grpc.service`：Linux gRPC/RPC JSON contract sample，验证 proto envelope 与 shared governance precheck。
- `central-brain.package-profile.json`：Linux cockpit-domain 样例 profile，固定 install root、service identity、env file、runtime/log 目录、四个 unit、Req ID、hardening 和非目标边界。
- `tools/check_central_brain_linux_systemd_hardening.sh`：检查 service user/group、`NoNewPrivileges`、`PrivateTmp`、`PrivateDevices`、`ProtectSystem=strict`、`ProtectHome=true`、`RestrictSUIDSGID`、`LockPersonality`、`PYTHONDONTWRITEBYTECODE`、`LogsDirectory` 和最小 `ReadWritePaths`。
- `tools/check_central_brain_linux_package_profile.sh`：检查 package profile、env example 与 systemd unit 的字段一致性。

验证命令：

```bash
bash tools/check_central_brain_delivery_docs.sh
bash tools/check_central_brain_linux_package_profile.sh
bash tools/check_central_brain_linux_systemd_hardening.sh
bash tools/check_central_brain_virtualization_docs.sh
bash tools/smoke_central_brain_semantic_gateway.sh
bash tools/smoke_central_brain_linux_ipc.sh
bash tools/smoke_central_brain_linux_grpc.sh
```

## Android system/privileged service 集成约束

Android 量产集成说明位于
`docs/CENTRAL_BRAIN_ANDROID_SYSTEM_SERVICE_INTEGRATION.md`，覆盖 DEL-001、
DEL-003、DEL-004、XSC-001、APP-004、XSC-002、XSC-003、XSC-005、
XSC-006、NV-P-002、NV-P-005、FW-U-007、FW-S-005、NV-G-005。

该文档将当前 debug APK 内置 Binder sample 与目标 AAOS system/privileged
service 区分开：当前 sample 只验证 App -> Binder -> AI SDK/Uni Info Bus/SOA 路径；
目标集成需要平台方提供签名、priv-app 白名单、SELinux domain、service context
和上游 native gateway/system service 形态。Binder caller identity 必须进入
Runtime & Governance Policy 输入，不能替代 Policy 检查。

验证命令：

```bash
bash tools/check_central_brain_android_system_service_docs.sh
```

## 当前限制

- Android Binder sample、Linux IPC daemon 和 Linux gRPC/RPC JSON contract sample 允许路径仍代理到同进程 REST prototype gateway；`/governance/precheck`、`/governance/backend-contract`、`/governance/migration-check`、`/governance/deployment-plan`、`/bindings/readiness`、`/delivery/readiness`、`/prototype/readiness`、Linux governance daemon 和 reusable governance client 是显式 contract/共享 socket 样例，不是共享量产治理 daemon 或量产 transport；Linux governance daemon 现在可直接暴露 runtime/audit diagnostics，但仍不代表多进程量产治理后端；Linux IPC/gRPC sample 已对 SOA 调用增加 shared governance client precheck + local fallback，偏差记录见 DEV-001、DEV-013。
- Android system/privileged service 当前只有集成约束文档，没有 framework patch、priv-app 签名配置或 sepolicy，风险记录见 ISSUE-013。
- Linux systemd unit 与 package profile 是带最小 hardening 约束的部署样例，不等同量产包管理、LSM 策略或安全认证基线。
- 当前审计可选 JSONL 持久化并恢复最近 50 条；仍不是量产审计后端，偏差记录见 DEV-006。
- 当前没有真实 Driver/HAL/NPU/Vehicle bus 接入；`/uib/events/subscriptions`、`/uib/events/subscriptions/request`、`/uib/events/subscriptions/cancel` 和 `/uib/events/subscriptions/transport-readiness` 只是 Event subscription lifecycle 与 transport readiness contract，不持久化 subscription，不选择 transport，不注册 callback/watch，不启动 broker、SSE/WebSocket、DDS runtime 或高频数据面；`/hardware/interfaces` 只是 empty-interface registry，`/vehicle/signals` 只是 read-only VSS-style catalog，`/vehicle/signals/activation` 只是 read-bridge activation criteria，`/vehicle/signals/validation` 只是 read-bridge validation envelope，不访问 HAL、device node、vendor SDK、shared memory、VHAL、SocketCAN、DBC/ARXML、真实车辆总线或虚拟化层，偏差记录见 DEV-004、DEV-005、DEV-007、DEV-014、DEV-016。
