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
            "req_ids": NATIVE_ADAPTER_REQ_IDS + ["DEL-001", "DEL-002", "DEL-005"],
        }
