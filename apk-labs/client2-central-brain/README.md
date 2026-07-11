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
| Uni Info Bus / SOA / Governance | `XSC-002`, `XSC-003`, `XSC-005`, `XSC-006` | Future interactive calls must go through the Central Brain prototype boundary. |

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
task service, context/growth, and safety/runtime. Each button calls
`POST /agent/scenarios/run`; the response area renders compact module and
policy evidence from the Python prototype.

## Boundaries

- No original APK is modified.
- No RenderService or Unity asset is modified.
- No Driver/HAL, PCIe NPU, vendor SDK, device node, shared memory, or
  virtualization code is accessed.
- Rebuilt APKs are signed with the local debug key. If RenderService enforces
  original signature trust later, Client2 and RenderService signing must be
  re-evaluated together.
