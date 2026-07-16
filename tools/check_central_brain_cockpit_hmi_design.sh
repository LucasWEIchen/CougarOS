#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-001/003/004, FW-U-001/003/004, FW-S-003/005,
# XSC-001, NV-F-001/003/004/009, NV-G-005/006/007, NV-P-002,
# DEL-001/004, S2-UX-001..003, S2-HMI-001..005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOC="$ROOT_DIR/docs/CENTRAL_BRAIN_COCKPIT_HMI_UX_DESIGN_MOCKUPS.md"
SOURCE_DIR="$ROOT_DIR/docs/ui/cockpit-hmi-design"
ASSET_DIR="$ROOT_DIR/docs/assets/cockpit-hmi-design"
REFERENCE="$ASSET_DIR/client2-emulator-scene-reference.png"

required_files=(
  "$DOC"
  "$SOURCE_DIR/index.html"
  "$SOURCE_DIR/styles.css"
  "$SOURCE_DIR/app.js"
  "$SOURCE_DIR/render_mockups.sh"
  "$REFERENCE"
  "$ASSET_DIR/01-care.png"
  "$ASSET_DIR/02-hvac.png"
  "$ASSET_DIR/03-seat.png"
  "$ASSET_DIR/04-execution.png"
)
for file in "${required_files[@]}"; do
  [[ -f "$file" ]] || { echo "missing cockpit HMI design asset: ${file#$ROOT_DIR/}" >&2; exit 1; }
done

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$file" \
    || { echo "cockpit HMI design marker missing in ${file#$ROOT_DIR/}: $marker" >&2; exit 1; }
}

for marker in \
  '# Central Brain Client2 中控 UI/UX 设计稿' \
  'cockpit_hmi_design_mockups_ready=true' \
  'cockpit_hvac_surface_implemented=false' \
  'cockpit_seat_surface_implemented=false' \
  'cockpit_demo_control_loop_implemented=false' \
  '## 5. 四个主视图' \
  '## 6. 核心 UX 流程' \
  '## 7. Android 开发映射' \
  '## 9. 设计验收' \
  'S2-HMI-001..005'; do
  require_text "$DOC" "$marker"
done

for marker in \
  'data-view="care"' \
  'data-view="hvac"' \
  'data-view="seat"' \
  'data-view="execution"' \
  'SIMULATED' \
  'HMI-D1 DESIGN ONLY' \
  '保持当前音乐播放' \
  '导航未参与本次计划'; do
  require_text "$SOURCE_DIR/index.html" "$marker"
done

for marker in \
  'width: 1920px' \
  'height: 1080px' \
  'width: 636px' \
  'top: 12px' \
  'right: 12px' \
  'border-radius: 8px' \
  'backdrop-filter: blur(20px)' \
  'height: 48px'; do
  require_text "$SOURCE_DIR/styles.css" "$marker"
done

if rg -n 'linear-gradient|radial-gradient|conic-gradient' "$SOURCE_DIR/styles.css"; then
  echo "cockpit HMI design must not use decorative gradients" >&2
  exit 1
fi

require_text "$SOURCE_DIR/app.js" 'window.__COCKPIT_HMI_READY__ = true'
require_text "$SOURCE_DIR/render_mockups.sh" 'cockpit_hmi_design_output_count=4'

expected_reference_sha="ea67855e9ec184559546c35f8be0d3a87abc5fc7f8edff02d5e411fe98479e80"
actual_reference_sha="$(sha256sum "$REFERENCE" | awk '{print $1}')"
[[ "$actual_reference_sha" == "$expected_reference_sha" ]] \
  || { echo "cockpit HMI reference image digest mismatch" >&2; exit 1; }

python3 -B - "$ASSET_DIR" <<'PY'
import pathlib
import struct
import sys

asset_dir = pathlib.Path(sys.argv[1])
expected = {
    "01-care.png",
    "02-hvac.png",
    "03-seat.png",
    "04-execution.png",
}
for name in sorted(expected):
    path = asset_dir / name
    data = path.read_bytes()[:24]
    if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise SystemExit(f"not a valid PNG design asset: {name}")
    width, height = struct.unpack(">II", data[16:24])
    if (width, height) != (1920, 1080):
        raise SystemExit(f"unexpected design asset size for {name}: {width}x{height}")
print("cockpit_hmi_design_png_dimensions_verified=4")
PY

printf '%s\n' \
  'Central Brain cockpit HMI design check passed' \
  'cockpit_hmi_design_mockups_ready=true' \
  'cockpit_hmi_design_only=true' \
  'cockpit_hvac_surface_implemented=false' \
  'cockpit_seat_surface_implemented=false' \
  'cockpit_demo_control_loop_implemented=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
