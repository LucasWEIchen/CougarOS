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

API 33 evidence is part of `tools/install_central_brain_android_runtime.sh --require-api-33` and `tools/test_central_brain_android_capability_policy.sh --require-api-33`. R3 is complete at `R3_TRUSTED_GOVERNANCE` / `android_integrated`, meaning the Android boundary and emulator behavior are integrated. It does not mean production Safety/Vehicle data, approval grant authority, durable recovery, real action dispatch or target hardware are complete; those remain R4 and target-platform work.

## R4A Room Durable Schema

`runtime-service` now compiles AndroidX Room `2.8.4` and exports `CentralBrainDatabase` schema version 2. The app-private WAL database owns eight tables: session, task, checkpoint, pending effect, effect outbox, approval, audit event and event cursor. Unique idempotency/owner indexes and task/effect foreign keys are part of the exported schema. Payload/checkpoint/outbox/audit content is represented by digests; R4A does not persist raw utterances, model output or vehicle frames.

`MIGRATION_1_2` upgrades the prior minimal task/approval shape without destructive fallback. A DUMP-protected debug-only migration probe creates an isolated v1 database, inserts legacy rows, opens it through Room, and verifies schema version 2, eight tables, WAL plus preserved task/approval data. The probe never opens or deletes the production database and is absent from release.

R4A defines and validates storage ownership only. Production Runtime/Governance Services do not open the database yet, `durable_dispatch_enabled=false`, and restart recovery is not claimed. R4B1 now adds transactional task admission below; later R4B increments wire task lifecycle and approval writes. R4C will add restart recovery and pending-effect/outbox processing while keeping real hardware dispatch disabled.

## R4B1 Durable Task Admission

`DurablePrincipalFingerprint` hashes Android user serial plus canonical package/current-signer pairs with a domain-separated, length-framed SHA-256 encoding. It deliberately excludes the ephemeral UID so ownership survives package UID reassignment, but live Binder identity and capability authorization are still required before computing it. Only the fingerprint is persisted; signer evidence is not copied into Room.

`DurableTaskRepository.admit` performs owner+idempotency lookup, task creation and `TASK_ACCEPTED` audit insertion in one Room transaction. An exact replay returns the original task without a second row or acceptance audit. Reusing the same owner/key for different session, client request or payload digest raises an explicit conflict; another owner may use the same key independently. The repository stores structured metadata and a payload digest, never the raw utterance.

The DUMP-protected debug repository probe closes and reopens its isolated database before replay, then verifies two owners produce exactly two tasks and two acceptance audits. R4B1 intentionally reports `runtime_repository_wired=false`: production Services still use the R3 in-memory path until R4B2, and no pending effect, outbox dispatch or hardware access is enabled.

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
