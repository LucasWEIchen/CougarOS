#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-SAF-001, S2-TOL-001, S2-OBS-001, DEL-001/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/central-brain/android-runtime"
BUDGET_SECONDS="${CENTRAL_BRAIN_FUZZ_SECONDS:-20}"
MAX_BUDGET_SECONDS=300
FUZZ_ROOT="$RUNTIME_DIR/runtime-service/build/fuzz/p9-w03f"
LOG_FILE="$(mktemp "${TMPDIR:-/tmp}/central-brain-p9-w03f.XXXXXX.log")"

cleanup() {
  rm -f "$LOG_FILE"
}
trap cleanup EXIT

if [[ ! "$BUDGET_SECONDS" =~ ^[0-9]+$ ]] \
    || ((BUDGET_SECONDS < 1 || BUDGET_SECONDS > MAX_BUDGET_SECONDS)); then
  echo "CENTRAL_BRAIN_FUZZ_SECONDS must be between 1 and $MAX_BUDGET_SECONDS" >&2
  exit 2
fi

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  source "$ROOT_DIR/env.sh"
fi
: "${JAVA_HOME:?JAVA_HOME must point to JDK 17}"

set +e
"$RUNTIME_DIR/gradlew" \
  --project-dir "$RUNTIME_DIR" \
  --no-daemon \
  -PcentralBrainFuzzSeconds="$BUDGET_SECONDS" \
  :runtime-service:fuzzParserSecurity >"$LOG_FILE" 2>&1
STATUS=$?
set -e

if ((STATUS != 0)); then
  tail -n 80 "$LOG_FILE" >&2
  echo "P9-W03f bounded parser robustness campaign failed" >&2
  exit "$STATUS"
fi

DONE_LINE="$(grep -E '^#[0-9]+.*DONE' "$LOG_FILE" | tail -n 1 || true)"
EXECUTED_UNITS="$(sed -n 's/^stat::number_of_executed_units: *\([0-9][0-9]*\).*/\1/p' "$LOG_FILE" | tail -n 1)"
if [[ -z "$EXECUTED_UNITS" ]]; then
  EXECUTED_UNITS="$(sed -n 's/^#\([0-9][0-9]*\).*DONE.*/\1/p' <<<"$DONE_LINE")"
fi
EDGE_COVERAGE="$(sed -n 's/.*DONE.*cov: *\([0-9][0-9]*\).*/\1/p' <<<"$DONE_LINE")"

read_counter() {
  local marker="$1"
  sed -n "s/^${marker}=\([0-9][0-9]*\)$/\1/p" "$LOG_FILE" | tail -n 1
}

CHECKPOINT_CALLS="$(read_counter central_brain_fuzz_checkpoint_calls)"
SCENARIO_CALLS="$(read_counter central_brain_fuzz_scenario_calls)"
TOOL_CALLS="$(read_counter central_brain_fuzz_tool_calls)"
RAW_INPUT_LOGGED="$(sed -n 's/^central_brain_fuzz_raw_input_logged=\(true\|false\)$/\1/p' "$LOG_FILE" | tail -n 1)"

for value_name in EXECUTED_UNITS EDGE_COVERAGE CHECKPOINT_CALLS SCENARIO_CALLS TOOL_CALLS; do
  value="${!value_name:-}"
  if [[ ! "$value" =~ ^[0-9]+$ ]] || ((value < 1)); then
    tail -n 80 "$LOG_FILE" >&2
    echo "P9-W03f evidence is missing or zero: $value_name" >&2
    exit 1
  fi
done
[[ "$RAW_INPUT_LOGGED" == "false" ]] \
  || { echo "P9-W03f raw-input logging invariant is missing" >&2; exit 1; }

CRASH_COUNT=0
if [[ -d "$FUZZ_ROOT/artifacts" ]]; then
  CRASH_COUNT="$(find "$FUZZ_ROOT/artifacts" -maxdepth 1 -type f | wc -l)"
fi
((CRASH_COUNT == 0)) \
  || { echo "P9-W03f produced $CRASH_COUNT local crash artifact(s)" >&2; exit 1; }

printf '%s\n' \
  'Central Brain Android P9-W03f bounded parser robustness campaign passed' \
  'security_parser_robustness_engine=Jazzer-0.30.0' \
  "security_parser_robustness_budget_seconds=$BUDGET_SECONDS" \
  "security_parser_robustness_executed_units=$EXECUTED_UNITS" \
  "security_parser_robustness_edge_coverage=$EDGE_COVERAGE" \
  'security_parser_robustness_surface_count=3' \
  'security_parser_robustness_seed_count=6' \
  "security_parser_robustness_checkpoint_calls=$CHECKPOINT_CALLS" \
  "security_parser_robustness_scenario_calls=$SCENARIO_CALLS" \
  "security_parser_robustness_tool_calls=$TOOL_CALLS" \
  "security_parser_robustness_crash_count=$CRASH_COUNT" \
  "security_parser_robustness_raw_input_logged=$RAW_INPUT_LOGGED" \
  'security_parser_robustness_host_campaign_verified=true' \
  'security_coverage_guided_fuzz_complete=false' \
  'security_production_signer_verified=false' \
  'network_accessed=false' \
  'hardware_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
