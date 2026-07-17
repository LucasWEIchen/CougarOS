# 车载中央大脑接口设计

版本：3.4

日期：2026-07-17

状态：Android 13 实际工程接口基线

## 接口设计原则

1. 架构图 Req ID 是最高需求基线。
2. 应用只通过 Java SDK 和 typed AIDL 调用 Runtime，不存在 REST/Python fallback。
3. Caller identity、signer、capability 和 Policy 由 Runtime 可信解析，请求体不能自报 authority。
4. 所有 payload、callback、queue、deadline、retry 和 retention 必须有界。
5. 车辆副作用必须经过 durable Effect、Safety、Approval、verify/reconcile。
6. Model Provider 不得直接调用 Effect；Vendor NPU/vehicle adapter 未激活时返回 unavailable。
7. AIDL v1 保持 hash/version；Stage 2 新 DTO 使用独立 versioned surface。
8. C ABI 只承担稳定 native/provider 边界，不拥有 Binder、Policy、Room 或 UI。
9. `production_ready=false` 和 `target_hardware_validated=false` 只能由独立目标证据关闭。

Req ID：`APP-004`、`XSC-001..006`、`FW-U-001..008`、`FW-S-001..006`、
`NV-F-001..012`、`NV-G-001..007`、`NV-P-002`、`KH-003/006/007`、
`DEL-001/003/004/005`。

## 当前接口族

| 接口族 | 权威实现 | 状态 |
| --- | --- | --- |
| Runtime task | `ICentralBrainRuntime` / `CentralBrainClient` | Android integrated |
| Governance | `ICentralBrainGovernance` / `CentralBrainGovernanceClient` | Android integrated, grant authority absent |
| Diagnostics | `ICentralBrainDiagnostics` | Android integrated, read-only |
| Durable state | Room repositories | software baseline |
| Model | `ModelProvider` / Scheduler / readiness | contract and test-only implementation |
| Effect | `EffectAdapter` / material source / activation gate | contract, production blocked |
| Native | `central_brain_native.h` / JNI / Java wrapper | lifecycle ABI integrated |
| Session/Plan/Event contract | Stage 2 P1-W01..P1-W03 | contract defined; services not published |
| Effect/Approval contract | Stage 2 P1-W04 | contract defined; service/grant/undo not published |
| Vehicle/NPU adapter | AAOS/Vendor published API/ABI | external blocked |

早期通用 JSON envelope、HTTP endpoint、第一阶段 JSON Binder 和 Linux binding 已退役。下列章节保留
Android R2-B5 已实现接口的源码级合同和验收边界。

## Android R2 Typed AIDL Contract

The Android product runtime no longer extends the legacy String/JSON Binder surface for new business operations. R2 uses `ICentralBrainRuntime` for typed task control, `ICentralBrainTaskCallback` for oneway async results, and `ICentralBrainDiagnostics` for bounded read-only cursor pages. Full V1 types, method latency, cancellation, death handling, version/hash rules and the no-hardware boundary are defined in `CENTRAL_BRAIN_ANDROID_AIDL_CONTRACT.md`.

This is Gradle application structured AIDL because the project cannot modify the prebuilt vendor/AOSP Soong build. It must not be labeled VINTF stable AIDL. Req IDs: `XSC-001`, `XSC-004`, `XSC-005`, `XSC-006`, `NV-F-001`, `NV-G-003`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-003`, `DEL-004`; deviation/issue tracking: `DEV-018`, `ISSUE-021`.

R2B published `ICentralBrainRuntime` and `ICentralBrainDiagnostics` from separate signature-protected Service components. `CentralBrainClient` uses an explicit component, narrow package visibility, executor-dispatched callbacks and a service `DeathRecipient`; Runtime task work runs off Binder threads and diagnostic pages remain bounded/read-only. Its API 33 evidence covered protocol negotiation, completion, duplicate cancel, permission denial and diagnostic paging; process-death, rebind and cancel-completion race evidence was completed in R2C.

R2C completes that lifecycle contract: death recipients are scoped to exact Binder instances, active callbacks fail once with `ERROR_SERVICE_DIED`, `reconnect()` explicitly unbinds/rebinds, and post-terminal updates are suppressed. API 33 instrumentation covers Runtime force-stop/recovery, duplicate disconnect suppression, 15-task cancel-completion races and separate client-process death. The typed Android Protocol Binding is `android_integrated`; DEV-018/ISSUE-021 now track app-local AIDL versus target VINTF/system ownership, not a retained JSON/HTTP compatibility layer.

## Android R3A Job Supervisor And Trusted Identity Interfaces

R3A keeps the frozen V1 AIDL unchanged and adds internal AIOS Kernel/Runtime & Governance interfaces:

| Interface | Input | Output/failure | Owner |
| --- | --- | --- | --- |
| `JobSupervisor.admit` | Runtime task ID, resolved caller snapshot, initial message | accepted snapshot + pressure-evicted terminal IDs; rejects duplicate ID, unresolved owner or full active registry | AIOS Kernel `NV-F-001` |
| `JobSupervisor.transition` | task ID, target state, monotonic progress, message | applied/latest snapshot; rejects illegal transition or progress regression | AIOS Kernel/Lifecycle `NV-G-006` |
| `JobSupervisor.cancelOwned` | task ID, trusted current caller, reason message | applied, already-cancelled, terminal, or not-found/not-owner without existence disclosure | Permission/Policy `FW-U-007`, `NV-G-005` |
| `JobSupervisor.findOwned` | task ID, trusted current caller | snapshot or null for both missing and non-owner | Runtime & Governance `XSC-005` |
| `JobSupervisor.pruneExpired` | elapsed realtime | terminal task IDs removed after retention; never removes active work | Lifecycle/Audit `NV-G-006/007` |
| `JobSupervisor.markTerminalDeliverySettled` | terminal task ID after completion/failure callback attempt or confirmed callback death | makes the terminal record eligible for later retention/pressure eviction; rejects non-terminal settlement | Lifecycle/Audit `NV-G-006/007` |
| `AndroidCallerIdentityResolver.resolveCallingIdentity` | current Binder transaction | UID, Android user serial, sorted package/current-signer SHA-256 evidence; unresolved result fails closed | Protocol Binding/Policy `XSC-006`, `NV-P-002` |

The package and each current signer remain paired in `CallerIdentitySnapshot.PackageIdentity`; a flat package/digest cross-product is forbidden. No request field participates in identity or authorization. R3B consumes this snapshot through a package+signer capability policy and default-deny unknown clients; R3C will add trusted Safety/Vehicle State and approval inputs.

## Android R3B Capability Policy Interfaces

| Interface | Input | Output/failure | Rule |
| --- | --- | --- | --- |
| `AndroidCapabilityPolicyLoader.load` | APK XML + Runtime own trusted identity | immutable policy or startup failure | root must be V1/default deny; only literal package and `runtime-current` signer rules |
| `CallerCapabilityPolicy.evaluate` | complete caller snapshot + capability enum | allow or stable deny reason | exact package/current-signer pair; no request assertions or signer intersection |
| `resolveAuthorizedCaller` | active Binder caller + required production/diagnostic capability | trusted caller snapshot or `SecurityException` | runs before request parsing/task lookup and writes bounded denial audit |

Production capability IDs are `runtime.protocol.read`, `runtime.task.submit`, `runtime.task.status.own`, and `runtime.task.cancel.own`; diagnostics use `runtime.diagnostics.read`. For shared UID identities, correctly signed configured packages contribute capabilities to the UID principal; a signer mismatch on any configured package fails closed. Stable denial reasons are `IDENTITY_UNRESOLVED`, `PACKAGE_NOT_CONFIGURED`, `CURRENT_SIGNER_MISMATCH`, and `CAPABILITY_NOT_GRANTED`.

`policy-probe` is a separate same-signer APK used only to prove that manifest signature permission is not treated as capability authorization. It binds both Services successfully but is absent from the policy, so every production method and diagnostic read is denied on API 33. It is not an AI SDK or Runtime delivery module and adds no architecture layer.

## Android R3C1 Action Governance Core Interfaces

R3C1 keeps the frozen task/diagnostic AIDL unchanged and defines the internal Governance core that R3C2 publishes through a separate typed Binder.

| Interface | Trusted input | Output/failure | Boundary |
| --- | --- | --- | --- |
| `SafetyVehicleStateProvider.currentSnapshot` | Runtime-owned provider only | immutable source/revision/Safety/Motion/driver snapshot | no Binder payload state; current stub is not production trusted |
| `ActionGovernancePolicy.classify` | exact stable Action ID | one of five risk classes or `UNKNOWN` | caller cannot submit or lower a risk class |
| `ActionGovernancePolicy.evaluate` | Action ID + provider snapshot | `ALLOW_POLICY_ONLY`, `APPROVAL_REQUIRED`, or `DENY` with stable reason | always `dispatchAllowed=false` |
| `InMemoryApprovalRegistry.request` | high-risk approval-required decision + trusted caller snapshot | owner-bound `PENDING` record or capacity rejection | no approval grant and no durable recovery |
| `InMemoryApprovalRegistry.findOwned` | approval ID + trusted current caller | snapshot or null for missing/non-owner | no existence disclosure across owners |
| `InMemoryApprovalRegistry.cancelOwned` | approval ID + trusted current caller | idempotent cancel or false | expired/missing/non-owner cannot be cancelled |

The exact Action catalog is `vehicle.state.read`, `cabin.temperature.set`, `driver.display.video.play`, `vehicle.diagnostics.write`, and `system.ota.install`. Read-only remains visible even during emergency state; comfort is policy-only when Safety/Motion are known; driver-distraction, diagnostic-write and OTA require parked/normal/driver-available state before a pending approval may be created. Moving high-risk actions are denied before approval creation.

The current provider uses `RUNTIME_OWNED_STUB` with `hardwareBacked=false` and `productionTrusted=false`. The current registry uses bounded process memory with `supportsApprovalGrant=false` and `isDurable=false`. These are deliberate R3C1 limits, not approval completion or target Safety evidence. Req IDs: `FW-U-004`, `FW-U-007`, `FW-S-005`, `XSC-005`, `XSC-006`, `NV-G-005`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-004`.

## Android R3C2 Typed Governance Binder Interfaces

The Android product path publishes `ICentralBrainGovernance` as an independent production control plane. It is not part of the legacy JSON gateway and does not change task/diagnostic V1 transaction order.

| Binder method | Required inner capability | Result | Side-effect boundary |
| --- | --- | --- | --- |
| `getProtocolVersion/getProtocolHash` | `governance.protocol.read` | Governance V1 identity | no policy or approval mutation |
| `evaluateAction(ActionRequest)` | `governance.action.evaluate` | typed risk/outcome/reason + Runtime state-source metadata | no approval creation and no dispatch |
| `requestApproval(ActionRequest)` | `governance.approval.request` | owner-bound pending `ApprovalHandle` | only high-risk approval-required decisions; no grant |
| `getApprovalStatus(ApprovalHandle)` | `governance.approval.status.own` | owner status or `UNKNOWN` | missing/non-owner indistinguishable |
| `cancelApproval(ApprovalHandle)` | `governance.approval.cancel.own` | idempotent true for owner-cancelled, false otherwise | no action dispatch |

Outer access uses `com.centralbrain.permission.BIND_GOVERNANCE` (`signature`). Inner policy still requires exact package plus complete current signer set, so sharing the signer alone does not authorize a package. `CentralBrainGovernanceClient` binds the explicit Service component, negotiates version/hash and handles Binder death; it never accepts a caller-supplied identity or state provider.

`ActionRequest` has exactly `schemaVersion`, `clientRequestId`, `actionId`, and `idempotencyKey`. `ActionDecision` exposes derived risk/outcome and Runtime state-source metadata. `ApprovalHandle/ApprovalStatus` expose bounded pending/cancelled/expired state; status always reports grant/durable/dispatch false. Governance V1 is frozen by `central-brain-sdk/aidl-api/governance-v1.sha256` and intentionally has no approval resolution method.

