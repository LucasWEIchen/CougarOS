#!/usr/bin/env python3
"""Linux client sample for the Central Brain Unix socket IPC binding."""

from __future__ import annotations

import argparse
import json
import os
import socket
import sys
from typing import Any


DEFAULT_SOCKET_PATH = os.environ.get("CENTRAL_BRAIN_IPC_SOCKET", "/tmp/central_brain_gateway.sock")

COMMANDS: dict[str, tuple[str, dict[str, Any]]] = {
    "context": ("uib.context.get", {}),
    "state": ("uib.state.get", {}),
    "events": ("uib.events.topics", {}),
    "event-recent": ("uib.events.recent", {}),
    "extensions": ("uib.extensions.get", {}),
    "services": ("soa.services.list", {}),
    "service-contracts": ("soa.contracts.get", {}),
    "governance": ("governance.runtime.get", {}),
    "governance-backend-contract": ("governance.backend.contract.get", {}),
    "governance-migration-check": ("governance.migration.check", {}),
    "governance-deployment-plan": ("governance.deployment.plan.get", {}),
    "audit": ("audit.recent.get", {}),
    "bindings": ("bindings.list", {}),
    "binding-readiness": ("bindings.readiness.get", {}),
    "delivery-readiness": ("delivery.readiness.get", {}),
    "prototype-readiness": ("prototype.readiness.get", {}),
    "hardware-interfaces": ("hardware.interfaces.get", {}),
    "ai-sdk": ("ai.sdk.capabilities", {}),
    "skills": ("skills.list", {}),
    "agent-plan": (
        "agent.plan",
        {
            "trace_id": "linux-ipc-agent-plan",
            "utterance": "query vehicle state",
            "caller": {"app_id": "linux-ipc-client", "role": "debug_console"},
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "agent-execute": (
        "agent.execute",
        {
            "trace_id": "linux-ipc-agent-execute",
            "task": {
                "task_id": "task-linux-ipc",
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
        "skills.invoke",
        {
            "trace_id": "linux-ipc-skill-invoke",
            "input": {"signals": ["Vehicle.Speed"]},
            "permissions": ["vehicle.read"],
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "memory-query": (
        "memory.query",
        {
            "trace_id": "linux-ipc-memory-query",
            "query": "cabin temperature preference",
            "scope": "driver_profile",
            "permissions": ["vehicle.read"],
            "caller_permissions": ["vehicle.read", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "action-request": (
        "uib.actions.request",
        {
            "trace_id": "linux-ipc-action",
            "action": "Cabin.SetTemperature",
            "target": {"zone": "row1-left", "temperature_c": 22.5},
            "permissions": ["vehicle.control"],
            "caller_permissions": ["vehicle.read", "vehicle.control"],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    ),
    "policy": (
        "policy.evaluate",
        {
            "trace_id": "linux-ipc-policy",
            "permissions": ["vehicle.control"],
            "caller_permissions": ["vehicle.read"],
            "vehicle_state": "driving",
            "safety_state": "normal",
        },
    ),
    "governance-precheck": (
        "governance.precheck",
        {
            "trace_id": "linux-ipc-governance-precheck",
            "service": "npu-inference",
            "method": "infer",
            "caller_permissions": ["ai.infer", "service.read"],
            "vehicle_state": "parked",
            "safety_state": "normal",
            "consume_qos": False,
        },
    ),
    "event-publish": (
        "uib.events.publish",
        {
            "trace_id": "linux-ipc-event",
            "topic": "vehicle.signal.changed",
            "source": "linux-ipc-client",
            "safety_state": "normal",
            "payload": {"signal": "Vehicle.Speed", "value": 0},
        },
    ),
    "infer": (
        "soa.service.invoke",
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
        "soa.service.invoke",
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
}


def call(socket_path: str, operation: str, payload: dict[str, Any], trace_id: str) -> dict[str, Any]:
    envelope = {
        "trace_id": trace_id,
        "operation": operation,
        "payload": payload,
        "req_ids": ["XSC-006", "NV-P-002", "DEL-002"],
    }
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as client:
        client.settimeout(5)
        client.connect(socket_path)
        client.sendall(json.dumps(envelope).encode("utf-8"))
        client.shutdown(socket.SHUT_WR)
        chunks = []
        while True:
            chunk = client.recv(65536)
            if not chunk:
                break
            chunks.append(chunk)
    return json.loads(b"".join(chunks).decode("utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser(description="Call the Central Brain Linux IPC binding sample.")
    parser.add_argument("command", choices=sorted(COMMANDS))
    parser.add_argument("--socket-path", default=DEFAULT_SOCKET_PATH)
    parser.add_argument("--trace-id", default="linux-ipc-cli")
    args = parser.parse_args()

    operation, payload = COMMANDS[args.command]
    result = call(args.socket_path, operation, payload, args.trace_id)
    json.dump(result, sys.stdout, ensure_ascii=False, indent=2)
    print()
    return 0 if result.get("status") == "ok" else 1


if __name__ == "__main__":
    raise SystemExit(main())
