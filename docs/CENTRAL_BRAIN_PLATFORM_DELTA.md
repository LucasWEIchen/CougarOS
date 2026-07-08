# Android/Linux 平台差异说明

版本：0.1
日期：2026-07-08

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

硬件接口激活前 owner/ABI/smoke 门禁按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/activation-checklist`、Android Binder
`getHardwareInterfaceActivationChecklistJson`、Linux CLI
`hardware-interface-activation-checklist`、Linux IPC
`hardware.interfaces.activation.checklist` 与 Linux gRPC/RPC
`GetHardwareInterfaceActivationChecklist` 查询。该视图只暴露 `HW-ACT-001..008`
门禁、owner decision shape、target hardware smoke evidence shape 和 no-hardware-access
约束，固定 `activation_allowed=false`、`hardware_accessed=false`、
`driver_development_triggered=false` 和 `virtualization_development_triggered=false`，
不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service。

硬件接口 owner 决策状态按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-status`、Android Binder
`getHardwareInterfaceOwnerDecisionStatusJson`、Linux CLI
`hardware-interface-owner-decision-status`、Linux IPC
`hardware.interfaces.owner.decision.status` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionStatus` 查询。该视图只暴露 `HW-ODS-001..008`
的 target owner、Android ABI owner、Linux ABI owner、Driver/HAL gap owner、
Safety/Policy owner、target smoke evidence owner、rollback/fault semantics owner
和 no-hardware-access-in-prototype 状态，固定 `activation_allowed=false`、
`hardware_accessed=false`、`driver_development_triggered=false` 和
`virtualization_development_triggered=false`，不分配量产 owner，不关闭 activation gate，
不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service。

硬件接口 owner evidence intake 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`POST /hardware/interfaces/owner-decision-evidence`、Android Binder
`submitHardwareInterfaceOwnerDecisionEvidenceJson`、Linux CLI
`hardware-interface-owner-decision-evidence`、Linux IPC
`hardware.interfaces.owner.decision.evidence` 与 Linux gRPC/RPC
`SubmitHardwareInterfaceOwnerDecisionEvidence` 查询。该视图只校验 target interface、
target gate、evidence reference shape、reviewer identity 和 Runtime & Governance policy/audit
check，并固定 `owner_decision_evidence_persisted=false`、`review_queue_updated=false`、
`owner_assigned=false`、`gate_state_changed=false`、`gates_closed=false`、
`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`
和 `virtualization_development_triggered=false`；不创建 durable evidence store，不分配量产 owner，
不关闭 activation gate，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service。

硬件接口 owner evidence status 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/status`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceStatusJson`、Linux CLI
`hardware-interface-owner-decision-evidence-status`、Linux IPC
`hardware.interfaces.owner.decision.evidence.status` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceStatus` 查询。该视图只报告 evidence store、
review workflow、gate closure authority、target smoke evidence rules 和 zero persisted submissions
状态，并固定 `evidence_store_active=false`、`review_workflow_active=false`、
`persisted_submission_count=0`、`pending_review_count=0`、`owner_assigned=false`、
`gates_closed=false`、`activation_allowed=false`、`hardware_accessed=false`、
`driver_development_triggered=false` 和 `virtualization_development_triggered=false`；不读取 evidence store，
不创建 review queue，不分配 owner，不关闭 gate，不激活硬件，不触发 Driver/HAL。

