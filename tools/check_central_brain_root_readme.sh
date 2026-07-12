#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/002/003/004/005/006, NV-F-001/011/012,
# NV-G-003/005/006/007, NV-P-002, KH-003/006, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
README="$ROOT_DIR/README.md"
SETTINGS="$ROOT_DIR/central-brain/android-runtime/settings.gradle.kts"

[[ -f "$README" ]] || { echo "missing repository architecture README: $README" >&2; exit 1; }

require_text() {
  local marker="$1"
  grep -Fq -- "$marker" "$README" \
    || { echo "root README marker missing: $marker" >&2; exit 1; }
}

for heading in \
  '# CougarOS Central Brain' \
  '## 当前状态' \
  '## README 维护规则' \
  '## 软件总架构' \
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
  'Android 实际工程路径' \
  'Python 架构原型路径' \
  'central-brain/android-runtime/' \
  'central-brain-sdk' \
  'runtime-service' \
  'native-runtime' \
  'demo-hmi' \
  'policy-probe' \
  'apk-labs/client2-central-brain/' \
  'central-brain/backend/mock_npu_service.py' \
  '[软件详细设计](docs/CENTRAL_BRAIN_SOFTWARE_DETAILED_DESIGN.md)' \
  'central-brain/bindings/linux/ipc/' \
  'central_brain_github_remote_testing.json' \
  'tools/check_central_brain_android_runtime_evolution.sh' \
  'tools/check_central_brain_root_readme.sh' \
  'tools/check_central_brain_software_detailed_design.sh' \
  'android13-hwtest-v0.5.0-rc.2' \
  '5708dfa6' \
  '6ca306f4' \
  '909dfd83' \
  '74b71868' \
  'physical_controller_evidence_available=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'driver_development_triggered=false' \
  'virtualization_development_triggered=false' \
  'GitHub 不连接目标 ADB' \
  '不修改厂商 Android Framework'; do
  require_text "$marker"
done

if grep -Fq '# Unity Cabin APK Reverse Engineering Workspace' "$README"; then
  echo "root README still presents the local APK workspace as the repository architecture" >&2
  exit 1
fi

for path in \
  .github/ISSUE_TEMPLATE/hardware-test.yml \
  .github/workflows/central-brain-remote-test-contract.yml \
  .githooks/pre-push \
  apk-labs/client2-central-brain/README.md \
  central-brain/android-runtime/settings.gradle.kts \
  central-brain/backend/mock_npu_service.py \
  central-brain/bindings/android/README.md \
  central-brain/bindings/linux/README.md \
  central-brain/contracts/central_brain_api.json \
  central-brain/contracts/central_brain_github_remote_testing.json \
  central-brain/delivery/android-hybrid/central-brain.android-hybrid-delivery-profile.json \
  central-brain/deploy/linux/central-brain.package-profile.json \
  central-brain/linux-cli/central_brain_cli.py \
  docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md \
  docs/CENTRAL_BRAIN_SOFTWARE_DETAILED_DESIGN.md \
  docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_ROADMAP.md \
  tools/check_central_brain_android_runtime_evolution.sh \
  tools/check_central_brain_software_detailed_design.sh \
  tools/check_central_brain_github_remote_testing.sh; do
  [[ -f "$ROOT_DIR/$path" ]] \
    || { echo "README-mapped repository file is missing: $path" >&2; exit 1; }
done

python3 -B - "$ROOT_DIR" "$README" "$SETTINGS" <<'PY'
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
readme_path = pathlib.Path(sys.argv[2])
settings_path = pathlib.Path(sys.argv[3])
text = readme_path.read_text(encoding="utf-8")
settings = settings_path.read_text(encoding="utf-8")

modules = re.findall(r'include\(":([a-z0-9-]+)"\)', settings)
expected_modules = {
    "central-brain-sdk",
    "native-runtime",
    "runtime-service",
    "demo-hmi",
    "policy-probe",
}
if set(modules) != expected_modules:
    raise SystemExit(f"unexpected Android Gradle modules: {modules}")
for module in modules:
    if f"`{module}`" not in text:
        raise SystemExit(f"Android Gradle module missing from README: {module}")

relative_links = []
for target in re.findall(r"\[[^\]]+\]\(([^)]+)\)", text):
    if target.startswith(("http://", "https://", "#")):
        continue
    target = target.split("#", 1)[0]
    if target:
        relative_links.append(target)
for target in relative_links:
    if not (root / target).exists():
        raise SystemExit(f"README relative link does not exist: {target}")

recent_section = text.split("## 近期修改日志", 1)[1]
commit_links = re.findall(r"https://github\.com/LucasWEIchen/CougarOS/commit/", recent_section)
if len(commit_links) < 8:
    raise SystemExit("README recent change log must retain at least eight architecture commits")

required_req_groups = (
    "APP-004",
    "XSC-001..006",
    "NV-F-001/011/012",
    "NV-G-003/005/006/007",
    "DEL-001/003/004/005",
)
for group in required_req_groups:
    if group not in text:
        raise SystemExit(f"README architecture Req ID group missing: {group}")

print("root_readme_android_gradle_modules_verified=true")
print(f"root_readme_relative_links_verified={len(relative_links)}")
print(f"root_readme_recent_commit_links={len(commit_links)}")
PY

printf '%s\n' \
  'Central Brain repository architecture README check passed' \
  'root_readme_architecture_documented=true' \
  'root_readme_module_mapping_documented=true' \
  'root_readme_recent_changes_documented=true' \
  'production_ready=false' \
  'target_hardware_validated=false'
