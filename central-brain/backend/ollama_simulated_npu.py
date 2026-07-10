#!/usr/bin/env python3
"""Ollama-backed simulated NPU runtime for the Central Brain prototype.

This adapter is intentionally a user-space simulation backend. It does not
touch PCIe devices, Driver/HAL, vendor SDKs, DMA, shared memory, Safety Runtime,
or virtualization APIs.
"""

from __future__ import annotations

import json
import os
import time
import uuid
from typing import Any
from urllib import error as urlerror
from urllib import request as urlrequest


REQ_IDS = ["XSC-001", "HW-002", "NV-F-011", "KH-003", "KH-006", "DEL-001", "DEL-002", "DEL-005"]
DEFAULT_BASE_URL = "http://127.0.0.1:11434"
DEFAULT_MODEL = "qwen3.5:27b-optimized"


def selected_backend(request_payload: dict[str, Any] | None = None) -> str:
    payload = request_payload or {}
    requested = str(
        payload.get("runtime")
        or payload.get("simulated_npu_backend")
        or os.environ.get("CENTRAL_BRAIN_SIMULATED_NPU_BACKEND")
        or "mock"
    ).strip().lower()
    if requested in {"ollama", "ollama-simulated-npu", "simulated-ollama", "ollama_simulated_npu"}:
        return "ollama"
    return "mock"


def runtime_name(request_payload: dict[str, Any] | None = None) -> str:
    return "ollama-simulated-npu" if selected_backend(request_payload) == "ollama" else "mock-npu"


def config() -> dict[str, Any]:
    timeout_raw = os.environ.get("CENTRAL_BRAIN_OLLAMA_TIMEOUT_MS", "60000")
    try:
        timeout_ms = max(1000, int(timeout_raw))
    except ValueError:
        timeout_ms = 60000
    return {
        "base_url": os.environ.get("CENTRAL_BRAIN_OLLAMA_URL", DEFAULT_BASE_URL).rstrip("/"),
        "model": os.environ.get("CENTRAL_BRAIN_OLLAMA_MODEL", DEFAULT_MODEL),
        "timeout_ms": timeout_ms,
    }


def _num_predict() -> int:
    raw_value = os.environ.get("CENTRAL_BRAIN_OLLAMA_NUM_PREDICT", "96")
    try:
        return max(1, int(raw_value))
    except ValueError:
        return 96


def _request_json(method: str, path: str, payload: dict[str, Any] | None, timeout_ms: int) -> dict[str, Any]:
    cfg = config()
    data = None
    headers = {"Accept": "application/json"}
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    req = urlrequest.Request(cfg["base_url"] + path, data=data, headers=headers, method=method)
    with urlrequest.urlopen(req, timeout=timeout_ms / 1000) as response:
        return json.loads(response.read().decode("utf-8"))


def status_payload() -> dict[str, Any]:
    cfg = config()
    status: dict[str, Any] = {
        "selected": selected_backend(),
        "runtime": runtime_name(),
        "base_url": cfg["base_url"],
        "model": cfg["model"],
        "reachable": False,
        "available_models": [],
        "selected_model_loaded": False,
        "error": None,
        "production_ready": False,
        "hardware_accessed": False,
        "driver_development_triggered": False,
        "virtualization_development_triggered": False,
        "service_dispatch_triggered": False,
        "req_ids": REQ_IDS,
    }
    if selected_backend() != "ollama":
        status["state"] = "disabled"
        return status

    try:
        tags = _request_json("GET", "/api/tags", None, min(cfg["timeout_ms"], 5000))
    except (OSError, TimeoutError, json.JSONDecodeError, urlerror.URLError) as exc:
        status["state"] = "unreachable"
        status["error"] = {"type": exc.__class__.__name__, "message": str(exc)}
        return status

    models = [str(item.get("name") or item.get("model")) for item in tags.get("models", []) if item]
    status.update(
        {
            "state": "available",
            "reachable": True,
            "available_models": models,
            "selected_model_loaded": cfg["model"] in models,
        }
    )
    if not status["selected_model_loaded"]:
        status["state"] = "model-missing"
        status["error"] = {"type": "ModelNotListed", "message": f"{cfg['model']} not found in Ollama tags"}
    return status


def _prompt(logical_model: str, input_value: Any, policy: dict[str, Any]) -> str:
    return (
        "You are the Central Brain simulated NPU model runtime. "
        "Return a concise vehicle-cockpit assistant inference result. "
        "Do not claim real vehicle, Driver/HAL, or PCIe NPU access.\n\n"
        f"Logical model: {logical_model}\n"
        f"Policy: {json.dumps(policy, ensure_ascii=False, sort_keys=True)}\n"
        f"Input: {json.dumps(input_value, ensure_ascii=False, sort_keys=True)}"
    )


def infer_payload(request_payload: dict[str, Any], started: float | None = None) -> dict[str, Any]:
    cfg = config()
    started_at = started or time.time()
    logical_model = str(request_payload.get("model") or "central-intent-v0")
    input_value = request_payload.get("input", {})
    policy = request_payload.get("policy", {})
    ollama_model = str(request_payload.get("ollama_model") or cfg["model"])

    options = {
        "temperature": 0.2,
        "num_predict": _num_predict(),
    }
    body = {
        "model": ollama_model,
        "prompt": _prompt(logical_model, input_value, policy),
        "stream": False,
        "options": options,
    }

    try:
        response = _request_json("POST", "/api/generate", body, cfg["timeout_ms"])
    except (OSError, TimeoutError, json.JSONDecodeError, urlerror.URLError) as exc:
        inference_ms = (time.time() - started_at) * 1000
        return {
            "request_id": str(uuid.uuid4()),
            "model": logical_model,
            "backend_model": ollama_model,
            "runtime": "ollama-simulated-npu",
            "simulated_npu_backend": "ollama",
            "status": "error",
            "result": {
                "reason": "ollama simulated NPU backend unavailable",
                "error": {"type": exc.__class__.__name__, "message": str(exc)},
                "input_echo": input_value,
            },
            "metrics": {"queue_ms": 0.4, "inference_ms": round(inference_ms, 3)},
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "production_ready": False,
            "req_ids": REQ_IDS,
        }

    inference_ms = (time.time() - started_at) * 1000
    generated_text = str(response.get("response") or "").strip()
    return {
        "request_id": str(uuid.uuid4()),
        "model": logical_model,
        "backend_model": ollama_model,
        "runtime": "ollama-simulated-npu",
        "simulated_npu_backend": "ollama",
        "status": "ok",
        "result": {
            "summary": "ollama simulated NPU inference accepted",
            "generated_text": generated_text,
            "visible_text_available": bool(generated_text),
            "input_echo": input_value,
            "ollama_done": bool(response.get("done")),
        },
        "metrics": {
            "queue_ms": 0.4,
            "inference_ms": round(inference_ms, 3),
            "ollama_total_duration_ns": response.get("total_duration"),
            "ollama_eval_count": response.get("eval_count"),
        },
        "hardware_accessed": False,
        "driver_development_triggered": False,
        "virtualization_development_triggered": False,
        "production_ready": False,
        "req_ids": REQ_IDS,
    }
