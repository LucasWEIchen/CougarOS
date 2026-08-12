#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

CENTRAL_BRAIN_CLIENT2_MODEL_ROUTE=development_ty1100_vllm \
  CENTRAL_BRAIN_CLIENT2_SCENARIO="${CENTRAL_BRAIN_CLIENT2_SCENARIO:-smoking}" \
  ANDROID_SERIAL="${ANDROID_SERIAL:-testboard}" \
  ADB_SERVER_PORT="${ADB_SERVER_PORT:-5038}" \
  "$ROOT/tools/run_client2_central_brain_openclaw_development_test.sh"
