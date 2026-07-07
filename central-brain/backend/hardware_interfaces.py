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
