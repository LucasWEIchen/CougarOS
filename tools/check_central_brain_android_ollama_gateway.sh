#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MDL-001, S2-SAF-001, S2-OBS-001, XSC-001/005/006,
# DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_ROOT="central-brain/android-runtime/runtime-service"
CONFIG="$RUNTIME_ROOT/src/main/java/com/centralbrain/runtime/model/OllamaEndpointConfig.java"
ENGINE="$RUNTIME_ROOT/src/debug/java/com/centralbrain/runtime/model/OllamaInferenceEngine.java"
BOUNDARY="$RUNTIME_ROOT/src/debug/java/com/centralbrain/runtime/orchestration/DebugDecisionCompositionBoundary.java"
BUILD="$RUNTIME_ROOT/build.gradle.kts"
MANIFEST="$RUNTIME_ROOT/src/main/AndroidManifest.xml"
MAIN_NETWORK="$RUNTIME_ROOT/src/main/res/xml/network_security_config.xml"
DEBUG_NETWORK="$RUNTIME_ROOT/src/debug/res/xml/network_security_config.xml"
FROZEN_AIDL="central-brain/android-runtime/central-brain-sdk/src/main/aidl/com/centralbrain/sdk/orchestration/OrchestrationSnapshot.aidl"
MODEL_AIDL_ROOT="central-brain/android-runtime/central-brain-sdk/src/debug/aidl/com/centralbrain/sdk/model"
MODEL_AIDL="$MODEL_AIDL_ROOT/DevelopmentModelProjection.aidl"
MODEL_INTERFACE="$MODEL_AIDL_ROOT/ICentralBrainDevelopmentModelProjection.aidl"
MODEL_CONTRACT="central-brain/android-runtime/central-brain-sdk/src/debug/java/com/centralbrain/sdk/model/DevelopmentModelProjectionContract.java"
MODEL_CLIENT="central-brain/android-runtime/central-brain-sdk/src/debug/java/com/centralbrain/sdk/DevelopmentModelProjectionClient.java"
MODEL_STORE="$RUNTIME_ROOT/src/debug/java/com/centralbrain/runtime/model/DevelopmentModelProjectionStore.java"
MODEL_SERVICE="$RUNTIME_ROOT/src/debug/java/com/centralbrain/runtime/model/DevelopmentModelProjectionService.java"
DEBUG_MANIFEST="$RUNTIME_ROOT/src/debug/AndroidManifest.xml"
PERSISTENCE="$RUNTIME_ROOT/src/main/java/com/centralbrain/runtime/persistence/DurableOrchestrationProjectionRepository.java"
RUNTIME_SERVICE="$RUNTIME_ROOT/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
CLIENT_STATE="apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitSimulatedScenarioState.java"
CLIENT_REDUCER="apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitHmiReducer.java"
CLIENT_RUNTIME="apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/OrchestrationRuntimeClient.java"
BRIDGE="tools/start_central_brain_wsl_ollama_bridge.sh"
CONTRACT="central-brain/contracts/central_brain_android_ollama_gateway_v1.json"
DESIGN="docs/CENTRAL_BRAIN_OLLAMA_MODEL_GATEWAY.md"

require_file() {
  [[ -f "$ROOT_DIR/$1" ]] \
    || { echo "missing P7-R2 Ollama gateway file: $1" >&2; exit 1; }
}

require_text() {
  grep -Fq -- "$2" "$ROOT_DIR/$1" \
    || { echo "missing P7-R2 marker '$2' in $1" >&2; exit 1; }
}

for file in \
  "$CONFIG" "$ENGINE" "$BOUNDARY" "$BUILD" "$MANIFEST" \
  "$MAIN_NETWORK" "$DEBUG_NETWORK" "$FROZEN_AIDL" "$MODEL_AIDL" \
  "$MODEL_INTERFACE" "$MODEL_CONTRACT" "$MODEL_CLIENT" "$MODEL_STORE" \
  "$MODEL_SERVICE" "$DEBUG_MANIFEST" "$PERSISTENCE" "$RUNTIME_SERVICE" \
  "$CLIENT_STATE" "$CLIENT_REDUCER" "$CLIENT_RUNTIME" "$BRIDGE" "$CONTRACT" \
  "$DESIGN"; do
  require_file "$file"
done

for marker in \
  'DEVELOPMENT_HOST = "127.0.0.1"' \
  'PRODUCTION_HOST = "169.254.208.110"' \
  'CHAT_PATH = "/api/chat"' \
  'developmentWslAdbReverse' \
  'productionLinkLocal' \
  'value.contains("://")' \
  '"UNCONFIGURED".equals(value)'; do
  require_text "$CONFIG" "$marker"
