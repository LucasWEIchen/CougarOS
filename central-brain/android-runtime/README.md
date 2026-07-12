# Central Brain Android Runtime

This is the Android 13 product-oriented build root introduced by R1. It does not replace or modify vendor Android Framework/BSP sources, and it does not embed the Python prototype in an APK.

Req IDs: `APP-004`, `XSC-001`, `XSC-004`, `XSC-005`, `XSC-006`, `NV-F-001`, `NV-P-002`, `DEL-001`, `DEL-003`, `DEL-004`, `DEL-005`.

## Modules

| Module | Artifact | Current responsibility |
| --- | --- | --- |
| `central-brain-sdk` | AAR | Public typed task/Governance clients, structured AIDL types, callback bridge and protocol identity |
| `runtime-service` | APK without launcher | Signature-protected task/diagnostic/Governance Binders, bounded supervisors and deterministic hardware-free runtime |
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
