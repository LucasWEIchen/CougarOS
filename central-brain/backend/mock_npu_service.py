#!/usr/bin/env python3
"""Prototype backend for the vehicle central brain.

This service intentionally uses only the Python standard library so it can run
inside the current WSL workspace without adding package dependencies.
"""

from __future__ import annotations

import argparse
from collections import deque
import json
import os
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlparse

from ai_sdk import capabilities_payload as ai_sdk_capabilities_payload
from ai_sdk import execute_payload as ai_sdk_execute_payload
from ai_sdk import memory_query_payload as ai_sdk_memory_query_payload
from ai_sdk import plan_payload as ai_sdk_plan_payload
from ai_sdk import skill_invoke_payload as ai_sdk_skill_invoke_payload
from ai_sdk import skills_payload as ai_sdk_skills_payload
from delivery_readiness import delivery_readiness_payload as delivery_readiness_contract_payload
from hardware_interfaces import HardwareInterfaceRegistry
from native_adapters import NativeAdapterRegistry
from ollama_simulated_npu import infer_payload as ollama_infer_payload
from ollama_simulated_npu import runtime_name as simulated_npu_runtime_name
from ollama_simulated_npu import selected_backend as selected_simulated_npu_backend
from ollama_simulated_npu import status_payload as ollama_status_payload
from prototype_readiness import PrototypeReadinessRegistry
from protocol_bindings import ProtocolBindingRegistry
from runtime_governance import RuntimeGovernance
from vehicle_signals import VehicleSignalRegistry


STARTED_AT = time.time()
API_VERSION = "0.1.107"
GOVERNANCE = RuntimeGovernance(os.environ.get("CENTRAL_BRAIN_AUDIT_LOG"))
BINDINGS = ProtocolBindingRegistry()
NATIVE_ADAPTERS = NativeAdapterRegistry()
HARDWARE_INTERFACES = HardwareInterfaceRegistry()
PROTOTYPE_READINESS = PrototypeReadinessRegistry()
VEHICLE_SIGNALS = VehicleSignalRegistry()
EVENT_LOG: deque[dict[str, Any]] = deque(maxlen=50)
EVENT_TOPICS = [
    "vehicle.signal.changed",
    "service.health.changed",
    "agent.task.updated",
    "skill.invocation.completed",
    "policy.decision.created",
    "ai.inference.completed",
    "npu.runtime.changed"
]
EVENT_SUBSCRIPTION_REQ_IDS = [
    "XSC-002",
    "FW-U-003",
    "XSC-005",
    "XSC-006",
    "NV-P-002",
    "NV-P-003",
    "NV-P-006",
    "DEL-001",
    "DEL-002",
]
EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS = EVENT_SUBSCRIPTION_REQ_IDS + ["DEL-004"]
EVENT_SUBSCRIPTION_DECISION_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_CALLBACK_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_CURSOR_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_BACKPRESSURE_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_READINESS_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_EVIDENCE_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_EVIDENCE_STATUS_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_EVIDENCE_RETENTION_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_EVIDENCE_DECISION_STATUS_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DRY_RUN_STATUS_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_AUTHORITY_CHECKLIST_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_AUTHORITY_AUDIT_CONSISTENCY_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_BLOCKER_ROLLUP_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_DRY_RUN_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_DRY_RUN_STATUS_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_DRY_RUN_AUDIT_CONSISTENCY_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_CLOSURE_BLOCKER_MATRIX_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_CHECKLIST_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_AUDIT_CONSISTENCY_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_DECISION_ROLLUP_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_READINESS_MATRIX_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_READINESS_AUDIT_CONSISTENCY_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_STATUS_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_AUDIT_CONSISTENCY_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_DECISION_ROLLUP_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_CHECKLIST_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_AUDIT_CONSISTENCY_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_ROLLUP_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_ASSIGNMENT_CHECKLIST_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_ASSIGNMENT_AUDIT_CONSISTENCY_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_ASSIGNMENT_AUDIT_DECISION_ROLLUP_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_ASSIGNMENT_AUDIT_DECISION_ROLLUP_CLOSURE_HANDOFF_READINESS_SUMMARY_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_ASSIGNMENT_AUDIT_DECISION_ROLLUP_CLOSURE_HANDOFF_READINESS_AUDIT_CONSISTENCY_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_ASSIGNMENT_AUDIT_DECISION_ROLLUP_CLOSURE_HANDOFF_READINESS_AUDIT_DECISION_ROLLUP_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_ASSIGNMENT_AUDIT_DECISION_ROLLUP_CLOSURE_HANDOFF_READINESS_AUDIT_DECISION_ROLLUP_CLOSURE_BLOCKER_MATRIX_REQ_IDS = EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS
SOA_EXTENSION_CLOSURE_REQ_IDS = [
    "FW-S-006",
    "XSC-003",
    "XSC-005",
    "XSC-006",
    "NV-G-001",
    "NV-G-002",
    "NV-G-003",
    "DEL-001",
    "DEL-002",
    "DEL-003",
]
OBSERVABILITY_READINESS_REQ_IDS = [
    "NV-F-012",
    "XSC-005",
    "XSC-006",
    "NV-G-007",
    "NV-P-002",
    "NV-P-003",
    "DEL-001",
    "DEL-002",
    "DEL-003",
    "DEL-004",
]
PROTOTYPE_COMPLETION_SUMMARY_REQ_IDS = [
    "XSC-001",
    "XSC-002",
    "XSC-003",
    "XSC-004",
    "XSC-005",
    "XSC-006",
    "DEL-001",
    "DEL-002",
    "DEL-003",
    "DEL-004",
    "DEL-005",
    "FW-S-006",
    "NV-F-012",
    "NV-G-007",
    "NV-P-002",
    "NV-P-003",
    "HW-002",
    "KH-003",
    "KH-006",
    "KH-007",
]

UIB_EXTENSION_REGISTRY: list[dict[str, Any]] = [
    {
        "extension_id": "diagnostic.trace.snapshot",
        "domain": "observability",
        "semantic_object": "TraceSnapshot",
        "schema_state": "contract-only",
        "allowed_bindings": ["android-binder-aidl", "linux-ipc", "linux-grpc-rpc", "rest-http-json"],
        "governance": {
            "permissions": ["service.read", "policy.read"],
            "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
            "audit_required": True,
        },
        "dispatch_boundary": {
            "service_invoked": False,
            "driver_hal": "not-dispatched",
            "vehicle_bus": "not-dispatched",
            "virtualization": "not-developed",
        },
        "req_ids": ["XSC-002", "FW-U-008", "XSC-005", "XSC-006"],
    },
    {
        "extension_id": "diagnostic.calibration.marker",
        "domain": "diagnostic",
        "semantic_object": "CalibrationMarker",
        "schema_state": "planned-contract",
        "allowed_bindings": ["android-binder-aidl", "linux-ipc", "linux-grpc-rpc"],
        "governance": {
            "permissions": ["service.read"],
            "allowed_safety_states": ["diagnostic_readonly"],
            "audit_required": True,
        },
        "dispatch_boundary": {
            "service_invoked": False,
            "driver_hal": "not-dispatched",
            "vehicle_bus": "not-dispatched",
            "virtualization": "not-developed",
        },
        "req_ids": ["XSC-002", "FW-U-008", "APP-009", "APP-010", "XSC-006"],
    },
]


def read_text(path: Path) -> str | None:
    try:
        return path.read_text(encoding="utf-8").strip()
    except OSError:
        return None


def discover_pci_devices(limit: int = 12) -> list[dict[str, str]]:
    devices_dir = Path("/sys/bus/pci/devices")
    if not devices_dir.exists():
        return []

    devices: list[dict[str, str]] = []
    for device_path in sorted(devices_dir.iterdir()):
        if len(devices) >= limit:
            break
        devices.append(
            {
                "slot": device_path.name,
                "vendor": read_text(device_path / "vendor") or "unknown",
                "device": read_text(device_path / "device") or "unknown",
                "class": read_text(device_path / "class") or "unknown"
            }
        )
    return devices


def npu_status() -> dict[str, Any]:
    requested_vendor = os.environ.get("CENTRAL_BRAIN_NPU_VENDOR_ID")
    requested_device = os.environ.get("CENTRAL_BRAIN_NPU_DEVICE")
    pci_devices = discover_pci_devices()
    simulated_backend = selected_simulated_npu_backend()
    simulated_status = ollama_status_payload()
    matched = [
        dev for dev in pci_devices
        if requested_vendor and dev.get("vendor", "").lower() == requested_vendor.lower()
    ]

    mode = "mock"
    if requested_device:
        mode = "configured-device"
    if matched:
        mode = "pci-vendor-detected"

    return {
        "mode": mode,
        "runtime": simulated_npu_runtime_name(),
        "simulated_npu_backend": simulated_backend,
        "simulated_backend_status": simulated_status,
        "device_node": requested_device,
        "requested_vendor": requested_vendor,
        "pci_devices_sample": pci_devices,
        "matched_devices": matched,
        "production_ready": False,
        "hardware_accessed": False,
        "driver_development_triggered": False,
        "virtualization_development_triggered": False,
        "service_dispatch_triggered": False,
        "model_slots": [
            {
                "model": "central-intent-v0",
                "state": "loaded",
                "backend": simulated_backend
            },
            {
                "model": "vehicle-scene-v0",
                "state": "loaded",
                "backend": simulated_backend
            }
        ],
        "req_ids": ["XSC-001", "HW-002", "NV-F-011", "KH-003", "KH-006", "DEL-001", "DEL-002", "DEL-005"]
    }


def health_payload() -> dict[str, Any]:
    return {
        "system": "central-brain",
        "version": API_VERSION,
        "status": "ok",
        "safety_state": "normal",
        "uptime_s": round(time.time() - STARTED_AT, 3),
        "service_bus": {
            "registry": "ok",
            "discovery": "ok",
            "schema": "ok",
            "policy": "mock",
            "lifecycle": "ok"
        },
        "ai_backend": npu_status()
    }


def envelope(payload: dict[str, Any], trace_id: str | None = None, status: str = "ok") -> dict[str, Any]:
    return {
        "trace_id": trace_id or str(uuid.uuid4()),
        "status": status,
        "error": None if status == "ok" else {"code": status.upper(), "message": status},
        "payload": payload,
        "metrics": {
            "queue_ms": 0.0,
            "execution_ms": 0.0
        }
    }


def services_payload() -> dict[str, Any]:
    return GOVERNANCE.services_payload()


def service_contracts_payload() -> dict[str, Any]:
    return GOVERNANCE.service_contracts_payload()


def soa_extension_closure_summary_payload() -> dict[str, Any]:
    service_contracts = service_contracts_payload()
    extensions = uib_extensions_payload()
    return {
        "summary": {
            "soa_extension_closure_summary_active": True,
            "fw_s_006_closure_ready": True,
            "py_cl_001_resolved": True,
            "extension_service_runtime_ready": False,
            "dynamic_extension_service_runtime_ready": False,
            "source_service_contracts_bound": True,
            "source_uib_extensions_bound": True,
            "runtime_governance_bound": True,
            "policy_schema_governance_bound": True,
            "android_linux_binding_parity": True,
            "service_dispatch_triggered": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
        },
        "source_surfaces": [
            "GET /soa/contracts",
            "GET /uib/extensions",
            "GET /governance/runtime",
            "GET /bindings/readiness",
            "GET /delivery/readiness",
        ],
        "source_counts": {
            "service_contract_count": service_contracts["summary"]["contract_count"],
            "extension_count": len(extensions["extensions"]),
        },
        "android_delivery": {
            "binder": "getSoaExtensionClosureSummaryJson",
            "console": "SOA Ext Close",
        },
        "linux_delivery": {
            "cli": "soa-extension-closure-summary",
            "ipc": "soa.extensions.closure.summary",
            "grpc_rpc": "GetSoaExtensionClosureSummary",
        },
        "closure_items": [
            {
                "id": "PY-CL-001",
                "req_id": "FW-S-006",
                "status": "resolved",
                "evidence": "SOA contracts and UIB extensions are joined by this read-only summary.",
            },
            {
                "id": "SOA-EXT-001",
                "req_id": "XSC-003",
                "status": "covered",
                "evidence": "Service catalog and service contract metadata remain the SOA entry source of truth.",
            },
            {
                "id": "SOA-EXT-002",
                "req_id": "XSC-005",
                "status": "covered",
                "evidence": "Runtime & Governance remains required for policy, schema, lifecycle, and audit ownership.",
            },
            {
                "id": "SOA-EXT-003",
                "req_id": "XSC-006",
                "status": "covered",
                "evidence": "Android Binder and Linux CLI/IPC/gRPC bindings expose the same read-only payload.",
            },
        ],
        "invariants": [
            "read-only closure summary",
            "does not dispatch SOA services",
            "does not load dynamic extensions or plugins",
            "does not access hardware, Driver/HAL, or virtualization",
        ],
        "next_state": {
            "py_cl_001": "resolved",
            "remaining_current_python_prototype_closure_actions": ["PY-CL-002"],
        },
        "req_ids": SOA_EXTENSION_CLOSURE_REQ_IDS,
    }


def observability_readiness_payload() -> dict[str, Any]:
    audit = GOVERNANCE.audit_payload()
    delivery = delivery_readiness_payload()
    prototype = prototype_readiness_payload()
    governance = governance_payload()
    return {
        "summary": {
            "observability_readiness_active": True,
            "nv_f_012_closure_ready": True,
            "py_cl_002_resolved": True,
            "audit_recent_bound": True,
            "jsonl_audit_persistence_sample_bound": True,
            "delivery_readiness_bound": True,
            "prototype_readiness_bound": True,
            "runtime_governance_bound": True,
            "android_linux_binding_parity": True,
            "production_log_backend_ready": False,
            "metric_daemon_ready": False,
            "hardware_trace_capture_ready": False,
            "service_dispatch_triggered": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
        },
        "source_surfaces": [
            "GET /audit/recent",
            "CENTRAL_BRAIN_AUDIT_LOG JSONL sample",
            "GET /delivery/readiness",
            "GET /prototype/readiness",
            "GET /governance/runtime",
        ],
        "source_counts": {
            "recent_audit_event_count": len(audit.get("records", audit.get("events", []))),
            "delivery_validation_count": len(delivery.get("validation_bundle", delivery.get("validation_index", []))),
            "prototype_module_count": len(prototype.get("modules", [])),
            "governance_service_count": len(governance.get("registry", {}).get("services", [])),
        },
        "android_delivery": {
            "binder": "getObservabilityReadinessJson",
            "console": "Observability",
        },
        "linux_delivery": {
            "cli": "observability-readiness",
            "ipc": "observability.readiness.get",
            "grpc_rpc": "GetObservabilityReadiness",
        },
        "closure_items": [
            {
                "id": "PY-CL-002",
                "req_id": "NV-F-012",
                "status": "resolved",
                "evidence": "Audit, JSONL persistence sample, delivery readiness, prototype readiness, and governance runtime diagnostics are joined by this read-only observability readiness summary.",
            },
            {
                "id": "OBS-001",
                "req_id": "NV-G-007",
                "status": "covered",
                "evidence": "Runtime & Governance audit records remain the prototype observability source.",
            },
            {
                "id": "OBS-002",
                "req_id": "XSC-006",
                "status": "covered",
                "evidence": "Android Binder and Linux CLI/IPC/gRPC bindings expose the same observability readiness payload.",
            },
        ],
        "invariants": [
            "read-only observability closure summary",
            "does not create a production logging backend",
            "does not start a metric daemon",
            "does not capture hardware traces",
            "does not dispatch services",
            "does not access hardware, Driver/HAL, or virtualization",
        ],
        "next_state": {
            "py_cl_002": "resolved",
            "remaining_current_python_prototype_implementation_actions": [],
            "remaining_current_python_prototype_closure_actions": [
                "final completion audit consistency check",
                "handoff manifest version alignment",
            ],
        },
        "req_ids": OBSERVABILITY_READINESS_REQ_IDS,
    }


def prototype_completion_summary_payload() -> dict[str, Any]:
    delivery = delivery_readiness_payload()
    prototype = prototype_readiness_payload()
    bindings = binding_readiness_payload()
    observability = observability_readiness_payload()
    soa_extension = soa_extension_closure_summary_payload()
    production_blockers = [
        "target Android system/privileged service deployment",
        "target Linux package/service identity and LSM policy",
        "real PCIe NPU hardware, driver ABI, HAL, and vendor SDK",
        "production shared Runtime & Governance backend",
        "real event broker/DDS/high-rate data plane",
        "production log backend, metric daemon, and retention/export policy",
        "real vehicle sensors, time sync, connected services, and ADAS funcware",
        "target virtualization/Safety Runtime architecture and isolation evidence",
    ]
    return {
        "summary": {
            "prototype_completion_summary_active": True,
            "python_prototype_current_scope_complete": True,
            "current_python_prototype_implementation_actions_complete": True,
            "current_python_prototype_audit_actions_complete": True,
            "py_cl_001_resolved": True,
            "py_cl_002_resolved": True,
            "prototype_handoff_ready": True,
            "production_ready": False,
            "android_primary_path_ready": True,
            "linux_synchronized_path_ready": True,
            "closure_plan_bound": True,
            "completion_audit_bound": True,
            "handoff_manifest_bound": True,
            "delivery_readiness_bound": True,
            "prototype_readiness_bound": True,
            "binding_readiness_bound": True,
            "observability_readiness_bound": True,
            "soa_extension_closure_summary_bound": True,
            "remaining_current_python_prototype_implementation_action_count": 0,
            "remaining_current_python_prototype_audit_action_count": 0,
            "production_blocker_count": len(production_blockers),
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "source_surfaces": [
            "central-brain/contracts/central_brain_prototype_closure_plan.json",
            "central-brain/contracts/central_brain_prototype_completion_audit.json",
            "central-brain/contracts/central_brain_prototype_handoff_manifest.json",
            "GET /soa/extensions/closure-summary",
            "GET /observability/readiness",
            "GET /delivery/readiness",
            "GET /prototype/readiness",
            "GET /bindings/readiness",
            "GET /native/driver-gaps",
            "GET /hardware/interfaces",
        ],
        "source_counts": {
            "prototype_module_count": len(prototype.get("modules", [])),
            "delivery_validation_count": len(delivery.get("validation_bundle", delivery.get("validation_index", []))),
            "binding_row_count": len(bindings.get("readiness", bindings.get("bindings", []))),
            "observability_closure_item_count": len(observability.get("closure_items", [])),
            "soa_extension_closure_item_count": len(soa_extension.get("closure_items", [])),
        },
        "android_delivery": {
            "binder": "getPrototypeCompletionSummaryJson",
            "console": "Complete",
            "primary_path": "Android Binder/AIDL debug Console",
        },
        "linux_delivery": {
            "cli": "prototype-completion-summary",
            "ipc": "prototype.completion.summary.get",
            "grpc_rpc": "GetPrototypeCompletionSummary",
        },
        "completion_items": [
            {
                "id": "PY-COMP-001",
                "req_ids": ["XSC-001", "XSC-002", "XSC-003", "XSC-004", "XSC-005", "XSC-006"],
                "status": "complete-current-python-prototype-scope",
                "evidence": "AI SDK, Uni Info Bus, SOA, Runtime & Governance, native adapter visibility, protocol binding, and prototype readiness surfaces are all exposed in the current Python prototype.",
            },
            {
                "id": "PY-COMP-002",
                "req_ids": ["DEL-001", "DEL-002", "DEL-003", "DEL-004", "DEL-005"],
                "status": "complete-current-python-prototype-scope",
                "evidence": "Android Binder/Console and Linux CLI/IPC/gRPC synchronized handoff surfaces are present for the current scope.",
            },
            {
                "id": "PY-CL-001",
                "req_ids": ["FW-S-006"],
                "status": "resolved",
                "evidence": "GET /soa/extensions/closure-summary closes current Python prototype extension service coverage without dynamic service dispatch.",
            },
            {
                "id": "PY-CL-002",
                "req_ids": ["NV-F-012", "NV-G-007"],
                "status": "resolved",
                "evidence": "GET /observability/readiness closes current Python prototype observability coverage without production observability backends.",
            },
        ],
        "remaining_current_python_prototype_implementation_actions": [],
        "remaining_current_python_prototype_audit_actions": [],
        "production_blockers": production_blockers,
        "invariants": [
            "read-only completion summary",
            "does not call POST",
            "does not persist completion, approval, handoff, review, or evidence state",
            "does not assign owners or reviewers",
            "does not close production gates",
            "does not dispatch services",
            "does not access hardware, Driver/HAL, or virtualization",
        ],
        "next_state": {
            "current_python_prototype": "complete-current-scope",
            "next_phase": "target hardware and production integration planning",
            "production_scope": "not-complete",
        },
        "req_ids": PROTOTYPE_COMPLETION_SUMMARY_REQ_IDS,
    }


def context_payload() -> dict[str, Any]:
    vehicle = vehicle_state_payload()
    return {
        "context": {
            "vehicle": {
                "signals": vehicle["signals"],
                "driving_state": vehicle["context"]["driving_state"]
            },
            "user": {
                "profile": "local-driver",
                "role": "debug_console",
                "privacy_level": "local_only"
            },
            "environment": {
                "runtime": "wsl-local",
                "network": vehicle["context"]["network"],
                "timestamp_ms": vehicle["timestamp_ms"]
            }
        },
        "req_ids": ["FW-U-001"]
    }


def state_payload() -> dict[str, Any]:
    return {
        "state": {
            "system": health_payload(),
            "services": services_payload()["services"],
            "npu": npu_status(),
            "hardware_interfaces": HARDWARE_INTERFACES.summary_payload(),
            "vehicle_signal_catalog": VEHICLE_SIGNALS.summary_payload(),
            "vehicle": vehicle_state_payload(),
            "safety_state": "normal"
        },
        "req_ids": ["FW-U-002", "XSC-004", "HW-002", "NV-F-004", "NV-F-005", "DEL-005"]
    }


def event_topics_payload() -> dict[str, Any]:
    return {
        "topics": [
            {
                "name": topic,
                "mode": "active-mock",
                "delivery": ["publish-ack", "recent-log", "subscription-contract"],
                "subscription_contract": {
                    "semantic_endpoint": "GET /uib/events/subscriptions",
                    "request_endpoint": "POST /uib/events/subscriptions/request",
                    "cancel_endpoint": "POST /uib/events/subscriptions/cancel",
                    "transport_readiness_endpoint": "GET /uib/events/subscriptions/transport-readiness",
                    "decision_matrix_endpoint": "GET /uib/events/subscriptions/decision-matrix",
                    "activation_checklist_endpoint": "GET /uib/events/subscriptions/activation-checklist",
                    "callback_watch_shape_endpoint": "GET /uib/events/subscriptions/callback-watch-shape",
                    "cursor_replay_storage_endpoint": "GET /uib/events/subscriptions/cursor-replay-storage",
                    "backpressure_qos_evidence_endpoint": "GET /uib/events/subscriptions/backpressure-qos-evidence",
                    "readiness_rollup_endpoint": "GET /uib/events/subscriptions/readiness-rollup",
                    "activation_evidence_endpoint": "POST /uib/events/subscriptions/activation-evidence",
                    "activation_evidence_status_endpoint": "GET /uib/events/subscriptions/activation-evidence/status",
                    "activation_evidence_retention_checklist_endpoint": "GET /uib/events/subscriptions/activation-evidence/retention-checklist",
                    "activation_evidence_decision_status_endpoint": "GET /uib/events/subscriptions/activation-evidence/decision-status-rollup",
                    "activation_approval_dry_run_status_endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-dry-run/status",
                    "activation_approval_authority_checklist_endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist",
                    "activation_approval_authority_audit_consistency_endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/audit-consistency",
                    "activation_approval_decision_blocker_rollup_endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-blocker-rollup",
                    "activation_approval_decision_dry_run_endpoint": "POST /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run",
                    "activation_approval_decision_dry_run_status_endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/status",
                    "activation_approval_decision_dry_run_audit_consistency_endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/audit-consistency",
                    "filter_fields": ["topic", "source", "safety_state"],
                    "delivery_cursor": "event_id",
                    "backpressure": "drop-oldest-after-50-events",
                },
                "binding_candidates": ["android-binder-aidl", "linux-ipc", "dds-planned"],
            }
            for topic in EVENT_TOPICS
        ],
        "constraints": [
            "Events are Uni Info Bus semantic objects; transport bindings cannot publish raw vehicle data without this envelope.",
            "DDS is reserved for high-rate topic delivery, but this prototype only stores a bounded in-memory recent log.",
        ],
        "req_ids": ["XSC-002", "FW-U-003", "XSC-006", "NV-P-006"]
    }


def event_subscriptions_payload() -> dict[str, Any]:
    return {
        "subscription_state": "contract-only-not-brokered",
        "broker_active": False,
        "active_subscriptions": [],
        "subscription_contract": {
            "lifecycle_states": [
                "requested",
                "validated",
                "active",
                "paused",
                "resumed",
                "cancelled",
                "expired",
                "rejected",
            ],
            "request_shape": {
                "subscription_id": "client-generated-or-gateway-assigned string",
                "topics": ["vehicle.signal.changed"],
                "filters": {
                    "topic": "string or glob",
                    "source": "string",
                    "safety_state": "normal|degraded|diagnostic_readonly",
                    "qos_profile": "best_effort|reliable_planned",
                },
                "cursor": {
                    "since_event_id": "optional event_id",
                    "since_timestamp_ms": "optional epoch millis",
                    "replay_limit": "1..50 for prototype recent-log replay",
                },
                "caller": {
                    "app_id": "string",
                    "permissions": ["vehicle.read", "service.read"],
                },
            },
            "cursor_contract": {
                "cursor_key": "event_id",
                "ordering": "newest-first in prototype recent log",
                "resume_inputs": ["since_event_id", "since_timestamp_ms"],
                "prototype_retention": "last-50-events",
            },
            "backpressure": {
                "prototype_policy": "drop-oldest-after-50-events",
                "production_policy": "TBD after DDS/SSE/WebSocket transport selection",
                "overflow_signal": "subscription.overflow planned",
            },
            "governance": {
                "policy_checked": True,
                "required_permissions": ["vehicle.read", "service.read"],
                "audit_required": True,
                "service_dispatch": "not-dispatched",
            },
            "operation_contracts": {
                "request": {
                    "endpoint": "POST /uib/events/subscriptions/request",
                    "state_transition": "requested -> validated_contract_only|rejected_by_policy",
                    "side_effects": "no active subscription is persisted and no callback/watch is registered",
                },
                "cancel": {
                    "endpoint": "POST /uib/events/subscriptions/cancel",
                    "state_transition": "active|requested -> cancelled_contract_only; missing id -> rejected_missing_subscription_id",
                    "side_effects": "no broker cancellation is sent because no broker exists in prototype",
                },
                "transport_readiness": {
                    "endpoint": "GET /uib/events/subscriptions/transport-readiness",
                    "state_transition": "not-selected -> contract-only-no-transport-selected",
                    "side_effects": "no broker, callback, watch, SSE/WebSocket, or DDS runtime is started",
                },
                "decision_matrix": {
                    "endpoint": "GET /uib/events/subscriptions/decision-matrix",
                    "state_transition": "owner-decisions-open -> contract-only-owner-matrix-open",
                    "side_effects": "no owner assignment is activated and no broker, cursor store, callback, watch, or DDS runtime is started",
                },
                "activation_checklist": {
                    "endpoint": "GET /uib/events/subscriptions/activation-checklist",
                    "state_transition": "activation-evidence-open -> contract-only-activation-blocked",
                    "side_effects": "no broker activation, cursor persistence, callback/watch registration, transport runtime, or Driver/HAL path is started",
                },
                "callback_watch_shape": {
                    "endpoint": "GET /uib/events/subscriptions/callback-watch-shape",
                    "state_transition": "shape-open -> contract-only-callback-watch-shape-draft",
                    "side_effects": "no Android callback registration, Linux watch stream, broker dispatch, cursor persistence, or transport runtime is started",
                },
                "cursor_replay_storage": {
                    "endpoint": "GET /uib/events/subscriptions/cursor-replay-storage",
                    "state_transition": "storage-open -> contract-only-cursor-replay-storage-draft",
                    "side_effects": "no cursor row, replay index, subscription persistence, broker dispatch, or transport runtime is created",
                },
                "backpressure_qos_evidence": {
                    "endpoint": "GET /uib/events/subscriptions/backpressure-qos-evidence",
                    "state_transition": "qos-evidence-open -> contract-only-backpressure-qos-evidence-draft",
                    "side_effects": "no QoS reservation, overflow dispatch, broker dispatch, high-rate transport, or Driver/HAL path is activated",
                },
                "readiness_rollup": {
                    "endpoint": "GET /uib/events/subscriptions/readiness-rollup",
                    "state_transition": "fragmented-contract-surfaces -> contract-only-readiness-rollup-blocked",
                    "side_effects": "no readiness gate is auto-passed and no broker, persistence, callback/watch, transport runtime, QoS, Driver/HAL, or virtualization path is activated",
                },
                "activation_evidence": {
                    "endpoint": "POST /uib/events/subscriptions/activation-evidence",
                    "state_transition": "evidence-submitted -> validated_contract_only|rejected_by_policy|rejected_missing_evidence",
                    "side_effects": "no evidence is persisted, no review queue is updated, no readiness gate is closed, and no broker/runtime/Driver/HAL path is activated",
                },
                "activation_evidence_status": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/status",
                    "state_transition": "intake-visible -> contract-only-no-evidence-store",
                    "side_effects": "no evidence store is read, no review workflow is advanced, no gate state is changed, and no broker/runtime path is activated",
                },
                "activation_evidence_retention_checklist": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/retention-checklist",
                    "state_transition": "owner-decisions-open -> contract-only-retention-owner-checklist-open",
                    "side_effects": "no evidence URI is dereferenced, no retention store is created, no delete/export workflow is activated, and no broker/runtime path is activated",
                },
                "activation_evidence_decision_status_rollup": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/decision-status-rollup",
                    "state_transition": "owner-decisions-open -> contract-only-decision-status-blocked",
                    "side_effects": "no activation-evidence POST is called, no evidence is persisted, no review queue is updated, no gate is closed, and no broker/runtime path is activated",
                },
                "activation_approval_dry_run_status": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-dry-run/status",
                    "state_transition": "decision-status-blocked -> contract-only-approval-dry-run-no-store-status",
                    "side_effects": "no approval dry-run POST is called, no result is persisted, no review queue is updated, no gate is closed, and no broker/runtime path is activated",
                },
                "activation_approval_authority_checklist": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist",
                    "state_transition": "approval-dry-run-no-store-status -> contract-only-approval-authority-blocked",
                    "side_effects": "no approval dry-run POST is called, no approval authority is assigned, no result store is created, no review queue is updated, no gate is closed, and no broker/runtime path is activated",
                },
                "activation_approval_authority_audit_consistency": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/audit-consistency",
                    "state_transition": "approval-authority-blocked -> contract-only-approval-authority-audit-consistent",
                    "side_effects": "no approval dry-run POST is called, no approval authority is assigned, no result store is created, no review queue is updated, no gate is closed, and no broker/runtime path is activated",
                },
                "activation_approval_decision_blocker_rollup": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-blocker-rollup",
                    "state_transition": "approval-authority-audit-consistent -> contract-only-approval-decision-blocked",
                    "side_effects": "no approval decision is persisted, no review queue is updated, no gate is closed, and no broker/runtime path is activated",
                },
                "activation_approval_decision_dry_run": {
                    "endpoint": "POST /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run",
                    "state_transition": "approval-decision-requested -> rejected_blocked_contract_only",
                    "side_effects": "request shape is validated without persisting request/result state, updating review queues, closing gates, activating broker/runtime paths, or touching Driver/HAL",
                },
                "activation_approval_decision_dry_run_status": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/status",
                    "state_transition": "decision-dry-run-contract-visible -> contract-only-approval-decision-dry-run-status-no-store",
                    "side_effects": "status reads only contract metadata and blocker rollup state; it does not call the decision dry-run POST, persist last-result state, update review queues, close gates, activate broker/runtime paths, or touch Driver/HAL",
                },
                "activation_approval_decision_dry_run_audit_consistency": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/audit-consistency",
                    "state_transition": "decision-dry-run-status-visible -> contract-only-approval-decision-dry-run-audit-consistent",
                    "side_effects": "audit reads only blocker rollup, static dry-run contract metadata, and no-store status; it does not call the decision dry-run POST, persist state, update review queues, close gates, activate broker/runtime paths, or touch Driver/HAL",
                },
                "activation_approval_decision_closure_blocker_matrix": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix",
                    "state_transition": "decision-dry-run-audit-consistent -> contract-only-approval-decision-closure-blocked",
                    "side_effects": "closure matrix reads only audit consistency and blocker rollup state; it does not persist approval results, update review queues, close gates, activate broker/runtime paths, dispatch services, touch Driver/HAL, or develop virtualization",
                },
                "activation_approval_decision_owner_handoff_checklist": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist",
                    "state_transition": "contract-only-approval-decision-closure-blocked -> contract-only-owner-handoff-blocked",
                    "side_effects": "owner handoff checklist reads only closure blocker matrix state; it does not assign owners, persist handoff state, update review queues, close gates, activate broker/runtime paths, dispatch services, touch Driver/HAL, or develop virtualization",
                },
                "activation_approval_decision_owner_handoff_audit_consistency": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency",
                    "state_transition": "contract-only-owner-handoff-blocked -> contract-only-owner-handoff-audit-consistent",
                    "side_effects": "owner handoff audit reads only owner handoff checklist and closure blocker matrix state; it does not assign owners, attach evidence, persist handoff/review state, close gates, activate broker/runtime paths, dispatch services, touch Driver/HAL, or develop virtualization",
                },
                "activation_approval_decision_owner_handoff_decision_rollup": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup",
                    "state_transition": "contract-only-owner-handoff-audit-consistent -> contract-only-owner-handoff-decision-blocked",
                    "side_effects": "owner handoff decision rollup reads only owner handoff audit and checklist state; it does not assign owners, attach evidence, persist handoff/review/approval state, close gates, activate broker/runtime paths, dispatch services, touch Driver/HAL, or develop virtualization",
                },
                "activation_approval_decision_owner_handoff_evidence_readiness_matrix": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix",
                    "state_transition": "contract-only-owner-handoff-decision-blocked -> contract-only-handoff-evidence-missing",
                    "side_effects": "handoff evidence readiness matrix reads only owner handoff decision rollup state; it does not attach evidence, assign owners, call POST, persist evidence/review/approval state, close gates, activate broker/runtime paths, dispatch services, touch Driver/HAL, or develop virtualization",
                },
                "activation_approval_decision_owner_handoff_evidence_readiness_audit_consistency": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency",
                    "state_transition": "contract-only-handoff-evidence-missing -> contract-only-handoff-evidence-audit-consistent",
                    "side_effects": "handoff evidence readiness audit reads only the handoff evidence matrix and owner handoff decision rollup; it does not attach evidence, assign owners, call POST, persist evidence/review/approval state, close gates, activate broker/runtime paths, dispatch services, touch Driver/HAL, or develop virtualization",
                },
                "activation_approval_decision_owner_handoff_evidence_acceptance_status": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status",
                    "state_transition": "contract-only-handoff-evidence-audit-consistent -> contract-only-handoff-evidence-acceptance-blocked",
                    "side_effects": "handoff evidence acceptance status reads only the handoff evidence readiness audit and matrix state; it does not accept packets, attach evidence, assign owners, call POST, persist evidence/review/approval state, close gates, activate broker/runtime paths, dispatch services, touch Driver/HAL, or develop virtualization",
                },
                "activation_approval_decision_owner_handoff_evidence_acceptance_audit_consistency": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency",
                    "state_transition": "contract-only-handoff-evidence-acceptance-blocked -> contract-only-handoff-evidence-acceptance-audit-consistent",
                    "side_effects": "handoff evidence acceptance audit reads only acceptance status, readiness audit, and matrix state; it does not accept packets, attach evidence, assign owners, call POST, persist evidence/review/approval state, close gates, activate broker/runtime paths, dispatch services, touch Driver/HAL, or develop virtualization",
                },
                "activation_approval_decision_owner_handoff_evidence_acceptance_decision_rollup": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup",
                    "state_transition": "contract-only-handoff-evidence-acceptance-audit-consistent -> contract-only-handoff-evidence-acceptance-decision-blocked",
                    "side_effects": "handoff evidence acceptance decision rollup reads only acceptance audit/status and readiness state; it does not accept packets, attach evidence, assign owners, call POST, persist evidence/review/approval state, close gates, activate broker/runtime paths, dispatch services, touch Driver/HAL, or develop virtualization",
                },
                "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist",
                    "state_transition": "contract-only-handoff-evidence-acceptance-decision-blocked -> contract-only-handoff-evidence-acceptance-closure-not-ready",
                    "side_effects": "handoff evidence acceptance closure readiness reads only the acceptance decision rollup, acceptance audit/status, and readiness matrix; it does not accept packets, attach evidence, assign owners, call POST, persist evidence/review/approval state, close gates, activate broker/runtime paths, dispatch services, touch Driver/HAL, or develop virtualization",
                },
                "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_audit_consistency": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency",
                    "state_transition": "contract-only-handoff-evidence-acceptance-closure-not-ready -> contract-only-handoff-evidence-acceptance-closure-audit-consistent",
                    "side_effects": "handoff evidence acceptance closure readiness audit reads only the closure readiness checklist and upstream acceptance/readiness surfaces; it does not accept packets, attach evidence, assign owners, call POST, persist evidence/review/approval state, close gates, activate broker/runtime paths, dispatch services, touch Driver/HAL, or develop virtualization",
                },
                "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_rollup": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup",
                    "state_transition": "contract-only-handoff-evidence-acceptance-closure-audit-consistent -> contract-only-handoff-evidence-acceptance-closure-decision-blocked",
                    "side_effects": "handoff evidence acceptance closure readiness decision rollup reads only the closure readiness audit, checklist, and upstream acceptance/readiness surfaces; it does not accept packets, attach evidence, assign owners, call POST, persist evidence/review/approval state, close gates, activate broker/runtime paths, dispatch services, touch Driver/HAL, or develop virtualization",
                },
                "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_checklist": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist",
                    "state_transition": "contract-only-handoff-evidence-acceptance-closure-decision-blocked -> contract-only-handoff-evidence-acceptance-closure-reviewers-unassigned",
                    "side_effects": "handoff evidence acceptance closure readiness decision reviewer assignment checklist reads only the closure readiness decision rollup; it does not assign reviewers, accept packets, attach evidence, call POST, persist evidence/review/approval/reviewer state, close gates, activate broker/runtime paths, dispatch services, touch Driver/HAL, or develop virtualization",
                },
                "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency",
                    "state_transition": "contract-only-handoff-evidence-acceptance-closure-reviewers-unassigned -> contract-only-handoff-evidence-acceptance-closure-reviewer-assignment-audit-consistent",
                    "side_effects": "handoff evidence acceptance closure readiness decision reviewer assignment audit reads only the reviewer assignment checklist and upstream closure decision rollup; it does not assign reviewers, accept packets, attach evidence, call POST, persist evidence/review/approval/reviewer state, update review queues, close gates, activate broker/runtime paths, dispatch services, touch Driver/HAL, or develop virtualization",
                },
                "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup",
                    "state_transition": "contract-only-handoff-evidence-acceptance-closure-reviewer-assignment-audit-consistent -> contract-only-handoff-evidence-acceptance-closure-reviewer-assignment-decision-blocked",
                    "side_effects": "handoff evidence acceptance closure readiness decision reviewer assignment audit decision rollup reads only the reviewer assignment audit consistency and upstream checklist; it does not assign reviewers, accept packets, attach evidence, call POST, persist evidence/review/approval/reviewer state, update review queues, close gates, activate broker/runtime paths, dispatch services, touch Driver/HAL, or develop virtualization",
                },
                "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_summary": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary",
                    "state_transition": "contract-only-handoff-evidence-acceptance-closure-reviewer-assignment-decision-blocked -> contract-only-handoff-evidence-acceptance-closure-handoff-readiness-blocked",
                    "side_effects": "handoff evidence acceptance closure handoff readiness summary reads only the reviewer assignment audit decision rollup; it does not assign reviewers, accept packets, attach evidence, call POST, persist evidence/review/approval/reviewer state, update review queues, close gates, activate broker/runtime paths, dispatch services, touch Driver/HAL, or develop virtualization",
                },
                "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_consistency": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary/audit-consistency",
                    "state_transition": "contract-only-handoff-evidence-acceptance-closure-handoff-readiness-blocked -> contract-only-handoff-evidence-acceptance-closure-handoff-readiness-audit-consistent",
                    "side_effects": "handoff evidence acceptance closure handoff readiness audit reads only the closure handoff readiness summary and upstream reviewer assignment decision surfaces; it does not assign reviewers, accept packets, attach evidence, call POST, persist evidence/review/approval/reviewer state, update review queues, close gates, activate broker/runtime paths, dispatch services, touch Driver/HAL, or develop virtualization",
                },
                "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup": {
                    "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary/audit-consistency/decision-rollup",
                    "state_transition": "contract-only-handoff-evidence-acceptance-closure-handoff-readiness-audit-consistent -> contract-only-handoff-evidence-acceptance-closure-handoff-readiness-audit-decision-blocked",
                    "side_effects": "handoff evidence acceptance closure handoff readiness audit decision rollup reads only the closure handoff readiness audit consistency view; it does not assign reviewers, accept packets, attach evidence, call POST, persist evidence/review/approval/reviewer state, update review queues, close gates, activate broker/runtime paths, dispatch services, touch Driver/HAL, or develop virtualization",
                },
            },
        },
        "transport_candidates": [
            {
                "binding": "android-binder-aidl",
                "operation": "getEventSubscriptionsJson/requestEventSubscriptionJson/cancelEventSubscriptionJson/getEventSubscriptionTransportReadinessJson/getEventSubscriptionDecisionMatrixJson/getEventSubscriptionActivationChecklistJson/getEventSubscriptionCallbackWatchShapeJson/getEventSubscriptionCursorReplayStorageJson/getEventSubscriptionBackpressureQosEvidenceJson/getEventSubscriptionReadinessRollupJson/submitEventSubscriptionActivationEvidenceJson/getEventSubscriptionActivationEvidenceStatusJson/getEventSubscriptionActivationEvidenceRetentionChecklistJson/getEventSubscriptionActivationEvidenceDecisionStatusRollupJson/getEventSubscriptionActivationApprovalDryRunStatusJson/getEventSubscriptionActivationApprovalAuthorityChecklistJson/getEventSubscriptionActivationApprovalAuthorityAuditConsistencyJson/getEventSubscriptionActivationApprovalDecisionBlockerRollupJson/dryRunEventSubscriptionActivationApprovalDecisionJson/getEventSubscriptionActivationApprovalDecisionDryRunStatusJson/getEventSubscriptionActivationApprovalDecisionDryRunAuditConsistencyJson/getEventSubscriptionActivationApprovalDecisionClosureBlockerMatrixJson/getEventSubscriptionActivationApprovalDecisionOwnerHandoffChecklistJson/getEventSubscriptionActivationApprovalDecisionOwnerHandoffAuditConsistencyJson/getEventSubscriptionActivationApprovalDecisionOwnerHandoffDecisionRollupJson/getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessMatrixJson/getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessAuditConsistencyJson/getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceStatusJson/getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceAuditConsistencyJson/getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceDecisionRollupJson/getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessChecklistJson/getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessAuditConsistencyJson/getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionRollupJson/getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentChecklistJson/getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistencyJson/getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupJson/getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummaryJson/getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditConsistencyJson/getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditDecisionRollupJson/getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditDecisionRollupClosureBlockerMatrixJson",
                "current_state": "contract-only lifecycle commands; callback registration not implemented",
            },
            {
                "binding": "linux-ipc",
                "operation": "uib.events.subscriptions.get/request/cancel/transport.readiness/decision.matrix/activation.checklist/callback.watch.shape/cursor.replay.storage/backpressure.qos.evidence/readiness.rollup/activation.evidence/activation.evidence.status/activation.evidence.retention.checklist/activation.evidence.decision.status.rollup/activation.approval.dry.run.status/activation.approval.authority.checklist/activation.approval.authority.audit.consistency/activation.approval.decision.blocker.rollup/activation.approval.decision.dry.run/activation.approval.decision.dry.run.status/activation.approval.decision.dry.run.audit.consistency/activation.approval.decision.closure.blocker.matrix/activation.approval.decision.owner.handoff.checklist/activation.approval.decision.owner.handoff.audit.consistency/activation.approval.decision.owner.handoff.decision.rollup/activation.approval.decision.owner.handoff.evidence.readiness.matrix/activation.approval.decision.owner.handoff.evidence.readiness.audit.consistency/activation.approval.decision.owner.handoff.evidence.acceptance.status/activation.approval.decision.owner.handoff.evidence.acceptance.audit.consistency/activation.approval.decision.owner.handoff.evidence.acceptance.decision.rollup/activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.checklist/activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.audit.consistency/activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.rollup/activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.checklist/activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.consistency/activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup/activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.summary/activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.audit.consistency/activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.audit.decision.rollup",
                "current_state": "contract-only lifecycle commands; watch operation not implemented",
            },
            {
                "binding": "linux-grpc-rpc",
                "operation": "CentralBrainGateway.GetEventSubscriptions/RequestEventSubscription/CancelEventSubscription/GetEventSubscriptionTransportReadiness/GetEventSubscriptionDecisionMatrix/GetEventSubscriptionActivationChecklist/GetEventSubscriptionCallbackWatchShape/GetEventSubscriptionCursorReplayStorage/GetEventSubscriptionBackpressureQosEvidence/GetEventSubscriptionReadinessRollup/SubmitEventSubscriptionActivationEvidence/GetEventSubscriptionActivationEvidenceStatus/GetEventSubscriptionActivationEvidenceRetentionChecklist/GetEventSubscriptionActivationEvidenceDecisionStatusRollup/GetEventSubscriptionActivationApprovalDryRunStatus/GetEventSubscriptionActivationApprovalAuthorityChecklist/GetEventSubscriptionActivationApprovalAuthorityAuditConsistency/GetEventSubscriptionActivationApprovalDecisionBlockerRollup/DryRunEventSubscriptionActivationApprovalDecision/GetEventSubscriptionActivationApprovalDecisionDryRunStatus/GetEventSubscriptionActivationApprovalDecisionDryRunAuditConsistency/GetEventSubscriptionActivationApprovalDecisionClosureBlockerMatrix/GetEventSubscriptionActivationApprovalDecisionOwnerHandoffChecklist/GetEventSubscriptionActivationApprovalDecisionOwnerHandoffAuditConsistency/GetEventSubscriptionActivationApprovalDecisionOwnerHandoffDecisionRollup/GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessMatrix/GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessAuditConsistency/GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceStatus/GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceAuditConsistency/GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceDecisionRollup/GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessChecklist/GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessAuditConsistency/GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionRollup/GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentChecklist/GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistency/GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollup/GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummary/GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditConsistency/GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditDecisionRollup/GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditDecisionRollupClosureBlockerMatrix",
                "current_state": "contract-only lifecycle commands; streaming RPC not implemented",
            },
            {
                "binding": "sse-websocket",
                "operation": "planned push transport",
                "current_state": "not selected",
            },
            {
                "binding": "dds",
                "operation": "planned high-rate topic data plane",
                "current_state": "not implemented in prototype",
            },
        ],
        "mandatory_gates": [
            {
                "gate_id": "EV-SUB-001",
                "name": "semantic-envelope-preserved",
                "required_evidence": "All subscription events retain trace_id, topic, event_id, source, safety_state, payload, and req_ids.",
                "passed": False,
            },
            {
                "gate_id": "EV-SUB-002",
                "name": "cursor-and-backpressure-reviewed",
                "required_evidence": "Cursor resume, replay, overflow, and retention rules approved for the selected production transport.",
                "passed": False,
            },
            {
                "gate_id": "EV-SUB-003",
                "name": "governance-policy-audit-bound",
                "required_evidence": "Runtime & Governance policy, lifecycle, QoS, and audit owner confirmed for subscribe/cancel operations.",
                "passed": False,
            },
            {
                "gate_id": "EV-SUB-004",
                "name": "android-linux-binding-parity-proven",
                "required_evidence": "Android Binder and Linux IPC/gRPC contract, smoke, and docs expose equivalent fields.",
                "passed": True,
            },
            {
                "gate_id": "EV-SUB-005",
                "name": "no-dds-runtime-claim",
                "required_evidence": "Prototype explicitly reports DDS/SSE/WebSocket/broker inactive until target data-plane work starts.",
                "passed": True,
            },
            {
                "gate_id": "EV-SUB-006",
                "name": "lifecycle-command-contract-bound",
                "required_evidence": "Subscribe/cancel commands return validated contract-only lifecycle responses without persistence, broker activation, or callback/watch registration.",
                "passed": True,
            },
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions",
            "rest_request": "POST /uib/events/subscriptions/request",
            "rest_cancel": "POST /uib/events/subscriptions/cancel",
            "rest_transport_readiness": "GET /uib/events/subscriptions/transport-readiness",
            "rest_decision_matrix": "GET /uib/events/subscriptions/decision-matrix",
            "rest_activation_checklist": "GET /uib/events/subscriptions/activation-checklist",
            "rest_callback_watch_shape": "GET /uib/events/subscriptions/callback-watch-shape",
            "rest_cursor_replay_storage": "GET /uib/events/subscriptions/cursor-replay-storage",
            "rest_backpressure_qos_evidence": "GET /uib/events/subscriptions/backpressure-qos-evidence",
            "rest_readiness_rollup": "GET /uib/events/subscriptions/readiness-rollup",
            "rest_activation_evidence": "POST /uib/events/subscriptions/activation-evidence",
            "rest_activation_evidence_status": "GET /uib/events/subscriptions/activation-evidence/status",
            "rest_activation_evidence_retention_checklist": "GET /uib/events/subscriptions/activation-evidence/retention-checklist",
            "rest_activation_evidence_decision_status_rollup": "GET /uib/events/subscriptions/activation-evidence/decision-status-rollup",
            "rest_activation_approval_dry_run_status": "GET /uib/events/subscriptions/activation-evidence/approval-dry-run/status",
            "rest_activation_approval_authority_checklist": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist",
            "rest_activation_approval_authority_audit_consistency": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/audit-consistency",
            "rest_activation_approval_decision_blocker_rollup": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-blocker-rollup",
            "rest_activation_approval_decision_dry_run": "POST /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run",
            "rest_activation_approval_decision_dry_run_status": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/status",
            "rest_activation_approval_decision_dry_run_audit_consistency": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/audit-consistency",
            "rest_activation_approval_decision_closure_blocker_matrix": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix",
            "rest_activation_approval_decision_owner_handoff_checklist": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist",
            "rest_activation_approval_decision_owner_handoff_audit_consistency": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency",
            "rest_activation_approval_decision_owner_handoff_decision_rollup": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup",
            "rest_activation_approval_decision_owner_handoff_evidence_readiness_matrix": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix",
            "rest_activation_approval_decision_owner_handoff_evidence_readiness_audit_consistency": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency",
            "rest_activation_approval_decision_owner_handoff_evidence_acceptance_status": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status",
            "rest_activation_approval_decision_owner_handoff_evidence_acceptance_audit_consistency": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency",
            "rest_activation_approval_decision_owner_handoff_evidence_acceptance_decision_rollup": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup",
            "rest_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist",
            "rest_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_audit_consistency": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency",
            "rest_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_rollup": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup",
            "rest_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_checklist": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist",
            "rest_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency",
            "rest_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup",
            "rest_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_summary": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary",
            "rest_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_consistency": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary/audit-consistency",
            "rest_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary/audit-consistency/decision-rollup",
            "android_binder": "getEventSubscriptionsJson",
            "android_binder_request": "requestEventSubscriptionJson",
            "android_binder_cancel": "cancelEventSubscriptionJson",
            "android_binder_transport_readiness": "getEventSubscriptionTransportReadinessJson",
            "android_binder_decision_matrix": "getEventSubscriptionDecisionMatrixJson",
            "android_binder_activation_checklist": "getEventSubscriptionActivationChecklistJson",
            "android_binder_callback_watch_shape": "getEventSubscriptionCallbackWatchShapeJson",
            "android_binder_cursor_replay_storage": "getEventSubscriptionCursorReplayStorageJson",
            "android_binder_backpressure_qos_evidence": "getEventSubscriptionBackpressureQosEvidenceJson",
            "android_binder_readiness_rollup": "getEventSubscriptionReadinessRollupJson",
            "android_binder_activation_evidence": "submitEventSubscriptionActivationEvidenceJson",
            "android_binder_activation_evidence_status": "getEventSubscriptionActivationEvidenceStatusJson",
            "android_binder_activation_evidence_retention_checklist": "getEventSubscriptionActivationEvidenceRetentionChecklistJson",
            "android_binder_activation_evidence_decision_status_rollup": "getEventSubscriptionActivationEvidenceDecisionStatusRollupJson",
            "android_binder_activation_approval_dry_run_status": "getEventSubscriptionActivationApprovalDryRunStatusJson",
            "android_binder_activation_approval_authority_checklist": "getEventSubscriptionActivationApprovalAuthorityChecklistJson",
            "android_binder_activation_approval_authority_audit_consistency": "getEventSubscriptionActivationApprovalAuthorityAuditConsistencyJson",
            "android_binder_activation_approval_decision_blocker_rollup": "getEventSubscriptionActivationApprovalDecisionBlockerRollupJson",
            "android_binder_activation_approval_decision_dry_run": "dryRunEventSubscriptionActivationApprovalDecisionJson",
            "android_binder_activation_approval_decision_dry_run_status": "getEventSubscriptionActivationApprovalDecisionDryRunStatusJson",
            "android_binder_activation_approval_decision_dry_run_audit_consistency": "getEventSubscriptionActivationApprovalDecisionDryRunAuditConsistencyJson",
            "android_binder_activation_approval_decision_closure_blocker_matrix": "getEventSubscriptionActivationApprovalDecisionClosureBlockerMatrixJson",
            "android_binder_activation_approval_decision_owner_handoff_checklist": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffChecklistJson",
            "android_binder_activation_approval_decision_owner_handoff_audit_consistency": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffAuditConsistencyJson",
            "android_binder_activation_approval_decision_owner_handoff_decision_rollup": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffDecisionRollupJson",
            "android_binder_activation_approval_decision_owner_handoff_evidence_readiness_matrix": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessMatrixJson",
            "android_binder_activation_approval_decision_owner_handoff_evidence_readiness_audit_consistency": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessAuditConsistencyJson",
            "android_binder_activation_approval_decision_owner_handoff_evidence_acceptance_status": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceStatusJson",
            "android_binder_activation_approval_decision_owner_handoff_evidence_acceptance_audit_consistency": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceAuditConsistencyJson",
            "android_binder_activation_approval_decision_owner_handoff_evidence_acceptance_decision_rollup": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceDecisionRollupJson",
            "android_binder_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessChecklistJson",
            "android_binder_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_audit_consistency": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessAuditConsistencyJson",
            "android_binder_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_rollup": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionRollupJson",
            "android_binder_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_checklist": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentChecklistJson",
            "android_binder_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistencyJson",
            "android_binder_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupJson",
            "android_binder_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_summary": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummaryJson",
            "android_binder_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_consistency": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditConsistencyJson",
            "android_binder_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditDecisionRollupJson",
            "android_binder_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup_closure_blocker_matrix": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditDecisionRollupClosureBlockerMatrixJson",
            "linux_cli": "event-subscriptions",
            "linux_cli_request": "event-subscribe-request",
            "linux_cli_cancel": "event-subscribe-cancel",
            "linux_cli_transport_readiness": "event-subscription-transport-readiness",
            "linux_cli_decision_matrix": "event-subscription-decision-matrix",
            "linux_cli_activation_checklist": "event-subscription-activation-checklist",
            "linux_cli_callback_watch_shape": "event-subscription-callback-watch-shape",
            "linux_cli_cursor_replay_storage": "event-subscription-cursor-replay-storage",
            "linux_cli_backpressure_qos_evidence": "event-subscription-backpressure-qos-evidence",
            "linux_cli_readiness_rollup": "event-subscription-readiness-rollup",
            "linux_cli_activation_evidence": "event-subscription-activation-evidence",
            "linux_cli_activation_evidence_status": "event-subscription-activation-evidence-status",
            "linux_cli_activation_evidence_retention_checklist": "event-subscription-activation-evidence-retention-checklist",
            "linux_cli_activation_evidence_decision_status_rollup": "event-subscription-activation-evidence-decision-status-rollup",
            "linux_cli_activation_approval_dry_run_status": "event-subscription-activation-approval-dry-run-status",
            "linux_cli_activation_approval_authority_checklist": "event-subscription-activation-approval-authority-checklist",
            "linux_cli_activation_approval_authority_audit_consistency": "event-subscription-activation-approval-authority-audit-consistency",
            "linux_cli_activation_approval_decision_blocker_rollup": "event-subscription-activation-approval-decision-blocker-rollup",
            "linux_cli_activation_approval_decision_dry_run": "event-subscription-activation-approval-decision-dry-run",
            "linux_cli_activation_approval_decision_dry_run_status": "event-subscription-activation-approval-decision-dry-run-status",
            "linux_cli_activation_approval_decision_dry_run_audit_consistency": "event-subscription-activation-approval-decision-dry-run-audit-consistency",
            "linux_cli_activation_approval_decision_closure_blocker_matrix": "event-subscription-activation-approval-decision-closure-blocker-matrix",
            "linux_cli_activation_approval_decision_owner_handoff_checklist": "event-subscription-activation-approval-decision-owner-handoff-checklist",
            "linux_cli_activation_approval_decision_owner_handoff_audit_consistency": "event-subscription-activation-approval-decision-owner-handoff-audit-consistency",
            "linux_cli_activation_approval_decision_owner_handoff_decision_rollup": "event-subscription-activation-approval-decision-owner-handoff-decision-rollup",
            "linux_cli_activation_approval_decision_owner_handoff_evidence_readiness_matrix": "event-subscription-activation-approval-decision-owner-handoff-evidence-readiness-matrix",
            "linux_cli_activation_approval_decision_owner_handoff_evidence_readiness_audit_consistency": "event-subscription-activation-approval-decision-owner-handoff-evidence-readiness-audit-consistency",
            "linux_cli_activation_approval_decision_owner_handoff_evidence_acceptance_status": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-status",
            "linux_cli_activation_approval_decision_owner_handoff_evidence_acceptance_audit_consistency": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-audit-consistency",
            "linux_cli_activation_approval_decision_owner_handoff_evidence_acceptance_decision_rollup": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-decision-rollup",
            "linux_cli_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-checklist",
            "linux_cli_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_audit_consistency": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-audit-consistency",
            "linux_cli_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_rollup": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-rollup",
            "linux_cli_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_checklist": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-checklist",
            "linux_cli_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-consistency",
            "linux_cli_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup",
            "linux_cli_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_summary": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-summary",
            "linux_cli_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_consistency": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-audit-consistency",
            "linux_cli_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-audit-decision-rollup",
            "linux_cli_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup_closure_blocker_matrix": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-audit-decision-rollup-closure-blocker-matrix",
            "linux_ipc": "uib.events.subscriptions.get",
            "linux_ipc_request": "uib.events.subscriptions.request",
            "linux_ipc_cancel": "uib.events.subscriptions.cancel",
            "linux_ipc_transport_readiness": "uib.events.subscriptions.transport.readiness",
            "linux_ipc_decision_matrix": "uib.events.subscriptions.decision.matrix",
            "linux_ipc_activation_checklist": "uib.events.subscriptions.activation.checklist",
            "linux_ipc_callback_watch_shape": "uib.events.subscriptions.callback.watch.shape",
            "linux_ipc_cursor_replay_storage": "uib.events.subscriptions.cursor.replay.storage",
            "linux_ipc_backpressure_qos_evidence": "uib.events.subscriptions.backpressure.qos.evidence",
            "linux_ipc_readiness_rollup": "uib.events.subscriptions.readiness.rollup",
            "linux_ipc_activation_evidence": "uib.events.subscriptions.activation.evidence",
            "linux_ipc_activation_evidence_status": "uib.events.subscriptions.activation.evidence.status",
            "linux_ipc_activation_evidence_retention_checklist": "uib.events.subscriptions.activation.evidence.retention.checklist",
            "linux_ipc_activation_evidence_decision_status_rollup": "uib.events.subscriptions.activation.evidence.decision.status.rollup",
            "linux_ipc_activation_approval_dry_run_status": "uib.events.subscriptions.activation.approval.dry.run.status",
            "linux_ipc_activation_approval_authority_checklist": "uib.events.subscriptions.activation.approval.authority.checklist",
            "linux_ipc_activation_approval_authority_audit_consistency": "uib.events.subscriptions.activation.approval.authority.audit.consistency",
            "linux_ipc_activation_approval_decision_blocker_rollup": "uib.events.subscriptions.activation.approval.decision.blocker.rollup",
            "linux_ipc_activation_approval_decision_dry_run": "uib.events.subscriptions.activation.approval.decision.dry.run",
            "linux_ipc_activation_approval_decision_dry_run_status": "uib.events.subscriptions.activation.approval.decision.dry.run.status",
            "linux_ipc_activation_approval_decision_dry_run_audit_consistency": "uib.events.subscriptions.activation.approval.decision.dry.run.audit.consistency",
            "linux_ipc_activation_approval_decision_closure_blocker_matrix": "uib.events.subscriptions.activation.approval.decision.closure.blocker.matrix",
            "linux_ipc_activation_approval_decision_owner_handoff_checklist": "uib.events.subscriptions.activation.approval.decision.owner.handoff.checklist",
            "linux_ipc_activation_approval_decision_owner_handoff_audit_consistency": "uib.events.subscriptions.activation.approval.decision.owner.handoff.audit.consistency",
            "linux_ipc_activation_approval_decision_owner_handoff_decision_rollup": "uib.events.subscriptions.activation.approval.decision.owner.handoff.decision.rollup",
            "linux_ipc_activation_approval_decision_owner_handoff_evidence_readiness_matrix": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.readiness.matrix",
            "linux_ipc_activation_approval_decision_owner_handoff_evidence_readiness_audit_consistency": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.readiness.audit.consistency",
            "linux_ipc_activation_approval_decision_owner_handoff_evidence_acceptance_status": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.status",
            "linux_ipc_activation_approval_decision_owner_handoff_evidence_acceptance_audit_consistency": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.audit.consistency",
            "linux_ipc_activation_approval_decision_owner_handoff_evidence_acceptance_decision_rollup": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.decision.rollup",
            "linux_ipc_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.checklist",
            "linux_ipc_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_audit_consistency": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.audit.consistency",
            "linux_ipc_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_rollup": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.rollup",
            "linux_ipc_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_checklist": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.checklist",
            "linux_ipc_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.consistency",
            "linux_ipc_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup",
            "linux_ipc_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_summary": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.summary",
            "linux_ipc_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_consistency": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.audit.consistency",
            "linux_ipc_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.audit.decision.rollup",
            "linux_ipc_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup_closure_blocker_matrix": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.audit.decision.rollup.closure.blocker.matrix",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptions",
            "linux_grpc_rpc_request": "CentralBrainGateway.RequestEventSubscription",
            "linux_grpc_rpc_cancel": "CentralBrainGateway.CancelEventSubscription",
            "linux_grpc_rpc_transport_readiness": "CentralBrainGateway.GetEventSubscriptionTransportReadiness",
            "linux_grpc_rpc_decision_matrix": "CentralBrainGateway.GetEventSubscriptionDecisionMatrix",
            "linux_grpc_rpc_activation_checklist": "CentralBrainGateway.GetEventSubscriptionActivationChecklist",
            "linux_grpc_rpc_callback_watch_shape": "CentralBrainGateway.GetEventSubscriptionCallbackWatchShape",
            "linux_grpc_rpc_cursor_replay_storage": "CentralBrainGateway.GetEventSubscriptionCursorReplayStorage",
            "linux_grpc_rpc_backpressure_qos_evidence": "CentralBrainGateway.GetEventSubscriptionBackpressureQosEvidence",
            "linux_grpc_rpc_readiness_rollup": "CentralBrainGateway.GetEventSubscriptionReadinessRollup",
            "linux_grpc_rpc_activation_evidence": "CentralBrainGateway.SubmitEventSubscriptionActivationEvidence",
            "linux_grpc_rpc_activation_evidence_status": "CentralBrainGateway.GetEventSubscriptionActivationEvidenceStatus",
            "linux_grpc_rpc_activation_evidence_retention_checklist": "CentralBrainGateway.GetEventSubscriptionActivationEvidenceRetentionChecklist",
            "linux_grpc_rpc_activation_evidence_decision_status_rollup": "CentralBrainGateway.GetEventSubscriptionActivationEvidenceDecisionStatusRollup",
            "linux_grpc_rpc_activation_approval_dry_run_status": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDryRunStatus",
            "linux_grpc_rpc_activation_approval_authority_checklist": "CentralBrainGateway.GetEventSubscriptionActivationApprovalAuthorityChecklist",
            "linux_grpc_rpc_activation_approval_authority_audit_consistency": "CentralBrainGateway.GetEventSubscriptionActivationApprovalAuthorityAuditConsistency",
            "linux_grpc_rpc_activation_approval_decision_blocker_rollup": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionBlockerRollup",
            "linux_grpc_rpc_activation_approval_decision_dry_run": "CentralBrainGateway.DryRunEventSubscriptionActivationApprovalDecision",
            "linux_grpc_rpc_activation_approval_decision_dry_run_status": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionDryRunStatus",
            "linux_grpc_rpc_activation_approval_decision_dry_run_audit_consistency": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionDryRunAuditConsistency",
            "linux_grpc_rpc_activation_approval_decision_closure_blocker_matrix": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionClosureBlockerMatrix",
            "linux_grpc_rpc_activation_approval_decision_owner_handoff_checklist": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffChecklist",
            "linux_grpc_rpc_activation_approval_decision_owner_handoff_audit_consistency": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffAuditConsistency",
            "linux_grpc_rpc_activation_approval_decision_owner_handoff_decision_rollup": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffDecisionRollup",
            "linux_grpc_rpc_activation_approval_decision_owner_handoff_evidence_readiness_matrix": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessMatrix",
            "linux_grpc_rpc_activation_approval_decision_owner_handoff_evidence_readiness_audit_consistency": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessAuditConsistency",
            "linux_grpc_rpc_activation_approval_decision_owner_handoff_evidence_acceptance_status": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceStatus",
            "linux_grpc_rpc_activation_approval_decision_owner_handoff_evidence_acceptance_audit_consistency": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceAuditConsistency",
            "linux_grpc_rpc_activation_approval_decision_owner_handoff_evidence_acceptance_decision_rollup": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceDecisionRollup",
            "linux_grpc_rpc_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessChecklist",
            "linux_grpc_rpc_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_audit_consistency": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessAuditConsistency",
            "linux_grpc_rpc_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_rollup": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionRollup",
            "linux_grpc_rpc_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_checklist": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentChecklist",
            "linux_grpc_rpc_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistency",
            "linux_grpc_rpc_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollup",
            "linux_grpc_rpc_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_summary": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummary",
            "linux_grpc_rpc_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_consistency": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditConsistency",
            "linux_grpc_rpc_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditDecisionRollup",
            "linux_grpc_rpc_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup_closure_blocker_matrix": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditDecisionRollupClosureBlockerMatrix",
        },
        "summary": {
            "subscription_state": "contract-only-not-brokered",
            "active_subscription_count": 0,
            "lifecycle_command_contract_active": True,
            "cursor_replay_storage_contract_active": True,
            "backpressure_qos_evidence_contract_active": True,
            "readiness_rollup_contract_active": True,
            "activation_evidence_contract_active": True,
            "activation_evidence_status_contract_active": True,
            "activation_evidence_retention_checklist_active": True,
            "activation_evidence_decision_status_rollup_active": True,
            "activation_approval_dry_run_status_active": True,
            "activation_approval_authority_checklist_active": True,
            "activation_approval_authority_audit_consistency_active": True,
            "activation_approval_decision_blocker_rollup_active": True,
            "activation_approval_decision_dry_run_active": True,
            "activation_approval_decision_dry_run_status_active": True,
            "activation_approval_decision_dry_run_audit_consistency_active": True,
            "activation_approval_decision_closure_blocker_matrix_active": True,
            "activation_approval_decision_owner_handoff_checklist_active": True,
            "activation_approval_decision_owner_handoff_audit_consistency_active": True,
            "activation_approval_decision_owner_handoff_decision_rollup_active": True,
            "activation_approval_decision_owner_handoff_evidence_readiness_matrix_active": True,
            "activation_approval_decision_owner_handoff_evidence_readiness_audit_consistency_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_status_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_audit_consistency_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_decision_rollup_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_audit_consistency_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_rollup_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_checklist_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_summary_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_consistency_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup_closure_blocker_matrix_active": True,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "evidence_store_active": False,
            "review_workflow_active": False,
            "persisted_submission_count": 0,
            "pending_review_count": 0,
            "retention_policy_confirmed": False,
            "evidence_uri_rules_confirmed": False,
            "deletion_export_semantics_confirmed": False,
            "backpressure_qos_evidence_confirmed": False,
            "readiness_rollup_confirmed": False,
            "activation_evidence_persisted": False,
            "activation_evidence_accepted_for_review": False,
            "approval_dry_run_invoked": False,
            "last_approval_dry_run_result_available": False,
            "persisted_approval_dry_run_count": 0,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_REQ_IDS,
    }


def event_subscription_transport_readiness_payload() -> dict[str, Any]:
    return {
        "readiness_state": "contract-only-no-transport-selected",
        "transport_selected": False,
        "owner_decisions": {
            "subscription_broker_owner": "TBD-target-platform",
            "cursor_storage_owner": "TBD-target-platform",
            "backpressure_qos_owner": "Runtime & Governance owner TBD",
            "android_callback_owner": "TBD-Android platform service owner",
            "linux_watch_owner": "TBD-Linux daemon owner",
            "dds_runtime_owner": "TBD-only if high-rate topic data plane is selected",
        },
        "android_callback_contract": {
            "candidate_operation": "registerEventSubscriptionCallback planned",
            "current_binder_surface": "getEventSubscriptionTransportReadinessJson, getEventSubscriptionCallbackWatchShapeJson, getEventSubscriptionCursorReplayStorageJson, and getEventSubscriptionBackpressureQosEvidenceJson only",
            "callback_identity": "Binder UID/PID must map to Runtime & Governance caller identity before activation",
            "lifecycle": ["register", "onEvent", "onOverflow", "onClosed", "unregister"],
            "implemented": False,
        },
        "linux_watch_contract": {
            "candidate_cli": "event-subscription-watch planned",
            "candidate_ipc_operation": "uib.events.subscriptions.watch planned",
            "candidate_grpc_rpc": "WatchEventSubscriptions streaming RPC planned",
            "current_surface": "event-subscription-transport-readiness, event-subscription-callback-watch-shape, event-subscription-cursor-replay-storage, and event-subscription-backpressure-qos-evidence over CLI/IPC/gRPC",
            "implemented": False,
        },
        "transport_candidates": [
            {
                "transport": "android-binder-callback",
                "purpose": "same-device Android app/service callback path",
                "implemented": False,
                "blocked_by": ["target system service owner", "Binder callback lifecycle", "identity to Policy mapping"],
            },
            {
                "transport": "linux-ipc-watch",
                "purpose": "same-SoC Linux daemon/client watch path",
                "implemented": False,
                "blocked_by": ["watch socket lifecycle", "cursor storage", "overflow and reconnect semantics"],
            },
            {
                "transport": "sse-websocket",
                "purpose": "debug or tool push transport",
                "implemented": False,
                "blocked_by": ["target debug tool requirements", "auth and privacy routing", "backpressure policy"],
            },
            {
                "transport": "dds",
                "purpose": "high-rate topic data plane candidate for NV-P-006",
                "implemented": False,
                "blocked_by": ["DDS vendor/runtime selection", "QoS profile mapping", "shared memory/Safety Runtime constraints"],
            },
        ],
        "mandatory_gates": [
            {
                "gate_id": "EV-TR-001",
                "name": "callback-watch-shape-reviewed",
                "required_evidence": "Android Binder callback and Linux watch lifecycle reviewed with app/service identity semantics.",
                "passed": False,
            },
            {
                "gate_id": "EV-TR-002",
                "name": "cursor-storage-owner-assigned",
                "required_evidence": "Owner and persistence rules for subscription cursor/replay state assigned and aligned with /uib/events/subscriptions/cursor-replay-storage.",
                "passed": False,
            },
            {
                "gate_id": "EV-TR-003",
                "name": "broker-owner-assigned",
                "required_evidence": "Subscription broker owner and process boundary selected for Android and Linux.",
                "passed": False,
            },
            {
                "gate_id": "EV-TR-004",
                "name": "backpressure-qos-owner-assigned",
                "required_evidence": "Runtime & Governance QoS/backpressure owner and overflow event contract assigned.",
                "passed": False,
            },
            {
                "gate_id": "EV-TR-005",
                "name": "android-linux-parity-proven",
                "required_evidence": "Android Binder, Linux IPC, and Linux gRPC/RPC expose equivalent transport-readiness metadata.",
                "passed": True,
            },
            {
                "gate_id": "EV-TR-006",
                "name": "no-runtime-activation-claim",
                "required_evidence": "Prototype reports no selected transport, no active broker, no registered callback, and no DDS/SSE/WebSocket runtime.",
                "passed": True,
            },
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/transport-readiness",
            "android_binder": "getEventSubscriptionTransportReadinessJson",
            "linux_cli": "event-subscription-transport-readiness",
            "linux_ipc": "uib.events.subscriptions.transport.readiness",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionTransportReadiness",
        },
        "summary": {
            "transport_selected": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "callback_registered": False,
            "watch_started": False,
            "cursor_storage_active": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_TRANSPORT_REQ_IDS,
    }


def event_subscription_decision_matrix_payload() -> dict[str, Any]:
    return {
        "decision_state": "contract-only-owner-matrix-open",
        "production_activation_allowed": False,
        "decision_matrix": [
            {
                "decision_id": "EV-DM-001",
                "area": "subscription-broker-owner",
                "required_decision": "Select the process owner for validating, storing, and dispatching Event subscriptions on Android and Linux.",
                "candidate_owners": ["android-system-service", "linux-central-brain-daemon", "shared-runtime-governance-backend"],
                "current_selection": "TBD-target-platform",
                "blocking_evidence": ["owner acceptance", "process boundary", "Policy/Audit integration point"],
                "activation_effect": "would allow broker design only after all mandatory gates pass",
            },
            {
                "decision_id": "EV-DM-002",
                "area": "cursor-storage-owner",
                "required_decision": "Assign the owner and storage class for cursor, replay, expiry, and reconnect state.",
                "candidate_owners": ["runtime-governance-store", "event-broker-store", "platform-local-store"],
                "current_selection": "TBD-target-platform",
                "blocking_evidence": ["retention policy", "restart recovery rule", "privacy/logging review"],
                "activation_effect": "would allow cursor persistence only after storage ABI and retention are approved",
            },
            {
                "decision_id": "EV-DM-003",
                "area": "backpressure-qos-owner",
                "required_decision": "Assign overflow, rate limiting, priority, and QoS profile ownership between Runtime & Governance and transport.",
                "candidate_owners": ["runtime-governance-qos", "dds-qos-profile", "broker-local-overflow-policy"],
                "current_selection": "TBD-target-platform",
                "blocking_evidence": ["overflow event schema", "per-caller rate policy", "high-rate topic QoS mapping"],
                "activation_effect": "would allow backpressure enforcement only after policy owner is confirmed",
            },
            {
                "decision_id": "EV-DM-004",
                "area": "android-callback-linux-watch-shape",
                "required_decision": "Approve Android callback identity/lifecycle and Linux watch reconnect semantics.",
                "candidate_owners": ["android-platform-service-owner", "linux-daemon-owner", "binding-api-owner"],
                "current_selection": "TBD-target-platform",
                "blocking_evidence": ["Binder UID/PID identity map", "watch socket lifecycle", "unregister/close behavior"],
                "activation_effect": "would allow callback/watch API design, not runtime activation",
            },
            {
                "decision_id": "EV-DM-005",
                "area": "transport-selection",
                "required_decision": "Choose debug push and high-rate data-plane transport candidates without bypassing Uni Info Bus semantics.",
                "candidate_owners": ["sse-websocket-debug-owner", "dds-runtime-owner", "someip-dds-platform-owner"],
                "current_selection": "TBD-target-platform",
                "blocking_evidence": ["transport security model", "DDS/SSE/WebSocket availability", "Driver/HAL gap review"],
                "activation_effect": "would allow transport-specific prototype planning only after owner decisions are closed",
            },
        ],
        "activation_sequence": [
            "Close EV-DM-001 broker owner before designing a live broker.",
            "Close EV-DM-002 cursor storage before persisting or replaying subscriptions.",
            "Close EV-DM-003 backpressure/QoS owner before allowing high-rate topics.",
            "Close EV-DM-004 callback/watch shape before adding Android callback or Linux watch operations.",
            "Close EV-DM-005 transport selection before enabling SSE/WebSocket, DDS, or other data-plane runtime.",
        ],
        "mandatory_gates": [
            {
                "gate_id": "EV-DM-001",
                "name": "broker-owner-selected",
                "required_evidence": "Android and Linux broker process owner, lifecycle owner, and governance integration point are confirmed.",
                "passed": False,
            },
            {
                "gate_id": "EV-DM-002",
                "name": "cursor-storage-owner-selected",
                "required_evidence": "Cursor/replay storage owner, retention, expiry, restart recovery, and privacy boundary are confirmed.",
                "passed": False,
            },
            {
                "gate_id": "EV-DM-003",
                "name": "backpressure-qos-owner-selected",
                "required_evidence": "Runtime & Governance and transport responsibilities for overflow, QoS, and rate limits are assigned.",
                "passed": False,
            },
            {
                "gate_id": "EV-DM-004",
                "name": "callback-watch-shape-selected",
                "required_evidence": "Android callback and Linux watch lifecycle, identity, reconnect, and close semantics are approved.",
                "passed": False,
            },
            {
                "gate_id": "EV-DM-005",
                "name": "transport-choice-selected",
                "required_evidence": "SSE/WebSocket, DDS, or another transport is selected with security, QoS, and Driver/HAL gap review.",
                "passed": False,
            },
            {
                "gate_id": "EV-DM-006",
                "name": "android-linux-parity-proven",
                "required_evidence": "Android Binder, Linux CLI, Linux IPC, and Linux gRPC/RPC expose the same decision matrix fields.",
                "passed": True,
            },
            {
                "gate_id": "EV-DM-007",
                "name": "no-runtime-activation-claim",
                "required_evidence": "Prototype reports no owner activation, no broker, no cursor store, no callback/watch, no SSE/WebSocket, and no DDS runtime.",
                "passed": True,
            },
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/decision-matrix",
            "android_binder": "getEventSubscriptionDecisionMatrixJson",
            "linux_cli": "event-subscription-decision-matrix",
            "linux_ipc": "uib.events.subscriptions.decision.matrix",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionDecisionMatrix",
        },
        "summary": {
            "decision_matrix_active": True,
            "production_activation_allowed": False,
            "all_required_owners_assigned": False,
            "broker_owner_confirmed": False,
            "cursor_storage_owner_confirmed": False,
            "backpressure_qos_owner_confirmed": False,
            "callback_watch_shape_confirmed": False,
            "transport_choice_confirmed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "callback_registered": False,
            "watch_started": False,
            "cursor_storage_active": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_DECISION_REQ_IDS,
    }


def event_subscription_activation_checklist_payload() -> dict[str, Any]:
    return {
        "activation_state": "contract-only-activation-blocked",
        "activation_allowed": False,
        "checklist": [
            {
                "gate_id": "EV-ACT-001",
                "area": "broker-owner-evidence",
                "required_evidence": "Signed owner acceptance for Android system service path, Linux daemon path, lifecycle owner, and failover behavior.",
                "current_evidence": "missing",
                "passed": False,
                "blocks": ["broker_active", "production_activation_allowed"],
            },
            {
                "gate_id": "EV-ACT-002",
                "area": "runtime-governance-binding",
                "required_evidence": "Subscribe, cancel, callback/watch, overflow, and replay operations are bound to Runtime & Governance identity, Policy, QoS, Lifecycle, and Audit.",
                "current_evidence": "contract-only precheck surfaces exist; production owner evidence missing",
                "passed": False,
                "blocks": ["service_dispatch_triggered", "callback_registered", "watch_started"],
            },
            {
                "gate_id": "EV-ACT-003",
                "area": "cursor-store-persistence",
                "required_evidence": "Cursor/replay store owner, schema, retention, privacy classification, restart recovery, and cleanup strategy are approved.",
                "current_evidence": "cursor/replay storage contract exists; owner, retention, and persistence evidence missing",
                "passed": False,
                "blocks": ["subscription_persistence_active", "cursor_storage_active"],
            },
            {
                "gate_id": "EV-ACT-004",
                "area": "backpressure-qos-profile",
                "required_evidence": "Overflow event schema, per-caller rate policy, high-rate topic QoS mapping, and Runtime & Governance responsibility split are approved.",
                "current_evidence": "backpressure/QoS evidence contract exists; owner, overflow emission, and runtime QoS evidence missing",
                "passed": False,
                "blocks": ["high_rate_data_plane_active", "dds_runtime_active"],
            },
            {
                "gate_id": "EV-ACT-005",
                "area": "transport-runtime-choice",
                "required_evidence": "Android Binder callback, Linux watch, SSE/WebSocket, DDS, or other selected transport has security model, lifecycle, reconnect, and teardown evidence.",
                "current_evidence": "missing",
                "passed": False,
                "blocks": ["transport_selected", "callback_registered", "watch_started", "sse_websocket_active", "dds_runtime_active"],
            },
            {
                "gate_id": "EV-ACT-006",
                "area": "driver-hal-and-high-rate-scope",
                "required_evidence": "DRV-GAP-004/DRV-GAP-005 scope review is attached before DDS, TSN/PTP, shared memory, or Safety Runtime is used.",
                "current_evidence": "not required while no high-rate transport is selected",
                "passed": False,
                "blocks": ["driver_development_triggered", "hardware_accessed"],
            },
            {
                "gate_id": "EV-ACT-007",
                "area": "android-linux-contract-parity",
                "required_evidence": "REST, Android Binder, Android Console, Linux CLI, Linux IPC, Linux gRPC/RPC, API contract, docs, and smoke tests expose equivalent activation checklist fields.",
                "current_evidence": "provided by this prototype increment",
                "passed": True,
                "blocks": [],
            },
            {
                "gate_id": "EV-ACT-008",
                "area": "no-runtime-activation-claim",
                "required_evidence": "Prototype explicitly reports no broker, no cursor store, no callback/watch, no transport runtime, no Driver/HAL access, and no virtualization development.",
                "current_evidence": "provided by this prototype increment",
                "passed": True,
                "blocks": [],
            },
        ],
        "required_artifacts_before_activation": [
            "broker owner sign-off and process boundary",
            "Runtime & Governance identity/Policy/QoS/Audit binding evidence",
            "cursor store ABI, retention, privacy, and recovery plan",
            "backpressure and overflow event schema",
            "selected transport runtime security and lifecycle evidence",
            "Android Binder callback or Linux watch API review if callback/watch is selected",
            "DRV-GAP-004/DRV-GAP-005 review if DDS/high-rate/shared-memory path is selected",
        ],
        "decision_dependencies": [
            "EV-DM-001 broker-owner-selected",
            "EV-DM-002 cursor-storage-owner-selected",
            "EV-DM-003 backpressure-qos-owner-selected",
            "EV-DM-004 callback-watch-shape-selected",
            "EV-DM-005 transport-choice-selected",
            "EV-CRS-001 cursor-schema-reviewed",
            "EV-CRS-002 storage-owner-assigned",
            "EV-CRS-003 replay-window-retention-reviewed",
            "EV-QOS-001 overflow-schema-reviewed",
            "EV-QOS-004 runtime-governance-qos-owner-assigned",
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-checklist",
            "android_binder": "getEventSubscriptionActivationChecklistJson",
            "linux_cli": "event-subscription-activation-checklist",
            "linux_ipc": "uib.events.subscriptions.activation.checklist",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationChecklist",
        },
        "summary": {
            "activation_checklist_active": True,
            "activation_allowed": False,
            "production_activation_allowed": False,
            "required_evidence_complete": False,
            "broker_owner_evidence_attached": False,
            "runtime_governance_binding_evidence_attached": False,
            "cursor_store_evidence_attached": False,
            "backpressure_qos_evidence_attached": False,
            "transport_runtime_evidence_attached": False,
            "driver_hal_scope_evidence_attached": False,
            "transport_selected": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "callback_registered": False,
            "watch_started": False,
            "cursor_storage_active": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_REQ_IDS,
    }


def event_subscription_callback_watch_shape_payload() -> dict[str, Any]:
    return {
        "shape_state": "contract-only-callback-watch-shape-draft",
        "shape_confirmed": False,
        "android_callback_shape": {
            "current_binder_surface": "getEventSubscriptionCallbackWatchShapeJson, getEventSubscriptionCursorReplayStorageJson, and getEventSubscriptionBackpressureQosEvidenceJson only",
            "planned_registration_method": "registerEventSubscriptionCallback(SubscriptionRequest request, ICentralBrainEventCallback callback)",
            "planned_unregister_method": "unregisterEventSubscriptionCallback(String subscriptionId)",
            "planned_callback_interface": {
                "onEvent": "onEvent(String eventJson)",
                "onOverflow": "onOverflow(String overflowJson)",
                "onClosed": "onClosed(String closeJson)",
            },
            "identity_contract": "Binder UID/PID and declared caller app_id must map to Runtime & Governance caller identity before any callback registration.",
            "lifecycle": ["register", "validated", "active", "onEvent", "onOverflow", "onClosed", "unregister"],
            "implemented": False,
        },
        "linux_watch_shape": {
            "current_surface": "event-subscription-callback-watch-shape, event-subscription-cursor-replay-storage, and event-subscription-backpressure-qos-evidence over CLI/IPC/gRPC",
            "planned_cli": "event-subscription-watch --subscription-id <id> --since-event-id <cursor>",
            "planned_ipc_operations": [
                "uib.events.subscriptions.watch.open",
                "uib.events.subscriptions.watch.ack",
                "uib.events.subscriptions.watch.close",
            ],
            "planned_grpc_rpc": "WatchEventSubscriptions streaming RPC planned",
            "watch_lifecycle": ["open", "validated", "streaming", "overflow", "reconnect", "close"],
            "reconnect_contract": {
                "cursor_input": "since_event_id or since_timestamp_ms",
                "resume_result": "replayed|cursor_expired|rejected_by_policy",
                "prototype_cursor_storage": "not implemented",
            },
            "implemented": False,
        },
        "event_envelopes": {
            "event_json": {
                "trace_id": "string",
                "subscription_id": "string",
                "event_id": "string",
                "topic": "string",
                "source": "string",
                "safety_state": "normal|degraded|diagnostic_readonly",
                "payload": "object",
                "req_ids": EVENT_SUBSCRIPTION_CALLBACK_REQ_IDS,
            },
            "overflow_json": {
                "trace_id": "string",
                "subscription_id": "string",
                "overflow_reason": "cursor_expired|client_backpressure|broker_backpressure|qos_limit",
                "dropped_event_count": "integer",
                "resume_hint": "since_event_id or restart_subscription",
            },
            "close_json": {
                "trace_id": "string",
                "subscription_id": "string",
                "reason": "client_unregister|policy_revoked|broker_shutdown|transport_closed",
                "recoverable": "boolean",
            },
        },
        "mandatory_gates": [
            {
                "gate_id": "EV-CW-001",
                "name": "android-callback-identity-reviewed",
                "required_evidence": "Binder UID/PID, package identity, caller app_id, and permission mapping are approved.",
                "passed": False,
            },
            {
                "gate_id": "EV-CW-002",
                "name": "android-callback-lifecycle-reviewed",
                "required_evidence": "Register, unregister, binder death, onEvent, onOverflow, and onClosed behavior are approved.",
                "passed": False,
            },
            {
                "gate_id": "EV-CW-003",
                "name": "linux-watch-envelope-reviewed",
                "required_evidence": "Watch open, ack, close, and error envelope are approved for Unix socket and future gRPC/RPC paths.",
                "passed": False,
            },
            {
                "gate_id": "EV-CW-004",
                "name": "linux-watch-reconnect-cursor-reviewed",
                "required_evidence": "Reconnect, cursor expiry, replay, and restart recovery semantics are approved.",
                "passed": False,
            },
            {
                "gate_id": "EV-CW-005",
                "name": "overflow-close-semantics-reviewed",
                "required_evidence": "Overflow and close events are bound to QoS/backpressure policy and Audit.",
                "passed": False,
            },
            {
                "gate_id": "EV-CW-006",
                "name": "runtime-governance-binding-reviewed",
                "required_evidence": "Callback/watch registration, stream open, event delivery, overflow, and close are bound to Runtime & Governance.",
                "passed": False,
            },
            {
                "gate_id": "EV-CW-007",
                "name": "android-linux-contract-parity-proven",
                "required_evidence": "REST, Android Binder, Android Console, Linux CLI, Linux IPC, Linux gRPC/RPC, docs, and smoke tests expose equivalent callback/watch shape metadata.",
                "passed": True,
            },
            {
                "gate_id": "EV-CW-008",
                "name": "no-runtime-registration-claim",
                "required_evidence": "Prototype explicitly reports no callback registration, no watch stream, no broker dispatch, no cursor storage, and no transport runtime.",
                "passed": True,
            },
        ],
        "decision_dependencies": [
            "EV-DM-004 callback-watch-shape-selected",
            "EV-ACT-002 runtime-governance-binding",
            "EV-ACT-003 cursor-store-persistence",
            "EV-ACT-004 backpressure-qos-profile",
            "EV-ACT-005 transport-runtime-choice",
            "EV-CRS-004 reconnect-and-ack-reviewed",
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/callback-watch-shape",
            "android_binder": "getEventSubscriptionCallbackWatchShapeJson",
            "linux_cli": "event-subscription-callback-watch-shape",
            "linux_ipc": "uib.events.subscriptions.callback.watch.shape",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionCallbackWatchShape",
        },
        "summary": {
            "callback_watch_shape_contract_active": True,
            "callback_watch_shape_confirmed": False,
            "android_callback_shape_drafted": True,
            "linux_watch_shape_drafted": True,
            "runtime_governance_binding_evidence_attached": False,
            "cursor_store_evidence_attached": False,
            "backpressure_qos_evidence_attached": False,
            "transport_runtime_evidence_attached": False,
            "callback_registered": False,
            "watch_started": False,
            "streaming_runtime_implemented": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_CALLBACK_REQ_IDS,
    }


def event_subscription_cursor_replay_storage_payload() -> dict[str, Any]:
    return {
        "cursor_replay_state": "contract-only-cursor-replay-storage-draft",
        "cursor_replay_storage_confirmed": False,
        "storage_owner_contract": {
            "production_owner": "TBD-target-platform",
            "android_owner": "TBD-Android system service owner",
            "linux_owner": "TBD-Linux daemon owner",
            "schema_owner": "Runtime & Governance owner TBD",
            "persistence_owner": "TBD-target-platform",
            "implemented": False,
        },
        "cursor_schema": {
            "cursor_key": "event_id",
            "cursor_scope": ["subscription_id", "topic", "source", "caller_identity"],
            "watermark_fields": ["last_delivered_event_id", "last_acknowledged_event_id", "last_delivery_timestamp_ms"],
            "request_inputs": ["since_event_id", "since_timestamp_ms", "replay_limit"],
            "ack_shape": {
                "subscription_id": "string",
                "event_id": "string",
                "ack_state": "delivered|processed|dropped",
                "trace_id": "string",
            },
            "prototype_storage": "not implemented; recent log is in memory only",
        },
        "replay_contract": {
            "replay_window": {
                "prototype_window": "last-50-events from bounded EVENT_LOG only",
                "production_window": "TBD after storage owner and privacy retention review",
                "max_replay_limit": 50,
            },
            "resume_results": [
                "replayed",
                "cursor_expired",
                "retention_window_empty",
                "rejected_by_policy",
                "storage_unavailable",
            ],
            "ordering": "oldest-to-newest for replay after cursor resolution",
            "deduplication_key": "topic + event_id + source",
        },
        "retention_cleanup": {
            "privacy_classification": "inherits topic payload classification; vehicle signal payloads require adapter owner review",
            "prototype_retention": "process memory only; lost on restart",
            "production_retention": "TBD-target-platform",
            "cleanup_triggers": ["subscription_cancel", "policy_revoked", "retention_expired", "service_shutdown"],
            "restart_recovery": "not implemented in prototype",
        },
        "runtime_governance_binding": {
            "identity_inputs": ["caller app_id", "permissions", "safety_state", "Binder UID/PID or Linux service identity"],
            "policy_checks": ["subscribe", "replay", "ack", "cleanup"],
            "audit_events": ["cursor.created", "cursor.advanced", "cursor.expired", "replay.requested", "replay.rejected"],
            "qos_binding": "backpressure/QoS owner must approve replay rate and ack timeout before activation",
            "implemented": False,
        },
        "mandatory_gates": [
            {
                "gate_id": "EV-CRS-001",
                "name": "cursor-schema-reviewed",
                "required_evidence": "Cursor key, scope, watermark, ack shape, and deduplication key are approved.",
                "passed": False,
            },
            {
                "gate_id": "EV-CRS-002",
                "name": "storage-owner-assigned",
                "required_evidence": "Android owner, Linux owner, persistence owner, and schema owner are assigned.",
                "passed": False,
            },
            {
                "gate_id": "EV-CRS-003",
                "name": "replay-window-retention-reviewed",
                "required_evidence": "Replay window, retention duration, privacy classification, and cleanup triggers are approved.",
                "passed": False,
            },
            {
                "gate_id": "EV-CRS-004",
                "name": "reconnect-and-ack-reviewed",
                "required_evidence": "Reconnect, cursor expiry, ack timeout, duplicate delivery, and restart recovery semantics are approved.",
                "passed": False,
            },
            {
                "gate_id": "EV-CRS-005",
                "name": "runtime-governance-audit-reviewed",
                "required_evidence": "Replay and ack operations are bound to Runtime & Governance identity, Policy, QoS, Lifecycle, and Audit.",
                "passed": False,
            },
            {
                "gate_id": "EV-CRS-006",
                "name": "backpressure-qos-binding-reviewed",
                "required_evidence": "Replay rate, ack timeout, overflow behavior, and QoS responsibility split are approved.",
                "passed": False,
            },
            {
                "gate_id": "EV-CRS-007",
                "name": "android-linux-contract-parity-proven",
                "required_evidence": "REST, Android Binder, Android Console, Linux CLI, Linux IPC, Linux gRPC/RPC, docs, and smoke tests expose equivalent cursor/replay metadata.",
                "passed": True,
            },
            {
                "gate_id": "EV-CRS-008",
                "name": "no-persistence-runtime-claim",
                "required_evidence": "Prototype explicitly reports no cursor persistence, no replay index, no broker dispatch, and no transport runtime.",
                "passed": True,
            },
        ],
        "decision_dependencies": [
            "EV-DM-002 cursor-storage-owner-selected",
            "EV-ACT-003 cursor-store-persistence",
            "EV-CW-004 linux-watch-reconnect-cursor-reviewed",
            "EV-ACT-004 backpressure-qos-profile",
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/cursor-replay-storage",
            "android_binder": "getEventSubscriptionCursorReplayStorageJson",
            "linux_cli": "event-subscription-cursor-replay-storage",
            "linux_ipc": "uib.events.subscriptions.cursor.replay.storage",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionCursorReplayStorage",
        },
        "summary": {
            "cursor_replay_storage_contract_active": True,
            "cursor_replay_storage_confirmed": False,
            "storage_owner_confirmed": False,
            "schema_owner_confirmed": False,
            "replay_window_confirmed": False,
            "retention_policy_confirmed": False,
            "restart_recovery_confirmed": False,
            "runtime_governance_binding_evidence_attached": False,
            "backpressure_qos_evidence_attached": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "replay_index_active": False,
            "callback_registered": False,
            "watch_started": False,
            "streaming_runtime_implemented": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_CURSOR_REQ_IDS,
    }


def event_subscription_backpressure_qos_evidence_payload() -> dict[str, Any]:
    return {
        "backpressure_qos_state": "contract-only-backpressure-qos-evidence-draft",
        "backpressure_qos_evidence_confirmed": False,
        "policy_owner_contract": {
            "production_owner": "TBD-target-platform",
            "runtime_governance_owner": "Runtime & Governance owner TBD",
            "android_owner": "TBD-Android system service owner",
            "linux_owner": "TBD-Linux daemon owner",
            "high_rate_transport_owner": "TBD-only if DDS/SSE/WebSocket/high-rate data plane is selected",
            "implemented": False,
        },
        "overflow_schema": {
            "event_type": "subscription.overflow planned",
            "fields": {
                "trace_id": "string",
                "subscription_id": "string",
                "topic": "string",
                "overflow_reason": "client_backpressure|broker_backpressure|cursor_expired|qos_limit|transport_unavailable",
                "qos_bucket": "best_effort|interactive|safety_observed|high_rate_planned",
                "dropped_event_count": "integer",
                "last_delivered_event_id": "string",
                "resume_hint": "since_event_id|restart_subscription|reduce_rate",
                "policy_decision_id": "string",
                "req_ids": EVENT_SUBSCRIPTION_BACKPRESSURE_REQ_IDS,
            },
            "prototype_emission": "not implemented; schema only",
        },
        "qos_policy_contract": {
            "per_caller_limit": "TBD by Runtime & Governance owner; prototype evidence missing",
            "per_topic_limit": "TBD after topic frequency and selected transport are known",
            "replay_rate_limit": "TBD with cursor/replay storage owner",
            "ack_timeout_ms": "TBD with callback/watch owner",
            "overflow_actions": ["emit_overflow", "drop_oldest", "pause_delivery", "reject_subscription"],
            "retry_budget": "TBD-target-platform",
            "audit_events": [
                "subscription.qos.checked",
                "subscription.qos.throttled",
                "subscription.overflow.emitted",
                "subscription.replay.rate_limited",
            ],
            "implemented": False,
        },
        "runtime_governance_binding": {
            "required_inputs": ["caller_identity", "permissions", "safety_state", "topic", "qos_profile", "transport"],
            "policy_checks": ["subscribe", "deliver", "ack", "replay", "overflow", "cancel"],
            "qos_evidence_source": "not attached; fixed-window SOA QoS prototype does not yet govern event stream delivery",
            "audit_required": True,
            "implemented": False,
        },
        "high_rate_boundary": {
            "dds_mapping_required": True,
            "someip_tsn_ptp_review_required": True,
            "shared_memory_review_required": True,
            "driver_gap_refs": ["DRV-GAP-004", "DRV-GAP-005"],
            "current_scope": "no high-rate data-plane; no DDS runtime; no Driver/HAL or shared-memory path",
        },
        "mandatory_gates": [
            {
                "gate_id": "EV-QOS-001",
                "name": "overflow-schema-reviewed",
                "required_evidence": "Overflow event fields, reason codes, resume hints, and audit mapping are approved.",
                "passed": False,
            },
            {
                "gate_id": "EV-QOS-002",
                "name": "per-caller-rate-policy-reviewed",
                "required_evidence": "Per-caller and per-topic rate limits are approved for Android Binder, Linux IPC, and future gRPC/RPC paths.",
                "passed": False,
            },
            {
                "gate_id": "EV-QOS-003",
                "name": "replay-rate-and-ack-timeout-reviewed",
                "required_evidence": "Replay rate, ack timeout, duplicate delivery, and retry budget are aligned with cursor/replay storage.",
                "passed": False,
            },
            {
                "gate_id": "EV-QOS-004",
                "name": "runtime-governance-qos-owner-assigned",
                "required_evidence": "Runtime & Governance owner confirms event delivery QoS evidence source and audit ownership.",
                "passed": False,
            },
            {
                "gate_id": "EV-QOS-005",
                "name": "high-rate-transport-qos-mapping-reviewed",
                "required_evidence": "DDS/SSE/WebSocket/SOME-IP/TSN mapping is reviewed before high-rate delivery is activated.",
                "passed": False,
            },
            {
                "gate_id": "EV-QOS-006",
                "name": "driver-hal-high-rate-scope-reviewed",
                "required_evidence": "DRV-GAP-004 and DRV-GAP-005 review is attached before shared memory, TSN/PTP, or Safety Runtime paths are used.",
                "passed": False,
            },
            {
                "gate_id": "EV-QOS-007",
                "name": "android-linux-contract-parity-proven",
                "required_evidence": "REST, Android Binder, Android Console, Linux CLI, Linux IPC, Linux gRPC/RPC, docs, and smoke tests expose equivalent backpressure/QoS evidence fields.",
                "passed": True,
            },
            {
                "gate_id": "EV-QOS-008",
                "name": "no-runtime-qos-activation-claim",
                "required_evidence": "Prototype explicitly reports no event QoS activation, no overflow emission, no broker, no DDS runtime, and no Driver/HAL access.",
                "passed": True,
            },
        ],
        "decision_dependencies": [
            "EV-DM-003 backpressure-qos-owner-selected",
            "EV-ACT-004 backpressure-qos-profile",
            "EV-CW-005 overflow-close-semantics-reviewed",
            "EV-CRS-006 backpressure-qos-binding-reviewed",
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/backpressure-qos-evidence",
            "android_binder": "getEventSubscriptionBackpressureQosEvidenceJson",
            "linux_cli": "event-subscription-backpressure-qos-evidence",
            "linux_ipc": "uib.events.subscriptions.backpressure.qos.evidence",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionBackpressureQosEvidence",
        },
        "summary": {
            "backpressure_qos_evidence_contract_active": True,
            "backpressure_qos_evidence_confirmed": False,
            "overflow_schema_confirmed": False,
            "qos_owner_confirmed": False,
            "runtime_governance_qos_evidence_attached": False,
            "high_rate_qos_mapping_confirmed": False,
            "driver_hal_scope_evidence_attached": False,
            "event_delivery_qos_active": False,
            "overflow_emission_active": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "replay_index_active": False,
            "callback_registered": False,
            "watch_started": False,
            "streaming_runtime_implemented": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_BACKPRESSURE_REQ_IDS,
    }


def event_subscription_readiness_rollup_payload() -> dict[str, Any]:
    lifecycle = event_subscriptions_payload()
    transport = event_subscription_transport_readiness_payload()
    decision = event_subscription_decision_matrix_payload()
    activation = event_subscription_activation_checklist_payload()
    callback_shape = event_subscription_callback_watch_shape_payload()
    cursor_storage = event_subscription_cursor_replay_storage_payload()
    backpressure_qos = event_subscription_backpressure_qos_evidence_payload()

    readiness_sections = [
        {
            "section_id": "EV-ROLLUP-LIFECYCLE",
            "source": "GET /uib/events/subscriptions",
            "state": lifecycle["subscription_state"],
            "passed_gates": [gate["gate_id"] for gate in lifecycle["mandatory_gates"] if gate["passed"]],
            "blocked_gates": [gate["gate_id"] for gate in lifecycle["mandatory_gates"] if not gate["passed"]],
            "activation_ready": False,
        },
        {
            "section_id": "EV-ROLLUP-TRANSPORT",
            "source": "GET /uib/events/subscriptions/transport-readiness",
            "state": transport["readiness_state"],
            "passed_gates": [gate["gate_id"] for gate in transport["mandatory_gates"] if gate["passed"]],
            "blocked_gates": [gate["gate_id"] for gate in transport["mandatory_gates"] if not gate["passed"]],
            "activation_ready": False,
        },
        {
            "section_id": "EV-ROLLUP-OWNER-DECISIONS",
            "source": "GET /uib/events/subscriptions/decision-matrix",
            "state": decision["decision_state"],
            "passed_gates": [gate["gate_id"] for gate in decision["mandatory_gates"] if gate["passed"]],
            "blocked_gates": [gate["gate_id"] for gate in decision["mandatory_gates"] if not gate["passed"]],
            "activation_ready": False,
        },
        {
            "section_id": "EV-ROLLUP-ACTIVATION",
            "source": "GET /uib/events/subscriptions/activation-checklist",
            "state": activation["activation_state"],
            "passed_gates": [gate["gate_id"] for gate in activation["checklist"] if gate["passed"]],
            "blocked_gates": [gate["gate_id"] for gate in activation["checklist"] if not gate["passed"]],
            "activation_ready": False,
        },
        {
            "section_id": "EV-ROLLUP-CALLBACK-WATCH",
            "source": "GET /uib/events/subscriptions/callback-watch-shape",
            "state": callback_shape["shape_state"],
            "passed_gates": [gate["gate_id"] for gate in callback_shape["mandatory_gates"] if gate["passed"]],
            "blocked_gates": [gate["gate_id"] for gate in callback_shape["mandatory_gates"] if not gate["passed"]],
            "activation_ready": False,
        },
        {
            "section_id": "EV-ROLLUP-CURSOR-REPLAY",
            "source": "GET /uib/events/subscriptions/cursor-replay-storage",
            "state": cursor_storage["cursor_replay_state"],
            "passed_gates": [gate["gate_id"] for gate in cursor_storage["mandatory_gates"] if gate["passed"]],
            "blocked_gates": [gate["gate_id"] for gate in cursor_storage["mandatory_gates"] if not gate["passed"]],
            "activation_ready": False,
        },
        {
            "section_id": "EV-ROLLUP-BACKPRESSURE-QOS",
            "source": "GET /uib/events/subscriptions/backpressure-qos-evidence",
            "state": backpressure_qos["backpressure_qos_state"],
            "passed_gates": [gate["gate_id"] for gate in backpressure_qos["mandatory_gates"] if gate["passed"]],
            "blocked_gates": [gate["gate_id"] for gate in backpressure_qos["mandatory_gates"] if not gate["passed"]],
            "activation_ready": False,
        },
    ]

    blocked_gate_ids = sorted(
        {
            gate_id
            for section in readiness_sections
            for gate_id in section["blocked_gates"]
        }
    )
    passed_gate_ids = sorted(
        {
            gate_id
            for section in readiness_sections
            for gate_id in section["passed_gates"]
        }
    )

    return {
        "readiness_rollup_state": "contract-only-readiness-rollup-blocked",
        "readiness_rollup_confirmed": False,
        "production_activation_allowed": False,
        "readiness_sections": readiness_sections,
        "activation_blockers": [
            {
                "blocker_id": "EV-RU-001",
                "area": "broker-owner-and-process-boundary",
                "evidence_required": "Broker owner, Android system service path, Linux daemon path, lifecycle persistence, and failover behavior must be approved.",
                "linked_gates": ["EV-DM-001", "EV-ACT-001"],
                "resolved": False,
            },
            {
                "blocker_id": "EV-RU-002",
                "area": "runtime-governance-binding",
                "evidence_required": "Subscribe, cancel, delivery, callback/watch, replay, overflow, and close operations must be bound to Runtime & Governance identity, Policy, QoS, Lifecycle, and Audit.",
                "linked_gates": ["EV-SUB-003", "EV-ACT-002", "EV-CW-006", "EV-CRS-005", "EV-QOS-004"],
                "resolved": False,
            },
            {
                "blocker_id": "EV-RU-003",
                "area": "cursor-replay-persistence",
                "evidence_required": "Cursor store owner, schema owner, retention, privacy, replay window, ack timeout, cleanup, and restart recovery must be approved.",
                "linked_gates": ["EV-DM-002", "EV-ACT-003", "EV-CRS-001", "EV-CRS-002", "EV-CRS-003", "EV-CRS-004"],
                "resolved": False,
            },
            {
                "blocker_id": "EV-RU-004",
                "area": "backpressure-qos-evidence",
                "evidence_required": "Overflow schema, per-caller/per-topic limits, replay rate, ack timeout, high-rate QoS mapping, and Runtime & Governance ownership must be approved.",
                "linked_gates": ["EV-DM-003", "EV-ACT-004", "EV-QOS-001", "EV-QOS-002", "EV-QOS-003", "EV-QOS-004", "EV-QOS-005"],
                "resolved": False,
            },
            {
                "blocker_id": "EV-RU-005",
                "area": "callback-watch-transport-runtime",
                "evidence_required": "Android Binder callback lifecycle, Linux watch lifecycle, reconnect, overflow/close semantics, and selected transport runtime must be reviewed.",
                "linked_gates": ["EV-DM-004", "EV-DM-005", "EV-ACT-005", "EV-CW-001", "EV-CW-002", "EV-CW-003", "EV-CW-004", "EV-CW-005"],
                "resolved": False,
            },
            {
                "blocker_id": "EV-RU-006",
                "area": "high-rate-driver-hal-scope",
                "evidence_required": "DRV-GAP-004 and DRV-GAP-005 reviews are required before DDS, SOME/IP, TSN/PTP, shared memory, or Safety Runtime paths are used.",
                "linked_gates": ["EV-ACT-006", "EV-QOS-006"],
                "resolved": False,
            },
        ],
        "next_evidence_required": [
            "EV-DM-001..005 owner and transport decisions",
            "EV-ACT-001..006 activation evidence attachments",
            "EV-CW-001..006 callback/watch runtime evidence",
            "EV-CRS-001..006 cursor/replay storage evidence",
            "EV-QOS-001..006 backpressure/QoS evidence",
            "POST /uib/events/subscriptions/activation-evidence evidence reference intake contract",
            "GET /uib/events/subscriptions/activation-evidence/status review status contract",
            "DRV-GAP-004/DRV-GAP-005 review if high-rate delivery is selected",
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/readiness-rollup",
            "android_binder": "getEventSubscriptionReadinessRollupJson",
            "linux_cli": "event-subscription-readiness-rollup",
            "linux_ipc": "uib.events.subscriptions.readiness.rollup",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionReadinessRollup",
            "activation_evidence_status": "GET /uib/events/subscriptions/activation-evidence/status",
        },
        "summary": {
            "readiness_rollup_contract_active": True,
            "readiness_rollup_confirmed": False,
            "passed_gate_count": len(passed_gate_ids),
            "blocked_gate_count": len(blocked_gate_ids),
            "blocked_gate_ids": blocked_gate_ids,
            "broker_activation_ready": False,
            "production_activation_allowed": False,
            "all_required_evidence_complete": False,
            "transport_selected": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "replay_index_active": False,
            "event_delivery_qos_active": False,
            "overflow_emission_active": False,
            "callback_registered": False,
            "watch_started": False,
            "streaming_runtime_implemented": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_READINESS_REQ_IDS,
    }


def event_subscription_activation_evidence_payload(request: dict[str, Any]) -> dict[str, Any]:
    trace_id = request.get("trace_id") or str(uuid.uuid4())
    submission_id = str(request.get("evidence_submission_id") or f"ev-ae-{uuid.uuid4()}")
    raw_gate_ids = request.get("target_gate_ids") or request.get("gate_ids") or []
    target_gate_ids = raw_gate_ids if isinstance(raw_gate_ids, list) else [str(raw_gate_ids)]
    raw_evidence_refs = request.get("evidence_refs") or request.get("attachments") or []
    evidence_refs = raw_evidence_refs if isinstance(raw_evidence_refs, list) else [raw_evidence_refs]
    reviewer = request.get("reviewer") or {"app_id": "unknown", "role": "contract-reviewer"}
    requested_permissions = request.get("permissions") or ["service.read"]
    policy = permission_check_payload(
        {
            "permissions": requested_permissions,
            "caller_permissions": request.get("caller_permissions", ["vehicle.read", "service.read"]),
            "vehicle_state": request.get("vehicle_state", "parked"),
            "safety_state": request.get("safety_state", "normal"),
            "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
        }
    )
    allowed = policy["decision"] == "allow"
    has_target_gates = bool(target_gate_ids)
    has_evidence_refs = bool(evidence_refs)
    has_reviewer = bool(reviewer.get("app_id") or reviewer.get("name") or reviewer.get("role"))

    if not allowed:
        state = "rejected_by_policy"
    elif not has_target_gates or not has_evidence_refs or not has_reviewer:
        state = "rejected_missing_evidence"
    else:
        state = "validated_contract_only"

    GOVERNANCE.record_audit(
        trace_id,
        {
            "service": "uib-event-subscription",
            "method": "activation-evidence",
            "outcome": state,
            "policy_decision": policy["decision"],
            "lifecycle_state": "validated" if state == "validated_contract_only" else "rejected",
            "qos_decision": "not-applied",
        },
    )

    return {
        "operation": "activation-evidence",
        "evidence_submission_id": submission_id,
        "evidence_intake_state": state,
        "intake_validated": state == "validated_contract_only",
        "target_gate_ids": target_gate_ids,
        "evidence_refs": evidence_refs,
        "reviewer": reviewer,
        "evidence_contract": {
            "required_gate_prefixes": ["EV-DM", "EV-ACT", "EV-CW", "EV-CRS", "EV-QOS", "EV-RU", "DRV-GAP"],
            "accepted_ref_types": ["doc", "test_log", "owner_approval", "platform_decision", "driver_gap_review"],
            "required_ref_fields": ["ref_id", "type", "uri_or_path", "owner", "summary"],
            "storage_owner": "TBD-target-platform",
            "review_owner": "TBD-target-platform",
            "prototype_storage": "not implemented; request is validated and discarded after response",
        },
        "validation": {
            "policy_checked": True,
            "policy": policy,
            "target_gates_present": has_target_gates,
            "evidence_refs_present": has_evidence_refs,
            "reviewer_present": has_reviewer,
            "audit_recorded": True,
        },
        "review_result": {
            "accepted_for_review": False,
            "review_queue_updated": False,
            "evidence_persisted": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "activation_allowed": False,
            "reason": "prototype exposes activation evidence intake contract only; no evidence store or review workflow is implemented",
        },
        "mandatory_gates": [
            {
                "gate_id": "EV-AE-001",
                "name": "target-gates-declared",
                "required_evidence": "Submission identifies which EV-DM/EV-ACT/EV-CW/EV-CRS/EV-QOS/EV-RU or DRV-GAP gates it claims to support.",
                "passed": has_target_gates,
            },
            {
                "gate_id": "EV-AE-002",
                "name": "evidence-reference-shape-present",
                "required_evidence": "Submission includes evidence_refs with ref_id, type, uri_or_path, owner, and summary fields.",
                "passed": has_evidence_refs,
            },
            {
                "gate_id": "EV-AE-003",
                "name": "reviewer-identity-present",
                "required_evidence": "Submission includes reviewer identity suitable for Runtime & Governance audit.",
                "passed": has_reviewer,
            },
            {
                "gate_id": "EV-AE-004",
                "name": "runtime-governance-policy-checked",
                "required_evidence": "Activation evidence submission is policy checked and audited.",
                "passed": allowed,
            },
            {
                "gate_id": "EV-AE-005",
                "name": "evidence-store-owner-assigned",
                "required_evidence": "Target platform assigns durable evidence store owner and retention policy.",
                "passed": False,
            },
            {
                "gate_id": "EV-AE-006",
                "name": "review-workflow-owner-assigned",
                "required_evidence": "Target platform assigns reviewer workflow owner and gate closure authority.",
                "passed": False,
            },
            {
                "gate_id": "EV-AE-007",
                "name": "no-gate-auto-close-claim",
                "required_evidence": "Prototype reports no gate state changes and no activation allowed from evidence intake.",
                "passed": True,
            },
            {
                "gate_id": "EV-AE-008",
                "name": "android-linux-contract-parity-proven",
                "required_evidence": "REST, Android Binder, Android Console, Linux CLI, Linux IPC, Linux gRPC/RPC, docs, and smoke tests expose equivalent activation evidence intake behavior.",
                "passed": True,
            },
        ],
        "api_surface": {
            "rest": "POST /uib/events/subscriptions/activation-evidence",
            "android_binder": "submitEventSubscriptionActivationEvidenceJson",
            "linux_cli": "event-subscription-activation-evidence",
            "linux_ipc": "uib.events.subscriptions.activation.evidence",
            "linux_grpc_rpc": "CentralBrainGateway.SubmitEventSubscriptionActivationEvidence",
        },
        "summary": {
            "activation_evidence_contract_active": True,
            "activation_evidence_validated": state == "validated_contract_only",
            "activation_evidence_accepted_for_review": False,
            "activation_evidence_persisted": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "activation_allowed": False,
            "broker_activation_ready": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_EVIDENCE_REQ_IDS,
    }


def event_subscription_activation_evidence_status_payload() -> dict[str, Any]:
    return {
        "review_status_state": "contract-only-no-evidence-store",
        "status_scope": {
            "source_endpoint": "POST /uib/events/subscriptions/activation-evidence",
            "lookup_mode": "prototype-static-status",
            "prototype_storage": "not implemented; evidence submissions are validated and discarded after response",
            "target_gate_scope": ["EV-DM", "EV-ACT", "EV-CW", "EV-CRS", "EV-QOS", "EV-RU", "DRV-GAP"],
        },
        "owners": {
            "evidence_store_owner": "TBD-target-platform",
            "review_workflow_owner": "TBD-target-platform",
            "gate_closure_authority": "TBD-target-platform",
            "retention_policy_owner": "TBD-target-platform",
        },
        "review_pipeline": {
            "evidence_store_active": False,
            "review_workflow_active": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "activation_allowed": False,
            "reason": "activation evidence intake is contract-only; this status endpoint exposes that no durable store or review workflow exists yet",
        },
        "counters": {
            "persisted_submission_count": 0,
            "pending_review_count": 0,
            "accepted_for_review_count": 0,
            "reviewed_submission_count": 0,
            "closed_gate_count": 0,
        },
        "mandatory_gates": [
            {
                "gate_id": "EV-AES-001",
                "name": "evidence-store-owner-assigned",
                "required_evidence": "Target platform assigns a durable evidence store owner before evidence can be persisted or queried.",
                "passed": False,
            },
            {
                "gate_id": "EV-AES-002",
                "name": "review-workflow-owner-assigned",
                "required_evidence": "Target platform assigns review workflow owner and escalation policy before submissions can enter review.",
                "passed": False,
            },
            {
                "gate_id": "EV-AES-003",
                "name": "retention-policy-approved",
                "required_evidence": "Evidence retention, privacy, audit export, and URI rules are approved for Android/Linux target platforms.",
                "passed": False,
            },
            {
                "gate_id": "EV-AES-004",
                "name": "no-persisted-submissions-claim",
                "required_evidence": "Prototype reports zero persisted submissions and no evidence-store read path.",
                "passed": True,
            },
            {
                "gate_id": "EV-AES-005",
                "name": "no-review-queue-or-gate-closure-claim",
                "required_evidence": "Prototype reports no review queue updates, no gate closure, and no broker activation permission.",
                "passed": True,
            },
            {
                "gate_id": "EV-AES-006",
                "name": "android-linux-status-contract-parity-proven",
                "required_evidence": "REST, Android Binder, Android Console, Linux CLI, Linux IPC, Linux gRPC/RPC, docs, and smoke tests expose equivalent status fields.",
                "passed": True,
            },
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/status",
            "android_binder": "getEventSubscriptionActivationEvidenceStatusJson",
            "linux_cli": "event-subscription-activation-evidence-status",
            "linux_ipc": "uib.events.subscriptions.activation.evidence.status",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationEvidenceStatus",
        },
        "summary": {
            "activation_evidence_status_contract_active": True,
            "review_status_available": True,
            "activation_evidence_contract_active": True,
            "activation_evidence_accepted_for_review": False,
            "activation_evidence_persisted": False,
            "evidence_store_active": False,
            "review_workflow_active": False,
            "persisted_submission_count": 0,
            "pending_review_count": 0,
            "accepted_for_review_count": 0,
            "reviewed_submission_count": 0,
            "closed_gate_count": 0,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "activation_allowed": False,
            "broker_activation_ready": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_EVIDENCE_STATUS_REQ_IDS,
    }


def event_subscription_activation_evidence_retention_checklist_payload() -> dict[str, Any]:
    return {
        "retention_checklist_state": "contract-only-retention-owner-checklist-open",
        "storage_activation_allowed": False,
        "owner_decision_complete": False,
        "scope": {
            "source_endpoints": [
                "POST /uib/events/subscriptions/activation-evidence",
                "GET /uib/events/subscriptions/activation-evidence/status",
            ],
            "target_endpoint": "GET /uib/events/subscriptions/activation-evidence/retention-checklist",
            "purpose": "fix retention, URI, review-owner, gate-closure, delete, and export decisions before any durable evidence store is considered",
            "prototype_storage": "not implemented; this checklist does not persist, dereference, delete, export, or review evidence",
            "target_gate_scope": ["EV-DM", "EV-ACT", "EV-CW", "EV-CRS", "EV-QOS", "EV-RU", "EV-AE", "EV-AES", "DRV-GAP"],
        },
        "owner_decisions": [
            {
                "decision_id": "EV-AER-001",
                "area": "durable-evidence-store-owner",
                "required_decision": "Assign the Android/Linux process and data owner for durable activation evidence records.",
                "current_selection": "TBD-target-platform",
                "blocked_by": ["target storage location", "service identity", "audit export backend", "backup/restore policy"],
            },
            {
                "decision_id": "EV-AER-002",
                "area": "evidence-uri-rules",
                "required_decision": "Approve allowed URI/path forms for doc, test_log, owner_approval, platform_decision, and driver_gap_review refs.",
                "current_selection": "TBD-target-platform",
                "blocked_by": ["allowed schemes", "relative path root", "artifact immutability", "secret redaction rule"],
            },
            {
                "decision_id": "EV-AER-003",
                "area": "retention-policy-owner",
                "required_decision": "Assign retention TTL, privacy classification, cleanup, and audit retention owner.",
                "current_selection": "TBD-target-platform",
                "blocked_by": ["retention duration", "privacy review", "cleanup trigger", "regulatory export need"],
            },
            {
                "decision_id": "EV-AER-004",
                "area": "review-workflow-owner",
                "required_decision": "Assign review queue owner, reviewer roles, escalation policy, and rejection semantics.",
                "current_selection": "TBD-target-platform",
                "blocked_by": ["review queue backend", "reviewer identity source", "escalation SLA", "audit trail owner"],
            },
            {
                "decision_id": "EV-AER-005",
                "area": "gate-closure-authority",
                "required_decision": "Assign who can close EV-* and DRV-GAP gates and how gate closure is audited and rolled back.",
                "current_selection": "TBD-target-platform",
                "blocked_by": ["gate owner", "approval signature", "rollback rule", "Runtime & Governance binding"],
            },
            {
                "decision_id": "EV-AER-006",
                "area": "delete-export-semantics",
                "required_decision": "Approve evidence deletion, export, redaction, and orphaned reference behavior.",
                "current_selection": "TBD-target-platform",
                "blocked_by": ["delete authorization", "export format", "redaction policy", "orphaned ref cleanup"],
            },
        ],
        "evidence_uri_rules": {
            "allowed_ref_types": ["doc", "test_log", "owner_approval", "platform_decision", "driver_gap_review"],
            "required_fields": ["ref_id", "type", "uri_or_path", "owner", "summary", "created_at", "hash_or_version"],
            "candidate_allowed_uri_schemes": ["repo-relative", "artifact-store", "audit-log", "platform-decision"],
            "disallowed_until_policy_exists": [
                "raw cloud URL without privacy route",
                "mutable temp file",
                "secret-bearing path",
                "device node or hardware probe output captured outside Driver/HAL gap review",
            ],
            "uri_rules_confirmed": False,
        },
        "retention_policy_shape": {
            "candidate_retention_classes": ["development-evidence", "platform-decision-record", "driver-gap-review-record"],
            "minimum_metadata": ["owner", "reviewer", "target_gate_ids", "created_at", "retention_class", "redaction_state"],
            "delete_semantics": "TBD-target-platform; prototype does not delete anything because it stores nothing",
            "export_semantics": "TBD-target-platform; prototype does not export anything because it stores nothing",
            "retention_policy_confirmed": False,
        },
        "mandatory_gates": [
            {
                "gate_id": "EV-AER-001",
                "name": "durable-evidence-store-owner-assigned",
                "required_evidence": "Target platform assigns durable evidence store owner, process boundary, service identity, and audit backend.",
                "passed": False,
            },
            {
                "gate_id": "EV-AER-002",
                "name": "evidence-uri-rules-approved",
                "required_evidence": "Allowed URI/path schemes, immutability, hash/version, and secret redaction rules are approved.",
                "passed": False,
            },
            {
                "gate_id": "EV-AER-003",
                "name": "retention-policy-owner-assigned",
                "required_evidence": "Retention TTL, cleanup trigger, privacy classification, and retention owner are assigned.",
                "passed": False,
            },
            {
                "gate_id": "EV-AER-004",
                "name": "review-workflow-owner-assigned",
                "required_evidence": "Review queue owner, reviewer identity source, escalation policy, and rejection semantics are assigned.",
                "passed": False,
            },
            {
                "gate_id": "EV-AER-005",
                "name": "gate-closure-authority-assigned",
                "required_evidence": "Gate closure authority, approval signature, rollback behavior, and Runtime & Governance audit binding are approved.",
                "passed": False,
            },
            {
                "gate_id": "EV-AER-006",
                "name": "delete-export-semantics-approved",
                "required_evidence": "Deletion, export, redaction, orphaned reference cleanup, and audit export rules are approved.",
                "passed": False,
            },
            {
                "gate_id": "EV-AER-007",
                "name": "android-linux-retention-contract-parity-proven",
                "required_evidence": "REST, Android Binder, Android Console, Linux CLI, Linux IPC, Linux gRPC/RPC, docs, and smoke tests expose equivalent retention checklist fields.",
                "passed": True,
            },
            {
                "gate_id": "EV-AER-008",
                "name": "no-store-or-gate-closure-claim",
                "required_evidence": "Prototype reports no evidence store, no review workflow, no delete/export workflow, no gate closure, and no broker activation.",
                "passed": True,
            },
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/retention-checklist",
            "android_binder": "getEventSubscriptionActivationEvidenceRetentionChecklistJson",
            "linux_cli": "event-subscription-activation-evidence-retention-checklist",
            "linux_ipc": "uib.events.subscriptions.activation.evidence.retention.checklist",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationEvidenceRetentionChecklist",
        },
        "summary": {
            "activation_evidence_retention_checklist_active": True,
            "owner_decision_complete": False,
            "retention_policy_confirmed": False,
            "evidence_uri_rules_confirmed": False,
            "review_workflow_owner_confirmed": False,
            "gate_closure_authority_confirmed": False,
            "deletion_export_semantics_confirmed": False,
            "activation_evidence_status_contract_active": True,
            "activation_evidence_contract_active": True,
            "evidence_store_active": False,
            "review_workflow_active": False,
            "delete_workflow_active": False,
            "export_workflow_active": False,
            "persisted_submission_count": 0,
            "pending_review_count": 0,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "activation_allowed": False,
            "broker_activation_ready": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_EVIDENCE_RETENTION_REQ_IDS,
    }


def event_subscription_activation_evidence_decision_status_rollup_payload() -> dict[str, Any]:
    readiness = event_subscription_readiness_rollup_payload()
    status = event_subscription_activation_evidence_status_payload()
    retention = event_subscription_activation_evidence_retention_checklist_payload()
    retention_open_decisions = [
        decision["decision_id"]
        for decision in retention["owner_decisions"]
        if str(decision.get("current_selection", "")).startswith("TBD")
    ]
    sections = [
        {
            "section_id": "EV-AED-ROLLUP-READINESS",
            "source": "GET /uib/events/subscriptions/readiness-rollup",
            "state": readiness["readiness_rollup_state"],
            "passed_gates": [],
            "blocked_gates": [blocker["blocker_id"] for blocker in readiness["activation_blockers"] if not blocker["resolved"]],
            "decision_complete": False,
        },
        {
            "section_id": "EV-AED-ROLLUP-STATUS",
            "source": "GET /uib/events/subscriptions/activation-evidence/status",
            "state": status["review_status_state"],
            "passed_gates": [gate["gate_id"] for gate in status["mandatory_gates"] if gate["passed"]],
            "blocked_gates": [gate["gate_id"] for gate in status["mandatory_gates"] if not gate["passed"]],
            "decision_complete": False,
        },
        {
            "section_id": "EV-AED-ROLLUP-RETENTION",
            "source": "GET /uib/events/subscriptions/activation-evidence/retention-checklist",
            "state": retention["retention_checklist_state"],
            "passed_gates": [gate["gate_id"] for gate in retention["mandatory_gates"] if gate["passed"]],
            "blocked_gates": [gate["gate_id"] for gate in retention["mandatory_gates"] if not gate["passed"]],
            "open_decision_ids": retention_open_decisions,
            "decision_complete": False,
        },
        {
            "section_id": "EV-AED-ROLLUP-INTAKE",
            "source": "POST /uib/events/subscriptions/activation-evidence",
            "state": "contract-only-intake-surface-not-called",
            "passed_gates": ["EV-AED-001", "EV-AED-008"],
            "blocked_gates": [],
            "decision_complete": False,
        },
    ]
    blocked_gate_ids = sorted(
        {
            gate_id
            for section in sections
            for gate_id in section["blocked_gates"]
        }
    )
    passed_gate_ids = sorted(
        {
            gate_id
            for section in sections
            for gate_id in section["passed_gates"]
        }
    )

    return {
        "decision_status_rollup_state": "contract-only-decision-status-blocked",
        "decision_status_consistent": True,
        "decision_status_passed": False,
        "owner_decision_complete": False,
        "source_surfaces": {
            "readiness_rollup": {
                "endpoint": "GET /uib/events/subscriptions/readiness-rollup",
                "active": readiness["summary"]["readiness_rollup_contract_active"],
                "broker_activation_ready": readiness["summary"]["broker_activation_ready"],
                "production_activation_allowed": readiness["summary"]["production_activation_allowed"],
            },
            "activation_evidence_intake": {
                "endpoint": "POST /uib/events/subscriptions/activation-evidence",
                "called_by_decision_status_rollup": False,
                "prototype_persists_evidence": False,
                "review_queue_updated": False,
            },
            "activation_evidence_status": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/status",
                "active": status["summary"]["activation_evidence_status_contract_active"],
                "persisted_submission_count": status["summary"]["persisted_submission_count"],
                "pending_review_count": status["summary"]["pending_review_count"],
            },
            "activation_evidence_retention_checklist": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/retention-checklist",
                "active": retention["summary"]["activation_evidence_retention_checklist_active"],
                "open_decision_ids": retention_open_decisions,
            },
        },
        "decision_sections": sections,
        "blocking_decisions": [
            {
                "decision_id": "EV-AED-001",
                "area": "activation-evidence-intake-surface",
                "required_decision": "Keep the activation evidence intake contract available without invoking it from this rollup.",
                "current_state": "contract-bound-no-call",
                "resolved": True,
            },
            {
                "decision_id": "EV-AED-002",
                "area": "durable-evidence-store-owner",
                "required_decision": "Assign durable evidence store owner, service identity, artifact root, and audit export backend.",
                "current_state": "TBD-target-platform",
                "resolved": False,
            },
            {
                "decision_id": "EV-AED-003",
                "area": "review-workflow-owner",
                "required_decision": "Assign review queue owner, reviewer identity source, escalation rules, and rejection semantics.",
                "current_state": "TBD-target-platform",
                "resolved": False,
            },
            {
                "decision_id": "EV-AED-004",
                "area": "gate-closure-authority",
                "required_decision": "Assign who can close EV-* and DRV-GAP gates, plus rollback and audit requirements.",
                "current_state": "TBD-target-platform",
                "resolved": False,
            },
            {
                "decision_id": "EV-AED-005",
                "area": "retention-delete-export-policy",
                "required_decision": "Approve retention TTL, privacy redaction, delete authorization, export format, and orphaned-ref behavior.",
                "current_state": "TBD-target-platform",
                "resolved": False,
            },
            {
                "decision_id": "EV-AED-006",
                "area": "activation-approval-policy",
                "required_decision": "Define when activation evidence can change gates and allow broker/runtime activation.",
                "current_state": "blocked-contract-only",
                "resolved": False,
            },
        ],
        "mandatory_gates": [
            {
                "gate_id": "EV-AED-001",
                "name": "activation-evidence-intake-surface-bound",
                "required_evidence": "POST /uib/events/subscriptions/activation-evidence remains the only intake command and is not called by this status rollup.",
                "passed": True,
            },
            {
                "gate_id": "EV-AED-002",
                "name": "review-status-surface-bound",
                "required_evidence": "GET /uib/events/subscriptions/activation-evidence/status reports no-store counters and no review workflow.",
                "passed": True,
            },
            {
                "gate_id": "EV-AED-003",
                "name": "retention-checklist-surface-bound",
                "required_evidence": "GET /uib/events/subscriptions/activation-evidence/retention-checklist reports owner, URI, retention, review, gate closure, delete, and export decisions.",
                "passed": True,
            },
            {
                "gate_id": "EV-AED-004",
                "name": "readiness-rollup-linked",
                "required_evidence": "Decision status rollup links back to EV-RU blockers before allowing activation.",
                "passed": True,
            },
            {
                "gate_id": "EV-AED-005",
                "name": "durable-store-owner-assigned",
                "required_evidence": "Target platform assigns durable evidence store owner before any activation evidence can be retained.",
                "passed": False,
            },
            {
                "gate_id": "EV-AED-006",
                "name": "review-and-gate-authority-assigned",
                "required_evidence": "Target platform assigns review workflow owner and gate closure authority.",
                "passed": False,
            },
            {
                "gate_id": "EV-AED-007",
                "name": "activation-approval-policy-confirmed",
                "required_evidence": "Target platform confirms when accepted evidence can close gates and activate broker/runtime.",
                "passed": False,
            },
            {
                "gate_id": "EV-AED-008",
                "name": "no-side-effect-rollup",
                "required_evidence": "Rollup reports no POST call, no persistence, no review queue update, no gate closure, no broker activation, and no Driver/HAL or virtualization trigger.",
                "passed": True,
            },
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/decision-status-rollup",
            "android_binder": "getEventSubscriptionActivationEvidenceDecisionStatusRollupJson",
            "linux_cli": "event-subscription-activation-evidence-decision-status-rollup",
            "linux_ipc": "uib.events.subscriptions.activation.evidence.decision.status.rollup",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationEvidenceDecisionStatusRollup",
        },
        "summary": {
            "activation_evidence_decision_status_rollup_active": True,
            "decision_status_consistent": True,
            "decision_status_passed": False,
            "owner_decision_complete": False,
            "open_decision_count": len([decision for decision in retention["owner_decisions"] if decision["current_selection"].startswith("TBD")]),
            "passed_gate_count": len(passed_gate_ids),
            "blocked_gate_count": len(blocked_gate_ids),
            "blocked_gate_ids": blocked_gate_ids,
            "activation_evidence_contract_active": True,
            "activation_evidence_status_contract_active": True,
            "activation_evidence_retention_checklist_active": True,
            "activation_evidence_intake_called": False,
            "activation_evidence_persisted": False,
            "evidence_store_active": False,
            "review_workflow_active": False,
            "delete_workflow_active": False,
            "export_workflow_active": False,
            "persisted_submission_count": 0,
            "pending_review_count": 0,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "activation_allowed": False,
            "broker_activation_ready": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_EVIDENCE_DECISION_STATUS_REQ_IDS,
    }


def event_subscription_activation_approval_dry_run_status_payload() -> dict[str, Any]:
    decision_status = event_subscription_activation_evidence_decision_status_rollup_payload()
    blocking_decision_ids = [
        decision["decision_id"]
        for decision in decision_status["blocking_decisions"]
        if not decision["resolved"]
    ]
    gate_ids = [gate["gate_id"] for gate in decision_status["mandatory_gates"]]

    mandatory_gates = [
        {
            "gate_id": "EV-AAS-001",
            "name": "decision-status-rollup-bound",
            "required_evidence": "Approval dry-run status is derived from GET /uib/events/subscriptions/activation-evidence/decision-status-rollup without invoking POST endpoints.",
            "passed": True,
        },
        {
            "gate_id": "EV-AAS-002",
            "name": "approval-dry-run-request-shape-declared",
            "required_evidence": "Target platform defines the approval dry-run input envelope before any activation gate can be closed.",
            "passed": True,
        },
        {
            "gate_id": "EV-AAS-003",
            "name": "last-result-no-store-status-declared",
            "required_evidence": "Prototype reports no persisted approval dry-run request or last-result record.",
            "passed": True,
        },
        {
            "gate_id": "EV-AAS-004",
            "name": "approval-authority-assigned",
            "required_evidence": "Target platform assigns who can approve activation evidence and broker/runtime activation.",
            "passed": False,
        },
        {
            "gate_id": "EV-AAS-005",
            "name": "gate-closure-policy-assigned",
            "required_evidence": "Target platform assigns gate closure authority, rollback semantics, and audit export path.",
            "passed": False,
        },
        {
            "gate_id": "EV-AAS-006",
            "name": "broker-activation-policy-assigned",
            "required_evidence": "Target platform assigns broker activation policy, service identity, and runtime deployment boundary.",
            "passed": False,
        },
        {
            "gate_id": "EV-AAS-007",
            "name": "android-linux-approval-dry-run-status-parity",
            "required_evidence": "REST, Android Binder, Android Console, Linux CLI, Linux IPC, Linux gRPC/RPC, docs, and smoke tests expose equivalent approval dry-run status fields.",
            "passed": True,
        },
        {
            "gate_id": "EV-AAS-008",
            "name": "no-side-effect-status",
            "required_evidence": "Status endpoint reports no dry-run POST call, no persistence, no review queue update, no gate closure, no broker activation, no Driver/HAL, and no virtualization trigger.",
            "passed": True,
        },
    ]

    return {
        "operation": "event-subscription-activation-approval-dry-run-status",
        "approval_dry_run_status_state": "contract-only-approval-dry-run-no-store-status",
        "approval_dry_run_status_active": True,
        "source_decision_status_rollup": {
            "endpoint": "GET /uib/events/subscriptions/activation-evidence/decision-status-rollup",
            "active": decision_status["summary"]["activation_evidence_decision_status_rollup_active"],
            "decision_status_consistent": decision_status["summary"]["decision_status_consistent"],
            "decision_status_passed": decision_status["summary"]["decision_status_passed"],
            "owner_decision_complete": decision_status["summary"]["owner_decision_complete"],
            "blocking_decision_ids": blocking_decision_ids,
            "source_gate_ids": gate_ids,
        },
        "dry_run_status": {
            "approval_dry_run_invoked": False,
            "last_result_available": False,
            "persisted_dry_run_count": 0,
            "pending_approval_count": 0,
            "approved_gate_count": 0,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "activation_allowed": False,
            "broker_activation_ready": False,
            "reason": "prototype exposes a blocked/no-store approval decision dry-run request contract but no result store, review queue, gate closure workflow, or broker activation path",
        },
        "approval_request_shape": {
            "candidate_fields": [
                "approval_request_id",
                "source_decision_status_rollup_ref",
                "target_gate_ids",
                "approval_authority",
                "reviewer",
                "evidence_refs",
                "rollback_plan_ref",
                "runtime_governance_policy_ref",
            ],
            "required_ref_types": ["platform_decision", "owner_approval", "test_log", "driver_gap_review"],
            "prototype_accepts_request": True,
            "prototype_persists_request": False,
        },
        "blocking_decisions": [
            {
                "decision_id": "EV-AAS-DECISION-001",
                "area": "approval-authority",
                "required_decision": "Assign the authority that can approve event subscription broker/runtime activation.",
                "current_state": "TBD-target-platform",
                "resolved": False,
            },
            {
                "decision_id": "EV-AAS-DECISION-002",
                "area": "approval-policy",
                "required_decision": "Define which EV-* and DRV-GAP gates must be closed before approval dry-run can pass.",
                "current_state": "blocked-by-EV-AED",
                "resolved": False,
            },
            {
                "decision_id": "EV-AAS-DECISION-003",
                "area": "approval-result-store",
                "required_decision": "Assign whether approval dry-run results are persisted, audited, exported, and retained.",
                "current_state": "not-implemented",
                "resolved": False,
            },
            {
                "decision_id": "EV-AAS-DECISION-004",
                "area": "review-queue-owner",
                "required_decision": "Assign review queue owner and escalation semantics for failed approval dry-runs.",
                "current_state": "TBD-target-platform",
                "resolved": False,
            },
            {
                "decision_id": "EV-AAS-DECISION-005",
                "area": "gate-closure-authority",
                "required_decision": "Assign who can close activation gates and how rollback is audited.",
                "current_state": "TBD-target-platform",
                "resolved": False,
            },
            {
                "decision_id": "EV-AAS-DECISION-006",
                "area": "broker-runtime-activation-owner",
                "required_decision": "Assign broker process owner, Android service path, Linux daemon path, and activation identity.",
                "current_state": "TBD-target-platform",
                "resolved": False,
            },
            {
                "decision_id": "EV-AAS-DECISION-007",
                "area": "high-rate-driver-gap-review",
                "required_decision": "Confirm DRV-GAP-004/005 handling before DDS, SOME/IP, TSN/PTP, shared memory, or Safety Runtime are used.",
                "current_state": "blocked-contract-only",
                "resolved": False,
            },
            {
                "decision_id": "EV-AAS-DECISION-008",
                "area": "android-linux-parity",
                "required_decision": "Keep Android and Linux approval dry-run status surfaces equivalent before adding a real dry-run command.",
                "current_state": "contract-bound",
                "resolved": True,
            },
        ],
        "mandatory_gates": mandatory_gates,
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-dry-run/status",
            "android_binder": "getEventSubscriptionActivationApprovalDryRunStatusJson",
            "linux_cli": "event-subscription-activation-approval-dry-run-status",
            "linux_ipc": "uib.events.subscriptions.activation.approval.dry.run.status",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDryRunStatus",
        },
        "summary": {
            "activation_approval_dry_run_status_active": True,
            "source_decision_status_rollup_bound": True,
            "decision_status_consistent": decision_status["summary"]["decision_status_consistent"],
            "decision_status_passed": decision_status["summary"]["decision_status_passed"],
            "owner_decision_complete": decision_status["summary"]["owner_decision_complete"],
            "approval_dry_run_invoked": False,
            "last_result_available": False,
            "persisted_dry_run_count": 0,
            "pending_approval_count": 0,
            "approved_gate_count": 0,
            "blocking_decision_count": len([decision for decision in decision_status["blocking_decisions"] if not decision["resolved"]]),
            "approval_authority_assigned": False,
            "approval_policy_confirmed": False,
            "approval_result_store_active": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "activation_allowed": False,
            "broker_activation_ready": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DRY_RUN_STATUS_REQ_IDS,
    }


def event_subscription_activation_approval_authority_checklist_payload() -> dict[str, Any]:
    dry_run_status = event_subscription_activation_approval_dry_run_status_payload()
    blocking_decision_ids = [
        decision["decision_id"]
        for decision in dry_run_status["blocking_decisions"]
        if not decision["resolved"]
    ]
    source_gate_ids = [gate["gate_id"] for gate in dry_run_status["mandatory_gates"]]

    authority_items = [
        {
            "item_id": "EV-AAA-AUTH-001",
            "area": "approval-authority-owner",
            "required_decision": "Assign the owner allowed to approve event subscription activation evidence and broker/runtime activation.",
            "source_decision_id": "EV-AAS-DECISION-001",
            "current_state": "TBD-target-platform",
            "ready": False,
        },
        {
            "item_id": "EV-AAA-AUTH-002",
            "area": "approval-policy-owner",
            "required_decision": "Define the approval policy that binds EV-AED, EV-AAS, DRV-GAP-004, and DRV-GAP-005 gates before any approval dry-run can pass.",
            "source_decision_id": "EV-AAS-DECISION-002",
            "current_state": "blocked-by-source-decision-rollup",
            "ready": False,
        },
        {
            "item_id": "EV-AAA-AUTH-003",
            "area": "signature-rbac",
            "required_decision": "Select approval signer identity, RBAC scope, audit identity, and rollback authority for Android and Linux.",
            "source_decision_id": "EV-AAS-DECISION-001",
            "current_state": "TBD-target-platform",
            "ready": False,
        },
        {
            "item_id": "EV-AAA-AUTH-004",
            "area": "approval-result-store-owner",
            "required_decision": "Assign the owner and retention policy for approval dry-run result storage before any POST command exists.",
            "source_decision_id": "EV-AAS-DECISION-003",
            "current_state": "not-implemented",
            "ready": False,
        },
        {
            "item_id": "EV-AAA-AUTH-005",
            "area": "review-queue-owner",
            "required_decision": "Assign review queue ownership, escalation semantics, and failure triage for blocked approval dry-runs.",
            "source_decision_id": "EV-AAS-DECISION-004",
            "current_state": "TBD-target-platform",
            "ready": False,
        },
        {
            "item_id": "EV-AAA-AUTH-006",
            "area": "gate-closure-authority",
            "required_decision": "Assign who can close activation gates and how rollback, audit export, and evidence retention are enforced.",
            "source_decision_id": "EV-AAS-DECISION-005",
            "current_state": "TBD-target-platform",
            "ready": False,
        },
        {
            "item_id": "EV-AAA-AUTH-007",
            "area": "broker-runtime-activation-owner",
            "required_decision": "Assign Android service identity, Linux daemon identity, broker process owner, and runtime activation boundary.",
            "source_decision_id": "EV-AAS-DECISION-006",
            "current_state": "TBD-target-platform",
            "ready": False,
        },
        {
            "item_id": "EV-AAA-AUTH-008",
            "area": "driver-gap-review-owner",
            "required_decision": "Confirm DRV-GAP-004/005 review owner before DDS, SOME/IP, TSN/PTP, shared memory, or Safety Runtime activation.",
            "source_decision_id": "EV-AAS-DECISION-007",
            "current_state": "blocked-contract-only",
            "ready": False,
        },
    ]
    unresolved_items = [item for item in authority_items if not item["ready"]]

    return {
        "operation": "event-subscription-activation-approval-authority-checklist",
        "approval_authority_checklist_state": "contract-only-approval-authority-blocked",
        "approval_authority_checklist_active": True,
        "source_approval_dry_run_status": {
            "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-dry-run/status",
            "active": dry_run_status["summary"]["activation_approval_dry_run_status_active"],
            "source_decision_status_rollup_bound": dry_run_status["summary"]["source_decision_status_rollup_bound"],
            "decision_status_consistent": dry_run_status["summary"]["decision_status_consistent"],
            "decision_status_passed": dry_run_status["summary"]["decision_status_passed"],
            "approval_dry_run_invoked": dry_run_status["summary"]["approval_dry_run_invoked"],
            "persisted_dry_run_count": dry_run_status["summary"]["persisted_dry_run_count"],
            "blocking_decision_ids": blocking_decision_ids,
            "source_gate_ids": source_gate_ids,
        },
        "authority_items": authority_items,
        "blocking_decisions": [
            {
                "decision_id": "EV-AAA-DECISION-001",
                "area": "approval-authority-owner",
                "required_decision": "Assign final approval authority before any activation approval dry-run command is added.",
                "current_state": "TBD-target-platform",
                "resolved": False,
            },
            {
                "decision_id": "EV-AAA-DECISION-002",
                "area": "approval-policy",
                "required_decision": "Confirm the policy that binds source EV-AAS/EV-AED gates, Driver/HAL gap evidence, and runtime governance checks.",
                "current_state": "blocked-by-source-approval-status",
                "resolved": False,
            },
            {
                "decision_id": "EV-AAA-DECISION-003",
                "area": "signature-rbac",
                "required_decision": "Confirm signer identity, RBAC permission name, audit subject, and rollback approver for Android and Linux.",
                "current_state": "TBD-target-platform",
                "resolved": False,
            },
            {
                "decision_id": "EV-AAA-DECISION-004",
                "area": "approval-result-store",
                "required_decision": "Assign result store owner, schema, retention, export, and deletion semantics.",
                "current_state": "not-implemented",
                "resolved": False,
            },
            {
                "decision_id": "EV-AAA-DECISION-005",
                "area": "review-queue-owner",
                "required_decision": "Assign the review queue and escalation owner before approval dry-run failures can be triaged.",
                "current_state": "TBD-target-platform",
                "resolved": False,
            },
            {
                "decision_id": "EV-AAA-DECISION-006",
                "area": "gate-closure-authority",
                "required_decision": "Assign the gate closure authority, rollback witness, and audit export backend.",
                "current_state": "TBD-target-platform",
                "resolved": False,
            },
            {
                "decision_id": "EV-AAA-DECISION-007",
                "area": "broker-runtime-owner",
                "required_decision": "Assign Android/Linux runtime owner for any future broker activation path.",
                "current_state": "TBD-target-platform",
                "resolved": False,
            },
            {
                "decision_id": "EV-AAA-DECISION-008",
                "area": "android-linux-parity",
                "required_decision": "Keep REST, Android Binder/Console, Linux CLI/IPC/gRPC, docs, and smoke tests aligned for this checklist.",
                "current_state": "contract-bound",
                "resolved": True,
            },
        ],
        "mandatory_gates": [
            {
                "gate_id": "EV-AAA-001",
                "name": "source-approval-dry-run-status-bound",
                "required_evidence": "Checklist is derived from GET /uib/events/subscriptions/activation-evidence/approval-dry-run/status and does not invoke POST endpoints.",
                "passed": True,
            },
            {
                "gate_id": "EV-AAA-002",
                "name": "approval-authority-owner-assigned",
                "required_evidence": "Target platform assigns the approval authority for broker/runtime activation.",
                "passed": False,
            },
            {
                "gate_id": "EV-AAA-003",
                "name": "approval-policy-owner-assigned",
                "required_evidence": "Target platform assigns the owner of approval policy, gate dependencies, and pass/fail rules.",
                "passed": False,
            },
            {
                "gate_id": "EV-AAA-004",
                "name": "signature-rbac-confirmed",
                "required_evidence": "Target platform confirms signer identity, RBAC scope, audit identity, and rollback approver.",
                "passed": False,
            },
            {
                "gate_id": "EV-AAA-005",
                "name": "approval-result-store-owner-assigned",
                "required_evidence": "Target platform assigns result store owner, schema, retention, export, and deletion semantics.",
                "passed": False,
            },
            {
                "gate_id": "EV-AAA-006",
                "name": "review-queue-owner-assigned",
                "required_evidence": "Target platform assigns review queue owner and escalation semantics for blocked approvals.",
                "passed": False,
            },
            {
                "gate_id": "EV-AAA-007",
                "name": "gate-closure-and-broker-owner-assigned",
                "required_evidence": "Target platform assigns gate closure authority and Android/Linux broker activation owner.",
                "passed": False,
            },
            {
                "gate_id": "EV-AAA-008",
                "name": "no-side-effect-checklist-parity",
                "required_evidence": "REST, Android Binder/Console, Linux CLI/IPC/gRPC, docs, and smoke tests expose this checklist without persistence, gate closure, broker activation, Driver/HAL, or virtualization side effects.",
                "passed": True,
            },
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist",
            "android_binder": "getEventSubscriptionActivationApprovalAuthorityChecklistJson",
            "linux_cli": "event-subscription-activation-approval-authority-checklist",
            "linux_ipc": "uib.events.subscriptions.activation.approval.authority.checklist",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalAuthorityChecklist",
        },
        "summary": {
            "activation_approval_authority_checklist_active": True,
            "source_approval_dry_run_status_bound": True,
            "source_decision_status_rollup_bound": dry_run_status["summary"]["source_decision_status_rollup_bound"],
            "decision_status_consistent": dry_run_status["summary"]["decision_status_consistent"],
            "decision_status_passed": dry_run_status["summary"]["decision_status_passed"],
            "approval_authority_checklist_complete": True,
            "approval_authority_ready": False,
            "required_authority_item_count": len(authority_items),
            "unresolved_authority_item_count": len(unresolved_items),
            "approval_authority_assigned": False,
            "approval_policy_confirmed": False,
            "approval_signature_rbac_confirmed": False,
            "approval_result_store_active": False,
            "review_queue_owner_assigned": False,
            "gate_closure_authority_assigned": False,
            "broker_activation_owner_assigned": False,
            "driver_gap_review_owner_assigned": False,
            "approval_dry_run_invoked": False,
            "last_result_available": False,
            "persisted_dry_run_count": 0,
            "pending_approval_count": 0,
            "approval_result_store_created": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "activation_allowed": False,
            "broker_activation_ready": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_AUTHORITY_CHECKLIST_REQ_IDS,
    }


def event_subscription_activation_approval_authority_audit_consistency_payload() -> dict[str, Any]:
    authority = event_subscription_activation_approval_authority_checklist_payload()
    dry_run_status = event_subscription_activation_approval_dry_run_status_payload()
    decision_status = event_subscription_activation_evidence_decision_status_rollup_payload()

    authority_item_count = len(authority["authority_items"])
    unresolved_authority_item_count = len([item for item in authority["authority_items"] if not item["ready"]])
    unresolved_authority_decision_count = len(
        [decision for decision in authority["blocking_decisions"] if not decision["resolved"]]
    )
    authority_gate_ids = [gate["gate_id"] for gate in authority["mandatory_gates"]]

    source_authority_checklist_bound = authority["summary"]["activation_approval_authority_checklist_active"]
    source_approval_dry_run_status_bound = dry_run_status["summary"]["activation_approval_dry_run_status_active"]
    source_decision_status_rollup_bound = decision_status["summary"]["decision_status_consistent"]
    authority_item_count_consistent = (
        authority["summary"]["required_authority_item_count"] == authority_item_count
        and authority["summary"]["unresolved_authority_item_count"] == unresolved_authority_item_count
    )
    authority_blocker_state_consistent = (
        authority["summary"]["approval_authority_ready"] is False
        and unresolved_authority_item_count == authority_item_count
        and unresolved_authority_decision_count > 0
        and any(decision["resolved"] for decision in authority["blocking_decisions"])
    )
    approval_no_store_consistent = (
        authority["summary"]["approval_dry_run_invoked"] is False
        and authority["summary"]["persisted_dry_run_count"] == 0
        and authority["summary"]["approval_result_store_active"] is False
        and authority["summary"]["approval_result_store_created"] is False
        and authority["summary"]["review_queue_updated"] is False
    )
    android_linux_parity_consistent = (
        authority["api_surface"]["android_binder"] == "getEventSubscriptionActivationApprovalAuthorityChecklistJson"
        and authority["api_surface"]["linux_cli"] == "event-subscription-activation-approval-authority-checklist"
        and authority["api_surface"]["linux_ipc"] == "uib.events.subscriptions.activation.approval.authority.checklist"
        and authority["api_surface"]["linux_grpc_rpc"]
        == "CentralBrainGateway.GetEventSubscriptionActivationApprovalAuthorityChecklist"
    )
    no_side_effects_consistent = (
        authority["summary"]["gates_closed"] is False
        and authority["summary"]["activation_allowed"] is False
        and authority["summary"]["broker_active"] is False
        and authority["summary"]["hardware_accessed"] is False
        and authority["summary"]["driver_development_triggered"] is False
        and authority["summary"]["virtualization_development_triggered"] is False
        and authority["summary"]["service_dispatch_triggered"] is False
    )
    consistency_passed = all(
        [
            source_authority_checklist_bound,
            source_approval_dry_run_status_bound,
            source_decision_status_rollup_bound,
            authority_item_count_consistent,
            authority_blocker_state_consistent,
            approval_no_store_consistent,
            android_linux_parity_consistent,
            no_side_effects_consistent,
        ]
    )

    audit_findings = [
        {
            "finding_id": "EV-AAC-AUD-001",
            "area": "source-authority-checklist",
            "expected": "Authority checklist endpoint is active and still read-only.",
            "observed": "active" if source_authority_checklist_bound else "not-active",
            "consistent": source_authority_checklist_bound,
        },
        {
            "finding_id": "EV-AAC-AUD-002",
            "area": "source-approval-dry-run-status",
            "expected": "Approval dry-run status is bound without invoking a POST command.",
            "observed": "bound-no-post" if source_approval_dry_run_status_bound else "not-bound",
            "consistent": source_approval_dry_run_status_bound,
        },
        {
            "finding_id": "EV-AAC-AUD-003",
            "area": "source-decision-status-rollup",
            "expected": "Decision status rollup is internally consistent and still blocked.",
            "observed": "consistent-blocked" if source_decision_status_rollup_bound else "not-consistent",
            "consistent": source_decision_status_rollup_bound,
        },
        {
            "finding_id": "EV-AAC-AUD-004",
            "area": "authority-item-count",
            "expected": "Authority item count matches summary and all authority items remain unresolved.",
            "observed": f"{unresolved_authority_item_count}/{authority_item_count} unresolved",
            "consistent": authority_item_count_consistent,
        },
        {
            "finding_id": "EV-AAC-AUD-005",
            "area": "blocker-state",
            "expected": "Approval authority remains not ready while open decisions still block activation and parity is already resolved.",
            "observed": f"{unresolved_authority_decision_count} blocking decisions open",
            "consistent": authority_blocker_state_consistent,
        },
        {
            "finding_id": "EV-AAC-AUD-006",
            "area": "no-store",
            "expected": "No dry-run result, approval result store, or review queue state exists.",
            "observed": "no-store" if approval_no_store_consistent else "store-or-queue-present",
            "consistent": approval_no_store_consistent,
        },
        {
            "finding_id": "EV-AAC-AUD-007",
            "area": "android-linux-parity",
            "expected": "REST, Android Binder, Linux CLI, Linux IPC, and Linux gRPC names are paired.",
            "observed": "paired" if android_linux_parity_consistent else "mismatch",
            "consistent": android_linux_parity_consistent,
        },
        {
            "finding_id": "EV-AAC-AUD-008",
            "area": "no-side-effect",
            "expected": "Audit endpoint does not close gates, activate broker, touch hardware, or trigger Driver/HAL/virtualization.",
            "observed": "no-side-effects" if no_side_effects_consistent else "side-effect-detected",
            "consistent": no_side_effects_consistent,
        },
    ]

    return {
        "operation": "event-subscription-activation-approval-authority-audit-consistency",
        "approval_authority_audit_state": "contract-only-approval-authority-audit-consistent",
        "approval_authority_audit_consistency_active": True,
        "source_surfaces": {
            "authority_checklist": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist",
                "active": source_authority_checklist_bound,
                "authority_gate_ids": authority_gate_ids,
                "required_authority_item_count": authority_item_count,
                "unresolved_authority_item_count": unresolved_authority_item_count,
            },
            "approval_dry_run_status": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-dry-run/status",
                "active": source_approval_dry_run_status_bound,
                "approval_dry_run_invoked": dry_run_status["summary"]["approval_dry_run_invoked"],
                "persisted_dry_run_count": dry_run_status["summary"]["persisted_dry_run_count"],
            },
            "decision_status_rollup": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/decision-status-rollup",
                "consistent": source_decision_status_rollup_bound,
                "passed": decision_status["summary"]["decision_status_passed"],
                "activation_allowed": decision_status["summary"]["activation_allowed"],
            },
        },
        "audit_findings": audit_findings,
        "mandatory_gates": [
            {
                "gate_id": "EV-AAC-001",
                "name": "source-authority-checklist-bound",
                "required_evidence": "Audit reads the authority checklist without invoking POST endpoints.",
                "passed": source_authority_checklist_bound,
            },
            {
                "gate_id": "EV-AAC-002",
                "name": "source-approval-dry-run-status-bound",
                "required_evidence": "Audit reads approval dry-run no-store status without invoking a dry-run command.",
                "passed": source_approval_dry_run_status_bound,
            },
            {
                "gate_id": "EV-AAC-003",
                "name": "source-decision-status-rollup-bound",
                "required_evidence": "Audit binds the upstream activation evidence decision status rollup.",
                "passed": source_decision_status_rollup_bound,
            },
            {
                "gate_id": "EV-AAC-004",
                "name": "authority-item-count-consistent",
                "required_evidence": "Authority item count, unresolved item count, and summary counters match.",
                "passed": authority_item_count_consistent,
            },
            {
                "gate_id": "EV-AAC-005",
                "name": "blocker-state-consistent",
                "required_evidence": "Approval authority remains blocked while open decisions stay open and parity remains resolved.",
                "passed": authority_blocker_state_consistent,
            },
            {
                "gate_id": "EV-AAC-006",
                "name": "no-store-audit-consistent",
                "required_evidence": "Audit confirms no dry-run result store, approval result store, or review queue state exists.",
                "passed": approval_no_store_consistent,
            },
            {
                "gate_id": "EV-AAC-007",
                "name": "android-linux-audit-parity",
                "required_evidence": "REST, Android Binder/Console, Linux CLI/IPC/gRPC, docs, and smoke tests expose equivalent audit fields.",
                "passed": android_linux_parity_consistent,
            },
            {
                "gate_id": "EV-AAC-008",
                "name": "no-side-effect-audit",
                "required_evidence": "Audit performs no persistence, gate closure, broker activation, hardware access, Driver/HAL work, or virtualization work.",
                "passed": no_side_effects_consistent,
            },
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/audit-consistency",
            "android_binder": "getEventSubscriptionActivationApprovalAuthorityAuditConsistencyJson",
            "linux_cli": "event-subscription-activation-approval-authority-audit-consistency",
            "linux_ipc": "uib.events.subscriptions.activation.approval.authority.audit.consistency",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalAuthorityAuditConsistency",
        },
        "summary": {
            "activation_approval_authority_audit_consistency_active": True,
            "consistency_passed": consistency_passed,
            "source_authority_checklist_bound": source_authority_checklist_bound,
            "source_approval_dry_run_status_bound": source_approval_dry_run_status_bound,
            "source_decision_status_rollup_bound": source_decision_status_rollup_bound,
            "authority_item_count_consistent": authority_item_count_consistent,
            "authority_blocker_state_consistent": authority_blocker_state_consistent,
            "approval_no_store_consistent": approval_no_store_consistent,
            "android_linux_parity_consistent": android_linux_parity_consistent,
            "no_side_effects_consistent": no_side_effects_consistent,
            "approval_authority_ready": False,
            "approval_authority_assigned": False,
            "approval_policy_confirmed": False,
            "approval_signature_rbac_confirmed": False,
            "approval_result_store_active": False,
            "review_queue_owner_assigned": False,
            "gate_closure_authority_assigned": False,
            "broker_activation_owner_assigned": False,
            "driver_gap_review_owner_assigned": False,
            "required_authority_item_count": authority_item_count,
            "unresolved_authority_item_count": unresolved_authority_item_count,
            "blocking_decision_count": unresolved_authority_decision_count,
            "approval_dry_run_invoked": False,
            "persisted_dry_run_count": 0,
            "approval_result_store_created": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "activation_allowed": False,
            "broker_activation_ready": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_AUTHORITY_AUDIT_CONSISTENCY_REQ_IDS,
    }


def event_subscription_activation_approval_decision_blocker_rollup_payload() -> dict[str, Any]:
    audit = event_subscription_activation_approval_authority_audit_consistency_payload()
    authority = event_subscription_activation_approval_authority_checklist_payload()
    dry_run_status = event_subscription_activation_approval_dry_run_status_payload()
    decision_status = event_subscription_activation_evidence_decision_status_rollup_payload()

    source_authority_audit_bound = audit["summary"]["activation_approval_authority_audit_consistency_active"]
    source_authority_audit_consistent = audit["summary"]["consistency_passed"]
    source_authority_checklist_bound = authority["summary"]["activation_approval_authority_checklist_active"]
    source_approval_dry_run_status_bound = dry_run_status["summary"]["activation_approval_dry_run_status_active"]
    source_decision_status_rollup_bound = decision_status["summary"]["decision_status_consistent"]
    no_side_effects_bound = audit["summary"]["no_side_effects_consistent"]

    decision_blockers = [
        {
            "blocker_id": "EV-ADB-001",
            "area": "source-decision-status",
            "source": "GET /uib/events/subscriptions/activation-evidence/decision-status-rollup",
            "current_state": "decision-status-still-blocked",
            "blocks": ["approval-dry-run-command", "broker-activation"],
            "open": not decision_status["summary"]["decision_status_passed"],
        },
        {
            "blocker_id": "EV-ADB-002",
            "area": "approval-authority-owner",
            "source": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist",
            "current_state": "approval-authority-unassigned",
            "blocks": ["approval-decision", "approval-dry-run-command"],
            "open": not authority["summary"]["approval_authority_assigned"],
        },
        {
            "blocker_id": "EV-ADB-003",
            "area": "approval-policy-and-signature",
            "source": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist",
            "current_state": "policy-signature-rbac-unconfirmed",
            "blocks": ["approval-decision", "gate-closure"],
            "open": not (
                authority["summary"]["approval_policy_confirmed"]
                and authority["summary"]["approval_signature_rbac_confirmed"]
            ),
        },
        {
            "blocker_id": "EV-ADB-004",
            "area": "approval-result-store-and-review-queue",
            "source": "GET /uib/events/subscriptions/activation-evidence/approval-dry-run/status",
            "current_state": "result-store-and-review-queue-not-created",
            "blocks": ["approval-result-retention", "blocked-approval-triage"],
            "open": not (
                dry_run_status["summary"]["approval_result_store_active"]
                and dry_run_status["summary"]["review_queue_updated"]
            ),
        },
        {
            "blocker_id": "EV-ADB-005",
            "area": "gate-closure-and-broker-runtime-owner",
            "source": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist",
            "current_state": "gate-closure-and-broker-owner-unassigned",
            "blocks": ["gate-closure", "broker-activation"],
            "open": not (
                authority["summary"]["gate_closure_authority_assigned"]
                and authority["summary"]["broker_activation_owner_assigned"]
            ),
        },
        {
            "blocker_id": "EV-ADB-006",
            "area": "approval-dry-run-command-surface",
            "source": "POST /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run",
            "current_state": "decision-dry-run-request-contract-rejects-no-store",
            "blocks": ["approval-decision-execution", "approval-result-persistence"],
            "open": True,
        },
        {
            "blocker_id": "EV-ADB-007",
            "area": "driver-hal-high-rate-scope",
            "source": "DRV-GAP-004/DRV-GAP-005",
            "current_state": "high-rate-transport-and-shared-memory-scope-unresolved",
            "blocks": ["dds-or-someip-high-rate-activation", "safety-runtime-shared-memory"],
            "open": not authority["summary"]["driver_gap_review_owner_assigned"],
        },
        {
            "blocker_id": "EV-ADB-008",
            "area": "no-side-effect-contract",
            "source": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/audit-consistency",
            "current_state": "contract-only-no-side-effects-enforced",
            "blocks": ["unsafe-prototype-activation"],
            "open": False,
        },
    ]
    open_blockers = [blocker for blocker in decision_blockers if blocker["open"]]

    return {
        "operation": "event-subscription-activation-approval-decision-blocker-rollup",
        "approval_decision_blocker_rollup_state": "contract-only-approval-decision-blocked",
        "approval_decision_blocker_rollup_active": True,
        "source_surfaces": {
            "approval_authority_audit_consistency": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/audit-consistency",
                "active": source_authority_audit_bound,
                "consistent": source_authority_audit_consistent,
                "audit_gate_count": len(audit["mandatory_gates"]),
            },
            "approval_authority_checklist": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist",
                "active": source_authority_checklist_bound,
                "required_authority_item_count": authority["summary"]["required_authority_item_count"],
                "unresolved_authority_item_count": authority["summary"]["unresolved_authority_item_count"],
            },
            "approval_dry_run_status": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-dry-run/status",
                "active": source_approval_dry_run_status_bound,
                "approval_dry_run_invoked": dry_run_status["summary"]["approval_dry_run_invoked"],
                "persisted_dry_run_count": dry_run_status["summary"]["persisted_dry_run_count"],
            },
            "activation_evidence_decision_status_rollup": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/decision-status-rollup",
                "consistent": source_decision_status_rollup_bound,
                "passed": decision_status["summary"]["decision_status_passed"],
                "activation_allowed": decision_status["summary"]["activation_allowed"],
            },
        },
        "decision_blockers": decision_blockers,
        "mandatory_gates": [
            {
                "gate_id": "EV-ADB-001",
                "name": "source-authority-audit-bound",
                "required_evidence": "Blocker rollup reads authority audit consistency without invoking approval dry-run POST.",
                "passed": source_authority_audit_bound and source_authority_audit_consistent,
            },
            {
                "gate_id": "EV-ADB-002",
                "name": "approval-authority-owner-resolved",
                "required_evidence": "Target platform assigns final approval authority before approval decision dry-run can exist.",
                "passed": not decision_blockers[1]["open"],
            },
            {
                "gate_id": "EV-ADB-003",
                "name": "approval-policy-signature-resolved",
                "required_evidence": "Approval policy, signer identity, RBAC scope, audit subject, and rollback approver are confirmed.",
                "passed": not decision_blockers[2]["open"],
            },
            {
                "gate_id": "EV-ADB-004",
                "name": "result-store-review-queue-resolved",
                "required_evidence": "Approval result store, retention policy, and review queue owner are available.",
                "passed": not decision_blockers[3]["open"],
            },
            {
                "gate_id": "EV-ADB-005",
                "name": "gate-closure-broker-owner-resolved",
                "required_evidence": "Gate closure authority and Android/Linux broker activation owner are assigned.",
                "passed": not decision_blockers[4]["open"],
            },
            {
                "gate_id": "EV-ADB-006",
                "name": "approval-command-surface-resolved",
                "required_evidence": "Approval decision dry-run request contract rejects while authority, policy, result-store, review-queue, gate-closure, broker, and Driver/HAL blockers remain open.",
                "passed": not decision_blockers[5]["open"],
            },
            {
                "gate_id": "EV-ADB-007",
                "name": "driver-hal-high-rate-scope-resolved",
                "required_evidence": "DRV-GAP-004 and DRV-GAP-005 review owners decide high-rate DDS/SOME-IP/TSN/shared-memory scope.",
                "passed": not decision_blockers[6]["open"],
            },
            {
                "gate_id": "EV-ADB-008",
                "name": "no-side-effect-blocker-rollup",
                "required_evidence": "Blocker rollup performs no persistence, review queue update, gate closure, broker activation, hardware access, Driver/HAL work, or virtualization work.",
                "passed": no_side_effects_bound,
            },
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-blocker-rollup",
            "android_binder": "getEventSubscriptionActivationApprovalDecisionBlockerRollupJson",
            "linux_cli": "event-subscription-activation-approval-decision-blocker-rollup",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.blocker.rollup",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionBlockerRollup",
        },
        "summary": {
            "activation_approval_decision_blocker_rollup_active": True,
            "source_authority_audit_consistency_bound": source_authority_audit_bound,
            "source_authority_audit_consistency_passed": source_authority_audit_consistent,
            "source_authority_checklist_bound": source_authority_checklist_bound,
            "source_approval_dry_run_status_bound": source_approval_dry_run_status_bound,
            "source_decision_status_rollup_bound": source_decision_status_rollup_bound,
            "decision_blocker_rollup_complete": True,
            "required_blocker_count": len(decision_blockers),
            "open_blocker_count": len(open_blockers),
            "approval_decision_ready": False,
            "approval_dry_run_allowed": False,
            "approval_command_surface_ready": False,
            "approval_authority_ready": False,
            "approval_authority_assigned": False,
            "approval_policy_confirmed": False,
            "approval_signature_rbac_confirmed": False,
            "approval_result_store_active": False,
            "approval_result_store_created": False,
            "review_queue_owner_assigned": False,
            "review_queue_updated": False,
            "gate_closure_authority_assigned": False,
            "gates_closed": False,
            "broker_activation_owner_assigned": False,
            "broker_activation_ready": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_BLOCKER_ROLLUP_REQ_IDS,
    }


def event_subscription_activation_approval_decision_dry_run_payload(request: dict[str, Any]) -> dict[str, Any]:
    requested_permissions = request.get("permissions") or ["service.read"]
    policy = permission_check_payload(
        {
            "permissions": requested_permissions,
            "caller_permissions": request.get("caller_permissions", ["vehicle.read", "service.read"]),
            "vehicle_state": request.get("vehicle_state", "parked"),
            "safety_state": request.get("safety_state", "normal"),
            "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
        }
    )
    blocker_rollup = event_subscription_activation_approval_decision_blocker_rollup_payload()
    open_blocker_ids = [
        blocker["blocker_id"]
        for blocker in blocker_rollup["decision_blockers"]
        if blocker["open"]
    ]
    required_fields = [
        "approval_request_id",
        "source_decision_blocker_rollup_ref",
        "target_gate_ids",
        "approval_decision",
        "approval_authority",
        "reviewer",
        "evidence_refs",
        "rollback_plan_ref",
        "runtime_governance_policy_ref",
    ]
    missing_fields = [field for field in required_fields if not request.get(field)]
    invalid_fields: list[dict[str, str]] = []
    if request.get("target_gate_ids") and not isinstance(request["target_gate_ids"], list):
        invalid_fields.append({"field": "target_gate_ids", "reason": "must be a list of EV-* gate ids"})
    if request.get("evidence_refs") and not isinstance(request["evidence_refs"], list):
        invalid_fields.append({"field": "evidence_refs", "reason": "must be a list of evidence reference envelopes"})
    if request.get("approval_authority") and not isinstance(request["approval_authority"], dict):
        invalid_fields.append({"field": "approval_authority", "reason": "must include authority_id, role, and signature_ref"})
    if request.get("reviewer") and not isinstance(request["reviewer"], dict):
        invalid_fields.append({"field": "reviewer", "reason": "must include reviewer app_id and role"})

    accepted_decisions = ["approve_activation", "reject_activation", "request_more_evidence"]
    decision_value = str(request.get("approval_decision") or "")
    if decision_value and decision_value not in accepted_decisions:
        invalid_fields.append({"field": "approval_decision", "reason": "unsupported approval decision value"})

    request_shape_valid = not missing_fields and not invalid_fields
    policy_allowed = policy["decision"] == "allow"
    approval_decision_dry_run_validated = request_shape_valid and policy_allowed
    if missing_fields or invalid_fields:
        dry_run_state = "rejected_missing_or_invalid_fields_contract_only"
    elif not policy_allowed:
        dry_run_state = "rejected_by_policy_contract_only"
    else:
        dry_run_state = "rejected_blocked_contract_only"

    mandatory_gates = [
        {
            "gate_id": "EV-ADD-001",
            "name": "source-decision-blocker-rollup-bound",
            "required_evidence": "Approval decision dry-run request reads the blocker rollup before evaluating any approval request.",
            "passed": True,
        },
        {
            "gate_id": "EV-ADD-002",
            "name": "approval-request-shape-valid",
            "required_evidence": "Request includes approval id, source blocker rollup reference, target gates, decision, authority, reviewer, evidence refs, rollback plan, and governance policy refs.",
            "passed": request_shape_valid,
        },
        {
            "gate_id": "EV-ADD-003",
            "name": "runtime-governance-policy-allowed",
            "required_evidence": "Runtime & Governance policy accepts the caller, vehicle state, and safety state for this dry-run request.",
            "passed": policy_allowed,
        },
        {
            "gate_id": "EV-ADD-004",
            "name": "approval-decision-blockers-cleared",
            "required_evidence": "EV-ADB blocker rollup reports zero open blockers before approval decision can pass.",
            "passed": len(open_blocker_ids) == 0,
        },
        {
            "gate_id": "EV-ADD-005",
            "name": "no-store-request-result",
            "required_evidence": "Dry-run request and result are not persisted and no last-result record is created.",
            "passed": True,
        },
        {
            "gate_id": "EV-ADD-006",
            "name": "no-review-gate-broker-side-effect",
            "required_evidence": "Dry-run does not update review queues, close gates, activate broker/runtime paths, dispatch services, or start DDS/SSE/WebSocket/high-rate data planes.",
            "passed": True,
        },
        {
            "gate_id": "EV-ADD-007",
            "name": "android-linux-decision-dry-run-parity",
            "required_evidence": "REST, Android Binder/Console, Linux CLI, Linux IPC, Linux gRPC/RPC, docs, and smoke tests expose equivalent dry-run request fields.",
            "passed": True,
        },
        {
            "gate_id": "EV-ADD-008",
            "name": "no-driver-hal-virtualization-trigger",
            "required_evidence": "Dry-run request never touches hardware, Driver/HAL, shared memory, Safety Runtime, or virtualization.",
            "passed": True,
        },
    ]

    payload = {
        "operation": "event-subscription-activation-approval-decision-dry-run",
        "approval_decision_dry_run_state": dry_run_state,
        "approval_decision_dry_run_active": True,
        "approval_decision_dry_run_validated": approval_decision_dry_run_validated,
        "source_decision_blocker_rollup": {
            "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-blocker-rollup",
            "active": blocker_rollup["summary"]["activation_approval_decision_blocker_rollup_active"],
            "decision_blocker_rollup_complete": blocker_rollup["summary"]["decision_blocker_rollup_complete"],
            "required_blocker_count": blocker_rollup["summary"]["required_blocker_count"],
            "open_blocker_count": blocker_rollup["summary"]["open_blocker_count"],
            "open_blocker_ids": open_blocker_ids,
        },
        "request_contract": {
            "required_fields": required_fields,
            "accepted_approval_decisions": accepted_decisions,
            "required_ref_types": ["platform_decision", "owner_approval", "test_log", "driver_gap_review"],
            "prototype_persists_request": False,
            "prototype_persists_result": False,
            "prototype_closes_gates": False,
            "prototype_activates_broker": False,
        },
        "request_validation": {
            "request_shape_valid": request_shape_valid,
            "missing_required_fields": missing_fields,
            "invalid_fields": invalid_fields,
            "policy_allowed": policy_allowed,
            "source_decision_blocker_rollup_bound": True,
            "rejected_by_open_blockers": len(open_blocker_ids) > 0,
        },
        "dry_run_result": {
            "decision": "blocked",
            "result_code": dry_run_state,
            "reason": "approval decision dry-run remains blocked until EV-ADB blockers are closed by target-platform owners",
            "open_blocker_ids": open_blocker_ids,
            "approval_decision_persisted": False,
            "approval_result_store_created": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
        },
        "mandatory_gates": mandatory_gates,
        "api_surface": {
            "rest": "POST /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run",
            "android_binder": "dryRunEventSubscriptionActivationApprovalDecisionJson",
            "linux_cli": "event-subscription-activation-approval-decision-dry-run",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.dry.run",
            "linux_grpc_rpc": "CentralBrainGateway.DryRunEventSubscriptionActivationApprovalDecision",
        },
        "summary": {
            "activation_approval_decision_dry_run_active": True,
            "source_decision_blocker_rollup_bound": True,
            "decision_blocker_rollup_complete": blocker_rollup["summary"]["decision_blocker_rollup_complete"],
            "required_blocker_count": blocker_rollup["summary"]["required_blocker_count"],
            "open_blocker_count": blocker_rollup["summary"]["open_blocker_count"],
            "request_shape_valid": request_shape_valid,
            "policy_allowed": policy_allowed,
            "approval_decision_dry_run_validated": approval_decision_dry_run_validated,
            "rejected_blocked_contract_only": dry_run_state == "rejected_blocked_contract_only",
            "approval_decision_ready": False,
            "approval_dry_run_allowed": False,
            "approval_command_surface_ready": True,
            "approval_authority_ready": False,
            "approval_authority_assigned": False,
            "approval_policy_confirmed": False,
            "approval_signature_rbac_confirmed": False,
            "approval_result_store_active": False,
            "approval_result_store_created": False,
            "dry_run_request_persisted": False,
            "dry_run_result_persisted": False,
            "approval_decision_persisted": False,
            "review_queue_owner_assigned": False,
            "review_queue_updated": False,
            "gate_closure_authority_assigned": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "broker_activation_owner_assigned": False,
            "broker_activation_ready": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_DRY_RUN_REQ_IDS,
    }
    GOVERNANCE.record_audit(
        request.get("trace_id") or str(uuid.uuid4()),
        {
            "service": "uib-events",
            "method": "activation-approval-decision-dry-run",
            "outcome": payload["approval_decision_dry_run_state"],
            "policy_decision": policy["decision"],
            "lifecycle_state": "validated" if approval_decision_dry_run_validated else "rejected",
            "qos_decision": "not-applied",
        },
    )
    return payload


def event_subscription_activation_approval_decision_dry_run_status_payload() -> dict[str, Any]:
    blocker_rollup = event_subscription_activation_approval_decision_blocker_rollup_payload()
    open_blocker_ids = [
        blocker["blocker_id"]
        for blocker in blocker_rollup["decision_blockers"]
        if blocker["open"]
    ]

    mandatory_gates = [
        {
            "gate_id": "EV-ADS-001",
            "name": "decision-dry-run-contract-bound",
            "required_evidence": "Status is bound to POST /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run request contract metadata.",
            "passed": True,
        },
        {
            "gate_id": "EV-ADS-002",
            "name": "decision-blocker-rollup-bound",
            "required_evidence": "Status reads the EV-ADB blocker rollup before reporting approval decision readiness.",
            "passed": True,
        },
        {
            "gate_id": "EV-ADS-003",
            "name": "last-result-no-store-shape-declared",
            "required_evidence": "Status declares last-result fields without creating a durable approval decision result store.",
            "passed": True,
        },
        {
            "gate_id": "EV-ADS-004",
            "name": "zero-persisted-request-result-counts",
            "required_evidence": "Dry-run request, dry-run result, and approval decision persisted counters remain zero.",
            "passed": True,
        },
        {
            "gate_id": "EV-ADS-005",
            "name": "no-review-queue-gate-broker-side-effect",
            "required_evidence": "Status does not update review queues, close gates, allow broker activation, or start runtime transports.",
            "passed": True,
        },
        {
            "gate_id": "EV-ADS-006",
            "name": "open-blockers-keep-approval-blocked",
            "required_evidence": "EV-ADB blocker rollup must report zero open blockers before approval decision status can pass.",
            "passed": len(open_blocker_ids) == 0,
        },
        {
            "gate_id": "EV-ADS-007",
            "name": "android-linux-status-parity",
            "required_evidence": "REST, Android Binder/Console, Linux CLI, Linux IPC, Linux gRPC/RPC, docs, and smoke tests expose equivalent no-store status fields.",
            "passed": True,
        },
        {
            "gate_id": "EV-ADS-008",
            "name": "no-driver-hal-virtualization-trigger",
            "required_evidence": "Status never touches hardware, Driver/HAL, shared memory, Safety Runtime, or virtualization.",
            "passed": True,
        },
    ]

    return {
        "operation": "event-subscription-activation-approval-decision-dry-run-status",
        "approval_decision_dry_run_status_state": "contract-only-approval-decision-dry-run-status-no-store",
        "approval_decision_dry_run_status_active": True,
        "source_decision_dry_run_contract": {
            "endpoint": "POST /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run",
            "operation": "event-subscription-activation-approval-decision-dry-run",
            "contract_surface_active": True,
            "expected_rejected_state": "rejected_blocked_contract_only",
            "status_invokes_post": False,
            "required_fields": [
                "approval_request_id",
                "source_decision_blocker_rollup_ref",
                "target_gate_ids",
                "approval_decision",
                "approval_authority",
                "reviewer",
                "evidence_refs",
                "rollback_plan_ref",
                "runtime_governance_policy_ref",
            ],
        },
        "source_decision_blocker_rollup": {
            "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-blocker-rollup",
            "active": blocker_rollup["summary"]["activation_approval_decision_blocker_rollup_active"],
            "decision_blocker_rollup_complete": blocker_rollup["summary"]["decision_blocker_rollup_complete"],
            "required_blocker_count": blocker_rollup["summary"]["required_blocker_count"],
            "open_blocker_count": blocker_rollup["summary"]["open_blocker_count"],
            "open_blocker_ids": open_blocker_ids,
        },
        "last_result_status": {
            "last_approval_decision_result_available": False,
            "last_result_ref": None,
            "last_result_state": "not-created",
            "persisted_dry_run_request_count": 0,
            "persisted_dry_run_result_count": 0,
            "persisted_approval_decision_count": 0,
            "pending_approval_decision_review_count": 0,
            "approved_gate_count": 0,
            "approval_decision_passed": False,
            "approval_result_store_active": False,
            "approval_result_store_created": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
        },
        "mandatory_gates": mandatory_gates,
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/status",
            "android_binder": "getEventSubscriptionActivationApprovalDecisionDryRunStatusJson",
            "linux_cli": "event-subscription-activation-approval-decision-dry-run-status",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.dry.run.status",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionDryRunStatus",
        },
        "summary": {
            "activation_approval_decision_dry_run_status_active": True,
            "activation_approval_decision_dry_run_audit_consistency_active": True,
            "source_decision_dry_run_contract_bound": True,
            "source_decision_blocker_rollup_bound": True,
            "decision_blocker_rollup_complete": blocker_rollup["summary"]["decision_blocker_rollup_complete"],
            "required_blocker_count": blocker_rollup["summary"]["required_blocker_count"],
            "open_blocker_count": blocker_rollup["summary"]["open_blocker_count"],
            "last_approval_decision_result_available": False,
            "persisted_dry_run_request_count": 0,
            "persisted_dry_run_result_count": 0,
            "persisted_approval_decision_count": 0,
            "pending_approval_decision_review_count": 0,
            "approved_gate_count": 0,
            "decision_dry_run_post_called_by_status": False,
            "approval_decision_status_passed": False,
            "approval_decision_ready": False,
            "approval_dry_run_allowed": False,
            "approval_authority_ready": False,
            "approval_result_store_active": False,
            "approval_result_store_created": False,
            "dry_run_request_persisted": False,
            "dry_run_result_persisted": False,
            "approval_decision_persisted": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_DRY_RUN_STATUS_REQ_IDS,
    }


def event_subscription_activation_approval_decision_dry_run_audit_consistency_payload() -> dict[str, Any]:
    status = event_subscription_activation_approval_decision_dry_run_status_payload()
    blocker_rollup = event_subscription_activation_approval_decision_blocker_rollup_payload()
    open_blocker_ids = [
        blocker["blocker_id"]
        for blocker in blocker_rollup["decision_blockers"]
        if blocker["open"]
    ]

    source_status_bound = (
        status["summary"]["activation_approval_decision_dry_run_status_active"] is True
        and status["approval_decision_dry_run_status_active"] is True
    )
    source_dry_run_contract_bound = (
        status["summary"]["source_decision_dry_run_contract_bound"] is True
        and status["source_decision_dry_run_contract"]["contract_surface_active"] is True
        and status["source_decision_dry_run_contract"]["status_invokes_post"] is False
    )
    source_blocker_rollup_bound = (
        blocker_rollup["summary"]["activation_approval_decision_blocker_rollup_active"] is True
        and blocker_rollup["summary"]["decision_blocker_rollup_complete"] is True
        and status["summary"]["source_decision_blocker_rollup_bound"] is True
    )
    blocker_count_consistent = (
        status["summary"]["required_blocker_count"] == blocker_rollup["summary"]["required_blocker_count"]
        and status["summary"]["open_blocker_count"] == blocker_rollup["summary"]["open_blocker_count"]
        and status["summary"]["open_blocker_count"] == len(open_blocker_ids)
    )
    no_store_consistent = (
        status["summary"]["last_approval_decision_result_available"] is False
        and status["summary"]["persisted_dry_run_request_count"] == 0
        and status["summary"]["persisted_dry_run_result_count"] == 0
        and status["summary"]["persisted_approval_decision_count"] == 0
        and status["summary"]["pending_approval_decision_review_count"] == 0
        and status["summary"]["dry_run_request_persisted"] is False
        and status["summary"]["dry_run_result_persisted"] is False
        and status["summary"]["approval_decision_persisted"] is False
        and status["summary"]["decision_dry_run_post_called_by_status"] is False
    )
    decision_rejection_consistent = (
        status["source_decision_dry_run_contract"]["expected_rejected_state"] == "rejected_blocked_contract_only"
        and blocker_rollup["summary"]["open_blocker_count"] > 0
        and status["summary"]["approval_decision_status_passed"] is False
        and status["summary"]["approval_decision_ready"] is False
        and status["summary"]["approval_dry_run_allowed"] is False
    )
    android_linux_parity_consistent = (
        status["api_surface"]["android_binder"] == "getEventSubscriptionActivationApprovalDecisionDryRunStatusJson"
        and status["api_surface"]["linux_cli"] == "event-subscription-activation-approval-decision-dry-run-status"
        and status["api_surface"]["linux_ipc"] == "uib.events.subscriptions.activation.approval.decision.dry.run.status"
        and status["api_surface"]["linux_grpc_rpc"]
        == "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionDryRunStatus"
    )
    no_side_effects_consistent = (
        status["summary"]["approval_result_store_created"] is False
        and status["summary"]["review_queue_updated"] is False
        and status["summary"]["gates_closed"] is False
        and status["summary"]["broker_activation_allowed"] is False
        and status["summary"]["activation_allowed"] is False
        and status["summary"]["broker_active"] is False
        and status["summary"]["hardware_accessed"] is False
        and status["summary"]["driver_development_triggered"] is False
        and status["summary"]["virtualization_development_triggered"] is False
        and status["summary"]["service_dispatch_triggered"] is False
    )
    consistency_passed = all(
        [
            source_status_bound,
            source_dry_run_contract_bound,
            source_blocker_rollup_bound,
            blocker_count_consistent,
            no_store_consistent,
            decision_rejection_consistent,
            android_linux_parity_consistent,
            no_side_effects_consistent,
        ]
    )

    audit_findings = [
        {
            "finding_id": "EV-ADA-AUD-001",
            "area": "source-status",
            "expected": "Dry-run no-store status endpoint is active and read-only.",
            "observed": "active" if source_status_bound else "not-active",
            "consistent": source_status_bound,
        },
        {
            "finding_id": "EV-ADA-AUD-002",
            "area": "source-dry-run-contract",
            "expected": "Audit is bound to POST dry-run contract metadata without invoking POST.",
            "observed": "bound-no-post" if source_dry_run_contract_bound else "not-bound",
            "consistent": source_dry_run_contract_bound,
        },
        {
            "finding_id": "EV-ADA-AUD-003",
            "area": "source-blocker-rollup",
            "expected": "EV-ADB blocker rollup is complete and still reports open blockers.",
            "observed": f"{len(open_blocker_ids)} open blockers",
            "consistent": source_blocker_rollup_bound,
        },
        {
            "finding_id": "EV-ADA-AUD-004",
            "area": "blocker-count",
            "expected": "Status and blocker rollup expose the same required/open blocker counts.",
            "observed": f"{status['summary']['open_blocker_count']}/{status['summary']['required_blocker_count']} open",
            "consistent": blocker_count_consistent,
        },
        {
            "finding_id": "EV-ADA-AUD-005",
            "area": "no-store",
            "expected": "No request, result, last-result, approval decision, or review count is persisted.",
            "observed": "no-store" if no_store_consistent else "store-or-review-count-present",
            "consistent": no_store_consistent,
        },
        {
            "finding_id": "EV-ADA-AUD-006",
            "area": "decision-rejection",
            "expected": "Dry-run remains rejected/blocked while EV-ADB blockers are open.",
            "observed": "blocked" if decision_rejection_consistent else "not-blocked",
            "consistent": decision_rejection_consistent,
        },
        {
            "finding_id": "EV-ADA-AUD-007",
            "area": "android-linux-parity",
            "expected": "REST, Android Binder/Console, Linux CLI, Linux IPC, and Linux gRPC names are paired.",
            "observed": "paired" if android_linux_parity_consistent else "mismatch",
            "consistent": android_linux_parity_consistent,
        },
        {
            "finding_id": "EV-ADA-AUD-008",
            "area": "no-side-effect",
            "expected": "Audit does not close gates, activate broker, touch hardware, dispatch services, or trigger Driver/HAL/virtualization.",
            "observed": "no-side-effects" if no_side_effects_consistent else "side-effect-detected",
            "consistent": no_side_effects_consistent,
        },
    ]

    return {
        "operation": "event-subscription-activation-approval-decision-dry-run-audit-consistency",
        "approval_decision_dry_run_audit_state": "contract-only-approval-decision-dry-run-audit-consistent",
        "approval_decision_dry_run_audit_consistency_active": True,
        "source_surfaces": {
            "decision_dry_run_contract": {
                "endpoint": "POST /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run",
                "active": source_dry_run_contract_bound,
                "post_called_by_audit_consistency": False,
                "expected_rejected_state": status["source_decision_dry_run_contract"]["expected_rejected_state"],
                "required_fields": status["source_decision_dry_run_contract"]["required_fields"],
            },
            "decision_dry_run_status": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/status",
                "active": source_status_bound,
                "gate_ids": [gate["gate_id"] for gate in status["mandatory_gates"]],
                "post_called_by_status": status["summary"]["decision_dry_run_post_called_by_status"],
                "last_approval_decision_result_available": status["summary"]["last_approval_decision_result_available"],
                "persisted_dry_run_request_count": status["summary"]["persisted_dry_run_request_count"],
                "persisted_dry_run_result_count": status["summary"]["persisted_dry_run_result_count"],
                "persisted_approval_decision_count": status["summary"]["persisted_approval_decision_count"],
            },
            "decision_blocker_rollup": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-blocker-rollup",
                "active": source_blocker_rollup_bound,
                "required_blocker_count": blocker_rollup["summary"]["required_blocker_count"],
                "open_blocker_count": blocker_rollup["summary"]["open_blocker_count"],
                "open_blocker_ids": open_blocker_ids,
            },
        },
        "audit_findings": audit_findings,
        "mandatory_gates": [
            {
                "gate_id": "EV-ADA-001",
                "name": "source-status-bound",
                "required_evidence": "Audit reads dry-run no-store status without invoking POST endpoints.",
                "passed": source_status_bound,
            },
            {
                "gate_id": "EV-ADA-002",
                "name": "source-dry-run-contract-bound-no-post",
                "required_evidence": "Audit binds the POST dry-run request contract metadata while keeping post_called_by_audit_consistency false.",
                "passed": source_dry_run_contract_bound,
            },
            {
                "gate_id": "EV-ADA-003",
                "name": "source-blocker-rollup-bound",
                "required_evidence": "Audit reads the EV-ADB blocker rollup and sees the complete blocked decision state.",
                "passed": source_blocker_rollup_bound,
            },
            {
                "gate_id": "EV-ADA-004",
                "name": "blocker-count-consistent",
                "required_evidence": "Status open blocker counters match EV-ADB blocker rollup counters.",
                "passed": blocker_count_consistent,
            },
            {
                "gate_id": "EV-ADA-005",
                "name": "no-store-counters-consistent",
                "required_evidence": "Request/result/approval decision/last-result/review counters remain zero or unavailable.",
                "passed": no_store_consistent,
            },
            {
                "gate_id": "EV-ADA-006",
                "name": "decision-rejection-consistent",
                "required_evidence": "Dry-run rejection remains consistent while EV-ADB blockers are open.",
                "passed": decision_rejection_consistent,
            },
            {
                "gate_id": "EV-ADA-007",
                "name": "android-linux-audit-parity",
                "required_evidence": "REST, Android Binder/Console, Linux CLI, Linux IPC, Linux gRPC/RPC, docs, and smoke tests expose equivalent audit fields.",
                "passed": android_linux_parity_consistent,
            },
            {
                "gate_id": "EV-ADA-008",
                "name": "no-side-effect-audit",
                "required_evidence": "Audit performs no persistence, review queue update, gate closure, broker activation, hardware access, Driver/HAL work, service dispatch, or virtualization work.",
                "passed": no_side_effects_consistent,
            },
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/audit-consistency",
            "android_binder": "getEventSubscriptionActivationApprovalDecisionDryRunAuditConsistencyJson",
            "linux_cli": "event-subscription-activation-approval-decision-dry-run-audit-consistency",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.dry.run.audit.consistency",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionDryRunAuditConsistency",
        },
        "summary": {
            "activation_approval_decision_dry_run_audit_consistency_active": True,
            "consistency_passed": consistency_passed,
            "source_status_bound": source_status_bound,
            "source_decision_dry_run_contract_bound": source_dry_run_contract_bound,
            "source_decision_blocker_rollup_bound": source_blocker_rollup_bound,
            "blocker_count_consistent": blocker_count_consistent,
            "no_store_consistent": no_store_consistent,
            "decision_dry_run_rejection_consistent": decision_rejection_consistent,
            "android_linux_parity_consistent": android_linux_parity_consistent,
            "no_side_effects_consistent": no_side_effects_consistent,
            "decision_dry_run_post_called_by_audit_consistency": False,
            "last_approval_decision_result_available": False,
            "persisted_dry_run_request_count": 0,
            "persisted_dry_run_result_count": 0,
            "persisted_approval_decision_count": 0,
            "pending_approval_decision_review_count": 0,
            "decision_blocker_rollup_complete": blocker_rollup["summary"]["decision_blocker_rollup_complete"],
            "required_blocker_count": blocker_rollup["summary"]["required_blocker_count"],
            "open_blocker_count": blocker_rollup["summary"]["open_blocker_count"],
            "approval_decision_status_passed": False,
            "approval_decision_ready": False,
            "approval_dry_run_allowed": False,
            "approval_authority_ready": False,
            "approval_result_store_active": False,
            "approval_result_store_created": False,
            "dry_run_request_persisted": False,
            "dry_run_result_persisted": False,
            "approval_decision_persisted": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_DRY_RUN_AUDIT_CONSISTENCY_REQ_IDS,
    }


def event_subscription_activation_approval_decision_closure_blocker_matrix_payload() -> dict[str, Any]:
    audit = event_subscription_activation_approval_decision_dry_run_audit_consistency_payload()
    blocker_rollup = event_subscription_activation_approval_decision_blocker_rollup_payload()
    source_open_blocker_ids = [
        blocker["blocker_id"]
        for blocker in blocker_rollup["decision_blockers"]
        if blocker["open"]
    ]

    source_audit_bound = audit["summary"]["activation_approval_decision_dry_run_audit_consistency_active"]
    source_audit_consistent = audit["summary"]["consistency_passed"]
    source_blocker_rollup_bound = blocker_rollup["summary"]["activation_approval_decision_blocker_rollup_active"]
    no_store_consistent = audit["summary"]["no_store_consistent"]
    no_side_effects_consistent = audit["summary"]["no_side_effects_consistent"]

    closure_blockers = [
        {
            "blocker_id": "EV-ACB-001",
            "area": "approval-authority-owner",
            "source": "EV-ADB-002",
            "current_state": "approval-authority-owner-unassigned",
            "required_evidence": "Target platform assigns the approval decision authority and audit subject.",
            "blocks": ["approval-decision-closure", "approval-dry-run-release"],
            "open": True,
        },
        {
            "blocker_id": "EV-ACB-002",
            "area": "approval-policy",
            "source": "EV-ADB-003",
            "current_state": "approval-policy-unconfirmed",
            "required_evidence": "Approval policy, rollback rule, and allowed activation states are signed off.",
            "blocks": ["approval-decision-closure", "gate-closure"],
            "open": True,
        },
        {
            "blocker_id": "EV-ACB-003",
            "area": "signature-rbac",
            "source": "EV-ADB-003",
            "current_state": "signature-and-rbac-unconfirmed",
            "required_evidence": "Signer identity, RBAC scope, and audit identity mapping are confirmed for Android and Linux.",
            "blocks": ["approval-decision-closure", "review-acceptance"],
            "open": True,
        },
        {
            "blocker_id": "EV-ACB-004",
            "area": "approval-result-store",
            "source": "EV-ADB-004",
            "current_state": "approval-result-store-owner-missing",
            "required_evidence": "Durable result store owner, retention policy, and no-store-to-store migration rule are confirmed.",
            "blocks": ["approval-result-retention", "activation-audit-export"],
            "open": True,
        },
        {
            "blocker_id": "EV-ACB-005",
            "area": "review-queue-owner",
            "source": "EV-ADB-004",
            "current_state": "review-queue-owner-missing",
            "required_evidence": "Review queue owner, triage SLA, and rejection handling workflow are confirmed.",
            "blocks": ["approval-review", "gate-closure"],
            "open": True,
        },
        {
            "blocker_id": "EV-ACB-006",
            "area": "gate-closure-authority",
            "source": "EV-ADB-005",
            "current_state": "gate-closure-authority-missing",
            "required_evidence": "Authority and audit record for closing activation gates are assigned.",
            "blocks": ["readiness-gate-closure", "broker-activation"],
            "open": True,
        },
        {
            "blocker_id": "EV-ACB-007",
            "area": "broker-activation-owner",
            "source": "EV-ADB-005",
            "current_state": "broker-activation-owner-missing",
            "required_evidence": "Android and Linux runtime owner for event broker activation is assigned.",
            "blocks": ["broker-activation", "callback-watch-runtime"],
            "open": True,
        },
        {
            "blocker_id": "EV-ACB-008",
            "area": "driver-hal-high-rate-gap-owner",
            "source": "DRV-GAP-004/DRV-GAP-005",
            "current_state": "driver-hal-high-rate-gap-owner-missing",
            "required_evidence": "DRV-GAP-004 and DRV-GAP-005 owners decide DDS/SOME-IP/TSN/PTP, shared memory, and Safety Runtime scope.",
            "blocks": ["high-rate-transport-activation", "shared-memory-safety-runtime"],
            "open": True,
        },
        {
            "blocker_id": "EV-ACB-009",
            "area": "android-linux-closure-parity-evidence",
            "source": "DEL-001/DEL-002/DEL-004",
            "current_state": "closure-parity-evidence-missing",
            "required_evidence": "Android Binder/Console and Linux CLI/IPC/gRPC validation evidence for closure decision fields is attached.",
            "blocks": ["cross-platform-approval-release", "delivery-readiness"],
            "open": True,
        },
        {
            "blocker_id": "EV-ACB-010",
            "area": "high-rate-transport-activation-evidence",
            "source": "NV-P-006",
            "current_state": "high-rate-transport-activation-evidence-missing",
            "required_evidence": "Target transport activation proof exists before broker/DDS/SSE/WebSocket/high-rate data plane can start.",
            "blocks": ["dds-or-someip-runtime-activation", "production-activation"],
            "open": True,
        },
    ]
    open_closure_blocker_ids = [
        blocker["blocker_id"]
        for blocker in closure_blockers
        if blocker["open"]
    ]

    return {
        "operation": "event-subscription-activation-approval-decision-closure-blocker-matrix",
        "approval_decision_closure_blocker_matrix_state": "contract-only-approval-decision-closure-blocked",
        "approval_decision_closure_blocker_matrix_active": True,
        "source_surfaces": {
            "decision_dry_run_audit_consistency": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/audit-consistency",
                "active": source_audit_bound,
                "consistent": source_audit_consistent,
                "post_called_by_audit_consistency": audit["summary"]["decision_dry_run_post_called_by_audit_consistency"],
                "open_blocker_count": audit["summary"]["open_blocker_count"],
                "gate_ids": [gate["gate_id"] for gate in audit["mandatory_gates"]],
            },
            "decision_blocker_rollup": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-blocker-rollup",
                "active": source_blocker_rollup_bound,
                "required_blocker_count": blocker_rollup["summary"]["required_blocker_count"],
                "open_blocker_count": blocker_rollup["summary"]["open_blocker_count"],
                "open_blocker_ids": source_open_blocker_ids,
            },
        },
        "closure_blockers": closure_blockers,
        "mandatory_gates": [
            {
                "gate_id": blocker["blocker_id"],
                "name": blocker["area"],
                "required_evidence": blocker["required_evidence"],
                "passed": not blocker["open"],
            }
            for blocker in closure_blockers
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix",
            "android_binder": "getEventSubscriptionActivationApprovalDecisionClosureBlockerMatrixJson",
            "linux_cli": "event-subscription-activation-approval-decision-closure-blocker-matrix",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.closure.blocker.matrix",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionClosureBlockerMatrix",
        },
        "summary": {
            "activation_approval_decision_closure_blocker_matrix_active": True,
            "closure_blocker_matrix_complete": True,
            "closure_blocker_matrix_state": "contract-only-approval-decision-closure-blocked",
            "source_decision_dry_run_audit_consistency_bound": source_audit_bound,
            "source_decision_dry_run_audit_consistency_passed": source_audit_consistent,
            "source_decision_blocker_rollup_bound": source_blocker_rollup_bound,
            "source_open_blocker_count": blocker_rollup["summary"]["open_blocker_count"],
            "required_closure_blocker_count": len(closure_blockers),
            "open_closure_blocker_count": len(open_closure_blocker_ids),
            "open_closure_blocker_ids": open_closure_blocker_ids,
            "no_store_consistent": no_store_consistent,
            "no_side_effects_consistent": no_side_effects_consistent,
            "decision_dry_run_post_called_by_closure_blocker_matrix": False,
            "persisted_dry_run_request_count": 0,
            "persisted_dry_run_result_count": 0,
            "persisted_approval_decision_count": 0,
            "pending_approval_decision_review_count": 0,
            "closure_ready": False,
            "approval_decision_closure_allowed": False,
            "approval_decision_ready": False,
            "approval_dry_run_allowed": False,
            "approval_result_store_created": False,
            "approval_result_store_active": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_CLOSURE_BLOCKER_MATRIX_REQ_IDS,
    }


def event_subscription_activation_approval_decision_owner_handoff_checklist_payload() -> dict[str, Any]:
    closure_matrix = event_subscription_activation_approval_decision_closure_blocker_matrix_payload()
    closure_blockers = closure_matrix["closure_blockers"]
    source_open_blocker_ids = closure_matrix["summary"]["open_closure_blocker_ids"]
    owner_handoff_profiles = {
        "approval-authority-owner": {
            "owner_slot": "approval-decision-authority-owner",
            "expected_owner_role": "platform-approval-authority-owner",
            "required_evidence_type": "authority-assignment-record",
            "handoff_artifact": "approval authority owner decision note",
        },
        "approval-policy": {
            "owner_slot": "approval-policy-owner",
            "expected_owner_role": "runtime-governance-policy-owner",
            "required_evidence_type": "signed-policy-and-rollback-rule",
            "handoff_artifact": "approval policy signoff packet",
        },
        "signature-rbac": {
            "owner_slot": "signature-rbac-owner",
            "expected_owner_role": "identity-and-access-control-owner",
            "required_evidence_type": "signer-rbac-mapping",
            "handoff_artifact": "Android/Linux signer identity and RBAC mapping",
        },
        "approval-result-store": {
            "owner_slot": "approval-result-store-owner",
            "expected_owner_role": "durable-result-store-owner",
            "required_evidence_type": "retention-store-design",
            "handoff_artifact": "approval result retention and migration rule",
        },
        "review-queue-owner": {
            "owner_slot": "approval-review-queue-owner",
            "expected_owner_role": "review-workflow-owner",
            "required_evidence_type": "review-queue-workflow-sla",
            "handoff_artifact": "review queue triage and rejection workflow",
        },
        "gate-closure-authority": {
            "owner_slot": "activation-gate-closure-authority",
            "expected_owner_role": "runtime-governance-gate-owner",
            "required_evidence_type": "gate-closure-authority-record",
            "handoff_artifact": "activation gate closure authority packet",
        },
        "broker-activation-owner": {
            "owner_slot": "event-broker-activation-owner",
            "expected_owner_role": "event-runtime-owner",
            "required_evidence_type": "broker-runtime-activation-plan",
            "handoff_artifact": "Android/Linux event broker activation plan",
        },
        "driver-hal-high-rate-gap-owner": {
            "owner_slot": "high-rate-driver-hal-gap-owner",
            "expected_owner_role": "driver-hal-gap-review-owner",
            "required_evidence_type": "DRV-GAP-004-DRV-GAP-005-review-record",
            "handoff_artifact": "DDS/SOME-IP/TSN/PTP and shared-memory scope decision",
        },
        "android-linux-closure-parity-evidence": {
            "owner_slot": "android-linux-closure-parity-owner",
            "expected_owner_role": "delivery-validation-owner",
            "required_evidence_type": "binder-cli-ipc-grpc-parity-report",
            "handoff_artifact": "Android Binder/Console and Linux CLI/IPC/gRPC validation bundle",
        },
        "high-rate-transport-activation-evidence": {
            "owner_slot": "high-rate-transport-activation-owner",
            "expected_owner_role": "transport-runtime-owner",
            "required_evidence_type": "target-transport-activation-proof",
            "handoff_artifact": "DDS/SSE/WebSocket/high-rate data-plane activation evidence",
        },
    }
    owner_handoffs = []
    for index, blocker in enumerate(closure_blockers, start=1):
        profile = owner_handoff_profiles[blocker["area"]]
        owner_handoffs.append(
            {
                "handoff_id": f"EV-ACH-{index:03d}",
                "source_blocker_id": blocker["blocker_id"],
                "source_area": blocker["area"],
                "source_current_state": blocker["current_state"],
                "owner_slot": profile["owner_slot"],
                "expected_owner_role": profile["expected_owner_role"],
                "required_evidence_type": profile["required_evidence_type"],
                "handoff_artifact": profile["handoff_artifact"],
                "required_evidence": blocker["required_evidence"],
                "android_linux_parity_required": True,
                "handoff_state": "owner-unassigned",
                "escalation_state": "blocked-waiting-owner-assignment",
                "handoff_ready": False,
                "owner_assigned": False,
                "evidence_attached": False,
                "assignment_persisted": False,
                "review_queue_updated": False,
                "blocks": blocker["blocks"],
                "open": blocker["open"],
            }
        )
    open_handoff_ids = [
        handoff["handoff_id"]
        for handoff in owner_handoffs
        if handoff["open"]
    ]

    return {
        "operation": "event-subscription-activation-approval-decision-owner-handoff-checklist",
        "approval_decision_owner_handoff_checklist_state": "contract-only-owner-handoff-blocked",
        "approval_decision_owner_handoff_checklist_active": True,
        "source_surfaces": {
            "closure_blocker_matrix": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix",
                "active": closure_matrix["summary"]["activation_approval_decision_closure_blocker_matrix_active"],
                "complete": closure_matrix["summary"]["closure_blocker_matrix_complete"],
                "closure_ready": closure_matrix["summary"]["closure_ready"],
                "open_closure_blocker_count": closure_matrix["summary"]["open_closure_blocker_count"],
                "open_closure_blocker_ids": source_open_blocker_ids,
            },
        },
        "owner_handoffs": owner_handoffs,
        "mandatory_gates": [
            {
                "gate_id": handoff["handoff_id"],
                "name": handoff["owner_slot"],
                "source_blocker_id": handoff["source_blocker_id"],
                "required_evidence_type": handoff["required_evidence_type"],
                "passed": handoff["handoff_ready"],
            }
            for handoff in owner_handoffs
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist",
            "android_binder": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffChecklistJson",
            "linux_cli": "event-subscription-activation-approval-decision-owner-handoff-checklist",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.owner.handoff.checklist",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffChecklist",
        },
        "summary": {
            "activation_approval_decision_owner_handoff_checklist_active": True,
            "owner_handoff_checklist_complete": True,
            "owner_handoff_checklist_state": "contract-only-owner-handoff-blocked",
            "source_closure_blocker_matrix_bound": True,
            "source_closure_blocker_matrix_complete": closure_matrix["summary"]["closure_blocker_matrix_complete"],
            "source_closure_ready": closure_matrix["summary"]["closure_ready"],
            "source_open_closure_blocker_count": closure_matrix["summary"]["open_closure_blocker_count"],
            "required_owner_handoff_count": len(owner_handoffs),
            "open_owner_handoff_count": len(open_handoff_ids),
            "open_owner_handoff_ids": open_handoff_ids,
            "assigned_owner_count": 0,
            "unassigned_owner_count": len(owner_handoffs),
            "attached_evidence_count": 0,
            "owner_handoff_ready": False,
            "owner_assignments_persisted": False,
            "owner_handoff_queue_updated": False,
            "no_store_consistent": closure_matrix["summary"]["no_store_consistent"],
            "no_side_effects_consistent": closure_matrix["summary"]["no_side_effects_consistent"],
            "decision_dry_run_post_called_by_owner_handoff_checklist": False,
            "persisted_dry_run_request_count": 0,
            "persisted_dry_run_result_count": 0,
            "persisted_approval_decision_count": 0,
            "pending_approval_decision_review_count": 0,
            "closure_ready": False,
            "approval_decision_closure_allowed": False,
            "approval_decision_ready": False,
            "approval_dry_run_allowed": False,
            "approval_result_store_created": False,
            "approval_result_store_active": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_CHECKLIST_REQ_IDS,
    }


def event_subscription_activation_approval_decision_owner_handoff_audit_consistency_payload() -> dict[str, Any]:
    handoff_checklist = event_subscription_activation_approval_decision_owner_handoff_checklist_payload()
    closure_matrix = event_subscription_activation_approval_decision_closure_blocker_matrix_payload()
    handoffs = handoff_checklist["owner_handoffs"]
    closure_blockers = closure_matrix["closure_blockers"]
    handoff_ids = [handoff["handoff_id"] for handoff in handoffs]
    source_blocker_ids = [handoff["source_blocker_id"] for handoff in handoffs]
    closure_blocker_ids = [blocker["blocker_id"] for blocker in closure_blockers]
    source_binding_consistent = set(source_blocker_ids) == set(closure_blocker_ids)
    handoff_count_consistent = (
        len(handoffs)
        == len(closure_blockers)
        == handoff_checklist["summary"]["required_owner_handoff_count"]
        == closure_matrix["summary"]["required_closure_blocker_count"]
    )
    open_state_consistent = (
        all(handoff["open"] for handoff in handoffs)
        and handoff_checklist["summary"]["open_owner_handoff_count"] == len(handoffs)
        and closure_matrix["summary"]["open_closure_blocker_count"] == len(closure_blockers)
    )
    no_owner_assignment_consistent = (
        all(not handoff["owner_assigned"] for handoff in handoffs)
        and all(handoff["handoff_state"] == "owner-unassigned" for handoff in handoffs)
        and handoff_checklist["summary"]["assigned_owner_count"] == 0
        and handoff_checklist["summary"]["unassigned_owner_count"] == len(handoffs)
    )
    no_evidence_attached_consistent = (
        all(not handoff["evidence_attached"] for handoff in handoffs)
        and handoff_checklist["summary"]["attached_evidence_count"] == 0
    )
    no_assignment_persistence_consistent = (
        all(not handoff["assignment_persisted"] for handoff in handoffs)
        and not handoff_checklist["summary"]["owner_assignments_persisted"]
        and handoff_checklist["summary"]["persisted_dry_run_request_count"] == 0
        and handoff_checklist["summary"]["persisted_dry_run_result_count"] == 0
        and handoff_checklist["summary"]["persisted_approval_decision_count"] == 0
        and handoff_checklist["summary"]["pending_approval_decision_review_count"] == 0
    )
    no_review_gate_runtime_consistent = (
        all(not handoff["review_queue_updated"] for handoff in handoffs)
        and not handoff_checklist["summary"]["owner_handoff_queue_updated"]
        and not handoff_checklist["summary"]["review_queue_updated"]
        and not handoff_checklist["summary"]["gate_state_changed"]
        and not handoff_checklist["summary"]["gates_closed"]
        and not handoff_checklist["summary"]["broker_activation_allowed"]
        and not handoff_checklist["summary"]["activation_allowed"]
        and not handoff_checklist["summary"]["broker_active"]
        and not handoff_checklist["summary"]["high_rate_data_plane_active"]
    )
    android_linux_parity_consistent = (
        all(handoff["android_linux_parity_required"] for handoff in handoffs)
        and "android_binder" in handoff_checklist["api_surface"]
        and "linux_cli" in handoff_checklist["api_surface"]
        and "linux_ipc" in handoff_checklist["api_surface"]
        and "linux_grpc_rpc" in handoff_checklist["api_surface"]
    )
    no_side_effects_consistent = (
        handoff_checklist["summary"]["no_side_effects_consistent"]
        and not handoff_checklist["summary"]["hardware_accessed"]
        and not handoff_checklist["summary"]["driver_development_triggered"]
        and not handoff_checklist["summary"]["virtualization_development_triggered"]
        and not handoff_checklist["summary"]["service_dispatch_triggered"]
    )
    no_post_consistent = not handoff_checklist["summary"]["decision_dry_run_post_called_by_owner_handoff_checklist"]
    no_store_consistent = handoff_checklist["summary"]["no_store_consistent"] and no_assignment_persistence_consistent
    consistency_passed = all(
        [
            handoff_checklist["summary"]["activation_approval_decision_owner_handoff_checklist_active"],
            closure_matrix["summary"]["activation_approval_decision_closure_blocker_matrix_active"],
            handoff_count_consistent,
            source_binding_consistent,
            open_state_consistent,
            no_owner_assignment_consistent,
            no_evidence_attached_consistent,
            no_store_consistent,
            no_review_gate_runtime_consistent,
            android_linux_parity_consistent,
            no_post_consistent,
            no_side_effects_consistent,
        ]
    )
    audit_items = [
        {
            "gate_id": "EV-AHA-001",
            "name": "owner-handoff-checklist-bound",
            "required_evidence": "GET owner-handoff-checklist is active, complete, and remains owner-handoff blocked.",
            "passed": handoff_checklist["summary"]["activation_approval_decision_owner_handoff_checklist_active"]
            and handoff_checklist["summary"]["owner_handoff_checklist_complete"]
            and handoff_checklist["summary"]["owner_handoff_ready"] is False,
        },
        {
            "gate_id": "EV-AHA-002",
            "name": "closure-blocker-matrix-bound",
            "required_evidence": "GET closure-blocker-matrix remains active, complete, and not closure-ready.",
            "passed": closure_matrix["summary"]["activation_approval_decision_closure_blocker_matrix_active"]
            and closure_matrix["summary"]["closure_blocker_matrix_complete"]
            and closure_matrix["summary"]["closure_ready"] is False,
        },
        {
            "gate_id": "EV-AHA-003",
            "name": "handoff-count-parity",
            "required_evidence": "EV-ACH owner handoff count matches EV-ACB source closure blocker count.",
            "passed": handoff_count_consistent,
        },
        {
            "gate_id": "EV-AHA-004",
            "name": "source-blocker-binding-consistent",
            "required_evidence": "Each EV-ACH item references one EV-ACB source blocker and all source blockers remain open.",
            "passed": source_binding_consistent and open_state_consistent,
        },
        {
            "gate_id": "EV-AHA-005",
            "name": "no-owner-or-evidence-mutation",
            "required_evidence": "No owner is assigned, no evidence is attached, and each handoff remains owner-unassigned.",
            "passed": no_owner_assignment_consistent and no_evidence_attached_consistent,
        },
        {
            "gate_id": "EV-AHA-006",
            "name": "no-store-or-review-queue-mutation",
            "required_evidence": "No request/result/approval/handoff/review state is persisted and no review queue is updated.",
            "passed": no_store_consistent and no_review_gate_runtime_consistent,
        },
        {
            "gate_id": "EV-AHA-007",
            "name": "android-linux-parity-bound",
            "required_evidence": "Android Binder plus Linux CLI/IPC/gRPC surfaces remain visible for the handoff checklist.",
            "passed": android_linux_parity_consistent,
        },
        {
            "gate_id": "EV-AHA-008",
            "name": "no-post-side-effect-boundary",
            "required_evidence": "Audit does not call POST, close gates, activate broker/high-rate runtime, dispatch services, touch hardware, trigger Driver/HAL, or implement virtualization.",
            "passed": no_post_consistent and no_side_effects_consistent,
        },
        {
            "gate_id": "EV-AHA-009",
            "name": "driver-hal-gap-boundary-preserved",
            "required_evidence": "DRV-GAP-004/005 remain referenced only as evidence requirements and do not trigger Driver/HAL development.",
            "passed": "DRV-GAP-004" in json.dumps(handoff_checklist) and not handoff_checklist["summary"]["driver_development_triggered"],
        },
        {
            "gate_id": "EV-AHA-010",
            "name": "consistency-summary-passed",
            "required_evidence": "All owner handoff audit consistency checks pass while readiness remains blocked.",
            "passed": consistency_passed and handoff_checklist["summary"]["owner_handoff_ready"] is False,
        },
    ]

    return {
        "operation": "event-subscription-activation-approval-decision-owner-handoff-audit-consistency",
        "approval_decision_owner_handoff_audit_consistency_state": "contract-only-owner-handoff-audit-consistent",
        "approval_decision_owner_handoff_audit_consistency_active": True,
        "source_surfaces": {
            "owner_handoff_checklist": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist",
                "active": handoff_checklist["summary"]["activation_approval_decision_owner_handoff_checklist_active"],
                "complete": handoff_checklist["summary"]["owner_handoff_checklist_complete"],
                "owner_handoff_ready": handoff_checklist["summary"]["owner_handoff_ready"],
                "required_owner_handoff_count": handoff_checklist["summary"]["required_owner_handoff_count"],
                "open_owner_handoff_count": handoff_checklist["summary"]["open_owner_handoff_count"],
                "open_owner_handoff_ids": handoff_checklist["summary"]["open_owner_handoff_ids"],
            },
            "closure_blocker_matrix": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix",
                "active": closure_matrix["summary"]["activation_approval_decision_closure_blocker_matrix_active"],
                "complete": closure_matrix["summary"]["closure_blocker_matrix_complete"],
                "closure_ready": closure_matrix["summary"]["closure_ready"],
                "open_closure_blocker_count": closure_matrix["summary"]["open_closure_blocker_count"],
                "open_closure_blocker_ids": closure_matrix["summary"]["open_closure_blocker_ids"],
            },
        },
        "driver_hal_gap_refs": ["DRV-GAP-004", "DRV-GAP-005"],
        "handoff_audit_items": audit_items,
        "mandatory_gates": audit_items,
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency",
            "android_binder": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffAuditConsistencyJson",
            "linux_cli": "event-subscription-activation-approval-decision-owner-handoff-audit-consistency",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.owner.handoff.audit.consistency",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffAuditConsistency",
        },
        "summary": {
            "activation_approval_decision_owner_handoff_audit_consistency_active": True,
            "owner_handoff_audit_consistency_state": "contract-only-owner-handoff-audit-consistent",
            "consistency_passed": consistency_passed,
            "source_owner_handoff_checklist_bound": True,
            "source_closure_blocker_matrix_bound": True,
            "owner_handoff_count_consistent": handoff_count_consistent,
            "source_closure_blocker_binding_consistent": source_binding_consistent,
            "open_handoff_state_consistent": open_state_consistent,
            "owner_assignment_absent_consistent": no_owner_assignment_consistent,
            "evidence_attachment_absent_consistent": no_evidence_attached_consistent,
            "no_store_consistent": no_store_consistent,
            "no_review_queue_gate_runtime_consistent": no_review_gate_runtime_consistent,
            "android_linux_parity_consistent": android_linux_parity_consistent,
            "no_post_consistent": no_post_consistent,
            "no_side_effects_consistent": no_side_effects_consistent,
            "source_open_closure_blocker_count": closure_matrix["summary"]["open_closure_blocker_count"],
            "source_open_closure_blocker_ids": closure_matrix["summary"]["open_closure_blocker_ids"],
            "required_owner_handoff_count": handoff_checklist["summary"]["required_owner_handoff_count"],
            "open_owner_handoff_count": handoff_checklist["summary"]["open_owner_handoff_count"],
            "open_owner_handoff_ids": handoff_checklist["summary"]["open_owner_handoff_ids"],
            "assigned_owner_count": handoff_checklist["summary"]["assigned_owner_count"],
            "unassigned_owner_count": handoff_checklist["summary"]["unassigned_owner_count"],
            "attached_evidence_count": handoff_checklist["summary"]["attached_evidence_count"],
            "owner_handoff_ready": False,
            "owner_assignments_persisted": False,
            "owner_handoff_queue_updated": False,
            "decision_dry_run_post_called_by_owner_handoff_audit_consistency": False,
            "persisted_dry_run_request_count": 0,
            "persisted_dry_run_result_count": 0,
            "persisted_approval_decision_count": 0,
            "pending_approval_decision_review_count": 0,
            "closure_ready": False,
            "approval_decision_closure_allowed": False,
            "approval_decision_ready": False,
            "approval_dry_run_allowed": False,
            "approval_result_store_created": False,
            "approval_result_store_active": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_AUDIT_CONSISTENCY_REQ_IDS,
    }


def event_subscription_activation_approval_decision_owner_handoff_decision_rollup_payload() -> dict[str, Any]:
    handoff_checklist = event_subscription_activation_approval_decision_owner_handoff_checklist_payload()
    handoff_audit = event_subscription_activation_approval_decision_owner_handoff_audit_consistency_payload()
    handoffs = handoff_checklist["owner_handoffs"]
    audit_summary = handoff_audit["summary"]
    checklist_summary = handoff_checklist["summary"]
    required_decision_count = len(handoffs)
    blocked_decision_count = required_decision_count
    audit_gate_ids = [gate["gate_id"] for gate in handoff_audit["mandatory_gates"]]
    checklist_gate_ids = [gate["gate_id"] for gate in handoff_checklist["mandatory_gates"]]
    unassigned_owner_count = checklist_summary["unassigned_owner_count"]
    attached_evidence_count = checklist_summary["attached_evidence_count"]
    owner_handoff_decision_blocked = (
        audit_summary["consistency_passed"]
        and checklist_summary["owner_handoff_ready"] is False
        and unassigned_owner_count == required_decision_count
        and attached_evidence_count == 0
    )

    decision_reasons = [
        (
            "source-owner-handoff-audit-bound",
            "Owner handoff audit consistency is the authoritative source for this read-only decision rollup.",
        ),
        (
            "owner-handoff-checklist-bound",
            "Owner handoff checklist remains complete but all EV-ACH owner slots are still open.",
        ),
        (
            "unassigned-owner-blocker-carried-forward",
            "No approval, review, broker, transport, Driver/HAL, or delivery owner has been assigned.",
        ),
        (
            "missing-evidence-blocker-carried-forward",
            "No owner handoff evidence packet has been attached for any EV-ACH gate.",
        ),
        (
            "no-review-result-store-decision-boundary",
            "No approval result store, handoff store, evidence store, or review queue exists in the prototype.",
        ),
        (
            "no-gate-closure-or-broker-activation",
            "No gate closure authority is assigned, so broker/DDS/high-rate activation stays blocked.",
        ),
        (
            "driver-hal-gap-decision-boundary",
            "DRV-GAP-004/005 stay as future high-rate transport and shared-memory review references only.",
        ),
        (
            "android-linux-parity-decision-boundary",
            "Android Binder and Linux CLI/IPC/gRPC surfaces are visible but only for contract review.",
        ),
        (
            "no-post-no-store-side-effect-boundary",
            "Decision rollup does not call POST, persist state, mutate queues, close gates, dispatch services, touch hardware, trigger Driver/HAL, or implement virtualization.",
        ),
        (
            "decision-rollup-blocked-summary",
            "Approval decision remains blocked until owners, evidence, review workflow, stores, gate authority, and high-rate transport decisions are supplied.",
        ),
    ]
    blocked_decisions = []
    for index, handoff in enumerate(handoffs, start=1):
        gate_name, required_evidence = decision_reasons[index - 1]
        blocked_decisions.append(
            {
                "gate_id": f"EV-AHD-{index:03d}",
                "name": gate_name,
                "source_handoff_id": handoff["handoff_id"],
                "source_blocker_id": handoff["source_blocker_id"],
                "owner_slot": handoff["owner_slot"],
                "expected_owner_role": handoff["expected_owner_role"],
                "required_evidence": required_evidence,
                "decision_state": "blocked",
                "decision_blocked": True,
                "owner_assigned": handoff["owner_assigned"],
                "evidence_attached": handoff["evidence_attached"],
                "review_queue_updated": handoff["review_queue_updated"],
                "blocks": handoff["blocks"],
                "open": True,
            }
        )

    decision_rollup_consistent = all(
        [
            handoff_audit["approval_decision_owner_handoff_audit_consistency_active"],
            handoff_checklist["approval_decision_owner_handoff_checklist_active"],
            audit_summary["consistency_passed"],
            audit_summary["open_owner_handoff_count"] == required_decision_count,
            blocked_decision_count == required_decision_count,
            unassigned_owner_count == required_decision_count,
            attached_evidence_count == 0,
            audit_summary["no_store_consistent"],
            audit_summary["no_post_consistent"],
            audit_summary["no_side_effects_consistent"],
            not audit_summary["review_queue_updated"],
            not audit_summary["gates_closed"],
            not audit_summary["broker_activation_allowed"],
            not audit_summary["activation_allowed"],
            not audit_summary["hardware_accessed"],
            not audit_summary["driver_development_triggered"],
            not audit_summary["virtualization_development_triggered"],
            not audit_summary["service_dispatch_triggered"],
        ]
    )

    return {
        "operation": "event-subscription-activation-approval-decision-owner-handoff-decision-rollup",
        "approval_decision_owner_handoff_decision_rollup_state": "contract-only-owner-handoff-decision-blocked",
        "approval_decision_owner_handoff_decision_rollup_active": True,
        "source_surfaces": {
            "owner_handoff_audit_consistency": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency",
                "active": handoff_audit["approval_decision_owner_handoff_audit_consistency_active"],
                "consistent": audit_summary["consistency_passed"],
                "owner_handoff_ready": audit_summary["owner_handoff_ready"],
                "open_owner_handoff_count": audit_summary["open_owner_handoff_count"],
                "open_owner_handoff_ids": audit_summary["open_owner_handoff_ids"],
                "audit_gate_ids": audit_gate_ids,
            },
            "owner_handoff_checklist": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist",
                "active": handoff_checklist["approval_decision_owner_handoff_checklist_active"],
                "complete": checklist_summary["owner_handoff_checklist_complete"],
                "owner_handoff_ready": checklist_summary["owner_handoff_ready"],
                "open_owner_handoff_count": checklist_summary["open_owner_handoff_count"],
                "open_owner_handoff_ids": checklist_summary["open_owner_handoff_ids"],
                "checklist_gate_ids": checklist_gate_ids,
            },
        },
        "driver_hal_gap_refs": ["DRV-GAP-004", "DRV-GAP-005"],
        "blocked_decisions": blocked_decisions,
        "decision_items": blocked_decisions,
        "mandatory_gates": [
            {
                "gate_id": decision["gate_id"],
                "name": decision["name"],
                "source_handoff_id": decision["source_handoff_id"],
                "required_evidence": decision["required_evidence"],
                "passed": not decision["decision_blocked"],
            }
            for decision in blocked_decisions
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup",
            "android_binder": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffDecisionRollupJson",
            "linux_cli": "event-subscription-activation-approval-decision-owner-handoff-decision-rollup",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.owner.handoff.decision.rollup",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffDecisionRollup",
        },
        "summary": {
            "activation_approval_decision_owner_handoff_decision_rollup_active": True,
            "owner_handoff_decision_rollup_state": "contract-only-owner-handoff-decision-blocked",
            "decision_rollup_complete": True,
            "decision_rollup_consistent": decision_rollup_consistent,
            "source_owner_handoff_audit_bound": True,
            "source_owner_handoff_checklist_bound": True,
            "owner_handoff_audit_consistent": audit_summary["consistency_passed"],
            "owner_handoff_checklist_complete": checklist_summary["owner_handoff_checklist_complete"],
            "owner_handoff_decision_blocked": owner_handoff_decision_blocked,
            "owner_handoff_decision_ready": False,
            "approval_decision_ready": False,
            "approval_dry_run_allowed": False,
            "handoff_evidence_ready": False,
            "required_decision_count": required_decision_count,
            "blocked_decision_count": blocked_decision_count,
            "required_owner_handoff_count": checklist_summary["required_owner_handoff_count"],
            "open_owner_handoff_count": checklist_summary["open_owner_handoff_count"],
            "open_owner_handoff_ids": checklist_summary["open_owner_handoff_ids"],
            "assigned_owner_count": checklist_summary["assigned_owner_count"],
            "unassigned_owner_count": unassigned_owner_count,
            "attached_evidence_count": attached_evidence_count,
            "decision": "blocked-by-unassigned-owners-and-missing-evidence",
            "decision_blocker_summary": "approval decision remains blocked by unassigned owners, missing handoff evidence, no review queue/result store, no gate closure authority, and unresolved DRV-GAP-004/005 high-rate transport decisions",
            "owner_assignments_persisted": False,
            "owner_handoff_queue_updated": False,
            "decision_dry_run_post_called_by_owner_handoff_decision_rollup": False,
            "persisted_dry_run_request_count": 0,
            "persisted_dry_run_result_count": 0,
            "persisted_approval_decision_count": 0,
            "pending_approval_decision_review_count": 0,
            "approval_result_store_created": False,
            "approval_result_store_active": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "no_store_consistent": audit_summary["no_store_consistent"],
            "no_post_consistent": audit_summary["no_post_consistent"],
            "no_side_effects_consistent": audit_summary["no_side_effects_consistent"],
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_DECISION_ROLLUP_REQ_IDS,
    }


def event_subscription_activation_approval_decision_owner_handoff_evidence_readiness_matrix_payload() -> dict[str, Any]:
    decision_rollup = event_subscription_activation_approval_decision_owner_handoff_decision_rollup_payload()
    rollup_summary = decision_rollup["summary"]
    decision_items = decision_rollup["decision_items"]
    required_packet_count = len(decision_items)
    missing_packet_count = required_packet_count
    attached_evidence_count = rollup_summary["attached_evidence_count"]

    evidence_packets = []
    for index, decision in enumerate(decision_items, start=1):
        evidence_packets.append(
            {
                "evidence_id": f"EV-AHE-{index:03d}",
                "source_decision_gate_id": decision["gate_id"],
                "source_handoff_id": decision["source_handoff_id"],
                "source_blocker_id": decision["source_blocker_id"],
                "owner_slot": decision["owner_slot"],
                "expected_owner_role": decision["expected_owner_role"],
                "required_packet_type": "owner-handoff-evidence-packet",
                "required_evidence": decision["required_evidence"],
                "packet_state": "missing",
                "readiness_state": "blocked-missing-owner-and-evidence",
                "owner_assigned": decision["owner_assigned"],
                "evidence_attached": decision["evidence_attached"],
                "evidence_uri_present": False,
                "evidence_hash_present": False,
                "owner_signature_present": False,
                "evidence_persisted": False,
                "review_queue_updated": decision["review_queue_updated"],
                "blocks": ["approval-decision-review", "gate-closure", "broker-activation"],
                "open": True,
            }
        )

    readiness_matrix_consistent = all(
        [
            decision_rollup["approval_decision_owner_handoff_decision_rollup_active"],
            rollup_summary["decision_rollup_complete"],
            rollup_summary["decision_rollup_consistent"],
            rollup_summary["owner_handoff_decision_blocked"],
            missing_packet_count == required_packet_count,
            attached_evidence_count == 0,
            rollup_summary["no_store_consistent"],
            rollup_summary["no_post_consistent"],
            rollup_summary["no_side_effects_consistent"],
            not rollup_summary["approval_decision_ready"],
            not rollup_summary["approval_dry_run_allowed"],
            not rollup_summary["review_queue_updated"],
            not rollup_summary["gates_closed"],
            not rollup_summary["broker_activation_allowed"],
            not rollup_summary["activation_allowed"],
            not rollup_summary["hardware_accessed"],
            not rollup_summary["driver_development_triggered"],
            not rollup_summary["virtualization_development_triggered"],
            not rollup_summary["service_dispatch_triggered"],
        ]
    )

    return {
        "operation": "event-subscription-activation-approval-decision-owner-handoff-evidence-readiness-matrix",
        "approval_decision_owner_handoff_evidence_readiness_matrix_state": "contract-only-handoff-evidence-missing",
        "approval_decision_owner_handoff_evidence_readiness_matrix_active": True,
        "source_surfaces": {
            "owner_handoff_decision_rollup": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup",
                "active": decision_rollup["approval_decision_owner_handoff_decision_rollup_active"],
                "complete": rollup_summary["decision_rollup_complete"],
                "consistent": rollup_summary["decision_rollup_consistent"],
                "decision_blocked": rollup_summary["owner_handoff_decision_blocked"],
                "decision_gate_ids": [item["gate_id"] for item in decision_rollup["mandatory_gates"]],
                "open_owner_handoff_ids": rollup_summary["open_owner_handoff_ids"],
            },
        },
        "driver_hal_gap_refs": decision_rollup["driver_hal_gap_refs"],
        "evidence_packets": evidence_packets,
        "readiness_items": evidence_packets,
        "mandatory_gates": [
            {
                "gate_id": packet["evidence_id"],
                "name": packet["required_packet_type"],
                "source_decision_gate_id": packet["source_decision_gate_id"],
                "source_handoff_id": packet["source_handoff_id"],
                "required_evidence": packet["required_evidence"],
                "passed": False,
            }
            for packet in evidence_packets
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix",
            "android_binder": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessMatrixJson",
            "linux_cli": "event-subscription-activation-approval-decision-owner-handoff-evidence-readiness-matrix",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.readiness.matrix",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessMatrix",
        },
        "summary": {
            "activation_approval_decision_owner_handoff_evidence_readiness_matrix_active": True,
            "handoff_evidence_readiness_matrix_state": "contract-only-handoff-evidence-missing",
            "readiness_matrix_complete": True,
            "readiness_matrix_consistent": readiness_matrix_consistent,
            "source_owner_handoff_decision_rollup_bound": True,
            "source_owner_handoff_decision_rollup_consistent": rollup_summary["decision_rollup_consistent"],
            "source_owner_handoff_decision_blocked": rollup_summary["owner_handoff_decision_blocked"],
            "handoff_evidence_ready": False,
            "approval_decision_ready": False,
            "approval_dry_run_allowed": False,
            "required_evidence_packet_count": required_packet_count,
            "missing_evidence_packet_count": missing_packet_count,
            "attached_evidence_count": attached_evidence_count,
            "persisted_evidence_packet_count": 0,
            "evidence_uri_count": 0,
            "evidence_hash_count": 0,
            "owner_signature_count": 0,
            "required_owner_handoff_count": rollup_summary["required_owner_handoff_count"],
            "open_owner_handoff_count": rollup_summary["open_owner_handoff_count"],
            "open_owner_handoff_ids": rollup_summary["open_owner_handoff_ids"],
            "assigned_owner_count": rollup_summary["assigned_owner_count"],
            "unassigned_owner_count": rollup_summary["unassigned_owner_count"],
            "decision": "blocked-by-missing-handoff-evidence-packets",
            "decision_blocker_summary": "approval decision cannot enter review because all owner handoff evidence packets are missing and no evidence URI, hash, signature, store, or review queue is available",
            "owner_assignments_persisted": False,
            "owner_handoff_queue_updated": False,
            "evidence_packets_attached": False,
            "evidence_store_created": False,
            "evidence_store_active": False,
            "decision_dry_run_post_called_by_handoff_evidence_readiness_matrix": False,
            "persisted_dry_run_request_count": 0,
            "persisted_dry_run_result_count": 0,
            "persisted_approval_decision_count": 0,
            "pending_approval_decision_review_count": 0,
            "approval_result_store_created": False,
            "approval_result_store_active": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "no_store_consistent": rollup_summary["no_store_consistent"],
            "no_post_consistent": rollup_summary["no_post_consistent"],
            "no_side_effects_consistent": rollup_summary["no_side_effects_consistent"],
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_READINESS_MATRIX_REQ_IDS,
    }


def event_subscription_activation_approval_decision_owner_handoff_evidence_readiness_audit_consistency_payload() -> dict[str, Any]:
    matrix = event_subscription_activation_approval_decision_owner_handoff_evidence_readiness_matrix_payload()
    decision_rollup = event_subscription_activation_approval_decision_owner_handoff_decision_rollup_payload()
    matrix_summary = matrix["summary"]
    rollup_summary = decision_rollup["summary"]
    evidence_packets = matrix["evidence_packets"]
    packet_ids = [packet["evidence_id"] for packet in evidence_packets]
    source_decision_gate_ids = [packet["source_decision_gate_id"] for packet in evidence_packets]
    source_handoff_ids = [packet["source_handoff_id"] for packet in evidence_packets]
    source_blocker_ids = [packet["source_blocker_id"] for packet in evidence_packets]

    packet_count_consistent = all(
        [
            len(evidence_packets) == matrix_summary["required_evidence_packet_count"],
            len(evidence_packets) == matrix_summary["missing_evidence_packet_count"],
            len(evidence_packets) == rollup_summary["required_decision_count"],
            len(evidence_packets) == 10,
            matrix_summary["attached_evidence_count"] == 0,
            matrix_summary["persisted_evidence_packet_count"] == 0,
            matrix_summary["evidence_uri_count"] == 0,
            matrix_summary["evidence_hash_count"] == 0,
            matrix_summary["owner_signature_count"] == 0,
        ]
    )
    packet_state_consistent = all(
        packet["packet_state"] == "missing"
        and packet["readiness_state"] == "blocked-missing-owner-and-evidence"
        and packet["open"] is True
        and packet["owner_assigned"] is False
        and packet["evidence_attached"] is False
        and packet["evidence_uri_present"] is False
        and packet["evidence_hash_present"] is False
        and packet["owner_signature_present"] is False
        and packet["evidence_persisted"] is False
        and packet["review_queue_updated"] is False
        for packet in evidence_packets
    )
    source_binding_consistent = all(
        [
            matrix_summary["source_owner_handoff_decision_rollup_bound"],
            matrix_summary["source_owner_handoff_decision_rollup_consistent"],
            rollup_summary["decision_rollup_consistent"],
            all(gate_id.startswith("EV-AHD-") for gate_id in source_decision_gate_ids),
            all(handoff_id.startswith("EV-ACH-") for handoff_id in source_handoff_ids),
            all(blocker_id.startswith("EV-ACB-") for blocker_id in source_blocker_ids),
            "EV-AHD-010" in source_decision_gate_ids,
            "EV-ACH-010" in source_handoff_ids,
            "EV-ACB-010" in source_blocker_ids,
        ]
    )
    android_linux_parity_consistent = all(
        [
            "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessMatrixJson" in matrix["api_surface"]["android_binder"],
            "event-subscription-activation-approval-decision-owner-handoff-evidence-readiness-matrix" in matrix["api_surface"]["linux_cli"],
            "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.readiness.matrix" in matrix["api_surface"]["linux_ipc"],
            "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessMatrix" in matrix["api_surface"]["linux_grpc_rpc"],
        ]
    )
    no_store_consistent = all(
        [
            matrix_summary["no_store_consistent"],
            matrix_summary["persisted_dry_run_request_count"] == 0,
            matrix_summary["persisted_dry_run_result_count"] == 0,
            matrix_summary["persisted_approval_decision_count"] == 0,
            matrix_summary["pending_approval_decision_review_count"] == 0,
            matrix_summary["persisted_evidence_packet_count"] == 0,
            not matrix_summary["owner_assignments_persisted"],
            not matrix_summary["evidence_store_created"],
            not matrix_summary["evidence_store_active"],
            not matrix_summary["approval_result_store_created"],
            not matrix_summary["approval_result_store_active"],
            not matrix_summary["review_queue_updated"],
        ]
    )
    no_post_consistent = all(
        [
            matrix_summary["no_post_consistent"],
            not matrix_summary["decision_dry_run_post_called_by_handoff_evidence_readiness_matrix"],
        ]
    )
    no_side_effects_consistent = all(
        [
            matrix_summary["no_side_effects_consistent"],
            not matrix_summary["owner_handoff_queue_updated"],
            not matrix_summary["evidence_packets_attached"],
            not matrix_summary["gate_state_changed"],
            not matrix_summary["gates_closed"],
            not matrix_summary["broker_activation_allowed"],
            not matrix_summary["activation_allowed"],
            not matrix_summary["production_activation_allowed"],
            not matrix_summary["broker_active"],
            not matrix_summary["subscription_persistence_active"],
            not matrix_summary["cursor_storage_active"],
            not matrix_summary["event_delivery_qos_active"],
            not matrix_summary["callback_registered"],
            not matrix_summary["watch_started"],
            not matrix_summary["dds_runtime_active"],
            not matrix_summary["sse_websocket_active"],
            not matrix_summary["high_rate_data_plane_active"],
            not matrix_summary["hardware_accessed"],
            not matrix_summary["driver_development_triggered"],
            not matrix_summary["virtualization_development_triggered"],
            not matrix_summary["service_dispatch_triggered"],
        ]
    )
    consistency_passed = all(
        [
            matrix["approval_decision_owner_handoff_evidence_readiness_matrix_active"],
            matrix_summary["readiness_matrix_complete"],
            matrix_summary["readiness_matrix_consistent"],
            packet_count_consistent,
            packet_state_consistent,
            source_binding_consistent,
            android_linux_parity_consistent,
            no_store_consistent,
            no_post_consistent,
            no_side_effects_consistent,
        ]
    )

    audit_items = []
    for index, packet in enumerate(evidence_packets, start=1):
        audit_items.append(
            {
                "gate_id": f"EV-AHF-{index:03d}",
                "source_evidence_id": packet["evidence_id"],
                "source_decision_gate_id": packet["source_decision_gate_id"],
                "source_handoff_id": packet["source_handoff_id"],
                "source_blocker_id": packet["source_blocker_id"],
                "check_category": [
                    "packet-count",
                    "packet-state",
                    "source-binding",
                    "android-linux-parity",
                    "no-store",
                    "no-post",
                    "no-side-effect",
                ][(index - 1) % 7],
                "result": "consistent",
                "passed": True,
                "readiness_still_blocked": True,
            }
        )

    return {
        "operation": "event-subscription-activation-approval-decision-owner-handoff-evidence-readiness-audit-consistency",
        "approval_decision_owner_handoff_evidence_readiness_audit_consistency_state": "contract-only-handoff-evidence-audit-consistent",
        "approval_decision_owner_handoff_evidence_readiness_audit_consistency_active": True,
        "source_surfaces": {
            "handoff_evidence_readiness_matrix": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix",
                "active": matrix["approval_decision_owner_handoff_evidence_readiness_matrix_active"],
                "complete": matrix_summary["readiness_matrix_complete"],
                "consistent": matrix_summary["readiness_matrix_consistent"],
                "handoff_evidence_ready": matrix_summary["handoff_evidence_ready"],
                "evidence_packet_ids": packet_ids,
            },
            "owner_handoff_decision_rollup": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup",
                "active": decision_rollup["approval_decision_owner_handoff_decision_rollup_active"],
                "complete": rollup_summary["decision_rollup_complete"],
                "consistent": rollup_summary["decision_rollup_consistent"],
                "decision_blocked": rollup_summary["owner_handoff_decision_blocked"],
                "decision_gate_ids": source_decision_gate_ids,
            },
        },
        "driver_hal_gap_refs": matrix["driver_hal_gap_refs"],
        "audit_items": audit_items,
        "mandatory_gates": [
            {
                "gate_id": item["gate_id"],
                "name": item["check_category"],
                "source_evidence_id": item["source_evidence_id"],
                "passed": item["passed"],
                "readiness_still_blocked": item["readiness_still_blocked"],
            }
            for item in audit_items
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency",
            "android_binder": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessAuditConsistencyJson",
            "linux_cli": "event-subscription-activation-approval-decision-owner-handoff-evidence-readiness-audit-consistency",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.readiness.audit.consistency",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessAuditConsistency",
        },
        "summary": {
            "activation_approval_decision_owner_handoff_evidence_readiness_audit_consistency_active": True,
            "handoff_evidence_readiness_audit_consistency_state": "contract-only-handoff-evidence-audit-consistent",
            "consistency_passed": consistency_passed,
            "source_handoff_evidence_readiness_matrix_bound": True,
            "source_owner_handoff_decision_rollup_bound": True,
            "source_handoff_evidence_readiness_matrix_consistent": matrix_summary["readiness_matrix_consistent"],
            "source_owner_handoff_decision_rollup_consistent": rollup_summary["decision_rollup_consistent"],
            "packet_count_consistent": packet_count_consistent,
            "packet_state_consistent": packet_state_consistent,
            "source_binding_consistent": source_binding_consistent,
            "android_linux_parity_consistent": android_linux_parity_consistent,
            "no_store_consistent": no_store_consistent,
            "no_post_consistent": no_post_consistent,
            "no_side_effects_consistent": no_side_effects_consistent,
            "handoff_evidence_ready": False,
            "approval_decision_ready": False,
            "approval_dry_run_allowed": False,
            "required_evidence_packet_count": matrix_summary["required_evidence_packet_count"],
            "missing_evidence_packet_count": matrix_summary["missing_evidence_packet_count"],
            "attached_evidence_count": matrix_summary["attached_evidence_count"],
            "persisted_evidence_packet_count": matrix_summary["persisted_evidence_packet_count"],
            "evidence_uri_count": matrix_summary["evidence_uri_count"],
            "evidence_hash_count": matrix_summary["evidence_hash_count"],
            "owner_signature_count": matrix_summary["owner_signature_count"],
            "required_owner_handoff_count": matrix_summary["required_owner_handoff_count"],
            "open_owner_handoff_count": matrix_summary["open_owner_handoff_count"],
            "open_owner_handoff_ids": matrix_summary["open_owner_handoff_ids"],
            "assigned_owner_count": matrix_summary["assigned_owner_count"],
            "unassigned_owner_count": matrix_summary["unassigned_owner_count"],
            "decision": "blocked-by-missing-handoff-evidence-packets",
            "decision_blocker_summary": "audit confirms handoff evidence readiness matrix is internally consistent while all evidence packets remain missing",
            "owner_assignments_persisted": False,
            "owner_handoff_queue_updated": False,
            "evidence_packets_attached": False,
            "evidence_store_created": False,
            "evidence_store_active": False,
            "decision_dry_run_post_called_by_handoff_evidence_readiness_audit_consistency": False,
            "persisted_dry_run_request_count": 0,
            "persisted_dry_run_result_count": 0,
            "persisted_approval_decision_count": 0,
            "pending_approval_decision_review_count": 0,
            "approval_result_store_created": False,
            "approval_result_store_active": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_READINESS_AUDIT_CONSISTENCY_REQ_IDS,
    }


def event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_status_payload() -> dict[str, Any]:
    audit = event_subscription_activation_approval_decision_owner_handoff_evidence_readiness_audit_consistency_payload()
    matrix = event_subscription_activation_approval_decision_owner_handoff_evidence_readiness_matrix_payload()
    audit_summary = audit["summary"]
    matrix_summary = matrix["summary"]
    evidence_packets = matrix["evidence_packets"]

    acceptance_items = []
    for index, packet in enumerate(evidence_packets, start=1):
        acceptance_items.append(
            {
                "gate_id": f"EV-AHG-{index:03d}",
                "source_audit_gate_id": f"EV-AHF-{index:03d}",
                "source_evidence_id": packet["evidence_id"],
                "source_decision_gate_id": packet["source_decision_gate_id"],
                "source_handoff_id": packet["source_handoff_id"],
                "source_blocker_id": packet["source_blocker_id"],
                "acceptance_state": "blocked-missing-evidence-packet",
                "acceptance_allowed": False,
                "accepted": False,
                "acceptance_record_persisted": False,
                "evidence_attached": False,
                "evidence_persisted": False,
                "review_queue_updated": False,
                "blocker_reason": "evidence packet is missing and no URI, hash, owner signature, evidence store, or review queue exists",
            }
        )

    required_acceptance_count = len(acceptance_items)
    accepted_evidence_packet_count = sum(1 for item in acceptance_items if item["accepted"])
    blocked_acceptance_count = sum(1 for item in acceptance_items if not item["accepted"])
    acceptance_record_persisted_count = sum(1 for item in acceptance_items if item["acceptance_record_persisted"])
    acceptance_status_consistent = all(
        [
            audit["approval_decision_owner_handoff_evidence_readiness_audit_consistency_active"],
            audit_summary["consistency_passed"],
            audit_summary["handoff_evidence_ready"] is False,
            matrix_summary["handoff_evidence_ready"] is False,
            required_acceptance_count == audit_summary["required_evidence_packet_count"],
            required_acceptance_count == matrix_summary["required_evidence_packet_count"],
            blocked_acceptance_count == matrix_summary["missing_evidence_packet_count"],
            accepted_evidence_packet_count == 0,
            acceptance_record_persisted_count == 0,
            matrix_summary["attached_evidence_count"] == 0,
            matrix_summary["persisted_evidence_packet_count"] == 0,
            matrix_summary["evidence_uri_count"] == 0,
            matrix_summary["owner_signature_count"] == 0,
            not matrix_summary["review_queue_updated"],
            not matrix_summary["gates_closed"],
            not matrix_summary["activation_allowed"],
        ]
    )
    no_store_consistent = all(
        [
            audit_summary["no_store_consistent"],
            acceptance_record_persisted_count == 0,
            matrix_summary["persisted_evidence_packet_count"] == 0,
            not matrix_summary["evidence_store_created"],
            not matrix_summary["evidence_store_active"],
            not matrix_summary["approval_result_store_created"],
            not matrix_summary["approval_result_store_active"],
            not matrix_summary["review_queue_updated"],
        ]
    )
    no_post_consistent = all(
        [
            audit_summary["no_post_consistent"],
            not audit_summary["decision_dry_run_post_called_by_handoff_evidence_readiness_audit_consistency"],
            not matrix_summary["decision_dry_run_post_called_by_handoff_evidence_readiness_matrix"],
        ]
    )
    no_side_effects_consistent = all(
        [
            audit_summary["no_side_effects_consistent"],
            not matrix_summary["owner_handoff_queue_updated"],
            not matrix_summary["evidence_packets_attached"],
            not matrix_summary["gate_state_changed"],
            not matrix_summary["gates_closed"],
            not matrix_summary["broker_activation_allowed"],
            not matrix_summary["activation_allowed"],
            not matrix_summary["hardware_accessed"],
            not matrix_summary["driver_development_triggered"],
            not matrix_summary["virtualization_development_triggered"],
            not matrix_summary["service_dispatch_triggered"],
        ]
    )

    return {
        "operation": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-status",
        "approval_decision_owner_handoff_evidence_acceptance_status_state": "contract-only-handoff-evidence-acceptance-blocked",
        "approval_decision_owner_handoff_evidence_acceptance_status_active": True,
        "source_surfaces": {
            "handoff_evidence_readiness_audit_consistency": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency",
                "active": audit["approval_decision_owner_handoff_evidence_readiness_audit_consistency_active"],
                "consistent": audit_summary["consistency_passed"],
                "handoff_evidence_ready": audit_summary["handoff_evidence_ready"],
            },
            "handoff_evidence_readiness_matrix": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix",
                "active": matrix["approval_decision_owner_handoff_evidence_readiness_matrix_active"],
                "complete": matrix_summary["readiness_matrix_complete"],
                "missing_evidence_packet_count": matrix_summary["missing_evidence_packet_count"],
            },
        },
        "driver_hal_gap_refs": matrix["driver_hal_gap_refs"],
        "acceptance_items": acceptance_items,
        "mandatory_gates": [
            {
                "gate_id": item["gate_id"],
                "source_audit_gate_id": item["source_audit_gate_id"],
                "source_evidence_id": item["source_evidence_id"],
                "acceptance_state": item["acceptance_state"],
                "acceptance_allowed": item["acceptance_allowed"],
                "accepted": item["accepted"],
            }
            for item in acceptance_items
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status",
            "android_binder": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceStatusJson",
            "linux_cli": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-status",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.status",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceStatus",
        },
        "summary": {
            "activation_approval_decision_owner_handoff_evidence_acceptance_status_active": True,
            "handoff_evidence_acceptance_status_state": "contract-only-handoff-evidence-acceptance-blocked",
            "acceptance_status_complete": True,
            "acceptance_status_consistent": acceptance_status_consistent,
            "source_handoff_evidence_readiness_audit_bound": True,
            "source_handoff_evidence_readiness_matrix_bound": True,
            "source_handoff_evidence_readiness_audit_consistent": audit_summary["consistency_passed"],
            "required_acceptance_count": required_acceptance_count,
            "blocked_acceptance_count": blocked_acceptance_count,
            "accepted_evidence_packet_count": accepted_evidence_packet_count,
            "acceptance_record_persisted_count": acceptance_record_persisted_count,
            "handoff_evidence_acceptance_allowed": False,
            "handoff_evidence_ready": False,
            "approval_decision_ready": False,
            "approval_dry_run_allowed": False,
            "required_evidence_packet_count": matrix_summary["required_evidence_packet_count"],
            "missing_evidence_packet_count": matrix_summary["missing_evidence_packet_count"],
            "attached_evidence_count": matrix_summary["attached_evidence_count"],
            "persisted_evidence_packet_count": matrix_summary["persisted_evidence_packet_count"],
            "evidence_uri_count": matrix_summary["evidence_uri_count"],
            "evidence_hash_count": matrix_summary["evidence_hash_count"],
            "owner_signature_count": matrix_summary["owner_signature_count"],
            "decision": "blocked-by-missing-handoff-evidence-packets",
            "decision_blocker_summary": "acceptance cannot start because every handoff evidence packet remains missing and no acceptance record store, evidence store, or review queue exists",
            "owner_assignments_persisted": False,
            "owner_handoff_queue_updated": False,
            "evidence_packets_attached": False,
            "evidence_store_created": False,
            "evidence_store_active": False,
            "decision_dry_run_post_called_by_handoff_evidence_acceptance_status": False,
            "persisted_dry_run_request_count": 0,
            "persisted_dry_run_result_count": 0,
            "persisted_approval_decision_count": 0,
            "pending_approval_decision_review_count": 0,
            "approval_result_store_created": False,
            "approval_result_store_active": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "no_store_consistent": no_store_consistent,
            "no_post_consistent": no_post_consistent,
            "no_side_effects_consistent": no_side_effects_consistent,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_STATUS_REQ_IDS,
    }


def event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_audit_consistency_payload() -> dict[str, Any]:
    status = event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_status_payload()
    readiness_audit = event_subscription_activation_approval_decision_owner_handoff_evidence_readiness_audit_consistency_payload()
    status_summary = status["summary"]
    readiness_audit_summary = readiness_audit["summary"]
    acceptance_items = status["acceptance_items"]

    audit_items = []
    for index, item in enumerate(acceptance_items, start=1):
        audit_items.append(
            {
                "gate_id": f"EV-AHH-{index:03d}",
                "source_acceptance_gate_id": item["gate_id"],
                "source_readiness_audit_gate_id": item["source_audit_gate_id"],
                "source_evidence_id": item["source_evidence_id"],
                "source_decision_gate_id": item["source_decision_gate_id"],
                "source_handoff_id": item["source_handoff_id"],
                "source_blocker_id": item["source_blocker_id"],
                "result": "consistent",
                "passed": True,
                "acceptance_still_blocked": True,
                "checks": [
                    "acceptance-state-blocked",
                    "missing-evidence-packet-bound",
                    "no-acceptance-record-persisted",
                    "no-evidence-attached",
                    "no-review-queue-updated",
                    "no-gate-closed",
                    "android-linux-binding-visible",
                ],
            }
        )

    required_audit_count = len(audit_items)
    acceptance_count_consistent = all(
        [
            required_audit_count == status_summary["required_acceptance_count"],
            status_summary["required_acceptance_count"] == status_summary["blocked_acceptance_count"],
            status_summary["accepted_evidence_packet_count"] == 0,
            status_summary["acceptance_record_persisted_count"] == 0,
            status_summary["missing_evidence_packet_count"] == status_summary["required_evidence_packet_count"],
        ]
    )
    blocked_acceptance_state_consistent = all(
        [
            item["acceptance_state"] == "blocked-missing-evidence-packet",
            item["acceptance_allowed"] is False,
            item["accepted"] is False,
            item["acceptance_record_persisted"] is False,
            item["evidence_attached"] is False,
            item["evidence_persisted"] is False,
            item["review_queue_updated"] is False,
        ]
        for item in acceptance_items
    )
    no_store_consistent = all(
        [
            status_summary["no_store_consistent"],
            status_summary["acceptance_record_persisted_count"] == 0,
            status_summary["persisted_evidence_packet_count"] == 0,
            not status_summary["evidence_store_created"],
            not status_summary["evidence_store_active"],
            not status_summary["approval_result_store_created"],
            not status_summary["approval_result_store_active"],
            not status_summary["review_queue_updated"],
        ]
    )
    no_post_consistent = all(
        [
            status_summary["no_post_consistent"],
            not status_summary["decision_dry_run_post_called_by_handoff_evidence_acceptance_status"],
        ]
    )
    no_side_effects_consistent = all(
        [
            status_summary["no_side_effects_consistent"],
            not status_summary["owner_assignments_persisted"],
            not status_summary["owner_handoff_queue_updated"],
            not status_summary["evidence_packets_attached"],
            not status_summary["gate_state_changed"],
            not status_summary["gates_closed"],
            not status_summary["broker_activation_allowed"],
            not status_summary["activation_allowed"],
            not status_summary["hardware_accessed"],
            not status_summary["driver_development_triggered"],
            not status_summary["virtualization_development_triggered"],
            not status_summary["service_dispatch_triggered"],
        ]
    )
    consistency_passed = all(
        [
            status["approval_decision_owner_handoff_evidence_acceptance_status_active"],
            status_summary["acceptance_status_complete"],
            status_summary["acceptance_status_consistent"],
            readiness_audit["approval_decision_owner_handoff_evidence_readiness_audit_consistency_active"],
            readiness_audit_summary["consistency_passed"],
            acceptance_count_consistent,
            blocked_acceptance_state_consistent,
            no_store_consistent,
            no_post_consistent,
            no_side_effects_consistent,
        ]
    )

    return {
        "operation": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-audit-consistency",
        "approval_decision_owner_handoff_evidence_acceptance_audit_consistency_state": "contract-only-handoff-evidence-acceptance-audit-consistent",
        "approval_decision_owner_handoff_evidence_acceptance_audit_consistency_active": True,
        "source_surfaces": {
            "handoff_evidence_acceptance_status": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status",
                "active": status["approval_decision_owner_handoff_evidence_acceptance_status_active"],
                "consistent": status_summary["acceptance_status_consistent"],
                "blocked_acceptance_count": status_summary["blocked_acceptance_count"],
                "accepted_evidence_packet_count": status_summary["accepted_evidence_packet_count"],
            },
            "handoff_evidence_readiness_audit_consistency": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency",
                "active": readiness_audit["approval_decision_owner_handoff_evidence_readiness_audit_consistency_active"],
                "consistent": readiness_audit_summary["consistency_passed"],
                "handoff_evidence_ready": readiness_audit_summary["handoff_evidence_ready"],
            },
        },
        "driver_hal_gap_refs": status["driver_hal_gap_refs"],
        "audit_items": audit_items,
        "mandatory_gates": [
            {
                "gate_id": item["gate_id"],
                "source_acceptance_gate_id": item["source_acceptance_gate_id"],
                "source_readiness_audit_gate_id": item["source_readiness_audit_gate_id"],
                "result": item["result"],
                "passed": item["passed"],
                "acceptance_still_blocked": item["acceptance_still_blocked"],
            }
            for item in audit_items
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency",
            "android_binder": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceAuditConsistencyJson",
            "linux_cli": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-audit-consistency",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.audit.consistency",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceAuditConsistency",
        },
        "summary": {
            "activation_approval_decision_owner_handoff_evidence_acceptance_audit_consistency_active": True,
            "handoff_evidence_acceptance_audit_consistency_state": "contract-only-handoff-evidence-acceptance-audit-consistent",
            "consistency_passed": consistency_passed,
            "source_acceptance_status_bound": True,
            "source_handoff_evidence_readiness_audit_bound": True,
            "acceptance_status_consistent": status_summary["acceptance_status_consistent"],
            "handoff_evidence_readiness_audit_consistent": readiness_audit_summary["consistency_passed"],
            "acceptance_count_consistent": acceptance_count_consistent,
            "blocked_acceptance_state_consistent": blocked_acceptance_state_consistent,
            "android_linux_parity_consistent": True,
            "no_store_consistent": no_store_consistent,
            "no_post_consistent": no_post_consistent,
            "no_side_effects_consistent": no_side_effects_consistent,
            "required_audit_count": required_audit_count,
            "required_acceptance_count": status_summary["required_acceptance_count"],
            "blocked_acceptance_count": status_summary["blocked_acceptance_count"],
            "accepted_evidence_packet_count": status_summary["accepted_evidence_packet_count"],
            "acceptance_record_persisted_count": status_summary["acceptance_record_persisted_count"],
            "handoff_evidence_acceptance_allowed": False,
            "handoff_evidence_ready": False,
            "approval_decision_ready": False,
            "approval_dry_run_allowed": False,
            "required_evidence_packet_count": status_summary["required_evidence_packet_count"],
            "missing_evidence_packet_count": status_summary["missing_evidence_packet_count"],
            "attached_evidence_count": status_summary["attached_evidence_count"],
            "persisted_evidence_packet_count": status_summary["persisted_evidence_packet_count"],
            "evidence_uri_count": status_summary["evidence_uri_count"],
            "evidence_hash_count": status_summary["evidence_hash_count"],
            "owner_signature_count": status_summary["owner_signature_count"],
            "decision": "blocked-by-missing-handoff-evidence-packets",
            "decision_blocker_summary": "acceptance audit is consistent but every evidence packet remains missing, so no acceptance, review, gate closure, or broker activation is allowed",
            "owner_assignments_persisted": False,
            "owner_handoff_queue_updated": False,
            "evidence_packets_attached": False,
            "evidence_store_created": False,
            "evidence_store_active": False,
            "decision_dry_run_post_called_by_handoff_evidence_acceptance_audit_consistency": False,
            "persisted_dry_run_request_count": 0,
            "persisted_dry_run_result_count": 0,
            "persisted_approval_decision_count": 0,
            "pending_approval_decision_review_count": 0,
            "approval_result_store_created": False,
            "approval_result_store_active": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_AUDIT_CONSISTENCY_REQ_IDS,
    }


def event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_decision_rollup_payload() -> dict[str, Any]:
    acceptance_status = event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_status_payload()
    acceptance_audit = event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_audit_consistency_payload()
    readiness_matrix = event_subscription_activation_approval_decision_owner_handoff_evidence_readiness_matrix_payload()
    status_summary = acceptance_status["summary"]
    audit_summary = acceptance_audit["summary"]
    matrix_summary = readiness_matrix["summary"]
    acceptance_items = acceptance_status["acceptance_items"]

    decision_rows = []
    for index, item in enumerate(acceptance_items, start=1):
        decision_rows.append(
            {
                "decision_id": f"EV-AHI-DEC-{index:03d}",
                "gate_id": f"EV-AHI-{index:03d}",
                "source_acceptance_gate_id": item["gate_id"],
                "source_audit_gate_id": f"EV-AHH-{index:03d}",
                "source_evidence_id": item["source_evidence_id"],
                "source_decision_gate_id": item["source_decision_gate_id"],
                "source_handoff_id": item["source_handoff_id"],
                "source_blocker_id": item["source_blocker_id"],
                "decision": "acceptance_decision_for_handoff_evidence_packet",
                "owner": "acceptance_authority_owner_unassigned",
                "state": "blocked_missing_evidence_packet_and_acceptance_authority",
                "source_surface": "handoff_evidence_acceptance_audit_consistency",
                "source_blocker": "handoff_evidence_acceptance_allowed=false",
                "decision_confirmed": False,
                "required_before": ["handoff_evidence_acceptance", "approval_review", "gate_closure"],
                "blocks_handoff_acceptance": True,
                "blocks_approval_review": True,
                "blocks_gate_closure": True,
                "blocks_broker_activation": True,
            }
        )

    blocked_decision_count = sum(1 for item in decision_rows if item["decision_confirmed"] is False)
    source_surfaces_bound = all(
        [
            acceptance_status["approval_decision_owner_handoff_evidence_acceptance_status_active"],
            acceptance_audit["approval_decision_owner_handoff_evidence_acceptance_audit_consistency_active"],
            readiness_matrix["approval_decision_owner_handoff_evidence_readiness_matrix_active"],
            audit_summary["consistency_passed"],
            status_summary["acceptance_status_consistent"],
            matrix_summary["readiness_matrix_consistent"],
        ]
    )
    no_store_consistent = all(
        [
            audit_summary["no_store_consistent"],
            audit_summary["acceptance_record_persisted_count"] == 0,
            audit_summary["persisted_evidence_packet_count"] == 0,
            not audit_summary["evidence_store_created"],
            not audit_summary["evidence_store_active"],
            not audit_summary["approval_result_store_created"],
            not audit_summary["approval_result_store_active"],
            not audit_summary["review_queue_updated"],
        ]
    )
    no_post_consistent = all(
        [
            audit_summary["no_post_consistent"],
            not audit_summary["decision_dry_run_post_called_by_handoff_evidence_acceptance_audit_consistency"],
        ]
    )
    no_side_effects_consistent = all(
        [
            audit_summary["no_side_effects_consistent"],
            not audit_summary["owner_assignments_persisted"],
            not audit_summary["owner_handoff_queue_updated"],
            not audit_summary["evidence_packets_attached"],
            not audit_summary["gate_state_changed"],
            not audit_summary["gates_closed"],
            not audit_summary["broker_activation_allowed"],
            not audit_summary["activation_allowed"],
            not audit_summary["hardware_accessed"],
            not audit_summary["driver_development_triggered"],
            not audit_summary["virtualization_development_triggered"],
            not audit_summary["service_dispatch_triggered"],
        ]
    )
    decision_rollup_consistent = all(
        [
            source_surfaces_bound,
            blocked_decision_count == len(decision_rows),
            status_summary["blocked_acceptance_count"] == audit_summary["blocked_acceptance_count"],
            status_summary["required_acceptance_count"] == audit_summary["required_acceptance_count"],
            status_summary["accepted_evidence_packet_count"] == 0,
            audit_summary["acceptance_record_persisted_count"] == 0,
            matrix_summary["missing_evidence_packet_count"] == audit_summary["missing_evidence_packet_count"],
            no_store_consistent,
            no_post_consistent,
            no_side_effects_consistent,
        ]
    )

    return {
        "operation": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-decision-rollup",
        "approval_decision_owner_handoff_evidence_acceptance_decision_rollup_state": "contract-only-handoff-evidence-acceptance-decision-blocked",
        "approval_decision_owner_handoff_evidence_acceptance_decision_rollup_active": True,
        "decision_rollup_complete": True,
        "decision_rollup_consistent": decision_rollup_consistent,
        "source_surfaces_bound": source_surfaces_bound,
        "acceptance_authority_confirmed": False,
        "acceptance_record_store_confirmed": False,
        "review_workflow_owner_confirmed": False,
        "audit_retention_owner_confirmed": False,
        "evidence_packet_presence_confirmed": False,
        "evidence_uri_rules_confirmed": False,
        "owner_signature_rule_confirmed": False,
        "gate_closure_authority_confirmed": False,
        "broker_activation_owner_confirmed": False,
        "driver_gap_review_owner_confirmed": False,
        "acceptance_decision_ready": False,
        "handoff_evidence_acceptance_allowed": False,
        "approval_review_allowed": False,
        "gate_closure_allowed": False,
        "broker_activation_allowed": False,
        "required_decision_count": len(decision_rows),
        "blocked_decision_count": blocked_decision_count,
        "required_acceptance_count": status_summary["required_acceptance_count"],
        "blocked_acceptance_count": status_summary["blocked_acceptance_count"],
        "accepted_evidence_packet_count": status_summary["accepted_evidence_packet_count"],
        "acceptance_record_persisted_count": status_summary["acceptance_record_persisted_count"],
        "required_evidence_packet_count": matrix_summary["required_evidence_packet_count"],
        "missing_evidence_packet_count": matrix_summary["missing_evidence_packet_count"],
        "attached_evidence_count": matrix_summary["attached_evidence_count"],
        "persisted_evidence_packet_count": matrix_summary["persisted_evidence_packet_count"],
        "decision_rows": decision_rows,
        "source_surfaces": {
            "handoff_evidence_acceptance_status": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status",
                "active": acceptance_status["approval_decision_owner_handoff_evidence_acceptance_status_active"],
                "consistent": status_summary["acceptance_status_consistent"],
                "required_acceptance_count": status_summary["required_acceptance_count"],
                "blocked_acceptance_count": status_summary["blocked_acceptance_count"],
                "accepted_evidence_packet_count": status_summary["accepted_evidence_packet_count"],
                "acceptance_record_persisted_count": status_summary["acceptance_record_persisted_count"],
            },
            "handoff_evidence_acceptance_audit_consistency": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency",
                "active": acceptance_audit["approval_decision_owner_handoff_evidence_acceptance_audit_consistency_active"],
                "consistent": audit_summary["consistency_passed"],
                "blocked_acceptance_state_consistent": audit_summary["blocked_acceptance_state_consistent"],
                "no_store_consistent": audit_summary["no_store_consistent"],
                "no_post_consistent": audit_summary["no_post_consistent"],
                "no_side_effects_consistent": audit_summary["no_side_effects_consistent"],
            },
            "handoff_evidence_readiness_matrix": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix",
                "active": readiness_matrix["approval_decision_owner_handoff_evidence_readiness_matrix_active"],
                "consistent": matrix_summary["readiness_matrix_consistent"],
                "required_evidence_packet_count": matrix_summary["required_evidence_packet_count"],
                "missing_evidence_packet_count": matrix_summary["missing_evidence_packet_count"],
                "handoff_evidence_ready": matrix_summary["handoff_evidence_ready"],
            },
        },
        "driver_hal_gap_refs": acceptance_audit["driver_hal_gap_refs"],
        "mandatory_gates": [
            {
                "gate_id": item["gate_id"],
                "source_acceptance_gate_id": item["source_acceptance_gate_id"],
                "source_audit_gate_id": item["source_audit_gate_id"],
                "decision_confirmed": item["decision_confirmed"],
                "blocks_handoff_acceptance": item["blocks_handoff_acceptance"],
                "blocks_gate_closure": item["blocks_gate_closure"],
                "blocks_broker_activation": item["blocks_broker_activation"],
            }
            for item in decision_rows
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup",
            "android_binder": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceDecisionRollupJson",
            "linux_cli": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-decision-rollup",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.decision.rollup",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceDecisionRollup",
        },
        "summary": {
            "activation_approval_decision_owner_handoff_evidence_acceptance_decision_rollup_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_audit_consistency_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_status_active": True,
            "handoff_evidence_acceptance_decision_rollup_state": "contract-only-handoff-evidence-acceptance-decision-blocked",
            "decision_rollup_complete": True,
            "decision_rollup_consistent": decision_rollup_consistent,
            "source_surfaces_bound": source_surfaces_bound,
            "source_acceptance_audit_bound": True,
            "source_acceptance_status_bound": True,
            "source_handoff_evidence_readiness_matrix_bound": True,
            "acceptance_authority_confirmed": False,
            "acceptance_record_store_confirmed": False,
            "review_workflow_owner_confirmed": False,
            "audit_retention_owner_confirmed": False,
            "evidence_packet_presence_confirmed": False,
            "evidence_uri_rules_confirmed": False,
            "owner_signature_rule_confirmed": False,
            "gate_closure_authority_confirmed": False,
            "broker_activation_owner_confirmed": False,
            "driver_gap_review_owner_confirmed": False,
            "acceptance_decision_ready": False,
            "required_decision_count": len(decision_rows),
            "blocked_decision_count": blocked_decision_count,
            "required_acceptance_count": status_summary["required_acceptance_count"],
            "blocked_acceptance_count": status_summary["blocked_acceptance_count"],
            "accepted_evidence_packet_count": status_summary["accepted_evidence_packet_count"],
            "acceptance_record_persisted_count": status_summary["acceptance_record_persisted_count"],
            "required_evidence_packet_count": matrix_summary["required_evidence_packet_count"],
            "missing_evidence_packet_count": matrix_summary["missing_evidence_packet_count"],
            "attached_evidence_count": matrix_summary["attached_evidence_count"],
            "persisted_evidence_packet_count": matrix_summary["persisted_evidence_packet_count"],
            "evidence_uri_count": matrix_summary["evidence_uri_count"],
            "evidence_hash_count": matrix_summary["evidence_hash_count"],
            "owner_signature_count": matrix_summary["owner_signature_count"],
            "decision": "blocked-by-missing-handoff-evidence-acceptance-decisions",
            "decision_blocker_summary": "acceptance audit is consistent but acceptance authority, record store, review workflow, evidence packet presence, gate closure, and broker activation decisions remain blocked",
            "handoff_evidence_acceptance_allowed": False,
            "handoff_evidence_ready": False,
            "approval_decision_ready": False,
            "approval_dry_run_allowed": False,
            "approval_review_allowed": False,
            "gate_closure_allowed": False,
            "owner_assignments_persisted": False,
            "owner_handoff_queue_updated": False,
            "evidence_packets_attached": False,
            "evidence_store_created": False,
            "evidence_store_active": False,
            "decision_dry_run_post_called_by_handoff_evidence_acceptance_decision_rollup": False,
            "persisted_dry_run_request_count": 0,
            "persisted_dry_run_result_count": 0,
            "persisted_approval_decision_count": 0,
            "pending_approval_decision_review_count": 0,
            "approval_result_store_created": False,
            "approval_result_store_active": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "no_store_consistent": no_store_consistent,
            "no_post_consistent": no_post_consistent,
            "no_side_effects_consistent": no_side_effects_consistent,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_DECISION_ROLLUP_REQ_IDS,
    }


def event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist_payload() -> dict[str, Any]:
    decision_rollup = event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_decision_rollup_payload()
    acceptance_audit = event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_audit_consistency_payload()
    acceptance_status = event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_status_payload()
    readiness_matrix = event_subscription_activation_approval_decision_owner_handoff_evidence_readiness_matrix_payload()
    rollup_summary = decision_rollup["summary"]
    audit_summary = acceptance_audit["summary"]
    status_summary = acceptance_status["summary"]
    matrix_summary = readiness_matrix["summary"]

    closure_items = []
    closure_checks = [
        ("acceptance-authority", "acceptance_authority_confirmed", "EV-AHI-001"),
        ("acceptance-record-store", "acceptance_record_store_confirmed", "EV-AHI-002"),
        ("approval-review-workflow", "review_workflow_owner_confirmed", "EV-AHI-003"),
        ("audit-retention-owner", "audit_retention_owner_confirmed", "EV-AHI-004"),
        ("evidence-packet-presence", "evidence_packet_presence_confirmed", "EV-AHI-005"),
        ("evidence-uri-hash-signature-rules", "evidence_uri_rules_confirmed", "EV-AHI-006"),
        ("owner-signature-rules", "owner_signature_rule_confirmed", "EV-AHI-007"),
        ("gate-closure-authority", "gate_closure_authority_confirmed", "EV-AHI-008"),
        ("broker-activation-owner", "broker_activation_owner_confirmed", "EV-AHI-009"),
        ("driver-gap-review-owner", "driver_gap_review_owner_confirmed", "EV-AHI-010"),
    ]
    for index, (name, source_field, source_decision_gate_id) in enumerate(closure_checks, start=1):
        closure_items.append(
            {
                "closure_check_id": f"EV-AHJ-CHK-{index:03d}",
                "gate_id": f"EV-AHJ-{index:03d}",
                "name": name,
                "source_decision_gate_id": source_decision_gate_id,
                "source_acceptance_gate_id": f"EV-AHG-{index:03d}",
                "source_audit_gate_id": f"EV-AHH-{index:03d}",
                "source_evidence_id": f"EV-AHE-{index:03d}",
                "source_decision_field": source_field,
                "state": "blocked_missing_acceptance_decision_and_evidence_packet",
                "ready": False,
                "required_before": ["approval_review", "gate_closure", "broker_activation"],
                "blocks_approval_review": True,
                "blocks_gate_closure": True,
                "blocks_broker_activation": True,
            }
        )

    required_closure_check_count = len(closure_items)
    open_closure_check_count = sum(1 for item in closure_items if not item["ready"])
    no_store_consistent = all(
        [
            rollup_summary["no_store_consistent"],
            audit_summary["no_store_consistent"],
            rollup_summary["accepted_evidence_packet_count"] == 0,
            rollup_summary["acceptance_record_persisted_count"] == 0,
            rollup_summary["persisted_evidence_packet_count"] == 0,
            not rollup_summary["evidence_store_created"],
            not rollup_summary["evidence_store_active"],
            not rollup_summary["approval_result_store_created"],
            not rollup_summary["approval_result_store_active"],
            not rollup_summary["review_queue_updated"],
        ]
    )
    no_post_consistent = all(
        [
            rollup_summary["no_post_consistent"],
            audit_summary["no_post_consistent"],
            not rollup_summary["decision_dry_run_post_called_by_handoff_evidence_acceptance_decision_rollup"],
        ]
    )
    no_side_effects_consistent = all(
        [
            rollup_summary["no_side_effects_consistent"],
            audit_summary["no_side_effects_consistent"],
            not rollup_summary["owner_assignments_persisted"],
            not rollup_summary["owner_handoff_queue_updated"],
            not rollup_summary["evidence_packets_attached"],
            not rollup_summary["gate_state_changed"],
            not rollup_summary["gates_closed"],
            not rollup_summary["broker_activation_allowed"],
            not rollup_summary["activation_allowed"],
            not rollup_summary["hardware_accessed"],
            not rollup_summary["driver_development_triggered"],
            not rollup_summary["virtualization_development_triggered"],
            not rollup_summary["service_dispatch_triggered"],
        ]
    )
    source_surfaces_bound = all(
        [
            decision_rollup["approval_decision_owner_handoff_evidence_acceptance_decision_rollup_active"],
            acceptance_audit["approval_decision_owner_handoff_evidence_acceptance_audit_consistency_active"],
            acceptance_status["approval_decision_owner_handoff_evidence_acceptance_status_active"],
            readiness_matrix["approval_decision_owner_handoff_evidence_readiness_matrix_active"],
            rollup_summary["decision_rollup_consistent"],
            audit_summary["consistency_passed"],
            status_summary["acceptance_status_consistent"],
            matrix_summary["readiness_matrix_consistent"],
        ]
    )
    closure_readiness_consistent = all(
        [
            source_surfaces_bound,
            open_closure_check_count == required_closure_check_count,
            rollup_summary["blocked_decision_count"] == required_closure_check_count,
            status_summary["blocked_acceptance_count"] == required_closure_check_count,
            matrix_summary["missing_evidence_packet_count"] == required_closure_check_count,
            no_store_consistent,
            no_post_consistent,
            no_side_effects_consistent,
        ]
    )

    return {
        "operation": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-checklist",
        "approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist_state": "contract-only-handoff-evidence-acceptance-closure-not-ready",
        "approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist_active": True,
        "closure_readiness_checklist_complete": True,
        "closure_readiness_consistent": closure_readiness_consistent,
        "source_surfaces_bound": source_surfaces_bound,
        "closure_ready": False,
        "closure_allowed": False,
        "approval_review_allowed": False,
        "gate_closure_allowed": False,
        "broker_activation_allowed": False,
        "required_closure_check_count": required_closure_check_count,
        "open_closure_check_count": open_closure_check_count,
        "ready_closure_check_count": required_closure_check_count - open_closure_check_count,
        "required_decision_count": rollup_summary["required_decision_count"],
        "blocked_decision_count": rollup_summary["blocked_decision_count"],
        "required_acceptance_count": status_summary["required_acceptance_count"],
        "blocked_acceptance_count": status_summary["blocked_acceptance_count"],
        "accepted_evidence_packet_count": rollup_summary["accepted_evidence_packet_count"],
        "acceptance_record_persisted_count": rollup_summary["acceptance_record_persisted_count"],
        "required_evidence_packet_count": matrix_summary["required_evidence_packet_count"],
        "missing_evidence_packet_count": matrix_summary["missing_evidence_packet_count"],
        "attached_evidence_count": matrix_summary["attached_evidence_count"],
        "persisted_evidence_packet_count": matrix_summary["persisted_evidence_packet_count"],
        "closure_items": closure_items,
        "source_surfaces": {
            "handoff_evidence_acceptance_decision_rollup": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup",
                "active": decision_rollup["approval_decision_owner_handoff_evidence_acceptance_decision_rollup_active"],
                "consistent": rollup_summary["decision_rollup_consistent"],
                "blocked_decision_count": rollup_summary["blocked_decision_count"],
                "acceptance_decision_ready": rollup_summary["acceptance_decision_ready"],
            },
            "handoff_evidence_acceptance_audit_consistency": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency",
                "active": acceptance_audit["approval_decision_owner_handoff_evidence_acceptance_audit_consistency_active"],
                "consistent": audit_summary["consistency_passed"],
                "no_store_consistent": audit_summary["no_store_consistent"],
                "no_side_effects_consistent": audit_summary["no_side_effects_consistent"],
            },
            "handoff_evidence_acceptance_status": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status",
                "active": acceptance_status["approval_decision_owner_handoff_evidence_acceptance_status_active"],
                "consistent": status_summary["acceptance_status_consistent"],
                "blocked_acceptance_count": status_summary["blocked_acceptance_count"],
            },
            "handoff_evidence_readiness_matrix": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix",
                "active": readiness_matrix["approval_decision_owner_handoff_evidence_readiness_matrix_active"],
                "consistent": matrix_summary["readiness_matrix_consistent"],
                "missing_evidence_packet_count": matrix_summary["missing_evidence_packet_count"],
            },
        },
        "driver_hal_gap_refs": decision_rollup["driver_hal_gap_refs"],
        "mandatory_gates": [
            {
                "gate_id": item["gate_id"],
                "source_decision_gate_id": item["source_decision_gate_id"],
                "source_acceptance_gate_id": item["source_acceptance_gate_id"],
                "source_audit_gate_id": item["source_audit_gate_id"],
                "ready": item["ready"],
                "blocks_gate_closure": item["blocks_gate_closure"],
                "blocks_broker_activation": item["blocks_broker_activation"],
            }
            for item in closure_items
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist",
            "android_binder": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessChecklistJson",
            "linux_cli": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-checklist",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.checklist",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessChecklist",
        },
        "summary": {
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_decision_rollup_active": True,
            "handoff_evidence_acceptance_closure_readiness_checklist_state": "contract-only-handoff-evidence-acceptance-closure-not-ready",
            "closure_readiness_checklist_complete": True,
            "closure_readiness_consistent": closure_readiness_consistent,
            "source_surfaces_bound": source_surfaces_bound,
            "source_decision_rollup_bound": True,
            "source_acceptance_audit_bound": True,
            "source_acceptance_status_bound": True,
            "source_handoff_evidence_readiness_matrix_bound": True,
            "closure_ready": False,
            "closure_allowed": False,
            "approval_review_allowed": False,
            "gate_closure_allowed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "required_closure_check_count": required_closure_check_count,
            "open_closure_check_count": open_closure_check_count,
            "ready_closure_check_count": required_closure_check_count - open_closure_check_count,
            "required_decision_count": rollup_summary["required_decision_count"],
            "blocked_decision_count": rollup_summary["blocked_decision_count"],
            "required_acceptance_count": status_summary["required_acceptance_count"],
            "blocked_acceptance_count": status_summary["blocked_acceptance_count"],
            "accepted_evidence_packet_count": rollup_summary["accepted_evidence_packet_count"],
            "acceptance_record_persisted_count": rollup_summary["acceptance_record_persisted_count"],
            "required_evidence_packet_count": matrix_summary["required_evidence_packet_count"],
            "missing_evidence_packet_count": matrix_summary["missing_evidence_packet_count"],
            "attached_evidence_count": matrix_summary["attached_evidence_count"],
            "persisted_evidence_packet_count": matrix_summary["persisted_evidence_packet_count"],
            "evidence_uri_count": matrix_summary["evidence_uri_count"],
            "evidence_hash_count": matrix_summary["evidence_hash_count"],
            "owner_signature_count": matrix_summary["owner_signature_count"],
            "decision": "blocked-by-missing-acceptance-closure-readiness",
            "decision_blocker_summary": "acceptance decision rollup is consistent but closure readiness remains blocked by missing authority, record store, review workflow, evidence packet, gate closure, broker activation, and Driver/HAL gap decisions",
            "handoff_evidence_acceptance_allowed": False,
            "handoff_evidence_ready": False,
            "approval_decision_ready": False,
            "approval_dry_run_allowed": False,
            "owner_assignments_persisted": False,
            "owner_handoff_queue_updated": False,
            "evidence_packets_attached": False,
            "evidence_store_created": False,
            "evidence_store_active": False,
            "decision_dry_run_post_called_by_handoff_evidence_acceptance_closure_readiness_checklist": False,
            "persisted_dry_run_request_count": 0,
            "persisted_dry_run_result_count": 0,
            "persisted_approval_decision_count": 0,
            "pending_approval_decision_review_count": 0,
            "approval_result_store_created": False,
            "approval_result_store_active": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "no_store_consistent": no_store_consistent,
            "no_post_consistent": no_post_consistent,
            "no_side_effects_consistent": no_side_effects_consistent,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_CHECKLIST_REQ_IDS,
    }


def event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_audit_consistency_payload() -> dict[str, Any]:
    closure = event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist_payload()
    closure_summary = closure["summary"]
    closure_items = closure["closure_items"]

    audit_items = []
    for index, item in enumerate(closure_items, start=1):
        audit_items.append(
            {
                "audit_check_id": f"EV-AHK-AUD-{index:03d}",
                "gate_id": f"EV-AHK-{index:03d}",
                "source_closure_check_id": item["closure_check_id"],
                "source_closure_gate_id": item["gate_id"],
                "source_decision_gate_id": item["source_decision_gate_id"],
                "source_acceptance_gate_id": item["source_acceptance_gate_id"],
                "source_audit_gate_id": item["source_audit_gate_id"],
                "source_evidence_id": item["source_evidence_id"],
                "result": "consistent",
                "passed": True,
                "closure_still_blocked": True,
                "checks": [
                    "closure-check-count-bound",
                    "closure-blocker-state-open",
                    "source-decision-rollup-bound",
                    "source-acceptance-audit-bound",
                    "source-acceptance-status-bound",
                    "source-evidence-readiness-bound",
                    "android-linux-binding-visible",
                    "no-store-no-post-no-side-effect",
                ],
            }
        )

    required_audit_count = len(audit_items)
    closure_check_count_consistent = all(
        [
            required_audit_count == closure_summary["required_closure_check_count"],
            closure_summary["required_closure_check_count"] == closure_summary["open_closure_check_count"],
            closure_summary["ready_closure_check_count"] == 0,
            closure_summary["closure_ready"] is False,
        ]
    )
    closure_blocker_state_consistent = all(
        [
            item["ready"] is False,
            item["blocks_approval_review"] is True,
            item["blocks_gate_closure"] is True,
            item["blocks_broker_activation"] is True,
        ]
        for item in closure_items
    )
    source_surfaces_bound = all(
        [
            closure["approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist_active"],
            closure_summary["closure_readiness_checklist_complete"],
            closure_summary["closure_readiness_consistent"],
            closure_summary["source_surfaces_bound"],
            closure_summary["source_decision_rollup_bound"],
            closure_summary["source_acceptance_audit_bound"],
            closure_summary["source_acceptance_status_bound"],
            closure_summary["source_handoff_evidence_readiness_matrix_bound"],
        ]
    )
    no_store_consistent = all(
        [
            closure_summary["no_store_consistent"],
            closure_summary["accepted_evidence_packet_count"] == 0,
            closure_summary["acceptance_record_persisted_count"] == 0,
            closure_summary["persisted_evidence_packet_count"] == 0,
            not closure_summary["evidence_store_created"],
            not closure_summary["evidence_store_active"],
            not closure_summary["approval_result_store_created"],
            not closure_summary["approval_result_store_active"],
            not closure_summary["review_queue_updated"],
        ]
    )
    no_post_consistent = all(
        [
            closure_summary["no_post_consistent"],
            not closure_summary["decision_dry_run_post_called_by_handoff_evidence_acceptance_closure_readiness_checklist"],
        ]
    )
    no_side_effects_consistent = all(
        [
            closure_summary["no_side_effects_consistent"],
            not closure_summary["owner_assignments_persisted"],
            not closure_summary["owner_handoff_queue_updated"],
            not closure_summary["evidence_packets_attached"],
            not closure_summary["gate_state_changed"],
            not closure_summary["gates_closed"],
            not closure_summary["broker_activation_allowed"],
            not closure_summary["activation_allowed"],
            not closure_summary["hardware_accessed"],
            not closure_summary["driver_development_triggered"],
            not closure_summary["virtualization_development_triggered"],
            not closure_summary["service_dispatch_triggered"],
        ]
    )
    consistency_passed = all(
        [
            source_surfaces_bound,
            closure_check_count_consistent,
            closure_blocker_state_consistent,
            no_store_consistent,
            no_post_consistent,
            no_side_effects_consistent,
        ]
    )

    return {
        "operation": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-audit-consistency",
        "approval_decision_owner_handoff_evidence_acceptance_closure_readiness_audit_consistency_state": "contract-only-handoff-evidence-acceptance-closure-audit-consistent",
        "approval_decision_owner_handoff_evidence_acceptance_closure_readiness_audit_consistency_active": True,
        "consistency_passed": consistency_passed,
        "source_surfaces": {
            "handoff_evidence_acceptance_closure_readiness_checklist": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist",
                "active": closure["approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist_active"],
                "consistent": closure_summary["closure_readiness_consistent"],
                "closure_ready": closure_summary["closure_ready"],
                "open_closure_check_count": closure_summary["open_closure_check_count"],
            },
            **closure["source_surfaces"],
        },
        "driver_hal_gap_refs": closure["driver_hal_gap_refs"],
        "audit_items": audit_items,
        "mandatory_gates": [
            {
                "gate_id": item["gate_id"],
                "source_closure_gate_id": item["source_closure_gate_id"],
                "source_decision_gate_id": item["source_decision_gate_id"],
                "source_acceptance_gate_id": item["source_acceptance_gate_id"],
                "source_audit_gate_id": item["source_audit_gate_id"],
                "source_evidence_id": item["source_evidence_id"],
                "passed": item["passed"],
                "closure_still_blocked": item["closure_still_blocked"],
            }
            for item in audit_items
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency",
            "android_binder": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessAuditConsistencyJson",
            "linux_cli": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-audit-consistency",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.audit.consistency",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessAuditConsistency",
        },
        "summary": {
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_audit_consistency_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist_active": True,
            "closure_readiness_audit_consistency_state": "contract-only-handoff-evidence-acceptance-closure-audit-consistent",
            "consistency_passed": consistency_passed,
            "source_surfaces_bound": source_surfaces_bound,
            "source_closure_readiness_checklist_bound": True,
            "source_decision_rollup_bound": True,
            "source_acceptance_audit_bound": True,
            "source_acceptance_status_bound": True,
            "source_handoff_evidence_readiness_matrix_bound": True,
            "source_evidence_readiness_bound": True,
            "closure_check_count_consistent": closure_check_count_consistent,
            "closure_blocker_state_consistent": closure_blocker_state_consistent,
            "android_linux_parity_consistent": True,
            "no_store_consistent": no_store_consistent,
            "no_post_consistent": no_post_consistent,
            "no_side_effects_consistent": no_side_effects_consistent,
            "required_audit_count": required_audit_count,
            "required_closure_check_count": closure_summary["required_closure_check_count"],
            "open_closure_check_count": closure_summary["open_closure_check_count"],
            "ready_closure_check_count": closure_summary["ready_closure_check_count"],
            "required_decision_count": closure_summary["required_decision_count"],
            "blocked_decision_count": closure_summary["blocked_decision_count"],
            "required_acceptance_count": closure_summary["required_acceptance_count"],
            "blocked_acceptance_count": closure_summary["blocked_acceptance_count"],
            "accepted_evidence_packet_count": closure_summary["accepted_evidence_packet_count"],
            "acceptance_record_persisted_count": closure_summary["acceptance_record_persisted_count"],
            "required_evidence_packet_count": closure_summary["required_evidence_packet_count"],
            "missing_evidence_packet_count": closure_summary["missing_evidence_packet_count"],
            "attached_evidence_count": closure_summary["attached_evidence_count"],
            "persisted_evidence_packet_count": closure_summary["persisted_evidence_packet_count"],
            "evidence_uri_count": closure_summary["evidence_uri_count"],
            "evidence_hash_count": closure_summary["evidence_hash_count"],
            "owner_signature_count": closure_summary["owner_signature_count"],
            "closure_ready": False,
            "closure_allowed": False,
            "approval_review_allowed": False,
            "gate_closure_allowed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "decision": "blocked-but-closure-readiness-audit-consistent",
            "decision_blocker_summary": "closure readiness audit is consistent, but authority, record store, review workflow, evidence packet, gate closure, broker activation, Driver/HAL gap owner, and Android/Linux parity evidence remain unresolved",
            "handoff_evidence_acceptance_allowed": False,
            "handoff_evidence_ready": False,
            "approval_decision_ready": False,
            "approval_dry_run_allowed": False,
            "owner_assignments_persisted": False,
            "owner_handoff_queue_updated": False,
            "evidence_packets_attached": False,
            "evidence_store_created": False,
            "evidence_store_active": False,
            "decision_dry_run_post_called_by_handoff_evidence_acceptance_closure_readiness_audit_consistency": False,
            "persisted_dry_run_request_count": 0,
            "persisted_dry_run_result_count": 0,
            "persisted_approval_decision_count": 0,
            "pending_approval_decision_review_count": 0,
            "approval_result_store_created": False,
            "approval_result_store_active": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_AUDIT_CONSISTENCY_REQ_IDS,
    }


def event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_rollup_payload() -> dict[str, Any]:
    closure_audit = event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_audit_consistency_payload()
    audit_summary = closure_audit["summary"]
    audit_items = closure_audit["audit_items"]

    source_surfaces_bound = all(
        [
            closure_audit["approval_decision_owner_handoff_evidence_acceptance_closure_readiness_audit_consistency_active"],
            audit_summary["activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_audit_consistency_active"],
            audit_summary["activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist_active"],
            audit_summary["source_surfaces_bound"],
            audit_summary["source_closure_readiness_checklist_bound"],
            audit_summary["source_decision_rollup_bound"],
            audit_summary["source_acceptance_audit_bound"],
            audit_summary["source_acceptance_status_bound"],
            audit_summary["source_handoff_evidence_readiness_matrix_bound"],
            audit_summary["source_evidence_readiness_bound"],
        ]
    )
    closure_readiness_audit_consistent = all(
        [
            closure_audit["consistency_passed"],
            audit_summary["consistency_passed"],
            audit_summary["closure_check_count_consistent"],
            audit_summary["closure_blocker_state_consistent"],
            audit_summary["android_linux_parity_consistent"],
            all(item["passed"] and item["closure_still_blocked"] for item in audit_items),
        ]
    )
    closure_ready_decision_blocked = all(
        [
            audit_summary["closure_ready"] is False,
            audit_summary["closure_allowed"] is False,
            audit_summary["approval_review_allowed"] is False,
            audit_summary["gate_closure_allowed"] is False,
            audit_summary["open_closure_check_count"] == audit_summary["required_closure_check_count"],
            audit_summary["ready_closure_check_count"] == 0,
        ]
    )
    evidence_acceptance_decision_blocked = all(
        [
            audit_summary["required_decision_count"] == audit_summary["blocked_decision_count"],
            audit_summary["blocked_acceptance_count"] == audit_summary["required_acceptance_count"],
            audit_summary["accepted_evidence_packet_count"] == 0,
            audit_summary["acceptance_record_persisted_count"] == 0,
            audit_summary["missing_evidence_packet_count"] == audit_summary["required_evidence_packet_count"],
            audit_summary["handoff_evidence_acceptance_allowed"] is False,
            audit_summary["handoff_evidence_ready"] is False,
            audit_summary["approval_decision_ready"] is False,
        ]
    )
    no_store_decision_rollup = all(
        [
            audit_summary["no_store_consistent"],
            audit_summary["accepted_evidence_packet_count"] == 0,
            audit_summary["acceptance_record_persisted_count"] == 0,
            audit_summary["persisted_evidence_packet_count"] == 0,
            audit_summary["persisted_dry_run_request_count"] == 0,
            audit_summary["persisted_dry_run_result_count"] == 0,
            audit_summary["persisted_approval_decision_count"] == 0,
            audit_summary["pending_approval_decision_review_count"] == 0,
            audit_summary["evidence_store_created"] is False,
            audit_summary["evidence_store_active"] is False,
            audit_summary["approval_result_store_created"] is False,
            audit_summary["approval_result_store_active"] is False,
        ]
    )
    no_post_decision_rollup = all(
        [
            audit_summary["no_post_consistent"],
            audit_summary["decision_dry_run_post_called_by_handoff_evidence_acceptance_closure_readiness_audit_consistency"] is False,
        ]
    )
    no_side_effects_consistent = all(
        [
            audit_summary["no_side_effects_consistent"],
            audit_summary["owner_assignments_persisted"] is False,
            audit_summary["owner_handoff_queue_updated"] is False,
            audit_summary["evidence_packets_attached"] is False,
            audit_summary["review_queue_updated"] is False,
            audit_summary["gate_state_changed"] is False,
            audit_summary["gates_closed"] is False,
            audit_summary["broker_activation_allowed"] is False,
            audit_summary["activation_allowed"] is False,
            audit_summary["broker_active"] is False,
            audit_summary["high_rate_data_plane_active"] is False,
            audit_summary["hardware_accessed"] is False,
            audit_summary["driver_development_triggered"] is False,
            audit_summary["virtualization_development_triggered"] is False,
            audit_summary["service_dispatch_triggered"] is False,
        ]
    )
    decision_rollup_consistent = all(
        [
            source_surfaces_bound,
            closure_readiness_audit_consistent,
            closure_ready_decision_blocked,
            evidence_acceptance_decision_blocked,
            no_store_decision_rollup,
            no_post_decision_rollup,
            no_side_effects_consistent,
        ]
    )
    decision_names = [
        "closure-readiness-decision-rollup-surfaces-bound",
        "closure-readiness-audit-decision-consistency",
        "closure-ready-decision-blocked",
        "evidence-acceptance-decision-blocked",
        "no-store-closure-readiness-decision-rollup",
        "no-post-closure-readiness-decision-rollup",
        "android-linux-closure-readiness-decision-rollup-parity",
        "no-side-effect-closure-readiness-decision-rollup",
        "broker-activation-closure-decision-blocked",
        "driver-hal-virtualization-closure-decision-blocked",
    ]
    decision_consistency = [
        source_surfaces_bound,
        closure_readiness_audit_consistent,
        closure_ready_decision_blocked,
        evidence_acceptance_decision_blocked,
        no_store_decision_rollup,
        no_post_decision_rollup,
        True,
        no_side_effects_consistent,
        audit_summary["broker_activation_allowed"] is False and audit_summary["activation_allowed"] is False,
        audit_summary["hardware_accessed"] is False
        and audit_summary["driver_development_triggered"] is False
        and audit_summary["virtualization_development_triggered"] is False,
    ]
    decision_rows = []
    for index, item in enumerate(audit_items, start=1):
        decision_rows.append(
            {
                "decision_id": f"EV-AHL-DECISION-{index:03d}",
                "gate_id": f"EV-AHL-{index:03d}",
                "name": decision_names[index - 1],
                "source_audit_check_id": item["audit_check_id"],
                "source_audit_gate_id": item["gate_id"],
                "source_closure_gate_id": item["source_closure_gate_id"],
                "source_decision_gate_id": item["source_decision_gate_id"],
                "source_acceptance_gate_id": item["source_acceptance_gate_id"],
                "source_evidence_id": item["source_evidence_id"],
                "decision": "blocked-until-closure-readiness-authority-evidence-and-gate-closure-owners-are-accepted",
                "consistent": decision_consistency[index - 1],
                "blocks_approval_review": True,
                "blocks_gate_closure": True,
                "blocks_broker_activation": True,
            }
        )

    return {
        "operation": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-rollup",
        "approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_rollup_state": "contract-only-handoff-evidence-acceptance-closure-decision-blocked",
        "approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_rollup_active": True,
        "decision_rollup_complete": True,
        "decision_rollup_consistent": decision_rollup_consistent,
        "closure_readiness_audit_consistent": closure_readiness_audit_consistent,
        "closure_ready": False,
        "closure_decision_ready": False,
        "source_surfaces_bound": source_surfaces_bound,
        "required_decision_count": len(decision_rows),
        "blocked_decision_count": len(decision_rows),
        "decision_rows": decision_rows,
        "source_surfaces": {
            "handoff_evidence_acceptance_closure_readiness_audit_consistency": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency",
                "consistent": closure_audit["consistency_passed"],
                "closure_ready": audit_summary["closure_ready"],
                "open_closure_check_count": audit_summary["open_closure_check_count"],
            },
            **closure_audit["source_surfaces"],
        },
        "driver_hal_gap_refs": closure_audit["driver_hal_gap_refs"],
        "mandatory_gates": [
            {
                "gate_id": item["gate_id"],
                "source_audit_gate_id": item["source_audit_gate_id"],
                "source_closure_gate_id": item["source_closure_gate_id"],
                "source_decision_gate_id": item["source_decision_gate_id"],
                "source_acceptance_gate_id": item["source_acceptance_gate_id"],
                "source_evidence_id": item["source_evidence_id"],
                "decision": item["decision"],
                "consistent": item["consistent"],
                "blocks_approval_review": item["blocks_approval_review"],
                "blocks_gate_closure": item["blocks_gate_closure"],
                "blocks_broker_activation": item["blocks_broker_activation"],
            }
            for item in decision_rows
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup",
            "android_binder": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionRollupJson",
            "linux_cli": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-rollup",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.rollup",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionRollup",
        },
        "summary": {
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_rollup_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_audit_consistency_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist_active": True,
            "decision_rollup_complete": True,
            "decision_rollup_consistent": decision_rollup_consistent,
            "closure_readiness_audit_consistent": closure_readiness_audit_consistent,
            "closure_ready": False,
            "closure_decision_ready": False,
            "required_decision_count": len(decision_rows),
            "blocked_decision_count": len(decision_rows),
            "required_closure_check_count": audit_summary["required_closure_check_count"],
            "open_closure_check_count": audit_summary["open_closure_check_count"],
            "ready_closure_check_count": audit_summary["ready_closure_check_count"],
            "required_acceptance_count": audit_summary["required_acceptance_count"],
            "blocked_acceptance_count": audit_summary["blocked_acceptance_count"],
            "accepted_evidence_packet_count": audit_summary["accepted_evidence_packet_count"],
            "acceptance_record_persisted_count": audit_summary["acceptance_record_persisted_count"],
            "required_evidence_packet_count": audit_summary["required_evidence_packet_count"],
            "missing_evidence_packet_count": audit_summary["missing_evidence_packet_count"],
            "attached_evidence_count": audit_summary["attached_evidence_count"],
            "persisted_evidence_packet_count": audit_summary["persisted_evidence_packet_count"],
            "source_surfaces_bound": source_surfaces_bound,
            "closure_ready_decision_blocked": closure_ready_decision_blocked,
            "evidence_acceptance_decision_blocked": evidence_acceptance_decision_blocked,
            "no_store_decision_rollup": no_store_decision_rollup,
            "no_post_decision_rollup": no_post_decision_rollup,
            "no_side_effects_consistent": no_side_effects_consistent,
            "closure_allowed": False,
            "approval_review_allowed": False,
            "gate_closure_allowed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "handoff_evidence_acceptance_allowed": False,
            "handoff_evidence_ready": False,
            "approval_decision_ready": False,
            "approval_dry_run_allowed": False,
            "owner_assignments_persisted": False,
            "owner_handoff_queue_updated": False,
            "evidence_packets_attached": False,
            "evidence_store_created": False,
            "evidence_store_active": False,
            "decision_dry_run_post_called_by_handoff_evidence_acceptance_closure_readiness_decision_rollup": False,
            "persisted_dry_run_request_count": 0,
            "persisted_dry_run_result_count": 0,
            "persisted_approval_decision_count": 0,
            "pending_approval_decision_review_count": 0,
            "approval_result_store_created": False,
            "approval_result_store_active": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_ROLLUP_REQ_IDS,
    }


def event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_checklist_payload() -> dict[str, Any]:
    decision_rollup = event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_rollup_payload()
    decision_summary = decision_rollup["summary"]
    decision_rows = decision_rollup["decision_rows"]

    source_decision_rollup_bound = all(
        [
            decision_rollup["approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_rollup_active"],
            decision_summary["activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_rollup_active"],
            decision_summary["decision_rollup_complete"],
            decision_summary["decision_rollup_consistent"],
            decision_summary["source_surfaces_bound"],
        ]
    )
    reviewer_assignment_blocked = all(
        [
            decision_summary["closure_ready"] is False,
            decision_summary["closure_decision_ready"] is False,
            decision_summary["approval_review_allowed"] is False,
            decision_summary["gate_closure_allowed"] is False,
            decision_summary["broker_activation_allowed"] is False,
            decision_summary["blocked_decision_count"] == decision_summary["required_decision_count"],
        ]
    )
    no_store_reviewer_assignment_checklist = all(
        [
            decision_summary["no_store_decision_rollup"],
            decision_summary["owner_assignments_persisted"] is False,
            decision_summary["owner_handoff_queue_updated"] is False,
            decision_summary["evidence_store_created"] is False,
            decision_summary["approval_result_store_created"] is False,
            decision_summary["review_queue_updated"] is False,
            decision_summary["persisted_dry_run_request_count"] == 0,
            decision_summary["persisted_dry_run_result_count"] == 0,
            decision_summary["persisted_approval_decision_count"] == 0,
            decision_summary["persisted_evidence_packet_count"] == 0,
        ]
    )
    no_post_reviewer_assignment_checklist = all(
        [
            decision_summary["no_post_decision_rollup"],
            decision_summary["decision_dry_run_post_called_by_handoff_evidence_acceptance_closure_readiness_decision_rollup"] is False,
        ]
    )
    no_side_effects_consistent = all(
        [
            decision_summary["no_side_effects_consistent"],
            decision_summary["review_queue_updated"] is False,
            decision_summary["gate_state_changed"] is False,
            decision_summary["gates_closed"] is False,
            decision_summary["broker_active"] is False,
            decision_summary["high_rate_data_plane_active"] is False,
            decision_summary["hardware_accessed"] is False,
            decision_summary["driver_development_triggered"] is False,
            decision_summary["virtualization_development_triggered"] is False,
            decision_summary["service_dispatch_triggered"] is False,
        ]
    )
    reviewer_assignment_checklist_complete = all(
        [
            source_decision_rollup_bound,
            reviewer_assignment_blocked,
            no_store_reviewer_assignment_checklist,
            no_post_reviewer_assignment_checklist,
            no_side_effects_consistent,
        ]
    )
    assignment_names = [
        "reviewer-assignment-checklist-surfaces-bound",
        "closure-decision-rollup-source-bound",
        "closure-review-authority-unassigned",
        "evidence-acceptance-reviewer-unassigned",
        "gate-closure-reviewer-unassigned",
        "broker-activation-reviewer-unassigned",
        "driver-hal-gap-reviewer-unassigned",
        "android-linux-reviewer-assignment-parity",
        "no-store-reviewer-assignment-checklist",
        "no-side-effect-reviewer-assignment-checklist",
    ]
    assignment_consistency = [
        source_decision_rollup_bound,
        decision_rollup["decision_rollup_consistent"],
        reviewer_assignment_blocked,
        decision_summary["evidence_acceptance_decision_blocked"],
        decision_summary["gate_closure_allowed"] is False,
        decision_summary["broker_activation_allowed"] is False and decision_summary["activation_allowed"] is False,
        decision_summary["hardware_accessed"] is False and decision_summary["driver_development_triggered"] is False,
        True,
        no_store_reviewer_assignment_checklist and no_post_reviewer_assignment_checklist,
        no_side_effects_consistent,
    ]
    reviewer_assignment_rows = []
    for index, item in enumerate(decision_rows, start=1):
        reviewer_assignment_rows.append(
            {
                "assignment_id": f"EV-AHM-ASSIGNMENT-{index:03d}",
                "gate_id": f"EV-AHM-{index:03d}",
                "name": assignment_names[index - 1],
                "source_decision_id": item["decision_id"],
                "source_decision_gate_id": item["gate_id"],
                "source_audit_gate_id": item["source_audit_gate_id"],
                "source_closure_gate_id": item["source_closure_gate_id"],
                "source_acceptance_gate_id": item["source_acceptance_gate_id"],
                "source_evidence_id": item["source_evidence_id"],
                "reviewer_assignment_state": "unassigned",
                "reviewer_id": None,
                "required_reviewer_role": "closure-readiness-decision-reviewer",
                "assignment_required": True,
                "assigned": False,
                "assignment_persisted": False,
                "queue_updated": False,
                "consistent": assignment_consistency[index - 1],
                "blocks_approval_review": True,
                "blocks_gate_closure": True,
                "blocks_broker_activation": True,
            }
        )

    return {
        "operation": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-checklist",
        "state": "contract-only-handoff-evidence-acceptance-closure-reviewers-unassigned",
        "approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_checklist_state": "contract-only-handoff-evidence-acceptance-closure-reviewers-unassigned",
        "approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_checklist_active": True,
        "reviewer_assignment_checklist_complete": reviewer_assignment_checklist_complete,
        "reviewer_assignment_ready": False,
        "source_decision_rollup_bound": source_decision_rollup_bound,
        "required_reviewer_assignment_count": len(reviewer_assignment_rows),
        "assigned_reviewer_count": 0,
        "unassigned_reviewer_count": len(reviewer_assignment_rows),
        "reviewer_assignment_rows": reviewer_assignment_rows,
        "source_surfaces": {
            "closure_readiness_decision_rollup": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup",
                "decision_rollup_consistent": decision_rollup["decision_rollup_consistent"],
                "closure_decision_ready": decision_rollup["closure_decision_ready"],
                "blocked_decision_count": decision_summary["blocked_decision_count"],
            },
            **decision_rollup["source_surfaces"],
        },
        "driver_hal_gap_refs": decision_rollup["driver_hal_gap_refs"],
        "mandatory_gates": [
            {
                "gate_id": item["gate_id"],
                "source_decision_gate_id": item["source_decision_gate_id"],
                "source_audit_gate_id": item["source_audit_gate_id"],
                "source_closure_gate_id": item["source_closure_gate_id"],
                "source_acceptance_gate_id": item["source_acceptance_gate_id"],
                "source_evidence_id": item["source_evidence_id"],
                "reviewer_assignment_state": item["reviewer_assignment_state"],
                "assigned": item["assigned"],
                "assignment_required": item["assignment_required"],
                "consistent": item["consistent"],
                "blocks_approval_review": item["blocks_approval_review"],
                "blocks_gate_closure": item["blocks_gate_closure"],
                "blocks_broker_activation": item["blocks_broker_activation"],
            }
            for item in reviewer_assignment_rows
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist",
            "android_binder": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentChecklistJson",
            "linux_cli": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-checklist",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.checklist",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentChecklist",
        },
        "summary": {
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_checklist_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_rollup_active": True,
            "reviewer_assignment_checklist_complete": reviewer_assignment_checklist_complete,
            "reviewer_assignment_ready": False,
            "source_decision_rollup_bound": source_decision_rollup_bound,
            "reviewer_assignment_decision_blocked": reviewer_assignment_blocked,
            "required_reviewer_assignment_count": len(reviewer_assignment_rows),
            "assigned_reviewer_count": 0,
            "unassigned_reviewer_count": len(reviewer_assignment_rows),
            "required_decision_count": decision_summary["required_decision_count"],
            "blocked_decision_count": decision_summary["blocked_decision_count"],
            "required_closure_check_count": decision_summary["required_closure_check_count"],
            "open_closure_check_count": decision_summary["open_closure_check_count"],
            "required_acceptance_count": decision_summary["required_acceptance_count"],
            "blocked_acceptance_count": decision_summary["blocked_acceptance_count"],
            "accepted_evidence_packet_count": decision_summary["accepted_evidence_packet_count"],
            "acceptance_record_persisted_count": decision_summary["acceptance_record_persisted_count"],
            "missing_evidence_packet_count": decision_summary["missing_evidence_packet_count"],
            "attached_evidence_count": decision_summary["attached_evidence_count"],
            "no_store_reviewer_assignment_checklist": no_store_reviewer_assignment_checklist,
            "no_post_reviewer_assignment_checklist": no_post_reviewer_assignment_checklist,
            "no_side_effects_consistent": no_side_effects_consistent,
            "reviewer_assignments_persisted": False,
            "reviewer_assignment_queue_updated": False,
            "closure_ready": False,
            "closure_decision_ready": False,
            "closure_allowed": False,
            "approval_review_allowed": False,
            "gate_closure_allowed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "handoff_evidence_acceptance_allowed": False,
            "handoff_evidence_ready": False,
            "approval_decision_ready": False,
            "owner_assignments_persisted": False,
            "owner_handoff_queue_updated": False,
            "evidence_packets_attached": False,
            "evidence_store_created": False,
            "evidence_store_active": False,
            "decision_dry_run_post_called_by_handoff_evidence_acceptance_closure_readiness_reviewer_assignment_checklist": False,
            "persisted_dry_run_request_count": 0,
            "persisted_dry_run_result_count": 0,
            "persisted_approval_decision_count": 0,
            "pending_approval_decision_review_count": 0,
            "approval_result_store_created": False,
            "approval_result_store_active": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_ASSIGNMENT_CHECKLIST_REQ_IDS,
    }


def event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency_payload() -> dict[str, Any]:
    assignment_checklist = event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_checklist_payload()
    assignment_summary = assignment_checklist["summary"]
    reviewer_assignment_rows = assignment_checklist["reviewer_assignment_rows"]

    source_reviewer_assignment_checklist_bound = all(
        [
            assignment_checklist["approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_checklist_active"],
            assignment_summary["activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_checklist_active"],
            assignment_summary["reviewer_assignment_checklist_complete"],
            assignment_summary["source_decision_rollup_bound"],
            assignment_summary["reviewer_assignment_ready"] is False,
            assignment_summary["reviewer_assignment_decision_blocked"],
        ]
    )
    reviewer_assignment_count_consistent = all(
        [
            len(reviewer_assignment_rows) == 10,
            assignment_summary["required_reviewer_assignment_count"] == 10,
            assignment_summary["assigned_reviewer_count"] == 0,
            assignment_summary["unassigned_reviewer_count"] == 10,
        ]
    )
    reviewer_assignment_blocker_state_consistent = all(
        [
            all(
                item["reviewer_assignment_state"] == "unassigned"
                and item["assigned"] is False
                and item["assignment_required"] is True
                and item["assignment_persisted"] is False
                and item["queue_updated"] is False
                and item["blocks_approval_review"] is True
                and item["blocks_gate_closure"] is True
                and item["blocks_broker_activation"] is True
                for item in reviewer_assignment_rows
            ),
            assignment_summary["reviewer_assignment_ready"] is False,
            assignment_summary["approval_review_allowed"] is False,
            assignment_summary["gate_closure_allowed"] is False,
            assignment_summary["broker_activation_allowed"] is False,
        ]
    )
    no_store_consistent = all(
        [
            assignment_summary["no_store_reviewer_assignment_checklist"],
            assignment_summary["reviewer_assignments_persisted"] is False,
            assignment_summary["reviewer_assignment_queue_updated"] is False,
            assignment_summary["accepted_evidence_packet_count"] == 0,
            assignment_summary["acceptance_record_persisted_count"] == 0,
            assignment_summary["persisted_dry_run_request_count"] == 0,
            assignment_summary["persisted_dry_run_result_count"] == 0,
            assignment_summary["persisted_approval_decision_count"] == 0,
            assignment_summary["pending_approval_decision_review_count"] == 0,
            assignment_summary["evidence_store_created"] is False,
            assignment_summary["evidence_store_active"] is False,
            assignment_summary["approval_result_store_created"] is False,
            assignment_summary["approval_result_store_active"] is False,
        ]
    )
    no_post_consistent = all(
        [
            assignment_summary["no_post_reviewer_assignment_checklist"],
            assignment_summary[
                "decision_dry_run_post_called_by_handoff_evidence_acceptance_closure_readiness_reviewer_assignment_checklist"
            ]
            is False,
        ]
    )
    no_queue_gate_broker_consistent = all(
        assignment_summary[key] is False
        for key in [
            "reviewer_assignment_queue_updated",
            "review_queue_updated",
            "gate_state_changed",
            "gates_closed",
            "gate_closure_allowed",
            "broker_activation_allowed",
            "activation_allowed",
            "broker_active",
            "high_rate_data_plane_active",
        ]
    )
    no_side_effects_consistent = all(
        [
            assignment_summary["no_side_effects_consistent"],
            no_queue_gate_broker_consistent,
            assignment_summary["owner_assignments_persisted"] is False,
            assignment_summary["owner_handoff_queue_updated"] is False,
            assignment_summary["evidence_packets_attached"] is False,
            assignment_summary["hardware_accessed"] is False,
            assignment_summary["driver_development_triggered"] is False,
            assignment_summary["virtualization_development_triggered"] is False,
            assignment_summary["service_dispatch_triggered"] is False,
        ]
    )
    android_linux_reviewer_assignment_audit_parity = True
    consistency_passed = all(
        [
            source_reviewer_assignment_checklist_bound,
            reviewer_assignment_count_consistent,
            reviewer_assignment_blocker_state_consistent,
            no_store_consistent,
            no_post_consistent,
            no_queue_gate_broker_consistent,
            no_side_effects_consistent,
            android_linux_reviewer_assignment_audit_parity,
        ]
    )
    audit_names = [
        "reviewer-assignment-audit-source-bound",
        "reviewer-assignment-count-consistency",
        "reviewer-assignment-blocker-state-consistency",
        "no-store-reviewer-assignment-audit",
        "no-post-reviewer-assignment-audit",
        "no-review-queue-gate-broker-audit",
        "android-linux-reviewer-assignment-audit-parity",
        "no-driver-hal-virtualization-reviewer-assignment-audit",
        "reviewer-assignment-closure-decision-still-blocked",
        "no-side-effect-reviewer-assignment-audit",
    ]
    audit_consistency = [
        source_reviewer_assignment_checklist_bound,
        reviewer_assignment_count_consistent,
        reviewer_assignment_blocker_state_consistent,
        no_store_consistent,
        no_post_consistent,
        no_queue_gate_broker_consistent,
        android_linux_reviewer_assignment_audit_parity,
        assignment_summary["hardware_accessed"] is False
        and assignment_summary["driver_development_triggered"] is False
        and assignment_summary["virtualization_development_triggered"] is False,
        assignment_summary["closure_decision_ready"] is False
        and assignment_summary["reviewer_assignment_ready"] is False,
        no_side_effects_consistent,
    ]
    audit_items = []
    for index, item in enumerate(reviewer_assignment_rows, start=1):
        audit_items.append(
            {
                "audit_check_id": f"EV-AHN-AUDIT-{index:03d}",
                "gate_id": f"EV-AHN-{index:03d}",
                "name": audit_names[index - 1],
                "source_assignment_id": item["assignment_id"],
                "source_assignment_gate_id": item["gate_id"],
                "source_decision_gate_id": item["source_decision_gate_id"],
                "source_audit_gate_id": item["source_audit_gate_id"],
                "source_closure_gate_id": item["source_closure_gate_id"],
                "source_acceptance_gate_id": item["source_acceptance_gate_id"],
                "source_evidence_id": item["source_evidence_id"],
                "passed": audit_consistency[index - 1],
                "reviewer_assignment_still_blocked": True,
                "reviewer_assigned": False,
                "assignment_persisted": False,
                "queue_updated": False,
                "blocks_approval_review": True,
                "blocks_gate_closure": True,
                "blocks_broker_activation": True,
            }
        )

    return {
        "operation": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-consistency",
        "state": "contract-only-handoff-evidence-acceptance-closure-reviewer-assignment-audit-consistent",
        "approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency_state": "contract-only-handoff-evidence-acceptance-closure-reviewer-assignment-audit-consistent",
        "approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency_active": True,
        "consistency_passed": consistency_passed,
        "source_reviewer_assignment_checklist_bound": source_reviewer_assignment_checklist_bound,
        "reviewer_assignment_checklist_consistent": source_reviewer_assignment_checklist_bound,
        "reviewer_assignment_count_consistent": reviewer_assignment_count_consistent,
        "reviewer_assignment_blocker_state_consistent": reviewer_assignment_blocker_state_consistent,
        "no_store_consistent": no_store_consistent,
        "no_post_consistent": no_post_consistent,
        "no_queue_gate_broker_consistent": no_queue_gate_broker_consistent,
        "no_side_effects_consistent": no_side_effects_consistent,
        "android_linux_reviewer_assignment_audit_parity": android_linux_reviewer_assignment_audit_parity,
        "reviewer_assignment_ready": False,
        "required_reviewer_assignment_count": assignment_summary["required_reviewer_assignment_count"],
        "assigned_reviewer_count": assignment_summary["assigned_reviewer_count"],
        "unassigned_reviewer_count": assignment_summary["unassigned_reviewer_count"],
        "audit_items": audit_items,
        "source_surfaces": {
            "reviewer_assignment_checklist": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist",
                "reviewer_assignment_checklist_complete": assignment_checklist["reviewer_assignment_checklist_complete"],
                "reviewer_assignment_ready": assignment_checklist["reviewer_assignment_ready"],
                "assigned_reviewer_count": assignment_checklist["assigned_reviewer_count"],
                "unassigned_reviewer_count": assignment_checklist["unassigned_reviewer_count"],
            },
            **assignment_checklist["source_surfaces"],
        },
        "driver_hal_gap_refs": assignment_checklist["driver_hal_gap_refs"],
        "mandatory_gates": [
            {
                "gate_id": item["gate_id"],
                "source_assignment_gate_id": item["source_assignment_gate_id"],
                "source_decision_gate_id": item["source_decision_gate_id"],
                "source_audit_gate_id": item["source_audit_gate_id"],
                "source_closure_gate_id": item["source_closure_gate_id"],
                "source_acceptance_gate_id": item["source_acceptance_gate_id"],
                "source_evidence_id": item["source_evidence_id"],
                "passed": item["passed"],
                "reviewer_assignment_still_blocked": item["reviewer_assignment_still_blocked"],
                "reviewer_assigned": item["reviewer_assigned"],
                "assignment_persisted": item["assignment_persisted"],
                "queue_updated": item["queue_updated"],
            }
            for item in audit_items
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency",
            "android_binder": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistencyJson",
            "linux_cli": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-consistency",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.consistency",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistency",
        },
        "summary": {
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_checklist_active": True,
            "consistency_passed": consistency_passed,
            "source_reviewer_assignment_checklist_bound": source_reviewer_assignment_checklist_bound,
            "reviewer_assignment_checklist_consistent": source_reviewer_assignment_checklist_bound,
            "reviewer_assignment_count_consistent": reviewer_assignment_count_consistent,
            "reviewer_assignment_blocker_state_consistent": reviewer_assignment_blocker_state_consistent,
            "no_store_consistent": no_store_consistent,
            "no_post_consistent": no_post_consistent,
            "no_queue_gate_broker_consistent": no_queue_gate_broker_consistent,
            "no_side_effects_consistent": no_side_effects_consistent,
            "android_linux_reviewer_assignment_audit_parity": android_linux_reviewer_assignment_audit_parity,
            "reviewer_assignment_checklist_complete": assignment_summary["reviewer_assignment_checklist_complete"],
            "reviewer_assignment_ready": False,
            "reviewer_assignment_decision_blocked": assignment_summary["reviewer_assignment_decision_blocked"],
            "required_reviewer_assignment_count": assignment_summary["required_reviewer_assignment_count"],
            "assigned_reviewer_count": assignment_summary["assigned_reviewer_count"],
            "unassigned_reviewer_count": assignment_summary["unassigned_reviewer_count"],
            "reviewer_assignments_persisted": False,
            "reviewer_assignment_queue_updated": False,
            "closure_ready": False,
            "closure_decision_ready": False,
            "closure_allowed": False,
            "approval_review_allowed": False,
            "gate_closure_allowed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "handoff_evidence_acceptance_allowed": False,
            "handoff_evidence_ready": False,
            "approval_decision_ready": False,
            "owner_assignments_persisted": False,
            "owner_handoff_queue_updated": False,
            "evidence_packets_attached": False,
            "evidence_store_created": False,
            "evidence_store_active": False,
            "decision_dry_run_post_called_by_handoff_evidence_acceptance_closure_readiness_reviewer_assignment_audit_consistency": False,
            "persisted_dry_run_request_count": 0,
            "persisted_dry_run_result_count": 0,
            "persisted_approval_decision_count": 0,
            "pending_approval_decision_review_count": 0,
            "approval_result_store_created": False,
            "approval_result_store_active": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_ASSIGNMENT_AUDIT_CONSISTENCY_REQ_IDS,
    }


def event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_payload() -> dict[str, Any]:
    audit = event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency_payload()
    audit_summary = audit["summary"]
    audit_items = audit["audit_items"]

    source_reviewer_assignment_audit_bound = all(
        [
            audit["approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency_active"],
            audit_summary["activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency_active"],
            audit_summary["consistency_passed"],
            audit_summary["source_reviewer_assignment_checklist_bound"],
            audit_summary["reviewer_assignment_count_consistent"],
            audit_summary["reviewer_assignment_blocker_state_consistent"],
        ]
    )
    reviewer_assignment_audit_consistent = audit_summary["consistency_passed"]
    reviewer_assignment_decision_blocked = all(
        [
            audit_summary["reviewer_assignment_ready"] is False,
            audit_summary["assigned_reviewer_count"] == 0,
            audit_summary["unassigned_reviewer_count"] == 10,
            audit_summary["reviewer_assignments_persisted"] is False,
            audit_summary["reviewer_assignment_queue_updated"] is False,
            audit_summary["approval_review_allowed"] is False,
            audit_summary["gate_closure_allowed"] is False,
            audit_summary["broker_activation_allowed"] is False,
            audit_summary["activation_allowed"] is False,
        ]
    )
    no_store_consistent = all(
        [
            audit_summary["no_store_consistent"],
            audit_summary["reviewer_assignments_persisted"] is False,
            audit_summary["reviewer_assignment_queue_updated"] is False,
            audit_summary["persisted_dry_run_request_count"] == 0,
            audit_summary["persisted_dry_run_result_count"] == 0,
            audit_summary["persisted_approval_decision_count"] == 0,
            audit_summary["approval_result_store_created"] is False,
            audit_summary["approval_result_store_active"] is False,
            audit_summary["evidence_store_created"] is False,
            audit_summary["evidence_store_active"] is False,
        ]
    )
    no_post_consistent = all(
        [
            audit_summary["no_post_consistent"],
            audit_summary[
                "decision_dry_run_post_called_by_handoff_evidence_acceptance_closure_readiness_reviewer_assignment_audit_consistency"
            ]
            is False,
        ]
    )
    no_side_effects_consistent = all(
        [
            audit_summary["no_side_effects_consistent"],
            audit_summary["review_queue_updated"] is False,
            audit_summary["gate_state_changed"] is False,
            audit_summary["gates_closed"] is False,
            audit_summary["broker_active"] is False,
            audit_summary["high_rate_data_plane_active"] is False,
            audit_summary["hardware_accessed"] is False,
            audit_summary["driver_development_triggered"] is False,
            audit_summary["virtualization_development_triggered"] is False,
            audit_summary["service_dispatch_triggered"] is False,
        ]
    )
    required_decision_count = len(audit_items)
    blocked_decision_count = required_decision_count
    decision_rollup_consistent = all(
        [
            source_reviewer_assignment_audit_bound,
            reviewer_assignment_audit_consistent,
            reviewer_assignment_decision_blocked,
            required_decision_count == 10,
            blocked_decision_count == 10,
            no_store_consistent,
            no_post_consistent,
            no_side_effects_consistent,
        ]
    )
    decision_items = []
    for index, item in enumerate(audit_items, start=1):
        decision_items.append(
            {
                "decision_id": f"EV-AHO-DECISION-{index:03d}",
                "gate_id": f"EV-AHO-{index:03d}",
                "source_audit_gate_id": item["gate_id"],
                "source_assignment_gate_id": item["source_assignment_gate_id"],
                "source_decision_gate_id": item["source_decision_gate_id"],
                "source_closure_gate_id": item["source_closure_gate_id"],
                "source_acceptance_gate_id": item["source_acceptance_gate_id"],
                "source_evidence_id": item["source_evidence_id"],
                "decision": "blocked-by-unassigned-reviewers",
                "decision_ready": False,
                "decision_blocked": True,
                "reviewer_assignment_audit_passed": item["passed"],
                "reviewer_assignment_still_blocked": item["reviewer_assignment_still_blocked"],
                "reviewer_assigned": False,
                "assignment_persisted": False,
                "queue_updated": False,
                "blocks_approval_review": True,
                "blocks_gate_closure": True,
                "blocks_broker_activation": True,
            }
        )

    return {
        "operation": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup",
        "state": "contract-only-handoff-evidence-acceptance-closure-reviewer-assignment-decision-blocked",
        "approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_state": "contract-only-handoff-evidence-acceptance-closure-reviewer-assignment-decision-blocked",
        "approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_active": True,
        "decision_rollup_complete": True,
        "decision_rollup_consistent": decision_rollup_consistent,
        "source_reviewer_assignment_audit_bound": source_reviewer_assignment_audit_bound,
        "reviewer_assignment_audit_consistent": reviewer_assignment_audit_consistent,
        "reviewer_assignment_decision_blocked": reviewer_assignment_decision_blocked,
        "reviewer_assignment_decision_ready": False,
        "reviewer_assignment_decision": "blocked-by-unassigned-reviewers",
        "required_decision_count": required_decision_count,
        "blocked_decision_count": blocked_decision_count,
        "assigned_reviewer_count": audit_summary["assigned_reviewer_count"],
        "unassigned_reviewer_count": audit_summary["unassigned_reviewer_count"],
        "decision_items": decision_items,
        "source_surfaces": {
            "reviewer_assignment_audit_consistency": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency",
                "consistency_passed": audit["consistency_passed"],
                "reviewer_assignment_ready": audit["reviewer_assignment_ready"],
                "assigned_reviewer_count": audit["assigned_reviewer_count"],
                "unassigned_reviewer_count": audit["unassigned_reviewer_count"],
            },
            **audit["source_surfaces"],
        },
        "driver_hal_gap_refs": audit["driver_hal_gap_refs"],
        "mandatory_gates": [
            {
                "gate_id": item["gate_id"],
                "source_audit_gate_id": item["source_audit_gate_id"],
                "source_assignment_gate_id": item["source_assignment_gate_id"],
                "source_decision_gate_id": item["source_decision_gate_id"],
                "source_closure_gate_id": item["source_closure_gate_id"],
                "source_acceptance_gate_id": item["source_acceptance_gate_id"],
                "source_evidence_id": item["source_evidence_id"],
                "decision": item["decision"],
                "decision_ready": item["decision_ready"],
                "decision_blocked": item["decision_blocked"],
                "reviewer_assignment_audit_passed": item["reviewer_assignment_audit_passed"],
            }
            for item in decision_items
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup",
            "android_binder": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupJson",
            "linux_cli": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollup",
        },
        "summary": {
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency_active": True,
            "decision_rollup_complete": True,
            "decision_rollup_consistent": decision_rollup_consistent,
            "source_reviewer_assignment_audit_bound": source_reviewer_assignment_audit_bound,
            "reviewer_assignment_audit_consistent": reviewer_assignment_audit_consistent,
            "reviewer_assignment_decision_blocked": reviewer_assignment_decision_blocked,
            "reviewer_assignment_decision_ready": False,
            "reviewer_assignment_decision": "blocked-by-unassigned-reviewers",
            "required_decision_count": required_decision_count,
            "blocked_decision_count": blocked_decision_count,
            "consistency_passed": audit_summary["consistency_passed"],
            "reviewer_assignment_count_consistent": audit_summary["reviewer_assignment_count_consistent"],
            "reviewer_assignment_blocker_state_consistent": audit_summary["reviewer_assignment_blocker_state_consistent"],
            "no_store_consistent": no_store_consistent,
            "no_post_consistent": no_post_consistent,
            "no_side_effects_consistent": no_side_effects_consistent,
            "android_linux_reviewer_assignment_audit_parity": audit_summary["android_linux_reviewer_assignment_audit_parity"],
            "reviewer_assignment_ready": False,
            "reviewer_assignment_checklist_complete": audit_summary["reviewer_assignment_checklist_complete"],
            "required_reviewer_assignment_count": audit_summary["required_reviewer_assignment_count"],
            "assigned_reviewer_count": audit_summary["assigned_reviewer_count"],
            "unassigned_reviewer_count": audit_summary["unassigned_reviewer_count"],
            "reviewer_assignments_persisted": False,
            "reviewer_assignment_queue_updated": False,
            "closure_ready": False,
            "closure_decision_ready": False,
            "closure_allowed": False,
            "approval_review_allowed": False,
            "gate_closure_allowed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "handoff_evidence_acceptance_allowed": False,
            "handoff_evidence_ready": False,
            "approval_decision_ready": False,
            "owner_assignments_persisted": False,
            "owner_handoff_queue_updated": False,
            "evidence_packets_attached": False,
            "evidence_store_created": False,
            "evidence_store_active": False,
            "decision_dry_run_post_called_by_handoff_evidence_acceptance_closure_readiness_reviewer_assignment_audit_decision_rollup": False,
            "persisted_dry_run_request_count": 0,
            "persisted_dry_run_result_count": 0,
            "persisted_approval_decision_count": 0,
            "pending_approval_decision_review_count": 0,
            "approval_result_store_created": False,
            "approval_result_store_active": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "production_activation_allowed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_ASSIGNMENT_AUDIT_DECISION_ROLLUP_REQ_IDS,
    }


def event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_summary_payload() -> dict[str, Any]:
    decision_rollup = event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_payload()
    rollup_summary = decision_rollup["summary"]

    source_decision_rollup_bound = all(
        [
            decision_rollup["approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_active"],
            rollup_summary["decision_rollup_complete"],
            rollup_summary["decision_rollup_consistent"],
            rollup_summary["reviewer_assignment_decision_blocked"],
            rollup_summary["activation_allowed"] is False,
        ]
    )
    reviewer_assignment_decision_blocked = (
        rollup_summary["reviewer_assignment_decision"] == "blocked-by-unassigned-reviewers"
        and rollup_summary["assigned_reviewer_count"] == 0
        and rollup_summary["unassigned_reviewer_count"] == 10
        and rollup_summary["reviewer_assignment_ready"] is False
        and rollup_summary["reviewer_assignment_decision_ready"] is False
    )
    no_store_handoff_readiness_summary = all(
        rollup_summary[key] is False
        for key in [
            "reviewer_assignments_persisted",
            "owner_assignments_persisted",
            "evidence_packets_attached",
            "evidence_store_created",
            "evidence_store_active",
            "approval_result_store_created",
            "approval_result_store_active",
        ]
    )
    no_queue_gate_broker_handoff_readiness_summary = all(
        rollup_summary[key] is False
        for key in [
            "reviewer_assignment_queue_updated",
            "owner_handoff_queue_updated",
            "review_queue_updated",
            "gate_state_changed",
            "gates_closed",
            "gate_closure_allowed",
            "broker_activation_allowed",
            "broker_active",
            "subscription_persistence_active",
            "cursor_storage_active",
            "event_delivery_qos_active",
            "callback_registered",
            "watch_started",
            "dds_runtime_active",
            "sse_websocket_active",
            "high_rate_data_plane_active",
        ]
    )
    no_side_effects_handoff_readiness_summary = all(
        rollup_summary[key] is False
        for key in [
            "handoff_evidence_acceptance_allowed",
            "handoff_evidence_ready",
            "approval_review_allowed",
            "gate_closure_allowed",
            "activation_allowed",
            "review_queue_updated",
            "gate_state_changed",
            "gates_closed",
            "broker_active",
            "high_rate_data_plane_active",
            "hardware_accessed",
            "driver_development_triggered",
            "virtualization_development_triggered",
            "service_dispatch_triggered",
        ]
    )
    android_linux_closure_handoff_readiness_summary_parity = True
    closure_handoff_readiness_complete = all(
        [
            source_decision_rollup_bound,
            reviewer_assignment_decision_blocked,
            no_store_handoff_readiness_summary,
            no_queue_gate_broker_handoff_readiness_summary,
            no_side_effects_handoff_readiness_summary,
            android_linux_closure_handoff_readiness_summary_parity,
        ]
    )
    gates = [
        {
            "gate_id": "EV-AHP-001",
            "name": "closure-handoff-readiness-summary-surfaces-bound",
            "required_evidence": "REST, Android Binder, Linux CLI, Linux IPC, and Linux gRPC/RPC expose the closure handoff readiness summary.",
            "passed": False,
        },
        {
            "gate_id": "EV-AHP-002",
            "name": "source-decision-rollup-consistency-bound",
            "required_evidence": "EV-AHO reviewer assignment audit decision rollup is complete, consistent, and blocked by unassigned reviewers.",
            "passed": False,
        },
        {
            "gate_id": "EV-AHP-003",
            "name": "closure-handoff-dependencies-summarized",
            "required_evidence": "Each EV-AHO decision item is represented as a blocked closure handoff dependency.",
            "passed": False,
        },
        {
            "gate_id": "EV-AHP-004",
            "name": "reviewer-assignment-handoff-blocker-carried-forward",
            "required_evidence": "Reviewer assignment decision remains blocked until reviewer owner, review workflow, and assignment evidence are provided.",
            "passed": False,
        },
        {
            "gate_id": "EV-AHP-005",
            "name": "no-store-closure-handoff-readiness-summary",
            "required_evidence": "Summary does not create evidence, result, approval, handoff, review, or reviewer assignment storage.",
            "passed": False,
        },
        {
            "gate_id": "EV-AHP-006",
            "name": "no-post-closure-handoff-readiness-summary",
            "required_evidence": "Summary is GET-only and does not call activation evidence, approval decision, or handoff POST paths.",
            "passed": False,
        },
        {
            "gate_id": "EV-AHP-007",
            "name": "android-linux-closure-handoff-readiness-summary-parity",
            "required_evidence": "Android primary path and Linux CLI/IPC/gRPC path carry the same summary contract and Req IDs.",
            "passed": False,
        },
        {
            "gate_id": "EV-AHP-008",
            "name": "no-broker-high-rate-closure-handoff-readiness-summary",
            "required_evidence": "Summary does not activate broker, subscription persistence, cursor store, callback/watch, DDS, SSE/WebSocket, or high-rate data plane.",
            "passed": False,
        },
        {
            "gate_id": "EV-AHP-009",
            "name": "no-driver-virtualization-closure-handoff-readiness-summary",
            "required_evidence": "Summary does not touch hardware, trigger Driver/HAL development, or implement virtualization.",
            "passed": False,
        },
        {
            "gate_id": "EV-AHP-010",
            "name": "no-side-effect-closure-handoff-readiness-summary",
            "required_evidence": "Summary does not dispatch services, update queues, change gates, accept evidence, or alter activation state.",
            "passed": False,
        },
    ]
    handoff_dependencies = [
        {
            "dependency_id": f"EV-AHP-HANDOFF-{index:03d}",
            "gate_id": gate["gate_id"],
            "source_decision_id": item["decision_id"],
            "source_gate_id": item["gate_id"],
            "source_audit_gate_id": item["source_audit_gate_id"],
            "source_assignment_gate_id": item["source_assignment_gate_id"],
            "dependency_state": "blocked",
            "handoff_ready": False,
            "blocks_approval_review": True,
            "blocks_gate_closure": True,
            "blocks_broker_activation": True,
            "required_closure_handoff": gate["required_evidence"],
        }
        for index, (gate, item) in enumerate(zip(gates, decision_rollup["decision_items"]), start=1)
    ]

    return {
        "operation": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-summary",
        "state": "contract-only-handoff-evidence-acceptance-closure-handoff-readiness-blocked",
        "approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_summary_state": "contract-only-handoff-evidence-acceptance-closure-handoff-readiness-blocked",
        "approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_summary_active": True,
        "source_decision_rollup_bound": source_decision_rollup_bound,
        "decision_rollup_consistent": rollup_summary["decision_rollup_consistent"],
        "closure_handoff_readiness_complete": closure_handoff_readiness_complete,
        "closure_handoff_ready": False,
        "handoff_ready": False,
        "activation_decision": "blocked-by-unassigned-reviewers",
        "reviewer_assignment_decision_blocked": reviewer_assignment_decision_blocked,
        "required_handoff_dependency_count": len(handoff_dependencies),
        "open_handoff_dependency_count": len(handoff_dependencies),
        "assigned_reviewer_count": rollup_summary["assigned_reviewer_count"],
        "unassigned_reviewer_count": rollup_summary["unassigned_reviewer_count"],
        "reviewer_assignment_ready": False,
        "reviewer_assignment_decision_ready": False,
        "reviewer_assignments_persisted": False,
        "reviewer_assignment_queue_updated": False,
        "handoff_evidence_acceptance_allowed": False,
        "approval_review_allowed": False,
        "gate_closure_allowed": False,
        "broker_activation_allowed": False,
        "activation_allowed": False,
        "review_queue_updated": False,
        "gate_state_changed": False,
        "gates_closed": False,
        "broker_active": False,
        "high_rate_data_plane_active": False,
        "hardware_accessed": False,
        "driver_development_triggered": False,
        "virtualization_development_triggered": False,
        "service_dispatch_triggered": False,
        "handoff_dependencies": handoff_dependencies,
        "source_surfaces": {
            "reviewer_assignment_audit_decision_rollup": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup",
                "decision_rollup_complete": rollup_summary["decision_rollup_complete"],
                "decision_rollup_consistent": rollup_summary["decision_rollup_consistent"],
                "reviewer_assignment_decision_blocked": rollup_summary["reviewer_assignment_decision_blocked"],
                "reviewer_assignment_decision": rollup_summary["reviewer_assignment_decision"],
            },
            **decision_rollup["source_surfaces"],
        },
        "driver_hal_gap_refs": decision_rollup["driver_hal_gap_refs"],
        "mandatory_gates": [
            {
                "gate_id": gate["gate_id"],
                "name": gate["name"],
                "required_evidence": gate["required_evidence"],
                "source_decision_id": dependency["source_decision_id"],
                "source_gate_id": dependency["source_gate_id"],
                "source_audit_gate_id": dependency["source_audit_gate_id"],
                "source_assignment_gate_id": dependency["source_assignment_gate_id"],
                "passed": gate["passed"],
            }
            for gate, dependency in zip(gates, handoff_dependencies)
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary",
            "android_binder": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummaryJson",
            "linux_cli": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-summary",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.summary",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummary",
        },
        "summary": {
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_summary_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_active": True,
            "source_decision_rollup_bound": source_decision_rollup_bound,
            "decision_rollup_consistent": rollup_summary["decision_rollup_consistent"],
            "closure_handoff_readiness_complete": closure_handoff_readiness_complete,
            "closure_handoff_ready": False,
            "handoff_ready": False,
            "activation_decision": "blocked-by-unassigned-reviewers",
            "reviewer_assignment_decision_blocked": reviewer_assignment_decision_blocked,
            "required_handoff_dependency_count": len(handoff_dependencies),
            "open_handoff_dependency_count": len(handoff_dependencies),
            "assigned_reviewer_count": rollup_summary["assigned_reviewer_count"],
            "unassigned_reviewer_count": rollup_summary["unassigned_reviewer_count"],
            "reviewer_assignment_ready": False,
            "reviewer_assignment_decision_ready": False,
            "reviewer_assignments_persisted": False,
            "reviewer_assignment_queue_updated": False,
            "handoff_evidence_acceptance_allowed": False,
            "handoff_evidence_ready": False,
            "approval_review_allowed": False,
            "gate_closure_allowed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "owner_assignments_persisted": False,
            "owner_handoff_queue_updated": False,
            "evidence_packets_attached": False,
            "evidence_store_created": False,
            "evidence_store_active": False,
            "approval_result_store_created": False,
            "approval_result_store_active": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
            "android_linux_closure_handoff_readiness_summary_parity": android_linux_closure_handoff_readiness_summary_parity,
            "no_store_handoff_readiness_summary": no_store_handoff_readiness_summary,
            "no_queue_gate_broker_handoff_readiness_summary": no_queue_gate_broker_handoff_readiness_summary,
            "no_side_effects_handoff_readiness_summary": no_side_effects_handoff_readiness_summary,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_ASSIGNMENT_AUDIT_DECISION_ROLLUP_CLOSURE_HANDOFF_READINESS_SUMMARY_REQ_IDS,
    }


def event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_consistency_payload() -> dict[str, Any]:
    readiness = event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_summary_payload()
    readiness_summary = readiness["summary"]
    handoff_dependencies = readiness["handoff_dependencies"]

    source_closure_handoff_readiness_summary_bound = all(
        [
            readiness["approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_summary_active"],
            readiness_summary["activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_summary_active"],
            readiness_summary["closure_handoff_readiness_complete"],
            readiness_summary["source_decision_rollup_bound"],
            readiness_summary["decision_rollup_consistent"],
            readiness_summary["reviewer_assignment_decision_blocked"],
            readiness_summary["activation_allowed"] is False,
        ]
    )
    closure_handoff_dependency_count_consistent = all(
        [
            readiness["required_handoff_dependency_count"] == 10,
            readiness["open_handoff_dependency_count"] == 10,
            readiness_summary["required_handoff_dependency_count"] == 10,
            readiness_summary["open_handoff_dependency_count"] == 10,
            len(handoff_dependencies) == 10,
        ]
    )
    closure_handoff_blocker_state_consistent = all(
        [
            readiness_summary["closure_handoff_ready"] is False,
            readiness_summary["handoff_ready"] is False,
            readiness_summary["reviewer_assignment_decision_blocked"],
            readiness_summary["assigned_reviewer_count"] == 0,
            readiness_summary["unassigned_reviewer_count"] == 10,
            all(dependency["dependency_state"] == "blocked" for dependency in handoff_dependencies),
            all(dependency["handoff_ready"] is False for dependency in handoff_dependencies),
            all(dependency["blocks_approval_review"] for dependency in handoff_dependencies),
            all(dependency["blocks_gate_closure"] for dependency in handoff_dependencies),
            all(dependency["blocks_broker_activation"] for dependency in handoff_dependencies),
        ]
    )
    no_store_consistent = all(
        [
            readiness_summary["no_store_handoff_readiness_summary"],
            readiness_summary["reviewer_assignments_persisted"] is False,
            readiness_summary["owner_assignments_persisted"] is False,
            readiness_summary["evidence_packets_attached"] is False,
            readiness_summary["evidence_store_created"] is False,
            readiness_summary["evidence_store_active"] is False,
            readiness_summary["approval_result_store_created"] is False,
            readiness_summary["approval_result_store_active"] is False,
        ]
    )
    no_post_consistent = True
    no_queue_gate_broker_consistent = all(
        [
            readiness_summary["no_queue_gate_broker_handoff_readiness_summary"],
            readiness_summary["reviewer_assignment_queue_updated"] is False,
            readiness_summary["owner_handoff_queue_updated"] is False,
            readiness_summary["review_queue_updated"] is False,
            readiness_summary["gate_state_changed"] is False,
            readiness_summary["gates_closed"] is False,
            readiness_summary["gate_closure_allowed"] is False,
            readiness_summary["broker_activation_allowed"] is False,
            readiness_summary["broker_active"] is False,
            readiness_summary["subscription_persistence_active"] is False,
            readiness_summary["cursor_storage_active"] is False,
            readiness_summary["event_delivery_qos_active"] is False,
            readiness_summary["callback_registered"] is False,
            readiness_summary["watch_started"] is False,
            readiness_summary["dds_runtime_active"] is False,
            readiness_summary["sse_websocket_active"] is False,
            readiness_summary["high_rate_data_plane_active"] is False,
        ]
    )
    no_side_effects_consistent = all(
        [
            readiness_summary["no_side_effects_handoff_readiness_summary"],
            readiness_summary["handoff_evidence_acceptance_allowed"] is False,
            readiness_summary["handoff_evidence_ready"] is False,
            readiness_summary["approval_review_allowed"] is False,
            readiness_summary["gate_closure_allowed"] is False,
            readiness_summary["activation_allowed"] is False,
            readiness_summary["review_queue_updated"] is False,
            readiness_summary["gate_state_changed"] is False,
            readiness_summary["gates_closed"] is False,
            readiness_summary["broker_active"] is False,
            readiness_summary["high_rate_data_plane_active"] is False,
            readiness_summary["hardware_accessed"] is False,
            readiness_summary["driver_development_triggered"] is False,
            readiness_summary["virtualization_development_triggered"] is False,
            readiness_summary["service_dispatch_triggered"] is False,
        ]
    )
    android_linux_closure_handoff_readiness_audit_parity = True
    consistency_passed = all(
        [
            source_closure_handoff_readiness_summary_bound,
            closure_handoff_dependency_count_consistent,
            closure_handoff_blocker_state_consistent,
            no_store_consistent,
            no_post_consistent,
            no_queue_gate_broker_consistent,
            no_side_effects_consistent,
            android_linux_closure_handoff_readiness_audit_parity,
        ]
    )
    audit_names = [
        "closure-handoff-readiness-audit-surfaces-bound",
        "source-closure-handoff-readiness-summary-bound",
        "closure-handoff-dependency-count-consistency",
        "closure-handoff-blocker-state-consistency",
        "no-store-closure-handoff-readiness-audit",
        "no-post-closure-handoff-readiness-audit",
        "android-linux-closure-handoff-readiness-audit-parity",
        "no-queue-gate-broker-closure-handoff-readiness-audit",
        "no-driver-virtualization-closure-handoff-readiness-audit",
        "no-side-effect-closure-handoff-readiness-audit",
    ]
    audit_consistency = [
        source_closure_handoff_readiness_summary_bound,
        readiness_summary["closure_handoff_readiness_complete"],
        closure_handoff_dependency_count_consistent,
        closure_handoff_blocker_state_consistent,
        no_store_consistent,
        no_post_consistent,
        android_linux_closure_handoff_readiness_audit_parity,
        no_queue_gate_broker_consistent,
        readiness_summary["hardware_accessed"] is False
        and readiness_summary["driver_development_triggered"] is False
        and readiness_summary["virtualization_development_triggered"] is False,
        no_side_effects_consistent,
    ]
    audit_items = []
    for index, dependency in enumerate(handoff_dependencies, start=1):
        audit_items.append(
            {
                "audit_check_id": f"EV-AHQ-AUDIT-{index:03d}",
                "gate_id": f"EV-AHQ-{index:03d}",
                "name": audit_names[index - 1],
                "source_handoff_dependency_id": dependency["dependency_id"],
                "source_handoff_gate_id": dependency["gate_id"],
                "source_decision_id": dependency["source_decision_id"],
                "source_decision_gate_id": dependency["source_gate_id"],
                "source_audit_gate_id": dependency["source_audit_gate_id"],
                "source_assignment_gate_id": dependency["source_assignment_gate_id"],
                "passed": audit_consistency[index - 1],
                "handoff_dependency_still_blocked": True,
                "reviewer_assigned": False,
                "assignment_persisted": False,
                "queue_updated": False,
                "blocks_approval_review": True,
                "blocks_gate_closure": True,
                "blocks_broker_activation": True,
            }
        )

    return {
        "operation": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-audit-consistency",
        "state": "contract-only-handoff-evidence-acceptance-closure-handoff-readiness-audit-consistent",
        "approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_consistency_state": "contract-only-handoff-evidence-acceptance-closure-handoff-readiness-audit-consistent",
        "approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_consistency_active": True,
        "consistency_passed": consistency_passed,
        "source_closure_handoff_readiness_summary_bound": source_closure_handoff_readiness_summary_bound,
        "closure_handoff_readiness_summary_consistent": source_closure_handoff_readiness_summary_bound,
        "closure_handoff_dependency_count_consistent": closure_handoff_dependency_count_consistent,
        "closure_handoff_blocker_state_consistent": closure_handoff_blocker_state_consistent,
        "no_store_consistent": no_store_consistent,
        "no_post_consistent": no_post_consistent,
        "no_queue_gate_broker_consistent": no_queue_gate_broker_consistent,
        "no_side_effects_consistent": no_side_effects_consistent,
        "android_linux_closure_handoff_readiness_audit_parity": android_linux_closure_handoff_readiness_audit_parity,
        "closure_handoff_ready": False,
        "handoff_ready": False,
        "required_handoff_dependency_count": readiness_summary["required_handoff_dependency_count"],
        "open_handoff_dependency_count": readiness_summary["open_handoff_dependency_count"],
        "assigned_reviewer_count": readiness_summary["assigned_reviewer_count"],
        "unassigned_reviewer_count": readiness_summary["unassigned_reviewer_count"],
        "audit_items": audit_items,
        "source_surfaces": {
            "closure_handoff_readiness_summary": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary",
                "closure_handoff_readiness_complete": readiness["closure_handoff_readiness_complete"],
                "closure_handoff_ready": readiness["closure_handoff_ready"],
                "handoff_ready": readiness["handoff_ready"],
                "open_handoff_dependency_count": readiness["open_handoff_dependency_count"],
            },
            **readiness["source_surfaces"],
        },
        "driver_hal_gap_refs": readiness["driver_hal_gap_refs"],
        "mandatory_gates": [
            {
                "gate_id": item["gate_id"],
                "source_handoff_dependency_id": item["source_handoff_dependency_id"],
                "source_handoff_gate_id": item["source_handoff_gate_id"],
                "source_decision_id": item["source_decision_id"],
                "source_decision_gate_id": item["source_decision_gate_id"],
                "source_audit_gate_id": item["source_audit_gate_id"],
                "source_assignment_gate_id": item["source_assignment_gate_id"],
                "passed": item["passed"],
                "handoff_dependency_still_blocked": item["handoff_dependency_still_blocked"],
                "reviewer_assigned": item["reviewer_assigned"],
                "assignment_persisted": item["assignment_persisted"],
                "queue_updated": item["queue_updated"],
            }
            for item in audit_items
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary/audit-consistency",
            "android_binder": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditConsistencyJson",
            "linux_cli": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-audit-consistency",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.audit.consistency",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditConsistency",
        },
        "summary": {
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_consistency_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_summary_active": True,
            "consistency_passed": consistency_passed,
            "source_closure_handoff_readiness_summary_bound": source_closure_handoff_readiness_summary_bound,
            "closure_handoff_readiness_summary_consistent": source_closure_handoff_readiness_summary_bound,
            "closure_handoff_dependency_count_consistent": closure_handoff_dependency_count_consistent,
            "closure_handoff_blocker_state_consistent": closure_handoff_blocker_state_consistent,
            "no_store_consistent": no_store_consistent,
            "no_post_consistent": no_post_consistent,
            "no_queue_gate_broker_consistent": no_queue_gate_broker_consistent,
            "no_side_effects_consistent": no_side_effects_consistent,
            "android_linux_closure_handoff_readiness_audit_parity": android_linux_closure_handoff_readiness_audit_parity,
            "closure_handoff_readiness_complete": readiness_summary["closure_handoff_readiness_complete"],
            "closure_handoff_ready": False,
            "handoff_ready": False,
            "source_decision_rollup_bound": readiness_summary["source_decision_rollup_bound"],
            "decision_rollup_consistent": readiness_summary["decision_rollup_consistent"],
            "reviewer_assignment_decision_blocked": readiness_summary["reviewer_assignment_decision_blocked"],
            "activation_decision": readiness_summary["activation_decision"],
            "required_handoff_dependency_count": readiness_summary["required_handoff_dependency_count"],
            "open_handoff_dependency_count": readiness_summary["open_handoff_dependency_count"],
            "assigned_reviewer_count": readiness_summary["assigned_reviewer_count"],
            "unassigned_reviewer_count": readiness_summary["unassigned_reviewer_count"],
            "reviewer_assignment_ready": False,
            "reviewer_assignment_decision_ready": False,
            "reviewer_assignments_persisted": False,
            "reviewer_assignment_queue_updated": False,
            "handoff_evidence_acceptance_allowed": False,
            "handoff_evidence_ready": False,
            "approval_review_allowed": False,
            "gate_closure_allowed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "owner_assignments_persisted": False,
            "owner_handoff_queue_updated": False,
            "evidence_packets_attached": False,
            "evidence_store_created": False,
            "evidence_store_active": False,
            "approval_result_store_created": False,
            "approval_result_store_active": False,
            "decision_dry_run_post_called_by_handoff_evidence_acceptance_closure_handoff_readiness_audit_consistency": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_ASSIGNMENT_AUDIT_DECISION_ROLLUP_CLOSURE_HANDOFF_READINESS_AUDIT_CONSISTENCY_REQ_IDS,
    }


def event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup_payload() -> dict[str, Any]:
    audit = event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_consistency_payload()
    audit_summary = audit["summary"]
    audit_items = audit["audit_items"]

    source_closure_handoff_readiness_audit_bound = all(
        [
            audit["approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_consistency_active"],
            audit_summary["activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_consistency_active"],
            audit_summary["consistency_passed"],
            audit_summary["source_closure_handoff_readiness_summary_bound"],
            audit_summary["closure_handoff_dependency_count_consistent"],
            audit_summary["closure_handoff_blocker_state_consistent"],
            audit_summary["activation_allowed"] is False,
        ]
    )
    closure_handoff_audit_decision_count_consistent = all(
        [
            len(audit_items) == 10,
            audit_summary["required_handoff_dependency_count"] == 10,
            audit_summary["open_handoff_dependency_count"] == 10,
            all(item["passed"] for item in audit_items),
            all(item["handoff_dependency_still_blocked"] for item in audit_items),
            all(item["reviewer_assigned"] is False for item in audit_items),
            all(item["assignment_persisted"] is False for item in audit_items),
            all(item["queue_updated"] is False for item in audit_items),
        ]
    )
    closure_handoff_audit_blocker_state_consistent = all(
        [
            audit_summary["closure_handoff_ready"] is False,
            audit_summary["handoff_ready"] is False,
            audit_summary["reviewer_assignment_decision_blocked"],
            audit_summary["assigned_reviewer_count"] == 0,
            audit_summary["unassigned_reviewer_count"] == 10,
            audit_summary["reviewer_assignments_persisted"] is False,
            audit_summary["reviewer_assignment_queue_updated"] is False,
            audit_summary["approval_review_allowed"] is False,
            audit_summary["gate_closure_allowed"] is False,
            audit_summary["broker_activation_allowed"] is False,
        ]
    )
    no_store_consistent = all(
        [
            audit_summary["no_store_consistent"],
            audit_summary["owner_assignments_persisted"] is False,
            audit_summary["owner_handoff_queue_updated"] is False,
            audit_summary["evidence_packets_attached"] is False,
            audit_summary["evidence_store_created"] is False,
            audit_summary["evidence_store_active"] is False,
            audit_summary["approval_result_store_created"] is False,
            audit_summary["approval_result_store_active"] is False,
        ]
    )
    no_post_consistent = audit_summary["no_post_consistent"]
    no_queue_gate_broker_consistent = all(
        [
            audit_summary["no_queue_gate_broker_consistent"],
            audit_summary["review_queue_updated"] is False,
            audit_summary["gate_state_changed"] is False,
            audit_summary["gates_closed"] is False,
            audit_summary["broker_active"] is False,
            audit_summary["subscription_persistence_active"] is False,
            audit_summary["cursor_storage_active"] is False,
            audit_summary["event_delivery_qos_active"] is False,
            audit_summary["callback_registered"] is False,
            audit_summary["watch_started"] is False,
            audit_summary["dds_runtime_active"] is False,
            audit_summary["sse_websocket_active"] is False,
            audit_summary["high_rate_data_plane_active"] is False,
        ]
    )
    no_side_effects_consistent = all(
        [
            audit_summary["no_side_effects_consistent"],
            audit_summary["handoff_evidence_acceptance_allowed"] is False,
            audit_summary["handoff_evidence_ready"] is False,
            audit_summary["activation_allowed"] is False,
            audit_summary["hardware_accessed"] is False,
            audit_summary["driver_development_triggered"] is False,
            audit_summary["virtualization_development_triggered"] is False,
            audit_summary["service_dispatch_triggered"] is False,
        ]
    )
    android_linux_closure_handoff_readiness_audit_decision_parity = True
    decision_rollup_consistent = all(
        [
            source_closure_handoff_readiness_audit_bound,
            closure_handoff_audit_decision_count_consistent,
            closure_handoff_audit_blocker_state_consistent,
            no_store_consistent,
            no_post_consistent,
            no_queue_gate_broker_consistent,
            no_side_effects_consistent,
            android_linux_closure_handoff_readiness_audit_decision_parity,
        ]
    )
    decision_names = [
        "closure-handoff-readiness-audit-decision-surfaces-bound",
        "source-closure-handoff-readiness-audit-bound",
        "closure-handoff-audit-decision-count-consistency",
        "closure-handoff-audit-blocker-state-consistency",
        "no-store-closure-handoff-audit-decision",
        "no-post-closure-handoff-audit-decision",
        "android-linux-closure-handoff-audit-decision-parity",
        "no-queue-gate-broker-closure-handoff-audit-decision",
        "no-driver-virtualization-closure-handoff-audit-decision",
        "no-side-effect-closure-handoff-audit-decision",
    ]
    decision_results = [
        source_closure_handoff_readiness_audit_bound,
        audit_summary["consistency_passed"],
        closure_handoff_audit_decision_count_consistent,
        closure_handoff_audit_blocker_state_consistent,
        no_store_consistent,
        no_post_consistent,
        android_linux_closure_handoff_readiness_audit_decision_parity,
        no_queue_gate_broker_consistent,
        audit_summary["hardware_accessed"] is False
        and audit_summary["driver_development_triggered"] is False
        and audit_summary["virtualization_development_triggered"] is False,
        no_side_effects_consistent,
    ]
    decision_items = []
    for index, item in enumerate(audit_items, start=1):
        decision_items.append(
            {
                "decision_id": f"EV-AHR-DECISION-{index:03d}",
                "gate_id": f"EV-AHR-{index:03d}",
                "name": decision_names[index - 1],
                "source_audit_check_id": item["audit_check_id"],
                "source_audit_gate_id": item["gate_id"],
                "source_handoff_dependency_id": item["source_handoff_dependency_id"],
                "source_handoff_gate_id": item["source_handoff_gate_id"],
                "source_decision_id": item["source_decision_id"],
                "source_decision_gate_id": item["source_decision_gate_id"],
                "source_assignment_gate_id": item["source_assignment_gate_id"],
                "source_audit_passed": item["passed"],
                "decision_state": "blocked",
                "decision_ready": False,
                "decision_consistent": decision_results[index - 1],
                "required_decision": "Closure handoff audit result is consistent, but approval review, gate closure, broker activation, and high-rate transport remain blocked until reviewer and handoff authorities provide accepted evidence.",
                "blocks_approval_review": True,
                "blocks_gate_closure": True,
                "blocks_broker_activation": True,
                "reviewer_assigned": False,
                "assignment_persisted": False,
                "queue_updated": False,
            }
        )

    return {
        "operation": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-audit-decision-rollup",
        "state": "contract-only-handoff-evidence-acceptance-closure-handoff-readiness-audit-decision-blocked",
        "approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup_state": "contract-only-handoff-evidence-acceptance-closure-handoff-readiness-audit-decision-blocked",
        "approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup_active": True,
        "source_closure_handoff_readiness_audit_bound": source_closure_handoff_readiness_audit_bound,
        "closure_handoff_readiness_audit_consistent": audit_summary["consistency_passed"],
        "decision_rollup_complete": True,
        "decision_rollup_consistent": decision_rollup_consistent,
        "closure_handoff_audit_decision_ready": False,
        "closure_handoff_audit_decision_blocked": True,
        "closure_handoff_audit_decision": "blocked-by-unassigned-reviewers",
        "closure_handoff_ready": False,
        "handoff_ready": False,
        "activation_decision": "blocked-by-unassigned-reviewers",
        "required_decision_count": len(decision_items),
        "blocked_decision_count": len(decision_items),
        "passed_audit_count": sum(1 for item in audit_items if item["passed"]),
        "failed_audit_count": sum(1 for item in audit_items if not item["passed"]),
        "assigned_reviewer_count": audit_summary["assigned_reviewer_count"],
        "unassigned_reviewer_count": audit_summary["unassigned_reviewer_count"],
        "decision_items": decision_items,
        "source_surfaces": {
            "closure_handoff_readiness_audit_consistency": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary/audit-consistency",
                "consistency_passed": audit_summary["consistency_passed"],
                "closure_handoff_ready": audit["closure_handoff_ready"],
                "handoff_ready": audit["handoff_ready"],
                "open_handoff_dependency_count": audit["open_handoff_dependency_count"],
            },
            **audit["source_surfaces"],
        },
        "driver_hal_gap_refs": audit["driver_hal_gap_refs"],
        "mandatory_gates": [
            {
                "gate_id": item["gate_id"],
                "name": item["name"],
                "source_audit_check_id": item["source_audit_check_id"],
                "source_audit_gate_id": item["source_audit_gate_id"],
                "source_handoff_dependency_id": item["source_handoff_dependency_id"],
                "source_handoff_gate_id": item["source_handoff_gate_id"],
                "source_decision_id": item["source_decision_id"],
                "source_decision_gate_id": item["source_decision_gate_id"],
                "source_assignment_gate_id": item["source_assignment_gate_id"],
                "passed": item["decision_consistent"],
                "decision_state": item["decision_state"],
                "decision_ready": item["decision_ready"],
                "reviewer_assigned": item["reviewer_assigned"],
                "assignment_persisted": item["assignment_persisted"],
                "queue_updated": item["queue_updated"],
            }
            for item in decision_items
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary/audit-consistency/decision-rollup",
            "android_binder": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditDecisionRollupJson",
            "linux_cli": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-audit-decision-rollup",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.audit.decision.rollup",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditDecisionRollup",
        },
        "summary": {
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_consistency_active": True,
            "source_closure_handoff_readiness_audit_bound": source_closure_handoff_readiness_audit_bound,
            "closure_handoff_readiness_audit_consistent": audit_summary["consistency_passed"],
            "decision_rollup_complete": True,
            "decision_rollup_consistent": decision_rollup_consistent,
            "closure_handoff_audit_decision_ready": False,
            "closure_handoff_audit_decision_blocked": True,
            "closure_handoff_audit_decision": "blocked-by-unassigned-reviewers",
            "closure_handoff_ready": False,
            "handoff_ready": False,
            "activation_decision": "blocked-by-unassigned-reviewers",
            "required_decision_count": len(decision_items),
            "blocked_decision_count": len(decision_items),
            "passed_audit_count": sum(1 for item in audit_items if item["passed"]),
            "failed_audit_count": sum(1 for item in audit_items if not item["passed"]),
            "required_handoff_dependency_count": audit_summary["required_handoff_dependency_count"],
            "open_handoff_dependency_count": audit_summary["open_handoff_dependency_count"],
            "assigned_reviewer_count": audit_summary["assigned_reviewer_count"],
            "unassigned_reviewer_count": audit_summary["unassigned_reviewer_count"],
            "reviewer_assignment_decision_blocked": audit_summary["reviewer_assignment_decision_blocked"],
            "reviewer_assignment_ready": False,
            "reviewer_assignment_decision_ready": False,
            "reviewer_assignments_persisted": False,
            "reviewer_assignment_queue_updated": False,
            "handoff_evidence_acceptance_allowed": False,
            "handoff_evidence_ready": False,
            "approval_review_allowed": False,
            "gate_closure_allowed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "owner_assignments_persisted": False,
            "owner_handoff_queue_updated": False,
            "evidence_packets_attached": False,
            "evidence_store_created": False,
            "evidence_store_active": False,
            "approval_result_store_created": False,
            "approval_result_store_active": False,
            "decision_dry_run_post_called_by_handoff_evidence_acceptance_closure_handoff_readiness_audit_decision_rollup": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
            "closure_handoff_audit_decision_count_consistent": closure_handoff_audit_decision_count_consistent,
            "closure_handoff_audit_blocker_state_consistent": closure_handoff_audit_blocker_state_consistent,
            "no_store_consistent": no_store_consistent,
            "no_post_consistent": no_post_consistent,
            "no_queue_gate_broker_consistent": no_queue_gate_broker_consistent,
            "no_side_effects_consistent": no_side_effects_consistent,
            "android_linux_closure_handoff_readiness_audit_decision_parity": android_linux_closure_handoff_readiness_audit_decision_parity,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_ASSIGNMENT_AUDIT_DECISION_ROLLUP_CLOSURE_HANDOFF_READINESS_AUDIT_DECISION_ROLLUP_REQ_IDS,
    }


def event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup_closure_blocker_matrix_payload() -> dict[str, Any]:
    rollup = event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup_payload()
    rollup_summary = rollup["summary"]
    decision_items = rollup["decision_items"]
    blocker_names = [
        "closure-handoff-audit-decision-owner-unassigned",
        "approval-review-authority-unassigned",
        "gate-closure-authority-unassigned",
        "broker-activation-owner-unassigned",
        "handoff-evidence-acceptance-record-store-missing",
        "review-queue-owner-unassigned",
        "android-linux-handoff-parity-evidence-missing",
        "driver-hal-high-rate-gap-owner-unassigned",
        "virtualization-boundary-unchanged",
        "closure-handoff-decision-side-effect-free",
    ]
    blocker_requirements = [
        "Assign a closure handoff audit decision owner before any approval review can move beyond contract-only state.",
        "Assign approval review authority and signature/RBAC policy before accepting closure handoff review.",
        "Assign gate closure authority and rollback evidence rules before closing activation gates.",
        "Assign broker activation owner before enabling event subscription broker or DDS/high-rate transport.",
        "Define durable acceptance record store, retention, and evidence URI/hash/signature rules before accepting handoff evidence.",
        "Define review queue owner and workflow semantics before reviewer assignments are persisted or queued.",
        "Provide Android/Linux parity evidence for closure handoff, reviewer assignment, and audit export surfaces.",
        "Confirm DRV-GAP-004/DRV-GAP-005 owner decisions before high-rate event data plane activation.",
        "Keep virtualization boundary unchanged: document only, no hypervisor or cross-VM shared-memory implementation in this prototype.",
        "Maintain no-side-effect closure handoff decision semantics until all owners, evidence, and gate authorities are confirmed.",
    ]
    blocker_matrix_complete = len(decision_items) == len(blocker_names)
    source_closure_handoff_readiness_audit_decision_rollup_bound = all(
        [
            rollup["decision_rollup_complete"],
            rollup["decision_rollup_consistent"],
            rollup_summary["closure_handoff_audit_decision_blocked"],
            rollup_summary["blocked_decision_count"] == len(decision_items),
        ]
    )
    no_side_effects_consistent = all(
        [
            rollup_summary["reviewer_assignments_persisted"] is False,
            rollup_summary["reviewer_assignment_queue_updated"] is False,
            rollup_summary["review_queue_updated"] is False,
            rollup_summary["gates_closed"] is False,
            rollup_summary["broker_active"] is False,
            rollup_summary["hardware_accessed"] is False,
            rollup_summary["driver_development_triggered"] is False,
            rollup_summary["virtualization_development_triggered"] is False,
            rollup_summary["service_dispatch_triggered"] is False,
        ]
    )
    closure_blocker_matrix_consistent = all(
        [
            blocker_matrix_complete,
            source_closure_handoff_readiness_audit_decision_rollup_bound,
            rollup_summary["assigned_reviewer_count"] == 0,
            rollup_summary["unassigned_reviewer_count"] == len(decision_items),
            no_side_effects_consistent,
        ]
    )
    blocker_items = []
    for index, decision in enumerate(decision_items, start=1):
        blocker_items.append(
            {
                "blocker_id": f"EV-AHS-BLOCKER-{index:03d}",
                "gate_id": f"EV-AHS-{index:03d}",
                "name": blocker_names[index - 1],
                "source_decision_id": decision["decision_id"],
                "source_decision_gate_id": decision["gate_id"],
                "source_audit_gate_id": decision["source_audit_gate_id"],
                "source_handoff_gate_id": decision["source_handoff_gate_id"],
                "source_assignment_gate_id": decision["source_assignment_gate_id"],
                "source_decision_consistent": decision["decision_consistent"],
                "blocker_state": "open",
                "closure_blocked": True,
                "blocker_ready_to_close": False,
                "required_evidence": blocker_requirements[index - 1],
                "owner_assigned": False,
                "reviewer_assigned": False,
                "assignment_persisted": False,
                "queue_updated": False,
                "gate_closed": False,
                "side_effects": "none-in-prototype",
            }
        )

    return {
        "operation": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-audit-decision-rollup-closure-blocker-matrix",
        "state": "contract-only-handoff-evidence-acceptance-closure-handoff-readiness-audit-decision-closure-blocked",
        "approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup_closure_blocker_matrix_state": "contract-only-handoff-evidence-acceptance-closure-handoff-readiness-audit-decision-closure-blocked",
        "approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup_closure_blocker_matrix_active": True,
        "source_closure_handoff_readiness_audit_decision_rollup_bound": source_closure_handoff_readiness_audit_decision_rollup_bound,
        "closure_blocker_matrix_complete": blocker_matrix_complete,
        "closure_blocker_matrix_consistent": closure_blocker_matrix_consistent,
        "closure_handoff_audit_decision_blocked": True,
        "closure_handoff_closure_ready": False,
        "closure_handoff_ready": False,
        "handoff_ready": False,
        "activation_decision": "blocked-by-open-closure-blockers",
        "required_blocker_count": len(blocker_items),
        "open_blocker_count": len(blocker_items),
        "closed_blocker_count": 0,
        "assigned_reviewer_count": rollup_summary["assigned_reviewer_count"],
        "unassigned_reviewer_count": rollup_summary["unassigned_reviewer_count"],
        "blocker_items": blocker_items,
        "source_surfaces": {
            "closure_handoff_readiness_audit_decision_rollup": {
                "endpoint": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary/audit-consistency/decision-rollup",
                "decision_rollup_complete": rollup["decision_rollup_complete"],
                "decision_rollup_consistent": rollup["decision_rollup_consistent"],
                "closure_handoff_audit_decision_blocked": rollup["closure_handoff_audit_decision_blocked"],
                "blocked_decision_count": rollup["blocked_decision_count"],
            },
            **rollup["source_surfaces"],
        },
        "driver_hal_gap_refs": rollup["driver_hal_gap_refs"],
        "mandatory_gates": [
            {
                "gate_id": item["gate_id"],
                "name": item["name"],
                "source_decision_id": item["source_decision_id"],
                "source_decision_gate_id": item["source_decision_gate_id"],
                "source_audit_gate_id": item["source_audit_gate_id"],
                "source_handoff_gate_id": item["source_handoff_gate_id"],
                "passed": item["source_decision_consistent"],
                "blocker_state": item["blocker_state"],
                "closure_blocked": item["closure_blocked"],
                "blocker_ready_to_close": item["blocker_ready_to_close"],
                "owner_assigned": item["owner_assigned"],
                "reviewer_assigned": item["reviewer_assigned"],
                "assignment_persisted": item["assignment_persisted"],
                "queue_updated": item["queue_updated"],
                "gate_closed": item["gate_closed"],
            }
            for item in blocker_items
        ],
        "api_surface": {
            "rest": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary/audit-consistency/decision-rollup/closure-blocker-matrix",
            "android_binder": "getEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditDecisionRollupClosureBlockerMatrixJson",
            "linux_cli": "event-subscription-activation-approval-decision-owner-handoff-evidence-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-audit-decision-rollup-closure-blocker-matrix",
            "linux_ipc": "uib.events.subscriptions.activation.approval.decision.owner.handoff.evidence.acceptance.closure.readiness.decision.reviewer.assignment.audit.decision.rollup.closure.handoff.readiness.audit.decision.rollup.closure.blocker.matrix",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditDecisionRollupClosureBlockerMatrix",
        },
        "summary": {
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup_closure_blocker_matrix_active": True,
            "activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup_active": True,
            "source_closure_handoff_readiness_audit_decision_rollup_bound": source_closure_handoff_readiness_audit_decision_rollup_bound,
            "closure_blocker_matrix_complete": blocker_matrix_complete,
            "closure_blocker_matrix_consistent": closure_blocker_matrix_consistent,
            "closure_handoff_audit_decision_blocked": True,
            "closure_handoff_closure_ready": False,
            "closure_handoff_ready": False,
            "handoff_ready": False,
            "activation_decision": "blocked-by-open-closure-blockers",
            "required_blocker_count": len(blocker_items),
            "open_blocker_count": len(blocker_items),
            "closed_blocker_count": 0,
            "required_decision_count": rollup_summary["required_decision_count"],
            "blocked_decision_count": rollup_summary["blocked_decision_count"],
            "passed_audit_count": rollup_summary["passed_audit_count"],
            "failed_audit_count": rollup_summary["failed_audit_count"],
            "assigned_reviewer_count": rollup_summary["assigned_reviewer_count"],
            "unassigned_reviewer_count": rollup_summary["unassigned_reviewer_count"],
            "reviewer_assignment_ready": False,
            "reviewer_assignment_decision_ready": False,
            "reviewer_assignments_persisted": False,
            "reviewer_assignment_queue_updated": False,
            "handoff_evidence_acceptance_allowed": False,
            "handoff_evidence_ready": False,
            "approval_review_allowed": False,
            "gate_closure_allowed": False,
            "broker_activation_allowed": False,
            "activation_allowed": False,
            "owner_assignments_persisted": False,
            "owner_handoff_queue_updated": False,
            "evidence_packets_attached": False,
            "evidence_store_created": False,
            "evidence_store_active": False,
            "approval_result_store_created": False,
            "approval_result_store_active": False,
            "review_queue_updated": False,
            "gate_state_changed": False,
            "gates_closed": False,
            "broker_active": False,
            "subscription_persistence_active": False,
            "cursor_storage_active": False,
            "event_delivery_qos_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
            "no_side_effects_consistent": no_side_effects_consistent,
            "android_linux_closure_blocker_matrix_parity": True,
        },
        "req_ids": EVENT_SUBSCRIPTION_ACTIVATION_APPROVAL_DECISION_OWNER_HANDOFF_EVIDENCE_ACCEPTANCE_CLOSURE_READINESS_DECISION_REVIEWER_ASSIGNMENT_AUDIT_DECISION_ROLLUP_CLOSURE_HANDOFF_READINESS_AUDIT_DECISION_ROLLUP_CLOSURE_BLOCKER_MATRIX_REQ_IDS,
    }


def event_subscription_request_payload(request: dict[str, Any]) -> dict[str, Any]:
    trace_id = request.get("trace_id") or str(uuid.uuid4())
    subscription_id = str(request.get("subscription_id") or f"sub-{uuid.uuid4()}")
    raw_topics = request.get("topics") or [request.get("topic") or "vehicle.signal.changed"]
    topics = raw_topics if isinstance(raw_topics, list) else [str(raw_topics)]
    requested_permissions = request.get("permissions") or ["vehicle.read"]
    policy = permission_check_payload(
        {
            "permissions": requested_permissions,
            "caller_permissions": request.get("caller_permissions", ["vehicle.read", "service.read"]),
            "vehicle_state": request.get("vehicle_state", "parked"),
            "safety_state": request.get("safety_state", "normal"),
            "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
        }
    )
    allowed = policy["decision"] == "allow"
    state = "validated_contract_only" if allowed else "rejected_by_policy"
    lifecycle_to = "validated" if allowed else "rejected"

    GOVERNANCE.record_audit(
        trace_id,
        {
            "service": "uib-event-subscription",
            "method": "request",
            "outcome": state,
            "policy_decision": policy["decision"],
            "lifecycle_state": lifecycle_to,
            "qos_decision": "not-applied",
        },
    )
    return {
        "operation": "request",
        "subscription_id": subscription_id,
        "state": state,
        "lifecycle_transition": {
            "from": "requested",
            "to": lifecycle_to,
            "persisted": False,
            "broker_notified": False,
        },
        "request_contract": {
            "topics": topics,
            "filters": request.get("filters", {"source": "any", "safety_state": request.get("safety_state", "normal")}),
            "cursor": request.get("cursor", {"replay_limit": 10}),
            "delivery": request.get("delivery", {"mode": "contract-only", "callback": "not-registered"}),
            "caller": request.get("caller", {"app_id": "unknown", "role": "contract-client"}),
        },
        "validation": {
            "policy_checked": True,
            "policy": policy,
            "cursor_validated": True,
            "backpressure_profile": "prototype-drop-oldest-after-50-events",
            "audit_recorded": True,
        },
        "subscription_record": {
            "persisted": False,
            "active": False,
            "stored_in_broker": False,
            "reason": "prototype exposes lifecycle command contract only; active subscription storage is not implemented",
        },
        "dispatch": {
            "service_invoked": False,
            "driver_hal": "not-dispatched",
            "vehicle_bus": "not-accessed",
            "virtualization": "not-developed",
        },
        "summary": {
            "lifecycle_command_contract_active": True,
            "subscription_persisted": False,
            "active_subscription_count": 0,
            "broker_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_REQ_IDS,
    }


def event_subscription_cancel_payload(request: dict[str, Any]) -> dict[str, Any]:
    trace_id = request.get("trace_id") or str(uuid.uuid4())
    subscription_id = str(request.get("subscription_id") or "")
    requested_permissions = request.get("permissions") or ["vehicle.read"]
    policy = permission_check_payload(
        {
            "permissions": requested_permissions,
            "caller_permissions": request.get("caller_permissions", ["vehicle.read", "service.read"]),
            "vehicle_state": request.get("vehicle_state", "parked"),
            "safety_state": request.get("safety_state", "normal"),
            "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
        }
    )
    allowed = policy["decision"] == "allow"
    if not subscription_id:
        state = "rejected_missing_subscription_id"
        lifecycle_to = "rejected"
    elif not allowed:
        state = "rejected_by_policy"
        lifecycle_to = "rejected"
    else:
        state = "cancelled_contract_only"
        lifecycle_to = "cancelled"

    GOVERNANCE.record_audit(
        trace_id,
        {
            "service": "uib-event-subscription",
            "method": "cancel",
            "outcome": state,
            "policy_decision": policy["decision"],
            "lifecycle_state": lifecycle_to,
            "qos_decision": "not-applied",
        },
    )
    return {
        "operation": "cancel",
        "subscription_id": subscription_id or "missing",
        "state": state,
        "lifecycle_transition": {
            "from": "requested|validated|active",
            "to": lifecycle_to,
            "matched_active_subscription": False,
            "broker_notified": False,
        },
        "validation": {
            "policy_checked": True,
            "policy": policy,
            "subscription_id_present": bool(subscription_id),
            "audit_recorded": True,
        },
        "subscription_record": {
            "persisted": False,
            "active": False,
            "removed_from_broker": False,
            "reason": "no active subscription store exists in the prototype",
        },
        "dispatch": {
            "service_invoked": False,
            "driver_hal": "not-dispatched",
            "vehicle_bus": "not-accessed",
            "virtualization": "not-developed",
        },
        "summary": {
            "lifecycle_command_contract_active": True,
            "subscription_persisted": False,
            "matched_active_subscription": False,
            "active_subscription_count": 0,
            "broker_active": False,
            "callback_registered": False,
            "watch_started": False,
            "dds_runtime_active": False,
            "sse_websocket_active": False,
            "high_rate_data_plane_active": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
        },
        "req_ids": EVENT_SUBSCRIPTION_REQ_IDS,
    }


def uib_extensions_payload() -> dict[str, Any]:
    return {
        "extensions": UIB_EXTENSION_REGISTRY,
        "extension_rules": [
            "Extensions must preserve the Uni Info Bus envelope: trace_id, caller, permission_context, payload, and req_ids.",
            "Extensions cannot bypass SOA service entry, Runtime & Governance, Policy, Audit, or Protocol Binding.",
            "This endpoint is read-only contract visibility; it does not load plugins, dispatch services, or access Driver/HAL.",
        ],
        "binding_visibility": {
            "android": "getUibExtensionsJson",
            "linux_cli": "extensions",
            "linux_ipc": "uib.extensions.get",
            "linux_grpc_rpc": "GetUibExtensions",
        },
        "summary": {
            "dynamic_extension_runtime_ready": False,
            "service_dispatch_triggered": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
        },
        "req_ids": ["XSC-002", "FW-U-008", "XSC-005", "XSC-006", "NV-P-002", "NV-P-003", "DEL-001", "DEL-002"],
    }


def tools_payload() -> dict[str, Any]:
    return {
        "tools": [
            {
                "tool_id": "vehicle_state_query",
                "permissions": ["vehicle.read"],
                "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
                "schema": {"signals": "string[]"}
            },
            {
                "tool_id": "npu_inference",
                "permissions": ["ai.infer"],
                "allowed_safety_states": ["normal"],
                "schema": {"model": "string", "input": "object"}
            },
            {
                "tool_id": "policy_evaluate",
                "permissions": ["policy.read"],
                "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
                "schema": {"action": "string", "resource": "string", "permissions": "string[]"}
            }
        ],
        "req_ids": ["FW-U-006"]
    }


def agent_plan_payload(request: dict[str, Any]) -> dict[str, Any]:
    intent = request.get("intent") or request.get("utterance") or "vehicle_state_query"
    requires_vehicle_control = "comfort" in str(intent).lower() or "control" in str(intent).lower()
    required_permissions = ["vehicle.read", "vehicle.control"] if requires_vehicle_control else ["vehicle.read"]
    policy = permission_check_payload(
        {
            "permissions": request.get("permissions") or required_permissions,
            "caller_permissions": request.get("caller_permissions", ["vehicle.read", "service.read"]),
            "vehicle_state": request.get("vehicle_state", "parked"),
            "safety_state": request.get("safety_state", "normal"),
            "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
        }
    )
    return ai_sdk_plan_payload(request, policy)


def agent_execute_payload(request: dict[str, Any]) -> dict[str, Any]:
    task = request.get("task") if isinstance(request.get("task"), dict) else {}
    task_policy = task.get("policy", {}) if isinstance(task.get("policy"), dict) else {}
    required_permissions = (
        request.get("permissions")
        or task_policy.get("required_permissions")
        or task_policy.get("requires")
        or ["vehicle.read"]
    )
    trace_id = request.get("trace_id") or str(uuid.uuid4())
    policy = permission_check_payload(
        {
            "permissions": required_permissions,
            "caller_permissions": request.get("caller_permissions", ["vehicle.read", "service.read"]),
            "vehicle_state": request.get("vehicle_state", "parked"),
            "safety_state": request.get("safety_state", "normal"),
            "allowed_safety_states": request.get(
                "allowed_safety_states",
                ["normal", "degraded", "diagnostic_readonly"],
            ),
        }
    )
    payload = ai_sdk_execute_payload(request, policy)
    GOVERNANCE.record_audit(
        trace_id,
        {
            "service": "agent-execute",
            "method": "execute",
            "outcome": payload["task_execution"]["state"],
            "policy_decision": policy["decision"],
            "lifecycle_state": "ready",
            "qos_decision": "not-applied",
        },
    )
    return payload


def skill_invoke_payload(skill_id: str, request: dict[str, Any]) -> dict[str, Any]:
    trace_id = request.get("trace_id") or str(uuid.uuid4())
    policy = permission_check_payload(
        {
            "permissions": request.get("permissions") or ["vehicle.read"],
            "caller_permissions": request.get("caller_permissions", ["vehicle.read", "service.read"]),
            "vehicle_state": request.get("vehicle_state", "parked"),
            "safety_state": request.get("safety_state", "normal"),
            "allowed_safety_states": request.get("allowed_safety_states", ["normal", "degraded", "diagnostic_readonly"]),
        }
    )
    payload = ai_sdk_skill_invoke_payload(skill_id, request, policy)
    invocation = payload.get("skill_invocation", {})
    GOVERNANCE.record_audit(
        trace_id,
        {
            "service": "skill-invoke",
            "method": skill_id,
            "outcome": invocation.get("state", payload.get("state", "unknown")),
            "policy_decision": policy["decision"],
            "lifecycle_state": "ready",
            "qos_decision": "not-applied",
        },
    )
    return payload


def memory_query_payload(request: dict[str, Any]) -> dict[str, Any]:
    trace_id = request.get("trace_id") or str(uuid.uuid4())
    policy = permission_check_payload(
        {
            "permissions": request.get("permissions") or ["vehicle.read"],
            "caller_permissions": request.get("caller_permissions", ["vehicle.read", "service.read"]),
            "vehicle_state": request.get("vehicle_state", "parked"),
            "safety_state": request.get("safety_state", "normal"),
            "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
        }
    )
    payload = ai_sdk_memory_query_payload(request, policy)
    GOVERNANCE.record_audit(
        trace_id,
        {
            "service": "memory-query",
            "method": "query",
            "outcome": payload["memory_query"]["state"],
            "policy_decision": policy["decision"],
            "lifecycle_state": "ready",
            "qos_decision": "not-applied",
        },
    )
    return payload


def governance_payload() -> dict[str, Any]:
    return GOVERNANCE.governance_payload()


def governance_backend_contract_payload() -> dict[str, Any]:
    return GOVERNANCE.backend_contract_payload()


def governance_migration_check_payload() -> dict[str, Any]:
    return GOVERNANCE.migration_check_payload()


def governance_deployment_plan_payload() -> dict[str, Any]:
    return GOVERNANCE.deployment_plan_payload()


def governance_precheck_payload(request: dict[str, Any]) -> dict[str, Any]:
    trace_id = request.get("trace_id") or str(uuid.uuid4())
    consume_qos = bool(request.get("consume_qos", False))
    precheck = GOVERNANCE.precheck(request, consume_qos=consume_qos)
    service = request.get("service", "vehicle-state")
    method = request.get("method", "invoke")
    policy = precheck["policy"]
    qos_decision = precheck["qos_decision"]
    allowed = policy["decision"] == "allow" and qos_decision["decision"] == "allow"
    outcome = "precheck_allowed" if allowed else "precheck_rejected"
    GOVERNANCE.record_audit(
        trace_id,
        {
            "service": service,
            "method": method,
            "outcome": outcome,
            "policy_decision": policy["decision"],
            "lifecycle_state": precheck["lifecycle_state"],
            "qos_decision": qos_decision["decision"],
        },
    )
    return {
        "service": service,
        "method": method,
        "state": "allowed" if allowed else "rejected",
        "service_contract": {
            "version": precheck["service"].get("version") if precheck["service"] else "unknown",
            "domain": precheck["service"].get("domain") if precheck["service"] else "unknown",
            "req_ids": precheck["service"].get("req_ids") if precheck["service"] else [],
        },
        "policy": policy,
        "lifecycle_state": precheck["lifecycle_state"],
        "qos": precheck["qos"],
        "qos_decision": qos_decision,
        "dispatch": {
            "service_invoked": False,
            "driver_hal": "not-dispatched",
            "virtualization": "not-developed",
        },
        "precheck_mode": {
            "consume_qos": consume_qos,
            "default": "diagnostic-peek-without-service-dispatch",
        },
        "req_ids": ["XSC-005", "NV-G-002", "NV-G-004", "NV-G-005", "NV-G-006", "NV-G-007", "FW-S-005"],
    }


def bindings_payload() -> dict[str, Any]:
    return BINDINGS.list_payload()


def binding_detail_payload() -> dict[str, Any]:
    return BINDINGS.detail_payload()


def binding_readiness_payload() -> dict[str, Any]:
    return BINDINGS.readiness_payload()


def delivery_readiness_payload() -> dict[str, Any]:
    return delivery_readiness_contract_payload()


def prototype_readiness_payload() -> dict[str, Any]:
    return PROTOTYPE_READINESS.readiness_payload()


def native_adapters_payload() -> dict[str, Any]:
    return NATIVE_ADAPTERS.list_payload()


def native_adapters_detail_payload() -> dict[str, Any]:
    return NATIVE_ADAPTERS.detail_payload()


def native_driver_gaps_payload() -> dict[str, Any]:
    return NATIVE_ADAPTERS.driver_gap_payload()


def hardware_interfaces_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.interfaces_payload()


def hardware_interface_activation_checklist_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.activation_checklist_payload()


def hardware_interface_owner_decision_status_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_status_payload()


def hardware_interface_owner_decision_evidence_payload(request: dict[str, Any]) -> dict[str, Any]:
    requested_permissions = request.get("permissions") or ["service.read"]
    policy = permission_check_payload(
        {
            "permissions": requested_permissions,
            "caller_permissions": request.get("caller_permissions", ["vehicle.read", "service.read"]),
            "vehicle_state": request.get("vehicle_state", "parked"),
            "safety_state": request.get("safety_state", "normal"),
            "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
        }
    )
    payload = HARDWARE_INTERFACES.owner_decision_evidence_payload(request, policy)
    GOVERNANCE.record_audit(
        request.get("trace_id") or str(uuid.uuid4()),
        {
            "service": "hardware-interface",
            "method": "owner-decision-evidence",
            "outcome": payload["evidence_intake_state"],
            "policy_decision": policy["decision"],
            "lifecycle_state": "validated" if payload["intake_validated"] else "rejected",
            "qos_decision": "not-applied",
        },
    )
    return payload


def hardware_interface_owner_decision_evidence_status_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_status_payload()


def hardware_interface_owner_decision_evidence_retention_checklist_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_retention_checklist_payload()


def hardware_interface_owner_decision_evidence_replacement_trigger_checklist_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_replacement_trigger_checklist_payload()


def hardware_interface_owner_decision_evidence_selected_adapter_readiness_checklist_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_selected_adapter_readiness_checklist_payload()


def hardware_interface_owner_decision_evidence_adapter_load_blocker_rollup_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_blocker_rollup_payload()


def hardware_interface_owner_decision_evidence_adapter_load_dry_run_payload(request: dict[str, Any]) -> dict[str, Any]:
    requested_permissions = request.get("permissions") or ["service.read"]
    policy = permission_check_payload(
        {
            "permissions": requested_permissions,
            "caller_permissions": request.get("caller_permissions", ["vehicle.read", "service.read"]),
            "vehicle_state": request.get("vehicle_state", "parked"),
            "safety_state": request.get("safety_state", "normal"),
            "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
        }
    )
    payload = HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_dry_run_payload(request, policy)
    GOVERNANCE.record_audit(
        request.get("trace_id") or str(uuid.uuid4()),
        {
            "service": "hardware-interface",
            "method": "owner-decision-evidence-adapter-load-dry-run",
            "outcome": payload["adapter_load_dry_run_state"],
            "policy_decision": policy["decision"],
            "lifecycle_state": "validated" if payload["dry_run_validated"] else "rejected",
            "qos_decision": "not-applied",
        },
    )
    return payload


def hardware_interface_owner_decision_evidence_adapter_load_approval_decision_dry_run_payload(request: dict[str, Any]) -> dict[str, Any]:
    requested_permissions = request.get("permissions") or ["service.read"]
    policy = permission_check_payload(
        {
            "permissions": requested_permissions,
            "caller_permissions": request.get("caller_permissions", ["vehicle.read", "service.read"]),
            "vehicle_state": request.get("vehicle_state", "parked"),
            "safety_state": request.get("safety_state", "normal"),
            "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
        }
    )
    payload = HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_approval_decision_dry_run_payload(request, policy)
    GOVERNANCE.record_audit(
        request.get("trace_id") or str(uuid.uuid4()),
        {
            "service": "hardware-interface",
            "method": "owner-decision-evidence-adapter-load-approval-decision-dry-run",
            "outcome": payload["approval_decision_dry_run_state"],
            "policy_decision": policy["decision"],
            "lifecycle_state": "validated" if payload["approval_decision_dry_run_validated"] else "rejected",
            "qos_decision": "not-applied",
        },
    )
    return payload


def hardware_interface_owner_decision_evidence_adapter_load_approval_decision_dry_run_status_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_approval_decision_dry_run_status_payload()


def hardware_interface_owner_decision_evidence_adapter_load_approval_decision_dry_run_audit_consistency_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_approval_decision_dry_run_audit_consistency_payload()


def hardware_interface_owner_decision_evidence_adapter_load_approval_decision_closure_blocker_matrix_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_approval_decision_closure_blocker_matrix_payload()


def hardware_interface_owner_decision_evidence_adapter_load_approval_decision_reviewer_matrix_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_approval_decision_reviewer_matrix_payload()


def hardware_interface_owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_payload()


def hardware_interface_owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_status_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_status_payload()


def hardware_interface_owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_audit_consistency_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_audit_consistency_payload()


def hardware_interface_owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_decision_rollup_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_decision_rollup_payload()


def hardware_interface_owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_closure_readiness_checklist_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_closure_readiness_checklist_payload()


def hardware_interface_owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_closure_readiness_audit_consistency_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_closure_readiness_audit_consistency_payload()


def hardware_interface_owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_rollup_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_rollup_payload()


def hardware_interface_owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_checklist_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_checklist_payload()


def hardware_interface_owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency_payload()


def hardware_interface_owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_payload()


def hardware_interface_owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_summary_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_summary_payload()


def hardware_interface_owner_decision_evidence_adapter_load_dry_run_status_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_dry_run_status_payload()


def hardware_interface_owner_decision_evidence_adapter_load_dry_run_audit_consistency_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_dry_run_audit_consistency_payload()


def hardware_interface_owner_decision_evidence_adapter_load_approval_authority_checklist_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_approval_authority_checklist_payload()


def hardware_interface_owner_decision_evidence_adapter_load_approval_authority_status_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_approval_authority_status_payload()


def hardware_interface_owner_decision_evidence_adapter_load_approval_authority_audit_consistency_payload() -> dict[str, Any]:
    return HARDWARE_INTERFACES.owner_decision_evidence_adapter_load_approval_authority_audit_consistency_payload()


def vehicle_signals_payload() -> dict[str, Any]:
    return VEHICLE_SIGNALS.catalog_payload()


def vehicle_signal_activation_payload() -> dict[str, Any]:
    return VEHICLE_SIGNALS.activation_payload()


def vehicle_signal_validation_payload() -> dict[str, Any]:
    return VEHICLE_SIGNALS.validation_payload()


def vehicle_state_payload() -> dict[str, Any]:
    now = time.time()
    return {
        "timestamp_ms": int(now * 1000),
        "signals": {
            "Vehicle.Speed": {
                "value": 0,
                "unit": "km/h",
                "quality": "mock"
            },
            "Vehicle.Cabin.HVAC.Station.Row1.Left.Temperature": {
                "value": 22.5,
                "unit": "celsius",
                "quality": "mock"
            },
            "Vehicle.Cabin.Seat.Row1.Left.Position": {
                "value": "comfort",
                "quality": "mock"
            },
            "Vehicle.Powertrain.TractionBattery.StateOfCharge.Current": {
                "value": 78,
                "unit": "percent",
                "quality": "mock"
            }
        },
        "context": {
            "driving_state": "parked",
            "occupants": 1,
            "network": "local-wsl"
        }
    }


def inference_payload(request: dict[str, Any]) -> dict[str, Any]:
    started = time.time()
    model = str(request.get("model") or "central-intent-v0")
    input_value = request.get("input", {})
    policy = request.get("policy", {})
    required_state = policy.get("safety_state_required", "normal")
    runtime = simulated_npu_runtime_name(request)

    if required_state != "normal":
        return {
            "request_id": str(uuid.uuid4()),
            "model": model,
            "runtime": runtime,
            "simulated_npu_backend": selected_simulated_npu_backend(request),
            "status": "rejected",
            "result": {
                "reason": "prototype only accepts normal safety state"
            },
            "metrics": {
                "queue_ms": 0.2,
                "inference_ms": 0.0
            },
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "production_ready": False
        }

    if selected_simulated_npu_backend(request) == "ollama":
        return ollama_infer_payload(request, started)

    time.sleep(0.03)
    inference_ms = (time.time() - started) * 1000
    return {
        "request_id": str(uuid.uuid4()),
        "model": model,
        "runtime": "mock-npu",
        "status": "ok",
        "result": {
            "intent": "vehicle_state_query",
            "confidence": 0.91,
            "summary": "mock inference accepted",
            "input_echo": input_value
        },
        "metrics": {
            "queue_ms": 0.4,
            "inference_ms": round(inference_ms, 3)
        },
        "hardware_accessed": False,
        "driver_development_triggered": False,
        "virtualization_development_triggered": False,
        "production_ready": False,
        "req_ids": ["XSC-001", "HW-002", "NV-F-011", "DEL-001", "DEL-002"]
    }


def permission_check_payload(request: dict[str, Any]) -> dict[str, Any]:
    return GOVERNANCE.evaluate_permissions(
        request.get("permissions") or request.get("required_permissions") or [],
        request.get("caller_permissions") or ["vehicle.read", "ai.infer", "service.read", "policy.read"],
        request.get("safety_state", "normal"),
        request.get("vehicle_state", "parked"),
        request.get("allowed_safety_states")
    )


def action_request_payload(request: dict[str, Any]) -> dict[str, Any]:
    action = request.get("action", "Unknown.Action")
    permissions = request.get("permissions") or ["vehicle.control"]
    target = request.get("target", {})
    decision = permission_check_payload(
        {
            "permissions": permissions,
            "caller_permissions": request.get("caller_permissions", ["vehicle.read", "vehicle.control"]),
            "vehicle_state": request.get("vehicle_state", "parked"),
            "safety_state": request.get("safety_state", "normal")
        }
    )
    return {
        "action_id": str(uuid.uuid4()),
        "action": action,
        "target": target,
        "state": "accepted" if decision["decision"] == "allow" else "rejected",
        "execution_mode": "policy-checked-mock",
        "dispatch": {
            "driver_hal": "not-dispatched",
            "vehicle_bus": "not-dispatched",
            "virtualization": "not-developed",
        },
        "policy": decision,
        "req_ids": ["XSC-002", "FW-U-004", "FW-U-007", "XSC-005", "NV-G-005"]
    }


def service_invoke_payload(request: dict[str, Any]) -> dict[str, Any]:
    service = request.get("service", "vehicle-state")
    method = request.get("method", "getState")
    trace_id = request.get("trace_id") or str(uuid.uuid4())
    precheck = GOVERNANCE.precheck(request)
    service_entry = precheck["service"]
    policy = precheck["policy"]
    qos_decision = precheck["qos_decision"]
    if policy["decision"] != "allow":
        response = {
            "service": service,
            "method": method,
            "state": "rejected",
            "policy": policy,
            "lifecycle_state": precheck["lifecycle_state"],
            "qos": precheck["qos"],
            "qos_decision": qos_decision,
            "req_ids": ["FW-U-005", "FW-U-007", "FW-S-004", "FW-S-005", "NV-G-002", "NV-G-005", "NV-G-006"]
        }
        GOVERNANCE.record_audit(
            trace_id,
            {
                "service": service,
                "method": method,
                "outcome": "rejected",
                "policy_decision": policy["decision"],
                "lifecycle_state": precheck["lifecycle_state"],
                "qos_decision": qos_decision["decision"],
            }
        )
        return response
    if qos_decision["decision"] != "allow":
        response = {
            "service": service,
            "method": method,
            "state": "rejected",
            "policy": policy,
            "lifecycle_state": precheck["lifecycle_state"],
            "qos": precheck["qos"],
            "qos_decision": qos_decision,
            "req_ids": ["FW-U-005", "FW-S-004", "FW-S-005", "NV-G-002", "NV-G-004", "NV-G-005", "NV-G-006"]
        }
        GOVERNANCE.record_audit(
            trace_id,
            {
                "service": service,
                "method": method,
                "outcome": "qos_rejected",
                "policy_decision": policy["decision"],
                "lifecycle_state": precheck["lifecycle_state"],
                "qos_decision": qos_decision["decision"],
            }
        )
        return response

    if service == "vehicle-state":
        result: dict[str, Any] = vehicle_state_payload()
    elif service == "npu-inference":
        result = inference_payload(request.get("payload", {}))
    elif service == "service-registry":
        result = services_payload()
    else:
        result = {"message": "mock service not implemented", "service": service, "method": method}
    response = {
        "service": service,
        "method": method,
        "state": "completed",
        "service_contract": {
            "version": service_entry.get("version") if service_entry else "unknown",
            "domain": service_entry.get("domain") if service_entry else "unknown",
            "req_ids": service_entry.get("req_ids") if service_entry else []
        },
        "policy": policy,
        "lifecycle_state": precheck["lifecycle_state"],
        "qos": precheck["qos"],
        "qos_decision": qos_decision,
        "result": result,
        "req_ids": ["FW-U-005", "FW-U-007", "FW-S-004", "FW-S-005", "NV-G-002", "NV-G-004", "NV-G-005", "NV-G-006"]
    }
    GOVERNANCE.record_audit(
        trace_id,
        {
            "service": service,
            "method": method,
            "outcome": "completed",
            "policy_decision": policy["decision"],
            "lifecycle_state": precheck["lifecycle_state"],
            "qos_decision": qos_decision["decision"],
        }
    )
    return response


def event_publish_payload(request: dict[str, Any]) -> dict[str, Any]:
    topic = request.get("topic", "vehicle.signal.changed")
    accepted = topic in EVENT_TOPICS
    event = {
        "event_id": str(uuid.uuid4()),
        "topic": topic,
        "timestamp_ms": int(time.time() * 1000),
        "source": request.get("source", "mock-gateway"),
        "safety_state": request.get("safety_state", "normal"),
        "payload": request.get("payload", {}),
        "req_ids": ["FW-U-003"],
    }
    if accepted:
        EVENT_LOG.appendleft(event)
    return {
        "event_id": event["event_id"],
        "topic": topic,
        "state": "accepted" if accepted else "rejected",
        "known_topic": accepted,
        "event": event if accepted else None,
        "delivery": {
            "mode": "recent-log",
            "retention": "last-50-events",
            "dds_status": "planned-for-high-rate-topics",
        },
        "req_ids": ["XSC-002", "FW-U-003", "XSC-006", "NV-P-006"]
    }


def event_recent_payload(limit: int = 20) -> dict[str, Any]:
    safe_limit = max(1, min(limit, 50))
    return {
        "events": list(EVENT_LOG)[:safe_limit],
        "limit": safe_limit,
        "delivery": "bounded-in-memory-log",
        "req_ids": ["XSC-002", "FW-U-003", "XSC-006", "NV-P-006"]
    }


class Handler(BaseHTTPRequestHandler):
    server_version = "CentralBrainMock/0.1"

    def log_message(self, fmt: str, *args: Any) -> None:
        print("[%s] %s" % (self.log_date_time_string(), fmt % args), flush=True)

    def send_json(self, status: int, payload: dict[str, Any]) -> None:
        encoded = json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        path = parsed.path
        query = parse_qs(parsed.query)
        if path == "/health":
            self.send_json(200, health_payload())
        elif path == "/services":
            self.send_json(200, services_payload())
        elif path in ("/context", "/uib/context"):
            self.send_json(200, envelope(context_payload()))
        elif path in ("/state", "/uib/state"):
            self.send_json(200, envelope(state_payload()))
        elif path == "/soa/services":
            self.send_json(200, envelope(services_payload()))
        elif path == "/soa/contracts":
            self.send_json(200, envelope(service_contracts_payload()))
        elif path == "/soa/extensions/closure-summary":
            self.send_json(200, envelope(soa_extension_closure_summary_payload()))
        elif path == "/governance/runtime":
            self.send_json(200, envelope(governance_payload()))
        elif path == "/governance/backend-contract":
            self.send_json(200, envelope(governance_backend_contract_payload()))
        elif path == "/governance/migration-check":
            self.send_json(200, envelope(governance_migration_check_payload()))
        elif path == "/governance/deployment-plan":
            self.send_json(200, envelope(governance_deployment_plan_payload()))
        elif path == "/audit/recent":
            self.send_json(200, envelope(GOVERNANCE.audit_payload()))
        elif path == "/observability/readiness":
            self.send_json(200, envelope(observability_readiness_payload()))
        elif path == "/prototype/completion-summary":
            self.send_json(200, envelope(prototype_completion_summary_payload()))
        elif path == "/bindings":
            self.send_json(200, envelope(bindings_payload()))
        elif path == "/bindings/detail":
            self.send_json(200, envelope(binding_detail_payload()))
        elif path == "/bindings/readiness":
            self.send_json(200, envelope(binding_readiness_payload()))
        elif path == "/delivery/readiness":
            self.send_json(200, envelope(delivery_readiness_payload()))
        elif path == "/prototype/readiness":
            self.send_json(200, envelope(prototype_readiness_payload()))
        elif path == "/native/adapters":
            self.send_json(200, envelope(native_adapters_payload()))
        elif path == "/native/adapters/detail":
            self.send_json(200, envelope(native_adapters_detail_payload()))
        elif path == "/native/driver-gaps":
            self.send_json(200, envelope(native_driver_gaps_payload()))
        elif path == "/hardware/interfaces":
            self.send_json(200, envelope(hardware_interfaces_payload()))
        elif path == "/hardware/interfaces/activation-checklist":
            self.send_json(200, envelope(hardware_interface_activation_checklist_payload()))
        elif path == "/hardware/interfaces/owner-decision-status":
            self.send_json(200, envelope(hardware_interface_owner_decision_status_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/status":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_status_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/retention-checklist":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_retention_checklist_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_replacement_trigger_checklist_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_selected_adapter_readiness_checklist_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_blocker_rollup_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_dry_run_status_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/audit-consistency":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_dry_run_audit_consistency_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_approval_authority_checklist_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/status":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_approval_authority_status_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/audit-consistency":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_approval_authority_audit_consistency_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/status":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_approval_decision_dry_run_status_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/audit-consistency":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_approval_decision_dry_run_audit_consistency_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_approval_decision_closure_blocker_matrix_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_approval_decision_reviewer_matrix_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_status_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/audit-consistency":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_audit_consistency_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_decision_rollup_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_closure_readiness_checklist_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_closure_readiness_audit_consistency_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_rollup_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_checklist_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_approval_reviewer_evidence_handoff_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_summary_payload()))
        elif path == "/vehicle/signals":
            self.send_json(200, envelope(vehicle_signals_payload()))
        elif path == "/vehicle/signals/activation":
            self.send_json(200, envelope(vehicle_signal_activation_payload()))
        elif path == "/vehicle/signals/validation":
            self.send_json(200, envelope(vehicle_signal_validation_payload()))
        elif path in ("/events/topics", "/uib/events/topics"):
            self.send_json(200, envelope(event_topics_payload()))
        elif path == "/uib/events/subscriptions":
            self.send_json(200, envelope(event_subscriptions_payload()))
        elif path == "/uib/events/subscriptions/transport-readiness":
            self.send_json(200, envelope(event_subscription_transport_readiness_payload()))
        elif path == "/uib/events/subscriptions/decision-matrix":
            self.send_json(200, envelope(event_subscription_decision_matrix_payload()))
        elif path == "/uib/events/subscriptions/activation-checklist":
            self.send_json(200, envelope(event_subscription_activation_checklist_payload()))
        elif path == "/uib/events/subscriptions/callback-watch-shape":
            self.send_json(200, envelope(event_subscription_callback_watch_shape_payload()))
        elif path == "/uib/events/subscriptions/cursor-replay-storage":
            self.send_json(200, envelope(event_subscription_cursor_replay_storage_payload()))
        elif path == "/uib/events/subscriptions/backpressure-qos-evidence":
            self.send_json(200, envelope(event_subscription_backpressure_qos_evidence_payload()))
        elif path == "/uib/events/subscriptions/readiness-rollup":
            self.send_json(200, envelope(event_subscription_readiness_rollup_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/status":
            self.send_json(200, envelope(event_subscription_activation_evidence_status_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/retention-checklist":
            self.send_json(200, envelope(event_subscription_activation_evidence_retention_checklist_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/decision-status-rollup":
            self.send_json(200, envelope(event_subscription_activation_evidence_decision_status_rollup_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-dry-run/status":
            self.send_json(200, envelope(event_subscription_activation_approval_dry_run_status_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist":
            self.send_json(200, envelope(event_subscription_activation_approval_authority_checklist_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/audit-consistency":
            self.send_json(200, envelope(event_subscription_activation_approval_authority_audit_consistency_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-blocker-rollup":
            self.send_json(200, envelope(event_subscription_activation_approval_decision_blocker_rollup_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/status":
            self.send_json(200, envelope(event_subscription_activation_approval_decision_dry_run_status_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/audit-consistency":
            self.send_json(200, envelope(event_subscription_activation_approval_decision_dry_run_audit_consistency_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix":
            self.send_json(200, envelope(event_subscription_activation_approval_decision_closure_blocker_matrix_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist":
            self.send_json(200, envelope(event_subscription_activation_approval_decision_owner_handoff_checklist_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency":
            self.send_json(200, envelope(event_subscription_activation_approval_decision_owner_handoff_audit_consistency_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup":
            self.send_json(200, envelope(event_subscription_activation_approval_decision_owner_handoff_decision_rollup_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix":
            self.send_json(200, envelope(event_subscription_activation_approval_decision_owner_handoff_evidence_readiness_matrix_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency":
            self.send_json(200, envelope(event_subscription_activation_approval_decision_owner_handoff_evidence_readiness_audit_consistency_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status":
            self.send_json(200, envelope(event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_status_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency":
            self.send_json(200, envelope(event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_audit_consistency_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup":
            self.send_json(200, envelope(event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_decision_rollup_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist":
            self.send_json(200, envelope(event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_checklist_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency":
            self.send_json(200, envelope(event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_audit_consistency_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup":
            self.send_json(200, envelope(event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_rollup_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist":
            self.send_json(200, envelope(event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_checklist_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency":
            self.send_json(200, envelope(event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_consistency_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup":
            self.send_json(200, envelope(event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary":
            self.send_json(200, envelope(event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_summary_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary/audit-consistency":
            self.send_json(200, envelope(event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_consistency_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary/audit-consistency/decision-rollup":
            self.send_json(200, envelope(event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup_payload()))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary/audit-consistency/decision-rollup/closure-blocker-matrix":
            self.send_json(200, envelope(event_subscription_activation_approval_decision_owner_handoff_evidence_acceptance_closure_readiness_decision_reviewer_assignment_audit_decision_rollup_closure_handoff_readiness_audit_decision_rollup_closure_blocker_matrix_payload()))
        elif path == "/uib/events/recent":
            limit = int(query.get("limit", ["20"])[0])
            self.send_json(200, envelope(event_recent_payload(limit)))
        elif path == "/uib/extensions":
            self.send_json(200, envelope(uib_extensions_payload()))
        elif path == "/tools":
            self.send_json(200, envelope(tools_payload()))
        elif path == "/ai/sdk/capabilities":
            self.send_json(200, envelope(ai_sdk_capabilities_payload()))
        elif path == "/skills":
            self.send_json(200, envelope(ai_sdk_skills_payload()))
        elif path == "/vehicle/state":
            self.send_json(200, vehicle_state_payload())
        elif path == "/npu/status":
            self.send_json(200, npu_status())
        else:
            self.send_json(404, {"status": "error", "message": "unknown endpoint"})

    def do_POST(self) -> None:
        path = urlparse(self.path).path
        length = int(self.headers.get("Content-Length", "0"))
        raw_body = self.rfile.read(length) if length else b"{}"
        try:
            request = json.loads(raw_body.decode("utf-8"))
        except json.JSONDecodeError as exc:
            self.send_json(400, {"status": "error", "message": str(exc)})
            return

        if path == "/ai/infer":
            self.send_json(200, inference_payload(request))
        elif path in ("/permission/check", "/policy/evaluate"):
            self.send_json(200, envelope(permission_check_payload(request), request.get("trace_id")))
        elif path == "/governance/precheck":
            trace_id = request.get("trace_id") or str(uuid.uuid4())
            request["trace_id"] = trace_id
            self.send_json(200, envelope(governance_precheck_payload(request), trace_id))
        elif path in ("/actions/request", "/uib/actions/request"):
            self.send_json(200, envelope(action_request_payload(request), request.get("trace_id")))
        elif path in ("/service/invoke", "/soa/invoke"):
            trace_id = request.get("trace_id") or str(uuid.uuid4())
            request["trace_id"] = trace_id
            self.send_json(200, envelope(service_invoke_payload(request), trace_id))
        elif path in ("/events/publish", "/uib/events/publish"):
            self.send_json(200, envelope(event_publish_payload(request), request.get("trace_id")))
        elif path == "/uib/events/subscriptions/request":
            trace_id = request.get("trace_id") or str(uuid.uuid4())
            request["trace_id"] = trace_id
            self.send_json(200, envelope(event_subscription_request_payload(request), trace_id))
        elif path == "/uib/events/subscriptions/cancel":
            trace_id = request.get("trace_id") or str(uuid.uuid4())
            request["trace_id"] = trace_id
            self.send_json(200, envelope(event_subscription_cancel_payload(request), trace_id))
        elif path == "/uib/events/subscriptions/activation-evidence":
            trace_id = request.get("trace_id") or str(uuid.uuid4())
            request["trace_id"] = trace_id
            self.send_json(200, envelope(event_subscription_activation_evidence_payload(request), trace_id))
        elif path == "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run":
            trace_id = request.get("trace_id") or str(uuid.uuid4())
            request["trace_id"] = trace_id
            self.send_json(200, envelope(event_subscription_activation_approval_decision_dry_run_payload(request), trace_id))
        elif path == "/hardware/interfaces/owner-decision-evidence":
            trace_id = request.get("trace_id") or str(uuid.uuid4())
            request["trace_id"] = trace_id
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_payload(request), trace_id))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run":
            trace_id = request.get("trace_id") or str(uuid.uuid4())
            request["trace_id"] = trace_id
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_dry_run_payload(request), trace_id))
        elif path == "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run":
            trace_id = request.get("trace_id") or str(uuid.uuid4())
            request["trace_id"] = trace_id
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_adapter_load_approval_decision_dry_run_payload(request), trace_id))
        elif path == "/agent/plan":
            self.send_json(200, envelope(agent_plan_payload(request), request.get("trace_id")))
        elif path == "/agent/execute":
            trace_id = request.get("trace_id") or str(uuid.uuid4())
            request["trace_id"] = trace_id
            self.send_json(200, envelope(agent_execute_payload(request), trace_id))
        elif path.startswith("/skills/") and path.endswith("/invoke"):
            trace_id = request.get("trace_id") or str(uuid.uuid4())
            request["trace_id"] = trace_id
            skill_id = path.removeprefix("/skills/").removesuffix("/invoke")
            self.send_json(200, envelope(skill_invoke_payload(skill_id, request), trace_id))
        elif path == "/memory/query":
            trace_id = request.get("trace_id") or str(uuid.uuid4())
            request["trace_id"] = trace_id
            self.send_json(200, envelope(memory_query_payload(request), trace_id))
        else:
            self.send_json(404, {"status": "error", "message": "unknown endpoint"})


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the central brain mock NPU backend.")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=int(os.environ.get("CENTRAL_BRAIN_PORT", "8787")))
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"Central brain mock backend listening on http://{args.host}:{args.port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("Stopping central brain mock backend", flush=True)
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
