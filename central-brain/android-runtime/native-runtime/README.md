# Central Brain Native Runtime

This Android library is the C/Java boundary for the black-box Android 13
engineering track. It produces an AAR containing
`libcentral_brain_native.so` for `arm64-v8a` and `x86_64`.

The C11 core implements ABI validation, bounded slot leases, lifecycle and a
fail-closed health snapshot. It does not perform inference, access hardware,
load vendor libraries or call Android framework APIs. Vendor NPU and VHAL remain
unavailable integration slots.

The Java API is `com.centralbrain.nativebridge.NativeRuntime`. JNI registration
is performed in `JNI_OnLoad` with `RegisterNatives`; the C core does not retain
Java references.

Run the host contract test from the repository root:

```bash
bash tools/test_central_brain_native_runtime_host.sh
```

Build the Android AAR with the full runtime build:

```bash
bash tools/build_central_brain_android_runtime.sh
```

Re-run the ABI, exported-symbol and ELF hardening checks against an existing
AAR:

```bash
bash tools/verify_central_brain_native_runtime_aar.sh
```

See `docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md` for the stable ABI and ownership
contract.
