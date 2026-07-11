#!/usr/bin/env python3
"""KaKaClaw-inspired Agent scenario test harness for the prototype."""

from __future__ import annotations

import uuid
from typing import Any, Callable, Mapping


Operation = Callable[..., dict[str, Any]]

SCENARIO_REQ_IDS = [
    "APP-004",
    "XSC-001",
    "XSC-002",
    "XSC-003",
    "XSC-005",
    "XSC-006",
    "FW-U-004",
    "FW-U-006",
    "FW-U-007",
    "NV-F-001",
    "NV-F-011",
    "NV-G-005",
    "NV-G-007",
    "DEL-001",
    "DEL-002",
    "DEL-003",
]

SCENARIOS: tuple[dict[str, Any], ...] = (
    {
        "scenario_id": "care.cold",
        "label": "我冷了",
        "group": "task_service",
        "capabilities": ["fuzzy_intent", "active_care", "model_routing", "cabin_action"],
        "interfaces": ["POST /ai/infer", "POST /agent/plan", "POST /uib/actions/request"],
        "state": "active-mock",
    },
    {
        "scenario_id": "care.fatigue",
        "label": "我累了",
        "group": "task_service",
        "capabilities": ["fuzzy_intent", "active_care", "vehicle_context", "model_routing"],
        "interfaces": ["GET /vehicle/state", "POST /ai/infer"],
        "state": "active-mock",
    },
    {
        "scenario_id": "task.home",
        "label": "回家规划",
        "group": "task_service",
        "capabilities": ["task_as_service", "multi_instruction", "task_graph", "policy_gate"],
        "interfaces": ["POST /agent/plan", "POST /agent/execute"],
        "state": "contract-mock",
    },
    {
        "scenario_id": "skill.nap",
        "label": "午休模式",
        "group": "task_service",
        "capabilities": ["scene_skill", "skill_sandbox", "policy_gate"],
        "interfaces": ["POST /skills/cabin.scene.nap/invoke"],
        "state": "contract-mock",
    },
    {
        "scenario_id": "state.vehicle",
        "label": "车辆状态",
        "group": "context_growth",
        "capabilities": ["vehicle_context", "semantic_state"],
        "interfaces": ["GET /uib/state"],
        "state": "active-mock",
    },
    {
        "scenario_id": "memory.preference",
        "label": "偏好记忆",
        "group": "context_growth",
        "capabilities": ["long_term_memory", "local_first_privacy"],
        "interfaces": ["POST /memory/query"],
        "state": "active-mock",
    },
    {
        "scenario_id": "skills.catalog",
        "label": "技能中心",
        "group": "context_growth",
        "capabilities": ["skill_catalog", "sandbox_visibility", "permission_visibility"],
        "interfaces": ["GET /skills"],
        "state": "active-mock",
    },
    {
        "scenario_id": "security.denied",
        "label": "越权拦截",
        "group": "safety_runtime",
        "capabilities": ["default_deny", "policy_gate", "audit"],
        "interfaces": ["POST /policy/evaluate", "GET /audit/recent"],
        "state": "active-mock",
    },
    {
        "scenario_id": "security.privacy",
        "label": "隐私检查",
        "group": "safety_runtime",
        "capabilities": ["privacy_router", "local_first_memory", "default_deny"],
        "interfaces": ["POST /policy/evaluate"],
        "state": "contract-mock",
    },
    {
        "scenario_id": "governance.audit",
        "label": "审计记录",
        "group": "safety_runtime",
        "capabilities": ["audit_visibility", "traceability"],
        "interfaces": ["GET /audit/recent"],
        "state": "active-mock",
    },
    {
        "scenario_id": "runtime.npu",
        "label": "NPU状态",
        "group": "safety_runtime",
        "capabilities": ["model_runtime", "backend_visibility", "hardware_boundary"],
        "interfaces": ["GET /npu/status"],
        "state": "active-mock",
    },
    {
        "scenario_id": "system.overview",
        "label": "系统总览",
        "group": "safety_runtime",
        "capabilities": ["prototype_readiness", "android_linux_delivery", "production_boundary"],
        "interfaces": ["GET /prototype/completion-summary"],
        "state": "active-read-only",
    },
)


