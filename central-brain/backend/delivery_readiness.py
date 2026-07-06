#!/usr/bin/env python3
"""Android/Linux delivery readiness contract for the Central Brain prototype."""

from __future__ import annotations

import copy
from typing import Any


DELIVERY_REQ_IDS = [
    "DEL-001",
    "DEL-002",
    "DEL-003",
    "DEL-004",
    "DEL-005",
    "XSC-001",
    "XSC-002",
    "XSC-003",
    "XSC-004",
    "XSC-005",
    "XSC-006",
]


DELIVERY_READINESS_ROWS: list[dict[str, Any]] = [
    {
        "target": "android-debug-console",
        "platform": "Android",
        "current_state": "debug-apk-binder-sample",
        "ready_for": [
            "emulator validation",
            "Binder contract review",
            "AI SDK/Agent and governance debug visibility",
        ],
        "artifacts": [
            "central-brain/android-console/AndroidManifest.xml",
            "central-brain/bindings/android/java/com/centralbrain/binding/CentralBrainGatewayClient.java",
            "central-brain/bindings/android/java/com/centralbrain/binding/CentralBrainGatewayBinderService.java",
        ],
        "validation": [
            "bash tools/build_central_brain_console.sh",
            "bash tools/check_central_brain_binding_artifacts.sh",
        ],
        "blocked_by": [
            "target AAOS image signing and priv-app policy",
            "SELinux domain and service manager registration decision",
        ],
        "req_ids": ["DEL-001", "DEL-003", "DEL-004", "XSC-001", "XSC-002", "XSC-003", "XSC-005", "XSC-006", "NV-P-002"],
    },
    {
        "target": "android-system-service-integration-note",
        "platform": "Android",
        "current_state": "documented-constraints",
        "ready_for": ["platform owner review", "privileged service planning", "Binder identity policy mapping review"],
        "artifacts": ["docs/CENTRAL_BRAIN_ANDROID_SYSTEM_SERVICE_INTEGRATION.md"],
        "validation": ["bash tools/check_central_brain_android_system_service_docs.sh"],
        "blocked_by": [
            "target framework/service registration owner",
            "production shared Runtime & Governance backend owner",
        ],
        "req_ids": ["DEL-001", "DEL-003", "DEL-004", "XSC-005", "XSC-006", "NV-P-002", "NV-P-005"],
    },
    {
        "target": "linux-cli-semantic-gateway",
        "platform": "Linux",
        "current_state": "active-cli-sample",
        "ready_for": ["contract smoke testing", "cockpit-domain developer inspection", "REST prototype fallback"],
        "artifacts": ["central-brain/linux-cli/central_brain_cli.py"],
        "validation": ["bash tools/smoke_central_brain_semantic_gateway.sh"],
        "blocked_by": ["replacement of REST prototype by target daemon/service boundary"],
        "req_ids": ["DEL-002", "DEL-003", "DEL-004", "XSC-001", "XSC-002", "XSC-003", "XSC-005", "XSC-006"],
    },
    {
        "target": "linux-ipc-daemon-sample",
        "platform": "Linux",
        "current_state": "active-unix-socket-sample",
        "ready_for": ["same-SoC IPC integration sample", "shared governance socket precheck review"],
        "artifacts": [
            "central-brain/bindings/linux/ipc/central_brain_ipc_daemon.py",
            "central-brain/bindings/linux/ipc/central_brain_ipc_client.py",
            "central-brain/bindings/linux/ipc/central_brain_governance_client.py",
        ],
        "validation": ["bash tools/smoke_central_brain_linux_ipc.sh"],
        "blocked_by": [
            "target distro service identity and socket path decision",
            "production shared governance daemon replacement",
        ],
        "req_ids": ["DEL-002", "DEL-003", "DEL-004", "XSC-005", "XSC-006", "NV-P-002"],
    },
    {
        "target": "linux-grpc-rpc-sample",
        "platform": "Linux",
        "current_state": "grpc-json-active-sample",
        "ready_for": ["proto review", "RPC contract smoke testing", "transport replacement planning"],
        "artifacts": [
            "central-brain/bindings/linux/proto/central_brain_gateway.proto",
            "central-brain/bindings/linux/grpc/central_brain_grpc_server.py",
            "central-brain/bindings/linux/grpc/central_brain_grpc_client.py",
        ],
        "validation": ["bash tools/smoke_central_brain_linux_grpc.sh"],
        "blocked_by": [
            "grpcio or C++ gRPC runtime availability",
            "production credential source and identity mapping",
        ],
        "req_ids": ["DEL-002", "DEL-003", "DEL-004", "XSC-005", "XSC-006", "NV-P-003"],
    },
    {
        "target": "linux-systemd-package-profile",
        "platform": "Linux",
        "current_state": "sample-profile-and-hardening-check",
        "ready_for": ["target distro review", "systemd sandbox review", "package layout discussion"],
        "artifacts": [
            "central-brain/deploy/linux/central-brain.package-profile.json",
            "central-brain/deploy/linux/systemd/central-brain-backend.service",
            "central-brain/deploy/linux/systemd/central-brain-governance.service",
            "central-brain/deploy/linux/systemd/central-brain-linux-ipc.service",
            "central-brain/deploy/linux/systemd/central-brain-linux-grpc.service",
        ],
        "validation": [
            "bash tools/check_central_brain_linux_package_profile.sh",
            "bash tools/check_central_brain_linux_systemd_hardening.sh",
        ],
        "blocked_by": [
            "target distro/package format decision",
            "LSM/SELinux/AppArmor policy for target image",
        ],
        "req_ids": ["DEL-002", "DEL-003", "DEL-004", "XSC-005", "XSC-006", "NV-P-002", "NV-P-003"],
    },
    {
        "target": "driver-hal-gap-backlog",
        "platform": "Android/Linux",
        "current_state": "read-only-gap-contract",
        "ready_for": ["hardware/vendor SDK intake", "Driver/HAL scope review"],
        "artifacts": [
            "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md",
            "docs/CENTRAL_BRAIN_NPU_RUNTIME_INTERFACE.md",
            "GET /native/driver-gaps",
        ],
        "validation": ["bash tools/check_central_brain_npu_interface.sh"],
        "blocked_by": [
            "real PCIe NPU hardware and vendor SDK",
            "target vehicle signal catalog, DBC/ARXML, or VHAL contract",
        ],
        "req_ids": ["DEL-005", "XSC-004", "KH-003", "KH-006", "KH-007", "HW-002", "NV-F-011"],
    },
    {
        "target": "virtualization-safety-constraints",
        "platform": "Android/Linux",
        "current_state": "documented-non-development-scope",
        "ready_for": ["Safety State mapping review", "ASIL/QM deployment assumption review"],
        "artifacts": ["docs/CENTRAL_BRAIN_VIRTUALIZATION_SAFETY_CONSTRAINTS.md"],
        "validation": ["bash tools/check_central_brain_virtualization_docs.sh"],
        "blocked_by": ["target SoC/hypervisor safety-domain definition"],
        "req_ids": ["DEL-004", "HV-001", "HV-002", "HV-003", "FW-S-005", "NV-G-005"],
    },
]


def delivery_readiness_payload() -> dict[str, Any]:
    rows = copy.deepcopy(DELIVERY_READINESS_ROWS)
    return {
        "readiness": rows,
        "summary": {
            "production_ready": False,
            "android_debug_ready": True,
            "linux_samples_ready": True,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
            "target_decisions_required": [
                "AAOS signing/SELinux/service owner",
                "target Linux distro/package format",
                "true gRPC runtime and credential source",
                "production shared governance backend owner",
                "real Driver/HAL hardware and vendor SDK inputs",
            ],
        },
        "non_goals": [
            "No production Android system service, true gRPC runtime, package manager integration, Driver/HAL, Safety Runtime, vehicle bus, or virtualization code is implemented by this readiness contract.",
            "This endpoint does not dispatch SOA services or consume QoS; it is delivery metadata for cockpit-domain engineers.",
        ],
        "validation_bundle": sorted({command for row in rows for command in row["validation"]}),
        "req_ids": DELIVERY_REQ_IDS,
    }
