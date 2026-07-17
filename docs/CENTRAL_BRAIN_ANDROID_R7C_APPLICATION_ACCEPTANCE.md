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

The Runtime process-death injector is `RuntimeFaultProbeReceiver`. It exists
only under the debug source set, requires `android.permission.DUMP`, accepts one
explicit action and calls `Process.killProcess` after logging bounded evidence.
It must not appear in the release manifest or be callable by Client2.

## Run

With one API 33 Android device online:

```bash
bash tools/test_client2_central_brain_recovery.sh --require-api-33
bash tools/test_client2_central_brain_engineer_simulation.sh --require-api-33
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
- `api33_end_to_end_acceptance_complete=true`
- `r7_application_integration_complete=true`

The following remain false: production activation, target system integration
owner resolution and target hardware validation. Effect, Model, Event, Memory
and Skill/Governance production blockers remain open. Client2 has no cancel or
timeout control, so R7C does not claim UI coverage for those operations; the
typed SDK cancellation race remains covered by instrumentation. Contract schema 1.9 adds R7C-E-012 for the protected engineer
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
parameter field, HMI_CONTROL source or approval transport; `DEV-054/055/057/058/059` track that boundary.
