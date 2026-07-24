#!/usr/bin/env python3
"""Copy the original RenderService APK and replace its Unity launcher bundle."""

from __future__ import annotations

import argparse
import shutil
import tempfile
import zipfile
from pathlib import Path

from patch_unity_hvac_bundle import patch_bundle


UNITY_ENTRY = (
    "assets/aa/HMIAndroid/"
    "launcher_assets_all_c93fe44a4d61e1b9545c50b3baddfbd7.bundle"
)


def is_signature_entry(name: str) -> bool:
    upper = name.upper()
    return upper.startswith("META-INF/") and upper.endswith(
        (".RSA", ".DSA", ".EC", ".SF", "MANIFEST.MF")
    )


def build_apk(source_apk: Path, output_apk: Path) -> None:
    output_apk.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="central-brain-renderservice-") as tmp:
        temp_root = Path(tmp)
        source_bundle = temp_root / Path(UNITY_ENTRY).name
        patched_bundle = temp_root / f"patched-{source_bundle.name}"
        with zipfile.ZipFile(source_apk, "r") as source_zip:
            try:
                with source_zip.open(UNITY_ENTRY) as source:
                    with source_bundle.open("wb") as destination:
                        shutil.copyfileobj(source, destination)
            except KeyError as exc:
                raise SystemExit(f"source APK is missing {UNITY_ENTRY}") from exc

        patch_bundle(source_bundle, patched_bundle)

        with zipfile.ZipFile(source_apk, "r") as source_zip:
            with zipfile.ZipFile(
                output_apk,
                "w",
                allowZip64=True,
            ) as output_zip:
                for info in source_zip.infolist():
                    if is_signature_entry(info.filename):
                        continue
                    if info.filename == UNITY_ENTRY:
                        with patched_bundle.open("rb") as source:
                            with output_zip.open(info, "w", force_zip64=True) as destination:
                                shutil.copyfileobj(source, destination)
                        continue
                    with source_zip.open(info) as source:
                        with output_zip.open(info, "w", force_zip64=True) as destination:
                            shutil.copyfileobj(source, destination)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-apk", type=Path, required=True)
    parser.add_argument("--output-apk", type=Path, required=True)
    args = parser.parse_args()
    build_apk(args.source_apk, args.output_apk)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