done

for marker in \
  'implements LocalModelProvider.LocalInferenceEngine' \
  'Profile.DEVELOPMENT_WSL_ADB_REVERSE' \
  'connection.setInstanceFollowRedirects(false)' \
  'connection.setRequestMethod("POST")' \
  'root.add("format", responseSchema(prompt))' \
  'prompt.allowedActions.contains(action)' \
  'failure_code=' \
  'raw_prompt_logged=false' \
  'raw_response_logged=false'; do
  require_text "$ENGINE" "$marker"
done

for marker in \
  'new OllamaInferenceEngine(endpoint)' \
  'ModelProviderRegistry.ANDROID_LOCAL_DEVELOPMENT_ID' \
  'PolicyAwareModelRouter.RouteMode.DEVELOPMENT' \
  'PolicyAwareModelRouter.NetworkPolicy.ALLOW_ANY' \
  'getAssistantDisplayText()' \
  'getModelLatencyMs()'; do
  require_text "$BOUNDARY" "$marker"
done

for marker in \
  'buildConfigField("boolean", "OLLAMA_DEVELOPMENT_ENABLED", "true")' \
  'buildConfigField("String", "OLLAMA_BASE_URL", "\"http://127.0.0.1:11434\"")' \
  'buildConfigField("boolean", "OLLAMA_DEVELOPMENT_ENABLED", "false")' \
  'buildConfigField("String", "OLLAMA_BASE_URL", "\"http://169.254.208.110:11434\"")' \
  'buildConfigField("String", "OLLAMA_MODEL", "\"UNCONFIGURED\"")'; do
  require_text "$BUILD" "$marker"
done

require_text "$MANIFEST" 'android.permission.INTERNET'
require_text "$MANIFEST" 'android:networkSecurityConfig="@xml/network_security_config"'
require_text "$MAIN_NETWORK" 'cleartextTrafficPermitted="false"'
require_text "$MAIN_NETWORK" '>169.254.208.110</domain>'
require_text "$DEBUG_NETWORK" 'cleartextTrafficPermitted="false"'
require_text "$DEBUG_NETWORK" '>127.0.0.1</domain>'
if grep -Fq '169.254.208.110' "$ROOT_DIR/$DEBUG_NETWORK"; then
  echo 'debug network policy must not expose the production Ollama endpoint' >&2
  exit 1
fi

for marker in \
  'String assistantDisplayText = "";' \
  'String providerId = "";' \
  'long latencyMs = 0;' \
  'String outputDigest = "";' \
  'String projectionDigest = "";'; do
  require_text "$MODEL_AIDL" "$marker"
done
if grep -Eq 'assistantDisplayText|providerId|latencyMs|outputDigest' \
    "$ROOT_DIR/$FROZEN_AIDL"; then
  echo 'development model fields must not modify frozen Orchestration V1' >&2
  exit 1
fi
declared_hash="$(sed -nE \
  's/.*INTERFACE_HASH = "([0-9a-f]{64})";.*/\1/p' \
  "$ROOT_DIR/$MODEL_INTERFACE")"
computed_hash="$({
  for file in "$MODEL_AIDL" "$MODEL_INTERFACE"; do
    sed -E \
      's/const String INTERFACE_HASH = "[0-9a-f]{64}";/const String INTERFACE_HASH = "<generated-by-checker>";/' \
      "$ROOT_DIR/$file"
  done
} | sha256sum | awk '{print $1}')"
[[ "$declared_hash" == "$computed_hash" ]] \
  || { echo "development model projection hash drift" >&2; exit 1; }
for marker in \
  'MAX_ASSISTANT_DISPLAY_CHARS = 256' \
  'MAX_LATENCY_MS = 120_000L' \
  'projection digest mismatch'; do
  require_text "$MODEL_CONTRACT" "$marker"
done
for marker in \
  'MAX_ENTRIES = 16' \
  'getOwn(' \
  'DevelopmentModelProjectionContract.validate(projection)'; do
  require_text "$MODEL_STORE" "$marker"
done
for marker in \
  'Capability.ORCHESTRATION_READ_OWN' \
  'findSessionOwned(sessionId, owner)' \
  'durable_model_text_stored=false'; do
  require_text "$MODEL_SERVICE" "$marker"
