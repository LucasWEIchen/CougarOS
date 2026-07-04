#!/usr/bin/env python3
"""Native adapter registry for the Central Brain prototype."""

from __future__ import annotations

import copy
from typing import Any


NATIVE_ADAPTER_REQ_IDS = [
    "XSC-004",
    "NV-F-001",
    "NV-F-003",
    "NV-F-004",
    "NV-F-005",
    "NV-F-008",
    "NV-F-009",
    "NV-F-011",
]

DRIVER_GAP_REQ_IDS = [
    "KH-003",
    "KH-006",
    "KH-007",
    "DEL-001",
    "DEL-002",
    "DEL-005",
]

NATIVE_ADAPTERS: list[dict[str, Any]] = [
    {
        "name": "aios-kernel",
        "status": "contract-mock",
        "layer": "Native/AIOS Kernel",
        "ownership": "platform",
        "semantic_entrypoints": ["Tool", "Service", "Permission", "Action"],
        "android_delivery": "native service adapter behind Android client SDK",
        "linux_delivery": "daemon module behind Linux CLI/IPC client",
        "responsibilities": [
            "agent task dispatch boundary",
            "tool and memory routing boundary",
            "model request normalization",
            "safety context propagation",
        ],
        "req_ids": ["XSC-004", "NV-F-001"],
    },
    {
        "name": "soa-service-adapter",
        "status": "active-prototype",
        "layer": "Native/SOA Service Runtime and Service Adapters",
        "ownership": "platform",
        "semantic_entrypoints": ["POST /soa/invoke", "GET /soa/services"],
        "android_delivery": "Binder service stub target; REST prototype currently active",
        "linux_delivery": "Unix socket or gRPC daemon target; REST prototype currently active",
        "responsibilities": [
            "map SOA service contracts to local handlers",
            "preserve Registry/Discovery/Policy/Lifecycle/Audit precheck",
            "keep Business/Foundation/Atomic service domains explicit",
        ],
        "req_ids": ["XSC-003", "NV-F-003", "NV-F-008", "FW-S-001", "FW-S-004", "FW-S-005"],
    },
    {
        "name": "vehicle-signal-adapter",
        "status": "mock-vss-snapshot",
        "layer": "Native/Vehicle Body Signal and ECU Proxy",
        "ownership": "ecosystem-adapter",
        "semantic_entrypoints": ["GET /uib/context", "GET /uib/state", "POST /soa/invoke vehicle-state"],
        "android_delivery": "VHAL/AIDL bridge contract after signal catalog is available",
        "linux_delivery": "SocketCAN/DBC/ARXML gateway contract after signal catalog is available",
        "responsibilities": [
            "normalize BCM/HVAC/Seat/Door/Light signals",
            "track signal quality and timestamp metadata",
            "map future DBC/ARXML/VHAL paths into Uni Info Bus Context and State",
        ],
        "driver_hal_dependency": {
            "current": "none; mock VSS-style signals only",
            "android_target": "Vehicle HAL or vendor AIDL service",
            "linux_target": "SocketCAN, vendor gateway, or SOME/IP service",
        },
        "req_ids": ["NV-F-004", "NV-F-005", "KH-006"],
    },
    {
        "name": "model-runtime-adapter",
        "status": "mock-npu-runtime",
        "layer": "Native/Model Runtime Adapter",
        "ownership": "platform",
        "semantic_entrypoints": ["POST /soa/invoke npu-inference", "GET /npu/status"],
        "android_delivery": "vendor SDK JNI/native bridge or Android NDK service target",
        "linux_delivery": "vendor runtime library or PCIe userspace daemon target",
        "responsibilities": [
            "discover runtime backend",
            "load and unload model handles",
            "create inference sessions with QoS and safety state",
            "normalize GPU/NPU/CPU/Cloud fallback results",
        ],
        "driver_hal_dependency": {
            "current": "none; Python mock and optional PCI sysfs enumeration only",
            "android_target": "NPU HAL/AIDL/NDK bridge or vendor SDK",
            "linux_target": "/dev node, ioctl, sysfs, vendor runtime library, or PCIe daemon",
        },
        "req_ids": ["NV-F-011", "KH-003", "KH-006", "HW-002"],
    },
    {
        "name": "security-policy-adapter",
        "status": "active-prototype",
        "layer": "Native/Security and Policy Adapter",
        "ownership": "ecosystem-adapter",
        "semantic_entrypoints": ["POST /policy/evaluate", "POST /soa/invoke"],
        "android_delivery": "Binder caller identity plus platform policy context",
        "linux_delivery": "process identity plus daemon policy context",
        "responsibilities": [
            "map caller permissions into Runtime & Governance",
            "propagate Safety State decisions",
            "keep ASIL/QM assumptions documented without implementing virtualization",
        ],
        "req_ids": ["NV-F-009", "NV-G-005", "FW-U-007", "FW-S-005", "HV-002"],
    },
]

