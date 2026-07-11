# Central Brain Android AIDL Contract

Version: 1.0-draft
Date: 2026-07-12
Stage: R2B Service/SDK/API 33 integration complete; R2C death/race verification pending

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
- Runtime callback Binders and the SDK service Binder use `linkToDeath`. R2C remains responsible for process-death, explicit rebind and race instrumentation before R2 exits.
- `CentralBrainDiagnosticService` returns three deterministic structured records and clamps each page to `1..100`; it does not call the production Binder or any adapter.
- `CentralBrainClient` binds an explicit component and the SDK AAR contributes a narrow `<queries><package android:name="com.centralbrain.runtime"/></queries>` declaration for Android 11+ package visibility. `QUERY_ALL_PACKAGES` is forbidden.
- API 33 x86_64 validation passed production protocol negotiation, completion callback, duplicate cancel, shell denial by both signature permissions, Demo diagnostic-permission absence and diagnostic page probing. Debug probes require `android.permission.DUMP` and are absent from release APKs.
- Public app-SDK code throws `IllegalArgumentException` for malformed synchronous requests; it does not depend on hidden `android.os.ServiceSpecificException`.

## Cancellation And Death

- Client SDK links a `DeathRecipient` to the production service Binder. Service death fails all non-terminal callbacks with a stable `SERVICE_DIED` error. R2C must prove and finalize explicit rebind behavior.
- Runtime links a `DeathRecipient` to every remote callback Binder. Client death removes the callback and requests cancellation of tasks that have no durable detached-execution capability.
- `cancelTask` is idempotent. Repeated cancellation returns the same accepted/not-accepted outcome and never repeats a side effect.
- Callback `RemoteException` is treated as callback death; no callback retry loop may block a Binder thread.
- R2 must test service-process death, client-process death, callback death, duplicate cancel and cancel-vs-completion races.

## Versioning

V1 protocol identities:

- Production: version `1`, hash `55bed5957371d691221ba99032a07f7162f4699c6a0c6c3d1f5e1fcc5707f1f5`.
- Diagnostic: version `1`, hash `319aaf93eebc5b35b9466952bf97a05bd67465808ed2e3b2ff5bc6cc13342386`.

Because this is app-layer Gradle AIDL, `getProtocolVersion` and `getProtocolHash` are explicit project methods. The names `getInterfaceVersion` and `getInterfaceHash` are reserved by the AIDL compiler and are not used. Any V2 change must preserve V1 transaction order, append methods/fields only, update the protocol identity, and retain a compatibility test.

## Security And Hardware Boundary

R2B publishes app-layer production and diagnostic Binders behind separate signature permissions. This proves the independent APK boundary but is not yet the R3 trusted capability model: R3 will derive identity from Binder UID/package/signature, never from `AgentTaskRequest` fields.

No AIDL type includes a device node, fd, shared memory, vendor handle, PCIe/NPU object, vehicle bus frame, camera/audio buffer, Safety Runtime token or virtualization control. `hardware_accessed=false`, `driver_development_triggered=false`, and `virtualization_development_triggered=false` remain mandatory.

## References

- Android app AIDL: <https://developer.android.com/develop/background-work/services/aidl>
- AIDL API guidelines: <https://source.android.com/docs/core/architecture/aidl/stable-aidl-apis>
- Stable AIDL and Soong freeze boundary: <https://source.android.com/docs/core/architecture/aidl/stable-aidl>
- Binder death recipient: <https://developer.android.com/reference/android/os/IBinder.DeathRecipient>
