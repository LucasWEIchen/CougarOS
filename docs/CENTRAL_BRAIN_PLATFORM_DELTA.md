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

硬件接口 owner evidence adapter-load approval authority audit consistency 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/audit-consistency`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistencyJson`、Android Console `HW ApAudit`、
Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-authority-audit-consistency`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.approval.authority.audit.consistency` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistency` 调用。该 audit consistency view 只比较
approval checklist、approval authority no-store status、adapter-load dry-run audit consistency、adapter-load blocker rollup
和 `HW-ALA`/`HW-AAS`/`HW-ALC`/`HW-ALB`/`HW-AAC` gate family，固定 `HW-AAC-001..008`、
`consistency_passed=true`、`approval_status_no_store_consistent=true`、`approval_decisions_open_consistent=true`、
`adapter_load_blocked_consistent=true`、`adapter_load_allowed=false`、`hardware_accessed=false`、
`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。它不调用 dry-run POST，
不持久化 approval record，不创建 evidence store，不创建 review queue，不关闭 gate，不选择 adapter，
不加载 adapter，不激活 adapter，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，
不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence adapter-load approval decision dry-run 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`POST /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run`、Android Binder
`dryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionJson`、Android Console `HW ApDec`、
Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.dry.run` 与 Linux gRPC/RPC
`DryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecision` 调用。该 dry-run 只校验 selected interface、
adapter identity、approval decision、approval authority/signature、requester 和 evidence refs，并绑定 approval
checklist/status/audit 与 blocker rollup，固定 `HW-APD-001..008`、
`approval_decision_dry_run_state=rejected_blocked_contract_only`、`approval_authority_ready=false`、
`approval_record_persisted=false`、`approval_decision_persisted=false`、`approval_review_queue_updated=false`、
`approval_evidence_store_active=false`、`adapter_load_allowed=false`、`hardware_accessed=false`、
`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。它不持久化 approval decision，
不创建 evidence store，不更新 review queue，不关闭 gate，不选择 adapter，不加载 adapter，不激活 adapter，
不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence adapter-load approval decision dry-run status 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/status`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunStatusJson`、Android Console `HW ApDStat`、
Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run-status`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.dry.run.status` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunStatus` 调用。该 status 只报告 approval decision dry-run
last-result 未持久化、approval decision persisted count 为 0、无 approval decision review queue、无 evidence store，
并固定 `HW-APS-001..008`、`decision_dry_run_post_called_by_status=false`、`adapter_load_allowed=false`、
`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。它不调用 dry-run POST，
不持久化 approval decision，不创建 evidence store，不更新 review queue，不关闭 gate，不选择 adapter，不加载 adapter，
不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence adapter-load approval decision dry-run audit consistency 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/audit-consistency`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunAuditConsistencyJson`、Android Console `HW ApDAudit`、
Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run-audit-consistency`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.dry.run.audit.consistency` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunAuditConsistency` 调用。该 audit consistency 只交叉核对 approval decision dry-run/status、
approval authority audit/status 和 blocker rollup，固定 `HW-APA-001..008`、`consistency_passed=true`、
`decision_dry_run_post_called_by_audit_consistency=false`、`approval_decision_persisted=false`、`adapter_load_allowed=false`、
`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。它不调用 dry-run POST，
不持久化 approval decision，不创建 evidence store，不更新 review queue，不关闭 gate，不选择 adapter，不加载 adapter，
不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence adapter-load approval decision closure blocker matrix 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionClosureBlockerMatrixJson`、Android Console `HW ApBlock`、
Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-decision-closure-blocker-matrix`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.closure.blocker.matrix` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionClosureBlockerMatrix` 调用。该 matrix 只把 approval authority、approval policy、
owner signature source、RBAC mapping、approval record schema、evidence store owner、review workflow、target smoke evidence、rollback plan、
fault model、Driver/HAL gap closure evidence、audit owner 和 gate closure authority 固定为 `HW-APM-001..008` 开放阻塞项，报告
`closure_ready=false`、`approval_decision_closure_allowed=false`、`adapter_load_allowed=false`、`hardware_accessed=false`、
`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。它不调用 dry-run POST，不持久化 approval decision，
不创建 evidence store，不更新 review queue，不关闭 gate，不选择 adapter，不加载 adapter，不打开 device node，不调用 HAL/vendor SDK，
不分配 shared memory，不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence adapter-load approval decision reviewer matrix 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionReviewerMatrixJson`、Android Console `HW ApRev`、
Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-decision-reviewer-matrix`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.reviewer.matrix` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionReviewerMatrix` 调用。该 matrix 只把 approval authority、approval policy、
signature/RBAC、approval record schema、approval evidence store、review workflow、target smoke、rollback/fault、Driver/HAL gap、
audit export 和 gate closure reviewer 固定为 `HW-APR-001..008` 未分配项，报告 `unassigned_reviewer_count=11`、`review_ready=false`、
`approval_review_allowed=false`、`retention_review_allowed=false`、`gate_closure_allowed=false`、`adapter_load_allowed=false`、
`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。它不分配 reviewer，
不持久化 approval decision，不创建 evidence store，不更新 review queue，不关闭 gate，不选择 adapter，不加载 adapter，
不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence adapter-load approval reviewer evidence handoff checklist 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffChecklistJson`、Android Console `HW ApHand`、
Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-checklist`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.checklist` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffChecklist` 调用。该 checklist 只把 11 个 reviewer handoff packet
固定为缺失项，暴露 `HW-ARH-001..008`、`required_handoff_packet_count=11`、`missing_handoff_packet_count=11`、`handoff_ready=false`、
`evidence_handoff_allowed=false`、`approval_review_allowed=false`、`retention_review_allowed=false`、`gate_closure_allowed=false`、
`adapter_load_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。
它不接收或附加 evidence handoff packet，不持久化 approval decision，不创建 evidence store，不更新 review queue，不关闭 gate，
不选择 adapter，不加载 adapter，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence adapter-load approval reviewer evidence handoff acceptance status 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceStatusJson`、Android Console `HW ApHStat`、
Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-status`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.status` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceStatus` 调用。该 status 只把 11 个 handoff packet
acceptance 固定为阻塞项，暴露 `HW-AHA-001..008`、`required_acceptance_count=11`、`blocked_acceptance_count=11`、
`accepted_handoff_packet_count=0`、`acceptance_record_persisted_count=0`、`handoff_acceptance_allowed=false`、
`approval_review_allowed=false`、`retention_review_allowed=false`、`gate_closure_allowed=false`、`adapter_load_allowed=false`、
`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。
它不接收或接受 evidence handoff packet，不持久化 acceptance record，不创建 evidence store，不更新 review queue，不关闭 gate，
不选择 adapter，不加载 adapter，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence adapter-load approval reviewer evidence handoff acceptance audit consistency 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/audit-consistency`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceAuditConsistencyJson`、Android Console `HW ApHAud`、
Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-audit-consistency`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.audit.consistency` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceAuditConsistency` 调用。该 audit 只交叉校验 checklist/status/parity/no-side-effect，
暴露 `HW-AHC-001..008`、`consistency_passed=true`、`handoff_checklist_consistent=true`、`acceptance_status_consistent=true`、
`blocked_acceptance_state_consistent=true`、`no_store_consistent=true`、`no_review_gate_load_consistent=true`、`no_side_effects_consistent=true`、
`required_handoff_packet_count=11`、`missing_handoff_packet_count=11`、`required_acceptance_count=11`、`blocked_acceptance_count=11`、
`accepted_handoff_packet_count=0`、`acceptance_record_persisted_count=0`、`adapter_load_allowed=false`、`hardware_accessed=false`、
`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。它不接收或接受 evidence handoff packet，不持久化 acceptance record，
不创建 evidence store，不更新 review queue，不关闭 gate，不选择 adapter，不加载 adapter，不打开 device node，不调用 HAL/vendor SDK，
不分配 shared memory，不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence adapter-load approval reviewer evidence handoff acceptance decision rollup 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceDecisionRollupJson`、Android Console `HW ApHRoll`、
Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-decision-rollup`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.decision.rollup` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceDecisionRollup` 调用。该 rollup 只汇总 acceptance authority、
acceptance record store、review workflow、audit retention、rollback/fault、Driver/HAL acceptance reviewer、gate closure authority 和 handoff packet presence
的未确认决策，暴露 `HW-AHD-001..008`、`decision_rollup_complete=true`、`decision_rollup_consistent=true`、`required_decision_count=8`、
`blocked_decision_count=8`、`acceptance_decision_ready=false`、`accepted_handoff_packet_count=0`、`acceptance_record_persisted_count=0`、
`adapter_load_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。
它不接收或接受 evidence handoff packet，不持久化 acceptance record/approval/evidence，不创建 evidence store，不更新 review queue，不关闭 gate，
不选择 adapter，不加载 adapter，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence adapter-load approval reviewer evidence handoff acceptance closure readiness decision rollup 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionRollupJson`、Android Console `HW ApHCRoll`、
Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-rollup`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.rollup` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionRollup` 调用。该 rollup 只汇总 closure readiness audit、closure blockers、
acceptance decision blockers、Android/Linux parity、no-store/no-review-gate-load 和 no-side-effect counters，暴露 `HW-AHG-001..008`、
`decision_rollup_complete=true`、`decision_rollup_consistent=true`、`closure_readiness_audit_consistent=true`、`closure_ready=false`、`closure_decision_ready=false`、
`blocked_decision_count=8`、`closure_blocker_count=8`、`adapter_load_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。
它不接收或接受 evidence handoff packet，不持久化 acceptance record/approval/evidence，不创建 evidence store，不更新 review queue，不关闭 gate，
不选择 adapter，不加载 adapter，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence adapter-load approval reviewer evidence handoff acceptance closure readiness checklist 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessChecklistJson`、Android Console `HW ApHClose`、
Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-checklist`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.checklist` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessChecklist` 调用。该 checklist 只汇总 acceptance authority、
acceptance record store、review workflow、audit retention、rollback/fault、Driver/HAL acceptance、gate closure authority 和 handoff packet presence 的 closure readiness 阻塞项，
暴露 `HW-AHE-001..008`、`closure_readiness_complete=true`、`closure_ready=false`、`required_closure_check_count=8`、`closure_blocker_count=8`、
`ready_closure_check_count=0`、`decision_rollup_consistent=true`、`accepted_handoff_packet_count=0`、`acceptance_record_persisted_count=0`、
`adapter_load_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。
它不接收或接受 evidence handoff packet，不持久化 acceptance record/approval/evidence，不创建 evidence store，不更新 review queue，不关闭 gate，
不选择 adapter，不加载 adapter，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence adapter-load approval reviewer evidence handoff acceptance closure readiness audit consistency 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessAuditConsistencyJson`、Android Console `HW ApHCAud`、
Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-audit-consistency`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.audit.consistency` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessAuditConsistency` 调用。该 audit 只校验 closure readiness checklist、
closure blocker state、decision rollup、Android/Linux parity、no-store/no-review-gate-load 和 no-side-effect counters，暴露 `HW-AHF-001..008`、
`consistency_passed=true`、`closure_readiness_checklist_consistent=true`、`closure_ready=false`、`required_closure_check_count=8`、`closure_blocker_count=8`、
`ready_closure_check_count=0`、`adapter_load_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。
它不接收或接受 evidence handoff packet，不持久化 acceptance record/approval/evidence，不创建 evidence store，不更新 review queue，不关闭 gate，
不选择 adapter，不加载 adapter，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence adapter-load approval reviewer evidence handoff acceptance closure readiness decision reviewer assignment checklist 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentChecklistJson`、Android Console `HW ApHCRev`、
Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-checklist`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.reviewer.assignment.checklist` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentChecklist` 调用。该 checklist 只汇总 reviewer assignment authority、closure readiness authority、
acceptance authority、record/evidence store、review workflow/audit retention、rollback/fault、Driver/HAL acceptance reviewer、gate closure authority 和 Android/Linux parity
的未分配项，暴露 `HW-AHH-001..008`、`reviewer_assignment_checklist_complete=true`、`reviewer_assignment_ready=false`、`reviewer_assignment_allowed=false`、
`required_reviewer_assignment_count=8`、`assigned_reviewer_count=0`、`unassigned_reviewer_count=8`、`reviewer_assignments_persisted=false`、`reviewer_assignment_queue_updated=false`、
`adapter_load_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。
它不分配 reviewer，不接收或接受 evidence handoff packet，不持久化 acceptance record/approval/evidence，不创建 evidence store，不更新 review queue，不关闭 gate，
不选择 adapter，不加载 adapter，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence adapter-load approval reviewer evidence handoff acceptance closure readiness decision reviewer assignment audit consistency 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistencyJson`、Android Console `HW ApHRvA`、
Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-consistency`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.reviewer.assignment.audit.consistency` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistency` 调用。该 audit 只校验 reviewer assignment checklist、
assignment count、blocked assignment state、no-store、no-review/gate/load、Android/Linux parity 和 no-side-effect counters，暴露 `HW-AHI-001..008`、
`consistency_passed=true`、`source_reviewer_assignment_checklist_bound=true`、`reviewer_assignment_count_consistent=true`、`reviewer_assignment_blocker_state_consistent=true`、
`no_store_consistent=true`、`no_review_queue_gate_load_consistent=true`、`no_side_effects_consistent=true`、`reviewer_assignment_ready=false`、`assigned_reviewer_count=0`、
`unassigned_reviewer_count=8`、`reviewer_assignments_persisted=false`、`reviewer_assignment_queue_updated=false`、`adapter_load_allowed=false`、`hardware_accessed=false`、
`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。
它不分配 reviewer，不接收或接受 evidence handoff packet，不持久化 acceptance record/approval/evidence/reviewer assignment，不创建 evidence store，不更新 review queue，不关闭 gate，
不选择 adapter，不加载 adapter，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence adapter-load closure handoff readiness summary 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummaryJson`、Android Console `HW ApHReady`、
Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-summary`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.summary` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummary` 调用。该 summary 只汇总 reviewer assignment audit decision rollup 后仍开放的 handoff dependencies，暴露 `HW-AHK-001..008`、`closure_handoff_readiness_complete=true`、`closure_handoff_ready=false`、`handoff_ready=false`、
`required_handoff_dependency_count=8`、`open_handoff_dependency_count=8`、`reviewer_assignment_decision_blocked=true`、`adapter_load_decision=blocked-by-unassigned-reviewers`、`assigned_reviewer_count=0`、`unassigned_reviewer_count=8`、
`reviewer_assignments_persisted=false`、`reviewer_assignment_queue_updated=false`、`handoff_acceptance_allowed=false`、`adapter_load_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。
它不分配 reviewer，不接收或接受 evidence handoff packet，不持久化 acceptance record/approval/evidence/reviewer assignment，不创建 evidence store，不更新 review queue，不关闭 gate，
不选择 adapter，不加载 adapter，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不触发 Driver/HAL 或虚拟化层。

硬件接口 owner evidence adapter-load approval reviewer evidence handoff acceptance closure readiness decision reviewer assignment audit decision rollup 按 HW-002、KH-003、KH-006、KH-007、DEL-005 在
`GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup`、Android Binder
`getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupJson`、Android Console `HW ApHRvRoll`、
Linux CLI `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup`、Linux IPC
`hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup` 与 Linux gRPC/RPC
`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollup` 调用。该 rollup 只汇总 reviewer assignment audit consistency、
未分配 reviewer 和 adapter load 阻塞决策，暴露 `HW-AHJ-001..008`、`decision_rollup_complete=true`、`decision_rollup_consistent=true`、`source_reviewer_assignment_audit_bound=true`、
`reviewer_assignment_audit_consistent=true`、`reviewer_assignment_decision_blocked=true`、`adapter_load_decision=blocked-by-unassigned-reviewers`、`required_decision_count=8`、
`blocked_decision_count=8`、`assigned_reviewer_count=0`、`unassigned_reviewer_count=8`、`reviewer_assignments_persisted=false`、`reviewer_assignment_queue_updated=false`、
`adapter_load_allowed=false`、`hardware_accessed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。
它不分配 reviewer，不接收或接受 evidence handoff packet，不持久化 acceptance record/approval/evidence/reviewer assignment，不创建 evidence store，不更新 review queue，不关闭 gate，
不选择 adapter，不加载 adapter，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不触发 Driver/HAL 或虚拟化层。

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