DRIVER_HAL_GAPS: list[dict[str, Any]] = [
    {
        "gap_id": "DRV-GAP-001",
        "interface_domain": "npu",
        "status": "open",
        "current_environment": "mock NPU runtime only; no real PCIe NPU driver, HAL, or vendor SDK",
        "android_target": "NPU HAL/AIDL/NDK bridge or vendor SDK JNI/native bridge",
        "linux_target": "/dev node, ioctl, sysfs, vendor runtime library, or PCIe userspace daemon",
        "trigger_condition": "user provides real PCIe NPU hardware, vendor id, SDK, and driver ABI",
        "minimal_development": "model-runtime-adapter bridge plus Driver/HAL adapter shim; no direct App or SOA vendor SDK calls",
        "mock_boundary": "GET /npu/status and POST /soa/invoke npu-inference remain mock-only",
        "forbidden_shortcut": "do not bypass Model Runtime Adapter, Runtime & Governance, or SOA policy checks",
        "req_ids": ["HW-002", "NV-F-011", "KH-003", "KH-006", "KH-007", "DEL-001", "DEL-002", "DEL-005"],
    },
    {
        "gap_id": "DRV-GAP-002",
        "interface_domain": "vehicle-bus",
        "status": "open",
        "current_environment": "mock VSS-style state only; no VHAL, SocketCAN, DBC, ARXML, or vendor gateway",
        "android_target": "Vehicle HAL or vendor AIDL service behind Vehicle Signal Adapter",
        "linux_target": "SocketCAN, DBC/ARXML parser, SOME/IP service, or vendor gateway behind Vehicle Signal Adapter",
        "trigger_condition": "target vehicle signal catalog, DBC/ARXML, VHAL contract, or gateway access becomes available",
        "minimal_development": "Vehicle Signal Adapter mapping table and read-only signal bridge before any control path",
        "mock_boundary": "Uni Info Bus Context/State and Action mock do not dispatch to vehicle bus",
        "forbidden_shortcut": "do not let Actions call CAN/VHAL directly without Policy and Safety State",
        "req_ids": ["NV-F-004", "NV-F-005", "FW-U-004", "FW-U-007", "KH-006", "DEL-001", "DEL-002", "DEL-005"],
    },
    {
        "gap_id": "DRV-GAP-003",
        "interface_domain": "camera-audio-sensors",
        "status": "planned",
        "current_environment": "no real vehicle camera, mic array, radar, USS, or IMU source is connected",
        "android_target": "Camera2/Camera HAL, Audio HAL, AAudio, or vendor sensor service",
        "linux_target": "V4L2, ALSA/PipeWire, or vendor sensor SDK",
        "trigger_condition": "multimodal Agent or ADAS adapter requires real sensor frames or audio capture",
        "minimal_development": "Sensor/Actuator adapter read path with timestamp and quality metadata",
        "mock_boundary": "AI SDK/Agent contract mocks must not imply real sensor ingestion",
        "forbidden_shortcut": "do not pass raw sensor frames outside Policy/Privacy controls",
        "req_ids": ["NV-F-002", "NV-F-006", "APP-004", "KH-003", "KH-006", "DEL-001", "DEL-002"],
    },
    {
        "gap_id": "DRV-GAP-004",
        "interface_domain": "ethernet-someip-dds-tsn",
        "status": "planned",
        "current_environment": "WSL network is enough for REST/JSON samples only; no SOME/IP, DDS, TSN, or PTP stack is validated",
        "android_target": "platform network service or native binding under Protocol Binding",
        "linux_target": "netdev/socket, SOME/IP stack, DDS runtime, linuxptp, or vendor middleware",
        "trigger_condition": "cross-ECU services, high-rate topics, or time-synchronized data become required",
        "minimal_development": "Protocol Binding adapter and time-sync metadata contract before production traffic",
        "mock_boundary": "Event active mock is bounded recent-log, not high-rate DDS data plane",
        "forbidden_shortcut": "do not treat REST prototype as vehicle network binding",
        "req_ids": ["NV-P-001", "NV-P-006", "NV-F-006", "KH-001", "KH-003", "DEL-002", "DEL-004"],
    },
    {
        "gap_id": "DRV-GAP-005",
        "interface_domain": "shared-memory-safety-runtime",
        "status": "planned",
        "current_environment": "no shared memory, DMA-BUF, ashmem, memfd, IOMMU, or Safety Runtime integration is active",
        "android_target": "AIDL shared memory, HardwareBuffer, Safety State service, or target safety runtime bridge",
        "linux_target": "POSIX shm, memfd, DMA-BUF, IOMMU-aware buffer lifecycle, or safety daemon",
        "trigger_condition": "high-throughput model/sensor data or ASIL/QM domain integration requires zero-copy or safety runtime",
        "minimal_development": "buffer envelope and safety state bridge after target platform constraints are known",
        "mock_boundary": "virtualization remains documentation-only and no shared-memory driver is added in this increment",
        "forbidden_shortcut": "do not implement cross-VM or safety isolation code in this project scope",
        "req_ids": ["KH-002", "KH-007", "HV-001", "HV-002", "HV-003", "DEL-004", "DEL-005"],
    },
]


