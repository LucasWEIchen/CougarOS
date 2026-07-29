#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RECOVERY="$ROOT/tools/recover_central_brain_android_client1_render_session.sh"
REQUIREMENTS="$ROOT/docs/CENTRAL_BRAIN_REQUIREMENTS.md"
ARCHITECTURE="$ROOT/docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md"
DEVELOPMENT="$ROOT/docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md"

[[ -x "$RECOVERY" ]] || { echo "Client1 recovery tool missing or not executable" >&2; exit 1; }
bash -n "$RECOVERY"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq "$marker" "$file" \
    || { echo "missing Client1 recovery marker in $file: $marker" >&2; exit 1; }
}

for marker in \
  'com.tuanjie.urasclient' \
  'com.tuanjie.urasclient2' \
  'com.tuanjie.renderservice' \
  'CENTRAL_BRAIN_CLIENT1_DISPLAY_ID' \
  'CENTRAL_BRAIN_CLIENT2_DISPLAY_ID' \
  'am force-stop' \
  'am start -W --display' \
  'CLIENT1_ACTIVITY_NOT_RESUMED_ON_DISPLAY' \
  'CLIENT2_ACTIVITY_NOT_RESUMED_ON_DISPLAY' \
  'CLIENT1_RENDER_SURFACE_MISSING' \
  'CLIENT2_RENDER_SURFACE_MISSING' \
  'CLIENT1_RENDER_INDEX_MISSING' \
  'CLIENT2_RENDER_INDEX_MISSING' \
  'CLIENT1_FRAMEBUFFER_MISSING' \
  'CLIENT2_NATIVE_FRAMEBUFFER_MISSING' \
  'CLIENT2_SCALED_FRAMEBUFFER_MISSING' \
  'COMBINED_DISPLAY_MASK_MISSING' \
  'cockpit_render_stack_recovered=true' \
  'client1_render_session_recovered=true' \
  'client1_started_before_client2=true' \
  'client1_render_index=0' \
  'client2_render_index=1' \
  'client1_surface_resolution=1920x720' \
  'client2_surface_resolution=1920x1080' \
  'client2_scaled_framebuffer_resolution=2880x1620' \
  'client1_data_cleared=false' \
  'client2_data_cleared=false' \
  'client2_restarted=true' \
  'system_partition_modified=false' \
  'production_ready=false' \
  'target_hardware_validated=false'; do
  require_text "$RECOVERY" "$marker"
done

python3 - "$RECOVERY" <<'PY'
from pathlib import Path
import sys

text = Path(sys.argv[1]).read_text(encoding="utf-8")
ordered = [
    'shell am force-stop "$CLIENT2_PACKAGE"',
    'shell am force-stop "$CLIENT1_PACKAGE"',
    'shell am force-stop "$RENDER_SERVICE_PACKAGE"',
    'shell am start -W --display "$CLIENT1_DISPLAY_ID"',
    'shell am start -W --display "$CLIENT2_DISPLAY_ID"',
]
positions = [text.index(marker) for marker in ordered]
if positions != sorted(positions):
    raise SystemExit("render-stack recovery command order is not deterministic")
for forbidden in ("pm clear", " uninstall ", "adb root", " remount"):
    if forbidden in text:
        raise SystemExit(f"destructive recovery command is forbidden: {forbidden.strip()}")
PY

bash "$ROOT/tools/check_central_brain_production_document_set.sh" >/dev/null
require_text "$REQUIREMENTS" '`P4-R7` 双屏渲染、动态 HVAC 与车模触摸交互'
require_text "$ARCHITECTURE" '1920x1080'
require_text "$DEVELOPMENT" 'RenderService 保留原生触摸输入'

printf '%s\n' \
  "client1_render_session_recovery_contract_verified=true" \
  "cockpit_render_stack_restart_order_verified=true" \
  "client1_secondary_display_default=2" \
  "client2_primary_display_default=0" \
  "client1_data_clear_enabled=false" \
  "client2_data_clear_enabled=false" \
  "client2_restart_required=true" \
  "driver_hal_development_required=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
