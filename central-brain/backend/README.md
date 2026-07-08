# Mock NPU Backend

`mock_npu_service.py` 是第一阶段后端，用标准库 HTTP server 模拟中央大脑 AI 基座。
`ai_sdk.py` 承载当前 AI SDK/Agent facade mock，用 intent/utterance 生成 policy-aware task graph，并提供 execute/Skill/Memory contract mock，覆盖 XSC-001、APP-004、NV-F-001、FW-U-006、FW-U-007。
`runtime_governance.py` 承载当前 Runtime & Governance 原型，包括服务注册、发现、Policy、Lifecycle、per-service fixed-window QoS 和可选 JSONL 审计持久化。
`protocol_bindings.py` 承载当前 Protocol Binding 注册表，包括 REST active prototype、Android Binder/AIDL service stub sample、带 shared SOA Runtime & Governance precheck/runtime/audit direct diagnostics + fallback 的 Linux IPC active sample、Linux gRPC/RPC JSON contract sample 和 MQTT/SOME-IP/DDS 计划态。
`delivery_readiness.py` 承载 Android/Linux delivery readiness contract，汇总交付样例、验证命令、阻塞项和非目标边界。
`prototype_readiness.py` 承载 Python prototype readiness contract，汇总模块成熟度、Android/Linux 绑定可见性、开放偏差、开放问题和下一步候选增量。
`native_adapters.py` 承载当前 Native adapter 注册表，包括 AIOS Kernel、SOA Service Adapter、Vehicle Signal Adapter、Model Runtime Adapter、Security/Policy Adapter 的 Android/Linux 交付边界，以及 Driver/HAL gap backlog。
`hardware_interfaces.py` 承载硬件依赖空接口注册表，列出 NPU、Vehicle bus、Camera/Audio/Sensors、Ethernet/SOME-IP/DDS/TSN、Shared memory/Safety Runtime 的 reserved methods、Android 主路径、Linux 同步路径和 no-hardware-access 边界。
`vehicle_signals.py` 承载 Vehicle/Body Signal 只读目录、读桥激活准入 contract 与读桥校验证据 envelope，列出 VSS-style signal catalog、ECU/Signal Adapter 边界、Android/Linux 绑定可见性、activation/validation gates 和 DRV-GAP-002 链接。

## 启动

```bash
bash tools/run_central_brain_backend.sh
```

默认监听：

```text
0.0.0.0:8787
```

## 接口

- `GET /health`
- `GET /services`
- `GET /uib/context`
- `GET /uib/state`
- `GET /uib/events/topics`
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
- `GET /soa/services`
- `GET /soa/contracts`
- `GET /governance/runtime`
- `POST /governance/precheck`
- `GET /governance/backend-contract`
- `GET /governance/migration-check`
- `GET /governance/deployment-plan`
- `GET /audit/recent`
- `GET /bindings`
- `GET /bindings/detail`
- `GET /bindings/readiness`
- `GET /delivery/readiness`
- `GET /prototype/readiness`
- `GET /native/adapters`
- `GET /native/adapters/detail`
- `GET /native/driver-gaps`
- `GET /hardware/interfaces`
- `GET /hardware/interfaces/activation-checklist`
- `GET /hardware/interfaces/owner-decision-status`
- `POST /hardware/interfaces/owner-decision-evidence`
- `GET /hardware/interfaces/owner-decision-evidence/status`
- `GET /hardware/interfaces/owner-decision-evidence/retention-checklist`
- `GET /hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist`
- `GET /hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist`
- `GET /hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup`
- `POST /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run`
- `GET /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status`
- `GET /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/audit-consistency`
- `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist`
- `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/status`
- `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/audit-consistency`
- `GET /vehicle/signals`
- `GET /vehicle/signals/activation`
- `GET /vehicle/signals/validation`
- `GET /ai/sdk/capabilities`
- `GET /skills`
- `GET /vehicle/state`
- `GET /npu/status`
- `POST /agent/plan`
- `POST /agent/execute`
- `POST /skills/{skill_id}/invoke`
- `POST /memory/query`
- `POST /soa/invoke`
- `POST /uib/events/publish`
- `POST /uib/actions/request`
- `POST /policy/evaluate`
- `POST /ai/infer`

旧的 `/context`、`/state`、`/actions/request`、`/service/invoke` 仍保留为兼容入口。Android 和 Linux 新样例优先使用 `/uib/*` 与 `/soa/*`，REST 在此阶段只作为 `NV-P-005` prototype binding。

