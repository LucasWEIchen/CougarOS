#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REQ="$ROOT_DIR/docs/CENTRAL_BRAIN_REQUIREMENTS.md"
ARCH="$ROOT_DIR/docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md"
DEV="$ROOT_DIR/docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md"
README="$ROOT_DIR/README.md"

expected_docs=(
  "CENTRAL_BRAIN_REQUIREMENTS.md"
  "CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md"
  "CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md"
  "modules/01-sdk-binder-contracts.md"
  "modules/02-runtime-service-composition.md"
  "modules/03-session-persistence.md"
  "modules/04-context-vehicle-twin.md"
  "modules/05-scenario-plan.md"
  "modules/06-agent-graph-orchestration.md"
  "modules/07-governance-approval-identity.md"
  "modules/08-tool-skill-runtime.md"
  "modules/09-memory-lifecycle.md"
  "modules/10-event-trigger-suggestion.md"
  "modules/11-model-scheduler-openclaw.md"
  "modules/11a-openclaw-production-ethernet-api.md"
  "modules/12-effect-vehicle-adapter.md"
  "modules/13-client2-hmi.md"
  "modules/14-renderservice-unity.md"
  "modules/15-native-runtime.md"
  "modules/16-security-privacy-release-observability.md"
)
mapfile -t actual_docs < <(
  git -C "$ROOT_DIR" ls-files 'docs/**' \
    | sed 's#^docs/##' \
    | LC_ALL=C sort
)
if [[ "${actual_docs[*]}" != "${expected_docs[*]}" ]]; then
  printf 'docs/ must contain the three canonical production documents and the exact module detailed-design set.\nexpected:\n%s\nactual:\n%s\n' \
    "$(printf '%s\n' "${expected_docs[@]}")" \
    "$(printf '%s\n' "${actual_docs[@]}")" >&2
  exit 1
fi

mapfile -t MODULE_DOCS < <(
  find "$ROOT_DIR/docs/modules" -maxdepth 1 -type f -name '*.md' -print | LC_ALL=C sort
)

for file in "$REQ" "$ARCH" "$DEV" "${MODULE_DOCS[@]}"; do
  [[ -s "$file" ]] || { echo "production document missing or empty: $file" >&2; exit 1; }
  grep -Fq 'production_document_scope=true' "$file" \
    || { echo "production scope marker missing: $file" >&2; exit 1; }
  grep -Fq 'production_ready=false' "$file" \
    || { echo "production readiness boundary missing: $file" >&2; exit 1; }
  grep -Fq 'target_hardware_validated=false' "$file" \
    || { echo "target validation boundary missing: $file" >&2; exit 1; }
done

grep -Fq 'production_requirements_document=true' "$REQ"
grep -Fq 'production_architecture_document=true' "$ARCH"
grep -Fq 'production_development_document=true' "$DEV"
for file in "${MODULE_DOCS[@]}"; do
  grep -Fq 'module_detailed_design=true' "$file" \
    || { echo "module detailed-design marker missing: $file" >&2; exit 1; }
done

python3 - "$REQ" "$ARCH" "$DEV" "$README" "$ROOT_DIR" <<'PY'
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

requirements_path, architecture_path, development_path, readme_path, root_path = (
    Path(value) for value in sys.argv[1:]
)
requirements = requirements_path.read_text(encoding="utf-8")
architecture = architecture_path.read_text(encoding="utf-8")
development = development_path.read_text(encoding="utf-8")
readme = readme_path.read_text(encoding="utf-8")
root = root_path
module_paths = sorted((root / "docs" / "modules").glob("*.md"))
if len(module_paths) != 17:
    raise SystemExit(f"module detailed-design count must be 17: {len(module_paths)}")
modules = {path: path.read_text(encoding="utf-8") for path in module_paths}

work_ids = re.findall(
    r"^\| `((?:P\d+(?:-P\d+)?-(?:W|R|D|EV|EXT|ACT)[A-Za-z0-9-]*)|(?:SCOPE-\d+))`",
    requirements,
    flags=re.MULTILINE,
)
if len(work_ids) != 145 or len(set(work_ids)) != 145:
    raise SystemExit(
        f"requirements must track 145 unique work packages: rows={len(work_ids)}, "
        f"unique={len(set(work_ids))}"
    )
