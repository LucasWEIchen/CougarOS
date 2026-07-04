# Linux Protocol Binding

This directory contains Linux-side protocol binding skeletons for the Central
Brain semantic gateway.

## Scope

- Req IDs: XSC-001, XSC-002, XSC-003, XSC-005, XSC-006, APP-004, FW-U-003, FW-U-004, FW-U-006, NV-P-002, NV-P-003, NV-P-006, DEL-002.
- `proto/central_brain_gateway.proto` defines the gRPC/RPC surface.
- `grpc/central_brain_grpc_server.py` and `grpc/central_brain_grpc_client.py`
  are dependency-free JSON TCP samples that mirror the proto request/response
  fields and validate NV-P-003 behavior in the current workspace.
- `ipc/central_brain_ipc_envelope.schema.json` defines the Unix domain socket
  JSON envelope for a lightweight local IPC daemon.
- `ipc/central_brain_ipc_daemon.py` is an active Unix socket sample that maps
  IPC envelopes to the architecture-aligned semantic gateway and calls a shared
  Runtime & Governance socket precheck for `soa.service.invoke` when configured.
- `ipc/central_brain_governance_daemon.py` is a Linux Runtime & Governance
  socket sample for shared `governance.precheck` decisions across local binding
  processes.
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
| `agent.execute` | `POST /agent/execute` | XSC-001, APP-004, NV-F-001, FW-U-006, FW-U-007 |
| `skills.list` | `GET /skills` | XSC-001, FW-U-006 |
| `skills.invoke` | `POST /skills/{skill_id}/invoke` | XSC-001, FW-U-006, NV-G-005 |
| `memory.query` | `POST /memory/query` | XSC-001, NV-F-001, FW-U-006 |
| `soa.services.list` | `GET /soa/services` | XSC-003, FW-S-001..004 |
| `soa.service.invoke` | shared Linux governance daemon precheck with local fallback -> `POST /soa/invoke` | XSC-003, XSC-005, FW-S-005, NV-G-002, NV-G-004, NV-G-005, NV-G-006, NV-G-007 |
| `policy.evaluate` | `POST /policy/evaluate` | XSC-005, NV-G-005 |
| `governance.precheck` | `POST /governance/precheck` | XSC-005, NV-G-002, NV-G-004, NV-G-005, NV-G-006, NV-G-007 |
| `governance.runtime.get` | `GET /governance/runtime` | XSC-005, NV-G-001..007 |
| `audit.recent.get` | `GET /audit/recent` | XSC-005, NV-G-007 |
| `bindings.list` | `GET /bindings` | XSC-006, NV-P-001..006 |

The gRPC/RPC JSON sample maps the same semantic endpoints through
`CentralBrainGateway.*` RPC names from `proto/central_brain_gateway.proto`.
`CentralBrainGateway.InvokeService` calls the shared Linux governance daemon
before forwarding allowed SOA calls and falls back to local Runtime &
Governance when the shared socket is unavailable.

## Unix Socket Sample

Run the backend and governance daemon first, then start the IPC daemon:

```bash
CENTRAL_BRAIN_GOVERNANCE_SOCKET=/tmp/central_brain_governance.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_governance_daemon.py \
  --socket-path /tmp/central_brain_governance.sock
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 \
  CENTRAL_BRAIN_GOVERNANCE_SOCKET=/tmp/central_brain_governance.sock \
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
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py agent-execute
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py skill-invoke
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py memory-query
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py action-request
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py governance-precheck
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py infer-denied
```

Validate daemon/client behavior:

```bash
bash tools/smoke_central_brain_linux_ipc.sh
```

## gRPC/RPC Contract Sample

The current workspace does not include `grpcio`, so this sample verifies the
gRPC/RPC contract with a small standard-library TCP JSON wrapper. It is an
active Linux delivery sample for NV-P-003, not a production gRPC server.

Run the backend and governance daemon first, then start the gRPC/RPC sample:

```bash
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 \
  CENTRAL_BRAIN_GOVERNANCE_SOCKET=/tmp/central_brain_governance.sock \
  CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_server.py \
  --host 127.0.0.1 --port 18788
```

Call it with the sample client:

```bash
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py state
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py governance-precheck
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py infer-denied
```

Validate behavior:

```bash
bash tools/smoke_central_brain_linux_grpc.sh
```

For systemd deployment samples, see:

```bash
sed -n '1,220p' central-brain/deploy/linux/README.md
bash tools/check_central_brain_delivery_docs.sh
```

## Delivery Assumptions

- Local Linux daemon integration should start with Unix domain sockets for
  same-SoC IPC and add gRPC when cross-process or cross-host tooling needs it.
- The current gRPC/RPC sample mirrors proto fields over JSON TCP because
  `grpcio` is unavailable; target images can replace only the transport while
  keeping the same RPC names, Req IDs, and governance precheck behavior.
- The current IPC daemon is an active sample, not a full production gateway; it
  applies a shared Linux governance daemon precheck to SOA service invocations
  when `CENTRAL_BRAIN_GOVERNANCE_SOCKET` is configured, falls back to local
  Runtime & Governance precheck if unavailable, then forwards allowed calls to
  the REST prototype binding while preserving Uni Info Bus and SOA semantic
  operations.
- `CENTRAL_BRAIN_IPC_AUDIT_LOG` can persist IPC-side precheck decisions for
  Linux integration tests; it is separate from the backend
  `CENTRAL_BRAIN_AUDIT_LOG` sample.
- `CENTRAL_BRAIN_GOVERNANCE_AUDIT_LOG` can persist shared governance daemon
  decisions for Linux integration tests.
- `CENTRAL_BRAIN_GRPC_AUDIT_LOG` can persist gRPC/RPC sample local fallback
  precheck decisions separately from the backend and IPC audit logs.
- SOME/IP and DDS remain separate vehicle-network/high-rate topic bindings and
  are not implemented in this prototype.
- Policy and lifecycle checks stay in Runtime & Governance regardless of the
  selected transport.
- `governance.precheck` exposes discovery, Policy, Lifecycle, and QoS decisions
  without service dispatch; the default request uses `consume_qos=false`.
- Agent execute, Skill invoke, and Memory query are contract mocks that expose
  AIOS Kernel/Tool/Memory boundaries without running real Skill sandbox,
  persistent Memory store, Driver/HAL, vehicle bus, or virtualization code.
