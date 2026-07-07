#!/usr/bin/env python3
"""Hardware empty-interface registry for the Central Brain prototype.

Req IDs: XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-001, DEL-002,
DEL-005.

This module intentionally exposes contracts only. It must not open device
nodes, call vendor HALs, probe VHAL, allocate DMA buffers, or touch
virtualization APIs in the Python prototype.
"""

from __future__ import annotations

import copy
from typing import Any


HARDWARE_REQ_IDS = [
    "XSC-004",
    "XSC-006",
    "HW-002",
    "KH-001",
    "KH-002",
    "KH-003",
    "KH-006",
    "KH-007",
    "APP-004",
    "FW-U-003",
    "FW-U-004",
    "NV-F-004",
    "NV-F-005",
    "NV-F-006",
    "NV-F-011",
    "NV-P-001",
    "NV-P-006",
    "HV-001",
    "HV-002",
    "HV-003",
    "DEL-001",
    "DEL-002",
    "DEL-005",
]


EMPTY_INTERFACE_REGISTRY: list[dict[str, Any]] = [
    {
        "interface_id": "npu-runtime",
        "name": "External PCIe NPU Runtime",
        "architecture_layer": "AI SDK -> Model Runtime Adapter -> Driver/HAL",
        "implementation_state": "empty-interface",
        "hardware_dependency": "external PCIe NPU accelerator card",
        "android_primary_path": "future vendor HAL/AIDL or JNI bridge behind the AI SDK facade",
        "linux_sync_path": "future device node, vendor daemon, or runtime SDK adapter behind the same AI SDK facade",
        "reserved_methods": [
            {
                "name": "load_model",
                "request_contract": "model_id, model_uri, runtime_policy",
                "response_contract": "model_handle, runtime_capabilities, safety_state",
                "side_effects": "none-in-prototype",
            },
            {
                "name": "infer",
                "request_contract": "model_handle, input_tensors, qos_policy",
                "response_contract": "output_tensors, timing, accelerator_status",
                "side_effects": "none-in-prototype",
            },
            {
                "name": "get_accelerator_status",
                "request_contract": "trace_id",
                "response_contract": "power, temperature, memory, error_state",
                "side_effects": "none-in-prototype",
            },
        ],
        "driver_gap_ids": ["DRV-GAP-001", "DRV-GAP-005"],
        "activation_trigger": "real PCIe NPU card, vendor runtime, IOMMU/DMA policy, and safety isolation requirements are available",
        "req_ids": ["XSC-001", "APP-004", "HW-002", "NV-F-011", "KH-003", "KH-006", "KH-007", "DEL-005"],
    },
    {
        "interface_id": "vehicle-bus",
        "name": "Vehicle Signal and Control Bus",
        "architecture_layer": "Uni Info Bus -> SOA -> AIOS Kernel native adapter -> Driver/HAL",
        "implementation_state": "empty-interface",
        "hardware_dependency": "VHAL, CAN/LIN/FlexRay/Ethernet signal source, DBC or ARXML catalog",
        "android_primary_path": "future Vehicle HAL or platform service adapter mapped to Uni Info Bus objects",
        "linux_sync_path": "future SocketCAN, SOME/IP, vendor daemon, or signal gateway adapter",
        "reserved_methods": [
            {
                "name": "read_signal",
                "request_contract": "signal_name, freshness_ms, safety_state",
                "response_contract": "value, timestamp_ms, source_quality",
                "side_effects": "none-in-prototype",
            },
            {
                "name": "write_actuator",
                "request_contract": "action_name, target, policy_token",
                "response_contract": "accepted, dispatch_id, interlock_state",
                "side_effects": "none-in-prototype",
            },
            {
                "name": "subscribe_signal",
                "request_contract": "topic, qos, backpressure_policy",
                "response_contract": "subscription_id, event_contract",
                "side_effects": "none-in-prototype",
            },
        ],
        "driver_gap_ids": ["DRV-GAP-002", "DRV-GAP-004"],
        "activation_trigger": "target vehicle bus catalog, permission model, and HAL/service transport are selected",
        "req_ids": ["XSC-002", "XSC-003", "XSC-004", "FW-U-003", "FW-U-004", "NV-F-004", "NV-F-005", "KH-006", "DEL-005"],
    },
    {
        "interface_id": "camera-audio-sensors",
        "name": "Cabin Camera, Audio, and Sensor Inputs",
        "architecture_layer": "Application layer -> AI SDK -> Uni Info Bus event/context objects",
        "implementation_state": "empty-interface",
        "hardware_dependency": "camera HAL, audio capture path, IMU/sensor HAL, privacy gate",
        "android_primary_path": "future Android Camera/Audio/Sensor APIs or vendor HAL under policy gate",
        "linux_sync_path": "future V4L2, ALSA/PipeWire, sensor hub, or vendor daemon adapter",
        "reserved_methods": [
            {
                "name": "open_stream",
                "request_contract": "stream_type, purpose, privacy_scope",
                "response_contract": "stream_handle, format, safety_state",
                "side_effects": "none-in-prototype",
            },
            {
                "name": "read_frame_or_sample",
                "request_contract": "stream_handle, deadline_ms",
                "response_contract": "buffer_ref, metadata, redaction_state",
                "side_effects": "none-in-prototype",
            },
        ],
        "driver_gap_ids": ["DRV-GAP-003"],
        "activation_trigger": "target cockpit sensors, privacy rules, and data-plane runtime are selected",
        "req_ids": ["APP-004", "NV-F-006", "KH-003", "KH-006", "DEL-005"],
    },
    {
        "interface_id": "ethernet-someip-dds-tsn",
        "name": "Vehicle Ethernet, SOME/IP, DDS, and TSN Binding",
        "architecture_layer": "Protocol Binding -> SOA/Uni Info Bus event data plane",
        "implementation_state": "empty-interface",
        "hardware_dependency": "vehicle Ethernet NIC, SOME/IP stack, DDS runtime, TSN QoS policy",
        "android_primary_path": "future native transport service exposed through Binder or AIOS Kernel adapter",
        "linux_sync_path": "future SOME/IP/DDS daemon or C++ binding beside the Python prototype contract",
        "reserved_methods": [
            {
                "name": "publish_topic",
                "request_contract": "topic, payload, qos, safety_state",
                "response_contract": "accepted, sequence_id, transport_state",
                "side_effects": "none-in-prototype",
            },
            {
                "name": "call_service",
                "request_contract": "service_id, method, payload, policy_token",
                "response_contract": "result, transport_status, audit_id",
                "side_effects": "none-in-prototype",
            },
        ],
        "driver_gap_ids": ["DRV-GAP-004"],
        "activation_trigger": "target network stack, service discovery source, and QoS/backpressure rules are selected",
        "req_ids": ["XSC-003", "XSC-006", "NV-P-001", "NV-P-006", "KH-001", "KH-003", "DEL-005"],
    },
    {
        "interface_id": "shared-memory-safety-runtime",
        "name": "Shared Memory and Safety Runtime Boundary",
        "architecture_layer": "Runtime & Governance -> AIOS Kernel -> virtualization boundary",
        "implementation_state": "empty-interface",
        "hardware_dependency": "shared memory allocator, safety monitor, isolation policy, hypervisor or OS partitioning",
        "android_primary_path": "future platform-owned safety service or native shared-memory bridge",
        "linux_sync_path": "future memfd/hugepage/vendor shared-memory bridge controlled by safety policy",
        "reserved_methods": [
            {
                "name": "reserve_buffer",
                "request_contract": "size, producer, consumer, safety_class",
                "response_contract": "buffer_handle, permissions, lifetime",
                "side_effects": "none-in-prototype",
            },
            {
                "name": "report_safety_state",
                "request_contract": "component, state, evidence",
                "response_contract": "accepted, mitigation, audit_id",
                "side_effects": "none-in-prototype",
            },
        ],
        "driver_gap_ids": ["DRV-GAP-005"],
        "activation_trigger": "platform safety owner defines shared-memory, ASIL/QM, and isolation rules",
        "req_ids": ["XSC-005", "KH-002", "KH-007", "HV-001", "HV-002", "HV-003", "DEL-005"],
    },
]