API 33 allowed-client evidence is emitted by `tools/install_central_brain_android_runtime.sh`; same-signer unknown-client denial is emitted by `tools/test_central_brain_android_capability_policy.sh`. Req IDs: `FW-U-004`, `FW-U-007`, `FW-S-005`, `XSC-005`, `XSC-006`, `NV-G-005`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`, `DEL-004`.

## Android R4A Durable Schema Interfaces

R4A establishes the persistence ownership boundary without changing production AIDL or wiring a dispatcher.

| Room object/table | Key and integrity rule | Intended R4 owner |
| --- | --- | --- |
| `runtime_session` | session PK; unique owner fingerprint + session key | session lifecycle |
| `runtime_task` | task PK; unique owner fingerprint + idempotency key | Job Supervisor durable mirror |
| `task_checkpoint` | checkpoint PK; unique task + sequence; task FK cascade | task step/checkpoint recovery |
| `pending_effect` | effect PK; global unique idempotency key; task FK cascade | prepare-before-side-effect boundary |
| `effect_outbox` | outbox PK; one row per effect; effect FK cascade | retryable delivery intent |
| `approval_request` | approval PK; unique owner fingerprint + idempotency key | durable pending/decision lifecycle |
| `audit_event` | auto sequence; unique event ID | ordered durable governance audit |
| `event_cursor` | cursor PK; unique owner fingerprint + topic | replay/cursor recovery |

`CentralBrainDatabase.open` configures WAL and only registers explicit `MIGRATION_1_2`; destructive fallback is forbidden. `RuntimeStateDao` currently exposes migration reads and insert primitives. It is not yet the production repository API, and production Services do not open it in R4A.

The debug migration probe creates a separate v1 database, inserts task/approval rows, migrates to v2, checks the eight-table schema and WAL, then deletes only the probe database. Payload-bearing columns are digests, not raw payload. R4B must add transactions and repository invariants; R4C must add restart/outbox recovery. Req IDs: `FW-U-004`, `NV-F-001`, `NV-G-006`, `NV-G-007`, `XSC-005`, `XSC-006`, `DEL-001`, `DEL-004`.
## Android R4B1 Durable Task Admission

R4B1 adds an internal Java repository boundary; it does not change frozen task/diagnostic or Governance AIDL.

| Interface | Input | Output/failure | Transactional rule |
| --- | --- | --- | --- |
| `DurablePrincipalFingerprint.from` | resolved trusted caller snapshot | lowercase SHA-256 owner fingerprint; unresolved identity throws | canonical Android user + package/current-signer pairs; UID excluded |
| `DurableTaskRepository.admit` | owner fingerprint, session/client request/idempotency metadata, payload digest | `CREATED` or `REPLAYED` admission; mismatched replay throws `IdempotencyConflictException` | owner/key lookup + task insert + `TASK_ACCEPTED` audit insert in one Room transaction |
| `RuntimeStateDao.findTaskByOwnerAndIdempotency` | owner fingerprint + idempotency key | matching mutable entity or null, internal only | backed by `index_runtime_task_owner_idempotency` unique index |

The repository never accepts a raw utterance, caller-supplied identity, risk classification or permission assertion. `payloadDigest` is a lowercase SHA-256 placeholder; production keying/HMAC policy remains open under ISSUE-022. R4B1 is not referenced by production Services, does not write checkpoint/effect/outbox rows, and cannot dispatch an Action.

## Android R4B2 Durable Runtime Wiring

Frozen `ICentralBrainRuntime` V1 is unchanged. Durability is an internal Service/repository contract.

| Call/path | Durable behavior | Callback/recovery behavior |
| --- | --- | --- |
| `submitAgentTask` new key | trusted owner + request digest; task/ACCEPTED checkpoint/audit commit before handle | schedules deterministic stub only after durable admission |
| `submitAgentTask` exact live replay | returns original handle; no new task/checkpoint/audit; admission-to-live-map publication is serialized | same callback Binder is deduplicated; up to four observer Binders receive current and terminal events |
| `submitAgentTask` existing but unrecovered | returns original handle | emits durable status then retryable `ERROR_INTERNAL`; no execution until R4C |
| RUNNING/terminal transition | task update + next checkpoint + transition audit in one transaction | Job Supervisor advances after commit |
| terminal callback settlement | task settled flag + settlement audit, idempotent | executed after callback attempts; failed persistence remains pending |
| `getTaskStatus` | live owner snapshot first, then owner-fingerprint Room lookup | durable fallback message states recovery is pending |

`DurableDigest` length-frames every UTF-8 field and domain-separates request/checkpoint/settlement hashes. Deadline policy is transactional: exact existing replay is returned even when creation is no longer allowed; a new expired request throws before any task row is inserted. R4B2 does not expose database handles through AIDL and does not access pending-effect/outbox dispatch.

## Android R4B3 Durable Approval

Frozen `ICentralBrainGovernance` V1 remains unchanged; the implementation backing changes from process-local registry to Room.

| Repository call | Result | Transaction rule |
| --- | --- | --- |
| `request(owner,key,action,risk,reason,creationAllowed)` | `CREATED` or `REPLAYED`; conflict/rejection/capacity exception | expire due rows, lookup owner/key, then optional PENDING insert + request audit |
| `findOwned(approvalId,owner)` | durable snapshot or null | expire due rows first; non-owner is indistinguishable from missing |
| `cancelOwned(approvalId,owner)` | `APPLIED`, `REPLAYED`, `NOT_FOUND`, `NOT_PENDING` | one PENDING→CANCELLED update + one cancel audit |

The persisted equivalence key is owner + idempotency key + exact Action ID. `clientRequestId` remains tracing metadata and does not create another approval for the same operation key. Stored risk/reason are Runtime-derived originals. Replaying an existing approval under changed policy returns its current PENDING/CANCELLED/EXPIRED state but cannot grant or dispatch it. Wall timestamps are converted to elapsed-realtime fields for AIDL responses; trusted clock and reboot/direct-boot qualification remain open.

## Android R4C1 Fail-Closed Restart Reconciliation

Frozen production AIDL remains unchanged. Restart behavior is an internal Runtime/Room contract.

| Interface/path | Behavior | Boundary |
| --- | --- | --- |
| `RuntimeStateDao.findTasksNeedingRestartReconciliation` | selects ACCEPTED/RUNNING and COMPLETED with unsettled terminal delivery | internal DAO only; owner-scoped Binder authorization remains at Service entry |
| `DurableTaskRepository.reconcileInterruptedTasks` | atomically changes each selected task to FAILED and inserts the next checkpoint plus restart audit | idempotent second pass; no raw payload reconstruction |
| Runtime startup Future barrier | runs Room work on the single task executor; task submit/cancel/status await completion | no main-thread database transaction and no admission/reconciliation race |
| exact replay without live record | returns existing handle, sends FAILED update then retryable `ERROR_INTERNAL`, then attempts durable settlement | no task execution resume, effect creation, outbox delivery or hardware dispatch |
| SDK `SerialExecutor` per callback | preserves update-before-terminal delivery over a concurrent caller executor | terminal remains exactly once and updates are not reordered behind it |

A COMPLETED task with an unsettled callback is conservatively changed to FAILED because R4C1 stores no result payload that can be proven equivalent after process loss. This is an explicit availability tradeoff in favor of no false-success/no duplicate-effect semantics. R4C2 owns pending-effect/outbox state; real resumable task execution requires an approved durable input/result format and is not implied by `restart_reconciliation_enabled=true`.

## Android R4C2A Effect Prepare And Claim

`DurableEffectRepository` is an internal Java/Room boundary and is not referenced by a production Service in this increment.

| Call | Transactional result | Explicit non-result |
| --- | --- | --- |
| `prepare(owner,task,key,type,action,payloadDigest,destination,envelopeDigest)` | owner/key digest lookup; exact replay or PREPARED effect + PENDING outbox + audit | no adapter lookup or dispatch |
| `claimNext(destination)` | due/eligible row moves PREPARED/PENDING→IN_FLIGHT/IN_FLIGHT, attempt increments, audit appends | returns digest metadata only; does not call destination |
| `reconcileInterruptedClaims()` | interrupted pairs return to PREPARED/PENDING at current `not_before`, one recovery audit each | startup/offline reconciliation only; does not assert whether an external side effect occurred |
| `findOwned(effectId,owner)` | owner-isolated effect/outbox snapshot | no cross-owner existence disclosure |

The stored `idempotency_key` is a domain-separated owner+caller-key digest, called the idempotency token in repository snapshots. A claim carries IDs, owner token, type/action, payload/envelope digests, destination, state and attempt count; no raw command exists to dispatch. Route pairs are fixed to UIB Action, SOA Operation and Skill. Because an IN_FLIGHT crash is ambiguous once a real adapter exists, requeue alone provides at-least-once infrastructure, not exactly-once execution; adapter idempotency/status contracts remain a hard activation gate.

## Android R4C2B Effect Retry And Terminal States

The internal Room API closes local effect lifecycle semantics while keeping all destination adapters disconnected.

| Call | Required current state | Transactional result |
| --- | --- | --- |
| `recordSuccess(effect,outbox,owner,expectedAttempt,resultDigest)` | matching IN_FLIGHT pair and attempt | APPLIED/DELIVERED + `EFFECT_APPLIED`; exact replay returns `REPLAYED` |
| `scheduleRetry(effect,outbox,owner,expectedAttempt,delay,failureDigest)` | matching non-final IN_FLIGHT pair | PREPARED/PENDING + bounded `not_before` + `EFFECT_RETRY_SCHEDULED` |
| `deadLetter(effect,outbox,owner,expectedAttempt,failureDigest)` | matching IN_FLIGHT pair | FAILED/DEAD_LETTER + `EFFECT_DEAD_LETTERED` |
| `cancelPrepared(effect,outbox,owner,expectedAttempt,reasonDigest)` | matching PREPARED/PENDING pair and attempt | CANCELLED/CANCELLED + `EFFECT_CANCELLED` |
| `reconcileInterruptedClaims()` | IN_FLIGHT pairs after reopen | attempts remaining: requeue; exhausted: FAILED/DEAD_LETTER + `EFFECT_CLAIM_EXHAUSTED` |

Result, failure and cancellation content enters Room only as lowercase SHA-256 digests. Replay validation binds IDs, expected attempt and operation-specific input; retry also binds the requested delay and persisted not-before timestamp. Default `maxAttempts=3`, constructor bounds are 1..100, and delay bounds are 0..24 hours.

`EFFECT_CLAIM_EXHAUSTED` is a local fail-closed outcome for an unknown final-attempt result. It does not authorize blind re-delivery and does not state that the destination never applied the operation. No dispatcher, adapter call or production Service wiring exists in R4C2B; R4C3 must supply destination idempotency/status interfaces and crash-point evidence first.

## Android R4C3A Effect Adapter Contract

`EffectAdapter` is an internal Java contract, not a Binder, HAL or vendor implementation.

| Interface | Input | Result and invariant |
| --- | --- | --- |
| `descriptor()` | none | adapter ID/destination, `TOKEN_DEDUPLICATED`, duplicate returns original, APPLIED status returns original evidence, `LINEARIZABLE` status, bounded operation timeout |
| `apply(Invocation)` | effect/outbox IDs, persisted token, route/action, attempt, bounded transient canonical payload/envelope | typed apply state + echoed token + evidence digest; duplicate token must not repeat the side effect |
| `queryStatus(token)` | persisted idempotency token | NOT_APPLIED/APPLIED/REJECTED/UNKNOWN + echoed token + evidence digest; query transport failure is a distinct exception |
| `EffectAdapterContract.requireSafe` | adapter + expected destination | rejects route mismatch, non-token idempotency, changed duplicate result or non-linearizable status |
| `EffectStatusReconciler.reconcile` | durable IN_FLIGHT snapshot + safe adapter + retry delay | queries status only and atomically calls repository success/retry/dead-letter, or defers with no mutation |

APPLIED maps to APPLIED/DELIVERED. NOT_APPLIED maps to PREPARED/PENDING only while attempts remain and otherwise to FAILED/DEAD_LETTER. REJECTED/UNKNOWN map to FAILED/DEAD_LETTER. Adapter unavailability keeps IN_FLIGHT unchanged so a transport failure is not mistaken for destination state.

The debug fixture retains token status only in process memory and receives canonical bytes directly from the probe. It validates the algorithm across Room close/reopen but does not solve command-material recovery after process death. No production Service references these interfaces in R4C3A; R4C3B must bind any retry-capable activation to a trusted durable material source whose confidentiality, digest verification and lifecycle are explicit.

## Android R4C3B Effect Material Activation Gate

| Interface | Input | Output/failure |
| --- | --- | --- |
| `EffectMaterialSource.descriptor` | none | availability, assurance, restart durability, at-rest encryption, effect integrity binding, deletion and retention metadata |
| `EffectMaterialSource.resolve` | durable claim | canonical payload/envelope + effect ID + source revision, or `MaterialUnavailableException` |
| `EmptyEffectMaterialSource` | any claim | always unavailable; current main-source product boundary |
| `EffectDeliveryActivationGate.evaluate` | adapter, material source, expected destination | ordered blocker list; no material resolution, adapter status query or apply |
| `EffectDeliveryActivationGate.resolveInvocation` | blocker-free adapter/source + IN_FLIGHT claim | digest-verified `EffectAdapter.Invocation`, or activation/material/integrity failure |

Stable blockers cover missing/unsafe adapter; missing/invalid/empty/non-production source; non-durable, unencrypted or integrity-unbound material; missing delete support; and invalid retention. A blocker-free result is only a code-level necessary condition: target evidence must still bind the actual signed provider, key owner, storage policy and vendor adapter conformance.

The current production configuration has no adapter and uses the empty source, so it remains blocked. The debug synthetic source can exercise the positive branch and resolve bytes after a Room reopen, but it does not survive process death and is never a release implementation. R4C3B does not add payload columns or blobs to Room.

## Android R4C3C Production Activation Snapshot

`EffectDeliveryActivationSnapshot.current()` is the only production visibility object. It is an immutable singleton derived from `EffectDeliveryActivationGate.evaluate(null, EmptyEffectMaterialSource, UIB_ACTION)`. It exposes activation, adapter/material/apply/status booleans, source ID, ordered blockers and a bounded diagnostic detail string.

`CentralBrainRuntimeService` reads the snapshot for startup logging and protected Service dumpsys. `CentralBrainDiagnosticService` reads the same snapshot for record ID `effect-delivery-activation`, summary `blocked`, sequence 4 in the existing cursor-paged diagnostic interface. No new AIDL transaction or Parcelable is added.

This interface is observation-only. Neither Service gets an adapter or material-source handle from the snapshot; neither can call apply/query/resolve or claim an outbox. Current blocker visibility therefore cannot be used as an activation command.

## Android R5A1 Model Provider Contract

映射 Req ID：`APP-004`、`XSC-001`、`XSC-004`、`NV-F-011`、`NV-G-004`、`NV-G-006`、`DEL-001`、`DEL-004`、`DEL-005`。

| 接口/类型 | 调用方 -> 实现方 | 语义 | 当前实现 |
| --- | --- | --- | --- |
| `ModelProvider.descriptor()` | Model Router -> Provider | backend/assurance/fallback/operation/concurrency immutable contract | 类型已实现；无 provider instance |
| `snapshot()` | Scheduler/Diagnostics -> Provider | lifecycle、health、loaded/active/queued、hardware evidence | profile snapshot only |
| `warmup(ModelSpec)` | Model Router -> Provider | model id/version/artifact digest lifecycle transition | contract only |
| `infer(InferenceRequest, StreamObserver)` | Scheduler -> Provider | deadline-bound async inference; chunks transient and <=64 KiB | contract only |
| `cancel(requestId, reason)` | Scheduler -> Provider | cancelled/pending-ack/terminal/not-found/unsupported | contract only |
| `metrics()` / `lastFault()` | Governance/Diagnostics -> Provider | bounded counters and fault/isolation status | contract only |
| `close()` | Runtime lifecycle -> Provider | stop provider and reject new work | contract only |

`deterministic.stub` 为 TEST_ONLY、COLD、1 个声明 slot，所有 operation 仅表示 R5B contract，当前未配置/未路由。`vendor.npu.empty` 为 EMPTY/UNAVAILABLE、0 slot，只能暴露 unavailable health/fault metadata。Test/local-development provider 不能声明 production/hardware；EMPTY 不能声明 inference/fallback。R5A2 Scheduler 形成排队/准入调用关系，R5B 只允许 test Router 调用 deterministic provider。

## Android R5A2 Inference Resource Scheduler

| 接口/类型 | 输入 | 输出/约束 |
| --- | --- | --- |
| `TrustedSubmission.fromRuntimePolicy` | request/owner/model/provider、effective priority、elapsed task deadline、queue wait | 唯一 priority 构造入口；不接受 Binder payload priority |
| `admit` | trusted submission | ADMITTED/REPLAYED/duplicate/deadline/timeout/global-owner quota/route unavailable |
| `claimNext` | 无 | 按 priority -> queue deadline -> FIFO -> request ID 选择，返回 lease；同时返回 deadline sweep report |
| `cancelOwned` | request ID + durable owner fingerprint | queued 本地移除；running 返回 provider cancellation directive；非 owner 不泄露 |
| `sweepDeadlines` | injected elapsed-realtime now | queued expiry、running cancellation directive、cancel-unsupported blocker |
| `settle` | request ID + lease ID + provider terminal outcome | stale lease/invalid state/cancel race 检查，释放 slot 并返回 local terminal mapping |
| `RouteTarget.fromProfile` | R5A1 immutable profile | 当前两个 profile 均 disabled |
| `RouteTarget.forContractTest` | `test.*` ID、slot、cancel capability | 仅 unit/debug contract route，不是 production activation |

`CANCEL_REQUESTED` 继续占用 running/global/owner/provider slot，直到 provider acknowledgement；Scheduler 不调用 `ModelProvider.infer/cancel`。Deadline cancellation 的 local terminal 固定为 DEADLINE_EXCEEDED；owner cancellation 后的迟到 COMPLETED 只作为资源释放 acknowledgement，本地映射为 CANCELLED 且不接受输出。Scheduler 只拥有 active resource admission，terminal task/checkpoint/audit 仍由 Job Supervisor 和 Room repository 持有。

## Android R5B1 Deterministic Stub Provider

| 接口 | 行为 | 失败/边界 |
| --- | --- | --- |
| `warmup(ModelSpec)` | allowlisted model ID/version/artifact digest，COLD->READY | mismatch、closed、fault-isolated 拒绝 |
| `infer(request, observer)` | injected executor 两阶段执行；stream sequence 1/2；terminal digest | not-ready、deadline、duplicate、slot-full 拒绝 |
| `cancel(requestId, reason)` | active 标记 cancel，返回 `PENDING_PROVIDER_ACK` | missing/terminal/unsupported typed state |
| `snapshot()` | lifecycle/health/loaded=0..1/active=0..1，hardware=false | 无 queue；Scheduler 单独拥有排队 |
| `metrics()` | accepted/completed/cancelled/failed bounded counters | terminal sum 不得超过 accepted |
| `lastFault()` | NONE/retryable/terminal/fault-isolated code | 仅 test fault injection |
| `close()` | active 终结为 CANCELLED，进入 STOPPED | 重复 close 幂等，禁止 reuse |

Provider output 只由 `modelId + inputDigest` 生成 synthetic bytes；不接收真实 utterance 或 buffer。Terminal history 上限 64。R5B1 不创建 Model Router，不接 Scheduler lease；class availability 与 profile activation 分离，当前 profile 仍 implementation/routing false。

## Android R5B2 Test-Only Model Router

| 接口/类型 | 调用关系 | 输出/约束 |
| --- | --- | --- |
| `createForContractTest(scheduler, provider)` | debug/test composition root -> Router | 只接受 deterministic TEST_ONLY provider；无 production factory |
| `routeTargetForContractTest(provider)` | Router -> Scheduler route catalog | 固定 `test.deterministic.stub`，slot/cancel 来自受检 descriptor |
| `submit(TrustedRouteRequest, observer)` | trusted test caller -> Scheduler -> Provider | typed ADMITTED/REPLAYED/REJECTED/PROVIDER_UNAVAILABLE；claim 后才 infer |
| `pump()` | Router -> Scheduler `claimNext` -> Provider `infer` | lease/request/provider identity 全匹配才计为 dispatched |
| `cancelOwned(requestId, owner, reason)` | caller -> Scheduler directive -> Provider cancel | queued 本地 terminal；running provider ack 后归一化 terminal |
| `tick()` | elapsed clock -> Scheduler sweep -> Provider cancel | queue expiry、running deadline、unsupported cancel 失败关闭 |
| `snapshot()` | debug diagnostics -> Router | bounded counters、`NO_FALLBACK`、production/hardware false |

Provider chunks 仅在 matching active lease 下转发；terminal 先由 Scheduler settle，再向原 observer 交付且最多一次。Exact replay 不替换 observer；changed duplicate 拒绝。Router 不写 Room、不拥有最终 durable task 状态，也不允许 local-development/Vendor fallback。Production Runtime/Governance 不引用该 class，current profile 仍 configuration/routing false。

## Android R5C1 Model Runtime Readiness Snapshot

| 接口/字段 | 数据来源 | 约束 |
| --- | --- | --- |
| `ModelRuntimeReadinessSnapshot.current()` | immutable `ModelProviderProfiles` | singleton；当前配置若可激活则初始化失败 |
| `diagnosticDetail()` | profile metadata + fixed production wiring flags | bounded key/value detail；无 model material、caller input 或 runtime execution |
| Runtime startup log | shared snapshot | configuration/lifecycle/health/detail/blocker visibility |
| Runtime protected `dump()` | shared snapshot | 与 startup/Diagnostic 值一致；shell DUMP 只读 |
| Diagnostic record `model-runtime-readiness` | shared snapshot | 现有 paged AIDL、signature permission 和 capability policy，不增接口 |

Deterministic Stub 报告 TEST_ONLY/COLD/HEALTHY/`STUB_IMPLEMENTATION_NOT_WIRED`；Vendor NPU 报告 EMPTY/UNAVAILABLE/UNAVAILABLE/`VENDOR_RUNTIME_UNAVAILABLE`。`HEALTHY` 只属于 immutable Stub descriptor/profile contract，不代表 provider instance。Snapshot 不引用 executable Router/Scheduler/provider class，不执行 warmup/infer/cancel/dispatch，production activation 固定 false。

## Android R5D1 Target Deployment Evidence Contract

| Evidence field | Source | Acceptance meaning |
| --- | --- | --- |
| `android_api`, `device_abi`, `device_fingerprint` | public Android properties | API must be exactly 33; identifies evidence environment |
| artifact/signing SHA-256 | host artifacts + `apksigner` | reproducible SDK/APK and common signer identity |
| package path/UID/version | `pm path` + `dumpsys package` | ordinary `/data/app` application deployment |
| manifest SDK/service/permission | `apkanalyzer` | minSdk/targetSdk and three signature-protected Service shape |
| network/native flags | manifest + archive listing | current artifacts request no INTERNET and carry no `.so` |
| Model Runtime flags | protected Runtime dumpsys | production inference/provider/router/hardware remain blocked |
| `evidence_scope` | `ro.kernel.qemu` | emulator vs device application-layer evidence is explicit |

The script exits non-zero on any mismatch and prints key/value evidence only after every check passes. It does not expose a new Binder API. `target_hardware_validated=false` is invariant and application-layer acceptance does not close `DRV-GAP-001`.

## Android R6A1 Bounded Event Runtime

| Interface/type | Input | Output/constraint |
| --- | --- | --- |
| `TrustedPublication.fromRuntimePolicy` | trusted topic, schema ID, payload SHA-256 | no raw payload; unknown topic is typed rejection |
| `publish` | trusted publication | global monotonic `EventEnvelope`, bounded retention, subscriber enqueue |
| `TrustedSubscription.fromRuntimePolicy` | client ID, owner fingerprint, topic set, global cursor, queue capacity | 1..8 unique topics; owner is lowercase SHA-256 |
| `subscribe` | trusted request + process observer | CREATED/REPLAYED/CONFLICT/topic/cursor/quota outcome; replay keeps original observer |
| `dispatchOwned` | subscription ID, owner, bounded batch | overflow callback first, then events; observer failure or reentrant mutation retains head |
| `cancelOwned` | subscription ID + owner | CANCELLED/ALREADY_CANCELLED/not-owner; one close callback |
| `findOwned` / `snapshot` | trusted owner or diagnostics | no cross-owner leakage; bounded counts and no-production flags |

The cursor is global across the three trusted low-frequency topics. A retention gap is therefore a conservative global overflow range; consumers must resynchronize state after overflow. An observer callback cannot reenter publish, subscribe, dispatch or cancel on the same runtime; the attempt is treated as `OBSERVER_FAILED` before queue ownership advances. Event/subscription/cursor state is process-only, callback dispatch is an explicit test call, and no Room/Binder/DDS/network/hardware path is active.

## Android R6A2A Durable Event Schema

| Interface/type | Persisted input | Constraint |
| --- | --- | --- |
| `EventCursorEntity` identity | cursor ID, owner fingerprint, client subscription ID | unique owner + client; no cross-owner key |
| Subscription shape | canonical topics, requested cursor, queue capacity | metadata only; compared by R6A2B repository |
| Recovery state | acknowledged cursor, ACTIVE/RESYNC/CANCELLED, overflow range/count | no event payload or callback object |
| `MIGRATION_2_3` | v2 owner/topic/last sequence/update time | deterministic legacy client identity; all source values retained |
| DAO shape | find by cursor, find by owner/client, insert, update | repository-only foundation; no production Service call |

The schema remains eight tables at version 3. A v2 cursor becomes ACTIVE with requested and acknowledged sequence both equal to its prior `last_sequence`, queue capacity 1, zero overflow and `created_at_wall_ms` copied from the previous update time. This compatibility mapping does not claim that a historical callback registration existed.

## Android R6A2B Durable Event Repository

| Method | Input | Result/constraint |
| --- | --- | --- |
| `register` | owner, client ID, trusted topics, after cursor, queue, trusted latest | CREATED/REPLAYED/CONFLICT/future/global limit/owner limit/source regression |
| `findOwned` | cursor ID + owner | snapshot or null without cross-owner existence disclosure |
| `acknowledgeOwned` | cursor, owner, ACK, trusted latest | monotonic apply/replay; regression/future/source reset/RESYNC blocked |
| `markOverflowOwned` | cursor, owner, dropped range, trusted latest | conservative union; ACTIVE -> RESYNC_REQUIRED; exact range replay |
| `completeResyncOwned` | cursor, owner, snapshot sequence, trusted latest | requires coverage through overflow last; clears range atomically |
| `cancelOwned` | cursor + owner | APPLIED/REPLAYED/not found; bounded cancelled-row retention |

Applied state changes and their digest-only audit rows share one Room transaction. `knownLatestSequence` is trusted Runtime input, not request-body authority. A latest value below persisted ACK returns `SOURCE_REGRESSION`; this prevents accidental reuse after a process-local publisher resets but does not itself provide a durable sequence source.

## Android R6A3 Event Runtime Readiness

| Surface | Record | Constraint |
| --- | --- | --- |
| Runtime startup log | `event_runtime_readiness_snapshot_wired=true` plus activation/wiring/blockers | immutable metadata only |
| Runtime dumpsys | complete key/value readiness snapshot | protected framework diagnostic path; no repository query |
| Diagnostic Binder | `runtime/event-runtime-readiness`, summary `blocked`, sequence 6 | existing paged V1 contract; no AIDL change |

The snapshot reports implementation availability for R6A1/R6A2A/B, trusted topic count 3 and six ordered activation blockers. It never reports live subscription counts or opens the database; those would create a runtime dependency and a privacy surface before production Event ownership is approved.

## Android R6B1 Bounded Memory Lifecycle

| Interface/type | Input | Result/constraint |
| --- | --- | --- |
| `TrustedWrite.fromRuntimePolicy` | owner, client ID, scope, purpose, session, schema, SHA-256 digest, TTL, optional consent | metadata only; PROFILE has no session and requires eligible purpose/consent |
| `write` | trusted write | CREATED/REPLAYED/CONFLICT/policy/consent/quota outcome |
| `queryOwned` | owner plus optional scope/purpose and bounded limit | active redacted records; no digest or cross-owner disclosure |
| `findOwned` | memory ID + owner | lifecycle state/expiry/content-reference-presence only |
| `deleteOwned` | memory ID + owner | APPLIED/REPLAYED/not found; digest cleared and terminal retention bounded |
| `exportOwned` | memory ID + owner + Governance authorization | digest-only SESSION/PROFILE export; EPHEMERAL forbidden |
| `snapshot` | none | bounded counts plus persistence/production/raw-content false flags |

TTL uses an injected monotonic elapsed clock and therefore has no restart guarantee. `TrustedConsentEvidence.grantedByGovernance` and `TrustedExportAuthorization.grantedByGovernance` are internal contract factories, not Binder APIs or production decision authorities. Delete/expiry preserve only a domain-separated request fingerprint for bounded replay; once terminal retention evicts a record, its replay guarantee ends.

## Android R6B2 Memory Runtime Readiness

| Surface | Record | Constraint |
| --- | --- | --- |
| Runtime startup log | `memory_runtime_readiness_snapshot_wired=true` plus activation/prerequisites/blockers | immutable metadata only; no lifecycle instance |
| Runtime dumpsys | complete Memory readiness key/value snapshot | protected framework diagnostics; no Room/Keystore query |
| Diagnostic Binder | `runtime/memory-runtime-readiness`, summary `blocked`, sequence 7 | existing paged V1 contract; no AIDL change |

The snapshot reports R6B1 implementation availability and scope count 3, while schema/repository, encrypted storage, key lifecycle, consent/revocation, trusted retention clock and production wiring remain false. Its eight ordered blocker IDs are the admission gate for any later durable Memory design; the record is not a consent decision or storage-health probe.

## Android R6C1 Signed Built-In Skill Runtime

| Interface/type | Input | Result/constraint |
| --- | --- | --- |
| `listManifests` / `findManifest` | built-in Skill ID | immutable 3-item catalog; no filesystem/package scan |
| `SkillManifest` | compiled version/schema/route/capability/risk/safety/digest/signer constants | allowlist matched, artifact digest bound, real artifact crypto verification false |
| `TrustedInvocation.fromRuntimePolicy` | owner/client, Skill/version/schema, input SHA-256, granted capabilities, trusted safety state | metadata only; no raw input or request-provided identity |
| `admit` | trusted invocation | ADMITTED/REPLAYED/CONFLICT/unknown/version/schema/capability/safety/quota outcome; dispatch false |
| `findOwned` / `cancelOwned` | invocation ID + owner | no cross-owner disclosure; owner cancel idempotent while retained |
| `snapshot` | none | catalog/active/cancelled counts and no-loading/no-network/no-production/no-hardware flags |

Routes (`SOA_OPERATION`, `UIB_ACTION`, `AGENT_PLAN`) are declarative targets only. R6C1 neither calls the route nor performs cryptographic verification over artifact bytes. The signer digest is compile-time contract evidence awaiting a real build/publish verifier and production Skill dispatcher.

## Android R6C2 Fixed Governance Middleware Chain

| Interface/type | Input | Result/constraint |
| --- | --- | --- |
| `stageOrder` | none | immutable identity/schema/privacy/policy/QoS/trace/dispatch/output/audit order |
| `TrustedExchange.fromRuntimePolicy` | trusted identity, compiled Skill manifest, schema/digests, privacy, capabilities/safety, QoS, trace, route and output metadata | metadata only; no raw request/output or request-provided authority |
| `evaluate` | trusted exchange | ALLOWED or first-stage DENIED; later decision stages skipped; AUDIT always recorded once |
| `StageEvidence` | stage/status/reason | domain-separated SHA-256 evidence; immutable and ordered |
| `AuditRecord` | request fingerprint, decision and first rejection | bounded process-local sequence/digest; no raw data or durable claim |
| `recentAudits` | bounded count | immutable newest retained audit window |
| `snapshot` | none | counts plus production/dispatch/raw/audit-persistence/network/hardware false flags |

`DISPATCH_GATE` is a route admission check, not a dispatcher. `dispatchContractAllowed=true` can coexist with `serviceDispatchTriggered=false`, including a later output-guard rejection. AUDIT is deliberately a terminal finalizer after the short-circuited decision chain so denied requests remain observable without evaluating skipped business stages.

## Android R6C3 Skill And Governance Readiness

| Surface | Record | Constraint |
| --- | --- | --- |
| Runtime startup log | Skill/middleware implementation, fixed counts/order, wiring flags and blockers | immutable constants only; no runtime construction |
| Runtime dumpsys | complete Skill/Governance readiness key/value snapshot | protected framework diagnostic path; no catalog/storage query |
| Diagnostic Binder | `runtime/skill-governance-readiness`, summary `blocked`, sequence 8 | existing paged V1 contract; no AIDL change |

The snapshot distinguishes compile-time signer evidence from cryptographic artifact verification and fixed middleware code from production wiring. It reports lifecycle/revocation/rollback, sandbox, authority, route owner, audit persistence and dispatcher gaps without probing an APK, package signer, database, service or hardware device.

## Android R7A1 Runtime Acceptance Snapshot

| Surface | Record | Constraint |
| --- | --- | --- |
| Runtime startup log | core/R7/production/hardware dimensions plus ordered blockers | separate bounded log entry; no subsystem activation |
| Runtime dumpsys | complete aggregate acceptance key/value snapshot | protected framework diagnostic path; no Room query |
| Diagnostic Binder | `runtime/runtime-acceptance`, summary `core-ready-production-blocked`, sequence 9 | existing paged V1 contract; no AIDL change |

The rollup consumes immutable child snapshots, SDK maturity/stage constants and Room schema version only. `core_software_baseline_ready` is a software composition statement; Client2 migration and API 33 E2E remain explicit R7 blockers, while system owner, production subsystems and target hardware are independent blockers that application-layer tests cannot close.

## Android R7B Client2 SDK/Binder Migration (P4-W01 evolved)

| Surface | Input/output | Constraint |
| --- | --- | --- |
| `Client2ScenarioBridge.openSession` | Activity, allowlisted UI alias, bounded text, typed callback | opens one owner-scoped Session/Event stream; no HTTP/model/hardware API |
| legacy `Client2ScenarioBridge.submit` | unchanged Smali descriptor | compatibility wrapper only; replaces previous compatibility stream |
| `SessionClient` | explicit Runtime Session/Event components, version/hash, snapshot/cursor replay | signature permission plus Runtime capability policy remain authoritative |
| `ScenarioCallback` | handle, snapshot, event, replay, overflow, close, error | old status/reply/failure remain default compatibility projection |
| Client2 manifest | Runtime package query and `BIND_RUNTIME` permission | no INTERNET or cleartext opt-in |
| Runtime capability principal | package + complete current signer set | protocol + owned task compatibility + session/event read/open/subscribe/cancel |
| API 33 acceptance script | signed Runtime/Client2 APKs and visible cold-scenario button | verifies Session snapshot/event/replay/reconnect/UI; reports no dispatch/hardware |

The SDK AAR and the two bridge Java sources are compiled by D8 into an embedded `classes2.dex`; the existing Client2 Activity is hooked only after `setContentView`. The debug signer is intentionally shared with Runtime so Android can grant the signature permission, while the inner package/current-signer policy still applies least privilege. This is an APK-level test integration, not a claim that the original Client2 signer or RenderService trust contract is preserved.

## Android R7C Application Integration Acceptance

| Test surface | Trigger | Expected contract |
| --- | --- | --- |
| Runtime availability | disable/enable Runtime package from adb shell | visible bind failure, in-flight release, same-Activity retry success |
| Client2 compatibility replacement | two sequential accepted Session requests | previous stream closes; each Session has one sequence-1 event and one initial replay |
| Runtime death | DUMP-protected debug broadcast after initial replay | original Session reconnects and replays without duplicate event or fake terminal |
| Client2 restart | force-stop/relaunch Activity process | fresh panel hook, SDK bind, callback and UI reply |
| SDK lifecycle regression | existing androidTest instrumentation | service death/reconnect, callback death, terminal uniqueness, cancel/completion race |

`RuntimeFaultProbeReceiver` is a debug-only test interface, not a Runtime product API. Client2 cannot invoke it, release packaging excludes it, and the acceptance script restores Runtime package state through an EXIT trap. The evidence contract is stored in `central_brain_android_r7c_acceptance.json`; its positive claims stop at API 33 application integration.

## Android R7D Delivery And Empty Integration Slots

R7D adds deployment contracts and host tooling, not a new runtime service API. The application call path remains Client2/HMI -> public SDK -> typed Binder -> identity/capability/governance -> durable Runtime. Packaging never bypasses this path.

| Contract | Producer | Consumer | Invariant |
| --- | --- | --- | --- |
| `central-brain.android-delivery-profile.json` | repository owner | package builder/static gate | four ordered artifacts, one signer cohort, seven inactive slots |
| `DELIVERY-MANIFEST.json` | package builder | verifier/installer/integrator | source commit, artifact facts, support inventory, status and blocker snapshot |
| `SHA256SUMS` | package builder | verifier/integrator | exact path-safe coverage of every non-symlink bundle file except the checksum list itself; consistency only, not publisher authentication |
| `target-inputs.example.json` | project team | target integration owner | unresolved owner/deployment/vendor/evidence decisions; no guessed positive claim |
| installer dry-run output | bundle installer | release/acceptance owner | API 33, existing signer parity, no install/uninstall and blocked production/hardware state |

The seven empty slots are `target.system.owner.empty`, `effect.delivery.empty`, `model.vendor.npu.empty`, `event.runtime.empty`, `memory.runtime.empty`, `skill.governance.empty` and `target.hardware.evidence.empty`. Each slot carries one blocker ID, `activation_allowed=false` and replacement evidence requirements. The vendor NPU slot contains no native implementation; C/C++ is allowed only after a published vendor SDK requires a native adapter at the existing Model Provider boundary.

The installer resolves `adb`, `aapt`, `apksigner` and Java from explicit variables, `PATH` or standard Android/JDK roots. It re-reads each delivered APK package/signer, then verifies all already-installed signer digests before issuing the first fixed-order `adb install -r`; a mismatch returns `SIGNER_MIGRATION_REQUIRED` with no package mutation.

The target deployment and Client2 recovery commands are source-checkout acceptance bindings. Their inclusion in the bundle supplies the executable test entrypoints and traceability, not a claim that the archive contains the complete Gradle/Client2 build graph.

## Android B0 C/Java Ownership Boundary

| Boundary | Owner | Allowed data | Forbidden responsibility |
| --- | --- | --- | --- |
| App/SDK -> Runtime | Java/AIDL | typed task/action/status and callbacks | raw pointer, vendor SDK object, device handle |
| Runtime -> Native | Java/JNI | ABI version, fixed-width values, bounded byte arrays | caller identity, permission assertion, long Binder work |
| Native core | C ABI V1 | lifecycle, resource counters, provider descriptors/status | Binder/PackageManager, Room, network, device nodes, policy |
| Native -> Vendor slot | versioned C provider contract | published SDK-owned descriptor and opaque adapter state | guessed ioctl/HAL, implicit ownership, unbounded buffers |

Public C structs begin with `struct_size`/`abi_version`, use fixed-width integer types and caller-owned outputs. JNI registers through `JNI_OnLoad`/`RegisterNatives`, does not cache `JNIEnv*` or Java local references, and converts C status into immutable Java snapshots. Initial ABIs are `arm64-v8a` and `x86_64`; Vendor NPU/VHAL remain `UNAVAILABLE` with `hardware_accessed=false`.

## Android B1 Native Runtime API V1

| Surface | Operations | Ownership/error model |
| --- | --- | --- |
| C ABI | `create/get_health/acquire_slot/release_slot/destroy/status_name` | opaque handle; caller-owned outputs; typed status; active lease blocks destroy |
| JNI | `nativeCreate/nativeSnapshot/nativeAcquireSlot/nativeReleaseSlot/nativeDestroy` | static registered methods; no cached refs; Java owns handle serialization |
| Java | `NativeRuntime.snapshot/acquireSlot/releaseSlot/close` | synchronized, `AutoCloseable`, invalid/closed state fails visibly |
| Diagnostic value | `NativeRuntimeSnapshot` | immutable strict 10-field parse; ABI/range/boolean/provider/hardware drift fails closed |

The B1 interface is process-local and does not accept caller identity, Binder objects, file descriptors, model buffers or hardware handles. B2 may expose its readiness through existing Runtime/Diagnostic surfaces but may not transfer Governance ownership into C or enable provider dispatch.

## Android B2 Native Runtime Process Integration

| Caller/surface | Callee/data | Invariant |
| --- | --- | --- |
| Android process start | `CentralBrainRuntimeApplication -> NativeRuntimeProcess.start(4)` | one process-owned handle; failure becomes `UNAVAILABLE` |
| Runtime Service log/dumpsys | `NativeRuntimeProcessSnapshot` | read-only readiness; no slot lease or dispatch |
| Diagnostic Service | sequence 10 `runtime/native-runtime-readiness` | same snapshot and ordered ABI/lifecycle/provider fields |
| Debug native probe | isolated `NativeRuntime(2)` | load/capacity/busy-close/release/drain/close only |
| Host verifier | Runtime APK native payload and ELF metadata | exact arm64/x86_64 allowlist, signer and hardening checks |

The production call relationship is `Binder client -> Java Runtime/Governance -> durable Java workflow`; it does not continue into C in B2. Native Runtime is a process-health and future-provider boundary only. `software_provider_available`, `vendor_npu_provider_available`, `runtime_dispatch_enabled` and `hardware_accessed` remain false on every production and diagnostic surface.

## Android B3 Black-Box Preflight Interfaces

| Interface | Producer -> consumer | Fail-closed rule |
| --- | --- | --- |
| Host read-only preflight | adb/getprop/pm/apksigner -> integration owner | API/64-bit ABI/signer mismatch stops before install |
| Existing package signer check | `pm path` + readable installed base APK -> apksigner | unreadable or mismatched signer is not auto-bypassed |
| Signer negative fixture | temporary alternate keystore/APK copies -> read-only preflight | mismatch rejected before install; fixture deleted; device unchanged |
| Java environment probe | PackageManager/Build/Process -> DUMP-protected log | exact API 33, ordinary app, private data, signer and native readiness required |
| B3 acceptance contract | checked-in JSON -> CI/integrator | emulator and physical target claims remain separate |
| Evidence properties | pre/post/install/native tools -> delivery audit | raw observed feature/SELinux/boot values retained; no inferred hardware claim |

The Java probe is in the debug source set and is not a public production Binder API. It neither discovers vendor interfaces nor transfers package/signer authority into C. The only native input is the existing immutable readiness snapshot; Vendor NPU, dispatch and hardware remain false.

## Android B4 Hybrid Delivery Contracts

| Contract | Producer -> consumer | Invariant |
| --- | --- | --- |
| hybrid delivery profile | repository -> package builder/verifier | five fixed artifacts, two fixed native artifacts, seven blockers |
| `DELIVERY-MANIFEST.json` | builder -> installer/integrator | source commit/date, hash/size, APK/signer, ABI/ELF and support inventory |
| `SHA256SUMS` | builder -> verifier | exact non-symlink bundle file coverage; consistency, not publisher authentication |
| maintenance install profile | installer -> Runtime+Demo | default; Client2 untouched |
| Client2 install profile | explicit `--include-client2` -> three APKs | same signer and Runtime -> Demo -> Client2 order |
| target input template | target owners -> installer/review | no guessed physical/production/hardware claims |

The AARs are integration inputs and are not device packages. Runtime owns the C library through Java/JNI; clients continue to call typed Binder rather than native symbols. Packaging does not create a Vendor NPU provider, and the optional Client2 artifact remains behind a target RenderService/signer decision.

## Android B5 GitHub Remote Test Contracts

| Contract | Producer -> consumer | Invariant |
| --- | --- | --- |
| immutable Release | controlled maintainer workstation -> target tester | tag, source commit, archive SHA-256 and delivery ID identify one build |
| publication history guard | selected local ref -> remote `main` | complete reachable history clean; exact ref push only; no mirror/internal refs |
| completed target inputs | target owner -> installer | local-only input; positive production/hardware claims remain forbidden before review |
| remote acceptance runner | target tester host -> B4 verifier/installer/ADB | dry-run default; explicit install; no upload or privileged/system operation |
| `github-safe/summary.env` | evidence collector -> Issue Form | non-secret alias/evidence reference and exit states only; no raw or derived device identity, logs or payload |
| local `private/` evidence | evidence collector -> approved target owner channel | never automatically uploaded; security/privacy review required |
| hardware-test Issue | target tester -> maintainer | immutable release identity, manual scenario result and retest timeline |
| 15-minute Issue poll | Private CougarOS Issue -> maintenance automation through authenticated `gh` | structured reports or maintainer instructions only; one Req-ID increment; never auto-close |

The asynchronous relationship is `maintainer Release -> target tester ADB -> GitHub-safe Issue ->
15-minute poll -> maintainer fix -> replacement Release -> target retest`. GitHub is not a Protocol Binding
to the vehicle, does not invoke Runtime and cannot close a hardware gate. The poll is not an immediate
webhook and a target tester's named-release verification remains mandatory before issue closure.

## Stage 2 P1-W01 Session Contract V1

| Type/surface | Fields or methods | Boundary |
| --- | --- | --- |
| `SessionRequest` | request/scenario/utterance/source/seat/locale/deadline/context version | owner and vehicle Safety state forbidden in payload |
| `SessionHandle` | session ID, accepted time, expiry | Runtime assigns ID/TTL; client cannot assert owner |
| `SessionSnapshot` | state, plan revision, event sequence, timestamps, bounded summary | unknown version/state rejected |
| `SessionQuery` | state filter, terminal flag, opaque cursor, page size | caller owner scope implicit; max page 50 |
| `SessionPage` | bounded snapshots, opaque next cursor, hasMore, generated time | typical Binder reply target <=64 KiB |
| `ICentralBrainSessionRuntime` V1 | version/hash, open/get/list/cancel | contract only; no published Service in P1-W01 |
| `SessionContract` | structural/admission validation | throws stable `CB_SESSION_CONTRACT` argument failure before Binder use |

Protocol identity is
`f4b3ac677b3294e7cb20382652d37ef995432a2e5ca335d131295d6a43d4024c`; six source files are frozen in
`central-brain-sdk/aidl-api/session-v1.sha256`. Android Parcel instrumentation verifies wire serialization, not
service connectivity. P1-W05 owns Binder bind/death/reconnect once Runtime publication and capability policy exist.

Status: `session_contract_v1_defined=true`, `session_runtime_service_published=false`,
`session_runtime_persistence_wired=false`, `hardware_accessed=false`.

## Stage 2 P1-W02 Plan/Node Contract V1

| Type | Core fields | Contract responsibility |
| --- | --- | --- |
| `ScenarioPlan` | IDs/revision/digests/times/nodes/dependencies | immutable bounded graph envelope |
| `PlanNode` | type/capability/input/resource/timeout/retry/idempotency/required/compensation/policy | complete node execution metadata without raw payload |
| `NodeDependency` | prerequisite/dependent/condition | directed DAG edge with existing endpoints |
| `NodePolicy` | policy version/risk/approval/verification/failure mode | metadata only; never an authorization grant |
| `PlanContract` | schema/range/allowlist/DAG/compensation validation | throws stable `CB_PLAN_CONTRACT` failure before runtime use |

The 11 node types and graph limits are frozen by `PlanContract`; the four AIDL sources are frozen by
`aidl-api/plan-v1.sha256` with concatenated identity
`8dbf27424a09ecac969aff444e7fc9e3c939c7bc5c6687de2d8a5a627d60dabd`. Existing task, Governance and Session
checksums remain unchanged. Android 13/API 33 ARM64 instrumentation proves Parcel and rejection behavior only.

There is no new Binder surface in P1-W02. A future Scenario/Session Runtime may return these DTOs only after
P1-W03..P1-W05 define event/callback/facade ownership. `plan_runtime_published=false`; P2-W07 owns compilation and
full semantic graph validation, and P3 owns durable scheduling/recovery. Req IDs: `S2-SCN-001`, `S2-GRF-001`,
`FW-S-001`, `NV-F-001`, `NV-F-008`, `NV-G-004`.

## Stage 2 P1-W03 Event Contract V1

| Type/surface | Core fields or calls | Contract responsibility |
| --- | --- | --- |
| `RuntimeEvent` | event/sequence/session/parent/type/source/time/privacy/digests/payload kind | immutable typed event envelope and causal identity |
| `ActionEvent` | action/node/capability/state/action digest/required | governed action metadata, not authorization |
| `ObservationEvent` | observation/subject/outcome/quality/evidence digest/terminal | bounded readback metadata |
| `MessageEvent` | message/role/locale/display text/content digest/redaction | bounded HMI text and explicit redaction |
| `EventPage` | request cursor/after sequence/events/next marker/hasMore/redaction/time | authoritative bounded replay page |
| `ICentralBrainSessionEvents` V1 | version/hash/get/register/unregister | independent contract-only Event surface |
| one-way callback | event/overflow/closed | notification only; cursor replay recovers gaps |

The seven AIDL sources are frozen by `aidl-api/events-v1.sha256`; the normalized interface identity is
`bb3618ca5f5818ce70b3a889a928b54ad83c70f0e439db5b62c67eb3234957d5`. `EventContract` validates a 23-type
allowlist, exact typed payload mapping, canonical identifiers/digests, contiguous sequence and parent ordering,
explicit redaction, page markers and immutable cursor continuation. No event update method exists.

Status: `event_contract_v1_defined=true`, `event_parcel_physical_android13_arm64_verified=true`,
`event_runtime_service_published=false`, `event_callback_service_published=false`, `hardware_accessed=false`.
P1-W05 owns Binder publication/lifecycle and P1-W06 owns durable storage; neither is implied by this interface
definition. Req IDs: `S2-SES-001`, `S2-EVT-001`, `FW-U-003`, `NV-F-009`, `NV-G-003`, `NV-G-007`.

## Stage 2 P1-W04 Effect/Approval Contract V1

| Type | Core fields | Contract responsibility |
| --- | --- | --- |
| `EffectIntent` | effect/session/plan/node/action/capability/area IDs, one typed scalar, target/idempotency/plan/context digests, context version, risk, verification, compensation, deadline | immutable requested side-effect identity and execution preconditions; never an authorization grant |
| `EffectObservation` | observation/effect IDs, state/source/attempt, target/reported/evidence/observation digests, failure/terminal/retry/simulated markers | separates dispatch, delivery, apply, verification, unknown and compensation evidence |
| `ApprovalPrompt` | approval/session/plan/action/target/context/policy binding, digest, created/expiry | bounded human decision prompt; stale/expired resume fails closed |
| `UndoHandle` | undo/effect/source observation/verified observation/compensation binding, context version, state, created/expiry | bounded eligibility for a future governed compensation operation; not a rollback command |
| `EffectContract` | structural, typed-value, transition, approval-resume and undo validation | rejects unknown schema/enums, identity drift, skipped/terminal transitions, stale context and unsafe simulation markers |

`EffectIntent` carries exactly one active boolean/integer/decimal/text scalar and its target digest. The state
machine distinguishes `DISPATCHED`, `DELIVERED`, `APPLIED` and `VERIFIED`; retry increments the attempt exactly
once, and every terminal state is immutable. Applied/verified/compensation observations require a reported-value
digest. `SIMULATED` source and marker must agree.

Approval is valid for at most five minutes and is rebound against the current plan/action/context digest and
context version before execution can resume. Undo eligibility is valid for at most fifteen minutes, references a
verified observation and compensation digest, and cannot regress context version. Safety remains authoritative:
a prompt or handle never overrides a hard interlock and neither contains caller identity or vehicle-state claims.

The four AIDL sources are frozen by `aidl-api/effect-v1.sha256`; concatenated protocol identity is
`709828114422595f1889dad58e8e60daf4d5e4f98c962a6145f2f8a39b0c178d`. P1-W04 intentionally adds no
Binder interface and does not modify Governance V1. Status: `effect_contract_v1_defined=true`,
`effect_parcel_physical_android13_arm64_verified=true`, `effect_runtime_service_published=false`,
`approval_response_service_published=false`, `undo_service_published=false`, `hardware_accessed=false`.
P1-W05 now owns Session/Event facade/Service lifecycle; P1-W06 owns durable storage. Req IDs: `S2-EFF-001`, `S2-SAF-001`,
`S2-UX-002`, `FW-S-005`, `NV-F-001`, `NV-G-005`, `NV-G-006`, `NV-G-007`.

## Stage 2 P1-W05 SDK Facade v2

| Surface | Calls/callbacks | Ownership and error boundary |
| --- | --- | --- |
| `ScenarioClient` | connect/reconnect/open/get/list/cancel/observe/stop/close | HMI public API; typed DTO and stable `Failure.code` only |
| `RuntimeEventListener` | snapshot/event/replay complete/overflow/closed/error | serial caller executor; no Binder thread UI work |
| `ScenarioTransport` | Session/Event protocol and data calls | package-private test seam; may use AIDL/RemoteException internally |
| `AndroidScenarioTransport` | explicit dual-action bind/death/callback bridge | same Runtime component, generation-scoped Binder lifecycle |
| `TransientSessionEndpoint` | Session/Event AIDL Stub | capability before request; owner from Binder identity |
| `SessionRegistry` | open/find/list/cancel/events | injectable owner-scoped persistence boundary |
| `DurableSessionRegistry` | same interface over Room v4 | transaction, idempotency, capacity and process-death recovery owner |

Public facade errors are `NOT_CONNECTED`, `PROTOCOL_MISMATCH`, `TRANSPORT`, `SUBSCRIPTION`, `CLOSED`.
Contract violations remain domain-specific argument errors. Session/Event AIDL V1 version/hash are unchanged;
P1-W05 only publishes them through actions:

```text
com.centralbrain.runtime.action.SESSION_RUNTIME
com.centralbrain.runtime.action.SESSION_EVENTS
```

Recovery is snapshot -> authoritative cursor replay -> monotonic sequence deduplication -> callback register.
Because Event V1 terminal pages do not return a forward resume cursor, reconnect may replay already-seen events;
sequence deduplication preserves delivery correctness, while `ISSUE-034` tracks a future V2 cursor/ack contract.

Status: `sdk_facade_v2_available=true`, `session_runtime_service_published=true`,
`event_runtime_service_published=true`, `event_callback_service_published=true`,
`active_session_reconnect_resubscribe_verified=true`. P1-W06 后 registry 为 Room-backed：
`session_runtime_persistence_wired=true`, `session_runtime_process_death_rehydration=true`,
`scenario_execution_enabled=false`, `hardware_accessed=false`. Approval response, grant and undo execution remain
undefined and are intentionally absent from this facade.

## Stage 2 P1-W06 Room v4 Interfaces

| Interface/data surface | Core operations/keys | Constraint |
| --- | --- | --- |
| `CentralBrainDatabase` v4 | `MIGRATION_3_4`; 13 tables | WAL; no destructive migration |
| `SessionEntity` | sessionId; owner+clientRequestId unique | request digest only; revisioned snapshot |
| `PlanEntity` / `PlanNodeEntity` | session+revision; plan+node; idempotency | schema foundation; no executor |
| `RuntimeEventEntity` | eventId; session+sequence unique | immutable metadata; canonical payload <= 8192 UTF-8 bytes |
| `EffectObservationEntity` | effect+sequence; observationId unique | digest/reference evidence only |
| `CompensationEntity` | compensationId; idempotency unique | not a grant or database rollback |
| `RuntimeStateDao` | owner session lookup/page, event replay, terminal eviction | no cross-owner query surface |
| `SessionRegistry` | open/find/list/cancel/events | Binder endpoint persistence abstraction |
| `DurableSessionRegistry` | Room transaction implementation | callback dispatch occurs after commit |

`MIGRATION_3_4` maps legacy `runtime_session.session_key` to `sessions.client_request_id`, maps known textual
state to Session V1 integers and uses a fixed legacy digest marker because old rows never retained request content.
Completed/cancelled rows remain terminal; all other legacy states fail closed to `FAILED`. Owner-scoped Session V1
queries exclude the legacy digest marker because historical IDs and requests cannot satisfy the new UUID/canonical
request contract. An unfiltered DAO read exists only for migration verification/internal audit. The migration then
drops only the replaced legacy table. Existing task/effect/outbox/approval/audit/event-cursor tables and rows remain
unchanged.

`openOwned` computes the request digest in the call frame, checks owner+request idempotency, evicts only the oldest
terminal Session at capacity, and atomically inserts Session + `ScenarioRequested`. `cancelOwned` returns false for
missing/terminal rows and atomically advances revision/sequence plus appends `SessionStateChanged`. `eventsOwned`
first proves Session ownership, then reads an ordered bounded page. Callback Binder registrations are not database
rows; SDK reconnect rebuilds them from durable snapshot and Event replay.

Status: `room_schema_version=4`, `room_migration_3_4_verified=true`,
`session_runtime_persistence_wired=true`, `session_runtime_process_death_rehydration=true`,
`scenario_execution_enabled=false`, `hardware_accessed=false`. Req IDs: `S2-SES-001`, `S2-GRF-001`,
`S2-EFF-001`, `S2-EVT-001`, `XSC-005/006`, `NV-G-003/004/006/007`, `NV-P-002`.

## Stage 2 P1-W07 Runtime Contract v2 Aggregate

| Aggregate surface | Fixed value | Compatibility boundary |
| --- | --- | --- |
| `central_brain_runtime_contract_v2.json` | schema `2.0.0` | composition identity; not an AIDL V2 |
| Session wire | V1 / frozen hash | published; owner-scoped Room-backed |
| Plan DTO | V1 / frozen manifest | no Binder/compiler/executor |
| Event wire/callback | V1 / frozen hash | published; terminal cursor limitation retained |
| Effect DTO | V1 / frozen manifest | no Effect/approval-response/undo Service |
| SDK error contract | five lifecycle codes + typed Java categories | no public Binder/RemoteException |
| persistence | Room v4 / 13 tables | callbacks are not persisted |
| Event evolution | separate V2 cursor + monotonic ACK required | design decision only; not published |

The aggregate contract fixes Session/Event capability ownership to Binder UID/package/current signer and forbids
identity, permission or Safety authority in payloads. It binds 50/100 item Session/Event pages, 256-character
cursors, 64 replay pages, 4 callbacks/session, 128 callbacks total, 64 durable sessions, 8 current events/session
and 8192 UTF-8 canonical payload bytes. Target Binder budgets are 10 ms protocol, 50 ms open, 30 ms read/cancel
and 50 ms registration; these are testable app-layer budgets, not hardware performance claims.

Event V2 must always provide a resume cursor for the delivered sequence, including a terminal page, and accept a
monotonic owner/session-bound ACK with bounded retention and stale/future rejection. It must use a new interface
version/hash and capability review. V1 cannot be silently changed, and sequence deduplication remains its current
correctness mechanism. `tools/check_central_brain_runtime_contract_v2.sh` enforces the aggregate.

Status: `runtime_contract_v2_defined=true`, `runtime_contract_v2_verified=true`,
`runtime_contract_v2_physical_android13_arm64_verified=true`,
`frozen_v1_hashes_unchanged=true`, `event_v2_cursor_ack_required=true`,
`event_v2_interface_published=false`, `scenario_execution_enabled=false`, `hardware_accessed=false`.

## Android P2-W01 Canonical Vehicle Signal Schema

Package: `com.centralbrain.runtime.vehicle.schema`. This is an in-process Java value contract. It is not AIDL,
not a vehicle provider, and not an OEM property mapping.

| Type | Public contract | Failure behavior |
| --- | --- | --- |
| `VehicleSignalPath` | `fromCanonicalPath(String)`、`getScalarType/unit/areas/maximumAgeMs`、`validateUnitAndArea` | unknown path、wrong unit/area -> `IllegalArgumentException` |
| `SignalValue` | `ofBoolean/ofInteger/ofDecimal/ofText/withoutValue`、typed getter、`validateFreshness(nowElapsedMs)` | type/quality/value mismatch、non-finite decimal、control/oversize text、non-positive revision -> reject |
| `SignalTimestamp` | source epoch + received elapsed realtime、`ageMs/isFresh` | non-positive source、negative/future receive、negative max age -> reject |
| `SignalQuality` | `VALID/STALE/UNAVAILABLE/ERROR/CONFLICT`、`hasScalarValue/isUsableForDecision` | only `VALID` is decision-usable; no-value qualities reject scalar |
| `SignalSource` | `SIMULATED/AAOS/VENDOR/DERIVED` provenance helpers | never grants provider availability or production authority |

The initial allowlist is exactly:

| Canonical path | Scalar | Unit | Areas | Max age |
| --- | --- | --- | --- | --- |
| `Vehicle.Speed` | decimal | `km/h` | `global` | 500 ms |
| `Vehicle.Powertrain.Transmission.CurrentGear` | text | empty | `global` | 1000 ms |
| `Vehicle.Chassis.ParkingBrake.IsEngaged` | boolean | empty | `global` | 1000 ms |
| `Vehicle.Cabin.HVAC.IsAirConditioningActive` | boolean | empty | `cabin` | 2000 ms |
| `Vehicle.Cabin.HVAC.AmbientAirTemperature` | decimal | `celsius` | `cabin` | 5000 ms |
| `Vehicle.Cabin.HVAC.Station.TargetTemperature` | decimal | `celsius` | four seat zones | 2000 ms |
| `Vehicle.Cabin.HVAC.Station.FanSpeed` | integer | `level` | cabin/front zones | 2000 ms |
| `Vehicle.Cabin.Seat.IsOccupied` | boolean | empty | four seat zones | 1000 ms |
| `Vehicle.Cabin.Seat.IsBelted` | boolean | empty | four seat zones | 1000 ms |
| `Vehicle.Cabin.Seat.Heating` | integer | `level` | four seat zones | 2000 ms |
| `Vehicle.Cabin.Seat.Ventilation` | integer | `level` | four seat zones | 2000 ms |
| `Vehicle.Cabin.Seat.Position.Recline` | decimal | `degree` | four seat zones | 1000 ms |

`VALID` must be fresh at validation time; `STALE` must be older than the path maximum. `UNAVAILABLE/ERROR/
CONFLICT` carry metadata but no scalar. Freshness is based only on `receivedElapsedRealtimeMs`; source epoch remains
for trace correlation and must not drive safety timeout decisions.

Status: `vehicle_signal_schema_defined=true`, `vehicle_signal_path_allowlist_count=12`,
`vehicle_signal_schema_android13_arm64_verified=true`, `vehicle_signal_provider_wired=false`,
`vehicle_property_mapping_configured=false`, `hardware_accessed=false`. Req IDs: `S2-CTX-001`, `S2-TWN-001`,
`DEL-001/003..005`; tracking: `DEV-030`, `ISSUE-030`.

## Android P2-W02 Vehicle Capability Catalog

Package: `com.centralbrain.runtime.vehicle.capability`. This is immutable in-process metadata. It does not discover,
bind or call an adapter.

| Type | Public contract | Invariant |
| --- | --- | --- |
| `CapabilityCatalog` | `stage2Defaults/all/require/size/productionAuthorizedCount` | exactly 8 ordered IDs; immutable; duplicate/null rejected |
| `VehicleCapability` | id/version/areas/availability/unit/range/risk/reported path/required signals | reported path type+unit+areas must match target |
| `TargetRange` | boolean/integer/decimal/text factory and type-specific validator | finite, bounded, step-aligned, bounded text, optional text allowlist |
| `CapabilityAvailability` | readable/writable/simulatable/productionAvailable/productionAuthorized | authorized requires available+writable; default production false |

| Capability | Target contract | Areas | Readback/dependency | Risk |
| --- | --- | --- | --- | --- |
| `vehicle.hvac.target_temperature` | decimal 16..30 `celsius`, step 0.5 | four seat zones | target-temperature path | LOW |
| `vehicle.hvac.power` | boolean | cabin | HVAC-active path | LOW |
| `vehicle.hvac.fan_level` | integer 0..7 `level` | cabin/front zones | fan-level path | LOW |
| `vehicle.seat.heating` | integer 0..3 `level` | four seat zones | heating + fresh occupancy | MEDIUM |
| `vehicle.seat.ventilation` | integer 0..3 `level` | four seat zones | ventilation + fresh occupancy | MEDIUM |
| `vehicle.seat.recline` | decimal 0..60 `degree`, step 1 | four seat zones | recline + speed/gear/brake/occupancy/belt | HIGH |
| `media.playback` | text enum PLAY/PAUSE/STOP | cabin | no fabricated vehicle readback | LOW |
| `navigation.poi` | text, max 128 chars | cabin | no fabricated vehicle readback | MEDIUM |

`readable/writable/simulatable` describe supported semantics and future debug adapters. They do not imply that an
adapter exists. `productionAvailable/productionAuthorized` are false for all Stage 2 defaults, and the catalog's
authorized count must be zero. Real target evidence must produce a separately reviewed activation mapping; it may
not mutate this catalog silently.

Status: `vehicle_capability_catalog_defined=true`, `vehicle_capability_count=8`,
`vehicle_capability_catalog_android13_arm64_verified=true`,
`vehicle_production_capability_authorized_count=0`,
`vehicle_capability_adapter_registry_wired=false`, `vehicle_property_mapping_configured=false`,
`hardware_accessed=false`. Req IDs: `S2-TWN-001`, `S2-ADP-001`, `DEL-001/003..005`; tracking:
`DEV-031`, `ISSUE-029/030`.

## Android P2-W03 Vehicle Digital Twin Store

Package: `com.centralbrain.runtime.vehicle.twin`. This is a pure-Java in-process state boundary. It is not an
AIDL surface, Room repository, vehicle adapter, Effect executor or hardware abstraction.

| Type | Public contract | Invariant/failure |
| --- | --- | --- |
| `VehicleDigitalTwinStore` | `getRevision/updateReported/setDesired/compareAndSetDesired/clearDesired/reported/desired/snapshot` | synchronized global revision; stale/conflicting reported update rejects; duplicate replay is idempotent |
| `DesiredStateRecord` | `ofBoolean/ofInteger/ofDecimal/ofText`, typed getters, requested/expiry/store revision, `matches` | canonical type/unit/area; finite/bounded scalar; `0 < TTL <= 15 min`; template gets revision exactly once |
| `ReportedStateRecord` | value, accepted store revision, expiry, effective quality, decision usability | accepted value was fresh; snapshot-time VALID may project to STALE without changing source value |
| `DigitalTwinSnapshot` | revision/capture time, immutable desired/reported lists, lookup and `reconcile` | one lock/revision window; path filtered; active desired excludes expired while audit lookup retains it |

Reconciliation values are `NO_DESIRED`, `DESIRED_EXPIRED`, `PENDING_REPORTED`, `REPORTED_STALE`,
`REPORTED_UNAVAILABLE`, `MATCHED`, and `MISMATCH`. A match compares exact typed scalar, path, area and unit; it is
state agreement only, not delivery, application or verification evidence.

All time parameters are receive-side elapsed realtime milliseconds supplied by the caller so unit tests and
snapshot policy remain deterministic. Source epoch remains observation metadata. `compareAndSetDesired` compares
the current desired record revision for one path/area; it is not a distributed transaction or hardware CAS.

Status: `vehicle_digital_twin_store_defined=true`,
`vehicle_digital_twin_android13_arm64_verified=true`,
`vehicle_digital_twin_persistence_wired=false`, `vehicle_digital_twin_adapter_wired=false`,
`vehicle_property_mapping_configured=false`, `hardware_accessed=false`. Req IDs: `S2-TWN-001`,
`DEL-001/003..005`; tracking: `DEV-032`, `ISSUE-030`.

## Android P2-W04 Trusted Context Snapshot

Package: `com.centralbrain.runtime.context`. This is an immutable in-process Context foundation. It is not an
AIDL DTO, persistent repository, Safety authority, vehicle provider or production Service.

```java
ContextSnapshot ContextSnapshotBuilder.build(
    DigitalTwinSnapshot twin,
    SafetyVehicleStateSnapshot runtimeState,
    ContextFieldPolicy policy,
    ContextSnapshot.SeatZone seatZone,
    boolean profileMemoryAvailable);
