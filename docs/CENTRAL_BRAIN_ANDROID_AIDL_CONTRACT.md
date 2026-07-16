# Central Brain Android AIDL Contract

Version: 1.1-draft
Date: 2026-07-17
Stage: R2 complete / Stage 2 P1-W01 Session contract `contract_defined`

## Scope

This document defines the Android 13 user-space Protocol Binding between the Central Brain SDK AAR and Runtime Service APK. The architecture diagram remains the requirement baseline. This contract implements only the Binder boundary owned by `AI SDK -> Protocol Binding -> Runtime & Governance/AIOS Kernel`; it does not add a new architecture layer.

Req IDs: `XSC-001`, `XSC-004`, `XSC-005`, `XSC-006`, `NV-F-001`, `NV-G-003`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-003`, `DEL-004`.

R2A delivered Gradle application structured AIDL in `central-brain-sdk`; R2B publishes and consumes that frozen V1 contract from separate application APKs. It is not VINTF stable AIDL: the project cannot add a Soong `aidl_interface`, freeze platform API under `aidl_api`, or modify vendor/system build files because the target Android SDK and system image are prebuilt. This accepted temporary limitation is tracked under `DEV-018` and `ISSUE-021`.

## Binder Surfaces

| Surface | Package/interface | Responsibility | Forbidden content |
| --- | --- | --- | --- |
| Production | `com.centralbrain.sdk.production.ICentralBrainRuntime` | Typed agent-task submit, cancel, small status query | JSON, readiness rollups, audit dumps, hardware evidence |
| Callback | `com.centralbrain.sdk.production.ICentralBrainTaskCallback` | Oneway progress, completion and failure notifications | Blocking work, request dispatch, large payloads |
| Diagnostic | `com.centralbrain.sdk.diagnostics.ICentralBrainDiagnostics` | Bounded, read-only, cursor-paged diagnostics | Task submit/cancel, state mutation, hardware activation |
| Session V1 | `com.centralbrain.sdk.session.ICentralBrainSessionRuntime` | Stage 2 open/get/list/cancel contract | Runtime publication, callback/event stream, vehicle/NPU dispatch |

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

## References

- Android app AIDL: <https://developer.android.com/develop/background-work/services/aidl>
- AIDL API guidelines: <https://source.android.com/docs/core/architecture/aidl/stable-aidl-apis>
- Stable AIDL and Soong freeze boundary: <https://source.android.com/docs/core/architecture/aidl/stable-aidl>
- Binder death recipient: <https://developer.android.com/reference/android/os/IBinder.DeathRecipient>
