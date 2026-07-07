# Linux Protocol Binding

This directory contains Linux-side protocol binding skeletons for the Central
Brain semantic gateway.

## Scope

- Req IDs: XSC-001, XSC-002, XSC-003, XSC-004, XSC-005, XSC-006, APP-004, FW-U-003, FW-U-004, FW-U-006, HW-002, KH-003, KH-006, KH-007, NV-F-003, NV-F-004, NV-F-005, NV-P-002, NV-P-003, NV-P-006, DEL-002, DEL-005.
- `proto/central_brain_gateway.proto` defines the gRPC/RPC surface.
- `grpc/central_brain_grpc_server.py` and `grpc/central_brain_grpc_client.py`
  are dependency-free JSON TCP samples that mirror the proto request/response
  fields and validate NV-P-003 behavior in the current workspace.
- `ipc/central_brain_ipc_envelope.schema.json` defines the Unix domain socket
  JSON envelope for a lightweight local IPC daemon.
- `ipc/central_brain_ipc_daemon.py` is an active Unix socket sample that maps
  IPC envelopes to the architecture-aligned semantic gateway and calls a shared
  Runtime & Governance socket precheck for `soa.service.invoke` when configured;
  runtime and audit diagnostics use the same shared socket before REST fallback.
- `ipc/central_brain_governance_daemon.py` is a Linux Runtime & Governance
  socket sample for shared `governance.precheck`, `governance.runtime.get`,
  and `audit.recent.get` visibility across local binding processes.
