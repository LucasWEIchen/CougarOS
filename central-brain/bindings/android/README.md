# Android Protocol Binding Skeleton

This directory contains the Android Binder/AIDL binding sample for the Central
Brain semantic gateway.

## Scope

- Req IDs: XSC-001, XSC-002, XSC-003, XSC-004, XSC-005, XSC-006, APP-004, FW-U-003, FW-U-004, FW-U-006, NV-P-002, NV-P-006, KH-003, KH-006, DEL-001, DEL-005.
- This is a Binder service/client sample. It does not replace Uni Info Bus or
  SOA semantics, and it does not access drivers, HAL, or virtualization
  directly.
- The Android Console debug APK now binds this service sample before calling
  Uni Info Bus State, AI SDK/Agent task planning, Agent execute, Skill invoke,
  Memory query, Runtime & Governance precheck, shared governance backend target
  contract, and Driver/HAL gap backlog contract mocks. The Binder service
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
| `getAiSdkCapabilitiesJson` | `GET /ai/sdk/capabilities` | XSC-001, APP-004 |
| `planAgentTaskJson` | `POST /agent/plan` | XSC-001, APP-004, NV-F-001, FW-U-006, FW-U-007 |
| `executeAgentTaskJson` | `POST /agent/execute` | XSC-001, APP-004, NV-F-001, FW-U-006, FW-U-007 |
| `listSkillsJson` | `GET /skills` | XSC-001, FW-U-006 |
| `invokeSkillJson` | `POST /skills/{skill_id}/invoke` | XSC-001, FW-U-006, NV-G-005 |
| `queryMemoryJson` | `POST /memory/query` | XSC-001, NV-F-001, FW-U-006 |
| `requestActionJson` | `POST /uib/actions/request` | XSC-002, FW-U-004, FW-U-007, XSC-005, NV-G-005 |
| `listServicesJson` | `GET /soa/services` | XSC-003, FW-S-001..004 |
| `invokeServiceJson` | `POST /soa/invoke` | XSC-003, FW-S-005 |
| `evaluatePolicyJson` | `POST /policy/evaluate` | XSC-005, NV-G-005 |
| `precheckGovernanceJson` | `POST /governance/precheck` | XSC-005, NV-G-002, NV-G-004, NV-G-005, NV-G-006, NV-G-007 |
| `getGovernanceBackendContractJson` | `GET /governance/backend-contract` | XSC-005, XSC-006, NV-G-001..007, NV-P-002, NV-P-003 |
| `getRuntimeGovernanceJson` | `GET /governance/runtime` | XSC-005, NV-G-001..007 |
| `getRecentAuditJson` | `GET /audit/recent` | XSC-005, NV-G-007 |
| `listBindingsJson` | `GET /bindings` | XSC-006, NV-P-001..006 |
| `getBindingDetailJson` | `GET /bindings/detail` | XSC-006, NV-P-002 |
| `getNativeAdaptersDetailJson` | `GET /native/adapters/detail` | XSC-004, NV-F-001, NV-F-003, NV-F-011 |
| `getDriverHalGapsJson` | `GET /native/driver-gaps` | KH-003, KH-006, DEL-005 |

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
- `getDriverHalGapsJson` exposes the Driver/HAL gap backlog for Android
  integration review through the Android Console `Driver Gaps` action only; it
  does not call HAL, device nodes, vendor SDKs, or Safety Runtime.

## System Service Integration Notes

`docs/CENTRAL_BRAIN_ANDROID_SYSTEM_SERVICE_INTEGRATION.md` records the Android
system/privileged service integration constraints for DEL-001, DEL-003,
DEL-004, DEL-005, XSC-002, XSC-003, XSC-005, XSC-006, NV-P-002, NV-P-005,
FW-U-007, FW-S-005, NV-G-005, KH-003, and KH-006. It covers target service shapes, manifest permission
constraints, Binder identity to Policy mapping, SELinux/deployment assumptions,
and verification checks.

The note is intentionally documentation-only in this increment. It does not add
Android framework patches, priv-app signing config, sepolicy, Driver/HAL code,
Safety Runtime code, or virtualization code.
