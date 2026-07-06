#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROFILE="$ROOT_DIR/central-brain/deploy/linux/central-brain.package-profile.json"
ENV_FILE="$ROOT_DIR/central-brain/deploy/linux/central-brain.env.example"
SYSTEMD_DIR="$ROOT_DIR/central-brain/deploy/linux/systemd"

if [[ ! -f "$PROFILE" ]]; then
  echo "missing Linux package profile: central-brain/deploy/linux/central-brain.package-profile.json" >&2
  exit 1
fi

python3 - "$ROOT_DIR" "$PROFILE" "$ENV_FILE" "$SYSTEMD_DIR" <<'PY'
import json
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
profile_path = pathlib.Path(sys.argv[2])
env_path = pathlib.Path(sys.argv[3])
systemd_dir = pathlib.Path(sys.argv[4])

profile = json.loads(profile_path.read_text(encoding="utf-8"))
env_text = env_path.read_text(encoding="utf-8")

required_profile_ids = {
    "DEL-002",
    "DEL-003",
    "DEL-004",
    "XSC-005",
    "XSC-006",
    "NV-P-002",
    "NV-P-003",
    "NV-G-007",
}
missing = sorted(required_profile_ids - set(profile.get("req_ids", [])))
if missing:
    raise SystemExit(f"package profile missing Req IDs: {', '.join(missing)}")

target = profile.get("target", {})
if target.get("install_root") != "/opt/central-brain/appDev":
    raise SystemExit("package profile install_root must match systemd WorkingDirectory")
if target.get("environment_file") != "/etc/central-brain/central-brain.env":
    raise SystemExit("package profile environment_file must match systemd EnvironmentFile")
if target.get("service_user") != "centralbrain" or target.get("service_group") != "centralbrain":
    raise SystemExit("package profile service identity must be centralbrain:centralbrain")

for env_name in profile.get("required_environment", []):
    if f"{env_name}=" not in env_text:
        raise SystemExit(f"missing required environment variable in env example: {env_name}")

hardening = profile.get("hardening_required", [])
services = profile.get("services", [])
if len(services) != 4:
    raise SystemExit("package profile must cover the four current Linux services")

for service in services:
    unit_name = service["unit"]
    unit_path = systemd_dir / unit_name
    if not unit_path.exists():
        raise SystemExit(f"missing systemd unit declared by package profile: {unit_name}")
    unit_text = unit_path.read_text(encoding="utf-8")

    expected = [
        "User=centralbrain",
        "Group=centralbrain",
        "WorkingDirectory=/opt/central-brain/appDev",
        "EnvironmentFile=-/etc/central-brain/central-brain.env",
        "LogsDirectory=central-brain",
        service["exec_contains"],
    ]
    expected.extend(hardening)

    for pattern in expected:
        if pattern not in unit_text:
            raise SystemExit(f"{unit_name} missing package-profile pattern: {pattern}")

    if service.get("requires_runtime_directory") and "RuntimeDirectory=central-brain" not in unit_text:
        raise SystemExit(f"{unit_name} must declare RuntimeDirectory=central-brain")

    write_paths = "ReadWritePaths=" + " ".join(service.get("write_paths", []))
    if write_paths not in unit_text:
        raise SystemExit(f"{unit_name} ReadWritePaths does not match package profile")

    if not set(service.get("req_ids", [])):
        raise SystemExit(f"{unit_name} has no Req ID traceability")

non_goals = " ".join(profile.get("non_goals", []))
for boundary in ("Driver/HAL", "virtualization", "production package manager"):
    if boundary not in non_goals:
        raise SystemExit(f"package profile must state non-goal boundary: {boundary}")

print("Central Brain Linux package profile check passed")
PY
