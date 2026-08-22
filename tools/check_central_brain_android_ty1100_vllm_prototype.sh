#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/VllmEndpointConfig.java"
ROUTER="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ModelProfileRouter.java"
ROUTER_TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/model/ModelProfileRouterTest.java"
ENGINE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/VllmInferenceEngine.java"
BOUNDARY="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/orchestration/DebugDecisionCompositionBoundary.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/orchestration/VllmPrototypeIntegrationProbeActivity.java"
BUILD="central-brain/android-runtime/runtime-service/build.gradle.kts"
CONTRACT="central-brain/contracts/central_brain_android_ty1100_vllm_prototype_v1.json"
MANAGER="tools/manage_central_brain_ty1100_routed_vllm.sh"
PREWARM="tools/prewarm_central_brain_ty1100_vllm_models.py"

require_text() {
  grep -Fq -- "$2" "$ROOT/$1" \
    || { echo "missing TY1100 vLLM marker '$2' in $1" >&2; exit 1; }
}

for file in "$CONFIG" "$ROUTER" "$ROUTER_TEST" "$ENGINE" "$BOUNDARY" \
  "$PROBE" "$BUILD" "$CONTRACT" "$MANAGER" "$PREWARM" \
  tools/manage_central_brain_ty1100_vllm_bridge.sh \
  tools/run_central_brain_android_ty1100_vllm_probe.sh; do
  [[ -f "$ROOT/$file" ]] || { echo "missing TY1100 vLLM file: $file" >&2; exit 1; }
done

for marker in \
  'GENERAL_VLLM_PORT = 10_030' 'SMOKING_VLLM_PORT = 10_031' \
  'DEVELOPMENT_HOST = "127.0.0.1"' \
  'CHAT_COMPLETIONS_PATH = "/v1/chat/completions"' \
  'GENERAL_MODEL = "Qwen3.5-9B-AWQ"' \
  'SMOKING_MODEL = "Qwen3.5-2B-AWQ"' \
  'GENERAL_MAX_CONTEXT_TOKENS = 8_192' \
  'SMOKING_MAX_CONTEXT_TOKENS = 4_096' \
  'TY1100_GENERAL_9B_VIA_ADB_REVERSE' \
  'TY1100_SMOKING_2B_VIA_ADB_REVERSE'; do
  require_text "$CONFIG" "$marker"
done
for marker in \
  'GENERAL_PROFILE_ID = "model.general-cockpit.9b.v1"' \
  'SMOKING_PROFILE_ID = "model.cabin-smoking.2b.v1"' \
  'CABIN_SMOKING_COMPLIANCE' 'NO_FALLBACK' \
  'TARGET_HEALTH_STALE' 'TARGET_NOT_READY' \
  'isActionAuthorizationGranted() { return false; }' \
  'isEffectDispatchRequested() { return false; }'; do
  require_text "$ROUTER" "$marker"
done
for marker in \
  'smokingVisionRequestSelectsOnlyReady2bProfile' \
  'textAndNonSmokingVisionRequestsSelectGeneral9bProfile' \
  'requiredTargetFailureDoesNotFallbackToOtherModel' \
  'requestContractAndFixedProfileIdentityFailClosed'; do
  require_text "$ROUTER_TEST" "$marker"
done
for marker in \
  '"image_url"' '"data:" + imageMimeType + ";base64,"' \
  '"response_format"' '"json_schema"' '"strict", true' \
  'UrlConnectionWarmupProbe' 'warmupProbe.verify(endpoint)' \
  'duplicate actions are rejected locally' \
  'raw_prompt_logged=false' 'raw_response_logged=false'; do
  require_text "$ENGINE" "$marker"
done
for marker in \
  'VLLM_DEVELOPMENT' 'ty1100General9bViaAdbReverse()' \
  'ty1100Smoking2bViaAdbReverse()' 'ANDROID_LOCAL_DEVELOPMENT_ID' \
  'RouteMode.DEVELOPMENT' 'ModelProfileRouter.decide(' \
  'model-profile-route-v1' 'model-specialist-route-v1' \
  'warmupWithoutFallback(modelProvider, modelSpec)' \
  'warmupWithoutFallback(smokingModelProvider, smokingModelSpec)'; do
  require_text "$BOUNDARY" "$marker"
done
provider_route_line="$(grep -nF \
  'PolicyAwareModelRouter.RouteDecision route = PolicyAwareModelRouter.decide(' \
  "$ROOT/$BOUNDARY" | cut -d: -f1)"
profile_route_line="$(grep -nF \
  'profileRoute = ModelProfileRouter.decide(' \
  "$ROOT/$BOUNDARY" | cut -d: -f1)"
[[ -n "$provider_route_line" && -n "$profile_route_line" \
  && "$provider_route_line" -lt "$profile_route_line" ]] \
  || { echo "Provider route must precede model-profile route" >&2; exit 1; }