def catalog_payload() -> dict[str, Any]:
    return {
        "scenario_harness": {
            "name": "central-brain-agent-scenario-test-harness",
            "version": "0.1.0",
            "reference": "KaKaClaw public product concepts mapped to Central Brain prototype interfaces",
            "execution_entry": "POST /agent/scenarios/run",
            "scenario_count": len(SCENARIOS),
        },
        "groups": [
            {
                "group_id": "task_service",
                "label": "场景任务",
                "focus": ["task_as_service", "fuzzy_intent", "active_care", "multi_instruction"],
            },
            {
                "group_id": "context_growth",
                "label": "状态与成长",
                "focus": ["vehicle_context", "memory", "skills"],
            },
            {
                "group_id": "safety_runtime",
                "label": "安全与系统",
                "focus": ["policy", "privacy", "audit", "model_runtime", "readiness"],
            },
        ],
        "scenarios": [dict(item) for item in SCENARIOS],
        "reference_capability_gaps": [
            "continuous multi-turn dialogue session state",
            "switchable personality and dialect runtime",
            "zero-code Skill creation and publishing",
            "real navigation, media, cabin control, and ADAS dispatch",
            "production Skill sandbox and Privacy Router",
        ],
        "boundaries": {
            "independent_implementation": True,
            "product_compatibility_claimed": False,
            "real_vehicle_control": False,
            "service_dispatch_triggered": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "production_ready": False,
        },
        "req_ids": SCENARIO_REQ_IDS,
    }


def _scenario(scenario_id: str) -> dict[str, Any] | None:
    return next((dict(item) for item in SCENARIOS if item["scenario_id"] == scenario_id), None)


def _model_text(payload: dict[str, Any]) -> str:
    result = payload.get("result") if isinstance(payload.get("result"), dict) else {}
    for key in ("generated_text", "summary", "reason"):
        value = result.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return ""


def _check(module: str, interface: str, state: str, detail: str) -> dict[str, str]:
    return {"module": module, "interface": interface, "state": state, "detail": detail}


