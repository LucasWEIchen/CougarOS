#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ACTIVITY="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/DirectModelServiceDevelopmentProbeActivity.java"
MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
CONTRACT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/DirectModelServiceContract.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/model/DirectModelServiceContractTest.java"
RUNNER="tools/run_central_brain_android_direct_model_development_probe.sh"

for path in "$ACTIVITY" "$MANIFEST" "$CONTRACT" "$TEST" "$RUNNER"; do
  [[ -s "$ROOT/$path" ]] \
    || { echo "direct model development artifact missing: $path" >&2; exit 1; }
done

require_text() {
  grep -Fq -- "$2" "$ROOT/$1" \
    || { echo "direct model development marker missing in $1: $2" >&2; exit 1; }
}

for marker in \
  'DEVELOPMENT_PROFILE_ID' \
  'developmentOllama(String modelName)' \
  'OllamaEndpointConfig.developmentWslAdbReverse(modelName)'; do
  require_text "$CONTRACT" "$marker"
done
require_text "$TEST" 'developmentOllamaEndpointIsFixedForAdbReverseAndNotProduction'
require_text "$TEST" 'http://127.0.0.1:11434/api/chat'

for marker in \
  'class DirectModelServiceDevelopmentProbeActivity' \
  'DirectModelServiceContract.developmentOllama(' \
  'new DirectModelServiceProvider(' \
  'new OllamaChatProtocolAdapter()' \
  'validateStructuredReply(content)' \
  'direct_model_development_probe_complete=' \
  'raw_prompt_logged=false' \
  'raw_response_logged=false' \
  'tool_authority=false' \
  'effect_authority=false' \
  'production_ready=false' \
  'target_hardware_validated=false'; do
  require_text "$ACTIVITY" "$marker"
done
require_text "$MANIFEST" '.model.DirectModelServiceDevelopmentProbeActivity'
require_text "$MANIFEST" 'android:permission="android.permission.DUMP"'

for marker in \
  'start_central_brain_wsl_ollama_bridge.sh' \
  '-PcentralBrainDevelopmentOllama=true' \
  ':runtime-service:testDebugUnitTest' \
  ':runtime-service:assembleDebug' \
  'DirectModelServiceDevelopmentProbeActivity' \
  'reason=ANDROID_PROCESS_CRASHED' \
  'android_testboard_verified=true' \
  'production_model_provider_wired=false' \
  'target_hardware_validated=false'; do
  require_text "$RUNNER" "$marker"
done

if find "$ROOT/central-brain/android-runtime/runtime-service/src/main" \
    -name 'DirectModelServiceDevelopmentProbeActivity.java' -print -quit \
    | grep -q .; then
  echo "debug direct model probe leaked into the production source set" >&2
  exit 1
fi

python3 - "$ROOT/$MANIFEST" <<'PY'
import sys
import xml.etree.ElementTree as ET

android = "{http://schemas.android.com/apk/res/android}"
root = ET.parse(sys.argv[1]).getroot()
matches = [
    item for item in root.find("application").findall("activity")
    if item.get(android + "name")
       == ".model.DirectModelServiceDevelopmentProbeActivity"
]
if len(matches) != 1:
    raise SystemExit("direct model development probe manifest entry is not unique")
activity = matches[0]
if activity.get(android + "permission") != "android.permission.DUMP":
    raise SystemExit("direct model development probe is not DUMP protected")
if activity.get(android + "theme") != "@android:style/Theme.Translucent.NoTitleBar":
    raise SystemExit("direct model development probe must support asynchronous execution")
PY

printf '%s\n' \
  'Central Brain Android direct model development probe check passed' \
  'development_endpoint_fixed=true' \
  'android_probe_uses_direct_model_provider=true' \
  'android_probe_uses_openclaw=false' \
  'raw_model_content_logged=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
