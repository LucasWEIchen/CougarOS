# Android/Linux 座舱域交付目标

版本：0.1
日期：2026-07-08

## 交付对象

本项目交付对象是使用 Android 和 Linux 系统的座舱域软件工程师。交付物必须能帮助他们完成集成、调试、验证和二次开发，而不只是展示 Demo。

## 平台优先级

| 平台 | 优先级 | 交付定位 | 当前状态 |
| --- | --- | --- | --- |
| Android | 主路径 | App、SDK client、AIDL/Binder 设计、Android system/privileged service 集成约束、模拟器/设备验证 | Console 已绑定 Binder service sample，并可触发 `planAgentTaskJson`、`executeAgentTaskJson`、`invokeSkillJson`、`queryMemoryJson`、`getEventSubscriptionsJson`、`requestEventSubscriptionJson`、`cancelEventSubscriptionJson`、`getEventSubscriptionTransportReadinessJson`、`getEventSubscriptionDecisionMatrixJson`、`getEventSubscriptionActivationChecklistJson`、`getEventSubscriptionCallbackWatchShapeJson`、`getEventSubscriptionCursorReplayStorageJson`、`getEventSubscriptionBackpressureQosEvidenceJson`、`getEventSubscriptionReadinessRollupJson`、`submitEventSubscriptionActivationEvidenceJson`、`precheckGovernanceJson`、`getDriverHalGapsJson`、`getHardwareInterfacesJson`、`getHardwareInterfaceActivationChecklistJson`、`getHardwareInterfaceOwnerDecisionStatusJson`、`submitHardwareInterfaceOwnerDecisionEvidenceJson`、`getHardwareInterfaceOwnerDecisionEvidenceStatusJson`、`getHardwareInterfaceOwnerDecisionEvidenceRetentionChecklistJson`、`getHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklistJson`、`getHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklistJson`、`getPrototypeReadinessJson`、`getVehicleSignalsJson`、`getVehicleSignalActivationJson`、`getVehicleSignalValidationJson`；system service integration note 初版 |
| Linux | 同步交付 | CLI/client、daemon 形态、systemd/进程部署、IPC/REST/gRPC 集成、驱动接口说明 | CLI smoke 初版；Linux CLI 提供 `event-subscriptions`、`event-subscribe-request`、`event-subscribe-cancel`、`event-subscription-transport-readiness`、`event-subscription-decision-matrix`、`event-subscription-activation-checklist`、`event-subscription-callback-watch-shape`、`event-subscription-cursor-replay-storage`、`event-subscription-backpressure-qos-evidence`、`event-subscription-readiness-rollup`、`event-subscription-activation-evidence`、`driver-gaps`、`hardware-interfaces`、`hardware-interface-activation-checklist`、`hardware-interface-owner-decision-status`、`hardware-interface-owner-decision-evidence`、`hardware-interface-owner-decision-evidence-status`、`hardware-interface-owner-decision-evidence-retention-checklist`、`hardware-interface-owner-decision-evidence-replacement-trigger-checklist`、`hardware-interface-owner-decision-evidence-selected-adapter-readiness-checklist`、`prototype-readiness`、`vehicle-signals`、`vehicle-signal-activation` 和 `vehicle-signal-validation`；Unix socket IPC daemon/client active sample 已含 execute/Skill/Memory mock、`uib.events.subscriptions.get`、`uib.events.subscriptions.request`、`uib.events.subscriptions.cancel`、`uib.events.subscriptions.transport.readiness`、`uib.events.subscriptions.decision.matrix`、`uib.events.subscriptions.activation.checklist`、`uib.events.subscriptions.callback.watch.shape`、`uib.events.subscriptions.cursor.replay.storage`、`uib.events.subscriptions.backpressure.qos.evidence`、`uib.events.subscriptions.readiness.rollup`、`uib.events.subscriptions.activation.evidence`、`hardware.interfaces.activation.checklist`、`hardware.interfaces.owner.decision.status`、`hardware.interfaces.owner.decision.evidence`、`hardware.interfaces.owner.decision.evidence.status`、`hardware.interfaces.owner.decision.evidence.retention.checklist`、`hardware.interfaces.owner.decision.evidence.replacement.trigger.checklist`、`hardware.interfaces.owner.decision.evidence.selected.adapter.readiness.checklist`、`prototype.readiness.get`、`vehicle.signals.list`、`vehicle.signals.activation.get` 与 `vehicle.signals.validation.get`；gRPC/RPC JSON contract sample 已含 `GetEventSubscriptions`、`RequestEventSubscription`、`CancelEventSubscription`、`GetEventSubscriptionTransportReadiness`、`GetEventSubscriptionDecisionMatrix`、`GetEventSubscriptionActivationChecklist`、`GetEventSubscriptionCallbackWatchShape`、`GetEventSubscriptionCursorReplayStorage`、`GetEventSubscriptionBackpressureQosEvidence`、`GetEventSubscriptionReadinessRollup`、`SubmitEventSubscriptionActivationEvidence`、`GetHardwareInterfaceActivationChecklist`、`GetHardwareInterfaceOwnerDecisionStatus`、`SubmitHardwareInterfaceOwnerDecisionEvidence`、`GetHardwareInterfaceOwnerDecisionEvidenceStatus`、`GetHardwareInterfaceOwnerDecisionEvidenceRetentionChecklist`、`GetHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklist`、`GetHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklist`、`GetPrototypeReadiness`、`GetVehicleSignals`、`GetVehicleSignalActivation` 与 `GetVehicleSignalValidation`；systemd 部署样例初版 + hardening check + package profile check |

## 每个核心模块的交付形态