class NativeAdapterRegistry:
    """In-process Native adapter catalog for architecture tracking."""

    def list_payload(self) -> dict[str, Any]:
        adapters = copy.deepcopy(NATIVE_ADAPTERS)
        return {
            "adapters": adapters,
            "domains": {
                "platform": [adapter["name"] for adapter in adapters if adapter["ownership"] == "platform"],
                "ecosystem-adapter": [
                    adapter["name"] for adapter in adapters if adapter["ownership"] == "ecosystem-adapter"
                ],
            },
            "req_ids": NATIVE_ADAPTER_REQ_IDS,
        }

    def detail_payload(self) -> dict[str, Any]:
        adapters = copy.deepcopy(NATIVE_ADAPTERS)
        return {
            "adapters": adapters,
            "driver_hal_gap_backlog": copy.deepcopy(DRIVER_HAL_GAPS),
            "constraints": [
                "Native adapters cannot bypass Uni Info Bus semantic objects or SOA policy checks.",
                "Driver/HAL access remains documented only until Android/Linux environment capability is insufficient.",
                "Virtualization, ASIL/QM isolation, and cross-VM communication are deployment assumptions in this increment.",
                "Yellow-sun cross-SoC components keep Android and Linux delivery paths explicit.",
            ],
            "android_primary_path": [
                "Android app or SDK client",
                "Uni Info Bus semantic entry",
                "SOA service entry",
                "Native adapter registry",
                "future Binder/native service integration",
            ],
            "linux_sync_path": [
                "Linux CLI or daemon client",
                "Uni Info Bus semantic entry",
                "SOA service entry",
                "Native adapter registry",
                "future Unix socket or gRPC daemon integration",
            ],
            "req_ids": NATIVE_ADAPTER_REQ_IDS + DRIVER_GAP_REQ_IDS + ["DEL-001", "DEL-002", "DEL-005"],
        }

    def driver_gap_payload(self) -> dict[str, Any]:
        gaps = copy.deepcopy(DRIVER_HAL_GAPS)
        return {
            "gaps": gaps,
            "summary": {
                "open": sum(1 for gap in gaps if gap["status"] == "open"),
                "planned": sum(1 for gap in gaps if gap["status"] == "planned"),
                "driver_development_triggered": False,
                "current_increment": "contract-backlog-only",
            },
            "constraints": [
                "Driver/HAL work is not a default development track.",
                "Each gap must stay behind Uni Info Bus, SOA, Runtime & Governance, and Native adapter boundaries.",
                "Android primary and Linux sync targets are documented together before any driver code is added.",
                "Virtualization, cross-VM shared memory, and ASIL/QM isolation remain deployment assumptions only.",
            ],
            "android_primary_path": [
                "Android SDK/Binder caller",
                "Uni Info Bus or AI SDK facade",
                "SOA service entry",
                "Native adapter",
                "future HAL/AIDL/vendor bridge only when gap trigger is met",
            ],
            "linux_sync_path": [
                "Linux CLI/IPC/gRPC caller",
                "Uni Info Bus or AI SDK facade",
                "SOA service entry",
                "Native adapter",
                "future device node/vendor daemon only when gap trigger is met",
            ],
            "req_ids": DRIVER_GAP_REQ_IDS + [
                "HW-002",
                "NV-F-002",
                "NV-F-004",
                "NV-F-005",
                "NV-F-006",
                "NV-F-011",
                "NV-P-001",
                "NV-P-006",
                "HV-001",
                "HV-002",
                "HV-003",
            ],
        }