```

| Type | Public contract | Invariant/failure |
| --- | --- | --- |
| `ContextFieldPolicy` | `general/seatComfort/seatRecline`, policy ID/version, max Runtime-state age, immutable requirements | fixed path+scope allowlist, no duplicate field; general safety paths always required |
| `ContextSnapshotBuilder` | one atomic Twin snapshot + Runtime state -> Context | future Runtime state rejects; stale/unknown/conflicting required context fails closed |
| `ContextSnapshot` | context/schema/policy/Twin/Runtime identity, seat/driving/safety/source, restricted/trust/digest, immutable reports | contextId derives from 64-hex SHA-256; P2-W04 rejects productionTrusted=true |
| `ContextField` | path/area/required/state/typed value/effective quality/source/trust | MISSING carries no observation; observed state exactly matches effective quality |

Field states are `AVAILABLE/MISSING/STALE/UNAVAILABLE/ERROR/CONFLICT`; source trust is
`SIMULATED/PLATFORM_UNVERIFIED/DERIVED_UNVERIFIED/UNKNOWN`. Report lists separately expose missing required,
stale, conflict and all observed non-production-trusted fields. Optional missing cabin fields do not restrict a
general Context; any required non-AVAILABLE field does.

Driving state is `PARKED/MOVING/UNKNOWN`. Runtime motion and signal-derived motion must agree when both are known;
disagreement selects the conservative state and sets motionConflict/restricted. Moving is carried as Context,
not treated as a blanket error; downstream action policy owns moving-specific denial.

Status: `context_snapshot_defined=true`, `context_snapshot_android13_arm64_verified=true`,
`context_snapshot_production_trusted=false`, `context_snapshot_production_wired=false`,
`vehicle_signal_provider_wired=false`, `hardware_accessed=false`. Req IDs: `S2-CTX-001`, `S2-SAF-001`,
`DEL-001/003..005`; tracking: `DEV-033`, `ISSUE-029/030`.

## Android P2-W05 Scenario Manifest

Package: `com.centralbrain.runtime.scenario`. These are pure-Java build-owned metadata and validation types. They
are not AIDL, Resolver, Compiler, executable Graph, Effect dispatcher or production Service.

| Type | Public contract | Invariant/failure |
| --- | --- | --- |
| `ScenarioManifest` | schema/scenario/version/digest, source/zone, context/capabilities, risk, Plan template, fallback, UI | immutable; fixed IDs/enums/bounds; node type/capability/policy/DAG/compensation validation |
| `ScenarioManifestParser` | `parse(sourceName, bytes)` | strict JSON, <=64 KiB/depth16/token4096; duplicate/unknown/null/trailing/type/version reject |
| `ScenarioCatalog` | `load(Map<String, byte[]>)`, `all/find/require/disabled/getCatalogDigest` | deterministic sort; invalid isolated; duplicate scenario ID disables all copies; immutable index |
| JSON schema | draft 2020-12, `additionalProperties=false`, schemaVersion const 1 | build-time structural contract; parser remains runtime authority |
| checksum sidecar | SHA-256 for three manifests and schema | Git/CI/APK asset identity; not independent cryptographic signer evidence |

The built-in v1 IDs are `scene.comfort.cold.v1`, `scene.fatigue.assist.v1` and `scene.rest.nap.v1`. Each declares
supported request sources/zones, a fixed Context policy, required/optional canonical Context paths and capability
IDs, highest risk, bounded node/dependency template, fail-closed or optional-only fallback and localization/icon
keys. The Plan template carries no target scalar and cannot be dispatched. P2-W06/P2-W07 must resolve and compile
it under fresh Context/capability policy.

Fatigue and rest seat-recline templates are HIGH, `PARKED_ONLY` and approval-required. This metadata cannot grant
approval or override a hard interlock. Unknown manifest fields, unknown canonical paths/capabilities/node types,
oversize input, duplicate field/ID, invalid dependency/cycle/compensation and risk mismatch fail closed. One bad
asset does not remove other unique valid scenarios.

Status: `scenario_manifest_schema_version=1`, `scenario_catalog_count=3`,
`scenario_manifest_android13_arm64_verified=true`, `scenario_manifest_artifact_crypto_verified=false`,
`scenario_catalog_production_trusted=false`, `scenario_runtime_wired=false`,
`scenario_graph_execution_enabled=false`, `effect_dispatch_enabled=false`, `hardware_accessed=false`.
Req IDs: `S2-SCN-001`, `S2-SAF-001`, `DEL-001/003..005`; tracking: `DEV-034`, `ISSUE-029/031`.

## Android P2-W06 Deterministic Scenario Resolver

### Public internal Java contract

```java
ScenarioResolution ScenarioResolver.resolve(
    ScenarioResolver.Request request,
    ScenarioCatalog catalog,
    ContextSnapshot context,
    ScenarioResolver.CapabilitySnapshot capabilities);
