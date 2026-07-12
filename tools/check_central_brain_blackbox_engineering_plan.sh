#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001/004/005/006, NV-F-001/011/012,
# NV-G-003/005/006/007, NV-P-002, KH-003/006, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLAN="docs/CENTRAL_BRAIN_BLACKBOX_ANDROID13_ENGINEERING_PLAN.md"

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing black-box Android 13 engineering pattern '$pattern' in $path" >&2
    exit 1
  fi
}

[[ -f "$ROOT_DIR/$PLAN" ]] || { echo "missing black-box engineering plan" >&2; exit 1; }

for pattern in \
  "Android 13/API 33" \
  '普通 `/data/app`' \
  'arm64-v8a' \
  'x86_64' \
  'native-runtime' \
  'C ABI V1' \
  'JNI_OnLoad' \
  'RegisterNatives' \
  'hardware_accessed=false' \
  '不新增 Linux 前端' \
  '不开发虚拟化' \
  'B0' \
  'B1' \
  'B2' \
  'B3' \
  'B4'; do
  require_text "$PLAN" "$pattern"
done

require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "B0 black-box Android 13 engineering trace"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md" "DEV-020"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md" "ISSUE-027"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android B0 Black-Box Engineering Baseline"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "B0 Black-Box Native Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android B0 C/Java Ownership Boundary"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "| B0 |"
require_text "docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md" "B2 Native Runtime process integration trace"
require_text "docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md" "Android B2 Native Runtime Integration Artifact"
require_text "docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md" "B2 Native Process Integration Driver/HAL Result"
require_text "docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md" "Android B2 Native Runtime Process Integration"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "| B2 | Runtime 集成 | Native lifecycle 接入 Binder Runtime 与 Diagnostic | 已完成 |"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "| B3 | 黑盒验收 | 公开 API 能力探测、安全安装、API 33 设备证据 | 已完成（模拟器） |"
require_text "docs/CENTRAL_BRAIN_ANDROID13_HYBRID_INSTALLATION_AND_USAGE.md" "Central Brain Android 13 Hybrid 工程安装与使用指南"
require_text "central-brain/delivery/android-hybrid/central-brain.android-hybrid-delivery-profile.json" "android13-blackbox-hybrid-application-layer"
require_text "docs/CENTRAL_BRAIN_ROADMAP.md" "| B4 | 实际工程交付 | APK/AAR、hash/signer/ABI、安装和使用指南 | 已完成（软件交付） |"

if grep -Eiq \
    'implement (hypervisor|virtualization)|scan .*device node|guess .*ioctl|modify .*vendor partition' \
    "$ROOT_DIR/$PLAN"; then
  echo "black-box engineering plan contains a forbidden implementation scope" >&2
  exit 1
fi

echo "Central Brain black-box Android 13 engineering plan check passed"
