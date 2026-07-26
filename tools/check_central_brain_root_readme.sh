#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001..006, S2-*, NV-*, DEL-001/004/005.
# The GitHub homepage is intentionally limited to the architecture diagram and
# detailed, code-linked, smallest traceable requirement work packages.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
README="$ROOT_DIR/README.md"
CATALOG="$ROOT_DIR/docs/CENTRAL_BRAIN_GRANULAR_REQUIREMENT_CATALOG.md"

[[ -f "$README" ]] || { echo "missing repository README" >&2; exit 1; }
[[ -f "$CATALOG" ]] || { echo "missing granular requirement catalog" >&2; exit 1; }

python3 - "$README" "$CATALOG" <<'PY'
from __future__ import annotations

import re
import sys
from pathlib import Path
from pathlib import PurePosixPath

readme_path = Path(sys.argv[1])
readme = readme_path.read_text(encoding="utf-8")
catalog_path = Path(sys.argv[2])
catalog = catalog_path.read_text(encoding="utf-8")
repo_root = readme_path.parent

if readme.count("# CougarOS Central Brain") != 1:
    raise SystemExit("README must contain exactly one project title")

h2_headings = re.findall(r"^## .+$", readme, flags=re.MULTILINE)
expected_h2 = [
    "## 软件架构框图",
    "## 开发进度总表：最细颗粒度需求跟进列表",
]
if h2_headings != expected_h2:
    raise SystemExit(
        "README H2 sections must be architecture plus granular requirement tracking only: "
        f"{h2_headings}"
    )

if readme.count("```mermaid") != 1 or readme.count("flowchart TB") != 1:
    raise SystemExit("README must contain exactly one top-to-bottom Mermaid architecture diagram")

architecture_markers = (
    'subgraph HMI["Android 13 座舱应用与 HMI"]',
    'subgraph SDK["Java SDK 与 Android Protocol Binding"]',
    'subgraph Runtime["Android AIOS Runtime"]',
    'subgraph Native["Native 与硬件接口边界"]',
    'subgraph Compute["模型算力路径"]',
    'subgraph Vehicle["车辆与安全外部边界"]',
    'subgraph Delivery["交付与测试闭环"]',
    "Client2 --> Voice --> HmiState --> ClientBridge --> JavaSdk",
    'ModelIO["模型 I/O 实时反馈\\n文字 / 图片缩略图 / 居中预览"]',
    "ClientBridge --> ModelIO --> HmiState",
    "Identity --> Orchestration --> Durable",
    "Orchestration --> Context --> Scenario --> Graph",
    "Graph --> Model --> Prompt",
    'Observation["Cabin Observation\\nVisible Facts / Seat Evidence"]',
    'Hypothesis["Shopping Intent\\nEvidence Bound / Confirm Required"]',
    'Confirmation["Shopping / Purchase / Navigation\\nIndependent Confirmation"]',
    'Navigation["Navigation Tool / Adapter\\nPOI / Route / Start"]',
    'Commerce["Commerce Tool / Adapter\\nSearch / Preview / Commit"]',
    "Model --> Observation --> Context",
    "Context --> Hypothesis --> Graph",
    "Graph --> Confirmation --> Effect",
    "Tool --> Navigation --> Effect",
    "Tool --> Commerce --> Effect",
    "RuntimeApi --> Jni --> CAbi --> NpuEmpty",
    'NpuEmpty -. "Vendor SDK / model / evidence required" .-> ProductionModel',
    'Effect -. "production adapter unavailable" .-> VehicleApi',
    'DriverContract -. "OEM interface required" .-> Safety',
    "Bundle --> GitHub --> TargetTest --> Issue",
)
for marker in architecture_markers:
    if marker not in readme:
        raise SystemExit(f"README architecture marker missing: {marker}")

for section in ("### 已开发并验证", "### 未开发或外部阻塞"):
    if readme.count(section) != 1:
        raise SystemExit(f"README requirement status section missing or duplicated: {section}")

