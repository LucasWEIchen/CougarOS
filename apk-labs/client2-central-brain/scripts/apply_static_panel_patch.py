#!/usr/bin/env python3
"""Apply Client2 Central Brain APK resource, manifest, and smali patches."""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path


def patch_layout(work_dir: Path, patch_xml: Path) -> None:
    target_xml = work_dir / "res" / "layout" / "main_layout.xml"

    if not target_xml.is_file():
        raise SystemExit(f"missing target layout: {target_xml}")
    if not patch_xml.is_file():
        raise SystemExit(f"missing patch layout: {patch_xml}")

    current = target_xml.read_text(encoding="utf-8")
    if "@id/view1" not in current:
        raise SystemExit("target layout does not contain original @id/view1 anchor")

    patched = patch_xml.read_text(encoding="utf-8")
    required_markers = [
        "@id/view1",
        "@+id/centralBrainPanel",
        "@+id/centralBrainColdButton",
        "@+id/centralBrainTiredButton",
        "@+id/centralBrainReplyText",
        "No Driver/HAL, hardware, or virtualization work",
    ]
    missing = [marker for marker in required_markers if marker not in patched]
    if missing:
        raise SystemExit(f"patch layout missing markers: {missing}")

    target_xml.write_text(patched, encoding="utf-8")
    print(f"patched {target_xml}")


def patch_manifest(work_dir: Path) -> None:
    manifest = work_dir / "AndroidManifest.xml"
    if not manifest.is_file():
        raise SystemExit(f"missing AndroidManifest.xml: {manifest}")

    text = manifest.read_text(encoding="utf-8")
    if "android.permission.INTERNET" not in text:
        anchor = '<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/>'
        if anchor not in text:
            raise SystemExit("manifest missing QUERY_ALL_PACKAGES permission anchor")
        text = text.replace(
            anchor,
            anchor + '\n    <uses-permission android:name="android.permission.INTERNET"/>',
            1,
        )

    if "android:usesCleartextTraffic=" not in text:
        app_anchor = "<application "
        if app_anchor not in text:
            raise SystemExit("manifest missing application tag")
        text = text.replace(app_anchor, '<application android:usesCleartextTraffic="true" ', 1)

    manifest.write_text(text, encoding="utf-8")
    print(f"patched {manifest}")


def patch_main_activity_hook(work_dir: Path) -> None:
    main_activity = work_dir / "smali" / "com" / "tuanjie" / "urasclient2" / "MainActivity.smali"
    if not main_activity.is_file():
        raise SystemExit(f"missing MainActivity.smali: {main_activity}")

    text = main_activity.read_text(encoding="utf-8")
    hook = (
        "    invoke-static {p0}, "
        "Lcom/tuanjie/urasclient2/CentralBrainPanelController;->install(Landroid/app/Activity;)V"
    )
    if hook in text:
        print(f"MainActivity hook already present: {main_activity}")
        return

    marker = (
        "    invoke-virtual {p0, p1}, "
        "Lcom/tuanjie/urasclient2/MainActivity;->setContentView(I)V\n\n"
        "    .line 77"
    )
    replacement = (
        "    invoke-virtual {p0, p1}, "
        "Lcom/tuanjie/urasclient2/MainActivity;->setContentView(I)V\n\n"
        f"{hook}\n\n"
        "    .line 77"
    )
    if marker not in text:
        raise SystemExit("MainActivity onCreate setContentView marker not found")

    main_activity.write_text(text.replace(marker, replacement, 1), encoding="utf-8")
    print(f"patched {main_activity}")


def copy_smali_patches(work_dir: Path, project_dir: Path) -> None:
    smali_src = project_dir / "patches" / "smali"
    smali_dst = work_dir / "smali"
    if not smali_src.is_dir():
        raise SystemExit(f"missing smali patch directory: {smali_src}")
    if not smali_dst.is_dir():
        raise SystemExit(f"missing smali output directory: {smali_dst}")

    copied = 0
    for src in smali_src.rglob("*.smali"):
        dst = smali_dst / src.relative_to(smali_src)
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        copied += 1
    if copied < 3:
        raise SystemExit(f"expected at least 3 smali patch files, copied {copied}")
    print(f"copied {copied} smali patch files into {smali_dst}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--work-dir", required=True)
    parser.add_argument("--patch-xml", required=True)
    args = parser.parse_args()

    work_dir = Path(args.work_dir)
    patch_xml = Path(args.patch_xml)
    project_dir = Path(__file__).resolve().parents[1]

    if not work_dir.is_dir():
        raise SystemExit(f"missing work dir: {work_dir}")
    patch_layout(work_dir, patch_xml)
    patch_manifest(work_dir)
    patch_main_activity_hook(work_dir)
    copy_smali_patches(work_dir, project_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
