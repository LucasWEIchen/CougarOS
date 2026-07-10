# Android Protocol Binding Skeleton

This directory contains the Android Binder/AIDL binding sample for the Central
Brain semantic gateway.

## Scope

- Req IDs: XSC-001, XSC-002, XSC-003, XSC-004, XSC-005, XSC-006, APP-004, FW-U-003, FW-U-004, FW-U-006, FW-U-008, HW-002, NV-F-003, NV-F-004, NV-F-005, NV-P-002, NV-P-006, KH-003, KH-006, KH-007, DEL-001, DEL-002, DEL-003, DEL-004, DEL-005.
- This is a Binder service/client sample. It does not replace Uni Info Bus or
  SOA semantics, and it does not access drivers, HAL, or virtualization
  directly.
- The Android Console debug APK now binds this service sample before calling
  Uni Info Bus State, AI SDK/Agent task planning, Agent execute, Skill invoke,
  Memory query, Runtime & Governance precheck, shared governance backend target
  contract, governance migration readiness, governance deployment plan,
  Protocol Binding readiness, Android/Linux delivery readiness, Python
  prototype readiness, SOA service contract visibility, Driver/HAL gap backlog,
  hardware empty-interface registry, hardware interface activation checklist, Event subscription lifecycle command, transport readiness, owner decision matrix, activation checklist, callback/watch shape, cursor/replay storage, backpressure/QoS evidence, readiness rollup contract, activation evidence review status contract, activation evidence retention checklist contract, activation evidence decision status rollup contract, activation approval dry-run status contract, hardware owner evidence replacement trigger checklist contract, selected-adapter readiness checklist contract, adapter load blocker rollup contract, adapter-load dry-run contract, adapter-load dry-run status contract, adapter-load dry-run audit consistency contract, adapter-load approval authority checklist contract, adapter-load approval authority no-store status contract, adapter-load approval authority audit consistency contract, and adapter-load approval decision dry-run contract,
  Vehicle/Body Signal catalog, and Vehicle
  Signal read-bridge activation criteria contract mocks. The Binder service
  sample still proxies to the REST semantic gateway as its upstream prototype
  binding.

## Mapping

