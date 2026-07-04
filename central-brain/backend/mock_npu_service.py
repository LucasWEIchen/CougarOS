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
from native_adapters import NativeAdapterRegistry
from protocol_bindings import ProtocolBindingRegistry
from runtime_governance import RuntimeGovernance


STARTED_AT = time.time()
API_VERSION = "0.1.14"
GOVERNANCE = RuntimeGovernance(os.environ.get("CENTRAL_BRAIN_AUDIT_LOG"))
BINDINGS = ProtocolBindingRegistry()
NATIVE_ADAPTERS = NativeAdapterRegistry()
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
            "vehicle": vehicle_state_payload(),
            "safety_state": "normal"
        },
        "req_ids": ["FW-U-002"]
    }


def event_topics_payload() -> dict[str, Any]:
    return {
        "topics": [
            {
                "name": topic,
                "mode": "active-mock",
                "delivery": ["publish-ack", "recent-log"],
                "subscription_contract": {
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


def bindings_payload() -> dict[str, Any]:
    return BINDINGS.list_payload()


def binding_detail_payload() -> dict[str, Any]:
    return BINDINGS.detail_payload()


def native_adapters_payload() -> dict[str, Any]:
    return NATIVE_ADAPTERS.list_payload()


def native_adapters_detail_payload() -> dict[str, Any]:
    return NATIVE_ADAPTERS.detail_payload()


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
        elif path == "/governance/runtime":
            self.send_json(200, envelope(governance_payload()))
        elif path == "/audit/recent":
            self.send_json(200, envelope(GOVERNANCE.audit_payload()))
        elif path == "/bindings":
            self.send_json(200, envelope(bindings_payload()))
        elif path == "/bindings/detail":
            self.send_json(200, envelope(binding_detail_payload()))
        elif path == "/native/adapters":
            self.send_json(200, envelope(native_adapters_payload()))
        elif path == "/native/adapters/detail":
            self.send_json(200, envelope(native_adapters_detail_payload()))
        elif path in ("/events/topics", "/uib/events/topics"):
            self.send_json(200, envelope(event_topics_payload()))
        elif path == "/uib/events/recent":
            limit = int(query.get("limit", ["20"])[0])
            self.send_json(200, envelope(event_recent_payload(limit)))
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
        elif path in ("/actions/request", "/uib/actions/request"):
            self.send_json(200, envelope(action_request_payload(request), request.get("trace_id")))
        elif path in ("/service/invoke", "/soa/invoke"):
            trace_id = request.get("trace_id") or str(uuid.uuid4())
            request["trace_id"] = trace_id
            self.send_json(200, envelope(service_invoke_payload(request), trace_id))
        elif path in ("/events/publish", "/uib/events/publish"):
            self.send_json(200, envelope(event_publish_payload(request), request.get("trace_id")))
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
