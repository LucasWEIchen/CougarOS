#!/usr/bin/env python3
"""Protocol Binding registry for the Central Brain prototype."""

from __future__ import annotations

import copy
from typing import Any


BINDING_REQ_IDS = [
    "XSC-006",
    "NV-P-001",
    "NV-P-002",
    "NV-P-003",
    "NV-P-004",
    "NV-P-005",
    "NV-P-006",
]

BINDING_REGISTRY: list[dict[str, Any]] = [
    {
        "name": "rest-http-json",
        "status": "active-prototype",
        "platforms": ["Android emulator", "Linux host"],
        "semantic_paths": ["/uib/context", "/uib/state", "/soa/services", "/soa/invoke"],
        "artifacts": ["central-brain/contracts/central_brain_api.json"],
        "req_ids": ["XSC-006", "NV-P-005"],
    },
    {
        "name": "android-binder-aidl",
        "status": "contract-skeleton",
        "platforms": ["Android"],
        "semantic_paths": [
            "getContextJson -> /uib/context",
            "getStateJson -> /uib/state",
            "invokeServiceJson -> /soa/invoke",
        ],
        "artifacts": [
            "central-brain/bindings/android/aidl/com/centralbrain/binding/ICentralBrainGateway.aidl",
            "central-brain/bindings/android/README.md",
        ],
        "req_ids": ["XSC-002", "XSC-003", "XSC-005", "XSC-006", "NV-P-002", "DEL-001"],
    },
    {
        "name": "linux-ipc",
        "status": "contract-skeleton",
        "platforms": ["Linux"],
        "semantic_paths": [
            "uib.context.get -> /uib/context",
            "uib.state.get -> /uib/state",
            "soa.service.invoke -> /soa/invoke",
        ],
        "artifacts": [
            "central-brain/bindings/linux/ipc/central_brain_ipc_envelope.schema.json",
            "central-brain/bindings/linux/README.md",
        ],
        "req_ids": ["XSC-002", "XSC-003", "XSC-005", "XSC-006", "NV-P-002", "DEL-002"],
    },
    {
        "name": "grpc",
        "status": "contract-skeleton",
        "platforms": ["Android", "Linux"],
        "semantic_paths": [
            "CentralBrainGateway.GetContext -> /uib/context",
            "CentralBrainGateway.GetState -> /uib/state",
            "CentralBrainGateway.InvokeService -> /soa/invoke",
        ],
        "artifacts": ["central-brain/bindings/linux/proto/central_brain_gateway.proto"],
        "req_ids": ["XSC-002", "XSC-003", "XSC-005", "XSC-006", "NV-P-003", "DEL-002"],
    },
    {
        "name": "mqtt",
        "status": "planned-policy-gated",
        "platforms": ["Android", "Linux"],
        "semantic_paths": ["cloud/vehicle message topics via Policy and Privacy checks"],
        "artifacts": [],
        "req_ids": ["XSC-006", "NV-P-004"],
    },
    {
        "name": "someip",
        "status": "planned-after-vehicle-network",
        "platforms": ["Linux", "QNX/RT domain integration assumption"],
        "semantic_paths": ["vehicle cross-ECU services via service discovery"],
        "artifacts": [],
        "req_ids": ["XSC-006", "NV-P-001"],
    },
    {
        "name": "dds",
        "status": "planned-for-high-rate-topics",
        "platforms": ["Linux", "Android native"],
        "semantic_paths": ["high-rate Context/Event topics"],
        "artifacts": [],
        "req_ids": ["XSC-006", "NV-P-006"],
    },
]


class ProtocolBindingRegistry:
    """In-process binding registry for prototype discovery and smoke tests."""

    def list_payload(self) -> dict[str, Any]:
        return {
            "bindings": copy.deepcopy(BINDING_REGISTRY),
            "req_ids": BINDING_REQ_IDS,
        }

    def detail_payload(self) -> dict[str, Any]:
        bindings = copy.deepcopy(BINDING_REGISTRY)
        return {
            "bindings": bindings,
            "active_semantic_entrypoints": sorted(
                {
                    path
                    for binding in bindings
                    for path in binding.get("semantic_paths", [])
                    if path.startswith("/")
                }
            ),
            "contract_artifacts": sorted(
                {
                    artifact
                    for binding in bindings
                    for artifact in binding.get("artifacts", [])
                }
            ),
            "constraints": [
                "Protocol Binding cannot bypass Uni Info Bus semantic objects.",
                "SOA calls remain policy, lifecycle, and audit checked.",
                "Virtualization and driver layers are documented integration assumptions only in this increment.",
            ],
            "req_ids": BINDING_REQ_IDS + ["DEL-001", "DEL-002"],
        }