for required_id in (
    "P0-W01", "P1-W01", "P4-R1", "P4-R7", "P4-R8", "P4-R9", "P4-R12", "P4-R13", "P4-R14", "P7-R3-OC2",
    "P6-P7-R1", "P8-W05", "P9-EXT-07", "P10-R1", "SCOPE-05",
):
    if required_id not in work_ids:
        raise SystemExit(f"required work package missing: {required_id}")

required_atomic_ids = (
    "APP-001", "APP-002", "APP-003", "APP-004", "APP-005", "APP-006",
    "APP-007",
    "S2-SES-001", "S2-GRF-001", "S2-EVT-001", "S2-EFF-001",
    "S2-TRG-002", "S2-SCN-001", "S2-SCN-002", "S2-SCN-003",
    "S2-SCN-004", "S2-SCN-005", "S2-SCN-006", "S2-INT-001", "S2-NAV-001",
    "S2-COM-001", "S2-MDL-001", "S2-MDL-002", "S2-MDL-003",
    "S2-MDL-004", "S2-MDL-005", "S2-MDL-006", "S2-MDL-007", "S2-MDL-008", "S2-TOL-001",
    "S2-TOL-002", "S2-SKL-001", "S2-MEM-001", "S2-MEM-002",
    "S2-CTX-001", "S2-CTX-002", "S2-TWN-001", "S2-ADP-001",
    "S2-ADP-002", "S2-ADP-003", "S2-ADP-004", "S2-SAF-001",
    "S2-SAF-002", "S2-SAF-003", "S2-SAF-004", "S2-SAF-005", "S2-SAF-006",
    "S2-UX-001", "S2-UX-002", "S2-UX-003", "S2-HMI-001",
    "S2-HMI-002", "S2-HMI-003", "S2-HMI-004", "S2-HMI-005",
    "S2-HMI-006", "S2-HMI-007", "S2-HMI-008", "S2-HMI-009",
    "S2-HMI-010", "S2-HMI-011", "S2-HMI-012", "S2-HMI-013",
    "S2-HMI-014",
    "S2-PER-001", "S2-OBS-001", "S2-OBS-002", "S2-REL-001",
)
for requirement_id in required_atomic_ids:
    if f"| `{requirement_id}` |" not in requirements:
        raise SystemExit(f"atomic production requirement is not defined: {requirement_id}")
module_corpus = "\n".join(modules.values())
for requirement_id in required_atomic_ids:
    if f"`{requirement_id}`" not in module_corpus:
        raise SystemExit(
            f"atomic production requirement is not mapped to a module design: "
            f"{requirement_id}"
        )

required_requirement_markers = (
    "## 2. 产品定义",
    "## 3. 状态模型",
    "## 4. 系统级功能需求",
    "## 5. 非功能需求",
    "## 6. 生产激活门槛",
    "## 7. 全量需求跟踪",
    "## 8. 退出生产基线的历史 ID",
    "S2-MDL-001",
    "S2-ADP-002",
    "S2-SAF-001",
)
for marker in required_requirement_markers:
    if marker not in requirements:
        raise SystemExit(f"requirements marker missing: {marker}")

mermaid_count = architecture.count("```mermaid")
if mermaid_count < 12:
    raise SystemExit(f"architecture must contain at least 12 Mermaid diagrams: {mermaid_count}")
for marker in (
    "## 2. 系统上下文",
    "## 3. 生产部署架构",
    "## 4. 逻辑分层",
    "## 5. 核心模块",
    "## 6. 关键业务流程",
    "## 7. 数据架构",
    "## 8. 安全、隐私与发布架构",
    "## 9. 可用性与性能",
    "## 10. 生产集成缺口",
):
    if marker not in architecture:
        raise SystemExit(f"architecture marker missing: {marker}")

for marker in (
    "## 2. 源码结构与模块归属",
    "### 2.1 模块详设索引",
    "## 5. AIDL 对外接口",
    "## 6. SDK 详设",
    "## 8. Session 与 Persistence",
    "## 11. Agent Graph Runtime",
    "## 12. Governance 与 Approval",
    "## 15. Model Runtime 与外部 Provider",
    "## 16. Effect 与 Adapter",
    "## 17. Client2 HMI 详设",
    "## 18. Native Runtime",
    "ICentralBrainRuntime",
    "ICentralBrainSessionRuntime",
    "ICentralBrainSessionEventsV2",
    "ICentralBrainOrchestration",
    "ICentralBrainGovernance",
    "cb_runtime_create_v1",
):
    if marker not in development:
        raise SystemExit(f"development marker missing: {marker}")

