#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PROJECT_DIR="$ROOT_DIR/apk-labs/renderservice-central-brain"
SIGNED_APK="$ROOT_DIR/builds/renderservice-central-brain/signed/renderservice-central-brain.debug.apk"

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/env.sh"
fi

bash -n "$PROJECT_DIR/scripts/build_debug_apk.sh"
bash -n "$PROJECT_DIR/scripts/verify_project.sh"
python -m py_compile \
  "$PROJECT_DIR/scripts/patch_unity_hvac_bundle.py" \
  "$PROJECT_DIR/scripts/build_unaligned_apk.py"
rm -rf "$PROJECT_DIR/scripts/__pycache__"
python -m json.tool "$PROJECT_DIR/renderservice-central-brain.project.json" \
  >/dev/null
rg -q 'CentralBrainDriverTemperature' \
  "$PROJECT_DIR/scripts/patch_unity_hvac_bundle.py"
rg -q 'CentralBrainPassengerTemperature' \
  "$PROJECT_DIR/scripts/patch_unity_hvac_bundle.py"
rg -q '_targetInputDisplay.*=.*1' \
  "$PROJECT_DIR/scripts/patch_unity_hvac_bundle.py"
rg -q '_eventSystemRaycastCheck.*=.*0' \
  "$PROJECT_DIR/scripts/patch_unity_hvac_bundle.py"
rg -q -- '-0.18.*_FaceDilate' \
  "$PROJECT_DIR/scripts/patch_unity_hvac_bundle.py"
rg -q 'launcher_assets_all_c93fe44a4d61e1b9545c50b3baddfbd7.bundle' \
  "$PROJECT_DIR/scripts/build_unaligned_apk.py"

if [[ -f "$SIGNED_APK" ]]; then
  apksigner verify --verbose --print-certs "$SIGNED_APK" >/dev/null
  aapt dump badging "$SIGNED_APK" \
    | rg -q "package: name='com.tuanjie.renderservice'"
  python - "$SIGNED_APK" <<'PY'
import sys
import zipfile

entry = (
    "assets/aa/HMIAndroid/"
    "launcher_assets_all_c93fe44a4d61e1b9545c50b3baddfbd7.bundle"
)
with zipfile.ZipFile(sys.argv[1]) as apk:
    assert entry in apk.namelist()
    assert apk.getinfo(entry).file_size > 10_000_000
PY
fi

echo "RenderService dynamic Unity HVAC and orbit-input patch project verified"
