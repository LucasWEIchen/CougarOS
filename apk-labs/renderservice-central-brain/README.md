# RenderService Vendor Baseline Preservation

This work package protects the original shared RenderService used by Client and
Client2. The active delivery path no longer rewrites the Unity Addressables
bundle, changes the render scale, or replaces the vendor touch recognizer.

Source baseline:

```text
apks/original/service_20260306_171242.apk
SHA-256 a24fbb471399e152c172455afbaee92e311f24f63910b263a577d58d59d7c633
```

Build the immutable passthrough artifact:

```bash
source env.sh
apk-labs/renderservice-central-brain/scripts/build_debug_apk.sh
```

Output:

```text
builds/renderservice-central-brain/vendor-baseline/renderservice-vendor-baseline.apk
```

The output must be byte-identical to the original APK and retain its vendor
signer. AIOS HMI behavior is implemented in the Client2 Android overlay and
Central Brain Runtime. It must not alter Client/Client2 Tuanjie render-session
indices, Unity input routing, or vendor assets.

The earlier dynamic-HVAC Unity patch remains source history only and is not
called by any active build or installation path. Native HVAC actuation stays a
reserved vehicle adapter interface until vendor source-level integration is
available.
