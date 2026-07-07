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
