# Central Brain Android 13 Application Handoff

This directory defines the R7D software handoff for an existing Android 13
cockpit image. It packages application-layer artifacts only. It does not modify
vendor Android, AOSP, BSP, system or vendor partitions.

Req IDs: `APP-004`, `XSC-001`, `XSC-004`, `XSC-005`, `XSC-006`, `NV-F-001`,
`NV-F-011`, `NV-F-012`, `NV-G-003`, `NV-G-006`, `NV-G-007`, `NV-P-002`,
`DEL-001`, `DEL-003`, `DEL-004`, `DEL-005`.

## Package

From the repository root:

```bash
bash tools/package_central_brain_android_delivery.sh
```

The command builds and verifies an unpacked bundle plus a normalized tar
archive under `builds/central-brain-android-delivery/`. The bundle contains four
artifacts, their hashes and signer metadata, target-input template, acceptance
contracts, migration guidance, self-verification/install tools and the two
source-checkout acceptance scripts.

## Verify

```bash
python3 tools/central_brain_android_delivery.py verify \
  --bundle-dir builds/central-brain-android-delivery/central-brain-android13-handoff
```

## Device Preflight

The host needs Python 3, Android `adb`, Android build-tools `aapt`/`apksigner`
and a Java runtime. Put them on `PATH`, or set `ADB`, `AAPT`, `APKSIGNER` and
`JAVA_HOME`.

```bash
bash tools/install_central_brain_android_delivery.sh \
  --bundle-dir <bundle-dir> \
  --serial <adb-serial>
```

This is a dry-run. It verifies Android API 33 and all existing package signers
before any install. A disposable test-device install additionally requires
`--execute --allow-debug-signing`.

The generated hashes prove bundle consistency, not publisher authenticity.
Obtain the archive SHA-256 through a trusted release channel before unpacking or
installing the bundle.

The target deployment and Client2 recovery scripts expect the complete
repository checkout, Gradle modules, Client2 patch outputs and related tools.
They are copied into the bundle for handoff and traceability; the bundle is not
a self-contained build tree.

## Handoff Boundary

`software_handoff_ready=true` means the debug application bundle is internally
consistent and repeatably accepted on the API 33 emulator. It does not mean
production signing, system integration ownership, physical target validation,
NPU/VHAL access or production Effect/Model/Event/Memory/Skill activation is
complete. Those seven blockers remain explicit empty integration slots in the
delivery profile.
