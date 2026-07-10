# Central Brain Python Prototype Closure Plan

This document turns the completion audit into a requirement-by-requirement closure plan. It separates current Python prototype closure actions from production-only or target-platform blockers.

## Artifact

- Contract: `central-brain/contracts/central_brain_prototype_closure_plan.json`
- Baseline completion audit: `central-brain/contracts/central_brain_prototype_completion_audit.json`
- Baseline API contract: `0.1.104`
- Current estimate after this planning slice: 84-90%

The closure plan keeps `python_prototype_current_scope_complete=false` because two prototype-scope classification actions remain open: `FW-S-006` extension service coverage and `NV-F-012` observability coverage.

## Current Prototype Closure Actions

- `PY-CL-001`: decide whether `FW-S-006` is already covered by SOA contract visibility and `/uib/extensions`, or add a small read-only extension-service closure summary.
- `PY-CL-002`: decide whether `NV-F-012` is sufficiently represented by `/audit/recent`, JSONL audit persistence, delivery readiness and prototype readiness, or add a small read-only observability readiness summary.
- `PY-CL-003`: keep customer application modules `APP-001..003` and `APP-005..010` outside Python prototype completion criteria.
- `PY-CL-004`: keep chip OS base and UniSOC hardware baseline IDs as target-platform responsibilities.
- `PY-CL-005`: keep real sensors, time sync, connected funcware, ADAS funcware and production network bindings outside the current Python prototype unless a read-only placeholder is missing.
- `PY-CL-006`: retain hardware empty-interface delivery while tracking real Vehicle Signal, PCIe NPU, Driver/HAL ABI and smoke evidence as production blockers.

## Production-Only Or Target-Platform Blockers

- Customer apps: `APP-001..003`, `APP-005..010`.
- Target OS and hardware base: `HW-001`, `KH-001`, `KH-002`, `KH-004`, `KH-005`, `KH-008`, `KH-009`.
- Production funcware and protocol runtimes: `NV-F-002`, `NV-F-006`, `NV-F-007`, `NV-F-010`, `NV-P-001`, `NV-P-004`, `NV-P-007`.
- Virtualization: `HV-001..003` remains documentation-only by user constraint.

## Boundaries

This closure plan is read-only and evidence-only:

- no runtime endpoint
- no POST call
- no persistence
- no owner or reviewer assignment
- no evidence acceptance
- no gate closure
- no broker, DDS, SOME/IP, MQTT or high-rate data plane activation
- no service dispatch
- no hardware access
- no Driver/HAL development
- no virtualization development

## Validation

```bash
python3 -m json.tool central-brain/contracts/central_brain_prototype_closure_plan.json
bash tools/check_central_brain_delivery_docs.sh
git diff --check
```