| AIDL method | Semantic endpoint | Req IDs |
| --- | --- | --- |
| `getContextJson` | `GET /uib/context` | XSC-002, FW-U-001 |
| `getStateJson` | `GET /uib/state` | XSC-002, FW-U-002 |
| `listEventTopicsJson` | `GET /uib/events/topics` | XSC-002, FW-U-003, NV-P-006 |
| `publishEventJson` | `POST /uib/events/publish` | XSC-002, FW-U-003, NV-P-006 |
| `getRecentEventsJson` | `GET /uib/events/recent` | XSC-002, FW-U-003, NV-P-006 |
| `getEventSubscriptionsJson` | `GET /uib/events/subscriptions` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002 |
| `requestEventSubscriptionJson` | `POST /uib/events/subscriptions/request` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-006, DEL-001, DEL-002 |
| `cancelEventSubscriptionJson` | `POST /uib/events/subscriptions/cancel` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-006, DEL-001, DEL-002 |
| `getEventSubscriptionTransportReadinessJson` | `GET /uib/events/subscriptions/transport-readiness` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionDecisionMatrixJson` | `GET /uib/events/subscriptions/decision-matrix` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionActivationChecklistJson` | `GET /uib/events/subscriptions/activation-checklist` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionCallbackWatchShapeJson` | `GET /uib/events/subscriptions/callback-watch-shape` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionCursorReplayStorageJson` | `GET /uib/events/subscriptions/cursor-replay-storage` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionBackpressureQosEvidenceJson` | `GET /uib/events/subscriptions/backpressure-qos-evidence` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionReadinessRollupJson` | `GET /uib/events/subscriptions/readiness-rollup` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `submitEventSubscriptionActivationEvidenceJson` | `POST /uib/events/subscriptions/activation-evidence` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionActivationEvidenceStatusJson` | `GET /uib/events/subscriptions/activation-evidence/status` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionActivationEvidenceRetentionChecklistJson` | `GET /uib/events/subscriptions/activation-evidence/retention-checklist` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionActivationEvidenceDecisionStatusRollupJson` | `GET /uib/events/subscriptions/activation-evidence/decision-status-rollup` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionActivationApprovalDryRunStatusJson` | `GET /uib/events/subscriptions/activation-evidence/approval-dry-run/status` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionActivationApprovalAuthorityChecklistJson` | `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionActivationApprovalAuthorityAuditConsistencyJson` | `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/audit-consistency` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionActivationApprovalDecisionBlockerRollupJson` | `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-blocker-rollup` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `dryRunEventSubscriptionActivationApprovalDecisionJson` | `POST /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionActivationApprovalDecisionDryRunStatusJson` | `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/status` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionActivationApprovalDecisionDryRunAuditConsistencyJson` | `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/audit-consistency` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionActivationApprovalDecisionClosureBlockerMatrixJson` | `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionActivationApprovalDecisionOwnerHandoffChecklistJson` | `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionActivationApprovalDecisionOwnerHandoffAuditConsistencyJson` | `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionActivationApprovalDecisionOwnerHandoffDecisionRollupJson` | `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessMatrixJson` | `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessAuditConsistencyJson` | `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceStatusJson` | `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceAuditConsistencyJson` | `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceDecisionRollupJson` | `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-002, DEL-004 |
| `getUibExtensionsJson` | `GET /uib/extensions` | XSC-002, FW-U-008, XSC-005, XSC-006 |
| `getAiSdkCapabilitiesJson` | `GET /ai/sdk/capabilities` | XSC-001, APP-004 |
| `planAgentTaskJson` | `POST /agent/plan` | XSC-001, APP-004, NV-F-001, FW-U-006, FW-U-007 |
| `executeAgentTaskJson` | `POST /agent/execute` | XSC-001, APP-004, NV-F-001, FW-U-006, FW-U-007 |
| `listSkillsJson` | `GET /skills` | XSC-001, FW-U-006 |
| `invokeSkillJson` | `POST /skills/{skill_id}/invoke` | XSC-001, FW-U-006, NV-G-005 |
| `queryMemoryJson` | `POST /memory/query` | XSC-001, NV-F-001, FW-U-006 |
| `requestActionJson` | `POST /uib/actions/request` | XSC-002, FW-U-004, FW-U-007, XSC-005, NV-G-005 |
| `listServicesJson` | `GET /soa/services` | XSC-003, FW-S-001..004 |
| `getServiceContractsJson` | `GET /soa/contracts` | XSC-003, FW-S-004, NV-G-003 |
| `invokeServiceJson` | `POST /soa/invoke` | XSC-003, FW-S-005 |
| `evaluatePolicyJson` | `POST /policy/evaluate` | XSC-005, NV-G-005 |
| `precheckGovernanceJson` | `POST /governance/precheck` | XSC-005, NV-G-002, NV-G-004, NV-G-005, NV-G-006, NV-G-007 |
| `getGovernanceBackendContractJson` | `GET /governance/backend-contract` | XSC-005, XSC-006, NV-G-001..007, NV-P-002, NV-P-003 |
| `getGovernanceMigrationCheckJson` | `GET /governance/migration-check` | XSC-005, XSC-006, NV-G-001, NV-G-002, NV-G-004, NV-G-005, NV-G-006, NV-G-007, NV-P-002, NV-P-003, DEL-001, DEL-002, DEL-003, DEL-004 |
| `getGovernanceDeploymentPlanJson` | `GET /governance/deployment-plan` | XSC-005, XSC-006, NV-G-001..007, NV-P-002, NV-P-003, DEL-001, DEL-002, DEL-003, DEL-004 |
| `getRuntimeGovernanceJson` | `GET /governance/runtime` | XSC-005, NV-G-001..007 |
| `getRecentAuditJson` | `GET /audit/recent` | XSC-005, NV-G-007 |
| `listBindingsJson` | `GET /bindings` | XSC-006, NV-P-001..006 |
| `getBindingDetailJson` | `GET /bindings/detail` | XSC-006, NV-P-002 |
| `getBindingReadinessJson` | `GET /bindings/readiness` | XSC-006, NV-P-001..006, DEL-001..004 |
| `getDeliveryReadinessJson` | `GET /delivery/readiness` | DEL-001..005, XSC-001..006 |
| `getPrototypeReadinessJson` | `GET /prototype/readiness` | XSC-001..006, DEL-001..005 |
| `getNativeAdaptersDetailJson` | `GET /native/adapters/detail` | XSC-004, NV-F-001, NV-F-003, NV-F-011 |
| `getDriverHalGapsJson` | `GET /native/driver-gaps` | KH-003, KH-006, DEL-005 |
| `getHardwareInterfacesJson` | `GET /hardware/interfaces` | XSC-004, XSC-006, HW-002, KH-001, KH-002, KH-003, KH-006, KH-007, DEL-005 |
| `getHardwareInterfaceActivationChecklistJson` | `GET /hardware/interfaces/activation-checklist` | XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-005 |
| `getHardwareInterfaceOwnerDecisionStatusJson` | `GET /hardware/interfaces/owner-decision-status` | XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-005 |
| `submitHardwareInterfaceOwnerDecisionEvidenceJson` | `POST /hardware/interfaces/owner-decision-evidence` | XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-005 |
| `getHardwareInterfaceOwnerDecisionEvidenceStatusJson` | `GET /hardware/interfaces/owner-decision-evidence/status` | XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-005 |
| `getHardwareInterfaceOwnerDecisionEvidenceRetentionChecklistJson` | `GET /hardware/interfaces/owner-decision-evidence/retention-checklist` | XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-005 |
| `getHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklistJson` | `GET /hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist` | XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-005 |
| `getHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklistJson` | `GET /hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist` | XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-005 |
| `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollupJson` | `GET /hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup` | XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-005 |
| `dryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadJson` | `POST /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run` | XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-005 |
| `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatusJson` | `GET /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status` | XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-005 |
| `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistencyJson` | `GET /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/audit-consistency` | XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-005 |
| `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklistJson` | `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist` | XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-005 |
| `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatusJson` | `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/status` | XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-005 |
| `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistencyJson` | `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/audit-consistency` | XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-005 |
| `dryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionJson` | `POST /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run` | XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-005 |
| `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunStatusJson` | `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/status` | XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-005 |
| `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunAuditConsistencyJson` | `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/audit-consistency` | XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-005 |
| `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummaryJson` | `GET /hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary` | XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-005 |
| `getVehicleSignalsJson` | `GET /vehicle/signals` | XSC-002, XSC-004, XSC-006, NV-F-004, NV-F-005, NV-P-002, NV-P-003, DEL-001, DEL-002, DEL-005 |
| `getVehicleSignalActivationJson` | `GET /vehicle/signals/activation` | XSC-002, XSC-004, XSC-006, NV-F-003, NV-F-004, NV-F-005, NV-P-001, NV-P-002, NV-P-003, KH-003, KH-006, KH-007, DEL-001, DEL-002, DEL-005 |
| `getVehicleSignalValidationJson` | `GET /vehicle/signals/validation` | XSC-002, XSC-004, XSC-006, NV-F-003, NV-F-004, NV-F-005, NV-P-001, NV-P-002, NV-P-003, KH-003, KH-006, KH-007, DEL-001, DEL-002, DEL-005 |

## Artifacts

- `aidl/com/centralbrain/binding/ICentralBrainGateway.aidl`: stable Binder
  contract for Android IPC integration.
- `java/com/centralbrain/binding/CentralBrainGatewayBinderService.java`: sample
  service stub that maps Binder methods to the semantic gateway.
- `java/com/centralbrain/binding/CentralBrainGatewayClient.java`: sample app or
  SDK-side client helper for binding to the service.

## Sample Service Manifest Entry

```xml
<service
    android:name="com.centralbrain.binding.CentralBrainGatewayBinderService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.centralbrain.binding.action.BIND_CENTRAL_BRAIN_GATEWAY" />
    </intent-filter>
