#!/usr/bin/env python3
"""Runtime & Governance prototype for the Central Brain backend."""

from __future__ import annotations

import copy
import time
from collections import deque
from typing import Any


GOVERNANCE_REQ_IDS = [
    "XSC-005",
    "NV-G-001",
    "NV-G-002",
    "NV-G-003",
    "NV-G-004",
    "NV-G-005",
    "NV-G-006",
    "NV-G-007",
]

SERVICE_CATALOG: list[dict[str, Any]] = [
    {
        "name": "vehicle-state",
        "version": "0.1.0",
        "domain": "atomic",
        "contract": "GET /vehicle/state",
        "semantic_entry": "POST /soa/invoke service=vehicle-state method=getState",
        "layer": "Framework/SOA Service Entry",
        "req_ids": ["XSC-003", "FW-S-003", "FW-S-004"],
        "permissions": ["vehicle.read"],
        "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
        "qos": {"priority": "vehicle-control", "timeout_ms": 1000},
        "implementation": "mock-handler",
    },
    {
        "name": "npu-inference",
        "version": "0.1.0",
        "domain": "foundation",
        "contract": "POST /ai/infer",
        "semantic_entry": "POST /soa/invoke service=npu-inference method=infer",
        "layer": "Framework/SOA Service Entry -> Native/Model Runtime Adapter",
        "req_ids": ["XSC-003", "FW-S-002", "FW-S-004", "FW-S-005", "NV-F-011"],
        "permissions": ["ai.infer"],
        "allowed_safety_states": ["normal"],
        "qos": {"priority": "ai-task", "timeout_ms": 2000},
        "implementation": "mock-handler",
    },
    {
        "name": "service-registry",
        "version": "0.1.0",
        "domain": "foundation",
        "contract": "GET /services",
        "semantic_entry": "GET /soa/services",
        "layer": "Native/Runtime & Governance",
        "req_ids": ["XSC-005", "FW-S-002", "NV-G-001", "NV-G-002", "NV-G-003"],
        "permissions": ["service.read"],
        "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
        "qos": {"priority": "diagnostic", "timeout_ms": 1000},
        "implementation": "runtime-governance",
    },
    {
        "name": "cabin-comfort-scene",
        "version": "0.1.0",
        "domain": "business",
        "contract": "planned scene orchestration service",
        "semantic_entry": "POST /soa/invoke service=cabin-comfort-scene method=prepare",
        "layer": "Framework/SOA Business Services",
        "req_ids": ["XSC-003", "FW-S-001", "FW-S-004", "FW-S-005"],
        "permissions": ["vehicle.read", "vehicle.control"],
        "allowed_safety_states": ["normal"],
        "qos": {"priority": "vehicle-control", "timeout_ms": 1500},
        "implementation": "planned",
    },
]


