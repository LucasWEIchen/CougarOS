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
  tools/check_central_brain_android_runtime_gradle.sh; do
  require_file "$path"
done

require_text "$PLAN" "R1A Gradle foundation"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "R1A Android Gradle foundation trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "central-brain-sdk-debug.aar"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "R1A Gradle Foundation Driver/HAL Evidence"

bash "$ROOT_DIR/tools/check_central_brain_android_runtime_gradle.sh"

echo "Central Brain Android runtime evolution baseline check passed"
