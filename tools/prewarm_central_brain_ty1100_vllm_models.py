#!/usr/bin/env python3
"""Verify and prewarm the fixed dual-model TY1100 vLLM prototype runtime."""

from __future__ import annotations

import argparse
import base64
import json
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
GENERAL_ENDPOINT = "http://127.0.0.1:10030"
GENERAL_MODEL = "Qwen3.5-9B-AWQ"
GENERAL_CONTEXT = 8192
SMOKING_ENDPOINT = "http://127.0.0.1:10031"
SMOKING_MODEL = "Qwen3.5-2B-AWQ"
SMOKING_CONTEXT = 4096
SMOKING_IMAGE = ROOT / "central-brain/querySample/违规场景图片/第1组_01.png"
SMOKING_AGENT = ROOT / (
    "central-brain/android-runtime/runtime-service/src/main/assets/agents/"
    "smoking-detection-agent-v1.md"
)
MAX_RESPONSE_BYTES = 512 * 1024


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--passes", type=int, default=2)
    parser.add_argument("--timeout-seconds", type=float, default=120.0)
    parser.add_argument("--output", type=Path)
    return parser.parse_args()


def fetch_json(url: str, timeout: float) -> dict[str, Any]:
    with urllib.request.urlopen(url, timeout=timeout) as response:
        raw = response.read(MAX_RESPONSE_BYTES + 1)
        if response.status != 200 or len(raw) > MAX_RESPONSE_BYTES:
            raise RuntimeError("bounded GET failed")
    value = json.loads(raw.decode("utf-8", errors="strict"))
    if not isinstance(value, dict):
        raise RuntimeError("GET response is not an object")
    return value


def preflight(endpoint: str, model: str, context: int, timeout: float) -> None:
    with urllib.request.urlopen(endpoint + "/health", timeout=timeout) as response:
        if response.status != 200:
            raise RuntimeError(f"{model} health failed")
    catalog = fetch_json(endpoint + "/v1/models", timeout)
    data = catalog.get("data")
    if not isinstance(data, list) or len(data) != 1:
        raise RuntimeError(f"{model} catalog cardinality mismatch")
    item = data[0]
    if not isinstance(item, dict) or item.get("id") != model:
        raise RuntimeError(f"{model} identity mismatch")
    if item.get("max_model_len") != context:
        raise RuntimeError(
            f"{model} context mismatch: {item.get('max_model_len')} != {context}"
        )


def object_schema(scenario_id: str) -> dict[str, Any]:
    return {
        "type": "object",
        "additionalProperties": False,
        "properties": {
            "scenario_id": {"type": "string", "enum": [scenario_id]},
            "reply": {"type": "string", "minLength": 1, "maxLength": 64},
            "actions": {
                "type": "array",
                "minItems": 1,
                "maxItems": 1,
                "items": {"type": "string", "enum": ["assistant.respond"]},
            },
        },
        "required": ["scenario_id", "reply", "actions"],
    }


def compact_smoking_schema() -> dict[str, Any]:
    def branch(
        status: int,
        count_min: int,
        count_max: int,
        location_min: int,
        location_max: int,
        confidence_min: int,
        confidence_max: int,
    ) -> dict[str, Any]:
        return {
            "type": "array",
            "prefixItems": [
                {"const": status},
                {"type": "integer", "minimum": count_min, "maximum": count_max},
                {
                    "type": "integer",
                    "minimum": location_min,
                    "maximum": location_max,
                },
                {
                    "type": "integer",
                    "minimum": confidence_min,
                    "maximum": confidence_max,
                },
            ],
            "minItems": 4,
            "maxItems": 4,
        }

    return {
        "oneOf": [
            branch(0, 0, 0, 0, 0, 50, 100),
            branch(1, 1, 2, 1, 4, 50, 100),
            branch(2, 0, 0, 0, 0, 0, 49),
        ]
    }


def general_body() -> bytes:
    scenario_id = "scene.prototype.prewarm.general.v1"
    value = {
        "model": GENERAL_MODEL,
        "stream": False,
        "temperature": 0,
        "max_tokens": 96,
        "chat_template_kwargs": {"enable_thinking": False},
        "messages": [
            {
                "role": "system",
                "content": (
                    "你运行在汽车座舱AIOS的预热检查中。只返回合同JSON，"
                    "不得调用工具或车辆执行器。"
                ),
            },
            {
                "role": "user",
                "content": "返回预热完成，并使用唯一动作assistant.respond。",
            },
        ],
        "response_format": {
            "type": "json_schema",
            "json_schema": {
                "name": "central_brain_general_prewarm_v1",
                "strict": True,
                "schema": object_schema(scenario_id),
            },
        },
    }
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode()