硬件接口 owner evidence retention checklist 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/retention-checklist`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceRetentionChecklistJson`、Linux CLI
`hardware-interface-owner-decision-evidence-retention-checklist`、Linux IPC
`hardware.interfaces.owner.decision.evidence.retention.checklist` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceRetentionChecklist` 查询。该视图只报告 durable
evidence store owner、evidence URI rules、retention policy owner、review workflow owner、
gate closure authority、delete/export semantics、rollback/fault closure evidence 和
`HW-OER-001..008` 门禁仍待确认；固定 `owner_decision_complete=false`、
`retention_policy_confirmed=false`、`evidence_uri_rules_confirmed=false`、
`delete_workflow_active=false`、`export_workflow_active=false`、`gate_closure_allowed=false`、
`gates_closed=false`、`activation_allowed=false`、`hardware_accessed=false`、
`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。
它不创建 durable evidence store，不读取或 dereference evidence URI，不创建 review queue、
delete/export workflow，不分配 owner，不关闭 gate，不激活硬件，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence replacement trigger checklist 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklistJson`、Linux CLI
`hardware-interface-owner-decision-evidence-replacement-trigger-checklist`、Linux IPC
`hardware.interfaces.owner.decision.evidence.replacement.trigger.checklist` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklist` 查询。该视图只报告 replacement
target、adapter readiness criteria、Driver/HAL gap closure evidence、Android/Linux ABI replacement parity、
rollback-to-empty-interface plan、Safety/Policy replacement review、smoke harness replacement evidence 和
`HW-OET-001..008` 门禁仍待确认；固定 `replacement_policy_confirmed=false`、
`replacement_allowed=false`、`adapter_activation_allowed=false`、`gate_closure_allowed=false`、
`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和
`virtualization_development_triggered=false`。它不替换 adapter，不激活 adapter，不关闭 gate，
不创建 durable evidence store，不读取或 dereference evidence URI，不创建 review queue，
不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence selected-adapter readiness checklist 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklistJson`、Linux CLI
`hardware-interface-owner-decision-evidence-selected-adapter-readiness-checklist`、Linux IPC
`hardware.interfaces.owner.decision.evidence.selected.adapter.readiness.checklist` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklist` 查询。该视图只报告 selected
adapter owner、adapter interface contract、Driver/HAL gap evidence、Android/Linux binding parity、
Safety/Policy fault model、smoke harness plan、rollback-to-empty-interface review 和 `HW-OEA-001..008`
门禁仍待确认；固定 `adapter_candidate_recorded=false`、`adapter_load_allowed=false`、
`adapter_activation_allowed=false`、`hardware_access_allowed=false`、`gate_closure_allowed=false`、
`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和
`virtualization_development_triggered=false`。它不选择 adapter，不加载 adapter，不激活 adapter，不关闭 gate，
不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence adapter-load blocker rollup 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollupJson`、Linux CLI
`hardware-interface-owner-decision-evidence-adapter-load-blocker-rollup`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.blocker.rollup` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollup` 查询。该视图只聚合 activation checklist、
owner decision status、owner evidence status、retention checklist、replacement trigger checklist 和 selected-adapter
readiness checklist 中仍阻止 adapter load 的 `HW-ALB-001..008` 门禁；固定
`adapter_load_ready=false`、`all_blockers_cleared=false`、`adapter_load_allowed=false`、
`adapter_activation_allowed=false`、`hardware_access_allowed=false`、`gate_closure_allowed=false`、
`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和
`virtualization_development_triggered=false`。它不选择 adapter，不加载 adapter，不替换 adapter，不激活 adapter，
不关闭 gate，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence adapter-load dry-run 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`POST /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run`、Android Binder
`dryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadJson`、Linux CLI
`hardware-interface-owner-decision-evidence-adapter-load-dry-run`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.dry.run` 与 Linux gRPC/RPC
`DryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoad` 调用。该请求只校验 selected interface、selected adapter、
adapter version、requested_by 和 evidence refs 的 envelope，并强制返回
`adapter_load_dry_run_state=rejected_blocked_contract_only`；固定 `adapter_load_allowed=false`、
`adapter_activation_allowed=false`、`hardware_access_allowed=false`、`gate_closure_allowed=false`、
`evidence_persisted=false`、`review_queue_updated=false`、`hardware_accessed=false`、
`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。它不选择 adapter，不加载 adapter，
不替换 adapter，不激活 adapter，不关闭 gate，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，
不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence adapter-load dry-run status 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatusJson`、Linux CLI
`hardware-interface-owner-decision-evidence-adapter-load-dry-run-status`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.dry.run.status` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatus` 调用。该 status 只报告 no-store counters 和
last-result shape，固定 `last_result_available=false`、`persisted_dry_run_count=0`、
`pending_review_count=0`、`review_queue_updated=false`、`evidence_persisted=false`、
`adapter_load_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和
`virtualization_development_triggered=false`。它不保存请求或结果，不创建 evidence store，不创建 review queue，
不选择 adapter，不加载 adapter，不激活 adapter，不关闭 gate，不打开 device node，不调用 HAL/vendor SDK，
不分配 shared memory，不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence adapter-load dry-run audit consistency 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/audit-consistency`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistencyJson`、Linux CLI
`hardware-interface-owner-decision-evidence-adapter-load-dry-run-audit-consistency`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.dry.run.audit.consistency` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistency` 调用。该 consistency view 只比较
dry-run、dry-run/status、adapter-load blocker rollup 和 gate family，固定 `consistency_passed=true`、
`no_store_consistent=true`、`blocker_rollup_consistent=true`、`dry_run_rejection_consistent=true`、
`adapter_load_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和
`virtualization_development_triggered=false`。它不调用 dry-run POST，不保存请求或结果，不创建 evidence store，
不创建 review queue，不选择 adapter，不加载 adapter，不激活 adapter，不关闭 gate，不打开 device node，
不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence adapter-load approval authority checklist 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklistJson`、Android Console `HW Approve`、
Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-authority-checklist`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.approval.authority.checklist` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklist` 调用。该 checklist 只报告
approval authority、approval policy、signature/RBAC、durable evidence workflow、target smoke/rollback/fault evidence
和 no-load parity，固定 `approval_authority_assigned=false`、`approval_policy_confirmed=false`、
`approval_record_persisted=false`、`adapter_load_allowed=false`、`hardware_accessed=false`、
`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。它不调用 dry-run POST，
不持久化 approval，不创建 evidence store，不创建 review queue，不选择 adapter，不加载 adapter，
不激活 adapter，不关闭 gate，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，
不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence adapter-load approval authority no-store status 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/status`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatusJson`、Android Console `HW ApStat`、
Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-authority-status`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.approval.authority.status` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatus` 调用。该 status 只报告
approval record、review queue、approval evidence store、gate closure 和 adapter load 仍未发生，固定
`approval_record_available=false`、`persisted_approval_record_count=0`、`pending_approval_review_count=0`、
`approval_review_queue_updated=false`、`approval_evidence_store_active=false`、`approval_decision_passed=false`、
`adapter_load_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和
`virtualization_development_triggered=false`。它不调用 dry-run POST，不持久化 approval record，
不创建 evidence store，不创建 review queue，不选择 adapter，不加载 adapter，不激活 adapter，
不关闭 gate，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，
不触发 Driver/HAL 或虚拟化层。