</service>
```

## Delivery Assumptions

- Android production integration should wrap this AIDL in a system or privileged
  service depending on the target AAOS image.
- Permission checks remain in Runtime & Governance; Binder caller identity is an
  input to policy, not a replacement for policy.
- Stable parcelable models can replace JSON after the semantic contract settles.
- The current sample is not a Driver/HAL bridge and does not create any
  virtualization-layer development scope.
- Agent execute, Skill invoke, and Memory query methods are contract mocks now
  exposed by the Android Console. They validate policy and expose dispatch
  boundaries, but they do not run a real Skill sandbox, Memory store, Model
  Runtime Adapter, Driver/HAL, vehicle bus, or virtualization path.
- `precheckGovernanceJson` is a diagnostic Runtime & Governance contract. It
  is now exposed by the Android Console `Precheck` action and checks discovery,
  Policy, Lifecycle, and QoS decisions without dispatching a service; by default
  it does not reserve the QoS fixed-window slot.
- `getGovernanceBackendContractJson` exposes the target shared Runtime &
  Governance backend contract that Binder, Linux IPC, and Linux gRPC/RPC must
  share when the sample transport is replaced. It is metadata only and does not
  implement a production governance backend.
- `getGovernanceMigrationCheckJson` exposes the read-only migration readiness
  check for that backend replacement. It reports the SOA precheck, Policy/QoS
  ownership, runtime/audit diagnostic, and non-goal invariants and explicitly
  does not implement a production governance backend.
- `getServiceContractsJson` exposes SOA service contract, version, Policy,
  Safety State, QoS, Lifecycle, and no-dispatch boundary metadata from the same
  Runtime & Governance catalog used by `/soa/services`; it does not invoke the
  service, Driver/HAL, vehicle bus, or virtualization layer.
- `getBindingReadinessJson` exposes Android Binder, Linux IPC, Linux gRPC/RPC,
  REST, MQTT, SOME/IP, and DDS readiness, blockers, validation commands, and
  non-goal boundaries. It does not implement production transports, Driver/HAL,
  Safety Runtime, vehicle bus, or virtualization.
- `getDeliveryReadinessJson` exposes Android debug Console/Binder, Android
  system service notes, Linux CLI/IPC/gRPC samples, Linux package profile,
  Driver/HAL gap backlog, and virtualization constraints for delivery review.
  It does not implement Android system service, true gRPC runtime, production
  packaging, Driver/HAL, Safety Runtime, vehicle bus, or virtualization.
- `getPrototypeReadinessJson` exposes the Python prototype module maturity view
  for Android integration review through the Android Console `Prototype`
  action. It lists module state, Android/Linux binding visibility, deviations,
  issues, next increment candidates, and no-goal boundaries without dispatching
  services, touching hardware, creating Driver/HAL scope, or creating
  virtualization work.
- `getUibExtensionsJson` exposes FW-U-008 extension registry contract metadata,
  governance rules, binding visibility, and no-dispatch boundaries. It does
  not load plugins, dispatch SOA services, access Driver/HAL, or create
  virtualization work.
- `getEventSubscriptionsJson`, `requestEventSubscriptionJson`,
  `cancelEventSubscriptionJson`, `getEventSubscriptionTransportReadinessJson`,
  `getEventSubscriptionDecisionMatrixJson`,
  `getEventSubscriptionActivationChecklistJson`,
  `getEventSubscriptionCallbackWatchShapeJson`,
  `getEventSubscriptionCursorReplayStorageJson`, and
  `getEventSubscriptionBackpressureQosEvidenceJson`,
  `getEventSubscriptionReadinessRollupJson`,
  `submitEventSubscriptionActivationEvidenceJson`,
  `getEventSubscriptionActivationEvidenceStatusJson`,
  `getEventSubscriptionActivationEvidenceRetentionChecklistJson`,
  `getEventSubscriptionActivationEvidenceDecisionStatusRollupJson`,
  `getEventSubscriptionActivationApprovalDryRunStatusJson`,
  `getEventSubscriptionActivationApprovalAuthorityChecklistJson`,
  `getEventSubscriptionActivationApprovalAuthorityAuditConsistencyJson`, and
  `getEventSubscriptionActivationApprovalDecisionBlockerRollupJson`,
  `dryRunEventSubscriptionActivationApprovalDecisionJson`,
  `getEventSubscriptionActivationApprovalDecisionDryRunStatusJson`, and
  `getEventSubscriptionActivationApprovalDecisionDryRunAuditConsistencyJson`,
  `getEventSubscriptionActivationApprovalDecisionClosureBlockerMatrixJson`,
  `getEventSubscriptionActivationApprovalDecisionOwnerHandoffChecklistJson`,
  `getEventSubscriptionActivationApprovalDecisionOwnerHandoffAuditConsistencyJson`,
  `getEventSubscriptionActivationApprovalDecisionOwnerHandoffDecisionRollupJson`,
  `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessMatrixJson`,
  `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessAuditConsistencyJson`, and
  `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceStatusJson`,
  `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceAuditConsistencyJson`, and
  `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceDecisionRollupJson` expose FW-U-003/NV-P-006 Event
  subscription lifecycle, cursor, backpressure, governance, binding parity,
  request/cancel contract-only commands, callback/watch transport readiness,
  broker/cursor/backpressure owner decision matrix, activation evidence gates,
  activation evidence intake, review status, retention checklist, decision status rollup, approval dry-run status, approval authority checklist, approval authority audit consistency, approval decision blocker rollup, approval decision dry-run request, approval decision dry-run no-store status, approval decision dry-run audit consistency, approval decision closure blocker matrix, approval decision owner handoff checklist, owner handoff audit consistency, owner handoff decision rollup, owner handoff evidence readiness matrix, owner handoff evidence readiness audit consistency, owner handoff evidence acceptance status, owner handoff evidence acceptance audit consistency, and owner handoff evidence acceptance decision rollup,
  callback/watch API shape, cursor/replay storage schema, overflow schema,
  replay rate, ack timeout, per-caller throttling, Runtime & Governance QoS evidence,
  readiness blockers,
  and no-persistence/no-broker/no-runtime boundaries through the Android
  Console `Event Subs`, `Sub Req`, `Sub Cancel`, `Sub Link`, `Sub Matrix`, `Sub Gate`, `Sub Shape`, `Sub Cursor`, `Sub QoS`, `Sub Ready`, `Sub Evidence`, `Sub Review`, `Sub Retain`, `Sub Decide`, `Sub ApStat`, `Sub ApAuth`, `Sub ApAudit`, `Sub ApBlock`, `Sub ApDec`, `Sub ApDStat`, `Sub ApDAudit`, `Sub ApClose`, `Sub ApHand`, `Sub ApHAud`, `Sub ApHRoll`, `Sub ApHEv`, `Sub ApHEvAud`, `Sub ApHAcc`, `Sub ApHAcAud`, and `Sub ApHDec`
  actions only. They do not assign production owners, select a transport,
  pass approval dry-run, assign approval authority, save dry-run results, create durable evidence stores, create approval result stores, create review queues, create delete/export workflows,
  activate event QoS, close readiness gates, register callbacks, start SSE/WebSocket, start DDS, dispatch services, access
  Driver/HAL, or create virtualization work.
- `getDriverHalGapsJson` exposes the Driver/HAL gap backlog for Android
  integration review through the Android Console `Driver Gaps` action only; it
  does not call HAL, device nodes, vendor SDKs, or Safety Runtime.
- `getHardwareInterfacesJson` exposes the hardware empty-interface registry for
  Android integration review through the Android Console `Hardware IF` action
  only; it returns reserved methods and Android/Linux target paths but does not
  access hardware, call HAL, allocate shared memory, invoke vendor SDKs, or
  create virtualization work.
- `getHardwareInterfaceActivationChecklistJson` exposes the hardware interface
  activation checklist for Android integration review through the Android
  Console `HW Gate` action only; it returns owner, ABI, Driver/HAL gap,
  Safety/Policy, smoke evidence, rollback/fault gates, and keeps
  `activation_allowed=false`, `hardware_accessed=false`,
  `driver_development_triggered=false`, and
  `virtualization_development_triggered=false`.
- `getHardwareInterfaceOwnerDecisionStatusJson` exposes the hardware owner
  decision rollup through the Android Console `HW Owner` action only; it keeps
  target owner, Android ABI owner, Linux ABI owner, Driver/HAL gap owner,
  Safety/Policy owner, target smoke evidence owner, and rollback/fault
  semantics owner open, and keeps `activation_allowed=false`,
  `hardware_accessed=false`, `driver_development_triggered=false`, and
  `virtualization_development_triggered=false`.
- `submitHardwareInterfaceOwnerDecisionEvidenceJson` exposes the hardware
  owner decision evidence intake through the Android Console `HW Evidence`
  action only; it validates target interfaces, target gates, evidence
  references, reviewer identity, and Runtime & Governance policy/audit input,
  but keeps `owner_decision_evidence_persisted=false`,
  `review_queue_updated=false`, `owner_assigned=false`, `gates_closed=false`,
  `activation_allowed=false`, `hardware_accessed=false`,
  `driver_development_triggered=false`, and
  `virtualization_development_triggered=false`.
- `getHardwareInterfaceOwnerDecisionEvidenceStatusJson` exposes the hardware
  owner decision evidence status through the Android Console `HW EvStatus`
  action only; it reports no durable evidence store, no review workflow, zero
  persisted submissions, zero pending reviews, no owner assignment, no gate
  closure, and no hardware activation.
- `getHardwareInterfaceOwnerDecisionEvidenceRetentionChecklistJson` exposes the
  hardware owner evidence retention and closure checklist through the Android
  Console `HW Retain` action only; it reports durable evidence store, URI rule,
  retention policy, review workflow, gate closure authority, delete/export, and
  rollback/fault closure decisions as open while keeping `evidence_store_active=false`,
  `delete_workflow_active=false`, `export_workflow_active=false`,
  `gates_closed=false`, `activation_allowed=false`, and `hardware_accessed=false`.
- `getHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklistJson`
  exposes the hardware owner evidence replacement trigger checklist through the
  Android Console `HW Replace` action only; it reports replacement target,
  adapter readiness, Driver/HAL gap closure evidence, Android/Linux ABI parity,
  rollback-to-empty-interface plan, Safety/Policy review, and smoke harness
  decisions as open while keeping `replacement_allowed=false`,
  `adapter_activation_allowed=false`, `gate_closure_allowed=false`,
  `activation_allowed=false`, and `hardware_accessed=false`.
- `getHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklistJson`
  exposes the selected-adapter readiness evidence checklist through the Android
  Console `HW Adapter` action only; it reports adapter owner, adapter interface
  contract, Driver/HAL gap evidence, Android/Linux binding parity, Safety/Policy
  fault model, smoke harness plan, and rollback-to-empty-interface review as open
  while keeping `adapter_candidate_recorded=false`, `adapter_load_allowed=false`,
  `adapter_activation_allowed=false`, `hardware_access_allowed=false`, and
  `hardware_accessed=false`.
- `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollupJson`
  exposes the adapter-load blocker rollup through the Android Console `HW Load`
  action only; it aggregates activation, owner, evidence, retention, replacement,
  and selected-adapter blockers while keeping `adapter_load_ready=false`,
  `all_blockers_cleared=false`, `adapter_load_allowed=false`,
  `adapter_activation_allowed=false`, `hardware_access_allowed=false`, and
  `hardware_accessed=false`.
- `dryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadJson` exposes the
  adapter-load approval dry-run through the Android Console `HW DryRun` action
  only; it validates request shape against the blocker rollup and returns
  `adapter_load_dry_run_state=rejected_blocked_contract_only` while keeping
  `adapter_load_allowed=false`, `adapter_activation_allowed=false`,
  `hardware_access_allowed=false`, and `hardware_accessed=false`.
- `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatusJson`
  exposes the adapter-load dry-run no-store status through the Android Console
  `HW DryState` action only; it reports `last_result_available=false`,
  `persisted_dry_run_count=0`, `pending_review_count=0`,
  `review_queue_updated=false`, and `hardware_accessed=false` without storing
  requests or results.
- `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistencyJson`
  exposes the read-only adapter-load dry-run audit consistency view through
  the Android Console `HW DryAudit` action only; it cross-checks dry-run,
  status, blocker rollup, and gate families without calling the dry-run POST
  path, persisting requests, loading adapters, or accessing hardware.
- `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklistJson`
  exposes the adapter-load approval authority checklist through the Android
  Console `HW Approve` action only; it reports approval authority, policy,
  signature/RBAC, durable evidence workflow, target smoke, rollback, and fault
  evidence as open without persisting approval records, loading adapters, or
  accessing hardware.
- `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatusJson`
  exposes the approval authority no-store status through the Android Console
  `HW ApStat` action only; it reports `approval_record_available=false`,
  `persisted_approval_record_count=0`, `pending_approval_review_count=0`,
  `approval_decision_passed=false`, and `hardware_accessed=false` without
  creating evidence stores, updating review queues, closing gates, loading
  adapters, or creating Driver/HAL or virtualization work.
- `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistencyJson`
  exposes the approval authority audit consistency view through the Android
  Console `HW ApAudit` action only; it cross-checks approval checklist,
  approval status, dry-run audit consistency, blocker rollup, and `HW-AAC`
  gates without persisting approval records, updating review queues, loading
  adapters, or creating Driver/HAL or virtualization work.
- `getEventSubscriptionActivationApprovalDecisionBlockerRollupJson` exposes
  the Event subscription approval decision blocker rollup through the Android
  Console `Sub ApBlock` action only; it reports `EV-ADB-001..008`,
  `approval_decision_ready=false`, `approval_dry_run_allowed=false`,
  `approval_result_store_created=false`, `review_queue_updated=false`,
  `gates_closed=false`, `broker_activation_allowed=false`, and
  `activation_allowed=false` without executing approval dry-run, creating
  stores or queues, closing gates, starting broker/DDS/high-rate data plane, or
  creating Driver/HAL or virtualization work.
- `dryRunEventSubscriptionActivationApprovalDecisionJson` exposes the Event
  subscription approval decision dry-run request through the Android Console
  `Sub ApDec` action only; it validates the request shape, binds the
  `EV-ADB-001..008` blocker rollup, returns
  `rejected_blocked_contract_only`, and does not persist request/result state,
  create approval result stores, update review queues, close gates, start
  broker/DDS/high-rate data plane, or create Driver/HAL or virtualization work.
- `getEventSubscriptionActivationApprovalDecisionDryRunStatusJson` exposes the
  Event subscription approval decision dry-run no-store status through the
  Android Console `Sub ApDStat` action only; it reports
  `EV-ADS-001..008`, no last approval decision result, zero persisted
  dry-run requests/results/approval decisions, and no dry-run POST call from
  status while keeping stores, queues, gates, broker/DDS/high-rate data plane,
  Driver/HAL, and virtualization inactive.
- `getEventSubscriptionActivationApprovalDecisionDryRunAuditConsistencyJson`
  exposes the Event subscription approval decision dry-run audit consistency
  view through the Android Console `Sub ApDAudit` action only; it reports
  `EV-ADA-001..008`, `consistency_passed=true`, zero persisted dry-run
  request/result/approval decision counters, and
  `decision_dry_run_post_called_by_audit_consistency=false` while keeping
  stores, queues, gates, broker/DDS/high-rate data plane, Driver/HAL, and
  virtualization inactive.
- `getEventSubscriptionActivationApprovalDecisionClosureBlockerMatrixJson`
  exposes the Event subscription approval decision closure blocker matrix
  through the Android Console `Sub ApClose` action only; it reports
  `EV-ACB-001..010`, ten open closure blockers, zero persisted dry-run
  request/result/approval decision counters, and
  `decision_dry_run_post_called_by_closure_blocker_matrix=false` while keeping
  stores, queues, gates, broker/DDS/high-rate data plane, Driver/HAL, and
  virtualization inactive.
- `getEventSubscriptionActivationApprovalDecisionOwnerHandoffChecklistJson`
  exposes the Event subscription approval decision owner handoff checklist
  through the Android Console `Sub ApHand` action only; it reports
  `EV-ACH-001..010`, ten unresolved owner handoff slots, zero assigned owners,
  zero attached evidence, and
  `decision_dry_run_post_called_by_owner_handoff_checklist=false` while keeping
  owner assignment persistence, review queues, gates, broker/DDS/high-rate data
  plane, Driver/HAL, and virtualization inactive.
- `getEventSubscriptionActivationApprovalDecisionOwnerHandoffAuditConsistencyJson`
  exposes the Event subscription approval decision owner handoff audit
  consistency through the Android Console `Sub ApHAud` action only; it reports
  `EV-AHA-001..010`, handoff/closure count parity, source binding, zero
  assigned owners, zero attached evidence, and
  `decision_dry_run_post_called_by_owner_handoff_audit_consistency=false` while
  keeping owner assignment persistence, review queues, gates,
  broker/DDS/high-rate data plane, Driver/HAL, and virtualization inactive.
- `getEventSubscriptionActivationApprovalDecisionOwnerHandoffDecisionRollupJson`
  exposes the Event subscription approval decision owner handoff decision rollup
  through the Android Console `Sub ApHRoll` action only; it reports
  `EV-AHD-001..010`, ten blocked owner/evidence/review/gate/transport
  decisions, zero assigned owners, zero attached evidence, and keeps review
  queues, gates, broker/DDS/high-rate data plane, Driver/HAL, and
  virtualization inactive.
- `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessMatrixJson`
  exposes the Event subscription approval decision owner handoff evidence
  readiness matrix through the Android Console `Sub ApHEv` action only; it
  reports `EV-AHE-001..010`, ten missing evidence packets, zero evidence URI,
  zero owner signatures, and keeps evidence stores, review queues, gates,
  broker/DDS/high-rate data plane, Driver/HAL, and virtualization inactive.
- `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessAuditConsistencyJson`
  exposes the Event subscription approval decision owner handoff evidence
  readiness audit consistency through the Android Console `Sub ApHEvAud` action
  only; it reports `EV-AHF-001..010`, packet count/state parity, source
  binding, Android/Linux parity, no-store, no-POST, and no-side-effect
  consistency while keeping owner assignment, evidence attachment, evidence
  stores, review queues, gates, broker/DDS/high-rate data plane, Driver/HAL,
  and virtualization inactive.
- `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceStatusJson`
  exposes the Event subscription approval decision owner handoff evidence
  acceptance status through the Android Console `Sub ApHAcc` action only; it
  reports `EV-AHG-001..010`, ten blocked acceptance rows, zero accepted evidence
  packets, zero persisted acceptance records, and keeps evidence attachment,
  owner assignment, evidence stores, review queues, gates, broker/DDS/high-rate
  data plane, Driver/HAL, and virtualization inactive.
- `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceAuditConsistencyJson`
  exposes the Event subscription approval decision owner handoff evidence
  acceptance audit consistency through the Android Console `Sub ApHAcAud`
  action only; it reports `EV-AHH-001..010`, acceptance count consistency,
  blocked acceptance state consistency, Android/Linux parity, no-store,
  no-POST, and no-side-effect checks, while keeping evidence acceptance,
  evidence attachment, owner assignment, stores, review queues, gates,
  broker/DDS/high-rate data plane, Driver/HAL, and virtualization inactive.
- `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceDecisionRollupJson`
  exposes the Event subscription approval decision owner handoff evidence
  acceptance decision rollup through the Android Console `Sub ApHDec` action
  only; it reports `EV-AHI-001..010`, decision rollup consistency, ten blocked
  acceptance decision rows, zero accepted evidence packets, zero persisted
  acceptance records, and keeps evidence acceptance, evidence attachment, owner
  assignment, stores, review queues, gates, broker/DDS/high-rate data plane,
  Driver/HAL, and virtualization inactive.
- `dryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionJson`
  exposes the approval decision dry-run through the Android Console `HW ApDec`
  action only; it validates approval decision request shape, authority,
  signature, requester, and approval evidence refs, then returns
  `approval_decision_dry_run_state=rejected_blocked_contract_only` while keeping
  `approval_record_persisted=false`, `approval_decision_persisted=false`,
  `approval_review_queue_updated=false`, `approval_evidence_store_active=false`,
  `adapter_load_allowed=false`, and `hardware_accessed=false`.
- `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunStatusJson`
  exposes the approval decision dry-run status through the Android Console
  `HW ApDStat` action only; it reports `last_approval_decision_result_available=false`,
  `persisted_approval_decision_count=0`, no approval decision review queue, no
  evidence store, and `decision_dry_run_post_called_by_status=false` while
  keeping adapter load, hardware access, Driver/HAL, and virtualization disabled.
- `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunAuditConsistencyJson`
  exposes the approval decision dry-run audit consistency through the Android
  Console `HW ApDAudit` action only; it cross-checks decision dry-run/status,
  approval authority audit/status, and blocker rollup with `HW-APA-001..008`
  while keeping the dry-run POST uncalled, approval decisions unpersisted,
  adapter load, hardware access, Driver/HAL, and virtualization disabled.
- `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionClosureBlockerMatrixJson`
  exposes the approval decision closure blocker matrix through the Android
  Console `HW ApBlock` action only; it lists approval authority, policy,
  signature/RBAC, approval record schema, evidence store, review workflow,
  target smoke, rollback, fault model, Driver/HAL gap closure, audit owner, and
  gate closure blockers with `HW-APM-001..008` while keeping closure, adapter
  load, hardware access, Driver/HAL, and virtualization disabled.
- `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionReviewerMatrixJson`
  exposes the approval decision reviewer matrix through the Android Console
  `HW ApRev` action only; it lists approval authority, policy, signature/RBAC,
  approval record schema, evidence store, review workflow, target smoke,
  rollback/fault, Driver/HAL gap, audit export, and gate closure reviewers with
  `HW-APR-001..008` while keeping reviewers unassigned, closure, adapter load,
  hardware access, Driver/HAL, and virtualization disabled.
- `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffChecklistJson`
  exposes the approval reviewer evidence handoff checklist through the Android
  Console `HW ApHand` action only; it lists required handoff packet fields and
  11 missing reviewer-owned packets with `HW-ARH-001..008` while keeping
  evidence unpersisted, review queues inactive, gates open, adapter load,
  hardware access, Driver/HAL, and virtualization disabled.
- `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceStatusJson`
  exposes the approval reviewer evidence handoff acceptance status through the
  Android Console `HW ApHStat` action only; it reports 11 blocked handoff packet
  acceptance rows with `HW-AHA-001..008` while keeping acceptance records
  unpersisted, review queues inactive, gates open, adapter load, hardware
  access, Driver/HAL, and virtualization disabled.
- `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceAuditConsistencyJson`
  exposes the approval reviewer evidence handoff acceptance audit consistency
  through the Android Console `HW ApHAud` action only; it cross-checks checklist
  and acceptance status counts with `HW-AHC-001..008` while keeping acceptance
  records unpersisted, review queues inactive, gates open, adapter load,
  hardware access, Driver/HAL, and virtualization disabled.
- `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceDecisionRollupJson`
  exposes the approval reviewer evidence handoff acceptance decision rollup
  through the Android Console `HW ApHRoll` action only; it reports 8 blocked
  acceptance decision rows with `HW-AHD-001..008` while keeping acceptance
  records unpersisted, review queues inactive, gates open, adapter load,
  hardware access, Driver/HAL, and virtualization disabled.
- `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessChecklistJson`
  exposes the approval reviewer evidence handoff acceptance closure readiness
  checklist through the Android Console `HW ApHClose` action only; it reports 8
  blocked closure readiness checks with `HW-AHE-001..008` while keeping
  acceptance records unpersisted, review queues inactive, gates open, adapter
  load, hardware access, Driver/HAL, and virtualization disabled.
- `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessAuditConsistencyJson`
  exposes the approval reviewer evidence handoff acceptance closure readiness
  audit consistency view through the Android Console `HW ApHCAud` action only;
  it reports `HW-AHF-001..008`, consistent checklist/count/blocker/rollup
  checks, and keeps acceptance records unpersisted, review queues inactive,
  gates open, adapter load, hardware access, Driver/HAL, and virtualization
  disabled.
- `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionRollupJson`
  exposes the approval reviewer evidence handoff acceptance closure readiness
  decision rollup through the Android Console `HW ApHCRoll` action only; it
  reports `HW-AHG-001..008` blocked closure decisions while keeping acceptance
  records unpersisted, review queues inactive, gates open, adapter load,
  hardware access, Driver/HAL, and virtualization disabled.
- `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentChecklistJson`
  exposes the approval reviewer evidence handoff acceptance closure readiness
  decision reviewer assignment checklist through the Android Console `HW
  ApHCRev` action only; it reports `HW-AHH-001..008`, eight unassigned reviewer
  assignments, no persisted assignments, no review queue update, and keeps
  gates open, adapter load, hardware access, Driver/HAL, and virtualization
  disabled.
- `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistencyJson`
  exposes the approval reviewer evidence handoff acceptance closure readiness
  decision reviewer assignment audit consistency view through the Android
  Console `HW ApHRvA` action only; it reports `HW-AHI-001..008`, source
  checklist binding, assignment counts, unassigned blocker state, no persisted
  assignments, no review queue/gate/load effects, and keeps hardware,
  Driver/HAL, and virtualization disabled.
- `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupJson`
  exposes the approval reviewer evidence handoff acceptance closure readiness
  decision reviewer assignment audit decision rollup through the Android Console
  `HW ApHRvRoll` action only; it reports `HW-AHJ-001..008`, blocks adapter load
  by unassigned reviewers, keeps reviewer assignments unpersisted, and keeps
  hardware, Driver/HAL, and virtualization disabled.
- `getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummaryJson`
  exposes the approval reviewer evidence handoff acceptance closure handoff
  readiness summary through the Android Console `HW ApHReady` action only; it
  reports `HW-AHK-001..008`, keeps eight handoff dependencies open, blocks
  adapter load by unassigned reviewers, keeps reviewer assignments unpersisted,
  and keeps hardware, Driver/HAL, and virtualization disabled.
- `getVehicleSignalsJson` exposes the Vehicle/Body Signal read-only catalog for
  Android integration review through the Android Console `Vehicle Signals`
  action only; it returns VSS-style signal paths, access metadata, adapter
  boundaries, and DRV-GAP-002 linkage but does not load DBC/ARXML, call VHAL or
  HAL, connect SocketCAN/vendor gateways, touch a real vehicle bus, or create
  Driver/HAL or virtualization work.
- `getVehicleSignalActivationJson` exposes Vehicle Signal read-bridge
  activation criteria for Android integration review through the Android
  Console `Signal Gate` action only; it returns DBC/ARXML, VHAL/vendor AIDL,
  Linux SocketCAN, vendor gateway/SOME-IP inputs and `VS-ACT-001..005` gates,
  but does not activate a bridge, touch a real vehicle bus, or create
  Driver/HAL or virtualization work.
- `getVehicleSignalValidationJson` exposes the Vehicle Signal read-bridge
  validation envelope for Android integration review through the Android
  Console `Signal Check` action only; it returns schema-source metadata,
  adapter owner, ABI owner, Android/Linux parity, and DRV-GAP-002 evidence
  gates, but does not parse DBC/ARXML, activate a bridge, touch a real vehicle
  bus, or create Driver/HAL or virtualization work.

## System Service Integration Notes

`docs/CENTRAL_BRAIN_ANDROID_SYSTEM_SERVICE_INTEGRATION.md` records the Android
system/privileged service integration constraints for DEL-001, DEL-003,
DEL-004, DEL-005, XSC-002, XSC-003, XSC-005, XSC-006, HW-002, NV-F-003, NV-F-004, NV-F-005, NV-P-002, NV-P-005,
FW-U-007, FW-S-005, NV-G-005, KH-003, KH-006, and KH-007. It covers target service shapes, manifest permission
constraints, Binder identity to Policy mapping, SELinux/deployment assumptions,
and verification checks.

The note is intentionally documentation-only in this increment. It does not add
Android framework patches, priv-app signing config, sepolicy, Driver/HAL code,
Safety Runtime code, or virtualization code.

## Event Subscription EV-AHJ Closure Readiness

- `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessChecklistJson` exposes `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist` through the Android Console `Sub ApHClose` action. It reports `EV-AHJ-001..010`, `closure_ready=false`, `open_closure_check_count=10`, zero accepted evidence packets, zero persisted acceptance records, no review queue update, open gates, no broker activation, no Driver/HAL access, and no virtualization work. Req IDs: XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-004.

## Event Subscription EV-AHK Closure Readiness Audit

- `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessAuditConsistencyJson` exposes `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency` through the Android Console `Sub ApHClAud` action. It reports `EV-AHK-001..010`, `consistency_passed=true`, closure checklist and source rollup consistency, `closure_ready=false`, `open_closure_check_count=10`, zero accepted evidence packets, zero persisted acceptance records, no review queue update, open gates, no broker activation, no Driver/HAL access, and no virtualization work. Req IDs: XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-004.

## Event Subscription EV-AHL Closure Readiness Decision Rollup

- `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionRollupJson` exposes `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup` through the Android Console `Sub ApHClR` action. It reports `EV-AHL-001..010`, `decision_rollup_complete=true`, `decision_rollup_consistent=true`, closure readiness audit consistency, `closure_ready=false`, `closure_decision_ready=false`, ten blocked decisions, zero accepted evidence packets, zero persisted acceptance records, no review queue update, open gates, no broker activation, no Driver/HAL access, and no virtualization work. Req IDs: XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-004.

## Event Subscription EV-AHM Reviewer Assignment Checklist

- `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentChecklistJson` exposes `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist` through the Android Console `Sub ApHRev` action. It reports `EV-AHM-001..010`, `reviewer_assignment_checklist_complete=true`, `reviewer_assignment_ready=false`, ten unassigned reviewers, zero persisted reviewer assignments, no reviewer assignment queue update, no review queue update, open gates, no broker activation, no Driver/HAL access, and no virtualization work. Req IDs: XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-004.

## Event Subscription EV-AHN Reviewer Assignment Audit Consistency

- `getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistencyJson` exposes `GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency` through the Android Console `Sub ApHRvA` action. It reports `EV-AHN-001..010`, `consistency_passed=true`, reviewer assignment count and blocker state consistency, ten unassigned reviewers, zero persisted reviewer assignments, no reviewer assignment queue update, no review queue update, open gates, no broker activation, no Driver/HAL access, and no virtualization work. Req IDs: XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-001, DEL-004.
