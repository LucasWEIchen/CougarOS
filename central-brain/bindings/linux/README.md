# Linux Protocol Binding

This directory contains Linux-side protocol binding skeletons for the Central
Brain semantic gateway.

## Scope

- Req IDs: XSC-001, XSC-002, XSC-003, XSC-005, XSC-006, APP-004, FW-U-003, FW-U-004, NV-P-002, NV-P-003, NV-P-006, DEL-002.
- `proto/central_brain_gateway.proto` defines the gRPC/RPC surface.
- `ipc/central_brain_ipc_envelope.schema.json` defines the Unix domain socket
  JSON envelope for a lightweight local IPC daemon.
- `ipc/central_brain_ipc_daemon.py` is an active Unix socket sample that maps
  IPC envelopes to the architecture-aligned semantic gateway.
- `ipc/central_brain_ipc_client.py` is a Linux client sample for the same IPC
  envelope.
- These files do not implement SOME/IP, DDS, MQTT, drivers, HAL, or
  virtualization.

## Mapping

| Binding operation | Semantic endpoint | Req IDs |
| --- | --- | --- |
| `uib.context.get` | `GET /uib/context` | XSC-002, FW-U-001 |
| `uib.state.get` | `GET /uib/state` | XSC-002, FW-U-002 |
| `uib.events.topics` | `GET /uib/events/topics` | XSC-002, FW-U-003, NV-P-006 |
| `uib.events.publish` | `POST /uib/events/publish` | XSC-002, FW-U-003, NV-P-006 |
| `uib.events.recent` | `GET /uib/events/recent` | XSC-002, FW-U-003, NV-P-006 |
| `uib.actions.request` | `POST /uib/actions/request` | XSC-002, FW-U-004, FW-U-007, XSC-005, NV-G-005 |
| `ai.sdk.capabilities` | `GET /ai/sdk/capabilities` | XSC-001, APP-004 |
| `agent.plan` | `POST /agent/plan` | XSC-001, APP-004, NV-F-001, FW-U-006, FW-U-007 |
| `soa.services.list` | `GET /soa/services` | XSC-003, FW-S-001..004 |
| `soa.service.invoke` | `POST /soa/invoke` | XSC-003, FW-S-005 |
| `policy.evaluate` | `POST /policy/evaluate` | XSC-005, NV-G-005 |
| `governance.runtime.get` | `GET /governance/runtime` | XSC-005, NV-G-001..007 |
| `audit.recent.get` | `GET /audit/recent` | XSC-005, NV-G-007 |
| `bindings.list` | `GET /bindings` | XSC-006, NV-P-001..006 |

## Unix Socket Sample

Run the backend first, then start the IPC daemon:

```bash
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_daemon.py \
  --socket-path /tmp/central_brain_gateway.sock
```

Call it with the sample client:

```bash
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py state
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-publish
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py agent-plan
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py action-request
```

Validate daemon/client behavior:

```bash
bash tools/smoke_central_brain_linux_ipc.sh
```

For systemd deployment samples, see:

```bash
sed -n '1,220p' central-brain/deploy/linux/README.md
bash tools/check_central_brain_delivery_docs.sh
```

## Delivery Assumptions

- Local Linux daemon integration should start with Unix domain sockets for
  same-SoC IPC and add gRPC when cross-process or cross-host tooling needs it.
- The current IPC daemon is an active sample, not a full production gateway; it
  forwards to the REST prototype binding while preserving Uni Info Bus and SOA
  semantic operations.
- SOME/IP and DDS remain separate vehicle-network/high-rate topic bindings and
  are not implemented in this prototype.
- Policy and lifecycle checks stay in Runtime & Governance regardless of the
  selected transport.
