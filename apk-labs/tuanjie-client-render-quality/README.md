# Tuanjie Client Render Quality

This project rebuilds the approved original `com.tuanjie.urasclient` APK with one
bounded display-quality change. Immediately after the original `TuanjieView` is
added to its original container, `MainActivity` calls the vendor public API
`setRenderScale(1.25f)`.

The same patcher is called by the Client2 build for display index 1. Client and
Client2 therefore use one policy implementation and one approved scale value.

The Android Surface remains 1920x1080. RenderService renders display index 0 at
2400x1350 and downsamples into that Surface. The patch does not modify the
RenderService APK, Unity assets, layout hierarchy, Surface size, display index,
frame interval, touch listener or vehicle UI behavior.

The client-side `onServiceConnected()` callback re-arms only the existing
`mNeedSetRenderScale` flag. A restarted RenderService therefore receives the
same approved scale again after the original Surface registration; no private
service field or Binder transaction is accessed by the patch.

Build:

```bash
source env.sh
bash apk-labs/tuanjie-client-render-quality/scripts/build_client_debug_apk.sh
```

Output:

```text
builds/client-render-quality/signed/client-render-quality.debug.apk
```

This is a debug-signed validation artifact. GPU frame pacing, temperature and
long-duration stability must pass before the policy is approved for production.

## Selection rationale

An original-versus-AIOS screenshot comparison showed that both APKs already
created 1920x1080 buffers and that the overlay did not lower the native Surface
resolution. A 1.5 scale improved edge quality but reduced the observed update
rate to about 20 FPS. The selected 1.25 profile retained a 1920x1080 buffer,
produced a 2400x1350 internal target, and measured a 34.975 ms median frame
interval (28.59 FPS) with a 50.210 ms p95 on the current validation device.
