#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

python3 - "$ROOT_DIR" <<'PY'
import json
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
api_path = root / "central-brain/contracts/central_brain_api.json"
api_version = json.loads(api_path.read_text(encoding="utf-8"))["version"]

baseline_json = [
    "central-brain/contracts/central_brain_prototype_handoff_manifest.json",
    "central-brain/contracts/central_brain_prototype_completion_audit.json",
    "central-brain/contracts/central_brain_prototype_closure_plan.json",
]
for relative in baseline_json:
    payload = json.loads((root / relative).read_text(encoding="utf-8"))
    baseline = payload.get("baseline_api_contract_version")
    if baseline != api_version:
        raise SystemExit(f"API baseline mismatch: {relative}={baseline}, api={api_version}")

baseline_docs = [
    "docs/CENTRAL_BRAIN_PROTOTYPE_HANDOFF_MANIFEST.md",
    "docs/CENTRAL_BRAIN_PROTOTYPE_COMPLETION_AUDIT.md",
    "docs/CENTRAL_BRAIN_PROTOTYPE_CLOSURE_PLAN.md",
]
for relative in baseline_docs:
    text = (root / relative).read_text(encoding="utf-8")
    if f"`{api_version}`" not in text:
        raise SystemExit(f"API baseline version {api_version} missing from {relative}")
PY

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android runtime evolution pattern '$pattern' in $path" >&2
    exit 1
  fi
}

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android runtime evolution file: $path" >&2
    exit 1
  fi
}

PLAN="docs/CENTRAL_BRAIN_ANDROID_RUNTIME_EVOLUTION_PLAN.md"
for state in contract_defined prototype_implemented android_integrated hardware_validated production_qualified; do
  require_text "$PLAN" "$state"
done

for issue in ISSUE-021 ISSUE-022 ISSUE-023 ISSUE-024 ISSUE-025; do
  require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "$issue"
  require_text "$PLAN" "$issue"
done

for deviation in DEV-018 DEV-019; do
  require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "$deviation"
  require_text "$PLAN" "$deviation"
done

python3 - "$ROOT_DIR" <<'PY'
import json
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
for relative in (
    "central-brain/contracts/central_brain_prototype_handoff_manifest.json",
    "central-brain/contracts/central_brain_prototype_completion_audit.json",
):
    payload = json.loads((root / relative).read_text(encoding="utf-8"))
    issues = set(payload.get("open_issues", []))
    deviations = set(payload.get("open_deviations", []))
    missing_issues = {f"ISSUE-{number:03d}" for number in range(21, 26)} - issues
    missing_deviations = {"DEV-018", "DEV-019"} - deviations
    if missing_issues or missing_deviations:
        raise SystemExit(
            f"runtime evolution tracking missing from {relative}: "
            f"issues={sorted(missing_issues)}, deviations={sorted(missing_deviations)}"
        )
PY

require_text "$PLAN" "只聚焦 Android"
require_text "$PLAN" "不开发 Linux 前端"
require_text "$PLAN" "不修改厂商 Android Framework"
require_text "$PLAN" "Vendor NPU provider 保持 empty adapter"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "Android Runtime Evolution Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "2026-07-12 当前实施阶段"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "| R7 |"

for path in \
  central-brain/android-runtime/README.md \
  central-brain/android-runtime/central-brain-sdk/build.gradle.kts \
  central-brain/android-runtime/runtime-service/build.gradle.kts \
  central-brain/android-runtime/demo-hmi/build.gradle.kts \
  tools/build_central_brain_android_runtime.sh \
  tools/install_central_brain_android_runtime.sh \
  tools/check_central_brain_android_aidl_contract.sh \
  tools/check_central_brain_android_binder_runtime.sh \
  tools/check_central_brain_android_binder_lifecycle.sh \
  tools/check_central_brain_android_job_supervisor.sh \
  tools/check_central_brain_android_capability_policy.sh \
  tools/check_central_brain_android_action_governance.sh \
  tools/check_central_brain_android_durable_schema.sh \
  tools/check_central_brain_android_durable_repository.sh \
  tools/check_central_brain_android_durable_runtime_wiring.sh \
  tools/test_central_brain_android_capability_policy.sh \
  tools/test_central_brain_android_binder_lifecycle.sh \
  tools/check_central_brain_android_runtime_gradle.sh; do
  require_file "$path"
