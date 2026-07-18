#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-REL-001, S2-OBS-001, XSC-001/004/005/006,
# KH-003/006, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_p9_stability_fault_matrix.json"
IMPLEMENTATION="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/reliability/StabilityFaultMatrixContract.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/reliability/StabilityFaultMatrixContractTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/reliability/StabilityFaultMatrixProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"
DOC="docs/CENTRAL_BRAIN_STABILITY_FAULT_MATRIX.md"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P9-W02 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$CONTRACT" "$IMPLEMENTATION" "$TEST" "$PROBE" "$DOC" \
    "$DEBUG_MANIFEST" "$MAIN_MANIFEST" "$RUNTIME" "$GOVERNANCE" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P9-W02 file missing: $file" >&2; exit 1; }
done

python3 - "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$IMPLEMENTATION" <<'PY'
import json
import re
import sys

contract_path, java_path = sys.argv[1:]
with open(contract_path, encoding="utf-8") as stream:
    contract = json.load(stream)
java = open(java_path, encoding="utf-8").read()

if contract.get("schema_version") != "1.0.0":
    raise SystemExit("P9-W02 matrix schema version changed")
if contract.get("profile_id") != "android13-stability-v1":
    raise SystemExit("P9-W02 matrix profile changed")
if contract.get("maturity") != "software_contract_target_72h_pending":
    raise SystemExit("P9-W02 maturity changed")

workloads = [
    "scene.comfort.cold.v1",
    "scene.fatigue.assist.v1",
    "scene.rest.nap.v1",
]
faults = [
    ("none", "COMPLETED", 0),
    ("adapter_death", "RECOVERED", 5_000),
    ("runtime_restart", "RECOVERED", 30_000),
    ("storage_pressure", "FAIL_CLOSED", 5_000),
    ("callback_churn", "RECOVERED", 5_000),
    ("network_loss", "DEGRADED", 5_000),
]
if contract.get("workloads") != workloads:
    raise SystemExit("P9-W02 workload catalog changed")
actual_faults = [
    (item["fault_id"], item["expected_outcome"], item["recovery_budget_ms"])
    for item in contract.get("faults", [])
]
if actual_faults != faults:
    raise SystemExit("P9-W02 fault catalog changed")

expected_matrix = [
    (f"{workload}::{fault}", workload, fault)
    for workload in workloads
    for fault, _, _ in faults
]
actual_matrix = [
    (item["case_id"], item["workload"], item["fault"])
    for item in contract.get("matrix", [])
]
if actual_matrix != expected_matrix:
    raise SystemExit("P9-W02 18-case matrix changed")

workload_source = java.split("public enum Workload", 1)[1].split(
    "public enum ExpectedOutcome", 1
)[0]
java_workloads = re.findall(r'[A-Z_]+\("([^"]+)"\)', workload_source)
if java_workloads != workloads:
    raise SystemExit("P9-W02 Java and JSON workloads differ")
fault_source = java.split("public enum Fault", 1)[1].split(
    "public enum EvidenceMode", 1
)[0]
java_faults = [
    (fault_id, outcome, int(limit.replace("_", "")))
    for fault_id, outcome, limit in re.findall(
        r'[A-Z_]+\("([^"]+)",\s*ExpectedOutcome\.([A-Z_]+),\s*([0-9_]+)L\)',
        fault_source,
        re.DOTALL,
    )
]
if java_faults != faults:
    raise SystemExit("P9-W02 Java and JSON faults differ")

modes = contract.get("evidence_modes", {})
if modes.get("TARGET_ANDROID13_72H", {}).get("minimum_duration_ms") != 259_200_000:
    raise SystemExit("P9-W02 target duration is not 72h")
if modes.get("ANDROID_APPLICATION", {}).get("minimum_iterations_per_case") != 30:
    raise SystemExit("P9-W02 Android minimum iterations changed")

state = contract.get("claim_state", {})
required_true = {
    "stability_fault_matrix_contract_defined",
    "stability_report_validation_verified",
}
required_false = {
    "stability_target_72h_complete",
    "stability_target_owner_approved",
    "stability_android13_arm64_verified",
    "stability_fault_injection_runtime_wired",
    "hardware_accessed",
    "production_ready",
    "target_hardware_validated",
}
if any(state.get(key) is not True for key in required_true):
    raise SystemExit("P9-W02 required software claim is false")
if any(state.get(key) is not False for key in required_false):
    raise SystemExit("P9-W02 target or production claim was raised")
if state.get("stability_workload_count") != 3:
    raise SystemExit("P9-W02 workload count changed")
if state.get("stability_fault_count") != 6:
    raise SystemExit("P9-W02 fault count changed")
if state.get("stability_matrix_case_count") != 18:
    raise SystemExit("P9-W02 matrix count changed")
PY

