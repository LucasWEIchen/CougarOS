# Android R7C Application Integration Acceptance

## Scope

R7C closes only the Android 13 application-integration gate for the
Client2 -> public SDK -> typed Binder -> Runtime path. The evidence is bounded
to API 33 and does not qualify target hardware, production service placement,
model/NPU execution or any vehicle effect.

Req IDs: `S2-UX-001`, `S2-HMI-001`, `S2-HMI-003..006`, `S2-ADP-001`, `APP-004`, `XSC-001`, `XSC-005`, `XSC-006`, `NV-F-001`,
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
| `R7C-E-007` | HVAC control surface and rapid temperature changes | Full control set renders; three rapid steps coalesce into one governed manual Session; desired becomes 24.0 C while reported stays unavailable and VERIFIED stays false |

The Runtime process-death injector is `RuntimeFaultProbeReceiver`. It exists
only under the debug source set, requires `android.permission.DUMP`, accepts one
explicit action and calls `Process.killProcess` after logging bounded evidence.
It must not appear in the release manifest or be callable by Client2.

## Run

With one API 33 Android device online:

```bash
bash tools/test_client2_central_brain_recovery.sh --require-api-33
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
- `api33_end_to_end_acceptance_complete=true`
- `r7_application_integration_complete=true`

The following remain false: production activation, target system integration
owner resolution and target hardware validation. Effect, Model, Event, Memory
and Skill/Governance production blockers remain open. Client2 has no cancel or
timeout control, so R7C does not claim UI coverage for those operations; the
typed SDK cancellation race remains covered by instrumentation. Contract schema 1.4 adds the maintained HVAC control surface,
300 ms debounce, governed manual Session admission and desired/reported separation evidence. Application-layer evidence does not mean
scenario/Graph/Effect execution, HVAC Adapter dispatch, Seat controls, vehicle readback or production storage is active. Frozen Session
V1 still carries HVAC1 in utterance and has no typed parameter field or HMI_CONTROL source; `DEV-054` tracks that boundary.
