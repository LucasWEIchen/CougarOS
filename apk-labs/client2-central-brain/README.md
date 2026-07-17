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
| Typed Binder boundary | `XSC-005`, `XSC-006`, `NV-G-006`, `NV-P-002` | Client2 uses the public SDK/AIDL contract, Runtime package visibility, signature permission and package/current-signer capability policy. |
| Uni Info Bus / SOA / Governance | `XSC-002`, `XSC-003`, `XSC-005`, `XSC-006` | Runtime remains the single app-facing ingress; Client2 does not bypass it for model or vehicle access. |

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

If Android rejects an already installed `com.tuanjie.urasclient2` because its
signer differs, an authorized test owner may explicitly replace it. This
removes the existing package and its app data; no replacement occurs without
the flag:

```bash
bash tools/install_client2_central_brain_demo.sh \
  --serial <serial> \
  --replace-conflicting-client2
```

Run the typed Binder/UI acceptance on an Android 13 emulator:

```bash
bash tools/test_client2_central_brain_binder.sh --require-api-33
```

The Binder test accepts the same `--replace-conflicting-client2` option for an
owner-approved signer migration. Other install failures remain fail-closed.

Run the R7C fault/recovery matrix after the happy-path check:

```bash
bash tools/test_client2_central_brain_recovery.sh --require-api-33
```

## Current Patch

The layout patch keeps the original render hierarchy full-screen and adds a
right-side overlay in the existing root `FrameLayout`:

```text
Activity
├── full-screen: original TuanjieView containers `view1`, `view2`, `view3`
├── floating overlay: 624x888 translucent Central Brain panel in the 1920x1080 safe frame
└── bottom trigger rail: transparent target over the rendered navigation icon
```

The overlay does not resize the vehicle scene. One navigation-target click
shows the panel; a second click or a click outside the panel hides it. The panel
consumes touches over its own surface so its controls do not dismiss it. The
primary Intent surface exposes four natural scenes and the stage rail exposes
Intent, Plan, Execution and Result. HVAC/Seat remain secondary detail drawers;
HVAC now contains the P4-W04 governed manual control surface and Seat remains a placeholder.
Each scene creates a typed
Session through `CockpitControlCoordinator -> Client2ScenarioBridge.openSession
-> SessionClient`; snapshot, typed events and authoritative cursor replay are
reduced into immutable `CockpitHmiState` before rendering. The bridge
`submit(...)` descriptor remains only as an unused compatibility wrapper.
The SDK, AIDL parcelables and a narrow Client2 bridge are compiled into
`classes2.dex`. The APK requests no network permission and contains no direct
HTTP fallback.

The four visible XML scenario tags remain stable two-segment UI aliases. A 13-entry exact bridge
compatibility allowlist still maps all supported aliases to qualified Session IDs before Runtime admission; unknown
aliases fail before binding and the frozen Session V1 validation is not relaxed.
Runtime process death reconnects the active stream, replays the owner-scoped
snapshot/history and drops already delivered event sequences.

The previous Smali panel controller has been removed. MainActivity contains only
a one-line bootstrap to the maintained Java coordinator in `classes2.dex`.
The coordinator owns View binding, Session replacement and Activity lifecycle.
On recreation it resumes the existing Session by handle/cursor. Its private
checkpoint saves only panel state, aliases, handle metadata, cursor and last
sequence; it never persists user/model/display text.

The bottom navigation is drawn by the Tuanjie render surface and has no Android
`View` callback. The patch therefore uses a transparent, accessibility-visible
touch target aligned to the current navigation location. This is verified on
the 1920x1080 API 33 target and remains a closed-source geometry dependency;
supported display variants need their own coordinate/accessibility regression.

The debug build is deliberately signed with the same Gradle debug signer as
the Runtime APK. Runtime still applies default-deny package/current-signer
capability policy and grants Client2 only protocol read plus submit/status/cancel
for its own tasks.

## Cockpit HVAC/Seat Control Loop

P4-W03 replaced the 12-button primary test console with the intent-first shell.
It is not a vehicle-control completion claim. The current overlay contains:

```text
Intent | Plan | Execution | Result | secondary HVAC/Seat detail drawer
```