required_module_headings = (
    "## 1. 设计目标与边界",
    "## 2. 需求映射",
    "## 3. 源码地图",
    "## 4. 核心设计",
    "## 5. 接口与数据",
    "## 6. 关键流程",
    "## 7. 失败关闭与并发",
    "## 8. 代码校对清单",
    "## 9. 增量开发规则",
    "## 10. 当前缺口",
)
for module_path, module in modules.items():
    for heading in required_module_headings:
        if heading not in module:
            raise SystemExit(
                f"module detailed-design heading missing in {module_path.name}: {heading}"
            )
    if not re.search(r"`(?:APP|S2)-[A-Z0-9-]+`", module):
        raise SystemExit(f"module has no production Req ID mapping: {module_path.name}")
    source_links = re.findall(r"\]\((\.\./\.\./[^)#]+)(?:#[^)]+)?\)", module)
    if len(source_links) < 5:
        raise SystemExit(
            f"module must link at least five source artifacts: "
            f"{module_path.name}={len(source_links)}"
        )
    index_target = f"modules/{module_path.name}"
    if development.count(index_target) != 1:
        raise SystemExit(
            f"development document must link module exactly once: {index_target}"
        )
    for target in re.findall(r"\]\(([^)]+)\)", module):
        relative_target = target.split("#", 1)[0]
        if "://" in relative_target or not relative_target:
            continue
        resolved = (module_path.parent / relative_target).resolve()
        try:
            resolved.relative_to(root.resolve())
        except ValueError as error:
            raise SystemExit(
                f"module link escapes repository: {module_path.name}:{target}"
            ) from error
        if not resolved.exists():
            raise SystemExit(
                f"module link target missing: {module_path.name}:{target}"
            )

for forbidden in (
    r"\btestboard\b",
    r"\bADB\b",
    r"\bWSL\b",
    r"\bemulator\b",
    r"\bdebug\b",
    r"\bsimulated\b",
    r"\bsimulation\b",
    r"开发环境",
    r"测试环境",
    r"模拟器",
    r"测试板",
    r"反编译",
    r"逆向",
    r"localhost",
    r"127\.0\.0\.1",
):
    documents = [
        (requirements_path, requirements),
        (architecture_path, architecture),
        (development_path, development),
        *modules.items(),
    ]
    for path, content in documents:
        if re.search(forbidden, content, flags=re.IGNORECASE):
            raise SystemExit(
                f"environment-specific content found in {path.name}: {forbidden}"
            )

all_formal_documents = requirements + architecture + development + readme
all_formal_documents += "".join(modules.values())
if "Iluvatar1!" in all_formal_documents:
    raise SystemExit("embedded credential must not be copied into formal documentation")

expected_links = (
    "docs/CENTRAL_BRAIN_REQUIREMENTS.md",
    "docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md",
    "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md",
)
for link in expected_links:
    if readme.count(link) != 1:
        raise SystemExit(f"README must link canonical document exactly once: {link}")

legacy_references = []
repository_files = subprocess.run(
    ["git", "-C", str(root), "ls-files", "-co", "--exclude-standard"],
    check=True,
    capture_output=True,
    text=True,
).stdout.splitlines()
for relative in repository_files:
    path = root / relative
    if not path.is_file() or path.parent == root / "docs":
        continue
    try:
        text = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        continue
    for match in re.findall(r"docs/(CENTRAL_BRAIN_[A-Z0-9_]+\.md)", text):
        if match not in {
            "CENTRAL_BRAIN_REQUIREMENTS.md",
            "CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md",
            "CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md",
        }:
            # The retirement checker intentionally names removed prototype documents.
            if path.name == "check_central_brain_python_prototype_retirement.sh":
                continue
            legacy_references.append(f"{path.relative_to(root)}:{match}")
if legacy_references:
    raise SystemExit("legacy document references remain:\n" + "\n".join(legacy_references))

print("production_requirement_work_package_count=145")
print(f"production_architecture_mermaid_diagram_count={mermaid_count}")
print(f"production_module_detailed_design_count={len(module_paths)}")
PY

printf '%s\n' \
  'central_brain_production_document_set_ready=true' \
  'central_brain_canonical_document_count=3' \
  'central_brain_module_detailed_design_count=17' \
  'central_brain_docs_file_count=20' \
  'production_environment_only=true' \
  'legacy_document_reference_count=0' \
  'production_ready=false' \
  'target_hardware_validated=false'
