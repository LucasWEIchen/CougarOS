#!/usr/bin/env python3
"""Shared Linux governance socket client for Protocol Binding samples.

Req IDs: XSC-005, XSC-006, NV-G-002, NV-G-004, NV-G-005, NV-G-006,
NV-G-007, NV-P-002, NV-P-003, DEL-002.
"""

from __future__ import annotations

import json
import socket
from typing import Any


def call_governance_socket(
    socket_path: str,
    trace_id: str,
    operation: str,
    payload: dict[str, Any] | None = None,
    req_ids: list[str] | None = None,
    timeout_seconds: float = 3,
) -> dict[str, Any]:
    envelope = {
        "trace_id": trace_id,
        "operation": operation,
        "payload": payload or {},
        "req_ids": req_ids or ["XSC-005", "XSC-006", "DEL-002"],
    }
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as client:
        client.settimeout(timeout_seconds)
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


def precheck_service_via_socket(
    socket_path: str,
    trace_id: str,
    payload: dict[str, Any],
    req_ids: list[str],
    source_label: str = "shared-linux-governance-daemon",
) -> tuple[dict[str, Any], bool]:
    result = call_governance_socket(
        socket_path,
        trace_id,
        "governance.precheck",
        {**payload, "consume_qos": payload.get("consume_qos", True)},
        req_ids,
    )
    if result.get("status") != "ok":
        message = result.get("error", {}).get("message", "governance daemon rejected precheck")
        raise OSError(message)

    precheck = result["payload"]
    allowed = precheck["state"] == "allowed"
    precheck["precheck_source"] = {
        "mode": source_label,
        "socket": socket_path,
    }
    return precheck, allowed


def _diagnostic_payload_from_socket(
    socket_path: str,
    trace_id: str,
    operation: str,
    payload: dict[str, Any],
    req_ids: list[str],
    source_label: str,
) -> dict[str, Any]:
    result = call_governance_socket(socket_path, trace_id, operation, payload, req_ids)
    if result.get("status") != "ok":
        message = result.get("error", {}).get("message", f"governance daemon rejected {operation}")
        raise OSError(message)

    diagnostic = result["payload"]
    diagnostic["diagnostic_source"] = {
        "mode": source_label,
        "socket": socket_path,
        "operation": operation,
    }
    return diagnostic


def get_runtime_via_socket(
    socket_path: str,
    trace_id: str,
    req_ids: list[str],
    source_label: str = "shared-linux-governance-daemon",
) -> dict[str, Any]:
    return _diagnostic_payload_from_socket(
        socket_path,
        trace_id,
        "governance.runtime.get",
        {},
        req_ids,
        source_label,
    )


def get_audit_via_socket(
    socket_path: str,
    trace_id: str,
    payload: dict[str, Any],
    req_ids: list[str],
    source_label: str = "shared-linux-governance-daemon",
) -> dict[str, Any]:
    return _diagnostic_payload_from_socket(
        socket_path,
        trace_id,
        "audit.recent.get",
        payload,
        req_ids,
        source_label,
    )
