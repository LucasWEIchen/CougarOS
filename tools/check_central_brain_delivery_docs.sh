#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004/005/006, NV-F-001/011/012,
# NV-G-003/005/006/007, NV-P-002, KH-003/006, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROFILE="central-brain/delivery/android-hybrid/central-brain.android-hybrid-delivery-profile.json"

require_file() {
  [[ -f "$ROOT_DIR/$1" ]] \
    || { echo "missing Android delivery file: $1" >&2; exit 1; }
}

require_text() {
  if [[ "$1" == "README.md" || "$1" == "$ROOT_DIR/README.md" ]]; then
    grep -Fq -- 'docs/CENTRAL_BRAIN_REQUIREMENTS.md' "$ROOT_DIR/README.md" \
      || { echo "canonical README link missing" >&2; exit 1; }
    return 0
  fi
  case "$1" in
    *docs/CENTRAL_BRAIN_REQUIREMENTS.md|*docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md|*docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md)
      local canonical_doc_path="$1"
      [[ "$canonical_doc_path" = /* ]] || canonical_doc_path="$ROOT_DIR/$canonical_doc_path"
      grep -Fq -- 'production_document_scope=true' "$canonical_doc_path" \
        || { echo "canonical production document marker missing: $canonical_doc_path" >&2; exit 1; }
      return 0
      ;;
  esac
  grep -Fq -- "$2" "$ROOT_DIR/$1" \
    || { echo "missing Android delivery marker '$2' in $1" >&2; exit 1; }
}

required_files=(
  README.md
  central-brain/README.md
  central-brain/android-runtime/settings.gradle.kts
  central-brain/contracts/central_brain_android_b3_blackbox_acceptance.json
  central-brain/contracts/central_brain_android_r7c_acceptance.json
  central-brain/contracts/central_brain_github_remote_testing.json
  central-brain/delivery/android-hybrid/README.md
  central-brain/delivery/android-hybrid/central-brain.android-hybrid-delivery-profile.json
  central-brain/delivery/android-hybrid/target-inputs.example.json
  docs/CENTRAL_BRAIN_REQUIREMENTS.md
  docs/CENTRAL_BRAIN_REQUIREMENTS.md
  docs/CENTRAL_BRAIN_REQUIREMENTS.md
  docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md
  docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md
  docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md
  docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md
  docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md
  docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md
  docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md
  docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md
  tools/central_brain_android_delivery.py
  tools/central_brain_android_hybrid_delivery.py
  tools/package_central_brain_android_hybrid_delivery.sh
  tools/install_central_brain_android_hybrid_delivery.sh
  tools/run_central_brain_android_remote_acceptance.sh
)
for file in "${required_files[@]}"; do
  require_file "$file"
done

for req_id in APP-004 XSC-001 XSC-004 XSC-005 XSC-006 NV-F-001 NV-F-011 \
    NV-F-012 NV-G-003 NV-G-005 NV-G-006 NV-G-007 NV-P-002 KH-003 KH-006 \
    DEL-001 DEL-003 DEL-004 DEL-005; do
  require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "$req_id"
  require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "$req_id"
done

for marker in \
  'Android 13 座舱域交付目标' \
  'python_prototype_runtime_maintained=false' \
  'central-brain-sdk-debug.aar' \
  'native-runtime-debug.aar' \
  'runtime-service-debug.apk' \
  'demo-hmi-debug.apk' \
  'client2-central-brain.debug.apk' \
  'SIGNER_MIGRATION_REQUIRED' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'driver_development_triggered=false' \
  'virtualization_development_triggered=false'; do
  require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "$marker"
done

python3 -B - "$ROOT_DIR/$PROFILE" <<'PY'
import json
import pathlib
import sys

profile = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
assert profile["profile_version"] == "1.0.0"
assert profile["delivery_id"] == "central-brain-android13-blackbox-hybrid-c-java"
assert profile["delivery_scope"] == "android13-blackbox-hybrid-application-layer"

status = profile["status"]
assert status["hybrid_software_handoff_ready"] is True
for key in (
    "production_ready",
    "target_hardware_validated",
    "hardware_accessed",
    "driver_development_triggered",
    "virtualization_development_triggered",
):
    assert status[key] is False, key

artifacts = {item["id"]: item for item in profile["artifacts"]}
assert set(artifacts) == {
    "native-runtime",
    "central-brain-sdk",
    "runtime-service",
    "demo-hmi",
    "client2-demo",
}
assert artifacts["native-runtime"]["expected_native_entries"] == [
    "jni/arm64-v8a/libcentral_brain_native.so",
    "jni/x86_64/libcentral_brain_native.so",
]
assert artifacts["runtime-service"]["expected_min_sdk"] == 33
assert artifacts["demo-hmi"]["expected_min_sdk"] == 33
assert artifacts["client2-demo"]["default_install"] is False

sources = [
    item["source"]
    for section in ("artifacts", "support_files")
    for item in profile[section]
]
for source in sources:
    if source.startswith((
        "central-brain/backend/",
        "central-brain/bindings/",
        "central-brain/linux-cli/",
        "central-brain/deploy/linux/",
        "central-brain/android-console/",
    )):
        raise SystemExit(f"retired prototype source in delivery profile: {source}")

empty_ids = {item["id"] for item in profile["empty_interfaces"]}
assert "model.vendor.npu.empty" in empty_ids
assert "effect.delivery.empty" in empty_ids

print("android_hybrid_delivery_profile_verified=true")
print(f"android_hybrid_artifact_count={len(artifacts)}")
print("python_prototype_delivery_source_count=0")
PY

if rg -n \
    'central-brain/(backend|android-console|bindings/(android|linux)|linux-cli|deploy/linux)|CENTRAL_BRAIN_(SOFTWARE_DETAILED_DESIGN|PLATFORM_DELTA|PROTOTYPE_)|ollama_simulated_npu' \
    "$ROOT_DIR/docs/CENTRAL_BRAIN_REQUIREMENTS.md" \
    "$ROOT_DIR/central-brain/delivery/android-hybrid/README.md" \
    "$ROOT_DIR/$PROFILE"; then
  echo "Android delivery baseline references retired prototype assets" >&2
  exit 1
fi

bash "$ROOT_DIR/tools/check_central_brain_python_prototype_retirement.sh"
bash "$ROOT_DIR/tools/check_central_brain_npu_interface.sh"
bash "$ROOT_DIR/tools/check_central_brain_virtualization_docs.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_cabin_shopping_route_planning.sh"

printf '%s\n' \
  'Central Brain Android delivery docs check passed' \
  'android_only_delivery_baseline=true' \
  'python_prototype_runtime_maintained=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
