#!/usr/bin/env python3
"""Linux client for the Central Brain gRPC/RPC contract sample."""

from __future__ import annotations

import argparse
import json
import os
import socket
import sys
from typing import Any


DEFAULT_HOST = os.environ.get("CENTRAL_BRAIN_GRPC_HOST", "127.0.0.1")
DEFAULT_PORT = int(os.environ.get("CENTRAL_BRAIN_GRPC_PORT", "18788"))

COMMANDS: dict[str, tuple[str, dict[str, Any]]] = {
    "state": ("GetState", {}),
    "service-contracts": ("GetServiceContracts", {}),
    "events": ("ListEventTopics", {}),
    "event-subscriptions": ("GetEventSubscriptions", {}),
    "event-subscribe-request": (
        "RequestEventSubscription",
        {
            "trace_id": "linux-grpc-event-subscribe-request",
            "subscription_id": "linux-grpc-contract-sub",
            "topics": ["vehicle.signal.changed"],
            "filters": {"source": "linux-grpc-client", "safety_state": "normal"},
            "cursor": {"replay_limit": 5},
            "delivery": {"mode": "contract-only", "callback": "not-registered"},
            "caller": {"app_id": "linux-grpc-client", "role": "debug_console"},
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "event-subscribe-cancel": (
        "CancelEventSubscription",
        {
            "trace_id": "linux-grpc-event-subscribe-cancel",
            "subscription_id": "linux-grpc-contract-sub",
            "caller": {"app_id": "linux-grpc-client", "role": "debug_console"},
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "event-subscription-transport-readiness": ("GetEventSubscriptionTransportReadiness", {}),
    "event-subscription-decision-matrix": ("GetEventSubscriptionDecisionMatrix", {}),
    "event-subscription-activation-checklist": ("GetEventSubscriptionActivationChecklist", {}),
    "event-subscription-callback-watch-shape": ("GetEventSubscriptionCallbackWatchShape", {}),
    "event-subscription-cursor-replay-storage": ("GetEventSubscriptionCursorReplayStorage", {}),
    "event-subscription-backpressure-qos-evidence": ("GetEventSubscriptionBackpressureQosEvidence", {}),
    "event-subscription-readiness-rollup": ("GetEventSubscriptionReadinessRollup", {}),
    "event-subscription-activation-evidence": (
        "SubmitEventSubscriptionActivationEvidence",
        {
            "trace_id": "linux-grpc-event-subscription-activation-evidence",
            "evidence_submission_id": "linux-grpc-activation-evidence",
            "target_gate_ids": ["EV-ACT-001", "EV-RU-001", "DRV-GAP-004"],
            "evidence_refs": [
                {
                    "ref_id": "linux-grpc-evidence-doc",
                    "type": "doc",
                    "uri_or_path": "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md",
                    "owner": "linux-grpc-client",
                    "summary": "contract-only evidence reference sample",
                }
            ],
            "reviewer": {"app_id": "linux-grpc-client", "role": "debug_console"},
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "event-subscription-activation-evidence-status": ("GetEventSubscriptionActivationEvidenceStatus", {}),
    "event-subscription-activation-evidence-retention-checklist": (
        "GetEventSubscriptionActivationEvidenceRetentionChecklist",
        {},
    ),
    "event-subscription-activation-evidence-decision-status-rollup": (
        "GetEventSubscriptionActivationEvidenceDecisionStatusRollup",
        {},
    ),
    "extensions": ("GetUibExtensions", {}),
    "event-publish": (
        "PublishEvent",
        {
            "trace_id": "linux-grpc-event",
            "topic": "vehicle.signal.changed",
            "source": "linux-grpc-client",
            "safety_state": "normal",
            "payload": {"signal": "Vehicle.Speed", "value": 0},
        },
    ),
    "ai-sdk": ("GetAiSdkCapabilities", {}),
    "agent-plan": (
        "PlanAgentTask",
        {
            "trace_id": "linux-grpc-agent-plan",
            "utterance": "query vehicle state",
            "caller": {"app_id": "linux-grpc-client", "role": "debug_console"},
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "agent-execute": (
        "ExecuteAgentTask",
        {
            "trace_id": "linux-grpc-agent-execute",
            "task": {
                "task_id": "task-linux-grpc",
                "steps": [
                    {"step_id": "state", "type": "read_state", "semantic_entry": "GET /uib/state"},
                    {
                        "step_id": "query_vehicle_state",
                        "type": "invoke_service",
                        "service": "vehicle-state",
                        "method": "getState",
                        "semantic_entry": "POST /soa/invoke",
                    },
                ],
                "policy": {"required_permissions": ["vehicle.read"]},
            },
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "skill-invoke": (
        "InvokeSkill",
        {
            "trace_id": "linux-grpc-skill-invoke",
            "input": {"signals": ["Vehicle.Speed"]},
            "permissions": ["vehicle.read"],
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "memory-query": (
        "QueryMemory",
        {
            "trace_id": "linux-grpc-memory-query",
            "query": "cabin temperature preference",
            "scope": "driver_profile",
            "permissions": ["vehicle.read"],
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "action-request": (
        "RequestAction",
        {
            "trace_id": "linux-grpc-action",
            "action": "Cabin.SetTemperature",
            "target": {"zone": "row1-left", "temperature_c": 22.5},
            "permissions": ["vehicle.control"],
            "caller_permissions": ["vehicle.read", "vehicle.control"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "governance-precheck": (
        "PrecheckGovernance",
        {
            "trace_id": "linux-grpc-governance-precheck",
            "service": "npu-inference",
            "method": "infer",
            "caller_permissions": ["ai.infer", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
            "consume_qos": False,
        },
    ),
    "governance-backend-contract": ("GetGovernanceBackendContract", {}),
    "governance-migration-check": ("GetGovernanceMigrationCheck", {}),
    "governance-deployment-plan": ("GetGovernanceDeploymentPlan", {}),
    "governance": ("GetRuntimeGovernance", {}),
    "audit": ("GetRecentAudit", {"limit": 10}),
    "infer": (
        "InvokeService",
        {
            "service": "npu-inference",
            "method": "infer",
            "caller_permissions": ["ai.infer", "service.read"],
            "payload": {
                "model": "central-intent-v0",
                "input": {"utterance": "query vehicle state"},
                "policy": {"safety_state_required": "normal", "timeout_ms": 2000},
            },
        },
    ),
    "infer-denied": (
        "InvokeService",
        {
            "service": "npu-inference",
            "method": "infer",
            "caller_permissions": ["service.read"],
            "payload": {
                "model": "central-intent-v0",
                "input": {"utterance": "query vehicle state without ai permission"},
                "policy": {"safety_state_required": "normal", "timeout_ms": 2000},
            },
        },
    ),
    "bindings": ("ListBindings", {}),
    "binding-readiness": ("GetBindingReadiness", {}),
    "delivery-readiness": ("GetDeliveryReadiness", {}),
    "prototype-readiness": ("GetPrototypeReadiness", {}),
    "hardware-interfaces": ("GetHardwareInterfaces", {}),
    "hardware-interface-activation-checklist": ("GetHardwareInterfaceActivationChecklist", {}),
    "hardware-interface-owner-decision-status": ("GetHardwareInterfaceOwnerDecisionStatus", {}),
    "hardware-interface-owner-decision-evidence": (
        "SubmitHardwareInterfaceOwnerDecisionEvidence",
        {
            "trace_id": "linux-grpc-hardware-owner-decision-evidence",
            "evidence_submission_id": "linux-grpc-hw-owner-evidence",
            "target_interface_ids": ["npu-runtime"],
            "target_gate_ids": ["HW-ODS-001", "HW-ODS-006", "DRV-GAP-001"],
            "evidence_refs": [
                {
                    "ref_id": "linux-grpc-hw-owner-doc",
                    "type": "owner_approval",
                    "uri_or_path": "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md",
                    "owner": "linux-grpc-client",
                    "summary": "contract-only hardware owner evidence reference",
                }
            ],
            "reviewer": {"app_id": "linux-grpc-client", "role": "debug_console"},
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "hardware-interface-owner-decision-evidence-status": ("GetHardwareInterfaceOwnerDecisionEvidenceStatus", {}),
    "hardware-interface-owner-decision-evidence-retention-checklist": (
        "GetHardwareInterfaceOwnerDecisionEvidenceRetentionChecklist",
        {},
    ),
    "hardware-interface-owner-decision-evidence-replacement-trigger-checklist": (
        "GetHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklist",
        {},
    ),
    "hardware-interface-owner-decision-evidence-selected-adapter-readiness-checklist": (
        "GetHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklist",
        {},
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-blocker-rollup": (
        "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollup",
        {},
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-dry-run": (
        "DryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoad",
        {
            "trace_id": "linux-grpc-hardware-owner-evidence-adapter-load-dry-run",
            "dry_run_request_id": "linux-grpc-hw-adapter-load-dry-run",
            "selected_interface_id": "npu-runtime",
            "selected_adapter_id": "target-platform-npu-adapter",
            "adapter_version": "0.0.0-contract",
            "evidence_refs": [
                {
                    "ref_id": "linux-grpc-hw-adapter-load-approval",
                    "type": "owner_approval",
                    "uri_or_path": "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md",
                    "owner": "linux-grpc-client",
                    "summary": "contract-only adapter-load dry-run approval reference",
                }
            ],
            "requested_by": {"app_id": "linux-grpc-client", "role": "debug_console"},
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-dry-run-status": (
        "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatus",
        {},
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-dry-run-audit-consistency": (
        "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistency",
        {},
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-authority-checklist": (
        "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklist",
        {},
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-authority-status": (
        "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatus",
        {},
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-authority-audit-consistency": (
        "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistency",
        {},
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run": (
        "DryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecision",
        {
            "approval_decision_request_id": "linux-grpc-hw-approval-decision-dry-run",
            "selected_interface_id": "npu-runtime",
            "selected_adapter_id": "target-platform-npu-adapter",
            "adapter_version": "0.0.0-contract",
            "approval_decision": "approve_adapter_load",
            "approval_authority": "target-platform-approval-authority",
            "approval_signature": "contract-only-signature-placeholder",
            "evidence_refs": [
                {
                    "ref_id": "linux-grpc-hw-approval-decision-evidence",
                    "type": "approval_authority",
                    "uri_or_path": "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md",
                    "owner": "linux-grpc",
                    "summary": "contract-only approval decision dry-run evidence reference",
                }
            ],
            "requested_by": {"app_id": "linux-grpc", "role": "debug_console"},
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run-status": (
        "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunStatus",
        {},
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run-audit-consistency": (
        "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunAuditConsistency",
        {},
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-decision-closure-blocker-matrix": (
        "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionClosureBlockerMatrix",
        {},
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-decision-reviewer-matrix": (
        "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionReviewerMatrix",
        {},
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-checklist": (
        "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffChecklist",
        {},
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-status": (
        "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceStatus",
        {},
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-audit-consistency": (
        "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceAuditConsistency",
        {},
    ),
    "vehicle-signals": ("GetVehicleSignals", {}),
    "vehicle-signal-activation": ("GetVehicleSignalActivation", {}),
    "vehicle-signal-validation": ("GetVehicleSignalValidation", {}),
}


def call(host: str, port: int, rpc: str, payload: dict[str, Any], trace_id: str) -> dict[str, Any]:
    request_payload = {
        "trace_id": trace_id,
        "rpc": rpc,
        "caller": "linux-grpc-client",
        "permission_context_json": "{}",
        "payload_json": json.dumps(payload, ensure_ascii=False),
    }
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as client:
        client.settimeout(5)
        client.connect((host, port))
        client.sendall(json.dumps(request_payload, ensure_ascii=False).encode("utf-8"))
        client.shutdown(socket.SHUT_WR)
        chunks = []
        while True:
            chunk = client.recv(65536)
            if not chunk:
                break
            chunks.append(chunk)
    return json.loads(b"".join(chunks).decode("utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser(description="Call the Central Brain Linux gRPC/RPC contract sample.")
    parser.add_argument("command", choices=sorted(COMMANDS))
    parser.add_argument("--host", default=DEFAULT_HOST)
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--trace-id", default="linux-grpc-cli")
    args = parser.parse_args()

    rpc, payload = COMMANDS[args.command]
    result = call(args.host, args.port, rpc, payload, args.trace_id)
    json.dump(result, sys.stdout, ensure_ascii=False, indent=2)
    print()
    return 0 if result.get("status") == "ok" else 1


if __name__ == "__main__":
    raise SystemExit(main())
