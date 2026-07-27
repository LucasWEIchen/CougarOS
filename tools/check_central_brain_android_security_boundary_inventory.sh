#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SAF-001, S2-TOL-001, S2-SES-001, S2-MDL-001, S2-OBS-001,
# DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_p9_security_boundary_inventory.json"
AIDL_ROOT="central-brain/android-runtime/central-brain-sdk/src/main/aidl"
IMPLEMENTATION="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/security/SecurityBoundaryInventoryContract.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/security/SecurityBoundaryInventoryContractTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/StructuredModelOutputProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
INSTALLER="tools/install_central_brain_android_runtime.sh"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
DOC="docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md"

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
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P9-W03c marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$CONTRACT" "$IMPLEMENTATION" "$TEST" "$PROBE" "$DEBUG_MANIFEST" \
    "$MAIN_MANIFEST" "$INSTALLER" "$RUNTIME" "$GOVERNANCE" "$DOC"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P9-W03c file missing: $file" >&2; exit 1; }
done

python3 - "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$AIDL_ROOT" \
    "$ROOT_DIR/$IMPLEMENTATION" "$ROOT_DIR/$TEST" <<'PY'
import json
import pathlib
import re
import sys

contract_path, aidl_root, java_path, test_path = map(pathlib.Path, sys.argv[1:])
contract = json.loads(contract_path.read_text(encoding="utf-8"))
java = java_path.read_text(encoding="utf-8")
test = test_path.read_text(encoding="utf-8")

if contract.get("schema_version") != "1.0.0":
    raise SystemExit("P9-W03c inventory schema version changed")
if contract.get("profile_id") != "android13-p9-security-boundary-inventory-v1":
    raise SystemExit("P9-W03c inventory profile changed")
if contract.get("maturity") != "static_inventory_host_aggregate_debug_probe_available":
    raise SystemExit("P9-W03c inventory maturity changed")

actual = []
namespace_counts = {}
for path in sorted(aidl_root.rglob("*.aidl")):
    source = path.read_text(encoding="utf-8")
    relative = path.relative_to(aidl_root).as_posix()
    if re.search(r"^(?:oneway )?interface\s+\w+\s*\{", source, re.MULTILINE):
        kind = "INTERFACE"
        index = 0
    elif re.search(r"^parcelable\s+\w+\s*\{", source, re.MULTILINE):
        kind = "PARCELABLE"
        index = 1
    else:
        raise SystemExit(f"P9-W03c unclassified AIDL surface: {relative}")
    actual.append({"path": relative, "kind": kind})
    namespace = relative.split("/")[3]
    namespace_counts.setdefault(namespace, [0, 0])[index] += 1

if contract.get("aidl_surfaces") != actual:
    raise SystemExit("P9-W03c machine inventory differs from public AIDL tree")
if len(actual) != 51 or sum(item["kind"] == "INTERFACE" for item in actual) != 10:
    raise SystemExit("P9-W03c AIDL surface/interface count changed")
if sum(item["kind"] == "PARCELABLE" for item in actual) != 41:
    raise SystemExit("P9-W03c AIDL parcelable count changed")
if contract.get("aidl_surface_count") != 51 \
        or contract.get("aidl_interface_count") != 10 \
        or contract.get("aidl_parcelable_count") != 41:
    raise SystemExit("P9-W03c JSON counts changed")

expected_namespaces = {
    "diagnostics": [1, 3],
    "effect": [0, 4],
    "event": [4, 10],
    "governance": [1, 4],
    "orchestration": [1, 6],
    "plan": [0, 4],
    "production": [2, 5],
    "session": [1, 5],
}
if namespace_counts != expected_namespaces:
    raise SystemExit("P9-W03c namespace counts changed")
java_namespaces = {
    name: [int(interface_count), int(parcelable_count)]
    for name, interface_count, parcelable_count in re.findall(
        r'new NamespaceCount\("([a-z_]+)",\s*(\d+),\s*(\d+)\)', java)
}
if java_namespaces != expected_namespaces:
    raise SystemExit("P9-W03c Java and AIDL namespace counts differ")

families = [
    "SESSION_CONTRACT",
    "PLAN_CONTRACT",
    "EVENT_CONTRACT",
    "EFFECT_CONTRACT",
    "CHECKPOINT",
    "SCENARIO_MANIFEST",
    "TOOL_SCHEMA",
    "STRUCTURED_MODEL_OUTPUT",
]
if contract.get("validation_families") != families:
    raise SystemExit("P9-W03c validation families changed")
for family in families:
    if family not in java:
        raise SystemExit(f"P9-W03c Java validation family missing: {family}")

for marker in [
    "publicAidlTreeMatchesTenInterfacesFortyOneParcelablesAndNamespaces",
    "structuredModelOutputRejectsUnknownPathAndOversizeExactly",
    "sessionContractRejectsAggregateUtteranceOversize",
    "inventoryDoesNotClaimProbeExecutionFuzzAndroidOrProductionQualification",
]:
    if marker not in test:
        raise SystemExit(f"P9-W03c JVM coverage marker missing: {marker}")

