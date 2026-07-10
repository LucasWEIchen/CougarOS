# 车载中央大脑路线图与进展

更新时间：2026-07-10

## 长期任务拆解

| 阶段 | 目标 | 主要交付物 | 状态 |
| --- | --- | --- | --- |
| M0 | 建立方向、文档、原型骨架 | 产品设计、架构设计、资料纪要、Android 原型、mock NPU 后端 | 已完成 |
| M0.1 | PM 级需求拆解和接口设计 | 需求拆解、接口设计、KaKaClaw 参考产品概念映射 | 已完成 |
| A0 | 架构图需求基线化 | 需求矩阵、偏差表、疑点表、按图执行计划 | 已完成 |
| A1 | Uni Info Bus 语义接口 mock | Context/State/Event/Action/Service/Tool/Permission contract 与 client | Event + Action active mock；Event subscription lifecycle + transport readiness + decision matrix + activation checklist + callback/watch shape + cursor/replay storage + backpressure/QoS evidence + readiness rollup + activation evidence intake/status/retention checklist/decision status rollup/approval dry-run status/approval authority checklist/audit consistency/decision blocker rollup/decision dry-run request contract/decision dry-run status/audit consistency/closure blocker matrix/owner handoff checklist/audit consistency；Android/Linux 主路径初版 |
| A2 | SOA 服务入口 mock | Business/Foundation/Atomic/Contract/Safety State | SOA invoke + service contract visibility 初版 |
| A3 | Runtime & Governance mock | Registry、Discovery、Schema、QoS、Policy、Lifecycle、Audit | active prototype + JSONL audit persistence sample + fixed-window QoS + `/governance/precheck` + `/governance/backend-contract` + `/governance/migration-check` + `/governance/deployment-plan` + Linux shared governance daemon runtime/audit diagnostics used by IPC/gRPC |
| A4 | Protocol Binding 分层 | REST 下沉为 binding，IPC/gRPC/MQTT/SOME-IP/DDS stub | Linux IPC active sample with shared governance precheck/runtime/audit direct diagnostics + backend contract/migration/deployment/binding readiness visibility + fallback；Linux gRPC/RPC JSON contract sample with same shared diagnostics；Android Binder service stub sample；Android Console Binder path；Android system service integration note；Event semantic mapping |
| A5 | Native adapters mock | AIOS Kernel、Service Adapter、Vehicle Signal、Model Runtime Adapter | adapter registry 初版 + Vehicle Signal catalog/activation/validation contract |
| A6 | Kernel/HAL/NPU 设计落地 | Driver/HAL/NPU runtime design、PCIe 接入路径 | NPU runtime interface + driver gap backlog + hardware empty-interface registry contract + hardware activation checklist + owner decision status + owner evidence intake/status/retention-closure/replacement-trigger/selected-adapter readiness checklist + adapter-load blocker rollup + adapter-load dry-run + dry-run no-store status + dry-run audit consistency + adapter-load approval authority checklist + approval authority no-store status + approval authority audit consistency + approval decision dry-run + approval decision dry-run no-store status + approval decision dry-run audit consistency + approval decision closure blocker matrix + approval decision reviewer matrix + approval reviewer evidence handoff checklist + approval reviewer evidence handoff acceptance status + approval reviewer evidence handoff acceptance audit consistency |
| A6.1 | 驱动接口支持矩阵 | Android/Linux 驱动能力、缺口、最小新增开发量 | 初版完成 + `/native/driver-gaps` + `/hardware/interfaces` + `/hardware/interfaces/activation-checklist` + `/hardware/interfaces/owner-decision-status` + `/hardware/interfaces/owner-decision-evidence` + `/hardware/interfaces/owner-decision-evidence/status` + `/hardware/interfaces/owner-decision-evidence/retention-checklist` + `/hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist` + `/hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist` + `/hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup` + `/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run` + `/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status` + `/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/audit-consistency` + `/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist` + `/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/status` + `/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/audit-consistency` + `/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run` + `/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/status` + `/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/audit-consistency` + `/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix` + `/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix` + `/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist` + `/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status` + `/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/audit-consistency` |
| A7 | Hypervisor/Safety 接口约束 | ASIL/QM domain map、跨 VM 通信假设；不开发虚拟化 | 接口约束初版 |
| A8 | 应用层扩展 | 座舱、Agent、Cluster/TBOX、ADAS、诊断视图 | AI SDK/Agent plan + execute/Skill/Memory contract mock + Android Console Binder debug path |
| A9 | Android/Linux 双平台交付 | Android APK/SDK sample、Linux CLI/daemon sample、平台差异说明 | Android system service integration note + Linux systemd 与平台差异初版 + Linux systemd hardening check + Linux package profile check + `/delivery/readiness` + `/prototype/readiness` |

## M0 任务清单

- [x] 读取并归档用户提供的软件架构图。
- [x] 调研 AAOS/VHAL/AIDL/VSS/SOME-IP/DDS/NPU 接入资料。
- [x] 核对现有 Android SDK、AVD、Git、OpenCLAW 环境。
- [x] 创建长期任务分支。
- [x] 编写产品设计文档初版。
- [x] 编写软件架构设计文档初版。
- [x] 编写资料纪要。
- [x] 创建 Android Console 原型。
- [x] 创建 mock NPU 后端。
- [x] 构建 Android APK。
- [x] 启动 mock 后端。
- [x] 安装 APK 到本地模拟器。
- [x] 验证 App 访问 `/health` 返回 `Health HTTP 200`。
- [x] 验证 App 触发 `/ai/infer` 返回 `Inference HTTP 200`。
- [x] 形成 M0 Git 提交。

## 当前工程策略

- 不把现有 APK 逆向产物作为本任务第一阶段的修改对象。
- 所有新系统代码放在 `central-brain/`。
- 构建和运行脚本放在 `tools/`。
- 文档放在 `docs/CENTRAL_BRAIN_*`。
- 后续每完成一个可运行增量，都创建 Git 提交。
- 架构图是最高优先级需求基线；所有产品参考、接口扩展和 mock 实现都必须映射回图中模块。
- 新增或保留任何软件偏差，必须同步更新 `docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md`。
- 发现图中边界不清或工程风险，必须同步更新 `docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md`。

## 最近进展

### 2026-07-10

- 推进 FW-U-003/NV-P-006 Event subscription activation approval decision owner handoff evidence acceptance closure readiness decision reviewer assignment audit consistency contract：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency`，用于只读校验 `EV-AHN-001..010` reviewer assignment checklist、assignment count、blocked state、no-store、no-POST、no-queue/gate/broker、Android/Linux parity 和 no-side-effect 是否一致。
  - Android 主路径新增 Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistencyJson` 与 Console `Sub ApHRvA`；Linux 同步路径新增 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-consistency`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.consistency` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistency`。
  - 本轮只做 contract-only read-only reviewer assignment audit consistency，不分配 reviewer，不接受 packet，不附加 evidence，不调用 POST，不持久化 request/result/approval/handoff/review/evidence/reviewer state，不创建 evidence store、result store 或 review queue，不关闭 gate，不激活 broker/DDS/high-rate data plane，不开发 Driver/HAL 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。

- 推进 FW-U-003/NV-P-006 Event subscription activation approval decision owner handoff evidence acceptance closure readiness decision reviewer assignment checklist contract：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist`，用于只读列出 `EV-AHM-001..010` closure readiness decision reviewer assignment gates，确认 EV-AHL decision rollup 已绑定，但 reviewer assignment 仍全部 unassigned。
  - Android 主路径新增 Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentChecklistJson` 与 Console `Sub ApHRev`；Linux 同步路径新增 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-checklist`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.checklist` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentChecklist`。
  - 本轮只做 contract-only read-only reviewer assignment checklist，不分配 reviewer，不接受 packet，不附加 evidence，不调用 POST，不持久化 request/result/approval/handoff/review/evidence/reviewer state，不创建 evidence store、result store 或 review queue，不关闭 gate，不激活 broker/DDS/high-rate data plane，不开发 Driver/HAL 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。

### 2026-07-09

- 推进 FW-U-003/NV-P-006 Event subscription activation approval decision owner handoff evidence acceptance closure readiness decision rollup contract：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup`，用于只读汇总 `EV-AHL-001..010` closure readiness decision gates，确认 EV-AHK audit consistency、EV-AHJ closure readiness checklist、EV-AHI acceptance decision rollup、Android/Linux parity、no-store、no-POST 和 no-side-effect 决策状态一致，但 closure decision 仍保持 blocked。
  - Android 主路径新增 Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionRollupJson` 与 Console `Sub ApHClR`；Linux 同步路径新增 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-rollup`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.rollup` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionRollup`。
  - 本轮只做 contract-only read-only decision rollup，不接受 packet，不附加 evidence，不分配 owner，不调用 POST，不创建或持久化 evidence/acceptance/review/result/approval/handoff state，不关闭 gate，不激活 broker/DDS/high-rate data plane，不开发 Driver/HAL 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。

- 推进 FW-U-003/NV-P-006 Event subscription activation approval decision owner handoff evidence acceptance closure readiness audit consistency contract：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency`，用于只读核对 `EV-AHK-001..010` closure readiness audit consistency，确认 `EV-AHJ` closure readiness checklist 与 `EV-AHI` decision rollup、`EV-AHH` acceptance audit、`EV-AHG` acceptance status、`EV-AHE` evidence readiness matrix 的计数、source binding、Android/Linux parity、no-store、no-POST 和 no-side-effect 一致。
  - Android 主路径新增 Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessAuditConsistencyJson` 与 Console `Sub ApHClAud`；Linux 同步路径新增 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-audit-consistency`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.audit.consistency` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessAuditConsistency`。
  - 本轮只做 contract-only read-only audit consistency，不接受 packet，不附加 evidence，不分配 owner，不调用 POST，不创建 evidence/acceptance/review/result store，不关闭 gate，不激活 broker/DDS/high-rate data plane，不开发 Driver/HAL 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。

- 推进 FW-U-003/NV-P-006 Event subscription activation approval decision owner handoff evidence acceptance audit consistency contract：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency`，用于在 `EV-AHG-001..010` acceptance status 之后只读核对 `EV-AHH-001..010` audit consistency，确认 acceptance status、readiness audit、Android/Linux parity、no-store、no-POST 和 no-side-effect 计数一致。
  - Android Binder/AIDL 新增 `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceAuditConsistencyJson`，Android Console 新增 `Sub ApHAcAud` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-audit-consistency`、`uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.audit.consistency`、`GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceAuditConsistency` 可见路径。
  - 本轮只完成 handoff evidence acceptance audit consistency 只读视图，不接受 packet，不附加 evidence，不分配 owner，不调用 decision dry-run POST，不持久化 evidence/request/result/approval/handoff/review state，不创建 evidence store、approval result store 或 review queue，不关闭 gate，不允许 broker activation，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。

- 推进 FW-U-003/NV-P-006 Event subscription activation approval decision owner handoff evidence acceptance status contract：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status`，用于在 `EV-AHF-001..010` audit consistency 之后只读报告 `EV-AHG-001..010` evidence packet acceptance 状态，并固定所有 packet 因缺少 URI、hash、owner signature、evidence store、review queue 和 gate closure authority 而保持 blocked。
  - Android Binder/AIDL 新增 `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceStatusJson`，Android Console 新增 `Sub ApHAcc` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-status`、`uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.status`、`GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceStatus` 可见路径。
  - 本轮只完成 handoff evidence acceptance status 只读视图，不接受 packet，不附加 evidence，不分配 owner，不调用 decision dry-run POST，不持久化 evidence/request/result/approval/handoff/review state，不创建 evidence store、approval result store 或 review queue，不关闭 gate，不允许 broker activation，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。

- 推进 FW-U-003/NV-P-006 Event subscription activation approval decision owner handoff evidence readiness audit consistency contract：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency`，用于只读核对 `EV-AHE-001..010` evidence packets 与 owner handoff decision rollup 的数量、source binding、Android/Linux parity、no-store、no-POST、Driver/HAL gap reference 和 no-side-effect 一致性，并固定 `EV-AHF-001..010` audit gates。
  - Android Binder/AIDL 新增 `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessAuditConsistencyJson`，Android Console 新增 `Sub ApHEvAud` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-activation-approval-decision-owner-handoff-evidence-readiness-audit-consistency`、`uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.readiness.audit.consistency`、`GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessAuditConsistency` 可见路径。
  - 本轮只完成 handoff evidence readiness audit consistency 只读视图，不分配 owner，不附加 evidence，不调用 decision dry-run POST，不持久化 evidence/request/result/approval/review state，不创建 evidence store 或 approval result store，不更新 review queue，不关闭 gate，不允许 broker activation，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。