HARDWARE_ACTIVATION_REQ_IDS = [
    "XSC-004",
    "XSC-006",
    "HW-002",
    "KH-003",
    "KH-006",
    "KH-007",
    "DEL-001",
    "DEL-002",
    "DEL-005",
]

HARDWARE_ACTIVATION_GATES = [
    {
        "gate_id": "HW-ACT-001",
        "name": "target-interface-owner-assigned",
        "required_evidence": "Target Android/Linux owner, process boundary, and escalation path are assigned for each hardware interface.",
        "passed": False,
    },
    {
        "gate_id": "HW-ACT-002",
        "name": "driver-gap-reviewed",
        "required_evidence": "Linked DRV-GAP items have target platform evidence and minimal Driver/HAL development decision.",
        "passed": False,
    },
    {
        "gate_id": "HW-ACT-003",
        "name": "android-abi-contract-approved",
        "required_evidence": "Android HAL/AIDL/NDK/vendor SDK ABI, permission model, and Binder identity mapping are approved.",
        "passed": False,
    },
    {
        "gate_id": "HW-ACT-004",
        "name": "linux-abi-contract-approved",
        "required_evidence": "Linux device node, ioctl/sysfs/vendor library, daemon, or IPC ABI and service identity are approved.",
        "passed": False,
    },
    {
        "gate_id": "HW-ACT-005",
        "name": "safety-and-policy-binding-approved",
        "required_evidence": "Safety State, Policy, Runtime & Governance audit, fault fallback, and ASIL/QM assumptions are approved.",
        "passed": False,
    },
    {
        "gate_id": "HW-ACT-006",
        "name": "smoke-test-harness-defined",
        "required_evidence": "No-hardware prototype smoke plus target hardware smoke commands and pass/fail evidence format are defined.",
        "passed": False,
    },
    {
        "gate_id": "HW-ACT-007",
        "name": "rollback-and-fault-semantics-approved",
        "required_evidence": "Timeout, reset, degrade, retry, rollback, and telemetry behavior are approved for target hardware.",
        "passed": False,
    },
    {
        "gate_id": "HW-ACT-008",
        "name": "no-hardware-access-in-prototype",
        "required_evidence": "Prototype exposes activation criteria only and reports no device, HAL, vendor SDK, shared memory, or virtualization access.",
        "passed": True,
    },
]

