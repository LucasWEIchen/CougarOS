#!/usr/bin/env python3
"""Evaluate the Android smoking Agent's 720p compact-first, full-image fallback path."""

from __future__ import annotations

import argparse
import base64
import csv
import hashlib
import io
import json
import statistics
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from PIL import Image

import evaluate_central_brain_smoking_dataset as baseline


FAST_WIDTH = 1280
FAST_HEIGHT = 720
FAST_JPEG_QUALITY = 85
FAST_MAX_TOKENS = 24
FALLBACK_MAX_TOKENS = 64
FAST_ACCEPT_CONFIDENCE = 0.80

CSV_FIELDS = [
    "image",
    "sha256",
    "source_bytes",
    "fast_bytes",
    "classification",
    "contract_valid",
    "smoking_detected",
    "person_count",
    "location",
    "confidence",
    "description",
    "pass_count",
    "fallback_used",
    "thinking_enabled",
    "fast_prompt_tokens",
    "fast_completion_tokens",
    "fallback_prompt_tokens",
    "fallback_completion_tokens",
    "total_prompt_tokens",
    "total_completion_tokens",
    "preprocess_ms",
    "fast_request_ms",
    "fallback_request_ms",
    "end_to_end_ms",
    "failure_code",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--endpoint", default="http://127.0.0.1:10030")
    parser.add_argument("--timeout-seconds", type=float, default=120.0)
    parser.add_argument("--limit", type=int, default=0)
    return parser.parse_args()


def fast_prompt_material(full_system: str, full_user: str) -> tuple[str, str]:
    output_section = full_system.find("## 输出字段")
    output_line = full_user.rfind("\n请只返回")
    if output_section <= 0 or output_line <= 0:
        raise ValueError("smoking Agent output markers are unavailable")
    system = full_system[:output_section].strip() + (
        "\n运行时补充约束：如果输入图像不符合标准后排摄像头几何，"
        "仍可判断直接可见的客观吸烟事实，但座位无法可靠确定时必须使用UNKNOWN。"
        "你没有工具、车辆执行或业务处置权限。"
        "\n## Provider内部传输格式\n"
        "只输出四元素紧凑JSON数组[s,n,l,c]，不得输出其他文字："
        "s为0未吸烟、1吸烟、2不确定；n为吸烟人数0..2；"
        "l为0 UNKNOWN、1 IMAGE_ROW_2_LEFT、2 IMAGE_ROW_2_RIGHT、"
        "3 IMAGE_ROW_1_LEFT、4 IMAGE_ROW_1_RIGHT；"
        "c为整数置信度百分比0..100。"
        "s=2时必须输出[2,0,0,c]且c<50。"
    )
    return system, full_user[:output_line] + "\n只返回四元素JSON数组。"


def compact_schema() -> dict[str, Any]:
    def branch(prefix_items: list[dict[str, Any]]) -> dict[str, Any]:
        return {
            "type": "array",
            "prefixItems": prefix_items,
            "minItems": 4,
            "maxItems": 4,
        }

    return {
        "oneOf": [
            branch(
                [
                    {"const": 0},
                    {"const": 0},
                    {"const": 0},
                    {"type": "integer", "minimum": 50, "maximum": 100},
                ]
            ),
            branch(
                [
                    {"const": 1},
                    {"type": "integer", "minimum": 1, "maximum": 2},
                    {"type": "integer", "minimum": 1, "maximum": 4},
                    {"type": "integer", "minimum": 50, "maximum": 100},
                ]
            ),
            branch(
                [
                    {"const": 2},
                    {"const": 0},
                    {"const": 0},
                    {"type": "integer", "minimum": 0, "maximum": 49},
                ]
            ),
        ]
    }


def request_body(
    image: bytes,
    mime: str,
    system: str,
    user: str,
    schema_name: str,
    schema: dict[str, Any],
    max_tokens: int,
    model: str = baseline.MODEL,
) -> bytes:
    body = {
        "model": model,
        "stream": False,
        "temperature": 0,
        "max_tokens": max_tokens,
        "chat_template_kwargs": {"enable_thinking": False},
        "messages": [
            {"role": "system", "content": system},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": user},
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": "data:"
                            + mime
                            + ";base64,"
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
                "name": schema_name,
                "strict": True,
                "schema": schema,
            },
        },
    }
    return json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode(
        "utf-8"
    )