Event subscription activation approval dry-run status 按 FW-U-003、NV-P-006、XSC-002、XSC-005、XSC-006 在
`GET /uib/events/subscriptions/activation-evidence/approval-dry-run/status`、Android Binder
`getEventSubscriptionActivationApprovalDryRunStatusJson`、Android Console `Sub ApStat`、Linux CLI
`event-subscription-activation-approval-dry-run-status`、Linux IPC
`uib.events.subscriptions.activation.approval.dry.run.status` 与 Linux gRPC/RPC
`GetEventSubscriptionActivationApprovalDryRunStatus` 查询。该视图只报告 activation approval
dry-run 的 no-store status、last-result shape 和 `EV-AAS-001..008` 门禁；
固定 `approval_dry_run_invoked=false`、`last_result_available=false`、
`persisted_dry_run_count=0`、`pending_approval_count=0`、
`approval_authority_assigned=false`、`approval_policy_confirmed=false`、
`approval_result_store_active=false`、`review_queue_updated=false`、`gates_closed=false`、
`activation_allowed=false`、`broker_active=false`、`driver_development_triggered=false`
和 `virtualization_development_triggered=false`。它不调用 approval dry-run POST，不保存请求或结果，
不创建 review queue、不关闭 readiness gate、不激活 broker，不触发 Driver/HAL 或虚拟化层。

