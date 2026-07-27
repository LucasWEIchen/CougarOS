#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-UX-002, S2-SAF-001, S2-EFF-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_p9_driver_safety_admission.json"
SOURCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/governance/DriverSafetyAdmissionContract.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/governance/DriverSafetyAdmissionContractTest.java"
ACTION_POLICY="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/governance/ActionGovernancePolicy.java"
CAPABILITY="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/vehicle/capability/VehicleCapability.java"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
DOC="docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md"

for file in "$CONTRACT" "$SOURCE" "$TEST" "$ACTION_POLICY" "$CAPABILITY" \
    "$RUNTIME" "$GOVERNANCE" "$DOC"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P9-W06a file missing: $file" >&2; exit 1; }
done

python3 -B - "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$SOURCE" "$ROOT_DIR/$TEST" \
    "$ROOT_DIR/$ACTION_POLICY" "$ROOT_DIR/$CAPABILITY" "$ROOT_DIR/$DOC" <<'PY'
import json
import pathlib
import sys

contract_path, source_path, test_path, policy_path, capability_path, doc_path = \
    map(pathlib.Path, sys.argv[1:])
contract = json.loads(contract_path.read_text(encoding="utf-8"))
source = source_path.read_text(encoding="utf-8")
test = test_path.read_text(encoding="utf-8")
supporting_source = (
    policy_path.read_text(encoding="utf-8")
    + capability_path.read_text(encoding="utf-8")
)
doc = doc_path.read_text(encoding="utf-8")

if contract.get("schema_version") != 1 \
        or contract.get("profile_id") != "android13-p9-driver-safety-admission-v1":
    raise SystemExit("P9-W06a contract identity changed")
if contract.get("requirement_ids") != [
        "S2-UX-002", "S2-SAF-001", "S2-EFF-001", "S2-OBS-001",
        "DEL-001", "DEL-004", "DEL-005"]:
    raise SystemExit("P9-W06a Req ID set changed")
if contract.get("maximum_state_age_ms") != 500:
    raise SystemExit("P9-W06a state freshness ceiling changed")
if contract.get("ux_profiles") != [
        "PARKED_FULL", "MOVING_RESTRICTED", "UNKNOWN_RESTRICTED",
        "FAULT_RESTRICTED"]:
    raise SystemExit("P9-W06a UX profile set changed")
if contract.get("owner_roles") != [
        "FUNCTIONAL_SAFETY", "DRIVER_DISTRACTION_HMI", "VEHICLE_INTEGRATION"]:
    raise SystemExit("P9-W06a owner role set changed")

expected_actions = [
    "vehicle.state.read",
    "scene.intent.submit",
    "session.cancel",
    "ui.long_text.display",
    "ui.parameter.edit",
    "driver.display.video.play",
    "cabin.temperature.set",
    "vehicle.seat.driver.heating.set",
    "vehicle.seat.driver.ventilation.set",
    "vehicle.seat.driver.recline.set",
    "vehicle.diagnostics.write",
    "system.ota.install",
]
rules = contract.get("action_rules", [])
if [item.get("action_id") for item in rules] != expected_actions:
    raise SystemExit("P9-W06a action order or set changed")
if len(rules) != 12 or len({item.get("action_id") for item in rules}) != 12:
    raise SystemExit("P9-W06a action catalog count changed")
for action in expected_actions:
    if action not in source + supporting_source:
        raise SystemExit(f"P9-W06a Java action marker missing: {action}")

by_action = {item["action_id"]: item for item in rules}
for action in [
        "ui.long_text.display", "ui.parameter.edit", "driver.display.video.play",
        "vehicle.seat.driver.recline.set", "vehicle.diagnostics.write",
        "system.ota.install"]:
    if by_action[action].get("allowed_while_moving") is not False:
        raise SystemExit(f"P9-W06a moving hard interlock changed: {action}")
for action in [
        "cabin.temperature.set", "vehicle.seat.driver.heating.set",
        "vehicle.seat.driver.ventilation.set", "vehicle.seat.driver.recline.set"]:
    item = by_action[action]
    if not item.get("requires_trusted_state") \
            or not item.get("requires_owner_policy") \
            or not item.get("requires_driver_available") \
            or not item.get("requires_readback"):
        raise SystemExit(f"P9-W06a vehicle gate was relaxed: {action}")

state = contract.get("current_repository_evidence", {})
if state.get("owner_approval_count") != 0:
    raise SystemExit("P9-W06a repository must not claim owner evidence")
for key in [
        "current_owner_policy_approved", "vehicle_state_provider_wired",
        "effect_runtime_wired", "android13_arm64_verified", "hardware_accessed",
        "production_ready", "target_hardware_validated"]:
    if state.get(key) is not False:
        raise SystemExit(f"P9-W06a forbidden claim was raised: {key}")

for marker in [
        "exactCatalogAndUxOnlyActionsNeverGrantEffectAuthority",
        "staleFutureAndUntrustedStateFailClosed",
        "movingProfileAllowsGovernedComfortButHardDeniesDistractionAndRecline",
        "parkedDriverSeatReclineRequiresApprovalAndNeverDispatches",
        "ownerPolicyMustContainThreeUniqueDigestBoundRoles",
        "capabilityAvailabilityAuthorizationReadbackAndActivationAreIndependentGates",
        "emergencyUnknownMotionAndMissingDriverRemainRestricted",
        "repositoryClaimsRemainOwnerBlockedUnwiredAndUnverified"]:
    if marker not in test:
        raise SystemExit(f"P9-W06a JVM marker missing: {marker}")

for marker in [
        "isEffectDispatchAuthorized()", "return false;",
        "MAXIMUM_STATE_AGE_MS = 500", "isProductionTrusted()",
        "MOVING_HARD_INTERLOCK", "currentDraftPolicy()"]:
    if marker not in source:
        raise SystemExit(f"P9-W06a source boundary marker missing: {marker}")
for forbidden in [
        "import android.", "java.io.", "java.net.", "PackageManager",
        "CarPropertyManager", "VehicleHal", "ServiceManager", "System.loadLibrary",
        "Runtime.getRuntime", "/dev/", "/proc/", "ioctl", "startService(",
        "sendBroadcast("]:
    if forbidden in source:
        raise SystemExit(f"P9-W06a source crosses software-only boundary: {forbidden}")
if "production_document_scope=true" not in doc:
    raise SystemExit("P9-W06a production development document marker missing")
PY

if grep -Fq 'DriverSafetyAdmissionContract' "$ROOT_DIR/$RUNTIME" \
    || grep -Fq 'DriverSafetyAdmissionContract' "$ROOT_DIR/$GOVERNANCE"; then
  echo "P9-W06a contract was wired into production Services" >&2
  exit 1
fi

printf '%s\n' \
  'Central Brain Android P9-W06a driver safety admission check passed' \
  'driver_safety_admission_defined=true' \
  'driver_safety_action_rule_count=12' \
  'driver_safety_owner_role_count=3' \
  'driver_safety_state_maximum_age_ms=500' \
  'driver_safety_moving_hard_interlock_verified=true' \
  'driver_safety_current_owner_policy_approved=false' \
  'driver_safety_vehicle_state_provider_wired=false' \
  'driver_safety_effect_runtime_wired=false' \
  'driver_safety_android13_arm64_verified=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
