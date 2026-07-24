# RenderService Dynamic HVAC, Orbit and Fidelity Patch

This maintained work package patches the closed-source RenderService launcher
Addressables bundle without modifying the vendor Android system image.

The patch replaces the fixed Unity temperature textures with two active
TextMeshPro objects. Client2 updates them through RenderService
`c2sSendMessage(..., "SetText", value)` calls, using a bounded 18.0-30.0°C
state machine with 0.5°C steps and a 26.5°C default. The cloned Unity material
uses the existing Urbanist typeface with a thinner face dilation so the
dynamic value remains visually consistent with the launcher.

The patch also binds the Unity pan recognizer to RenderService display 1 and
disables its EventSystem raycast precondition. Client2 requests a 1.5 render
scale from `TuanjieView`; this is an application-level supersampling request,
not a device display or engine-quality guarantee.

Build:

```bash
source env.sh
apk-labs/renderservice-central-brain/scripts/build_debug_apk.sh
```

Output:

```text
builds/renderservice-central-brain/signed/renderservice-central-brain.debug.apk
```

Real vehicle HVAC actuation remains a reserved interface. This package changes
only Unity-native HMI state and launcher input configuration used for the
hardware demonstration.
