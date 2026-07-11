#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PROJECT_DIR="$ROOT_DIR/apk-labs/client2-central-brain"
BASELINE_DIR="$ROOT_DIR/reverse/client2/apktool"
WORK_DIR="${CLIENT2_CB_WORK_DIR:-$ROOT_DIR/builds/client2-central-brain/workdir}"

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/env.sh"
fi

case "$WORK_DIR" in
  "$ROOT_DIR"/builds/client2-central-brain/*) ;;
  *)
    echo "Refusing to prepare outside builds/client2-central-brain: $WORK_DIR" >&2
    exit 2
    ;;
esac

if [[ ! -d "$BASELINE_DIR" ]]; then
  echo "Missing decoded Client2 baseline: $BASELINE_DIR" >&2
  exit 1
fi

mkdir -p "$(dirname "$WORK_DIR")"
rm -rf "$WORK_DIR"
cp -a "$BASELINE_DIR" "$WORK_DIR"
rm -rf "$WORK_DIR/build" "$WORK_DIR/dist"

python3 "$PROJECT_DIR/scripts/apply_static_panel_patch.py" \
  --work-dir "$WORK_DIR" \
  --patch-xml "$PROJECT_DIR/patches/main_layout.central_brain_panel.xml"

echo "Client2 Central Brain workdir: $WORK_DIR"