- `ipc/central_brain_governance_client.py` is the reusable Linux helper used
  by both IPC and gRPC/RPC samples to call the shared governance socket without
  duplicating the Runtime & Governance envelope.
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
| `uib.events.subscriptions.get` | `GET /uib/events/subscriptions` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-002 |
| `uib.events.subscriptions.request` | `POST /uib/events/subscriptions/request` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-006, DEL-002 |
| `uib.events.subscriptions.cancel` | `POST /uib/events/subscriptions/cancel` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-006, DEL-002 |
| `uib.events.subscriptions.transport.readiness` | `GET /uib/events/subscriptions/transport-readiness` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-002, DEL-004 |
| `uib.events.subscriptions.decision.matrix` | `GET /uib/events/subscriptions/decision-matrix` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-002, DEL-004 |
| `uib.events.subscriptions.activation.checklist` | `GET /uib/events/subscriptions/activation-checklist` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-002, DEL-004 |
| `uib.events.subscriptions.callback.watch.shape` | `GET /uib/events/subscriptions/callback-watch-shape` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-002, DEL-004 |
| `uib.events.subscriptions.cursor.replay.storage` | `GET /uib/events/subscriptions/cursor-replay-storage` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-002, DEL-004 |
| `uib.events.subscriptions.backpressure.qos.evidence` | `GET /uib/events/subscriptions/backpressure-qos-evidence` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-002, DEL-004 |
| `uib.events.subscriptions.readiness.rollup` | `GET /uib/events/subscriptions/readiness-rollup` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-002, DEL-004 |
| `uib.events.subscriptions.activation.evidence` | `POST /uib/events/subscriptions/activation-evidence` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-002, DEL-004 |
| `uib.events.subscriptions.activation.evidence.status` | `GET /uib/events/subscriptions/activation-evidence/status` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-002, DEL-004 |
| `uib.events.subscriptions.activation.evidence.retention.checklist` | `GET /uib/events/subscriptions/activation-evidence/retention-checklist` | XSC-002, FW-U-003, XSC-005, XSC-006, NV-P-002, NV-P-003, NV-P-006, DEL-002, DEL-004 |
| `uib.extensions.get` | `GET /uib/extensions` | XSC-002, FW-U-008, XSC-005, XSC-006 |
| `uib.actions.request` | `POST /uib/actions/request` | XSC-002, FW-U-004, FW-U-007, XSC-005, NV-G-005 |
| `ai.sdk.capabilities` | `GET /ai/sdk/capabilities` | XSC-001, APP-004 |
| `agent.plan` | `POST /agent/plan` | XSC-001, APP-004, NV-F-001, FW-U-006, FW-U-007 |
| `agent.execute` | `POST /agent/execute` | XSC-001, APP-004, NV-F-001, FW-U-006, FW-U-007 |
| `skills.list` | `GET /skills` | XSC-001, FW-U-006 |
| `skills.invoke` | `POST /skills/{skill_id}/invoke` | XSC-001, FW-U-006, NV-G-005 |
| `memory.query` | `POST /memory/query` | XSC-001, NV-F-001, FW-U-006 |
| `soa.services.list` | `GET /soa/services` | XSC-003, FW-S-001..004 |
| `soa.contracts.get` | `GET /soa/contracts` | XSC-003, FW-S-004, NV-G-003 |
| `soa.service.invoke` | shared Linux governance daemon precheck with local fallback -> `POST /soa/invoke` | XSC-003, XSC-005, FW-S-005, NV-G-002, NV-G-004, NV-G-005, NV-G-006, NV-G-007 |
| `policy.evaluate` | `POST /policy/evaluate` | XSC-005, NV-G-005 |
| `governance.precheck` | `POST /governance/precheck` | XSC-005, NV-G-002, NV-G-004, NV-G-005, NV-G-006, NV-G-007 |
| `governance.backend.contract.get` | `GET /governance/backend-contract` | XSC-005, XSC-006, NV-G-001..007, NV-P-002, NV-P-003 |
| `governance.migration.check` | `GET /governance/migration-check` | XSC-005, XSC-006, NV-G-001, NV-G-002, NV-G-004, NV-G-005, NV-G-006, NV-G-007, NV-P-002, NV-P-003, DEL-002, DEL-003, DEL-004 |
| `governance.deployment.plan.get` | `GET /governance/deployment-plan` | XSC-005, XSC-006, NV-G-001..007, NV-P-002, NV-P-003, DEL-001, DEL-002, DEL-003, DEL-004 |
| `governance.runtime.get` | shared governance socket diagnostic, REST fallback to `GET /governance/runtime` | XSC-005, NV-G-001..007 |
| `audit.recent.get` | shared governance socket diagnostic, REST fallback to `GET /audit/recent` | XSC-005, NV-G-007 |
| `bindings.list` | `GET /bindings` | XSC-006, NV-P-001..006 |
| `bindings.readiness.get` | `GET /bindings/readiness` | XSC-006, NV-P-001..006, DEL-002, DEL-003, DEL-004 |
| `delivery.readiness.get` | `GET /delivery/readiness` | DEL-001..005, XSC-001..006 |
| `prototype.readiness.get` | `GET /prototype/readiness` | XSC-001..006, DEL-001..005 |
| `hardware.interfaces.get` | `GET /hardware/interfaces` | XSC-004, XSC-006, HW-002, KH-001, KH-002, KH-003, KH-006, KH-007, DEL-002, DEL-005 |
| `hardware.interfaces.activation.checklist` | `GET /hardware/interfaces/activation-checklist` | XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-002, DEL-005 |
| `hardware.interfaces.owner.decision.status` | `GET /hardware/interfaces/owner-decision-status` | XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-002, DEL-005 |
| `vehicle.signals.list` | `GET /vehicle/signals` | XSC-002, XSC-004, XSC-006, NV-F-004, NV-F-005, NV-P-002, NV-P-003, DEL-002, DEL-005 |
| `vehicle.signals.activation.get` | `GET /vehicle/signals/activation` | XSC-002, XSC-004, XSC-006, NV-F-003, NV-F-004, NV-F-005, NV-P-001, NV-P-002, NV-P-003, DEL-002, DEL-005 |
| `vehicle.signals.validation.get` | `GET /vehicle/signals/validation` | XSC-002, XSC-004, XSC-006, NV-F-003, NV-F-004, NV-F-005, NV-P-001, NV-P-002, NV-P-003, KH-003, KH-006, KH-007, DEL-002, DEL-005 |