Event subscription cursor/replay storage 按 FW-U-003、NV-P-006、XSC-002、XSC-006 在
`GET /uib/events/subscriptions/cursor-replay-storage`、Android Binder
`getEventSubscriptionCursorReplayStorageJson`、Linux CLI
`event-subscription-cursor-replay-storage`、Linux IPC
`uib.events.subscriptions.cursor.replay.storage` 与 Linux gRPC/RPC
`GetEventSubscriptionCursorReplayStorage` 查询。该视图只暴露 cursor schema、ack shape、
replay window、retention/cleanup、Runtime & Governance audit binding 和 `EV-CRS-001..008`
门禁，不创建 cursor row，不建立 replay index，不持久化 subscription，不启动 broker、
callback/watch、SSE/WebSocket、DDS、高频数据面、Driver/HAL 或虚拟化层。

Event subscription backpressure/QoS evidence 按 FW-U-003、NV-P-006、XSC-002、XSC-005、XSC-006 在
`GET /uib/events/subscriptions/backpressure-qos-evidence`、Android Binder
`getEventSubscriptionBackpressureQosEvidenceJson`、Linux CLI
`event-subscription-backpressure-qos-evidence`、Linux IPC
`uib.events.subscriptions.backpressure.qos.evidence` 与 Linux gRPC/RPC
`GetEventSubscriptionBackpressureQosEvidence` 查询。该视图只暴露 overflow schema、per-caller
throttling、replay rate、ack timeout、Runtime & Governance QoS evidence、高频 transport
QoS mapping 和 `EV-QOS-001..008` 门禁，不激活事件 QoS，不发送 overflow，不启动 broker、
SSE/WebSocket、DDS、高频数据面、Driver/HAL 或虚拟化层。

Event subscription readiness rollup 按 FW-U-003、NV-P-006、XSC-002、XSC-005、XSC-006 在
`GET /uib/events/subscriptions/readiness-rollup`、Android Binder
`getEventSubscriptionReadinessRollupJson`、Linux CLI
`event-subscription-readiness-rollup`、Linux IPC
`uib.events.subscriptions.readiness.rollup` 与 Linux gRPC/RPC
`GetEventSubscriptionReadinessRollup` 查询。该视图只聚合 lifecycle、transport readiness、
owner decision matrix、activation checklist、callback/watch shape、cursor/replay storage 与
backpressure/QoS evidence 的 blocked gates 和 `EV-RU-001..006` blockers，不关闭 gate，
不激活 broker、cursor、callback/watch、DDS、高频数据面、Driver/HAL 或虚拟化层。

Event subscription activation evidence intake 按 FW-U-003、NV-P-006、XSC-002、XSC-005、XSC-006 在
`POST /uib/events/subscriptions/activation-evidence`、Android Binder
`submitEventSubscriptionActivationEvidenceJson`、Linux CLI
`event-subscription-activation-evidence`、Linux IPC
`uib.events.subscriptions.activation.evidence` 与 Linux gRPC/RPC
`SubmitEventSubscriptionActivationEvidence` 查询。该视图只校验 activation gate evidence
reference envelope、reviewer identity、Runtime & Governance policy/audit check 和
`EV-AE-001..008` 门禁，不持久化 evidence，不更新 review queue，不关闭 gate，
不允许 broker activation，不触发 Driver/HAL 或虚拟化层。

