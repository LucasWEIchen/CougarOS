# Android R7C Application Integration Acceptance

## Scope

R7C closes only the Android 13 application-integration gate for the
Client2 -> public SDK -> typed Binder -> Runtime path. The evidence is bounded
to API 33 and does not qualify target hardware, production service placement,
model/NPU execution or any vehicle effect.

Req IDs: `S2-UX-001`, `S2-HMI-001..006`, `S2-ADP-001`, `S2-SAF-001`, `APP-004`, `XSC-001`, `XSC-005`, `XSC-006`, `NV-F-001`,
`NV-F-012`, `NV-G-003`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`,
`DEL-003`, `DEL-004`, `DEL-005`.

## Evidence Matrix

| ID | Scenario | Pass condition |
| --- | --- | --- |
| `R7C-E-001` | Runtime package disabled, then enabled | Client2 shows bind failure and succeeds with a new Session in the same Activity |
| `R7C-E-002` | Two sequential accepted Client2 requests | Java HMI coordinator starts the replacement bind before cancelling/closing the old Session; each new Session has one sequence-1 request event/replay and no active-session leak |
| `R7C-E-003` | Runtime process death after initial Session replay | DUMP-protected probe kills debug Runtime; original Session reconnects, replays, suppresses duplicate event and emits no fake terminal |
| `R7C-E-004` | Hide, then Client2 process force-stop/relaunch | New process restores the text-free checkpoint, resumes the existing Session, preserves hidden state and renders replay after menu reopen |
| `R7C-E-005` | SDK Binder lifecycle regression | Existing instrumentation verifies service death, explicit reconnect, callback death, terminal uniqueness and cancel/completion race |
| `R7C-E-006` | Intent-first four-stage shell on 1920x1080 Client2 | Intent/Plan/Execution/Result, exact safe frame and HVAC/Seat detail drawer render while dispatch stays disabled |
| `R7C-E-007` | HVAC control surface under unavailable driving Context | Surface remains present but parameter controls are disabled; no new manual Session is admitted while reported stays unavailable and VERIFIED stays false |
| `R7C-E-008` | Seat control surface under unavailable driving Context | Surface remains present but parameter/high-risk controls are disabled; UNKNOWN_RESTRICTED submits no Session and dispatches no Effect |
| `R7C-E-009` | Observable execution timeline after accepted Session | Seven phases, Media/Navigation projection and bounded typed trace render in the 1920x1080 panel; absent Plan/Graph/Effect/readback remain NOT PUBLISHED/NOT WIRED/NOT DISPATCHED/UNAVAILABLE |
| `R7C-E-010` | Approval/partial/retry/undo recovery UX with unpublished command services | Approval reason/target/expiry gaps, partial evidence counters and compensation render; approve/reject/retry/undo remain visible and disabled; outside dismiss/reopen preserves Session/recovery state |
| `R7C-E-011` | Driving restriction renderer with unavailable trusted Context | UNKNOWN is treated as moving-restricted; long details, parameter editing and high-risk scenario entry are disabled while Runtime policy authority remains independent |
| `R7C-E-012` | Protected engineer simulation drawer | Signature/capability/version/hash admission succeeds in debug; PARKED/MOVING/UNKNOWN, occupancy/belt, fault matrix, monotonic revision and reset fail-closed pass; release Service and Effect authority remain absent |
| `R7C-E-013` | Scenario/manual-control synchronization | cold/fatigue/rest and manual HVAC/Seat share ScenarioClient/Session/Event state; catalog roles, lifecycle and event sequence match across shell/device details while Plan/Effect/readback stay unavailable |
| `R7C-E-014` | Accessibility/display matrix | 1280x720, 1920x1080 and 2560x1440 allowlisted profiles, 1.3 font scale, 48dp targets, runtime content descriptions, non-color selected state and unsupported-display fail-closed pass on Android 13 ARM64 |
| `R7C-E-015` | P4 aggregate device/fault/recovery acceptance | Recovery, protected fault matrix, natural/manual sync, display matrix, per-suite crash buffer and final UI tree pass while blocked Plan/Effect/approval/undo/readback remain explicit false claims |

The Runtime process-death injector is `RuntimeFaultProbeReceiver`. It exists
only under the debug source set, requires `android.permission.DUMP`, accepts one
explicit action and calls `Process.killProcess` after logging bounded evidence.
It must not appear in the release manifest or be callable by Client2.

## Run

With one API 33 Android device online:

```bash
bash tools/test_client2_central_brain_p4_acceptance.sh \
  --require-api-33 --replace-conflicting-client2