done

require_text "$PLAN" "R1A Gradle foundation"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R1A Android Gradle foundation trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "central-brain-sdk-debug.aar"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R1A Gradle Foundation Driver/HAL Evidence"
require_text "$PLAN" "R1B device lifecycle check"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R1B Android device lifecycle trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "r1_api33_exit_criteria_met=false"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R1B Device Lifecycle Driver/HAL Evidence"
require_text "$PLAN" "R1C API 33 exit"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R1C Android 13 exit trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "r1_api33_exit_criteria_met=true"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R1C API 33 Exit Driver/HAL Evidence"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "| R1 | Android Gradle 多模块交付骨架 | AI SDK AAR、Runtime Service APK、Demo HMI APK | 已完成"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "R1 Gradle 多模块、API 33"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "R1 SDK AAR、Runtime Service APK、Demo HMI APK"
require_file "docs/CENTRAL_BRAIN_ANDROID_AIDL_CONTRACT.md"
require_text "$PLAN" "R2A compiled AIDL contract"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R2A compiled AIDL contract trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "CENTRAL_BRAIN_ANDROID_AIDL_CONTRACT.md"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R2A AIDL Contract Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android R2 Typed AIDL Contract"
require_text "$PLAN" "R2B typed Binder runtime"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R2B typed Binder runtime trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "typed_binder_connected=true"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R2B Binder Runtime Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "R2C 已通过 API 33"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "R2C"
require_text "$PLAN" "R2C Binder lifecycle/race instrumentation"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R2C Binder lifecycle and race trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "r2_binder_exit_criteria_met=true"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R2C Binder Lifecycle Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "| R2 | Typed/async Protocol Binding | production/diagnostic AIDL、Parcelable、callback/cancel/death | 已完成"
require_text "docs/CENTRAL_BRAIN_ANDROID_AIDL_CONTRACT.md" "R2C Lifecycle And Race Evidence"
require_text "central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/CentralBrainSdk.java" 'MATURITY = "android_integrated"'
require_text "$PLAN" "R3A Job Supervisor foundation"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R3A Job Supervisor foundation trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "trusted_caller_identity_resolved=true"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R3A Job Supervisor Driver/HAL Boundary"
require_text "$PLAN" "R3B capability policy"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R3B capability policy trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "unknown_client_default_deny_verified=true"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R3B Capability Policy Driver/HAL Boundary"
require_text "$PLAN" "R3C1 action governance core"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R3C1 action governance core trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R3C1 Action Governance Core"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R3C1 Action Governance Core Driver/HAL Boundary"
require_text "$PLAN" "R3C2 typed Governance Binder"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R3C2 typed Governance Binder trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R3C2 Typed Governance Binder"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R3C2 Governance Binder Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "| R3 | Android Runtime 核心 | Job Supervisor、可信 Binder 身份、capability/policy | 已完成"
require_text "central-brain/android-runtime/central-brain-sdk/src/main/java/com/centralbrain/sdk/CentralBrainSdk.java" 'EVOLUTION_STAGE = "R3_TRUSTED_GOVERNANCE"'
require_text "$PLAN" "R4A Room durable schema"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R4A Room durable schema trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R4A Room Durable Schema"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R4A Room Schema Driver/HAL Boundary"
require_text "$PLAN" "R4B1 durable task admission"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R4B1 durable task admission trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R4B1 Durable Task Admission"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R4B1 Durable Repository Driver/HAL Boundary"
require_text "$PLAN" "R4B2 durable Runtime wiring"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R4B2 durable Runtime wiring trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android R4B2 Durable Runtime Wiring"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R4B2 Durable Runtime Driver/HAL Boundary"

bash "$ROOT_DIR/tools/check_central_brain_android_job_supervisor.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_capability_policy.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_action_governance.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_durable_schema.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_durable_repository.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_durable_runtime_wiring.sh"

bash "$ROOT_DIR/tools/check_central_brain_android_runtime_gradle.sh"

echo "Central Brain Android runtime evolution baseline check passed"
