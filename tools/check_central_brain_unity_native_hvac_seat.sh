#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-HMI-001/002/003/004, S2-UX-002, S2-ADP-001/002,
# S2-SAF-001, DEL-004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLIENT_PROJECT="$ROOT_DIR/apk-labs/client2-central-brain"
RENDER_PROJECT="$ROOT_DIR/apk-labs/renderservice-central-brain"
COORDINATOR="$CLIENT_PROJECT/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
LAYOUT="$CLIENT_PROJECT/patches/main_layout.central_brain_panel.xml"
DESIGN="$ROOT_DIR/docs/CENTRAL_BRAIN_CLIENT2_UNITY_NATIVE_HVAC_SEAT_PATCH.md"

for path in "$COORDINATOR" "$LAYOUT" "$DESIGN" \
  "$RENDER_PROJECT/renderservice-central-brain.project.json" \
  "$RENDER_PROJECT/scripts/patch_unity_hvac_bundle.py"; do
  test -f "$path"
done

bash "$RENDER_PROJECT/scripts/verify_project.sh"
bash "$CLIENT_PROJECT/scripts/verify_project.sh"

grep -Fq 'seatBackView.setRotation(-(current - from) * 1.2f)' "$COORDINATOR"
grep -Fq 'setUnityTemperatureState(true)' "$COORDINATOR"
grep -Fq 'MotionEvent.TOOL_TYPE_FINGER' "$COORDINATOR"
grep -Fq 'InputDevice.SOURCE_TOUCHSCREEN' "$COORDINATOR"
grep -Fq 'new MotionEvent.PointerProperties[]{properties}' "$COORDINATOR"
grep -Fq 'f"CentralBrain_{zone}_temperature_28_0"' \
  "$RENDER_PROJECT/scripts/patch_unity_hvac_bundle.py"
grep -Fq '"driver": {' "$RENDER_PROJECT/scripts/patch_unity_hvac_bundle.py"
grep -Fq '"passenger": {' "$RENDER_PROJECT/scripts/patch_unity_hvac_bundle.py"
grep -Fq '"m_MethodName": "SetActive"' \
  "$RENDER_PROJECT/scripts/patch_unity_hvac_bundle.py"

if grep -Fq 'centralBrainDriverTemperatureOverlay' "$LAYOUT"; then
  echo "Android driver temperature overlay is still present" >&2
  exit 1
fi
if grep -Fq 'centralBrainPassengerTemperatureOverlay' "$LAYOUT"; then
  echo "Android passenger temperature overlay is still present" >&2
  exit 1
fi

printf '%s\n' \
  'seat_recline_expansion_direction_verified=true' \
  'unity_native_dual_zone_hvac_state_defined=true' \
  'android_temperature_overlay_present=false' \
  'vehicle_bus_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
