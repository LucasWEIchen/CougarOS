#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004/005/006, NV-F-001/011/012,
# NV-G-003/006/007, NV-P-002, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD=true
OUTPUT_ROOT="$ROOT_DIR/builds/central-brain-android-delivery"
BUNDLE_NAME="central-brain-android13-handoff"

usage() {
  cat <<'EOF'
Usage: package_central_brain_android_delivery.sh [options]

Options:
  --skip-build         Reuse existing Runtime, Demo, SDK and Client2 artifacts.
  --output-root PATH   Write under this builds directory.
  -h, --help           Show this help.
EOF
}

while (($# > 0)); do
  case "$1" in
    --skip-build)
      BUILD=false
      shift
      ;;
    --output-root)
      [[ $# -ge 2 ]] || { echo "--output-root requires a value" >&2; exit 2; }
      OUTPUT_ROOT="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/env.sh"
fi

: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"
AAPT="${AAPT:-$ROOT_DIR/.tools/android-build-tools-current/aapt}"
APKSIGNER="${APKSIGNER:-$ROOT_DIR/.tools/android-build-tools-current/apksigner}"
for tool in "$AAPT" "$APKSIGNER"; do
  [[ -x "$tool" ]] || { echo "required delivery tool is unavailable: $tool" >&2; exit 1; }
done

OUTPUT_ROOT="$(realpath -m "$OUTPUT_ROOT")"
case "$OUTPUT_ROOT" in
  "$ROOT_DIR"/builds/*) ;;
  *)
    echo "delivery output must remain under $ROOT_DIR/builds" >&2
    exit 2
    ;;
esac
BUNDLE_DIR="$OUTPUT_ROOT/$BUNDLE_NAME"
ARCHIVE="$OUTPUT_ROOT/$BUNDLE_NAME.tar.gz"

if [[ "$BUILD" == true ]]; then
  bash "$ROOT_DIR/tools/build_client2_central_brain_demo.sh"
fi

rm -rf "$BUNDLE_DIR"
mkdir -p "$BUNDLE_DIR"
rm -f "$ARCHIVE" "$ARCHIVE.sha256"

GIT_COMMIT="$(git -C "$ROOT_DIR" rev-parse HEAD)"
python3 "$ROOT_DIR/tools/central_brain_android_delivery.py" build \
  --repo-root "$ROOT_DIR" \
  --profile "$ROOT_DIR/central-brain/delivery/android/central-brain.android-delivery-profile.json" \
  --output-dir "$BUNDLE_DIR" \
  --git-commit "$GIT_COMMIT" \
  --aapt "$AAPT" \
  --apksigner "$APKSIGNER"
python3 "$ROOT_DIR/tools/central_brain_android_delivery.py" verify \
  --bundle-dir "$BUNDLE_DIR"

SOURCE_DATE_EPOCH="$(git -C "$ROOT_DIR" show -s --format=%ct HEAD)"
tar \
  --sort=name \
  --mtime="@$SOURCE_DATE_EPOCH" \
  --owner=0 \
  --group=0 \
  --numeric-owner \
  -C "$OUTPUT_ROOT" \
  -czf "$ARCHIVE" \
  "$BUNDLE_NAME"
sha256sum "$ARCHIVE" >"$ARCHIVE.sha256"

printf '%s\n' \
  "android_delivery_package_ready=true" \
  "software_handoff_ready=true" \
  "production_ready=false" \
  "target_hardware_validated=false" \
  "bundle_dir=$BUNDLE_DIR" \
  "bundle_archive=$ARCHIVE" \
  "bundle_archive_sha256=$(awk '{print $1}' "$ARCHIVE.sha256")" \
  "source_git_commit=$GIT_COMMIT"
