#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-002/003/004/005/007, S2-SCN-006, S2-MDL-007,
# S2-SAF-006, S2-HMI-008/011, S2-OBS-001/002.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_smoking_detection_agent_v1.json"
ROUTER="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/agent/CabinComplianceAgentRouter.java"
RESULT="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/SmokingDetectionResult.java"
PROMPT="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/assets/agents/smoking-detection-agent-v1.md"
SCENARIO="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/assets/scenarios/scene.cabin.compliance.smoking.v1.json"
VLLM="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/VllmInferenceEngine.java"
OPENCLAW="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/OpenClawInferenceEngine.java"
INPUT="$ROOT_DIR/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitMultimodalInput.java"
CONTROL="$ROOT_DIR/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitScenarioControlState.java"
COORDINATOR="$ROOT_DIR/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
LAYOUT="$ROOT_DIR/apk-labs/client2-central-brain/patches/main_layout.central_brain_panel.xml"
IMAGE="$ROOT_DIR/apk-labs/client2-central-brain/patches/res/raw/central_brain_smoking_detection_frame.png"

for path in "$CONTRACT" "$ROUTER" "$RESULT" "$PROMPT" "$SCENARIO" \
  "$VLLM" "$OPENCLAW" "$INPUT" "$CONTROL" "$COORDINATOR" "$LAYOUT" "$IMAGE"; do
  [[ -f "$path" ]] || { echo "missing smoking-detection artifact: $path" >&2; exit 1; }
done

python3 -B - "$CONTRACT" "$SCENARIO" <<'PY'
import json
import pathlib
import sys

contract = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
scenario = json.loads(pathlib.Path(sys.argv[2]).read_text(encoding="utf-8"))

assert contract["schema_version"] == 1
assert contract["scene"]["canonical_scenario_id"] == scenario["scenarioId"]
assert contract["scene"]["button_label"] == "检测吸烟"
assert contract["agent_route"]["explicit_route_selected_by_model"] is False
assert contract["model_input"]["required_model_capability"] == "VISION_CLASSIFICATION"
assert contract["agent_output"]["exact_fields"] == [
    "smoking_detected", "person_count", "location", "confidence", "description"
]
assert contract["authority"]["model_authorizes_tool"] is False
assert contract["authority"]["model_authorizes_effect"] is False
assert contract["validation_boundary"]["watermarked_fixture_is_accuracy_evidence"] is False
assert contract["claim_state"]["production_ready"] is False
assert contract["claim_state"]["target_hardware_validated"] is False

nodes = scenario["planTemplate"]["nodes"]
assert [node["nodeId"] for node in nodes] == [
    "capture_compliance_context",
    "route_smoking_specialist",
    "invoke_smoking_detection_agent",
    "validate_smoking_detection_result",
    "render_compliance_result",
]
assert not any(node["nodeType"] in {"tool.invoke", "effect.execute"} for node in nodes)
assert scenario["requiredCapabilities"] == ["perception.cabin.smoking_detection"]
print("smoking_detection_contract_verified=true")
PY

grep -Fq 'routeExplicit(String scenarioId)' "$ROUTER"
grep -Fq 'routeCandidate(' "$ROUTER"
grep -Fq '"smoking_detected"' "$RESULT"
grep -Fq 'SMOKING_DETECTION_V1' "$VLLM" "$OPENCLAW"
grep -Fq 'reader.setStrictness(Strictness.STRICT)' "$RESULT"
grep -Fq 'parseCompactWire(String raw)' "$RESULT"
grep -Fq 'registerScenarioImageAttachment' "$VLLM" "$OPENCLAW"
grep -Fq 'SMOKING_FAST_MAX_OUTPUT_TOKENS = 24' "$VLLM"
grep -Fq 'SMOKING_FALLBACK_MAX_OUTPUT_TOKENS = 64' "$VLLM"
grep -Fq 'SMOKING_FAST_IMAGE_WIDTH = 1_280' "$VLLM"
grep -Fq 'SMOKING_FAST_IMAGE_HEIGHT = 720' "$VLLM"
grep -Fq 'chatTemplateKwargs.addProperty("enable_thinking", false)' "$VLLM"
grep -Fq 'requiresSmokingFallback' "$VLLM"
grep -Fq 'Arrays.fill(fastImage, (byte) 0)' "$VLLM"
grep -Fq 'public static final String SMOKING_UI_SCENARIO_ID = "cabin.smoking"' "$INPUT"
grep -Fq '"cabin.smoking", "scene.cabin.compliance.smoking.v1"' "$CONTROL"
grep -Fq 'android:tag="cabin.smoking"' "$LAYOUT"
grep -Fq 'android:text="检测吸烟"' "$LAYOUT"
grep -Fq '"AGENT ROUTER"' "$COORDINATOR"
grep -Fq '"COMPLIANCE RESULT"' "$COORDINATOR"

[[ "$(sha256sum "$PROMPT" | cut -d' ' -f1)" == \
  "a79926000fbf7aa7d7888daa4ed04f72cd591ce48888303af7d0f3993827c2fc" ]]
[[ "$(sha256sum "$IMAGE" | cut -d' ' -f1)" == \
  "fc561d2870d467da910353e6623ab39f056641bf4169083953b9d3c9c82f10a7" ]]
[[ "$(stat -c '%s' "$IMAGE")" == "206611" ]]

grep -Fq '`P4-R9` 座舱吸烟合规多 Agent 场景' \
  "$ROOT_DIR/docs/CENTRAL_BRAIN_REQUIREMENTS.md"
grep -Fq 'SmokingDetectionAgent' \
  "$ROOT_DIR/docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md"
grep -Fq 'SmokingDetectionResult.parse()' \
  "$ROOT_DIR/docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md"
grep -Fq '座舱吸烟合规 Agent' "$ROOT_DIR/README.md"

printf '%s\n' \
  "Central Brain Android smoking-detection Agent check passed" \
  "smoking_detection_route_deterministic=true" \
  "smoking_detection_output_strict=true" \
  "smoking_detection_fast_path=1280x720_compact_wire_v2" \
  "smoking_detection_fallback=original_image_five_field_v1" \
  "smoking_detection_thinking_enabled=false" \
  "smoking_detection_tool_node_count=0" \
  "smoking_detection_effect_node_count=0" \
  "fixture_accuracy_evidence=false" \
  "production_ready=false" \
  "target_hardware_validated=false"
