#!/usr/bin/env python3
"""AI SDK and Agent planning facade for the Central Brain prototype."""

from __future__ import annotations

import time
import uuid
from typing import Any


AI_SDK_REQ_IDS = ["XSC-001", "APP-004", "NV-F-001", "FW-U-006", "FW-U-007"]

SKILL_MANIFESTS: list[dict[str, Any]] = [
    {
        "skill_id": "vehicle.state.query",
        "name": "Vehicle State Query",
        "version": "0.1.0",
        "status": "active-contract-mock",
        "permissions": ["vehicle.read"],
        "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
        "semantic_entry": "POST /soa/invoke service=vehicle-state method=getState",
        "sandbox": {"network": "none", "vehicle_write": False, "cloud_access": False},
        "req_ids": ["XSC-001", "FW-U-006", "XSC-003", "FW-S-003", "FW-S-005"],
    },
    {
        "skill_id": "cabin.precondition",
        "name": "Cabin Precondition",
        "version": "0.1.0",
        "status": "contract-only",
        "permissions": ["vehicle.read", "vehicle.control"],
        "allowed_safety_states": ["normal"],
        "semantic_entry": "POST /uib/actions/request action=Cabin.SetTemperature",
        "sandbox": {"network": "none", "vehicle_write": "requires Action", "cloud_access": False},
        "req_ids": ["XSC-001", "FW-U-006", "FW-U-004", "FW-U-007", "NV-G-005"],
    },
    {
        "skill_id": "cabin.scene.nap",
        "name": "Cabin Nap Scene",
        "version": "0.1.0",
        "status": "contract-only",
        "permissions": ["vehicle.read", "vehicle.control"],
        "allowed_safety_states": ["normal"],
        "semantic_entry": "POST /agent/plan intent=cabin_nap_prepare; executable actions must re-enter /uib/actions/request or /soa/invoke",
        "sandbox": {"network": "none", "vehicle_write": "requires Action", "cloud_access": False},
        "req_ids": ["XSC-001", "FW-U-006", "FW-U-004", "FW-U-007", "NV-G-005"],
    },
]

MEMORY_ITEMS: list[dict[str, Any]] = [
    {
        "memory_id": "mem-cabin-temp-local",
        "scope": "driver_profile",
        "classification": "local_sensitive",
        "ttl": "long_term",
        "content": {"preference": "cabin_temperature", "value": 22.5, "unit": "celsius"},
        "privacy": {"cloud_sync": False, "requires_user_consent": True},
        "req_ids": ["XSC-001", "NV-F-001", "FW-U-006"],
    },
    {
        "memory_id": "mem-last-state-query",
        "scope": "vehicle_session",
        "classification": "local_operational",
        "ttl": "session",
        "content": {"last_intent": "vehicle_state_query", "preferred_entry": "GET /uib/state"},
        "privacy": {"cloud_sync": False, "requires_user_consent": False},
        "req_ids": ["XSC-001", "NV-F-001", "FW-U-006"],
    },
]


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
                "name": "executeTask",
                "path": "POST /agent/execute",
                "description": "Validate a task graph and return policy-gated execution dispatch boundaries.",
                "req_ids": ["XSC-001", "APP-004", "NV-F-001", "FW-U-006", "FW-U-007"],
            },
            {
                "name": "listSkills",
                "path": "GET /skills",
                "description": "Return Skill manifests with sandbox, permissions, and semantic entry constraints.",
                "req_ids": ["XSC-001", "FW-U-006"],
            },
            {
                "name": "queryMemory",
                "path": "POST /memory/query",
                "description": "Return local-only mock memory items for task planning context.",
                "req_ids": ["XSC-001", "NV-F-001", "FW-U-006"],
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
            "path": "Binder/AIDL client methods planAgentTaskJson/executeAgentTaskJson -> /agent/*",
            "status": "contract-sample",
            "req_ids": ["DEL-001", "XSC-001", "XSC-006", "NV-P-002"],
        },
        "linux_delivery": {
            "path": "CLI commands agent-plan/agent-execute and Linux IPC operations agent.plan/agent.execute",
            "status": "active-sample",
            "req_ids": ["DEL-002", "XSC-001", "XSC-006", "NV-P-002"],
        },
        "req_ids": AI_SDK_REQ_IDS + ["DEL-001", "DEL-002"],
    }


def skills_payload() -> dict[str, Any]:
    return {
        "skills": SKILL_MANIFESTS,
        "constraints": [
            "Skills are FW-U-006 Tool contracts; they cannot bypass Uni Info Bus, Action, SOA, Policy, or Audit.",
            "This prototype returns sandbox metadata and mock invocation boundaries only.",
        ],
        "req_ids": ["XSC-001", "FW-U-006", "FW-U-007", "NV-G-005", "DEL-001", "DEL-002"],
    }