for marker in \
  'PROFILE_ID = "android13-stability-v1"' \
  'WORKLOAD_COUNT = 3' \
  'FAULT_COUNT = 6' \
  'MATRIX_CASE_COUNT = WORKLOAD_COUNT * FAULT_COUNT' \
  'TARGET_DURATION_MILLIS = 259_200_000L' \
  'CONTRACT_TEST' \
  'ANDROID_APPLICATION' \
  'TARGET_ANDROID13_72H' \
  'UNEXPECTED_CRASH' \
  'ANR_DETECTED' \
  'INVARIANT_VIOLATION' \
  'RECOVERY_TIMEOUT' \
  'private final String observationDigest;' \
  'private static String observationDigest(Observation observation)' \
  'public static Report evaluate(' \
  'isTargetEvidenceStructurallyComplete()' \
  'isFaultInjectionRuntimeWired()' \
  'isHardwareAccessed()' \
  'isProductionReady()' \
  'isTargetHardwareQualified()'; do
  require_text "$IMPLEMENTATION" "$marker"
done

for test_name in \
  fixedMatrixContainsThreeWorkloadsSixFaultsAndEighteenCases \
  matrixIsExactWorkloadFaultCrossProduct \
  completeContractReportPassesWithoutTargetQualification \
  duplicateAndMissingCasesFailClosed \
  applicationIterationAndDurationRequirementsFailClosed \
  crashAnrAndInvariantViolationFailReport \
  outcomeMismatchAndRecoveryTimeoutFailReport \
  targetNeedsSeventyTwoHoursSamplesAndOwnerButNeverAutoQualifies \
  reportDigestIsIndependentOfObservationInputOrder \
  reportDigestBindsAllObservationCounters; do
  require_text "$TEST" "$test_name"
done

for marker in \
  stability_fault_matrix_probe_complete \
  stability_fault_matrix_contract_verified \
  stability_matrix_verified \
  stability_report_validation_verified \
  stability_boundary_verified; do
  require_text "$PROBE" "$marker="
done

for marker in \
  stability_fault_matrix_contract_verified=true \
  stability_matrix_verified=true \
  stability_report_validation_verified=true \
  stability_boundary_verified=true \
  stability_workload_count=3 \
  stability_fault_count=6 \
  stability_matrix_case_count=18 \
  stability_target_72h_complete=false \
  stability_fault_injection_runtime_wired=false \
  stability_android13_arm64_verified=true \
  hardware_accessed=false \
  production_ready=false \
  target_hardware_validated=false; do
  require_text "$INSTALLER" "$marker"
done

require_text "$DEBUG_MANIFEST" '.reliability.StabilityFaultMatrixProbeActivity'
if grep -Fq 'StabilityFaultMatrixProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P9-W02 debug probe leaked into the release manifest" >&2
  exit 1
fi
if grep -Fq 'StabilityFaultMatrixContract' "$ROOT_DIR/$RUNTIME" \
    || grep -Fq 'StabilityFaultMatrixContract' "$ROOT_DIR/$GOVERNANCE"; then
  echo "P9-W02 evaluator was wired into production Services" >&2
  exit 1
fi
if grep -R -Eiq \
    'System[.](currentTimeMillis|nanoTime)|elapsedRealtime|Thread[.]sleep|android[.]os|android[.]car|CarPropertyManager|VehicleHal|VehiclePropertyIds|/proc/|dumpsys|perfetto|simpleperf|ioctl|sysfs|/dev/|java[.]net|okhttp|https?://' \
    "$ROOT_DIR/$IMPLEMENTATION"; then
  echo "P9-W02 contract reads time, Android, vehicle, network, or hardware state" >&2
  exit 1
fi
require_text "$PROBE" 'EvidenceMode.CONTRACT_TEST'
require_text "$DOC" 'Central Brain P9-W02 Stability and Fault Matrix'
require_text "$DOC" 'software_contract_target_72h_pending'
require_text "$DOC" 'implementation_stage=P9-W03'

require_text "central-brain/android-runtime/README.md" "P9-W02 Stability and fault matrix"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P9-W02` 72h stability and fault matrix'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P9-W02 stability and fault matrix trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P9-W02 Stability Fault Matrix Contract"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P9-W02 stability fault matrix architecture"
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" "P9-W02 stability fault matrix detailed design"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P9-W02 Stability Fault Matrix Contract"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P9-W02 Stability Fault Matrix Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "P9-W02 synthetic matrix is not a 72h target run"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "ISSUE-049 P9 target 72h stability evidence is unavailable"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P9-W02 Stability Fault Matrix progress"
require_text "README.md" "P9 Stability Fault Matrix Contract"

printf '%s\n' \
  "Central Brain Android stability fault matrix check passed" \
  "stability_fault_matrix_contract_defined=true" \
  "stability_workload_count=3" \
  "stability_fault_count=6" \
  "stability_matrix_case_count=18" \
  "stability_matrix_catalog_verified=true" \
  "stability_report_validation_verified=true" \
  "stability_failure_invariants_verified=true" \
  "stability_evidence_mode_separation_verified=true" \
  "stability_target_72h_complete=false" \
  "stability_target_owner_approved=false" \
  "stability_android13_arm64_verified=false" \
  "stability_fault_injection_runtime_wired=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false" \
  "implementation_stage=P9-W03"