Event subscription activation evidence review status 按 FW-U-003、NV-P-006、XSC-002、XSC-005、XSC-006 在
`GET /uib/events/subscriptions/activation-evidence/status`、Android Binder
`getEventSubscriptionActivationEvidenceStatusJson`、Linux CLI
`event-subscription-activation-evidence-status`、Linux IPC
`uib.events.subscriptions.activation.evidence.status` 与 Linux gRPC/RPC
`GetEventSubscriptionActivationEvidenceStatus` 查询。该视图只报告 activation evidence
intake 后仍无 durable evidence store、review workflow、gate closure authority 或 retention
policy，并固定 `EV-AES-001..006`、`persisted_submission_count=0` 和
`pending_review_count=0`；不读取 evidence store，不创建 review queue，不关闭 gate，
不允许 broker activation，不触发 Driver/HAL 或虚拟化层。

Event subscription activation evidence retention checklist 按 FW-U-003、NV-P-006、XSC-002、XSC-005、XSC-006 在
`GET /uib/events/subscriptions/activation-evidence/retention-checklist`、Android Binder
`getEventSubscriptionActivationEvidenceRetentionChecklistJson`、Linux CLI
`event-subscription-activation-evidence-retention-checklist`、Linux IPC
`uib.events.subscriptions.activation.evidence.retention.checklist` 与 Linux gRPC/RPC
`GetEventSubscriptionActivationEvidenceRetentionChecklist` 查询。该视图只报告 durable
evidence store owner、URI rules、retention policy owner、review workflow owner、
gate closure authority、delete/export semantics 和 `EV-AER-001..008` 门禁仍待确认；
固定 `owner_decision_complete=false`、`retention_policy_confirmed=false`、
`evidence_uri_rules_confirmed=false`、`delete_workflow_active=false`、
`export_workflow_active=false`、`gates_closed=false` 和 `activation_allowed=false`。
它不创建 durable evidence store，不读取或 dereference evidence URI，不创建 review queue、
delete/export workflow，不关闭 readiness gate，不激活 broker，不触发 Driver/HAL 或虚拟化层。

Event subscription activation evidence decision status rollup 按 FW-U-003、NV-P-006、XSC-002、XSC-005、XSC-006 在
`GET /uib/events/subscriptions/activation-evidence/decision-status-rollup`、Android Binder
`getEventSubscriptionActivationEvidenceDecisionStatusRollupJson`、Linux CLI
`event-subscription-activation-evidence-decision-status-rollup`、Linux IPC
`uib.events.subscriptions.activation.evidence.decision.status.rollup` 与 Linux gRPC/RPC
`GetEventSubscriptionActivationEvidenceDecisionStatusRollup` 查询。该视图只汇总 readiness rollup、
activation evidence intake/status、retention checklist 和 `EV-AED-001..008` 决策门禁；
固定 `decision_status_consistent=true`、`decision_status_passed=false`、
`owner_decision_complete=false`、`activation_evidence_intake_called=false`、
`activation_evidence_persisted=false`、`review_queue_updated=false`、`gates_closed=false`
和 `activation_allowed=false`。它不调用 activation evidence POST，不创建 durable evidence store，
不读取 evidence URI，不创建 review queue 或 delete/export workflow，不关闭 readiness gate，
不激活 broker，不触发 Driver/HAL 或虚拟化层。

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

Uni Info Bus Event 订阅生命周期、transport readiness、owner decision matrix、activation checklist 与 callback/watch shape 契约按 FW-U-003、NV-P-006、XSC-002、XSC-006 在
`GET /uib/events/subscriptions`、`POST /uib/events/subscriptions/request`、`POST /uib/events/subscriptions/cancel`、`GET /uib/events/subscriptions/transport-readiness`、`GET /uib/events/subscriptions/decision-matrix`、`GET /uib/events/subscriptions/activation-checklist`、`GET /uib/events/subscriptions/callback-watch-shape`、Android Binder
`getEventSubscriptionsJson`/`requestEventSubscriptionJson`/`cancelEventSubscriptionJson`/`getEventSubscriptionTransportReadinessJson`/`getEventSubscriptionDecisionMatrixJson`/`getEventSubscriptionActivationChecklistJson`/`getEventSubscriptionCallbackWatchShapeJson`、Linux CLI
`event-subscriptions`/`event-subscribe-request`/`event-subscribe-cancel`/`event-subscription-transport-readiness`/`event-subscription-decision-matrix`/`event-subscription-activation-checklist`/`event-subscription-callback-watch-shape`、Linux IPC
`uib.events.subscriptions.get`/`uib.events.subscriptions.request`/`uib.events.subscriptions.cancel`/`uib.events.subscriptions.transport.readiness`/`uib.events.subscriptions.decision.matrix`/`uib.events.subscriptions.activation.checklist`/`uib.events.subscriptions.callback.watch.shape` 与 Linux gRPC/RPC
`GetEventSubscriptions`/`RequestEventSubscription`/`CancelEventSubscription`/`GetEventSubscriptionTransportReadiness`/`GetEventSubscriptionDecisionMatrix`/`GetEventSubscriptionActivationChecklist`/`GetEventSubscriptionCallbackWatchShape` 查询。该视图只暴露订阅 lifecycle、cursor/replay、filter、
QoS/backpressure、Runtime & Governance、request/cancel contract-only 命令、callback/watch transport readiness、owner decision matrix、activation evidence gates、callback/watch API shape 和 binding parity，不持久化 subscription，不选择 transport，不注册
callback/watch，不启动真实订阅 broker、SSE/WebSocket、DDS runtime 或高频数据面，也不触发 Driver/HAL 或虚拟化开发。

