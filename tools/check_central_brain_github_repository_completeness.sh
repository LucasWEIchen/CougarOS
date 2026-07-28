#!/usr/bin/env bash
set -euo pipefail

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
  central-brain/contracts/central_brain_android_multimodal_model_io_hmi_requirement_v1.json
  apk-labs/client2-central-brain/README.md
  docs/CENTRAL_BRAIN_REQUIREMENTS.md
  docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md
  docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md
  tools/check_central_brain_production_document_set.sh
  tools/check_central_brain_root_readme.sh
  tools/check_central_brain_github_publication_tree.sh
  tools/check_central_brain_github_repository_completeness.sh
)

for file in "${required_tracked_paths[@]}"; do
  if [[ "$MODE" == "--pre-push" ]]; then
    git -C "$ROOT_DIR" cat-file -e "HEAD:$file" 2>/dev/null \
      || { echo "maintained project path is not committed: $file" >&2; exit 1; }
  else
    [[ -e "$ROOT_DIR/$file" ]] \
      || { echo "maintained project path is missing: $file" >&2; exit 1; }
  fi
done

FORMAL_UNTRACKED_PATTERN='^(central-brain/|apk-labs/client2-central-brain/|\.github/|\.githooks/|docs/|tools/(build|check|install|package|run|test)_central_brain_)'
FORMAL_CHANGE_PATTERN='^(central-brain/|apk-labs/client2-central-brain/|docs/|tools/.*central_brain|\.github/|\.githooks/)'
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
    | grep -E '^(README\.md$|central-brain/|apk-labs/client2-central-brain/|\.github/|\.githooks/|docs/|tools/.*central_brain)' \
    | wc -l
)"
if git -C "$ROOT_DIR" diff --quiet --cached -- docs; then
  tracked_doc_count="$(git -C "$ROOT_DIR" ls-files 'docs/*' | wc -l)"
else
  tracked_doc_count="$(find "$ROOT_DIR/docs" -type f | wc -l)"
fi

((tracked_project_file_count >= 100)) \
  || { echo "tracked maintained project file inventory is unexpectedly small" >&2; exit 1; }
[[ "$tracked_doc_count" -eq 19 ]] \
  || {
    echo "production document set must contain 3 canonical documents and 16 module designs: $tracked_doc_count" >&2
    exit 1
  }

bash "$ROOT_DIR/tools/check_central_brain_production_document_set.sh" >/dev/null
bash "$ROOT_DIR/tools/check_central_brain_root_readme.sh" >/dev/null

printf '%s\n' \
  'Central Brain GitHub repository completeness check passed' \
  'github_source_of_truth=true' \
  'github_sync_required=true' \
  "github_homepage_change_range_verified=$CHANGE_RANGE_STATUS" \
  'maintained_project_files_tracked=true' \
  "tracked_project_file_count=$tracked_project_file_count" \
  "tracked_central_brain_doc_count=$tracked_doc_count" \
  'canonical_production_document_count=3' \
  'module_detailed_design_count=16' \
  'controlled_inputs_published=false' \
  'raw_evidence_published=false'