Event subscription activation approval authority checklist 按 FW-U-003、NV-P-006、XSC-002、XSC-005、XSC-006 在
`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist`、Android Binder
`getEventSubscriptionActivationApprovalAuthorityChecklistJson`、Android Console `Sub ApAuth`、Linux CLI
`event-subscription-activation-approval-authority-checklist`、Linux IPC
`uib.events.subscriptions.activation.approval.authority.checklist` 与 Linux gRPC/RPC
`GetEventSubscriptionActivationApprovalAuthorityChecklist` 查询。该视图只报告 activation approval
authority checklist 和 `EV-AAA-001..008` 门禁；固定
`approval_authority_ready=false`、`approval_authority_assigned=false`、
`approval_policy_confirmed=false`、`approval_signature_rbac_confirmed=false`、
`approval_result_store_active=false`、`review_queue_owner_assigned=false`、
`gate_closure_authority_assigned=false`、`broker_activation_owner_assigned=false`、
`driver_gap_review_owner_assigned=false`、`approval_dry_run_invoked=false`、
`approval_result_store_created=false`、`review_queue_updated=false`、`gates_closed=false`、
`activation_allowed=false`、`broker_active=false`、`driver_development_triggered=false`
和 `virtualization_development_triggered=false`。它不调用 approval dry-run POST，不分配 approval
authority，不创建 approval result store，不更新 review queue，不关闭 readiness gate，不激活 broker，
不触发 Driver/HAL 或虚拟化层。