## 差异矩阵

| 维度 | Android 主开发路径 | Linux 同步交付路径 | 当前交付状态 | Req ID |
| --- | --- | --- | --- | --- |
| 应用入口 | Android Console APK 和后续 AI SDK client；Console 已通过 Binder 暴露 `planAgentTaskJson`、`executeAgentTaskJson`、`invokeSkillJson`、`queryMemoryJson`、`getEventSubscriptionsJson`、`requestEventSubscriptionJson`、`cancelEventSubscriptionJson`、`getEventSubscriptionTransportReadinessJson`、`getEventSubscriptionDecisionMatrixJson`、`getEventSubscriptionActivationChecklistJson`、`getEventSubscriptionCallbackWatchShapeJson`、`getEventSubscriptionCursorReplayStorageJson`、`getEventSubscriptionBackpressureQosEvidenceJson`、`getEventSubscriptionReadinessRollupJson`、`precheckGovernanceJson`、`getGovernanceDeploymentPlanJson`、`getBindingReadinessJson`、`getDeliveryReadinessJson`、`getPrototypeReadinessJson`、`getDriverHalGapsJson`、`getHardwareInterfacesJson`、`getHardwareInterfaceActivationChecklistJson`、`getHardwareInterfaceOwnerDecisionStatusJson`、`submitHardwareInterfaceOwnerDecisionEvidenceJson`、`getHardwareInterfaceOwnerDecisionEvidenceStatusJson`、`getHardwareInterfaceOwnerDecisionEvidenceRetentionChecklistJson`、`getHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklistJson`、`getHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklistJson`、`getVehicleSignalsJson`、`getVehicleSignalActivationJson`、`getVehicleSignalValidationJson` | CLI/client，无 UI 最低样例；CLI/IPC 暴露 `agent-plan`、`agent-execute`、`skill-invoke`、`memory-query`、`event-subscriptions`/`event-subscribe-request`/`event-subscribe-cancel`/`event-subscription-transport-readiness`/`event-subscription-decision-matrix`/`event-subscription-activation-checklist`/`event-subscription-callback-watch-shape`/`event-subscription-cursor-replay-storage`/`event-subscription-backpressure-qos-evidence`/`event-subscription-readiness-rollup`、`uib.events.subscriptions.get`/`uib.events.subscriptions.request`/`uib.events.subscriptions.cancel`/`uib.events.subscriptions.transport.readiness`/`uib.events.subscriptions.decision.matrix`/`uib.events.subscriptions.activation.checklist`/`uib.events.subscriptions.callback.watch.shape`/`uib.events.subscriptions.cursor.replay.storage`/`uib.events.subscriptions.backpressure.qos.evidence`/`uib.events.subscriptions.readiness.rollup`、`governance-precheck`、`governance-deployment-plan`、`binding-readiness`、`delivery-readiness`、`prototype-readiness`、`driver-gaps`、`hardware-interfaces`、`hardware-interface-activation-checklist`/`hardware.interfaces.activation.checklist`、`hardware-interface-owner-decision-status`/`hardware.interfaces.owner.decision.status`、`hardware-interface-owner-decision-evidence`/`hardware.interfaces.owner.decision.evidence`、`hardware-interface-owner-decision-evidence-status`/`hardware.interfaces.owner.decision.evidence.status`、`hardware-interface-owner-decision-evidence-retention-checklist`/`hardware.interfaces.owner.decision.evidence.retention.checklist`、`hardware-interface-owner-decision-evidence-replacement-trigger-checklist`/`hardware.interfaces.owner.decision.evidence.replacement.trigger.checklist`、`hardware-interface-owner-decision-evidence-selected-adapter-readiness-checklist`/`hardware.interfaces.owner.decision.evidence.selected.adapter.readiness.checklist`、`vehicle-signals`/`vehicle.signals.list`、`vehicle-signal-activation`/`vehicle.signals.activation.get`、`vehicle-signal-validation`/`vehicle.signals.validation.get` | Console + CLI 初版；AI SDK/Agent plan + execute/Skill/Memory contract mock；Event subscription lifecycle + transport readiness + owner decision matrix + activation checklist + callback/watch shape + cursor/replay storage + backpressure/QoS evidence + readiness rollup contract；Governance precheck、deployment plan、Protocol Binding readiness、Delivery readiness、Prototype readiness、Driver/HAL gap backlog、hardware empty-interface registry、hardware activation checklist、hardware owner decision status/evidence intake/status/retention-closure/replacement-trigger/selected-adapter readiness checklist、Vehicle/Body Signal catalog、read-bridge activation criteria 与 validation envelope 可见 | DEL-001, DEL-002, DEL-003, DEL-004, DEL-005, XSC-001, XSC-002, XSC-004, XSC-005, XSC-006, APP-004, FW-U-003, FW-U-006, NV-F-003, NV-F-004, NV-F-005, NV-P-006, HW-002, KH-003, KH-006, KH-007 |
| Uni Info Bus | App/client 调用 `/uib/*`，Binder sample 暴露 `getContextJson`、`getStateJson` | CLI 和 Unix socket IPC operation 映射 `uib.context.get`、`uib.state.get` | active prototype | XSC-002, FW-U-001, FW-U-002 |
| SOA 服务入口 | Binder sample 暴露服务目录和 invoke 方法，当前代理语义网关 | CLI、Unix socket IPC 和 systemd gateway sample | active prototype + Linux unit sample | XSC-003, FW-S-004, FW-S-005 |
| Runtime & Governance | 通过 `/policy/evaluate`、`/governance/precheck`、`/governance/backend-contract`、`/governance/migration-check`、`/governance/deployment-plan`、`/governance/runtime`、`/audit/recent` 验证；Console `Precheck` 按钮通过 Binder `precheckGovernanceJson` 直接触发只检查不调用路径；Binder `getGovernanceBackendContractJson`/`getGovernanceMigrationCheckJson`/`getGovernanceDeploymentPlanJson` 可查共享治理后端目标契约、替换 readiness 和部署计划 | 同一 contract；Linux CLI/IPC 提供 `governance-precheck`/`governance.precheck`、`governance-backend-contract`/`governance.backend.contract.get`、`governance-migration-check`/`governance.migration.check` 和 `governance-deployment-plan`/`governance.deployment.plan.get`；Linux IPC/gRPC 对 `soa.service.invoke` 优先通过 reusable shared governance client 调用 governance daemon precheck，不可用时回退本地 precheck；shared governance socket 可直接查询 `governance.runtime.get` 和 `audit.recent.get` | active prototype；`/governance/precheck` 默认不消费 QoS 且不 dispatch 服务；`/governance/backend-contract` 是目标契约；`/governance/migration-check` 是 readiness contract；`/governance/deployment-plan` 是部署形态 contract；Linux governance daemon/client 是共享 socket 样例，不是量产治理后端 | XSC-005, NV-G-001..007 |
| Protocol Binding | Binder/AIDL service stub sample；REST 只是 prototype binding；Binder contract 暴露 `/uib/events/subscriptions`、`/uib/events/subscriptions/request`、`/uib/events/subscriptions/cancel`、`/uib/events/subscriptions/transport-readiness`、`/uib/events/subscriptions/decision-matrix`、`/uib/events/subscriptions/activation-checklist`、`/uib/events/subscriptions/callback-watch-shape`、`/uib/events/subscriptions/cursor-replay-storage`、`/uib/events/subscriptions/backpressure-qos-evidence`、`/uib/events/subscriptions/readiness-rollup`、`/soa/contracts`、`/vehicle/signals`、`/vehicle/signals/activation`、`/vehicle/signals/validation`、shared governance backend target、migration readiness、deployment plan、binding readiness、delivery readiness 和 prototype readiness | Unix socket IPC active sample；gRPC/RPC JSON contract sample；REST prototype binding；systemd unit hardening check + package profile check；IPC/gRPC 均可查询 `/uib/events/subscriptions`、`/uib/events/subscriptions/request`、`/uib/events/subscriptions/cancel`、`/uib/events/subscriptions/transport-readiness`、`/uib/events/subscriptions/decision-matrix`、`/uib/events/subscriptions/activation-checklist`、`/uib/events/subscriptions/callback-watch-shape`、`/uib/events/subscriptions/cursor-replay-storage`、`/uib/events/subscriptions/backpressure-qos-evidence`、`/uib/events/subscriptions/readiness-rollup`、`/soa/contracts`、`/vehicle/signals`、`/vehicle/signals/activation`、`/vehicle/signals/validation`、backend contract、migration readiness、deployment plan、binding readiness、delivery readiness 和 prototype readiness | Android stub + Linux active samples；`/bindings/readiness` 汇总 active sample、planned binding、阻塞项和验证命令；`/delivery/readiness` 汇总 Android/Linux 交付样例、验证 bundle 和阻塞项；`/prototype/readiness` 汇总原型模块成熟度、偏差、问题和下一步候选增量；当前 gRPC/RPC sample 因环境无 `grpcio` 使用 JSON TCP wrapper | XSC-002, XSC-003, XSC-006, FW-U-003, NV-P-001, NV-P-002, NV-P-003, NV-P-004, NV-P-005, NV-P-006, DEL-001, DEL-002, DEL-003, DEL-004, DEL-005 |
| 服务部署 | Debug APK 内置 Binder sample；量产目标为 AAOS system/privileged service 约束 | `central-brain-backend.service` + `central-brain-governance.service` + `central-brain-linux-ipc.service` + `central-brain-linux-grpc.service` 样例，含 unit hardening check 与 `central-brain.package-profile.json` | Android system service integration note + Linux systemd sample + hardening/package profile check | DEL-001, DEL-002, DEL-003, DEL-004 |
| 权限模型 | Android app permission、Binder caller identity、signature permission、Runtime & Governance policy | Linux service user/group、Unix socket mode、Runtime & Governance policy | Android 权限/SELinux 假设文档化，未接入真实系统权限 | FW-U-007, FW-S-005, NV-G-005, DEL-004 |
| 日志与审计 | Android logcat + `/audit/recent`；可通过服务配置指定 `CENTRAL_BRAIN_AUDIT_LOG` | journald + `/audit/recent`；可指定 gateway JSONL audit log 路径、shared governance audit log、IPC fallback audit log | JSONL 持久化样例已可验证，量产仍需轮转/导出/权限加固 | XSC-005, NV-G-007, DEL-002, DEL-004 |
| Driver/HAL | Android HAL/AIDL/NDK/vendor SDK bridge，当前不新增驱动；Console `Driver Gaps`、`Hardware IF`、`HW Gate`、`HW Owner`、`HW Evidence`、`HW EvStatus`、`HW Retain`、`HW Replace`、`HW Adapter`、`Vehicle Signals`、`Signal Gate` 和 `Signal Check` 按钮通过 Binder `getDriverHalGapsJson`/`getHardwareInterfacesJson`/`getHardwareInterfaceActivationChecklistJson`/`getHardwareInterfaceOwnerDecisionStatusJson`/`submitHardwareInterfaceOwnerDecisionEvidenceJson`/`getHardwareInterfaceOwnerDecisionEvidenceStatusJson`/`getHardwareInterfaceOwnerDecisionEvidenceRetentionChecklistJson`/`getHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklistJson`/`getHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklistJson`/`getVehicleSignalsJson`/`getVehicleSignalActivationJson`/`getVehicleSignalValidationJson` 可查 gap backlog、空接口目录、硬件激活前门禁、硬件 owner 决策状态、硬件 owner evidence intake/status/retention-closure/replacement-trigger/selected-adapter readiness checklist、只读信号目录、读桥准入条件和读桥校验证据 | Linux device node/ioctl/sysfs/vendor lib，当前不新增驱动；CLI `driver-gaps`/`hardware-interfaces`/`hardware-interface-activation-checklist`/`hardware-interface-owner-decision-status`/`hardware-interface-owner-decision-evidence`/`hardware-interface-owner-decision-evidence-status`/`hardware-interface-owner-decision-evidence-retention-checklist`/`hardware-interface-owner-decision-evidence-replacement-trigger-checklist`/`hardware-interface-owner-decision-evidence-selected-adapter-readiness-checklist`/`vehicle-signals`/`vehicle-signal-activation`/`vehicle-signal-validation`、IPC `hardware.interfaces.get`/`hardware.interfaces.activation.checklist`/`hardware.interfaces.owner.decision.status`/`hardware.interfaces.owner.decision.evidence`/`hardware.interfaces.owner.decision.evidence.status`/`hardware.interfaces.owner.decision.evidence.retention.checklist`/`hardware.interfaces.owner.decision.evidence.replacement.trigger.checklist`/`hardware.interfaces.owner.decision.evidence.selected.adapter.readiness.checklist`/`vehicle.signals.list`/`vehicle.signals.activation.get`/`vehicle.signals.validation.get` 和 gRPC/RPC `GetHardwareInterfaces`/`GetHardwareInterfaceActivationChecklist`/`GetHardwareInterfaceOwnerDecisionStatus`/`SubmitHardwareInterfaceOwnerDecisionEvidence`/`GetHardwareInterfaceOwnerDecisionEvidenceStatus`/`GetHardwareInterfaceOwnerDecisionEvidenceRetentionChecklist`/`GetHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklist`/`GetHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklist`/`GetVehicleSignals`/`GetVehicleSignalActivation`/`GetVehicleSignalValidation` 可查 gap backlog、空接口目录、硬件激活前门禁、硬件 owner 决策状态、硬件 owner evidence intake/status/retention-closure/replacement-trigger/selected-adapter readiness checklist、只读信号目录、读桥准入条件与读桥校验证据 | 接口矩阵 + `/native/driver-gaps` contract + `/hardware/interfaces` empty-interface registry + `/hardware/interfaces/activation-checklist` activation checklist + `/hardware/interfaces/owner-decision-status` owner decision status + `/hardware/interfaces/owner-decision-evidence` owner evidence intake + `/hardware/interfaces/owner-decision-evidence/status` owner evidence status + `/hardware/interfaces/owner-decision-evidence/retention-checklist` owner evidence retention checklist + `/hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist` owner evidence replacement trigger checklist + `/hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist` owner evidence selected-adapter readiness checklist + `/vehicle/signals` read-only catalog + `/vehicle/signals/activation` activation criteria + `/vehicle/signals/validation` validation envelope | DEL-005, KH-003, KH-006, KH-007, HW-002, NV-F-003, NV-F-004, NV-F-005, XSC-004, XSC-006 |
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
- 当前没有真实 Driver/HAL/NPU/Vehicle bus 接入；`/uib/events/subscriptions`、`/uib/events/subscriptions/request`、`/uib/events/subscriptions/cancel`、`/uib/events/subscriptions/transport-readiness`、`/uib/events/subscriptions/decision-matrix`、`/uib/events/subscriptions/activation-checklist`、`/uib/events/subscriptions/callback-watch-shape`、`/uib/events/subscriptions/cursor-replay-storage`、`/uib/events/subscriptions/backpressure-qos-evidence`、`/uib/events/subscriptions/readiness-rollup`、`/uib/events/subscriptions/activation-evidence`、`/uib/events/subscriptions/activation-evidence/status`、`/uib/events/subscriptions/activation-evidence/retention-checklist` 和 `/uib/events/subscriptions/activation-evidence/decision-status-rollup` 只是 Event subscription lifecycle、transport readiness、owner decision matrix、activation checklist、callback/watch shape、cursor/replay storage、backpressure/QoS evidence、readiness blocker rollup、activation evidence intake、review status、retention checklist 与 decision status rollup contract，不持久化 subscription/evidence，不调用 activation evidence POST，不读取 evidence store，不创建或更新 review queue，不创建 delete/export workflow，不关闭 gate，不选择 transport，不分配量产 owner，不注册 callback/watch，不启动 broker、SSE/WebSocket、DDS runtime 或高频数据面；`/hardware/interfaces` 只是 empty-interface registry，`/hardware/interfaces/activation-checklist` 只是 hardware activation owner/ABI/smoke checklist，`/hardware/interfaces/owner-decision-status` 只是 hardware owner decision status rollup，`/hardware/interfaces/owner-decision-evidence` 只是 no-store hardware owner evidence reference intake，`/hardware/interfaces/owner-decision-evidence/status` 只是 no-store hardware owner evidence status rollup，`/hardware/interfaces/owner-decision-evidence/retention-checklist` 只是 no-store retention/closure checklist，`/hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist` 只是 no-store replacement trigger checklist，`/hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist` 只是 no-store selected-adapter readiness checklist，`/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist` 只是 no-store/no-load approval authority checklist，`/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/status` 只是 no-store/no-load approval authority status，固定 `replacement_allowed=false`、`adapter_candidate_recorded=false`、`approval_authority_assigned=false`、`approval_policy_confirmed=false`、`approval_record_available=false`、`approval_record_persisted=false`、`persisted_approval_record_count=0`、`approval_decision_passed=false`、`adapter_load_allowed=false`、`adapter_activation_allowed=false`、`delete_workflow_active=false`、`export_workflow_active=false`、`gate_closure_allowed=false`、`hardware_access_allowed=false` 和 `activation_allowed=false`，不选择 adapter，不加载 adapter，不替换 adapter，不激活 adapter，不持久化 approval，不创建 durable evidence store，不读取 evidence URI，不创建 review queue，不创建 delete/export workflow，不关闭 gate；`/vehicle/signals` 只是 read-only VSS-style catalog，`/vehicle/signals/activation` 只是 read-bridge activation criteria，`/vehicle/signals/validation` 只是 read-bridge validation envelope，不访问 HAL、device node、vendor SDK、shared memory、VHAL、SocketCAN、DBC/ARXML、真实车辆总线或虚拟化层，偏差记录见 DEV-004、DEV-005、DEV-007、DEV-014、DEV-016。
