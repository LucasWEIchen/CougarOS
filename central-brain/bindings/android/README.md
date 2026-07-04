# Android Protocol Binding Skeleton

This directory contains the Android Binder/AIDL binding sample for the Central
Brain semantic gateway.

## Scope

- Req IDs: XSC-002, XSC-003, XSC-004, XSC-005, XSC-006, FW-U-003, NV-P-002, NV-P-006, DEL-001.
- This is a Binder service/client sample. It does not replace Uni Info Bus or
  SOA semantics, and it does not access drivers, HAL, or virtualization
  directly.
- The Android Console debug APK now binds this service sample before calling
  Uni Info Bus State and SOA Inference. The Binder service sample still proxies
  to the REST semantic gateway as its upstream prototype binding.

## Mapping

| AIDL method | Semantic endpoint | Req IDs |
| --- | --- | --- |
| `getContextJson` | `GET /uib/context` | XSC-002, FW-U-001 |
| `getStateJson` | `GET /uib/state` | XSC-002, FW-U-002 |
| `listEventTopicsJson` | `GET /uib/events/topics` | XSC-002, FW-U-003, NV-P-006 |
| `publishEventJson` | `POST /uib/events/publish` | XSC-002, FW-U-003, NV-P-006 |
| `getRecentEventsJson` | `GET /uib/events/recent` | XSC-002, FW-U-003, NV-P-006 |
| `listServicesJson` | `GET /soa/services` | XSC-003, FW-S-001..004 |
| `invokeServiceJson` | `POST /soa/invoke` | XSC-003, FW-S-005 |
| `evaluatePolicyJson` | `POST /policy/evaluate` | XSC-005, NV-G-005 |
| `getRuntimeGovernanceJson` | `GET /governance/runtime` | XSC-005, NV-G-001..007 |
| `getRecentAuditJson` | `GET /audit/recent` | XSC-005, NV-G-007 |
| `listBindingsJson` | `GET /bindings` | XSC-006, NV-P-001..006 |
| `getBindingDetailJson` | `GET /bindings/detail` | XSC-006, NV-P-002 |
| `getNativeAdaptersDetailJson` | `GET /native/adapters/detail` | XSC-004, NV-F-001, NV-F-003, NV-F-011 |

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