```

This is an in-process Runtime-domain API, not AIDL and not a published Service. `Request` contains only bounded
`explicitScenarioId`, `textIntent`, manifest `Source` and `Zone`; it computes a digest and rejects empty requests,
invalid IDs, text over 256 characters and control characters. Binder caller identity, Safety authority and vehicle
state are not request fields.

| Type | Contract | Invariant/failure |
| --- | --- | --- |
| `Request` | explicit ID, bounded text, source, zone, request digest | explicit ID wins; text does not persist into result; empty/invalid/control input rejected |
| `CapabilitySnapshot` | schema/revision/profile, immutable ID availability, digest | software-simulation uses writable+simulatable; production uses production available+authorized; runtime unavailable is subtractive; production trust fixed false |
| `DeterministicScenarioResolver` | exact built-in ID or fixed normalized alias -> registered manifest | unknown/multi-scene ambiguous/catalog miss fail closed; no model, fuzzy matching or capability creation |
| `ScenarioResolution` | decision, match type/rule, selected ID/manifest, stable reason list, missing context/capability lists, request/context/capability/resolution digests | immutable; accepted/degraded alone carry selected manifest; rejected carries no executable manifest; `isExecutable=false` |

Decision semantics:

1. `REJECTED`: any required source/zone/Context/capability/production-trust/PARKED_ONLY gate fails;
2. `DEGRADED`: all required gates pass but an optional capability or optional parked-only branch is unavailable;
3. `ACCEPTED`: required and optional resolver-level availability gates pass. This still does not authorize or execute
   the scenario; P2-W07 must compile and revalidate the exact digests.

The allowlisted text rules are versioned in source (`intent.cold.v1`, `intent.fatigue.v1`, `intent.rest.v1`) and only
select the three P2-W05 manifest IDs. The resolver does not interpret free-form targets. Moving fatigue can degrade
without optional seat recline; moving rest rejects because the rest manifest requires recline.

Status: `scenario_resolver_defined=true`, `scenario_resolution_schema_version=1`,
`scenario_resolver_android13_arm64_verified=true`, `scenario_resolver_model_invoked=false`,
`scenario_resolver_runtime_wired=false`, `scenario_compiler_wired=false`,
`scenario_graph_execution_enabled=false`, `effect_dispatch_enabled=false`, `hardware_accessed=false`.
Req IDs: `S2-SCN-001`, `S2-SAF-001`, `DEL-001/003..005`; tracking: `DEV-035`, `ISSUE-029/031`.

## Android P2-W07 Scenario Plan Compiler

### Public internal Java contract

```java
ScenarioPlanCompiler.CompiledPlan ScenarioPlanCompiler.compile(
    ScenarioPlanCompiler.CompileRequest request,
    ScenarioResolution resolution,
    ContextSnapshot context,
    ScenarioResolver.CapabilitySnapshot capabilities);

