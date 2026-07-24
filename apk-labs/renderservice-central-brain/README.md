# RenderService Unity-native HVAC Patch

This maintained work package patches the closed-source RenderService launcher
Addressables bundle without modifying the vendor Android system image.

The patch keeps the original Unity `26.5°C` RawImage as the default state,
adds native TextMeshPro `28.0°C` states for both zones, and binds the existing
Unity decrease/increase buttons to deterministic `GameObject.SetActive`
transitions. Client2 dispatches the normal TuanjieView touch path after an
allowlisted HVAC effect; it no longer draws an Android temperature TextView
over the Unity surface.

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
only Unity-native HMI state used for the hardware demonstration.
