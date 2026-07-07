#!/usr/bin/env python3
"""Prototype readiness registry for the Central Brain Python prototype.

Req IDs: XSC-001, XSC-002, XSC-003, XSC-004, XSC-005, XSC-006, DEL-001,
DEL-002, DEL-003, DEL-004, DEL-005.

This module is a read-only product and architecture status surface. It must not
dispatch SOA services, access Driver/HAL, probe hardware, or implement
virtualization behavior.
"""

from __future__ import annotations

import copy
from typing import Any


PROTOTYPE_REQ_IDS = [
    "XSC-001",
    "XSC-002",
    "XSC-003",
    "XSC-004",
    "XSC-005",
    "XSC-006",
    "APP-004",
    "FW-U-003",
    "FW-U-004",
    "FW-U-006",
    "FW-U-008",
    "FW-S-004",
    "FW-S-005",
    "NV-F-001",
    "NV-F-003",
    "NV-F-004",
    "NV-F-005",
    "NV-F-006",
    "NV-F-011",
    "NV-G-002",
    "NV-G-003",
    "NV-G-004",
    "NV-G-005",
    "NV-G-007",
    "NV-P-002",
    "NV-P-003",
    "NV-P-001",
    "NV-P-005",
    "NV-P-006",
    "HW-002",
    "KH-003",
    "KH-006",
    "KH-007",
    "DEL-001",
    "DEL-002",
    "DEL-003",
    "DEL-004",
    "DEL-005",
]