The gRPC/RPC JSON sample maps the same semantic endpoints through
`CentralBrainGateway.*` RPC names from `proto/central_brain_gateway.proto`.
`CentralBrainGateway.GetServiceContracts` exposes the same SOA contract catalog
as Android Binder and Linux IPC without invoking services.
`CentralBrainGateway.GetUibExtensions` exposes the same FW-U-008 extension
registry contract as Android Binder and Linux IPC without loading plugins or
dispatching services.
`CentralBrainGateway.GetEventSubscriptions`, `RequestEventSubscription`,
`CancelEventSubscription`, `GetEventSubscriptionTransportReadiness`, and
`GetEventSubscriptionDecisionMatrix`, and
`GetEventSubscriptionActivationChecklist`,
`GetEventSubscriptionCallbackWatchShape`,
`GetEventSubscriptionCursorReplayStorage`, and
`GetEventSubscriptionBackpressureQosEvidence`, and
`GetEventSubscriptionReadinessRollup` and
`SubmitEventSubscriptionActivationEvidence` and
`GetEventSubscriptionActivationEvidenceStatus` and
`GetEventSubscriptionActivationEvidenceRetentionChecklist` expose the same FW-U-003/NV-P-006
Event subscription lifecycle, transport readiness, owner decision matrix,
activation evidence intake/review/retention checklist,
activation evidence checklist, callback/watch API shape, cursor/replay storage,
backpressure/QoS evidence, readiness rollup, and activation evidence review status contracts as Android Binder and Linux IPC without
assigning production owners, selecting a transport, persisting subscriptions,
activating event QoS, closing readiness gates, starting a broker, callback/watch path, SSE/WebSocket, DDS runtime, high-rate
data plane, Driver/HAL, or virtualization work.
`CentralBrainGateway.InvokeService` calls the shared Linux governance daemon
through the same helper as Linux IPC before forwarding allowed SOA calls and
falls back to local Runtime & Governance when the shared socket is unavailable.
`CentralBrainGateway.GetGovernanceBackendContract` exposes the same target
shared governance backend contract as Binder and Linux IPC; it is metadata for
transport replacement, not a production governance backend implementation.
`CentralBrainGateway.GetGovernanceMigrationCheck` exposes the same read-only
replacement readiness check as Binder and Linux IPC; it keeps production
backend invariants visible without implementing that backend.
`CentralBrainGateway.GetGovernanceDeploymentPlan` exposes the Android system
service, Linux daemon, and true gRPC/RPC deployment-shape contract; it records
open deployment decisions without implementing the production backend.
`CentralBrainGateway.GetBindingReadiness` exposes Android Binder, Linux IPC,
Linux gRPC/RPC, REST, MQTT, SOME/IP, and DDS readiness, blockers, validation
commands, and non-goal boundaries without implementing production transports.
`CentralBrainGateway.GetDeliveryReadiness` exposes Android/Linux delivery
sample status, validation commands, blockers, and non-goal boundaries without
implementing production services, packaging, Driver/HAL, or virtualization.
`CentralBrainGateway.GetPrototypeReadiness` exposes the Python prototype
module maturity view, Android/Linux binding visibility, deviations, issues,
and next increment candidates without dispatching services, touching hardware,
creating Driver/HAL scope, or creating virtualization work.
`CentralBrainGateway.GetHardwareInterfaces` exposes the same hardware
empty-interface registry as Android Binder and Linux IPC without touching
devices, HALs, shared memory, vehicle bus, or virtualization APIs.
`CentralBrainGateway.GetHardwareInterfaceActivationChecklist` exposes the same
hardware activation checklist as Android Binder and Linux IPC without
activating hardware, opening device nodes, dispatching services, or creating
Driver/HAL or virtualization work.
`CentralBrainGateway.GetHardwareInterfaceOwnerDecisionStatus` exposes the same
hardware owner decision rollup as Android Binder and Linux IPC without assigning
owners, closing activation gates, opening device nodes, dispatching services, or
creating Driver/HAL or virtualization work.
`CentralBrainGateway.GetVehicleSignals` exposes the same read-only Vehicle/Body
Signal catalog as Android Binder and Linux IPC without loading DBC/ARXML,
calling VHAL/HAL, connecting SocketCAN/vendor gateways, touching a real vehicle
bus, or creating Driver/HAL or virtualization work.
`CentralBrainGateway.GetVehicleSignalActivation` exposes the same Vehicle
Signal read-bridge activation criteria as Android Binder and Linux IPC without
loading DBC/ARXML, connecting VHAL/SocketCAN/vendor gateways, touching a real
vehicle bus, or creating Driver/HAL or virtualization work.
`CentralBrainGateway.GetVehicleSignalValidation` exposes the same Vehicle
Signal read-bridge validation envelope as Android Binder and Linux IPC without
parsing DBC/ARXML, connecting VHAL/SocketCAN/vendor gateways, touching a real
vehicle bus, or creating Driver/HAL or virtualization work.
`CentralBrainGateway.GetRuntimeGovernance` and
`CentralBrainGateway.GetRecentAudit` use the same shared governance client as
IPC for read-only diagnostics before falling back to the REST prototype gateway.

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
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscriptions
  CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscribe-request
  CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscribe-cancel
  CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-transport-readiness
  CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-decision-matrix
  CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-activation-checklist
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-callback-watch-shape
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-cursor-replay-storage
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-backpressure-qos-evidence
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-readiness-rollup
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-activation-evidence
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-activation-evidence-status
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py event-subscription-activation-evidence-retention-checklist
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py extensions
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
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py service-contracts
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py governance-precheck
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py governance-backend-contract
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py governance-migration-check
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py binding-readiness
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py delivery-readiness
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py prototype-readiness
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py hardware-interfaces
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py hardware-interface-activation-checklist
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py hardware-interface-owner-decision-status
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py vehicle-signals
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py vehicle-signal-activation
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py vehicle-signal-validation
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py governance
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py audit
CENTRAL_BRAIN_IPC_SOCKET=/tmp/central_brain_gateway.sock \
  python3 central-brain/bindings/linux/ipc/central_brain_ipc_client.py infer-denied
