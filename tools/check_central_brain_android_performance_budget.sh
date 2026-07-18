#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-OBS-001, S2-REL-001, XSC-001/004/005/006,
# KH-003/006, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="central-brain/contracts/central_brain_android_p9_performance_budget.json"
IMPLEMENTATION="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/performance/PerformanceBudgetContract.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/performance/PerformanceBudgetContractTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/performance/PerformanceBudgetProbeActivity.java"
DEBUG_MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
RUNTIME="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"
DOC="docs/CENTRAL_BRAIN_PERFORMANCE_BUDGETS.md"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P9-W01 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$CONTRACT" "$IMPLEMENTATION" "$TEST" "$PROBE" "$DOC" \
    "$DEBUG_MANIFEST" "$MAIN_MANIFEST" "$RUNTIME" "$GOVERNANCE" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P9-W01 file missing: $file" >&2; exit 1; }
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
    raise SystemExit("P9-W01 budget schema version changed")
if contract.get("budget_profile_id") != "android13-app-baseline-v1":
    raise SystemExit("P9-W01 budget profile changed")
if contract.get("maturity") != "initial_software_budget":
    raise SystemExit("P9-W01 budget maturity must remain initial")
expected_categories = [
    "binder", "plan", "effect_dispatch", "database", "memory", "cpu", "startup"
]
if contract.get("metric_categories") != expected_categories:
    raise SystemExit("P9-W01 budget categories changed")
expected = [
    ("binder.protocol_negotiation.latency_us", "binder", "P95", "MICROSECONDS", 10_000),
    ("binder.session_open.latency_us", "binder", "P95", "MICROSECONDS", 50_000),
    ("binder.read_cancel.latency_us", "binder", "P95", "MICROSECONDS", 30_000),
    ("binder.callback_registration.latency_us", "binder", "P95", "MICROSECONDS", 50_000),
    ("plan.compile.latency_us", "plan", "P95", "MICROSECONDS", 300_000),
    ("effect.dispatch_admission.latency_us", "effect_dispatch", "P95", "MICROSECONDS", 200_000),
    ("database.combined_size.bytes", "database", "MAX", "BYTES", 67_108_864),
    ("runtime.pss.bytes", "memory", "MAX", "BYTES", 268_435_456),
    ("runtime.cpu.single_core_permille", "cpu", "P95", "PERMILLE_SINGLE_CORE", 300),
    ("runtime.cold_start_to_binder_ready.latency_us", "startup", "P95", "MICROSECONDS", 3_000_000),
]
actual = [
    (item["metric_id"], item["category"], item["aggregation"], item["unit"], item["limit"])
    for item in contract.get("budgets", [])
]
if actual != expected:
    raise SystemExit("P9-W01 JSON budget catalog changed")

matches = re.findall(
    r'[A-Z_]+\(\s*"([^"]+)",\s*Category\.([A-Z_]+),\s*'
    r'Aggregation\.([A-Z0-9_]+),\s*Unit\.([A-Z_]+),\s*([0-9_]+)L\)',
    java,
    re.DOTALL,
)
java_catalog = [
    (metric_id, category.lower(), aggregation, unit, int(limit.replace("_", "")))
    for metric_id, category, aggregation, unit, limit in matches
]
if java_catalog != expected:
    raise SystemExit("P9-W01 Java and JSON budget catalogs differ")

state = contract.get("claim_state", {})
required_true = {
    "performance_budget_contract_defined",
    "performance_budget_report_validation_verified",
}
required_false = {
    "performance_budget_target_owner_approved",
    "performance_budget_android13_arm64_verified",
    "performance_budget_target_measurement_complete",
    "performance_budget_runtime_wired",
    "hardware_accessed",
    "production_ready",
    "target_hardware_validated",
}
if any(state.get(key) is not True for key in required_true):
    raise SystemExit("P9-W01 required software claim is false")
if any(state.get(key) is not False for key in required_false):
    raise SystemExit("P9-W01 target or production claim was raised")
if state.get("performance_budget_category_count") != 7:
    raise SystemExit("P9-W01 category count changed")
if state.get("performance_budget_metric_count") != 10:
    raise SystemExit("P9-W01 metric count changed")
PY

for marker in \
  'PROFILE_ID = "android13-app-baseline-v1"' \
  'METRIC_CATEGORY_COUNT = 7' \
  'METRIC_COUNT = 10' \
  'TARGET_MINIMUM_SAMPLES = 30' \
  'CONTRACT_TEST' \
  'ANDROID_APPLICATION' \
  'TARGET_ANDROID13' \
  'WITHIN_BUDGET' \
  'EXCEEDED' \
  'MISSING' \
  'UNIT_MISMATCH' \
  'INSUFFICIENT_SAMPLES' \
  'public static Report evaluate(' \
  'isTargetEvidenceStructurallyComplete()' \
  'isRuntimeWired()' \
  'isHardwareAccessed()' \
  'isProductionReady()' \
  'isTargetHardwareQualified()'; do
  require_text "$IMPLEMENTATION" "$marker"
