#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-HMI-001/002/003/004, S2-UX-002, S2-ADP-001/002,
# S2-SAF-001, DEL-004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLIENT_PROJECT="$ROOT_DIR/apk-labs/client2-central-brain"
RENDER_PROJECT="$ROOT_DIR/apk-labs/renderservice-central-brain"
COORDINATOR="$CLIENT_PROJECT/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
LAYOUT="$CLIENT_PROJECT/patches/main_layout.central_brain_panel.xml"
DESIGN="$ROOT_DIR/docs/CENTRAL_BRAIN_CLIENT2_RENDER_FIDELITY_HVAC_ORBIT.md"

for path in "$COORDINATOR" "$LAYOUT" "$DESIGN" \
  "$RENDER_PROJECT/renderservice-central-brain.project.json" \
  "$RENDER_PROJECT/scripts/patch_unity_hvac_bundle.py"; do
  test -f "$path"
done

bash "$RENDER_PROJECT/scripts/verify_project.sh"
bash "$CLIENT_PROJECT/scripts/verify_project.sh"

grep -Fq 'seatBackView.setRotation(-(current - from) * 1.2f)' "$COORDINATOR"
grep -Fq 'UNITY_RENDER_SCALE = 1.5f' "$COORDINATOR"
grep -Fq 'UNITY_TEMPERATURE_MIN_C = 18.0f' "$COORDINATOR"
grep -Fq 'UNITY_TEMPERATURE_MAX_C = 30.0f' "$COORDINATOR"
grep -Fq 'UNITY_TEMPERATURE_STEP_C = 0.5f' "$COORDINATOR"
grep -Fq 'CentralBrainDriverTemperature' "$COORDINATOR"
grep -Fq 'CentralBrainPassengerTemperature' "$COORDINATOR"
grep -Fq '"c2sSendMessage"' "$COORDINATOR"
grep -Fq 'UNITY_TEMPERATURE_METHOD = "SetText"' "$COORDINATOR"
grep -Fq 'return false;' "$COORDINATOR"
grep -Fq '"driver": "CentralBrainDriverTemperature"' \
  "$RENDER_PROJECT/scripts/patch_unity_hvac_bundle.py"
grep -Fq '"passenger": "CentralBrainPassengerTemperature"' \
  "$RENDER_PROJECT/scripts/patch_unity_hvac_bundle.py"
grep -Fq 'component_tree["_targetInputDisplay"] = 1' \
  "$RENDER_PROJECT/scripts/patch_unity_hvac_bundle.py"
grep -Fq 'component_tree["_eventSystemRaycastCheck"] = 0' \
  "$RENDER_PROJECT/scripts/patch_unity_hvac_bundle.py"
grep -Fq '(key, -0.18 if key == "_FaceDilate" else value)' \
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
  'unity_dynamic_temperature_defined=true' \
  'unity_temperature_range_18_30=true' \
  'unity_temperature_step_0_5=true' \
  'unity_orbit_pan_display_binding_corrected=true' \
  'unity_render_scale_1_5_requested=true' \
  'android_temperature_overlay_present=false' \
  'vehicle_bus_accessed=false' \
  'testboard_android13_arm64_verified=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