class RuntimeGovernance:
    """Small in-process runtime standing in for registry, policy and audit."""

    def __init__(self) -> None:
        self.lifecycle: dict[str, str] = {
            service["name"]: "ready" if service["implementation"] != "planned" else "planned"
            for service in SERVICE_CATALOG
        }
        self.audit_events: deque[dict[str, Any]] = deque(maxlen=50)
        self.sequence = 0

    def services_payload(self) -> dict[str, Any]:
        services = copy.deepcopy(SERVICE_CATALOG)
        for service in services:
            service["lifecycle_state"] = self.lifecycle.get(service["name"], "unknown")
        return {
            "services": services,
            "domains": {
                "business": [service["name"] for service in services if service["domain"] == "business"],
                "foundation": [service["name"] for service in services if service["domain"] == "foundation"],
                "atomic": [service["name"] for service in services if service["domain"] == "atomic"],
            },
            "req_ids": ["XSC-003", "FW-S-001", "FW-S-002", "FW-S-003", "FW-S-004", "NV-G-001"],
        }

    def discover(self, service_name: str) -> dict[str, Any] | None:
        for service in SERVICE_CATALOG:
            if service["name"] == service_name:
                return copy.deepcopy(service)
        return None

    def evaluate_permissions(
        self,
        required_permissions: list[str],
        caller_permissions: list[str],
        safety_state: str,
        vehicle_state: str,
        allowed_safety_states: list[str] | None = None,
    ) -> dict[str, Any]:
        required = set(required_permissions)
        granted = set(caller_permissions)
        allowed_safety = allowed_safety_states or ["normal"]
        high_risk = bool(required.intersection({"vehicle.control", "diagnostics.write", "ota.manage"}))
        allowed = (
            required.issubset(granted)
            and safety_state in allowed_safety
            and (not high_risk or vehicle_state == "parked")
        )
        return {
            "decision": "allow" if allowed else "deny",
            "required_permissions": sorted(required),
            "granted_permissions": sorted(granted),
            "safety_state": safety_state,
            "allowed_safety_states": allowed_safety,
            "vehicle_state": vehicle_state,
            "reason": "policy runtime allowed" if allowed else "missing permission or unsafe vehicle/safety state",
            "req_ids": ["FW-U-007", "FW-S-005", "NV-G-005"],
        }

    def precheck(self, request: dict[str, Any]) -> dict[str, Any]:
        service_name = request.get("service", "vehicle-state")
        service = self.discover(service_name)
        if service is None:
            policy = self.evaluate_permissions(
                ["service.read"],
                request.get("caller_permissions", []),
                request.get("safety_state", "normal"),
                request.get("vehicle_state", "parked"),
                ["normal", "degraded", "diagnostic_readonly"],
            )
            policy["decision"] = "deny"
            policy["reason"] = "service is not registered"
            return {
                "service": None,
                "policy": policy,
                "lifecycle_state": "unknown",
                "qos": {"priority": "diagnostic", "timeout_ms": 1000},
            }

        lifecycle_state = self.lifecycle.get(service_name, "unknown")
        policy = self.evaluate_permissions(
            service.get("permissions", ["service.read"]),
            request.get("caller_permissions", ["vehicle.read", "ai.infer", "service.read"]),
            request.get("safety_state", "normal"),
            request.get("vehicle_state", "parked"),
            service.get("allowed_safety_states", ["normal"]),
        )
        if lifecycle_state != "ready":
            policy["decision"] = "deny"
            policy["reason"] = f"service lifecycle is {lifecycle_state}"

        return {
            "service": service,
            "policy": policy,
            "lifecycle_state": lifecycle_state,
            "qos": service.get("qos", {"priority": "diagnostic", "timeout_ms": 1000}),
        }

    def record_audit(self, trace_id: str, record: dict[str, Any]) -> dict[str, Any]:
        self.sequence += 1
        event = {
            "sequence": self.sequence,
            "timestamp_ms": int(time.time() * 1000),
            "trace_id": trace_id,
            **record,
            "req_ids": ["NV-G-007"],
        }
        self.audit_events.appendleft(event)
        return event

    def audit_payload(self, limit: int = 20) -> dict[str, Any]:
        return {
            "events": list(self.audit_events)[:limit],
            "retention": "in-memory-last-50",
            "req_ids": ["NV-G-007"],
        }

    def governance_payload(self) -> dict[str, Any]:
        return {
            "registry": {
                "state": "ok",
                "source": "runtime_governance.SERVICE_CATALOG",
                "registered_services": len(SERVICE_CATALOG),
                "domains": ["business", "foundation", "atomic"],
                "req_ids": ["NV-G-001"],
            },
            "discovery": {
                "state": "prototype",
                "lookup": "service name to lifecycle-checked local handler",
                "req_ids": ["NV-G-002"],
            },
            "schema": {
                "state": "prototype",
                "contract": "central-brain/contracts/central_brain_api.json",
                "req_ids": ["NV-G-003"],
            },
            "qos": {
                "state": "mock-enforced-metadata",
                "priority_classes": ["vehicle-control", "ai-task", "diagnostic"],
                "service_defaults": {
                    service["name"]: service["qos"]
                    for service in SERVICE_CATALOG
                },
                "req_ids": ["NV-G-004"],
            },
            "policy": {
                "state": "active-prototype",
                "entry": "POST /policy/evaluate",
                "also_applied_to": ["POST /soa/invoke", "POST /permission/check"],
                "req_ids": ["NV-G-005", "FW-U-007", "FW-S-005"],
            },
            "lifecycle": {
                "state": "active-prototype",
                "service_states": copy.deepcopy(self.lifecycle),
                "allowed_states": ["starting", "ready", "degraded", "stopped", "planned"],
                "req_ids": ["NV-G-006"],
            },
            "audit": {
                "state": "active-prototype",
                "entry": "GET /audit/recent",
                "recent_count": len(self.audit_events),
                "record_fields": ["trace_id", "service", "method", "policy.decision", "lifecycle_state"],
                "req_ids": ["NV-G-007"],
            },
            "req_ids": GOVERNANCE_REQ_IDS,
        }