void PlanGraphValidator.validate(
    ScenarioPlanCompiler.CompiledPlan plan,
    ContextSnapshot context);
```

This remains an in-process Runtime-domain API, not AIDL Service publication. `CompileRequest` carries canonical
plan/session UUID, positive revision and a bounded compile/deadline window. It contains no caller authority, target
scalar, vehicle payload or adapter handle. The compiler rejects `REJECTED` Resolution and rechecks the exact
resolution, Context, capability, scenario, manifest version/artifact and Context-policy bindings before translating
the template.

| Type | Contract | Invariant/failure |
| --- | --- | --- |
| `CompileRequest` | plan/session ID, revision, compile/deadline | canonical UUID; positive revision; deadline <=15 minutes |
| `ScenarioPlanCompiler` | accepted/degraded Resolution + same snapshots -> compiled owner | optional-only fallback pruning; required/undeclared/drift fails closed with `CB_SCENARIO_COMPILE` |
| `CompiledPlan` | immutable owner of typed DAG and binding metadata | every `toScenarioPlan()` is a deep copy; `isExecutable=false`; `isProductionTrusted=false` |
| `PlanDigest` | canonical node-input and full-plan SHA-256 | binds Resolution, manifest version/artifact, Context, capability, IDs, time window, nodes, policies, edges and excluded branch |
| `PlanGraphValidator` | P1 PlanContract plus scenario semantics | required Effect -> reachable verify; HIGH Effect <- approval; valid compensation; non-parked graph excludes PARKED_ONLY/driver recline |

The transport remains the frozen P1-W02 `ScenarioPlan`, `PlanNode`, `NodeDependency` and `NodePolicy` contract.
Compiler output uses digest-only node input because P2-W05 manifests intentionally contain no temperature, fan,
seat-angle, media or navigation target. P3/P4 must provide governed typed Effect material and must re-evaluate fresh
Context/Safety before dispatch; a compiled plan alone grants no execution authority.

Status: `scenario_plan_compiler_defined=true`, `scenario_plan_schema_version=1`,
`scenario_plan_compiler_android13_arm64_verified=true`, `scenario_plan_compiler_runtime_wired=false`,
`scenario_plan_runtime_published=false`, `scenario_graph_execution_enabled=false`, `effect_dispatch_enabled=false`,
`hardware_accessed=false`. Req IDs: `S2-SCN-001`, `S2-GRF-001`, `S2-SAF-001`, `DEL-001/003..005`;
tracking: `DEV-036`, `ISSUE-029/031`.

## Android P2-W08 Simulated Effect Adapter Base

### Debug-only internal Java contract

```java
long SimulationClock.nowElapsedRealtimeMs();
long SimulationClock.advanceBy(long durationMs);

