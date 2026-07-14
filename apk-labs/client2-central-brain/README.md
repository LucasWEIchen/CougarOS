# Client2 Central Brain APK Reverse Demo

This is the APK-level test project for the Central Brain in-cockpit demo.
It is intentionally built on the decoded `client2` APK resources and smali
instead of a normal Android source tree.

## Scope

- Source APK: `apks/original/client2_20260306_170923.apk`
- Decoded baseline: `reverse/client2/apktool`
- Working copy: `builds/client2-central-brain/workdir`
- Output APK: `builds/client2-central-brain/signed/client2-central-brain.debug.apk`

This project keeps `apks/original` and `reverse/client2/apktool` as baselines.
Every build copies the decoded APK into a generated workdir, applies patches,
then rebuilds and signs a debug APK.

## Architecture Traceability

| Area | Req ID | Handling |
| --- | --- | --- |
| Android demo delivery | `DEL-001`, `DEL-003` | Produces a debuggable APK for emulator/device validation. |
| Platform difference visibility | `DEL-004` | Documents that this is an APK patch path, not a production system-service path. |
| AI SDK entry point | `APP-004`, `XSC-001` | The right panel is reserved for AI SDK/Agent state and must not call NPU directly. |
| Typed Binder boundary | `XSC-005`, `XSC-006`, `NV-G-006`, `NV-P-002` | Client2 uses the public SDK/AIDL contract, Runtime package visibility, signature permission and package/current-signer capability policy. |
| Uni Info Bus / SOA / Governance | `XSC-002`, `XSC-003`, `XSC-005`, `XSC-006` | Runtime remains the single app-facing ingress; Client2 does not bypass it for model or vehicle access. |

## Commands

Build the patched APK:

```bash
bash tools/build_client2_central_brain_demo.sh
```

Verify the project and latest signed output:

```bash
bash tools/check_client2_central_brain_demo.sh
```

Install to the currently selected Android device or emulator:

```bash
bash tools/install_client2_central_brain_demo.sh
```

If Android rejects an already installed `com.tuanjie.urasclient2` because its
signer differs, an authorized test owner may explicitly replace it. This
removes the existing package and its app data; no replacement occurs without
the flag:

```bash
bash tools/install_client2_central_brain_demo.sh \
  --serial <serial> \
  --replace-conflicting-client2
```

Run the typed Binder/UI acceptance on an Android 13 emulator:

```bash
bash tools/test_client2_central_brain_binder.sh --require-api-33
```

The Binder test accepts the same `--replace-conflicting-client2` option for an
owner-approved signer migration. Other install failures remain fail-closed.

Run the R7C fault/recovery matrix after the happy-path check:

```bash
bash tools/test_client2_central_brain_recovery.sh --require-api-33
```

## Current Patch

The layout patch keeps the original render hierarchy full-screen and adds a
right-side overlay in the existing root `FrameLayout`:

```text
Activity
├── full-screen: original TuanjieView containers `view1`, `view2`, `view3`
└── floating overlay: translucent right 1/3 Central Brain demo panel
```

The overlay does not resize the vehicle scene. Empty space outside the panel
continues to pass input to Client2, while the panel consumes touches over its
own surface. The scrollable control area groups 12 stable scenario IDs under
task service, context/growth, and safety/runtime. Each button creates a typed
`AgentTaskRequest` through `CentralBrainClient`; asynchronous Binder callbacks
update the response area. The SDK, AIDL parcelables and a narrow Client2 bridge
are compiled into `classes2.dex`. The APK requests no network permission and
contains no direct HTTP fallback.

The debug build is deliberately signed with the same Gradle debug signer as
the Runtime APK. Runtime still applies default-deny package/current-signer
capability policy and grants Client2 only protocol read plus submit/status/cancel
for its own tasks.

## Boundaries

- No original APK is modified.
- No RenderService or Unity asset is modified.
- No Driver/HAL, PCIe NPU, vendor SDK, device node, shared memory, or
  virtualization code is accessed.
- Rebuilt APKs use the Runtime-compatible local debug key. This invalidates any
  trust tied to the original Client2 signer; target-device RenderService or
  vendor allowlist compatibility remains an explicit integration risk.
