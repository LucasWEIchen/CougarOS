#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004/005/006, NV-F-001/011/012,
# NV-G-003/005/006/007, NV-P-002, KH-003/006, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD=true
OUTPUT_ROOT="$ROOT_DIR/builds/central-brain-android-hybrid-delivery"
BUNDLE_NAME="central-brain-android13-hybrid"

usage() {
  cat <<'EOF'
Usage: package_central_brain_android_hybrid_delivery.sh [options]

Options:
  --skip-build         Reuse existing Native/SDK/Runtime/Demo/Client2 artifacts.
  --output-root PATH   Write under this repository's builds directory.
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
  source "$ROOT_DIR/env.sh"
fi
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"
AAPT="${AAPT:-$ANDROID_HOME/build-tools/37.0.0/aapt}"
APKSIGNER="${APKSIGNER:-$ANDROID_HOME/build-tools/37.0.0/apksigner}"
for tool in "$AAPT" "$APKSIGNER"; do
  [[ -x "$tool" ]] || { echo "required hybrid packaging tool unavailable: $tool" >&2; exit 1; }
done

OUTPUT_ROOT="$(realpath -m "$OUTPUT_ROOT")"
case "$OUTPUT_ROOT" in
  "$ROOT_DIR"/builds/*) ;;
  *) echo "hybrid output must remain under $ROOT_DIR/builds" >&2; exit 2 ;;
esac
BUNDLE_DIR="$OUTPUT_ROOT/$BUNDLE_NAME"
ARCHIVE="$OUTPUT_ROOT/$BUNDLE_NAME.tar.gz"

if [[ "$BUILD" == true ]]; then
  bash "$ROOT_DIR/tools/build_client2_central_brain_demo.sh"
fi
bash "$ROOT_DIR/tools/check_central_brain_android_blackbox_preflight.sh"
bash "$ROOT_DIR/tools/verify_central_brain_native_runtime_apk.sh" >/dev/null

rm -rf "$BUNDLE_DIR"
mkdir -p "$BUNDLE_DIR"
rm -f "$ARCHIVE" "$ARCHIVE.sha256"

GIT_COMMIT="$(git -C "$ROOT_DIR" rev-parse HEAD)"
SOURCE_DATE_EPOCH="$(git -C "$ROOT_DIR" show -s --format=%ct HEAD)"
PROFILE="$ROOT_DIR/central-brain/delivery/android-hybrid/central-brain.android-hybrid-delivery-profile.json"

python3 -B "$ROOT_DIR/tools/central_brain_android_hybrid_delivery.py" build \
  --repo-root "$ROOT_DIR" \
  --profile "$PROFILE" \
  --output-dir "$BUNDLE_DIR" \
  --git-commit "$GIT_COMMIT" \
  --source-date-epoch "$SOURCE_DATE_EPOCH" \
  --aapt "$AAPT" \
  --apksigner "$APKSIGNER"
python3 -B "$ROOT_DIR/tools/central_brain_android_hybrid_delivery.py" verify \
  --bundle-dir "$BUNDLE_DIR"

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
  "android_hybrid_delivery_package_ready=true" \
  "hybrid_software_handoff_ready=true" \
  "artifact_count=5" \
  "native_artifact_count=2" \
  "native_runtime_abis=arm64-v8a,x86_64" \
  "signer_cohort_verified=true" \
  "production_ready=false" \
  "physical_controller_evidence_available=false" \
  "target_hardware_validated=false" \
  "bundle_dir=$BUNDLE_DIR" \
  "bundle_archive=$ARCHIVE" \
  "bundle_archive_sha256=$(awk '{print $1}' "$ARCHIVE.sha256")" \
  "source_git_commit=$GIT_COMMIT"
