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
  'central_brain_reply_background.xml' \
  'central_brain_stage_tab.xml' \
  'central_brain_status_badge.xml' \
  'central_brain_section_background.xml' \
  'central_brain_drawer_background.xml'; do
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
rg -q 'Expected exactly nineteen Client2' "$PROJECT_DIR/scripts/build_binder_bridge_dex.sh"
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
  for surface_id in \
    centralBrainHeader \
    centralBrainSourceText \
    centralBrainDrivingText \
    centralBrainRestrictionText \
    centralBrainConnectionText \
    centralBrainIntentTab \
    centralBrainPlanTab \
    centralBrainExecutionTab \
    centralBrainResultTab \
    centralBrainIntentSurface \
    centralBrainPlanSurface \
    centralBrainExecutionSurface \
    centralBrainTimelineIntentText \
    centralBrainTimelineContextText \
    centralBrainTimelinePlanText \
    centralBrainTimelinePolicyText \
    centralBrainTimelineGraphText \
    centralBrainTimelineEffectText \
    centralBrainTimelineReadbackText \
    centralBrainExecutionActionsText \
    centralBrainApprovalStateText \
    centralBrainApproveButton \
    centralBrainRejectButton \
    centralBrainPartialStateText \
    centralBrainCompensationStateText \
    centralBrainRetryButton \
    centralBrainUndoButton \
    centralBrainExecutionChainText \
    centralBrainResultSurface \
    centralBrainHomeButton \
    centralBrainNapButton \
    centralBrainSessionStrip \
    centralBrainDeviceDrawer \
    centralBrainHvacSurface \
    centralBrainHvacDesiredText \
    centralBrainHvacPowerButton \
    centralBrainHvacTemperatureDownButton \
    centralBrainHvacTemperatureUpButton \
    centralBrainHvacFanDownButton \
    centralBrainHvacFanUpButton \
    centralBrainHvacAutoButton \
    centralBrainHvacAcButton \
    centralBrainHvacSyncButton \
    centralBrainHvacAirflowButton \
    centralBrainHvacWarmPresetButton \
    centralBrainHvacCoolPresetButton \
    centralBrainHvacClearPresetButton \
    centralBrainHvacEvidenceText \
    centralBrainHvacRequestText \
    centralBrainSeatSurface \
    centralBrainSeatDesiredText \
    centralBrainSeatZoneDriverButton \
    centralBrainSeatZonePassengerButton \
    centralBrainSeatZoneRearLeftButton \
    centralBrainSeatZoneRearRightButton \
    centralBrainSeatHeatDownButton \
    centralBrainSeatHeatUpButton \
    centralBrainSeatVentilationDownButton \
    centralBrainSeatVentilationUpButton \
    centralBrainSeatMassageButton \
    centralBrainSeatReclineDownButton \
    centralBrainSeatReclineUpButton \
    centralBrainSeatUprightPresetButton \
    centralBrainSeatComfortPresetButton \
    centralBrainSeatRestPresetButton \
    centralBrainSeatSafetyText \
    centralBrainSeatEvidenceText \
    centralBrainSeatRequestText \
    centralBrainHvacDetailButton \
    centralBrainSeatDetailButton \
    centralBrainEngineerDetailButton \
    centralBrainEngineerSurface \
    centralBrainEngineerStatusText \
    centralBrainEngineerDrivingUnknownButton \
    centralBrainEngineerDrivingParkedButton \
    centralBrainEngineerDrivingMovingButton \
    centralBrainEngineerOccupancyEmptyButton \
    centralBrainEngineerOccupancyOccupiedButton \
    centralBrainEngineerBeltBeltedButton \
    centralBrainEngineerBeltUnbeltedButton \
    centralBrainEngineerAdapterHvacButton \
    centralBrainEngineerAdapterSeatButton \
    centralBrainEngineerFaultNoneButton \
    centralBrainEngineerFaultDelayButton \
    centralBrainEngineerFaultTimeoutButton \
    centralBrainEngineerFaultFailureButton \
    centralBrainEngineerFaultTerminalButton \
    centralBrainEngineerFaultMismatchButton \
    centralBrainEngineerContextText \
    centralBrainEngineerFaultText \
    centralBrainEngineerResetButton; do
    rg -q "$surface_id" "$WORK_DIR/res/layout/main_layout.xml"
  done
  for stage_tag in \
    central_brain_stage_intent \
    central_brain_stage_plan \
    central_brain_stage_execution \
    central_brain_stage_result; do
    rg -Fq "android:tag=\"$stage_tag\"" "$WORK_DIR/res/layout/main_layout.xml"
  done
  for scenario_id in \
    care.cold \
    care.fatigue \
    task.home \
    skill.nap; do
    rg -Fq "android:tag=\"$scenario_id\"" "$WORK_DIR/res/layout/main_layout.xml"
  done
  if rg -q 'android:tag="(state\.vehicle|memory\.preference|skills\.catalog|governance\.audit|security\.denied|security\.privacy|runtime\.npu|system\.overview)"' \
      "$WORK_DIR/res/layout/main_layout.xml"; then
    echo "legacy diagnostic aliases must not remain on the intent-first primary surface" >&2
    exit 1
  fi
  rg -q "centralBrainReplyText" "$WORK_DIR/res/layout/main_layout.xml"
  rg -q "central_brain_panel_background" "$WORK_DIR/res/layout/main_layout.xml"
  rg -q 'centralBrainRenderRegion.*android:layout_width="match_parent".*android:layout_height="match_parent"' "$WORK_DIR/res/layout/main_layout.xml"
  if rg -q 'centralBrainRenderRegion.*android:layout_weight=' "$WORK_DIR/res/layout/main_layout.xml"; then
    echo "Client2 render region must remain full-screen behind the floating panel" >&2
    exit 1
  fi
  rg -q 'centralBrainPanelOverlay.*android:layout_width="match_parent".*android:layout_height="match_parent".*android:visibility="gone".*android:clickable="true"' "$WORK_DIR/res/layout/main_layout.xml"
  rg -q 'centralBrainPanel.*android:layout_width="624.0dp".*android:layout_height="888.0dp".*android:layout_gravity="top|right".*android:layout_marginTop="160.0dp".*android:layout_marginRight="32.0dp".*android:elevation="8.0dp"' "$WORK_DIR/res/layout/main_layout.xml"
  rg -q 'centralBrainNavigationTriggerRail.*android:layout_height="96.0dp".*android:layout_gravity="bottom".*android:weightSum="24.0"' "$WORK_DIR/res/layout/main_layout.xml"
  rg -q 'Space.*android:layout_weight="9.5"' "$WORK_DIR/res/layout/main_layout.xml"
  rg -q 'centralBrainNavigationTrigger.*android:tag="central_brain_menu_toggle".*android:layout_weight="1.0".*android:background="@android:color/transparent".*android:clickable="true".*android:contentDescription="Central Brain menu"' "$WORK_DIR/res/layout/main_layout.xml"
  rg -q 'Space.*android:layout_weight="13.5"' "$WORK_DIR/res/layout/main_layout.xml"
  rg -q '#99EEF2F3' "$WORK_DIR/res/drawable/central_brain_panel_background.xml"
  rg -q '#E8FFFFFF' "$WORK_DIR/res/drawable/central_brain_action_button.xml"
  rg -q '#C8FFFFFF' "$WORK_DIR/res/drawable/central_brain_reply_background.xml"
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
