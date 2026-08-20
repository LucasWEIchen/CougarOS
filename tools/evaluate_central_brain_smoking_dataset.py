#!/usr/bin/env python3
"""Evaluate the explicit smoking-specialist route against a positive image set."""

from __future__ import annotations

import argparse
import base64
import csv
import hashlib
import json
import math
import statistics
import struct
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


MODEL = "Qwen3.5-9B-AWQ"
SCENARIO_ID = "scene.cabin.compliance.smoking.v1"
TRIAGE_AGENT_ID = "agent.cabin.compliance-triage.v1"
SPECIALIST_AGENT_ID = "agent.cabin.smoking-detection.v1"
ACTIVATION_SOURCE = "EXPLICIT_SCENARIO"
MAX_IMAGE_BYTES = 6 * 1024 * 1024
MAX_RESPONSE_BYTES = 512 * 1024
DEFAULT_MAX_TOKENS = 64
EXPECTED_IMAGE_WIDTH = 1920
EXPECTED_IMAGE_HEIGHT = 1080
LOCATIONS = (
    "IMAGE_ROW_2_LEFT",
    "IMAGE_ROW_2_RIGHT",
    "IMAGE_ROW_1_LEFT",
    "IMAGE_ROW_1_RIGHT",
    "UNKNOWN",
)
DESCRIPTIONS = (
    "检测到吸烟行为。",
    "未检测到吸烟行为。",
    "uncertain: 图像不足以可靠判断。",
)
FIELDS = {
    "smoking_detected",
    "person_count",
    "location",
    "confidence",
    "description",
}
CSV_FIELDS = [
    "image",
    "sha256",
    "bytes",
    "classification",
    "contract_valid",
    "smoking_detected",
    "person_count",
    "location",
    "confidence",
    "description",
    "finish_reason",
    "prompt_tokens",
    "completion_tokens",
    "total_tokens",
    "encode_ms",
    "request_ms",
    "validate_ms",
    "end_to_end_ms",
    "failure_code",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--endpoint", default="http://127.0.0.1:10030")
    parser.add_argument("--max-tokens", type=int, default=DEFAULT_MAX_TOKENS)
    parser.add_argument("--timeout-seconds", type=float, default=120.0)
    parser.add_argument("--limit", type=int, default=0)
    return parser.parse_args()


def require_fixed_endpoint(value: str) -> str:
    parsed = urllib.parse.urlparse(value)
    if (parsed.scheme, parsed.hostname, parsed.port, parsed.path.rstrip("/")) != (
        "http",
        "127.0.0.1",
        10030,
        "",
    ):
        raise ValueError("endpoint must be the fixed TY1100 WSL bridge")
    return value.rstrip("/")


def reject_constant(value: str) -> None:
    raise ValueError(f"non-finite JSON number: {value}")


def reject_duplicate_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON field: {key}")
        result[key] = value
    return result


def strict_json(raw: bytes | str) -> Any:
    if isinstance(raw, bytes):
        raw = raw.decode("utf-8", errors="strict")
    return json.loads(
        raw,
        object_pairs_hook=reject_duplicate_pairs,
        parse_constant=reject_constant,
    )


def response_schema() -> dict[str, Any]:
    return {
        "type": "object",
        "additionalProperties": False,
        "properties": {
            "smoking_detected": {"type": "integer", "minimum": 0, "maximum": 1},
            "person_count": {"type": "integer", "minimum": 0, "maximum": 2},
            "location": {"type": "string", "enum": list(LOCATIONS)},
            "confidence": {"type": "number", "minimum": 0, "maximum": 1},
            "description": {"type": "string", "enum": list(DESCRIPTIONS)},
        },
        "required": [
            "smoking_detected",
            "person_count",
            "location",
            "confidence",
            "description",
        ],
    }


def route_digest() -> str:
    digest = hashlib.sha256()
    for value in (
        "central-brain-cabin-agent-route-v1",
        ACTIVATION_SOURCE,
        TRIAGE_AGENT_ID,
        SPECIALIST_AGENT_ID,
        SCENARIO_ID,
    ):
        encoded = value.encode("utf-8")
        digest.update(struct.pack(">i", len(encoded)))
        digest.update(encoded)
    return digest.hexdigest()


def prompt_material(root: Path) -> tuple[str, str, str]:
    agent_path = root / (
        "central-brain/android-runtime/runtime-service/src/main/assets/agents/"
        "smoking-detection-agent-v1.md"
    )
    agent = agent_path.read_text(encoding="utf-8").strip()
    system = agent + (
        "\n运行时补充约束：如果输入图像不符合标准后排摄像头几何，"
        "仍可判断直接可见的客观吸烟事实，但座位无法可靠确定时必须使用UNKNOWN。"
        "你没有工具、车辆执行或业务处置权限。"
    )
    user = (
        "触发文本：检测吸烟\n"
        f"场景ID：{SCENARIO_ID}\n"
        f"专用Agent：{SPECIALIST_AGENT_ID}\n"
        "运行上下文：environment=ROBOTAXI_CABIN;input_mode=TEXT_AND_IMAGE;"
        "camera_contract=REAR_ROW_STANDARD_WITH_FRONT_ROW_EXTENSION;"
        "location_policy=IMAGE_COORDINATES_ONLY;"
        "view_mismatch_policy=USE_UNKNOWN_LOCATION;"
        "execution_policy=DETECTION_ONLY;vehicle_bus=NOT_AUTHORIZED\n"
        "请只返回该Agent规定的五字段紧凑JSON。"
    )
    return system, user, hashlib.sha256(agent.encode("utf-8")).hexdigest()


def image_mime(content: bytes) -> str:
    if content.startswith(b"\xff\xd8\xff"):
        return "image/jpeg"
    if content.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"
    raise ValueError("image signature is not PNG or JPEG")


def image_size(content: bytes, mime: str) -> tuple[int, int]:
    if mime == "image/png":
        if len(content) < 24 or content[12:16] != b"IHDR":
            raise ValueError("PNG header is invalid")
        return struct.unpack(">II", content[16:24])

    offset = 2
    start_of_frame = {
        0xC0,
        0xC1,
        0xC2,
        0xC3,
        0xC5,
        0xC6,
        0xC7,
        0xC9,
        0xCA,
        0xCB,
        0xCD,
        0xCE,
        0xCF,
    }
    while offset + 8 < len(content):
        if content[offset] != 0xFF:
            raise ValueError("JPEG marker is invalid")
        while offset < len(content) and content[offset] == 0xFF:
            offset += 1
        marker = content[offset]
        offset += 1
        if marker in start_of_frame:
            height, width = struct.unpack(">HH", content[offset + 3 : offset + 7])
            return width, height
        if marker in {0x01, *range(0xD0, 0xDA)}:
            continue
        if offset + 2 > len(content):
            break
        segment_length = struct.unpack(">H", content[offset : offset + 2])[0]
        if segment_length < 2 or offset + segment_length > len(content):
            raise ValueError("JPEG segment length is invalid")
        offset += segment_length
    raise ValueError("JPEG dimensions are unavailable")


def validate_dataset_resolution(paths: list[Path]) -> None:
    invalid: list[str] = []
    for path in paths:
        content = path.read_bytes()
        mime = image_mime(content)
        width, height = image_size(content, mime)
        if (width, height) != (EXPECTED_IMAGE_WIDTH, EXPECTED_IMAGE_HEIGHT):
            invalid.append(f"{path.name}={width}x{height}")
    if invalid:
        raise ValueError(
            "dataset images must be exact 1920x1080: " + ", ".join(invalid)
        )


def validate_result(value: Any) -> str:
    if not isinstance(value, dict) or set(value) != FIELDS:
        raise ValueError("five-field payload shape is not exact")
    smoking = value["smoking_detected"]
    count = value["person_count"]
    location = value["location"]
    confidence = value["confidence"]
    description = value["description"]
    if type(smoking) is not int or smoking not in (0, 1):
        raise ValueError("smoking_detected is invalid")
    if type(count) is not int or not 0 <= count <= 2:
        raise ValueError("person_count is invalid")
    if location not in LOCATIONS:
        raise ValueError("location is invalid")
    if type(confidence) not in (int, float) or not math.isfinite(confidence):
        raise ValueError("confidence is not finite")
    if not 0 <= confidence <= 1:
        raise ValueError("confidence is outside 0..1")
    if description not in DESCRIPTIONS:
        raise ValueError("description is not allowlisted")
    if confidence < 0.5:
        if (smoking, count, location, description) != (
            0,
            0,
            "UNKNOWN",
            "uncertain: 图像不足以可靠判断。",
        ):
            raise ValueError("uncertain result is not normalized")
        return "uncertain"
    if smoking == 1:
        if count < 1 or location == "UNKNOWN" or description != "检测到吸烟行为。":
            raise ValueError("positive result is inconsistent")
        return "detected"
    if count != 0 or location != "UNKNOWN" or description != "未检测到吸烟行为。":
        raise ValueError("negative result is inconsistent")
    return "not_detected"


def request_body(
    image: bytes,
    mime: str,
    system: str,
    user: str,
    max_tokens: int,
) -> bytes:
    body = {
        "model": MODEL,
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
                "name": "central_brain_smoking_detection_v1",
                "strict": True,
                "schema": response_schema(),
            },
        },
    }
    return json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def preflight(endpoint: str, timeout: float) -> None:
    with urllib.request.urlopen(endpoint + "/health", timeout=timeout) as response:
        if response.status != 200:
            raise RuntimeError("vLLM health check failed")
    with urllib.request.urlopen(endpoint + "/v1/models", timeout=timeout) as response:
        catalog = strict_json(response.read(MAX_RESPONSE_BYTES + 1))
    models = [item.get("id") for item in catalog.get("data", [])]
    if models != [MODEL]:
        raise RuntimeError(f"unexpected model catalog: {models}")


