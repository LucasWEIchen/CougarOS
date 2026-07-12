#!/usr/bin/env python3
"""Build and verify the Android 13 Central Brain hybrid C/Java bundle."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import shutil
import sys
import zipfile
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True

from central_brain_android_delivery import (
    apk_facts,
    fail,
    inventory_file,
    load_json,
    rooted_path,
    sha256,
    validate_relative_path,
)


EXPECTED_ARTIFACT_IDS = [
    "native-runtime",
    "central-brain-sdk",
    "runtime-service",
    "demo-hmi",
    "client2-demo",
]
EXPECTED_INSTALL_SEQUENCE = ["runtime-service", "demo-hmi", "client2-demo"]
EXPECTED_INSTALL_PROFILES = {
    "maintenance": ["runtime-service", "demo-hmi"],
    "client2-demo": ["runtime-service", "demo-hmi", "client2-demo"],
}
EXPECTED_BLOCKERS = [
    "TARGET_SYSTEM_INTEGRATION_OWNER_UNRESOLVED",
    "PRODUCTION_EFFECT_DELIVERY_BLOCKED",
    "PRODUCTION_MODEL_RUNTIME_BLOCKED",
    "PRODUCTION_EVENT_RUNTIME_BLOCKED",
    "PRODUCTION_MEMORY_RUNTIME_BLOCKED",
    "PRODUCTION_SKILL_GOVERNANCE_BLOCKED",
    "TARGET_HARDWARE_NOT_VALIDATED",
]
EXPECTED_STATUS = {
    "hybrid_software_handoff_ready": True,
    "b3_emulator_acceptance_complete": True,
    "physical_controller_evidence_available": False,
    "production_ready": False,
    "target_system_integration_owner_resolved": False,
    "target_hardware_validated": False,
    "hardware_accessed": False,
    "driver_development_triggered": False,
    "virtualization_development_triggered": False,
}
EXPECTED_NATIVE_CONTRACT = {
    "abi_version": 1,
    "library_name": "libcentral_brain_native.so",
    "packaged_abis": ["arm64-v8a", "x86_64"],
    "process_owner": "CentralBrainRuntimeApplication",
    "software_provider_available": False,
    "vendor_npu_provider_available": False,
    "runtime_dispatch_enabled": False,
    "hardware_accessed": False,
}
ELF_MACHINES = {
    "arm64-v8a": (183, "AArch64"),
    "x86_64": (62, "Advanced Micro Devices X86-64"),
}


def validate_target_inputs(path: Path) -> None:
    target = load_json(path)
    if target.get("schema_version") != "1.0.0" or target.get("status") != "template":
        fail("hybrid target input example must be an unresolved 1.0.0 template")
    device = target.get("device")
    if not isinstance(device, dict) or device.get("android_api_expected") != 33:
        fail("hybrid target input template must require Android API 33")
    owners = target.get("owners")
    if not isinstance(owners, dict) or any(value is not None for value in owners.values()):
        fail("hybrid target input template must not guess target owners")
    if target.get("claim_state") != {
        "physical_controller_evidence_available": False,
        "target_system_integration_owner_resolved": False,
        "production_activation_allowed": False,
        "target_hardware_validated": False,
    }:
        fail("hybrid target input template contains an unsupported positive claim")


def validate_profile(profile: dict[str, Any]) -> None:
    if profile.get("profile_version") != "1.0.0":
        fail("hybrid delivery profile_version must be 1.0.0")
    if profile.get("delivery_scope") != "android13-blackbox-hybrid-application-layer":
        fail("hybrid delivery scope changed")
    if profile.get("status") != EXPECTED_STATUS:
        fail("hybrid delivery status changed")
    artifacts = profile.get("artifacts")
    if not isinstance(artifacts, list):
        fail("hybrid artifacts must be a list")
    if [item.get("id") for item in artifacts if isinstance(item, dict)] \
            != EXPECTED_ARTIFACT_IDS or len(artifacts) != len(EXPECTED_ARTIFACT_IDS):
        fail("hybrid artifact IDs/order changed")
    if profile.get("install_sequence") != EXPECTED_INSTALL_SEQUENCE:
        fail("hybrid install sequence changed")
    if profile.get("install_profiles") != EXPECTED_INSTALL_PROFILES:
        fail("hybrid install profiles changed")
    if profile.get("rollback_sequence") != list(reversed(EXPECTED_INSTALL_SEQUENCE)):
        fail("hybrid rollback sequence must reverse install order")
    if profile.get("native_runtime_contract") != EXPECTED_NATIVE_CONTRACT:
        fail("hybrid Native Runtime contract changed")
    if profile.get("preserved_blockers") != EXPECTED_BLOCKERS:
        fail("hybrid blocker order changed")

    interfaces = profile.get("empty_interfaces")
    if not isinstance(interfaces, list) or len(interfaces) != 7:
        fail("hybrid profile must preserve exactly seven blocked integration slots")
    if [item.get("blocker") for item in interfaces] != EXPECTED_BLOCKERS:
        fail("hybrid integration slots do not map exactly to blockers")
    if any(item.get("activation_allowed") is not False for item in interfaces):
        fail("hybrid integration slot unexpectedly permits activation")
    npu_slot = interfaces[2]
    if npu_slot.get("current_native_lifecycle_code_present") is not True \
            or npu_slot.get("vendor_npu_adapter_present") is not False:
        fail("hybrid NPU slot confuses native lifecycle with a vendor adapter")

    signing = profile.get("signing")
    if not isinstance(signing, dict):
        fail("hybrid signing contract is missing")
    if signing.get("current_artifact_class") != "debug":
        fail("hybrid artifacts must remain explicitly debug-signed")
    if signing.get("same_signer_required_for") != EXPECTED_INSTALL_SEQUENCE:
        fail("hybrid signer cohort changed")
    if signing.get("production_resigning_required") is not True:
        fail("hybrid production re-signing must remain required")
    if signing.get("automatic_uninstall_on_signer_mismatch") is not False:
        fail("hybrid delivery must never auto-uninstall on signer mismatch")

    expected_native = {
        "native-runtime": [
            "jni/arm64-v8a/libcentral_brain_native.so",
            "jni/x86_64/libcentral_brain_native.so",
        ],
        "central-brain-sdk": [],
        "runtime-service": [
            "lib/arm64-v8a/libcentral_brain_native.so",
            "lib/x86_64/libcentral_brain_native.so",
        ],
        "demo-hmi": [],
        "client2-demo": [],
    }
    bundle_paths: list[str] = []
    for index, item in enumerate(artifacts):
        if not isinstance(item, dict):
            fail(f"hybrid artifact entry is not an object: {index}")
        validate_relative_path(item.get("source"), f"artifacts[{index}].source")
        bundle_paths.append(validate_relative_path(
            item.get("bundle_path"), f"artifacts[{index}].bundle_path"
        ))
        entries = item.get("expected_native_entries")
        if entries != expected_native[item["id"]]:
            fail(f"hybrid native allowlist changed for {item['id']}")
        expected_policy = "exact" if entries else "forbidden"
        if item.get("native_payload_policy") != expected_policy:
            fail(f"hybrid native payload policy changed for {item['id']}")

    support_files = profile.get("support_files")
    if not isinstance(support_files, list) or len(support_files) < 12:
        fail("hybrid support inventory is incomplete")
    for index, item in enumerate(support_files):
        if not isinstance(item, dict):
            fail(f"hybrid support entry is not an object: {index}")
        source = validate_relative_path(item.get("source"), f"support[{index}].source")
        if source.startswith("central-brain/deploy/linux/"):
            fail("Android hybrid delivery must not include Linux frontend artifacts")
        bundle_paths.append(validate_relative_path(
            item.get("bundle_path"), f"support[{index}].bundle_path"
        ))
    if len(bundle_paths) != len(set(bundle_paths)):
        fail("hybrid bundle paths must be unique")
    if {"DELIVERY-MANIFEST.json", "SHA256SUMS"} & set(bundle_paths):
        fail("hybrid profile must not overwrite generated inventory files")


def inspect_native_payload(
        path: Path,
        expected_entries: list[str],
) -> tuple[list[str], list[dict[str, Any]], bool]:
    try:
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            native_entries = sorted(name for name in names if name.lower().endswith(".so"))
            if native_entries != expected_entries:
                fail(
                    f"native payload mismatch for {path}: "
                    f"{native_entries} != {expected_entries}"
                )
            facts: list[dict[str, Any]] = []
            for entry in native_entries:
                parts = entry.split("/")
                if len(parts) != 3 or parts[1] not in ELF_MACHINES:
                    fail(f"unexpected native ABI path: {entry}")
                data = archive.read(entry)
                if len(data) < 20 or data[:4] != b"\x7fELF":
                    fail(f"native payload is not ELF: {entry}")
                if data[4] != 2 or data[5] != 1:
                    fail(f"native payload must be 64-bit little-endian ELF: {entry}")
                machine = int.from_bytes(data[18:20], byteorder="little", signed=False)
                expected_machine, machine_name = ELF_MACHINES[parts[1]]
                if machine != expected_machine:
                    fail(f"ELF machine mismatch for {entry}: {machine}")
                facts.append({
                    "path": entry,
                    "abi": parts[1],
                    "elf_machine": machine,
                    "elf_machine_name": machine_name,
                })
            return native_entries, facts, "classes2.dex" in names
    except (OSError, zipfile.BadZipFile) as exc:
        fail(f"invalid hybrid ZIP artifact {path}: {exc}")
    raise AssertionError("unreachable")


def validate_profile_command(args: argparse.Namespace) -> None:
    validate_profile(load_json(Path(args.profile)))
    validate_target_inputs(Path(args.target_inputs))
    print("hybrid_delivery_profile_verified=true")
    print("hybrid_delivery_expected_artifact_count=5")
    print("hybrid_delivery_expected_native_artifact_count=2")
    print("hybrid_delivery_target_hardware_validated=false")


def build_bundle(args: argparse.Namespace) -> None:
    repo_root = Path(args.repo_root).resolve()
    bundle_dir = Path(args.output_dir).resolve()
    profile = load_json(Path(args.profile).resolve())
    validate_profile(profile)
    validate_target_inputs(
        repo_root / "central-brain/delivery/android-hybrid/target-inputs.example.json"
    )
    if not re.fullmatch(r"[0-9a-f]{7,40}", args.git_commit):
        fail("hybrid git commit must be a lowercase hexadecimal object ID")
    try:
        source_epoch = int(args.source_date_epoch)
    except ValueError:
        fail("hybrid source date epoch must be an integer")
    if source_epoch < 0:
        fail("hybrid source date epoch must be non-negative")
    if bundle_dir.exists() and any(bundle_dir.iterdir()):
        fail(f"hybrid bundle output directory must be empty: {bundle_dir}")
    bundle_dir.mkdir(parents=True, exist_ok=True)

    artifact_inventory: list[dict[str, Any]] = []
    signer_by_id: dict[str, str] = {}
    signer_dn_by_id: dict[str, str] = {}
    for item in profile["artifacts"]:
        source = rooted_path(repo_root, item["source"], f"artifact {item['id']} source")
        if not source.is_file():
            fail(f"missing hybrid delivery artifact: {source}")
        destination = rooted_path(
            bundle_dir, item["bundle_path"], f"artifact {item['id']} bundle path"
        )
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        entries, native_facts, secondary_dex = inspect_native_payload(
            destination, item["expected_native_entries"]
        )
        record: dict[str, Any] = {
            "id": item["id"],
            "kind": item["kind"],
            "required": item["required"],
            "install_order": item["install_order"],
            **inventory_file(destination, item["bundle_path"]),
            "native_library_payload_present": bool(entries),
            "native_entries": entries,
            "native_abis": [fact["abi"] for fact in native_facts],
            "native_elf": native_facts,
        }
        if item["kind"] == "apk":
            facts = apk_facts(destination, args.aapt, args.apksigner)
            if facts["package_name"] != item["package_name"]:
                fail(f"hybrid APK package mismatch for {item['id']}")
            if facts["min_sdk"] != str(item["expected_min_sdk"]):
                fail(f"hybrid APK minSdk mismatch for {item['id']}")
            record.update(facts)
            signer_by_id[item["id"]] = facts["signer_sha256"]
            signer_dn_by_id[item["id"]] = facts["signer_dn"]
        if item["id"] == "client2-demo":
            if not secondary_dex:
                fail("hybrid Client2 APK is missing classes2.dex")
            record["secondary_sdk_dex_present"] = True
        artifact_inventory.append(record)

    cohort = profile["signing"]["same_signer_required_for"]
    cohort_signers = {signer_by_id[item_id] for item_id in cohort}
    cohort_dns = {signer_dn_by_id[item_id] for item_id in cohort}
    if len(cohort_signers) != 1 or len(cohort_dns) != 1:
        fail("hybrid Runtime, Demo and Client2 must form one signer cohort")
    signer_sha256 = next(iter(cohort_signers))
    signer_dn = next(iter(cohort_dns))

    support_inventory: list[dict[str, Any]] = []
    for item in profile["support_files"]:
        source = rooted_path(repo_root, item["source"], "hybrid support source")
        if not source.is_file():
            fail(f"missing hybrid support file: {source}")
        destination = rooted_path(bundle_dir, item["bundle_path"], "support bundle path")
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        support_inventory.append(inventory_file(destination, item["bundle_path"]))

    generated = dt.datetime.fromtimestamp(source_epoch, tz=dt.timezone.utc).isoformat()
    manifest = {
        "manifest_version": "1.0.0",
        "delivery_id": profile["delivery_id"],
        "delivery_scope": profile["delivery_scope"],
        "generated_at_utc": generated,
        "source_git_commit": args.git_commit,
        "source_date_epoch": source_epoch,
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
        "install_profiles": profile["install_profiles"],
        "rollback_sequence": profile["rollback_sequence"],
        "native_runtime_contract": profile["native_runtime_contract"],
        "empty_interfaces": profile["empty_interfaces"],
        "preserved_blockers": profile["preserved_blockers"],
        "validation_commands": profile["validation_commands"],
        "prohibited_operations": profile["prohibited_operations"],
    }
    manifest_path = bundle_dir / "DELIVERY-MANIFEST.json"
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=True, indent=2) + "\n", encoding="utf-8"
    )
    checksum_paths = sorted(path for path in bundle_dir.rglob("*") if path.is_file())
    (bundle_dir / "SHA256SUMS").write_text(
        "\n".join(
            f"{sha256(path)}  {path.relative_to(bundle_dir).as_posix()}"
            for path in checksum_paths
        ) + "\n",
        encoding="ascii",
    )
    print(f"hybrid_delivery_manifest={manifest_path}")
    print("hybrid_artifact_count=5")
    print("hybrid_native_artifact_count=2")
    print("hybrid_signer_cohort_verified=true")


def verify_bundle(args: argparse.Namespace) -> None:
    bundle_dir = Path(args.bundle_dir).resolve()
    manifest_path = bundle_dir / "DELIVERY-MANIFEST.json"
    checksum_path = bundle_dir / "SHA256SUMS"
    profile_path = bundle_dir / "contracts/central-brain.android-hybrid-delivery-profile.json"
    target_path = bundle_dir / "contracts/target-inputs.example.json"
    for path in (manifest_path, checksum_path, profile_path, target_path):
        if not path.is_file():
            fail(f"hybrid bundle file is missing: {path}")
    profile = load_json(profile_path)
    manifest = load_json(manifest_path)
    validate_profile(profile)
    validate_target_inputs(target_path)
    if manifest.get("manifest_version") != "1.0.0":
        fail("hybrid manifest version changed")
    if manifest.get("delivery_id") != profile.get("delivery_id"):
        fail("hybrid manifest/profile ID mismatch")
    if manifest.get("delivery_scope") != profile.get("delivery_scope"):
        fail("hybrid manifest/profile scope mismatch")
    for field in (
        "status",
        "install_sequence",
        "install_profiles",
        "rollback_sequence",
        "native_runtime_contract",
        "empty_interfaces",
        "preserved_blockers",
        "validation_commands",
        "prohibited_operations",
    ):
        if manifest.get(field) != profile.get(field):
            fail(f"hybrid manifest/profile mismatch: {field}")
    if not re.fullmatch(r"[0-9a-f]{7,40}", str(manifest.get("source_git_commit", ""))):
        fail("hybrid manifest source commit is invalid")
    if not isinstance(manifest.get("source_date_epoch"), int):
        fail("hybrid manifest source date epoch is invalid")

    for path in bundle_dir.rglob("*"):
        if path.is_symlink():
            fail(f"hybrid bundle must not contain symbolic links: {path}")
    expected_checksums: dict[str, str] = {}
    for line in checksum_path.read_text(encoding="ascii").splitlines():
        digest, separator, relative = line.partition("  ")
        if separator != "  " or not re.fullmatch(r"[0-9a-f]{64}", digest):
            fail(f"invalid hybrid SHA256SUMS line: {line}")
        validate_relative_path(relative, "hybrid SHA256SUMS path")
        if relative in expected_checksums:
            fail(f"duplicate hybrid SHA256SUMS path: {relative}")
        expected_checksums[relative] = digest
    actual_paths = {
        path.relative_to(bundle_dir).as_posix()
        for path in bundle_dir.rglob("*")
        if path.is_file() and path.name != "SHA256SUMS"
    }
    if set(expected_checksums) != actual_paths:
        fail("hybrid SHA256SUMS coverage differs from bundle files")
    for relative, digest in expected_checksums.items():
        if sha256(rooted_path(bundle_dir, relative, "checksum path")) != digest:
            fail(f"hybrid checksum mismatch: {relative}")

    records = manifest.get("artifact_inventory")
    if not isinstance(records, list) or len(records) != len(EXPECTED_ARTIFACT_IDS):
        fail("hybrid artifact inventory size changed")
    if [record.get("id") for record in records if isinstance(record, dict)] \
            != EXPECTED_ARTIFACT_IDS:
        fail("hybrid artifact inventory IDs changed")
    for item, record in zip(profile["artifacts"], records):
        if not isinstance(record, dict):
            fail("hybrid artifact record is not an object")
        for key in ("id", "kind", "required", "install_order", "bundle_path"):
            if record.get(key) != item.get(key):
                fail(f"hybrid artifact profile mismatch: {item['id']} {key}")
        path = rooted_path(bundle_dir, record["bundle_path"], "artifact path")
        if not path.is_file() \
                or path.stat().st_size != record.get("size_bytes") \
                or sha256(path) != record.get("sha256"):
            fail(f"hybrid artifact hash/size mismatch: {item['id']}")
        entries, native_facts, secondary = inspect_native_payload(
            path, item["expected_native_entries"]
        )
        if record.get("native_entries") != entries:
            fail(f"hybrid native entry record mismatch: {item['id']}")
        if record.get("native_abis") != [fact["abi"] for fact in native_facts]:
            fail(f"hybrid native ABI record mismatch: {item['id']}")
        if record.get("native_elf") != native_facts:
            fail(f"hybrid native ELF record mismatch: {item['id']}")
        if record.get("native_library_payload_present") is not bool(entries):
            fail(f"hybrid native presence record mismatch: {item['id']}")
        if item["kind"] == "apk":
            if record.get("package_name") != item.get("package_name") \
                    or record.get("min_sdk") != str(item.get("expected_min_sdk")):
                fail(f"hybrid APK metadata record mismatch: {item['id']}")
        if item["id"] == "client2-demo" \
                and (not secondary or record.get("secondary_sdk_dex_present") is not True):
            fail("hybrid Client2 SDK dex evidence is missing")

    support_records = manifest.get("support_inventory")
    if not isinstance(support_records, list):
        fail("hybrid support inventory is missing")
    expected_support = [item["bundle_path"] for item in profile["support_files"]]
    if [record.get("bundle_path") for record in support_records] != expected_support:
        fail("hybrid support inventory differs from profile")
    for record in support_records:
        path = rooted_path(bundle_dir, record.get("bundle_path"), "support path")
        if not path.is_file() \
                or path.stat().st_size != record.get("size_bytes") \
                or sha256(path) != record.get("sha256"):
            fail(f"hybrid support hash/size mismatch: {record.get('bundle_path')}")

    signing = manifest.get("signing")
    if not isinstance(signing, dict) \
            or signing.get("signer_cohort_verified") is not True \
            or signing.get("debug_signer_detected") is not True:
        fail("hybrid signer cohort evidence is invalid")
    for key, value in profile["signing"].items():
        if signing.get(key) != value:
            fail(f"hybrid signing contract differs from profile: {key}")
    apk_signers = {
        record.get("signer_sha256")
        for record in records
        if record.get("kind") == "apk"
    }
    if apk_signers != {signing.get("current_signer_sha256")}:
        fail("hybrid APK signer records differ from signer cohort")

    native_ids = [
        record["id"] for record in records
        if record.get("native_library_payload_present") is True
    ]
    if native_ids != ["native-runtime", "runtime-service"]:
        fail("hybrid native artifact set changed")

    print("hybrid_delivery_bundle_verified=true")
    print("hybrid_software_handoff_ready=true")
    print("b3_emulator_acceptance_complete=true")
    print("production_ready=false")
    print("physical_controller_evidence_available=false")
    print("target_hardware_validated=false")
    print("artifact_count=5")
    print("native_artifact_count=2")
    print("native_runtime_abis=arm64-v8a,x86_64")
    print("native_runtime_abi_version=1")
    print("signer_cohort_verified=true")
    print("client2_secondary_sdk_dex_present=true")
    print("native_vendor_npu_provider_available=false")
    print("native_runtime_dispatch_enabled=false")
    print("hardware_accessed=false")
    print("driver_development_triggered=false")
    print("virtualization_development_triggered=false")


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser()
    commands = root.add_subparsers(dest="command", required=True)

    validate = commands.add_parser("validate-profile")
    validate.add_argument("--profile", required=True)
    validate.add_argument("--target-inputs", required=True)
    validate.set_defaults(handler=validate_profile_command)

    build = commands.add_parser("build")
    build.add_argument("--repo-root", required=True)
    build.add_argument("--profile", required=True)
    build.add_argument("--output-dir", required=True)
    build.add_argument("--git-commit", required=True)
    build.add_argument("--source-date-epoch", required=True)
    build.add_argument("--aapt", required=True)
    build.add_argument("--apksigner", required=True)
    build.set_defaults(handler=build_bundle)

    verify = commands.add_parser("verify")
    verify.add_argument("--bundle-dir", required=True)
    verify.set_defaults(handler=verify_bundle)
    return root


def main() -> int:
    args = parser().parse_args()
    args.handler(args)
    return 0


if __name__ == "__main__":
    sys.exit(main())