required_ids: list[str] = []
required_ids.extend(f"P0-W{i:02d}" for i in range(1, 4))
required_ids.extend(f"P1-W{i:02d}" for i in range(1, 8))
required_ids.append("P6-EV2")
required_ids.extend(f"P2-W{i:02d}" for i in range(1, 13))
required_ids.extend(f"P3-W{i:02d}" for i in range(1, 10))
required_ids.append("P4-R1")
required_ids.extend(f"P4-W{i:02d}" for i in range(1, 13))
required_ids.extend(f"P4-D4{suffix}" for suffix in "abcde")
required_ids.extend(("P4-R2", "P4-R3", "P4-R4"))
required_ids.extend(f"P4-R5{suffix}" for suffix in "abcdefghijkl")
required_ids.append("P4-R6")
required_ids.append("P4-R7")
required_ids.extend(f"P5-W{i:02d}" for i in range(1, 11))
required_ids.append("P5-R1")
required_ids.extend(f"P6-W{i:02d}" for i in range(1, 7))
required_ids.append("P6-P7-R1")
required_ids.extend(f"P7-W{i:02d}" for i in range(1, 8))
required_ids.extend(("P7-R2", "P7-R3-OC2", "P7-R4-OCDEV", "P7-R5-MMDEV"))
required_ids.extend(("P9-W01", "P9-W02"))
required_ids.extend(f"P9-W03{suffix}" for suffix in "abcdefg")
required_ids.extend(f"P9-W04{suffix}" for suffix in "abc")
required_ids.extend(f"P9-W05{suffix}" for suffix in "ab")
required_ids.extend(f"P9-W06{suffix}" for suffix in "ab")
required_ids.extend(f"P9-W07{suffix}" for suffix in "abc")
required_ids.append("P10-R1")
required_ids.extend(f"P{stage}-ACT-01" for stage in range(3, 8))
required_ids.extend(f"P8-W{i:02d}" for i in range(1, 7))
required_ids.extend(f"P9-EXT-{i:02d}" for i in range(1, 8))
required_ids.extend(f"SCOPE-{i:02d}" for i in range(1, 6))

duplicates = [item for item in required_ids if readme.count(f"`{item}`") != 1]
if duplicates:
    raise SystemExit(f"README work package IDs must appear exactly once: {duplicates}")

tracked_ids = set(
    re.findall(
        r"`((?:P\d+-(?:W|R|D|EV|EXT|ACT)[A-Za-z0-9-]*)|(?:SCOPE-\d+))`",
        readme,
    )
)
unexpected_ids = sorted(tracked_ids - set(required_ids))
if unexpected_ids:
    raise SystemExit(f"README contains unclassified work package IDs: {unexpected_ids}")

table_rows = [
    line for line in readme.splitlines()
    if line.startswith("| `P") or line.startswith("| `SCOPE-")
]
expected_header = "| 跟进 ID | 详细需求 | Req IDs | 代码/接口 | 已交付与证据 | 状态 |"
if readme.count(expected_header) != 15:
    raise SystemExit("README must use the six-column detailed requirement header 15 times")
if len(table_rows) != len(required_ids):
    raise SystemExit(
        f"README must have one row per smallest work package: rows={len(table_rows)}, "
        f"expected={len(required_ids)}"
    )
