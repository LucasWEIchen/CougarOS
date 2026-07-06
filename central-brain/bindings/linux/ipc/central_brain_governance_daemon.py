#!/usr/bin/env python3
"""Linux Runtime & Governance Unix socket sample.

Req IDs: XSC-005, XSC-006, NV-G-002, NV-G-004, NV-G-005, NV-G-006, NV-G-007, NV-P-002, DEL-002.
This daemon is a small Linux delivery sample for sharing Runtime & Governance
precheck decisions, runtime status, and recent audit visibility across local
Protocol Binding processes.
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

BACKEND_DIR = Path(__file__).resolve().parents[3] / "backend"
sys.path.insert(0, str(BACKEND_DIR))

from runtime_governance import RuntimeGovernance


DEFAULT_SOCKET_PATH = os.environ.get("CENTRAL_BRAIN_GOVERNANCE_SOCKET", "/tmp/central_brain_governance.sock")
DEFAULT_AUDIT_LOG = os.environ.get("CENTRAL_BRAIN_GOVERNANCE_AUDIT_LOG") or os.environ.get("CENTRAL_BRAIN_IPC_AUDIT_LOG")
GOVERNANCE = RuntimeGovernance(DEFAULT_AUDIT_LOG)


def response(trace_id: str, status: str, payload: dict[str, Any], error: dict[str, Any] | None = None) -> dict[str, Any]:
    return {
        "trace_id": trace_id,
        "status": status,
        "error": error,
        "payload": payload,
        "req_ids": ["XSC-005", "XSC-006", "NV-P-002", "DEL-002"],
    }


def validate_envelope(envelope: Any) -> tuple[str, str, dict[str, Any]]:
    if not isinstance(envelope, dict):
        raise ValueError("governance envelope must be a JSON object")
    trace_id = envelope.get("trace_id")
    operation = envelope.get("operation")
    payload = envelope.get("payload")
    req_ids = envelope.get("req_ids", [])
    if not isinstance(trace_id, str) or not trace_id:
        raise ValueError("trace_id must be a non-empty string")
    if operation not in {"governance.precheck", "governance.runtime.get", "audit.recent.get"}:
        raise ValueError(f"unsupported operation: {operation}")
    if not isinstance(payload, dict):
        raise ValueError("payload must be an object")
    if req_ids and (not isinstance(req_ids, list) or "XSC-005" not in req_ids or "XSC-006" not in req_ids):
        raise ValueError("req_ids must include XSC-005 and XSC-006 when provided")
    return trace_id, operation, payload


def governance_precheck(trace_id: str, payload: dict[str, Any]) -> dict[str, Any]:
    consume_qos = bool(payload.get("consume_qos", True))
    precheck = GOVERNANCE.precheck(payload, consume_qos=consume_qos)
    service = payload.get("service", "vehicle-state")
    method = payload.get("method", "invoke")
    policy = precheck["policy"]
    qos_decision = precheck["qos_decision"]
    allowed = policy["decision"] == "allow" and qos_decision["decision"] == "allow"
    outcome = "shared_governance_precheck_allowed" if allowed else "shared_governance_precheck_rejected"
    GOVERNANCE.record_audit(
        trace_id,
        {
            "service": service,
            "method": method,
            "outcome": outcome,
            "policy_decision": policy["decision"],
            "lifecycle_state": precheck["lifecycle_state"],
            "qos_decision": qos_decision["decision"],
            "binding": "linux-governance-daemon",
        },
    )
    return {
        "state": "allowed" if allowed else "rejected",
        "service": service,
        "method": method,
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
            "source": "linux-governance-daemon",
        },
        "audit": {
            "scope": "linux-governance-daemon",
            "persistence": "enabled" if DEFAULT_AUDIT_LOG else "disabled",
            "path": DEFAULT_AUDIT_LOG,
        },
        "req_ids": ["XSC-005", "NV-G-002", "NV-G-004", "NV-G-005", "NV-G-006", "NV-G-007", "NV-P-002", "DEL-002"],
    }


def governance_runtime(trace_id: str) -> dict[str, Any]:
    payload = GOVERNANCE.governance_payload()
    payload["shared_daemon"] = {
        "trace_id": trace_id,
        "operation": "governance.runtime.get",
        "source": "linux-governance-daemon",
        "socket": DEFAULT_SOCKET_PATH,
        "dispatch": {
            "service_invoked": False,
            "driver_hal": "not-dispatched",
            "virtualization": "not-developed",
        },
        "req_ids": ["XSC-005", "XSC-006", "NV-G-001", "NV-G-007", "NV-P-002", "DEL-002"],
    }
    return payload


def governance_audit(payload: dict[str, Any]) -> dict[str, Any]:
    limit = int(payload.get("limit", 20) or 20)
    limit = max(1, min(limit, 50))
    audit_payload = GOVERNANCE.audit_payload(limit=limit)
    audit_payload["shared_daemon"] = {
        "operation": "audit.recent.get",
        "source": "linux-governance-daemon",
        "dispatch": {
            "service_invoked": False,
            "driver_hal": "not-dispatched",
            "virtualization": "not-developed",
        },
        "req_ids": ["XSC-005", "XSC-006", "NV-G-007", "NV-P-002", "DEL-002"],
    }
    return audit_payload


def handle_bytes(raw: bytes) -> dict[str, Any]:
    fallback_trace_id = "unknown"
    try:
        envelope = json.loads(raw.decode("utf-8"))
        trace_id, operation, payload = validate_envelope(envelope)
        fallback_trace_id = trace_id
        if operation == "governance.precheck":
            result = governance_precheck(trace_id, payload)
        elif operation == "governance.runtime.get":
            result = governance_runtime(trace_id)
        else:
            result = governance_audit(payload)
        return response(trace_id, "ok", result)
    except json.JSONDecodeError as exc:
        return response(fallback_trace_id, "error", {}, {"code": "INVALID_JSON", "message": str(exc)})
    except ValueError as exc:
        return response(fallback_trace_id, "error", {}, {"code": "INVALID_ENVELOPE", "message": str(exc)})


def serve(socket_path: str) -> int:
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
        print(f"Central Brain Linux governance daemon listening on {socket_path}", flush=True)
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
                payload = handle_bytes(b"".join(chunks))
                conn.sendall(json.dumps(payload, ensure_ascii=False).encode("utf-8") + b"\n")

    if os.path.exists(socket_path):
        os.unlink(socket_path)
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the Central Brain Linux Runtime & Governance socket sample.")
    parser.add_argument("--socket-path", default=DEFAULT_SOCKET_PATH)
    args = parser.parse_args()
    try:
        return serve(args.socket_path)
    except OSError as exc:
        print(f"governance daemon failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
