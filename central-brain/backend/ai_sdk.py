#!/usr/bin/env python3
"""AI SDK and Agent planning facade for the Central Brain prototype."""

from __future__ import annotations

import time
import uuid
from typing import Any


AI_SDK_REQ_IDS = ["XSC-001", "APP-004", "NV-F-001", "FW-U-006", "FW-U-007"]


def capabilities_payload() -> dict[str, Any]:
    return {
        "sdk": {
            "name": "central-brain-ai-sdk",
            "version": "0.1.0",
            "status": "active-mock",
            "role": "Application-layer client facade for intent, tool, memory, and model routing.",
        },
        "entrypoints": [
            {
                "name": "planTask",
                "path": "POST /agent/plan",
                "description": "Convert app intent or scene trigger into a policy-aware task graph.",
                "req_ids": ["XSC-001", "APP-004", "NV-F-001"],
            },
            {
                "name": "invokeService",
                "path": "POST /soa/invoke",
                "description": "Execute approved service calls through SOA and Runtime & Governance.",
                "req_ids": ["XSC-003", "FW-U-005", "FW-S-005"],
            },
        ],
        "routing_constraints": [
            "Apps call AI SDK/Agent task APIs instead of direct model runtime or vendor SDK APIs.",
            "Task steps must resolve to Uni Info Bus semantic objects, Tools, Actions, or SOA services.",
            "Execution remains gated by Runtime & Governance Policy, Lifecycle, QoS, and Audit.",
        ],
        "android_delivery": {
            "path": "Binder/AIDL client method planAgentTaskJson -> /agent/plan",
            "status": "contract-sample",
            "req_ids": ["DEL-001", "XSC-001", "XSC-006", "NV-P-002"],
        },
        "linux_delivery": {
            "path": "CLI command agent-plan and Linux IPC operation agent.plan",
            "status": "active-sample",
            "req_ids": ["DEL-002", "XSC-001", "XSC-006", "NV-P-002"],
        },
        "req_ids": AI_SDK_REQ_IDS + ["DEL-001", "DEL-002"],
    }


def _intent_from_request(request: dict[str, Any]) -> str:
    intent = request.get("intent")
    if isinstance(intent, str) and intent.strip():
        return intent.strip()

    utterance = request.get("utterance") or request.get("text")
    if isinstance(utterance, str) and utterance.strip():
        lowered = utterance.lower()
        if "state" in lowered or "battery" in lowered or "speed" in lowered:
            return "vehicle_state_query"
        if "comfort" in lowered or "seat" in lowered or "cabin" in lowered:
            return "cabin_comfort_prepare"
        return "general_vehicle_assistant"

    return "vehicle_state_query"


def plan_payload(request: dict[str, Any], policy_decision: dict[str, Any]) -> dict[str, Any]:
    intent = _intent_from_request(request)
    task_id = request.get("task_id") or f"task-{uuid.uuid4().hex[:12]}"
    trace_id = request.get("trace_id") or str(uuid.uuid4())
    now_ms = int(time.time() * 1000)

    if intent == "cabin_comfort_prepare":
        required_permissions = ["vehicle.read", "vehicle.control"]
        steps = [
            {
                "step_id": "context",
                "type": "read_context",
                "semantic_entry": "GET /uib/context",
                "req_ids": ["XSC-002", "FW-U-001"],
            },
            {
                "step_id": "policy",
                "type": "evaluate_policy",
                "semantic_entry": "POST /policy/evaluate",
                "depends_on": ["context"],
                "req_ids": ["FW-U-007", "NV-G-005"],
            },
            {
                "step_id": "prepare_cabin",
                "type": "invoke_service",
                "service": "cabin-comfort-scene",
                "method": "prepare",
                "semantic_entry": "POST /soa/invoke",
                "depends_on": ["policy"],
                "state": "planned-only",
                "req_ids": ["XSC-003", "FW-S-001", "FW-S-005"],
            },
        ]
    else:
        required_permissions = ["vehicle.read"]
        steps = [
            {
                "step_id": "state",
                "type": "read_state",
                "semantic_entry": "GET /uib/state",
                "req_ids": ["XSC-002", "FW-U-002"],
            },
            {
                "step_id": "query_vehicle_state",
                "type": "invoke_service",
                "service": "vehicle-state",
                "method": "getState",
                "semantic_entry": "POST /soa/invoke",
                "depends_on": ["state"],
                "req_ids": ["XSC-003", "FW-S-003", "FW-S-005"],
            },
        ]

    return {
        "task": {
            "task_id": task_id,
            "trace_id": trace_id,
            "intent": intent,
            "created_at_ms": now_ms,
            "state": "planned" if policy_decision["decision"] == "allow" else "blocked_by_policy",
            "planner": "ai-sdk-agent-planner-mock",
            "steps": steps,
            "policy": {
                "required_permissions": required_permissions,
                "decision": policy_decision,
            },
            "execution_boundary": "Plan only; execution must use SOA services, Tool, or Action entries.",
        },
        "sdk_boundary": {
            "app_contract": "App submits intent or utterance to AI SDK/Agent facade.",
            "model_runtime_contract": "Model Runtime Adapter remains behind SOA/AIOS Kernel; apps do not call NPU runtime directly.",
            "req_ids": ["XSC-001", "APP-004", "NV-F-001", "NV-F-011"],
        },
        "req_ids": AI_SDK_REQ_IDS + ["XSC-002", "XSC-003", "FW-S-005"],
    }
