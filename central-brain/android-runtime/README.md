# Central Brain Android Runtime

This is the Android 13 product-oriented build root introduced by R1. It does not replace or modify vendor Android Framework/BSP sources, and it does not embed the Python prototype in an APK.

Req IDs: `APP-004`, `XSC-001`, `XSC-004`, `XSC-005`, `XSC-006`, `NV-F-001`, `NV-P-002`, `DEL-001`, `DEL-003`, `DEL-004`, `DEL-005`.

## Modules

| Module | Artifact | Current responsibility |
| --- | --- | --- |
| `central-brain-sdk` | AAR | Public typed task/Governance/Scenario clients, structured task/Governance/Session/Plan/Event/Effect AIDL types, callback bridge and protocol identity |
| `runtime-service` | APK without launcher | Three signature-protected Services; task/diagnostic/Governance plus Runtime dual-action Session/Event Binder publication |
| `demo-hmi` | Launcher APK | Source-built typed Binder integration client for Android hardware testing |
| `policy-probe` | Test-only APK | Same-signer, unconfigured-package default-deny device probe; excluded from standard delivery build |

R2A added compiled, structured production and diagnostic AIDL contracts to `central-brain-sdk`. R2B publishes them from separate exported Services protected by `com.centralbrain.permission.BIND_RUNTIME` and `com.centralbrain.permission.ACCESS_DIAGNOSTICS`. R3C2 adds an independent Governance Service protected by `com.centralbrain.permission.BIND_GOVERNANCE`. Demo HMI requests task and Governance access, binds through the two typed SDK clients, and never requests diagnostics. No module requests network, vehicle, device-node, camera, audio, location, or hardware permissions.

The Runtime debug variant adds `RuntimeProbeActivity` and `DiagnosticProbeActivity` only under `src/debug`. Both are ADB test probes protected by the platform `android.permission.DUMP` permission. The release APK contains neither activity.

## R2 Protocol Binding

- Production: `com.centralbrain.sdk.production.ICentralBrainRuntime`
- Oneway callback: `com.centralbrain.sdk.production.ICentralBrainTaskCallback`
- Diagnostics: `com.centralbrain.sdk.diagnostics.ICentralBrainDiagnostics`
- Frozen source checksum list: `central-brain-sdk/aidl-api/v1.sha256`
- Detailed semantics: `docs/CENTRAL_BRAIN_ANDROID_AIDL_CONTRACT.md`

Production AIDL contains only typed task fields; JSON, `Bundle`, file descriptors and shared memory are rejected by `tools/check_central_brain_android_aidl_contract.sh`. Diagnostic records are structured, read-only and cursor-paged with a maximum page size of 100.

## Stage 2 P1-W01 Session Contract

`central-brain-sdk` now contains `SessionRequest`, `SessionHandle`, `SessionSnapshot`, `SessionQuery`,
`SessionPage` and independent `ICentralBrainSessionRuntime` V1. `SessionContract` rejects unknown schema/enums,
non-canonical IDs, oversized strings/pages and invalid deadline/timestamp bounds. The Session source list is frozen by
`central-brain-sdk/aidl-api/session-v1.sha256`; the protocol hash is reproduced by
`tools/check_central_brain_android_session_contract.sh` while the original task/diagnostic and Governance V1
checksums remain unchanged.

JVM tests cover validation and rejection. `SessionParcelInstrumentation` verifies all five DTO round trips on real
Android. P1-W01 itself was contract-only; P1-W05 later published the app-layer Binder/facade and P1-W06 wired its
owner state into Room v4. Vehicle/NPU access remains disabled.

## Stage 2 P1-W02 Plan/Node Contract

`central-brain-sdk` now also contains the bounded `ScenarioPlan`, `PlanNode`, `NodeDependency` and `NodePolicy`
structured parcelables. `PlanContract` accepts only the 11 executor types frozen in the detailed design and rejects
unknown versions/types/policy enums, duplicate IDs/edges/idempotency keys, missing dependencies, graph or
compensation cycles and limits violations. A plan is capped at 64 nodes, 256 edges, depth 16, width 8 and a
15-minute deadline; a node is capped at 120 seconds and three attempts. Retryable and side-effect nodes require an
idempotency key.

`central-brain-sdk/aidl-api/plan-v1.sha256` and
`tools/check_central_brain_android_plan_contract.sh` freeze the four AIDL sources independently while rechecking
the task/diagnostic, Governance and Session checksums. JVM tests and the cumulative
`SessionParcelInstrumentation` passed on an Android 13/API 33 ARM64 physical controller; the temporary test APK
was removed. This is wire/validation evidence only: `plan_contract_v1_defined=true`,
`plan_parcel_physical_android13_arm64_verified=true`, `plan_runtime_published=false` and
`hardware_accessed=false`. P2-W07 still owns the real Plan Compiler/Graph Validator and P3 owns execution.

## Stage 2 P1-W03 Event Contract

`central-brain-sdk` now contains `RuntimeEvent`, `ActionEvent`, `ObservationEvent`, `MessageEvent` and
`EventPage`, plus independent contract-only `ICentralBrainSessionEvents` and one-way
`ICentralBrainSessionEventCallback` V1. The existing Session V1 file and transaction order are unchanged. The Event
surface declares bounded `getEvents`, register and unregister methods; P1-W05 still owns a Service implementation,
Binder identity/capability enforcement and callback lifecycle.

`EventContract` validates 23 event types, canonical event/session/parent IDs, contiguous session sequence,
parent sequence ordering, typed payload matching, owner-page size <=100, explicit message redaction, opaque cursor
continuity and immutable replay identity. Callback delivery is notification only; `EventPage` replay is the future
authoritative recovery path. `events-v1.sha256` and
`tools/check_central_brain_android_event_contract.sh` freeze the seven AIDL files and recheck all earlier V1
checksums.

JVM tests and cumulative instrumentation passed on an Android 13/API 33 ARM64 physical controller; the temporary
test APK was removed. Status is `event_contract_v1_defined=true`,
`event_parcel_physical_android13_arm64_verified=true`, `event_runtime_service_published=false`,
`event_callback_service_published=false` and `hardware_accessed=false` for the P1-W03 increment. P1-W05 later
published the app-layer Event/callback Binder and P1-W06 made its authoritative replay rows durable; this is still
not a production Event broker.

## Stage 2 P1-W04 Effect/Approval Contract

`central-brain-sdk` now contains `EffectIntent`, `EffectObservation`, `ApprovalPrompt` and `UndoHandle` under
`com.centralbrain.sdk.effect`. No Binder interface is added in P1-W04. `EffectIntent` carries one bounded typed
scalar, immutable operation IDs/digests, idempotency, Context version, risk, verification, reversibility and a
bounded deadline without JSON/Bundle/FD material.

`EffectContract` distinguishes `DISPATCHED`, `DELIVERED`, `APPLIED` and `VERIFIED`, validates the complete
fail-closed transition/retry table, requires reported evidence before applied/verified, and enforces explicit
simulation source markers. `ApprovalPrompt` binds plan/action/target/Context/policy and expires within five
minutes; resume rejects stale bindings. `UndoHandle` has bounded TTL and only authorizes a future request to enter
Governance again; it does not roll back state or bypass current Safety/Context checks.

The four AIDL files have identity `709828114422595f1889dad58e8e60daf4d5e4f98c962a6145f2f8a39b0c178d`,
are frozen by `aidl-api/effect-v1.sha256` and checked by
`tools/check_central_brain_android_effect_contract.sh`. JVM and cumulative Android 13/API 33 ARM64 Parcel tests
pass. Status is `effect_contract_v1_defined=true`,
`effect_parcel_physical_android13_arm64_verified=true`, `effect_runtime_service_published=false`,
`approval_response_service_published=false`, `undo_service_published=false` and `hardware_accessed=false`.
P1-W05 now owns Session/Event SDK facade/lifecycle; P1-W06 owns Room v4. Existing Governance V1 still has no grant method.

## Stage 2 P1-W05 SDK Facade v2

`ScenarioClient`, `SessionClient` and `RuntimeEventListener` expose Session/Event use without public Binder
primitives. `AndroidScenarioTransport` binds the same explicit Runtime component with
`com.centralbrain.runtime.action.SESSION_RUNTIME` and `...SESSION_EVENTS`, negotiates both frozen V1
version/hash identities, treats either Binder death as a generation failure, and rebuilds callback bridges after
explicit reconnect.

`CentralBrainRuntimeService` returns the Session/Event Stub by action while retaining the legacy no-action task
Binder. `TransientSessionEndpoint` applies seven operation capabilities before deriving an owner fingerprint;
`TransientSessionRegistry` is owner-scoped, bounded and idempotent and does not retain raw utterances. It belongs
to the Runtime process, so Service instance rebind survives but Runtime process death does not. The production
policy grants Demo/Client2; the same-signer instrumentation principal exists only in `src/debug` policy overlay.

Fake-transport and registry JVM tests cover mismatch/race/reconnect/close/owner/capacity/cursor. Android 13/API 33
ARM64 physical instrumentation verifies real Binder open/replay/reconnect/resubscribe/cancel with duplicate replay
suppression. Status: `sdk_facade_v2_available=true`, `session_runtime_service_published=true`,
`event_runtime_service_published=true`, `event_callback_service_published=true`,
`session_runtime_persistence_wired=true`, `session_runtime_process_death_rehydration=true`,
`scenario_execution_enabled=false`, `hardware_accessed=false`.

## Stage 2 P1-W06 Room v4

Room v4 replaces the unused `runtime_session` skeleton with owner-scoped `sessions` and adds `plans`,
`plan_nodes`, `runtime_events`, `effect_observations` and `compensations`. Existing durable task/checkpoint/effect/
outbox/approval/audit/event-cursor rows are preserved. `MIGRATION_3_4` copies legacy session identity, then creates
foreign keys and unique owner/request, session/revision, node-idempotency and event-sequence indices without using
destructive migration.

`DurableSessionRegistry` is injected into the existing Session/Event Binder endpoint. Session admission plus the
initial event and terminal cancellation plus its event are Room transactions; the database stores only the
request digest, typed metadata and bounded canonical event payload, never the raw utterance or Binder objects.
Callback registrations remain process-local and are reconstructed by SDK snapshot/cursor replay after process
death.

The debug migration fixture verifies v1->v2->v3->v4 data preservation, 13-table shape, WAL, foreign keys, owner
query index selection and rollback of an interrupted session/event transaction. Android 13/API 33 ARM64 evidence
seeds a session, kills the Runtime process through the DUMP-protected debug receiver, rebinds, recovers the same
session and event history, then verifies terminal cancellation idempotency. Run:

```bash
bash tools/test_central_brain_android_session_durability.sh --require-api-33
```

Current boundaries: `room_schema_version=4`, `session_runtime_persistence_wired=true`,
`session_runtime_process_death_rehydration=true`, `scenario_execution_enabled=false`,
`effect_runtime_service_published=false`, `hardware_accessed=false`.

## Stage 2 P1-W07 Runtime Contract v2 aggregate

`central-brain/contracts/central_brain_runtime_contract_v2.json` is the machine-readable aggregate identity for
the completed P1 capability set. Aggregate version 2 composes the frozen Session/Plan/Event/Effect V1 sources,
SDK facade and Room v4; it is not a replacement AIDL version and changes no V1 transaction or hash.

`RuntimeContractV2` exposes compatible SDK constants and the five stable facade lifecycle errors. DTO validation
continues to fail with domain-prefixed `IllegalArgumentException`; authorization remains `SecurityException`, and
Binder details remain internal to the package-private transport. Bounds are fixed at 50 Session rows, 100 Event
rows, 256 cursor characters, 64 replay pages, 4 callbacks/session, 128 callbacks total and 8192 UTF-8 bytes for a
durable canonical Event payload. Binder latency targets are contract limits, not target-hardware performance
qualification.

The aggregate review records that Event V1 terminal pages cannot advance an opaque resume cursor. A separately
versioned Event V2 must add a terminal resume cursor and monotonic owner/session-scoped ACK; P1-W07 does not publish
that interface or a production broker. Run `bash tools/check_central_brain_runtime_contract_v2.sh`.

Status: `runtime_contract_v2_defined=true`, `runtime_contract_v2_verified=true`,
`runtime_contract_v2_physical_android13_arm64_verified=true`,
`frozen_v1_hashes_unchanged=true`, `event_v2_cursor_ack_required=true`,
`event_v2_interface_published=false`, `scenario_execution_enabled=false`, `hardware_accessed=false`.

## Stage 2 P2-W01 Canonical Vehicle Signal Types

`runtime-service/.../vehicle/schema` defines the first fixed canonical vehicle-signal contract. The 12-entry
`VehicleSignalPath` allowlist binds each VSS-style path to one scalar type, exact unit, allowed cabin area and
maximum age. `SignalValue` uses explicit boolean, integer, finite decimal and bounded text factories; arbitrary
objects, JSON and Android parcel payloads are excluded. `SignalTimestamp` preserves source wall time but computes
freshness from receive-side elapsed realtime.

`SignalQuality` separates value-bearing `VALID/STALE` from no-value `UNAVAILABLE/ERROR/CONFLICT` states.
`SignalSource` is provenance only: AAOS or VENDOR does not authorize or activate an adapter. Run
`bash tools/check_central_brain_android_vehicle_signal_schema.sh`; the cumulative installer also runs a
DUMP-protected API 33 ARM64 debug probe.

Status: `vehicle_signal_schema_defined=true`, `vehicle_signal_path_allowlist_count=12`,
`vehicle_signal_schema_android13_arm64_verified=true`, `vehicle_signal_provider_wired=false`,
`vehicle_property_mapping_configured=false`, `hardware_accessed=false`. P2-W02 owns capability ranges and
authorization; P2-W03/P2-W04 own Digital Twin and Context snapshots.

## Stage 2 P2-W02 Vehicle Capability Catalog

`runtime-service/.../vehicle/capability` defines eight immutable HVAC, seat, media and navigation capabilities.
Each item fixes semantic readable/writable/simulatable flags, explicit production availability/authorization,
typed target range, areas, risk class, optional canonical readback path and required fresh signals. Numeric targets
must be finite, bounded and step-aligned; text targets are bounded and optionally allowlisted.

The Stage 2 defaults are software/debug contracts: production availability and authorization are false for every
item. Seat recline is HIGH risk and requires fresh speed, gear, parking brake, occupancy and belt signals. Media
and navigation do not fabricate vehicle-signal readback paths. Run
`bash tools/check_central_brain_android_vehicle_capability_catalog.sh`; the cumulative installer runs the matching
DUMP-protected API 33 ARM64 debug probe.

Status: `vehicle_capability_catalog_defined=true`, `vehicle_capability_count=8`,
`vehicle_capability_catalog_android13_arm64_verified=true`,
`vehicle_production_capability_authorized_count=0`,
`vehicle_capability_adapter_registry_wired=false`, `vehicle_property_mapping_configured=false`,
`hardware_accessed=false`. P2-W03 owns the Digital Twin store; P8 owns evidence-backed target mapping.

## Stage 2 P2-W03 Vehicle Digital Twin Store

`runtime-service/.../vehicle/twin` defines a thread-safe in-process desired/reported state boundary.
`VehicleDigitalTwinStore` assigns one global monotonic revision, rejects stale or conflicting reported updates,
supports desired compare-and-set, and captures immutable path-filtered snapshots under the same store lock.
`DesiredStateRecord` uses explicit typed scalar factories and a maximum 15-minute TTL; `ReportedStateRecord`
derives snapshot-time effective quality from receive-side elapsed realtime.

`DigitalTwinSnapshot` does not conflate requested state with observed state. Its reconciliation result is one of
no desired, desired expired, pending reported, stale, unavailable, matched or mismatch. Run
`bash tools/check_central_brain_android_vehicle_digital_twin.sh`; the cumulative installer runs the matching
DUMP-protected API 33 ARM64 debug probe.

Status: `vehicle_digital_twin_store_defined=true`, `vehicle_digital_twin_android13_arm64_verified=true`,
`vehicle_digital_twin_persistence_wired=false`, `vehicle_digital_twin_adapter_wired=false`,
`vehicle_property_mapping_configured=false`, `hardware_accessed=false`. P2-W04 owns trusted Context snapshots;
P8 owns evidence-backed production adapter activation.

## Stage 2 P2-W04 Trusted Context Snapshot

`runtime-service/.../context` builds one immutable Context from a single `DigitalTwinSnapshot` revision plus a
Runtime-owned `SafetyVehicleStateSnapshot`, seat zone and profile-memory availability bit. `ContextFieldPolicy`
provides fixed general, seat-comfort and seat-recline profiles. The general safety set requires fresh speed, gear
and parking brake; seat recline additionally requires occupancy, belt and reported angle in the selected seat area.

`ContextSnapshotBuilder` derives parked/moving/unknown conservatively and reports Runtime-motion disagreement.
It records missing required, stale, conflict and non-production-trusted fields separately. Missing/invalid required
data, stale Runtime state, unknown/degraded/emergency Safety state or motion conflict sets `restricted=true`.
A complete moving Context is not automatically restricted; action-specific policy must still deny driver-seat
recline while moving. The SHA-256 digest binds policy, Twin/Runtime revisions, all typed fields and memory mode.

Status: `context_snapshot_defined=true`, `context_snapshot_android13_arm64_verified=true`,
`context_snapshot_production_trusted=false`, `context_snapshot_production_wired=false`,
`vehicle_signal_provider_wired=false`, `hardware_accessed=false`. SIMULATED is visible and usable only for the
debug/test workflow; AAOS/VENDOR provenance remains unverified until P8 activation evidence exists.

## Stage 2 P2-W05 Scenario Manifest

`runtime-service/.../scenario` adds immutable `ScenarioManifest`, strict bounded `ScenarioManifestParser` and
invalid-asset-isolating `ScenarioCatalog`. Gson 2.11 strict streaming parsing rejects duplicate/unknown fields,
nulls, trailing content, oversized input and unsupported typed values. Manifest validation reuses the frozen Plan
node allowlist and limits nodes, dependencies, depth, parallelism, retry/timeout, capability references, risk,
approval metadata, fallback and UI resource keys.

APK assets contain exactly three build-owned v1 templates: `scene.comfort.cold.v1`,
`scene.fatigue.assist.v1` and `scene.rest.nap.v1`. Fatigue/rest seat-recline nodes are explicitly
`PARKED_ONLY` and approval-required. A strict draft-2020-12 JSON schema and SHA-256 sidecar are checked in CI and
again from packaged assets by a DUMP-protected Android 13 ARM64 probe. Invalid or duplicate-ID assets are disabled
without disabling unrelated valid scenarios.

These are non-executable templates. P2-W05 performs no Resolver/Compiler/Graph/Effect action and does not wire the
catalog into a production Service. The checksum is build integrity, not artifact cryptographic signature evidence.
Status: `scenario_manifest_schema_version=1`, `scenario_catalog_count=3`,
`scenario_manifest_artifact_crypto_verified=false`, `scenario_catalog_production_trusted=false`,
`scenario_runtime_wired=false`, `scenario_graph_execution_enabled=false`, `hardware_accessed=false`.

## Stage 2 P2-W06 Deterministic Scenario Resolver

`runtime-service` now contains `ScenarioResolver`, `DeterministicScenarioResolver` and `ScenarioResolution`.
An admitted internal request selects only a manifest already present in `ScenarioCatalog`: explicit scenario ID has
priority, otherwise a <=256-character normalized text value is matched against a fixed Chinese/English alias table.
Unknown and multi-segment ambiguous intents fail closed; no model or dynamic capability creation is involved.

Resolution binds request, Context and capability snapshot digests. It validates source, zone, fixed Context policy,
required fresh Context paths, runtime-owned capability availability and parked-only node policy. Missing required
inputs reject; unavailable optional inputs degrade. A moving fatigue request therefore omits the optional recline
branch, while moving rest rejects because recline is required. Production profile rejects the current non-trusted
Context/capability foundation.

The result is immutable `ACCEPTED`, `DEGRADED` or `REJECTED` metadata with stable reason codes. It is explicitly
non-executable. The resolver is not wired to the production Service and does not compile a plan, call a model,
dispatch an Effect or access a vehicle/NPU/Driver-HAL interface.

Status: `scenario_resolver_defined=true`, `scenario_resolution_schema_version=1`,
`scenario_resolver_model_invoked=false`, `scenario_resolver_runtime_wired=false`,
`scenario_compiler_wired=false`, `scenario_graph_execution_enabled=false`, `effect_dispatch_enabled=false` and
`hardware_accessed=false`.

## Stage 2 P2-W07 Scenario Plan Compiler

`runtime-service` now contains `ScenarioPlanCompiler`, `PlanGraphValidator` and `PlanDigest`. The compiler accepts
only an `ACCEPTED` or `DEGRADED` Resolution together with the exact Context and capability snapshots used by the
resolver. It recalculates the Resolution digest and rejects Context/capability/manifest version or digest drift,
required gate failures and fallback branches not explicitly listed by the build-owned manifest.