def _find_skill(skill_id: str) -> dict[str, Any] | None:
    for skill in SKILL_MANIFESTS:
        if skill["skill_id"] == skill_id:
            return skill
    return None


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
    elif intent == "home_trip_prepare":
        required_permissions = ["vehicle.read", "vehicle.control", "service.read"]
        steps = [
            {
                "step_id": "context",
                "type": "read_context",
                "semantic_entry": "GET /uib/context",
                "req_ids": ["XSC-002", "FW-U-001"],
            },
            {
                "step_id": "memory",
                "type": "query_memory",
                "semantic_entry": "POST /memory/query",
                "depends_on": ["context"],
                "req_ids": ["XSC-001", "NV-F-001", "FW-U-006"],
            },
            {
                "step_id": "route",
                "type": "invoke_service",
                "service": "navigation-route-planner",
                "method": "planAlternatives",
                "semantic_entry": "POST /soa/invoke",
                "depends_on": ["memory"],
                "state": "planned-contract-only",
                "req_ids": ["XSC-003", "FW-S-001", "FW-S-005"],
            },
            {
                "step_id": "cabin",
                "type": "request_action",
                "action": "Cabin.SetTemperature",
                "semantic_entry": "POST /uib/actions/request",
                "depends_on": ["memory"],
                "state": "planned-only",
                "req_ids": ["XSC-002", "FW-U-004", "FW-U-007"],
            },
            {
                "step_id": "media",
                "type": "invoke_skill",
                "skill_id": "media.favorites.play",
                "semantic_entry": "POST /skills/media.favorites.play/invoke",
                "depends_on": ["memory"],
                "state": "planned-contract-only",
                "req_ids": ["XSC-001", "FW-U-006", "FW-U-007"],
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


def execute_payload(request: dict[str, Any], policy_decision: dict[str, Any]) -> dict[str, Any]:
    task = request.get("task") if isinstance(request.get("task"), dict) else {}
    steps = task.get("steps") if isinstance(task.get("steps"), list) else request.get("steps", [])
    if not isinstance(steps, list):
        steps = []

    execution_steps: list[dict[str, Any]] = []
    for index, step in enumerate(steps):
        if not isinstance(step, dict):
            continue
        step_type = step.get("type", "unknown")
        semantic_entry = step.get("semantic_entry")
        if not semantic_entry:
            if step_type == "invoke_skill":
                semantic_entry = f"POST /skills/{step.get('skill_id', 'unknown')}/invoke"
            elif step_type == "request_action":
                semantic_entry = "POST /uib/actions/request"
            elif step_type == "invoke_service":
                semantic_entry = "POST /soa/invoke"
            else:
                semantic_entry = "GET /uib/state"
        execution_steps.append(
            {
                "step_id": step.get("step_id", f"step-{index + 1}"),
                "type": step_type,
                "semantic_entry": semantic_entry,
                "state": "validated-not-dispatched" if policy_decision["decision"] == "allow" else "blocked_by_policy",
                "dispatch": {
                    "tool": "contract-visible" if step_type in {"invoke_skill", "call_tool"} else "not-used",
                    "soa": "required-entry" if "soa" in semantic_entry else "not-used",
                    "action": "required-entry" if "actions" in semantic_entry else "not-used",
                    "driver_hal": "not-dispatched",
                    "virtualization": "not-developed",
                },
                "req_ids": ["FW-U-006", "FW-U-007", "NV-G-005"],
            }
        )

    return {
        "task_execution": {
            "task_id": task.get("task_id") or request.get("task_id") or f"task-{uuid.uuid4().hex[:12]}",
            "trace_id": request.get("trace_id") or str(uuid.uuid4()),
            "state": "validated_mock" if policy_decision["decision"] == "allow" else "blocked_by_policy",
            "execution_mode": "policy-checked-contract-mock",
            "steps": execution_steps,
            "policy": policy_decision,
            "audit": "recorded-by-runtime-governance",
        },
        "execution_boundary": {
            "agent_execute": "No real Skill, Memory write, Model Runtime Adapter, Driver/HAL, or vehicle bus operation is executed.",
            "required_runtime_path": "Executable work must be re-entered through /soa/invoke, /uib/actions/request, or /skills/{skill_id}/invoke.",
            "req_ids": ["XSC-001", "APP-004", "NV-F-001", "FW-U-006", "FW-U-007"],
        },
        "req_ids": AI_SDK_REQ_IDS + ["XSC-002", "XSC-003", "XSC-005", "NV-G-005"],
    }


def skill_invoke_payload(skill_id: str, request: dict[str, Any], policy_decision: dict[str, Any]) -> dict[str, Any]:
    skill = _find_skill(skill_id)
    if skill is None:
        return {
            "skill_id": skill_id,
            "state": "rejected",
            "reason": "skill is not registered",
            "req_ids": ["XSC-001", "FW-U-006", "NV-G-005"],
        }

    allowed = policy_decision["decision"] == "allow"
    return {
        "skill_invocation": {
            "invocation_id": f"skill-{uuid.uuid4().hex[:12]}",
            "skill_id": skill_id,
            "state": "completed_mock" if allowed else "blocked_by_policy",
            "execution_mode": "sandbox-contract-mock",
            "semantic_entry": skill["semantic_entry"],
            "sandbox": skill["sandbox"],
            "policy": policy_decision,
            "result": {
                "message": "skill invocation contract accepted; no real adapter or vehicle bus dispatch was performed",
                "input_echo": request.get("input", {}),
            } if allowed else None,
        },
        "req_ids": skill["req_ids"] + ["NV-G-005", "DEL-001", "DEL-002"],
    }


def memory_query_payload(request: dict[str, Any], policy_decision: dict[str, Any]) -> dict[str, Any]:
    query = str(request.get("query") or request.get("intent") or "").lower()
    if policy_decision["decision"] != "allow":
        items: list[dict[str, Any]] = []
    elif "cabin" in query or "temperature" in query:
        items = [MEMORY_ITEMS[0]]
    elif "state" in query:
        items = [MEMORY_ITEMS[1]]
    else:
        items = MEMORY_ITEMS

    return {
        "memory_query": {
            "state": "completed_mock" if policy_decision["decision"] == "allow" else "blocked_by_policy",
            "scope": request.get("scope", "local_vehicle"),
            "items": items,
            "policy": policy_decision,
            "privacy": {
                "cloud_sync": False,
                "storage": "in-process mock only",
                "egress": "not allowed by this endpoint",
            },
        },
        "req_ids": ["XSC-001", "NV-F-001", "FW-U-006", "FW-U-007", "NV-G-005"],
    }
