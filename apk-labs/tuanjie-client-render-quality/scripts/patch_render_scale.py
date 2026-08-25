#!/usr/bin/env python3
"""Apply the approved TuanjieView supersampling request to a decoded client APK."""

from __future__ import annotations

import argparse
from pathlib import Path


APPROVED_SCALES = {
    "1.0": "0x3f800000",
    "1.25": "0x3fa00000",
    "1.5": "0x3fc00000",
}
PATCH_MARKER = "Central Brain controlled render-quality policy"
RECONNECT_MARKER = "Central Brain render-quality reconnect policy"


def patch_main_activity(work_dir: Path, package_name: str, scale: str) -> Path:
    if scale not in APPROVED_SCALES:
        raise SystemExit(
            f"unsupported render scale {scale}; approved values: "
            + ", ".join(APPROVED_SCALES)
        )

    descriptor = package_name.replace(".", "/")
    main_activity = work_dir / "smali" / descriptor / "MainActivity.smali"
    tuanjie_view = (
        work_dir
        / "smali"
        / "com"
        / "unity3d"
        / "renderservice"
        / "client"
        / "TuanjieView.smali"
    )
    if not main_activity.is_file():
        raise SystemExit(f"missing client MainActivity: {main_activity}")
    if not tuanjie_view.is_file():
        raise SystemExit(f"missing vendor TuanjieView API: {tuanjie_view}")

    text = main_activity.read_text(encoding="utf-8")
    if PATCH_MARKER in text:
        raise SystemExit(f"render-quality patch already present: {main_activity}")

    owner = f"L{descriptor}/MainActivity;"
    anchor = (
        "    invoke-virtual {p1, v0}, "
        "Landroid/view/ViewGroup;->addView(Landroid/view/View;)V\n"
    )
    if text.count(anchor) != 1:
        raise SystemExit(
            "expected exactly one original TuanjieView addView anchor in MainActivity"
        )

    injection = (
        anchor
        + "\n"
        + f"    # {PATCH_MARKER}\n"
        + f"    iget-object p1, p0, {owner}->mTuanjieViewFromXML:"
        + "Lcom/unity3d/renderservice/client/TuanjieView;\n\n"
        + f"    const/high16 v0, {APPROVED_SCALES[scale]}    # {scale}f\n\n"
        + "    invoke-virtual {p1, v0}, "
        + "Lcom/unity3d/renderservice/client/TuanjieView;->setRenderScale(F)V\n"
    )
    main_activity.write_text(text.replace(anchor, injection, 1), encoding="utf-8")

    patched = main_activity.read_text(encoding="utf-8")
    if patched.count(PATCH_MARKER) != 1 or patched.count("->setRenderScale(F)V") != 1:
        raise SystemExit("render-quality patch verification failed")
    return main_activity


def patch_tuanjie_view_reconnect(work_dir: Path) -> Path:
    tuanjie_view = (
        work_dir
        / "smali"
        / "com"
        / "unity3d"
        / "renderservice"
        / "client"
        / "TuanjieView.smali"
    )
    if not tuanjie_view.is_file():
        raise SystemExit(f"missing vendor TuanjieView API: {tuanjie_view}")

    text = tuanjie_view.read_text(encoding="utf-8")
    if RECONNECT_MARKER in text:
        raise SystemExit(f"render-quality reconnect patch already present: {tuanjie_view}")
    anchor = (
        "    .line 766\n"
        "    invoke-direct {p0}, "
        "Lcom/unity3d/renderservice/client/TuanjieView;"
        "->syncViewDataToRenderService()V\n"
    )
    if text.count(anchor) != 1:
        raise SystemExit("expected exactly one TuanjieView onServiceConnected anchor")

    injection = (
        f"    # {RECONNECT_MARKER}\n"
        "    const/4 v0, 0x1\n\n"
        "    iput-boolean v0, p0, "
        "Lcom/unity3d/renderservice/client/TuanjieView;"
        "->mNeedSetRenderScale:Z\n\n"
        + anchor
    )
    tuanjie_view.write_text(text.replace(anchor, injection, 1), encoding="utf-8")
    patched = tuanjie_view.read_text(encoding="utf-8")
    if patched.count(RECONNECT_MARKER) != 1:
        raise SystemExit("render-quality reconnect patch verification failed")
    return tuanjie_view


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--work-dir", required=True)
    parser.add_argument("--package-name", required=True)
    parser.add_argument("--scale", default="1.25")
    args = parser.parse_args()

    main_activity = patch_main_activity(
        Path(args.work_dir),
        args.package_name,
        args.scale,
    )
    tuanjie_view = patch_tuanjie_view_reconnect(Path(args.work_dir))
    print(f"patched render scale {args.scale}: {main_activity}")
    print(f"patched render-scale reconnect: {tuanjie_view}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
