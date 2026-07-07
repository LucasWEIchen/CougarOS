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
from prototype_readiness import PrototypeReadinessRegistry
from protocol_bindings import ProtocolBindingRegistry
from runtime_governance import RuntimeGovernance
from vehicle_signals import VehicleSignalRegistry


STARTED_AT = time.time()
API_VERSION = "0.1.40"
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
        "runtime": "mock-npu",
        "device_node": requested_device,
        "requested_vendor": requested_vendor,
        "pci_devices_sample": pci_devices,
        "matched_devices": matched,
        "model_slots": [
            {
                "model": "central-intent-v0",
                "state": "loaded",
                "backend": mode
            },
            {
                "model": "vehicle-scene-v0",
                "state": "loaded",
                "backend": mode
            }
        ]
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
            },
        },
        "transport_candidates": [
            {
                "binding": "android-binder-aidl",
                "operation": "getEventSubscriptionsJson/requestEventSubscriptionJson/cancelEventSubscriptionJson/getEventSubscriptionTransportReadinessJson/getEventSubscriptionDecisionMatrixJson/getEventSubscriptionActivationChecklistJson/getEventSubscriptionCallbackWatchShapeJson",
                "current_state": "contract-only lifecycle commands; callback registration not implemented",
            },
            {
                "binding": "linux-ipc",
                "operation": "uib.events.subscriptions.get/request/cancel/transport.readiness/decision.matrix/activation.checklist/callback.watch.shape",
                "current_state": "contract-only lifecycle commands; watch operation not implemented",
            },
            {
                "binding": "linux-grpc-rpc",
                "operation": "CentralBrainGateway.GetEventSubscriptions/RequestEventSubscription/CancelEventSubscription/GetEventSubscriptionTransportReadiness/GetEventSubscriptionDecisionMatrix/GetEventSubscriptionActivationChecklist/GetEventSubscriptionCallbackWatchShape",
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
            "android_binder": "getEventSubscriptionsJson",
            "android_binder_request": "requestEventSubscriptionJson",
            "android_binder_cancel": "cancelEventSubscriptionJson",
            "android_binder_transport_readiness": "getEventSubscriptionTransportReadinessJson",
            "android_binder_decision_matrix": "getEventSubscriptionDecisionMatrixJson",
            "android_binder_activation_checklist": "getEventSubscriptionActivationChecklistJson",
            "android_binder_callback_watch_shape": "getEventSubscriptionCallbackWatchShapeJson",
            "linux_cli": "event-subscriptions",
            "linux_cli_request": "event-subscribe-request",
            "linux_cli_cancel": "event-subscribe-cancel",
            "linux_cli_transport_readiness": "event-subscription-transport-readiness",
            "linux_cli_decision_matrix": "event-subscription-decision-matrix",
            "linux_cli_activation_checklist": "event-subscription-activation-checklist",
            "linux_cli_callback_watch_shape": "event-subscription-callback-watch-shape",
            "linux_ipc": "uib.events.subscriptions.get",
            "linux_ipc_request": "uib.events.subscriptions.request",
            "linux_ipc_cancel": "uib.events.subscriptions.cancel",
            "linux_ipc_transport_readiness": "uib.events.subscriptions.transport.readiness",
            "linux_ipc_decision_matrix": "uib.events.subscriptions.decision.matrix",
            "linux_ipc_activation_checklist": "uib.events.subscriptions.activation.checklist",
            "linux_ipc_callback_watch_shape": "uib.events.subscriptions.callback.watch.shape",
            "linux_grpc_rpc": "CentralBrainGateway.GetEventSubscriptions",
            "linux_grpc_rpc_request": "CentralBrainGateway.RequestEventSubscription",
            "linux_grpc_rpc_cancel": "CentralBrainGateway.CancelEventSubscription",
            "linux_grpc_rpc_transport_readiness": "CentralBrainGateway.GetEventSubscriptionTransportReadiness",
            "linux_grpc_rpc_decision_matrix": "CentralBrainGateway.GetEventSubscriptionDecisionMatrix",
            "linux_grpc_rpc_activation_checklist": "CentralBrainGateway.GetEventSubscriptionActivationChecklist",
            "linux_grpc_rpc_callback_watch_shape": "CentralBrainGateway.GetEventSubscriptionCallbackWatchShape",
        },
        "summary": {
            "subscription_state": "contract-only-not-brokered",
            "active_subscription_count": 0,
            "lifecycle_command_contract_active": True,
            "broker_active": False,
            "subscription_persistence_active": False,
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
            "current_binder_surface": "getEventSubscriptionTransportReadinessJson and getEventSubscriptionCallbackWatchShapeJson only",
            "callback_identity": "Binder UID/PID must map to Runtime & Governance caller identity before activation",
            "lifecycle": ["register", "onEvent", "onOverflow", "onClosed", "unregister"],
            "implemented": False,
        },
        "linux_watch_contract": {
            "candidate_cli": "event-subscription-watch planned",
            "candidate_ipc_operation": "uib.events.subscriptions.watch planned",
            "candidate_grpc_rpc": "WatchEventSubscriptions streaming RPC planned",
            "current_surface": "event-subscription-transport-readiness and event-subscription-callback-watch-shape over CLI/IPC/gRPC",
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
                "required_evidence": "Owner and persistence rules for subscription cursor/replay state assigned.",
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
                "current_evidence": "missing",
                "passed": False,
                "blocks": ["subscription_persistence_active", "cursor_storage_active"],
            },
            {
                "gate_id": "EV-ACT-004",
                "area": "backpressure-qos-profile",
                "required_evidence": "Overflow event schema, per-caller rate policy, high-rate topic QoS mapping, and Runtime & Governance responsibility split are approved.",
                "current_evidence": "missing",
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
            "current_binder_surface": "getEventSubscriptionCallbackWatchShapeJson only",
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
            "current_surface": "event-subscription-callback-watch-shape over CLI/IPC/gRPC",
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

    if required_state != "normal":
        return {
            "request_id": str(uuid.uuid4()),
            "model": model,
            "runtime": "mock-npu",
            "status": "rejected",
            "result": {
                "reason": "prototype only accepts normal safety state"
            },
            "metrics": {
                "queue_ms": 0.2,
                "inference_ms": 0.0
            }
        }

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
        }
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
