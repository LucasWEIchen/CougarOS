#!/usr/bin/env bash
set -euo pipefail

# Req IDs: XSC-005, NV-G-006/007, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REVISION="${1:-HEAD}"
MAX_BLOB_BYTES=$((20 * 1024 * 1024))
FORBIDDEN_PATH_PATTERN='^(apks/|reverse/|builds/|logs/|keystores/|\.tools/|env\.sh$)'
FORBIDDEN_EXTENSION_PATTERN='\.(apk|aar|aab|dex|so|jks|keystore|pem|key|p12|pfx|zip|7z|tgz|tar|gz)$'

git -C "$ROOT_DIR" rev-parse --verify "$REVISION^{commit}" >/dev/null

MAX_OBSERVED_BYTES=0
MAX_OBSERVED_PATH=""
OBJECT_COUNT=0
while IFS=' ' read -r type object_id size path; do
  [[ "$type" == "blob" ]] || continue
  OBJECT_COUNT=$((OBJECT_COUNT + 1))
  if ((size > MAX_OBSERVED_BYTES)); then
    MAX_OBSERVED_BYTES=$size
    MAX_OBSERVED_PATH="$path"
  fi
  if ((size > MAX_BLOB_BYTES)); then
    echo "publication history blob exceeds $MAX_BLOB_BYTES bytes: $path ($size)" >&2
    exit 1
  fi
  if [[ "$path" =~ $FORBIDDEN_PATH_PATTERN ]]; then
    echo "publication history contains forbidden path: $path" >&2
    exit 1
  fi
  if [[ "${path,,}" =~ $FORBIDDEN_EXTENSION_PATTERN ]]; then
    echo "publication history contains forbidden binary/key extension: $path" >&2
    exit 1
  fi
done < <(
  git -C "$ROOT_DIR" rev-list --objects "$REVISION" \
    | git -C "$ROOT_DIR" cat-file --batch-check='%(objecttype) %(objectname) %(objectsize) %(rest)'
)

if git -C "$ROOT_DIR" grep -nI -E \
    -- '-----BEGIN ([A-Z0-9]+ )?PRIVATE KEY-----|github_pat_[A-Za-z0-9_]{20,}|ghp_[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}' \
    "$REVISION" --; then
  echo "publication history contains a credential-like marker" >&2
  exit 1
fi

[[ "$OBJECT_COUNT" -gt 0 ]] || { echo "publication history has no blobs" >&2; exit 1; }

printf '%s\n' \
  "github_publication_tree_verified=true" \
  "publication_revision=$(git -C "$ROOT_DIR" rev-parse "$REVISION^{commit}")" \
  "reachable_blob_count=$OBJECT_COUNT" \
  "max_reachable_blob_bytes=$MAX_OBSERVED_BYTES" \
  "max_reachable_blob_path=$MAX_OBSERVED_PATH" \
  "max_allowed_blob_bytes=$MAX_BLOB_BYTES" \
  "forbidden_legacy_paths_present=false" \
  "credential_marker_detected=false"
