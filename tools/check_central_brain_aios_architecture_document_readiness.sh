#!/usr/bin/env bash
set -euo pipefail

# Compatibility entry. Production documentation now has one centralized gate.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec bash "$ROOT_DIR/tools/check_central_brain_production_document_set.sh"