## 环境变量

- `CENTRAL_BRAIN_PORT`：监听端口，默认 `8787`。
- `CENTRAL_BRAIN_NPU_VENDOR_ID`：用于模拟指定 PCIe vendor id。
- `CENTRAL_BRAIN_NPU_DEVICE`：用于标记真实或模拟 NPU 设备节点。
- `CENTRAL_BRAIN_AUDIT_LOG`：可选 JSONL 审计日志路径；设置后 `/audit/recent` 会在服务重启后恢复最近 50 条 SOA 审计记录，覆盖 XSC-005、NV-G-007、DEL-002。

当前服务只做 mock，不访问真实 NPU。`/agent/execute`、`/skills/{skill_id}/invoke` 和 `/memory/query` 只做 Policy/Safety State 检查、audit 记录和 contract 边界展示，不运行真实 Skill sandbox、Memory store、Model Runtime Adapter、Driver/HAL、车身总线或虚拟化层。

`GET /native/driver-gaps` 覆盖 KH-003、KH-006、KH-007、DEL-005，只返回 NPU、Vehicle bus、Camera/Audio/Sensors、Ethernet/SOME-IP/DDS/TSN、Shared memory/Safety Runtime 缺口、触发条件和 Android/Linux 目标接口；`summary.driver_development_triggered=false` 表示本轮没有新增真实 Driver/HAL 开发。

`GET /hardware/interfaces` 覆盖 XSC-004、XSC-006、HW-002、KH-001、KH-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005，只返回硬件依赖空接口、reserved methods、Android 主路径、Linux 同步路径和触发条件；`summary.hardware_accessed=false`、`summary.driver_development_triggered=false`、`summary.virtualization_development_triggered=false` 表示本轮没有访问真实硬件、没有新增 Driver/HAL、没有开发虚拟化层。

`GET /hardware/interfaces/activation-checklist` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005，只返回硬件接口激活前的 owner、Android ABI、Linux ABI、Driver/HAL gap review、Safety/Policy、smoke evidence 和 rollback/fault 语义门禁；Android Binder `getHardwareInterfaceActivationChecklistJson`、Linux CLI `hardware-interface-activation-checklist`、Linux IPC `hardware.interfaces.activation.checklist` 与 Linux gRPC/RPC `GetHardwareInterfaceActivationChecklist` 暴露同一视图。该接口明确 `activation_allowed=false`、`owner_decision_complete=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`，不打开 device node、不调用 HAL/vendor SDK、不分配共享内存、不访问车辆总线，也不开发虚拟化层。

`GET /hardware/interfaces/owner-decision-status` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005，只返回硬件接口 target owner、Android ABI owner、Linux ABI owner、Driver/HAL gap owner、Safety/Policy owner、target smoke evidence owner、rollback/fault semantics owner 和 no-hardware-access 状态；Android Binder `getHardwareInterfaceOwnerDecisionStatusJson`、Linux CLI `hardware-interface-owner-decision-status`、Linux IPC `hardware.interfaces.owner.decision.status` 与 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionStatus` 暴露同一视图。该接口明确 `all_required_owners_assigned=false`、`target_hardware_smoke_attached=false`、`rollback_fault_semantics_confirmed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`，不分配 owner、不关闭 gate、不访问硬件、不新增 Driver/HAL 或虚拟化开发。

`POST /hardware/interfaces/owner-decision-evidence` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005，只校验硬件 owner/smoke/ABI/Driver-HAL/Safety gate evidence reference envelope，不持久化 evidence；Android Binder `submitHardwareInterfaceOwnerDecisionEvidenceJson`、Linux CLI `hardware-interface-owner-decision-evidence`、Linux IPC `hardware.interfaces.owner.decision.evidence` 与 Linux gRPC/RPC `SubmitHardwareInterfaceOwnerDecisionEvidence` 暴露同一视图。该接口明确 `owner_decision_evidence_persisted=false`、`review_queue_updated=false`、`owner_assigned=false`、`gate_state_changed=false`、`gates_closed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`，不读取 evidence URI、不创建 review queue、不关闭 gate、不访问硬件、不新增 Driver/HAL 或虚拟化开发。

`GET /hardware/interfaces/owner-decision-evidence/status` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005，只返回硬件 owner evidence no-store status、evidence store/review workflow/gate closure authority 待定项和 zero persisted counters；Android Binder `getHardwareInterfaceOwnerDecisionEvidenceStatusJson`、Linux CLI `hardware-interface-owner-decision-evidence-status`、Linux IPC `hardware.interfaces.owner.decision.evidence.status` 与 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceStatus` 暴露同一视图。该接口明确 `evidence_store_active=false`、`review_workflow_active=false`、`persisted_submission_count=0`、`pending_review_count=0`、`owner_assigned=false`、`gates_closed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`，不读取 evidence store、不创建 review queue、不关闭 gate、不访问硬件、不新增 Driver/HAL 或虚拟化开发。

