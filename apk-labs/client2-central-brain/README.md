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
HVAC contains the P4-W04 governed manual control surface and Seat contains the
P4-W05 safety-gated governed manual control surface.
Each scene creates a typed
Session through `CockpitControlCoordinator -> Client2ScenarioBridge.openSession
-> SessionClient`; snapshot, typed events and authoritative cursor replay are
reduced into immutable `CockpitHmiState` before rendering. The bridge
`submit(...)` descriptor remains only as an unused compatibility wrapper.
The SDK, AIDL parcelables and a narrow Client2 bridge are compiled into
`classes2.dex`. The APK requests no network permission and contains no direct
HTTP fallback.

The four visible XML scenario tags remain stable two-segment UI aliases. A 14-entry exact bridge
compatibility allowlist still maps all supported aliases to qualified Session IDs before Runtime admission; unknown
aliases fail before binding and the frozen Session V1 validation is not relaxed.
Runtime process death reconnects the active stream, replays the owner-scoped
snapshot/history and drops already delivered event sequences.

The Execution surface also owns a fail-closed approval and recovery section.
`CockpitRecoveryState` projects approval status, partial terminal evidence and
compensation from sanitized Session/Event data. Because the current Client2
surface does not receive `ApprovalPrompt`, `EffectObservation.retryable` or
`UndoHandle`, approve/reject/retry/undo stay visible and disabled; outside
dismissal preserves the active Session and recovery projection.

