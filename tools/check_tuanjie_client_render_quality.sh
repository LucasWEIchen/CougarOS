#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-001/004, S2-HMI-006/015, S2-UX-002, DEL-004, P4-R15.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
QUALITY_PROJECT="$ROOT_DIR/apk-labs/tuanjie-client-render-quality"
CLIENT2_PROJECT="$ROOT_DIR/apk-labs/client2-central-brain"
RENDER_PROJECT="$ROOT_DIR/apk-labs/renderservice-central-brain"
PATCHER="$QUALITY_PROJECT/scripts/patch_render_scale.py"
CLIENT2_PREPARE="$CLIENT2_PROJECT/scripts/prepare_workspace.sh"
CLIENT2_COORDINATOR="$CLIENT2_PROJECT/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"

for path in \
  "$PATCHER" \
  "$QUALITY_PROJECT/tuanjie-client-render-quality.project.json" \
  "$CLIENT2_PREPARE" \
  "$CLIENT2_COORDINATOR" \
  "$RENDER_PROJECT/renderservice-central-brain.project.json"; do
  test -f "$path"
done

bash "$QUALITY_PROJECT/scripts/verify_project.sh"
bash "$CLIENT2_PROJECT/scripts/verify_project.sh"
bash "$RENDER_PROJECT/scripts/verify_project.sh"

rg -Fq '"1.25": "0x3fa00000"' "$PATCHER"
rg -Fq 'Central Brain controlled render-quality policy' "$PATCHER"
rg -Fq 'Central Brain render-quality reconnect policy' "$PATCHER"
rg -Fq -- '->mNeedSetRenderScale:Z' "$PATCHER"
if rg -q 'setFixedSize|setOnTouchListener|c2sSendMessage|mTuanjieRenderService' "$PATCHER"; then
  echo 'Render-quality policy must not resize the Surface or bypass vendor APIs' >&2
  exit 1
fi

rg -Fq 'RENDER_QUALITY_PATCHER=' "$CLIENT2_PREPARE"
rg -Fq -- '--package-name com.tuanjie.urasclient2' "$CLIENT2_PREPARE"
rg -Fq -- '--scale 1.25' "$CLIENT2_PREPARE"
if rg -q 'setRenderScale|setFixedSize|setOnTouchListener|c2sSendMessage' \
  "$CLIENT2_COORDINATOR"; then
  echo 'Client2 business coordinator must not own vendor render quality' >&2
  exit 1
fi

python3 - \
  "$QUALITY_PROJECT/tuanjie-client-render-quality.project.json" \
  "$RENDER_PROJECT/renderservice-central-brain.project.json" <<'PY'
import json
import sys

quality = json.load(open(sys.argv[1], encoding="utf-8"))
service = json.load(open(sys.argv[2], encoding="utf-8"))
policy = quality["quality_policy"]
assert policy["surface_size"] == "1920x1080"
assert policy["render_scale"] == 1.25
assert policy["internal_render_target"] == "2400x1350"
assert policy["renderservice_modified"] is False
assert policy["unity_assets_modified"] is False
assert policy["touch_listener_modified"] is False
assert "S2-HMI-015" in quality["requirement_ids"]
assert "P4-R15" in quality["requirement_ids"]
assert service["delivery_mode"] == "vendor_baseline_passthrough"
assert service["implementation"]["render_scale_override_enabled"] is False
assert service["implementation"]["touch_listener_override_enabled"] is False
PY

printf '%s\n' \
  'client_surface_size=1920x1080' \
  'client2_surface_size=1920x1080' \
  'client_render_scale=1.25' \
  'client2_render_scale=1.25' \
  'internal_render_target=2400x1350' \
  'render_scale_reconnect_policy=true' \
  'client2_business_render_control=false' \
  'renderservice_modified=false' \
  'unity_assets_modified=false' \
  'touch_listener_modified=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