The output owner is immutable and exposes the frozen P1-W02 `ScenarioPlan` transport only as a deep copy. Plan and
node-input digests bind Resolution, manifest, Context, capabilities, IDs, deadline, policies, nodes, edges and the
excluded optional branch. Required Effects need a reachable same-capability verify node; HIGH Effects need an
approval predecessor. Moving or unknown graphs exclude all `PARKED_ONLY` nodes and driver recline dispatch. Moving
fatigue therefore compiles without the optional approval/recline/verify branch.

Manifest templates still carry no target scalar, so this compiler cannot invent temperature, fan, seat-angle,
media or navigation material. It is not wired to `CentralBrainRuntimeService` or Room, and it does not publish or
execute a Plan, schedule a Graph, dispatch an Effect or access vehicle/NPU/Driver-HAL interfaces.

Status: `scenario_plan_compiler_defined=true`, `scenario_plan_schema_version=1`,
`scenario_plan_compiler_runtime_wired=false`, `scenario_plan_runtime_published=false`,
`scenario_graph_execution_enabled=false`, `effect_dispatch_enabled=false` and `hardware_accessed=false`.

`CentralBrainClient` binds the explicit `com.centralbrain.runtime/.CentralBrainRuntimeService` component. The SDK AAR contributes a narrow package-visibility query for `com.centralbrain.runtime`; it does not use `QUERY_ALL_PACKAGES`. Callbacks are dispatched through the executor supplied by the app, and service death fails active callbacks with `ERROR_SERVICE_DIED`.

The R2 deterministic runtime returns a typed handle before work, emits ACCEPTED/RUNNING/COMPLETED, and supports asynchronous idempotent cancellation. R2C adds Binder-instance-scoped death handling, explicit reconnect, terminal callback uniqueness and API 33 service/client death plus cancel-completion race instrumentation. The typed Protocol Binding is `android_integrated`.

## R3A Job Supervisor

`runtime-service` now routes task lifecycle through a pure-Java `JobSupervisor`. Legal transitions are explicit, progress is monotonic, active tasks are never evicted, total records are bounded to 128, and terminal records are retained for five minutes before deterministic expiry/pressure eviction.

Every production Binder entry resolves its caller from `Binder.getCallingUid()`, Android user serial, PackageManager UID packages and each package's current signing-certificate SHA-256. The request cannot claim identity or permissions. A task is bound to the complete snapshot; another principal receives unknown status and cannot cancel it. Unresolved identity is denied.

R3A does not by itself close R3. R3B adds package+signer capability policy and a second-client API 33 default-deny test; R3C must still add action risk classes and high-risk approval. R4 owns durable SQLite recovery, so the R3A registry must not be described as durable.

## R3B Capability Policy

`runtime-service/src/main/res/xml/central_brain_capability_policy.xml` is a strict V1 default-deny policy. The baseline grants Demo four production capabilities and the Runtime package one diagnostic-read capability only when each literal package is paired with the Runtime APK's complete current signer set. Because `runtime-current` is resolved after APK signing, no local debug certificate digest is embedded in source. Wildcards, unknown fields, duplicate rules and non-deny defaults fail startup closed.

Every production V1 method enforces one of `runtime.protocol.read`, `runtime.task.submit`, `runtime.task.status.own`, or `runtime.task.cancel.own`; diagnostic version/hash/page enforce `runtime.diagnostics.read`. The test-only `policy-probe` shares the debug signer and receives both signature permissions but has no package rule; both Binder surfaces deny its API 33 calls inside Runtime. Run this evidence with `tools/test_central_brain_android_capability_policy.sh --require-api-33`.

The policy probe is not assembled by `tools/build_central_brain_android_runtime.sh`, has only a debug variant, is marked `android:testOnly=true`, and is not a product artifact. R3C now supplies the separate Governance boundary; R4 remains responsible for durable approval recovery.

## R3C1 Action Governance Core

`ActionGovernancePolicy` derives risk only from a Runtime-owned stable catalog. Its five classes are read-only, comfort control, driver distraction, diagnostic write and OTA; the caller cannot submit a risk label. Unknown actions, unavailable state and unsafe vehicle conditions fail closed. Read and comfort decisions are policy-only and always report `isDispatchAllowed=false`; the three high-risk classes require approval when parked and are denied while moving.

`SafetyVehicleStateProvider` is the input boundary for Safety/Vehicle State. The current `RuntimeOwnedSafetyVehicleStateProvider` is a caller-independent, hardware-free test fixture with `RUNTIME_OWNED_STUB`, `hardwareBacked=false` and `productionTrusted=false`. It proves that Binder payloads do not control policy state, but it is not target-vehicle evidence and does not close `DRV-GAP-002` or `DRV-GAP-005`.

`InMemoryApprovalRegistry` is bounded and owner-isolated. It stores only high-risk `PENDING` requests, never pressure-evicts pending records, expires/cancels deterministically, and explicitly reports `supportsApprovalGrant=false` and `isDurable=false`. R3C2 publishes this boundary through a separate typed Governance Binder; R4 will replace the process-local registry with durable approval/checkpoint/outbox storage.

## R3C2 Typed Governance Binder

`central-brain-sdk` now includes `ICentralBrainGovernance`, four structured parcelables and `CentralBrainGovernanceClient`. The Governance V1 source is frozen by `central-brain-sdk/aidl-api/governance-v1.sha256`; the original task/diagnostic `v1.sha256` remains unchanged. `ActionRequest` contains only client request ID, exact Action ID and idempotency key. It has no risk, Safety/Vehicle State, caller, package, signer or permission assertion.

`CentralBrainGovernanceService` performs protocol read, action evaluation, approval request, owner status and owner cancel behind separate capabilities and the `BIND_GOVERNANCE` signature permission. The API deliberately has no approve/grant method. Demo HMI verifies read/comfort policy-only decisions, OTA approval-required, pending status and idempotent cancel. The same-signer unconfigured `policy-probe` receives the outer permission but every Governance method is denied by the inner default-deny policy.

API 33 evidence is part of `tools/install_central_brain_android_runtime.sh --require-api-33` and `tools/test_central_brain_android_capability_policy.sh --require-api-33`. R3 completed at `R3_TRUSTED_GOVERNANCE` / `android_integrated`; the current stage is now R4 durable workflow. Neither stage implies production Safety/Vehicle data, approval grant authority, real action dispatch or target hardware qualification.

## R4A Room Durable Schema

`runtime-service` now compiles AndroidX Room `2.8.4` and exports `CentralBrainDatabase` schema version 2. The app-private WAL database owns eight tables: session, task, checkpoint, pending effect, effect outbox, approval, audit event and event cursor. Unique idempotency/owner indexes and task/effect foreign keys are part of the exported schema. Payload/checkpoint/outbox/audit content is represented by digests; R4A does not persist raw utterances, model output or vehicle frames.

`MIGRATION_1_2` upgrades the prior minimal task/approval shape without destructive fallback. A DUMP-protected debug-only migration probe creates an isolated v1 database, inserts legacy rows, opens it through Room, and verifies schema version 2, eight tables, WAL plus preserved task/approval data. The probe never opens or deletes the production database and is absent from release.

R4A defines and validates storage ownership only. Production Runtime/Governance Services do not open the database yet, `durable_dispatch_enabled=false`, and restart recovery is not claimed. R4B1 now adds transactional task admission below; later R4B increments wire task lifecycle and approval writes. R4C will add restart recovery and pending-effect/outbox processing while keeping real hardware dispatch disabled.

## R4B1 Durable Task Admission

`DurablePrincipalFingerprint` hashes Android user serial plus canonical package/current-signer pairs with a domain-separated, length-framed SHA-256 encoding. It deliberately excludes the ephemeral UID so ownership survives package UID reassignment, but live Binder identity and capability authorization are still required before computing it. Only the fingerprint is persisted; signer evidence is not copied into Room.

`DurableTaskRepository.admit` performs owner+idempotency lookup, task creation and `TASK_ACCEPTED` audit insertion in one Room transaction. An exact replay returns the original task without a second row or acceptance audit. Reusing the same owner/key for different session, client request or payload digest raises an explicit conflict; another owner may use the same key independently. The repository stores structured metadata and a payload digest, never the raw utterance.

The DUMP-protected debug repository probe closes and reopens its isolated database before replay, then verifies two owners produce exactly two tasks and two acceptance audits. R4B1 intentionally reports `runtime_repository_wired=false`: production Services still use the R3 in-memory path until R4B2, and no pending effect, outbox dispatch or hardware access is enabled.

## R4B2 Durable Runtime Wiring

`CentralBrainRuntimeService` now opens the app-private Room database and completes durable admission before returning a task handle. The initial ACCEPTED checkpoint is sequence 1. Each RUNNING/COMPLETED/FAILED/CANCELLED transition updates the task, inserts the next checkpoint and inserts a transition audit in one transaction; the in-memory `JobSupervisor` advances only after that transaction succeeds. Terminal callback attempts are followed by a separate idempotent settlement transaction and audit.

The Binder request is hashed with a domain-separated, length-framed digest before persistence; raw utterance and reply text remain memory-only. Exact replay is owner-scoped and payload-checked. A short admission lock covers durable admission through live-map publication, so concurrent same-key Binder calls cannot be misclassified as unrecovered work. A live same-process replay returns the same handle and can attach at most four observer callbacks; Demo verifies sequential and concurrent replay callbacks reach the same completion. An exact existing replay remains valid after its original deadline, while a new expired request is rejected without inserting a task.

Status can fall back to owner-isolated durable metadata after an in-memory record is gone. Automatic execution recovery is deliberately not enabled: if the database contains a task that this process has not recovered, submit returns the existing handle and asynchronously reports retryable `ERROR_INTERNAL` with `task_recovery_enabled=false`, rather than executing it twice. R4C owns recovery and fault injection. Governance approval is still process-local, and effect/outbox dispatch remains disabled.

## R4B3 Durable Approval

`CentralBrainGovernanceService` now uses `DurableApprovalRepository` instead of the R3 process-local registry. Owner fingerprint + idempotency key lookup, pending creation and `APPROVAL_REQUESTED` audit are transactional. The same key/action returns the original approval across database reopen even if the current policy state would no longer permit creating a new request; a different action under the key is a conflict. This replay cannot authorize or dispatch anything because the AIDL still has no approve/grant method.

Owner status and cancel read Room directly. Cancel is idempotent and writes one cancellation audit. Pending rows use a two-minute wall-clock expiry; expiry is applied transactionally on repository access and writes one expiry audit. The global bound is 64 pending approvals. Terminal rows are intentionally not purged yet so idempotency is not silently lost; retention/key rotation and trusted-clock policy remain ISSUE-022 decisions.

