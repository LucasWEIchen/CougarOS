#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004/005/006, NV-F-011/012, NV-P-002, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEST="tools/test_central_brain_android_target_deployment.sh"
INSTALLER="tools/install_central_brain_android_runtime.sh"
DOCUMENT="docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android target deployment file: $path" >&2
    exit 1
  fi
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
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android target deployment pattern '$pattern' in $path" >&2
    exit 1
  fi
}

for path in "$TEST" "$INSTALLER" "$DOCUMENT"; do
  require_file "$path"
done

require_text "$TEST" "--skip-install-gate"
require_text "$TEST" "--require-api-33"
require_text "$TEST" '[[ "$SDK" == "33" ]]'
require_text "$TEST" "manifest_value application-id"
require_text "$TEST" "manifest_value min-sdk"
require_text "$TEST" "manifest_value target-sdk"
require_text "$TEST" "manifest permissions"
require_text "$TEST" "android.permission.INTERNET"
require_text "$TEST" "pm path com.centralbrain.runtime"
require_text "$TEST" "pm path com.centralbrain.demo"
require_text "$TEST" "package:/data/app/"
require_text "$TEST" "pkgFlags="
require_text "$TEST" "userId="
require_text "$TEST" "certificate SHA-256 digest"
require_text "$TEST" "sha256sum"
require_text "$TEST" "dumpsys activity service"

for marker in \
  "target_deployment_preflight_verified=true" \
  "android_api_33_verified=true" \
  "application_layer_only_verified=true" \
  "data_app_install_verified=true" \
  "system_app_required=false" \
  "privileged_app_required=false" \
  "vendor_aosp_bsp_modified=false" \
  "system_partition_write_capability=false" \
  "internet_permission_requested=false" \
  "native_library_payload_present=false" \
  "signature_protected_service_count=3" \
  "runtime_demo_signer_match=true" \
  "production_inference_allowed=false" \
  "vendor_npu_provider_available=false" \
  "hardware_accessed=false" \
  "driver_development_triggered=false" \
  "virtualization_development_triggered=false" \
  "target_hardware_validated=false"; do
  require_text "$TEST" "$marker"
  require_text "$DOCUMENT" "$marker"
done
require_text "$TEST" 'EVIDENCE_SCOPE="api33-emulator-application-layer"'
require_text "$TEST" 'EVIDENCE_SCOPE="api33-device-application-layer"'
require_text "$DOCUMENT" "real_target_application_acceptance_required=true"

if grep -Eiq \
    '(^|[[:space:]])(fastboot|flash|remount|disable-verity)([[:space:]]|$)|adb[[:space:]]+root|shell[[:space:]]+(mount|su)([[:space:]]|$)|push[[:space:]].*/(system|vendor)' \
    "$ROOT_DIR/$TEST" "$ROOT_DIR/$INSTALLER"; then
  echo "Android target deployment tooling must not modify system/vendor partitions" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "R5D1 Android 13 Application-Layer Deployment Acceptance"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "R5D1 Android 13 application-layer deployment acceptance"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R5D1 Android 13 application-layer deployment acceptance trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android R5D1 Target Deployment Evidence Contract"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android R5D1 Application-Layer Target Deployment Acceptance"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R5D1 Application-Layer Deployment Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R5D1 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R5D1 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "R5D1 application-layer deployment"

echo "Central Brain Android target deployment acceptance check passed"
