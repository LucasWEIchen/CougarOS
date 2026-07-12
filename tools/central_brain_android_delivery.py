#!/usr/bin/env python3
"""Build and verify the Android 13 Central Brain application handoff bundle."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path, PurePosixPath
from typing import Any


EXPECTED_ARTIFACT_IDS = [
    "central-brain-sdk",
    "runtime-service",
    "demo-hmi",
    "client2-demo",
]
EXPECTED_INSTALL_SEQUENCE = ["runtime-service", "demo-hmi", "client2-demo"]
EXPECTED_BLOCKERS = [
    "TARGET_SYSTEM_INTEGRATION_OWNER_UNRESOLVED",
    "PRODUCTION_EFFECT_DELIVERY_BLOCKED",
    "PRODUCTION_MODEL_RUNTIME_BLOCKED",
    "PRODUCTION_EVENT_RUNTIME_BLOCKED",
    "PRODUCTION_MEMORY_RUNTIME_BLOCKED",
    "PRODUCTION_SKILL_GOVERNANCE_BLOCKED",
    "TARGET_HARDWARE_NOT_VALIDATED",
]


def fail(message: str) -> None:
    raise SystemExit(message)


def load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"cannot read JSON {path}: {exc}")
    if not isinstance(value, dict):
        fail(f"JSON root must be an object: {path}")
    return value


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def validate_relative_path(value: Any, field: str) -> str:
    if not isinstance(value, str) or not value or "\\" in value or "\0" in value:
        fail(f"invalid relative path for {field}: {value!r}")
    relative = PurePosixPath(value)
    if relative.is_absolute() or ".." in relative.parts or relative.as_posix() != value:
        fail(f"unsafe or non-normalized relative path for {field}: {value!r}")
    return value


def rooted_path(root: Path, relative: Any, field: str) -> Path:
    value = validate_relative_path(relative, field)
    return root.joinpath(*PurePosixPath(value).parts)


def run_text(command: list[str]) -> str:
    try:
        completed = subprocess.run(
            command,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
    except (OSError, subprocess.CalledProcessError) as exc:
        output = getattr(exc, "stdout", "")
        fail(f"command failed: {' '.join(command)}\n{output}")
    return completed.stdout


def validate_profile(profile: dict[str, Any]) -> None:
    if profile.get("profile_version") != "1.0.0":
        fail("delivery profile_version must be 1.0.0")
    if profile.get("delivery_scope") != "android13-application-layer":
        fail("delivery profile must remain Android 13 application-layer only")
    status = profile.get("status")
    if not isinstance(status, dict):
        fail("delivery profile status is missing")
    expected_status = {
        "software_handoff_ready": True,
        "r7_application_integration_complete": True,
        "production_ready": False,
        "target_system_integration_owner_resolved": False,
        "target_hardware_validated": False,
        "hardware_accessed": False,
        "driver_development_triggered": False,
        "virtualization_development_triggered": False,
    }
    if status != expected_status:
        fail(f"delivery status changed: {status}")

    artifacts = profile.get("artifacts")
    if not isinstance(artifacts, list):
        fail("delivery artifacts must be a list")
    if [item.get("id") for item in artifacts] != EXPECTED_ARTIFACT_IDS:
        fail("delivery artifact IDs/order changed")
    if profile.get("install_sequence") != EXPECTED_INSTALL_SEQUENCE:
        fail("delivery install sequence changed")
    if profile.get("rollback_sequence") != list(reversed(EXPECTED_INSTALL_SEQUENCE)):
        fail("delivery rollback sequence must reverse install order")
    if profile.get("preserved_blockers") != EXPECTED_BLOCKERS:
        fail("delivery blocker order changed")

    empty_interfaces = profile.get("empty_interfaces")
    if not isinstance(empty_interfaces, list) or len(empty_interfaces) != 7:
        fail("delivery profile must expose exactly seven empty integration slots")
    if [item.get("blocker") for item in empty_interfaces] != EXPECTED_BLOCKERS:
        fail("empty integration slots do not map exactly to preserved blockers")
    if any(item.get("activation_allowed") is not False for item in empty_interfaces):
        fail("empty integration slot unexpectedly permits activation")

    signing = profile.get("signing")
    if not isinstance(signing, dict):
        fail("delivery signing contract is missing")
    if signing.get("current_artifact_class") != "debug":
        fail("current delivery artifacts must remain explicitly debug-signed")
    if signing.get("same_signer_required_for") != EXPECTED_INSTALL_SEQUENCE:
        fail("signature permission cohort changed")
    if signing.get("production_resigning_required") is not True:
        fail("production re-signing must remain required")
    if signing.get("automatic_uninstall_on_signer_mismatch") is not False:
        fail("delivery must never auto-uninstall on signer mismatch")

    support_files = profile.get("support_files")
    if not isinstance(support_files, list) or len(support_files) < 8:
        fail("delivery support file inventory is incomplete")
    bundle_paths: list[str] = []
    for index, item in enumerate(artifacts):
        if not isinstance(item, dict):
            fail(f"delivery artifact entry is not an object: {index}")
        validate_relative_path(item.get("source"), f"artifacts[{index}].source")
        bundle_paths.append(
            validate_relative_path(
                item.get("bundle_path"), f"artifacts[{index}].bundle_path"
            )
        )
    for index, item in enumerate(support_files):
        if not isinstance(item, dict):
            fail(f"delivery support entry is not an object: {index}")
        validate_relative_path(item.get("source"), f"support_files[{index}].source")
        bundle_paths.append(
            validate_relative_path(
                item.get("bundle_path"), f"support_files[{index}].bundle_path"
            )
        )
    if len(bundle_paths) != len(set(bundle_paths)):
        fail("delivery bundle paths must be unique")
    if {"DELIVERY-MANIFEST.json", "SHA256SUMS"} & set(bundle_paths):
        fail("delivery profile must not overwrite generated inventory files")
    all_sources = [str(item.get("source", "")) for item in artifacts + support_files]
    if any(path.startswith("central-brain/deploy/linux/") for path in all_sources):
        fail("Android-only R7D delivery must not include Linux deployment artifacts")


def validate_target_inputs(path: Path) -> None:
    target = load_json(path)
    if target.get("schema_version") != "1.0.0" or target.get("status") != "template":
        fail("target input example must remain an unresolved 1.0.0 template")
    claims = target.get("claim_state")
    if claims != {
        "target_system_integration_owner_resolved": False,
        "production_activation_allowed": False,
        "target_hardware_validated": False,
    }:
        fail("target input template contains an unsupported positive claim")
    owners = target.get("owners")
    if not isinstance(owners, dict) or any(value is not None for value in owners.values()):
        fail("target input template must not guess target owners")


def zip_facts(path: Path) -> tuple[bool, bool]:
    try:
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
    except (OSError, zipfile.BadZipFile) as exc:
        fail(f"invalid ZIP artifact {path}: {exc}")
    native = any(
        re.match(r"^(lib|jni|libs)/.+\.so$", name, flags=re.IGNORECASE)
        for name in names
    )
    return native, "classes2.dex" in names


def apk_facts(path: Path, aapt: str, apksigner: str) -> dict[str, Any]:
    badging = run_text([aapt, "dump", "badging", str(path)])
    package_match = re.search(
        r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']*)'",
        badging,
    )
    if package_match is None:
        fail(f"cannot parse APK badging: {path}")
    min_sdk = re.search(r"sdkVersion:'([^']+)'", badging)
    target_sdk = re.search(r"targetSdkVersion:'([^']+)'", badging)
    signer = run_text([apksigner, "verify", "--print-certs", str(path)])
    digest_match = re.search(r"certificate SHA-256 digest:\s*([^\s]+)", signer)
    dn_match = re.search(r"certificate DN:\s*(.+)", signer)
    if digest_match is None or dn_match is None:
        fail(f"cannot parse APK signer: {path}")
    return {
        "package_name": package_match.group(1),
        "version_code": package_match.group(2),
        "version_name": package_match.group(3),
        "min_sdk": min_sdk.group(1) if min_sdk else "",
        "target_sdk": target_sdk.group(1) if target_sdk else "",
        "signer_sha256": digest_match.group(1).lower(),
        "signer_dn": dn_match.group(1).strip(),
    }


def inventory_file(path: Path, bundle_path: str) -> dict[str, Any]:
    return {
        "bundle_path": bundle_path,
        "size_bytes": path.stat().st_size,
        "sha256": sha256(path),
    }


def build_bundle(args: argparse.Namespace) -> None:
    repo_root = Path(args.repo_root).resolve()
    profile_path = Path(args.profile).resolve()
    bundle_dir = Path(args.output_dir).resolve()
    profile = load_json(profile_path)
    validate_profile(profile)
    validate_target_inputs(
        repo_root / "central-brain/delivery/android/target-inputs.example.json"
    )
    if not re.fullmatch(r"[0-9a-f]{7,40}", args.git_commit):
        fail("git commit must be a lowercase hexadecimal Git object ID")
    if bundle_dir.exists() and any(bundle_dir.iterdir()):
        fail(f"bundle output directory must be empty: {bundle_dir}")
    bundle_dir.mkdir(parents=True, exist_ok=True)

    artifact_inventory: list[dict[str, Any]] = []
    signer_by_id: dict[str, str] = {}
    signer_dn_by_id: dict[str, str] = {}
    for item in profile["artifacts"]:
        source = rooted_path(repo_root, item["source"], f"artifact {item['id']} source")
        if not source.is_file():
            fail(f"missing delivery artifact: {source}")
        destination = rooted_path(
            bundle_dir, item["bundle_path"], f"artifact {item['id']} bundle path"
        )
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        native_payload, secondary_dex = zip_facts(destination)
        if native_payload:
            fail(f"current application handoff must not contain native payload: {source}")
        record = {
            "id": item["id"],
            "kind": item["kind"],
            "required": item["required"],
            "install_order": item["install_order"],
            **inventory_file(destination, item["bundle_path"]),
            "native_library_payload_present": native_payload,
        }
        if item["kind"] == "apk":
            facts = apk_facts(destination, args.aapt, args.apksigner)
            if facts["package_name"] != item["package_name"]:
                fail(
                    f"APK package mismatch for {item['id']}: "
                    f"{facts['package_name']} != {item['package_name']}"
                )
            if facts["min_sdk"] != str(item["expected_min_sdk"]):
                fail(
                    f"APK minSdk mismatch for {item['id']}: "
                    f"{facts['min_sdk']} != {item['expected_min_sdk']}"
                )
            record.update(facts)
            signer_by_id[item["id"]] = facts["signer_sha256"]
            signer_dn_by_id[item["id"]] = facts["signer_dn"]
        if item["id"] == "client2-demo":
            if not secondary_dex:
                fail("Client2 handoff APK is missing classes2.dex")
            record["secondary_sdk_dex_present"] = True
        artifact_inventory.append(record)

    cohort = profile["signing"]["same_signer_required_for"]
    cohort_signers = {signer_by_id[item_id] for item_id in cohort}
    cohort_dns = {signer_dn_by_id[item_id] for item_id in cohort}
    if len(cohort_signers) != 1 or len(cohort_dns) != 1:
        fail("Runtime, Demo and Client2 must form one signer cohort")
    signer_sha256 = next(iter(cohort_signers))
    signer_dn = next(iter(cohort_dns))

    support_inventory: list[dict[str, Any]] = []
    for item in profile["support_files"]:
        source = rooted_path(repo_root, item["source"], "support source")
        if not source.is_file():
            fail(f"missing delivery support file: {source}")
        destination = rooted_path(bundle_dir, item["bundle_path"], "support bundle path")
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        support_inventory.append(inventory_file(destination, item["bundle_path"]))

    manifest = {
        "manifest_version": "1.0.0",
        "delivery_id": profile["delivery_id"],
        "delivery_scope": profile["delivery_scope"],
        "generated_at_utc": dt.datetime.now(dt.timezone.utc)
        .replace(microsecond=0)
        .isoformat(),
        "source_git_commit": args.git_commit,
        "status": profile["status"],
        "artifact_inventory": artifact_inventory,
        "support_inventory": support_inventory,
        "signing": {
            **profile["signing"],
            "signer_cohort_verified": True,
            "current_signer_sha256": signer_sha256,
            "current_signer_dn": signer_dn,
            "debug_signer_detected": "Android Debug" in signer_dn,
        },
        "install_sequence": profile["install_sequence"],
        "rollback_sequence": profile["rollback_sequence"],
        "empty_interfaces": profile["empty_interfaces"],
        "preserved_blockers": profile["preserved_blockers"],
        "validation_commands": profile["validation_commands"],
        "prohibited_operations": profile["prohibited_operations"],
    }
    manifest_path = bundle_dir / "DELIVERY-MANIFEST.json"
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=True, indent=2) + "\n", encoding="utf-8"
    )

    checksum_paths = sorted(
        path for path in bundle_dir.rglob("*") if path.is_file()
    )
    checksum_lines = [
        f"{sha256(path)}  {path.relative_to(bundle_dir).as_posix()}"
        for path in checksum_paths
    ]
    (bundle_dir / "SHA256SUMS").write_text(
        "\n".join(checksum_lines) + "\n", encoding="ascii"
    )
    print(f"delivery_manifest={manifest_path}")
    print(f"artifact_count={len(artifact_inventory)}")
    print("signer_cohort_verified=true")


def verify_bundle(args: argparse.Namespace) -> None:
    bundle_dir = Path(args.bundle_dir).resolve()
    manifest_path = bundle_dir / "DELIVERY-MANIFEST.json"
    checksum_path = bundle_dir / "SHA256SUMS"
    profile_path = bundle_dir / "contracts/central-brain.android-delivery-profile.json"
    target_inputs_path = bundle_dir / "contracts/target-inputs.example.json"
    for path in (manifest_path, checksum_path, profile_path, target_inputs_path):
        if not path.is_file():
            fail(f"bundle file is missing: {path}")

    profile = load_json(profile_path)
    manifest = load_json(manifest_path)
    validate_profile(profile)
    validate_target_inputs(target_inputs_path)
    if manifest.get("manifest_version") != "1.0.0":
        fail("delivery manifest version changed")
    if manifest.get("delivery_id") != profile.get("delivery_id"):
        fail("delivery manifest/profile ID mismatch")
    if manifest.get("delivery_scope") != "android13-application-layer":
        fail("delivery manifest scope changed")
    if manifest.get("status") != profile.get("status"):
        fail("delivery manifest status differs from profile")
    if manifest.get("preserved_blockers") != EXPECTED_BLOCKERS:
        fail("delivery manifest blockers changed")
    artifact_records = manifest.get("artifact_inventory")
    if not isinstance(artifact_records, list):
        fail("delivery artifact inventory is not a list")
    if [item.get("id") for item in artifact_records if isinstance(item, dict)] \
            != EXPECTED_ARTIFACT_IDS or len(artifact_records) != len(EXPECTED_ARTIFACT_IDS):
        fail("delivery artifact inventory changed")
    if manifest.get("install_sequence") != profile.get("install_sequence"):
        fail("delivery manifest install sequence differs from profile")
    if manifest.get("rollback_sequence") != profile.get("rollback_sequence"):
        fail("delivery manifest rollback sequence differs from profile")
    if manifest.get("empty_interfaces") != profile.get("empty_interfaces"):
        fail("delivery manifest empty interfaces differ from profile")
    if manifest.get("validation_commands") != profile.get("validation_commands"):
        fail("delivery manifest validation commands differ from profile")
    if manifest.get("prohibited_operations") != profile.get("prohibited_operations"):
        fail("delivery manifest prohibited operations differ from profile")
    if not re.fullmatch(r"[0-9a-f]{7,40}", str(manifest.get("source_git_commit", ""))):
        fail("delivery manifest source commit is invalid")

    for path in bundle_dir.rglob("*"):
        if path.is_symlink():
            fail(f"delivery bundle must not contain symbolic links: {path}")

    expected_checksums: dict[str, str] = {}
    for line in checksum_path.read_text(encoding="ascii").splitlines():
        digest, separator, relative = line.partition("  ")
        if separator != "  " or not re.fullmatch(r"[0-9a-f]{64}", digest):
            fail(f"invalid SHA256SUMS line: {line}")
        validate_relative_path(relative, "SHA256SUMS path")
        if relative in expected_checksums:
            fail(f"duplicate SHA256SUMS path: {relative}")
        expected_checksums[relative] = digest
    actual_paths = {
        path.relative_to(bundle_dir).as_posix()
        for path in bundle_dir.rglob("*")
        if path.is_file() and path.name != "SHA256SUMS"
    }
    if set(expected_checksums) != actual_paths:
        fail("SHA256SUMS does not cover exactly every bundle file")
    for relative, digest in expected_checksums.items():
        if sha256(rooted_path(bundle_dir, relative, "SHA256SUMS path")) != digest:
            fail(f"bundle checksum mismatch: {relative}")

    for profile_item, record in zip(profile["artifacts"], artifact_records):
        for key in ("id", "kind", "required", "install_order", "bundle_path"):
            if record.get(key) != profile_item.get(key):
                fail(f"artifact manifest/profile mismatch for {profile_item['id']}: {key}")
        if profile_item["kind"] == "apk":
            if record.get("package_name") != profile_item.get("package_name"):
                fail(f"artifact package mismatch for {profile_item['id']}")
            if record.get("min_sdk") != str(profile_item.get("expected_min_sdk")):
                fail(f"artifact minSdk mismatch for {profile_item['id']}")

    support_records = manifest.get("support_inventory")
    if not isinstance(support_records, list):
        fail("manifest section is not a list: support_inventory")
    expected_support_paths = [item["bundle_path"] for item in profile["support_files"]]
    if [item.get("bundle_path") for item in support_records] != expected_support_paths:
        fail("support manifest/profile inventory mismatch")

    for section in ("artifact_inventory", "support_inventory"):
        records = manifest.get(section)
        if not isinstance(records, list):
            fail(f"manifest section is not a list: {section}")
        for record in records:
            if not isinstance(record, dict):
                fail(f"manifest record is not an object: {section}")
            path = rooted_path(bundle_dir, record.get("bundle_path"), section)
            if not path.is_file():
                fail(f"manifest file missing: {record['bundle_path']}")
            if not isinstance(record.get("size_bytes"), int) or record["size_bytes"] < 0:
                fail(f"manifest size is invalid: {record['bundle_path']}")
            if not re.fullmatch(r"[0-9a-f]{64}", str(record.get("sha256", ""))):
                fail(f"manifest hash is invalid: {record['bundle_path']}")
            if path.stat().st_size != record["size_bytes"]:
                fail(f"manifest size mismatch: {record['bundle_path']}")
            if sha256(path) != record["sha256"]:
                fail(f"manifest hash mismatch: {record['bundle_path']}")

    signing = manifest.get("signing")
    if not isinstance(signing, dict):
        fail("delivery manifest signing record is missing")
    if signing.get("signer_cohort_verified") is not True:
        fail("delivery signer cohort is unverified")
    if signing.get("debug_signer_detected") is not True:
        fail("current handoff bundle must remain explicitly debug-signed")
    if signing.get("production_resigning_required") is not True:
        fail("delivery manifest lost production re-signing requirement")
    for key, value in profile["signing"].items():
        if signing.get(key) != value:
            fail(f"delivery manifest signing contract differs from profile: {key}")

    apk_records = [item for item in artifact_records if item.get("kind") == "apk"]
    apk_signers = {item.get("signer_sha256") for item in apk_records}
    if apk_signers != {signing.get("current_signer_sha256")}:
        fail("delivery APK signer records differ from the signer cohort")

    client = next(
        item
        for item in manifest["artifact_inventory"]
        if item["id"] == "client2-demo"
    )
    if client.get("secondary_sdk_dex_present") is not True:
        fail("delivery manifest lost Client2 SDK dex evidence")
    if any(
        item.get("native_library_payload_present") is not False
        for item in manifest["artifact_inventory"]
    ):
        fail("delivery artifact inventory unexpectedly contains native payload")

    print("delivery_bundle_verified=true")
    print("software_handoff_ready=true")
    print("r7_application_integration_complete=true")
    print("production_ready=false")
    print("target_system_integration_owner_resolved=false")
    print("target_hardware_validated=false")
    print("artifact_count=4")
    print("preserved_blocker_count=7")
    print("signer_cohort_verified=true")
    print("debug_signer_detected=true")
    print("production_resigning_required=true")
    print("native_library_payload_present=false")
    print("client2_secondary_sdk_dex_present=true")
    print("hardware_accessed=false")
    print("driver_development_triggered=false")
    print("virtualization_development_triggered=false")


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser()
    subcommands = root.add_subparsers(dest="command", required=True)

    build = subcommands.add_parser("build")
    build.add_argument("--repo-root", required=True)
    build.add_argument("--profile", required=True)
    build.add_argument("--output-dir", required=True)
    build.add_argument("--git-commit", required=True)
    build.add_argument("--aapt", required=True)
    build.add_argument("--apksigner", required=True)
    build.set_defaults(handler=build_bundle)

    verify = subcommands.add_parser("verify")
    verify.add_argument("--bundle-dir", required=True)
    verify.set_defaults(handler=verify_bundle)
    return root


def main() -> int:
    args = parser().parse_args()
    args.handler(args)
    return 0


if __name__ == "__main__":
    sys.exit(main())