def prepare_fast_jpeg(source: bytes) -> bytes:
    with Image.open(io.BytesIO(source)) as image:
        image.load()
        image = image.convert("RGB")
        image.thumbnail((FAST_WIDTH, FAST_HEIGHT), Image.Resampling.LANCZOS)
        output = io.BytesIO()
        image.save(output, format="JPEG", quality=FAST_JPEG_QUALITY)
    encoded = output.getvalue()
    if not encoded or len(encoded) > baseline.MAX_IMAGE_BYTES:
        raise ValueError("fast image size is invalid")
    return encoded


def execute_completion(
    endpoint: str,
    timeout: float,
    body: bytes,
    expected_model: str = baseline.MODEL,
) -> tuple[str, dict[str, int], float]:
    started = time.perf_counter_ns()
    request = urllib.request.Request(
        endpoint + "/v1/chat/completions",
        data=body,
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        raw = response.read(baseline.MAX_RESPONSE_BYTES + 1)
        if response.status != 200:
            raise RuntimeError(f"HTTP_{response.status}")
    request_ms = baseline.elapsed_ms(started)
    if len(raw) > baseline.MAX_RESPONSE_BYTES:
        raise ValueError("response exceeds size bound")
    envelope = baseline.strict_json(raw)
    if envelope.get("model") != expected_model:
        raise ValueError("response model mismatch")
    choices = envelope.get("choices")
    if not isinstance(choices, list) or len(choices) != 1:
        raise ValueError("response choice count is invalid")
    choice = choices[0]
    if choice.get("finish_reason") != "stop":
        raise ValueError("response is not terminal")
    message = choice.get("message")
    if not isinstance(message, dict) or message.get("role") != "assistant":
        raise ValueError("response role is invalid")
    content = message.get("content")
    if not isinstance(content, str) or not content:
        raise ValueError("response content is invalid")
    usage: dict[str, int] = {}
    raw_usage = envelope.get("usage") or {}
    for field in ("prompt_tokens", "completion_tokens", "total_tokens"):
        value = raw_usage.get(field, 0)
        if type(value) is not int or value < 0:
            raise ValueError("response usage is invalid")
        usage[field] = value
    return content, usage, request_ms


def compact_result(raw: str) -> tuple[dict[str, Any], str]:
    value = baseline.strict_json(raw)
    if not isinstance(value, list) or len(value) != 4:
        raise ValueError("compact result shape is not exact")
    if any(type(item) is not int for item in value):
        raise ValueError("compact result values must be integers")
    status, count, location_code, confidence_percent = value
    if not 0 <= status <= 2:
        raise ValueError("compact status is invalid")
    if not 0 <= count <= 2 or not 0 <= location_code <= 4:
        raise ValueError("compact count or location is invalid")
    if not 0 <= confidence_percent <= 100:
        raise ValueError("compact confidence is invalid")
    confidence = confidence_percent / 100.0
    locations = (
        "UNKNOWN",
        "IMAGE_ROW_2_LEFT",
        "IMAGE_ROW_2_RIGHT",
        "IMAGE_ROW_1_LEFT",
        "IMAGE_ROW_1_RIGHT",
    )
    if status == 2:
        if count != 0 or location_code != 0 or confidence_percent >= 50:
            raise ValueError("compact uncertain result is not normalized")
        result = {
            "smoking_detected": 0,
            "person_count": 0,
            "location": "UNKNOWN",
            "confidence": confidence,
            "description": "uncertain: 图像不足以可靠判断。",
        }
    elif status == 1:
        if count < 1 or location_code == 0 or confidence_percent < 50:
            raise ValueError("compact positive result is inconsistent")
        result = {
            "smoking_detected": 1,
            "person_count": count,
            "location": locations[location_code],
            "confidence": confidence,
            "description": "检测到吸烟行为。",
        }
    else:
        if count != 0 or location_code != 0 or confidence_percent < 50:
            raise ValueError("compact negative result is inconsistent")
        result = {
            "smoking_detected": 0,
            "person_count": 0,
            "location": "UNKNOWN",
            "confidence": confidence,
            "description": "未检测到吸烟行为。",
        }
    return result, baseline.validate_result(result)


def evaluate_case(
    path: Path,
    endpoint: str,
    timeout: float,
    full_system: str,
    full_user: str,
    fast_system: str,
    fast_user: str,
) -> dict[str, Any]:
    started = time.perf_counter_ns()
    row: dict[str, Any] = {field: "" for field in CSV_FIELDS}
    row.update(
        {
            "image": path.name,
            "contract_valid": False,
            "fallback_used": False,
            "thinking_enabled": False,
            "pass_count": 0,
        }
    )
    try:
        source = path.read_bytes()
        if not source or len(source) > baseline.MAX_IMAGE_BYTES:
            raise ValueError("source image size is invalid")
        source_mime = baseline.image_mime(source)
        row["sha256"] = hashlib.sha256(source).hexdigest()
        row["source_bytes"] = len(source)

        preprocess_started = time.perf_counter_ns()
        fast_image = prepare_fast_jpeg(source)
        row["preprocess_ms"] = baseline.elapsed_ms(preprocess_started)
        row["fast_bytes"] = len(fast_image)

        fast_content, fast_usage, fast_ms = execute_completion(
            endpoint,
            timeout,
            request_body(
                fast_image,
                "image/jpeg",
                fast_system,
                fast_user,
                "central_brain_smoking_wire_v2",
                compact_schema(),
                FAST_MAX_TOKENS,
            ),
        )
        row["pass_count"] = 1
        row["fast_request_ms"] = fast_ms
        row["fast_prompt_tokens"] = fast_usage["prompt_tokens"]
        row["fast_completion_tokens"] = fast_usage["completion_tokens"]
        result, classification = compact_result(fast_content)

        if classification != "detected" or result["confidence"] < FAST_ACCEPT_CONFIDENCE:
            row["fallback_used"] = True
            row["pass_count"] = 2
            fallback_content, fallback_usage, fallback_ms = execute_completion(
                endpoint,
                timeout,
                request_body(
                    source,
                    source_mime,
                    full_system,
                    full_user,
                    "central_brain_smoking_detection_v1",
                    baseline.response_schema(),
                    FALLBACK_MAX_TOKENS,
                ),
            )
            row["fallback_request_ms"] = fallback_ms
            row["fallback_prompt_tokens"] = fallback_usage["prompt_tokens"]
            row["fallback_completion_tokens"] = fallback_usage["completion_tokens"]
            result = baseline.strict_json(fallback_content)
            classification = baseline.validate_result(result)
        else:
            fallback_usage = {"prompt_tokens": 0, "completion_tokens": 0}

        row.update(result)
        row["classification"] = classification
        row["contract_valid"] = True
        row["total_prompt_tokens"] = (
            fast_usage["prompt_tokens"] + fallback_usage["prompt_tokens"]
        )
        row["total_completion_tokens"] = (
            fast_usage["completion_tokens"] + fallback_usage["completion_tokens"]
        )
    except urllib.error.HTTPError as failure:
        row["classification"] = "transport_error"
        row["failure_code"] = f"HTTP_{failure.code}"
    except (urllib.error.URLError, TimeoutError) as failure:
        row["classification"] = "transport_error"
        row["failure_code"] = type(failure).__name__
    except (OSError, RuntimeError, ValueError, KeyError, TypeError) as failure:
        row["classification"] = "contract_error"
        row["failure_code"] = type(failure).__name__
    row["end_to_end_ms"] = baseline.elapsed_ms(started)
    return row


def write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_FIELDS, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def numeric(rows: list[dict[str, Any]], field: str) -> list[float]:
    return [float(row[field]) for row in rows if row[field] != ""]


def summarize(
    rows: list[dict[str, Any]], wall_ms: float, agent_sha256: str
) -> dict[str, Any]:
    counts = {
        name: sum(row["classification"] == name for row in rows)
        for name in (
            "detected",
            "not_detected",
            "uncertain",
            "contract_error",
            "transport_error",
        )
    }
    valid = counts["detected"] + counts["not_detected"] + counts["uncertain"]
    fallback_count = sum(bool(row["fallback_used"]) for row in rows)
    prompt_tokens = numeric(rows, "total_prompt_tokens")
    completion_tokens = numeric(rows, "total_completion_tokens")
    return {
        "schema_version": 1,
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "dataset": {
            "expected_positive_cases": len(rows),
            "executed_cases": len(rows),
            "unique_image_sha256": len({row["sha256"] for row in rows if row["sha256"]}),
            "required_source_resolution": {"width": 1920, "height": 1080},
        },
        "pipeline": {
            "fast_image_maximum": {"width": FAST_WIDTH, "height": FAST_HEIGHT},
            "fast_jpeg_quality": FAST_JPEG_QUALITY,
            "fast_wire": "[status,count,location_code,confidence_percent]",
            "fast_max_tokens": FAST_MAX_TOKENS,
            "fast_accept_confidence": FAST_ACCEPT_CONFIDENCE,
            "fallback_image": "ORIGINAL_1920x1080",
            "fallback_contract": "FIVE_FIELD_V1",
            "fallback_max_tokens": FALLBACK_MAX_TOKENS,
            "thinking_enabled": False,
        },
        "route": {
            "activation_source": baseline.ACTIVATION_SOURCE,
            "triage_agent_id": baseline.TRIAGE_AGENT_ID,
            "specialist_agent_id": baseline.SPECIALIST_AGENT_ID,
            "scenario_id": baseline.SCENARIO_ID,
            "route_digest": baseline.route_digest(),
            "model_selected_route": False,
        },
        "provider": {
            "model": baseline.MODEL,
            "agent_instruction_sha256": agent_sha256,
            "stream": False,
            "temperature": 0,
        },
        "outcomes": {
            **counts,
            "strict_valid_outputs": valid,
            "positive_sample_hit_rate": round(counts["detected"] / len(rows), 6),
            "strict_valid_output_rate": round(valid / len(rows), 6),
            "complete_dataset_accuracy_available": False,
            "metric_note": "positive-only dataset; hit rate equals positive-class recall",
        },
        "fallback": {
            "count": fallback_count,
            "rate": round(fallback_count / len(rows), 6),
        },
        "latency": {
            "preprocess": baseline.distribution(numeric(rows, "preprocess_ms")),
            "fast_request": baseline.distribution(numeric(rows, "fast_request_ms")),
            "fallback_request": baseline.distribution(numeric(rows, "fallback_request_ms")),
            "end_to_end": baseline.distribution(numeric(rows, "end_to_end_ms")),
            "wall_clock_ms": round(wall_ms, 3),
            "images_per_minute": round(len(rows) * 60_000 / wall_ms, 3),
        },
        "tokens": {
            "prompt_mean": round(statistics.fmean(prompt_tokens), 3),
            "prompt_max": int(max(prompt_tokens, default=0)),
            "completion_mean": round(statistics.fmean(completion_tokens), 3),
            "completion_max": int(max(completion_tokens, default=0)),
        },
        "evidence_boundary": {
            "android_bitmap_preprocessor_included": False,
            "android_binder_hmi_included": False,
            "vehicle_bus_accessed": False,
            "production_acceptance": False,
        },
    }


def main() -> int:
    args = parse_args()
    if args.timeout_seconds <= 0:
        raise SystemExit("--timeout-seconds must be positive")
    endpoint = baseline.require_fixed_endpoint(args.endpoint)
    root = Path(__file__).resolve().parents[1]
    full_system, full_user, agent_sha256 = baseline.prompt_material(root)
    fast_system, fast_user = fast_prompt_material(full_system, full_user)
    paths = sorted(args.dataset.glob("*.jpg")) + sorted(args.dataset.glob("*.png"))
    paths = sorted(set(paths), key=lambda path: path.name)
    if args.limit > 0:
        paths = paths[: args.limit]
    if not paths:
        raise SystemExit("dataset has no JPEG or PNG images")
    try:
        baseline.validate_dataset_resolution(paths)
    except (OSError, ValueError) as failure:
        raise SystemExit(str(failure)) from failure

    args.output_dir.mkdir(parents=True, exist_ok=True)
    if any((args.output_dir / name).exists() for name in ("cases.csv", "summary.json")):
        raise SystemExit("output directory already contains evaluation results")
    baseline.preflight(endpoint, min(args.timeout_seconds, 10.0))
    rows: list[dict[str, Any]] = []
    wall_started = time.perf_counter_ns()
    for index, path in enumerate(paths, start=1):
        row = evaluate_case(
            path,
            endpoint,
            args.timeout_seconds,
            full_system,
            full_user,
            fast_system,
            fast_user,
        )
        rows.append(row)
        print(
            f"case={index}/{len(paths)} image={path.name} "
            f"classification={row['classification']} passes={row['pass_count']} "
            f"fallback={row['fallback_used']} end_to_end_ms={row['end_to_end_ms']}",
            flush=True,
        )

    wall_ms = baseline.elapsed_ms(wall_started)
    summary = summarize(rows, wall_ms, agent_sha256)
    write_csv(args.output_dir / "cases.csv", rows)
    (args.output_dir / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
