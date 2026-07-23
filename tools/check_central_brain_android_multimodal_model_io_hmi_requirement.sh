#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, S2-HMI-003/007/008, S2-MDL-002, S2-OBS-002,
# S2-SAF-001, XSC-001/005/006, DEL-001/003/004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_multimodal_model_io_hmi_requirement_v1.json"
LAYOUT="$ROOT_DIR/apk-labs/client2-central-brain/patches/main_layout.central_brain_panel.xml"

python3 -m json.tool "$CONTRACT" >/dev/null
python3 - "$CONTRACT" <<'PY'
import json
import sys

contract = json.load(open(sys.argv[1], encoding="utf-8"))
assert contract["schema_version"] == 1
assert contract["profile_id"] == "android13-client2-multimodal-model-io-hmi-requirement-v1"
assert contract["maturity"] == "requirement_defined_implementation_open"
assert {"S2-HMI-008", "S2-MDL-002", "S2-OBS-002"}.issubset(
    contract["requirement_ids"])

surface = contract["surface"]
assert surface["model_input_visible"] is True
assert surface["model_output_visible"] is True
assert surface["source_must_be_actual_model_exchange"] is True
assert surface["button_label_as_model_input_allowed"] is False
assert surface["fixture_as_live_input_allowed"] is False

text = contract["text"]
assert text["render_directly"] is True
assert text["maximum_code_points_per_item"] == 4096
assert text["truncation_must_be_explicit"] is True

image = contract["image"]
assert image["maximum_images_per_model_input"] == 1
assert image["allowlisted_mime_types"] == ["image/png", "image/jpeg"]
assert image["thumbnail_max_width_dp"] == 320
assert image["thumbnail_max_height_dp"] == 180
assert image["thumbnail_scale_type"] == "FIT_CENTER"
assert image["preserve_aspect_ratio"] is True
assert image["crop_allowed"] is False
assert image["text_and_thumbnail_visible_together"] is True
assert image["tap_opens_center_preview"] is True
assert image["preview_max_screen_width_ratio"] == 0.9
assert image["preview_max_screen_height_ratio"] == 0.85
assert image["tap_outside_dismisses_preview"] is True
assert image["back_dismisses_preview"] is True
assert image["tap_image_dismisses_preview"] is False

restriction = contract["driving_restriction"]
assert restriction["fullscreen_preview_allowed_states"] == ["PARKED", "IDLE"]
assert restriction["fullscreen_preview_blocked_states"] == [
    "MOVING_RESTRICTED", "UNKNOWN_RESTRICTED", "FAULT_RESTRICTED"]

privacy = contract["lifecycle_and_privacy"]
assert privacy["ui_process_memory_only"] is True
for key in (
    "room_persistence_allowed",
    "shared_preferences_persistence_allowed",
    "checkpoint_persistence_allowed",
    "raw_text_logging_allowed",
    "raw_image_logging_allowed",
    "github_evidence_payload_allowed",
):
    assert privacy[key] is False

claims = contract["claim_state"]
assert claims["requirement_defined"] is True
for key in (
    "model_io_hmi_implemented",
    "frontend_multimodal_ingress_bound",
    "actual_model_input_projected_to_hmi",
    "actual_model_output_projected_to_hmi",
    "image_thumbnail_rendered",
    "image_center_preview_interaction_implemented",
    "android13_arm64_model_io_hmi_verified",
    "repository_software_requirements_complete",
    "production_ready",
    "target_hardware_validated",
):
    assert claims[key] is False
assert claims["implementation_stage"] == "P4-R4-REQUIREMENT"
PY

for doc in \
  README.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_AIOS_STAGE2_PRODUCT_UX_PLAN.md \
  docs/CENTRAL_BRAIN_AIOS_STAGE2_DEVELOPMENT_BACKLOG.md \
  docs/CENTRAL_BRAIN_COCKPIT_HMI_CONTROL_LOOP_PLAN.md \
  docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md \
  docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md \
  docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md \
  docs/CENTRAL_BRAIN_ROADMAP.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md \
  docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md \
  docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md; do
  grep -Fq 'P4-R4' "$ROOT_DIR/$doc" \
    || { echo "P4-R4 requirement marker missing: $doc" >&2; exit 1; }
done

for marker in \
  'centralBrainModelInputText' \
  'centralBrainModelInputImageThumbnail' \
  'centralBrainModelOutputText' \
  'centralBrainModelImagePreviewOverlay' \
  'centralBrainModelImagePreview'; do
  if grep -Fq "$marker" "$LAYOUT"; then
    echo "P4-R4 UI marker exists while requirement contract still says implementation open: $marker" >&2
    exit 1
  fi
done

printf '%s\n' \
  'multimodal_model_io_hmi_requirement_defined=true' \
  'model_io_hmi_implemented=false' \
  'frontend_multimodal_ingress_bound=false' \
  'image_center_preview_interaction_implemented=false' \
  'android13_arm64_model_io_hmi_verified=false' \
  'repository_software_requirements_complete=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'implementation_stage=P4-R4-REQUIREMENT'
