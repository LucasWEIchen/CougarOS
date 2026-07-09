#!/usr/bin/env python3
"""Linux CLI sample for the Central Brain semantic gateway."""

from __future__ import annotations

import argparse
import json
import os
import sys
from typing import Any
from urllib import request


DEFAULT_BASE_URL = os.environ.get("CENTRAL_BRAIN_BASE_URL", "http://127.0.0.1:8787")


COMMANDS: dict[str, tuple[str, str, dict[str, Any] | None]] = {
    "context": ("GET", "/uib/context", None),
    "state": ("GET", "/uib/state", None),
    "services": ("GET", "/soa/services", None),
    "service-contracts": ("GET", "/soa/contracts", None),
    "events": ("GET", "/uib/events/topics", None),
    "event-recent": ("GET", "/uib/events/recent", None),
    "event-subscriptions": ("GET", "/uib/events/subscriptions", None),
    "event-subscribe-request": (
        "POST",
        "/uib/events/subscriptions/request",
        {
            "trace_id": "linux-cli-event-subscribe-request",
            "subscription_id": "linux-cli-contract-sub",
            "topics": ["vehicle.signal.changed"],
            "filters": {"source": "linux-cli", "safety_state": "normal"},
            "cursor": {"replay_limit": 5},
            "delivery": {"mode": "contract-only", "callback": "not-registered"},
            "caller": {"app_id": "linux-cli", "role": "debug_console"},
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "event-subscribe-cancel": (
        "POST",
        "/uib/events/subscriptions/cancel",
        {
            "trace_id": "linux-cli-event-subscribe-cancel",
            "subscription_id": "linux-cli-contract-sub",
            "caller": {"app_id": "linux-cli", "role": "debug_console"},
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "event-subscription-transport-readiness": ("GET", "/uib/events/subscriptions/transport-readiness", None),
    "event-subscription-decision-matrix": ("GET", "/uib/events/subscriptions/decision-matrix", None),
    "event-subscription-activation-checklist": ("GET", "/uib/events/subscriptions/activation-checklist", None),
    "event-subscription-callback-watch-shape": ("GET", "/uib/events/subscriptions/callback-watch-shape", None),
    "event-subscription-cursor-replay-storage": ("GET", "/uib/events/subscriptions/cursor-replay-storage", None),
    "event-subscription-backpressure-qos-evidence": ("GET", "/uib/events/subscriptions/backpressure-qos-evidence", None),
    "event-subscription-readiness-rollup": ("GET", "/uib/events/subscriptions/readiness-rollup", None),
    "event-subscription-activation-evidence": (
        "POST",
        "/uib/events/subscriptions/activation-evidence",
        {
            "trace_id": "linux-cli-event-subscription-activation-evidence",
            "evidence_submission_id": "linux-cli-activation-evidence",
            "target_gate_ids": ["EV-ACT-001", "EV-RU-001", "DRV-GAP-004"],
            "evidence_refs": [
                {
                    "ref_id": "linux-cli-evidence-doc",
                    "type": "doc",
                    "uri_or_path": "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md",
                    "owner": "linux-cli",
                    "summary": "contract-only evidence reference sample",
                }
            ],
            "reviewer": {"app_id": "linux-cli", "role": "debug_console"},
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "event-subscription-activation-evidence-status": ("GET", "/uib/events/subscriptions/activation-evidence/status", None),
    "event-subscription-activation-evidence-retention-checklist": (
        "GET",
        "/uib/events/subscriptions/activation-evidence/retention-checklist",
        None,
    ),
    "event-subscription-activation-evidence-decision-status-rollup": (
        "GET",
        "/uib/events/subscriptions/activation-evidence/decision-status-rollup",
        None,
    ),
    "event-subscription-activation-approval-dry-run-status": (
        "GET",
        "/uib/events/subscriptions/activation-evidence/approval-dry-run/status",
        None,
    ),
    "event-subscription-activation-approval-authority-checklist": (
        "GET",
        "/uib/events/subscriptions/activation-evidence/approval-authority-checklist",
        None,
    ),
    "event-subscription-activation-approval-authority-audit-consistency": (
        "GET",
        "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/audit-consistency",
        None,
    ),
    "event-subscription-activation-approval-decision-blocker-rollup": (
        "GET",
        "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-blocker-rollup",
        None,
    ),
    "event-subscription-activation-approval-decision-dry-run": (
        "POST",
        "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run",
        {
            "trace_id": "linux-cli-event-subscription-activation-approval-decision-dry-run",
            "approval_request_id": "linux-cli-approval-decision-dry-run",
            "source_decision_blocker_rollup_ref": "GET /uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-blocker-rollup",
            "target_gate_ids": ["EV-ADB-001", "EV-ADB-002", "EV-ADB-006", "DRV-GAP-004"],
            "approval_decision": "approve_activation",
            "approval_authority": {
                "authority_id": "linux-cli-approver",
                "role": "debug_console",
                "signature_ref": "contract-only-signature",
            },
            "reviewer": {"app_id": "linux-cli", "role": "debug_console"},
            "evidence_refs": [
                {
                    "ref_id": "linux-cli-blocker-rollup",
                    "type": "api",
                    "uri_or_path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-blocker-rollup",
                    "owner": "linux-cli",
                    "summary": "contract-only blocker rollup reference",
                }
            ],
            "rollback_plan_ref": "contract-only-rollback-plan",
            "runtime_governance_policy_ref": "runtime-governance-policy:event-subscription-approval",
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "event-subscription-activation-approval-decision-dry-run-status": (
        "GET",
        "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/status",
        None,
    ),
    "extensions": ("GET", "/uib/extensions", None),
    "governance": ("GET", "/governance/runtime", None),
    "governance-backend-contract": ("GET", "/governance/backend-contract", None),
    "governance-migration-check": ("GET", "/governance/migration-check", None),
    "governance-deployment-plan": ("GET", "/governance/deployment-plan", None),
    "audit": ("GET", "/audit/recent", None),
    "bindings": ("GET", "/bindings", None),
    "binding-detail": ("GET", "/bindings/detail", None),
    "binding-readiness": ("GET", "/bindings/readiness", None),
    "delivery-readiness": ("GET", "/delivery/readiness", None),
    "prototype-readiness": ("GET", "/prototype/readiness", None),
    "native-adapters": ("GET", "/native/adapters", None),
    "native-adapters-detail": ("GET", "/native/adapters/detail", None),
    "driver-gaps": ("GET", "/native/driver-gaps", None),
    "hardware-interfaces": ("GET", "/hardware/interfaces", None),
    "hardware-interface-activation-checklist": ("GET", "/hardware/interfaces/activation-checklist", None),
    "hardware-interface-owner-decision-status": ("GET", "/hardware/interfaces/owner-decision-status", None),
    "hardware-interface-owner-decision-evidence": (
        "POST",
        "/hardware/interfaces/owner-decision-evidence",
        {
            "trace_id": "linux-cli-hardware-owner-decision-evidence",
            "evidence_submission_id": "linux-cli-hw-owner-evidence",
            "target_interface_ids": ["npu-runtime"],
            "target_gate_ids": ["HW-ODS-001", "HW-ODS-006", "DRV-GAP-001"],
            "evidence_refs": [
                {
                    "ref_id": "linux-cli-hw-owner-doc",
                    "type": "owner_approval",
                    "uri_or_path": "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md",
                    "owner": "linux-cli",
                    "summary": "contract-only hardware owner evidence reference",
                }
            ],
            "reviewer": {"app_id": "linux-cli", "role": "debug_console"},
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "hardware-interface-owner-decision-evidence-status": ("GET", "/hardware/interfaces/owner-decision-evidence/status", None),
    "hardware-interface-owner-decision-evidence-retention-checklist": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/retention-checklist",
        None,
    ),
    "hardware-interface-owner-decision-evidence-replacement-trigger-checklist": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist",
        None,
    ),
    "hardware-interface-owner-decision-evidence-selected-adapter-readiness-checklist": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist",
        None,
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-blocker-rollup": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup",
        None,
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-dry-run": (
        "POST",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run",
        {
            "trace_id": "linux-cli-hardware-owner-evidence-adapter-load-dry-run",
            "dry_run_request_id": "linux-cli-hw-adapter-load-dry-run",
            "selected_interface_id": "npu-runtime",
            "selected_adapter_id": "target-platform-npu-adapter",
            "adapter_version": "0.0.0-contract",
            "evidence_refs": [
                {
                    "ref_id": "linux-cli-hw-adapter-load-approval",
                    "type": "owner_approval",
                    "uri_or_path": "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md",
                    "owner": "linux-cli",
                    "summary": "contract-only adapter-load dry-run approval reference",
                }
            ],
            "requested_by": {"app_id": "linux-cli", "role": "debug_console"},
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-dry-run-status": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status",
        None,
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-dry-run-audit-consistency": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/audit-consistency",
        None,
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-authority-checklist": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist",
        None,
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-authority-status": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/status",
        None,
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-authority-audit-consistency": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/audit-consistency",
        None,
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run": (
        "POST",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run",
        {
            "approval_decision_request_id": "linux-cli-hw-approval-decision-dry-run",
            "selected_interface_id": "npu-runtime",
            "selected_adapter_id": "target-platform-npu-adapter",
            "adapter_version": "0.0.0-contract",
            "approval_decision": "approve_adapter_load",
            "approval_authority": "target-platform-approval-authority",
            "approval_signature": "contract-only-signature-placeholder",
            "evidence_refs": [
                {
                    "ref_id": "linux-cli-hw-approval-decision-evidence",
                    "type": "approval_authority",
                    "uri_or_path": "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md",
                    "owner": "linux-cli",
                    "summary": "contract-only approval decision dry-run evidence reference",
                }
            ],
            "requested_by": {"app_id": "linux-cli", "role": "debug_console"},
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run-status": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/status",
        None,
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run-audit-consistency": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/audit-consistency",
        None,
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-decision-closure-blocker-matrix": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix",
        None,
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-decision-reviewer-matrix": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix",
        None,
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-checklist": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist",
        None,
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-status": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status",
        None,
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-audit-consistency": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/audit-consistency",
        None,
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-decision-rollup": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup",
        None,
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-checklist": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist",
        None,
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-audit-consistency": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency",
        None,
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-rollup": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup",
        None,
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-checklist": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist",
        None,
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-consistency": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency",
        None,
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup",
        None,
    ),
    "hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-summary": (
        "GET",
        "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary",
        None,
    ),
    "vehicle-signals": ("GET", "/vehicle/signals", None),
    "vehicle-signal-activation": ("GET", "/vehicle/signals/activation", None),
    "vehicle-signal-validation": ("GET", "/vehicle/signals/validation", None),
    "ai-sdk": ("GET", "/ai/sdk/capabilities", None),
    "skills": ("GET", "/skills", None),
    "agent-plan": (
        "POST",
        "/agent/plan",
        {
            "trace_id": "linux-cli-agent-plan",
            "utterance": "query vehicle state",
            "caller": {"app_id": "linux-cli", "role": "debug_console"},
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "agent-execute": (
        "POST",
        "/agent/execute",
        {
            "trace_id": "linux-cli-agent-execute",
            "task": {
                "task_id": "task-linux-cli",
                "steps": [
                    {
                        "step_id": "state",
                        "type": "read_state",
                        "semantic_entry": "GET /uib/state",
                    },
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
        "POST",
        "/skills/vehicle.state.query/invoke",
        {
            "trace_id": "linux-cli-skill-invoke",
            "input": {"signals": ["Vehicle.Speed"]},
            "permissions": ["vehicle.read"],
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "memory-query": (
        "POST",
        "/memory/query",
        {
            "trace_id": "linux-cli-memory-query",
            "query": "cabin temperature preference",
            "scope": "driver_profile",
            "permissions": ["vehicle.read"],
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "action-request": (
        "POST",
        "/uib/actions/request",
        {
            "trace_id": "linux-cli-action",
            "action": "Cabin.SetTemperature",
            "target": {"zone": "row1-left", "temperature_c": 22.5},
            "permissions": ["vehicle.control"],
            "caller_permissions": ["vehicle.read", "vehicle.control"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "policy": (
        "POST",
        "/policy/evaluate",
        {
            "trace_id": "linux-cli-policy",
            "permissions": ["vehicle.control"],
            "caller_permissions": ["vehicle.read"],
            "vehicle_state": "driving",
            "safety_state": "normal",
        },
    ),
    "governance-precheck": (
        "POST",
        "/governance/precheck",
        {
            "trace_id": "linux-cli-governance-precheck",
            "service": "npu-inference",
            "method": "infer",
            "caller_permissions": ["ai.infer", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
            "consume_qos": False,
        },
    ),
    "event-publish": (
        "POST",
        "/uib/events/publish",
        {
            "trace_id": "linux-cli-event",
            "topic": "vehicle.signal.changed",
            "source": "linux-cli",
            "safety_state": "normal",
            "payload": {"signal": "Vehicle.Speed", "value": 0},
        },
    ),
    "vehicle-state": (
        "POST",
        "/soa/invoke",
        {
            "service": "vehicle-state",
            "method": "getState",
            "caller_permissions": ["vehicle.read", "service.read"],
        },
    ),
    "infer": (
        "POST",
        "/soa/invoke",
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
}


def call(base_url: str, method: str, path: str, body: dict[str, Any] | None) -> dict[str, Any]:
    encoded = None
    headers = {"Accept": "application/json"}
    if body is not None:
        encoded = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"

    req = request.Request(base_url.rstrip("/") + path, data=encoded, headers=headers, method=method)
    with request.urlopen(req, timeout=5) as response:
        return json.loads(response.read().decode("utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser(description="Call the Central Brain Linux semantic gateway sample.")
    parser.add_argument("command", choices=sorted(COMMANDS))
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    args = parser.parse_args()

    method, path, body = COMMANDS[args.command]
    payload = call(args.base_url, method, path, body)
    json.dump(payload, sys.stdout, ensure_ascii=False, indent=2)
    print()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