`ApprovalStatus.durable` is now `true`, while `grantSupported` and `dispatchAllowed` remain `false`. Demo verifies duplicate request returns the same approval and production Room contains the cancelled record/audits. The debug probe closes and reopens an isolated database to verify replay, mismatched-action conflict, owner isolation, cancel idempotency and expiry. Resumable task execution and pending-effect/outbox processing remain later R4 work; R4C1 below only provides fail-closed restart reconciliation.

## R4C1 Fail-Closed Restart Reconciliation

Runtime startup now submits Room reconciliation to the single task executor and gates task submit, cancel and status on that Future. This keeps database transactions off the Android main thread and prevents a new admission from racing ahead of restart cleanup. ACCEPTED/RUNNING tasks and COMPLETED tasks whose terminal callback was not durably settled become FAILED in one transaction with a next checkpoint and `TASK_RESTART_RECONCILED` audit. A second reconciliation pass changes nothing.

The database intentionally has no raw utterance or result payload, so this increment does not resume execution. An exact replay after process death returns the original handle, sends the durable FAILED update and retryable internal failure, then attempts terminal-delivery settlement. `CentralBrainClient` serializes each task callback over the caller's executor so update-before-terminal order is preserved even for a thread pool.

The isolated debug probe and API 33 Binder lifecycle instrumentation verify both reconciliation classes, idempotency, same-handle FAILED replay, terminal uniqueness and cancel/completion races. `task_execution_resume_enabled=false` and `durable_dispatch_enabled=false`; pending-effect/outbox processing remains R4C2, with no hardware, Driver/HAL, vendor-system or virtualization change.

## R4C2A Effect Prepare And Claim

`DurableEffectRepository` now owns repository-only prepare and claim semantics. A new operation requires an owner-matched RUNNING task. The repository hashes owner fingerprint plus caller idempotency key into the globally indexed token, then atomically inserts one PREPARED effect, one PENDING outbox row and one audit. Exact replay across database reopen returns the same rows; changed content conflicts and another owner can reuse the caller key.

Claim selects one due PENDING/PREPARED/RUNNING tuple, atomically changes both rows to IN_FLIGHT, increments attempt and audits the claim. An isolated reopen can requeue interrupted IN_FLIGHT rows without decrementing attempt; setting `not_before` to reconciliation time lets older pending work run first. The API 33 probe verifies that fairness and then reclaims the interrupted effect at attempt 2.

This repository stores only operation and envelope digests. Runtime and Governance do not reference it, and `outbox_dispatch_enabled=false`: no UIB, SOA, Skill or hardware call occurs.

## R4C2B Effect Retry And Terminal States

The repository now closes its local state machine without enabling delivery. A claimed effect may be atomically marked APPLIED/DELIVERED, scheduled back to PREPARED/PENDING with a bounded delay, or marked FAILED/DEAD_LETTER. A pending effect may be cancelled. Every mutation checks owner, effect/outbox identity and expected attempt, appends one audit record, supports an exact replay, and rejects changed result/failure/reason digests or retry delay.

Claims are limited to three attempts by default. The claim query excludes exhausted rows, and retry is rejected on the final attempt. If the process dies while that final claim is IN_FLIGHT, restart reconciliation fails the pair closed to FAILED/DEAD_LETTER with `EFFECT_CLAIM_EXHAUSTED`; this is idempotent and does not claim that an external side effect did or did not occur.

The API 33 isolated probe verifies delay gating, stale-attempt rejection, success/dead-letter/cancel replay, terminal state counts, final-attempt crash handling and reconciliation idempotency. Runtime and Governance still do not reference `DurableEffectRepository`; `effect_repository_wired=false` and `outbox_dispatch_enabled=false`. R4C3 must define an adapter idempotency/status contract and fault matrix before any production dispatcher is added.

## R4C3A Effect Adapter Contract And Fault Matrix

`EffectAdapter` now defines a strict internal destination boundary: apply must deduplicate the persisted token and return the original result on duplicate calls, while token status must be linearizable and APPLIED status must return the original result evidence. An invocation contains bounded transient canonical payload/envelope bytes and must match both durable SHA-256 digests. Byte arrays are defensively copied and are never added to Room by this increment.

`EffectStatusReconciler` only queries status; it never calls adapter apply. APPLIED commits local success, authoritative NOT_APPLIED schedules a retry while attempts remain, and REJECTED/UNKNOWN fail closed. A status transport failure leaves the pair IN_FLIGHT for later reconciliation, and final-attempt NOT_APPLIED becomes a dead letter.

The debug-only deterministic adapter and API 33 isolated probe verify duplicate apply, crash before apply, crash after apply but before the local commit, unavailable/unknown status, final-attempt handling and exact replay. The fixture is process-memory simulation, so `transient_effect_material_durable=false`, `effect_adapter_production_wired=false`, and `real_adapter_dispatch_enabled=false`. R4C3B must define the trusted durable material source and activation gate before production wiring can be considered.

## R4C3B Effect Material Source And Activation Gate

`EffectMaterialSource` defines canonical material ownership outside the digest-only Room schema. Production eligibility requires an available source, explicit production assurance, process-restart durability, encryption at rest, effect-bound integrity, bounded retention and deletion support. `EffectDeliveryActivationGate` combines those requirements with the R4C3A adapter contract and returns stable blockers without invoking apply or status.

The current main-source implementation is deliberately `EmptyEffectMaterialSource`; it resolves nothing and fails every production activation check. A debug-only synthetic source verifies the positive contract, Room reopen resolution, defensive copies and digest mismatch/missing-material rejection, but remains process memory and is not delivery evidence.

API 33 reports `production_effect_delivery_activation_allowed=false`, `production_effect_material_source=empty`, `production_effect_material_durable=false` and `raw_effect_material_persisted=false`. R4C3C must wire this fail-closed result into the production Runtime boundary without enabling a dispatcher; target material storage still requires platform-owned key, retention and deletion evidence.

## R4C3C Production Fail-Closed Activation Visibility

Runtime and Diagnostic Services now share one immutable `EffectDeliveryActivationSnapshot`. It evaluates the current `adapter=null + EmptyEffectMaterialSource` configuration once and fails class initialization if that impossible configuration ever reports allowed. Runtime startup logs and `dumpsys activity service` expose only bounded blocker/status fields; the existing read-only diagnostic page adds one `effect-delivery-activation` record.

No production path resolves material, queries status, calls apply or opens `DurableEffectRepository` for dispatch. API 33 verifies the diagnostic record, Runtime log and dumpsys all agree on `activation_allowed=false`, adapter/material/apply/status disabled, and the full blocker set. Release still contains only the three signature-protected Services and no probes.

This closes the R4 durable-workflow foundation at `R4_DURABLE_WORKFLOW` / `android_integrated`. It does not activate effect delivery: target adapter/material/key/trusted-clock evidence remains an ISSUE-022/Driver-HAL gate and later integration work.

## R5A1 Model Provider Contract

`ModelProvider` defines the internal Model Runtime Adapter lifecycle boundary: descriptor, health/lifecycle snapshot, model warmup, asynchronous inference with bounded transient stream chunks, cancellation, metrics, fault reporting and close. Descriptor invariants reject production or hardware claims from deterministic-stub/Ollama-debug backends and reject any inference slot or fallback capability on an empty provider.

`ModelProviderProfiles` currently publishes two immutable, non-routable profiles. `deterministic.stub` is TEST_ONLY, hardware-free and COLD with one declared future concurrency slot; its implementation is not configured in R5A1. `vendor.npu.empty` is EMPTY/UNAVAILABLE, has zero slots and cannot warm up, infer, stream, cancel, collect device metrics or act as fallback. Neither profile is held by a production Service.

JVM tests and a DUMP-protected debug-only API 33 probe validate both profiles, unsafe descriptor rejection and defensive stream-chunk copies. R5A1 keeps `model_provider_runtime_wired=false`, `model_router_dispatch_enabled=false`, `ollama_android_provider_configured=false` and `hardware_accessed=false`. R5A2 owns scheduler admission/priority/deadline/quota/cancel semantics; R5B will add the first executable deterministic stub without enabling vendor NPU access.

## R5A2 Inference Resource Scheduler

`InferenceResourceScheduler` is a synchronized pure-Java admission state machine. A `TrustedSubmission` can only be constructed through the Runtime-policy factory; priority is not read from Binder payload. Queue and task deadlines use an injected elapsed-realtime clock. Dispatch order is effective priority, earliest queue deadline, admission FIFO and request ID.

Limits independently bound global/per-owner queued work, global/per-owner running work and maximum queue wait. A route adds its own concurrency slots. Exact active submission replay consumes no quota; changed duplicate IDs are rejected. Current R5A1 profiles convert to disabled routes, while enabled `test.*` routes exist only for contract evidence.

Queued cancellation removes the admission locally. Running cancellation or deadline expiry changes state to `CANCEL_REQUESTED` and returns a lease-bound directive; the scheduler never calls `ModelProvider.cancel` or `infer`. Any later provider terminal acknowledgement releases the slot, but a late completion is mapped to local CANCELLED or DEADLINE_EXCEEDED and its output is not accepted. Job Supervisor/durable workflow still owns final task state. Production Services remain unwired and all provider/hardware flags remain false.

## R5B1 Deterministic Stub Provider

`DeterministicStubModelProvider` is an executable implementation of the R5A1 contract, but its descriptor is permanently TEST_ONLY, non-hardware and non-production. It starts COLD, validates one allowlisted model artifact during warmup, then accepts one active request. Inference uses an injected executor and elapsed clock; streaming emits two ordered bounded chunks followed by a digest-only terminal result. Equal model/input digests produce identical output across provider instances.

Cancellation sets a flag and returns `PENDING_PROVIDER_ACK`; a later executor phase emits the terminal cancellation and releases the slot. Metrics track accepted/completed/cancelled/failed work, terminal history is bounded to 64, and test fault modes cover retryable-before-stream, terminal-after-first-chunk and fault isolation. Close terminates active test work and prevents reuse.

The implementation is instantiated only by JVM tests and a DUMP-protected debug probe. The current immutable profile still reports `implementationConfigured=false` and `routingEnabled=false`; Runtime, Governance, Scheduler and Model Router do not hold the provider. Ollama, Vendor NPU, network and hardware access remain disabled.

## R5B2 Test-Only Model Router

`TestOnlyModelRouter` is the first executable Scheduler-to-Provider coordinator. Its only factory is `createForContractTest`, and its only route is `test.deterministic.stub`. A trusted Runtime-policy request is admitted by `InferenceResourceScheduler`, claimed with a lease, converted to a digest-only provider request and dispatched to `DeterministicStubModelProvider`. Stream chunks are forwarded transiently; provider handle, request and lease identities are validated before terminal settlement.

