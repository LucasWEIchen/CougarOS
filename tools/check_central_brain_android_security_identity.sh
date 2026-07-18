#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SAF-001, S2-TOL-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_p9_identity_device_evidence.json"
SERVICE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/security/SecurityIdentityProbeService.java"
RUNTIME_AIDL="central-brain/android-runtime/runtime-service/src/debug/aidl/com/centralbrain/runtime/security/ISecurityIdentityProbe.aidl"
TEST_AIDL="central-brain/android-runtime/central-brain-sdk/src/androidTest/aidl/com/centralbrain/runtime/security/ISecurityIdentityProbe.aidl"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
INSTRUMENTATION="central-brain/android-runtime/central-brain-sdk/src/androidTest/java/com/centralbrain/sdk/session/SessionParcelInstrumentation.java"
DEVICE_TEST="tools/test_central_brain_android_security_identity.sh"
DOCUMENTS=(
  "README.md"
  "central-brain/README.md"
  "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md"
  "docs/CENTRAL_BRAIN_SECURITY_REVIEW_FUZZ.md"
  "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md"
  "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md"
  "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md"
  "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md"
  "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md"
  "docs/CENTRAL_BRAIN_ROADMAP.md"
  "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md"
  "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md"
  "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md"
  "docs/CENTRAL_BRAIN_ANDROID13_PHYSICAL_TARGET_TEST_REPORT.md"
)

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P9-W03d marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$CONTRACT" "$SERVICE" "$RUNTIME_AIDL" "$TEST_AIDL" "$DEBUG_MANIFEST" "$INSTRUMENTATION" "$DEVICE_TEST"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P9-W03d file missing: $file" >&2; exit 1; }
done

python3 - "$ROOT_DIR/$CONTRACT" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    payload = json.load(stream)
if payload.get("schema_version") != "1.0.0":
    raise SystemExit("P9-W03d schema version changed")
if payload.get("profile_id") != "android13-p9-binder-identity-device-evidence-v1":
    raise SystemExit("P9-W03d profile changed")
if payload.get("work_package") != "P9-W03d":
    raise SystemExit("P9-W03d package changed")
if payload.get("maturity") != "android_application_security_evidence":
    raise SystemExit("P9-W03d maturity changed")
probe = payload.get("probe", {})
expected_probe = {
    "runtime_component": "com.centralbrain.runtime.security.SecurityIdentityProbeService",
    "caller_package": "com.centralbrain.sdk.test",
    "transport": "explicit_signature_protected_binder",
    "identity_source": "Binder.getCallingUid",
    "signer_source": "PackageManager.GET_SIGNING_CERTIFICATES",
    "signer_digest": "SHA-256",
    "raw_identity_published": False,
}
if probe != expected_probe:
    raise SystemExit("P9-W03d probe contract changed")
requirements = payload.get("device_requirements", {})
if requirements != {
    "android_api": 33,
    "abi": "arm64-v8a",
    "distinct_process_uids": True,
    "identity_redacted": True,
}:
    raise SystemExit("P9-W03d device requirements changed")
state = payload.get("claim_state", {})
required_true = {
    "security_identity_device_probe_verified",
    "security_distinct_app_uids_verified",
    "security_binder_calling_uid_spoof_android_verified",
    "security_package_signature_cryptographically_verified",
    "security_same_signer_debug_binding_verified",
}
required_false = {
    "security_production_signer_verified",
    "security_coverage_guided_fuzz_complete",
    "security_runtime_wired",
    "hardware_accessed",
    "production_ready",
    "target_hardware_validated",
}
if set(state) != required_true | required_false:
    raise SystemExit("P9-W03d claim set changed")
if any(state[key] is not True for key in required_true):
    raise SystemExit("P9-W03d verified claim is false")
if any(state[key] is not False for key in required_false):
    raise SystemExit("P9-W03d forbidden claim was raised")
PY

cmp -s "$ROOT_DIR/$RUNTIME_AIDL" "$ROOT_DIR/$TEST_AIDL" \
  || { echo "P9-W03d debug/test AIDL drifted" >&2; exit 1; }
for marker in \
  'Binder.getCallingUid()' \
  'resolveCallingIdentity()' \
  'ISecurityIdentityProbe.Stub' \
  'callingUid != spoofedUid' \
  '!hasPackage(identity, spoofedPackage)' \
  '!hasSigner(identity, expectedPackage, spoofedSignerSha256)'; do
  require_text "$SERVICE" "$marker"
done
for marker in verifyCallingUid verifyCallingPackage verifyCallingSigner; do
  require_text "$RUNTIME_AIDL" "$marker"
done
require_text "$DEBUG_MANIFEST" '.security.SecurityIdentityProbeService'
require_text "$DEBUG_MANIFEST" 'android:permission="com.centralbrain.permission.BIND_RUNTIME"'
if grep -Fq 'SecurityIdentityProbeService' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P9-W03d probe service leaked into the main manifest" >&2
  exit 1
fi

for marker in 'securityIdentity' 'currentSignerSha256'; do
  require_text "$INSTRUMENTATION" "$marker"
done
for marker in \
  'security_identity_device_probe_verified=true' \
  'security_distinct_app_uids_verified=true' \
  'security_binder_calling_uid_spoof_android_verified=true' \
  'security_package_signature_cryptographically_verified=true' \
  'security_production_signer_verified=false'; do
  require_text "$INSTRUMENTATION" "$marker"
  require_text "$DEVICE_TEST" "$marker"
done
for document in "${DOCUMENTS[@]}"; do
  require_text "$document" 'P9-W03d'
done
for document in \
  "README.md" \
  "docs/CENTRAL_BRAIN_SECURITY_REVIEW_FUZZ.md" \
  "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" \
  "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" \
  "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" \
  "docs/CENTRAL_BRAIN_ROADMAP.md" \
  "docs/CENTRAL_BRAIN_ANDROID13_PHYSICAL_TARGET_TEST_REPORT.md"; do
  require_text "$document" 'security_identity_device_probe_verified=true'
  require_text "$document" 'security_distinct_app_uids_verified=true'
  require_text "$document" 'security_production_signer_verified=false'
done

printf '%s\n' \
  'Central Brain Android P9-W03d security identity check passed' \
  'security_identity_device_probe_verified=true' \
  'security_distinct_app_uids_verified=true' \
  'security_binder_calling_uid_spoof_android_verified=true' \
  'security_package_signature_cryptographically_verified=true' \
  'security_same_signer_debug_binding_verified=true' \
  'security_production_signer_verified=false' \
  'security_coverage_guided_fuzz_complete=false' \
  'security_runtime_wired=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