`GET /hardware/interfaces/owner-decision-evidence/retention-checklist` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005，只返回硬件 owner evidence retention/closure checklist、durable evidence store owner、evidence URI rules、retention policy owner、review workflow owner、gate closure authority、delete/export semantics、rollback/fault closure evidence 和 `HW-OER-001..008` 门禁；Android Binder `getHardwareInterfaceOwnerDecisionEvidenceRetentionChecklistJson`、Linux CLI `hardware-interface-owner-decision-evidence-retention-checklist`、Linux IPC `hardware.interfaces.owner.decision.evidence.retention.checklist` 与 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceRetentionChecklist` 暴露同一视图。该接口明确 `owner_decision_complete=false`、`retention_policy_confirmed=false`、`evidence_uri_rules_confirmed=false`、`delete_workflow_active=false`、`export_workflow_active=false`、`gate_closure_allowed=false`、`gates_closed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`，不创建 durable evidence store、不读取 evidence URI、不创建 review queue、不创建 delete/export workflow、不关闭 gate、不访问硬件、不新增 Driver/HAL 或虚拟化开发。

`GET /hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005，只返回硬件 owner evidence replacement trigger checklist、replacement target、adapter readiness criteria、Driver/HAL gap closure evidence、Android/Linux ABI replacement parity、rollback-to-empty-interface plan、Safety/Policy replacement review、smoke harness evidence 和 `HW-OET-001..008` 门禁；Android Binder `getHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklistJson`、Linux CLI `hardware-interface-owner-decision-evidence-replacement-trigger-checklist`、Linux IPC `hardware.interfaces.owner.decision.evidence.replacement.trigger.checklist` 与 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklist` 暴露同一视图。该接口明确 `replacement_policy_confirmed=false`、`replacement_allowed=false`、`adapter_activation_allowed=false`、`gate_closure_allowed=false`、`activation_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`，不替换 adapter、不激活 adapter、不关闭 gate、不访问硬件、不新增 Driver/HAL 或虚拟化开发。

`GET /hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005，只返回硬件 owner evidence selected-adapter readiness checklist、adapter owner、adapter interface contract、Driver/HAL gap evidence、Android/Linux binding parity、Safety/Policy fault model、smoke harness plan、rollback-to-empty-interface review 和 `HW-OEA-001..008` 门禁；Android Binder `getHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklistJson`、Linux CLI `hardware-interface-owner-decision-evidence-selected-adapter-readiness-checklist`、Linux IPC `hardware.interfaces.owner.decision.evidence.selected.adapter.readiness.checklist` 与 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklist` 暴露同一视图。该接口明确 `adapter_candidate_recorded=false`、`adapter_load_allowed=false`、`adapter_activation_allowed=false`、`hardware_access_allowed=false`、`gate_closure_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`，不选择 adapter、不加载 adapter、不激活 adapter、不关闭 gate、不访问硬件、不新增 Driver/HAL 或虚拟化开发。

`GET /hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005，只聚合 activation checklist、owner decision status、evidence status、retention checklist、replacement trigger checklist 和 selected-adapter readiness checklist 的未清阻塞项；Android Binder `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollupJson`、Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-blocker-rollup`、Linux IPC `hardware.interfaces.owner.decision.evidence.adapter.load.blocker.rollup` 与 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollup` 暴露同一视图。该接口固定 `HW-ALB-001..008`、`adapter_load_ready=false`、`all_blockers_cleared=false`、`adapter_load_allowed=false`、`adapter_activation_allowed=false`、`hardware_access_allowed=false`、`gate_closure_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`，不选择 adapter、不加载 adapter、不替换 adapter、不激活 adapter、不关闭 gate、不访问硬件、不新增 Driver/HAL 或虚拟化开发。

