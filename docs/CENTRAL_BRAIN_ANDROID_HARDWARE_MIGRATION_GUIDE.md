# Central Brain Android 13 Hardware Migration Guide

## Audience And Scope

This handoff is for cockpit-domain engineers integrating the Central Brain on
an existing Android 13 image without vendor Android SDK, AOSP or BSP source.
The current delivery is application-layer software. It installs ordinary APKs
under `/data/app`, uses public Binder/PackageManager APIs and keeps every real
NPU, VHAL, vehicle bus and production subsystem behind an empty interface.

Req IDs: `APP-004`, `XSC-001`, `XSC-004`, `XSC-005`, `XSC-006`, `NV-F-001`,
`NV-F-011`, `NV-F-012`, `NV-G-003`, `NV-G-006`, `NV-G-007`, `NV-P-002`,
`DEL-001`, `DEL-003`, `DEL-004`, `DEL-005`.

## Delivered Modules

| Artifact | Language | Role |
| --- | --- | --- |
| `central-brain-sdk-debug.aar` | Java/AIDL | typed client, Parcelable task contract, callback/cancel/death handling |
| `runtime-service-debug.apk` | Java, Room, AIDL | signature-protected Runtime/Governance/Diagnostic services and deterministic software stub |
| `demo-hmi-debug.apk` | Java | maintained reference client and Binder lifecycle test host |
| `client2-central-brain.debug.apk` | original APK resources/Smali plus Java secondary dex | cockpit overlay and 12 scenario buttons using the public SDK |

There is no C/C++ payload in the current artifacts. A future vendor NPU bridge
may use C or C++ only after a published vendor SDK/ABI requires it. That bridge
must implement the existing Model Provider boundary and cannot be added by
guessing device nodes or private ioctls.

## Runtime Call Path

```text
Client2 or maintained HMI
  -> CentralBrainClient (public SDK)
  -> signature-protected typed Binder
  -> caller package/current-signer capability policy
  -> Runtime task admission and durable checkpoint
  -> deterministic software reply

Blocked production branches:
  -> Effect/VHAL adapter: empty
  -> Vendor NPU provider: empty
  -> Event broker/callback runtime: empty
  -> encrypted durable Memory: empty
  -> Skill/Governance production composition: empty
```

Client applications never call NPU, VHAL or a Python/Ollama endpoint directly.
The current deterministic reply proves the Android protocol and lifecycle, not
model inference or vehicle control.

## Target Inputs

Complete a copy of `contracts/target-inputs.example.json` before a physical
device review. Unknown fields remain `null`; do not infer owners, signing rules
or vendor interfaces from emulator behavior.

The minimum decisions are:

1. Application installation, rollback, signing and evidence owners.
2. Whether ordinary `/data/app` packages are allowed by MDM/device policy.
3. One signing strategy for Runtime, Demo and patched Client2 so Android's
   signature permission is grantable.
4. Whether the new Client2 signer remains trusted by RenderService/vendor
   allowlists.
5. Whether a system/privileged placement or SELinux change is actually needed.
6. Published NPU/VHAL/Safety/Event contracts and their ABI owners, if present.

The current software does not require system/privileged placement. If the
target owner requires it, that is a new deployment decision and cannot be
implemented without the relevant image/signing/SELinux authority.

## Build The Handoff Bundle

From the repository root:

```bash
bash tools/package_central_brain_android_delivery.sh
```

Use `--skip-build` only after all four debug artifacts are current. The output
contains artifacts, contracts, migration documents, self-verification tooling,
`DELIVERY-MANIFEST.json`, `SHA256SUMS` and a tar archive under
`builds/central-brain-android-delivery/`.

Validate the unpacked bundle:

```bash
python3 tools/central_brain_android_delivery.py verify \
  --bundle-dir builds/central-brain-android-delivery/central-brain-android13-handoff
```

Verification checks every hash/size, APK package name, signer cohort, no-native
payload, Client2 `classes2.dex`, software-handoff status and all seven preserved
blockers.

The manifest and hashes prove internal consistency, not publisher authenticity.
The release owner must distribute the archive SHA-256 through a trusted channel
and the integrator must compare it before unpacking or installing the bundle.

## Signing Gate

The generated package is debug-signed and is not a production release. Runtime,
Demo and Client2 currently share one debug signer because Runtime's Binder
permission has `signature` protection.

Before production installation, the target signing owner must choose one of:

- Re-sign all three APKs with one approved application certificate and confirm
  RenderService/vendor trust for the patched Client2.