```

The shared governance socket also accepts direct diagnostic envelopes for
`governance.runtime.get` and `audit.recent.get`. These operations expose the
same XSC-005 registry/lifecycle/QoS and NV-G-007 audit state used by SOA
prechecks without dispatching a service, Driver/HAL, or virtualization layer.

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
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py service-contracts
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py extensions
  CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscriptions
  CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscribe-request
  CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscribe-cancel
  CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-transport-readiness
  CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-decision-matrix
  CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-activation-checklist
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-callback-watch-shape
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-cursor-replay-storage
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-backpressure-qos-evidence
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-readiness-rollup
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-activation-evidence
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-activation-evidence-status
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py event-subscription-activation-evidence-retention-checklist
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py governance-precheck
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py governance-backend-contract
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py governance-migration-check
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py binding-readiness
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py delivery-readiness
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py prototype-readiness
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py hardware-interfaces
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py hardware-interface-activation-checklist
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py hardware-interface-owner-decision-status
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py vehicle-signals
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py vehicle-signal-activation
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py vehicle-signal-validation
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py governance
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 central-brain/bindings/linux/grpc/central_brain_grpc_client.py audit
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
- IPC and gRPC/RPC samples share `central_brain_governance_client.py` for the
  `governance.precheck`, `governance.runtime.get`, and `audit.recent.get`
  socket envelopes, so a future production governance backend can replace that
  boundary once instead of separately per transport.
- `/governance/backend-contract` and the `governance.backend.contract.get` /
  `GetGovernanceBackendContract` binding operations document that future
  replacement boundary across Binder, IPC, and gRPC/RPC without implementing a
  production governance backend in this sample.
- `/governance/migration-check` and the `governance.migration.check` /
  `GetGovernanceMigrationCheck` binding operations document the read-only
  replacement readiness invariants and keep `production_backend_ready=false`
  until a target deployment shape exists.
- `/bindings/readiness` and the `bindings.readiness.get` /
  `GetBindingReadiness` binding operations document current binding maturity,
  blockers, validation commands, and planned transport non-goals while keeping
  `production_ready=false`.
- `/delivery/readiness` and the `delivery.readiness.get` /
  `GetDeliveryReadiness` binding operations document Android/Linux delivery
  sample maturity, validation bundle, blockers, and non-goal boundaries while
  keeping `production_ready=false`.
- `/prototype/readiness` and the `prototype.readiness.get` /
  `GetPrototypeReadiness` binding operations document Python prototype module
  maturity, Android/Linux binding visibility, deviations, issues, next
  increment candidates, and non-goal boundaries while keeping
  `production_ready=false`, `hardware_accessed=false`,
  `driver_development_triggered=false`, and
  `virtualization_development_triggered=false`.
- `/hardware/interfaces/activation-checklist` and the
  `hardware.interfaces.activation.checklist` /
  `GetHardwareInterfaceActivationChecklist` binding operations document
  HW-002/KH owner, ABI, Driver/HAL gap, Safety/Policy, smoke evidence, and
  rollback/fault gates while keeping `activation_allowed=false`,
  `hardware_accessed=false`, `driver_development_triggered=false`, and
  `virtualization_development_triggered=false`.
- `/hardware/interfaces/owner-decision-status` and the
  `hardware.interfaces.owner.decision.status` /
  `GetHardwareInterfaceOwnerDecisionStatus` binding operations document
  HW-002/KH target owner, Android ABI owner, Linux ABI owner, Driver/HAL gap
  owner, Safety/Policy owner, target smoke evidence owner, and rollback/fault
  semantics owner status while keeping `activation_allowed=false`,
  `hardware_accessed=false`, `driver_development_triggered=false`, and
  `virtualization_development_triggered=false`.
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
  precheck decisions and make them visible through direct `audit.recent.get`
  socket diagnostics for Linux integration tests.
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
