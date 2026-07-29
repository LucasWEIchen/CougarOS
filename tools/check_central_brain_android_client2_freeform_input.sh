#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-001/003/004/006, S2-HMI-006/010, S2-MDL-004/005, S2-OBS-001/002.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_client2_freeform_input_v1.json"
LAYOUT="$ROOT_DIR/apk-labs/client2-central-brain/patches/main_layout.central_brain_panel.xml"
COORDINATOR="$ROOT_DIR/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
STATE="$ROOT_DIR/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitHmiState.java"
REDUCER="$ROOT_DIR/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitHmiReducer.java"
ORCHESTRATION="$ROOT_DIR/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/OrchestrationRuntimeClient.java"
INPUT_CONTRACT="$ROOT_DIR/central-brain/android-runtime/central-brain-sdk/src/debug/java/com/centralbrain/sdk/model/DevelopmentModelInputContract.java"
PROMPT="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/CockpitModelPrompt.java"
SCENARIO="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/assets/scenarios/scene.aios.freeform.v1.json"

for path in \
  "$CONTRACT" "$LAYOUT" "$COORDINATOR" "$STATE" "$REDUCER" \
  "$ORCHESTRATION" "$INPUT_CONTRACT" "$PROMPT" "$SCENARIO"; do
  test -f "$path"
done

python3 -m json.tool "$CONTRACT" >/dev/null
python3 -m json.tool "$SCENARIO" >/dev/null

python3 - "$CONTRACT" <<'PY'
import json
import sys

contract = json.load(open(sys.argv[1], encoding="utf-8"))
assert contract["entry_mapping"]["phone_icon_action"] == "OPEN_FREEFORM_TEXT_INPUT"
assert contract["entry_mapping"]["navigation_icon_action"] == "TOGGLE_FIXED_TASK_PANEL"
assert contract["entry_mapping"]["entry_surfaces_mutually_exclusive"] is True
assert contract["text_input"]["maximum_chars"] == 1024
assert contract["text_input"]["raw_text_persisted"] is False
assert contract["model_input"]["consume_once"] is True
assert contract["model_authority"]["model_may_dispatch_effect_directly"] is False
assert contract["claim_state"]["production_ready"] is False
assert contract["claim_state"]["target_hardware_validated"] is False
PY

grep -Fq 'android:id="@+id/centralBrainPhoneTrigger"' "$LAYOUT"
grep -Fq 'android:tag="central_brain_freeform_toggle"' "$LAYOUT"
grep -Fq 'android:contentDescription="AIOS text input"' "$LAYOUT"
grep -Fq 'android:id="@+id/centralBrainNavigationTrigger"' "$LAYOUT"
grep -Fq 'android:tag="central_brain_menu_toggle"' "$LAYOUT"
grep -Fq 'android:id="@+id/centralBrainFreeformInput"' "$LAYOUT"
grep -Fq 'android:maxLength="1024"' "$LAYOUT"
grep -Fq 'android:id="@+id/centralBrainFreeformSubmitButton"' "$LAYOUT"

grep -Fq 'TextInputVisibility' "$STATE"
grep -Fq 'textInputVisibility(boolean visible)' "$REDUCER"
grep -Fq 'submitFreeformInput()' "$COORDINATOR"
grep -Fq 'startScenario("agent.freeform", text)' "$COORDINATOR"
grep -Fq 'raw_utterance_logged=false' "$COORDINATOR"
grep -Fq 'INPUT_TEXT_ONLY' "$ORCHESTRATION"
grep -Fq 'stageOwnModelInput' "$ORCHESTRATION"
grep -Fq 'MAX_TEXT_CHARS = 1_024' "$INPUT_CONTRACT"
grep -Fq 'forFreeform' "$PROMPT"
grep -Fq 'MODEL_PROPOSAL_ONLY' "$PROMPT"
grep -Fq '"scenarioId": "scene.aios.freeform.v1"' "$SCENARIO"
grep -Fq '"nodeType": "model.invoke"' "$SCENARIO"
grep -Fq '"nodeType": "policy.evaluate"' "$SCENARIO"

if grep -Eq 'putString\([^)]*(freeform|inputText|utterance)' "$COORDINATOR"; then
  echo "free-form raw text must not enter SharedPreferences" >&2
  exit 1
fi

echo "client2_freeform_dual_entry_defined=true"
echo "client2_freeform_text_bound=1024"
echo "client2_freeform_model_input_consume_once=true"
echo "client2_freeform_model_effect_authority=false"
echo "production_ready=false"
echo "target_hardware_validated=false"
