# Central Brain Python Prototype Handoff Manifest

This document is the cockpit-domain engineer handoff index for the current Central Brain Python prototype. It is generated as a static, read-only delivery artifact and does not add runtime behavior.

## Scope

- Manifest: `central-brain/contracts/central_brain_prototype_handoff_manifest.json`
- Baseline API contract: `central-brain/contracts/central_brain_api.json`
- Baseline API version: `0.1.108`
- Baseline commit: `2884438c`
- Req IDs: XSC-001, XSC-002, XSC-003, XSC-004, XSC-005, XSC-006, DEL-001, DEL-002, DEL-003, DEL-004, DEL-005

## Android Handoff

Android is the primary delivery path. The current handoff includes the debug Console APK build output, Binder/AIDL contract, Binder service/client sample, Android binding README, and Android system service integration note.

Primary Android methods:

- `getPrototypeReadinessJson`
- `getObservabilityReadinessJson`
- `getDeliveryReadinessJson`
- `getBindingReadinessJson`
- `getDriverHalGapsJson`
- `getHardwareInterfacesJson`

The Android path remains a debug Binder/Console sample. It does not include Android framework patches, priv-app signing configuration, SELinux policy, or production system service deployment.

## Linux Handoff

Linux is delivered as synchronized CLI, Unix socket IPC, and gRPC/RPC JSON contract samples.

Primary Linux commands:

- `prototype-readiness`
- `observability-readiness`
- `delivery-readiness`
- `binding-readiness`
- `driver-gaps`
- `hardware-interfaces`

Primary Linux IPC/RPC operations:

- `prototype.readiness.get`
- `observability.readiness.get`
- `delivery.readiness.get`
- `bindings.readiness.get`
- `native.driver.gaps.get`
- `hardware.interfaces.get`
- `GetPrototypeReadiness`
- `GetObservabilityReadiness`
- `GetDeliveryReadiness`
- `GetBindingReadiness`
- `GetDriverHalGaps`
- `GetHardwareInterfaces`

The Linux path remains sample CLI/IPC/gRPC delivery. It does not activate production daemon ownership, real gRPC runtime credentials, MQTT/SOME-IP/DDS runtime, high-rate data plane, or production package management beyond the documented sample profile.

## Observability Handoff

`GET /observability/readiness` is the current `PY-CL-002` / `NV-F-012` handoff surface. It is visible through Android Binder `getObservabilityReadinessJson` / Console `Observability` and Linux `observability-readiness` / `observability.readiness.get` / `GetObservabilityReadiness`.

This handoff surface is read-only. It does not implement a production log backend, metric daemon, hardware trace capture, Driver/HAL, or virtualization.

## Completion Summary Handoff

`GET /prototype/completion-summary` is the current-scope completion handoff surface. It is visible through Android Binder `getPrototypeCompletionSummaryJson` / Console `Complete` and Linux `prototype-completion-summary` / `prototype.completion.summary.get` / `GetPrototypeCompletionSummary`.

This surface reports `python_prototype_current_scope_complete=true`, `prototype_handoff_ready=true`, and `production_ready=false`. The current Python prototype is complete for handoff to Android/Linux cockpit-domain engineers, while target hardware, production Driver/HAL, production observability, real broker/DDS/high-rate data plane, Android system service deployment, and virtualization remain separate production planning work.

## Validation Index

Required validation commands:

```bash
python3 -m json.tool central-brain/contracts/central_brain_api.json
python3 -m json.tool central-brain/contracts/central_brain_prototype_handoff_manifest.json
python3 -m json.tool central-brain/bindings/linux/ipc/central_brain_ipc_envelope.schema.json
python3 -m py_compile central-brain/backend/mock_npu_service.py central-brain/backend/protocol_bindings.py central-brain/backend/delivery_readiness.py central-brain/backend/prototype_readiness.py central-brain/linux-cli/central_brain_cli.py central-brain/bindings/linux/ipc/central_brain_ipc_daemon.py central-brain/bindings/linux/ipc/central_brain_ipc_client.py central-brain/bindings/linux/grpc/central_brain_grpc_server.py central-brain/bindings/linux/grpc/central_brain_grpc_client.py
bash tools/check_central_brain_binding_artifacts.sh
bash tools/check_central_brain_delivery_docs.sh
bash tools/check_central_brain_android_system_service_docs.sh
bash tools/smoke_central_brain_semantic_gateway.sh
bash tools/smoke_central_brain_linux_ipc.sh
bash tools/smoke_central_brain_linux_grpc.sh
bash tools/build_central_brain_console.sh
git diff --check
```

## Invariants

The handoff manifest is read-only and preserves the current prototype boundaries:

- no state persistence
- no POST side effects
- no owner or reviewer assignment
- no evidence acceptance
- no gate closure
- no broker, DDS, or high-rate data plane activation
- no hardware access
- no Driver/HAL development
- no virtualization development

## Open Items

The handoff intentionally keeps production blockers visible rather than hiding them:

- DEV-004: real vehicle signal source remains absent.
- DEV-005: real PCIe NPU hardware/vendor SDK remains absent.
- DEV-007: real event subscription broker/runtime activation remains blocked.
- DEV-016: hardware empty-interface replacement still depends on target owners, ABI evidence, smoke evidence, and review workflow.
- ISSUE-013, ISSUE-014, ISSUE-016, ISSUE-017, and ISSUE-018 remain unresolved production decisions.
