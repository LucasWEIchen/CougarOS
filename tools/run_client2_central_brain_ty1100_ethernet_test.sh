#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

ANDROID_SERIAL="${ANDROID_SERIAL:-0123456789ABCDEF}" \
CENTRAL_BRAIN_CLIENT2_MODEL_ROUTE=target_ty1100_vllm_ethernet \
CENTRAL_BRAIN_CLIENT2_SCENARIO="${CENTRAL_BRAIN_CLIENT2_SCENARIO:-cold}" \
  "$ROOT/tools/run_client2_central_brain_openclaw_development_test.sh"