state = contract.get("claim_state", {})
required_true = {
    "security_aidl_parcel_inventory_complete",
    "security_host_path_oversize_aggregate_verified",
    "security_android_debug_probe_available",
}
required_false = {
    "security_android_debug_probe_executed",
    "security_coverage_guided_fuzz_complete",
    "security_binder_calling_uid_spoof_android_verified",
    "security_package_signature_cryptographically_verified",
    "security_android13_arm64_verified",
    "security_runtime_wired",
    "hardware_accessed",
    "production_ready",
    "target_hardware_validated",
}
if any(state.get(key) is not True for key in required_true):
    raise SystemExit("P9-W03c required static/host/probe availability claim is false")
if any(state.get(key) is not False for key in required_false):
    raise SystemExit("P9-W03c probe execution, fuzz, Android or production claim was raised")
PY

for marker in \
  'AIDL_INTERFACE_COUNT = 10' \
  'AIDL_PARCELABLE_COUNT = 41' \
  'AIDL_SURFACE_COUNT = AIDL_INTERFACE_COUNT + AIDL_PARCELABLE_COUNT' \
  'VALIDATION_FAMILY_COUNT = 8' \
  'isAidlParcelInventoryComplete()' \
  'isHostPathOversizeAggregateVerified()' \
  'isAndroidDebugProbeAvailable()' \
  'isAndroidDebugProbeExecuted()' \
  'isCoverageGuidedFuzzComplete()' \
  'isBinderCallingUidSpoofAndroidVerified()' \
  'isPackageSignatureCryptographicallyVerified()' \
  'isAndroid13Arm64Verified()' \
  'isRuntimeWired()' \
  'isHardwareAccessed()' \
  'isProductionReady()' \
  'isTargetHardwareValidated()'; do
  require_text "$IMPLEMENTATION" "$marker"
done

for marker in \
  'security_boundary_probe_complete=' \
  'model_output_unknown_field_rejected=' \
  'model_output_path_like_identifier_rejected=' \
  'model_output_oversize_rejected=' \
  'session_request_oversize_rejected=' \
  'security_android_debug_probe_available=true' \
  'security_android_debug_probe_executed=true'; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "${marker%=}"
done

require_text "$DEBUG_MANIFEST" '.model.StructuredModelOutputProbeActivity'
if grep -Fq 'StructuredModelOutputProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P9-W03c debug probe leaked into the release manifest" >&2
  exit 1
fi
if grep -Fq 'SecurityBoundaryInventoryContract' "$ROOT_DIR/$RUNTIME" \
    || grep -Fq 'SecurityBoundaryInventoryContract' "$ROOT_DIR/$GOVERNANCE"; then
  echo "P9-W03c metadata inventory was wired into production Services" >&2
  exit 1
fi
if grep -Eiq \
    'android[.]|java[.]io|java[.]nio[.]file|java[.]net|okhttp|https?://|CarPropertyManager|VehicleHal|/proc/|/dev/|ioctl|sysfs' \
    "$ROOT_DIR/$IMPLEMENTATION"; then
  echo "P9-W03c main inventory reads platform, file, network, vehicle, or hardware state" >&2
  exit 1
fi

require_text "$DOC" 'W03D_ANDROID_IDENTITY_VERIFIED / TARGET_FUZZ_PENDING'
require_text "$DOC" 'implementation_stage=P9-W03'
require_text "README.md" 'P9 Security Boundary Inventory'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W03c security boundary inventory and debug probe'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W03c security boundary inventory trace'
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" 'Android P9-W03c Security Boundary Inventory Contract'
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" 'P9-W03c security boundary inventory architecture'
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" 'P9-W03c security boundary inventory detailed design'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'Android P9-W03c Security Boundary Inventory'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W03c Security Boundary Inventory Driver/HAL Boundary'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'DEV-090 P9-W03c static inventory and debug probe availability are not target fuzz evidence'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'ISSUE-050 P9 complete security fuzz evidence is unavailable'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W03c Security Boundary Inventory progress'

printf '%s\n' \
  'Central Brain Android security boundary inventory check passed' \
  'security_aidl_parcel_inventory_complete=true' \
  'security_aidl_interface_count=10' \
  'security_aidl_parcelable_count=41' \
  'security_aidl_surface_count=51' \
  'security_validation_family_count=8' \
  'security_host_path_oversize_aggregate_verified=true' \
  'security_android_debug_probe_available=true' \
  'security_android_debug_probe_executed=true' \
  'security_boundary_probe_android13_arm64_verified=true' \
  'security_coverage_guided_fuzz_complete=false' \
  'security_binder_calling_uid_spoof_android_verified=false' \
  'security_package_signature_cryptographically_verified=false' \
  'security_android13_arm64_verified=false' \
  'security_runtime_wired=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'implementation_stage=P9-W03'
