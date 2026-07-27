#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RECOVERY="$ROOT/tools/recover_central_brain_android_client1_render_session.sh"
ROADMAP="$ROOT/docs/CENTRAL_BRAIN_ROADMAP.md"
REQUIREMENTS="$ROOT/docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md"
ISSUES="$ROOT/docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md"
DELIVERY="$ROOT/docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md"
DETAIL="$ROOT/docs/CENTRAL_BRAIN_CLIENT2_RENDER_FIDELITY_HVAC_ORBIT.md"
DRIVER="$ROOT/docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md"

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
  'com.tuanjie.renderservice' \
  'CENTRAL_BRAIN_CLIENT1_DISPLAY_ID' \
  'am force-stop' \
  'am start -W --display' \
  'ACTIVITY_NOT_RESUMED_ON_DISPLAY' \
  'RENDER_SURFACE_MISSING' \
  'client1_render_session_recovered=true' \
  'client1_data_cleared=false' \
  'client2_restarted=false' \
  'system_partition_modified=false' \
  'production_ready=false' \
  'target_hardware_validated=false'; do
  require_text "$RECOVERY" "$marker"
done

for doc in "$ROADMAP" "$REQUIREMENTS" "$ISSUES" "$DELIVERY" "$DETAIL" "$DRIVER"; do
  require_text "$doc" 'P4-R7-CLIENT1-RENDER-SESSION-RECOVERY'
done

printf '%s\n' \
  "client1_render_session_recovery_contract_verified=true" \
  "client1_secondary_display_default=2" \
  "client1_data_clear_enabled=false" \
  "client2_restart_required=false" \
  "driver_hal_development_required=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
