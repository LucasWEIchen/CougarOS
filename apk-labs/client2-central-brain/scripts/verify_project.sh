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
bash -n "$PROJECT_DIR/scripts/build_binder_bridge_dex.sh"
bash -n "$PROJECT_DIR/scripts/build_debug_apk.sh"
bash -n "$PROJECT_DIR/scripts/verify_project.sh"
bash -n "$PROJECT_DIR/scripts/install_debug_apk.sh"
python3 -m py_compile "$PROJECT_DIR/scripts/apply_static_panel_patch.py"
rm -rf "$PROJECT_DIR/scripts/__pycache__"
python3 -m json.tool "$PROJECT_DIR/client2-central-brain.project.json" >/dev/null

for resource_file in \
  'central_brain_panel_background.xml' \
  'central_brain_action_button.xml' \
  'central_brain_live_trace_background.xml' \
  'central_brain_effect_feedback_background.xml' \
  'central_brain_seat_part.xml'; do
  test -f "$PROJECT_DIR/patches/res/drawable/$resource_file"
done

for java_file in \
  Client2ScenarioBridge.java \
  ScenarioCallback.java \
  CockpitHmiState.java \
  CockpitHvacState.java \
  HvacControlIntent.java \
  CockpitSeatState.java \
  SeatControlIntent.java \
  CockpitExecutionTimeline.java \
  CockpitRecoveryState.java \
  PanelPresentationMode.java \
  DrivingUxPolicy.java \
  CockpitEngineerState.java \
  DebugSimulationControllerClient.java \
  CockpitSimulatedScenarioState.java \
  OrchestrationRuntimeClient.java \
  CockpitMultimodalInput.java \
  CockpitScenarioControlState.java \
  CockpitDisplayPolicy.java \
  CockpitHmiReducer.java \
  CockpitControlCoordinator.java; do
  test -f "$PROJECT_DIR/bridge/src/com/centralbrain/client2/$java_file"
done
test ! -f "$PROJECT_DIR/bridge/src/com/centralbrain/client2/SimulatedScenarioRuntimeClient.java"
test ! -f "$PROJECT_DIR/bridge/src/com/centralbrain/runtime/scenario/SimulatedScenarioBinderSnapshot.java"
if rg -q 'ISimulatedScenarioRuntime.aidl' "$PROJECT_DIR/scripts/build_binder_bridge_dex.sh"; then
  echo "legacy simulated-scenario AIDL must not be compiled into Client2" >&2
  exit 1
fi
rg -q 'Expected exactly twenty Client2' "$PROJECT_DIR/scripts/build_binder_bridge_dex.sh"
rg -q 'Expected exactly one generated debug simulation-controller Binder source' \
  "$PROJECT_DIR/scripts/build_binder_bridge_dex.sh"
if find "$PROJECT_DIR/patches/smali" -type f -name '*.smali' -print -quit 2>/dev/null \
    | grep -q .; then
  echo "maintained Client2 Smali controller must be absent" >&2
  exit 1
fi

for tool in apktool apksigner zipalign aapt adb jar javac d8; do
  command -v "$tool" >/dev/null
done