| 模块 | Req ID | Android 交付 | Linux 交付 | 备注 |
| --- | --- | --- | --- | --- |
| AI SDK | XSC-001 | Android Binder/AIDL `planAgentTaskJson`、`executeAgentTaskJson`、Skill/Memory contract sample + `/ai/sdk/capabilities` | Linux CLI/IPC `agent-plan`、`agent-execute`、`skill-invoke`、`memory-query` active sample + `/ai/sdk/capabilities` | 黄色小太阳，跨 SoC；当前是 facade/plan/execute/Skill/Memory contract mock，不是真实 SDK library |
| Uni Info Bus 语义接口 | XSC-002 | Android client + contract + Binder `getEventSubscriptionsJson`/`requestEventSubscriptionJson`/`cancelEventSubscriptionJson`/`getEventSubscriptionTransportReadinessJson`/`getEventSubscriptionDecisionMatrixJson`/`getEventSubscriptionActivationChecklistJson`/`getEventSubscriptionCallbackWatchShapeJson`/`getEventSubscriptionCursorReplayStorageJson`/`getEventSubscriptionBackpressureQosEvidenceJson`/`getEventSubscriptionReadinessRollupJson`/`submitEventSubscriptionActivationEvidenceJson`/`getUibExtensionsJson`/`getVehicleSignalActivationJson`/`getVehicleSignalValidationJson` | Linux client + contract + CLI/IPC/gRPC `event-subscriptions`/`event-subscribe-request`/`event-subscribe-cancel`/`event-subscription-transport-readiness`/`event-subscription-decision-matrix`/`event-subscription-activation-checklist`/`event-subscription-callback-watch-shape`/`event-subscription-cursor-replay-storage`/`event-subscription-backpressure-qos-evidence`/`event-subscription-readiness-rollup`/`event-subscription-activation-evidence`、`uib.events.subscriptions.get`/`uib.events.subscriptions.request`/`uib.events.subscriptions.cancel`/`uib.events.subscriptions.transport.readiness`/`uib.events.subscriptions.decision.matrix`/`uib.events.subscriptions.activation.checklist`/`uib.events.subscriptions.callback.watch.shape`/`uib.events.subscriptions.cursor.replay.storage`/`uib.events.subscriptions.backpressure.qos.evidence`/`uib.events.subscriptions.readiness.rollup`/`uib.events.subscriptions.activation.evidence`、`GetEventSubscriptions`/`RequestEventSubscription`/`CancelEventSubscription`/`GetEventSubscriptionTransportReadiness`/`GetEventSubscriptionDecisionMatrix`/`GetEventSubscriptionActivationChecklist`/`GetEventSubscriptionCallbackWatchShape`/`GetEventSubscriptionCursorReplayStorage`/`GetEventSubscriptionBackpressureQosEvidence`/`GetEventSubscriptionReadinessRollup`/`SubmitEventSubscriptionActivationEvidence` + `extensions`/`uib.extensions.get`/`GetUibExtensions` + `vehicle-signal-activation`/`vehicle.signals.activation.get`/`GetVehicleSignalActivation` + `vehicle-signal-validation`/`vehicle.signals.validation.get`/`GetVehicleSignalValidation` | `/uib/context`、`/uib/state`、`/uib/events/*`、`/uib/events/subscriptions`、`/uib/events/subscriptions/request`、`/uib/events/subscriptions/cancel`、`/uib/events/subscriptions/transport-readiness`、`/uib/events/subscriptions/decision-matrix`、`/uib/events/subscriptions/activation-checklist`、`/uib/events/subscriptions/callback-watch-shape`、`/uib/events/subscriptions/cursor-replay-storage`、`/uib/events/subscriptions/backpressure-qos-evidence`、`/uib/events/subscriptions/readiness-rollup`、`/uib/events/subscriptions/activation-evidence`、`/uib/extensions`、`/uib/actions/request`、`/vehicle/signals/activation`、`/vehicle/signals/validation` 初版；订阅为 contract-only，不启动 broker/DDS |
| SOA 服务入口 | XSC-003 | Android service/client + Binder `getServiceContractsJson` | Linux daemon/client + CLI/IPC/gRPC `service-contracts`/`soa.contracts.get`/`GetServiceContracts` | `/soa/services`、`/soa/contracts`、`/soa/invoke` 初版；contract 查询不 dispatch 服务 |
| AIOS Kernel | XSC-004 | Native service adapter + Agent execute/Skill/Memory boundary sample + Driver/HAL gap visibility + hardware empty-interface visibility + hardware activation checklist + hardware owner decision status + hardware owner evidence intake/status/retention-closure/replacement-trigger/selected-adapter readiness checklist + Vehicle Signal catalog/activation/validation visibility | Linux service adapter + Agent execute/Skill/Memory boundary sample + `driver-gaps`/`hardware-interfaces`/`hardware-interface-activation-checklist`/`hardware-interface-owner-decision-status`/`hardware-interface-owner-decision-evidence`/`hardware-interface-owner-decision-evidence-status`/`hardware-interface-owner-decision-evidence-retention-checklist`/`hardware-interface-owner-decision-evidence-replacement-trigger-checklist`/`hardware-interface-owner-decision-evidence-selected-adapter-readiness-checklist`/`vehicle-signals`/`vehicle-signal-activation`/`vehicle-signal-validation` CLI | `GET /native/adapters/detail`、`GET /native/driver-gaps`、`GET /hardware/interfaces`、`GET /hardware/interfaces/activation-checklist`、`GET /hardware/interfaces/owner-decision-status`、`POST /hardware/interfaces/owner-decision-evidence`、`GET /hardware/interfaces/owner-decision-evidence/status`、`GET /hardware/interfaces/owner-decision-evidence/retention-checklist`、`GET /hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist`、`GET /hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist`、`GET /vehicle/signals`、`GET /vehicle/signals/activation` 与 `GET /vehicle/signals/validation`；AIOS Kernel 真实 runtime 仍未实现 |
| Vehicle/Body Signal catalog | NV-F-004, NV-F-005 | Binder `getVehicleSignalsJson` + Console `Vehicle Signals` 调试入口 | Linux CLI/IPC/gRPC `vehicle-signals`/`vehicle.signals.list`/`GetVehicleSignals` | `GET /vehicle/signals` 只读 VSS-style catalog；不加载 DBC/ARXML，不连接 VHAL/SocketCAN/vendor gateway，不触发 Driver/HAL 开发 |
| Vehicle Signal read-bridge activation criteria | NV-F-003, NV-F-004, NV-F-005 | Binder `getVehicleSignalActivationJson` + Console `Signal Gate` 调试入口 | Linux CLI/IPC/gRPC `vehicle-signal-activation`/`vehicle.signals.activation.get`/`GetVehicleSignalActivation` | `GET /vehicle/signals/activation` 只读返回 DBC/ARXML、Android VHAL/vendor AIDL、Linux SocketCAN、vendor gateway/SOME-IP 准入门禁；不激活真实读桥，不触发 Driver/HAL 开发 |
| Vehicle Signal read-bridge validation envelope | NV-F-003, NV-F-004, NV-F-005 | Binder `getVehicleSignalValidationJson` + Console `Signal Check` 调试入口 | Linux CLI/IPC/gRPC `vehicle-signal-validation`/`vehicle.signals.validation.get`/`GetVehicleSignalValidation` | `GET /vehicle/signals/validation` 只读返回 schema source metadata、adapter owner、ABI owner、Android/Linux parity 和 DRV-GAP-002 evidence 门禁；不解析 DBC/ARXML，不激活真实读桥，不触发 Driver/HAL 开发 |
| Runtime & Governance | XSC-005 | Registry/Policy/Lifecycle/QoS integration + Console `Precheck` 调用 `precheckGovernanceJson` + Binder `getGovernanceBackendContractJson`/`getGovernanceMigrationCheckJson`/`getGovernanceDeploymentPlanJson` 目标契约、迁移检查与部署计划可见性 | daemon modules + JSONL audit persistence sample + QoS fixed-window sample + Linux shared governance daemon precheck/runtime/audit diagnostics + IPC/gRPC shared governance client precheck/runtime/audit direct diagnostic path + local/REST fallback + `governance-precheck` + `governance-backend-contract` + `governance-migration-check` + `governance-deployment-plan` | `/governance/runtime`、`/governance/precheck`、`/governance/backend-contract`、`/governance/migration-check`、`/governance/deployment-plan`、`/policy/evaluate`、`/audit/recent` active prototype；`CENTRAL_BRAIN_AUDIT_LOG` 可恢复最近审计；`/soa/invoke`、Linux governance daemon 与 Linux IPC/gRPC `soa.service.invoke` 执行 NV-G-004 QoS 检查；shared governance socket 可直接查询 runtime/audit，且 IPC/gRPC sample 优先使用该 direct path；`/governance/precheck` 默认只检查不消费 QoS；`/governance/backend-contract` 是目标契约，`/governance/migration-check` 是替换 readiness 检查，`/governance/deployment-plan` 是部署形态 contract，均不是量产治理后端 |
| Protocol Binding | XSC-006 | Console Binder client path + Binder/AIDL service stub sample + Android system/privileged service integration note，service 上游仍代理 REST prototype，含 Event 语义映射和 shared governance backend target/migration/deployment/binding readiness/delivery readiness contract | REST active prototype + Unix socket IPC daemon/client active sample with shared governance client precheck/runtime/audit direct diagnostics/backend contract/deployment/binding readiness/delivery readiness visibility + Linux gRPC/RPC JSON contract sample with same diagnostics + systemd sample + unit hardening check + package profile check，含 Event 语义映射；MQTT/SOME-IP/DDS 计划态 | `/bindings/detail` 返回 binding artifact、sample 状态和 Req ID；`/bindings/readiness` 返回 Android Binder/Linux IPC/Linux gRPC/REST/MQTT/SOME-IP/DDS readiness、阻塞项和验证命令；`/delivery/readiness` 汇总 Android/Linux 交付样例、验证 bundle、阻塞项和非目标边界；当前 gRPC/RPC sample 因环境无 `grpcio` 使用 JSON TCP wrapper；DDS 不在本轮实现 |
| Prototype Readiness | XSC-001..006, DEL-001..005 | Console `Prototype` 调用 Binder `getPrototypeReadinessJson` | Linux CLI/IPC/gRPC `prototype-readiness`/`prototype.readiness.get`/`GetPrototypeReadiness` | `GET /prototype/readiness` 汇总 Python 原型模块成熟度、Android/Linux 绑定可见性、偏差、问题、下一步候选增量和非目标边界；只读，不 dispatch SOA service，不访问硬件，不触发 Driver/HAL 或虚拟化开发 |
| Model Runtime Adapter | NV-F-011 | Android native/runtime bridge + NPU runtime interface contract | Linux runtime bridge + NPU runtime interface contract | NPU/GPU/Cloud 后端可替换；见 `CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md` |
| Driver/HAL interface | KH-003, KH-006, DEL-005 | Android HAL/AIDL/NDK interface docs + Console `Driver Gaps`/`Hardware IF`/`HW Gate`/`HW Owner`/`HW Evidence`/`HW EvStatus`/`HW Retain`/`HW Replace`/`HW Adapter` 调用 Binder `getDriverHalGapsJson`/`getHardwareInterfacesJson`/`getHardwareInterfaceActivationChecklistJson`/`getHardwareInterfaceOwnerDecisionStatusJson`/`submitHardwareInterfaceOwnerDecisionEvidenceJson`/`getHardwareInterfaceOwnerDecisionEvidenceStatusJson`/`getHardwareInterfaceOwnerDecisionEvidenceRetentionChecklistJson`/`getHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklistJson`/`getHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklistJson` | Linux device node/ioctl/sysfs/libs docs + CLI `driver-gaps`/`hardware-interfaces`/`hardware-interface-activation-checklist`/`hardware-interface-owner-decision-status`/`hardware-interface-owner-decision-evidence`/`hardware-interface-owner-decision-evidence-status`/`hardware-interface-owner-decision-evidence-retention-checklist`/`hardware-interface-owner-decision-evidence-replacement-trigger-checklist`/`hardware-interface-owner-decision-evidence-selected-adapter-readiness-checklist`、IPC `hardware.interfaces.owner.decision.evidence.retention.checklist`/`hardware.interfaces.owner.decision.evidence.replacement.trigger.checklist`/`hardware.interfaces.owner.decision.evidence.selected.adapter.readiness.checklist`、gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceRetentionChecklist`/`GetHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklist`/`GetHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklist` | 只在缺口处新增开发；NPU 检查点、Driver/HAL gap backlog、hardware empty-interface registry、hardware activation checklist、hardware owner decision status 和 hardware owner evidence intake/status/retention-closure/replacement-trigger/selected-adapter readiness checklist 已文档化/可查询 |
| Hypervisor/Safety constraints | HV-001, HV-002, HV-003 | Android domain、Binder identity、Safety State 和 Policy 集成假设 | Linux domain、service identity、IPC fallback 和 Safety State 集成假设 | 只记录接口约束和部署假设，不开发虚拟化 |

本轮新增 adapter-load blocker rollup 交付面：Android Console `HW Load` 调用 Binder `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollupJson`，REST 路径为 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup`；Linux CLI 为 `hardware-interface-owner-decision-evidence-adapter-load-blocker-rollup`，Linux IPC operation 为 `hardware.interfaces.owner.decision.evidence.adapter.load.blocker.rollup`，Linux gRPC/RPC 为 `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollup`。该交付面只报告 `HW-ALB-001..008` 阻塞汇总，固定 `adapter_load_allowed=false`、`adapter_activation_allowed=false`、`hardware_access_allowed=false` 和 `driver_development_triggered=false`，不加载 adapter、不激活硬件、不新增 Driver/HAL 或虚拟化层。