Queued cancellation terminates locally. Running cancellation and deadline expiry consume the Scheduler's lease-bound directive, invoke provider cancellation once, then normalize the provider acknowledgement to the Scheduler-owned CANCELLED or DEADLINE_EXCEEDED state. Exact active replay retains the original observer, changed duplicate content is rejected, duplicate terminals are ignored, and the only fallback policy is `NO_FALLBACK`.

JVM tests and the DUMP-protected debug API 33 probe exercise sequential slot dispatch, stream forwarding, cancellation, deadlines, provider identity, replay, duplicate-terminal settlement and retryable failure. The router is not referenced by production Runtime/Governance, no production factory or Binder API exists, and the immutable profile remains unconfigured/non-routable. Production inference, Ollama, Vendor NPU, network and hardware access remain disabled.

## R5C1 Production-Safe Model Runtime Readiness

`ModelRuntimeReadinessSnapshot` is an immutable metadata-only view shared by Runtime startup logging, protected dumpsys and the existing bounded Diagnostic Binder page. It reads only `ModelProviderProfiles`; it does not construct a provider, Scheduler or Router and cannot warm, infer, cancel or dispatch.

The snapshot separates contract/test implementation availability from production activation. The deterministic profile reports TEST_ONLY, COLD/HEALTHY and `STUB_IMPLEMENTATION_NOT_WIRED`; the Vendor NPU profile reports EMPTY, UNAVAILABLE/UNAVAILABLE and `VENDOR_RUNTIME_UNAVAILABLE`. Ordered blockers explicitly report missing production provider/route, unwired Scheduler/Router and an empty Vendor NPU interface.

All three visibility surfaces report production inference false, profile configuration/routing false, production Router dispatch false, Ollama disabled and hardware untouched. R5C1 changes no AIDL, Room schema or artifact shape and does not promote the production evolution stage beyond the R4 durable foundation.

## R5D1 Android 13 Application-Layer Deployment Acceptance

`tools/test_central_brain_android_target_deployment.sh` validates the SDK AAR, Runtime APK and Demo APK against an API 33 device. The default run builds and invokes the complete installation gate, then records artifact hashes, signer identity, package versions/UIDs, `/data/app` placement, manifest SDK/service shape and fail-closed model readiness.

The gate rejects INTERNET/native payload and any SYSTEM/PRIVILEGED/PERSISTENT package requirement. It does not require vendor/AOSP/BSP source or modify system/vendor partitions. Emulator evidence and physical-device application evidence are labeled separately, while `target_hardware_validated=false` remains mandatory for both. See `docs/CENTRAL_BRAIN_ANDROID_TARGET_DEPLOYMENT_ACCEPTANCE.md`.

## R6A1 Bounded Event Runtime Contract

`BoundedEventRuntime` is a synchronized pure-Java contract fixture for three trusted low-frequency topics: task state, policy decision and model health. Publications are created through a Runtime-policy factory and carry schema/digest metadata only. The runtime assigns one global monotonic sequence and retains a bounded in-process replay window.

Owner-scoped subscriptions use client idempotency keys, a global cursor and bounded delivery queues. Exact replay returns the original subscription and observer; changed content conflicts. Retention or queue loss produces an explicit overflow callback before retained events. Observer failure does not remove the event or advance the delivered cursor. Observer callbacks cannot reenter a mutating runtime operation; an attempt fails closed as an observer failure without changing the queue. Owner cancellation is isolated and idempotent with bounded tombstones.

R6A1 is instantiated only by JVM tests and a DUMP-protected API 33 probe. It does not use the existing Room cursor table, expose Binder callbacks, start a broker, DDS/network transport or vehicle data plane, or wire production Services. R6A2 owns durable cursor/subscription recovery.

## R6A2A Durable Event Schema

Room schema v3 keeps the existing eight-table artifact shape while rebuilding `event_cursor` around owner plus client-subscription identity. It stores canonical trusted topics, requested and acknowledged global sequence, queue capacity, ACTIVE/RESYNC/CANCELLED state, overflow range/count and timestamps. It stores no event payload, model output or vehicle frame.

`MIGRATION_2_3` preserves each v2 owner/topic cursor as `legacy:<cursor_id>`, retaining its cursor ID, owner, topic, acknowledged sequence and update time. API 33 migration evidence validates the complete v1 -> v2 -> v3 path. R6A2A adds DAO shape only; production Services, the R6A1 process runtime and Binder APIs do not read or write these rows. R6A2B owns repository semantics and restart recovery.

## R6A2B Durable Event Repository

`DurableEventCursorRepository` owns Room transactions for owner/client registration, monotonic acknowledgement, conservative overflow, explicit resynchronization and owner-isolated cancellation. Canonical topic sets make request order irrelevant; exact replay returns the existing row, changed parameters conflict, and active plus cancelled records are bounded. Each applied transition writes one digest-only audit event in the same transaction.

An isolated API 33 probe verifies database reopen while RESYNC_REQUIRED, continuation after resync, source-sequence regression rejection, cancellation idempotency and cancelled-record trimming. The implementation requires an upstream sequence that never resets. R6A1 is process-local and resets after process death, so the repository is not wired to R6A1 or production Services; Binder callbacks, broker activation and production cursor persistence remain false.

## R6A3 Event Runtime Readiness

`EventRuntimeReadinessSnapshot` is an immutable production-safe view shared by Runtime startup logging, protected Runtime dumpsys and the bounded Diagnostic Binder page. It reports the historical Event cursor schema/repository foundation as available inside current Room v4 while activation, production broker wiring, durable publisher ACK, middleware and vehicle transport remain unavailable. The separate Stage 2 Session/Event callback Binder is app-layer and does not promote this production readiness snapshot.

The snapshot is fail closed with ordered blockers and does not open Room or construct an Event runtime/repository. R6A3 changes no AIDL and adds no dispatch path. It closes the Event software foundation visibility step, not production Event activation; durable publisher ownership and callback/broker integration remain ISSUE-025 work.

## R6B1 Bounded Memory Lifecycle

`BoundedMemoryLifecycle` is a synchronized pure-Java contract fixture for `EPHEMERAL`, `SESSION` and `PROFILE` Memory metadata. Every write is bound to a trusted owner, client idempotency key, purpose, schema, lowercase SHA-256 content reference and bounded TTL. PROFILE writes additionally require matching, unexpired Governance consent that covers the complete requested retention interval; only profile-eligible purposes are accepted.

Owner-scoped queries expose redacted lifecycle metadata and never return the content digest. SESSION/PROFILE export returns the digest reference only after matching Governance authorization; EPHEMERAL export is forbidden. Expiry and delete clear the exportable digest, keep only a domain-separated request fingerprint for bounded idempotency, and retain a bounded terminal history whose eviction ends replay guarantees for the evicted request.

R6B1 is process-local test/debug code. It uses injected elapsed time, does not survive process death, and is not wired to Room, AIDL, production Services, consent revocation, encryption keys or hardware. The Governance consent/export factories model trusted inputs but are not production authorities. Durable PROFILE storage and key/consent ownership must be resolved before any persistence increment.

## R6B2 Memory Runtime Readiness

`MemoryRuntimeReadinessSnapshot` is an immutable production-safe view shared by Runtime startup logging, protected Runtime dumpsys and the existing bounded Diagnostic Binder page. It validates only the R6B1 scope/TTL constants and reports implementation availability without constructing `BoundedMemoryLifecycle`, opening Room or creating a Memory write path.

Activation is fail closed behind eight ordered blockers: durable encrypted storage, key lifecycle, consent authority, consent revocation, trusted retention clock, repository implementation, Runtime wiring and middleware wiring. Schema/repository/production wiring remain false, raw content remains absent, and PROFILE storage remains non-durable. R6B2 changes no AIDL or database schema; it makes the prerequisite gap auditable before any persistence design is approved.

## R6C1 Signed Built-In Skill Runtime

`BoundedBuiltInSkillRuntime` defines three compiled-in manifests aligned with the existing prototype: `vehicle.state.query`, `cabin.precondition` and `cabin.scene.nap`. Each immutable manifest fixes version `0.1.0`, input/output schema IDs, semantic route kind/target, required capabilities, risk class, allowed safety states, artifact digest and signer digest evidence.

Manifest construction accepts only the compile-time signer allowlist. This proves deterministic catalog policy, not artifact cryptography: `cryptographic_artifact_verification_performed=false`, and no APK/JAR/native code is loaded. Trusted invocation admission is owner/client idempotent, schema/version/capability/safety-state gated and digest-only. Active/cancelled records are bounded and owner cancellation is idempotent, while every admitted snapshot keeps dispatch false.

R6C1 is test/debug process-local code and is not referenced by production Services. It adds no AIDL, Room schema, network, dynamic plugin loader or hardware path. A real signed Skill packaging/publishing pipeline and production dispatcher remain open prerequisites.

## R6C2 Fixed Governance Middleware Chain

`FixedGovernanceMiddlewareChain` fixes the contract order to identity, schema, privacy, policy, QoS, trace, dispatch gate, output guard and audit. Each stage emits immutable digest-only evidence. The first rejected decision stage stops all later decision stages, which are marked `SKIPPED`; the final audit stage is a mandatory terminal recorder and executes exactly once for both allowed and denied evaluations.

The dispatch gate validates compiled manifest, route-owner and route-policy metadata only. A passed gate means the contract may continue to output validation; it never invokes SOA, UIB, Agent, network or hardware, and `serviceDispatchTriggered` remains false. Output guarding checks schema, bounded size and required redaction without receiving or storing raw output.

R6C2 is process-local JVM/debug evidence and is not referenced by production Services. Its audit ring is bounded and non-durable. It adds no AIDL, Room schema, production middleware wiring, network or hardware path; R6C3 will expose these activation blockers through production-safe readiness diagnostics.

## R6C3 Skill And Governance Readiness

`SkillGovernanceReadinessSnapshot` is an immutable production-safe view shared by Runtime startup logging, protected Runtime dumpsys and the existing bounded Diagnostic Binder page at sequence 8. It validates only the three built-in Skill IDs and nine middleware stage constants; it never constructs `BoundedBuiltInSkillRuntime` or `FixedGovernanceMiddlewareChain` and never opens storage.

Visibility separates R6C1/R6C2 implementation availability from production activation. Ten ordered blockers cover artifact cryptographic verification, Skill lifecycle/revocation/rollback, sandbox, production Governance authorities, route-owner registry, middleware wiring, audit persistence and production Skill dispatch. Compile-time signer evidence remains visible but does not become real artifact verification.

R6C3 changes no AIDL, Room schema or artifact shape. Event and Memory readiness continue to report `MIDDLEWARE_CHAIN_NOT_WIRED`; the new snapshot proves a contract implementation exists, not that it is wired into production request execution.

