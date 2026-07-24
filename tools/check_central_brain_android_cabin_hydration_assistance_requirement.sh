#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-HMI-009, S2-CTX-002, S2-PER-001, S2-INT-001,
# S2-NAV-001, S2-COM-001, plus existing Stage 2 governance contracts.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_cabin_hydration_assistance_requirement_v1.json"
DESIGN="$ROOT_DIR/docs/CENTRAL_BRAIN_CABIN_HYDRATION_ASSISTANCE_DESIGN.md"

python3 -m json.tool "$CONTRACT" >/dev/null
python3 - "$CONTRACT" <<'PY'
import json
import sys

contract = json.load(open(sys.argv[1], encoding="utf-8"))
assert contract["schema_version"] == 1
assert contract["profile_id"] == "android13-cabin-hydration-assistance-requirement-v1"
assert contract["maturity"] == "requirement_defined_software_open"

expected_packages = [f"P4-R5{suffix}" for suffix in "abcdefghijkl"]
assert contract["work_packages"] == expected_packages
assert {
    "S2-HMI-009",
    "S2-CTX-002",
    "S2-PER-001",
    "S2-INT-001",
    "S2-NAV-001",
    "S2-COM-001",
}.issubset(contract["requirement_ids"])

reasoning = contract["reasoning_boundary"]
assert reasoning["visible_fact_contract_required"] is True
assert reasoning["intent_hypothesis_separate_from_observation"] is True
assert reasoning["age_identity_family_relation_inference_allowed"] is False
assert reasoning["thirst_as_direct_visual_fact_allowed"] is False
assert reasoning["model_can_authorize_tool_or_effect"] is False
assert reasoning["model_can_authorize_purchase_or_navigation"] is False
assert reasoning["unknown_or_conflict_fails_closed"] is True

seat = contract["seat_context"]
assert seat["seat_zones"] == [
    "ROW1_DRIVER", "ROW1_PASSENGER", "ROW2_LEFT", "ROW2_RIGHT"]
assert seat["per_seat_occupancy_required"] is True
assert seat["vision_sensor_conflict_state_required"] is True
assert seat["empty_seat_effect_allowed"] is False
assert seat["unknown_or_conflict_seat_effect_allowed"] is False

confirmation = contract["confirmations"]
assert confirmation["types"] == [
    "ASSISTANCE_CONSENT", "PURCHASE_COMMIT", "NAVIGATION_START"]
assert confirmation["purchase_and_navigation_confirmation_separate"] is True
assert confirmation["target_digest_binding_required"] is True
assert confirmation["cross_session_replay_allowed"] is False
assert confirmation["confirmation_overrides_hard_interlock"] is False

tools = contract["tools"]
assert len(tools["families"]) == 6
assert tools["commerce_is_vehicle_capability"] is False
assert tools["raw_image_as_tool_input_allowed"] is False
assert tools["production_simulation_fallback_allowed"] is False

hmi = contract["hmi"]
assert len(hmi["stages"]) == 12
assert hmi["event_driven_live_trace_required"] is True
assert hmi["single_jump_to_final_result_allowed"] is False
assert hmi["simulation_label_required"] is True
assert hmi["target_display"] == "1920x1080"

assert contract["production_empty_interfaces"] == [
    "live_oms_or_camera_frame_provider",
    "trusted_seat_occupancy_source",
    "production_navigation_adapter",
    "production_commerce_and_payment_adapter",
]

claims = contract["claim_state"]
assert claims["hydration_assistance_requirement_defined"] is True
assert claims["hydration_assistance_software_implemented"] is False
assert claims["p4_r5_open_work_package_count"] == 12
assert claims["repository_software_requirements_complete"] is False
assert claims["open_repository_software_requirement_count"] == 12
assert claims["unclassified_repository_requirement_count"] == 0
assert claims["production_navigation_adapter_wired"] is False
assert claims["production_commerce_adapter_wired"] is False
assert claims["production_payment_implemented"] is False
assert claims["driver_hal_development_required"] is False
assert claims["production_ready"] is False
assert claims["target_hardware_validated"] is False
assert claims["implementation_stage"] == "P4-R5-REQUIREMENT"
PY

for marker in \
  'P4-R5a' 'P4-R5b' 'P4-R5c' 'P4-R5d' 'P4-R5e' 'P4-R5f' \
  'P4-R5g' 'P4-R5h' 'P4-R5i' 'P4-R5j' 'P4-R5k' 'P4-R5l' \
  'CabinFrameInputV2' 'CabinObservationBatchV1' 'CabinOccupancyContextV1' \
  'ModelSemanticProposalV2' 'IntentHypothesisV1' 'ConfirmationRequestV1' \
  'ASSISTANCE_CONSENT' 'PURCHASE_COMMIT' 'NAVIGATION_START' \
  'repository_software_requirements_complete=false' \
  'production_payment_implemented=false'; do
  grep -Fq "$marker" "$DESIGN" \
    || { echo "hydration requirement design marker missing: $marker" >&2; exit 1; }
done

for doc in \
  README.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_ROADMAP.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md \
  docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md \
  docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md \
  docs/CENTRAL_BRAIN_GRANULAR_REQUIREMENT_CATALOG.md \
  docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md \
  docs/CENTRAL_BRAIN_AIOS_STAGE2_PRODUCT_UX_PLAN.md \
  docs/CENTRAL_BRAIN_COCKPIT_HMI_CONTROL_LOOP_PLAN.md \
  docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md \
  docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md \
  docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md; do
  grep -Fq 'P4-R5' "$ROOT_DIR/$doc" \
    || { echo "P4-R5 requirement marker missing: $doc" >&2; exit 1; }
done

printf '%s\n' \
  'cabin_hydration_assistance_requirement_defined=true' \
  'cabin_hydration_assistance_module_count=12' \
  'cabin_hydration_assistance_software_implemented=false' \
  'repository_software_requirements_complete=false' \
  'open_repository_software_requirement_count=12' \
  'unclassified_repository_requirement_count=0' \
  'production_navigation_adapter_wired=false' \
  'production_commerce_adapter_wired=false' \
  'production_payment_implemented=false' \
  'driver_hal_development_required=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'implementation_stage=P4-R5-REQUIREMENT'
