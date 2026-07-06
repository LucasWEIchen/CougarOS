#!/usr/bin/env python3
"""Runtime & Governance prototype for the Central Brain backend."""

from __future__ import annotations

import copy
import json
import time
from collections import deque
from pathlib import Path
from threading import Lock
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
        "qos": {
            "priority": "vehicle-control",
            "timeout_ms": 1000,
            "rate_limit": {"max_requests": 20, "window_s": 1},
        },
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
        "qos": {
            "priority": "ai-task",
            "timeout_ms": 2000,
            "rate_limit": {"max_requests": 2, "window_s": 1},
        },
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
        "qos": {
            "priority": "diagnostic",
            "timeout_ms": 1000,
            "rate_limit": {"max_requests": 20, "window_s": 1},
        },
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
        "qos": {
            "priority": "vehicle-control",
            "timeout_ms": 1500,
            "rate_limit": {"max_requests": 10, "window_s": 1},
        },
        "implementation": "planned",
    },
    {
        "name": "agent-task-planner",
        "version": "0.1.0",
        "domain": "foundation",
        "contract": "POST /agent/plan",
        "semantic_entry": "POST /agent/plan intent=<intent>",
        "layer": "Application/AI SDK -> Native/AIOS Kernel -> Framework/SOA Service Entry",
        "req_ids": ["XSC-001", "APP-004", "NV-F-001", "FW-U-006", "FW-U-007"],
        "permissions": ["vehicle.read"],
        "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
        "qos": {
            "priority": "ai-task",
            "timeout_ms": 1000,
            "rate_limit": {"max_requests": 10, "window_s": 1},
        },
        "implementation": "mock-handler",
    },
    {
        "name": "agent-task-executor",
        "version": "0.1.0",
        "domain": "foundation",
        "contract": "POST /agent/execute",
        "semantic_entry": "POST /agent/execute task=<task_graph>",
        "layer": "Application/AI SDK -> Native/AIOS Kernel -> Tool/Action/SOA boundary",
        "req_ids": ["XSC-001", "APP-004", "NV-F-001", "FW-U-006", "FW-U-007", "NV-G-005"],
        "permissions": ["vehicle.read"],
        "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
        "qos": {
            "priority": "ai-task",
            "timeout_ms": 1000,
            "rate_limit": {"max_requests": 10, "window_s": 1},
        },
        "implementation": "contract-mock",
    },
    {
        "name": "skill-registry",
        "version": "0.1.0",
        "domain": "foundation",
        "contract": "GET /skills and POST /skills/{skill_id}/invoke",
        "semantic_entry": "GET /skills; POST /skills/{skill_id}/invoke",
        "layer": "Application/AI SDK -> Native/AIOS Kernel -> Framework/Tool",
        "req_ids": ["XSC-001", "NV-F-001", "FW-U-006", "FW-U-007", "NV-G-005"],
        "permissions": ["vehicle.read"],
        "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
        "qos": {
            "priority": "ai-task",
            "timeout_ms": 1000,
            "rate_limit": {"max_requests": 20, "window_s": 1},
        },
        "implementation": "contract-mock",
    },
    {
        "name": "memory-query",
        "version": "0.1.0",
        "domain": "foundation",
        "contract": "POST /memory/query",
        "semantic_entry": "POST /memory/query scope=<local_vehicle>",
        "layer": "Application/AI SDK -> Native/AIOS Kernel -> Memory boundary",
        "req_ids": ["XSC-001", "NV-F-001", "FW-U-006", "FW-U-007", "NV-G-005"],
        "permissions": ["vehicle.read"],
        "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
        "qos": {
            "priority": "ai-task",
            "timeout_ms": 1000,
            "rate_limit": {"max_requests": 20, "window_s": 1},
        },
        "implementation": "contract-mock",
    },
]


