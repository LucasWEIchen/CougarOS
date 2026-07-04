# Linux Protocol Binding Skeleton

This directory contains Linux-side protocol binding skeletons for the Central
Brain semantic gateway.

## Scope

- Req IDs: XSC-002, XSC-003, XSC-005, XSC-006, NV-P-002, NV-P-003, DEL-002.
- `proto/central_brain_gateway.proto` defines the gRPC/RPC surface.
- `ipc/central_brain_ipc_envelope.schema.json` defines the Unix domain socket
  JSON envelope for a lightweight local IPC daemon.
- These files are binding contracts only. They do not implement SOME/IP, DDS,
  MQTT, drivers, HAL, or virtualization.

## Mapping

| Binding operation | Semantic endpoint | Req IDs |
| --- | --- | --- |
| `uib.context.get` | `GET /uib/context` | XSC-002, FW-U-001 |
| `uib.state.get` | `GET /uib/state` | XSC-002, FW-U-002 |
| `soa.services.list` | `GET /soa/services` | XSC-003, FW-S-001..004 |
| `soa.service.invoke` | `POST /soa/invoke` | XSC-003, FW-S-005 |
| `policy.evaluate` | `POST /policy/evaluate` | XSC-005, NV-G-005 |
| `governance.runtime.get` | `GET /governance/runtime` | XSC-005, NV-G-001..007 |
| `audit.recent.get` | `GET /audit/recent` | XSC-005, NV-G-007 |
| `bindings.list` | `GET /bindings` | XSC-006, NV-P-001..006 |

## Delivery Assumptions

- Local Linux daemon integration should start with Unix domain sockets for
  same-SoC IPC and add gRPC when cross-process or cross-host tooling needs it.
- SOME/IP and DDS remain separate vehicle-network/high-rate topic bindings and
  are not implemented in this prototype.
- Policy and lifecycle checks stay in Runtime & Governance regardless of the
  selected transport.