void SimulatedEffectAdapter.setFaultInjectionProfile(FaultInjectionProfile profile);
EffectAdapter.ApplyResult SimulatedEffectAdapter.apply(EffectAdapter.Invocation invocation);
EffectAdapter.StatusResult SimulatedEffectAdapter.queryStatus(String idempotencyToken);
SimulatedEffectAdapter.SimulationObservation
    SimulatedEffectAdapter.querySimulationObservation(String idempotencyToken);
```

All three implementation types live in `runtime-service/src/debug` and are absent from main/release source. The base
adapter implements the existing P1 `EffectAdapter` contract; its supplementary `SimulationDescriptor` cannot be
used as an activation descriptor and always reports `simulation=true`, `productionAuthorized=false` and source
`SIMULATED`.

| Type | Contract | Invariant/failure |
| --- | --- | --- |
| `SimulationClock` | explicit monotonic elapsed time | no wall clock or sleep; advance is 1 ms..24 h and overflow fails closed |
| `FaultInjectionProfile` | immutable, SHA-256-bound selection for one admitted invocation | NONE, DELAY, TIMEOUT, RETRYABLE_FAILURE, TERMINAL_FAILURE, READBACK_MISMATCH; timing is 1..60000 ms |
| `SimulatedEffectAdapter` | token-deduplicated base with linearizable delivery status | maximum 128 process-memory records; same token/different invocation rejects; admitted profile is frozen |
| `SimulationObservation` | separate simulated readback projection | source is SIMULATED and production trust is always false; delivery success does not imply readback match |

`DELAY` completes only after explicit clock advancement and invokes the subclass apply callback once. `TIMEOUT`
keeps delivery unknown and produces timed-out readback. Retryable and terminal failures stay distinct.
`READBACK_MISMATCH` deliberately returns delivery applied while readback is mismatch. Subclasses may validate typed
targets and update simulated state only through `validateSimulationInvocation`, `onSimulationApplied` and
`onSimulationReset`; P2-W09..P2-W11 own those domain implementations.

The base has no AIDL/Service registration, Room persistence, Plan publication, Graph scheduling, Digital Twin
wiring, Vehicle/VHAL/NPU or Driver/HAL access. Status: `simulated_effect_adapter_base_defined=true`,
`simulated_effect_adapter_android13_arm64_verified=true`, `simulated_effect_adapter_debug_only=true`,
`simulated_effect_adapter_production_registered=false`, `simulated_effect_adapter_runtime_wired=false`,
`effect_dispatch_enabled=false`, `hardware_accessed=false`. Req IDs: `S2-ADP-001`, `S2-EFF-001`,
`DEL-001/003..005`; tracking: `DEV-037`, `ISSUE-030/033`.

## Android P2-W09 Simulated HVAC Adapter

### Debug-only typed target contract

```java
HvacTarget HvacTarget.power(boolean enabled);
HvacTarget HvacTarget.targetTemperature(String area, double celsius);
HvacTarget HvacTarget.fanLevel(String area, long level);
byte[] HvacTarget.toCanonicalPayload();
HvacTarget HvacTarget.fromCanonicalPayload(byte[] canonicalPayload);

DigitalTwinSnapshot SimulatedHvacEffectAdapter.getDigitalTwinSnapshot();
```

The payload is a version 1 fixed binary structure: magic, schema version, stable capability code, UTF-8 area length/
bytes, scalar-kind code and full 64-bit scalar value. Decode requires exact length and re-encoding is canonical.
`Invocation.actionId` must equal the decoded capability canonical ID and destination must be `vehicle.hvac`.

| Capability | Area | Absolute target | Readback path |
| --- | --- | --- | --- |
| HVAC power | cabin | boolean | `HVAC_ACTIVE` |
| target temperature | four seat zones | 16..30 celsius, step 0.5 | `HVAC_TARGET_TEMPERATURE` |
| fan level | cabin/row1 zones | 0..7 level, step 1 | `HVAC_FAN_LEVEL` |

Admission validates writable+simulatable/non-production capability metadata and writes desired with a 180-second
TTL. NONE/DELAY completion writes source SIMULATED reported once. TIMEOUT/retryable/terminal failure leave reported
absent. READBACK_MISMATCH writes a deterministic valid but different value so both base observation and Twin
reconciliation expose mismatch. Duplicate token replay cannot advance Twin revision.

The adapter and target type remain debug-source internal APIs: no AIDL, production registry, shared Runtime Twin,
Room, Plan/Graph/Effect Service, Vehicle/VHAL/NPU or Driver/HAL. Status: `simulated_hvac_adapter_defined=true`,
`simulated_hvac_typed_target_verified=true`, `simulated_hvac_desired_reported_verified=true`,
`simulated_hvac_android13_arm64_verified=true`, `simulated_hvac_production_registered=false`,
`simulated_hvac_runtime_wired=false`, `effect_dispatch_enabled=false`, `hardware_accessed=false`.
Req IDs: `S2-ADP-001`, `S2-EFF-001`, `DEL-001/003..005`; tracking: `DEV-038`,
`ISSUE-030/033`.

## Android P2-W10 Simulated Seat Adapter

### Debug-only typed target and safety contract

```java
SeatTarget SeatTarget.heatingLevel(String area, long level);
SeatTarget SeatTarget.ventilationLevel(String area, long level);
SeatTarget SeatTarget.reclineAngle(String area, double angle, String approvalDigest);
byte[] SeatTarget.toCanonicalPayload();
SeatTarget SeatTarget.fromCanonicalPayload(byte[] canonicalPayload);

SeatOccupantSnapshot SeatOccupantStateProvider.currentSnapshot(String area);
boolean SeatApprovalVerifier.isApproved(String approvalDigest, String actionId,
        String area, long safetyRevision, long occupantRevision);
SeatProgressObservation SimulatedSeatEffectAdapter.querySeatProgress(String idempotencyToken);
DigitalTwinSnapshot SimulatedSeatEffectAdapter.getDigitalTwinSnapshot();
```

The version 1 fixed-binary payload binds stable capability code, UTF-8 area, scalar kind/value and an optional
lowercase SHA-256 approval digest. Heating/ventilation require an empty approval; recline requires a digest bound by
the injected simulation verifier. Exact decode length and canonical round-trip reject malformed or trailing input.

| Capability | Area | Absolute target | Additional gate |
| --- | --- | --- | --- |
| Seat heating | driver/passenger | 0..3 level, step 1 | fresh occupied seat |
| Seat ventilation | driver/passenger | 0..3 level, step 1 | fresh occupied seat |
| Seat recline | driver/passenger | 0..60 degree, step 1 | fresh NORMAL+PARKED, driver available where applicable, occupied, unbelted, approval valid |

Recline executes two validations. Admission establishes desired state; dispatch re-reads the Safety and occupant
providers and revalidates approval against their revisions. Motion, unknown/stale safety, belt/occupancy changes or
approval replacement permanently change delivery to `REJECTED` and readback to `TERMINAL_FAILURE`; no reported
state is written. Delayed operations expose a bounded simulated progress projection but publish reported only on
successful completion.

Providers and approval are injected debug contracts, not OEM authorities. The adapter has no AIDL/Service
registration, Room/shared Twin, Plan/Graph/Effect wiring, Vehicle/VHAL/NPU or Driver/HAL access. Status:
`simulated_seat_adapter_defined=true`, `simulated_seat_recline_safety_verified=true`,
`simulated_seat_dispatch_revalidation_verified=true`, `simulated_seat_progress_verified=true`,
`simulated_seat_android13_arm64_verified=true`, `simulated_seat_production_registered=false`,
`simulated_seat_runtime_wired=false`, `effect_dispatch_enabled=false`, `hardware_accessed=false`.
Req IDs: `S2-ADP-001`, `S2-SAF-001`, `DEL-001/003..005`; tracking: `DEV-039`,
`ISSUE-029/030/033`.

## Android P2-W11 Simulated Media/Navigation Adapters

### Debug-only replaceable backend contracts

```java
MediaTarget MediaTarget.playback(PlaybackCommand command); // PLAY/PAUSE/STOP
Optional<MediaStateObservation> SimulatedMediaEffectAdapter.getCurrentState();

NavigationTarget NavigationTarget.poi(String canonicalQuery);
String NavigationTarget.getQueryDigest();
Optional<String> SimulatedNavigationEffectAdapter.getAdmittedQueryDigest(String token);
Optional<NavigationObservation> SimulatedNavigationEffectAdapter.getObservation(String token);
```

Both targets use exact-length version 1 fixed-binary payloads and bind destination/action/capability/cabin area.
Media validates the P2-W02 PLAY/PAUSE/STOP allowlist. Navigation canonicalizes only at its factory boundary with
NFKC+trim; decoder input must already be canonical, control-free and <=128 Java characters.

`MediaStateBackend.apply` receives only enum/revision/elapsed time/mismatch and returns immutable source-SIMULATED
state. `SyntheticNavigationBackend.resolve` receives query SHA-256 rather than raw query and returns only stable
synthetic IDs, label key, bounded distance/duration, revision and mismatch. Both interfaces expose explicit
simulation/production/activity/network flags; Navigation adds location-upload. The adapter constructor rejects any
unsafe flag, and validates every backend result before publication.

No Android MediaPlayer/MediaSession, Intent/startActivity, location API, network, Room/shared Twin, Plan/Graph/
Effect Service, Vehicle/VHAL/NPU or Driver/HAL is referenced. Status: `simulated_media_adapter_defined=true`,
`simulated_navigation_adapter_defined=true`, `simulated_navigation_query_digest_only=true`,
`simulated_media_nav_replaceable_backend_verified=true`, `simulated_media_nav_android13_arm64_verified=true`,
`simulated_media_nav_production_registered=false`, `simulated_media_nav_runtime_wired=false`,
`external_activity_started=false`, `location_uploaded=false`, `network_accessed=false`,
`effect_dispatch_enabled=false`, `hardware_accessed=false`. Req IDs: `S2-ADP-001`, `DEL-001/003..005`;
tracking: `DEV-040`, `ISSUE-030/031/033`.

## Android P2-W12 Debug Simulation Controller

### Debug-only AIDL V1

```java
int getProtocolVersion();
String getProtocolHash();
long setDrivingState(int drivingState);
long setSignal(String canonicalPath, String area, int scalarType,
        boolean booleanValue, long integerValue, double decimalValue, String textValue);
long setAdapterFault(String adapterId, int faultMode, long durationMs);
long advanceSimulationClock(long durationMs);
long reset();
long getRevision();
int getDrivingState();
int getSignalCount();
int getFaultCount();
long getSimulationElapsedRealtimeMs();
int getAuditEntryCount();
String getSnapshotDigest();
```

接口只编译进 debug Runtime APK。Binder component 必须显式绑定 action
`com.centralbrain.runtime.action.BIND_DEBUG_SIMULATION_CONTROLLER`；外层要求 debug-only signature permission
`com.centralbrain.permission.CONTROL_DEBUG_SIMULATION`，方法级再检查 calling UID/package/current signer 对应的
`debug.simulation.control` capability。shell、不同签名或未配置 principal 均失败关闭。

`setSignal` 的 path/area/type 必须匹配 P2-W01；scalar union 未使用字段必须为 canonical default，text 不写
audit。adapterId 只允许 P2-W09..W11 的 HVAC/Seat/Media/Navigation stable debug ID；fault 为 NONE/DELAY/
TIMEOUT/RETRYABLE_FAILURE/TERMINAL_FAILURE/READBACK_MISMATCH，只有 timing fault 携带 1..60000 ms。

返回 revision 仅表示 debug controller process state 变更，不是 Room/Context/Twin/vehicle revision。snapshot
只返回 SHA-256 和计数；不对外传任意 JSON、原始 signal map、POI query 或车辆数据。release 不含接口或
Service。状态：`debug_simulation_controller_runtime_wired=false`、`vehicle_signal_provider_wired=false`、
`hardware_accessed=false`。Req IDs：`S2-CTX-001`、`S2-ADP-001`、`DEL-001/003..005`；tracking：
`DEV-041`、`ISSUE-030/033`。

## Android P3-W01 Agent Graph Runtime

### Process-local API

```java
GraphRunSnapshot start(ScenarioPlan plan);
void pump();
NodeRunSnapshot claimNextReadyNode(String runId);
GraphRunSnapshot suspendClaimedNode(String runId);
GraphRunSnapshot resumeNode(String runId, String nodeId);
GraphRunSnapshot completeClaimedNode(String runId, NodeExecutionOutcome outcome);
GraphRunSnapshot completeWaitingNode(
    String runId, String nodeId, NodeExecutionOutcome outcome);
GraphRunSnapshot cancel(String runId);
GraphRunSnapshot get(String runId);
List<GraphRunSnapshot> list();
```

该 API 不是 AIDL，也没有 `CentralBrainRuntimeService` publication。输入必须先通过 P1-W02
`PlanContract` 与 P2-W07 `PlanGraphValidator`；Runtime 深拷贝 DTO，runId 固定为 planId，不接收任意
executor object、Bundle、Parcel blob 或 node input material。`NodeExecutionOutcome` 只有 SUCCEEDED/FAILED/
SKIPPED；required node 不能 SKIPPED。

Graph 状态为 CREATED/PLANNING/WAITING/EXECUTING/PARTIAL/COMPENSATING/COMPLETED/FAILED/CANCELLED/STUCK；
PARTIAL/COMPLETED/FAILED/CANCELLED/STUCK 是终态。Node 状态为 PENDING/READY/EXECUTING/WAITING/SUCCEEDED/
FAILED/SKIPPED/CANCELLED/COMPENSATING/COMPENSATED/STUCK。状态非法时抛稳定前缀
`CB_GRAPH_RUNTIME:`，容量不足抛 `CB_GRAPH_RUNTIME_CAPACITY:`，registry 准入错误使用
`CB_GRAPH_REGISTRY:`。

### Snapshot 与事件所有权

`GraphRunSnapshot` 只返回 run/session/scenario ID、plan digest、Graph 状态、revision、queue position、
deadline、node immutable projection、retained event 和 event chain digest。active queuePosition=0，queued>0，
terminal=-1。每个 run 最多保留 256 条事件；事件没有 raw input/output/error text。

`NodeExecutorRegistry.controlOnlyContractRegistry()` 只冻结 P1 allowlist 并固定
`dispatchEnabled=false`、`productionAuthorized=false`。P3-W02 可以在该类型上增加 typed executor 合同，
但在该工作包完成前，claim/complete 只能由测试 harness 推进，不能解释为 Effect、model、tool 或 memory 已执行。

状态：`agent_graph_runtime_defined=true`、`agent_graph_state_machine_verified=true`、
`agent_graph_android13_arm64_verified=true`、`agent_graph_executor_dispatch_enabled=false`、
`agent_graph_runtime_persistence_wired=false`、`agent_graph_runtime_binder_published=false`、
`agent_graph_runtime_production_wired=false`、`effect_dispatch_enabled=false`、`model_invoked=false`、
`hardware_accessed=false`。Req IDs：`S2-GRF-001`、`NV-G-004/006/007`、`DEL-001/003..005`；
tracking：`DEV-042`、`ISSUE-022/026`。

## Android P3-W02 Typed Node Executors

### Main-source contract

```java
interface TypedNodeExecutor<I extends NodeExecutionInput, O extends NodeExecutionOutput> {
    String nodeType();
    Class<I> inputType();
    Class<O> outputType();
    NodeExecutionResult<O> execute(I input);
}

