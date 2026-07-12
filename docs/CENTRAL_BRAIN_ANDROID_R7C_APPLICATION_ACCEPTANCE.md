# Android R7C Application Integration Acceptance

## Scope

R7C closes only the Android 13 emulator application-integration gate for the
Client2 -> public SDK -> typed Binder -> Runtime path. The evidence is bounded
to API 33 and does not qualify target hardware, production service placement,
model/NPU execution or any vehicle effect.

Req IDs: `APP-004`, `XSC-001`, `XSC-005`, `XSC-006`, `NV-F-001`,
`NV-F-012`, `NV-G-003`, `NV-G-006`, `NV-G-007`, `NV-P-002`, `DEL-001`,
`DEL-003`, `DEL-004`, `DEL-005`.

## Evidence Matrix

| ID | Scenario | Pass condition |
| --- | --- | --- |
| `R7C-E-001` | Runtime package disabled, then enabled | Client2 shows bind failure, releases single-flight state and succeeds on retry in the same Activity |
| `R7C-E-002` | Two rapid Client2 taps | Exactly one Runtime submission and one terminal callback |
| `R7C-E-003` | Runtime process death after task submission | DUMP-protected debug probe kills only the debug process; Client2 receives one `ERROR_SERVICE_DIED`, retry restarts Runtime, and interrupted work is reconciled without resume |
| `R7C-E-004` | Client2 process force-stop/relaunch | New process reinstalls the panel hook, binds Runtime and renders a completed reply |
| `R7C-E-005` | SDK Binder lifecycle regression | Existing instrumentation verifies service death, explicit reconnect, callback death, terminal uniqueness and cancel/completion race |

The Runtime process-death injector is `RuntimeFaultProbeReceiver`. It exists
only under the debug source set, requires `android.permission.DUMP`, accepts one
explicit action and calls `Process.killProcess` after logging bounded evidence.
It must not appear in the release manifest or be callable by Client2.

## Run

With one API 33 emulator online:

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
- `api33_end_to_end_acceptance_complete=true`
- `r7_application_integration_complete=true`

The following remain false: production activation, target system integration
owner resolution and target hardware validation. Effect, Model, Event, Memory
and Skill/Governance production blockers remain open. Client2 has no cancel or
timeout control, so R7C does not claim UI coverage for those operations; the
typed SDK cancellation race remains covered by instrumentation.