```

Use `--skip-build` only when the Runtime, Client2, Demo and androidTest APKs are
already current. The script restores a temporarily disabled Runtime package in
an EXIT trap and writes evidence under
`logs/test/client2-central-brain-recovery/`.

## Acceptance State

Passing the complete matrix permits these baseline transitions:

- `client2_binder_migration_complete=true`
- `cockpit_hmi_state_reducer_implemented=true`
- `cockpit_hmi_four_stage_shell_implemented=true`
- `cockpit_hvac_surface_implemented=true`
- `cockpit_seat_surface_implemented=true`
- `cockpit_execution_timeline_implemented=true`
- `cockpit_execution_typed_event_projection=true`
- `cockpit_recovery_state_reducer_owned=true`
- `cockpit_partial_outcome_projection=true`
- `cockpit_driving_ux_policy_implemented=true`
- `cockpit_unknown_driving_restricted=true`
- `cockpit_engineer_simulation_drawer_implemented=true`
- `cockpit_engineer_runtime_release_service_absent=true`
- `cockpit_engineer_effect_authorization_source=false`
- `cockpit_engineer_production_available=false`
- `cockpit_scenario_control_state_reducer_owned=true`
- `cockpit_scenario_catalog_normalized=true`
- `cockpit_scenario_device_session_synchronized=true`
- `cockpit_display_matrix_defined=true`
- `cockpit_accessibility_semantics_runtime_owned=true`
- `cockpit_display_matrix_android13_arm64_verified=true`
- `p4_w12_application_acceptance_complete=true`
- `p4_android13_arm64_aggregate_verified=true`
- `p4_ui_tree_verified=true`
- `p4_crash_buffer_clean=true`
- `runtime_release_simulation_surface_absent=true`
- `p4_plan_effect_projection_host_verified=true`
- `api33_end_to_end_acceptance_complete=true`
- `r7_application_integration_complete=true`

The following remain false: production activation, target system integration
owner resolution and target hardware validation. Effect, Model, Event, Memory
and Skill/Governance production blockers remain open. `p4_automatic_plan_runtime_published=false`,
`p4_production_effect_dispatch_enabled=false`, `p4_approval_response_service_published=false`,
`p4_undo_service_published=false`, `p4_vehicle_readback_available=false`,
`client2_production_release_artifact_available=false` and `hmi_d4_demo_control_loop_complete=false` remain explicit.
Client2 has no cancel or
timeout control, so R7C does not claim UI coverage for those operations; the
typed SDK cancellation race remains covered by instrumentation. Contract schema 2.2 adds R7C-E-015 and the P4 aggregate runner.
Each physical child suite clears and checks the Android crash buffer; the aggregate ends with a fresh Activity launch and UI tree.
Host-only approval/partial/mismatch/undo projection is not promoted to physical execution evidence, and Runtime release absence does
not imply that a production Client2 release artifact exists. Contract schema 2.1 added R7C-E-014 for the strict three-profile
display allowlist, 48dp targets, runtime accessibility names/state, 1.3 font scale and unsupported-display fail-closed behavior.
It does not claim portrait, arbitrary display/density support, OEM distraction qualification or Effect authority. Contract schema 2.0 added R7C-E-013 for scenario/manual-control
synchronization. It verifies exact catalog normalization, the cold/fatigue/rest and manual HVAC/Seat matrix, one Session/Event state
source and canonical mismatch host failure without inferring Runtime Plan, Effect dispatch or readback. Contract schema 1.9 added
R7C-E-012 for the protected engineer
simulation drawer. It verifies debug-only signature/capability/protocol admission, acknowledged revisioned Context projection,
PARKED/MOVING/UNKNOWN presentation, occupancy/belt, fault profiles and reset without claiming production Context, Safety or Effect
authority. Runtime release has no Controller Service. Contract schema 1.8 added the fail-closed driving restriction
renderer, default UNKNOWN restrictions, hidden long details, disabled parameter/high-risk controls and explicit UI non-authority.
Because the current physical Client2 has no trusted global Context provider, E-007/E-008 now verify the restricted device surfaces
and report manual Session admission retest as false; historical P4-W04/P4-W05 manual evidence remains recorded. E-012 now covers the
protected debug presentation matrix, while production trusted Context and actual vehicle authority remain unimplemented. Contract
schema 1.7 adds reducer-owned approval/partial/compensation
projection, explicit unavailable details, four disabled unpublished commands and outside-dismiss state preservation. Contract schema 1.6 adds the reducer-owned observable execution
timeline, seven stable phase rows, Media/Navigation projections and an eight-event redacted trace. Contract schema 1.5 added the maintained Seat control surface,
heat/vent mutual exclusion, UNKNOWN_RESTRICTED driver-position denial, governed manual Session admission and desired/reported separation
evidence. Application-layer evidence does not mean scenario/Graph/Effect execution, HVAC/Seat Adapter dispatch, trusted vehicle Context,
approval response, vehicle readback or production storage is active. Frozen Session V1 carries HVAC1/SEAT1 in utterance and has no typed
parameter field, HMI_CONTROL source or approval transport; `DEV-054/055/057/058/059/060/061/062` track that boundary.