void NodeExecutorRegistry.validateInput(String nodeType, NodeExecutionInput input);
void NodeExecutorRegistry.validateResult(String nodeType, NodeExecutionResult<?> result);
void NodeExecutorRegistry.validateExecutor(TypedNodeExecutor<?, ?> executor);
```

`NodeExecutionInput.Identity` 固定绑定 execution/plan/session UUID、node ID、Plan/input digest、attempt 和
deadline。派生 input 为 `ContextInput`、`PolicyInput`、`ApprovalInput`、`EffectInput`、
`VerificationInput`、`SummaryInput`、`CompensationInput`、`DigestOnlyInput`。它们没有 byte[]、Object、Map、
Bundle、JSON 或反射类名字段。

输出为对应 exact class；`NodeExecutionResult.Status` 只有 SUCCEEDED/WAITING/FAILED/REJECTED，reason 只使用
固定 enum，result digest 绑定 node type、status、reason、schema ID、output digest 与 trust flag。Summary 只返回
message key/count/digest，不返回大模型自由文本。

### Schema 与 debug harness

11 类 schema 必须与 `PlanContract.allowedNodeTypes()` 完全相等。Context/Policy/Approval/Effect/Verification/
Summary/Compensation 在 `src/debug` 有确定性实现；Model/Tool/Memory Query/Memory Write 只有
`DigestOnlyInput/Output` schema，无 executor。debug harness 使用显式 switch 和 exact cast；registry 仅校验，
不持有或调用 executor。

Effect executor 固定返回 WAITING + EFFECT_DISPATCH_DISABLED + NOT_DISPATCHED；Compensation 固定返回
REJECTED + COMPENSATION_DISABLED。Context/Verification 即使输入携带 trust evidence，debug output 也固定
`productionTrusted=false`。Policy/Approval 必须有显式可信 authority 才能返回 allowed/approved。

错误前缀：contract/schema 为 `CB_NODE_CONTRACT:`，registry 为 `CB_GRAPH_REGISTRY:`，debug harness 为
`CB_NODE_EXECUTOR:`。状态：`typed_node_executor_contract_defined=true`、
`typed_node_executor_schema_count=11`、`typed_node_executor_debug_count=7`、
`typed_node_executor_graph_dispatch_enabled=false`、`typed_node_executor_production_wired=false`、
`effect_dispatch_enabled=false`、`model_invoked=false`、`hardware_accessed=false`。Req IDs：
`S2-GRF-001`、`S2-SAF-001`、`S2-EFF-001`、`DEL-001/003..005`；tracking：`DEV-043`、
`ISSUE-022/023/024/026`。

## Android P3-W03 CheckpointSerializer

### Registered DTO API

```java
interface PayloadCodec<T> {
    CheckpointValue encode(T value);
    T decode(CheckpointValue value);
}

Registration<T>(String type, int schemaVersion, Class<T> payloadClass, PayloadCodec<T> codec);

CheckpointEnvelope CheckpointSerializer.create(
    String type,
    int schemaVersion,
    String nodeId,
    String planDigest,
    String contextDigest,
    T payload,
    long createdAtEpochMs);
byte[] CheckpointSerializer.serialize(CheckpointEnvelope envelope);
CheckpointEnvelope CheckpointSerializer.deserialize(byte[] encoded);
T CheckpointSerializer.decodePayload(CheckpointEnvelope envelope, Class<T> expectedClass);
```

Registration 在 serializer 构造时冻结，key 为 exact `type + schemaVersion`；payload 使用 `getClass()` exact
match，不接收 class name。Codec 只能与 immutable `CheckpointValue` 交换。该 tree 支持 string、boolean、bounded
long/BigDecimal、enum stable name、list 和 key-sorted map，不支持 null、Object、Bundle、Parcel、Binder、fd、
file path 或 native pointer。

### Envelope 与错误

Canonical JSON 固定字段顺序：

```text
schemaVersion -> type -> nodeId -> planDigest -> contextDigest -> payload -> digest -> createdAt
```

`digest=SHA-256("central-brain.checkpoint.v1" + NUL + canonical-envelope-without-digest)`。decode 先通过 strict
streaming parser 生成 bounded primitive tree，再校验 registration、codec、digest 和完整 byte-for-byte canonical
form。限制为 64 KiB、payload 8 层、1024 value token、每 container 64 项、string 1024 字符、map key 64 字符。

错误前缀为 `CB_CHECKPOINT_<ErrorCode>:`，固定 code 包含 INVALID_ARGUMENT、TYPE_UNREGISTERED、
VERSION_UNSUPPORTED、TYPE_MISMATCH、OVERSIZE、MALFORMED_JSON、DUPLICATE_FIELD、UNKNOWN_FIELD、
DEPTH_EXCEEDED、LIMIT_EXCEEDED、DIGEST_MISMATCH、NON_CANONICAL、PAYLOAD_REJECTED。

状态：`checkpoint_serializer_defined=true`、`checkpoint_serializer_canonical_digest_verified=true`、
`checkpoint_serializer_java_serialization_enabled=false`、`agent_graph_runtime_persistence_wired=false`、
`agent_graph_executor_dispatch_enabled=false`、`effect_dispatch_enabled=false`、`model_invoked=false`、
`hardware_accessed=false`。Req IDs：`S2-GRF-001`、`NV-G-003/006/007`、`DEL-001/003..005`；tracking：
`DEV-044`、`ISSUE-022/026`。

## Android P3-W04 Retry/Timeout Policy

### Timeout 与 backoff API

```java
NodeTimeoutPolicy NodeTimeoutPolicy.from(PlanNode node);
AttemptWindow openAttempt(int attemptNumber,
                          long startedAtElapsedMs,
                          long planDeadlineElapsedMs);
TimeoutSnapshot inspect(AttemptWindow window, long nowElapsedMs);

BackoffCalculator(long baseDelayMs, long maxDelayMs, int jitterPermille);
long calculateDelayMs(String nodeId, String retrySeedDigest, int nextAttempt);
```

`NodeTimeoutPolicy` 先复用 `PlanContract.validateNode`，attempt 只允许 1..node.maxAttempts。window deadline 为
`min(saturatedAdd(start, node.timeoutMs), planDeadline)`；`now >= deadline` 即 EXPIRED。全部时间均为 caller 提供的
monotonic elapsed time，接口不读 wall clock、不创建 timer/thread。

`BackoffCalculator` 允许 base 1..120000 ms、max base..120000 ms、jitter 0..250 permille，nextAttempt 只允许
2..3。默认值为 250 ms/8000 ms/200 permille。jitter digest domain 为 `graph.retry.jitter.v1`，输入固定为
node ID、retry seed SHA-256 与 nextAttempt；同一输入必须得到相同 delay。

### Retry decision API

```java
NodeRetryPolicy NodeRetryPolicy.from(PlanNode node, BackoffCalculator backoff);
RetryDecision decide(int completedAttempt,
                     FailureKind failure,
                     ReconcileState reconcile,
                     long nowElapsedMs,
                     long planDeadlineElapsedMs,
                     String planDigest);
```

`FailureKind` 固定为 RETRYABLE_FAILURE/TIMEOUT/TERMINAL_FAILURE/CANCELLED/DELIVERY_UNKNOWN；
`ReconcileState` 固定为 NOT_REQUIRED/CONFIRMED_NOT_APPLIED/CONFIRMED_APPLIED/UNKNOWN。Decision action 固定为
RETRY、RECONCILE、STOP_EFFECT_ALREADY_APPLIED、STOP_ATTEMPTS_EXHAUSTED、STOP_DEADLINE_EXCEEDED、
STOP_TERMINAL、STOP_CANCELLED，并只暴露 completed/next attempt、delay、eligible elapsed time 与 digest。

非 Effect node 只接受 NOT_REQUIRED，且 DELIVERY_UNKNOWN 非法。`effect.execute`/`compensate` 必须具有 P1 typed
idempotency key；UNKNOWN/未提供 reconcile 只返回 RECONCILE，APPLIED 停止，只有 NOT_APPLIED 才继续评估
attempt/deadline。RECONCILE 在 attempt/deadline 耗尽后仍可返回，因为它不授权再次 dispatch。

状态：`node_retry_policy_defined=true`、`node_timeout_policy_defined=true`、
`effect_idempotency_reconcile_gate_verified=true`、`retry_timeout_policy_runtime_wired=false`、
`agent_graph_executor_dispatch_enabled=false`、`effect_dispatch_enabled=false`、`model_invoked=false`、
`hardware_accessed=false`。Req IDs：`S2-GRF-001`、`NV-G-004`、`DEL-001/003..005`；tracking：`DEV-045`、
`ISSUE-022/026`。

## Android P3-W05 Durable Approval Interrupt

### `ApprovalInterruptExecutor.Request`

| 字段 | 类型/约束 | 语义 |
| --- | --- | --- |
| `approvalId/sessionId/planId` | canonical UUID | 防止跨审批、会话、计划重放 |
| `ownerFingerprint` | lowercase SHA-256 | trusted caller 的非原始身份绑定 |
| `nodeId` | Plan node ID | 当前 approval node |
| `action/plan/context/policy/safetyStateDigest` | lowercase SHA-256 | 执行与恢复时的完整治理绑定 |
| `ttlMs` | 1..300000 | approval 有效窗口，仍受 plan deadline 截断 |
| `planDeadlineEpochMs` | positive long | plan 的绝对截止时间，由 caller 提供 |

`createPending(request, nowEpochMs)` 不读取 clock。返回 immutable `ApprovalInterruptRecord`，其 decision 为 PENDING，
authority material 为空。`recordDecision` 只接受 APPROVED/REJECTED/CANCELLED、trusted authority digest 和窗口内
decision time；`expire` 只允许窗口到期后调用。任何 terminal replay 返回 `CB_APPROVAL_INTERRUPT` 异常。

### Checkpoint codec

`checkpointRegistration()` 返回 type=`graph.approval.interrupt`、version=1、exact class 的 registration。Codec
只使用 `CheckpointValue` map/string/bool，18 个字段必须完整且无未知字段。epoch long 使用无前导零的 decimal
string；record digest 恢复后重新计算。Envelope `createdAt` 也使用 canonical string，以支持当前 epoch。

### `ApprovalResumeValidator`

`ResumeContext` 必须提供当前 owner/session/plan/node/action/plan/context/policy/Safety digest、caller-supplied now、
context fresh、policy authorized、capability allowed、Safety trusted 和 `SAFE/UNSAFE/UNKNOWN`。返回：

| 字段 | 说明 |
| --- | --- |
| `allowed` | 仅 Reason=VALID 时为 true |
| `reason` | VALID、NOT_APPROVED、EXPIRED、AUTHORITY_UNTRUSTED、OWNER_MISMATCH、BINDING_MISMATCH、CONTEXT_STALE、CONTEXT_CHANGED、POLICY_DENIED、POLICY_CHANGED、CAPABILITY_DENIED、SAFETY_UNTRUSTED、SAFETY_UNSAFE、SAFETY_CHANGED |
| `resultDigest` | `graph.approval.resume.v1` domain-separated SHA-256 |

接口不持久化、不调用 Graph/Effect、无 Binder/grant Service、无 hardware API。状态：
`approval_interrupt_persistence_wired=false`、`approval_grant_service_published=false`、
`agent_graph_executor_dispatch_enabled=false`、`effect_dispatch_enabled=false`、`hardware_accessed=false`。
Req IDs：`S2-SAF-001`、`S2-UX-003`、`S2-GRF-001`、`NV-G-005/006/007`、`DEL-001/003..005`；
tracking：`DEV-046`、`ISSUE-022/026/029`。

## Android P3-W06 EffectCoordinator

### `EffectBatch`

```java
EffectBatch create(String batchId, List<EffectBatch.Entry> entries, long nowEpochMs);
EffectBatch.Entry(EffectIntent intent, String resourceKey, List<String> dependencyEffectIds);
```

Batch 限制为 1..16 项，每项最多 16 个 dependency。Constructor deep-copy AIDL mutable `EffectIntent`，调用
`EffectContract.validateIntent`，并强制同 session/plan/action/plan digest、唯一 effect/idempotency key、batch 内依赖、
required 不依赖 optional。`getEntries()` 不可修改，`getIntent()` 每次返回 copy；`batchDigest` 绑定 entry/dependency
计数、顺序、resource 和 Effect 治理字段。

### `EffectDependencyPlanner`

```java
EffectDependencyPlanner.Plan plan(EffectBatch batch);
```

返回 deterministic immutable waves；dependency 必须在更早 wave，同 resource 不进入同 wave。环返回
`CB_EFFECT_DEPENDENCY`。Plan 暴露 batch/digest、waves、effect->wave index 和 `planDigest`，不创建线程或执行 Effect。

### `AdapterRegistry`

```java
Resolution resolve(String capabilityId, String targetArea, Profile profile);
PrepareResult PreparationAdapter.prepare(EffectIntent intent, long nowEpochMs);
```

`Profile` 只有 `DEBUG_SIMULATION`/`PRODUCTION`。Registration 绑定 registration/capability/area/profile、activated、
simulation、productionAuthorized、preparation adapter 和既有幂等 `EffectAdapter`；构造时冻结通过
`EffectAdapterContract.requireSafe` 的 descriptor。Resolve 只做 exact match，无 production->debug fallback；缺失或
未授权返回 `CB_ERR_ADAPTER_UNAVAILABLE`。

`PrepareResult` 为 READY(material) 或 REJECTED(uppercase failure code)。`PreparedMaterial` 绑定 action、destination、
canonical payload/envelope defensive bytes 及其 digest、before-state digest、preparation evidence digest；Coordinator
输出不暴露 bytes。

### `EffectCoordinator`

```java
ExecutionResult execute(EffectBatch batch, AdapterRegistry.Profile profile, long nowEpochMs);
```

固定两阶段：先按 plan 遍历并 prepare 全部 item，再判断 required failure；有 required failure 时不调用任何
`EffectAdapter.apply`。否则按 wave 下发，dependency 只有 DELIVERED 才允许子项。Adapter APPLIED/UNKNOWN/
RETRYABLE_FAILURE/TERMINAL_FAILURE 分别映射到 typed DELIVERED/UNKNOWN/FAILED_RETRYABLE/FAILED_TERMINAL；没有
readback 或 retry。Batch status 为 ALL_DISPATCHED/PARTIAL/FAILED/UNKNOWN/PREPARE_REJECTED。

每个 `ItemResult` 暴露 effect ID、resource、required、outcome、before-state digest 和 defensive
`EffectObservation`；`ExecutionResult` 暴露 batch/dependency-plan/result digest 与不可修改 item list。状态：
`effect_coordinator_graph_wired=false`、`effect_coordinator_persistence_wired=false`、
`production_effect_adapter_registered=false`、`production_effect_dispatch_enabled=false`、
`effect_verification_reconciliation_wired=false`、`hardware_accessed=false`。Req IDs：`S2-EFF-001`、
`S2-SAF-001`、`NV-G-005/006/007`、`DEL-001/003..005`；tracking：`DEV-047`、
`ISSUE-022/026/030/033`。

## Android P3-W07 Effect verification/reconciliation

### `EffectVerifier`

```java
Result verify(EffectIntent intent,
              EffectObservation previous,
              VerificationEvidence evidence,
              AdapterRegistry.Profile profile,
              long nowEpochMs);

Result fail(EffectIntent intent,
            EffectObservation previous,
            String failureCode,
            String evidenceDigest,
            long nowEpochMs);

String expectedTargetDigest(EffectIntent intent,
                            List<VerificationField> expectedFields);
```

`VerificationEvidence` 只能是 CALLBACK、READBACK、UNAVAILABLE、TERMINAL；READBACK 包含 1..8 个唯一 canonical
`VehicleSignalPath`+area field，每项是 immutable expected/before/reported typed scalar、finite tolerance 和 primary 标志。
禁止 caller-supplied matched boolean、map/JSON/Bundle/raw payload。source/sourceId 必须与前一 observation 和 profile 一致。

CALLBACK_ONLY 只允许 catalog 无 readback 的 LOW risk；reported equals/tolerance/state-transition/composite 均由 verifier
内部计算。成功从 DELIVERED/UNKNOWN 先返回 APPLIED，再返回 VERIFIED；mismatch 只到 APPLIED；unavailable 到 UNKNOWN；
deadline/terminal 到 FAILED_TERMINAL。`Result.transitions` 和 latest observation 均 defensive-copy。

### `DigitalTwinEffectReconciler`

```java
Result reconcile(EffectIntent intent,
                 EffectObservation previous,
                 AdapterRegistry.Profile profile,
                 DigitalTwinSnapshot snapshot,
                 long nowEpochMs,
                 int reconcileSequence);
```

sequence 为 1..64；退避 250 ms 指数增长，上限 30 s，`nextReconcileAtEpochMs <= intent.deadlineEpochMs`。方法只调用
`queryStatus`：NOT_APPLIED 返回确认事实；APPLIED 与 fresh VALID Twin report 进入 verifier；UNKNOWN/不可用返回下次
时间；REJECTED/status regression terminal。previous VERIFIED 在 registry resolve/status query 前返回 ALREADY_VERIFIED。
PRODUCTION profile 固定 `PRODUCTION_READBACK_UNAVAILABLE`，不 query debug adapter。

状态：`effect_verification_reconciliation_runtime_wired=false`、`effect_verification_scheduler_wired=false`、
`effect_verification_persistence_wired=false`、`effect_verification_production_readback_wired=false`、
`effect_verification_graph_wired=false`、`production_effect_dispatch_enabled=false`、`hardware_accessed=false`。Req IDs：
`S2-EFF-001`、`S2-TWN-001`、`NV-G-005/006/007`、`DEL-001/003..005`；tracking：`DEV-048`、
`ISSUE-022/026/030/033`。

## Android P3-W08 Compensation/Undo

### `CompensationPlanner`

```java
CompensationPlanner(CapabilityCatalog catalog,
                    List<ReversibleTarget> reversibleTargets);

Plan plan(EffectBatch sourceBatch,
          List<SourceState> sourceStates,
          long nowEpochMs);

static String expectedCompensationDescriptorDigest(EffectIntent sourceIntent);
static String expectedCompensationIdempotencyKey(String compensationPlanId,
                                                 String sourceEffectId);