for marker in \
  'development_ty1100_vllm' \
  'VLLM_GENERAL_MODEL", "\"Qwen3.5-9B-AWQ\""' \
  'VLLM_GENERAL_CONTEXT_TOKENS", "8192"' \
  'VLLM_SMOKING_MODEL", "\"Qwen3.5-2B-AWQ\""' \
  'VLLM_SMOKING_CONTEXT_TOKENS", "4096"' \
  'VLLM_MODEL_ROUTING_ENABLED", "true"' \
  'VLLM_PREWARM_REQUIRED", "true"' \
  'buildConfigField("boolean", "VLLM_DEVELOPMENT_ENABLED", "false")' \
  'buildConfigField("boolean", "VLLM_MODEL_ROUTING_ENABLED", "false")' \
  'buildConfigField("boolean", "VLLM_PREWARM_REQUIRED", "false")' \
  'ws://169.254.208.110:18789' \
  'buildConfigField("int", "OPENCLAW_PROTOCOL_VERSION", "3")'; do
  require_text "$BUILD" "$marker"
done
for marker in \
  'vllm_prototype_probe_complete=' 'android_transport=ADB_REVERSE' \
  'ai_transport=ETHERNET_SSH_TUNNEL' 'model_action_authority=false' \
  'model_profile_id=' 'model_id=' 'production_ready=false'; do
  require_text "$PROBE" "$marker"
done
for marker in \
  'central-brain-routed-qwen35-9b' 'central-brain-routed-qwen35-2b' \
  'GENERAL_CONTEXT="8192"' 'SMOKING_CONTEXT="4096"' \
  '--restart always' 'start_services' 'prewarm_models' \
  'production_configuration_changed=false'; do
  require_text "$MANAGER" "$marker"
done
for marker in \
  'GENERAL_CONTEXT = 8192' 'SMOKING_CONTEXT = 4096' \
  'chat_template_kwargs": {"enable_thinking": False}' \
  'both_models_resident_and_ready' '"passes"'; do
  require_text "$PREWARM" "$marker"
done

python3 -B - "$ROOT/$CONTRACT" <<'PY'
import json
import pathlib
import sys

contract = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
assert contract["schema_version"] == 1
assert contract["implementation_stage"] == "P4-R10-VLLM"
provider = contract["prototype_provider"]
assert provider["provider_id"] == "android.vllm.prototype"
assert provider["only_selectable_prototype_network_provider"] is True
profiles = {item["profile_id"]: item for item in provider["model_profiles"]}
assert set(profiles) == {
    "model.general-cockpit.9b.v1", "model.cabin-smoking.2b.v1"
}
general = profiles["model.general-cockpit.9b.v1"]
assert general["model"] == "Qwen3.5-9B-AWQ"
assert general["android_base_url"] == "http://127.0.0.1:10030"
assert general["maximum_context_tokens"] == 8192
smoking = profiles["model.cabin-smoking.2b.v1"]
assert smoking["model"] == "Qwen3.5-2B-AWQ"
assert smoking["android_base_url"] == "http://127.0.0.1:10031"
assert smoking["maximum_context_tokens"] == 4096
routing = contract["model_profile_routing"]
assert routing["enabled_in_debug"] is True
assert routing["provider_route_precedes_profile_route"] is True
assert routing["fallback_policy"] == "NO_FALLBACK"
assert routing["smoking_scenario_profile_id"] == "model.cabin-smoking.2b.v1"
assert routing["all_other_scenarios_profile_id"] == "model.general-cockpit.9b.v1"
residency = contract["residency_and_prewarm"]
assert residency["both_models_resident"] is True
assert residency["prewarm_required"] is True
assert residency["thinking_enabled"] is False
topology = contract["validation_topology"]
assert topology["android_to_wsl"] == ["ADB_REVERSE_TCP_10030", "ADB_REVERSE_TCP_10031"]
assert topology["wsl_to_ai_device"] == "DUAL_SSH_LOCAL_FORWARD_OVER_ETHERNET"
assert topology["ai_device_ipv4"] == "192.168.250.100"
assert topology["ai_device_service_modified"] is False
protocol = contract["protocol"]
assert protocol["api"] == "OPENAI_CHAT_COMPLETIONS_COMPATIBLE"
assert protocol["text_supported"] is True
assert protocol["one_png_or_jpeg_image_supported"] is True
production = contract["production_separation"]
assert production["release_openclaw_base_url"] == "ws://169.254.208.110:18789"
assert production["release_openclaw_protocol_version"] == 3
assert production["production_configuration_changed"] is False
claims = contract["claim_state"]
assert claims["android13_arm64_text_verified"] is True
assert claims["android13_arm64_image_verified"] is True
assert claims["debug_model_profile_routing_verified"] is True
assert claims["dual_model_residency_verified"] is True
for key in (
    "direct_android_ethernet_validated", "direct_npu_api_accessed",
    "vehicle_effect_hardware_accessed", "production_ready",
    "target_hardware_validated",
):
    assert claims[key] is False
PY

printf '%s\n' \
  'ty1100_vllm_prototype_contract_verified=true' \
  'prototype_provider=android.vllm.prototype' \
  'general_model=Qwen3.5-9B-AWQ' \
  'smoking_model=Qwen3.5-2B-AWQ' \
  'model_profile_routing=true' \
  'production_configuration_changed=false' \
  'production_ready=false'