本轮新增 adapter-load dry-run 交付面：Android Console `HW DryRun` 调用 Binder `dryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadJson`，REST 路径为 `POST /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run`；Linux CLI 为 `hardware-interface-owner-decision-evidence-adapter-load-dry-run`，Linux IPC operation 为 `hardware.interfaces.owner.decision.evidence.adapter.load.dry.run`，Linux gRPC/RPC 为 `DryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoad`。该交付面只校验 `HW-ALD-001..008` dry-run 请求形状和 blocker rollup 绑定，并返回 `rejected_blocked_contract_only`，固定 `adapter_load_allowed=false`、`adapter_activation_allowed=false`、`hardware_access_allowed=false`、`evidence_persisted=false` 和 `driver_development_triggered=false`，不加载 adapter、不激活硬件、不新增 Driver/HAL 或虚拟化层。

本轮新增 adapter-load dry-run status 交付面：Android Console `HW DryState` 调用 Binder `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatusJson`，REST 路径为 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status`；Linux CLI 为 `hardware-interface-owner-decision-evidence-adapter-load-dry-run-status`，Linux IPC operation 为 `hardware.interfaces.owner.decision.evidence.adapter.load.dry.run.status`，Linux gRPC/RPC 为 `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatus`。该交付面只报告 `HW-ALS-001..008` no-store status/last-result shape，固定 `last_result_available=false`、`persisted_dry_run_count=0`、`pending_review_count=0`、`review_queue_updated=false`、`evidence_persisted=false`、`adapter_load_allowed=false` 和 `driver_development_triggered=false`，不保存请求或结果、不加载 adapter、不激活硬件、不新增 Driver/HAL 或虚拟化层。

本轮新增 adapter-load dry-run audit consistency 交付面：Android Console `HW DryAudit` 调用 Binder `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistencyJson`，REST 路径为 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/audit-consistency`；Linux CLI 为 `hardware-interface-owner-decision-evidence-adapter-load-dry-run-audit-consistency`，Linux IPC operation 为 `hardware.interfaces.owner.decision.evidence.adapter.load.dry.run.audit.consistency`，Linux gRPC/RPC 为 `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistency`。该交付面只报告 `HW-ALC-001..008` no-store audit consistency、dry-run/status/blocker rollup/gate family cross-check，固定 `consistency_passed=true`、`no_store_consistent=true`、`blocker_rollup_consistent=true`、`dry_run_rejection_consistent=true`、`adapter_load_allowed=false` 和 `driver_development_triggered=false`，不调用 dry-run POST、不保存请求或结果、不加载 adapter、不激活硬件、不新增 Driver/HAL 或虚拟化层。