PROTOTYPE_MODULES: list[dict[str, Any]] = [
    {
        "module_id": "ai-sdk-agent-facade",
        "architecture_component": "AI SDK / Agent / Skill / Memory facade",
        "diagram_group": "application-layer",
        "yellow_sun_portable": True,
        "current_state": "active-contract-mock",
        "ready_for": ["Android debug Binder exercise", "Linux CLI/IPC/gRPC contract smoke", "agent task planning review"],
        "not_ready_for": ["real LLM runtime routing", "production skill sandbox", "cloud memory sync"],
        "android_primary_surface": "planAgentTaskJson, executeAgentTaskJson, invokeSkillJson, queryMemoryJson",
        "linux_sync_surface": "agent-plan, agent-execute, skill-invoke, memory-query over CLI/IPC/gRPC",
        "open_deviations": ["DEV-003"],
        "open_issues": [],
        "req_ids": ["XSC-001", "APP-004", "FW-U-006", "NV-F-001", "DEL-001", "DEL-002"],
    },
    {
        "module_id": "uni-info-bus",
        "architecture_component": "Uni Info Bus semantic Context/State/Event/Action/Extension",
        "diagram_group": "middle-layer",
        "yellow_sun_portable": True,
        "current_state": "active-semantic-mock-with-subscription-lifecycle-transport-readiness-decision-matrix-activation-checklist-callback-watch-shape-cursor-replay-storage-backpressure-qos-evidence-readiness-rollup-activation-evidence-intake-status-and-retention-checklist-contract",
        "ready_for": ["semantic gateway smoke", "bounded event-log validation", "event subscription request/cancel contract review", "callback/watch transport readiness review", "broker/cursor/backpressure owner decision review", "activation evidence checklist review", "activation evidence intake contract review", "activation evidence review status contract review", "activation evidence retention owner checklist review", "callback/watch API shape review", "cursor/replay storage contract review", "backpressure/QoS evidence contract review", "end-to-end readiness rollup review", "extension contract review"],
        "not_ready_for": ["real event broker", "SSE/WebSocket push", "DDS high-rate data plane", "plugin runtime loading", "real actuator dispatch"],
        "android_primary_surface": "getStateJson, listEventTopicsJson, getEventSubscriptionsJson, requestEventSubscriptionJson, cancelEventSubscriptionJson, getEventSubscriptionTransportReadinessJson, getEventSubscriptionDecisionMatrixJson, getEventSubscriptionActivationChecklistJson, getEventSubscriptionCallbackWatchShapeJson, getEventSubscriptionCursorReplayStorageJson, getEventSubscriptionBackpressureQosEvidenceJson, getEventSubscriptionReadinessRollupJson, submitEventSubscriptionActivationEvidenceJson, getEventSubscriptionActivationEvidenceStatusJson, getEventSubscriptionActivationEvidenceRetentionChecklistJson, getUibExtensionsJson, requestActionJson",
        "linux_sync_surface": "state, events, event-subscriptions, event-subscribe-request, event-subscribe-cancel, event-subscription-transport-readiness, event-subscription-decision-matrix, event-subscription-activation-checklist, event-subscription-callback-watch-shape, event-subscription-cursor-replay-storage, event-subscription-backpressure-qos-evidence, event-subscription-readiness-rollup, event-subscription-activation-evidence, event-subscription-activation-evidence-status, event-subscription-activation-evidence-retention-checklist, extensions, action-request over CLI/IPC/gRPC",
        "open_deviations": ["DEV-007", "DEV-015"],
        "open_issues": ["ISSUE-015", "ISSUE-018"],
        "req_ids": ["XSC-002", "FW-U-003", "FW-U-004", "FW-U-008", "NV-P-006", "DEL-001", "DEL-002"],
    },
    {
        "module_id": "soa-service-entry",
        "architecture_component": "SOA service entry and service contract visibility",
        "diagram_group": "middle-layer",
        "yellow_sun_portable": True,
        "current_state": "active-policy-checked-mock",
        "ready_for": ["mock service invocation", "service contract review", "governance precheck validation"],
        "not_ready_for": ["production service discovery", "vehicle network service bridge", "SOME/IP runtime"],
        "android_primary_surface": "getServiceContractsJson, invokeServiceJson",
        "linux_sync_surface": "service-contracts and infer/vehicle-state over CLI/IPC/gRPC",
        "open_deviations": ["DEV-001", "DEV-002"],
        "open_issues": [],
        "req_ids": ["XSC-003", "FW-S-004", "FW-S-005", "NV-G-003", "DEL-001", "DEL-002"],
    },
    {
        "module_id": "runtime-governance",
        "architecture_component": "Runtime & Governance policy, lifecycle, QoS, audit",
        "diagram_group": "middle-layer",
        "yellow_sun_portable": True,
        "current_state": "active-prototype",
        "ready_for": ["shared governance socket smoke", "audit persistence sample", "migration/deployment planning"],
        "not_ready_for": ["production governance backend", "platform credential authority", "ASIL/SOTIF safety monitor"],
        "android_primary_surface": "precheckGovernanceJson plus governance contract query methods",
        "linux_sync_surface": "governance-precheck and governance diagnostics over CLI/IPC/gRPC",
        "open_deviations": ["DEV-001", "DEV-002"],
        "open_issues": ["ISSUE-014"],
        "req_ids": ["XSC-005", "NV-G-002", "NV-G-004", "NV-G-005", "NV-G-007", "DEL-003", "DEL-004"],
    },
    {
        "module_id": "protocol-binding",
        "architecture_component": "Protocol Binding for REST, Android Binder, Linux IPC, Linux gRPC/RPC",
        "diagram_group": "binding-layer",
        "yellow_sun_portable": True,
        "current_state": "sample-proxy-surfaces",
        "ready_for": ["Binder AIDL review", "Unix socket smoke", "JSON TCP gRPC contract smoke"],
        "not_ready_for": ["production Binder registration", "true grpcio/C++ gRPC runtime", "MQTT/SOME-IP/DDS transports"],
        "android_primary_surface": "ICentralBrainGateway AIDL and debug Console APK",
        "linux_sync_surface": "central_brain_cli.py, IPC daemon/client, gRPC JSON sample",
        "open_deviations": ["DEV-001", "DEV-011"],
        "open_issues": ["ISSUE-013"],
        "req_ids": ["XSC-006", "NV-P-002", "NV-P-003", "NV-P-005", "NV-P-006", "DEL-001", "DEL-002"],
    },
    {
        "module_id": "native-adapters-driver-hal",
        "architecture_component": "AIOS Kernel native adapters and Driver/HAL boundary",
        "diagram_group": "native-layer",
        "yellow_sun_portable": True,
        "current_state": "read-only-signal-catalog-validation-and-gap-backlog",
        "ready_for": ["Vehicle Signal catalog review", "Vehicle Signal activation criteria review", "read-bridge validation evidence review", "Driver/HAL ownership review", "Android/Linux adapter planning", "gap triage"],
        "not_ready_for": ["vendor HAL calls", "VHAL/DBC integration", "kernel or driver implementation"],
        "android_primary_surface": "getNativeAdaptersDetailJson, getDriverHalGapsJson, getVehicleSignalsJson, getVehicleSignalActivationJson, getVehicleSignalValidationJson",
        "linux_sync_surface": "native-adapters-detail, driver-gaps, vehicle-signals, vehicle-signal-activation, and vehicle-signal-validation over CLI/IPC/gRPC where exposed",
        "open_deviations": ["DEV-004", "DEV-014"],
        "open_issues": [],
        "req_ids": ["XSC-004", "NV-F-003", "NV-F-004", "NV-F-005", "NV-F-011", "NV-P-001", "KH-003", "KH-006", "KH-007", "DEL-005"],
    },
    {
        "module_id": "hardware-empty-interfaces",
        "architecture_component": "External PCIe NPU, vehicle bus, sensors, Ethernet, shared-memory safety runtime interfaces",
        "diagram_group": "hardware-boundary",
        "yellow_sun_portable": True,
        "current_state": "empty-interface-registry-with-activation-checklist-contract",
        "ready_for": ["interface ownership review", "hardware activation checklist review", "future HAL/vendor SDK scoping", "no-hardware smoke validation"],
        "not_ready_for": ["real PCIe NPU runtime", "DMA/IOMMU access", "Safety Runtime shared-memory bridge"],
        "android_primary_surface": "getHardwareInterfacesJson, getHardwareInterfaceActivationChecklistJson",
        "linux_sync_surface": "hardware-interfaces and hardware-interface-activation-checklist over CLI/IPC/gRPC",
        "open_deviations": ["DEV-005", "DEV-016"],
        "open_issues": ["ISSUE-016"],
        "req_ids": ["HW-002", "KH-003", "KH-006", "KH-007", "DEL-005", "XSC-004", "XSC-006"],
    },
]