def evaluate_case(
    path: Path,
    endpoint: str,
    timeout: float,
    max_tokens: int,
    system: str,
    user: str,
) -> dict[str, Any]:
    started = time.perf_counter_ns()
    row: dict[str, Any] = {field: "" for field in CSV_FIELDS}
    row.update({"image": path.name, "contract_valid": False})
    try:
        encode_started = time.perf_counter_ns()
        image = path.read_bytes()
        if not image or len(image) > MAX_IMAGE_BYTES:
            raise ValueError("image size is invalid")
        mime = image_mime(image)
        row["sha256"] = hashlib.sha256(image).hexdigest()
        row["bytes"] = len(image)
        body = request_body(image, mime, system, user, max_tokens)
        row["encode_ms"] = elapsed_ms(encode_started)

        request_started = time.perf_counter_ns()
        request = urllib.request.Request(
            endpoint + "/v1/chat/completions",
            data=body,
            headers={"Content-Type": "application/json"},
        )
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read(MAX_RESPONSE_BYTES + 1)
            if len(raw) > MAX_RESPONSE_BYTES:
                raise ValueError("response exceeds size bound")
            if response.status != 200:
                raise RuntimeError(f"HTTP_{response.status}")
        row["request_ms"] = elapsed_ms(request_started)

        validate_started = time.perf_counter_ns()
        envelope = strict_json(raw)
        if envelope.get("model") != MODEL:
            raise ValueError("response model mismatch")
        choices = envelope.get("choices")
        if not isinstance(choices, list) or len(choices) != 1:
            raise ValueError("response choice count is invalid")
        choice = choices[0]
        row["finish_reason"] = choice.get("finish_reason", "")
        if row["finish_reason"] != "stop":
            raise ValueError("response is not terminal")
        message = choice.get("message")
        if not isinstance(message, dict) or message.get("role") != "assistant":
            raise ValueError("response role is invalid")
        result = strict_json(message.get("content", ""))
        classification = validate_result(result)
        usage = envelope.get("usage") or {}
        for field in ("prompt_tokens", "completion_tokens", "total_tokens"):
            value = usage.get(field, "")
            row[field] = value if type(value) is int and value >= 0 else ""
        row.update(result)
        row["classification"] = classification
        row["contract_valid"] = True
        row["validate_ms"] = elapsed_ms(validate_started)
    except urllib.error.HTTPError as failure:
        row["classification"] = "transport_error"
        row["failure_code"] = f"HTTP_{failure.code}"
    except (urllib.error.URLError, TimeoutError) as failure:
        row["classification"] = "transport_error"
        row["failure_code"] = type(failure).__name__
    except (OSError, RuntimeError, ValueError, KeyError, TypeError) as failure:
        row["classification"] = "contract_error"
        row["failure_code"] = type(failure).__name__
    row["end_to_end_ms"] = elapsed_ms(started)
    return row


