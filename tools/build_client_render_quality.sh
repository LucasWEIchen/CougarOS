#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec "$ROOT_DIR/apk-labs/tuanjie-client-render-quality/scripts/build_client_debug_apk.sh" "$@"
