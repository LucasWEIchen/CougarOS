#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SYSTEMD_DIR="$ROOT_DIR/central-brain/deploy/linux/systemd"

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq "$pattern" "$path"; then
    echo "missing pattern '$pattern' in ${path#$ROOT_DIR/}" >&2
    exit 1
  fi
}

check_unit() {
  local unit="$1"
  local path="$SYSTEMD_DIR/$unit"
  if [[ ! -f "$path" ]]; then
    echo "missing systemd unit: central-brain/deploy/linux/systemd/$unit" >&2
    exit 1
  fi

  require_text "$path" "User=centralbrain"
  require_text "$path" "Group=centralbrain"
  require_text "$path" "NoNewPrivileges=true"
  require_text "$path" "PrivateTmp=true"
  require_text "$path" "PrivateDevices=true"
  require_text "$path" "ProtectSystem=strict"
  require_text "$path" "ProtectHome=true"
  require_text "$path" "RestrictSUIDSGID=true"
  require_text "$path" "LockPersonality=true"
  require_text "$path" "Environment=PYTHONDONTWRITEBYTECODE=1"
  require_text "$path" "LogsDirectory=central-brain"
  require_text "$path" "ReadWritePaths=/"
}

check_unit "central-brain-backend.service"
check_unit "central-brain-governance.service"
check_unit "central-brain-linux-ipc.service"
check_unit "central-brain-linux-grpc.service"

require_text "$SYSTEMD_DIR/central-brain-governance.service" "RuntimeDirectory=central-brain"
require_text "$SYSTEMD_DIR/central-brain-governance.service" "ReadWritePaths=/run/central-brain /var/log/central-brain"
require_text "$SYSTEMD_DIR/central-brain-linux-ipc.service" "RuntimeDirectory=central-brain"
require_text "$SYSTEMD_DIR/central-brain-linux-ipc.service" "ReadWritePaths=/run/central-brain /var/log/central-brain"
require_text "$SYSTEMD_DIR/central-brain-backend.service" "ReadWritePaths=/var/log/central-brain"
require_text "$SYSTEMD_DIR/central-brain-linux-grpc.service" "ReadWritePaths=/var/log/central-brain"

if command -v systemd-analyze >/dev/null 2>&1; then
  systemd-analyze verify "$SYSTEMD_DIR"/*.service
fi

echo "Central Brain Linux systemd hardening check passed"
