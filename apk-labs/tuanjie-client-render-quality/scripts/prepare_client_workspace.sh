#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SOURCE_APK="${TUANJIE_CLIENT_SOURCE_APK:-$ROOT_DIR/apks/original/client_20260306_170919.apk}"
EXPECTED_SHA256="5832ea4d5868ede11b89b150fdefd37453ecee11ddb5ea247abe1cbea8c25d8b"
WORK_DIR="${TUANJIE_CLIENT_WORK_DIR:-$ROOT_DIR/builds/client-render-quality/workdir}"
PATCHER="$ROOT_DIR/apk-labs/tuanjie-client-render-quality/scripts/patch_render_scale.py"

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/env.sh"
fi

case "$WORK_DIR" in
  "$ROOT_DIR"/builds/client-render-quality/*) ;;
  *)
    echo "Refusing to prepare outside builds/client-render-quality: $WORK_DIR" >&2
    exit 2
    ;;
esac

test -f "$SOURCE_APK"
test "$(sha256sum "$SOURCE_APK" | awk '{print $1}')" = "$EXPECTED_SHA256"
rm -rf "$WORK_DIR"
mkdir -p "$(dirname "$WORK_DIR")"
apktool d -f "$SOURCE_APK" -o "$WORK_DIR" >/dev/null
python3 "$PATCHER" \
  --work-dir "$WORK_DIR" \
  --package-name com.tuanjie.urasclient \
  --scale 1.25

echo "Client render-quality workdir: $WORK_DIR"