if [[ -d "$WORK_DIR" ]]; then
  rg -q "centralBrainPanel" "$WORK_DIR/res/layout/main_layout.xml"
  rg -q "centralBrainPanelOverlay" "$WORK_DIR/res/layout/main_layout.xml"
  rg -q "centralBrainNavigationTriggerRail" "$WORK_DIR/res/layout/main_layout.xml"
  rg -q "centralBrainNavigationTrigger" "$WORK_DIR/res/layout/main_layout.xml"
  rg -q 'central_brain_menu_toggle' "$WORK_DIR/res/layout/main_layout.xml"
  rg -q "@id/view1" "$WORK_DIR/res/layout/main_layout.xml"
  rg -q "centralBrainColdButton" "$WORK_DIR/res/layout/main_layout.xml"
  rg -q "centralBrainTiredButton" "$WORK_DIR/res/layout/main_layout.xml"
  rg -q "centralBrainMultimodalButton" "$WORK_DIR/res/layout/main_layout.xml"
  test -f "$WORK_DIR/res/raw/central_brain_cabin_frame.png"
  for surface_id in \
    centralBrainLiveTraceScroll \
    centralBrainLiveTraceText \
    centralBrainActuatorOverlay \
    centralBrainActuatorTitleText \
    centralBrainActuatorStateText \
    centralBrainActuatorHvacTemperatureText \
    centralBrainActuatorHvacFanText \
    centralBrainActuatorMediaText \
    centralBrainActuatorFanProgress \
    centralBrainSeatBack \
    centralBrainActuatorSeatAngleText \
    centralBrainModelInputSurface \
    centralBrainModelInputThumbnail \
    centralBrainImagePreviewOverlay \
    centralBrainImagePreview; do
    rg -q "$surface_id" "$WORK_DIR/res/layout/main_layout.xml"
  done
  for scenario_id in \
    care.cold \
    care.fatigue \
    cabin.multimodal; do
    rg -Fq "android:tag=\"$scenario_id\"" "$WORK_DIR/res/layout/main_layout.xml"
  done
  if rg -q 'android:tag="(state\.vehicle|memory\.preference|skills\.catalog|governance\.audit|security\.denied|security\.privacy|runtime\.npu|system\.overview)"' \
      "$WORK_DIR/res/layout/main_layout.xml"; then
    echo "legacy diagnostic aliases must not remain on the intent-first primary surface" >&2
    exit 1
  fi
  rg -q "central_brain_panel_background" "$WORK_DIR/res/layout/main_layout.xml"
  rg -q 'centralBrainRenderRegion.*android:layout_width="match_parent".*android:layout_height="match_parent"' "$WORK_DIR/res/layout/main_layout.xml"
  if rg -q 'centralBrainRenderRegion.*android:layout_weight=' "$WORK_DIR/res/layout/main_layout.xml"; then
    echo "Client2 render region must remain full-screen behind the floating panel" >&2
    exit 1
  fi
  rg -q 'centralBrainPanelOverlay.*android:layout_width="match_parent".*android:layout_height="match_parent".*android:visibility="gone".*android:clickable="true"' "$WORK_DIR/res/layout/main_layout.xml"
  rg -q 'centralBrainPanel.*android:layout_width="600.0dp".*android:layout_height="760.0dp".*android:layout_gravity="top|right".*android:layout_marginTop="200.0dp".*android:layout_marginRight="32.0dp".*android:elevation="8.0dp"' "$WORK_DIR/res/layout/main_layout.xml"
  rg -q 'centralBrainNavigationTriggerRail.*android:layout_height="96.0dp".*android:layout_gravity="bottom".*android:weightSum="24.0"' "$WORK_DIR/res/layout/main_layout.xml"
  rg -q 'Space.*android:layout_weight="9.5"' "$WORK_DIR/res/layout/main_layout.xml"
  rg -q 'centralBrainNavigationTrigger.*android:tag="central_brain_menu_toggle".*android:layout_weight="1.0".*android:background="@android:color/transparent".*android:clickable="true".*android:contentDescription="AIOS task menu"' "$WORK_DIR/res/layout/main_layout.xml"
  rg -q 'Space.*android:layout_weight="13.5"' "$WORK_DIR/res/layout/main_layout.xml"
  rg -q '#80EEF2F3' "$WORK_DIR/res/drawable/central_brain_panel_background.xml"
  rg -q '#E8FFFFFF' "$WORK_DIR/res/drawable/central_brain_action_button.xml"
  rg -q '#B0222B31' "$WORK_DIR/res/drawable/central_brain_live_trace_background.xml"
  if rg -q 'centralBrain(Driver|Passenger)TemperatureOverlay|central_brain_temperature_overlay' \
      "$WORK_DIR/res/layout/main_layout.xml"; then
    echo "Android HVAC temperature overlays must not cover Unity-native controls" >&2
    exit 1
  fi
  rg -Fq 'seatBackView.setRotation(-(current - from) * 1.2f)' \
    "$PROJECT_DIR/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
  rg -q 'unity_hvac_native_dispatch=' \
    "$PROJECT_DIR/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
  rg -Fq 'UNITY_TEMPERATURE_MIN_C = 18.0f' \
    "$PROJECT_DIR/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
  rg -Fq 'UNITY_TEMPERATURE_MAX_C = 30.0f' \
    "$PROJECT_DIR/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
  rg -Fq 'UNITY_TEMPERATURE_STEP_C = 0.5f' \
    "$PROJECT_DIR/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
  rg -Fq 'UNITY_RENDER_SCALE = 1.5f' \
    "$PROJECT_DIR/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
  rg -Fq 'CentralBrainDriverTemperature' \
    "$PROJECT_DIR/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
  rg -Fq 'CentralBrainPassengerTemperature' \
    "$PROJECT_DIR/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
  rg -Fq 'UNITY_TEMPERATURE_METHOD = "set_text"' \
    "$PROJECT_DIR/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
  rg -q 'UI SIMULATION ONLY' "$WORK_DIR/res/layout/main_layout.xml"
  if rg -q 'centralBrain(StageNavigation|IntentTab|PlanTab|ExecutionTab|ResultTab|DeviceDrawer|HvacPowerButton|SeatHeatUpButton)' \
      "$WORK_DIR/res/layout/main_layout.xml"; then
    echo "legacy multi-surface or manual actuator controls remain in voice-first HMI" >&2
    exit 1
  fi
  rg -q "com.centralbrain.permission.BIND_RUNTIME" "$WORK_DIR/AndroidManifest.xml"
  rg -q "com.centralbrain.permission.CONTROL_DEBUG_SIMULATION" "$WORK_DIR/AndroidManifest.xml"
  rg -q 'package android:name="com.centralbrain.runtime"' "$WORK_DIR/AndroidManifest.xml"
  if rg -q "android.permission.INTERNET|android:usesCleartextTraffic" \
      "$WORK_DIR/AndroidManifest.xml"; then
    echo "Client2 Binder demo must not request network or cleartext access" >&2
    exit 1
  fi
  rg -q "CockpitControlCoordinator;->install" "$WORK_DIR/smali/com/tuanjie/urasclient2/MainActivity.smali"
  test ! -f "$WORK_DIR/smali/com/tuanjie/urasclient2/CentralBrainPanelController.smali"
  test ! -f "$WORK_DIR/smali/com/tuanjie/urasclient2/CentralBrainPanelController\$UiUpdate.smali"
  test -f "$WORK_DIR/unknown/classes2.dex"
  rg -a -q 'client2_orchestration_sdk_connected' "$WORK_DIR/unknown/classes2.dex"
  rg -a -q 'cockpit_orchestration_sdk_v1_wired' "$WORK_DIR/unknown/classes2.dex"
  if rg -a -q 'BIND_SIMULATED_SCENARIO_RUNTIME|cockpit_simulated_scenario_binder_v2_wired' \
      "$WORK_DIR/unknown/classes2.dex"; then
    echo "legacy simulated-scenario Binder remains in Client2 dex" >&2
    exit 1
  fi
  if rg -a -q "http://10.0.2.2:8787|HttpURLConnection" "$WORK_DIR"; then
    echo "legacy Client2 HTTP transport remains in generated workdir" >&2
    exit 1
  fi
