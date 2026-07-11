# Central Brain Android Runtime

This is the Android 13 product-oriented build root introduced by R1. It does not replace or modify vendor Android Framework/BSP sources, and it does not embed the Python prototype in an APK.

Req IDs: `APP-004`, `XSC-001`, `XSC-004`, `XSC-005`, `XSC-006`, `NV-F-001`, `NV-P-002`, `DEL-001`, `DEL-003`, `DEL-004`, `DEL-005`.

## Modules

| Module | Artifact | Current responsibility |
| --- | --- | --- |
| `central-brain-sdk` | AAR | Public Android SDK boundary, structured AIDL types and protocol identity |
| `runtime-service` | APK without launcher | Independent user-space runtime process/lifecycle boundary |
| `demo-hmi` | Launcher APK | Source-built integration client for Android hardware testing |

R2A adds compiled, structured production and diagnostic AIDL contracts to `central-brain-sdk`. `runtime-service` remains non-exported and `onBind()` still returns no Binder until R2B implements separate signature-permission Service endpoints. No module requests network, vehicle, device-node, camera, audio, location, or privileged permissions.

The debug variant adds `RuntimeProbeActivity` only under `src/debug`. It is an ADB lifecycle probe protected by the platform `android.permission.DUMP` permission; it starts the non-exported service from inside the runtime package and immediately finishes. The release APK does not contain this activity.

## R2A Protocol Contract

- Production: `com.centralbrain.sdk.production.ICentralBrainRuntime`
- Oneway callback: `com.centralbrain.sdk.production.ICentralBrainTaskCallback`
- Diagnostics: `com.centralbrain.sdk.diagnostics.ICentralBrainDiagnostics`
- Frozen source checksum list: `central-brain-sdk/aidl-api/v1.sha256`
- Detailed semantics: `docs/CENTRAL_BRAIN_ANDROID_AIDL_CONTRACT.md`

Production AIDL contains only typed task fields; JSON, `Bundle`, file descriptors and shared memory are rejected by `tools/check_central_brain_android_aidl_contract.sh`. Diagnostic records are structured, read-only and cursor-paged with a maximum page size of 100.

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

Build success alone proves `contract_defined` only. R1 strict validation passed on the `central_brain_api33_x86_64` Android 13 AVD with system image revision 17, fingerprint `google/sdk_gphone64_x86_64/emu64x:13/TE1A.240213.009/12342917:userdebug/dev-keys`, and a `1920x1080` display. The overall Runtime remains `contract_defined` until R2 adds production Binder contracts and instrumentation evidence.

The local build currently warns that its Android SDK command-line tools understand SDK XML up to version 3 while the installed SDK contains version 4 metadata. The build succeeds, but production CI must align command-line tools and SDK metadata before qualification.
