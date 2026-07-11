# Central Brain Android Runtime

This is the Android 13 product-oriented build root introduced by R1. It does not replace or modify vendor Android Framework/BSP sources, and it does not embed the Python prototype in an APK.

Req IDs: `APP-004`, `XSC-001`, `XSC-004`, `XSC-005`, `XSC-006`, `NV-F-001`, `NV-P-002`, `DEL-001`, `DEL-003`, `DEL-004`, `DEL-005`.

## Modules

| Module | Artifact | R1 responsibility |
| --- | --- | --- |
| `central-brain-sdk` | AAR | Public Android SDK ownership boundary and version identity |
| `runtime-service` | APK without launcher | Independent user-space runtime process/lifecycle boundary |
| `demo-hmi` | Launcher APK | Source-built integration client for Android hardware testing |

R1 intentionally has no AIDL. `runtime-service` is non-exported and `onBind()` returns no Binder until R2 introduces separate typed production and diagnostic contracts. No module requests network, vehicle, device-node, camera, audio, location, or privileged permissions.

The debug variant adds `RuntimeProbeActivity` only under `src/debug`. It is an ADB lifecycle probe protected by the platform `android.permission.DUMP` permission; it starts the non-exported service from inside the runtime package and immediately finishes. The release APK does not contain this activity.

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

The check installs both APKs, invokes the DUMP-protected debug probe, verifies the non-exported service process, launches Demo HMI, checks the resumed Activity and UI text, and reports hardware/Driver/HAL/virtualization boundaries.

The existing hand-built Android Console and Client2 reverse-demo APK remain separate compatibility/test artifacts. They are not copied into this Gradle project.

R1 build success proves `contract_defined` only. Promotion to `android_integrated` requires installation and runtime evidence from an API 33 device or emulator; the current workspace only has an API 36 AVD.

The local build currently warns that its Android SDK command-line tools understand SDK XML up to version 3 while the installed SDK contains version 4 metadata. The build succeeds, but production CI must align command-line tools and SDK metadata before qualification.
