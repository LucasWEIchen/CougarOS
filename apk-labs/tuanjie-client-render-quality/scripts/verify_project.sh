#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PROJECT_DIR="$ROOT_DIR/apk-labs/tuanjie-client-render-quality"
WORK_DIR="${TUANJIE_CLIENT_WORK_DIR:-$ROOT_DIR/builds/client-render-quality/workdir}"
PROJECT_JSON="$PROJECT_DIR/tuanjie-client-render-quality.project.json"
PATCHER="$PROJECT_DIR/scripts/patch_render_scale.py"
SIGNED_APK="$ROOT_DIR/builds/client-render-quality/signed/client-render-quality.debug.apk"

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/env.sh"
fi

bash -n "$PROJECT_DIR/scripts/prepare_client_workspace.sh"
bash -n "$PROJECT_DIR/scripts/build_client_debug_apk.sh"
bash -n "$PROJECT_DIR/scripts/verify_project.sh"
python3 -m py_compile "$PATCHER"
rm -rf "$PROJECT_DIR/scripts/__pycache__"
python3 -m json.tool "$PROJECT_JSON" >/dev/null

rg -Fq 'APPROVED_SCALES' "$PATCHER"
rg -Fq '"1.25": "0x3fa00000"' "$PATCHER"
rg -Fq 'Lcom/unity3d/renderservice/client/TuanjieView;->setRenderScale(F)V' "$PATCHER"
rg -Fq '"surface_size": "1920x1080"' "$PROJECT_JSON"
rg -Fq '"internal_render_target": "2400x1350"' "$PROJECT_JSON"
rg -Fq '"renderservice_modified": false' "$PROJECT_JSON"
rg -Fq '"touch_listener_modified": false' "$PROJECT_JSON"

if [[ -d "$WORK_DIR" ]]; then
  MAIN_ACTIVITY="$WORK_DIR/smali/com/tuanjie/urasclient/MainActivity.smali"
  TUANJIE_VIEW="$WORK_DIR/smali/com/unity3d/renderservice/client/TuanjieView.smali"
  test -f "$MAIN_ACTIVITY"
  test -f "$TUANJIE_VIEW"
  test "$(rg -c 'Central Brain controlled render-quality policy' "$MAIN_ACTIVITY")" -eq 1
  test "$(rg -c -- '->setRenderScale\(F\)V' "$MAIN_ACTIVITY")" -eq 1
  rg -Fq 'const/high16 v0, 0x3fa00000    # 1.25f' "$MAIN_ACTIVITY"
  test "$(rg -c 'Central Brain render-quality reconnect policy' \
    "$TUANJIE_VIEW")" -eq 1
  rg -Fq -- '->mNeedSetRenderScale:Z' "$TUANJIE_VIEW"
  if rg -q 'setOnTouchListener|setFixedSize|c2sSendMessage' "$MAIN_ACTIVITY"; then
    echo "Client render-quality patch changed a forbidden input or service path" >&2
    exit 1
  fi
fi

if [[ -f "$SIGNED_APK" ]]; then
  apksigner verify --verbose --print-certs "$SIGNED_APK" >/dev/null
  aapt dump badging "$SIGNED_APK" | rg -q "package: name='com.tuanjie.urasclient'"
fi

printf '%s\n' \
  'client_render_quality_patch_verified=true' \
  'client_surface_size_changed=false' \
  'client_touch_path_changed=false' \
  'client_render_scale_reconnect_policy=true' \
  'renderservice_modified=false'
