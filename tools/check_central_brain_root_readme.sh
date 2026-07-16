#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001..006, NV-F-001/011/012,
# NV-G-003/005/006/007, NV-P-002, KH-003/006, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
README="$ROOT_DIR/README.md"
SETTINGS="$ROOT_DIR/central-brain/android-runtime/settings.gradle.kts"

[[ -f "$README" ]] || { echo "missing repository README" >&2; exit 1; }
[[ -f "$SETTINGS" ]] || { echo "missing Android Gradle settings" >&2; exit 1; }

require_text() {
  local marker="$1"
  grep -Fq -- "$marker" "$README" \
    || { echo "root README marker missing: $marker" >&2; exit 1; }
}

for heading in \
  '# CougarOS Central Brain' \
  '## 当前状态' \
  '## GitHub 同步与仓库完整性' \
  '## README 维护规则' \
  '## 软件总架构' \
  '## 开发进度总表' \
  '### 已开发并验证' \
  '### 未开发或外部阻塞' \
  '## 核心调用链' \
  '## 仓库目录与模块映射' \
  '## 语言与所有权边界' \
  '## Android 交付产物' \
  '## 安全和集成边界' \
  '## 关键架构文档' \
  '## 本地受控输入与非发布内容' \
  '## 近期修改日志'; do
  require_text "$heading"
done

for marker in \
  '用户提供的架构图是需求基线，不是示意图' \
  'github_source_of_truth=true' \
  'github_sync_required=true' \
  'maintained_project_files_synced=true' \
  'github_homepage_architecture_current=true' \
  '每个完成的开发增量必须在同一轮完成 Git commit、push 和远端检查' \
  'python_prototype_runtime_maintained=false' \
  'central-brain/android-runtime/' \
  'central-brain-sdk' \
  'runtime-service' \
  'native-runtime' \
  'demo-hmi' \
  'policy-probe' \
  'apk-labs/client2-central-brain/' \
  'vendor.npu.empty' \
  'CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md' \
  'CENTRAL_BRAIN_COCKPIT_HMI_UX_DESIGN_MOCKUPS.md' \
  'CENTRAL_BRAIN_PYTHON_PROTOTYPE_RETIREMENT.md' \
  'tools/check_central_brain_python_prototype_retirement.sh' \
  'central_brain_github_remote_testing.json' \
  'android13-hwtest-v0.5.0-rc.2' \
  'physical_controller_application_evidence_available=true' \
  'cockpit_hmi_design_mockups_ready=true' \
  'aios_intent_orchestration_ux_ready=true' \
  'cockpit_hmi_1920x1080_safe_frame_verified=true' \
  'cockpit_hmi_translucent_material_ready=true' \
  'session_contract_v1_defined=true' \
  'session_parcel_physical_android13_arm64_verified=true' \
  'session_runtime_service_published=false' \
  'plan_contract_v1_defined=true' \
  'plan_parcel_physical_android13_arm64_verified=true' \
  'plan_runtime_published=false' \
  'event_contract_v1_defined=true' \
  'event_parcel_physical_android13_arm64_verified=true' \
  'event_runtime_service_published=false' \
  'event_callback_service_published=false' \
  'ICentralBrainSessionRuntime V1（合同已冻结）' \
  'cockpit_demo_control_loop_implemented=false' \
  'S2-HMI-001..006' \
  '意图输入（设计稿已交付）' \
  '计划与 Policy（设计稿已交付）' \
  '中控 AIOS 演示闭环' \
  'HVAC/Seat/Media/Nav Effect 详情' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'driver_development_triggered=false' \
  'virtualization_development_triggered=false' \
  'GitHub 不连接目标 ADB' \
  '不修改厂商 Android Framework'; do
  require_text "$marker"
done

