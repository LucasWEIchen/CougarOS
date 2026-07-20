#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001..006, S2-*, NV-*, DEL-001/004/005.
# The GitHub homepage is intentionally limited to the architecture diagram and
# the smallest traceable requirement work packages.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
README="$ROOT_DIR/README.md"

[[ -f "$README" ]] || { echo "missing repository README" >&2; exit 1; }

python3 - "$README" <<'PY'
from __future__ import annotations

import re
import sys
from pathlib import Path

readme_path = Path(sys.argv[1])
readme = readme_path.read_text(encoding="utf-8")

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
    "Identity --> Orchestration --> Durable",
    "Orchestration --> Context --> Scenario --> Graph",
    "Graph --> Model --> Prompt",
    "Graph --> Effect",
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
required_ids.extend(("P4-R2", "P4-R3"))
required_ids.extend(f"P5-W{i:02d}" for i in range(1, 11))
required_ids.append("P5-R1")
required_ids.extend(f"P6-W{i:02d}" for i in range(1, 7))
required_ids.append("P6-P7-R1")
required_ids.extend(f"P7-W{i:02d}" for i in range(1, 8))
required_ids.extend(("P7-R2", "P7-R3-OC2"))
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
if len(table_rows) != len(required_ids):
    raise SystemExit(
        f"README must have one row per smallest work package: rows={len(table_rows)}, "
        f"expected={len(required_ids)}"
    )
for row in table_rows:
    cells = [cell.strip() for cell in row.strip("|").split("|")]
    is_scope_row = row.startswith("| `SCOPE-")
    expected_cells = 4 if is_scope_row else 5
    if len(cells) != expected_cells:
        raise SystemExit(
            f"README tracking row has {len(cells)} columns; expected {expected_cells}: {row}"
        )
    if not is_scope_row and not cells[2]:
        raise SystemExit(f"README tracking row has no requirement trace: {row}")
    if not cells[-1].startswith("`") or not cells[-1].endswith("`"):
        raise SystemExit(f"README tracking row has no explicit status: {row}")

required_claims = (
    "github_source_of_truth=true",
    "maintained_project_files_synced=true",
    "repository_software_requirements_complete=true",
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
print("root_readme_h2_section_count=2")
PY

printf '%s\n' \
  'Central Brain focused GitHub homepage README check passed' \
  'root_readme_architecture_documented=true' \
  'root_readme_granular_requirement_tracking_documented=true' \
  'github_homepage_architecture_current=true' \
  'python_prototype_runtime_maintained=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
