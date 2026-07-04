#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${CENTRAL_BRAIN_AUDIT_TEST_PORT:-19787}"
BASE_URL="http://127.0.0.1:${PORT}"
AUDIT_LOG="$(mktemp)"
SERVER_LOG="$(mktemp)"

cleanup() {
  if [[ -n "${SERVER_PID:-}" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true
  fi
  rm -f "$AUDIT_LOG" "$SERVER_LOG"
}
trap cleanup EXIT

start_server() {
  CENTRAL_BRAIN_AUDIT_LOG="$AUDIT_LOG" \
    python3 "$ROOT_DIR/central-brain/backend/mock_npu_service.py" \
    --host 127.0.0.1 --port "$PORT" >"$SERVER_LOG" 2>&1 &
  SERVER_PID="$!"

  for _ in {1..40}; do
    if curl -fsS "$BASE_URL/health" >/dev/null 2>&1; then
      return
    fi
    sleep 0.2
  done

  echo "backend did not start" >&2
  cat "$SERVER_LOG" >&2 || true
  exit 1
}

stop_server() {
  kill "$SERVER_PID" 2>/dev/null || true
  wait "$SERVER_PID" 2>/dev/null || true
  unset SERVER_PID
}

start_server
curl -fsS -X POST "$BASE_URL/soa/invoke" \
  -H 'Content-Type: application/json' \
  -d '{"trace_id":"audit-persistence-smoke","service":"vehicle-state","method":"getState","caller_permissions":["vehicle.read","service.read"]}' \
  | python3 -m json.tool >/dev/null
stop_server

if ! grep -q 'audit-persistence-smoke' "$AUDIT_LOG"; then
  echo "audit log did not contain the SOA trace id" >&2
  exit 1
fi

start_server
python3 - "$BASE_URL" <<'PY'
import json
import sys
from urllib import request

base_url = sys.argv[1].rstrip("/")
with request.urlopen(base_url + "/audit/recent", timeout=5) as response:
    payload = json.loads(response.read().decode("utf-8"))

events = payload["payload"]["events"]
assert events, "audit events were not restored"
assert events[0]["trace_id"] == "audit-persistence-smoke", "restored trace id mismatch"
assert payload["payload"]["retention"] == "jsonl-last-50", "audit retention did not report JSONL mode"
assert payload["payload"]["persistence"]["state"] == "enabled", "audit persistence was not enabled"
assert "NV-G-007" in json.dumps(payload), "audit response missing NV-G-007"
print("audit persistence smoke ok")
PY