本轮新增 adapter-load approval authority checklist 交付面：Android Console `HW Approve` 调用 Binder `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklistJson`，REST 路径为 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist`；Linux CLI 为 `hardware-interface-owner-decision-evidence-adapter-load-approval-authority-checklist`，Linux IPC operation 为 `hardware.interfaces.owner.decision.evidence.adapter.load.approval.authority.checklist`，Linux gRPC/RPC 为 `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklist`。该交付面只报告 `HW-ALA-001..008` approval authority、approval policy、signature/RBAC、durable evidence workflow、target smoke/rollback/fault evidence 和 no-load parity 决策状态，固定 `approval_authority_assigned=false`、`approval_policy_confirmed=false`、`approval_record_persisted=false`、`adapter_load_allowed=false` 和 `driver_development_triggered=false`，不持久化 approval、不关闭 gate、不加载 adapter、不激活硬件、不新增 Driver/HAL 或虚拟化层。

本轮新增 adapter-load approval authority no-store status 交付面：Android Console `HW ApStat` 调用 Binder `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatusJson`，REST 路径为 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/status`；Linux CLI 为 `hardware-interface-owner-decision-evidence-adapter-load-approval-authority-status`，Linux IPC operation 为 `hardware.interfaces.owner.decision.evidence.adapter.load.approval.authority.status`，Linux gRPC/RPC 为 `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatus`。该交付面只报告 `HW-AAS-001..008` approval record/status 的 no-store 状态，固定 `approval_record_available=false`、`persisted_approval_record_count=0`、`pending_approval_review_count=0`、`approval_decision_passed=false`、`adapter_load_allowed=false` 和 `driver_development_triggered=false`，不持久化 approval record、不创建 evidence store、不更新 review queue、不关闭 gate、不加载 adapter、不激活硬件、不新增 Driver/HAL 或虚拟化层。

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
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscriptions
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscribe-request
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscribe-cancel
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscription-transport-readiness
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscription-decision-matrix
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscription-activation-checklist
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscription-callback-watch-shape
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscription-readiness-rollup
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscription-activation-evidence
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscription-activation-evidence-status
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscription-activation-evidence-retention-checklist
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py event-subscription-activation-evidence-decision-status-rollup
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py extensions
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py audit
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py service-contracts
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py binding-detail
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py binding-readiness
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py delivery-readiness
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py prototype-readiness
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py native-adapters-detail
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py driver-gaps
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py hardware-interfaces
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py hardware-interface-activation-checklist
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py hardware-interface-owner-decision-status
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py hardware-interface-owner-decision-evidence
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py hardware-interface-owner-decision-evidence-status
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py hardware-interface-owner-decision-evidence-retention-checklist
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py hardware-interface-owner-decision-evidence-replacement-trigger-checklist
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py hardware-interface-owner-decision-evidence-selected-adapter-readiness-checklist
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py hardware-interface-owner-decision-evidence-adapter-load-blocker-rollup
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py hardware-interface-owner-decision-evidence-adapter-load-dry-run
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py hardware-interface-owner-decision-evidence-adapter-load-dry-run-status
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py hardware-interface-owner-decision-evidence-adapter-load-dry-run-audit-consistency
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py hardware-interface-owner-decision-evidence-adapter-load-approval-authority-checklist
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py hardware-interface-owner-decision-evidence-adapter-load-approval-authority-status
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py vehicle-signals
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py vehicle-signal-activation
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py vehicle-signal-validation
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py ai-sdk
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py agent-plan
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py agent-execute
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py skills
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py skill-invoke
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py memory-query
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py action-request
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py governance-precheck
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py governance-backend-contract
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py governance-migration-check
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 python3 central-brain/linux-cli/central_brain_cli.py governance-deployment-plan
bash tools/check_central_brain_binding_artifacts.sh
bash tools/check_central_brain_delivery_docs.sh
bash tools/check_central_brain_linux_package_profile.sh
bash tools/check_central_brain_virtualization_docs.sh
bash tools/smoke_central_brain_audit_persistence.sh
bash tools/smoke_central_brain_qos.sh
bash tools/smoke_central_brain_linux_ipc.sh
bash tools/smoke_central_brain_linux_grpc.sh
```

Linux IPC `infer-denied` 样例用于验证 Unix socket binding 在转发到 REST prototype gateway 前先执行 Runtime & Governance precheck；配置 `CENTRAL_BRAIN_GOVERNANCE_SOCKET` 时优先走共享 governance daemon，不可用时回退本地 precheck。`governance` 与 `audit` 样例用于验证 IPC/gRPC 对 `governance.runtime.get` 与 `audit.recent.get` 也优先走 shared governance socket direct diagnostic path，不可用时回退 REST；这些路径验证 XSC-005/NV-G-001/NV-G-007 的 Linux 同步可见性，且不 dispatch SOA service、Driver/HAL 或虚拟化层：

```bash
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py infer-denied
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py governance
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py audit
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscriptions
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscribe-request
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscribe-cancel
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-transport-readiness
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-decision-matrix
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-activation-checklist
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-callback-watch-shape
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-readiness-rollup
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-activation-evidence
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-activation-evidence-status
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-activation-evidence-retention-checklist
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-activation-evidence-decision-status-rollup
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py hardware-interfaces
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py hardware-interface-activation-checklist
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py hardware-interface-owner-decision-status
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py hardware-interface-owner-decision-evidence
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py hardware-interface-owner-decision-evidence-status
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py hardware-interface-owner-decision-evidence-retention-checklist
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py hardware-interface-owner-decision-evidence-replacement-trigger-checklist
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py hardware-interface-owner-decision-evidence-selected-adapter-readiness-checklist
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py hardware-interface-owner-decision-evidence-adapter-load-blocker-rollup
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py hardware-interface-owner-decision-evidence-adapter-load-dry-run
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py hardware-interface-owner-decision-evidence-adapter-load-dry-run-status
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py hardware-interface-owner-decision-evidence-adapter-load-dry-run-audit-consistency
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py hardware-interface-owner-decision-evidence-adapter-load-approval-authority-checklist
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py hardware-interface-owner-decision-evidence-adapter-load-approval-authority-status
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py prototype-readiness
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py vehicle-signals
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py vehicle-signal-activation
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py vehicle-signal-validation
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py governance
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py audit
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscriptions
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscribe-request
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscribe-cancel
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-transport-readiness
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-decision-matrix
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-activation-checklist
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-callback-watch-shape
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-readiness-rollup
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-activation-evidence
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-activation-evidence-status
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-activation-evidence-retention-checklist
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-activation-evidence-decision-status-rollup
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py hardware-interfaces
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py hardware-interface-activation-checklist
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py hardware-interface-owner-decision-status
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py hardware-interface-owner-decision-evidence
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py hardware-interface-owner-decision-evidence-status
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py hardware-interface-owner-decision-evidence-retention-checklist
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py hardware-interface-owner-decision-evidence-replacement-trigger-checklist
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py hardware-interface-owner-decision-evidence-selected-adapter-readiness-checklist
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py hardware-interface-owner-decision-evidence-adapter-load-blocker-rollup
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py hardware-interface-owner-decision-evidence-adapter-load-dry-run
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py hardware-interface-owner-decision-evidence-adapter-load-dry-run-status
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py hardware-interface-owner-decision-evidence-adapter-load-dry-run-audit-consistency
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py hardware-interface-owner-decision-evidence-adapter-load-approval-authority-checklist
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py hardware-interface-owner-decision-evidence-adapter-load-approval-authority-status
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py prototype-readiness
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py vehicle-signals
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py vehicle-signal-activation
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py vehicle-signal-validation
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

## Hardware Interface Activation Checklist 交付补充

HW-002/KH-003/KH-006/KH-007/DEL-005 的 hardware interface activation checklist contract 通过 `GET /hardware/interfaces/activation-checklist` 对 Android/Linux 同步可见。Android 主路径为 Binder `getHardwareInterfaceActivationChecklistJson` 与 Console `HW Gate`；Linux 同步路径为 CLI `hardware-interface-activation-checklist`、IPC `hardware.interfaces.activation.checklist` 和 gRPC/RPC `GetHardwareInterfaceActivationChecklist`。

该交付项只用于 review `HW-ACT-001..008`：target interface owner、Driver/HAL gap review、Android ABI、Linux ABI、Safety/Policy binding、smoke test harness、rollback/fault semantics 和 no-hardware-access 证据。它固定 `activation_allowed=false`、`owner_decision_complete=false`、`android_abi_confirmed=false`、`linux_abi_confirmed=false`、`driver_gap_review_complete=false`、`safety_policy_binding_confirmed=false`、`target_hardware_smoke_attached=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不访问车辆总线，不新增 Driver/HAL 或虚拟化层。

## Hardware Interface Owner Decision Status 交付补充

HW-002/KH-003/KH-006/KH-007/DEL-005 的 hardware interface owner decision status contract 通过 `GET /hardware/interfaces/owner-decision-status` 对 Android/Linux 同步可见。Android 主路径为 Binder `getHardwareInterfaceOwnerDecisionStatusJson` 与 Console `HW Owner`；Linux 同步路径为 CLI `hardware-interface-owner-decision-status`、IPC `hardware.interfaces.owner.decision.status` 和 gRPC/RPC `GetHardwareInterfaceOwnerDecisionStatus`。

该交付项只用于 review `HW-ODS-001..008`：target interface owner、Android ABI owner、Linux ABI owner、Driver/HAL gap owner、Safety/Policy owner、target smoke evidence owner、rollback/fault semantics owner 和 no-hardware-access-in-prototype。它固定 `owner_decision_status_active=true`、`all_required_owners_assigned=false`、`target_hardware_smoke_attached=false`、`rollback_fault_semantics_confirmed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；不分配量产 owner，不关闭 activation gate，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不访问车辆总线，不新增 Driver/HAL 或虚拟化层。

