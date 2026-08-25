#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
"$ROOT_DIR/apk-labs/client2-central-brain/scripts/verify_project.sh" "$@"
bash "$ROOT_DIR/tools/check_tuanjie_client_render_quality.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_voice_first_hmi.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_client2_smoking_dataset.sh"
