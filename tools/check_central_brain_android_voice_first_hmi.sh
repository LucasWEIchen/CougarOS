#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, S2-HMI-007, S2-MDL-002, S2-OBS-002, S2-SAF-001,
# S2-EFF-001, XSC-001/005/006, DEL-001/003/004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_voice_first_hmi_v1.json"
MODEL_PROMPT="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/CockpitModelPrompt.java"
OPENCLAW="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/OpenClawInferenceEngine.java"
OLLAMA="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/OllamaInferenceEngine.java"
BOUNDARY="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/orchestration/DebugDecisionCompositionBoundary.java"
BACKEND="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/orchestration/DebugSimulatedOrchestrationBackend.java"
LAYOUT="$ROOT_DIR/apk-labs/client2-central-brain/patches/main_layout.central_brain_panel.xml"
COORDINATOR="$ROOT_DIR/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
CLIENT="$ROOT_DIR/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/OrchestrationRuntimeClient.java"

for path in "$CONTRACT" "$MODEL_PROMPT" "$OPENCLAW" "$OLLAMA" "$BOUNDARY" \
  "$BACKEND" "$LAYOUT" "$COORDINATOR" "$CLIENT"; do
  test -f "$path"
done

python3 -m json.tool "$CONTRACT" >/dev/null
python3 - "$CONTRACT" <<'PY'
import json
import sys

contract = json.load(open(sys.argv[1], encoding="utf-8"))
assert contract["profile_id"] == "android13-client2-voice-first-hmi-v1"
assert {"S2-HMI-007", "S2-MDL-002", "S2-OBS-002"}.issubset(
    contract["requirement_ids"])
hmi = contract["hmi"]
assert hmi["task_trigger_count"] == 2
assert hmi["four_stage_navigation_exposed"] is False
assert hmi["manual_actuator_controls_exposed"] is False
assert hmi["panel_bounds_1920x1080"] == [1288, 200, 1888, 960]
context = contract["model_context"]
assert context["environment"] == "AUTOMOTIVE_COCKPIT"
assert context["required_actions_bound_to_capability_plan"] is True
assert context["model_authorizes_effect"] is False
feedback = contract["simulated_feedback"]
assert feedback["vehicle_bus_accessed"] is False
assert feedback["effect_authority_granted"] is False
assert feedback["safety_implementation_present"] is False
evidence = contract["android13_arm64_evidence"]
assert evidence["development_ollama_over_adb_reverse_completed"] is True
assert evidence["openclaw_target_port_18789_listening"] is False
assert evidence["openclaw_target_profile_installed_after_development_retest"] is True
assert evidence["fixed_target_credential_embedded_in_source_and_apk"] is True
assert evidence["credential_logged"] is False
claims = contract["claim_state"]
assert claims["voice_first_hmi_implemented"] is True
assert claims["production_ready"] is False
assert claims["target_hardware_validated"] is False
PY

for marker in \
  'environment=AUTOMOTIVE_COCKPIT' \
  'occupant_role=DRIVER' \
  'service_goal=DRIVER_COMFORT_AND_ALERTNESS' \
  'EFFECT_MODE = "UI_SIMULATION_ONLY"' \
  'SAFETY_MODE = "INTERFACE_RESERVED"' \
  'hvac_setpoint=' \
  'required action is missing'; do
  grep -Fq "$marker" "$MODEL_PROMPT"
done
grep -Fq 'CockpitModelPrompt' "$OPENCLAW" "$OLLAMA" "$BOUNDARY"
grep -Fq 'getAdmittedActions' "$BOUNDARY"
grep -Fq 'modelUnavailableCapabilities' "$BACKEND"

for id in \
  centralBrainTiredButton centralBrainColdButton centralBrainLiveTraceScroll \
  centralBrainLiveTraceText centralBrainActuatorOverlay \
  centralBrainActuatorFanProgress centralBrainSeatFeedbackRegion centralBrainSeatBack; do
  grep -Fq "$id" "$LAYOUT"
done
if grep -Eq 'centralBrain(Driver|Passenger)TemperatureOverlay' "$LAYOUT"; then
  echo "legacy Android temperature overlay remains exposed" >&2
  exit 1
fi
if grep -Eq 'centralBrain(StageNavigation|IntentTab|PlanTab|ExecutionTab|ResultTab|DeviceDrawer|HvacPowerButton|SeatHeatUpButton)' "$LAYOUT"; then
  echo "legacy multi-stage or manual actuator UI remains exposed" >&2
  exit 1
fi
test "$(grep -Eo 'android:tag="care\.(fatigue|cold)"' "$LAYOUT" | wc -l)" -eq 2
grep -Fq 'android:layout_width="600.0dp"' "$LAYOUT"
grep -Fq 'android:layout_height="760.0dp"' "$LAYOUT"
grep -Fq 'LIVE_TRACE_INTERVAL_MS = 360L' "$COORDINATOR"
grep -Fq 'MAX_LIVE_TRACE_LINES = 32' "$COORDINATOR"
grep -Fq 'animateTemperature(26.5f, 28.0f)' "$COORDINATOR"
grep -Fq 'setUnityTemperatureState(true)' "$COORDINATOR"
grep -Fq 'animateFan(1, 3)' "$COORDINATOR"
grep -Fq 'animateSeat(15.0f, 30.0f)' "$COORDINATOR"
grep -Fq 'seatBackView.setRotation(-(current - from) * 1.2f)' "$COORDINATOR"
grep -Fq 'setVisible(seatFeedbackRegion, fatigue)' "$COORDINATOR"
grep -Fq 'onPipelineMilestone' "$CLIENT" "$COORDINATOR"
grep -Fq 'Demo auto-continue · no authority granted' "$CLIENT"
grep -Fq 'No vehicle-bus evidence' "$CLIENT"

printf '%s\n' \
  'voice_first_hmi_static_contract_verified=true' \
  'cockpit_model_context_verified=true' \
  'model_action_plan_binding_verified=true' \
  'live_pipeline_trace_verified=true' \
  'simulated_actuator_feedback_verified=true' \
  'security_implementation_present=false' \
  'vehicle_bus_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
