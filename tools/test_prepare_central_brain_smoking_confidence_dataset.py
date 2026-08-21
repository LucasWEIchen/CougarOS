#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

from PIL import Image


MODULE_PATH = Path(__file__).with_name(
    "prepare_central_brain_smoking_confidence_dataset.py"
)
SPEC = importlib.util.spec_from_file_location("prepare_smoking_confidence", MODULE_PATH)
assert SPEC and SPEC.loader
prepare = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(prepare)


class PrepareSmokingConfidenceDatasetTest(unittest.TestCase):
    def test_normalize_image_preserves_aspect_ratio_on_exact_canvas(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "001.jpg"
            Image.new("RGB", (1448, 1086), (200, 100, 50)).save(path, quality=80)

            prepare.normalize_image_in_place(path)

            with Image.open(path) as image:
                self.assertEqual((1920, 1080), image.size)
                self.assertEqual("RGB", image.mode)
                self.assertLess(sum(image.getpixel((10, 540))), 20)
                self.assertGreater(sum(image.getpixel((960, 540))), 200)

    def test_grouped_split_is_deterministic_and_exact(self) -> None:
        groups = [f"{index:03d}" for index in range(1, 101)]
        first = prepare.grouped_split(groups, 70)
        second = prepare.grouped_split(list(reversed(groups)), 70)

        self.assertEqual(first, second)
        self.assertEqual(70, sum(split == "calibration" for split in first.values()))
        self.assertEqual(30, sum(split == "test" for split in first.values()))

    def test_manifest_keeps_pair_members_in_same_split(self) -> None:
        positive = [
            {
                "name": "001.jpg",
                "sha256": "a",
                "bytes": 1,
                "width": 1920,
                "height": 1080,
            },
            {
                "name": "002.jpg",
                "sha256": "b",
                "bytes": 1,
                "width": 1920,
                "height": 1080,
            },
        ]
        negative = [
            {
                "name": "001.jpg",
                "sha256": "c",
                "bytes": 1,
                "width": 1920,
                "height": 1080,
            },
            {
                "name": "002.jpg",
                "sha256": "d",
                "bytes": 1,
                "width": 1920,
                "height": 1080,
            },
        ]

        rows = prepare.build_manifest(positive, negative, calibration_groups=1)

        by_group: dict[str, set[str]] = {}
        for row in rows:
            by_group.setdefault(row["group_id"], set()).add(row["split"])
        self.assertTrue(all(len(splits) == 1 for splits in by_group.values()))


if __name__ == "__main__":
    unittest.main()
