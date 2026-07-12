#!/usr/bin/env python3
"""Apply Client2 Central Brain APK resource, manifest, and smali patches."""

from __future__ import annotations

import argparse
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path


ANDROID_NAME = "{http://schemas.android.com/apk/res/android}name"
ANDROID_CLEARTEXT = "{http://schemas.android.com/apk/res/android}usesCleartextTraffic"


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
        "@+id/centralBrainPanelOverlay",
        "@+id/centralBrainPanel",
        "@+id/centralBrainControlScroll",
        "@+id/centralBrainColdButton",
        "@+id/centralBrainTiredButton",
        "@+id/centralBrainHomeButton",
        "@+id/centralBrainNapButton",
        "@+id/centralBrainVehicleStateButton",
        "@+id/centralBrainMemoryButton",
        "@+id/centralBrainSkillsButton",
        "@+id/centralBrainAuditButton",
        "@+id/centralBrainDeniedButton",
        "@+id/centralBrainPrivacyButton",
        "@+id/centralBrainNpuButton",
        "@+id/centralBrainOverviewButton",
        "@+id/centralBrainReplyText",
        "@drawable/central_brain_panel_background",
        "@drawable/central_brain_action_button",
        "@drawable/central_brain_reply_background",
    ]
    missing = [marker for marker in required_markers if marker not in patched]
    if missing:
        raise SystemExit(f"patch layout missing markers: {missing}")

    target_xml.write_text(patched, encoding="utf-8")
    print(f"patched {target_xml}")


def copy_resource_patches(work_dir: Path, project_dir: Path) -> None:
    resource_src = project_dir / "patches" / "res"
    resource_dst = work_dir / "res"
    if not resource_src.is_dir():
        raise SystemExit(f"missing resource patch directory: {resource_src}")
    if not resource_dst.is_dir():
        raise SystemExit(f"missing resource output directory: {resource_dst}")

    copied = 0
    for src in resource_src.rglob("*"):
        if not src.is_file():
            continue
        dst = resource_dst / src.relative_to(resource_src)
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        copied += 1
    if copied < 3:
        raise SystemExit(f"expected at least 3 resource patch files, copied {copied}")
    print(f"copied {copied} resource patch files into {resource_dst}")


def patch_manifest(work_dir: Path) -> None:
    manifest = work_dir / "AndroidManifest.xml"
    if not manifest.is_file():
        raise SystemExit(f"missing AndroidManifest.xml: {manifest}")

    try:
        ET.parse(manifest)
    except ET.ParseError as exc:
        raise SystemExit(f"invalid source manifest: {exc}") from exc

    text = manifest.read_text(encoding="utf-8")
    text = text.replace(
        '    <uses-permission android:name="android.permission.INTERNET"/>\n', ""
    )
    text = text.replace('android:usesCleartextTraffic="true" ', "")

    bind_permission = (
        '<uses-permission android:name="com.centralbrain.permission.BIND_RUNTIME"/>'
    )
    if bind_permission not in text:
        anchor = '<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/>'
        if anchor not in text:
            raise SystemExit("manifest missing QUERY_ALL_PACKAGES permission anchor")
        text = text.replace(anchor, anchor + "\n    " + bind_permission, 1)

    runtime_query = '<package android:name="com.centralbrain.runtime"/>'
    if runtime_query not in text:
        app_anchor = "    <application "
        if app_anchor not in text:
            raise SystemExit("manifest missing application tag")
        queries = (
            "    <queries>\n"
            f"        {runtime_query}\n"
            "    </queries>\n"
        )
        text = text.replace(app_anchor, queries + app_anchor, 1)

    manifest.write_text(text, encoding="utf-8")
    root = ET.parse(manifest).getroot()
    permissions = {
        element.attrib.get(ANDROID_NAME, "")
        for element in root.findall("uses-permission")
    }
    if "com.centralbrain.permission.BIND_RUNTIME" not in permissions:
        raise SystemExit("manifest missing Central Brain Runtime signature permission")
    if "android.permission.INTERNET" in permissions:
        raise SystemExit("Client2 Binder demo must not request INTERNET")
    runtime_packages = {
        element.attrib.get(ANDROID_NAME, "")
        for element in root.findall("queries/package")
    }
    if "com.centralbrain.runtime" not in runtime_packages:
        raise SystemExit("manifest missing explicit Central Brain Runtime package query")
    application = root.find("application")
    if application is None or ANDROID_CLEARTEXT in application.attrib:
        raise SystemExit("Client2 Binder demo must not opt into cleartext traffic")
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
    if copied != 2:
        raise SystemExit(f"expected exactly 2 smali patch files, copied {copied}")
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
    copy_resource_patches(work_dir, project_dir)
    patch_manifest(work_dir)
    patch_main_activity_hook(work_dir)
    copy_smali_patches(work_dir, project_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