- 推进 FW-U-003/NV-P-006 Event subscription activation approval decision owner handoff evidence readiness matrix contract：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix`，用于从 owner handoff decision rollup 只读派生 `EV-AHE-001..010` handoff evidence packets，列出 approval decision 进入 review 前仍缺失的 evidence URI、hash、owner signature、evidence store 和 review queue 条件。
  - Android Binder/AIDL 新增 `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessMatrixJson`，Android Console 新增 `Sub ApHEv` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-activation-approval-decision-owner-handoff-evidence-readiness-matrix`、`uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.readiness.matrix`、`GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessMatrix` 可见路径。
  - 本轮只完成 handoff evidence readiness matrix 只读视图，不分配 owner，不附加 evidence，不调用 decision dry-run POST，不持久化 evidence/request/result/approval/review state，不创建 evidence store 或 approval result store，不更新 review queue，不关闭 gate，不允许 broker activation，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。

- 推进 FW-U-003/NV-P-006 Event subscription activation approval decision owner handoff decision rollup contract：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup`，用于从 owner handoff audit consistency 和 owner handoff checklist 只读汇总 approval decision 仍被未分配 owner、缺少 handoff evidence、无 review queue/result store、无 gate closure authority、以及 DRV-GAP-004/005 高频 transport 决策阻塞，并固定 `EV-AHD-001..010` decision gates。
  - Android Binder/AIDL 新增 `getEventSubscriptionActivationApprovalDecisionOwnerHandoffDecisionRollupJson`，Android Console 新增 `Sub ApHRoll` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-activation-approval-decision-owner-handoff-decision-rollup`、`uib.events.subscriptions.activation.approval.decision.owner.handoff.decision.rollup`、`GetEventSubscriptionActivationApprovalDecisionOwnerHandoffDecisionRollup` 可见路径。
  - 本轮只完成 activation approval decision owner handoff decision rollup 只读视图，不分配 owner，不附加 evidence，不调用 decision dry-run POST，不持久化 handoff/request/result/approval/review state，不创建 approval result store，不更新 review queue，不关闭 gate，不允许 broker activation，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。

- 推进 FW-U-003/NV-P-006 Event subscription activation approval decision owner handoff audit consistency contract：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency`，用于只读核对 `EV-ACH-001..010` owner handoff 与 `EV-ACB-001..010` closure blocker 的数量、source binding、open state、owner assignment、evidence attachment、no-store、Android/Linux parity、no-POST 和 no-side-effect 一致性，并固定 `EV-AHA-001..010` audit gates。
  - Android Binder/AIDL 新增 `getEventSubscriptionActivationApprovalDecisionOwnerHandoffAuditConsistencyJson`，Android Console 新增 `Sub ApHAud` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-activation-approval-decision-owner-handoff-audit-consistency`、`uib.events.subscriptions.activation.approval.decision.owner.handoff.audit.consistency`、`GetEventSubscriptionActivationApprovalDecisionOwnerHandoffAuditConsistency` 可见路径。
  - 本轮只完成 activation approval decision owner handoff audit consistency 只读视图，不分配 owner，不附加 evidence，不调用 decision dry-run POST，不持久化 handoff/request/result/approval/review state，不创建 approval result store，不更新 review queue，不关闭 gate，不允许 broker activation，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。

- 推进 FW-U-003/NV-P-006 Event subscription activation approval decision owner handoff checklist contract：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist`，用于从上一轮 `EV-ACB-001..010` closure blocker matrix 派生 `EV-ACH-001..010` owner handoff 槽位，列出 expected owner role、required evidence type、Android/Linux parity requirement 和 escalation state。
  - Android Binder/AIDL 新增 `getEventSubscriptionActivationApprovalDecisionOwnerHandoffChecklistJson`，Android Console 新增 `Sub ApHand` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-activation-approval-decision-owner-handoff-checklist`、`uib.events.subscriptions.activation.approval.decision.owner.handoff.checklist`、`GetEventSubscriptionActivationApprovalDecisionOwnerHandoffChecklist` 可见路径。
  - 本轮只完成 activation approval decision owner handoff checklist 只读视图，不分配 owner，不调用 decision dry-run POST，不持久化 handoff/request/result/approval decision，不创建 approval result store，不更新 review queue，不关闭 gate，不允许 broker activation，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。

- 推进 FW-U-003/NV-P-006 Event subscription activation approval decision closure blocker matrix contract：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix`，用于在 decision dry-run audit consistency 之后列明 approval authority、approval policy、signature/RBAC、approval result store、review queue owner、gate closure authority、broker activation owner、DRV-GAP-004/005 owner、Android/Linux closure parity evidence 和 high-rate transport activation evidence 十个 `EV-ACB-001..010` closure blockers。
  - Android Binder/AIDL 新增 `getEventSubscriptionActivationApprovalDecisionClosureBlockerMatrixJson`，Android Console 新增 `Sub ApClose` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-activation-approval-decision-closure-blocker-matrix`、`uib.events.subscriptions.activation.approval.decision.closure.blocker.matrix`、`GetEventSubscriptionActivationApprovalDecisionClosureBlockerMatrix` 可见路径。
  - 本轮只完成 activation approval decision closure blocker matrix 只读视图，不调用 decision dry-run POST，不持久化 request/result/approval decision，不创建 approval result store，不更新 review queue，不关闭 gate，不允许 broker activation，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。

- 推进 FW-U-003/NV-P-006 Event subscription activation approval decision dry-run audit consistency contract：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/audit-consistency`，用于只读交叉核对 decision blocker rollup、decision dry-run request contract、decision dry-run no-store status、Android/Linux parity、zero persisted counters 和 no-side-effect 边界，并固定 `EV-ADA-001..008` 门禁。
  - Android Binder/AIDL 新增 `getEventSubscriptionActivationApprovalDecisionDryRunAuditConsistencyJson`，Android Console 新增 `Sub ApDAudit` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-activation-approval-decision-dry-run-audit-consistency`、`uib.events.subscriptions.activation.approval.decision.dry.run.audit.consistency`、`GetEventSubscriptionActivationApprovalDecisionDryRunAuditConsistency` 可见路径。
  - 本轮只完成 activation approval decision dry-run audit consistency 只读视图，不调用 decision dry-run POST，不持久化 request/result/approval decision，不创建 approval result store，不更新 review queue，不关闭 gate，不允许 broker activation，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。

- 推进 FW-U-003/NV-P-006 Event subscription activation approval decision dry-run status contract：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/status`，用于在上一轮 blocked decision dry-run request contract 之后报告 no-store last-result/status、绑定 `EV-ADB-001..008` blocker rollup，并固定 `EV-ADS-001..008` 门禁结果。
  - Android Binder/AIDL 新增 `getEventSubscriptionActivationApprovalDecisionDryRunStatusJson`，Android Console 新增 `Sub ApDStat` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-activation-approval-decision-dry-run-status`、`uib.events.subscriptions.activation.approval.decision.dry.run.status`、`GetEventSubscriptionActivationApprovalDecisionDryRunStatus` 可见路径。
  - 本轮只完成 activation approval decision dry-run no-store status，不调用 decision dry-run POST，不持久化 last-result/request/result/approval decision，不创建 approval result store，不更新 review queue，不关闭 gate，不允许 broker activation，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。

- 推进 FW-U-003/NV-P-006 Event subscription activation approval decision dry-run request contract：
  - 新增 `POST /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run`，用于校验 activation approval decision dry-run 请求 envelope，绑定上一轮 `EV-ADB-001..008` blocker rollup，并固定返回 blocked/no-store 的 `EV-ADD-001..008` 门禁结果。
  - Android Binder/AIDL 新增 `dryRunEventSubscriptionActivationApprovalDecisionJson`，Android Console 新增 `Sub ApDec` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-activation-approval-decision-dry-run`、`uib.events.subscriptions.activation.approval.decision.dry.run`、`DryRunEventSubscriptionActivationApprovalDecision` 可见路径。
  - 本轮只完成 activation approval decision dry-run request contract，不持久化请求或结果，不创建 approval result store，不更新 review queue，不关闭 gate，不允许 broker activation，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。

- 推进 FW-U-003/NV-P-006 Event subscription activation approval decision blocker rollup contract：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-blocker-rollup`，用于在 approval authority audit consistency 之后只读汇总仍阻止真实 approval dry-run command、review queue、gate closure 和 broker activation 的 `EV-ADB-001..008` 决策阻塞项。
  - Android Binder/AIDL 新增 `getEventSubscriptionActivationApprovalDecisionBlockerRollupJson`，Android Console 新增 `Sub ApBlock` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-activation-approval-decision-blocker-rollup`、`uib.events.subscriptions.activation.approval.decision.blocker.rollup`、`GetEventSubscriptionActivationApprovalDecisionBlockerRollup` 可见路径。
  - 本轮只完成 activation approval decision blocker rollup 只读视图，不持久化 approval decision，不分配 approval authority，不创建 approval result store，不更新 review queue，不关闭 gate，不允许 broker activation，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。

- 推进 FW-U-003/NV-P-006 Event subscription activation approval authority audit consistency contract：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/audit-consistency`，用于只读核对 approval authority checklist、approval dry-run status、activation evidence decision status rollup、authority item/blocker counters、Android/Linux parity 和 no-store/no-side-effect 约束，并固定 `EV-AAC-001..008` 门禁。
  - Android Binder/AIDL 新增 `getEventSubscriptionActivationApprovalAuthorityAuditConsistencyJson`，Android Console 新增 `Sub ApAudit` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-activation-approval-authority-audit-consistency`、`uib.events.subscriptions.activation.approval.authority.audit.consistency`、`GetEventSubscriptionActivationApprovalAuthorityAuditConsistency` 可见路径。
  - 本轮只完成 activation approval authority audit consistency 只读视图，不调用 approval dry-run POST，不分配 approval authority，不创建 approval result store，不更新 review queue，不关闭 gate，不允许 broker activation，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。

- 推进 FW-U-003/NV-P-006 Event subscription activation approval authority checklist contract：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist`，用于在任何真实 approval dry-run command 前列明 approval authority、approval policy、signature/RBAC、approval result store、review queue、gate closure authority、broker activation owner、DRV-GAP-004/005 owner 和 `EV-AAA-001..008` 门禁。
  - Android Binder/AIDL 新增 `getEventSubscriptionActivationApprovalAuthorityChecklistJson`，Android Console 新增 `Sub ApAuth` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-activation-approval-authority-checklist`、`uib.events.subscriptions.activation.approval.authority.checklist`、`GetEventSubscriptionActivationApprovalAuthorityChecklist` 可见路径。
  - 本轮只完成 activation approval authority checklist 只读视图，不调用 approval dry-run POST，不分配 approval authority，不创建 approval result store，不更新 review queue，不关闭 gate，不允许 broker activation，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。

- 推进 FW-U-003/NV-P-006 Event subscription activation approval dry-run status contract：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/approval-dry-run/status`，用于在真实 approval dry-run request/workflow 前报告 no-store status、last-result shape、source decision status rollup 绑定和 `EV-AAS-001..008` 门禁。
  - Android Binder/AIDL 新增 `getEventSubscriptionActivationApprovalDryRunStatusJson`，Android Console 新增 `Sub ApStat` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-activation-approval-dry-run-status`、`uib.events.subscriptions.activation.approval.dry.run.status`、`GetEventSubscriptionActivationApprovalDryRunStatus` 可见路径。
  - 本轮只完成 activation approval dry-run no-store status 只读视图，不调用 approval dry-run POST，不持久化请求或结果，不创建 result store/evidence store，不更新 review queue，不关闭 gate，不允许 broker activation，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。

- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load closure handoff readiness summary contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary`，用于把 reviewer assignment audit decision rollup 后仍未闭合的 handoff 依赖汇总为只读 readiness summary，并固定 `HW-AHK-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummaryJson`，Android Console 新增 `HW ApHReady` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-summary`、`hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.summary`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummary` 可见路径。
  - 本轮只完成 closure handoff readiness summary 只读视图，不分配 reviewer，不接收或接受 handoff packet，不持久化 acceptance record/approval/evidence/reviewer assignment，不创建 evidence store，不更新 review queue，不关闭 gate，不选择 adapter，不加载 adapter，不激活 adapter，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。

- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load approval reviewer evidence handoff acceptance closure decision reviewer assignment audit decision rollup contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup`，用于把 closure decision reviewer assignment audit consistency 的结论汇总为 adapter load 仍被未分配 reviewer 阻塞的只读决策，并固定 `HW-AHJ-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupJson`，Android Console 新增 `HW ApHRvRoll` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup`、`hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollup` 可见路径。
  - 本轮只完成 closure decision reviewer assignment audit decision rollup 只读视图，不分配 reviewer，不接收或接受 handoff packet，不持久化 acceptance record/approval/evidence/reviewer assignment，不创建 evidence store，不更新 review queue，不关闭 gate，不选择 adapter，不加载 adapter，不激活 adapter，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。

- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load approval reviewer evidence handoff acceptance closure decision reviewer assignment audit consistency contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency`，用于校验 closure decision reviewer assignment checklist、assignment count、blocker state、no-store、no-review/gate/load、Android/Linux parity 和 no-side-effect 是否一致，并固定 `HW-AHI-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistencyJson`，Android Console 新增 `HW ApHRvA` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-consistency`、`hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.reviewer.assignment.audit.consistency`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistency` 可见路径。
  - 本轮只完成 closure decision reviewer assignment audit consistency 只读视图，不分配 reviewer，不接收或接受 handoff packet，不持久化 acceptance record/approval/evidence/reviewer assignment，不创建 evidence store，不更新 review queue，不关闭 gate，不选择 adapter，不加载 adapter，不激活 adapter，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。

- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load approval reviewer evidence handoff acceptance closure decision reviewer assignment checklist contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist`，用于把 closure readiness decision rollup 后仍需确认的 reviewer assignment 拆成 8 个未分配项，并固定 `HW-AHH-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentChecklistJson`，Android Console 新增 `HW ApHCRev` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-checklist`、`hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.reviewer.assignment.checklist`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentChecklist` 可见路径。
  - 本轮只完成 closure decision reviewer assignment checklist 只读视图，不分配 reviewer，不接收或接受 handoff packet，不持久化 acceptance record/approval/evidence，不创建 evidence store，不更新 review queue，不关闭 gate，不选择 adapter，不加载 adapter，不激活 adapter，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。

### 2026-07-08

- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load approval reviewer evidence handoff acceptance closure readiness decision rollup contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup`，用于把 closure readiness checklist、closure readiness audit consistency 和 acceptance decision rollup 汇总为明确的 closure decision blocker，并固定 `HW-AHG-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionRollupJson`，Android Console 新增 `HW ApHCRoll` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-rollup`、`hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.decision.rollup`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionRollup` 可见路径。
  - 本轮只完成 approval reviewer evidence handoff acceptance closure readiness decision rollup 只读视图，不接收或接受 handoff packet，不持久化 acceptance record/approval/evidence，不创建 evidence store，不更新 review queue，不关闭 gate，不选择 adapter，不加载 adapter，不激活 adapter，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load approval reviewer evidence handoff acceptance closure readiness audit consistency contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency`，用于交叉校验 closure readiness checklist、closure blocker state、decision rollup、no-store/no-review-gate-load 约束、Android/Linux parity 与 no-side-effect 计数，并固定 `HW-AHF-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessAuditConsistencyJson`，Android Console 新增 `HW ApHCAud` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-audit-consistency`、`hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.audit.consistency`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessAuditConsistency` 可见路径。
  - 本轮只完成 approval reviewer evidence handoff acceptance closure readiness audit consistency 只读视图，不接收或接受 handoff packet，不持久化 acceptance record/approval/evidence，不创建 evidence store，不更新 review queue，不关闭 gate，不选择 adapter，不加载 adapter，不激活 adapter，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load approval reviewer evidence handoff acceptance closure readiness checklist contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist`，用于把 acceptance authority、acceptance record store、review workflow、audit retention、rollback/fault、Driver/HAL acceptance、gate closure authority 与 handoff packet presence 的 closure readiness 条件汇总为机器可读阻塞项，并固定 `HW-AHE-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessChecklistJson`，Android Console 新增 `HW ApHClose` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-checklist`、`hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.closure.readiness.checklist`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessChecklist` 可见路径。
  - 本轮只完成 approval reviewer evidence handoff acceptance closure readiness checklist 只读视图，不接收或接受 handoff packet，不持久化 acceptance record/approval/evidence，不创建 evidence store，不更新 review queue，不关闭 gate，不选择 adapter，不加载 adapter，不激活 adapter，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load approval reviewer evidence handoff acceptance decision rollup contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup`，用于把 acceptance authority、acceptance record store、review workflow、audit retention、rollback/fault、Driver/HAL acceptance reviewer、gate closure authority 与 handoff packet presence 的未确认决策汇总为机器可读阻塞项，并固定 `HW-AHD-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceDecisionRollupJson`，Android Console 新增 `HW ApHRoll` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-decision-rollup`、`hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.decision.rollup`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceDecisionRollup` 可见路径。
  - 本轮只完成 approval reviewer evidence handoff acceptance decision rollup 只读视图，不接收或接受 handoff packet，不持久化 acceptance record，不创建 evidence store，不更新 review queue，不关闭 gate，不选择 adapter，不加载 adapter，不激活 adapter，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load approval reviewer evidence handoff acceptance audit consistency contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/audit-consistency`，用于交叉校验 handoff checklist、acceptance status、Android/Linux parity 与 no-side-effect 计数，并固定 `HW-AHC-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceAuditConsistencyJson`，Android Console 新增 `HW ApHAud` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-audit-consistency`、`hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.audit.consistency`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceAuditConsistency` 可见路径。
  - 本轮只完成 approval reviewer evidence handoff acceptance audit consistency 只读视图，不接收或接受 handoff packet，不持久化 acceptance record，不创建 evidence store，不更新 review queue，不关闭 gate，不选择 adapter，不加载 adapter，不激活 adapter，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load approval reviewer evidence handoff acceptance status contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status`，用于把 approval reviewer evidence handoff packet 的 acceptance 状态记录为机器可读阻塞项，并固定 `HW-AHA-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceStatusJson`，Android Console 新增 `HW ApHStat` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-status`、`hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.acceptance.status`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceStatus` 可见路径。
  - 本轮只完成 approval reviewer evidence handoff acceptance status 只读视图，不接收 handoff packet acceptance，不持久化 acceptance record，不创建 evidence store，不更新 review queue，不关闭 gate，不选择 adapter，不加载 adapter，不激活 adapter，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load approval reviewer evidence handoff checklist contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist`，用于把 reviewer identity、source blocker reference、evidence URI、owner signature、acceptance rule、retention policy、audit export 和 rollback/fault note 等 handoff packet 字段记录为机器可读缺失项，并固定 `HW-ARH-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffChecklistJson`，Android Console 新增 `HW ApHand` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-checklist`、`hardware.interfaces.owner.decision.evidence.adapter.load.approval.reviewer.evidence.handoff.checklist`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffChecklist` 可见路径。
  - 本轮只完成 approval reviewer evidence handoff checklist 只读视图，不分配 reviewer，不附加或持久化 evidence，不创建 evidence store，不更新 review queue，不关闭 gate，不选择 adapter，不加载 adapter，不激活 adapter，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load approval decision reviewer matrix contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix`，用于把 approval authority、approval policy、signature/RBAC、approval record schema、approval evidence store、review workflow、target smoke、rollback/fault、Driver/HAL gap、audit export 和 gate closure reviewer 记录为机器可读未分配项，并固定 `HW-APR-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionReviewerMatrixJson`，Android Console 新增 `HW ApRev` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-approval-decision-reviewer-matrix`、`hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.reviewer.matrix`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionReviewerMatrix` 可见路径。
  - 本轮只完成 approval decision reviewer matrix 只读视图，不分配 reviewer，不调用 dry-run POST，不持久化 approval decision，不创建 evidence store，不更新 review queue，不关闭 gate，不选择 adapter，不加载 adapter，不激活 adapter，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load approval decision closure blocker matrix contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix`，用于把 approval authority、approval policy、owner signature source、RBAC mapping、approval record schema、approval evidence store owner、review workflow、target smoke evidence、rollback plan、fault model、Driver/HAL gap closure evidence、audit owner 和 gate closure authority 记录为机器可读开放阻塞项，并固定 `HW-APM-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionClosureBlockerMatrixJson`，Android Console 新增 `HW ApBlock` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-approval-decision-closure-blocker-matrix`、`hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.closure.blocker.matrix`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionClosureBlockerMatrix` 可见路径。
  - 本轮只完成 approval decision closure blocker matrix 只读视图，不调用 dry-run POST，不持久化 approval decision，不创建 evidence store，不更新 review queue，不关闭 gate，不选择 adapter，不加载 adapter，不激活 adapter，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load approval decision dry-run audit consistency contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/audit-consistency`，用于只读交叉核对 approval decision dry-run/status、approval authority audit/status 和 adapter-load blocker rollup，并固定 `HW-APA-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunAuditConsistencyJson`，Android Console 新增 `HW ApDAudit` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run-audit-consistency`、`hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.dry.run.audit.consistency`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunAuditConsistency` 可见路径。
  - 本轮只完成 approval decision dry-run audit consistency 只读视图，不调用 dry-run POST，不持久化 approval decision，不创建 evidence store，不更新 review queue，不关闭 gate，不选择 adapter，不加载 adapter，不激活 adapter，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load approval decision dry-run status contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/status`，用于只读报告 approval decision dry-run 的 no-store 状态、last approval decision result 不可用、approval decision 持久化计数为 0、无 review queue、无 evidence store，并固定 `HW-APS-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunStatusJson`，Android Console 新增 `HW ApDStat` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run-status`、`hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.dry.run.status`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunStatus` 可见路径。
  - 本轮只完成 approval decision dry-run no-store status 只读视图，不调用 dry-run POST，不持久化 approval decision，不创建 evidence store，不更新 review queue，不关闭 gate，不选择 adapter，不加载 adapter，不激活 adapter，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load approval decision dry-run contract：
  - 新增 `POST /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run`，用于校验 selected interface、selected adapter、adapter version、approval decision、approval authority/signature、requested_by 与 approval evidence refs 的请求形状，并强制绑定 approval checklist/status/audit 与 adapter-load blocker rollup 的开放阻塞项。
  - Android Binder/AIDL 新增 `dryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionJson`，Android Console 新增 `HW ApDec` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run`、`hardware.interfaces.owner.decision.evidence.adapter.load.approval.decision.dry.run`、`DryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecision` 可见路径。
  - 本轮只完成 approval decision dry-run request contract，返回 `rejected_blocked_contract_only`，不持久化 approval record 或 decision，不创建 evidence store，不更新 review queue，不关闭 gate，不选择 adapter，不加载 adapter，不激活 adapter，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load approval authority audit consistency contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/audit-consistency`，用于只读核对 approval authority checklist、approval no-store status、dry-run audit consistency 和 adapter-load blocker rollup 是否共同保持 no-store/no-load/no-hardware-access 状态，并固定 `HW-AAC-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistencyJson`，Android Console 新增 `HW ApAudit` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-approval-authority-audit-consistency`、`hardware.interfaces.owner.decision.evidence.adapter.load.approval.authority.audit.consistency`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistency` 可见路径。
  - 本轮只完成 approval authority audit consistency 只读视图，不调用 dry-run POST，不持久化 approval record，不创建 evidence store，不更新 review queue，不选择 adapter，不加载 adapter，不激活 adapter，不关闭 gate，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load approval authority no-store status contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/status`，用于只读报告 approval authority checklist 之后仍没有 approval record、review queue、approval evidence store、gate closure 或 adapter load 的状态，并固定 `HW-AAS-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatusJson`，Android Console 新增 `HW ApStat` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-approval-authority-status`、`hardware.interfaces.owner.decision.evidence.adapter.load.approval.authority.status`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatus` 可见路径。
  - 本轮只完成 approval authority no-store status 只读视图，不调用 dry-run POST，不持久化 approval record，不创建 evidence store，不更新 review queue，不选择 adapter，不加载 adapter，不激活 adapter，不关闭 gate，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load approval authority checklist contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist`，用于只读列出 adapter-load 从 dry-run 进入真实 approval/load 前必须确认的 approval authority、approval policy、signature/RBAC、durable evidence workflow、target smoke/rollback/fault evidence 与 `HW-ALA-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklistJson`，Android Console 新增 `HW Approve` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-approval-authority-checklist`、`hardware.interfaces.owner.decision.evidence.adapter.load.approval.authority.checklist`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklist` 可见路径。
  - 本轮只完成 approval authority checklist 只读视图，不调用 dry-run POST，不持久化 approval，不创建 evidence store，不更新 review queue，不选择 adapter，不加载 adapter，不激活 adapter，不关闭 gate，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 FW-U-003/NV-P-006 Event subscription activation evidence decision status rollup contract：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/decision-status-rollup`，用于只读汇总 readiness rollup、activation evidence intake/status、retention checklist 与 `EV-AED-001..008` 决策门禁。
  - Android Binder/AIDL 新增 `getEventSubscriptionActivationEvidenceDecisionStatusRollupJson`，Android Console 新增 `Sub Decide` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-activation-evidence-decision-status-rollup`、`uib.events.subscriptions.activation.evidence.decision.status.rollup`、`GetEventSubscriptionActivationEvidenceDecisionStatusRollup` 可见路径。
  - 本轮只完成 activation evidence decision status rollup，不调用 activation evidence POST，不持久化 evidence，不读取 evidence store，不创建 review queue，不创建 delete/export workflow，不关闭 gate，不允许 broker activation，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load dry-run audit consistency contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/audit-consistency`，用于只读核对 adapter-load dry-run、dry-run/status、adapter-load blocker rollup 与 `HW-ALB`/`HW-ALD`/`HW-ALS`/`HW-ALC` gate family 是否一致。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistencyJson`，Android Console 新增 `HW DryAudit` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-dry-run-audit-consistency`、`hardware.interfaces.owner.decision.evidence.adapter.load.dry.run.audit.consistency`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistency` 可见路径。
  - 本轮只完成 dry-run audit consistency 只读视图，不调用 dry-run POST，不保存 dry-run 请求或结果，不创建 evidence store，不更新 review queue，不选择 adapter，不加载 adapter，不激活 adapter，不关闭 gate，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load dry-run status contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status`，用于报告 adapter-load dry-run 的 no-store 状态、last-result shape、`persisted_dry_run_count=0`、`last_result_available=false`、`review_queue_updated=false` 与 blocker rollup 仍开放的事实。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatusJson`，Android Console 新增 `HW DryState` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-dry-run-status`、`hardware.interfaces.owner.decision.evidence.adapter.load.dry.run.status`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatus` 可见路径。
  - 本轮只完成 adapter-load dry-run status/last-result no-store view，不保存 dry-run 请求或结果，不创建 evidence store，不更新 review queue，不选择 adapter，不加载 adapter，不激活 adapter，不关闭 gate，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load dry-run contract：
  - 新增 `POST /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run`，用于校验 selected interface、selected adapter、adapter version、requested_by 与 evidence refs 的 approval dry-run 请求形状，并强制绑定 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup` 的开放阻塞项。
  - Android Binder/AIDL 新增 `dryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadJson`，Android Console 新增 `HW DryRun` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-dry-run`、`hardware.interfaces.owner.decision.evidence.adapter.load.dry.run`、`DryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoad` 可见路径。
  - 本轮只完成 adapter-load approval dry-run request contract，返回 `rejected_blocked_contract_only`，不选择 adapter，不加载 adapter，不替换 adapter，不激活 adapter，不关闭 gate，不持久化 evidence，不更新 review queue，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence adapter-load blocker rollup contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup`，用于聚合 activation checklist、owner decision status、owner evidence status、retention checklist、replacement trigger checklist 和 selected-adapter readiness checklist 中仍阻止 adapter load 的 `HW-ALB-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollupJson`，Android Console 新增 `HW Load` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-adapter-load-blocker-rollup`、`hardware.interfaces.owner.decision.evidence.adapter.load.blocker.rollup`、`GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollup` 可见路径。
  - 本轮只完成 adapter-load blocker rollup contract，不选择 adapter，不加载 adapter，不替换 adapter，不激活 adapter，不关闭 gate，不创建 evidence store，不读取或 dereference evidence URI，不创建 review queue，不分配 owner，不允许 activation，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence selected-adapter readiness checklist contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist`，用于在目标平台提出真实 adapter 候选后、任何 adapter load/activation 前固定 adapter owner、adapter interface contract、Driver/HAL gap evidence、Android/Linux binding parity、Safety/Policy fault model、smoke harness plan、rollback-to-empty-interface review 与 `HW-OEA-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklistJson`，Android Console 新增 `HW Adapter` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-selected-adapter-readiness-checklist`、`hardware.interfaces.owner.decision.evidence.selected.adapter.readiness.checklist`、`GetHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklist` 可见路径。
  - 本轮只完成 selected-adapter readiness checklist contract，不选择 adapter，不加载 adapter，不激活 adapter，不关闭 gate，不创建 evidence store，不读取或 dereference evidence URI，不创建 review queue，不分配 owner，不允许 activation，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence replacement trigger checklist contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist`，用于在真实 adapter 替换硬件空接口前固定 replacement target、adapter readiness criteria、Driver/HAL gap closure evidence、Android/Linux ABI replacement parity、rollback-to-empty-interface plan、Safety/Policy review、smoke harness evidence 与 `HW-OET-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklistJson`，Android Console 新增 `HW Replace` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-replacement-trigger-checklist`、`hardware.interfaces.owner.decision.evidence.replacement.trigger.checklist`、`GetHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklist` 可见路径。
  - 本轮只完成 replacement trigger checklist contract，不替换 adapter，不激活 adapter，不关闭 gate，不创建 evidence store，不读取或 dereference evidence URI，不创建 review queue，不创建 delete/export workflow，不分配 owner，不允许 activation，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。

### 2026-07-07

- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence retention and closure checklist contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/retention-checklist`，用于在真实 hardware evidence store 和 gate closure workflow 前固定 durable evidence store owner、URI rules、retention policy owner、review workflow owner、gate closure authority、delete/export semantics、rollback/fault closure evidence 与 `HW-OER-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceRetentionChecklistJson`，Android Console 新增 `HW Retain` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-retention-checklist`、`hardware.interfaces.owner.decision.evidence.retention.checklist`、`GetHardwareInterfaceOwnerDecisionEvidenceRetentionChecklist` 可见路径。
  - 本轮只完成 owner evidence retention/closure checklist contract，不创建 durable evidence store，不读取或 dereference evidence URI，不创建 review queue，不创建 delete/export workflow，不分配 owner，不关闭 gate，不允许 activation，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence status contract：
  - 新增 `GET /hardware/interfaces/owner-decision-evidence/status`，用于查询硬件 owner evidence intake 之后的 no-store/no-review status，返回 `HW-OES-001..008` 门禁、evidence store/review workflow/gate closure authority 待定项、`persisted_submission_count=0`、`pending_review_count=0`、`evidence_store_active=false` 和 `review_workflow_active=false`。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionEvidenceStatusJson`，Android Console 新增 `HW EvStatus` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence-status`、`hardware.interfaces.owner.decision.evidence.status`、`GetHardwareInterfaceOwnerDecisionEvidenceStatus` 可见路径。
  - 本轮只完成 owner evidence no-store status rollup，不读取 evidence store，不创建 review queue，不分配 owner，不关闭 gate，不允许 activation，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision evidence intake contract：
  - 新增 `POST /hardware/interfaces/owner-decision-evidence`，用于提交硬件空接口 owner/ABI/Driver-HAL/Safety/smoke/rollback gate 的 evidence reference envelope，返回 `HW-ODE-001..008` 门禁和 `validated_contract_only`/`rejected_missing_evidence`/`rejected_by_policy` intake 状态。
  - Android Binder/AIDL 新增 `submitHardwareInterfaceOwnerDecisionEvidenceJson`，Android Console 新增 `HW Evidence` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-evidence`、`hardware.interfaces.owner.decision.evidence`、`SubmitHardwareInterfaceOwnerDecisionEvidence` 可见路径。
  - 本轮只完成 owner decision evidence reference intake，不持久化 evidence，不更新 review queue，不分配 owner，不关闭 activation gate，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface owner decision status rollup contract：
  - 新增 `GET /hardware/interfaces/owner-decision-status`，用于聚合真实 PCIe NPU、Vehicle bus、Camera/Audio/Sensors、Ethernet/SOME-IP/DDS/TSN、shared memory 与 Safety Runtime 接入前仍未关闭的 target owner、Android ABI owner、Linux ABI owner、Driver/HAL gap owner、Safety/Policy owner、target smoke evidence owner 和 rollback/fault semantics owner 决策。
  - Android Binder/AIDL 新增 `getHardwareInterfaceOwnerDecisionStatusJson`，Android Console 新增 `HW Owner` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-owner-decision-status`、`hardware.interfaces.owner.decision.status`、`GetHardwareInterfaceOwnerDecisionStatus` 可见路径。
  - 本轮只完成 `HW-ODS-001..008` owner decision status rollup，不分配量产 owner，不关闭 activation gate，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 HW-002/KH-003/KH-006/KH-007 hardware interface activation checklist contract：
  - 新增 `GET /hardware/interfaces/activation-checklist`，用于在真实 PCIe NPU、Vehicle bus、Camera/Audio/Sensors、Ethernet/SOME-IP/DDS/TSN、shared memory 或 Safety Runtime 接入前固定 owner、Android ABI、Linux ABI、Driver/HAL gap review、Safety/Policy、smoke test harness、rollback/fault semantics 和 no-hardware-access 八类 `HW-ACT-001..008` 门禁。
  - Android Binder/AIDL 新增 `getHardwareInterfaceActivationChecklistJson`，Android Console 新增 `HW Gate` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interface-activation-checklist`、`hardware.interfaces.activation.checklist`、`GetHardwareInterfaceActivationChecklist` 可见路径。
  - 本轮只完成硬件激活前门禁 contract，不访问真实硬件，不打开 device node，不调用 HAL/vendor SDK，不分配 shared memory，不 dispatch service，不新增 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 FW-U-003/NV-P-006 Event subscription activation evidence retention and owner decision checklist contract：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/retention-checklist`，用于在真实 evidence store 前固定 evidence URI rules、durable store owner、retention policy owner、review workflow owner、gate closure authority、delete/export semantics 与 `EV-AER-001..008` 门禁。
  - Android Binder/AIDL 新增 `getEventSubscriptionActivationEvidenceRetentionChecklistJson`，Android Console 新增 `Sub Retain` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-activation-evidence-retention-checklist`、`uib.events.subscriptions.activation.evidence.retention.checklist`、`GetEventSubscriptionActivationEvidenceRetentionChecklist` 可见路径。
  - 本轮只完成 activation evidence retention/owner checklist contract，不创建 durable evidence store，不读取或 dereference evidence URI，不创建 delete/export workflow，不创建 review queue，不关闭 readiness gate，不允许 broker activation，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。
- 推进 FW-U-003/NV-P-006 Event subscription activation evidence review status contract：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/status`，用于查询 activation evidence intake 之后的 contract-only review status，返回 `EV-AES-001..006` 门禁、owner 待定项、`persisted_submission_count=0`、`pending_review_count=0`、`evidence_store_active=false` 和 `review_workflow_active=false`。
  - Android Binder/AIDL 新增 `getEventSubscriptionActivationEvidenceStatusJson`，Android Console 新增 `Sub Review` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-activation-evidence-status`、`uib.events.subscriptions.activation.evidence.status`、`GetEventSubscriptionActivationEvidenceStatus` 可见路径。
  - 本轮只完成 activation evidence review status contract，不读取 evidence store，不创建 review queue，不关闭 readiness gate，不允许 broker activation，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。
