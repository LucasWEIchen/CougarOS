#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PROJECT_DIR="$ROOT_DIR/apk-labs/renderservice-central-brain"
PROJECT_JSON="$PROJECT_DIR/renderservice-central-brain.project.json"
BUILD_SCRIPT="$PROJECT_DIR/scripts/build_debug_apk.sh"
SOURCE_APK="$ROOT_DIR/apks/original/service_20260306_171242.apk"
OUTPUT_APK="$ROOT_DIR/builds/renderservice-central-brain/vendor-baseline/renderservice-vendor-baseline.apk"
EXPECTED_SHA256="a24fbb471399e152c172455afbaee92e311f24f63910b263a577d58d59d7c633"

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/env.sh"
fi

bash -n "$BUILD_SCRIPT"
bash -n "$PROJECT_DIR/scripts/verify_project.sh"
python3 -m json.tool "$PROJECT_JSON" >/dev/null

rg -Fq '"delivery_mode": "vendor_baseline_passthrough"' "$PROJECT_JSON"
rg -Fq '"unity_asset_patch_enabled": false' "$PROJECT_JSON"
rg -Fq '"render_scale_override_enabled": false' "$PROJECT_JSON"
rg -Fq '"client_render_quality_policy_owner": "apk-labs/tuanjie-client-render-quality"' "$PROJECT_JSON"
rg -Fq '"touch_listener_override_enabled": false' "$PROJECT_JSON"
rg -Fq 'EXPECTED_SHA256="a24fbb471399e152c172455afbaee92e311f24f63910b263a577d58d59d7c633"' "$BUILD_SCRIPT"
rg -Fq 'cp -f "$SOURCE_APK" "$OUTPUT_APK"' "$BUILD_SCRIPT"
if rg -q 'patch_unity_hvac_bundle|build_unaligned_apk|apksigner sign|zipalign' "$BUILD_SCRIPT"; then
  echo "active RenderService build must not rewrite or re-sign the vendor APK" >&2
  exit 1
fi

if [[ -f "$SOURCE_APK" ]]; then
  test "$(sha256sum "$SOURCE_APK" | awk '{print $1}')" = "$EXPECTED_SHA256"
fi
if [[ -f "$OUTPUT_APK" ]]; then
  test "$(sha256sum "$OUTPUT_APK" | awk '{print $1}')" = "$EXPECTED_SHA256"
  apksigner verify --verbose --print-certs "$OUTPUT_APK" >/dev/null
  aapt dump badging "$OUTPUT_APK" | rg -q "package: name='com.tuanjie.renderservice'"
fi

printf '%s\n' \
  'renderservice_vendor_baseline_contract_verified=true' \
  'renderservice_unity_asset_patch_enabled=false' \
  'renderservice_render_scale_override_enabled=false' \
  'renderservice_touch_override_enabled=false'