Event subscription activation approval authority audit consistency 按 FW-U-003、NV-P-006、XSC-002、XSC-005、XSC-006 在
`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/audit-consistency`、Android Binder
`getEventSubscriptionActivationApprovalAuthorityAuditConsistencyJson`、Android Console `Sub ApAudit`、Linux CLI
`event-subscription-activation-approval-authority-audit-consistency`、Linux IPC
`uib.events.subscriptions.activation.approval.authority.audit.consistency` 与 Linux gRPC/RPC
`GetEventSubscriptionActivationApprovalAuthorityAuditConsistency` 查询。该视图只报告 approval authority checklist、
approval dry-run status、activation evidence decision status rollup、Android/Linux parity 与 no-side-effect 的
`EV-AAC-001..008` 审计一致性；固定 `consistency_passed=true`、`source_authority_checklist_bound=true`、
`source_approval_dry_run_status_bound=true`、`source_decision_status_rollup_bound=true`、
`approval_no_store_consistent=true`、`android_linux_parity_consistent=true`、`no_side_effects_consistent=true`、
`approval_authority_ready=false`、`approval_dry_run_invoked=false`、`approval_result_store_created=false`、
`review_queue_updated=false`、`gates_closed=false`、`activation_allowed=false`、`broker_active=false`、
`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。它不调用 approval dry-run POST，
不分配 approval authority，不创建 approval result store，不更新 review queue，不关闭 readiness gate，
不激活 broker、DDS runtime、高频数据面、Driver/HAL 或虚拟化层。

Event subscription activation approval decision blocker rollup 按 FW-U-003、NV-P-006、XSC-002、XSC-005、XSC-006 在
`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-blocker-rollup`、Android Binder
`getEventSubscriptionActivationApprovalDecisionBlockerRollupJson`、Android Console `Sub ApBlock`、Linux CLI
`event-subscription-activation-approval-decision-blocker-rollup`、Linux IPC
`uib.events.subscriptions.activation.approval.decision.blocker.rollup` 与 Linux gRPC/RPC
`GetEventSubscriptionActivationApprovalDecisionBlockerRollup` 查询。该视图只报告 `EV-ADB-001..008` approval decision blocker rollup；固定
`approval_decision_ready=false`、`approval_dry_run_allowed=false`、`approval_result_store_created=false`、
`review_queue_updated=false`、`gates_closed=false`、`broker_activation_allowed=false`、`activation_allowed=false`、
`broker_active=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。它不持久化 approval decision，
不分配 approval authority，不创建 result store/review queue，不关闭 gate，不激活 broker/DDS/high-rate data plane，不触发 Driver/HAL 或虚拟化层。

Event subscription activation approval decision dry-run request 按 FW-U-003、NV-P-006、XSC-002、XSC-005、XSC-006 在
`POST /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run`、Android Binder
`dryRunEventSubscriptionActivationApprovalDecisionJson`、Android Console `Sub ApDec`、Linux CLI
`event-subscription-activation-approval-decision-dry-run`、Linux IPC
`uib.events.subscriptions.activation.approval.decision.dry.run` 与 Linux gRPC/RPC
`DryRunEventSubscriptionActivationApprovalDecision` 查询。该 dry-run request 只校验 `EV-ADD-001..008` request contract 和 source blocker rollup binding；固定
`rejected_blocked_contract_only=true`、`approval_decision_ready=false`、`approval_dry_run_allowed=false`、
`dry_run_request_persisted=false`、`dry_run_result_persisted=false`、`approval_decision_persisted=false`、
`review_queue_updated=false`、`gates_closed=false`、`broker_activation_allowed=false`、`activation_allowed=false`、
`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。它不保存请求或结果，不创建 result store/review queue，
不关闭 gate，不激活 broker/DDS/high-rate data plane，不触发 Driver/HAL 或虚拟化层。

Event subscription activation approval decision dry-run status 按 FW-U-003、NV-P-006、XSC-002、XSC-005、XSC-006 在
`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/status`、Android Binder
`getEventSubscriptionActivationApprovalDecisionDryRunStatusJson`、Android Console `Sub ApDStat`、Linux CLI
`event-subscription-activation-approval-decision-dry-run-status`、Linux IPC
`uib.events.subscriptions.activation.approval.decision.dry.run.status` 与 Linux gRPC/RPC
`GetEventSubscriptionActivationApprovalDecisionDryRunStatus` 查询。该 status 只报告 `EV-ADS-001..008` no-store decision status 和 source blocker rollup binding；固定
`last_approval_decision_result_available=false`、`persisted_dry_run_request_count=0`、`persisted_dry_run_result_count=0`、`persisted_approval_decision_count=0`、
`decision_dry_run_post_called_by_status=false`、`approval_result_store_created=false`、`review_queue_updated=false`、`gates_closed=false`、
`broker_activation_allowed=false`、`activation_allowed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。它不调用 dry-run POST，
不保存 last-result/request/result/approval decision，不创建 result store/review queue，不关闭 gate，不激活 broker/DDS/high-rate data plane，不触发 Driver/HAL 或虚拟化层。