- 推进 FW-U-003/NV-P-006 Event subscription activation evidence intake contract：
  - 新增 `POST /uib/events/subscriptions/activation-evidence`，用于提交 broker/runtime activation gate 的 evidence reference envelope，返回 `EV-AE-001..008` 门禁和 `validated_contract_only`/`rejected_missing_evidence`/`rejected_by_policy` intake 状态。
  - Android Binder/AIDL 新增 `submitEventSubscriptionActivationEvidenceJson`，Android Console 新增 `Sub Evidence` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-activation-evidence`、`uib.events.subscriptions.activation.evidence`、`SubmitEventSubscriptionActivationEvidence` 可见路径。
  - 本轮只完成 activation evidence intake contract，不持久化 evidence，不更新 review queue，不关闭 readiness gate，不允许 broker activation，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。
- 推进 FW-U-003/NV-P-006 Event subscription end-to-end readiness rollup contract：
  - 新增 `GET /uib/events/subscriptions/readiness-rollup`，聚合 lifecycle、transport readiness、owner decision matrix、activation checklist、callback/watch shape、cursor/replay storage、backpressure/QoS evidence 的阻塞门禁，返回 `EV-RU-001..006` activation blockers 和 blocked gate summary。
  - Android Binder/AIDL 新增 `getEventSubscriptionReadinessRollupJson`，Android Console 新增 `Sub Ready` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-readiness-rollup`、`uib.events.subscriptions.readiness.rollup`、`GetEventSubscriptionReadinessRollup` 可见路径。
  - 本轮只完成 readiness rollup contract，不自动通过任何 gate，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。
- 推进 FW-U-003/NV-P-006 Event subscription backpressure/QoS evidence contract：
  - 新增 `GET /uib/events/subscriptions/backpressure-qos-evidence`，返回 overflow schema、per-caller throttling、per-topic limit、replay rate、ack timeout、Runtime & Governance QoS evidence binding、高频 transport QoS mapping 与 `EV-QOS-001..008` 门禁。
  - Android Binder/AIDL 新增 `getEventSubscriptionBackpressureQosEvidenceJson`，Android Console 新增 `Sub QoS` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-backpressure-qos-evidence`、`uib.events.subscriptions.backpressure.qos.evidence`、`GetEventSubscriptionBackpressureQosEvidence` 可见路径。
  - 本轮只完成 backpressure/QoS evidence contract，不激活事件 QoS，不发送 overflow，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。
- 推进 FW-U-003/NV-P-006 Event subscription cursor/replay storage contract：
  - 新增 `GET /uib/events/subscriptions/cursor-replay-storage`，返回 cursor schema、ack shape、replay window、retention/cleanup、Runtime & Governance audit binding 与 `EV-CRS-001..008` 门禁。
  - Android Binder/AIDL 新增 `getEventSubscriptionCursorReplayStorageJson`，Android Console 新增 `Sub Cursor` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-cursor-replay-storage`、`uib.events.subscriptions.cursor.replay.storage`、`GetEventSubscriptionCursorReplayStorage` 可见路径。
  - 本轮只完成 cursor/replay storage contract，不创建 cursor row，不建立 replay index，不持久化 subscription，不启动 broker、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。
- 推进 FW-U-003/NV-P-006 Event subscription callback/watch API shape contract：
  - 新增 `GET /uib/events/subscriptions/callback-watch-shape`，返回 Android planned Binder callback、Linux planned watch lifecycle、event/overflow/close envelope、Runtime & Governance binding、cursor/reconnect 与 no-runtime-registration 八类 `EV-CW-001..008` 门禁。
  - Android Binder/AIDL 新增 `getEventSubscriptionCallbackWatchShapeJson`，Android Console 新增 `Sub Shape` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-callback-watch-shape`、`uib.events.subscriptions.callback.watch.shape`、`GetEventSubscriptionCallbackWatchShape` 可见路径。
  - 本轮只完成 callback/watch API shape contract，不注册 Android callback，不启动 Linux watch stream，不启动真实订阅 broker、cursor store、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。
- 推进 FW-U-003/NV-P-006 Event subscription activation prerequisite evidence checklist contract：
  - 新增 `GET /uib/events/subscriptions/activation-checklist`，返回 broker owner evidence、Runtime & Governance binding、cursor store persistence、backpressure/QoS profile、transport runtime choice、Driver/HAL high-rate scope、Android/Linux parity 与 no-runtime-activation 八类 `EV-ACT-001..008` 门禁。
  - Android Binder/AIDL 新增 `getEventSubscriptionActivationChecklistJson`，Android Console 新增 `Sub Gate` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-activation-checklist`、`uib.events.subscriptions.activation.checklist`、`GetEventSubscriptionActivationChecklist` 可见路径。
  - 本轮只完成 broker activation 前置证据清单 contract，不允许 activation，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。
- 推进 FW-U-003/NV-P-006 Event subscription broker/cursor/backpressure decision matrix contract：
  - 新增 `GET /uib/events/subscriptions/decision-matrix`，返回 broker owner、cursor storage owner、backpressure/QoS owner、Android callback/Linux watch shape、transport selection 五类 `EV-DM-001..005` 决策项，以及 `EV-DM-006..007` Android/Linux parity 与 no-runtime-activation 门禁。
  - Android Binder/AIDL 新增 `getEventSubscriptionDecisionMatrixJson`，Android Console 新增 `Sub Matrix` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-decision-matrix`、`uib.events.subscriptions.decision.matrix`、`GetEventSubscriptionDecisionMatrix` 可见路径。
  - 本轮只完成 owner decision matrix contract，不分配量产 owner，不选择 transport，不启动真实订阅 broker、cursor store、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。
- 推进 FW-U-003/NV-P-006 Event subscription callback/watch transport readiness contract：
  - 新增 `GET /uib/events/subscriptions/transport-readiness`，返回 callback/watch lifecycle、broker owner、cursor storage owner、backpressure/QoS owner、SSE/WebSocket/DDS transport candidates 和 `EV-TR-001..006` 门禁。
  - Android Binder/AIDL 新增 `getEventSubscriptionTransportReadinessJson`，Android Console 新增 `Sub Link` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscription-transport-readiness`、`uib.events.subscriptions.transport.readiness`、`GetEventSubscriptionTransportReadiness` 可见路径。
  - 本轮只完成订阅 transport readiness contract，不选择 transport，不启动真实订阅 broker、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。
