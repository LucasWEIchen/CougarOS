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

copy_smoking_dataset() {
  local category source_dir index number source_file target_file
  local copied=0
  for category in positive negative; do
    if [[ "$category" == "positive" ]]; then
      source_dir="$ROOT_DIR/passenger_smoking_100"
    else
      source_dir="$ROOT_DIR/passenger_unbelted_nonsmoking_100"
    fi
    if [[ ! -d "$source_dir" ]]; then
      echo "Missing smoking evaluation dataset: $source_dir" >&2
      exit 1
    fi
    for index in $(seq 1 100); do
      printf -v number '%03d' "$index"
      source_file="$source_dir/$number.jpg"
      target_file="$WORK_DIR/res/raw/central_brain_smoking_${category}_${number}.jpg"
      if [[ ! -f "$source_file" ]]; then
        echo "Missing smoking evaluation frame: $source_file" >&2
        exit 1
      fi
      cp "$source_file" "$target_file"
      copied=$((copied + 1))
    done
  done
  if [[ "$copied" -ne 200 ]]; then
    echo "Expected 200 bundled smoking evaluation frames, copied $copied" >&2
    exit 1
  fi
  echo "bundled smoking evaluation frames: $copied"
}

copy_smoking_dataset

echo "Client2 Central Brain workdir: $WORK_DIR"
