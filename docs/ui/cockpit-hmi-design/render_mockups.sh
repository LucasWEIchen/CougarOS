#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SOURCE="$ROOT_DIR/docs/ui/cockpit-hmi-design/index.html"
OUTPUT_DIR="$ROOT_DIR/docs/assets/cockpit-hmi-design"
SOURCE_PATH="docs/ui/cockpit-hmi-design/index.html"
WINDOWS_CHROME="${WINDOWS_CHROME:-/mnt/c/Program Files/Google/Chrome/Application/chrome.exe}"
RENDER_BACKEND="${RENDER_BACKEND:-auto}"

[[ -f "$SOURCE" ]] || { echo "missing cockpit HMI design source: $SOURCE" >&2; exit 1; }
mkdir -p "$OUTPUT_DIR"

read -r -a PLAYWRIGHT_COMMAND <<<"${PLAYWRIGHT_COMMAND:-npx --no-install playwright}"

render_with_playwright() {
  local view="$1"
  local output="$2"
  "${PLAYWRIGHT_COMMAND[@]}" screenshot \
    --browser chromium \
    --viewport-size "1920,1080" \
    --wait-for-timeout 700 \
    "file://$SOURCE?view=$view" \
    "$OUTPUT_DIR/$output"
}

SERVER_PID=""
SERVER_LOG=""
WINDOWS_TEMP_DIR=""
WINDOWS_PROFILE_DIR=""

cleanup() {
  if [[ -n "$SERVER_PID" ]]; then
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true
  fi
  [[ -z "$SERVER_LOG" ]] || rm -f "$SERVER_LOG"
  [[ -z "$WINDOWS_PROFILE_DIR" ]] || rm -rf "$WINDOWS_PROFILE_DIR"
}
trap cleanup EXIT

start_windows_render_server() {
  local port="$1"
  SERVER_LOG="$(mktemp)"
  python3 -m http.server "$port" --bind 0.0.0.0 --directory "$ROOT_DIR" \
    >"$SERVER_LOG" 2>&1 &
  SERVER_PID="$!"

  for _ in {1..50}; do
    if curl --silent --fail --head "http://localhost:$port/$SOURCE_PATH" >/dev/null; then
      return
    fi
    sleep 0.1
  done
  cat "$SERVER_LOG" >&2
  echo "cockpit HMI design render server did not start" >&2
  exit 1
}

prepare_windows_temp() {
  local windows_temp
  windows_temp="$(cmd.exe /d /c echo %TEMP% 2>/dev/null | tr -d '\r')"
  WINDOWS_TEMP_DIR="$(wslpath -u "$windows_temp")"
  WINDOWS_PROFILE_DIR="$(mktemp -d "$WINDOWS_TEMP_DIR/cougaros-hmi-profile.XXXXXX")"
}

render_with_windows_chrome() {
  local view="$1"
  local output="$2"
  local port="$3"
  local temp_linux="$WINDOWS_TEMP_DIR/cougaros-$output"
  local temp_windows
  local profile_windows
  temp_windows="$(wslpath -w "$temp_linux")"
  profile_windows="$(wslpath -w "$WINDOWS_PROFILE_DIR")"

  "$WINDOWS_CHROME" \
    --headless=new \
    --disable-gpu \
    --disable-background-networking \
    --hide-scrollbars \
    --window-size=1920,1080 \
    --force-device-scale-factor=1 \
    --virtual-time-budget=1500 \
    --user-data-dir="$profile_windows" \
    --screenshot="$temp_windows" \
    "http://localhost:$port/$SOURCE_PATH?view=$view"
  install -m 0644 "$temp_linux" "$OUTPUT_DIR/$output"
  rm -f "$temp_linux"
}

if [[ "$RENDER_BACKEND" == "auto" ]]; then
  if [[ -x "$WINDOWS_CHROME" ]] && command -v cmd.exe >/dev/null && command -v wslpath >/dev/null; then
    RENDER_BACKEND="windows-chrome"
  else
    RENDER_BACKEND="playwright"
  fi
fi

if [[ "$RENDER_BACKEND" == "windows-chrome" ]]; then
  RENDER_PORT="${COCKPIT_HMI_DESIGN_PORT:-8765}"
  prepare_windows_temp
  start_windows_render_server "$RENDER_PORT"
  render_with_windows_chrome care 01-care.png "$RENDER_PORT"
  render_with_windows_chrome hvac 02-hvac.png "$RENDER_PORT"
  render_with_windows_chrome seat 03-seat.png "$RENDER_PORT"
  render_with_windows_chrome execution 04-execution.png "$RENDER_PORT"
elif [[ "$RENDER_BACKEND" == "playwright" ]]; then
  render_with_playwright care 01-care.png
  render_with_playwright hvac 02-hvac.png
  render_with_playwright seat 03-seat.png
  render_with_playwright execution 04-execution.png
else
  echo "unsupported cockpit HMI render backend: $RENDER_BACKEND" >&2
  exit 1
fi

printf '%s\n' \
  "cockpit_hmi_design_rendered=true" \
  "cockpit_hmi_design_output_count=4" \
  "cockpit_hmi_design_target_size=1920x1080" \
  "cockpit_hmi_design_render_backend=$RENDER_BACKEND" \
  "cockpit_hmi_design_only=true"