Event subscription activation approval decision dry-run audit consistency 按 FW-U-003、NV-P-006、XSC-002、XSC-005、XSC-006 在
`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/audit-consistency`、Android Binder
`getEventSubscriptionActivationApprovalDecisionDryRunAuditConsistencyJson`、Android Console `Sub ApDAudit`、Linux CLI
`event-subscription-activation-approval-decision-dry-run-audit-consistency`、Linux IPC
`uib.events.subscriptions.activation.approval.decision.dry.run.audit.consistency` 与 Linux gRPC/RPC
`GetEventSubscriptionActivationApprovalDecisionDryRunAuditConsistency` 查询。该 audit consistency 只报告 `EV-ADA-001..008`，只读核对 decision blocker rollup、
decision dry-run request contract、decision dry-run no-store status、Android/Linux parity、zero persisted counters 和 no-side-effect 边界；固定
`consistency_passed=true`、`source_decision_dry_run_contract_bound=true`、`source_decision_blocker_rollup_bound=true`、`blocker_count_consistent=true`、
`no_store_consistent=true`、`decision_dry_run_rejection_consistent=true`、`android_linux_parity_consistent=true`、`no_side_effects_consistent=true`、
`decision_dry_run_post_called_by_audit_consistency=false`、`persisted_dry_run_request_count=0`、`persisted_dry_run_result_count=0`、
`persisted_approval_decision_count=0`、`review_queue_updated=false`、`gates_closed=false`、`broker_activation_allowed=false`、`activation_allowed=false`、
`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。它不调用 dry-run POST，不保存 request/result/approval decision，
不创建 result store/review queue，不关闭 gate，不激活 broker/DDS/high-rate data plane，不触发 Driver/HAL 或虚拟化层。

Event subscription activation approval decision closure blocker matrix 按 FW-U-003、NV-P-006、XSC-002、XSC-005、XSC-006 在
`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix`、Android Binder
`getEventSubscriptionActivationApprovalDecisionClosureBlockerMatrixJson`、Android Console `Sub ApClose`、Linux CLI
`event-subscription-activation-approval-decision-closure-blocker-matrix`、Linux IPC
`uib.events.subscriptions.activation.approval.decision.closure.blocker.matrix` 与 Linux gRPC/RPC
`GetEventSubscriptionActivationApprovalDecisionClosureBlockerMatrix` 查询。该 closure blocker matrix 只报告 `EV-ACB-001..010`，列出 approval authority、
approval policy、signature/RBAC、approval result store、review queue owner、gate closure authority、broker activation owner、DRV-GAP-004/005 owner、
Android/Linux closure parity evidence 和 high-rate transport activation evidence；固定 `closure_ready=false`、`open_closure_blocker_count=10`、
`decision_dry_run_post_called_by_closure_blocker_matrix=false`、`persisted_dry_run_request_count=0`、`persisted_dry_run_result_count=0`、
`persisted_approval_decision_count=0`、`review_queue_updated=false`、`gates_closed=false`、`broker_activation_allowed=false`、`activation_allowed=false`、
`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。它不调用 dry-run POST，不保存 request/result/approval decision，
不创建 result store/review queue，不关闭 gate，不激活 broker/DDS/high-rate data plane，不触发 Driver/HAL 或虚拟化层。

Event subscription activation approval decision owner handoff checklist 按 FW-U-003、NV-P-006、XSC-002、XSC-005、XSC-006 在
`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist`、Android Binder
`getEventSubscriptionActivationApprovalDecisionOwnerHandoffChecklistJson`、Android Console `Sub ApHand`、Linux CLI
`event-subscription-activation-approval-decision-owner-handoff-checklist`、Linux IPC
`uib.events.subscriptions.activation.approval.decision.owner.handoff.checklist` 与 Linux gRPC/RPC
`GetEventSubscriptionActivationApprovalDecisionOwnerHandoffChecklist` 查询。该 owner handoff checklist 只报告 `EV-ACH-001..010`，列出 expected owner role、
required evidence type、Android/Linux parity requirement 和 escalation state；固定 `owner_handoff_ready=false`、`open_owner_handoff_count=10`、
`assigned_owner_count=0`、`unassigned_owner_count=10`、`owner_assignments_persisted=false`、`owner_handoff_queue_updated=false`、
`decision_dry_run_post_called_by_owner_handoff_checklist=false`、`review_queue_updated=false`、`gates_closed=false`、`broker_activation_allowed=false`、
`activation_allowed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。它不分配 owner，不调用 dry-run POST，
不保存 handoff/request/result/approval decision，不创建 result store/review queue，不关闭 gate，不激活 broker/DDS/high-rate data plane，
不触发 Driver/HAL 或虚拟化层。

Event subscription activation approval decision owner handoff audit consistency 按 FW-U-003、NV-P-006、XSC-002、XSC-005、XSC-006 在
`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency`、Android Binder
`getEventSubscriptionActivationApprovalDecisionOwnerHandoffAuditConsistencyJson`、Android Console `Sub ApHAud`、Linux CLI
`event-subscription-activation-approval-decision-owner-handoff-audit-consistency`、Linux IPC
`uib.events.subscriptions.activation.approval.decision.owner.handoff.audit.consistency` 与 Linux gRPC/RPC
`GetEventSubscriptionActivationApprovalDecisionOwnerHandoffAuditConsistency` 查询。该 audit consistency 只核对 `EV-ACH-001..010` handoff checklist 与 `EV-ACB-001..010` closure blocker matrix 的数量、source binding、open state、owner assignment、evidence attachment、no-store、Android/Linux parity 和 no-side-effect；固定 `consistency_passed=true`、`owner_handoff_ready=false`、`assigned_owner_count=0`、`unassigned_owner_count=10`、`attached_evidence_count=0`、`owner_assignments_persisted=false`、`owner_handoff_queue_updated=false`、`decision_dry_run_post_called_by_owner_handoff_audit_consistency=false`、`review_queue_updated=false`、`gates_closed=false`、`broker_activation_allowed=false`、`activation_allowed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。它不分配 owner，不附加 evidence，不调用 dry-run POST，不保存 handoff/request/result/approval decision，不创建 result store/review queue，不关闭 gate，不激活 broker/DDS/high-rate data plane，不触发 Driver/HAL 或虚拟化层。