The HVAC surface exposes power, zone, temperature, fan, AUTO, A/C, SYNC,
airflow and comfort presets. Immutable `HvacControlIntent` and `CockpitHvacState`
separate desired revision, request state, reported value, source, quality and
Effect state. The Coordinator coalesces continuous changes for 300 ms and opens
one `scene.manual.hvac.adjust.v1` governed Session; Session admission is shown as
`REQUESTED`, never as vehicle execution or readback. The Seat surface will expose zone, heating,
ventilation, massage, recline and upright/comfort/rest presets with driving-state
restrictions. The execution surface will show desired versus reported values,
plan/effect progress, approval, partial failure, retry, undo and recovery.

Maintained Java code in `classes2.dex` now owns immutable HMI state, reducer,
rendering and SDK coordination. Smali is only the one-line install bootstrap.
Manual controls and AI scenarios both submit through the Scenario/Session SDK;
neither the View nor the bridge may call a simulated or target vehicle adapter
directly. Runtime Governance/Graph/Effect wiring remains a later work package.

Without real vehicle signals, only debug/test builds may use the Android Digital
Twin and simulated HVAC/Seat adapters. The panel must continuously display
`SIMULATED`; a release/production build with no target adapter displays
`UNAVAILABLE` and disables controls. Current flags remain:

```text
cockpit_hvac_surface_implemented=true
cockpit_hvac_reducer_owned=true
cockpit_hvac_debounce_ms=300
cockpit_hvac_governed_manual_session=true
cockpit_hvac_reported_readback_available=false
hvac_manual_typed_parameter_field=false
cockpit_seat_surface_implemented=false
cockpit_demo_control_loop_implemented=false
real_vehicle_effect_adapter_available=false
```

P4-W01 through P4-W04 are complete. The primary bridge exposes typed Session handle,
snapshot, event, replay, overflow, close and error callbacks. The Java coordinator
reduces these callbacks, owns lifecycle and resumes a text-free checkpoint after
Client2 process restart. Android 13 ARM64 acceptance covers Runtime/Client2 process
death, duplicate suppression, hidden-state restore, menu reopen, exact 1920x1080
safe-frame rendering, four stage selection, HVAC controls, debounce and governed
manual Session admission. P4-W05 is the next work package and will implement the
Seat control surface without enabling vehicle or production Effect dispatch:

```text
client2_session_event_primary_api=true
client2_session_event_typed_callback=true
client2_scenario_alias_map_count=13
client2_session_reconnect_replay_verified=true
client2_session_duplicate_event_suppressed=true
cockpit_hmi_state_reducer_implemented=true
cockpit_hmi_lifecycle_owner_java=true
cockpit_hmi_four_stage_shell_implemented=true
cockpit_hmi_intent_first_primary=true
cockpit_hmi_safe_frame_1920x1080_verified=true
cockpit_hmi_material_alpha=0.60
cockpit_hmi_device_drawer_scaffolded=true
cockpit_hvac_surface_implemented=true
cockpit_hvac_reducer_owned=true
cockpit_hvac_debounce_ms=300
cockpit_hvac_governed_manual_session=true
cockpit_hvac_desired_reported_separation_verified=true
cockpit_hvac_reported_readback_available=false
cockpit_hvac_verified_before_readback=false
hvac_manual_typed_parameter_field=false
cockpit_seat_surface_implemented=false
client2_smali_controller_retired=true
client2_hmi_checkpoint_resume_verified=true
client2_hmi_hidden_state_recreation_verified=true
client2_hmi_checkpoint_text_persisted=false
legacy_text_callback_authoritative=false
cockpit_demo_control_loop_implemented=false
implementation_stage=P4-W05
```

The implementation plan, class/file map and acceptance matrix are maintained in
[`docs/CENTRAL_BRAIN_COCKPIT_HMI_CONTROL_LOOP_PLAN.md`](../../docs/CENTRAL_BRAIN_COCKPIT_HMI_CONTROL_LOOP_PLAN.md).

## Boundaries

- No original APK is modified.
- No RenderService or Unity asset is modified.
- No Driver/HAL, PCIe NPU, vendor SDK, device node, shared memory, or
  virtualization code is accessed.
- Rebuilt APKs use the Runtime-compatible local debug key. This invalidates any
  trust tied to the original Client2 signer; target-device RenderService or
  vendor allowlist compatibility remains an explicit integration risk.
- Frozen Session V1 has no typed parameter field or `HMI_CONTROL` source. P4-W04
  carries a strict canonical `HVAC1` value inside `utterance` with
  `SOURCE_HMI_BUTTON`; the bridge hides that grammar from Views and logs no target
  payload. `DEV-054` tracks replacement by a versioned typed contract.