def elapsed_ms(started_ns: int) -> float:
    return round((time.perf_counter_ns() - started_ns) / 1_000_000, 3)


def percentile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    position = (len(ordered) - 1) * fraction
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)


def distribution(values: list[float]) -> dict[str, float | int]:
    if not values:
        return {"count": 0}
    return {
        "count": len(values),
        "min_ms": round(min(values), 3),
        "mean_ms": round(statistics.fmean(values), 3),
        "p50_ms": round(percentile(values, 0.50), 3),
        "p90_ms": round(percentile(values, 0.90), 3),
        "p95_ms": round(percentile(values, 0.95), 3),
        "p99_ms": round(percentile(values, 0.99), 3),
        "max_ms": round(max(values), 3),
    }


def write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_FIELDS)
        writer.writeheader()
        writer.writerows(rows)


def summarize(
    rows: list[dict[str, Any]],
    expected: int,
    wall_ms: float,
    max_tokens: int,
    agent_sha256: str,
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
    request_times = [float(row["request_ms"]) for row in rows if row["request_ms"] != ""]
    end_to_end_times = [float(row["end_to_end_ms"]) for row in rows]
    completion_tokens = [
        int(row["completion_tokens"])
        for row in rows
        if row["completion_tokens"] != ""
    ]
    return {
        "schema_version": 1,
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "dataset": {
            "expected_positive_cases": expected,
            "executed_cases": len(rows),
            "unique_image_sha256": len({row["sha256"] for row in rows if row["sha256"]}),
            "required_resolution": {
                "width": EXPECTED_IMAGE_WIDTH,
                "height": EXPECTED_IMAGE_HEIGHT,
            },
        },
        "route": {
            "activation_source": ACTIVATION_SOURCE,
            "triage_agent_id": TRIAGE_AGENT_ID,
            "specialist_agent_id": SPECIALIST_AGENT_ID,
            "scenario_id": SCENARIO_ID,
            "route_digest": route_digest(),
            "model_selected_route": False,
        },
        "provider": {
            "model": MODEL,
            "max_tokens": max_tokens,
            "agent_instruction_sha256": agent_sha256,
            "stream": False,
            "temperature": 0,
            "thinking_enabled": False,
        },
        "outcomes": {
            **counts,
            "strict_valid_outputs": valid,
            "positive_sample_hit_rate": round(counts["detected"] / expected, 6),
            "strict_valid_output_rate": round(valid / expected, 6),
            "complete_dataset_accuracy_available": False,
            "metric_note": "positive-only dataset; hit rate equals positive-class recall",
        },
        "latency": {
            "request": distribution(request_times),
            "end_to_end": distribution(end_to_end_times),
            "wall_clock_ms": round(wall_ms, 3),
            "images_per_minute": round(len(rows) * 60_000 / wall_ms, 3),
        },
        "tokens": {
            "completion_count": len(completion_tokens),
            "completion_mean": round(statistics.fmean(completion_tokens), 3)
            if completion_tokens
            else 0,
            "completion_max": max(completion_tokens, default=0),
            "truncated_response_count": sum(
                row["finish_reason"] not in ("", "stop") for row in rows
            ),
        },
        "evidence_boundary": {
            "android_binder_hmi_included": False,
            "vehicle_bus_accessed": False,
            "production_acceptance": False,
        },
    }


def main() -> int:
    args = parse_args()
    if not 1 <= args.max_tokens <= 192:
        raise SystemExit("--max-tokens must be within 1..192")
    if args.timeout_seconds <= 0:
        raise SystemExit("--timeout-seconds must be positive")
    endpoint = require_fixed_endpoint(args.endpoint)
    root = Path(__file__).resolve().parents[1]
    system, user, agent_sha256 = prompt_material(root)
    paths = sorted(args.dataset.glob("*.jpg")) + sorted(args.dataset.glob("*.png"))
    paths = sorted(set(paths), key=lambda path: path.name)
    if args.limit > 0:
        paths = paths[: args.limit]
    if not paths:
        raise SystemExit("dataset has no JPEG or PNG images")
    try:
        validate_dataset_resolution(paths)
    except (OSError, ValueError) as failure:
        raise SystemExit(str(failure)) from failure

    args.output_dir.mkdir(parents=True, exist_ok=True)
    preflight(endpoint, min(args.timeout_seconds, 10.0))
    rows: list[dict[str, Any]] = []
    wall_started = time.perf_counter_ns()
    for index, path in enumerate(paths, start=1):
        row = evaluate_case(
            path,
            endpoint,
            args.timeout_seconds,
            args.max_tokens,
            system,
            user,
        )
        rows.append(row)
        with (args.output_dir / "cases.jsonl").open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")
        print(
            f"case={index}/{len(paths)} image={path.name} "
            f"classification={row['classification']} "
            f"request_ms={row['request_ms']} end_to_end_ms={row['end_to_end_ms']}",
            flush=True,
        )

    wall_ms = elapsed_ms(wall_started)
    summary = summarize(
        rows,
        len(paths),
        wall_ms,
        args.max_tokens,
        agent_sha256,
    )
    write_csv(args.output_dir / "cases.csv", rows)
    (args.output_dir / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