Event subscription activation approval decision owner handoff decision rollup 按 FW-U-003、NV-P-006、XSC-002、XSC-005、XSC-006 在
`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup`、Android Binder
`getEventSubscriptionActivationApprovalDecisionOwnerHandoffDecisionRollupJson`、Android Console `Sub ApHRoll`、Linux CLI
`event-subscription-activation-approval-decision-owner-handoff-decision-rollup`、Linux IPC
`uib.events.subscriptions.activation.approval.decision.owner.handoff.decision.rollup` 与 Linux gRPC/RPC
`GetEventSubscriptionActivationApprovalDecisionOwnerHandoffDecisionRollup` 查询。该 decision rollup 只汇总 `EV-AHD-001..010` blocked decisions；固定 `owner_handoff_decision_blocked=true`、`approval_decision_ready=false`、`blocked_decision_count=10`、`unassigned_owner_count=10`、`attached_evidence_count=0`、`review_queue_updated=false`、`gates_closed=false`、`broker_activation_allowed=false`、`activation_allowed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。它不分配 owner，不附加 evidence，不调用 dry-run POST，不保存 handoff/request/result/approval decision，不创建 result store/review queue，不关闭 gate，不激活 broker/DDS/high-rate data plane，不触发 Driver/HAL 或虚拟化层。

Event subscription activation approval decision owner handoff evidence readiness matrix 按 FW-U-003、NV-P-006、XSC-002、XSC-005、XSC-006 在
`GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix`、Android Binder
`getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessMatrixJson`、Android Console `Sub ApHEv`、Linux CLI
`event-subscription-activation-approval-decision-owner-handoff-evidence-readiness-matrix`、Linux IPC
`uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.readiness.matrix` 与 Linux gRPC/RPC
`GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessMatrix` 查询。该 matrix 只汇总 `EV-AHE-001..010` missing evidence packets；固定 `handoff_evidence_ready=false`、`approval_decision_ready=false`、`missing_evidence_packet_count=10`、`attached_evidence_count=0`、`persisted_evidence_packet_count=0`、`evidence_uri_count=0`、`owner_signature_count=0`、`review_queue_updated=false`、`gates_closed=false`、`broker_activation_allowed=false`、`activation_allowed=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。它不分配 owner，不附加 evidence，不调用 dry-run POST，不保存 evidence/request/result/approval decision，不创建 evidence store 或 result store/review queue，不关闭 gate，不激活 broker/DDS/high-rate data plane，不触发 Driver/HAL 或虚拟化层。

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
- `/uib/events/subscriptions/activation-evidence/approval-authority-checklist/audit-consistency` 只是 Event subscription activation approval authority 的只读一致性视图；Android Binder `getEventSubscriptionActivationApprovalAuthorityAuditConsistencyJson`、Android Console `Sub ApAudit`、Linux CLI `event-subscription-activation-approval-authority-audit-consistency`、Linux IPC `uib.events.subscriptions.activation.approval.authority.audit.consistency` 与 Linux gRPC/RPC `GetEventSubscriptionActivationApprovalAuthorityAuditConsistency` 只证明 checklist/status/rollup/parity/no-side-effect 计数一致，不代表 approval authority 已分配、approval dry-run 已执行、review queue/result store 已创建、gate 已关闭、broker/DDS/high-rate data plane 已激活，且不触发 Driver/HAL 或虚拟化开发。
- `/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-blocker-rollup` 只是 Event subscription activation approval decision blocker 的只读汇总；Android Binder `getEventSubscriptionActivationApprovalDecisionBlockerRollupJson`、Android Console `Sub ApBlock`、Linux CLI `event-subscription-activation-approval-decision-blocker-rollup`、Linux IPC `uib.events.subscriptions.activation.approval.decision.blocker.rollup` 与 Linux gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionBlockerRollup` 只证明 `EV-ADB-001..008` 阻塞项已显式列出，不代表 approval dry-run、approval result store、review queue、gate closure、broker/DDS/high-rate data plane 已可用，且不触发 Driver/HAL 或虚拟化开发。
- `/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run` 只是 Event subscription activation approval decision dry-run 的 blocked/no-store request contract；Android Binder `dryRunEventSubscriptionActivationApprovalDecisionJson`、Android Console `Sub ApDec`、Linux CLI `event-subscription-activation-approval-decision-dry-run`、Linux IPC `uib.events.subscriptions.activation.approval.decision.dry.run` 与 Linux gRPC/RPC `DryRunEventSubscriptionActivationApprovalDecision` 只证明 dry-run request envelope 和 source blocker rollup binding 可校验，不代表 approval result store、review queue、gate closure、broker/DDS/high-rate data plane 已可用，且不保存请求或结果、不触发 Driver/HAL 或虚拟化开发。
- `/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency` 只是 Event subscription activation approval decision owner handoff evidence readiness 的只读审计一致性视图；Android Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessAuditConsistencyJson`、Android Console `Sub ApHEvAud`、Linux CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-readiness-audit-consistency`、Linux IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.readiness.audit.consistency` 与 Linux gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessAuditConsistency` 只证明 `EV-AHF-001..010` packet count/state、source binding、Android/Linux parity、no-store、no-POST 和 no-side-effect 一致，不代表 evidence packet 已提交、owner 已分配、review queue/result store/evidence store 已创建、gate 已关闭、broker/DDS/high-rate data plane 已激活，且不触发 Driver/HAL 或虚拟化开发。
- `/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status` 只是 Event subscription activation approval decision owner handoff evidence acceptance 的只读状态视图；Android Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceStatusJson`、Android Console `Sub ApHAcc`、Linux CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-status`、Linux IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.status` 与 Linux gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceStatus` 只证明 `EV-AHG-001..010` acceptance status 可查且全部被 missing evidence packet 阻塞，不代表 evidence packet 已被接受、evidence 已附加、owner 已分配、acceptance record/review queue/result store/evidence store 已创建、gate 已关闭、broker/DDS/high-rate data plane 已激活，且不触发 Driver/HAL 或虚拟化开发。
- `/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency` 只是 Event subscription activation approval decision owner handoff evidence acceptance 的只读审计一致性视图；Android Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceAuditConsistencyJson`、Android Console `Sub ApHAcAud`、Linux CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-audit-consistency`、Linux IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.audit.consistency` 与 Linux gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceAuditConsistency` 只证明 `EV-AHH-001..010` acceptance audit consistency 可查且 no-store/no-POST/no-side-effect 计数一致，不代表 evidence packet 已被接受、evidence 已附加、owner 已分配、acceptance record/review queue/result store/evidence store 已创建、gate 已关闭、broker/DDS/high-rate data plane 已激活，且不触发 Driver/HAL 或虚拟化开发。
- `/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup` 只是 Event subscription activation approval decision owner handoff evidence acceptance 的只读决策汇总视图；Android Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceDecisionRollupJson`、Android Console `Sub ApHDec`、Linux CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-decision-rollup`、Linux IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.decision.rollup` 与 Linux gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceDecisionRollup` 只证明 `EV-AHI-001..010` acceptance decision rollup 可查且全部仍被 missing evidence/authority/store/review/gate blockers 阻塞，不代表 evidence packet 已被接受、acceptance record/review queue/result store/evidence store 已创建、gate 已关闭、broker/DDS/high-rate data plane 已激活，且不触发 Driver/HAL 或虚拟化开发。
- 当前没有真实 Driver/HAL/NPU/Vehicle bus 接入；`/uib/events/subscriptions`、`/uib/events/subscriptions/request`、`/uib/events/subscriptions/cancel`、`/uib/events/subscriptions/transport-readiness`、`/uib/events/subscriptions/decision-matrix`、`/uib/events/subscriptions/activation-checklist`、`/uib/events/subscriptions/callback-watch-shape`、`/uib/events/subscriptions/cursor-replay-storage`、`/uib/events/subscriptions/backpressure-qos-evidence`、`/uib/events/subscriptions/readiness-rollup`、`/uib/events/subscriptions/activation-evidence`、`/uib/events/subscriptions/activation-evidence/status`、`/uib/events/subscriptions/activation-evidence/retention-checklist` 和 `/uib/events/subscriptions/activation-evidence/decision-status-rollup` 只是 Event subscription lifecycle、transport readiness、owner decision matrix、activation checklist、callback/watch shape、cursor/replay storage、backpressure/QoS evidence、readiness blocker rollup、activation evidence intake、review status、retention checklist 与 decision status rollup contract，不持久化 subscription/evidence，不调用 activation evidence POST，不读取 evidence store，不创建或更新 review queue，不创建 delete/export workflow，不关闭 gate，不选择 transport，不分配量产 owner，不注册 callback/watch，不启动 broker、SSE/WebSocket、DDS runtime 或高频数据面；`/hardware/interfaces` 只是 empty-interface registry，`/hardware/interfaces/activation-checklist` 只是 hardware activation owner/ABI/smoke checklist，`/hardware/interfaces/owner-decision-status` 只是 hardware owner decision status rollup，`/hardware/interfaces/owner-decision-evidence` 只是 no-store hardware owner evidence reference intake，`/hardware/interfaces/owner-decision-evidence/status` 只是 no-store hardware owner evidence status rollup，`/hardware/interfaces/owner-decision-evidence/retention-checklist` 只是 no-store retention/closure checklist，`/hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist` 只是 no-store replacement trigger checklist，`/hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist` 只是 no-store selected-adapter readiness checklist，`/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist` 只是 no-store/no-load approval authority checklist，`/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/status` 只是 no-store/no-load approval authority status，`/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/audit-consistency` 只是 no-store/no-load approval authority audit consistency，固定 `replacement_allowed=false`、`adapter_candidate_recorded=false`、`approval_authority_assigned=false`、`approval_policy_confirmed=false`、`approval_record_available=false`、`approval_record_persisted=false`、`persisted_approval_record_count=0`、`approval_decision_passed=false`、`approval_decisions_open=true`、`consistency_passed=true`、`approval_status_no_store_consistent=true`、`approval_decisions_open_consistent=true`、`adapter_load_blocked_consistent=true`、`adapter_load_allowed=false`、`adapter_activation_allowed=false`、`delete_workflow_active=false`、`export_workflow_active=false`、`gate_closure_allowed=false`、`hardware_access_allowed=false` 和 `activation_allowed=false`，不选择 adapter，不加载 adapter，不替换 adapter，不激活 adapter，不持久化 approval，不创建 durable evidence store，不读取 evidence URI，不创建 review queue，不创建 delete/export workflow，不关闭 gate；`/vehicle/signals` 只是 read-only VSS-style catalog，`/vehicle/signals/activation` 只是 read-bridge activation criteria，`/vehicle/signals/validation` 只是 read-bridge validation envelope，不访问 HAL、device node、vendor SDK、shared memory、VHAL、SocketCAN、DBC/ARXML、真实车辆总线或虚拟化层，偏差记录见 DEV-004、DEV-005、DEV-007、DEV-014、DEV-016。

## EV-AHJ Platform Delta

Event subscription activation approval decision owner handoff evidence acceptance closure readiness checklist 在 Android 与 Linux 上使用同一语义对象和同一 no-side-effect 边界。Android 主路径暴露 `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessChecklistJson` 与 Console `Sub ApHClose`，由 Binder service sample 代理 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist`；Linux 同步路径暴露 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-checklist`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.checklist` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessChecklist`。

该差异面只涉及调用壳层差异：Android 使用 Binder/AIDL 方法名和 Console debug action，Linux 使用 CLI/Unix socket IPC/gRPC JSON contract sample；两端 payload 均固定 `EV-AHJ-001..010`、`closure_ready=false`、`open_closure_check_count=10`、`accepted_evidence_packet_count=0`、`acceptance_record_persisted_count=0`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。Android system service、Linux daemon、Driver/HAL、DDS/high-rate data plane 和虚拟化层均不在本增量开发范围内。

## EV-AHK Platform Delta

Event subscription activation approval decision owner handoff evidence acceptance closure readiness audit consistency 在 Android 与 Linux 上使用同一语义对象和同一 no-store/no-POST/no-side-effect 边界。Android 主路径暴露 `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessAuditConsistencyJson` 与 Console `Sub ApHClAud`，由 Binder service sample 代理 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency`；Linux 同步路径暴露 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-audit-consistency`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.audit.consistency` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessAuditConsistency`。

该差异面只涉及调用壳层差异：Android 使用 Binder/AIDL 方法名和 Console debug action，Linux 使用 CLI/Unix socket IPC/gRPC JSON contract sample；两端 payload 均固定 `EV-AHK-001..010`、`consistency_passed=true`、`closure_check_count_consistent=true`、`closure_blocker_state_consistent=true`、`android_linux_parity_consistent=true`、`closure_ready=false`、`open_closure_check_count=10`、`accepted_evidence_packet_count=0`、`acceptance_record_persisted_count=0`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。Android system service、Linux daemon、Driver/HAL、DDS/high-rate data plane 和虚拟化层均不在本增量开发范围内。

## EV-AHL Platform Delta

Event subscription activation approval decision owner handoff evidence acceptance closure readiness decision rollup 在 Android 与 Linux 上使用同一语义对象和同一 no-store/no-POST/no-side-effect 边界。Android 主路径暴露 `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionRollupJson` 与 Console `Sub ApHClR`，由 Binder service sample 代理 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup`；Linux 同步路径暴露 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-rollup`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.rollup` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionRollup`。

该差异面只涉及调用壳层差异：Android 使用 Binder/AIDL 方法名和 Console debug action，Linux 使用 CLI/Unix socket IPC/gRPC JSON contract sample；两端 payload 均固定 `EV-AHL-001..010`、`decision_rollup_complete=true`、`decision_rollup_consistent=true`、`closure_readiness_audit_consistent=true`、`closure_ready=false`、`closure_decision_ready=false`、`blocked_decision_count=10`、`accepted_evidence_packet_count=0`、`acceptance_record_persisted_count=0`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。Android system service、Linux daemon、Driver/HAL、DDS/high-rate data plane 和虚拟化层均不在本增量开发范围内。

## EV-AHM Platform Delta

Event subscription activation approval decision owner handoff evidence acceptance closure readiness decision reviewer assignment checklist 在 Android 与 Linux 上使用同一语义对象和同一 no-store/no-POST/no-side-effect 边界。Android 主路径暴露 `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentChecklistJson` 与 Console `Sub ApHRev`，由 Binder service sample 代理 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist`；Linux 同步路径暴露 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-checklist`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.checklist` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentChecklist`。

该差异面只涉及调用壳层差异：Android 使用 Binder/AIDL 方法名和 Console debug action，Linux 使用 CLI/Unix socket IPC/gRPC JSON contract sample；两端 payload 均固定 `EV-AHM-001..010`、`reviewer_assignment_checklist_complete=true`、`reviewer_assignment_ready=false`、`assigned_reviewer_count=0`、`unassigned_reviewer_count=10`、`reviewer_assignments_persisted=false`、`reviewer_assignment_queue_updated=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。Android system service、Linux daemon、Driver/HAL、DDS/high-rate data plane 和虚拟化层均不在本增量开发范围内。