`DrivingUxPolicy` now maps the immutable Seat/Context driving projection to
`PanelPresentationMode`. Missing, stale, unavailable, unknown, or moving
evidence always selects `MOVING_RESTRICTED`: long detail/trace Views are hidden,
the reply strip is one line, HVAC/Seat parameter controls and the high-risk rest
scenario are disabled. Only trusted observed PARKED evidence selects
`PARKED_FULL`. This is presentation policy only and never authorizes Runtime,
Safety, approval, or Effect dispatch.

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
`REQUESTED`, never as vehicle execution or readback. The Seat surface exposes
four zones, mutually exclusive heating/ventilation levels, massage, recline and
upright/comfort/rest presets. Immutable `SeatControlIntent` and
`CockpitSeatState` keep desired state, safety evidence, request state and device
readback separate. Low-risk comfort changes debounce for 300 ms into
`scene.manual.seat.adjust.v1`; position changes fail closed while trusted Context
is unavailable or the driver is moving, and parked rest remains approval-required.
The execution surface now uses immutable `CockpitExecutionTimeline` state to show
Intent, Context, Plan, Policy, Graph, Effect and Readback with target/source/result,
Media/Navigation projections and the newest eight redacted typed events. Approval,
partial failure, retry and undo commands remain P4-W07 work and are not synthesized.

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
cockpit_seat_surface_implemented=true
cockpit_seat_reducer_owned=true
cockpit_seat_debounce_ms=300
cockpit_seat_governed_manual_session=true
cockpit_seat_unknown_restricted_fail_closed=true
cockpit_seat_reported_readback_available=false
seat_manual_typed_parameter_field=false
cockpit_demo_control_loop_implemented=false
real_vehicle_effect_adapter_available=false
```

P4-W01 through P4-W11 are complete. The primary bridge exposes typed Session handle,
snapshot, event, replay, overflow, close and error callbacks. The Java coordinator
reduces these callbacks, owns lifecycle and resumes a text-free checkpoint after
Client2 process restart. Android 13 ARM64 acceptance covers Runtime/Client2 process
death, duplicate suppression, hidden-state restore, menu reopen, exact 1920x1080
safe-frame rendering, four stage selection, HVAC and Seat controls, debounce,
governed manual Session admission, unknown-context Seat position blocking and the
seven-phase observable execution timeline, fail-closed approval/recovery UX, driving
restriction renderer, protected engineer simulation drawer, scenario/manual synchronization
and the three-profile accessibility/display matrix. P4-W12 is next and will run aggregate
Android device acceptance/fault/recovery without enabling vehicle or production Effect dispatch:

```text
client2_session_event_primary_api=true
client2_session_event_typed_callback=true
client2_scenario_alias_map_count=14
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
cockpit_seat_surface_implemented=true
cockpit_seat_reducer_owned=true
cockpit_seat_debounce_ms=300
cockpit_seat_governed_manual_session=true
cockpit_seat_heat_vent_mutex_verified=true
cockpit_seat_unknown_restricted_fail_closed=true
cockpit_seat_desired_reported_separation_verified=true
cockpit_seat_reported_readback_available=false
cockpit_seat_verified_before_readback=false
seat_manual_typed_parameter_field=false
cockpit_execution_timeline_implemented=true
cockpit_execution_timeline_reducer_owned=true
cockpit_execution_typed_event_projection=true
cockpit_execution_trace_capacity=8
cockpit_execution_plan_published=false
cockpit_execution_effect_dispatch_enabled=false
cockpit_execution_readback_available=false
cockpit_recovery_state_reducer_owned=true
cockpit_approval_details_fail_closed=true
cockpit_partial_outcome_projection=true
cockpit_compensation_projection=true
cockpit_approval_response_service_published=false
cockpit_retry_service_published=false
cockpit_undo_service_published=false
cockpit_recovery_commands_enabled=false
cockpit_driving_ux_policy_implemented=true
cockpit_unknown_driving_restricted=true
cockpit_engineer_simulation_drawer_implemented=true
cockpit_engineer_signature_permission_required=true
cockpit_engineer_capability_required=true
cockpit_engineer_context_revisioned=true
cockpit_engineer_runtime_release_service_absent=true
cockpit_engineer_effect_authorization_source=false
cockpit_engineer_production_available=false
cockpit_display_matrix_defined=true
cockpit_touch_target_min_dp=48
cockpit_accessibility_semantics_runtime_owned=true
cockpit_display_matrix_android13_arm64_verified=true
cockpit_display_effect_authorization_source=false
client2_smali_controller_retired=true
client2_hmi_checkpoint_resume_verified=true
client2_hmi_hidden_state_recreation_verified=true
client2_hmi_checkpoint_text_persisted=false
legacy_text_callback_authoritative=false
cockpit_demo_control_loop_implemented=false
implementation_stage=P6-W01
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
- Frozen Session V1 has no typed parameter field, `HMI_CONTROL` source or approval
  response. P4-W04/P4-W05 carry strict canonical `HVAC1`/`SEAT1` values inside
  `utterance` with `SOURCE_HMI_BUTTON`; the bridge hides both grammars from Views
  and logs no target payload. `DEV-054`/`DEV-055` track replacement by a versioned
  typed contract and governed approval path. `DEV-057` separately tracks
  ApprovalPrompt/retry/UndoHandle publication to Client2.
- `DEV-058` tracks that the current Client2 transport has no production-trusted
  global driving Context. P4-W09 closes only the protected debug testability
  subcondition; P8 must still supply target authority.
- `DEV-059` tracks that P4-W09 locally projects only acknowledged debug-controller
  values/revisions. Runtime release contains no controller Service, and the projection
  is never an Effect, Safety or production vehicle authorization source.
- `DEV-061` tracks that P4-W11 certifies only three exact landscape profiles and
  `fontScale<=1.30`; it is not OEM multi-display, TalkBack or production HMI certification.

## P4-W09 protected engineer simulation drawer

The Plan surface exposes a third detail entry only after
`DebugSimulationControllerClient` binds the explicit Runtime debug component and
verifies `IDebugSimulationController` V1/hash. Client2 requests the debug signature
permission, while Runtime debug policy grants `debug.simulation.control` only to the
current-signer Client2 principal. The production policy does not grant it.

The drawer can set `PARKED/MOVING/UNKNOWN`, driver occupancy/belt, select the
simulated HVAC or Seat adapter, inject none/delay/timeout/retryable/terminal/mismatch
faults and reset the controller. Every accepted remote mutation returns a strictly
increasing revision before the sole reducer changes the HMI projection. Binder loss,
protocol mismatch, reset and process recreation return the UI to fail-closed UNKNOWN.

