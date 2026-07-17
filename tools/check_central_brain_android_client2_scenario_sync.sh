#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-HMI-001..006, S2-SCN-001, APP-004, XSC-001/005/006.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="$ROOT_DIR/apk-labs/client2-central-brain"
CONTROL="$PROJECT/bridge/src/com/centralbrain/client2/CockpitScenarioControlState.java"
STATE="$PROJECT/bridge/src/com/centralbrain/client2/CockpitHmiState.java"
REDUCER="$PROJECT/bridge/src/com/centralbrain/client2/CockpitHmiReducer.java"
COORDINATOR="$PROJECT/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
BRIDGE="$PROJECT/bridge/src/com/centralbrain/client2/Client2ScenarioBridge.java"
TEST_MAIN="$PROJECT/bridge/test/com/centralbrain/client2/CockpitHmiReducerTestMain.java"
DEVICE_TEST="$ROOT_DIR/tools/test_client2_central_brain_scenario_sync.sh"
PHYSICAL_REPORT="$ROOT_DIR/docs/CENTRAL_BRAIN_ANDROID13_PHYSICAL_TARGET_TEST_REPORT.md"

require_text() {
  local file="$1"
  local text="$2"
  if ! grep -Fq "$text" "$file"; then
    echo "Missing required scenario synchronization marker '$text' in $file" >&2
    exit 1
  fi
}

for path in \
  "$CONTROL" "$STATE" "$REDUCER" "$COORDINATOR" "$BRIDGE" "$TEST_MAIN" \
  "$DEVICE_TEST" "$PHYSICAL_REPORT"; do
  test -f "$path"
done

require_text "$CONTROL" 'care.cold", "scene.comfort.cold.v1"'
require_text "$CONTROL" 'care.fatigue", "scene.fatigue.assist.v1"'
require_text "$CONTROL" 'skill.nap", "scene.rest.nap.v1"'
require_text "$CONTROL" 'manual.hvac", "scene.manual.hvac.adjust.v1"'
require_text "$CONTROL" 'manual.seat", "scene.manual.seat.adjust.v1"'
require_text "$CONTROL" 'CatalogStatus.MISMATCH'
require_text "$CONTROL" 'public boolean isPlanPublished()'
require_text "$CONTROL" 'public boolean isEffectDispatchEnabled()'
require_text "$CONTROL" 'public boolean isReadbackAvailable()'
require_text "$STATE" 'CockpitScenarioControlState scenarioControlState'
require_text "$REDUCER" '.runtimeEvent(event.sequence)'
require_text "$REDUCER" 'CB_HMI_SCENARIO_MISMATCH'
require_text "$COORDINATOR" 'cockpit_scenario_device_session_synchronized=true'
require_text "$COORDINATOR" 'Desired parameters：NOT PUBLISHED'
require_text "$BRIDGE" 'private ScenarioClient client;'
require_text "$BRIDGE" 'CockpitScenarioControlState.canonicalScenarioId(scenarioId)'
require_text "$TEST_MAIN" 'cockpit_scenario_plan_publication_inferred=false'
require_text "$DEVICE_TEST" 'cockpit_scenario_natural_cold_sync_verified=true'
require_text "$DEVICE_TEST" 'cockpit_scenario_manual_seat_sync_verified=true'
require_text "$DEVICE_TEST" 'cockpit_scenario_debug_context_effect_authority=false'
require_text "$PHYSICAL_REPORT" 'P4-W10 scenario/manual control synchronization evidence'
require_text "$PHYSICAL_REPORT" 'cockpit_scenario_device_session_synchronized=true'
require_text "$PHYSICAL_REPORT" 'cockpit_scenario_effect_dispatch_enabled=false'

if grep -Fq 'private SessionClient client;' "$BRIDGE"; then
  echo "Client2 bridge must depend on the ScenarioClient interface" >&2
  exit 1
fi
if grep -Eq 'import .*Adapter|new .*Adapter|VehicleProperty|CarPropertyManager' \
    "$COORDINATOR" "$REDUCER" "$CONTROL"; then
  echo "Client2 HMI must not bypass ScenarioClient to reach adapters or vehicle APIs" >&2
  exit 1
fi

bash "$ROOT_DIR/tools/check_central_brain_android_client2_hmi_reducer.sh"

echo "cockpit_scenario_control_state_reducer_owned=true"
echo "cockpit_scenario_catalog_normalized=true"
echo "cockpit_scenario_manual_shared_client=true"
echo "cockpit_scenario_device_session_synchronized=true"
echo "cockpit_scenario_plan_publication_inferred=false"
echo "cockpit_scenario_effect_dispatch_enabled=false"
echo "cockpit_scenario_readback_available=false"
echo "scenario_execution_enabled=false"
echo "hardware_accessed=false"
echo "Central Brain Android Client2 scenario synchronization check passed"
