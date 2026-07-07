#!/usr/bin/env python3
"""Linux Unix socket sample for the Central Brain IPC binding.

Req IDs: XSC-005, XSC-006, NV-P-002, DEL-002.
This daemon is a transport sample: it maps local IPC envelopes onto the
architecture-aligned Uni Info Bus and SOA semantic gateway.
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import signal
import socket
import sys
from typing import Any
from urllib import request
from urllib.error import HTTPError, URLError

BACKEND_DIR = Path(__file__).resolve().parents[3] / "backend"
sys.path.insert(0, str(BACKEND_DIR))

from central_brain_governance_client import get_audit_via_socket
from central_brain_governance_client import get_runtime_via_socket
from central_brain_governance_client import precheck_service_via_socket
from runtime_governance import RuntimeGovernance


DEFAULT_BASE_URL = os.environ.get("CENTRAL_BRAIN_BASE_URL", "http://127.0.0.1:8787")
DEFAULT_SOCKET_PATH = os.environ.get("CENTRAL_BRAIN_IPC_SOCKET", "/tmp/central_brain_gateway.sock")
DEFAULT_GOVERNANCE_SOCKET = os.environ.get("CENTRAL_BRAIN_GOVERNANCE_SOCKET")
DEFAULT_IPC_AUDIT_LOG = os.environ.get("CENTRAL_BRAIN_IPC_AUDIT_LOG")
IPC_GOVERNANCE = RuntimeGovernance(DEFAULT_IPC_AUDIT_LOG)

OPERATION_MAP: dict[str, dict[str, Any]] = {
    "uib.context.get": {
        "method": "GET",
        "path": "/uib/context",
        "req_ids": ["XSC-002", "XSC-006", "FW-U-001", "NV-P-002", "DEL-002"],
    },
    "uib.state.get": {
        "method": "GET",
        "path": "/uib/state",
        "req_ids": ["XSC-002", "XSC-006", "FW-U-002", "NV-P-002", "DEL-002"],
    },
    "uib.events.topics": {
        "method": "GET",
        "path": "/uib/events/topics",
        "req_ids": ["XSC-002", "XSC-006", "FW-U-003", "NV-P-002", "NV-P-006", "DEL-002"],
    },
    "uib.events.publish": {
        "method": "POST",
        "path": "/uib/events/publish",
        "req_ids": ["XSC-002", "XSC-006", "FW-U-003", "NV-P-002", "NV-P-006", "DEL-002"],
    },
    "uib.events.recent": {
        "method": "GET",
        "path": "/uib/events/recent",
        "req_ids": ["XSC-002", "XSC-006", "FW-U-003", "NV-P-002", "NV-P-006", "DEL-002"],
    },
    "uib.events.subscriptions.get": {
        "method": "GET",
        "path": "/uib/events/subscriptions",
        "req_ids": ["XSC-002", "XSC-006", "FW-U-003", "NV-P-002", "NV-P-006", "DEL-002"],
    },
    "uib.events.subscriptions.request": {
        "method": "POST",
        "path": "/uib/events/subscriptions/request",
        "req_ids": ["XSC-002", "XSC-005", "XSC-006", "FW-U-003", "NV-P-002", "NV-P-006", "DEL-002"],
    },
    "uib.events.subscriptions.cancel": {
        "method": "POST",
        "path": "/uib/events/subscriptions/cancel",
        "req_ids": ["XSC-002", "XSC-005", "XSC-006", "FW-U-003", "NV-P-002", "NV-P-006", "DEL-002"],
    },
    "uib.events.subscriptions.transport.readiness": {
        "method": "GET",
        "path": "/uib/events/subscriptions/transport-readiness",
        "req_ids": ["XSC-002", "XSC-005", "XSC-006", "FW-U-003", "NV-P-002", "NV-P-006", "DEL-002", "DEL-004"],
    },
    "uib.events.subscriptions.decision.matrix": {
        "method": "GET",
        "path": "/uib/events/subscriptions/decision-matrix",
        "req_ids": ["XSC-002", "XSC-005", "XSC-006", "FW-U-003", "NV-P-002", "NV-P-006", "DEL-002", "DEL-004"],
    },
    "uib.events.subscriptions.activation.checklist": {
        "method": "GET",
        "path": "/uib/events/subscriptions/activation-checklist",
        "req_ids": ["XSC-002", "XSC-005", "XSC-006", "FW-U-003", "NV-P-002", "NV-P-006", "DEL-002", "DEL-004"],
    },
    "uib.events.subscriptions.callback.watch.shape": {
        "method": "GET",
        "path": "/uib/events/subscriptions/callback-watch-shape",
        "req_ids": ["XSC-002", "XSC-005", "XSC-006", "FW-U-003", "NV-P-002", "NV-P-006", "DEL-002", "DEL-004"],
    },
    "uib.events.subscriptions.cursor.replay.storage": {
        "method": "GET",
        "path": "/uib/events/subscriptions/cursor-replay-storage",
        "req_ids": ["XSC-002", "XSC-005", "XSC-006", "FW-U-003", "NV-P-002", "NV-P-006", "DEL-002", "DEL-004"],
    },
    "uib.events.subscriptions.backpressure.qos.evidence": {
        "method": "GET",
        "path": "/uib/events/subscriptions/backpressure-qos-evidence",
        "req_ids": ["XSC-002", "XSC-005", "XSC-006", "FW-U-003", "NV-P-002", "NV-P-006", "DEL-002", "DEL-004"],
    },
    "uib.events.subscriptions.readiness.rollup": {
        "method": "GET",
        "path": "/uib/events/subscriptions/readiness-rollup",
        "req_ids": ["XSC-002", "XSC-005", "XSC-006", "FW-U-003", "NV-P-002", "NV-P-006", "DEL-002", "DEL-004"],
    },
    "uib.events.subscriptions.activation.evidence": {
        "method": "POST",
        "path": "/uib/events/subscriptions/activation-evidence",
        "req_ids": ["XSC-002", "XSC-005", "XSC-006", "FW-U-003", "NV-P-002", "NV-P-006", "DEL-002", "DEL-004"],
    },
    "uib.events.subscriptions.activation.evidence.status": {
        "method": "GET",
        "path": "/uib/events/subscriptions/activation-evidence/status",
        "req_ids": ["XSC-002", "XSC-005", "XSC-006", "FW-U-003", "NV-P-002", "NV-P-006", "DEL-002", "DEL-004"],
    },
    "uib.events.subscriptions.activation.evidence.retention.checklist": {
        "method": "GET",
        "path": "/uib/events/subscriptions/activation-evidence/retention-checklist",
        "req_ids": ["XSC-002", "XSC-005", "XSC-006", "FW-U-003", "NV-P-002", "NV-P-006", "DEL-002", "DEL-004"],
    },
    "uib.extensions.get": {
        "method": "GET",
        "path": "/uib/extensions",
        "req_ids": ["XSC-002", "XSC-005", "XSC-006", "FW-U-008", "NV-P-002", "DEL-002"],
    },
    "uib.actions.request": {
        "method": "POST",
        "path": "/uib/actions/request",
        "req_ids": ["XSC-002", "XSC-005", "XSC-006", "FW-U-004", "FW-U-007", "NV-G-005", "NV-P-002", "DEL-002"],
    },
    "ai.sdk.capabilities": {
        "method": "GET",
        "path": "/ai/sdk/capabilities",
        "req_ids": ["XSC-001", "XSC-006", "APP-004", "NV-P-002", "DEL-002"],
    },
    "agent.plan": {
        "method": "POST",
        "path": "/agent/plan",
        "req_ids": ["XSC-001", "XSC-006", "APP-004", "NV-F-001", "NV-P-002", "DEL-002"],
    },
    "agent.execute": {
        "method": "POST",
        "path": "/agent/execute",
        "req_ids": ["XSC-001", "XSC-006", "APP-004", "NV-F-001", "FW-U-006", "NV-P-002", "DEL-002"],
    },
    "skills.list": {
        "method": "GET",
        "path": "/skills",
        "req_ids": ["XSC-001", "XSC-006", "FW-U-006", "NV-P-002", "DEL-002"],
    },
    "skills.invoke": {
        "method": "POST",
        "path": "/skills/vehicle.state.query/invoke",
        "req_ids": ["XSC-001", "XSC-006", "FW-U-006", "NV-G-005", "NV-P-002", "DEL-002"],
    },
    "memory.query": {
        "method": "POST",
        "path": "/memory/query",
        "req_ids": ["XSC-001", "XSC-006", "NV-F-001", "FW-U-006", "NV-P-002", "DEL-002"],
    },
    "soa.services.list": {
        "method": "GET",
        "path": "/soa/services",
        "req_ids": ["XSC-003", "XSC-006", "FW-S-004", "NV-P-002", "DEL-002"],
    },
    "soa.contracts.get": {
        "method": "GET",
        "path": "/soa/contracts",
        "req_ids": ["XSC-003", "XSC-006", "FW-S-004", "NV-G-003", "NV-P-002", "DEL-002"],
    },
    "soa.service.invoke": {
        "method": "POST",
        "path": "/soa/invoke",
        "req_ids": ["XSC-003", "XSC-006", "FW-S-005", "NV-G-004", "NV-P-002", "DEL-002"],
    },
    "policy.evaluate": {
        "method": "POST",
        "path": "/policy/evaluate",
        "req_ids": ["XSC-005", "XSC-006", "NV-G-005", "NV-P-002", "DEL-002"],
    },
    "governance.precheck": {
        "method": "POST",
        "path": "/governance/precheck",
        "req_ids": ["XSC-005", "XSC-006", "NV-G-002", "NV-G-004", "NV-G-005", "NV-G-006", "NV-G-007", "NV-P-002", "DEL-002"],
    },
    "governance.backend.contract.get": {
        "method": "GET",
        "path": "/governance/backend-contract",
        "req_ids": ["XSC-005", "XSC-006", "NV-G-001", "NV-G-002", "NV-G-004", "NV-G-007", "NV-P-002", "DEL-002"],
    },
    "governance.migration.check": {
        "method": "GET",
        "path": "/governance/migration-check",
        "req_ids": ["XSC-005", "XSC-006", "NV-G-002", "NV-G-004", "NV-G-005", "NV-G-007", "NV-P-002", "DEL-002", "DEL-003", "DEL-004"],
    },
    "governance.deployment.plan.get": {
        "method": "GET",
        "path": "/governance/deployment-plan",
        "req_ids": ["XSC-005", "XSC-006", "NV-G-001", "NV-G-002", "NV-G-004", "NV-G-007", "NV-P-002", "DEL-001", "DEL-002", "DEL-003", "DEL-004"],
    },
    "governance.runtime.get": {
        "method": "GET",
        "path": "/governance/runtime",
        "req_ids": ["XSC-005", "XSC-006", "NV-G-001", "NV-P-002", "DEL-002"],
    },
    "audit.recent.get": {
        "method": "GET",
        "path": "/audit/recent",
        "req_ids": ["XSC-005", "XSC-006", "NV-G-007", "NV-P-002", "DEL-002"],
    },
    "bindings.list": {
        "method": "GET",
        "path": "/bindings",
        "req_ids": ["XSC-006", "NV-P-001", "NV-P-002", "NV-P-003", "NV-P-004", "NV-P-005", "NV-P-006", "DEL-002"],
    },
    "bindings.readiness.get": {
        "method": "GET",
        "path": "/bindings/readiness",
        "req_ids": ["XSC-006", "NV-P-001", "NV-P-002", "NV-P-003", "NV-P-004", "NV-P-005", "NV-P-006", "DEL-002", "DEL-003", "DEL-004"],
    },
    "delivery.readiness.get": {
        "method": "GET",
        "path": "/delivery/readiness",
        "req_ids": ["DEL-001", "DEL-002", "DEL-003", "DEL-004", "DEL-005", "XSC-001", "XSC-002", "XSC-003", "XSC-004", "XSC-005", "XSC-006", "NV-P-002"],
    },
    "prototype.readiness.get": {
        "method": "GET",
        "path": "/prototype/readiness",
        "req_ids": ["XSC-001", "XSC-002", "XSC-003", "XSC-004", "XSC-005", "XSC-006", "NV-P-002", "DEL-001", "DEL-002", "DEL-003", "DEL-004", "DEL-005"],
    },
    "hardware.interfaces.get": {
        "method": "GET",
        "path": "/hardware/interfaces",
        "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-001", "KH-002", "KH-003", "KH-006", "KH-007", "NV-P-002", "DEL-002", "DEL-005"],
    },
    "hardware.interfaces.activation.checklist": {
        "method": "GET",
        "path": "/hardware/interfaces/activation-checklist",
        "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-002", "DEL-002", "DEL-005"],
    },
    "vehicle.signals.list": {
        "method": "GET",
        "path": "/vehicle/signals",
        "req_ids": ["XSC-002", "XSC-004", "XSC-006", "NV-F-004", "NV-F-005", "NV-P-002", "DEL-002", "DEL-005"],
    },
    "vehicle.signals.activation.get": {
        "method": "GET",
        "path": "/vehicle/signals/activation",
        "req_ids": ["XSC-002", "XSC-004", "XSC-006", "NV-F-003", "NV-F-004", "NV-F-005", "NV-P-001", "NV-P-002", "DEL-002", "DEL-005"],
    },
    "vehicle.signals.validation.get": {
        "method": "GET",
        "path": "/vehicle/signals/validation",
        "req_ids": ["XSC-002", "XSC-004", "XSC-006", "NV-F-003", "NV-F-004", "NV-F-005", "NV-P-001", "NV-P-002", "KH-003", "KH-006", "KH-007", "DEL-002", "DEL-005"],
    },
}


def response(trace_id: str, status: str, payload: dict[str, Any], error: dict[str, Any] | None = None) -> dict[str, Any]:
    return {
        "trace_id": trace_id,
        "status": status,
        "error": error,
        "payload": payload,
        "req_ids": ["XSC-006", "NV-P-002", "DEL-002"],
    }


def local_governance_precheck(trace_id: str, payload: dict[str, Any], reason: str | None = None) -> tuple[dict[str, Any], bool]:
    precheck = IPC_GOVERNANCE.precheck(payload)
    policy = precheck["policy"]
    qos_decision = precheck["qos_decision"]
    allowed = policy["decision"] == "allow" and qos_decision["decision"] == "allow"
    service = payload.get("service", "vehicle-state")
    method = payload.get("method", "invoke")
    outcome = "ipc_prechecked_allowed"
    if policy["decision"] != "allow":
        outcome = "ipc_policy_or_lifecycle_rejected"
    elif qos_decision["decision"] != "allow":
        outcome = "ipc_qos_rejected"

    IPC_GOVERNANCE.record_audit(
        trace_id,
        {
            "service": service,
            "method": method,
            "outcome": outcome,
            "policy_decision": policy["decision"],
            "lifecycle_state": precheck["lifecycle_state"],
            "qos_decision": qos_decision["decision"],
            "binding": "linux-ipc",
        },
    )
    return {
        "state": "allowed" if allowed else "rejected",
        "service": service,
        "method": method,
        "policy": policy,
        "lifecycle_state": precheck["lifecycle_state"],
        "qos": precheck["qos"],
        "qos_decision": qos_decision,
        "audit": {
            "scope": "linux-ipc-daemon",
            "persistence": "enabled" if DEFAULT_IPC_AUDIT_LOG else "disabled",
            "path": DEFAULT_IPC_AUDIT_LOG,
        },
        "precheck_source": {
            "mode": "local-runtime-governance",
            "fallback_reason": reason,
        },
        "req_ids": ["XSC-005", "XSC-006", "NV-G-002", "NV-G-004", "NV-G-005", "NV-G-006", "NV-G-007", "NV-P-002", "DEL-002"],
    }, allowed


def shared_governance_precheck(trace_id: str, payload: dict[str, Any]) -> tuple[dict[str, Any], bool] | None:
    if not DEFAULT_GOVERNANCE_SOCKET:
        return None

    return precheck_service_via_socket(
        DEFAULT_GOVERNANCE_SOCKET,
        trace_id,
        payload,
        ["XSC-005", "XSC-006", "NV-P-002", "DEL-002"],
    )


def shared_governance_diagnostic(trace_id: str, operation: str, payload: dict[str, Any]) -> dict[str, Any] | None:
    if not DEFAULT_GOVERNANCE_SOCKET:
        return None
    if operation == "governance.runtime.get":
        return get_runtime_via_socket(
            DEFAULT_GOVERNANCE_SOCKET,
            trace_id,
            ["XSC-005", "XSC-006", "NV-G-001", "NV-G-007", "NV-P-002", "DEL-002"],
        )
    if operation == "audit.recent.get":
        return get_audit_via_socket(
            DEFAULT_GOVERNANCE_SOCKET,
            trace_id,
            payload,
            ["XSC-005", "XSC-006", "NV-G-007", "NV-P-002", "DEL-002"],
        )
    return None


def ipc_governance_precheck(trace_id: str, operation: str, payload: dict[str, Any]) -> tuple[dict[str, Any] | None, bool]:
    if operation != "soa.service.invoke":
        return None, True

    try:
        shared = shared_governance_precheck(trace_id, payload)
        if shared is not None:
            return shared
    except (OSError, TimeoutError, json.JSONDecodeError, KeyError, TypeError) as exc:
        return local_governance_precheck(trace_id, payload, f"shared governance daemon unavailable: {exc}")

    return local_governance_precheck(trace_id, payload)


def validate_envelope(envelope: Any) -> tuple[str, str, dict[str, Any]]:
    if not isinstance(envelope, dict):
        raise ValueError("IPC envelope must be a JSON object")
    trace_id = envelope.get("trace_id")
    operation = envelope.get("operation")
    payload = envelope.get("payload")
    req_ids = envelope.get("req_ids", [])
    if not isinstance(trace_id, str) or not trace_id:
        raise ValueError("trace_id must be a non-empty string")
    if operation not in OPERATION_MAP:
        raise ValueError(f"unsupported operation: {operation}")
    if not isinstance(payload, dict):
        raise ValueError("payload must be an object")
    if req_ids and (not isinstance(req_ids, list) or "XSC-006" not in req_ids):
        raise ValueError("req_ids must include XSC-006 when provided")
    return trace_id, operation, payload


def call_gateway(base_url: str, trace_id: str, operation: str, payload: dict[str, Any]) -> dict[str, Any]:
    mapping = OPERATION_MAP[operation]
    diagnostic_fallback_reason = None
    if operation in {"governance.runtime.get", "audit.recent.get"}:
        try:
            diagnostic = shared_governance_diagnostic(trace_id, operation, payload)
            if diagnostic is not None:
                return response(
                    trace_id,
                    "ok",
                    {
                        "operation": operation,
                        "semantic_path": mapping["path"],
                        "forwarding": "shared-governance-socket",
                        "shared_governance_diagnostic": diagnostic,
                        "req_ids": mapping["req_ids"] + ["XSC-005", "NV-G-007"],
                    },
                )
        except (OSError, TimeoutError, json.JSONDecodeError, KeyError, TypeError) as exc:
            diagnostic_fallback_reason = f"shared governance daemon unavailable: {exc}"

    governance_precheck, can_forward = ipc_governance_precheck(trace_id, operation, payload)
    if governance_precheck is not None and not can_forward:
        return response(
            trace_id,
            "ok",
            {
                "operation": operation,
                "semantic_path": mapping["path"],
                "forwarding": "blocked-before-rest-gateway",
                "ipc_governance_precheck": governance_precheck,
                "req_ids": mapping["req_ids"] + ["XSC-005", "NV-G-004", "NV-G-005", "NV-G-007"],
            },
        )

    method = mapping["method"]
    body = None
    headers = {"Accept": "application/json"}
    if method == "POST":
        outbound = dict(payload)
        outbound.setdefault("trace_id", trace_id)
        body = json.dumps(outbound).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"

    req = request.Request(base_url.rstrip("/") + mapping["path"], data=body, headers=headers, method=method)
    with request.urlopen(req, timeout=5) as gateway_response:
        gateway_payload = json.loads(gateway_response.read().decode("utf-8"))

    return response(
        trace_id,
        "ok",
        {
            "operation": operation,
            "semantic_path": mapping["path"],
            "ipc_governance_precheck": governance_precheck,
            "shared_governance_diagnostic_fallback": diagnostic_fallback_reason,
            "gateway": gateway_payload,
            "req_ids": mapping["req_ids"],
        },
    )


def handle_bytes(raw: bytes, base_url: str) -> dict[str, Any]:
    fallback_trace_id = "unknown"
    try:
        envelope = json.loads(raw.decode("utf-8"))
        trace_id, operation, payload = validate_envelope(envelope)
        fallback_trace_id = trace_id
        return call_gateway(base_url, trace_id, operation, payload)
    except json.JSONDecodeError as exc:
        return response(fallback_trace_id, "error", {}, {"code": "INVALID_JSON", "message": str(exc)})
    except ValueError as exc:
        return response(fallback_trace_id, "error", {}, {"code": "INVALID_ENVELOPE", "message": str(exc)})
    except (HTTPError, URLError, TimeoutError, OSError) as exc:
        return response(fallback_trace_id, "error", {}, {"code": "GATEWAY_UNAVAILABLE", "message": str(exc)})


def serve(socket_path: str, base_url: str) -> int:
    if os.path.exists(socket_path):
        os.unlink(socket_path)

    shutdown = False

    def stop(_signum: int, _frame: Any) -> None:
        nonlocal shutdown
        shutdown = True

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)

    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as server:
        server.bind(socket_path)
        os.chmod(socket_path, 0o660)
        server.listen(8)
        server.settimeout(0.2)
        print(f"Central Brain Linux IPC daemon listening on {socket_path}", flush=True)
        while not shutdown:
            try:
                conn, _ = server.accept()
            except socket.timeout:
                continue
            with conn:
                chunks = []
                while True:
                    chunk = conn.recv(65536)
                    if not chunk:
                        break
                    chunks.append(chunk)
                raw = b"".join(chunks)
                payload = handle_bytes(raw, base_url)
                conn.sendall(json.dumps(payload, ensure_ascii=False).encode("utf-8") + b"\n")

    if os.path.exists(socket_path):
        os.unlink(socket_path)
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the Central Brain Linux IPC binding sample.")
    parser.add_argument("--socket-path", default=DEFAULT_SOCKET_PATH)
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    args = parser.parse_args()
    try:
        return serve(args.socket_path, args.base_url)
    except OSError as exc:
        print(f"ipc daemon failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
