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
API_VERSION = "0.1.51"
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
                    "cursor_replay_storage_endpoint": "GET /uib/events/subscriptions/cursor-replay-storage",
                    "backpressure_qos_evidence_endpoint": "GET /uib/events/subscriptions/backpressure-qos-evidence",
                    "readiness_rollup_endpoint": "GET /uib/events/subscriptions/readiness-rollup",
                    "activation_evidence_endpoint": "POST /uib/events/subscriptions/activation-evidence",
                    "activation_evidence_status_endpoint": "GET /uib/events/subscriptions/activation-evidence/status",
                    "activation_evidence_retention_checklist_endpoint": "GET /uib/events/subscriptions/activation-evidence/retention-checklist",
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
            },
        },
        "transport_candidates": [
            {
                "binding": "android-binder-aidl",
                "operation": "getEventSubscriptionsJson/requestEventSubscriptionJson/cancelEventSubscriptionJson/getEventSubscriptionTransportReadinessJson/getEventSubscriptionDecisionMatrixJson/getEventSubscriptionActivationChecklistJson/getEventSubscriptionCallbackWatchShapeJson/getEventSubscriptionCursorReplayStorageJson/getEventSubscriptionBackpressureQosEvidenceJson/getEventSubscriptionReadinessRollupJson/submitEventSubscriptionActivationEvidenceJson/getEventSubscriptionActivationEvidenceStatusJson/getEventSubscriptionActivationEvidenceRetentionChecklistJson",
                "current_state": "contract-only lifecycle commands; callback registration not implemented",
            },
            {
                "binding": "linux-ipc",
                "operation": "uib.events.subscriptions.get/request/cancel/transport.readiness/decision.matrix/activation.checklist/callback.watch.shape/cursor.replay.storage/backpressure.qos.evidence/readiness.rollup/activation.evidence/activation.evidence.status/activation.evidence.retention.checklist",
                "current_state": "contract-only lifecycle commands; watch operation not implemented",
            },
            {
                "binding": "linux-grpc-rpc",
                "operation": "CentralBrainGateway.GetEventSubscriptions/RequestEventSubscription/CancelEventSubscription/GetEventSubscriptionTransportReadiness/GetEventSubscriptionDecisionMatrix/GetEventSubscriptionActivationChecklist/GetEventSubscriptionCallbackWatchShape/GetEventSubscriptionCursorReplayStorage/GetEventSubscriptionBackpressureQosEvidence/GetEventSubscriptionReadinessRollup/SubmitEventSubscriptionActivationEvidence/GetEventSubscriptionActivationEvidenceStatus/GetEventSubscriptionActivationEvidenceRetentionChecklist",
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
        elif path == "/hardware/interfaces/activation-checklist":
            self.send_json(200, envelope(hardware_interface_activation_checklist_payload()))
        elif path == "/hardware/interfaces/owner-decision-status":
            self.send_json(200, envelope(hardware_interface_owner_decision_status_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/status":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_status_payload()))
        elif path == "/hardware/interfaces/owner-decision-evidence/retention-checklist":
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_retention_checklist_payload()))
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
        elif path == "/hardware/interfaces/owner-decision-evidence":
            trace_id = request.get("trace_id") or str(uuid.uuid4())
            request["trace_id"] = trace_id
            self.send_json(200, envelope(hardware_interface_owner_decision_evidence_payload(request), trace_id))
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
