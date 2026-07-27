#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-HMI-009, S2-CTX-002, S2-PER-001, S2-INT-001,
# S2-NAV-001, S2-COM-001, S2-SAF-001, XSC-001/005/006.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_cabin_shopping_route_planning_v1.json"
DESIGN="$ROOT_DIR/docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md"
SCENARIO="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/assets/scenarios/scene.cabin.multimodal.assist.v1.json"
SERVICE="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/scenario/SimulatedShoppingPlanningService.java"
PROMPT="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/model/CockpitModelPrompt.java"
CLIENT="$ROOT_DIR/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/OrchestrationRuntimeClient.java"
COORDINATOR="$ROOT_DIR/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
LAYOUT="$ROOT_DIR/apk-labs/client2-central-brain/patches/main_layout.central_brain_panel.xml"

python3 -B - "$CONTRACT" "$SCENARIO" <<'PY'
import json
import pathlib
import sys

contract = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
scenario = json.loads(pathlib.Path(sys.argv[2]).read_text(encoding="utf-8"))

assert contract["schema_version"] == 2
assert contract["profile_id"] == "android13-cabin-shopping-route-planning-v2"
assert contract["maturity"] == "debug_software_implemented_production_adapters_empty"
assert contract["work_packages"] == [f"P4-R5{x}" for x in "abcdefghijkl"]
assert contract["service_taxonomy"] == {
    "primary_services": ["shopping.service", "route_planning.service"],
    "hydration_service_exists": False,
    "water_is_product_category": True,
    "commerce_is_vehicle_capability": False,
    "route_planning_is_vehicle_effect": False,
}
assert contract["model_actions"]["required"] == [
    "shopping.search_products", "navigation.plan_purchase_route"]
assert contract["model_actions"]["vehicle_hvac_action_required"] is False
assert contract["scenario_graph"]["node_count"] == 13
assert contract["scenario_graph"]["tool_node_count"] == 6
assert contract["scenario_graph"]["approval_node_count"] == 3
assert contract["scenario_graph"]["vehicle_effect_node_count"] == 0
assert contract["confirmations"]["purchase_and_navigation_confirmation_separate"] is True
assert contract["confirmations"]["node_digest_binding_required"] is True
assert contract["debug_results"]["order_commit_status"] == "ORDER_NOT_DISPATCHED"
assert contract["debug_results"]["navigation_status"] == "NAVIGATION_SIMULATED"
assert contract["debug_results"]["external_dispatch_performed"] is False
assert contract["debug_results"]["payment_material_accessed"] is False
assert contract["debug_results"]["vehicle_hardware_accessed"] is False
hardware = contract["hardware_validation"]
assert hardware["test_device_serial_alias"] == "testboard"
assert hardware["testboard_wsl_openclaw_verified"] is True
assert hardware["production_device_serial_alias"] == "production-board"
assert hardware["production_target_ipv4_route_available"] is True
assert hardware["production_target_ipv4_configuration_persistent"] is False
assert hardware["production_openclaw_ethernet_verified"] is True
assert hardware["production_openclaw_multimodal_verified"] is True
assert hardware["production_model_latency_ms"] == 44908
assert hardware["production_graph_final_revision"] == 77
claims = contract["claim_state"]
assert claims["shopping_route_planning_requirement_defined"] is True
assert claims["shopping_route_planning_debug_software_implemented"] is True
assert claims["p4_r5_open_work_package_count"] == 0
assert claims["production_navigation_adapter_wired"] is False
assert claims["production_commerce_adapter_wired"] is False
assert claims["production_payment_implemented"] is False
assert claims["production_ready"] is False
assert claims["target_hardware_validated"] is False
assert claims["implementation_stage"] == "P4-R5-SHOPPING-ROUTE"

assert scenario["scenarioId"] == "scene.cabin.multimodal.assist.v1"
assert scenario["version"] == 2
nodes = scenario["planTemplate"]["nodes"]
assert len(nodes) == 13
assert sum(node["nodeType"] == "tool.invoke" for node in nodes) == 6
assert sum(node["nodeType"] == "approval.interrupt" for node in nodes) == 3
assert sum(node["nodeType"] == "effect.execute" for node in nodes) == 0
assert {node["nodeId"] for node in nodes} >= set(contract["shopping_tools"])
assert {node["nodeId"] for node in nodes} >= set(
    contract["scenario_graph"]["approval_nodes"])
PY

for file in "$DESIGN" "$SERVICE" "$PROMPT" "$CLIENT" "$COORDINATOR" "$LAYOUT"; do
  [[ -f "$file" ]] || { echo "shopping route file missing: $file" >&2; exit 1; }
done

for marker in \
  'shopping.search_products' 'navigation.plan_purchase_route' \
  'ORDER_NOT_DISPATCHED' 'NAVIGATION_SIMULATED'; do
  grep -Fq "$marker" "$SERVICE" "$PROMPT" "$DESIGN" \
    || { echo "shopping route marker missing: $marker" >&2; exit 1; }
done

for marker in \
  'request_shopping_consent' \
  'request_purchase_confirmation' \
  'request_navigation_confirmation'; do
  grep -Fq "$marker" "$SCENARIO" \
    || { echo "shopping approval marker missing: $marker" >&2; exit 1; }
done

for marker in \
  'centralBrainApprovalControls' \
  'centralBrainApproveButton' \
  'centralBrainShoppingFeedbackRegion'; do
  grep -Fq "$marker" "$LAYOUT" "$COORDINATOR" \
    || { echo "shopping HMI marker missing: $marker" >&2; exit 1; }
done

printf '%s\n' \
  'cabin_shopping_route_planning_check_passed=true' \
  'shopping_route_planning_requirement_defined=true' \
  'shopping_route_planning_debug_software_implemented=true' \
  'testboard_wsl_openclaw_verified=true' \
  'production_navigation_adapter_wired=false' \
  'production_commerce_adapter_wired=false' \
  'production_payment_implemented=false' \
  'production_openclaw_ethernet_verified=true' \
  'production_openclaw_multimodal_verified=true' \
  'production_target_ipv4_configuration_persistent=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'implementation_stage=P4-R5-SHOPPING-ROUTE'