## EV-AHN Platform Delta

Event subscription activation approval decision owner handoff evidence acceptance closure readiness decision reviewer assignment audit consistency 在 Android 与 Linux 上使用同一语义对象和同一 no-store/no-POST/no-side-effect 边界。Android 主路径暴露 `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistencyJson` 与 Console `Sub ApHRvA`，由 Binder service sample 代理 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency`；Linux 同步路径暴露 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-consistency`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.consistency` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistency`。

该差异面只涉及调用壳层差异：Android 使用 Binder/AIDL 方法名和 Console debug action，Linux 使用 CLI/Unix socket IPC/gRPC JSON contract sample；两端 payload 均固定 `EV-AHN-001..010`、`consistency_passed=true`、`source_reviewer_assignment_checklist_bound=true`、`reviewer_assignment_count_consistent=true`、`reviewer_assignment_blocker_state_consistent=true`、`reviewer_assignment_ready=false`、`assigned_reviewer_count=0`、`unassigned_reviewer_count=10`、`reviewer_assignments_persisted=false`、`reviewer_assignment_queue_updated=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。Android system service、Linux daemon、Driver/HAL、DDS/high-rate data plane 和虚拟化层均不在本增量开发范围内。

## EV-AHO Platform Delta

Event subscription activation approval decision owner handoff evidence acceptance closure readiness decision reviewer assignment audit decision rollup 在 Android 与 Linux 上使用同一语义对象和同一 no-store/no-POST/no-side-effect 边界。Android 主路径暴露 `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupJson` 与 Console `Sub ApHRvRoll`，由 Binder service sample 代理 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup`；Linux 同步路径暴露 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollup`。

该差异面只涉及调用壳层差异：Android 使用 Binder/AIDL 方法名和 Console debug action，Linux 使用 CLI/Unix socket IPC/gRPC JSON contract sample；两端 payload 均固定 `EV-AHO-001..010`、`decision_rollup_complete=true`、`decision_rollup_consistent=true`、`source_reviewer_assignment_audit_bound=true`、`reviewer_assignment_audit_consistent=true`、`reviewer_assignment_decision_blocked=true`、`reviewer_assignment_decision_ready=false`、`assigned_reviewer_count=0`、`unassigned_reviewer_count=10`、`reviewer_assignments_persisted=false`、`reviewer_assignment_queue_updated=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。Android system service、Linux daemon、Driver/HAL、DDS/high-rate data plane 和虚拟化层均不在本增量开发范围内。