- 推进 FW-U-003/NV-P-006 Event subscription request/cancel lifecycle command contract：
  - 新增 `POST /uib/events/subscriptions/request` 与 `POST /uib/events/subscriptions/cancel`，返回 request/cancel lifecycle validation、Policy/Audit 结果和 no-persistence/no-broker/no-callback/no-DDS 边界。
  - Android Binder/AIDL 新增 `requestEventSubscriptionJson` 与 `cancelEventSubscriptionJson`，Android Console 新增 `Sub Req`/`Sub Cancel` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscribe-request`/`event-subscribe-cancel`、`uib.events.subscriptions.request`/`uib.events.subscriptions.cancel`、`RequestEventSubscription`/`CancelEventSubscription` 可见路径。
  - 本轮只完成订阅 request/cancel 生命周期命令契约，不持久化 active subscription，不注册 callback/watch，不启动真实订阅 broker、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002。
- 推进 Vehicle Signal read-bridge validation envelope contract：
  - `GET /vehicle/signals/validation` 新增 schema source metadata、Vehicle Signal Adapter owner、platform ABI owner、Android/Linux parity evidence、DRV-GAP-002 evidence 和 no-write-before-read-bridge 六类校验门禁 `VS-VAL-001..006`。
  - Android Binder/AIDL 新增 `getVehicleSignalValidationJson`，Android Console 新增 `Signal Check` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `vehicle-signal-validation`/`vehicle.signals.validation.get`/`GetVehicleSignalValidation` 可见路径。
  - 本轮只完成读桥激活前的 no-hardware validation envelope，不解析 DBC/ARXML，不连接 VHAL、SocketCAN、SOME/IP、vendor gateway 或真实车辆总线，不访问硬件，不开发 Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、XSC-004、XSC-006、NV-F-003、NV-F-004、NV-F-005、NV-P-001、NV-P-002、NV-P-003、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 FW-U-003/NV-P-006 Event subscription placeholder contract：
  - `GET /uib/events/subscriptions` 新增订阅 lifecycle、cursor/replay、filter、QoS/backpressure、Runtime & Governance、Android/Linux binding parity 和 `EV-SUB-001..005` 门禁。
  - Android Binder/AIDL 新增 `getEventSubscriptionsJson`，Android Console 新增 `Event Subs` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `event-subscriptions`/`uib.events.subscriptions.get`/`GetEventSubscriptions` 可见路径。
  - 本轮只完成只读订阅占位契约，不实现真实订阅 broker、callback/watch、SSE/WebSocket、DDS runtime、高频数据面、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002。
- 推进 Vehicle Signal read-bridge activation criteria contract：
  - `GET /vehicle/signals/activation` 新增 DBC/ARXML、Android VHAL/vendor AIDL、Linux SocketCAN、vendor gateway/SOME-IP 四类读桥激活准入条件，以及 `VS-ACT-001..005` 必过门禁。
  - Android Binder/AIDL 新增 `getVehicleSignalActivationJson`，Android Console 新增 `Signal Gate` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `vehicle-signal-activation`/`vehicle.signals.activation.get`/`GetVehicleSignalActivation` 可见路径。
  - 本轮只完成读桥激活准入 contract，不加载 DBC/ARXML，不连接 VHAL、SocketCAN、vendor gateway 或真实车辆总线，不访问硬件，不开发 Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、XSC-004、XSC-006、NV-F-003、NV-F-004、NV-F-005、NV-P-001、NV-P-002、NV-P-003、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 NV-F-004/NV-F-005 Vehicle/Body Signal catalog contract：
  - 新增 `central-brain/backend/vehicle_signals.py` 与 `GET /vehicle/signals`，以只读 VSS-style catalog 暴露 BCM/HVAC/Seat/Door/Light/Powertrain 等信号路径、访问级别、governance tag、Adapter 边界和 Driver/HAL 缺口链接。
  - Android Binder/AIDL 新增 `getVehicleSignalsJson`，Android Console 新增 `Vehicle Signals` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `vehicle-signals`/`vehicle.signals.list`/`GetVehicleSignals` 可见路径。
  - 本轮只完成信号目录和 Android/Linux 同步可见性，不加载 DBC/ARXML，不连接 VHAL、SocketCAN、vendor gateway 或真实车辆总线，不访问硬件，不开发 Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、XSC-004、XSC-006、NV-F-004、NV-F-005、FW-U-001、FW-U-002、FW-U-003、FW-U-004、NV-P-002、NV-P-003、DEL-001、DEL-002、DEL-005。
- 推进 Python 原型成熟度总览 contract：
  - 新增 `central-brain/backend/prototype_readiness.py` 与 `GET /prototype/readiness`，集中暴露 AI SDK、Uni Info Bus、SOA、Runtime & Governance、Protocol Binding、Native adapters/Driver-HAL backlog、hardware empty interfaces 的当前成熟度、Android 主路径、Linux 同步路径、开放偏差、开放问题和下一步候选增量。
  - Android Binder/AIDL 新增 `getPrototypeReadinessJson`，Android Console 新增 `Prototype` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `prototype-readiness`/`prototype.readiness.get`/`GetPrototypeReadiness` 可见路径。
  - 本轮只补项目/产品/架构状态总览，不 dispatch SOA service，不访问真实硬件，不开发 Driver/HAL、vendor SDK、Safety Runtime、共享内存、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-001、XSC-002、XSC-003、XSC-004、XSC-005、XSC-006、DEL-001、DEL-002、DEL-003、DEL-004、DEL-005、HW-002、KH-003、KH-006、KH-007。
- 推进 A6/A6.1 Python 原型硬件空接口注册表：
  - 新增 `central-brain/backend/hardware_interfaces.py` 与 `GET /hardware/interfaces`，覆盖外置 PCIe NPU、Vehicle bus、Camera/Audio/Sensors、Ethernet/SOME-IP/DDS/TSN、Shared memory/Safety Runtime 五类硬件依赖空接口。
  - Android Binder/AIDL 新增 `getHardwareInterfacesJson`，Android Console 新增 `Hardware IF` 调试入口；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `hardware-interfaces`/`hardware.interfaces.get`/`GetHardwareInterfaces` 可见路径。
  - 本轮只完成 Python 原型中的接口预留和 Android/Linux 同步可见性，不访问真实硬件，不开发 Driver/HAL、vendor SDK、Safety Runtime、共享内存、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-006、HW-002、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005、NV-F-011、NV-P-002、NV-P-003。
- 推进 A1 FW-U-008 Uni Info Bus extension registry contract：
  - 新增 `GET /uib/extensions`，以只读 contract 暴露扩展语义对象、schema 状态、治理规则、binding 可见性和 no-dispatch 边界，避免“其他/扩展”能力绕开 Uni Info Bus、SOA、Runtime & Governance 或 Protocol Binding。
  - Android Binder/AIDL 新增 `getUibExtensionsJson`；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `extensions`/`uib.extensions.get`/`GetUibExtensions` 可见路径。
  - 本轮只补 FW-U-008 扩展机制 contract 可查询能力，不实现动态插件 runtime、真实 extension loader、SOA dispatch、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-008、XSC-005、XSC-006、NV-P-002、NV-P-003、DEL-001、DEL-002。
- 推进 A9 Android/Linux delivery readiness contract：
  - 新增 `GET /delivery/readiness`，集中暴露 Android debug Console/Binder、Android system service note、Linux CLI、Linux IPC、Linux gRPC/RPC、Linux systemd/package profile、Driver/HAL gap backlog 和虚拟化约束的当前状态、阻塞项、验证命令和非目标边界。
  - Android Binder/AIDL 新增 `getDeliveryReadinessJson`；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `delivery-readiness`/`delivery.readiness.get`/`GetDeliveryReadiness` 可见路径。
  - 本轮只补 Android/Linux 交付 readiness 可查询能力，不实现 Android system service、真实 gRPC runtime、量产包管理、生产共享治理后端、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：DEL-001、DEL-002、DEL-003、DEL-004、DEL-005、XSC-001、XSC-002、XSC-003、XSC-004、XSC-005、XSC-006、NV-P-002、NV-P-003、KH-003、KH-006、KH-007。
- 推进 A4 Protocol Binding readiness contract：
  - 新增 `GET /bindings/readiness`，集中暴露 Android Binder、Linux IPC、Linux gRPC/RPC、REST、MQTT、SOME/IP、DDS 的当前状态、阻塞项、验证命令、下一步决策和非目标边界。
  - Android Binder/AIDL 新增 `getBindingReadinessJson`；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `binding-readiness`/`bindings.readiness.get`/`GetBindingReadiness` 可见路径。
  - 本轮只补 Protocol Binding readiness 可查询能力，不实现量产 shared governance backend、true gRPC runtime、MQTT/SOME/IP/DDS、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-006、NV-P-001、NV-P-002、NV-P-003、NV-P-004、NV-P-005、NV-P-006、DEL-001、DEL-002、DEL-003、DEL-004。
- 推进 A2 SOA service contract 可见性：
  - 新增 `GET /soa/contracts`，从 `runtime_governance.SERVICE_CATALOG` 暴露服务 contract、版本、domain、Policy/Safety State、QoS、Lifecycle、schema source 和 no-dispatch 边界。
  - Android Binder/AIDL 新增 `getServiceContractsJson`；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `service-contracts`/`soa.contracts.get`/`GetServiceContracts` 可见路径。
  - 本轮只补 SOA contract 可查询能力，不 dispatch SOA service，不消费 QoS，不访问 Driver/HAL、车辆总线、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-003、FW-S-001、FW-S-002、FW-S-003、FW-S-004、FW-S-005、NV-G-001、NV-G-002、NV-G-003、NV-P-002、NV-P-003、DEL-001、DEL-002。

### 2026-07-06

- 推进 shared Runtime & Governance backend deployment plan contract：
  - 新增 `GET /governance/deployment-plan`，用 Runtime & Governance payload 固定未来共享治理后端的 Android system/privileged service、Linux standalone daemon、true gRPC/RPC 三类目标部署形态、身份输入、开放决策和非目标边界。
  - Android Binder/AIDL 新增 `getGovernanceDeploymentPlanJson`；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `governance-deployment-plan`/`governance.deployment.plan.get`/`GetGovernanceDeploymentPlan` 可见路径。
  - 本轮只新增部署计划 contract 和验证，不实现生产多进程治理后端、真实 gRPC runtime、Android framework/SELinux patch、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-005、XSC-006、NV-G-001、NV-G-002、NV-G-003、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、NV-P-003、DEL-001、DEL-002、DEL-003、DEL-004。
- 推进 Linux IPC/gRPC shared governance runtime/audit diagnostic path：
  - `central_brain_governance_client.py` 新增 `get_runtime_via_socket` 与 `get_audit_via_socket`，让 Linux IPC 与 Linux gRPC/RPC sample 在 `governance.runtime.get`/`GetRuntimeGovernance` 和 `audit.recent.get`/`GetRecentAudit` 上复用同一 shared governance socket envelope。
  - `central_brain_ipc_daemon.py` 与 `central_brain_grpc_server.py` 对 runtime/audit 只读诊断优先走 shared governance daemon，不可用时回退 REST prototype gateway；SOA `InvokeService` precheck 仍保留 shared precheck + local Runtime & Governance fallback。
  - Linux IPC/gRPC smoke 现在验证 runtime/audit 诊断经 `forwarding=shared-governance-socket` 返回，且不 dispatch SOA service、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 本轮未新增生产多进程治理后端、真实 gRPC runtime、Android framework/SELinux patch、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-005、XSC-006、NV-G-001、NV-G-002、NV-G-004、NV-G-007、NV-P-002、NV-P-003、DEL-002。
- 推进 shared Runtime & Governance backend migration readiness check：
  - 新增 `GET /governance/migration-check`，用机器可读 payload 固定生产共享治理后端替换前必须保持的三类不变量：SOA dispatch 必须经 `governance.precheck`、各 transport 不复制 Policy/QoS 逻辑、runtime/audit 诊断只读且不触发 Driver/HAL/车辆总线/虚拟化。
  - Android Binder/AIDL 新增 `getGovernanceMigrationCheckJson`；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `governance-migration-check`/`governance.migration.check`/`GetGovernanceMigrationCheck` 可见路径。
  - 本轮只新增迁移 readiness contract 和验证，不实现量产多进程治理后端、真实 gRPC runtime、Android framework/SELinux patch、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-005、XSC-006、NV-G-001、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、NV-P-003、DEL-001、DEL-002、DEL-003、DEL-004。
- 推进 shared Runtime & Governance backend target contract：
  - 新增 `GET /governance/backend-contract`，用 Runtime & Governance payload 固定未来共享治理后端必须支持的 `governance.precheck`、`governance.runtime.get`、`audit.recent.get` 三类操作、Android Binder/Linux IPC/Linux gRPC-RPC 接入形态、替换规则和非目标边界。
  - Android Binder/AIDL 新增 `getGovernanceBackendContractJson`；Linux CLI、Linux IPC active sample 与 Linux gRPC/RPC JSON contract sample 新增 `governance-backend-contract`/`governance.backend.contract.get`/`GetGovernanceBackendContract` 可见路径。
  - Protocol Binding registry、API contract、smoke/static checks、接口设计、平台差异、交付目标、偏差和驱动支持边界同步说明该能力是共享治理后端目标契约，不是量产多进程治理后端、真实 gRPC runtime、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-005、XSC-006、NV-G-001、NV-G-002、NV-G-003、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、NV-P-003、DEL-001、DEL-002。
- 推进 Linux Protocol Binding shared governance client 收敛：
  - 新增 `central_brain_governance_client.py`，把 Linux IPC 与 Linux gRPC/RPC sample 调用 shared governance socket 的 `governance.precheck` envelope 收敛到同一 client helper。
  - `central_brain_ipc_daemon.py` 与 `central_brain_grpc_server.py` 继续保留各自 local Runtime & Governance fallback，但 shared daemon 调用路径不再重复实现 socket 读写与 envelope 组装。
  - Protocol Binding registry、API contract、Linux README、接口设计、偏差和驱动支持边界同步说明该 helper 只是 Linux 本地共享治理样例的 client boundary，不是量产治理后端、真实 gRPC runtime、Driver/HAL 或虚拟化层。
  - 覆盖 Req ID：XSC-005、XSC-006、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、NV-P-003、DEL-002。
- 推进 Linux shared Runtime & Governance daemon 诊断可见性：
  - `central_brain_governance_daemon.py` 在现有 `governance.precheck` 基础上新增 direct socket operation：`governance.runtime.get` 与 `audit.recent.get`，复用 `RuntimeGovernance.governance_payload()` 和 `audit_payload()`。
  - Linux IPC smoke 现在直接连接 shared governance socket，验证 runtime registry/QoS 状态和 precheck 审计事件可通过该 daemon 查询，且不 dispatch SOA service、Driver/HAL 或虚拟化层。
  - Protocol Binding registry、API contract、Linux README、delivery docs、需求矩阵、偏差和驱动支持边界同步说明该能力仍是 Linux 单机共享治理样例，不是量产多进程治理后端。
  - 覆盖 Req ID：XSC-005、XSC-006、NV-G-001、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、DEL-002。
- 推进 Android Console Governance/Driver gap Binder 可见性：
  - `MainActivity` 在现有 `Refresh`、`Plan Agent Task`、`Execute Task`、`Invoke Skill`、`Query Memory` 基础上新增 `Precheck` 与 `Driver Gaps` 调试入口，分别调用 `precheckGovernanceJson` 和 `getDriverHalGapsJson`。
  - Android Console 现在可直接验证 XSC-005 的 Runtime & Governance 只检查不调用路径，以及 KH-003/KH-006/DEL-005 的 Driver/HAL gap backlog 只读可见性。
  - 静态绑定检查新增对 Console governance precheck 与 driver gaps 按钮路径的断言，防止 Android 主路径只停留在 contract 文档。
  - 本轮未新增 Android system service、真实 Driver/HAL、Safety Runtime、车辆总线、NPU vendor SDK、真实共享治理后端或虚拟化层。
  - 覆盖 Req ID：XSC-004、XSC-005、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、KH-003、KH-006、DEL-001、DEL-005。
- 推进 A9 Linux package/profile 静态契约：
  - 新增 `central-brain/deploy/linux/central-brain.package-profile.json`，用机器可读清单固定 Linux 样例的安装根、服务身份、环境文件、runtime/log 目录、四个 systemd unit、Req ID、hardening 要求和非目标边界。
  - 新增 `tools/check_central_brain_linux_package_profile.sh`，验证 package profile、env example 与 backend/governance/IPC/gRPC-RPC unit 的 service identity、`WorkingDirectory`、`EnvironmentFile`、`ExecStart`、`ReadWritePaths`、runtime 目录和 hardening 约束一致。
  - `tools/check_central_brain_delivery_docs.sh` 纳入 package profile 检查项；交付目标、平台差异、偏差和驱动支持文档同步说明该 profile 是 Linux cockpit-domain 样例，不是量产包管理。
  - 本轮未新增真实 gRPC runtime、package manager 集成、LSM/SELinux/AppArmor policy、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：DEL-002、DEL-003、DEL-004、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-G-007。

### 2026-07-05

- 推进 A9 Linux systemd hardening sample：
  - 四个 Linux systemd unit 新增 `ProtectSystem=strict`、`ProtectHome=true`、`PrivateDevices=true`、`RestrictSUIDSGID=true`、`LockPersonality=true`、`PYTHONDONTWRITEBYTECODE=1` 和最小 `ReadWritePaths`。
  - 新增 `tools/check_central_brain_linux_systemd_hardening.sh`，验证 gateway、governance、IPC、gRPC/RPC 样例 unit 的服务身份、日志目录、运行目录和 hardening 约束，并在可用时运行 `systemd-analyze verify`。
  - 本轮只收紧 Linux 交付样例部署约束；未新增量产包管理、真实 IPC/gRPC runtime、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：DEL-002、DEL-003、DEL-004、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-G-007。
- 推进 A6 Driver/HAL gap backlog contract：
  - `native_adapters.py` 新增 Driver/HAL gap backlog，覆盖 NPU、Vehicle bus、Camera/Audio/Sensors、Ethernet/SOME-IP/DDS/TSN、Shared memory/Safety Runtime 五类缺口。
  - 后端新增 `GET /native/driver-gaps`；Android Binder/AIDL 新增 `getDriverHalGapsJson`；Linux CLI 新增 `driver-gaps`，均只返回触发条件、Android 主路径、Linux 同步路径和最小新增开发量。
  - `/native/adapters/detail` 同步包含 `driver_hal_gap_backlog`，方便 Native adapter 交付边界与 Driver/HAL 缺口一起检查。
  - 本轮未开发 NPU/GPU/Camera/Audio/ETH/Vehicle bus driver、HAL、Safety Runtime、共享内存、真实 vendor SDK bridge 或虚拟化层。
  - 覆盖 Req ID：KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005、HW-002、NV-F-002、NV-F-004、NV-F-005、NV-F-006、NV-F-011、NV-P-001、NV-P-006、HV-001、HV-002、HV-003。
- 推进 Android Console execute/Skill/Memory Binder 调试路径：
  - `MainActivity` 在现有 `Refresh` 与 `Plan Agent Task` 基础上新增 `Execute Task`、`Invoke Skill`、`Query Memory`，分别调用 `executeAgentTaskJson`、`invokeSkillJson`、`queryMemoryJson`。
  - Android Console 现在可直接验证 XSC-001/FW-U-006 的 Agent execute、Skill/Tool 与 Memory contract mock；所有调用仍经 Binder service 上游 REST prototype gateway，不绕过 AI SDK/Uni Info Bus/SOA/Runtime & Governance 边界。
  - 静态绑定检查新增对 Console execute/Skill/Memory 按钮与 Binder client 方法的断言，防止 Android 主路径只停留在 plan。
  - 本轮未开发真实 Agent runtime、Skill sandbox、Memory store、Model Runtime Adapter、Driver/HAL、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-001、APP-004、NV-F-001、FW-U-006、FW-U-007、XSC-006、NV-P-002、NV-P-005、DEL-001。
- 推进 Linux gRPC/RPC contract active sample：
  - 新增 `central_brain_grpc_server.py` 与 `central_brain_grpc_client.py`，用当前环境可运行的 JSON TCP wrapper 验证 `central_brain_gateway.proto` 中 GatewayRequest/GatewayResponse 与 RPC 名称映射。
  - `InvokeService` 在转发到 REST prototype gateway 前优先调用 shared Linux governance daemon precheck，不可用时回退本地 Runtime & Governance precheck。
  - 新增 `central-brain-linux-grpc.service`、`CENTRAL_BRAIN_GRPC_PORT`、`CENTRAL_BRAIN_GRPC_AUDIT_LOG` 和 `tools/smoke_central_brain_linux_grpc.sh`。
  - 当前环境无 `grpcio`，因此本轮不是量产 gRPC server；仍未开发 Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-001、XSC-002、XSC-003、XSC-005、XSC-006、APP-004、FW-U-003、FW-U-004、FW-U-006、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-003、DEL-002、DEL-004。
- 推进 Linux Runtime & Governance 共享 daemon 样例：
  - 新增 `central_brain_governance_daemon.py`，通过 Unix socket 提供 `governance.precheck` 决策，Linux IPC `soa.service.invoke` 可优先调用该共享 socket。
  - Linux IPC daemon 在 `CENTRAL_BRAIN_GOVERNANCE_SOCKET` 不可用时保留本地 Runtime & Governance precheck fallback，避免绕过 Policy/Lifecycle/QoS。
  - Linux env/systemd 样例新增 `central-brain-governance.service`、`CENTRAL_BRAIN_GOVERNANCE_SOCKET` 和 `CENTRAL_BRAIN_GOVERNANCE_AUDIT_LOG`。
  - 本轮仍未开发量产多进程治理后端、独立 native gateway、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-005、XSC-006、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、DEL-002、DEL-004。
- 推进 Runtime & Governance 显式 precheck 契约：
  - 后端新增 `POST /governance/precheck`，对服务执行 discovery、Policy/Safety State、Lifecycle 和 QoS 决策检查，但默认 `consume_qos=false`，不 dispatch 到 SOA service、Driver/HAL、车辆总线或虚拟化层。
  - Android Binder/AIDL 新增 `precheckGovernanceJson`；Linux CLI/IPC 新增 `governance-precheck` / `governance.precheck`；gRPC contract skeleton 新增 `PrecheckGovernance`。
  - Protocol Binding registry、API contract、smoke test、交付文档与检查脚本同步覆盖该 precheck 路径。
  - 本轮仍未开发共享量产治理 daemon、多进程 QoS 后端、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-005、XSC-006、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、NV-P-003、DEL-001、DEL-002。
- 推进 Linux IPC Runtime & Governance 前置检查样例：
  - `central_brain_ipc_daemon.py` 对 `soa.service.invoke` 新增本地 Runtime & Governance precheck，覆盖 service discovery、Policy/Safety State、Lifecycle、QoS 和 IPC audit。
  - 允许的 SOA 调用继续转发到 REST semantic gateway；拒绝的 SOA 调用直接在 IPC 边界返回 `forwarding=blocked-before-rest-gateway` 和 `ipc_governance_precheck`。
  - Linux env/systemd 样例新增 `CENTRAL_BRAIN_IPC_AUDIT_LOG=/var/log/central-brain/ipc-audit.jsonl`，用于区分 IPC binding 审计与后端 gateway 审计。
  - 本轮仍未开发独立量产 gateway、多进程治理后端、Driver/HAL、Safety Runtime、车辆总线或虚拟化层。
  - 覆盖 Req ID：XSC-005、XSC-006、NV-G-002、NV-G-004、NV-G-005、NV-G-006、NV-G-007、NV-P-002、DEL-002、DEL-004。
- 推进 XSC-001/FW-U-006 Agent execute、Skill 与 Memory contract mock：
  - 后端新增 `POST /agent/execute`、`GET /skills`、`POST /skills/{skill_id}/invoke`、`POST /memory/query`，执行 Permission/Safety State 检查并写入 Runtime & Governance audit。
  - execute 只返回 `execution_mode=policy-checked-contract-mock` 和 SOA/Tool/Action dispatch 边界，不运行真实 Skill sandbox、Memory store、Model Runtime Adapter、Driver/HAL、车身总线或虚拟化层。
  - Android Binder/AIDL、Linux CLI/IPC 与 gRPC contract skeleton 同步新增 execute/Skill/Memory 映射。
  - 覆盖 Req ID：XSC-001、APP-004、NV-F-001、FW-U-006、FW-U-007、XSC-005、XSC-006、NV-G-005、NV-P-002、NV-P-003、DEL-001、DEL-002。
- 推进 FW-U-004 Uni Info Bus Action active mock：
  - 后端新增架构命名动作入口：`POST /uib/actions/request`；legacy `/actions/request` 仅保留兼容。
  - Action 请求执行 Permission/Safety State 检查并返回 `execution_mode=policy-checked-mock`，明确不 dispatch 到 Driver/HAL、Vehicle bus 或虚拟化层。
  - Linux CLI 与 Linux IPC active sample 新增 `action-request` / `uib.actions.request` 验证路径。
  - Android Binder/AIDL 与 gRPC contract skeleton 新增 `requestActionJson` / `RequestAction` 映射。
  - 本轮未开发真实车控执行、Vehicle Signal/ECU Adapter、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、XSC-005、XSC-006、FW-U-004、FW-U-007、NV-G-005、NV-P-002、NV-P-005、NV-P-003、DEL-001、DEL-002。

### 2026-07-04

- 确认图片可通过 WSL 路径读取，并归档到 `docs/assets/central_brain_architecture_source.png`。
- 确认当前仓库已有 Android 逆向和模拟器工具链。
- 确认 OpenCLAW 绝对路径可用：`/home/normad400/.npm-global/bin/openclaw`。
- 创建分支：`codex/central-brain-ecosystem`。
- 新增中央大脑文档和第一阶段代码骨架。
- 构建并签名 Android APK：`central-brain/android-console/out/central-brain-console.debug.apk`。
- 启动 WSL mock 后端并验证 `/health`、`/vehicle/state`、`/ai/infer`。
- 在本地 AVD `cabin_client_api36_x86_64` 安装并启动 `com.centralbrain.console`。
- 验证模拟器 App 通过 `10.0.2.2:8787` 连通 WSL 后端。
- 保存验证截图：
  - `logs/test/central-brain/console-launch.png`
  - `logs/test/central-brain/console-inference.png`
- 参考地平线 KaKaClaw 咖咖虾公开资料，补充产品概念映射：
  - Agentic Car OS
  - 任务即服务
  - Soul / Skill / Memory
  - 舱驾协同
  - Skill 沙箱
  - Privacy Router
- 新增 PM 级需求拆解：`docs/CENTRAL_BRAIN_REQUIREMENTS_BREAKDOWN.md`。
- 新增接口设计文档：`docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md`。
- 创建 20 小时自动进展推进任务：每 20 分钟一次，共 60 次，自动化 ID `20`。
- 用户明确要求将架构图作为真实需求基线，而非示意图。
- 新增架构图需求矩阵：`docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md`。
- 新增软件偏差登记表：`docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md`。
- 新增架构疑点登记表：`docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md`。
- 新增按图执行计划：`docs/CENTRAL_BRAIN_ARCHITECTURE_EXECUTION_PLAN.md`。
- 更新自动化 ID `20`：每 20 分钟推进时必须先检查架构需求矩阵、偏差登记表和疑点登记表。
- 用户明确虚拟化层不开发；本项目只记录虚拟化接口约束和部署假设。
- 用户明确驱动层仅在当前 Android/Linux 环境能力不足时新增开发量，但驱动接口支持必须文档化。
- 用户明确黄色小太阳组件会在多个 SoC 出现；已按跨 SoC 可移植平台组件建立 `XSC-001..006`。
- 用户明确以 Android 开发为主，交付时同时提供 Linux 版本；交付对象为 Android/Linux 座舱域软件工程师。
- 新增交付目标文档：`docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md`。
- 新增驱动接口支持矩阵：`docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md`。
- 立即手动执行一次自动化任务：
  - 完成 A1 后端 mock 初版：`/context`、`/state`、`/events/topics`、`/events/publish`、`/actions/request`、`/service/invoke`、`/tools`、`/permission/check`。
  - 新增 Linux/WSL smoke test：`tools/test_central_brain_bus.sh`。
  - 注意：Android Console 仍未切到 Uni Info Bus client，`DEV-001` 保持临时偏差。
- 推进语义网关主路径：
  - Android Console 改为调用 `GET /uib/state` 和 `POST /soa/invoke`。
  - 后端新增架构命名入口：`/uib/context`、`/uib/state`、`/soa/services`、`/soa/invoke`、`/governance/runtime`、`/bindings`。
  - 新增 Linux CLI：`central-brain/linux-cli/central_brain_cli.py`。
  - 新增 smoke test：`tools/smoke_central_brain_semantic_gateway.sh`。
  - 覆盖 Req ID：XSC-002、XSC-003、XSC-005、XSC-006、FW-U-001、FW-U-002、FW-U-005、FW-U-007、FW-S-004、FW-S-005、NV-G-001..007、NV-P-001..006。
- 推进 Runtime & Governance active prototype：
  - 新增 `central-brain/backend/runtime_governance.py`，集中承载服务注册、发现、Policy、Lifecycle、QoS 元数据和内存审计。
  - `/soa/invoke` 现在先通过 registry/discovery/policy/lifecycle precheck，再写入 audit。
  - 新增架构命名入口：`POST /policy/evaluate`、`GET /audit/recent`。
  - Linux CLI 与 smoke test 覆盖 policy deny、SOA invoke audit 和 governance 状态。
  - 覆盖 Req ID：XSC-005、FW-S-001、FW-S-002、FW-S-003、FW-S-004、FW-S-005、NV-G-001..007。
- 推进 Protocol Binding contract skeleton：
  - 新增 `central-brain/backend/protocol_bindings.py`，把 REST、Android Binder/AIDL、Linux IPC、gRPC、MQTT、SOME/IP、DDS 纳入绑定注册表。
  - 新增 Android AIDL artifact：`central-brain/bindings/android/aidl/com/centralbrain/binding/ICentralBrainGateway.aidl`。
  - 新增 Linux artifact：`central-brain/bindings/linux/proto/central_brain_gateway.proto` 与 `central-brain/bindings/linux/ipc/central_brain_ipc_envelope.schema.json`。
  - 新增 `/bindings/detail` 与 Linux CLI `binding-detail`，可查看绑定 artifact、语义入口映射和分层约束。
  - 新增 `tools/check_central_brain_binding_artifacts.sh`，验证 AIDL/proto/schema artifact 存在、可解析并包含 Req ID。
  - 覆盖 Req ID：XSC-002、XSC-003、XSC-005、XSC-006、NV-P-001..006、DEL-001、DEL-002。
- 推进 A5 Native adapters mock：
  - 新增 `central-brain/backend/native_adapters.py`，建立 AIOS Kernel、SOA Service Adapter、Vehicle Signal Adapter、Model Runtime Adapter、Security/Policy Adapter 注册表。
  - 后端新增 `/native/adapters` 与 `/native/adapters/detail`，返回 Android 主开发路径、Linux 同步交付路径、Driver/HAL 依赖与虚拟化约束。
  - Linux CLI 与语义网关 smoke test 新增 `native-adapters-detail` 校验。
  - 覆盖 Req ID：XSC-004、NV-F-001、NV-F-003、NV-F-004、NV-F-005、NV-F-008、NV-F-009、NV-F-011、DEL-001、DEL-002、DEL-005。
- 推进 Linux IPC binding active sample：
  - 新增 Unix domain socket daemon：`central-brain/bindings/linux/ipc/central_brain_ipc_daemon.py`。
  - 新增 IPC client：`central-brain/bindings/linux/ipc/central_brain_ipc_client.py`。
  - `linux-ipc` 在 `/bindings/detail` 中从 `contract-skeleton` 推进为 `active-sample`，映射 `uib.context.get`、`uib.state.get`、`soa.services.list`、`soa.service.invoke`、`policy.evaluate`、`governance.runtime.get`、`audit.recent.get`、`bindings.list`。
  - 新增 smoke test：`tools/smoke_central_brain_linux_ipc.sh`。
  - 覆盖 Req ID：XSC-002、XSC-003、XSC-005、XSC-006、FW-U-001、FW-U-002、FW-S-004、FW-S-005、NV-G-005、NV-G-007、NV-P-002、DEL-002。
- 推进 Android Binder service stub sample：
  - 扩展 AIDL：`getBindingDetailJson`、`getNativeAdaptersDetailJson`，让 Android IPC contract 覆盖 Protocol Binding 与 Native adapter 可见性。
  - 新增 Android service/client sample：`CentralBrainGatewayBinderService.java`、`CentralBrainGatewayClient.java`，将 Binder 方法映射到 `/uib/*`、`/soa/*`、`/policy/evaluate`、`/governance/runtime`、`/bindings/detail`、`/native/adapters/detail`。
  - `/bindings/detail` 中 `android-binder-aidl` 从 `contract-skeleton` 推进为 `service-stub-sample`；REST 仍只是上游 prototype binding。
  - 覆盖 Req ID：XSC-002、XSC-003、XSC-004、XSC-005、XSC-006、NV-P-002、DEL-001。
- 推进 A9 Android/Linux 交付样例：
  - 新增平台差异说明：`docs/CENTRAL_BRAIN_PLATFORM_DELTA.md`，覆盖 Android/Linux IPC、权限、部署、日志、Driver/HAL、虚拟化差异。
  - 新增 Linux 部署样例：`central-brain/deploy/linux/central-brain.env.example`、`central-brain/deploy/linux/systemd/central-brain-backend.service`、`central-brain/deploy/linux/systemd/central-brain-linux-ipc.service`。
  - 新增静态验证脚本：`tools/check_central_brain_delivery_docs.sh`。
  - 覆盖 Req ID：DEL-001、DEL-002、DEL-003、DEL-004、XSC-002、XSC-003、XSC-005、XSC-006、NV-P-002。
- 推进 A7 Hypervisor/Safety 接口约束：
  - 新增 `docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md`，记录 HV-001..003 的非开发范围、ASIL/QM domain 假设、Safety State 映射、跨 VM envelope 和 fallback。
  - 新增静态验证脚本：`tools/check_central_brain_virtualization_docs.sh`。
  - 未开发虚拟化层、Safety Runtime、共享内存驱动或 Driver/HAL。
  - 覆盖 Req ID：HV-001、HV-002、HV-003、FW-S-005、NV-G-005、NV-F-009、KH-007、DEL-004。
- 推进 FW-U-003 Uni Info Bus Event active mock：
  - 后端新增架构命名事件入口：`GET /uib/events/topics`、`POST /uib/events/publish`、`GET /uib/events/recent`；legacy `/events/*` 仅保留兼容。
  - Linux CLI 与 Linux IPC active sample 新增 `events`、`event-publish`、`event-recent` 验证路径。
  - Android Binder/AIDL 与 gRPC contract skeleton 新增事件 topic、publish、recent 方法。
  - 本轮未实现 DDS、高频推送、真实订阅 broker、Driver/HAL 或虚拟化层。
  - 覆盖 Req ID：XSC-002、XSC-006、FW-U-003、NV-P-002、NV-P-006、DEL-001、DEL-002。
- 推进 A6 NPU Runtime Adapter 接口约束：
  - 新增 `docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md`，固定外置 PCIe NPU 从 Uni Info Bus/SOA 到 Model Runtime Adapter、Driver/HAL 的分层边界。
  - 明确 Android 主开发路径、Linux 同步交付路径、最低 API 抽象、统一 envelope、状态机、错误码和 Driver/HAL 集成检查点。
  - 新增静态验证脚本：`tools/check_central_brain_npu_interface.sh`。
  - 本轮未开发 NPU driver、HAL、DMA/IOMMU、Safety Runtime、虚拟化层或 vendor SDK bridge。
  - 覆盖 Req ID：HW-002、NV-F-011、KH-003、KH-006、KH-007、DEL-001、DEL-002、DEL-005。
- 推进 A3 Runtime & Governance audit persistence sample：
  - `RuntimeGovernance` 新增可选 `CENTRAL_BRAIN_AUDIT_LOG` JSONL 审计日志，服务重启后可恢复最近 50 条 SOA audit。
  - `/audit/recent` 和 `/governance/runtime` 返回 persistence state、path、last_error，便于 Android/Linux 座舱工程师确认集成状态。
  - Linux systemd/env 样例新增 `CENTRAL_BRAIN_AUDIT_LOG=/var/log/central-brain/audit.jsonl` 与 `LogsDirectory=central-brain`。
  - 新增验证脚本：`tools/smoke_central_brain_audit_persistence.sh`。
  - 本轮未开发 Driver/HAL、Safety Runtime、虚拟化层、真实审计后端或日志轮转。
  - 覆盖 Req ID：XSC-005、NV-G-007、DEL-002、DEL-004。
- 推进 A3 Runtime & Governance QoS active prototype：
  - `RuntimeGovernance` 新增 per-service fixed-window QoS 检查，`/soa/invoke` 在 Policy/Lifecycle 之后执行限流。
  - `npu-inference` 样例限制为每 1 秒 2 次，超出后返回 `qos_decision=deny` 并写入 `qos_rejected` audit。
  - 新增验证脚本：`tools/smoke_central_brain_qos.sh`。
  - 本轮未开发多进程限流、真实服务治理后端、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-005、NV-G-004、NV-G-007、FW-S-005、DEL-002。
- 推进 Android Console Binder 绑定路径：
  - Debug APK 构建脚本生成 `ICentralBrainGateway` AIDL Java，并把 Android Binder service/client sample 编入 Console APK。
  - `AndroidManifest.xml` 声明 `CentralBrainGatewayBinderService`，Console 启动后先绑定该 service，再通过 `CentralBrainGatewayClient` 调用 `getStateJson` 和 `invokeServiceJson`。
  - Android App 层不再直接发起 `/uib/state`、`/soa/invoke` HTTP 调用；REST 仍仅由 Binder service 作为上游 prototype binding 代理。
  - 本轮未开发 Android system service、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-002、XSC-003、XSC-006、NV-P-002、NV-P-005、DEL-001。
- 推进 Android system/privileged service 集成说明：
  - 新增 `docs/CENTRAL_BRAIN_ANDROID_SYSTEM_SERVICE_INTEGRATION.md`，明确 debug APK Binder sample 与目标 AAOS system/privileged service 的差异。
  - 文档化 manifest/signature permission、Binder identity 到 Runtime & Governance Policy、SELinux/deployment 假设和验证检查项。
  - 新增 `tools/check_central_brain_android_system_service_docs.sh`，并纳入交付文档校验。
  - 登记 ISSUE-013：目标 AAOS 镜像签名、priv-app 白名单、SELinux domain、service manager 注册方式和 native gateway 形态待确认。
  - 本轮未开发 Android framework patch、priv-app 签名配置、SELinux policy、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：DEL-001、DEL-003、DEL-004、XSC-002、XSC-003、XSC-005、XSC-006、NV-P-002、NV-P-005、FW-U-007、FW-S-005、NV-G-005。
- 推进 XSC-001 AI SDK/Agent 任务规划入口：
  - 新增 `central-brain/backend/ai_sdk.py`，提供 AI SDK facade capabilities 与 policy-aware task graph planner。
  - 后端新增 `GET /ai/sdk/capabilities` 与 `POST /agent/plan`，App 提交 intent/utterance 后只获得任务图，执行步骤仍必须经 Uni Info Bus、Tool、Action 或 SOA 服务入口。
  - Runtime & Governance registry 新增 `agent-task-planner`，Protocol Binding 增加 Android Binder/AIDL、Linux IPC 和 gRPC contract 映射。
  - Linux CLI/IPC active sample 新增 `ai-sdk`、`agent-plan` 验证路径，semantic gateway 和 Linux IPC smoke test 已覆盖。
  - 本轮未开发 AI SDK 真库、Agent execute、Skill sandbox、Memory store、真实 Model Runtime Adapter、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-001、APP-004、NV-F-001、FW-U-006、FW-U-007、XSC-002、XSC-003、FW-S-005、XSC-006、NV-P-002、NV-P-003、DEL-001、DEL-002。
- 推进 Android Console AI SDK/Agent 主任务路径：
  - `MainActivity` 第二个主按钮从 SOA inference 调试入口切换为 `CentralBrainGatewayClient.planAgentTaskJson`。
  - Console 现在通过 Binder 提交 utterance、caller、permission、vehicle/safety state 到 `/agent/plan`，只获得任务图；实际执行仍必须经 SOA/Tool/Action。
  - 静态绑定检查新增对 Console `Plan Agent Task` 与 `planAgentTaskJson` 的断言，防止 App 主路径回退到直按 SOA 推理。
  - 本轮未开发 AI SDK 真库、Agent execute、Skill sandbox、Memory store、真实 Model Runtime Adapter、Driver/HAL、Safety Runtime 或虚拟化层。
  - 覆盖 Req ID：XSC-001、APP-004、XSC-002、XSC-003、XSC-006、NV-P-002、NV-P-005、DEL-001。
- 推进 FW-U-003/NV-P-006 Event subscription activation approval decision owner handoff evidence acceptance decision rollup：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup`，汇总 `EV-AHI-001..010` still-blocked acceptance decision gates。
  - Android 主路径新增 Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceDecisionRollupJson` 与 Console `Sub ApHDec`。
  - Linux 同步路径新增 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-decision-rollup`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.decision.rollup` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceDecisionRollup`。
  - 本轮只做 contract-only read-only rollup，不接受 packet，不创建 evidence/acceptance/review store，不关闭 gate，不激活 broker/DDS/high-rate data plane，不开发 Driver/HAL 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。
- 推进 FW-U-003/NV-P-006 Event subscription activation approval decision owner handoff evidence acceptance closure readiness checklist：
  - 新增 `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist`，枚举 `EV-AHJ-001..010` closure readiness blockers。
  - Android 主路径新增 Binder `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessChecklistJson` 与 Console `Sub ApHClose`。
  - Linux 同步路径新增 CLI `event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-checklist`、IPC `uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.checklist` 和 gRPC/RPC `GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessChecklist`。
  - 本轮只做 contract-only read-only checklist，不接受 packet，不附加 evidence，不分配 owner，不调用 POST，不创建 evidence/acceptance/review/result store，不关闭 gate，不激活 broker/DDS/high-rate data plane，不开发 Driver/HAL 或虚拟化层。
  - 覆盖 Req ID：XSC-002、FW-U-003、XSC-005、XSC-006、NV-P-002、NV-P-003、NV-P-006、DEL-001、DEL-002、DEL-004。