HARDWARE_OWNER_DECISION_REQ_IDS = HARDWARE_ACTIVATION_REQ_IDS

HARDWARE_OWNER_DECISION_GATES = [
    {
        "gate_id": "HW-ODS-001",
        "name": "target-interface-owner-open",
        "required_decision": "Assign the target interface owner and escalation path for each hardware interface.",
        "status": "open",
        "passed": False,
    },
    {
        "gate_id": "HW-ODS-002",
        "name": "android-abi-owner-open",
        "required_decision": "Assign the Android HAL/AIDL/NDK/vendor SDK ABI owner and permission model reviewer.",
        "status": "open",
        "passed": False,
    },
    {
        "gate_id": "HW-ODS-003",
        "name": "linux-abi-owner-open",
        "required_decision": "Assign the Linux device node, ioctl/sysfs/vendor library, daemon, or IPC ABI owner.",
        "status": "open",
        "passed": False,
    },
    {
        "gate_id": "HW-ODS-004",
        "name": "driver-gap-owner-open",
        "required_decision": "Assign owner review for linked Driver/HAL gap IDs and minimal new development decisions.",
        "status": "open",
        "passed": False,
    },
    {
        "gate_id": "HW-ODS-005",
        "name": "safety-policy-owner-open",
        "required_decision": "Assign Safety/Policy owner for fault isolation, ASIL/QM assumptions, and audit behavior.",
        "status": "open",
        "passed": False,
    },
    {
        "gate_id": "HW-ODS-006",
        "name": "target-smoke-evidence-open",
        "required_decision": "Attach target hardware smoke evidence owner and pass/fail evidence format.",
        "status": "open",
        "passed": False,
    },
    {
        "gate_id": "HW-ODS-007",
        "name": "rollback-fault-semantics-open",
        "required_decision": "Assign owner for timeout, reset, degrade, retry, rollback, and telemetry semantics.",
        "status": "open",
        "passed": False,
    },
    {
        "gate_id": "HW-ODS-008",
        "name": "no-hardware-access-in-prototype",
        "required_decision": "Keep this Python prototype contract-only until target evidence is reviewed.",
        "status": "satisfied-by-prototype-boundary",
        "passed": True,
    },
]

HARDWARE_OWNER_DECISION_ITEMS = [
    {
        "decision_id": "target_interface_owner",
        "gate_id": "HW-ODS-001",
        "owner_status": "open",
        "required_owner": "target Android/Linux hardware-interface owner",
    },
    {
        "decision_id": "android_abi_owner",
        "gate_id": "HW-ODS-002",
        "owner_status": "open",
        "required_owner": "Android HAL/AIDL/NDK/vendor SDK ABI owner",
    },
    {
        "decision_id": "linux_abi_owner",
        "gate_id": "HW-ODS-003",
        "owner_status": "open",
        "required_owner": "Linux device node/ioctl/sysfs/vendor daemon ABI owner",
    },
    {
        "decision_id": "driver_hal_gap_owner",
        "gate_id": "HW-ODS-004",
        "owner_status": "open",
        "required_owner": "Driver/HAL gap reviewer",
    },
    {
        "decision_id": "safety_policy_owner",
        "gate_id": "HW-ODS-005",
        "owner_status": "open",
        "required_owner": "Safety/Policy binding owner",
    },
    {
        "decision_id": "target_smoke_evidence_owner",
        "gate_id": "HW-ODS-006",
        "owner_status": "open",
        "required_owner": "target hardware smoke evidence owner",
    },
    {
        "decision_id": "rollback_fault_semantics_owner",
        "gate_id": "HW-ODS-007",
        "owner_status": "open",
        "required_owner": "rollback and fault semantics owner",
    },
]