## R7A1 Runtime Acceptance Snapshot

`RuntimeAcceptanceSnapshot` aggregates the immutable Effect, Model, Event, Memory and Skill/Governance readiness views with the typed Binder, trusted Governance and durable-workflow baseline. It is exposed through Runtime startup logging, protected dumpsys and Diagnostic Binder sequence 9 without opening storage or activating a blocked subsystem.

The initial R7A1 rollup separated `core_software_baseline_ready=true` from `r7_application_integration_complete=false`, `production_activation_allowed=false` and `target_hardware_validated=false`. Its nine ordered blockers covered Client2 Binder migration, API 33 end-to-end evidence, target system owner, five blocked production subsystem groups and target hardware validation; later R7B/R7C sections record the evidence-backed application transitions.

R7A1 is an acceptance contract, not a certificate embedded in the APK. The installer must still verify its three diagnostic surfaces and all underlying probes. R7B and R7C will remove only the application-integration blockers supported by concrete evidence; production and hardware blockers remain independent.

## R7B Client2 SDK/Binder Migration

The isolated Client2 reverse-demo project embeds the public SDK/AIDL contract and a narrow scenario bridge as `classes2.dex`. Its 12 scenario buttons now map their stable UI aliases to canonical scenario IDs, open typed Session/Event streams through `SessionClient`, and receive snapshot/event/replay callbacks; the previous Task API bridge, HTTP RequestTask, INTERNET permission and cleartext opt-in are absent.

The generated Client2 debug APK uses the same debug signer as Runtime to pass `BIND_RUNTIME`. Runtime then applies its package/current-signer capability policy and grants Client2 only protocol read plus owned Session open/read/cancel. The API 33 acceptance taps the real overlay button, verifies Runtime identity resolution, ordered event projection and replay completion, and records no HTTP, service dispatch or hardware access.

This evidence changes only `client2_binder_migration_complete=true`. Full R7 application integration, target system ownership, production subsystem activation and target hardware validation remain blocked. Original Client2/RenderService signing compatibility must be validated on the target device.

## R7C Android 13 Application Acceptance

R7C adds a repeatable API 33 recovery matrix over Client2 and Runtime. It verifies visible Runtime-unavailable failure and same-Activity retry, deterministic compatibility-stream replacement, Runtime process-death reconnect with sequence-deduplicated replay, fail-closed restart reconciliation, Client2 process restart/rebind and all existing Binder death/reconnect/cancel-race instrumentation.

The process-death receiver exists only in the Runtime debug source set and requires `android.permission.DUMP`; release packaging excludes it. Passing the matrix sets `r7_application_integration_complete=true` and `api33_end_to_end_acceptance_complete=true`. Seven production/system/hardware blockers remain, so this is not target-device or production qualification.

## R7D Android Software Handoff

R7D packages the public SDK AAR, Runtime debug APK, Demo debug APK and patched Client2 debug APK with an immutable delivery profile, per-file SHA-256 inventory, package/minSdk/signer facts, target-input template, migration documents and self-verification/install tools. Runtime, Demo and Client2 must remain one signer cohort because the Runtime service uses a signature permission.

`tools/package_central_brain_android_delivery.sh` builds the bundle and deterministic tar archive. The bundle installer is dry-run by default, requires API 33, checks every existing package signer before the first `adb install -r`, and requires explicit debug-signing consent for execution. It has no automatic uninstall, root, remount, fastboot or partition-write path.

The R7D result is `software_handoff_ready=true`, not production or physical-hardware qualification. Seven explicit empty integration slots preserve target system ownership, Effect delivery, vendor NPU Model Runtime, Event Runtime, encrypted Memory, Skill/Governance composition and target-hardware evidence as external blockers.

## B1-B2 Native Runtime

`native-runtime` is a C11/Java AAR with a versioned C ABI, registered JNI bridge and exactly two payloads: `arm64-v8a` and `x86_64`. `CentralBrainRuntimeApplication` owns one native handle for the Runtime APK process. Production Runtime and Diagnostic Services query its immutable snapshot but never acquire a native slot or dispatch work through it.

Build-time verification checks both the AAR and final Runtime APK for exact ABI payloads, ELF machine/export/hardening properties, signer validity, no vendor/hardware dynamic linkage and no INTERNET permission. API 33 integration is exercised with:

```bash
bash tools/test_central_brain_android_native_runtime.sh --require-api-33
```

The probe verifies load/init, bounded leases, capacity, busy close, duplicate release, drain/close, Runtime dumpsys, Diagnostic Binder sequence 10 and process recreation. All provider, dispatch and hardware fields remain false; this is not NPU/VHAL or target-controller evidence.

## B3 Black-Box Android 13 Preflight

Run the mutation-free device and existing-package check before installation:

```bash
bash tools/preflight_central_brain_android13_blackbox.sh \
  --serial <serial> --require-api-33 --report <path>
```

Then run controlled API 33 acceptance:

```bash
bash tools/test_central_brain_android_blackbox_acceptance.sh --serial <serial>
```

The first command verifies API/ABI, delivered and existing package signers, `/data/app` placement and read-only build/security observations without package mutation. The second runs the existing Binder/Room/HMI and native recovery gates plus a debug-only public-API Java probe for PackageManager signer parity, ordinary UID and app-private storage. Emulator results are never promoted to physical-controller or hardware evidence.

## B4 Hybrid Delivery

Build the five-artifact C/Java handoff with:

```bash
bash tools/package_central_brain_android_hybrid_delivery.sh
```

The generated bundle has a default Runtime+Demo maintenance profile and an explicit optional Client2 profile. Use `docs/CENTRAL_BRAIN_ANDROID13_HYBRID_INSTALLATION_AND_USAGE.md` for package verification, dry-run, install, UI, diagnostics, rollback and future vendor-adapter integration. Bundle readiness remains separate from production signing and physical target qualification.

## Toolchain

- Android Gradle Plugin: `8.10.1`
- Gradle wrapper: `8.11.1`
- Gradle distribution SHA-256: `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`
- JDK: `17`
- `compileSdk`: `36`
- `minSdk`: `33` (Android 13)
- Local build tools: `37.0.0`

AGP 8.10 supports API 36 and requires Gradle 8.11.1 and JDK 17 according to the official Android compatibility table: <https://developer.android.com/build/releases/agp-8-10-0-release-notes>.

## Build

From the repository root:

```bash
bash tools/build_central_brain_android_runtime.sh
```

Expected outputs:

- `central-brain/android-runtime/native-runtime/build/outputs/aar/native-runtime-debug.aar`
- `central-brain/android-runtime/central-brain-sdk/build/outputs/aar/central-brain-sdk-debug.aar`
- `central-brain/android-runtime/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk`
- `central-brain/android-runtime/demo-hmi/build/outputs/apk/debug/demo-hmi-debug.apk`

## Device Check

With exactly one online adb device:

```bash
bash tools/install_central_brain_android_runtime.sh
```

Use `--serial <serial>` when multiple devices are attached and `--skip-build` to reuse existing artifacts. `--require-api-33` is the R1 exit gate: it fails on newer compatibility-test AVDs rather than treating them as Android 13 evidence.

The check installs both APKs, invokes the DUMP-protected lifecycle and diagnostic probes, verifies all three signature-permission boundaries, launches Demo HMI, checks typed task completion/cancellation plus Governance evaluate/pending/cancel UI, and reports hardware/Driver/HAL/virtualization boundaries.

Run the R2 Binder lifecycle suite on Android 13 with:

```bash
bash tools/test_central_brain_android_binder_lifecycle.sh --require-api-33
```

It builds and installs the debug/androidTest artifacts, runs service-death/reconnect and cancel-completion instrumentation, then verifies callback death by force-stopping a separate debug client process. The test path never accesses hardware or vendor interfaces.

The normal build also runs `runtime-service:testDebugUnitTest`; `tools/check_central_brain_android_job_supervisor.sh` checks the R3A state, identity and no-hardware boundaries.

The existing hand-built Android Console and Client2 reverse-demo APK remain separate compatibility/test artifacts. They are not copied into this Gradle project.

Build success alone proves `contract_defined` only. R1 and complete R2 strict validation passed on the `central_brain_api33_x86_64` Android 13 AVD with system image revision 17, fingerprint `google/sdk_gphone64_x86_64/emu64x:13/TE1A.240213.009/12342917:userdebug/dev-keys`, and a `1920x1080` display. Binder/instrumentation plus API 33 evidence promotes only the typed Protocol Binding to `android_integrated`; it does not imply target-hardware validation or production qualification.

The local build currently warns that its Android SDK command-line tools understand SDK XML up to version 3 while the installed SDK contains version 4 metadata. The build succeeds, but production CI must align command-line tools and SDK metadata before qualification.

## P2-W08 Simulated Effect Adapter Base

`runtime-service/src/debug/java/com/centralbrain/runtime/simulation/` provides a debug/test-only base that implements
the existing typed `EffectAdapter` contract. `SimulationClock` advances monotonic time explicitly;
`FaultInjectionProfile` immutably selects no fault, delay, timeout, retryable failure, terminal failure or readback
mismatch; `SimulatedEffectAdapter` bounds process-memory records to 128 and preserves token idempotency.

Delivery status and simulated readback are separate. A mismatch can have delivery `APPLIED` while its observation is
`MISMATCH`; every simulation observation is source `SIMULATED` and is never production trusted. The base is absent
from main/release source, unregistered by production services, and does not parse domain targets, persist state,
publish plans, execute graphs, dispatch production effects or access Vehicle/VHAL/NPU/Driver-HAL.

The JVM suite, debug/release source compilation and Android 13/API 33 ARM64 probe establish
`simulated_effect_adapter_base_defined=true` and `simulated_effect_adapter_android13_arm64_verified=true`, while
`simulated_effect_adapter_production_registered=false`, `simulated_effect_adapter_runtime_wired=false`,
`effect_dispatch_enabled=false` and `hardware_accessed=false` remain enforced. P2-W09 adds the first typed HVAC
subclass without changing these production boundaries.

## P2-W09 Simulated HVAC Adapter

`SimulatedHvacEffectAdapter` remains in Runtime `src/debug` and extends the P2-W08 base. Its version 1 canonical
binary `HvacTarget` represents absolute power, target-temperature and fan-level commands; action, capability,
area, scalar, range and step are validated against the P2-W02 catalog before any state is admitted.

The adapter owns an isolated P2-W03 Digital Twin. Admission publishes desired state with a bounded TTL; immediate or
manual-clock delayed completion publishes source-SIMULATED reported state exactly once. Timeout, retryable failure and
terminal failure do not fabricate reported state. Readback mismatch writes a different valid value so both the base
observation and Twin reconciliation show mismatch. Duplicate apply does not advance the Twin revision.