`POST /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005，只校验 adapter-load approval dry-run 请求形状并绑定 adapter-load blocker rollup；Android Binder `dryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadJson`、Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-dry-run`、Linux IPC `hardware.interfaces.owner.decision.evidence.adapter.load.dry.run` 与 Linux gRPC/RPC `DryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoad` 暴露同一拒绝路径。该接口固定 `HW-ALD-001..008`、`adapter_load_dry_run_state=rejected_blocked_contract_only`、`adapter_load_allowed=false`、`adapter_activation_allowed=false`、`hardware_access_allowed=false`、`gate_closure_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`，不选择 adapter、不加载 adapter、不激活 adapter、不关闭 gate、不访问硬件、不新增 Driver/HAL 或虚拟化开发。

`GET /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005，只报告 adapter-load dry-run no-store status、last-result shape 和 zero persisted counters；Android Binder `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatusJson`、Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-dry-run-status`、Linux IPC `hardware.interfaces.owner.decision.evidence.adapter.load.dry.run.status` 与 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatus` 暴露同一状态视图。该接口固定 `HW-ALS-001..008`、`last_result_available=false`、`persisted_dry_run_count=0`、`pending_review_count=0`、`review_queue_updated=false`、`evidence_persisted=false`、`adapter_load_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`，不保存请求或结果、不创建 review queue、不加载 adapter、不访问硬件、不新增 Driver/HAL 或虚拟化开发。

`GET /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/audit-consistency` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005，只报告 adapter-load dry-run、no-store status、blocker rollup 和 `HW-ALB`/`HW-ALD`/`HW-ALS`/`HW-ALC` gate families 的审计一致性；Android Binder `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistencyJson`、Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-dry-run-audit-consistency`、Linux IPC `hardware.interfaces.owner.decision.evidence.adapter.load.dry.run.audit.consistency` 与 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistency` 暴露同一只读视图。该接口固定 `HW-ALC-001..008`、`consistency_passed=true`、`no_store_consistent=true`、`blocker_rollup_consistent=true`、`dry_run_rejection_consistent=true`、`adapter_load_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`，不调用 dry-run POST、不保存请求或结果、不创建 review queue、不加载 adapter、不访问硬件、不新增 Driver/HAL 或虚拟化开发。

`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005，只报告 adapter-load 从 dry-run 进入真实 approval 前必须确认的 approval authority、policy、signature/RBAC、durable evidence workflow、target smoke/rollback/fault evidence 和 `HW-ALA-001..008` gate family；Android Binder `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklistJson`、Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-authority-checklist`、Linux IPC `hardware.interfaces.owner.decision.evidence.adapter.load.approval.authority.checklist` 与 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklist` 暴露同一只读视图。该接口固定 `approval_authority_assigned=false`、`approval_policy_confirmed=false`、`approval_signature_rules_confirmed=false`、`approval_rbac_confirmed=false`、`approval_workflow_active=false`、`approval_record_persisted=false`、`adapter_load_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`，不调用 dry-run POST、不持久化 approval record、不关闭 gate、不加载 adapter、不访问硬件、不新增 Driver/HAL 或虚拟化开发。

`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/status` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005，只报告 adapter-load approval authority checklist 之后的 no-store status、zero approval record counters、no review queue、no evidence store 和 `HW-AAS-001..008` gate family；Android Binder `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatusJson`、Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-authority-status`、Linux IPC `hardware.interfaces.owner.decision.evidence.adapter.load.approval.authority.status` 与 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatus` 暴露同一只读视图。该接口固定 `approval_record_available=false`、`persisted_approval_record_count=0`、`pending_approval_review_count=0`、`approval_review_queue_updated=false`、`approval_evidence_store_active=false`、`approval_decision_passed=false`、`approval_decisions_open=true`、`adapter_load_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`，不调用 dry-run POST、不持久化 approval record、不创建 evidence store、不更新 review queue、不关闭 gate、不加载 adapter、不访问硬件、不新增 Driver/HAL 或虚拟化开发。

