#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PROJECT_DIR="$ROOT_DIR/apk-labs/client2-central-brain"
WORK_DIR="${CLIENT2_CB_WORK_DIR:-$ROOT_DIR/builds/client2-central-brain/workdir}"
SIGNED_APK="$ROOT_DIR/builds/client2-central-brain/signed/client2-central-brain.debug.apk"

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/env.sh"
fi

bash -n "$PROJECT_DIR/scripts/prepare_workspace.sh"
bash -n "$PROJECT_DIR/scripts/build_debug_apk.sh"
bash -n "$PROJECT_DIR/scripts/verify_project.sh"
bash -n "$PROJECT_DIR/scripts/install_debug_apk.sh"
python3 -m py_compile "$PROJECT_DIR/scripts/apply_static_panel_patch.py"
rm -rf "$PROJECT_DIR/scripts/__pycache__"
python3 -m json.tool "$PROJECT_DIR/client2-central-brain.project.json" >/dev/null

for smali_file in \
  'CentralBrainPanelController.smali' \
  'CentralBrainPanelController$RequestTask.smali' \
  'CentralBrainPanelController$UiUpdate.smali'; do
  test -f "$PROJECT_DIR/patches/smali/com/tuanjie/urasclient2/$smali_file"
done

for tool in apktool apksigner zipalign aapt adb; do
  command -v "$tool" >/dev/null
done

if [[ -d "$WORK_DIR" ]]; then
  rg -q "centralBrainPanel" "$WORK_DIR/res/layout/main_layout.xml"
  rg -q "@id/view1" "$WORK_DIR/res/layout/main_layout.xml"
  rg -q "centralBrainColdButton" "$WORK_DIR/res/layout/main_layout.xml"
  rg -q "centralBrainTiredButton" "$WORK_DIR/res/layout/main_layout.xml"
  rg -q "centralBrainReplyText" "$WORK_DIR/res/layout/main_layout.xml"
  rg -q "android.permission.INTERNET" "$WORK_DIR/AndroidManifest.xml"
  rg -q 'android:usesCleartextTraffic="true"' "$WORK_DIR/AndroidManifest.xml"
  rg -q "CentralBrainPanelController;->install" "$WORK_DIR/smali/com/tuanjie/urasclient2/MainActivity.smali"
  rg -q "http://10.0.2.2:8787/ai/infer" "$WORK_DIR/smali/com/tuanjie/urasclient2/CentralBrainPanelController\$RequestTask.smali"
  rg -q "generated_text" "$WORK_DIR/smali/com/tuanjie/urasclient2/CentralBrainPanelController\$RequestTask.smali"
fi

if [[ -f "$SIGNED_APK" ]]; then
  apksigner verify --verbose --print-certs "$SIGNED_APK" >/dev/null
  aapt dump badging "$SIGNED_APK" | rg -q "package: name='com.tuanjie.urasclient2'"
  aapt dump permissions "$SIGNED_APK" | rg -q "android.permission.INTERNET"
fi

echo "Client2 Central Brain APK reverse demo project verified"