JVM tests, debug/release compilation and the API 33 ARM64 probe establish
`simulated_hvac_adapter_defined=true`, `simulated_hvac_typed_target_verified=true`,
`simulated_hvac_desired_reported_verified=true` and `simulated_hvac_android13_arm64_verified=true`.
`simulated_hvac_production_registered=false`, `simulated_hvac_runtime_wired=false`,
`effect_dispatch_enabled=false` and `hardware_accessed=false` remain enforced. P2-W10 adds the debug-only Seat
domain adapter and fresh safety-state gate.

## P2-W10 Simulated Seat Adapter

`SimulatedSeatEffectAdapter` remains in Runtime `src/debug`. Its version 1 canonical `SeatTarget` supports absolute
heating/ventilation levels and recline angle, validating action, area, scalar, range and step against P2-W02 before
writing its isolated desired state.

Recline is admitted only with fresh NORMAL+PARKED safety state, an occupied/unbelted seat and a simulation approval
bound to the current safety/occupant revisions. The adapter repeats those checks immediately before dispatch;
motion, belt, occupancy or approval races permanently reject delivery without fabricating reported state. Delayed
recline exposes bounded progress and writes reported only after successful completion. Timeout/failure/mismatch and
duplicate behavior remain inherited from P2-W08 and are covered by the Seat suite.

JVM tests, debug/release compilation and the API 33 ARM64 probe establish
`simulated_seat_adapter_defined=true`, `simulated_seat_recline_safety_verified=true`,
`simulated_seat_dispatch_revalidation_verified=true`, `simulated_seat_progress_verified=true` and
`simulated_seat_android13_arm64_verified=true`. The providers are simulation-only, production source and Services
do not contain/register the adapter, and `simulated_seat_runtime_wired=false`, `effect_dispatch_enabled=false` and
`hardware_accessed=false` remain enforced. P2-W11 adds debug-only typed Media/Navigation state adapters.

## P2-W11 Simulated Media/Navigation Adapters

`SimulatedMediaEffectAdapter` accepts only a versioned cabin `PLAY`, `PAUSE` or `STOP` target and updates an
immutable source-SIMULATED player state. Its replaceable backend receives only the bounded enum, revision, elapsed
time and fault marker; it cannot be production-authorized, networked or Activity-starting.

`SimulatedNavigationEffectAdapter` canonicalizes a bounded POI query at the factory boundary, stores only its
SHA-256 after admission and passes only that digest to a deterministic synthetic backend. The published observation
contains synthetic POI/route IDs, a label key and bounded distance/duration, never the raw query or a real coordinate.
Backends declaring production authority, network, location upload or external Activity are rejected before use.

JVM tests, debug/release compilation and the API 33 ARM64 probe establish
`simulated_media_adapter_defined=true`, `simulated_navigation_adapter_defined=true`,
`simulated_navigation_query_digest_only=true`, `simulated_media_nav_replaceable_backend_verified=true` and
`simulated_media_nav_android13_arm64_verified=true`. Production source/Services do not contain/register either
adapter; `simulated_media_nav_runtime_wired=false`, `external_activity_started=false`, `location_uploaded=false`,
`network_accessed=false`, `effect_dispatch_enabled=false` and `hardware_accessed=false` remain enforced.

## P2-W12 Debug Simulation Controller

`runtime-service/src/debug` now contains `IDebugSimulationController` V1, a process-local controller and an exported
debug Service protected by `com.centralbrain.permission.CONTROL_DEBUG_SIMULATION`. The permission exists only in the
debug manifest and uses `signature` protection. Every Binder method also resolves calling UID/package/current signer
and requires the debug-only `debug.simulation.control` capability from the default-deny policy.

The typed interface controls only PARKED/MOVING/UNKNOWN, the P2-W01 canonical signal allowlist, fault profiles on the
four P2-W09..W11 adapters, manual time and reset. It returns only revisions, counts, elapsed time and a deterministic
snapshot digest. The 128-entry audit ring stores command/outcome/target digest rather than raw signal text; Service
logs record accepted and rejected commands without payloads.

JVM tests, debug/release compilation, shell signature rejection and the Android 13/API 33 ARM64 Binder probe establish
`debug_simulation_controller_defined=true`, `debug_simulation_controller_capability_enforced=true` and
`debug_simulation_controller_android13_arm64_verified=true`. Release has no AIDL source, permission, Service or probe;
`debug_simulation_controller_production_exported=false`, `debug_simulation_controller_runtime_wired=false`,
`vehicle_signal_provider_wired=false` and `hardware_accessed=false` remain enforced.

## P3-W01 Agent Graph Runtime state machine

`runtime-service/src/main/java/com/centralbrain/runtime/graph` now contains the process-local `AgentGraphRuntime`,
stable Graph/Node state enums and a control-only `NodeExecutorRegistry`. Admission revalidates and deep-copies the
P1/P2 typed Plan. One session has one active run and FIFO queued runs; different sessions use at most eight active
slots. The synchronized reducer exposes READY nodes in dependency order and handles claim, suspend, resume,
success/failure/skip, cancellation and manual-clock plan deadlines.

PARTIAL is a terminal optional-degradation result. Required failure closes the run as FAILED; impossible required
dependencies or unavailable compensation close it as STUCK. At most 64 runs and 256 events per run are retained.
Events expose only IDs, enum states, reason codes, elapsed time and a chained SHA-256; Plan/node input and executor
output are not projected.

Seven JVM test groups, debug/release compilation and the Android 13/API 33 ARM64 probe establish
`agent_graph_runtime_defined=true`, `agent_graph_state_machine_verified=true`,
`agent_graph_same_session_fifo_verified=true`, `agent_graph_event_projection_bounded=true`,
`agent_graph_compensation_fail_closed_verified=true` and `agent_graph_android13_arm64_verified=true`. The registry
does not hold or invoke a typed executor;
`agent_graph_executor_dispatch_enabled=false`, `agent_graph_runtime_persistence_wired=false`,
`agent_graph_runtime_binder_published=false`, `agent_graph_runtime_production_wired=false`,
`effect_dispatch_enabled=false`, `model_invoked=false` and `hardware_accessed=false` remain enforced. P3-W02 typed
node executors is documented below.

## P3-W02 Typed node executors

`runtime-service/src/main/java/com/centralbrain/runtime/graph` now defines immutable typed input/output/result
contracts, an exact-class schema for every one of the 11 Plan node types and `TypedNodeExecutor<I,O>`. The
`NodeExecutorRegistry` validates schema classes and rejects executors that request production authorization, Effect or
model dispatch, network, hardware, or raw-data persistence. It does not retain or invoke executor instances.

Seven deterministic executors exist only under `src/debug`: Context, Policy, Approval, Effect, Verification, Summary
and Compensation. Context/Verification never upgrade production trust. Policy/Approval require explicit trusted test
authority evidence. Effect always returns WAITING/NOT_DISPATCHED and Compensation returns
REJECTED/NOT_DISPATCHED. Model/Tool/Memory have digest-only schemas and no executor or fallback.

Eight JVM test groups, debug/release compilation and the Android 13/API 33 ARM64 probe establish
`typed_node_executor_contract_defined=true`, `typed_node_executor_schema_count=11`,
`typed_node_executor_debug_count=7`, `typed_node_executor_exact_class_verified=true`,
`typed_node_executor_effect_fail_closed_verified=true` and
`typed_node_executor_unsupported_fail_closed_verified=true`. Release contains no deterministic executor or probe;
`typed_node_executor_graph_dispatch_enabled=false`, `typed_node_executor_production_wired=false`,
`effect_dispatch_enabled=false`, `model_invoked=false`, `network_accessed=false` and `hardware_accessed=false` remain
enforced. P3-W03 CheckpointSerializer is documented below.

## P3-W03 CheckpointSerializer

`runtime-service/src/main/java/com/centralbrain/runtime/graph` now defines immutable `CheckpointValue`, registered
`CheckpointSerializer`, digest-bound `CheckpointEnvelope` and strict `JsonPrimitiveCheckpointSerializer`. A
registration freezes an exact type, schema version, payload class and explicit codec. Codecs can exchange only bounded
strings, booleans, integers/decimals, enum names and immutable list/map trees; no class name is read from data.

The canonical JSON envelope fixes field order, sorts payload map keys, normalizes numbers and binds all fields except
the digest through domain-separated SHA-256. Strict streaming decode rejects unknown type/version/class, duplicate or
unknown fields, null/trailing/malformed JSON, digest changes and non-canonical bytes. Limits are 64 KiB, eight payload
levels, 1,024 value tokens and 64 items per container. Java serialization, reflection, Binder/Parcel blobs, network and
hardware material are absent from the main implementation.

Eight JVM test groups, debug/release compilation and the Android 13/API 33 ARM64 probe establish
`checkpoint_serializer_defined=true`, `checkpoint_serializer_registered_dto_verified=true`,
`checkpoint_serializer_canonical_digest_verified=true`, `checkpoint_serializer_size_depth_limit_verified=true`,
`checkpoint_serializer_security_corpus_verified=true` and
`checkpoint_serializer_android13_arm64_verified=true`. The debug probe is absent from release;
`checkpoint_serializer_java_serialization_enabled=false`, `agent_graph_runtime_persistence_wired=false`,
`agent_graph_executor_dispatch_enabled=false`, `effect_dispatch_enabled=false`, `model_invoked=false` and
`hardware_accessed=false` remain enforced.

## P3-W04 Retry/Timeout policy

`NodeTimeoutPolicy` derives a monotonic attempt window from the typed Plan node timeout and clamps it to the plan
deadline. Expiry is inclusive at the effective deadline, the caller supplies elapsed time, and the policy owns no
wall clock, thread, executor or timer. `BackoffCalculator` applies bounded exponential delay with deterministic
SHA-256-derived jitter; the configured delay cannot exceed the existing 120-second node timeout bound.

`NodeRetryPolicy` freezes node type, attempt budget and a digest of the idempotency key. Retryable non-Effect failures
can schedule only attempts 2..3 and only when backoff completes strictly before the plan deadline. Terminal failures
and cancellation never retry. `effect.execute` and `compensate` require the existing typed idempotency key and cannot
retry until reconcile reports `CONFIRMED_NOT_APPLIED`; unknown delivery returns `RECONCILE`, while confirmed applied
returns `STOP_EFFECT_ALREADY_APPLIED`.