class PrototypeReadinessRegistry:
    """Read-only maturity view for product, architecture, and delivery tracking."""

    def readiness_payload(self) -> dict[str, Any]:
        modules = copy.deepcopy(PROTOTYPE_MODULES)
        states: dict[str, int] = {}
        for module in modules:
            state = module["current_state"]
            states[state] = states.get(state, 0) + 1

        return {
            "maturity_view": "python-prototype-contract-demo",
            "architecture_baseline": "user-provided Central Brain architecture diagram is the binding requirement baseline",
            "modules": modules,
            "summary": {
                "module_count": len(modules),
                "state_counts": states,
                "python_prototype_ready_for_contract_demo": True,
                "production_ready": False,
                "android_primary_path_ready": True,
                "linux_sync_path_ready": True,
                "hardware_accessed": False,
                "driver_development_triggered": False,
                "virtualization_development_triggered": False,
                "service_dispatch_triggered": False,
            },
            "api_surface": {
                "rest": "GET /prototype/readiness",
                "android_binder": "getPrototypeReadinessJson",
                "linux_cli": "prototype-readiness",
                "linux_ipc": "prototype.readiness.get",
                "linux_grpc_rpc": "CentralBrainGateway.GetPrototypeReadiness",
            },
            "validation": [
                "python3 -m json.tool central-brain/contracts/central_brain_api.json",
                "python3 -m py_compile central-brain/backend/*.py central-brain/linux-cli/central_brain_cli.py central-brain/bindings/linux/ipc/*.py central-brain/bindings/linux/grpc/*.py",
                "bash tools/check_central_brain_binding_artifacts.sh",
                "bash tools/smoke_central_brain_semantic_gateway.sh",
                "bash tools/smoke_central_brain_linux_ipc.sh",
                "bash tools/smoke_central_brain_linux_grpc.sh",
                "bash tools/build_central_brain_console.sh",
            ],
            "tracked_open_deviations": sorted(
                {
                    deviation
                    for module in modules
                    for deviation in module.get("open_deviations", [])
                }
            ),
            "tracked_open_issues": sorted(
                {
                    issue
                    for module in modules
                    for issue in module.get("open_issues", [])
                }
            ),
            "next_increment_candidates": [
                {
                    "candidate": "hardware interface owner decision status rollup",
                    "reason": "HW-002/KH activation gates now exist; the next safe step is a no-hardware rollup of remaining owner, ABI, safety, Driver/HAL gap, and smoke evidence decisions.",
                    "req_ids": ["HW-002", "KH-003", "KH-006", "KH-007", "DEL-005"],
                },
                {
                    "candidate": "event subscription activation evidence decision status rollup",
                    "reason": "FW-U-003/NV-P-006 now expose intake, review status, and retention checklist; the next event-safe step is a no-store rollup of remaining owner decisions without creating evidence persistence or broker activation.",
                    "req_ids": ["XSC-002", "FW-U-003", "XSC-005", "NV-P-002", "NV-P-003", "NV-P-006", "DEL-001", "DEL-002", "DEL-004"],
                },
            ],
            "non_goals": [
                "No virtualization implementation in the Python prototype.",
                "No real Driver/HAL, VHAL, PCIe NPU, DMA, camera/audio/sensor HAL, or vehicle network access.",
                "No production Android system service registration, SELinux policy, signing, or privileged deployment.",
                "No production gRPC runtime claim while grpcio/C++ gRPC is unavailable in this workspace.",
            ],
            "req_ids": PROTOTYPE_REQ_IDS,
        }
