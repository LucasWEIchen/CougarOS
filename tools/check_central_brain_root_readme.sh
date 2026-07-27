#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
README="$ROOT_DIR/README.md"

[[ -s "$README" ]] || { echo "missing repository README" >&2; exit 1; }
[[ "$(grep -c '^# CougarOS Central Brain$' "$README")" -eq 1 ]]
[[ "$(grep -c '^## 软件架构$' "$README")" -eq 1 ]]
[[ "$(grep -c '^## 权威文档$' "$README")" -eq 1 ]]
[[ "$(grep -c '^## 当前状态$' "$README")" -eq 1 ]]
[[ "$(grep -c '```mermaid' "$README")" -eq 1 ]]

for document in \
  docs/CENTRAL_BRAIN_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md \
  docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md; do
  [[ "$(grep -Fc "$document" "$README")" -eq 1 ]] \
    || { echo "README canonical document link missing or duplicated: $document" >&2; exit 1; }
done

grep -Fq '| `production_ready` | `false` |' "$README"
grep -Fq '| `target_hardware_validated` | `false` |' "$README"

printf '%s\n' \
  'Central Brain focused GitHub homepage README check passed' \
  'root_readme_architecture_documented=true' \
  'root_readme_canonical_document_count=3' \
  'root_readme_requirements_linked=true' \
  'production_ready=false' \
  'target_hardware_validated=false'
