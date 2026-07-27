#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001..006, S2-*, DEL-001/003/004/005.
# Stage: P10-R1-AIOS-ARCHITECTURE-DOCUMENT-CONVERGENCE.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARCH="$ROOT/docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md"
DETAIL="$ROOT/docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md"
INTERFACE="$ROOT/docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md"
USE_CASE="$ROOT/docs/CENTRAL_BRAIN_MODULE_USE_CASE_DIAGRAM.md"
ROADMAP="$ROOT/docs/CENTRAL_BRAIN_ROADMAP.md"
BACKLOG="$ROOT/docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md"
README="$ROOT/README.md"
P4_R6="$ROOT/docs/CENTRAL_BRAIN_CLIENT2_UNITY_NATIVE_HVAC_SEAT_PATCH.md"
P4_R7="$ROOT/docs/CENTRAL_BRAIN_CLIENT2_RENDER_FIDELITY_HVAC_ORBIT.md"
OPENCLAW_TARGET="$ROOT/docs/CENTRAL_BRAIN_OPENCLAW_TARGET_GATEWAY.md"
MULTIMODAL="$ROOT/docs/CENTRAL_BRAIN_OPENCLAW_MULTIMODAL_DEVELOPMENT.md"

require_text() {
  local file="$1"
  local marker="$2"
  grep -Fq "$marker" "$file" \
    || { echo "architecture readiness marker missing in $file: $marker" >&2; exit 1; }
}

for file in \
  "$ARCH" "$DETAIL" "$INTERFACE" "$USE_CASE" "$ROADMAP" "$BACKLOG" "$README" \
  "$P4_R6" "$P4_R7" "$OPENCLAW_TARGET" "$MULTIMODAL"; do
  [[ -f "$file" ]] || { echo "architecture readiness document missing: $file" >&2; exit 1; }
done

require_text "$ARCH" '版本：3.6'
require_text "$ARCH" 'REPOSITORY_SOFTWARE_ARCHITECTURE_BASELINE_READY'
require_text "$ARCH" '## 文档就绪结论与权威关系'
require_text "$ARCH" '## P4-R7 双屏渲染、动态 HVAC 与触摸架构'
require_text "$ARCH" '## P4-R6 Unity 原生 HVAC 与座椅架构'
require_text "$ARCH" 'Client1/DisplayIndex0'
require_text "$ARCH" 'Client2/DisplayIndex1'
require_text "$ARCH" 'target_openclaw_transitional'

require_text "$DETAIL" '版本：2.8'
require_text "$DETAIL" 'REPOSITORY_SOFTWARE_DESIGN_BASELINE_READY'
require_text "$DETAIL" '## P4-R7 render/HVAC/orbit 实现级收口'
require_text "$DETAIL" '## P4-R6 Unity-native HVAC/Seat 实现级收口'
require_text "$DETAIL" 'c2sSendMessage('
require_text "$DETAIL" 'STOP RenderService'
require_text "$DETAIL" 'FLAG_SECURE'

require_text "$INTERFACE" '版本：3.5'
require_text "$INTERFACE" 'REPOSITORY_INTERFACE_BASELINE_READY'
require_text "$INTERFACE" '## P4-R7 render/HVAC/orbit interfaces'
require_text "$INTERFACE" '## P4-R6 Unity-native HVAC/Seat interfaces'
require_text "$INTERFACE" 'setRenderScale(float)'
require_text "$INTERFACE" 'c2sSendMessage(object,method,value)'
require_text "$INTERFACE" 'recover_central_brain_android_client1_render_session.sh'
require_text "$INTERFACE" '不得自动回退到 UI simulation'

for file in "$ARCH" "$DETAIL" "$INTERFACE"; do
  require_text "$file" 'architecture_document_set_ready=true'
  require_text "$file" 'repository_software_requirements_complete=true'
  require_text "$file" 'open_repository_software_requirement_count=0'
  require_text "$file" 'external_activation_requirements_classified=true'
  require_text "$file" 'production_ready=false'
  require_text "$file" 'target_hardware_validated=false'
done

require_text "$USE_CASE" '版本：1.1'
require_text "$USE_CASE" 'Client1 副屏 d2 / Render index0'
require_text "$USE_CASE" 'Client2 主屏 d0 / Render index1'
require_text "$USE_CASE" 'Target OpenClaw transitional'
require_text "$USE_CASE" 'Vehicle adapter empty'
require_text "$USE_CASE" 'Vendor NPU provider empty'

require_text "$README" 'Client1 副屏 APK\nAndroid d2 / Render index0'
require_text "$README" 'Client2 中控 APK\nAndroid d0 / Render index1'
require_text "$README" '双屏渲染恢复\nClient1 first / Client2 second'

require_text "$ROADMAP" '版本：1.4'
require_text "$ROADMAP" 'REPOSITORY_SOFTWARE_COMPLETE / ARCHITECTURE_BASELINE_READY'
require_text "$ROADMAP" '## 2026-07-27 AIOS 架构文档收口'
if grep -Fq '状态：Stage 2 P6 in progress' "$ROADMAP"; then
  echo "roadmap still carries the stale Stage 2 P6 status" >&2
  exit 1
fi

require_text "$BACKLOG" '版本：1.6'
require_text "$BACKLOG" 'REPOSITORY_SOFTWARE_WORK_PACKAGES_CLASSIFIED_COMPLETE'
require_text "$BACKLOG" '## P10-R1 文档与工作包收口'
require_text "$BACKLOG" '## P4-R7 双屏渲染、动态温区与车模旋转'

printf '%s\n' \
  "aios_architecture_document_set_ready=true" \
  "aios_master_architecture_current_through=P4-R7" \
  "aios_master_detailed_design_current_through=P4-R7" \
  "aios_master_interface_design_current_through=P4-R7" \
  "aios_module_use_case_current=true" \
  "repository_software_requirements_complete=true" \
  "open_repository_software_requirement_count=0" \
  "external_activation_requirements_classified=true" \
  "production_ready=false" \
  "target_hardware_validated=false"