## Hardware Interface Owner Decision Evidence 交付补充

HW-002/KH-003/KH-006/KH-007/DEL-005 的 hardware interface owner decision evidence intake contract 通过 `POST /hardware/interfaces/owner-decision-evidence` 对 Android/Linux 同步可见。Android 主路径为 Binder `submitHardwareInterfaceOwnerDecisionEvidenceJson` 与 Console `HW Evidence`；Linux 同步路径为 CLI `hardware-interface-owner-decision-evidence`、IPC `hardware.interfaces.owner.decision.evidence` 和 gRPC/RPC `SubmitHardwareInterfaceOwnerDecisionEvidence`。

该交付项只用于 review `HW-ODE-001..008`：target interfaces、target gates、evidence reference shape、reviewer identity、Runtime & Governance policy check、evidence store owner、no-gate-auto-close claim 和 Android/Linux parity。它固定 `owner_decision_evidence_contract_active=true`、`owner_decision_evidence_persisted=false`、`review_queue_updated=false`、`owner_assigned=false`、`gate_state_changed=false`、`gates_closed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；不持久化 evidence，不分配量产 owner，不关闭 activation gate，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不访问车辆总线，不新增 Driver/HAL 或虚拟化层。

## Hardware Interface Owner Decision Evidence Status 交付补充

HW-002/KH-003/KH-006/KH-007/DEL-005 的 hardware interface owner decision evidence status contract 通过 `GET /hardware/interfaces/owner-decision-evidence/status` 对 Android/Linux 同步可见。Android 主路径为 Binder `getHardwareInterfaceOwnerDecisionEvidenceStatusJson` 与 Console `HW EvStatus`；Linux 同步路径为 CLI `hardware-interface-owner-decision-evidence-status`、IPC `hardware.interfaces.owner.decision.evidence.status` 和 gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceStatus`。

该交付项只用于 review `HW-OES-001..008`：evidence store owner、review workflow owner、gate closure authority、target smoke evidence rules、no persisted submissions、no review queue/gate closure、no hardware access 和 Android/Linux parity。它固定 `owner_decision_evidence_status_contract_active=true`、`evidence_store_active=false`、`review_workflow_active=false`、`persisted_submission_count=0`、`pending_review_count=0`、`review_queue_updated=false`、`owner_assigned=false`、`gate_state_changed=false`、`gates_closed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；不读取 evidence store，不创建 review queue，不分配量产 owner，不关闭 activation gate，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不访问车辆总线，不新增 Driver/HAL 或虚拟化层。

## Hardware Interface Owner Decision Evidence Retention Checklist 交付补充

HW-002/KH-003/KH-006/KH-007/DEL-005 的 hardware interface owner decision evidence retention checklist contract 通过 `GET /hardware/interfaces/owner-decision-evidence/retention-checklist` 对 Android/Linux 同步可见。Android 主路径为 Binder `getHardwareInterfaceOwnerDecisionEvidenceRetentionChecklistJson` 与 Console `HW Retain`；Linux 同步路径为 CLI `hardware-interface-owner-decision-evidence-retention-checklist`、IPC `hardware.interfaces.owner.decision.evidence.retention.checklist` 和 gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceRetentionChecklist`。

该交付项只用于 review `HW-OER-001..008`：durable evidence store owner、evidence URI rules、retention policy owner、review workflow owner、gate closure authority、delete/export semantics、rollback/fault closure evidence 和 Android/Linux retention-closure contract parity。它固定 `owner_decision_evidence_retention_checklist_active=true`、`owner_decision_complete=false`、`retention_policy_confirmed=false`、`evidence_uri_rules_confirmed=false`、`review_workflow_owner_confirmed=false`、`gate_closure_authority_confirmed=false`、`deletion_export_semantics_confirmed=false`、`rollback_fault_closure_confirmed=false`、`evidence_store_active=false`、`review_workflow_active=false`、`delete_workflow_active=false`、`export_workflow_active=false`、`gate_closure_allowed=false`、`gates_closed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；不创建 durable evidence store，不读取或 dereference evidence URI，不创建 review queue，不创建 delete/export workflow，不分配 owner，不关闭 gate，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不访问车辆总线，不新增 Driver/HAL 或虚拟化层。

## Hardware Interface Owner Decision Evidence Replacement Trigger Checklist 交付补充

HW-002/KH-003/KH-006/KH-007/DEL-005 的 hardware interface owner decision evidence replacement trigger checklist contract 通过 `GET /hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist` 对 Android/Linux 同步可见。Android 主路径为 Binder `getHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklistJson` 与 Console `HW Replace`；Linux 同步路径为 CLI `hardware-interface-owner-decision-evidence-replacement-trigger-checklist`、IPC `hardware.interfaces.owner.decision.evidence.replacement.trigger.checklist` 和 gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklist`。

该交付项只用于 review `HW-OET-001..008`：replacement target interface、adapter readiness criteria、Driver/HAL gap closure evidence、Android/Linux ABI replacement parity、rollback-to-empty-interface plan、Safety/Policy replacement review、smoke harness replacement evidence 和 no-auto-replacement contract parity。它固定 `owner_decision_evidence_replacement_trigger_checklist_active=true`、`replacement_policy_confirmed=false`、`replacement_target_selected=false`、`adapter_readiness_criteria_confirmed=false`、`driver_hal_gap_closure_evidence_confirmed=false`、`rollback_to_empty_interface_plan_confirmed=false`、`replacement_allowed=false`、`adapter_activation_allowed=false`、`gate_closure_allowed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；不替换 adapter，不激活 adapter，不关闭 gate，不创建 durable evidence store，不读取或 dereference evidence URI，不创建 review queue，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不访问车辆总线，不新增 Driver/HAL 或虚拟化层。

## Hardware Interface Owner Decision Evidence Selected Adapter Readiness Checklist 交付补充

HW-002/KH-003/KH-006/KH-007/DEL-005 的 hardware interface owner decision evidence selected-adapter readiness checklist contract 通过 `GET /hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist` 对 Android/Linux 同步可见。Android 主路径为 Binder `getHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklistJson` 与 Console `HW Adapter`；Linux 同步路径为 CLI `hardware-interface-owner-decision-evidence-selected-adapter-readiness-checklist`、IPC `hardware.interfaces.owner.decision.evidence.selected.adapter.readiness.checklist` 和 gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklist`。