done
require_text "$DEBUG_MANIFEST" '.model.DevelopmentModelProjectionService'
if grep -Fq 'DevelopmentModelProjectionService' "$ROOT_DIR/$MANIFEST"; then
  echo 'development model projection Service leaked into the release manifest' >&2
  exit 1
fi
if grep -Eq 'assistantDisplayText|modelProviderId|modelLatencyMs' "$ROOT_DIR/$PERSISTENCE"; then
  echo 'ephemeral model projection must not be checkpointed by the durable repository' >&2
  exit 1
fi
require_text "$RUNTIME_SERVICE" 'ollama_development_gateway_enabled='
require_text "$RUNTIME_SERVICE" 'ollama_endpoint_profile='
require_text "$RUNTIME_SERVICE" 'ollama_release_provider_enabled=false'

require_text "$CLIENT_STATE" 'isModelInferenceCompleted()'
require_text "$CLIENT_STATE" 'getAssistantDisplayText()'
require_text "$CLIENT_REDUCER" 'next.assistantDisplayText = simulated.getAssistantDisplayText();'
require_text "$CLIENT_RUNTIME" 'modelProjectionClient.getOwnProjection(start.sessionId)'

for marker in \
  'reverse "tcp:${DEVICE_PORT}" "tcp:${HOST_PORT}"' \
  '/api/version' \
  'raw_identity_logged=false' \
  'production_ready=false' \
  'target_hardware_validated=false'; do
  require_text "$BRIDGE" "$marker"
done

python3 - "$ROOT_DIR/$CONTRACT" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    contract = json.load(handle)
assert contract["schema_version"] == 1
dev = contract["development_profile"]
assert dev["android_base_url"] == "http://127.0.0.1:11434"
assert dev["host_base_url"] == "http://127.0.0.1:11434"
assert dev["adb_reverse"] == "tcp:11434 tcp:11434"
assert dev["model"] == "qwen3.5:27b-optimized"
assert dev["actual_http_provider_wired"] is True
assert dev["structured_output_required"] is True
assert dev["action_allowlist_required"] is True
assert dev["ephemeral_reply_projection_wired"] is True
assert dev["projection_interface"] == "com.centralbrain.sdk.model.ICentralBrainDevelopmentModelProjection"
assert dev["projection_interface_hash"] == "6932c481e3f6556a6ffe336459615687df26419a82f47cb30c074e3ec4ffd015"
assert dev["projection_debug_source_set_only"] is True
assert dev["orchestration_v1_unchanged"] is True
assert dev["model_text_persisted"] is False
production = contract["production_profile"]
assert production["android_base_url"] == "http://169.254.208.110:11434"
assert production["arbitrary_endpoint_override_allowed"] is False
assert production["redirects_allowed"] is False
assert production["release_provider_enabled"] is False
assert production["development_projection_service_published"] is False
assert production["release_model"] == "UNCONFIGURED"
assert production["production_runtime_wired"] is False
evidence = contract["development_hardware_evidence"]
assert evidence["android_api"] == 33
assert evidence["abi"] == "arm64-v8a"
assert evidence["device_endpoint_reached"] is True
assert evidence["model_invoked"] is True
assert evidence["terminal_response_validated"] is True
assert evidence["current_projection_interface_android13_arm64_verified"] is False
assert evidence["current_projection_retest_blocker"] == "adb_device_count_0"
state = contract["claim_state"]
assert state["development_wsl_gateway_implemented"] is True
assert state["development_android13_arm64_verified"] is True
assert state["development_projection_host_verified"] is True
assert state["development_projection_android13_arm64_verified"] is False
assert state["production_endpoint_contract_defined"] is True
assert state["production_provider_implemented"] is False
assert state["production_npu_validated"] is False
assert state["production_ready"] is False
assert state["target_hardware_validated"] is False
assert state["implementation_stage"] == "P7-R2"
PY

for doc in \
  README.md \
  central-brain/android-runtime/README.md \
  docs/CENTRAL_BRAIN_ROADMAP.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md \
  docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md \
  docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md \
  docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md \
  docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md; do
  require_text "$doc" 'P7-R2'
done

printf '%s\n' \
  'Central Brain Android Ollama gateway check passed' \
  'development_wsl_gateway_implemented=true' \
  'development_android13_arm64_verified=true' \
  'development_projection_host_verified=true' \
  'development_projection_android13_arm64_verified=false' \
  'production_endpoint_contract_defined=true' \
  'production_provider_implemented=false' \
  'production_npu_validated=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'implementation_stage=P7-R2'