done

for test_name in \
  fixedCatalogContainsSevenCategoriesAndTenMetrics \
  completeContractReportPassesWithoutTargetQualification \
  exactThresholdPassesAndOneUnitAboveFails \
  missingAndInsufficientSamplesFailClosed \
  duplicateMetricIsRejected \
  unitMismatchFailsClosed \
  reportDigestIsIndependentOfMeasurementInputOrder \
  targetEvidenceNeedsSamplesAndOwnerButNeverAutoQualifiesHardware; do
  require_text "$TEST" "$test_name"
done

for marker in \
  performance_budget_probe_complete \
  performance_budget_contract_verified \
  performance_budget_catalog_verified \
  performance_budget_report_validation_verified \
  performance_budget_boundary_verified; do
  require_text "$PROBE" "$marker="
done

for marker in \
  performance_budget_contract_verified=true \
  performance_budget_catalog_verified=true \
  performance_budget_report_validation_verified=true \
  performance_budget_boundary_verified=true \
  performance_budget_category_count=7 \
  performance_budget_metric_count=10 \
  performance_budget_target_measurement_complete=false \
  performance_budget_runtime_wired=false \
  performance_budget_android13_arm64_verified=true \
  hardware_accessed=false \
  production_ready=false \
  target_hardware_validated=false; do
  require_text "$INSTALLER" "$marker"
done

require_text "$DEBUG_MANIFEST" '.performance.PerformanceBudgetProbeActivity'
if grep -Fq 'PerformanceBudgetProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P9-W01 debug probe leaked into the release manifest" >&2
  exit 1
fi
if grep -Fq 'PerformanceBudgetContract' "$ROOT_DIR/$RUNTIME" \
    || grep -Fq 'PerformanceBudgetContract' "$ROOT_DIR/$GOVERNANCE"; then
  echo "P9-W01 budget evaluator was wired into production Services" >&2
  exit 1
fi
if grep -R -Eiq \
    'System[.](currentTimeMillis|nanoTime)|elapsedRealtime|Debug[.]MemoryInfo|android[.]os|android[.]car|CarPropertyManager|VehicleHal|VehiclePropertyIds|/proc/|dumpsys|perfetto|simpleperf|ioctl|sysfs|/dev/|java[.]net|okhttp|https?://' \
    "$ROOT_DIR/$IMPLEMENTATION"; then
  echo "P9-W01 contract reads time, Android, vehicle, network, or hardware state" >&2
  exit 1
fi
require_text "$PROBE" 'EvidenceMode.CONTRACT_TEST'
require_text "$PROBE" 'budget.getLimit()'
require_text "$DOC" 'Central Brain P9-W01 Performance Budgets'
require_text "$DOC" 'initial_software_budget'
require_text "$DOC" 'implementation_stage=P9-W03'

require_text "central-brain/android-runtime/README.md" "P9-W01 Performance budgets"
require_text "docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md" '`P9-W01` Performance budgets'
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "P9-W01 performance budget trace"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android P9-W01 Performance Budget Contract"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md" "P9-W01 performance budget architecture"
require_text "docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md" "P9-W01 performance budget detailed design"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android P9-W01 Performance Budget Contract"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "P9-W01 Performance Budget Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "P9-W01 initial budgets are not target measurements"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "ISSUE-048 P9 target performance evidence is unavailable"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "P9-W01 Performance Budget progress"
require_text "README.md" "P9 Performance Budget Contract"

printf '%s\n' \
  "Central Brain Android performance budget check passed" \
  "performance_budget_contract_defined=true" \
  "performance_budget_category_count=7" \
  "performance_budget_metric_count=10" \
  "performance_budget_catalog_verified=true" \
  "performance_budget_report_validation_verified=true" \
  "performance_budget_threshold_fail_closed_verified=true" \
  "performance_budget_evidence_mode_separation_verified=true" \
  "performance_budget_contract_probe_android13_arm64_verified=true" \
  "performance_budget_target_owner_approved=false" \
  "performance_budget_target_measurement_complete=false" \
  "performance_budget_android13_arm64_verified=false" \
  "performance_budget_runtime_wired=false" \
  "hardware_accessed=false" \
  "production_ready=false" \
  "target_hardware_validated=false" \
  "implementation_stage=P9-W03"