required_paths=(
  .github/ISSUE_TEMPLATE/hardware-test.yml
  .github/workflows/central-brain-remote-test-contract.yml
  .githooks/pre-push
  apk-labs/client2-central-brain/README.md
  central-brain/android-runtime/settings.gradle.kts
  central-brain/android-runtime/central-brain-sdk/build.gradle.kts
  central-brain/android-runtime/native-runtime/build.gradle.kts
  central-brain/android-runtime/runtime-service/build.gradle.kts
  central-brain/contracts/central_brain_android_b3_blackbox_acceptance.json
  central-brain/contracts/central_brain_android_r7c_acceptance.json
  central-brain/contracts/central_brain_github_remote_testing.json
  central-brain/delivery/android-hybrid/central-brain.android-hybrid-delivery-profile.json
  docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md
  docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md
  docs/CENTRAL_BRAIN_COCKPIT_HMI_CONTROL_LOOP_PLAN.md
  docs/CENTRAL_BRAIN_COCKPIT_HMI_UX_DESIGN_MOCKUPS.md
  docs/ui/cockpit-hmi-design/index.html
  docs/ui/cockpit-hmi-design/styles.css
  docs/ui/cockpit-hmi-design/app.js
  docs/assets/cockpit-hmi-design/01-intent.png
  docs/assets/cockpit-hmi-design/02-plan.png
  docs/assets/cockpit-hmi-design/03-execution.png
  docs/assets/cockpit-hmi-design/04-result.png
  docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md
  docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md
  docs/CENTRAL_BRAIN_ROADMAP.md
  docs/CENTRAL_BRAIN_PYTHON_PROTOTYPE_RETIREMENT.md
  tools/check_central_brain_android_runtime_evolution.sh
  tools/check_central_brain_android_session_contract.sh
  tools/check_central_brain_android_plan_contract.sh
  tools/check_central_brain_android_event_contract.sh
  tools/check_central_brain_aios_stage2_design.sh
  tools/check_central_brain_cockpit_hmi_design.sh
  tools/check_central_brain_github_repository_completeness.sh
  tools/check_central_brain_python_prototype_retirement.sh
  tools/check_central_brain_github_remote_testing.sh
)
for path in "${required_paths[@]}"; do
  [[ -f "$ROOT_DIR/$path" ]] \
    || { echo "README-mapped repository file is missing: $path" >&2; exit 1; }
done

if rg -n \
    'central-brain/(backend|android-console|bindings/(android|linux)|linux-cli|deploy/linux)|CENTRAL_BRAIN_(SOFTWARE_DETAILED_DESIGN|PLATFORM_DELTA|PROTOTYPE_|ANDROID_SYSTEM_SERVICE_INTEGRATION)|ollama_simulated_npu' \
    "$README"; then
  echo "root README references a retired Python prototype path" >&2
  exit 1
fi

python3 -B - "$ROOT_DIR" "$README" "$SETTINGS" <<'PY'
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
readme = pathlib.Path(sys.argv[2]).read_text(encoding="utf-8")
settings = pathlib.Path(sys.argv[3]).read_text(encoding="utf-8")

modules = re.findall(r'include\(":([a-z0-9-]+)"\)', settings)
expected = {
    "central-brain-sdk",
    "native-runtime",
    "runtime-service",
    "demo-hmi",
    "policy-probe",
}
if set(modules) != expected:
    raise SystemExit(f"unexpected Android Gradle modules: {modules}")
for module in modules:
    if f"`{module}`" not in readme:
        raise SystemExit(f"Android Gradle module missing from README: {module}")

relative_links = []
for target in re.findall(r"\[[^\]]+\]\(([^)]+)\)", readme):
    if target.startswith(("http://", "https://", "#")):
        continue
    target = target.split("#", 1)[0]
    if target:
        relative_links.append(target)
for target in relative_links:
    if not (root / target).exists():
        raise SystemExit(f"README relative link does not exist: {target}")

recent = readme.split("## 近期修改日志", 1)[1]
commit_links = re.findall(
    r"https://github\.com/LucasWEIchen/CougarOS/commit/", recent
)
if len(commit_links) < 8:
    raise SystemExit("README recent log must retain at least eight commit links")

developed = readme.split("### 已开发并验证", 1)[1].split(
    "### 未开发或外部阻塞", 1
)[0]
if developed.count("`DEVELOPED`") < 9:
    raise SystemExit("README developed table must contain at least nine modules")

remaining = readme.split("### 未开发或外部阻塞", 1)[1].split(
    "## 核心调用链", 1
)[0]
remaining_rows = sum(
    remaining.count(status)
    for status in ("`NOT_STARTED`", "`EXTERNAL_BLOCKED`", "`OUT_OF_SCOPE`")
)
if remaining_rows < 12:
    raise SystemExit("README remaining-work table must contain at least twelve modules")
if "`P1-W01..P1-W03`" not in remaining or "`P1-W04`" not in remaining:
    raise SystemExit("README remaining-work table must preserve P1-W01..P1-W03 status and name P1-W04")

for group in (
    "APP-004",
    "XSC-001..006",
    "NV-F-001/011/012",
    "NV-G-003/005/006/007",
    "DEL-001/003/004/005",
):
    if group not in readme:
        raise SystemExit(f"README Req ID group missing: {group}")

print("root_readme_android_gradle_modules_verified=true")
print(f"root_readme_relative_links_verified={len(relative_links)}")
print(f"root_readme_recent_commit_links={len(commit_links)}")
PY

printf '%s\n' \
  'Central Brain repository architecture README check passed' \
  'root_readme_architecture_documented=true' \
  'root_readme_module_mapping_documented=true' \
  'root_readme_development_progress_documented=true' \
  'github_homepage_architecture_current=true' \
  'python_prototype_runtime_maintained=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
