# Central Brain Android AIDL Contract

Version: 1.4
Date: 2026-07-17
Stage: R2 complete / Stage 2 P1-W01..P1-W04 contracts `contract_defined`

## Scope

This document defines the Android 13 user-space Protocol Binding between the Central Brain SDK AAR and Runtime Service APK. The architecture diagram remains the requirement baseline. This contract implements only the Binder boundary owned by `AI SDK -> Protocol Binding -> Runtime & Governance/AIOS Kernel`; it does not add a new architecture layer.

Req IDs: `XSC-001`, `XSC-004`, `XSC-005`, `XSC-006`, `FW-U-003`, `FW-U-004`, `FW-U-007`, `NV-F-001`, `NV-F-003`, `NV-F-008`, `NV-F-009`, `NV-G-003`, `NV-G-004`, `NV-G-005`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-003`, `DEL-004`, `S2-SES-001`, `S2-SCN-001`, `S2-GRF-001`, `S2-EVT-001`, `S2-EFF-001`, `S2-SAF-001`, `S2-UX-003`.

R2A delivered Gradle application structured AIDL in `central-brain-sdk`; R2B publishes and consumes that frozen V1 contract from separate application APKs. It is not VINTF stable AIDL: the project cannot add a Soong `aidl_interface`, freeze platform API under `aidl_api`, or modify vendor/system build files because the target Android SDK and system image are prebuilt. This accepted temporary limitation is tracked under `DEV-018` and `ISSUE-021`.

## Binder Surfaces

| Surface | Package/interface | Responsibility | Forbidden content |
| --- | --- | --- | --- |
| Production | `com.centralbrain.sdk.production.ICentralBrainRuntime` | Typed agent-task submit, cancel, small status query | JSON, readiness rollups, audit dumps, hardware evidence |
| Callback | `com.centralbrain.sdk.production.ICentralBrainTaskCallback` | Oneway progress, completion and failure notifications | Blocking work, request dispatch, large payloads |
| Diagnostic | `com.centralbrain.sdk.diagnostics.ICentralBrainDiagnostics` | Bounded, read-only, cursor-paged diagnostics | Task submit/cancel, state mutation, hardware activation |
| Session V1 | `com.centralbrain.sdk.session.ICentralBrainSessionRuntime` | Stage 2 open/get/list/cancel contract | Runtime publication, callback/event stream, vehicle/NPU dispatch |
| Session Event V1 | `com.centralbrain.sdk.event.ICentralBrainSessionEvents` | Bounded cursor replay and callback registration contract | Service publication, caller authority, raw payload, vehicle/NPU dispatch |
| Effect DTO V1 | `com.centralbrain.sdk.effect` structured parcelables; no interface | Typed target/lifecycle, approval binding and undo eligibility | Effect Service, approval grant/response, undo execution, Room and hardware |

R2B publishes production and diagnostic interfaces from separate Android Service components with separate signature-level permissions. The Demo and production SDK path request only `com.centralbrain.permission.BIND_RUNTIME`; diagnostic access is independently protected by `com.centralbrain.permission.ACCESS_DIAGNOSTICS`.

## Production Types

All parcelables start with `schemaVersion=1`. New fields may only be appended and must have explicit defaults.

| Type | Required semantics |
| --- | --- |
| `AgentTaskRequest` | Client request/session IDs, utterance, locale, monotonic deadline, priority and idempotency key; no caller-provided permission field |
| `TaskHandle` | Runtime task ID and monotonic acceptance timestamp; returned before work begins |
| `TaskUpdate` | Task ID, state, bounded progress 0..100, strictly increasing sequence and short message |
| `TaskResult` | Terminal task ID, completion code, user-visible reply, summary and monotonic completion timestamp |
| `TaskFailure` | Terminal task ID, stable error code, bounded message and retryable flag |

Task states are `UNKNOWN=0`, `ACCEPTED=1`, `RUNNING=2`, `COMPLETED=3`, `FAILED=4`, and `CANCELLED=5`. Terminal states never transition again.

Cancellation reasons are `USER=1`, `CLIENT_DIED=2`, `DEADLINE=3`, and `POLICY=4`. Stable failure codes are `NONE=0`, `INVALID_ARGUMENT=1`, `DEADLINE_EXCEEDED=2`, `CANCELLED=3`, `SERVICE_DIED=4`, and `INTERNAL=5`.

## Production Methods

| Method | Thread/latency contract | Result |
| --- | --- | --- |
| `getProtocolVersion` | Constant-time, target <= 10 ms | Integer protocol version; V1 returns 1 |
| `getProtocolHash` | Constant-time, target <= 10 ms | Immutable 64-character V1 identity token |
| `submitAgentTask` | Validate envelope, allocate task/DeathRecipient and return; target <= 50 ms | `TaskHandle`; execution happens off Binder thread |
| `cancelTask` | Mark cancellation request only; target <= 20 ms | Whether cancellation was accepted; terminal callback follows asynchronously |
| `getTaskStatus` | Bounded in-memory/durable lookup; target <= 20 ms | Latest small `TaskUpdate` |

`ICentralBrainTaskCallback` is a `oneway interface`; generated Java proxies must use `IBinder.FLAG_ONEWAY`. A callback must never be called while holding the task-registry lock.

## Diagnostic Contract

`DiagnosticQuery.pageSize` defaults to 50 and is clamped to `1..100`. `cursor` is opaque to clients. `DiagnosticPage` contains structured `DiagnosticRecord[]`, an opaque `nextCursor`, `hasMore`, and a monotonic generation timestamp. R2B must keep each reply comfortably below `IBinder.getSuggestedMaxIpcSizeBytes()` and target 64 KiB or less.

Diagnostics are read-only snapshots. They must not invoke task submission, cancel tasks, dispatch SOA/Skill actions, probe device nodes, activate a model provider, or close governance gates.

## R2B Implementation And Evidence

- `CentralBrainRuntimeService` validates and accepts requests on Binder, returns a `TaskHandle`, and executes the deterministic hardware-free task on a single-thread executor.
- Cancellation marks terminal state synchronously but dispatches the cancellation update/failure asynchronously. Duplicate cancellation returns the original accepted outcome and does not duplicate notification.
- Runtime callback Binders and the SDK service Binder use `linkToDeath`. R2C associates death recipients with exact Binder instances and verifies process death, explicit rebind and terminal race behavior.
- `CentralBrainDiagnosticService` returns three deterministic structured records and clamps each page to `1..100`; it does not call the production Binder or any adapter.
- `CentralBrainClient` binds an explicit component and the SDK AAR contributes a narrow `<queries><package android:name="com.centralbrain.runtime"/></queries>` declaration for Android 11+ package visibility. `QUERY_ALL_PACKAGES` is forbidden.
- API 33 x86_64 validation passed production protocol negotiation, completion callback, duplicate cancel, shell denial by both signature permissions, Demo diagnostic-permission absence and diagnostic page probing. Debug probes require `android.permission.DUMP` and are absent from release APKs.
- Public app-SDK code throws `IllegalArgumentException` for malformed synchronous requests; it does not depend on hidden `android.os.ServiceSpecificException`.

## R2C Lifecycle And Race Evidence

- `CentralBrainClient.reconnect()` explicitly unbinds and binds the known Runtime component. Stale or duplicate death notifications cannot clear a newer Binder connection.
- Runtime force-stop fails each active task callback once with `ERROR_SERVICE_DIED`, emits one disconnect notification, and permits a new typed task to complete after explicit reconnect.
- A 15-task cancel/completion race produces both terminal outcomes, keeps duplicate cancel outcomes consistent, emits exactly one terminal callback per task and emits no queued update after terminal.
- A DUMP-protected debug client in a separate app process is force-stopped while its task is active. The independently started Runtime observes callback Binder death and cancels with `CANCEL_REASON_CLIENT_DIED`.
- `tools/test_central_brain_android_binder_lifecycle.sh --serial emulator-5554 --require-api-33` records all R2 exit flags. Test Activities and instrumentation are excluded from release artifacts.

## Cancellation And Death

- Client SDK links a `DeathRecipient` to the production service Binder. Service death fails all non-terminal callbacks once with a stable `SERVICE_DIED` error; explicit `reconnect()` performs the required unbind/rebind.
- Runtime links a `DeathRecipient` to every remote callback Binder. Client death removes the callback and requests cancellation of tasks that have no durable detached-execution capability.
- `cancelTask` is idempotent. Repeated cancellation returns the same accepted/not-accepted outcome and never repeats a side effect.
- Callback `RemoteException` is treated as callback death; no callback retry loop may block a Binder thread.
- R2 must test service-process death, client-process death, callback death, duplicate cancel and cancel-vs-completion races.

All listed R2 death/cancel/race cases have API 33 evidence. Durable task recovery across Runtime process death is not part of R2 and remains R4 work.

## Versioning

V1 protocol identities:

- Production: version `1`, hash `55bed5957371d691221ba99032a07f7162f4699c6a0c6c3d1f5e1fcc5707f1f5`.
- Diagnostic: version `1`, hash `319aaf93eebc5b35b9466952bf97a05bd67465808ed2e3b2ff5bc6cc13342386`.

Because this is app-layer Gradle AIDL, `getProtocolVersion` and `getProtocolHash` are explicit project methods. The names `getInterfaceVersion` and `getInterfaceHash` are reserved by the AIDL compiler and are not used. Any V2 change must preserve V1 transaction order, append methods/fields only, update the protocol identity, and retain a compatibility test.

## Security And Hardware Boundary

R2B publishes app-layer production and diagnostic Binders behind separate signature permissions. R3A now derives a caller snapshot from Binder UID, Android user serial, UID package evidence and each package's current signer SHA-256; identity can never be supplied by `AgentTaskRequest`. The Job Supervisor binds every task to that snapshot, hides status from non-owners and denies non-owner cancellation.

R3A is not the complete trusted capability model. R3B must map package + current signer to explicit capabilities, deny unknown callers by default and prove denial from a second independently packaged client on API 33. R3C must add trusted Safety/Vehicle State inputs and recoverable approval for high-risk actions. Signature permission alone is not treated as capability authorization.

No AIDL type includes a device node, fd, shared memory, vendor handle, PCIe/NPU object, vehicle bus frame, camera/audio buffer, Safety Runtime token or virtualization control. `hardware_accessed=false`, `driver_development_triggered=false`, and `virtualization_development_triggered=false` remain mandatory.

## R3A Job Supervisor And Identity Evidence

- `JobSupervisor` is a bounded AIOS Kernel owner for `ACCEPTED/RUNNING/COMPLETED/FAILED/CANCELLED`; invalid transitions, terminal re-entry and progress regression fail closed.
- Capacity is 128 records. Active records and terminal records with unsettled callback delivery are never pressure-evicted; settled terminal records expire after five minutes or are deterministically evicted to admit later work.
- `AndroidCallerIdentityResolver` uses only public Android APIs and current APK signers. Signing history is not silently treated as a current capability credential.
- Unit tests cover lifecycle, owner isolation, idempotent cancel, capacity and retention. API 33 validation reports `job_supervisor_active=true`, `trusted_caller_identity_resolved=true`, and `request_identity_fields_used=false` while preserving R2 lifecycle/race results.
- The V1 AIDL checksum is unchanged because R3A is an internal Runtime implementation and needs no request field or transaction addition.

## R3B Capability Policy Evidence

- Runtime loads a strict V1 XML with an immutable default-deny rule. Allowed Demo production and Runtime diagnostic principals are literal package names paired with the Runtime's complete current signer set; no signer digest is hard-coded before APK signing.
- `getProtocolVersion/getProtocolHash`, `submitAgentTask`, `getTaskStatus`, and `cancelTask` enforce protocol-read, submit, own-status and own-cancel capabilities respectively before parsing or task lookup. Diagnostic version/hash/page independently enforce diagnostic-read.
- A test-only second APK uses the same debug signer and obtains both `BIND_RUNTIME` and `ACCESS_DIAGNOSTICS`, proving the outer manifest permissions passed. Because its package is absent from policy, both Binder surfaces throw `SecurityException` and log `PACKAGE_NOT_CONFIGURED` without signer bytes.
- API 33 evidence is produced by `tools/test_central_brain_android_capability_policy.sh`; the normal delivery build excludes the probe.
- R3B changes no AIDL field, transaction order, version or hash. Trusted Safety/Vehicle State and high-risk approval remain R3C/R4 work.

## R3C1 Governance Core Boundary

R3C1 adds no method, field, transaction or hash to the frozen Runtime/Diagnostic V1 interfaces. The pure Java governance core derives one of five risk classes from exact Runtime-owned Action IDs, consumes Safety/Vehicle State only through a Runtime-owned provider, and creates bounded owner-isolated pending approval records for parked high-risk actions. All policy outcomes keep service dispatch disabled.

The current state provider is a hardware-free stub and the current approval registry cannot grant approval or recover across process restart. R3C2 exposes governance through a separate typed AIDL rather than append unrelated action/approval methods to `ICentralBrainRuntime`; R4 will add durable approval/checkpoint/outbox ownership. This preserves V1 task-client compatibility and keeps target VHAL/Safety Runtime work behind `DRV-GAP-002`/`DRV-GAP-005`.

## R3C2 Governance AIDL V1

R3C2 publishes a third, independent app-layer structured AIDL surface:

- Interface: `com.centralbrain.sdk.governance.ICentralBrainGovernance`.
- Parcelables: `ActionRequest`, `ActionDecision`, `ApprovalHandle`, `ApprovalStatus`.
- Permission: `com.centralbrain.permission.BIND_GOVERNANCE` with `signature` protection.
- SDK facade: `CentralBrainGovernanceClient` using an explicit component, narrow package visibility and Binder death handling.
- Frozen source list: `central-brain-sdk/aidl-api/governance-v1.sha256`.

Methods are bounded quick-return calls: protocol version/hash, evaluate exact Action ID, create pending approval, owner status and owner cancel. There is intentionally no approval grant method. `ActionRequest` contains no risk class, Safety/Vehicle State, identity, permission, package or signer field; those contexts remain Runtime-owned. Missing/non-owner status returns `APPROVAL_STATUS_UNKNOWN`, and cancel returns false without disclosing another owner's record.

API 33 allowed-client evidence verifies policy-only read/comfort, OTA approval-required, pending creation and idempotent cancel with `sourceHardwareBacked=false`, `sourceProductionTrusted=false`, `grantSupported=false`, `durable=false`, and `dispatchAllowed=false`. A same-signer unconfigured package passes the outer Governance permission and bind, then receives `SecurityException` for protocol/evaluate/request/status/cancel from the inner capability policy. The task/diagnostic V1 files, transaction order and checksum remain unchanged.

## Stage 2 P1-W01 Session AIDL V1

P1-W01 adds an independent app-layer contract without changing the frozen task/diagnostic or Governance V1
surfaces:

- Interface: `com.centralbrain.sdk.session.ICentralBrainSessionRuntime`, version 1, hash
  `f4b3ac677b3294e7cb20382652d37ef995432a2e5ca335d131295d6a43d4024c`.
- Parcelables: `SessionRequest`, `SessionHandle`, `SessionSnapshot`, `SessionQuery`, `SessionPage`.
- Methods: constant-time version/hash negotiation plus bounded `openSession`, `getSession`, `listSessions` and
  `cancelSession` contract declarations.
- Freeze evidence: `central-brain-sdk/aidl-api/session-v1.sha256` and
  `tools/check_central_brain_android_session_contract.sh`.

`SessionContract` rejects unknown schema/source/seat/state values, non-canonical IDs, oversized strings/pages,
invalid timestamp ordering and deadlines beyond the five-minute admission window. Page size is limited to 50,
summary to 512 characters and utterance to 1024 characters to preserve the 64 KiB Binder target.

The request contains no owner, permission, signer, speed, gear or belt assertion. A future Runtime implementation
must derive owner from Binder identity, assign TTL and enforce idempotency by request ID plus canonical content.
`session_contract_v1_defined=true`, but `session_runtime_service_published=false` and
`session_runtime_persistence_wired=false`. SDK bind/death/reconnect belongs to P1-W05 after a service owner exists.
Android instrumentation in P1-W01 tests real Parcel round trips only and reports `hardware_accessed=false`.
The test passed on an Android 13/API 33 ARM64 physical controller on 2026-07-17 and its temporary test package
was removed afterwards: `session_parcel_physical_android13_arm64_verified=true`. This is serialization evidence,
not Session Service, vehicle, NPU or target-hardware qualification.

## Stage 2 P1-W02 Plan/Node AIDL V1

P1-W02 adds four independent structured parcelables under `com.centralbrain.sdk.plan` without changing the
task/diagnostic, Governance or Session V1 files and transaction order:

| Type | Required contract fields | Boundaries |
| --- | --- | --- |
| `ScenarioPlan` | schema, plan/session/scenario IDs, revision, context/plan digests, compiled/deadline, nodes, dependencies | 1..64 nodes, <=256 edges, <=15 minute plan window |
| `PlanNode` | schema, node/type/capability/input digest/resource, timeout, maxAttempts, idempotency, required, compensation, policy | timeout 1..120000 ms, attempts 1..3, allowlisted type only |
| `NodeDependency` | schema, prerequisite, dependent, success/terminal condition | both nodes must exist; self/duplicate edge rejected |
| `NodePolicy` | schema, policy ID/version, risk, approval/verification flags, failure mode | unknown version/risk/failure mode fails closed |

The V1 node-type allowlist is `context.capture`, `policy.evaluate`, `approval.interrupt`, `effect.execute`,
`effect.verify`, `tool.invoke`, `model.invoke`, `memory.query`, `memory.write`, `summary.render` and `compensate`.
`PlanContract` validates canonical IDs and lowercase SHA-256 digests, unique node/idempotency identities, referenced
edges, an acyclic graph, compensation target/type/loop rules, maximum graph depth 16 and width 8. Retryable nodes
and side-effect types require an idempotency key. HIGH/CRITICAL policy metadata requires approval, but Runtime
Governance must still re-evaluate current caller, Context, Safety and capability immediately before dispatch.

The concatenated AIDL identity is
`8dbf27424a09ecac969aff444e7fc9e3c939c7bc5c6687de2d8a5a627d60dabd`; per-file sources are frozen in
`central-brain-sdk/aidl-api/plan-v1.sha256` and checked by
`tools/check_central_brain_android_plan_contract.sh`. The cumulative instrumentation passed Parcel round trips,
cycle rejection and unknown-type rejection on an Android 13/API 33 ARM64 physical controller and then removed the
temporary test package.

`plan_contract_v1_defined=true`, `plan_parcel_physical_android13_arm64_verified=true` and
`plan_runtime_published=false`. No Binder method publishes a Plan, no compiler/graph scheduler consumes it, no
Room schema changed and no vehicle/NPU/Driver-HAL path was accessed. This is contract evidence, not execution,
hardware validation or production qualification.

## Stage 2 P1-W03 Event AIDL V1

P1-W03 freezes an independent Event surface instead of changing `ICentralBrainSessionRuntime` V1. It adds five
structured parcelables, one one-way callback and one query/registration interface under
`com.centralbrain.sdk.event`:

| Type/surface | Contract | Boundaries |
| --- | --- | --- |
| `RuntimeEvent` | immutable event identity, sequence, parent, type/source/time, privacy class and digests | exactly one typed payload; no raw vehicle/model/blob/handle |
| `ActionEvent` | action/node/capability/state/action digest and required flag | metadata only; never grants authorization |
| `ObservationEvent` | observation/subject/outcome/quality/evidence digest and terminal flag | evidence remains digest-oriented |
| `MessageEvent` | message/role/locale/bounded display text/content digest/redaction metadata | display text <=1024; redaction is explicit |
| `EventPage` | request cursor, contiguous events, next cursor/sequence, hasMore/redaction/time | page 1..100; cursor is opaque and bounded |
| `ICentralBrainSessionEvents` | version/hash, `getEvents`, register/unregister callback | contract only; owner/capability derived by future Service |
| `ICentralBrainSessionEventCallback` | one-way event, overflow and close notifications | notification only; cursor replay remains authoritative |

`EventContract` recognizes 23 event types from `UserMessageReceived` through `SessionStateChanged`. It rejects
unknown schema/type/source/privacy/payload enums, non-canonical UUID/digest values, payload/type mismatch,
sequence gaps, invalid or forward parent references, unsafe redaction, cursor discontinuity and replay mutation.
Events have semantic immutability: a correction is a new event, and `validateImmutableReplay` requires the same
event identity/digest when a replay overlaps prior evidence. There is no event update method.

The normalized seven-file interface identity is
`bb3618ca5f5818ce70b3a889a928b54ad83c70f0e439db5b62c67eb3234957d5`. Per-file checksums are frozen in
`central-brain-sdk/aidl-api/events-v1.sha256` and verified by
`tools/check_central_brain_android_event_contract.sh`, which also rechecks all earlier AIDL checksums.

JVM tests cover positive typed pages plus version/type/payload, ordering/parent, redaction/page-marker,
cursor/replay mutation and size/enum rejection. Cumulative instrumentation passed Parcel round trips and those
rejection paths on the Android 13/API 33 ARM64 physical controller on 2026-07-17; the temporary test APK was then
removed. The evidence reports `event_contract_v1_defined=true`,
`event_parcel_physical_android13_arm64_verified=true`, `event_runtime_service_published=false`,
`event_callback_service_published=false` and `hardware_accessed=false`. P1-W05 now owns publication and callback
lifecycle; P1-W06 owns Room v4 persistence. This increment is not Event runtime, vehicle/NPU access, Driver/HAL
development or production qualification.

## Stage 2 P1-W04 Effect/Approval AIDL V1

P1-W04 freezes four structured parcelables without adding or changing a Binder interface:

| Type | Required contract fields | Fail-closed boundary |
| --- | --- | --- |
| `EffectIntent` | effect/session/plan/node/action/capability/area IDs, one typed scalar, target/idempotency/plan/Context digests, Context version, risk, verification, reversibility and deadline | inactive scalar fields empty/default; no raw payload or authority |
| `EffectObservation` | observation/effect/session/action binding, state/source/attempt, target/reported/evidence digests, failure/terminal/retry/simulation markers | dispatched/delivered/applied/verified remain distinct |
| `ApprovalPrompt` | approval/session/plan/node/action/effect IDs, plan/action/target/Context/policy bindings, risk/reason/prompt/digest and TTL | presentation/binding only; no response or grant authority |
| `UndoHandle` | undo/session/effect/source-observation/capability binding, verified/compensation/handle digests, Context version, state and TTL | eligibility only; undo is a new governed compensation operation |

`EffectContract` validates one active BOOLEAN/INTEGER/DECIMAL/TEXT scalar, a 15-minute Effect deadline, five
verification policies, compensation binding and risk enum parity with Plan V1. Its transition table enforces
`PROPOSED -> AUTHORIZED -> PREPARED -> DISPATCHED -> DELIVERED -> APPLIED -> VERIFIED`, bounded exact retry,
UNKNOWN reconciliation and terminal immutability. Applied/verified states require reported-value evidence;
simulation source and marker must agree.

Approval TTL is at most five minutes and resume requires exact authoritative plan/action/Context digest plus
Context version. Any mismatch yields `stale approval binding rejected`. Undo TTL is at most 15 minutes; request
requires AVAILABLE state, original plan/verified-observation binding and non-regressed Context version. Runtime
must still re-read current capability, Safety, Context and adapter activation before either operation.

The four-file AIDL identity is
`709828114422595f1889dad58e8e60daf4d5e4f98c962a6145f2f8a39b0c178d`; files are frozen in
`central-brain-sdk/aidl-api/effect-v1.sha256` and checked by
`tools/check_central_brain_android_effect_contract.sh`, which rechecks all earlier manifests. JVM tests and
cumulative instrumentation passed on the Android 13/API 33 ARM64 physical controller, then removed the temporary
test package.

Status: `effect_contract_v1_defined=true`,
`effect_parcel_physical_android13_arm64_verified=true`, `effect_runtime_service_published=false`,
`approval_response_service_published=false`, `undo_service_published=false`, `hardware_accessed=false`.
P1-W05 owns facade and Binder lifecycle, P1-W06 owns Room v4. Existing Governance V1 intentionally remains
without a grant method; P1-W04 is not Effect execution, vehicle/NPU access, Driver/HAL development or production
qualification.

## Stage 2 P1-W05 Session/Event Service Publication

P1-W05 does not change any frozen AIDL file or hash. It publishes `ICentralBrainSessionRuntime` and
`ICentralBrainSessionEvents` from the existing `CentralBrainRuntimeService` using two explicit Intent actions,
so the Manifest keeps exactly three signature-protected app Services. Legacy no-action binding remains the
production task Binder.

Every Session/Event transaction resolves Binder UID/package/current signer and enforces one of seven operation
capabilities before deriving owner fingerprint. Request DTOs cannot claim owner or authority. The public
`ScenarioClient` contains no Binder primitive; package-private transport owns Stub/Proxy, death recipients,
protocol negotiation and callback bridge.

P1-W05 initially used a process-local bounded registry. Reconnect recovery is snapshot -> cursor replay -> sequence
deduplication -> callback registration. Android 13/API 33 ARM64 real Binder instrumentation verified Service
rebind with `hardware_accessed=false`; P1-W06 later replaced the production registry with Room v4. Current status is
`session_runtime_service_published=true`, `event_runtime_service_published=true`,
`event_callback_service_published=true`, `session_runtime_persistence_wired=true`,
`session_runtime_process_death_rehydration=true`, `scenario_execution_enabled=false`.

## Stage 2 P1-W06 Room v4 And Frozen AIDL

P1-W06 changes no AIDL source, transaction number, interface version or hash. All task/diagnostic/Governance/
Session/Plan/Event/Effect checksum manifests are revalidated. Room entity/repository evolution is an internal
Runtime implementation detail behind the already frozen Session/Event V1 Binder.

`SessionRegistry` is the internal persistence boundary. `DurableSessionRegistry` now serves the production
Binder and stores owner-scoped Session snapshots plus immutable Event rows in Room v4. Binder callbacks are not
persisted; after process death, the unchanged SDK protocol reads the same V1 snapshot/events and registers a new
callback. This preserves old client wire compatibility while adding `session_runtime_process_death_rehydration=true`.

The v4 schema also defines Plan/Node/EffectObservation/Compensation tables, but no corresponding Binder Service or
new method is inferred. Existing Governance V1 still cannot grant approval, and `ApprovalPrompt`/`UndoHandle`
remain data-only contracts. P1-W06 is therefore not Plan/Effect execution or authority expansion.

## Stage 2 P1-W07 Aggregate Contract v2

Aggregate version 2 is a compatibility manifest over the frozen P1 V1 files, not a Stable AIDL interface version.
`central_brain_runtime_contract_v2.json` pins Session/Event interface versions and hashes, Plan/Effect manifests,
the seven published capabilities, SDK error categories, Room v4 identity and bounded payload/page/callback/latency
limits. `RuntimeContractV2` exposes the same compatibility constants to SDK tests; `ScenarioClient` aliases its
five lifecycle error codes from that class.

The target latency table for the currently published Session/Event app-layer Binder is:

| Method class | Target | Constraint |
| --- | --- | --- |
| protocol version/hash | <= 10 ms | constant-time; no I/O |
| `openSession` | <= 50 ms | validate and commit bounded Session + initial Event only |
| get/list/cancel/getEvents | <= 30 ms | owner-scoped bounded Room transaction/query |
| callback register/unregister | <= 50 ms | bounded replay/register; callback remains one-way |

Event V1's terminal `EventPage` still has empty `nextCursor`. The approved evolution is a separate Event V2 wire
contract with terminal resume cursor and explicit monotonic owner/session-scoped ACK, bounded retention and stale/
future cursor rejection. No V2 AIDL is published by P1-W07, so `event_v2_interface_published=false` and production
Event broker readiness remains false. Existing V1 transaction order/hash cannot be altered to implement this.

The cumulative SDK instrumentation verifies aggregate constants, V1 versions, bounds and publication flags on the
Android 13/API 33 ARM64 physical controller. It reports
`runtime_contract_v2_physical_android13_arm64_verified=true` and `hardware_accessed=false`; this is application
serialization evidence, not vehicle/NPU or target performance qualification.

## References

- Android app AIDL: <https://developer.android.com/develop/background-work/services/aidl>
- AIDL API guidelines: <https://source.android.com/docs/core/architecture/aidl/stable-aidl-apis>
- Stable AIDL and Soong freeze boundary: <https://source.android.com/docs/core/architecture/aidl/stable-aidl>
- Binder death recipient: <https://developer.android.com/reference/android/os/IBinder.DeathRecipient>