def smoking_body() -> bytes:
    image = SMOKING_IMAGE.read_bytes()
    if not image.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError("smoking prewarm image is not PNG")
    agent = SMOKING_AGENT.read_text(encoding="utf-8").strip()
    value = {
        "model": SMOKING_MODEL,
        "stream": False,
        "temperature": 0,
        "max_tokens": 24,
        "chat_template_kwargs": {"enable_thinking": False},
        "messages": [
            {
                "role": "system",
                "content": (
                    agent.split("## 输出字段", 1)[0].strip()
                    + "\n这是预热检查。你没有工具、车身执行或业务处置权限。"
                    "只输出四元素JSON数组[s,n,l,c]：s为0未吸烟、1吸烟、2不确定；"
                    "n为人数0..2；l为0 UNKNOWN或1..4图像座位；c为0..100。"
                ),
            },
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": "预热座舱吸烟检测视觉通路，只返回数组。"},
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": "data:image/png;base64,"
                            + base64.b64encode(image).decode("ascii"),
                            "detail": "auto",
                        },
                    },
                ],
            },
        ],
        "response_format": {
            "type": "json_schema",
            "json_schema": {
                "name": "central_brain_smoking_prewarm_v1",
                "strict": True,
                "schema": compact_smoking_schema(),
            },
        },
    }
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode()


def invoke(endpoint: str, model: str, body: bytes, timeout: float) -> float:
    started = time.perf_counter_ns()
    request = urllib.request.Request(
        endpoint + "/v1/chat/completions",
        data=body,
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        raw = response.read(MAX_RESPONSE_BYTES + 1)
        if response.status != 200 or len(raw) > MAX_RESPONSE_BYTES:
            raise RuntimeError(f"{model} prewarm response is invalid")
    envelope = json.loads(raw.decode("utf-8", errors="strict"))
    if envelope.get("model") != model:
        raise RuntimeError(f"{model} prewarm response identity mismatch")
    choices = envelope.get("choices")
    if not isinstance(choices, list) or len(choices) != 1:
        raise RuntimeError(f"{model} prewarm choice count mismatch")
    choice = choices[0]
    if choice.get("finish_reason") != "stop":
        raise RuntimeError(f"{model} prewarm response is not terminal")
    message = choice.get("message")
    if not isinstance(message, dict) or message.get("role") != "assistant":
        raise RuntimeError(f"{model} prewarm role mismatch")
    content = json.loads(message.get("content", ""))
    if model == GENERAL_MODEL:
        if (
            not isinstance(content, dict)
            or content.get("scenario_id") != "scene.prototype.prewarm.general.v1"
            or content.get("actions") != ["assistant.respond"]
        ):
            raise RuntimeError("general prewarm payload mismatch")
    elif not isinstance(content, list) or len(content) != 4:
        raise RuntimeError("smoking prewarm payload mismatch")
    return round((time.perf_counter_ns() - started) / 1_000_000, 3)


def main() -> int:
    args = parse_args()
    if args.passes < 1 or args.passes > 4:
        raise SystemExit("--passes must be within 1..4")
    if args.timeout_seconds <= 0:
        raise SystemExit("--timeout-seconds must be positive")
    preflight(GENERAL_ENDPOINT, GENERAL_MODEL, GENERAL_CONTEXT, 10.0)
    preflight(SMOKING_ENDPOINT, SMOKING_MODEL, SMOKING_CONTEXT, 10.0)
    general = general_body()
    smoking = smoking_body()
    passes: list[dict[str, Any]] = []
    for index in range(1, args.passes + 1):
        passes.append(
            {
                "pass": index,
                "general_9b_ms": invoke(
                    GENERAL_ENDPOINT, GENERAL_MODEL, general, args.timeout_seconds
                ),
                "smoking_2b_ms": invoke(
                    SMOKING_ENDPOINT, SMOKING_MODEL, smoking, args.timeout_seconds
                ),
            }
        )
    result = {
        "schema_version": 1,
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "profile": "ty1100-routed-dual-model-v1",
        "targets": [
            {
                "route": "GENERAL_COCKPIT",
                "model": GENERAL_MODEL,
                "context_tokens": GENERAL_CONTEXT,
                "endpoint": GENERAL_ENDPOINT,
            },
            {
                "route": "CABIN_SMOKING_COMPLIANCE",
                "model": SMOKING_MODEL,
                "context_tokens": SMOKING_CONTEXT,
                "endpoint": SMOKING_ENDPOINT,
            },
        ],
        "thinking_enabled": False,
        "both_models_resident_and_ready": True,
        "passes": passes,
        "evidence_boundary": {
            "android_runtime_included": False,
            "production_ready": False,
            "target_hardware_validated": False,
        },
    }
    encoded = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded, encoding="utf-8")
    print(encoded, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
