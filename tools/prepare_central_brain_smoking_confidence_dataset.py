#!/usr/bin/env python3
"""Prepare paired smoking/non-smoking images for pilot confidence evaluation."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import statistics
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from PIL import Image, ImageOps, ImageStat, UnidentifiedImageError


TARGET_SIZE = (1920, 1080)
JPEG_QUALITY = 95
SPLIT_SALT = "central-brain-smoking-confidence-pilot-v1"
MANIFEST_FIELDS = (
    "sample_id",
    "group_id",
    "split",
    "label",
    "dataset",
    "image",
    "sha256",
    "bytes",
    "width",
    "height",
    "seatbelt_state",
    "confound_note",
)
IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--positive-dir", type=Path, required=True)
    parser.add_argument("--negative-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--backup", type=Path, required=True)
    parser.add_argument("--normalize-negative-in-place", action="store_true")
    parser.add_argument("--calibration-groups", type=int, default=70)
    return parser.parse_args()


def image_paths(directory: Path) -> list[Path]:
    if not directory.is_dir():
        raise ValueError(f"dataset directory is unavailable: {directory}")
    paths = sorted(
        (path for path in directory.iterdir() if path.suffix.lower() in IMAGE_SUFFIXES),
        key=lambda path: path.name,
    )
    if not paths:
        raise ValueError(f"dataset has no images: {directory}")
    return paths


def sha256_path(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def image_record(path: Path) -> dict[str, Any]:
    content = path.read_bytes()
    if not content:
        raise ValueError(f"empty image: {path}")
    try:
        with Image.open(path) as image:
            image.load()
            width, height = image.size
            mode = image.mode
            brightness_image = ImageOps.exif_transpose(image).convert("L").resize((64, 64))
            mean_brightness = ImageStat.Stat(brightness_image).mean[0]
    except (OSError, UnidentifiedImageError) as failure:
        raise ValueError(f"unreadable image: {path}") from failure
    return {
        "path": path,
        "name": path.name,
        "sha256": hashlib.sha256(content).hexdigest(),
        "bytes": len(content),
        "width": width,
        "height": height,
        "mode": mode,
        "mean_brightness": round(mean_brightness, 3),
    }


def profile(paths: list[Path]) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    records = [image_record(path) for path in paths]
    hashes = [record["sha256"] for record in records]
    dimensions = sorted({f"{record['width']}x{record['height']}" for record in records})
    return records, {
        "count": len(records),
        "unique_sha256": len(set(hashes)),
        "dimensions": dimensions,
        "mean_bytes": round(statistics.fmean(record["bytes"] for record in records), 3),
        "mean_brightness": round(
            statistics.fmean(record["mean_brightness"] for record in records), 3
        ),
    }


def normalize_image_in_place(path: Path) -> None:
    try:
        with Image.open(path) as source:
            source.load()
            image = ImageOps.exif_transpose(source).convert("RGB")
    except (OSError, UnidentifiedImageError) as failure:
        raise ValueError(f"unreadable image: {path}") from failure

    image.thumbnail(TARGET_SIZE, Image.Resampling.LANCZOS)
    canvas = Image.new("RGB", TARGET_SIZE, (0, 0, 0))
    left = (TARGET_SIZE[0] - image.width) // 2
    top = (TARGET_SIZE[1] - image.height) // 2
    canvas.paste(image, (left, top))

    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{path.stem}.", suffix=".tmp", dir=path.parent
    )
    os.close(descriptor)
    temporary = Path(temporary_name)
    try:
        canvas.save(
            temporary,
            format="JPEG",
            quality=JPEG_QUALITY,
            subsampling=0,
            optimize=True,
        )
        with Image.open(temporary) as check:
            check.load()
            if check.size != TARGET_SIZE or check.mode != "RGB":
                raise ValueError(f"normalized image verification failed: {path}")
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def grouped_split(group_ids: list[str], calibration_groups: int) -> dict[str, str]:
    if calibration_groups <= 0 or calibration_groups >= len(group_ids):
        raise ValueError("calibration group count must leave a non-empty test split")
    ranked = sorted(
        group_ids,
        key=lambda group_id: hashlib.sha256(
            f"{SPLIT_SALT}:{group_id}".encode("utf-8")
        ).hexdigest(),
    )
    calibration = set(ranked[:calibration_groups])
    return {
        group_id: "calibration" if group_id in calibration else "test"
        for group_id in group_ids
    }


def exact_name_map(records: list[dict[str, Any]], label: str) -> dict[str, dict[str, Any]]:
    result: dict[str, dict[str, Any]] = {}
    for record in records:
        if record["name"] in result:
            raise ValueError(f"duplicate {label} filename: {record['name']}")
        result[record["name"]] = record
    return result


def build_manifest(
    positive: list[dict[str, Any]],
    negative: list[dict[str, Any]],
    calibration_groups: int,
) -> list[dict[str, Any]]:
    positives = exact_name_map(positive, "positive")
    negatives = exact_name_map(negative, "negative")
    if positives.keys() != negatives.keys():
        missing_negative = sorted(positives.keys() - negatives.keys())
        missing_positive = sorted(negatives.keys() - positives.keys())
        raise ValueError(
            "paired filenames differ: "
            f"missing_negative={missing_negative} missing_positive={missing_positive}"
        )
    group_ids = [Path(name).stem for name in sorted(positives)]
    splits = grouped_split(group_ids, calibration_groups)
    rows: list[dict[str, Any]] = []
    for name in sorted(positives):
        group_id = Path(name).stem
        for dataset, label, record, seatbelt_state in (
            ("passenger_smoking_100", 1, positives[name], "UNKNOWN"),
            (
                "passenger_unbelted_nonsmoking_100",
                0,
                negatives[name],
                "UNBELTED_BY_DATASET_CONTRACT",
            ),
        ):
            rows.append(
                {
                    "sample_id": f"{group_id}-{'positive' if label else 'negative'}",
                    "group_id": group_id,
                    "split": splits[group_id],
                    "label": label,
                    "dataset": dataset,
                    "image": name,
                    "sha256": record["sha256"],
                    "bytes": record["bytes"],
                    "width": record["width"],
                    "height": record["height"],
                    "seatbelt_state": seatbelt_state,
                    "confound_note": (
                        "The paired sets differ in smoking label and seatbelt state; "
                        "this pilot cannot isolate smoking as the only causal factor."
                    ),
                }
            )
    return rows


def require_delivery_resolution(records: list[dict[str, Any]], label: str) -> None:
    invalid = [
        record["name"]
        for record in records
        if (record["width"], record["height"]) != TARGET_SIZE
    ]
    if invalid:
        raise ValueError(f"{label} images are not exact 1920x1080: {invalid[:5]}")


def write_manifest(path: Path, rows: list[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=MANIFEST_FIELDS, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def main() -> int:
    args = parse_args()
    if not args.backup.is_file():
        raise SystemExit(f"required external backup is unavailable: {args.backup}")

    positive_paths = image_paths(args.positive_dir)
    negative_paths = image_paths(args.negative_dir)
    positive_records, positive_profile = profile(positive_paths)
    negative_before_records, negative_before_profile = profile(negative_paths)

    if args.normalize_negative_in_place:
        for index, path in enumerate(negative_paths, start=1):
            normalize_image_in_place(path)
            print(f"normalized={index}/{len(negative_paths)} image={path.name}", flush=True)

    negative_records, negative_profile = profile(negative_paths)
    require_delivery_resolution(positive_records, "positive")
    require_delivery_resolution(negative_records, "negative")

    positive_hashes = {record["sha256"] for record in positive_records}
    negative_hashes = {record["sha256"] for record in negative_records}
    if len(positive_hashes) != len(positive_records):
        raise SystemExit("positive dataset contains exact duplicates")
    if len(negative_hashes) != len(negative_records):
        raise SystemExit("negative dataset contains exact duplicates")
    if positive_hashes & negative_hashes:
        raise SystemExit("positive and negative datasets contain cross-set exact duplicates")

    rows = build_manifest(positive_records, negative_records, args.calibration_groups)
    output_files = (
        args.output_dir / "confidence-pilot-manifest.csv",
        args.output_dir / "confidence-pilot-data-quality.json",
    )
    args.output_dir.mkdir(parents=True, exist_ok=True)
    if any(path.exists() for path in output_files):
        raise SystemExit("output directory already contains dataset preparation results")
    write_manifest(output_files[0], rows)
    split_counts = {
        split: sum(row["split"] == split for row in rows)
        for split in ("calibration", "test")
    }
    quality = {
        "schema_version": 1,
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "purpose": "PILOT_CONFIDENCE_CALIBRATION_ONLY",
        "normalization": {
            "target_resolution": {"width": TARGET_SIZE[0], "height": TARGET_SIZE[1]},
            "method": "EXIF_TRANSPOSE_LANCZOS_ASPECT_FIT_CENTERED_BLACK_CANVAS",
            "jpeg_quality": JPEG_QUALITY,
            "jpeg_subsampling": "4:4:4",
            "negative_in_place_requested": args.normalize_negative_in_place,
        },
        "backup": {
            "external_to_repository": True,
            "filename": args.backup.name,
            "bytes": args.backup.stat().st_size,
            "sha256": sha256_path(args.backup),
        },
        "datasets": {
            "positive": positive_profile,
            "negative_before": negative_before_profile,
            "negative_after": negative_profile,
            "cross_set_exact_duplicate_count": 0,
        },
        "split": {
            "strategy": "PAIRED_GROUP_HASH_RANK",
            "salt": SPLIT_SALT,
            "calibration_groups": args.calibration_groups,
            "test_groups": len(positive_records) - args.calibration_groups,
            "sample_counts": split_counts,
            "pair_leakage_count": 0,
        },
        "known_biases": [
            "The negative set is also labelled unbelted, so smoking label and seatbelt state are confounded.",
            "The two source sets had different resolution, compression, size, and brightness distributions.",
            "The images are controlled pilot data and are not target-camera or production-distribution evidence.",
        ],
        "acceptance_boundary": {
            "calibrator_deployment_ready": False,
            "production_confidence_claim_allowed": False,
            "target_hardware_validated": False,
        },
    }
    output_files[1].write_text(
        json.dumps(quality, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(quality, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