- Supply a vendor-managed deployment design that preserves equivalent Binder
  permission and package/current-signer policy guarantees.

Do not mix signers. Do not silently uninstall the original Client2 to bypass an
update mismatch. The installer performs signer preflight and stops before any
installation when an installed package has a different signer.

## Dry Run And Install

The host needs Python 3, Android `adb`, Android build-tools `aapt`/`apksigner`
and a Java runtime. Put the tools on `PATH`, or set `ADB`, `AAPT`, `APKSIGNER`
and `JAVA_HOME`.
The installer resolves the newest `apksigner` below `ANDROID_HOME` when one is
not explicitly selected and fails before package installation when any host
tool is unavailable.

The bundle installer defaults to dry-run:

```bash
bash tools/install_central_brain_android_delivery.sh \
  --bundle-dir <bundle-dir> \
  --serial <adb-serial>
```

For a disposable test device only, debug-signed installation requires two
explicit switches:

```bash
bash tools/install_central_brain_android_delivery.sh \
  --bundle-dir <bundle-dir> \
  --serial <adb-serial> \
  --execute \
  --allow-debug-signing
```

Install order is fixed:

1. Runtime Service APK, which defines signature permissions.
2. Demo HMI reference client.
3. Patched Client2 demo client.

The installer never runs `adb root`, remount, fastboot, partition writes or
automatic uninstall. It requires API 33, verifies existing package signers
before the first install and uses only `adb install -r`.

## Acceptance Commands

On an Android 13 source-checkout test environment:

```bash
bash tools/test_central_brain_android_target_deployment.sh \
  --serial <adb-serial> \
  --skip-build

bash tools/test_client2_central_brain_recovery.sh \
  --serial <adb-serial> \
  --skip-build \
  --require-api-33
```

The first command distinguishes emulator from physical-device application
evidence. A physical `/data/app` pass still does not validate NPU, VHAL or
production subsystem activation.

## Rollback Plan

Rollback is owner-controlled and must be rehearsed before installation:

1. Record installed package versions, signer digests and APK backup references.
2. Force-stop Client2 and Demo; do not delete app data unless the data owner
   explicitly approves it.
3. Restore client packages in reverse dependency order, then restore Runtime.
4. A previous APK can be restored only when its signer/version policy permits
   an update; otherwise the signing owner must provide an approved migration.
5. Re-run signature-permission, Binder and UI smoke checks.

No automatic rollback/uninstall script is delivered because a safe rollback
requires target-owned previous APKs, signer lineage and data-retention policy.

## Diagnostics

Collect these application-layer records without root:

```bash
adb -s <serial> shell dumpsys package com.centralbrain.runtime
adb -s <serial> shell dumpsys package com.tuanjie.urasclient2
adb -s <serial> shell dumpsys activity service \
  com.centralbrain.runtime/.CentralBrainRuntimeService
adb -s <serial> logcat -d \
  CbClient2Binder:I CentralBrainRuntime:I CentralBrainGovernance:I '*:S'
```

Expected blocked markers include `production_activation_allowed=false`,
`vendor_npu_provider_available=false` and `target_hardware_validated=false`.
Unexpected hardware access or a missing signature permission is a failed
handoff, not a reason to bypass policy.

## Blocker Closure

| Blocker | Evidence required before closure |
| --- | --- |
| `TARGET_SYSTEM_INTEGRATION_OWNER_UNRESOLVED` | named owner and approved package placement/signing/MDM/SELinux decision |
| `PRODUCTION_EFFECT_DELIVERY_BLOCKED` | approved VHAL/SOA adapter, trusted state, idempotency and fault/rollback evidence |
| `PRODUCTION_MODEL_RUNTIME_BLOCKED` | vendor NPU SDK/ABI, memory/lifecycle/cancel/fault contract and physical smoke result |
| `PRODUCTION_EVENT_RUNTIME_BLOCKED` | durable publisher sequence, callback/broker owner and middleware evidence |
| `PRODUCTION_MEMORY_RUNTIME_BLOCKED` | encrypted store, key lifecycle, consent/revocation and trusted retention clock |
| `PRODUCTION_SKILL_GOVERNANCE_BLOCKED` | artifact verifier, lifecycle/rollback, sandbox, authority, audit and dispatcher |
| `TARGET_HARDWARE_NOT_VALIDATED` | physical Android 13 application pass plus all approved subsystem/hardware qualifications |

R7D marks the Android software handoff package ready. It does not close any of
these seven external-evidence blockers and does not claim production readiness.