## EV-AHP Platform Delta

Event subscription activation approval decision owner handoff evidence acceptance closure handoff readiness summary 在 Android 与 Linux 上使用同一语义对象和同一 no-store/no-POST/no-side-effect 边界。Android 主路径暴露 `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummaryJson` 与 Console `Sub ApHReady`，由 Binder service sample 代理 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary`；Linux 同步路径暴露 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-summary`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.summary` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummary`。

该差异面只涉及调用壳层差异：Android 使用 Binder/AIDL 方法名和 Console debug action，Linux 使用 CLI/Unix socket IPC/gRPC JSON contract sample；两端 payload 均固定 `EV-AHP-001..010`、`closure_handoff_readiness_complete=true`、`closure_handoff_ready=false`、`handoff_ready=false`、`source_decision_rollup_bound=true`、`decision_rollup_consistent=true`、`reviewer_assignment_decision_blocked=true`、`assigned_reviewer_count=0`、`unassigned_reviewer_count=10`、`reviewer_assignments_persisted=false`、`reviewer_assignment_queue_updated=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。Android system service、Linux daemon、Driver/HAL、DDS/high-rate data plane 和虚拟化层均不在本增量开发范围内。

## EV-AHQ Platform Delta

Event subscription activation approval decision owner handoff evidence acceptance closure handoff readiness audit consistency 在 Android 与 Linux 上使用同一语义对象和同一 no-store/no-POST/no-queue/gate/broker/no-side-effect 边界。Android 主路径暴露 `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditConsistencyJson` 与 Console `Sub ApHReadyA`，由 Binder service sample 代理 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary/audit-consistency`；Linux 同步路径暴露 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-audit-consistency`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.audit.consistency` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditConsistency`。

该差异面只涉及调用壳层差异：Android 使用 Binder/AIDL 方法名和 Console debug action，Linux 使用 CLI/Unix socket IPC/gRPC JSON contract sample；两端 payload 均固定 `EV-AHQ-001..010`、`consistency_passed=true`、`source_closure_handoff_readiness_summary_bound=true`、`closure_handoff_dependency_count_consistent=true`、`closure_handoff_blocker_state_consistent=true`、`closure_handoff_ready=false`、`handoff_ready=false`、`assigned_reviewer_count=0`、`unassigned_reviewer_count=10`、`reviewer_assignments_persisted=false`、`reviewer_assignment_queue_updated=false`、`driver_development_triggered=false` 和 `virtualization_development_triggered=false`。Android system service、Linux daemon、Driver/HAL、DDS/high-rate data plane 和虚拟化层均不在本增量开发范围内。
