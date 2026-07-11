#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${CENTRAL_BRAIN_SCENARIO_SMOKE_PORT:-18791}"
BASE_URL="http://127.0.0.1:${PORT}"
LOG_DIR="$ROOT_DIR/logs/test/central-brain-agent-scenarios"
SERVER_LOG="$LOG_DIR/backend.log"

mkdir -p "$LOG_DIR"

CENTRAL_BRAIN_SIMULATED_NPU_BACKEND=mock \
PYTHONDONTWRITEBYTECODE=1 \
python3 "$ROOT_DIR/central-brain/backend/mock_npu_service.py" \
  --host 127.0.0.1 \
  --port "$PORT" >"$SERVER_LOG" 2>&1 &
SERVER_PID=$!

cleanup() {
  kill "$SERVER_PID" >/dev/null 2>&1 || true
  wait "$SERVER_PID" >/dev/null 2>&1 || true
}
trap cleanup EXIT

BASE_URL="$BASE_URL" python3 - <<'PY'
import json
import os
import time
from urllib.error import URLError
from urllib.request import Request, urlopen

base_url = os.environ["BASE_URL"]


def call(path, body=None):
    encoded = None
    method = "GET"
    headers = {"Accept": "application/json"}
    if body is not None:
        encoded = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
        method = "POST"
    request = Request(base_url + path, data=encoded, headers=headers, method=method)
    with urlopen(request, timeout=10) as response:
        return json.loads(response.read().decode("utf-8"))


for _ in range(50):
    try:
        if call("/health").get("status") == "ok":
            break
    except URLError:
        time.sleep(0.1)
else:
    raise SystemExit("scenario smoke backend did not become ready")

catalog = call("/agent/scenarios")["payload"]
scenarios = catalog["scenarios"]
expected_ids = {
    "care.cold",
    "care.fatigue",
    "task.home",
    "skill.nap",
    "state.vehicle",
    "memory.preference",
    "skills.catalog",
    "security.denied",
    "security.privacy",
    "governance.audit",
    "runtime.npu",
    "system.overview",
}
assert {item["scenario_id"] for item in scenarios} == expected_ids
assert catalog["boundaries"]["product_compatibility_claimed"] is False
assert catalog["boundaries"]["service_dispatch_triggered"] is False

for scenario in scenarios:
    scenario_id = scenario["scenario_id"]
    payload = call(
        "/agent/scenarios/run",
        {
            "trace_id": f"scenario-smoke-{scenario_id}",
            "scenario_id": scenario_id,
            "utterance": scenario["label"],
            "runtime": "mock",
            "caller_permissions": [
                "vehicle.read",
                "vehicle.control",
                "service.read",
                "ai.infer",
                "policy.read",
            ],
            "vehicle_state": "parked",
            "safety_state": "normal",
        },
    )
    expected_outcome = "blocked_as_expected" if scenario_id.startswith("security.") else "passed"
    assert payload["status"] == "ok", payload
    assert payload["result"]["outcome"] == expected_outcome, payload
    assert payload["result"]["generated_text"], payload
    assert payload["result"]["checks"], payload
    assert payload["boundaries"]["real_vehicle_control_dispatched"] is False
    assert payload["boundaries"]["real_vehicle_control"] is False
    assert payload["boundaries"]["service_dispatch_triggered"] is False
    assert payload["boundaries"]["hardware_accessed"] is False
    assert payload["boundaries"]["driver_development_triggered"] is False
    assert payload["boundaries"]["virtualization_development_triggered"] is False
    assert "APP-004" in payload["req_ids"] and "XSC-001" in payload["req_ids"]

unknown = call("/agent/scenarios/run", {"scenario_id": "unknown"})
assert unknown["status"] == "error"
assert unknown["result"]["outcome"] == "unknown_scenario"
PY

python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" \
  agent-scenarios --base-url "$BASE_URL" >/dev/null
python3 "$ROOT_DIR/central-brain/linux-cli/central_brain_cli.py" \
  agent-scenario-home --base-url "$BASE_URL" >/dev/null

echo "Central Brain Agent scenarios smoke passed"
