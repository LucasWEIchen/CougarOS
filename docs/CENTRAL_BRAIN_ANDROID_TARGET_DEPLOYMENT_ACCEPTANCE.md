# Central Brain Android 13 Application-Layer Deployment Acceptance

Version: 0.1
Date: 2026-07-12

## Purpose

This contract validates that the current Central Brain Android deliverables can be installed and exercised on an Android 13/API 33 environment without rebuilding or modifying the vendor Android SDK, AOSP, BSP, system image or vendor partition. It validates application-layer deployment only. It never qualifies a real NPU, Driver/HAL, vehicle bus, Safety Runtime or production model path.

Req IDs: `APP-004`, `XSC-001`, `XSC-004`, `XSC-005`, `XSC-006`, `NV-F-011`, `NV-F-012`, `NV-P-002`, `DEL-001`, `DEL-003`, `DEL-004`, `DEL-005`.

## Artifacts

| Artifact | Role | Target location |
| --- | --- | --- |
| `central-brain-sdk-debug.aar` | App integration SDK | Integrator build input; not installed as a system component |
| `runtime-service-debug.apk` | Runtime, Diagnostic and Governance Services | Normal application package under `/data/app` |
| `demo-hmi-debug.apk` | Binder integration and acceptance HMI | Normal application package under `/data/app` |

The test records SHA-256 for all three artifacts and the shared Runtime/Demo signing certificate digest. Debug artifacts are acceptance artifacts, not production signing deliverables.

## Prerequisites

- One online adb device selected explicitly or exactly one online device.
- Android 13 with `ro.build.version.sdk=33`.
- JDK 17, adb, `apkanalyzer`, `apksigner` and the repository toolchain environment.
- USB debugging or the equivalent vendor-supported adb transport.
- No root, system image source tree, vendor build tree or platform signing key is required.

## Standard Run

From the repository root:

```bash
source env.sh
bash tools/test_central_brain_android_target_deployment.sh --serial <serial>
```

The default path builds the debug artifacts and runs the complete API 33 installation/Binder/governance/readiness gate before collecting deployment evidence.

For an unchanged installation that has already passed the complete gate:

```bash
bash tools/test_central_brain_android_target_deployment.sh \
  --serial <serial> \
  --skip-build \
  --skip-install-gate
```

`--skip-install-gate` is only an iteration optimization. It must not be used as the sole delivery acceptance record.

## Verified Conditions

| Area | Acceptance rule |
| --- | --- |
| OS | Device reports API 33 and a non-empty ABI/fingerprint |
| Package placement | Runtime and Demo resolve to `package:/data/app/.../base.apk` |
| App identity | Both packages have ordinary application UIDs >= 10000 |
| System dependency | Package flags do not require SYSTEM, PRIVILEGED or PERSISTENT |
| Manifest | minSdk 33, targetSdk >= 33 and exactly three Runtime services |
| Service protection | Runtime, Diagnostic and Governance services retain distinct signature permissions |
| Signing | Runtime and Demo debug signer sets match and are reported |
| Network | Runtime and Demo do not request `android.permission.INTERNET` |
| Native payload | AAR/APKs contain no `.so` under native library payload directories |
| Model runtime | Production inference, profile routing, Scheduler/Router and Vendor NPU remain disabled |
| Scope | No vendor/AOSP/BSP modification, Driver/HAL implementation or virtualization work |

Static guard `tools/check_central_brain_android_target_deployment.sh` rejects deployment tooling that introduces system/vendor partition commands. The dynamic test verifies package placement and ordinary UID/flag evidence. `vendor_aosp_bsp_modified=false` describes what this tool performs; it is not a forensic claim about unrelated device history.

## Required Evidence

A passing run emits at least:

```text
target_deployment_preflight_verified=true
android_api_33_verified=true
application_layer_only_verified=true
data_app_install_verified=true
system_app_required=false
privileged_app_required=false
vendor_aosp_bsp_modified=false
system_partition_write_capability=false
internet_permission_requested=false
native_library_payload_present=false
signature_protected_service_count=3
runtime_demo_signer_match=true
production_inference_allowed=false
vendor_npu_provider_available=false
hardware_accessed=false
driver_development_triggered=false
virtualization_development_triggered=false
target_hardware_validated=false
```

It also emits device serial/model/API/ABI/fingerprint, package UIDs and paths, version names, target SDK values, signer SHA-256, and all artifact SHA-256 values.

## Emulator And Device Semantics

- `evidence_scope=api33-emulator-application-layer` means the software/tooling gate passed on an emulator. It must also emit `real_target_application_acceptance_required=true`.
- `evidence_scope=api33-device-application-layer` means the same application-layer checks ran on a device whose `ro.kernel.qemu` is not `1`. It does not establish that the device is the final vehicle target or that its NPU exists.
- `target_hardware_validated=false` is mandatory in both cases. Real NPU, PCIe/IOMMU, Driver/HAL, vendor SDK, thermal/performance and fault-recovery evidence remains under `DRV-GAP-001`.

## Failure Handling

- API other than 33: use the designated Android 13 target or AVD; do not weaken the gate.
- Package not under `/data/app`: investigate vendor deployment policy before changing packaging. Do not silently convert the APK into a system app.
- Signature permissions missing: rebuild Runtime and Demo with the same approved integration signer.
- INTERNET/native payload appears: record an architecture deviation and review the new dependency before acceptance.
- Readiness reports production inference or Vendor NPU available: stop acceptance and reconcile the activation contract, Driver/HAL evidence and ISSUE-024 first.

## Non-Goals

This acceptance does not install framework services, write SELinux policy, modify boot/system/vendor images, use platform signing, access NPU/GPU/vehicle hardware, or approve production inference. It does not replace the later target-specific security, performance, thermal, longevity, rollback and functional-safety qualification.
