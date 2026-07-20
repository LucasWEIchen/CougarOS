#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004/005/006, NV-G-007,
# DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-check}"
BASE_REVISION="${2:-}"
HEAD_REVISION="${3:-}"
CHANGE_RANGE_STATUS="not_requested"

if [[ "$MODE" != "check" && "$MODE" != "--pre-push" \
    && "$MODE" != "--changed-range" ]]; then
  echo "usage: $0 [--pre-push | --changed-range <base> <head>]" >&2
  exit 2
fi
if [[ "$MODE" == "--changed-range" \
    && ( -z "$BASE_REVISION" || -z "$HEAD_REVISION" ) ]]; then
  echo "--changed-range requires base and head revisions" >&2
  exit 2
fi

git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null

required_tracked_paths=(
  README.md
  .github/workflows/central-brain-remote-test-contract.yml
  .githooks/pre-push
  central-brain/README.md
  central-brain/android-runtime/settings.gradle.kts
  central-brain/android-runtime/central-brain-sdk/build.gradle.kts
  central-brain/android-runtime/native-runtime/build.gradle.kts
  central-brain/android-runtime/runtime-service/build.gradle.kts
  central-brain/android-runtime/demo-hmi/build.gradle.kts
  central-brain/android-runtime/policy-probe/build.gradle.kts
  apk-labs/client2-central-brain/README.md
  docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md
  docs/CENTRAL_BRAIN_GRANULAR_REQUIREMENT_CATALOG.md
  docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md
  docs/CENTRAL_BRAIN_COCKPIT_HMI_UX_DESIGN_MOCKUPS.md
  docs/CENTRAL_BRAIN_ROADMAP.md
  docs/ui/cockpit-hmi-design/index.html
  docs/ui/cockpit-hmi-design/styles.css
  docs/ui/cockpit-hmi-design/app.js
  docs/ui/cockpit-hmi-design/render_mockups.sh
  docs/assets/cockpit-hmi-design/01-intent.png
  docs/assets/cockpit-hmi-design/02-plan.png
  docs/assets/cockpit-hmi-design/03-execution.png
  docs/assets/cockpit-hmi-design/04-result.png
  tools/check_central_brain_root_readme.sh
  tools/check_central_brain_cockpit_hmi_design.sh
  tools/check_central_brain_github_publication_tree.sh
  tools/check_central_brain_github_repository_completeness.sh
)

for path in "${required_tracked_paths[@]}"; do
  git -C "$ROOT_DIR" ls-files --error-unmatch -- "$path" >/dev/null 2>&1 \
    || { echo "maintained project path is not tracked: $path" >&2; exit 1; }
done

FORMAL_UNTRACKED_PATTERN='^(central-brain/|apk-labs/client2-central-brain/|\.github/|\.githooks/|docs/CENTRAL_BRAIN_[^/]*|docs/(ui|assets)/cockpit-hmi-design/|tools/(build|check|install|package|run|test)_central_brain_)'
FORMAL_CHANGE_PATTERN='^(central-brain/|apk-labs/client2-central-brain/|docs/CENTRAL_BRAIN_|docs/(ui|assets)/cockpit-hmi-design/|tools/.*central_brain|\.github/|\.githooks/)'
untracked_formal="$({
  git -C "$ROOT_DIR" ls-files --others --exclude-standard \
    | grep -E "$FORMAL_UNTRACKED_PATTERN"
} || true)"
if [[ -n "$untracked_formal" ]]; then
  printf 'untracked maintained project files must be committed:\n%s\n' "$untracked_formal" >&2
  exit 1
fi

if git -C "$ROOT_DIR" ls-files \
    | grep -E '^(apks/|reverse/|logs/|keystores/|builds/|env\.sh$)' >/dev/null; then
  echo "controlled input, generated output, evidence, or secret path is tracked" >&2
  exit 1
fi

if [[ "$MODE" == "--pre-push" ]]; then
  tracked_dirty="$(git -C "$ROOT_DIR" status --porcelain --untracked-files=no)"
  if [[ -n "$tracked_dirty" ]]; then
    printf 'tracked modifications must be committed before push:\n%s\n' "$tracked_dirty" >&2
    exit 1
  fi
fi

if [[ "$MODE" == "--changed-range" ]]; then
  git -C "$ROOT_DIR" rev-parse --verify "$BASE_REVISION^{commit}" >/dev/null
  git -C "$ROOT_DIR" rev-parse --verify "$HEAD_REVISION^{commit}" >/dev/null
  changed_paths="$(git -C "$ROOT_DIR" diff --name-only "$BASE_REVISION" "$HEAD_REVISION")"
  if grep -Eq "$FORMAL_CHANGE_PATTERN" <<<"$changed_paths" \
      && ! grep -Fxq 'README.md' <<<"$changed_paths"; then
    echo "Central Brain project changes must update the GitHub homepage README" >&2
    exit 1
  fi
  CHANGE_RANGE_STATUS="true"
fi

tracked_project_file_count="$(
  git -C "$ROOT_DIR" ls-files \
    | grep -E '^(README\.md$|central-brain/|apk-labs/client2-central-brain/|\.github/|\.githooks/|docs/CENTRAL_BRAIN_|docs/(ui|assets)/cockpit-hmi-design/|tools/.*central_brain)' \
    | wc -l
)"
tracked_central_brain_doc_count="$(
  git -C "$ROOT_DIR" ls-files 'docs/CENTRAL_BRAIN_*.md' | wc -l
)"

((tracked_project_file_count >= 100)) \
  || { echo "tracked maintained project file inventory is unexpectedly small" >&2; exit 1; }
((tracked_central_brain_doc_count >= 20)) \
  || { echo "tracked Central Brain document inventory is unexpectedly small" >&2; exit 1; }

grep -Fq 'github_source_of_truth=true' "$ROOT_DIR/README.md"
grep -Fq 'maintained_project_files_synced=true' "$ROOT_DIR/README.md"
grep -Fq '## 开发进度总表' "$ROOT_DIR/README.md"

printf '%s\n' \
  'Central Brain GitHub repository completeness check passed' \
  'github_source_of_truth=true' \
  'github_sync_required=true' \
  "github_homepage_change_range_verified=$CHANGE_RANGE_STATUS" \
  'maintained_project_files_tracked=true' \
  "tracked_project_file_count=$tracked_project_file_count" \
  "tracked_central_brain_doc_count=$tracked_central_brain_doc_count" \
  'controlled_inputs_published=false' \
  'raw_evidence_published=false'
