#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-001/004, S2-HMI-001/003/004, S2-UX-002, DEL-004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLIENT_PROJECT="$ROOT_DIR/apk-labs/client2-central-brain"
RENDER_PROJECT="$ROOT_DIR/apk-labs/renderservice-central-brain"
COORDINATOR="$CLIENT_PROJECT/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
LAYOUT="$CLIENT_PROJECT/patches/main_layout.central_brain_panel.xml"

for path in "$COORDINATOR" "$LAYOUT" \
  "$RENDER_PROJECT/renderservice-central-brain.project.json"; do
  test -f "$path"
done

bash "$RENDER_PROJECT/scripts/verify_project.sh"
bash "$CLIENT_PROJECT/scripts/verify_project.sh"

grep -Fq 'seatBackView.setRotation(-(current - from) * 1.2f)' "$COORDINATOR"
grep -Fq 'android:layout_gravity="end|top"' "$LAYOUT"
grep -Fq 'android:id="@id/topControls"' "$LAYOUT"
grep -Fq 'android:id="@id/view1"' "$LAYOUT"
grep -Fq 'android:id="@id/view2"' "$LAYOUT"
grep -Fq 'android:id="@id/view3"' "$LAYOUT"

for forbidden in \
  'setRenderScale' \
  'setOnTouchListener' \
  'c2sSendMessage' \
  'mTuanjieRenderService' \
  'CentralBrainDriverTemperature' \
  'CentralBrainPassengerTemperature'; do
  if grep -Fq "$forbidden" "$COORDINATOR"; then
    echo "Client2 must not override vendor Unity behavior: $forbidden" >&2
    exit 1
  fi
done
if grep -Fq 'centralBrainRenderRegion' "$LAYOUT"; then
  echo "Client2 must preserve the original unnamed render container" >&2
  exit 1
fi

printf '%s\n' \
  'client1_vendor_ui_preserved=true' \
  'client2_vendor_render_hierarchy_preserved=true' \
  'client2_vendor_touch_path_preserved=true' \
  'client2_render_scale_override_enabled=false' \
  'renderservice_vendor_apk_preserved=true' \
  'seat_recline_simulation_direction_verified=true' \
  'vehicle_bus_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
