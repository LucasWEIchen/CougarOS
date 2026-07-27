#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MEM-001, S2-SAF-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_p9_privacy_data_inventory.json"
IMPLEMENTATION="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/privacy/PrivacyDataInventoryContract.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/privacy/PrivacyDataInventoryContractTest.java"
MAIN_JAVA="central-brain/android-runtime/runtime-service/src/main/java"
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
    || { echo "P9-W04a marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$CONTRACT" "$IMPLEMENTATION" "$TEST" "$RUNTIME" "$GOVERNANCE" "$DOC"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P9-W04a file missing: $file" >&2; exit 1; }
done

python3 - "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$IMPLEMENTATION" \
    "$ROOT_DIR/$TEST" "$ROOT_DIR/$MAIN_JAVA" <<'PY'
import json
import pathlib
import re
import sys

contract_path, java_path, test_path, main_java = map(pathlib.Path, sys.argv[1:])
contract = json.loads(contract_path.read_text(encoding="utf-8"))
java = java_path.read_text(encoding="utf-8")
test = test_path.read_text(encoding="utf-8")

if contract.get("schema_version") != "1.0.0":
    raise SystemExit("P9-W04a schema version changed")
if contract.get("profile_id") != "android13-p9-privacy-data-inventory-v1":
    raise SystemExit("P9-W04a profile changed")
if contract.get("maturity") != "static_inventory_policy_gaps_explicit":
    raise SystemExit("P9-W04a maturity changed")

expected_ids = [
    "durable.session_event",
    "durable.plan_graph",
    "durable.effect_recovery",
    "durable.approval",
    "durable.audit",
    "durable.event_cursor",
    "memory.working",
    "memory.profile",
    "memory.episodic",
    "events.broker",
    "tools.execution_audit",
    "model.inference_boundary",
]
surfaces = contract.get("surfaces", [])
if [surface.get("id") for surface in surfaces] != expected_ids:
    raise SystemExit("P9-W04a surface order or IDs changed")
if contract.get("surface_count") != 12 \
        or contract.get("durable_surface_count") != 6 \
        or contract.get("process_local_surface_count") != 5 \
        or contract.get("transient_surface_count") != 1 \
        or contract.get("policy_gap_count") != 2 \
        or contract.get("authorized_export_surface_count") != 1:
    raise SystemExit("P9-W04a aggregate counts changed")

pattern = re.compile(
    r'surface\(\s*"([^"]+)",\s*Sensitivity\.([A-Z_]+),'
    r'\s*StorageMode\.([A-Z_]+),\s*ContentForm\.([A-Z_]+),'
    r'\s*OwnerScope\.([A-Z_]+),\s*ConsentMode\.([A-Z_]+),'
    r'\s*RetentionMode\.([A-Z0-9_]+),\s*DeletionMode\.([A-Z_]+),'
    r'\s*ExportMode\.([A-Z_]+),\s*LogMode\.([A-Z_]+),'
    r'\s*EnforcementState\.([A-Z_]+),\s*(true|false),'
    r'\s*((?:"[^"]+"\s*,?\s*)+)\)',
    re.DOTALL,
)
java_surfaces = []
for match in pattern.finditer(java):
    values = list(match.groups())
    sources = re.findall(r'"([^"]+)"', values.pop())
    java_surfaces.append({
        "id": values[0],
        "sensitivity": values[1],
        "storage": values[2],
        "content": values[3],
        "owner": values[4],
        "consent": values[5],
        "retention": values[6],
        "deletion": values[7],
        "export": values[8],
        "log": values[9],
        "enforcement": values[10],
        "accepts_content_payload": values[11] == "true",
        "sources": sources,
    })
if java_surfaces != surfaces:
    raise SystemExit("P9-W04a Java and JSON inventories differ")

for surface in surfaces:
    for source in surface.get("sources", []):
        if not (main_java / source).is_file():
            raise SystemExit(f"P9-W04a inventory source missing: {source}")

gap_ids = [surface["id"] for surface in surfaces if surface["enforcement"] == "POLICY_GAP"]
if gap_ids != ["durable.effect_recovery", "durable.audit"]:
    raise SystemExit("P9-W04a policy gaps changed")
exported = [surface for surface in surfaces if surface["export"] == "AUTHORIZED_BOUNDED"]
if len(exported) != 1 \
        or exported[0]["id"] != "memory.profile" \
        or exported[0]["consent"] != "EXPLICIT_CONSENT":
    raise SystemExit("P9-W04a authorized export boundary changed")
content_ids = [surface["id"] for surface in surfaces if surface["accepts_content_payload"]]
if content_ids != ["memory.working", "memory.profile", "model.inference_boundary"]:
    raise SystemExit("P9-W04a content boundary changed")
if any(surface["log"] != "CONTENT_FORBIDDEN"
       for surface in surfaces if surface["accepts_content_payload"]):
    raise SystemExit("P9-W04a content logging boundary was relaxed")

state = contract.get("claim_state", {})
required_true = {"privacy_data_inventory_complete"}
required_false = {
    "privacy_raw_user_text_persisted",
    "privacy_raw_model_output_persisted",
    "privacy_raw_vehicle_payload_persisted",
    "privacy_location_persisted",
    "privacy_audit_content_logged",
    "privacy_owner_policy_approved",
    "privacy_production_lifecycle_complete",
    "privacy_runtime_lifecycle_wiring_complete",
    "privacy_android13_arm64_verified",
    "hardware_accessed",
    "production_ready",
    "target_hardware_validated",
}
if any(state.get(key) is not True for key in required_true):
    raise SystemExit("P9-W04a inventory claim is false")
if any(state.get(key) is not False for key in required_false):
    raise SystemExit("P9-W04a privacy, Android, hardware or production claim was raised")
if state.get("implementation_stage") != "P9-W04":
    raise SystemExit("P9-W04a implementation stage changed")

for marker in [
    "inventoryHasTwelveExactStorageSurfacesAndExistingSources",
    "ownerApprovedRetentionGapsRemainExplicitAndBounded",
    "contentPayloadSurfacesForbidContentLoggingAndProductionRetentionClaims",
    "onlyExplicitConsentProfileSurfaceAllowsBoundedExport",
    "inventoryDoesNotClaimPolicyApprovalAndroidOrProductionCompletion",
]:
    if marker not in test:
        raise SystemExit(f"P9-W04a JVM marker missing: {marker}")
PY

for marker in \
  'SURFACE_COUNT = 12' \
  'DURABLE_SURFACE_COUNT = 6' \
  'PROCESS_LOCAL_SURFACE_COUNT = 5' \
  'TRANSIENT_SURFACE_COUNT = 1' \
  'POLICY_GAP_COUNT = 2' \
  'AUTHORIZED_EXPORT_SURFACE_COUNT = 1' \
  'isInventoryComplete()' \
  'isRawUserTextPersisted()' \
  'isRawModelOutputPersisted()' \
  'isRawVehiclePayloadPersisted()' \
  'isLocationPersisted()' \
  'isAuditContentLogged()' \
  'isOwnerPolicyApproved()' \
  'isProductionLifecycleComplete()' \
  'isRuntimeLifecycleWiringComplete()' \
  'isAndroid13Arm64Verified()' \
  'isHardwareAccessed()' \
  'isProductionReady()' \
  'isTargetHardwareValidated()'; do
  require_text "$IMPLEMENTATION" "$marker"
done

if grep -Fq 'PrivacyDataInventoryContract' "$ROOT_DIR/$RUNTIME" \
    || grep -Fq 'PrivacyDataInventoryContract' "$ROOT_DIR/$GOVERNANCE"; then
  echo "P9-W04a inventory was wired into production Services" >&2
  exit 1
fi
if grep -Eiq \
    'android[.]|java[.]io|java[.]nio[.]file|java[.]net|okhttp|https?://|Room[.(]|CarPropertyManager|VehicleHal|/proc/|/dev/|ioctl|sysfs' \
    "$ROOT_DIR/$IMPLEMENTATION"; then
  echo "P9-W04a inventory reads platform, storage, network, vehicle or hardware state" >&2
  exit 1
fi

require_text "$DOC" 'W04A_INVENTORY_VERIFIED / POLICY_GAPS_OPEN'
require_text "$DOC" 'implementation_stage=P9-W04'
require_text "README.md" 'P9 Privacy Data Inventory'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W04a privacy data inventory'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W04a privacy data inventory trace'
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" 'Android P9-W04a Privacy Data Inventory Contract'
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" 'P9-W04a privacy data inventory architecture'
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" 'P9-W04a privacy data inventory detailed design'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'Android P9-W04a Privacy Data Inventory'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W04a Privacy Data Inventory Driver/HAL Boundary'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'DEV-091 P9-W04a inventory is not lifecycle enforcement'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'ISSUE-051 P9 durable privacy lifecycle policies are incomplete'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" 'P9-W04a Privacy Data Inventory progress'

printf '%s\n' \
  'Central Brain Android privacy data inventory check passed' \
  'privacy_data_inventory_complete=true' \
  'privacy_data_surface_count=12' \
  'privacy_durable_surface_count=6' \
  'privacy_process_local_surface_count=5' \
  'privacy_transient_surface_count=1' \
  'privacy_policy_gap_count=2' \
  'privacy_authorized_export_surface_count=1' \
  'privacy_raw_user_text_persisted=false' \
  'privacy_raw_model_output_persisted=false' \
  'privacy_raw_vehicle_payload_persisted=false' \
  'privacy_location_persisted=false' \
  'privacy_audit_content_logged=false' \
  'privacy_owner_policy_approved=false' \
  'privacy_production_lifecycle_complete=false' \
  'privacy_runtime_lifecycle_wiring_complete=false' \
  'privacy_android13_arm64_verified=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'implementation_stage=P9-W04'