Eight JVM test groups, debug/release compilation and the Android 13/API 33 ARM64 probe establish
`node_retry_policy_defined=true`, `node_timeout_policy_defined=true`,
`backoff_deterministic_bounded_verified=true`, `timeout_deadline_clamp_verified=true`,
`retry_attempt_budget_verified=true`, `effect_idempotency_reconcile_gate_verified=true`,
`retry_deadline_fail_closed_verified=true` and `retry_timeout_policy_android13_arm64_verified=true`.
`retry_timeout_policy_runtime_wired=false`, `agent_graph_executor_dispatch_enabled=false`,
`effect_dispatch_enabled=false`, `model_invoked=false` and `hardware_accessed=false` remain enforced. P3-W05 Durable
approval interrupt is the next work package.

## P3-W05 Durable approval interrupt

`ApprovalInterruptRecord` is an immutable checkpoint-ready state bound to canonical approval/session/plan IDs,
owner fingerprint, node, action/plan/context/policy/Safety digests, plan deadline and a maximum five-minute expiry.
`ApprovalInterruptExecutor` creates pending records and accepts only trusted APPROVED/REJECTED/CANCELLED decisions
inside the validity window; terminal replay and early expiry fail closed. Its explicit
`graph.approval.interrupt` codec stores epoch values as canonical decimal strings so current epoch milliseconds do
not violate the bounded primitive-number contract. P3-W05 also corrected the checkpoint envelope `createdAt` field
to the same canonical long-string representation.

`ApprovalResumeValidator` resumes only an APPROVED, unexpired, trusted record whose current owner/session/plan/node/
action/plan digest are identical. It then requires fresh context, current policy authorization and digest, allowed
capability, trusted SAFE state and an unchanged Safety digest. The result exposes only allowed/reason/result digest.

Eight JVM test groups and the Android 13/API 33 ARM64 probe establish
`approval_interrupt_record_defined=true`, `approval_interrupt_binding_verified=true`,
`approval_interrupt_checkpoint_roundtrip_verified=true`, `approval_interrupt_trusted_decision_verified=true`,
`approval_resume_owner_plan_context_policy_verified=true`, `approval_resume_safety_revalidation_verified=true`,
`approval_resume_expiry_verified=true` and `approval_interrupt_android13_arm64_verified=true`.
`approval_interrupt_persistence_wired=false`, `approval_grant_service_published=false`,
`agent_graph_executor_dispatch_enabled=false`, `effect_dispatch_enabled=false`, `model_invoked=false` and
`hardware_accessed=false` remain enforced. Room/restart durability belongs to P3-W09; P3-W06 EffectCoordinator is
the next work package.

## P3-W06 EffectCoordinator

`EffectBatch` freezes up to 16 existing typed Effect intents under one session/plan/action/plan digest, unique Effect
and idempotency identities, bounded resources/dependencies and a structural SHA-256. `EffectDependencyPlanner`
rejects cycles and generates deterministic waves in which dependencies are earlier and one resource appears at most
once. It owns no execution thread.

`AdapterRegistry` resolves an exact capability/area/profile route. DEBUG accepts only isolated simulation entries;
PRODUCTION accepts only activated, explicitly authorized non-simulation entries. No profile fallback exists and the
repository registers no production adapter. `EffectCoordinator` prepares every item before dispatch; any required
prepare failure causes zero adapter applies, while an optional failure can degrade the batch. Dispatch follows the
dependency waves and emits one defensive typed observation per item. Adapter APPLIED maps only to DELIVERED;
verification/readback is handled by the separate P3-W07 contract and is not called from Coordinator.

Nine JVM test groups and the Android 13/API 33 ARM64 probe establish `effect_batch_defined=true`,
`effect_dependency_plan_verified=true`, `effect_resource_conflict_serialized=true`,
`effect_adapter_registry_profile_isolation_verified=true`, `effect_prepare_all_required_verified=true`,
`effect_optional_degradation_verified=true`, `effect_independent_observation_verified=true` and
`effect_coordinator_android13_arm64_verified=true`. `effect_coordinator_graph_wired=false`,
`effect_coordinator_persistence_wired=false`, `production_effect_adapter_registered=false`,
`production_effect_dispatch_enabled=false`, `effect_verification_reconciliation_wired=false` and
`hardware_accessed=false` remain enforced. P3-W07 supplies the separate process-local verifier/reconciler below;
Coordinator/Graph/Room wiring remains pending.

## P3-W07 Effect verification/reconciliation

`EffectVerifier` consumes the existing typed intent/observation plus bounded callback or typed readback evidence.
It revalidates capability/area/risk/unit/range, target specification digest, source/profile, evidence time and
deadline. CALLBACK_ONLY is limited to LOW-risk services without readback; REPORTED_EQUALS,
REPORTED_TOLERANCE, STATE_TRANSITION and COMPOSITE calculate match internally. A successful delivered/unknown
Effect emits separate APPLIED and VERIFIED observations; mismatch remains APPLIED, unavailable evidence becomes
UNKNOWN and deadline/trust failures remain fail closed.

`DigitalTwinEffectReconciler` calls only linearizable `queryStatus`; it never calls apply. APPLIED delivery can be
checked against one immutable fresh Twin snapshot, NOT_APPLIED is returned as a fact for the retry policy, and
UNKNOWN produces a caller-owned 250 ms..30 s next-reconcile time capped by the Effect deadline. A VERIFIED replay
returns before adapter resolution/query. The process-local Twin has no production trust, so PRODUCTION fails before
query and cannot fall back to debug simulation.

Nine JVM test groups and the Android 13/API 33 ARM64 probe establish `effect_verifier_defined=true`,
`effect_verification_policies_verified=true`, `effect_state_separation_verified=true`,
`effect_unknown_reconciliation_verified=true`, `effect_verified_redispatch_blocked=true`,
`effect_production_readback_fail_closed=true` and `effect_verification_android13_arm64_verified=true`.
`effect_verification_reconciliation_runtime_wired=false`, `effect_verification_scheduler_wired=false`,
`effect_verification_persistence_wired=false`, `effect_verification_production_readback_wired=false`,
`effect_verification_graph_wired=false`, `production_effect_dispatch_enabled=false` and `hardware_accessed=false`
remain enforced. P3-W08 Compensation/Undo is the next work package.

## P3-W08 Compensation/Undo

`CompensationPlanner` accepts a complete terminal source `EffectBatch`, one state for every Effect, an explicit
capability+catalog-area reversible allowlist, a VALID typed before snapshot and a caller-built new Effect intent.
Only VERIFIED reversible Effects can enter the plan. The source compensation descriptor, prepared before-state
digest, capability/path/area/range/unit, source Context and absolute before scalar must all match. The generated
waves reverse the original dependency waves; compensation intents share a new session/plan/action, use a
source-bound idempotency key, cannot recursively advertise Undo and never mutate the original VERIFIED terminal
observation.

`UndoService` is a pure Java process-local admission object, not an Android Service. It issues one digest-bound,
deadline-capped P1 `UndoHandle` per compensation step. Request admission verifies every handle and its TTL, then
requires a fresh matching Context, trusted current Governance authority, allowed capabilities, authorized policy
and trusted SAFE state. Success creates a new immutable governed task plus REQUESTED handle copies; a bounded
owner+idempotency record returns the same task on replay. PRODUCTION always returns
`PRODUCTION_COMPENSATION_UNAVAILABLE` because no production authority, durable before material or adapter exists.

Eight JVM test groups and the Android 13/API 33 ARM64 probe establish
`compensation_planner_defined=true`, `compensation_absolute_before_verified=true`,
`compensation_reverse_dependency_verified=true`, `compensation_irreversible_rejected=true`,
`undo_ttl_governance_verified=true`, `undo_new_governed_task_verified=true`,
`undo_idempotent_replay_verified=true`, `undo_production_fail_closed=true` and
`compensation_undo_android13_arm64_verified=true`. `compensation_undo_runtime_wired=false`,
`compensation_undo_persistence_wired=false`, `undo_binder_service_published=false`,
`compensation_dispatch_enabled=false`, `production_compensation_authority_wired=false`,
`effect_dispatch_enabled=false` and `hardware_accessed=false` remain enforced. P3-W09 Restart recovery is the
next work package.

## P3-W09 Restart recovery

`GraphRestartReconciler` is a pure-Java, fail-closed reducer over an immutable persistent Graph projection. It
recovers WAITING/EXECUTING/UNKNOWN state without invoking executors or adapters. Effect, approval, compensation,
model, tool and memory-write nodes return to WAITING with typed reconcile/revalidation directives. A control node
can return READY only after a VALID checkpoint and caller-provided Governance revalidation. Missing, mismatched or
untrusted checkpoints make the non-terminal Graph STUCK; an expired Graph becomes FAILED.

`DurableGraphRecoveryRepository` reuses the frozen Room v4 Plan/PlanNode/EffectObservation/Compensation tables. It
loads a bounded projection, validates plan/session/digest and complete node identity, applies only Plan/Node target
states in one transaction, and writes a digest-only `GRAPH_RESTART_RECONCILED` audit exactly once for the same
result digest. Effect observations and compensation evidence are immutable. Reopening the already-recovered state
therefore changes zero rows and creates no second audit or side effect.

The debug-only DUMP probe seeds EXECUTING/WAITING/UNKNOWN material, survives two installer-driven Runtime
`force-stop` operations, and verifies first recovery plus idempotent replay on Android 13/API 33 ARM64. The process
generation proof stores only a SHA-256 derived from the nonce, PID and process birth elapsed time.

`graph_restart_reconciler_defined=true`, `graph_restart_room_v4_repository_verified=true`,
`graph_restart_process_death_verified=true`, `graph_restart_idempotent_reopen_verified=true`,
`graph_restart_audit_exactly_once_verified=true` and `graph_restart_side_effect_count=0` are verified.
Historical A-B-A result-digest replay is also verified exactly once per deterministic audit event ID.
`graph_restart_runtime_wired=false`, `graph_restart_binder_published=false`,
`graph_restart_executor_dispatch_enabled=false`, `graph_restart_effect_dispatch_enabled=false`,
`graph_restart_production_wired=false`, `agent_graph_runtime_persistence_wired=false`,
`production_effect_dispatch_enabled=false` and `hardware_accessed=false` remain enforced. Runtime/Binder activation
is tracked by DEV-050. P4-W01 migrated Client2 to the published Session/Event facade without activating this
recovery path. P4-W02 then replaced the legacy Smali lifecycle owner with an immutable HMI state/reducer and a
maintained Java coordinator that resumes the same owner Session from a text-free checkpoint. P4-W03 added the
intent/plan/execution/result renderer, four natural-scene inputs, source/driving/connection status, exact 1920x1080
safe frame and secondary HVAC/Seat drawer. P4-W04 HVAC controls are next; scenario/Graph/Effect/vehicle dispatch remains disabled.
