# Android Protocol Binding Skeleton

This directory contains the Android Binder/AIDL binding skeleton for the
Central Brain semantic gateway.

## Scope

- Req IDs: XSC-002, XSC-003, XSC-005, XSC-006, NV-P-002, DEL-001.
- This is a binding contract only. It does not replace Uni Info Bus or SOA
  semantics, and it does not access drivers, HAL, or virtualization directly.
- The current Android Console still uses the REST prototype binding while this
  AIDL shape is used as the target integration contract.

## Mapping

| AIDL method | Semantic endpoint | Req IDs |
| --- | --- | --- |
| `getContextJson` | `GET /uib/context` | XSC-002, FW-U-001 |
| `getStateJson` | `GET /uib/state` | XSC-002, FW-U-002 |
| `listServicesJson` | `GET /soa/services` | XSC-003, FW-S-001..004 |
| `invokeServiceJson` | `POST /soa/invoke` | XSC-003, FW-S-005 |
| `evaluatePolicyJson` | `POST /policy/evaluate` | XSC-005, NV-G-005 |
| `getRuntimeGovernanceJson` | `GET /governance/runtime` | XSC-005, NV-G-001..007 |
| `getRecentAuditJson` | `GET /audit/recent` | XSC-005, NV-G-007 |
| `listBindingsJson` | `GET /bindings` | XSC-006, NV-P-001..006 |

## Delivery Assumptions

- Android production integration should wrap this AIDL in a system or privileged
  service depending on the target AAOS image.
- Permission checks remain in Runtime & Governance; Binder caller identity is an
  input to policy, not a replacement for policy.
- Stable parcelable models can replace JSON after the semantic contract settles.