class RuntimeGovernance:
    """Small in-process runtime standing in for registry, policy and audit."""

    def __init__(self, audit_log_path: str | None = None) -> None:
        self.lifecycle: dict[str, str] = {
            service["name"]: "ready" if service["implementation"] != "planned" else "planned"
            for service in SERVICE_CATALOG
        }
        self.audit_events: deque[dict[str, Any]] = deque(maxlen=50)
        self.sequence = 0
        self.audit_log_path = Path(audit_log_path).expanduser() if audit_log_path else None
        self.audit_persistence_error: str | None = None
        self.qos_windows: dict[str, deque[float]] = {}
        self.qos_lock = Lock()
        self._load_audit_events()

    def _load_audit_events(self) -> None:
        if self.audit_log_path is None or not self.audit_log_path.exists():
            return

        try:
            records: list[dict[str, Any]] = []
            for line in self.audit_log_path.read_text(encoding="utf-8").splitlines():
                if not line.strip():
                    continue
                record = json.loads(line)
                if isinstance(record, dict):
                    records.append(record)

            for record in records[-50:]:
                self.audit_events.appendleft(record)
                self.sequence = max(self.sequence, int(record.get("sequence", 0)))
            self.audit_persistence_error = None
        except (OSError, json.JSONDecodeError, ValueError) as exc:
            self.audit_persistence_error = f"audit restore failed: {exc}"

    def _persist_audit_event(self, event: dict[str, Any]) -> None:
        if self.audit_log_path is None:
            return

        try:
            self.audit_log_path.parent.mkdir(parents=True, exist_ok=True)
            with self.audit_log_path.open("a", encoding="utf-8") as log_file:
                log_file.write(json.dumps(event, ensure_ascii=False, sort_keys=True))
                log_file.write("\n")
            self.audit_persistence_error = None
        except OSError as exc:
            self.audit_persistence_error = f"audit persist failed: {exc}"

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

    def precheck(self, request: dict[str, Any], consume_qos: bool = True) -> dict[str, Any]:
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
                "qos_decision": {
                    "decision": "allow",
                    "reason": "service not registered; QoS window not applied",
                    "req_ids": ["NV-G-004"],
                },
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

        qos = service.get("qos", {"priority": "diagnostic", "timeout_ms": 1000})
        if policy["decision"] != "allow":
            return {
                "service": service,
                "policy": policy,
                "lifecycle_state": lifecycle_state,
                "qos": qos,
                "qos_decision": {
                    "decision": "skipped",
                    "service": service_name,
                    "reason": "Policy or lifecycle denied before QoS window was consumed",
                    "req_ids": ["NV-G-004"],
                },
            }

        qos_decision = self.evaluate_qos(service_name, qos, consume=consume_qos)
        return {
            "service": service,
            "policy": policy,
            "lifecycle_state": lifecycle_state,
            "qos": qos,
            "qos_decision": qos_decision,
        }

    def evaluate_qos(self, service_name: str, qos: dict[str, Any], consume: bool = True) -> dict[str, Any]:
        rate_limit = qos.get("rate_limit") or {}
        max_requests = int(rate_limit.get("max_requests", 0) or 0)
        window_s = float(rate_limit.get("window_s", 0) or 0)
        if max_requests <= 0 or window_s <= 0:
            return {
                "decision": "allow",
                "service": service_name,
                "reason": "no rate limit configured",
                "req_ids": ["NV-G-004"],
            }

        now = time.monotonic()
        with self.qos_lock:
            window = self.qos_windows.setdefault(service_name, deque())
            while window and now - window[0] >= window_s:
                window.popleft()
            current_count = len(window)
            allowed = current_count < max_requests
            if allowed and consume:
                window.append(now)

        return {
            "decision": "allow" if allowed else "deny",
            "service": service_name,
            "priority": qos.get("priority", "diagnostic"),
            "window_s": window_s,
            "max_requests": max_requests,
            "current_count": current_count + 1 if allowed else current_count,
            "retry_after_ms": 0 if allowed else int(window_s * 1000),
            "consumed": bool(allowed and consume),
            "reason": "QoS window allowed" if allowed else "QoS rate limit exceeded",
            "req_ids": ["NV-G-004"],
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
        self._persist_audit_event(event)
        return event

    def audit_payload(self, limit: int = 20) -> dict[str, Any]:
        return {
            "events": list(self.audit_events)[:limit],
            "retention": "jsonl-last-50" if self.audit_log_path else "in-memory-last-50",
            "persistence": {
                "state": "enabled" if self.audit_log_path else "disabled",
                "path": str(self.audit_log_path) if self.audit_log_path else None,
                "last_error": self.audit_persistence_error,
            },
            "req_ids": ["XSC-005", "NV-G-007", "DEL-002"],
        }

    def backend_contract_payload(self) -> dict[str, Any]:
        return {
            "name": "central-brain-shared-governance-backend",
            "state": "target-contract-with-linux-socket-sample",
            "current_sample": {
                "linux_socket": "central-brain/bindings/linux/ipc/central_brain_governance_daemon.py",
                "client_helper": "central-brain/bindings/linux/ipc/central_brain_governance_client.py",
                "android_path": "ICentralBrainGateway.getGovernanceBackendContractJson -> /governance/backend-contract",
                "linux_cli": "central_brain_cli.py governance-backend-contract",
            },
            "required_operations": [
                {
                    "operation": "governance.precheck",
                    "purpose": "service discovery, Policy/Safety State, Lifecycle, and QoS decision before SOA dispatch",
                    "dispatch": {"service_invoked": False, "driver_hal": "not-dispatched", "virtualization": "not-developed"},
                    "req_ids": ["NV-G-002", "NV-G-004", "NV-G-005", "NV-G-006"],
                },
                {
                    "operation": "governance.runtime.get",
                    "purpose": "registry, discovery, schema, QoS, policy, lifecycle, and audit diagnostics",
                    "dispatch": {"service_invoked": False, "driver_hal": "not-dispatched", "virtualization": "not-developed"},
                    "req_ids": ["NV-G-001", "NV-G-002", "NV-G-003", "NV-G-004", "NV-G-005", "NV-G-006"],
                },
                {
                    "operation": "audit.recent.get",
                    "purpose": "recent governance audit visibility for Android/Linux integration tests",
                    "dispatch": {"service_invoked": False, "driver_hal": "not-dispatched", "virtualization": "not-developed"},
                    "req_ids": ["NV-G-007", "DEL-001", "DEL-002"],
                },
            ],
            "binding_contract": {
                "android_binder": {
                    "current": "debug APK Binder service proxies the REST prototype gateway",
                    "target": "system/privileged Binder service calls the shared governance backend before SOA dispatch",
                    "req_ids": ["DEL-001", "NV-P-002", "XSC-005", "XSC-006"],
                },
                "linux_ipc": {
                    "current": "Unix socket IPC active sample calls the shared governance socket for soa.service.invoke",
                    "target": "same operation envelope backed by the production governance backend",
                    "req_ids": ["DEL-002", "NV-P-002", "XSC-005", "XSC-006"],
                },
                "linux_grpc_rpc": {
                    "current": "JSON TCP wrapper mirrors proto RPC names and reuses the Linux governance socket client",
                    "target": "true gRPC server keeps the same precheck/runtime/audit operation names",
                    "req_ids": ["DEL-002", "NV-P-003", "XSC-005", "XSC-006"],
                },
            },
            "replacement_rules": [
                "Bindings must depend on the governance operation envelope, not duplicate Policy/QoS logic per transport.",
                "SOA service dispatch is allowed only after governance.precheck returns allow.",
                "Diagnostic runtime/audit operations must stay read-only and cannot consume QoS or invoke services.",
                "Binder caller identity, Linux service identity, and gRPC peer identity are Policy inputs, not Policy replacements.",
            ],
            "non_goals": [
                "No production multi-process governance backend is implemented in this increment.",
                "No Android framework patch, SELinux policy, true gRPC runtime, Driver/HAL, Safety Runtime, vehicle bus, or virtualization code is added.",
            ],
            "req_ids": ["XSC-005", "XSC-006", "NV-G-001", "NV-G-002", "NV-G-003", "NV-G-004", "NV-G-005", "NV-G-006", "NV-G-007", "NV-P-002", "NV-P-003", "DEL-001", "DEL-002"],
        }

    def migration_check_payload(self) -> dict[str, Any]:
        return {
            "name": "central-brain-governance-backend-migration-check",
            "state": "readiness-check-contract",
            "production_backend_ready": False,
            "current_sample_baseline": {
                "gateway_contract": "GET /governance/backend-contract",
                "linux_shared_socket": "governance.precheck + governance.runtime.get + audit.recent.get",
                "linux_client_helper": "central_brain_governance_client.py",
                "android_visibility": "Binder/AIDL getGovernanceMigrationCheckJson",
                "linux_visibility": "CLI/IPC/gRPC governance-migration-check",
            },
            "required_invariants": [
                {
                    "id": "GOV-MIG-001",
                    "rule": "All SOA service dispatch remains gated by governance.precheck.",
                    "status": "sample-enforced-for-linux-ipc-grpc",
                    "req_ids": ["XSC-005", "NV-G-002", "NV-G-004", "NV-G-005", "NV-G-006"],
                },
                {
                    "id": "GOV-MIG-002",
                    "rule": "Binding transports depend on the shared governance operation envelope instead of duplicating Policy/QoS logic.",
                    "status": "linux-helper-enforced-android-target-documented",
                    "req_ids": ["XSC-006", "NV-P-002", "NV-P-003"],
                },
                {
                    "id": "GOV-MIG-003",
                    "rule": "Runtime and audit diagnostic operations remain read-only and never dispatch services, Driver/HAL, vehicle bus, or virtualization.",
                    "status": "sample-enforced",
                    "req_ids": ["NV-G-001", "NV-G-007", "KH-003", "KH-006", "HV-001", "HV-002", "HV-003"],
                },
            ],
            "binding_migration_matrix": [
                {
                    "binding": "android-binder-aidl",
                    "current": "debug APK Binder service proxies REST prototype gateway",
                    "target_replacement": "system/privileged Binder service calls production governance backend before SOA dispatch",
                    "readiness": "contract-visible-not-production-ready",
                    "open_decisions": ["target AAOS service owner", "signature permission", "SELinux domain", "native gateway process shape"],
                    "req_ids": ["DEL-001", "DEL-003", "DEL-004", "NV-P-002", "XSC-005", "XSC-006"],
                },
                {
                    "binding": "linux-ipc",
                    "current": "Unix socket IPC calls shared governance socket through the reusable client helper",
                    "target_replacement": "same operation envelope backed by production governance service",
                    "readiness": "sample-ready-for-backend-swap",
                    "open_decisions": ["target distro package format", "service account policy", "audit export backend"],
                    "req_ids": ["DEL-002", "DEL-003", "DEL-004", "NV-P-002", "XSC-005", "XSC-006"],
                },
                {
                    "binding": "linux-grpc-rpc",
                    "current": "dependency-free JSON TCP sample mirrors proto RPC names and uses the shared governance client helper",
                    "target_replacement": "true gRPC server keeps the same governance precheck/runtime/audit operation names",
                    "readiness": "blocked-on-grpc-runtime-tooling",
                    "open_decisions": ["grpcio or C++ gRPC availability", "service credentials", "peer identity mapping"],
                    "req_ids": ["DEL-002", "NV-P-003", "XSC-005", "XSC-006"],
                },
            ],
            "validation_commands": [
                "bash tools/check_central_brain_binding_artifacts.sh",
                "bash tools/smoke_central_brain_semantic_gateway.sh",
                "bash tools/smoke_central_brain_linux_ipc.sh",
                "bash tools/smoke_central_brain_linux_grpc.sh",
                "bash tools/check_central_brain_delivery_docs.sh",
            ],
            "non_goals": [
                "No production multi-process governance backend is implemented by this readiness check.",
                "No Android framework patch, SELinux policy, true gRPC runtime, package manager integration, Driver/HAL, Safety Runtime, vehicle bus, or virtualization code is added.",
            ],
            "req_ids": ["XSC-005", "XSC-006", "NV-G-001", "NV-G-002", "NV-G-004", "NV-G-005", "NV-G-006", "NV-G-007", "NV-P-002", "NV-P-003", "DEL-001", "DEL-002", "DEL-003", "DEL-004"],
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
                "state": "active-prototype-fixed-window",
                "priority_classes": ["vehicle-control", "ai-task", "diagnostic"],
                "service_defaults": {
                    service["name"]: service["qos"]
                    for service in SERVICE_CATALOG
                },
                "enforced_on": [
                    "POST /soa/invoke",
                    "Linux IPC soa.service.invoke pre-forwarding precheck",
                    "Linux governance daemon shared precheck sample",
                ],
                "diagnostic_precheck": "POST /governance/precheck defaults to consume_qos=false",
                "limiter": "in-process fixed window per service",
                "req_ids": ["NV-G-004"],
            },
            "policy": {
                "state": "active-prototype",
                "entry": "POST /policy/evaluate",
                "also_applied_to": [
                    "POST /soa/invoke",
                    "POST /permission/check",
                    "POST /agent/execute",
                    "POST /skills/{skill_id}/invoke",
                    "POST /memory/query",
                ],
                "req_ids": ["NV-G-005", "FW-U-007", "FW-S-005"],
            },
            "lifecycle": {
                "state": "active-prototype",
                "service_states": copy.deepcopy(self.lifecycle),
                "allowed_states": ["starting", "ready", "degraded", "stopped", "planned"],
                "req_ids": ["NV-G-006"],
            },
            "audit": {
                "state": "active-prototype-jsonl" if self.audit_log_path else "active-prototype",
                "entry": "GET /audit/recent",
                "recent_count": len(self.audit_events),
                "persistence": {
                    "state": "enabled" if self.audit_log_path else "disabled",
                    "path": str(self.audit_log_path) if self.audit_log_path else None,
                    "last_error": self.audit_persistence_error,
                },
                "record_fields": ["trace_id", "service", "method", "policy.decision", "lifecycle_state"],
                "req_ids": ["XSC-005", "NV-G-007", "DEL-002"],
            },
            "req_ids": GOVERNANCE_REQ_IDS,
        }
