#!/usr/bin/env bash
set -euo pipefail

# Compatibility entry point. P4-D4e remains historical evidence; P4-R2 is current.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HISTORICAL="$ROOT_DIR/central-brain/contracts/central_brain_android_p4_d4e_client2_scenario_chain.json"

python3 -B - "$HISTORICAL" <<'PY'
import json
import pathlib
import sys

contract = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
assert contract["profile_id"] == "android13-p4-d4e-client2-scenario-chain-v1"
assert contract["claim_state"]["implementation_stage"] == "P4-D4e"
PY

bash "$ROOT_DIR/tools/check_central_brain_android_client2_orchestration_migration.sh"
printf '%s\n' \
  'p4_d4e_contract_retained_as_historical_evidence=true' \
  'p4_d4e_legacy_client_runtime_path_active=false' \
  'p4_d4e_superseded_by=P4-R2'
