#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${CENTRAL_BRAIN_PORT:-8787}"

python3 "$ROOT_DIR/central-brain/backend/mock_npu_service.py" --host 0.0.0.0 --port "$PORT"
