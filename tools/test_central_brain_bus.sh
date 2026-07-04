#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${CENTRAL_BRAIN_TEST_PORT:-8877}"
BASE_URL="http://127.0.0.1:${PORT}"

cleanup() {
  if [[ -n "${SERVER_PID:-}" ]]; then
    kill "$SERVER_PID" >/dev/null 2>&1 || true
    wait "$SERVER_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

CENTRAL_BRAIN_PORT="$PORT" python3 "$ROOT_DIR/central-brain/backend/mock_npu_service.py" \
  --host 127.0.0.1 --port "$PORT" >/tmp/central-brain-bus-test.log 2>&1 &
SERVER_PID="$!"

for _ in {1..30}; do
  if curl -fsS "$BASE_URL/health" >/dev/null 2>&1; then
    break
  fi
  sleep 0.2
done

check_get() {
  local path="$1"
  curl -fsS "$BASE_URL$path" | python3 -m json.tool >/dev/null
  echo "GET $path OK"
}

check_post() {
  local path="$1"
  local body="$2"
  curl -fsS -X POST "$BASE_URL$path" \
    -H 'Content-Type: application/json' \
    -d "$body" | python3 -m json.tool >/dev/null
  echo "POST $path OK"
}

check_get /context
check_get /state
check_get /events/topics
check_get /tools
check_post /permission/check '{"trace_id":"smoke-permission","permissions":["vehicle.read"],"caller_permissions":["vehicle.read"],"vehicle_state":"parked","safety_state":"normal"}'
check_post /actions/request '{"trace_id":"smoke-action","action":"Cabin.SetTemperature","permissions":["vehicle.control"],"caller_permissions":["vehicle.read","vehicle.control"],"vehicle_state":"parked","safety_state":"normal"}'
check_post /service/invoke '{"trace_id":"smoke-service","service":"vehicle-state","method":"getState","payload":{}}'
check_post /events/publish '{"trace_id":"smoke-event","topic":"vehicle.signal.changed","payload":{"signal":"Vehicle.Speed","value":0}}'

echo "Central Brain Uni Info Bus smoke test passed on $BASE_URL"