```

`SourceState` 必须一一覆盖 source batch；非 VERIFIED 只允许 terminal observation 且无 material。VERIFIED state 必须
包含 `BeforeSnapshot`、P3-W06 prepared-before digest 和 caller 构造的新 `EffectIntent`。`BeforeSnapshot` 只包装
immutable VALID `SignalValue`、Context digest/version、capture epoch/elapsed 和 production-trust 标志，并生成
domain-separated digest。`ReversibleTarget` 是显式 capability+catalog-area policy，不能由 source
`reversible=true` 自动推导。

`Plan` 暴露 source/new session/plan/action binding、deadline、digest、reverse-order `Wave` 和 immutable `Step`。
每个 step 保留 defensive source intent/VERIFIED observation/new compensation intent、before/descriptor/step digest。
新 target 必须等于 before absolute scalar，idempotency key 必须绑定 new plan + source Effect；新 intent 固定
`reversible=false`。原 VERIFIED observation 不发生状态转换。

### `UndoService`

```java
List<UndoHandle> issueHandles(CompensationPlanner.Plan plan,
                              List<String> undoIds,
                              long createdAtEpochMs,
                              long requestedTtlMs);

Admission requestUndo(String taskId,
                      String taskIdempotencyKey,
                      CompensationPlanner.Plan plan,
                      List<UndoHandle> handles,
                      GovernanceSnapshot governance,
                      AdapterRegistry.Profile profile,
                      long nowEpochMs);
```

`UndoService` 不是 Android Service。Handle TTL 最多 15 分钟且 cap 到 plan deadline；`handleDigest` 覆盖全部 P1
字段和 state。`GovernanceSnapshot` 只含 principal/Context/Policy/Safety digest、Context version、bounded capability
set 与 trusted/fresh/authorized/safe boolean。ADMITTED 返回新 `GovernedTask` 和 REQUESTED handle copies；同
principal+idempotency+material 返回首次 task，不同 material 冲突。REJECTED 只有 stable reason，无 task。

PRODUCTION 固定 `PRODUCTION_COMPENSATION_UNAVAILABLE`；本接口不 dispatch Effect，不接 Graph/Room/Binder/vehicle
authority。状态：`compensation_planner_defined=true`、`undo_new_governed_task_verified=true`、
`compensation_undo_runtime_wired=false`、`compensation_undo_persistence_wired=false`、
`undo_binder_service_published=false`、`compensation_dispatch_enabled=false`、
`production_compensation_authority_wired=false`、`hardware_accessed=false`。Req IDs：`S2-EFF-001`、
`S2-UX-003`、`S2-SAF-001`、`NV-G-005/006/007`、`DEL-001/003..005`；tracking：`DEV-049`、
`ISSUE-022/023/026/029/030/033`。

## Android P3-W09 Restart recovery

### `GraphRestartReconciler`

```java
Result reconcile(PersistentRun run,
                 Evidence evidence,
                 long nowEpochMs);
```

`PersistentRun` 是 Room recovery projection，不是 AIDL DTO。它固定包含 canonical plan/session UUID、revision、
Graph state、plan/context/manifest SHA-256、created/updated/deadline epoch、1..64 `PersistentNode`、最多 64 个
latest `PersistentEffect` 和最多 64 个 `PersistentCompensation`。构造时必须完成 defensive copy、唯一性、状态码、
时间、digest、node type、idempotency 和 Effect/Compensation source binding 校验。

`Evidence` 只含一个 Governance-revalidated boolean、checkpoint ref -> `CheckpointStatus` 和 Effect ID ->
`EffectDeliveryStatus` 的有界 map。它不接收 JSON、Bundle、Parcelable、raw checkpoint bytes、approval token、车辆
payload 或 adapter object。未知 map key 不被自动信任。

`Result` 含 target Graph state、每个 Node 的 source identity + target state、有序 typed `Directive`、result digest 和
`continuationAllowed`。Directive subject 只能是既有 Node/Effect/Compensation ID。Result digest 使用 domain-separated
canonical field list；它有意不依赖瞬时 source state，因此 EXECUTING 首次恢复与已落盘 WAITING 的同 material reopen
产生相同 digest。`isExecutorDispatchEnabled()` 与 `isProductionAuthorized()` 固定返回 false。

### `DurableGraphRecoveryRepository`

```java
DurableGraphRecoveryRepository(CentralBrainDatabase database);

void persistInitial(GraphRestartReconciler.PersistentRun run);
GraphRestartReconciler.PersistentRun loadRequired(String planId);
ApplyReport applyRecovery(GraphRestartReconciler.Result result,
                          long nowEpochMs);
```

调用顺序固定为：Session 已存在 -> `persistInitial` -> 进程重启后 `loadRequired` -> caller 构造可信 `Evidence` ->
`reconcile` -> `applyRecovery`。`persistInitial` 在一个 transaction 写 Plan/Node/initial Effect observation/
Compensation；`loadRequired` 用 DAO count + bounded query 检查查询完整性，并只选择每个 Effect 的 latest sequence。

`applyRecovery` 重新加载 durable run，比较 plan/session/digest 和完整 Node identity，随后只更新变化的 Plan/Node state。
它不更改 Effect observation 或 Compensation row。审计事件类型固定为 `GRAPH_RESTART_RECONCILED`，detail 只存 result
digest；event ID 由 plan+result digest 确定性派生。命中任意历史同 ID 时必须复验 type/subject/owner/outcome/digest，
相同 digest 重放返回 `auditReplayed=true`，不新增行。`ApplyReport` 暴露 changed row count、audit inserted/replayed 和
result digest，`isExecutorDispatchEnabled()` 固定 false。

线程、时钟和 transaction 调度由 caller 所有。当前 Service/Binder 未发布本接口，应用不得直接创建 repository；
debug probe 仅以 DUMP permission 验证 Room/process-death contract。Req IDs：`S2-SES-001`、`S2-GRF-001`、
`S2-EFF-001`、`S2-SAF-001`、`NV-G-005/006/007`、`DEL-001/003..005`；tracking：`DEV-050`、
`ISSUE-022/023/026/030/033`。

## Android P4-W01 Client2 Session/Event Bridge Interface

### Primary entry

```java
public static Client2ScenarioBridge.SessionConnection openSession(
        Activity activity,
        String uiScenarioAlias,
        String userText,
        ScenarioCallback callback);
```

前置条件：`activity != null`、`callback != null`、alias 命中 12 项 exact map。`userText == null` 转空串，trim 后
截断到 `SessionContract.MAX_UTTERANCE_CHARS`。返回非空表示初始 transport bind 已受理，不表示 Session terminal、
scenario executable 或 Effect dispatched。

### SessionConnection

| 方法 | 返回/语义 | 失败行为 |
| --- | --- | --- |
| `isConnected()` | Session/Event 双 Binder 当前均 ready | closed/null client 返回 false |
| `getSessionHandle()` | defensive copy；open 前可为 null | 不暴露内部 mutable handle |
| `cancel()` | `CANCEL_REASON_USER` owner-scoped cancel | stable error callback，随后关闭 |
| `close()` | 幂等 unregister/unbind/owner release | 不发 terminal，不触发 adapter |

### ScenarioCallback

| Callback | 输入 | HMI 用法 |
| --- | --- | --- |
| `onSessionConnectionChanged` | connected/reconnected | 更新 connection projection，不清空已知 state |
| `onSessionOpened` | copied handle + canonical scenario ID | 建立 session identity；不得显示 raw UUID 到普通 UI |
| `onSessionSnapshot` | validated snapshot | reducer 的 authoritative base state |
| `onSessionEvent` | validated typed RuntimeEvent | 按 `(sessionId, sequence, eventId)` reduce |
| `onSessionReplayComplete` | copied handle + last sequence | 标记 catch-up 完成后再开放依赖完整历史的操作 |
| `onSessionOverflow` | handle + opaque cursor | 显示 recovering；不得解析 cursor |
| `onSessionClosed` | reason + cursor | 停止等待，保留最后 projection 供诊断 |
| `onSessionError` | optional handle + SDK code + bounded message | fail closed；不根据 message 字符串分支 |

`onBridgeStatus/onBridgeReply/onBridgeFailure` 均为 `@Deprecated` default method，只供现有 Smali。new HMI callback
可以只覆盖 typed methods。

### Request mapping

```text
UI alias -> exact alias map -> canonical SessionRequest.scenarioId
UI label -> bounded utterance
source=SOURCE_HMI_BUTTON
seatZone=SEAT_ZONE_DRIVER
locale=zh-CN
deadlineEpochMs=now+10000
clientContextVersion=0
```

alias 映射表以 `Client2ScenarioBridge.scenarioAliases()` 为单一实现源，文档表见完整软件详设。已有 manifest 的
canonical ID 是 `scene.comfort.cold.v1`、`scene.fatigue.assist.v1`、`scene.rest.nap.v1`；其他 ID 当前只可创建
admission-only Session。`SessionContract` 与 frozen AIDL/hash 不变。

### Lifecycle and event sequence

```text
openSession
  -> SessionClient.connect
  -> onConnected(false)
  -> SessionClient.openSession(request, listener)
  -> onSessionOpened
  -> onSessionSnapshot
  -> onSessionEvent(sequence ascending)
  -> onSessionReplayComplete

Binder death
  -> onSessionConnectionChanged(false, false)
  -> reconnect
  -> onSessionConnectionChanged(true, true)
  -> snapshot + cursor replay + duplicate drop
  -> replay complete
```

`onDisconnected` 不能映射为 Session FAILED。只有 stable protocol/transport/subscription error 或 authoritative terminal
snapshot 决定结束。Event V1 terminal cursor 重读限制继续由 `ISSUE-034` 跟踪；bridge 不自行构造 cursor/ACK。

### Compatibility descriptor

```text
Client2ScenarioBridge.submit(
  Activity, String, String, ScenarioCallback) -> boolean
ScenarioCallback.onBridgeStatus(String) -> void
ScenarioCallback.onBridgeReply(String) -> void
ScenarioCallback.onBridgeFailure(String) -> void
```

这些 descriptor 在 P4-W01 不变。每个兼容 submit 替换前一兼容 stream；authoritative replay 后投影 snapshot summary。
`closeLegacySession()` 是过渡释放 API，P4-W02 必须以 maintained Java lifecycle owner 替代静态 owner。

状态：`client2_session_event_primary_api=true`、`client2_legacy_submit_compatibility=true`、
`client2_smali_descriptor_unchanged=true`、`client2_session_android13_arm64_verified=true`、
`scenario_execution_enabled=false`、`hardware_accessed=false`。Req IDs：`S2-UX-001`、`S2-HMI-005`、
`XSC-001/005/006`、`NV-G-003/006/007`、`DEL-001/003/004/005`；tracking：`DEV-051`、`ISSUE-033/034`。

## Android P4-W02 Cockpit HMI State/Reducer/Lifecycle Interface

### Immutable state API

```java
CockpitHmiState CockpitHmiReducer.reduce(
    CockpitHmiState current,
    CockpitHmiReducer.Event event);

CockpitHmiState.Checkpoint CockpitHmiState.checkpoint();
SessionHandle CockpitHmiState.toSessionHandle(); // defensive copy
String CockpitHmiState.renderText();              // projection only
```

`reduce` 是唯一 mutation authority。返回同一对象表示 callback 被 same-session sequence dedup 丢弃；调用方不得自行增加
revision。`renderText` 不得反向参与 reducer decision。Checkpoint 不包含 display content。

### Coordinator bootstrap

```text
MainActivity.onCreate after setContentView
  invoke-static {p0},
    Lcom/centralbrain/client2/CockpitControlCoordinator;->install(Landroid/app/Activity;)V
```

Smali 不持有 View、Session、callback 或 request-in-flight state。`install` 注册 Activity lifecycle、绑定 menu/overlay/button，
从 process-local state 或 private checkpoint 构造初始 immutable projection，然后按 state 决定是否 resume。

### Existing Session resume

```java
public static Client2ScenarioBridge.SessionConnection resumeSession(
    Activity activity,
    String uiScenarioAlias,
    SessionHandle handle,
    String opaqueResumeCursor,
    ScenarioCallback callback);
```

前置条件：activity/callback 非空、alias 命中 exact map、handle 通过 `SessionContract.validateHandle`、cursor 非 null、
长度不超过 256 且无 control character。初次 Binder connected 后调用
`ScenarioClient.observeSession(copiedHandle, cursor, listener)`，不得调用 `openSession` 创建第二个 Session。
成功路径 callback 顺序为 `connectionChanged(true,false) -> sessionOpened(existing) -> snapshot -> replay events ->
replayComplete`。transport death 后仍由 SDK reconnect existing subscription。

### Checkpoint schema v1

| Key | Type | 限制 |
| --- | --- | --- |
| `panel_visible` | boolean | 保留 hide/show |
| `ui_scenario` | String | <=96，UI alias |
| `canonical_scenario` | String | <=96，resume binding |
| `handle_schema/session_id` | int/String | frozen Session V1/canonical UUID |
| `accepted_at/expires_at` | long | handle validity |
| `last_sequence` | long | >=0，HMI projection dedup |
| `resume_cursor` | String | opaque <=256 |

禁止 key/value：utterance、snapshot summary、assistant/model display text、event payload、vehicle value、device identity、
signer、log。过期/非法 checkpoint 原子清空，不能回退为新 Session。

### Lifecycle and render contract

| Trigger | SessionConnection | HMI state | View |
| --- | --- | --- | --- |
| panel hide | 保持 | visibility=HIDDEN，其余不变 | overlay GONE |
| panel show | 保持 | visibility=VISIBLE | 从 state 重绘 |
| new scenario | close previous；open new | clear old identity -> CONNECTING | render connecting |
| Runtime death | SDK reconnect | RECONNECTING -> CONNECTED | 保留 last projection |
| Activity destroy | close | DETACHED/DISCONNECTED，checkpoint | 不再访问旧 View |
| Activity/process recreate | resume existing | RESTORED -> RECONNECTING -> CONNECTED | snapshot/replay 后重绘 |
| terminal snapshot | close by bridge | terminal/CLOSED | 显示 authoritative summary |

状态：`cockpit_hmi_state_reducer_implemented=true`、`cockpit_hmi_lifecycle_owner_java=true`、
`client2_smali_controller_retired=true`、`client2_hmi_checkpoint_text_persisted=false`、
`legacy_text_callback_authoritative=false`、`scenario_execution_enabled=false`、`hardware_accessed=false`。
Req IDs：`S2-UX-001..003`、`S2-HMI-003/005/006`、`APP-004`、`XSC-001/005/006`、
`NV-G-003/006/007`、`DEL-001/003/004/005`；tracking：`DEV-051`、`ISSUE-019/033/034`。

## Android P4-W03 Intent-first Four-stage HMI Interface

### State extensions

```java
enum SurfaceStage { INTENT, PLAN, EXECUTION, RESULT }
enum DeviceDrawer { CLOSED, HVAC, SEAT }

CockpitHmiReducer.Event surfaceSelected(SurfaceStage stage);
CockpitHmiReducer.Event drawerSelected(DeviceDrawer drawer);
```

`CockpitHmiState` owns both values as immutable fields. Initial/process-restored state uses `INTENT/CLOSED`; Activity recreation
within the same process keeps the current values. `SCENARIO_SUBMITTED` atomically selects `PLAN` and closes the drawer.
`PANEL_VISIBILITY(false)` closes the drawer but retains the selected stage and Session projection. No View listener may call
`setVisibility` for a stage/drawer without first reducing the corresponding event.

### Stable View/resource IDs

| Surface | Stable IDs/tags | Contract |
| --- | --- | --- |
| Header | `centralBrainSourceText`, `centralBrainDrivingText`, `centralBrainConnectionText` | Source/driving/connection always visible |
| Stage rail | `centralBrainIntentTab/PlanTab/ExecutionTab/ResultTab` | tags `central_brain_stage_*`; activated state only from reducer |
| Intent | `centralBrainIntentSurface` and four scenario buttons | only `care.fatigue/care.cold/skill.nap/task.home` are primary |
| Plan | `centralBrainPlanSurface`, `centralBrainPlanSummaryText` | Session admission projection; no compiled Plan claim |
| Execution | `centralBrainExecutionSurface`, `centralBrainExecutionSummaryText` | Graph/Effect/readback unavailable projection |
| Result | `centralBrainResultSurface`, `centralBrainResultSummaryText` | no vehicle success without evidence |
| Device drawer | `centralBrainDeviceDrawer`, HVAC/Seat detail/close buttons | placeholder scaffold in P4-W03 |
| Session strip | `centralBrainSessionStrip`, `centralBrainReplyText` | existing bounded Session/Event projection remains visible |

### Geometry/material contract

For the current 1920x1080 Client2 target, `centralBrainPanel` is `624dp x 888dp`, gravity `top|right`, top margin `160dp`,
right margin `32dp`; at density 160 this yields `(1264,160)-(1888,1048)`. The root render region stays full-screen and the
overlay stays initially `GONE`. `central_brain_panel_background` uses `#99EEF2F3`, exactly 60% alpha. Density/rotation variants
are not inferred from this contract and are tracked by `DEV-053`.

### Projection rules

| Evidence | Plan | Execution | Result |
| --- | --- | --- | --- |
| No Session | no request | Graph `NOT WIRED`; Effect `NOT DISPATCHED` | no physical evidence |
| Connecting/admitted Session | normalized alias + admission state | still no dispatch | accepted request only |
| Snapshot/Event summary | bounded authoritative summary | does not imply Effect | does not imply vehicle readback |
| Missing Context/vehicle source | `UNAVAILABLE` | restricted/fail closed | `UNAVAILABLE` |

Status: `cockpit_hmi_four_stage_shell_implemented=true`, `cockpit_hmi_device_drawer_scaffolded=true`,
`cockpit_hvac_surface_implemented=false`, `cockpit_seat_surface_implemented=false`, `scenario_execution_enabled=false`,
`service_dispatch_triggered=false`, `hardware_accessed=false`, `implementation_stage=P4-W04`.
Req IDs: `S2-UX-001..003`, `S2-HMI-001..003/006`, `APP-004`, `XSC-001/005/006`,
`NV-G-003/006/007`, `DEL-001/003/004/005`; tracking: `DEV-051..053`, `ISSUE-019/033/035`.
