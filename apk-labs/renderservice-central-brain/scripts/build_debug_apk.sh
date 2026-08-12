#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PROJECT_DIR="$ROOT_DIR/apk-labs/renderservice-central-brain"
SOURCE_APK="${CENTRAL_BRAIN_RENDERSERVICE_SOURCE_APK:-$ROOT_DIR/apks/original/service_20260306_171242.apk}"
EXPECTED_SHA256="a24fbb471399e152c172455afbaee92e311f24f63910b263a577d58d59d7c633"
OUT_DIR="$ROOT_DIR/builds/renderservice-central-brain/vendor-baseline"
OUTPUT_APK="$OUT_DIR/renderservice-vendor-baseline.apk"

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/env.sh"
fi

[[ -f "$SOURCE_APK" ]] || { echo "Missing original RenderService APK: $SOURCE_APK" >&2; exit 1; }
SOURCE_SHA256="$(sha256sum "$SOURCE_APK" | awk '{print $1}')"
[[ "$SOURCE_SHA256" == "$EXPECTED_SHA256" ]] || {
  echo "RenderService vendor baseline hash mismatch" >&2
  exit 1
}

mkdir -p "$OUT_DIR"
cp -f "$SOURCE_APK" "$OUTPUT_APK"
OUTPUT_SHA256="$(sha256sum "$OUTPUT_APK" | awk '{print $1}')"
[[ "$OUTPUT_SHA256" == "$EXPECTED_SHA256" ]] || {
  echo "RenderService passthrough output differs from vendor baseline" >&2
  exit 1
}

apksigner verify --verbose --print-certs "$OUTPUT_APK" >/dev/null
aapt dump badging "$OUTPUT_APK" | rg -q "package: name='com.tuanjie.renderservice'"
"$PROJECT_DIR/scripts/verify_project.sh"

printf '%s\n' \
  "renderservice_vendor_baseline_preserved=true" \
  "renderservice_unity_bundle_modified=false" \
  "renderservice_apk_sha256=$OUTPUT_SHA256" \
  "vendor APK: $OUTPUT_APK"