class HardwareInterfaceRegistry:
    """Read-only registry for hardware-dependent empty interfaces."""

    def summary_payload(self) -> dict[str, Any]:
        interfaces = copy.deepcopy(EMPTY_INTERFACE_REGISTRY)
        return {
            "interface_count": len(interfaces),
            "interfaces": [item["interface_id"] for item in interfaces],
            "implementation_state": "empty-interface-registry",
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
            "req_ids": HARDWARE_REQ_IDS,
        }

    def activation_checklist_payload(self) -> dict[str, Any]:
        interfaces = copy.deepcopy(EMPTY_INTERFACE_REGISTRY)
        gate_ids = [gate["gate_id"] for gate in HARDWARE_ACTIVATION_GATES]
        per_interface = []
        for item in interfaces:
            per_interface.append(
                {
                    "interface_id": item["interface_id"],
                    "name": item["name"],
                    "implementation_state": item["implementation_state"],
                    "activation_state": "blocked-pending-target-platform-evidence",
                    "activation_trigger": item["activation_trigger"],
                    "driver_gap_ids": item["driver_gap_ids"],
                    "required_owner_decisions": [
                        "target_interface_owner",
                        "android_abi_owner",
                        "linux_abi_owner",
                        "driver_hal_gap_owner",
                        "safety_policy_owner",
                        "test_harness_owner",
                    ],
                    "required_gate_ids": gate_ids,
                    "android_primary_path": item["android_primary_path"],
                    "linux_sync_path": item["linux_sync_path"],
                    "activation_allowed": False,
                    "hardware_accessed": False,
                    "driver_development_triggered": False,
                    "virtualization_development_triggered": False,
                }
            )

        return {
            "activation_checklist_state": "contract-only-no-hardware-activation",
            "activation_allowed": False,
            "interfaces": per_interface,
            "mandatory_gates": copy.deepcopy(HARDWARE_ACTIVATION_GATES),
            "owner_decision_shape": {
                "required_fields": [
                    "interface_id",
                    "target_owner",
                    "android_abi_owner",
                    "linux_abi_owner",
                    "driver_gap_owner",
                    "safety_policy_owner",
                    "test_harness_owner",
                    "evidence_refs",
                ],
                "owner_decision_complete": False,
            },
            "test_evidence_shape": {
                "prototype_smoke_required": [
                    "GET /hardware/interfaces",
                    "GET /hardware/interfaces/activation-checklist",
                    "Android Binder getHardwareInterfaceActivationChecklistJson",
                    "Linux CLI hardware-interface-activation-checklist",
                    "Linux IPC hardware.interfaces.activation.checklist",
                    "Linux gRPC/RPC GetHardwareInterfaceActivationChecklist",
                ],
                "target_hardware_smoke_required_before_activation": [
                    "device-discovery-without-root-bypass",
                    "permission-denied-negative-test",
                    "fault-timeout-reset-test",
                    "audit-trace-export-test",
                    "android-linux-contract-parity-test",
                ],
                "target_hardware_smoke_attached": False,
            },
            "api_surface": {
                "rest": "GET /hardware/interfaces/activation-checklist",
                "android_binder": "getHardwareInterfaceActivationChecklistJson",
                "linux_cli": "hardware-interface-activation-checklist",
                "linux_ipc": "hardware.interfaces.activation.checklist",
                "linux_grpc_rpc": "CentralBrainGateway.GetHardwareInterfaceActivationChecklist",
            },
            "summary": {
                "hardware_activation_checklist_active": True,
                "owner_decision_complete": False,
                "android_abi_confirmed": False,
                "linux_abi_confirmed": False,
                "driver_gap_review_complete": False,
                "safety_policy_binding_confirmed": False,
                "target_hardware_smoke_attached": False,
                "activation_allowed": False,
                "hardware_accessed": False,
                "driver_development_triggered": False,
                "virtualization_development_triggered": False,
                "service_dispatch_triggered": False,
            },
            "req_ids": HARDWARE_ACTIVATION_REQ_IDS,
        }

    def owner_decision_status_payload(self) -> dict[str, Any]:
        interfaces = copy.deepcopy(EMPTY_INTERFACE_REGISTRY)
        open_gate_ids = [gate["gate_id"] for gate in HARDWARE_OWNER_DECISION_GATES if not gate["passed"]]
        per_interface = []
        for item in interfaces:
            per_interface.append(
                {
                    "interface_id": item["interface_id"],
                    "name": item["name"],
                    "implementation_state": item["implementation_state"],
                    "decision_state": "blocked-owner-decisions-open",
                    "activation_trigger": item["activation_trigger"],
                    "driver_gap_ids": item["driver_gap_ids"],
                    "owner_decisions": copy.deepcopy(HARDWARE_OWNER_DECISION_ITEMS),
                    "open_gate_ids": open_gate_ids,
                    "android_primary_path": item["android_primary_path"],
                    "linux_sync_path": item["linux_sync_path"],
                    "activation_allowed": False,
                    "hardware_accessed": False,
                    "driver_development_triggered": False,
                    "virtualization_development_triggered": False,
                }
            )

        return {
            "owner_decision_status_state": "contract-only-owner-decisions-open",
            "activation_allowed": False,
            "interfaces": per_interface,
            "decision_gates": copy.deepcopy(HARDWARE_OWNER_DECISION_GATES),
            "rollup": {
                "open_decision_count": len(HARDWARE_OWNER_DECISION_ITEMS) * len(interfaces),
                "open_gate_ids": open_gate_ids,
                "blocked_interface_count": len(interfaces),
                "blocking_sources": [
                    "target_interface_owner",
                    "android_abi_owner",
                    "linux_abi_owner",
                    "driver_hal_gap_owner",
                    "safety_policy_owner",
                    "target_smoke_evidence_owner",
                    "rollback_fault_semantics_owner",
                ],
            },
            "api_surface": {
                "rest": "GET /hardware/interfaces/owner-decision-status",
                "android_binder": "getHardwareInterfaceOwnerDecisionStatusJson",
                "linux_cli": "hardware-interface-owner-decision-status",
                "linux_ipc": "hardware.interfaces.owner.decision.status",
                "linux_grpc_rpc": "CentralBrainGateway.GetHardwareInterfaceOwnerDecisionStatus",
            },
            "summary": {
                "owner_decision_status_active": True,
                "all_required_owners_assigned": False,
                "target_interface_owner_assigned": False,
                "android_abi_owner_assigned": False,
                "linux_abi_owner_assigned": False,
                "driver_gap_owner_assigned": False,
                "safety_policy_owner_assigned": False,
                "target_hardware_smoke_attached": False,
                "rollback_fault_semantics_confirmed": False,
                "activation_allowed": False,
                "hardware_accessed": False,
                "driver_development_triggered": False,
                "virtualization_development_triggered": False,
                "service_dispatch_triggered": False,
            },
            "req_ids": HARDWARE_OWNER_DECISION_REQ_IDS,
        }

    def interfaces_payload(self) -> dict[str, Any]:
        interfaces = copy.deepcopy(EMPTY_INTERFACE_REGISTRY)
        return {
            "interfaces": interfaces,
            "summary": {
                "interface_count": len(interfaces),
                "implementation_state": "empty-interface-registry",
                "hardware_accessed": False,
                "driver_development_triggered": False,
                "virtualization_development_triggered": False,
                "service_dispatch_triggered": False,
                "android_primary_path": "Binder-facing Android prototype remains primary; real hardware routes stay behind future platform HAL/native adapters.",
                "linux_sync_path": "Linux CLI/IPC/gRPC samples expose the same read-only interface catalog without touching devices.",
            },
            "global_constraints": [
                "Python prototype must not access real PCIe NPU, VHAL, camera/audio/sensor HAL, vehicle Ethernet, shared memory, or virtualization APIs.",
                "Reserved methods are interface contracts only and are not callable hardware dispatch points in this increment.",
                "Driver/HAL implementation starts only after a documented platform gap cannot be satisfied by current Android/Linux facilities.",
                "Yellow-sun portable components must keep Android and Linux binding visibility aligned.",
            ],
            "api_surface": {
                "rest": "GET /hardware/interfaces",
                "android_binder": "getHardwareInterfacesJson",
                "linux_cli": "hardware-interfaces",
                "linux_ipc": "hardware.interfaces.get",
                "linux_grpc_rpc": "CentralBrainGateway.GetHardwareInterfaces",
            },
            "req_ids": HARDWARE_REQ_IDS,
        }