def run_payload(request: dict[str, Any], operations: Mapping[str, Operation]) -> dict[str, Any]:
    scenario_id = str(request.get("scenario_id") or "").strip()
    scenario = _scenario(scenario_id)
    trace_id = str(request.get("trace_id") or uuid.uuid4())
    utterance = str(request.get("utterance") or (scenario or {}).get("label") or "")

    if scenario is None:
        return {
            "trace_id": trace_id,
            "status": "error",
            "scenario": {"scenario_id": scenario_id or None},
            "result": {
                "outcome": "unknown_scenario",
                "generated_text": "未知测试场景，请刷新场景目录。",
                "checks": [],
            },
            "req_ids": SCENARIO_REQ_IDS,
        }

    caller_permissions = request.get("caller_permissions") or [
        "vehicle.read",
        "vehicle.control",
        "service.read",
        "ai.infer",
        "policy.read",
    ]
    base_request = {
        "trace_id": trace_id,
        "caller_permissions": caller_permissions,
        "vehicle_state": request.get("vehicle_state", "parked"),
        "safety_state": request.get("safety_state", "normal"),
    }
    checks: list[dict[str, str]] = []
    generated_text = ""
    outcome = "passed"
    evidence: dict[str, Any] = {}

    if scenario_id == "care.cold":
        plan = operations["agent_plan"](
            {**base_request, "intent": "cabin_comfort_prepare", "permissions": ["vehicle.read", "vehicle.control"]}
        )
        action = operations["action_request"](
            {
                **base_request,
                "action": "Cabin.SetTemperature",
                "target": {"zone": "row1-left", "temperature_c": 24.0},
                "permissions": ["vehicle.control"],
            }
        )
        inference = operations["inference"](
            {
                "runtime": request.get("runtime", "mock"),
                "model": request.get("model", "central-intent-v0"),
                "input": {"utterance": utterance, "source": "client2-agent-scenario-panel", "locale": "zh-CN"},
                "policy": {"safety_state_required": "normal", "timeout_ms": 90000},
                "caller": "Client2",
            }
        )
        model_text = _model_text(inference) or "已识别寒冷意图，建议提高空调温度或开启座椅加热。"
        plan_state = plan.get("task", {}).get("state", "unknown")
        action_state = action.get("state", "unknown")
        generated_text = f"{model_text}\n\n规划: {plan_state}；空调动作: {action_state}（策略校验 mock，未下发硬件）"
        checks.extend(
            [
                _check("AI SDK", "POST /agent/plan", plan_state, "舒适任务图"),
                _check("Uni Info Bus Action", "POST /uib/actions/request", action_state, "Cabin.SetTemperature"),
                _check("Model Runtime Adapter", "POST /ai/infer", inference.get("status", "unknown"), inference.get("runtime", "unknown")),
            ]
        )
        evidence = {"task_id": plan.get("task", {}).get("task_id"), "action_id": action.get("action_id")}

    elif scenario_id == "care.fatigue":
        vehicle = operations["vehicle_state"]()
        inference = operations["inference"](
            {
                "runtime": request.get("runtime", "mock"),
                "model": request.get("model", "central-intent-v0"),
                "input": {"utterance": utterance, "source": "client2-agent-scenario-panel", "locale": "zh-CN"},
                "policy": {"safety_state_required": "normal", "timeout_ms": 90000},
                "caller": "Client2",
            }
        )
        speed = vehicle.get("signals", {}).get("Vehicle.Speed", {}).get("value", "?")
        model_text = _model_text(inference) or "检测到疲劳表达，建议安全停车休息并减少驾驶负担。"
        generated_text = f"{model_text}\n\n当前 mock 车速: {speed} km/h；仅提供主动关怀建议，不接管辅助驾驶。"
        checks.extend(
            [
                _check("Vehicle Signal Adapter", "GET /vehicle/state", "read", f"Vehicle.Speed={speed}"),
                _check("Model Runtime Adapter", "POST /ai/infer", inference.get("status", "unknown"), inference.get("runtime", "unknown")),
            ]
        )

    elif scenario_id == "task.home":
        plan_request = {
            **base_request,
            "intent": "home_trip_prepare",
            "utterance": utterance,
            "permissions": ["vehicle.read", "vehicle.control", "service.read"],
        }
        plan = operations["agent_plan"](plan_request)
        task = plan.get("task", {})
        execution = operations["agent_execute"]({**base_request, "task": task})
        execution_state = execution.get("task_execution", {}).get("state", "unknown")
        step_count = len(task.get("steps", []))
        generated_text = (
            f"回家任务已生成 {step_count} 个步骤，执行校验: {execution_state}。\n\n"
            "覆盖上下文、偏好、导航/座舱/媒体语义入口；当前只验证任务图和策略，不调用真实导航或车控。"
        )
        checks.extend(
            [
                _check("AI SDK", "POST /agent/plan", task.get("state", "unknown"), f"steps={step_count}"),
                _check("Runtime & Governance", "POST /agent/execute", execution_state, "policy-checked-contract-mock"),
            ]
        )
        evidence = {"task_id": task.get("task_id"), "step_count": step_count}

    elif scenario_id == "skill.nap":
        invocation = operations["skill_invoke"](
            "cabin.scene.nap",
            {
                **base_request,
                "input": {"duration_minutes": 30, "seat": "row1-left"},
                "permissions": ["vehicle.read", "vehicle.control"],
            },
        )
        skill = invocation.get("skill_invocation", {})
        skill_state = skill.get("state", invocation.get("state", "unknown"))
        generated_text = (
            f"午休 Skill 校验: {skill_state}。\n\n"
            "座椅、车窗、遮阳和空调只形成沙箱内语义计划；所有车控仍必须重新进入 Action/SOA 与策略门禁。"
        )
        checks.append(_check("AI SDK Skill", "POST /skills/cabin.scene.nap/invoke", skill_state, "sandbox-contract-mock"))
        evidence = {"invocation_id": skill.get("invocation_id"), "sandbox": skill.get("sandbox")}

    elif scenario_id == "state.vehicle":
        state = operations["state"]()
        vehicle = state.get("state", {}).get("vehicle", {})
        signals = vehicle.get("signals", {})
        speed = signals.get("Vehicle.Speed", {}).get("value", "?")
        battery = signals.get("Vehicle.Powertrain.TractionBattery.StateOfCharge.Current", {}).get("value", "?")
        temperature = signals.get("Vehicle.Cabin.HVAC.Station.Row1.Left.Temperature", {}).get("value", "?")
        generated_text = f"车辆状态（mock）\n车速 {speed} km/h｜电量 {battery}%｜左前舱温 {temperature}°C"
        checks.append(_check("Uni Info Bus", "GET /uib/state", "read", f"signals={len(signals)}"))

    elif scenario_id == "memory.preference":
        memory = operations["memory_query"](
            {**base_request, "query": "cabin temperature preference", "scope": "driver_profile", "permissions": ["vehicle.read"]}
        )
        query = memory.get("memory_query", {})
        items = query.get("items", [])
        content = items[0].get("content", {}) if items else {}
        value = content.get("value", "?")
        generated_text = f"本地偏好记忆：常用舱温 {value}°C。\n\ncloud_sync=false；当前仅查询进程内 mock，不写入长期存储。"
        checks.append(_check("AI SDK Memory", "POST /memory/query", query.get("state", "unknown"), f"items={len(items)}"))

    elif scenario_id == "skills.catalog":
        skills = operations["skills"]().get("skills", [])
        names = "、".join(str(item.get("name", item.get("skill_id"))) for item in skills)
        generated_text = f"技能中心：{len(skills)} 个 contract mock。\n{names}\n\n零代码创建、发布和下载技能尚未实现。"
        checks.append(_check("AI SDK Skill", "GET /skills", "read", f"skills={len(skills)}"))

    elif scenario_id == "security.denied":
        policy = operations["permission_check"](
            {
                "permissions": ["vehicle.control"],
                "caller_permissions": ["vehicle.read"],
                "vehicle_state": "driving",
                "safety_state": "normal",
            }
        )
        decision = policy.get("decision", "unknown")
        outcome = "blocked_as_expected" if decision == "deny" else "failed"
        generated_text = f"越权测试：{decision.upper()}。\n{policy.get('reason', '')}\n\n未授权车控默认拦截，未访问车辆总线。"
        checks.append(_check("Runtime & Governance", "POST /policy/evaluate", decision, "vehicle.control while driving"))

    elif scenario_id == "security.privacy":
        policy = operations["permission_check"](
            {
                "permissions": ["cloud.egress"],
                "caller_permissions": ["vehicle.read"],
                "vehicle_state": "parked",
                "safety_state": "normal",
            }
        )
        decision = policy.get("decision", "unknown")
        outcome = "blocked_as_expected" if decision == "deny" else "failed"
        generated_text = f"隐私路由测试：外发请求 {decision.upper()}。\n\n用户偏好保持 local_only；当前为策略 contract mock，不是量产 Privacy Router。"
        checks.append(_check("Runtime & Governance", "POST /policy/evaluate", decision, "cloud.egress without permission"))

    elif scenario_id == "governance.audit":
        audit = operations["audit"]()
        events = audit.get("events", [])
        latest = events[0] if events else {}
        generated_text = (
            f"审计记录：当前 {len(events)} 条。\n"
            f"最近服务: {latest.get('service', 'none')}｜结果: {latest.get('outcome', 'none')}\n"
            f"保留策略: {audit.get('retention', 'unknown')}"
        )
        checks.append(_check("Runtime & Governance", "GET /audit/recent", "read", f"events={len(events)}"))

    elif scenario_id == "runtime.npu":
        npu = operations["npu_status"]()
        backend = npu.get("simulated_npu_backend", "unknown")
        backend_status = npu.get("simulated_backend_status", {})
        model = backend_status.get("model", backend_status.get("backend_model", "unknown"))
        generated_text = (
            f"模型运行时：{npu.get('runtime', 'unknown')}\n后端: {backend}｜模型: {model}\n\n"
            "hardware_accessed=false；Ollama 仅模拟外置 NPU 内模型。"
        )
        checks.append(_check("Model Runtime Adapter", "GET /npu/status", "read", f"backend={backend}"))

    elif scenario_id == "system.overview":
        completion = operations["completion"]()
        summary = completion.get("summary", {})
        generated_text = (
            f"Python 原型范围完成: {str(summary.get('python_prototype_current_scope_complete', False)).lower()}\n"
            f"Android 主路径: {str(summary.get('android_primary_path_ready', False)).lower()}｜Linux 同步: {str(summary.get('linux_synchronized_path_ready', False)).lower()}\n"
            f"量产就绪: {str(summary.get('production_ready', False)).lower()}｜量产阻塞项: {summary.get('production_blocker_count', '?')}"
        )
        checks.append(_check("Delivery Readiness", "GET /prototype/completion-summary", "read", "current-scope summary"))

    audit_event = operations["record_audit"](
        trace_id,
        {
            "service": "agent-scenario-test",
            "method": scenario_id,
            "outcome": outcome,
            "policy_decision": "deny" if outcome == "blocked_as_expected" else "allow",
            "lifecycle_state": "ready",
            "qos_decision": "not-applied",
        },
    )

    return {
        "trace_id": trace_id,
        "status": "ok",
        "scenario": scenario,
        "result": {
            "outcome": outcome,
            "generated_text": generated_text,
            "checks": checks,
        },
        "evidence": {**evidence, "audit_sequence": audit_event.get("sequence")},
        "boundaries": {
            "real_navigation_dispatched": False,
            "real_media_dispatched": False,
            "real_vehicle_control_dispatched": False,
            "real_vehicle_control": False,
            "service_dispatch_triggered": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "production_ready": False,
        },
        "req_ids": SCENARIO_REQ_IDS,
    }