code_link_count = 0
for row in table_rows:
    cells = [cell.strip() for cell in row.strip("|").split("|")]
    if len(cells) != 6:
        raise SystemExit(
            f"README tracking row has {len(cells)} columns; expected 6: {row}"
        )
    if not cells[2]:
        raise SystemExit(f"README tracking row has no requirement trace: {row}")
    if not cells[4]:
        raise SystemExit(f"README tracking row has no delivery/evidence summary: {row}")
    if not cells[-1].startswith("`") or not cells[-1].endswith("`"):
        raise SystemExit(f"README tracking row has no explicit status: {row}")
    item_match = re.fullmatch(r"`([^`]+)`", cells[0])
    if item_match is None:
        raise SystemExit(f"README tracking row has invalid work package ID: {row}")
    item_id = item_match.group(1)
    expected_target = (
        "docs/CENTRAL_BRAIN_GRANULAR_REQUIREMENT_CATALOG.md#"
        f"{item_id.lower()}"
    )
    detail_match = re.fullmatch(
        r"\[([^\]]+)\]\(([^)]+)\)<br>需求：(.*?)<br>模块：(.*?)"
        r"<br>输入：(.*?)<br>验收：(.*?)<br>边界：(.*)",
        cells[1],
    )
    if detail_match is None or detail_match.group(2) != expected_target:
        raise SystemExit(
            f"README detailed requirement must target its exact catalog anchor: {row}"
        )
    section_match = re.search(
        rf"^### {re.escape(item_id)} ([^\n]+)\n(?P<body>.*?)(?=^### |\Z)",
        catalog,
        flags=re.MULTILINE | re.DOTALL,
    )
    if section_match is None:
        raise SystemExit(f"granular requirement catalog section missing: {item_id}")
    if section_match.group(1) != detail_match.group(1):
        raise SystemExit(
            f"README label and catalog heading differ for {item_id}: "
            f"readme={detail_match.group(1)!r}, catalog={section_match.group(1)!r}"
        )
    section_body = section_match.group("body")
    catalog_fields = {}
    for field_name in (
        "需求描述", "需求追踪", "负责模块", "前置输入", "输出与验收", "边界与非目标",
        "代码对应", "当前状态",
    ):
        field_match = re.search(
            rf"^- \*\*{re.escape(field_name)}\*\*：(.*)$",
            section_body,
            flags=re.MULTILINE,
        )
        if field_match is None:
            raise SystemExit(f"catalog field missing for {item_id}: {field_name}")
        catalog_fields[field_name] = field_match.group(1)
    if list(detail_match.groups()[2:]) != [
        catalog_fields["需求描述"],
        catalog_fields["负责模块"],
        catalog_fields["前置输入"],
        catalog_fields["输出与验收"],
        catalog_fields["边界与非目标"],
    ]:
        raise SystemExit(f"README and catalog detailed requirement differ for {item_id}")
    if catalog_fields["需求追踪"] != f"{cells[2]}。":
        raise SystemExit(f"README and catalog requirement trace differ for {item_id}")
    if catalog_fields["代码对应"] != f"{cells[3]}。":
        raise SystemExit(f"README and catalog code trace differ for {item_id}")
    if catalog_fields["当前状态"] != f"{cells[-1]}。":
        raise SystemExit(f"README and catalog status differ for {item_id}")

    if item_id.startswith("P0-"):
        expected_category = "设计门禁"
    elif item_id == "P10-R1":
        expected_category = "聚合门禁"
    elif item_id == "P9-W03f":
        expected_category = "无执行代码；撤回门禁"
    elif item_id == "P9-W03g":
        expected_category = "非执行接口"
    elif item_id.startswith("SCOPE-"):
        expected_category = "无实现；范围门禁"
    elif "-ACT-" in item_id or item_id.startswith(("P8-", "P9-EXT-")):
        expected_category = "空接口/准入"
    elif item_id == "P7-R3-OC2":
        expected_category = "过渡实现"
    elif item_id.startswith("P4-R5"):
        expected_category = "Debug 实现"
    elif item_id in ("P4-R6", "P4-R7"):
        expected_category = "Client2/Unity 实现"
    elif re.match(r"^(P2-W0[89]|P2-W1[0-2]|P4-D4|P5-R1|P6-P7-R1|P7-R2|P7-R4-OCDEV|P7-R5-MMDEV)", item_id):
        expected_category = "Debug 实现"
    elif item_id.startswith("P1-") or item_id == "P6-EV2" or item_id.startswith("P9-W"):
        expected_category = "合同实现"
    elif item_id == "P4-R4":
        expected_category = "合同实现"
    elif item_id.startswith("P4-"):
        expected_category = "Client2/Runtime 实现"
    else:
        expected_category = "软件实现"
    if not cells[3].startswith(f"{expected_category}："):
        raise SystemExit(f"README code classification differs for {item_id}")

    code_links = re.findall(r"\[([^\]]+)\]\(([^)]+)\)", cells[3])
    if not code_links:
        raise SystemExit(f"README code trace has no links for {item_id}")
    code_link_count += len(code_links)
    source_link_present = False
    for label, target in code_links:
        target_match = re.fullmatch(
            r"https://github\.com/LucasWEIchen/CougarOS/blob/main/"
            r"([^#]+)#L([1-9][0-9]*)-L([1-9][0-9]*)",
            target,
        )
        if target_match is None:
            raise SystemExit(f"README code trace is not a main-branch line link: {target}")
        relative_path = target_match.group(1)
        line_start = int(target_match.group(2))
        line_end = int(target_match.group(3))
        if label != PurePosixPath(relative_path).name:
            raise SystemExit(f"README code link label does not match its file: {target}")
        if relative_path.startswith(("apks/", "reverse/", "logs/", "builds/")):
            raise SystemExit(f"README code trace targets a controlled/generated path: {target}")
        if not relative_path.startswith((
            "central-brain/android-runtime/", "central-brain/contracts/",
            "apk-labs/client2-central-brain/", "apk-labs/renderservice-central-brain/",
            "tools/", ".github/",
        )):
            raise SystemExit(f"README code trace target is outside maintained code: {target}")
        source_path = repo_root / relative_path
        if not source_path.is_file():
            raise SystemExit(f"README code trace target does not exist: {target}")
        source_line_count = len(source_path.read_text(encoding="utf-8").splitlines())
        if not (1 <= line_start <= line_end <= source_line_count):
            raise SystemExit(f"README code trace line range is invalid: {target}")
        if line_end - line_start > 40:
            raise SystemExit(f"README code trace line range is too broad: {target}")
        if relative_path.startswith((
            "central-brain/android-runtime/", "apk-labs/client2-central-brain/",
        )) and PurePosixPath(relative_path).suffix in {".java", ".aidl", ".c", ".h", ".xml"}:
            source_link_present = True

    no_source_required = (
        item_id.startswith("P0-") or item_id in {"P4-R4", "P9-W03f", "P9-W03g", "P10-R1"}
        or item_id.startswith("P4-R5")
        or "-ACT-" in item_id or item_id.startswith(("P8-", "P9-EXT-", "SCOPE-"))
    )
    if not no_source_required and not source_link_present:
        raise SystemExit(f"implemented README item has no Android/Client2 source link: {item_id}")