The signed debug APK passed the Android 13/API 33 ARM64 matrix and 1920x1080 visual
inspection. No raw device identity, UI tree, logs, signal values or screenshots are
tracked. Runtime release omits the controller Service; no production Client2 release
artifact with this drawer is delivered.

## P4-W10 scenario/manual synchronization

`CockpitScenarioControlState` is the single Java source for all 14 UI aliases and canonical scenario IDs. It projects cold, fatigue
and rest HVAC/Seat catalog roles plus manual HVAC/Seat target roles into the same immutable reducer state as Session lifecycle, Plan
revision and event sequence. `Client2ScenarioBridge` owns only the `ScenarioClient` interface; `SessionClient` is the composition-time
implementation for natural and manual requests.

The Plan, Result and device drawer text comes from that one state. A canonical mismatch fails with no device role. Natural scenarios
do not mutate manual desired parameters. Plan requires a positive Runtime revision; Effect and readback stay disabled/unavailable.
`tools/test_client2_central_brain_scenario_sync.sh --require-api-33` runs the physical cold/fatigue/rest/manual matrix. No raw device
identity or evidence is tracked. Req IDs: `S2-HMI-001..006`, `S2-SCN-001`; tracking: `DEV-060`, `ISSUE-033`.

## P4-W11 accessibility/display matrix

`CockpitDisplayPolicy` admits only `1280x720@107dpi`, `1920x1080@160dpi` and `2560x1440@213dpi` landscape profiles with
`0.85 <= fontScale <= 1.30`. It returns deterministic panel bounds and a density-equivalent 48dp touch target. Unsupported metrics,
portrait, wrong density or oversized text disable the bottom navigation trigger and keep the overlay hidden.

The patched XML provides a static 48dp baseline. `CockpitControlCoordinator` adds nonempty content descriptions, focus and
accessibility importance, two-line ellipsis, selected state and state descriptions at runtime. State remains understandable without
color, and the policy is never an Effect authorization source.

Run `tools/check_central_brain_android_client2_accessibility_display.sh` for host/static validation and
`tools/test_client2_central_brain_accessibility_display.sh --require-api-33` for the physical matrix. The device script restores
size/density/font/rotation and emits no raw device identity or UI evidence. Req IDs: `S2-UX-003`, `S2-HMI-001/002`;
tracking: `DEV-061`, `ISSUE-019/033`.

## P4-W12 aggregate Android acceptance

`central-brain/contracts/central_brain_android_p4_hmi_acceptance.json` freezes the ordered P4 application acceptance matrix and its
evidence layers. Run `tools/check_central_brain_android_client2_p4_acceptance.sh` for host/static validation. On the approved Android
13 ARM64 device, run:

```bash
tools/test_client2_central_brain_p4_acceptance.sh \
  --require-api-33 \
  --replace-conflicting-client2
```

The runner executes recovery, debug engineer-state rejection, scenario/manual synchronization and accessibility/display suites,
requires fresh child markers, checks crash buffers between suites, and finishes with a nonempty UI tree containing the navigation
trigger. It records no raw serial, UI tree, screenshot, log, user/model text or vehicle payload.

This closes P4 application acceptance only. Automatic Plan publication, production Effect dispatch, approval response, undo, vehicle
readback and an independent production Client2 release remain unavailable. `p4_w12_application_acceptance_complete=true`,
`p4_automatic_plan_runtime_published=false`, `hmi_d4_demo_control_loop_complete=false`,
`production_ready=false`, `target_hardware_validated=false`, `implementation_stage=P6-W01`. Req IDs: `S2-UX-001..003`,
`S2-HMI-001..006`, `S2-SCN-001`, `S2-SAF-001`, `S2-EFF-001`; tracking: `DEV-062`, `ISSUE-033`.