fi

if [[ -f "$SIGNED_APK" ]]; then
  apksigner verify --verbose --print-certs "$SIGNED_APK" >/dev/null
  aapt dump badging "$SIGNED_APK" | rg -q "package: name='com.tuanjie.urasclient2'"
  aapt dump permissions "$SIGNED_APK" | rg -q "com.centralbrain.permission.BIND_RUNTIME"
  aapt dump permissions "$SIGNED_APK" | rg -q "com.centralbrain.permission.CONTROL_DEBUG_SIMULATION"
  if aapt dump permissions "$SIGNED_APK" | rg -q "android.permission.INTERNET"; then
    echo "signed Client2 Binder APK unexpectedly requests INTERNET" >&2
    exit 1
  fi
  jar tf "$SIGNED_APK" | rg -q '^classes2\.dex$'

  RUNTIME_APK="$ROOT_DIR/central-brain/android-runtime/runtime-service/build/outputs/apk/debug/runtime-service-debug.apk"
  test -f "$RUNTIME_APK"
  RUNTIME_SIGNER="$(apksigner verify --print-certs "$RUNTIME_APK" \
    | awk -F': ' '/certificate SHA-256 digest/ {print $2; exit}')"
  CLIENT2_SIGNER="$(apksigner verify --print-certs "$SIGNED_APK" \
    | awk -F': ' '/certificate SHA-256 digest/ {print $2; exit}')"
  test -n "$RUNTIME_SIGNER"
  test "$RUNTIME_SIGNER" = "$CLIENT2_SIGNER"
fi

echo "Client2 Central Brain APK reverse demo project verified"