expected_anchor_ids = {item.lower() for item in required_ids}
catalog_anchor_ids = re.findall(r'^<a id="([a-z0-9-]+)"></a>$', catalog, re.MULTILINE)
if len(catalog_anchor_ids) != len(required_ids):
    raise SystemExit(
        "granular requirement catalog must contain exactly one anchor per work package: "
        f"anchors={len(catalog_anchor_ids)}, expected={len(required_ids)}"
    )
if len(set(catalog_anchor_ids)) != len(catalog_anchor_ids):
    raise SystemExit("granular requirement catalog contains duplicate anchors")
if set(catalog_anchor_ids) != expected_anchor_ids:
    missing = sorted(expected_anchor_ids - set(catalog_anchor_ids))
    extra = sorted(set(catalog_anchor_ids) - expected_anchor_ids)
    raise SystemExit(f"granular requirement catalog anchor mismatch: missing={missing}, extra={extra}")

catalog_heading_ids = re.findall(
    r"^### ((?:P\d+-[A-Za-z0-9-]+)|(?:SCOPE-\d+))(?:\s|$)",
    catalog,
    re.MULTILINE,
)
if catalog_heading_ids != required_ids:
    raise SystemExit(
        "granular requirement catalog headings must follow the README work-package order: "
        f"headings={len(catalog_heading_ids)}, expected={len(required_ids)}"
    )

required_catalog_fields = (
    "- **需求描述**：",
    "- **需求追踪**：",
    "- **负责模块**：",
    "- **前置输入**：",
    "- **输出与验收**：",
    "- **边界与非目标**：",
    "- **代码对应**：",
    "- **当前状态**：",
    "- **权威依据**：",
)
for field in required_catalog_fields:
    count = catalog.count(field)
    if count != len(required_ids):
        raise SystemExit(
            f"granular requirement catalog field count mismatch: {field}={count}, "
            f"expected={len(required_ids)}"
        )

for target in re.findall(r"\[[^\]]+\]\(([^)]+)\)", catalog):
    if target.startswith(("http://", "https://", "#")):
        continue
    relative_path = target.split("#", 1)[0]
    if not (catalog_path.parent / relative_path).is_file():
        raise SystemExit(f"granular requirement catalog link target does not exist: {target}")

for claim in ("production_ready=false", "target_hardware_validated=false"):
    if claim not in catalog:
        raise SystemExit(f"granular requirement catalog boundary claim missing: {claim}")

required_claims = (
    "github_source_of_truth=true",
    "maintained_project_files_synced=true",
    "repository_software_requirements_complete=true",
    "open_repository_software_requirement_count=0",
    "unclassified_repository_requirement_count=0",
    "python_prototype_runtime_maintained=false",
    "production_ready=false",
    "target_hardware_validated=false",
)
for claim in required_claims:
    if claim not in readme:
        raise SystemExit(f"README boundary claim missing: {claim}")

remaining = readme.split("### 未开发或外部阻塞", 1)[1]
for forbidden in ("`IN_PROGRESS`", "`NOT_STARTED`", "待开发", "未开始"):
    if forbidden in remaining:
        raise SystemExit(f"README contains unclassified remaining work: {forbidden}")

for status in ("`EXTERNAL_BLOCKED`", "`SUSPENDED / EXTERNAL_BLOCKED`", "`OUT_OF_SCOPE`", "`SUSPENDED`"):
    if status not in remaining:
        raise SystemExit(f"README remaining-work classification missing: {status}")

print(f"root_readme_granular_requirement_rows={len(table_rows)}")
print(f"root_readme_requirement_links_verified={len(table_rows)}")
print(f"root_readme_code_segment_links_verified={code_link_count}")
print(f"granular_requirement_catalog_entries={len(catalog_anchor_ids)}")
print("root_readme_h2_section_count=2")
PY

printf '%s\n' \
  'Central Brain focused GitHub homepage README check passed' \
  'root_readme_architecture_documented=true' \
  'root_readme_granular_requirement_tracking_documented=true' \
  'root_readme_granular_requirement_catalog_linked=true' \
  'root_readme_detailed_requirements_embedded=true' \
  'root_readme_code_traceability_verified=true' \
  'github_homepage_architecture_current=true' \
  'python_prototype_runtime_maintained=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
