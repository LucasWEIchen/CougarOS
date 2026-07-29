#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_CONTRACT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/DirectModelServiceContract.java"
ENDPOINT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/OllamaEndpointConfig.java"
PROFILES="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ModelProviderProfiles.java"
REGISTRY="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/ModelProviderRegistry.java"
ROUTER="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/PolicyAwareModelRouter.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/model/DirectModelServiceContractTest.java"
MACHINE_CONTRACT="central-brain/contracts/central_brain_android_direct_model_service_v1.json"

for path in "$JAVA_CONTRACT" "$ENDPOINT" "$PROFILES" "$REGISTRY" "$ROUTER" \
    "$TEST" "$MACHINE_CONTRACT"; do
  [[ -s "$ROOT_DIR/$path" ]] \
    || { echo "direct model service artifact missing: $path" >&2; exit 1; }
done

require_text() {
  grep -Fq -- "$2" "$ROOT_DIR/$1" \
    || { echo "direct model service marker missing in $1: $2" >&2; exit 1; }
}

for marker in \
  'class DirectModelServiceContract' \
  'enum WireProtocol' \
  'OLLAMA_CHAT_V1' \
  'enum Modality' \
  'TEXT_IMAGE' \
  'MAX_IMAGE_COUNT = 1' \
  'MAX_IMAGE_BYTES = 6L * 1024L * 1024L' \
  'isAgentGatewayRequired()' \
  'return false;' \
  'isArbitraryEndpointOverrideAllowed()' \
  'grantsToolAuthority()' \
  'grantsEffectAuthority()' \
  'productionOllama(String modelName)'; do
  require_text "$JAVA_CONTRACT" "$marker"
done

for marker in \
  'DIRECT_MODEL_SERVICE_ID' \
  '"external.model-service.direct"' \
  'BackendKind.DIRECT_MODEL_SERVICE' \
  'DIRECT_MODEL_SERVICE_IMPLEMENTATION_NOT_WIRED'; do
  require_text "$PROFILES" "$marker"
done

for marker in \
  'ProviderKind.DIRECT_MODEL_SERVICE' \
  'HealthSource.DIRECT_MODEL_SERVICE_RUNTIME' \
  'DIRECT_MODEL_SERVICE_ID'; do
  require_text "$REGISTRY" "$marker"
done
require_text "$ROUTER" 'ProviderKind.DIRECT_MODEL_SERVICE'
require_text "$ROUTER" 'DIRECT_MODEL_SERVICE_ID'

require_text "$ENDPOINT" 'PRODUCTION_HOST = "169.254.208.110"'
require_text "$ENDPOINT" 'CHAT_PATH = "/api/chat"'
require_text "$TEST" 'productionOllamaEndpointIsFixedAndAgentGatewayFree'
require_text "$TEST" 'textAndImageRequestBindsAllDigestsWithoutGrantingAuthority'
require_text "$TEST" 'modalityMimeAndSizeViolationsFailClosed'

python3 - "$ROOT_DIR/$REGISTRY" "$ROOT_DIR/$MACHINE_CONTRACT" <<'PY'
import json
import sys
from pathlib import Path

registry = Path(sys.argv[1]).read_text(encoding="utf-8")
catalog = registry.split(
    "private static List<ProviderDescriptor> createFixedCatalog()", 1
)[1].split("private static String digestCatalog", 1)[0]
if "DIRECT_MODEL_SERVICE_ID" not in catalog:
    raise SystemExit("direct model provider is absent from the fixed catalog")
if "TARGET_OPENCLAW_TRANSITIONAL_ID" in catalog:
    raise SystemExit("OpenClaw remains registered in the new fixed catalog")

contract = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
architecture = contract["architecture"]
endpoint = contract["production_endpoint"]
modalities = contract["modalities"]
migration = contract["migration"]
claims = contract["claim_state"]

assert architecture["provider_id"] == "external.model-service.direct"
assert architecture["backend_kind"] == "DIRECT_MODEL_SERVICE"
assert architecture["agent_gateway_required"] is False
assert architecture["aios_owns_session_context"] is True
assert architecture["aios_owns_prompt_composition"] is True
assert architecture["aios_owns_tool_orchestration"] is True
assert architecture["model_output_authorizes_tools"] is False
assert architecture["model_output_authorizes_effects"] is False
assert endpoint["base_uri"] == "http://169.254.208.110:11434"
assert endpoint["chat_path"] == "/api/chat"
assert endpoint["arbitrary_endpoint_override_allowed"] is False
assert modalities["text_supported"] is True
assert modalities["text_image_supported"] is True
assert modalities["maximum_images_per_request"] == 1
assert migration["openclaw_in_new_provider_catalog"] is False
assert migration["openclaw_selected_by_new_router"] is False
assert migration["direct_model_contract_implemented"] is True
assert migration["direct_model_provider_implemented"] is False
assert claims["release_provider_implemented"] is False
assert claims["production_ready"] is False
assert claims["target_hardware_validated"] is False
PY

printf '%s\n' \
  'Central Brain Android direct model service contract check passed' \
  'aios_owns_model_session=true' \
  'aios_owns_prompt_and_tool_orchestration=true' \
  'direct_model_service_contract_implemented=true' \
  'direct_model_service_registered=true' \
  'openclaw_in_new_provider_catalog=false' \
  'openclaw_selected_by_new_router=false' \
  'direct_model_release_provider_implemented=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