该交付项只用于 review `HW-OEA-001..008`：selected adapter owner、adapter interface contract、Driver/HAL gap evidence、Android/Linux binding parity、Safety/Policy fault model、smoke harness plan、rollback-to-empty-interface review 和 no-adapter-load contract parity。它固定 `owner_decision_evidence_selected_adapter_readiness_checklist_active=true`、`adapter_candidate_recorded=false`、`adapter_owner_assigned=false`、`adapter_interface_contract_approved=false`、`driver_hal_gap_evidence_attached=false`、`android_linux_binding_parity_approved=false`、`safety_policy_fault_model_reviewed=false`、`smoke_harness_plan_attached=false`、`rollback_to_empty_interface_reviewed=false`、`adapter_load_allowed=false`、`adapter_activation_allowed=false`、`hardware_access_allowed=false`、`gate_closure_allowed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；不选择 adapter，不加载 adapter，不激活 adapter，不关闭 gate，不创建 durable evidence store，不读取或 dereference evidence URI，不创建 review queue，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不访问车辆总线，不新增 Driver/HAL 或虚拟化层。

## Event Subscription Cursor/Replay Storage 交付补充

FW-U-003/NV-P-006 的 cursor/replay storage contract 通过 `GET /uib/events/subscriptions/cursor-replay-storage` 对 Android/Linux 同步可见。Android 主路径为 Binder `getEventSubscriptionCursorReplayStorageJson` 与 Console `Sub Cursor`；Linux 同步路径为 CLI `event-subscription-cursor-replay-storage`、IPC `uib.events.subscriptions.cursor.replay.storage` 和 gRPC/RPC `GetEventSubscriptionCursorReplayStorage`。

该交付项只用于 review cursor schema、ack shape、replay window、retention/cleanup、Runtime & Governance audit binding 和 `EV-CRS-001..008` 门禁；不创建 cursor row，不建立 replay index，不持久化 subscription，不启动 broker、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL 或虚拟化层。

## Event Subscription Backpressure/QoS Evidence 交付补充

FW-U-003/NV-P-006 的 backpressure/QoS evidence contract 通过 `GET /uib/events/subscriptions/backpressure-qos-evidence` 对 Android/Linux 同步可见。Android 主路径为 Binder `getEventSubscriptionBackpressureQosEvidenceJson` 与 Console `Sub QoS`；Linux 同步路径为 CLI `event-subscription-backpressure-qos-evidence`、IPC `uib.events.subscriptions.backpressure.qos.evidence` 和 gRPC/RPC `GetEventSubscriptionBackpressureQosEvidence`。

该交付项只用于 review overflow schema、per-caller throttling、per-topic limit、replay rate、ack timeout、Runtime & Governance QoS evidence、高频 transport QoS mapping 和 `EV-QOS-001..008` 门禁；不激活事件 QoS，不发送 overflow，不启动 broker、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL 或虚拟化层。

## Event Subscription Readiness Rollup 交付补充

FW-U-003/NV-P-006 的 readiness rollup contract 通过 `GET /uib/events/subscriptions/readiness-rollup` 对 Android/Linux 同步可见。Android 主路径为 Binder `getEventSubscriptionReadinessRollupJson` 与 Console `Sub Ready`；Linux 同步路径为 CLI `event-subscription-readiness-rollup`、IPC `uib.events.subscriptions.readiness.rollup` 和 gRPC/RPC `GetEventSubscriptionReadinessRollup`。

该交付项只用于 review lifecycle、transport readiness、owner decision matrix、activation checklist、callback/watch shape、cursor/replay storage 与 backpressure/QoS evidence 的端到端阻塞汇总，以及 `EV-RU-001..006` activation blockers；它不关闭任何 gate，不激活 broker/cursor/callback/watch/DDS，不访问 Driver/HAL，不新增虚拟化层。

## Event Subscription Activation Evidence 交付补充

FW-U-003/NV-P-006 的 activation evidence intake contract 通过 `POST /uib/events/subscriptions/activation-evidence` 对 Android/Linux 同步可见。Android 主路径为 Binder `submitEventSubscriptionActivationEvidenceJson` 与 Console `Sub Evidence`；Linux 同步路径为 CLI `event-subscription-activation-evidence`、IPC `uib.events.subscriptions.activation.evidence` 和 gRPC/RPC `SubmitEventSubscriptionActivationEvidence`。

该交付项只用于 review activation gate evidence reference envelope、reviewer identity、Runtime & Governance policy/audit check 和 `EV-AE-001..008` 门禁；它不持久化 evidence，不更新 review queue，不关闭 readiness gate，不允许 broker activation，不访问 Driver/HAL，不新增虚拟化层。

## Event Subscription Activation Evidence Status 交付补充

FW-U-003/NV-P-006 的 activation evidence review status contract 通过 `GET /uib/events/subscriptions/activation-evidence/status` 对 Android/Linux 同步可见。Android 主路径为 Binder `getEventSubscriptionActivationEvidenceStatusJson` 与 Console `Sub Review`；Linux 同步路径为 CLI `event-subscription-activation-evidence-status`、IPC `uib.events.subscriptions.activation.evidence.status` 和 gRPC/RPC `GetEventSubscriptionActivationEvidenceStatus`。

该交付项只用于 review activation evidence intake 之后的 no-store/no-workflow 状态、owner 待定项、`EV-AES-001..006` 门禁、`persisted_submission_count=0` 和 `pending_review_count=0`；它不读取 evidence store，不创建 review queue，不关闭 readiness gate，不允许 broker activation，不访问 Driver/HAL，不新增虚拟化层。

## Event Subscription Activation Evidence Retention Checklist 交付补充

FW-U-003/NV-P-006 的 activation evidence retention checklist contract 通过 `GET /uib/events/subscriptions/activation-evidence/retention-checklist` 对 Android/Linux 同步可见。Android 主路径为 Binder `getEventSubscriptionActivationEvidenceRetentionChecklistJson` 与 Console `Sub Retain`；Linux 同步路径为 CLI `event-subscription-activation-evidence-retention-checklist`、IPC `uib.events.subscriptions.activation.evidence.retention.checklist` 和 gRPC/RPC `GetEventSubscriptionActivationEvidenceRetentionChecklist`。

- FW-U-003/NV-P-006 的 activation evidence decision status rollup contract 通过 `GET /uib/events/subscriptions/activation-evidence/decision-status-rollup` 对 Android/Linux 同步可见。Android 主路径为 Binder `getEventSubscriptionActivationEvidenceDecisionStatusRollupJson` 与 Console `Sub Decide`；Linux 同步路径为 CLI `event-subscription-activation-evidence-decision-status-rollup`、IPC `uib.events.subscriptions.activation.evidence.decision.status.rollup` 和 gRPC/RPC `GetEventSubscriptionActivationEvidenceDecisionStatusRollup`。

该交付项只用于 review durable evidence store owner、URI rules、retention policy owner、review workflow owner、gate closure authority、delete/export semantics 和 `EV-AER-001..008` 门禁；它不创建 evidence store，不读取或 dereference evidence URI，不创建 delete/export workflow，不创建 review queue，不关闭 readiness gate，不允许 broker activation，不访问 Driver/HAL，不新增虚拟化层。

当前 Android Console 主路径：

- 绑定 `CentralBrainGatewayBinderService`。
- 通过 `CentralBrainGatewayClient.getStateJson` 调用 Uni Info Bus State。
- 通过 `CentralBrainGatewayClient.getEventSubscriptionsJson` 查看 FW-U-003/NV-P-006 Event subscription lifecycle、cursor、backpressure、governance 和 no-broker/no-DDS 边界。
- 通过 `CentralBrainGatewayClient.requestEventSubscriptionJson` 验证 FW-U-003/NV-P-006 Event subscription request lifecycle command contract；该路径只返回 `validated_contract_only`、Policy/Audit 和 `subscription_persisted=false`，不注册 callback/watch，不启动 broker 或 DDS。
- 通过 `CentralBrainGatewayClient.cancelEventSubscriptionJson` 验证 FW-U-003/NV-P-006 Event subscription cancel lifecycle command contract；该路径只返回 `cancelled_contract_only` 或 rejected contract response，不联系真实 broker，不移除持久化状态。
- 通过 `CentralBrainGatewayClient.getEventSubscriptionTransportReadinessJson` 查看 FW-U-003/NV-P-006 Event subscription callback/watch transport readiness contract；该路径只返回 broker owner、cursor storage owner、Android callback、Linux watch、SSE/WebSocket/DDS candidates 和 `EV-TR-001..006` 门禁，不选择 transport，不注册 callback/watch，不启动 broker 或 DDS。
- 通过 `CentralBrainGatewayClient.getEventSubscriptionDecisionMatrixJson` 查看 FW-U-003/NV-P-006 Event subscription broker/cursor/backpressure owner decision matrix contract；该路径只返回 `EV-DM-001..007` 决策门禁，不分配量产 owner，不启动 broker、cursor store、callback/watch 或 DDS。
- 通过 `CentralBrainGatewayClient.getEventSubscriptionActivationChecklistJson` 查看 FW-U-003/NV-P-006 Event subscription broker activation prerequisite evidence checklist；该路径只返回 `EV-ACT-001..008` 激活前门禁和 `activation_allowed=false`，不启动 broker、cursor store、callback/watch、SSE/WebSocket、DDS、高频数据面、Driver/HAL 或虚拟化层。
- 通过 `CentralBrainGatewayClient.getEventSubscriptionCallbackWatchShapeJson` 查看 FW-U-003/NV-P-006 Event subscription Android callback/Linux watch API shape contract；该路径只返回 `EV-CW-001..008` shape 门禁、event/overflow/close envelope 和 `shape_confirmed=false`，不注册 callback，不启动 watch stream、broker、cursor store、SSE/WebSocket、DDS、高频数据面、Driver/HAL 或虚拟化层。
- 通过 `CentralBrainGatewayClient.getEventSubscriptionCursorReplayStorageJson` 查看 FW-U-003/NV-P-006 Event subscription cursor/replay storage contract；该路径只返回 `EV-CRS-001..008` storage 门禁、cursor schema、ack shape、replay window 和 `cursor_replay_storage_confirmed=false`，不创建 cursor row，不建立 replay index，不持久化 subscription，不启动 broker、callback/watch、SSE/WebSocket、DDS、高频数据面、Driver/HAL 或虚拟化层。
- 通过 `CentralBrainGatewayClient.getEventSubscriptionBackpressureQosEvidenceJson` 查看 FW-U-003/NV-P-006 Event subscription backpressure/QoS evidence contract；该路径只返回 `EV-QOS-001..008` 门禁、overflow schema、per-caller throttling、replay rate、ack timeout 和 `backpressure_qos_evidence_confirmed=false`，不激活事件 QoS，不发送 overflow，不启动 broker、callback/watch、SSE/WebSocket、DDS、高频数据面、Driver/HAL 或虚拟化层。
- 通过 `CentralBrainGatewayClient.getEventSubscriptionReadinessRollupJson` 查看 FW-U-003/NV-P-006 Event subscription readiness rollup contract；该路径只返回 `EV-RU-001..006` activation blockers、blocked gate summary 和 `readiness_rollup_confirmed=false`，不关闭 gate，不激活 broker、cursor store、callback/watch、SSE/WebSocket、DDS、高频数据面、Driver/HAL 或虚拟化层。
- 通过 `CentralBrainGatewayClient.submitEventSubscriptionActivationEvidenceJson` 提交 FW-U-003/NV-P-006 Event subscription activation evidence reference contract；该路径只返回 `EV-AE-001..008` intake 门禁、`activation_evidence_persisted=false`、`review_queue_updated=false`、`gate_state_changed=false`、`gates_closed=false` 和 `activation_allowed=false`，不持久化 evidence，不关闭 gate，不激活 broker、DDS、高频数据面、Driver/HAL 或虚拟化层。
- 通过 `CentralBrainGatewayClient.getEventSubscriptionActivationEvidenceStatusJson` 查看 FW-U-003/NV-P-006 Event subscription activation evidence review status contract；该路径只返回 `EV-AES-001..006` status 门禁、`evidence_store_active=false`、`review_workflow_active=false`、`persisted_submission_count=0`、`pending_review_count=0`、`gates_closed=false` 和 `activation_allowed=false`，不读取 evidence store，不创建 review queue，不关闭 gate，不激活 broker、DDS、高频数据面、Driver/HAL 或虚拟化层。
- 通过 `CentralBrainGatewayClient.getEventSubscriptionActivationEvidenceRetentionChecklistJson` 查看 FW-U-003/NV-P-006 Event subscription activation evidence retention checklist contract；该路径只返回 `EV-AER-001..008` retention 门禁、`owner_decision_complete=false`、`retention_policy_confirmed=false`、`evidence_uri_rules_confirmed=false`、`delete_workflow_active=false`、`export_workflow_active=false`、`gates_closed=false` 和 `activation_allowed=false`，不创建 durable evidence store，不读取 evidence URI，不创建 delete/export workflow，不关闭 gate，不激活 broker、DDS、高频数据面、Driver/HAL 或虚拟化层。
- 通过 `CentralBrainGatewayClient.getEventSubscriptionActivationEvidenceDecisionStatusRollupJson` 查看 FW-U-003/NV-P-006 Event subscription activation evidence decision status rollup contract；该路径只返回 `EV-AED-001..008` 决策门禁、`decision_status_consistent=true`、`decision_status_passed=false`、`owner_decision_complete=false`、`activation_evidence_intake_called=false`、`activation_evidence_persisted=false`、`review_queue_updated=false`、`gates_closed=false` 和 `activation_allowed=false`，不调用 activation evidence POST，不创建 durable evidence store，不读取 evidence URI，不创建 review queue 或 delete/export workflow，不关闭 gate，不激活 broker、DDS、高频数据面、Driver/HAL 或虚拟化层。
- 通过 `CentralBrainGatewayClient.getUibExtensionsJson` 查看 FW-U-008 扩展语义 contract、治理规则和 no-dispatch 边界。
- 通过 `CentralBrainGatewayClient.planAgentTaskJson` 调用 AI SDK/Agent task plan。
- 通过 `CentralBrainGatewayClient.executeAgentTaskJson` 验证 Agent execute contract mock，只返回 policy-checked dispatch 边界。
- 通过 `CentralBrainGatewayClient.invokeSkillJson` 验证 Skill/Tool contract mock，不运行真实 sandbox 或车身总线。
- 通过 `CentralBrainGatewayClient.queryMemoryJson` 验证本地 Memory query contract mock，不允许 cloud sync。
- 通过 `CentralBrainGatewayClient.precheckGovernanceJson` 验证 Runtime & Governance discovery、Policy、Lifecycle、QoS 的只检查不调用路径。
- 通过 `CentralBrainGatewayClient.getGovernanceBackendContractJson` 查看共享 Runtime & Governance 后端目标契约，确认 Binder/IPC/gRPC 未来替换时共用 `governance.precheck`、`governance.runtime.get`、`audit.recent.get` 操作边界。
- 通过 `CentralBrainGatewayClient.getGovernanceMigrationCheckJson` 查看生产共享治理后端替换 readiness，确认 SOA precheck、不复制 Policy/QoS、runtime/audit 只读诊断和非目标边界。
- 通过 `CentralBrainGatewayClient.getGovernanceDeploymentPlanJson` 查看共享 Runtime & Governance 后端部署计划，确认 Android system/privileged service、Linux daemon 和 true gRPC/RPC 的目标形态、身份输入和开放决策。
- 通过 `CentralBrainGatewayClient.getBindingReadinessJson` 查看 Android Binder、Linux IPC、Linux gRPC/RPC、REST、MQTT、SOME/IP、DDS 的 readiness、阻塞项、验证命令和非目标边界。
- 通过 `CentralBrainGatewayClient.getDeliveryReadinessJson` 查看 Android debug Console/Binder、Android system service note、Linux CLI/IPC/gRPC、Linux systemd/package profile、Driver/HAL gap backlog 和虚拟化约束的交付 readiness、验证 bundle、阻塞项和非目标边界。
- 通过 `CentralBrainGatewayClient.getPrototypeReadinessJson` 查看 Python 原型模块成熟度、Android/Linux 绑定可见性、开放偏差、开放问题、下一步候选增量和非目标边界。
- 通过 `CentralBrainGatewayClient.getVehicleSignalsJson` 查看 NV-F-004/NV-F-005 Vehicle/Body Signal 只读目录、ECU/Signal Adapter 边界、Driver/HAL gap 链接和 no-hardware/no-driver/no-virtualization 验收状态。
- 通过 `CentralBrainGatewayClient.getVehicleSignalActivationJson` 查看 NV-F-003/NV-F-004/NV-F-005 Vehicle Signal 读桥激活准入门禁、Android/Linux parity、Driver/HAL scope review 和 no-hardware/no-driver/no-virtualization 验收状态。
- 通过 `CentralBrainGatewayClient.getVehicleSignalValidationJson` 查看 NV-F-003/NV-F-004/NV-F-005 Vehicle Signal 读桥校验证据 envelope、schema source metadata、adapter owner、ABI owner、DRV-GAP-002 evidence 和 no-hardware/no-driver/no-virtualization 验收状态。
- 通过 `CentralBrainGatewayClient.getServiceContractsJson` 查看 SOA service contract、版本、Policy/Safety State、QoS、Lifecycle 和 no-dispatch 边界，确认 FW-S-004/NV-G-003 contract 可见性。
- 通过 `CentralBrainGatewayClient.getDriverHalGapsJson` 查看 KH-003/KH-006/DEL-005 的 Driver/HAL gap backlog；该路径只读，不触发任何驱动开发或 HAL 调用。
- 通过 `CentralBrainGatewayClient.getHardwareInterfacesJson` 查看 HW-002/KH-003/KH-006/KH-007/DEL-005 的硬件依赖空接口目录；该路径只读，返回 `hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。
- 通过 `CentralBrainGatewayClient.getHardwareInterfaceOwnerDecisionStatusJson` 查看 HW-002/KH-003/KH-006/KH-007/DEL-005 的硬件依赖 owner/ABI/Driver-HAL/Safety/smoke/rollback 未决状态；该路径只读，返回 `activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。
- 通过 `CentralBrainGatewayClient.submitHardwareInterfaceOwnerDecisionEvidenceJson` 提交 HW-002/KH-003/KH-006/KH-007/DEL-005 的硬件 owner evidence reference envelope；该路径只返回 `HW-ODE-001..008` intake 门禁，不持久化 evidence，不更新 review queue，不关闭 gate，不访问硬件，不触发 Driver/HAL 或虚拟化开发。
- 通过 `CentralBrainGatewayClient.getHardwareInterfaceOwnerDecisionEvidenceStatusJson` 查看 HW-002/KH-003/KH-006/KH-007/DEL-005 的硬件 owner evidence no-store status；该路径只返回 `HW-OES-001..008` status 门禁、`evidence_store_active=false`、`review_workflow_active=false`、`persisted_submission_count=0`、`pending_review_count=0`、`gates_closed=false` 和 `activation_allowed=false`，不读取 evidence store，不创建 review queue，不关闭 gate，不访问硬件，不触发 Driver/HAL 或虚拟化开发。
- 通过 `CentralBrainGatewayClient.getHardwareInterfaceOwnerDecisionEvidenceRetentionChecklistJson` 查看 HW-002/KH-003/KH-006/KH-007/DEL-005 的硬件 owner evidence retention/closure checklist；该路径只返回 `HW-OER-001..008` retention 门禁、`owner_decision_complete=false`、`retention_policy_confirmed=false`、`evidence_uri_rules_confirmed=false`、`delete_workflow_active=false`、`export_workflow_active=false`、`gate_closure_allowed=false`、`gates_closed=false` 和 `activation_allowed=false`，不创建 durable evidence store，不读取 evidence URI，不创建 review queue 或 delete/export workflow，不关闭 gate，不访问硬件，不触发 Driver/HAL 或虚拟化开发。
- 通过 `CentralBrainGatewayClient.getHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklistJson` 查看 HW-002/KH-003/KH-006/KH-007/DEL-005 的硬件 owner evidence replacement trigger checklist；该路径只返回 `HW-OET-001..008` replacement 门禁、`replacement_policy_confirmed=false`、`replacement_allowed=false`、`adapter_activation_allowed=false`、`gate_closure_allowed=false` 和 `activation_allowed=false`，不替换 adapter，不激活 adapter，不关闭 gate，不访问硬件，不触发 Driver/HAL 或虚拟化开发。
- 通过 `CentralBrainGatewayClient.getHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklistJson` 查看 HW-002/KH-003/KH-006/KH-007/DEL-005 的硬件 owner evidence selected-adapter readiness checklist；该路径只返回 `HW-OEA-001..008` readiness 门禁、`adapter_candidate_recorded=false`、`adapter_load_allowed=false`、`adapter_activation_allowed=false`、`hardware_access_allowed=false` 和 `gate_closure_allowed=false`，不选择 adapter，不加载 adapter，不激活 adapter，不关闭 gate，不访问硬件，不触发 Driver/HAL 或虚拟化开发。
- Binder service sample 内部仍以 REST prototype gateway 作为上游绑定，不代表量产 system service。

当前 Android binding service stub sample：

- `central-brain/bindings/android/aidl/com/centralbrain/binding/ICentralBrainGateway.aidl`
- `central-brain/bindings/android/java/com/centralbrain/binding/CentralBrainGatewayBinderService.java`
- `central-brain/bindings/android/java/com/centralbrain/binding/CentralBrainGatewayClient.java`
- `GET /bindings/detail`
- `GET /bindings/readiness`
- `GET /native/adapters/detail`
- `GET /native/driver-gaps`
- `GET /soa/contracts`
- `GET /uib/events/topics`
- `POST /uib/events/publish`
- `GET /uib/events/recent`
- `GET /uib/events/subscriptions`
- `POST /uib/events/subscriptions/request`
- `POST /uib/events/subscriptions/cancel`
- `GET /uib/events/subscriptions/transport-readiness`
- `GET /uib/events/subscriptions/decision-matrix`
- `GET /uib/events/subscriptions/activation-checklist`
- `GET /uib/events/subscriptions/callback-watch-shape`
- `GET /uib/events/subscriptions/cursor-replay-storage`
- `GET /uib/events/subscriptions/backpressure-qos-evidence`
- `GET /uib/events/subscriptions/readiness-rollup`
- `POST /uib/events/subscriptions/activation-evidence`
- `GET /uib/events/subscriptions/activation-evidence/status`
- `GET /uib/events/subscriptions/activation-evidence/retention-checklist`
- `GET /uib/events/subscriptions/activation-evidence/decision-status-rollup`
- `GET /uib/extensions`
- `GET /ai/sdk/capabilities`
- `POST /agent/plan`
- `POST /agent/execute`
- `GET /skills`
- `POST /skills/{skill_id}/invoke`
- `POST /memory/query`
- `POST /uib/actions/request`
- `POST /governance/precheck`
- `GET /governance/backend-contract`
- `GET /governance/migration-check`
- `GET /governance/deployment-plan`
- `GET /bindings/readiness`
- `GET /delivery/readiness`
- `GET /prototype/readiness`
- `GET /hardware/interfaces`
- `GET /hardware/interfaces/activation-checklist`
- `GET /hardware/interfaces/owner-decision-status`
- `POST /hardware/interfaces/owner-decision-evidence`
- `GET /hardware/interfaces/owner-decision-evidence/status`
- `GET /hardware/interfaces/owner-decision-evidence/retention-checklist`
- `GET /hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist`
- `GET /hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist`
- `GET /vehicle/signals`
- `GET /vehicle/signals/activation`
- `GET /vehicle/signals/validation`

当前 Android system/privileged service integration note：

- `docs/CENTRAL_BRAIN_ANDROID_SYSTEM_SERVICE_INTEGRATION.md`
- `bash tools/check_central_brain_android_system_service_docs.sh`
- 覆盖 DEL-001、DEL-003、DEL-004、XSC-002、XSC-003、XSC-005、XSC-006、NV-P-002、NV-P-005、FW-U-007、FW-S-005、NV-G-005。
- 本轮不新增 Android framework patch、priv-app 签名配置、SELinux policy、Driver/HAL、Safety Runtime 或虚拟化层代码。
