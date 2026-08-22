#!/usr/bin/env python3
"""Evaluate and pilot-calibrate smoking decision confidence on paired data."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import statistics
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

import evaluate_central_brain_smoking_dataset as baseline
import evaluate_central_brain_smoking_fast_fallback as fast


TOP_LOGPROBS = 5
MODEL_PROFILES = {
    "qwen35-9b-awq": {
        "model": "Qwen3.5-9B-AWQ",
        "endpoint": "http://127.0.0.1:10030",
        "port": 10030,
        "endpoint_class": "FIXED_TY1100_WSL_BRIDGE_9B",
    },
    "qwen35-2b-awq": {
        "model": "Qwen3.5-2B-AWQ",
        "endpoint": "http://127.0.0.1:10031",
        "port": 10031,
        "endpoint_class": "FIXED_TY1100_WSL_BRIDGE_2B",
    },
}
FEATURE_NAMES = (
    "self_confidence_logit",
    "selected_status_logprob_margin",
    "status_is_positive",
)
CASE_FIELDS = (
    "sample_id",
    "group_id",
    "split",
    "label",
    "dataset",
    "image",
    "sha256",
    "status",
    "classification",
    "predicted_label",
    "abstained",
    "decision_correct",
    "self_confidence",
    "status_probability_0",
    "status_probability_1",
    "status_probability_2",
    "selected_status_probability",
    "selected_status_logprob_margin",
    "calibrated_decision_confidence",
    "prompt_tokens",
    "completion_tokens",
    "preprocess_ms",
    "request_ms",
    "end_to_end_ms",
    "failure_code",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--positive-dir", type=Path, required=True)
    parser.add_argument("--negative-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument(
        "--model-profile",
        choices=tuple(MODEL_PROFILES),
        default="qwen35-9b-awq",
    )
    parser.add_argument("--endpoint")
    parser.add_argument("--timeout-seconds", type=float, default=120.0)
    parser.add_argument("--limit", type=int, default=0)
    return parser.parse_args()


def resolve_model_profile(name: str, endpoint: str | None = None) -> dict[str, Any]:
    profile = dict(MODEL_PROFILES[name])
    profile["endpoint"] = baseline.require_fixed_endpoint(
        endpoint or str(profile["endpoint"]), int(profile["port"])
    )
    return profile


def strict_float(value: Any, label: str) -> float:
    if type(value) not in (float, int):
        raise ValueError(f"{label} must be numeric")
    result = float(value)
    if not math.isfinite(result):
        raise ValueError(f"{label} must be finite")
    return result


def load_manifest(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        required = {
            "sample_id",
            "group_id",
            "split",
            "label",
            "dataset",
            "image",
            "sha256",
            "width",
            "height",
        }
        if reader.fieldnames is None or not required.issubset(reader.fieldnames):
            raise ValueError("manifest fields are incomplete")
        rows = list(reader)
    if not rows:
        raise ValueError("manifest has no samples")
    sample_ids = [row["sample_id"] for row in rows]
    if len(sample_ids) != len(set(sample_ids)):
        raise ValueError("manifest sample IDs are not unique")
    for row in rows:
        if row["split"] not in ("calibration", "test"):
            raise ValueError("manifest split is invalid")
        if row["label"] not in ("0", "1"):
            raise ValueError("manifest label is invalid")
        if (row["width"], row["height"]) != ("1920", "1080"):
            raise ValueError("manifest image resolution is invalid")
    group_splits: dict[str, set[str]] = {}
    for row in rows:
        group_splits.setdefault(row["group_id"], set()).add(row["split"])
    if any(len(splits) != 1 for splits in group_splits.values()):
        raise ValueError("paired group members leak across splits")
    return rows


def resolve_image(
    row: dict[str, str], positive_dir: Path, negative_dir: Path
) -> Path:
    directory = positive_dir if row["label"] == "1" else negative_dir
    path = directory / row["image"]
    if not path.is_file() or path.name != row["image"]:
        raise ValueError(f"manifest image is unavailable: {row['sample_id']}")
    content = path.read_bytes()
    if hashlib.sha256(content).hexdigest() != row["sha256"]:
        raise ValueError(f"manifest image hash mismatch: {row['sample_id']}")
    mime = baseline.image_mime(content)
    if baseline.image_size(content, mime) != (1920, 1080):
        raise ValueError(f"manifest image resolution mismatch: {row['sample_id']}")
    return path


def request_with_logprobs(body: bytes) -> bytes:
    value = baseline.strict_json(body)
    if not isinstance(value, dict):
        raise ValueError("request body is invalid")
    value["logprobs"] = True
    value["top_logprobs"] = TOP_LOGPROBS
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def execute_completion(
    endpoint: str, timeout: float, body: bytes, expected_model: str
) -> tuple[str, list[dict[str, Any]], dict[str, int], float]:
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
    if not isinstance(envelope, dict) or envelope.get("model") != expected_model:
        raise ValueError("response model mismatch")
    choices = envelope.get("choices")
    if not isinstance(choices, list) or len(choices) != 1:
        raise ValueError("response choice count is invalid")
    choice = choices[0]
    if not isinstance(choice, dict) or choice.get("finish_reason") != "stop":
        raise ValueError("response is not terminal")
    message = choice.get("message")
    if not isinstance(message, dict) or message.get("role") != "assistant":
        raise ValueError("response role is invalid")
    content = message.get("content")
    if not isinstance(content, str) or not content:
        raise ValueError("response content is invalid")
    logprobs = choice.get("logprobs")
    if not isinstance(logprobs, dict) or not isinstance(logprobs.get("content"), list):
        raise ValueError("response token logprobs are unavailable")
    logprob_items = logprobs["content"]
    if not logprob_items or len(logprob_items) > fast.FAST_MAX_TOKENS:
        raise ValueError("response token logprobs length is invalid")
    usage: dict[str, int] = {}
    raw_usage = envelope.get("usage") or {}
    for field in ("prompt_tokens", "completion_tokens", "total_tokens"):
        value = raw_usage.get(field, 0)
        if type(value) is not int or value < 0:
            raise ValueError("response usage is invalid")
        usage[field] = value
    return content, logprob_items, usage, request_ms


def status_probabilities(
    content: str, logprob_items: list[dict[str, Any]], expected_status: int
) -> tuple[dict[int, float], float]:
    prefix = content.lstrip()
    leading = len(content) - len(prefix)
    if len(prefix) < 2 or prefix[0] != "[" or prefix[1] not in "012":
        raise ValueError("compact status character is unavailable")
    status_offset = leading + 1

    tokens: list[str] = []
    selected_item: dict[str, Any] | None = None
    offset = 0
    for item in logprob_items:
        if not isinstance(item, dict) or not isinstance(item.get("token"), str):
            raise ValueError("token logprob item is invalid")
        token = item["token"]
        tokens.append(token)
        end = offset + len(token)
        if offset <= status_offset < end:
            selected_item = item
        offset = end
    if tokens and tokens[-1] == "<|im_end|>":
        tokens.pop()
    if "".join(tokens) != content:
        raise ValueError("token logprobs do not reconstruct response content")
    if selected_item is None or selected_item["token"].strip() != str(expected_status):
        raise ValueError("selected status token is inconsistent")

    alternatives = selected_item.get("top_logprobs")
    if not isinstance(alternatives, list):
        raise ValueError("status alternatives are unavailable")
    by_status: dict[int, float] = {}
    for alternative in alternatives:
        if not isinstance(alternative, dict) or not isinstance(alternative.get("token"), str):
            raise ValueError("status alternative is invalid")
        token = alternative["token"].strip()
        if token in ("0", "1", "2"):
            status = int(token)
            if status in by_status:
                raise ValueError("duplicate status alternative")
            logprob = strict_float(alternative.get("logprob"), "status logprob")
            if logprob <= -100:
                raise ValueError("status alternative is numerically unavailable")
            by_status[status] = logprob
    if set(by_status) != {0, 1, 2}:
        raise ValueError("all status alternatives must be present")
    maximum = max(by_status.values())
    denominator = sum(math.exp(value - maximum) for value in by_status.values())
    probabilities = {
        status: math.exp(value - maximum) / denominator
        for status, value in by_status.items()
    }
    selected_logprob = by_status[expected_status]
    strongest_other = max(
        value for status, value in by_status.items() if status != expected_status
    )
    return probabilities, selected_logprob - strongest_other


def clamp_probability(value: float) -> float:
    return min(max(value, 1e-6), 1.0 - 1e-6)


def logit(value: float) -> float:
    probability = clamp_probability(value)
    return math.log(probability / (1.0 - probability))


def features(row: dict[str, Any]) -> list[float]:
    return [
        logit(float(row["self_confidence"])),
        float(row["selected_status_logprob_margin"]),
        1.0 if int(row["status"]) == 1 else 0.0,
    ]


def sigmoid(value: float) -> float:
    if value >= 0:
        inverse = math.exp(-value)
        return 1.0 / (1.0 + inverse)
    exponent = math.exp(value)
    return exponent / (1.0 + exponent)


def solve_linear(matrix: list[list[float]], vector: list[float]) -> list[float]:
    size = len(vector)
    augmented = [matrix[index][:] + [vector[index]] for index in range(size)]
    for column in range(size):
        pivot = max(range(column, size), key=lambda row: abs(augmented[row][column]))
        if abs(augmented[pivot][column]) < 1e-12:
            raise ValueError("calibrator Hessian is singular")
        augmented[column], augmented[pivot] = augmented[pivot], augmented[column]
        divisor = augmented[column][column]
        augmented[column] = [value / divisor for value in augmented[column]]
        for row in range(size):
            if row == column:
                continue
            factor = augmented[row][column]
            augmented[row] = [
                current - factor * pivot_value
                for current, pivot_value in zip(augmented[row], augmented[column])
            ]
    return [augmented[row][-1] for row in range(size)]


def fit_calibrator(
    rows: list[dict[str, Any]], l2: float = 1.0, max_iterations: int = 100
) -> dict[str, Any]:
    training = [row for row in rows if row["split"] == "calibration" and not row["abstained"]]
    outcomes = [int(row["decision_correct"]) for row in training]
    if len(training) < 30 or set(outcomes) != {0, 1}:
        raise ValueError("calibration split needs at least 30 terminal decisions and both outcomes")
    raw_features = [features(row) for row in training]
    means = [statistics.fmean(column) for column in zip(*raw_features)]
    scales = []
    for index, mean in enumerate(means):
        variance = statistics.fmean(
            (row[index] - mean) ** 2 for row in raw_features
        )
        scales.append(max(math.sqrt(variance), 1e-6))
    design = [
        [1.0]
        + [
            (value - means[index]) / scales[index]
            for index, value in enumerate(row)
        ]
        for row in raw_features
    ]
    coefficients = [0.0] * len(design[0])
    converged = False
    iterations = 0
    for iterations in range(1, max_iterations + 1):
        gradient = [0.0] * len(coefficients)
        hessian = [[0.0] * len(coefficients) for _ in coefficients]
        for row, outcome in zip(design, outcomes):
            probability = sigmoid(sum(c * x for c, x in zip(coefficients, row)))
            weight = max(probability * (1.0 - probability), 1e-9)
            for left in range(len(coefficients)):
                gradient[left] += (probability - outcome) * row[left]
                for right in range(len(coefficients)):
                    hessian[left][right] += weight * row[left] * row[right]
        for index in range(1, len(coefficients)):
            gradient[index] += l2 * coefficients[index]
            hessian[index][index] += l2
        delta = solve_linear(hessian, gradient)
        coefficients = [value - change for value, change in zip(coefficients, delta)]
        if max(abs(change) for change in delta) < 1e-8:
            converged = True
            break
    if not converged:
        raise ValueError("calibrator did not converge")
    return {
        "type": "L2_REGULARIZED_LOGISTIC_REGRESSION",
        "target": "P_AUTOMATIC_DECISION_CORRECT",
        "feature_names": list(FEATURE_NAMES),
        "means": means,
        "scales": scales,
        "coefficients": coefficients,
        "l2": l2,
        "iterations": iterations,
        "training_terminal_decisions": len(training),
        "training_correct": sum(outcomes),
        "training_incorrect": len(outcomes) - sum(outcomes),
    }


def predict_calibrated(row: dict[str, Any], calibrator: dict[str, Any]) -> float:
    values = features(row)
    standardized = [
        (value - calibrator["means"][index]) / calibrator["scales"][index]
        for index, value in enumerate(values)
    ]
    linear = calibrator["coefficients"][0] + sum(
        coefficient * value
        for coefficient, value in zip(calibrator["coefficients"][1:], standardized)
    )
    return sigmoid(linear)


def ece(probabilities: list[float], outcomes: list[int], bins: int = 10) -> float:
    total = len(outcomes)
    result = 0.0
    for index in range(bins):
        lower = index / bins
        upper = (index + 1) / bins
        members = [
            member
            for member, probability in enumerate(probabilities)
            if lower <= probability < upper or (index == bins - 1 and probability == 1.0)
        ]
        if not members:
            continue
        confidence = statistics.fmean(probabilities[member] for member in members)
        accuracy = statistics.fmean(outcomes[member] for member in members)
        result += len(members) / total * abs(accuracy - confidence)
    return result


def auroc(probabilities: list[float], outcomes: list[int]) -> float | None:
    positives = sum(outcomes)
    negatives = len(outcomes) - positives
    if positives == 0 or negatives == 0:
        return None
    wins = 0.0
    for left, outcome in enumerate(outcomes):
        if outcome != 1:
            continue
        for right, other_outcome in enumerate(outcomes):
            if other_outcome != 0:
                continue
            if probabilities[left] > probabilities[right]:
                wins += 1.0
            elif probabilities[left] == probabilities[right]:
                wins += 0.5
    return wins / (positives * negatives)


def calibration_metrics(rows: list[dict[str, Any]], field: str) -> dict[str, Any]:
    terminal = [row for row in rows if not row["abstained"] and row[field] != ""]
    probabilities = [float(row[field]) for row in terminal]
    outcomes = [int(row["decision_correct"]) for row in terminal]
    if not outcomes:
        has_terminal = any(not row["abstained"] for row in rows)
        return {
            "available": False,
            "reason": "NO_CONFIDENCE_VALUES" if has_terminal else "NO_TERMINAL_DECISIONS",
        }
    return {
        "available": True,
        "count": len(outcomes),
        "correct": sum(outcomes),
        "incorrect": len(outcomes) - sum(outcomes),
        "brier": round(
            statistics.fmean((probability - outcome) ** 2 for probability, outcome in zip(probabilities, outcomes)),
            6,
        ),
        "nll": round(
            statistics.fmean(
                -(
                    outcome * math.log(clamp_probability(probability))
                    + (1 - outcome) * math.log(1 - clamp_probability(probability))
                )
                for probability, outcome in zip(probabilities, outcomes)
            ),
            6,
        ),
        "ece_10_bin": round(ece(probabilities, outcomes), 6),
        "auroc": None if auroc(probabilities, outcomes) is None else round(auroc(probabilities, outcomes) or 0.0, 6),
    }


def classification_metrics(rows: list[dict[str, Any]]) -> dict[str, Any]:
    valid = [row for row in rows if row["failure_code"] == ""]
    tp = sum(row["label"] == 1 and row["predicted_label"] == 1 for row in valid)
    tn = sum(row["label"] == 0 and row["predicted_label"] == 0 for row in valid)
    fp = sum(row["label"] == 0 and row["predicted_label"] == 1 for row in valid)
    fn = sum(row["label"] == 1 and row["predicted_label"] == 0 for row in valid)
    abstain_positive = sum(row["label"] == 1 and row["abstained"] for row in valid)
    abstain_negative = sum(row["label"] == 0 and row["abstained"] for row in valid)
    terminal = tp + tn + fp + fn
    return {
        "samples": len(rows),
        "strict_valid_outputs": len(valid),
        "transport_or_contract_errors": len(rows) - len(valid),
        "true_positive": tp,
        "true_negative": tn,
        "false_positive": fp,
        "false_negative": fn,
        "abstain_positive": abstain_positive,
        "abstain_negative": abstain_negative,
        "coverage": round(terminal / len(valid), 6) if valid else None,
        "terminal_accuracy": round((tp + tn) / terminal, 6) if terminal else None,
        "all_sample_accuracy_abstain_incorrect": round((tp + tn) / len(rows), 6)
        if rows
        else None,
        "positive_recall": round(tp / (tp + fn + abstain_positive), 6)
        if tp + fn + abstain_positive
        else None,
        "specificity": round(tn / (tn + fp + abstain_negative), 6)
        if tn + fp + abstain_negative
        else None,
        "precision": round(tp / (tp + fp), 6) if tp + fp else None,
    }


def evaluate_case(
    manifest_row: dict[str, str],
    path: Path,
    endpoint: str,
    timeout: float,
    system: str,
    user: str,
    model: str,
) -> dict[str, Any]:
    started = time.perf_counter_ns()
    row: dict[str, Any] = {field: "" for field in CASE_FIELDS}
    row.update(
        {
            "sample_id": manifest_row["sample_id"],
            "group_id": manifest_row["group_id"],
            "split": manifest_row["split"],
            "label": int(manifest_row["label"]),
            "dataset": manifest_row["dataset"],
            "image": manifest_row["image"],
            "sha256": manifest_row["sha256"],
        }
    )
    try:
        source = path.read_bytes()
        preprocess_started = time.perf_counter_ns()
        fast_image = fast.prepare_fast_jpeg(source)
        row["preprocess_ms"] = baseline.elapsed_ms(preprocess_started)
        body = request_with_logprobs(
            fast.request_body(
                fast_image,
                "image/jpeg",
                system,
                user,
                "central_brain_smoking_wire_v2_confidence",
                fast.compact_schema(),
                fast.FAST_MAX_TOKENS,
                model,
            )
        )
        content, logprobs, usage, request_ms = execute_completion(
            endpoint, timeout, body, model
        )
        result, classification = fast.compact_result(content)
        compact = baseline.strict_json(content)
        status = compact[0]
        probabilities, margin = status_probabilities(content, logprobs, status)
        predicted_label = status if status in (0, 1) else ""
        abstained = status == 2
        decision_correct = "" if abstained else int(predicted_label == row["label"])
        row.update(
            {
                "status": status,
                "classification": classification,
                "predicted_label": predicted_label,
                "abstained": abstained,
                "decision_correct": decision_correct,
                "self_confidence": result["confidence"],
                "status_probability_0": probabilities[0],
                "status_probability_1": probabilities[1],
                "status_probability_2": probabilities[2],
                "selected_status_probability": probabilities[status],
                "selected_status_logprob_margin": margin,
                "prompt_tokens": usage["prompt_tokens"],
                "completion_tokens": usage["completion_tokens"],
                "request_ms": request_ms,
            }
        )
    except urllib.error.HTTPError as failure:
        row["failure_code"] = f"HTTP_{failure.code}"
    except (urllib.error.URLError, TimeoutError) as failure:
        row["failure_code"] = type(failure).__name__
    except (OSError, RuntimeError, ValueError, KeyError, TypeError) as failure:
        row["failure_code"] = type(failure).__name__
    row["end_to_end_ms"] = baseline.elapsed_ms(started)
    return row


def write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=CASE_FIELDS, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def numeric(rows: Iterable[dict[str, Any]], field: str) -> list[float]:
    return [float(row[field]) for row in rows if row[field] != ""]


def main() -> int:
    args = parse_args()
    if args.timeout_seconds <= 0:
        raise SystemExit("--timeout-seconds must be positive")
    profile = resolve_model_profile(args.model_profile, args.endpoint)
    endpoint = str(profile["endpoint"])
    model = str(profile["model"])
    manifest_rows = load_manifest(args.manifest)
    if args.limit > 0:
        manifest_rows = manifest_rows[: args.limit]
    resolved = [
        (row, resolve_image(row, args.positive_dir, args.negative_dir))
        for row in manifest_rows
    ]
    args.output_dir.mkdir(parents=True, exist_ok=True)
    output_files = (
        args.output_dir / "cases.csv",
        args.output_dir / "summary.json",
        args.output_dir / "calibrator.json",
    )
    if any(path.exists() for path in output_files):
        raise SystemExit("output directory already contains confidence results")

    root = Path(__file__).resolve().parents[1]
    full_system, full_user, agent_sha256 = baseline.prompt_material(root)
    system, user = fast.fast_prompt_material(full_system, full_user)
    baseline.preflight(endpoint, min(args.timeout_seconds, 10.0), model)

    rows: list[dict[str, Any]] = []
    wall_started = time.perf_counter_ns()
    for index, (manifest_row, path) in enumerate(resolved, start=1):
        row = evaluate_case(
            manifest_row, path, endpoint, args.timeout_seconds, system, user, model
        )
        rows.append(row)
        print(
            f"case={index}/{len(resolved)} sample={row['sample_id']} "
            f"status={row['status']} correct={row['decision_correct']} "
            f"self_confidence={row['self_confidence']} "
            f"status_probability={row['selected_status_probability']} "
            f"request_ms={row['request_ms']} failure={row['failure_code']}",
            flush=True,
        )
    wall_ms = baseline.elapsed_ms(wall_started)

    valid_rows = [row for row in rows if row["failure_code"] == ""]
    calibrator: dict[str, Any]
    calibration_failure = ""
    try:
        calibrator = fit_calibrator(valid_rows)
        for row in valid_rows:
            if not row["abstained"]:
                row["calibrated_decision_confidence"] = predict_calibrated(row, calibrator)
    except ValueError as failure:
        calibration_failure = str(failure)
        calibrator = {
            "type": "NOT_FITTED",
            "target": "P_AUTOMATIC_DECISION_CORRECT",
            "reason": calibration_failure,
        }

    by_split: dict[str, Any] = {}
    for split in ("calibration", "test"):
        split_rows = [row for row in rows if row["split"] == split]
        by_split[split] = {
            "classification": classification_metrics(split_rows),
            "self_reported_confidence": calibration_metrics(split_rows, "self_confidence"),
            "calibrated_confidence": calibration_metrics(
                split_rows, "calibrated_decision_confidence"
            ),
        }
    test_raw = by_split["test"]["self_reported_confidence"]
    test_calibrated = by_split["test"]["calibrated_confidence"]
    deployment_ready = bool(
        not calibration_failure
        and test_raw.get("available")
        and test_calibrated.get("available")
        and test_calibrated.get("incorrect", 0) > 0
        and test_calibrated["brier"] < test_raw["brier"]
        and test_calibrated["ece_10_bin"] < test_raw["ece_10_bin"]
        and all(row["failure_code"] == "" for row in rows)
    )
    calibrator.update(
        {
            "schema_version": 1,
            "generated_at_utc": datetime.now(timezone.utc).isoformat(),
            "status": "PILOT_VALIDATED" if deployment_ready else "PILOT_NOT_DEPLOYABLE",
            "deployment_ready": deployment_ready,
            "model": model,
            "agent_instruction_sha256": agent_sha256,
            "manifest_sha256": hashlib.sha256(args.manifest.read_bytes()).hexdigest(),
            "route_digest": baseline.route_digest(),
            "preprocessing": {
                "source_resolution": "1920x1080",
                "fast_image_maximum": f"{fast.FAST_WIDTH}x{fast.FAST_HEIGHT}",
                "fast_jpeg_quality": fast.FAST_JPEG_QUALITY,
            },
            "wire": {
                "schema": "[status,count,location_code,confidence_percent]",
                "logprobs": True,
                "top_logprobs": TOP_LOGPROBS,
                "thinking_enabled": False,
            },
        }
    )
    summary = {
        "schema_version": 1,
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "purpose": "PILOT_CONFIDENCE_CALIBRATION_ONLY",
        "dataset": {
            "samples": len(rows),
            "manifest_sha256": hashlib.sha256(args.manifest.read_bytes()).hexdigest(),
            "paired_group_split": True,
            "known_seatbelt_confound": True,
        },
        "provider": {
            "model": model,
            "model_profile": args.model_profile,
            "endpoint_class": profile["endpoint_class"],
            "temperature": 0,
            "thinking_enabled": False,
            "token_logprobs_requested": True,
            "top_logprobs": TOP_LOGPROBS,
        },
        "splits": by_split,
        "latency": {
            "preprocess": baseline.distribution(numeric(rows, "preprocess_ms")),
            "request": baseline.distribution(numeric(rows, "request_ms")),
            "end_to_end": baseline.distribution(numeric(rows, "end_to_end_ms")),
            "wall_clock_ms": wall_ms,
        },
        "calibrator": {
            "fit_failure": calibration_failure or None,
            "deployment_ready": deployment_ready,
            "decision_rule": (
                "Independent test Brier and ECE must both improve, test must contain "
                "incorrect decisions, and every model response must pass strict validation."
            ),
        },
        "evidence_boundary": {
            "production_confidence_claim_allowed": False,
            "android_runtime_inference_included": False,
            "android_calibrator_deployed": False,
            "target_camera_validated": False,
            "target_hardware_validated": False,
        },
    }
    write_csv(output_files[0], rows)
    output_files[1].write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    output_files[2].write_text(
        json.dumps(calibrator, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0 if all(row["failure_code"] == "" for row in rows) else 2


if __name__ == "__main__":
    sys.exit(main())