`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/audit-consistency` 覆盖 XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005，只报告 approval checklist、approval authority no-store status、adapter-load dry-run audit consistency、adapter-load blocker rollup 和 `HW-AAC-001..008` gate family 的审计一致性；Android Binder `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistencyJson`、Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-authority-audit-consistency`、Linux IPC `hardware.interfaces.owner.decision.evidence.adapter.load.approval.authority.audit.consistency` 与 Linux gRPC/RPC `GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistency` 暴露同一只读视图。该接口固定 `consistency_passed=true`、`approval_status_no_store_consistent=true`、`approval_decisions_open_consistent=true`、`adapter_load_blocked_consistent=true`、`adapter_load_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`，不调用 dry-run POST、不持久化 approval record、不创建 evidence store、不更新 review queue、不关闭 gate、不加载 adapter、不访问硬件、不新增 Driver/HAL 或虚拟化开发。

`GET /vehicle/signals` 覆盖 XSC-002、XSC-004、XSC-006、NV-F-004、NV-F-005、NV-P-002、NV-P-003、DEL-001、DEL-002、DEL-005，只返回 Vehicle/Body Signal 只读 VSS-style catalog、访问级别、governance tag、ECU/Signal Adapter 边界和 DRV-GAP-002 链接；`summary.dbc_arxml_loaded=false`、`summary.real_vehicle_bus_connected=false`、`summary.hardware_accessed=false`、`summary.driver_development_triggered=false`、`summary.virtualization_development_triggered=false` 表示本轮没有加载 DBC/ARXML、没有连接真实车辆总线、没有访问硬件、没有新增 Driver/HAL、没有开发虚拟化层。

`GET /vehicle/signals/activation` 覆盖 XSC-002、XSC-004、XSC-006、NV-F-003、NV-F-004、NV-F-005、NV-P-001、NV-P-002、NV-P-003、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005，只返回 DBC/ARXML、Android VHAL/vendor AIDL、Linux SocketCAN、vendor gateway/SOME-IP 读桥激活准入条件和 `VS-ACT-001..005` 门禁；`summary.read_bridge_activated=false`、`summary.hardware_accessed=false`、`summary.driver_development_triggered=false`、`summary.virtualization_development_triggered=false` 表示本轮没有激活真实读桥、没有访问硬件、没有新增 Driver/HAL、没有开发虚拟化层。

`GET /vehicle/signals/validation` 覆盖 XSC-002、XSC-004、XSC-006、NV-F-003、NV-F-004、NV-F-005、NV-P-001、NV-P-002、NV-P-003、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005，只返回 schema source metadata、Vehicle Signal Adapter owner、platform ABI owner、Android/Linux parity evidence、DRV-GAP-002 evidence 和 no-write-before-read-bridge 门禁；`summary.read_bridge_activated=false`、`summary.schema_source_attached=false`、`summary.adapter_owner_confirmed=false`、`summary.parity_evidence_attached=false`、`summary.drv_gap_002_evidence_attached=false`、`summary.hardware_accessed=false`、`summary.driver_development_triggered=false`、`summary.virtualization_development_triggered=false` 表示本轮没有解析 DBC/ARXML、没有激活真实读桥、没有访问硬件、没有新增 Driver/HAL、没有开发虚拟化层。

`GET /uib/events/subscriptions`、`POST /uib/events/subscriptions/request`、`POST /uib/events/subscriptions/cancel`、`GET /uib/events/subscriptions/transport-readiness`、`GET /uib/events/subscriptions/decision-matrix`、`GET /uib/events/subscriptions/activation-checklist`、`GET /uib/events/subscriptions/callback-watch-shape`、`GET /uib/events/subscriptions/cursor-replay-storage`、`GET /uib/events/subscriptions/backpressure-qos-evidence`、`GET /uib/events/subscriptions/readiness-rollup`、`POST /uib/events/subscriptions/activation-evidence`、`GET /uib/events/subscriptions/activation-evidence/status`、`GET /uib/events/subscriptions/activation-evidence/retention-checklist` 和 `GET /uib/events/subscriptions/activation-evidence/decision-status-rollup` 覆盖 XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004，只返回 Event subscription lifecycle、cursor/replay storage、filter、QoS/backpressure、governance、request/cancel contract-only 命令、callback/watch transport readiness、broker/cursor/backpressure owner decision matrix、activation evidence gates、activation evidence intake、activation evidence review status、retention checklist、decision status rollup、callback/watch API shape、cursor schema、ack shape、replay window、retention/cleanup、overflow schema、per-caller throttling、replay rate、ack timeout、Runtime & Governance QoS evidence、readiness blockers、Android/Linux parity 和 `EV-SUB-001..006`/`EV-TR-001..006`/`EV-DM-001..007`/`EV-ACT-001..008`/`EV-CW-001..008`/`EV-CRS-001..008`/`EV-QOS-001..008`/`EV-RU-001..006`/`EV-AE-001..008`/`EV-AES-001..006`/`EV-AER-001..008`/`EV-AED-001..008` 门禁；`summary.cursor_replay_storage_confirmed=false`、`summary.backpressure_qos_evidence_confirmed=false`、`summary.readiness_rollup_confirmed=false`、`summary.activation_evidence_status_contract_active=true`、`summary.activation_evidence_retention_checklist_active=true`、`summary.activation_evidence_decision_status_rollup_active=true`、`summary.decision_status_consistent=true`、`summary.decision_status_passed=false`、`summary.owner_decision_complete=false`、`summary.retention_policy_confirmed=false`、`summary.evidence_uri_rules_confirmed=false`、`summary.evidence_store_active=false`、`summary.review_workflow_active=false`、`summary.delete_workflow_active=false`、`summary.export_workflow_active=false`、`summary.persisted_submission_count=0`、`summary.pending_review_count=0`、`summary.activation_evidence_intake_called=false`、`summary.activation_evidence_persisted=false`、`summary.activation_evidence_accepted_for_review=false`、`summary.review_queue_updated=false`、`summary.gate_state_changed=false`、`summary.gates_closed=false`、`summary.production_activation_allowed=false`、`summary.activation_allowed=false`、`summary.broker_activation_ready=false`、`summary.all_required_evidence_complete=false`、`summary.transport_selected=false`、`summary.subscription_persisted=false`、`summary.subscription_persistence_active=false`、`summary.broker_active=false`、`summary.callback_registered=false`、`summary.watch_started=false`、`summary.cursor_storage_active=false`、`summary.replay_index_active=false`、`summary.dds_runtime_active=false`、`summary.sse_websocket_active=false`、`summary.high_rate_data_plane_active=false`、`summary.hardware_accessed=false`、`summary.driver_development_triggered=false`、`summary.virtualization_development_triggered=false` 表示本轮没有分配量产 owner、没有选择 transport、没有持久化 evidence、没有调用 activation evidence POST、没有读取 evidence store、没有创建 review queue、没有创建 delete/export workflow、没有激活事件 QoS、没有关闭 readiness gate、没有创建 cursor storage、没有建立 replay index、没有启动真实订阅 broker、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL 或虚拟化层。

`GET /soa/contracts` 覆盖 XSC-003、FW-S-004、NV-G-003、DEL-001、DEL-002，从 `runtime_governance.SERVICE_CATALOG` 返回服务 contract、版本、Policy/Safety State、QoS、Lifecycle 和 no-dispatch 边界；它只做 contract visibility，不调用 SOA service、Driver/HAL、车辆总线或虚拟化层。

`GET /bindings/readiness` 覆盖 XSC-006、NV-P-001..006、DEL-001、DEL-002、DEL-003、DEL-004，从 `protocol_bindings.py` 返回 Android Binder、Linux IPC、Linux gRPC/RPC、REST、MQTT、SOME/IP、DDS readiness、阻塞项、验证命令和下一步决策；它只做 contract visibility，不实现量产 transport、真实 gRPC runtime、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。

`GET /delivery/readiness` 覆盖 DEL-001、DEL-002、DEL-003、DEL-004、DEL-005、XSC-001..006，从 `delivery_readiness.py` 返回 Android debug Console/Binder、Android system service note、Linux CLI、Linux IPC、Linux gRPC/RPC、Linux systemd/package profile、Driver/HAL gap backlog 和虚拟化约束的交付状态、验证命令和阻塞项；它只做交付 metadata visibility，不实现 Android system service、真实 gRPC runtime、量产包管理、生产共享治理后端、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。

`GET /prototype/readiness` 覆盖 XSC-001..006、DEL-001..005、HW-002、KH-003、KH-006、KH-007，从 `prototype_readiness.py` 返回 Python 原型模块成熟度、Android 主路径、Linux 同步路径、开放偏差、开放问题和下一步候选增量；它只做产品/架构/交付状态总览，明确 `production_ready=false`、`hardware_accessed=false`、`driver_development_triggered=false`、`virtualization_development_triggered=false` 和 `service_dispatch_triggered=false`。

## 验证

```bash
bash tools/smoke_central_brain_qos.sh
```

该脚本验证 `POST /soa/invoke` 对 `npu-inference` 执行 NV-G-004 QoS fixed-window 限流，并把 `qos_rejected` 写入 audit。

`POST /governance/precheck` 用于 XSC-005/NV-G-002/NV-G-004..007 的只检查不调用路径，默认 `consume_qos=false`，因此可供 Android/Linux 集成方在发起真实 SOA 调用前查看 discovery、Policy、Lifecycle 与 QoS 决策，不会 dispatch 到 Driver/HAL、车辆总线或虚拟化层。
